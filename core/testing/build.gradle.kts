// Fakes of every port, shared by the tests of all modules. No test framework on its main classpath:
// assertions throw AssertionError so JUnit Jupiter and instrumented JUnit 4 tests can both use them.
plugins {
    id("framelock.jvm.core")
}

dependencies {
    api(projects.core.domain)
}
