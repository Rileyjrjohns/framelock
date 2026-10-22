plugins {
    id("framelock.jvm.core")
}

dependencies {
    api(projects.core.domain)
    testImplementation(projects.core.testing)
}
