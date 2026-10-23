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

dependencies {
    // compileOnly: the root build puts these plugins on the classpath (`apply false`), so the
    // convention plugins share the same plugin classes as every module.
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("jvmPure") {
            id = "framelock.jvm.pure"
            implementationClass = "dev.framelock.buildlogic.JvmPureConventionPlugin"
        }
        register("jvmArchitectureTests") {
            id = "framelock.jvm.architecture-tests"
            implementationClass = "dev.framelock.buildlogic.JvmArchitectureTestsConventionPlugin"
        }
        register("androidAdapter") {
            id = "framelock.android.adapter"
            implementationClass = "dev.framelock.buildlogic.AndroidAdapterConventionPlugin"
        }
        register("androidUi") {
            id = "framelock.android.ui"
            implementationClass = "dev.framelock.buildlogic.AndroidUiConventionPlugin"
        }
        register("androidApplication") {
            id = "framelock.android.application"
            implementationClass = "dev.framelock.buildlogic.AndroidApplicationConventionPlugin"
        }
    }
}
