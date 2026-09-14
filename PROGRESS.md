# PROGRESS — ShankaRavam App Build Tracker

> Single source of truth for build progress. Update this file at the END of every Group session, in the same task as the code changes. Stale rows mislead the next session.

## Current Pointer
- **Status:** ALL GROUPS DONE ✅ + Fix Track F1–F6 ALL DONE ✅ + **R2 Media-Sharing Phase 0–3 DONE ✅ (uncommitted)** — local ledger/voice fixes, Worker gateway code, Android receipts + CAS audio; `assembleDebug` + 31 suites green
- **Next session:** user deploys Worker (`tools/worker/README.md`), runs `verify.mjs`, pastes gateway URL in Settings → Cloud Sync, two-device pilot; then Phase 4 (Sarvam-key removal)
- **Plan:** `festival organizer app plan.md` 📎24 + `SYNC_VOICE_FIX_PLAN.md` (F1–F6) + `R2_IMPLEMENTATION_PLAN.md` / `R2_TASKS.md` / `R2_CONTEXT.md` (Phase 0–3 done, Phase 4 + cloud deploy pending)
- **Handoff details:** see `SESSION_HANDOFF.md`

## Sync/Voice Fix Track — Phase 6 (F6) ✅ done 13-09-2026
- `NativeVoiceInfo` + `pickBestTeluguVoice`: automatic high-quality network voice selection when voice unconfigured; fallback to highest-quality embedded voice; exposed `🌐 Network` vs `💾 Offline` badges to UI dropdowns.
- `AndroidTtsClient`: runtime offline retry fallback (switches to embedded voice on network failure during speech so playback never drops).
- Intro/outro phrase caching (`phraseCacheFileName`, `getOrGeneratePhraseAudio`, `ensurePhraseCached`): Sarvam intro/outro clips pre-generated and cached per-speaker so roster mode speaks in a single cohesive voice without cloud audio storage.
- `DualTtsEngine.playPhraseBest`: checks phrase cache first, falls back to native voice, seamless chime wrap.
- Queue VM & Screen: pre-caches intro & outro in background prefetch loop, wires `playPhraseBest` in sequence & advance, loads and displays network/offline badges in VoiceSettingsCard.
- **Tests:** +`VoiceSelectionAndPhraseTest` (9 tests: network priority, embedded fallback, displayName/badge, safe phrase caching, collision prevention)
- **Verify:** `assembleDebug` + `testDebugUnitTest` 131/131 green

## Sync/Voice Fix Track — Phase 5 (F5) ✅ done 13-09-2026
- `maybeAutoPullVoice` (service, throttled 15 min, silent, never fails sync): runs after every sync Done + Settings entry + sign-in; `shouldApplySharedKey` matrix (blank/same → no-op; new → apply, flip to cloud unless explicitly locked offline)
- Explicit offline lock: Offline chip / key-clear sets it, cloud choice clears it; manual Pull bypasses it
- Cloud Voice card pill: "☁️ Shared voice: Shubh (synced …)" vs "📱 Key on this device only"
- **Tests:** +`VoiceKeyApplyTest` (5)
- **Verify:** `assembleDebug` + `testDebugUnitTest` 122/122 green

## Sync/Voice Fix Track — Phase 4 (F4) ✅ done 13-09-2026
- `pickSyncCounter` (counter → Google name → omit): join/presence/publish stamp human names; head seat carries identity (no more "Unknown counter"); backfills on next touch, no migration
- `myRole()` revoked-first: revoked admins keep zero head powers
- Ledger maps emit `deviceTag` (last-4) only — full install UUID never leaves Room
- **Tests:** +`SyncCounterTest` (4), mapper privacy assertions
- **Verify:** `assembleDebug` + `testDebugUnitTest` 117/117 green

## Sync/Voice Fix Track — Phase 3 (F3) ✅ done 13-09-2026
- `SyncOutcome` Done/Blocked/Failed: pending/revoked/sign-out show human copy, never retry (worker hot-loop gone); network still retries
- Seat-first `syncEvent`: approval/revoke learned before ledger touch (deadlock gone); creator bootstrap preserved when seat missing; header merge-write; per-row push-stamp (`stampForPush` + `markSynced` mirror) + 120 s read fudge; corrections download added (`correctionFromMap`, `createdAt` cursor)
- `ForegroundSyncManager`: app-lifecycle ledger + seat listeners (foreground only, watermark-filtered, shared ingest, presence never from callbacks); immediate upload on save (direct `syncEvent`, not Worker queue); live pending/revoked chip in Team section
- **Tests:** +`stampForPush` lift/keep tests
- **Verify:** `assembleDebug` + `testDebugUnitTest` 113/113 green

## Sync/Voice Fix Track — Phase 2 (F2) ✅ done 13-09-2026
- `QrInvite.kt`: gallery-pick pipeline (bounded load → `RGBLuminanceSource` decode → `parseJoinCode`, zero permission), off-thread QR render, WhatsApp-capable PNG share via `cacheDir/share/` FileProvider
- `InviteCard` async QR (spinner meanwhile, jank gone) + Share button · `JoinCard` gallery-pick button · Team section wires both; `CloudSyncViewModel.info()` for pick results
- **Tests:** +`QrCodecTest` pure-JVM zxing round-trip (encode→pixels→decode→parse)
- **Verify:** `assembleDebug` + `testDebugUnitTest` 111/111 green

## Sync/Voice Fix Track — Phase 1 (F1 + P0 safety) ✅ done 13-09-2026
- **P0** event-scoped `pendingSyncForEvent(eventId)` in all 3 DAOs (sync uses scoped; global stays display-only for the badge) · `share/` FileProvider path (unblocks F2 WhatsApp share)
- **F1** canonical Team & Cloud Sync section in gear (`TeamSyncSection`, sign-in gated, Join with zero events — no dummy duplicates) · `parseJoinCode` (raw/QR/URL) + 3 tests · join auto-enables sync + silent empty-dummy cleanup · last-sync diagnostics line · `CLOUD_SYNC` route redirects to gear `expandTeam`
- **Verify:** `assembleDebug` + `testDebugUnitTest` 109/109 green

## Group Status
| Group | Scope | Status | Verify | Notes |
|-------|-------|--------|--------|-------|
| G1 | Foundation Shell: scaffold + theme + Chakra splash + nav | ✅ done | `assembleDebug` green, APK 18.2 MB, splash 1400ms → dashboard | Gradle 8.11.1 + AGP 8.9.2 + Kotlin 2.0.21 |
| G2 | Offline Data Core: Room entities/DAOs + domain/repos/use cases | ✅ done | `assembleDebug` + `testDebugUnitTest` 14/14 green, KSP Room codegen | Manual AppContainer DI, no UI yet |
| G3 | Events + Donations + Dashboard (first usable app) | ✅ done | `assembleDebug` + 14/14 tests green, zero warnings; counter loop works offline | Money-in loop live |
| G4 | Telugu Voice + Announcement Queue | ✅ done | `assembleDebug` + 23/23 tests green, zero warnings | Native te-IN + Sarvam `cacheDir/audio/` |
| G5 | Expenses + Ledger Safety + Reports (offline complete) | ✅ done | `assembleDebug` + 31/31 tests green, zero warnings | PDF/CSV/WhatsApp all local |
| G6 | Optional Cloud Sync + Admin + Hardening (last) | ✅ done | `assembleDebug` + 42/42 tests green; offline default untouched | Live on `google-services.json` drop-in |

Legend: ⬜ todo · 🟡 in_progress · ✅ done · ⏭️ skipped (G6 may ship without)

## Admin Head Track (ADMIN_HEAD_PLAN.md S1–S5) — ✅ done 12-09-2026
- **Status:** all 5 phases done — `assembleDebug` + `testDebugUnitTest` 69/69 green
- **S1** rules hardening (Google-pinned admin, escalation/overwrite/TTS seals, least-privilege `roleOf`) · **S2** foundation (`AdminConfig`, `CloudMember`, merge-safe mappers, scoped head override, auth-owned flag) · **S3** membership flows (admin auto-elevate, presence touch, unified roster, `setMemberRole`) · **S4** head-only team directory + Verified badges + admin-only Publish · **S5** `AdminConfigTest` (12 tests) + this pointer
- **Next:** `firebase deploy --only firestore:rules` → two-device manual matrix (ADMIN_HEAD_PLAN App.B / S5.3)

## Temple Enhancements Batch (6 features) — ✅ done 12-09-2026
- **Status:** implemented + `assembleDebug` + `testDebugUnitTest` 85/85 green (no rules redeploy needed)
- **#1** revoked banner (`common/AccessBanner.kt`, Dashboard + DonationEntry, EN/TE strings) · **#2** 10-day expiring + head-closeable invite codes (`isCodeLive`, grandfathering, `closeShareCode`, InviteCard button; enforcement client-side, stated in code) · **#3** repeat-last CUT — already shipped as `replay()` + transport button · **#4** temple bell chime (pure WAV synth via existing `playFile` path, toggle in Settings & Voice, pause/stop-safe) · **#5** WhatsApp receipt (no gothram — never collected; UUID short-ref; DetailSheet button; entry-screen placement skipped — screen pops on save) · **#6** 60 s duplicate guard (same donor+gift+status, cash + non-cash, confirm dialog)
- **Tests:** +`ShareCodesTest` expiry matrix, +`TempleChimeTest` (WAV header/decay/bake-once), +`ReceiptFormatterTest`, +`DuplicateGuardTest`
- **Next:** on-device pass — chime through the horn speaker, WhatsApp share from the ledger, revoke→banner, close-code→rejoin-rejected

## Review Patch Batch (P0–P2) — ✅ done 12-09-2026
- **Status:** `assembleDebug` + `testDebugUnitTest` 86/86 green
- **P0** manifest `<package com.whatsapp/w4b>` visibility (detection actually works on API 30+ now) · reactive `codeTick` in CloudSync VM (InviteCard flips live on publish AND close)
- **P1** direct WhatsApp launch with untargeted-chooser fallback + Activity-aware NEW_TASK · ThreadLocal receipt date format · clock-skew guard (`age < 0` never flags, seconds never negative) · `DonationDao.latestForEvent` O(1) wired through repo + fake
- **P2** focus held across chime→speech handoff (`abandonOnDone=false`; errors always abandon) · roster items skip the chime (intro/outro/full announcements keep it)
- **Tests:** +clock-skew boundary tests in `DuplicateGuardTest`
- **Next:** on-device — WhatsApp direct-open, no ducking bounce on the horn, invite publish/close flips without leaving the screen

## Non-Negotiables (final verification, G6)
- [x] Offline-first: fresh install works with zero login, Room = UI source of truth (Firebase guarded, sync off by default)
- [x] Free tier: zero audio in Firebase Storage (maps exclude it), delta sync only, receipts WebP ~100KB
- [x] Non-destructive ledger: no silent deletes (no @Delete; rules deny delete), corrections preserve original + delta + reason
- [x] Perf: keyed LazyColumn + animateItem + derivedStateOf, entry <10ms, splash 1400ms ≤1.5s

## Identity/Cards/Roster Batch (IDENTITY_ROLES_REVIEW.md §6) — ✅ done 13-09-2026
- **Status:** implemented + `assembleDebug` + `testDebugUnitTest` 90/90 green (86 existing + 4 new `MemberNameTest`)
- **P1** expandable History + Expense cards (tap → `AnimatedVisibility` block: full collector, event name, paid-by/vendor/pronunciation, recorded-at, sync status; donation sheet untouched per verdict Q1)
- **P2** `resolveMemberName()` + UID-leak fixes (ApprovalsCard, TeamRow subtitle, account fallback, `EVENT_CLOSED` actor → `attributionName()`)
- **Q2** CLOSED-event hard block (entry VMs `isEventClosed` + save refusal + `ClosedEventBanner` EN/TE + disabled save)
- **Q3** read-only roster for active collectors (`canViewRoster`; role buttons stay head-only via `manageEnabled`)
- **Left as documented gaps:** full P3 status-aware gating, server-side closed immutability, full-deviceId in ledger maps
- **Next:** on-device pass — expand/collapse at 60fps, closed banner on a closed festival, collector sees read-only team tab

## Voice Unification Track (P1–P3) — ✅ done 13-09-2026
- **Status:** implemented + `testDebugUnitTest` green (P1 96/96 → P2 102/102 → P3 green; no rules redeploy needed)
- **P1 engine/cache:** speaker-aware Sarvam cache (`donation_{id}_{speaker}[_roster].mp3`; ghost-voice fix), human imports in speaker-agnostic slot as intentional override, one-time legacy rename migration (guarded by `audioCacheV2Migrated`), live `speakerProvider`, prefix-scan delete/prune, stale `10/45min` comment fix
- **P2 reactivity/UI:** `VoiceConfig` single source of truth (mode-first shared label — RC3 fix), reactive prefs flows + key-version flow (RC1 fix), explicit `OFFLINE_NATIVE`/`SARVAM_CLOUD` mode honored by engine + prefetch gate, key dialog removed from queue (secrets in Settings only), live native-voice reset, Remove-key action in Settings
- **P3 quota transparency (RC4):** quota pill (`⚠️ Cloud quota reached (20/20)… until h:mm`) in transport card instead of silent mid-queue flips; waiting count stays visible on quota hit; pill clears when a slot succeeds or the window rolls; Sarvam test discloses it spends 1 of 20 calls
- **User-visible on upgrade:** old cached voices are attributed to the active speaker once (no re-download storm); picking another voice generates fresh audio within quota, offline voice covers the rest with the pill explaining why
- **Tests:** +`SarvamCacheNamingTest` speaker/migration/deletion, +`VoiceConfigTest` label + pill text
- **Fix batch (control-flow trace, 5/5):** prefetch now observes speaker/mode/key flows (speaker switch + fresh key retrigger generation); human imports play before Sarvam in full mode (share sheet aligned); quota pill clears on offline switch; device-voice picks no longer hijack engine mode; card summaries are mode-aware (cloud names speaker, offline+key says "Key Saved")
- **Next:** on-device pass — switch Priya→Shubh mid-queue, exhaust 20 calls and watch the pill, offline-mode queue with horn speaker

## Access Hardening Batch (P3 status gate + creator gates + server invite binding) — ✅ done 14-09-2026
- **P3 status-aware money gate:** `AccessPolicy.canWriteMoney(role, status)` + `writeBlockedReason()` (sync-matching copy); wired into donation/expense entry `save()` + donation correct + expense correct/cancel; `canAddExpense` fixed to `role != MEMBER` (was `true` for viewers — contradicted `canWriteLedger`). Offline/local events unaffected (status defaults ACTIVE).
- **Creator-or-admin UI gates:** `CloudSyncViewModel.canManageTeam()/canManageCode()/canApproveNow()` — close-invite + promote/revoke hidden from non-creator heads (rules deny them), approvals require active-collector; `setMemberRole/approve/closeCode` re-gated in VM with clear notices.
- **Server-side invite binding:** `memberToMap(viaCode)` stamped on join; `firestore.rules` `isLiveCode()` verifies code→event match + active status + Timestamp expiry on every pending/member create; `viaCode` added to self-touch keys; `expiresAt` now written as Firestore `Timestamp` (legacy Longs tolerated client-side, grandfathered server-side). Rules dry-run compiles.
- **R2 lifecycle codified:** `tools/worker/r2-lifecycle.json` (`audio/` → 30d, receipts retained) + README apply/verify steps (matches the dashboard rule already set).
- **Tests:** +`canWriteMoney`/`writeBlockedReason`/unknown-status tests, +`member_via_code` mapper test; `member_can_only_view` corrected.
- **Verify:** `assembleDebug` + `testDebugUnitTest` 175/175 green + `firebase deploy --only firestore:rules --dry-run` compiles. **Needs real deploy:** `firebase deploy --only firestore:rules` (joins from old builds without `viaCode` will be denied until updated — republish codes after deploy).

## R2 Media-Sharing Track — Phase 0–3 ✅ done 14-09-2026 (uncommitted)
- **Phase 0 local (T0.1–T0.5):** grace-edit audio purge + human `.bak` quarantine; effective amounts latest-wins (balance + voice + export + writers); `updateAudioStatus` timestamp drop; prefetch read-cap + phrase prune (cap later deleted); Shubh default / Pooja secondary lineup
- **Phase 2 Worker (`tools/worker/`, uncommitted):** `src/index.ts` + `src/lib.ts` — JWKS verify, forwarded-token Firestore seat read (fail-closed, no service account), `POST /v1/audio/resolve` (hash-recompute compare, 500-char cap, HEAD→Sarvam-JIT-once→PUT, 1,000/day cap via optional KV else per-isolate), receipt PUT/GET (150 KB + WebP-magic), auth-gated GETs (no signed-URL expiry machinery). Verify: 15 vitest + `tsc` clean; live matrix via `verify.mjs` after user deploy
- **Phase 3 Android (uncommitted):** Room v2→v3 (`expenses.receiptUrl`, `MIGRATION_2_3`), mappers carry path-only (bytes never leave device), `attach/applyReceiptUrl` DAOs + receipt merge branch in `ingestExpenseDocs` (never CONFLICT, never re-upload loop), `ReceiptUploadWorker` + save-time schedule + Coil `SharedReceiptImage` on expand + gateway URL card in Cloud Sync; CAS `audio_{hash}.mp3` dual-read (new writes CAS-only), engine cloud-first branch, prefetch rewire (N+1 loop deleted), server-429 pill; cross-language hash vector pinned both sides (`27b47756…eb1d`); worst bilingual measured 357 chars → 500 cap kept (300 would 400 legit rows)
- **Verify:** `assembleDebug` + `testDebugUnitTest` 31 suites green (incl. `EffectiveAmountsTest` 12, `GraceAudioInvalidationTest` 7, `VoiceLineupTest` 4, `AudioStatusWriteContractTest` 2, `MaxTemplateLengthTest` 2, receipt mapper test)
- **User-side before pilot:** add `FIREBASE_PROJECT_ID`/`FIREBASE_WEB_API_KEY` vars (values in `tools/worker/README.md`), `wrangler deploy`, `verify.mjs` run, paste URL in app Settings, two-device matrix (§5.2.5). Phase 4 key-removal only after audio proven on two devices
- **Deviations (deliberate, in `tools/worker/README.md`):** auth-gated GETs instead of short-lived URLs; receipt GET streams bytes (no 302); race convergence via re-HEAD; `?eventId=` carries opaque UUID only
- **Post-build audit fixes F1–F4 (14-09-2026, uncommitted):** F1 post-grace purge + `legacyCacheCovers()` bypass in engine (both play paths) and prefetch filter (heals peer-corrected rows too); F2 conditional FAILED (only when cloud work was attempted — no spurious FAILED on keyless installs); F3 WhatsApp receipt prints effective amount; F4 receipt view distinguishes unconfigured/signed-out/loading. Suite green after each

## How To Update (end of each session)
1. Flip the finished Group row to ✅, next row to 🟡.
2. Move `Current Pointer` to the next Group.
3. Fill `SESSION_HANDOFF.md` (what built, files touched, decisions, next prompt).
4. Commit code + both tracking files together.
