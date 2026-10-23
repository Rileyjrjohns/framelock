# ADR-007: Test frameworks

- Status: Accepted
- Date: 2026-10-22

## Context

The core is tested on the JVM with virtual time; Android adapters need some tests on a device.

## Decision

- **JVM and local Android tests**: JUnit Jupiter (JUnit 6.1.3) with `kotlinx-coroutines-test`. Local Android tests
  run on the JUnit Platform through `useJUnitPlatform()`; no extra Gradle plugin is needed.
- **Instrumented tests**: JUnit 4 with AndroidX Test.
- **No Robolectric**: Room and WorkManager are tested instrumented.
- **Fakes**: `:shared:testing` provides a fake for every port. It has no test framework on its main classpath (its
  assertions throw `AssertionError`), so both JUnit Jupiter and instrumented JUnit 4 tests can use it.

## Consequences

JUnit 6 requires Java 17, which is already the project baseline.
