#!/usr/bin/env python3
"""Bounded Perfetto captures on the explicitly selected disposable emulator only."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time


ROOT = Path(__file__).resolve().parent
COMPONENT = 'com.cyanrbt.walkbuddy.test/com.cyanrbt.walkbuddy.RuntimeProbe'


class Adb:
    def __init__(self, serial):
        self.serial = serial

    def run(self, *args, timeout=20, input=None, check=True):
        result = subprocess.run(['adb', '-s', self.serial, *args], input=input,
                                text=True, capture_output=True, timeout=timeout)
        if check and result.returncode:
            raise RuntimeError(f'adb {args!r}: {result.returncode}: stdout={result.stdout.strip()}; stderr={result.stderr.strip()}')
        return result


def stop_trace(adb, pid, timeout=20):
    """Wait for process exit, not just signal delivery, before pulling the trace."""
    if not re.fullmatch(r'[1-9][0-9]*', str(pid)):
        raise ValueError('Invalid owned Perfetto PID')
    if adb.run('shell', 'test', '-d', f'/proc/{pid}', check=False).returncode:
        raise RuntimeError('Perfetto ended before the probe was stopped')
    adb.run('shell', 'kill', '-INT', str(pid))
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if adb.run('shell', 'test', '-d', f'/proc/{pid}', check=False).returncode:
            return
        time.sleep(.1)
    raise TimeoutError('Perfetto still alive; trace flush is not confirmed')


def pull_trace(adb, remote, directory):
    """Retry copying the same stopped trace, never rerun or replace the probe."""
    expected_bytes = int(adb.run('shell', 'stat', '-c', '%s', remote).stdout.strip())
    hash_fields = adb.run('shell', 'sha256sum', remote).stdout.split()
    expected_hash = hash_fields[0] if hash_fields else ''
    if expected_bytes <= 0 or not re.fullmatch(r'[0-9a-f]{64}', expected_hash):
        raise RuntimeError('Stopped remote trace has no valid size/SHA-256')
    part, final = directory/'trace.partial', directory/'trace.pftrace'
    attempts = []
    for index in (1, 2):
        part.unlink(missing_ok=True)
        attempt = {'attempt': index, 'verified': False}
        try:
            if index > 1:
                # A transport can briefly go offline after the first copy fails.
                # Reconnect to the same emulator and copy the same stopped file.
                ready = adb.run('wait-for-device', timeout=10, check=False)
                (directory/f'reconnect-{index}.txt').write_text(ready.stdout+ready.stderr)
                attempt['reconnect_exit'] = ready.returncode
                if ready.returncode:
                    raise RuntimeError(f'Emulator transport did not reconnect: {ready.stdout.strip()}; {ready.stderr.strip()}')
            result = adb.run('pull', remote, str(part), timeout=40, check=False)
            (directory/f'pull-{index}.txt').write_text(result.stdout+result.stderr)
            attempt['exit'] = result.returncode
            attempt['bytes'] = part.stat().st_size if part.is_file() else 0
            attempt['sha256'] = hashlib.sha256(part.read_bytes()).hexdigest() if part.is_file() else None
            attempt['verified'] = result.returncode == 0 and attempt['bytes'] == expected_bytes \
                and attempt['sha256'] == expected_hash
            if not attempt['verified']:
                attempt['error'] = f'Transfer incomplete or mismatched: stdout={result.stdout.strip()}; stderr={result.stderr.strip()}'
        except (OSError, RuntimeError, subprocess.TimeoutExpired) as error:
            attempt['error'] = str(error)
        attempts.append(attempt)
        (directory/'transfer.json').write_text(json.dumps({'remote_bytes': expected_bytes,
            'remote_sha256': expected_hash, 'attempts': attempts}, indent=2)+'\n')
        if attempt['verified']:
            part.replace(final)
            return {'trace_bytes': expected_bytes, 'trace_sha256': expected_hash, 'transfer_attempts': attempts}
    raise RuntimeError('Both copies of the same stopped trace failed; see transfer.json and pull logs')


def capture(adb, directory, config, index):
    directory.mkdir(parents=True, exist_ok=True)
    remote = f'/data/misc/perfetto-traces/walkbuddy-preview-{index}.pftrace'
    record = {'trial': index, 'flushed': False, 'probe_pass': False, 'errors': []}
    pid = None
    try:
        try:
            state = adb.run('shell', 'perfetto', '--query', timeout=10, check=False)
            (directory/'service-before.txt').write_text(state.stdout+state.stderr)
            record['service_query_exit'] = state.returncode
        except (RuntimeError, subprocess.TimeoutExpired) as query_error:
            record['service_query_error'] = str(query_error)
        start = adb.run('shell', 'perfetto', '--txt', '-c', '-', '-o', remote,
                        '--background-wait', input=config, timeout=40, check=False)
        (directory/'start.txt').write_text(start.stdout + start.stderr)
        if re.fullmatch(r'[1-9][0-9]*', start.stdout.strip()):
            pid = start.stdout.strip()
            record['perfetto_pid'] = int(pid)
        if start.returncode or pid is None:
            raise RuntimeError(f'Perfetto did not confirm a PID: {start.returncode}: {start.stdout.strip()}; {start.stderr.strip()}')
        result = adb.run('shell', 'am', 'instrument', '-w', '-e', 'pip_recovery', 'true',
                         '-e', 'preview_trace', 'true', COMPONENT, timeout=110, check=False)
        (directory/'instrumentation.txt').write_text(result.stdout + result.stderr)
        record['instrumentation_exit'] = result.returncode
        record['probe_pass'] = result.returncode == 0 and 'PIP_RECOVERY_PROBE_PASS' in result.stdout \
            and 'PIP_RECOVERY_PROBE_FAIL' not in result.stdout
        if not record['probe_pass']:
            record['errors'].append('Recovery instrumentation did not pass')
    except (RuntimeError, subprocess.TimeoutExpired) as error:
        record['errors'].append(str(error))
        if isinstance(error, subprocess.TimeoutExpired):
            (directory/'timeout.txt').write_text(str(error))
            output = error.stdout or b''
            (directory/'partial-instrumentation.txt').write_text(output.decode(errors='replace') if isinstance(output, bytes) else output)
            # The adb client timeout does not stop remote instrumentation.
            try:
                adb.run('shell', 'am', 'force-stop', 'com.cyanrbt.walkbuddy')
            except (RuntimeError, subprocess.TimeoutExpired) as cleanup:
                record['errors'].append(str(cleanup))
    finally:
        if pid is not None:
            try:
                stop_trace(adb, pid)
                record['flushed'] = True
            except (RuntimeError, TimeoutError, subprocess.TimeoutExpired) as error:
                record['errors'].append(str(error))
            try:
                record.update(pull_trace(adb, remote, directory))
            except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired) as error:
                record['errors'].append(str(error))
        try:
            logs = adb.run('logcat', '-d', '-s', 'WalkBuddyTiming:I', 'WalkBuddyPip:I',
                           'WalkBuddyCamera:W', '*:S')
            (directory/'logcat.txt').write_text(logs.stdout)
        except (RuntimeError, subprocess.TimeoutExpired) as error:
            record['errors'].append(str(error))
        (directory/'capture.json').write_text(json.dumps(record, indent=2)+'\n')
    return record


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('directory', type=Path)
    parser.add_argument('--serial', required=True)
    args = parser.parse_args()
    adb = Adb(args.serial)
    # Check before starting any trace or instrumentation. No adb root or global kill.
    if adb.run('shell', 'getprop', 'ro.kernel.qemu').stdout.strip() != '1':
        raise SystemExit('Preview tracing is restricted to the disposable emulator')
    args.directory.mkdir(parents=True, exist_ok=True)
    config = (ROOT/'preview_trace.pbtxt').read_text()
    metadata = {'source_commit': os.environ.get('GITHUB_SHA'), 'serial': args.serial,
                'config_sha256': hashlib.sha256(config.encode()).hexdigest(),
                'trials': [], 'scope': 'Traced emulator observations; observer overhead; no device/FPS/power claim'}
    for name in ('ro.build.fingerprint', 'ro.build.type', 'ro.build.version.sdk'):
        metadata[name] = adb.run('shell', 'getprop', name).stdout.strip()
    for index in range(1, 4):
        print(f'Recording preview recovery trial {index}/3', flush=True)
        result = capture(adb, args.directory/f'trial-{index}', config, index)
        metadata['trials'].append(result)
        print(json.dumps(result), flush=True)
        (args.directory/'capture.json').write_text(json.dumps(metadata, indent=2)+'\n')
    return int(any(t['errors'] or not t['flushed'] or not t['probe_pass'] for t in metadata['trials']))


if __name__ == '__main__':
    raise SystemExit(main())
