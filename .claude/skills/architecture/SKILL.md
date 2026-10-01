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
| `:core:ui` | Presenter contracts (`Presenter`, `UiModel`, `UiEvent`, `EventHandler`), design tokens, theme, shared components such as the instrument strip, and UI-side contracts features share, such as `ExternalLinkOpener` (`com.bbbjam.core.ui.link`). | `:core:model` |
| `:feature:<name>` | One screen or flow: its presenters, `UiModel`s, composables, and one Koin module. | `:core:*` only |
| `:app` | Navigation, bottom bar, `BluesJamApp` (`startKoin` with every module), `appModule` (Android implementations of `:core` contracts, such as `IntentLinkOpener`), and the action registry (D-13). | everything |
| `backend/apps-script` | Not a Gradle module. The Apps Script web app (`src/*.js`, `appsscript.json`), its Node tests and `tools/`. The only code that touches the Sheet; its contract is `docs/apps-script-api.md`. | nothing |
| `:konsist-test` | Test-only JVM module (`bluesjam.jvm.library`, no `src/main`) holding the Konsist architecture suite `ModuleIsolationTest`. It reads every module's sources from disk. | nothing (no project dependency) |

Feature modules are added by the slice that first needs them, not up front. `:feature:info` exists
(set by `info-screen`); the planned ones are `:feature:next-jam`, `:feature:song-detail` and
`:feature:past-jams`.

**Admin is a state, not a module.** An admin is a musician with extra controls on the same screens
(one app, not two). Each presenter reads the admin flag from `AdminSession` in `:core:data` and adds
admin events and controls to its own `UiModel`. The setlist mutations are drawn in
`:feature:next-jam`; the passphrase login lives in `:feature:info`, where its discreet entry point
is. There is no `:feature:admin`.

## Where Each Piece Goes

- A new domain type or rule → `:core:model`, with a unit test.
- A read or a write against the Sheet → a repository function in `:core:data`.
- A new Sheet read or write → a route in `backend/apps-script` plus a repository function in
  `:core:data`.
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
- Opening a web link → `ExternalLinkOpener` from `:core:ui`, injected into the presenter and called
  from an event handler; `:app` binds `IntentLinkOpener`. Never `LocalUriHandler` or an `Intent`
  in a feature.

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
covered without editing it. It holds 11 rules; the two that read build files are
`build-file-project-deps` and `build-file-applies-convention` (see Build Conventions). **A new
dependency rule means a new test in that class**, proven able
to fail before it is trusted.

## Build Conventions

Set by `module-skeleton` (spec `docs/specs/module-skeleton.md`); moved into convention plugins by
`build-logic-conventions` (spec `docs/specs/build-logic-conventions.md`). Every new module follows
them.

- **Convention plugins.** `build-logic/` is an included build (`includeBuild("build-logic")` first
  in `pluginManagement`, not `buildSrc`) with one subproject, `:convention` (`kotlin-dsl`, binary
  plugins in `com.bbbjam.buildlogic`). It reads the root catalog from `../gradle/libs.versions.toml`
  and compiles against AGP and KGP as `compileOnly` (catalog `android-gradlePlugin`,
  `kotlin-gradlePlugin`). A module build file applies only `id("bluesjam.…")` plugins and keeps what
  is its own: `namespace`, `:app`'s identity and build types, project dependencies and
  module-specific libraries.

  | Plugin | Applies / sets | Used by |
  |---|---|---|
  | `bluesjam.jvm.library` | `org.jetbrains.kotlin.jvm`; `java {}` source/target 11; `jvmTarget = JVM_11` | `:core:model`, `:konsist-test` |
  | `bluesjam.android.library` | `com.android.library`; common Android setup (SDKs, Java 11) | `:core:data` |
  | `bluesjam.android.application` | `com.android.application`; common Android setup; `targetSdk` from the catalog | `:app` |
  | `bluesjam.android.compose` | requires an Android plugin first; Compose compiler plugin; `buildFeatures.compose = true` | `:app` (and through the two below) |
  | `bluesjam.android.presenter` | `.android.library` + `.android.compose`; `unitTests.isReturnDefaultValues = true`; `testImplementation` junit, molecule-runtime, turbine, kotlinx-coroutines-test | `:core:ui` |
  | `bluesjam.android.feature` | `.android.presenter`; `implementation` Koin BOM, `koin-core`, `koin-compose` | every `:feature:*` |

  A convention adds a dependency only when every module of its kind needs it. Optional or
  module-specific libraries (Compose BOM, foundation, tooling, `koin-android`, junit in JVM modules
  and `:app`) stay in the module file. **Project dependencies never go into a convention**:
  `build-file-project-deps` reads them from module files (D-03). The Konsist rule
  `build-file-applies-convention` fails a module that applies no `bluesjam.*` plugin, an `:app`
  without `bluesjam.android.application`, a `:feature:*` without `bluesjam.android.feature`, a raw
  plugin (`alias(libs.plugins…)`, another `id`, `kotlin("…")`, `apply(plugin…)`), a
  convention-owned setting (`compileSdk`, `minSdk`, `targetSdk`, `JavaVersion`, `JvmTarget`,
  `jvmTarget`, `jvmToolchain`, `sourceCompatibility`, `targetCompatibility`, `buildFeatures`,
  `isReturnDefaultValues`) outside a `//` comment, or a missing build file. Editing a convention
  recompiles `build-logic` and misses the configuration cache once (about 18 s of `init.sh`
  instead of 7).
- **Plugin per module kind.** A pure Kotlin module applies `bluesjam.jvm.library`. An Android
  library applies `bluesjam.android.library` (or `.presenter`/`.feature`, which include it) and
  relies on AGP 9's built-in Kotlin. Never apply `org.jetbrains.kotlin.android`, and never
  `com.android.kotlin.multiplatform.library`: the project is Android-only.
- **Every plugin is declared at the root** `build.gradle.kts` with `apply false`, from the catalog
  with `alias(...)`. The conventions compile against AGP and KGP `compileOnly` and apply plugins by
  id, so at runtime they use the copies the root puts on the classpath: without `kotlin.compose`
  there, configuration fails ("An exception occurred applying plugin request [id:
  'bluesjam.android.compose']"). It also keeps one AGP and one KGP in `buildEnvironment`.
- **SDK levels come from the catalog**, set by the conventions: `compileSdk` and `minSdk` in every
  Android module (`configureAndroidCommon` in `ProjectExtensions.kt`), `targetSdk` in `:app` only
  through `bluesjam.android.application`. Change a level in `[versions]`, never in a module.
- **Java 11 everywhere**, set by the conventions: `compileOptions` `VERSION_11` in Android modules;
  `java {}` `VERSION_11` plus `jvmTarget = JVM_11` in JVM modules. No `jvmToolchain(11)`, which
  would make foojay download a JDK 11 for nothing. `build-logic` itself targets the daemon JVM
  (pinned by `gradle/gradle-daemon-jvm.properties`); do not add a toolchain there either.
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
  `ignoreFailures`, or a blanket rule disable without the user's approval. The tools stay
  root-applied after `build-logic-conventions`, not in a convention. That root block is the build's
  only Isolated Projects blocker; moving the tools into a `bluesjam.quality` convention applied by
  the three base conventions was proven to work (spec Decision 3) and is left to its own slice.
  Known gap: the Kotlin in `build-logic` is outside the root build, so detekt and ktlint do not
  lint it.
- **A module with presenters** (set by `molecule-presenter-harness`, spec
  `docs/specs/molecule-presenter-harness.md`) applies `bluesjam.android.presenter` (a feature applies
  `bluesjam.android.feature`, which includes it). The convention applies the Compose compiler with
  `buildFeatures.compose = true`, adds `testImplementation` junit, molecule-runtime, turbine,
  kotlinx-coroutines-test, and sets `testOptions.unitTests.isReturnDefaultValues = true`; the
  Compose runtime arrives through `:core:ui` (which exposes the BOM and
  `androidx.compose.runtime:runtime` as `api`). Without the flag every Molecule test fails on
  `android.os.Trace` "not mocked", because the Android Compose runtime calls it.
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
- **Reference feature build file**: `feature/info/build.gradle.kts` (set by `info-screen`, reshaped
  by `build-logic-conventions`): `id("bluesjam.android.feature")`, `android { namespace =
  "com.bbbjam.feature.<name>" }`, its only project dependency `implementation(project(":core:ui"))`
  (plus `:core:data` once a feature reads data), `androidx.compose.foundation` and tooling-preview
  (+ debug tooling). SDKs, Java 11, Compose, the test flag, Koin and the presenter test libraries
  come from the convention.
- **Koin 4.1.1** (set by `info-screen`, approved 30 September 2026; D-16). Catalog: `koin-bom`
  (versioned) and `koin-core`, `koin-android`, `koin-compose` (BOM-managed). Feature modules use
  `koin-core` + `koin-compose` (for `koinInject()`); `:app` uses `koin-android` (for
  `androidContext`). Not 4.2.x: it raises `kotlin-stdlib` to 2.3.20 and Compose to 1.10.x over the
  pinned Kotlin 2.2.10 and BOM 2025.09.00; upgrade Koin together with Kotlin and the BOM. 4.1.1
  only moves lifecycle 2.9.0 → 2.9.3. `koin-android` brings `koin-core-viewmodel` and the lifecycle
  ViewModel artifacts transitively; that does not relax the ViewModel ban (D-02).
- **UI copy lives in Kotlin, not string resources** (set by `info-screen`, Decision 6): an
  `internal object <Name>Copy` in the feature, read by the presenter into the `UiModel`. The app is
  Spanish-only (D-12), the copy must be in the `UiModel` for the assistant, and presenters stay
  Android-free and JVM-testable. `app_name` stays a resource.
- **48dp touch targets without a literal**: clickable rows use
  `Modifier.heightIn(min = LocalMinimumInteractiveComponentSize.current)` (Material 3, 48dp), with
  `Role.Button` and an `onClickLabel` when the row's action is not obvious from its text.

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
  build.gradle.kts                     # id("bluesjam.android.feature"); depends on :core:* only
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
