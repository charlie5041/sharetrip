import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Plain Kotlin shared by the Android app and the web app: data models and
// the money arithmetic. No Android or Compose dependencies, so its tests
// run anywhere with `./gradlew :core:jvmTest`.
plugins {
    kotlin("multiplatform")
}

kotlin {
    // Android uses this JVM build.
    jvm {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { browser() }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
