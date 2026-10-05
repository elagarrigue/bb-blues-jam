# Feature Implementation Spec: Show a past jam read-only

## Source Feature

- `id`: past-jam-detail
- `area`: feature-past-jams
- `depends_on`: `past-jams-list` (`accepted`)
- `status`: not_started at planning time
- `source`: `feature_list.json` (entry, verification, notes: "Editing history is not supported.
  Recorded in docs/user-and-access-model.md.")
- **Revision 2 (5 October 2026)** after the user's answers: S1 = expandable rows, C1 approved, D1 =
  yes. No new copy and no open question blocks implementation (see User Approvals).
- **Sequencing: implement only after `bottom-navigation` is `accepted`.** That slice deletes
  `TemporaryTabs.kt` and splits navigation into an outer host (`tabs`, `song/{jamDate}/{position}`)
  and `TabsShell` (inner host with the tab routes). This slice adds its route to the **outer** host
  beside the song route and an `onOpenPastJam` callback through `TabsShell` to the Anteriores tab
  destination (Technical Approach 8). Re-read `AppNavHost.kt`, `TabsShell.kt`, `AppRoutes.kt` and
  `AppRoutesTest.kt` at HEAD before starting.
- **`unpublished-setlist-state`** adds `Jam.setlistForMusicians()` in `:core:model`. If it exists
  at HEAD, this slice reads `jam.setlistForMusicians()` (never `jam.setlist`) in both the detail
  mapping and the Anteriores row summary; if not yet, it reads `jam.setlist` and the implementer
  records that the switch is owed. Past drafts already arrive as `Setlist.Withheld` from the
  repository, so behaviour is identical either way; the call keeps one musician rule everywhere.
- **Shared files touched:** `app/.../navigation/AppRoutes.kt`, `AppNavHost.kt`, `TabsShell.kt`,
  `app/src/test/.../navigation/AppRoutesTest.kt`, `konsist-test/.../ModuleIsolationTest.kt` (one
  allowlist entry); for D1 `app/build.gradle.kts` and both `DebugOverrides.kt`. Not touched:
  `:feature:next-jam`, `:feature:song-detail`, `:core:data`, `:core:model`, `BluesJamApp.kt`.

## Goal

Tapping a past jam in **Anteriores** opens it full screen: a back control, the date with the year,
the venue and the song count, then its setlist in position order with the same row structure as
Próxima jam — position, title, **key** (the only amber, DESIGN.md §5) and, collapsed, the instrument
strip of who played; tapping a row's header expands it in place to the artist and the lineup panel.
Unlike Próxima jam there is no filter, no song-detail link, no open slot and no free-slot message
(no `LIBRE`, no "Cupos libres", no hint, no "No quedan cupos libres."), and no admin control. Muted
archive treatment. Read-only: the Sheet owns past jams (D-04), nothing is mutated (D-13).

## Non-Goals

- Editing history, admin controls, `AdminSession` (user-and-access-model.md).
- A link to the song detail from a past jam (the user did not ask; S1 kept it out).
- Instrument filter, open-slot counts, the jam summary counter.
- Staleness notice, error block or retry on the detail (as the song detail).
- Any change to `toLineupPanel`, `LineupPanel`'s composable, `:feature:next-jam`, `:core:data`,
  Apps Script or the Sheet; any external API (D-09); any new library.

## Job Story

When I want to remember what was played at a jam and who played it,
I want to open that jam from Anteriores and read its songs, keys and musicians,
so I can avoid repeating a song or know who to ask about one.

## Users And Permissions

Musician and admin: identical read-only view. The detail presenter's only dependency is
`JamsRepository`, so it cannot read an admin flag; admin slices must never add one here.

## Acceptance Scenarios

1. **Open.** Given Anteriores shows 2026-07-25 with "13 temas", when the musician taps the row,
   then the past jam opens full screen (no tab bar) with "Sábado 25 de julio de 2026", the venue,
   "13 temas", and 13 collapsed rows in position order ("01"…"13", title, key).
2. **Collapsed: who played.** A song with Guitarra "Tincho", Bajo "Lucía" and the extra "saxo,
   Valeria" shows the strip `Gtr: Tincho`, `Bajo: Lucía`, `+ saxo: Valeria` and no other chip.
3. **Expand.** Tapping that row's header expands it: the artist replaces the strip, then the panel
   with `CUPOS CUBIERTOS` (Guitarra Tincho, Bajo Lucía) and `OTROS` (+ saxo Valeria). Tapping the
   header again collapses it. Several rows may be expanded at once; a tap on the panel does nothing.
4. **No free-slot notion.** Given a past jam whose lineups (built in a test fake) contain open
   slots, then no chip is `OPEN_SLOT`, every panel has `openSlots` empty and `noOpenSlotsNote` and
   `hint` null, and no model string contains `LIBRE`, `libre` or `Cupos libres`.
5. **Nobody recorded.** A song with an empty lineup and no extras (every song of the real
   2026-07-25 jam) draws no strip collapsed; expanded it shows only the artist; no note (C1).
6. **Not openable list rows.** A `NotShown` row in Anteriores (past draft, unavailable, no songs)
   is not clickable; a `Songs` row is one clickable node (`Role.Button`, ≥ 48dp, click label
   `ver la lista de temas`).
7. **State.** Expanded rows survive rotation and process death; leaving with back drops them
   (the destination is popped), so reopening the jam shows every row collapsed.
8. **Back.** `Volver` or system back returns to Anteriores, still selected, at the same scroll
   position; two fast taps on `Volver` pop only the detail.
9. **Live changes.** A snapshot in which that jam's setlist becomes `Withheld`/`Unavailable`/empty
   keeps the header and shows the Anteriores line (P1 copy) instead of rows; a snapshot without the
   jam shows the not-found block (C1); a refresh that fills a name keeps the same rows expanded.
10. **Dropped rows.** `Available(songs, droppedRows = 2)` → "Faltan 2 temas: no se pudieron leer."
    under the rows; none when 0.
11. **Next-jam unchanged.** Every `:feature:next-jam` source and test, `LineupPanel.kt`,
    `LineupPanelMapper.kt`'s `toLineupPanel` and `LineupPanelMapperTest` are byte-for-byte as before.
12. **Amber only on keys.** `feature/past-jams` reads only the `key` role (Konsist allowlist).

## Repository Research

### Files Inspected

`AGENTS.md`, `PROGRESS.md`, `feature_list.json`, `CONTEXT.md`, `DESIGN.md` (tokens, song row
collapsed/expanded, song detail, Core Screens 4–5, Required States), `bb-blues-jam-design-prompt.md`
(§1, §4, §5), `.claude/skills/architecture/SKILL.md`, `docs/domain-model.md` and
`docs/user-and-access-model.md` (grep), specs `past-jams-list`, `song-detail-screen`,
`bottom-navigation` and `unpublished-setlist-state` (grep: hosts, `TabsShell`,
`setlistForMusicians`); code: all of `feature/past-jams/src/main`, `SongDetailPresenter.kt`,
`SongDetailUiModel.kt`, `SongDetailCopy.kt`, `SongDetailScreen.kt` (head),
`SongDetailModelShapeTest.kt`, `NextJamPresenter.kt`, `NextJamUiModel.kt`, `NextJamCopy.kt`,
`ExpandedRows.kt`, `NextJamScreen.kt` (row composables), `AppNavHost.kt`, `AppRoutes.kt`,
`AppRoutesTest.kt`, `TemporaryTabs.kt` (pre-`bottom-navigation`), `app/src/debug/.../*.kt`,
`app/src/release/.../DebugOverrides.kt`, `app/build.gradle.kts` (demo flag),
`core/ui/.../lineup/LineupPanel.kt`, `LineupPanelMapper.kt`, `LineupPanelUiModel.kt`,
`ExpandIndicator.kt` (signature), `strip/InstrumentStripCopy.kt`, `core/model` `Setlist.kt`,
`Lineup.kt` (grep), `ModuleIsolationTest.kt` (allowlist), `docs/sheet-seed/2026-07-25.csv`.
Not inspected: `presenter-pattern.md`, `BackButton.kt` body, `TabsShell.kt` (does not exist yet).

### Findings

- `LineupPanel(model)` already draws each section only when its list is non-empty and each caption
  only when non-null. A `LineupPanelUiModel` with `openSlots = []`, `noOpenSlotsNote = null`,
  `hint = null` therefore draws exactly `CUPOS CUBIERTOS` + `OTROS` with **no composable change**.
  Only `toLineupPanel` forces the note/hint; a second mapper avoids touching it.
- `JamsSnapshot.past` keeps only filled slots (P6); this slice still drops open slots in the mapper
  so the guarantee does not rest on the repository alone.
- `ExpandedRows` is `internal` to `:feature:next-jam` and scopes positions by jam date because
  Próxima jam's jam changes under the same screen. On the past-jam detail the date is fixed per
  navigation entry, so a plain set of positions suffices (no copy of `ExpandedRows`).
- The real past jam has no musician in any cell: filled chips are visible on the device only with
  the D1 demo jam.

## Technical Approach

**1. `:core:ui` archive panel mapper** (`com.bbbjam.core.ui.lineup`, new file
`PastLineupPanelMapper.kt`): `fun Lineup.toPastLineupPanel(extras: List<ExtraParticipant>):
LineupPanelUiModel` = `LineupPanelUiModel(openSlots = emptyList(), filledSlots = slots.filterNot {
it.isOpen }.map { it.toLine() }, extras = extras.map { it.toLine() }, noOpenSlotsNote = null,
hint = null)`, reusing the internal `Slot.toLine()`/`ExtraParticipant.toLine()`. KDoc: a past jam
has no notion of a free slot (DESIGN.md §5); an open slot there means "not recorded" and is dropped.
New `PastLineupPanelMapperTest`: default (all open) lineup → everything empty and null; mixed
lineup → only filled lines in lineup order; extras kept in order; descriptions equal
`toLineupPanel`'s for the same filled slot. `LineupPanel.kt`, `toLineupPanel`, `LineupPanelCopy`
and their tests are not edited, which is how Próxima jam stays the same (scenario 11, checked with
`git diff --exit-code` on those paths and `:feature:next-jam`).

**2. List entry point** (`PastJamsPresenter`, `PastJamsUiModel`, `PastJamsScreen`).
`PastJamsPresenter : Presenter<PastJamsUiModel, PastJamsPresenter.Params>`, `data class
Params(val onOpenJam: (jamDate: LocalDate) -> Unit = {})`, read through `rememberUpdatedState`;
`toUiModel(now, onRetry, onOpenJam = {})`. `PastJamRowUiModel` gains `openLabel: String?`
(`ver la lista de temas`; null for `NotShown`) and `events: EventHandler<Event>` with `Event.Open`
(calls `onOpenJam(date)`). `PastJamsScreen(onOpenJam: (LocalDate) -> Unit = {}, modifier,
contentPadding, presenter)`. A row with `openLabel != null` gets `clickable(onClickLabel =
openLabel, role = Role.Button)` + `heightIn(min = LocalMinimumInteractiveComponentSize.current)`,
still one merged node; no chevron, colours unchanged. Mechanical update of `present(Unit)` calls.

**3. Detail presenter** (`PastJamDetailPresenter.kt`): `class PastJamDetailPresenter(private val
jams: JamsRepository) : Presenter<PastJamDetailUiModel, PastJamDetailPresenter.Params>`,
`Params(jamDate: LocalDate, onBack: () -> Unit)`. Body: `snapshot` collected once (as
`SongDetailPresenter`), `currentOnBack` via `rememberUpdatedState`, and
`var expanded by rememberSaveable(stateSaver = ExpandedPositionsSaver) { mutableStateOf(emptySet<Int>()) }`
— **created once, never recreated, not keyed on the snapshot or the date** (an earlier model's
handler must write through the same state). `onToggle: (Int) -> Unit = { p -> expanded = if (p in
expanded) expanded - p else expanded + p }`. `ExpandedPositionsSaver` (internal, same file):
`listSaver` to `ArrayList<Int>` and back. Keyed by the Sheet's `posicion`, never renumbered, so a
refresh keeps the right rows open. `jamDate` is constant for a navigation entry (each route is its
own entry), so no date scoping is needed. Pure mapping
`internal fun JamsSnapshot.toPastJamDetail(jamDate, expanded: Set<Int> = emptySet(), onToggle:
(Int) -> Unit = {}, onBack: () -> Unit = {})`:

| Input | Model |
|---|---|
| no emission | `Loading(DETAIL_LOADING, back)` |
| no jam with `date == jamDate` in `past` (never `upcoming`) | `NotFound(EmptyStateUiModel(NOT_FOUND_TITLE, NOT_FOUND_MESSAGE), back)` |
| found, `Available`, songs non-empty | `Jam(header, Songs(rows, droppedRowsNote), back)` |
| found, `Available` empty / `Withheld` / `Unavailable` | `Jam(header, NotShown(EMPTY_SETLIST / SETLIST_NOT_PUBLISHED / SETLIST_UNAVAILABLE), back)` |

The setlist read is `jam.setlistForMusicians()` if it exists (Source Feature). Header:
`pastJamDateLabel(date)`, `venue`, `countLabel = songCount(n)` only for `Songs`. Row per
`JamSong`, with `recorded = Lineup(lineup.slots.filterNot { it.isOpen })`: `position`,
`positionLabel` (`padStart(2, '0')`), `title`, `artist`, `key = key.value` (D-08), `keyDescription
= "Tonalidad $key"`, `instruments = recorded.toInstrumentChips(extraParticipants)`, `isExpanded =
position in expanded`, `stateDescription`/`toggleLabel` (copy below), `lineup =
lineup.toPastLineupPanel(extraParticipants)`, `events` → `ToggleExpanded` calls `onToggle(position)`.

**4. UiModel** (`PastJamDetailUiModel.kt`): `sealed interface PastJamDetailUiModel : UiModel { val
back: BackUiModel }` — `Loading(description, back)`, `NotFound(empty, back)`, `Jam(header:
PastJamHeaderUiModel, setlist: PastSetlistUiModel, back)`. `PastJamHeaderUiModel(dateLabel, venue,
countLabel: String?)`. `PastSetlistUiModel`: `Songs(rows: List<PastSongRowUiModel>,
droppedRowsNote: String?)`, `NotShown(message)`. `PastSongRowUiModel(position, positionLabel,
title, artist: String, key, keyDescription, instruments: List<InstrumentChipUiModel>, isExpanded,
stateDescription, toggleLabel, lineup: LineupPanelUiModel, events: EventHandler<Event>)` with
`sealed interface Event : UiEvent { data object ToggleExpanded }` — the only event. No filter,
detail, admin or edit field.

**5. Archive treatment** (`PastJamDetailDefaults`, internal): header date `textMuted` (`h1`),
venue and count `textMuted` (`body`); row fill `surface`, position `textMuted`, title `textMuted`
(`songTitle`), artist `archive` (`body`, 5.39:1 on `surface`), key `colors.key` (`key` typography,
the only amber), dropped note `textMuted` (`caption`). The strip, panel and chevron keep their
`:core:ui` colours (filled `textMuted` on `slotFilled`, extras `textMuted`, chevron `textMuted`;
no open kind ever reaches them, so `slotOpen` is never drawn). `PastJamDetailDefaultsTest`: every
non-key colour is `surface`, `textMuted` or `archive`; the key colour is the `key` role.

**6. Screen** (`PastJamDetailScreen(jamDate, onBack, modifier, contentPadding, presenter =
koinInject())` → `PastJamDetailContent(model, …)`): a `LazyColumn` on `background`, padding as
`SongDetailContent`. Items: `BackButton(model.back)`; Loading → nothing else (list content
description = `description`); NotFound → `EmptyStateBlock`; Jam → header (date as heading), rows
spaced `sm` (key = position) or the `NotShown` message (`body`, `textMuted`), then the dropped
note. Row = `NextJamScreen`'s `SongRow` structure without the detail action: `Surface(fill,
shapes.md)` → `Column(Modifier.animateContentSize())` → header (`clickable(onClickLabel =
toggleLabel, role = Role.Button)`, `stateDescription`, padding `md`×`sm`; title line = position,
title weight 1, key with `contentDescription = keyDescription`, `ExpandIndicator(isExpanded)`;
then the strip when collapsed and non-empty, or the artist when expanded and not blank) and, when
expanded, `LineupPanel(row.lineup, Modifier.padding(horizontal = spacing.md))` plus bottom
padding `sm`. The panel is not clickable. Header ≥ 48dp from the key line plus padding, as
Próxima jam. Previews (`PastJamDetailPreview.kt`) through `toPastJamDetail`: Loading, NotFound,
Jam with one row expanded (names + extras), one empty-lineup row expanded, a dropped note,
Withheld.

**7. Koin.** `pastJamsModule` adds `factory { PastJamDetailPresenter(get()) }`;
`PastJamsModuleTest` resolves both presenters, each a new instance.

**8. Navigation (`:app`, after `bottom-navigation`).** `AppRoutes`: `PAST_JAM =
"pastJam/{$JAM_DATE}"`, `pastJam(date) = "pastJam/$date"`, `parsePastJam(jamDate: String?):
LocalDate?` (reusing the ISO parse). Outer host in `AppNavHost`: `composable(AppRoutes.PAST_JAM,
arguments = listOf(navArgument(JAM_DATE) { type = NavType.StringType }))` beside `SONG_DETAIL`,
drawing `PastJamDetailScreen` with the **same insets wrapping and `RESUMED` back guard** the song
destination has at that point; a null argument pops (`LaunchedEffect`). `TabsShell` gains
`onOpenPastJam: (LocalDate) -> Unit`, passed by `AppNavHost` as `{ date ->
outer.navigate(AppRoutes.pastJam(date)) { launchSingleTop = true } }` and given to
`PastJamsScreen(onOpenJam = …)` in the inner Anteriores destination, exactly as `onOpenSong` reaches
`NextJamScreen`. Anteriores' scroll and selection survive because the outer `tabs` entry keeps the
inner host's saved state (`bottom-navigation`'s design).

**9. Amber allowlist.** `AMBER_ROLE_ALLOWLIST` gains `"feature/past-jams" to setOf("key")`. No
other amber role: `slotOpen` is read only inside `:core:ui` and never drawn here.

**10. Debug demo past jam (D1).** `bluesjam.demoPastJam=true` in `local.properties` →
`BuildConfig.DEMO_PAST_JAM` (debug; release hard `false`), commented like the upcoming flag.
`app/src/debug/java/com/bbbjam/debug/`: `DemoPastJam.on(today)` dated `today.minusDays(14)`, venue
`DemoUpcomingJam.VENUE`, `PUBLISHED`, `Available(songs, droppedRows = 1)`, lineups with **filled
slots only** (P6 shape): one fully filled song, one partly filled, one with nobody and no extra, one
with a short and one with a long extra, keys `Bb` and `F#m`, titles from `catalog-seed.json`.
`DemoPastJamRepository(real, calendar)`: adds the demo to `past` unless a real past jam has that
date, keeps newest first, never touches `upcoming`, `freshness`, Room or the Sheet; `refresh()`
delegates. `debugOverrides()` wraps the real repository with each enabled decorator. `isDemo()`
already matches by venue. Release `DebugOverrides.kt`: no behaviour change.
`DemoPastJamRepositoryTest` in `app/src/testDebug/`.

## Expected File Changes

- `core/ui/src/main/.../lineup/PastLineupPanelMapper.kt` — create; test
  `PastLineupPanelMapperTest` — create. No other `:core:ui` file changes.
- `feature/past-jams/src/main/.../`: modify `PastJamsPresenter.kt`, `PastJamsUiModel.kt`,
  `PastJamsScreen.kt`, `PastJamsStatesPreview.kt`, `PastJamsCopy.kt`, `di/PastJamsModule.kt`;
  create `PastJamDetailPresenter.kt`, `PastJamDetailUiModel.kt`, `PastJamDetailDefaults.kt`,
  `PastJamDetailScreen.kt`, `PastJamDetailPreview.kt`.
- `feature/past-jams/src/test/.../`: modify `PastJamsPresenterTest`, `PastJamsStatesTest`,
  `PastJamsModuleTest`; create `PastJamDetailMappingTest`, `PastJamDetailPresenterTest`,
  `PastJamDetailDefaultsTest`, `PastJamDetailModelShapeTest`.
- `app/.../navigation/AppRoutes.kt`, `AppNavHost.kt`, `TabsShell.kt`, `AppRoutesTest.kt` — modify.
- `konsist-test/.../ModuleIsolationTest.kt` — one allowlist entry.
- D1: `app/build.gradle.kts`, `app/src/debug/.../DebugOverrides.kt`, new `DemoPastJam.kt`,
  `DemoPastJamRepository.kt`, `app/src/testDebug/.../DemoPastJamRepositoryTest.kt`.

## Copy

All reused, no new string. `PastJamsCopy` gains constants with these exact values (feature copy
cannot be imported across features; KDoc cites the approvals):

| Use | Text | Source |
|---|---|---|
| List row click label | `ver la lista de temas` | C1 (this spec) |
| Detail loading (screen reader) | `Cargando la jam` | C1 |
| Not found | `Esta jam ya no está en el archivo` / `Puede que la organización la haya cambiado. Volvé a Anteriores para ver las jams guardadas.` | C1 |
| Row state | `expandido` / `contraído` | `song-row-expansion` C1 |
| Row action | `ver los cupos` / `ocultar los cupos` | `song-row-expansion` C1 |
| Key description | `Tonalidad B` | next-jam C1 form |
| Dropped rows | `Falta 1 tema: no se pudo leer.` / `Faltan 2 temas: no se pudieron leer.` | next-jam P4 |
| Header, NotShown lines | date, venue, `13 temas`, the three P1 lines | `past-jams-list` C1 |
| Panel headings | `Cupos cubiertos`, `Otros` | `:core:ui` `LineupPanelCopy` (unchanged) |

"ver los cupos" refers to the filled slots (DESIGN.md §5: "los cupos se muestran cubiertos").

## Visual Design Impact

- UI: yes. Sources: `DESIGN.md` Core Screen 5, "Song row, collapsed/expanded", the strip rules,
  Required States; `bb-blues-jam-design-prompt.md` §5. New artifact: no.
- States: detail loading (back only), not found, jam with rows (collapsed/expanded), jam with a
  `NotShown` line; Anteriores rows become tappable (ripple only).

## Durable Documentation Impact

- `DESIGN.md`: update Core Screen 5 (as built: header, rows, expansion without free-slot messages,
  archive colours, copy) and Core Screen 4 ("rows with songs open the jam").
- `.claude/skills/architecture/SKILL.md`: `:feature:past-jams` (detail presenter, allowlist
  `{key}`), `toPastLineupPanel` beside the panel example, the `PAST_JAM` outer route, the D1 demo.
- `docs/risks-and-open-questions.md`: no staleness notice on the detail; previews not rendered.
- `PROGRESS.md`, `feature_list.json`: evidence, status `passing`.
- `AGENTS.md`, `CONTEXT.md`, `docs/domain-model.md`: not needed. `ARCHITECTURE.md`,
  `CONSTRAINTS.md`: not present in this repo.

## Implementation Plan

1. Confirm `bottom-navigation` is `accepted`; check whether `setlistForMusicians()` exists; re-read
   the `:app` navigation files; `CI=true ./init.sh` baseline; record SHA-1s of the unchanged files
   of scenario 11.
2. `toPastLineupPanel` + test.
3. Detail UiModel, copy, pure mapping and tests; presenter (expansion state) and Molecule tests;
   shape test.
4. Defaults + test, screen, previews, Koin.
5. List entry point (Params, `Open` event, clickable row) and tests.
6. `:app` route + test, outer destination, `TabsShell` callback; allowlist entry.
7. D1 demo + test; failure demonstrations; docs; final gate; device check.

## Verification Plan

- `CI=true ./init.sh` exit 0, `konsist: wired` (17/17, unchanged count), `detekt: wired`,
  `ktlint: wired`; previous result files unchanged in counts except the modified test classes.
- Scenario 11: `git diff --exit-code <baseline> -- feature/next-jam core/ui/src/main/kotlin/com/bbbjam/core/ui/lineup/LineupPanel.kt core/ui/src/main/kotlin/com/bbbjam/core/ui/lineup/LineupPanelMapper.kt core/ui/src/test/kotlin/com/bbbjam/core/ui/lineup/LineupPanelMapperTest.kt`
  empty; `LineupPanelMapperTest` and `NextJamPresenterTest` counts unchanged.
- `PastLineupPanelMapperTest` (Technical Approach 1).
- `PastJamDetailMappingTest` (strings written out): every table row of Technical Approach 3;
  lookup ignores `upcoming` with the same date; scenario 4 (open slots in a fake → no open chip,
  empty `openSlots`, null note/hint, no `LIBRE`/`libre`/`Cupos libres` in any model string);
  `expanded = {2}` → only row 2 `isExpanded` with `expandido`/`ocultar los cupos`; empty lineup →
  no chips, empty panel; key equals `JamSong.key`; dropped note 0/1/2.
- `PastJamDetailPresenterTest` (Molecule + Turbine): Loading → Jam; toggle row 1 and row 3 → both
  expanded (a transition); toggle row 1 again → only row 3; a new snapshot that fills a name keeps
  row 3 expanded; an **earlier model's** `ToggleExpanded` handler still toggles the current state;
  snapshot without the date → NotFound; `Back` calls the current `onBack` after Params change.
- `PastJamDetailModelShapeTest`: declared fields of `PastSongRowUiModel` and `Jam` exactly the
  approved sets, none matching `filter|admin|edit|detail|libre`; `Event`'s sealed subclasses are
  exactly `{ToggleExpanded}`; presenter constructor takes only `JamsRepository`.
- `ExpandedPositionsSaver` round trip test (save → restore equals; empty set).
- `PastJamsPresenterTest`/`PastJamsStatesTest`: `Open` on a `Songs` row calls `onOpenJam(date)`;
  stale handler calls the current callback; `NotShown` rows have `openLabel == null`.
- `AppRoutesTest`: `pastJam(2026-07-25)` = `pastJam/2026-07-25`; parse accepts/rejects as the song
  route; every-month round trip.
- **Failure demonstrations** (edit, run, restore; record messages): (1) make `toPastLineupPanel`
  call `toLineupPanel` → mapping and `PastLineupPanelMapperTest` fail; (2) drop the
  `filterNot { isOpen }` for the strip → scenario 4 test fails; (3) `colors.slotOpen` in
  `PastJamDetailScreen.kt` → `amber-roles-allowlisted` fails; (4) add `val isAdmin: Boolean` to
  `PastSongRowUiModel` → shape test fails; (5) wrap the expansion state in `remember(snapshot)` →
  the stale-handler or the refresh-keeps-expansion test fails; (6) drop `rememberUpdatedState` in
  `PastJamsPresenter` → stale-handler test fails; (7) remove the allowlist entry →
  `amber-roles-allowlisted` fails on `colors.key`.
- Greps on `feature/past-jams/src/main`: no `androidx.navigation`, `MaterialTheme.`, `Color(`,
  dp/sp literal, `toLineupPanel`, `toInstrumentGroups`, `AdminSession`, `InstrumentFilter`,
  `OpenDetail`, tú forms; `jam.setlist` absent if `setlistForMusicians()` exists.
- **Device (Pixel 5, outside the gate, optional; never edit the Sheet; never enable TalkBack or
  any accessibility service).** Real jam: Anteriores → tap 2026-07-25 → full screen, no bar, 13
  rows, amber keys, no strip; expand row 1 → artist only. `uiautomator dump`: list row one
  clickable node with its click label; detail row header clickable with state description; panel
  lines not clickable; `Volver` ≥ 48dp. With `bluesjam.demoPastJam=true`: the demo jam shows
  chips with names and `+ saxo`; expand two rows → `CUPOS CUBIERTOS`/`OTROS`, no `LIBRE`, no hint
  or note; rotate → still expanded; back button, system back, double tap → Anteriores selected at
  the same scroll. Restore `local.properties` and rotation; crash buffer empty. Never log the Apps
  Script URL.

## Evidence To Capture

Gate output and counts, the scenario 11 diff result, each demonstration's failure message, grep
results, device dump excerpts and screenshots (if run), in `feature_list.json` and `PROGRESS.md`.

## Validator Checklist

- [ ] Waited for `bottom-navigation`; route in the outer host; callback through `TabsShell`.
- [ ] Only the listed files changed; `:feature:next-jam`, `LineupPanel.kt`, `toLineupPanel` and its
      test untouched (scenario 11).
- [ ] No free-slot notion: `toPastLineupPanel` only, open slots dropped, no `LIBRE`/hint/note.
- [ ] Expansion: positions set in `rememberSaveable`, created once, header-only toggle, several rows.
- [ ] No filter, admin, edit or detail field; one event; presenter takes only `JamsRepository`.
- [ ] Amber only `key`, allowlisted; archive colours from `PastJamDetailDefaults`.
- [ ] `setlistForMusicians()` used if present; copy as in the Copy table; no mutation, no API.
- [ ] D1 code only in `app/src/debug` (release no-op); demonstrations recorded; three `wired`.

## Risks

- Conflicts with `bottom-navigation` if started early (Source Feature).
- No staleness notice on the detail: offline, the musician sees cached data without a warning
  there (the list shows it).
- The real archive has no musician names, so every real row expands to the artist only until the
  Sheet records who played.
- `PastJamsCopy` repeats four strings from `NextJamCopy`; a future wording change must touch both
  (moving them to `:core:ui` would edit `:feature:next-jam`, which this slice must not do).

## User Approvals

Answered by the user on 5 October 2026: **S1 expandable rows** (the alternative, not the
recommendation): the spec must be revised before implementation, including the `:core:ui` panel
variant without free-slot messages — done in revision 2 (Technical Approach 1). **C1** copy
approved as written. **D1** yes: debug-only demo past jam behind `bluesjam.demoPastJam`.

The questions as asked:

- **S1 — row structure.** *Recommended:* non-expandable rows (position, title, key, artist, strip
  with filled slots and `Otros`), no link to the song detail, and only `Songs` rows in Anteriores
  are tappable. Reason: the panel and the song detail always show "No quedan cupos libres." or the
  hint, a notion of open slots the design forbids here, and the strip already shows who played.
  Alternative: expandable rows with a panel variant without note/hint (a `:core:ui` change).
- **C1 — copy** (Rioplatense, vos). *Recommended:* approve as written.

  | Use | Text |
  |---|---|
  | Row click label (TalkBack: "Presioná dos veces para…") | `ver la lista de temas` |
  | Detail loading (screen reader) | `Cargando la jam` |
  | Not found, title | `Esta jam ya no está en el archivo` |
  | Not found, message | `Puede que la organización la haya cambiado. Volvé a Anteriores para ver las jams guardadas.` |
  | Header | `Sábado 25 de julio de 2026`, venue, `13 temas` (reused) |
  | Key description | `Tonalidad B` (reused form) |
  | Dropped rows | `Falta 1 tema: no se pudo leer.` / `Faltan 2 temas: no se pudieron leer.` (Próxima jam wording) |
  | Not shown lines | the approved P1 lines of Anteriores (reused) |

  A song with nobody recorded shows no line (no "No se registró quién tocó."). Say if you want one.
- **D1 — debug demo past jam.** *Recommended:* yes — add `bluesjam.demoPastJam` (debug source set
  only, Technical Approach 10), because the real 2026-07-25 jam has no musician names and filled
  chips could not be seen on the device. Alternative: JVM tests and previews only.

Revision 2 raises **no blocking question**. One optional wording choice, default = reuse: the
expand action reads `ver los cupos` / `ocultar los cupos` (approved for Próxima jam). If you prefer
an archive wording such as `ver quién tocó` / `ocultar quién tocó`, say so; otherwise the reused
strings stand.
