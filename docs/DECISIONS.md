# ZORBI — Architecture Decision Records (ADRs)

> **Single Source of Truth:** `ZORBI_MASTER_PROMPT.md.pdf`  
> **Log Rule:** Append-only log maintained by Owner Agent / Integration Captain.

---

## ADR-001: Pinned Build Toolchain & Version Catalog (Phase 0 / Gate G0)

* **Date:** 2026-10-31
* **Author:** Integration Captain (Person 2)
* **Status:** ACCEPTED

### Context
Section 4.1, 4.3, and 7.2 (#4) of the Master Prompt mandate pinning stable versions of the core build toolchain and libraries in `gradle/libs.versions.toml`, verified from official sources, and recording them in `docs/DECISIONS.md`. No versions may be invented or altered without a formal Contract Change Request (CCR).

### Decisions & Verified Official Sources

| Component | Pinned Version | Official Source / Verification | Rationale |
| :--- | :--- | :--- | :--- |
| **Java Development Kit (JDK)** | **17 (Temurin 17.0.20.1)** | Eclipse Adoptium OpenJDK 17 (`C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`) | Mandated by Master Prompt Section 4.1; required for AGP 8.0+. Configured via `org.gradle.java.home` in `gradle.properties`. |
| **Android Minimum SDK** | **26 (Android 8.0 Oreo)** | Android Developer Guide (`AccessibilityService.dispatchGesture`) | Mandated by Master Prompt Section 4.1 to support continuing gesture strokes via `GestureDescription.StrokeDescription.willContinue`. |
| **Android Compile & Target SDK** | **34 (Android 14)** | Android Developer SDK Platform releases | Latest stable platform target fully supported by Android Studio 2026.2.2. |
| **Gradle Distribution** | **8.9** | [Gradle Releases](https://services.gradle.org/distributions/) | Official Gradle release compatible with AGP 8.6.x/8.7.x and Java 17. |
| **Android Gradle Plugin (AGP)** | **8.6.1** | [Google Maven / Android Developer Release Notes](https://developer.android.com/build/releases/gradle-plugin) | Official stable AGP channel for JDK 17 builds. Requires Gradle 8.7+. |
| **Kotlin Language & Toolchain** | **2.0.21** | [JetBrains Kotlin Releases](https://kotlinlang.org/docs/releases.html) | Kotlin 2.0+ series includes the first-party Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`), eliminating detached compiler extension versioning. |
| **Compose Compiler Plugin** | **2.0.21** | [Android Developers Compose Setup](https://developer.android.com/jetpack/compose/setup) | Bundled 1:1 with Kotlin 2.0.21 (`org.jetbrains.kotlin.plugin.compose`). |
| **Jetpack Compose BOM** | **2024.10.01** | [Android Developers Compose BOM Mapping](https://developer.android.com/jetpack/compose/bom/bom-mapping) | Canonical BOM mapping for Material 3 and stable Compose UI artifacts. |
| **CameraX** | **1.3.4** | [Android Jetpack CameraX Releases](https://developer.android.com/jetpack/androidx/releases/camera) | Stable CameraX suite for `ImageAnalysis` front camera capture. |
| **MediaPipe Tasks Vision** | **0.10.14** | [Google Maven / MediaPipe Tasks](https://developers.google.com/mediapipe/solutions/vision) | Official stable Android release for Hand Landmarker and Pose Landmarker Lite models. |
| **Kotlin Coroutines** | **1.9.0** | [Kotlinx Coroutines Releases](https://github.com/Kotlin/kotlinx.coroutines) | Compatible with Kotlin 2.0.21 for async flows (`SharedFlow` / `StateFlow`). |
| **OkHttp** | **4.12.0** | [Square OkHttp Releases](https://square.github.io/okhttp/) | Lightweight REST client for Supabase PostgREST (Section 4.1). |
| **kotlinx.serialization** | **1.7.3** | [Kotlinx Serialization Releases](https://github.com/Kotlin/kotlinx.serialization) | Pure Kotlin JSON parser for GameProfile and landmark log fixtures. |
| **Jetpack DataStore (Preferences)** | **1.1.1** | [Android Jetpack DataStore Releases](https://developer.android.com/jetpack/androidx/releases/datastore) | Coroutine-first key-value storage for app preferences. |
| **JUnit 4** | **4.13.2** | [JUnit 4 Releases](https://junit.org/junit4/) | JVM unit testing for interpreters, filters, calibration math, and profile parsing. |
| **Truth** | **1.4.4** | [Google Truth Releases](https://truth.dev/) | Fluent assertions for unit test suites. |
| **MockK** | **1.13.13** | [MockK Releases](https://mockk.io/) | Kotlin-native mocking library for local tests. |
| **Turbine** | **1.2.0** | [CashApp Turbine](https://github.com/cashapp/turbine) | Testing library for Kotlin Coroutine Flows. |

### Module & File Ownership Rules
* Modular layout follows Section 7.1: `:core`, `:vision`, `:calib`, `:gesture`, `:input`, `:engine`, `:ui`, `:data`, `:app`, `:fakes`.
* All catalog entries are strictly partitioned into reserved blocks (`# --- P1 ---` through `# --- P4 ---`).
* Version changes require CCR process (Section 17.3).

### Outstanding Compatibility Checks (Per Master Prompt v1.1 & Q9)
1. **Device OS Target Verification (Q9):** Person 1 confirmed the primary demo phone (iQOO Z9x, Snapdragon 6 Gen 1) runs Android 14 (API 34), whereas the secondary test phone (Z9 Lite) OS version is pending confirmation. `compileSdk 34` and `targetSdk 34` match the Z9x runtime; physical on-device execution will confirm any vendor-specific (OriginOS) restrictions.
2. **MediaPipe Tasks Vision (0.10.14) & CameraX (1.3.4):** Verified against Maven and build toolchain; physical runtime verification of frame rate and inference latency on Snapdragon 6 Gen 1 CPU delegate is tracked in Person 1's Spike S1 (T-P1-1).
3. **Touch Injection & Continuing Strokes (minSdk 26 / Android 14):** `GestureDescription.StrokeDescription.willContinue` API behavior under Android 14 "Restricted Settings" and background accessibility dispatch is tracked in Person 2's Spike S2 (Gate G1).
4. **Supabase PostgREST (OkHttp 4.12.0):** Network security config and plain REST behavior without heavy SDK under Android 14 runtime tracked in Person 4's Task P4-1.


