/*
 * Material Mascot — a living bot avatar for Android apps.
 * Copyright (C) 2026 Max Master
 * SPDX-License-Identifier: MIT
 */

package com.maxmaster.materialmascot.config

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maxmaster.materialmascot.engine.BotState
import com.maxmaster.materialmascot.engine.EyeStyle
import com.maxmaster.materialmascot.engine.PixelShade

/**
 * How the bot is painted.
 *
 * Every finish shares the exact same shapes and animation; only the paint
 * changes. [FLAT], [CHROME] and [OUTLINE] are the original three.
 */
public enum class MascotFinish(internal val nativeId: Int) {
    /** One solid fill with smooth edges: the reference look. */
    FLAT(0),

    /** Lit body, rim light, soft highlight, ground shadow, glowing eyes. */
    CHROME(1),

    /**
     * Line art: an unfilled body drawn as a stroke, with the eyes in the same
     * ink. Reads clearly on any background — including a photo or a coloured
     * card, where a filled bot fights whatever is behind it.
     */
    OUTLINE(2),

    /** Smooth 3D shading. */
    CLAY(3),

    /** Line art with a glow. */
    NEON(4),

    /** Pixel art on a [MascotConfig.pixelGrid]-cell grid. */
    PIXEL(5),

    /** The pixel grid shown as round dots, like an LED matrix. */
    LED(6);

    /** True for the finishes drawn on a coarse grid. */
    public val isGrid: Boolean get() = this == PIXEL || this == LED
}

/**
 * Host-owned immutable configuration for the Material Bot mascot.
 *
 * All fields are immutable so this can be backed by DataStore,
 * ViewModel state, or any other settings system.
 *
 * @param enabled Whether the mascot is visible. Default: true.
 * @param state Current emotional/operational state. Default: [BotState.Idle].
 * @param initialState The state shown on first composition before any state changes. Default: [BotState.Idle].
 * @param color Body fill color; defaults to the 'encre' ink (0xFF0A0A0C).
 *              Ignored when [palette] is set.
 * @param eyeColor Eye fill color; defaults to white. Ignored when [palette] is set.
 * @param size Canvas size in dp. Default 64dp.
 * @param motion If false, turns off idle life (gaze wander and blinking) for
 *               reduced-motion settings. State animation still plays.
 * @param energy Kept for source compatibility. The Rust core has one fixed
 *               liveliness, so this value currently has no effect.
 * @param eyeStyle Eye personality. Default: [EyeStyle.GROK].
 * @param finish How the bot is painted. Default [MascotFinish.FLAT].
 * @param cat Cat look: pointed ears on the resting ball. Default: false.
 * @param palette Full colour set. When null (the default) one is derived
 *                from [color] and [eyeColor].
 * @param pixelGrid Cells across for the pixel finishes. Default 32.
 * @param pixelShade Body shading for the pixel finishes.
 * @param maxFps Upper bound on how often the bot is repainted. Default 30;
 *               0 repaints on every display frame.
 * @param maxRenderPx Upper bound on the side of the rendered frame, in
 *                    pixels. A larger bot is rendered at this size and scaled
 *                    up, trading a little sharpness for CPU time. Default 256.
 */
@Immutable
public data class MascotConfig(
    public val enabled: Boolean = true,
    public val state: BotState = BotState.Idle,
    public val initialState: BotState = BotState.Idle,
    public val color: Color = Color(0xFF0A0A0C),
    public val eyeColor: Color = Color.White,
    public val size: Dp = 64.dp,
    public val motion: Boolean = true,
    public val energy: Float = 1.9f,
    public val eyeStyle: EyeStyle = EyeStyle.GROK,
    public val finish: MascotFinish = MascotFinish.FLAT,
    public val cat: Boolean = false,
    public val palette: MascotPalette? = null,
    public val pixelGrid: Int = 32,
    public val pixelShade: PixelShade = PixelShade.LIT,
    public val maxFps: Int = 30,
    public val maxRenderPx: Int = 256
)
