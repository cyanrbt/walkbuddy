#!/usr/bin/env python3
"""Record bounded runner capacity and verify the disposable emulator's topology."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess


def host_profile(requested, allowed, quota):
    if requested not in (2, 4) or not allowed:
        raise ValueError('CPU profile requires 2 or 4 requested cores and nonempty host affinity')
    limits = [requested, len(allowed)]
    if quota is not None:
        budget, period = quota.split()
        if int(period) <= 0:
            raise ValueError('Invalid cgroup CPU period')
        if budget != 'max':
            if int(budget) <= 0:
                raise ValueError('Invalid cgroup CPU budget')
            limits.append(max(1, int(budget)//int(period)))
    return {'requested_cores': requested, 'allowed_cpu_ids': sorted(allowed),
            'cgroup_cpu_max': quota, 'configured_cores': min(limits),
            'scope': 'Runner capacity and emulator topology; not device throughput'}


def guest_profile(serial, expected):
    def adb(*args):
        return subprocess.check_output(['adb', '-s', serial, 'shell', *args], text=True, timeout=10).strip()
    if adb('getprop', 'ro.kernel.qemu') != '1':
        raise ValueError('CPU profiling requires the disposable emulator')
    online = adb('cat', '/sys/devices/system/cpu/online')
    ids = set()
    for part in online.split(','):
        if not re.fullmatch(r'\d+(?:-\d+)?', part):
            raise ValueError('Malformed emulator online CPU list')
        ends = [int(n) for n in part.split('-')]
        first, last = ends[0], ends[-1]
        if not 0 <= first <= last < 256:
            raise ValueError('Invalid emulator CPU range')
        ids.update(range(first, last+1))
    return {'expected_cores': expected, 'online': online, 'online_cpu_ids': sorted(ids),
            'valid': len(ids) == expected, 'scope': 'Disposable emulator topology only'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('host', 'guest'))
    parser.add_argument('--cores', type=int, required=True)
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--serial', default='emulator-5554')
    args = parser.parse_args()
    if not 1 <= args.cores <= 4:
        parser.error('cores must be between 1 and 4')
    if args.mode == 'host':
        quota_file = Path('/sys/fs/cgroup/cpu.max')
        profile = host_profile(args.cores, os.sched_getaffinity(0),
                               quota_file.read_text().strip() if quota_file.is_file() else None)
        profile['source_commit'] = os.environ.get('GITHUB_SHA')
    else:
        profile = guest_profile(args.serial, args.cores)
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(profile, indent=2)+'\n')
    print(json.dumps(profile), flush=True)
    if args.mode == 'host' and os.environ.get('GITHUB_OUTPUT'):
        with Path(os.environ['GITHUB_OUTPUT']).open('a') as output:
            output.write(f"cores={profile['configured_cores']}\n")
    return int(profile.get('valid') is False)


if __name__ == '__main__':
    raise SystemExit(main())
