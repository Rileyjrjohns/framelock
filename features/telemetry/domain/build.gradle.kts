// Telemetry is downstream of every feature: its allowlist knows each feature's event family (ADR-014).
plugins {
    id("framelock.jvm.pure")
}

dependencies {
    api(projects.features.live.domain)
    api(projects.features.models.domain)
}
