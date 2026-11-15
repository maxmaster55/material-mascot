//! Renders an animation as a stream of binary PPM frames (no dependencies), ready to be
//! turned into a GIF. `tools/make-gifs.sh` drives this to produce the README images.
//!
//!     cargo run --release --example frames -- out.ppm <states> [look] [eyes] [palette] [size] [fps] [ms]
//!
//! states: one name ("happy") or a comma list ("idle,happy,heart"). A list is shown as a
//!         loop that morphs from each state to the next, holding each for `ms`.
//! ms:     length of the clip for one state, or the hold per state for a list.

use mascot_core::{EyeStyle, Look, Mascot, Palette, State, Style};
use std::io::Write;

fn main() -> std::io::Result<()> {
    let args: Vec<String> = std::env::args().collect();
    let arg = |i: usize, d: &str| args.get(i).cloned().unwrap_or_else(|| d.to_string());
    let path = arg(1, "frames.ppm");
    let states: Vec<State> = arg(2, "idle")
        .split(',')
        .map(|n| State::from_name(n).unwrap_or_else(|| panic!("unknown state: {n}")))
        .collect();
    let look = match arg(3, "flat").as_str() {
        "chrome" => Look::Chrome,
        "outline" => Look::Outline,
        "clay" => Look::Clay,
        "neon" => Look::Neon,
        "pixel" => Look::Pixel,
        "led" => Look::Led,
        _ => Look::Flat,
    };
    let eyes = match arg(4, "grok").as_str() {
        "classic" => EyeStyle::Classic,
        "big" => EyeStyle::Big,
        "round" => EyeStyle::Round,
        "pupil" => EyeStyle::Pupil,
        "block" => EyeStyle::Block,
        "visor" => EyeStyle::Visor,
        "dot" => EyeStyle::Dot,
        _ => EyeStyle::Grok,
    };
    let palette = match arg(5, "grape").as_str() {
        "ink" => Palette::INK,
        "ember" => Palette::EMBER,
        "mint" => Palette::MINT,
        "handheld" => Palette::HANDHELD,
        _ => Palette::GRAPE,
    };
    let size: u32 = arg(6, "128").parse().unwrap_or(128);
    let fps: u64 = arg(7, "20").parse().unwrap_or(20);
    let ms: u64 = arg(8, "3000").parse().unwrap_or(3000);

    let style = Style { look, eyes, palette, ..Style::DEFAULT };
    let bg = palette.bg;
    let total = ms * states.len() as u64;
    let frames = total * fps / 1000;

    // Start as if the last state had just handed over to the first, so a list loops cleanly.
    let mut mascot = Mascot::new(*states.last().unwrap());
    if states.len() > 1 {
        mascot.set_state(states[0], 0);
    }
    let mut shown = 0usize;

    let mut f = std::io::BufWriter::new(std::fs::File::create(&path)?);
    let mut img = vec![0u8; (size * size * 3) as usize];
    for i in 0..frames {
        let now = i * 1000 / fps;
        let want = (now / ms) as usize;
        if want != shown {
            shown = want;
            mascot.set_state(states[want], want as u64 * ms);
        }
        for p in img.chunks_exact_mut(3) {
            p.copy_from_slice(&[bg.0, bg.1, bg.2]);
        }
        mascot.render(now, size, &style, |x, y, c| {
            let rgb = c.over(bg);
            let k = ((y * size + x) * 3) as usize;
            img[k..k + 3].copy_from_slice(&[rgb.0, rgb.1, rgb.2]);
        });
        write!(f, "P6\n{size} {size}\n255\n")?;
        f.write_all(&img)?;
    }
    eprintln!("wrote {path}: {frames} frames, {size}x{size}, {fps} fps");
    Ok(())
}
