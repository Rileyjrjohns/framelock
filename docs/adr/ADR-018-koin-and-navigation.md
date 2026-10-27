# ADR-018: Application composition and navigation

- Status: Accepted
- Date: 2026-10-27
- Amends: ADR-006 and ADR-014

## Context

The composition root mixes dependency construction, startup work and screen wiring. Its lifetimes and threading
contracts must survive the move to a container. Feature code must stay independent of DI and platform navigation.

## Decision

Use Koin 4.2.2 only in `:app`, with modules for system services, telemetry, live inference and model management.
Keep the classic constructor DSL: it needs no annotation processor or additional compiler plugin. The whole module
tree is checked with the documented JVM `verify()` API. Values constructed inside definitions are declared explicitly
in that check; missing application services must still fail it.

Pipeline, engine, monitors, repository, audit, source selection, telemetry writer and coroutine scope are application
singletons. ViewModels are lifecycle-owned. Named startup, source-wiring, model-listing and debug-launch classes keep
side effects out of container initialization. Presentation contracts replace bundles of unrelated suspend lambdas;
feature UI modules retain their existing inward dependencies.

Keep the existing telemetry worker factory, resolved from Koin by `Configuration.Provider`. The default initializer
stays disabled. Schedule uploads only after container startup. Keep the debug tee, delayed upload and launch extras.

Navigation Compose and the application navigator will be added in the next phase, before routes are moved out of the
activity. Guards belong to a pure redirect function. This ADR records the intended boundary, not completed routing.

## Alternatives considered

- Manual DI preserves static wiring but leaves the composition root and factories to grow together.
- Hilt adds generated Android entry points and annotations where an app-confined container is sufficient.
- Navigation 3 would require a different back-stack ownership model for an existing two-screen flow.

## Consequences

DI remains an application concern, enforced by Gradle and source rules. Reflection-based verification does not inspect
`get()` calls inside definition lambdas or qualifiers; constructor dependencies and explicit bindings stay the primary
form of wiring. Device verification remains necessary for Android initialization and native resources.
