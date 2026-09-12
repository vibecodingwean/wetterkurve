#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd -- "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
"$PROJECT_DIR/test.sh"

windows_exe="$PROJECT_DIR/windows/release/Wetterkurve/Wetterkurve.exe"
windows_zip="$PROJECT_DIR/dist/Wetterkurve-Windows-x64.zip"
dotnet test "$PROJECT_DIR/windows/Wetterkurve.Core.Tests" -c Release --nologo
rm -rf -- "$PROJECT_DIR/windows/release/Wetterkurve"
dotnet publish "$PROJECT_DIR/windows/Wetterkurve.Desktop" \
  -c Release -r win-x64 --self-contained false -p:EnableWindowsTargeting=true \
  -o "$PROJECT_DIR/windows/release/Wetterkurve" --nologo
test -f "$windows_exe"
mkdir -p "$PROJECT_DIR/dist"
rm -f "$windows_zip"
(cd "$PROJECT_DIR/windows/release" && zip -r "$windows_zip" Wetterkurve)

apk_debug="$PROJECT_DIR/android/app/build/outputs/apk/debug/app-debug.apk"
apk_name=""
if [ -f "$apk_debug" ]; then
  apk_version="$(awk -F'"' '/versionName[[:space:]]*=/ {print $2; exit}' \
    "$PROJECT_DIR/android/app/build.gradle.kts")"
  apk_name="$PROJECT_DIR/dist/Wetterkurve-android-${apk_version}.apk"
  cp -f -- "$apk_debug" "$apk_name"
fi

printf 'Release packages are ready:\n  %s\n  %s\n' \
  "$PROJECT_DIR/dist/wetterkurve@wean.de.shell-extension.zip" \
  "$windows_zip"
if [ -n "$apk_name" ]; then
  printf '  %s\n' "$apk_name"
fi
