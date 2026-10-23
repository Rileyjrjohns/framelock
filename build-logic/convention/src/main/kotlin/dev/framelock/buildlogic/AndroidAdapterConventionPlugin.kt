package dev.framelock.buildlogic

import org.gradle.api.Plugin
import org.gradle.api.Project

/** A feature's adapter: implements that feature's ports over an Android or third-party API (ADR-014). */
class AndroidAdapterConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = target.configureAndroidLibrary()
}
