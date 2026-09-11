# PROGRESS — Durgamma Festival App Build Tracker

> Single source of truth for build progress. Update this file at the END of every Group session, in the same task as the code changes. Stale rows mislead the next session.

## Current Pointer
- **Status:** G1 done ✅ — `app-debug.apk` (18.2 MB) builds clean
- **Next session:** `G2 — Offline Data Core`
- **Plan:** `festival organizer app plan.md` §24 (6 groups, 1 group = 1 session)
- **Handoff details:** see `SESSION_HANDOFF.md`

## Group Status
| Group | Scope | Status | Verify | Notes |
|-------|-------|--------|--------|-------|
| G1 | Foundation Shell: scaffold + theme + Chakra splash + nav | ✅ done | `assembleDebug` green, APK 18.2 MB, splash 1400ms → dashboard | Gradle 8.11.1 + AGP 8.9.2 + Kotlin 2.0.21 |
| G2 | Offline Data Core: Room entities/DAOs + domain/repos/use cases | 🟡 next | `./gradlew testDebugUnitTest`, <10ms Room write → Flow | No UI, no network |
| G3 | Events + Donations + Dashboard (first usable app) | ⬜ todo | Airplane-mode counter test, pledged excluded | Money-in loop |
| G4 | Telugu Voice + Announcement Queue | ⬜ todo | Offline TTS instant, Sarvam cached in `cacheDir/audio/`, BT ducking | Load `telugu-tts-audio` skill |
| G5 | Expenses + Ledger Safety + Reports (offline complete) | ⬜ todo | Correction preserves original, PDF/CSV/WhatsApp offline | 100% offline done here |
| G6 | Optional Cloud Sync + Admin + Hardening (last) | ⬜ todo | Zero-login fresh install still works, delta sync + backoff | Only networked code |

Legend: ⬜ todo · 🟡 in_progress · ✅ done · ⏭️ skipped (G6 may ship without)

## Non-Negotiables (check every group)
- [ ] Offline-first: fresh install works with zero login, Room = UI source of truth
- [ ] Free tier: zero audio in Firebase Storage, delta sync only, receipts WebP ~100KB
- [ ] Non-destructive ledger: no silent deletes, corrections preserve original + delta + reason
- [ ] Perf: keyed LazyColumn + animateItem + derivedStateOf, entry <10ms, splash ≤1.5s

## How To Update (end of each session)
1. Flip the finished Group row to ✅, next row to 🟡.
2. Move `Current Pointer` to the next Group.
3. Fill `SESSION_HANDOFF.md` (what built, files touched, decisions, next prompt).
4. Commit code + both tracking files together.
