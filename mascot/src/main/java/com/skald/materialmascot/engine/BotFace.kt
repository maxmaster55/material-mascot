package com.maxmaster.materialmascot.engine

/**
 * The eyes are painted on a sphere, not laid flat.
 *
 * Measured off the reference video: the eye nearest the edge is 0.69x the
 * width of the other and its area 0.663x — exactly the depth factor
 * (z = 0.669) of a point on a sphere at that distance from center. We model a
 * real head orientation: each eye gets the tangent frame of the sphere,
 * projected orthographically. Compression and tilt fall out of it.
 *
 * Constants below are fitted on frame-by-frame measurements (residual ~1 px
 * for a radius of 190 px), not hand-picked.
 */
object Face {
    /** Half-separation of the eyes on the sphere, degrees (~31 deg total). */
    const val EYE_SPLIT = 15.46f

    /** Resting eye size, in ball-radius units. */
    const val EYE_W = 0.186f
    const val EYE_H = 0.412f

    /** Resting head orientation, fitted on reference frames. */
    val REST_GAZE = HeadGaze(yaw = 28.49f, pitch = 28.62f, roll = -13f)
}

data class HeadGaze(
    /** yaw, degrees, positive = looks right */
    val yaw: Float,
    /** pitch, degrees, positive = looks up */
    val pitch: Float,
    /** roll, degrees, head tilt */
    val roll: Float
)

/** Local eye config in ball-radius units; [open] 1 = open, 0 = closed. */
data class EyeCfg(
    val w: Float,
    val h: Float,
    val open: Float = 1f,
    /**
     * Own tilt of the capsule, degrees, positive = top leans right (screen
     * coords). Applied AFTER the tangent frame of the sphere.
     */
    val tilt: Float = 0f
)

/** One eye's placement: center + tangent 2x2 matrix columns + depth. */
data class EyePose(
    val x: Float,
    val y: Float,
    val a: Float,
    val b: Float,
    val c: Float,
    val d: Float,
    /** z component of the normal: > 0 = facing visible */
    val depth: Float
)

private fun deg(d: Float): Float = d * (Math.PI.toFloat() / 180f)

/** Rotates two vectors of an orthonormal frame within their common plane. */
private fun spin(u: FloatArray, v: FloatArray, angle: Float): Pair<FloatArray, FloatArray> {
    val c = kotlin.math.cos(angle)
    val s = kotlin.math.sin(angle)
    return floatArrayOf(
        u[0] * c + v[0] * s,
        u[1] * c + v[1] * s,
        u[2] * c + v[2] * s
    ) to floatArrayOf(
        v[0] * c - u[0] * s,
        v[1] * c - u[1] * s,
        v[2] * c - u[2] * s
    )
}

/**
 * Head then eye frames.
 * Screen frame: x right, y down, z toward the viewer.
 * Index 0 is the inner eye, index 1 the outer one.
 */
fun eyePoses(gaze: HeadGaze, scale: Float, split: Float = Face.EYE_SPLIT): Pair<EyePose, EyePose> {
    var f = floatArrayOf(0f, 0f, 1f)
    var right = floatArrayOf(1f, 0f, 0f)
    var down = floatArrayOf(0f, 1f, 0f)

    // yaw: forward tips toward right
    val fr = spin(f, right, deg(gaze.yaw))
    f = fr.first; right = fr.second
    // pitch: forward tips up (against down)
    val df = spin(down, f, deg(gaze.pitch))
    down = df.first; f = df.second
    // roll: the head tilts within its own plane
    val rd = spin(right, down, deg(gaze.roll))
    right = rd.first; down = rd.second

    fun build(side: Int): EyePose {
        val ef = spin(f, right, deg(split * side))
        val efr = ef.first
        val er = ef.second
        return EyePose(
            x = efr[0] * scale,
            y = efr[1] * scale,
            a = er[0],
            b = er[1],
            c = down[0],
            d = down[1],
            depth = efr[2]
        )
    }

    return build(-1) to build(1)
}

/**
 * Life at rest: slow gaze drift, saccades, blinks.
 *
 * Pure function of time (no internal state), so pause, resume and jumping to
 * an arbitrary date always give the same image. Values are DELTAS added to the
 * current state's pose.
 */
data class Liveliness(
    val dYaw: Float,
    val dPitch: Float,
    val dRoll: Float,
    /** 1 = eye open, 0 = closed (vertical screen-space squash) */
    val lid: Float,
    val driftX: Float,
    val driftY: Float,
    val breath: Float
)

/** Pre-drawn blink calendar: deterministic and stateless. */
val BLINKS: FloatArray = run {
    val rng = Mulberry32(0x5eed)
    val out = ArrayList<Float>()
    var t = 1.4f
    while (t < 900f) {
        out.add(t)
        // 1.9 to 4.6 s between blinks, sometimes a double blink
        t += 1.9f + rng() * 2.7f
        if (rng() < 0.18f) {
            out.add(t)
            t += 0.24f
        }
    }
    out.toFloatArray()
}

/** Measured: 1 to 2 frames at 10 fps. */
const val BLINK_DUR = 0.18f

fun blinkLid(t: Float): Float {
    for (start in BLINKS) {
        if (t < start) break
        val k = (t - start) / BLINK_DUR
        if (k >= 0f && k <= 1f) {
            // fast close, slightly slower reopen
            return if (k < 0.45f) 1f - k / 0.45f else (k - 0.45f) / 0.55f
        }
    }
    return 1f
}

fun liveliness(
    t: Float,
    wander: Float = 1f,
    blink: Boolean = true,
    float: Boolean = true,
    energy: Float = 1f
): Liveliness {
    // Energy > 1 shortens every period and widens every amplitude, turning the
    // measured calm of the reference video into a cheerful, active character.
    val e = if (energy.isFinite()) energy.coerceIn(0.5f, 2.5f) else 1f
    return Liveliness(
        dYaw = (loopNoise(t, 11.3f / e, 0.4f) * 5.5f + loopNoise(t, 3.7f / e, 2.1f) * 1.6f) * wander * e,
        dPitch = (loopNoise(t, 9.1f / e, 1.3f) * 4.2f + loopNoise(t, 4.3f / e, 0.7f) * 1.3f) * wander * e,
        dRoll = loopNoise(t, 13.7f / e, 3.2f) * 2.2f * wander * e,
        lid = if (blink) blinkLid(t * e) else 1f,
        // At rest the video is nearly motionless (center stable +-0.003):
        // all the life goes through gaze and blinking.
        driftX = if (float) loopNoise(t, 7.9f / e, 1.9f) * (0.006f * e) else 0f,
        driftY = if (float) loopNoise(t, 5.3f / e, 0.3f) * (0.007f * e) else 0f,
        // Width stays constant, only height breathes.
        breath = if (float) 1f + kotlin.math.sin((t / (3.4f / e)) * TAU) * (0.005f * e) else 1f
    )
}

/**
 * The blink is a VERTICAL squash in screen space around the eye center
 * (measured: bbox width preserved, height falls to ~0.35), not a shrink along
 * the capsule axis. Applied after the tangent matrix, only affecting y outs.
 */
fun blinkScale(lid: Float): Float = 0.06f + 0.94f * clamp(lid)
