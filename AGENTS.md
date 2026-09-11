# AGENTS.md — Guidelines for AI Coding Agents

Welcome! You are working on **Durgamma / Festival Organizer App**, a high-performance, fluid Native Android application built for Indian temples and festival committees (Vinayaka Chavithi, Sri Rama Navami, Dasara, Hanuman Jayanthi, etc.).

Organizers, collectors, and volunteers use this app in high-stress, noisy environments (temple counters, festival pandals) to record cash and material donations, broadcast Telugu announcements through Bluetooth horn speakers/amplifiers, track expenses, and view real-time balances.

---

## 1. Non-Negotiable Architectural Principles

### 1.1. Local-First & Zero Barrier (Rule #1)
- **The app must work 100% offline out-of-the-box on first launch.**
- NEVER block the user with mandatory login, network calls, or Firebase setup on app opening.
- **Room Database is the SINGLE source of truth for the UI.**
- Tapping "Save Donation" must commit to Room DB instantly ($<10$ ms) and update the UI with a haptic. Network synchronization via WorkManager is strictly a background concern.

### 1.2. 100% Free Tier Safeguards (Rule #2)
- **Zero Audio in Cloud Storage**: NEVER upload or stream generated TTS audio from Firebase Storage. All generated speech (.mp3/.wav) is cached strictly in local device storage (`context.cacheDir/audio/`).
- **Delta Sync Only**: Cloud Firestore is only used when the user explicitly enables "Cloud Sync". Sync only delta records. Never query full collections repeatedly.
- **Receipt Images**: Compress all expense receipts to WebP (~100 KB) before optional cloud upload.

### 1.3. Non-Destructive Financial Ledger (Rule #3)
- **Never silently delete or overwrite past financial records.**
- When an amount error occurs, record a **Correction Transaction** preserving the original entry, delta amount, author, timestamp, and reason.
- Voided records are marked `Cancelled`, never deleted from the SQLite database.

---

## 2. Tech Stack & Dependencies

- **Language**: Kotlin 2.0+
- **UI Toolkit**: Jetpack Compose (Material 3)
- **Architecture**: MVVM / MVI with Clean Architecture (`data`, `domain`, `presentation`)
- **Persistence**: Room Database 2.6+ with SQLite, Kotlin Coroutines, and `StateFlow`
- **Background Tasks**: Jetpack WorkManager 2.9+
- **Audio Output**: Android Native `AudioManager` with `AudioFocusRequest` (Bluetooth amplifier/speaker routing)
- **Text-to-Speech**:
  - Offline: Android Native `TextToSpeech` (`Locale("te", "IN")`)
  - Cloud: Sarvam AI Telugu REST API (configurable API key stored in encrypted preferences / Firestore)
- **Cloud Backend (Optional)**: Firebase Auth (Google Sign-In) + Cloud Firestore (Spark Tier)

---

## 3. Visual Identity & Temple Aesthetics

### 3.1. Color Tokens
```kotlin
val DeepMaroon = Color(0xFF4A0E17)    // Sandalwood / Sacred Maroon (Primary Dark)
val TempleSaffron = Color(0xFFE65100) // Vibrant Temple Saffron (Primary Light)
val TempleGold = Color(0xFFFFD700)    // Sacred Gold (Accent / Highlights)
val DivineAmber = Color(0xFFFFA000)   // Secondary Accent
val WarmIvory = Color(0xFFFFFDF7)     // Surface Light
val SacredCharcoal = Color(0xFF1A120B)// Surface Dark
```

### 3.2. Launch Screen (Vishnu Sudarshana Chakra)
- **Mandatory**: On app launch, display a lightweight, vector-rendered spinning **Vishnu Sudarshana Chakra**.
- Duration: Maximum **1.2 to 1.5 seconds**. Snappy and responsive.
- Animation: Smooth continuous rotation easing with subtle radiant aura/glow.
- Immediate transition to the Event Dashboard or Event Selector.

---

## 4. Audio & Text-to-Speech (TTS) Guidelines

### 4.1. Audio Routing
- Audio playback must use `AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY` or `USAGE_MEDIA` with `STREAM_MUSIC`.
- Manage `AudioFocusRequestCompat`: Request transient audio focus with ducking before speaking, and abandon focus once the utterance completes.
- Display the active audio route: `Phone Speaker`, `Bluetooth Amplifier/Device`, or `Wired Headset`.

### 4.2. Telugu Template Engine
- Handle cash amounts with proper Telugu words (e.g., 5000 $\rightarrow$ `ఐదు వేల రూపాయలు`).
- Support pronunciation overrides: If donor name is entered in English (e.g. "Ramesh"), permit pronunciation text in Telugu ("రమేష్") for the TTS engine.
- Fallback gracefully: If Sarvam AI fails or is offline, instantly switch to Android Native `TextToSpeech` without user-visible errors.

---

## 5. Coding Standards for Jetpack Compose

1. **Recomposition Performance**:
   - Always use `@Immutable` or `@Stable` data classes for UI states.
   - Use `derivedStateOf` for calculated totals (e.g., dashboard balances).
   - In `LazyColumn`, always provide a stable `key` (e.g., `key = { it.id }`).
   - Use `Modifier.animateItem()` for fluid list item insertions and deletions.
2. **State Hoisting**:
   - Keep composables stateless where possible.
   - ViewModels expose a single immutable `uiState: StateFlow<UiState>`.
3. **No Business Logic in Composables**:
   - Database operations, formatting, and audio triggers belong in Use Cases / ViewModels.

---

## 6. Project Directory Layout

```text
app/src/main/java/com/durgamma/festival/
├── core/
│   ├── audio/        # AudioFocusManager, AudioRouteDetector
│   ├── database/     # Room AppDatabase, TypeConverters
│   ├── tts/          # DualTtsEngine, AndroidTtsClient, SarvamTtsClient, TeluguNumberFormatter
│   ├── theme/        # TempleColor, Type, Shape, Theme
│   └── util/         # Formatters, Resource, Result
├── data/
│   ├── local/        # Entities, DAOs (EventDao, DonationDao, ExpenseDao, CorrectionDao)
│   ├── remote/       # FirestoreService, SarvamApiService (Retrofit/Ktor)
│   └── repository/   # Repository Implementations (Offline-first)
├── domain/
│   ├── model/        # Domain Models (Event, Donation, Expense, UserRole)
│   ├── repository/   # Repository Interfaces
│   └── usecase/      # Business Use Cases (SaveDonation, AnnounceDonation, CalculateBalance)
└── presentation/
    ├── splash/       # VishnuChakraSplashScreen
    ├── dashboard/    # EventDashboardScreen, QuickDonationBar
    ├── donation/     # DonationEntryScreen, DonationListScreen, DonationDetailSheet
    ├── announcement/ # AnnouncementQueueScreen, AudioRouteBadge
    ├── expense/      # ExpenseEntryScreen, ExpenseListScreen
    ├── settings/     # AdminTtsSettingsScreen, CloudSyncScreen
    └── common/       # TempleAppBar, ChakraLoader, CurrencyTextField
```

---

## 7. Verification Checklist Before Committing Changes

- [ ] Does saving a donation work completely offline without network lag?
- [ ] Does the UI render at a fluid 60/120 FPS with no jank during rapid entry?
- [ ] Is TTS audio cached locally on disk without burning cloud storage?
- [ ] Are financial corrections non-destructive (no silent deletes)?
- [ ] Does the Sudarshana Chakra splash screen animate smoothly and transition within 1.5 seconds?
