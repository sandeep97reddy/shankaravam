# SESSION_HANDOFF — What The Next Session Needs

> Copy-paste starter + context bridge. Update ALL sections at the end of every Group session. The next session starts by reading this file + `PROGRESS.md` + plan §24 + `AGENTS.md`.

## 1. Where We Are
- **Last completed:** `G2 — Offline Data Core` ✅ — `assembleDebug` + `testDebugUnitTest` (14/14, 0 failures) green, zero warnings
- **Next up:** `G3 — Events + Donations + Dashboard` (plan §24: P5 event mgmt + live dashboard, P6 donation entry/list/detail/filter)
- **Current branch/status:** Room v1 (5 tables) + 5 repos + 5 use cases + manual AppContainer wired in DurgammaApp; UI still G1 placeholder

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

## 4. Gotchas For Next Session
- Deps cached — G2 full verify took ~3 min; use `.\gradlew.bat` or the extracted dist binary at `Temp/opencode/gradle-dist/gradle-8.11.1`
- Room v1 has NO migrations and exportSchema=false — G3 must NOT change entity columns; new queries only (DAO additions are safe). Schema changes wait for post-G5
- DAOs expose newest-first Flows only — G3 sorting/filtering should be added as DAO queries (preferred) or in-memory for small lists
- `SaveDonationUseCase` defaults: status=RECEIVED, audio=NOT_GENERATED, sync=PENDING_UPLOAD; validation rejects blank donor / negative / cash-zero-without-item
- `RecordCorrectionUseCase` never touches the target row (invariant) — G5 builds its UI on this
- KSP+Room stable on Kotlin 2.0.21; do not bump Kotlin/Room versions mid-build
- Load skill `jetpack-compose-performance` in G3 (keyed LazyColumn + animateItem + derivedStateOf); do NOT pull `telugu-tts-audio` until G4

## 5. Paste This To Start The Next Session (G3)
```
Read AGENTS.md, PROGRESS.md, SESSION_HANDOFF.md, and "festival organizer app plan.md" §24.
Load skill: jetpack-compose-performance (lists + derivedStateOf sections).

Execute ONLY GROUP G3 — Events + Donations + Dashboard (P5 event selector/creator + live BalanceSnapshot dashboard, P6 donation entry/list/detail/sort-filter).
Consume AppContainer repos + SaveDonationUseCase + ObserveEventTotalsUseCase; single uiState StateFlow per ViewModel; keyed LazyColumn + animateItem.
Do not start G4 (no TTS/audio code; announcement preview TEXT only).
End with assembleDebug passing + airplane-mode counter walkthrough. Then update PROGRESS.md + SESSION_HANDOFF.md §1/§3 and stop.
```

## 6. Template For Future Handoffs (overwrite §1/§3/§4/§5 each session)
- §1: last completed group + verify result, next group ID
- §3: list every file created/modified this session
- §4: blockers, schema changes, API key handling, anything the next session must not break
- §5: rewrite paste-prompt for the immediately-next group (G2/G3/G4/G5/G6), naming its phases + skills + verify command
