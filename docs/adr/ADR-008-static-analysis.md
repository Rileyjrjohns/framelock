# ADR-008: Static analysis

- Status: Accepted
- Date: 2026-10-22

## Context

detekt 1.23.8 is the only stable detekt release. It is built for Kotlin 2.0 and AGP 8, cannot hook into AGP 9's
built-in Kotlin, and uses a Gradle API removed in Gradle 10. detekt 2.0 is still a prerelease.

## Decision

- **Android Lint** on Android modules.
- **ktlint 1.8.0 through Spotless**, on every Kotlin and Gradle Kotlin file, with the Compose rule set.
- **detekt 1.23.8 on the pure-Kotlin modules only** (`:shared:*`, every `domain` and `application`), with its default configuration.

## Consequences

- Re-evaluate when detekt 2.0 is stable, or before moving to Gradle 10, whichever comes first.
- ktlint parses with Kotlin 2.2; syntax newer than that would fail to lint and would be reported, not worked around.
