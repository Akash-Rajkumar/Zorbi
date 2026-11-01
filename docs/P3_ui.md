# ZORBI — UI & UX Architecture (Person 3)

## Task P3-0: Design Tokens & Reusable Component Foundation

### 1. Frozen Design Tokens (Master Prompt Section 13)
All color tokens, spacing metrics, and elevation rules strictly implement the authoritative specification:

* **Dark-Only Theme Palette:**
  * Background: `#0A0E1A` (`ZorbiBackground`)
  * Surface: `#121830` (`ZorbiSurface`)
  * Elevated Glass Card: Surface with ~70% alpha (`#B3121830`, `ZorbiSurfaceElevated`), 1 dp border `#1E2A4A`, soft elevation
  * Border: `#1E2A4A` (`ZorbiBorder`)
  * Primary Neon Cyan: `#00E5FF` (`ZorbiPrimaryCyan`)
  * Accent Violet: `#7C4DFF` (`ZorbiAccentViolet`)
  * Semantic Success: `#00E676` (`ZorbiSuccess`)
  * Semantic Warning: `#FFB300` (`ZorbiWarning`)
  * Semantic Error: `#FF5252` (`ZorbiError`)
  * Text Primary: `#E6EDF7` (`ZorbiTextPrimary`)
  * Text Muted: `#8B97B5` (`ZorbiTextMuted`)
  * Text Disabled: `#5A6682` (`ZorbiTextDisabled`)

* **Grid & Spacing:**
  * 8dp geometric grid (`sm = 8.dp`, `md = 12.dp`, `lg = 16.dp`, `xl = 24.dp`, `xxl = 32.dp`)
  * Standard screen gutter: `16.dp`
  * Minimum interactive touch target: `48.dp` (Accessibility verified)

* **Typography & Shapes:**
  * Clean, geometric sans typography hierarchy (`ZorbiTypography`)
  * Specialized monospace typography for HUD frame stats and telemetry (`ZorbiMetricTypography`)
  * Rounded glass shapes: 16dp for cards, 12dp for buttons, pills for chips/badges

---

### 2. Component Kit Inventory (`com.zorbi.ui.components`)

| Component | Target Use Case | Key Features |
| :--- | :--- | :--- |
| `ZorbiPrimaryButton` | Main actions (e.g., Start Calibration, Launch Game) | Neon cyan glow, high contrast dark text, loading spinner state, min 48dp height |
| `ZorbiSecondaryButton` | Secondary actions (e.g., Community Profiles, Retry) | Glass surface, violet border, accent ripple, min 48dp height |
| `ZorbiOutlinedButton` | Tertiary actions, filters, resets | Subtle border, muted text, transparent glass fill |
| `ZorbiIconButton` | Navigation, back actions, toolbar triggers | 48dp accessible bounding box, icon tinting, ripple effect |
| `ZorbiGlassCard` | Content containers, game cards, settings blocks | Elevated glass styling (70% alpha surface, 1dp `#1E2A4A` border, 16dp rounded corners) |
| `ZorbiSectionHeader` | Section titles across screens | Glowing neon cyan accent bar, title, subtitle, and action slot |
| `ZorbiMetricCard` | Telemetry & calibration feedback | Monospace metric readout with unit tag |
| `ZorbiStatusDot` | Status visualization | Pulsing animation for active/granted states (Green/Yellow/Red/Neutral) |
| `ZorbiStatusChip` | Wizard permissions (Camera, Accessibility, Battery) | Turns green only when truly granted; interactive click handling |
| `ZorbiFpsChip` | HUD diagnostics overlay | Live FPS and latency readout |
| `ZorbiEngineStateBadge` | Engine state visualization | Displays IDLE, CALIBRATING, RUNNING, PAUSED, ERROR states |
| `ZorbiBadge` / `ZorbiTrackerBadge` / `ZorbiMappingBadge` | Profile details | Compact chips showing tracker type, mapping type, and tags |
| `ZorbiFilterChip` | Filter toggles for Library/Community | Selection state with cyan accent highlight |
| `ZorbiLoadingIndicator` | Async processing | Futuristic rotating radar/gradient scanner |
| `ZorbiEmptyState` | Empty library/community views | Central icon, title, description, and CTA button slot |
| `ZorbiErrorCard` | Permission/System errors | Error styling with direct "Fix it" CTA button |
| `ZorbiScaffold` | Top-level screen scaffold | Safe insets, consistent 16dp horizontal padding, and integrated top bar |
| `SciFiChamferedPanel` | Sci-fi gaming HUD panel | Cut-corner octagonal container with multi-gradient neon border |
| `SciFiAngledTab` | Navigation rail tabs | Angled parallelogram tab with vibrant active gradient and neon glow |
| `SciFiPillToggle` | Sci-fi switches | Glowing pill switch (`ON`/`OFF`) with tactical haptics |
| `SciFiNeonSlider` | Audio/Sensitivity sliders | Futuristic range slider with illuminated neon track and thumb |

---

## Task P3-1: Navigation Host, Home Screen & Game Library

### 1. Navigation Contract (Master Prompt Section 5.4 - FROZEN)
All 8 frozen routes are fully declared and handled in `com.zorbi.ui.navigation.ZorbiNavHost`:
- `NavRoutes.HOME` (`"home"`): Application landing dashboard
- `NavRoutes.LIBRARY` (`"library"`): Installed game profiles list
- `NavRoutes.WIZARD` (`"wizard"`): Guided permissions & system setup
- `NavRoutes.CALIBRATE` (`"calibrate/{packageName}"`): Motion calibrator
- `NavRoutes.PLAY` (`"play/{packageName}"`): In-game gesture HUD overlay
- `NavRoutes.COMMUNITY` (`"community"`): Cloud profile repository
- `NavRoutes.SETTINGS` (`"settings"`): App settings & telemetry opt-in
- `NavRoutes.ABOUT` (`"about"`): Privacy statement & build info

### 2. Home Screen (`com.zorbi.ui.screens.home.HomeScreen`)
- **Branding Header:** Displays glowing ZORBI title, engine state badge, and quick access to About dialog.
- **System Readiness Banner:** Real-time visual status. Turns green only when Camera, Accessibility Service, and Battery Optimization exemption are fully verified. Tap action navigates straight to Setup Wizard.
- **Prerequisite Status Chips:** 3 status chips for Camera, Accessibility Service, and Battery Optimization.
- **Hero Play Card:** Features the active/last configured game with package name, orientation badge, tracker type badge, and mapping badge. Features prominent `PLAY NOW` (min 48dp) and `CALIBRATE PROFILE (60s)` action buttons.
- **Control Center Grid:** Clean tile navigation to Game Library, Setup Wizard, Community Profiles, and Settings.

### 3. Game Library (`com.zorbi.ui.screens.library.GameLibraryScreen`)
- **Profile List:** Asynchronously queries `ProfileRepository.list()` from `AppGraph`.
- **Filtering Engine:** Instant category filtering across tracker types (1-Hand, 2-Hand Wheel, Pose Lean) and mapping types (Lane Swipe, Air Slash, Steer).
- **Game Cards:** Detailed cards featuring package name, tracker and mapping badges, with direct "PLAY" and "CALIBRATE" launch triggers.

---

## Task P3-2: Android Setup Wizard (System Readiness & OEM Guidance)

### 1. Architecture & Core Subsystems (`com.zorbi.ui.screens.wizard`)
- **Lifecycle Awareness & Reactive Check on Resume:** Re-evaluates permissions when returning from system settings.
- **4 Required Setup Steps:** Accessibility Service, Camera runtime permission, Foreground notification (Android 13+), Battery Optimization exemption.
- **Android 13+ "Restricted Settings" Guidance:** Step-by-step guidance modal to unlock sideloaded APK accessibility restrictions.
- **Vivo / OriginOS Background Guidance:** Instructions for Autostart, High Background Power, and Recents locking.

---

## Task P3-3: 60-Second Motion Calibrator (`com.zorbi.ui.screens.calibrator`)

### 1. Architecture & Live Flow
- **Guided 60-Second Sequence:** 4 distinct phases (Alignment, Neutral Posture, Range of Motion, Computation).
- **Animated Circular Countdown Ring:** Smooth radial sweep with live seconds counter.
- **Live Skeleton Canvas (`SkeletonCanvas`):** Renders glowing neon lines and joints from real normalized points.
- **Result Card (`CalibrationResultCard`):** Displays computed neutral and max steering angles and reach spans.

---

## Task P3-4: Gaming HUD Overlay (`com.zorbi.ui.hud`)

### 1. Architecture & Core Subsystems
- **Data Flow Integration:** Consumes real-time `HudFrame` from `EngineController.hud` (Flow) containing `landmarks: List<Pt>`, `lastGesture: GestureEvent?`, `fps: Float`, and `e2eLatencyMs: Float`.
- **Minimal Diagnostic Corner Chip:** Displays real measured FPS and e2e latency (ms) in the top-right corner.
- **HUD Opacity Toggle:** Allows user to cycle HUD visibility presets (`100% (FULL)` -> `40% (SUBTLE)` -> `15% (MINIMAL)` -> `Off (HIDDEN)`), ensuring zero gaming view obstruction.
- **Dynamic Gesture Visual Effects:**
  - `GestureRippleOverlay`: Animates expanding neon ripples along lane swipes and glowing laser cutting paths for air-slashes.
  - Smooth fadeout animation over 600ms via `Animatable`.
- **Steering Gauge (`SteeringGauge`):**
  - Displays real-time steering angle and normalized value in $[-1.0, 1.0]$.
  - Features dedicated deadzone indicator and smooth tick indicator.
- **Fist-Pause Indicator Badge (`PauseIndicatorBadge`):**
  - Displays a high-visibility amber badge when `PauseToggle` is detected or the engine is paused.
- **Game Control Occlusion Avoidance:**
  - All HUD elements strictly respect the 20% screen edge margin per Section 13, keeping controls unobstructed.
- **Session Screen Integration:**
  - Route `play/{packageName}` wired to [PlayScreen.kt](file:///C:/Users/nerun/Desktop/ZORBI/ui/src/main/java/com/zorbi/ui/screens/play/PlayScreen.kt), with engine start/stop lifecycle management.

### 2. Verification (Test Gate T-P3-4)
- Unit tests: `com.zorbi.ui.hud.HudStateTest`:
  - Opacity preset cycling order and alpha values.
  - Steer clamping within $[-1.0, 1.0]$.
  - Gesture visual lifetime decay.
- Previews:
  - `PreviewGamingHudPhone`, `PreviewGamingHudSteeringTablet`, `PreviewPauseBadge`, `PreviewSteeringGauge`.


---

## Task P3-5: Settings, Community & About Screens

### 1. Architecture & Core Subsystems

#### A. Settings Screen (`com.zorbi.ui.screens.settings`)
* **Performance Mode Selection:**
  - `QUALITY`: High resolution, maximum precision, ~30 FPS target for Snapdragon 6 Gen 1+ or flagship devices.
  - `BALANCED` (Default): Dynamic frame rate, adaptive smoothing, optimal thermal and battery efficiency.
  - `PERFORMANCE`: Downsampled frames, lightweight inference, ~15 FPS cap for budget hardware.
  - Directly maps to frozen `com.zorbi.core.PerfMode` enum.
* **Anonymous Telemetry & Strict Opt-In Consent:**
  - Adheres strictly to Section 5.5 and privacy mandates: default value is `false`.
  - Never uploads data without explicit user consent.
  - Toggling ON displays an explicit `AlertDialog` detailing aggregate metrics (FPS, inference ms, session duration, gesture counts) and emphasizing the **Zero-Cloud Vision Guarantee** (zero camera frames or video data ever leave the device).
  - Toggling OFF immediately disables telemetry collection.
* **Persistence & Section 5.5 Keys:**
  - `perf_mode`: String enum name (`QUALITY`, `BALANCED`, `PERFORMANCE`).
  - `telemetry_opt_in`: Boolean (`false` default).
  - `device_id`: Generated UUID string (preserved across resets).
  - `wizard_done`: Boolean indicating setup wizard completion.
  - `last_game`: Package name of the last played game.
* **Reset Functionality:**
  - Confirmation modal resets performance to `BALANCED`, disables telemetry, and resets wizard/session state while keeping installed game profiles intact.

#### B. Community Profiles Screen (`com.zorbi.ui.screens.community`)
* **Cloud Repository Browsing:**
  - Displays curated verified profiles (e.g. Subway Surfers, Hill Climb Racing, Temple Run 2, Fruit Ninja, Asphalt Legends).
  - Real-time search filter by title, package name, or author.
  - Category filtering: `ALL`, `STEER`, `SWIPE`, `SLASH`, `POSE`.
* **Real Repository Import & Zero Fake Success:**
  - Directly calls `AppGraph.profiles.importFromCloud(id: String): GameProfile?`.
  - When non-null is returned: updates state to display "INSTALLED IN LOCAL LIBRARY" badge and informs user.
  - When null is returned: displays defensive error card ("Cloud profile unavailable or remote service offline. Check network connection.") without pretending the import succeeded.
* **Defensive Lifecycle States:**
  - Loading: `ZorbiLoadingIndicator`.
  - Empty: `ZorbiEmptyState` with category/search reset action.
  - Error: `ZorbiErrorCard` per card with inline error feedback.

#### C. About Screen (`com.zorbi.ui.screens.about`)
* **Authoritative Problem Statement (Master Prompt Appendix A):**
  - "Mobile touch gaming creates an artificial barrier. Screen controls obscure visual gameplay, induce thumb fatigue, and restrict players with motor or dexterity impairments. ZORBI transforms any budget or flagship Android device's front-facing camera into an ultra-low-latency, contactless motion controller. Powered by on-device MediaPipe landmark detection and native AccessibilityService touch injection, ZORBI enables touchless steering, slashing, swiping, and tap gestures without modifying game binaries or requiring external peripherals. With a strict 100% on-device vision pipeline, zero camera frames or video data ever leave the device. Designed specifically for low-end chipsets, ZORBI delivers responsive gameplay through sub-30ms end-to-end latency, a 60-second personalized calibration sequence, and an open JSON game profile specification."
* **On-Device Vision Guarantee:**
  - Prominent green glass banner detailing local-RAM inference, offline gesture operation, zero frame upload, and opt-in aggregate telemetry.
* **Team Credits (Section 7.1 CODEOWNERS):**
  - Person 1: Vision & Calibration (`:vision`, `:calib`).
  - Person 2: Gesture, Input & Engine (`:gesture`, `:input`, `:engine`).
  - Person 3: App Shell, UI & Setup Wizard (`:app`, `:ui`).
  - Person 4: Backend, Data, Tools & CI (`:data`, `:supabase`, CI).

### 2. Navigation Integration (`ZorbiNavHost`)
All 8 frozen routes per Section 5.4 are now 100% connected to production UI screens:
1. `home` -> `HomeScreen`
2. `library` -> `GameLibraryScreen`
3. `wizard` -> `WizardScreen`
4. `calibrate/{packageName}` -> `CalibrateScreen`
5. `play/{packageName}` -> `PlayScreen`
6. `community` -> `CommunityScreen`
7. `settings` -> `SettingsScreen`
8. `about` -> `AboutScreen`

### 3. Verification & Previews
* **Unit Tests:**
  - `com.zorbi.ui.screens.settings.SettingsStateTest`: Default values, Section 5.5 keys, PerfMode enum safety, reset logic.
  - `com.zorbi.ui.screens.community.CommunityStateTest`: Schema version 1 conversion, category filtering, repository null handling.
  - `com.zorbi.ui.screens.about.AboutSpecTest`: Kodikon v1.0 versioning, privacy statements, four-member team responsibilities.
  - `com.zorbi.ui.navigation.NavigationTest`: 8 frozen navigation routes verification.
* **Compose Previews:**
  - `PreviewSettingsPhoneBalanced`, `PreviewSettingsPhoneQualityOptedIn`, `PreviewSettingsTablet`.
  - `PreviewCommunityPhone`, `PreviewCommunityTablet`.
  - `PreviewAboutPhone`, `PreviewAboutTablet`.


---

## Task P3-6: UI Polish, Animations, Haptics & Adaptive App Icon

### 1. Architecture & Core Subsystems

#### A. Tactical Haptic Feedback (`com.zorbi.ui.haptics.ZorbiHaptics`)
* **Contract & Event Design:**
  - `click()`: Subtle key tap feedback for button presses, navigation tabs, and chips (`HapticFeedbackConstants.KEYBOARD_TAP` / `EFFECT_CLICK`).
  - `confirm()`: Affirmative tactile pulse for successful calibration, profile import, or permission grant (`HapticFeedbackConstants.CONFIRM`).
  - `warning()`: Double-pulse caution for errors, denied permissions, or lost tracking (`HapticFeedbackConstants.REJECT`).
  - `tick()`: Ultra-light segment tick for countdown seconds, steering slider adjustments (`HapticFeedbackConstants.SEGMENT_TICK` / `CLOCK_TICK`).
  - `toggle()`: Crisp switch toggle feedback (`HapticFeedbackConstants.TOGGLE_ON`).
* **Hardware & Accessibility Safety:**
  - Integrated into all buttons ([`ZorbiButton.kt`](file:///C:/Users/nerun/Desktop/ZORBI/ui/src/main/java/com/zorbi/ui/components/ZorbiButton.kt)) via `rememberZorbiHaptics()`.
  - Non-intrusive: strictly avoids continuous vibration to prevent interfering with camera tracking or battery.
  - Headless-safe: gracefully falls back to `NoOpZorbiHaptics` in Compose previews and JVM tests.

#### B. Visual Polish & Micro-Animations (`com.zorbi.ui.animations`)
* **Interactive Spring Bounce (`Modifier.interactiveBounce`):**
  - Instantaneous spring scale compression to `~0.965f` upon pointer press, providing tactile responsiveness without input lag.
* **Ambient Breathing Glow (`Modifier.pulsingGlow`):**
  - Smooth radial gradient glow in `#00E5FF` (Neon Cyan) or `#7C4DFF` (Accent Violet) behind hero play cards and status indicators.
* **Pulsing Border (`Modifier.pulsingBorder`):**
  - Rhythmic border alpha pulse highlighting active calibrator or setup cards.
* **High-Tech Gaming Shimmer (`Modifier.gamingShimmer`):**
  - Linear gradient sweep across skeleton frames and loading placeholders.

#### C. Smooth Screen Navigation Transitions (`ZorbiNavHost.kt`)
* **Global Screen Transition Spec:**
  - Forward navigation: `slideIntoContainer(Start, 260ms) + fadeIn(220ms)`.
  - Backward navigation: `slideOutOfContainer(End, 260ms) + fadeOut(180ms)`.
* **Gaming HUD Overlay Route (`play/{packageName}`):**
  - Custom full-screen scale-in (`scaleIn(0.92f, 280ms) + fadeIn(220ms)`), establishing an immersive transition into live gesture control.

#### D. Adaptive Launcher Icon (`app/src/main/res/drawable/ic_launcher_foreground.xml`)
* **Vector Design Specification:**
  - 108dp x 108dp adaptive format conforming to Android 8.0+ (API 26+) adaptive icon standards.
  - Features the stylized geometric **ZORBI Monogram** in `#00E5FF` (Neon Cyan) with dynamic diagonal slash.
  - Lateral motion flank accents in `#7C4DFF` (Accent Violet).
  - Ambient circular radar targeting reticle with 4 cardinal camera calibration ticks.
  - Central camera focal sensor node centered at $(54, 54)$ within the safe zone.
  - Paired with `@color/ic_launcher_background` (`#0A0E1A`).

### 2. Verification (Test Gate T-P3-6)
* **Unit Tests:**
  - `com.zorbi.ui.haptics.ZorbiHapticsTest`: Headless safety and null-context resilience.
  - `com.zorbi.ui.animations.AnimationsTest`: Transition duration caps (<=300ms) and glow alpha bounds.
* **Compose Previews:**
  - `PreviewPolishGlowAndBorder` in [`PolishPreviews.kt`](file:///C:/Users/nerun/Desktop/ZORBI/ui/src/main/java/com/zorbi/ui/components/PolishPreviews.kt).


---

## Task P3-7: Final Integration Review, System Audit & Handover

### 1. Final Screen Inventory

| Screen | Target Route | Core Responsibilities | Implementation File |
| :--- | :--- | :--- | :--- |
| **Home** | `home` | Landing dashboard, branding header, system readiness banner, permission status chips, hero play card, quick action tiles | [`HomeScreen.kt`](file:///C:/Users/nerun/Desktop/ZORBI/ui/src/main/java/com/zorbi/ui/screens/home/HomeScreen.kt) |
| **Game Library** | `library` | Async catalog browsing, tracker & mapping filters, profile cards, direct play & calibrate triggers, empty state | [`GameLibraryScreen.kt`](file:///C:/Users/nerun/Desktop/ZORBI/ui/src/main/java/com/zorbi/ui/screens/library/GameLibraryScreen.kt) |
| **Setup Wizard** | `wizard` | 4-step system setup (Accessibility, Camera, Notification, Battery), Vivo/OriginOS guidance, Restricted Settings help dialog | [`WizardScreen.kt`](file:///C:/Users/nerun/Desktop/ZORBI/ui/src/main/java/com/zorbi/ui/screens/wizard/WizardScreen.kt) |
| **Calibrator** | `calibrate/{packageName}` | 60-second guided countdown, live MediaPipe 21/33 landmark canvas, neutral angle and reach computation, result card | [`CalibrateScreen.kt`](file:///C:/Users/nerun/Desktop/ZORBI/ui/src/main/java/com/zorbi/ui/screens/calibrator/CalibrateScreen.kt) |
| **Gaming HUD / Play** | `play/{packageName}` | Live camera landmark skeleton overlay, real-time FPS & latency diagnostic chip, steering gauge, gesture ripples, fist-pause badge | [`PlayScreen.kt`](file:///C:/Users/nerun/Desktop/ZORBI/ui/src/main/java/com/zorbi/ui/screens/play/PlayScreen.kt) & [`GamingHudOverlay.kt`](file:///C:/Users/nerun/Desktop/ZORBI/ui/src/main/java/com/zorbi/ui/hud/GamingHudOverlay.kt) |
| **Community** | `community` | Cloud profile browsing, keyword search, category filter chips, `ProfileRepository.importFromCloud()` integration, zero-fake-success error handling | [`CommunityScreen.kt`](file:///C:/Users/nerun/Desktop/ZORBI/ui/src/main/java/com/zorbi/ui/screens/community/CommunityScreen.kt) |
| **Settings** | `settings` | Vision performance mode (`QUALITY`, `BALANCED`, `PERFORMANCE`), anonymous telemetry opt-in toggle with consent modal, device ID, preference reset | [`SettingsScreen.kt`](file:///C:/Users/nerun/Desktop/ZORBI/ui/src/main/java/com/zorbi/ui/screens/settings/SettingsScreen.kt) |
| **About** | `about` | Master Prompt Appendix A mission statement, 100% on-device vision guarantee, technical specifications, 4-member team credits | [`AboutScreen.kt`](file:///C:/Users/nerun/Desktop/ZORBI/ui/src/main/java/com/zorbi/ui/screens/about/AboutScreen.kt) |

---

### 2. Frozen Navigation Route Inventory (Section 5.4)

All 8 navigation routes are strictly declared and connected in [`ZorbiNavHost.kt`](file:///C:/Users/nerun/Desktop/ZORBI/ui/src/main/java/com/zorbi/ui/navigation/ZorbiNavHost.kt):
1. `home` — Application landing screen.
2. `library` — Installed game profiles screen.
3. `wizard` — Guided system readiness setup wizard.
4. `calibrate/{packageName}` — 60-second motion calibration screen.
5. `play/{packageName}` — In-game gesture HUD overlay and engine session screen.
6. `community` — Cloud motion profile repository screen.
7. `settings` — App & engine settings screen.
8. `about` — Version, privacy guarantee, and credits screen.

---

### 3. Cross-Team Integration & Contract Compatibility

#### A. Person 1 (Vision & Calibration)
* **Contracts Reused:** `com.zorbi.core.TrackerFrame`, `HandsFrame`, `PoseFrame`, `Calibration`, `CalibrationRepository`.
* **Verification:** [`SkeletonCanvas.kt`](file:///C:/Users/nerun/Desktop/ZORBI/ui/src/main/java/com/zorbi/ui/screens/calibrator/SkeletonCanvas.kt) renders MediaPipe 21 hand landmarks and 33 pose landmarks upright and mirrored from normalised coordinates. [`CalibrateScreen.kt`](file:///C:/Users/nerun/Desktop/ZORBI/ui/src/main/java/com/zorbi/ui/screens/calibrator/CalibrateScreen.kt) correctly delegates sample calculation to `CalibrationRepository.computeFromSamples()`.
* **Zero Ownership Violations:** No edits made to `:vision` or `:calib`.

#### B. Person 2 (Gesture, Input & Engine)
* **Contracts Reused:** `com.zorbi.core.EngineController`, `EngineState`, `HudFrame`, `InputInjector`, `GestureEvent` (`Steer`, `Swipe`, `Slash`, `PauseToggle`).
* **Verification:** [`GamingHudOverlay.kt`](file:///C:/Users/nerun/Desktop/ZORBI/ui/src/main/java/com/zorbi/ui/hud/GamingHudOverlay.kt) consumes real `HudFrame` data from `EngineController.hud` (Flow). Steering angle and deadzone are visualized via [`SteeringGauge.kt`](file:///C:/Users/nerun/Desktop/ZORBI/ui/src/main/java/com/zorbi/ui/hud/SteeringGauge.kt). `PauseToggle` displays [`PauseIndicatorBadge.kt`](file:///C:/Users/nerun/Desktop/ZORBI/ui/src/main/java/com/zorbi/ui/hud/PauseIndicatorBadge.kt).
* **Zero Ownership Violations:** No edits made to `:gesture`, `:input`, or `:engine`.

#### C. Person 4 (Backend, Data, Tools & CI)
* **Contracts Reused:** `com.zorbi.core.ProfileRepository`, `com.zorbi.core.TelemetrySink`, Section 5.5 DataStore keys (`perf_mode`, `telemetry_opt_in`, `device_id`, `wizard_done`, `last_game`).
* **Verification:** [`CommunityScreen.kt`](file:///C:/Users/nerun/Desktop/ZORBI/ui/src/main/java/com/zorbi/ui/screens/community/CommunityScreen.kt) directly invokes `ProfileRepository.importFromCloud(id)` with zero fake success. [`SettingsState.kt`](file:///C:/Users/nerun/Desktop/ZORBI/ui/src/main/java/com/zorbi/ui/screens/settings/SettingsState.kt) matches Section 5.5 keys 1:1, ensuring seamless drop-in when Person 4 delivers the shared `:data` DataStore implementation.
* **Zero Ownership Violations:** No edits made to `:data`, `:supabase`, `/tools/`, or CI workflows.

---

### 4. Verification Suite & Test Coverage

| Test Suite | Target Package / Component | Verified Behaviors |
| :--- | :--- | :--- |
| `ThemeTest` | `com.zorbi.ui.theme` | Color palette tokens, typography scales, spacing metrics, and 48dp touch targets |
| `NavigationTest` | `com.zorbi.ui.navigation` | 8 frozen route names, dynamic parameter route generators, system readiness logic |
| `WizardStateTest` | `com.zorbi.ui.screens.wizard` | Step counting, all-green condition, prerequisite verification |
| `CalibratorStateTest` | `com.zorbi.ui.screens.calibrator` | 4-phase sequence flow, 60s countdown progression, error transitions |
| `HudStateTest` | `com.zorbi.ui.hud` | Opacity presets cycling, steering angle clamping $[-1.0, 1.0]$, gesture decay |
| `SettingsStateTest` | `com.zorbi.ui.screens.settings` | Section 5.5 keys, PerfMode enum safety, opt-in privacy default `false`, reset logic |
| `CommunityStateTest` | `com.zorbi.ui.screens.community` | Schema version 1 conversion, category filtering, repository null import handling |
| `AboutSpecTest` | `com.zorbi.ui.screens.about` | Versioning (Kodikon Edition), privacy guarantees, 4-member team responsibilities |
| `AnimationsTest` | `com.zorbi.ui.animations` | Low-latency transition duration caps ($\le 300\text{ ms}$), glow alpha bounds |
| `ZorbiHapticsTest` | `com.zorbi.ui.haptics` | Headless execution safety, null context resilience for all haptic events |

---

### 5. Known Limitations & Outstanding Dependencies
* **Toolchain Blocker CCR-01:** `org.gradle.java.home` in `gradle.properties` line 11 points to machine-specific path `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`. Until Integration Captain comments out or updates line 11, command-line `./gradlew` execution fails during Java home validation.
* **UI Screenshot Capture:** Physical application screenshot capture is deferred until CCR-01 is unblocked and the APK can be assembled. Previews are available for all 8 screens.
