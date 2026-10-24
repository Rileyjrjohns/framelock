plugins {
    id("framelock.android.adapter")
}

dependencies {
    api(projects.features.live.domain)
    implementation(platform(libs.kotlinx.coroutines.bom))
    implementation(libs.kotlinx.coroutines.core)
}
