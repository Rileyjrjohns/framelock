pluginManagement {
    includeBuild("build-logic")
    repositories {
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

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    // Applies verifyModuleBoundaries to every project (see docs/adr/ADR-006-module-boundaries.md).
    id("framelock.module-boundaries")
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "framelock"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":shared:kernel")
include(":shared:testing")
include(":features:live:domain")
include(":features:live:application")
include(":features:live:capture-uvc")
include(":features:live:capture-camerax")
include(":features:live:capture-file")
include(":features:live:inference-litert")
include(":features:live:system")
include(":features:live:ui")
include(":features:models:domain")
include(":features:models:application")
include(":features:models:store")
include(":features:models:ui")
include(":features:telemetry:domain")
include(":features:telemetry:application")
include(":features:telemetry:room")
include(":app")
include(":architecture-tests")
