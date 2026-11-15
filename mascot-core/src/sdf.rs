//! Signed distance fields: negative inside, positive outside, in ball-radius units.
//! Every body and every eye is one of these, which is what lets any shape morph
//! into any other by blending two distances.

use crate::math::*;

#[inline]
pub fn circle(x: f32, y: f32, cx: f32, cy: f32, r: f32) -> f32 {
    hypot(x - cx, y - cy) - r
}

/// Box centred on (cx, cy) with half-extents (hw, hh) and corner radius r.
pub fn box_r(x: f32, y: f32, cx: f32, cy: f32, hw: f32, hh: f32, r: f32) -> f32 {
    let dx = abs(x - cx) - hw + r;
    let dy = abs(y - cy) - hh + r;
    hypot(mx(dx, 0.0), mx(dy, 0.0)) + mn(mx(dx, dy), 0.0) - r
}

/// Unsigned distance to the segment a-b.
pub fn seg(x: f32, y: f32, ax: f32, ay: f32, bx: f32, by: f32) -> f32 {
    let (px, py, ex, ey) = (x - ax, y - ay, bx - ax, by - ay);
    let h = sat((px * ex + py * ey) / (ex * ex + ey * ey));
    hypot(px - ex * h, py - ey * h)
}

/// Exact signed distance to a simple polygon.
pub fn poly(x: f32, y: f32, p: &[[f32; 2]]) -> f32 {
    let n = p.len();
    let mut d = (x - p[0][0]) * (x - p[0][0]) + (y - p[0][1]) * (y - p[0][1]);
    let mut s = 1.0;
    let mut j = n - 1;
    for i in 0..n {
        let (ex, ey) = (p[j][0] - p[i][0], p[j][1] - p[i][1]);
        let (wx, wy) = (x - p[i][0], y - p[i][1]);
        let t = sat((wx * ex + wy * ey) / (ex * ex + ey * ey));
        let (bx, by) = (wx - ex * t, wy - ey * t);
        d = mn(d, bx * bx + by * by);
        let c1 = y >= p[i][1];
        let c2 = y < p[j][1];
        let c3 = ex * wy > ey * wx;
        if (c1 && c2 && c3) || (!c1 && !c2 && !c3) {
            s = -s;
        }
        j = i;
    }
    s * sqrt(d)
}

/// Union that rounds the join over a width of k.
#[inline]
pub fn smin(a: f32, b: f32, k: f32) -> f32 {
    let h = mx(k - abs(a - b), 0.0) / k;
    mn(a, b) - h * h * k * 0.25
}

/// Unit heart: tip at the origin, lobes up to y = 1.1, +y is up.
pub fn heart(x: f32, y: f32) -> f32 {
    let x = abs(x);
    if y + x > 1.0 {
        return hypot(x - 0.25, y - 0.75) - 0.353_553_4;
    }
    let a = x * x + (y - 1.0) * (y - 1.0);
    let m = 0.5 * mx(x + y, 0.0);
    let b = (x - m) * (x - m) + (y - m) * (y - m);
    let d = sqrt(mn(a, b));
    if x > y { d } else { -d }
}

/// Convex hull of two circles on a shared axis: radius r1 at the origin,
/// radius r2 at (0, h). Teardrops and tapered bars.
pub fn uneven_capsule(x: f32, y: f32, r1: f32, r2: f32, h: f32) -> f32 {
    let x = abs(x);
    let b = (r1 - r2) / h;
    let a = sqrt(1.0 - b * b);
    let k = -x * b + y * a;
    if k < 0.0 {
        hypot(x, y) - r1
    } else if k > a * h {
        hypot(x, y - h) - r2
    } else {
        x * a + y * b - r1
    }
}
