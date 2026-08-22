plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("maven-publish")
}

group = "com.maxmaster.materialmascot"
version = "1.0.0"

android {
    namespace = "com.maxmaster.materialmascot"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures { compose = true }
    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.compose.foundation:foundation:1.9.3")
    implementation("androidx.compose.ui:ui:1.9.3")
    implementation("androidx.compose.ui:ui-graphics:1.9.3")
    implementation("androidx.compose.ui:ui-tooling-preview:1.9.3")
    // ComposeViewAdapter is the runtime Android Studio uses to render @Preview.
    // Keep it out of the published release AAR while making debug previews work.
    debugImplementation("androidx.compose.ui:ui-tooling:1.9.3")
    implementation("androidx.compose.material3:material3:1.5.0-alpha06")
    // live package: shake sensor lifecycle + status bus + state collection
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    testImplementation("junit:junit:4.13.2")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            afterEvaluate { from(components["release"]) }

            pom {
                name.set("Material Mascot")
                description.set("A living bot avatar for Android/Jetpack Compose — a faithful Kotlin/Canvas port of bloub, the MIT-licensed SVG recreation of the x.ai Grok avatar. Everything is drawn at runtime — no image assets.")
                url.set("https://github.com/maxmaster/material-mascot")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }

                developers {
                    developer {
                        id.set("maxmaster")
                        name.set("Max Master")
                        email.set("maxmaster@example.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/maxmaster/material-mascot.git")
                    developerConnection.set("scm:git:ssh://github.com/maxmaster/material-mascot.git")
                    url.set("https://github.com/maxmaster/material-mascot")
                }
            }
        }
    }
}
