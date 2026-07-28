#!/usr/bin/env bash
set -euo pipefail

PROJECT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
UUID="muenchen-wetter@wean.de"
TARGET_DIR="${XDG_DATA_HOME:-$HOME/.local/share}/gnome-shell/extensions/$UUID"

"$PROJECT_DIR/test.sh"
mkdir -p -- "$(dirname -- "$TARGET_DIR")"

if [[ -e "$TARGET_DIR" && ! -L "$TARGET_DIR" ]]; then
  printf 'Abbruch: %s existiert bereits und ist kein Symlink.\n' "$TARGET_DIR" >&2
  exit 1
fi

ln -sfn -- "$PROJECT_DIR/extension" "$TARGET_DIR"

if gnome-extensions info "$UUID" >/dev/null 2>&1; then
  gnome-extensions enable "$UUID"
  printf 'München Wetter ist installiert und aktiviert.\n'
else
  printf '%s\n' \
    'München Wetter ist installiert.' \
    'GNOME Shell kennt die neue Erweiterung noch nicht.' \
    'Bitte einmal abmelden und wieder anmelden; danach wird sie aktiviert.'
fi
