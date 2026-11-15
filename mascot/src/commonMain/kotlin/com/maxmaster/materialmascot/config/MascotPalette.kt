/*
 * Material Mascot — a living bot avatar for Android apps.
 * Copyright (C) 2026 Max Master
 * SPDX-License-Identifier: MIT
 */

package com.maxmaster.materialmascot.config

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb

/**
 * The mascot's colours. Lighting tones not listed here (deep shadow,
 * highlights, the chrome gradient) are derived from these by the core.
 *
 * @param outline Darkest tone: pixel-art outline and the rim of props.
 * @param shadow Body in shade.
 * @param base Body colour; the only tone [MascotFinish.FLAT] uses.
 * @param highlight Body in light.
 * @param eye Eye colour.
 * @param pink Hearts, blush, alerts.
 * @param blue Tears, sleep, question marks.
 * @param yellow Sparkles and stars.
 * @param background A background the palette was designed to sit on. Never
 *                   painted by the mascot; offered for hosts that want it.
 */
@Immutable
public data class MascotPalette(
    public val outline: Color,
    public val shadow: Color,
    public val base: Color,
    public val highlight: Color,
    public val eye: Color,
    public val pink: Color = Color(0xFFFF6B9D),
    public val blue: Color = Color(0xFF6BD6FF),
    public val yellow: Color = Color(0xFFFFD84A),
    public val background: Color = Color(0xFF15122B)
) {
    internal fun toArgbArray(): IntArray = intArrayOf(
        outline.toArgb(), shadow.toArgb(), base.toArgb(), highlight.toArgb(), eye.toArgb(),
        pink.toArgb(), blue.toArgb(), yellow.toArgb(), background.toArgb()
    )

    public companion object {
        public val Grape: MascotPalette = MascotPalette(
            outline = Color(0xFF1A0F3A), shadow = Color(0xFF4B2EA0), base = Color(0xFF7450E0),
            highlight = Color(0xFFA689FF), eye = Color(0xFFFFFFFF),
            pink = Color(0xFFFF6B9D), blue = Color(0xFF6BD6FF), yellow = Color(0xFFFFD84A),
            background = Color(0xFF15122B)
        )

        /** The original look: near-black body, white eyes. */
        public val Ink: MascotPalette = MascotPalette(
            outline = Color(0xFF020206), shadow = Color(0xFF0E0E16), base = Color(0xFF1E1E2B),
            highlight = Color(0xFF41415A), eye = Color(0xFFFFFFFF),
            pink = Color(0xFFFF5A7A), blue = Color(0xFF3AA8F5), yellow = Color(0xFFF5B921),
            background = Color(0xFFE9E5DA)
        )

        public val Ember: MascotPalette = MascotPalette(
            outline = Color(0xFF2B0A05), shadow = Color(0xFFA02C10), base = Color(0xFFE8591A),
            highlight = Color(0xFFFFAB3D), eye = Color(0xFFFFF4D6),
            pink = Color(0xFFFF7AA8), blue = Color(0xFF7FD8FF), yellow = Color(0xFFFFF07A),
            background = Color(0xFF1C1210)
        )

        public val Mint: MascotPalette = MascotPalette(
            outline = Color(0xFF06302B), shadow = Color(0xFF138A72), base = Color(0xFF2FD0A5),
            highlight = Color(0xFFA8F5D8), eye = Color(0xFF082A25),
            pink = Color(0xFFFF6B9D), blue = Color(0xFF57B4FF), yellow = Color(0xFFFFE066),
            background = Color(0xFF0E1F1D)
        )

        /** Four greens, for a monochrome-handheld feel. */
        public val Handheld: MascotPalette = MascotPalette(
            outline = Color(0xFF0F380F), shadow = Color(0xFF306230), base = Color(0xFF306230),
            highlight = Color(0xFF8BAC0F), eye = Color(0xFF9BBC0F),
            pink = Color(0xFF0F380F), blue = Color(0xFF0F380F), yellow = Color(0xFF0F380F),
            background = Color(0xFF9BBC0F)
        )

        /**
         * A palette from just a body and an eye colour; the shading tones are
         * derived. This is what [MascotConfig.color] and
         * [MascotConfig.eyeColor] go through when no palette is given.
         */
        public fun fromBody(body: Color, eye: Color): MascotPalette = MascotPalette(
            outline = lerp(body, Color.Black, 170f / 256f),
            shadow = lerp(body, Color.Black, 90f / 256f),
            base = body,
            highlight = lerp(body, Color.White, 80f / 256f),
            eye = eye
        )
    }
}
