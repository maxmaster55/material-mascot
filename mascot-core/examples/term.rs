//! Live mascot in a truecolour terminal, touring every state (no dependencies).
//!
//!     cargo run --release --example term -- [look] [cells]
//!
//! Each character cell shows two pixels using the upper-half-block glyph.
//! Ctrl-C to quit.

use mascot_core::{Look, Mascot, State, Style};
use std::io::Write;
use std::time::{Duration, Instant};

fn main() {
    let args: Vec<String> = std::env::args().collect();
    let look = match args.get(1).map(String::as_str) {
        Some("chrome") => Look::Chrome,
        Some("outline") => Look::Outline,
        Some("clay") => Look::Clay,
        Some("neon") => Look::Neon,
        Some("flat") => Look::Flat,
        Some("led") => Look::Led,
        _ => Look::Pixel,
    };
    let size: u32 = args.get(2).and_then(|s| s.parse().ok()).unwrap_or(48) & !1;
    let style = Style { look, grid: size.min(64) as u16, ..Style::DEFAULT };
    let bg = style.palette.bg;

    let mut mascot = Mascot::new(State::Idle);
    let start = Instant::now();
    let mut next = 0usize;
    let mut buf = vec![bg; (size * size) as usize];
    let mut out = String::new();
    print!("\x1b[2J\x1b[?25l");
    loop {
        let now = start.elapsed().as_millis() as u64;
        if now / 2600 != next as u64 {
            next = (now / 2600) as usize;
            mascot.set_state(State::ALL[next % State::ALL.len()], now);
        }
        mascot.render(now, size, &style, |x, y, c| buf[(y * size + x) as usize] = c.over(bg));
        out.clear();
        out.push_str("\x1b[H");
        for y in (0..size).step_by(2) {
            for x in 0..size {
                let (t, b) = (buf[(y * size + x) as usize], buf[((y + 1) * size + x) as usize]);
                out.push_str(&format!("\x1b[38;2;{};{};{}m\x1b[48;2;{};{};{}m\u{2580}", t.0, t.1, t.2, b.0, b.1, b.2));
            }
            out.push_str("\x1b[0m\n");
        }
        out.push_str(&format!("\x1b[0m\x1b[K {}\n", mascot.state().name()));
        let mut so = std::io::stdout().lock();
        let _ = so.write_all(out.as_bytes());
        let _ = so.flush();
        std::thread::sleep(Duration::from_millis(40));
    }
}
