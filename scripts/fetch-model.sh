#!/usr/bin/env bash
# Downloads the public detection model used from M2 on (docs/soup.md) and checks its SHA-256.
# Places it where the debug app and the LiteRT golden test expect it. Model files are not committed (.gitignore).
set -euo pipefail

URL="https://storage.googleapis.com/mediapipe-models/object_detector/efficientdet_lite0/int8/1/efficientdet_lite0.tflite"
SHA256="0720bf247bd76e6594ea28fa9c6f7c5242be774818997dbbeffc4da460c723bb"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TARGETS=(
  "$ROOT/app/src/debug/assets/models/efficientdet_lite0.tflite"
  "$ROOT/features/live/inference-litert/src/androidTest/assets/efficientdet_lite0.tflite"
)

tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT
curl --fail --silent --show-error --location "$URL" --output "$tmp"
actual="$(shasum -a 256 "$tmp" | cut -d' ' -f1)"
if [[ "$actual" != "$SHA256" ]]; then
  echo "SHA-256 mismatch: expected $SHA256, got $actual" >&2
  exit 1
fi
for target in "${TARGETS[@]}"; do
  mkdir -p "$(dirname "$target")"
  cp "$tmp" "$target"
  echo "model -> $target"
done
