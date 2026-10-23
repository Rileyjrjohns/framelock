plugins {
    id("framelock.android.ui")
}

dependencies {
    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(projects.features.models.application)
}
