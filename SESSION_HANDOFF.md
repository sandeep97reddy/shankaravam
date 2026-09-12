# SESSION_HANDOFF — What The Next Session Needs

> Copy-paste starter + context bridge. Update ALL sections at the end of every Group session. The next session starts by reading this file + `PROGRESS.md` + plan §24 + `AGENTS.md`.

## 1. Where We Are
- **Admin Head track S1–S5 (uncommitted):** master-admin whitelist (`sandeepreddyr97@gmail.com`, Google-pinned in rules), member escalation / code overwrite / TTS hijack seals, least-privilege `roleOf`, merge-safe member docs + presence, unified roster + `setMemberRole`, head-only Connected Team card + Verified badges + admin-only Publish, `AdminConfigTest` (12 tests). Verify: `assembleDebug` + 69/69 tests green. Next: `firebase deploy --only firestore:rules` → two-device manual matrix.
- **Post-G6 audio iteration (uncommitted):** natural Telugu numbers (నూట/వందల), roster mode + presets, PNG chakra logo, native voice picker — plus 6 review fixes: secure-key single source (VM + queue card → `secureKeys`), roster-aware Sarvam cache (`donation_{id}_roster.mp3`, cache-first playback, roster-aware prefetch with status persistence), voice/speed applied at TTS init, reactive voice list, dead `tint` param removed. Verify: `assembleDebug` + 46/46 tests green, zero warnings.
- **Last completed:** `G6 — Optional Cloud Sync + Admin + Hardening` ✅ — `assembleDebug` + 42/42 tests green, zero warnings, APK 25.1 MB, v1.0.0-g6. **ALL GROUPS DONE — BUILD COMPLETE.**
- **Next up:** nothing scheduled. To go live with teams: create Firebase (Spark) project → drop in `google-services.json` → `firebase deploy --only firestore:rules` → sign in via Settings → enable sync. No code changes needed.
- **Current branch/status:** shippable festival app (offline-first + optional cloud); committed as `ecc4e74` + tag `v1.0.0-g6` (not pushed — say the word for `git push origin master --tags`)

## 2. Key Decisions (carry forward, do not re-litigate)
- Package: `com.shankaravam.festival`, Kotlin 2.0+, Compose BOM + Material3, Room 2.6+ w/ KSP, `StateFlow` + `WhileSubscribed(5000)`
- Audio cache path fixed: `cacheDir/audio/donation_{id}.mp3` — never Firebase Storage
- Ledger: `Cancelled` flag, never DELETE; corrections = new row with delta + reason
- Cloud is G6-only; G1–G5 must never import Firebase/WorkManager-network code
- Toolchain (verified): Gradle 8.11.1 + AGP 8.9.2 + Kotlin 2.0.21 + compileSdk/target 36 + minSdk 26 + JVM 17; full Gradle dist at `Temp/opencode/gradle-dist/gradle-8.11.1`, wrapper committed; KSP plugin alias already in catalog (unused until G2)

## 3. Files Touched So Far
- `festival organizer app plan.md` §24 — restructured 5 phases → 6 session-sized Groups (G1–G6), each with files/skills/verify/stop rule
- `PROGRESS.md` — created (status table + pointer); G1 marked ✅
- `SESSION_HANDOFF.md` — this file (created, updated post-G1)
- G1 scaffold: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradlew`/`gradlew.bat` + `gradle/wrapper/*`, `local.properties`, `app/build.gradle.kts`, `app/proguard-rules.pro`, `.gitignore`, `app/.gitignore`
- G1 app code: `MainActivity.kt`, `ShankaRavamApp.kt`, `core/theme/{Color,Type,Shape,Theme}.kt`, `presentation/{navigation/NavGraph,splash/SplashScreen,dashboard/DashboardScreen,common/{ChakraLoader,TempleAppBar,CurrencyTextField}}.kt`
- G1 res: `AndroidManifest.xml`, `res/{values/{strings,themes,colors},drawable/{ic_sudarshana_chakra,ic_launcher_foreground},mipmap-anydpi-v26/{ic_launcher,ic_launcher_round}}.xml`
- G2 gradle: Room 2.6.1 + KSP applied, `room-runtime/ktx/compiler`, junit + kotlin-test + coroutines-test(1.8.1); versionName `0.2.0-g2`
- G2 data: `data/local/{Event,Donation,Expense,Correction,Activity}Entity.kt`, `Converters.kt` (char-31 separator), `{Event,Donation,Expense,Correction,Activity}Dao.kt`, `AppDatabase.kt` (v1, exportSchema=false), `EntityMappers.kt`
- G2 domain: `domain/model/{Enums,Event,Donation,Expense,Correction,Activity}.kt`, `domain/repository/Repositories.kt`, `domain/usecase/{SaveDonation,SaveExpense,RecordCorrection,CalculateBalance (BalanceSnapshot),ObserveEventTotals}UseCase.kt`
- G2 di/app: `di/AppContainer.kt`, `ShankaRavamApp` owns container; `core/util/{Outcome,Formatters,AppIds}.kt`
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
- G6 gradle: Firebase BOM 33.7.0 (auth+firestore), work-runtime-ktx 2.9.0, security-crypto, play-services-auth, zxing core, coroutines-play-services; NO google-services plugin (offline build stays green without json); versionName `1.0.0-g6`
- G6 data: `data/local/SecureKeyStore.kt` (encrypted Sarvam key + one-way G4 migration), `SessionPrefs` += deviceId/cloudSyncEnabled/lastSync/myRole/shareCode maps; DAO `updateSyncState` (donation/expense/correction, no version bump) + `CorrectionDao.pendingSync`; repo `updateSyncState`/`pendingSync` wired
- G6 cloud: `data/remote/{AuthRepository (guarded classic Google Sign-In), FirestoreSyncService (delta up/down, conflict→CONFLICT flag, codes/members/tts-key), FirestoreMappers (Long-millis, audio excluded)}`, `data/work/SyncWorker.kt` (15-min + on-demand, no-op when disabled/signed-out)
- G6 domain: `domain/model/Membership.kt` (UserRole/MemberStatus/AccessPolicy) + `core/util/ShareCodes.kt` (6-char unambiguous codes)
- G6 UI: `presentation/settings/{CloudSyncScreen (account, sync toggle, sync-now, QR invite + join-by-code + approvals), AdminSettingsScreen (secure key pull/push, role label, close-event)}`, nav `CLOUD_SYNC/ADMIN`, dashboard Sync button; role gating in donation-correct + expense correct/cancel VMs
- G6 root: `firestore.rules` (collectors-write/members-read, append-only corrections, join-request flow, tts_settings)
- G6 tests (42 green): + `AccessPolicyTest`, `FirestoreMappersTest` (round-trip + conflict rule), `ShareCodesTest`
- G6 deviations (documented, deliberate): join by typed code + QR display (no camera-scan dep); classic sign-in intent (Credential Manager 1.3.0 moved GoogleId classes); timestamps as Long millis (not Timestamp); receipts NOT auto-uploaded yet (paths local-only; upload is a 10-line worker addition once Storage is provisioned)

## 4. Gotchas For Future Work
- Deps cached — verify ~15s incremental; use `.\gradlew.bat` or dist binary at `Temp/opencode/gradle-dist/gradle-8.11.1`
- Room v1 STILL frozen (no migrations, exportSchema=false) — first schema change ships with a migration
- `combine` wider than 3 flows does NOT resolve here — only ≤3-flow combines everywhere
- AuthRepository uses classic GoogleSignIn intent (`@file:Suppress DEPRECATION`); migrate to Credential Manager only after pinning a version with stable googleid classes
- `androidx.credentials` catalog entries are unused (kept for that future migration)
- Cloud goes live with: Firebase Spark project → `google-services.json` in `app/` → `firebase deploy --only firestore:rules` → sign in → enable sync → publish invite
- Suggest: `git add -A; git commit -m "..."; git tag v1.0.0-g6` — NOT done (needs your explicit word per repo rules)

## 5. No Further Groups — Build Complete ✅
```
All 6 groups done. v1.0.0-g6: assembleDebug + 42/42 tests green, zero warnings, APK 25.1 MB.
Future sessions: read AGENTS.md, PROGRESS.md, SESSION_HANDOFF.md and state the new feature.
```

## 6. Template For Future Handoffs (overwrite §1/§3/§4/§5 each session)
- §1: last completed group + verify result, next group ID
- §3: list every file created/modified this session
- §4: blockers, schema changes, API key handling, anything the next session must not break
- §5: rewrite paste-prompt for the immediately-next group (G2/G3/G4/G5/G6), naming its phases + skills + verify command
