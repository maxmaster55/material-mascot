package com.maxmaster.materialmascot.engine

/**
 * A decorative stroked ring around the bot, in ball-radius units.
 * Rendered as a circle outline in the body color; [stroke] is the line
 * width, also in ball-radius units.
 */
data class ArcSpec(
    val x: Float,
    val y: Float,
    val r: Float,
    val stroke: Float = 0.05f,
    val alpha: Float = 1f
)

/** Orbit's release flourish: three concentric rings centered on the origin. */
val ORBIT_RINGS: List<ArcSpec> = listOf(
    ArcSpec(x = 0f, y = 0f, r = 1.35f, stroke = 0.06f),
    ArcSpec(x = 0f, y = 0f, r = 1.55f, stroke = 0.06f),
    ArcSpec(x = 0f, y = 0f, r = 1.75f, stroke = 0.06f)
)
