package com.maxmaster.materialmascot.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * The added life layers (saccades, glances, squints, organic breath) must stay
 * deterministic, bounded and continuous enough never to read as a glitch.
 */
class BotLifeTest {

    @Test
    fun liveliness_is_deterministic_for_same_time() {
        val a = liveliness(432.1f, energy = 1.9f)
        val b = liveliness(432.1f, energy = 1.9f)
        assertEquals(a, b)
    }

    @Test
    fun wander_zero_kills_the_gaze_layers() {
        val l = liveliness(100f, wander = 0f, energy = 1.9f)
        assertEquals(0f, l.dYaw, 1e-6f)
        assertEquals(0f, l.dPitch, 1e-6f)
    }

    @Test
    fun squint_stays_between_0_35_and_1_and_blink_can_fully_close() {
        var t = 0f
        while (t < 1200f) {
            val s = squintLid(t)
            assertTrue("squint=$s at t=$t", s >= 0.35f && s <= 1f)
            val l = liveliness(t)
            // combined lid may close fully during blinks, never invert
            assertTrue("lid=${l.lid} at t=$t", l.lid >= 0f && l.lid <= 1f)
            t += 0.05f
        }
    }

    @Test
    fun gaze_offsets_stay_finite_and_bounded() {
        var t = 0f
        while (t < 1200f) {
            val l = liveliness(t, energy = 1.9f)
            assertFalse("NaN yaw at t=$t", l.dYaw.isNaN())
            assertTrue("yaw runaway ${l.dYaw} at t=$t", abs(l.dYaw) <= 30f)
            assertTrue("pitch runaway ${l.dPitch} at t=$t", abs(l.dPitch) <= 24f)
            t += 0.033f
        }
    }

    @Test
    fun no_frame_to_frame_teleport_at_60fps() {
        // Saccade attacks are ALLOWED to be fast (that is what a saccade is);
        // anything beyond ~4 deg per frame is a discontinuity, not speed.
        var prev = liveliness(500f)
        var t = 500f + 0.016f
        while (t < 560f) {
            val cur = liveliness(t)
            assertTrue("dYaw jump ${cur.dYaw - prev.dYaw} at t=$t", abs(cur.dYaw - prev.dYaw) < 4f)
            assertTrue(
                "dPitch jump ${cur.dPitch - prev.dPitch} at t=$t",
                abs(cur.dPitch - prev.dPitch) < 4f
            )
            prev = cur
            t += 0.016f
        }
    }
}
