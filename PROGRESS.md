# PROGRESS — ShankaRavam App Build Tracker

> Single source of truth for build progress. Update this file at the END of every Group session, in the same task as the code changes. Stale rows mislead the next session.

## Current Pointer
- **Status:** ALL GROUPS DONE ✅ — v1.0.0-g6, APK 25.1 MB, 42/42 tests, zero warnings
- **Next session:** none scheduled — app is shippable offline; cloud goes live on `google-services.json` + `firestore.rules` deploy
- **Plan:** `festival organizer app plan.md` §24 (6 groups, 1 group = 1 session)
- **Handoff details:** see `SESSION_HANDOFF.md`

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

## How To Update (end of each session)
1. Flip the finished Group row to ✅, next row to 🟡.
2. Move `Current Pointer` to the next Group.
3. Fill `SESSION_HANDOFF.md` (what built, files touched, decisions, next prompt).
4. Commit code + both tracking files together.
