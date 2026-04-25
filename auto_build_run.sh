#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
TASK_SCRIPT="$ROOT_DIR/android_tasks.sh"

usage() {
  cat <<'EOF'
Usage: ./auto_build_run.sh [--clean]

Options:
  --clean   Run clean before build
  -h, --help  Show this help

Behavior:
  1) build debug APK
  2) deploy to connected device/emulator
  3) run app launcher activity
EOF
}

if [[ ! -x "$TASK_SCRIPT" ]]; then
  echo "Error: missing or non-executable script: $TASK_SCRIPT"
  echo "Run: chmod +x ./android_tasks.sh"
  exit 1
fi

DO_CLEAN=0
if [[ $# -gt 1 ]]; then
  usage
  exit 1
fi

if [[ $# -eq 1 ]]; then
  case "$1" in
    --clean)
      DO_CLEAN=1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage
      exit 1
      ;;
  esac
fi

if [[ $DO_CLEAN -eq 1 ]]; then
  "$TASK_SCRIPT" clean
fi

"$TASK_SCRIPT" build
"$TASK_SCRIPT" deploy
"$TASK_SCRIPT" run

echo "Auto build and run completed successfully."
