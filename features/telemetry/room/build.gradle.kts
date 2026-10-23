plugins {
    id("framelock.android.adapter")
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// The schema is exported and committed: a change to the entities shows up in review (ADR-004, M6).
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(projects.features.telemetry.domain)
    implementation(platform(libs.kotlinx.coroutines.bom))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.work.runtime)

    testImplementation(projects.shared.testing)

    androidTestImplementation(platform(libs.kotlinx.coroutines.bom))
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.work.testing)
}
