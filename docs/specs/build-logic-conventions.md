# Feature Implementation Spec: Introduce build-logic convention plugins

## Source Feature

- `id`: build-logic-conventions
- `area`: bootstrap
- `depends_on`: module-skeleton (`accepted`), detekt-ktlint-gate (`accepted`)
- `status`: not_started (at planning time, 30 September 2026, HEAD `7b3cc11`)
- `source`: `feature_list.json`

## Goal

Move the per-module build boilerplate (plugin choice, SDK levels, Java 11, Compose setup,
`isReturnDefaultValues`, the presenter test stack, Koin for features) into binary convention plugins
in a `build-logic/` included build. Every module build file then applies one or two `bluesjam.*`
plugins and keeps only what is its own: namespace, `:app` identity, project dependencies and
module-specific libraries. Nothing observable changes: same task graph, same resolved dependencies,
same tests, a byte-identical debug APK. A new Konsist rule fails when a module skips its convention
or copies convention-owned settings back into its own file.

## Non-Goals

- No new module, no product code, no UI change. `next-jam-read-only-list` adds `:feature:next-jam`.
- detekt and ktlint stay applied from the root `subprojects {}` block (Decision 3). The block does
  not move, and neither does their configuration.
- No change to `init.sh`, `gradle.properties`, the wrapper, `gradle-daemon-jvm.properties`,
  `.editorconfig` or `config/detekt/detekt.yml`.
- No Isolated Projects opt-in (Decision 5 records what would be needed).
- No version bump of anything, and no catalog-accessor plugin aliases for the conventions.

## Job Story

When a slice adds the second (and every later) `:feature:*` module,
I want to apply `bluesjam.android.feature` and list only that module's own dependencies,
so the SDK levels, Java target, Compose and test setup cannot drift between modules.

## Users And Permissions

- No roles, writes or mutations. D-13 does not apply. The app behaves exactly as before.

## Acceptance Scenarios

### Scenario 1: The gate is unchanged and green
Given the repository after this slice
When `CI=true ./init.sh` runs
Then it exits 0 and prints `konsist: wired` (11 tests), `detekt: wired`, `ktlint: wired`.

### Scenario 2: Module files carry no convention-owned settings
Given every module in `settings.gradle.kts`
When its `build.gradle.kts` is read
Then its `plugins {}` holds only `id("bluesjam.…")` entries, and it contains none of `compileSdk`,
`minSdk`, `targetSdk`, `JavaVersion`, `JvmTarget`, `jvmTarget`, `jvmToolchain`,
`sourceCompatibility`, `targetCompatibility`, `buildFeatures`, `isReturnDefaultValues`.

### Scenario 3: Zero behaviour change
Given the before (HEAD) and after trees built with `./gradlew build`
When the checks in Verification Plan item 3 run
Then the `check --dry-run` and `build --dry-run` plans and `buildEnvironment` are identical, every
module's classpath resolves to the same set of coordinates, test classes and counts are identical,
and the debug APK has the same entry list and the same SHA-1 for every entry outside `META-INF/`.

### Scenario 4: A module that skips its convention is caught
Given `core/data/build.gradle.kts` restored to its HEAD content (raw `android-library`, inline SDKs
and Java 11), or `feature/info` applying `bluesjam.android.presenter` instead of `.feature`
When `./gradlew :konsist-test:test` runs
Then `build-file-applies-convention` fails naming the file, line and reason (texts below).

### Scenario 5: The app still launches
Given the debug build installed on the Pixel 5
When it is launched cold
Then Info is drawn as before, with an empty crash buffer.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md`, `feature_list.json` (this entry: verification items and notes).
- `settings.gradle.kts`, root `build.gradle.kts` (six plugins `apply false`, `subprojects {}`
  quality block), `gradle.properties` (configuration cache on), `gradle/libs.versions.toml`.
- Every module file: `app`, `core/model`, `core/ui`, `core/data`, `feature/info`, `konsist-test`.
- `init.sh` (tool detection via `check --dry-run`),
  `konsist-test/src/test/kotlin/com/bbbjam/konsist/ModuleIsolationTest.kt` (10 rules, including
  `build-file-project-deps`, a line regex over `core/*` and `feature/*` build files).
- `.claude/skills/architecture/SKILL.md` (Build Conventions), `docs/specs/module-skeleton.md`
  (Decision 2, the original deferral), `.gitignore`.

### Prototype (verified, not assumed)

Two fresh clones of HEAD `7b3cc11` in the session scratchpad (`blc/before`, `blc/after`), with the
exact setup in Technical Approach and the rule in Decision 4. Results, 30 September 2026:

- `build-logic` compiled at the first attempt with `kotlin-dsl` (Gradle 9.6.0 embedded Kotlin),
  against AGP 9.4.1 and KGP 2.2.10 as `compileOnly`, no `w:` lines. In AGP 9.4.1 `CommonExtension`
  is **not generic** and `compileSdk { version = release(n) }` works on it from Kotlin.
- `CI=true ./init.sh`: exit 0, three tools `wired`, Konsist 11 tests, 0 failures.
- Compared with `blc/capture.sh` (dependency reports for `debugCompileClasspath`,
  `debugRuntimeClasspath`, `releaseRuntimeClasspath`, `debugUnitTestRuntimeClasspath` of the four
  Android modules and `compileClasspath`, `runtimeClasspath`, `testRuntimeClasspath` of the two JVM
  modules; APK list and per-entry SHA-1; test XML counts; merged manifests; AAR names):
  **identical**: check plan (353 lines), build plan (590), `buildEnvironment`, APK list (466
  lines) and SHA-1 of all 392 non-`META-INF` entries (including `classes*.dex`), merged manifests,
  all 20 test classes and counts, every `:core:*` and `:konsist-test` report.
  **Order-only difference**: `:feature:info`'s four reports and `:app`'s three runtime reports,
  because Koin is now declared by the convention before the module's own `dependencies {}`.
  Normalised to the sorted set of coordinates (tree glyphs, `(*)`, `(c)`, `(n)` stripped) they are
  identical.
- `check --dry-run` lists no `:build-logic:*` task, so `init.sh`'s module detection is unaffected.
- Negative check: removing `alias(libs.plugins.kotlin.compose) apply false` from the root fails
  configuration ("An exception occurred applying plugin request [id: 'bluesjam.android.compose']").
  Removing `android.library` did not fail (AGP's artifact already carries it); keep all six anyway.
- Device: after-APK installed on the Pixel 5 (API 34), `am start -W` COLD 787 ms, `Status: ok`,
  empty crash buffer and AndroidRuntime log, Info screen drawn unchanged (screenshot checked).
- Isolated Projects probe (`-Dorg.gradle.unsafe.isolated-projects=true`): before and after both
  fail with the same two unique problems, both from the root `subprojects {}` block
  (`Project.pluginManager`/`Project.extensions` on subprojects). The conventions add none.
  A third variant with detekt/ktlint moved into a `bluesjam.quality` convention (root block
  deleted) configured **and** ran `check` under Isolated Projects, and `CI=true ./init.sh` stayed
  green with an identical check plan. Not adopted; see Decision 3.
- Timings (`CI=true ./init.sh`, same machine, seconds):

  | Run | Before | After |
  |---|---|---|
  | Fresh clone, first run | 45 | 74 (includes compiling build-logic once) |
  | Warm, nothing changed | 7, 7 | 8, 7 |
  | After `./gradlew clean` | 37 | 39 |
  | One module build file edited (configuration cache miss) | 14 | 16 |
  | One convention source edited | n/a | 19 |

- Not prototyped: Android Studio sync, and opening `build-logic` as a standalone build (it failed
  on the shell's Java 16 because `build-logic` has no `gradle-daemon-jvm.properties`; running
  through the root `./gradlew` uses the Java 25 daemon and works).

## Technical Approach

### Decision 1: included build with binary plugins, catalog shared

- `build-logic/` is an included build: `pluginManagement { includeBuild("build-logic") … }` as the
  first line of `pluginManagement` in `settings.gradle.kts`. Not `buildSrc`: any change to
  `buildSrc` invalidates every build script's classpath, it is always built, and it cannot be
  referenced from `pluginManagement`. The included build is the pattern Gradle and Now in Android
  recommend.
- One subproject, `build-logic/convention`, with `kotlin-dsl`. **Binary plugins** (Kotlin classes
  registered in `gradlePlugin {}`), not precompiled script plugins: script plugins cannot use the
  `libs` accessor either, generate accessors on every compile, and are harder to share helpers
  between. Classes are plain Kotlin that detekt/ktlint could lint later.
- The catalog is shared, not duplicated: `build-logic/settings.gradle.kts` creates `libs` from
  `../gradle/libs.versions.toml`. The build file uses the accessor
  (`compileOnly(libs.android.gradlePlugin)`); plugin classes read versions with
  `extensions.getByType<VersionCatalogsExtension>().named("libs")`.
- AGP and KGP are `compileOnly`: at runtime the conventions use the copies the root build loads
  through its `apply false` block, so there is one AGP and one KGP (proven by identical
  `buildEnvironment`). The root `plugins {}` block therefore stays exactly as it is.
- Convention ids are applied as `id("bluesjam.…")`. No catalog entries for them: a plain id is what
  the Konsist rule matches and needs no version.

### Decision 2: six plugins

| Plugin | Applies / sets | Used by |
|---|---|---|
| `bluesjam.jvm.library` | `org.jetbrains.kotlin.jvm`; `java {}` source/target 11; `compilerOptions.jvmTarget = JVM_11`. No toolchain. | `:core:model`, `:konsist-test` |
| `bluesjam.android.library` | `com.android.library`; common Android setup | `:core:data` |
| `bluesjam.android.application` | `com.android.application`; common Android setup; `targetSdk` from the catalog | `:app` |
| `bluesjam.android.compose` | fails unless `com.android.base` is applied; applies `org.jetbrains.kotlin.plugin.compose`; `buildFeatures.compose = true` on `CommonExtension` | `:app` (and through the two below) |
| `bluesjam.android.presenter` | `bluesjam.android.library` + `.compose`; `testOptions.unitTests.isReturnDefaultValues = true`; `testImplementation` junit, molecule-runtime, turbine, kotlinx-coroutines-test | `:core:ui` |
| `bluesjam.android.feature` | `bluesjam.android.presenter`; `implementation` `platform(koin-bom)`, `koin-core`, `koin-compose` | `:feature:*` |

"Common Android setup" is one helper in `ProjectExtensions.kt`, the load-bearing AGP 9 API:

```kotlin
internal fun Project.configureAndroidCommon(android: CommonExtension) {
    android.apply {
        compileSdk { version = release(libs.intVersion("compileSdk")) }
        defaultConfig.minSdk = libs.intVersion("minSdk")
        compileOptions.sourceCompatibility = JavaVersion.VERSION_11
        compileOptions.targetCompatibility = JavaVersion.VERSION_11
    }
}
```

Dependency rule for conventions: a convention adds a dependency only when the architecture skill
makes it mandatory for every module of that kind (every presenter module has Molecule tests; every
feature has one Koin module). Everything optional or module-specific stays in the module file:
Compose BOM, foundation, tooling-preview and debug tooling, `koin-android`, junit in JVM modules and
`:app`. **Project dependencies never move into a convention**: `build-file-project-deps` reads them
from module files, and a `project(":…")` inside `build-logic` would escape it (D-03).

Catalog changes: `[versions] targetSdk = "37"`; `[libraries] android-gradlePlugin`
(`com.android.tools.build:gradle`, `version.ref = "agp"`) and `kotlin-gradlePlugin`
(`org.jetbrains.kotlin:kotlin-gradle-plugin`, `version.ref = "kotlin"`), under a comment saying they
are for build-logic only.

### Decision 3: detekt and ktlint stay in the root `subprojects {}` block

Kept, as the architecture skill already records. This slice's promise is zero behaviour change, and
the root block is the wiring `detekt-ktlint-gate` was accepted with. `init.sh` needs no change either
way: its detection reads the `check` plan, which the prototype shows identical. Recorded for later:
the root block is the only Isolated Projects blocker in the build, and moving the tools into a
`bluesjam.quality` convention applied by the three base conventions is proven to work (above). It
becomes safe once Decision 4's rule exists, since every module must then apply a base convention.

Known gap, not closed here: `build-logic` Kotlin is outside the root build, so detekt and ktlint do
not lint it, and `init.sh` does not count it as a Kotlin module (correctly: `check` never compiles
it). Closing it means making root `check` depend on an included-build task, a gate change left to a
later slice if the user wants it.

### Decision 4: the enforcing check — new Konsist rule `build-file-applies-convention` (**needs approval**)

An 11th test in `ModuleIsolationTest`, same style as `build-file-project-deps` (plain text over build
files, reported as `Assert '<name>' was violated (n times)`). For every module in
`settings.gradle.kts` (same `INCLUDE_REGEX`), with `//` comments stripped per line:

- at least one `id("bluesjam.…")` (`CONVENTION_ID = \bid\(\s*"(bluesjam\.[a-z.]+)"\s*\)`);
- `app` must apply `bluesjam.android.application`; `feature/*` must apply `bluesjam.android.feature`;
- no raw plugin: `\balias\(\s*libs\.plugins\.|\bid\(\s*"(?!bluesjam\.)|\bkotlin\(\s*"|\bapply\(\s*plugin`;
- no convention-owned word: `\b(compileSdk|minSdk|targetSdk|JavaVersion|JvmTarget|jvmTarget|jvmToolchain|sourceCompatibility|targetCompatibility|buildFeatures|isReturnDefaultValues)\b`;
- a missing `build.gradle.kts` is itself a violation.

Why Konsist and not Gradle: a Gradle-side check needs cross-project access (`subprojects`/
`afterEvaluate`), which adds Isolated Projects problems, and it would run only where a plugin
already is. The Konsist suite already runs inside `check`, already parses build files, and its test
task already declares every `.kts` as an input. Why it matters although Gradle fails a module with
no plugin at all: the real drift is a module that applies the raw plugin and copies the old block
back (which still builds), and a feature that forgets Koin/test setup through the wrong convention.

Prototype failure texts (both demonstrated, then reverted):

```
Assert 'build-file-applies-convention' was violated (7 times). Invalid build files:
core/data/build.gradle.kts applies no bluesjam.* convention plugin
core/data/build.gradle.kts:2 applies a plugin directly, use a convention: alias(libs.plugins.android.library)
core/data/build.gradle.kts:7 sets 'compileSdk', owned by a convention: compileSdk {
…
Assert 'build-file-applies-convention' was violated (1 times). Invalid build files:
feature/info/build.gradle.kts must apply bluesjam.android.feature (applies [bluesjam.android.presenter])
```

`build-file-project-deps` was re-proven on the new file shape (a temporary
`implementation(project(":feature:info"))` in `core/data` failed it). Update the class KDoc to
mention the build-file rules.

**Approval required**: this adds a gate rule (Konsist 10 → 11). The feature's own third
verification item asks for such a check, but per the harness a new gate rule is a verification
change the user must confirm. **Fallback if declined**: no committed rule; the implementer runs the
same checks as a one-off script (`grep` for `id("bluesjam.` and for the owned words in each included
module's build file), records output and a demonstrated failure in evidence, and the Build
Conventions section lists it as a validator checklist item. The third verification item would then
be met by a manual check, which the user must accept explicitly, since nothing would catch a later
regression.

### Decision 5: configuration cache, speed, Isolated Projects

- Configuration cache: stored and reused on every prototype run; `init.sh` warm time unchanged
  (7–8 s). Editing a convention invalidates the cache and recompiles build-logic (19 s vs 14–16 s
  for a module file edit). First run on a fresh clone pays about 25–30 s once for build-logic.
- Isolated Projects: conventions are compatible (use `isolated.rootProject` if a convention ever
  needs a root path). The only blocker remains the root quality block (Decision 3).
- `build-logic` bytecode targets the daemon JVM (Java 25, class major 69); the daemon is pinned by
  `gradle/gradle-daemon-jvm.properties`, so this is safe. Do not add a toolchain.

## Expected File Changes

- `settings.gradle.kts` — modify; `includeBuild("build-logic")` first inside `pluginManagement`.
- `gradle/libs.versions.toml` — modify; `targetSdk`, `android-gradlePlugin`, `kotlin-gradlePlugin`.
- `build.gradle.kts` (root) — **unchanged**.
- `build-logic/settings.gradle.kts` — create; repositories as the root (google with the same content
  filter, mavenCentral, gradlePluginPortal), `FAIL_ON_PROJECT_REPOS`, catalog `libs` from
  `../gradle/libs.versions.toml`, `rootProject.name = "build-logic"`, `include(":convention")`.
- `build-logic/convention/build.gradle.kts` — create; `kotlin-dsl`, the two `compileOnly`, six
  `gradlePlugin` registrations (ids above, classes `com.bbbjam.buildlogic.<Name>ConventionPlugin`).
- `build-logic/convention/src/main/kotlin/com/bbbjam/buildlogic/` — create `ProjectExtensions.kt`
  (`libs`, `intVersion`, `library`, `configureAndroidCommon`) and the six plugin classes, each with a
  one-line KDoc saying what it is for.
- `build-logic/convention/.gitignore` — create, `/build` (`.gradle` is already ignored by the root).
- Module files, after (before = HEAD):
  - `core/model`: `plugins { id("bluesjam.jvm.library") }` + `testImplementation(libs.junit)`.
  - `konsist-test`: `id("bluesjam.jvm.library")`; the `JvmTarget` import and `java {}`/`kotlin {}`
    blocks go; its header comment, `dependencies` and `tasks.test {}` stay verbatim.
  - `core/data`: `id("bluesjam.android.library")`; `android { namespace = "com.bbbjam.core.data" }`;
    `api(project(":core:model"))`.
  - `core/ui`: `id("bluesjam.android.presenter")`; `android { namespace = "com.bbbjam.core.ui" }`;
    dependencies as today minus the four `testImplementation` lines (comments kept).
  - `feature/info`: `id("bluesjam.android.feature")`; `android { namespace = "com.bbbjam.feature.info" }`;
    the D-03 comment, `implementation(project(":core:ui"))`, `androidx.compose.foundation`,
    tooling-preview, debug tooling. Koin and test lines go.
  - `app`: `id("bluesjam.android.application")`, `id("bluesjam.android.compose")`; `android {}`
    keeps `namespace`, `defaultConfig` (`applicationId`, `versionCode`, `versionName`,
    `testInstrumentationRunner`) and `buildTypes`; `compileSdk`, `minSdk`, `targetSdk`,
    `compileOptions`, `buildFeatures` go; `dependencies` unchanged.
- `konsist-test/src/test/kotlin/com/bbbjam/konsist/ModuleIsolationTest.kt` — modify (Decision 4).
- `.claude/skills/architecture/SKILL.md`, `PROGRESS.md`, `feature_list.json` — see below.

## Visual Design Impact

- UI involved: no. `DESIGN.md` not applicable.

## Durable Documentation Impact

- `.claude/skills/architecture/SKILL.md` — **update** Build Conventions:
  - Replace "No `build-logic` convention plugins yet" with a **Convention plugins** bullet: the
    table of Decision 2, the rule "conventions add only dependencies mandatory for the module kind;
    project dependencies stay in module files", and that `build-file-applies-convention` enforces it.
  - Rewrite "Plugin per module kind", "SDK levels come from the catalog" and "Java 11 everywhere"
    as what the conventions do (targetSdk now from the catalog via `bluesjam.android.application`).
  - "Every plugin is declared at the root": keep; new reason is the conventions' `compileOnly`
    AGP/KGP and `pluginManager.apply(id)` (proven failure without `kotlin.compose`).
  - "A module with presenters" → apply `bluesjam.android.presenter` (or `.feature`).
  - Quality tools bullet: still root-applied; add the Isolated Projects note and the unlinted
    `build-logic` gap (Decision 3).
  - Reference feature build file: `feature/info/build.gradle.kts`, now `id("bluesjam.android.feature")`,
    namespace, `project(":core:*")` deps, foundation and tooling.
  - Konsist paragraph: 11 rules, add `build-file-applies-convention`.
  - Minimal Feature Template: `build.gradle.kts  # id("bluesjam.android.feature"); depends on :core:* only`.
- `AGENTS.md`, `init.sh`: not needed; startup path and tool detection unchanged.
- `PROGRESS.md`: "What exists" (build-logic, Konsist 11), session entry, timings.
- `ARCHITECTURE.md`, `CONSTRAINTS.md`: do not exist; the skill is the source of truth.

## Implementation Plan

1. Baseline: `CI=true ./init.sh`; then capture the before artifacts (Verification item 3) from a
   clean `./gradlew build` into a directory outside the repo.
2. Catalog and `settings.gradle.kts`; `build-logic/` with the seven Kotlin files; `./gradlew help`.
3. Convert module files one at a time in this order: `core/model`, `konsist-test`, `core/data`,
   `core/ui`, `feature/info`, `app`; `./gradlew build` after each.
4. Add the Konsist rule; prove it fails on the two Scenario 4 cases, revert each.
5. Gate, after artifacts and comparison, device launch.
6. Architecture skill, `PROGRESS.md`, `feature_list.json` (`passing`).

## Implementation Tasks

- [ ] Baseline gate and before artifacts recorded.
- [ ] Catalog entries; `includeBuild("build-logic")`.
- [ ] `build-logic` settings, build file, `.gitignore`, helpers, six plugin classes.
- [ ] Six module files converted (exact contents above).
- [ ] `build-file-applies-convention` rule + KDoc; two failing cases demonstrated and reverted.
- [ ] Gate, comparisons, device.
- [ ] Architecture skill, `PROGRESS.md`, `feature_list.json`.

## Verification Plan

1. `CI=true ./init.sh` exits 0; three tools `wired`; `ModuleIsolationTest` XML `tests="11"`,
   `failures="0"`.
2. `grep -nE "compileSdk|minSdk|targetSdk|JavaVersion|JvmTarget|buildFeatures|isReturnDefaultValues" */build.gradle.kts */*/build.gradle.kts`
   returns nothing, exit 1 (prototype-verified; build-logic's own build file holds none either).
   Plain `grep`, not `git grep`, so untracked new files are included.
3. Zero behaviour change, each compared before vs after after a clean `./gradlew build`:
   - `./gradlew -q check --dry-run | sort` and `build --dry-run | sort`: identical.
   - `./gradlew -q buildEnvironment`: identical (one AGP 9.4.1, one KGP 2.2.10).
   - `dependencies --configuration <c>` for the configurations listed in the Prototype section:
     byte-identical for `:core:*` and `:konsist-test`; for `:app` and `:feature:info`, identical
     after normalising to the sorted set of coordinates (`sed -E 's/^[| +\\-]+//; s/ \((\*|c|n)\)$//' | grep -E '^[a-z]' | sort -u`).
     Any coordinate difference is a failure.
   - `unzip -l app/build/outputs/apk/debug/app-debug.apk` entry list identical, and SHA-1 of every
     extracted entry outside `META-INF/` identical.
   - Test XML file names and `tests/failures` counts identical (20 classes before; the Konsist class
     goes 10 → 11 tests, the only allowed difference).
4. Scenario 4: both failures shown with their messages, then reverted; `git diff` clean of them.
5. Device (manual, not in the gate): `./gradlew :app:installDebug`; `adb -s 09281FDD4004U6 shell am
   start -W com.bbbjam/.MainActivity` (adb at `$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe`);
   `Status: ok`; `logcat -d -b crash` empty; Info drawn as before.
6. `git status` shows no `build-logic/convention/build` or `build-logic/.gradle` output.

No persistent E2E harness exists; a build-structure slice with a byte-identical APK needs none.

## Evidence To Capture

- Gate output (before/after) and the Konsist count.
- The comparison summary per item 3 (which files are byte-identical, which set-identical).
- Both Scenario 4 failure texts.
- Timings: warm, clean and configuration-cache-miss `init.sh`, before and after.
- Device model, API, `am start` status and time, crash-buffer result.

## Validator Checklist

- [ ] `build-logic` is an included build via `pluginManagement`; no `buildSrc`; binary plugins;
      catalog read from `../gradle/libs.versions.toml`, not copied.
- [ ] Six plugins with the ids and responsibilities of Decision 2; no project dependency and no
      optional library in any convention.
- [ ] Root `build.gradle.kts`, `init.sh`, wrapper, `gradle.properties` unchanged.
- [ ] Module files match Expected File Changes; verification item 2 returns nothing.
- [ ] Item 3 comparisons rerun or their evidence checked; APK byte-identical outside `META-INF/`.
- [ ] New rule present (if approved), both failing cases in evidence; `build-file-project-deps`
      still passes and still fails on a forbidden project dependency.
- [ ] Architecture skill updated as listed; `feature_list.json` `passing`, not `accepted`.

## Open Items For The User

- **Approve Decision 4** (new gate rule `build-file-applies-convention`), or accept the fallback.
- Optional: moving detekt/ktlint into a convention for Isolated Projects (proven, not planned here),
  and linting `build-logic` itself (a gate change) — each would be its own small slice.
