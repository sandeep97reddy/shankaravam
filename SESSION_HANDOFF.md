# SESSION_HANDOFF — What The Next Session Needs

> Copy-paste starter + context bridge. Update ALL sections at the end of every Group session. The next session starts by reading this file + `PROGRESS.md` + plan §24 + `AGENTS.md`.

## 1. Where We Are
- **Last completed:** `G3 — Events + Donations + Dashboard` ✅ — `assembleDebug` + 14/14 tests green, zero warnings; money-in loop fully offline
- **Next up:** `G4 — Telugu Voice + Announcement Queue` (plan §24: P7 TTS engine + audio routing, P8 queue UI with playback controls)
- **Current branch/status:** usable counter app (create event → save cash/item donations with haptic → live totals → list/filter/sort → detail sheet with preview text); no audio code yet

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
- G3 donations: `Donation{Entry,List,Detail}ViewModel`, `DonationEntryScreen` (cash/item form, tags incl. custom, haptic-on-save), `DonationListScreen` (search + status/tag/sort persisted, keyed animateItem, badges), `DonationDetailSheet` (details + preview + correction history), `AnnouncementPreview.kt` (`buildAnnouncementPreview` — G4 must keep signature)

## 4. Gotchas For Next Session
- Deps cached — G3 verify ~20s incremental; use `.\gradlew.bat` or dist binary at `Temp/opencode/gradle-dist/gradle-8.11.1`
- Room v1 still frozen (no migrations, exportSchema=false) — G4 must NOT change entities; audio status is updated via existing `DonationDao.updateStatus`? NO — audio needs its own query; G4 may ADD a DAO method `updateAudioStatus` (query-only addition, no schema change, safe)
- `buildAnnouncementPreview(donation, eventName)` signature is G4's entry point — replace amount digits with TeluguNumberFormatter, keep function
- Donation rows already carry `audioStatus` (NOT_GENERATED default); G4 updates it to PREPARING/READY/FAILED as Sarvam cache fills
- Material3 quirks hit: ExposedDropdownMenu needs `menuAnchor(MenuAnchorType.PrimaryNotEditable)`; only `List` (not `Add`) has AutoMirrored icon; private `@OptIn` needed per-composable
- Load skill `telugu-tts-audio` in G4 (full file); `jetpack-compose-performance` only for queue-list perf
- G4 needs Retrofit/OkHttp for Sarvam REST — new catalog deps (add `retrofit`, `converter-moshi`/`kotlinx-serialization`? prefer Moshi or manual JSON via org.json to avoid converter weight; skill uses Retrofit — add retrofit2 + converter-gson? keep minimal: retrofit + scalars? Sarvam returns JSON with base64 audio — manual JSONObject parse on `ResponseBody` string is simplest, zero converter dep)

## 5. Paste This To Start The Next Session (G4)
```
Read AGENTS.md, PROGRESS.md, SESSION_HANDOFF.md, and "festival organizer app plan.md" §24.
Load skill: telugu-tts-audio (full file).

Execute ONLY GROUP G4 — Telugu Voice + Announcement Queue (P7 TeluguNumberFormatter + templates + AndroidTtsClient + SarvamTtsClient with cacheDir/audio cache + DualTtsEngine + AudioFocus/RouteDetector + test-audio; P8 queue screen with Play/Pause/Resume/Stop/Replay/Skip/Prev/Next/Repeat + gap setting).
Keep buildAnnouncementPreview() signature; add DonationDao.updateAudioStatus (query-only, no schema change); donation entry must never block on audio; zero Firebase Storage.
End with assembleDebug + testDebugUnitTest passing (add TeluguNumberFormatter + template unit tests). Then update PROGRESS.md + SESSION_HANDOFF.md §1/§3 and stop.
```

## 6. Template For Future Handoffs (overwrite §1/§3/§4/§5 each session)
- §1: last completed group + verify result, next group ID
- §3: list every file created/modified this session
- §4: blockers, schema changes, API key handling, anything the next session must not break
- §5: rewrite paste-prompt for the immediately-next group (G2/G3/G4/G5/G6), naming its phases + skills + verify command
