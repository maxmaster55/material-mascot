/*
 * Material Mascot — a living bot avatar for Android apps.
 * Copyright (C) 2026 Max Master
 * SPDX-License-Identifier: MIT
 */

package com.maxmaster.materialmascot.engine

/**
 * What the mascot is feeling, doing, or has turned into.
 *
 * The poses themselves live in the Rust core; each object here is a handle to
 * one of them. The bot has no mouth: it emotes through eye shape, body squash,
 * motion and small props.
 *
 * Emotions: [Idle], [Listening], [Thinking], [Working], [Responding],
 * [Narrating], [Reading], [Alert], [Notify], [Happy], [Smile], [Wink],
 * [Laugh], [Excited], [Love], [Proud], [Greet], [Confused], [Silly], [Sad],
 * [Sleepy], [Angry], [Dizzy].
 *
 * Objects: [Heart], [Star], [Droplet], [Cloud], [Ghost], [Bubble], [Bulb],
 * [Gear], [Moon], [Flame], [Bolt], [Diamond], [Bell], [Check], [Cross],
 * [Magnifier], [Hourglass], [Cat].
 *
 * Shape morphs kept from the first version: [Egg], [Hexagon], [Play],
 * [Exclaim], [Burst], [Comet], [Orbit], [Swirl].
 */
public sealed class BotState(
    /** Unique lowercase identifier, e.g. "idle", "thinking". */
    public val name: String,
    /** Position of this state in the core's list. */
    internal val id: Int,
    /**
     * Length of the state's main loop, in milliseconds; 0 when it has none.
     * Several states layer motions of different lengths, so treat this as the
     * dominant rhythm rather than an exact tiling period.
     */
    public val posePeriodMs: Long = 0L
) {
    /** Duration of the morph into this state, in seconds. */
    public val morphSeconds: Float get() = BotEngine.MORPH_MS / 1000f

    override fun toString(): String = name

    // --- emotions ---

    /** Calm breathing ball facing the viewer. */
    public object Idle : BotState("idle", 0, 3400L)

    /** Tall attentive eyes, sound arcs arriving from the side. */
    public object Listening : BotState("listening", 1, 3000L)

    /** Eyes up and away under half lids, three dots bobbing overhead. */
    public object Thinking : BotState("thinking", 2, 2800L)

    /** Focused half-lidded eyes scanning side to side, a small spinner. */
    public object Working : BotState("working", 3, 1600L)

    /** Lively bob with a typing speech bubble. */
    public object Responding : BotState("responding", 4, 600L)

    /** Reading aloud: eyes lowered, speaking-cadence bob. */
    public object Narrating : BotState("narrating", 5, 550L)

    /** Eyes tracking lines of text left to right over an open book. */
    public object Reading : BotState("reading", 6, 2600L)

    /** Wide round eyes, a buzz, and a flashing "!". */
    public object Alert : BotState("alert", 7, 500L)

    /** Looks up at a notification badge that pops beside it. */
    public object Notify : BotState("notify", 8, 2400L)

    /** Arched happy eyes and a bouncy hop, with sparkles. */
    public object Happy : BotState("happy", 9, 1800L)

    /** Soft arched eyes, a gentle sway and blushing cheeks. */
    public object Smile : BotState("smile", 10, 3200L)

    /** One eye open, one scrunched shut. */
    public object Wink : BotState("wink", 11, 3000L)

    /** Both eyes scrunched, shaking with laughter. */
    public object Laugh : BotState("laugh", 12, 640L)

    /** Star eyes and fast hops. */
    public object Excited : BotState("excited", 13, 1000L)

    /** Beating heart eyes, floating hearts, a giddy sway. */
    public object Love : BotState("love", 14, 1800L)

    /** Chin up, flat half-lidded eyes, a twinkle. */
    public object Proud : BotState("proud", 15, 3600L)

    /** Friendly hello: small hops and a waving hand. */
    public object Greet : BotState("greet", 16, 2600L)

    /** Mismatched eyes, a head tilt and a "?". */
    public object Confused : BotState("confused", 17, 2600L)

    /** One ring eye, one scrunched, rocking side to side. */
    public object Silly : BotState("silly", 18, 900L)

    /** Drooping lids, eyes lowered, a falling tear. */
    public object Sad : BotState("sad", 19, 3200L)

    /** Closed eyes, slow breathing, drifting Zs. */
    public object Sleepy : BotState("sleepy", 20, 3200L)

    /** Lids slanted down toward the nose, shaking, an anger mark. */
    public object Angry : BotState("angry", 21, 100L)

    /** Ring eyes, a wobble, stars circling overhead. */
    public object Dizzy : BotState("dizzy", 22, 1600L)

    // --- objects ---

    /** A beating heart. */
    public object Heart : BotState("heart", 23, 1100L)

    /** A twinkling star. */
    public object Star : BotState("star", 24, 2400L)

    /** A wobbling water droplet. */
    public object Droplet : BotState("droplet", 25, 1600L)

    /** A drifting rain cloud. */
    public object Cloud : BotState("cloud", 26, 4000L)

    /** A floating ghost with a rippling hem. */
    public object Ghost : BotState("ghost", 27, 2000L)

    /** A speech bubble with typing dots, no face. */
    public object Bubble : BotState("bubble", 28, 2400L)

    /** A lightbulb with flickering rays: an idea. */
    public object Bulb : BotState("bulb", 29, 800L)

    /** A turning gear. */
    public object Gear : BotState("gear", 30, 873L)

    /** A sleeping crescent moon. */
    public object Moon : BotState("moon", 31, 3400L)

    /** A flickering flame. */
    public object Flame : BotState("flame", 32, 400L)

    /** A lightning bolt throwing sparks, no face. */
    public object Bolt : BotState("bolt", 33, 625L)

    /** A sparkling gem. */
    public object Diamond : BotState("diamond", 34, 2200L)

    /** A ringing bell. */
    public object Bell : BotState("bell", 35, 500L)

    /** A check mark that pops in: done. No face. */
    public object Check : BotState("check", 36, 2400L)

    /** A cross that shakes: failed. No face. */
    public object Cross : BotState("cross", 37, 2400L)

    /** A magnifier circling as it searches, no face. */
    public object Magnifier : BotState("magnifier", 38, 2856L)

    /** An hourglass that flips over, no face. */
    public object Hourglass : BotState("hourglass", 39, 4800L)

    /** The ball with cat ears. */
    public object Cat : BotState("cat", 40, 3400L)

    // --- the original shape morphs ---

    /** Egg-shaped body, rocking. */
    public object Egg : BotState("egg", 41, 1400L)

    /** Hexagonal body with rounded corners. */
    public object Hexagon : BotState("hexagon", 42, 3000L)

    /** Rounded "play" triangle. */
    public object Play : BotState("play", 43, 1200L)

    /** Upright "!" glyph, hopping. No face. */
    public object Exclaim : BotState("exclaim", 44, 1600L)

    /** Collapses to a pebble while sparkles spiral in, then pops back. */
    public object Burst : BotState("burst", 45, 2600L)

    /** Shrinks to a comet dot with an orbiting trail. */
    public object Comet : BotState("comet", 46, 2400L)

    /** Triangle tumbles once while rings bloom, then settles into the ball. */
    public object Orbit : BotState("orbit", 47, 3400L)

    /** Rings flourishing around the resting ball. */
    public object Swirl : BotState("swirl", 48, 1800L)

    public companion object {
        // Lazy so the registry never builds during the sealed class's own
        // initialization window.
        /** Every state, in the core's order: emotions, objects, shape morphs. */
        public val all: List<BotState> by lazy {
            listOf(
                Idle, Listening, Thinking, Working, Responding, Narrating, Reading, Alert,
                Notify, Happy, Smile, Wink, Laugh, Excited, Love, Proud, Greet, Confused,
                Silly, Sad, Sleepy, Angry, Dizzy,
                Heart, Star, Droplet, Cloud, Ghost, Bubble, Bulb, Gear, Moon, Flame, Bolt,
                Diamond, Bell, Check, Cross, Magnifier, Hourglass, Cat,
                Egg, Hexagon, Play, Exclaim, Burst, Comet, Orbit, Swirl
            )
        }

        /**
         * Finds a [BotState] by name (case-insensitive).
         * Returns [Idle] if not found.
         */
        public fun fromName(name: String): BotState =
            all.find { it.name.equals(name, ignoreCase = true) } ?: Idle
    }
}
