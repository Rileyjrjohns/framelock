plugins {
    id("framelock.android.application")
}

android {
    namespace = "dev.framelock.app"
    defaultConfig {
        applicationId = "dev.framelock"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    // Composition root (AppGraph, manual DI): the only module that sees every other one.
    implementation(projects.core.application)
    implementation(projects.adapters.captureUvc)
    implementation(projects.adapters.captureCamerax)
    implementation(projects.adapters.inferenceLitert)
    implementation(projects.adapters.modelStore)
    implementation(projects.adapters.system)
    implementation(projects.adapters.telemetry)

    implementation(libs.androidx.core)
    implementation(libs.bundles.compose)
    debugImplementation(libs.bundles.compose.debug)
    testImplementation(projects.core.testing)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
