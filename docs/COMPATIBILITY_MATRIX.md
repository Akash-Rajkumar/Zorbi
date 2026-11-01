# ZORBI — Device Compatibility Matrix & QA Report

> **Authority:** `ZORBI_MASTER_PROMPT_v1_1.md.pdf` (Sections 2, 3.3, 10 & Gate T-P4-7)  
> **Author:** Person 4 (Backend, Tools, CI, QA, Docs & Demo)  
> **Generated:** 2026-11-01 05:10:19 UTC  
> **Branch:** `Person4/live-dashboard` (Gate `T-P4-7`)  

---

## 1. Executive Summary & Gate T-P4-7 Audit Verdict

### 1.1 Applicable Game × Ladder Interactions Matrix
- **Total Applicable Matrix Cells:** 14
  - **PASS:** 0 (0.0%)
  - **FAIL:** 0 (0.0%)
  - **BLOCKED:** 3 (21.4%)
  - **NOT TESTED:** 11 (78.6%)

### 1.2 Physical Tracker Benchmark Matrix (Raw Pipeline)
- **Historical Vision Benchmarks on Primary Device (iQOO Z9x 5G):**
  - `HAND_SINGLE` (QUALITY): **FAIL** (Latency 135.8 ms exceeds <= 80 ms budget)
  - `HAND_SINGLE` (BALANCED): **FAIL** (Latency 118.5 ms exceeds <= 80 ms budget)
  - `HAND_SINGLE` (PERFORMANCE): **PASS** (Latency 78.1 ms <= 80 ms budget; 15.0 FPS >= 15 FPS)
  - `HAND_DUAL_WHEEL` (BALANCED): **PASS** (8.0 FPS >= 8.0 FPS floor)
  - `POSE_LITE` (BALANCED): **PASS** (26.0 FPS >= 15 FPS target)
  - `POSE_LITE` (PERFORMANCE): **PASS** (17.0 FPS >= 15 FPS target)
- **Secondary Device (iQOO Z9 Lite 5G):** 4/4 slots **NOT TESTED** (Hardware slot pending).

### 1.3 Gate T-P4-7 Status: **INCOMPLETE / CONDITIONAL (Awaiting Owner Agent Review)**

> [!WARNING]
> **1. Demo Game Selection (Q7) Status:** `UNCONFIRMED`. Candidate games (`Subway Surfers`, `Fruit Ninja`, `Hill Climb Racing`) 
> are provisional. Master Prompt Section 3.3 notes these are candidate examples to try in Phase 0; formal confirmation requires 
> an explicit human / Owner Agent decision.
>
> **2. Game Interaction Evidence:** A raw vision benchmark does **not** prove on-device touch injection, in-game ladder 
> interaction, setup wizard completion, or calibration. Because no live gameplay APK sessions have been logged in repository logs, 
> all 7 candidate game-ladder cells on the Z9x are honestly classified as **NOT TESTED**.
>
> **3. Primary Device OS Verification:** Master Prompt Section 2 Q9 records Person 1's physical device report that the iQOO Z9x 
> runs **Android 14 (API 34)**, confirmed in `docs/DECISIONS.md` ADR-001. The v1.0 initial assumption of Android 16 was superseded.
>
> **4. Secondary Device:** iQOO Z9 Lite 5G physical testing remains **BLOCKED / NOT TESTED** due to unallocated hardware slots.

---

## 2. Target Device Hardware Inventory

| Device | Role | SoC / Processor | GPU | RAM | Verified OS / API | Hardware Status | OS Evidence Source |
| :--- | :--- | :--- | :--- | :--- | :---: | :---: | :--- |
| **iQOO Z9x 5G** | Primary Demo Device | Qualcomm Snapdragon 6 Gen 1 (4x 2.2 GHz Cortex-A78 + 4x 1.8 GHz Cortex-A55) | Adreno 710 | 8 GB LPDDR4X | Android 14 (OriginOS / FuntouchOS 14 baseline) (API 34) | Available (Tested) | Master Prompt Section 2 Q9 & docs/DECISIONS.md ADR-001 (Person 1 on-device report) |
| **iQOO Z9 Lite 5G** | Secondary Test Device | MediaTek Dimensity 6300 (2x 2.4 GHz Cortex-A76 + 6x 2.0 GHz Cortex-A55) | Mali-G57 MC2 | 6 GB LPDDR4X | Android 14 (FuntouchOS 14) [UNVERIFIED / PENDING HARDWARE] (API 34) | Pending Slot | Master Prompt Section 2 Q9 (Pending physical device session confirmation) |

---

## 3. Demo Game × Applicable Ladder Item Compatibility Matrix

> [!NOTE]
> **Evidence Standard:** Cells represent full end-to-end game interaction (tracker + gesture interpreter + touch injector + target game). 
> In accordance with Master Prompt rules R4 and R10, absence of recorded in-game logs requires classification as `NOT TESTED`.

| Target Phone | Demo Game (Status) | Genre | Ladder Item | Tracker & Mode | Measured FPS | P95 Latency | Status | Evidence Source & Audit Notes |
| :--- | :--- | :--- | :--- | :--- | :---: | :---: | :---: | :--- |
| **iQOO Z9x 5G** | **Subway Surfers (Candidate)** <br>*[UNCONFIRMED_CANDIDATE]* | Lane Runner | Ladder 3: Air-swipe in a runner | `HAND_SINGLE` (PERFORMANCE) | Not recorded | Not recorded | _NOT TESTED_ | NOT TESTED on-device: Vision pipeline benchmark passed in PERFORMANCE mode (78.1 ms P95, 15 FPS), but touch injection into live Subway Surfers APK has no recorded test log. <br>*(Source: Candidate selection per Section 3.3 (Q7 unconfirmed); docs/P1_vision.md Section 8 (pipeline only))* |
| **iQOO Z9x 5G** | **Subway Surfers (Candidate)** <br>*[UNCONFIRMED_CANDIDATE]* | Lane Runner | Ladder 5: 60-second calibrator + setup wizard | `SYSTEM` | Not recorded | Not recorded | _NOT TESTED_ | NOT TESTED on-device: FistDetector unit tests pass (FistDetectorTest.kt), but full on-device onboarding flow with live candidate game is unrecorded. <br>*(Source: calib/src/test/java/.../FistDetectorTest.kt (unit test only; no physical run log))* |
| **iQOO Z9x 5G** | **Fruit Ninja (Candidate)** <br>*[UNCONFIRMED_CANDIDATE]* | Fruit Slicer | Ladder 4: Air-slash in a slicing game | `HAND_SINGLE` (PERFORMANCE) | Not recorded | Not recorded | _NOT TESTED_ | NOT TESTED on-device: Vision pipeline benchmark passed in PERFORMANCE mode (78.1 ms P95, 15 FPS), but slash path injection into live Fruit Ninja APK has no recorded test log. <br>*(Source: Candidate selection per Section 3.3 (Q7 unconfirmed); docs/P1_vision.md Section 8 (pipeline only))* |
| **iQOO Z9x 5G** | **Fruit Ninja (Candidate)** <br>*[UNCONFIRMED_CANDIDATE]* | Fruit Slicer | Ladder 5: 60-second calibrator + setup wizard | `SYSTEM` | Not recorded | Not recorded | _NOT TESTED_ | NOT TESTED on-device: Landscape HUD and overlay permission wizard unrecorded on physical device with this game. <br>*(Source: docs/DECISIONS.md ADR-001 (architecture plan only; no physical run log))* |
| **iQOO Z9x 5G** | **Hill Climb Racing (Candidate)** <br>*[UNCONFIRMED_CANDIDATE]* | Touch-Steering Racer | Ladder 1: Air steering wheel in a racing game | `HAND_DUAL_WHEEL` (BALANCED) | Not recorded | Not recorded | _NOT TESTED_ | NOT TESTED on-device: HAND_DUAL_WHEEL pipeline achieved 8.0 FPS (floor met), but gas/brake touch button dispatch into Hill Climb Racing APK has no recorded test log. <br>*(Source: Candidate selection per Section 3.3 (Q7 unconfirmed); docs/P1_vision.md Section 8 (pipeline only))* |
| **iQOO Z9x 5G** | **Hill Climb Racing (Candidate)** <br>*[UNCONFIRMED_CANDIDATE]* | Touch-Steering Racer | Ladder 2: Body-lean steering | `POSE_LITE` (BALANCED) | Not recorded | Not recorded | _NOT TESTED_ | NOT TESTED on-device: POSE_LITE pipeline achieved 26.0 FPS, but torso lean touch mapping into Hill Climb Racing APK has no recorded test log. <br>*(Source: Candidate selection per Section 3.3 (Q7 unconfirmed); docs/P1_vision.md Section 8 (pipeline only))* |
| **iQOO Z9x 5G** | **Hill Climb Racing (Candidate)** <br>*[UNCONFIRMED_CANDIDATE]* | Touch-Steering Racer | Ladder 5: 60-second calibrator + setup wizard | `SYSTEM` | Not recorded | Not recorded | _NOT TESTED_ | NOT TESTED on-device: 60-second steering neutral/range calibration flow with live game has no recorded test log. <br>*(Source: docs/DECISIONS.md ADR-001 (architecture plan only; no physical run log))* |
| **iQOO Z9 Lite 5G** | **Subway Surfers (Candidate)** <br>*[UNCONFIRMED_CANDIDATE]* | Lane Runner | Ladder 3: Air-swipe in a runner | `HAND_SINGLE` (PERFORMANCE) | Not recorded | Not recorded | _NOT TESTED_ | NOT TESTED: Physical device slot not yet allocated. Dimensity 6300 benchmarks pending booked session. <br>*(Source: Awaiting booked physical testing slot)* |
| **iQOO Z9 Lite 5G** | **Subway Surfers (Candidate)** <br>*[UNCONFIRMED_CANDIDATE]* | Lane Runner | Ladder 5: 60-second calibrator + setup wizard | `SYSTEM` | Not recorded | Not recorded | **BLOCKED** | BLOCKED: Cannot verify FuntouchOS 14 restricted settings guidance without hardware access. <br>*(Source: Blocked: Pending Z9 Lite hardware availability)* |
| **iQOO Z9 Lite 5G** | **Fruit Ninja (Candidate)** <br>*[UNCONFIRMED_CANDIDATE]* | Fruit Slicer | Ladder 4: Air-slash in a slicing game | `HAND_SINGLE` (PERFORMANCE) | Not recorded | Not recorded | _NOT TESTED_ | NOT TESTED: Physical device slot not yet allocated. Dimensity 6300 benchmarks pending booked session. <br>*(Source: Awaiting booked physical testing slot)* |
| **iQOO Z9 Lite 5G** | **Fruit Ninja (Candidate)** <br>*[UNCONFIRMED_CANDIDATE]* | Fruit Slicer | Ladder 5: 60-second calibrator + setup wizard | `SYSTEM` | Not recorded | Not recorded | **BLOCKED** | BLOCKED: Pending Z9 Lite hardware availability. <br>*(Source: Blocked: Pending Z9 Lite hardware availability)* |
| **iQOO Z9 Lite 5G** | **Hill Climb Racing (Candidate)** <br>*[UNCONFIRMED_CANDIDATE]* | Touch-Steering Racer | Ladder 1: Air steering wheel in a racing game | `HAND_DUAL_WHEEL` (BALANCED) | Not recorded | Not recorded | _NOT TESTED_ | NOT TESTED: Dimensity 6300 dual-hand throughput (Mali-G57 MC2) unmeasured on physical device. <br>*(Source: Awaiting booked physical testing slot)* |
| **iQOO Z9 Lite 5G** | **Hill Climb Racing (Candidate)** <br>*[UNCONFIRMED_CANDIDATE]* | Touch-Steering Racer | Ladder 2: Body-lean steering | `POSE_LITE` (BALANCED) | Not recorded | Not recorded | _NOT TESTED_ | NOT TESTED: Pending physical device session. <br>*(Source: Awaiting booked physical testing slot)* |
| **iQOO Z9 Lite 5G** | **Hill Climb Racing (Candidate)** <br>*[UNCONFIRMED_CANDIDATE]* | Touch-Steering Racer | Ladder 5: 60-second calibrator + setup wizard | `SYSTEM` | Not recorded | Not recorded | **BLOCKED** | BLOCKED: Pending Z9 Lite hardware availability. <br>*(Source: Blocked: Pending Z9 Lite hardware availability)* |

---

## 4. Physical Device Performance & Tracking Matrix (Empirical Vision Pipeline Benchmarks)

> [!IMPORTANT]
> **Historical Benchmark Provenance:** Physical device `10BE8634H4001GF`, Android 14 (API 34), captured in `docs/P1_vision.md` Section 8. 
> Latency is evaluated strictly against the <= 80 ms P95 budget. `HAND_SINGLE` in `QUALITY` and `BALANCED` modes **FAIL** the budget.

| Target Phone | Tracker | Mode | FPS | P50 (ms) | P95 (ms) | Dropped | Temp | FPS Budget | Latency Budget (<= 80ms) | Status | Historical Evidence Source & Notes |
| :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| **iQOO Z9x 5G** | `HAND_SINGLE` | `QUALITY` | 20.0 FPS | 97.7 ms | 135.8 ms | 0 | 35.0°C | **PASS** (>= 15.0 FPS) | **FAIL** (<= 80.0 ms P95) | **FAIL** | *(Historical)* FAILS latency budget: P95 135.8 ms exceeds <= 80 ms budget. FPS 20.0 satisfies >= 15 FPS. <br>*(Source: docs/P1_vision.md Section 8 (Device Benchmark Results, Serial: 10BE8634H4001GF); Conditions: CPU delegate, front camera 8MP downsampled, Android 14 (API 34), historical run (commit 6ecda1c))* |
| **iQOO Z9x 5G** | `HAND_SINGLE` | `BALANCED` | 20.0 FPS | 88.2 ms | 118.5 ms | 0 | 35.0°C | **PASS** (>= 15.0 FPS) | **FAIL** (<= 80.0 ms P95) | **FAIL** | *(Historical)* FAILS latency budget: P95 118.5 ms exceeds <= 80 ms budget. FPS 20.0 satisfies >= 15 FPS. <br>*(Source: docs/P1_vision.md Section 8 (Device Benchmark Results, Serial: 10BE8634H4001GF); Conditions: CPU delegate, front camera 8MP downsampled, Android 14 (API 34), historical run (commit 6ecda1c))* |
| **iQOO Z9x 5G** | `HAND_SINGLE` | `PERFORMANCE` | 15.0 FPS | 72.4 ms | 78.1 ms | 0 | 34.8°C | **PASS** (>= 15.0 FPS) | **PASS** (<= 80.0 ms P95) | **PASS** | *(Historical)* PASSES: P95 78.1 ms satisfies <= 80 ms budget. FPS 15.0 satisfies >= 15 FPS. Required mode for interactive play. <br>*(Source: docs/P1_vision.md Section 8 (Device Benchmark Results, Serial: 10BE8634H4001GF); Conditions: CPU delegate, front camera 8MP downsampled, Android 14 (API 34), historical run (commit 6ecda1c))* |
| **iQOO Z9x 5G** | `HAND_DUAL_WHEEL` | `BALANCED` | 8.0 FPS | 116.7 ms | 186.9 ms | 0 | 35.4°C | **PASS** (>= 8.0 FPS (minimum usable)) | **PASS** (No standalone <= 80ms rule; 8 FPS floor) | **PASS** | *(Historical)* PASSES: 8.0 FPS meets Section 3.3 / G1 minimum threshold (>= 8.0 FPS). Thermal 35.4°C normal. <br>*(Source: docs/P1_vision.md Section 8 (Device Benchmark Results, Serial: 10BE8634H4001GF); Conditions: CPU delegate, 42 landmarks, Android 14 (API 34), historical run (commit 6ecda1c))* |
| **iQOO Z9x 5G** | `POSE_LITE` | `BALANCED` | 26.0 FPS | 68.6 ms | 90.5 ms | 0 | 35.1°C | **PASS** (>= 15.0 FPS) | **PASS** (Upper-body lean tracking budget) | **PASS** | *(Historical)* PASSES: 26.0 FPS exceeds 15 FPS target. Smooth shoulder lean tracking. <br>*(Source: docs/P1_vision.md Section 8 (Device Benchmark Results, Serial: 10BE8634H4001GF); Conditions: CPU delegate, 33 pose landmarks, Android 14 (API 34), historical run (commit 6ecda1c))* |
| **iQOO Z9x 5G** | `POSE_LITE` | `PERFORMANCE` | 17.0 FPS | 70.5 ms | 90.7 ms | 0 | 35.1°C | **PASS** (>= 15.0 FPS) | **PASS** (Upper-body lean tracking budget) | **PASS** | *(Historical)* PASSES: 17.0 FPS exceeds 15 FPS target while reducing CPU utilization. <br>*(Source: docs/P1_vision.md Section 8 (Device Benchmark Results, Serial: 10BE8634H4001GF); Conditions: CPU delegate, 33 pose landmarks, Android 14 (API 34), historical run (commit 6ecda1c))* |
| **iQOO Z9 Lite 5G** | `HAND_SINGLE` | `BALANCED` | Not recorded | Not recorded | Not recorded | Not recorded | Not recorded | Not recorded (>= 15.0 FPS) | Not recorded (<= 80.0 ms P95) | _NOT TESTED_ | NOT TESTED: Hardware slot not yet allocated. Dimensity 6300 + Mali-G57 MC2 requires real benchmark. <br>*(Source: Not recorded Awaiting booked physical testing slot; Conditions: Not recorded)* |
| **iQOO Z9 Lite 5G** | `HAND_SINGLE` | `PERFORMANCE` | Not recorded | Not recorded | Not recorded | Not recorded | Not recorded | Not recorded (>= 15.0 FPS) | Not recorded (<= 80.0 ms P95) | _NOT TESTED_ | NOT TESTED: Pending physical device session. <br>*(Source: Not recorded Awaiting booked physical testing slot; Conditions: Not recorded)* |
| **iQOO Z9 Lite 5G** | `HAND_DUAL_WHEEL` | `BALANCED` | Not recorded | Not recorded | Not recorded | Not recorded | Not recorded | Not recorded (>= 8.0 FPS (minimum usable)) | Not recorded (No standalone <= 80ms rule) | _NOT TESTED_ | NOT TESTED: Pending physical device session. <br>*(Source: Not recorded Awaiting booked physical testing slot; Conditions: Not recorded)* |
| **iQOO Z9 Lite 5G** | `POSE_LITE` | `BALANCED` | Not recorded | Not recorded | Not recorded | Not recorded | Not recorded | Not recorded (>= 15.0 FPS) | Not recorded (Upper-body lean tracking budget) | _NOT TESTED_ | NOT TESTED: Pending physical device session. <br>*(Source: Not recorded Awaiting booked physical testing slot; Conditions: Not recorded)* |

---

## 5. Full Cross-Product Matrix Completeness (3 Games × 5 Ladder Items × 2 Phones = 30 Cells)

Explicit evaluation of the complete cross-product showing applicability:

| Phone | Demo Game | Ladder 1 (Wheel) | Ladder 2 (Lean) | Ladder 3 (Swipe) | Ladder 4 (Slash) | Ladder 5 (Wizard/Calib) |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: |
| **iQOO Z9x 5G** | **Subway Surfers (Candidate)** | _N/A_ | _N/A_ | _NOT TESTED_ | _N/A_ | _NOT TESTED_ |
| **iQOO Z9x 5G** | **Fruit Ninja (Candidate)** | _N/A_ | _N/A_ | _N/A_ | _NOT TESTED_ | _NOT TESTED_ |
| **iQOO Z9x 5G** | **Hill Climb Racing (Candidate)**| _NOT TESTED_ | _NOT TESTED_ | _N/A_ | _N/A_ | _NOT TESTED_ |
| **iQOO Z9 Lite 5G** | **Subway Surfers (Candidate)** | _N/A_ | _N/A_ | _NOT TESTED_ | _N/A_ | **BLOCKED** |
| **iQOO Z9 Lite 5G** | **Fruit Ninja (Candidate)** | _N/A_ | _N/A_ | _N/A_ | _NOT TESTED_ | **BLOCKED** |
| **iQOO Z9 Lite 5G** | **Hill Climb Racing (Candidate)**| _NOT TESTED_ | _NOT TESTED_ | _N/A_ | _N/A_ | **BLOCKED** |

*Summary of 30-cell cross-product: 14 applicable cells (11 NOT TESTED, 3 BLOCKED, 0 PASS, 0 FAIL); 16 N/A cells (non-applicable combinations).* 

---

## 6. Open Items & Owner Agent Decisions Required

1. **Q7 Game Selection Confirmation:** Owner Agent / human must formally confirm whether candidate games (`Subway Surfers`, `Fruit Ninja`, `Hill Climb Racing`) are accepted as the official demo games.
2. **On-Device Gameplay Integration Runs (E2E-1 / E2E-2):** Execute dedicated on-device gameplay sessions on physical iQOO Z9x 5G to verify touch injection across candidate games under `PERFORMANCE` mode.
3. **Secondary Device Hardware Slot:** Book physical test session for iQOO Z9 Lite 5G to unblock Dimensity 6300 benchmarking and FuntouchOS 14 restricted settings verification.

---

## 7. Audit & Provenance Traceability Record

| Record | Evidence Source | Verification Method | Provenance / Hash |
| :--- | :--- | :--- | :--- |
| Z9x Vision Benchmarks | `docs/P1_vision.md` Section 8 | Physical on-device execution (`10BE8634H4001GF`, Android 14) | Historical Record (Commit `6ecda1c`) |
| OS Version Verification | Master Prompt Section 2 Q9 | Person 1 device audit recorded in `docs/DECISIONS.md` ADR-001 | ADR-001 (Accepted) |
| FistDetector Timing | `calib/src/test/java/.../FistDetectorTest.kt` | 60s continuous open-hand (0 false pauses) + 1s hold | JVM Unit Test (Commit `6ecda1c`) |
| Replay Parity Suite | `fakes/src/test/java/.../ReplayTrackerTest.kt` | Deterministic JSONL fixture playback | JVM Unit Test (Commit `8ac7fd5`) |
| Fixture Integrity | `fixtures/*.jsonl` (5 fixtures) | SHA-256 parity via `tools/test_fixtures.py` | Automated Suite (Commit `130d289`) |
| Live Web Dashboard | `web/dashboard/app.js` | 270 automated resilience checks (0 flakes) | Stage 4E Passed |