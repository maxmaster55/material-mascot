/*
 * Material Mascot — a living bot avatar for Android apps.
 * Copyright (C) 2026 Max Master
 * SPDX-License-Identifier: MIT
 */

package com.maxmaster.materialmascot.sample

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.maxmaster.materialmascot.config.MascotConfig
import com.maxmaster.materialmascot.engine.BotState
import com.maxmaster.materialmascot.ui.MaterialBot
import kotlinx.coroutines.delay

/**
 * Example: Chat UI with a bot mascot that reacts to message states.
 *
 * This is a sample for demonstration purposes and is not part of the public API.
 * It is marked internal to exclude it from the published library.
 */
@Composable
internal fun ChatWithBot() {
    var botState by remember { mutableStateOf<BotState>(BotState.Idle) }
    var isTyping by remember { mutableStateOf(false) }

    // Simulate a conversation
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            botState = BotState.Listening
            delay(1500)
            botState = BotState.Thinking
            delay(2000)
            botState = BotState.Responding
            delay(1500)
            botState = BotState.Happy
            delay(2000)
            botState = BotState.Idle
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        MaterialBot(
            config = MascotConfig(
                state = botState,
                color = MaterialTheme.colorScheme.primary,
                size = 120.dp,
                motion = true
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "State: ${botState.name}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewChatWithBot() {
    ChatWithBot()
}

/**
 * Example: Accessibility-friendly version with reduced motion.
 *
 * This is a sample for demonstration purposes and is not part of the public API.
 * It is marked internal to exclude it from the published library.
 */
@Composable
internal fun AccessibleBot(state: BotState) {
    MaterialBot(
        config = MascotConfig(
            state = state,
            color = Color(0xFF0A0A0C),
            size = 64.dp,
            motion = false // Respects system reduced-motion preference
        )
    )
}