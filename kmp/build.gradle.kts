buildscript {
    // Android's Gradle plugin is only put on the classpath when the Android
    // app is built, so the web app also builds without Google's repository.
    val withAndroid = (findProperty("tripshare.android") as String?) != "false"
    repositories {
        if (withAndroid) google()
        mavenCentral()
    }
    dependencies {
        if (withAndroid) classpath("com.android.tools.build:gradle:9.3.1")
    }
}

plugins {
    kotlin("multiplatform") version "2.4.21" apply false
    kotlin("plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.compose") version "1.12.2" apply false
}
