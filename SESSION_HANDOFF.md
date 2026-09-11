# SESSION_HANDOFF — What The Next Session Needs

> Copy-paste starter + context bridge. Update ALL sections at the end of every Group session. The next session starts by reading this file + `PROGRESS.md` + plan §24 + `AGENTS.md`.

## 1. Where We Are
- **Last completed:** `G4 — Telugu Voice + Announcement Queue` ✅ — `assembleDebug` + 23/23 tests green, zero warnings
- **Next up:** `G5 — Expenses + Ledger Safety + Reports` (plan §24: P9 expense entry/list + receipts + corrections UI + activity feed, P10 PDF/CSV/WhatsApp export)
- **Current branch/status:** full money-in + voice app (donations → Telugu/English announcements over phone/BT speaker, cloud-voice prefetch, single-play in detail sheet); no expense UI yet

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

## 4. Gotchas For Next Session
- Deps cached — G4 verify ~4s incremental; use `.\gradlew.bat` or dist binary at `Temp/opencode/gradle-dist/gradle-8.11.1`
- Room v1 STILL frozen (no migrations) — G5 must NOT change entities either; expense/correction/activity tables already exist, receipt = local path string in `receiptPath`
- Toolchain lesson: `kotlinx.coroutines.flow.combine` wider than 3 flows does NOT resolve here — G5 must use only ≤3-flow combines (see AnnouncementQueueViewModel nesting pattern); also add explicit OkHttp dep for any new Square lib
- Sarvam key lives in plain SessionPrefs as DEV holder — G6 migrates to EncryptedSharedPreferences; G5 must not build on it
- Prefetch runs inside AnnouncementQueueViewModel init (collects uiState on IO) — G5 queue-adjacent work must not duplicate prefetch loops
- TTS singletons live in AppContainer (ttsEngine/routeDetector/audioFocus); detector.start() is owned by the queue VM — don't double-start
- G5 needs: camera/gallery picker (ActivityResultContracts, no new dep), Bitmap→WebP compress (~100KB) via `Bitmap.compress(WEBP_LOSSY)`, PDF via framework `PdfDocument` (no dep), CSV via plain file IO, share via ACTION_SEND intent (WhatsApp text). Zero new Gradle deps expected
- Load skill `jetpack-compose-performance` in G5 (expense list perf); no TTS skill needed

## 5. Paste This To Start The Next Session (G5)
```
Read AGENTS.md, PROGRESS.md, SESSION_HANDOFF.md, and "festival organizer app plan.md" §24.
Load skill: jetpack-compose-performance (lists + derivedStateOf sections).

Execute ONLY GROUP G5 — Expenses + Ledger Safety + Reports (P9 ExpenseEntry/List + WebP receipt + grace-window edit vs RecordCorrection flow + cancel (never delete) + ActivityFeed; P10 local PDF summary + CSV export + WhatsApp share text + non-cash dashboard breakdown).
Consume SaveExpenseUseCase + RecordCorrectionUseCase + existing Correction/Activity repos; framework PdfDocument + ACTION_SEND only, no new deps; only ≤3-flow combines.
End with assembleDebug + testDebugUnitTest passing (add receipt-compress + export-format unit tests where JVM-feasible). Then update PROGRESS.md + SESSION_HANDOFF.md §1/§3 and stop.
```

## 6. Template For Future Handoffs (overwrite §1/§3/§4/§5 each session)
- §1: last completed group + verify result, next group ID
- §3: list every file created/modified this session
- §4: blockers, schema changes, API key handling, anything the next session must not break
- §5: rewrite paste-prompt for the immediately-next group (G2/G3/G4/G5/G6), naming its phases + skills + verify command
