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
import androidx.compose.ui.graphics.ClipOp
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
import com.maxmaster.materialmascot.engine.EAR_LEFT_BASE
import com.maxmaster.materialmascot.engine.EAR_RIGHT
import com.maxmaster.materialmascot.engine.EAR_RIGHT_BASE
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
            // Behind the body, and sunk deep: the body silhouette is drawn on
            // top of the ear bases, so no pose, morph or squash can ever open
            // daylight at the join — the old over-the-body placement relied on
            // the base curve landing exactly on the outline, and lifted off
            // whenever the pose rotated.
            if (config.cat) {
                val twitch = if (config.motion) earTwitch(frameMs, config.energy) else 0f
                val ears: DrawScope.() -> Unit = {
                    drawCatEars(
                        points = frame.bodyPoints,
                        color = config.color,
                        chrome = chrome,
                        outline = outline,
                        sizePx = drawPx,
                        perk = earPerk,
                        splayDeg = earSplay,
                        twitchDeg = twitch,
                        twitchSide = twitchSide(frameMs),
                        path = earPath
                    )
                }
                if (outline) {
                    // Line art has no fill to hide the sunk base curve, so the
                    // ear would read as a shape lying loose on the head. Clip
                    // it to outside the body outline: what is left is exactly
                    // the ear above the head, ending on the outline.
                    buildBodyPath(frame.bodyPoints, bodyPath)
                    clipPath(bodyPath, ClipOp.Difference) { ears() }
                } else {
                    ears()
                }
            }
            when {
                chrome != null -> drawChromeBody(frame.bodyPoints, chrome, bodyPath)
                outline -> drawOutlineBody(frame.bodyPoints, config.color, drawPx, bodyPath)
                else -> drawBody(frame.bodyPoints, config.color, bodyPath)
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
            frame.dots.forEach { drawDot(it, config.color, inkColor, drawPx, bodyPath) }
            // Eyes belong ON the body, never across its edge: clipped to the
            // silhouette, a gaze that drifts to the rim gets cut by the form
            // instead of sliding off it — the read of a solid 3D shape rather
            // than a face pasted on a disc. Rebuilt here because drawDot
            // borrows bodyPath for tears.
            buildBodyPath(frame.bodyPoints, bodyPath)
            clipPath(bodyPath) {
                frame.eyes.forEach {
                    drawEye(
                        it, inkColor, eyeSrcPath, eyeDstPath, eyeMatrix,
                        glow = chrome?.eyesGlow == true
                    )
                }
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
private fun DrawScope.drawChromeBody(points: List<Offset>, chrome: ChromeBrushes, path: Path) {
    if (points.size < 3) return
    buildBodyPath(points, path)
    drawOval(brush = chrome.shadow, topLeft = chrome.shadowTopLeft, size = chrome.shadowSize)
    drawPath(path = path, brush = chrome.body)
    drawPath(path = path, brush = chrome.rim, style = Stroke(width = chrome.rimWidth))
    clipPath(path) {
        drawOval(brush = chrome.specular, topLeft = chrome.specularTopLeft, size = chrome.specularSize)
    }
}

/** [MascotFinish.OUTLINE]: the same silhouette as a stroke, with no fill. */
private fun DrawScope.drawOutlineBody(
    points: List<Offset>,
    color: Color,
    sizePx: Float,
    path: Path
) {
    if (points.size < 3) return
    buildBodyPath(points, path)
    drawPath(
        path = path,
        color = color,
        // Scaled off the bot's radius so the line keeps its weight at any size.
        style = Stroke(width = sizePx * BotEngine.RADIUS_FRACTION * 0.06f)
    )
}

private fun DrawScope.drawBody(points: List<Offset>, color: Color, path: Path) {
    if (points.size < 3) return
    buildBodyPath(points, path)
    drawPath(path = path, color = color)
}

/** Catmull-Rom style closed spline through the sampled silhouette points. */
private fun buildBodyPath(points: List<Offset>, path: Path) {
    val n = points.size
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

/**
 * Draws one traced shape through a base frame, as a closed Catmull-Rom spline.
 *
 * The frame is two vectors: [xx]/[xy] spans the base (local x = +-1 lands on
 * the two base corners) and [yx]/[yy] rises toward the tip. Points are mapped
 * as they are consumed, so a 40-point shape costs no allocation per frame.
 */
private fun tracedPath(
    shape: Array<Offset>,
    ox: Float, oy: Float,
    xx: Float, xy: Float,
    yx: Float, yy: Float,
    path: Path,
    base: IntRange,
    buryLen: Float
) {
    val n = shape.size
    fun at(i: Int): Offset {
        val k = ((i % n) + n) % n
        val p = shape[k]
        // The ear flares toward its base: the traced tip is what reads as cat,
        // the bottom is what reads as attached, so the lower it goes the wider
        // it gets.
        val flare = 1f + EAR_BASE_FLARE * (1f - (p.y / EAR_TIP_Y).coerceIn(0f, 1f))
        val px = p.x * flare
        val x = ox + xx * px + yx * p.y
        val y = oy + xy * px + yy * p.y
        // Base points are pinned inside the body, never merely placed near it:
        // the drawing origin IS the body centre here, so one clamp on the
        // radius holds at every pose rotation and morph.
        if (k in base) {
            val d = kotlin.math.hypot(x, y)
            if (d > buryLen) return Offset(x * buryLen / d, y * buryLen / d)
        }
        return Offset(x, y)
    }
    path.reset()
    val first = at(0)
    path.moveTo(first.x, first.y)
    for (i in 0 until n) {
        val a = at(i - 1)
        val b = at(i)
        val c = at(i + 1)
        val d = at(i + 2)
        path.cubicTo(
            b.x + (c.x - a.x) / 6f, b.y + (c.y - a.y) / 6f,
            c.x - (d.x - b.x) / 6f, c.y - (d.y - b.y) / 6f,
            c.x, c.y
        )
    }
    path.close()
}

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

/**
 * How deep the ear base is pinned inside the body, as a fraction of the ball
 * radius. Anything closer to the edge can be swung back into view by a splayed
 * pose; at this depth no flare, rotation or squash lifts it out. It sits under
 * the body paint (and is clipped away in line art), so it costs nothing.
 */
private const val EAR_BURY = 0.86f

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
 * Two ears at the crown, from the measured pair in [EAR_LEFT] / [EAR_RIGHT].
 *
 * The pair is placed as one piece, in the orientation it was traced at, so the
 * ears keep the lean and spacing they were drawn with. The frame is rebuilt
 * every frame from the crown of the live body outline — position and outward
 * normal both — so the ears ride whatever squash, stretch or morph the current
 * state is in. Because the caller paints the body OVER the ear bases — or, in
 * line art, clips this drawing to outside the body outline — the join is
 * always hidden: attachment no longer depends on the base curve landing
 * exactly on the outline.
 *
 * One shape per ear, in the body's own paint: a silhouette accent, not a
 * second character with its own colours.
 */
private fun DrawScope.drawCatEars(
    points: List<Offset>,
    color: Color,
    chrome: ChromeBrushes?,
    outline: Boolean,
    sizePx: Float,
    perk: Float,
    splayDeg: Float,
    twitchDeg: Float,
    twitchSide: Int,
    path: Path
) {
    val n = points.size
    if (n < 8) return
    val ballR = sizePx * BotEngine.RADIUS_FRACTION
    val half = ballR * EAR_WIDTH
    val sink = ballR * EAR_SINK

    val crown = points[n * 3 / 4]
    val crownLen = kotlin.math.hypot(crown.x, crown.y)
    if (crownLen < 1e-3f) return
    // Outward at the crown, and its perpendicular: the pair's up and right.
    val ux = crown.x / crownLen
    val uy = crown.y / crownLen
    val e1x = -uy * half
    val e1y = ux * half
    val e2x = ux * half
    val e2y = uy * half

    for (side in intArrayOf(-1, 1)) {
        val shape = if (side < 0) EAR_LEFT else EAR_RIGHT
        val base = if (side < 0) EAR_LEFT_BASE else EAR_RIGHT_BASE
        val pivot = if (side < 0) EAR_LEFT_PIVOT else EAR_RIGHT_PIVOT
        val pose = (splayDeg + (if (side == twitchSide) twitchDeg else 0f))
            .coerceIn(-EAR_SPLAY_LIMIT, EAR_SPLAY_LIMIT)
        val deg = -side * pose
        val rad = deg * PI.toFloat() / 180f
        val c = cos(rad)
        val sn = sin(rad)

        val ox = crown.x - ux * sink + e1x * pivot * (1f - c) - e2x * pivot * sn
        val oy = crown.y - uy * sink + e1y * pivot * (1f - c) - e2y * pivot * sn
        val xx = e1x * c + e2x * sn
        val xy = e1y * c + e2y * sn
        val yx = (-e1x * sn + e2x * c) * perk * EAR_TIP_DAMP
        val yy = (-e1y * sn + e2y * c) * perk * EAR_TIP_DAMP

        tracedPath(shape, ox, oy, xx, xy, yx, yy, path, base, ballR * EAR_BURY)
        when {
            outline -> drawPath(
                path = path,
                color = color,
                style = Stroke(width = ballR * 0.06f, cap = StrokeCap.Round)
            )
            chrome != null -> drawPath(path, brush = chrome.body)
            else -> drawPath(path, color = color)
        }
    }
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