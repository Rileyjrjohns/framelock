// Models is downstream of live (ADR-014): ModelSwapPolicy reads the live PipelineState.
plugins {
    id("framelock.jvm.pure")
}

dependencies {
    api(projects.features.live.domain)
}
