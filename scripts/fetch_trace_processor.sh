#!/usr/bin/env bash
set -euo pipefail
destination="$1"
mkdir -p "$destination"
curl -fL --retry 2 --connect-timeout 20 --max-time 180 \
  https://github.com/google/perfetto/releases/download/v59.1/linux-amd64.zip \
  -o "$destination/linux-amd64.zip"
python3 - "$destination" <<'PY'
import hashlib
from pathlib import Path
import sys
import zipfile
directory = Path(sys.argv[1])
archive = directory / 'linux-amd64.zip'
expected = '7deb8c2572c6ae7ec80cd719d388041079827b0c44a1659694d5d88faa4f501e'
if hashlib.sha256(archive.read_bytes()).hexdigest() != expected:
    raise SystemExit('Official Perfetto v59.1 archive SHA-256 mismatch')
with zipfile.ZipFile(archive) as source:
    binary = directory / 'trace_processor_shell'
    binary.write_bytes(source.read('linux-amd64/trace_processor_shell'))
binary.chmod(0o755)
PY
"$destination/trace_processor_shell" --version
