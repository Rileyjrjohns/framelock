#!/usr/bin/env bash
# Downloads the fixed input video of the performance runs, checks its SHA-256 and pushes it to the debug app (M7).
#
#   fetch-input.sh [--file <local.mp4>] [--no-push]
#
# Default: downloads classroom.mp4 (1920x1080, H.264 High, 30 fps, 32.8 s, no audio, 13.5 MB) from the public
# sample-videos repository, pinned to a commit, and verifies it against the SHA-256 below. Licence: CC BY 4.0,
# attribution in docs/soup.md. The video is kept outside the repository (~/.framelock/perf/input.mp4, override with
# FRAMELOCK_PERF_DIR); nothing is transcoded.
# --file uses a local H.264 MP4 instead (for a different clip): its SHA-256 is printed, not checked, and must then be
# recorded by the runs (run.sh does it from the file on the device).
#
# Pushing: the debug app reads filesDir/perf/input.mp4, which only the app (or run-as, on a debuggable build) can write:
#   adb push input.mp4 /data/local/tmp/input.mp4
#   adb shell run-as dev.framelock sh -c 'mkdir -p files/perf && cp /data/local/tmp/input.mp4 files/perf/input.mp4'
# If the copy from /data/local/tmp is refused on a device, the file is streamed through run-as instead
# (`adb shell -T run-as ... sh -c 'cat > ...' < input.mp4`,
# binary-safe without a pty). The debug app must be installed (./gradlew installDebug).
# ANDROID_SERIAL selects the device when several are attached.
set -euo pipefail

PACKAGE="dev.framelock"
# Pinned to the commit of intel-iot-devkit/sample-videos at the time of M7; GitHub serves raw files by commit.
COMMIT="57978890822836f2b4743852f04f62fc511757e4"
URL="https://github.com/intel-iot-devkit/sample-videos/raw/$COMMIT/classroom.mp4"
SHA256="a69bd5e39ff0e74286d13bcbfdadddd307b85decd2d9853db7518e0028785e31"
DIR="${FRAMELOCK_PERF_DIR:-$HOME/.framelock/perf}"
TARGET="$DIR/input.mp4"

local_file=""
push=1
while [[ $# -gt 0 ]]; do
  case "$1" in
    --file) local_file="${2:?--file needs a path}"; shift 2 ;;
    --no-push) push=0; shift ;;
    -h|--help) sed -n '2,19p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown argument: $1 (see --help)" >&2; exit 2 ;;
  esac
done

sha256() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi
}

mkdir -p "$DIR"
if [[ -n "$local_file" ]]; then
  [[ -f "$local_file" ]] || { echo "No such file: $local_file" >&2; exit 1; }
  cp "$local_file" "$TARGET"
  echo "Local file used, SHA-256 $(sha256 "$TARGET") (not checked against a pinned value)"
else
  if [[ -f "$TARGET" && "$(sha256 "$TARGET")" == "$SHA256" ]]; then
    echo "Already downloaded: $TARGET"
  else
    tmp="$(mktemp)"
    trap 'rm -f "$tmp"' EXIT
    curl --fail --silent --show-error --location "$URL" --output "$tmp"
    actual="$(sha256 "$tmp")"
    if [[ "$actual" != "$SHA256" ]]; then
      echo "SHA-256 mismatch for $URL: expected $SHA256, got $actual" >&2
      exit 1
    fi
    mv "$tmp" "$TARGET"
    trap - EXIT
    echo "input video -> $TARGET"
  fi
fi

[[ $push -eq 1 ]] || exit 0

command -v adb >/dev/null || { echo "adb not found on PATH" >&2; exit 1; }
adb shell pm path "$PACKAGE" >/dev/null 2>&1 || { echo "$PACKAGE is not installed: run ./gradlew installDebug" >&2; exit 1; }
adb push "$TARGET" /data/local/tmp/input.mp4
if ! adb shell "run-as $PACKAGE sh -c 'mkdir -p files/perf && cp /data/local/tmp/input.mp4 files/perf/input.mp4'"; then
  echo "Copy from /data/local/tmp refused, streaming through run-as" >&2
  adb shell -T "run-as $PACKAGE sh -c 'mkdir -p files/perf && cat > files/perf/input.mp4'" < "$TARGET"
fi
adb shell rm -f /data/local/tmp/input.mp4
size="$(adb shell run-as "$PACKAGE" stat -c %s files/perf/input.mp4 | tr -d '\r')"
expected="$(wc -c < "$TARGET" | tr -d ' ')"
if [[ "$size" != "$expected" ]]; then
  echo "On-device size $size differs from $expected: the push failed" >&2
  exit 1
fi
echo "pushed -> $PACKAGE files/perf/input.mp4 ($size bytes)"
