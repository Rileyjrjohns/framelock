# ADR-003: Models outside the APK, signed, swapped only when idle

- Status: Accepted
- Date: 2026-10-22

## Context

Models change more often than the app. A model loaded from storage must be authentic, and swapping it during a
capture session would change results mid-session.

## Decision

- Models live in `filesDir/models/<version>/{model.tflite, manifest.json}` with an `active` pointer file.
- Import: copy to a temporary directory, verify SHA-256 and an ECDSA P-256 signature (`SHA256withECDSA`) against a
  public key embedded in the app, then move atomically.
- Activate only when `ModelSwapPolicy` allows it (pipeline `Idle` or `NoSignal`). The engine loads and warms up the
  new model before the pointer switches; on failure the previous model stays active. The previous version stays on
  disk for rollback.
- Every import, activation, rejection and rollback emits telemetry and an audit entry.
- `scripts/sign-model.sh` signs a model with a private key kept out of the repository.

## Consequences

### Amendment (2026-10-23, M1)

- `ModelRepository` gains `artifact(version)` and `previous()`, so the engine can load the new (or previous) model
  before the pointer switches.
- New port `AuditLog.record(AuditEntry)`; an entry has an action, a version, an outcome and a timestamp. Implemented by
  the model store in M5.
- `ModelSource` is a domain interface (`displayName`, `open(): InputStream`), so the domain never sees an Android `Uri`.
- `ActivateModel` and `RollbackModel` run only when `ModelSwapPolicy` allows it, load on the engine's thread, and hold a
  swap lock that `StartPipeline` also takes, so a session cannot start in the middle of a swap. If the repository
  fails after a successful load, the previous model is loaded back.
- Starting without an active model fails with a typed error. Importing is allowed only when idle or without signal
  (it costs CPU and I/O).
- Refusals and failures emit `ModelRejected(reason)` with `SWAP_REFUSED` or `LOAD_FAILED`, plus an audit entry.
