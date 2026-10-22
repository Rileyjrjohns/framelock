package dev.framelock.buildlogic

import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/** `:app`: Android application with Compose and AGP's built-in Kotlin. */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            extensions.configure<ApplicationExtension> {
                compileSdk = libs.int("compileSdk")
                defaultConfig {
                    minSdk = libs.int("minSdk")
                    targetSdk = libs.int("targetSdk")
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.toVersion(libs.int("jdk"))
                    targetCompatibility = JavaVersion.toVersion(libs.int("jdk"))
                }
                buildFeatures {
                    compose = true
                }
                testOptions {
                    unitTests.all { it.useJUnitPlatform() }
                }
            }
            configureJvmTests()
            dependencies {
                val composeBom = platform(libs.library("androidx-compose-bom"))
                "implementation"(composeBom)
                "androidTestImplementation"(composeBom)
                "androidTestImplementation"(libs.findBundle("android-test").get())
            }
        }
}
