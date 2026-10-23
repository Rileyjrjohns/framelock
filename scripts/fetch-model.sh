#!/usr/bin/env bash
# Downloads the public detection model used from M2 on (docs/soup.md) and checks its SHA-256.
# EfficientDet-Lite0 with the TFLite detection post-processing op (4 outputs: boxes, classes, scores, count), from
# Kaggle Models (tensorflow/efficientdet/tfLite/lite0-detection-metadata, version 1, Apache-2.0).
# Places it where the debug app and the LiteRT golden test expect it. Model files are not committed (.gitignore).
set -euo pipefail

URL="https://www.kaggle.com/api/v1/models/tensorflow/efficientdet/tfLite/lite0-detection-metadata/1/download"
ARCHIVE_SHA256="0799da1decd268958a9eb8341aab08eb99dee27b0ffdf9924e10ce428a97e854"
MODEL_SHA256="2e04c53bfeac0ac2a30c057c7e2a777594ce39baaac35a92f74fb1e8c4fc4e0b"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TARGETS=(
  "$ROOT/app/src/debug/assets/models/efficientdet_lite0.tflite"
  "$ROOT/features/live/inference-litert/src/androidTest/assets/efficientdet_lite0.tflite"
)

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
