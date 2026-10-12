#!/usr/bin/env bash
# CI release signing must be stable across tags. Never generate a replacement key.
set -euo pipefail

if [[ -z "${DEBUG_KEYSTORE_BASE64:-}" ]]; then
  echo "::error::Missing DEBUG_KEYSTORE_BASE64. Restore the existing signing key into the repository Actions secret before publishing. Refusing to generate a new key that would break upgrades."
  exit 1
fi

umask 077
signing_dir="${SIGNING_KEYSTORE_DIR:-${HOME}/.android}"
mkdir -p "$signing_dir"
signing_file=$(mktemp "$signing_dir/walkbuddy-signing.XXXXXX")
trap 'rm -f "$signing_file"' EXIT

if ! printf '%s' "$DEBUG_KEYSTORE_BASE64" | tr -d '\r\n ' | base64 --decode > "$signing_file"; then
  echo "::error::DEBUG_KEYSTORE_BASE64 is not valid base64."
  exit 1
fi

# These credentials match the existing debug APK signing configuration.
# Validate before replacing any local keystore; never print the secret itself.
keytool -list -keystore "$signing_file" -storepass android -alias androiddebugkey > /dev/null
keytool -exportcert -keystore "$signing_file" -storepass android -alias androiddebugkey |
  sha256sum | awk '{ print "Signing certificate SHA-256: " $1 }'
mv "$signing_file" "$signing_dir/debug.keystore"

# Pass the exact restored file to Gradle, independent of Android/Java home defaults.
if [[ -n "${GITHUB_ENV:-}" ]]; then
  printf 'WALKBUDDY_SIGNING_KEYSTORE=%s/debug.keystore\n' "$(cd "$signing_dir" && pwd)" >> "$GITHUB_ENV"
fi
