/*
 * Material Mascot — a living bot avatar for Android apps.
 * Copyright (C) 2026 Max Master
 * SPDX-License-Identifier: MIT
 */

package com.maxmaster.materialmascot.ui

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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import com.maxmaster.materialmascot.config.MascotConfig
import com.maxmaster.materialmascot.config.MascotPalette
import com.maxmaster.materialmascot.engine.BotEngine
import com.maxmaster.materialmascot.engine.BotState
import com.maxmaster.materialmascot.engine.RenderStyle
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Renders the bot mascot.
 *
 * This is the main entry point for displaying the mascot in your UI.
 * Create a [MascotConfig] with your desired settings and pass it here.
 *
 * The bot animates on its own from the frame clock — no manual frame
 * advancement needed. State changes are smooth morph transitions. The pixels
 * come from the Rust core; this composable keeps the state, asks for a frame
 * and draws it.
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
    val renderPx = renderSize(sizePx, config)

    // Kept as the state OBJECT and never read with `by` at composable scope: a
    // read during composition would subscribe this composable to a value that
    // changes every frame, recomposing every bot on screen at display refresh
    // rate. The draw lambda and the effects below read `.longValue` instead,
    // which invalidates only the canvas' draw.
    //
    // Seeded from nanoTime, not currentTimeMillis: withFrameNanos reports the
    // nanoTime-based frame clock, so an epoch seed would make the first tick
    // jump the bot's clock backwards.
    val frameMs = remember { mutableLongStateOf(System.nanoTime() / 1_000_000L) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { frameMs.longValue = it / 1_000_000L }
        }
    }

    val engine = remember { BotEngine(config.initialState) }

    // Poke reaction: a tap makes the bot light up for a moment, whatever state
    // it is in. Pure delight, no configuration needed.
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
            // Read inside the effect, not in composition: same current value,
            // without subscribing the composable to the per-frame ticker.
            engine.setState(shownState, frameMs.longValue)
        }
    }

    // Rebuilt only when something that affects the paint changes; the palette
    // crosses into native code as an array built once here, not per frame.
    val style = remember(
        config.finish, config.eyeStyle, config.palette, config.color, config.eyeColor,
        config.pixelGrid, config.pixelShade, config.cat, config.motion
    ) {
        RenderStyle(
            finish = config.finish,
            eyeStyle = config.eyeStyle,
            palette = config.palette ?: MascotPalette.fromBody(config.color, config.eyeColor),
            pixelGrid = config.pixelGrid,
            pixelShade = config.pixelShade,
            cat = config.cat,
            life = config.motion
        )
    }
    val surface = remember(renderPx) { FrameSurface(renderPx) }
    val frameStepMs = if (config.maxFps > 0) (1000L / config.maxFps).coerceAtLeast(1L) else 1L
    // Grid finishes are scaled with hard edges so the cells stay crisp.
    val filter = if (config.finish.isGrid) FilterQuality.None else FilterQuality.Low
    val fallbackColor = style.palette.base

    val canvasModifier = if (contentDescription != null) {
        modifier
            .size(config.size)
            .semantics { this.contentDescription = contentDescription }
    } else {
        modifier.size(config.size)
    }

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
        // Rendered in the draw phase: the ticker state is read here, so a new
        // frame invalidates only this canvas' draw — not recomposition.
        val tick = frameMs.longValue
        val now = tick - tick % frameStepMs
        val image = surface.frame(engine, now, style)
        if (image != null) {
            drawImage(
                image = image,
                dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                filterQuality = filter
            )
        } else {
            // The native core failed to load. A plain ball keeps the host's
            // layout honest; BotEngine.nativeError says what went wrong.
            drawCircle(color = fallbackColor, radius = size.minDimension / 2f * BotEngine.RADIUS_FRACTION)
        }
    }
}

/**
 * Side of the frame to render, in pixels.
 *
 * Smooth finishes render at the display size, capped by
 * [MascotConfig.maxRenderPx]. Grid finishes render a whole number of pixels
 * per cell so every cell comes out the same size, then scale to fit.
 */
private fun renderSize(sizePx: Float, config: MascotConfig): Int {
    val cap = config.maxRenderPx.coerceAtLeast(16)
    val wanted = sizePx.roundToInt().coerceIn(16, cap)
    if (!config.finish.isGrid) return wanted
    val grid = config.pixelGrid.coerceIn(8, 256)
    return grid * (wanted / grid).coerceAtLeast(1)
}

/**
 * The pixel buffer and bitmap one bot draws through, reused across frames so
 * nothing is allocated at display rate. Skips the render entirely when asked
 * for the frame it already holds.
 */
private class FrameSurface(private val px: Int) {
    private val pixels = IntArray(px * px)
    private val bitmap = createPixelImage(px)
    private var held: ImageBitmap? = null
    private var heldAt = Long.MIN_VALUE
    private var heldStyle: RenderStyle? = null
    private var heldGeneration = -1

    fun frame(engine: BotEngine, nowMs: Long, style: RenderStyle): ImageBitmap? {
        val cached = held
        if (cached != null && nowMs == heldAt && style == heldStyle && engine.generation == heldGeneration) {
            return cached
        }
        if (!engine.render(nowMs, px, pixels, style)) return null
        val image = bitmap.update(pixels)
        held = image
        heldAt = nowMs
        heldStyle = style
        heldGeneration = engine.generation
        return image
    }
}
