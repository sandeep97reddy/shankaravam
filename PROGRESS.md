# PROGRESS — Durgamma Festival App Build Tracker

> Single source of truth for build progress. Update this file at the END of every Group session, in the same task as the code changes. Stale rows mislead the next session.

## Current Pointer
- **Status:** G5 done ✅ — offline app COMPLETE (expenses + ledger safety + reports)
- **Next session:** `G6 — Optional Cloud Sync + Admin` (last, optional)
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
| G6 | Optional Cloud Sync + Admin + Hardening (last) | 🟡 next | Zero-login fresh install still works, delta sync + backoff | Only networked code |

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
