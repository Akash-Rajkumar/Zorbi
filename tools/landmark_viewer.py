#!/usr/bin/env python3
"""
ZORBI Landmark Viewer (P4-3)
Visualizes synthetic or recorded MediaPipe landmark JSONL sequences.

Modes:
  - ascii: Terminal-based ASCII/Unicode 2D canvas playback (zero dependencies, works in any terminal).
  - gui: Interactive Tkinter desktop window with playback controls, timeline slider, and skeletal connections.
  - summary: Non-interactive inspection of frames, motion delta, and landmark bounds.
"""

import argparse
import json
import math
import os
import sys
import time
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

# MediaPipe Hand skeletal connection pairs (0..20)
HAND_CONNECTIONS = [
    # Palm
    (0, 1),
    (0, 5),
    (5, 9),
    (9, 13),
    (13, 17),
    (0, 17),
    # Thumb
    (1, 2),
    (2, 3),
    (3, 4),
    # Index
    (5, 6),
    (6, 7),
    (7, 8),
    # Middle
    (9, 10),
    (10, 11),
    (11, 12),
    # Ring
    (13, 14),
    (14, 15),
    (15, 16),
    # Pinky
    (17, 18),
    (18, 19),
    (19, 20),
]

# MediaPipe Pose skeletal connection pairs (0..32)
POSE_CONNECTIONS = [
    # Face & head
    (0, 1),
    (1, 2),
    (2, 3),
    (3, 7),
    (0, 4),
    (4, 5),
    (5, 6),
    (6, 8),
    (9, 10),
    # Torso
    (11, 12),
    (11, 23),
    (12, 24),
    (23, 24),
    # Left Arm
    (11, 13),
    (13, 15),
    (15, 17),
    (15, 19),
    (15, 21),
    (17, 19),
    # Right Arm
    (12, 14),
    (14, 16),
    (16, 18),
    (16, 20),
    (16, 22),
    (18, 20),
    # Left Leg
    (23, 25),
    (25, 27),
    (27, 29),
    (29, 31),
    (27, 31),
    # Right Leg
    (24, 26),
    (26, 28),
    (28, 30),
    (30, 32),
    (28, 32),
]


def load_jsonl_fixture(path: Path) -> List[Dict[str, Any]]:
    """Loads and validates a JSONL fixture from disk, reporting line numbers on parse error."""
    if not path.exists():
        raise FileNotFoundError(f"Fixture file not found: {path}")

    frames = []
    with open(path, "r", encoding="utf-8") as f:
        for line_num, line in enumerate(f, start=1):
            line_str = line.strip()
            if not line_str:
                continue
            try:
                data = json.loads(line_str)
                frames.append(data)
            except json.JSONDecodeError as err:
                raise ValueError(
                    f"Malformed JSON at {path}:{line_num}: {err.msg}"
                ) from err
    if not frames:
        raise ValueError(f"Fixture file {path} contains no valid frames.")
    return frames


# -----------------------------------------------------------------------------
# Summary Mode (Inspection & Smoke Test)
# -----------------------------------------------------------------------------
def run_summary(frames: List[Dict[str, Any]], filename: str) -> None:
    print(f"\n=======================================================")
    print(f"FIXTURE INSPECTION: {filename}")
    print(f"Total Frames : {len(frames)}")

    first_t = frames[0].get("t", 0)
    last_t = frames[-1].get("t", 0)
    duration_s = (last_t - first_t) / 1e9 if last_t >= first_t else 0.0
    tracker = frames[0].get("tracker", "UNKNOWN")

    print(f"Tracker ID   : {tracker}")
    print(f"Duration     : {duration_s:.3f} s ({first_t} ns -> {last_t} ns)")

    # Compute trajectory bounds and motion delta
    centroids: List[Tuple[float, float]] = []
    for idx, frame in enumerate(frames):
        pts = []
        if tracker in {"HAND_SINGLE", "HAND_DUAL_WHEEL"}:
            for hand in frame.get("hands", []):
                for pt in hand.get("lm", []):
                    pts.append((pt["x"], pt["y"]))
        elif tracker == "POSE_LITE":
            for pt in frame.get("lm", []):
                pts.append((pt["x"], pt["y"]))

        if pts:
            avg_x = sum(p[0] for p in pts) / len(pts)
            avg_y = sum(p[1] for p in pts) / len(pts)
            centroids.append((avg_x, avg_y))

    if centroids:
        min_x = min(c[0] for c in centroids)
        max_x = max(c[0] for c in centroids)
        min_y = min(c[1] for c in centroids)
        max_y = max(c[1] for c in centroids)
        print(f"Centroid X   : min={min_x:.3f}, max={max_x:.3f}, span={max_x - min_x:.3f}")
        print(f"Centroid Y   : min={min_y:.3f}, max={max_y:.3f}, span={max_y - min_y:.3f}")

        # Compute total travel distance
        travel = 0.0
        for i in range(1, len(centroids)):
            dx = centroids[i][0] - centroids[i - 1][0]
            dy = centroids[i][1] - centroids[i - 1][1]
            travel += math.hypot(dx, dy)
        print(f"Total Travel : {travel:.4f} normalized units")

    # Sample first and last frame details
    print(f"Sample Frame 0 details:")
    if tracker in {"HAND_SINGLE", "HAND_DUAL_WHEEL"}:
        for h_i, hand in enumerate(frames[0].get("hands", [])):
            side = hand.get("side")
            score = hand.get("score")
            lm_count = len(hand.get("lm", []))
            print(f"  Hand {h_i}: side={side}, score={score}, landmarks={lm_count}")
    elif tracker == "POSE_LITE":
        score = frames[0].get("score")
        lm_count = len(frames[0].get("lm", []))
        print(f"  Pose: score={score}, landmarks={lm_count}")
    print(f"=======================================================\n")


# -----------------------------------------------------------------------------
# Terminal ASCII Mode
# -----------------------------------------------------------------------------
def render_ascii_frame(frame: Dict[str, Any], width: int = 60, height: int = 24) -> str:
    tracker = frame.get("tracker", "")
    t = frame.get("t", 0)

    # Initialize canvas
    grid = [[" " for _ in range(width)] for _ in range(height)]

    # Draw border
    for x in range(width):
        grid[0][x] = "-"
        grid[height - 1][x] = "-"
    for y in range(height):
        grid[y][0] = "|"
        grid[y][width - 1] = "|"
    grid[0][0] = "+"
    grid[0][width - 1] = "+"
    grid[height - 1][0] = "+"
    grid[height - 1][width - 1] = "+"

    def plot(nx: float, ny: float, ch: str):
        col = int(nx * (width - 3)) + 1
        row = int(ny * (height - 3)) + 1
        if 1 <= col < width - 1 and 1 <= row < height - 1:
            grid[row][col] = ch

    if tracker in {"HAND_SINGLE", "HAND_DUAL_WHEEL"}:
        for hand in frame.get("hands", []):
            side = hand.get("side", "")
            ch = "R" if side == "RIGHT" else ("L" if side == "LEFT" else "H")
            wrist_ch = "W"
            for idx, pt in enumerate(hand.get("lm", [])):
                plot(pt["x"], pt["y"], wrist_ch if idx == 0 else ch)
    elif tracker == "POSE_LITE":
        for idx, pt in enumerate(frame.get("lm", [])):
            ch = "*"
            if idx == 0:
                ch = "N"  # Nose
            elif idx in (11, 12):
                ch = "S"  # Shoulders
            elif idx in (15, 16):
                ch = "W"  # Wrists
            elif idx in (23, 24):
                ch = "H"  # Hips
            plot(pt["x"], pt["y"], ch)

    lines = ["".join(row) for row in grid]
    return "\n".join(lines)


def run_ascii(frames: List[Dict[str, Any]], fps: float = 30.0, frame_idx: Optional[int] = None) -> None:
    if frame_idx is not None:
        idx = max(0, min(len(frames) - 1, frame_idx))
        print(f"--- Frame {idx} / {len(frames)} (t={frames[idx].get('t')} ns) ---")
        print(render_ascii_frame(frames[idx]))
        return

    delay = 1.0 / fps if fps > 0 else 0.033
    print(f"Starting ASCII playback ({len(frames)} frames at ~{fps:.1f} fps)...")
    try:
        for idx, frame in enumerate(frames):
            os.system("cls" if os.name == "nt" else "clear")
            tracker = frame.get("tracker")
            t_rel = (frame.get("t", 0) - frames[0].get("t", 0)) / 1e9
            print(f"ZORBI LANDMARK VIEWER | Frame {idx + 1}/{len(frames)} | Tracker: {tracker} | Time: +{t_rel:.3f}s")
            print(render_ascii_frame(frame))
            time.sleep(delay)
    except KeyboardInterrupt:
        print("\nPlayback interrupted by user.")


# -----------------------------------------------------------------------------
# Desktop Tkinter GUI Mode
# -----------------------------------------------------------------------------
class LandmarkViewerApp:
    """
    Tkinter desktop viewer for MediaPipe landmark sequences.
    Implements robust callback synchronization preventing slider/renderer recursion loops.
    """

    def __init__(self, root: Any = None, frames: Optional[List[Dict[str, Any]]] = None, title: str = "Zorbi Landmark Viewer"):
        self.root = root
        self.frames = frames or []
        self.total_frames = len(self.frames)
        self.current_frame_idx = 0
        self.is_playing = False
        self._slider_updating = False
        self._play_after_id: Optional[str] = None
        self._playback_generation = 0
        self._slider_call_count = 0
        self._render_call_count = 0

        self.canvas_width = 560
        self.canvas_height = 560

        self.info_var = None
        self.info_label = None
        self.canvas = None
        self.slider = None
        self.play_btn = None
        self.first_btn = None
        self.prev_btn = None
        self.next_btn = None
        self.last_btn = None

        is_real_tk = False
        if self.root is not None and self._is_alive():
            try:
                import tkinter as tk
                if hasattr(self.root, "tk"):
                    is_real_tk = True
            except Exception:
                is_real_tk = False

        if is_real_tk:
            import tkinter as tk
            from tkinter import ttk

            self.root.title(title)
            self.root.geometry("640x720")

            self.info_var = tk.StringVar()

            top_frame = ttk.Frame(self.root, padding=6)
            top_frame.pack(fill=tk.X)
            self.info_label = ttk.Label(top_frame, textvariable=self.info_var, font=("Consolas", 10))
            self.info_label.pack(side=tk.LEFT)

            self.canvas = tk.Canvas(self.root, width=self.canvas_width, height=self.canvas_height, bg="#121212")
            self.canvas.pack(pady=6)

            # Controls
            ctrl_frame = ttk.Frame(self.root, padding=6)
            ctrl_frame.pack(fill=tk.X)

            self.first_btn = ttk.Button(ctrl_frame, text="|<", width=3, command=self.on_first)
            self.first_btn.pack(side=tk.LEFT, padx=2)

            self.prev_btn = ttk.Button(ctrl_frame, text="< Prev", command=self.on_prev)
            self.prev_btn.pack(side=tk.LEFT, padx=2)

            self.play_btn = ttk.Button(ctrl_frame, text="Play", command=self.toggle_play)
            self.play_btn.pack(side=tk.LEFT, padx=2)

            self.next_btn = ttk.Button(ctrl_frame, text="Next >", command=self.on_next)
            self.next_btn.pack(side=tk.LEFT, padx=2)

            self.last_btn = ttk.Button(ctrl_frame, text=">|", width=3, command=self.on_last)
            self.last_btn.pack(side=tk.LEFT, padx=2)

            max_idx = max(0, self.total_frames - 1)
            self.slider = ttk.Scale(
                ctrl_frame,
                from_=0,
                to=max_idx,
                orient=tk.HORIZONTAL,
                command=self.on_slider,
            )
            self.slider.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=8)

            # Keyboard shortcuts
            self.root.bind("<Left>", lambda e: self.on_prev())
            self.root.bind("<Right>", lambda e: self.on_next())
            self.root.bind("<Home>", lambda e: self.on_first())
            self.root.bind("<End>", lambda e: self.on_last())
            self.root.bind("<space>", lambda e: self.toggle_play())

            self.root.protocol("WM_DELETE_WINDOW", self.on_close)

        # Initial render: display frame 0 and synchronize slider with guard active
        self.goto_frame(0, update_slider=True)

    def render_canvas(self, frame: Dict[str, Any]) -> None:
        """Renders the given frame landmarks and skeletal connections on the canvas."""
        self._render_call_count += 1
        if not self._is_alive() or self.canvas is None:
            return

        self.canvas.delete("all")
        idx = self.current_frame_idx
        tracker = frame.get("tracker", "")
        first_t = self.frames[0].get("t", 0) if self.frames else 0
        t_rel = (frame.get("t", 0) - first_t) / 1e9
        if self.info_var is not None:
            self.info_var.set(f"Frame {idx + 1}/{self.total_frames} | {tracker} | +{t_rel:.3f} s")

        cw, ch = self.canvas_width, self.canvas_height
        self.canvas.create_rectangle(10, 10, cw - 10, ch - 10, outline="#333333", width=1)
        self.canvas.create_line(cw / 2, 10, cw / 2, ch - 10, fill="#222222", dash=(2, 4))
        self.canvas.create_line(10, ch / 2, cw - 10, ch / 2, fill="#222222", dash=(2, 4))

        def to_px(nx: float, ny: float) -> Tuple[float, float]:
            return 10 + nx * (cw - 20), 10 + ny * (ch - 20)

        if tracker in {"HAND_SINGLE", "HAND_DUAL_WHEEL"}:
            for hand in frame.get("hands", []):
                side = hand.get("side", "")
                pts = [to_px(p["x"], p["y"]) for p in hand.get("lm", [])]
                color = "#00E5FF" if side == "RIGHT" else "#FF4081"
                line_color = "#00838F" if side == "RIGHT" else "#C2185B"

                # Draw skeletal connections
                for a, b in HAND_CONNECTIONS:
                    if a < len(pts) and b < len(pts):
                        self.canvas.create_line(pts[a][0], pts[a][1], pts[b][0], pts[b][1], fill=line_color, width=2)

                # Draw joint points
                for p_idx, (px, py) in enumerate(pts):
                    r = 5 if p_idx in (4, 8, 12, 16, 20) else (6 if p_idx == 0 else 3)
                    p_col = "#FFEB3B" if p_idx in (4, 8, 12, 16, 20) else color
                    self.canvas.create_oval(px - r, py - r, px + r, py + r, fill=p_col, outline="")

        elif tracker == "POSE_LITE":
            pts = [to_px(p["x"], p["y"]) for p in frame.get("lm", [])]
            for a, b in POSE_CONNECTIONS:
                if a < len(pts) and b < len(pts):
                    self.canvas.create_line(pts[a][0], pts[a][1], pts[b][0], pts[b][1], fill="#388E3C", width=2)
            for p_idx, (px, py) in enumerate(pts):
                r = 4 if p_idx == 0 else 3
                col = "#76FF03"
                self.canvas.create_oval(px - r, py - r, px + r, py + r, fill=col, outline="")

    def goto_frame(self, target_idx: int, update_slider: bool = True) -> int:
        """
        Navigates to the specified frame index, safely clamped to [0, total_frames - 1].
        If update_slider is True, updates the slider position using callback guards to prevent recursion.
        Does not attempt canvas or slider operations after the window has been destroyed.
        """
        if self.total_frames == 0:
            return 0
        clamped = max(0, min(self.total_frames - 1, int(target_idx)))
        self.current_frame_idx = clamped

        if not self._is_alive():
            return clamped

        self.render_canvas(self.frames[clamped])

        if update_slider and self._is_alive() and self.slider is not None:
            self._slider_updating = True
            try:
                self.slider.set(clamped)
            finally:
                self._slider_updating = False

        return clamped

    def on_slider(self, val: Any) -> None:
        """
        Callback invoked when the user scrubs or clicks the slider.
        Guarded against re-entrant calls when the slider is set programmatically.
        """
        self._slider_call_count += 1
        if self._slider_updating:
            return

        try:
            val_float = float(val)
        except (ValueError, TypeError):
            return

        idx = int(round(val_float))
        if self.is_playing:
            self.stop_playback()

        # Update canvas without re-triggering slider.set to avoid callback loops
        self.goto_frame(idx, update_slider=False)

    def on_prev(self) -> None:
        if self.is_playing:
            self.stop_playback()
        self.goto_frame(self.current_frame_idx - 1, update_slider=True)

    def on_next(self) -> None:
        if self.is_playing:
            self.stop_playback()
        self.goto_frame(self.current_frame_idx + 1, update_slider=True)

    def on_first(self) -> None:
        if self.is_playing:
            self.stop_playback()
        self.goto_frame(0, update_slider=True)

    def on_last(self) -> None:
        if self.is_playing:
            self.stop_playback()
        self.goto_frame(self.total_frames - 1, update_slider=True)

    def toggle_play(self) -> None:
        if self.is_playing:
            self.stop_playback()
        else:
            self.start_playback()

    def start_playback(self) -> None:
        if self.total_frames <= 1 or self.is_playing:
            return
        if self.current_frame_idx >= self.total_frames - 1:
            self.goto_frame(0, update_slider=True)
        self._playback_generation += 1
        self.is_playing = True
        if self._is_alive() and self.play_btn is not None:
            self.play_btn.config(text="Pause")
        self._schedule_next_step()

    def stop_playback(self) -> None:
        self._playback_generation += 1
        self.is_playing = False
        if self._play_after_id is not None:
            if self._is_alive():
                try:
                    self.root.after_cancel(self._play_after_id)
                except Exception:
                    pass
            self._play_after_id = None
        if self._is_alive() and self.play_btn is not None:
            self.play_btn.config(text="Play")

    def _schedule_next_step(self) -> None:
        if not self.is_playing or not self._is_alive():
            return
        if self._play_after_id is not None:
            # Prevent duplicate pending timer callbacks
            return
        current_gen = self._playback_generation
        self._play_after_id = self.root.after(33, lambda g=current_gen: self._play_step(g))

    def _play_step(self, generation: Optional[int] = None) -> None:
        if generation is not None and generation != self._playback_generation:
            return
        if not self.is_playing or not self._is_alive():
            return
        self._play_after_id = None
        if self.current_frame_idx + 1 < self.total_frames:
            self.goto_frame(self.current_frame_idx + 1, update_slider=True)
            self._schedule_next_step()
        else:
            self.stop_playback()

    def _is_alive(self) -> bool:
        if self.root is None:
            return False
        try:
            return bool(self.root.winfo_exists())
        except Exception:
            return False

    def on_close(self) -> None:
        self.stop_playback()
        if self._is_alive():
            self.root.destroy()


def run_gui(frames: List[Dict[str, Any]], title: str = "Zorbi Landmark Viewer") -> None:
    try:
        import tkinter as tk
    except ImportError:
        print("Tkinter is not available in this Python environment. Falling back to ASCII mode.")
        run_ascii(frames)
        return

    root = tk.Tk()
    app = LandmarkViewerApp(root, frames, title=title)
    root.mainloop()


# -----------------------------------------------------------------------------
# Main CLI Entry Point
# -----------------------------------------------------------------------------
def main():
    parser = argparse.ArgumentParser(description="Zorbi Landmark Viewer (P4-3)")
    parser.add_argument("fixture", type=Path, help="Path to .jsonl fixture file")
    parser.add_argument(
        "--mode",
        choices=["gui", "ascii", "summary"],
        default="ascii",
        help="Display mode: 'ascii' terminal canvas, 'gui' window, or 'summary' static inspection (default: ascii)",
    )
    parser.add_argument(
        "--frame",
        type=int,
        default=None,
        help="Display a specific frame index in ascii mode",
    )
    parser.add_argument(
        "--fps",
        type=float,
        default=30.0,
        help="Playback framerate in ascii mode (default: 30)",
    )
    args = parser.parse_args()

    try:
        frames = load_jsonl_fixture(args.fixture)
    except Exception as err:
        print(f"Error loading fixture: {err}", file=sys.stderr)
        sys.exit(1)

    if args.mode == "summary":
        run_summary(frames, args.fixture.name)
    elif args.mode == "ascii":
        run_ascii(frames, fps=args.fps, frame_idx=args.frame)
    elif args.mode == "gui":
        run_gui(frames, title=f"Zorbi Landmark Viewer - {args.fixture.name}")


if __name__ == "__main__":
    main()
