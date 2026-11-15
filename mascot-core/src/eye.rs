//! Eyes: the mascot has no mouth, so these carry every expression.

use crate::math::*;
use crate::palette::Ink;
use crate::sdf::*;

#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub(crate) enum EyeKind {
    /// Capsule, optionally cut by a lid.
    Cap,
    /// Curved stroke: `dir` 1 arches up (happy), -1 sags (asleep).
    Arc,
    Heart,
    /// Four-point sparkle.
    Spark,
    Ring,
    /// Chevron pointing at the nose: `> <`.
    Chev,
}

/// One eye, in ball-radius units.
#[derive(Clone, Copy, Debug)]
pub(crate) struct Eye {
    pub kind: EyeKind,
    pub w: f32,
    pub h: f32,
    pub tilt: f32,
    /// How far the upper lid has come down, 0..1.
    pub lid: f32,
    /// Lid slope: positive drops toward the nose (angry), negative away (sad).
    pub slant: f32,
    pub dir: f32,
    pub ink: Ink,
    pub sq: bool,
    pub pupil: bool,
    pub orb: bool,
}

impl Eye {
    pub const fn cap(w: f32, h: f32) -> Eye {
        Eye {
            kind: EyeKind::Cap,
            w,
            h,
            tilt: 0.0,
            lid: 0.0,
            slant: 0.0,
            dir: 1.0,
            ink: Ink::Eye,
            sq: false,
            pupil: false,
            orb: false,
        }
    }
    pub const fn of(kind: EyeKind, w: f32, h: f32) -> Eye {
        let mut e = Eye::cap(w, h);
        e.kind = kind;
        e
    }
    pub const fn lid(mut self, lid: f32) -> Eye {
        self.lid = lid;
        self
    }
    pub const fn slant(mut self, slant: f32) -> Eye {
        self.slant = slant;
        self
    }
    pub const fn dir(mut self, dir: f32) -> Eye {
        self.dir = dir;
        self
    }
    pub const fn ink(mut self, ink: Ink) -> Eye {
        self.ink = ink;
        self
    }

    /// Signed distance in eye-local space: u right, v down.
    /// `px` is half a pixel in the same units, which keeps thin strokes at
    /// least one pixel thick. `b` is blink, 0 open to 1 shut.
    /// `side` is -1 for the left eye and +1 for the right.
    pub fn dist(&self, u: f32, v: f32, side: f32, px: f32, b: f32) -> f32 {
        let hw = mx(self.w / 2.0, px);
        let th = mx(0.07, px * 1.02);
        let cap = self.kind == EyeKind::Cap;
        if cap || b > 0.5 {
            let hh = if cap { mx(self.h / 2.0 * (1.0 - b), px) } else { px };
            let lidded = cap && (self.lid > 0.0 || self.slant != 0.0) && b < 0.5;
            let k = if self.sq {
                0.0
            } else if lidded && !self.orb {
                0.3
            } else {
                1.0
            };
            let mut d = box_r(u, v, 0.0, 0.0, hw, hh, mn(hw, hh) * k);
            if lidded {
                let ly = -self.h / 2.0 + self.lid * self.h - self.slant * side * u;
                d = mx(d, mn(ly, self.h / 2.0 - 2.0 * px) - v);
            }
            return d;
        }
        let hh = mx(self.h / 2.0, px);
        match self.kind {
            EyeKind::Arc => {
                let mut d = 9.0;
                let (mut pu, mut pv) = (-hw, self.dir * hh);
                for i in 1..=4 {
                    let uu = -hw + i as f32 * hw / 2.0;
                    let n = uu / hw;
                    let vv = self.dir * (2.0 * n * n - 1.0) * hh;
                    d = mn(d, seg(u, v, pu, pv, uu, vv));
                    pu = uu;
                    pv = vv;
                }
                d - th
            }
            EyeKind::Heart => {
                let s = self.w / 1.21;
                heart(u / s, (0.55 * s - v) / s) * s
            }
            EyeKind::Spark => {
                let a = hw * 0.26;
                let p = [[hw, 0.0], [a, a], [0.0, hw], [-a, a], [-hw, 0.0], [-a, -a], [0.0, -hw], [a, -a]];
                poly(u, v, &p) - hw * 0.06
            }
            EyeKind::Ring => abs(hypot(u, v) - (hw - th)) - th,
            EyeKind::Chev => {
                let (tip, end) = (-side * hw, side * hw);
                mn(seg(u, v, end, -hh, tip, 0.0), seg(u, v, end, hh, tip, 0.0)) - th
            }
            EyeKind::Cap => 9.0,
        }
    }
}

/// Eye personality. Reshapes the plain eye of every state; expression shapes
/// (hearts, arcs, chevrons) keep their meaning.
#[derive(Clone, Copy, PartialEq, Eq, Debug, Default)]
pub enum EyeStyle {
    /// Giant glossy ovals, set a little wider apart.
    #[default]
    Grok,
    /// Tall capsules: the original library's measured look.
    Classic,
    /// Classic, scaled up.
    Big,
    /// Circles.
    Round,
    /// Wide whites with a dark pupil that follows the gaze.
    Pupil,
    /// Square-cornered.
    Block,
    /// Wide flat slits.
    Visor,
    /// Small beads.
    Dot,
}

impl EyeStyle {
    pub(crate) fn apply(self, e: Eye) -> Eye {
        let mut o = e;
        if self == EyeStyle::Grok {
            if e.kind != EyeKind::Cap {
                o.w = e.w * 1.3;
                o.h = e.h * 1.3;
            } else {
                o.w = mn(mx(e.w * 1.9, 0.42), 0.48);
                o.h = mn(mx(e.h * 1.25, 0.5), 0.66);
                o.slant = e.slant * 0.55;
                o.orb = true;
            }
            return o;
        }
        if e.kind != EyeKind::Cap {
            return e;
        }
        match self {
            EyeStyle::Grok | EyeStyle::Classic => {}
            EyeStyle::Big => {
                o.w = e.w * 1.55;
                o.h = e.h * 1.2;
            }
            EyeStyle::Round => {
                let s = mx(e.w, e.h);
                o.w = s;
                o.h = s;
            }
            EyeStyle::Pupil => {
                o.w = mx(e.w * 1.6, 0.52);
                o.h = mx(e.h, 0.52);
                o.pupil = true;
            }
            EyeStyle::Block => {
                o.w = e.w * 1.5;
                o.sq = true;
            }
            EyeStyle::Visor => {
                o.w = e.w * 1.9;
                o.h = mx(e.h * 0.42, 0.12);
                o.sq = true;
            }
            EyeStyle::Dot => {
                o.w = 0.2;
                o.h = 0.2;
                o.lid = 0.0;
                o.slant = 0.0;
            }
        }
        o
    }
}
