#!/usr/bin/env bash
set -euo pipefail
: "${WALKBUDDY_SIGNING_KEYSTORE:?Expected signing keystore must be specified}"
: "${ANDROID_HOME:?Android SDK path must be specified}"
apk="${1:?APK path is required}"
expected=$(keytool -exportcert -keystore "$WALKBUDDY_SIGNING_KEYSTORE" -storepass android -alias androiddebugkey | sha256sum | awk '{print $1}')
report=$("${ANDROID_HOME}/build-tools/36.0.0/apksigner" verify --print-certs "$apk")
actual=$(printf '%s\n' "$report" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')
if [[ "$actual" != "$expected" ]]; then
  echo "::error::APK signing certificate does not match the restored fixed key. Refusing to publish."
  exit 1
fi
printf 'Verified APK signing certificate SHA-256: %s\n' "$actual"
