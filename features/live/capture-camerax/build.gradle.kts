plugins {
    id("framelock.android.adapter")
}

dependencies {
    api(projects.features.live.domain)
    implementation(libs.bundles.camerax)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(platform(libs.kotlinx.coroutines.bom))
    implementation(libs.kotlinx.coroutines.android)
}
