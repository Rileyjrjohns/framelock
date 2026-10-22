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

spotless {
    kotlin {
        target("**/src/**/*.kt")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get()).customRuleSets(
            listOf(
                libs.compose.rules.ktlint
                    .get()
                    .toString(),
            ),
        )
    }
    kotlinGradle {
        target("*.gradle.kts", "**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint(libs.versions.ktlint.get())
    }
}

tasks.check {
    dependsOn(gradle.includedBuild("build-logic").task(":boundaries:check"))
    dependsOn(gradle.includedBuild("build-logic").task(":convention:check"))
}
