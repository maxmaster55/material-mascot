/*
 * Material Mascot — a living bot avatar for Android apps.
 * Copyright (C) 2026 Max Master
 * SPDX-License-Identifier: MIT
 */

package com.maxmaster.materialmascot.engine

import androidx.compose.ui.geometry.Offset

/*
 * Measured ear geometry, produced by tools/trace_ear.py --pair.
 *
 * Same approach as the body silhouettes: measure the shape once and ship the
 * numbers, rather than hand-tuning bezier control points until something looks
 * close. Both ears are traced in ONE frame, in the reference's own axes, so
 * they arrive at exactly the angle and spacing they were drawn at — deriving
 * each ear's rotation from a detected base edge is what previously turned them
 * into horns.
 *
 * MaterialBot anchors this frame at the crown of the live body outline, so the
 * pair rides every squash, stretch and morph.
 */
// Pair frame: x spans -1..+1 across both ears, y = 0 at their base,
// y up. Tips reach 1.067.

/** Left ear exactly as drawn, in the shared pair frame. */
internal val EAR_LEFT: Array<Offset> = arrayOf(
    Offset(-0.8376f, +1.0673f),
    Offset(-0.8838f, +1.0118f),
    Offset(-0.9118f, +0.9460f),
    Offset(-0.9309f, +0.8766f),
    Offset(-0.9490f, +0.8067f),
    Offset(-0.9648f, +0.7359f),
    Offset(-0.9768f, +0.6635f),
    Offset(-0.9861f, +0.5900f),
    Offset(-0.9944f, +0.5161f),
    Offset(-1.0000f, +0.4410f),
    Offset(-1.0000f, +0.3636f),
    Offset(-1.0000f, +0.2863f),
    Offset(-0.9954f, +0.2108f),
    Offset(-0.9847f, +0.1379f),
    Offset(-0.9675f, +0.0677f),
    Offset(-0.9344f, +0.0040f),
    Offset(-0.8947f, +0.0542f),
    Offset(-0.8532f, +0.1144f),
    Offset(-0.8050f, +0.1718f),
    Offset(-0.7503f, +0.2265f),
    Offset(-0.6929f, +0.2746f),
    Offset(-0.6341f, +0.3195f),
    Offset(-0.5743f, +0.3619f),
    Offset(-0.5138f, +0.4026f),
    Offset(-0.4523f, +0.4409f),
    Offset(-0.3908f, +0.4792f),
    Offset(-0.3280f, +0.5142f),
    Offset(-0.2625f, +0.5429f),
    Offset(-0.1967f, +0.5708f),
    Offset(-0.1810f, +0.6242f),
    Offset(-0.2259f, +0.6830f),
    Offset(-0.2773f, +0.7390f),
    Offset(-0.3334f, +0.7904f),
    Offset(-0.3908f, +0.8386f),
    Offset(-0.4509f, +0.8802f),
    Offset(-0.5111f, +0.9217f),
    Offset(-0.5739f, +0.9568f),
    Offset(-0.6363f, +0.9930f),
    Offset(-0.7002f, +1.0255f),
    Offset(-0.7652f, +1.0552f)
)


/** Right ear exactly as drawn, in the same frame. */
internal val EAR_RIGHT: Array<Offset> = arrayOf(
    Offset(+0.8005f, +1.0673f),
    Offset(+0.7335f, +1.0421f),
    Offset(+0.6687f, +1.0116f),
    Offset(+0.6063f, +0.9752f),
    Offset(+0.5434f, +0.9402f),
    Offset(+0.4819f, +0.9018f),
    Offset(+0.4217f, +0.8602f),
    Offset(+0.3628f, +0.8153f),
    Offset(+0.3054f, +0.7671f),
    Offset(+0.2506f, +0.7123f),
    Offset(+0.2024f, +0.6549f),
    Offset(+0.1694f, +0.5911f),
    Offset(+0.2261f, +0.5568f),
    Offset(+0.2920f, +0.5290f),
    Offset(+0.3560f, +0.4965f),
    Offset(+0.4186f, +0.4607f),
    Offset(+0.4802f, +0.4224f),
    Offset(+0.5417f, +0.3841f),
    Offset(+0.6019f, +0.3424f),
    Offset(+0.6607f, +0.2975f),
    Offset(+0.7182f, +0.2493f),
    Offset(+0.7757f, +0.2011f),
    Offset(+0.8271f, +0.1450f),
    Offset(+0.8720f, +0.0862f),
    Offset(+0.9072f, +0.0233f),
    Offset(+0.9490f, +0.0314f),
    Offset(+0.9734f, +0.0987f),
    Offset(+0.9861f, +0.1708f),
    Offset(+0.9954f, +0.2444f),
    Offset(+1.0000f, +0.3199f),
    Offset(+0.9954f, +0.3955f),
    Offset(+0.9954f, +0.4729f),
    Offset(+0.9901f, +0.5481f),
    Offset(+0.9814f, +0.6220f),
    Offset(+0.9722f, +0.6956f),
    Offset(+0.9582f, +0.7672f),
    Offset(+0.9418f, +0.8378f),
    Offset(+0.9211f, +0.9067f),
    Offset(+0.8979f, +0.9745f),
    Offset(+0.8654f, +1.0385f)
)


/** X of each ear's base centre: the point its pose rotates about. */
internal const val EAR_LEFT_PIVOT: Float = -0.9125f
internal const val EAR_RIGHT_PIVOT: Float = +0.9254f

/** How high the traced pair reaches above its base, in pair-frame units. */
internal const val EAR_TIP_Y: Float = 1.0673f
