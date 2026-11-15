//! A living mascot you can draw anywhere.
//!
//! `mascot-core` has **no dependencies**, is `no_std`, never allocates and
//! contains no `unsafe`. It is a pure function from *(state, time, style)* to
//! pixels, so it runs the same on a desktop, in WebAssembly, or on a
//! microcontroller driving a small colour display.
//!
//! ```
//! use mascot_core::{Mascot, State, Style, Rgb};
//!
//! let mut mascot = Mascot::new(State::Idle);
//! mascot.set_state(State::Happy, 1_000);          // morphs over half a second
//!
//! // 64x64 RGB565 frame for an embedded display, composited on black.
//! let mut frame = [0u16; 64 * 64];
//! mascot.render_rgb565(1_250, 64, &Style::DEFAULT, Rgb::BLACK, &mut frame);
//! ```
//!
//! Rendering keeps no framebuffer: [`Mascot::render`] hands each pixel to a
//! closure, so you can stream straight to a display and use only a few hundred
//! bytes of stack.
//!
//! Time is whatever millisecond counter you have. The same inputs always give
//! the same frame.

#![no_std]
#![forbid(unsafe_code)]

mod eye;
pub mod math;
mod palette;
mod render;
pub mod sdf;
mod shape;
mod sprite;
mod state;

pub use eye::EyeStyle;
pub use palette::{Ink, Palette, Rgb, Rgba8};
pub use render::{Look, Mascot, PixelShade, Style, MORPH_MS};
pub use shape::Shape;
pub use state::State;
