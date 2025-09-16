#!/usr/bin/env sh
DIR="$(cd "$(dirname "$0")" && pwd)"
GRADLE_BINARY=${GRADLE_BINARY:-gradle}
if ! command -v "$GRADLE_BINARY" >/dev/null 2>&1; then
  echo "Gradle is not installed. Please install Gradle or set GRADLE_BINARY to a valid Gradle executable." >&2
  exit 1
fi
exec "$GRADLE_BINARY" "$@"
