# R2 Context — New-Session Bootstrap (read this first, then the plan)

> Paste this file + `R2_IMPLEMENTATION_PLAN.md` + `R2_TASKS.md` into a fresh session.
> Plan is DRAFT — do not implement until the user says go. Start at `R2_TASKS.md` T0.1.

## 1. Goal in one paragraph

Festival donation app stays 100% offline-first (Room = truth, save <10 ms), but Sarvam Telugu announcements and receipts become shareable across volunteer phones via private Cloudflare R2 behind a Worker gateway, with content-addressed caching so an unchanged donation costs Sarvam exactly once and any correction auto-invalidates. End state holds zero cloud secrets on phones. First fix the local ledger/voice bugs (Phase 0), then wire the cloud.

## 2. Decisions already locked (don't reopen)

- **Cloudflare R2 + Workers**, card on file ($0 billed; $5 preauth hold at activation). Rejected: direct-phone→bucket, B2, Supabase/Render/Fly, Firebase Storage (Blaze-only since Feb 2026), public buckets/`r2.dev` for ledger data.
- **Private bucket; `workers.dev` ships first**, custom domain later (Cache API is no-op on `workers.dev`, but 20K reads = 0.2% of 10M-B free — optimization, not blocker).
- **Two-tier cache**: Tier 1 disk `audio_{hash}.mp3` (0 ms, airplane-safe) → Tier 2 Worker `HEAD` R2 (hit streams, miss JIT-once). Hash = `SHA-256("language|speaker|roster|text")` full hex over client-built effective-amount text. Unchanged → identical hash → 0 calls; any spoken-byte change → new hash → one synthesis; cancelled → filtered pre-fetch, ledger row kept.
- **Latest-wins corrections** (NOT Σ): reader takes latest `Correction` by `(createdAt, id)` per target; writer passes effective amount into `CorrectDialog`. (Σ gives ₹800 for 500→600→700; latest gives ₹700.)
- **No TS Telugu port**: phone builds text; Worker recomputes/compares hash + `≤500`-char cap (measure max template first) + speaker-normalization parity only.
- **Auth without service account**: JWKS verify + forward the same user ID token to Firestore REST seat read (rules evaluate as that user); fail-closed. JWKS-only is an open proxy (auth ≠ membership) — rejected.
- **Human recordings quarantined** (`.bak-<timestamp>` + notice), never purged; Sarvam clips deleted on grace edits.
- **Voice lineup**: Shubh default (fresh/corrupt), Pooja secondary; order Shubh→Pooja→Priya→Kavitha→Ratan; never overwrite stored values.
- **Lifecycle**: `audio/*` → 30d delete; receipts retained; ledger/corrections never expire.

## 3. Key files map (where everything lives)

- Voice build: `core/tts/AnnouncementTemplates.kt`, `core/tts/TeluguNumberFormatter.kt` (100 lines), `core/tts/AudioImport.kt` (`audioHashFor`), `core/tts/SarvamTtsClient.kt` (disk cache, prune, `deleteDonationCache`), `core/tts/DualTtsEngine.kt` (playback order: human → Sarvam → native).
- Ledger: `domain/usecase/CorrectRecordUseCase.kt` + `RecordCorrectionUseCase.kt`, `domain/model/Correction.kt` (`effectiveAmount`), `domain/usecase/CalculateBalance.kt`, `ObserveEventTotalsUseCase.kt`, `presentation/dashboard/DashboardViewModel.kt`, `presentation/donation/DonationDetailViewModel.kt` + `DonationDetailSheet.kt:151` (dialog wiring), `presentation/expense/ExpenseListViewModel.kt:123`, `presentation/correction/CorrectDialog.kt` (amount-only).
- Sync: `data/remote/FirestoreSyncService.kt` (delta up/down, `readAudioMeta` N+1 at `:755-772`, `readTtsKey/writeTtsKey` at `:752-780`, F5 pull `:270-296`), `data/remote/ForegroundSyncManager.kt` (UX gating only — never the security boundary), `data/remote/AuthRepository.kt` (any Google account can auth), `data/local/DonationDao.kt:49-50` (audio-status bug), `firestore.rules` (members/ledger/config).
- Voice state/UI: `data/local/SessionPrefs.kt:190` (default), `domain/model/Voice.kt` (config + pill), `presentation/settings/AdminSettingsScreen.kt:1568-1573` (chips), `presentation/announcement/AnnouncementQueueScreen.kt:347-383` (menu), `presentation/announcement/AnnouncementQueueViewModel.kt` (prefetch + playback).
- DB: `data/local/AppDatabase.kt` (v2 + `MIGRATION_1_2` precedent), `ExpenseEntity.kt`, `Expense.kt`, `EntityMappers.kt`, `FirestoreMappers.kt`.
- Docs: `AGENTS.md` Rules #1–3, `ARCHITECTURE.md` §2/§7/§8, `OBJECT_STORAGE_BUCKET_REVIEW.md` (why), `R2_IMPLEMENTATION_PLAN.md` (how), `R2_TASKS.md` (order).

## 4. Invariants (breaking any = regression)

1. Room is UI truth; network never blocks save; background prefetch only, instant native fallback.
2. No silent ledger deletes; corrections append-only; cancelled = flag.
3. Human-override playback order preserved; `.bak` exempt from playback/prune until restore/delete UI exists.
4. No secrets in APK/prefs/Firestore-readable-to-volunteers (end state); no per-object public URLs; no `ListObjects` from phones; no S3 SDK in app (OkHttp only).
5. `text.length` cap + hash-recompute compare + seat check on every Worker write path; fail-closed.

## 5. Pitfalls for the implementer

- Virama/space drift forks hashes — golden-test `audioHashFor` vectors; only port `normalizeSarvamSpeaker`.
- `updateAudioStatus` must not move `updatedAt` (breaks `isRemoteNewer`).
- Old corrections carry base-original; reader must stay latest-wins (no backfill).
- `workers.dev` Cache API silently no-ops — don't assert HIT without custom domain.
- ₹0-limit virtual cards fail R2 preauth; need real card + alerts.
- Keep `fallbackToDestructiveMigration` OFF; forward-fix Room migrations only.
- Maintenance contract: each task updates `PROGRESS.md` + `SESSION_HANDOFF.md` with its commit.

## 6. Open questions (need user before Phase 1/2)

Bucket name; receipt retention (forever?); daily-new cap 1,000 OK + text cap 500 OK; legacy filename dual-read one release OK; one-release hidden Sarvam-key debug flag OK. (Custom domain deferred.)

## 7. Resume pointer

Status: plan + tasks + context written; zero implementation done. Next: user picks T0.1 start (recommended) or answers §6 first. Verify each task per `R2_TASKS.md`; stop on first red.
