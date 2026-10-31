"""Reproduce this case's summary and SVG from its checked Perfetto CSV rows."""
import collections
import csv
import hashlib
import json
import os
from pathlib import Path

os.environ.setdefault('MPLCONFIGDIR', '/tmp/walkbuddy-matplotlib')
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt
from matplotlib.patches import Patch

root = Path(__file__).resolve().parent
events = root/'brightness-wait-stages-2026-10-31.csv'
cores = root/'brightness-wait-cpus-2026-10-31.csv'
rows = list(csv.DictReader(events.open()))
logs = [r for r in rows if r['kind'] == 'log']
start = int(next(r['ts'] for r in logs if 'event=RETURN ' in r['detail']))
end = int(next(r['ts'] for r in logs if 'event=CALLBACK ' in r['detail']))
duration = end-start


def clipped(row):
    return max(0, min(int(row['ts'])+int(row['dur']), end)-max(int(row['ts']), start))


threads = []
for tid, label in ((3638, 'Main'), (3683, 'RenderThread'), (3770, 'Brightness worker')):
    states = [r for r in rows if r['kind'] == 'state' and int(r['tid']) == tid]
    totals = collections.Counter()
    for state in states:
        totals[state['name']] += clipped(state)
    cpu = int(next(r['dur'] for r in rows if r['kind'] == 'cpu' and int(r['tid']) == tid))
    assert sum(totals.values()) == duration
    assert totals['Running'] == cpu
    threads.append({'tid': tid, 'label': label, 'states_ns': dict(totals), 'cpu_ns': cpu})

cpu_rows = list(csv.DictReader(cores.open()))
assert len(cpu_rows) == 2
for core in cpu_rows:
    assert int(core['observed_ns']) == duration
    assert int(core['idle_ns']) == int(core['unattributed_ns']) == 0
copies = [r for r in rows if r['kind'] == 'slice' and r['name'] == 'copySurfaceInto']
assert len(copies) == 1
copy = copies[0]
summary = {
    'source_commit': '1b5217706b5a0fd0d7ee755ed7cb6d0cd7eaeff3',
    'run': 41178081292, 'job': 124469681763, 'artifact': 13034166185,
    'trace_sha256': 'c05660c8b13006979e83f1839f6091f4d552fe8d0daa12435c2187ed3b514cb3',
    'pid': 3638, 'request': 1, 'due_to_delivery_ms': 1134,
    'return_trace_ns': start, 'callback_trace_ns': end, 'window_ns': duration,
    'threads': threads, 'cores': cpu_rows,
    'observed_copy': {'ts': int(copy['ts']), 'dur': int(copy['dur']),
                      'starts_after_return_ns': int(copy['ts'])-start,
                      'association': 'Same process, one in-flight request, adjacent timing; no native request-ID flow'},
    'cpu_by_process': sorted(({'pid': int(r['tid']), 'name': r['thread'], 'cpu_ns': int(r['dur'])}
                              for r in rows if r['kind'] == 'process_cpu'), key=lambda r: r['cpu_ns'], reverse=True),
    'query_inputs': {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in (events, cores)},
    'scope': 'One traced two-core emulator sample; CPU contention observed; not the earlier expiry or device acceptance',
}
(root/'brightness-wait-summary-2026-10-31.json').write_text(json.dumps(summary, indent=2)+'\n')

plt.rcParams['svg.hashsalt'] = 'walkbuddy-brightness-wait-2026-10-31'
fig, ax = plt.subplots(figsize=(12.5, 4.4))
colors = {'Running': '#12887a', 'R': '#f0ad49', 'R+': '#f0ad49', 'S': '#d8e1eb'}
for index, thread in enumerate(threads):
    lane = 3-index
    for row in rows:
        if row['kind'] != 'state' or int(row['tid']) != thread['tid'] or not clipped(row):
            continue
        assert row['name'] in colors
        ax.broken_barh([((max(int(row['ts']), start)-start)/1e6, clipped(row)/1e6)],
                       (lane-.31, .62), facecolors=colors[row['name']], edgecolors='none')
copy_at = (int(copy['ts'])-start)/1e6
copy_ms = int(copy['dur'])/1e6
ax.broken_barh([(copy_at, copy_ms)], (-.27, .54), facecolors='#6960b8')
ax.annotate(f'copySurfaceInto: {copy_ms:.1f} ms', (copy_at+copy_ms/2, .3),
            xytext=(560, .55), arrowprops={'arrowstyle': '-', 'color': '#6960b8'}, fontsize=10)
ax.set_yticks([3, 2, 1, 0], ['Main', 'RenderThread', 'Brightness worker', 'Observed native readback'])
ax.set_ylim(-.65, 3.65)
ax.set_xlim(0, duration/1e6)
ax.set_xlabel('Milliseconds after PixelCopy API return; window ends at worker callback')
ax.set_title('Where the first brightness request waited', loc='left', fontsize=16, pad=35)
ax.text(0, 1.075, 'API 35 · two-core emulator · request 1 · full due-to-delivery 1134 ms',
        transform=ax.transAxes, color='#526176', fontsize=10)
ax.legend(handles=[Patch(color=colors['Running'], label='Running on CPU'),
                   Patch(color=colors['R'], label='Runnable / preempted'),
                   Patch(color=colors['S'], label='Sleeping')],
          loc='upper right', bbox_to_anchor=(1, 1.18), frameon=False, ncol=3, fontsize=9)
ax.set_axisbelow(True)
ax.grid(axis='x', color='#e7ebef')
for spine in ('top', 'right', 'left'):
    ax.spines[spine].set_visible(False)
fig.text(.015, .025, 'Observed trace slices and clipped scheduler states. Tracing overhead; not a phone/GPU performance benchmark.', fontsize=9, color='#526176')
fig.tight_layout(rect=(0, .055, 1, 1))
fig.savefig(root/'brightness-wait-2026-10-31.svg', metadata={'Date': None})
fig.savefig('/tmp/walkbuddy-brightness-wait-2026-10-31.png', dpi=145)
print(json.dumps({'window_ms': duration/1e6, 'copy_starts_after_return_ms': copy_at, 'copy_wall_ms': copy_ms,
                  'state_and_cpu_totals_verified': True, 'matplotlib': matplotlib.__version__}))
