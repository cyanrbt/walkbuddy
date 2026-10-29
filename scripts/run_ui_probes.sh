#!/usr/bin/env bash
set -euo pipefail
serial="${WALKBUDDY_EMULATOR_SERIAL:-emulator-5554}"
mkdir -p ui-evidence
collect_evidence() {
  adb -s "$serial" logcat -d -s WalkBuddyCamera:W '*:S' > ui-evidence/camera-timing.txt || true
  adb -s "$serial" logcat -d -b crash > ui-evidence/crash.txt || true
  adb -s "$serial" logcat -d -s WalkBuddyAnalysis:I '*:S' > ui-evidence/analysis-timing.txt || true
  adb -s "$serial" logcat -d -s ActivityManager:I ActivityTaskManager:I PowerManagerService:I lmkd:I AndroidRuntime:E libc:F DEBUG:F '*:S' > ui-evidence/lifecycle.txt || true
  adb -s "$serial" exec-out run-as com.cyanrbt.walkbuddy tar -cf - cache/ui-probe | tar -xf - -C ui-evidence || true
}
trap collect_evidence EXIT
adb -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
for evidence in runtime overlay layout recovery pip pip-repeat; do
  probe="${evidence%%-*}"
  if [[ "$probe" == recovery ]]; then
    # Revocation can kill the app process: do it before, never inside live instrumentation.
    adb -s "$serial" shell pm revoke com.cyanrbt.walkbuddy android.permission.CAMERA
  fi
  adb -s "$serial" shell am instrument -w -e "$probe" true com.cyanrbt.walkbuddy.test/com.cyanrbt.walkbuddy.RuntimeProbe > "ui-evidence/${evidence}.txt"
  cat "ui-evidence/${evidence}.txt"
  python3 - "$probe" "$evidence" <<'PY'
from pathlib import Path
import sys
probe, evidence = sys.argv[1:]
result = Path(f"ui-evidence/{evidence}.txt").read_text()
if f"{probe.upper()}_PROBE_PASS" not in result:
    raise SystemExit(f"Android {probe} probe did not pass")
PY
done
