plugins {
    id("framelock.android.adapter")
}

dependencies {
    api(projects.features.live.domain)
    implementation(libs.androidx.core)
    implementation(libs.androidx.tracing)
}
