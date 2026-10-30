#!/usr/bin/env bash
set -euo pipefail
serial="${WALKBUDDY_EMULATOR_SERIAL:-emulator-5554}"
mkdir -p ui-evidence
collect_evidence() {
  adb -s "$serial" logcat -d -s WalkBuddyCamera:W '*:S' > ui-evidence/camera-timing.txt || true
  adb -s "$serial" logcat -d -s WalkBuddyTiming:W '*:S' > ui-evidence/pipeline-timing.txt || true
  adb -s "$serial" logcat -d -s WalkBuddyPip:I WalkBuddyInput:I '*:S' > ui-evidence/pip-timing.txt || true
  adb -s "$serial" logcat -d -b crash > ui-evidence/crash.txt || true
  adb -s "$serial" logcat -d -s WalkBuddyAnalysis:I '*:S' > ui-evidence/analysis-timing.txt || true
  adb -s "$serial" logcat -d -s ActivityManager:I ActivityTaskManager:I PowerManagerService:I lmkd:I AndroidRuntime:E libc:F DEBUG:F '*:S' > ui-evidence/lifecycle.txt || true
  adb -s "$serial" exec-out run-as com.cyanrbt.walkbuddy tar -cf - cache/ui-probe | tar -xf - -C ui-evidence || true
}
trap collect_evidence EXIT
adb -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
