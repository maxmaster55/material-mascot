package com.maxmaster.materialmascot.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** A decorative dot: circle, or the teardrop of the "!" glyph when [tear].
 *  [onBody] dots are painted on top of the body in the eye color. */
data class DotSpec(
    val x: Float,
    val y: Float,
    val r: Float,
    val alpha: Float = 1f,
    val tear: Boolean = false,
    val rotDeg: Float = 0f,
    val onBody: Boolean = false
)

/** Notification badge, positioned in ball-radius units. */
data class NotifSpec(val x: Float, val y: Float, val r: Float)

/**
 * One instant of the bot: silhouette, head orientation, eye configs and decor.
 * All geometry lives in ball-radius units (resting ball radius = 1).
 */
class Pose(
    val sil: Silhouette,
    val gaze: HeadGaze = Face.REST_GAZE,
    val split: Float = Face.EYE_SPLIT,
    val eye0: EyeCfg = EyeCfg(Face.EYE_W, Face.EYE_H),
    val eye1: EyeCfg = EyeCfg(Face.EYE_W, Face.EYE_H),
    val eyeAlpha: Float = 1f,
    val dots: List<DotSpec> = emptyList(),
    val notif: NotifSpec? = null
)

private fun basePose(
    sil: Silhouette = circle(1f),
    gaze: HeadGaze = Face.REST_GAZE,
    split: Float = Face.EYE_SPLIT,
    w0: Float = Face.EYE_W, h0: Float = Face.EYE_H, t0: Float = 0f,
    w1: Float = Face.EYE_W, h1: Float = Face.EYE_H, t1: Float = 0f,
    eyeAlpha: Float = 1f,
    dots: List<DotSpec> = emptyList(),
    notif: NotifSpec? = null
) = Pose(
    sil = sil, gaze = gaze, split = split,
    eye0 = EyeCfg(w0, h0, tilt = t0),
    eye1 = EyeCfg(w1, h1, tilt = t1),
    eyeAlpha = eyeAlpha, dots = dots, notif = notif
)

/* ------------------------------------------------- non-radial body shapes */

/**
 * Bar of the upright "!": convex hull of two circles.
 * Measured: top circle (0, -0.505) r 0.132, bottom circle (0, +0.130) r 0.075,
 * straight flanks — hence tapered (top/bottom ratio 1.76).
 */
private val EGG_RADII by lazy { eggProfile() }
private val HEX_RADII by lazy { hexagonProfile() }

private const val BAR_UPRIGHT_CY = -0.1875f
private val BAR_UPRIGHT_RADII =
    profileFromHullOfCircles(0f, -0.505f, 0.132f, 0f, 0.13f, 0.075f, 0f, BAR_UPRIGHT_CY)

/** Bar of the tilted "!": pure capsule (constant width 0.269, length 0.776). */
private val BAR_ITALIC_RADII =
    profileFromHullOfCircles(0f, -0.2535f, 0.1345f, 0f, 0.2535f, 0.1345f)

/**
 * The dot of the tilted "!" is a teardrop: round end (r 0.118) toward the bar,
 * pointed tail opposite, length 0.300 along the glyph axis.
 */
val TEAR_POINTS: Array<androidx.compose.ui.geometry.Offset> by lazy {
    hullOfCirclesPoints(0f, 0f, 0.118f, 0f, 0.172f, 0.012f)
}

/* ------------------------------------------------------------ state utils */

/** Three-dot layout of the processing animation, measured off the video. */
val DOT_X = floatArrayOf(-0.557f, -0.013f, 0.532f)
const val DOT_R = 0.165f
const val DOT_PEAK = 1.25f

/** Left-to-right pulse wave traveling across the three dots. */
fun dotPulse(t: Float, index: Int): Float {
    val raw = (t - index * 0.32f) / 1.0f
    val p = raw - kotlin.math.floor(raw)
    val k = if (p < 0.5f) 0.5f - 0.5f * cos(p * TAU) else 0f
    return clamp(k * 2f)
}

/** Processing pose shared by Thinking and Working.
 *  The body KEEPS its full size: three dots pulse across the middle in the
 *  eye color while the ball does a happy little bounce. */
private fun dotsPose(t: Float): Pose {
    val dots = (0..2).map { i ->
        val k = dotPulse(t, i)
        DotSpec(
            x = DOT_X[i] * 0.62f,
            y = 0.02f,
            r = DOT_R * 0.72f * (1f + (DOT_PEAK - 1f) * k),
            alpha = 0.55f + 0.45f * k,
            onBody = true
        )
    }
    return basePose(
        sil = circle(1f, cy = sin(t * (TAU / 1.2f)) * 0.006f),
        eyeAlpha = 0f,
        dots = dots
    )
}

/**
 * Emotional/operational states for the bot mascot.
 *
 * Poses are ported from bloub (SVG recreation of the x.ai Grok avatar):
 * single filled silhouette morphing through radial profiles, two capsule eyes
 * painted on a sphere, exponential ease-out transitions, life at rest through
 * gaze drift and blinking.
 *
 * - Idle: calm breathing ball
 * - Listening: wide surprised eyes ("wide" state)
 * - Thinking / Working: three pulsing dots, no face
 * - Responding: lively ball, slightly raised gaze
 * - Alert: tilted "!" glyph with travel and buzz
 * - Happy: wink
 * - Confused: mismatched eyes with mirrored tilts
 * - Sleepy: small bouncing ball, standby
 * - Notify: round wide eyes + blue notification badge pop
 */
sealed class BotState(
    val name: String,
    /** duration of the entry morph, seconds */
    val morphSeconds: Float,
    /** true = the entry is masked by a blink, as in the reference video */
    val blinkIn: Boolean,
    /** local state time (seconds) -> pose */
    internal val poseFn: (Float) -> Pose
) {
    // --- Core States ---

    object Idle : BotState(
        name = "idle",
        morphSeconds = 0.32f,
        blinkIn = false,
        poseFn = { t ->
            // Always a little alive: gentle happy bob on top of the rest life.
            // Faces the viewer directly (the video's sideways glance reads as
            // odd for a companion), with big friendly eyes.
            basePose(
                sil = circle(1f, cy = sin(t * (TAU / 1.7f)) * 0.006f),
                gaze = HeadGaze(yaw = 0f, pitch = 8f, roll = -8f),
                w0 = 0.21f, h0 = 0.45f,
                w1 = 0.21f, h1 = 0.45f
            )
        }
    )

    object Listening : BotState(
        name = "listening",
        morphSeconds = 0.38f,
        blinkIn = true,
        poseFn = { _ ->
            basePose(
                gaze = HeadGaze(yaw = 6.92f, pitch = -21.96f, roll = 11.6f),
                split = 18.43f,
                w0 = 0.356f, h0 = 0.875f,
                w1 = 0.356f, h1 = 0.875f
            )
        }
    )

    object Thinking : BotState(
        name = "thinking",
        morphSeconds = 0.28f,
        blinkIn = true,
        poseFn = ::dotsPose
    )

    object Responding : BotState(
        name = "responding",
        morphSeconds = 0.32f,
        blinkIn = false,
        poseFn = { t ->
            // Cheerful micro-bounce while streaming a reply.
            val bob = sin(t * (TAU / 0.8f)) * 0.018f
            basePose(
                sil = circle(1f, cy = bob),
                gaze = HeadGaze(yaw = 10f, pitch = 14f, roll = -13f),
                split = 16f,
                w0 = 0.196f, h0 = 0.42f,
                w1 = 0.196f, h1 = 0.42f
            )
        }
    )

    object Alert : BotState(
        name = "alert",
        morphSeconds = 0.32f,
        blinkIn = false,
        poseFn = { t ->
            // Alarmed, not transformed: the ball stays full size, stretches a
            // touch, vibrates at 2.5 Hz and opens huge round eyes.
            val buzz = sin(t * 2.5f * TAU) * 0.006f
            basePose(
                sil = circle(1f, cx = buzz, sx = 1.08f),
                gaze = HeadGaze(yaw = 0f, pitch = -24f, roll = 0f),
                split = 19f,
                w0 = 0.30f, h0 = 0.30f,
                w1 = 0.30f, h1 = 0.30f
            )
        }
    )

    object Happy : BotState(
        name = "happy",
        morphSeconds = 0.22f,
        blinkIn = true,
        poseFn = { _ ->
            basePose(
                gaze = HeadGaze(yaw = -5.37f, pitch = 4.55f, roll = 6.7f),
                split = 16.25f,
                // The closed eye is not the open eye squashed: it is a
                // horizontal dash WIDER than the open eye (0.447 vs 0.236).
                w0 = 0.236f, h0 = 0.464f,
                w1 = 0.447f, h1 = 0.089f
            )
        }
    )

    object Confused : BotState(
        name = "confused",
        morphSeconds = 0.28f,
        blinkIn = false,
        poseFn = { _ ->
            // Mirrored tilts between the two eyes are exactly what per-eye
            // tilt enables (see bloub EyeCfg.tilt); sizes mismatch too.
            basePose(
                gaze = HeadGaze(yaw = -14f, pitch = 2f, roll = -26f),
                split = 15f,
                w0 = 0.236f, h0 = 0.40f, t0 = 10f,
                w1 = 0.30f, h1 = 0.30f, t1 = -14f
            )
        }
    )

    object Sleepy : BotState(
        name = "sleepy",
        morphSeconds = 0.35f,
        blinkIn = false,
        poseFn = { t ->
            // Full-size body, slightly squashed and breathing slowly; the
            // eyes are two droopy closed dashes tilted away from each other.
            val breathe = sin(t * (TAU / 2.8f)) * 0.01f
            basePose(
                sil = circle(1f, sy = 0.95f + breathe),
                gaze = HeadGaze(yaw = 0f, pitch = -6f, roll = -13f),
                w0 = 0.447f, h0 = 0.089f, t0 = -8f,
                w1 = 0.447f, h1 = 0.089f, t1 = 8f
            )
        }
    )

    object Notify : BotState(
        name = "notify",
        morphSeconds = 0.35f,
        blinkIn = true,
        poseFn = { t ->
            // Blue badge pop: peak at +14 % around 0.3 s then settling.
            val p = clamp(t / 0.45f)
            val pop = 1f + (NOTIF_POP - 1f) * sin(p * PI.toFloat()) * (1f - p * 0.35f)
            val r = NOTIF_R * if (p < 1f) pop else 1f
            val a = NOTIF_ANGLE * PI.toFloat() / 180f
            basePose(
                // The gaze moves away from the badge.
                gaze = HeadGaze(yaw = -21.94f, pitch = -5.82f, roll = -12.2f),
                split = 18.89f,
                w0 = 0.505f, h0 = 0.498f,
                w1 = 0.505f, h1 = 0.498f,
                notif = NotifSpec(x = cos(a) * NOTIF_DIST, y = sin(a) * NOTIF_DIST, r = r)
            )
        }
    )

    object Working : BotState(
        name = "working",
        morphSeconds = 0.28f,
        blinkIn = true,
        poseFn = ::dotsPose
    )

    object Narrating : BotState(
        name = "narrating",
        morphSeconds = 0.32f,
        blinkIn = true,
        poseFn = { t ->
            // The companion reads aloud: eyes lowered toward the book, a soft
            // bob at speaking cadence, relaxed half-lowered capsules.
            basePose(
                sil = circle(1f, cy = sin(t * (TAU / 1.1f)) * 0.010f),
                gaze = HeadGaze(yaw = 0f, pitch = -16f, roll = -8f),
                w0 = 0.21f, h0 = 0.34f,
                w1 = 0.21f, h1 = 0.34f
            )
        }
    )

    object Sad : BotState(
        name = "sad",
        morphSeconds = 0.35f,
        blinkIn = false,
        poseFn = { t ->
            // Mirrored droopy tilts are exactly what per-eye tilt enables.
            val sigh = sin(t * (TAU / 3.2f)) * 0.006f
            basePose(
                sil = circle(1f, sy = 0.96f, cy = sigh),
                gaze = HeadGaze(yaw = -4f, pitch = -18f, roll = -18f),
                split = 14f,
                w0 = 0.236f, h0 = 0.36f, t0 = 12f,
                w1 = 0.236f, h1 = 0.36f, t1 = -12f
            )
        }
    )

    object Love : BotState(
        name = "love",
        morphSeconds = 0.28f,
        blinkIn = false,
        poseFn = { t ->
            // Round shining eyes and a giddy sway.
            basePose(
                sil = circle(1f, cx = sin(t * (TAU / 1.4f)) * 0.008f),
                gaze = HeadGaze(yaw = 0f, pitch = 10f, roll = -10f + sin(t * (TAU / 1.4f)) * 5f),
                split = 17f,
                w0 = 0.26f, h0 = 0.26f,
                w1 = 0.26f, h1 = 0.26f
            )
        }
    )

    object Silly : BotState(
        name = "silly",
        morphSeconds = 0.3f,
        blinkIn = true,
        poseFn = { _ ->
            // Crossed mismatched tilts: pure goofball.
            basePose(
                gaze = HeadGaze(yaw = 6f, pitch = -4f, roll = 14f),
                split = 13f,
                w0 = 0.30f, h0 = 0.24f, t0 = 20f,
                w1 = 0.20f, h1 = 0.40f, t1 = -22f
            )
        }
    )

    object Egg : BotState(
        name = "egg",
        morphSeconds = 0.4f,
        blinkIn = true,
        poseFn = { _ ->
            // bloub's egg body with its measured face, straightened to face
            // the viewer.
            basePose(
                sil = Silhouette(EGG_RADII),
                gaze = HeadGaze(yaw = 8f, pitch = 20f, roll = -14f),
                split = 12f,
                w0 = 0.164f, h0 = 0.385f,
                w1 = 0.164f, h1 = 0.385f
            )
        }
    )

    object Hexagon : BotState(
        name = "hexagon",
        morphSeconds = 0.4f,
        blinkIn = true,
        poseFn = { _ ->
            basePose(
                sil = Silhouette(HEX_RADII),
                gaze = HeadGaze(yaw = 9f, pitch = 18f, roll = -12f),
                split = 13f,
                w0 = 0.177f, h0 = 0.411f,
                w1 = 0.177f, h1 = 0.411f
            )
        }
    )

    companion object {
        // Lazy so the registry never builds during the sealed class's own
        // initialization window.
        val all: List<BotState> by lazy {
            listOf(
                Idle, Listening, Thinking, Responding, Alert, Happy, Confused,
                Sleepy, Notify, Working, Narrating, Sad, Love, Silly, Egg, Hexagon
            )
        }

        fun fromName(name: String): BotState =
            all.find { it.name.equals(name, ignoreCase = true) } ?: Idle
    }
}

/** Notification badge constants, measured (bloub decor.ts). */
const val NOTIF_BLUE: Long = 0xFF2496E8
const val NOTIF_ANGLE = -42f
const val NOTIF_DIST = 1.003f
const val NOTIF_R = 0.15f
const val NOTIF_POP = 1.09f
