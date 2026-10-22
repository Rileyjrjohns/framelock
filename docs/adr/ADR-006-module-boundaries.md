# ADR-006: Module boundaries enforced by Gradle and Konsist

- Status: Accepted
- Date: 2026-10-22

## Context

The architecture depends on dependency rules: adapters depend on the domain only, never on each other; the core is
pure Kotlin with kotlinx-coroutines-core as its only library. Rules that are only written down erode.

## Decision

Two complementary checks, both part of `./gradlew check`:

1. **Gradle**: the settings plugin `framelock.module-boundaries` (in `build-logic/boundaries`) applies to every
   project, so no module can opt out. Each module gets a role from its path; a module without a role fails the build.
   `verifyModuleBoundaries` checks the project dependencies declared on every configuration (main and test), and for
   `:core:*` the external modules resolved on `compileClasspath` and `runtimeClasspath` (so a `compileOnly` DI
   annotation library is caught too).

   | Module | May depend on (main) | Test configurations may add |
   |---|---|---|
   | `:core:domain` | nothing | `:core:testing` |
   | `:core:application`, `:core:testing`, `:adapters:*` | `:core:domain` | `:core:testing` |
   | `:app` | `:core:domain`, `:core:application`, `:adapters:*` | `:core:testing` |
   | `:architecture-tests` | nothing | nothing |

   TestKit tests in `build-logic/boundaries` prove each failure case.
2. **Konsist** (`:architecture-tests`): import-level rules the Gradle graph cannot see: no `android.*` and no DI
   annotations in the core, a per-module import allowlist for the core, no cross-adapter imports, ports implemented
   only in adapters, no `limitedParallelism` in the pipeline, no Hilt or Koin.

## Consequences

- Adding a module means giving it a role in `ModuleRole` first.
- The boundary plugin lives in its own build-logic project because settings plugins are loaded in the settings
  classloader, where AGP is not visible.
- Every module keeps at least one source file, otherwise Konsist rules would pass on an empty scope; a rule fails
  when its scope is empty.
