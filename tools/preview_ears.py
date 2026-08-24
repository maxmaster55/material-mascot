#!/usr/bin/env python3
"""
Render the traced ears on a mock bot head, without building the app.

Mirrors MaterialBot.catSilhouette: the same profile table, the same smooth
union, the same fit. Iterate here in a second, build once when it looks right.

It also asserts the thing you cannot eyeball: that CAT_FIT keeps the eared
silhouette inside the footprint the earless bot occupies, at every pose.

Usage:  python3 preview_ears.py [--splay 0] [--perk 1] [--outline] [--reach]
"""
import argparse
import math
import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ENGINE = Path(__file__).resolve().parents[1] / "mascot/src/main/java/com/maxmaster/materialmascot/engine"
SHAPE = ENGINE / "EarShape.kt"
SAMPLES = 64                 # PROFILE_SAMPLES
CAT_SAMPLES = 192            # MaterialBot.CAT_SAMPLES
RADIUS_FRACTION = 100 / 158  # BotEngine.RADIUS_FRACTION
TIP_DAMP = 0.9               # EAR_TIP_DAMP
PERK_MAX = 1.20              # EAR_PERK_MAX
MELT = 0.13                  # EAR_MELT
SPLAY_LIMIT = 12             # EAR_SPLAY_LIMIT
SPREAD = 7                   # EAR_SPREAD


def load_profile():
    src = SHAPE.read_text()
    block = src.split("EAR_PROFILE", 1)[1].split("floatArrayOf(", 1)[1].split(")", 1)[0]
    prof = [float(v) for v in re.findall(r"([-+0-9.]+)f", block)]
    if not prof:
        sys.exit("no EAR_PROFILE found")
    frm = float(re.search(r"EAR_FROM_DEG: Float = ([-+0-9.]+)f", src).group(1))
    step = float(re.search(r"EAR_STEP_DEG: Float = ([-+0-9.]+)f", src).group(1))
    reach = float(re.search(r"EAR_TIP_REACH: Float = ([-+0-9.]+)f", src).group(1))
    return prof, frm, step, reach


PROFILE, FROM_DEG, STEP_DEG, TIP_REACH = load_profile()
EAR_REACH = 1 + (TIP_REACH - 1) * PERK_MAX * TIP_DAMP
CAT_FIT = 1 / EAR_REACH


def ear_profile(deg):
    x = (deg - FROM_DEG) / STEP_DEG
    if x <= 0 or x >= len(PROFILE) - 1:
        return 0.0
    i = int(x)
    return PROFILE[i] + (PROFILE[i + 1] - PROFILE[i]) * (x - i)


def smooth_max(a, b, k):
    if k <= 1e-4:
        return max(a, b)
    h = min(max(0.5 + 0.5 * (a - b) / k, 0.0), 1.0)
    return (b + (a - b) * h) + k * h * (1 - h)


def body_points(size):
    """A resting ball, sampled the way BotShape.toPoints does."""
    r = size * RADIUS_FRACTION
    return [(math.cos(i / SAMPLES * math.tau) * r, math.sin(i / SAMPLES * math.tau) * r)
            for i in range(SAMPLES)]


def body_radius(points, angle):
    dx, dy = math.cos(angle), math.sin(angle)
    best = nxt = 0.0
    best_dot = nxt_dot = -1e30
    for x, y in points:
        d = math.hypot(x, y)
        if d > 1e-3:
            dot = (x * dx + y * dy) / d
            if dot > best_dot:
                best_dot, best, nxt_dot, nxt = dot, d, best_dot, best
            elif dot > nxt_dot:
                nxt_dot, nxt = dot, d
    if nxt_dot <= -1:
        return best
    span = max(best_dot - nxt_dot, 1e-6)
    t = min(max(0.5 - span * 0.5 / max(1 - nxt_dot, 1e-6), 0.0), 0.5)
    return best + (nxt - best) * t


def silhouette(points, ballness, perk, splay):
    """MaterialBot.catSilhouette, line for line."""
    if ballness <= 0.01:
        return points
    n = len(points)
    crown = points[n * 3 // 4]
    up = math.atan2(crown[1], crown[0])
    stand = perk * TIP_DAMP * ballness
    out = []
    for i in range(CAT_SAMPLES):
        a = i / CAT_SAMPLES * math.tau
        body_r = body_radius(points, a)
        off = (a - up + math.pi) % math.tau - math.pi
        pose = min(max(splay, -SPLAY_LIMIT), SPLAY_LIMIT)
        deg = abs(off) * 180 / math.pi - pose - SPREAD
        prof = ear_profile(deg)
        ear_r = 0.0 if prof <= 0 else body_r * (1 + (prof - 1) * stand)
        r = smooth_max(body_r, ear_r, body_r * MELT)
        out.append((math.cos(a) * r, math.sin(a) * r))
    return out


def reach():
    """Worst-case distance from centre to outline, in ball radii."""
    pts = body_points(1.0)
    worst = 0.0
    for perk in (0.76, 1.0, PERK_MAX):
        for splay in (-SPLAY_LIMIT, 0.0, SPLAY_LIMIT):
            merged = silhouette(pts, 1.0, perk, splay)
            worst = max(worst, max(math.hypot(x, y) for x, y in merged))
    return worst / RADIUS_FRACTION


def check_fit():
    got = reach()
    assert got <= EAR_REACH + 1e-3, (
        f"ears reach {got:.4f} ball radii > EAR_REACH {EAR_REACH:.4f} — "
        f"the fit no longer holds"
    )


def check_ballness():
    """Ears belong on a ball: every other silhouette the engine ships must lose
    them, and the resting ball must keep them. Mirrors BotEngine.ballness."""
    src = (ENGINE / "BotShape.kt").read_text()
    kt = (ENGINE / "BotEngine.kt").read_text()
    round_tol = float(re.search(r"BALL_ROUND_TOLERANCE = ([-+0-9.]+)f", kt).group(1))
    size_tol = float(re.search(r"BALL_SIZE_TOLERANCE = ([-+0-9.]+)f", kt).group(1))

    def ballness(r):
        lo, hi, mean = min(r), max(r), sum(r) / len(r)
        if mean < 1e-4:
            return 0.0
        return max(0.0, min(1.0, min(1 - ((hi - lo) / mean) / round_tol,
                                     1 - (1 - min(mean, 1.0)) / size_tol)))

    assert ballness([1.0] * 64) == 1.0, "the resting ball must keep its ears"
    assert ballness([0.97] * 64) > 0.8, "breathing must not shed the ears"
    for m in re.finditer(r"fun (\w+Profile)\(\): FloatArray = floatArrayOf\((.*?)\)", src, re.S):
        name, body = m.group(1), m.group(2)
        v = [float(x) for x in re.findall(r"([-+0-9.]+)f", body)]
        got = ballness(v)
        assert got < 0.25, f"{name} is not a ball but keeps ears (ballness {got:.2f})"


def spline(pts, per_seg=4):
    """buildBodyPath: a closed Catmull-Rom through the outline."""
    n = len(pts)
    out = []
    for i in range(n):
        p0, p1, p2, p3 = pts[(i - 1) % n], pts[i], pts[(i + 1) % n], pts[(i + 2) % n]
        c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        for k in range(per_seg):
            t = k / per_seg
            u = 1 - t
            out.append((
                u**3 * p1[0] + 3*u*u*t * c1[0] + 3*u*t*t * c2[0] + t**3 * p2[0],
                u**3 * p1[1] + 3*u*u*t * c1[1] + 3*u*t*t * c2[1] + t**3 * p2[1],
            ))
    return out


def main():
    global MELT
    ap = argparse.ArgumentParser()
    ap.add_argument("--splay", type=float, default=0.0, help="pose rotation, degrees")
    ap.add_argument("--perk", type=float, default=1.0, help="pose length multiplier")
    ap.add_argument("--ballness", type=float, default=1.0, help="1 = ball, 0 = no ears")
    ap.add_argument("--melt", type=float, default=None, help="EAR_MELT")
    ap.add_argument("--outline", action="store_true", help="line art, as OUTLINE draws")
    ap.add_argument("--reach", action="store_true", help="print the measured reach and exit")
    ap.add_argument("--size", type=int, default=420)
    ap.add_argument("--out", default="/tmp/ears_preview.png")
    args = ap.parse_args()

    if args.melt is not None:
        MELT = args.melt
    if args.reach:
        print(round(reach(), 4))
        return
    check_fit()
    check_ballness()

    size = args.size
    bg = "#111114"
    canvas = int(size * 1.6)
    img = Image.new("RGB", (canvas, canvas), bg)
    d = ImageDraw.Draw(img)
    cx = cy = canvas / 2
    draw_px = size * CAT_FIT
    r = draw_px * RADIUS_FRACTION
    fill = "#F2F0E9"

    pts = body_points(draw_px)
    merged = silhouette(pts, args.ballness, args.perk * 1.0, args.splay)
    poly = [(cx + x, cy + y) for x, y in spline(merged)]

    # The footprint the host padded for: the earless ball at the full size.
    plain = size * RADIUS_FRACTION
    d.ellipse([cx - plain, cy - plain, cx + plain, cy + plain], outline="#3A3A44", width=1)
    d.rectangle([cx - size/2, cy - size/2, cx + size/2, cy + size/2],
                outline="#2A2A32", width=1)

    if args.outline:
        d.polygon(poly, outline=fill, width=5)
    else:
        d.polygon(poly, fill=fill)
        eye = r * 0.22
        for sx in (-1, 1):
            ex, ey = cx + sx * r * 0.30, cy + r * 0.10
            d.ellipse([ex - eye, ey - eye, ex + eye, ey + eye], fill="#0A0A0C")

    img.save(args.out)
    print(args.out)


if __name__ == "__main__":
    main()
