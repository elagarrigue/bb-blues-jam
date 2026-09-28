---
name: architecture
description: Canonical architecture and design patterns for BB Blues Jam — module layout, allowed dependencies, where each piece goes, the composable presenter pattern, the mutation contract, and anti-patterns. Use when creating or modifying a module, a feature, a presenter, a repository, a mutation, or a Gradle dependency, and when planning, implementing, or validating any feature that touches code structure.
---

# Architecture

The rules below come from decisions D-02, D-03, D-04, D-09, D-13, D-15 and D-16, with their reasoning in
`bb-blues-jam-bitacora.md`. Do not reverse one without saying so and waiting for confirmation.

## Canonical Pattern

Clean Architecture, multi-module, with feature modules strictly isolated from each other.
Presentation is a composable presenter that returns a plain `UiModel`; there are no ViewModels.

## Module Layout

| Module | Holds | May depend on |
|---|---|---|
| `:core:model` | Domain types: `Jam`, `JamSong`, `Slot`, `Song`, `JamStatus`. Pure Kotlin, no Android. | nothing |
| `:core:data` | Repository interfaces (the contracts features use) and their implementations: Apps Script client, Room cache, DataStore admin flag. | `:core:model` |
| `:core:ui` | Presenter contracts (`Presenter`, `UiModel`, `UiEvent`, `EventHandler`), design tokens, theme, and shared components such as the instrument strip. | `:core:model` |
| `:feature:<name>` | One screen or flow: its presenters, `UiModel`s, composables, and one Koin module. | `:core:*` only |
| `:app` | Navigation, bottom bar, `startKoin` with every module, and the action registry (D-13). | everything |

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
- A component used by two features → `:core:ui`. Never copy it between features.
- Wiring an implementation to its interface → the Koin module of the module that owns the
  implementation; `:app` only lists modules in `startKoin`.
- Anything that needs two features to talk → `:app` navigation or a `:core` contract.

## Dependency Rules

- `:feature:a` → `:feature:b` is forbidden, in any direction and for any reason. Konsist enforces it
  once `konsist-isolation-rules` lands.
- `:core:*` never depends on `:feature:*` or `:app`.
- `:core:model` has no Android dependency.
- External music APIs (MusicBrainz, Deezer, Last.fm) are called only from background enrichment in
  `:core:data`, cached in Room, never from a presenter or during list rendering (D-09).

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
- **Dependencies**: `:core:ui` and `:core:data` use `api(project(":core:model"))`, because their
  public contracts expose domain types. `:app` lists each module it uses with
  `implementation(project(...))`. Use `project(":…")`, not type-safe project accessors.
- **Each module has its own `.gitignore`** with `/build`; the root one only ignores the root build.
- **No `build-logic` convention plugins yet.** Plain per-module build files are cheaper at this
  size. Revisit when the first `:feature:*` module is added or when `detekt-ktlint-gate` needs to
  configure every module, whichever comes first.

## Presenter Pattern

Read `references/presenter-pattern.md` before writing a presenter. It holds the contracts, a full
next-jam example with a child presenter and admin state, the screen, the Koin wiring, and Molecule
tests. In short:

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
