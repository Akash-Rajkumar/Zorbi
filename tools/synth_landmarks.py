#!/usr/bin/env python3
"""
ZORBI Synthetic Landmark Generator (P4-3)
Generates deterministic synthetic MediaPipe landmark sequences formatted as JSONL fixtures.

Supported Sequences:
  - swipe: Rapid horizontal hand swipe (HAND_SINGLE, 21 landmarks)
  - slash: Rapid diagonal slashing motion (HAND_SINGLE, 21 landmarks)
  - wheel: Dual hands rotating a virtual steering wheel (HAND_DUAL_WHEEL, 21 landmarks/hand)
  - lean: Body leaning left and right (POSE_LITE, 33 landmarks)
  - fist: Open hand closing into a fist and holding for ~1 sec (HAND_SINGLE, 21 landmarks)
"""

import argparse
import json
import math
import os
import random
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

FRAME_INTERVAL_NANOS = 33_333_333  # ~30 FPS (33.33 ms)
BASE_TIMESTAMP_NANOS = 1_000_000_000  # 1.0 second baseline


def clamp(val: float, min_val: float = 0.0, max_val: float = 1.0) -> float:
    return max(min_val, min(max_val, val))


# -----------------------------------------------------------------------------
# MediaPipe Hand Model Geometry (21 Points)
# -----------------------------------------------------------------------------
def generate_hand_landmarks(
    palm_x: float,
    palm_y: float,
    palm_z: float = 0.0,
    hand_scale: float = 0.12,
    curl: float = 0.0,
    rotation_deg: float = 0.0,
    side: str = "RIGHT",
) -> List[Dict[str, float]]:
    """
    Generates 21 MediaPipe hand landmarks centered around (palm_x, palm_y).
    curl: 0.0 = fully open, 1.0 = tightly closed fist.
    rotation_deg: Clockwise rotation in degrees around palm center.
    """
    rot_rad = math.radians(rotation_deg)
    cos_r = math.cos(rot_rad)
    sin_r = math.sin(rot_rad)

    def rot(dx: float, dy: float) -> Tuple[float, float]:
        rx = dx * cos_r - dy * sin_r
        ry = dx * sin_r + dy * cos_r
        return rx, ry

    # Side multiplier: thumb is on opposite sides
    side_mult = 1.0 if side == "RIGHT" else -1.0

    # Base canonical offsets relative to palm center (before curl/rotation)
    # 0: Wrist
    wrist_dx, wrist_dy = 0.0, 0.45 * hand_scale

    # Curl factor shortens finger extension and brings tips toward palm
    ext = 1.0 - 0.75 * curl
    z_curl = -0.04 * curl

    # Finger rays (MCP, PIP, DIP, TIP)
    # Thumb (1..4)
    t_mcp = (0.22 * side_mult * hand_scale, 0.25 * hand_scale)
    t_pip = (0.32 * side_mult * hand_scale, 0.12 * hand_scale * ext)
    t_dip = (0.38 * side_mult * hand_scale, -0.02 * hand_scale * ext)
    t_tip = (
        0.42 * side_mult * hand_scale * (1.0 - 0.5 * curl),
        -0.12 * hand_scale * ext + (0.25 * hand_scale * curl),
    )

    # Index (5..8)
    i_mcp = (0.12 * side_mult * hand_scale, -0.15 * hand_scale)
    i_pip = (0.12 * side_mult * hand_scale, -0.40 * hand_scale * ext)
    i_dip = (0.12 * side_mult * hand_scale, -0.65 * hand_scale * ext)
    i_tip = (
        0.12 * side_mult * hand_scale,
        (-0.90 * hand_scale * ext) + (0.80 * hand_scale * curl),
    )

    # Middle (9..12)
    m_mcp = (0.00, -0.18 * hand_scale)
    m_pip = (0.00, -0.45 * hand_scale * ext)
    m_dip = (0.00, -0.72 * hand_scale * ext)
    m_tip = (0.00, (-1.00 * hand_scale * ext) + (0.85 * hand_scale * curl))

    # Ring (13..16)
    r_mcp = (-0.11 * side_mult * hand_scale, -0.15 * hand_scale)
    r_pip = (-0.11 * side_mult * hand_scale, -0.38 * hand_scale * ext)
    r_dip = (-0.11 * side_mult * hand_scale, -0.62 * hand_scale * ext)
    r_tip = (
        -0.11 * side_mult * hand_scale,
        (-0.85 * hand_scale * ext) + (0.75 * hand_scale * curl),
    )

    # Pinky (17..20)
    p_mcp = (-0.21 * side_mult * hand_scale, -0.10 * hand_scale)
    p_pip = (-0.21 * side_mult * hand_scale, -0.28 * hand_scale * ext)
    p_dip = (-0.21 * side_mult * hand_scale, -0.48 * hand_scale * ext)
    p_tip = (
        -0.21 * side_mult * hand_scale,
        (-0.68 * hand_scale * ext) + (0.60 * hand_scale * curl),
    )

    raw_points = [
        (wrist_dx, wrist_dy, 0.0),
        (t_mcp[0], t_mcp[1], z_curl * 0.2),
        (t_pip[0], t_pip[1], z_curl * 0.4),
        (t_dip[0], t_dip[1], z_curl * 0.7),
        (t_tip[0], t_tip[1], z_curl * 1.0),
        (i_mcp[0], i_mcp[1], 0.0),
        (i_pip[0], i_pip[1], z_curl * 0.3),
        (i_dip[0], i_dip[1], z_curl * 0.6),
        (i_tip[0], i_tip[1], z_curl * 1.0),
        (m_mcp[0], m_mcp[1], 0.0),
        (m_pip[0], m_pip[1], z_curl * 0.3),
        (m_dip[0], m_dip[1], z_curl * 0.6),
        (m_tip[0], m_tip[1], z_curl * 1.0),
        (r_mcp[0], r_mcp[1], 0.0),
        (r_pip[0], r_pip[1], z_curl * 0.3),
        (r_dip[0], r_dip[1], z_curl * 0.6),
        (r_tip[0], r_tip[1], z_curl * 1.0),
        (p_mcp[0], p_mcp[1], 0.0),
        (p_pip[0], p_pip[1], z_curl * 0.3),
        (p_dip[0], p_dip[1], z_curl * 0.6),
        (p_tip[0], p_tip[1], z_curl * 1.0),
    ]

    landmarks = []
    for dx, dy, dz in raw_points:
        rx, ry = rot(dx, dy)
        landmarks.append(
            {
                "x": round(clamp(palm_x + rx), 4),
                "y": round(clamp(palm_y + ry), 4),
                "z": round(palm_z + dz, 4),
            }
        )
    return landmarks


# -----------------------------------------------------------------------------
# MediaPipe Pose Model Geometry (33 Points)
# -----------------------------------------------------------------------------
def generate_pose_landmarks(
    lean_angle_deg: float = 0.0,
    center_x: float = 0.50,
    center_y: float = 0.40,
    body_scale: float = 0.35,
) -> List[Dict[str, float]]:
    """
    Generates 33 MediaPipe pose landmarks.
    lean_angle_deg: Lateral lean (positive = lean right, negative = lean left).
    """
    rad = math.radians(lean_angle_deg)
    sin_a = math.sin(rad)
    cos_a = math.cos(rad)

    def tilt(bx: float, by: float) -> Tuple[float, float]:
        # Rotation around mid-hip base (center_x, center_y + body_scale * 0.6)
        base_y = center_y + body_scale * 0.6
        rel_x = bx - center_x
        rel_y = by - base_y
        tx = rel_x * cos_a - rel_y * sin_a + center_x
        ty = rel_x * sin_a + rel_y * cos_a + base_y
        return tx, ty

    # Head and face
    nose = tilt(center_x, center_y - body_scale * 0.45)
    l_eye_in = tilt(center_x - 0.015, center_y - body_scale * 0.48)
    l_eye = tilt(center_x - 0.025, center_y - body_scale * 0.48)
    l_eye_out = tilt(center_x - 0.035, center_y - body_scale * 0.48)
    r_eye_in = tilt(center_x + 0.015, center_y - body_scale * 0.48)
    r_eye = tilt(center_x + 0.025, center_y - body_scale * 0.48)
    r_eye_out = tilt(center_x + 0.035, center_y - body_scale * 0.48)
    l_ear = tilt(center_x - 0.060, center_y - body_scale * 0.45)
    r_ear = tilt(center_x + 0.060, center_y - body_scale * 0.45)
    m_left = tilt(center_x - 0.020, center_y - body_scale * 0.40)
    m_right = tilt(center_x + 0.020, center_y - body_scale * 0.40)

    # Shoulders
    l_sh = tilt(center_x - 0.16 * body_scale / 0.35, center_y - body_scale * 0.20)
    r_sh = tilt(center_x + 0.16 * body_scale / 0.35, center_y - body_scale * 0.20)

    # Arms
    l_elb = tilt(center_x - 0.22 * body_scale / 0.35, center_y + body_scale * 0.05)
    r_elb = tilt(center_x + 0.22 * body_scale / 0.35, center_y + body_scale * 0.05)
    l_wri = tilt(center_x - 0.24 * body_scale / 0.35, center_y + body_scale * 0.30)
    r_wri = tilt(center_x + 0.24 * body_scale / 0.35, center_y + body_scale * 0.30)
    l_pnk = tilt(center_x - 0.25 * body_scale / 0.35, center_y + body_scale * 0.35)
    r_pnk = tilt(center_x + 0.25 * body_scale / 0.35, center_y + body_scale * 0.35)
    l_idx = tilt(center_x - 0.24 * body_scale / 0.35, center_y + body_scale * 0.36)
    r_idx = tilt(center_x + 0.24 * body_scale / 0.35, center_y + body_scale * 0.36)
    l_thb = tilt(center_x - 0.23 * body_scale / 0.35, center_y + body_scale * 0.33)
    r_thb = tilt(center_x + 0.23 * body_scale / 0.35, center_y + body_scale * 0.33)

    # Hips (relatively fixed pivot base)
    l_hip = (center_x - 0.10, center_y + body_scale * 0.50)
    r_hip = (center_x + 0.10, center_y + body_scale * 0.50)

    # Legs (seated / torso view)
    l_knee = (center_x - 0.12, center_y + body_scale * 0.85)
    r_knee = (center_x + 0.12, center_y + body_scale * 0.85)
    l_ank = (center_x - 0.12, center_y + body_scale * 1.15)
    r_ank = (center_x + 0.12, center_y + body_scale * 1.15)
    l_heel = (center_x - 0.12, center_y + body_scale * 1.18)
    r_heel = (center_x + 0.12, center_y + body_scale * 1.18)
    l_foot = (center_x - 0.12, center_y + body_scale * 1.22)
    r_foot = (center_x + 0.12, center_y + body_scale * 1.22)

    raw_pose = [
        nose,
        l_eye_in,
        l_eye,
        l_eye_out,
        r_eye_in,
        r_eye,
        r_eye_out,
        l_ear,
        r_ear,
        m_left,
        m_right,
        l_sh,
        r_sh,
        l_elb,
        r_elb,
        l_wri,
        r_wri,
        l_pnk,
        r_pnk,
        l_idx,
        r_idx,
        l_thb,
        r_thb,
        l_hip,
        r_hip,
        l_knee,
        r_knee,
        l_ank,
        r_ank,
        l_heel,
        r_heel,
        l_foot,
        r_foot,
    ]

    landmarks = []
    for px, py in raw_pose:
        landmarks.append(
            {
                "x": round(clamp(px), 4),
                "y": round(clamp(py), 4),
                "z": round(0.0, 4),
            }
        )
    return landmarks


# -----------------------------------------------------------------------------
# Sequence Generators
# -----------------------------------------------------------------------------
def generate_swipe_sequence(frame_count: int = 30) -> List[Dict[str, Any]]:
    """
    Simulates a horizontal left swipe gesture:
    Hand starts at right (x ~ 0.78), moves smoothly and quickly left to (x ~ 0.22).
    """
    frames = []
    start_x, end_x = 0.78, 0.22
    y_pos = 0.52

    for i in range(frame_count):
        t = BASE_TIMESTAMP_NANOS + i * FRAME_INTERVAL_NANOS
        progress = i / max(1, frame_count - 1)
        # S-curve (smoothstep) for realistic arm acceleration and deceleration
        s_curve = progress * progress * (3.0 - 2.0 * progress)
        curr_x = start_x + (end_x - start_x) * s_curve
        curr_y = y_pos + 0.03 * math.sin(progress * math.pi)

        lm = generate_hand_landmarks(curr_x, curr_y, side="RIGHT", curl=0.1)
        frames.append(
            {
                "t": t,
                "tracker": "HAND_SINGLE",
                "hands": [
                    {
                        "side": "RIGHT",
                        "score": round(0.96 + 0.03 * math.cos(progress), 3),
                        "lm": lm,
                    }
                ],
            }
        )
    return frames


def generate_slash_sequence(frame_count: int = 30) -> List[Dict[str, Any]]:
    """
    Simulates a swift diagonal slash gesture (top-right to bottom-left).
    """
    frames = []
    start_x, start_y = 0.82, 0.20
    end_x, end_y = 0.18, 0.80

    for i in range(frame_count):
        t = BASE_TIMESTAMP_NANOS + i * FRAME_INTERVAL_NANOS
        progress = i / max(1, frame_count - 1)
        # Fast ease-in-out
        curve = progress * progress * (3.0 - 2.0 * progress)
        curr_x = start_x + (end_x - start_x) * curve
        curr_y = start_y + (end_y - start_y) * curve
        # Slight tilt matching the slash angle (~ -45 deg)
        rot_angle = -40.0 * (1.0 - progress)

        lm = generate_hand_landmarks(
            curr_x, curr_y, rotation_deg=rot_angle, side="RIGHT", curl=0.05
        )
        frames.append(
            {
                "t": t,
                "tracker": "HAND_SINGLE",
                "hands": [
                    {
                        "side": "RIGHT",
                        "score": round(0.97 - 0.02 * abs(progress - 0.5), 3),
                        "lm": lm,
                    }
                ],
            }
        )
    return frames


def generate_wheel_sequence(frame_count: int = 60) -> List[Dict[str, Any]]:
    """
    Simulates dual hands steering a virtual wheel:
    Starts neutral (0°), steers clockwise (+35°), returns to neutral,
    steers counter-clockwise (-35°), returns to neutral.
    """
    frames = []
    center_x, center_y = 0.50, 0.58
    wheel_radius = 0.22

    for i in range(frame_count):
        t = BASE_TIMESTAMP_NANOS + i * FRAME_INTERVAL_NANOS
        progress = i / max(1, frame_count - 1)

        # Full steering cycle: neutral -> turn right (+35°) -> neutral -> turn left (-35°) -> neutral
        steer_angle_deg = 35.0 * math.sin(2.0 * math.pi * progress)
        angle_rad = math.radians(steer_angle_deg)

        # Left hand (180 deg base position + steering delta)
        left_angle = math.pi + angle_rad
        left_x = center_x + wheel_radius * math.cos(left_angle)
        left_y = center_y + wheel_radius * math.sin(left_angle)

        # Right hand (0 deg base position + steering delta)
        right_angle = 0.0 + angle_rad
        right_x = center_x + wheel_radius * math.cos(right_angle)
        right_y = center_y + wheel_radius * math.sin(right_angle)

        lm_left = generate_hand_landmarks(
            left_x,
            left_y,
            rotation_deg=steer_angle_deg,
            side="LEFT",
            curl=0.45,
        )
        lm_right = generate_hand_landmarks(
            right_x,
            right_y,
            rotation_deg=steer_angle_deg,
            side="RIGHT",
            curl=0.45,
        )

        frames.append(
            {
                "t": t,
                "tracker": "HAND_DUAL_WHEEL",
                "hands": [
                    {"side": "LEFT", "score": 0.98, "lm": lm_left},
                    {"side": "RIGHT", "score": 0.98, "lm": lm_right},
                ],
            }
        )
    return frames


def generate_lean_sequence(frame_count: int = 60) -> List[Dict[str, Any]]:
    """
    Simulates upper-body pose leaning left, returning center, and leaning right.
    """
    frames = []
    for i in range(frame_count):
        t = BASE_TIMESTAMP_NANOS + i * FRAME_INTERVAL_NANOS
        progress = i / max(1, frame_count - 1)

        # Tilt cycle: 0 -> -16 deg (lean left) -> 0 -> +16 deg (lean right) -> 0
        lean_angle = 16.0 * math.sin(2.0 * math.pi * progress)
        lm = generate_pose_landmarks(lean_angle_deg=lean_angle)

        frames.append(
            {
                "t": t,
                "tracker": "POSE_LITE",
                "score": round(0.96 + 0.02 * math.cos(progress * math.pi), 3),
                "lm": lm,
            }
        )
    return frames


def generate_fist_sequence(frame_count: int = 50) -> List[Dict[str, Any]]:
    """
    Simulates a hand closing into a tight fist, holding for ~1 sec, then releasing.
    """
    frames = []
    palm_x, palm_y = 0.50, 0.54

    for i in range(frame_count):
        t = BASE_TIMESTAMP_NANOS + i * FRAME_INTERVAL_NANOS

        # Timeline:
        # Frames 0..9 (10 frames): Open palm (curl = 0.0)
        # Frames 10..14 (5 frames): Transition to fist
        # Frames 15..39 (25 frames): Closed fist hold (curl = 1.0, ~0.83 sec hold)
        # Frames 40..44 (5 frames): Transition to open
        # Frames 45..49 (5 frames): Open palm
        if i < 10:
            curl = 0.0
        elif i < 15:
            curl = (i - 10) / 5.0
        elif i < 40:
            curl = 1.0
        elif i < 45:
            curl = 1.0 - (i - 40) / 5.0
        else:
            curl = 0.0

        lm = generate_hand_landmarks(palm_x, palm_y, side="RIGHT", curl=curl)
        frames.append(
            {
                "t": t,
                "tracker": "HAND_SINGLE",
                "hands": [{"side": "RIGHT", "score": 0.99, "lm": lm}],
            }
        )
    return frames


# -----------------------------------------------------------------------------
# Validation Routine
# -----------------------------------------------------------------------------
def validate_sequence(frames: List[Dict[str, Any]], sequence_name: str) -> None:
    """
    Validates that a generated sequence adheres strictly to the frozen contract.
    Throws ValueError on any contract discrepancy.
    """
    if not frames:
        raise ValueError(
            f"[{sequence_name}] Sequence is empty; at least 1 frame required."
        )

    prev_t = -1
    for idx, frame in enumerate(frames):
        # 1. Timestamp validation
        if "t" not in frame or not isinstance(frame["t"], int):
            raise ValueError(
                f"[{sequence_name}] Frame {idx}: Missing or non-integer timestamp 't'."
            )
        if frame["t"] <= prev_t:
            raise ValueError(
                f"[{sequence_name}] Frame {idx}: Non-monotonic timestamp (prev={prev_t}, curr={frame['t']})."
            )
        prev_t = frame["t"]

        # 2. Tracker validation
        tracker = frame.get("tracker")
        if tracker not in {"HAND_SINGLE", "HAND_DUAL_WHEEL", "POSE_LITE"}:
            raise ValueError(
                f"[{sequence_name}] Frame {idx}: Invalid tracker identifier '{tracker}'."
            )

        # 3. Hand payload validation
        if tracker in {"HAND_SINGLE", "HAND_DUAL_WHEEL"}:
            hands = frame.get("hands")
            if not isinstance(hands, list) or len(hands) == 0:
                raise ValueError(
                    f"[{sequence_name}] Frame {idx}: Hand tracker missing 'hands' list."
                )
            if tracker == "HAND_SINGLE" and len(hands) != 1:
                raise ValueError(
                    f"[{sequence_name}] Frame {idx}: HAND_SINGLE must have exactly 1 hand, got {len(hands)}."
                )
            if tracker == "HAND_DUAL_WHEEL" and len(hands) != 2:
                raise ValueError(
                    f"[{sequence_name}] Frame {idx}: HAND_DUAL_WHEEL must have exactly 2 hands, got {len(hands)}."
                )

            for h_idx, hand in enumerate(hands):
                side = hand.get("side")
                if side not in {"LEFT", "RIGHT", "UNKNOWN"}:
                    raise ValueError(
                        f"[{sequence_name}] Frame {idx} Hand {h_idx}: Invalid side '{side}'."
                    )
                score = hand.get("score")
                if (
                    not isinstance(score, (int, float))
                    or math.isnan(score)
                    or math.isinf(score)
                ):
                    raise ValueError(
                        f"[{sequence_name}] Frame {idx} Hand {h_idx}: Invalid score {score}."
                    )
                lm = hand.get("lm")
                if not isinstance(lm, list) or len(lm) != 21:
                    raise ValueError(
                        f"[{sequence_name}] Frame {idx} Hand {h_idx}: Hand 'lm' must contain exactly 21 points, got {len(lm) if isinstance(lm, list) else None}."
                    )
                for pt_idx, pt in enumerate(lm):
                    for coord in ("x", "y", "z"):
                        val = pt.get(coord)
                        if (
                            not isinstance(val, (int, float))
                            or math.isnan(val)
                            or math.isinf(val)
                        ):
                            raise ValueError(
                                f"[{sequence_name}] Frame {idx} Hand {h_idx} Point {pt_idx}: Point missing or non-finite {coord} ({val})."
                            )
                    if not (0.0 <= pt["x"] <= 1.0 and 0.0 <= pt["y"] <= 1.0):
                        raise ValueError(
                            f"[{sequence_name}] Frame {idx} Hand {h_idx} Point {pt_idx}: Coordinates out of [0, 1] range (x={pt['x']}, y={pt['y']})."
                        )

        # 4. Pose payload validation
        elif tracker == "POSE_LITE":
            score = frame.get("score")
            if (
                not isinstance(score, (int, float))
                or math.isnan(score)
                or math.isinf(score)
            ):
                raise ValueError(
                    f"[{sequence_name}] Frame {idx}: Invalid pose score {score}."
                )
            lm = frame.get("lm")
            if not isinstance(lm, list) or len(lm) != 33:
                raise ValueError(
                    f"[{sequence_name}] Frame {idx}: Pose 'lm' must contain exactly 33 points, got {len(lm) if isinstance(lm, list) else None}."
                )
            for pt_idx, pt in enumerate(lm):
                for coord in ("x", "y", "z"):
                    val = pt.get(coord)
                    if (
                        not isinstance(val, (int, float))
                        or math.isnan(val)
                        or math.isinf(val)
                    ):
                        raise ValueError(
                            f"[{sequence_name}] Frame {idx} Pose Point {pt_idx}: Point missing or non-finite {coord} ({val})."
                        )
                if not (0.0 <= pt["x"] <= 1.0 and 0.0 <= pt["y"] <= 1.0):
                    raise ValueError(
                        f"[{sequence_name}] Frame {idx} Pose Point {pt_idx}: Coordinates out of [0, 1] range (x={pt['x']}, y={pt['y']})."
                    )


# -----------------------------------------------------------------------------
# Main CLI & Export Entry Point
# -----------------------------------------------------------------------------
GENERATORS = {
    "swipe": (generate_swipe_sequence, 30),
    "slash": (generate_slash_sequence, 30),
    "wheel": (generate_wheel_sequence, 60),
    "lean": (generate_lean_sequence, 60),
    "fist": (generate_fist_sequence, 50),
}


def write_jsonl(frames: List[Dict[str, Any]], out_path: Path) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    with open(out_path, "w", encoding="utf-8") as f:
        for frame in frames:
            f.write(json.dumps(frame, separators=(",", ":")) + "\n")


def main():
    parser = argparse.ArgumentParser(
        description="Zorbi Synthetic Landmark Generator (P4-3)"
    )
    parser.add_argument(
        "--sequence",
        choices=["swipe", "slash", "wheel", "lean", "fist", "all"],
        default="all",
        help="Gesture sequence to generate (default: all)",
    )
    parser.add_argument(
        "--out-dir",
        type=Path,
        default=Path("fixtures"),
        help="Target directory for generated .jsonl fixtures (default: fixtures)",
    )
    parser.add_argument(
        "--seed",
        type=int,
        default=42,
        help="Random seed for deterministic generation (default: 42)",
    )
    parser.add_argument(
        "--frames",
        type=int,
        default=None,
        help="Optional override for number of frames per sequence",
    )
    args = parser.parse_args()

    random.seed(args.seed)

    sequences_to_gen = (
        list(GENERATORS.keys()) if args.sequence == "all" else [args.sequence]
    )

    print(f"Generating synthetic landmark fixtures (seed={args.seed})...")
    for seq_name in sequences_to_gen:
        gen_func, default_frames = GENERATORS[seq_name]
        frame_count = args.frames if args.frames is not None else default_frames

        frames = gen_func(frame_count=frame_count)
        validate_sequence(frames, seq_name)

        target_file = args.out_dir / f"{seq_name}.jsonl"
        write_jsonl(frames, target_file)
        print(f"  [OK] {target_file} ({len(frames)} frames generated and validated)")

    print("All sequences generated and validated successfully.")


if __name__ == "__main__":
    main()
