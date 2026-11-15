/*
 * Material Mascot — a living bot avatar for Android apps.
 * Copyright (C) 2026 Max Master
 * SPDX-License-Identifier: MIT
 */

package com.maxmaster.materialmascot.engine

/**
 * Personality for the eyes.
 *
 * A style reshapes the plain eye of every state; expression shapes (hearts,
 * happy arcs, chevrons) keep their meaning, so a wink stays a wink.
 *
 * Use with [com.maxmaster.materialmascot.config.MascotConfig.eyeStyle].
 */
public enum class EyeStyle(internal val nativeId: Int) {
    /** Giant glossy ovals, set a little wider apart. */
    GROK(0),

    /** Tall capsules: the original measured look. */
    CLASSIC(1),

    /** Classic, scaled up so the face reads at a glance. */
    BIG(2),

    /** Big and perfectly circular: curious cartoon mascot. */
    ROUND(3),

    /** Wide whites with a dark pupil that follows the gaze. */
    PUPIL(4),

    /** Square-cornered. */
    BLOCK(5),

    /** Wide horizontal visor slits: robotic, deadpan. */
    VIZOR(6),

    /** Beady little dots: understated, slightly mischievous. */
    DOT(7)
}
