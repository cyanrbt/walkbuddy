#!/bin/sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
fixture="$project_dir/app/src/androidTest/assets/portrait.jpg"
mkdir -p "$(dirname "$fixture")"
if ! curl -fL --retry 2 --max-time 120 'https://storage.googleapis.com/mediapipe-assets/portrait.jpg' -o "$fixture"; then
  curl -fL --retry 2 --max-time 120 'https://mediapipe-assets.storage.googleapis.com/portrait.jpg' -o "$fixture"
fi
printf '%s  %s\n' 'a6f11efaa834706db23f275b6115058fa87fc7f14362681e6abe14e82749de3e' "$fixture" | shasum -a 256 -c -
