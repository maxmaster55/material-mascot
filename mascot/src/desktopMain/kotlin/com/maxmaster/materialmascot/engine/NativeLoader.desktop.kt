/*
 * Material Mascot — a living bot avatar for Android apps.
 * Copyright (C) 2026 Max Master
 * SPDX-License-Identifier: MIT
 */

package com.maxmaster.materialmascot.engine

import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * The desktop jar carries the library as a resource under
 * `native/<os>-<arch>/`. A JVM can only load a library from a real file, so it
 * is copied out to a temp file first. If the resource is missing (running from
 * an IDE without the Gradle copy step, say) `java.library.path` is tried.
 */
internal actual fun loadMascotLibrary() {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val (dir, file) = when {
        "win" in os -> "windows" to "mascot_jni.dll"
        "mac" in os || "darwin" in os -> "macos" to "libmascot_jni.dylib"
        else -> "linux" to "libmascot_jni.so"
    }
    val cpu = if (arch == "aarch64" || arch == "arm64") "aarch64" else "x86_64"
    val stream = NativeMascot::class.java.getResourceAsStream("/native/$dir-$cpu/$file")
    if (stream == null) {
        System.loadLibrary("mascot_jni")
        return
    }
    val temp = Files.createTempFile("mascot_jni", "." + file.substringAfterLast('.'))
    stream.use { Files.copy(it, temp, StandardCopyOption.REPLACE_EXISTING) }
    temp.toFile().deleteOnExit()
    System.load(temp.toAbsolutePath().toString())
}
