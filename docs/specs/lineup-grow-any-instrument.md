# Feature Implementation Spec: Let the admin grow any instrument's slots up to three, with fixed Sheet columns

## Source Feature

- `id`: lineup-grow-any-instrument
- `area`: data+backend+next-jam
- `depends_on`: admin-row-icon-actions (`accepted`)
- `status`: `not_started` at planning time (12 October 2026)
- `source`: `feature_list.json`. User decisions (12 October 2026, do not reopen): amend D-18 so
  per-song slots can also **grow**, up to 3 per instrument, via **fixed** Sheet columns (`Guitarra
  1-3`, `Bajo 1-3`, `Batería 1-3`, `Voz 1-3`, `Armónica 1-3`, `Teclados 1-3` = 18 slot columns); a
  new song still starts with the 7 default slots, the 11 extra columns written `-`. The user adds
  the columns to the real Sheet and deploys Apps Script from a step-by-step guide this slice
  produces; then a read-only live check. `Otros` stays for anything beyond the lineup (D-18's other
  half is unchanged).

## Readiness

Builds on accepted code (all inspected, see **Repository Research**):

- `:core:model` — `Lineup`, `Instrument`, `Slot`, `SlotPosition`. `Lineup.withSlotCount` **already
  supports growth** (adds slots when `count > currentCount`), but both it and `Lineup`'s `init`
  guard cap every instrument at `defaultCount(instrument)` (2 for guitar, 1 for the rest). This
  slice's whole model change is separating "the lineup a new song starts with" (unchanged: 7 slots)
  from "the most slots an instrument can ever hold" (new: 3 for every instrument).
- `:core:data` — `SetlistMapper.lineupSlots` zips `Lineup.DEFAULT_INSTRUMENTS` (7 entries) against
  `SlotsDto.cells()` (7 named fields). `SetlistLineupChanges.confirmedSlots` and
  `SetlistSlotClears.confirmedSlots` each hardcode a 7-field `FIELDS` list and rebuild
  `Lineup.DEFAULT_INSTRUMENTS[column]` to turn a confirmed server answer into `JamSlotEntity` rows.
  `SetlistSongAdds.storeConfirmedSong` also uses `Lineup.DEFAULT_INSTRUMENTS` — correctly, because a
  new song's confirmed insert is always the 7-slot default and must stay that way.
  `SetlistAssignments` (`assignSlot`) and its DAO (`SetlistAssignmentDao.assignOpenSlot`,
  `slotsOf`) already operate on cached `jam_slot` rows with a free `columnIndex`, not a 0..6
  range — **no change needed there**.
- Room cache (`JamEntities.kt`, `SetlistSlotDao.replaceSlots`) — `JamSlotEntity.columnIndex` is
  already an unconstrained `Int` (only used as part of a composite primary key and for ordering by
  `column_index`). The schema needs **no migration**: the cache is destructively rebuilt from the
  Sheet on every structural change and is a pure re-fetchable mirror (`catalog-repository-cache`
  decision), so a wider column range is just a wider set of valid values for an existing `Int`
  column.
- `backend/apps-script/src/Jams.js` — `SLOT_FIELDS` is the 7-entry header↔field table read by
  `buildSetlist`/`buildJams` and re-exported for `Post.js`. `Post.js`'s `SLOT_COLUMNS_` maps each
  instrument name to its field list (`guitar: ['guitar1','guitar2']`, others to one field), used by
  `setSlotCount_`, `assignSlot_` and `clearSlot_` to validate ordinals and counts, and
  `presentSlotFields_`/`planSlotCount_` already implement "remove last open, restore first `-`"
  generically over whatever field list they are given — **no change needed there**, only to the
  field lists themselves and the one hardcoded upper bound in `setSlotCount_`
  (`count > fields.length`, which today equals `defaultCount` but must become a `MAX_SLOTS_` of 3).
  `createSetlistTab_`/`appendSetlistRow_` build a **new** jam tab and its rows from `setlistSpecs_()`
  (`SETLIST_FIELDS + SLOT_FIELDS + EXTRA_FIELD`) and only ever write mapped columns found by header
  name — so once `SLOT_FIELDS` lists 18 headers, a brand-new tab gets 18 columns and `addSong`
  writes to whichever of them exist in a given tab (old 7-column real tabs keep working; the new
  18-column tab the user creates by hand gets `-` written to the 11 extra cells for every new song,
  per the **Technical Approach** below).
- `docs/sheet-schema.md`, `docs/apps-script-api.md` — document the current fixed 7-column layout
  (`Slot columns` table, `SLOT_FIELDS`, `SlotsDto`) and the "slots only shrink" rule (D-18). Both
  need the 18-column layout, the dual-layout read behavior, and the growth write path.
  `bb-blues-jam-bitacora.md` D-18 needs a dated amendment (not a silent rewrite).
- `:core:ui` strip/filter/panel (`LineupChips.kt`, `InstrumentFilterMapper.kt`,
  `LineupPanelMapper.kt`) iterate `lineup.slots`/`openSlots` with no hardcoded count — **no change
  needed**; they will show up to 18 slots per song automatically once `Lineup` can hold them.
- `feature/next-jam` — `LineupReservations.lineupEditor`'s `+`/`−` caps (`canAdd = count <
  Lineup.defaultCount(instrument)`, `LineupControls.onLineupCount`'s `target in
  0..Lineup.defaultCount(instrument)`) must read the new max instead.
- `app/src/debug/.../DemoUpcomingJam.kt` requires `cells.size == Lineup.DEFAULT_INSTRUMENTS.size`
  (7) to build each demo song's starting lineup — stays unchanged; growth is exercised on-device by
  using the lineup editor's `+`, not by pre-seeding a grown demo song.

Size: one domain rule change, one backend field-table change plus one bound fix, four client mirror
sites, one editor bound fix, five docs updates, and one user migration guide. No new module, no new
mutation, no new route, no Room migration, no new screen.

## Goal

The admin can grow **any** instrument's per-song slot count up to **3**, not only guitar up to 2, so
a themed jam with three guitars (or three singers, three keyboardists, etc.) can declare that in
`Cambiar formación` instead of parking the third musician in `Otros`. A new song still starts with
today's 7 default slots (2 guitar, 1 each of bass/drums/vocals/harmonica/keyboards); the admin grows
from there with the same `+`/`−` editor, now enabled up to 3 for every instrument. The Sheet gains
three fixed columns per instrument (18 total) so there is no variable-width row to parse; a song
that does not use a third slot of some instrument has `-` in that column, exactly as it already
does for an unused second guitar.

Reads must accept **both** the old 7-column jam tabs (every real jam tab today) and the new
18-column layout, because the user adds the columns to the real Sheet by hand after this slice is
implemented, and the app must keep working on the existing tabs in the meantime and forever after
for any tab the admin never widens.

## Non-Goals

- A fourth slot of any instrument, or growth for `Otros` (D-18's "extra participants are never
  slots" half is unchanged; they stay free-text, uncounted, unassignable).
- Any UI redesign of the lineup editor beyond raising the `+` ceiling (`extras-dialog-and-lines` and
  `admin-mode-highlight` are separate, later slices).
- Migrating the real Sheet or deploying Apps Script — this slice produces the guide; the user
  performs both steps and reports back before the live check.
- Changing how `addSong`'s **existing** 7-column write path behaves for an old-layout tab: the
  default lineup a new song starts with is still 7 slots regardless of the tab's column count.
- Reordering or renaming instruments, or changing `Instrument` enum values.
- Any change to `assignSlot`, `clearSlot`'s request/response shape (they already resolve ordinals
  dynamically and need no change), or to the Room schema version.

## Job Story

When a themed jam needs more than the default number of one instrument (three guitars for a
"guitar battle" set, three vocalists for a gang chorus),
I want to grow that instrument's slot count in `Cambiar formación` up to 3, the same way I already
shrink it,
so I can represent the real lineup of that song without parking extra musicians in `Otros`, where
they would not show as an open slot for anyone else to claim.

## Users And Permissions

- **Admin**: sees the lineup editor's `+` enabled up to 3 for every instrument (today: enabled up to
  2 for guitar, 1 for the rest). Tapping `+` on an instrument already at 3 is impossible (button
  disabled), matching the existing pattern for `−` at 0. The mutation is the existing
  `SetlistRepository.setSlotCount(jamDate, songId, instrument, count)` — no new repository function,
  no new deeplink, so D-13 is satisfied by code this slice does not need to touch beyond widening
  its valid range.
- **Musician**: sees the extra open or filled slots in the strip, the filter and the song detail's
  lineup panel exactly like any other slot, with no UI change needed (the shared `:core:ui`
  components already iterate the lineup's actual slots).
- **Server**: validates `0..3` for every instrument (today: `0..fields.length`, which is 2 or 1)
  before writing, never overwrites an occupied cell (unchanged rule, already generic over the field
  list), and never has to guess a tab's column count — it reads headers by name as it always has.

## Acceptance Scenarios

### Scenario 1: growing a non-guitar instrument past its old default

Given an 18-column jam tab with a song whose `Voz` lineup is the default 1 slot, all open,
When the admin opens `Cambiar formación` and taps `+` on `Voz` twice,
Then the editor shows up to 3 `Voz` slots pending one at a time (`Guardando…`), each confirmed
`setSlotCount` call succeeds, and the strip, filter count and song detail panel show 3 vocal slots.

### Scenario 2: the ceiling is enforced in the editor and the server

Given a song whose `Guitarra` lineup already has 3 slots (2 filled, 1 open),
When the admin looks at the editor,
Then `+` on `Guitarra` is disabled (matching the existing disabled-at-floor pattern for `−`), and a
direct `setSlotCount` call for count 4 is rejected by the server with `invalid_count` before any
write (and locally by the repository the same way an out-of-range count is rejected today).

### Scenario 3: an occupied slot is still never silently dropped

Given a song whose `Batería` lineup has 3 slots, all filled,
When the admin taps `−` on `Batería`,
Then the server refuses with `slot_filled` before writing (unchanged rule, now exercised at a count
of 3, not just 2 or 1), and the editor shows why removal is blocked, exactly as it does today at the
old default.

### Scenario 4: reads still work on the old 7-column layout

Given a real jam tab that still has only the original 7 slot columns (no migration done yet),
When the app reads that jam (GET `jams`, the admin's `readJams`, or a cached refresh),
Then the setlist maps exactly as it does today: 7 possible slots, each respected by `-`/open/filled,
with no error and no missing-header failure, because the 11 new headers are optional columns the
mapper tolerates being absent.

### Scenario 5: a new song on an 18-column tab starts with 7 open slots and 11 `-` cells

Given an 18-column jam tab with no existing row for a song,
When the admin adds that song to the setlist (`addSong`),
Then the new row has the 7 default slots open (as today) and the 11 extra slot columns (`Guitarra
3`, `Bajo 2-3`, `Batería 2-3`, `Voz 2-3`, `Armónica 2-3`, `Teclados 2-3`) written `-`, so none of
them is mistaken for an open slot until the admin explicitly grows that instrument.

### Scenario 6: growing writes only the changed cell, never moves a name

Given a song whose `Bajo` lineup has 1 slot, filled, and `Bajo 2`/`Bajo 3` are `-`,
When the admin grows `Bajo` to 2,
Then only `Bajo 2`'s cell changes (from `-` to an empty open slot); `Bajo 1`'s musician's cell is
never written, matching the existing "no name ever moves" rule for shrink/restore.

## Repository Research

### Files Inspected

- `core/model/src/main/kotlin/com/bbbjam/core/model/Lineup.kt` — `DEFAULT_INSTRUMENTS` (7 entries,
  the Sheet's canonical column order for the default lineup), `DEFAULT_COUNTS`/`defaultCount`,
  `withSlotCount` (already grows, capped at `defaultCount`), the `init` guard (caps at
  `DEFAULT_COUNTS`).
- `core/model/src/main/kotlin/com/bbbjam/core/model/Instrument.kt`, `Slot.kt`, `SlotPosition.kt` —
  no instrument-count assumption; unaffected.
- `core/model/src/test/kotlin/com/bbbjam/core/model/LineupTest.kt` — exercises the current cap
  (`slot counts outside the default range are rejected`, `more slots of an instrument than the
  default are rejected`, with GUITAR=3 as the over-the-top case); these tests must be rewritten for
  the new cap, with a new over-the-(new)-max case.
- `core/data/src/main/kotlin/com/bbbjam/core/data/remote/JamDto.kt` — `SlotsDto`, 7 named nullable
  `String?` fields plus `cells()` returning them in `DEFAULT_INSTRUMENTS` order; `@Serializable`.
- `core/data/src/main/kotlin/com/bbbjam/core/data/jams/SetlistMapper.kt` — `lineupSlots` zips
  `DEFAULT_INSTRUMENTS` against `slots.cells()`.
- `core/data/src/main/kotlin/com/bbbjam/core/data/setlist/SetlistLineupChanges.kt` — `FIELDS` (7
  entries), `confirmedSlots` rebuilds `JamSlotEntity` list from the server's `slots` object, keyed
  by `FIELDS.toSet()` for exact-match validation.
- `core/data/src/main/kotlin/com/bbbjam/core/data/setlist/SetlistSlotClears.kt` — same `FIELDS` and
  `confirmedSlots` pattern, duplicated for the clear-slot mutation.
- `core/data/src/main/kotlin/com/bbbjam/core/data/setlist/SetlistSongAdds.kt` — correctly keeps
  `Lineup.DEFAULT_INSTRUMENTS` for a **new song's** confirmed insert (7 slots); must stay as is.
- `core/data/src/main/kotlin/com/bbbjam/core/data/setlist/SetlistAssignments.kt`,
  `core/data/src/main/kotlin/com/bbbjam/core/data/cache/SetlistAssignmentDao.kt` — already
  column-index-free; confirmed no change needed.
- `core/data/src/main/kotlin/com/bbbjam/core/data/cache/JamEntities.kt`,
  `core/data/src/main/kotlin/com/bbbjam/core/data/cache/SetlistSlotDao.kt` — `JamSlotEntity` schema
  confirmed to need no migration (see **Readiness**).
- `core/data/src/test/kotlin/com/bbbjam/core/data/jams/JamsMapperTest.kt`,
  `JamsRefreshOutcomeTest.kt`, `core/data/src/test/kotlin/com/bbbjam/core/data/cache/JamsDaoTest.kt`,
  `core/data/src/test/kotlin/com/bbbjam/core/data/Fixtures.kt` — every call site of
  `SlotsDto(...)` uses 7 positional arguments; all must be updated once the constructor grows (or,
  if named-arg defaults are used, most can stay unchanged — see **Technical Approach**).
- `core/data/src/test/kotlin/com/bbbjam/core/data/setlist/SetlistLineupChangesTest.kt`,
  `SetlistSlotClearsTest` (clear-slot test, same `slots(position)` helper pattern as
  `SetlistMoveRepositoryTest.kt`, `SetlistPublishRepositoryTest.kt`, `DefaultSetlistRepositoryTest.kt`,
  `core/data/src/test/kotlin/com/bbbjam/core/data/cache/SetlistDaoTest.kt`) — all build fixture
  `JamSlotEntity` lists from `Lineup.DEFAULT_INSTRUMENTS.mapIndexed`; these are **cache/mirror
  fixtures for a confirmed 7-slot default song**, not asserting a 7-column ceiling, so they do not
  need to change unless a specific test is widened to prove growth past the old default.
- `backend/apps-script/src/Jams.js` — `SLOT_FIELDS` (7 entries, `header`/`field`/`required: true`
  each), `buildSetlist` (reads every `SLOT_FIELDS` entry per row via `mapColumns`, which already
  treats each spec independently — no special-casing of "the 7").
- `backend/apps-script/src/Post.js` — `SLOT_COLUMNS_` (instrument → field-name array),
  `setSlotCount_` (`count > fields.length` is today's ceiling, must become a constant max of 3),
  `presentSlotFields_`/`planSlotCount_`/`writeSlotCells_` (already generic over whatever `fields`
  list they receive — confirmed no change needed beyond the ceiling and the field tables),
  `assignSlot_`/`clearSlot_` (`ordinal > SLOT_COLUMNS_[instrument].length` — automatically correct
  once `SLOT_COLUMNS_` lists 3 fields per instrument), `setlistSpecs_`/`createSetlistTab_`/
  `appendSetlistRow_` (build a brand-new tab's header and a new row's cells from whatever
  `setlistSpecs_()` returns — confirmed this is where the 11 "-" cells for a new song on an
  18-column tab must be written explicitly, because today's `appendSetlistRow_` only writes columns
  present in `values`, defaulting every other mapped column to `''`, not `'-'`).
- `backend/apps-script/src/Normalize.js` — `mapColumns` (headers matched by name, required-vs-not
  per spec; confirms the dual-layout read is free: make the 11 new headers `required: false`, keep
  the 7 original headers `required: true`, so an old tab still validates and a new tab's extra
  columns are read when present).
- `backend/apps-script/src/Code.js` — GET route unaffected except through `Jams.js`'s `SLOT_FIELDS`.
- `backend/apps-script/test/jams.test.js`, `test/helpers/jams-edge-input.js`,
  `backend/apps-script/test/post.test.js`, `test/setlist.test.js` — existing Node coverage patterns
  for `SLOT_HEADERS`, `buildSetlist`, `setSlotCount`, `checkSetlistWrite`; new tests must cover an
  18-header tab, a 7-header tab, and the boundary at 3.
- `docs/sheet-schema.md` — `Slot columns` table (7 fixed headers, `Instrument` mapping), `Otros`
  section, `Mapper rules`, `setSlotCount` paragraph (states "never above the default" — needs the
  amendment language).
- `docs/apps-script-api.md` — `Setlist row` table (`slots` object, exactly 7 keys today), `setSlotCount`
  section (states `count` 0 to 2/1 "within the default lineup" — needs the 0..3 amendment), POST
  actions list (unchanged: no new action name).
- `bb-blues-jam-bitacora.md` D-18 — states slots "only shrink"; needs a dated amendment recording
  the reversal, in the same style as the D-07 and D-17 later amendments already in this file (search
  "Enmienda del").
- `feature/next-jam/src/main/kotlin/com/bbbjam/feature/nextjam/LineupReservations.kt` — `canAdd =
  count < Lineup.defaultCount(instrument)`; must read the new `Lineup.maxCount(instrument)`.
- `feature/next-jam/src/main/kotlin/com/bbbjam/feature/nextjam/LineupControls.kt` — `target in
  0..com.bbbjam.core.model.Lineup.defaultCount(instrument)`; same change.
- `feature/next-jam/src/main/kotlin/com/bbbjam/feature/nextjam/LineupChangeOverlay.kt` — folds
  `withSlotCount` over Sending changes; unaffected (delegates its bound check to `Lineup`).
- `app/src/debug/java/com/bbbjam/debug/DemoUpcomingJam.kt` — `lineupOf` requires
  `cells.size == Lineup.DEFAULT_INSTRUMENTS.size`; confirmed unaffected (still builds 7-cell demo
  songs; growth is exercised live via the editor, not via a pre-grown fixture).
- `core/ui/src/main/kotlin/com/bbbjam/core/ui/strip/LineupChips.kt`,
  `core/ui/src/main/kotlin/com/bbbjam/core/ui/lineup/LineupPanelMapper.kt`,
  `core/ui/src/main/kotlin/com/bbbjam/core/ui/filter/InstrumentFilterMapper.kt` — all iterate
  `lineup.slots`/`openSlots` dynamically; confirmed no change needed.
- `backend/apps-script/README.md` — the "Updating the code later" procedure (paste, verify with a
  distinctive-line table, redeploy as **New version** on the existing deployment, check the
  `unknown_action` message's action list) is the pattern the user guide (this slice's deliverable)
  must follow, because no file content or action name changes in this slice (only the slot field
  tables inside `Jams.js`/`Post.js`), so the existing verify-the-paste table's distinctive lines
  still apply unchanged.
- `docs/risks-and-open-questions.md` — "Adjust-lineup risks" section already documents "JS resolves
  Sheet columns, Kotlin mirrors counts for display only; confirmed cache always comes from the
  server's seven-cell answer" — this sentence becomes stale once the answer can be an 18-cell
  object and must be corrected.

### Existing Patterns To Follow

- **A domain constant separates "default" from "ceiling".** `Lineup.defaultCount` already exists
  for the former; add `Lineup.maxCount` for the latter, both backed by explicit maps, so the
  distinction is a named, testable fact rather than an inline literal `3`.
- **The server validates with a field-count comparison it already has** (`fields.length` in
  `setSlotCount_`); only the field tables need to grow and the validation needs a named max
  constant instead of reusing the (now smaller) field-list length as the ceiling.
- **The client mirrors only what the server confirms, never guesses.** `SetlistLineupChanges` and
  `SetlistSlotClears` both exact-match the confirmed `slots` object's key set before trusting it
  (`it.keys == FIELDS.toSet()`); widen `FIELDS` in both to the full 18 names and keep the exact-match
  discipline — do not loosen it to a subset check.
- **Optional columns are already how the schema tolerates absence** (`Otros`, `songsterr_id`): the
  11 new slot headers become `required: false` in `backend/apps-script/src/Jams.js`'s `SLOT_FIELDS`,
  exactly like `EXTRA_FIELD`, so `mapColumns` accepts a header-matched old tab with no error.
- **`kotlinx.serialization`'s `ignoreUnknownKeys = true` is already global**
  (`AppsScriptEnvelope.json`, `core/data/src/main/kotlin/com/bbbjam/core/data/remote/Envelope.kt`),
  and Kotlin `@Serializable` data classes already default missing-key fields to their declared
  default value. Adding 11 new `val ...: String? = null` fields to `SlotsDto` means a GET from an
  un-migrated real Sheet (which will never emit the new keys because `SLOT_FIELDS` only emits keys
  for headers that exist) deserializes with those 11 fields `null`, matching `-`/absent semantics —
  see **Technical Approach** for how the mapper tells "column absent from this tab" apart from
  "column present and empty" for the new columns specifically.
- **A new admin test probe demonstrates a failing case before trusting a new rule** (the project's
  consistent pattern across every accepted slice): the implementer must show `setSlotCount` reject
  count 4 and the model reject a 4th slot before claiming the ceiling works, per `AGENTS.md`'s
  Konsist precedent generalized to this slice's own new boundary.
- **Dated amendments, not silent rewrites**, for `DESIGN.md` and `bb-blues-jam-bitacora.md`, as
  `admin-row-icon-actions` did twice in one slice (6.20) and `song-detail-replaces-row-expansion`
  did for D-07 (6.19).

### Current Gaps

- No existing mechanism distinguishes "this instrument's column is absent from the tab" (old
  7-column layout; the field truly does not exist for `setSlotCount_`'s purposes) from "this
  instrument's 2nd/3rd column exists and is `-`" (18-column layout, not grown yet). The Apps Script
  side already handles this correctly via `mapColumns`'s per-header matching (a column simply is not
  in `columns` when its header is absent) — the gap is only in the **client-side `Lineup` model**,
  which has no concept of "this instrument's slot N might not be representable on this specific jam's
  tab layout." This spec resolves it by making `Lineup`/`withSlotCount` purely about the domain
  ceiling (3) and leaving tab-layout capability to the server (`setSlotCount_` already fails cleanly
  with `invalid_count` if asked to go beyond what a specific tab's columns can represent — **this is
  new**: today `fields.length` for an instrument on an old 7-column tab is naturally 2 or 1 because
  `SLOT_COLUMNS_` is a fixed JS object, not derived from the sheet; after this slice `SLOT_COLUMNS_`
  will always list 3 fields per instrument regardless of what the sheet's header row actually has,
  so growing an instrument on an **old, un-migrated tab** would try to write a header that is not
  there). This is the one real design decision this spec must make explicitly — see **Technical
  Approach**, "Growing on an un-migrated tab".
- `docs/sheet-seed/2026-07-25.csv` is a 7-column seed fixture; it is not touched by this slice (seed
  data is not the live Sheet), but the user's manual migration guide must tell them to widen the
  *header row* of each real jam tab, not just rely on seed files.
- **A real correctness bug the server fix must close, found while tracing the read path.**
  `readSlotCells_` (`Post.js`) does `cells[spec.field] = textCell(display[columns[spec.field]])` for
  every entry of `SLOT_FIELDS`. If the 11 new headers are made `required: false` and a real tab
  still has only the original 7 columns, `columns['guitar3']` (etc.) is `undefined`, so
  `display[undefined]` is `undefined`, and `textCell(undefined)` returns `null` — the same value as
  an **open** slot. `presentSlotFields_` treats any non-`'-'` cell as present, so it would treat a
  column that does not exist on this tab as an open 3rd guitar slot. `setSlotCount_` would then let
  the admin "grow" guitar to 3 on an old tab, and `writeSlotCells_` would call
  `sheet.getRange(row, columns['guitar3'] + 1)` with `columns['guitar3'] === undefined`, i.e.
  `getRange(row, NaN)` — an uncaught runtime error (`internal_error`), not a clean, documented
  rejection. **This must be fixed explicitly, not inherited by accident**: the fix is in
  **Technical Approach**, "Growing on an un-migrated tab".

## Technical Approach

### 1. `:core:model` — separate "default" from "maximum"

In `Lineup.kt`, keep `DEFAULT_INSTRUMENTS` (7 entries, unchanged: it is the Sheet's canonical
column order for a *new song's starting lineup* and the order `SetlistSongAdds` still inserts) and
`DEFAULT_COUNTS`/`defaultCount` (unchanged: 2 for guitar, 1 for the rest). Add:

```kotlin
private val MAX_COUNTS: Map<Instrument, Int> = Instrument.entries.associateWith { MAX_SLOTS_PER_INSTRUMENT }
private const val MAX_SLOTS_PER_INSTRUMENT = 3

/** The most slots [instrument] may ever hold in one song's lineup (D-18 amendment, 12 October 2026). */
fun maxCount(instrument: Instrument): Int = MAX_COUNTS[instrument] ?: MAX_SLOTS_PER_INSTRUMENT
```

Change the `init` guard and `withSlotCount`'s `require` from `defaultCount(instrument)` to
`maxCount(instrument)` — this is the only functional change to `Lineup`'s existing growth logic
(`withSlotCount`'s add branch already works unmodified: it appends new open slots after the last
matching instrument slot, or at the canonical position when none exists, exactly the behavior this
feature needs).

`LineupTest.kt` changes: `slot counts outside the default range are rejected` and `more slots of an
instrument than the default are rejected` must assert the new ceiling (3) instead of the old
default, with a fresh test proving a 4th slot of any instrument is still rejected
(`more slots of an instrument than the max are rejected`), and a new test proving 3 is now valid
for every instrument, including non-guitar ones (`growing any instrument to its maximum is valid`,
mirroring `admin-adjust-lineup`'s original `increasing restores after the last matching slot or
canonical position` but asserting 3, not 2, for e.g. `VOCALS`). Keep `default and current counts
match the lineup` asserting `defaultCount` unchanged (2/1/1/1/1/1) — do not conflate the two
constants in any test.

### 2. `backend/apps-script/src/Jams.js` — 18 slot fields, 11 optional, two accepted spellings

Replace `SLOT_FIELDS` with entries in Sheet column order (the order the client's
`Lineup.DEFAULT_INSTRUMENTS`-derived canonical ordering must also use — see step 4). The user's
decision names the new columns `Guitarra 1-3`, `Bajo 1-3`, `Batería 1-3`, `Voz 1-3`, `Armónica 1-3`,
`Teclados 1-3` — i.e. when the user adds the columns by hand, the single existing header of each
non-guitar instrument (`Bajo`, `Batería`, `Voz`, `Armónica`, `Teclados`) is expected to become
`Bajo 1`, `Batería 1`, `Voz 1`, `Armónica 1`, `Teclados 1`. But **every real jam tab today still has
the un-numbered header**, and this slice must keep reading those tabs with no error (Scenario 4), so
`SLOT_FIELDS` must accept **either spelling** for those five instruments' first column: one
`required: true` spec with the old header (so an old, un-migrated tab still validates) and one
`required: false` spec with the new numbered header, both mapped to the **same field name**
(`bass1`, `drums1`, `vocals1`, `harmonica1`, `keyboards1`). Guitar is unaffected: `Guitarra 1` and
`Guitarra 2` already carry their final spelling and stay `required: true` unchanged; only
`Guitarra 3` is new and optional.

| Header(s) accepted | Field | Required |
|---|---|---|
| `Guitarra 1` | `guitar1` | yes (unchanged) |
| `Guitarra 2` | `guitar2` | yes (unchanged) |
| `Guitarra 3` | `guitar3` | no (new) |
| `Bajo` **or** `Bajo 1` | `bass1` | `Bajo` yes, `Bajo 1` no (one spec each; see below) |
| `Bajo 2` | `bass2` | no (new) |
| `Bajo 3` | `bass3` | no (new) |
| `Batería` **or** `Batería 1` | `drums1` | same dual-spec pattern |
| `Batería 2` | `drums2` | no (new) |
| `Batería 3` | `drums3` | no (new) |
| `Voz` **or** `Voz 1` | `vocals1` | same dual-spec pattern |
| `Voz 2` | `vocals2` | no (new) |
| `Voz 3` | `vocals3` | no (new) |
| `Armónica` **or** `Armónica 1` | `harmonica1` | same dual-spec pattern |
| `Armónica 2` | `harmonica2` | no (new) |
| `Armónica 3` | `harmonica3` | no (new) |
| `Teclados` **or** `Teclados 1` | `keyboards1` | same dual-spec pattern |
| `Teclados 2` | `keyboards2` | no (new) |
| `Teclados 3` | `keyboards3` | no (new) |

18 header strings total, mapping to 18 field names (`guitar1`, `guitar2`, `guitar3`, `bass1`,
`bass2`, `bass3`, `drums1`, `drums2`, `drums3`, `vocals1`, `vocals2`, `vocals3`, `harmonica1`,
`harmonica2`, `harmonica3`, `keyboards1`, `keyboards2`, `keyboards3`), 23 header **specs** in
`SLOT_FIELDS` (18 plus the 5 old-spelling duplicates for `Bajo`/`Batería`/`Voz`/`Armónica`/
`Teclados`). `mapColumns` already handles two specs mapping to the same field correctly for its
other purpose (`duplicate_header` when a tab has *both* spellings of one field — the existing,
correct behavior for any header mapped twice; a tab should never have both `Bajo` and `Bajo 1`,
and if it somehow does, that is exactly the ambiguity `duplicate_header` exists to catch). Document
this dual-spelling rule explicitly in `docs/sheet-schema.md` rather than leaving it implicit in
code. `buildSetlist`'s `SLOT_FIELDS.forEach` loop needs no change; it already treats every spec
independently, and `mapColumns`'s `byHeader[spec.header] = spec` lookup already tolerates two specs
with the same `field` as long as their `header` strings differ.

`SlotsDto` (`core/data/src/main/kotlin/com/bbbjam/core/data/remote/JamDto.kt`) grows from 7 to 18
`val x: String? = null` fields, one per field name above, in the same column order; `cells()` returns
all 18 in that order. Because every field defaults to `null` and
`AppsScriptEnvelope.json`'s `ignoreUnknownKeys = true` is already set, a 7-key response (from an
unmigrated tab, or from the current deployed backend before the user redeploys) still decodes
correctly, with the 11 new fields `null`.

### 3. `backend/apps-script/src/Post.js` — the 3-per-instrument ceiling and the un-migrated-tab fix

`SLOT_COLUMNS_` grows to list 3 field names per instrument:

```js
var SLOT_COLUMNS_ = {
  guitar: ['guitar1', 'guitar2', 'guitar3'], bass: ['bass1', 'bass2', 'bass3'],
  drums: ['drums1', 'drums2', 'drums3'], vocals: ['vocals1', 'vocals2', 'vocals3'],
  harmonica: ['harmonica1', 'harmonica2', 'harmonica3'], keyboards: ['keyboards1', 'keyboards2', 'keyboards3'],
};
var MAX_SLOTS_PER_INSTRUMENT_ = 3;
```

`setSlotCount_`'s bound check changes from `count > fields.length` to `count >
MAX_SLOTS_PER_INSTRUMENT_` (the domain ceiling, independent of how many of those fields exist on
*this* tab — that check happens next, as part of fixing the gap below). `assignSlot_`/`clearSlot_`'s
`ordinal > SLOT_COLUMNS_[instrument].length` bound is now automatically 3 for every instrument,
which is correct: a server-confirmed assignment or clear can only ever target a column the lineup
actually contains (checked by `presentSlotFields_` before that bound is reached), so this needs no
further change beyond the wider `SLOT_COLUMNS_` table.

**Growing on an un-migrated tab (the gap found in Repository Research).** `readSlotCells_` must stop
treating "this field's header is absent from this tab" the same as "this field's cell is empty"
(open). Fix: add a column-presence filter before building `cells`, so a field with no mapped column
on this specific tab is treated as `'-'` (not in this song's lineup) rather than `null` (open):

```js
function readSlotCells_(sheet, row, columns) {
  const display = sheet.getRange(row, 1, 1, sheet.getLastColumn()).getDisplayValues()[0];
  const cells = {};
  SLOT_FIELDS.forEach(function (spec) {
    cells[spec.field] = columns[spec.field] === undefined ? NOT_IN_LINEUP_ : textCell(display[columns[spec.field]]);
  });
  return cells;
}
```

(`NOT_IN_LINEUP_ = '-'`, a new constant next to `PLAIN_TEXT_FORMAT`.) With this fix,
`presentSlotFields_` correctly reports an old 7-column tab's `guitar3` as not present, so
`planSlotCount_` for guitar on that tab sees `present.length === 2` and the existing logic
(`count > present.length` → open the next `-` column) tries to open `guitar3` — which still is not
a real column on this tab. **This must fail cleanly, not with a Sheets runtime error.** Add one more
guard in `setSlotCount_`, right after resolving `fields`, that rejects growth the tab's columns
cannot represent, with a distinct, documented code:

```js
const missingColumn = fields.find(function (field) { return columns[field] === undefined; });
if (count > presentSlotFields_(readSlotCells_(sheet, row, columns), fields).length && missingColumn !== undefined &&
    columns[fields[count - 1]] === undefined) {
  throw new ContractError('instrument_not_expandable', 'This jam tab has no column for that slot yet');
}
```

(Exact placement/refactor is the implementer's to tighten — the required behavior is: asking to grow
past what this tab's own header row can represent must throw a new `instrument_not_expandable`
before any write, never a raw exception.) Document `instrument_not_expandable` in
`docs/apps-script-api.md`'s `setSlotCount` section and in the client's `WriteOutcome.Rejected`
mapping (no new `WriteOutcome` variant needed — `Rejected(code)` already carries any server code).
This means: **on a real jam tab the user has not yet widened, the admin can still use `+` up to the
old default (2 guitar, 1 for the rest) exactly as today, and a `+` past that point fails with a
clear, explained rejection** (new copy in `NextJamCopy`, e.g. a `lineupFailed` variant or reusing the
existing failure-card path with the new code mapped to Spanish text) **instead of a silent success
or a raw error.** This is the one behavior a human cannot discover from the ticket alone and must be
confirmed explicit before implementation.

`createSetlistTab_` (builds a **brand-new** tab's header row from `setlistSpecs_()`) needs no
change: once `SLOT_FIELDS` has 18 entries, any newly created tab (including the disposable
`checkSetlistWrite`/`checkSetlistMove`/`checkPublish` marker tabs) gets all 18 headers, so a brand
new real tab the admin creates by adding a `Jams` row before adding columns by hand would already
be 18-wide — harmless, and actually convenient (no migration needed for jams added after this
slice's deploy).

**Scenario 5's `-` cells.** `appendSetlistRow_` writes only columns explicitly present in its
`values` object, and defaults every other *mapped* column to `''` (empty, open) — today that is
correct, because all 7 columns are always meant to start open. For an 18-column tab, the 11 columns
beyond the instrument's old default must start as `-`, not `''`. Fix: in `appendSetlistRow_`,
default a slot field that is not in `values` to `-` when it is one of the "beyond-default" fields
(`guitar3`, `bass2`, `bass3`, `drums2`, `drums3`, `vocals2`, `vocals3`, `harmonica2`, `harmonica3`,
`keyboards2`, `keyboards3`) and to `''` for every other field (unchanged: `posicion` through the
instrument's first N default columns, and `Otros`). The cleanest implementation is a lookup table
(e.g. a `DEFAULT_CELL_` map from field name to `''` or `-`, built once from `SLOT_COLUMNS_` and
`Lineup`'s `defaultCount`-equivalent on the JS side, or simply listed explicitly) rather than an
inline conditional, so the "11 cells start `-`" rule reads as data, not a scattered `if`.

### 4. `:core:data` client mirrors — widen `FIELDS`, keep exact-match discipline

`SetlistMapper.lineupSlots` zips `Lineup.DEFAULT_INSTRUMENTS` against `slots.cells()` — this must
change to zip against the **18-entry canonical column order** instead (one `Instrument` repeated 3
times per the table above, in the same order as `SlotsDto`'s 18 fields / the backend's
`SLOT_FIELDS`). Introduce `Lineup.MAX_INSTRUMENTS` (or similarly named 18-entry
`List<Instrument>` companion constant, analogous to `DEFAULT_INSTRUMENTS` but for the full column
layout) so this canonical order is a single named source both `SetlistMapper` and the two
`confirmedSlots` functions share, rather than three independent 18-item literals.

`SetlistLineupChanges.confirmedSlots` and `SetlistSlotClears.confirmedSlots`: widen their `FIELDS`
constant to the 18 names in the same order, keep the exact `it.keys == FIELDS.toSet()` check
(**do not loosen it to a subset check** — a malformed or truncated server answer must still be
rejected as `Unavailable`, exactly as today), and map each field to `Lineup.MAX_INSTRUMENTS[column]`
instead of `Lineup.DEFAULT_INSTRUMENTS[column]`.

`SetlistSongAdds.storeConfirmedSong` keeps `Lineup.DEFAULT_INSTRUMENTS` unchanged (a new song's
confirmed insert is always the 7-slot default, regardless of how many columns the tab has — the
11 extra columns for that song are server-written `-` per step 3's `appendSetlistRow_` fix, and the
app never caches a `-` cell as a `JamSlotEntity` row at all, consistent with how `-` already means
"no row" in `SetlistMapper.lineupSlots`).

### 5. `feature/next-jam` — raise the editor's ceiling

`LineupReservations.lineupLine`: `canAdd = count < Lineup.defaultCount(instrument)` becomes `canAdd
= count < Lineup.maxCount(instrument)`. `LineupControls.onLineupCount`: `target in
0..com.bbbjam.core.model.Lineup.defaultCount(instrument)` becomes `0..Lineup.maxCount(instrument)`.
No other UI file changes: the editor's line rendering, `lineupCount` copy ("1 cupo", "2 cupos", "3
cupos") and the strip/filter/panel are all already generic over the lineup's actual slot count.

### 6. Dual-layout correctness is proven by tests, not by inspection

Node tests (`backend/apps-script/test/`) must cover, at minimum: (a) `buildSetlist` on an unchanged
7-header tab still returns the same rows as today (no regression — reuse the existing seed/edge
fixtures unmodified as the "old layout" case); (b) `buildSetlist` on a new 18-header tab reads all
18 slot cells correctly, `-` and filled and open; (c) `setSlotCount` growing an instrument to 3 on
an 18-column tab, writing only the one changed cell; (d) `setSlotCount` requesting count 4 on any
instrument rejected with `invalid_count` before any write, on either layout; (e) `setSlotCount`
requesting growth past what an **old 7-column tab's own headers support** rejected with
`instrument_not_expandable` before any write (the fix from step 3); (f) `appendSetlistRow_` on an
18-column tab writes the 11 beyond-default cells as literal `-` and the 7 default cells as open
(empty); (g) the duplicate-header rule for the `Bajo`/`Bajo 1` (etc.) dual-spelling case, both
directions. JVM tests (`:core:model`, `:core:data`) cover `Lineup.maxCount`, the new ceiling in
`withSlotCount`/`init`, `SetlistMapper` against both an 18-cell and a 7-cell `SlotsDto`, and
`confirmedSlots` in both mutation mirrors against an 18-key confirmed answer.

## Expected File Changes

- `core/model/src/main/kotlin/com/bbbjam/core/model/Lineup.kt` — modify; add `maxCount`,
  `MAX_INSTRUMENTS` (18-entry canonical order), change the `init` guard and `withSlotCount`'s bound
  from `defaultCount` to `maxCount`.
- `core/model/src/test/kotlin/com/bbbjam/core/model/LineupTest.kt` — modify; update the two
  existing boundary tests, add a 4th-slot-rejected test and a grow-to-3-for-any-instrument test.
- `backend/apps-script/src/Jams.js` — modify; `SLOT_FIELDS` grows to 18 entries (plus the two
  dual-spelling specs for the five single-slot instruments' first column), exported unchanged
  otherwise.
- `backend/apps-script/src/Post.js` — modify; `SLOT_COLUMNS_` grows to 3 fields per instrument,
  `MAX_SLOTS_PER_INSTRUMENT_` constant, `setSlotCount_`'s bound check, `readSlotCells_`'s
  column-presence fix, the new `instrument_not_expandable` guard, `appendSetlistRow_`'s
  beyond-default-cells-start-`-` fix.
- `core/data/src/main/kotlin/com/bbbjam/core/data/remote/JamDto.kt` — modify; `SlotsDto` grows to
  18 nullable fields with `= null` defaults, `cells()` returns all 18.
- `core/data/src/main/kotlin/com/bbbjam/core/data/jams/SetlistMapper.kt` — modify; `lineupSlots`
  zips against the 18-entry canonical order.
- `core/data/src/main/kotlin/com/bbbjam/core/data/setlist/SetlistLineupChanges.kt` — modify; widen
  `FIELDS` to 18, map columns through the 18-entry canonical order.
- `core/data/src/main/kotlin/com/bbbjam/core/data/setlist/SetlistSlotClears.kt` — modify; same as
  above.
- `core/data/src/test/kotlin/com/bbbjam/core/data/jams/JamsMapperTest.kt`,
  `JamsRefreshOutcomeTest.kt`, `core/data/src/test/kotlin/com/bbbjam/core/data/cache/JamsDaoTest.kt`,
  `core/data/src/test/kotlin/com/bbbjam/core/data/Fixtures.kt` — modify; update every `SlotsDto(...)`
  call site for the wider constructor (named arguments recommended to avoid an 18-positional-arg
  call), add at least one test exercising an 18-cell `SlotsDto`.
- `core/data/src/test/kotlin/com/bbbjam/core/data/setlist/SetlistLineupChangesTest.kt`,
  the clear-slot test file — modify/add; a test proving growth to 3 for a non-guitar instrument and
  a confirmed 18-key answer mirrored correctly.
- `feature/next-jam/src/main/kotlin/com/bbbjam/feature/nextjam/LineupReservations.kt` — modify;
  `canAdd` reads `Lineup.maxCount`.
- `feature/next-jam/src/main/kotlin/com/bbbjam/feature/nextjam/LineupControls.kt` — modify; the
  `target in` range reads `Lineup.maxCount`.
- `feature/next-jam/src/main/kotlin/com/bbbjam/feature/nextjam/NextJamCopy.kt` — modify; Spanish
  copy for the new `instrument_not_expandable` failure (mapped alongside the existing `SLOT_FILLED`,
  `CLEAR_STALE` etc. failure-message constants).
- `backend/apps-script/test/jams.test.js`, `test/setlist.test.js`, `test/post.test.js` — modify/add;
  cover the six cases in step 6 above.
- `backend/apps-script/README.md` — modify; add a dated subsection for this slice's "updating the
  code later" steps (the file content changed; the deploy procedure itself is unchanged).
- `docs/sheet-schema.md` — modify; `Slot columns` table grows to 18 rows with the dual-spelling
  note, `setSlotCount` paragraph gets the 0..3 amendment and the `instrument_not_expandable` code,
  Mapper rules note the optional new headers.
- `docs/apps-script-api.md` — modify; `Setlist row`/`slots` documentation grows to 18 keys,
  `setSlotCount` section's bound and error table gets `instrument_not_expandable`, `Client setlist
  mutations` section's `FIELDS`/`confirmedSlots` description updated.
- `bb-blues-jam-bitacora.md` — modify; dated D-18 amendment (12 October 2026) recording the
  reversal from "slots only shrink" to "slots can also grow, up to 3, via fixed columns", in the
  style of the existing D-07/D-17 "Enmienda del" amendments.
- `docs/risks-and-open-questions.md` — modify; correct the "Adjust-lineup risks" sentence that says
  the confirmed cache "always comes from the server's seven-cell answer" (now up to 18), and add a
  risk entry for the un-migrated-tab growth rejection (new, user-facing failure mode).
- `DESIGN.md` — modify; the `Cambiar formación` section's "default instrument" row description
  updated to say the ceiling is 3 for every instrument, not "default" per instrument; no new visual
  component.
- `docs/migrations/lineup-grow-any-instrument-sheet-migration.md` — **create**; the user-facing,
  step-by-step guide (exact 18 column headers and their position, which Apps Script files to paste,
  how to verify the paste and the deploy, and how to run the read-only live check). See
  **Verification Plan** for its required content.
- `feature_list.json` — modify (orchestrator, not this spec); status and evidence as the slice
  progresses.

No Room schema version bump (`JamSlotEntity.columnIndex` already unconstrained — see **Readiness**).
No new Gradle module, no new Koin binding, no new route, no new repository function, no new
`WriteOutcome` variant, no new screen or navigation route.

## Visual Design Impact

- UI involved: yes, but narrowly.
- Design source: `DESIGN.md`, "Adjusting a lineup" subsection (existing component, `FORMACIÓN`
  inline editor).
- Screens or states affected: the song detail's `Cambiar formación` editor (now shows up to 3 rows
  of `+` enabled per instrument instead of 2 for guitar / 1 for the rest); the strip, filter and
  lineup panel when a song actually has more than the old default of an instrument (new content,
  same existing component — `LineupChips`, `InstrumentFilterBar`, `LineupPanel` already render
  any count); a new failure-card message for `instrument_not_expandable` (reuses the existing
  dismissible failure-card component and typography, new copy only).
- New design artifact required: no. No new token, no new component, no new color. The existing
  48dp `+`/`−` squares, `surfaceRaised`/`border`/`text`/`textMuted` treatment and disabled-state
  rules (`admin-adjust-lineup`'s design, carried by `admin-row-icon-actions`'s icon-button pass
  only where it touched row-level actions, not this editor) apply unchanged at a higher count.

## Durable Documentation Impact

- `ARCHITECTURE.md`: not needed — this repository has no such file; architecture lives in
  `.claude/skills/architecture/SKILL.md`, whose "As built by admin-adjust-lineup" paragraph needs a
  short addendum (or a new "As built by lineup-grow-any-instrument" paragraph, following the
  existing convention) recording the `maxCount`/`defaultCount` split and the 18-column layout. The
  implementer should add it in the same style as the existing "As built by …" paragraphs; this is
  housekeeping the implementer owns, not a new file.
- `CONSTRAINTS.md`: not needed — this repository has no such file; durable MUST/MUST NOT rules live
  in `AGENTS.md`'s "Non-Negotiable Rules" and the bitácora's D-01..D-20. D-18 is amended (see above),
  not replaced; AGENTS.md's own D-18 summary line is unaffected (it does not restate D-18's detail).
- `AGENTS.md`: not needed — no change to the agent workflow, startup path, or repo-wide operating
  rules.
- `docs/sheet-schema.md`, `docs/apps-script-api.md`, `bb-blues-jam-bitacora.md`,
  `docs/risks-and-open-questions.md`, `DESIGN.md` — update, as detailed in **Expected File Changes**.
- `docs/migrations/lineup-grow-any-instrument-sheet-migration.md` — create (the user guide).
- `backend/apps-script/README.md` — update (deploy procedure addendum).

## Implementation Plan

1. `:core:model`: add `Lineup.maxCount`/`MAX_INSTRUMENTS`, widen the `init`/`withSlotCount` bound,
   update `LineupTest.kt` (including the new failing-case-first demonstration: show a 4th slot
   rejected before changing the bound, then show the new bound accepts 3 and still rejects 4).
2. `backend/apps-script`: widen `SLOT_FIELDS`/`SLOT_COLUMNS_`, fix `readSlotCells_`'s column-absence
   handling, add the `instrument_not_expandable` guard in `setSlotCount_`, fix
   `appendSetlistRow_`'s beyond-default-cells-start-`-` behavior, widen `createSetlistTab_`'s callers
   implicitly (no code change needed there). Add/extend Node tests for every case in step 6 of
   **Technical Approach**, each demonstrated failing before the fix and passing after, per
   `node --test backend/apps-script/test/*.test.js`.
3. `:core:data`: widen `SlotsDto`, `SetlistMapper.lineupSlots`, both `confirmedSlots` functions;
   update every existing `SlotsDto(...)` call site; add tests for an 18-cell response and an
   18-key confirmed mutation answer.
4. `feature/next-jam`: widen the editor's `canAdd`/count-range checks to `maxCount`; add the
   `instrument_not_expandable` Spanish copy and wire it into the existing failure-message mapping
   (reuse the existing failure-card path — no new UI state).
5. Run the full gate (`CI=true ./init.sh`) and the Node suite; fix anything the widened types or
   bounds break in unrelated tests that build a `Lineup`/`SlotsDto` fixture.
6. Write `docs/migrations/lineup-grow-any-instrument-sheet-migration.md` (see **Verification
   Plan**), and update `docs/sheet-schema.md`, `docs/apps-script-api.md`,
   `bb-blues-jam-bitacora.md`, `docs/risks-and-open-questions.md`, `DESIGN.md`,
   `backend/apps-script/README.md` and the architecture skill's "As built" log.
7. Device check on the Pixel 5 with the debug demo jam and debug admin flag: grow a non-guitar
   instrument to 3 via the editor against the demo jam (which is pure in-memory data, not the real
   Sheet — growth there only exercises the UI/model path, not a live write); confirm the strip,
   filter and panel show 3 slots; confirm `+` disables at 3 and `−` still refuses a filled slot at
   the new ceiling.
8. Hand the finished migration guide to the user. **Do not** proceed to a live Apps Script check
   until the user confirms both (a) the 18 columns were added to the real Sheet's existing jam
   tab(s) and (b) the updated `Post.js`/`Jams.js`/etc. were pasted and redeployed as a **new
   version** of the existing deployment (never a new deployment/new URL). Then perform the
   read-only live check described in **Verification Plan**.

## Implementation Tasks

- [ ] `Lineup.maxCount`/`MAX_INSTRUMENTS` added; `init`/`withSlotCount` bound changed; `LineupTest`
      updated with a before/after failing-case demonstration.
- [ ] `Jams.js` `SLOT_FIELDS` widened to 18 fields (with dual-spelling specs for the five
      single-slot instruments' first column: old and new header both accepted);
      Node test proving an unchanged 7-header tab still reads identically to today.
- [ ] `Post.js` `SLOT_COLUMNS_` widened to 3 per instrument; `setSlotCount_` bound uses
      `MAX_SLOTS_PER_INSTRUMENT_`; Node test proving count 4 is rejected for every instrument.
- [ ] `readSlotCells_` column-absence fix; Node test proving an old 7-column tab's "phantom" 3rd/2nd
      slot cells report as not-present, not as open.
- [ ] `instrument_not_expandable` guard added before any write in `setSlotCount_`; Node test proving
      growth past an old tab's real columns is rejected before any write (no partial write, no raw
      exception).
- [ ] `appendSetlistRow_` writes `-` to the 11 beyond-default cells on an 18-column tab; Node test
      proving a new song's row on such a tab has exactly 7 open/default cells and 11 `-` cells.
- [ ] `SlotsDto` widened to 18 fields with defaults; decode test for both a 7-key and an 18-key
      payload.
- [ ] `SetlistMapper.lineupSlots` and both `confirmedSlots` functions widened and using the shared
      18-entry canonical order; tests for growth confirmed via an 18-key answer.
- [ ] `LineupReservations`/`LineupControls` read `Lineup.maxCount`; presenter test proving `+` stays
      enabled past the old default and disables at 3, for a non-guitar instrument.
- [ ] `instrument_not_expandable` Spanish copy added and mapped through the existing failure-card
      path; presenter test proving the message renders for that code.
- [ ] `docs/migrations/lineup-grow-any-instrument-sheet-migration.md` written (exact columns,
      positions, files to paste, paste verification, deploy-as-new-version steps, read-only live
      check steps).
- [ ] `docs/sheet-schema.md`, `docs/apps-script-api.md`, `bb-blues-jam-bitacora.md` (dated D-18
      amendment), `docs/risks-and-open-questions.md`, `DESIGN.md`, `backend/apps-script/README.md`,
      and the architecture skill's "As built" log all updated.
- [ ] Full gate green (`CI=true ./init.sh`: konsist/detekt/ktlint wired) and
      `node --test backend/apps-script/test/*.test.js` green.
- [ ] Pixel 5 device check with `bluesjam.demoUpcomingJam=true` and the debug admin flag: grow and
      shrink a non-guitar instrument through 3, screenshots of the editor, strip, filter and panel
      at the new count.
- [ ] Migration guide handed to the user; user confirms Sheet columns added and backend redeployed;
      read-only live check performed and recorded (no write against the real Sheet beyond what the
      user's own migration and the self-cleaning `checkSetlistWrite`/`checkSetlistMove`/`checkPublish`
      deploy checks perform on their own disposable marker tabs).

## Verification Plan

- `./gradlew check` / `CI=true ./init.sh` — exits 0 with `konsist`, `detekt` and `ktlint` all
  `wired`; new and updated JVM tests pass (`:core:model` `LineupTest`, `:core:data`
  `SetlistMapperTest`/equivalent, `SetlistLineupChangesTest`, the clear-slot test,
  `JamsMapperTest`/`JamsRefreshOutcomeTest`/`JamsDaoTest`/`Fixtures` updates, `:feature:next-jam`
  presenter tests for the raised ceiling and the new failure copy).
- `node --test backend/apps-script/test/*.test.js` — passes, including every new case from step 6
  of **Technical Approach**; each new rule demonstrated failing before its fix lands, per this
  project's established pattern (a rule that cannot fail proves nothing).
- `git diff --check` — clean (no trailing whitespace / conflict markers introduced).
- Pixel 5 device check (debug demo jam `bluesjam.demoUpcomingJam=true`, debug admin
  `bluesjam.debugAdmin=true`, **never** against the real Sheet): grow a non-guitar instrument (e.g.
  `Voz`) from 1 to 3 through the editor on a demo song, screenshot the editor showing `+` enabled at
  1 and 2, disabled at 3; screenshot the strip, the instrument filter count and the song detail's
  lineup panel showing 3 vocal slots; shrink back and confirm a filled slot still blocks `−` at the
  new ceiling the same way it does at the old default. This is a UI/model check only — the demo jam
  is pure in-memory data with no Apps Script write, so it does **not** exercise
  `instrument_not_expandable` or the real Sheet's dual-spelling headers.
- Apps Script backend: no persistent E2E harness exists for this repository (D-13's registry and
  the Node suite are the closest analogues); the Node suite above is this feature's equivalent of
  focused flow coverage for the backend contract and is required, not optional.
- **The read-only live check, after the user's own migration and deploy** (not run by this slice's
  implementer without the user's explicit go-ahead): `readJams` (or the GET `jams` route) against
  the real Sheet, confirming the widened jam tab's setlist still maps correctly (18-key `slots`
  object per song, `-` for any ungrown column, names and open slots otherwise unchanged from before
  the migration) and that no real jam's data changed. This mirrors the project's existing L1-L5
  pattern (`admin-adjust-lineup`, `admin-publish-setlist`, etc.): read-only probes, codes and counts
  only, no musician name or passphrase in any log, no write beyond what a disposable, self-cleaning
  deploy check performs on its own temporary tab.
- `./init.sh` continues to run only the non-blocking standard gate; it starts no long-running
  process and is not where the live Sheet check happens.

## Evidence To Capture

- Gate output (`CI=true ./init.sh`) before and after, with konsist/detekt/ktlint wired and the full
  JVM test count delta.
- `node --test backend/apps-script/test/*.test.js` output, pass count before and after, naming each
  new test from step 6.
- At least one demonstrated failing case per new rule (the `Lineup` 4th-slot rejection, the
  `instrument_not_expandable` guard, the `appendSetlistRow_` `-`-cells fix, the dual-spelling
  header rule), shown failing before its fix and passing after, restored/reverted cleanly as this
  project's prior slices document.
- Pixel 5 screenshots: editor at count 1/2/3 for a non-guitar instrument, strip/filter/panel at
  count 3, the blocked-`−`-on-filled-slot message at the new ceiling.
- The finished migration guide's path and a confirmation that it was handed to the user.
- Once the user has migrated the Sheet and redeployed: the read-only live check's codes, counts and
  timings (no musician name, no passphrase, no URL), and confirmation that no real jam's data
  changed (e.g. an aggregate hash or song-count comparison before/after, as prior live-check
  evidence in this repository already records).

## Validator Checklist

- [ ] Implementation stays within this feature's scope (no 4th slot, no `Otros` growth, no new
      module/route/mutation, no Sheet migration or deploy performed by the implementer without the
      user's go-ahead).
- [ ] All six acceptance scenarios pass, including the dual-layout read scenario (old 7-column tab)
      and the un-migrated-tab growth rejection.
- [ ] `Lineup.maxCount` and `defaultCount` are both present and distinct; no code conflates them.
- [ ] The server never writes past what a specific tab's own header row supports; growth past that
      point is a clean, documented rejection (`instrument_not_expandable`), not a raw exception or a
      silent no-op.
- [ ] A new song's row on an 18-column tab has exactly 7 default-open/filled cells and 11 literal
      `-` cells (never blank) in the slot columns beyond each instrument's old default.
- [ ] `SlotsDto`/`confirmedSlots` changes keep the exact-key-set validation discipline (no loosened
      subset check).
- [ ] Verification evidence is present for every new rule, each shown failing before its fix.
- [ ] `docs/sheet-schema.md`, `docs/apps-script-api.md`, `bb-blues-jam-bitacora.md` (dated D-18
      amendment), `docs/risks-and-open-questions.md`, `DESIGN.md`, `backend/apps-script/README.md`,
      and the architecture skill are all updated to match the implemented behavior, not the plan.
- [ ] `docs/migrations/lineup-grow-any-instrument-sheet-migration.md` exists, names the exact 18
      column headers and their position, names which files to paste where, and includes a
      paste-verification step and a deploy-as-new-version step, consistent with
      `backend/apps-script/README.md`'s existing "Updating the code later" procedure.
- [ ] No write was made against the real Sheet by the implementer; the live check, if run at all in
      this slice's lifetime, happened only after the user confirmed the migration and redeploy, and
      is read-only.
- [ ] `feature_list.json` and `PROGRESS.md` were updated correctly, including that this feature
      depends on a human migration/deploy step before its live verification can close.
- [ ] No unrelated product behavior or extra feature work was added (in particular: no change to
      `assignSlot`/`clearSlot`'s request/response shape, no Room schema version bump).
