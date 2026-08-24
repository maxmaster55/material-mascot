#!/usr/bin/env python3
"""
Render the traced ears on a mock bot head, without building the app.

Mirrors MaterialBot.drawCatEars: the same crown anchor, pair frame, pivot
rotation and perk, reading the same EarShape.kt the app ships. Iterate here in
a second, build once when it looks right.

It also asserts the thing you cannot eyeball: that CAT_FIT keeps the tips
inside the footprint the earless bot occupies, at the tallest pose.

Usage:  python3 preview_ears.py [--splay 0] [--perk 1] [--width 0.74] [--sink 0.45]
"""
import argparse
import math
import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw

SHAPE = Path(__file__).resolve().parents[1] / (
    "mascot/src/main/java/com/maxmaster/materialmascot/engine/EarShape.kt"
)
SAMPLES = 64                 # PROFILE_SAMPLES
TIP_Y = 1.0673               # EAR_TIP_Y
RADIUS_FRACTION = 100 / 158  # BotEngine.RADIUS_FRACTION
TIP_DAMP = 0.9               # EAR_TIP_DAMP
PERK_MAX = 1.20              # EAR_PERK_MAX


def load(name, path=None):
    src = Path(path).read_text() if path else SHAPE.read_text()
    block = src.split(f"val {name}", 1)[1].split("arrayOf(", 1)[1].split(")\n", 1)[0]
    pts = re.findall(r"Offset\(([-+0-9.]+)f,\s*([-+0-9.]+)f\)", block)
    if not pts:
        sys.exit(f"no points found for {name}")
    return [(float(x), float(y)) for x, y in pts]


def load_const(name, path=None):
    src = Path(path).read_text() if path else SHAPE.read_text()
    m = re.search(rf"{name}: Float = ([-+0-9.]+)f", src)
    if not m:
        sys.exit(f"no constant {name}")
    return float(m.group(1))


def body_points(size):
    """A resting ball, sampled the way BotShape.toPoints does."""
    r = size * RADIUS_FRACTION
    return [
        (math.cos(i / SAMPLES * math.tau) * r, math.sin(i / SAMPLES * math.tau) * r)
        for i in range(SAMPLES)
    ]


def base_range(shape):
    """EarShape.baseRange: the stretch drawn to sit on the head."""
    outer = min(range(len(shape)), key=lambda i: shape[i][1])
    inner = min(range(len(shape)), key=lambda i: abs(shape[i][0]))
    return range(min(outer, inner), max(outer, inner) + 1)


def map_ear(points, shape, pivot, width, sink, splay, perk, flare):
    """MaterialBot.catSilhouette frame math, line for line."""
    n = len(points)
    crown = points[n * 3 // 4]
    length = max(math.hypot(*crown), 1e-3)
    ux, uy = crown[0] / length, crown[1] / length
    e1x, e1y = -uy * width, ux * width
    e2x, e2y = ux * width, uy * width
    rad = math.radians(splay)
    c, sn = math.cos(rad), math.sin(rad)
    ox = crown[0] - ux * sink + e1x * pivot * (1 - c) - e2x * pivot * sn
    oy = crown[1] - uy * sink + e1y * pivot * (1 - c) - e2y * pivot * sn
    xx, xy = e1x * c + e2x * sn, e1y * c + e2y * sn
    yx, yy = (-e1x * sn + e2x * c) * perk, (-e1y * sn + e2y * c) * perk
    out = []
    for px, py in shape:
        px = px * (1 + flare * (1 - min(max(py / TIP_Y, 0.0), 1.0)))
        out.append((ox + xx * px + yx * py, oy + xy * px + yy * py))
    return out


def outline_radius(points, dx, dy):
    best, best_dot = 0.0, -1e30
    for x, y in points:
        d = math.hypot(x, y)
        if d > 1e-3:
            dot = (x * dx + y * dy) / d
            if dot > best_dot:
                best_dot, best = dot, d
    return best


def inside(points, p):
    d = math.hypot(*p)
    return True if d < 1e-4 else d <= outline_radius(points, p[0] / d, p[1] / d)


def crossing(points, a, b):
    lo, hi = 0.0, 1.0
    for _ in range(12):
        t = (lo + hi) / 2
        q = (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
        if inside(points, q):
            lo = t
        else:
            hi = t
    t = (lo + hi) / 2
    return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)


def angle_of(p):
    a = math.atan2(p[1], p[0])
    return a + math.tau if a < 0 else a


def arc(frm, to):
    d = to - frm
    return d + math.tau if d < 0 else d


def silhouette(points, shapes, pivots, width, sink, splay, perk, flare):
    """The body outline with the ears spliced into it — one closed loop."""
    runs = []
    for side, shape, pivot in ((-1, shapes[0], pivots[0]), (1, shapes[1], pivots[1])):
        ear = map_ear(points, shape, pivot, width, sink, -side * splay, perk, flare)
        m = len(ear)
        first = next((i + 1) % m for i in range(m)
                     if inside(points, ear[i]) and not inside(points, ear[(i + 1) % m]))
        if first is None:
            continue
        count = 0
        while count < m and not inside(points, ear[(first + count) % m]):
            count += 1
        enter = crossing(points, ear[(first - 1) % m], ear[first])
        leave = crossing(points, ear[(first + count - 1) % m], ear[(first + count) % m])
        forward = arc(angle_of(enter), angle_of(leave)) < math.pi
        run = [ear[(first + k) % m] for k in range(count)]
        if not forward:
            run.reverse()
            enter, leave = leave, enter
        runs.append((angle_of(enter), angle_of(leave), [enter] + run + [leave]))

    out, done = [], [False] * len(runs)
    n = len(points)
    for i in range(n):
        a, prev = angle_of(points[i]), angle_of(points[i - 1])
        for r, (frm, to, run) in enumerate(runs):
            if not done[r] and arc(prev, frm) <= arc(prev, a):
                out.extend(run)
                done[r] = True
        if not any(arc(frm, a) <= arc(frm, to) for frm, to, _ in runs):
            out.append(points[i])
    for r, (_, _, run) in enumerate(runs):
        if not done[r]:
            out.extend(run)
    return out


def spline(pts, per_seg=8):
    """buildBodyPath: a closed Catmull-Rom through the merged outline."""
    n = len(pts)
    out = []
    for i in range(n):
        p0, p1, p2, p3 = (pts[(i - 1) % n], pts[i], pts[(i + 1) % n], pts[(i + 2) % n])
        c1 = (p1[0] + (p2[0] - p0[0]) / 6, p1[1] + (p2[1] - p0[1]) / 6)
        c2 = (p2[0] - (p3[0] - p1[0]) / 6, p2[1] - (p3[1] - p1[1]) / 6)
        for k in range(per_seg):
            t = k / per_seg
            u = 1 - t
            out.append((
                u**3 * p1[0] + 3 * u * u * t * c1[0] + 3 * u * t * t * c2[0] + t**3 * p2[0],
                u**3 * p1[1] + 3 * u * u * t * c1[1] + 3 * u * t * t * c2[1] + t**3 * p2[1],
            ))
    return out


# MaterialBot.EAR_REACH: how far the eared silhouette stands from the centre,
# in ball radii, worst case over the pose corners.
EAR_REACH = 1.62


def cat_fit():
    return 1 / EAR_REACH


def reach(shapes, pivots, width, sink, flare):
    pts = body_points(1.0)
    r = RADIUS_FRACTION
    worst = 0.0
    for perk in (0.76, 1.0, PERK_MAX):
        for splay in (-12.0, 0.0, 12.0):
            merged = silhouette(pts, shapes, pivots, r * width, r * sink,
                                splay, perk * TIP_DAMP, flare)
            worst = max(worst, max(math.hypot(x, y) for x, y in merged))
    return worst / r


def check_fit(shapes, pivots, width, sink, flare):
    got = reach(shapes, pivots, width, sink, flare)
    assert got <= EAR_REACH + 1e-4, (
        f"ears reach {got:.4f} ball radii > EAR_REACH {EAR_REACH} — "
        f"raise EAR_REACH in MaterialBot.kt and here, or pull the ears in"
    )


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--width", type=float, default=0.74, help="EAR_WIDTH, in head radii")
    ap.add_argument("--sink", type=float, default=0.45, help="EAR_SINK, in head radii")
    ap.add_argument("--splay", type=float, default=0.0, help="pose rotation, degrees")
    ap.add_argument("--perk", type=float, default=1.0, help="pose length multiplier")
    ap.add_argument("--shape", help="read the points from this .kt instead of EarShape.kt")
    ap.add_argument("--flare", type=float, default=0.20, help="EAR_BASE_FLARE")
    ap.add_argument("--outline", action="store_true", help="line art, as MascotFinish.OUTLINE draws")
    ap.add_argument("--reach", action="store_true", help="print the measured reach and exit")
    ap.add_argument("--size", type=int, default=420)
    ap.add_argument("--out", default="/tmp/ears_preview.png")
    args = ap.parse_args()

    shapes = (load("EAR_LEFT", args.shape), load("EAR_RIGHT", args.shape))
    pivots = (load_const("EAR_LEFT_PIVOT", args.shape), load_const("EAR_RIGHT_PIVOT", args.shape))
    if args.reach:
        print(round(reach(shapes, pivots, args.width, args.sink, args.flare), 4))
        return
    check_fit(shapes, pivots, args.width, args.sink, args.flare)

    size = args.size
    bg = "#111114"
    # The bot overflows its layout box by design (README: ball = 1.27 x size),
    # so the preview canvas is roomier than the box it draws.
    canvas = int(size * 1.6)
    img = Image.new("RGB", (canvas, canvas), bg)
    d = ImageDraw.Draw(img)
    cx = cy = canvas / 2
    draw_px = size * cat_fit()
    r = draw_px * RADIUS_FRACTION
    fill = "#F2F0E9"

    pts = body_points(draw_px)
    merged = silhouette(pts, shapes, pivots, r * args.width, r * args.sink,
                        args.splay, args.perk * TIP_DAMP, args.flare)
    poly = [(cx + x, cy + y) for x, y in spline(merged)]

    # The footprint the host padded for: the earless ball at the full size.
    plain = size * RADIUS_FRACTION
    d.ellipse([cx - plain, cy - plain, cx + plain, cy + plain], outline="#3A3A44", width=1)
    d.rectangle([cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2],
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
