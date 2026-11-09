# Spec: musician-filter

Filter the next-jam list by signed-up musician. Planned 9 November 2026. Everything labelled
"Assumption" was not asked of the user; everything labelled "Inspected" was read in the repo.

## Source feature

- id `musician-filter`, area `feature-setlist`, status `not_started`.
- depends_on: `instrument-filter-chips`, `admin-catalog-tab` (stated accepted by the orchestrator).
- User decisions (9 Nov 2026): names come from assigned slots AND the Otros extras of the upcoming jam; the
  admin always uses distinct names, so match on normalized name; it combines with the instrument filter;
  the chosen name is remembered on the device; local read-only filter, no mutation.
- Sizing: one pure rule, one small preference store, one filter block. Correctly sized for one session.
  Next feature `song-played-state` depends on this one (it touches the same list).

## Goal and non-goals

Goal: anyone (musician or admin) taps a name on Próxima jam and sees only the songs where that person
plays, with an answer ("Quedan 3 de 13 temas con Juan"), an empty state with a clear action, and the name
remembered across relaunches.

Non-goals: no mutation, no Apps Script change, no Sheet or Room change; no filter on Jams or the song
detail; no fuzzy, accent-insensitive or alias matching; no musician entity or profile (a musician is a name
on a slot); no multi-select of names; no change to the instrument filter's semantics (D-18: extras never
count as open slots).

## Job story and acceptance scenarios

When I open the next jam, I want to pick my name and see only my songs, so I know my night at a glance.

1. Given a published jam with Juan on slots in songs 2 and 5 and as an Otros extra in song 7, when I choose
   Juan, then the list shows songs 2, 5, 7 and the line "Quedan 3 de 13 temas con Juan".
2. Given "Juan" in one song and "juan  " (extra spaces, other case) in another, then both are one name in
   the picker (first spelling in position order is displayed) and both songs match.
3. Given Juan chosen and the instrument chip Bajo selected, then rows are songs with Juan AND an open bass
   slot; the line reads "Quedan 1 de 13 temas con Juan y cupo libre para bajo"; the instrument chips' counts
   are computed over Juan's songs only.
4. Given Juan chosen and no song has Juan, or Juan and Bajo match nothing, then no row is drawn and an own
   empty block names the filter with the action "Ver todos los temas" (clears the musician, and the
   instrument selection when it contributed to the empty result).
5. Given I chose Juan and relaunched the app, then Juan is still chosen. If Juan is no longer in the jam
   (a new jam, a name removed), the filter is still shown with his name and yields scenario 4's empty state
   with its clear action. Clearing removes the stored name.
6. Given nobody is assigned in the whole setlist and nothing is remembered, then the musician block is not
   drawn (nothing to pick). A draft or withheld jam, an unavailable setlist and a setlist with no songs
   draw no musician block, as today with the instrument bar.
7. Given an admin with a Sending assignment or extra, then the picker, counts and rows use the overlaid
   songs (same source the instrument filter uses); pending rows and failure cards are never hidden by
   either filter.
8. Rotation, tab switch and the song detail keep the selection (it lives in the store, not in the composition).

## Repository research (inspected)

- `core/ui/.../filter/`: `InstrumentFilterBar`, `InstrumentFilterMapper` (`instrumentFilterBar(lineups,
  selected, onChange)`, `matchesInstrumentFilter`, private `names()`), `InstrumentFilterChange`,
  `InstrumentFilterDefaults` (the only reader of `activeFilter`). Selected chip = `activeFilter` fill;
  the bar is the list's second item (`FILTER_KEY` in `NextJamScreen.kt`).
- `feature/next-jam/.../NextJamPresenter.kt`: the filter is `rememberSaveable(stateSaver =
  InstrumentFilterSaver)`, applied in `Setlist.toUiModel(date, RowState)` on `shownEntries` (overlaid, full
  order); `SetlistUiModel.Songs(rows, droppedRowsNote, filterBar)`; the presenter is a Koin `factory` in
  `nextJamModule` (`NextJamPresenter(get(), get(), get(), get(), get())`, last is `random`).
- `core/model`: `JamSong.lineup.slots[].musicianName` (null when open), `JamSong.extraParticipants[].name`,
  `MusicianName` (private whitespace normalizer, `parseOrNull`).
- Persistence: DataStore Preferences already exists in `:core:data` only (`AdminCredentialStore`, file
  `admin_session`, `OkioStorage` factory `dataStore(...)`, bound in `dataModule`). Room is a re-fetchable
  cache (destructive), so it is the wrong place. Features cannot import `androidx.datastore.` (Konsist).
- DESIGN.md "Filter chip" says the instrument selection "is lost on relaunch"; that stays true for
  instruments, not for the musician name. Design prompt requires: filter bar count, distinct no-results state
  that names the filter and offers to clear, and (line 662) "Filter with no results".
- Not inspected: `bb-blues-jam-bitacora.md`, `docs/user-and-access-model.md` (no decision found that this
  contradicts; D-04/D-08/D-09/D-13 untouched).

## Technical approach

Module targets and allowed dependencies (architecture skill):

- `:core:model` (pure Kotlin): new `MusicianRoster.kt`: `fun musicianMatchKey(name: String): String`
  (collapse whitespace, trim, `lowercase(Locale.ROOT)`; implementer may share the whitespace logic with
  `MusicianName`'s private normalizer), `fun JamSong.involves(matchKey: String): Boolean` (any slot
  `musicianName` or extra `name` with that key), and `fun List<JamSong>.musicianRoster(): List<RosterEntry>`
  where `RosterEntry(name, matchKey, songCount)` is deduplicated by key, displayed with the first spelling in
  position order, sorted by `matchKey` (case-insensitive alphabetical), blank and `-`-like placeholders
  skipped. No clock, no Android.
- `:core:data` (only module allowed DataStore): new package `musician/` (name is an implementer detail):
  public `MusicianFilterPreference` (`observeSelected(): Flow<String?>`, `suspend select(name: String?)`;
  blank or null clears) and internal `DefaultMusicianFilterPreference` over its **own** DataStore file
  `musician_filter` (key `musician_name`), built with the same okio factory as `AdminCredentialStore`
  (extract or reuse `AdminCredentialStore.dataStore(scope, produceFile)`; do not open a second instance on
  `admin_session`). A corrupt or unreadable file reads as none. Bound as `single<MusicianFilterPreference>`
  in `dataModule`. **No new dependency** (DataStore Preferences 1.2.1 is already pinned and used here).
- Mutation contract (D-13): this is a local device preference, not a Sheet or setlist mutation and not
  something the phase 2 assistant must perform; its "write" is `MusicianFilterPreference.select`, a
  repository function, never state that exists only in a composable. Never log the name.
- `:core:ui` `com.bbbjam.core.ui.filter`: `MusicianFilterUiModel`, `MusicianFilterBar` composable,
  internal `MusicianFilterDefaults` (reads `activeFilter`/`onActiveFilter` here so `:feature:next-jam` stays
  at `{key}` and the Konsist allowlist is unchanged), pure mapper `musicianFilter(...)`, `MusicianFilterChange`
  (`Select(name)`, `Clear`; handlers carry the change, as for instruments). Copy in
  `core/ui/src/main/res/values/strings.xml`, names prefixed `musician_filter_`, as `UiText`.
- `:feature:next-jam`: `NextJamPresenter` gains a `MusicianFilterPreference` constructor parameter (Koin
  factory gets one more `get()`); it collects `observeSelected()` with **no initial value** and stays
  `Loading` until the first read (like `SongDetailPresenter` and the admin flag), so the list never flashes
  unfiltered. A handler runs `preference.select(...)` in `rememberCoroutineScope`; the UI reflects the Flow.
  `Setlist.toUiModel` applies, in order: roster from `shownEntries`; musician predicate (`involves`);
  instrument bar built from the musician-filtered lineups (chip counts over those songs); then the
  instrument predicate. `SetlistUiModel.Songs` gains `musicianFilter: MusicianFilterUiModel?` (null in the
  cases of scenario 6; the existing `filterBar` keeps its current null rule).
- Combined messaging (presenter decides, screen draws): when a musician is selected, the musician block owns
  the count/empty line and the instrument bar's own `summary` and `noResults` are set to null; with no
  musician selected nothing changes for the instrument bar (existing tests unchanged).

UI (read DESIGN.md "Filter chip"): the block sits above the instrument filter bar, same list-item style
(`FILTER_KEY` neighbour item, scrolls with the list). Heading `Filtrá por músico` (caption, `textMuted`,
screen-reader heading). Not selected: one 48dp chip-like button `Elegir músico` (`surfaceRaised`,
`textMuted`, `rounded.lg`) opening a Material 3 `AlertDialog` (precedent: removal confirmation, add-extra
dialog; colours explicit from `BluesJamColors`, never amber) with `Todos los músicos` plus one
`Role.RadioButton` row per name, each 48dp with its song count ("Juan, 3 temas"). Selected: a chip with
`activeFilter` fill and `onActiveFilter` text showing the name (tap reopens the dialog to change) and a
text action `Quitar filtro` (underlined, 48dp) that clears; below, the polite live-region line. No horizontal
scroll; no dp/sp or colour literals outside `:core:ui`.

Copy (Rioplatense, vos; final wording may be adjusted but stays vos): `Filtrá por músico`, `Elegir músico`,
`Todos los músicos`, `Quitar filtro`; count plural by remaining rows: `Queda %1$d de %2$d temas con %3$s` /
`Quedan %1$d de %2$d temas con %3$s`, with instrument `... con %3$s y cupo libre para %4$s`; empty:
`%1$s no está en ningún tema de esta jam.` and, combined, `Ningún tema con %1$s tiene cupo libre para %2$s.`
with `Ver todos los temas` (reuse `filter_clear`). Instrument names reuse the existing lowercase/`names()`
phrasing (make the helper internal to the `filter` package).

## Expected file changes

- New: `core/model/.../MusicianRoster.kt` + test; `core/data/.../musician/MusicianFilterPreference.kt`,
  `DefaultMusicianFilterPreference.kt` + test; `core/ui/.../filter/Musician*.kt` (UiModel, Bar, Defaults,
  Mapper, Change) + tests; `docs/evidence/musician-filter/` (device notes, failing-case proofs).
- Edit: `core/data/.../di/DataModule.kt` (binding); `core/ui/src/main/res/values/strings.xml`;
  `InstrumentFilterMapper.kt` (share `names()`); `NextJamPresenter.kt`, `NextJamUiModel.kt`,
  `NextJamScreen.kt`/`NextJamListContent.kt` (draw the block), `di/NextJamModule.kt`,
  `NextJamPreview.kt`-style files for a preview in `*Preview.kt` (Konsist rule 22);
  tests `NextJamFilterTest.kt` (or a new `NextJamMusicianFilterTest.kt`) with a fake preference that stays
  in `:feature:next-jam` tests (single consumer).
- Not touched: `backend/apps-script`, Room schema, `SetlistRepository`, `:app` navigation, Konsist rules and
  allowlists (no new amber role for a feature, no new public feature name: everything new is `internal`).
- Assumption: the new store file is **not** excluded from backup (a name is not sensitive); the admin
  passphrase rules in `res/xml` stay as they are.

## Implementation tasks (in order)

1. `:core:model` roster rule and tests (normalization, dedup, first spelling, sort, extras count, placeholder
   and blank skipped, `involves`).
2. `:core:data` preference + `dataModule` binding + JVM test with a temp-file DataStore (same pattern as
   `AdminCredentialStore` tests: save, restart by cancelling the scope, clear, corrupt file).
3. `:core:ui` model, mapper, bar, defaults, strings; mapper tests (counts, plurals, empty states, handlers
   compare equal, "never amber" in the feature, amber only inside `Musician*Defaults`).
4. `NextJamPresenter` wiring, ordering of the two predicates, `Loading` until read, suppression of the
   instrument summary when a musician is selected, Koin factory update.
5. Screen item, preview file, `DESIGN.md` update (see docs).
6. Run `CI=true ./init.sh`; capture evidence; update `PROGRESS.md` and `feature_list.json`.

## Verification plan

All on the JVM (Molecule + Turbine, no Compose UI harness, per the project rule):

- Roster: sorted, deduplicated by normalized name, extras included, open and `-` cells excluded.
- Presenter: choose name filters rows; `Clear` restores; name + instrument = intersection; chip counts follow
  the musician-filtered songs; count line values (X after both filters, Y = all songs); empty states (name not
  in jam, combined zero) with a clear handler that empties the store; remembered selection restored from the
  store before the first non-`Loading` model; remembered absent name still shows the filter and the empty
  state; draft, withheld, unavailable and empty setlists have `musicianFilter == null`; admin overlays feed
  the roster and rows; pending rows and failure cards untouched; at least one state transition per test.
- Existing `NextJamFilterTest` cases still pass unmodified.
- Copy: a test pins resource ids (`UiText.res`), no `tú` forms in the new strings.
- `CI=true ./init.sh` exits 0 with `konsist`, `detekt`, `ktlint` printed `wired`; no baseline, no rule
  disable. No new Konsist rule is planned; if the implementer adds one, its failing case must be shown.
- Manual (device, debug demo jam with `bluesjam.demoUpcomingJam=true`, never Sheet data): pick a name, kill
  and relaunch the app, see it restored; rotate; open a song and go back; combine with a chip; clear.

## Durable documentation impact

- `DESIGN.md` "Filter chip": update (musician filter block, copy, remembered across relaunch; state that
  the instrument selection is still lost on relaunch). Update.
- `.claude/skills/architecture/SKILL.md`: add an "As built by `musician-filter`" paragraph (roster rule in
  `:core:model`, `musician/` preference in `:core:data`, filter component in `:core:ui`). Update.
- `PROGRESS.md`, `feature_list.json`: update with evidence.
- `docs/domain-model.md`, `docs/apps-script-api.md`, `docs/sheet-schema.md`, `AGENTS.md`: not needed (no
  entity, no backend, no rule change). Bitácora decision entry: not needed unless the user wants one.

## Validator checklist

- No ViewModel, no feature-to-feature import, no `get()` inside classes, no datastore import outside
  `:core:data`, no `MaterialTheme.colorScheme`, no amber read in `:feature:next-jam`.
- The filter is read-only and local: no call to any `Setlist...Repository`, nothing sent to Apps Script.
- Copy only in `strings.xml`, vos; presenters build `UiText` only.
- Combined filter semantics and the "quedan X de Y" numbers match scenarios 1 to 4; remembered-but-absent
  name yields the empty state, never a crash or a silently unfiltered list.
- Evidence recorded and `init.sh` output shows all three tools `wired`.

## Open points (defaults chosen, change only if the user objects)

- Matching is whitespace-collapsed and case-insensitive, not accent-insensitive.
- Picker is a dialog list, not one chip per name (a jam can have many names; chips would wrap for rows).
- With both filters active, the clear action clears both.
