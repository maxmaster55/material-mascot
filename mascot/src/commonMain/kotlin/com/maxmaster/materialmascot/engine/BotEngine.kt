/*
 * Material Mascot — a living bot avatar for Android apps.
 * Copyright (C) 2026 Max Master
 * SPDX-License-Identifier: MIT
 */

package com.maxmaster.materialmascot.engine

import androidx.compose.runtime.Immutable
import com.maxmaster.materialmascot.config.MascotFinish
import com.maxmaster.materialmascot.config.MascotPalette

/** Body shading for [MascotFinish.PIXEL] and [MascotFinish.LED]. */
public enum class PixelShade(internal val nativeId: Int) {
    /** Lit as a 3D form: banded tones, a highlight and a ground shadow. */
    LIT(0),

    /** A dithered rim of light and shade. */
    SOFT(1),

    /** One body tone. */
    FLAT(2)
}

/**
 * Everything about how a frame is painted, as opposed to what the bot is doing.
 *
 * @param finish How the body is painted.
 * @param eyeStyle Eye personality.
 * @param palette The mascot's colours.
 * @param pixelGrid Cells across for the pixel finishes (24 to 64 reads best).
 * @param pixelShade Body shading for the pixel finishes.
 * @param pixelOutline Dark one-cell outline in the pixel finishes.
 * @param cat Give the ball cat ears.
 * @param life Idle life: gaze wander and blinking.
 * @param fps Hold each frame for 1/fps seconds for a hand-animated feel; 0 = every frame.
 * @param seed Offsets the blink and gaze schedule so several bots do not move in unison.
 */
@Immutable
public data class RenderStyle(
    public val finish: MascotFinish = MascotFinish.FLAT,
    public val eyeStyle: EyeStyle = EyeStyle.GROK,
    public val palette: MascotPalette = MascotPalette.Ink,
    public val pixelGrid: Int = 32,
    public val pixelShade: PixelShade = PixelShade.LIT,
    public val pixelOutline: Boolean = true,
    public val cat: Boolean = false,
    public val life: Boolean = true,
    public val fps: Int = 0,
    public val seed: Float = 0.37f
) {
    // The palette crosses JNI as an int array; built once per style, not per frame.
    internal val paletteArgb: IntArray = palette.toArgbArray()
}

/**
 * Clock-free engine: [render] is a pure function of time.
 *
 * Pause, resume, slow motion and jumping to an arbitrary moment all give
 * exactly the same image. The drawing itself happens in the Rust core; this
 * class holds the only state there is — which [BotState] is showing, which one
 * it is morphing from, and when each began.
 *
 * Most users will not need this directly — prefer `MaterialBot` from the UI
 * package. It is public for custom renderers, tests and headless frame
 * generation.
 *
 * @param initial The initial [BotState] to start in.
 * @param initialTimeMs Starting time in milliseconds. Defaults to 0.
 */
public class BotEngine(
    initial: BotState,
    initialTimeMs: Long = 0L
) {
    /** Current state (read-only). */
    public var cur: BotState = initial
        private set
    private var prev: BotState? = null
    private var tCur: Long = initialTimeMs
    private var tPrev: Long = initialTimeMs

    /** Bumped on every state change, so callers can tell a cached frame is stale. */
    internal var generation: Int = 0
        private set

    public companion object {
        /** Ball radius as a fraction of the canvas half-side. */
        public const val RADIUS_FRACTION: Float = 0.6f

        /** Length of the morph between two states, in milliseconds. */
        public const val MORPH_MS: Long = 500L

        /**
         * Why the native core could not be loaded, or null if it is ready.
         * While this is non-null every [render] call returns false.
         */
        public val nativeError: Throwable? get() = NativeMascot.loadError
    }

    /** Current state of the engine. */
    public val state: BotState get() = cur

    /**
     * State change: body, eyes and props morph from the current state over
     * [MORPH_MS]. Only one slot of history is kept.
     *
     * @param id The new [BotState] to transition to.
     * @param nowMs Current time in milliseconds, on the same clock as [render].
     */
    public fun setState(id: BotState, nowMs: Long) {
        if (id == cur) return
        prev = cur
        tPrev = tCur
        cur = id
        tCur = nowMs
        generation++
    }

    /**
     * Paints one frame into [out] as straight-alpha 0xAARRGGBB ints, row by
     * row. Pixels the bot does not cover come back fully transparent.
     *
     * Pure: the same arguments always produce the same pixels.
     *
     * @param nowMs Current time in milliseconds.
     * @param sizePx Side of the square frame in pixels. The ball's radius is
     *               `sizePx / 2 * RADIUS_FRACTION`, leaving room for props.
     * @param out Buffer of at least `sizePx * sizePx` ints.
     * @param style How to paint.
     * @return false, leaving [out] untouched, if the native core is
     *         unavailable or the buffer is too small.
     */
    public fun render(
        nowMs: Long,
        sizePx: Int,
        out: IntArray,
        style: RenderStyle = RenderStyle()
    ): Boolean {
        if (NativeMascot.loadError != null) return false
        if (sizePx <= 0 || out.size < sizePx * sizePx) return false
        var flags = 0
        if (style.cat) flags = flags or NativeMascot.FLAG_CAT
        if (style.life) flags = flags or NativeMascot.FLAG_LIFE
        if (style.pixelOutline) flags = flags or NativeMascot.FLAG_PIXEL_OUTLINE
        return NativeMascot.render(
            out, sizePx, nowMs,
            cur.id, prev?.id ?: -1, tCur, tPrev,
            style.finish.nativeId, style.eyeStyle.nativeId,
            style.pixelGrid, style.pixelShade.nativeId,
            flags, style.fps, style.seed, style.paletteArgb
        )
    }
}
