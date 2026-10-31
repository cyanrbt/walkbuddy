#!/usr/bin/env bash
source "$(dirname "$0")/ui_probe_setup.sh"
failed=0
if ! python3 scripts/record_preview_trace.py ui-evidence/preview-trace --serial "$serial"; then failed=1; fi
if ! python3 scripts/report_preview_traces.py ui-evidence/preview-trace --processor "$WALKBUDDY_TRACE_PROCESSOR"; then failed=1; fi
# Run every standard gate too, without tracing or the main-looper observer.
if ! bash scripts/run_ui_probes.sh; then failed=1; fi
exit "$failed"
