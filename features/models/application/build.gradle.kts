plugins {
    id("framelock.jvm.pure")
}

dependencies {
    api(projects.features.models.domain)
    api(projects.features.live.application)
    testImplementation(projects.shared.testing)
}
