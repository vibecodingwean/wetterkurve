#!/usr/bin/env bash
# Source from a Wetterkurve Android build:  source scripts/android-env.sh
set -euo pipefail
HOST_ANDROID="${ANDROID_TOOLCHAIN_ROOT:-$HOME/.local/share/android-toolchains}"
if [[ ! -f "$HOST_ANDROID/env.sh" ]]; then
  printf 'Android toolchain missing: %s\n' "$HOST_ANDROID/env.sh" >&2
  exit 1
fi
# shellcheck source=/dev/null
source "$HOST_ANDROID/env.sh"
sdk_dir="$ANDROID_SDK_ROOT"
props="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)/android/local.properties"
printf 'sdk.dir=%s\n' "$sdk_dir" > "$props"
export JAVA_HOME ANDROID_HOME ANDROID_SDK_ROOT
