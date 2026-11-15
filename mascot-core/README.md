# mascot-core

The Material Mascot renderer in Rust: **no dependencies, `no_std`, no heap, no `unsafe`.**
It is a pure function from *(state, time, style)* to pixels, so the same code runs on a
desktop, in WebAssembly, or on a microcontroller driving a small colour display.

```rust
use mascot_core::{Mascot, State, Style, Look, Palette, Rgb};

let mut mascot = Mascot::new(State::Idle);
mascot.set_state(State::Happy, now_ms);            // morphs over half a second

// Stream pixels straight to a display: no framebuffer needed.
mascot.render(now_ms, 64, &Style::DEFAULT, |x, y, c| {
    display.set_pixel(x, y, c.over(Rgb::BLACK).to_565());
});

// Or fill a buffer you already have.
let mut frame = [0u16; 64 * 64];
let style = Style { look: Look::Pixel, palette: Palette::INK, ..Style::DEFAULT };
mascot.render_rgb565(now_ms, 64, &style, Rgb::BLACK, &mut frame);
```

`now_ms` is any millisecond counter. The same inputs always give the same frame, so
pausing, seeking and slow motion need nothing special.

## What you can set

| | Options |
|---|---|
| `State` | 49 states: 23 emotions, 18 objects, and the 8 shape morphs of the Kotlin library. `State::ALL`, `name()`, `from_name()`. |
| `Look` | `Flat` (default), `Chrome`, `Outline`, `Clay`, `Neon`, `Pixel`, `Led` |
| `EyeStyle` | `Grok` (default), `Classic`, `Big`, `Round`, `Pupil`, `Block`, `Visor`, `Dot` |
| `Palette` | `GRAPE`, `INK`, `EMBER`, `MINT`, `HANDHELD`, or `Palette::from_body(body, eye)` |
| `Style` | plus `grid` and `pixel_shade` for the pixel looks, `cat` ears, `life` (idle gaze and blinking), `fps` stepping, `seed` |

## On embedded devices

- **Memory**: nothing is allocated and no framebuffer is kept. Each pixel is computed on
  its own from the frame's pose, which lives on the stack.
- **Maths**: `sin`, `sqrt`, `atan2` and friends are implemented in the crate, so there is
  no `libm` to link. The crate builds for `thumbv7em-none-eabihf`, `thumbv6m-none-eabi`
  and `wasm32-unknown-unknown`.
- **Speed**: everything is `f32`. On a chip with a hardware FPU the smooth looks are
  practical at small sizes. Without one (Cortex-M0 class) it still builds, but soft-float
  will be slow: prefer `Look::Pixel` with a small `grid`, which costs per grid cell, not
  per output pixel. Not yet measured on real hardware.
- `Flat`, `Outline` and `Neon` evaluate the body once per pixel; `Chrome`, `Clay` and the
  lit pixel shading evaluate it five times for covered pixels.

## Try it

```sh
cargo test
cargo run --release --example term -- pixel 48     # live in a truecolour terminal
cargo run --release --example sheet -- sheet.ppm clay grok grape 128
```

## Relationship to the Kotlin library

The Compose library in this repository ([`../mascot`](../README.md)) is a thin wrapper
around this crate: it calls the renderer through [`mascot-jni`](../mascot-jni) and shows
the frame in a Compose `Canvas`. State ids are the positions in `State::ALL`, which is
the ABI the wrapper relies on, so new states must be appended, not inserted.

The original Kotlin engine drew bodies as 64-point radial profiles. Here they are signed
distance fields, so any shape can morph into any other, including ones a radial profile
cannot draw (crescent moon, magnifier, check mark). Eyes have real shapes (arcs, hearts,
slanted lids, chevrons, rings) and the faces were re-authored facing the viewer.
