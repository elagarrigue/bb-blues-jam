# Feature Implementation Spec: Adjust a song's lineup

## Source Feature

- `id`: admin-adjust-lineup
- `area`: feature-next-jam
- `depends_on`: admin-add-song-to-setlist (`accepted`), instrument-strip-component (`accepted`)
- `status`: `not_started` at planning time (7 October 2026)
- `source`: `feature_list.json`. Notes: D-06, D-18, U1 = B (slot identity by ordinal among non-`-`
  columns). The notes also ask for the extra-participant (`Otros`) mutations "here or in a split
  slice": this spec **splits them out** (question X1).

## Readiness

Builds on accepted code: `Post.js` (`ACTIONS`, router guard, write lock, `requireEditableJam_`,
`setlistSpecs_`, `findSongRow_`, `notInSetlist_`, the plain-text cell rule, `checkSetlistWrite_`
with `isMarkerRow_`), `AdminWriter.send`/`WriteOutcome`, `DefaultSetlistRepository` (`ids`, `order`,
`writes`, `DataScope`) with its helper classes (`SetlistKeyChanges` is the template), `SetlistDao`,
and the admin layer of `NextJamPresenter` (`AdminState`, `RowAdmin.decorate`, `pendingKey`,
`FailureMessages.kt`, `SongRowAdminUiModel`).

**Batched deploy.** Part A joins the second batched `Post.js` deploy proposed for adjust-lineup,
assign-musician, reorder and publish. Within the batch, implement server halves in
`feature_list.json` order (this slice first). Each slice that extends `checkSetlistWrite_` appends
its own step after the previous slice's; `isMarkerRow_` must compare against an expected-slots
argument (this slice introduces it, see A1), so later steps only change the expectation.

Size: one feature in two parts (A: server and `:core:data`; B: UI), each about one session. No new
screen, no route, no Room schema change.

## Goal

In admin mode an expanded row gets **Cambiar formación**. It opens an inline editor in the row:
one line per instrument of the default lineup with `−` and `+`. Each tap sets that instrument's slot
count (0 up to its default: guitar 2, every other instrument 1) and the row's strip and lineup panel
update **at once** (optimistic, as set-key's O1), with `Guardando…` until the server answers. On
failure the row reverts and a card says why.

- **Remove** writes `-` into a slot cell, so the instrument is not in this song's lineup.
- **Restore** writes an empty cell back into a `-` column: an open slot. Never beyond the default
  (D-18); nothing is ever added.
- **A slot holding a musician is never removed.** The server refuses with `slot_filled`, and the
  editor disables `−` when every slot of that instrument is filled, saying why (question F1).
- **No name ever moves.** Removal picks an open column; restore reopens a `-` column in place.

The mutation is `SetlistRepository.setSlotCount(jamDate, songId, instrument, count)` in
`:core:data`, server action `setSlotCount`, usable with no UI (D-13).

## Non-Goals

- Adding or removing extra participants (`Otros`): split slice (X1). The editor never touches `Otros`.
- Assigning or clearing names (`admin-assign-musician`, `admin-clear-slot`).
- More slots than the default, or any new instrument (D-18).
- Editing the lineup from the song detail (it keeps showing the confirmed lineup).
- Moving a musician between guitar columns; offline queueing (blocked, as the other writes).
- Deeplinks and the registry (`action-contract-registry`).

## Users And Permissions

- **Musician:** nothing changes; `SongRowUiModel.admin` is null and no overlay applies.
- **Admin:** sees the action, the editor and the optimistic lineup. Apps Script authorizes the write.
- **Debug admin** (no passphrase): `setSlotCount` returns `AccessRefused` without a request; the
  device shows the overlay, the revert and the card.

## Acceptance Scenarios

1. **Remove an open slot.** `crossroads` has harmonica open. The admin taps `−` on Armónica: the
   strip and panel drop the harmonica at once, `Guardando…` shows; the server writes `-` (plain text)
   to that row's `Armónica` cell only; Room's slots for the song are replaced from the answer; the
   status line goes; the harmonica stays gone. `readJams` (and the GET, if published) return `"-"`.
2. **Zero of an instrument renders.** A song with `Armónica = -` shows no harmonica chip, no panel
   line, and the filter counts no harmonica slot for it. A song with every slot removed shows an
   empty strip and `No quedan cupos libres.`.
3. **Guitar 2 → 1 keeps names in place.** `Guitarra 1 = Martín`, `Guitarra 2` empty: `−` writes `-` to
   `Guitarra 2`. With both open, the later column (`Guitarra 2`) goes. With `Guitarra 1` empty and
   `Guitarra 2 = Pedro`, `Guitarra 1` goes; Pedro's cell is never written.
4. **Restore.** Count 0 → 1 for an instrument writes an empty plain-text cell into its `-` column.
   Guitar 0 → 1 reopens `Guitarra 1`; 1 → 2 reopens the one `-` guitar column, whichever it is. With
   `Guitarra 1 = -` and `Guitarra 2 = Pedro`, restoring reopens `Guitarra 1`, so Pedro becomes the 2nd
   guitar slot by ordinal (U1 = B). This is accepted and documented: the name does not move, and the
   two guitar slots are interchangeable on every screen.
5. **Filled slot refused.** Bass is `Lucía`: `−` on Bajo is disabled with `Tiene músico anotado.
   Liberá el cupo antes de sacarlo.` Guitar with both filled: same. A direct call (assistant, or a
   name assigned meanwhile by another admin) gets `slot_filled`, nothing written; the row reverts and
   the card `No se pudo cambiar la formación de «…»` / `Ese cupo tiene un músico anotado. Liberalo
   antes de sacarlo.` appears.
6. **Same count is a no-op.** `+` at the default and `−` at 0 are disabled. A direct call with the
   current count writes nothing and answers `ok` with the row's slots.
7. **Revert on failure.** For `AccessRefused`, `Offline`, `Unavailable`, `Rejected(code)`: the row
   draws the confirmed lineup, the card stays until `Cerrar`, the filter never hides it, Room and
   the Sheet are unchanged.
8. **Server validates before writing**, in this order, each failure writing nothing:
   `invalid_date`; `invalid_song`; `invalid_instrument` (not one of `guitar`, `bass`, `drums`,
   `vocals`, `harmonica`, `keyboards`); `invalid_count` (not a JSON integer in 0..default for that
   instrument); `unknown_jam`/`duplicate_date`; `jam_not_editable`; no tab → `song_not_in_setlist`,
   else `missing_header`/`duplicate_header`; `song_not_in_setlist`/`duplicate_song` (`findSongRow_`);
   `slot_filled`.
9. **Ordered, latest wins, survives the caller.** Writes queue behind the shared mutexes in call
   order with adds, removals and key changes; leaving the screen never cancels one (`DataScope`).
   Two quick taps on one instrument draw the later count.
10. **Only the slot cells change.** `tono`, `posicion`, titles, `Otros` and `Catalogo` are never written.

## Repository Research

Inspected: `AGENTS.md`, `PROGRESS.md` (state, sessions 076–078), `feature_list.json`, `CONTEXT.md`
(Lineup, Extra Participant), `docs/domain-model.md`, `docs/sheet-schema.md` (Slot columns,
Identifiers, how Apps Script writes a jam tab), `docs/apps-script-api.md` (POST actions, client
write path, `SetlistRepository`), `docs/risks-and-open-questions.md` (action surface, offline),
bitácora D-06 and D-18, `DESIGN.md` (Colors, Song row expanded, Admin controls), design prompt §2–3
and design rules, the architecture skill (Where Each Piece Goes, Dependency Rules, Anti-Patterns),
`docs/specs/admin-set-key.md`, the start of `admin-remove-song-from-setlist.md`. Code: `Post.js`
(`ACTIONS`, `setKey_`, `writeKeyCell_`, `checkSetlistWrite_`, `isMarkerRow_`), `Jams.js`
(`SLOT_FIELDS` names only), `Lineup.kt`, `Slot.kt`, `SetlistDao`, `JamEntities` (`JamSlotEntity`),
`SetlistMapper` (`lineupSlots`), `SetlistRepository`, `DefaultSetlistRepository`,
`SetlistKeyChanges`, `NextJamPresenter` (row mapping, `decorate`), `NextJamUiModel`,
`KeyChangeOverlay.kt`, `FailureMessages.kt`, `NextJamCopy` constants, `core/ui/lineup` file list,
`InstrumentStripCopy.name`, `DemoUpcomingJam` (filled and `-` cells). Not inspected: the
`AdminControls.kt` composables, `LineupPanel.kt` drawing code, Node test helpers in detail.

Findings:

- `jam_slot` stores `column_index` and a `-` column has **no row**; restoring is an insert, removing
  a delete. Primary key `(jam_date, position, column_index)`: **no Room schema change**.
- The domain `Lineup` carries no column index; the overlay works on instruments and counts, and the
  confirmed mirror comes from the server's answer, which carries columns.
- `Lineup` already enforces "never more than the default" (D-18); `DEFAULT_COUNTS` is private.
- Instrument full names are `internal` in `:core:ui` (`InstrumentStripCopy.name`).
- `isMarkerRow_` asserts every slot open, so extending the check needs an expected-slots argument.
- The demo jam has filled, open and `-` cells (`dust-my-broom`, `crossroads`), enough for the device check.

## Technical Approach — Part A: server and `:core:data`

### `backend/apps-script/src/Post.js` (the only src file that changes)

- `ACTIONS.setSlotCount = { write: true, run: setSlotCount_ }`; request
  `{date, songId, instrument, count}`. Validate in scenario 8's order, reusing `isCalendarDate_`,
  `SONG_ID_`, `requireEditableJam_`, `notInSetlist_`, `mapColumns(date, setlistSpecs_(), header)`
  and `findSongRow_`. `count` must satisfy `typeof count === 'number' && Number.isInteger(count)`.
- `SLOT_COLUMNS_` maps each wire instrument to its slot fields in column order
  (`guitar` → `guitar1`, `guitar2`; `bass` → `bass`; …), so the default count is the list length.
- `planSlotCount_(cells, fields, count)` (pure, read only): with `present` = the fields whose trimmed
  cell is not `-`, in column order. Fewer wanted: remove open ones (empty cell), **last column
  first**; if the open ones are not enough, throw `slot_filled`. More wanted: reopen `-` columns,
  **first column first**. Returns `{field: newValue}` for the changed cells only (`'-'` or `''`).
  This is the one place that resolves columns; `admin-assign-musician` should reuse its `present`
  computation (`presentSlotFields_`) rather than write a second one.
- `writeSlotCells_(sheet, row, columns, changes)`: per changed cell, `setNumberFormat('@')`, then
  `setValue`. Then answer `{ok:true, slots:{guitar1…keyboards}}`, the row's seven cells re-read with
  the read path's rules (trimmed display text, empty → `null`, `-` raw), so the client mirrors the
  Sheet, a concurrent name included.
- **Deploy check (A1).** `checkSetlistWrite_` gains one step after the key rewrite: on the marker,
  `guitar` → 1, `harmonica` → 0, `harmonica` → 1, through `planSlotCount_`/`writeSlotCells_`.
  `isMarkerRow_(rows, marker, expectedSlots)` then expects `guitar2: "-"` and the rest `null`.
- New codes: `invalid_instrument`, `invalid_count`, `slot_filled`. Update the header comment,
  README marker and the `Known:` list.

### `:core:model`

- `Lineup.defaultCount(instrument): Int` (companion, public) and `Lineup.count(instrument): Int`.
- `Lineup.withSlotCount(instrument, count): Lineup?` — the client mirror of `planSlotCount_` for the
  overlay: fewer drops open slots of that instrument from the last; returns null when filled slots
  would have to go; more inserts open slots after the instrument's last slot (or at its default
  place in `DEFAULT_INSTRUMENTS` order when it has none). `require(count in 0..defaultCount)`.

### `:core:data`

- `cache/SetlistDao`: `@Transaction suspend fun replaceSlots(date, songId, slots: List<JamSlotEntity>
  ): Boolean` (build entities at the found position): only while `setlist_state` is `AVAILABLE` and
  exactly one cached song has the id; deletes that song's `jam_slot` rows and inserts the given ones.
  Also `@Query DELETE FROM jam_slot WHERE jam_date = :date AND position = :position`.
- `setlist/`: `SetSlotCountOutcome` (`SlotCountSet` data object, `NotSet(reason)`, never `Done`);
  `LineupChange(id, jamDate, songId, title, instrument, count, state)` with `Sending`/`Failed`;
  internal `SetlistLineupChanges(writer, setlistDao, catalogDao)` shaped like `SetlistKeyChanges`.
- `SetlistRepository` gains `suspend fun setSlotCount(jamDate, songId: SongId, instrument:
  Instrument, count: Int): SetSlotCountOutcome` and `fun observeLineupChanges():
  Flow<List<LineupChange>>`; `dismiss(id)` covers them.
- `DefaultSetlistRepository.setSlotCount`: same shape as `setKey` (undispatched in `scope`, entry
  under `order`, write under `writes`). A count outside `0..Lineup.defaultCount(instrument)` →
  `Failed(Rejected("invalid_count"))`, no request. Otherwise `writer.send("setSlotCount", {date,
  songId, instrument: instrument.name.lowercase(), count})`. `Ok` with a valid `slots` object (seven
  keys, each null, `"-"` or a non-blank string, and at most the default per instrument) → **first**
  `replaceSlots` (a `SQLException` is swallowed), **then** remove the entry, `SlotCountSet`. A
  malformed `Ok` → `NotSet(Unavailable)` (add-song precedent). `Refused` → `Failed(reason)`, Room untouched.
- `di/DataModule`: unchanged.

## Technical Approach — Part B: UI (`:feature:next-jam`, `:core:ui`)

- `:core:ui` `strip/`: public `fun Instrument.fullName(): String` delegating to
  `InstrumentStripCopy.name` (one source for "Guitarra"…). No new component.
- `NextJamPresenter` collects `observeLineupChanges()` into `AdminState.lineupChanges`, and holds
  `lineupEditing: String?` (the `removalKey` of the row whose editor is open, `rememberSaveable`,
  one at a time, like the removal's `confirming`).
- **Overlay** (`LineupChangeOverlay.kt`, beside `KeyChangeOverlay.kt`): `pendingLineup(date, songId,
  confirmed): Lineup?` takes, per instrument, the latest `Sending` change (highest id) and applies
  them with `withSlotCount`; a change that returns null is skipped. `Failed` never overlays: the
  failure **is** the revert. For the admin, the overlaid lineup replaces `song.lineup` **before**
  filtering, the filter bar counts, `toInstrumentChips` and `toLineupPanel`, so what is drawn and
  what is filtered agree. Musicians never get it.
- **Status line.** Rename `SongRowAdminUiModel.keyStatus` to `saveStatus`: `Guardando…` while any key
  or lineup change of the row is sending (one line, same drawing). Assign/clear may reuse it.
- `SongRowAdminUiModel` gains `lineup: LineupEditorUiModel` — `Idle(label, events)` (`Cambiar
  formación`) or `Editing(heading, lines, doneLabel, events)` with `LineupEditorLineUiModel(
  instrument, countLabel, removeDescription, addDescription, canRemove, canAdd, blockedNote,
  events)`. `canRemove` = count > 0 and `withSlotCount(count − 1) != null`; `canAdd` = count <
  default; both computed on the overlaid lineup. A tap calls `setSlotCount(…, count ± 1)` launched
  undispatched in the presenter's scope; handlers keyed by `(date, songId, instrument, step)`.
- Row actions, in order: `Ver detalle del tema`, `Cambiar tonalidad`, `Cambiar formación` (or its
  editor in place), `Quitar de la lista` (last).
- Failures: `NextJamAdminUiModel.failures` merges lineup changes by id; title
  `NextJamCopy.lineupFailed(title)`; `lineupFailureMessage`: `slot_filled` → `SLOT_FILLED`,
  `song_not_in_setlist` → `JAM_CHANGED`, `duplicate_song` → `DUPLICATE_SONG`, else `failureMessage`.

### Copy (Rioplatense, *vos*; to approve, question C1)

| Key | Text |
|---|---|
| action | `Cambiar formación` |
| heading | `FORMACIÓN` (caption, uppercase, heading) |
| count | `2 cupos` · `1 cupo` · `No va en este tema` |
| buttons | `−` / `+`, described `Sacar un cupo de guitarra` / `Sumar un cupo de guitarra` |
| blocked note | `Tiene músico anotado. Liberá el cupo antes de sacarlo.` |
| done | `Listo` |
| card title | `No se pudo cambiar la formación de «Crossroads»` |
| `slot_filled` | `Ese cupo tiene un músico anotado. Liberalo antes de sacarlo.` |

## Visual Design Impact

**No amber in the editor**: counts are not open slots. Lines are `surface` rows; the instrument in
`body`/`text`, the count in `caption`/`textMuted`; `−`/`+` are 48dp `surfaceRaised` squares,
`shapes.md`, glyph in `songTitle`/`text`, `Role.Button`; disabled ones `textMuted`, not clickable,
`disabled()` semantics. The blocked note is `caption`/`textMuted` under its line. `Listo` and
`Cambiar formación` are the underlined 48dp text action (`AdminControlsDefaults`). Colours through
`AdminControlsDefaults` (new `lineupEditor()`), no dp literals in the feature. Amber stays where the
strip and panel already draw it (open slots, inside `:core:ui`). Konsist allowlist unchanged
(`{key}`). `DESIGN.md` gains "Adjusting a lineup" under Admin controls.

## Durable Documentation Impact

- `docs/apps-script-api.md`: `setSlotCount` section (request, order, codes, answer), the
  column rule, the extended `checkSetlistWrite`, `Known:`, client `setSlotCount`.
- `docs/sheet-schema.md`: how `setSlotCount` writes (`-`/empty, plain text, open column picked last
  first, restore first `-` first, never a name), the restore-ordinal note, `_prueba_lista` row.
- `docs/domain-model.md`: adjusting a lineup as built; the filled-slot rule.
- `docs/risks-and-open-questions.md`: risks below; mark the extra-participant mutations as split.
- Architecture `SKILL.md`: `setSlotCount`, `LineupChange`, overlay by instrument, `saveStatus`,
  row-action order. `DESIGN.md` as above. `AGENTS.md`, `CONTEXT.md`: not needed.

## Implementation Plan

1. **A, model.** `defaultCount`, `count`, `withSlotCount` with `LineupTest` cases mirroring
   scenarios 2–5 (one case table, also used for the Node tests' expectations).
2. **A, server.** `setSlotCount_`, `planSlotCount_`, `writeSlotCells_`, the check step; Node tests:
   each scenario 3/4 layout; only the changed cells written, as `@`; validation table writes nothing;
   `slot_filled`; same count writes nothing; `Otros`, `tono` and `Catalogo` untouched; found by id
   after a removal renumber; GET after `setSlotCount` on a published jam; the extended check.
3. **A, data.** In-memory Room: `replaceSlots` only for one (date, songId), not for a non-available
   setlist or a duplicate id; `Done` mirrors the answer (restore inserts, remove deletes, a name from
   the answer kept); failures leave Room and publish `Failed`; cache before entry removal; local
   `invalid_count`; ordering with the other writes; cancellation; `dismiss`; unique ids.
4. **Failure demonstrations** (mutate, see the named test fail, restore, `sha1sum -c`): (a)
   `planSlotCount_` removes the first open column instead of the last; (b) it removes a filled slot
   instead of throwing; (c) `replaceSlots` drops `song_id` from its lookup; (d, Part B) the overlay
   also applies `Failed` changes.
5. `CI=true ./init.sh`. **Stop for the batched deploy** (user pastes `Post.js`, **New version**).
6. **Live checks** (scratchpad script, `json.dumps` bodies, prints only codes, counts and
   latencies; never the URL, passphrase or names; **never `setSlotCount` with a real song id**):
   L1 `{}` → `Known:` includes `setSlotCount`; L2 wrong passphrase → `invalid_passphrase`; L3
   `checkSetlistWrite` → `ok` (latency); L4 refusals: `invalid_date`, `invalid_song`,
   `invalid_instrument` (`kazoo`), `invalid_count` (guitar 3, bass 2, -1, `"1"`), `unknown_jam`
   (1999-01-01), `jam_not_editable` (the past jam), `song_not_in_setlist` (upcoming date,
   `zz-no-existe`); L5 `readJams` before and after: counts and SHA-256 identical.
7. **B.** Molecule tests: musician model unchanged; editor open/close; a `Sending` change overlays
   strip, panel, filter and counts with `Guardando…`; latest per instrument wins; `Failed` reverts
   and adds a card; after `Done` the cached lineup shows; `canRemove` false on filled; zero-slot and
   all-removed songs render; `saveStatus` with key and lineup together. Update set-key tests for the
   rename only.
8. **Gate** `CI=true ./init.sh`.
9. **Device**, Pixel 5, debug flags `demoUpcomingJamLive` and `debugAdmin` (restore
   `local.properties` from a byte copy; **never TalkBack or accessibility settings**): editor layout
   on `dust-my-broom` (guitar 1 filled, guitar 2 `-`: `+` enabled on Guitarra, `−` disabled with the
   note); `−` on Armónica of `the-thrill-is-gone` (all open): overlay then revert with the
   `AccessRefused` card (screenshots if catchable); rotation, font scale 2.0 (restored); flag off:
   musician view unchanged; `uiautomator dump` of the editor. Record and restore every setting.

## Verification Plan

- Node green; `CI=true ./init.sh` exits 0 with `konsist: wired` (17/17), `detekt: wired`, `ktlint: wired`.
- "Persists to the Sheet": the Node success tests plus L3 on real Sheets.
- "Zero slots valid and rendered": `LineupTest`, the Molecule zero/all-removed tests, the device.
- "Filled slot handled deliberately": Node `slot_filled`, `withSlotCount` null, `canRemove`, card copy.
- The success path on a device needs a stored passphrase, by design; Node and the JVM cover it.

## Evidence To Capture

Node and gate counts; demonstrations (a)–(d) with SHA-1 restores; L1–L5 codes, L3 latency, deployed
`Post.gs` SHA-1; device screenshots and dump; settings before and after; a statement that no URL,
passphrase or name appeared in any output.

## Risks

- **Two implementations of the column rule** (JS `planSlotCount_`, Kotlin `withSlotCount`). Only the
  JS one writes; Kotlin only draws the overlay, and the mirror comes from the answer. One shared case
  table keeps them aligned.
- **Overlay order among guitars** may differ for a moment from the confirmed order (the client
  does not know columns); cosmetic, gone on confirmation.
- **Restore ordinal shift** (scenario 4): Pedro moves from 1st to 2nd guitar by ordinal. Any write
  that targets a slot by ordinal (`admin-assign-musician`, `admin-clear-slot`) must re-resolve on the
  server and refuse when the resolved cell is not in the expected state, or it can hit the wrong column.
- **Unsaved lineup** drawn for ~5 s on the admin's device only (as O1); reverted on failure.
- **Lost entries** with the process, as the other writes.

## Validator Checklist

- [ ] Only `Post.js` changes server-side; `setSlotCount` is in `ACTIONS` with `write: true`, never
  calls the guard, checks everything before the first write, finds the row with `findSongRow_`.
- [ ] Only changed slot cells are written, as plain text; no name cell is ever written; a filled slot
  is never removed; never more than the default.
- [ ] `setSlotCount` is a public `SetlistRepository` function (D-13); Room changes only after `ok`,
  from the answer, for exactly one (date, songId).
- [ ] The overlay comes only from `Sending` entries; failure reverts and shows a card; musician
  models unchanged; no amber added; copy as approved.
- [ ] Three `wired`, Node green, live checks touched no real jam, no secret in any output.

## Open Questions (recommendations in bold)

- **X1, `Otros` mutations.** **Split into a new slice `admin-edit-extra-participants`** (add and
  remove one `Nombre (instrumento)` entry, server action(s) on the `Otros` cell, same pattern),
  depending on this one. Planning it here would double the slice (a second write, a text-entry form,
  parsing rules on the server). The orchestrator or user must add the entry to `feature_list.json`.
- **F1, a slot that holds a musician.** **Refuse** (`slot_filled`, `−` disabled with the note):
  safest against a concurrent assign, and once `admin-clear-slot` lands the admin clears first.
  Alternative: inline confirmation that clears the name, as remove-song does, at the cost of a
  second guarded parameter and a race to handle.
- **U1, inline editor vs full-screen.** **Inline in the expanded row**: the strip and panel update in
  view as the admin taps, no route. Alternative: a full-screen editor like the key picker.
- **C1, copy.** **As in the table above.**

Decided without a question, by precedent: optimistic display (set-key O1), extending
`checkSetlistWrite` (set-key L1 (a)), row found by `id_tema` (remove-song R1), offline blocked.
