# SESSION_HANDOFF — What The Next Session Needs

> Copy-paste starter + context bridge. Update ALL sections at the end of every Group session. The next session starts by reading this file + `PROGRESS.md` + plan §24 + `AGENTS.md`.

## 1. Where We Are
- **Last completed:** `G1 — Foundation Shell` ✅ — `assembleDebug` BUILD SUCCESSFUL, `app-debug.apk` 18.2 MB
- **Next up:** `G2 — Offline Data Core` (plan §24: P3 Room layer + P4 domain/repos/use cases)
- **Current branch/status:** scaffold + theme + splash + placeholder dashboard all compile; no Room/Firebase code yet

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

## 4. Gotchas For Next Session
- Slow network made first Gradle/dependency download take ~50 min total; all deps now cached in `~/.gradle` — G2 builds will be ~2–3 min incremental; prefer `.\gradlew.bat` (wrapper dist auto-downloads once) or the extracted dist binary
- AAPT requires launcher icons: adaptive `mipmap-anydpi-v26` + foreground/background now exist — never delete; pre-API-26 fallback not needed (minSdk 26)
- KSP + Room deps NOT yet in `app/build.gradle.kts` — G2 must add `alias(libs.plugins.ksp)`, `room-runtime/compiler/ktx` to catalog + module
- Splash budget constant: `SPLASH_TIMEOUT_MS = 1400L` in `SplashScreen.kt` — G3+ must keep ≤1500ms
- Load skill `jetpack-compose-performance` in G2 (Room-as-Source-of-Truth section); do NOT pull `telugu-tts-audio` until G4

## 5. Paste This To Start The Next Session (G2)
```
Read AGENTS.md, PROGRESS.md, SESSION_HANDOFF.md, and "festival organizer app plan.md" §24.
Load skill: jetpack-compose-performance (Room + StateFlow sections).

Execute ONLY GROUP G2 — Offline Data Core (P3 Room entities/DAOs/DB + P4 domain/repos/use cases).
Do not start G3 (no UI changes beyond wiring-free ViewModel-ready repos).
End with assembleDebug + testDebugUnitTest passing. Then update PROGRESS.md + SESSION_HANDOFF.md §1/§3 and stop.
```

## 6. Template For Future Handoffs (overwrite §1/§3/§4/§5 each session)
- §1: last completed group + verify result, next group ID
- §3: list every file created/modified this session
- §4: blockers, schema changes, API key handling, anything the next session must not break
- §5: rewrite paste-prompt for the immediately-next group (G2/G3/G4/G5/G6), naming its phases + skills + verify command
