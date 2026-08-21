package com.skald.materialmascot

import androidx.compose.ui.graphics.Color

/** The visual language of the companion. */
enum class MascotStyle { BOOK_WISP, SPARK, CIRCLE, SOFT_BLOB, HEXAGON, TRIANGLE }

/** Small, non-verbal faces that remain legible at compact sizes. */
enum class MascotExpression { CURIOUS, HAPPY, SLEEPY, SAD, ALERT, ERROR, FOCUSED, THINKING, EXCITED, SUCCESS, CONFUSED }

/**
 * All state needed to render a mascot. Hosts can keep this next to their own
 * preferences without making the library depend on a settings framework.
 */
data class MascotConfig(
    val enabled: Boolean = true,
    val color: Color = Color.Unspecified,
    val style: MascotStyle = MascotStyle.BOOK_WISP,
    val expression: MascotExpression = MascotExpression.CURIOUS,
    val motion: Boolean = true,
    /** Motion amplitude/intensity, clamped by the renderer to the supported range. */
    val motionScale: Float = 1f
)
