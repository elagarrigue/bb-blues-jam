# Spec: wiring-and-preview-hygiene

Code-review findings 7 and 8 (17 October 2026). No user-visible change. Depends on `feature-api-surface` (accepted).
Implementer: sonnet. Do not touch `presentations/` or other sessions' PROGRESS.md entries.

## What the planner inspected / assumed

Inspected: `feature_list.json` entry, the architecture skill, greps of every `src/main` constructor/property default in
`core/*`, `feature/*`, `app`, and every `@Preview` in main sources (72 occurrences, 25 files).
Assumed (labelled): the baseline is the last accepted count, 1154 testcases and 21 Konsist rules
(`docs/evidence/feature-api-surface/final-count.txt`); the implementer must re-measure it first.

## Part A: constructor defaults that create a collaborator (finding 7)

Grep result (main sources, all modules). Only these defaults construct an object; the rest are `emptyList()`/`emptyMap()`
value defaults on data classes, design-token constants and companion constants.

| Declaration | Default | Decision | Reason |
|---|---|---|---|
| `ProposalApplier.runLock` (`feature/assistant/.../run/ProposalApplier.kt:34`) | `RunLock()` | **Required** | The lock only works if one instance is shared with `JamCreationRunner`; a default silently gives each its own and defeats the mutual exclusion (D-29). Koin already passes the `single` (`AssistantModule.kt:35-60`). |
| `JamCreationRunner.runLock` (`.../run/JamCreationRunner.kt:45`) | `RunLock()` | **Required** | Same. |
| `SongAdminState.reservations` (`feature/next-jam/.../songdetail/SongAdminState.kt:30`) | `LineupReservations()` | **Required** | Stateful (`mutableStateMapOf`, a token counter) coordination of pending taps. `rememberSongAdminState` already passes the one `remember`ed instance; only the AdminSongPreview relies on the default, so the preview passes `LineupReservations()` explicitly. |
| `DefaultSetlistAssistant.statusProbe` (`core/data/.../assistant/DefaultSetlistAssistant.kt:35`) | `AssistantStatusProbe()` (builds an OkHttp client) | **Required** | A network collaborator created behind the Koin binding's back. `dataModule` (`DataModule.kt:61`) passes `AssistantStatusProbe()` explicitly, so behavior is identical; tests that omit it pass one explicitly (`DefaultSetlistAssistantTest`, `StreamingAssistantTest`, `AssistantLiveCallTest`; `AssistantStatusTest` already does). |
| `NextJamPresenter.random` | `Random.Default` | **Keep** | Stateless process-wide value; no sharing semantics; a test seam only. |
| `OkHttpAppsScriptTransport.ioDispatcher` | `Dispatchers.IO` | **Keep** | A global singleton, not a collaborator; tests pass `Unconfined`/test dispatchers. |
| `AssistantStatusProbe.client` / `baseUrl`, `AssistantHttp.executorFactory(okHttp = okHttpClient())` | fresh client builder, constant URL | **Keep** | Builders with one production call site and no state to share (each client is configured by the same factory); the probe now receives its instance from `dataModule`. |
| `AdminCredentialStore.dataStore(scope = CoroutineScope(IO + SupervisorJob()))` | fresh scope | **Keep, noted** | A factory called once from a `single`; DataStore allows one active instance per file, so a second scope cannot compete. Tests pass their own scope. Not a shared coordination default. |
| `SongAdminState.overlays`, `AdminState.removal`, and the other `X()` / `emptyList()` defaults on UI state holders | empty value objects | **Keep** | Immutable empty values, not shared coordination. |

Already done by `setlist-mutation-engine`: queue and failure-log defaults removed.

Changes:
1. Remove the four defaults marked Required (parameters keep their position).
2. Tests that relied on them pass the collaborator explicitly. Known sites: `ApplyFixtures.kt:169` (`testApplier`: add a
   `runLock: RunLock = RunLock()` parameter is acceptable in a test fixture only if every test that mixes applier and
   runner passes the same instance; `CreatePresenterTest` already shares one `lock`), `AssistantFixtures.kt:57`
   (`testPresenter` runner), `JamCreationRunnerTest.kt:26`, `JamPresenterTest.kt:54`, `CreatePresenterTest.kt:333`
   (already passes `lock`). Let the compiler find the rest; record the list.
3. Behavior must not change: no edit to logic, only constructor signatures and call sites.

Verification: grep for `RunLock()` in main shows only `AssistantModule`; `grep -rn "= AssistantStatusProbe()"` in main
shows none; `grep -n "LineupReservations()"` in main shows only `rememberSongAdminState`/`LineupControls` creation sites
and the preview.

## Part B: previews out of production screens (finding 8)

Scope: every `*Screen.kt` and `*Views.kt` in `:feature:*` main sources (16 files; `core/ui` has none, see Rule decision).

Inventory of `@Preview` and fixtures in those files:

| File | Contents to move |
|---|---|
| `feature/info/.../InfoScreen.kt` | `InfoScreenPreview` with an anonymous `AdminSession`, plus the preview-only imports |
| `feature/info/.../AdminLoginScreen.kt` | `AdminLoginScreenPreview` |
| `feature/next-jam/.../NextJamScreen.kt` | `NextJamScreenPreview` and `internal fun previewRow(...)` (used also by `NextJamRemovalPreview.kt`; moves with the preview, stays `internal`) |
| `.../songdetail/SongDetailScreen.kt` | `previewDate`, `previewModel`, `DetailPreview`, `fullSong`, `PREVIEW_TALL`, and the 4 previews (`RequiredFieldsPreview`, `FullSongPreview`, `AdminSongPreview`, `NotFoundPreview`); the `JamsSnapshot`/`Jam`/`JamSong` imports leave the screen file |
| `.../addsong/AddSongScreen.kt` | `AddSongScreenPreview` |
| `.../assign/AssignMusicianScreen.kt` | `AssignMusicianPreview` |
| `.../setkey/SetKeyScreen.kt` | `SetKeyScreenPreview` |
| `PastJamsScreen.kt`, `PastJamDetailScreen.kt`, `AssistantScreen.kt`, the six `*Views.kt` | none (already clean; previews live in `PastJamsStatesPreview`, `PastJamDetailPreview`, `AssistantPreview`) |

Not in scope (listed so nobody "fixes" them): the previews inside `:core:ui` component files (`TabBar`, `LineupPanel`,
`InstrumentStrip`, `JamStatusChips`, `InstrumentFilterBar`, `ListStateBlocks`, `IconRowAction`, `BackButton`,
`AdminBanner`, `SkeletonList`, `LoadingDots`, `ThemeShowcase`) and `InstrumentFilterBar.previewLineups`. None is a
`*Screen.kt` or `*Views.kt`; a component keeping its preview beside it is the shared-component convention. Say so in the
evidence.

Where each goes: a new `*Preview.kt` in the **same package and directory** as its screen: `InfoPreview.kt`,
`AdminLoginPreview.kt`, `NextJamPreview.kt` (distinct from the existing `NextJamStatesPreview/RemovalPreview/DraftPreview`),
`SongDetailPreview.kt`, `AddSongPreview.kt`, `AssignMusicianPreview.kt`, `SetKeyPreview.kt`. One file per screen, so
detekt `TooManyFunctions` is not hit (SongDetailScreen had 12 in one file before; the split should leave the screen file
well under the limit and the preview file at about 8).

Constraints:
- `@Preview` functions and their fixtures are `private`; a fixture shared by two preview files (only `previewRow`) is
  `internal`. **Nothing becomes public**: Konsist rule 21 (`feature-public-api-allowlisted`) allowlists public names, and a
  public preview would fail it.
- Previews should call the existing `internal` screen overloads / `internal` content functions
  (`SongDetailContent`, `InfoScreen(..., presenter)`, etc.). Widen a `private` content function (`AddSongContent`,
  `SetKeyContent`, `AssignMusicianContent`, `AdminLoginContent`, `InfoContent`) to `internal` only if the preview cannot
  use the internal screen overload; record each widening in the evidence. Never widen to public.
- The anonymous `AdminSession` object in `InfoScreenPreview` may stay a local object in `InfoPreview.kt`. Do not replace it
  with `FakeAdminSession` from `testFixtures` (that source set is test-only; main code must not use it).
- Previews keep wrapping in `BluesJamTheme { }`; no `MaterialTheme`; no color or dp literals (existing rules apply to
  preview files too).
- Behavior of screens must be byte-for-byte the same; only the preview/fixture declarations and now-unused imports move.
  ktlint's unused-import rule will flag leftovers.

## Konsist rule decision

**Add rule 22, `feature-previews-in-preview-files`** (new verification rule, so it must be shown failing first):
in every `:feature:*` main file whose name ends with `Screen.kt` or `Views.kt`, no function carries `@Preview`.
Reason: the finding is exactly the class of drift a rule catches and the cost is one small test in
`konsist-test/.../ModuleIsolationTest.kt` (same `scope.files ... assertFalse(testName = ...)` shape as rule 21). Not
applied to `:core:ui` (it has no such files and components keep their previews by design). Known limit to document in the
rule's KDoc and the architecture skill: it checks the annotation only, so a preview *fixture* left in a screen file is
not caught, and a preview placed in another non-`*Preview.kt` file escapes.

Procedure (record all three in `docs/evidence/wiring-and-preview-hygiene/`):
1. Add the rule **before** moving anything and run `./gradlew :konsist-test:test`; save the failing run naming the 7
   files (`konsist-failing-run.txt`).
2. Move the previews; rerun green (`konsist-green-run.txt`).
3. Temporarily add a `@Preview` function to a `*Screen.kt` (for example `PastJamsScreen.kt`), see rule 22 fail, remove it
   (`konsist-negative-run.txt`).

Update `.claude/skills/architecture/SKILL.md`: the Konsist rule count 21 → 22 and a one-sentence "As built" note (rule 22,
previews live in `*Preview.kt`, `RunLock`/`LineupReservations`/`statusProbe` are required constructor parameters).

## Verification and evidence (save under `docs/evidence/wiring-and-preview-hygiene/`)

1. Baseline: `CI=true ./init.sh` exit 0, konsist, detekt and ktlint all `wired`; testcase count (`baseline-count.txt`, same
   method as the previous slice).
2. Greps: before/after of constructor defaults (`RunLock()`, `AssistantStatusProbe()`, `LineupReservations()` in main) and
   `grep -rln "@Preview" feature/*/src/main --include=*Screen.kt --include=*Views.kt` returning nothing after.
3. The Konsist procedure above.
4. Final `CI=true ./init.sh` exit 0, three tools `wired`, 22 Konsist rules, testcase count = baseline + 1 (rule 22) and
   nothing else changed; `:app:assembleDebug` compiles; `git diff -- app` is empty.
5. No device run needed: previews are IDE-only and the screens are unchanged; say so. Optionally render one preview in
   Android Studio and note it was not done if skipped.

## Out of scope

Moving `:core:ui` component previews, any change to presenters/UiModels, `RunLock` semantics, and Koin module shapes beyond
the explicit arguments above. Do not mark the feature `accepted`; set `passing` and fill `evidence` in `feature_list.json`.
