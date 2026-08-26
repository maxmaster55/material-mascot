/*
 * Material Mascot — a living bot avatar for Android apps.
 * Copyright (C) 2026 Max Master
 * SPDX-License-Identifier: MIT
 */

package com.maxmaster.materialmascot.ui

// Android Studio @Preview tooling only exists on the Android target, so
// these live here rather than in commonMain's MaterialBot.kt — moved
// verbatim during the Kotlin Multiplatform port, no behaviour change.

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.maxmaster.materialmascot.config.MascotConfig
import com.maxmaster.materialmascot.config.MascotFinish
import com.maxmaster.materialmascot.engine.BotState

@Preview(showBackground = true, backgroundColor = 0xFF0B0B0D)
@Composable
private fun PreviewChromeIdle() {
    MaterialBot(
        config = MascotConfig(
            state = BotState.Idle,
            color = Color(0xFF14161A),
            size = 120.dp,
            finish = MascotFinish.CHROME
        )
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0B0D)
@Composable
private fun PreviewChromeHappy() {
    MaterialBot(
        config = MascotConfig(
            state = BotState.Happy,
            color = Color(0xFF14161A),
            size = 120.dp,
            finish = MascotFinish.CHROME
        )
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0B0B0D)
@Composable
private fun PreviewChromeNarrating() {
    MaterialBot(
        config = MascotConfig(
            state = BotState.Narrating,
            color = Color(0xFF14161A),
            size = 120.dp,
            finish = MascotFinish.CHROME
        )
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun PreviewIdle() {
    MaterialBot(
        config = MascotConfig(
            state = BotState.Idle,
            color = Color(0xFF0A0A0C),
            size = 120.dp
        )
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun PreviewHappy() {
    MaterialBot(
        config = MascotConfig(
            state = BotState.Happy,
            color = Color(0xFF0A0A0C),
            size = 120.dp
        )
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun PreviewAlert() {
    MaterialBot(
        config = MascotConfig(
            state = BotState.Alert,
            color = Color(0xFF0A0A0C),
            size = 120.dp
        )
    )
}
