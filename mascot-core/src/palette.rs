//! Colours: plain 8-bit types, ready for RGB565 panels.

/// Opaque 8-bit colour.
#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub struct Rgb(pub u8, pub u8, pub u8);

impl Rgb {
    pub const BLACK: Rgb = Rgb(0, 0, 0);
    pub const WHITE: Rgb = Rgb(255, 255, 255);

    /// From 0xRRGGBB.
    pub const fn hex(v: u32) -> Rgb {
        Rgb((v >> 16) as u8, (v >> 8) as u8, v as u8)
    }
    /// Packed 5-6-5, the native format of most small colour displays.
    pub const fn to_565(self) -> u16 {
        ((self.0 as u16 & 0xf8) << 8) | ((self.1 as u16 & 0xfc) << 3) | (self.2 as u16 >> 3)
    }
    /// Blend toward `other`; `t` is 0..=256.
    pub const fn mix(self, other: Rgb, t: u32) -> Rgb {
        const fn ch(a: u8, b: u8, t: u32) -> u8 {
            ((a as u32 * (256 - t) + b as u32 * t) >> 8) as u8
        }
        Rgb(ch(self.0, other.0, t), ch(self.1, other.1, t), ch(self.2, other.2, t))
    }
}

/// One rendered pixel, straight (non-premultiplied) alpha.
#[derive(Clone, Copy, PartialEq, Eq, Debug, Default)]
pub struct Rgba8 {
    pub r: u8,
    pub g: u8,
    pub b: u8,
    pub a: u8,
}

impl Rgba8 {
    /// Flatten onto an opaque background.
    pub const fn over(self, bg: Rgb) -> Rgb {
        let a = self.a as u32;
        const fn ch(f: u8, b: u8, a: u32) -> u8 {
            ((f as u32 * a + b as u32 * (255 - a) + 127) / 255) as u8
        }
        Rgb(ch(self.r, bg.0, a), ch(self.g, bg.1, a), ch(self.b, bg.2, a))
    }
}

/// A named slot in the [`Palette`].
#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum Ink {
    Outline,
    Shadow,
    Base,
    Hi,
    Eye,
    Pink,
    Blue,
    Yellow,
}

/// The mascot's colours. Lighting tones not listed here are derived from these.
#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub struct Palette {
    /// Darkest tone: pixel-art outline and prop rims.
    pub outline: Rgb,
    /// Body in shade.
    pub shadow: Rgb,
    /// Body colour. The only tone the Flat look uses.
    pub base: Rgb,
    /// Body in light.
    pub hi: Rgb,
    /// Eye colour.
    pub eye: Rgb,
    /// Hearts, blush, alerts.
    pub pink: Rgb,
    /// Tears, sleep, question marks.
    pub blue: Rgb,
    /// Sparkles and stars.
    pub yellow: Rgb,
    /// Suggested background behind the mascot. The renderer never paints it.
    pub bg: Rgb,
}

impl Palette {
    pub const GRAPE: Palette = Palette {
        outline: Rgb::hex(0x1a0f3a),
        shadow: Rgb::hex(0x4b2ea0),
        base: Rgb::hex(0x7450e0),
        hi: Rgb::hex(0xa689ff),
        eye: Rgb::hex(0xffffff),
        pink: Rgb::hex(0xff6b9d),
        blue: Rgb::hex(0x6bd6ff),
        yellow: Rgb::hex(0xffd84a),
        bg: Rgb::hex(0x15122b),
    };
    /// The original library look: near-black body, white eyes.
    pub const INK: Palette = Palette {
        outline: Rgb::hex(0x020206),
        shadow: Rgb::hex(0x0e0e16),
        base: Rgb::hex(0x1e1e2b),
        hi: Rgb::hex(0x41415a),
        eye: Rgb::hex(0xffffff),
        pink: Rgb::hex(0xff5a7a),
        blue: Rgb::hex(0x3aa8f5),
        yellow: Rgb::hex(0xf5b921),
        bg: Rgb::hex(0xe9e5da),
    };
    pub const EMBER: Palette = Palette {
        outline: Rgb::hex(0x2b0a05),
        shadow: Rgb::hex(0xa02c10),
        base: Rgb::hex(0xe8591a),
        hi: Rgb::hex(0xffab3d),
        eye: Rgb::hex(0xfff4d6),
        pink: Rgb::hex(0xff7aa8),
        blue: Rgb::hex(0x7fd8ff),
        yellow: Rgb::hex(0xfff07a),
        bg: Rgb::hex(0x1c1210),
    };
    pub const MINT: Palette = Palette {
        outline: Rgb::hex(0x06302b),
        shadow: Rgb::hex(0x138a72),
        base: Rgb::hex(0x2fd0a5),
        hi: Rgb::hex(0xa8f5d8),
        eye: Rgb::hex(0x082a25),
        pink: Rgb::hex(0xff6b9d),
        blue: Rgb::hex(0x57b4ff),
        yellow: Rgb::hex(0xffe066),
        bg: Rgb::hex(0x0e1f1d),
    };
    /// Four greens, for monochrome-style handheld screens.
    pub const HANDHELD: Palette = Palette {
        outline: Rgb::hex(0x0f380f),
        shadow: Rgb::hex(0x306230),
        base: Rgb::hex(0x306230),
        hi: Rgb::hex(0x8bac0f),
        eye: Rgb::hex(0x9bbc0f),
        pink: Rgb::hex(0x0f380f),
        blue: Rgb::hex(0x0f380f),
        yellow: Rgb::hex(0x0f380f),
        bg: Rgb::hex(0x9bbc0f),
    };

    /// Build a palette from just a body and an eye colour, the two the
    /// original library asked for. Shading tones are derived.
    pub const fn from_body(body: Rgb, eye: Rgb) -> Palette {
        Palette {
            outline: body.mix(Rgb::BLACK, 170),
            shadow: body.mix(Rgb::BLACK, 90),
            base: body,
            hi: body.mix(Rgb::WHITE, 80),
            eye,
            pink: Rgb::hex(0xff6b9d),
            blue: Rgb::hex(0x6bd6ff),
            yellow: Rgb::hex(0xffd84a),
            bg: Rgb::hex(0x15122b),
        }
    }

    pub const fn ink(&self, ink: Ink) -> Rgb {
        match ink {
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
