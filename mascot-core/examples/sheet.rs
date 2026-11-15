//! Renders every state into one contact-sheet image (binary PPM, no dependencies).
//!
//!     cargo run --release --example sheet -- out.ppm [look] [eyes] [palette] [tile] [ms]
//!
//! look: flat chrome outline clay neon pixel led      eyes: grok classic big round pupil block visor dot
//! palette: grape ink ember mint handheld

use mascot_core::{EyeStyle, Look, Mascot, Palette, State, Style};
use std::io::Write;

fn main() -> std::io::Result<()> {
    let args: Vec<String> = std::env::args().collect();
    let arg = |i: usize, d: &str| args.get(i).cloned().unwrap_or_else(|| d.to_string());
    let path = arg(1, "sheet.ppm");
    let look = match arg(2, "flat").as_str() {
        "chrome" => Look::Chrome,
        "outline" => Look::Outline,
        "clay" => Look::Clay,
        "neon" => Look::Neon,
        "pixel" => Look::Pixel,
        "led" => Look::Led,
        _ => Look::Flat,
    };
    let eyes = match arg(3, "grok").as_str() {
        "classic" => EyeStyle::Classic,
        "big" => EyeStyle::Big,
        "round" => EyeStyle::Round,
        "pupil" => EyeStyle::Pupil,
        "block" => EyeStyle::Block,
        "visor" => EyeStyle::Visor,
        "dot" => EyeStyle::Dot,
        _ => EyeStyle::Grok,
    };
    let palette = match arg(4, "grape").as_str() {
        "ink" => Palette::INK,
        "ember" => Palette::EMBER,
        "mint" => Palette::MINT,
        "handheld" => Palette::HANDHELD,
        _ => Palette::GRAPE,
    };
    let tile: u32 = arg(5, "128").parse().unwrap_or(128);
    let ms: u64 = arg(6, "900").parse().unwrap_or(900);

    // life off: a contact sheet should not catch anyone mid-blink
    let style = Style { look, eyes, palette, life: false, ..Style::DEFAULT };
    let cols = 7u32;
    let rows = (State::ALL.len() as u32).div_ceil(cols);
    let (w, h) = (cols * tile, rows * tile);
    let bg = palette.bg;
    let mut img = vec![0u8; (w * h * 3) as usize];
    for p in img.chunks_exact_mut(3) {
        p.copy_from_slice(&[bg.0, bg.1, bg.2]);
    }
    for (i, &state) in State::ALL.iter().enumerate() {
        let (ox, oy) = ((i as u32 % cols) * tile, (i as u32 / cols) * tile);
        Mascot::new(state).render(ms, tile, &style, |x, y, c| {
            let rgb = c.over(bg);
            let k = (((oy + y) * w + ox + x) * 3) as usize;
            img[k..k + 3].copy_from_slice(&[rgb.0, rgb.1, rgb.2]);
        });
    }
    let mut f = std::io::BufWriter::new(std::fs::File::create(&path)?);
    write!(f, "P6\n{w} {h}\n255\n")?;
    f.write_all(&img)?;
    eprintln!("wrote {path}: {} states, {w}x{h}", State::ALL.len());
    Ok(())
}
