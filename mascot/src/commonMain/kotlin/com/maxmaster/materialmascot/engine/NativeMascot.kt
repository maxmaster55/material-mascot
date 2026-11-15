/*
 * Material Mascot — a living bot avatar for Android apps.
 * Copyright (C) 2026 Max Master
 * SPDX-License-Identifier: MIT
 */

package com.maxmaster.materialmascot.engine

/**
 * The Rust core (`mascot-core`, reached through the `mascot-jni` bridge).
 *
 * Everything the mascot draws is computed there; this library only keeps the
 * state machine, hands over a pixel buffer and shows the result.
 *
 * The native side is stateless: every call carries the whole state, so there
 * is nothing to allocate, free or leak across the boundary.
 */
internal object NativeMascot {
    /** Why the native library could not be loaded, or null if it is ready. */
    val loadError: Throwable? = try {
        loadMascotLibrary()
        null
    } catch (t: Throwable) {
        t
    }

    const val FLAG_CAT: Int = 1
    const val FLAG_LIFE: Int = 2
    const val FLAG_PIXEL_OUTLINE: Int = 4

    /**
     * Paints one [size] x [size] frame into [out] as straight-alpha
     * 0xAARRGGBB ints. Returns false, drawing nothing, if an argument is
     * unusable. [prev] is -1 when there is no state to morph from.
     * [palette] is nine 0xRRGGBB colours, see [com.maxmaster.materialmascot.config.MascotPalette].
     */
    @JvmStatic
    external fun render(
        out: IntArray,
        size: Int,
        nowMs: Long,
        cur: Int,
        prev: Int,
        tCur: Long,
        tPrev: Long,
        look: Int,
        eyes: Int,
        grid: Int,
        pixelShade: Int,
        flags: Int,
        fps: Int,
        seed: Float,
        palette: IntArray
    ): Boolean

    /** Number of states the core knows. */
    @JvmStatic
    external fun stateCount(): Int

    /** Core name of state [id], or null when out of range. */
    @JvmStatic
    external fun stateName(id: Int): String?
}

/** Loads `mascot_jni` for the current platform. Throws if it cannot. */
internal expect fun loadMascotLibrary()
