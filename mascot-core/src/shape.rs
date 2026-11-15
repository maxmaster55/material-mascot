//! The bodies the mascot can take.

use crate::math::*;
use crate::sdf::*;

/// A body silhouette. Each is a distance field, so any two blend into each other.
#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum Shape {
    Ball,
    Cat,
    Egg,
    Hexagon,
    Play,
    Exclaim,
    Heart,
    Star,
    Droplet,
    Cloud,
    Ghost,
    Bubble,
    Bulb,
    Gear,
    Moon,
    Flame,
    Bolt,
    Diamond,
    Bell,
    Check,
    Cross,
    Magnifier,
    Hourglass,
}

const HEX: [[f32; 2]; 6] = [
    [0.0, -0.82],
    [0.7101, -0.41],
    [0.7101, 0.41],
    [0.0, 0.82],
    [-0.7101, 0.41],
    [-0.7101, -0.41],
];
const TRI: [[f32; 2]; 3] = [[-0.5, -0.74], [0.74, 0.0], [-0.5, 0.74]];
const STAR: [[f32; 2]; 10] = [
    [0.0, -0.92],
    [0.2821, -0.3883],
    [0.875, -0.2843],
    [0.4565, 0.1483],
    [0.5408, 0.7443],
    [0.0, 0.48],
    [-0.5408, 0.7443],
    [-0.4565, 0.1483],
    [-0.875, -0.2843],
    [-0.2821, -0.3883],
];
const BOLT: [[f32; 2]; 7] = [
    [0.45, -1.0],
    [-0.05, -1.0],
    [-0.5, 0.1],
    [-0.05, 0.1],
    [-0.3, 1.0],
    [0.5, -0.25],
    [0.08, -0.25],
];
const GEM: [[f32; 2]; 5] = [[-0.5, -0.72], [0.5, -0.72], [0.92, -0.25], [0.0, 0.92], [-0.92, -0.25]];
const TAIL: [[f32; 2]; 3] = [[-0.5, 0.45], [-0.1, 0.45], [-0.62, 0.98]];
const EAR_L: [[f32; 2]; 3] = [[-0.84, -0.25], [-0.74, -1.02], [-0.22, -0.72]];
const EAR_R: [[f32; 2]; 3] = [[0.84, -0.25], [0.74, -1.02], [0.22, -0.72]];
const GLASS: [[f32; 2]; 6] = [
    [-0.6, -0.86],
    [0.6, -0.86],
    [0.09, 0.0],
    [0.6, 0.86],
    [-0.6, 0.86],
    [-0.09, 0.0],
];

impl Shape {
    /// Signed distance at (x, y); `t` drives shapes that move on their own
    /// (the gear turns, the flame flickers, the ghost's hem ripples).
    pub fn dist(self, x: f32, y: f32, t: f32) -> f32 {
        match self {
            Shape::Ball => hypot(x, y) - 1.0,
            Shape::Cat => smin(hypot(x, y - 0.08) - 0.9, mn(poly(x, y, &EAR_L), poly(x, y, &EAR_R)), 0.14),
            Shape::Egg => smin(circle(x, y, 0.0, 0.2, 0.78), circle(x, y, 0.0, -0.35, 0.52), 0.6),
            Shape::Hexagon => poly(x, y, &HEX) - 0.14,
            Shape::Play => poly(x, y, &TRI) - 0.16,
            Shape::Exclaim => mn(uneven_capsule(x, y + 0.6, 0.32, 0.2, 0.72), circle(x, y, 0.0, 0.74, 0.25)),
            Shape::Heart => heart(x / 1.72, (0.95 - y) / 1.72) * 1.72,
            Shape::Star => poly(x, y, &STAR) - 0.1,
            Shape::Droplet => uneven_capsule(x, 0.28 - y, 0.68, 0.03, 1.23),
            Shape::Cloud => {
                let mut d = circle(x, y, -0.5, 0.18, 0.46);
                d = smin(d, circle(x, y, 0.52, 0.2, 0.44), 0.15);
                d = smin(d, circle(x, y, -0.02, -0.2, 0.58), 0.15);
                smin(d, box_r(x, y, 0.0, 0.36, 0.78, 0.28, 0.28), 0.1)
            }
            Shape::Ghost => {
                let d = mn(circle(x, y, 0.0, -0.15, 0.8), box_r(x, y, 0.0, 0.35, 0.8, 0.5, 0.0));
                let sx = rem(x + t * 0.25 + 0.265, 0.53) - 0.265;
                mx(d, -(hypot(sx, y - 0.92) - 0.18))
            }
            Shape::Bubble => mn(box_r(x, y, 0.0, -0.12, 0.95, 0.66, 0.3), poly(x, y, &TAIL)),
            Shape::Bulb => smin(circle(x, y, 0.0, -0.22, 0.72), box_r(x, y, 0.0, 0.62, 0.3, 0.3, 0.08), 0.25),
            Shape::Gear => {
                let seg_a = TAU / 8.0;
                let r = hypot(x, y);
                let a = rem(atan2(y, x) - t * 0.9, seg_a) - seg_a / 2.0;
                mn(r - 0.72, box_r(cos(a) * r, sin(a) * r, 0.78, 0.0, 0.2, 0.15, 0.04))
            }
            Shape::Moon => mx(hypot(x, y) - 0.95, -circle(x, y, 0.62, -0.25, 0.7)),
            Shape::Flame => {
                let s = sin(t * 7.0) * 0.12 + sin(t * 11.3) * 0.06;
                let mut d = circle(x, y, 0.0, 0.32, 0.66);
                d = smin(d, circle(x, y, s * 0.4, -0.22, 0.42), 0.35);
                d = smin(d, circle(x, y, s, -0.66, 0.2), 0.35);
                smin(d, circle(x, y, s * 1.4, -0.95, 0.05), 0.3)
            }
            Shape::Bolt => poly(x, y, &BOLT) - 0.04,
            Shape::Diamond => poly(x, y, &GEM) - 0.06,
            Shape::Bell => {
                let mut d = smin(circle(x, y, 0.0, -0.25, 0.55), box_r(x, y, 0.0, 0.2, 0.62, 0.35, 0.1), 0.2);
                d = mn(d, box_r(x, y, 0.0, 0.56, 0.86, 0.12, 0.12));
                d = mn(d, circle(x, y, 0.0, 0.84, 0.16));
                mn(d, circle(x, y, 0.0, -0.88, 0.12))
            }
            Shape::Check => mn(seg(x, y, -0.62, 0.08, -0.2, 0.5), seg(x, y, -0.2, 0.5, 0.66, -0.45)) - 0.2,
            Shape::Cross => mn(seg(x, y, -0.55, -0.55, 0.55, 0.55), seg(x, y, -0.55, 0.55, 0.55, -0.55)) - 0.2,
            Shape::Magnifier => mn(
                abs(circle(x, y, -0.15, -0.18, 0.55)) - 0.14,
                seg(x, y, 0.32, 0.3, 0.76, 0.78) - 0.15,
            ),
            Shape::Hourglass => mn(
                poly(x, y, &GLASS) - 0.05,
                mn(box_r(x, y, 0.0, -0.9, 0.72, 0.09, 0.05), box_r(x, y, 0.0, 0.9, 0.72, 0.09, 0.05)),
            ),
        }
    }

    /// Where the face sits: (x, y, scale), or `None` for shapes with no face.
    pub fn face(self) -> Option<(f32, f32, f32)> {
        Some(match self {
            Shape::Ball => (0.0, 0.0, 1.0),
            Shape::Cat => (0.0, 0.1, 0.9),
            Shape::Egg => (0.0, 0.15, 0.8),
            Shape::Hexagon => (0.0, 0.0, 0.9),
            Shape::Play => (-0.08, 0.0, 0.62),
            Shape::Heart => (0.0, -0.2, 0.78),
            Shape::Star => (0.0, 0.06, 0.6),
            Shape::Droplet => (0.0, 0.3, 0.68),
            Shape::Cloud => (0.0, 0.12, 0.8),
            Shape::Ghost => (0.0, -0.15, 0.8),
            Shape::Bulb => (0.0, -0.22, 0.72),
            Shape::Gear => (0.0, 0.0, 0.66),
            Shape::Moon => (-0.5, 0.08, 0.5),
            Shape::Flame => (0.0, 0.34, 0.62),
            Shape::Diamond => (0.0, -0.12, 0.68),
            Shape::Bell => (0.0, 0.02, 0.66),
            Shape::Exclaim
            | Shape::Bubble
            | Shape::Bolt
            | Shape::Check
            | Shape::Cross
            | Shape::Magnifier
            | Shape::Hourglass => return None,
        })
    }
}
