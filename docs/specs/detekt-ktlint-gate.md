# Feature Implementation Spec: Wire detekt and ktlint into the check task

## Source Feature

- `id`: detekt-ktlint-gate
- `area`: bootstrap
- `depends_on`: module-skeleton (`accepted`)
- `status`: not_started (at planning time, 28 September 2026, HEAD `693dda0`)
- `source`: `feature_list.json`

## Goal

`./gradlew check`, and so `CI=true ./init.sh`, runs detekt (static analysis) and ktlint (formatting)
over every module that compiles Kotlin. The gate passes on the current tree without a baseline file,
fails on a deliberate formatting violation (via ktlint) and on a deliberate static-analysis violation
(via detekt), and `init.sh` reports each tool as wired only when `check` really runs it in every
Kotlin module. A future `:feature:*` module gets both tools with no per-module build code.

## Non-Goals

- No Compose-specific rule sets (`io.nlopez.compose.rules`) — see Decision 3.
- No `build-logic` convention plugins (Decision 4); no change to any module `build.gradle.kts`.
- No type-resolution detekt tasks (`detektMain`, `detektDebug`…) in `check`; no detekt or ktlint
  baseline file; no linting of the root `build.gradle.kts`/`settings.gradle.kts` (Decision 4).
- No product behavior, no new module, no change to `ModuleIsolationTest` rules, `gradle.properties`,
  the wrapper or `gradle-daemon-jvm.properties`. No edit to `.claude/agents/*.md` or `AGENTS.md`.

## Job Story

When any later slice (or the phase 2 assistant) changes Kotlin code,
I want the standard gate to reject misformatted code and common static-analysis defects,
so I can review behavior instead of style, and cite the gate as course evidence.

## Users And Permissions

No end-user behavior changes. No writes or mutations; D-13 does not apply.

## Acceptance Scenarios

### Scenario 1: Green on the current tree
Given the repository after this slice
When `CI=true ./init.sh` runs
Then it exits 0 and prints `konsist: wired`, `detekt: wired`, `ktlint: wired`; `check` executed
`:<module>:detekt` and `:<module>:ktlint*SourceSetCheck` in `:app`, `:core:model`, `:core:ui`,
`:core:data` and `:konsist-test`, and no baseline file exists.

### Scenario 2: A formatting violation fails the gate through ktlint only
Given `CoreModelMarker.kt` with `const val PATH: String = ":core:model"` changed to
`const val PATH:String   =   ":core:model"`
When `./gradlew check --continue` runs, then `CI=true ./init.sh`
Then check fails with exactly one failed task, `:core:model:ktlintMainSourceSetCheck`
(`colon-spacing`, `no-multi-spaces`…); `init.sh` exits non-zero. After restoring the file (SHA-1
match) both pass.

### Scenario 3: A static-analysis violation fails the gate through detekt only
Given `CoreModelMarker.kt` with this appended (correctly formatted):
`fun detektProbe(value: Int): Int { if (value > 0) { } return value }` written over five lines
When `./gradlew check --continue` runs, then `CI=true ./init.sh`
Then exactly one failed task, `:core:model:detekt` (`EmptyIfBlock`, `CoreModelMarker.kt:12:20`);
`init.sh` exits non-zero. After restoring, both pass.

### Scenario 4: Composables are not flagged
Given `PlaceholderScreen.kt` unchanged (`@Composable fun PlaceholderScreen`, private preview)
When the gate runs
Then neither detekt `FunctionNaming` nor ktlint `function-naming` reports it.

### Scenario 5: init.sh does not over-report (approval-gated, Decision 5)
Given the tools applied only to `kotlin-jvm` modules (the `com.android.base` hook removed)
When `CI=true ./init.sh` runs
Then it prints `detekt: NOT WIRED YET (missing from check in: :app :core:data :core:ui)` and the
same for ktlint; with the root build file at HEAD it names all five modules. Restore afterwards.

## Repository Research

### Files Inspected
- `AGENTS.md`, `PROGRESS.md`, `feature_list.json` (this entry; three accepted features).
- `.claude/skills/architecture/SKILL.md` (Build Conventions: "revisit build-logic … when
  detekt-ktlint-gate needs to configure every module"), `docs/specs/konsist-isolation-rules.md`,
  `init.sh`, root and module `build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`,
  `gradle.properties` (CC on, `kotlin.code.style=official`), `gradle/gradle-daemon-jvm.properties`
  (daemon JVM 25), every tracked `.kt` file, `docs/technical-discovery.md` §Testing,
  `.claude/agents/*.md` and `START-HERE.md` (tool mentions only). No `.editorconfig` exists.
- Maven Central / Gradle Plugin Portal metadata (28 Sep 2026): detekt 1.x latest **1.23.8** (Feb
  2025); detekt 2 (`dev.detekt`) latest **2.0.0-alpha.6** (Aug 2026, no stable 2.0; its CLI runs on
  `kotlin-compiler` 2.4.10); ktlint-gradle **14.2.0** (14.0.1 added Gradle 9.1/Java 25 support,
  14.1.0 AGP 9 built-in Kotlin); ktlint **1.8.0**; kotlinter 5.7.0; compose-rules 0.6.7.

### Prototype (verified, not assumed)
A clone of `693dda0` in the session scratchpad, with `local.properties` copied. Results:
- **detekt 1.23.8 cannot run here.** With default settings: `Invalid value (25) passed to
  --jvm-target`. With `jvmTarget = "11"`: `IllegalArgumentException: 25.0.2` from
  `com.intellij.util.lang.JavaVersion.parse` inside its bundled `kotlin-compiler-embeddable`
  2.0.21 (it cannot parse the JDK 25 version string; detekt 1.x runs in the daemon, JVM 25).
  Forcing the compiler to 2.2.10: `detekt was compiled with Kotlin 2.0.21 but is currently running
  with 2.2.10. This is not supported.` No 1.23.x release is newer.
- **detekt 2.0.0-alpha.6 runs** on JDK 25 / Gradle 9.6 / AGP 9.4.1 built-in Kotlin with the
  configuration cache (entry stored, then `Reusing configuration cache`). It registers `detekt`
  per module, wired into `check`, plus variant-aware type-resolution tasks (not in `check`). The
  plain `detekt` task covers `src/{main,test}/{java,kotlin}` (4 files in `:app`), not `androidTest`.
- **ktlint-gradle 14.2.0 + ktlint 1.8.0 run**, detect AGP 9 built-in Kotlin source sets (main, test,
  androidTest, variants) and each module's `build.gradle.kts` (`ktlintKotlinScriptCheck`), all in
  `check`. Its worker uses `kotlin-compiler-embeddable` 2.2.21 (prints `sun.misc.Unsafe` warnings
  on JDK 25, like Konsist).
- Findings on today's tree, and resolution (all resolved, no baseline):
  | Tool / rule | Where | Resolution |
  |---|---|---|
  | ktlint default `ktlint_official` style: `chain-method-continuation`, `multiline-expression-wrapping`, `function-signature`… (~40) | build files, `ModuleIsolationTest.kt`, `PlaceholderScreen.kt` | Config: `ktlint_code_style = android_studio` (Android project) |
  | ktlint `trailing-comma-on-call-site` (android_studio default) | `PlaceholderScreen.kt`, konsist files | Config: allow trailing commas — the repo uses them consistently |
  | ktlint + detekt function naming | `PlaceholderScreen`, `PlaceholderScreenPreview` | Config: Composable exception (Decision 3) |
  | ktlint `import-ordering`, `no-wildcard-imports`, `final-newline`; detekt `NewLineAtEndOfFile` | `ExampleUnitTest.kt`, `ExampleInstrumentedTest.kt` (template files) | Fix: `import org.junit.Assert.assertEquals`, then `./gradlew ktlintFormat` |
  | ktlint `function-signature` (fits on one line) | `ModuleIsolationTest.kt:145` | Fix: `ktlintFormat` joins it (113 chars) |
  With those, `./gradlew check` exit 0; detekt reported nothing else on the tree.
- Scenarios 2, 3, 5 reproduced exactly as written (each probe failed only its tool; `init.sh`
  exit 1; restore by copy → SHA-1 `915f5409…` match → green).
- **ktlint-gradle quirk (fail-closed):** if a *new* file with violations is added and then
  *deleted*, `runKtlintCheckOverMainSourceSet` handles the removal incrementally, keeps the stale
  errors, and the gate stays red until `./gradlew ktlintCheck --rerun-tasks`. Restoring a modified
  file works normally. Hence the probes modify an existing file and restore it by copy.
- Unchanged `init.sh` already prints `detekt: wired`/`ktlint: wired` with these plugins (its grep
  over `tasks --all` matches the task names) — and also with the tools missing from every Android
  module (Scenario 5 layout), which is why Decision 5 exists.
- Timing (warm daemon, 3 runs each): `CI=true ./init.sh` no-op 7.0–7.2 s at HEAD → 5.6–6.2 s with
  tools and the Decision 5 detection (7.2–7.7 s with the old detection); after a one-line edit in
  `:core:model` 8.1–8.6 s → 7.1–7.6 s; `detekt ktlintCheck --rerun-tasks` over all modules
  6.2 s; the first run after the build-script change ~35 s (configuration + worker start).
- `./gradlew check --warning-mode all --rerun-tasks`: exit 0, no Gradle deprecation warning (only
  the JDK `sun.misc.Unsafe` lines). Not verified: a ktlint `intellij_idea` vs `android_studio`
  comparison (tool-permission errors in the planning session); `android_studio` is chosen because
  this is an Android project, not because the alternative was measured.

## Technical Approach

### Decision 1: detekt 2.0.0-alpha.6 and ktlint-gradle 14.2.0 with ktlint 1.8.0
detekt 1.23.8 is proven unusable on this JDK 25 daemon; 2.0.0-alpha.6 is the only detekt that runs.
It is an alpha: pin it exactly, no dynamic version, and flag it for user approval (below). ktlint via
**jlleitschuh ktlint-gradle** because it is proven with AGP 9 built-in Kotlin, lints `.kts` build
files too, offers `ktlintFormat` for one-command fixes, pins the ktlint engine independently (1.8.0,
latest), and its task names honestly contain "ktlint". Rejected: kotlinter (tasks `lintKotlin*`,
AGP 9 support not verified); Spotless (heavier, `spotlessCheck`, formatting-only engine wrapper);
detekt's ktlint wrapper (engine tied to an alpha, rules run inside the `detekt` task so ktlint
failures would be indistinguishable from detekt ones). Catalog additions:
`[versions] detekt = "2.0.0-alpha.6"`, `ktlintGradle = "14.2.0"`, `ktlint = "1.8.0"`;
`[plugins] detekt = { id = "dev.detekt", version.ref = "detekt" }`,
`ktlint = { id = "org.jlleitschuh.gradle.ktlint", version.ref = "ktlintGradle" }`.

### Decision 2: configuration files
`.editorconfig` (repo root, new; no `end_of_line`, because `core.autocrlf=true` here):
```
root = true

[*]
insert_final_newline = true

[*.{kt,kts}]
indent_style = space
indent_size = 4
max_line_length = 120
ktlint_code_style = android_studio
ktlint_function_naming_ignore_when_annotated_with = Composable
ij_kotlin_allow_trailing_comma = true
ij_kotlin_allow_trailing_comma_on_call_site = true
```
120 matches detekt's default `MaxLineLength`, so the tools agree. `config/detekt/detekt.yml` (new),
only overrides on top of the defaults:
```yaml
# Overrides on top of detekt's default config (buildUponDefaultConfig = true).
naming:
  FunctionNaming:
    # Composable functions are PascalCase by Compose convention.
    ignoreAnnotated:
      - 'Composable'
```

### Decision 3: Compose — exceptions only, no Compose rule set now
The two naming exceptions above are enough for today's code (Scenario 4). compose-rules 0.6.7 would
add a third release train, its detekt artifact targets the 1.x API (not verified against the
2.0 alpha), and there is one placeholder composable to check. Reconsider when `design-tokens-theme`
or the first `:feature:*` screen lands; do not add it here.

### Decision 4: apply from the root build file, not `build-logic`
Root `build.gradle.kts` becomes (verified; CC-compatible):
```kotlin
plugins {
    // existing four aliases unchanged, plus:
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

// detekt and ktlint run in every module that compiles Kotlin, including future :feature:*
// modules, without per-module build code (see the architecture skill, Build Conventions).
val ktlintVersion = libs.versions.ktlint.get()
val detektConfig = file("config/detekt/detekt.yml")

subprojects {
    val applyQualityTools = {
        pluginManager.apply("dev.detekt")
        pluginManager.apply("org.jlleitschuh.gradle.ktlint")
        extensions.configure<dev.detekt.gradle.extensions.DetektExtension> {
            buildUponDefaultConfig.set(true)
            config.setFrom(detektConfig)
        }
        extensions.configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
            version.set(ktlintVersion)
        }
    }
    pluginManager.withPlugin("org.jetbrains.kotlin.jvm") { applyQualityTools() }
    pluginManager.withPlugin("com.android.base") { applyQualityTools() }
}
```
(`libs` is not reachable inside `subprojects {}`; hence the vals.) Reasoning: the skill asked to
revisit `build-logic` now. A convention plugin must be applied per module — exactly the per-module
step this gate must not depend on, and a forgotten line would silently skip the gate. The
plugin-reactive root block reaches every Kotlin module, skips the empty `:core` container, and costs
no included-build compilation. Trade-off: cross-project configuration is incompatible with Gradle
Isolated Projects (not enabled here). Record `build-logic` as deferred to the first `:feature:*`
module, which will duplicate Android-library boilerplate; the quality block can move there then.
Root scripts stay unlinted (applying ktlint at the root would also need a root `check` task).

### Decision 5: init.sh detection — REQUIRES THE USER'S EXPLICIT APPROVAL
Unchanged, init.sh would print `wired` (task names match), but it only proves the plugin registered
tasks somewhere — it printed `wired` even with `:app`, `:core:ui`, `:core:data` unanalysed — and it
costs two `tasks --all` runs. Proposed change: replace the `for tool in detekt ktlint` loop with
the block below, and the `== Check ==` comment (three lines) with
`# ./gradlew check runs unit tests, Android lint, the Konsist suite (:konsist-test), detekt and` /
`# ktlint (both applied to every Kotlin module by the root build.gradle.kts).` Konsist detection is
untouched.
```bash
# detekt and ktlint are Gradle plugins. Each counts as wired only when `check` itself schedules
# its task in every module that compiles Kotlin (check has already passed above, so they ran
# clean). A Kotlin module that escapes a tool is named, and the tool is reported as not wired.
check_plan=$(./gradlew check --dry-run --quiet 2>/dev/null || true)
modules_running() { # $1: task-name regex; prints the paths of the modules whose check runs it
  grep -oE "^:[^ ]*:$1( |$)" <<<"$check_plan" | sed -E 's/ $//; s/:[^:]+$//' | sort -u
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
```
Honest because `set -e` means this line is reached only after `check` passed, and a task in
`check`'s dry-run graph did run (or was up to date with unchanged inputs). It still exits 0 when not
wired (status report, as today). **If the user declines**, leave init.sh untouched and record in
PROGRESS.md that `wired` there means "tasks registered", not "runs in every module".

## Expected File Changes
- `gradle/libs.versions.toml`, `build.gradle.kts` — modify (Decisions 1, 4).
- `.editorconfig`, `config/detekt/detekt.yml` — create (Decision 2).
- `app/src/test/java/com/bbbjam/ExampleUnitTest.kt`,
  `app/src/androidTest/java/com/bbbjam/ExampleInstrumentedTest.kt`,
  `konsist-test/src/test/kotlin/com/bbbjam/konsist/ModuleIsolationTest.kt` — formatting only.
- `init.sh` — only as in Decision 5, only with approval.
- `.claude/skills/architecture/SKILL.md`, `docs/technical-discovery.md`, `PROGRESS.md`,
  `feature_list.json` — see below.

## Visual Design Impact
- UI involved: no.

## Durable Documentation Impact
- `.claude/skills/architecture/SKILL.md` Build Conventions: **update** — replace the build-logic
  bullet with: quality tools are applied from the root `build.gradle.kts` to every module applying
  `kotlin-jvm` or an Android plugin; a new module needs no tool configuration; `build-logic`
  deferred to the first `:feature:*` module; config lives in `.editorconfig` and
  `config/detekt/detekt.yml`; `./gradlew ktlintFormat` fixes formatting; never add a baseline,
  `ignoreFailures` or a blanket rule disable without the user's approval.
- `docs/technical-discovery.md` §Testing: **update** the detekt/ktlint line with versions and
  "run by `./gradlew check`".
- `ARCHITECTURE.md`, `CONSTRAINTS.md`: do not exist; not needed. `AGENTS.md`,
  `.claude/agents/*.md`, `START-HERE.md`: not needed (the agents' "until their slices land"
  wording becomes outdated but stays conditional; rewording is the user's call — mention it).
- `PROGRESS.md`: "What exists", gate description, session entry, the ktlint deletion quirk.

## Implementation Plan
1. `CI=true ./init.sh` on the untouched tree; record exit code and status lines.
2. Catalog, root build file, `.editorconfig`, `detekt.yml`.
3. Fix the explicit import; `./gradlew ktlintFormat`; review the diff is formatting only.
4. `./gradlew check --warning-mode all` (record deprecations, if any), then Scenario 1.
5. Scenarios 2–4; restore with SHA-1 proof.
6. If approved, Decision 5 and Scenario 5; else record the fallback.
7. Docs, `feature_list.json` (`passing`), `PROGRESS.md`.

## Implementation Tasks
- [ ] Baseline gate recorded.
- [ ] Catalog entries and root `build.gradle.kts` block exactly as Decision 4.
- [ ] `.editorconfig` and `config/detekt/detekt.yml` exactly as Decision 2.
- [ ] Template tests and `ModuleIsolationTest.kt` formatted; no behavior change; tests still pass.
- [ ] Green gate; ktlint and detekt probes; restoration proof.
- [ ] init.sh (approval-gated) with negative control; skill, technical discovery, progress, list.

## Verification Plan
All JVM, inside the standard gate; no device step (nothing visible changes); no E2E harness exists.
- Scenario 1: `CI=true ./init.sh` exit 0 and the three `wired` lines; `./gradlew check` (non-quiet)
  lists `:X:detekt` and `:X:ktlint*SourceSetCheck` for the five modules; `git ls-files` has no
  `*baseline*.xml`.
- Scenarios 2 and 3: copy `CoreModelMarker.kt` to the scratchpad, record SHA-1, apply the change,
  `./gradlew check --continue` (only the named task FAILED), `CI=true ./init.sh` (non-zero), copy
  back, SHA-1 match, `CI=true ./init.sh` exit 0. Never demonstrate with a new-then-deleted file.
- Scenario 5 (only with approval): edit the root block, run, restore, rerun; also the HEAD root file.
- Timing: three warm `CI=true ./init.sh` runs before and after; the added time must stay within
  ~2 s of the baseline (the prototype measured it faster).

## Evidence To Capture
Short lines in `feature_list.json` `evidence` and the PROGRESS.md session entry; logs stay in the
scratchpad. Tool versions; baseline/final exit codes and status lines; per probe: the change, the
failing task, the first rule message (e.g. `CoreModelMarker.kt:12:20 … [EmptyIfBlock]`), init.sh
exit code, SHA-1 restore proof; timings; deprecation scan result; whether init.sh changed (with
approval) or the fallback was recorded.

## Validator Checklist
- [ ] Versions pinned in the catalog as Decision 1; no dynamic versions; no module build file changed.
- [ ] Config exactly as Decision 2; no baseline, no `ignoreFailures`, no extra rule disabled.
- [ ] Source edits are formatting only (validator reads the diff); all tests still pass.
- [ ] Validator reruns `CI=true ./init.sh` (exit 0, three `wired`) and reproduces Scenarios 2 and 3.
- [ ] init.sh changed only as Decision 5 and only with recorded approval.
- [ ] Skill and technical-discovery updates present; `feature_list.json` is `passing`, not `accepted`.

## User Approvals

Recorded 28 September 2026, before implementation:

- **Decision 5, the `init.sh` change: approved** by the user, exactly as written above.
- **detekt 2.0.0-alpha.6: approved** by the user over lowering the daemon JVM pin to 21. No stable
  detekt runs on the Java 25 daemon (`gradle/gradle-daemon-jvm.properties`, `toolchainVersion=25`).
