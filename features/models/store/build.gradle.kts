plugins {
    id("framelock.android.adapter")
}

dependencies {
    api(projects.features.models.domain)
    implementation(platform(libs.kotlinx.coroutines.bom))
    implementation(libs.kotlinx.coroutines.android)
}
