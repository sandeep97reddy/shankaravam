# Festival Donation and Expense App — Master Implementation Plan

## 1. Product Summary

An Android application for multiple temples, festivals, and community events (Vinayaka Chavithi, Sri Rama Navami, Dasara, Hanuman Jayanthi, Temple Renovation Funds, etc.).

Users can:

- Create or join event profiles
- Add cash and non-cash donations (Rice, Sarees, Deity Sponsorship, Flowers, Services)
- Announce donations through the phone speaker or Bluetooth amplifier horn speakers
- Track expenses and manage receipts
- View real-time balances and totals
- Work 100% offline out-of-the-box on first launch with zero barrier
- Synchronize later across multiple devices via Cloud Firestore (optional)
- Export reports (PDF, Excel, WhatsApp summary)
- Use Telugu and English app interfaces
- Generate natural Telugu donation announcements

The first version prioritizes rock-solid reliability, fluid 60/120 FPS performance, zero-jank offline entry, and simplicity over complicated enterprise features.

---

## 2. Technology & Operating Principles

- **Platform**: Native Android
- **Language**: Kotlin 2.0+
- **UI Toolkit**: Jetpack Compose (Material 3)
- **Architecture**: MVVM / MVI with Clean Architecture (`data`, `domain`, `presentation`)
- **Persistence**: Room Database (SQLite) — the single source of truth for the UI
- **Reactive Streams**: Kotlin Coroutines & `StateFlow`
- **Background Work**: Jetpack WorkManager for background delta-sync
- **Authentication**: Firebase Authentication with Google Sign-In (optional for cloud sync)
- **Cloud Database**: Cloud Firestore (Spark Free Tier)
- **Audio Output**: Android Native `AudioManager` with `AudioFocusRequestCompat` (Bluetooth amplifier/speaker routing)
- **Text-to-Speech**:
  - Offline / Default: Android Native `TextToSpeech` (`Locale("te", "IN")`) — 100% free, zero network dependency
  - Cloud / Natural: Sarvam AI Telugu REST API (configurable API key stored in encrypted preferences / Firestore)
- **Audio Storage**: Strictly local device caching (`context.cacheDir/audio/`). Zero audio uploaded to cloud storage to stay 100% on the Free Tier.
- **Simplicity**: Avoid analytics, unnecessary logging, and complicated microservices.

---

## 3. App Launch & Temple Visual Identity

### Visual Identity & Color Tokens
```kotlin
val DeepMaroon = Color(0xFF4A0E17)    // Sandalwood / Sacred Maroon (Primary Dark)
val TempleSaffron = Color(0xFFE65100) // Vibrant Temple Saffron (Primary Light)
val TempleGold = Color(0xFFFFD700)    // Sacred Gold (Accent / Highlights)
val DivineAmber = Color(0xFFFFA000)   // Secondary Accent
val WarmIvory = Color(0xFFFFFDF7)     // Surface Light
val SacredCharcoal = Color(0xFF1A120B)// Surface Dark
```

### Launch Screen (Vishnu Sudarshana Chakra Animation)
- **Mandatory**: On app cold launch, display a lightweight, vector-rendered spinning **Vishnu Sudarshana Chakra**.
- **Duration**: Maximum **1.2 to 1.5 seconds**. Snappy and responsive. Must never delay an organizer waiting to enter donations at a festival counter.
- **Animation**: Smooth continuous rotation easing (`Animatable` rotationZ 0° to 360°) with a subtle radiant divine aura/glow.
- **Transition**: Immediately and smoothly transitions into the Event Dashboard or Event Selector.

---

## 4. First-Launch Flow & Operating Modes

```mermaid
flowchart TD
    A[Launch: Sudarshana Chakra Animation] --> B{Choose Operating Mode}
    B -->|Immediate / No Internet| C[Pure Local Mode]
    B -->|Multi-Counter / Team| D[Cloud Sync Mode]
    
    C --> E[Create Local Event]
    E --> F[Instant Cash & Item Donations]
    F --> G[Telugu Announcements via Local TTS]
    F --> H[Export PDF / Excel Reports]
    
    C -.->|Whenever Ready| I[Enable Cloud Sync in Settings]
    I --> J[Sign in with Google]
    J --> K[Upload Local Room DB to Firestore]
    K --> D
```

### Pure Local Mode (Zero Barrier Out-of-the-Box)
- The app works 100% offline immediately upon first install.
- No mandatory login, no Firebase setup, and no internet required on first launch.
- SQLite / Room DB stores all events, donations, expenses, and settings locally.
- Full reporting (PDF/CSV/WhatsApp) and announcements work completely offline.

### Cloud Sync Mode (Multi-Device Team Mode)
- Enabled on-demand when multiple collectors or pandal counters need live synchronized totals.
- Authentication via Google Sign-In.
- Global Head creates or links a Cloud Event and generates an **Event QR Code / Share Code**.
- Collectors scan the QR code to join the event with role-based permissions.
- WorkManager automatically synchronizes local records with Cloud Firestore in the background.

---

## 5. Event Profiles

Each festival or temple activity is a separate event profile.

Examples:

- Vinayaka Chavithi 2026
- Sri Rama Navami 2026
- Temple Renovation Fund
- Hanuman Jayanthi

Each event contains:

- Event name
- Temple or organization name
- Location
- Event dates
- Default announcement language
- Members
- Roles
- Donation categories
- Expense categories
- Total donations
- Total expenses
- Balance
- Event QR code
- Active or closed status

Users may belong to multiple events.

The current event must always be clearly visible in the app to prevent entering records into the wrong event.

---

## 6. User Roles

### Global Head

There is only one global head per organization/cloud setup.

The global head can:

- Create events
- Add or remove event heads
- Manage all events
- Manage users
- Change roles
- Revoke access
- View all records
- Resolve conflicts
- Close events
- Recover access
- Configure **Cloud TTS API Key** (e.g. Sarvam AI key) in Settings, synced securely via Firestore

The global head should be controlled by a protected account identifier in Firebase, not only by a value stored inside the Android app.

### Organizer / Collector

Organizers and collectors are the same role in this version.

They can:

- Add donations
- Approve or confirm donations
- Add expenses
- Announce donations
- View event records
- Export reports
- Invite or approve members if permitted

They cannot silently modify or delete financial history.

### Regular Member

A regular logged-in member can:

- View public event information
- Add expenses (e.g., submitting receipts for reimbursement)
- Request collector access
- View donation and expense records allowed by the event

A regular member cannot add donations unless approved as an organizer/collector.

---

## 7. Authentication and Joining Events

Use Google Sign-In through Firebase Authentication.

Recommended joining process:

1. User installs the app.
2. User signs in with Google.
3. User scans an event QR code or enters an event code.
4. User requests access.
5. The event organizer or global head approves the request.
6. The user receives the selected role.

The QR code should identify the event. It must not automatically grant administrative access.

For sensitive actions, require a recent login or an additional PIN:

- Approving users
- Changing roles
- Closing an event
- Approving a correction
- Changing event ownership

---

## 8. Donation Types

Donations are not limited to money.

Supported examples:

- Cash
- UPI
- Bank transfer
- Cheque
- Deity sponsorship
- Saree
- Flowers
- Rice bags
- Food materials
- Decoration materials
- Services
- Any custom item

A donation must support both amount and item description.

Examples:

- Amount: ₹5,000, Item: Deity sponsorship
- Amount: ₹0, Item: 10 kg rice bag
- Amount: ₹2,000, Item: Flowers
- Amount: ₹0, Item: Volunteer service

For non-cash donations, support:

- Quantity
- Unit
- Item description
- Optional estimated value
- Notes

Do not force an estimated monetary value if the organizer does not know it.

---

## 9. Donation Fields

Each donation should contain:

- Donor full name
- Pronunciation text (e.g., English "Srinivas" $\rightarrow$ Telugu "శ్రీనివాస్" for natural TTS)
- Amount, optional for non-cash donations
- Currency
- Donation item or description
- Quantity
- Unit
- Payment method
- Tags
- Status
- Announcement preference
- Notes
- Added by user
- Added time
- Event ID
- Local record ID
- Synchronization status
- Correction status

Possible tags:

- Cash
- UPI
- Sponsor
- Saree
- Flowers
- Rice
- Food
- Decoration
- Material
- Service
- Other

Users should be able to create custom tags.

---

## 10. Donation Statuses

Donation status is required because some people promise to donate later.

Recommended statuses:

- Pledged
- Partially received
- Received
- Confirmed
- Cancelled

Meaning:

### Pledged
The donor promised the donation, but it has not yet been received.

### Partially Received
Only part of the promised donation has been received.

### Received
The collector recorded that the donation was received.

### Confirmed
The donation has been checked or approved.

### Cancelled
The pledge or record is no longer valid.

Only confirmed or received donations should be included in the collected total, according to the event setting.

Pledged donations should be excluded from the actual balance.

---

## 11. Announcement System

The announcement screen should allow the user to select:

- Current event
- Donation status
- Tags
- Date range
- Collector
- Language
- Sort order

Default announcement filter:

- Received and confirmed donations
- Not cancelled
- Announcement enabled

The user can choose whether pledged donations should be announced.

Recommended default:

- Do not announce pledged donations.
- Provide a separate “Pledged” announcement list if required.

### Playback Controls

Provide:

- Play
- Pause
- Resume
- Stop
- Replay
- Skip
- Previous
- Next
- Repeat list
- Adjustable pause between announcements (e.g., 2s, 5s, 10s)

The announcement queue should be created from the current sorting and filtering settings.

If the user sorts by amount, the app announces by amount. If the user sorts by insertion order, it announces by insertion order.

### Bluetooth Amplifier Support

The app does not need special Bluetooth code for normal amplifier playback.

It should use Android’s normal audio output system:

1. Connect the amplifier or speaker through Android Bluetooth settings.
2. The app plays audio using `AudioManager` (`STREAM_MUSIC`) and `AudioFocusRequestCompat`.
3. Ducks background temple music when an announcement starts.
4. Android sends audio to the connected amplifier.
5. If Bluetooth is disconnected, audio plays through the phone speaker.

The announcement screen should display the current audio route:

- Phone speaker
- Bluetooth device / Amplifier
- Wired headset

Add a **“Test audio”** button before starting a public announcement.

---

## 12. Announcement Text & Telugu Templates

The announcement should be template-based.

Example Telugu template for cash:
`శ్రీ {పేరు} గారు {ఉత్సవం} కోసం {మొత్తం} రూపాయలు విరాళంగా అందించారు.`
*(e.g., శ్రీ రమేష్ గారు వినాయక చవితి ఉత్సవాల కోసం ఐదు వేల రూపాయలు విరాళంగా అందించారు.)*

For material donations:
`శ్రీ {పేరు} గారు {ఉత్సవం} కోసం {పరిమాణం} {వస్తువు} విరాళంగా అందించారు.`
*(e.g., శ్రీ రమేష్ గారు వినాయక చవితి ఉత్సవాల కోసం పది కిలోల బియ్యం విరాళంగా అందించారు.)*

For sponsorship:
`శ్రీ {పేరు} గారు {ఉత్సవం} కోసం {సేవ/అలంకరణ} కు స్పాన్సర్ చేశారు.`
*(e.g., శ్రీ రమేష్ గారు వినాయక స్వామి అలంకరణకు స్పాన్సర్ చేశారు.)*

Templates should support:

- Telugu
- English
- Telugu followed by English
- Future Hindi, Tamil, or Kannada

The event profile selects the default announcement language.

Number to Telugu word conversion: e.g., 5000 $\rightarrow$ "ఐదు వేల", 10016 $\rightarrow$ "పది వేల పదహారు".

---

## 13. Dual-Engine TTS Strategy & API Key Sync

Use a reliable two-level system:

### Primary / High-End Voice: Sarvam AI Cloud TTS
- Natural Telugu speech cadence, handles Indian names and sacred terms gracefully.
- Audio generated in background after saving a donation.
- **Local Audio Cache**: Audio is cached on internal device disk (`context.cacheDir/audio/donation_{id}.mp3`).
- Reused during playlist announcements without re-calling the API or uploading to cloud storage.
- Manual regeneration option if donor name or pronunciation text is edited.

### Fallback / Offline Voice: Android Native Text-to-Speech
- Uses `android.speech.tts.TextToSpeech` with `Locale("te", "IN")`.
- Instant, zero network latency, 100% free, unlimited usage.
- Used when:
  - Internet is unavailable
  - Cloud audio is still downloading/preparing
  - Sarvam API request fails or quota runs out
  - User prefers offline local voice

Do not block donation entry while audio is being generated.

Each donation shows an audio status:
- Not generated
- Preparing
- Ready
- Failed
- Regenerate

### Pronunciation Override
- Display Name: Srinivas
- Pronunciation Text: శ్రీనివాస్
- The pronunciation text is sent to the TTS engine, while the display name remains unchanged in records and receipts.

### TTS API Key Management & Database Sync
- To avoid running a paid cloud server, the **Global Head** can enter the Sarvam AI API Key in **Admin Settings**.
- **Firestore Sync**: Saved to `/config/tts_settings` in Cloud Firestore. Protected by Firestore Security Rules (readable only by authorized collectors/admins, writable only by Global Head).
- **Local Encryption**: Cached locally in Android `EncryptedSharedPreferences`.
- Free tier guarantee: Audio is never stored in Firebase Cloud Storage.

---

## 14. Donation List Screen

Each donation row should show:

- Donor name
- Amount or item description
- Status (badge with distinct colors)
- Tags
- Added by
- Time
- Audio status (Ready / Preparing / Error icon)
- Sync status (Local / Synced)

Tapping a donation opens a detail sheet:

- Full details
- Announcement preview text
- Play announcement button
- Edit option (if permitted)
- Correction history
- Collector information
- Creation time
- Last update time

---

## 15. Sorting and Filtering

Support:

- Newest first
- Oldest first
- Insertion order
- Donor name A–Z
- Donor name Z–A
- Amount low to high
- Amount high to low
- Status (Pledged, Received, Confirmed, Cancelled)
- Tag (Cash, UPI, Rice, Saree, etc.)
- Payment method
- Collector
- Date range
- Audio ready / not ready
- Synced / not synced
- Corrected / not corrected

Save the last-used filter for convenience, but always show the active filter chip bar clearly.

---

## 16. Financial Record Safety & Non-Destructive Ledger

Do not silently edit or delete donations.

A safe, transparent approach:

- Allow the collector to edit typos immediately after entry (within a brief grace window, e.g. 5 minutes).
- After that window, require a **Correction Transaction**.
- Preserve the original record and amount.
- Store the delta amount, reason for correction, author, and timestamp.

Example:
- Original donation: ₹5,000
- Correction: -₹4,500
- Reason: "Typo entered 5000 instead of 500"
- Effective amount: ₹500
- The original record and the correction remain permanently visible in history.

Use **Cancelled** or **Voided** status instead of permanent deletion from SQLite.

This lightweight pattern prevents fraud without needing a complex enterprise accounting engine.

---

## 17. Transaction History

The transaction history should show:

- Donation records
- Expense records
- Corrections
- Cancellations
- User approvals
- Role changes
- Imports

For each transaction show:

- Who created it
- When it was created
- Who changed it
- When it was changed
- Current status
- Original values if corrected

A simple append-only history table in Room DB.

---

## 18. Expense Tracker

Any logged-in event member or authorized collector may add an expense.

Expense fields:

- Amount
- Description
- Category
- Date
- Paid by
- Payment method
- Vendor
- Notes
- Optional receipt image (compressed to WebP ~100KB)
- Added by
- Added time
- Event ID

Suggested categories:

- Decorations
- Food / Annadanam
- Flowers
- Sound system / Lighting
- Transport
- Priest or service fees
- Printing / Banners
- Electricity / Generator
- Cleaning / Sanitation
- Materials
- Miscellaneous

Expense rules:

- The person who added an expense may edit it.
- The person who added it may cancel it.
- Do not permanently delete expenses after synchronization.
- Preserve the original value in the history.
- No approval is required in the first version.

The global head or event organizer can resolve disputed expenses later.

---

## 19. Dashboard

The event dashboard should show:

- Total confirmed donations
- Total received donations
- Total pledged donations
- Total non-cash donations
- Total expenses
- Remaining balance
- Number of donors
- Number of expenses
- Pending access requests
- Unsynced records count

### Balance Formula:
$$
\text{Balance} = \sum \text{Confirmed & Received Cash} - \sum \text{Expenses}
$$

Non-cash donations should be displayed separately unless the organizer enters an estimated value.

Do not include pledged donations in the actual balance.

---

## 20. Offline-First Synchronization Architecture

The app must work without internet.

When offline:

- Users can view previously downloaded event data.
- Users can add donations with sub-10ms UI confirmation.
- Users can add expenses.
- Users can generate local Android TTS announcements.
- Records are marked as waiting for sync (`Pending Upload`).
- Synchronization occurs automatically via `WorkManager` when internet returns.

Each record contains:

- Unique record ID (UUID v4)
- Event ID
- Creator ID
- Device ID
- Creation time (UTC epoch millis)
- Last modification time
- Sync status (`Local Only`, `Pending Upload`, `Synced`, `Sync Failed`)
- Record version
- Status

Use Room as the single source of truth for the user interface.

Use WorkManager to retry synchronization with exponential backoff.

Recommended sync statuses:

- Local only
- Uploading
- Synced
- Sync failed
- Conflict
- Needs review

Do not use unrestricted last-write-wins behavior for money records. For corrections, create a new correction entry instead of overwriting the original donation or expense.

Offline data should remain usable indefinitely. The app should show an informative badge if data has been offline for a prolonged period (such as thirty days).

---

## 21. Fluid & Fast Performance Standards

- **Sub-10ms UI Response**: Saving a donation or expense commits to the local Room database immediately. The screen updates instantly with zero network waiting spinner.
- **60 / 120 FPS Jetpack Compose**:
  - `Modifier.animateItem()` for list insertions and reordering.
  - `derivedStateOf` for live balance calculations to prevent unnecessary recompositions.
  - Keyed `LazyColumn` (`key = { it.id }`) for zero-jank scrolling.
  - Predictive back gestures and smooth bottom sheets.
- **Background Sync**: Network operations run completely on background Coroutine dispatchers (`Dispatchers.IO`) via `WorkManager`. Network drops never freeze the UI.

---

## 22. Cloud Firestore Structure (Spark Free Tier)

```text
users/{userId}
    displayName: String
    email: String
    phone: String?
    createdAt: Timestamp

config/tts_settings (Restricted)
    sarvamApiKey: String
    defaultSpeaker: String
    updatedAt: Timestamp
    updatedBy: String

events/{eventId}
    name: String
    templeName: String
    location: String
    dates: Map
    status: "active" | "closed"
    globalHeadId: String
    createdAt: Timestamp

events/{eventId}/members/{userId}
    role: "global_head" | "organizer" | "member"
    status: "active" | "pending" | "revoked"
    approvedBy: String
    joinedAt: Timestamp

events/{eventId}/donations/{donationId}
    donorName: String
    pronunciationText: String?
    amount: Double
    isNonCash: Boolean
    itemDescription: String?
    quantity: Double?
    unit: String?
    paymentMethod: String
    tags: List<String>
    status: "pledged" | "received" | "confirmed" | "cancelled"
    addedBy: String
    deviceId: String
    createdAt: Timestamp
    updatedAt: Timestamp
    version: Long

events/{eventId}/expenses/{expenseId}
    amount: Double
    description: String
    category: String
    date: Timestamp
    paidBy: String
    receiptUrl: String?
    addedBy: String
    createdAt: Timestamp

events/{eventId}/corrections/{correctionId}
    targetRecordId: String
    targetType: "donation" | "expense"
    originalAmount: Double
    deltaAmount: Double
    reason: String
    correctedBy: String
    createdAt: Timestamp

events/{eventId}/activity/{activityId}
    actionType: String
    details: String
    actorId: String
    timestamp: Timestamp

events/{eventId}/imports/{importId}
    source: String
    count: Int
    importedBy: String
    timestamp: Timestamp
```

---

## 23. 100% Free Tier Blueprint & Quota Protections

| Component | Free Tier Quota (Spark Plan) | Our Architectural Safeguard |
| :--- | :--- | :--- |
| **Cloud Firestore** | 50,000 reads/day, 20,000 writes/day, 1 GB storage | Room DB serves 100% of UI reads. Firestore only receives delta changes. A festival with 2,000 donors uses < 10% of daily quota. |
| **Firebase Auth** | 50,000 Monthly Active Users | Google Sign-In is completely free. |
| **Firebase Storage** | 5 GB storage, 1 GB/day transfer | **Zero audio files in Firebase Storage**. TTS audio is strictly stored in local device cache. Receipts are compressed to WebP (~100 KB). |
| **Text-to-Speech** | Android Native TTS: Unlimited Free | Local Android TTS handles offline; Sarvam AI API called only when key provided. |

---

## 24. Implementation Plan — Grouped for Single-Context Execution

> **HOW TO EXECUTE THIS PLAN (READ FIRST):**
> - Each `GROUP` below = **exactly ONE context window / ONE session**. Do not mix groups in one session.
> - Each Group contains 1–2 `Phases`. Complete all Phases in the Group, verify with the `Verify` block, update `PROGRESS.md` + `SESSION_HANDOFF.md`, then **STOP and start a new session**.
> - Always load the listed Skills at session start. Always respect `AGENTS.md` Rules #1–#3 (offline-first, free-tier, non-destructive ledger).
> - Group outputs are cumulatively buildable: G1 = empty shell APK → G3 = usable offline counter app → G5 = complete offline app → G6 = optional cloud.
> - Suggested paste-prompt for a new session is in `SESSION_HANDOFF.md`.

---

### GROUP G1 — Foundation Shell (Session 1, start here)
**Phases:**
- P1: Project scaffold — Gradle (Kotlin 2.0+, AGP, KSP, Room 2.6+, Compose BOM, Navigation, WorkManager, Hilt/manual DI), `libs.versions.toml`, `AndroidManifest.xml`, `MainActivity.kt`, `NavGraph.kt`, package `com.shankaravam.festival`, light/dark `Theme.kt`.
- P2: Temple visual identity — color tokens (`DeepMaroon/TempleSaffron/TempleGold/DivineAmber/WarmIvory/SacredCharcoal`), `Type.kt`, `Shape.kt`, `TempleAppBar`, `ChakraLoader`, `CurrencyTextField`, `VishnuChakraSplashScreen` (<1.5s, vector rotation + aura), splash → placeholder dashboard navigation.

**Files (create):** `build.gradle.kts`, `libs.versions.toml`, `AndroidManifest.xml`, `MainActivity.kt`, `core/theme/*`, `presentation/splash/*`, `presentation/common/*`, `presentation/navigation/*`, `ic_sudarshana_chakra.xml`.
**Skills:** `jetpack-compose-performance` (splash + theme section).
**Verify:** `./gradlew assembleDebug` succeeds; cold launch shows spinning Chakra ≤1.5s → empty dashboard; no Firebase, no Room yet; 60fps, no jank.
**Exit criteria:** Launchable APK shell with temple theme. No business logic.

> **G1 description:** Smallest possible buildable app. Proves toolchain, theme, splash timing, and navigation before any data code pollutes context. Context load: ~10 files, all UI-only.
> 🛑 **STOP — start a NEW session for G2. Update PROGRESS.md + SESSION_HANDOFF.md first.**

---

### GROUP G2 — Offline Data Core (Session 2)
**Phases:**
- P3: Room layer — `EventEntity`, `DonationEntity`, `ExpenseEntity`, `CorrectionEntity`, `ActivityEntity`, `TypeConverters`, `AppDatabase`, `EventDao`, `DonationDao`, `ExpenseDao`, `CorrectionDao`, `ActivityDao` (all list queries return `Flow`), UUID v4 ids, sync-status + version columns.
- P4: Domain + repository — domain models (`Event/Donation/Expense/UserRole/Correction`), mappers, repository interfaces + offline-first impls (Room = single source of truth, `<10ms` write, `Dispatchers.IO`), use cases: `SaveDonation`, `SaveExpense`, `RecordCorrection`, `CalculateBalance`, `ObserveEventTotals`.

**Files (create):** `core/database/*`, `core/util/{Result,Formatters}.kt`, `data/local/{entities,daos}/*`, `domain/model/*`, `domain/repository/*`, `domain/usecase/*`, `data/repository/*`, DI modules.
**Skills:** `jetpack-compose-performance` (Room-as-Source-of-Truth + StateFlow section).
**Verify:** `./gradlew testDebugUnitTest` + `assembleDebug`; insert donation → Room → Flow emits <10ms; no UI required yet (temporary instrumented check ok); no network calls; no deletes, only `Cancelled` flag.
**Exit criteria:** All money writes go through Room reactively. UI still G1 placeholder but ViewModels can already `stateIn(WhileSubscribed(5000))`.

> **G2 description:** The correctness-critical ledger foundation. Isolated from UI/audio so Room schema, migration strategy, and non-destructive invariants get full attention. Largest correctness payoff per token.
> 🛑 **STOP — start a NEW session for G3. Update PROGRESS.md + SESSION_HANDOFF.md first.**

---

### GROUP G3 — Events + Donations + Dashboard (Session 3 — first usable app)
**Phases:**
- P5: Event management + dashboard — event selector/creator, current-event banner (always visible), dashboard cards (confirmed/received/pledged/non-cash totals, expenses, balance = confirmed+received cash − expenses, donor/expense counts, unsynced badge), `derivedStateOf` totals, 30-day-offline badge.
- P6: Donation loop — `QuickDonationBar` + `DonationEntryScreen` (name + pronunciation field, amount/item/qty/unit, payment method, tags incl. custom, status, announcement toggle), `DonationListScreen` (keyed `LazyColumn` + `animateItem`, status/audio/sync badges), `DonationDetailSheet` (full details, announcement preview, correction history), sorting/filtering (newest/oldest/amount/name/status/tag/collector/date/audio/sync/corrected) + saved-filter chips.

**Files (create):** `presentation/dashboard/*`, `presentation/donation/*`, `presentation/event/*` (if needed), ViewModels with single `uiState: StateFlow<UiState>`.
**Skills:** `jetpack-compose-performance` (full file — lists, derivedStateOf, keys, animateItem).
**Verify:** Airplane-mode test: create event → save cash + rice-bag + saree donations → list animates at 60fps, dashboard balance correct, pledged excluded; `./gradlew assembleDebug`; AGENTS.md checklist items 1–2 pass.
**Exit criteria:** A volunteer can run a festival counter fully offline. No TTS sound yet (preview text only), no expenses yet.

> **G3 description:** First half of the offline money loop (money-in). Deliberately excludes audio/expenses to keep this heavy UI session within one context. After this, the app is already field-usable.
> 🛑 **STOP — start a NEW session for G4. Update PROGRESS.md + SESSION_HANDOFF.md first.**

---

### GROUP G4 — Telugu Voice + Announcement Queue (Session 4)
**Phases:**
- P7: Audio engine — `TeluguNumberFormatter` (5000→ఐదు వేల, 10016→పది వేల పదహారు), announcement templates (cash/material/sponsorship, TE/EN/TE+EN), `AndroidTtsClient` (`Locale("te","IN")`, `STREAM_MUSIC`), `SarvamTtsClient` (Retrofit, base64→`cacheDir/audio/donation_{id}.mp3`, existence-check before call, silent fallback to native), `DualTtsEngine` (non-blocking generation, audio-status: NotGenerated/Preparing/Ready/Failed), `AudioFocusManager` (`USAGE_ASSISTANCE_ACCESSIBILITY`, transient-may-duck) + `AudioRouteDetector` (Speaker/Bluetooth/Wired) + test-audio.
- P8: Queue UI — `AnnouncementQueueScreen` (filters: event/status/tags/date/collector/language/sort; default = received+confirmed, no pledged/cancelled), playback controls (Play/Pause/Resume/Stop/Replay/Skip/Prev/Next/Repeat + 2s/5s/10s gap), playlist honors current sort, audio-route badge.

**Files (create):** `core/tts/*`, `core/audio/*`, `data/remote/SarvamApiService.kt`, `domain/usecase/AnnounceDonation.kt`, `presentation/announcement/*`.
**Skills:** `telugu-tts-audio` (full file) + `jetpack-compose-performance` (queue list perf only).
**Verify:** Airplane-mode → native Telugu speaks instantly; online + key → Sarvam mp3 cached locally, replay uses cache (no re-call); Bluetooth amp ducks music, disconnect falls back to speaker; donation entry never blocks on audio; zero files in Firebase Storage.
**Exit criteria:** Horn-speaker announcements work in noisy pandals with one-tap test.

> **G4 description:** Self-contained audio subsystem. All TTS risk (pronunciation, caching, focus, fallback) is quarantined here so a bad audio change can never regress the G3 money loop. Heaviest skill-dependent session — run alone.
> 🛑 **STOP — start a NEW session for G5. Update PROGRESS.md + SESSION_HANDOFF.md first.**

---

### GROUP G5 — Expenses + Ledger Safety + Reports (Session 5 — offline app complete)
**Phases:**
- P9: Money-out + safety — `ExpenseEntryScreen` (amount/desc/category chips/paid-by/vendor/method/date, WebP ~100KB receipt), `ExpenseListScreen`, grace-window typo edit (5 min) → `CorrectionTransaction` after (original preserved + delta + reason + author + timestamp), `Cancelled/Voided` never delete, `ActivityFeed` (donations/expenses/corrections/cancels/approvals/role-changes/imports with who/when/before-after).
- P10: Reports — local PDF summary, CSV/Excel export, WhatsApp share text (Telugu+English totals), transaction-history screen; dashboard non-cash breakdown (separate unless estimated value given).

**Files (create):** `presentation/expense/*`, `presentation/history/*`, `presentation/reports/*` (or `core/export/*`), `domain/usecase/{SaveExpense,RecordCorrection,ExportReport}.kt`, receipt compressor.
**Skills:** `jetpack-compose-performance` (lists + export off `Dispatchers.IO` only).
**Verify:** Edit after 5 min forces correction row, original immutable; cancel preserves row; PDF/CSV/WhatsApp generate fully offline; balance formula holds; AGENTS.md checklist items 1–4 pass.
**Exit criteria:** 100% offline festival app is FEATURE-COMPLETE. G6 is optional.

> **G5 description:** Closes the money-out loop and the fraud-safety contract. Merging this with G3/G4 would overflow context; alone it stays reviewable and lets PDF/WebP/ledger edge cases get proper tests.
> 🛑 **STOP — start a NEW session for G6. Update PROGRESS.md + SESSION_HANDOFF.md first.**

---

### GROUP G6 — Optional Cloud Sync + Admin + Hardening (Session 6, last)
**Phases:**
- P11: Cloud — Google Sign-In (lazy, settings-only, never on launch), event QR/share-code join + approval flow, Firestore delta-sync only (`Pending Upload→Uploading→Synced/Failed/Conflict`), `WorkManager` exponential backoff, `EncryptedSharedPreferences` Sarvam key ↔ `/config/tts_settings` (head-write, member-read), `firestore.rules`, receipt WebP upload only.
- P12: Admin + polish — `AdminTtsSettingsScreen`, `CloudSyncScreen`, role gating (global_head/organizer/member), sensitive-action PIN/re-auth, close-event, 120Hz jank pass, cold-start ≤1.5s re-check, release checklist + Play-signed AAB.

**Files (create):** `data/remote/FirestoreService.kt`, `data/work/*`, `presentation/settings/*`, `firestore.rules`, `firebase.json` (if needed).
**Skills:** none new (reuse both only for perf/audio regressions).
**Verify:** Fresh install works with zero login (Rule #1 still holds); enable sync → deltas sync, kill network → queue → resume with backoff; quota spot-check (2000-donor festival <10% reads/writes); audio still never in Storage; full AGENTS.md §7 checklist green.
**Exit criteria:** Multi-counter team mode works; single-counter offline mode untouched.

> **G6 description:** Only networked code in the whole app, deliberately last. If quotas/Firebase ever cause trouble, G1–G5 remain a shippable offline APK. Smallest blast radius for the riskiest dependency.
> 🛑 **DONE — final session. Mark PROGRESS.md all-green and tag release.**
