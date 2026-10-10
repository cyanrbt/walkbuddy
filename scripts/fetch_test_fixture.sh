#!/bin/sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
fixture="$project_dir/app/src/androidTest/assets/portrait.jpg"
mkdir -p "$(dirname "$fixture")"
curl -fL 'https://storage.googleapis.com/mediapipe-assets/portrait.jpg' -o "$fixture"
printf '%s  %s\n' 'a6f11efaa834706db23f275b6115058fa87fc7f14362681e6abe14e82749de3e' "$fixture" | shasum -a 256 -c -
