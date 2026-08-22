/*
 * Material Mascot — a living bot avatar for Android apps.
 * Copyright (C) 2026 Max Master
 * SPDX-License-Identifier: MIT
 */

package com.maxmaster.materialmascot.config

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.maxmaster.materialmascot.engine.BotState
import com.maxmaster.materialmascot.engine.EyeStyle

/**
 * Host-owned immutable configuration for the Material Bot mascot.
 *
 * All fields are immutable so this can be backed by DataStore,
 * ViewModel state, or any other settings system.
 *
 * @param enabled Whether the mascot is visible. Default: true.
 * @param state Current emotional/operational state. Default: [BotState.Idle].
 * @param initialState The state shown on first composition before any state changes. Default: [BotState.Idle].
 * @param color Body fill color; defaults to bloub's 'encre' ink (0xFF0A0A0C).
 * @param eyeColor Eye fill color; defaults to white.
 * @param size Canvas size in dp. Default 64dp.
 * @param motion If false, disables decorative transitions but keeps life
 *               (breath, gaze drift, blinking) per accessibility guidelines.
 * @param energy Liveliness multiplier: 1.0 = measured calm of the reference
 *               video, ~1.6 = cheerful and active. Scales gaze drift speed,
 *               blink cadence, breathing and floating amplitude. Default: 1.9.
 * @param eyeStyle Eye shape personality transform. Default: [EyeStyle.CLASSIC].
 */
@Immutable
public data class MascotConfig(
    public val enabled: Boolean = true,
    public val state: BotState = BotState.Idle,
    public val initialState: BotState = BotState.Idle,
    public val color: Color = Color(0xFF0A0A0C),
    public val eyeColor: Color = Color.White,
    public val size: Dp = 64.dp,
    public val motion: Boolean = true,
    public val energy: Float = 1.9f,
    public val eyeStyle: EyeStyle = EyeStyle.CLASSIC
)