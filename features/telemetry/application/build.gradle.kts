plugins {
    id("framelock.jvm.pure")
}

dependencies {
    api(projects.features.telemetry.domain)
    testImplementation(projects.shared.testing)
}
