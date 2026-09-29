# Feature Implementation Spec: Set up the composable presenter pattern with a Molecule test

## Source Feature

- `id`: molecule-presenter-harness
- `area`: bootstrap
- `depends_on`: module-skeleton (`accepted`)
- `status`: not_started (at planning time, 28 September 2026, HEAD `d482d5e`)
- `source`: `feature_list.json`

## Goal

The presenter contracts of D-02 (`Presenter`, `UiModel`, `UiEvent`, `EventHandler`) become real,
compiling code in `:core:ui`, and a sample presenter is covered by Molecule + Turbine tests that
run on the JVM inside `./gradlew check` with no device. `EventHandler` gets its own unit test for
the two defects the project documents (bitácora 3.5): `hashCode` inconsistent with `equals`, and
an undeclared `invoke`. Afterwards `presenter-pattern.md` is rewritten so its contracts match the
code verbatim, and the build convention every presenter module needs is recorded in the
architecture skill.

## Non-Goals

- No `:feature:*` module, no product presenter, no screen, no composable UI.
- No Koin, no domain types in `:core:model`, no repositories, no design tokens or theme
  (`design-tokens-theme`), no Compose `ui`/`foundation`/Material in `:core:ui`.
- No change to `init.sh`, `.editorconfig`, `config/detekt/detekt.yml`, the root build file,
  `ModuleIsolationTest`, `:core:data`, `:core:model`. No baseline, suppression or rule disable.
- No new Konsist rule (e.g. "presenters implement `Presenter`"): nothing product-side to check yet.

## Job Story

When a later slice writes the first real presenter (`next-jam-read-only-list`),
I want the contracts, the test libraries and the Android unit-test setup already proven,
so I can write the presenter and its Molecule test without rediscovering build traps.

## Users And Permissions

No end-user behavior. No writes, no mutations; D-13 does not apply.

## Acceptance Scenarios

### Scenario 1: Gate green with the new tests
Given the tree after this slice
When `CI=true ./init.sh` runs
Then it exits 0, prints `konsist: wired`, `detekt: wired`, `ktlint: wired`, and
`core/ui/build/test-results/testDebugUnitTest/` holds `EventHandlerTest` (6 tests) and
`SamplePresenterTest` (3 tests), 0 failures; `ModuleIsolationTest` still 8/8.

### Scenario 2: The presenter test asserts transitions, not a first emission
Given `SamplePresenter` with its toggle turned into a no-op (`expanded = expanded`)
When `./gradlew :core:ui:testDebugUnitTest` runs
Then `toggle expanded event changes the state` and `local state survives a new value from the
source` fail with Turbine's `No value produced in 3s`. Restore; green.

### Scenario 3: The equals/hashCode defect is caught
Given `EventHandler.hashCode()` changed to `handle.hashCode()` (the article's bug)
When the unit tests run
Then three `EventHandlerTest` tests fail (unkeyed hash, key hash, hash-set size). Restore; green.

### Scenario 4: The missing-invoke defect is caught
Given the `operator fun invoke` line removed from `EventHandler`
When `:core:ui:compileDebugUnitTestKotlin` runs
Then compilation fails (`Expression 'events' … cannot be invoked as a function`). Restore; green.

### Scenario 5: No device
The whole gate runs with no emulator or device attached; no Robolectric is added.

## Repository Research

### Files Inspected
`AGENTS.md`, `PROGRESS.md`, `feature_list.json` (this entry and its notes),
`.claude/skills/architecture/SKILL.md`, `.claude/skills/architecture/references/presenter-pattern.md`,
`docs/specs/detekt-ktlint-gate.md` (format, prior decisions), `docs/technical-discovery.md` §Testing,
bitácora §3.5, `gradle/libs.versions.toml`, root/`app`/`core/*` `build.gradle.kts`,
`settings.gradle.kts`, `gradle.properties`, `core/ui/.../CoreUiMarker.kt`,
`app/src/test/java/com/bbbjam/ModuleWiringTest.kt`, `ModuleIsolationTest.kt`,
`konsist-test/build.gradle.kts`, `.editorconfig`, `config/detekt/detekt.yml`, `init.sh`.
Maven metadata (28 Sep 2026): molecule-runtime **2.2.0** latest (Sep 2025); turbine **1.2.1**
latest; kotlinx-coroutines-test latest 1.11.0. Gradle module files: `molecule-runtime-android`
2.2.0 requires `androidx.compose.runtime:runtime` 1.9.1, coroutines-core 1.10.2, kotlin-stdlib
2.2.20; turbine 1.2.1 requires coroutines(-test) 1.10.2. Compose BOM 2025.09.00 pins runtime 1.9.1.
DESIGN.md not read: no UI in this slice.

### Prototype (verified, not assumed)
Clone of `d482d5e` in the session scratchpad (`local.properties` copied), with exactly the changes
in Expected File Changes. Results:
- **First run failed**: all three Molecule tests got `RuntimeException: Method beginSection in
  android.os.Trace not mocked` from `androidx.compose.runtime.internal.Trace` inside
  `ComposerImpl.doCompose`. `:core:ui` is an Android library, so Gradle resolves
  `molecule-runtime-android` and the Android Compose runtime, which calls `android.os.Trace`
  against the stub `android.jar`. Fixed with `testOptions.unitTests.isReturnDefaultValues = true`
  (Decision 4). Then 9/9 pass.
- Scenarios 2, 3, 4 reproduced exactly as written.
- `CI=true ./init.sh`: exit 0, three `wired` lines. Warm no-op runs 6.1–6.6 s; after a test-file
  edit 9.1 s. Only `testDebugUnitTest` runs for `:core:ui` in `check` (no release unit-test task
  results were produced).
- ktlint (android_studio `class-signature`) rejected the reference's multi-line
  `EventHandler(…)` and `SamplePresenter(…)`/`Data(…)` signatures because they fit in 120
  columns; `./gradlew :core:ui:ktlintFormat` put them on one line. detekt: 0 findings (main+test);
  `@Immutable`, the explicit `equals`/`hashCode`, the empty `UiEvent` interface and backtick test
  names raise nothing. Android lint on `:core:ui`: "No issues found".
- `--warning-mode all --rerun-tasks` on `:core:ui` compile + lint: no compiler warning, no Gradle
  deprecation. The unit-test classpath resolves stdlib 2.2.10→2.2.20 and coroutines 1.10.2
  (Molecule's requirements); main classpaths are unchanged (stdlib 2.2.10; `:app` coroutines 1.9.0).
- Removing `buildFeatures.compose` from `:core:ui` still compiled and passed: the Compose compiler
  plugin alone transforms main and test sources under AGP 9 built-in Kotlin. Kept anyway (Decision 2).

## Technical Approach

### Decision 1: versions
Catalog `[versions]`: `molecule = "2.2.0"`, `turbine = "1.2.1"`, `kotlinxCoroutines = "1.10.2"`.
`[libraries]`: `androidx-compose-runtime = { group = "androidx.compose.runtime", name = "runtime" }`
(BOM-managed), `molecule-runtime = { group = "app.cash.molecule", name = "molecule-runtime", version.ref = "molecule" }`,
`turbine = { group = "app.cash.turbine", name = "turbine", version.ref = "turbine" }`,
`kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "kotlinxCoroutines" }`.
Molecule 2.2.0 is the latest and needs exactly the runtime the BOM already pins (1.9.1); its
stdlib 2.2.20 requirement only reaches the test classpath and compiles cleanly with Kotlin 2.2.10.
Coroutines-test 1.10.2, not 1.11.0: it must equal the coroutines-core that Molecule and Turbine
pull (1.10.2), and 1.11.0 would also drag core to a version built with a newer Kotlin. The API is
`moleculeFlow(RecompositionMode.Immediate) { … }` (not `RecompositionClock`), collected with
Turbine's `test {}` inside `runTest`.

### Decision 2: `:core:ui` build — runtime only, as `api`
`core/ui/build.gradle.kts` becomes (this exact block was proven):
```kotlin
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}
android {
    // namespace, compileSdk, defaultConfig, compileOptions unchanged
    buildFeatures {
        compose = true
    }
    // The Compose runtime calls android.os.Trace while composing; Molecule tests run on the JVM
    // against the stub android.jar, whose methods throw unless they return defaults.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}
dependencies {
    api(project(":core:model"))
    // Presenter contracts expose @Composable and @Immutable, so consumers need the runtime too.
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.runtime)
    testImplementation(libs.junit)
    testImplementation(libs.molecule.runtime)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
}
```
Runtime only: `@Composable`, `@Immutable`, `remember`, `mutableStateOf` and `collectAsState` all
live in `androidx.compose.runtime`. `ui`/`foundation` arrive with `design-tokens-theme`. `api`
because the public `Presenter` signature carries `@Composable`, so every consumer compiles against
the runtime; `api(platform(bom))` aligns feature modules on the same Compose versions.
`buildFeatures.compose = true` is kept as the feature notes ask and for parity with `:app`, even
though the prototype shows it is not required for compilation. Molecule is test-only: presenters
never depend on it; only tests drive them.

### Decision 3: the sample lives in `:core:ui`'s test source set
`core/ui/src/test/kotlin/com/bbbjam/core/ui/presenter/SamplePresenter.kt` — not product code (not
shipped, not in `src/main`), not a `:feature:*` module, and needs no domain type. The next-jam
example cannot compile yet (`JamSong`, `SetlistRepository`, `AdminSession` don't exist) and building
fakes of them would pre-empt `domain-model-types` and the repository slices. The sample still
exercises every moving part of the reference: a sealed `UiModel` with `Loading`/`Data`, a
constructor dependency standing in for a repository flow (`Flow<String>` read with
`collectAsState(initial = null)`), local state in `remember`, a sealed `Event : UiEvent` handled by
an `EventHandler`, and whole-model comparison with `EventHandler {}`. Konsist already covers test
sources (package root, core import allowlist); a test-only sample also keeps `:core:ui` main free
of example code.

Sample (post-`ktlintFormat` form, proven):
```kotlin
sealed interface SampleUiModel : UiModel {
    data object Loading : SampleUiModel

    data class Data(val title: String, val expanded: Boolean, val events: EventHandler<Event>) : SampleUiModel {
        sealed interface Event : UiEvent {
            data object ToggleExpanded : Event
        }
    }
}

class SamplePresenter(private val titles: Flow<String>) : Presenter<SampleUiModel, Unit> {
    @Composable
    override fun present(params: Unit): SampleUiModel {
        val title by titles.collectAsState(initial = null)
        var expanded by remember { mutableStateOf(false) }

        val current = title ?: return SampleUiModel.Loading
        return SampleUiModel.Data(
            title = current,
            expanded = expanded,
            events = EventHandler { event ->
                when (event) {
                    SampleUiModel.Data.Event.ToggleExpanded -> expanded = !expanded
                }
            },
        )
    }
}
```
with a KDoc saying it is a test-only sample with no domain types.

`SamplePresenterTest` (source `MutableSharedFlow<String>(replay = 1)`, helper
`data(title, expanded) = SampleUiModel.Data(title, expanded, EventHandler {})`):
1. `emits loading, then data once the source emits` — `Loading`, emit, then `data(…, false)`.
2. `toggle expanded event changes the state` — `Loading`, `Data(expanded=false)`; invoke
   `ToggleExpanded` → `data(…, true)`; invoke again on the *first* model's handler → `data(…, false)`
   (proves a handler writes through state, not a stale copy).
3. `local state survives a new value from the source` — toggle, then emit a new title →
   `data("The Thrill Is Gone", expanded = true)`.

### Decision 4: `isReturnDefaultValues` instead of Robolectric or a JVM module
Presenters must live in Android-library modules (`:feature:*`), so the Android Compose runtime is
unavoidable in their unit tests. Robolectric adds seconds per run and a large dependency for one
stubbed `Trace` call; forcing a JVM Compose artifact on an Android variant fights Gradle variant
resolution. The cost: in `:core:ui` unit tests, any stubbed Android method returns a default
instead of throwing, so an accidental Android call in a presenter would not fail loudly. Presenters
must not call Android anyway, and the flag is local to unit tests. **Every future presenter module
needs the same line**; record it in the skill (see Documentation).

### Decision 5: contracts in `:core:ui`, package `com.bbbjam.core.ui.presenter`
One file per type in `core/ui/src/main/kotlin/com/bbbjam/core/ui/presenter/`: `UiModel.kt`
(`@Immutable interface UiModel`), `UiEvent.kt` (`interface UiEvent`), `Presenter.kt`
(`interface Presenter<Model : UiModel, Params> { @Composable fun present(params: Params): Model }`),
and `EventHandler.kt`:
```kotlin
@Immutable
class EventHandler<E : UiEvent>(private val key: Any? = null, val handle: (E) -> Unit) {
    operator fun invoke(event: E) = handle(event)

    override fun equals(other: Any?): Boolean = other is EventHandler<*> && key == other.key

    override fun hashCode(): Int = key?.hashCode() ?: 0

    override fun toString(): String = "EventHandler(key=$key)"
}
```
Each with a short KDoc (the reference's text for `EventHandler`, adding that `hashCode` derives from
the same key). `@Immutable` comes from the runtime dependency; nothing else is needed.

### Decision 6: `EventHandlerTest` (6 tests, JUnit 4, private sealed `Event : UiEvent` with `Ping` and `Pick(index)`)
Unkeyed handlers with different lambdas are equal with equal hash codes; same key → equal, same
hash; different key or keyed-vs-unkeyed → not equal; a `hashSetOf` of 2 unkeyed + 2×`key = 1` +
`key = 2` has size 3; a handler is not equal to its own lambda nor to `null`; `invoke` forwards
events both as `handler(e)` and `handler.invoke(e)`; `toString()` is `EventHandler(key=row-1)`.

### Decision 7: remove the placeholder
Delete `core/ui/src/main/kotlin/com/bbbjam/core/ui/CoreUiMarker.kt` and its import and assertion in
`ModuleWiringTest`; reword that test's KDoc to say it proves `:core:model` and `:core:data` wiring
(the `:core:ui` line went with its marker; `:app` will use `:core:ui` for real from
`design-tokens-theme`). Konsist `scope-sanity` still passes because `:core:ui` now has real files.

## Expected File Changes

- `gradle/libs.versions.toml` — modify (Decision 1).
- `core/ui/build.gradle.kts` — modify (Decision 2).
- `core/ui/src/main/kotlin/com/bbbjam/core/ui/presenter/{UiModel,UiEvent,Presenter,EventHandler}.kt` — create.
- `core/ui/src/test/kotlin/com/bbbjam/core/ui/presenter/{SamplePresenter,SamplePresenterTest,EventHandlerTest}.kt` — create.
- `core/ui/src/main/kotlin/com/bbbjam/core/ui/CoreUiMarker.kt` — delete.
- `app/src/test/java/com/bbbjam/ModuleWiringTest.kt` — modify (Decision 7).
- `.claude/skills/architecture/references/presenter-pattern.md`, `.claude/skills/architecture/SKILL.md`,
  `docs/technical-discovery.md`, `PROGRESS.md`, `feature_list.json` — see below.

## Visual Design Impact

- UI involved: no. Nothing on screen changes; no device check needed.

## Durable Documentation Impact

- `presenter-pattern.md` — **update**, per its own instruction:
  1. Replace the "reference sketch, not compiled code" paragraph: the contracts are real code in
     `core/ui/src/main/kotlin/com/bbbjam/core/ui/presenter/`, and the compiled, tested example is
     `SamplePresenter`/`SamplePresenterTest` in `core/ui/src/test/…`; the code wins over this file.
     The next-jam example stays an **illustrative sketch** until `next-jam-read-only-list` lands
     (it uses types that do not exist yet); label it so in its heading.
  2. Contracts block: replace with the four files' code verbatim (including the one-line
     `EventHandler` signature ktlint enforces), with the file path of each.
  3. "What Changed" table: add a row — "Articles run presenter tests without Android stubs |
     `unitTests.isReturnDefaultValues = true` in each presenter module | The Android Compose
     runtime calls `android.os.Trace`, which throws in JVM unit tests".
  4. Tests section: add a short "Module setup" list (compose plugin, `:core:ui` dependency, the four
     `testImplementation` libs, `isReturnDefaultValues`), and include `SamplePresenterTest`'s
     toggle test as the compiled example ahead of the illustrative next-jam tests.
- `SKILL.md` — **update**: Build Conventions gets a bullet "A module with presenters applies
  `kotlin-compose` with `buildFeatures.compose = true`, gets the Compose runtime through `:core:ui`,
  adds `testImplementation` junit, molecule-runtime, turbine, kotlinx-coroutines-test, and sets
  `testOptions.unitTests.isReturnDefaultValues = true`"; the Presenter Pattern section names the
  real package `com.bbbjam.core.ui.presenter` and the sample test as the compiled example.
- `docs/technical-discovery.md` §Testing — **update** the Molecule line: wired, Molecule 2.2.0,
  Turbine 1.2.1, coroutines-test 1.10.2, run by `./gradlew check`.
- `ARCHITECTURE.md`/`CONSTRAINTS.md`: do not exist; not needed. `AGENTS.md`: not needed.
- `PROGRESS.md` ("What exists", test list, session entry) and `feature_list.json` (`passing`, evidence).

## Implementation Plan

1. `CI=true ./init.sh` on the untouched tree; record exit code and status lines.
2. Catalog and `core/ui/build.gradle.kts` (Decisions 1–2).
3. Contracts (Decision 5); delete the marker and update `ModuleWiringTest` (Decision 7).
4. `EventHandlerTest`, `SamplePresenter`, `SamplePresenterTest` (Decisions 3, 6).
5. `./gradlew :core:ui:ktlintFormat`, then `./gradlew check --continue`; fix anything flagged in code,
   never in config.
6. Scenarios 2–4 as negative controls, restoring each file by copy with SHA-1 proof.
7. `CI=true ./init.sh` (Scenario 1); docs; `feature_list.json` → `passing`; `PROGRESS.md`.

## Implementation Tasks

- [ ] Baseline gate recorded.
- [ ] Catalog entries and `:core:ui` build file exactly as Decisions 1–2.
- [ ] Four contract files; marker deleted; `ModuleWiringTest` updated.
- [ ] Three test-source files; 9 tests green.
- [ ] Negative controls (Scenarios 2–4) fail as described; files restored.
- [ ] Gate green with three `wired`; reference, skill, technical discovery, progress, feature list.

## Verification Plan

All on the JVM inside the standard gate; no emulator, no E2E harness exists.
- `CI=true ./init.sh` exit 0 with `konsist: wired`, `detekt: wired`, `ktlint: wired`.
- Test counts from `core/ui/build/test-results/testDebugUnitTest/*.xml`: `EventHandlerTest` 6,
  `SamplePresenterTest` 3; `konsist-test` 8; `:app` `ModuleWiringTest` 1, `ExampleUnitTest` 1.
- Scenarios 2–4 with `./gradlew :core:ui:testDebugUnitTest` (or `compileDebugUnitTestKotlin`).
- `git ls-files | grep -i baseline` empty; `git diff` shows no change to `.editorconfig`,
  `config/detekt/detekt.yml`, the root build file or `init.sh`.
- `./gradlew :core:ui:dependencies --configuration debugUnitTestRuntimeClasspath` shows
  molecule-runtime 2.2.0, turbine 1.2.1, coroutines-test 1.10.2, compose runtime 1.9.1.

## Evidence To Capture

Short lines in `feature_list.json` `evidence` and the PROGRESS.md session entry: versions; baseline
and final `init.sh` exit codes and status lines; test counts per class; each negative control with
its failure message and restore proof; the `android.os.Trace` finding and its fix.

## Validator Checklist

- [ ] Contracts match Decision 5 and `presenter-pattern.md` shows the same code verbatim.
- [ ] `equals` and `hashCode` both derive from `key`; `invoke` is a declared `operator`.
- [ ] Sample and its tests live only in `core/ui/src/test`; no `:feature:*`, Koin, domain type,
      repository or token added; no Compose `ui`/`foundation` in `:core:ui`.
- [ ] At least one test asserts a transition after an event (validator reruns Scenario 2).
- [ ] Validator reruns `CI=true ./init.sh` (exit 0, three `wired`) and Scenario 3.
- [ ] No quality-tool config, baseline or suppression changed or added.
- [ ] Skill and technical-discovery updated; `feature_list.json` is `passing`, not `accepted`.
