#!/usr/bin/env bash
# Downloads the public detection model used from M2 on (docs/soup.md) and checks its SHA-256.
# EfficientDet-Lite0 with the TFLite detection post-processing op (4 outputs: boxes, classes, scores, count), from
# Kaggle Models (tensorflow/efficientdet/tfLite/lite0-detection-metadata, version 1, Apache-2.0).
# Places it where the debug app and the LiteRT golden test expect it, then signs it for the debug build: creates a
# development key pair outside the repository if there is none (~/.framelock/keys/development*.pem), writes its public
# key to app/src/debug/assets/keys/ (the debug build trusts it) and packs the model as a .flmodel there, which the
# debug app imports at its first start (scripts/sign-model.sh, ADR-003). Re-running re-signs with the same key.
# Model files and keys are not committed (.gitignore).
set -euo pipefail

URL="https://www.kaggle.com/api/v1/models/tensorflow/efficientdet/tfLite/lite0-detection-metadata/1/download"
ARCHIVE_SHA256="0799da1decd268958a9eb8341aab08eb99dee27b0ffdf9924e10ce428a97e854"
MODEL_SHA256="2e04c53bfeac0ac2a30c057c7e2a777594ce39baaac35a92f74fb1e8c4fc4e0b"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
# The raw model is only needed by tests (LiteRT golden test, embedded labels); the app gets the signed package.
TARGETS=(
  "$ROOT/features/live/inference-litert/src/androidTest/assets/efficientdet_lite0.tflite"
)

KEY_DIR="${FRAMELOCK_KEY_DIR:-$HOME/.framelock/keys}"
DEBUG_ASSETS="$ROOT/app/src/debug/assets"
VERSION="efficientdet-lite0-int8"
# Tensors the LiteRT engine expects: boxes [1,25,4], classes [1,25], scores [1,25], count [1].
INPUT="1x320x320x3:UINT8"
OUTPUTS="1x25x4:FLOAT32;1x25:FLOAT32;1x25:FLOAT32;1:FLOAT32"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
curl --fail --silent --show-error --location "$URL" --output "$work/model.tar.gz"
check() {
  local actual
  actual="$(shasum -a 256 "$1" | cut -d' ' -f1)"
  if [[ "$actual" != "$2" ]]; then
    echo "SHA-256 mismatch for $1: expected $2, got $actual" >&2
    exit 1
  fi
}
check "$work/model.tar.gz" "$ARCHIVE_SHA256"
tar -xzf "$work/model.tar.gz" -C "$work"
check "$work/1.tflite" "$MODEL_SHA256"
for target in "${TARGETS[@]}"; do
  mkdir -p "$(dirname "$target")"
  cp "$work/1.tflite" "$target"
  echo "model -> $target"
done

if [[ ! -f "$KEY_DIR/development.pem" ]]; then
  "$ROOT/scripts/sign-model.sh" --new-key "$KEY_DIR" --name development
fi
mkdir -p "$DEBUG_ASSETS/keys"
cp "$KEY_DIR/development.pub.pem" "$DEBUG_ASSETS/keys/development.pub.pem"
echo "trusted key -> $DEBUG_ASSETS/keys/development.pub.pem"
"$ROOT/scripts/sign-model.sh" --model "$work/1.tflite" --version "$VERSION" --input "$INPUT" --outputs "$OUTPUTS" \
  --key "$KEY_DIR/development.pem" --out "$DEBUG_ASSETS/models/$VERSION.flmodel"
