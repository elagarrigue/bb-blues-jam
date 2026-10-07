# Feature Implementation Spec: Assign a musician to an open slot

## Source Feature

- `id`: admin-assign-musician
- `area`: feature-next-jam
- `depends_on`: admin-add-song-to-setlist (`accepted`), song-row-expansion (`accepted`)
- `status`: `not_started` at planning time (7 October 2026)
- `source`: `feature_list.json`. Notes: D-05 (only the admin assigns), D-13, D-18. Suggestions come
  from prior jams (settled by `docs/sheet-schema.md`: the names in past jam tabs, no musicians
  list). Two musicians sharing a name are not disambiguated, by design.

## Readiness

Planned on 7 October 2026, while three other planners wrote `admin-adjust-lineup`,
`admin-reorder-songs` and `admin-publish-setlist`. The reorder planner proposes **one batched
`Post.js` deploy** for all four. Under that plan:

1. Each slice's Part A (server and `:core:data`) lands in `feature_list.json` order: adjust-lineup,
   assign (this one), reorder, publish. Every Part A keeps the earlier Node tests green unchanged.
2. Then comes the user's single paste and **New version**.
3. Then each slice's live checks.
4. Then each Part B, with validation per feature.

The slices share code, so whichever Part A lands first creates these and the others reuse them:

- **Server.** `INSTRUMENT_FIELDS_` maps each instrument's enum name to its slot fields
  (`GUITAR` → `guitar1`, `guitar2`; `BASS` → `bass`; and so on). `slotColumn_(columns, rowValues,
  instrument, ordinal)` resolves the U1 rule (below).
- **`:core:model`.** `SlotPosition` and its `Lineup` helpers.

If adjust-lineup already defines an equivalent, reuse it and do not create a second one.

Part B builds on set-key's Part B: `SongRowAdminUiModel`, the failure cards, `FailureMessages.kt`
and the `KeyChangeOverlay.kt` pattern.

Size: one feature in two parts, each about one session. It is at the upper bound because it adds a
new screen. The split already in `feature_list.json` (assign here, clear in `admin-clear-slot`)
keeps it within that bound.

## Goal

On Próxima jam, in admin mode, every **open** slot line of an expanded row becomes a 48dp button
with the trailing action `Anotar`. It opens a full-screen **Anotar músico** screen.

- **The slot is fixed.** The instrument comes from the line and is shown as a header, not a choice.
- **Entering the name.** The admin types a name, or taps a suggestion. Suggestions come from names
  already in this jam (`ESTA JAM`) and in past jams (`JAMS ANTERIORES`), all read from the jams
  cache.
- **Optimistic display (recommended, O1).** The screen closes. On the admin's device the slot is
  drawn filled with the name at once, with `Guardando…`.
- **The write.** Apps Script finds the row by `id_tema` and the column by the U1 rule. It writes
  the name only into a cell that is still empty.
- **On success,** Room's `jam_slot.musician_name` is updated and the status goes away.
- **On failure,** the slot reverts to open and a persistent card says why.

The mutation is `SetlistRepository.assignSlot(jamDate, songId, instrument, ordinal, musicianName)`
in `:core:data`. It is usable with no UI (D-13).

## Non-Goals

- **Clearing or replacing a filled slot.** That is `admin-clear-slot`. Under C1 (b) its server
  action ships here (see the Technical Approach); its repository function and UI do not.
- **Extra participants (`Otros`, `addExtra`).** These are not slots (D-18), and adjust-lineup's notes
  own them (X1).
- **Assigning from the song detail.** That screen stays read-only.
- **Musician self-signup, profiles, or a musicians list or entity** (D-05, out of MVP scope).
- **A new data source for suggestions.** No Sheet read beyond the existing jams refresh, and no
  external API.
- **Disambiguating equal names.**
- **Offline queueing.**
- **Deeplinks and the action registry.**

## Users And Permissions

- **Musician:** nothing changes.
  - Open lines stay non-interactive (DESIGN.md: no false affordance).
  - Names appear once the admin's write reaches the Sheet and the next refresh brings it.
  - In a draft, nothing appears until publish (`setlistForMusicians()`).
- **Admin** (`observeIsAdmin()` true): sees the slot actions, the screen and the optimistic names.
  Apps Script authorizes every write.
- **Debug admin:** `assignSlot` returns `AccessRefused` and sends nothing. The device shows the
  optimistic name, then the revert and the card.
- **Privacy:**
  - A published jam's names are visible to every musician. This is already true of names typed in
    the Sheet.
  - Suggestions (past names) are drawn only on the admin's screen.
  - No log line, refresh outcome, live-check output or failure message sent to logcat contains a
    name. The failure card on the admin's own screen does name the musician.

## Domain Rules (decided here)

- **Slot identity (U1, `sheet-schema.md` Identifiers).** A slot is (date, songId, instrument,
  ordinal). The k-th slot of an instrument is the k-th column of that instrument, in header order,
  whose cell is not `-`. The ordinal is 1 or 2 for `GUITAR` and 1 for every other instrument.
  - The panel sorts open lines first, so each line carries its ordinal from **lineup order**, never
    its drawn index.
  - The client and Apps Script resolve the column by the same rule.
- **Name (`MusicianName` in `:core:model`, mirrored in `Post.js`).**
  1. Normalize: trim, and collapse every run of whitespace to one space.
  2. Then the name must:
     - be non-empty;
     - be at most **40** UTF-16 units (Kotlin `length` equals JS `length`);
     - contain no control character;
     - contain no `;`, `(` or `)` (the `Otros` separators, so a name can move to `Otros` intact);
     - not start with `=`, `+`, `-` or `@` (formula injection, and `-` is the "not in lineup"
       sentinel);
     - contain at least one letter or digit.

  The read path still accepts any name the admin typed by hand. `Slot` is not changed.
- **The same musician in two songs, or in two slots of one song (sings and plays), is allowed**
  with no warning. Names are display strings (`domain-model.md`).
- **Assign never overwrites.** A cell that is non-empty after trimming is `slot_taken`, so two
  admins can never silently replace each other. Changing a name means clear, then assign.

## Acceptance Scenarios

1. **Optimistic, then saved.** The upcoming jam has `crossroads` with both guitars open. The admin
   expands the row, taps the first open `Guitarra` line, types ` Tincho ` and taps `Anotar`.
   - The screen closes. The admin's row shows `Guitarra: Tincho` with `Guardando…`, in the filled
     section, the strip and the filter counts.
   - The server writes `Tincho` as plain text into `Guitarra 1` of the one row whose `id_tema` is
     `crossroads`, and nothing else.
   - Room then holds the name for that slot, and the status goes away.
2. **U1.** With `Guitarra 1 = -` and `Guitarra 2` empty, the song's only guitar slot is ordinal 1,
   and the write goes to `Guitarra 2`.
3. **Revert on failure.** For `AccessRefused`, `Offline`, `Unavailable` or `Rejected(code)`:
   - the slot is open again;
   - the card `No se pudo anotar a «Tincho» en «Crossroads»` appears with the reason and `Cerrar`;
   - Room and the Sheet are unchanged.
4. **Server order, nothing written on failure.**
   1. `invalid_date`.
   2. `invalid_song`.
   3. `invalid_slot`: an unknown instrument, or an ordinal out of range.
   4. `invalid_name`: the rule above, after server-side normalization.
   5. `unknown_jam` or `duplicate_date`.
   6. `jam_not_editable`.
   7. No tab gives `song_not_in_setlist`. Otherwise the tab's `missing_header` or
      `duplicate_header`.
   8. `findSongRow_` gives `song_not_in_setlist` or `duplicate_song`.
   9. No k-th non-`-` column gives `slot_not_in_lineup`.
   10. A non-empty cell gives `slot_taken`.
5. **Suggestions.**
   - With the field empty, the screen lists up to 8 names under each of `ESTA JAM` (names already
     in the upcoming jam's slots and `Otros`) and `JAMS ANTERIORES` (past jams' names not already
     in `ESTA JAM`, by most recent appearance, then alphabetically).
   - Typing filters both lists to names with a word starting with the text, ignoring case and
     accents (`searchKey()`).
   - Names are deduplicated by `searchKey`, keeping the most recent spelling.
   - Tapping a suggestion assigns that spelling at once, like a key pick.
6. **Validation on the screen.**
   - Blank: `Anotar` is disabled.
   - Otherwise an invalid name shows one `caption` line (copy below), and `Anotar` and the IME
     action do nothing.
7. **Gone.** The screen shows `Ese cupo ya no está libre` / `Volvé a la próxima jam para ver la
   lista actual.` when any of these hold:
   - the flag is off;
   - the song is not in the readable upcoming setlist;
   - the slot no longer exists;
   - the slot is filled in the cache;
   - a `Sending` assignment targets the slot.
8. **Ordered, single.** Writes go in call order behind the shared mutexes. A double tap on `Anotar`
   or on a suggestion assigns once. Leaving the screen never cancels the write.

## Repository Research

Inspected:

- Project docs: `AGENTS.md`, `PROGRESS.md` (header), `feature_list.json`, `CONTEXT.md`,
  `docs/domain-model.md`, `docs/sheet-schema.md` (whole), `docs/apps-script-api.md` (`setKey`,
  client mutations), `docs/risks-and-open-questions.md` (action surface, suggestions).
- Design: `DESIGN.md` (Colors, Song row expanded, Admin controls, Core screens §8),
  `bb-blues-jam-design-prompt.md` §2 and §8.
- Architecture skill: whole.
- Specs: `docs/specs/admin-set-key.md`.
- Server: `Post.js` (`addSong_`, `checkSetlistWrite_`, `isMarkerRow_`, `findSongRow_`, `setKey_`,
  `requireEditableJam_`, `appendSetlistRow_`) and `Jams.js` `SLOT_FIELDS`.
- `:core:model`: `Slot`, `Lineup`, `ExtraParticipant`, `Instrument`.
- `:core:ui`: `EventHandler`, `LineupPanel*`.
- `:core:data`: `SetlistRepository`, `DefaultSetlistRepository`, `SetlistKeyChanges`, `KeyChange`,
  `SetlistDao`, `JamEntities`.
- `:feature:next-jam`: `NextJamUiModel`, `NextJamPresenter` (whole), `FailureMessages`,
  `AddSongPresenter.searchKey`, and the `SetKeyPresenter` signature.
- `:app`: the `AppRoutes` signatures.

Not inspected: `docs/user-and-access-model.md`, the bitácora, `NextJamScreen`/`AdminControls`
beyond grep, and the debug demo fixture's names.

Findings:

- The jam tab has the seven slot columns. `jam_slot` caches `column_index` 0..6 per non-`-` column
  (`-` has no row), so the U1 column can be resolved locally with no schema change.
- `JamsSnapshot.past` already holds past jams' filled slots and extras. The suggestions need no new
  read.
- `LineupPanelUiModel`'s lines carry no slot identity and no action. `LineupPanel` lives in
  `:core:ui`, so its lines need an optional action there. Amber stays inside the component, so
  `:feature:next-jam` keeps `{key}`.
- The deployed `Post.gs` includes `setKey` (live checks recorded in 2a0113a).
- `NextJamPresenter.kt` is 505 lines. Put the new admin mapping in its own file to stay clear of
  detekt `LargeClass` and `TooManyFunctions`.
- `SetlistRepository` grows to 9 functions here. Detekt's default interface threshold is 11, and
  the sibling slices also add functions: see Risks.

## Technical Approach — Part A: server and `:core:data`

### `backend/apps-script/src/Post.js`

- **`ACTIONS.assignSlot = { write: true, run: assignSlot_ }`**, with request
  `{date, songId, instrument, ordinal, name}`.
  - `instrument` is the enum name (`GUITAR`…) and `ordinal` a JSON integer.
  - It checks in scenario 4's order, reusing `isCalendarDate_`, `SONG_ID_`, `requireEditableJam_`,
    `mapColumns(date, setlistSpecs_(), header)` and `findSongRow_`.
  - Then `slotColumn_` and the emptiness check.
  - Then `writeSlotCell_(sheet, row, col, value)`: `setNumberFormat('@')`, then `setValue`.
  - It answers `{ok:true}` and never opens `Catalogo`.
- **`normalizeMusicianName_(raw)`** returns the normalized name, or throws `invalid_name`. It
  mirrors `MusicianName`.
- **`ACTIONS.clearSlot`** (only under C1 (b)), with request
  `{date, songId, instrument, ordinal, name}`.
  - The same checks 1–9.
  - A cell already empty gives `{ok:true}` with no write.
  - A cell whose trimmed value differs from `name` gives `slot_changed`.
  - Otherwise `writeSlotCell_(…, '')`.
  - No client in this slice. `admin-clear-slot` adds the repository function and the UI.
- **The deploy proof (extends `checkSetlistWrite`).** After the key step:
  1. Write `1/2` into the marker's GUITAR ordinal 2 with `slotColumn_` and `writeSlotCell_`.
  2. Read back. `guitar2` must be `1/2`, as text, and every other slot open.
  3. Under C1 (b), clear it with `clearSlot`'s path.
  4. The final read-back is the marker unchanged (`isMarkerRow_` as today). Under C1 (a), the final
     expectation includes `guitar2 = 1/2`.

  There is no new check action.
- Update the header comment, the README and the `Known:` list.

### `:core:model`

- `SlotPosition(instrument, ordinal)` (`require` 1..default count).
- `Lineup.positionOf(index): SlotPosition` and `Lineup.indexOf(position): Int?`.
- `object MusicianName` with `MAX_LENGTH = 40`, `normalize(raw)`, and `problem(normalized):
  Problem?`, where `Problem` is `BLANK`, `TOO_LONG`, `CONTROL_CHARACTER`, `RESERVED_CHARACTER`,
  `LEADING_SYMBOL` or `NO_LETTER_OR_DIGIT`.
- Unit tests for each.

### `:core:data`

- `cache/SetlistDao.assignSlot(date, songId, instrument, ordinal, name): Int` is **one `UPDATE`
  statement**, like `updateKey`. It sets `musician_name` on the `jam_slot` of the song's position
  whose `column_index` is the ordinal-th of that instrument (`ORDER BY column_index LIMIT 1 OFFSET
  ordinal-1`). It applies only when all of these hold:
  - the jam is `AVAILABLE`;
  - exactly one cached song of that date has the id;
  - the slot's `musician_name IS NULL`.
- `setlist/`:
  - `AssignSlotOutcome` is `Assigned` or `NotAssigned(reason)`, never `Done`.
  - `SlotAssignment(id, jamDate, songId, title, position: SlotPosition, musicianName, state)`, where
    `state` is `Sending` or `Failed(reason)`.
  - The internal `SetlistAssignments(writer, setlistDao, catalogDao)` mirrors
    `SetlistKeyChanges`: `start`, then `send` (on `Done`, the cache first, then the entry is
    removed), and `dismiss`.
- `SetlistRepository` gains `assignSlot(jamDate, songId, instrument, ordinal, musicianName):
  AssignSlotOutcome` and `observeAssignments()`. `dismiss` covers this kind too.
- `DefaultSetlistRepository.assignSlot` follows `setKey`'s shape: undispatched in `DataScope`,
  `order`, then `ids`, then `writes`.
- **Before sending,** it normalizes the name. An invalid name or an out-of-range ordinal publishes a
  `Failed(Rejected("invalid_name" | "invalid_slot"))` entry and sends nothing, as add-song does with
  `unknown_song`.

## Technical Approach — Part B: UI

- **`:core:ui` `lineup/`.**
  - `LineupLineUiModel` gains `status: String? = null` and `action: LineupLineActionUiModel? = null`.
    The action is `(label, clickLabel, events: EventHandler<Event.Activate>)`.
  - `toLineupPanel(extras, slotDecor: (SlotPosition, Slot) -> SlotLineDecor? = { _, _ -> null })`,
    where `SlotLineDecor(status, action)`. Musician calls are unchanged.
  - With an action, `Line` is one merged node: clickable, `Role.Button`, `onClickLabel`, and
    `heightIn(min = LocalMinimumInteractiveComponentSize)`.
    - It ends with the action label (`body`, `text`, underlined, never amber).
    - `LIBRE` stays in `slotOpen`.
  - A `status` is drawn after the detail (`caption`, `textMuted`) and appended to the
    `contentDescription` (`Guitarra: Tincho, guardando`).
- **`:feature:next-jam`.**
  - **Overlay.** `SlotAssignmentOverlay.kt` holds `List<SlotAssignment>.pendingNames(date, songId):
    Map<SlotPosition, String>` (`Sending` only; the highest id wins) and
    `Lineup.withPending(map)`, which fills only slots that are open in the cache.
  - **The admin row.** It is built from the overlaid lineup (strip, panel, filter match and bar
    counts agree). Pending slots get `status = Guardando…`. Cached-open slots get the `Anotar`
    action, keyed `"date|songId|INSTRUMENT|k|assign"` and calling `Params.onAssignSlot(date, songId,
    instrument, ordinal)`. Filled lines get nothing (clear-slot adds that).
  - Musicians never get an overlay or an action.
  - **Failures.** Cards merge assignments by id. The title is `NextJamCopy.assignFailed(name,
    title)`. `assignFailureMessage` maps:
    - `slot_taken` → `SLOT_TAKEN`;
    - `slot_not_in_lineup` → `SLOT_GONE`;
    - `invalid_name` → `NAME_REFUSED`;
    - `song_not_in_setlist` → `JAM_CHANGED`;
    - anything else → `failureMessage`.
  - **The screen.** `AssignSlotPresenter(JamsRepository, AdminSession, SetlistRepository)` with
    `Params(jamDate, songId, instrument, ordinal, onBack, onDone)`, plus the pure
    `assignSlotModel(...)` and `musicianSuggestions(snapshot, query)`. Add `AssignSlotUiModel`
    (`Loading`, `Gone`, `Content`), `AssignSlotScreen`, `AssignSlotCopy` and `AssignSlotDefaults`
    (no amber).
  - The typed name lives in `rememberSaveable` (not a secret).
  - The first valid submit launches `assignSlot` undispatched and calls `onDone()`. Later submits
    are ignored.
- **The screen's layout** (DESIGN.md gets "Assign screen, as built"):
  1. A 48dp `Volver`.
  2. The title `Anotar músico` (`h1`, a heading).
  3. The song title (`songTitle`, `text`).
  4. `CUPO` (`caption`, `textMuted`) over the instrument name (`songTitle`, `text`). There is no
     selector.
  5. The outlined field `Nombre`, with explicit token colours as in the admin login and IME action
     `Done`, then the validation line, if any (`caption`, `error`).
  6. A full-width 48dp `Anotar` button (`surfaceRaised`/`text`, `shapes.md`, `Role.Button`; disabled
     `textMuted` while blank).
  7. The suggestion sections `ESTA JAM` and `JAMS ANTERIORES` (`caption`, `textMuted`, headings).
     Each row is a `surface` card, at least 48dp, with the name in `body`/`text`, `Role.Button`, and
     the click label `anotar en este cupo`.

  The content sits above the keyboard (`imePadding`) and the screen scrolls.
- **`:app`.**
  - `AppRoutes.ASSIGN_SLOT = "assignSlot/{jamDate}/{songId}/{instrument}/{ordinal}"`, with
    `assignSlot(...)` and `parseAssignSlot`, which accepts an ISO date, `SongId.parseOrNull`, an
    `Instrument` name and an in-range ordinal; anything else is null.
  - A destination beside `setKey`, with the same slide, insets and `RESUMED` guard. `onDone` pops by
    route.
  - `TabsShell(onOpenAssignSlot)` → `NextJamScreen(onAssignSlot)`.
- Konsist stays at 17 rules with `{key}`. No Gradle, manifest, Room-version or dependency change.

### Copy (Rioplatense, *vos*)

| Key | Text |
|---|---|
| Line action / click label | `Anotar` / `anotar a alguien en este cupo` |
| Status | `Guardando…` (shared with set-key) |
| Title / slot caption | `Anotar músico` / `CUPO` |
| Field / button | `Nombre` / `Anotar` |
| Sections | `ESTA JAM`, `JAMS ANTERIORES` |
| Too long | `Usá hasta 40 caracteres.` |
| Reserved / leading | `El nombre no puede llevar ; ( ) ni empezar con = + - @.` |
| No letter, control | `Escribí un nombre con letras o números.` |
| Gone | `Ese cupo ya no está libre` / `Volvé a la próxima jam para ver la lista actual.` |
| Card title | `No se pudo anotar a «Tincho» en «Crossroads»` |
| `slot_taken` | `Ese cupo ya estaba ocupado. Actualizá la lista para ver quién está.` |
| `slot_not_in_lineup` | `Ese cupo ya no está en la formación del tema.` |
| `invalid_name` | `La planilla no aceptó ese nombre.` |

## Expected File Changes

- **Part A:**
  - `backend/apps-script/src/Post.js`, `test/setlist.test.js` (or a new `test/slots.test.js`),
    `README.md`;
  - `core/model/.../SlotPosition.kt` and `MusicianName.kt`, plus tests;
  - `core/data/.../cache/SetlistDao.kt`, `setlist/SetlistRepository.kt`,
    `DefaultSetlistRepository.kt`, and the new `SetlistAssignments.kt`, `SlotAssignment.kt` and
    `AssignSlotOutcome.kt`;
  - `SetlistDaoTest` and `DefaultSetlistRepositoryTest`.
- **Part B:**
  - `core/ui/.../lineup/LineupPanelUiModel.kt`, `LineupPanelMapper.kt`, `LineupPanel.kt`, and
    `LineupPanelMapperTest`;
  - `feature/next-jam/.../NextJamPresenter.kt`, `NextJamUiModel.kt`, `NextJamScreen.kt`,
    `NextJamCopy.kt`, `FailureMessages.kt`, `di/NextJamModule.kt`;
  - new `SlotAssignmentOverlay.kt`, `AssignSlot{Presenter,UiModel,Screen,Copy,Defaults}.kt`,
    `MusicianSuggestions.kt` and a preview;
  - tests: `AdminFakes.kt`, `NextJamAssignmentTest`, `AssignSlotPresenterTest`,
    `MusicianSuggestionsTest`, `AssignSlotDefaultsTest`;
  - `app/.../navigation/AppRoutes.kt`, `AppNavHost.kt`, `TabsShell.kt`, `AppRoutesTest`.

## Visual Design Impact

Amber stays where it was:

- `LIBRE` in `slotOpen`, inside `:core:ui`;
- the key.

The admin's `Anotar` action, the screen's button and the suggestions are non-amber. An optimistic
name is drawn as a filled slot (`slotFilled`, not amber) with `Guardando…`, so an unsaved name never
glows. The screen is full-screen, not the bottom sheet DESIGN.md §8 names. The design prompt allows
either. Full screen was chosen because:

- the keyboard and a suggestion list need the height;
- it matches the two existing pickers (routes, `RESUMED` guard, testable parsing);
- a Material 3 `ModalBottomSheet` is experimental in 1.3.2.

`DESIGN.md` §8 and Admin controls are updated.

## Durable Documentation Impact

- `docs/apps-script-api.md`:
  - `assignSlot` (and `clearSlot`): request, order, codes;
  - the extended `checkSetlistWrite`;
  - `Known:`;
  - the client mutation.
- `docs/sheet-schema.md`: what `assignSlot`/`clearSlot` write (one slot cell, plain text, only when
  empty or holding the expected name) and the name rule.
- `docs/domain-model.md`: Slot state "as built" and the name rule.
- `DESIGN.md`: as above.
- `.claude/skills/architecture/SKILL.md`: `SlotPosition`, `MusicianName`, `assignSlot`, the slot
  overlay rule, the `LineupLineUiModel.action` contract, and the route.
- `docs/risks-and-open-questions.md`: the risks below.
- `AGENTS.md`, `CONTEXT.md`, `docs/user-and-access-model.md`: not needed. No role or vocabulary
  changes.

## Implementation Plan and Verification

1. **Part A, server.** Node tests:
   - success writes one cell, as plain text;
   - U1, including `Guitarra 1 = -`;
   - every code in the validation table writes nothing;
   - `slot_taken` does not overwrite;
   - names: `=x`, `-`, `a;b`, 41 units, `\n` and whitespace-only are rejected, and `  Ana  María `
     is written as `Ana María`;
   - `Catalogo` is never written;
   - a published jam: the GET returns the name;
   - under C1 (b), `clearSlot` (empty is a no-op, a mismatch gives `slot_changed`);
   - the extended check.

   Run `node --test backend/apps-script/test/*.test.js`.
2. **Part A, model and data.**
   - `SlotPosition`/U1 and `MusicianName` tests.
   - In-memory Room:
     - only the matching slot changes;
     - a filled, missing or duplicate case changes nothing;
     - the cache is updated before the entry is removed;
     - invalid input sends nothing;
     - ordering with the other mutations;
     - caller cancellation;
     - `dismiss`.
3. **Failure demonstrations** (mutate, see the named test fail, restore with `sha1sum -c`):
   - (a) `assignSlot_` writes before the `slot_taken` check;
   - (b) `slotColumn_` counts `-` cells;
   - (c) the DAO statement drops `musician_name IS NULL`.
4. Run `CI=true ./init.sh`. **Stop for the batched deploy.**
5. **Live checks.** The script lives in the scratchpad and builds its body with `json.dumps`. It
   prints only codes, counts and latencies: never the URL, the passphrase or any name. **No probe
   may reach a write on a real row**, so every probe must fail at a check that comes before the row
   lookup, or target a song id that does not exist.
   - L1: `{}` gives a `Known:` list with `assignSlot` (and `clearSlot`).
   - L2: a wrong passphrase gives `invalid_passphrase`.
   - L3: `checkSetlistWrite` gives `ok`; record its latency.
   - L4: `readJams` gives `ok`.
   - L5: probes:
     - `invalid_date`, `invalid_song`;
     - `invalid_slot` (`SAX`, GUITAR 3);
     - `invalid_name` (`=x`, with the upcoming date);
     - `1999-01-01` gives `unknown_jam`;
     - a past date gives `jam_not_editable`;
     - the upcoming date with `zz-no-existe` gives `song_not_in_setlist`.
6. **Part B.** JVM tests:
   - mapper decor: musicians are unchanged;
   - the ordinal comes from lineup order, not drawn order;
   - overlay and status;
   - `Failed` reverts and shows a card;
   - after `Done` the cached name shows;
   - the filter and counts follow the overlay;
   - screen states: Gone cases, validation lines, single submit, suggestion sections, order, dedupe
     and accent-insensitive match;
   - `AppRoutesTest`.

   Failure demonstration (d): the overlay also applies `Failed` entries, so the revert test fails.
7. **Gate.** `CI=true ./init.sh` exits 0 with `konsist: wired` (17/17), `detekt: wired` and
   `ktlint: wired`.
8. **Device check** (Pixel 5, `demoUpcomingJam` and `debugAdmin`; **never TalkBack or accessibility
   settings**; font scale 2.0 restored after):
   1. Expand a row: open lines show `Anotar` and are 48dp; filled lines have no action.
   2. On the screen, check the header, suggestions, validation, rotation (the name survives) and the
      keyboard.
   3. Submit: the name shows with `Guardando…`, then reverts with the `AccessRefused` card.
   4. With the flag off, the musician view is unchanged.
   5. Take a `uiautomator dump`.

   Screenshots show only the demo fixture.

**Evidence:** Node and gate counts, demonstrations (a)–(d) with their SHA-1 restores, L1–L5 codes
and the deployed SHA-1, screenshots and the dump, and a statement that no URL, passphrase or real
name appeared in any output.

## Risks

- **An unsaved name on the admin's device** for about 5 s per queued write. At the jam, ten quick
  assignments queue for about 50 s. It is muted (`slotFilled` plus `Guardando…`), never amber.
- **`slot_taken` between admins.** This is correct by design. The card tells the admin to refresh,
  and the live refresh brings the other name.
- **Interface growth.** `SetlistRepository` will pass detekt's `TooManyFunctions` (11) once the
  siblings land. If it trips, split it by concern in a reviewed change, for example a
  `LineupRepository` for slots and extras. Do not suppress the rule.
- **Collision with adjust-lineup.** Both slices need `SlotPosition` and the server column
  resolver. See Readiness.
- **Suggestions.** A misspelt name in a past tab is suggested as written. Fixing it is a Sheet edit.

## Validator Checklist

- [ ] `assignSlot` is in `ACTIONS` with `write: true`, does not call the guard, writes nothing
  before every check, uses `findSongRow_` and the U1 resolver, and never overwrites a filled cell.
- [ ] Only one slot cell is written, as plain text. `Catalogo` is never opened.
- [ ] `assignSlot` is a public `SetlistRepository` function usable without UI (D-13). Room changes
  only after `Done`, for exactly one slot.
- [ ] The optimistic name comes from `Sending` entries only. A failure reverts it and shows a card.
  Musician models are unchanged (D-15).
- [ ] The instrument is fixed by the slot (no selector). Suggestions come only from the cached jams.
- [ ] Amber only where it was. The copy uses *vos*. Three `wired`. Node is green. Live checks touch
  no real row. No secret or name appears in any output.

## Open Questions (each with a recommendation)

- **C1: fold clearing in?** Clearing is not just "assign empty".
  - Assign must refuse a filled cell (`slot_taken`). Clear must refuse a cell holding a different
    name (`slot_changed`).
  - The filled line needs its own action and a confirmation.
  - The overlay runs the other way.

  Server side, though, it shares everything (finder, U1 resolver, cell writer) and is about 20
  lines plus tests. **Recommended: (b)**, `clearSlot`'s server action in this Part A, so the
  batched deploy covers it and `admin-clear-slot` becomes client-only with no further deploy.
  (a): keep it wholly in `admin-clear-slot`, which then needs its own deploy. (c): fold the whole
  slice in, which makes this one too large.
- **X1: `Otros` (`addExtra`) here?** **Recommended: no.** Extras are not slots, and
  `admin-adjust-lineup`'s notes own them. If the adjust-lineup planner also defers them, add a slice
  `admin-extra-participants` after this one. Their names already feed the suggestions.
- **O1: optimistic or confirmed?** **Recommended: optimistic,** as set-key's O1. Names are entered
  in a burst at the jam, and confirmed display would leave each slot `LIBRE` for about 5 s per
  queued write. Confirmed would draw the slot as before plus a pending line until `Done`.
- **V1: the screen and the copy.** Full screen instead of DESIGN.md's bottom sheet (justified
  above); suggestions in `ESTA JAM` and `JAMS ANTERIORES`; a tap on a suggestion assigns at once;
  names limited to 40 characters; the copy table. **Recommended: as specified.**
