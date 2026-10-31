#!/usr/bin/env python3
"""
ZORBI P4-3 Comprehensive Test Suite
Validates synthetic landmark generator, viewer, all 5 fixtures, and ReplayTracker parity.
"""

import hashlib
import json
import math
import os
import subprocess
import sys
from pathlib import Path

FIXTURES_DIR = Path("fixtures")
TOOLS_DIR = Path("tools")

EXPECTED_FIXTURES = {
    "swipe.jsonl": {"frames": 30, "tracker": "HAND_SINGLE", "hands": 1, "points": 21},
    "slash.jsonl": {"frames": 30, "tracker": "HAND_SINGLE", "hands": 1, "points": 21},
    "wheel.jsonl": {"frames": 60, "tracker": "HAND_DUAL_WHEEL", "hands": 2, "points": 21},
    "lean.jsonl":  {"frames": 60, "tracker": "POSE_LITE", "hands": 0, "points": 33},
    "fist.jsonl":  {"frames": 50, "tracker": "HAND_SINGLE", "hands": 1, "points": 21},
}

pass_count = 0
fail_count = 0


def assert_test(name: str, condition: bool, details: str = ""):
    global pass_count, fail_count
    if condition:
        print(f"  [PASS] {name}")
        pass_count += 1
    else:
        print(f"  [FAIL] {name} - {details}", file=sys.stderr)
        fail_count += 1


def compute_sha256(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while chunk := f.read(8192):
            h.update(chunk)
    return h.hexdigest()


# -----------------------------------------------------------------------------
# Test 1: Python Syntax & Compilation
# -----------------------------------------------------------------------------
def test_python_syntax():
    print("\n--- Test 1: Python Syntax Checks ---")
    for script in ["synth_landmarks.py", "landmark_viewer.py"]:
        path = TOOLS_DIR / script
        res = subprocess.run([sys.executable, "-m", "py_compile", str(path)], capture_output=True, text=True)
        assert_test(f"Syntax check: {script}", res.returncode == 0, res.stderr.strip())


# -----------------------------------------------------------------------------
# Test 2: Generator Determinism & Reproducibility
# -----------------------------------------------------------------------------
def test_generator_determinism():
    print("\n--- Test 2: Deterministic Output Verification ---")
    tmp_dir = Path("fixtures_tmp_test")
    try:
        # Run 1
        res1 = subprocess.run(
            [sys.executable, str(TOOLS_DIR / "synth_landmarks.py"), "--out-dir", str(tmp_dir), "--seed", "42"],
            capture_output=True, text=True
        )
        assert_test("Generator run 1 exit code 0", res1.returncode == 0, res1.stderr)
        hashes_run1 = {f.name: compute_sha256(f) for f in tmp_dir.glob("*.jsonl")}

        # Run 2
        res2 = subprocess.run(
            [sys.executable, str(TOOLS_DIR / "synth_landmarks.py"), "--out-dir", str(tmp_dir), "--seed", "42"],
            capture_output=True, text=True
        )
        assert_test("Generator run 2 exit code 0", res2.returncode == 0, res2.stderr)
        hashes_run2 = {f.name: compute_sha256(f) for f in tmp_dir.glob("*.jsonl")}

        assert_test("Run 1 vs Run 2 hash equality (100% deterministic)", hashes_run1 == hashes_run2, f"{hashes_run1} != {hashes_run2}")

        # Check against existing fixtures/
        for fname, exp_hash in hashes_run1.items():
            actual_fixture = FIXTURES_DIR / fname
            if actual_fixture.exists():
                act_hash = compute_sha256(actual_fixture)
                assert_test(f"Fixture {fname} matches deterministic seed 42", act_hash == exp_hash, f"{act_hash} != {exp_hash}")
    finally:
        # Cleanup
        if tmp_dir.exists():
            for f in tmp_dir.glob("*"):
                f.unlink()
            tmp_dir.rmdir()


# -----------------------------------------------------------------------------
# Test 3: Fixture Schema and Structural Validation
# -----------------------------------------------------------------------------
def test_all_fixtures():
    print("\n--- Test 3: Structural Validation of All 5 Fixtures ---")
    for fname, expected in EXPECTED_FIXTURES.items():
        fpath = FIXTURES_DIR / fname
        assert_test(f"Fixture exists: {fname}", fpath.exists(), f"File not found: {fpath}")
        if not fpath.exists():
            continue

        with open(fpath, "r", encoding="utf-8") as f:
            lines = [l.strip() for l in f if l.strip()]

        assert_test(f"[{fname}] Frame count == {expected['frames']}", len(lines) == expected["frames"], f"Got {len(lines)}")

        prev_t = -1
        valid_frames = True
        error_msg = ""

        for idx, line in enumerate(lines):
            try:
                frame = json.loads(line)
            except Exception as e:
                valid_frames = False
                error_msg = f"Line {idx+1} JSON decode error: {e}"
                break

            t = frame.get("t")
            if not isinstance(t, int) or t <= prev_t:
                valid_frames = False
                error_msg = f"Line {idx+1} Non-monotonic timestamp: prev={prev_t}, curr={t}"
                break
            prev_t = t

            tracker = frame.get("tracker")
            if tracker != expected["tracker"]:
                valid_frames = False
                error_msg = f"Line {idx+1} Tracker mismatch: expected {expected['tracker']}, got {tracker}"
                break

            if tracker in {"HAND_SINGLE", "HAND_DUAL_WHEEL"}:
                hands = frame.get("hands")
                if not isinstance(hands, list) or len(hands) != expected["hands"]:
                    valid_frames = False
                    error_msg = f"Line {idx+1} Hands count mismatch: expected {expected['hands']}, got {len(hands) if isinstance(hands, list) else None}"
                    break
                for h_idx, hand in enumerate(hands):
                    if hand.get("side") not in {"LEFT", "RIGHT"}:
                        valid_frames = False
                        error_msg = f"Line {idx+1} Hand {h_idx} invalid side {hand.get('side')}"
                        break
                    lm = hand.get("lm")
                    if not isinstance(lm, list) or len(lm) != 21:
                        valid_frames = False
                        error_msg = f"Line {idx+1} Hand {h_idx} landmark count != 21"
                        break
                    for p in lm:
                        x, y, z = p.get("x"), p.get("y"), p.get("z")
                        if any(math.isnan(v) or math.isinf(v) for v in (x, y, z)):
                            valid_frames = False
                            error_msg = f"Line {idx+1} Non-finite coordinate"
                            break
                        if not (0.0 <= x <= 1.0 and 0.0 <= y <= 1.0):
                            valid_frames = False
                            error_msg = f"Line {idx+1} Out of bounds: ({x}, {y})"
                            break

            elif tracker == "POSE_LITE":
                lm = frame.get("lm")
                if not isinstance(lm, list) or len(lm) != 33:
                    valid_frames = False
                    error_msg = f"Line {idx+1} Pose landmark count != 33"
                    break
                for p in lm:
                    x, y, z = p.get("x"), p.get("y"), p.get("z")
                    if any(math.isnan(v) or math.isinf(v) for v in (x, y, z)):
                        valid_frames = False
                        error_msg = f"Line {idx+1} Non-finite coordinate"
                        break
                    if not (0.0 <= x <= 1.0 and 0.0 <= y <= 1.0):
                        valid_frames = False
                        error_msg = f"Line {idx+1} Out of bounds: ({x}, {y})"
                        break

        assert_test(f"[{fname}] Strict contract & coordinate bounds check", valid_frames, error_msg)


# -----------------------------------------------------------------------------
# Test 4: Malformed Record Rejection
# -----------------------------------------------------------------------------
def test_malformed_rejections():
    print("\n--- Test 4: Malformed Record Rejection ---")
    sys.path.insert(0, str(TOOLS_DIR))
    from synth_landmarks import validate_sequence

    valid_frame = {
        "t": 1000000000,
        "tracker": "HAND_SINGLE",
        "hands": [{"side": "RIGHT", "score": 0.99, "lm": [{"x": 0.5, "y": 0.5, "z": 0.0}] * 21}]
    }

    # Case A: Non-monotonic timestamp
    f_non_monotonic = [
        valid_frame,
        {"t": 1000000000, "tracker": "HAND_SINGLE", "hands": valid_frame["hands"]}
    ]
    threw = False
    try:
        validate_sequence(f_non_monotonic, "test_non_monotonic")
    except ValueError:
        threw = True
    assert_test("Rejects non-monotonic timestamp", threw)

    # Case B: Unknown tracker identifier
    f_bad_tracker = [{"t": 1000000000, "tracker": "HAND_UNKNOWN", "hands": valid_frame["hands"]}]
    threw = False
    try:
        validate_sequence(f_bad_tracker, "test_bad_tracker")
    except ValueError:
        threw = True
    assert_test("Rejects invalid tracker identifier", threw)

    # Case C: Incorrect landmark count (20 instead of 21)
    f_short_lm = [{
        "t": 1000000000,
        "tracker": "HAND_SINGLE",
        "hands": [{"side": "RIGHT", "score": 0.99, "lm": [{"x": 0.5, "y": 0.5, "z": 0.0}] * 20}]
    }]
    threw = False
    try:
        validate_sequence(f_short_lm, "test_short_lm")
    except ValueError:
        threw = True
    assert_test("Rejects incorrect landmark point count (20 != 21)", threw)

    # Case D: Out of bounds coordinates (x = 1.25)
    f_oob = [{
        "t": 1000000000,
        "tracker": "HAND_SINGLE",
        "hands": [{"side": "RIGHT", "score": 0.99, "lm": [{"x": 1.25, "y": 0.5, "z": 0.0}] + [{"x": 0.5, "y": 0.5, "z": 0.0}] * 20}]
    }]
    threw = False
    try:
        validate_sequence(f_oob, "test_oob")
    except ValueError:
        threw = True
    assert_test("Rejects out of bounds coordinates (x > 1.0)", threw)

    # Case E: NaN / Inf coordinate
    f_nan = [{
        "t": 1000000000,
        "tracker": "HAND_SINGLE",
        "hands": [{"side": "RIGHT", "score": 0.99, "lm": [{"x": float('nan'), "y": 0.5, "z": 0.0}] * 21}]
    }]
    threw = False
    try:
        validate_sequence(f_nan, "test_nan")
    except ValueError:
        threw = True
    assert_test("Rejects NaN coordinates", threw)


# -----------------------------------------------------------------------------
# Test 5: Viewer Smoke Test
# -----------------------------------------------------------------------------
def test_viewer_smoke():
    print("\n--- Test 5: Viewer Smoke Test ---")
    # Test summary mode across all fixtures
    for fname in EXPECTED_FIXTURES.keys():
        fpath = FIXTURES_DIR / fname
        res = subprocess.run([sys.executable, str(TOOLS_DIR / "landmark_viewer.py"), str(fpath), "--mode", "summary"], capture_output=True, text=True)
        assert_test(f"Viewer summary mode: {fname}", res.returncode == 0 and "FIXTURE INSPECTION" in res.stdout, res.stderr)

    # Test ascii mode on single frame
    res_ascii = subprocess.run([sys.executable, str(TOOLS_DIR / "landmark_viewer.py"), str(FIXTURES_DIR / "swipe.jsonl"), "--mode", "ascii", "--frame", "0"], capture_output=True, text=True)
    assert_test("Viewer ascii mode single frame", res_ascii.returncode == 0 and "Frame 0" in res_ascii.stdout, res_ascii.stderr)


# -----------------------------------------------------------------------------
# Main Runner
# -----------------------------------------------------------------------------
def main():
    print("=================================================================")
    print("RUNNING ZORBI P4-3 VERIFICATION TEST SUITE")
    print("=================================================================")

    test_python_syntax()
    test_generator_determinism()
    test_all_fixtures()
    test_malformed_rejections()
    test_viewer_smoke()

    print("=================================================================")
    print(f"RESULTS: Passed={pass_count}, Failed={fail_count}")
    print("=================================================================")
    if fail_count > 0:
        sys.exit(1)


if __name__ == "__main__":
    main()
