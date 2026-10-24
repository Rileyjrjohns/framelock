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

    // Release signing comes from the environment (CI secrets or a local shell), never from the repository. Without it,
    // assembleRelease produces an unsigned APK.
    val keystore = providers.environmentVariable("FRAMELOCK_KEYSTORE").orNull
    if (keystore != null) {
        signingConfigs {
            create("release") {
                storeFile = file(keystore)
                storePassword = providers.environmentVariable("FRAMELOCK_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("FRAMELOCK_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("FRAMELOCK_KEY_PASSWORD").get()
            }
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
    }
}

dependencies {
    // Composition root (AppGraph, manual DI): the only module that sees every other one.
    implementation(projects.features.live.application)
    implementation(projects.features.live.captureUvc)
    implementation(projects.features.live.captureCamerax)
    implementation(projects.features.live.captureFile)
    implementation(projects.features.live.inferenceLitert)
    implementation(projects.features.live.system)
    implementation(projects.features.live.ui)
    implementation(projects.features.models.application)
    implementation(projects.features.models.store)
    implementation(projects.features.models.ui)
    implementation(projects.features.telemetry.application)
    implementation(projects.features.telemetry.room)

    implementation(libs.androidx.core)
    implementation(libs.androidx.work.runtime)
    implementation(platform(libs.kotlinx.coroutines.bom))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.bundles.compose)
    debugImplementation(libs.bundles.compose.debug)
    testImplementation(projects.shared.testing)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
