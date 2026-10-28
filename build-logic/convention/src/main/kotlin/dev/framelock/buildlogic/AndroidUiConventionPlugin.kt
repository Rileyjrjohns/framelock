package dev.framelock.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** A feature's Compose screens (ADR-014). */
class AndroidUiConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            configureAndroidLibrary()
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            extensions.configure<LibraryExtension> {
                buildFeatures {
                    compose = true
                }
            }
            dependencies {
                val composeBom = platform(libs.library("androidx-compose-bom"))
                "implementation"(composeBom)
                if (path == ":shared:designsystem") {
                    listOf(
                        "androidx-compose-ui",
                        "androidx-compose-ui-graphics",
                        "androidx-compose-foundation",
                        "androidx-compose-material3",
                        "androidx-compose-ui-tooling-preview",
                    ).forEach {
                        "api"(libs.library(it))
                    }
                } else {
                    "implementation"(libs.findBundle("compose").get())
                }
                "debugImplementation"(libs.findBundle("compose-debug").get())
                "androidTestImplementation"(composeBom)
                "androidTestImplementation"(libs.library("androidx-compose-ui-test-junit4"))
            }
        }
}
