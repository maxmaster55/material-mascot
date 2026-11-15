//! Every state the mascot can be in, and the pose each one holds at a given moment.

use crate::eye::{Eye, EyeKind};
use crate::math::*;
use crate::palette::Ink;
use crate::shape::Shape;
use crate::sprite::Sprite;

/// Fixed-capacity list, so poses need no heap.
#[derive(Clone, Copy)]
pub(crate) struct Buf<T: Copy, const N: usize> {
    items: [Option<T>; N],
    len: usize,
}
impl<T: Copy, const N: usize> Buf<T, N> {
    pub const fn new() -> Self {
        Buf { items: [None; N], len: 0 }
    }
    /// Adds an item; silently dropped when full (decor only).
    pub fn push(&mut self, v: T) {
        if self.len < N {
            self.items[self.len] = Some(v);
            self.len += 1;
        }
    }
    pub fn iter(&self) -> impl Iterator<Item = &T> {
        self.items[..self.len].iter().flatten()
    }
    pub fn is_empty(&self) -> bool {
        self.len == 0
    }
}

#[derive(Clone, Copy)]
pub(crate) struct Prop {
    pub sprite: Sprite,
    pub x: f32,
    pub y: f32,
    pub ink: Ink,
}
#[derive(Clone, Copy)]
pub(crate) struct Dot {
    pub x: f32,
    pub y: f32,
    pub r: f32,
    pub ink: Ink,
    /// Painted on the body (blush, typing dots) rather than floating beside it.
    pub on: bool,
}

/// One instant of the mascot. Geometry is in ball-radius units, y down.
#[derive(Clone, Copy)]
pub(crate) struct Pose {
    pub shape: Shape,
    /// Second shape and how far toward it, for states that morph within themselves.
    pub shape2: Option<Shape>,
    pub mix: f32,
    /// Clock handed to shapes that move on their own.
    pub st: f32,
    pub cx: f32,
    pub cy: f32,
    pub sx: f32,
    pub sy: f32,
    pub rot: f32,
    /// Gaze, degrees: yaw right, pitch up, roll clockwise.
    pub yaw: f32,
    pub pitch: f32,
    pub roll: f32,
    /// Half the angle between the eyes.
    pub split: f32,
    pub face: bool,
    pub blink: bool,
    /// Gaze is part of the act here, so idle wandering is turned down.
    pub still: bool,
    pub el: Eye,
    pub er: Eye,
    pub props: Buf<Prop, 6>,
    pub dots: Buf<Dot, 6>,
    pub rings: Buf<f32, 3>,
    /// A small lump merged into the body (the waving hand).
    pub blob: Option<(f32, f32, f32)>,
    /// Face anchor resolved from the shape: (x, y, scale).
    pub fx: f32,
    pub fy: f32,
    pub fs: f32,
    pub(crate) cr: f32,
    pub(crate) sr: f32,
}

impl Pose {
    fn new(t: f32) -> Pose {
        Pose {
            shape: Shape::Ball,
            shape2: None,
            mix: 0.0,
            st: t,
            cx: 0.0,
            cy: 0.0,
            sx: 1.0,
            sy: 1.0,
            rot: 0.0,
            yaw: 0.0,
            pitch: 4.0,
            roll: 0.0,
            split: 19.0,
            face: true,
            blink: true,
            still: false,
            el: Eye::cap(0.22, 0.46),
            er: Eye::cap(0.22, 0.46),
            props: Buf::new(),
            dots: Buf::new(),
            rings: Buf::new(),
            blob: None,
            fx: 0.0,
            fy: 0.0,
            fs: 1.0,
            cr: 1.0,
            sr: 0.0,
        }
    }
    fn eyes(&mut self, e: Eye) {
        self.el = e;
        self.er = e;
    }
    fn breathe(&mut self, t: f32, period: f32, amp: f32) {
        let b = wave(t, period, 0.0) * amp;
        self.sy = 1.0 + b;
        self.cy = -b;
    }
    fn prop(&mut self, sprite: Sprite, x: f32, y: f32, ink: Ink) {
        self.props.push(Prop { sprite, x, y, ink });
    }
    fn dot(&mut self, x: f32, y: f32, r: f32, ink: Ink, on: bool) {
        self.dots.push(Dot { x, y, r, ink, on });
    }
    /// Sound arcs radiating from one side.
    fn arcs(&mut self, t: f32, x: f32, y: f32, ink: Ink, left: bool) {
        let s = floor(t * 3.0) as i32 % 4;
        let d = if left { -1.0 } else { 1.0 };
        if s >= 1 {
            self.prop(if left { Sprite::Arc1L } else { Sprite::Arc1 }, x, y, ink);
        }
        if s >= 2 {
            self.prop(if left { Sprite::Arc2L } else { Sprite::Arc2 }, x + 0.24 * d, y, ink);
        }
    }
    fn twinkle(&mut self, t: f32, x: f32, y: f32, phase: f32) {
        let f = floor(t * 4.0 + phase) as i32 % 4;
        if f != 3 {
            self.prop(if f == 1 { Sprite::Spark } else { Sprite::Plus }, x, y, Ink::Yellow);
        }
    }
    fn zees(&mut self, t: f32, n: usize) {
        for i in 0..n {
            let p = fract(t / 3.0 + i as f32 / 3.0);
            self.prop(if p < 0.45 { Sprite::Zs } else { Sprite::Z }, 0.72 + p * 0.5, -0.72 - p * 0.62, Ink::Blue);
        }
    }

    /// Resolve the derived fields once per frame.
    pub(crate) fn finish(mut self, cat: bool) -> Pose {
        if cat && self.shape == Shape::Ball {
            self.shape = Shape::Cat;
        }
        if cat && self.shape2 == Some(Shape::Ball) {
            self.shape2 = Some(Shape::Cat);
        }
        match self.shape.face() {
            None => self.face = false,
            Some(f) => {
                let g = self.shape2.and_then(Shape::face).unwrap_or(f);
                let m = if self.shape2.is_some() { self.mix } else { 0.0 };
                self.fx = lerp(f.0, g.0, m);
                self.fy = lerp(f.1, g.1, m);
                self.fs = lerp(f.2, g.2, m);
            }
        }
        self.cr = cos(self.rot);
        self.sr = sin(self.rot);
        self
    }

    /// Body distance at a point, with squash, rotation and offset applied.
    pub(crate) fn dist(&self, x: f32, y: f32) -> f32 {
        let qx0 = (x - self.cx) / self.sx;
        let qy0 = (y - self.cy) / self.sy;
        let qx = qx0 * self.cr + qy0 * self.sr;
        let qy = -qx0 * self.sr + qy0 * self.cr;
        let mut d = self.shape.dist(qx, qy, self.st);
        if let Some(s2) = self.shape2 {
            if self.mix > 0.0 {
                d = lerp(d, s2.dist(qx, qy, self.st), self.mix);
            }
        }
        d *= mn(self.sx, self.sy);
        if let Some((bx, by, br)) = self.blob {
            d = crate::sdf::smin(d, hypot(x - bx, y - by) - br, 0.18);
        }
        d
    }
}

macro_rules! states {
    ($($variant:ident => $name:literal),* $(,)?) => {
        /// What the mascot is feeling, doing, or has turned into.
        #[derive(Clone, Copy, PartialEq, Eq, Debug, Hash)]
        pub enum State { $($variant),* }

        impl State {
            /// Every state, in display order: emotions, objects, then the
            /// original library's shape morphs.
            pub const ALL: &'static [State] = &[$(State::$variant),*];

            /// Lowercase name, e.g. `"thinking"`.
            pub const fn name(self) -> &'static str {
                match self { $(State::$variant => $name),* }
            }

            /// Look a state up by its lowercase name.
            pub fn from_name(name: &str) -> Option<State> {
                match name { $($name => Some(State::$variant),)* _ => None }
            }
        }
    };
}

states! {
    // emotions
    Idle => "idle", Listening => "listening", Thinking => "thinking", Working => "working",
    Responding => "responding", Narrating => "narrating", Reading => "reading", Alert => "alert",
    Notify => "notify", Happy => "happy", Smile => "smile", Wink => "wink", Laugh => "laugh",
    Excited => "excited", Love => "love", Proud => "proud", Greet => "greet", Confused => "confused",
    Silly => "silly", Sad => "sad", Sleepy => "sleepy", Angry => "angry", Dizzy => "dizzy",
    // objects
    Heart => "heart", Star => "star", Droplet => "droplet", Cloud => "cloud", Ghost => "ghost",
    Bubble => "bubble", Bulb => "bulb", Gear => "gear", Moon => "moon", Flame => "flame",
    Bolt => "bolt", Diamond => "diamond", Bell => "bell", Check => "check", Cross => "cross",
    Magnifier => "magnifier", Hourglass => "hourglass", Cat => "cat",
    // the original shape morphs
    Egg => "egg", Hexagon => "hexagon", Play => "play", Exclaim => "exclaim", Burst => "burst",
    Comet => "comet", Orbit => "orbit", Swirl => "swirl",
}

const ARC_HAPPY: Eye = Eye::of(EyeKind::Arc, 0.38, 0.15);

impl State {
    /// The pose at `t` seconds into this state. Pure: same `t`, same pose.
    pub(crate) fn pose(self, t: f32) -> Pose {
        use Ink::{Blue, Pink, Yellow};
        let mut p = Pose::new(t);
        match self {
            State::Idle => p.breathe(t, 3.4, 0.03),
            State::Listening => {
                p.breathe(t, 3.0, 0.02);
                p.pitch = 10.0;
                p.yaw = -6.0;
                p.eyes(Eye::cap(0.26, 0.56));
                p.arcs(t, 1.25, -0.15, Blue, false);
            }
            State::Thinking => {
                p.cy = wave(t, 2.8, 0.0) * 0.02;
                p.yaw = 20.0;
                p.pitch = 20.0;
                p.roll = -6.0;
                p.still = true;
                p.eyes(Eye::cap(0.22, 0.4).lid(0.15));
                for i in 0..3 {
                    let f = i as f32;
                    p.prop(Sprite::Dot, 0.5 + f * 0.32, -1.3 - mx(0.0, sin(t * 5.0 - f * 0.9)) * 0.14, Ink::Eye);
                }
            }
            State::Working => {
                p.cy = wave(t, 0.5, 0.0) * 0.015;
                p.pitch = -8.0;
                p.yaw = wave(t, 1.6, 0.0) * 9.0;
                p.still = true;
                p.eyes(Eye::cap(0.24, 0.34).lid(0.3));
                for j in 0..3 {
                    let a = (floor(t * 10.0) - j as f32) * TAU / 8.0;
                    p.prop(Sprite::Px, 1.12 + cos(a) * 0.24, -1.08 + sin(a) * 0.24, Yellow);
                }
            }
            State::Responding => {
                p.cy = wave(t, 0.6, 0.0) * 0.04;
                p.sy = 1.0 + wave(t, 0.6, 0.0) * 0.03;
                p.pitch = 10.0;
                p.eyes(Eye::cap(0.22, 0.5));
                let frame = [Sprite::Bub0, Sprite::Bub1, Sprite::Bub2, Sprite::Bub3][floor(t * 4.0) as usize % 4];
                p.prop(frame, 1.08, -1.08, Ink::Eye);
            }
            State::Narrating => {
                p.cy = wave(t, 0.55, 0.0) * 0.03;
                p.pitch = -12.0;
                p.eyes(Eye::cap(0.22, 0.36));
                p.arcs(t, 1.22, 0.2, Blue, false);
            }
            State::Reading => {
                let c = rem(t, 2.6) / 2.6;
                p.yaw = if c < 0.76 { -15.0 + 30.0 * (c / 0.76) } else { 15.0 - 30.0 * ease_io((c - 0.76) / 0.24) };
                p.pitch = -16.0;
                p.still = true;
                p.eyes(Eye::cap(0.24, 0.34).lid(0.2));
                p.prop(Sprite::Book, 0.0, 1.3, Ink::Eye);
            }
            State::Alert => {
                p.cx = wave(t, 0.12, 0.0) * 0.03;
                p.sy = 1.06;
                p.sx = 0.97;
                p.cy = -0.06;
                p.pitch = 6.0;
                p.still = true;
                p.eyes(Eye::cap(0.36, 0.36));
                if floor(t * 4.0) as i32 % 2 == 0 {
                    p.prop(Sprite::Ex, 1.22, -1.1, Pink);
                }
            }
            State::Notify => {
                p.breathe(t, 3.0, 0.02);
                p.yaw = 16.0;
                p.pitch = 14.0;
                p.still = true;
                p.eyes(Eye::cap(0.3, 0.36));
                p.prop(if rem(t, 2.4) < 0.25 { Sprite::BadgeL } else { Sprite::Badge }, 0.78, -0.78, Pink);
            }
            State::Happy => {
                let h = abs(wave(t, 1.8, 0.0));
                p.cy = -0.14 * h;
                p.sy = 0.95 + 0.1 * h;
                p.sx = 1.04 - 0.06 * h;
                p.pitch = 8.0;
                p.blink = false;
                p.eyes(ARC_HAPPY);
                p.twinkle(t, -1.2, -0.95, 0.0);
                p.twinkle(t, 1.25, -0.6, 2.0);
            }
            State::Smile => {
                p.breathe(t, 3.2, 0.02);
                p.roll = wave(t, 3.0, 0.0) * 4.0;
                p.pitch = 6.0;
                p.blink = false;
                p.eyes(Eye::of(EyeKind::Arc, 0.34, 0.14));
                p.dot(-0.62, 0.22, 0.11, Pink, true);
                p.dot(0.62, 0.22, 0.11, Pink, true);
            }
            State::Wink => {
                p.breathe(t, 3.0, 0.02);
                p.roll = 8.0;
                p.pitch = 6.0;
                p.blink = false;
                p.el = Eye::cap(0.22, 0.5);
                p.er = Eye::of(EyeKind::Chev, 0.32, 0.32);
                p.twinkle(t, 1.2, -0.62, 0.0);
            }
            State::Laugh => {
                p.cy = -abs(wave(t, 0.64, 0.0)) * 0.08;
                p.roll = wave(t, 0.64, 0.0) * 5.0;
                p.pitch = 10.0;
                p.blink = false;
                p.still = true;
                p.eyes(Eye::of(EyeKind::Chev, 0.32, 0.34));
            }
            State::Excited => {
                let h = abs(wave(t, 1.0, 0.0));
                p.cy = -0.18 * h;
                p.sy = 0.94 + 0.12 * h;
                p.sx = 1.05 - 0.08 * h;
                p.pitch = 8.0;
                p.blink = false;
                p.split = 27.0;
                p.eyes(Eye::of(EyeKind::Spark, 0.62, 0.62).ink(Yellow));
                p.twinkle(t * 1.5, -1.2, -0.9, 0.0);
                p.twinkle(t * 1.5, 1.2, -0.95, 1.0);
                p.twinkle(t * 1.5, 1.3, 0.3, 2.0);
            }
            State::Love => {
                let beat = 1.0 + 0.1 * mx(0.0, wave(t, 0.9, 0.0));
                p.cx = wave(t, 1.8, 0.0) * 0.04;
                p.roll = wave(t, 1.8, 0.0) * 5.0;
                p.pitch = 6.0;
                p.blink = false;
                p.split = 27.0;
                p.still = true;
                p.eyes(Eye::of(EyeKind::Heart, 0.62 * beat, 0.56).ink(Pink));
                for i in 0..2 {
                    let c = fract(t / 2.2 + i as f32 * 0.5);
                    if c <= 0.85 {
                        let x = if i == 1 { 1.15 } else { -1.15 };
                        p.prop(
                            if c < 0.4 { Sprite::HeartS } else { Sprite::Heart },
                            x + sin(c * TAU) * 0.08,
                            -0.2 - c,
                            Pink,
                        );
                    }
                }
            }
            State::Proud => {
                p.sy = 1.05 + wave(t, 3.6, 0.0) * 0.015;
                p.sx = 0.98;
                p.cy = -0.05;
                p.pitch = 14.0;
                p.roll = -5.0;
                p.still = true;
                p.eyes(Eye::cap(0.38, 0.4).lid(0.5));
                p.twinkle(t, -1.15, -0.95, 0.0);
            }
            State::Greet => {
                let hop = abs(wave(t, 2.6, 0.0));
                let a = (-38.0 + wave(t, 0.5, 0.0) * 22.0) * RAD;
                p.cy = -0.06 * hop;
                p.roll = wave(t, 2.6, 0.0) * 6.0;
                p.pitch = 8.0;
                p.eyes(Eye::cap(0.22, 0.5));
                p.blob = Some((cos(a) * 1.12, sin(a) * 1.12 - 0.06 * hop, 0.2));
            }
            State::Confused => {
                p.roll = -12.0 + wave(t, 2.6, 0.0) * 3.0;
                p.yaw = wave(t, 2.6, 0.0) * 6.0;
                p.still = true;
                p.el = Eye::cap(0.36, 0.36);
                p.er = Eye::cap(0.26, 0.2);
                p.prop(Sprite::Q, 1.1, -1.08 + wave(t, 1.4, 0.0) * 0.06, Blue);
            }
            State::Silly => {
                p.cx = wave(t, 0.9, 0.0) * 0.05;
                p.roll = wave(t, 0.9, 0.0) * 14.0;
                p.blink = false;
                p.still = true;
                p.split = 25.0;
                p.el = Eye::of(EyeKind::Ring, 0.6, 0.6);
                p.er = Eye::of(EyeKind::Chev, 0.32, 0.32);
            }
            State::Sad => {
                let c = fract(t / 1.8);
                p.sy = 0.94 + wave(t, 3.2, 0.0) * 0.012;
                p.cy = 0.08;
                p.pitch = -14.0;
                p.roll = wave(t, 4.0, 0.0) * 2.0;
                p.still = true;
                p.eyes(Eye::cap(0.42, 0.42).slant(-1.0).lid(0.1));
                if c < 0.8 {
                    p.prop(Sprite::Tear, -0.36, 0.6 + c * 0.55, Blue);
                }
            }
            State::Sleepy => {
                p.sy = 0.93 + wave(t, 3.2, 0.0) * 0.03;
                p.sx = 1.03;
                p.cy = 0.08;
                p.pitch = -8.0;
                p.roll = -4.0;
                p.blink = false;
                p.still = true;
                p.eyes(Eye::of(EyeKind::Arc, 0.34, 0.1).dir(-1.0));
                p.zees(t, 3);
            }
            State::Angry => {
                p.cx = wave(t, 0.1, 0.0) * 0.025;
                p.sy = 0.97;
                p.cy = 0.03;
                p.pitch = 2.0;
                p.still = true;
                p.blink = false;
                p.eyes(Eye::cap(0.42, 0.42).slant(1.0).lid(0.1));
                p.prop(
                    if floor(t * 3.0) as i32 % 2 == 1 { Sprite::Anger } else { Sprite::AngerS },
                    1.02,
                    -1.05,
                    Pink,
                );
            }
            State::Dizzy => {
                p.cx = cos(t * 4.0) * 0.06;
                p.cy = sin(t * 4.0) * 0.03;
                p.roll = wave(t, 1.6, 0.0) * 10.0;
                p.blink = false;
                p.still = true;
                p.split = 27.0;
                p.eyes(Eye::of(EyeKind::Ring, 0.6, 0.6));
                for i in 0..3 {
                    let a = t * 3.0 + i as f32 * TAU / 3.0;
                    p.prop(Sprite::Plus, cos(a) * 0.85, -1.2 + sin(a) * 0.16, Yellow);
                }
            }

            State::Heart => {
                let c = rem(t, 1.1) / 1.1;
                let b = 1.0 + 0.08 * if c < 0.5 { mx(0.0, sin(c * TAU * 2.0)) } else { 0.0 };
                p.shape = Shape::Heart;
                p.sx = b;
                p.sy = b;
                p.blink = false;
                p.eyes(ARC_HAPPY);
            }
            State::Star => {
                p.shape = Shape::Star;
                p.rot = wave(t, 2.4, 0.0) * 0.16;
                p.cy = wave(t, 1.2, 0.0) * 0.03;
                p.twinkle(t, -1.2, -0.8, 0.0);
                p.twinkle(t, 1.25, 0.5, 2.0);
            }
            State::Droplet => {
                p.shape = Shape::Droplet;
                p.sy = 1.0 + wave(t, 1.6, 0.0) * 0.05;
                p.sx = 1.0 - wave(t, 1.6, 0.0) * 0.04;
                p.cy = wave(t, 1.6, 0.0) * 0.03;
            }
            State::Cloud => {
                p.shape = Shape::Cloud;
                p.cx = wave(t, 4.0, 0.0) * 0.08;
                p.cy = -0.15;
                for i in 0..3 {
                    let f = i as f32;
                    let c = fract(t * 1.2 + f * 0.37);
                    p.dot(-0.45 + f * 0.45 + p.cx, 0.75 + c * 0.6, 0.06, Blue, false);
                }
            }
            State::Ghost => {
                p.shape = Shape::Ghost;
                p.cy = wave(t, 2.0, 0.0) * 0.06;
                p.eyes(Eye::cap(0.26, 0.46));
            }
            State::Bubble => {
                p.shape = Shape::Bubble;
                p.breathe(t, 2.4, 0.02);
                for i in 0..3 {
                    let f = i as f32;
                    p.dot(-0.42 + f * 0.42, -0.12 - mx(0.0, sin(t * 5.0 - f * 0.9)) * 0.14, 0.12, Ink::Eye, true);
                }
            }
            State::Bulb => {
                let r = if floor(t * 2.5) as i32 % 2 == 1 { 1.2 } else { 1.12 };
                p.shape = Shape::Bulb;
                p.blink = false;
                p.eyes(ARC_HAPPY);
                for a in [-170.0f32, -130.0, -90.0, -50.0, -10.0] {
                    p.dot(cos(a * RAD) * r, -0.22 + sin(a * RAD) * r, 0.07, Yellow, false);
                }
            }
            State::Gear => {
                p.shape = Shape::Gear;
                p.still = true;
                p.eyes(Eye::cap(0.26, 0.36).lid(0.3));
            }
            State::Moon => {
                p.shape = Shape::Moon;
                p.rot = wave(t, 3.4, 0.0) * 0.08;
                p.blink = false;
                p.still = true;
                p.split = 24.0;
                p.eyes(Eye::of(EyeKind::Arc, 0.4, 0.12).dir(-1.0));
                p.zees(t, 2);
            }
            State::Flame => {
                p.shape = Shape::Flame;
                p.sy = 1.0 + wave(t, 0.4, 0.0) * 0.03;
                p.cy = -wave(t, 0.4, 0.0) * 0.03;
            }
            State::Bolt => {
                p.shape = Shape::Bolt;
                p.cx = if floor(t * 8.0) as i32 % 5 == 0 { 0.05 } else { 0.0 };
                for i in 0..3 {
                    let f = i as f32;
                    let c = fract(t * 1.6 + f * 0.33);
                    let a = f * 2.3 + 0.6;
                    p.dot(cos(a) * (0.7 + c * 0.6), sin(a) * (0.7 + c * 0.6), 0.06, Yellow, false);
                }
            }
            State::Diamond => {
                p.shape = Shape::Diamond;
                p.cy = wave(t, 2.2, 0.0) * 0.04;
                p.twinkle(t, -1.1, -0.9, 0.0);
                p.twinkle(t, 1.15, -0.1, 2.0);
            }
            State::Bell => {
                p.shape = Shape::Bell;
                p.rot = wave(t, 0.5, 0.0) * 0.22;
                p.arcs(t, 1.2, -0.3, Yellow, false);
                p.arcs(t, -1.2, -0.3, Yellow, true);
            }
            State::Check => {
                let c = rem(t, 2.4);
                let s = 0.2 + 0.8 * settle(c / 0.45);
                p.shape = Shape::Check;
                p.sx = s;
                p.sy = s;
                if c > 0.4 {
                    p.twinkle(t, 1.1, -0.9, 0.0);
                }
            }
            State::Cross => {
                let c = rem(t, 2.4);
                p.shape = Shape::Cross;
                p.cx = wave(c, 0.1, 0.0) * 0.08 * mx(0.0, 1.0 - c / 0.6);
            }
            State::Magnifier => {
                p.shape = Shape::Magnifier;
                p.cx = cos(t * 2.2) * 0.14;
                p.cy = sin(t * 2.2) * 0.09;
            }
            State::Hourglass => {
                let c = t / 2.4;
                let i = floor(c);
                p.shape = Shape::Hourglass;
                // Only the parity of the turn count matters, which keeps the angle small.
                p.rot = PI * (rem(i, 2.0) + ease_io((c - i - 0.7) / 0.3));
                p.sx = 0.92;
                p.sy = 0.92;
            }
            State::Cat => {
                p.shape = Shape::Cat;
                p.breathe(t, 3.4, 0.03);
                p.eyes(Eye::cap(0.22, 0.42));
            }

            State::Egg => {
                p.shape = Shape::Egg;
                p.rot = wave(t, 1.4, 0.0) * 0.1;
                p.cx = wave(t, 1.4, 0.0) * 0.04;
            }
            State::Hexagon => {
                p.shape = Shape::Hexagon;
                p.rot = wave(t, 3.0, 0.0) * 0.12;
            }
            State::Play => {
                p.shape = Shape::Play;
                p.sx = 1.0 + wave(t, 1.2, 0.0) * 0.04;
                p.sy = 1.0 - wave(t, 1.2, 0.0) * 0.03;
                p.cx = 0.1;
            }
            State::Exclaim => {
                p.shape = Shape::Exclaim;
                p.cy = -abs(wave(t, 1.6, 0.0)) * 0.1;
            }
            State::Burst => {
                let c = rem(t, 2.6);
                let col = 1.0 - 0.834 * ease_out5(c / 0.7);
                let r = col + (1.0 - col) * ease_out5((c - 1.7) / 0.7);
                p.sx = r;
                p.sy = r;
                p.face = c > 1.95;
                p.still = true;
                for i in 0..5 {
                    let f = i as f32;
                    let u = c - f * 0.2;
                    if u > 0.0 && u < 0.62 {
                        // 0.75^(10u), the spiral's decay
                        let rho = (0.9 + (i % 3) as f32 * 0.12) * exp_neg(2.877 * u);
                        let a = f * (TAU / 5.0) + 0.7 + u * 100.0 * RAD;
                        p.dot(cos(a) * rho, sin(a) * rho, 0.06, Yellow, false);
                    }
                }
            }
            State::Comet => {
                let c = rem(t, 2.4);
                let col = 1.0 - 0.83 * ease_out5(c / 0.55);
                let r = col + (1.0 - col) * ease_out5((c - 1.85) / 0.6);
                let a = c * (TAU / 1.2);
                p.sx = r;
                p.sy = r;
                p.cy = -sin(sat(c / 1.7) * PI) * 0.06;
                p.face = c > 2.05;
                p.still = true;
                if c > 0.3 && c < 2.0 {
                    p.dot(cos(a) * 0.7, sin(a) * 0.2, 0.07, Yellow, false);
                    p.dot(cos(a - 0.5) * 0.7, sin(a - 0.5) * 0.2, 0.04, Yellow, false);
                }
            }
            State::Orbit => {
                let c = rem(t, 3.4);
                let back = ease_io((c - 1.4) / 0.8);
                p.shape = Shape::Play;
                p.shape2 = Some(Shape::Ball);
                p.mix = back;
                p.rot = -TAU * ease_io(c / 1.3);
                p.yaw = sin(c * 6.5) * 20.0 * (1.0 - back);
                p.still = true;
                for (i, r) in [1.22f32, 1.4, 1.58].into_iter().enumerate() {
                    let f = i as f32;
                    if c > 0.25 + f * 0.13 && c < 2.6 - f * 0.2 {
                        p.rings.push(r);
                    }
                }
            }
            State::Swirl => {
                let c = rem(t, 1.8);
                p.breathe(t, 3.4, 0.03);
                for (i, r) in [1.22f32, 1.4, 1.58].into_iter().enumerate() {
                    let f = i as f32;
                    if c > f * 0.14 && c < 0.9 + f * 0.14 {
                        p.rings.push(r);
                    }
                }
            }
        }
        p
    }
}
