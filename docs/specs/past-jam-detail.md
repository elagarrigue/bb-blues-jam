# Feature Implementation Spec: Show a past jam read-only

## Source Feature

- `id`: past-jam-detail
- `area`: feature-past-jams
- `depends_on`: `past-jams-list` (`accepted`)
- `status`: not_started at planning time
- `source`: `feature_list.json` (entry, verification, notes: "Editing history is not supported.
  Recorded in docs/user-and-access-model.md.")
- **Blocked on User Approvals C1, S1 and D1** (end of this file) before implementation starts.
- **Shared files this slice edits** (sequencing with `bottom-navigation` and
  `unpublished-setlist-state`, planned concurrently): `app/.../navigation/AppRoutes.kt`,
  `app/.../navigation/AppNavHost.kt`, `app/.../TemporaryTabs.kt`, `app/src/test/.../AppRoutesTest.kt`,
  `konsist-test/.../ModuleIsolationTest.kt` (one allowlist entry). With D1 also
  `app/build.gradle.kts`, `app/src/debug/.../DebugOverrides.kt`, `app/src/release/.../DebugOverrides.kt`.
  It does **not** touch `:feature:next-jam`, `:core:data` or `BluesJamApp.kt`. `bottom-navigation`
  replaces `TemporaryTabs` and reshapes `AppNavHost`, so the two must not run in parallel; whichever
  lands second re-reads those files. If `bottom-navigation` lands first: the `Anteriores` tab
  destination passes `onOpenJam`, and `AppRoutes.PAST_JAM` sits above the tabs exactly as
  `AppRoutes.SONG_DETAIL` does (full screen, no bar). No overlap expected with
  `unpublished-setlist-state` (Próxima jam only), but re-read `AppNavHost`/`TemporaryTabs` at HEAD.

## Goal

Tapping a past jam in **Anteriores** opens it full screen: a back control, the date with the year
and the venue, then its setlist in position order. Each song shows its position, title, artist,
**key** (the only amber on the screen, D-17/DESIGN.md §5) and **who played**: the instrument strip
with filled slots and `Otros` only. There is no filter, no expansion, no song-detail link, no open
slot, no hint, no "No quedan cupos libres." and no admin control. The archive is muted. Read-only:
the Sheet owns past jams (D-04), nothing is mutated (D-13).

## Non-Goals

- Editing history in any form, admin controls, `AdminSession` (does not exist yet).
- Opening the song detail from a past jam (S1); expandable rows; the lineup panel.
- Instrument filter, open-slot counts, the jam summary counter.
- Staleness notice, error block or retry on the detail (same as song detail, Decision 6 there).
- The bottom bar, transitions, deeplinks (`bottom-navigation`).
- Any `:core:data`, Apps Script or Sheet change; any external API (D-09); any new library.

## Job Story

When I want to remember what was played at a jam and who played it,
I want to open that jam from Anteriores and read its songs, keys and musicians,
so I can avoid repeating a song or know who to ask about one.

## Users And Permissions

Musician and admin: identical read-only view. The presenter's only dependency is `JamsRepository`,
so it cannot read an admin flag; admin slices must never add one here (user-and-access-model.md).

## Acceptance Scenarios

1. **Open.** Given Anteriores shows 2026-07-25 with "13 temas", when the musician taps the row,
   then the past jam opens full screen (no tab bar) with "Sábado 25 de julio de 2026", the venue,
   "13 temas", and 13 rows in position order, each with position "01"…"13", title and key.
2. **Who played.** Given a song whose lineup has Guitarra "Tincho", Bajo "Lucía" and an extra
   "saxo, Valeria", then its strip shows `Gtr: Tincho`, `Bajo: Lucía`, `+ saxo: Valeria` (the
   `:core:ui` strip copy) and no other chip.
3. **No open slot, ever.** Given a past jam whose lineup (built in a test fake) contains open slots,
   then no row has an `OPEN_SLOT` chip and no text contains `LIBRE`, `libre` or `cupo`.
4. **Nobody recorded.** Given a song with an empty lineup and no extras (the real 2026-07-25 jam),
   then the row draws no strip and no note.
5. **Not openable rows.** A `NotShown` row in Anteriores (past draft, unavailable, no songs) is not
   clickable and has no click label; a `Songs` row is one clickable node (`Role.Button`, ≥ 48dp,
   click label from C1).
6. **Back.** Given the detail is open, when the musician taps `Volver` or presses system back,
   then Anteriores is shown, still selected, at the same scroll position. Two fast taps on `Volver`
   pop only the detail.
7. **Live changes.** Given the detail is open, when a snapshot arrives in which that jam's setlist
   is `Withheld`/`Unavailable`/empty, the header stays and the list-row line (P1 copy) replaces the
   songs; when the jam is no longer in `past`, the not-found block (C1) is shown; back still works.
8. **Dropped rows.** `Setlist.Available(songs, droppedRows = 2)` → the note "Faltan 2 temas: no se
   pudieron leer." under the rows; none when 0.
9. **Rotation** keeps the same jam open; back still returns to Anteriores.
10. **Amber only on keys.** `feature/past-jams` reads only the `key` amber role (Konsist allowlist).

## Repository Research

### Files Inspected

`AGENTS.md`, `PROGRESS.md`, `feature_list.json`, `CONTEXT.md`, `DESIGN.md` (front matter, components,
Core Screens 4–5, Required States), `bb-blues-jam-design-prompt.md` (§1, §4, §5),
`.claude/skills/architecture/SKILL.md`, `docs/domain-model.md` and `docs/user-and-access-model.md`
(grep: past/historical), specs `past-jams-list`, `song-detail-screen`; code: all of
`feature/past-jams/src/main`, `SongDetailPresenter.kt`, `SongDetailUiModel.kt`, `SongDetailCopy.kt`,
`SongDetailScreen.kt` (head), `SongDetailModelShapeTest.kt`, `NextJamPresenter.kt`,
`NextJamUiModel.kt`, `NextJamCopy.kt`, `NextJamScreen.kt` (grep), `AppNavHost.kt`, `AppRoutes.kt`,
`AppRoutesTest.kt`, `TemporaryTabs.kt`, `app/src/debug/.../*.kt`, `app/src/release/.../DebugOverrides.kt`,
`app/build.gradle.kts` (demo flag), `core/ui/.../lineup/LineupPanelMapper.kt`,
`LineupPanelUiModel.kt`, `strip/*` (grep), `core/model` `Setlist.kt`, `Lineup.kt` (grep),
`ModuleIsolationTest.kt` (allowlist), `docs/sheet-seed/2026-07-25.csv`, `Jams.csv`.
Not inspected: `presenter-pattern.md`, `InstrumentGroups*`, `BackButton.kt` bodies, Stitch export.

### Existing Patterns To Follow

- `SongDetailPresenter`: full-screen read of `observeJams()`, `Loading(description, back)` /
  `NotFound(empty, back)` / content, `backUiModel(onBack)`, `rememberUpdatedState` for `onBack`,
  pure `JamsSnapshot.toX(...)` mapping, shape test by reflection.
- `NextJamPresenter.Params(onOpenSong)` + `rememberUpdatedState`; `:app` binds callbacks to
  `NavController`, routes only in `AppRoutes`; the `RESUMED` guard on `onBack` in `AppNavHost`.
- `Lineup.toInstrumentChips(extras)` and `InstrumentStrip(chips)` in `:core:ui` (open chips read
  `slotOpen` only inside `:core:ui`).
- Copy in `internal object …Copy`; colours in a JVM-testable `…Defaults`; no dp/sp/colour literal.

### Findings

- `JamsSnapshot.past` keeps only filled slots (P6, domain-model.md:100), so the strip already has
  no open chip; this slice still filters defensively (scenario 3) so the guarantee does not rest on
  one mapper.
- `toLineupPanel`/`toInstrumentGroups` always set the "No quedan cupos libres." note or the hint:
  reusing them would show a notion of open slots. Hence S1 (no panel, no song detail).
- The real past jam (2026-07-25) has no musician in any cell: on the device every row would show no
  strip. Filled chips can be seen on the device only with demo data (D1).
- `PastJamsPresenter` is `Presenter<PastJamsUiModel, Unit>`; rows have no events today.
- `AMBER_ROLE_ALLOWLIST` has no `feature/past-jams` entry; the `past-jams-list` spec anticipated
  this slice adding `key`.

## Technical Approach

**1. List entry point** (`PastJamsPresenter`, `PastJamsUiModel`, `PastJamsScreen`).
`PastJamsPresenter : Presenter<PastJamsUiModel, PastJamsPresenter.Params>`,
`data class Params(val onOpenJam: (jamDate: LocalDate) -> Unit = {})`, read through
`rememberUpdatedState`; `toUiModel(now, onRetry, onOpenJam = {})`. `PastJamRowUiModel` gains
`openLabel: String?` (C1 click label; null when the summary is `NotShown`) and
`events: EventHandler<Event>` with `sealed interface Event : UiEvent { data object Open }` (calls
`onOpenJam(date)`). `PastJamsScreen(onOpenJam: (LocalDate) -> Unit, modifier, contentPadding,
presenter)`. Row: when `openLabel != null`, `Modifier.clickable(onClickLabel = openLabel,
role = Role.Button) { events(Open) }` + `heightIn(min = LocalMinimumInteractiveComponentSize.current)`,
still `mergeDescendants`; otherwise unchanged. No chevron, no colour change (archive treatment
stays). Mechanical update of the `present(Unit)` call sites in tests.

**2. Detail presenter** (`PastJamDetailPresenter.kt`, `:feature:past-jams`).
`class PastJamDetailPresenter(private val jams: JamsRepository) :
Presenter<PastJamDetailUiModel, PastJamDetailPresenter.Params>`, `Params(jamDate: LocalDate,
onBack: () -> Unit)`. Body as `SongDetailPresenter` (collect once, `rememberUpdatedState(onBack)`).
Pure `internal fun JamsSnapshot.toPastJamDetail(jamDate, onBack)`:

| Input | Model |
|---|---|
| no emission | `Loading(PastJamsCopy.DETAIL_LOADING, back)` |
| no jam with `date == jamDate` in `past` (never `upcoming`) | `NotFound(EmptyStateUiModel(C1), back)` |
| found, `Available`, songs non-empty | `Jam(header, Songs(rows, droppedRowsNote), back)` |
| found, `Available` with no songs / `Withheld` / `Unavailable` | `Jam(header, NotShown(EMPTY_SETLIST / SETLIST_NOT_PUBLISHED / SETLIST_UNAVAILABLE), back)` |

Header: `dateLabel = pastJamDateLabel(date)`, `venue`, `countLabel = songCount(n)` only for `Songs`
(else null). Row per `JamSong`: `position`, `positionLabel` (`padStart(2,'0')`), `title`, `artist`
(null when blank), `key = key.value` (D-08), `keyDescription = "Tonalidad $key"`, `instruments =
Lineup(lineup.slots.filterNot { it.isOpen }).toInstrumentChips(extraParticipants)`.

**3. UiModel** (`PastJamDetailUiModel.kt`): `sealed interface PastJamDetailUiModel : UiModel { val
back: BackUiModel }` with `Loading(description, back)`, `NotFound(empty, back)`, `Jam(header:
PastJamHeaderUiModel, setlist: PastSetlistUiModel, back)`. `PastJamHeaderUiModel(dateLabel, venue,
countLabel: String?)`. `sealed interface PastSetlistUiModel : UiModel { Songs(rows:
List<PastSongRowUiModel>, droppedRowsNote: String?); NotShown(message) }`.
`PastSongRowUiModel(position, positionLabel, title, artist: String?, key, keyDescription,
instruments: List<InstrumentChipUiModel>)` — **no event, no filter, no expansion, no admin field**.

**4. Archive treatment** (`PastJamDetailDefaults`, internal). `headerStyle`: date `textMuted`
(`h1`), venue and count `textMuted` (`body`). `rowStyle`: fill `surface`, position `textMuted`,
title `textMuted` (`songTitle`), artist `archive` (`body`, 5.39:1 on `surface`), key `colors.key`
(`key` typography, the only amber), note `textMuted` (`caption`). Back arrow is `BackButton`
(`text`). `PastJamDetailDefaultsTest`: every non-key colour is one of `surface`, `textMuted`,
`archive`; key colour is the `key` role.

**5. Screen** (`PastJamDetailScreen(jamDate, onBack, modifier, contentPadding, presenter =
koinInject())` → `PastJamDetailContent(model, …)`): a `LazyColumn` on `background`, padding as
`SongDetailContent`. Items: `BackButton(model.back)`; Loading → nothing else (list content
description = `description`, as the song detail); NotFound → `EmptyStateBlock`; Jam → header
(date as heading), then rows (key = position) or the `NotShown` message (`body`, `textMuted`), then
the dropped-rows note. Row: `Surface(fill, shapes.md)`, padding `md`×`sm`, a title line
(position, title weight 1, key with `contentDescription = keyDescription`), the artist when
present, then `InstrumentStrip(instruments)` when non-empty. One merged node per row, not
clickable, no role, no ripple, no chevron. Previews (`PastJamDetailPreview.kt`) built through
`toPastJamDetail`: Loading, NotFound, Jam with names + extras + an empty lineup + dropped note,
Withheld.

**6. Koin.** `pastJamsModule` adds `factory { PastJamDetailPresenter(get()) }`; `PastJamsModuleTest`
resolves both, each a new instance.

**7. Navigation (`:app`).** `AppRoutes`: `PAST_JAM = "pastJam/{$JAM_DATE}"`, `pastJam(date) =
"pastJam/$date"`, `parsePastJam(jamDate: String?): LocalDate?` (reuses `toIsoDateOrNull`). Update
the `TABS` KDoc (three tabs). `AppNavHost`: `TemporaryTabs(onOpenSong = …, onOpenPastJam = { date ->
nav.navigate(AppRoutes.pastJam(date)) { launchSingleTop = true } })`; a new
`composable(AppRoutes.PAST_JAM, arguments = listOf(navArgument(JAM_DATE) { StringType }))` drawing
`PastJamDetailScreen(date, onBack = <same RESUMED guard>, contentPadding = systemBars)`; null
argument → `LaunchedEffect(Unit) { nav.popBackStack() }`. `TemporaryTabs` gains `onOpenPastJam` and
passes it to `PastJamsScreen(onOpenJam = …)`. The tab and the list scroll survive because the
`TABS` entry keeps its saveable state (as for the song detail).

**8. Amber allowlist.** `AMBER_ROLE_ALLOWLIST` gains `"feature/past-jams" to setOf("key")`.

**9. Debug demo past jam (only if D1 = yes).** `bluesjam.demoPastJam=true` in `local.properties` →
`BuildConfig.DEMO_PAST_JAM` (debug; release hard `false`), same comment style as the upcoming flag.
`app/src/debug/java/com/bbbjam/debug/`: `DemoPastJam.on(today)` dated `today.minusDays(14)`, venue
`DemoUpcomingJam.VENUE`, `PUBLISHED`, `Available(songs, droppedRows = 1)`, lineups built **with
filled slots only** (P6 shape): one song fully filled, one partly filled, one with no musician and
no extra, one with a short and one with a long extra, keys `Bb` and `F#m`; titles from
`catalog-seed.json`. `DemoPastJamRepository(real, calendar)`: adds the demo to `past` unless a real
past jam has that date, keeps newest-first, never touches `upcoming`, `freshness`, Room or the Sheet;
`refresh()` delegates. `debugOverrides()` wraps the real repository with each enabled decorator.
`isDemo()` already matches by venue. Release `DebugOverrides.kt` unchanged in behaviour. Test
`DemoPastJamRepositoryTest` in `app/src/testDebug/`.

## Expected File Changes

- `feature/past-jams/src/main/.../`: modify `PastJamsPresenter.kt`, `PastJamsUiModel.kt`,
  `PastJamsScreen.kt`, `PastJamsStatesPreview.kt`, `PastJamsCopy.kt`, `di/PastJamsModule.kt`;
  create `PastJamDetailPresenter.kt`, `PastJamDetailUiModel.kt`, `PastJamDetailDefaults.kt`,
  `PastJamDetailScreen.kt`, `PastJamDetailPreview.kt`.
- `feature/past-jams/src/test/.../`: modify `PastJamsPresenterTest`, `PastJamsStatesTest`,
  `PastJamsModuleTest`; create `PastJamDetailMappingTest`, `PastJamDetailPresenterTest`,
  `PastJamDetailDefaultsTest`, `PastJamDetailModelShapeTest`.
- `app/.../navigation/AppRoutes.kt`, `AppNavHost.kt`, `app/.../TemporaryTabs.kt`,
  `app/src/test/.../navigation/AppRoutesTest.kt` — modify.
- `konsist-test/.../ModuleIsolationTest.kt` — one allowlist entry.
- D1 only: `app/build.gradle.kts`, `app/src/debug/.../DebugOverrides.kt`, new `DemoPastJam.kt`,
  `DemoPastJamRepository.kt`, `app/src/testDebug/.../DemoPastJamRepositoryTest.kt`.

## Visual Design Impact

- UI: yes. Sources: `DESIGN.md` Core Screen 5, the strip rules, Required States;
  `bb-blues-jam-design-prompt.md` §5. New artifact: no.
- States: detail loading (back only), not found, jam with songs, jam with a `NotShown` line;
  Anteriores rows become tappable (no visual change beyond the ripple).

## Durable Documentation Impact

- `DESIGN.md`: update Core Screen 5 with the as-built layout, archive colours, C1 copy; Core
  Screen 4 "rows open the jam".
- `.claude/skills/architecture/SKILL.md`: update `:feature:past-jams` (detail presenter, allowlist
  `{key}`), `:app` row (`PAST_JAM` route), the debug demo paragraph if D1.
- `docs/risks-and-open-questions.md`: note no staleness notice on the detail; previews not rendered.
- `PROGRESS.md`, `feature_list.json`: evidence, status `passing`.
- `AGENTS.md`, `CONTEXT.md`, `docs/domain-model.md`: not needed. `ARCHITECTURE.md`,
  `CONSTRAINTS.md`: not present in this repo.

## Implementation Plan

1. Confirm C1/S1/D1 answers; re-read the shared `:app` files at HEAD; `CI=true ./init.sh` baseline.
2. Detail UiModel, copy, pure mapping and tests; presenter and its Molecule test; shape test.
3. Defaults + test, screen, previews, Koin.
4. List entry point (Params, row event, clickable row) and its tests.
5. `:app` routes + test, NavHost destination, `TemporaryTabs`; allowlist entry.
6. D1 demo if approved; failure demonstrations; docs; final gate; device check.

## Verification Plan

- `CI=true ./init.sh` exit 0, `konsist: wired` (17/17, unchanged count), `detekt: wired`,
  `ktlint: wired`; previous result files unchanged in counts except the modified test classes.
- `PastJamDetailMappingTest` (strings written out): every table row of Technical Approach 2;
  lookup ignores `upcoming` with the same date; open slots dropped (scenario 3); empty lineup → no
  chips; blank artist → null; key equals `JamSong.key` not `Song.defaultKey`; dropped note 0/1/2.
- `PastJamDetailPresenterTest` (Molecule + Turbine): Loading → Jam; Jam → NotFound on a snapshot
  without the date (a transition); `Back` calls the **current** `onBack` after Params change.
- `PastJamDetailModelShapeTest`: declared fields of `PastSongRowUiModel` and `Jam` exactly the
  approved sets, none matching `event|filter|admin|edit|expand|open|libre`; presenter constructor
  takes only `JamsRepository`.
- `PastJamsPresenterTest`/`PastJamsStatesTest`: `Open` on a `Songs` row calls `onOpenJam(date)`;
  stale handler calls the current callback; `NotShown` rows have `openLabel == null`.
- `AppRoutesTest`: `pastJam(2026-07-25)` = `pastJam/2026-07-25`; parse accepts/rejects as for the
  song route; every-month round trip.
- **Failure demonstrations** (edit, run, restore; record messages): (1) drop the `filterNot { isOpen }`
  → mapping test fails; (2) `colors.slotOpen` in `PastJamDetailScreen.kt` → `amber-roles-allowlisted`
  fails; (3) add `val isAdmin: Boolean` to `PastSongRowUiModel` → shape test fails; (4) drop
  `rememberUpdatedState` in the list presenter → stale-handler test fails; (5) remove the allowlist
  entry → `amber-roles-allowlisted` fails on `colors.key`.
- Greps on `feature/past-jams/src/main`: no `androidx.navigation`, `MaterialTheme.`, `Color(`,
  dp/sp literal, `toLineupPanel`, `toInstrumentGroups`, `AdminSession`, `InstrumentFilter`, tú forms.
- **Device (Pixel 5, outside the gate, optional; no Sheet edits, no TalkBack).** Real jam: open
  Anteriores, tap 2026-07-25 → detail full screen, 13 rows, amber keys, no strip, no tab bar;
  `uiautomator dump`: the list row is one clickable node with the C1 click label, detail rows are
  not clickable, back ≥ 48dp "Volver". Back button, system back, double tap, rotation → Anteriores
  selected at the same scroll. With D1: the demo past jam shows chips with names, `+ saxo`, a row
  with no strip, the dropped note, and no `LIBRE`. Restore `local.properties` flags and rotation;
  crash buffer empty. Never log the Apps Script URL.

## Evidence To Capture

Gate output and counts, each demonstration's failure message, grep results, device dump excerpts
and screenshots (if run), in `feature_list.json` and `PROGRESS.md`.

## Validator Checklist

- [ ] Only `:feature:past-jams` (deps `:core:ui`, `:core:data`) and the listed `:app`/Konsist files changed.
- [ ] No open-slot affordance: open slots filtered, no panel/groups mapper, no `LIBRE`, no hint/note.
- [ ] No filter, no admin field, no event on detail rows; presenter takes only `JamsRepository`.
- [ ] Amber only `key`, allowlisted; archive colours from `PastJamDetailDefaults`.
- [ ] Routes only in `AppRoutes`; no navigation import in features; back guarded; state restored.
- [ ] Copy matches C1; no mutation, no external API, no `:core:data` change.
- [ ] D1 code only in `app/src/debug` (release no-op); demonstrations recorded; three `wired`.

## Risks

- Conflicts with `bottom-navigation` in `AppNavHost`/`TemporaryTabs` (see Source Feature).
- No staleness notice on the detail: offline, the musician sees cached data without a warning
  there (the list shows it).
- The real archive has no musician names, so "who played" is empty until the Sheet records them.

## User Approvals

Answered by the user on 5 October 2026: **S1 expandable rows** (the alternative, not the
recommendation): the spec must be revised before implementation, including the `:core:ui` panel
variant without free-slot messages. **C1** copy approved as written. **D1** yes: debug-only demo past
jam behind `bluesjam.demoPastJam`.

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
  only, Technical Approach 9), because the real 2026-07-25 jam has no musician names and filled
  chips could not be seen on the device. Alternative: JVM tests and previews only.
