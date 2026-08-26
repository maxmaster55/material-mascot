/*
 * Material Mascot — a living bot avatar for Compose apps.
 * Copyright (C) 2026 Max Master
 * SPDX-License-Identifier: MIT
 */

package com.maxmaster.materialmascot.live

import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import kotlin.math.min
import kotlin.math.sin

/**
 * How dizzy the bot currently is, and the clock the wobble runs on.
 *
 * Only the *state* is common: what shakes the bot is inherently a platform
 * concern (an accelerometer on Android, nothing at all on desktop), so the
 * producer lives in the platform source set while everything that merely
 * *reads* dizziness — [dizzyWobble], the Silly-face swap in [LiveBot] — stays
 * here.
 *
 * Both values are snapshot state meant to be read inside draw-phase lambdas
 * (graphicsLayer), so watching them costs redraws only, never recomposition.
 * Hosts may also read [level] in composition (e.g. to swap the face past
 * [DIZZY_FACE_THRESHOLD]); writes happen only inside the library.
 */
public interface DizzySource {

    /** Current dizziness: 0 = sober, 1+ = very dizzy. */
    public val level: MutableFloatState

    /** Frame phase for the wobble; advanced only while dizzy. */
    public val phase: MutableFloatState
}

/** Above this dizziness the bot's face gives up and goes Silly. */
public const val DIZZY_FACE_THRESHOLD: Float = 0.25f

/**
 * Wobble transform for a dizzy bot: a decaying roll plus a horizontal stagger.
 * Reads draw-phase state only, so it costs recomposition exactly zero.
 * Pass [enabled] = false to honour reduced-motion preferences.
 *
 * This is applied automatically by [LiveBot]. Use directly if rendering
 * [com.maxmaster.materialmascot.ui.MaterialBot] yourself and you want the
 * shake wobble.
 *
 * @param controller A [DizzySource] — on Android a `ShakeDizzyController`
 *                   driven by `ShakeDizzyEffect` — or null to disable.
 * @param enabled If false, the modifier is a no-op (respects reduced-motion).
 */
public fun Modifier.dizzyWobble(controller: DizzySource?, enabled: Boolean = true): Modifier =
    composed {
        if (controller == null || !enabled) return@composed this
        val level by controller.level
        val phase by controller.phase
        graphicsLayer {
            val d = level
            if (d <= 0.01f) return@graphicsLayer
            val p = phase
            rotationZ = sin(p * 2f) * 14f * min(d, 1f)
            translationX = sin(p * 3.4f) * 7f * min(d, 1f)
        }
    }

/** Wobble frequency while dizzy, in Hz. Shared with the platform decay clock. */
internal const val WOBBLE_HZ = 9f

/** Dizziness shed per second of calm — sober in roughly two and a half seconds. */
internal const val SOBER_RATE = 0.42f
