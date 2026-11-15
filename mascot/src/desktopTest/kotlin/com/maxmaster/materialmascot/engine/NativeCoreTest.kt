/*
 * Material Mascot — a living bot avatar for Android apps.
 * Copyright (C) 2026 Max Master
 * SPDX-License-Identifier: MIT
 */

package com.maxmaster.materialmascot.engine

import com.maxmaster.materialmascot.config.MascotFinish
import com.maxmaster.materialmascot.config.MascotPalette
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises the real Rust core through JNI. */
class NativeCoreTest {

    private fun frame(
        engine: BotEngine,
        nowMs: Long,
        style: RenderStyle = RenderStyle(life = false),
        size: Int = 48
    ): IntArray {
        val out = IntArray(size * size)
        assertTrue("core refused to render", engine.render(nowMs, size, out, style))
        return out
    }

    private fun covered(pixels: IntArray): Int = pixels.count { it ushr 24 != 0 }

    @Test
    fun native_library_loads() {
        assertNull(BotEngine.nativeError)
    }

    @Test
    fun kotlin_states_match_the_core_one_for_one() {
        // State ids are positions in the core's list; a mismatch here would
        // mean asking for one state and silently getting another.
        assertEquals(NativeMascot.stateCount(), BotState.all.size)
        BotState.all.forEachIndexed { index, state ->
            assertEquals(index, state.id)
            assertEquals(state.name, NativeMascot.stateName(state.id))
        }
        assertNull(NativeMascot.stateName(BotState.all.size))
    }

    @Test
    fun every_state_draws_in_every_finish() {
        for (state in BotState.all) {
            for (finish in MascotFinish.entries) {
                val pixels = frame(BotEngine(state), 2_500, RenderStyle(finish = finish))
                assertTrue("${state.name} in $finish drew nothing", covered(pixels) > 20)
            }
        }
    }

    @Test
    fun same_time_gives_the_same_frame() {
        val engine = BotEngine(BotState.Idle)
        engine.setState(BotState.Love, 1_000)
        assertArrayEquals(frame(engine, 1_230), frame(engine, 1_230))
    }

    @Test
    fun a_state_change_morphs_then_settles() {
        val engine = BotEngine(BotState.Idle)
        val before = frame(engine, 5_000)
        engine.setState(BotState.Star, 5_000)
        val mid = frame(engine, 5_250)
        assertFalse("morph has not started", mid.contentEquals(before))
        assertFalse(
            "morph jumped straight to the target",
            mid.contentEquals(frame(BotEngine(BotState.Star), 250))
        )
        // Past the morph the frame depends only on the new state and its clock.
        val other = BotEngine(BotState.Hexagon).also { it.setState(BotState.Star, 5_000) }
        val after = 5_000 + BotEngine.MORPH_MS + 100
        assertArrayEquals(frame(other, after), frame(engine, after))
    }

    @Test
    fun setting_the_same_state_is_a_no_op() {
        val engine = BotEngine(BotState.Happy)
        val generation = engine.generation
        engine.setState(BotState.Happy, 9_000)
        assertEquals(generation, engine.generation)
    }

    @Test
    fun eye_style_and_palette_change_the_picture() {
        val base = frame(BotEngine(BotState.Idle), 900, size = 64)
        for (eyes in EyeStyle.entries.filter { it != EyeStyle.GROK }) {
            val styled = frame(BotEngine(BotState.Idle), 900, RenderStyle(eyeStyle = eyes, life = false), 64)
            assertFalse("$eyes looks like GROK", styled.contentEquals(base))
        }
        val grape = frame(BotEngine(BotState.Idle), 900, RenderStyle(palette = MascotPalette.Grape, life = false), 64)
        assertFalse(grape.contentEquals(base))
    }

    @Test
    fun pixel_finish_is_hard_edged_and_flat_is_not() {
        fun partial(finish: MascotFinish) =
            frame(BotEngine(BotState.Idle), 900, RenderStyle(finish = finish, pixelShade = PixelShade.FLAT), 64)
                .count { (it ushr 24) in 1..254 }
        assertEquals(0, partial(MascotFinish.PIXEL))
        assertTrue(partial(MascotFinish.FLAT) > 40)
    }

    @Test
    fun a_buffer_that_is_too_small_is_refused() {
        assertFalse(BotEngine(BotState.Idle).render(0, 32, IntArray(10)))
        assertFalse(BotEngine(BotState.Idle).render(0, 0, IntArray(10)))
    }
}
