#!/usr/bin/env bash
set -euo pipefail
serial="${WALKBUDDY_EMULATOR_SERIAL:-emulator-5554}"
mkdir -p ui-evidence
collect_evidence() {
  adb -s "$serial" exec-out run-as com.cyanrbt.walkbuddy tar -cf - cache/ui-probe | tar -xf - -C ui-evidence || true
}
trap collect_evidence EXIT
adb -s "$serial" install -r app/build/outputs/apk/debug/app-debug.apk
adb -s "$serial" install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
for probe in overlay layout recovery; do
  if [[ "$probe" == recovery ]]; then
    # Revocation can kill the app process: do it before, never inside live instrumentation.
    adb -s "$serial" shell pm revoke com.cyanrbt.walkbuddy android.permission.CAMERA
  fi
  adb -s "$serial" shell am instrument -w -e "$probe" true com.cyanrbt.walkbuddy.test/com.cyanrbt.walkbuddy.RuntimeProbe > "ui-evidence/${probe}.txt"
  cat "ui-evidence/${probe}.txt"
  python3 - "$probe" <<'PY'
from pathlib import Path
import sys
probe = sys.argv[1]
result = Path(f"ui-evidence/{probe}.txt").read_text()
if f"{probe.upper()}_PROBE_PASS" not in result:
    raise SystemExit(f"Android {probe} probe did not pass")
PY
done
