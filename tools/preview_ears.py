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


def ears(points, shapes, pivots, width, sink, splay, perk):
    """The Kotlin frame math, line for line."""
    n = len(points)
    crown = points[n * 3 // 4]
    length = max(math.hypot(*crown), 1e-3)
    ux, uy = crown[0] / length, crown[1] / length
    e1x, e1y = -uy * width, ux * width
    e2x, e2y = ux * width, uy * width

    out = []
    for side, shape, pivot in ((-1, shapes[0], pivots[0]), (1, shapes[1], pivots[1])):
        rad = math.radians(-side * splay)
        c, sn = math.cos(rad), math.sin(rad)
        ox = crown[0] - ux * sink + e1x * pivot * (1 - c) - e2x * pivot * sn
        oy = crown[1] - uy * sink + e1y * pivot * (1 - c) - e2y * pivot * sn
        xx, xy = e1x * c + e2x * sn, e1y * c + e2y * sn
        yx, yy = (-e1x * sn + e2x * c) * perk, (-e1y * sn + e2y * c) * perk
        frame = (ox, oy, xx, xy, yx, yy)
        mapped = [(ox + xx * px + yx * py, oy + xy * px + yy * py) for px, py in shape]
        out.append((mapped, frame))
    return out


def cat_fit(width, sink):
    """MaterialBot.CAT_FIT: shrink so the tallest tip reaches the plain crown."""
    return 1 / (1 - sink + TIP_Y * width * PERK_MAX * TIP_DAMP)


def check_fit(width, sink):
    """The tips must land inside the earless bot's footprint, at any pose."""
    plain = RADIUS_FRACTION            # earless crown, in canvas units
    r = RADIUS_FRACTION * cat_fit(width, sink)
    for perk in (0.76, 1.0, PERK_MAX):
        reach = r * (1 - sink + TIP_Y * width * perk * TIP_DAMP)
        assert reach <= plain + 1e-6, f"perk {perk}: tips reach {reach} > {plain}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--width", type=float, default=0.74, help="EAR_WIDTH, in head radii")
    ap.add_argument("--sink", type=float, default=0.45, help="EAR_SINK, in head radii")
    ap.add_argument("--splay", type=float, default=0.0, help="pose rotation, degrees")
    ap.add_argument("--perk", type=float, default=1.0, help="pose length multiplier")
    ap.add_argument("--shape", help="read the points from this .kt instead of EarShape.kt")
    ap.add_argument("--outline", action="store_true", help="line art, as MascotFinish.OUTLINE draws")
    ap.add_argument("--size", type=int, default=420)
    ap.add_argument("--out", default="/tmp/ears_preview.png")
    args = ap.parse_args()

    shapes = (load("EAR_LEFT", args.shape), load("EAR_RIGHT", args.shape))
    pivots = (load_const("EAR_LEFT_PIVOT", args.shape), load_const("EAR_RIGHT_PIVOT", args.shape))

    check_fit(args.width, args.sink)

    size = args.size
    bg = "#111114"
    # The bot overflows its layout box by design (README: ball = 1.27 x size),
    # so the preview canvas is roomier than the box it draws.
    canvas = int(size * 1.6)
    img = Image.new("RGB", (canvas, canvas), bg)
    d = ImageDraw.Draw(img)
    cx = cy = canvas / 2
    # What the app does: cat mode draws the bot at CAT_FIT so the tips land
    # where the earless crown would.
    draw_px = size * cat_fit(args.width, args.sink)
    r = draw_px * RADIUS_FRACTION
    fill = "#F2F0E9"

    pts = body_points(draw_px)
    drawn = ears(
        pts, shapes, pivots, r * args.width, r * args.sink,
        args.splay, args.perk * TIP_DAMP,
    )
    # The footprint the host padded for: the earless ball at the full size.
    plain = size * RADIUS_FRACTION
    d.ellipse([cx - plain, cy - plain, cx + plain, cy + plain], outline="#3A3A44", width=1)
    d.rectangle([cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2], outline="#2A2A32", width=1)

    # Ears first, body over the bases: the same order MaterialBot draws in.
    if args.outline:
        for ear, _ in drawn:
            d.polygon([(cx + x, cy + y) for x, y in ear], outline=fill, width=5)
        # Stand-in for clipPath(body, Difference): erase what fell inside.
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=bg)
        d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=fill, width=5)
    else:
        for ear, _ in drawn:
            d.polygon([(cx + x, cy + y) for x, y in ear], fill=fill)
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=fill)
        eye = r * 0.22
        for sx in (-1, 1):
            ex, ey = cx + sx * r * 0.30, cy + r * 0.10
            d.ellipse([ex - eye, ey - eye, ex + eye, ey + eye], fill="#0A0A0C")

    img.save(args.out)
    print(args.out)


if __name__ == "__main__":
    main()
