/*
 * Material Mascot — a living bot avatar for Android apps.
 * Copyright (C) 2026 Max Master
 * SPDX-License-Identifier: MIT
 */

package com.maxmaster.materialmascot.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.image.BufferedImage

internal actual fun createPixelImage(size: Int): PixelImage = DesktopPixelImage(size)

private class DesktopPixelImage(private val size: Int) : PixelImage {
    private val buffer = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)

    override fun update(pixels: IntArray): ImageBitmap {
        buffer.setRGB(0, 0, size, size, pixels, 0, size)
        // ponytail: this copies into a fresh Skia image every frame. Fine at
        // mascot sizes; swap for a reused org.jetbrains.skia.Bitmap if a
        // desktop screen ever shows dozens of large bots.
        return buffer.toComposeImageBitmap()
    }
}
