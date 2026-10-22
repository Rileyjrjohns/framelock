# ADR-002: One inference at a time on a dedicated thread, latest frame wins

- Status: Accepted
- Date: 2026-10-22

## Context

The LiteRT GPU delegate must be created and invoked on the same thread. Frames arrive faster than inference can
process them, and queueing them would add latency without bound.

## Decision

- The engine adapter creates one single-thread executor (`Executors.newSingleThreadExecutor()
  .asCoroutineDispatcher()`) and exposes it as `InferenceEngine.dispatcher`. The pipeline runs `load`, `infer` and
  `close` on it. `limitedParallelism(1)` is not used: it does not guarantee the same thread.
- The thread lives as long as the engine (application scope), not as long as a capture session. A model swap while
  idle therefore runs on the same thread as the delegate it replaces.
- Latest frame wins: frames arriving during an inference are conflated. Dropped frames are counted and their
  buffers released immediately. Frames older than the last processed one, or from a previous session, are dropped.

The dispatcher is part of the port so that the core stays free of threading decisions that belong to the runtime,
and so that tests can inject a test dispatcher.

## Consequences

- Core tests run the pipeline under virtual time with an injected dispatcher; a few tests use a real thread to
  check thread confinement.
- A Konsist rule forbids `limitedParallelism` in the pipeline.
