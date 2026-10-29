#!/usr/bin/env bash
source "$(dirname "$0")/ui_probe_setup.sh"
failed=0
index=0
# A-B-B-A in the same emulator; a fresh instrumentation process for each trial.
for priority in 10 1 1 10; do
  index=$((index+1))
  output="ui-evidence/profile-${index}-priority-${priority}.txt"
  adb -s "$serial" logcat -c
  if ! adb -s "$serial" shell am instrument -w -e pip true -e profile_priority "$priority" com.cyanrbt.walkbuddy.test/com.cyanrbt.walkbuddy.RuntimeProbe > "$output"; then
    failed=1
  fi
  cat "$output"
  adb -s "$serial" logcat -d -s WalkBuddyAnalysis:I WalkBuddyTiming:W WalkBuddyCamera:W '*:S' > "ui-evidence/profile-${index}-priority-${priority}-timing.txt"
  if ! python3 - "$output" <<'CHECK'
from pathlib import Path
import sys
if "PIP_PROBE_PASS" not in Path(sys.argv[1]).read_text():
    raise SystemExit("Scheduling trial did not pass")
CHECK
  then failed=1; fi
  adb -s "$serial" exec-out run-as com.cyanrbt.walkbuddy tar -cf - cache/ui-probe | tar -xf - -C ui-evidence || true
done
# Every trial must pass for this diagnostic run to be green; failures remain visible.
exit "$failed"
