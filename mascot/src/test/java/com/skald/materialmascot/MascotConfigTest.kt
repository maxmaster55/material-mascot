package com.skald.materialmascot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MascotConfigTest {
    @Test
    fun defaults_are_enabled_and_motion_safe() {
        val config = MascotConfig()
        assertTrue(config.enabled)
        assertTrue(config.motion)
        assertEquals(MascotStyle.BOOK_WISP, config.style)
        assertEquals(MascotExpression.CURIOUS, config.expression)
        assertEquals(1f, config.motionScale)
    }

    @Test
    fun static_customization_is_retained() {
        val config = MascotConfig(
            enabled = false,
            motion = false,
            style = MascotStyle.SPARK,
            expression = MascotExpression.SLEEPY
        )
        assertEquals(false, config.enabled)
        assertEquals(false, config.motion)
        assertEquals(MascotStyle.SPARK, config.style)
        assertEquals(MascotExpression.SLEEPY, config.expression)
    }
    @Test
    fun includes_companion_body_shapes() {
        assertTrue(MascotStyle.CIRCLE in MascotStyle.entries)
        assertTrue(MascotStyle.SOFT_BLOB in MascotStyle.entries)
        assertTrue(MascotStyle.HEXAGON in MascotStyle.entries)
        assertTrue(MascotStyle.TRIANGLE in MascotStyle.entries)
    }
    @Test
    fun includes_semantic_eye_expressions() {
        assertTrue(MascotExpression.SAD in MascotExpression.entries)
        assertTrue(MascotExpression.ALERT in MascotExpression.entries)
        assertTrue(MascotExpression.ERROR in MascotExpression.entries)
        assertTrue(MascotExpression.FOCUSED in MascotExpression.entries)
        assertTrue(MascotExpression.THINKING in MascotExpression.entries)
        assertTrue(MascotExpression.EXCITED in MascotExpression.entries)
        assertTrue(MascotExpression.SUCCESS in MascotExpression.entries)
        assertTrue(MascotExpression.CONFUSED in MascotExpression.entries)
    }
    @Test
    fun motion_scale_is_host_configurable() {
        val config = MascotConfig(motionScale = 1.8f)
        assertEquals(1.8f, config.motionScale)
    }

    @Test
    fun all_shapes_and_expressions_accept_supported_motion_scales() {
        val scales = listOf(0f, 0.65f, 1f, 1.6f, 2f)

        MascotStyle.entries.forEach { style ->
            MascotExpression.entries.forEach { expression ->
                scales.forEach { scale ->
                    val config = MascotConfig(
                        style = style,
                        expression = expression,
                        motionScale = scale
                    )
                    assertEquals(style, config.style)
                    assertEquals(expression, config.expression)
                    assertEquals(scale, normalizedMotionScale(config.motionScale))
                }
            }
        }
    }

    @Test
    fun non_finite_motion_scale_is_safe() {
        assertEquals(1f, normalizedMotionScale(Float.NaN))
        assertEquals(0f, normalizedMotionScale(Float.NEGATIVE_INFINITY))
        assertEquals(2f, normalizedMotionScale(Float.POSITIVE_INFINITY))
        assertTrue(normalizedMotionScale(Float.NaN).isFinite())
    }
}
