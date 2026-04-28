#!/usr/bin/env bash
# Capture recent Android log lines so Cursor/AI can read logs/android-logcat-latest.txt
# (no need to copy-paste from Android Studio Logcat).
#
# Usage (phone connected via USB, USB debugging on):
#   cd SampleNode && bash scripts/android-capture-logcat.sh
#
# Live sign-in trace (recommended while reproducing):
#   adb logcat -s BalajiSevak:I BalajiSevak:E BalajiSevakOkHttp:I
#
# Then in Cursor: "read logs/android-logcat-latest.txt" or attach the file.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/logs/android-logcat-latest.txt"
mkdir -p "$ROOT/logs"

# Prefer explicit device: export ANDROID_SERIAL=... or pass as $1
ADB=(adb)
if [[ -n "${ANDROID_SERIAL:-}" ]]; then
  ADB=(adb -s "$ANDROID_SERIAL")
elif [[ -n "${1:-}" ]]; then
  ADB=(adb -s "$1")
fi

{
  echo "=== $(date -u +%Y-%m-%dT%H:%M:%SZ) UTC ==="
  echo "=== adb devices ==="
  adb devices -l 2>&1 || true
  echo ""
  echo "=== Lines: BalajiSevak [AUTH] / OkHttp / AndroidRuntime / FATAL ==="
  "${ADB[@]}" logcat -d -t 20000 2>&1 | grep -E 'BalajiSevak|BalajiSevakOkHttp|AndroidRuntime|FATAL EXCEPTION|com\.bolguru\.balajisevak|\[AUTH\]' | tail -n 1500 || true
} > "$OUT"

echo "Wrote: $OUT"
echo "Lines: $(wc -l < "$OUT")"
echo "Open that file here or say: check logs/android-logcat-latest.txt"
