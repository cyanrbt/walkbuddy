#!/usr/bin/env python3
"""Decode trace evidence and separate forced faults from unforced deadline observations."""
import argparse
from collections import Counter
import csv
import hashlib
import io
import json
from pathlib import Path
import re
import subprocess


ROOT = Path(__file__).resolve().parent


def overlap(start, duration, other_start, other_duration):
    return max(0, min(start+duration, other_start+other_duration)-max(start, other_start))


def summarize(rows, capture):
    errors = list(capture.get('errors', []))
    if not capture.get('flushed'):
        errors.append('Trace process exit/flush was not confirmed')
    if not capture.get('probe_pass'):
        errors.append('Traced recovery probe did not pass')
    threads = [r for r in rows if r['kind'] == 'thread']
    main_ids = {int(r['utid']) for r in threads if r['name'] == 'main'}
    render_ids = {int(r['utid']) for r in threads if r['name'] == 'RenderThread'}
    slices = [r for r in rows if r['kind'] == 'slice']
    dispatch = [r for r in slices if r['name'] == 'wb_main:dispatch' and int(r['utid']) in main_ids]
    forced = [r for r in slices if r['name'] == 'wb_test:forced_expiry' and int(r['dur']) >= 0]
    cpu = [r for r in rows if r['kind'] == 'cpu']
    states = [r for r in rows if r['kind'] == 'state']
    stats = [r for r in rows if r['kind'] == 'stat']
    metrics = {r['name']: int(r['value']) for r in rows if r['kind'] == 'metric'}
    if not metrics.get('sched_rows', 0):
        errors.append('No scheduler events decoded')
    for ids, label in ((main_ids, 'main'), (render_ids, 'RenderThread')):
        if not ids or not any(int(r['utid']) in ids for r in cpu):
            errors.append(f'No application {label} CPU scheduling decoded')
        if not any(int(r['utid']) in ids for r in states):
            errors.append(f'No application {label} thread states decoded')
    if not dispatch:
        errors.append('No test-only main dispatch slices decoded')
    if any(int(r['dur']) < 0 for r in dispatch):
        errors.append('Incomplete main dispatch slice')
    if len(forced) != 4:
        errors.append(f'Expected four forced expiry markers, got {len(forced)}')
    overwrite_stats = {'traced_buf_bytes_overwritten', 'traced_buf_chunks_overwritten', 'traced_buf_chunks_discarded'}
    loss = [r for r in stats if (r['detail'].split('/')[0] in ('data_loss', 'error')
                                or r['name'] in overwrite_stats) and int(r['value']) > 0]
    if loss:
        errors.append('Trace parser reports data loss or errors')
    bounds = [(int(r['ts']), int(r['ts'])+int(r['dur'])) for r in dispatch if int(r['dur']) >= 0]
    start = min((r[0] for r in bounds), default=0)
    end = max((r[1] for r in bounds), default=0)
    stops, outside = [], []
    for row in rows:
        if row['kind'] != 'log' or 'Preview stopped reason=' not in row['detail']:
            continue
        message, at = row['detail'], int(row['ts'])
        if not start <= at <= end:
            outside.append({'ts': at, 'message': message})
            continue  # Preserve unscoped logs; do not classify them as natural or forced.
        fields = dict(re.findall(r'(\w+)=([^\s]+)', message))
        controlled = any(int(s['ts']) <= at <= int(s['ts'])+int(s['dur']) for s in forced)
        gaps = [int(fields.get(k, -1)) for k in ('captureGapMs', 'displayGapMs')]
        if controlled:
            category = 'forced_expiry'
        elif fields.get('reason') in ('LOCKED', 'HIDDEN'):
            category = 'lifecycle'
        elif fields.get('reason') == 'GENERAL' and max(gaps) > 1200:
            category = 'unforced_over_deadline'
        else:
            category = 'other_stop'
        stops.append({'ts': at, 'category': category, 'fields': fields, 'message': message})
    if sum(s['category'] == 'forced_expiry' for s in stops) != 4:
        errors.append('Four forced expiry stop logs did not align with their trace markers')
    long_dispatch = []
    for row in sorted(dispatch, key=lambda r: int(r['dur']), reverse=True)[:20]:
        at, duration, utid = (int(row[k]) for k in ('ts', 'dur', 'utid'))
        if duration < 20000000:
            continue
        state_ns = Counter()
        for state in states:
            if int(state['utid']) == utid:
                state_ns[state['name']] += overlap(at, duration, int(state['ts']), int(state['dur']))
        main_cpu = sum(overlap(at, duration, int(s['ts']), int(s['dur'])) for s in cpu if int(s['utid']) == utid)
        render_cpu = sum(overlap(at, duration, int(s['ts']), int(s['dur'])) for s in cpu if int(s['utid']) in render_ids)
        render_slices = sorted(({'name': s['name'], 'overlap_ms': overlap(at, duration, int(s['ts']), int(s['dur']))/1e6}
                                for s in slices if int(s['utid']) in render_ids and int(s['dur']) >= 0),
                               key=lambda s: s['overlap_ms'], reverse=True)
        long_dispatch.append({'ts': at, 'wall_ms': duration/1e6, 'main_cpu_ms': main_cpu/1e6,
                              'main_state_ms': {k: v/1e6 for k, v in state_ns.items() if v},
                              'render_cpu_overlap_ms': render_cpu/1e6,
                              'render_slices': [s for s in render_slices[:5] if s['overlap_ms'] > 0]})
    return {'valid': not errors, 'errors': errors, 'metrics': metrics, 'stats': stats,
            'main_dispatch_count': len(dispatch), 'forced_expiry_markers': len(forced),
            'outside_observer_stops': outside,
            'stops': stops, 'stop_counts': dict(Counter(s['category'] for s in stops)),
            'longest_dispatches': long_dispatch,
            'scope': 'Traced emulator observations with main-looper logging overhead; overlap is not causation'}


def analyze(directory, processor):
    capture = json.loads((directory/'capture.json').read_text())
    trace = directory/'trace.pftrace'
    if not trace.is_file() or not trace.stat().st_size:
        raise ValueError('Missing or empty trace')
    if hashlib.sha256(trace.read_bytes()).hexdigest() != capture.get('trace_sha256'):
        raise ValueError('Trace SHA-256 differs from captured bytes')
    decoded = subprocess.run([processor, 'query', '--format', 'csv', '--no-progress',
                              '-f', str(ROOT/'preview_trace.sql'), str(trace)],
                             text=True, capture_output=True, timeout=120)
    (directory/'decode.log').write_text(decoded.stderr)
    (directory/'events.csv').write_text(decoded.stdout)
    if decoded.returncode:
        raise ValueError(f'Trace processor failed: {decoded.returncode}: {decoded.stderr[-2000:]}')
    rows = list(csv.DictReader(io.StringIO(decoded.stdout)))
    report = summarize(rows, capture)
    report['trace_sha256'] = capture['trace_sha256']
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('--processor', required=True)
    args = parser.parse_args()
    args.directory.mkdir(parents=True, exist_ok=True)
    version = subprocess.run([args.processor, '--version'], capture_output=True, text=True, check=True, timeout=20)
    reports = []
    for index in range(1, 4):
        directory = args.directory/f'trial-{index}'
        try:
            report = analyze(directory, args.processor)
        except (OSError, ValueError, KeyError, subprocess.TimeoutExpired) as error:
            report = {'valid': False, 'errors': [str(error)]}
        reports.append({'trial': index, **report})
    result = {'valid': all(r['valid'] for r in reports), 'processor': version.stdout.strip(),
              'processor_sha256': hashlib.sha256(Path(args.processor).read_bytes()).hexdigest(), 'trials': reports}
    (args.directory/'summary.json').write_text(json.dumps(result, indent=2)+'\n')
    lines = ['## Preview system trace', '', 'Diagnostic emulator observations with tracing overhead; not device acceptance.', '',
             '| Trial | Evidence | Dispatches | Forced stops | Unforced stops over 1200ms |', '| --- | --- | ---: | ---: | ---: |']
    for report in reports:
        counts = report.get('stop_counts', {})
        lines.append(f"| {report['trial']} | {'complete' if report['valid'] else 'INCOMPLETE / FAILED'} | "
                     f"{report.get('main_dispatch_count', 0)} | {counts.get('forced_expiry', 0)} | {counts.get('unforced_over_deadline', 0)} |")
    lines += ['', 'Raw traces, decoded events, parser statistics and probe results are retained in this artifact.',
              'Successful trials do not establish the cause or resolution of earlier intermittent stalls.', '']
    for report in reports:
        for error in report['errors']:
            lines.append(f"- Trial {report['trial']}: {error}")
    (args.directory/'summary.md').write_text('\n'.join(lines)+'\n')
    print('\n'.join(lines))
    return int(not result['valid'])


if __name__ == '__main__':
    raise SystemExit(main())
