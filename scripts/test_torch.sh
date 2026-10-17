#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
kotlinc "$ROOT/tests/torch-stubs/CameraStub.kt" "$ROOT/tests/torch-stubs/HandlerStub.kt" \
  "$ROOT/app/src/main/java/com/minhacamera/PhoneEcoPolicy.kt" \
  "$ROOT/app/src/main/java/com/minhacamera/PhoneTorchController.kt" \
  "$ROOT/tests/PhoneTorchControllerSmoke.kt" -include-runtime -d "$TMP/torch.jar"
java -jar "$TMP/torch.jar"
