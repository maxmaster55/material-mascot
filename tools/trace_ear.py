#!/usr/bin/env python3
"""
Trace an ear reference image into the point arrays MaterialBot draws.

Same method the body silhouettes use: measure the artwork once, ship the
numbers, transform them at draw time — instead of hand-tuning bezier control
points until something looks close.

The ear is normalised into its own base frame, so the output is independent of
the reference's size and rotation:

    origin  = midpoint of the ear's base corners
    +x      = base corner 0 -> corner 1, scaled so the base half-width is 1
    +y      = perpendicular, pointing at the tip

MaterialBot rebuilds that frame from two points on the live body outline, so a
traced ear lands on any silhouette at any size.

Usage:  python3 trace_ear.py ears.png [--samples 40] [--inner-samples 32]
"""
import argparse
import sys

import cv2
import numpy as np


def largest_contours(mask, count):
    contours, _ = cv2.findContours(mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_NONE)
    contours = sorted(contours, key=cv2.contourArea, reverse=True)[:count]
    if len(contours) < count:
        sys.exit(f"found {len(contours)} shapes, expected {count}")
    # Left to right by centroid.
    return sorted(contours, key=lambda c: c[:, 0, 0].mean())


def corners(contour):
    """The three extreme points of a curved triangle: two base, one tip."""
    contour = np.asarray(contour, dtype=np.float32).reshape(-1, 1, 2)
    peri = cv2.arcLength(contour, True)
    for frac in np.arange(0.02, 0.20, 0.005):
        poly = cv2.approxPolyDP(contour, frac * peri, True)[:, 0, :]
        if len(poly) == 3:
            return poly.astype(float)
    sys.exit("could not reduce the ear to three corners; is the image clean?")


def base_frame(poly):
    """Split the corners into (base0, base1, tip) and build the local frame."""
    # The tip is the corner furthest from the midpoint of the other two.
    best = max(
        range(3),
        key=lambda i: np.linalg.norm(
            poly[i] - (poly[(i + 1) % 3] + poly[(i + 2) % 3]) / 2
        ),
    )
    tip = poly[best]
    b0, b1 = poly[(best + 1) % 3], poly[(best + 2) % 3]
    origin = (b0 + b1) / 2
    unit = np.linalg.norm(b1 - b0) / 2
    xhat = (b1 - b0) / (2 * unit)
    yhat = np.array([-xhat[1], xhat[0]])
    # Point +y at the tip, whichever way the perpendicular happened to fall.
    if np.dot(tip - origin, yhat) < 0:
        yhat = -yhat
        xhat = -xhat
    return origin, xhat, yhat, unit


def to_local(points, origin, xhat, yhat, unit):
    v = points - origin
    return np.stack([v @ xhat / unit, v @ yhat / unit], axis=1)


def resample(points, n):
    """Even arc-length resampling of a closed loop, starting where it starts."""
    loop = np.vstack([points, points[:1]])
    seg = np.linalg.norm(np.diff(loop, axis=0), axis=1)
    dist = np.concatenate([[0], np.cumsum(seg)])
    want = np.linspace(0, dist[-1], n, endpoint=False)
    return np.stack(
        [np.interp(want, dist, loop[:, 0]), np.interp(want, dist, loop[:, 1])],
        axis=1,
    )


def rotate_to_start(local):
    """Start the loop at the outer base corner and run toward the tip."""
    start = int(np.argmin(local[:, 0] + local[:, 1]))
    rolled = np.roll(local, -start, axis=0)
    # Walk +x along the base first, so both ears wind the same way.
    if rolled[1][1] > rolled[-1][1]:
        rolled = np.vstack([rolled[:1], rolled[1:][::-1]])
    return rolled


def pair_frame(left, right):
    """
    Normalise BOTH ears together, in the reference's own axes.

    Deriving each ear's rotation from a detected base edge throws away the
    orientation it was drawn at — which is most of what makes an ear an ear
    rather than a horn. So keep the drawing exactly as it is: one frame for the
    pair, x to the right, y up, origin centred on the pair at the height where
    the ears meet the head.

        x = -1 .. +1  spans the full width of the pair
        y = 0         the bottom of the ears
    """
    both = np.vstack([left, right])
    x0, x1 = both[:, 0].min(), both[:, 0].max()
    y1 = both[:, 1].max()          # image y grows down, so this is the bottom
    cx = (x0 + x1) / 2
    unit = (x1 - x0) / 2
    return np.array([cx, y1]), unit


def to_pair_local(points, origin, unit):
    v = points - origin
    return np.stack([v[:, 0] / unit, -v[:, 1] / unit], axis=1)


def pivot_x(pts):
    """X of the ear's base centre: what it pivots about when the mood poses it."""
    floor = pts[:, 1].min()
    base = pts[np.abs(pts[:, 1] - floor) < 0.12]
    return float(base[:, 0].mean()) if len(base) else float(pts[:, 0].mean())


def kotlin(name, pts, doc):
    body = ",\n".join(
        f"    Offset({x:+.4f}f, {y:+.4f}f)" for x, y in pts
    )
    return f"/** {doc} */\ninternal val {name}: Array<Offset> = arrayOf(\n{body}\n)\n"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("image")
    ap.add_argument("--samples", type=int, default=40)
    ap.add_argument("--inner-samples", type=int, default=32)
    ap.add_argument("--keep-tufts", action="store_true",
                    help="trace the inner fill exactly, decorative marks and all")
    ap.add_argument("--preview", help="write an overlay PNG to eyeball the trace")
    ap.add_argument("--pair", action="store_true",
                    help="trace both ears in one frame, keeping the drawn orientation")
    ap.add_argument("--outer-only", action="store_true",
                    help="emit just the silhouette, no inner-ear fill")
    args = ap.parse_args()

    img = cv2.imread(args.image)
    if img is None:
        sys.exit(f"could not read {args.image}")
    b, g, r = (c.astype(int) for c in cv2.split(img))

    # Anything that is not near-white background belongs to an ear.
    ink = ((r < 235) | (g < 235) | (b < 235)).astype(np.uint8) * 255
    ink = cv2.morphologyEx(ink, cv2.MORPH_CLOSE, np.ones((5, 5), np.uint8))
    # The inner fill: clearly warmer than it is bright-neutral, and not the
    # dark outline.
    pink = (((r - g) > 25) & ((r - b) > 10) & (r > 150)).astype(np.uint8) * 255
    pink = cv2.morphologyEx(pink, cv2.MORPH_CLOSE, np.ones((7, 7), np.uint8))

    ear_contours = largest_contours(ink, 2)
    left_c = ear_contours[0][:, 0, :].astype(float)
    right_c = ear_contours[1][:, 0, :].astype(float)

    if args.pair:
        origin, unit = pair_frame(left_c, right_c)
        left = resample(to_pair_local(left_c, origin, unit), args.samples)
        right = resample(to_pair_local(right_c, origin, unit), args.samples)
        print(f"// Traced from {args.image} by tools/trace_ear.py - do not hand-edit.")
        print(f"// Pair frame: x spans -1..+1 across both ears, y = 0 at their base,")
        print(f"// y up. Tips reach {max(left[:, 1].max(), right[:, 1].max()):.3f}.")
        print()
        print(kotlin("EAR_LEFT", left,
                     "Left ear exactly as drawn, in the shared pair frame."))
        print()
        print(kotlin("EAR_RIGHT", right,
                     "Right ear exactly as drawn, in the same frame."))
        print()
        print("/** X of each ear's base centre: the point its pose rotates about. */")
        print(f"internal const val EAR_LEFT_PIVOT: Float = {pivot_x(left):+.4f}f")
        print(f"internal const val EAR_RIGHT_PIVOT: Float = {pivot_x(right):+.4f}f")
        if args.preview:
            over = img.copy()
            for pts, colour in ((left, (0, 160, 255)), (right, (60, 220, 120))):
                back = np.stack([pts[:, 0] * unit + origin[0],
                                 -pts[:, 1] * unit + origin[1]], axis=1).astype(int)
                cv2.polylines(over, [back.reshape(-1, 1, 2)], True, colour, 2)
            cv2.imwrite(args.preview, over)
            print(f"// preview written to {args.preview}", file=sys.stderr)
        return

    outer = left_c
    inner_c = largest_contours(pink, 2)[0]
    if not args.keep_tufts:
        # The reference's interior fur marks bite chunks out of the fill; the
        # hull recovers the plain inner-ear shape underneath them.
        inner_c = cv2.convexHull(inner_c)
    inner = inner_c[:, 0, :].astype(float)

    origin, xhat, yhat, unit = base_frame(corners(outer))
    out_local = rotate_to_start(resample(to_local(outer, origin, xhat, yhat, unit), args.samples))
    in_local = rotate_to_start(resample(to_local(inner, origin, xhat, yhat, unit), args.inner_samples))

    print(f"// Traced from {args.image} by tools/trace_ear.py — do not hand-edit.")
    print(f"// Base half-width = 1; tip reaches y = {out_local[:, 1].max():.3f}.")
    print()
    print(kotlin("EAR_OUTER", out_local,
                 "Ear silhouette, in base-frame units: origin at the base midpoint, "
                 "+x along the base, +y toward the tip."))
    print()
    if not args.outer_only:
        print(kotlin("EAR_INNER", in_local, "Inner-ear fill, in the same base frame."))

    if args.preview:
        over = img.copy()
        shown = [(out_local, (0, 160, 255))]
        if not args.outer_only:
            shown.append((in_local, (255, 0, 160)))
        for pts, colour in shown:
            back = (origin + (pts[:, :1] * xhat + pts[:, 1:] * yhat) * unit).astype(int)
            cv2.polylines(over, [back.reshape(-1, 1, 2)], True, colour, 2)
            for x, y in back:
                cv2.circle(over, (int(x), int(y)), 3, colour, -1)
        cv2.imwrite(args.preview, over)
        print(f"// preview written to {args.preview}", file=sys.stderr)


if __name__ == "__main__":
    main()
