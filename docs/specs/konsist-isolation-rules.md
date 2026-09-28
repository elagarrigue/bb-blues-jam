# Feature Implementation Spec: Enforce feature module isolation with Konsist

## Source Feature

- `id`: konsist-isolation-rules
- `area`: bootstrap
- `depends_on`: module-skeleton (`accepted`, commit `c916042`)
- `status`: not_started (at planning time, 28 September 2026)
- `source`: `feature_list.json`

## Goal

Make the dependency rules of `.claude/skills/architecture/SKILL.md` executable. A Konsist test suite
runs inside `./gradlew check` (so inside `./init.sh`), passes on today's four modules, and fails when
a feature module reaches into another, when `:core:*` reaches into a feature or `:app`, when
`:core:model` touches Android, or when a ViewModel appears. Every rule is proven able to fail. The
rules read the module layout from the file system, so they keep working, unedited, when the first
`:feature:*` module lands. `init.sh` reports Konsist as wired only when those tests actually run.

## Non-Goals

- No permanent `:feature:*` module. Probe modules exist only during verification and are deleted.
- No detekt/ktlint (`detekt-ktlint-gate`), no `build-logic`, no Koin/presenter rules (they need
  Koin and presenter contracts to exist; add them with `molecule-presenter-harness` or the first Koin
  slice). No D-09/D-13 rules (no repositories or external APIs exist yet).
- No change to application source, `:app`/`:core:*` build files, `gradle.properties` or the wrapper.
- No edit to `.claude/agents/*.md` or `AGENTS.md` (their "until their slices land" wording stays
  true).

## Job Story

When a later slice (or the phase 2 assistant) adds code to a feature module,
I want the standard gate to fail if that code reaches into another feature, `:app`, or Android from
`:core:model`, so I can trust module isolation without reading every diff, and show it as evidence.

## Users And Permissions

- No end-user behavior changes. No writes, no mutations; D-13 does not apply.

## Acceptance Scenarios

### Scenario 1: Green on the current layout
Given the repository after this slice
When `CI=true ./init.sh` runs
Then it exits 0, `check` runs `:konsist-test:test` with 8 tests and 0 failures, and it prints
`konsist: wired` (detekt and ktlint still `NOT WIRED YET`).

### Scenario 2: Real feature modules do not trip the rules
Given two temporary Android-library modules `:feature:probe-a` and `:feature:probe-b` that follow
the build conventions and depend only on `:core:model`
When `./gradlew check` runs
Then it passes (no false positive, hyphenated module names handled).

### Scenario 3: A cross-feature import fails the gate
Given `:feature:probe-a` depends on `:feature:probe-b` in Gradle and imports
`com.bbbjam.feature.probeb.ProbeB`
When `CI=true ./init.sh` runs
Then it exits non-zero, and `feature modules do not import other feature modules` and
`module build files declare only allowed project dependencies` both fail naming the import (with
`file:line`) and the build file. After deleting the probes the gate is green again.

### Scenario 4: Every other rule fails on its own violation
Given one temporary violation per rule (table in Verification Plan)
When `./gradlew :konsist-test:test` runs
Then exactly the targeted test fails with its Konsist/JUnit message; after the revert it passes.

### Scenario 5: A source-only change reruns the suite
Given `:konsist-test:test` is UP-TO-DATE
When only a `.kt` file in another module changes
Then the next run executes the task instead of reporting UP-TO-DATE.

## Repository Research

### Files Inspected
- `AGENTS.md`, `PROGRESS.md`, `feature_list.json` (this entry, `module-skeleton`, `detekt-ktlint-gate`).
- `.claude/skills/architecture/SKILL.md` — Module Layout table, Dependency Rules, Build Conventions,
  Anti-Patterns (ViewModel row).
- `docs/specs/module-skeleton.md`; `settings.gradle.kts` (4 includes), root `build.gradle.kts`,
  `gradle/libs.versions.toml`, `gradle.properties` (configuration cache on), the four module build
  files, `init.sh`, `.gitignore` files, `docs/technical-discovery.md` §Testing and Verification.
- `.claude/agents/{planner,implementer,validator}.md`, `START-HERE.md` (Konsist mentions only).
- Maven Central `com/lemonappdev/konsist/maven-metadata.xml`: latest is **0.17.3** (published
  2024-12-08; nothing newer as of 28 September 2026). Its POM pulls `kotlin-compiler-embeddable`
  2.0.21 and `kotlin-stdlib-jdk8` 2.0.21 at runtime.

### Prototype (verified, not assumed)
A clone of `HEAD` in the session scratchpad got exactly the setup below. Results, 28 September 2026:
- `:konsist-test` compiled with Kotlin 2.2.10 against Konsist 0.17.3; all 8 tests below passed on
  the current tree (`tests="8" failures="0"`), in about 2 s.
  `testRuntimeClasspath` keeps `kotlin-compiler-embeddable:2.0.21` (Konsist's parser), isolated to this
  module. JDK 25 prints `sun.misc.Unsafe` warnings from that jar; harmless noise.
- `Konsist.scopeFromProject()` found every `.kt` file in `app`, `core/*` and `konsist-test`, all
  source sets, no `build/` output. It does **not** include `.kts` files.
- **`KoFile.moduleName` uses the OS separator on Windows (`core\model`)**; rules must normalise `\`
  to `/` or every `core/`/`feature/` filter silently matches nothing.
- Konsist `assertFalse`/`assertTrue` on an empty list **passes** (0.17.3 default), so rules over
  `feature/*` are vacuous today; the probes (Scenario 2, 3) are what prove them.
- **Without extra inputs the test task stays UP-TO-DATE after a violation is added to another
  module** (reproduced). Declaring the project's `.kt`/`.kts` files as inputs fixed it (Scenario 5).
- Every violation in the Scenario 4 table failed exactly its test. A fully qualified reference with
  no import (`com.bbbjam.feature.probeb.ProbeB()`) passed the import rule and was caught only by the
  build-file rule — the reason that rule exists. `hasParentWithName("ViewModel")` missed
  `: androidx.lifecycle.ViewModel()`; matching the parent's simple name fixed it.
- `./gradlew tasks --all` lists `konsist-test:test - Runs the test suite.` The **unchanged** init.sh
  greps that output for `konsist`, so it would print `wired` as soon as any module is *named*
  konsist-something, even with zero tests. The init.sh change below printed `wired` with the suite,
  and `NOT WIRED YET` with the test class removed (task NO-SOURCE).
- `--quiet` (used by init.sh) hides test logging; the failure message appears in a non-quiet run and
  always in the XML result.

## Technical Approach

### Decision 1: a dedicated test-only module `:konsist-test`
Directory `konsist-test/`, `kotlin-jvm` plugin, Java 11 exactly as `:core:model` (Build
Conventions), sources in `src/test/kotlin/com/bbbjam/konsist/`, own `.gitignore` with `/build`, no
`src/main`. Dependencies: `testImplementation(libs.junit)`, `testImplementation(libs.konsist)`, and
**no project dependency** (Konsist reads sources, not classes). Rejected: `:app` unit tests — `:app`'s
`test` runs debug and release variants (the suite would run twice), puts the Kotlin compiler on the
app's test classpath, and couples the architecture guard to the one module allowed to depend on
everything. `kotlin-jvm` is already declared at the root `apply false`. Catalog: `[versions]
konsist = "0.17.3"`, `[libraries] konsist = { group = "com.lemonappdev", name = "konsist",
version.ref = "konsist" }`. `settings.gradle.kts`: `include(":konsist-test")`.

Its `tasks.test` block (prototype-verified with the configuration cache):

```kotlin
// Konsist reads every module's sources, which are not inputs of this task by default.
// Without this, a new violation elsewhere leaves the test UP-TO-DATE and the gate stays green.
tasks.test {
    inputs.files(
        fileTree(rootDir) {
            include("**/*.kt", "**/*.kts")
            exclude("**/build/**", "**/.gradle/**", "**/.kotlin/**")
        },
    ).withPropertyName("projectKotlinSources").withPathSensitivity(PathSensitivity.RELATIVE)
    systemProperty("bbbjam.rootDir", rootDir.absolutePath)
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStackTraces = false
    }
}
```

### Decision 2: the rules (one class, `ModuleIsolationTest`, one `@Test` per rule)
Helpers: `scope = Konsist.scopeFromProject()` (lazy, shared); `modulePath = moduleName.replace('\\', '/')`;
`packageRootOf(path) = "com.bbbjam." + path.split('/').joinToString(".") { it.replace("-", "").lowercase() }`
(`feature/next-jam` → `com.bbbjam.feature.nextjam`). Module groups come from `modulePath` prefixes
`core/` and `feature/`, never from a hard-coded module list. Import rules assert on the **imports**
(`files.flatMap { it.imports }`) so the message names the import and `file:line`; each assert passes a
`testName` equal to the id below. All source sets are checked.

| Id / test | Rule | Source in the skill |
|---|---|---|
| `scope-sanity` / Konsist scope covers every included module | Every `include(":…")` in `settings.gradle.kts` (read via `bbbjam.rootDir`) has ≥1 file in scope | Guard: a broken scope makes every rule vacuous |
| `package-under-module-root` | Every file in `core/*`, `feature/*` declares a package equal to or under `packageRootOf(module)` | Build Conventions (`com.bbbjam.<path>`); makes the import-prefix rules sound |
| `feature-imports-feature` | A feature file imports nothing under `com.bbbjam.feature.` outside its own root | Dependency Rules, first bullet (D-03) |
| `feature-imports-app` | A feature file imports nothing under `com.bbbjam.` that is neither `com.bbbjam.core.` nor `com.bbbjam.feature.` | Layout: `:feature:<name>` may depend on `:core:*` only |
| `core-import-allowlist` | `core/model` imports only its own root; any other `core/<x>` only its own root and `com.bbbjam.core.model` | Layout table; "`:core:*` never depends on `:feature:*` or `:app`" |
| `core-model-no-android` | `core/model` imports nothing from `android.` or `androidx.` | "`:core:model` has no Android dependency" |
| `no-viewmodel-import`, `no-viewmodel-subclass` (one test) | No import starting `androidx.lifecycle.ViewModel`, `androidx.lifecycle.AndroidViewModel` or `androidx.lifecycle.viewmodel`; no class whose parent's simple name (`name.substringAfterLast('.')`) is `ViewModel` or `AndroidViewModel` | Anti-Patterns row 1 (D-02) |
| `build-file-project-deps` | In `core/*/build.gradle.kts` and `feature/*/build.gradle.kts`, every `project(":…")`: `core/model` none; other core only `:core:model`; feature only `:core:*`. Also flag any type-safe accessor (regex `\bprojects\.`) | Layout table + Build Conventions (`project(":…")` only); catches fully qualified use without an import |

Total: 8 tests. The build-file rule reads files with `java.io.File` because Konsist ignores `.kts`.
Known limit, stated in the class KDoc: Konsist sees imports, not resolved references; the build-file
rule closes that gap because a cross-module reference cannot compile without a Gradle dependency.

### Decision 3: demonstrating failure without a permanent feature module
Temporary probes `feature/probe-a` and `feature/probe-b` (Android library, namespace
`com.bbbjam.feature.probea`/`probeb`, catalog SDK levels, Java 11, `implementation(project(":core:model"))`,
one class each), included in `settings.gradle.kts`, give Scenario 2 and 3 through the **real** gate.
Other rules are demonstrated with single temporary files and `./gradlew :konsist-test:test`, which
compiles no other module (so a violation that would not compile, such as a ViewModel in `:app`, still
reaches Konsist). Before starting, copy `settings.gradle.kts` and every file to be touched to the
scratchpad and record SHA-1s; after, restore and confirm SHA-1s and `git status` (no `feature/`
directory, no stray file). No rule is edited between demonstrations.

### Decision 4: init.sh — REQUIRES THE USER'S EXPLICIT APPROVAL
The current detection cannot be honest for Konsist: it greps task names, Konsist registers none, and
the grep would flip to `wired` merely because the module is called `konsist-test`. Proposed change
(prototype-verified), replacing only the loop header and the `check` comment:

```bash
# (in "== Check ==") replace the two comment lines about "unit tests and lint only" with:
# ./gradlew check runs unit tests, lint and the Konsist suite (:konsist-test).

# before the loop:
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
for tool in detekt ktlint; do   # was: for tool in konsist detekt ktlint; do
```

Why it is honest: `set -e` means the script only reaches this point if `check` (which includes
`:konsist-test:test`) passed; Gradle deletes stale results when the task runs, and an UP-TO-DATE task
is trustworthy because of Decision 1's inputs. detekt/ktlint detection is untouched. **If the user
declines**, change nothing in init.sh: it will still print `konsist: wired` because of the module
name; record in PROGRESS.md that this is incidental and does not prove the tests ran.

## Expected File Changes
- `konsist-test/build.gradle.kts`, `konsist-test/.gitignore`,
  `konsist-test/src/test/kotlin/com/bbbjam/konsist/ModuleIsolationTest.kt` — create.
- `settings.gradle.kts`, `gradle/libs.versions.toml` — modify (Decision 1).
- `init.sh` — modify only as in Decision 4, only with approval.
- `.claude/skills/architecture/SKILL.md`, `docs/technical-discovery.md`, `PROGRESS.md`,
  `feature_list.json` — see below.

## Visual Design Impact
- UI involved: no.

## Durable Documentation Impact
- `.claude/skills/architecture/SKILL.md`: **update** — Layout table row `:konsist-test` (test-only,
  JVM, no project dependency); Dependency Rules: Konsist enforces them now (`ModuleIsolationTest`),
  and a new dependency rule means a new test there; Build Conventions: package root is
  `com.bbbjam.<module path>` with hyphens removed (`:feature:next-jam` → `com.bbbjam.feature.nextjam`),
  enforced by `package-under-module-root`.
- `docs/technical-discovery.md` §Testing and Verification: **update** one line naming `:konsist-test`
  and Konsist 0.17.3.
- `ARCHITECTURE.md`, `CONSTRAINTS.md`: do not exist; not needed. `AGENTS.md`, `.claude/agents/*`:
  not needed (wording stays true; any rewording is the user's call).
- `PROGRESS.md`: update "What exists" and the gate description; add a session entry.

## Implementation Plan
1. `CI=true ./init.sh` on the untouched tree; record it.
2. Catalog, settings, `konsist-test/` module with the `tasks.test` block; the 8 tests.
3. `./gradlew :konsist-test:test`, then `CI=true ./init.sh` (Scenario 1).
4. Scenario 5, then Scenarios 2–3 with the probes, then the Scenario 4 table; restore and verify.
5. If approved, apply Decision 4 and run its negative control; otherwise record the fallback.
6. Docs, `feature_list.json` (`passing`), `PROGRESS.md`.

## Implementation Tasks
- [ ] Baseline gate recorded.
- [ ] Catalog entries, `include(":konsist-test")`, module build file, `.gitignore`.
- [ ] `ModuleIsolationTest` with the 8 tests and the separator normalisation.
- [ ] Green gate; UP-TO-DATE check; probe and single-file demonstrations; restoration proof.
- [ ] init.sh (approval-gated), skill, technical discovery, `feature_list.json`, `PROGRESS.md`.

## Verification Plan
JVM, in the standard gate: `CI=true ./init.sh` exit 0 (Scenario 1);
`konsist-test/build/test-results/test/TEST-com.bbbjam.konsist.ModuleIsolationTest.xml` shows
`tests="8" failures="0" errors="0"`.

Scenario 5: run `:konsist-test:test` twice (second is UP-TO-DATE), append `// touch` to
`CoreModelMarker.kt`, run again: the task executes. Restore the file.

Scenarios 2–3: add probes → `./gradlew check` exit 0 → add the Gradle dependency and import in
probe-a → `CI=true ./init.sh` exits non-zero with the two named tests failing → replace the import
with a fully qualified reference → `./gradlew :konsist-test:test` fails **only** the build-file test →
delete probes, restore settings → `CI=true ./init.sh` exit 0.

Scenario 4, each alone, `./gradlew :konsist-test:test` (non-quiet), then revert:

| Test expected to fail | Temporary change |
|---|---|
| scope sanity | rename `core/ui/.../CoreUiMarker.kt` to `.kt.txt` |
| `package-under-module-root` | `core/ui/.../Squatter.kt` with `package com.bbbjam.core.data` |
| `feature-imports-app` | probe-a file importing `com.bbbjam.MainActivity` (probes present) |
| `core-import-allowlist` | `core/ui/.../Cross.kt` importing `com.bbbjam.core.data.CoreDataMarker` |
| `core-model-no-android` | `core/model/.../Droid.kt` importing `android.content.Context` |
| no ViewModel (import branch) | `app/.../ProbeViewModel.kt`: `import androidx.lifecycle.ViewModel` + subclass |
| no ViewModel (subclass branch) | same file, `class ProbeViewModel : androidx.lifecycle.ViewModel()`, no import |
| `build-file-project-deps` (core) | `core/data/build.gradle.kts` adds `implementation(project(":core:ui"))` |

init.sh (only if Decision 4 is approved): move `ModuleIsolationTest.kt` to the scratchpad,
`CI=true ./init.sh` prints `konsist: NOT WIRED YET`; restore it, prints `wired`.

No device step: nothing user-visible changes. No E2E harness exists; none is justified.

## Evidence To Capture
Record in `feature_list.json` `evidence` and the PROGRESS.md session entry (short lines, repo-relative
paths). Keep full console logs in the scratchpad only; commit no logs or XML.
- Baseline and final `CI=true ./init.sh` exit codes and the three tool-status lines.
- The XML summary line and the 8 test names — this is the course evidence of isolation.
- Per demonstration: the change, command, exit code, the failing test name and the first message
  line (`Assert '<id>' was violated (1 time)` plus the offending import or file with `:line`), and
  the restore proof (SHA-1 match, `git status`).
- Scenario 5 task outcomes (`UP-TO-DATE` then executed).
- Konsist version and the `kotlin-compiler-embeddable` version on `:konsist-test`'s test runtime.
- Whether init.sh was changed (with the user's approval) or the fallback was recorded.

## Validator Checklist
- [ ] `:konsist-test` follows Build Conventions, has no project dependency, is the only new module.
- [ ] The 8 tests exist as specified; `\` is normalised; no hard-coded list of feature modules.
- [ ] `tasks.test` declares the project sources as inputs; the validator reproduces Scenario 5.
- [ ] Validator reruns `CI=true ./init.sh` (exit 0, `konsist: wired`) and reproduces at least the
      cross-feature demonstration and one other row; tree restored afterwards.
- [ ] init.sh changed only as in Decision 4 and only with recorded user approval; otherwise unchanged.
- [ ] No `feature/` directory, probe file or application-source change remains.
- [ ] Skill and technical-discovery updates present; `feature_list.json` is `passing`, not `accepted`.

## User Approvals

Recorded 28 September 2026, before implementation:

- **Decision 4, the `init.sh` change: approved** by the user, exactly as written above.
- **Package convention `com.bbbjam.<module path>` with hyphens removed: approved** by the user.
