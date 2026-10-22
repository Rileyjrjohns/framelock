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

include(":core:domain")
include(":core:application")
include(":core:testing")
include(":adapters:capture-uvc")
include(":adapters:capture-camerax")
include(":adapters:inference-litert")
include(":adapters:model-store")
include(":adapters:system")
include(":adapters:telemetry")
include(":app")
include(":architecture-tests")
