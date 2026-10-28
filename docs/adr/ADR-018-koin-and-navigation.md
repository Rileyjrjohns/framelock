# ADR-018: Application composition and navigation

- Status: Accepted
- Date: 2026-10-28
- Amends: ADR-006 and ADR-014

## Context

The composition root mixes dependency construction, startup work and screen wiring. Its lifetimes and threading
contracts must survive the move to a container. Feature code must stay independent of DI and platform navigation.

## Decision

Use Koin 4.2.2 only in `:app`, with modules for system services, telemetry, live inference, model management and navigation.
Keep the classic constructor DSL: it needs no annotation processor or additional compiler plugin. The whole module
tree is checked with the documented JVM `verify()` API. Values constructed inside definitions are declared explicitly
in that check; missing application services must still fail it.

Pipeline, engine, monitors, repository, audit, source selection, telemetry writer and coroutine scope are application
singletons. ViewModels are lifecycle-owned. Named startup, source-wiring, model-listing and debug-launch classes keep
side effects out of container initialization. Presentation contracts replace bundles of unrelated suspend lambdas;
feature UI modules retain their existing inward dependencies.

Keep the existing telemetry worker factory, resolved from Koin by `Configuration.Provider`. The default initializer
stays disabled. Schedule uploads only after container startup. Keep the debug tee, delayed upload and launch extras.

Navigation Compose 2.10.2 owns the saved back stack. Serializable Live and Models routes are declared in `AppRoutes`,
with one graph extension per feature. `AppNavigator` queues commands in a channel; the host consumes them while started.
Consumed commands are not replayed on rotation. `redirectOf` is the single guard point and currently redirects nothing.
The activity remains the ViewModel owner, preserving debug autostart and presentation state when visiting Models.
Destination changes drive screen tracking, including system back. Transitions are disabled to preserve the existing
immediate screen switch. The serialization compiler plugin is applied only in `:app`.

## Alternatives considered

- Manual DI preserves static wiring but leaves the composition root and factories to grow together.
- Hilt adds generated Android entry points and annotations where an app-confined container is sufficient.
- Navigation 3 would require a different back-stack ownership model for an existing two-screen flow.

## Consequences

DI remains an application concern, enforced by Gradle and source rules. Reflection-based verification does not inspect
`get()` calls inside definition lambdas or qualifiers; constructor dependencies and explicit bindings stay the primary
form of wiring. Device verification remains necessary for Android initialization and native resources.

Versions were checked against the [Koin setup guide](https://insert-koin.io/docs/setup/), Maven Central metadata and
[Navigation release notes](https://developer.android.com/jetpack/androidx/releases/navigation). The
[verification guide](https://insert-koin.io/docs/reference/koin-test/verify/) documents the runtime-parameter exceptions.
The existing worker factory avoids a second WorkManager integration and preserves its upload contract.
