import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    kotlin("plugin.compose")
    id("org.jetbrains.compose")
}

val withAndroid = (findProperty("tripshare.android") as String?) != "false"
if (withAndroid) {
    apply(plugin = "com.android.kotlin.multiplatform.library")
    // Groovy, so this script still compiles where Android's plugin is absent.
    apply(from = "android.gradle")
}

val composeVersion = "1.12.2"
val firebaseVersion = "3.0.0-beta1"

// The version from version.properties, as a constant the app can show and
// put in error reports ("0.2.0+11").
val versionProps = java.util.Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }
val appVersion = "${versionProps["versionName"]}+${versionProps["versionCode"]}"
val generatedVersion = layout.buildDirectory.dir("generated/version")
val writeVersion by tasks.registering {
    inputs.property("version", appVersion)
    outputs.dir(generatedVersion)
    doLast {
        val f = generatedVersion.get().file("tw/tripshare/BuildInfo.kt").asFile
        f.parentFile.mkdirs()
        f.writeText("package tw.tripshare\n\nconst val APP_VERSION = \"$appVersion\"\n")
    }
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName.set("tripshare")
        browser {
            commonWebpackConfig { outputFileName = "tripshare.js" }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain { kotlin.srcDir(writeVersion) }
        commonMain.dependencies {
            implementation(project(":core"))
            implementation("org.jetbrains.compose.runtime:runtime:$composeVersion")
            implementation("org.jetbrains.compose.foundation:foundation:$composeVersion")
            implementation("org.jetbrains.compose.ui:ui:$composeVersion")
            implementation("org.jetbrains.compose.components:components-resources:$composeVersion")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
            implementation("dev.gitlive:firebase-auth:$firebaseVersion")
            implementation("dev.gitlive:firebase-firestore:$firebaseVersion")
        }
    }
}

compose.resources {
    packageOfResClass = "tw.tripshare.res"
    publicResClass = false
}
