# Feature Implementation Spec: Create the multi-module skeleton with :core modules

## Source Feature

- `id`: module-skeleton
- `area`: bootstrap
- `depends_on`: gradle-kotlin-compose-baseline (`accepted`, commit `ffff173`)
- `status`: not_started (at planning time, 28 September 2026)
- `source`: `feature_list.json`

## Goal

Split the single-module build into `:app`, `:core:model`, `:core:ui` and `:core:data`, with the
dependency direction from `.claude/skills/architecture/SKILL.md`: `:core:model` depends on nothing
and has no Android; `:core:ui` and `:core:data` depend on `:core:model`; `:app` depends on all
three. Each new module is a real, compiled module with one placeholder type, and `:app` is proven
to see all of them. The app still launches into the unchanged placeholder screen. Every later slice
inherits the build conventions chosen here, so the spec fixes them explicitly.

## Non-Goals

- No `:feature:*` module. Each is created by the slice that first needs it.
- No domain types (`domain-model-types`), tokens or theme (`design-tokens-theme`), presenter
  contracts (`molecule-presenter-harness`), repositories, Room, DataStore or networking.
- No Koin, Compose, Molecule, Turbine, Konsist, detekt or ktlint in any new module. `:core:ui` gets
  no Compose yet; the slice that first needs Compose there adds it.
- No `build-logic` included build or convention plugins (see Decision 2).
- No change to `MainActivity`, `PlaceholderScreen`, resources, the manifest, `init.sh`,
  `gradle.properties`, the wrapper or `gradle-daemon-jvm.properties`.
- No move of `:app` sources from `src/main/java` to `src/main/kotlin`.

## Job Story

When a later slice adds domain types, tokens, presenter contracts or a repository,
I want the target `:core:*` module to already exist with its plugin and dependencies set,
so I can add code in the right place without deciding the build structure again.

## Users And Permissions

- Any user: the app behaves exactly as before. No roles, no writes, no mutations; D-13 does not
  apply.

## Acceptance Scenarios

### Scenario 1: The gate is green with four modules

Given the repository after this slice
When `CI=true ./init.sh` runs
Then it exits 0, `build` and `check` run for all four modules (including the `:core:model` JVM
test and the `:app` wiring test), and Konsist, detekt and ktlint still print `NOT WIRED YET`.

### Scenario 2: Every module is included

Given `settings.gradle.kts`
When it is read
Then it includes `:app`, `:core:model`, `:core:ui` and `:core:data`, and nothing else.

### Scenario 3: Dependency direction is as specified

Given the Gradle dependency reports
When `:core:model`, `:core:ui`, `:core:data` and `:app` are inspected
Then `:core:model` has no project dependency and no Android/androidx artifact; `:core:ui` and
`:core:data` each have exactly one project dependency, `:core:model`; `:app` has all three.

### Scenario 4: `:core:model` cannot use Android

Given `:core:model` is built with the Kotlin JVM plugin
When its source imports any `android.*` type
Then compilation fails (no Android SDK on its classpath). Demonstrate once, then revert.

### Scenario 5: The app still launches

Given a debug build installed on the Pixel 5
When the app is launched
Then the same `#111318` screen with the centered `BB Blues Jam` label appears, with no crash.

## Repository Research

### Files Inspected

- `settings.gradle.kts` — foojay 1.0.0, `FAIL_ON_PROJECT_REPOS`, includes only `:app`.
- `build.gradle.kts` (root) — `android-application` and `kotlin-compose`, both `apply false`.
- `app/build.gradle.kts` — AGP 9 DSL, `compileSdk { version = release(37) }`, `minSdk = 24`,
  `targetSdk = 37`, Java 11 `compileOptions`, `buildFeatures.compose`, no Kotlin plugin (AGP 9
  built-in Kotlin).
- `gradle/libs.versions.toml` — agp 9.4.1, kotlin 2.2.10, Compose BOM 2025.09.00; plugins
  `android-application` and `kotlin-compose` only.
- `gradle.properties` (configuration cache on), `gradle/wrapper/gradle-wrapper.properties`
  (Gradle 9.6.0), `gradle/gradle-daemon-jvm.properties` (daemon on Java 25 via toolchain URLs; the
  shell's `java` is 16, which is fine because the launcher picks the daemon JVM).
- `init.sh` — `build`, `check`, then greps `tasks --all` for the three tools.
- `app/src/**` — `MainActivity.kt`, `PlaceholderScreen.kt`, `ExampleUnitTest.kt`,
  `ExampleInstrumentedTest.kt`, all under `java/com/bbbjam/`.
- `docs/specs/gradle-kotlin-compose-baseline.md` including Accepted Deviations,
  `.claude/skills/architecture/SKILL.md`, `AGENTS.md`, `PROGRESS.md`, `feature_list.json`,
  `docs/technical-discovery.md` (no module-level build conventions recorded there).
- AGP 9.4.1 jar: ships `com.android.library` (also `com.android.kotlin.multiplatform.library`,
  `com.android.fused-library`). KGP 2.2.10 jar (already on the classpath through AGP): ships
  `org.jetbrains.kotlin.jvm`.

### Prototype (verified, not assumed)

A throwaway copy of `HEAD` in the session scratchpad was given exactly the setup in Technical
Approach. Results, 28 September 2026:

- `./gradlew build` BUILD SUCCESSFUL (263 tasks), configuration cache stored, no `w:`/`e:` compiler
  lines on a `--rerun-tasks` run. `:core:model:test` and `:app:testDebugUnitTest` (wiring test) ran.
- `buildEnvironment`: `com.android.library.gradle.plugin:9.4.1` and
  `org.jetbrains.kotlin.jvm.gradle.plugin:2.2.10`, the latter resolving to the same
  `kotlin-gradle-plugin:2.2.10` AGP already uses. No second KGP version.
- Dependency reports matched Scenario 3; `:core:model` runtime classpath is only
  `kotlin-stdlib:2.2.10`.
- **Negative check:** removing either `kotlin-jvm` or `android-library` from the root `plugins {}`
  block fails configuration with "the plugin is already on the classpath with an unknown version".
  Both must be declared at the root with `apply false`.
- Not prototyped: device launch, and the Scenario 4 android-import failure.

### Existing Patterns To Follow

- Versions and plugins only through `libs.versions.toml`, applied with `alias(...)`, declared at
  the root with `apply false`.
- AGP 9 built-in Kotlin for Android modules: never apply `org.jetbrains.kotlin.android`.
- Package root `com.bbbjam`; Java 11 source/target.

## Technical Approach

### Decision 1: plugins per module

| Module | Plugin | Why |
|---|---|---|
| `:core:model` | `org.jetbrains.kotlin.jvm` (catalog `kotlin-jvm`, `version.ref = "kotlin"`) | The architecture requires no Android. AGP's built-in Kotlin only applies inside Android modules, so it does not conflict; the plugin comes from the same KGP 2.2.10 artifact AGP already loads, so no version skew is possible while `kotlin` stays equal to AGP's KGP. |
| `:core:ui`, `:core:data` | `com.android.library` (catalog `android-library`, `version.ref = "agp"`) | Both will need Android (Compose, Room, DataStore). Built-in Kotlin compiles their Kotlin with no extra plugin. `com.android.kotlin.multiplatform.library` is rejected: the project is Android-only. |
| `:app` | unchanged (`android-application`, `kotlin-compose`) | — |

`:core:model` must pin its JVM target to 11 so `:app` (Java 11) can consume it:
`java { sourceCompatibility/targetCompatibility = JavaVersion.VERSION_11 }` and
`kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_11 } }`. Do not use `jvmToolchain(11)`: it
would make foojay download a JDK 11 for no benefit.

### Decision 2: no convention plugins yet

Plain per-module `build.gradle.kts` files. With two Android libraries and one JVM module, the
duplication is about ten lines per library; a `build-logic` included build would cost more (a
`kotlin-dsl` build on Gradle's embedded Kotlin 2.3.21, AGP as a compile dependency, its own
catalog access, configuration-cache surface) than it saves, and every later slice would inherit
that machinery. Consistency is kept instead by:

- `compileSdk = "37"` and `minSdk = "24"` moved into `[versions]` of the catalog and read by `:app`,
  `:core:ui` and `:core:data` (`libs.versions.minSdk.get().toInt()`). Only one place changes an SDK
  level. `targetSdk` stays in `:app` only (application concern).
- Java 11 stays a literal `JavaVersion.VERSION_11`/`JvmTarget.JVM_11` in each file (four places).
  A mismatch here fails loudly at resolution, unlike an SDK drift.

Revisit trigger, to record in the architecture skill: introduce `build-logic` convention plugins
when the first `:feature:*` module is added or when `detekt-ktlint-gate` needs to configure every
module, whichever comes first.

### Decision 3: dependency declarations

- `:core:ui` and `:core:data`: `api(project(":core:model"))`. Their public contracts (repository
  return types, `UiModel`s) will expose domain types, which is what `api` is for.
- `:app`: `implementation(project(":core:model"))`, `(":core:ui")`, `(":core:data")`, all three
  explicit, per the architecture table.
- Use `project(":…")`, not type-safe project accessors (no feature preview flag needed).

### Namespaces, packages and source dirs

| Module | Android `namespace` / Kotlin package | Source dir |
|---|---|---|
| `:core:model` | package `com.bbbjam.core.model` (no namespace; not Android) | `core/model/src/main/kotlin/…` |
| `:core:ui` | `com.bbbjam.core.ui` | `core/ui/src/main/kotlin/…` |
| `:core:data` | `com.bbbjam.core.data` | `core/data/src/main/kotlin/…` |

New modules use `src/main/kotlin` (the Kotlin JVM default, also accepted by AGP 9 built-in Kotlin;
prototype-verified). `:app` keeps `src/main/java`.

### Placeholder content

Each module gets one public object, e.g.
`object CoreModelMarker { const val PATH: String = ":core:model" }` (likewise `CoreUiMarker`,
`CoreDataMarker`). No domain meaning, no Android types. Libraries need no `AndroidManifest.xml`
(namespace comes from the DSL).

- `core/model/src/test/kotlin/com/bbbjam/core/model/CoreModelMarkerTest.kt`: JUnit 4 test that
  asserts `PATH`. Proves the JVM test task runs under `check`. Add `testImplementation(libs.junit)`.
- `app/src/test/java/com/bbbjam/ModuleWiringTest.kt`: imports the three markers and asserts their
  paths. It only compiles if `:app` depends on all three, so it is the compile-time proof.

These markers are scaffolding. Each is deleted by the first slice that adds real content to its
module (`domain-model-types`, `molecule-presenter-harness` or `design-tokens-theme`, the first
repository slice), which also drops its line from `ModuleWiringTest`.

## Expected File Changes

- `settings.gradle.kts` — modify; include `:core:model`, `:core:ui`, `:core:data`.
- `build.gradle.kts` — modify; add `android-library` and `kotlin-jvm` with `apply false`.
- `gradle/libs.versions.toml` — modify; `compileSdk`, `minSdk` versions; `android-library`,
  `kotlin-jvm` plugins.
- `app/build.gradle.kts` — modify; SDK levels from the catalog, three project dependencies.
- `core/model/build.gradle.kts`, `core/ui/build.gradle.kts`, `core/data/build.gradle.kts` — create.
- `core/{model,ui,data}/src/main/kotlin/com/bbbjam/core/{model,ui,data}/Core*Marker.kt` — create.
- `core/model/src/test/kotlin/com/bbbjam/core/model/CoreModelMarkerTest.kt` — create.
- `app/src/test/java/com/bbbjam/ModuleWiringTest.kt` — create.
- `.claude/skills/architecture/SKILL.md` — modify; see Documentation Impact.
- `feature_list.json`, `PROGRESS.md` — status and evidence at the end.

- `core/model/.gitignore`, `core/ui/.gitignore`, `core/data/.gitignore` — create, each containing
  `/build`, mirroring `app/.gitignore`. The root `.gitignore` only ignores the root `/build`, so
  without these the module build outputs would show as untracked. Confirm `git status` is clean of
  `core/*/build` after the build.

## Visual Design Impact

- UI involved: no. The placeholder screen is unchanged; `DESIGN.md` not applicable.

## Durable Documentation Impact

- `ARCHITECTURE.md`, `CONSTRAINTS.md`: do not exist; not needed. The architecture lives in the
  skill.
- `.claude/skills/architecture/SKILL.md`: **update**. Add a short "Build Conventions" section:
  plugin per module kind (JVM → `kotlin-jvm`; Android library → `android-library`, never
  `org.jetbrains.kotlin.android`); every plugin declared at the root `apply false` (with the
  "unknown version" failure as the reason); SDK levels from the catalog; Java 11; `src/main/kotlin`
  for new modules; `api(project(":core:model"))` from `:core:ui`/`:core:data`; no `build-logic`
  yet and its revisit trigger.
- `AGENTS.md`: not needed; workflow unchanged.
- `PROGRESS.md`: update "What exists" and add a session entry.
- `docs/technical-discovery.md`: not needed; the catalog and skill are the source of truth.

## Implementation Plan

1. `CI=true ./init.sh` on the untouched tree; record the result.
2. Catalog, root build file, `settings.gradle.kts`; then the three module build files.
3. Marker objects, the `:core:model` test, then `:app` dependencies and `ModuleWiringTest`.
4. `./gradlew build`, then `CI=true ./init.sh`, then the dependency reports.
5. Scenario 4 negative check (temporary `import android.content.Context` in `CoreModelMarker.kt`,
   `./gradlew :core:model:compileKotlin` fails, revert).
6. Install and launch on the Pixel 5.
7. Update the architecture skill, `feature_list.json` (`passing`) and `PROGRESS.md`.

## Implementation Tasks

- [ ] Baseline gate recorded.
- [ ] Catalog: `compileSdk = "37"`, `minSdk = "24"`, plugins `android-library`, `kotlin-jvm`.
- [ ] Root `plugins {}`: both new plugins `apply false`.
- [ ] `settings.gradle.kts`: three `include`s.
- [ ] `:core:model` build file: `kotlin-jvm`, Java/JVM target 11, `testImplementation(libs.junit)`.
- [ ] `:core:ui`, `:core:data` build files: `android-library`, namespace, catalog SDK levels, Java
      11, `api(project(":core:model"))`.
- [ ] `:app`: catalog SDK levels, three `implementation(project(...))`.
- [ ] Three markers, `CoreModelMarkerTest`, `ModuleWiringTest`.
- [ ] Gate, reports, negative check, device launch.
- [ ] Architecture skill, `feature_list.json`, `PROGRESS.md`.

## Verification Plan

JVM, part of the standard gate:

- `CI=true ./init.sh` exits 0; tools still `NOT WIRED YET`.
- `core/model/build/test-results/test/TEST-com.bbbjam.core.model.CoreModelMarkerTest.xml` and
  `app/build/test-results/testDebugUnitTest/TEST-com.bbbjam.ModuleWiringTest.xml` exist with 0
  failures.
- `grep -n include settings.gradle.kts` lists exactly the four modules.

JVM, outside the gate (dependency direction; Konsist is not wired, so this is the check today):

- `./gradlew :core:model:dependencies --configuration runtimeClasspath -q`: no `project` line, only
  `kotlin-stdlib`.
- `./gradlew :core:ui:dependencies --configuration releaseRuntimeClasspath -q` and the same for
  `:core:data`: exactly one `project ':core:model'`.
- `./gradlew :app:dependencies --configuration debugRuntimeClasspath -q | grep "project '"`: shows
  `:core:model`, `:core:ui`, `:core:data`.
- `./gradlew buildEnvironment | grep -E "kotlin-gradle-plugin:|kotlin.jvm.gradle.plugin|com.android.library.gradle.plugin"`:
  KGP 2.2.10 only, library plugin 9.4.1.
- `grep -rn "org.jetbrains.kotlin.android" --include=*.kts --include=*.toml .` returns nothing.
- Scenario 4 negative check, output recorded, change reverted.

Device, manual (Pixel 5 over adb; `adb` may not be on the bash PATH, use the SDK's
`platform-tools`): `./gradlew :app:installDebug`, `adb shell am start -W com.bbbjam/.MainActivity`,
confirm the unchanged placeholder screen and an empty `adb logcat -s AndroidRuntime`.

E2E: no persistent E2E command exists; none is justified for a build-structure slice.

## Evidence To Capture

- Gate output and exit code, before and after.
- The four dependency-report excerpts and the `buildEnvironment` line.
- Test result file names and counts.
- Negative-check compiler error line.
- Device model, API level, `am start` status, logcat result. If no device, say so; Scenario 5 then
  stays unverified.

## Validator Checklist

- [ ] Exactly three new modules; no `:feature:*`, no `build-logic`.
- [ ] `:core:model` uses `kotlin-jvm` and has no Android dependency; `:core:ui`/`:core:data` use
      `android-library`; no `org.jetbrains.kotlin.android` anywhere.
- [ ] Both new plugins are declared at the root with `apply false` and come from the catalog;
      `kotlin-jvm` uses `version.ref = "kotlin"`.
- [ ] SDK levels read from the catalog in all three Android modules; Java 11 everywhere.
- [ ] Dependency reports match Scenario 3; validator reruns `CI=true ./init.sh` (exit 0).
- [ ] New modules contain only the marker objects and the one test; no domain types, tokens,
      Compose, Koin, Molecule, Konsist, detekt or ktlint.
- [ ] `MainActivity`, `PlaceholderScreen`, resources, manifest and `init.sh` untouched.
- [ ] Architecture skill has the Build Conventions section; `feature_list.json` is `passing`, not
      `accepted`; `PROGRESS.md` updated.
