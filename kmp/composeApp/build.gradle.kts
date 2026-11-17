import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

val withAndroid = (findProperty("tripshare.android") as String?) != "false"
if (withAndroid) {
    apply(plugin = "com.android.application")
    // Groovy, so this script still compiles where Android's plugin is absent.
    apply(from = "android.gradle")
}

val composeVersion = "1.12.2"
val firebaseVersion = "3.0.0-beta1"

kotlin {
    if (withAndroid) {
        androidTarget {
            compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("tripshare")
        browser {
            commonWebpackConfig { outputFileName = "tripshare.js" }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.compose.runtime:runtime:$composeVersion")
            implementation("org.jetbrains.compose.foundation:foundation:$composeVersion")
            implementation("org.jetbrains.compose.ui:ui:$composeVersion")
            implementation("org.jetbrains.compose.components:components-resources:$composeVersion")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("dev.gitlive:firebase-auth:$firebaseVersion")
            implementation("dev.gitlive:firebase-firestore:$firebaseVersion")
        }
        if (withAndroid) {
            getByName("androidMain").dependencies {
                implementation("androidx.activity:activity-compose:1.11.0")
            }
        }
    }
}

compose.resources {
    packageOfResClass = "tw.tripshare.res"
    publicResClass = false
}
