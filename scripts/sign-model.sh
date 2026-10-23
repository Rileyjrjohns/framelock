#!/usr/bin/env bash
# Signs a model and packs it as a .flmodel (docs/adr/ADR-003-signed-models-outside-apk.md, amendment M5).
#
#   sign-model.sh --new-key [<dir>] [--name <name>]
#       Creates an ECDSA P-256 key pair: <dir>/<name>.pem (private, mode 600) and <dir>/<name>.pub.pem (public,
#       what the app trusts). Default dir ~/.framelock/keys, default name release. Never overwrites a key.
#
#   sign-model.sh --model m.tflite --version v1 --input 1x320x320x3:UINT8 \
#       --outputs '1x25x4:FLOAT32;1x25:FLOAT32;1x25:FLOAT32;1:FLOAT32' --key private.pem [--labels labels.txt] --out m.flmodel
#       Writes manifest.properties (version, sha256 of the model, tensor specs, sha256 of the labels), signs its exact
#       bytes with SHA256withECDSA (DER, as openssl writes it) and zips model.tflite, manifest.properties,
#       manifest.sig and labels.txt (when given). Tensor specs are <dims joined by x>:<FLOAT32|UINT8|INT8>.
#
# The private key stays outside the repository (*.pem is ignored there as a second guard).
set -euo pipefail

usage() {
  sed -n '2,15p' "$0" | sed 's/^# \{0,1\}//' >&2
  exit 2
}

sha256() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | cut -d' ' -f1; else shasum -a 256 "$1" | cut -d' ' -f1; fi
}

new_key() {
  local dir="$1" name="$2"
  umask 077
  mkdir -p "$dir"
  if [[ -e "$dir/$name.pem" || -e "$dir/$name.pub.pem" ]]; then
    echo "Refusing to overwrite $dir/$name.pem" >&2
    exit 1
  fi
  openssl genpkey -algorithm EC -pkeyopt ec_paramgen_curve:P-256 -out "$dir/$name.pem" 2>/dev/null
  openssl pkey -in "$dir/$name.pem" -pubout -out "$dir/$name.pub.pem"
  chmod 600 "$dir/$name.pem"
  echo "private key (keep it out of the repository): $dir/$name.pem"
  echo "public key (the app trusts it): $dir/$name.pub.pem"
}

model="" version="" input="" outputs="" key="" labels="" out="" newkey=0 keydir="$HOME/.framelock/keys" name="release"
while [[ $# -gt 0 ]]; do
  case "$1" in
    --new-key)
      newkey=1
      if [[ $# -gt 1 && "$2" != --* ]]; then keydir="$2"; shift; fi
      ;;
    --name) name="${2:?}"; shift ;;
    --model) model="${2:?}"; shift ;;
    --version) version="${2:?}"; shift ;;
    --input) input="${2:?}"; shift ;;
    --outputs) outputs="${2:?}"; shift ;;
    --key) key="${2:?}"; shift ;;
    --labels) labels="${2:?}"; shift ;;
    --out) out="${2:?}"; shift ;;
    -h|--help) usage ;;
    *) echo "Unknown argument: $1" >&2; usage ;;
  esac
  shift
done

if [[ $newkey -eq 1 ]]; then
  new_key "$keydir" "$name"
  exit 0
fi

for required in model version input outputs key out; do
  [[ -n "${!required}" ]] || { echo "Missing --$required" >&2; usage; }
done
[[ -f "$model" ]] || { echo "No such model: $model" >&2; exit 1; }
[[ -f "$key" ]] || { echo "No such key: $key" >&2; exit 1; }
[[ -z "$labels" || -f "$labels" ]] || { echo "No such labels file: $labels" >&2; exit 1; }
# Same rules as ModelVersion and the tensor spec parser; the app would reject the package otherwise.
[[ "$version" =~ ^[A-Za-z0-9._-]{1,64}$ && "$version" != "." && "$version" != ".." ]] ||
  { echo "Invalid version: $version" >&2; exit 1; }
spec='[0-9]+(x[0-9]+)*:(FLOAT32|UINT8|INT8)'
[[ "$input" =~ ^$spec$ && "$outputs" =~ ^$spec(\;$spec)*$ ]] || { echo "Invalid tensor spec in --input or --outputs" >&2; exit 1; }

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
cp "$model" "$work/model.tflite"
{
  echo "version=$version"
  echo "sha256=$(sha256 "$work/model.tflite")"
  echo "input=$input"
  echo "outputs=$outputs"
  if [[ -n "$labels" ]]; then
    cp "$labels" "$work/labels.txt"
    echo "labels.sha256=$(sha256 "$work/labels.txt")"
  fi
} >"$work/manifest.properties"
openssl dgst -sha256 -sign "$key" -out "$work/manifest.sig" "$work/manifest.properties"

files=(model.tflite manifest.properties manifest.sig)
[[ -z "$labels" ]] || files+=(labels.txt)
mkdir -p "$(dirname "$out")"
out_abs="$(cd "$(dirname "$out")" && pwd)/$(basename "$out")"
rm -f "$out_abs"
(cd "$work" && zip -q -X "$out_abs" "${files[@]}")
echo "signed $version -> $out"
