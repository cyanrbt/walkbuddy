#!/usr/bin/env python3
"""Reject a tag/version mismatch or missing versioned release notes before publishing."""
from pathlib import Path
import re
import sys

root = Path(__file__).resolve().parents[1]

def main():
    if len(sys.argv) != 2 or not re.fullmatch(r"v\d+\.\d+\.\d+", sys.argv[1]):
        raise ValueError("Expected a release tag such as v0.4.5")
    tag = sys.argv[1]
    gradle = (root / "app/build.gradle").read_text(encoding="utf-8")
    version = re.search(r"\bversionName\s+['\"]([^'\"]+)['\"]", gradle)
    code = re.search(r"\bversionCode\s+(\d+)\b", gradle)
    if version is None or code is None or int(code[1]) <= 0:
        raise ValueError("Missing or invalid Android version metadata")
    if tag != "v" + version[1]:
        raise ValueError(f"Tag {tag} does not match Android version {version[1]}")
    notes = root / "docs/releases" / f"{tag}.md"
    if not notes.is_file() or not notes.read_text(encoding="utf-8").strip():
        raise ValueError(f"Missing release notes: docs/releases/{tag}.md")
    print(f"Release metadata: {tag}, versionCode {code[1]}, notes {notes.relative_to(root)}")

if __name__ == "__main__":
    try:
        main()
    except ValueError as error:
        print(f"Release check failed: {error}", file=sys.stderr)
        sys.exit(1)
