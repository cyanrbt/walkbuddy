#!/usr/bin/env python3
"""Free build-only tools and caches after APK compilation on GitHub-hosted runners."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil


def fingerprint(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def prepare(workspace, sdk, gradle_home, avd_home, environment):
    if environment.get('GITHUB_ACTIONS') != 'true' or environment.get('RUNNER_ENVIRONMENT') != 'github-hosted':
        raise ValueError('Storage cleanup requires a disposable GitHub-hosted runner')
    workspace, sdk, gradle_home, avd_home = (Path(p).resolve() for p in (workspace, sdk, gradle_home, avd_home))
    if sdk in (workspace, Path('/')) or gradle_home in (workspace, Path('/')):
        raise ValueError('Invalid SDK or Gradle cache root')
    if not (sdk/'platform-tools/adb').is_file():
        raise ValueError('Expected Android SDK with platform-tools/adb')
    outputs = [workspace/'app/build/outputs/apk/debug/app-debug.apk',
               workspace/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk']
    if any(not path.is_file() or not path.stat().st_size for path in outputs):
        raise ValueError('Both compiled APKs must exist before removing build-time tools')
    hashes = {str(path.relative_to(workspace)): fingerprint(path) for path in outputs}
    result = {'scope': 'Disposable hosted runner after compilation; APKs, emulator, system images, SDK platform/build tools and keys retained',
              'free_bytes_before': shutil.disk_usage(avd_home).free, 'removed': [], 'skipped': [],
              'apk_sha256': hashes}
    candidates = [(sdk, sdk/'ndk'), (sdk, sdk/'cmake'),
                  (gradle_home, gradle_home/'caches'), (gradle_home, gradle_home/'wrapper/dists')]
    for base, path in candidates:
        if not path.exists() and not path.is_symlink():
            continue
        if path.is_symlink() or not path.resolve().is_relative_to(base) or not path.is_dir():
            result['skipped'].append(str(path))
            continue
        shutil.rmtree(path)
        result['removed'].append(str(path))
    for path in outputs:
        if fingerprint(path) != hashes[str(path.relative_to(workspace))]:
            raise ValueError('Compiled APK changed during storage preparation')
    result.update(status='passed', free_bytes_after=shutil.disk_usage(avd_home).free,
                  compiled_apks_unchanged=True)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('workspace', 'sdk', 'gradle-home', 'avd-home', 'report'):
        parser.add_argument('--'+name, type=Path, required=True)
    args = parser.parse_args()
    try:
        result = prepare(args.workspace, args.sdk, args.gradle_home, args.avd_home, os.environ)
    except (OSError, ValueError) as error:
        result = {'status': 'failed', 'error': str(error), 'scope': 'Hosted-runner storage preparation'}
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(result, indent=2)+'\n')
    print(json.dumps(result, indent=2))
    return int(result['status'] != 'passed')


if __name__ == '__main__':
    raise SystemExit(main())
