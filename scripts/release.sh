#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
"$PROJECT_DIR/test.sh"

printf 'Release package is ready: %s\n' \
  "$PROJECT_DIR/dist/wetterkurve@wean.de.shell-extension.zip"
