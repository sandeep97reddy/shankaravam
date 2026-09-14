# R2 Tasks — Ordered Execution Checklist (DO NOT EXECUTE YET)

> Companion to `R2_IMPLEMENTATION_PLAN.md` (the why + how). This file is the run order.
> Status convention: `[ ]` todo · `[x]` done. One task per commit. Stop on first red verify.
> Plan status: DRAFT — no task below has been executed.

## Phase 0 — Local fixes (offline, no cloud)

- [ ] **T0.1 Grace purge + quarantine (donations)**
  Files: `DonationDetailViewModel.kt`, `SarvamTtsClient.kt`, `DualTtsEngine.kt`, `AppContainer.kt`, `DonationDetailSheet.kt` (notice).
  Do: Sarvam clips deleted always; human `donation_{id}[_roster].mp3` → `.bak-<timestamp>`; `audioStatus=NOT_GENERATED`; best-effort `runCatching`.
  Accept: ₹500→₹5,000 in-window → Sarvam gone, human `.bak` kept, replay speaks ₹5,000.
  Verify: unit (purge + quarantine + `.bak` exempt from playback/prune) + airplane-mode manual.

- [ ] **T0.2 Effective amounts (latest-wins)**
  Files: NEW `EffectiveAmounts.kt`, `CalculateBalance.kt`, `ObserveEventTotalsUseCase.kt`, `DashboardViewModel.kt` (via use-case), `AnnouncementTemplates.kt`, `DonationDetailSheet.kt:151` + `ExpenseListViewModel.kt:123` (pass effective as dialog `originalAmount`).
  Do: latest-by-`(createdAt, id)` per target; coerce `<0`→`0`; balance + voice use it; writer passes effective.
  Accept: 500→600→700 speaks/shows ₹700; dashboard + voice + export agree.
  Verify: unit matrix (latest-wins regression, mixed targets, cancelled/pledged/non-cash, floor-zero) + 6-min manual.

- [ ] **T0.3 `updateAudioStatus` stops touching `updatedAt`**
  Files: `DonationDao.kt`, `Repositories.kt`, `RepositoriesImpl.kt`, callers (`AnnouncementQueueViewModel.kt`, `DonationDetailSheet.kt`).
  Accept: READY/FAILED flips move zero `updatedAt`.
  Verify: DAO round-trip test + full unit suite green.

- [ ] **T0.4 Prefetch read cap + phrase prune**
  Files: `AnnouncementQueueViewModel.kt` (≤5 meta reads/pass + session hash cache), `SarvamTtsClient.kt` (prune `phrase_*.mp3`, keep chime/test exempt).
  Verify: unit (phrase victims) + logcat ≤5 meta reads.

- [ ] **T0.5 Voice lineup: Shubh default, Pooja secondary**
  Files: `SarvamTtsClient.kt` (fallbacks), `DualTtsEngine.kt` (`getOrDefault`), `SessionPrefs.kt` (default), `Voice.kt` (default + Pooja label), `FirestoreSyncService.kt` (fallback), `AdminSettingsScreen.kt` (chips + Pooja), `AnnouncementQueueScreen.kt` (menu + Pooja + pending default).
  Do: unset/corrupt → `shubh`; order Shubh→Pooja→Priya→Kavitha→Ratan everywhere; never overwrite stored values.
  Accept: fresh install = Shubh; Pooja in both pickers; upgraded Priya install stays Priya.
  Verify: unit (fallback + order) + fresh-vs-upgrade manual.

- [ ] **T0.exit Gate**
  `.\gradlew.bat testDebugUnitTest` + `assembleDebug` green; airplane counter loop; update `PROGRESS.md` + `SESSION_HANDOFF.md`.

## Phase 1 — Cloudflare infra (no app changes)

- [ ] **T1.1 Account + billing + alerts** (card; survive $5 preauth; no ₹0-limit virtual).
- [ ] **T1.2 Private R2 bucket** (name recorded; never public; no `r2.dev` for ledger data).
- [ ] **T1.3 Lifecycle `audio/` → 30d delete** (receipts retained).
- [ ] **T1.4 Worker service + R2 binding** (`AUDIO_BUCKET`); secrets: `SARVAM_API_KEY` only + public `FIREBASE_PROJECT_ID`/`FIREBASE_WEB_API_KEY` vars. No service-account JSON.
- [ ] **T1.exit** Record bucket + lifecycle + alerts in plan. (Custom domain deferred — optimization only.)

## Phase 2 — Worker gateway (spec → code → curl matrix)

- [ ] **T2.1 Auth**: JWKS verify + forwarded user-token Firestore seat REST read; fail-closed 503; 401/403 matrix.
- [ ] **T2.2 `POST /v1/audio/resolve`**: client text+hash; server hash-recompute compare, ≤500 chars (after max-template measurement), speaker-normalization parity only; HEAD→Sarvam-JIT-once→PUT; `immutable` headers.
- [ ] **T2.3 Receipts `PUT`/`GET`**: ≤150 KB body-bytes enforce, `image/webp`, UUID-shaped ids, short-lived URLs, never raw R2.
- [ ] **T2.4 Breaker + quotas**: daily-new cap (KV/DO ~1,000/day/festival → 429), Sarvam ~8 s timeout → 502.
- [ ] **T2.exit** Full curl matrix green (§4.3); output pasted to `SESSION_HANDOFF.md`.

## Phase 3 — Android client (receipts, then audio)

- [ ] **T3.1 Receipts**: Room v2→v3 (`receiptUrl`), `MIGRATION_2_3`, mappers, `ReceiptUploadWorker`, Coil signed-URL view, two-device verify.
- [ ] **T3.2 CAS audio**: dual-read filenames, `AudioCloudClient` (OkHttp, 1.5 s class, no S3 SDK), prefetch rewire (delete N+1 loop), quota-pill 429 line, two-device + offline + Sarvam-down matrix.

## Phase 4 — Key removal (last)

- [ ] **T4.1 Speaker-only `tts_settings`** (`defaultSpeaker` + `templateVersion`; ignore legacy key; rules comment).
- [ ] **T4.2 Deprecate key path** (`SecureKeyStore`/`SessionPrefs`/F5 pull → speaker-only; Settings becomes picker + gateway notice; optional one-release debug flag).
- [ ] **T4.3 Docs**: `AGENTS.md` Rule #2 + `ARCHITECTURE.md` §2/§7/§8 + `firestore.rules` header + `PROGRESS.md` + `SESSION_HANDOFF.md`.
- [ ] **T4.exit** Fresh install zero-key plays gateway online / native offline; APK holds no secret.
