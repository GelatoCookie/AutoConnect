#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
GRADLEW="$ROOT_DIR/gradlew"
APK_PATH="$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk"
APP_ID="com.zebra.rfid.demo.sdksample"
LAUNCH_ACTIVITY="com.zebra.rfid.demo.sdksample.MainActivity"

usage() {
  cat <<'EOF'
Usage: ./android_tasks.sh <command>

Commands:
  clean    Run Gradle clean
  build    Build debug APK (assembleDebug)
  deploy   Install debug APK on connected Android device/emulator
  run      Launch app on connected Android device/emulator
  connected-test  Run connected Android instrumentation tests
  all      clean + build + deploy + run
EOF
}

require_tool() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Error: required tool '$1' is not installed or not in PATH."
    exit 1
  fi
}

require_gradle_wrapper() {
  if [[ ! -x "$GRADLEW" ]]; then
    echo "Error: gradle wrapper not executable at: $GRADLEW"
    echo "Try: chmod +x ./gradlew"
    exit 1
  fi
}

require_adb_device() {
  require_tool adb
  if ! adb get-state >/dev/null 2>&1; then
    echo "Error: no adb device detected. Connect device or start emulator."
    exit 1
  fi
}

do_clean() {
  require_gradle_wrapper
  "$GRADLEW" clean
}

do_build() {
  require_gradle_wrapper
  "$GRADLEW" assembleDebug
}

do_deploy() {
  require_adb_device
  if [[ ! -f "$APK_PATH" ]]; then
    echo "Debug APK not found at: $APK_PATH"
    echo "Building debug APK first..."
    do_build
  fi
  adb install -r -d -t "$APK_PATH"
}

do_run() {
  require_adb_device
  adb shell am start -n "$APP_ID/$LAUNCH_ACTIVITY"
}

do_connected_test() {
  require_gradle_wrapper
  require_adb_device
  "$GRADLEW" :app:connectedDebugAndroidTest --console=plain
}

main() {
  if [[ $# -ne 1 ]]; then
    usage
    exit 1
  fi

  case "$1" in
    clean)
      do_clean
      ;;
    build)
      do_build
      ;;
    deploy)
      do_deploy
      ;;
    run)
      do_run
      ;;
    connected-test)
      do_connected_test
      ;;
    all)
      do_clean
      do_build
      do_deploy
      do_run
      ;;
    *)
      usage
      exit 1
      ;;
  esac
}

main "$@"
