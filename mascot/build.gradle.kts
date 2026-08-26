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
                "A living bot avatar for Compose Multiplatform — a faithful " +
                    "Kotlin/Canvas port of bloub, the MIT-licensed SVG recreation of " +
                    "the x.ai Grok avatar. Everything is drawn at runtime — no image assets."
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
