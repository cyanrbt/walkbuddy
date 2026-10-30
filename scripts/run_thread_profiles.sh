#!/usr/bin/env bash
source "$(dirname "$0")/ui_probe_setup.sh"
failed=0
if ! adb -s "$serial" shell am instrument -w -e thread_parity true com.cyanrbt.walkbuddy.test/com.cyanrbt.walkbuddy.RuntimeProbe > ui-evidence/thread-parity.txt; then failed=1; fi
cat ui-evidence/thread-parity.txt
index=0
# Same emulator and priority; fresh instrumentation per trial, A-B-B-A thread counts.
for threads in 2 1 1 2; do
  index=$((index+1))
  prefix="ui-evidence/threads-${index}-count-${threads}"
  adb -s "$serial" logcat -c
  if ! adb -s "$serial" shell am instrument -w -e pip true -e profile_priority 1 -e profile_threads "$threads" com.cyanrbt.walkbuddy.test/com.cyanrbt.walkbuddy.RuntimeProbe > "${prefix}.txt"; then failed=1; fi
  cat "${prefix}.txt"
  adb -s "$serial" logcat -d -b crash > "${prefix}-crash.txt"
  adb -s "$serial" logcat -d -s WalkBuddyAnalysis:I WalkBuddyTiming:W WalkBuddyCamera:W WalkBuddyPip:I '*:S' > "${prefix}-timing.txt"
  files="${prefix}-files"
  mkdir -p "$files"
  adb -s "$serial" exec-out run-as com.cyanrbt.walkbuddy tar -cf - cache/ui-probe | tar -xf - -C "$files" || true
done
if ! python3 scripts/report_thread_profiles.py ui-evidence; then failed=1; fi
# Failures stay visible; every trial runs so failed settings are not silently filtered.
exit "$failed"
