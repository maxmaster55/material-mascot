/*
 * Material Mascot — a living bot avatar for Android apps.
 * Copyright (C) 2026 Skald contributors
 * SPDX-License-Identifier: GPL-3.0-only
 */

package com.maxmaster.materialmascot.live

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Single source of truth for "is a screen currently showing its own big
 * companion". Backed by snapshot state so every reader (floating hosts,
 * scaffolds) recomposes the exact frame the claim changes — no stale pills,
 * no late hide/show.
 */
object BotStage {
    var bigCompanionOnStage: Boolean by mutableStateOf(false)
        private set

    fun enter() {
        bigCompanionOnStage = true
    }

    fun exit() {
        bigCompanionOnStage = false
    }
}

/**
 * The process-wide [MascotStatusBus], provided once at the app root.
 * Every [LiveBot] reads its mood from here.
 */
val LocalMascotStatus = staticCompositionLocalOf<MascotStatusBus?> { null }

/** Provided at the activity root; null where the shake feature is not installed. */
val LocalShakeDizzy = staticCompositionLocalOf<ShakeDizzyController?> { null }
