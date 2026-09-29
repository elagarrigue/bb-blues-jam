---
name: architecture
description: Canonical architecture and design patterns for BB Blues Jam — module layout, allowed dependencies, where each piece goes, the composable presenter pattern, the mutation contract, and anti-patterns. Use when creating or modifying a module, a feature, a presenter, a repository, a mutation, or a Gradle dependency, and when planning, implementing, or validating any feature that touches code structure.
---

# Architecture

The rules below come from decisions D-02, D-03, D-04, D-09, D-13, D-15, D-16 and D-17, with their reasoning in
`bb-blues-jam-bitacora.md`. Do not reverse one without saying so and waiting for confirmation.

## Canonical Pattern

Clean Architecture, multi-module, with feature modules strictly isolated from each other.
Presentation is a composable presenter that returns a plain `UiModel`; there are no ViewModels.

## Module Layout

| Module | Holds | May depend on |
|---|---|---|
| `:core:model` | Domain types: `Jam`, `JamStatus`, `JamSong`, `Lineup`, `Slot`, `ExtraParticipant`, `Instrument`, `Song`, `Tempo`, `Difficulty`, `Key`, `SongId`. Pure Kotlin, no Android, no serialization, no date parsing, no clock. | nothing |
| `:core:data` | Repository interfaces (the contracts features use) and their implementations: Apps Script client, Room cache, DataStore admin flag. | `:core:model` |
| `:core:ui` | Presenter contracts (`Presenter`, `UiModel`, `UiEvent`, `EventHandler`), design tokens, theme, and shared components such as the instrument strip. | `:core:model` |
| `:feature:<name>` | One screen or flow: its presenters, `UiModel`s, composables, and one Koin module. | `:core:*` only |
| `:app` | Navigation, bottom bar, `startKoin` with every module, and the action registry (D-13). | everything |
| `:konsist-test` | Test-only JVM module (`kotlin-jvm`, no `src/main`) holding the Konsist architecture suite `ModuleIsolationTest`. It reads every module's sources from disk. | nothing (no project dependency) |

Feature modules are added by the slice that first needs them, not up front. The planned ones are
`:feature:next-jam`, `:feature:song-detail`, `:feature:past-jams` and `:feature:info`.

**Admin is a state, not a module.** An admin is a musician with extra controls on the same screens
(one app, not two). Each presenter reads the admin flag from `AdminSession` in `:core:data` and adds
admin events and controls to its own `UiModel`. The setlist mutations are drawn in
`:feature:next-jam`; the passphrase login lives in `:feature:info`, where its discreet entry point
is. There is no `:feature:admin`.

## Where Each Piece Goes

- A new domain type or rule → `:core:model`, with a unit test.
- A read or a write against the Sheet → a repository function in `:core:data`.
- **A mutation** → a repository function in `:core:data`, then registered in the `:app` action
  registry. Never a lambda that only exists in a composable.
- A color, spacing or type value → `:core:ui` tokens from `DESIGN.md`. Never a literal in a feature.
  Screens wrap in `BluesJamTheme { }` (already done in `MainActivity`) and read `BluesJamTheme.colors`,
  `.typography`, `.shapes` and `.spacing` (package `com.bbbjam.core.ui.theme`), never
  `MaterialTheme.colorScheme` and never the internal palette (D-17). Amber has no public name; pick
  the role (`primaryAction`, `slotOpen`, `key`, `published`, `activeFilter` and their `on…`
  colors). A component slice that uses a Material 3 component sets its colors explicitly from
  `BluesJamColors`: Material defaults are mapped from tokens but are not design decisions.
- A component used by two features → `:core:ui`. Never copy it between features.
- Wiring an implementation to its interface → the Koin module of the module that owns the
  implementation; `:app` only lists modules in `startKoin`.
- Anything that needs two features to talk → `:app` navigation or a `:core` contract.

## Dependency Rules

- `:feature:a` → `:feature:b` is forbidden, in any direction and for any reason.
- `:core:*` never depends on `:feature:*` or `:app`.
- `:core:model` has no Android dependency.
- `:core:model` never reads the system clock: no `.now(`, `Clock.system…`,
  `System.currentTimeMillis(` or `System.nanoTime(` in its main or test sources. The caller passes
  today's date (`Jam.isHistorical(today)`) and owns the time zone.
- External music APIs (MusicBrainz, Deezer, Last.fm) are called only from background enrichment in
  `:core:data`, cached in Room, never from a presenter or during list rendering (D-09).

Konsist enforces the first four rules (the clock one as `core-model-no-system-clock`, a textual
match on each `:core:model` file), the package roots, the ViewModel ban and
`no-color-literal-outside-core-ui` (no numeric `Color(…)` and no ARGB hex literal in any module but
`:core:ui`; it does not catch `Color.Red`, `Color.parseColor`, named-argument `Color(red = …)` or XML
colors) in
`konsist-test/src/test/kotlin/com/bbbjam/konsist/ModuleIsolationTest.kt`, which runs inside
`./gradlew check` (so inside `./init.sh`). It checks imports and also every `project(":…")` in
`core/*` and `feature/*` build files, and reads module groups from paths, so a new `:feature:*` is
covered without editing it. **A new dependency rule means a new test in that class**, proven able
to fail before it is trusted.

## Build Conventions

Set by `module-skeleton` (spec `docs/specs/module-skeleton.md`). Every new module follows them.

- **Plugin per module kind.** A pure Kotlin module (`:core:model`) applies `kotlin-jvm`
  (`org.jetbrains.kotlin.jvm`, `version.ref = "kotlin"`). An Android library (`:core:ui`,
  `:core:data`, every `:feature:*`) applies `android-library` (`com.android.library`) and relies on
  AGP 9's built-in Kotlin. Never apply `org.jetbrains.kotlin.android`, and never
  `com.android.kotlin.multiplatform.library`: the project is Android-only.
- **Every plugin is declared at the root** `build.gradle.kts` with `apply false`, from the catalog
  with `alias(...)`. Omitting one there fails configuration with "the plugin is already on the
  classpath with an unknown version", because AGP already puts KGP and the library plugin on the
  classpath.
- **SDK levels come from the catalog**: `compileSdk` and `minSdk` in `[versions]`, read with
  `libs.versions.minSdk.get().toInt()` (and `release(libs.versions.compileSdk.get().toInt())`) in
  every Android module. `targetSdk` lives in `:app` only.
- **Java 11 everywhere**: `compileOptions` `VERSION_11` in Android modules; `java {}` `VERSION_11`
  plus `compilerOptions.jvmTarget = JvmTarget.JVM_11` in JVM modules. No `jvmToolchain(11)`, which
  would make foojay download a JDK 11 for nothing.
- **New modules keep sources in `src/main/kotlin`** (and `src/test/kotlin`). `:app` keeps
  `src/main/java`. Libraries need no `AndroidManifest.xml`; the namespace comes from the DSL,
  `com.bbbjam.<path>` (for example `com.bbbjam.core.ui`).
- **Package root is `com.bbbjam.<module path>` with hyphens removed** (`:feature:next-jam` →
  `com.bbbjam.feature.nextjam`), and every source file in `:core:*` and `:feature:*` declares a package
  equal to or under it. Enforced by the `package-under-module-root` Konsist rule.
- **Dependencies**: `:core:ui` and `:core:data` use `api(project(":core:model"))`, because their
  public contracts expose domain types. `:app` lists each module it uses with
  `implementation(project(...))`. Use `project(":…")`, not type-safe project accessors.
- **Each module has its own `.gitignore`** with `/build`; the root one only ignores the root build.
- **Quality tools come from the root build file** (set by `detekt-ktlint-gate`, spec
  `docs/specs/detekt-ktlint-gate.md`). The root `build.gradle.kts` applies detekt and ktlint to
  every module that applies `kotlin-jvm` or an Android plugin, so a new module needs no tool
  configuration and gets both in its `check`. Configuration lives in `.editorconfig` (ktlint,
  `android_studio` style, Composable naming exception) and `config/detekt/detekt.yml` (overrides on
  top of detekt's defaults). `./gradlew ktlintFormat` fixes formatting. Never add a baseline file,
  `ignoreFailures`, or a blanket rule disable without the user's approval.
- **A module with presenters** (set by `molecule-presenter-harness`, spec
  `docs/specs/molecule-presenter-harness.md`) applies `kotlin-compose` with
  `buildFeatures.compose = true`, gets the Compose runtime through `:core:ui` (which exposes the
  BOM and `androidx.compose.runtime:runtime` as `api`), adds `testImplementation` junit,
  molecule-runtime, turbine, kotlinx-coroutines-test, and sets
  `testOptions.unitTests.isReturnDefaultValues = true`. Without that flag every Molecule test fails
  on `android.os.Trace` "not mocked", because the Android Compose runtime calls it.
- **`:core:ui` is the design system** (set by `design-tokens-theme`, spec
  `docs/specs/design-tokens-theme.md`). Besides the runtime it exposes `androidx.compose.ui:ui` and
  `androidx.compose.material3:material3` as `api` (BOM-managed, material3 1.3.2), so a module that
  depends on it gets the token types and the themed components with no dependency line of its own.
  Fonts are bundled static TTFs in `core/ui/src/main/res/font`, with their SIL OFL 1.1 texts in
  `core/ui/src/main/assets/licenses`; no downloadable fonts, because the app is used offline. The
  only XML color is `bluesjam_window_background` in `core/ui/src/main/res/values/colors.xml`, used by
  the `:app` window theme and guarded by `WindowBackgroundTest` against drift from the token.
- **java.time and desugaring** (set by `domain-model-types`, spec
  `docs/specs/domain-model-types.md`). `:core:model` uses `java.time.LocalDate`/`LocalTime`, which
  do not exist below API 26 while `minSdk` is 24. That is safe only because `:core:model` never
  parses a date or reads the clock. The first Android module that constructs or parses a date
  (expected `catalog-repository-cache`) enables `isCoreLibraryDesugaringEnabled = true` plus
  `coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")` in `:app` **and** in its own
  module: lint `NewApi` fails a library that uses java.time without it even when `:app` has it, and
  a library that enables it without `:app` fails `:app:checkDebugAarMetadata`.
- **No `build-logic` convention plugins yet.** Deferred to the first `:feature:*` module, which will
  duplicate Android-library boilerplate; the root quality block can move there then. A convention
  plugin must be applied per module, which the quality gate must not depend on.

## Presenter Pattern

Read `references/presenter-pattern.md` before writing a presenter. It holds the contracts, the
module setup, a compiled sample test, and an illustrative next-jam example with a child presenter
and admin state, the screen, the Koin wiring, and Molecule tests. The contracts are real code in
`:core:ui`, package `com.bbbjam.core.ui.presenter`; the compiled example is `SamplePresenter` with
`SamplePresenterTest` in `core/ui/src/test/kotlin/com/bbbjam/core/ui/presenter/`. In short:

- A presenter is a class implementing `Presenter<Model, Params>` with a `@Composable present(params)`
  that returns an immutable `UiModel`. Dependencies arrive through the constructor via Koin; runtime
  inputs arrive as `Params`. State lives in the Compose runtime.
- A `UiModel` holds display values and `EventHandler<Event>`s, with events as a sealed
  `Event : UiEvent`. No repositories, no Android types, no raw lambdas. In phase 2 it is exposed as
  assistant context as-is.
- Parent presenters compose child presenters by calling `child.present(params)`.
- Tests use Molecule and Turbine on the JVM with fake repositories, and assert at least one state
  transition, not only the first emission.
- `EventHandler.equals` and `hashCode` both derive from `key`, and `invoke` is declared. The source
  articles get both wrong (bitácora 3.5).

## Dependency Injection — Koin

- Presenters are `factory`: they hold no state; the composition does. Repositories are `single`.
- Screens get their presenter with `koinInject()`, as a default parameter so previews and tests can
  pass one in.
- Each module exposes its own Koin `module { }`. Only `:app` calls `startKoin`, and a feature never
  references another feature's Koin module.
- No Koin in `:core:model`, and no `KoinComponent`/`inject()` inside presenters or repositories:
  constructor injection only, so tests build them by hand.

## Minimal Feature Template

```
feature/<name>/
  build.gradle.kts                     # depends on :core:* only
  src/main/.../<Name>Presenter.kt      # class <Name>Presenter : Presenter<<Name>UiModel, Params>
  src/main/.../<Name>UiModel.kt        # sealed or data class : UiModel, with sealed Event
  src/main/.../<Name>Screen.kt         # renders the UiModel, forwards events, holds no logic
  src/main/.../di/<Name>Module.kt      # val <name>Module = module { factory { … } }
  src/test/.../<Name>PresenterTest.kt  # Molecule + Turbine, fakes, JVM
```

Then include the module in `settings.gradle.kts` and add its Koin module to `startKoin` in `:app`.

## Anti-Patterns

| Do not | Instead |
|---|---|
| Add a `ViewModel` or `AndroidViewModel` | A composable presenter (D-02) |
| Create `:feature:admin` or a separate admin screen for setlist edits | Admin state inside the existing presenter (D-15) |
| Call `get()`/`inject()` inside a presenter or repository | Constructor injection, wired in a Koin module (D-16) |
| Put logic or `if (isAdmin)` decisions in a composable | The presenter decides; the screen draws `canEdit` |
| Import one feature module from another | Move the contract to `:core:*`, wire in `:app` (D-03) |
| Perform a write from a composable click handler | Call a `:core:data` repository function (D-13) |
| Sync a Sheet-owned entity back to the Sheet | The catalog and past jams are read-only in the app (D-04) |
| Fill `key` from an API or from `Song.defaultKey` silently | The admin sets `JamSong.key` (D-08) |
| Treat a missing enrichment field as an error | Every enrichment field is optional (D-09) |
| Use the local admin flag as authorization | Apps Script validates every write (D-11) |
| Add a `Musician` entity or an `ARCHIVED` status | A musician is a name on a `Slot`; archived is derived from the date |
| Hard-code colors, spacing or copy in `tú` | `:core:ui` tokens; Rioplatense Spanish with *vos* (D-12) |

## Still Open

- Navigation library and the deeplink scheme: decided by `bottom-navigation`, recorded here.
