#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
PACKAGE_PATH="$PROJECT_DIR/dist/wetterkurve@wean.de.shell-extension.zip"
STAGE_DIR="$(mktemp -d)"
cleanup() { rm -rf -- "$STAGE_DIR"; }
trap cleanup EXIT

mkdir -p "$PROJECT_DIR/dist" "$STAGE_DIR/icons" "$STAGE_DIR/schemas"
cp "$PROJECT_DIR/extension/metadata.json" \
  "$PROJECT_DIR/extension/extension.js" \
  "$PROJECT_DIR/extension/language.js" \
  "$PROJECT_DIR/extension/weather.js" \
  "$PROJECT_DIR/extension/stylesheet.css" \
  "$STAGE_DIR/"
cp -R "$PROJECT_DIR/extension/icons/." "$STAGE_DIR/icons/"
cp "$PROJECT_DIR/extension/schemas/org.gnome.shell.extensions.wetterkurve.gschema.xml" \
  "$STAGE_DIR/schemas/"

rm -f -- "$PACKAGE_PATH"
(
  cd -- "$STAGE_DIR"
  zip -q -r "$PACKAGE_PATH" \
    metadata.json extension.js language.js weather.js stylesheet.css icons schemas
)

printf 'Package created: %s\n' "$PACKAGE_PATH"
