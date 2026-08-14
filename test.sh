#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"

glib-compile-schemas "$PROJECT_DIR/extension/schemas"
node "$PROJECT_DIR/tests/metadata.test.js"
node "$PROJECT_DIR/tests/weather.test.js"
node --check "$PROJECT_DIR/extension/extension.js"
node --check "$PROJECT_DIR/extension/weather.js"
"$PROJECT_DIR/scripts/package.sh"

printf 'All tests passed. Package: %s\n' \
  "$PROJECT_DIR/dist/wetterkurve@wean.de.shell-extension.zip"
