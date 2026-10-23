plugins {
    id("framelock.jvm.pure")
}

dependencies {
    api(projects.features.live.domain)
    testImplementation(projects.shared.testing)
}
