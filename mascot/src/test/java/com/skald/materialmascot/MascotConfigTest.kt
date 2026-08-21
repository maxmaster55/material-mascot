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
}
