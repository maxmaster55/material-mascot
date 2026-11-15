plugins {
    id("com.android.library")
    // No version: both this repo's root and Skald's already put the Kotlin
    // Gradle Plugin jar on the classpath via kotlin.android (2.2.0) — a
    // second marker id for the SAME jar must not repeat the version, or
    // Gradle refuses it ("already on the classpath with an unknown version").
    id("org.jetbrains.kotlin.multiplatform")
    // A different jar entirely, and nothing else in either root touches it,
    // so this one DOES need its own explicit version.
    id("org.jetbrains.compose") version "1.9.3"
    id("org.jetbrains.kotlin.plugin.compose")
    id("maven-publish")
}

// Coordinates are single-sourced from gradle.properties when present (this
// repo's own build). Fallbacks keep the module compilable when consumed
// directly from another build (e.g. settings.gradle projectDir remap), where
// only the consuming build's gradle.properties is visible.
private val prop = { key: String, fallback: String ->
    providers.gradleProperty(key).orElse(fallback).get()
}

group = prop("library.group", "com.maxmaster.materialmascot")
version = prop("library.version", "1.0.0")

kotlin {
    // Not jvmToolchain(17): that demands an actual JDK 17 runtime be
    // locatable/downloadable, which this machine has neither. This targets
    // 17 bytecode from whatever JDK is running Gradle instead — the same
    // thing the pre-KMP build did via kotlinOptions.jvmTarget, just spelled
    // per-target under the multiplatform DSL.
    androidTarget {
        publishLibraryVariants("release")
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.material3)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            implementation("androidx.core:core-ktx:1.17.0")
            implementation("androidx.compose.ui:ui-tooling-preview:1.9.3")
            // live package: shake sensor lifecycle + status bus + state collection
            implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
            implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
        }
        val desktopTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation(kotlin("test-junit"))
            }
        }
    }
}

android {
    namespace = "com.maxmaster.materialmascot"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

// ComposeViewAdapter is the runtime Android Studio uses to render @Preview.
// Kept out of the published release AAR while making debug previews work —
// this is still a plain Android build-type configuration (AGP creates it
// regardless of the Kotlin Multiplatform wrapper), so it stays outside the
// kotlin{} source-set DSL rather than living in androidMain.
// ---------------------------------------------------------------- Rust core
//
// All drawing happens in the Rust workspace at the repository root
// (mascot-core, reached through the mascot-jni bridge). These tasks build it
// and put the library where each target looks for it.
//
// Needs `cargo` on the PATH. The Android build additionally needs the NDK and
// `cargo install cargo-ndk`, plus the Rust targets:
//   rustup target add aarch64-linux-android armv7-linux-androideabi x86_64-linux-android

val rustRoot = rootProject.layout.projectDirectory
val rustSources = files(
    rustRoot.file("Cargo.toml"),
    rustRoot.dir("mascot-core/src"), rustRoot.file("mascot-core/Cargo.toml"),
    rustRoot.dir("mascot-jni/src"), rustRoot.file("mascot-jni/Cargo.toml")
)

val hostOs: String = System.getProperty("os.name").lowercase().let {
    when {
        "win" in it -> "windows"
        "mac" in it || "darwin" in it -> "macos"
        else -> "linux"
    }
}
val hostArch: String = System.getProperty("os.arch").lowercase().let {
    if (it == "aarch64" || it == "arm64") "aarch64" else "x86_64"
}

val cargoBuildDesktop by tasks.registering(Exec::class) {
    group = "rust"
    description = "Builds the Rust core for this machine."
    workingDir = rustRoot.asFile
    commandLine("cargo", "build", "--release", "-p", "mascot-jni")
    inputs.files(rustSources)
    outputs.dir(rustRoot.dir("target/release"))
}

val copyDesktopNative by tasks.registering(Sync::class) {
    group = "rust"
    description = "Packages the host Rust library as a desktop resource."
    dependsOn(cargoBuildDesktop)
    from(rustRoot.dir("target/release")) {
        include("libmascot_jni.so", "libmascot_jni.dylib", "mascot_jni.dll")
        into("native/$hostOs-$hostArch")
    }
    into(layout.buildDirectory.dir("rust/desktop"))
}

// Registering the task itself as a resource directory makes everything that
// reads desktop resources (the jar, the tests, the sources jar) depend on it.
// The desktop loader extracts the library from the jar at startup.
kotlin.sourceSets.named("desktopMain") {
    resources.srcDir(copyDesktopNative)
}

val androidJniLibs = layout.buildDirectory.dir("rust/jniLibs")

val cargoBuildAndroid by tasks.registering(Exec::class) {
    group = "rust"
    description = "Builds the Rust core for every Android ABI (needs the NDK and cargo-ndk)."
    workingDir = rustRoot.asFile
    commandLine(
        "cargo", "ndk",
        "-t", "arm64-v8a", "-t", "armeabi-v7a", "-t", "x86_64",
        "-o", androidJniLibs.get().asFile.absolutePath,
        "build", "--release", "-p", "mascot-jni"
    )
    inputs.files(rustSources)
    outputs.dir(androidJniLibs)
}

android.sourceSets.getByName("main").jniLibs.srcDir(androidJniLibs)

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }.configureEach {
    dependsOn(cargoBuildAndroid)
}

dependencies {
    add("debugImplementation", "androidx.compose.ui:ui-tooling:1.9.3")
}

publishing {
    // One publication per Kotlin target (androidRelease, desktop, plus the
    // root kotlinMultiplatform artifact) is created automatically by the
    // Kotlin Multiplatform plugin once maven-publish is applied — unlike the
    // single-Android-variant setup this replaces, there is no one
    // components["release"] to register by hand. The POM metadata is common
    // to all of them, so it is applied to whichever publications exist
    // instead of assuming a fixed set.
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("Material Mascot")
            description.set(
                "A living bot avatar for Compose Multiplatform, drawn at runtime by a " +
                    "dependency-free Rust core — no image assets. Began as a port of bloub, " +
                    "the MIT-licensed SVG recreation of the x.ai Grok avatar."
            )
            url.set(prop("publish.url", "https://github.com/maxmaster/material-mascot"))

            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }

            developers {
                developer {
                    id.set(prop("publish.developer.id", "maxmaster"))
                    name.set(prop("publish.developer.name", "Max Master"))
                    email.set(prop("publish.developer.email", "yousseflkashef000@gmail.com"))
                }
            }

            scm {
                connection.set("scm:git:git://github.com/maxmaster/material-mascot.git")
                developerConnection.set("scm:git:ssh://github.com/maxmaster/material-mascot.git")
                url.set(prop("publish.url", "https://github.com/maxmaster/material-mascot"))
            }
        }
    }
}
