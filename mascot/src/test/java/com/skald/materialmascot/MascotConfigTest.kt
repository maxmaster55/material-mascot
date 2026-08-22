package com.maxmaster.materialmascot

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.maxmaster.materialmascot.config.MascotConfig
import com.maxmaster.materialmascot.engine.BotState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MascotConfigTest {
    @Test
    fun defaults_are_enabled_and_idle() {
        val config = MascotConfig()
        assertTrue(config.enabled)
        assertTrue(config.motion)
        assertEquals(BotState.Idle, config.state)
        assertEquals(BotState.Idle, config.initialState)
        assertEquals(64.dp, config.size)
        assertEquals(Color(0xFF0A0A0C), config.color)
        assertEquals(Color.White, config.eyeColor)
    }

    @Test
    fun custom_values_are_retained() {
        val config = MascotConfig(
            enabled = false,
            motion = false,
            state = BotState.Happy,
            initialState = BotState.Thinking,
            color = Color(0xFF2F8F83),
            size = 96.dp
        )
        assertEquals(false, config.enabled)
        assertEquals(false, config.motion)
        assertEquals(BotState.Happy, config.state)
        assertEquals(BotState.Thinking, config.initialState)
        assertEquals(Color(0xFF2F8F83), config.color)
        assertEquals(96.dp, config.size)
    }

    @Test
    fun all_states_are_registered() {
        val expected = listOf(
            BotState.Idle, BotState.Listening, BotState.Thinking, BotState.Responding,
            BotState.Alert, BotState.Happy, BotState.Confused, BotState.Sleepy,
            BotState.Notify, BotState.Working, BotState.Narrating, BotState.Sad,
            BotState.Love, BotState.Silly, BotState.Egg, BotState.Hexagon
        )
        assertEquals(expected, BotState.all)
    }

    @Test
    fun from_name_parses_case_insensitively() {
        assertEquals(BotState.Happy, BotState.fromName("HAPPY"))
        assertEquals(BotState.Thinking, BotState.fromName("thinking"))
        assertEquals(BotState.Responding, BotState.fromName("Responding"))
    }

    @Test
    fun unknown_name_falls_back_to_idle() {
        assertEquals(BotState.Idle, BotState.fromName("nope"))
        assertEquals(BotState.Idle, BotState.fromName(""))
    }
}
