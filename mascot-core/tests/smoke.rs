use mascot_core::{math, EyeStyle, Look, Mascot, Palette, PixelShade, Rgb, State, Style};

const LOOKS: [Look; 7] = [Look::Flat, Look::Chrome, Look::Outline, Look::Clay, Look::Neon, Look::Pixel, Look::Led];

fn frame(m: &Mascot, ms: u64, size: u32, style: &Style) -> Vec<u8> {
    let mut buf = vec![0u8; (size * size * 4) as usize];
    m.render_rgba8888(ms, size, style, &mut buf);
    buf
}

#[test]
fn every_state_draws_something_in_every_look() {
    for &state in State::ALL {
        for look in LOOKS {
            let style = Style { look, ..Style::DEFAULT };
            // Burst and Comet shrink to a speck mid-cycle; sample when they are whole.
            let buf = frame(&Mascot::new(state), 2_500, 48, &style);
            let covered = buf.chunks_exact(4).filter(|p| p[3] > 0).count();
            assert!(covered > 20, "{} in {look:?} drew only {covered} pixels", state.name());
        }
    }
}

#[test]
fn same_time_gives_the_same_frame() {
    let mut m = Mascot::new(State::Idle);
    m.set_state(State::Love, 1_000);
    for look in LOOKS {
        let style = Style { look, ..Style::DEFAULT };
        assert_eq!(frame(&m, 1_230, 40, &style), frame(&m, 1_230, 40, &style));
    }
}

#[test]
fn a_morph_is_in_between_and_then_settles() {
    let style = Style { life: false, ..Style::DEFAULT };
    let from = frame(&Mascot::new(State::Idle), 5_000, 48, &style);
    let mut m = Mascot::new(State::Idle);
    m.set_state(State::Star, 5_000);
    let mid = frame(&m, 5_250, 48, &style);
    assert_ne!(mid, from, "morph has not started");
    assert_ne!(mid, frame(&Mascot::new(State::Star), 250, 48, &style), "morph jumped straight to the target");
    // after the morph the frame depends only on the new state and its own clock
    let mut fresh = Mascot::new(State::Hexagon);
    fresh.set_state(State::Star, 5_000);
    assert_eq!(frame(&m, 6_000, 48, &style), frame(&fresh, 6_000, 48, &style));
}

#[test]
fn eye_styles_and_palettes_change_the_picture() {
    let base = frame(&Mascot::new(State::Idle), 900, 64, &Style { life: false, ..Style::DEFAULT });
    for eyes in [EyeStyle::Classic, EyeStyle::Big, EyeStyle::Round, EyeStyle::Pupil, EyeStyle::Block, EyeStyle::Visor, EyeStyle::Dot] {
        let s = Style { eyes, life: false, ..Style::DEFAULT };
        assert_ne!(frame(&Mascot::new(State::Idle), 900, 64, &s), base, "{eyes:?} looks like Grok");
    }
    let ink = Style { palette: Palette::INK, life: false, ..Style::DEFAULT };
    assert_ne!(frame(&Mascot::new(State::Idle), 900, 64, &ink), base);
}

#[test]
fn pixel_look_is_hard_edged_and_smooth_looks_are_not() {
    let partial = |look| {
        let style = Style { look, pixel_shade: PixelShade::Flat, ..Style::DEFAULT };
        frame(&Mascot::new(State::Idle), 900, 64, &style).chunks_exact(4).filter(|p| p[3] > 0 && p[3] < 255).count()
    };
    assert_eq!(partial(Look::Pixel), 0);
    assert!(partial(Look::Flat) > 40);
}

#[test]
fn rgb565_output_fills_the_buffer() {
    let mut buf = [0u16; 32 * 32];
    Mascot::new(State::Happy).render_rgb565(900, 32, &Style::DEFAULT, Rgb::BLACK, &mut buf);
    assert!(buf.iter().filter(|&&p| p != 0).count() > 100);
    // a short buffer must not panic
    let mut short = [0u16; 10];
    Mascot::new(State::Happy).render_rgb565(900, 32, &Style::DEFAULT, Rgb::BLACK, &mut short);
}

#[test]
fn long_uptime_does_not_break_rendering() {
    let style = Style::DEFAULT;
    for ms in [0u64, 3_599_999, 3_600_000, 86_400_000 * 400, u64::MAX / 2] {
        let buf = frame(&Mascot::new(State::Hourglass), ms, 32, &style);
        assert!(buf.chunks_exact(4).any(|p| p[3] > 0), "nothing drawn at {ms} ms");
    }
}

#[test]
fn names_round_trip() {
    for &s in State::ALL {
        assert_eq!(State::from_name(s.name()), Some(s));
    }
    assert_eq!(State::from_name("nope"), None);
    assert_eq!(State::ALL.len(), 49);
}

#[test]
fn homemade_maths_tracks_the_real_thing() {
    for i in -2000..2000 {
        let x = i as f32 * 0.01;
        assert!((math::sin(x) - x.sin()).abs() < 2e-5, "sin({x})");
        assert!((math::cos(x) - x.cos()).abs() < 2e-5, "cos({x})");
        assert_eq!(math::floor(x), x.floor(), "floor({x})");
        if x > 0.0 {
            assert!((math::sqrt(x) - x.sqrt()).abs() < 1e-5 * x.sqrt().max(1.0), "sqrt({x})");
        }
        let y = (i as f32 * 0.37).sin() * 3.0;
        assert!((math::atan2(y, x) - y.atan2(x)).abs() < 2e-3, "atan2({y}, {x})");
    }
}
