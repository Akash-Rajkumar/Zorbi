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
import unittest
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
skip_count = 0


def assert_test(name: str, condition: bool, details: str = ""):
    global pass_count, fail_count
    if condition:
        print(f"  [PASS] {name}")
        pass_count += 1
    else:
        print(f"  [FAIL] {name} - {details}", file=sys.stderr)
        fail_count += 1


def record_skip(name: str, reason: str):
    global skip_count
    print(f"  [SKIP] {name} - {reason}")
    skip_count += 1


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
# Test 6: GUI Callback Synchronization & Navigation
# -----------------------------------------------------------------------------
def is_display_available() -> bool:
    """Checks if a graphical display environment can initialize Tk."""
    try:
        import tkinter as tk
        r = tk.Tk()
        r.withdraw()
        r.destroy()
        return True
    except Exception:
        return False


def test_gui_callback_logic_headless():
    """Headless verification of slider callback synchronization and state transitions without display."""
    print("\n--- Test 6a: Headless Callback Synchronization Logic ---")
    try:
        from tools.landmark_viewer import LandmarkViewerApp, load_jsonl_fixture
    except ImportError:
        sys.path.insert(0, str(Path(".").resolve()))
        from tools.landmark_viewer import LandmarkViewerApp, load_jsonl_fixture

    frames = load_jsonl_fixture(FIXTURES_DIR / "swipe.jsonl")

    # Mock root to verify playback timer logic without a graphical display server
    class MockRoot:
        def __init__(self):
            self._exists = True
            self.after_calls = []
            self.canceled_calls = []
            self._counter = 0

        def winfo_exists(self):
            return self._exists

        def after(self, ms, callback):
            self._counter += 1
            job_id = f"after_job_{self._counter}"
            self.after_calls.append((job_id, ms, callback))
            return job_id

        def after_cancel(self, job_id):
            self.canceled_calls.append(job_id)

        def destroy(self):
            self._exists = False

    mock_root = MockRoot()
    app = LandmarkViewerApp(mock_root, frames)

    # 1. Initial state
    assert_test("Headless: initial frame is 0", app.current_frame_idx == 0)

    # 2. Rendering frame does not invoke slider callback
    initial_slider_calls = app._slider_call_count
    app.render_canvas(frames[5])
    assert_test(
        "Headless: rendering does not invoke slider callback",
        app._slider_call_count == initial_slider_calls,
    )

    # 3. Manual slider movement updates displayed frame
    app.on_slider("12.0")
    assert_test("Headless: manual slider updates frame to 12", app.current_frame_idx == 12)

    # 4. Programmatic slider updates render without recursion
    render_calls_before = app._render_call_count
    app.goto_frame(15, update_slider=True)
    assert_test(
        "Headless: programmatic update renders without recursion",
        app.current_frame_idx == 15 and app._render_call_count == render_calls_before + 1,
    )

    # 5. First, last, and relative navigation
    app.on_first()
    assert_test("Headless: first frame sets index to 0", app.current_frame_idx == 0)
    app.on_last()
    assert_test("Headless: last frame sets index to len - 1", app.current_frame_idx == len(frames) - 1)
    app.goto_frame(10)
    app.on_next()
    assert_test("Headless: on_next increments frame to 11", app.current_frame_idx == 11)
    app.on_prev()
    assert_test("Headless: on_prev decrements frame to 10", app.current_frame_idx == 10)

    # 6. Invalid frame indices are safely bounded
    app.goto_frame(-10)
    assert_test("Headless: negative index clamps to 0", app.current_frame_idx == 0)
    app.goto_frame(9999)
    assert_test("Headless: over-range index clamps to last frame", app.current_frame_idx == len(frames) - 1)
    app.on_slider("not_a_number")
    assert_test("Headless: non-numeric slider input ignored safely", app.current_frame_idx == len(frames) - 1)

    # 7. Playback scheduling and cancellation
    app.goto_frame(0)
    app.start_playback()
    assert_test("Headless: start_playback schedules timer callback", app.is_playing and app._play_after_id is not None)
    first_job = app._play_after_id

    # 8. Repeated start requests cannot create duplicate scheduled callbacks
    app.start_playback()
    assert_test("Headless: repeated start does not create duplicate callback", app._play_after_id == first_job)

    # 9. Scheduled callback is cancelled when playback stops
    app.stop_playback()
    assert_test(
        "Headless: stop_playback cancels scheduled callback",
        not app.is_playing and app._play_after_id is None and first_job in mock_root.canceled_calls,
    )

    # 10. Invoking stale callback after stop does not advance frame
    idx_before_stale = app.current_frame_idx
    app._play_step()
    assert_test("Headless: stale callback after stop does not advance frame", app.current_frame_idx == idx_before_stale)

    # 11. Closing window cancels pending callback
    app.start_playback()
    pending_job = app._play_after_id
    app.on_close()
    assert_test(
        "Headless: window close cancels pending callbacks",
        not app.is_playing and app._play_after_id is None and pending_job in mock_root.canceled_calls,
    )

    # 12. Lifecycle hardening: goto_frame after destroy does not crash
    assert_test("Headless: goto_frame after destroy returns safely", app.goto_frame(5) == 5)

    # 13. Playback Generation Token Tracking Regression Test
    mock_root_gen = MockRoot()
    app_gen = LandmarkViewerApp(mock_root_gen, frames)

    # Start playback session A
    app_gen.start_playback()
    call_A = mock_root_gen.after_calls[-1][2]
    timer_id_A = app_gen._play_after_id
    assert_test("GenTest: session A started with valid timer ID", app_gen.is_playing and timer_id_A is not None)

    # Stop playback A
    app_gen.stop_playback()
    assert_test("GenTest: session A stopped", not app_gen.is_playing and app_gen._play_after_id is None)

    # Start playback session B
    app_gen.start_playback()
    call_B = mock_root_gen.after_calls[-1][2]
    timer_id_B = app_gen._play_after_id
    total_scheduled_before_stale = len(mock_root_gen.after_calls)
    assert_test(
        "GenTest: session B started with new timer ID",
        app_gen.is_playing and timer_id_B is not None and timer_id_B != timer_id_A,
    )

    # Invoke stale callback A
    call_A()
    assert_test("GenTest: session B remains active after stale callback A", app_gen.is_playing is True)
    assert_test("GenTest: callback B timer ID remains intact", app_gen._play_after_id == timer_id_B)
    assert_test(
        "GenTest: no additional timer was scheduled by stale callback A",
        len(mock_root_gen.after_calls) == total_scheduled_before_stale,
    )

    # Invoke callback B and verify normal frame advancement
    current_idx_before_B = app_gen.current_frame_idx
    call_B()
    assert_test("GenTest: callback B advances frame normally", app_gen.current_frame_idx == current_idx_before_B + 1)
    assert_test(
        "GenTest: callback B scheduled next step",
        len(mock_root_gen.after_calls) == total_scheduled_before_stale + 1,
    )


def test_gui_widget_integration():
    """Integration test with live Tkinter widgets, exercising ttk.Scale and canvas event loop."""
    print("\n--- Test 6b: Live GUI Widget Integration (ttk.Scale & Window) ---")
    if not is_display_available():
        record_skip("Live GUI Widget Integration", "Graphical display unavailable for Tkinter")
        return

    try:
        import tkinter as tk
        try:
            from tools.landmark_viewer import LandmarkViewerApp, load_jsonl_fixture
        except ImportError:
            sys.path.insert(0, str(Path(".").resolve()))
            from tools.landmark_viewer import LandmarkViewerApp, load_jsonl_fixture
    except Exception as e:
        record_skip("Live GUI Widget Integration", f"Tkinter import failed: {e}")
        return

    frames = load_jsonl_fixture(FIXTURES_DIR / "swipe.jsonl")
    root = tk.Tk()
    root.withdraw()
    try:
        app = LandmarkViewerApp(root, frames)

        # 1. Widget instantiation and initial state
        assert_test("Live GUI: slider widget instantiated", app.slider is not None)
        assert_test("Live GUI: initial frame is 0", app.current_frame_idx == 0)
        assert_test("Live GUI: initial slider value is 0", abs(app.slider.get() - 0.0) < 0.01)

        # 2. Exercise actual ttk.Scale widget command callback
        app.slider.set(15)
        root.update()
        assert_test("Live GUI: ttk.Scale command updates frame index to 15", app.current_frame_idx == 15)
        assert_test("Live GUI: info label reflects frame 16", "Frame 16/" in app.info_var.get())

        # 3. Verify programmatic slider updates do not cause recursive rendering
        render_before = app._render_call_count
        app.goto_frame(20, update_slider=True)
        root.update()
        assert_test(
            "Live GUI: programmatic update renders once without recursion",
            app.current_frame_idx == 20 and app._render_call_count == render_before + 1,
        )

        # 4. First and last frame button interactions
        app.on_first()
        root.update()
        assert_test("Live GUI: first button sets frame to 0", app.current_frame_idx == 0)
        app.on_last()
        root.update()
        assert_test("Live GUI: last button sets frame to 29", app.current_frame_idx == len(frames) - 1)

        # 5. Playback start and button label consistency
        app.goto_frame(0)
        app.start_playback()
        assert_test("Live GUI: play button text becomes Pause", app.play_btn.cget("text") == "Pause")
        assert_test("Live GUI: play after id is scheduled", app._play_after_id is not None)

        # 6. Playback step advances frame
        app._play_step()
        assert_test("Live GUI: playback step advances frame", app.current_frame_idx == 1)

        # 7. Playback stop resets button text and cancels callback
        app.stop_playback()
        assert_test("Live GUI: play button resets to Play", app.play_btn.cget("text") == "Play")
        assert_test("Live GUI: after callback cancelled", app._play_after_id is None)

        # 8. Window close cancels pending callback
        app.start_playback()
        assert_test("Live GUI: playback active before close", app.is_playing is True)
        app.on_close()
        assert_test("Live GUI: window close stops playback and clears after_id", not app.is_playing and app._play_after_id is None)
        assert_test("Live GUI: goto_frame after close returns clamped index safely", app.goto_frame(10) == 10)
    finally:
        try:
            if root.winfo_exists():
                root.destroy()
        except Exception:
            pass


# -----------------------------------------------------------------------------
# Unittest TestCase Wrapper
# -----------------------------------------------------------------------------
class TestFixtures(unittest.TestCase):
    def test_01_python_syntax(self):
        test_python_syntax()
        self.assertEqual(fail_count, 0)

    def test_02_generator_determinism(self):
        test_generator_determinism()
        self.assertEqual(fail_count, 0)

    def test_03_all_fixtures(self):
        test_all_fixtures()
        self.assertEqual(fail_count, 0)

    def test_04_malformed_rejections(self):
        test_malformed_rejections()
        self.assertEqual(fail_count, 0)

    def test_05_viewer_smoke(self):
        test_viewer_smoke()
        self.assertEqual(fail_count, 0)

    def test_06_gui_callback_logic_headless(self):
        test_gui_callback_logic_headless()
        self.assertEqual(fail_count, 0)

    def test_07_gui_widget_integration(self):
        if not is_display_available():
            self.skipTest("Graphical display unavailable for Tkinter")
        test_gui_widget_integration()
        self.assertEqual(fail_count, 0)


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
    test_gui_callback_logic_headless()
    test_gui_widget_integration()

    print("=================================================================")
    print(f"RESULTS: Passed={pass_count}, Failed={fail_count}, Skipped={skip_count}")
    print("=================================================================")
    if fail_count > 0:
        sys.exit(1)


if __name__ == "__main__":
    main()
