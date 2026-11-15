//! Float maths with no libm and no std: everything the renderer needs,
//! written against `core` only so the crate links on bare metal.

pub const PI: f32 = 3.141_592_7;
pub const TAU: f32 = 6.283_185_5;
pub const RAD: f32 = PI / 180.0;

#[inline]
pub fn abs(x: f32) -> f32 {
    if x < 0.0 { -x } else { x }
}
#[inline]
pub fn mn(a: f32, b: f32) -> f32 {
    if a < b { a } else { b }
}
#[inline]
pub fn mx(a: f32, b: f32) -> f32 {
    if a > b { a } else { b }
}
#[inline]
pub fn clamp(v: f32, lo: f32, hi: f32) -> f32 {
    if v < lo { lo } else if v > hi { hi } else { v }
}
/// Clamp to 0..1.
#[inline]
pub fn sat(v: f32) -> f32 {
    clamp(v, 0.0, 1.0)
}
#[inline]
pub fn lerp(a: f32, b: f32, t: f32) -> f32 {
    a + (b - a) * t
}
/// Largest integer not above `x`. Valid for |x| < 2^31, far beyond anything here.
#[inline]
pub fn floor(x: f32) -> f32 {
    let i = x as i32 as f32;
    if i > x { i - 1.0 } else { i }
}
#[inline]
pub fn round(x: f32) -> f32 {
    floor(x + 0.5)
}
#[inline]
pub fn fract(x: f32) -> f32 {
    x - floor(x)
}
/// Always-positive remainder, so periodic patterns do not flip below zero.
#[inline]
pub fn rem(a: f32, b: f32) -> f32 {
    a - b * floor(a / b)
}

/// Square root: a bit-level first guess refined by two Newton steps (~1e-6 relative).
#[inline]
pub fn sqrt(x: f32) -> f32 {
    if !(x > 0.0) {
        return 0.0;
    }
    let mut y = f32::from_bits((x.to_bits() >> 1) + 0x1fbd_1df5);
    y = 0.5 * (y + x / y);
    y = 0.5 * (y + x / y);
    0.5 * (y + x / y)
}
#[inline]
pub fn hypot(x: f32, y: f32) -> f32 {
    sqrt(x * x + y * y)
}

/// Sine: fold into [-pi/2, pi/2], then a ninth-order series (error below 4e-6).
pub fn sin(x: f32) -> f32 {
    let mut x = x - TAU * round(x / TAU);
    if x > PI / 2.0 {
        x = PI - x;
    } else if x < -PI / 2.0 {
        x = -PI - x;
    }
    let s = x * x;
    x * (1.0 + s * (-1.0 / 6.0 + s * (1.0 / 120.0 + s * (-1.0 / 5040.0 + s * (1.0 / 362_880.0)))))
}
#[inline]
pub fn cos(x: f32) -> f32 {
    sin(x + PI / 2.0)
}

/// Four-quadrant arctangent, polynomial fit (error about 1e-4 rad).
pub fn atan2(y: f32, x: f32) -> f32 {
    let ax = abs(x);
    let ay = abs(y);
    let hi = mx(ax, ay);
    if hi == 0.0 {
        return 0.0;
    }
    let a = mn(ax, ay) / hi;
    let s = a * a;
    let mut r = ((-0.046_496_475 * s + 0.159_314_22) * s - 0.327_622_76) * s * a + a;
    if ay > ax {
        r = PI / 2.0 - r;
    }
    if x < 0.0 {
        r = PI - r;
    }
    if y < 0.0 { -r } else { r }
}

/// e^(-x) for x >= 0, by repeated squaring. Decorative accuracy only (a few percent).
pub fn exp_neg(x: f32) -> f32 {
    if x >= 32.0 {
        return 0.0;
    }
    let mut y = 1.0 - mx(x, 0.0) / 32.0;
    y *= y;
    y *= y;
    y *= y;
    y *= y;
    y * y
}

/// One full sine cycle every `period` seconds.
#[inline]
pub fn wave(t: f32, period: f32, phase: f32) -> f32 {
    sin((t / period + phase) * TAU)
}

/// Stateless pseudo-random value in 0..1 for a given input.
pub fn hash(n: f32) -> f32 {
    let mut h = (n * 1000.0) as i32 as u32;
    h ^= h >> 16;
    h = h.wrapping_mul(0x7feb_352d);
    h ^= h >> 15;
    h = h.wrapping_mul(0x846c_a68b);
    h ^= h >> 16;
    (h >> 8) as f32 / 16_777_216.0
}

pub fn ease_io(x: f32) -> f32 {
    let x = sat(x);
    if x < 0.5 {
        4.0 * x * x * x
    } else {
        let u = -2.0 * x + 2.0;
        1.0 - u * u * u / 2.0
    }
}
pub fn ease_out5(x: f32) -> f32 {
    let u = 1.0 - sat(x);
    let u2 = u * u;
    1.0 - u2 * u2 * u
}
/// State-transition curve: a small anticipation dip, then travel with a soft overshoot.
pub fn settle(x: f32) -> f32 {
    let x = sat(x);
    let ant = 0.14;
    if x < ant {
        return -0.04 * sin(x / ant * PI);
    }
    let d = (x - ant) / (1.0 - ant) - 1.0;
    1.0 + 1.9 * d * d * d + 0.9 * d * d
}
