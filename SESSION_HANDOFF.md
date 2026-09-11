# SESSION_HANDOFF — What The Next Session Needs

> Copy-paste starter + context bridge. Update ALL sections at the end of every Group session. The next session starts by reading this file + `PROGRESS.md` + plan §24 + `AGENTS.md`.

## 1. Where We Are
- **Last completed:** `G5 — Expenses + Ledger Safety + Reports` ✅ — `assembleDebug` + 31/31 tests green, zero warnings. **100% offline app is FEATURE-COMPLETE.**
- **Next up:** `G6 — Optional Cloud Sync + Admin + Hardening` (plan §24: P11 Google Sign-In + QR join + Firestore delta via WorkManager + key sync, P12 admin screens + polish). OPTIONAL — G1–G5 already shippable.
- **Current branch/status:** full festival app offline (events, donations+corrections, expenses+receipts, Telugu/EN announcements, history, PDF/CSV/WhatsApp); Firebase/WorkManager absent

## 2. Key Decisions (carry forward, do not re-litigate)
- Package: `com.durgamma.festival`, Kotlin 2.0+, Compose BOM + Material3, Room 2.6+ w/ KSP, `StateFlow` + `WhileSubscribed(5000)`
- Audio cache path fixed: `cacheDir/audio/donation_{id}.mp3` — never Firebase Storage
- Ledger: `Cancelled` flag, never DELETE; corrections = new row with delta + reason
- Cloud is G6-only; G1–G5 must never import Firebase/WorkManager-network code
- Toolchain (verified): Gradle 8.11.1 + AGP 8.9.2 + Kotlin 2.0.21 + compileSdk/target 36 + minSdk 26 + JVM 17; full Gradle dist at `Temp/opencode/gradle-dist/gradle-8.11.1`, wrapper committed; KSP plugin alias already in catalog (unused until G2)

## 3. Files Touched So Far
- `festival organizer app plan.md` §24 — restructured 5 phases → 6 session-sized Groups (G1–G6), each with files/skills/verify/stop rule
- `PROGRESS.md` — created (status table + pointer); G1 marked ✅
- `SESSION_HANDOFF.md` — this file (created, updated post-G1)
- G1 scaffold: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradlew`/`gradlew.bat` + `gradle/wrapper/*`, `local.properties`, `app/build.gradle.kts`, `app/proguard-rules.pro`, `.gitignore`, `app/.gitignore`
- G1 app code: `MainActivity.kt`, `DurgammaApp.kt`, `core/theme/{Color,Type,Shape,Theme}.kt`, `presentation/{navigation/NavGraph,splash/SplashScreen,dashboard/DashboardScreen,common/{ChakraLoader,TempleAppBar,CurrencyTextField}}.kt`
- G1 res: `AndroidManifest.xml`, `res/{values/{strings,themes,colors},drawable/{ic_sudarshana_chakra,ic_launcher_foreground},mipmap-anydpi-v26/{ic_launcher,ic_launcher_round}}.xml`
- G2 gradle: Room 2.6.1 + KSP applied, `room-runtime/ktx/compiler`, junit + kotlin-test + coroutines-test(1.8.1); versionName `0.2.0-g2`
- G2 data: `data/local/{Event,Donation,Expense,Correction,Activity}Entity.kt`, `Converters.kt` (char-31 separator), `{Event,Donation,Expense,Correction,Activity}Dao.kt`, `AppDatabase.kt` (v1, exportSchema=false), `EntityMappers.kt`
- G2 domain: `domain/model/{Enums,Event,Donation,Expense,Correction,Activity}.kt`, `domain/repository/Repositories.kt`, `domain/usecase/{SaveDonation,SaveExpense,RecordCorrection,CalculateBalance (BalanceSnapshot),ObserveEventTotals}UseCase.kt`
- G2 di/app: `di/AppContainer.kt`, `DurgammaApp` owns container; `core/util/{Outcome,Formatters,AppIds}.kt`
- G2 tests (14 green): `ConvertersTest`, `CalculateBalanceTest`, `SaveDonationUseCaseTest`, `RecordCorrectionUseCaseTest` (fakes included)
- G3 data/di: `data/local/SessionPrefs.kt` (current event + saved sort/status filter), `AppContainer.sessionPrefs`
- G3 shared UI: `presentation/common/{ViewModels.kt (containerViewModel+factory), Derived.kt (keyed derivedTotal)}`, nav routes `DONATION_ENTRY` + `DONATIONS`
- G3 events: `presentation/event/{EventViewModel, EventBanner.kt (CurrentEventBanner + CreateEventDialog)}`
- G3 dashboard: `DashboardViewModel` (event + BalanceSnapshot combine, unsynced refresh), rewritten `DashboardScreen` (pinned banner, totals grid, chips, FAB, G4/G5 placeholder button)
- G3 donations: `Donation{Entry,List,Detail}ViewModel`, `DonationEntryScreen` (cash/item form, tags incl. custom, haptic-on-save), `DonationListScreen` (search + status/tag/sort persisted, keyed animateItem, badges), `DonationDetailSheet` (details + preview + correction history + single-play), `AnnouncementPreview.kt` (delegates to G4 engine)
- G4 gradle: Retrofit 2.11.0 + OkHttp 4.12.0 (explicit — transitive alone didn't resolve); INTERNET + ACCESS_NETWORK_STATE in manifest; versionName still `0.2.0-g2` (bump in G5)
- G4 engine: `core/tts/{TeluguNumberFormatter (5000→ఐదు వేల verified), AnnouncementTemplates (TE/EN/BI), SarvamTtsClient (cacheDir/audio only), AndroidTtsClient (te-IN, AudioAttributes), DualTtsEngine (playBest/prefetch/test-line)}`, `core/audio/{AudioFocusManager (framework, minSdk 26), AudioRouteDetector (SPEAKER/BT/WIRED live)}`, `data/remote/SarvamApiService.kt` (hand-rolled JSON, no converter)
- G4 wiring: `DonationDao.updateAudioStatus` (no version/sync bump — local artifact), `DonationRepository.updateAudioStatus`, `SessionPrefs` += queueGap/queueSort/queueLanguage/sarvamApiKey/sarvamSpeaker, `AppContainer.{audioFocus, routeDetector, ttsEngine}`
- G4 UI: `presentation/announcement/{AnnouncementQueueViewModel (single-job transport, background prefetch, pledged toggle), AnnouncementQueueScreen (route badge + test, key/speaker/language cards, transport, tappable queue)}`, nav `ANNOUNCEMENTS`, dashboard Announce button live
- G4 tests (23 green): + `TeluguNumberFormatterTest` (plan byte-exact examples), `AnnouncementTemplatesTest` (TE/EN/BI/material)
- G5 domain: `CorrectRecordUseCase` (5-min grace direct-edit vs appended Correction, original never touched outside window); `AppContainer.{correctRecord, reportExporter}`, public `appContext`; versionName `0.3.0-g5`
- G5 money-out: `presentation/expense/{ExpenseEntryViewModel (receipt attach state), ExpenseEntryScreen (category chips+custom, DatePickerDialog, payment dropdown, WebP attach), ExpenseListViewModel (search/category/cancel/correct), ExpenseListScreen (badges, confirm-cancel, correct dialog)}`
- G5 safety: `presentation/correction/CorrectDialog.kt` (grace hint vs mandatory reason), donation `DonationDetailViewModel.correct` + Fix button, expense cancel keeps row + logs RECORD_CANCELLED
- G5 local files: `core/export/{ReceiptCompressor (~100KB WEBP_LOSSY/R), ReportContent (pure CSV/WhatsApp/line builders), ReportExporter (PdfDocument A4 + FileProvider share)}`, `res/xml/file_provider_paths.xml`, manifest FileProvider
- G5 screens: `presentation/history/ActivityFeedScreen.kt` (VM folded in), `presentation/reports/ExportScreen.kt` (PDF/CSV/WhatsApp + last-file), nav `EXPENSES/EXPENSE_ENTRY/HISTORY/REPORTS`, dashboard Expenses/History/Reports buttons live
- G5 tests (31 green): + `CorrectRecordUseCaseTest` (grace/correction/reject paths), `ReportContentTest` (CSV quoting, bilingual summary)

## 4. Gotchas For Next Session
- Deps cached — G5 verify ~11s incremental; use `.\gradlew.bat` or dist binary at `Temp/opencode/gradle-dist/gradle-8.11.1`
- Room v1 STILL frozen (no migrations, exportSchema=false) — G6 sync must map Firestore docs onto EXISTING entities; no column changes. First schema change (if ever) ships with a migration + exportSchema=true post-G6 decision
- `combine` wider than 3 flows does NOT resolve here — only ≤3-flow combines (ExpenseListViewModel chains member `.combine` calls; queue VM nests). Keep this pattern in G6 workers/observers
- Api quirks hit: `PdfDocument.PageInfo.Builder.create()` (not build()); `Icons.AutoMirrored.Filled.ReceiptLong` IS the current one (Add is the exception with no mirrored variant)
- Sarvam key still in plain SessionPrefs (`sarvamApiKey`) — G6 MUST migrate to EncryptedSharedPreferences (needs `androidx.security:security-crypto` dep) + `/config/tts_settings` Firestore sync (head-write/member-read rules)
- Receipts at `filesDir/receipts/*.webp`, reports staged at `cacheDir/reports/*`, audio at `cacheDir/audio/*` — G6 uploads receipts only (~100KB), NEVER audio
- Sync design pointers: `pendingSync()` one-shots already exist on donation/expense DAOs; corrections/activity are append-only (upload + never update); conflicts → NEEDS_REVIEW/CONFLICT status, never last-write-wins on money; WorkManager needs `androidx.work:work-runtime-ktx` + Auth (`firebase-auth`, `firebase-firestore`, Google Sign-In via Credential Manager) — all G6-only deps
- No TTS/audio skill needed in G6; `jetpack-compose-performance` only if lists jank

## 5. Paste This To Start The Next Session (G6 — LAST, OPTIONAL)
```
Read AGENTS.md, PROGRESS.md, SESSION_HANDOFF.md, and "festival organizer app plan.md" §24.
No skill load required (Firebase/WorkManager greenfield wiring).

Execute ONLY GROUP G6 — Optional Cloud Sync + Admin (P11 lazy Google Sign-In in Settings only + event QR/share-code join + approval + Firestore DELTA sync via WorkManager exponential backoff + Sarvam key migration to EncryptedSharedPreferences ↔ /config/tts_settings with head-write/member-read firestore.rules; P12 role gating global_head/organizer/member + sensitive-action re-auth + close-event + 120Hz jank pass + cold-start re-check).
HARD RULES: fresh install works with zero login (Rule #1 untouched); Room stays UI source of truth; audio never leaves the device; receipts ≤100KB only; no entity changes without a migration; only ≤3-flow combines.
End with assembleDebug + testDebugUnitTest passing + full AGENTS.md §7 checklist. Then update PROGRESS.md + SESSION_HANDOFF.md §1/§3, tag release, and stop.
```

## 6. Template For Future Handoffs (overwrite §1/§3/§4/§5 each session)
- §1: last completed group + verify result, next group ID
- §3: list every file created/modified this session
- §4: blockers, schema changes, API key handling, anything the next session must not break
- §5: rewrite paste-prompt for the immediately-next group (G2/G3/G4/G5/G6), naming its phases + skills + verify command
