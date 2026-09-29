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
# ./gradlew check runs unit tests, Android lint, the Konsist suite (:konsist-test), detekt and
# ktlint (both applied to every Kotlin module by the root build.gradle.kts).
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
# detekt and ktlint are Gradle plugins. Each counts as wired only when `check` itself schedules
# its task in every module that compiles Kotlin (check has already passed above, so they ran
# clean). A Kotlin module that escapes a tool is named, and the tool is reported as not wired.
check_plan=$(./gradlew check --dry-run --quiet 2>/dev/null || true)
modules_running() { # $1: task-name regex; prints the paths of the modules whose check runs it
  # `|| true`: an empty plan (e.g. a Gradle startup flake) must print NOT WIRED, not abort silently.
  { grep -oE "^:[^ ]*:$1( |$)" <<<"$check_plan" || true; } | sed -E 's/ $//; s/:[^:]+$//' | sort -u
}
kotlin_modules=$(modules_running 'compile[A-Za-z]*Kotlin')
report_tool() { # $1: tool name, $2: task-name regex of its analysis task
  local missing
  missing=$(comm -23 <(echo "$kotlin_modules") <(modules_running "$2") | tr '\n' ' ' | sed 's/ $//')
  if [ -n "$kotlin_modules" ] && [ -z "$missing" ]; then
    echo "  $1: wired"
  else
    echo "  $1: NOT WIRED YET (missing from check in: ${missing:-every module})"
  fi
}
report_tool detekt 'detekt'
report_tool ktlint 'ktlint[A-Za-z]*SourceSetCheck'

echo ""
echo "Baseline OK."
echo "Startup command: ./gradlew :app:installDebug   (requires a connected device or emulator)"

# CI=true keeps this non-interactive and never starts the app.
# feature-flow uses `CI=true ./init.sh` as its verification command.
if [ "${CI:-}" != "true" ] && [ "${RUN_START_COMMAND:-0}" = "1" ]; then
  exec ./gradlew :app:installDebug
fi
