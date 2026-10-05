# Feature Implementation Spec: Show a past jam read-only

## Source Feature

- `id`: past-jam-detail
- `area`: feature-past-jams
- `depends_on`: `past-jams-list` (`accepted`)
- `status`: not_started at planning time
- `source`: `feature_list.json` (entry, verification, notes: "Editing history is not supported.
  Recorded in docs/user-and-access-model.md.")
- **Revision 3 (5 October 2026)** after the user's direction "para las jams pasadas no importa
  quién tocó, solo la lista de temas": the detail is the song list only. It supersedes S1 and drops
  D1 (User Approvals). Revision 2's lineup strip, expansion, `:core:ui` mapper and demo jam are gone.
- **Sequencing:** implement only after `bottom-navigation` (commit `d0c4829`, `passing`, under
  validation) is `accepted`. This slice adds a route to the **outer** host in `AppNavHost.kt` beside
  the song route, and an `onOpenPastJam` callback through `TabsShell` to the Anteriores destination
  (Technical Approach 6). Re-read `AppNavHost.kt`, `TabsShell.kt`, `AppRoutes.kt`, `AppRoutesTest.kt`
  at HEAD before starting; if validation changed them, follow HEAD.
- **`unpublished-setlist-state`** adds `Jam.setlistForMusicians()` in `:core:model`. If it exists
  at HEAD, this slice reads `jam.setlistForMusicians()` (never `jam.setlist`) in the detail mapping
  and in the Anteriores row summary; if not yet, it reads `jam.setlist` and the implementer records
  that the switch is owed. Past drafts already arrive as `Setlist.Withheld`, so behaviour is
  identical either way.
- **Shared files touched:** `app/.../navigation/AppRoutes.kt`, `AppNavHost.kt`, `TabsShell.kt`,
  `app/src/test/.../navigation/AppRoutesTest.kt`, `konsist-test/.../ModuleIsolationTest.kt` (one
  allowlist entry). Not touched: `:core:*`, `:feature:next-jam`, `:feature:song-detail`,
  `app/build.gradle.kts`, the debug/release source sets, `BluesJamApp.kt`.

## Goal

Tapping a past jam in **Anteriores** opens it full screen: a back control, the date with the year,
the venue and the song count, then the songs in position order. Each row shows position, title,
artist and **key** — the key is kept because it is part of the song as played that night and the
only amber on the screen (DESIGN.md §5); the user may still drop it (User Approvals, K3). Nothing
about who played: no strip, no expansion, no lineup panel, no slot of any kind, no filter, no admin
control. Muted archive treatment. Read-only (D-04, D-13).

## Non-Goals

- Lineups, musicians, extras, slots (open or filled), expansion, the song detail link.
- Editing history, admin controls, `AdminSession` (user-and-access-model.md).
- Filter, counters, staleness notice, error block or retry on the detail (as the song detail).
- Any `:core:ui`, `:core:data`, `:core:model`, Apps Script or Sheet change; external APIs (D-09);
  new libraries; a debug demo jam.

## Job Story

When I want to remember what was played at a past jam,
I want to open it from Anteriores and read its songs and keys,
so I can avoid repeating a song or recall how it was played.

## Users And Permissions

Musician and admin: identical read-only view. The detail presenter's only dependency is
`JamsRepository`, so it cannot read an admin flag; admin slices must never add one here.

## Acceptance Scenarios

1. **Open.** Given Anteriores shows 2026-07-25 with "13 temas", when the musician taps the row,
   then the jam opens full screen (no tab bar) with "Sábado 25 de julio de 2026", the venue,
   "13 temas" and 13 rows in position order ("01"…"13"), each with title, artist and key.
2. **No lineup.** Given a past song whose lineup has filled and open slots and extras (test fake),
   then its row model and screen carry no instrument, musician or slot data at all.
3. **Not openable list rows.** A `NotShown` row in Anteriores (past draft, unavailable, no songs)
   is not clickable; a `Songs` row is one clickable node (`Role.Button`, ≥ 48dp, click label
   `ver la lista de temas`).
4. **Back.** `Volver` or system back returns to Anteriores, still selected, at the same scroll
   position; two fast taps on `Volver` pop only the detail. Rotation keeps the jam open.
5. **Live changes.** A snapshot in which that jam's setlist becomes `Withheld`/`Unavailable`/empty
   keeps the header and shows the Anteriores line (P1 copy) instead of rows; a snapshot without the
   jam shows the not-found block (C1).
6. **Dropped rows.** `Available(songs, droppedRows = 2)` → "Faltan 2 temas: no se pudieron leer."
   under the rows; none when 0.
7. **Blank artist.** A song with a blank artist draws no artist line.
8. **Amber only on keys.** `feature/past-jams` reads only the `key` role (Konsist allowlist).

## Repository Research

### Files Inspected

`AGENTS.md`, `PROGRESS.md`, `feature_list.json`, `CONTEXT.md`, `DESIGN.md` (tokens, song row,
song detail, Core Screens 4–5, Required States), `bb-blues-jam-design-prompt.md` (§4, §5),
`.claude/skills/architecture/SKILL.md`, `docs/domain-model.md`, `docs/user-and-access-model.md`
(grep), specs `past-jams-list`, `song-detail-screen`, `bottom-navigation`,
`unpublished-setlist-state` (grep); code: all of `feature/past-jams/src/main`,
`SongDetailPresenter.kt`, `SongDetailUiModel.kt`, `SongDetailCopy.kt`, `SongDetailScreen.kt`
(head), `SongDetailModelShapeTest.kt`, `NextJamPresenter.kt`, `NextJamCopy.kt`, `NextJamScreen.kt`
(row composables), `AppNavHost.kt`, `TabsShell.kt`, `AppRoutes.kt` at `d0c4829` (grep),
`AppRoutesTest.kt` (pre-`bottom-navigation`), `ModuleIsolationTest.kt` (allowlist),
`docs/sheet-seed/2026-07-25.csv`. Not inspected: `presenter-pattern.md`, `BackButton.kt` body.

### Findings

- `AppRoutes` at `d0c4829` already has `PAST_JAMS = "past-jams"` (the tab). The new detail route
  constant is therefore named `PAST_JAM_DETAIL` to avoid confusion.
- The outer song destination wraps the screen in a `background` `Box` with `statusBarsPadding()`,
  passes `contentPadding = WindowInsets.navigationBars.asPaddingValues()` and guards back with
  `entry.lifecycle.currentState == RESUMED`; `TabsShell(onOpenSong, …)` calls `PastJamsScreen()`.
- `PastJamsPresenter` is `Presenter<PastJamsUiModel, Unit>`; its rows have no events.
- The real 2026-07-25 jam has 13 songs with keys and artists: enough for the device check.

## Technical Approach

**1. List entry point** (`PastJamsPresenter`, `PastJamsUiModel`, `PastJamsScreen`).
`PastJamsPresenter : Presenter<PastJamsUiModel, PastJamsPresenter.Params>`, `data class
Params(val onOpenJam: (jamDate: LocalDate) -> Unit = {})`, read through `rememberUpdatedState`;
`toUiModel(now, onRetry, onOpenJam = {})`. `PastJamRowUiModel` gains `openLabel: String?`
(`ver la lista de temas`; null for `NotShown`) and `events: EventHandler<Event>` with
`Event.Open` (calls `onOpenJam(date)`). `PastJamsScreen(onOpenJam: (LocalDate) -> Unit = {},
modifier, contentPadding, presenter)`. A row with `openLabel != null` gets `clickable(onClickLabel
= openLabel, role = Role.Button)` + `heightIn(min = LocalMinimumInteractiveComponentSize.current)`,
still one merged node; no chevron; colours unchanged. Mechanical update of `present(Unit)` calls.

**2. Detail presenter** (`PastJamDetailPresenter.kt`): `class PastJamDetailPresenter(private val
jams: JamsRepository) : Presenter<PastJamDetailUiModel, PastJamDetailPresenter.Params>`,
`Params(jamDate: LocalDate, onBack: () -> Unit)`. Body as `SongDetailPresenter` (collect once,
`rememberUpdatedState(onBack)`); no other state. Pure `internal fun
JamsSnapshot.toPastJamDetail(jamDate, onBack: () -> Unit = {})`:

| Input | Model |
|---|---|
| no emission | `Loading(DETAIL_LOADING, back)` |
| no jam with `date == jamDate` in `past` (never `upcoming`) | `NotFound(EmptyStateUiModel(NOT_FOUND_TITLE, NOT_FOUND_MESSAGE), back)` |
| found, `Available`, songs non-empty | `Jam(header, Songs(rows, droppedRowsNote), back)` |
| found, `Available` empty / `Withheld` / `Unavailable` | `Jam(header, NotShown(EMPTY_SETLIST / SETLIST_NOT_PUBLISHED / SETLIST_UNAVAILABLE), back)` |

Header: `pastJamDateLabel(date)`, `venue`, `countLabel = songCount(n)` only for `Songs`. Row per
`JamSong`: `position`, `positionLabel` (`padStart(2, '0')`), `title`, `artist` (null when blank),
`key = key.value` (D-08), `keyDescription = "Tonalidad $key"`. `lineup` and `extraParticipants`
are never read.

**3. UiModel** (`PastJamDetailUiModel.kt`): `sealed interface PastJamDetailUiModel : UiModel { val
back: BackUiModel }` — `Loading(description, back)`, `NotFound(empty, back)`, `Jam(header:
PastJamHeaderUiModel, setlist: PastSetlistUiModel, back)`. `PastJamHeaderUiModel(dateLabel, venue,
countLabel: String?)`. `PastSetlistUiModel`: `Songs(rows: List<PastSongRowUiModel>,
droppedRowsNote: String?)`, `NotShown(message)`. `PastSongRowUiModel(position, positionLabel, title,
artist: String?, key, keyDescription)` — no event, no lineup, no filter or admin field.

**4. Archive treatment** (`PastJamDetailDefaults`, internal): header date `textMuted` (`h1`),
venue and count `textMuted` (`body`); row fill `surface`, position `textMuted`, title `textMuted`
(`songTitle`), artist `archive` (`body`, 5.39:1 on `surface`), key `colors.key` (`key`
typography), dropped note `textMuted` (`caption`). `PastJamDetailDefaultsTest`: every non-key colour
is `surface`, `textMuted` or `archive`; the key colour is the `key` role.

**5. Screen** (`PastJamDetailScreen(jamDate, onBack, modifier, contentPadding, presenter =
koinInject())` → `PastJamDetailContent(model, …)`): a `LazyColumn` on `background`, padding as
`SongDetailContent`. Items: `BackButton(model.back)`; Loading → nothing else (list content
description = `description`); NotFound → `EmptyStateBlock`; Jam → header (date as heading), rows
spaced `sm` (key = position) or the `NotShown` message (`body`, `textMuted`), then the dropped
note. Row: `Surface(fill, shapes.md)`, padding `md`×`sm`; a title line (position, title weight 1,
key with `contentDescription = keyDescription`) and the artist under it when present. One merged
node per row, not clickable, no role, no ripple, no chevron. No dp/sp/colour literal, no
`MaterialTheme`. Previews (`PastJamDetailPreview.kt`) through `toPastJamDetail`: Loading, NotFound,
Jam (with a blank artist and a dropped note), Withheld.

**6. Koin and navigation.** `pastJamsModule` adds `factory { PastJamDetailPresenter(get()) }`.
`AppRoutes`: `PAST_JAM_DETAIL = "pastJam/{$JAM_DATE}"`, `pastJamDetail(date) = "pastJam/$date"`,
`parsePastJamDetail(jamDate: String?): LocalDate?` (reusing the ISO parse). Outer host:
`composable(AppRoutes.PAST_JAM_DETAIL, arguments = listOf(navArgument(JAM_DATE) { type =
NavType.StringType }))` beside `SONG_DETAIL`, with the **same** `Box`/`statusBarsPadding`,
`navigationBars` content padding and `RESUMED` back guard; null argument → `LaunchedEffect(Unit) {
nav.popBackStack() }`. `TabsShell` gains `onOpenPastJam: (LocalDate) -> Unit`, passed by
`AppNavHost` as `{ date -> nav.navigate(AppRoutes.pastJamDetail(date)) { launchSingleTop = true } }`
and handed to `PastJamsScreen(onOpenJam = onOpenPastJam)`. Anteriores' scroll and selection survive
because the outer `tabs` entry keeps the inner host's saved state.

**7. Amber allowlist.** `AMBER_ROLE_ALLOWLIST` gains `"feature/past-jams" to setOf("key")`.

## Expected File Changes

- `feature/past-jams/src/main/.../`: modify `PastJamsPresenter.kt`, `PastJamsUiModel.kt`,
  `PastJamsScreen.kt`, `PastJamsStatesPreview.kt`, `PastJamsCopy.kt`, `di/PastJamsModule.kt`;
  create `PastJamDetailPresenter.kt`, `PastJamDetailUiModel.kt`, `PastJamDetailDefaults.kt`,
  `PastJamDetailScreen.kt`, `PastJamDetailPreview.kt`.
- `feature/past-jams/src/test/.../`: modify `PastJamsPresenterTest`, `PastJamsStatesTest`,
  `PastJamsModuleTest`; create `PastJamDetailMappingTest`, `PastJamDetailPresenterTest`,
  `PastJamDetailDefaultsTest`, `PastJamDetailModelShapeTest`.
- `app/.../navigation/AppRoutes.kt`, `AppNavHost.kt`, `TabsShell.kt`, `AppRoutesTest.kt` — modify.
- `konsist-test/.../ModuleIsolationTest.kt` — one allowlist entry.

## Copy

No new string. `PastJamsCopy` gains these constants (C1 approved; the rest reused):

| Use | Text |
|---|---|
| List row click label | `ver la lista de temas` |
| Detail loading (screen reader) | `Cargando la jam` |
| Not found | `Esta jam ya no está en el archivo` / `Puede que la organización la haya cambiado. Volvé a Anteriores para ver las jams guardadas.` |
| Key description | `Tonalidad B` (next-jam form) |
| Dropped rows | `Falta 1 tema: no se pudo leer.` / `Faltan 2 temas: no se pudieron leer.` (next-jam P4 wording) |
| Header, NotShown lines | date, venue, `13 temas`, the three P1 lines (`past-jams-list` C1) |
| Back | `Volver` (`:core:ui` `NavCopy`) |

## Visual Design Impact

- UI: yes. Sources: `DESIGN.md` Core Screen 5, song row, Required States; design prompt §5 as
  narrowed by the user (song list only). New artifact: no.
- States: detail loading (back only), not found, jam with rows, jam with a `NotShown` line;
  Anteriores rows with songs become tappable (ripple only).

## Durable Documentation Impact

- `DESIGN.md`: update Core Screen 5 (as built: song list only, no lineup per the user's 5 October
  direction; archive colours; copy) and Core Screen 4 ("rows with songs open the jam").
- `.claude/skills/architecture/SKILL.md`: `:feature:past-jams` (detail presenter, allowlist
  `{key}`), the `PAST_JAM_DETAIL` outer route.
- `docs/risks-and-open-questions.md`: no staleness notice on the detail.
- `PROGRESS.md`, `feature_list.json`: evidence, status `passing`. The entry's wording "who played"
  and its verification "Slots render as filled" are superseded by the user's direction; the
  implementer records that in the evidence (the orchestrator decides whether to edit the entry).
- `AGENTS.md`, `CONTEXT.md`, `docs/domain-model.md`: not needed. `ARCHITECTURE.md`,
  `CONSTRAINTS.md`: not present in this repo.

## Implementation Plan

1. Confirm `bottom-navigation` is `accepted`; check whether `setlistForMusicians()` exists; re-read
   the navigation files; `CI=true ./init.sh` baseline.
2. Detail UiModel, copy, pure mapping and tests; presenter and Molecule test; shape test.
3. Defaults + test, screen, previews, Koin.
4. List entry point (Params, `Open` event, clickable row) and tests.
5. Route + test, outer destination, `TabsShell` callback; allowlist entry.
6. Failure demonstrations; docs; final gate; device check.

## Verification Plan

- `CI=true ./init.sh` exit 0, `konsist: wired` (17/17, unchanged count), `detekt: wired`,
  `ktlint: wired`; previous result files unchanged in counts except the modified test classes.
- `PastJamDetailMappingTest` (strings written out): every table row of Technical Approach 2; lookup
  ignores `upcoming` with the same date; blank artist → null; key equals `JamSong.key`; dropped note
  0/1/2; a song with filled/open slots and extras maps to the same row as one with none.
- `PastJamDetailPresenterTest` (Molecule + Turbine): Loading → Jam; Jam → NotFound on a snapshot
  without the date (a transition); `Back` calls the current `onBack` after Params change.
- `PastJamDetailModelShapeTest`: declared fields of `PastSongRowUiModel` are exactly `position,
  positionLabel, title, artist, key, keyDescription`; no field of any detail model matches
  `lineup|instrument|slot|musician|extra|filter|admin|edit|event`; presenter constructor takes only
  `JamsRepository`.
- `PastJamsPresenterTest`/`PastJamsStatesTest`: `Open` on a `Songs` row calls `onOpenJam(date)`; an
  earlier model's handler calls the current callback; `NotShown` rows have `openLabel == null`.
- `AppRoutesTest`: `pastJamDetail(2026-07-25)` = `pastJam/2026-07-25`; parse accepts it, rejects
  null, empty, `25/07/2026`, `2026-13-01`; every-month round trip.
- **Failure demonstrations** (edit, run, restore; record messages): (1) add `val lineup:
  LineupPanelUiModel` to `PastSongRowUiModel` → shape test fails; (2) `colors.slotOpen` in
  `PastJamDetailScreen.kt` → `amber-roles-allowlisted` fails; (3) remove the allowlist entry →
  `amber-roles-allowlisted` fails on `colors.key`; (4) drop `rememberUpdatedState` in
  `PastJamsPresenter` → stale-handler test fails; (5) look the jam up in `upcoming` too → the
  mapping test fails.
- Greps on `feature/past-jams/src/main`: no `androidx.navigation`, `MaterialTheme.`, `Color(`,
  dp/sp literal, `lineup`, `InstrumentStrip`, `LineupPanel`, `AdminSession`, `InstrumentFilter`,
  tú forms; no `jam.setlist` if `setlistForMusicians()` exists.
- **Device (Pixel 5, outside the gate, optional; never edit the Sheet; never enable TalkBack or any
  accessibility service).** Anteriores → tap 2026-07-25 → full screen, no tab bar, header with
  "13 temas", 13 rows with title, artist and amber key, no strip. `uiautomator dump`: the list row
  is one clickable node with its click label; detail rows are not clickable; `Volver` ≥ 48dp.
  Scroll Anteriores first if it scrolls; back button, system back, double tap, rotation →
  Anteriores selected at the same position. Crash buffer empty; settings restored. Never log the
  Apps Script URL.

## Evidence To Capture

Gate output and counts, each demonstration's failure message, grep results, device dump excerpts
and screenshots (if run), in `feature_list.json` and `PROGRESS.md`.

## Validator Checklist

- [ ] Waited for `bottom-navigation`; route in the outer host; callback through `TabsShell`.
- [ ] Only the listed files changed; no `:core:*` change, no debug demo, no new flag.
- [ ] Detail rows: position, title, artist, key only; no lineup, slot or musician anywhere.
- [ ] No filter, admin or edit field; no row event; presenter takes only `JamsRepository`.
- [ ] Amber only `key`, allowlisted; archive colours from `PastJamDetailDefaults`.
- [ ] `setlistForMusicians()` used if present; copy as in the Copy table; no mutation, no API.
- [ ] Demonstrations recorded; three `wired`; docs updated.

## Risks

- `feature_list.json` still describes "who played" and filled slots; the entry is out of date until
  the orchestrator updates it.
- No staleness notice on the detail: offline, cached data shows without a warning there.
- `PastJamsCopy` repeats two strings from `NextJamCopy` (key description, dropped rows).

## User Approvals

Recorded 5 October 2026:

- **S1 — superseded.** The user first chose expandable rows (revision 2), then answered "para las
  jams pasadas no importa quién tocó, solo la lista de temas": the detail is the song list only.
- **D1 — no longer needed.** No demo past jam, no `bluesjam.demoPastJam`: there are no musician
  names to show, and the real 2026-07-25 jam (13 songs) covers the device check.
- **C1 — approved** as written (Copy table).
- **K3 — key on past rows (optional, default keep).** Each row keeps the key because it is part of
  the song as played that night, in the amber `key` role. If you want the archive without keys,
  say so: rows become position, title, artist, and the allowlist entry is dropped (no amber at all
  in `:feature:past-jams`). Not blocking.
