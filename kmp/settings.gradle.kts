rootProject.name = "tripshare"

pluginManagement {
    repositories {
        // Only Android's own artifacts come from Google; everything else is
        // on Maven Central. The filter also lets the web build run where
        // Google's repository can't be reached.
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

include(":core")
include(":composeApp")
// The Android app shell; skipped where the Android SDK isn't available.
if (providers.gradleProperty("tripshare.android").orNull != "false") include(":androidApp")
