#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REMOTE="${BEQUIET_SSH:-binary@192.168.178.20}"
WIN_SRC='C:\Users\weber\AppData\Local\Wetterkurve\src'
WSL_SRC='/mnt/c/Users/weber/AppData/Local/Wetterkurve/src'
DURABLE=~/Dokumente/CUDA_Workload/projects/wetterkurve-windows

echo "Syncing Windows project to bequiet..."
ssh "$REMOTE" "mkdir -p $DURABLE '$WSL_SRC'"
rsync -a --delete \
    --exclude bin --exclude obj --exclude .vs \
    "$ROOT/windows/" "$REMOTE:$WSL_SRC/"
ssh "$REMOTE" "mkdir -p $DURABLE && rsync -a --delete '$WSL_SRC/' $DURABLE/"

echo "Building and installing on Windows..."
ssh "$REMOTE" "/mnt/c/Windows/System32/WindowsPowerShell/v1.0/powershell.exe -NoProfile -ExecutionPolicy Bypass -File '${WIN_SRC}\\install.ps1' -LaunchDesktop"
