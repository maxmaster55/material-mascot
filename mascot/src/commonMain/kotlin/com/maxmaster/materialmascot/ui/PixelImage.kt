/*
 * Material Mascot — a living bot avatar for Android apps.
 * Copyright (C) 2026 Max Master
 * SPDX-License-Identifier: MIT
 */

package com.maxmaster.materialmascot.ui

import androidx.compose.ui.graphics.ImageBitmap

/**
 * A square bitmap that can be refilled from a pixel buffer every frame.
 *
 * Compose has no common way to turn an `IntArray` into an [ImageBitmap], so
 * each platform supplies its own.
 */
internal interface PixelImage {
    /**
     * Replaces the contents with [pixels] (straight-alpha 0xAARRGGBB, row by
     * row) and returns the bitmap to draw.
     */
    fun update(pixels: IntArray): ImageBitmap
}

/** A [PixelImage] of [size] x [size] pixels for this platform. */
internal expect fun createPixelImage(size: Int): PixelImage
