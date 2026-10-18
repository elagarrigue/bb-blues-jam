# Spec: shared-test-fixtures

Feature entry: `feature_list.json` id `shared-test-fixtures`, area `build`, depends on
`setlist-mutation-engine` (accepted). Origin: code review of 17 October 2026, finding 5.

## Goal

The test fakes that several modules need live once, in a shared test-fixtures location that the
consumers take as a test dependency. The duplicates are deleted. No production code, no test
assertion and no test count changes.

## Non-goals

- Feature-specific fakes stay where they are (see the inventory). No new fake is written.
- No change to what any test asserts; edits to tests are limited to imports, renamed fake classes and
  the property names a unified fake exposes (listed below).
- No new Konsist rule and no new module (see Decision 1).
- No change in `:konsist-test`, unless the fallback in Decision 1 is forced (then minimal, failing
  first).

## Repository research (inspected on 17 October 2026)

Files read: `feature_list.json` entry, `settings.gradle.kts`, `konsist-test/build.gradle.kts`,
`ModuleIsolationTest.kt` (lines 56-65, 226-320, 400-410, 480-490), `core/data/build.gradle.kts`,
`feature/info/build.gradle.kts`, the convention plugins list, `gradle.properties`, the fakes below.

Inventory of fakes (Fake* classes in test sources, found by grep on `src/test*`):

| Fake | Where | Verdict |
|---|---|---|
| `FakeJamsRepository` (counting, single-flight, `hold`, `fails`) | next-jam and past-jams, byte-identical apart from the package line | **Move** to fixtures |
| `FakeAdminSession` | next-jam `AdminFakes.kt` (10 lines, `logIn` = Unavailable), past-jams `AdminFakes.kt` (same), info `FakeAdminSession.kt` (`answer` deferred, `logIns`, `logOuts`), app `DebugAdminSessionTest` (private; `logInCalls`, `logOutCalls`, answer WrongPassphrase) | **Move**, one class (shape below) |
| `FakeSetlistRepository` | next-jam `AdminFakes.kt` (12-contract union, recording, imports `NextJamSetlistWrites`/`SongOverlaySources` helpers in the same file); app `AppModuleBindingsTest` (internal, outcomes plus `List<Any?>` call logs) | **Stay** (one consumer each, different shapes). Rename app's to `BindingsSetlistRepository` to end the name clash |
| `FakeJamStatusRepository`, `FakePublishRepository`, `FakeJamEditRepository`, `FakeJamCreator` | past-jams `AdminFakes.kt` (narrow contracts) | **Stay** (single consumer) |
| `FakeJamCreator` | assistant `ApplyFixtures.kt` (scripted outcome, `CallLog`) | **Stay**, rename to `ScriptedJamCreator` (name clash with past-jams) |
| `FakeJamsRepository` | assistant `ApplyFixtures.kt` (settable `snapshot`, `refreshes`) | **Stay**, rename to `SettableJamsRepository` (different semantics from the moved one) |
| `FakeSetlistSongAdder/Remover/Mover`, `FakeSetlistAssistant` | assistant | Stay (feature-specific) |
| `FakeCatalogRepository` | next-jam `AddSongPresenterTest` (nested) | Stay |
| `FakeLinkOpener` | info (private) | Stay |
| `FakeCatalog`, `FakeJams(past, upcoming)` | core/data `AssistantFakes.kt` (internal) | Stay; rename `FakeJams` to `FakePastJams` (clash with the two below) |
| `FakeJams(vararg snapshots)` / `FakeJams` (empty flow) | app `DemoUpcomingJamRepositoryTest`, `DebugAdminSessionTest` (private) | Stay; rename to `FakeSnapshotJams` and `FakeEmptyJams` |
| `FakePost` (x3: `AdminWriterTest`, `DefaultAdminSessionTest` identical; `DefaultJamsRepositoryTest` adds `answer` var and `actions()`) | core/data | **Dedupe inside `:core:data` test sources** (internal types, one consumer module): one `FakePost` in `core/data/src/test/kotlin/.../FakePost.kt` with `var answer` and `actions()`; the three tests use it |
| `FakeTransport` (x2: `DefaultCatalogRepositoryTest`, `DefaultJamsRepositoryTest`, identical) | core/data | Same: one shared file in `core/data` test sources |

`AppsScriptPostTransport`/`AppsScriptTransport` are `internal` to `:core:data`, so `FakePost` and
`FakeTransport` must not go to the fixtures (other modules cannot use them anyway).

## Decision 1: mechanism

**Choose AGP `testFixtures` in `:core:data`.** Reasons: the fakes implement `:core:data` contracts
(`JamsRepository`, `AdminSession`), so they sit beside what they fake and a contract change breaks
the fixtures at compile time in the module that changed; no new module, convention plugin or
`.gitignore`; and the Konsist build-file rules need no amendment, which I checked by reading them:

- `build-file-project-deps` (`isAllowedProjectDependency`): a feature may depend on any `:core:*`;
  `testImplementation(testFixtures(project(":core:data")))` is matched by `PROJECT_DEPENDENCY`
  as `:core:data` and passes. `:core:data`'s own file names no new project.
- `build-file-applies-convention`: `:core:data` keeps `bluesjam.android.data`; the new lines are an
  `android { testFixtures { enable = true } }` block and dependency lines, none in `OWNED_SETTING`
  (`buildFeatures`, `isReturnDefaultValues`, SDK and Java settings...). Put the block in
  `core/data/build.gradle.kts`, not in a convention (only this module uses it).
- `package-under-module-root`: fixtures live in `core/data/src/testFixtures/kotlin/com/bbbjam/core/data/testing/`,
  package `com.bbbjam.core.data.testing`, under `com.bbbjam.core.data`.
- Other rules apply to the fixtures unchanged (core import allowlist, data-libraries, ViewModel,
  colour/dp rules); the fakes use only `:core:model`, `:core:data`, coroutines and `java.time`.

**Unverified assumptions (the implementer proves them first, step 1):** AGP 9.4.1 with built-in
Kotlin supports `android { testFixtures { enable = true } }` and compiles `src/testFixtures/kotlin`
(the flag in older AGP was `android.experimental.enableTestFixtures=true` in `gradle.properties`;
`gradle.properties` currently has no `android.*` entries); unit tests of `:core:data` see its
fixtures; Konsist's `scopeFromProject` includes `src/testFixtures`; detekt, ktlint and lint run
over it.

**Fallback, only if step 1 shows testFixtures unusable** (record the error in evidence): a
`:core:testing` Android library (`bluesjam.android.library`, namespace `com.bbbjam.core.testing`, own
`.gitignore` with `/build`, `include(":core:testing")`, `api(project(":core:data"))`). It forces two
amendments, each shown failing first: `isAllowedProjectDependency` (`core/testing` may add
`:core:data`) and the core import rule at `ModuleIsolationTest` line ~87 (`core/testing` may import
`com.bbbjam.core.data`). A top-level `:testing` module is rejected: it would pass only because the
path-based rules skip it, silently. Stop and ask before taking the fallback if the amendment is
larger than those two lines.

## Fixture shapes

Package `com.bbbjam.core.data.testing`, files `FakeJamsRepository.kt`, `FakeAdminSession.kt`.

- `FakeJamsRepository`: the current next-jam class verbatim (public).
- `FakeAdminSession(isAdmin: Boolean = false)`: `val isAdmin: MutableStateFlow<Boolean>`,
  `val logIns`, `var logOuts` (private set), `var answer = CompletableDeferred<LoginOutcome>()`;
  `logIn` records, awaits `answer`, flips `isAdmin` on `Success`; `logOut` counts and sets false.
  This is info's class (its tests need the pending default). next-jam/past-jams never call `logIn`
  (checked: only `SongDetailAdminTest` line 162 has its own anonymous object, which stays). In app's
  `DebugAdminSessionTest` rename `logInCalls`/`logOutCalls` references to `logIns`/`logOuts` and
  complete `answer` with `WrongPassphrase` before `logIn`; the assertions keep their meaning. If a
  caller used the named argument `initiallyAdmin`, change it to `isAdmin`.

Fixtures dependencies in `core/data/build.gradle.kts`: `testFixturesApi(libs.kotlinx.coroutines.core)`
(find the catalog alias; add none if main already exposes it as `api`); consumers get junit and
turbine from their own conventions.

## Expected file changes

- `core/data/build.gradle.kts`: testFixtures block and dependencies.
- New: the two fixture files above, `core/data/src/test/kotlin/.../FakePost.kt` and `FakeTransport.kt`.
- `feature/next-jam`, `feature/past-jams`, `feature/info`, `app` build files:
  `testImplementation(testFixtures(project(":core:data")))` (next-jam, past-jams and info already depend on
  `:core:data` for main).
- Deleted: `FakeJamsRepository.kt` in next-jam and past-jams; `FakeAdminSession` from both `AdminFakes.kt`
  (the other fakes stay) and info's `FakeAdminSession.kt`; app's private `FakeAdminSession`.
- Test files: import lines, the renames in the inventory, nothing else.
- `.claude/skills/architecture/SKILL.md`: a Build Conventions paragraph "Shared test fixtures"
  (location, how to depend, what belongs there, the shared-test-fakes sentence of `feature-subpackages`
  updated). `AGENTS.md`, `DESIGN.md`, product docs: not needed.

## Implementation tasks

1. Baseline: `CI=true ./init.sh` (exit 0, konsist/detekt/ktlint wired) and the testcase count from
   the XML reports, as `setlist-mutation-engine` did (1153 expected). Save under
   `docs/evidence/shared-test-fixtures/`.
2. Spike: enable testFixtures, add a trivial fixture, run `:core:data:compileDebugTestFixturesKotlin`,
   `:feature:info:testDebugUnitTest` consuming it. Confirm the four assumptions above. If any fails,
   stop at the fallback rule.
3. Prove the tools see the fixtures: add a temporary violation in a fixture file for each of Konsist
   (wrong package), ktlint and detekt, show each fails, revert. Save the outputs.
4. Move `FakeJamsRepository` and `FakeAdminSession`; delete the duplicates; fix imports.
5. Dedupe `FakePost` and `FakeTransport` in `core/data` tests; apply the renames.
6. Update the architecture skill. Run the full gate; record evidence in `feature_list.json`/`PROGRESS.md`.

## Verification

- Same test count before and after (exact), `docs/evidence/shared-test-fixtures/baseline-count.txt` and
  `final-count.txt`; no test removed, ignored or edited beyond imports/renames/property names.
- Grep, saved as `grep-gates.txt`: the list of `class|object Fake\w+` (and `Scripted|Settable`) in every
  `src/test*`, `src/testFixtures` and `src/androidTest` dir has no simple name twice
  (`rg -o "(class|object) Fake\w+" -r '$2'`... then `sort | uniq -d` returns nothing), and
  `FakeJamsRepository` and `FakeAdminSession` are declared exactly once, under `src/testFixtures`.
- No `@Suppress`, baseline or `ignoreFailures` added; no change in `konsist-test` (diff empty) in the main path.
- `CI=true ./init.sh` exits 0 with `konsist`, `detekt`, `ktlint` all `wired`; Konsist rule count unchanged (20).
- Release build unaffected: `./gradlew :app:assembleRelease` (or `build`, inside the gate) shows no fixtures
  class in the APK (fixtures are test-only).

## Validator checklist

- Mechanism is testFixtures, or the fallback with a documented failure and two failing-first amendments.
- Only the two shared fakes moved; feature-specific fakes untouched except renames.
- Test count equal; assertions untouched (diff of test files is imports, renames, property names).
- Duplicate-name grep clean; tools proven to read the fixtures dir (step 3 evidence).
- Architecture skill updated; no ViewModel, no feature-to-feature dependency, no production code change.
