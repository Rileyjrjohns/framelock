// Settings plugin, kept apart from :convention: settings plugins are loaded in the settings classloader,
// where AGP is not visible. This project must therefore never reference AGP or other build plugins.
plugins {
    `kotlin-dsl`
}

group = "dev.framelock.buildlogic"

java {
    toolchain.languageVersion.set(
        JavaLanguageVersion.of(
            libs.versions.jdk
                .get()
                .toInt(),
        ),
    )
}

val functionalTest: SourceSet = sourceSets.create("functionalTest")

dependencies {
    "functionalTestImplementation"(platform(libs.junit.bom))
    "functionalTestImplementation"(libs.junit.jupiter)
    "functionalTestImplementation"(gradleTestKit())
    "functionalTestRuntimeOnly"(libs.junit.platform.launcher)
}

gradlePlugin {
    testSourceSets(functionalTest)
    plugins {
        register("moduleBoundaries") {
            id = "framelock.module-boundaries"
            implementationClass = "dev.framelock.buildlogic.ModuleBoundariesSettingsPlugin"
        }
    }
}

val functionalTestTask =
    tasks.register<Test>("functionalTest") {
        description = "Runs the TestKit tests of the module boundary plugin."
        group = "verification"
        testClassesDirs = functionalTest.output.classesDirs
        classpath = functionalTest.runtimeClasspath
        useJUnitPlatform()
    }

tasks.check { dependsOn(functionalTestTask) }
