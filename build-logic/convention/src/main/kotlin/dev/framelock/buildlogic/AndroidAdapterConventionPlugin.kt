package dev.framelock.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * `:adapters:*`: Android libraries with AGP's built-in Kotlin (never `org.jetbrains.kotlin.android`).
 * Namespace: `dev.framelock.adapters.<name with '-' as '.'>`, e.g. `:adapters:capture-uvc` -> `...adapters.capture.uvc`.
 */
class AndroidAdapterConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) =
        with(target) {
            pluginManager.apply("com.android.library")

            extensions.configure<LibraryExtension> {
                namespace = "dev.framelock.adapters." + name.replace('-', '.')
                compileSdk = libs.int("compileSdk")
                defaultConfig {
                    minSdk = libs.int("minSdk")
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.toVersion(libs.int("jdk"))
                    targetCompatibility = JavaVersion.toVersion(libs.int("jdk"))
                }
                testOptions {
                    unitTests.all { it.useJUnitPlatform() }
                }
            }
            configureJvmTests()
            dependencies {
                "androidTestImplementation"(libs.findBundle("android-test").get())
            }
        }
}
