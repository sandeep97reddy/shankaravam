# ShankaRavam — Technical Walkthrough & Verification

## 1. Announce Audio Crash Resolution & Audio System Hardening

### Root Causes & Architecture
1. **API Level Incompatibility in `AudioRouteDetector`**: Replaced runtime static field lookups (`AudioDeviceInfo.TYPE_BLE_HEADSET`, etc., added in API 31) with compile-time safe integer constants to prevent `NoClassDefFoundError` on Android 8.0–11.
2. **Native TTS Pack Resilience**: If Telugu voice data (`te-IN`) is not yet installed on the device, the engine safely falls back to `Locale.getDefault()` rather than disabling itself.
3. **Hardened MediaPlayer & Coroutine Protection**: Added upfront validation (`file.exists() && file.length() > 0L`) before playback initialization, wrapped ViewModel transport methods with `runCatching`, and added UI banners for error states.

---

## 2. Roomy, Light Visual Theme & Sacred Temple Palette

To eliminate dark, heavy blocks and harsh contrast borders, the visual design was refactored across tokens, bars, cards, and hubs:

### A. Theme & Token Enhancements ([Color.kt](file:///c:/Users/SANDEEP/Desktop/projects/shankaravam/app/src/main/java/com/shankaravam/festival/core/theme/Color.kt), [Theme.kt](file:///c:/Users/SANDEEP/Desktop/projects/shankaravam/app/src/main/java/com/shankaravam/festival/core/theme/Theme.kt))
- **Luminous Surfaces**: `LuminousSurface = Color(0xFFFFFFFF)`, `RoomyBackground = Color(0xFFFFFDF9)`, and `SoftCardSurface = Color(0xFFFAFAF7)`.
- **Airy Hairline Borders**: `HairlineBorder = Color(0xFFEDE8DF)` replacing heavy outlines.
- **Strict WCAG Legibility**:
  - Headings: `SacredCharcoal = Color(0xFF1A120B)` (> 15:1 contrast on white).
  - Telugu & Status Subtitles: `DeepMaroon = Color(0xFF4A0E17)` (13.5:1 contrast on white, satisfying WCAG AAA).
  - Secondary text: `Color(0xFF574A40)` (dark walnut, 7.4:1 contrast on white, satisfying WCAG AAA).
  - `TempleSaffron` (`#E65100`) is strictly reserved for large/bold accents and interactive primary buttons.
- **Unified Temple Palette Washes**:
  - Soft, airy icon washes derived strictly from the sacred temple palette:
    - `SaffronWash = Color(0xFFFFF7ED)`
    - `MaroonWash = Color(0xFFFAF2F3)`
    - `EmeraldWash = Color(0xFFF0FDF4)`
    - `CrimsonWash = Color(0xFFFEF2F2)`
    - `GoldWash = Color(0xFFFEFCE8)`
  - Explicitly avoided random blue, purple, or multi-colored clutter.

### B. Header & App Bar Modernization ([TempleAppBar.kt](file:///c:/Users/SANDEEP/Desktop/projects/shankaravam/app/src/main/java/com/shankaravam/festival/presentation/common/TempleAppBar.kt))
- Replaced the solid dark maroon header bar with an airy, elevated light surface bar.
- Navigation back arrow rendered in `DeepMaroon` with `SacredCharcoal` typography.

---

## 3. Redesigned Settings Screen: Collapsible Accordion UI ([AdminSettingsScreen.kt](file:///c:/Users/SANDEEP/Desktop/projects/shankaravam/app/src/main/java/com/shankaravam/festival/presentation/settings/AdminSettingsScreen.kt))

The Settings screen is built to be clean, accessible, and intuitive for rural/less-educated temple volunteers:

1. **App Language Selection (Top & Unobtrusive)**:
   - Placed directly at the top with clear, prominent chips: `English` and `తెలుగు (Telugu)`.

2. **Role & Local Storage Security Status**:
   - Compact status chip indicating role (`👑 Global Head • Verified` or `Role: Organizer / Volunteer`) and local encryption status.

3. **Collapsible Accordions with Live 1-Line Summaries**:
   - All tiles start collapsed (~70dp height) so the screen fits comfortably without scrolling.
   - Live 1-line subtitle summaries on closed cards:
     - 🔊 **Temple Voice & Audio**: Shows active voice name, speed (e.g. `1.00x`), and bell chime status (`Bell On / Off`).
     - 🏷️ **Advanced: Counter Identity**: Shows current counter name or `Not set (Default)`. Moved into an Advanced section.
     - ☁️ **Advanced: Cloud Voice (Sarvam AI)**: Shows `Offline Voice Active (Android TTS)` or `Sarvam Cloud Voice Configured`.
     - 🔒 **Festival Administration**: Shows active festival name and closure status (visible only to organizers).

4. **UX Bug Fixes & Accessibility Enhancements**:
   - **Hoisted Draft State**: Form drafts (`counterDraft`, `sarvamKeyDraft`, `sarvamSpeakerDraft`) are hoisted to the screen body outside `AnimatedVisibility`. Collapsing and re-expanding an accordion tile preserves entered text without loss.
   - **Live `keyTick` Reactivity**: Added `keyTick: MutableStateFlow<Int>` into the ViewModel's `combine` flow. Saving or pulling a Sarvam key immediately triggers UI re-evaluation so the card summary updates instantly without requiring screen navigation.
   - **TalkBack Accessibility**: Accordion clickable headers specify `role = Role.Button` and contextual `onClickLabel` (`"Expand ..."` / `"Collapse ..."`).
   - **Contrast Correction**: Telugu titles use `DeepMaroon` (13.5:1 contrast on white) to exceed WCAG AAA standards.

---

## 4. Verification & Test Results

1. **Kotlin Compilation**:
   - `.\gradlew.bat compileDebugKotlin` completed cleanly with `BUILD SUCCESSFUL`.
2. **Automated Unit Tests**:
   - `.\gradlew.bat testDebugUnitTest` executed with **86/86 tests green** (100% passing across domain, data, audio, and sync layers).
3. **Debug APK Assembly**:
   - `.\gradlew.bat assembleDebug` succeeded with zero resource or layout issues.
4. **Offline & Safety Checklist**:
   - All operations remain 100% offline-first.
   - Financial ledger remains non-destructive.
   - Release APK was **not** built, in compliance with instructions.
