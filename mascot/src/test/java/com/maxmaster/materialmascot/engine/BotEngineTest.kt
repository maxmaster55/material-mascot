package com.maxmaster.materialmascot.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * JVM unit tests for the bloub port's pure geometry/engine layer.
 * No Android dependencies; only Compose geometry value types.
 */
class BotEngineTest {

    @Test
    fun blink_schedule_starts_at_first_blink_1_4s() {
        assertEquals(1.4f, BLINKS.first(), 1e-4f)
    }

    @Test
    fun easeOutQuint_endpoints_and_monotonic() {
        assertEquals(0f, Easings.easeOutQuint(0f), 1e-6f)
        assertEquals(1f, Easings.easeOutQuint(1f), 1e-6f)
        var prev = -Float.MAX_VALUE
        for (i in 0..10) {
            val v = Easings.easeOutQuint(i / 10f)
            assertTrue("easeOutQuint not non-decreasing at t=${i / 10f}", v >= prev)
            prev = v
        }
    }

    @Test
    fun circle_profile_radius_is_constant_in_any_direction() {
        val r = radiusAtAngle(circle(1f).radii, 2.3f)
        assertEquals(1f, r, 1e-5f)
    }

    @Test
    fun sample_is_deterministic_for_same_time() {
        val e = BotEngine(BotState.Idle)
        val f1 = e.sample(10_000L, 158f)
        val f2 = e.sample(10_000L, 158f)
        assertTrue(f1.eyes.isNotEmpty())
        assertEquals(f1.eyes[0].tx, f2.eyes[0].tx, 0f)
        assertEquals(f1.eyes[0].ty, f2.eyes[0].ty, 0f)
    }

    @Test
    fun idle_eyes_stay_inside_body() {
        // scalePx=158f -> ball radius R = 158f * RADIUS_FRACTION = 100px.
        val e = BotEngine(BotState.Idle)
        val frame = e.sample(5_000L, 158f)
        assertEquals(2, frame.eyes.size)
        for (eye in frame.eyes) {
            assertTrue("eye tx outside body: ${eye.tx}", abs(eye.tx) < 100f)
            assertTrue("eye ty outside body: ${eye.ty}", abs(eye.ty) < 100f)
        }
    }

    @Test
    fun chained_transitions_keep_sample_pure() {
        val e = BotEngine(BotState.Idle, initialTimeMs = 9_000L)
        e.setState(BotState.Happy, 10_000L)
        e.sample(10_100L, 158f)
        e.setState(BotState.Sleepy, 10_150L)
        val f1 = e.sample(10_300L, 158f)
        val f2 = e.sample(10_300L, 158f)
        assertEquals(f1.bodyPoints, f2.bodyPoints)
        assertEquals(f1.eyes.size, f2.eyes.size)
    }

    @Test
    fun botstate_registry_and_lookup_guards() {
        // 26 states in BotState.all order
        assertEquals(26, BotState.all.size)
        BotState.all.forEach { state ->
            assertEquals(state, BotState.fromName(state.name.uppercase()))
        }
        assertEquals(BotState.Happy, BotState.fromName("HAPPY"))
        assertEquals(BotState.Idle, BotState.fromName("nope"))
    }
}
