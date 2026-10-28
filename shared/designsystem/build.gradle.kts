plugins { id("framelock.android.ui") }

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.haze)
    implementation(libs.haze.blur)
}
