# ZORBI — Person 1 (Vision & Calibration) Documentation

## 1. Overview & System Scope

**Person 1** is responsible for the vision pipeline, optical landmark tracking, noise filtering, tracking performance control, profile calibration persistence, and physical gesture triggers in the ZORBI real-time motion control system.

### Owned Modules
* **`:vision`**: CameraX capture, MediaPipe Tasks Vision integration, coordinate transformations, tracker implementations (`HAND_SINGLE`, `HAND_DUAL_WHEEL`, `POSE_LITE`), JSONL landmark recording & replay, One Euro filtering, and the dynamic `PerfController`.
* **`:calib`**: Pure calibration geometry math, file-system persistence (`RealCalibrationRepository`), fixture verification, and geometric `FistDetector` (Fist-to-Pause).

---

## 2. Architecture & Subsystems

```
                       ┌─────────────────────────┐
                       │  CameraX ImageAnalysis  │
                       │   (Front Camera YUV)    │
                       └────────────┬────────────┘
                                    │ ImageProxy
                                    ▼
                       ┌─────────────────────────┐
                       │ Bitmap Upright Matrix   │
                       │   Rotation (e.g. 270°)  │
                       └────────────┬────────────┘
                                    │ MPImage
                                    ▼
                       ┌─────────────────────────┐
                       │ MediaPipe Tasks Vision  │
                       │   (CPU Delegate)        │
                       └────────────┬────────────┘
                                    │ NormalizedLandmarks [0..1]
                                    ▼
                       ┌─────────────────────────┐
                       │  CoordinateTransforms   │
                       │  - Mirroring (1.0 - x)  │
                       │  - Pt(x, y, z) Core     │
                       └────────────┬────────────┘
                                    │
           ┌────────────────────────┼────────────────────────┐
           ▼                        ▼                        ▼
┌─────────────────────┐  ┌─────────────────────┐  ┌─────────────────────┐
│  HandSingleTracker  │  │ HandDualWheelTracker│  │   PoseLiteTracker   │
│  (1 Hand, 21 LMs)   │  │  (2 Hands, 42 LMs)  │  │  (1 Body, 33 LMs)   │
└──────────┬──────────┘  └──────────┬──────────┘  └──────────┬──────────┘
           │                        │                        │
           ▼                        ▼                        ▼
┌─────────────────────┐  ┌─────────────────────┐  ┌─────────────────────┐
│    OneEuroFilter    │  │    Steering Math    │  │   Body Lean Math    │
└──────────┬──────────┘  └─────────────────────┘  └─────────────────────┘
           │
           ├────────────────────────┐
           ▼                        ▼
┌─────────────────────┐  ┌─────────────────────┐
│    FistDetector     │  │   PerfController    │
│  (~1.0s Hold Pause) │  │  (P95 > 80ms Guard) │
└──────────┬──────────┘  └─────────────────────┘
           │
           ▼
┌─────────────────────┐
│ PauseToggle Event   │
└─────────────────────┘
```

---

## 3. Coordinate Pipeline & Transformations

### A. Coordinate Space Contract
All normalized landmarks adhere strictly to the frozen `:core` contract:
* **Type**: `com.zorbi.core.Pt(val x: Float, val y: Float, val z: Float = 0f)`
* **Coordinate Bounds**: $0.0 \le x \le 1.0$, $0.0 \le y \le 1.0$.
* **Origin**: Top-left corner $(0, 0)$ of the upright display viewport.
* **Front Camera Mirroring**: Mirrored horizontally so user movements match a natural selfie reflection (user's right hand appears on the right side of the screen).
* **Depth ($z$)**: Orthographic/camera-relative depth, with smaller values closer to the camera lens.

### B. Pre-Rotation & MediaPipe Tensor Skew Prevention
In MediaPipe Tasks Vision (`0.10.14`), supplying `setRotationDegrees(270)` to `detectAsync()` invokes `ImageToTensorCalculator`, which applies a skewed cropping matrix on Android front-facing portrait frames. This causes false hand detections on facial landmarks and misses the physical hand.

**Solution Implemented in all Trackers**:
1. CameraX produces sensor buffers (e.g., $1280 \times 960$) with `rotationDegrees = 270`.
2. Extract bitmap: `val rawBitmap = imageProxy.toBitmap()`.
3. Pre-rotate upright:
   ```kotlin
   val rotatedBitmap = if (rotationDegrees != 0) {
       val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
       Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
   } else {
       rawBitmap
   }
   ```
4. Feed `mpImage` to MediaPipe with rotation angle `0`.
5. Transform normalized coordinates to selfie mirror space:
   ```kotlin
   val mx = (1.0f - lm.x()).coerceIn(0.0f, 1.0f)
   val my = lm.y().coerceIn(0.0f, 1.0f)
   return Pt(x = mx, y = my, z = lm.z())
   ```
6. The resulting coordinates map with zero-offset precision across all display layouts.

---

## 4. Trackers (`:vision`)

### A. `HandSingleTracker` (Task P1-1, Gate T-P1-1)
* **Contract**: Implements `com.zorbi.core.Tracker`.
* **Output**: `SharedFlow<TrackerFrame>` emitting `HandsFrame(tsNanos, hands)` where `hands` contains at most 1 `Hand` (21 landmarks).
* **Side Determination**: Mapped from MediaPipe handedness (`Left` / `Right`) to `com.zorbi.core.Side`.
* **Modes**:
  - `QUALITY`: $1280 \times 720$ target resolution, unrestricted framerate.
  - `BALANCED`: $640 \times 480$ target resolution, unrestricted framerate.
  - `PERFORMANCE`: $640 \times 480$ target resolution, 66 ms frame-interval throttling (~15 FPS cap).
* **Resource Safety**: Uses `Mutex` for start/stop synchronization, increments monotonic session IDs to discard in-flight callbacks from stale runs, and guarantees `imageProxy.close()` inside `finally`.

### B. `HandDualWheelTracker` (Task P1-5, Gate T-P1-5)
* **Contract**: Implements `com.zorbi.core.Tracker`.
* **Capabilities**: Simultaneous two-hand detection (`numHands = 2`).
* **Steering Output**: Calculates virtual steering wheel angle from left and right wrist/MCP coordinates:
  $$\theta = \operatorname{atan2}(y_{\text{right}} - y_{\text{left}}, x_{\text{right}} - x_{\text{left}})$$
* **Performance**: Maintains concurrent dual-hand tracking on physical hardware.

### C. `PoseLiteTracker` (Task P1-6, Gate T-P1-6)
* **Contract**: Implements `com.zorbi.core.Tracker`.
* **Model**: Bundled Lite model `pose_landmarker_lite.task` in assets.
* **Output**: `PoseFrame(tsNanos, score, landmarks)` with 33 full-body landmarks.
* **Body Lean Calculation**: Computes lateral torso lean from shoulder midpoint to hip midpoint.

### D. `ReplayTracker` & `LandmarkJsonl` (Task P1-2, Gate T-P1-2)
* **Contract**: Offline, deterministic tracker replay for CI testing and gesture interpreter development.
* **Serialization**: Pure `kotlinx.serialization` JSONL streaming (`LandmarkJsonl`).
* **Playback Precision**: Accurately preserves inter-frame delta intervals in nanoseconds without drift.

---

## 5. Noise Filtering & Performance Controller

### A. One Euro Filter (Task P1-3, Gate T-P1-3)
* **Location**: `com.zorbi.vision.filter.OneEuroFilter`
* **Mechanism**: Dual low-pass filter with adaptive cutoff frequency:
  $$f_c = f_{c,\min} + \beta \cdot |\dot{x}|$$
* **Parameters**:
  - $f_{c,\min} = 1.0\text{ Hz}$ (smooths slow drift/quiver during stationary poses).
  - $\beta = 0.007$ (eliminates phase lag during fast steering sweeps).
  - $d_c = 1.0\text{ Hz}$ (derivative cutoff frequency).
* **Verification**: Unit tested for jitter reduction ($> 85\%$ variance suppression) and high-speed step responsiveness ($< 2$ frames lag).

### B. Performance Controller (Task P1-4, Gate T-P1-4)
* **Location**: `com.zorbi.vision.perf.PerfController`
* **Budget**: $80.0\text{ ms}$ P95 inference latency threshold.
* **State Machine**:
  - Automatically degrades mode from `QUALITY` / `BALANCED` to `PERFORMANCE` when rolling P95 latency exceeds 80.0 ms.
  - Implements 10-second stabilization hysteresis to prevent mode flapping.
  - Thread-safe lifecycle transitions using coroutines.

---

## 6. Calibration Subsystem (`:calib`)

### A. Mathematical Formulation (`CalibrationMath.kt`, Task P1-7)
* **Pure & Deterministic**: Zero side effects, synchronous execution.
* **Sentinel Timestamp**: Returns documented sentinel `createdAtEpochMs = 0L` to preserve pure function semantics.
* **Derived Quantities**:
  - `neutralAngleDeg`: Mean angle across neutral sample frames.
  - `maxDeflectionAngleDeg`: 95th percentile steering deflection angle.
  - `reachScaleX` / `reachScaleY`: Normalized physical span bounds.
* **Tolerances**: Synthetic neutral $\pm 1.0^\circ$, physical neutral $\pm 2.5^\circ$, reach scale $\pm 0.05$.

### B. Persistence (`RealCalibrationRepository.kt`, Task P1-7)
* **Storage Path**: `context.filesDir/calibrations/<packageName>.json`.
* **Epoch Timestamp**: `save()` assigns real Unix epoch time via `System.currentTimeMillis()` whenever the input calibration contains sentinel `0L`.
* **Atomic Serialization**: Saves and loads calibrations using `kotlinx.serialization.json.Json`.

---

## 7. Fist-to-Pause Subsystem (Task P1-8, Gate T-P1-8)

### A. Pure Landmark Geometric Classification (`FistDetector.kt`)
Operates on raw 21-point hand landmarks without auxiliary ML models:
1. **Dynamic Palm Scale**:
   $$D_{\text{palm}} = \operatorname{dist3D}(\text{WRIST}\,[0], \text{MIDDLE\_MCP}\,[9])$$
   Normalizes all distances to achieve distance and hand-size invariance.
2. **4-Finger Curl Metric**:
   - $\text{curlRatio} = \frac{\operatorname{dist}(\text{TIP}, \text{MCP})}{\sum \text{joint lengths}} < 0.68$
   - $\operatorname{dist}(\text{TIP}, \text{MCP}) < 0.80 \times D_{\text{palm}}$
   - $\operatorname{dist}(\text{TIP}, \text{WRIST}) < \operatorname{dist}(\text{PIP}, \text{WRIST}) + 0.05 \times D_{\text{palm}}$
3. **Thumb Curl Metric**:
   - $\operatorname{dist}(\text{THUMB\_TIP}, \text{PALM\_CENTER}) < 0.90 \times D_{\text{palm}}$
   - $\operatorname{dist}(\text{THUMB\_TIP}, \text{WRIST}) < 1.30 \times D_{\text{palm}}$

### B. Temporal State Machine
* **Hold Duration**: Requires continuous fist hold for $\ge 1.0\text{ s}$ (`1_000_000_000L` nanoseconds).
* **Single-Fire Guarantee**: Emits exactly **ONE** `PauseToggle(tsNanos)` event upon crossing the threshold. Continuous holds never repeat events.
* **Re-Arming**: Requires the hand to transition to `OPEN` state before re-arming.
* **Dropout Guard**: Tracking gaps $> 350\text{ ms}$ immediately reset the hold timer.
* **False Triggers**: Verified 0 false positives during 60 seconds (1,800 frames) of active play.

---

## 8. Physical Device Benchmarks & Verification

Measured on **vivo / iQOO Z9x 5G** (`10BE8634H4001GF`, Qualcomm Snapdragon 6 Gen 1, Android 16):

| Tracker | PerfMode | Framerate | P50 Latency | P95 Latency | Dropped | Thermal State |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| **HAND_SINGLE** | QUALITY | 20.0 FPS | 97.7 ms | 135.8 ms | 0 | 35.0°C (Normal) |
| **HAND_SINGLE** | BALANCED | 20.0 FPS | 88.2 ms | 118.5 ms | 0 | 35.0°C (Normal) |
| **HAND_SINGLE** | PERFORMANCE | 15.0 FPS | 72.4 ms | 78.1 ms | 0 | 34.8°C (Normal) |
| **HAND_DUAL_WHEEL**| BALANCED | 8.0 FPS | 116.7 ms | 186.9 ms | 0 | 35.4°C (Normal) |
| **POSE_LITE** | BALANCED | 26.0 FPS | 68.6 ms | 90.5 ms | 0 | 35.1°C (Normal) |
| **POSE_LITE** | PERFORMANCE | 17.0 FPS | 70.5 ms | 90.7 ms | 0 | 35.1°C (Normal) |

---

## 9. Test Suite & Validation Gates

All tests pass cleanly in local and device validation:

| Test Class | Scope / Subsystem | Test Count | Result |
| :--- | :--- | :---: | :---: |
| `CoordinateTransformsTest` | Rotation quarter-turns, mirroring, scaling, aspect ratios | 10 | **PASS** |
| `TrackerStatsCollectorTest` | FPS calculation, P50, P95 percentiles, dropped frames | 3 | **PASS** |
| `OneEuroFilterTest` | Signal smoothing, low latency response, cutoff formulas | 6 | **PASS** |
| `PerfControllerTest` | Budget limits, degradation to PERF, hysteresis timing | 4 | **PASS** |
| `LandmarkJsonlTest` | Recording & replay JSONL round-trip serialization | 3 | **PASS** |
| `HandDualWheelTest` | Dual hand steering angle math, side separation | 5 | **PASS** |
| `PoseLiteTest` | Body lean math, 33 landmark validation | 4 | **PASS** |
| `CalibrationMathTest` | Neutral angle, max angle, reach scales, sentinel epoch | 17 | **PASS** |
| `RealCalibrationRepositoryTest`| File persistence, epoch assignment, load, delete | 9 | **PASS** |
| `CalibrationFixtureTest` | Recorded fixture regression validations | 5 | **PASS** |
| `FistDetectorTest` | 1s hold, single-fire, 60s open-hand zero false pauses | 9 | **PASS** |
| **Total Automated Tests** | | **75** | **ALL PASS** |

### Build Status
* `.\gradlew.bat :vision:testDebugUnitTest`: **SUCCESS**
* `.\gradlew.bat :calib:testDebugUnitTest`: **SUCCESS**
* `.\gradlew.bat assembleDebug`: **SUCCESS**
