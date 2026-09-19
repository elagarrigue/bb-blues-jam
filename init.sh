#!/usr/bin/env bash
# Standard startup and verification path for BB Blues Jam.
# feature-flow runs this on every validation, so it must stay fast and must not block.
set -euo pipefail

cd "$(dirname "$0")"
echo "Repository: $(pwd)"

if [ ! -x ./gradlew ]; then
  echo "ERROR: ./gradlew not found or not executable."
  exit 1
fi

# Gradle is the only toolchain here: no dependency install step exists or is needed,
# since Gradle resolves dependencies as part of the build.
echo ""
echo "== Build =="
./gradlew build --quiet

echo ""
echo "== Check =="
# ./gradlew check currently runs unit tests and lint only.
# Konsist, detekt and ktlint are wired in by their own feature slices; once each is
# registered it is picked up here automatically, with no change to this script.
./gradlew check --quiet

echo ""
echo "== Verification tools status =="
for tool in konsist detekt ktlint; do
  if ./gradlew tasks --all --quiet 2>/dev/null | grep -qi "$tool"; then
    echo "  $tool: wired"
  else
    echo "  $tool: NOT WIRED YET (see feature_list.json)"
  fi
done

echo ""
echo "Baseline OK."
echo "Startup command: ./gradlew :app:installDebug   (requires a connected device or emulator)"

if [ "${RUN_START_COMMAND:-0}" = "1" ]; then
  exec ./gradlew :app:installDebug
fi
