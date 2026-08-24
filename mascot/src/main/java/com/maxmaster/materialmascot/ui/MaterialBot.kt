/*
 * Material Mascot — a living bot avatar for Android apps.
 * Copyright (C) 2026 Max Master
 * SPDX-License-Identifier: MIT
 */

package com.maxmaster.materialmascot.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.maxmaster.materialmascot.config.MascotConfig
import com.maxmaster.materialmascot.config.MascotFinish
import com.maxmaster.materialmascot.engine.BotEngine
import com.maxmaster.materialmascot.engine.BotState
import com.maxmaster.materialmascot.engine.DotSpec
import com.maxmaster.materialmascot.engine.EAR_LEFT
import com.maxmaster.materialmascot.engine.EAR_RIGHT
import com.maxmaster.materialmascot.engine.EAR_LEFT_PIVOT
import com.maxmaster.materialmascot.engine.EAR_RIGHT_PIVOT
import com.maxmaster.materialmascot.engine.EAR_TIP_Y
import com.maxmaster.materialmascot.engine.NOTIF_BLUE
import com.maxmaster.materialmascot.engine.RenderedEye
import com.maxmaster.materialmascot.engine.TEAR_POINTS
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Renders the bot mascot using Compose Canvas.
 *
 * This is the main entry point for displaying the mascot in your UI.
 * Create a [MascotConfig] with your desired settings and pass it here.
 *
 * The bot animates autonomously based on time — no manual frame advancement
 * needed. State changes are smooth morph transitions.
 *
 * @param config Host-owned immutable configuration. See [MascotConfig].
 * @param modifier Layout modifier for positioning, sizing, etc.
 * @param contentDescription Accessibility label; null omits the semantics node.
 *                           Provide a description like "Bot mascot" for accessibility.
 * @param onClick Optional host action run when the bot is tapped, in addition
 *                to its own poke reaction. Null leaves the bot decorative.
 *
 * @sample com.maxmaster.materialmascot.sample.SampleUsage.ChatWithBot
 * @sample com.maxmaster.materialmascot.sample.SampleUsage.AccessibleBot
 */
@Composable
public fun MaterialBot(
    config: MascotConfig,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
    onClick: (() -> Unit)? = null
) {
    if (!config.enabled) return

    val sizePx = with(LocalDensity.current) { config.size.toPx() }
    // Ears reach above the crown, so cat mode draws the whole bot smaller: the
    // eared silhouette then occupies exactly the footprint the earless one
    // does, and every host's margin and padding around `size` still holds.
    val drawPx = if (config.cat) sizePx * CAT_FIT else sizePx

    var frameMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameMs = it / 1_000_000L }
        }
    }

    val engine = remember { BotEngine(config.initialState) }
    // Read the ticker's CURRENT value when a state change lands: mixing clocks
    // here (epoch vs uptime nanos) would make every morph complete instantly.
    val latestFrameMs by rememberUpdatedState(frameMs)

    // Poke reaction: a tap makes the bot wink at you for a moment, whatever
    // state it is in. Pure delight, no configuration needed.
    var pokes by remember { mutableIntStateOf(0) }
    var celebrating by remember { mutableStateOf(false) }
    LaunchedEffect(pokes) {
        if (pokes == 0) return@LaunchedEffect
        celebrating = true
        delay(1100)
        celebrating = false
    }
    val shownState = if (celebrating && config.state != BotState.Happy) BotState.Happy else config.state

    LaunchedEffect(shownState) {
        if (engine.state != shownState) {
            engine.setState(shownState, latestFrameMs)
        }
    }

    // Ears read the mood: perked and forward when alert or pleased, splayed
    // and low when sleepy or sad. Animated rather than snapped so they arrive
    // with the body morph instead of popping ahead of it.
    val earPose = earPoseFor(shownState)
    val earPerk by animateFloatAsState(
        targetValue = if (config.cat) earPose.perk else 1f,
        animationSpec = tween(durationMillis = 420),
        label = "earPerk"
    )
    val earSplay by animateFloatAsState(
        targetValue = if (config.cat) earPose.splay else 0f,
        animationSpec = tween(durationMillis = 420),
        label = "earSplay"
    )

    val canvasModifier = if (contentDescription != null) {
        modifier
            .size(config.size)
            .semantics { this.contentDescription = contentDescription }
    } else {
        modifier.size(config.size)
    }

    // Reused across frames: allocating paths and matrices at 60 fps makes the
    // garbage collector stutter on device.
    // Every gradient is derived from the configured colors and the canvas size,
    // so it is built once per palette change — never per frame, which is what
    // drawing at display refresh rate demands.
    val chrome = remember(config.color, config.eyeColor, drawPx, config.finish) {
        if (config.finish == MascotFinish.CHROME) {
            ChromeBrushes(config.color, config.eyeColor, drawPx)
        } else null
    }

    val bodyPath = remember { Path() }
    val earPath = remember { Path() }
    val tearPath = remember { Path() }
    // The body outline with the ears merged into it, refilled every frame.
    val catPoints = remember { ArrayList<Offset>(96) }
    val catSplice = remember { ArrayList<Offset>(EAR_LEFT.size + 4) }
    val earXy = remember { FloatArray(EAR_LEFT.size * 2) }
    val eyeSrcPath = remember { Path() }
    val eyeDstPath = remember { Path() }
    val eyeMatrix = remember { android.graphics.Matrix() }

    Canvas(
        // The poke reaction always fires; a host action rides along with it,
        // so the bot can be a real button on screens that give it a job
        // without losing the delight of prodding it.
        modifier = canvasModifier.pointerInput(onClick) {
            detectTapGestures {
                pokes++
                onClick?.invoke()
            }
        }
    ) {
        // Sample in the draw phase: the ticker state is read here, so a new
        // frame invalidates only this canvas' draw — not recomposition.
        // Sampling during composition made every visible bot rebuild its
        // whole composable at display refresh rate.
        val frame = engine.sample(
            nowMs = frameMs,
            scalePx = drawPx,
            reducedMotion = !config.motion,
            energy = config.energy,
            eyeStyle = config.eyeStyle
        )
        val outline = config.finish == MascotFinish.OUTLINE
        // With no fill behind them, eyes in the configured eye color would sit
        // on the page rather than in the bot — so line art draws them in the
        // same ink as the stroke.
        val inkColor = if (outline) config.color else config.eyeColor
        translate(this.center.x, this.center.y) {
            val silhouette = if (config.cat) {
                val twitch = if (config.motion) earTwitch(frameMs, config.energy) else 0f
                catSilhouette(
                    points = frame.bodyPoints,
                    sizePx = drawPx,
                    perk = earPerk,
                    splayDeg = earSplay,
                    twitchDeg = twitch,
                    twitchSide = twitchSide(frameMs),
                    xy = earXy,
                    splice = catSplice,
                    out = catPoints
                )
            } else {
                frame.bodyPoints
            }
            buildBodyPath(silhouette, bodyPath)
            when {
                chrome != null -> drawChromeBody(bodyPath, chrome)
                outline -> drawOutlineBody(bodyPath, config.color, drawPx)
                else -> drawBody(bodyPath, config.color)
            }
            if (frame.arcs.isNotEmpty()) {
                val ballR = drawPx * BotEngine.RADIUS_FRACTION
                frame.arcs.forEach { arc ->
                    drawCircle(
                        color = inkColor,
                        radius = arc.r * ballR,
                        center = Offset(arc.x, arc.y),
                        alpha = arc.alpha,
                        style = Stroke(width = arc.stroke * ballR)
                    )
                }
            }
            frame.dots.forEach { drawDot(it, config.color, inkColor, drawPx, tearPath) }
            frame.eyes.forEach {
                drawEye(
                    it, inkColor, eyeSrcPath, eyeDstPath, eyeMatrix,
                    glow = chrome?.eyesGlow == true
                )
            }
            frame.notif?.let {
                drawCircle(
                    color = Color(NOTIF_BLUE),
                    radius = it.rPx,
                    center = Offset(it.x, it.y)
                )
            }
        }
    }
}

/**
 * The gradients behind [MascotFinish.CHROME], all expressed in the canvas'
 * centre-origin space so they can be built once and reused every frame.
 *
 * Each tone is derived from the host's body color rather than hardcoded, so a
 * mint or lavender bot gets the same lit-metal treatment as a near-black one.
 */
private class ChromeBrushes(color: Color, eyeColor: Color, sizePx: Float) {

    /**
     * Bloom is light spilling out of the eye, so it only makes sense when the
     * eye is brighter than the body. Blooming a dark eye on a pale body just
     * paints grey rings around it.
     */
    val eyesGlow: Boolean = eyeColor.luminance() > color.luminance() + 0.15f

    val ballR: Float = sizePx * BotEngine.RADIUS_FRACTION

    private val top = lerp(color, Color.White, 0.18f)
    private val bottom = lerp(color, Color.Black, 0.35f)
    private val sheen = lerp(color, Color.White, 0.55f)

    /** Lit from above, falling into shadow at the base. */
    val body: Brush = Brush.verticalGradient(
        colors = listOf(top, color, bottom),
        startY = -ballR,
        endY = ballR
    )

    /** The edge catch-light that does most of the work of reading as metal. */
    val rim: Brush = Brush.verticalGradient(
        colors = listOf(sheen.copy(alpha = 0.9f), sheen.copy(alpha = 0f)),
        startY = -ballR,
        endY = ballR * 0.25f
    )
    val rimWidth: Float = ballR * 0.022f

    /** One soft highlight, upper-left, clipped to the body. */
    val specular: Brush = Brush.radialGradient(
        colors = listOf(Color.White.copy(alpha = 0.20f), Color.White.copy(alpha = 0f)),
        center = Offset(-ballR * 0.34f, -ballR * 0.46f),
        radius = ballR * 0.62f
    )
    val specularTopLeft = Offset(-ballR * 0.72f, -ballR * 0.84f)
    val specularSize = Size(ballR * 0.78f, ballR * 0.58f)

    /** Contact shadow, so the bot sits in space instead of floating on it. */
    val shadow: Brush = Brush.radialGradient(
        colors = listOf(Color.Black.copy(alpha = 0.26f), Color.Black.copy(alpha = 0f)),
        center = Offset(0f, ballR * 0.99f),
        radius = ballR * 0.95f
    )
    val shadowTopLeft = Offset(-ballR * 0.95f, ballR * 0.72f)
    val shadowSize = Size(ballR * 1.9f, ballR * 0.54f)
}

/** Same silhouette as [drawBody], painted with depth instead of one flat fill. */
private fun DrawScope.drawChromeBody(path: Path, chrome: ChromeBrushes) {
    drawOval(brush = chrome.shadow, topLeft = chrome.shadowTopLeft, size = chrome.shadowSize)
    drawPath(path = path, brush = chrome.body)
    drawPath(path = path, brush = chrome.rim, style = Stroke(width = chrome.rimWidth))
    clipPath(path) {
        drawOval(brush = chrome.specular, topLeft = chrome.specularTopLeft, size = chrome.specularSize)
    }
}

/** [MascotFinish.OUTLINE]: the same silhouette as a stroke, with no fill. */
private fun DrawScope.drawOutlineBody(
    path: Path,
    color: Color,
    sizePx: Float
) {
    drawPath(
        path = path,
        color = color,
        // Scaled off the bot's radius so the line keeps its weight at any size.
        style = Stroke(width = sizePx * BotEngine.RADIUS_FRACTION * 0.06f)
    )
}

private fun DrawScope.drawBody(path: Path, color: Color) {
    drawPath(path = path, color = color)
}

/** Catmull-Rom style closed spline through the sampled silhouette points. */
private fun buildBodyPath(points: List<Offset>, path: Path) {
    val n = points.size
    if (n < 3) return
    path.reset()
    path.moveTo(points[0].x, points[0].y)
    for (i in 0 until n) {
        val p0 = points[(i - 1 + n) % n]
        val p1 = points[i]
        val p2 = points[(i + 1) % n]
        val p3 = points[(i + 2) % n]
        path.cubicTo(
            p1.x + (p2.x - p0.x) / 6f,
            p1.y + (p2.y - p0.y) / 6f,
            p2.x - (p3.x - p1.x) / 6f,
            p2.y - (p3.y - p1.y) / 6f,
            p2.x,
            p2.y
        )
    }
    path.close()
}

private fun DrawScope.drawDot(
    dot: DotSpec,
    color: Color,
    onBodyColor: Color,
    sizePx: Float,
    path: Path
) {
    if (!dot.tear) {
        // Dots painted "on" the body belong to face-less states and take the
        // eye color so they read as features, not holes.
        drawCircle(
            color = if (dot.onBody) onBodyColor else color,
            radius = dot.r,
            center = Offset(dot.x, dot.y),
            alpha = dot.alpha
        )
        return
    }
    val ballR = sizePx * BotEngine.RADIUS_FRACTION
    val rad = dot.rotDeg * PI.toFloat() / 180f
    val cr = cos(rad)
    val sr = sin(rad)
    path.reset()
    TEAR_POINTS.forEachIndexed { i, p ->
        val rx = p.x * cr - p.y * sr
        val ry = p.x * sr + p.y * cr
        val point = Offset(dot.x + rx * ballR, dot.y + ry * ballR)
        if (i == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
    }
    path.close()
    drawPath(path = path, color = color, alpha = dot.alpha)
}

/**
 * How far an ear may rotate. With the body painted over the bases the join
 * can no longer open, so this is a pose-sanity bound now: beyond it the ears
 * would read as antennae rather than ears.
 */
private const val EAR_SPLAY_LIMIT = 12f

/** How an ear sits: tip length multiplier, and outward rotation in degrees. */
private class EarPose(val perk: Float, val splay: Float)

/**
 * Cat body language, mapped onto the bot's states: forward and tall when
 * something has its attention, splayed and low when it is winding down.
 */
private fun earPoseFor(state: BotState): EarPose = when (state) {
    BotState.Alert, BotState.Exclaim, BotState.Notify -> EarPose(EAR_PERK_MAX, -8f)
    BotState.Listening -> EarPose(1.10f, -5f)
    BotState.Greet -> EarPose(1.14f, -4f)
    BotState.Happy, BotState.Smile, BotState.Proud, BotState.Love -> EarPose(1.12f, -3f)
    BotState.Responding, BotState.Narrating -> EarPose(1.03f, -1f)
    BotState.Working, BotState.Reading -> EarPose(1.0f, 2f)
    BotState.Thinking -> EarPose(0.97f, 5f)
    BotState.Confused -> EarPose(0.93f, 8f)
    BotState.Silly, BotState.Play, BotState.Swirl -> EarPose(1.08f, 9f)
    BotState.Sleepy -> EarPose(0.80f, 10f)
    BotState.Sad -> EarPose(0.76f, EAR_SPLAY_LIMIT)
    else -> EarPose(1f, 0f)
}

/** Which ear flicks this cycle; they take turns. */
private fun twitchSide(nowMs: Long): Int =
    if ((nowMs / TWITCH_PERIOD_MS) % 2L == 0L) -1 else 1

/**
 * The idle ear flick — a real cat never holds an ear perfectly still, and one
 * quick twitch every few seconds is what sells the bot as awake.
 */
private fun earTwitch(nowMs: Long, energy: Float): Float {
    val phase = (nowMs % TWITCH_PERIOD_MS).toFloat()
    if (phase > TWITCH_MS) return 0f
    return sin(phase / TWITCH_MS * PI.toFloat()) * 4f * energy.coerceIn(0.5f, 2f)
}

private const val TWITCH_PERIOD_MS = 5200L
private const val TWITCH_MS = 260f

private fun mix(a: Offset, b: Offset, t: Float) =
    Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

/** Half-span of the traced ear pair, in head radii. Kept modest so the ears
 *  read as a quiet accent on the silhouette, not a second character. */
private const val EAR_WIDTH = 0.74f

/** Global tip-height damping on top of the pose perk — the same "less is
 *  more" intent as [EAR_WIDTH]: cat, but understated. */
private const val EAR_TIP_DAMP = 0.9f

/** The tallest pose perk in [earPoseFor] — the reach behind [CAT_FIT] is
 *  measured at it. */
private const val EAR_PERK_MAX = 1.20f

/** How much wider the ear gets at its base than it was traced, tapering to
 *  nothing at the tip. Broadens the part that shows above the head without
 *  turning the tip blunt. */
private const val EAR_BASE_FLARE = 0.20f

/** How far the ear bases sit below the crown, in head radii. Deep enough that the body swallows the base curve at every pose, shallow enough that a good stretch of ear still shows. */
private const val EAR_SINK = 0.45f

/**
 * How far the eared silhouette stands from the body centre, in ball radii,
 * worst case over every pose corner — splay swings a tip further out than the
 * upright height alone, so this is measured with `preview_ears.py --reach`
 * rather than derived. That tool asserts it still holds.
 */
private const val EAR_REACH = 1.62f

/**
 * How much smaller cat mode draws the bot, so the ear tips land where the
 * plain bot's crown does and the footprint the host padded for is unchanged.
 */
private const val CAT_FIT = 1f / EAR_REACH

/**
 * The body outline with the ears merged into it, as one list of points.
 *
 * An ear painted as its own shape reads as a shape stuck on the head: wherever
 * its outline crosses the body's, the union leaves a corner, and a corner is
 * what looks rigid. So the ear is never a separate shape — the stretch of it
 * that stands outside the body replaces the stretch of body outline it covers,
 * and [buildBodyPath] splines the result. A spline through points has no
 * corners, so the ear flows out of the head and back into it, and every finish,
 * clip and fill downstream sees one silhouette.
 *
 * The junctions are the exact crossing points, interpolated, so the seam sits
 * on the outline rather than near it.
 */
private fun catSilhouette(
    points: List<Offset>,
    sizePx: Float,
    perk: Float,
    splayDeg: Float,
    twitchDeg: Float,
    twitchSide: Int,
    xy: FloatArray,
    splice: ArrayList<Offset>,
    out: ArrayList<Offset>
): List<Offset> {
    val n = points.size
    if (n < 8) return points
    val ballR = sizePx * BotEngine.RADIUS_FRACTION
    val half = ballR * EAR_WIDTH
    val sink = ballR * EAR_SINK

    val crown = points[n * 3 / 4]
    val crownLen = kotlin.math.hypot(crown.x, crown.y)
    if (crownLen < 1e-3f) return points
    // Outward at the crown, and its perpendicular: the pair's up and right.
    val ux = crown.x / crownLen
    val uy = crown.y / crownLen
    val e1x = -uy * half
    val e1y = ux * half
    val e2x = ux * half
    val e2y = uy * half

    out.clear()
    // Each ear becomes a run of points to splice in, plus the arc of body
    // outline it replaces. Two runs, kept flat to avoid per-frame garbage.
    var runs = 0
    val runFrom = FloatArray(2)
    val runTo = FloatArray(2)
    val runStart = IntArray(2)
    val runEnd = IntArray(2)
    splice.clear()

    for (side in intArrayOf(-1, 1)) {
        val shape = if (side < 0) EAR_LEFT else EAR_RIGHT
        val pivot = if (side < 0) EAR_LEFT_PIVOT else EAR_RIGHT_PIVOT
        val pose = (splayDeg + (if (side == twitchSide) twitchDeg else 0f))
            .coerceIn(-EAR_SPLAY_LIMIT, EAR_SPLAY_LIMIT)
        val rad = -side * pose * PI.toFloat() / 180f
        val c = cos(rad)
        val sn = sin(rad)

        val ox = crown.x - ux * sink + e1x * pivot * (1f - c) - e2x * pivot * sn
        val oy = crown.y - uy * sink + e1y * pivot * (1f - c) - e2y * pivot * sn
        val xx = e1x * c + e2x * sn
        val xy2 = e1y * c + e2y * sn
        val yx = (-e1x * sn + e2x * c) * perk * EAR_TIP_DAMP
        val yy = (-e1y * sn + e2y * c) * perk * EAR_TIP_DAMP

        val m = shape.size
        for (i in 0 until m) {
            val p = shape[i]
            // The ear flares toward its base: the traced tip is what reads as
            // cat, the bottom is what reads as attached.
            val flare = 1f + EAR_BASE_FLARE * (1f - (p.y / EAR_TIP_Y).coerceIn(0f, 1f))
            val px = p.x * flare
            xy[i * 2] = ox + xx * px + yx * p.y
            xy[i * 2 + 1] = oy + xy2 * px + yy * p.y
        }
        // Walk the ear looking for the one run that stands outside the body.
        var first = -1
        for (i in 0 until m) {
            val inside = insideBody(points, xy[i * 2], xy[i * 2 + 1])
            val nextOut = !insideBody(points, xy[((i + 1) % m) * 2], xy[((i + 1) % m) * 2 + 1])
            if (inside && nextOut) {
                first = (i + 1) % m
                break
            }
        }
        if (first < 0) continue          // wholly buried: nothing to splice
        var count = 0
        while (count < m &&
            !insideBody(points, xy[((first + count) % m) * 2], xy[((first + count) % m) * 2 + 1])
        ) {
            count++
        }
        val enter = crossing(points, xy, (first - 1 + m) % m, first)
        val leave = crossing(points, xy, (first + count - 1) % m, (first + count) % m)
        val aEnter = angleOf(enter)
        val aLeave = angleOf(leave)
        // The run has to read in the body's own winding direction.
        val forward = arcLength(aEnter, aLeave) < PI.toFloat()
        runStart[runs] = splice.size
        splice.add(if (forward) enter else leave)
        for (k in 0 until count) {
            val i = (if (forward) first + k else first + count - 1 - k) % m
            splice.add(Offset(xy[i * 2], xy[i * 2 + 1]))
        }
        splice.add(if (forward) leave else enter)
        runEnd[runs] = splice.size
        runFrom[runs] = if (forward) aEnter else aLeave
        runTo[runs] = if (forward) aLeave else aEnter
        runs++
    }
    if (runs == 0) return points

    val emitted = BooleanArray(runs)
    for (i in 0 until n) {
        val a = angleOf(points[i])
        val prev = angleOf(points[(i - 1 + n) % n])
        for (r in 0 until runs) {
            // Splice the ear in as soon as the outline reaches where it starts.
            if (!emitted[r] && arcLength(prev, runFrom[r]) <= arcLength(prev, a)) {
                for (k in runStart[r] until runEnd[r]) out.add(splice[k])
                emitted[r] = true
            }
        }
        var covered = false
        for (r in 0 until runs) {
            if (arcLength(runFrom[r], a) <= arcLength(runFrom[r], runTo[r])) covered = true
        }
        if (!covered) out.add(points[i])
    }
    for (r in 0 until runs) {
        if (!emitted[r]) for (k in runStart[r] until runEnd[r]) out.add(splice[k])
    }
    return out
}

/** Is (x, y) inside the sampled silhouette? Compared along its own direction,
 *  which is exact for the radial profiles this engine draws. */
private fun insideBody(points: List<Offset>, x: Float, y: Float): Boolean {
    val len = kotlin.math.hypot(x, y)
    if (len < 1e-4f) return true
    return len <= outlineRadius(points, x / len, y / len)
}

/** Distance from the centre to the sampled outline in direction (dx, dy). */
private fun outlineRadius(points: List<Offset>, dx: Float, dy: Float): Float {
    // ponytail: linear scan of 64 samples; index by angle if it ever shows up
    // in a frame trace.
    var bestDot = -Float.MAX_VALUE
    var best = 0f
    points.forEach { p ->
        val len = kotlin.math.hypot(p.x, p.y)
        if (len > 1e-3f) {
            val dot = (p.x * dx + p.y * dy) / len
            if (dot > bestDot) {
                bestDot = dot
                best = len
            }
        }
    }
    return best
}

/** Where the ear outline crosses the body outline, between two of its points. */
private fun crossing(points: List<Offset>, xy: FloatArray, i: Int, j: Int): Offset {
    var lo = 0f
    var hi = 1f
    val ax = xy[i * 2]
    val ay = xy[i * 2 + 1]
    val bx = xy[j * 2]
    val by = xy[j * 2 + 1]
    repeat(12) {
        val t = (lo + hi) / 2f
        val x = ax + (bx - ax) * t
        val y = ay + (by - ay) * t
        if (insideBody(points, x, y)) lo = t else hi = t
    }
    val t = (lo + hi) / 2f
    return Offset(ax + (bx - ax) * t, ay + (by - ay) * t)
}

private fun angleOf(p: Offset): Float {
    val a = kotlin.math.atan2(p.y, p.x)
    return if (a < 0f) a + 2f * PI.toFloat() else a
}

/** Distance from angle [from] to angle [to], going the way the outline runs. */
private fun arcLength(from: Float, to: Float): Float {
    val d = to - from
    return if (d < 0f) d + 2f * PI.toFloat() else d
}

private fun DrawScope.drawEye(
    eye: RenderedEye,
    color: Color,
    src: Path,
    dst: Path,
    matrix: android.graphics.Matrix,
    glow: Boolean = false
) {
    val corner = minOf(eye.wPx, eye.hPx) / 2f
    src.rewind()
    src.addRoundRect(RoundRect(0f, 0f, eye.wPx, eye.hPx, CornerRadius(corner, corner)))
    matrix.reset()
    matrix.setValues(
        floatArrayOf(
            eye.a, eye.c, eye.tx,
            eye.b * eye.blinkK, eye.d * eye.blinkK, eye.ty,
            0f, 0f, 1f
        )
    )
    src.asAndroidPath().transform(matrix, dst.asAndroidPath())
    if (glow && eye.alpha > 0f) {
        // Two expanding copies at falling alpha read as bloom without a blur
        // mask filter, which fights hardware acceleration on some devices.
        val pivot = dst.getBounds().center
        scale(scaleX = 1.55f, scaleY = 1.55f, pivot = pivot) {
            drawPath(path = dst, color = color, alpha = eye.alpha * 0.14f)
        }
        scale(scaleX = 1.25f, scaleY = 1.25f, pivot = pivot) {
            drawPath(path = dst, color = color, alpha = eye.alpha * 0.22f)
        }
    }
    drawPath(path = dst, color = color, alpha = eye.alpha)
}

// --- Previews ---

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