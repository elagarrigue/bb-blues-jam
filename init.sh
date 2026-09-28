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
# ./gradlew check runs unit tests, lint and the Konsist suite (:konsist-test).
# Konsist, detekt and ktlint are wired in by their own feature slices; once each is
# registered it is picked up here automatically, with no change to this script.
./gradlew check --quiet

echo ""
echo "== Verification tools status =="
# Konsist is a test library, not a Gradle plugin: no plugin registers a task named after it.
# It counts as wired only when the :konsist-test test task exists and its results from the
# `check` above hold at least one test (check has already failed this script if one failed).
konsist_wired() {
  ./gradlew tasks --all --quiet 2>/dev/null | grep -q '^konsist-test:test ' || return 1
  grep -hoE 'tests="[0-9]+"' konsist-test/build/test-results/test/TEST-*.xml 2>/dev/null \
    | grep -qv 'tests="0"'
}
if konsist_wired; then
  echo "  konsist: wired"
else
  echo "  konsist: NOT WIRED YET (see feature_list.json)"
fi
for tool in detekt ktlint; do
  if ./gradlew tasks --all --quiet 2>/dev/null | grep -qi "$tool"; then
    echo "  $tool: wired"
  else
    echo "  $tool: NOT WIRED YET (see feature_list.json)"
  fi
done

echo ""
echo "Baseline OK."
echo "Startup command: ./gradlew :app:installDebug   (requires a connected device or emulator)"

# CI=true keeps this non-interactive and never starts the app.
# feature-flow uses `CI=true ./init.sh` as its verification command.
if [ "${CI:-}" != "true" ] && [ "${RUN_START_COMMAND:-0}" = "1" ]; then
  exec ./gradlew :app:installDebug
fi
