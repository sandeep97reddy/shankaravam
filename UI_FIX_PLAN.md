# ShankaRavam — UI / TTS / Sync Fix Plan (2026-09-12)

Source: user field report (APK 11-09, rebuilt with google-services.json). Decisions locked below. Build in phases P1→P4.

## Locked decisions
1. Language lives in **Admin (Settings & Voice)** only. Header keeps gear → Admin. CloudSync stays for account/invite/sync.
2. **No login wall at counter.** One-time counter-name setup (`Counter 2 - Ramesh`) stamped offline on every row (`addedBy` + `deviceId`). Sign-in required only for sync / approve / publish-key / close-event.
3. **Announcer-only Sarvam.** Native te-IN default everywhere. Head publishes key once to `config/tts_settings`; announcer device (Bluetooth horn) pulls it. Add WhatsApp/file import → `cacheDir/audio/donation_{id}.mp3` so shared clips play with zero player change.

## P1 — Crash + header + labels ✅ DONE 12-09-2026 (assembleDebug + 49/49 tests green, APK 13:48)
- [x] 1.1 Header: remove `FilterChip` language from `presentation/common/TempleAppBar.kt:112-137`. Keep gear. `DashboardScreen.kt:111-120` drops `onToggleLanguage`; `DashboardViewModel.toggleLanguage` stays for Admin use.
- [ ] 1.2 Nav: gear → `ADMIN`, keep separate Sync entry → `CLOUD_SYNC`. Touch `presentation/navigation/NavGraph.kt:49-58`, `DashboardScreen.kt:86-96,193-198` (split `onSettings`/`onSync`, split `onAddExpense`/`onViewExpenses`).
- [ ] 1.3 Labels: `core/i18n/AppStrings.kt:131-132,224-225` `+ Add Donation` → `Donate`, `- Add Expense` → `Expense` (TE: `విరాళం` / `ఖర్చు`). Update `DualActionHeader:392-452` icons (keep saffron/maroon).
- [ ] 1.4 Expense nav: dashboard Expense button → `EXPENSE_ENTRY` direct; hub Expenses tile → `EXPENSES` list.
- [ ] 1.5 Event year: `presentation/event/EventBanner.kt:222-226,279` + `AppStrings.kt:119,212` remove `2026` → `Vinayaka Chavithi` / `వినాయక చవితి`.
- [ ] 1.6 Announce crash hardening (+ verified extras):
  - `TeluguNumberFormatter`: replace `require()` with safe fallback; guard `NaN/Infinite/negative` amount + negative qty → `formatInr` fallback, never throw.
  - `SaveDonationUseCase`: reject `!amount.isFinite()` (today `NaN < 0 == false` passes validation, crashes formatter later).
  - `AnnouncementTemplates`: safe `wordsForAmount/Number` wrappers with `runCatching` fallback.
  - `DualTtsEngine.kt:48,65-88,114-158`: wrap `onStatus`, `build*`, `audioFocus.request`, `MediaPlayer.start/resume/isPlaying/pause` in `runCatching`; always fall back to native test-line-safe text.
  - `AudioFocusManager.kt`: wrap `request/abandon` in `runCatching`.
  - `DonationDetailSheet.kt:100,185-191`: safe preview + safe click; gate Play label on `nativeReady`.
  - `AnnouncementQueueViewModel.kt:255-262,418-431`: safe `pause()`/`resume()` (`native.stop` + `resumePlayback` guarded).
- Verify: `.\gradlew.bat clean assembleDebug` + `testDebugUnitTest` green; manual: airplane-mode donate → announce plays native; NaN/negative/qty edge no crash.

## P2 — Attribution (who did what, offline, no Room migration) ✅ DONE 12-09-2026 (assembleDebug + 49/49 green, APK 13:56)
- [ ] 2.1 `SessionPrefs`: add `counterName` plain pref.
- [ ] 2.2 Save path stamps `addedBy = counterName.ifBlank { Counter-last4(deviceId) }` — touch use cases + entry VMs (currently `addedBy=""`). Never blank.
- [ ] 2.3 Show `Collector:` from `addedBy` (already guarded in most lists).
- [ ] 2.4 Mapper fix WITHOUT migration: `DonationEntity` has no `deviceId` column (`AppDatabase` v2) — do NOT add column. Change `donationToMap(row, deviceId)` signature, pass `prefs.deviceId` from `FirestoreSyncService`. Fixes `"deviceId" to e.addedBy` bug.
- Verify: offline set name once → donate/expense → History shows name → Firestore docs carry `addedBy` + real `deviceId`.

## P3 — Sync + head (event header + rules deadlock fix) ✅ DONE 12-09-2026 (assembleDebug + 50/50 green, APK 14:05)
- [ ] 3.1 Empty-states in `CloudSyncScreen`: `event==null` → `Create event first`; `user==null` → `Sign in first`; sync result toast.
- [ ] 3.2 Event upload: upsert `events/{eventId}` in `syncEvent` (today only donations/expenses/corrections — Device B never sees event). `publishShareCode` must also write event header, not just `codes/{code}`.
- [ ] 3.3 Join pull: Device B inserts pulled event into Room `eventDao` on join (else dashboard shows "No Event Selected").
- [ ] 3.4 Head stamp: `EventViewModel.createEvent` sets `globalHeadId = auth.uid ?: deviceId` + `creatorId/deviceId`; auto `setMyRole(global_head)` for creator. Role string must be exactly `global_head` (`roleOf` is uppercase `valueOf`, `head` falls back to ORGANIZER).
- [ ] 3.5 Rules deadlock: `members` create only allows `pending/member`, so creator can never become collector. Add `isEventCreator(eventId)` (`get(events).data.globalHeadId == auth.uid`) and allow creator self-register as `active/global_head`; extend `isCollector` with creator OR. Tighten `config/tts_settings` write to head-only. Keep `allow delete: false` — no global delete button.
- Verify: head creates → publishes → joiner requests → head approves → both sync → rows + event visible both sides.

## P4 — Voice saver (announcer-only + import, SAF first) ✅ DONE 12-09-2026 (assembleDebug + 57/57 green, APK 14:37)
- [x] 4.1 Key flow: head `pushKey` once → `config/tts_settings`; members `pullKey` (read-only, active members). Speaker passed through (`prefs.sarvamSpeaker`, was always `meera`). Key field debounced (Save/Clear buttons, no per-keystroke Keystore encrypt).
- [x] 4.2 Prefetch: one-at-a-time sequential, SIMPLE circuit-breaker (first Sarvam/network failure breaks the pass; next queue change retries). Local budget 10 calls / 45 min / device (`RateWindow`, unit-tested); over budget → silent native fallback.
- [x] 4.3 Cache ceiling 300 files / 20 days, inline in prefetch pass (no new Worker), currently-playing excluded. Any mp3 bytes play — human-recorded and Sarvam clips identical to the player.
- [x] 4.4 Imports (no ACTION_SEND, no popup): sheet-level single import → full-clip slot; queue-level batch roster import (multi-pick, donor-name match incl. Telugu pronunciation, leftovers reported never force-attached). Roster playback falls back roster → full → native.
- [x] 4.5 Re-fetch guard (silent, no popup): merge-stamped `audioHash/audioGeneratedAt/audioGeneratedBy` (no updatedAt/version touch, no Room change); same-hash within 30 min → skip call. Gated on sync-enabled.
- [x] 4.6 Firestore audio: **rejected** — 1MiB/doc cap, needs Storage + Functions (not Spark-free), violates AGENTS.md Rule #2. Local cache only.
- Verify: announcer pulls key once → prefetch fills → airplane-mode queue plays cached; imported WhatsApp mp3 plays via same button.

## Files index (all under app/src/main/java/com/shankaravam/festival/)
TempleAppBar, DashboardScreen/ViewModel, NavGraph, AppStrings, EventBanner, AdminSettingsScreen, CloudSyncScreen, DonationEntry/List/Detail, ExpenseEntry/List, SessionPrefs, SaveDonation/SaveExpense/CorrectRecordUseCase, FirestoreMappers/SyncService/SyncWorker/AuthRepository, DualTtsEngine/AndroidTtsClient/SarvamTtsClient/AnnouncementTemplates/TeluguNumberFormatter, AudioFocusManager, firestore.rules, AndroidManifest.xml

## Global verify per phase
```
.\gradlew.bat clean assembleDebug
.\gradlew.bat testDebugUnitTest
```
Manual: offline donate <10ms + haptic; announce native fallback; sync two devices; no silent deletes; splash ≤1.5s.
