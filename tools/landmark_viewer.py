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
def run_gui(frames: List[Dict[str, Any]], title: str = "Zorbi Landmark Viewer") -> None:
    try:
        import tkinter as tk
        from tkinter import ttk
    except ImportError:
        print("Tkinter is not available in this Python environment. Falling back to ASCII mode.")
        run_ascii(frames)
        return

    root = tk.Tk()
    root.title(title)
    root.geometry("640x720")

    canvas_width = 560
    canvas_height = 560

    current_frame_idx = [0]
    is_playing = [False]

    info_var = tk.StringVar()

    top_frame = ttk.Frame(root, padding=6)
    top_frame.pack(fill=tk.X)
    info_label = ttk.Label(top_frame, textvariable=info_var, font=("Consolas", 10))
    info_label.pack(side=tk.LEFT)

    canvas = tk.Canvas(root, width=canvas_width, height=canvas_height, bg="#121212")
    canvas.pack(pady=6)

    def draw_current_frame():
        canvas.delete("all")
        idx = current_frame_idx[0]
        frame = frames[idx]

        tracker = frame.get("tracker", "")
        t_rel = (frame.get("t", 0) - frames[0].get("t", 0)) / 1e9
        info_var.set(f"Frame {idx + 1}/{len(frames)} | {tracker} | +{t_rel:.3f} s")
        slider.set(idx)

        # Draw coordinate guidelines
        canvas.create_rectangle(10, 10, canvas_width - 10, canvas_height - 10, outline="#333333", width=1)
        canvas.create_line(canvas_width / 2, 10, canvas_width / 2, canvas_height - 10, fill="#222222", dash=(2, 4))
        canvas.create_line(10, canvas_height / 2, canvas_width - 10, canvas_height / 2, fill="#222222", dash=(2, 4))

        def to_px(nx: float, ny: float) -> Tuple[float, float]:
            return 10 + nx * (canvas_width - 20), 10 + ny * (canvas_height - 20)

        if tracker in {"HAND_SINGLE", "HAND_DUAL_WHEEL"}:
            for hand in frame.get("hands", []):
                side = hand.get("side", "")
                pts = [to_px(p["x"], p["y"]) for p in hand.get("lm", [])]
                color = "#00E5FF" if side == "RIGHT" else "#FF4081"
                line_color = "#00838F" if side == "RIGHT" else "#C2185B"

                # Draw connections
                for a, b in HAND_CONNECTIONS:
                    if a < len(pts) and b < len(pts):
                        canvas.create_line(pts[a][0], pts[a][1], pts[b][0], pts[b][1], fill=line_color, width=2)

                # Draw joint points
                for p_idx, (px, py) in enumerate(pts):
                    r = 5 if p_idx in (4, 8, 12, 16, 20) else (6 if p_idx == 0 else 3)
                    p_col = "#FFEB3B" if p_idx in (4, 8, 12, 16, 20) else color
                    canvas.create_oval(px - r, py - r, px + r, py + r, fill=p_col, outline="")

        elif tracker == "POSE_LITE":
            pts = [to_px(p["x"], p["y"]) for p in frame.get("lm", [])]
            # Draw connections
            for a, b in POSE_CONNECTIONS:
                if a < len(pts) and b < len(pts):
                    canvas.create_line(pts[a][0], pts[a][1], pts[b][0], pts[b][1], fill="#388E3C", width=2)
            # Draw points
            for p_idx, (px, py) in enumerate(pts):
                r = 4 if p_idx == 0 else 3
                col = "#76FF03"
                canvas.create_oval(px - r, py - r, px + r, py + r, fill=col, outline="")

    # Controls
    ctrl_frame = ttk.Frame(root, padding=6)
    ctrl_frame.pack(fill=tk.X)

    def on_prev():
        is_playing[0] = False
        current_frame_idx[0] = max(0, current_frame_idx[0] - 1)
        draw_current_frame()

    def on_next():
        is_playing[0] = False
        current_frame_idx[0] = min(len(frames) - 1, current_frame_idx[0] + 1)
        draw_current_frame()

    def toggle_play():
        is_playing[0] = not is_playing[0]
        play_btn.config(text="Pause" if is_playing[0] else "Play")
        if is_playing[0]:
            play_loop()

    def play_loop():
        if is_playing[0]:
            if current_frame_idx[0] + 1 < len(frames):
                current_frame_idx[0] += 1
                draw_current_frame()
                root.after(33, play_loop)
            else:
                is_playing[0] = False
                play_btn.config(text="Play")

    def on_slider(val):
        current_frame_idx[0] = int(float(val))
        draw_current_frame()

    prev_btn = ttk.Button(ctrl_frame, text="< Prev", command=on_prev)
    prev_btn.pack(side=tk.LEFT, padx=4)

    play_btn = ttk.Button(ctrl_frame, text="Play", command=toggle_play)
    play_btn.pack(side=tk.LEFT, padx=4)

    next_btn = ttk.Button(ctrl_frame, text="Next >", command=on_next)
    next_btn.pack(side=tk.LEFT, padx=4)

    slider = ttk.Scale(
        ctrl_frame,
        from_=0,
        to=len(frames) - 1,
        orient=tk.HORIZONTAL,
        command=on_slider,
    )
    slider.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=8)

    draw_current_frame()
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
