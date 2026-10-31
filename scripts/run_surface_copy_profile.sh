#!/usr/bin/env bash
set -euo pipefail
serial="${WALKBUDDY_EMULATOR_SERIAL:-emulator-5554}"
if [[ "$(adb -s "$serial" shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ]]; then
  echo 'Surface copy experiment requires the disposable emulator' >&2
  exit 1
fi
source "$(dirname "$0")/ui_probe_setup.sh"
failed=0
if ! adb -s "$serial" shell am instrument -w -e preview true -e surface_copy true \
  com.cyanrbt.walkbuddy.test/com.cyanrbt.walkbuddy.RuntimeProbe > ui-evidence/surface-copy.txt; then failed=1; fi
cat ui-evidence/surface-copy.txt
if ! python3 - <<'PY'
from pathlib import Path
record = Path('ui-evidence/surface-copy.txt').read_text()
if 'SURFACE_COPY_PARITY_PASS cases=32 pixels=6144' not in record or 'PREVIEW_PROBE_FAIL' in record:
    raise SystemExit('Experimental Surface copy pixel comparison did not pass')
PY
then failed=1; fi
api="$(adb -s "$serial" shell getprop ro.build.version.sdk | tr -d '\r')"
if [[ "$api" == 26 ]]; then
  echo 'API 26 scope: frozen Surface copy comparison; standard Android 15/16 probes not executed.'
else
  if ! bash scripts/run_ui_probes.sh; then failed=1; fi
fi
exit "$failed"
