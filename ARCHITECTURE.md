# ShankaRavam — Architecture

> Technical reference for contributors and AI coding agents.
> User-facing overview lives in `README.md`.
> Product spec: `festival organizer app plan.md` §1–23. Build order: §24 (G1–G6).
> Agent rules: `AGENTS.md` (Rules #1–#3 are non-negotiable).

## 1. Overview

Native Android festival-counter app for Indian temples (Vinayaka Chavithi, Sri Rama Navami, Dasara, etc.).
Volunteers record cash + material donations, broadcast Telugu announcements over a Bluetooth horn speaker,
track expenses, and see a live balance — in noisy, offline pandal environments.

Core guarantees:

1. **Local-first:** Room is the single source of truth. Save → Room in <10ms → UI updates. Network never blocks entry.
2. **Free-tier safe:** zero TTS audio in cloud storage, Firestore delta-sync only, receipts compressed to WebP ~100KB.
3. **Non-destructive ledger:** no silent deletes/overwrites. Corrections are new rows (original + delta + reason + author + time). Voids are `Cancelled` flags.

## 2. Tech Stack

| Layer | Choice |
|---|---|
| Language | Kotlin 2.0+ (JVM 17) |
| UI | Jetpack Compose (BOM) + Material 3, Navigation-Compose |
| Arch | MVVM + Clean (`core` / `data` / `domain` / `presentation`), manual DI via `di/AppContainer.kt` (no Hilt) |
| Local DB | Room 2.6+ with KSP, `Flow` queries, `StateFlow` in ViewModels (`WhileSubscribed(5000)`) |
| Background | WorkManager 2.9+ (`SyncWorker`, 15-min periodic + on-demand) |
| Audio | `AudioManager` + `AudioFocusManager`, `AudioRouteDetector`; `TextToSpeech` `te-IN` + Sarvam AI REST (Retrofit/OkHttp) |
| Cloud (optional) | Firebase Auth (Google Sign-In) + Firestore (Spark). Key in `EncryptedSharedPreferences` (`SecureKeyStore`) |
| Build | Gradle 8.11.1, AGP 8.9.2, compile/targetSdk 36, minSdk 26 |

## 3. Module Map

```text
app/src/main/java/com/shankaravam/festival/
├── MainActivity.kt, ShankaRavamApp.kt   # entry, owns AppContainer
├── di/AppContainer.kt                   # manual service locator (DB, prefs, repos, use cases, audio, cloud)
├── core/
│   ├── theme/    # Color, Type, Shape, Theme (temple tokens)
│   ├── util/     # Outcome, Formatters, AppIds (UUID), ShareCodes (6-char), RateWindow
│   ├── tts/      # TeluguNumberFormatter, AnnouncementTemplates, AndroidTtsClient,
│   │             #   SarvamTtsClient, DualTtsEngine, TempleChime, AudioImport
│   ├── audio/    # AudioFocusManager, AudioRouteDetector (SPEAKER / BLUETOOTH / WIRED)
│   ├── export/   # ReportContent (CSV/WhatsApp builders), ReportExporter (A4 PDF + FileProvider), ReceiptCompressor (WebP)
│   └── i18n/     # AppStrings (EN/TE)
├── data/
│   ├── local/    # AppDatabase (v1) + 5 entities/DAOs, Converters, EntityMappers,
│   │             #   SessionPrefs (current event, filters, audio prefs, sync flags), SecureKeyStore
│   ├── remote/   # AuthRepository, FirestoreSyncService (delta up/down), FirestoreMappers, SarvamApiService
│   ├── repository/# Room-backed impls (single source of truth)
│   └── work/     # SyncWorker
├── domain/
│   ├── model/    # Event, Donation, Expense, Correction, Activity, Enums, Membership (roles/status/AccessPolicy)
│   ├── repository/# interfaces
│   └── usecase/  # SaveDonation, SaveExpense, RecordCorrection, CorrectRecord (5-min grace),
│                 #   CalculateBalance, ObserveEventTotals
└── presentation/
    ├── navigation/NavGraph.kt  # SPLASH → DASHBOARD → entry/list/announce/expense/history/reports/cloud/admin
    ├── splash/   # Vishnu Chakra splash (vector rotate, ≤1.5s)
    ├── dashboard/# totals grid + balance + unsynced badge
    ├── event/    # selector + current-event banner (always visible)
    ├── donation/ # entry (name+pronunciation, cash/item, tags, status), list, detail sheet, DuplicateGuard
    ├── announcement/ # queue (filters, transport, gap, route badge)
    ├── expense/  # entry (category chips, date, WebP receipt), list
    ├── correction/# CorrectDialog (grace vs reason-required)
    ├── history/  # append-only activity feed
    ├── reports/  # PDF / CSV / WhatsApp export
    ├── settings/ # AdminSettings (voice, counter identity, Sarvam key, close event), CloudSync (QR invite, join, approvals, team)
    └── common/   # TempleAppBar, ChakraLoader, CurrencyTextField, AccessBanner, ShareSheet, Derived
```

## 4. Data Flows

### 4.1 Save donation (offline hot path)

```text
DonationEntryScreen → DonationEntryViewModel → SaveDonationUseCase
  → DonationRepositoryImpl → Room DonationDao.insert (<10ms)
  → Flow re-emits → list + dashboard update + haptic
  → background: DualTtsEngine.prefetch() (non-blocking) + WorkManager enqueue (if sync on)
```

Duplicate guard: same donor + gift + status within 60s → confirm dialog (`DuplicateGuard.kt`, `RateWindow.kt`).
Audio-status updates (`updateAudioStatus`) never bump version/sync flags — audio is a local artifact.

### 4.2 Announcement (horn-speaker path)

```text
AnnouncementQueueViewModel builds queue from current filter/sort
  (default: received + confirmed, no pledged/cancelled)
→ AnnouncementTemplates builds TE/EN/BI text (TeluguNumberFormatter: 5000 → ఐదు వేల)
→ DualTtsEngine.playBest():
    Sarvam hit? cacheDir/audio/donation_{id}_{speaker}[_roster].mp3 → play
    else human import? donation_{id}[_roster].mp3 → play (intentional override)
    else Sarvam cloud (if key + online) → cache → play
    else AndroidTtsClient te-IN instantly (offline fallback, never errors visibly)
    Explicit VoiceEngineMode (SessionPrefs flow): OFFLINE_NATIVE skips Sarvam
    files + prefetch (zero quota); human imports still play. Voice pickers in
    Settings and the queue read/write one VoiceConfig — no secrets in queue.
→ F6 Single-Voice Queue: DualTtsEngine.playPhraseBest caches Sarvam intro/outro phrases
    (cacheDir/audio/phrase_{safeKey}_{speaker}.mp3) so roster mode speaks in a single cohesive voice.
→ F6 Android Voice Pick & Fallback: pickBestTeluguVoice automatically selects the best network
    Telugu voice on first init; if network playback fails offline, runtime fallback seamlessly
    switches to an embedded voice so speech never aborts. UI badges display 🌐 Network vs 💾 Offline.
→ AudioFocusManager (transient-may-duck) + optional temple-bell chime → STREAM_MUSIC → BT amp or speaker
```

Route badge (`AudioRouteDetector`): Phone Speaker / Bluetooth / Wired + Test-audio button.
Controls: Play/Pause/Resume/Stop/Replay/Skip/Prev/Next/Repeat + 2/5/10s gap. Queue honors current sort.

### 4.3 Expense + ledger safety

```text
ExpenseEntryScreen → SaveExpenseUseCase → Room (receipt path local-only, WebP ~100KB)
Edit within 5-min grace → direct update; after → CorrectRecordUseCase forces Correction row
  (original preserved + delta + reason + author + timestamp) + activity log
Cancel → status = Cancelled, row kept. No @Delete anywhere. Rules deny delete server-side too.
```

Balance: `sum(confirmed + received cash) − sum(expenses)`. Pledged excluded. Non-cash shown separately unless estimated value given.

### 4.4 Cloud sync (opt-in only)

```text
Settings → Google Sign-In → enable sync → SyncWorker (or Sync-now)
→ FirestoreSyncService: push pending deltas (donations/expenses/corrections/activity), pull remote
→ conflict → mark CONFLICT flag (never last-write-wins on money)
→ presence touch (best-effort merge of lastActiveAt/counterName/deviceTag-last4, never fails sync)
```

Foreground (F3): while the app is visible, `ForegroundSyncManager`
(ProcessLifecycle-driven) holds watermark-filtered listeners on
donations/expenses/corrections + the own member seat — deltas land in Room in
~seconds, approve/revoke reflects instantly. Saves trigger an immediate direct
`syncEvent` (the upload leg); the worker stays as the background backstop.
Uploads are push-stamped (`max(local, now)`, mirrored via `markSynced`) so
offline batches are never skipped; reads use a 120 s fudge for clock skew.
Results are `SyncOutcome` Done/Blocked (pending/revoked/sign-out: show, never
retry) / Failed (network: retry).

Fresh install = offline Organizer, zero login. Firebase getters are guarded so the APK builds/runs without `google-services.json`. Invite = 6-char code + QR (`ShareCodes.kt`, 10-day expiry, head-closeable, `parseJoinCode` accepts raw/QR/URL). Join → `member/pending` → head/collector approves → `organizer/active` or `member/active`. Team UI is canonical in gear Settings (`TeamSyncSection`, sign-in gated, Join needs no local event); `CLOUD_SYNC` route redirects there. Join auto-enables sync + sweeps empty local dummies. Uploads use event-scoped `pendingSyncForEvent` (unscoped query is badge display-only).

## 5. Room Schema (v1, frozen — first change ships a migration)

`Event / Donation / Expense / Correction / Activity` entities. Every syncable row carries:
`id (UUID v4), eventId, createdAt/updatedAt (Long millis UTC), syncState (LOCAL_ONLY/PENDING/UPLOADING/SYNCED/FAILED/CONFLICT), version`.
List queries return `Flow`. `Converters` use char-31 separators. `exportSchema=false`.

## 6. Roles & Membership

Stored role vocabulary: `global_head` (Head) / `organizer` (Collector: money + announce) / `member` (Viewer: totals only).
Status is orthogonal: `active` / `pending` / `revoked`. Never store `role="revoked"`.

- Unknown/corrupt role strings fall back to `member` (least privilege).
- Money writes need BOTH: `AccessPolicy.canWriteMoney` = active + non-member (pending/revoked/viewer refused locally with sync-matching copy — the rules would deny them and strand PENDING_UPLOAD).
- Team management (promote/revoke) and invite-close need creator-or-admin (`globalHeadId == uid` or whitelisted admin); approvals need active-collector. Client gates mirror the rules so buttons never fail server-side.

- Unknown/corrupt role strings fall back to `member` (least privilege).
- Client email check is UI-only. `firestore.rules` (`isGlobalAdmin`, Google-provider + verified pin) is the security boundary.
- Global Head account is whitelisted in one place client-side (`AdminConfig`) and in `firestore.rules`; rotate both together.
- Member docs are merge-written only (presence/identity touch never wipes `joinedAt` or role/status).
- Only full `deviceId` never leaves the device — only `deviceTag` (last 4) is synced.

## 7. Firestore Layout (delta-synced, audio never here)

```text
users/{userId}
codes/{code}                        # invite code → eventId, createdBy owner, 10-day expiry (Timestamp expiresAt; client + server enforced, legacy grandfathered), head-closeable
config/tts_settings                 # { sarvamApiKey, defaultSpeaker } — head-write, member-read
# F5: collectors auto-pull it after every sync + on Settings entry/sign-in
# (15-min throttle; blank/same → no-op; explicit offline lock survives).
events/{eventId}                    # header { name, temple, location, dates, status, globalHeadId }
  members/{userId}                  # { role, status, approvedBy, joinedAt, viaCode (join invite, server-verified live), email?, displayName?, counterName?, deviceTag?, lastActiveAt }
  # F4: counterName = entered counter → Google name → omitted (never Counter-XXXX); deviceTag = last-4 only; ledger maps carry deviceTag, never the full UUID.
  donations/{id} | expenses/{id}    # ledger (no audio fields)
  corrections/{id}                  # append-only (create-only, no update/delete)
  activity/{id}                     # append-only feed
```

## 8. Hard Invariants

1. Room = UI source of truth; no network on the entry hot path; all IO on `Dispatchers.IO` / WorkManager.
2. `firestore.rules` denies all deletes on ledger/members/codes; corrections/activity are create-only.
3. Audio cache: Sarvam `cacheDir/audio/donation_{id}_{speaker}[_roster].mp3` (per-speaker, never collides), intro/outro `cacheDir/audio/phrase_{safeKey}_{speaker}.mp3`; human imports in `donation_{id}[_roster].mp3` (speaker-agnostic override). Zero audio in Firebase Storage. Pruning preserves chime and phrase clips.
4. TTS key lives in `SecureKeyStore` (encrypted) ↔ `/config/tts_settings`; never in logs, never in maps for audio rows.
5. Compose perf: keyed `LazyColumn (key = { it.id })` + `animateItem()`, `derivedStateOf` for totals, single `uiState: StateFlow`, no business logic in composables, `@Immutable` list items.
6. Splash ≤1.5s, entry <10ms, 60/120fps lists.
7. `combine` of >3 flows is avoided (toolchain limit) — nest combines instead.

## 9. Verify

```bat
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
firebase deploy --only firestore:rules --dry-run
```

Manual: airplane-mode counter loop (event → cash + rice-bag + saree → list animates → balance right → pledged excluded → native Telugu speaks); kill-network-then-resume sync; two-device approve/revoke matrix.

## 10. Maintenance Contract

When code changes, update this file + `README.md` (if user-visible) + `PROGRESS.md` + `SESSION_HANDOFF.md` in the same task. A stale architecture section misleads the next session more than no section.
