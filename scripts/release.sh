#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd -- "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
"$PROJECT_DIR/test.sh"

windows_exe="$PROJECT_DIR/windows/release/Wetterkurve/Wetterkurve.exe"
windows_zip="$PROJECT_DIR/dist/Wetterkurve-Windows-x64.zip"
test -f "$windows_exe"
mkdir -p "$PROJECT_DIR/dist"
rm -f "$windows_zip"
(cd "$PROJECT_DIR/windows/release" && zip -r "$windows_zip" Wetterkurve)

printf 'Release packages are ready:\n  %s\n  %s\n' \
  "$PROJECT_DIR/dist/wetterkurve@wean.de.shell-extension.zip" \
  "$windows_zip"
