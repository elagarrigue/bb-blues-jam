# Feature Implementation Spec: Song detail screen

## Source Feature

- `id`: song-detail-screen
- `area`: feature-song-detail
- `depends_on`: `song-row-expansion` (`accepted`)
- `status`: not_started at planning time
- `source`: `feature_list.json` (entry, verification and notes)
- **Sequencing:** this slice edits `NextJamPresenter.kt`, `NextJamUiModel.kt`, `NextJamScreen.kt`,
  `NextJamCopy.kt`, their tests and `ModuleIsolationTest.kt`, all touched by `list-states`, which
  is now `accepted` (commit `797bb2f`). No wait is needed; read those files as they are at HEAD.
- **Approvals:** N1, K1, G1 and C1 are answered (User Approvals, commit `86b5d11`). N1 chose
  Navigation Compose now, and this revision follows it. **Still blocked on R1 and K2**, the two new
  questions that revision raised.
- **Size:** at the top of one slice: a new module, two small `:core:ui` components, the row entry
  point, and the `:app` navigation host with its first library dependency. If the implementer runs
  out of session, the safe cut is after step 3 of the plan (module and components compile and are
  tested; the entry point and the nav host follow).

## Goal

A musician opens one song of the upcoming setlist and sees, on its own screen: the title, the
artist, **the key displayed very large** (the screen's main element, in the amber `key` role), and
the full lineup **grouped by instrument**, open and filled slots, then `Otros`. The screen is a new
module `:feature:song-detail`; it is opened from the expanded song row of Próxima jam and closed by
a visible back button or the system back. Read only. Navigation between the two is the app's first
use of Navigation Compose (N1), hosted in `:app` only: features expose screens with plain
parameters and callbacks and never see the library (D-03).

## Non-Goals

- Tempo, tags, difficulty, `songsterrId`, the tab button and `songsterr-browser-link` (D-20,
  blocked). The screen never reads `CatalogRepository` or `Song`; it reads `JamSong` only.
- Artwork or any enrichment field (D-09; `enrichment-background-fetch` is a separate, not-started
  slice). No external API call anywhere in this slice.
- Admin controls (edit key, lineup, assignments): `admin-set-key`, `admin-adjust-lineup`,
  `admin-assign-musician`. No mutation of any kind (D-13 is untouched).
- The bottom bar, tab destinations and per-tab state: `bottom-navigation`. This slice only adds
  the library and two destinations (the existing temporary tabs as one, the detail as the other).
- Deeplinks (no `deepLinks` on any destination, no manifest intent filter), type-safe routes
  unless R1 says otherwise, screen transitions beyond "none", navigation testing artifacts.
- Opening a song from a past jam (`past-jam-detail` decides; the lookup below tolerates it).
- The staleness notice, error block or retry on the detail screen (Decision 6).
- Jam date, venue or position on the detail screen; swipe between songs.

## Job Story

When I am about to go up on stage and want to check one song, I want to open it and read its key at
a glance from arm's length, and see who plays each instrument, so I can tune and know who I play with.

## Users And Permissions

Anyone; read only. The admin flag is not read (admin controls are later slices, D-15).

## Decisions

**1. Module and dependencies.** New `:feature:song-detail`, package `com.bbbjam.feature.songdetail`,
build file like `feature/next-jam/build.gradle.kts`: `id("bluesjam.android.feature")`,
`implementation(project(":core:ui"))`, `implementation(project(":core:data"))`, foundation and
tooling-preview (+ debug tooling). No dependency on `:feature:next-jam` and vice versa (D-03); no
new library in any feature or `:core` module. `:app` adds `implementation(project(
":feature:song-detail"))`, `implementation(libs.androidx.navigation.compose)` (Decision 11) and
`songDetailModule` to `startKoin`. `settings.gradle.kts` includes the module.

**2. Identity and lookup.** A JamSong is identified by jam date + position (its Sheet identity; the
key and lineup belong to the JamSong, not the Song, D-08). `SongDetailPresenter(jams:
JamsRepository) : Presenter<SongDetailUiModel, SongDetailPresenter.Params>`, `Params(jamDate:
LocalDate, position: Int, onBack: () -> Unit)`. It collects `jams.observeJams()` (cache-first;
the repository may start its own background refresh, as for the list) and looks the jam up by date
in `listOfNotNull(upcoming) + past`, then the song by `position` in `Setlist.Available.songs`. Pure
mapping in `JamsSnapshot.toSongDetail(jamDate, position, onBack)`:

| Input | Model |
|---|---|
| no emission yet | `Loading(description, back)` |
| jam found, setlist `Available`, position present | `Song(...)` |
| anything else (no such jam or position, `Withheld`, `Unavailable`, empty snapshot after a cache read failure) | `NotFound(empty, back)` |

A later snapshot moves between rows live (a refresh that drops the row turns `Song` into
`NotFound`). The key shown is exactly `JamSong.key.value`; never `Song.defaultKey` (D-08).

**3. UiModel** (`SongDetailUiModel.kt`, every string in the model, no domain type):
`sealed interface SongDetailUiModel : UiModel { val back: BackUiModel }` with `Loading(description,
back)`, `NotFound(empty: EmptyStateUiModel, back)`, `Song(title, artist: String?, keyLabel, key,
keyDescription, lineup: InstrumentGroupsUiModel, back)`. `artist` is null when blank.
`BackUiModel(label, events: EventHandler<Event>)` with `Event.Back` lives in `:core:ui` (Decision
5). `Song` has **no** field for tempo, tags, difficulty, songsterr or artwork (D-20 test).

**4. Lineup grouped by instrument** (G1). New pure mapper in `:core:ui`
`com.bbbjam.core.ui.lineup`: `Lineup.toInstrumentGroups(extras): InstrumentGroupsUiModel` →
`groups: List<InstrumentGroupUiModel(heading, lines: List<LineupLineUiModel>)>`, `extras:
List<LineupLineUiModel>`, `noOpenSlotsNote: String?`, `hint: String?`. Groups follow the order in
which each instrument first appears in the lineup (Sheet column order); an instrument with zero
slots has no group (D-18). Within a group, open lines first, then filled, each in lineup order
(stable). Extras are never merged into an instrument group, even "guitarra" typed as free text
(D-18): they go last under `Otros`. Lines reuse the panel's `LineupLineUiModel` (same
descriptions: "Guitarra: libre", "Guitarra: Tincho", "Otros: saxo, Juan"). Note and hint are the
panel's approved copy and rules (note when no slot is open, hint when at least one is). Composable
`InstrumentGroups(model)` beside `LineupPanel`: caption note/hint first, then per group an
uppercase caption heading (`heading()` semantics) and its lines, then `OTROS` and the extras. A
slot line draws the glyph and the detail only (the heading names the instrument); an extra line
draws `+ saxo` and the name. Make `LineupPanel.kt`'s private `Line` internal with a
`showInstrument` flag and **fix the validation note**: the instrument `Text` gets
`overflow = TextOverflow.Ellipsis` and a bounded width (`widthIn(max =
LineupPanelDefaults.INSTRUMENT_MAX_WIDTH)`, a `:core:ui` constant, proposed 160dp) so a long
free-text extra cannot squeeze the name. Not interactive, compact lines, no 48dp minimum, amber
only inside `:core:ui` (DESIGN.md "Song row, expanded" rules).

**5. Back control.** `com.bbbjam.core.ui.nav.BackButton(model: BackUiModel)`: an icon button
(`Icons.AutoMirrored.Filled.ArrowBack` from the already-declared `material-icons-core`), tint
`text`, never amber, 48dp touch target, `contentDescription = model.label`. In `:core:ui` because
features do not depend on the icons library and `past-jam-detail` will need it.

**6. States on the detail screen.** `Loading` draws the back button and nothing else (a local Room
read, normally under a frame; no spinner, no skeleton: `SkeletonList` is a list and would flash).
`NotFound` draws the back button and `EmptyStateBlock` (reused from `com.bbbjam.core.ui.state`).
No staleness notice: the musician arrives from the list, which shows it, and the detail updates
live when a refresh lands. Recorded as a risk.

**7. Layout** (`SongDetailScreen(jamDate, position, onBack, modifier, contentPadding, presenter =
koinInject())` + `SongDetailContent(model, …)`), a `LazyColumn` on `background`, padding from the
tokens plus `contentPadding`: back button; title (`h1`, `text`, wraps); artist (`body`,
`textMuted`) when present; key block: label `TONALIDAD` (caption, `textMuted`, uppercase) and the
key in `SongDetailDefaults.keyStyle` (K1), colour `colors.key`, `contentDescription =
keyDescription` (the label is merged away so TalkBack reads "Tonalidad Bm" once). `keyStyle` is
the new `BluesJamTypography.keyDisplay` (K1 approved: Barlow Condensed ExtraBold, 96sp, line
height 96sp; read through `BluesJamTheme.typography.keyDisplay`, documented in DESIGN.md's front
matter, covered by `BluesJamTypographyTest`); then the lineup
(`InstrumentGroups`) on a `surface` block with `shapes.md`. All text styles the screen uses come
from `SongDetailDefaults` (`keyStyle`, `titleStyle = h1`, `bodyStyle`, `captionStyle`), so a JVM
test can prove the key is the largest. No dp/sp/colour literal (Konsist).

**8. Entry point** (design prompt §1: "En la fila expandida también: … acceso al detalle
completo"). `SongRowUiModel` gains `detailLabel` and `Event.OpenDetail`. `NextJamScreen` draws,
only while a row is expanded, under the `LineupPanel`, a full-width text action (`body`, `text`,
underlined like "Ver todos los temas", `heightIn(min = LocalMinimumInteractiveComponentSize
.current)`, `Role.Button`), not amber. The header toggle is unchanged (header-only toggle, approved).
`NextJamPresenter` becomes `Presenter<NextJamUiModel, NextJamPresenter.Params>` with
`Params(onOpenSong: (jamDate: LocalDate, position: Int) -> Unit = { _, _ -> })`; the presenter
reads it through `rememberUpdatedState`, so an earlier model's handler calls the current callback.
`NextJamScreen(onOpenSong = …)` passes it. Mechanical update of the ~30 `present(Unit)` call sites.

**9. Navigation host in `:app`** (N1, Navigation Compose). New `app/src/main/java/com/bbbjam/
navigation/AppNavHost.kt` (`internal`) and `AppRoutes.kt`. `MainActivity` sets
`BluesJamTheme { AppNavHost() }`. In `AppNavHost`: `val nav = rememberNavController()`, `NavHost(nav,
startDestination = AppRoutes.TABS, enterTransition = { EnterTransition.None }, exitTransition = {
ExitTransition.None }, popEnterTransition = …None, popExitTransition = …None)` (the library's
default is a 700 ms fade; motion is left to `bottom-navigation`), with two destinations:

- `composable(AppRoutes.TABS)` draws today's `TemporaryTabs(onOpenSong = { date, position ->
  nav.navigate(AppRoutes.songDetail(date, position)) { launchSingleTop = true } })`.
  `TemporaryTabs` gains only that parameter and passes it to `NextJamScreen(onOpenSong = …)`;
  Info is unchanged. The two temporary tabs stay one destination, switched by the existing
  `rememberSaveable` tab state, until `bottom-navigation`.
- `composable(AppRoutes.SONG_DETAIL, arguments = listOf(navArgument(JAM_DATE) { type =
  NavType.StringType }, navArgument(POSITION) { type = NavType.IntType }))` reads the arguments,
  parses them with `AppRoutes.parseSongDetail(jamDate: String?, position: Int?):
  SongDetailArgs?` (pure; ISO date via `LocalDate.parse` guarded by `DateTimeParseException`,
  position ≥ 1; anything else → null) and draws `SongDetailScreen(jamDate, position, onBack =
  …, contentPadding = WindowInsets.systemBars.asPaddingValues())` full screen (no temporary
  bar). Null args → `LaunchedEffect(Unit) { nav.popBackStack() }` and nothing drawn (unreachable
  from the UI, defensive).

**Back.** System back and predictive back are the NavHost's own (it pops the detail; on `TABS` the
activity finishes as today). The visible button calls `onBack`, bound to a pop guarded against a
double tap: `if (entry.lifecycle.currentState == Lifecycle.State.RESUMED) nav.popBackStack()`
(`entry` is the destination's `NavBackStackEntry`), so a second tap during the pop never pops
`TABS` and leaves a blank host.

**State on return.** Nothing extra is needed: when `TABS` leaves composition, its
`NavBackStackEntry` keeps the `rememberSaveable` state of everything inside it (selected tab,
`ExpandedRows`, the filter set, the `LazyColumn`'s scroll position), and restores it when popped
back. The detail's `onBack` and `onOpenSong` are plain lambdas; presenter `Params` stay as in
Decisions 2 and 8. Rotation and process death keep the back stack and the route arguments
(`rememberNavController` is saveable).

**10. Amber allowlist.** `AMBER_ROLE_ALLOWLIST` gains `"feature/song-detail" to setOf("key")` —
the reviewed amber use the architecture skill requires in the same diff.

**11. Library and version.** Catalog: `navigationCompose = "2.9.8"` and
`androidx-navigation-compose = { group = "androidx.navigation", name = "navigation-compose",
version.ref = "navigationCompose" }`, with a comment giving the pin's reason, as the data stack
pins have. Declared in `app/build.gradle.kts` only (module-specific libraries stay in the module
file; no convention change). Facts read from Google Maven's POMs and `.module` files on 4 October
2026 (not yet from a Gradle resolution):

| | 2.9.8 (22 Apr 2026, last 2.9.x) | 2.10.x (2.10.2 latest) |
|---|---|---|
| kotlin-stdlib | 2.0.21 | 2.1.20 |
| Compose runtime/ui | 1.7.2 | 1.10.5 |
| activity-compose | 1.8.0 | 1.13.0 |
| lifecycle | 2.9.0 (viewmodel-compose 2.8.2/2.9.0) | up to 2.11.0 |
| kotlinx-serialization-core | 1.7.3 | 1.7.3 |
| savedstate | 1.3.0 | — |

2.10.x would raise Compose to 1.10 over the pinned BOM 2025.09.00 (Compose 1.9.1) and
activity-compose past 1.11.0, the Koin 4.2 trap again. 2.9.8 asks only for versions at or below
what the build already resolves (stdlib 2.2.10, Compose 1.9.1 from the BOM, activity-compose
1.11.0, lifecycle 2.9.3 from Koin, serialization-core 1.9.0 from `:core:data`, coroutines 1.10.2),
so nothing should move up. Its `minSdk` is 21 (≤ 24). The implementer verifies this, see
Verification. If any of those versions moves, stop and report instead of upgrading the pins.
Navigation brings the lifecycle ViewModel artifacts transitively, as Koin does; that does not
relax the ViewModel ban (D-02), and no code may use `viewModel()` or `hiltViewModel`.

**12. Routes are strings** (R1, pending). `AppRoutes` (`internal object` in `:app`): `TABS =
"tabs"`, `SONG_DETAIL = "song/{jamDate}/{position}"`, `songDetail(date, position) =
"song/$date/$position"` (`LocalDate.toString()` is ISO, no locale), the argument names, and
`parseSongDetail`. Type-safe routes would need `@Serializable` route classes in `:app`, which
breaks two Konsist rules as written (`only core data imports the data libraries` and its qualified
twin both forbid `kotlinx.serialization.` outside `:core:data`), and the serialization compiler
plugin in `:app`, which a module build file cannot apply raw (`build-file-applies-convention`), so
it would need a convention change too. String routes need neither. Routes live only in `:app`.

**13. Features never see navigation** (K2, pending). No `androidx.navigation` import or dependency
outside `:app`. Features take `onOpenSong`/`onBack` callbacks and plain values. K2 proposes a
Konsist rule to enforce it; without K2, a grep in the evidence.

## Copy (C1; Rioplatense, vos, D-12)

| Where | Text |
|---|---|
| Expanded row action (`NextJamCopy.OPEN_DETAIL`) | `Ver detalle del tema` |
| Back button description (`:core:ui` `NavCopy.BACK`) | `Volver` |
| Key label (drawn uppercase) | `Tonalidad` |
| Key description | `Tonalidad Bm` (same form as the approved row description) |
| Loading, screen readers only | `Cargando el tema` |
| Not found, title | `Este tema ya no está en la lista` |
| Not found, message | `Puede que la organización haya cambiado la lista. Volvé a la próxima jam para ver la actual.` |

Reused, already approved: instrument names, `LIBRE`, `Otros`, the hint and `No quedan cupos libres.`
Feature copy lives in `internal object SongDetailCopy`.

## Acceptance Scenarios

1. Given an upcoming published jam, when the musician expands song 2 and taps `Ver detalle del
   tema`, then the detail opens full screen (no temporary tab bar) with its title, artist, the key
   in the amber role larger than any other text, and the lineup grouped by instrument with `Otros`
   last.
2. Given the detail is open, when the musician taps the back button or presses system back, then
   Próxima jam returns with the same tab, the same rows expanded, the same filter and scroll
   position.
3. Given the back button is tapped twice quickly, then only the detail is popped; Próxima jam is
   shown, never a blank screen.
4. Given a song with blank artist, no extras and an empty lineup, then the detail shows title and
   key, no artist line, and `No quedan cupos libres.`; no error state.
5. Given the catalog song has tempo, tags, difficulty and songsterrId, then none appears (D-20).
6. Given the detail is open, when a refresh removes that row or withholds the setlist, then the
   detail shows the not-found block; back still works.
7. Given rotation on the detail, then the same song stays open, and back still restores the list.

## Repository Research

Inspected: `AGENTS.md`, `PROGRESS.md`, `feature_list.json`, `CONTEXT.md`, `DESIGN.md`,
`bb-blues-jam-design-prompt.md` (§1, §3), `bb-blues-jam-bitacora.md` (Detalle del tema, D-20),
`.claude/skills/architecture/SKILL.md`, specs `list-states`, `song-row-expansion` and
`catalog-repository-cache` (the stdlib pins), `app/.../TemporaryTabs.kt`, `MainActivity.kt`,
`BluesJamApp.kt`, `app/build.gradle.kts`, root `build.gradle.kts`, `settings.gradle.kts`,
`gradle/libs.versions.toml`, `build-logic/convention` (the plugin list,
`AndroidApplicationConventionPlugin`, `AndroidDataConventionPlugin`), `feature/next-jam/`
(presenter, screen, UiModel, copy, Koin module and its test), `core/ui/.../lineup/*`,
`strip/InstrumentStripCopy.kt`, `state/` signatures, `theme/BluesJamTypography.kt`,
`BluesJamDimens.kt`, `core/model` `JamSong`, `Song`, `Key`, `Lineup`, `Instrument`,
`core/data/.../JamsSnapshot.kt`, and in `ModuleIsolationTest.kt` the amber allowlist, the
data-library rules and `build-file-applies-convention` (`RAW_PLUGIN`). Outside the repo: Google
Maven metadata, POMs, `.module` files and the AAR manifests of `navigation-compose` 2.9.x/2.10.x.
Not inspected: `presenter-pattern.md` beyond its Params examples; no Gradle resolution was run.

Findings: `JamSong` has no tempo/tags/difficulty/songsterrId, so D-20 holds by construction if the
feature never reads `Song`. `Key` is at most three characters (`Bbm`). `activity-compose` 1.11.0
is in `:app`. `material-icons-core` is `implementation` in `:core:ui` only. The serialization
plugin is in the catalog (`kotlin-serialization`), declared at the root with `apply false`, and
applied only by `bluesjam.android.data`; a raw `alias(libs.plugins…)` in `app/build.gradle.kts`
fails `build-file-applies-convention`, and `import kotlinx.serialization.…` in `:app` fails
`only core data imports the data libraries`. The `:app` convention does not set
`isReturnDefaultValues`, so `:app` JVM tests must not touch Android classes (`Bundle`). There is
no upcoming jam in the real Sheet today.

## Expected File Changes

- `gradle/libs.versions.toml` — `navigationCompose = "2.9.8"` + library alias, with the pin comment.
- `app/build.gradle.kts` — `:feature:song-detail`, `libs.androidx.navigation.compose`.
- `settings.gradle.kts` — include `:feature:song-detail`.
- `app/src/main/java/com/bbbjam/navigation/AppNavHost.kt`, `AppRoutes.kt` — create.
- `MainActivity.kt` — `AppNavHost()` instead of `TemporaryTabs()`; `TemporaryTabs.kt` — the
  `onOpenSong` parameter only; `BluesJamApp.kt` — `songDetailModule`.
- `app/src/test/java/com/bbbjam/navigation/AppRoutesTest.kt` — create.
- `feature/song-detail/` — create: `.gitignore`, `build.gradle.kts`, `SongDetailPresenter.kt`,
  `SongDetailUiModel.kt`, `SongDetailScreen.kt` (+ previews), `SongDetailCopy.kt`,
  `SongDetailDefaults.kt`, `di/SongDetailModule.kt`; tests `SongDetailPresenterTest`,
  `SongDetailMappingTest`, `SongDetailDefaultsTest`, `SongDetailModelShapeTest`,
  `SongDetailModuleTest`, `FakeJamsRepository` (own copy; no test sharing across features).
- `core/ui/.../lineup/` — create `InstrumentGroups.kt`, `InstrumentGroupsMapper.kt`,
  `InstrumentGroupsUiModel.kt`; modify `LineupPanel.kt` (`Line` internal, ellipsis, width bound),
  `LineupPanelDefaults.kt`; test `InstrumentGroupsMapperTest`.
- `core/ui/.../nav/` — create `BackButton.kt`, `BackUiModel.kt`, `NavCopy.kt`. (`nav` is a package
  name for the back control, not the navigation library; nothing in `:core:ui` imports it.)
- `core/ui/.../theme/BluesJamTypography.kt` (+ `BluesJamTheme` accessor if typography is exposed
  through it) and `BluesJamTypographyTest` — `keyDisplay` (K1).
- `feature/next-jam/` — modify presenter, UiModel, screen, copy, tests (Decision 8).
- `konsist-test/.../ModuleIsolationTest.kt` — allowlist entry; the K2 rule if approved (17/17).

## Durable Documentation Impact

- `.claude/skills/architecture/SKILL.md`: update — `:feature:song-detail` exists; `nav/` and
  `InstrumentGroups` in `:core:ui`; presenter Params carrying a navigation callback via
  `rememberUpdatedState`; the module table's `:app` row (Navigation Compose host, `AppRoutes`);
  "Still Open: Navigation library" resolved (Navigation Compose 2.9.8, string routes or as R1
  says, deeplink scheme still open); the navigation pin and its reason beside the data stack pins;
  the K2 rule in the Konsist list if approved.
- `DESIGN.md`: update — Song detail section (layout, grouping, open first within a group, no
  transition); `keyDisplay` in the front matter.
- `docs/risks-and-open-questions.md`: update — no staleness notice on the detail; previews not
  rendered by the gate; NavHost behaviour verified only on the device.
- `docs/technical-discovery.md`: update the stack list with Navigation Compose 2.9.8.
- `bb-blues-jam-bitacora.md`: not by the implementer (the orchestrator adds entries).
- `AGENTS.md`, `ARCHITECTURE.md`, `CONSTRAINTS.md`: not needed (no workflow change; the
  architecture lives in the skill).
- `PROGRESS.md`, `feature_list.json`: update with evidence. **Note for `bottom-navigation`** (to be
  recorded by the orchestrator, not edited here): the library is chosen. That slice adds the tab
  destinations to `AppNavHost`, replaces the `TABS` destination and `TemporaryTabs` with
  per-tab routes (`saveState`/`restoreState`), keeps `AppRoutes.SONG_DETAIL` above the tabs, and
  owns transitions and the deeplink scheme. Its verification "each tab keeps its state" now rests on
  the back stack, not on a library decision.

## Implementation Plan

1. Re-read the next-jam files at HEAD (after `list-states`).
2. Record the dependency baseline (Verification, dependency check) before touching the catalog.
3. `:core:ui`: `keyDisplay`; `BackButton` + model + copy; `InstrumentGroups` mapper, model,
   composable, the `Line` fix; tests.
4. `:feature:song-detail` module, presenter, mapping, screen, previews, Koin module; tests.
5. `:feature:next-jam`: `OpenDetail` event, `detailLabel`, Params, the action; tests.
6. `:app`: catalog entry and dependency, compare the dependency report with the baseline;
   `AppRoutes` + test, `AppNavHost`, `MainActivity`, `TemporaryTabs` parameter, `startKoin`.
7. Allowlist entry and, if approved, the K2 rule (shown failing first); failure demonstrations;
   gate; docs; evidence.

## Verification Plan

- `CI=true ./init.sh` exit 0; `konsist: wired` (16/16, or 17/17 with K2), `detekt: wired`,
  `ktlint: wired`; no baseline, `ignoreFailures`, `@Suppress` or rule disable. Per-class counts.
- **Dependency check** (the mechanism earlier slices used, `catalog-repository-cache` and
  `info-screen`). Before step 6 and after it, save
  `./gradlew :app:dependencies --configuration debugRuntimeClasspath` and
  `--configuration releaseRuntimeClasspath`, and run `./gradlew :app:dependencyInsight
  --dependency org.jetbrains.kotlin:kotlin-stdlib --configuration debugRuntimeClasspath`. Required
  after: every `kotlin-stdlib` request resolves to **2.2.10**; `androidx.compose.runtime:runtime`
  and `ui` stay at the BOM's version (1.9.1); `material3` 1.3.2; `activity-compose` 1.11.0;
  `kotlinx-serialization-core` 1.9.0; `kotlinx-coroutines-core` 1.10.2; lifecycle 2.9.3 (Koin's);
  the only new groups are `androidx.navigation` (and anything else, listed and explained). Diff
  the two saved reports and record the added lines. `:app:checkDebugAarMetadata` passes (it runs in
  `build`; it fails on a too-high `compileSdk`/`minSdk` requirement). Any upward move → stop and
  report.
- `AppRoutesTest` (JVM, `:app`): `songDetail(2026-10-31, 2)` = `song/2026-10-31/2`; the pattern
  holds both argument names; `parseSongDetail` accepts that, rejects a null, a malformed date, `0`
  and a negative position; a round trip for a date in every month form (single-digit day/month).
- `InstrumentGroupsMapperTest`: default lineup → six groups in Sheet order, Guitarra with two open
  lines; mixed lineup → open before filled inside Guitarra; zero-slot instrument has no group; an
  extra typed "guitarra" stays in `extras`; note vs hint; descriptions; empty lineup.
- `SongDetailMappingTest` (pure `toSongDetail`, strings written out): every row of the Decision 2
  table; found in `past`; blank artist → null; key equals `JamSong.key`, not a different
  `Song.defaultKey`.
- `SongDetailPresenterTest` (Molecule + Turbine, fake repo): Loading → Song; a new snapshot without
  the row → NotFound (a transition, not only the first emission); `Back` calls `onBack`.
- `SongDetailModelShapeTest` (D-20): the declared field names of `SongDetailUiModel.Song` (Java
  reflection, `declaredFields`) are exactly the expected set and none matches
  `tempo|tag|difficulty|songsterr|artwork`; the presenter constructor takes only `JamsRepository`.
  Plus a grep: none of those words in `feature/song-detail/src/main`.
- `SongDetailDefaultsTest`: `keyStyle` is `keyDisplay` (96sp) and its `fontSize` > every other
  style the screen uses and > the `:core:ui` component styles it embeds (body, caption); key
  colour role is `key`. `BluesJamTypographyTest`: `keyDisplay` family, weight, size, line height.
- `SongDetailModuleTest`: factory resolves a new presenter each time with fake data bindings.
- `NextJamPresenterTest` additions: `OpenDetail` on row 2 calls `onOpenSong(date, 2)`; an earlier
  model's handler calls the **current** callback after Params change; `detailLabel` copy.
- **Failure demonstrations** (edit, run, restore, sha1 matches): (1) `keyStyle = key` → defaults
  test fails; (2) add `val tempo: String?` to the `Song` model → shape test fails; (3)
  `colors.slotOpen` in `SongDetailScreen.kt` → `amber-roles-allowlisted` fails; (4) filled before
  open in the group mapper → mapper test fails; (5) drop `rememberUpdatedState` → the stale-handler
  test fails; (6) one copy string changed → a test fails; (7) `parseSongDetail` accepting position
  0 → `AppRoutesTest` fails; (8) with K2, `import androidx.navigation.NavController` in a
  `:feature:song-detail` source → the new rule fails (the import need not compile: Konsist reads
  sources; run only `:konsist-test:test`).
- Greps: `feature/` and `core/` sources and build files contain no `androidx.navigation` and no
  `navigation` library alias (the K2 evidence if K2 is declined); `:app` has no
  `kotlinx.serialization`, `viewModel(` or `deepLinks`; `feature/song-detail/src/main`: no
  `MaterialTheme.`, no `Color(`, no `.dp`/`.sp` literal, no `LocalUriHandler`/`Intent`, no tú
  forms; `colors.key` the only amber read.
- Lint: `:app:lintDebug` — record the warning count against today's 14; a navigation lint issue
  is fixed, not suppressed.
- Previews: `SongDetailScreen.kt` has a preview built through `toSongDetail` from a JamSong with
  only required fields (blank artist, no extras, default lineup) and one full song; they compile in
  the gate but are not rendered by it. Render them in Android Studio if available, else record so.
- **Device (Pixel 5, outside the gate, needs the user; no agent reads the Sheet).** The NavHost
  cannot run on the JVM without Robolectric (T1 declined), so this is the only check of the
  navigation itself. User steps as in `song-row-expansion`: A. temporary upcoming jam tab + `Jams`
  row; B. fill a few slots and an `Otros`. Then, with screenshots and `uiautomator dump`: scroll a
  little, select a filter, expand a row, tap `Ver detalle del tema` → detail full screen, no tab
  bar; key bounds the tallest text; back button ≥ 48dp, description `Volver`; one node
  "Tonalidad X". Back button → list with the same filter, expansion and scroll offset. Open again,
  system back → the same. Open, double-tap the back button fast → list, not blank. Open, rotate →
  same song; back → list restored. On the list, system back → the app closes as today. Largest
  font size: the key fits on one line. D. The user deletes the temporary jam; relaunch confirms
  `upcoming none`. Crash buffer and AndroidRuntime log empty.

## Evidence To Capture

Gate output and counts; the before/after dependency reports' diff and the `dependencyInsight`
excerpt for kotlin-stdlib; each demonstration's failure; greps; lint count; preview status; device
dumps and screenshots per step (no URL, no names beyond the test data); the user's cleanup
confirmation.

## Validator Checklist

- [ ] `:feature:song-detail` depends on `:core:*` only; no feature imports another; Koin wired in `:app`.
- [ ] Navigation Compose 2.9.8 only in `:app`; routes only in `AppRoutes`; no feature or `:core`
      module references `androidx.navigation`; no serialization in `:app`; no deeplink.
- [ ] kotlin-stdlib 2.2.10, Compose 1.9.1, activity-compose 1.11.0, serialization 1.9.0 unchanged
      on `:app`'s runtime classpaths (reports attached).
- [ ] Key from `JamSong`, `keyDisplay`, largest text, `key` role; no tempo/tags/difficulty/
      songsterr/artwork.
- [ ] Lineup grouped per Decision 4; extras never merged; panel `Line` fixed.
- [ ] Back button visible, 48dp, described, double-tap safe; system back; list state restored on
      return; rotation (device evidence).
- [ ] No mutation, no admin control, no external API, no ViewModel.
- [ ] Copy matches C1; allowlist `{key}` for the new module; K2 as answered; three `wired`.
- [ ] Demonstrations and evidence recorded; docs updated, including the architecture "Still Open".

## Risks

- No staleness notice on the detail: a musician who opens it offline sees no age (the list did).
- The temporary tabs are one destination: switching to Info still loses Próxima jam's state
  (unchanged until `bottom-navigation`).
- NavHost behaviour (back stack, state restoration, double tap, rotation) is verified only on the
  device; a regression there is invisible to the gate.
- 2.9.8 is the end of the 2.9 line; staying on the pinned BOM blocks Navigation fixes after it.
  Upgrade navigation together with Kotlin, the Compose BOM and Koin.
- String routes are checked only by `AppRoutesTest` and the device; a typo in an argument name
  inside `AppNavHost` is a runtime error (R1's cost).
- A 96sp key at 200% font scale is about 190sp; three condensed characters should fit a 360dp
  width (assumption, checked on the device).
- Grouping by instrument puts a filled Guitarra group above an open Bajo slot, an exception to
  DESIGN.md's "open above filled, always" (G1, approved).
- The device check depends on the user's temporary jam and its cleanup.

## User Approvals

Answered by the user on 4 October 2026:

- **N1:** **Navigation Compose now** (the alternative, not the recommendation): a new dependency,
  and the library choice is made here instead of in `bottom-navigation`. The spec body must be
  revised to match before implementation. *(Revised: Decisions 9, 11–13.)*
- **K1:** yes — new type role `keyDisplay`, Barlow Condensed ExtraBold 96sp.
- **G1:** grouped by instrument, open first within each, `Otros` last; DESIGN.md records the
  exception.
- **C1:** copy approved as written.

**New questions from the N1 revision — answered by the user on 4 October 2026:** R1 = string
routes (recommended); K2 = yes, add `navigation-only-in-app` in this slice, demonstrated failing
first (Konsist 17/17).

The questions as asked:

- **R1 — route style.** *Recommended:* string routes (Decision 12): `song/{jamDate}/{position}`,
  built and parsed only in `AppRoutes`, tested on the JVM. No serialization in `:app`, no build-logic
  or Konsist change. Alternative: type-safe `@Serializable` routes, which need (a) the
  serialization compiler plugin in `:app` through a convention (a new `bluesjam.android.navigation`
  or added to `bluesjam.android.application`), and (b) relaxing the two data-library Konsist rules
  to allow `kotlinx.serialization.Serializable` in `:app` — a verification-rule change.
- **K2 — new Konsist rule `navigation-only-in-app`** (a verification-rule change). Outside `:app`
  and `:konsist-test`, no import of `androidx.navigation.`; same shape and limits as the
  data-library import rule (a text match on imports; a fully qualified use escapes, but a feature
  has no navigation on its compile classpath, so it would not compile anyway). Shown failing before
  it is trusted (demonstration 8); Konsist 17/17. *Recommended:* add it in this slice, so the
  "features never see navigation" boundary that N1 creates is enforced, not only grepped.
  Alternative: the grep in the evidence only.

The questions as asked before (N1, K1, G1, C1):

- **N1 — navigation structure.** *Recommended:* state-based in `:app`: the detail opens full screen
  over `TemporaryTabs` (bar hidden), `BackHandler` + a visible back button, `SaveableStateHolder`
  restores Próxima jam, no new dependency; `bottom-navigation` later picks the library and may move
  it. Alternative: add Navigation Compose now (new dependency, pre-empts `bottom-navigation`'s
  decision).
- **K1 — key size.** *Recommended:* a new typography token `keyDisplay` (Barlow Condensed
  ExtraBold, 96sp, line height 96sp) in `BluesJamTypography` and `DESIGN.md`, used only by the
  detail. Alternative: reuse `key` (44sp), which is already the largest token but no bigger than in
  the row.
- **G1 — lineup grouping.** *Recommended:* grouped by instrument as the feature entry and design
  prompt say (Decision 4), open first within each group, `Otros` last; DESIGN.md records it as the
  detail's exception to "open above filled". Alternative: reuse `LineupPanel` unchanged (open,
  filled, Otros), which drops `InstrumentGroups` from this slice.
- **C1 — copy.** Approve the copy table, or give replacements. *Recommended:* approve as written.
- Not questions: the allowlist entry `feature/song-detail → {key}` (Decision 10), no `init.sh`
  change, no D-xx change. The Navigation Compose dependency itself is approved by N1.
