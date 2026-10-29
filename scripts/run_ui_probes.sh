#!/usr/bin/env bash
source "$(dirname "$0")/ui_probe_setup.sh"
for evidence in runtime preview overlay layout home recovery pip pip-repeat; do
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
