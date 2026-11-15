//! JNI bridge: lets the Kotlin library draw with `mascot-core`.
//!
//! The Kotlin side keeps the tiny state machine (current state, previous state,
//! two timestamps) and passes it in on every call, so there is nothing to
//! allocate, free or leak across the boundary. Each call is one pure render.
//!
//! JNI is spoken directly through the function table instead of pulling in a
//! bindings crate, which keeps the whole stack dependency-free. Only the five
//! table entries below are used; their indices are fixed by the JNI spec.

#![allow(non_snake_case, clippy::missing_safety_doc, clippy::too_many_arguments)]

use core::ffi::{c_char, c_void};
use mascot_core::{EyeStyle, Look, Mascot, Palette, PixelShade, Rgb, State, Style};

type Env = *mut *const *const c_void;
type JObject = *mut c_void;

const NEW_STRING_UTF: usize = 167;
const GET_ARRAY_LENGTH: usize = 171;
const GET_INT_ARRAY_REGION: usize = 203;
const GET_PRIMITIVE_ARRAY_CRITICAL: usize = 222;
const RELEASE_PRIMITIVE_ARRAY_CRITICAL: usize = 223;

/// Fetch entry `idx` of the JNI function table as a typed function pointer.
unsafe fn jni<T: Copy>(env: Env, idx: usize) -> T {
    debug_assert_eq!(core::mem::size_of::<T>(), core::mem::size_of::<*const c_void>());
    core::mem::transmute_copy(&*(*env).add(idx))
}

fn state(id: i32) -> Option<State> {
    State::ALL.get(usize::try_from(id).ok()?).copied()
}

fn rgb(argb: i32) -> Rgb {
    Rgb::hex(argb as u32 & 0x00ff_ffff)
}

/// Bits of the `flags` argument. Mirrored in NativeMascot.kt.
const FLAG_CAT: i32 = 1;
const FLAG_LIFE: i32 = 2;
const FLAG_PIXEL_OUTLINE: i32 = 4;

/// Paint one frame into `out` as straight-alpha 0xAARRGGBB ints, row by row.
///
/// Returns false, drawing nothing, if an argument is unusable (unknown state,
/// buffer too small, palette not 9 colours).
#[no_mangle]
pub unsafe extern "system" fn Java_com_maxmaster_materialmascot_engine_NativeMascot_render(
    env: Env,
    _class: JObject,
    out: JObject,
    size: i32,
    now_ms: i64,
    cur: i32,
    prev: i32,
    t_cur_ms: i64,
    t_prev_ms: i64,
    look: i32,
    eyes: i32,
    grid: i32,
    pixel_shade: i32,
    flags: i32,
    fps: i32,
    seed: f32,
    palette: JObject,
) -> u8 {
    let Some(cur) = state(cur) else { return 0 };
    if out.is_null() || palette.is_null() || size <= 0 || size > 4096 {
        return 0;
    }
    let get_len: unsafe extern "system" fn(Env, JObject) -> i32 = jni(env, GET_ARRAY_LENGTH);
    let n = (size as usize) * (size as usize);
    if (get_len(env, out) as usize) < n || get_len(env, palette) != 9 {
        return 0;
    }
    let mut pal = [0i32; 9];
    let get_region: unsafe extern "system" fn(Env, JObject, i32, i32, *mut i32) = jni(env, GET_INT_ARRAY_REGION);
    get_region(env, palette, 0, 9, pal.as_mut_ptr());

    let style = Style {
        look: match look {
            1 => Look::Chrome,
            2 => Look::Outline,
            3 => Look::Clay,
            4 => Look::Neon,
            5 => Look::Pixel,
            6 => Look::Led,
            _ => Look::Flat,
        },
        eyes: match eyes {
            1 => EyeStyle::Classic,
            2 => EyeStyle::Big,
            3 => EyeStyle::Round,
            4 => EyeStyle::Pupil,
            5 => EyeStyle::Block,
            6 => EyeStyle::Visor,
            7 => EyeStyle::Dot,
            _ => EyeStyle::Grok,
        },
        palette: Palette {
            outline: rgb(pal[0]),
            shadow: rgb(pal[1]),
            base: rgb(pal[2]),
            hi: rgb(pal[3]),
            eye: rgb(pal[4]),
            pink: rgb(pal[5]),
            blue: rgb(pal[6]),
            yellow: rgb(pal[7]),
            bg: rgb(pal[8]),
        },
        grid: grid.clamp(8, 256) as u16,
        pixel_shade: match pixel_shade {
            1 => PixelShade::Soft,
            2 => PixelShade::Flat,
            _ => PixelShade::Lit,
        },
        pixel_outline: flags & FLAG_PIXEL_OUTLINE != 0,
        cat: flags & FLAG_CAT != 0,
        life: flags & FLAG_LIFE != 0,
        fps: fps.clamp(0, 240) as u8,
        seed,
    };
    let mascot = Mascot::from_parts(cur, state(prev), t_cur_ms.max(0) as u64, t_prev_ms.max(0) as u64);

    // Critical access hands back the array's own memory with no copy. Nothing
    // between get and release calls back into the JVM or blocks.
    let get: unsafe extern "system" fn(Env, JObject, *mut u8) -> *mut c_void = jni(env, GET_PRIMITIVE_ARRAY_CRITICAL);
    let release: unsafe extern "system" fn(Env, JObject, *mut c_void, i32) = jni(env, RELEASE_PRIMITIVE_ARRAY_CRITICAL);
    let ptr = get(env, out, core::ptr::null_mut());
    if ptr.is_null() {
        return 0;
    }
    let pixels = core::slice::from_raw_parts_mut(ptr as *mut i32, n);
    let size = size as u32;
    mascot.render(now_ms.max(0) as u64, size, &style, |x, y, c| {
        pixels[(y * size + x) as usize] =
            ((c.a as u32) << 24 | (c.r as u32) << 16 | (c.g as u32) << 8 | c.b as u32) as i32;
    });
    release(env, out, ptr, 0);
    1
}

/// How many states the core knows. The Kotlin side checks this against its own list.
#[no_mangle]
pub unsafe extern "system" fn Java_com_maxmaster_materialmascot_engine_NativeMascot_stateCount(
    _env: Env,
    _class: JObject,
) -> i32 {
    State::ALL.len() as i32
}

/// Name of state `id`, or null. State ids are positions in `State::ALL`; this
/// is how the Kotlin tests prove the two lists have not drifted apart.
#[no_mangle]
pub unsafe extern "system" fn Java_com_maxmaster_materialmascot_engine_NativeMascot_stateName(
    env: Env,
    _class: JObject,
    id: i32,
) -> JObject {
    let Some(s) = state(id) else { return core::ptr::null_mut() };
    let name = s.name().as_bytes();
    let mut buf = [0u8; 32];
    let len = name.len().min(buf.len() - 1);
    buf[..len].copy_from_slice(&name[..len]);
    let new_string: unsafe extern "system" fn(Env, *const c_char) -> JObject = jni(env, NEW_STRING_UTF);
    new_string(env, buf.as_ptr() as *const c_char)
}
