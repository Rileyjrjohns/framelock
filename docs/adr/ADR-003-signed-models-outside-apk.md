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

Port additions needed for "load before switching" and for the audit trail are decided in M1 (amendment to this ADR).
