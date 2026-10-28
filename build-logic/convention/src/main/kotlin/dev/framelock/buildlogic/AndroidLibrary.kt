package dev.framelock.buildlogic

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * `:features:<feature>:<module>` -> `dev.framelock.<feature>.<module with '-' as '.'>`,
 * e.g. `:features:live:capture-uvc` -> `dev.framelock.live.capture.uvc` (ADR-014).
 */
internal fun Project.featureNamespace(): String {
    if (path == ":shared:designsystem") return "dev.framelock.designsystem"
    val segments = path.removePrefix(":").split(':')
    require(segments.size == 3 && segments[0] == "features") { "$path is not a feature module" }
    return "dev.framelock.${segments[1]}.${segments[2].replace('-', '.')}"
}

/** Android library with AGP's built-in Kotlin (never `org.jetbrains.kotlin.android`). */
internal fun Project.configureAndroidLibrary() {
    pluginManager.apply("com.android.library")
    extensions.configure<LibraryExtension> {
        namespace = featureNamespace()
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
