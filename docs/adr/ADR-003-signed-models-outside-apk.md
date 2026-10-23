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

### Amendment (2026-10-23, M1 review)

- The swap lock is the pipeline's lifecycle lock. `ModelLoading.swapModel(target, restore, commit)` loads the target,
  runs the repository commit and, if the commit fails, loads `restore` back, all under that lock; the commit and the
  restore cannot be cancelled. `StartPipeline` reads the active model under the same lock. Engine and repository
  therefore always agree, and a session never starts in the middle of a swap.
- A swap is accepted when no session is connected (`Idle`, `NoSignal`), as `ModelSwapPolicy` says.
- Activating the model that is already active changes nothing and records nothing.
- A successful import emits `ModelImported(version)`; a restore that fails after a failed commit is audited too.
- Model versions cannot be `.` or `..` (they name directories).
- Left for M5, with the model store: audit entries also need a wall-clock time (the `Clock` port is monotonic and
  resets at boot), and the signature must cover the manifest (version, tensor specs), not only the model file.

### Amendment (2026-10-23, M5)

Closes the two points left for M5.

- **Package.** One file `<name>.flmodel`, a zip of `model.tflite`, `manifest.properties`, `manifest.sig` and an optional
  `labels.txt`. `manifest.properties` is a `java.util.Properties` file and replaces the `manifest.json` above: it is
  pure JDK, parses on the JVM and needs no JSON dependency. Keys: `version`, `sha256` (of `model.tflite`), `input`,
  `outputs` and, when `labels.txt` is present, `labels.sha256`. A tensor spec is its dimensions joined by `x`, a
  colon and the type (`1x320x320x3:UINT8`); `outputs` are separated by `;`. Unknown keys are ignored.
- **Signature.** `manifest.sig` is a DER ECDSA P-256 signature (`SHA256withECDSA`) over the exact bytes of
  `manifest.properties`. It therefore covers the version and tensor specs and, through the hashes, the model and the
  labels. `ModelManifest.signature` holds its Base64. The check runs before the manifest is parsed; a package without
  `manifest.sig` is `BAD_SIGNATURE`. A signature verifies if any trusted key accepts it.
- **Zip rules** (`PackageExtractor`). Only the four names above, each once; no directory entries and no path
  separators, so nothing is written outside the staging directory; a size limit per entry counted on the bytes read
  (model 256 MiB, manifest 16 KiB, signature 256 B, labels 1 MiB). A broken rule, a truncated or invalid zip, or a
  missing file is `MALFORMED_MANIFEST` (there is no separate reason for a malformed package). `labels.txt` and
  `labels.sha256` must both be present or both absent.
- **Layout.** `filesDir/models/<version>/{model.tflite, manifest.properties, manifest.sig, labels.txt?}`;
  pointers `filesDir/model-state/{active, previous}`; unpacking in `filesDir/model-staging/`. The pointers and the
  staging area are not under `models/` because every `ModelVersion` token, `active` included, is a valid directory
  name. All three are in `filesDir`, so the final step is a rename on one file system (`ATOMIC_MOVE`).
- **Import** unpacks, verifies the signature, parses, checks the SHA-256 of the model and labels, then moves the
  directory. A failure leaves `models/` untouched and the staging area is emptied. Importing a version already
  installed is **idempotent** when `manifest.properties` is byte for byte the same (nothing is written, success is
  audited); with other content it is refused as `BAD_HASH`, because a version names one model. A leftover directory
  without a complete set of files is replaced. A source that cannot be read is an I/O failure (`IO_ERROR`).
- **Pointers.** Written to a temporary file, flushed, renamed over the pointer. Activation writes `previous` (the old
  active) first, then `active`; if interrupted between the two, `previous` equals `active` and is reported as none.
  Rollback swaps the two, so a second rollback returns. A pointer to a version that is gone reads as no model.
  Reading a stored version does not check its signature again: the import is the trust boundary and `filesDir` is
  private to the app.
- **Trusted keys.** `FileModelRepository` takes a list of public keys. `:app` loads every `assets/keys/*.pub.pem` (X.509
  SubjectPublicKeyInfo PEM); a file that does not parse is skipped and logged. The debug build trusts a development
  key that `scripts/fetch-model.sh` creates outside the repository and copies to `app/src/debug/assets/keys/`. No
  release key exists yet: a release build with no key in `app/src/main/assets/keys/` refuses every import.
- **Audit.** `FileAuditLog` writes `filesDir/audit/audit.log`, one line per entry:
  `<epoch millis> <ACTION> <version or -> <OUTCOME> <REASON or ->`. The time is the wall clock read by the adapter
  when it writes (the `Clock` port is monotonic and resets at boot, so `AuditEntry.atNanos` is not stored). Only
  enums, a version token and a number. At 256 KiB the file becomes `audit.log.1`, replacing the previous one. A write
  that fails is dropped: the audit line must not undo a swap that already happened.
- **Bootstrap.** Debug builds bundle `assets/models/efficientdet-lite0-int8.flmodel`. At startup, off the main thread,
  if no model is active, `:app` imports it through `ImportModel` and activates it through `ActivateModel`, so it is
  verified, audited and recorded like any import. The unsigned development path is gone. Release builds bundle no
  package.
- **Labels.** `FileModelRepository.labels(version)` returns `labels.txt` when the package has one, else the labels
  embedded in the `.tflite` (`EmbeddedLabels`, moved from `:app`). `:app` caches them for the active version and
  refreshes after startup and after each swap, so drawing never reads a file.
- **Tooling.** `scripts/sign-model.sh` creates a key pair (`--new-key`, private key outside the repository, never
  overwritten) and builds a package. openssl writes DER signatures, which `SHA256withECDSA` accepts
  (`SigningScriptTest`).
