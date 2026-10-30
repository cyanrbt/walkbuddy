"""Reproduce the v0.4.25 cooldown trace using an installed JDK; no Android SDK required."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--output', type=Path, help='Write JSON here; otherwise print it')
args = parser.parse_args()
root = Path(__file__).resolve().parents[2]
java_home = os.environ.get('JAVA_HOME')
javac = str(Path(java_home) / 'bin/javac') if java_home else shutil.which('javac')
java = str(Path(java_home) / 'bin/java') if java_home else shutil.which('java')
if not javac or not java:
    parser.error('A JDK is required; set JAVA_HOME or put java/javac on PATH')
files = ['Detection.java', 'ObstacleMask.java', 'DepthSnapshot.java', 'AlertPolicy.java', 'DetectionFeedback.java']
variants = {}
with tempfile.TemporaryDirectory(prefix='walkbuddy-feedback-') as temp:
    for variant in ('baseline', 'fixed'):
        out = Path(temp) / variant
        src = out / 'src/com/cyanrbt/walkbuddy'
        src.mkdir(parents=True)
        hashes = {}
        for name in files:
            relative = 'app/src/main/java/com/cyanrbt/walkbuddy/' + name
            data = subprocess.check_output(['git', 'show', 'v0.4.25:' + relative], cwd=root) if variant == 'baseline' else (root / relative).read_bytes()
            (src / name).write_bytes(data)
            hashes[name] = hashlib.sha256(data).hexdigest()
        shutil.copyfile(Path(__file__).with_name('FeedbackTrace.java'), src / 'FeedbackTrace.java')
        subprocess.run([javac, '-encoding', 'UTF-8', '-d', str(out / 'classes'), *map(str, src.glob('*.java'))], check=True)
        raw = subprocess.check_output([java, '-cp', str(out / 'classes'), 'com.cyanrbt.walkbuddy.FeedbackTrace'], text=True)
        rows = []
        for line in raw.splitlines():
            scenario, at, source, notify, message = line.split('\t')
            rows.append(dict(scenario=scenario, at_ms=int(at), source=source, notify=notify == 'true', current_message=message))
        first = {key: next(row['at_ms'] for row in rows if row['scenario'] == key and row['notify'] and row['source'] == source)
                 for key, source in [('near', 'depth'), ('caution', 'basic')]}
        expected = {'near': 4900, 'caution': 8700} if variant == 'baseline' else {'near': 1600, 'caution': 3400}
        if first != expected:
            raise RuntimeError(f'{variant}: expected {expected}, observed {first}')
        variants[variant] = dict(source_sha256=hashes, first_eligible_source_notifications_ms=first, rows=rows)
messages = lambda rows: [{key: value for key, value in row.items() if key != 'notify'} for row in rows]
if messages(variants['baseline']['rows']) != messages(variants['fixed']['rows']):
    raise RuntimeError('Current messages changed between the two traces')
result = dict(scope='Constructed JVM timestamps/results; app notification eligibility, not physical delivery or AR latency',
              cadence_ms=100, steps_per_variant=len(variants['fixed']['rows']), current_messages_match_at_every_step=True, variants=variants)
output = json.dumps(result, ensure_ascii=False, indent=2) + '\n'
if args.output:
    args.output.write_text(output)
else:
    print(output, end='')
