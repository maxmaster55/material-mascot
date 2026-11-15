//! Turns a moment in time into pixels.
//!
//! Nothing here allocates or keeps a framebuffer: each pixel is computed on
//! its own from the frame's pose, so memory use is a few hundred bytes of
//! stack whatever the output size.

use crate::eye::{Eye, EyeKind, EyeStyle};
use crate::math::*;
use crate::palette::{Ink, Palette, Rgb, Rgba8};
use crate::state::{Pose, State};

/// How the mascot is painted. Every look shares the same shapes and animation.
#[derive(Clone, Copy, PartialEq, Eq, Debug, Default)]
pub enum Look {
    /// One solid fill with smooth edges. The original library's default.
    #[default]
    Flat,
    /// Lit from above, rim light, soft highlight, contact shadow, glowing eyes.
    Chrome,
    /// Unfilled stroke with the eyes in the same ink.
    Outline,
    /// Smooth 3D shading.
    Clay,
    /// Outline with a glow.
    Neon,
    /// Pixel art on a [`Style::grid`]-cell grid.
    Pixel,
    /// The pixel grid shown as round dots, like an LED matrix.
    Led,
}

impl Look {
    /// Pixel and Led compute one colour per grid cell instead of per output pixel.
    pub const fn is_grid(self) -> bool {
        matches!(self, Look::Pixel | Look::Led)
    }
}

/// Body shading for the [`Look::Pixel`] and [`Look::Led`] looks.
#[derive(Clone, Copy, PartialEq, Eq, Debug, Default)]
pub enum PixelShade {
    /// Lit as a 3D form: banded tones, a highlight and a ground shadow.
    #[default]
    Lit,
    /// A dithered rim of light and shade.
    Soft,
    /// One body tone.
    Flat,
}

/// Everything about how the mascot looks, as opposed to what it is doing.
#[derive(Clone, Copy, Debug)]
pub struct Style {
    pub look: Look,
    pub palette: Palette,
    pub eyes: EyeStyle,
    /// Cells across for the Pixel and Led looks (24 to 64 reads best).
    pub grid: u16,
    pub pixel_shade: PixelShade,
    /// Dark one-cell outline in the Pixel and Led looks.
    pub pixel_outline: bool,
    /// Give the ball cat ears.
    pub cat: bool,
    /// Idle life: gaze wander and blinking. Turn off for reduced motion.
    pub life: bool,
    /// Hold each frame for 1/fps seconds for a hand-animated feel. 0 = every frame.
    pub fps: u8,
    /// Offsets the blink and gaze schedule so several mascots do not move in unison.
    pub seed: f32,
}

impl Style {
    pub const DEFAULT: Style = Style {
        look: Look::Flat,
        palette: Palette::GRAPE,
        eyes: EyeStyle::Grok,
        grid: 32,
        pixel_shade: PixelShade::Lit,
        pixel_outline: true,
        cat: false,
        life: true,
        fps: 0,
        seed: 0.37,
    };
}
impl Default for Style {
    fn default() -> Self {
        Style::DEFAULT
    }
}

/// The mascot: which state it is in and when it got there.
///
/// Holds no animation state beyond that, so rendering is a pure function of
/// the time you pass in: pause, seek and slow motion all just work.
#[derive(Clone, Copy, Debug)]
pub struct Mascot {
    cur: State,
    prev: Option<State>,
    t_cur: u64,
    t_prev: u64,
}

/// Length of the morph between two states, in milliseconds.
pub const MORPH_MS: u64 = 500;

impl Mascot {
    pub const fn new(state: State) -> Mascot {
        Mascot { cur: state, prev: None, t_cur: 0, t_prev: 0 }
    }
    pub const fn state(&self) -> State {
        self.cur
    }
    /// Rebuild a mascot from its four fields, for hosts that keep the state
    /// machine on their side of a language boundary: the current state, the
    /// state it is morphing from (if any), and when each began.
    pub const fn from_parts(cur: State, prev: Option<State>, t_cur_ms: u64, t_prev_ms: u64) -> Mascot {
        Mascot { cur, prev, t_cur: t_cur_ms, t_prev: t_prev_ms }
    }
    /// Switch state. The body, eyes and props morph over [`MORPH_MS`].
    pub fn set_state(&mut self, state: State, now_ms: u64) {
        if state == self.cur {
            return;
        }
        self.prev = Some(self.cur);
        self.t_prev = self.t_cur;
        self.cur = state;
        self.t_cur = now_ms;
    }

    /// Paint one frame, `size` x `size` pixels, by calling `put(x, y, colour)`
    /// for every pixel in row order. Pixels the mascot does not cover come
    /// back fully transparent.
    pub fn render<F: FnMut(u32, u32, Rgba8)>(&self, now_ms: u64, size: u32, style: &Style, mut put: F) {
        if size == 0 {
            return;
        }
        let f = Frame::new(self, now_ms, style, size);
        if style.look.is_grid() {
            let g = f.grid as u32;
            let led = style.look == Look::Led && size >= g * 3;
            let mut cy = 0;
            let mut cell = [0.0f32; 4];
            for py in 0..size {
                // Rows are emitted in order, so each grid row is shaded once per
                // output row; the cost stays per-cell for the common 1:1 case.
                let ny = py * g / size;
                for px in 0..size {
                    let cx = px * g / size;
                    if px == 0 || cx != (px - 1) * g / size || ny != cy {
                        cell = f.sample_cell(cx, ny);
                    }
                    let mut c = cell;
                    if led {
                        let u = fract((px as f32 + 0.5) * g as f32 / size as f32) - 0.5;
                        let v = fract((py as f32 + 0.5) * g as f32 / size as f32) - 0.5;
                        c[3] *= sat((0.45 - hypot(u, v)) * size as f32 / g as f32);
                    }
                    put(px, py, to_rgba8(c));
                }
                cy = ny;
            }
        } else {
            let r = size as f32 * 0.3;
            let half = size as f32 / 2.0;
            for py in 0..size {
                let y = (py as f32 + 0.5 - half) / r;
                for px in 0..size {
                    let x = (px as f32 + 0.5 - half) / r;
                    put(px, py, to_rgba8(f.sample(x, y, 1.0 / r, 0, 0)));
                }
            }
        }
    }

    /// Paint into an RGBA8888 buffer of at least `size * size * 4` bytes.
    pub fn render_rgba8888(&self, now_ms: u64, size: u32, style: &Style, buf: &mut [u8]) {
        self.render(now_ms, size, style, |x, y, c| {
            let i = ((y * size + x) * 4) as usize;
            if let Some(p) = buf.get_mut(i..i + 4) {
                p.copy_from_slice(&[c.r, c.g, c.b, c.a]);
            }
        });
    }

    /// Paint into an RGB565 buffer of at least `size * size` words, flattened onto `bg`.
    pub fn render_rgb565(&self, now_ms: u64, size: u32, style: &Style, bg: Rgb, buf: &mut [u16]) {
        self.render(now_ms, size, style, |x, y, c| {
            if let Some(p) = buf.get_mut((y * size + x) as usize) {
                *p = c.over(bg).to_565();
            }
        });
    }
}

fn to_rgba8(c: [f32; 4]) -> Rgba8 {
    let q = |v: f32| clamp(v + 0.5, 0.0, 255.0) as u8;
    Rgba8 { r: q(c[0]), g: q(c[1]), b: q(c[2]), a: q(c[3] * 255.0) }
}

type C3 = [f32; 3];
const BLACK: C3 = [0.0; 3];
const WHITE: C3 = [255.0; 3];

fn c3(c: Rgb) -> C3 {
    [c.0 as f32, c.1 as f32, c.2 as f32]
}
fn mix3(a: C3, b: C3, t: f32) -> C3 {
    [lerp(a[0], b[0], t), lerp(a[1], b[1], t), lerp(a[2], b[2], t)]
}
/// Composite `src` at coverage `a` over the accumulated pixel.
fn over(dst: &mut [f32; 4], src: C3, a: f32) {
    if a <= 0.0 {
        return;
    }
    let w = dst[3] * (1.0 - a);
    let na = a + w;
    for i in 0..3 {
        dst[i] = (src[i] * a + dst[i] * w) / na;
    }
    dst[3] = na;
}

#[derive(Clone, Copy, PartialEq, Eq)]
enum Mode {
    Flat,
    Line,
    Chrome,
    Clay,
    PixLit,
    PixSoft,
    PixFlat,
}

struct Cols {
    outline: C3,
    shadow: C3,
    base: C3,
    hi: C3,
    eye: C3,
    pink: C3,
    blue: C3,
    yellow: C3,
    deep: C3,
    spec: C3,
    eye_shade: C3,
    ramp: [C3; 5],
    ch_top: C3,
    ch_bot: C3,
    ch_sheen: C3,
    /// Bloom only makes sense when the eye is brighter than the body.
    glow: bool,
}

impl Cols {
    fn new(p: &Palette) -> Cols {
        let (outline, shadow, base, hi, eye) = (c3(p.outline), c3(p.shadow), c3(p.base), c3(p.hi), c3(p.eye));
        let lum = |c: C3| (0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]) / 255.0;
        let deep = mix3(shadow, outline, 0.55);
        Cols {
            outline,
            shadow,
            base,
            hi,
            eye,
            pink: c3(p.pink),
            blue: c3(p.blue),
            yellow: c3(p.yellow),
            deep,
            spec: mix3(hi, WHITE, 0.38),
            eye_shade: mix3(eye, base, 0.17),
            ramp: [deep, shadow, base, hi, mix3(hi, WHITE, 0.75)],
            ch_top: mix3(base, WHITE, 0.18),
            ch_bot: mix3(base, BLACK, 0.35),
            ch_sheen: mix3(base, WHITE, 0.55),
            glow: lum(eye) > lum(base) + 0.15,
        }
    }
    fn ink(&self, i: Ink) -> C3 {
        match i {
            Ink::Outline => self.outline,
            Ink::Shadow => self.shadow,
            Ink::Base => self.base,
            Ink::Hi => self.hi,
            Ink::Eye => self.eye,
            Ink::Pink => self.pink,
            Ink::Blue => self.blue,
            Ink::Yellow => self.yellow,
        }
    }
}

/// One eye, resolved for the frame.
#[derive(Clone, Copy)]
struct EyeGeo {
    on: bool,
    ex: f32,
    ey: f32,
    ca: f32,
    sa: f32,
    ws: f32,
    fs: f32,
    side: f32,
    ea: Option<Eye>,
    eb: Eye,
    col: C3,
    orb: bool,
    /// Pupil: (offset x, offset y, half size).
    pupil: Option<(f32, f32, f32)>,
}

const BAYER: [u8; 16] = [0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5];
const CLAY_STOPS: [f32; 5] = [0.0, 0.3, 0.58, 0.9, 1.45];
/// Stroke width of the Outline and Neon looks, in ball radii.
const STROKE: f32 = 0.065;
/// Size of one prop bitmap cell in the smooth looks, in ball radii.
const PROP_CELL: f32 = 1.0 / (32.0 * 0.3);

/// A frame, ready to be sampled pixel by pixel.
struct Frame {
    a: Option<Pose>,
    b: Pose,
    m: f32,
    mode: Mode,
    aa: bool,
    outline: bool,
    neon: bool,
    line_ink: C3,
    cols: Cols,
    eyes: [EyeGeo; 2],
    face: bool,
    bl: f32,
    cx: f32,
    cy: f32,
    sx: f32,
    /// Grid looks: cells across, and cells per ball radius.
    grid: i32,
    gr: f32,
}

impl Frame {
    fn new(mascot: &Mascot, now_ms: u64, style: &Style, size: u32) -> Frame {
        let now_ms = if style.fps > 0 { now_ms - now_ms % (1000 / style.fps as u64).max(1) } else { now_ms };
        // Local clocks wrap hourly so f32 seconds keep sub-millisecond precision forever.
        let secs = |ms: u64| (ms % 3_600_000) as f32 / 1000.0;
        let since = now_ms.saturating_sub(mascot.t_cur);
        let b = mascot.cur.pose(secs(since)).finish(style.cat);
        let (a, m) = match mascot.prev {
            Some(prev) if since < MORPH_MS => (
                Some(prev.pose(secs(now_ms.saturating_sub(mascot.t_prev))).finish(style.cat)),
                settle(since as f32 / MORPH_MS as f32),
            ),
            _ => (None, 1.0),
        };
        let now = secs(now_ms);
        let seed = style.seed;

        let grid_look = style.look.is_grid();
        let grid = if grid_look { (style.grid as u32).clamp(8, size.max(8)) as i32 } else { 0 };
        let gr = grid as f32 * 0.3;
        let mode = match style.look {
            Look::Flat => Mode::Flat,
            Look::Chrome => Mode::Chrome,
            Look::Clay => Mode::Clay,
            Look::Outline | Look::Neon => Mode::Line,
            Look::Pixel | Look::Led => match style.pixel_shade {
                PixelShade::Lit => Mode::PixLit,
                PixelShade::Soft => Mode::PixSoft,
                PixelShade::Flat => Mode::PixFlat,
            },
        };
        let cols = Cols::new(&style.palette);
        let line_ink = if style.look == Look::Neon { cols.hi } else { cols.base };

        let l = |f: fn(&Pose) -> f32| match &a {
            Some(a) => lerp(f(a), f(&b), m),
            None => f(&b),
        };
        let front = match &a {
            Some(a) if m < 0.5 => a,
            _ => &b,
        };
        let face = match &a {
            Some(a) => lerp(a.face as u8 as f32, b.face as u8 as f32, m) > 0.5,
            None => b.face,
        };

        // --- life at rest: gaze wander in held jumps, and blinking
        let amp = if !style.life {
            0.0
        } else if front.still {
            0.25
        } else {
            1.0
        };
        let sac = floor(now / 1.9 + seed * 3.7);
        let yaw = l(|p| p.yaw) + amp * (wave(now, 11.3, seed) * 4.0 + (hash(sac + seed * 31.0) - 0.5) * 12.0);
        let pitch =
            l(|p| p.pitch) + amp * (wave(now, 9.1, seed + 0.3) * 2.5 + (hash(sac * 7.0 + seed * 17.0) - 0.5) * 6.0);
        let roll = l(|p| p.roll) * RAD;
        let mut split = l(|p| p.split);
        if style.eyes == EyeStyle::Grok {
            split = mx(split, 24.0);
        }
        let (fx, fy, fs) = (l(|p| p.fx), l(|p| p.fy), l(|p| p.fs));
        let (cx, cy, sx, sy) = (l(|p| p.cx), l(|p| p.cy), l(|p| p.sx), l(|p| p.sy));
        let rot = match &a {
            Some(a) => {
                // shortest way round
                let dr = rem(b.rot - a.rot + PI, TAU) - PI;
                a.rot + dr * m
            }
            None => b.rot,
        };
        let can_blink = b.blink && a.as_ref().map_or(true, |a| a.blink);
        let mut bl = 0.0;
        if can_blink && style.life {
            let w = 3.3;
            let tt = now + seed * 7.0;
            let bi = floor(tt / w);
            let k = (tt - bi * w - hash(bi + seed * 13.0) * (w - 0.3)) / 0.17;
            if (0.0..=1.0).contains(&k) {
                bl = if k < 0.45 { k / 0.45 } else { 1.0 - (k - 0.45) / 0.55 };
            }
        }

        let mut eyes = [EyeGeo {
            on: false,
            ex: 0.0,
            ey: 0.0,
            ca: 1.0,
            sa: 0.0,
            ws: 1.0,
            fs: 1.0,
            side: 1.0,
            ea: None,
            eb: b.el,
            col: cols.eye,
            orb: false,
            pupil: None,
        }; 2];
        if face {
            for (i, side) in [-1.0f32, 1.0].into_iter().enumerate() {
                let ya = (yaw + side * split) * RAD;
                let pi = pitch * RAD;
                let (ex, ey) = (sin(ya) * cos(pi), -sin(pi));
                let depth = cos(ya) * cos(pi);
                if depth < 0.1 {
                    continue;
                }
                let tx = (fx + (ex * cos(roll) - ey * sin(roll)) * fs) * sx;
                let ty = (fy + (ex * sin(roll) + ey * cos(roll)) * fs) * sy;
                let mut ex = tx * cos(rot) - ty * sin(rot) + cx;
                let mut ey = tx * sin(rot) + ty * cos(rot) + cy;
                if grid_look {
                    // Eyes land on cell boundaries, so both stay the same size as they move.
                    ex = round(ex * gr) / gr;
                    ey = round(ey * gr) / gr;
                }
                let pick = |p: &Pose| style.eyes.apply(if side < 0.0 { p.el } else { p.er });
                let eb = pick(&b);
                let ea = a.as_ref().map(pick);
                let g = match ea {
                    Some(ea) if m < 0.5 => ea,
                    _ => eb,
                };
                let tilt = match ea {
                    Some(ea) => lerp(ea.tilt, eb.tilt, m),
                    None => eb.tilt,
                } * RAD * side;
                let ang = roll + rot + tilt;
                let line = mode == Mode::Line;
                let col = if line && g.ink == Ink::Eye { line_ink } else { cols.ink(g.ink) };
                let pupil = if g.pupil && g.kind == EyeKind::Cap && bl < 0.3 {
                    let w = g.w * fs;
                    let dir = |v: f32| clamp(round(v / 9.0), -1.0, 1.0);
                    let (mut step, mut half) = (w / 5.0, w / 4.4);
                    if grid_look {
                        step = mx(1.0, round(step * gr)) / gr;
                        half = mx(1.0, round(half * 2.0 * gr)) / gr / 2.0;
                    }
                    Some((dir(yaw) * step, dir(-(pitch - 4.0)) * step, half))
                } else {
                    None
                };
                eyes[i] = EyeGeo {
                    on: true,
                    ex,
                    ey,
                    ca: cos(ang),
                    sa: sin(ang),
                    ws: mx(0.5, depth) * fs,
                    fs,
                    side,
                    ea,
                    eb,
                    col,
                    orb: !line && g.ink == Ink::Eye && g.orb,
                    pupil,
                };
            }
        }

        Frame {
            a,
            b,
            m,
            mode,
            aa: !grid_look,
            outline: grid_look && style.pixel_outline,
            neon: style.look == Look::Neon,
            line_ink,
            cols,
            eyes,
            face,
            bl,
            cx,
            cy,
            sx,
            grid,
            gr,
        }
    }

    fn front(&self) -> &Pose {
        match &self.a {
            Some(a) if self.m < 0.5 => a,
            _ => &self.b,
        }
    }

    #[inline]
    fn body(&self, x: f32, y: f32) -> f32 {
        let db = self.b.dist(x, y);
        match &self.a {
            Some(a) => lerp(a.dist(x, y), db, self.m),
            None => db,
        }
    }

    /// One grid cell of the Pixel and Led looks.
    fn sample_cell(&self, cx: u32, cy: u32) -> [f32; 4] {
        let half = self.grid as f32 / 2.0;
        self.sample(
            (cx as f32 + 0.5 - half) / self.gr,
            (cy as f32 + 0.5 - half) / self.gr,
            1.0 / self.gr,
            cx as i32,
            cy as i32,
        )
    }

    /// Colour at (x, y) in ball radii. `px` is the size of one pixel (or grid
    /// cell) in the same units; `ix`/`iy` are the cell indices for grid looks.
    fn sample(&self, x: f32, y: f32, px: f32, ix: i32, iy: i32) -> [f32; 4] {
        let mut o = [0.0f32; 4];
        // Nothing is ever drawn this far out; skip the work.
        if abs(x) > 1.72 || abs(y) > 1.72 {
            return o;
        }
        let aa = self.aa;
        let front = self.front();
        let cov_of = |d: f32| if aa { sat(0.5 - d / px) } else { (d < 0.0) as u8 as f32 };

        // --- ground shadow
        match self.mode {
            Mode::Chrome | Mode::Clay => {
                let (xx, yy) = ((x - self.cx) / 0.95, (y - 0.99) / 0.27);
                let e = xx * xx + yy * yy;
                if e < 1.0 {
                    over(&mut o, BLACK, 0.34 * (1.0 - e));
                }
            }
            Mode::PixLit => {
                let gw = mx(0.25, 0.8 - mx(0.0, -self.cy) * 1.6) * clamp(self.sx, 0.2, 1.2);
                let (xx, yy) = ((x - self.cx) / gw, (y - 1.2) / 0.11);
                if xx * xx + yy * yy < 1.0 {
                    over(&mut o, BLACK, 0.42);
                }
            }
            _ => {}
        }

        // --- rings and floating particles sit behind the body
        if !front.rings.is_empty() {
            let rr = hypot(x, y);
            let th = mx(0.045, 0.55 * px);
            for r in front.rings.iter() {
                over(&mut o, self.cols.base, cov_of(abs(rr - r) - th));
            }
        }
        for q in front.dots.iter().filter(|q| !q.on) {
            let rr = mx(q.r, 0.55 * px);
            over(&mut o, self.cols.ink(q.ink), cov_of(hypot(x - q.x, y - q.y) - rr));
        }

        // --- body
        let d = self.body(x, y);
        if self.neon {
            let ds = abs(d + STROKE / 2.0) - STROKE / 2.0;
            if ds > 0.0 && ds < 0.5 {
                over(&mut o, self.cols.hi, 0.5 * exp_neg(ds / 0.07));
            }
        }
        let cov = cov_of(d);
        if cov > 0.0 {
            let (mut col, mut alpha, edge) = self.body_colour(x, y, px, d, cov, ix, iy);
            if !edge {
                if self.face {
                    let (mut ecov, mut ecol, mut glow) = (0.0f32, self.cols.eye, 0.0f32);
                    for e in self.eyes.iter().filter(|e| e.on) {
                        let (dx, dy) = (x - e.ex, y - e.ey);
                        if abs(dx) > 0.8 || abs(dy) > 0.8 {
                            continue;
                        }
                        let u = (dx * e.ca + dy * e.sa) / e.ws;
                        let v = (-dx * e.sa + dy * e.ca) / e.fs;
                        let pxu = 0.55 * px / e.fs;
                        let db = e.eb.dist(u, v, e.side, pxu, self.bl);
                        let de = match &e.ea {
                            Some(ea) => lerp(ea.dist(u, v, e.side, pxu, self.bl), db, self.m),
                            None => db,
                        };
                        if self.mode == Mode::Chrome && de >= 0.0 && de < 0.14 {
                            let k = 1.0 - de / 0.14;
                            glow = mx(glow, 0.3 * k * k);
                        }
                        let c = if aa { sat(0.5 - de * e.fs / px) } else { (de < 0.0) as u8 as f32 };
                        if c <= ecov {
                            continue;
                        }
                        ecov = c;
                        ecol = e.col;
                        if e.orb && de < 0.0 {
                            // orb depth: a soft shaded crescent along the bottom
                            let (ou, ov) = (u / (e.eb.w * 0.5), (v + e.eb.h * 0.07) / (e.eb.h * 0.48));
                            if ou * ou + ov * ov > 1.0 {
                                ecol = self.cols.eye_shade;
                            }
                        }
                        if let Some((ox, oy, half)) = e.pupil {
                            let (qx, qy) = (dx - ox, dy - oy);
                            let inside = if aa { hypot(qx, qy) < half } else { abs(qx) < half && abs(qy) < half };
                            if inside && c > 0.5 {
                                ecol = self.cols.outline;
                            }
                        }
                    }
                    if glow > 0.0 && self.cols.glow {
                        col = mix3(col, self.cols.eye, glow);
                    }
                    if ecov > 0.0 {
                        col = mix3(col, ecol, ecov / (ecov + alpha * (1.0 - ecov)));
                        alpha = ecov + alpha * (1.0 - ecov);
                    }
                }
                for q in front.dots.iter().filter(|q| q.on) {
                    let c = cov_of(hypot(x - q.x, y - q.y) - mx(q.r, 0.55 * px));
                    if c > 0.0 {
                        col = mix3(col, self.cols.ink(q.ink), c / (c + alpha * (1.0 - c)));
                        alpha = c + alpha * (1.0 - c);
                    }
                }
            }
            over(&mut o, col, alpha);
        }

        // --- props on top
        for q in front.props.iter() {
            let ink = self.cols.ink(q.ink);
            if aa {
                self.prop_smooth(&mut o, q.sprite, x - q.x, y - q.y, px, ink);
            } else {
                self.prop_cell(&mut o, q.sprite, q.x, q.y, ix, iy, ink);
            }
        }
        o
    }

    /// Body colour, coverage and whether this is an outline pixel.
    fn body_colour(&self, x: f32, y: f32, px: f32, d: f32, cov: f32, ix: i32, iy: i32) -> (C3, f32, bool) {
        let c = &self.cols;
        let aa = self.aa;
        match self.mode {
            Mode::Flat => return (c.base, cov, false),
            Mode::Line => {
                let inner = if aa { sat(0.5 + (d + STROKE) / px) } else { (d > -STROKE) as u8 as f32 };
                return (self.line_ink, cov * inner, false);
            }
            Mode::PixFlat if !self.outline => return (c.base, cov, false),
            _ => {}
        }
        // The smooth looks read the slope over a wide span, which rounds off the
        // crease a distance field has down the middle of pointed shapes.
        let h = if aa { 0.13 } else { px };
        let (l, r) = (self.body(x - h, y), self.body(x + h, y));
        let (u, dn) = (self.body(x, y - h), self.body(x, y + h));
        if self.outline && (l >= 0.0 || r >= 0.0 || u >= 0.0 || dn >= 0.0) {
            return (c.outline, 1.0, true);
        }
        if self.mode == Mode::PixFlat {
            return (c.base, cov, false);
        }
        let (gx, gy) = (r - l, dn - u);
        let len = if aa { mx(2.0 * h, hypot(gx, gy)) } else { mx(hypot(gx, gy), 1e-6) };
        let bayer = BAYER[((ix & 3) + (iy & 3) * 4) as usize] as f32 / 16.0 - 0.5;
        if self.mode == Mode::PixSoft {
            let rim = sat(1.0 + d / 0.7);
            let s = (-(gx * 0.6 + gy * 0.8) / len) * rim * rim + bayer * 0.5;
            let col = if s > 0.4 {
                c.hi
            } else if s < -0.36 {
                c.shadow
            } else {
                c.base
            };
            return (col, cov, false);
        }
        // Inflate the silhouette into a dome: depth inside the edge becomes
        // height, which gives a surface normal to light. A ball is a true sphere.
        let t = clamp(-d, 0.02, 1.0);
        let z = sqrt(1.0 - (1.0 - t) * (1.0 - t));
        let (mut nx, mut ny, mut nz) = (gx / len * (1.0 - t), gy / len * (1.0 - t), z);
        let nl = mx(sqrt(nx * nx + ny * ny + nz * nz), 1e-6);
        nx /= nl;
        ny /= nl;
        nz /= nl;
        let diff = mx(0.0, nx * -0.4 + ny * -0.75 + nz * 0.53);
        let spec = nx * -0.229 + ny * -0.429 + nz * 0.875;
        match self.mode {
            Mode::PixLit => {
                let sv = 0.8 * diff + 0.2 * nz + bayer * 0.16;
                let col = if spec > 0.992 {
                    c.spec
                } else if sv < 0.2 {
                    c.deep
                } else if sv < 0.42 {
                    c.shadow
                } else if sv < 0.74 {
                    c.base
                } else {
                    c.hi
                };
                (col, cov, false)
            }
            Mode::Clay => {
                let v = clamp(0.8 * diff + 0.2 * nz + sat((spec - 0.95) / 0.04) * 0.55, 0.0, 1.449);
                let mut k = 0;
                while v > CLAY_STOPS[k + 1] {
                    k += 1;
                }
                let f = (v - CLAY_STOPS[k]) / (CLAY_STOPS[k + 1] - CLAY_STOPS[k]);
                (mix3(c.ramp[k], c.ramp[k + 1], f), cov, false)
            }
            _ => {
                // Chrome: lit-from-above gradient, soft highlight, rim catch-light.
                let ty = sat((y + 1.0) / 2.0);
                let mut col = if ty < 0.5 {
                    mix3(c.ch_top, c.base, ty * 2.0)
                } else {
                    mix3(c.base, c.ch_bot, (ty - 0.5) * 2.0)
                };
                let sp = sat((spec - 0.9) / 0.09);
                col = mix3(col, WHITE, 0.3 * sp * sp);
                let rim = sat((d + 0.05) / 0.03);
                col = mix3(col, c.ch_sheen, 0.9 * sat(1.0 - (y + 1.0) / 1.25) * rim);
                (col, cov, false)
            }
        }
    }

    /// Smooth looks: blend the bitmap between cell centres and cut it at half
    /// strength, so corners round off and diagonals join like a traced pixel font.
    fn prop_smooth(&self, o: &mut [f32; 4], s: crate::sprite::Sprite, dx: f32, dy: f32, px: f32, ink: C3) {
        let rows = s.rows();
        let (h, w) = (rows.len() as f32, rows[0].len() as f32);
        let (u, v) = (dx / PROP_CELL + w / 2.0, dy / PROP_CELL + h / 2.0);
        if u < -1.2 || v < -1.2 || u > w + 1.2 || v > h + 1.2 {
            return;
        }
        let (fu, fv) = (u - 0.5, v - 0.5);
        let (q0, r0) = (floor(fu), floor(fv));
        let (tu, tv) = (fu - q0, fv - r0);
        let (mut den, mut dark) = (0.0, 0.0);
        for a in 0..2 {
            for b in 0..2 {
                let ch = s.cell(r0 as i32 + a, q0 as i32 + b);
                if ch == b'.' {
                    continue;
                }
                let wgt = (if a == 1 { tv } else { 1.0 - tv }) * (if b == 1 { tu } else { 1.0 - tu });
                den += wgt;
                if ch == b'o' {
                    dark += wgt;
                }
            }
        }
        let k = PROP_CELL / px * 1.4;
        over(o, self.cols.outline, sat(0.5 + (den - 0.14) * k));
        if dark <= 0.5 {
            over(o, ink, sat(0.5 + (den - 0.5) * k));
        }
    }

    /// Grid looks: the bitmap as-is, with a one-cell dark rim so it reads on anything.
    fn prop_cell(&self, o: &mut [f32; 4], s: crate::sprite::Sprite, x: f32, y: f32, ix: i32, iy: i32, ink: C3) {
        let rows = s.rows();
        let (h, w) = (rows.len() as i32, rows[0].len() as i32);
        let k = mx(1.0, round(self.grid as f32 / 32.0)) as i32;
        let half = self.grid as f32 / 2.0;
        let x0 = round(x * self.gr + half - (w * k) as f32 / 2.0) as i32;
        let y0 = round(y * self.gr + half - (h * k) as f32 / 2.0) as i32;
        let (lx, ly) = (ix - x0, iy - y0);
        if lx < -1 || ly < -1 || lx > w * k || ly > h * k {
            return;
        }
        let at = |lx: i32, ly: i32| s.cell(ly.div_euclid(k), lx.div_euclid(k));
        match at(lx, ly) {
            b'#' => *o = [ink[0], ink[1], ink[2], 1.0],
            b'o' => *o = [self.cols.outline[0], self.cols.outline[1], self.cols.outline[2], 1.0],
            _ => {
                for (ax, ay) in [(-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (0, 1), (1, 1)] {
                    if at(lx + ax, ly + ay) != b'.' {
                        *o = [self.cols.outline[0], self.cols.outline[1], self.cols.outline[2], 1.0];
                        return;
                    }
                }
            }
        }
    }
}
