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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.maxmaster.materialmascot.config.MascotConfig
import com.maxmaster.materialmascot.engine.BotEngine
import com.maxmaster.materialmascot.engine.BotState
import com.maxmaster.materialmascot.engine.DotSpec
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
 * @param config Host-owned immutable configuration.
 * @param modifier Layout modifier.
 * @param contentDescription Accessibility label; null omits the semantics node.
 */
@Composable
fun MaterialBot(
    config: MascotConfig,
    modifier: Modifier = Modifier,
    contentDescription: String? = null
) {
    if (!config.enabled) return

    val sizePx = with(LocalDensity.current) { config.size.toPx() }

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

    val frame = engine.sample(
        nowMs = frameMs,
        scalePx = sizePx,
        reducedMotion = !config.motion,
        energy = config.energy
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
    val bodyPath = remember { Path() }
    val eyeSrcPath = remember { Path() }
    val eyeDstPath = remember { Path() }
    val eyeMatrix = remember { android.graphics.Matrix() }

    Canvas(
        modifier = canvasModifier.pointerInput(Unit) {
            detectTapGestures { pokes++ }
        }
    ) {
        translate(this.center.x, this.center.y) {
            drawBody(frame.bodyPoints, config.color, bodyPath)
            frame.dots.forEach { drawDot(it, config.color, config.eyeColor, sizePx, bodyPath) }
            frame.eyes.forEach { drawEye(it, config.eyeColor, eyeSrcPath, eyeDstPath, eyeMatrix) }
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

private fun DrawScope.drawBody(points: List<Offset>, color: Color, path: Path) {
    if (points.size < 3) return
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
    drawPath(path = path, color = color)
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

private fun DrawScope.drawEye(
    eye: RenderedEye,
    color: Color,
    src: Path,
    dst: Path,
    matrix: android.graphics.Matrix
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
    drawPath(path = dst, color = color, alpha = eye.alpha)
}

// --- Previews ---

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
