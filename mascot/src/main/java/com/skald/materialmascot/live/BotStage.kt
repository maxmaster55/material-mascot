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
 * Single source of truth for "is a screen currently showing its own
 * companion". Each visible owner gets a [Claim], so overlapping Compose
 * transitions cannot let one departing owner clear another owner's claim.
 * Backed by snapshot state so every reader (floating hosts, scaffolds)
 * recomposes the exact frame the final claim changes.
 */
object BotStage {
    /** Opaque ownership token returned by [enter]. */
    class Claim internal constructor()

    private val activeClaims = mutableSetOf<Claim>()

    var bigCompanionOnStage: Boolean by mutableStateOf(false)
        private set

    /** Claims the stage until this exact token is passed to [exit]. */
    @Synchronized
    fun enter(): Claim {
        val claim = Claim()
        activeClaims += claim
        bigCompanionOnStage = true
        return claim
    }

    /**
     * Releases [claim]. Releasing an already-released token is harmless and
     * never affects another owner that is still on stage.
     */
    @Synchronized
    fun exit(claim: Claim) {
        if (activeClaims.remove(claim)) {
            bigCompanionOnStage = activeClaims.isNotEmpty()
        }
    }
}

/**
 * The process-wide [MascotStatusBus], provided once at the app root.
 * Every [LiveBot] reads its mood from here.
 */
val LocalMascotStatus = staticCompositionLocalOf<MascotStatusBus?> { null }

/** Provided at the activity root; null where the shake feature is not installed. */
val LocalShakeDizzy = staticCompositionLocalOf<ShakeDizzyController?> { null }
