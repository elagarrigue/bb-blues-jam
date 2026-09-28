---
name: architecture
description: Canonical architecture and design patterns for BB Blues Jam — module layout, allowed dependencies, where each piece goes, the composable presenter pattern, the mutation contract, and anti-patterns. Use when creating or modifying a module, a feature, a presenter, a repository, a mutation, or a Gradle dependency, and when planning, implementing, or validating any feature that touches code structure.
---

# Architecture

The rules below come from decisions D-02, D-03, D-04, D-09 and D-13, with their reasoning in
`bb-blues-jam-bitacora.md`. Do not reverse one without saying so and waiting for confirmation.

## Canonical Pattern

Clean Architecture, multi-module, with feature modules strictly isolated from each other.
Presentation is a composable presenter that returns a plain `UiModel`; there are no ViewModels.

## Module Layout

| Module | Holds | May depend on |
|---|---|---|
| `:core:model` | Domain types: `Jam`, `JamSong`, `Slot`, `Song`, `JamStatus`. Pure Kotlin, no Android. | nothing |
| `:core:data` | Repository interfaces (the contracts features use) and their implementations: Apps Script client, Room cache, DataStore admin flag. | `:core:model` |
| `:core:ui` | Design tokens, theme, and shared components such as the instrument strip. | `:core:model` |
| `:feature:<name>` | One screen or flow: its presenter, `UiModel`, and composables. | `:core:*` only |
| `:app` | Navigation, bottom bar, dependency binding, and the action registry (D-13). | everything |

Feature modules are added by the slice that first needs them, not up front. Current feature areas in
`feature_list.json` are `next-jam`, `song-detail`, `past-jams`, `info` and `admin`.

**An area is not automatically a module.** Admin controls are drawn on the same next-jam screen
(one app, not two), and a feature module cannot import another. The first admin slice must decide
where admin UI lives and record it here; the default is that admin controls live in
`:feature:next-jam` behind the admin flag, and the mutations live in `:core:data`.

## Where Each Piece Goes

- A new domain type or rule → `:core:model`, with a unit test.
- A read or a write against the Sheet → a repository function in `:core:data`.
- **A mutation** → a repository function in `:core:data`, then registered in the `:app` action
  registry. Never a lambda that only exists in a composable.
- A color, spacing or type value → `:core:ui` tokens from `DESIGN.md`. Never a literal in a feature.
- A component used by two features → `:core:ui`. Never copy it between features.
- Wiring an implementation to its interface → `:app`.
- Anything that needs two features to talk → `:app` navigation or a `:core` contract.

## Dependency Rules

- `:feature:a` → `:feature:b` is forbidden, in any direction and for any reason. Konsist enforces it
  once `konsist-isolation-rules` lands.
- `:core:*` never depends on `:feature:*` or `:app`.
- `:core:model` has no Android dependency.
- External music APIs (MusicBrainz, Deezer, Last.fm) are called only from background enrichment in
  `:core:data`, cached in Room, never from a presenter or during list rendering (D-09).

## Presenter Pattern

- A presenter is a `@Composable` function that takes its dependencies and events and returns a
  `UiModel`. State lives in the Compose runtime (`remember`, `LaunchedEffect`, `collectAsState`).
- The `UiModel` is an immutable data class with no Android types and no lambdas that hide
  mutations. In phase 2 it is exposed as assistant context as-is.
- Tests use Molecule on the JVM, no emulator, and assert at least one state transition, not only
  the first emission.
- If a presenter type overrides `equals`, `hashCode` must derive from the same fields. If examples
  call a presenter with `presenter()`, the `invoke` operator must actually be declared. Both were
  bugs in the reference pattern (bitácora 3.5).

The canonical sample presenter and its test land with `molecule-presenter-harness`. Once they
exist, point to them here and copy their shape instead of this description.

## Minimal Feature Template

```
feature/<name>/
  build.gradle.kts                 # depends on :core:* only
  src/main/.../<Name>Presenter.kt  # @Composable fun present...(): <Name>UiModel
  src/main/.../<Name>UiModel.kt    # immutable data class
  src/main/.../<Name>Screen.kt     # renders the UiModel, emits events, holds no logic
  src/test/.../<Name>PresenterTest.kt  # Molecule, JVM
```

Then include the module in `settings.gradle.kts` and wire it from `:app`.

## Anti-Patterns

| Do not | Instead |
|---|---|
| Add a `ViewModel` or `AndroidViewModel` | A composable presenter (D-02) |
| Import one feature module from another | Move the contract to `:core:*`, wire in `:app` (D-03) |
| Perform a write from a composable click handler | Call a `:core:data` repository function (D-13) |
| Sync a Sheet-owned entity back to the Sheet | The catalog and past jams are read-only in the app (D-04) |
| Fill `key` from an API or from `Song.defaultKey` silently | The admin sets `JamSong.key` (D-08) |
| Treat a missing enrichment field as an error | Every enrichment field is optional (D-09) |
| Use the local admin flag as authorization | Apps Script validates every write (D-11) |
| Add a `Musician` entity or an `ARCHIVED` status | A musician is a name on a `Slot`; archived is derived from the date |
| Hard-code colors, spacing or copy in `tú` | `:core:ui` tokens; Rioplatense Spanish with *vos* (D-12) |

## Still Open

- Dependency injection framework: not decided. Use constructor injection wired in `:app` until a
  slice decides otherwise, and record the decision here.
- Navigation library and the deeplink scheme: decided by `bottom-navigation`.
