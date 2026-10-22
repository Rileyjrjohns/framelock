plugins {
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    // Also puts KGP 2.4.21 on the root classpath, above the 2.2.10 floor bundled with AGP's built-in Kotlin.
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.spotless)
}

// A pruned file tree, not a glob over the root: walking build/ while parallel tasks write to it made Spotless
// fail with "Could not read path .../build/...". Excluded directories are not traversed.
fun sources(include: String) =
    fileTree(rootDir) {
        include(include)
        exclude("**/build/**", "**/bin/**", "**/.gradle/**", "**/.kotlin/**")
        // Fixtures break the architecture rules on purpose (star imports, qualified names): not linted.
        exclude("architecture-tests/src/test/fixtures/**")
    }

spotless {
    kotlin {
        target(sources("**/src/**/*.kt"))
        ktlint(libs.versions.ktlint.get()).customRuleSets(
            listOf(
                libs.compose.rules.ktlint
                    .get()
                    .toString(),
            ),
        )
    }
    kotlinGradle {
        target(sources("**/*.gradle.kts"))
        ktlint(libs.versions.ktlint.get())
    }
}

tasks.check {
    dependsOn(gradle.includedBuild("build-logic").task(":boundaries:check"))
    dependsOn(gradle.includedBuild("build-logic").task(":convention:check"))
}
