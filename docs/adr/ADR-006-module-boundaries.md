# ADR-006: Module boundaries enforced by Gradle and Konsist

- Status: Accepted
- Date: 2026-10-22

## Context

The architecture depends on dependency rules: adapters depend on the domain only, never on each other; the core is
pure Kotlin with kotlinx-coroutines-core as its only library; DI is manual. Rules that are only written down erode.

## Decision

Two complementary checks, both part of `./gradlew check`.

### 1. Gradle: `verifyModuleBoundaries`

The settings plugin `framelock.module-boundaries` (in `build-logic/boundaries`) applies to every project. Each module
gets a role from its path; a module without a role fails the build.

The check works on **resolved classpaths, transitively**, not on declared dependencies. Whatever puts a module on a
classpath (a declaration, `extendsFrom`, dependency substitution, a test fixture, a transitive edge), the result is
checked. Every `compileClasspath`, `runtimeClasspath` and `<variant>(Compile|Runtime)Classpath` is checked.

| Module | May reach on production classpaths | Test classpaths may add |
|---|---|---|
| `:core:domain` | nothing | `:core:testing` |
| `:core:application`, `:core:testing`, `:adapters:*` | `:core:domain` | `:core:testing` |
| `:app` | `:core:domain`, `:core:application`, `:adapters:*` | `:core:testing` |
| `:architecture-tests` | nothing | nothing |

- **Test classpaths** are recognised by their exact shape only: `test(Compile|Runtime)Classpath` and
  `<variant>(UnitTest|AndroidTest)(Compile|Runtime)Classpath`. Everything else is production, including
  `testFixtures…` and flavors whose name contains "Test".
- **External libraries of `:core:*`** (production classpaths): only `kotlin-stdlib*`, `kotlinx-coroutines-core*`
  (and its BOM) and `org.jetbrains:annotations`. `files(...)` dependencies are refused in `:core:*`: a jar has no
  identity the check could verify.
- **DI libraries** (`com.google.dagger`, `io.insert-koin`, `javax.inject`, `jakarta.inject`, `com.google.inject`) are
  refused on every production classpath of every module. Test tooling may bring them transitively (espresso does).
- A project from another build is refused.

TestKit tests in `build-logic/boundaries` prove each failure case, including the bypasses above.

### 2. Konsist: `:architecture-tests`

Source-level rules the classpath cannot see. References are read from imports **and** from fully qualified names in
the code (comments and strings removed).

- The core references no `android.`, `androidx.` or `com.google.android.`.
- No module references a DI framework or DI annotation (`javax.inject.`, `jakarta.inject.`, `dagger.`, `org.koin.`,
  `com.google.inject.`).
- The core's production imports stay within a per-module allowlist. Entries ending with a dot are package prefixes,
  other entries are exact class names:

  | Module | Allowed imports |
  |---|---|
  | `:core:domain` | `kotlin.`, `kotlinx.coroutines.`, `dev.framelock.core.domain.`, `java.io.InputStream` (model import source) |
  | `:core:application` | `kotlin.`, `kotlinx.coroutines.`, `dev.framelock.core.domain.`, `dev.framelock.core.application.` |
  | `:core:testing` | `kotlin.`, `kotlinx.coroutines.`, `dev.framelock.core.domain.`, `dev.framelock.core.testing.`, `java.util.concurrent.` (thread-safe fakes) |

- The domain does not reference the application layer; adapters reference neither another adapter nor the
  application layer.
- **Ports** are the non-sealed interfaces of `dev.framelock.core.domain.port`. A port named in architecture.md must be
  declared there, may not be a `fun interface`, and may be implemented only in adapters, in `:core:testing` or in
  test sources. Implementations are found through named, nested and local types (indirect parents included, names
  resolved through imports, aliases, star imports and the same package) and through object expressions.
- The core never calls `limitedParallelism` (ADR-002).

Every rule is also run on fixture trees (`architecture-tests/src/test/fixtures`) that break it on purpose, so a rule
that silently stops matching fails its own test. Module scopes must be non-empty.

## Consequences

- Adding a module means giving it a role in `ModuleRole` first. Widening an allowlist is a change to this ADR.
- The boundary plugin lives in its own build-logic project because settings plugins are loaded in the settings
  classloader, where AGP is not visible.
- Every module keeps at least one source file, otherwise Konsist rules would pass on an empty scope.
- Known limits: a check can still be disabled on purpose in a build script (as any Gradle check can), and a build
  type literally named `unitTest` or `androidTest` would make its classpaths look like test ones. Both are visible
  in review.
