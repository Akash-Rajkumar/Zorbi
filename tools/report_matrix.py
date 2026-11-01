#!/usr/bin/env python3
"""
ZORBI — Compatibility Matrix & Device QA Reporter (P4-7 / Gate T-P4-7)
Authority: ZORBI Master Prompt (v1.1), Sections 2, 3.3, 10 & Gate T-P4-7
Author: Person 4 (Backend, Tools, CI, QA, Docs & Demo)

Generates empirical device compatibility matrices, tracking performance metrics,
background-kill evaluations, thermal observations, and gate compliance reports.

Strict Evidence & Auditing Rules:
1. Q7 Game Selection: Candidate games (Subway Surfers, Fruit Ninja, Hill Climb Racing)
   are labeled UNCONFIRMED / PROVISIONAL pending human / Owner Agent decision.
2. Z9x OS Version: Verified as Android 14 (API 34) per Master Prompt Section 2 Q9
   and docs/DECISIONS.md ADR-001 (Person 1 device report).
3. Evidence Rigor: A vision tracker benchmark does NOT prove game touch injection,
   ladder interactions, setup wizard, or calibration. Unverified interactions are
   downgraded to NOT TESTED or BLOCKED.
4. Latency Budgets: HAND_SINGLE P95 budget is strictly <= 80 ms.
   QUALITY (135.8 ms) and BALANCED (118.5 ms) are FAIL; PERFORMANCE (78.1 ms) is PASS.
5. Zero synthetic or manufactured results.
"""

import argparse
import json
import os
import sys
from dataclasses import asdict, dataclass
from datetime import datetime
from pathlib import Path
from typing import Any, Dict, List, Optional

REPO_ROOT = Path(__file__).resolve().parent.parent


# ==============================================================================
# DATA MODELS
# ==============================================================================

@dataclass
class DeviceSpec:
    name: str
    model_code: str
    soc: str
    gpu: str
    ram: str
    os_verified: str
    api_level: int
    serial: Optional[str]
    role: str  # "Primary Demo Device" or "Secondary Test Device"
    hardware_status: str  # "AVAILABLE_TESTED", "PENDING_HARDWARE"
    os_verification_source: str


@dataclass
class BenchmarkRecord:
    device_name: str
    tracker: str          # HAND_SINGLE, HAND_DUAL_WHEEL, POSE_LITE
    perf_mode: str        # QUALITY, BALANCED, PERFORMANCE
    fps: Optional[float]
    p50_latency_ms: Optional[float]
    p95_latency_ms: Optional[float]
    dropped_frames: Optional[int]
    temp_c: Optional[float]
    fps_criterion: str
    fps_pass: Optional[bool]
    latency_criterion: str
    latency_pass: Optional[bool]
    overall_status: str   # PASS, FAIL, NOT TESTED, BLOCKED
    source_file: str
    source_record: str
    test_conditions: str
    is_historical: bool
    notes: str


@dataclass
class MatrixCell:
    device_name: str
    game_id: str
    game_name: str
    game_package: str
    genre: str
    ladder_number: int
    ladder_title: str
    applicable_tracker: str
    perf_mode: str
    is_applicable: bool
    game_selection_status: str  # "UNCONFIRMED_CANDIDATE"
    status: str                 # PASS, FAIL, BLOCKED, NOT TESTED, NOT APPLICABLE
    latency_p95_ms: Optional[float]
    fps: Optional[float]
    notes: str
    source_ref: str
    evidence_type: str          # "Historical Vision Benchmark Only", "JVM Unit Test", "Pending Hardware", "Not Applicable"


# ==============================================================================
# CANONICAL DEVICE SPECIFICATIONS (Section 2 Q9 & Section 10)
# ==============================================================================
DEVICES: Dict[str, DeviceSpec] = {
    "iQOO Z9x 5G": DeviceSpec(
        name="iQOO Z9x 5G",
        model_code="I2218 / vivo 2333",
        soc="Qualcomm Snapdragon 6 Gen 1 (4x 2.2 GHz Cortex-A78 + 4x 1.8 GHz Cortex-A55)",
        gpu="Adreno 710",
        ram="8 GB LPDDR4X",
        os_verified="Android 14 (OriginOS / FuntouchOS 14 baseline)",
        api_level=34,
        serial="10BE8634H4001GF",
        role="Primary Demo Device",
        hardware_status="AVAILABLE_TESTED",
        os_verification_source="Master Prompt Section 2 Q9 & docs/DECISIONS.md ADR-001 (Person 1 on-device report)",
    ),
    "iQOO Z9 Lite 5G": DeviceSpec(
        name="iQOO Z9 Lite 5G",
        model_code="I2306",
        soc="MediaTek Dimensity 6300 (2x 2.4 GHz Cortex-A76 + 6x 2.0 GHz Cortex-A55)",
        gpu="Mali-G57 MC2",
        ram="6 GB LPDDR4X",
        os_verified="Android 14 (FuntouchOS 14) [UNVERIFIED / PENDING HARDWARE]",
        api_level=34,
        serial=None,
        role="Secondary Test Device",
        hardware_status="PENDING_HARDWARE",
        os_verification_source="Master Prompt Section 2 Q9 (Pending physical device session confirmation)",
    ),
}

# ==============================================================================
# DEMO GAMES & LADDER DEFINITIONS (Section 3.3, Section 2 Q7 & seed.sql)
# ==============================================================================
DEMO_GAMES = [
    {
        "id": "subway_surfers",
        "name": "Subway Surfers",
        "package": "com.kiloo.subwaysurf",
        "genre": "Lane Runner",
        "applicable_ladders": [3, 5],
        "selection_status": "UNCONFIRMED_CANDIDATE",
        "notes": "Candidate lane-runner per Section 3.3; formal Q7 answer pending Owner Agent decision.",
    },
    {
        "id": "fruit_ninja",
        "name": "Fruit Ninja",
        "package": "com.halfbrick.fruitninjafree",
        "genre": "Fruit Slicer",
        "applicable_ladders": [4, 5],
        "selection_status": "UNCONFIRMED_CANDIDATE",
        "notes": "Candidate fruit-slicer per Section 3.3; formal Q7 answer pending Owner Agent decision.",
    },
    {
        "id": "hill_climb",
        "name": "Hill Climb Racing",
        "package": "com.fingersoft.hillclimb",
        "genre": "Touch-Steering Racer",
        "applicable_ladders": [1, 2, 5],
        "selection_status": "UNCONFIRMED_CANDIDATE",
        "notes": "Candidate touch-steering racer per Section 3.3; formal Q7 answer pending Owner Agent decision.",
    },
]

LADDER_ITEMS = [
    {
        "number": 1,
        "title": "Air steering wheel in a racing game",
        "description": "Two hands; angle of wrist line = steering (HAND_DUAL_WHEEL)",
        "tracker": "HAND_DUAL_WHEEL",
    },
    {
        "number": 2,
        "title": "Body-lean steering",
        "description": "Pose-lite; angle of shoulder line (POSE_LITE)",
        "tracker": "POSE_LITE",
    },
    {
        "number": 3,
        "title": "Air-swipe in a runner",
        "description": "One hand; swipe gestures mapped to lanes (HAND_SINGLE)",
        "tracker": "HAND_SINGLE",
    },
    {
        "number": 4,
        "title": "Air-slash in a slicing game",
        "description": "One hand; slash paths across fruit (HAND_SINGLE)",
        "tracker": "HAND_SINGLE",
    },
    {
        "number": 5,
        "title": "60-second calibrator + setup wizard",
        "description": "Guided onboarding, permission verification & calibration flow",
        "tracker": "SYSTEM",
    },
]


# ==============================================================================
# PHYSICAL TRACKER BENCHMARKS (docs/P1_vision.md Section 8)
# Corrected per Section 9 Latency Rules: HAND_SINGLE P95 budget <= 80 ms.
# ==============================================================================
BENCHMARKS: List[BenchmarkRecord] = [
    # --- iQOO Z9x 5G (Historical Evidence from physical device 10BE8634H4001GF, Android 14) ---
    BenchmarkRecord(
        device_name="iQOO Z9x 5G",
        tracker="HAND_SINGLE",
        perf_mode="QUALITY",
        fps=20.0,
        p50_latency_ms=97.7,
        p95_latency_ms=135.8,
        dropped_frames=0,
        temp_c=35.0,
        fps_criterion=">= 15.0 FPS",
        fps_pass=True,
        latency_criterion="<= 80.0 ms P95",
        latency_pass=False,  # 135.8 ms > 80.0 ms
        overall_status="FAIL",
        source_file="docs/P1_vision.md",
        source_record="Section 8 (Device Benchmark Results, Serial: 10BE8634H4001GF)",
        test_conditions="CPU delegate, front camera 8MP downsampled, Android 14 (API 34), historical run (commit 6ecda1c)",
        is_historical=True,
        notes="FAILS latency budget: P95 135.8 ms exceeds <= 80 ms budget. FPS 20.0 satisfies >= 15 FPS.",
    ),
    BenchmarkRecord(
        device_name="iQOO Z9x 5G",
        tracker="HAND_SINGLE",
        perf_mode="BALANCED",
        fps=20.0,
        p50_latency_ms=88.2,
        p95_latency_ms=118.5,
        dropped_frames=0,
        temp_c=35.0,
        fps_criterion=">= 15.0 FPS",
        fps_pass=True,
        latency_criterion="<= 80.0 ms P95",
        latency_pass=False,  # 118.5 ms > 80.0 ms
        overall_status="FAIL",
        source_file="docs/P1_vision.md",
        source_record="Section 8 (Device Benchmark Results, Serial: 10BE8634H4001GF)",
        test_conditions="CPU delegate, front camera 8MP downsampled, Android 14 (API 34), historical run (commit 6ecda1c)",
        is_historical=True,
        notes="FAILS latency budget: P95 118.5 ms exceeds <= 80 ms budget. FPS 20.0 satisfies >= 15 FPS.",
    ),
    BenchmarkRecord(
        device_name="iQOO Z9x 5G",
        tracker="HAND_SINGLE",
        perf_mode="PERFORMANCE",
        fps=15.0,
        p50_latency_ms=72.4,
        p95_latency_ms=78.1,
        dropped_frames=0,
        temp_c=34.8,
        fps_criterion=">= 15.0 FPS",
        fps_pass=True,
        latency_criterion="<= 80.0 ms P95",
        latency_pass=True,  # 78.1 ms <= 80.0 ms
        overall_status="PASS",
        source_file="docs/P1_vision.md",
        source_record="Section 8 (Device Benchmark Results, Serial: 10BE8634H4001GF)",
        test_conditions="CPU delegate, front camera 8MP downsampled, Android 14 (API 34), historical run (commit 6ecda1c)",
        is_historical=True,
        notes="PASSES: P95 78.1 ms satisfies <= 80 ms budget. FPS 15.0 satisfies >= 15 FPS. Required mode for interactive play.",
    ),
    BenchmarkRecord(
        device_name="iQOO Z9x 5G",
        tracker="HAND_DUAL_WHEEL",
        perf_mode="BALANCED",
        fps=8.0,
        p50_latency_ms=116.7,
        p95_latency_ms=186.9,
        dropped_frames=0,
        temp_c=35.4,
        fps_criterion=">= 8.0 FPS (minimum usable)",
        fps_pass=True,
        latency_criterion="No standalone <= 80ms rule; 8 FPS floor",
        latency_pass=True,
        overall_status="PASS",
        source_file="docs/P1_vision.md",
        source_record="Section 8 (Device Benchmark Results, Serial: 10BE8634H4001GF)",
        test_conditions="CPU delegate, 42 landmarks, Android 14 (API 34), historical run (commit 6ecda1c)",
        is_historical=True,
        notes="PASSES: 8.0 FPS meets Section 3.3 / G1 minimum threshold (>= 8.0 FPS). Thermal 35.4°C normal.",
    ),
    BenchmarkRecord(
        device_name="iQOO Z9x 5G",
        tracker="POSE_LITE",
        perf_mode="BALANCED",
        fps=26.0,
        p50_latency_ms=68.6,
        p95_latency_ms=90.5,
        dropped_frames=0,
        temp_c=35.1,
        fps_criterion=">= 15.0 FPS",
        fps_pass=True,
        latency_criterion="Upper-body lean tracking budget",
        latency_pass=True,
        overall_status="PASS",
        source_file="docs/P1_vision.md",
        source_record="Section 8 (Device Benchmark Results, Serial: 10BE8634H4001GF)",
        test_conditions="CPU delegate, 33 pose landmarks, Android 14 (API 34), historical run (commit 6ecda1c)",
        is_historical=True,
        notes="PASSES: 26.0 FPS exceeds 15 FPS target. Smooth shoulder lean tracking.",
    ),
    BenchmarkRecord(
        device_name="iQOO Z9x 5G",
        tracker="POSE_LITE",
        perf_mode="PERFORMANCE",
        fps=17.0,
        p50_latency_ms=70.5,
        p95_latency_ms=90.7,
        dropped_frames=0,
        temp_c=35.1,
        fps_criterion=">= 15.0 FPS",
        fps_pass=True,
        latency_criterion="Upper-body lean tracking budget",
        latency_pass=True,
        overall_status="PASS",
        source_file="docs/P1_vision.md",
        source_record="Section 8 (Device Benchmark Results, Serial: 10BE8634H4001GF)",
        test_conditions="CPU delegate, 33 pose landmarks, Android 14 (API 34), historical run (commit 6ecda1c)",
        is_historical=True,
        notes="PASSES: 17.0 FPS exceeds 15 FPS target while reducing CPU utilization.",
    ),
    # --- iQOO Z9 Lite 5G (Hardware Slot Pending — Zero Synthetic Values) ---
    BenchmarkRecord(
        device_name="iQOO Z9 Lite 5G",
        tracker="HAND_SINGLE",
        perf_mode="BALANCED",
        fps=None,
        p50_latency_ms=None,
        p95_latency_ms=None,
        dropped_frames=None,
        temp_c=None,
        fps_criterion=">= 15.0 FPS",
        fps_pass=None,
        latency_criterion="<= 80.0 ms P95",
        latency_pass=None,
        overall_status="NOT TESTED",
        source_file="Not recorded",
        source_record="Awaiting booked physical testing slot",
        test_conditions="Not recorded",
        is_historical=False,
        notes="NOT TESTED: Hardware slot not yet allocated. Dimensity 6300 + Mali-G57 MC2 requires real benchmark.",
    ),
    BenchmarkRecord(
        device_name="iQOO Z9 Lite 5G",
        tracker="HAND_SINGLE",
        perf_mode="PERFORMANCE",
        fps=None,
        p50_latency_ms=None,
        p95_latency_ms=None,
        dropped_frames=None,
        temp_c=None,
        fps_criterion=">= 15.0 FPS",
        fps_pass=None,
        latency_criterion="<= 80.0 ms P95",
        latency_pass=None,
        overall_status="NOT TESTED",
        source_file="Not recorded",
        source_record="Awaiting booked physical testing slot",
        test_conditions="Not recorded",
        is_historical=False,
        notes="NOT TESTED: Pending physical device session.",
    ),
    BenchmarkRecord(
        device_name="iQOO Z9 Lite 5G",
        tracker="HAND_DUAL_WHEEL",
        perf_mode="BALANCED",
        fps=None,
        p50_latency_ms=None,
        p95_latency_ms=None,
        dropped_frames=None,
        temp_c=None,
        fps_criterion=">= 8.0 FPS (minimum usable)",
        fps_pass=None,
        latency_criterion="No standalone <= 80ms rule",
        latency_pass=None,
        overall_status="NOT TESTED",
        source_file="Not recorded",
        source_record="Awaiting booked physical testing slot",
        test_conditions="Not recorded",
        is_historical=False,
        notes="NOT TESTED: Pending physical device session.",
    ),
    BenchmarkRecord(
        device_name="iQOO Z9 Lite 5G",
        tracker="POSE_LITE",
        perf_mode="BALANCED",
        fps=None,
        p50_latency_ms=None,
        p95_latency_ms=None,
        dropped_frames=None,
        temp_c=None,
        fps_criterion=">= 15.0 FPS",
        fps_pass=None,
        latency_criterion="Upper-body lean tracking budget",
        latency_pass=None,
        overall_status="NOT TESTED",
        source_file="Not recorded",
        source_record="Awaiting booked physical testing slot",
        test_conditions="Not recorded",
        is_historical=False,
        notes="NOT TESTED: Pending physical device session.",
    ),
]


# ==============================================================================
# CANONICAL DEMO GAME × APPLICABLE LADDER ITEM × TARGET PHONE MATRIX
# Rigorous Evidence Policy:
# Tracker benchmarks do NOT prove game touch acceptance, ladder interactions,
# setup wizard completion, or calibration on live games.
# Because Q7 game selection is UNCONFIRMED and full on-device game APK sessions
# have not been recorded in logs, game-level interaction cells are NOT TESTED / BLOCKED.
# ==============================================================================
def build_game_ladder_matrix() -> List[MatrixCell]:
    cells: List[MatrixCell] = [
        # ======================================================================
        # Primary Demo Device: iQOO Z9x 5G (Android 14, API 34)
        # ======================================================================
        # 1. Subway Surfers (Candidate Game — UNCONFIRMED per Q7)
        MatrixCell(
            device_name="iQOO Z9x 5G",
            game_id="subway_surfers",
            game_name="Subway Surfers (Candidate)",
            game_package="com.kiloo.subwaysurf",
            genre="Lane Runner",
            ladder_number=3,
            ladder_title="Air-swipe in a runner",
            applicable_tracker="HAND_SINGLE",
            perf_mode="PERFORMANCE",
            is_applicable=True,
            game_selection_status="UNCONFIRMED_CANDIDATE",
            status="NOT TESTED",
            latency_p95_ms=None,
            fps=None,
            notes="NOT TESTED on-device: Vision pipeline benchmark passed in PERFORMANCE mode (78.1 ms P95, 15 FPS), but touch injection into live Subway Surfers APK has no recorded test log.",
            source_ref="Candidate selection per Section 3.3 (Q7 unconfirmed); docs/P1_vision.md Section 8 (pipeline only)",
            evidence_type="Historical Vision Benchmark Only",
        ),
        MatrixCell(
            device_name="iQOO Z9x 5G",
            game_id="subway_surfers",
            game_name="Subway Surfers (Candidate)",
            game_package="com.kiloo.subwaysurf",
            genre="Lane Runner",
            ladder_number=5,
            ladder_title="60-second calibrator + setup wizard",
            applicable_tracker="SYSTEM",
            perf_mode="DEFAULT",
            is_applicable=True,
            game_selection_status="UNCONFIRMED_CANDIDATE",
            status="NOT TESTED",
            latency_p95_ms=None,
            fps=None,
            notes="NOT TESTED on-device: FistDetector unit tests pass (FistDetectorTest.kt), but full on-device onboarding flow with live candidate game is unrecorded.",
            source_ref="calib/src/test/java/.../FistDetectorTest.kt (unit test only; no physical run log)",
            evidence_type="JVM Unit Test",
        ),

        # 2. Fruit Ninja (Candidate Game — UNCONFIRMED per Q7)
        MatrixCell(
            device_name="iQOO Z9x 5G",
            game_id="fruit_ninja",
            game_name="Fruit Ninja (Candidate)",
            game_package="com.halfbrick.fruitninjafree",
            genre="Fruit Slicer",
            ladder_number=4,
            ladder_title="Air-slash in a slicing game",
            applicable_tracker="HAND_SINGLE",
            perf_mode="PERFORMANCE",
            is_applicable=True,
            game_selection_status="UNCONFIRMED_CANDIDATE",
            status="NOT TESTED",
            latency_p95_ms=None,
            fps=None,
            notes="NOT TESTED on-device: Vision pipeline benchmark passed in PERFORMANCE mode (78.1 ms P95, 15 FPS), but slash path injection into live Fruit Ninja APK has no recorded test log.",
            source_ref="Candidate selection per Section 3.3 (Q7 unconfirmed); docs/P1_vision.md Section 8 (pipeline only)",
            evidence_type="Historical Vision Benchmark Only",
        ),
        MatrixCell(
            device_name="iQOO Z9x 5G",
            game_id="fruit_ninja",
            game_name="Fruit Ninja (Candidate)",
            game_package="com.halfbrick.fruitninjafree",
            genre="Fruit Slicer",
            ladder_number=5,
            ladder_title="60-second calibrator + setup wizard",
            applicable_tracker="SYSTEM",
            perf_mode="DEFAULT",
            is_applicable=True,
            game_selection_status="UNCONFIRMED_CANDIDATE",
            status="NOT TESTED",
            latency_p95_ms=None,
            fps=None,
            notes="NOT TESTED on-device: Landscape HUD and overlay permission wizard unrecorded on physical device with this game.",
            source_ref="docs/DECISIONS.md ADR-001 (architecture plan only; no physical run log)",
            evidence_type="JVM Unit Test",
        ),

        # 3. Hill Climb Racing (Candidate Game — UNCONFIRMED per Q7)
        MatrixCell(
            device_name="iQOO Z9x 5G",
            game_id="hill_climb",
            game_name="Hill Climb Racing (Candidate)",
            game_package="com.fingersoft.hillclimb",
            genre="Touch-Steering Racer",
            ladder_number=1,
            ladder_title="Air steering wheel in a racing game",
            applicable_tracker="HAND_DUAL_WHEEL",
            perf_mode="BALANCED",
            is_applicable=True,
            game_selection_status="UNCONFIRMED_CANDIDATE",
            status="NOT TESTED",
            latency_p95_ms=None,
            fps=None,
            notes="NOT TESTED on-device: HAND_DUAL_WHEEL pipeline achieved 8.0 FPS (floor met), but gas/brake touch button dispatch into Hill Climb Racing APK has no recorded test log.",
            source_ref="Candidate selection per Section 3.3 (Q7 unconfirmed); docs/P1_vision.md Section 8 (pipeline only)",
            evidence_type="Historical Vision Benchmark Only",
        ),
        MatrixCell(
            device_name="iQOO Z9x 5G",
            game_id="hill_climb",
            game_name="Hill Climb Racing (Candidate)",
            game_package="com.fingersoft.hillclimb",
            genre="Touch-Steering Racer",
            ladder_number=2,
            ladder_title="Body-lean steering",
            applicable_tracker="POSE_LITE",
            perf_mode="BALANCED",
            is_applicable=True,
            game_selection_status="UNCONFIRMED_CANDIDATE",
            status="NOT TESTED",
            latency_p95_ms=None,
            fps=None,
            notes="NOT TESTED on-device: POSE_LITE pipeline achieved 26.0 FPS, but torso lean touch mapping into Hill Climb Racing APK has no recorded test log.",
            source_ref="Candidate selection per Section 3.3 (Q7 unconfirmed); docs/P1_vision.md Section 8 (pipeline only)",
            evidence_type="Historical Vision Benchmark Only",
        ),
        MatrixCell(
            device_name="iQOO Z9x 5G",
            game_id="hill_climb",
            game_name="Hill Climb Racing (Candidate)",
            game_package="com.fingersoft.hillclimb",
            genre="Touch-Steering Racer",
            ladder_number=5,
            ladder_title="60-second calibrator + setup wizard",
            applicable_tracker="SYSTEM",
            perf_mode="DEFAULT",
            is_applicable=True,
            game_selection_status="UNCONFIRMED_CANDIDATE",
            status="NOT TESTED",
            latency_p95_ms=None,
            fps=None,
            notes="NOT TESTED on-device: 60-second steering neutral/range calibration flow with live game has no recorded test log.",
            source_ref="docs/DECISIONS.md ADR-001 (architecture plan only; no physical run log)",
            evidence_type="JVM Unit Test",
        ),

        # ======================================================================
        # Secondary Test Device: iQOO Z9 Lite 5G (Hardware Slot Pending)
        # ======================================================================
        # 1. Subway Surfers
        MatrixCell(
            device_name="iQOO Z9 Lite 5G",
            game_id="subway_surfers",
            game_name="Subway Surfers (Candidate)",
            game_package="com.kiloo.subwaysurf",
            genre="Lane Runner",
            ladder_number=3,
            ladder_title="Air-swipe in a runner",
            applicable_tracker="HAND_SINGLE",
            perf_mode="PERFORMANCE",
            is_applicable=True,
            game_selection_status="UNCONFIRMED_CANDIDATE",
            status="NOT TESTED",
            latency_p95_ms=None,
            fps=None,
            notes="NOT TESTED: Physical device slot not yet allocated. Dimensity 6300 benchmarks pending booked session.",
            source_ref="Awaiting booked physical testing slot",
            evidence_type="Pending Hardware",
        ),
        MatrixCell(
            device_name="iQOO Z9 Lite 5G",
            game_id="subway_surfers",
            game_name="Subway Surfers (Candidate)",
            game_package="com.kiloo.subwaysurf",
            genre="Lane Runner",
            ladder_number=5,
            ladder_title="60-second calibrator + setup wizard",
            applicable_tracker="SYSTEM",
            perf_mode="DEFAULT",
            is_applicable=True,
            game_selection_status="UNCONFIRMED_CANDIDATE",
            status="BLOCKED",
            latency_p95_ms=None,
            fps=None,
            notes="BLOCKED: Cannot verify FuntouchOS 14 restricted settings guidance without hardware access.",
            source_ref="Blocked: Pending Z9 Lite hardware availability",
            evidence_type="Pending Hardware",
        ),

        # 2. Fruit Ninja
        MatrixCell(
            device_name="iQOO Z9 Lite 5G",
            game_id="fruit_ninja",
            game_name="Fruit Ninja (Candidate)",
            game_package="com.halfbrick.fruitninjafree",
            genre="Fruit Slicer",
            ladder_number=4,
            ladder_title="Air-slash in a slicing game",
            applicable_tracker="HAND_SINGLE",
            perf_mode="PERFORMANCE",
            is_applicable=True,
            game_selection_status="UNCONFIRMED_CANDIDATE",
            status="NOT TESTED",
            latency_p95_ms=None,
            fps=None,
            notes="NOT TESTED: Physical device slot not yet allocated. Dimensity 6300 benchmarks pending booked session.",
            source_ref="Awaiting booked physical testing slot",
            evidence_type="Pending Hardware",
        ),
        MatrixCell(
            device_name="iQOO Z9 Lite 5G",
            game_id="fruit_ninja",
            game_name="Fruit Ninja (Candidate)",
            game_package="com.halfbrick.fruitninjafree",
            genre="Fruit Slicer",
            ladder_number=5,
            ladder_title="60-second calibrator + setup wizard",
            applicable_tracker="SYSTEM",
            perf_mode="DEFAULT",
            is_applicable=True,
            game_selection_status="UNCONFIRMED_CANDIDATE",
            status="BLOCKED",
            latency_p95_ms=None,
            fps=None,
            notes="BLOCKED: Pending Z9 Lite hardware availability.",
            source_ref="Blocked: Pending Z9 Lite hardware availability",
            evidence_type="Pending Hardware",
        ),

        # 3. Hill Climb Racing
        MatrixCell(
            device_name="iQOO Z9 Lite 5G",
            game_id="hill_climb",
            game_name="Hill Climb Racing (Candidate)",
            game_package="com.fingersoft.hillclimb",
            genre="Touch-Steering Racer",
            ladder_number=1,
            ladder_title="Air steering wheel in a racing game",
            applicable_tracker="HAND_DUAL_WHEEL",
            perf_mode="BALANCED",
            is_applicable=True,
            game_selection_status="UNCONFIRMED_CANDIDATE",
            status="NOT TESTED",
            latency_p95_ms=None,
            fps=None,
            notes="NOT TESTED: Dimensity 6300 dual-hand throughput (Mali-G57 MC2) unmeasured on physical device.",
            source_ref="Awaiting booked physical testing slot",
            evidence_type="Pending Hardware",
        ),
        MatrixCell(
            device_name="iQOO Z9 Lite 5G",
            game_id="hill_climb",
            game_name="Hill Climb Racing (Candidate)",
            game_package="com.fingersoft.hillclimb",
            genre="Touch-Steering Racer",
            ladder_number=2,
            ladder_title="Body-lean steering",
            applicable_tracker="POSE_LITE",
            perf_mode="BALANCED",
            is_applicable=True,
            game_selection_status="UNCONFIRMED_CANDIDATE",
            status="NOT TESTED",
            latency_p95_ms=None,
            fps=None,
            notes="NOT TESTED: Pending physical device session.",
            source_ref="Awaiting booked physical testing slot",
            evidence_type="Pending Hardware",
        ),
        MatrixCell(
            device_name="iQOO Z9 Lite 5G",
            game_id="hill_climb",
            game_name="Hill Climb Racing (Candidate)",
            game_package="com.fingersoft.hillclimb",
            genre="Touch-Steering Racer",
            ladder_number=5,
            ladder_title="60-second calibrator + setup wizard",
            applicable_tracker="SYSTEM",
            perf_mode="DEFAULT",
            is_applicable=True,
            game_selection_status="UNCONFIRMED_CANDIDATE",
            status="BLOCKED",
            latency_p95_ms=None,
            fps=None,
            notes="BLOCKED: Pending Z9 Lite hardware availability.",
            source_ref="Blocked: Pending Z9 Lite hardware availability",
            evidence_type="Pending Hardware",
        ),
    ]
    return cells


# ==============================================================================
# REPORT FORMATTERS
# ==============================================================================

def format_float(val: Optional[float], suffix: str = "") -> str:
    if val is None:
        return "Not recorded"
    return f"{val:.1f}{suffix}"


def format_status_badge(status: str) -> str:
    badges = {
        "PASS": "**PASS**",
        "FAIL": "**FAIL**",
        "BLOCKED": "**BLOCKED**",
        "NOT TESTED": "_NOT TESTED_",
        "NOT APPLICABLE": "_N/A_",
    }
    return badges.get(status, status)


def generate_markdown_report(
    matrix_cells: List[MatrixCell],
    benchmarks: List[BenchmarkRecord],
    devices: Dict[str, DeviceSpec],
) -> str:
    total_applicable = len(matrix_cells)
    passes = sum(1 for c in matrix_cells if c.status == "PASS")
    fails = sum(1 for c in matrix_cells if c.status == "FAIL")
    blocked = sum(1 for c in matrix_cells if c.status == "BLOCKED")
    not_tested = sum(1 for c in matrix_cells if c.status == "NOT TESTED")

    lines: List[str] = [
        "# ZORBI — Device Compatibility Matrix & QA Report",
        "",
        "> **Authority:** `ZORBI_MASTER_PROMPT_v1_1.md.pdf` (Sections 2, 3.3, 10 & Gate T-P4-7)  ",
        "> **Author:** Person 4 (Backend, Tools, CI, QA, Docs & Demo)  ",
        f"> **Generated:** {datetime.now().strftime('%Y-%m-%d %H:%M:%S UTC')}  ",
        f"> **Branch:** `Person4/live-dashboard` (Gate `T-P4-7`)  ",
        "",
        "---",
        "",
        "## 1. Executive Summary & Gate T-P4-7 Audit Verdict",
        "",
        "### 1.1 Applicable Game × Ladder Interactions Matrix",
        f"- **Total Applicable Matrix Cells:** {total_applicable}",
        f"  - **PASS:** {passes} ({passes / total_applicable * 100:.1f}%)",
        f"  - **FAIL:** {fails} ({fails / total_applicable * 100:.1f}%)",
        f"  - **BLOCKED:** {blocked} ({blocked / total_applicable * 100:.1f}%)",
        f"  - **NOT TESTED:** {not_tested} ({not_tested / total_applicable * 100:.1f}%)",
        "",
        "### 1.2 Physical Tracker Benchmark Matrix (Raw Pipeline)",
        "- **Historical Vision Benchmarks on Primary Device (iQOO Z9x 5G):**",
        "  - `HAND_SINGLE` (QUALITY): **FAIL** (Latency 135.8 ms exceeds <= 80 ms budget)",
        "  - `HAND_SINGLE` (BALANCED): **FAIL** (Latency 118.5 ms exceeds <= 80 ms budget)",
        "  - `HAND_SINGLE` (PERFORMANCE): **PASS** (Latency 78.1 ms <= 80 ms budget; 15.0 FPS >= 15 FPS)",
        "  - `HAND_DUAL_WHEEL` (BALANCED): **PASS** (8.0 FPS >= 8.0 FPS floor)",
        "  - `POSE_LITE` (BALANCED): **PASS** (26.0 FPS >= 15 FPS target)",
        "  - `POSE_LITE` (PERFORMANCE): **PASS** (17.0 FPS >= 15 FPS target)",
        "- **Secondary Device (iQOO Z9 Lite 5G):** 4/4 slots **NOT TESTED** (Hardware slot pending).",
        "",
        "### 1.3 Gate T-P4-7 Status: **INCOMPLETE / CONDITIONAL (Awaiting Owner Agent Review)**",
        "",
        "> [!WARNING]",
        "> **1. Demo Game Selection (Q7) Status:** `UNCONFIRMED`. Candidate games (`Subway Surfers`, `Fruit Ninja`, `Hill Climb Racing`) ",
        "> are provisional. Master Prompt Section 3.3 notes these are candidate examples to try in Phase 0; formal confirmation requires ",
        "> an explicit human / Owner Agent decision.",
        ">",
        "> **2. Game Interaction Evidence:** A raw vision benchmark does **not** prove on-device touch injection, in-game ladder ",
        "> interaction, setup wizard completion, or calibration. Because no live gameplay APK sessions have been logged in repository logs, ",
        "> all 7 candidate game-ladder cells on the Z9x are honestly classified as **NOT TESTED**.",
        ">",
        "> **3. Primary Device OS Verification:** Master Prompt Section 2 Q9 records Person 1's physical device report that the iQOO Z9x ",
        "> runs **Android 14 (API 34)**, confirmed in `docs/DECISIONS.md` ADR-001. The v1.0 initial assumption of Android 16 was superseded.",
        ">",
        "> **4. Secondary Device:** iQOO Z9 Lite 5G physical testing remains **BLOCKED / NOT TESTED** due to unallocated hardware slots.",
        "",
        "---",
        "",
        "## 2. Target Device Hardware Inventory",
        "",
        "| Device | Role | SoC / Processor | GPU | RAM | Verified OS / API | Hardware Status | OS Evidence Source |",
        "| :--- | :--- | :--- | :--- | :--- | :---: | :---: | :--- |",
    ]

    for dev in devices.values():
        stat_badge = "Available (Tested)" if dev.hardware_status == "AVAILABLE_TESTED" else "Pending Slot"
        lines.append(
            f"| **{dev.name}** | {dev.role} | {dev.soc} | {dev.gpu} | {dev.ram} | {dev.os_verified} (API {dev.api_level}) | {stat_badge} | {dev.os_verification_source} |"
        )

    lines.extend([
        "",
        "---",
        "",
        "## 3. Demo Game × Applicable Ladder Item Compatibility Matrix",
        "",
        "> [!NOTE]",
        "> **Evidence Standard:** Cells represent full end-to-end game interaction (tracker + gesture interpreter + touch injector + target game). ",
        "> In accordance with Master Prompt rules R4 and R10, absence of recorded in-game logs requires classification as `NOT TESTED`.",
        "",
        "| Target Phone | Demo Game (Status) | Genre | Ladder Item | Tracker & Mode | Measured FPS | P95 Latency | Status | Evidence Source & Audit Notes |",
        "| :--- | :--- | :--- | :--- | :--- | :---: | :---: | :---: | :--- |",
    ])

    for c in matrix_cells:
        badge = format_status_badge(c.status)
        fps_s = format_float(c.fps, " FPS")
        p95_s = format_float(c.latency_p95_ms, " ms")
        tracker_mode = f"`{c.applicable_tracker}` ({c.perf_mode})" if c.applicable_tracker != "SYSTEM" else "`SYSTEM`"
        lines.append(
            f"| **{c.device_name}** | **{c.game_name}** <br>*[{c.game_selection_status}]* | {c.genre} | Ladder {c.ladder_number}: {c.ladder_title} | {tracker_mode} | {fps_s} | {p95_s} | {badge} | {c.notes} <br>*(Source: {c.source_ref})* |"
        )

    lines.extend([
        "",
        "---",
        "",
        "## 4. Physical Device Performance & Tracking Matrix (Empirical Vision Pipeline Benchmarks)",
        "",
        "> [!IMPORTANT]",
        "> **Historical Benchmark Provenance:** Physical device `10BE8634H4001GF`, Android 14 (API 34), captured in `docs/P1_vision.md` Section 8. ",
        "> Latency is evaluated strictly against the <= 80 ms P95 budget. `HAND_SINGLE` in `QUALITY` and `BALANCED` modes **FAIL** the budget.",
        "",
        "| Target Phone | Tracker | Mode | FPS | P50 (ms) | P95 (ms) | Dropped | Temp | FPS Budget | Latency Budget (<= 80ms) | Status | Historical Evidence Source & Notes |",
        "| :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |",
    ])

    for b in benchmarks:
        fps_s = format_float(b.fps, " FPS")
        p50_s = format_float(b.p50_latency_ms, " ms")
        p95_s = format_float(b.p95_latency_ms, " ms")
        drop_s = str(b.dropped_frames) if b.dropped_frames is not None else "Not recorded"
        temp_s = format_float(b.temp_c, "°C")
        badge = format_status_badge(b.overall_status)
        fps_verdict = "**PASS**" if b.fps_pass else ("**FAIL**" if b.fps_pass is False else "Not recorded")
        lat_verdict = "**PASS**" if b.latency_pass else ("**FAIL**" if b.latency_pass is False else "Not recorded")
        hist_tag = "*(Historical)* " if b.is_historical else ""

        lines.append(
            f"| **{b.device_name}** | `{b.tracker}` | `{b.perf_mode}` | {fps_s} | {p50_s} | {p95_s} | {drop_s} | {temp_s} | {fps_verdict} ({b.fps_criterion}) | {lat_verdict} ({b.latency_criterion}) | {badge} | {hist_tag}{b.notes} <br>*(Source: {b.source_file} {b.source_record}; Conditions: {b.test_conditions})* |"
        )

    lines.extend([
        "",
        "---",
        "",
        "## 5. Full Cross-Product Matrix Completeness (3 Games × 5 Ladder Items × 2 Phones = 30 Cells)",
        "",
        "Explicit evaluation of the complete cross-product showing applicability:",
        "",
        "| Phone | Demo Game | Ladder 1 (Wheel) | Ladder 2 (Lean) | Ladder 3 (Swipe) | Ladder 4 (Slash) | Ladder 5 (Wizard/Calib) |",
        "| :--- | :--- | :---: | :---: | :---: | :---: | :---: |",
        "| **iQOO Z9x 5G** | **Subway Surfers (Candidate)** | _N/A_ | _N/A_ | _NOT TESTED_ | _N/A_ | _NOT TESTED_ |",
        "| **iQOO Z9x 5G** | **Fruit Ninja (Candidate)** | _N/A_ | _N/A_ | _N/A_ | _NOT TESTED_ | _NOT TESTED_ |",
        "| **iQOO Z9x 5G** | **Hill Climb Racing (Candidate)**| _NOT TESTED_ | _NOT TESTED_ | _N/A_ | _N/A_ | _NOT TESTED_ |",
        "| **iQOO Z9 Lite 5G** | **Subway Surfers (Candidate)** | _N/A_ | _N/A_ | _NOT TESTED_ | _N/A_ | **BLOCKED** |",
        "| **iQOO Z9 Lite 5G** | **Fruit Ninja (Candidate)** | _N/A_ | _N/A_ | _N/A_ | _NOT TESTED_ | **BLOCKED** |",
        "| **iQOO Z9 Lite 5G** | **Hill Climb Racing (Candidate)**| _NOT TESTED_ | _NOT TESTED_ | _N/A_ | _N/A_ | **BLOCKED** |",
        "",
        "*Summary of 30-cell cross-product: 14 applicable cells (11 NOT TESTED, 3 BLOCKED, 0 PASS, 0 FAIL); 16 N/A cells (non-applicable combinations).* ",
        "",
        "---",
        "",
        "## 6. Open Items & Owner Agent Decisions Required",
        "",
        "1. **Q7 Game Selection Confirmation:** Owner Agent / human must formally confirm whether candidate games (`Subway Surfers`, `Fruit Ninja`, `Hill Climb Racing`) are accepted as the official demo games.",
        "2. **On-Device Gameplay Integration Runs (E2E-1 / E2E-2):** Execute dedicated on-device gameplay sessions on physical iQOO Z9x 5G to verify touch injection across candidate games under `PERFORMANCE` mode.",
        "3. **Secondary Device Hardware Slot:** Book physical test session for iQOO Z9 Lite 5G to unblock Dimensity 6300 benchmarking and FuntouchOS 14 restricted settings verification.",
        "",
        "---",
        "",
        "## 7. Audit & Provenance Traceability Record",
        "",
        "| Record | Evidence Source | Verification Method | Provenance / Hash |",
        "| :--- | :--- | :--- | :--- |",
        "| Z9x Vision Benchmarks | `docs/P1_vision.md` Section 8 | Physical on-device execution (`10BE8634H4001GF`, Android 14) | Historical Record (Commit `6ecda1c`) |",
        "| OS Version Verification | Master Prompt Section 2 Q9 | Person 1 device audit recorded in `docs/DECISIONS.md` ADR-001 | ADR-001 (Accepted) |",
        "| FistDetector Timing | `calib/src/test/java/.../FistDetectorTest.kt` | 60s continuous open-hand (0 false pauses) + 1s hold | JVM Unit Test (Commit `6ecda1c`) |",
        "| Replay Parity Suite | `fakes/src/test/java/.../ReplayTrackerTest.kt` | Deterministic JSONL fixture playback | JVM Unit Test (Commit `8ac7fd5`) |",
        "| Fixture Integrity | `fixtures/*.jsonl` (5 fixtures) | SHA-256 parity via `tools/test_fixtures.py` | Automated Suite (Commit `130d289`) |",
        "| Live Web Dashboard | `web/dashboard/app.js` | 270 automated resilience checks (0 flakes) | Stage 4E Passed |",
    ])

    return "\n".join(lines)


def print_cli_summary(
    matrix_cells: List[MatrixCell],
    benchmarks: List[BenchmarkRecord],
    devices: Dict[str, DeviceSpec],
):
    total = len(matrix_cells)
    passes = sum(1 for c in matrix_cells if c.status == "PASS")
    fails = sum(1 for c in matrix_cells if c.status == "FAIL")
    blocked = sum(1 for c in matrix_cells if c.status == "BLOCKED")
    not_tested = sum(1 for c in matrix_cells if c.status == "NOT TESTED")

    print("=" * 80)
    print("=== ZORBI P4-7: DEVICE COMPATIBILITY MATRIX & QA SUMMARY ===")
    print("=" * 80)
    print(f"Applicable Demo Game x Ladder Cells: {total}")
    print(f"  [PASS]             {passes:<2} ({passes / total * 100:.1f}%)")
    print(f"  [FAIL]             {fails:<2} ({fails / total * 100:.1f}%)")
    print(f"  [BLOCKED]          {blocked:<2} ({blocked / total * 100:.1f}%)")
    print(f"  [NOT TESTED]       {not_tested:<2} ({not_tested / total * 100:.1f}%)")
    print("-" * 80)

    print("\n[Target Devices (Verified OS per Q9 & ADR-001)]")
    for dev in devices.values():
        print(f"  * {dev.name:<18} | Role: {dev.role:<22} | Status: {dev.hardware_status}")
        print(f"    SoC: {dev.soc}")
        print(f"    OS:  {dev.os_verified} (API {dev.api_level})")
        print(f"    Ref: {dev.os_verification_source}\n")

    print("[Demo Game x Applicable Ladder Matrix (Q7 Status: UNCONFIRMED)]")
    print(f"{'Device':<16} {'Game':<24} {'Ladder Item':<24} {'Status':<12}")
    print("-" * 80)
    for c in matrix_cells:
        ladder_short = f"L{c.ladder_number}: {c.ladder_title[:20]}"
        print(f"{c.device_name:<16} {c.game_name[:22]:<24} {ladder_short:<24} {c.status:<12}")

    print("-" * 80)
    print("\n[Physical Tracker Benchmarks & Latency Evaluation]")
    print(f"{'Device':<16} {'Tracker':<17} {'Mode':<12} {'FPS':<7} {'P95 (ms)':<9} {'Status':<10} {'Latency Check'}")
    print("-" * 80)
    for b in benchmarks:
        fps_s = f"{b.fps:.1f}" if b.fps is not None else "N/A"
        p95_s = f"{b.p95_latency_ms:.1f}" if b.p95_latency_ms is not None else "N/A"
        if b.overall_status in ("NOT TESTED", "BLOCKED"):
            lat_chk = "N/A"
        elif b.tracker == "HAND_SINGLE":
            lat_chk = "PASS (<=80ms)" if b.latency_pass else "FAIL (>80ms)"
        elif b.tracker == "HAND_DUAL_WHEEL":
            lat_chk = "Floor (>=8 FPS)" if b.fps_pass else "FAIL (<8 FPS)"
        elif b.tracker == "POSE_LITE":
            lat_chk = "Lean (>=15 FPS)" if b.fps_pass else "FAIL (<15 FPS)"
        else:
            lat_chk = "N/A"
        print(f"{b.device_name:<16} {b.tracker:<17} {b.perf_mode:<12} {fps_s:<7} {p95_s:<9} {b.overall_status:<10} {lat_chk}")

    print("-" * 80)
    print("\n[Gate T-P4-7 Determination]")
    print("  VERDICT: INCOMPLETE / CONDITIONAL")
    print("           - Game selection Q7 is UNCONFIRMED.")
    print("           - Direct on-device game touch injection logs are missing (NOT TESTED).")
    print("           - Secondary device (Z9 Lite) hardware is pending (NOT TESTED / BLOCKED).")
    print("=" * 80)


# ==============================================================================
# INTEGRITY ASSERTIONS & MAIN
# ==============================================================================

def validate_matrix(matrix_cells: List[MatrixCell], benchmarks: List[BenchmarkRecord]) -> List[str]:
    errors: List[str] = []

    # 1. Validate status values
    valid_statuses = {"PASS", "FAIL", "BLOCKED", "NOT TESTED", "NOT APPLICABLE"}
    for c in matrix_cells:
        if c.status not in valid_statuses:
            errors.append(f"Cell {c.device_name} / {c.game_name} / Ladder {c.ladder_number} has invalid status: {c.status}")
        if c.status == "PASS" and not c.source_ref:
            errors.append(f"Passing cell {c.device_name} / {c.game_name} missing source reference")
        if c.status in ("NOT TESTED", "BLOCKED"):
            if c.fps is not None or c.latency_p95_ms is not None:
                errors.append(f"Untested cell {c.device_name} / {c.game_name} cannot claim FPS or latency")

    # 2. Validate latency classification on HAND_SINGLE benchmarks
    for b in benchmarks:
        if b.tracker == "HAND_SINGLE" and b.p95_latency_ms is not None:
            if b.p95_latency_ms > 80.0 and b.latency_pass is True:
                errors.append(f"HAND_SINGLE P95 {b.p95_latency_ms} ms exceeds 80ms budget but marked latency_pass=True: {b}")
            if b.p95_latency_ms > 80.0 and b.overall_status == "PASS":
                errors.append(f"HAND_SINGLE benchmark {b.perf_mode} with P95 {b.p95_latency_ms} ms incorrectly classified as PASS: {b}")

    # 3. Validate cross-product completeness
    expected_cells = 14  # 7 applicable pairs x 2 phones
    if len(matrix_cells) != expected_cells:
        errors.append(f"Expected {expected_cells} applicable matrix cells, found {len(matrix_cells)}")

    return errors


def main():
    parser = argparse.ArgumentParser(description="ZORBI P4-7 Compatibility Matrix & QA Reporter")
    parser.add_argument("--markdown", action="store_true", help="Output GitHub-flavored Markdown report")
    parser.add_argument("--json", action="store_true", help="Output structured JSON compatibility matrix")
    parser.add_argument("--output", type=str, help="Write output to specified file path")
    parser.add_argument("--check", action="store_true", help="Run automated assertion check on QA records")
    args = parser.parse_args()

    matrix_cells = build_game_ladder_matrix()
    validation_errors = validate_matrix(matrix_cells, BENCHMARKS)

    if args.json:
        data = {
            "metadata": {
                "generated_at": datetime.now().isoformat(),
                "gate": "T-P4-7",
                "authority": "ZORBI_MASTER_PROMPT_v1_1.md.pdf Sections 2, 3.3, 10 & Gate T-P4-7",
                "author": "Person 4",
                "q7_game_selection_status": "UNCONFIRMED",
                "dimensions": {
                    "demo_games": len(DEMO_GAMES),
                    "ladder_items": len(LADDER_ITEMS),
                    "target_devices": len(DEVICES),
                    "applicable_matrix_cells": len(matrix_cells),
                },
                "summary": {
                    "pass": sum(1 for c in matrix_cells if c.status == "PASS"),
                    "fail": sum(1 for c in matrix_cells if c.status == "FAIL"),
                    "blocked": sum(1 for c in matrix_cells if c.status == "BLOCKED"),
                    "not_tested": sum(1 for c in matrix_cells if c.status == "NOT TESTED"),
                },
            },
            "devices": {k: asdict(v) for k, v in DEVICES.items()},
            "demo_games": DEMO_GAMES,
            "ladder_items": LADDER_ITEMS,
            "applicable_matrix": [asdict(c) for c in matrix_cells],
            "tracker_benchmarks": [asdict(b) for b in BENCHMARKS],
        }
        output_content = json.dumps(data, indent=2)
    elif args.markdown:
        output_content = generate_markdown_report(matrix_cells, BENCHMARKS, DEVICES)
    else:
        print_cli_summary(matrix_cells, BENCHMARKS, DEVICES)
        output_content = None

    if args.output and output_content:
        out_path = Path(args.output)
        out_path.parent.mkdir(parents=True, exist_ok=True)
        with open(out_path, "w", encoding="utf-8") as f:
            f.write(output_content)
        print(f"\n[INFO] Report written to: {out_path.resolve()}", file=sys.stderr)
    elif output_content:
        print(output_content)

    if args.check:
        if validation_errors:
            print("[ERROR] Matrix validation failed:", file=sys.stderr)
            for err in validation_errors:
                print(f"  - {err}", file=sys.stderr)
            sys.exit(1)
        print("[SUCCESS] Matrix validation passed: all 14 cells valid, zero incorrect classifications.", file=sys.stderr)
        sys.exit(0)


if __name__ == "__main__":
    main()
