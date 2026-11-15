/*
 * Material Mascot — a living bot avatar for Android apps.
 * Copyright (C) 2026 Max Master
 * SPDX-License-Identifier: MIT
 */

package com.maxmaster.materialmascot.engine

internal actual fun loadMascotLibrary() {
    // Packaged per ABI under jniLibs by the cargoBuildAndroid Gradle task.
    System.loadLibrary("mascot_jni")
}
