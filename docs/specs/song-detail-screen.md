# Feature Implementation Spec: Song detail screen

## Source Feature

- `id`: song-detail-screen
- `area`: feature-song-detail
- `depends_on`: `song-row-expansion` (`accepted`)
- `status`: not_started at planning time
- `source`: `feature_list.json` (entry, verification and notes)
- **Sequencing:** this slice edits `NextJamPresenter.kt`, `NextJamUiModel.kt`, `NextJamScreen.kt`,
  `NextJamCopy.kt`, their tests and `ModuleIsolationTest.kt`, all touched by `list-states` (commit
  `071755e`, `passing`, being validated). **Do not start implementing until `list-states` is
  `accepted`.** If the validator asks for a revision, re-read those files before starting.
- **Blocked on the User Approvals below** (N1, K1, G1, C1).

## Goal

A musician opens one song of the upcoming setlist and sees, on its own screen: the title, the
artist, **the key displayed very large** (the screen's main element, in the amber `key` role), and
the full lineup **grouped by instrument**, open and filled slots, then `Otros`. The screen is a new
module `:feature:song-detail`; it is opened from the expanded song row of Próxima jam and closed by
a visible back button or the system back. Read only.

## Non-Goals

- Tempo, tags, difficulty, `songsterrId`, the tab button and `songsterr-browser-link` (D-20,
  blocked). The screen never reads `CatalogRepository` or `Song`; it reads `JamSong` only.
- Artwork or any enrichment field (D-09; `enrichment-background-fetch` is a separate, not-started
  slice). No external API call anywhere in this slice.
- Admin controls (edit key, lineup, assignments): `admin-set-key`, `admin-adjust-lineup`,
  `admin-assign-musician`. No mutation of any kind (D-13 is untouched).
- A navigation library, deeplinks, the bottom bar, per-tab state: `bottom-navigation`.
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
new library. `:app` adds `implementation(project(":feature:song-detail"))` and `songDetailModule`
to `startKoin`. `settings.gradle.kts` includes it.

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
keyDescription` (the label is merged away so TalkBack reads "Tonalidad Bm" once); then the lineup
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

**9. Navigation in `:app`** (N1). In `TemporaryTabs.kt`: `var openSong by
rememberSaveable(stateSaver = OpenSongSaver) { mutableStateOf<OpenSong?>(null) }` (`internal data
class OpenSong(jamDate, position)`, saved as ISO date string + Int, survives rotation). When set,
the detail replaces the whole tab area and the temporary bar (full screen), with `BackHandler {
openSong = null }` and `onBack = { openSong = null }`; `contentPadding` = system bars insets. The
Próxima jam screen is wrapped in `rememberSaveableStateHolder().SaveableStateProvider("next-jam")`
so expanded rows, the filter and the list scroll position come back on return. No navigation
library; `bottom-navigation` replaces this file.

**10. Amber allowlist.** `AMBER_ROLE_ALLOWLIST` gains `"feature/song-detail" to setOf("key")` —
the reviewed amber use the architecture skill requires in the same diff. No new Konsist rule; 16/16.

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
   tema`, then the detail opens with its title, artist, the key in the amber role larger than any
   other text, and the lineup grouped by instrument with `Otros` last.
2. Given the detail is open, when the musician taps the back button or presses system back, then
   Próxima jam returns with the same rows expanded, the same filter and scroll position.
3. Given a song with blank artist, no extras and an empty lineup, then the detail shows title and
   key, no artist line, and `No quedan cupos libres.`; no error state.
4. Given the catalog song has tempo, tags, difficulty and songsterrId, then none appears (D-20).
5. Given the detail is open, when a refresh removes that row or withholds the setlist, then the
   detail shows the not-found block; back still works.
6. Given rotation on the detail, then the same song stays open.

## Repository Research

Inspected: `AGENTS.md`, `PROGRESS.md`, `feature_list.json`, `CONTEXT.md`, `DESIGN.md`,
`bb-blues-jam-design-prompt.md` (§1, §3), `bb-blues-jam-bitacora.md` (Detalle del tema, D-20),
`.claude/skills/architecture/SKILL.md`, specs `list-states` and `song-row-expansion`,
`app/.../TemporaryTabs.kt`, `MainActivity.kt`, `BluesJamApp.kt`, `app/build.gradle.kts`,
`settings.gradle.kts`, `feature/next-jam/` (presenter, screen, UiModel, copy, Koin module and its
test), `core/ui/.../lineup/*`, `strip/InstrumentStripCopy.kt`, `state/` signatures,
`theme/BluesJamTypography.kt`, `BluesJamDimens.kt`, `core/model` `JamSong`, `Song`, `Key`,
`Lineup`, `Instrument`, `core/data/.../JamsSnapshot.kt`, the amber allowlist in
`ModuleIsolationTest.kt`. Not inspected: `presenter-pattern.md` beyond its Params examples.

Findings: `JamSong` has no tempo/tags/difficulty/songsterrId, so D-20 holds by construction if the
feature never reads `Song`. `Key` is at most three characters (`Bbm`). The largest type token is
`key` 44sp. `activity-compose` (for `BackHandler`) is already in `:app`. `material-icons-core` is
`implementation` in `:core:ui` only. There is no upcoming jam in the real Sheet today.

## Expected File Changes

- `settings.gradle.kts`, `app/build.gradle.kts`, `BluesJamApp.kt`, `TemporaryTabs.kt` — modify.
- `feature/song-detail/` — create: `.gitignore`, `build.gradle.kts`, `SongDetailPresenter.kt`,
  `SongDetailUiModel.kt`, `SongDetailScreen.kt` (+ previews), `SongDetailCopy.kt`,
  `SongDetailDefaults.kt`, `di/SongDetailModule.kt`; tests `SongDetailPresenterTest`,
  `SongDetailMappingTest`, `SongDetailDefaultsTest`, `SongDetailModelShapeTest`,
  `SongDetailModuleTest`, `FakeJamsRepository` (own copy; no test sharing across features).
- `core/ui/.../lineup/` — create `InstrumentGroups.kt`, `InstrumentGroupsMapper.kt`,
  `InstrumentGroupsUiModel.kt`; modify `LineupPanel.kt` (`Line` internal, ellipsis, width bound),
  `LineupPanelDefaults.kt`; test `InstrumentGroupsMapperTest`.
- `core/ui/.../nav/` — create `BackButton.kt`, `BackUiModel.kt`, `NavCopy.kt`.
- `core/ui/.../theme/BluesJamTypography.kt` + `BluesJamTypographyTest` — only if K1 approves a token.
- `feature/next-jam/` — modify presenter, UiModel, screen, copy, tests (Decision 8).
- `konsist-test/.../ModuleIsolationTest.kt` — allowlist entry only.

## Durable Documentation Impact

- `.claude/skills/architecture/SKILL.md`: update — `:feature:song-detail` exists; temporary
  detail navigation in `TemporaryTabs`; `nav/` and `InstrumentGroups` in `:core:ui`; presenter
  Params carrying a navigation callback via `rememberUpdatedState`.
- `DESIGN.md`: update — Song detail component section (layout, grouping, open-first within a
  group); `keyDisplay` token in the front matter if K1 approves.
- `docs/risks-and-open-questions.md`: update — no staleness notice on the detail; temporary
  navigation; previews not rendered by the gate.
- `AGENTS.md`, `ARCHITECTURE.md`, `CONSTRAINTS.md`: not needed (no workflow change; the
  architecture lives in the skill; no new MUST rule).
- `PROGRESS.md`, `feature_list.json`: update with evidence.

## Implementation Plan

1. Wait for `list-states` `accepted`; re-read the next-jam files.
2. `:core:ui`: `BackButton` + model + copy; `InstrumentGroups` mapper, model, composable, the
   `Line` fix; tests. Token if K1.
3. `:feature:song-detail` module, presenter, mapping, screen, previews, Koin module; tests.
4. `:feature:next-jam`: `OpenDetail` event, `detailLabel`, Params, the action; tests.
5. `:app`: dependency, `startKoin`, `TemporaryTabs` navigation with `SaveableStateHolder`.
6. Allowlist entry; failure demonstrations; gate; docs; evidence.

## Verification Plan

- `CI=true ./init.sh` exit 0; `konsist: wired` (16/16), `detekt: wired`, `ktlint: wired`; no
  baseline, `ignoreFailures`, `@Suppress` or rule disable. Per-class test counts recorded.
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
- `SongDetailDefaultsTest`: `keyStyle.fontSize` > every other style's `fontSize` in the screen's
  set and > the `:core:ui` component styles it embeds (body, caption); key colour role is `key`.
- `SongDetailModuleTest`: factory resolves a new presenter each time with fake data bindings.
- `NextJamPresenterTest` additions: `OpenDetail` on row 2 calls `onOpenSong(date, 2)`; an earlier
  model's handler calls the **current** callback after Params change; `detailLabel` copy.
- **Failure demonstrations** (edit, run, restore, sha1 matches): (1) `keyStyle = h1` → defaults
  test fails; (2) add `val tempo: String?` to `Song` model → shape test fails; (3)
  `colors.slotOpen` in `SongDetailScreen.kt` → `amber-roles-allowlisted` fails; (4) filled before
  open in the group mapper → mapper test fails; (5) drop `rememberUpdatedState` → the stale-handler
  test fails; (6) one copy string changed → a test fails.
- Greps on `feature/song-detail/src/main`: no `MaterialTheme.`, no `Color(`, no `.dp`/`.sp`
  literal, no `LocalUriHandler`/`Intent`, no tú forms; `colors.key` the only amber read.
- Previews: `SongDetailScreen.kt` has a preview built through `toSongDetail` from a JamSong with
  only required fields (blank artist, no extras, default lineup) and one full song; they compile in
  the gate but are not rendered by it. Render them in Android Studio if available, else record so.
- **Device (Pixel 5, outside the gate, needs the user; no agent reads the Sheet).** User steps as in
  `song-row-expansion`: A. temporary upcoming jam tab + `Jams` row; B. fill a few slots and an
  `Otros`. Then: expand a row, tap `Ver detalle del tema`, screenshot + `uiautomator dump` (key
  bounds the tallest text; back button ≥ 48dp, description `Volver`; one node "Tonalidad X");
  system back and button back return with the row still expanded and the filter kept; rotate on the
  detail. D. user deletes the temporary jam; relaunch confirms `upcoming none`. Crash buffer empty.

## Evidence To Capture

Gate output and counts; each demonstration's failure; greps; preview status; device dumps and
screenshots (no URL, no names beyond the test data); the user's cleanup confirmation.

## Validator Checklist

- [ ] `:feature:song-detail` depends on `:core:*` only; no feature imports another; Koin wired in `:app`.
- [ ] Key from `JamSong`, largest text, `key` role; no tempo/tags/difficulty/songsterr/artwork.
- [ ] Lineup grouped per Decision 4 (or as G1 answered); extras never merged; panel `Line` fixed.
- [ ] Back button visible, 48dp, described; system back; state restored on return; rotation.
- [ ] No mutation, no admin control, no external API, no navigation library.
- [ ] Copy matches C1 as approved; allowlist `{key}` for the new module; three `wired`.
- [ ] Demonstrations and evidence recorded; docs updated.

## Risks

- No staleness notice on the detail: a musician who opens it offline sees no age (the list did).
- `SaveableStateHolder` keeps the list's saved state only while `TemporaryTabs` lives; switching to
  Info still loses it (unchanged, `bottom-navigation`).
- A 96sp key at 200% font scale is about 190sp; three condensed characters fit a 360dp width
  (assumption, check on the device at the largest font).
- Grouping by instrument puts a filled Guitarra group above an open Bajo slot, an exception to
  DESIGN.md's "open above filled, always" (G1).
- The device check depends on the user's temporary jam and its cleanup.

## User Approvals

Pending. Each has a recommended option.

- **N1 — navigation structure.** *Recommended:* state-based in `:app` (Decision 9): the detail
  opens full screen over `TemporaryTabs` (bar hidden), `BackHandler` + a visible back button,
  `SaveableStateHolder` restores Próxima jam, no new dependency; `bottom-navigation` later picks the
  library and may move it. Alternative: add Navigation Compose now (new dependency, pre-empts
  `bottom-navigation`'s decision).
- **K1 — key size.** *Recommended:* a new typography token `keyDisplay` (Barlow Condensed
  ExtraBold, 96sp, line height 96sp) in `BluesJamTypography` and `DESIGN.md`, used only by the
  detail. Alternative: reuse `key` (44sp), which is already the largest token but no bigger than in
  the row.
- **G1 — lineup grouping.** *Recommended:* grouped by instrument as the feature entry and design
  prompt say (Decision 4), open first within each group, `Otros` last; DESIGN.md records it as the
  detail's exception to "open above filled". Alternative: reuse `LineupPanel` unchanged (open,
  filled, Otros), which drops `InstrumentGroups` from this slice.
- **C1 — copy.** Approve the copy table, or give replacements. *Recommended:* approve as written.
- Not questions: the allowlist entry `feature/song-detail → {key}` (Decision 10), no new Konsist
  rule, no `init.sh` change, no D-xx change.
