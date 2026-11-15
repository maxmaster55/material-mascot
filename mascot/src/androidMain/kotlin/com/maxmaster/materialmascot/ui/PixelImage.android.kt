/*
 * Material Mascot — a living bot avatar for Android apps.
 * Copyright (C) 2026 Max Master
 * SPDX-License-Identifier: MIT
 */

package com.maxmaster.materialmascot.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

internal actual fun createPixelImage(size: Int): PixelImage = AndroidPixelImage(size)

private class AndroidPixelImage(private val size: Int) : PixelImage {
    // One bitmap for the life of the bot: setPixels refills it in place and
    // bumps its generation id, which is what tells the renderer to re-upload.
    private val bitmap: Bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    private val image: ImageBitmap = bitmap.asImageBitmap()

    override fun update(pixels: IntArray): ImageBitmap {
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return image
    }
}
