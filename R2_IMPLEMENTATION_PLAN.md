# Cloudflare R2 Implementation Plan — Phase 0 Local Fixes + Worker + R2 Integration

> **STATUS: FINAL DRAFT FOR NEW SESSION — DO NOT EXECUTE YET.**
> Decision locked by user: **Cloudflare R2** (card on file, $0 billed).
> Voice decision locked: **Shubh = default Sarvam voice, Pooja = secondary** (fresh installs; existing stored choices preserved).
> This file is a plan only. No code, infra, or billing changes were made.
> Sources cross-verified: `OBJECT_STORAGE_BUCKET_REVIEW.md`, this thread's verifications,
> `ARCHITECTURE.md`, `AGENTS.md`, and the files cited per step.
> Read `OBJECT_STORAGE_BUCKET_REVIEW.md` §2–§5 first for the why; this file is the how + order.
> Execution checklist lives in `R2_TASKS.md`; session bootstrap lives in `R2_CONTEXT.md`.

---

## 0. How to read this plan

- **Phase 0 (no cloud, no card, no migration):** must ship first. Fixes confidently-wrong money on the horn speaker + wrong dashboard totals. Independently shippable, fully offline-verifiable.
- **Phase 1 (Cloudflare infra):** dashboard + Wrangler setup. No app changes. Verifiable in Cloudflare dashboard alone.
- **Phase 2 (Worker gateway spec):** the only server code in this plan. Presented as a spec + skeleton — not deployed.
- **Phase 3 (Android client):** receipts first, then CAS audio. Each sub-phase shippable alone.
- **Phase 4 (key removal):** delete Sarvam-key distribution only after Phase 3 audio is proven on two devices.
- Every step lists **files, acceptance, and verify**. If Verify fails, stop — do not proceed to the next step.

---

## 1. Locked decisions (do not re-litigate in implementation)

1. **R2 + Workers, single Cloudflare account.** Rejected: B2-direct, Supabase/Render/Fly signers, Firebase Storage (Blaze-only since Feb 2026), public buckets.
2. **Private bucket only, `workers.dev` ships first.** No public `r2.dev` URLs for ledger data (rate-limited dev URLs + PII leak). Custom domain is a later edge-cache optimization, not a launch blocker (§3.4).
3. **Zero secrets on phones (end state).** Sarvam key lives only as a Worker secret. B2-style keys never exist (R2 uses native binding `env.AUDIO_BUCKET`, no S3 keys at all). `config/tts_settings` keeps `{ defaultSpeaker }` only.
3. **Zero secrets on phones (end state).** Sarvam key lives only as a Worker secret. B2-style keys never exist (R2 uses native binding `env.AUDIO_BUCKET`, no S3 keys at all). `config/tts_settings` keeps `{ defaultSpeaker }` only.
4. **Offline-first preserved (AGENTS.md Rule #1).** Save → Room <10 ms. Audio/receipt network is prefetch/background only, 1.5 s-class timeouts, instant native-TTS fallback. Playback never awaits network.
5. **Non-destructive ledger (Rule #3).** Lifecycle rules touch **only** `audio/*`. Ledger rows, corrections, receipts are never auto-deleted.
6. **CAS for Sarvam audio, per-donation slot for human recordings.** `audio/{hash}.mp3` is immutable + shared; `donation_{id}[_roster].mp3` stays the human-override slot and always wins playback. Phrases get `phrase/{hash}.mp3`. Never mix the namespaces.
7. **Corrections-aware everything.** Hash inputs and balance/voice both use **effective (post-correction) amounts via latest-by-`(createdAt, id)`** (Σ-deltas double-counts — see P0-2). Template bytes change → new hash (automatic invalidation).
8. **No `ListObjects` from phones.** Direct `HEAD`/`GET {hash}` via Worker only. Receipt lists come from Firestore (`receiptUrl`), never bucket listing.
9. **Voice lineup: Shubh default, Pooja secondary.** Fresh installs and unset keys resolve to `shubh`; UI lists order Shubh → Pooja → Priya → Kavitha → Ratan; stored user choices are never force-migrated. See P0-5.

---

## 1.5 Two-tier cache guarantee (why Sarvam is called at most once per unchanged figure)

> User's 2-tier writeup verified 2026-09-14 — adopted with two precision fixes (marked *).

**Tier 1 — phone disk (`cacheDir/audio/audio_{hash}.mp3`).** After first download the phone checks `File("audio_${hash}.mp3").exists()` on every play / replay / repeat / skip-back. Hit → plays from disk in ~0 ms, zero network, zero Cloudflare, zero Sarvam — airplane-mode safe. The announcer's repeat-last button never re-fetches.

**Tier 2 — R2 via Worker (`HEAD audio/{hash}.mp3` → stream or JIT-once).** A second volunteer's phone misses disk, calls `POST /v1/audio/resolve`, Worker `HEAD`s the key: hit → streams bytes (Sarvam: 0 calls); miss → exactly one Sarvam synthesis → `PUT` → stream. Concurrent same-hash races converge on one winner; losers read the winner's object.

| Donation state | Old hash | New hash | Result |
|---|---|---|---|
| Unchanged | `audio_8f4a…` | `audio_8f4a…` (identical) | 0 Sarvam calls, forever (disk or R2 hit) |
| Amount corrected ₹500 → ₹5,000 | `audio_8f4a…` (₹500) | `audio_9d2c…` (₹5,000) | New hash → synthesized **once** for ₹5,000; old clips orphaned, never played |
| Pronunciation fixed రమేష్ → రామేశ్వరరావు | `audio_8f4a…` | `audio_1e7b…` | New hash → synthesized once with correct pronunciation |
| Speaker / language / roster / template bytes differ | `audio_8f4a…` | new hash | Independent cache line per voice rendering (no cross-voice poisoning) |
| Cancelled | `audio_8f4a…` | n/a | Queue filter (`RECEIVED‖CONFIRMED`) drops it before any fetch; stale files die via LRU/lifecycle |

Precision fixes: (*) "exactly once in the history of the universe" means **once per successful generation per distinct hash** — retries after Sarvam/PUT failure and post-lifecycle (30-day `audio/` expiry) re-synthesis are expected and bounded, not zero. (*) Cancelled rows are excluded from *playback*, not deleted from the ledger (Rule #3).

---

## 2. Phase 0 — Local ledger + cache fixes (no bucket, no migration except where noted)

Goal: queue speaks corrected amounts correctly offline; dashboard totals match the ledger; audio bookkeeping stops corrupting sync timestamps.

### P0-1 Grace-window cache invalidation (donations)

- **Bug:** `presentation/donation/DonationDetailViewModel.kt:58-66` grace edit upserts the row with no cache purge. `core/tts/SarvamTtsClient.kt:58-61,215-219` + `core/tts/DualTtsEngine.kt:199-203` then serve the stale MP3.
- **Fix:**
  1. In the grace-edit lambda (`DonationDetailViewModel.correct`), after `donationRepository.save(...)`: **delete Sarvam clips always** (`deleteDonationCache` covers all speakers + roster + legacy Sarvam names), then `donationRepository.updateAudioStatus(id, NOT_GENERATED)`.
  2. **Human imports are quarantined, not purged (review refinement 2026-09-14):** `donation_{id}.mp3` / `donation_{id}_roster.mp3` are irreplaceable user recordings (WhatsApp), while Sarvam clips regenerate for free. On a grace amount edit (this path is amount-only — `CorrectEntryButton` + `CorrectDialog` amount-only), rename human files to `donation_{id}.mp3.bak-<timestamp>` (same for roster) instead of deleting, so the queue falls through to the correct new figure while the original stays recoverable. Surface a notice ("custom recording kept as backup"). A future UI may offer restore/delete; until then `.bak` files are exempt from playback and pruning. Rationale: a stale human clip of the old amount is still wrong to play, but permanent deletion on a typo fix is disproportionate.
  3. Wiring: ViewModel currently reaches `container.donationRepository` + `container.correctRecord` but not the TTS client. Expose `container.ttsEngine.deleteDonationCache(id)` (thin delegate to `SarvamTtsClient.deleteDonationCache`, which exists at `SarvamTtsClient.kt:170-171` but has zero production callers) and call it from the ViewModel on the IO dispatcher. Never throw out of this path (best-effort, `runCatching`).
  4. Same treatment for any other direct donation-amount/name/pronunciation/honorific edit path (audit `DonationEntryViewModel`, list-sheet edit flows — if amount is only editable via `DonationDetailViewModel.correct`, scope to that one call site and document it).
- **Expense counterpart:** `presentation/expense/ExpenseListViewModel.kt:119-138` grace edit needs no audio purge (expenses are never spoken), but keep the pattern symmetric for future-proofing. No action beyond review.
- **Accept:** edit ₹500 → ₹5,000 inside 5 min → Sarvam `donation_{id}_*.mp3` files gone from `cacheDir/audio/`, human files (if any) renamed to `.bak-<timestamp>` (playable path falls through to the new figure), `audioStatus=NOT_GENERATED`, next play regenerates (or native fallback) with ₹5,000.
- **Verify:** new unit test for `deleteDonationFiles` already exists (`SarvamCacheNamingTest.kt:85`); add ViewModel/use-case-level test asserting grace edit triggers purge + status reset. Manual: airplane-mode counter loop, correct, replay.

### P0-2 Effective-amount domain (the systemic fix)

- **Bug:** `CorrectRecordUseCase.kt:38-51` + `RecordCorrectionUseCase.kt:23-51` append `Correction(originalAmount, deltaAmount)` and intentionally leave the target row untouched. But `domain/usecase/CalculateBalance.kt:25-40`, `domain/usecase/ObserveEventTotalsUseCase.kt:16-20` (+ `presentation/dashboard/DashboardViewModel.kt:37-40`), and `core/tts/AnnouncementTemplates.kt:120-173` all read raw `donation.amount` / `expense.amount`. `presentation/reports/ExportScreen.kt:135-137` + `core/export/ReportExporter.kt:83-92` only *display* corrections as an audit table — totals stay raw. Result: dashboard **and** horn are wrong after the 5-min window.
- **Fix:**
  1. New pure domain helper (e.g. `domain/model/EffectiveAmounts.kt`): `effectiveDonationAmount(donation, correctionsForTarget): Double` = **latest correction by `(createdAt, id)`** — `latest?.let { it.originalAmount + it.deltaAmount } ?: donation.amount`; same for expenses with `targetType` filter. **Why latest, not Σ deltas (review correction 2026-09-14, verified):** every post-grace write today passes `originalAmount = donation.amount` (the untouched base row — see `DonationDetailSheet.kt:151`, `ExpenseListViewModel.kt:123`, `CorrectRecordUseCase.kt:44` where `delta = newAmount - original`). So ₹500 → ₹600 writes `+100`, then → ₹700 writes `+200` off the same base; Σ gives ₹800 (wrong), latest gives ₹700 (right). Latest is also migration-safe under both old (base-original) and fixed (effective-original) writer semantics, while Σ is only correct under fixed semantics. Coerce `<0` to `0` defensively (writer already blocks it in `RecordCorrectionUseCase.kt:34-36`). Document the rule + the 500→600→700 regression case in the file header.
  2. Writer fix (same sprint, prevents future audit-trail confusion): when opening `CorrectDialog`, pass the **current effective amount** as `originalAmount` (not the base row), so new deltas are incremental and each row's `original → effective` trail reads cleanly. Reader (latest-wins) stays correct for old base-original rows with no backfill.
  3. `calculateBalance(donations, expenses, correctionsByTarget)`: add third param (default `emptyMap()` so old call sites compile during migration), sum effective amounts for `countsTowardBalance` / active expenses. `ObserveEventTotalsUseCase` combines `correctionRepository.observeForEvent(eventId)` as a third flow and groups by `targetRecordId`.
  4. Announcement text: `buildDonationAnnouncement` / `buildRosterItemAnnouncement` gain an `effectiveAmount: Double? = null` (or a `corrections: List<Correction> = emptyList()`) param defaulting to raw amount — existing callers keep working; queue VMs pass the effective figure. Bilingual/English paths use the same figure. Non-cash qty/item corrections are out of scope (amount-only per `CorrectDialog.kt:28-43`).
  5. Audio hash: `audioHashFor(text, language, speaker, roster)` (`core/tts/AudioImport.kt:11-16`) stays the hash function, but its `text` input must now be built from the effective amount + a `TTS_TEMPLATE_VERSION` constant (new, e.g. `"v1"` in `AnnouncementTemplates.kt`). Template edit → bump version → new hashes automatically.
- **Accept:** post-grace correction ₹500 → ₹5,000 → dashboard balance +₹4,500, queue speaks ₹5,000 (native immediately, Sarvam after regen), export audit still shows original → effective trail. Multi-correct ₹500 → ₹600 → ₹700 speaks ₹700 (regression test for the Σ-delta trap).
- **Verify:** pure unit tests (latest-wins multi-correction incl. 500→600→700, mixed-target filtering, cancelled exclusion, pledged exclusion, non-cash exclusion, floor at zero). Manual: correct after 6 min, check dashboard + voice + export.

### P0-3 `updateAudioStatus` must not bump `updatedAt`

- **Bug:** `data/local/DonationDao.kt:49-50` does `SET audioStatus, updatedAt=now`, contradicting its comment. Inflated local `updatedAt` defeats `isRemoteNewer` (`data/remote/FirestoreMappers.kt:242-247`, requires `remoteUpdatedAt > localUpdatedAt && version differs`) — a peer's genuine money edit can be silently ignored. It also smears audio timing into ledger timestamps uploaded via `stampForPush`.
- **Fix:** `UPDATE donations SET audioStatus = :status WHERE id = :id` (drop `updatedAt`; drop the `now` param or keep as unused-with-underscore for API stability — prefer changing the DAO + `domain/repository/Repositories.kt:26` + `data/repository/RepositoriesImpl.kt:59-60` + the two callers `presentation/announcement/AnnouncementQueueViewModel.kt:791-793` and `presentation/donation/DonationDetailSheet.kt:307-310`). Audio state is local-only (never synced — `FirestoreMappers.donationToMap` excludes it), so no migration, no sync impact.
- **Accept:** prefetch READY/FAILED flips cause zero `updatedAt` movement (assert in test via DAO round-trip).
- **Verify:** DAO-level test + existing `testDebugUnitTest` green.

### P0-4 Prefetch read amplification + phrase pruning (small, same sprint)

- **Bug A:** `presentation/announcement/AnnouncementQueueViewModel.kt:755-772` does one `readAudioMeta` Firestore get per missing donation (up to 50/pass, only when `cloudSyncEnabled`). Burns Spark 50K reads/day on *deciding not to synthesize*.
  **Fix:** cap guard reads per pass (e.g. 5, in-memory hash cache per session) and document it as a stopgap to be deleted in Phase 3 (bucket `HEAD` replaces it). No behavior change except fewer reads.
- **Bug B:** `core/tts/SarvamTtsClient.kt:191-207` prunes only `donation_*.mp3`; `phrase_*.mp3` (`phraseCacheFileName`, `:225-229`) grows unbounded across presets/events/languages.
  **Fix:** extend `pruneCache` to `phrase_*.mp3` with the same age/count ceiling; keep chime/test files exempt (current behavior).
- **Verify:** unit tests for prune victim selection already exist (`AudioCachePruneTest.kt`); extend with phrase cases. Manual logcat: prefetch pass emits ≤5 meta reads.

### P0-5 Voice lineup: Shubh default, Pooja secondary (ships with Phase 0)

- **Today:** every default resolves to `priya` — `normalizeSarvamSpeaker` fallback (`SarvamTtsClient.kt:18,38`), all `speaker = "priya"` default params (`SarvamTtsClient.kt:58,79,123,136,215,225`; `DualTtsEngine.kt:33,56,86,199,237,249,276,284,306` via `getOrDefault("priya")`), `SessionPrefs.KEY_SPEAKER` default (`SessionPrefs.kt:190`), `VoiceConfig.sarvamSpeaker` (`Voice.kt:29`) + Priya-first labels (`Voice.kt:44-50`), `FirestoreSyncService.readTtsKey` fallback (`:756`), Settings chips Priya-first without Pooja (`AdminSettingsScreen.kt:1568-1573`), queue menu Priya-first without Pooja (`AnnouncementQueueScreen.kt:347-383`, `pendingSpeaker="priya"` at `:227`).
- **Fix:** change all unset/corrupt fallbacks to `shubh`; reorder both pickers to Shubh → Pooja → Priya → Kavitha → Ratan; add the missing Pooja entries (Settings chips + queue menu + `Voice.displayLabel` Pooja branch); keep `normalizeSarvamSpeaker` mappings (already supports `shubh`/`pooja`) and legacy `meera→kavitha` / `arvind→aditya`. **No force-migration:** stored `sarvam_speaker` values stay untouched — only fresh installs and corrupt values land on Shubh.
- **Accept:** fresh install speaks Shubh; Pooja selectable in both pickers; existing Priya installs unchanged until the user switches.
- **Verify:** unit tests for fallback + ordering; manual fresh-install vs upgrade matrix.

### Phase 0 exit gate

```bat
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

Manual: airplane-mode counter loop (cash + non-cash + saree) → grace correct → replay speaks new figure → post-grace correct (6+ min) → dashboard + voice both show effective figure → export shows audit trail → fresh-install voice is Shubh, upgrade keeps stored voice. Two-device sync matrix unchanged (Phase 0 touches no sync protocol).

---

## 3. Phase 1 — Cloudflare account + R2 bucket (no app changes)

Steps (dashboard + Wrangler; ~30 min, one sitting):

1. **Account + billing:** create Cloudflare account → Billing → add card. Expect a temporary **$5 preauth hold** (released by issuer; not a charge). Enable spend alerts / notifications. Do not use a ₹0-limit virtual card for activation — it fails the preauth and R2 stays inaccessible (billing policy: failed preauth suspends usage-based access; data retained ~30 days).
2. **R2 bucket:** Storage & Databases → R2 → create bucket (private by default; do not make public; do not use `r2.dev` public URL for ledger data). Name suggestion: `shankaravam-media` (final name is your call — record it here once chosen). Region: default.
3. **Lifecycle rule:** R2 → bucket → Settings → Lifecycle: prefix `audio/` → delete after **30 days** (festivals last 5–11 days; 2,000 clips ≈ 50 MB; storage stays <200 MB ≈ 2% of 10 GB free). No rule on `receipts/` (audit retention).
4. **Custom domain (optional stretch, NOT a launch blocker — review correction 2026-09-14):** Cache API (`caches.default`) is a no-op on `*.workers.dev`, but at festival scale (~20K reads = 0.2% of the 10M Class-B free tier) caching is an optimization, not a requirement. Ship on `*.workers.dev`; add a custom hostname you own (e.g. `media.<yourdomain>`) later for edge-cache shielding when reads approach millions/mo. Do not buy a domain just to start.
5. **Worker service:** Workers & Pages → create Worker (free plan, 100k req/day). Attach R2 binding in `wrangler.toml`: `r2_buckets = [{ binding = "AUDIO_BUCKET", bucket_name = "shankaravam-media" }]`.
6. **Secrets (dashboard only, never in repo):** `SARVAM_API_KEY` (secret) + `FIREBASE_PROJECT_ID` + `FIREBASE_WEB_API_KEY` (public Web API key — not a secret, used only to call Firestore REST as the user; see §4.1). No Google service-account JSON anywhere. Verify `wrangler secret list` shows only `SARVAM_API_KEY`.
7. **Record in this file:** bucket name, billing alert target (+ custom domain later if added). Nothing proceeds to Phase 2 without bucket + lifecycle + alerts recorded.

---

## 4. Phase 2 — Worker gateway spec (spec only; not deployed in this task)

### 4.1 Auth (closes the open-proxy hole — no service account)

- Client sends Firebase ID token (`Authorization: Bearer <idToken>`; phones already Google-sign-in via `AuthRepository`).
- Worker verifies RS256 via Google JWKS (`https://www.googleapis.com/robot/v1/metadata/x509/securetoken@system.gserviceaccount.com`), checks `exp`/`aud` (project ID) / `iss`.
- Seat check WITHOUT a service account (review refinement 2026-09-14): Worker forwards the **same user ID token** to Firestore REST — `GET /v1/projects/{projectId}/databases/(default)/documents/events/{eventId}/members/{uid}` with `Authorization: Bearer <userToken>`. Firestore `firestore.rules` evaluates **as that user** (own-seat self-read is allowed; `members` read clause covers `request.auth.uid == userId`), so no private-key JSON, no OAuth2 exchange, no rotation. Parse `role`/`status`: writes need `active` + `organizer|global_head`, reads need `active` (any role). Pending/revoked/unknown → 403, Firestore-unreachable → fail-closed 503 (phone falls back to native). Needs only the public Web API key + project ID as Worker vars.
- Why neither shortcut is acceptable: a shared festival header reintroduces the shared-secret leak; **JWKS-only with no seat check is an open proxy** — any Google account can Firebase-auth to the project (auth ≠ membership), and pending/revoked tokens stay valid, so anyone could burn Sarvam quota and upload receipts. Client-side `ForegroundSyncManager` gating is UX only (per `ARCHITECTURE.md`/rules comments, the client is never the security boundary) and is trivially bypassed with curl.

### 4.2 Routes (all behind auth above; all JSON except bytes)

```text
POST /v1/audio/resolve
  body: { eventId, donationId, text (canonical, client-built), language(TELUGU|ENGLISH|BILINGUAL),
          speaker, roster(bool), hash (client-computed), templateVersion }
  → 200 { hash, url (short-lived, same-domain GET), cached }
  → 400 validation | 401/403 auth | 429 quota | 502 sarvam-fail (client falls back to native)

GET  /v1/audio/{hash}.mp3          # same-domain; Cache API when custom domain attached; immutable
PUT  /v1/receipts/{eventId}/{expenseId}.webp   # ≤150 KB enforced on body bytes, content-type image/webp
GET  /v1/receipts/{eventId}/{expenseId}        # 302 to short-lived same-domain URL, never raw R2 URL
```

- **Canonical text stays client-side (review correction 2026-09-14 — no TS Telugu port):** `TeluguNumberFormatter.kt` (100 lines: నూట/వెయ్యి/లక్ష/కోటి + ఒక-రూపాయి rule + paise) + `AnnouncementTemplates.kt` (~235 lines: honorifics, non-cash qty/unit, TE/EN/BI, intro/outro presets) are too intricate to duplicate in TypeScript — one virama/space drift forks every SHA-256. So the phone builds `text` with the existing Kotlin builders from the **effective** amount, computes `hash = SHA-256("language|speaker|roster|text")` (same `audioHashFor`, full hex), and sends `{text, language, speaker, roster, hash, templateVersion}`. Worker **validates, never reconstructs**: recompute hash with WebCrypto over the received fields and constant-time compare to client `hash` (mismatch → 400, blocks hash-poisoning); enforce `text.length ≤ 500` (measure max bilingual template output in tests first; reviewer's 300 is a starting bid, not gospel), enum/shape checks, and speaker normalization parity (the ~20-line `normalizeSarvamSpeaker` map is the ONLY logic ported). `templateVersion` rides along for debugging/analytics — text bytes already carry template identity, so no separate hash input needed.
- **Abuse note (why this is safe):** free-text-with-cap still lets a valid seat holder synthesize arbitrary ≤500-char Telugu. That grants no new power: an active collector can already create arbitrary donations via the app/Firestore and synthesize through the legitimate path. The quota is protected by seat check (§4.1) + daily-new cap + per-seat rate limit, not by restricting vocabulary. POST body (never GET query) keeps donor PII out of edge access logs — log hashes + sizes + codes only.
- **Dedup (write-burn guard):** `HEAD audio/{hash}.mp3` via binding first; hit → return cached URL with zero Class-A write. Miss → Sarvam `POST https://api.sarvam.ai/text-to-speech` (`bulbul:v3`, `te-IN`, normalized speaker, received `text` verbatim) → `PUT audio/{hash}.mp3` → return URL. Concurrent same-hash races resolve to one winner; losers read the winner's object.
- **Caching:** serve with `Cache-Control: public, max-age=31536000, immutable`. Cache API shielding (custom domain) is an optimization for later — at festival scale direct binding reads are ~0.2% of the 10M Class-B free tier, so workers.dev ships first. Worker invocations still count on hits (100k/day budget — festival load is ~low thousands/day).
- **Circuit breaker (Guard-Rail 4, complete):** auth+seat (§4.1) → validate (UUID-shaped ids, no path traversal, enums, `text.length ≤ 500`, hash recompute match) → HEAD → daily-new-synthesis cap (KV/Durable Object counter, e.g. 1,000/day/festival → 429) → Sarvam timeout (~8 s) → on Sarvam fail return 502 so the phone speaks native instantly.

### 4.3 Worker verify (before any app points at it)

`wrangler dev` + curl matrix: unauth→401, pending/revoked→403 (seat check via forwarded token), bad fields→400, hash-mismatch→400, over-long text→400, oversize receipt→413, same-hash twice→second is `cached:true` with one Class-A PUT, Sarvam-down→502. Cache-HIT assertion only if custom domain attached. Record the run output in `SESSION_HANDOFF.md`.

---

## 5. Phase 3 — Android client (receipts first, then audio)

### 5.1 Receipts (smallest bucket win; ships alone)

1. **Schema:** Room `v2 → v3`: `expenses` add `receiptUrl TEXT NULL`. `MIGRATION_2_3` + `AppDatabase` version bump (today `AppDatabase.kt:18-28,52-67`, `exportSchema=false`, `MIGRATION_1_2` precedent). `ExpenseEntity.kt:22` + `domain/model/Expense.kt:17` + `EntityMappers.kt:66-83` carry the field. Local `receiptPath` (WebP file) stays the offline source of truth; `receiptUrl` is display/sync only.
2. **Sync:** `FirestoreMappers.expenseToMap/expenseFromMap` (`FirestoreMappers.kt:73-112`) include `receiptUrl` (never `receiptPath`/bytes — Rule #2 extension). Rules need no change (collector create/update already allowed; `firestore.rules:83-87`).
3. **Upload:** new `data/work/ReceiptUploadWorker.kt` (pattern: `SyncWorker.kt`): CONNECTED constraint, exponential backoff, unique work per `expenseId`, `PUT /v1/receipts/...` with `ReceiptCompressor` output (≤100 KB target, 150 KB hard ceiling), on 200 store `receiptUrl` + mark synced. Never blocks `SaveExpenseUseCase`.
4. **View:** expense list/detail loads via Coil `AsyncImage` with auth header against `GET /v1/receipts/...` (short-lived URL), lazy on expand only. No bucket listing anywhere.
5. **Verify:** airplane-mode receipt attach → online upload → second device sees receipt; kill-network mid-upload retries cleanly.

### 5.2 CAS audio sharing (only after §5.1 is green on two devices)

1. **Dual-read filenames:** `SarvamTtsClient` keeps reading legacy `donation_{id}_{speaker}[_roster].mp3` but all **new** Sarvam writes go to `audio_{hash}.mp3` (hash over client-built effective-amount canonical text per §4.2; `templateVersion` informational). One-release transition, then a follow-up removes legacy writes. Human imports stay in the legacy slot and always win playback (`DualTtsEngine.playBest/playRosterItem` order unchanged).
2. **Network client:** new `data/remote/AudioCloudClient.kt` (OkHttp only — already in `gradle/libs.versions.toml`; no S3 SDK, no APK growth): `resolve(fields) → {hash, bytes|url}`, download to `cacheDir/audio/audio_{hash}.mp3`, `1.5 s`-class timeouts, all `runCatching` → null → native fallback. No `ListObjects`.
3. **Prefetch rewire:** `AnnouncementQueueViewModel.prefetchWhenIdle` stops the `readAudioMeta` N+1 loop (delete P0-4 stopgap) and calls `resolve` per missing hash (bounded concurrency, ≤50/pass, skips rows with human imports). `DualTtsEngine.ensureCached` gains a cloud-first branch (disk → cloud → Sarvam-direct-legacy → native) gated by `voiceEngineMode != OFFLINE_NATIVE` + quota slot (`SessionPrefs.takeSarvamSlot` stays as the device-side backstop).
4. **Quota interplay:** server 1,000 new syntheses/day/festival + device 20/30-min slot both stay. First device pays one synthesis; peers pay zero (HEAD/cache hit). Quota pill (`quotaPillText`, `Voice.kt:95-104`) keeps its current behavior for device-side denials; add a distinct line for server 429.
5. **Verify:** two-device matrix — A generates, B plays same hash with zero Sarvam calls on B; correction → new hash → new voice; offline → native instantly; Sarvam-down → 502 → native with no UI error.

---

## 6. Phase 4 — Remove Sarvam-key distribution (last)

1. `config/tts_settings`: stop writing `sarvamApiKey` (`FirestoreSyncService.writeTtsKey/readTtsKey`, `:752-780`); keep `{ defaultSpeaker }` + add `templateVersion`. Rules `firestore.rules:154-157` stay admin-write; clients ignore any legacy `sarvamApiKey` value if present (do not delete the field server-side until all installs upgrade).
2. `SecureKeyStore` + `SessionPrefs.sarvamApiKey` + F5 `maybeAutoPullVoice` (`FirestoreSyncService.kt:270-296`, 15-min throttle): deprecate to speaker-only pull; `voiceOfflineLocked` semantics stay (offline chip still wins). `AdminSettingsScreen.saveKeyLocally/pushKey/clearKeyLocally` (`:305-360`) becomes speaker picker + "voice served by temple gateway" notice; local key entry removed (or hidden behind a debug flag for one release).
3. Docs: `AGENTS.md` Rule #2 + `ARCHITECTURE.md` §2/§7/§8 rewritten from "zero audio in cloud storage" to the new invariant (§1.5–1.8 of this plan); `firestore.rules` header comment updated.
4. Verify: fresh install with zero key plays gateway audio online + native offline; no `sarvamApiKey` value on any device; decompiled APK contains no cloud secret.

---

## 7. Guard-rails (corrected — replaces the 5-layer sketch)

1. **Billing:** real card + usage alerts (not ₹0-limit virtual — fails the $5 preauth). R2 free: 10 GB / 1M-A / 10M-B / $0 egress.
2. **Lifecycle:** `audio/*` → 30d delete (stays <200 MB). Receipts retained.
3. **Cache API on custom domain (later):** `immutable` + `s-maxage`; hits avoid Class-B (Worker invocations still count). Ships on `workers.dev` first — direct reads are ~0.2% of free tier.
4. **Worker breaker:** auth → validate → HEAD → daily-new cap (KV/DO) → byte-size enforce → Sarvam timeout → 502→native. 150 KB ceiling on receipts, ~30 KB typical audio.
5. **Device net:** disk-first, bounded prefetch, instant native fallback. Any guard trip = robot voice, never silence, never a crash, never a charge spike.

---

## 8. Test + rollout + rollback

- **Unit (each phase):** effective-amount matrix (latest-wins incl. 500→600→700 regression, mixed-target filtering, floor-zero, cancelled/pledged/non-cash), hash stability (same bytes→same hash; any text/speaker/language/roster byte change→different; hash-mismatch rejected), max-template-length measurement (pins the §4.2 length cap), DAO no-`updatedAt` regression, prune phrase + `.bak` exemption cases, `shouldApplySharedKey` speaker-only regression.
- **Manual matrix:** airplane counter loop; grace correct→replay; post-grace correct→dashboard+voice+export; quota-exhausted pill; offline queue on horn; kill-network-mid-upload; two-device approve/revoke unchanged.
- **Cloud matrix:** §4.3 curl suite + R2 metrics (Class-A/B vs free tier) + billing $0 check after 7 days.
- **Rollout:** Phase 0 alone → internal pandal pilot → Phase 1 infra → Phase 3.1 receipts pilot → Phase 3.2 audio pilot (one festival) → Phase 4 key removal. Each arrow is a separate commit + `PROGRESS.md` + `SESSION_HANDOFF.md` update per the maintenance contract.
- **Rollback:** Phase 0: revert commit (no migration except v2→v3 in 5.1 — keep `fallbackToDestructiveMigration` OFF; forward-fix only). Cloud: disable Worker route / remove binding (app falls back to native; legacy Sarvam-direct path kept until Phase 4). R2: lifecycle off + bucket empty (ledger untouched — it never lived there).

---

## 9. What I need from you before implementation (open questions)

1. R2 bucket name (custom domain only if/when you want edge-cache shielding — not needed to launch).
2. Receipt retention: forever (recommended) vs N years?
3. Server daily-new-synthesis cap: 1,000/day/festival OK? Audio resolve text cap: 500 chars OK (after max-template measurement)?
4. Keep legacy `donation_{id}_*.mp3` reads for one release (recommended) or hard cutover?
5. Keep a hidden local Sarvam-key debug override for one release (recommended) or remove immediately in Phase 4?

---

## Appendix A — File touch list (bounded; no other files in scope)

- Phase 0: `DonationDetailViewModel.kt`, `ExpenseListViewModel.kt` (review), `CorrectRecordUseCase.kt` (untouched — callers change), `EffectiveAmounts.kt` (new), `CalculateBalance.kt`, `ObserveEventTotalsUseCase.kt`, `DashboardViewModel.kt`, `AnnouncementTemplates.kt`, `AudioImport.kt` (version const), `DonationDao.kt`, `Repositories.kt`, `RepositoriesImpl.kt`, `DonationDetailSheet.kt`, `AnnouncementQueueViewModel.kt` (read-cap), `SarvamTtsClient.kt` (phrase prune + `.bak` quarantine + `deleteDonationCache` exposure), `AppContainer.kt` (engine delegate), voice lineup: `SessionPrefs.kt`, `Voice.kt`, `FirestoreSyncService.kt` (fallback), `AdminSettingsScreen.kt` (chips), `AnnouncementQueueScreen.kt` (menu).
- Phase 1: Cloudflare dashboard + `wrangler.toml` (new, outside this repo or under `tools/worker/`).
- Phase 2: `tools/worker/src/index.ts` (new) + `tools/worker/wrangler.toml` (new).
- Phase 3: `AppDatabase.kt` (v3 + `MIGRATION_2_3`), `ExpenseEntity.kt`, `Expense.kt`, `EntityMappers.kt`, `FirestoreMappers.kt`, `data/remote/AudioCloudClient.kt` (new), `data/work/ReceiptUploadWorker.kt` (new), `AnnouncementQueueViewModel.kt`, `DualTtsEngine.kt`, `SarvamTtsClient.kt`, `SessionPrefs.kt` (quota/backstop only).
- Phase 4: `FirestoreSyncService.kt` (speaker-only), `SecureKeyStore.kt` / `SessionPrefs.kt` (deprecate key), `AdminSettingsScreen.kt`, `firestore.rules` (comment), `AGENTS.md`, `ARCHITECTURE.md`, `PROGRESS.md`, `SESSION_HANDOFF.md`.

## Appendix B — Cost math (festival scale)

2,000 donations × 25 KB ≈ 50 MB audio + 200 receipts × 100 KB ≈ 20 MB → ~70 MB stored (<1% of 10 GB). 10 volunteers × 2,000 GETs = 20K reads (0.2% of 10M-B) before edge caching; writes ≤2,200 (0.22% of 1M-A). Worker invocations low thousands/day (<5% of 100k/day). Bill: **$0.00** with lifecycle + breaker.

## Appendix C — Top risks

1. Worker seat check via forwarded user token is the hardest code in this plan — budget it, test 401/403/503 matrix first (no service-account JSON; fail-closed).
2. Hash-mismatch false alarms if speaker normalization or text bytes differ by one virama/space — golden-test `audioHashFor` vectors + measure max template length before fixing the §4.2 cap.
3. Post-grace UX: users expect the old row to change; teach "original preserved + correction appended" in `CorrectDialog` copy (already present at `CorrectDialog.kt:52-55`) and in the detail sheet history.
4. `.bak`-quarantined human clips accumulate — a later release needs restore/delete UI; until then they are exempt from playback and pruning by design.

## Appendix D — Review log (2026-09-14 cross-verification)

- Multi-correction Σ-delta → latest-by-`(createdAt, id)` + writer passes effective amount (reviewer catch verified against `DonationDetailSheet.kt:151` + `CorrectRecordUseCase.kt:44`; Σ gives ₹800 for 500→600→700). Genuine critical.
- No TS Telugu port: client sends canonical `text`+`hash`, Worker recomputes/compares + length cap (reviewer catch verified against `TeluguNumberFormatter.kt` 100 lines + `AnnouncementTemplates.kt` ~235 lines). Accepted with hardening (seat check stays, POST-only, 500-char cap after measurement).
- Service-account JSON → forwarded user-token Firestore REST seat check (reviewer friction genuine, skip-seat-check conclusion rejected: auth ≠ membership, `ForegroundSyncManager` is UX-only, revoked/pending tokens stay valid).
- Custom domain → optional stretch (reviewer simplification verified: 20K reads = 0.2% of 10M-B; Cache API no-op on `workers.dev`). Accepted.
- Human imports → quarantine `.bak-<timestamp>` + notice instead of purge (reviewer nuance verified: amount-only path means stale, but recordings are irreplaceable and no restore UI exists). Accepted.
