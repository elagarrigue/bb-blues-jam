# Feature Implementation Spec: Set the key on a scheduled song

## Source Feature

- `id`: admin-set-key
- `area`: feature-next-jam
- `depends_on`: admin-add-song-to-setlist (`passing` at planning time, commits 29d3b54 and 2163aab,
  **not accepted**)
- `status`: `not_started` at planning time (6 October 2026)
- `source`: `feature_list.json`. Notes: D-08, D-13. The key belongs to the JamSong, not the Song.

## Readiness

**Implementation waits for `admin-add-song-to-setlist` to be `accepted`.** This spec builds on the
code as committed in 2163aab: `Post.js` `ACTIONS` with the router guard and the write lock,
`requireEditableJam_`, `setlistSpecs_` and the plain-text cell rule; `AdminWriter.write`/`send` and
`WriteOutcome` (W3: no answer touches the store); `SetlistRepository` and
`DefaultSetlistRepository` with its `ids`, `order` and `writes`; `SetlistDao`; the admin layer of
`NextJamPresenter` (`AdminState`, `NextJamAdminUiModel`, `AdminControls.kt`); `readJams` for the
admin; K1 (a), under which a new song starts in the catalog default key. If validation changes any of
them, re-read this spec before starting.

The slice also needs a user deploy of `Post.js`. Approval **B1** decides whether that deploy is
batched with `admin-remove-song-from-setlist`.

Size: one session. This is one mutation, one picker screen and one row action. The read path and
the write plumbing already exist.

## Goal

On Próxima jam, in admin mode, an expanded row ends with **Cambiar tonalidad**. It opens a
full-screen key picker that shows the song's current key and the 24 keys. Tapping a key closes the
picker at once. While the write runs, the row keeps the confirmed key and shows `Cambiando a Bb…`.
Apps Script writes the key into that row's `tono` cell in the jam tab, under the guard and the lock,
and nothing else. The catalog's `tono_default` is never read or written. On success, Room's
`jam_song.key` is updated from the confirmed write, so every screen that reads the cache shows the
new key: the collapsed row, the expanded row and the song detail. On failure, the key is unchanged
and a persistent failure card says why.

The mutation is `SetlistRepository.setKey(jamDate, position, songId, key)` in `:core:data`, usable
with no UI (D-13).

## Non-Goals

- Editing the key from the song detail (`:feature:song-detail`), even though the design prompt
  lists it (V1).
- Changing a catalog `defaultKey`, and any write to `Catalogo` (D-08, D-04).
- Deriving or suggesting a key from any external API (D-08). The picker has no "recommended" key.
- Rendering `b`/`#` as `♭`/`♯`. Keys are drawn as stored, as today.
- Keys outside `[A-G][#b]?m?` (no `maj7`, no modes), and offline queueing (decided: blocked).
- Remove, reorder, lineup, assign, clear and publish: their own slices.
- Deeplinks and the action registry (`action-contract-registry`).
- Pull-to-refresh, or any change to how often musician devices refresh (R1).

## Users And Permissions

- Musician: nothing changes. `SongRowUiModel.admin` is null, so every musician model stays equal to
  today's.
- Admin (`AdminSession.observeIsAdmin()` true): sees the row action and the picker. Apps Script
  authorizes the write, never the flag.
- Debug admin (`bluesjam.debugAdmin`, no passphrase stored): the action is drawn, and `setKey`
  returns `AccessRefused` without sending a request. This is the device path for the failure card.

## Acceptance Scenarios

1. **Set succeeds.** Given the upcoming jam (draft or published) with `crossroads` at position 2 in
   `A`, when the admin picks `Bb`, the picker closes and row 2 still shows `A` with
   `Cambiando a Bb…`. The server sets row 2's `tono` cell to `Bb` (plain text) and writes no other
   cell. Room then holds `key = Bb` for (date, 2, crossroads), and the row shows `Bb` with no status
   line. `readJams`, and for a published jam the GET, return `Bb`.
2. **Catalog untouched.** After scenario 1, `Catalogo` has no write in the Node log, and Room's
   `catalog_song.default_key` for `crossroads` is still `A`.
3. **Fails, no change.** For `AccessRefused`, `Offline`, `Unavailable` or `Rejected(code)`, the row
   keeps `A`, the status line goes away, and a failure card
   `No se pudo cambiar la tonalidad de «Crossroads»` appears after the rows with the outcome's
   message and `Cerrar`. Room and the Sheet are unchanged. The card stays until `Cerrar`, and the
   filter never hides it.
4. **Server validates before writing**, in this order, and each failure writes nothing:
   1. `invalid_date`;
   2. `invalid_position` (not a JSON integer of at least 1);
   3. `invalid_song`;
   4. `invalid_key`;
   5. `unknown_jam` or `duplicate_date`;
   6. `jam_not_editable` (the same rule as `addSong`);
   7. the tab's `missing_header` or `duplicate_header`;
   8. `unknown_position` (no tab, or no row whose whole-number `posicion` equals `position`) or
      `duplicate_position` (more than one such row);
   9. `song_moved` (that row's trimmed `id_tema` is not `songId`: the Sheet changed by hand).
5. **Same key.** In the picker, the current key's cell is marked `actual` and is not clickable, so
   no write is sent.
6. **Ordered.** Writes go in call order: a `setKey` called after an `addSong` waits for it, because
   both share `DefaultSetlistRepository`'s mutexes. Two quick changes on one song end with the later
   key.
7. **Survives the caller.** Closing the picker or leaving the tab does not cancel the write. It runs
   in `DataScope` from its first instruction.
8. **Musician view.** On another device the key shows after that device's next refresh. There is no
   publish step, and the GET reads the tab live (R1).

## Repository Research

Inspected:

- docs and harness: `AGENTS.md`, `PROGRESS.md` (head, latencies), `feature_list.json` (this entry,
  add-song, remove-song, the registry), `.claude/skills/architecture/SKILL.md`;
- design: `DESIGN.md` (Colors, amber rules, Song row, Admin controls as built), the design prompt
  §2 and §3, and the export `docs/design/screens/next-jam-admin.png` (an expanded row with
  `Editar Tono / Formación`);
- domain and backend docs: `docs/domain-model.md` (JamSong, "Changing a key at the last minute"),
  `docs/sheet-schema.md` (Keys, Identifiers), `docs/apps-script-api.md` (POST actions),
  `docs/risks-and-open-questions.md`, `docs/specs/admin-add-song-to-setlist.md`;
- seed: `docs/sheet-seed/*.csv` (keys used: A, B, Bm, C, D, E, G);
- code:
  - `backend/apps-script/src/Post.js` and `Jams.js` (whole files);
  - `Normalize.js` `integerTextCell`, and `test/setlist.test.js` (fakes, `WRITE_OPS`, the
    validation table);
  - `core/model` `Key` and `JamSong`;
  - `core/data` `setlist/*`, `admin/AdminWriter`, `WriteOutcome`, `cache/SetlistDao`,
    `JamEntities`, `JamsDao` (signatures), `jams/SetlistMapper`;
  - `feature/next-jam` `NextJamPresenter`, `NextJamUiModel`, `NextJamScreen` (row), `NextJamCopy`,
    `AdminControls` (head), `AdminControlsDefaults`, `AddSongPresenter` (head);
  - `app/.../navigation/AppRoutes.kt` (signatures).

`docs/user-and-access-model.md` and the bitácora were not reopened. D-08, D-13, D-15 and D-17 are
taken from `AGENTS.md` and the architecture skill.

Findings:

- **Identity.** A JamSong is (`fecha`, `posicion`) (sheet-schema Identifiers; SetlistMapper P4 never
  renumbers and drops shared positions). `buildSetlist` skips blank rows, so it cannot give a sheet
  row index. `setKey_` must scan the tab itself with `mapColumns` and `integerTextCell`.
- `Key` accepts any `[A-G][#b]?m?`, so both `A#` and `Bb` are valid. The Sheet may hold either
  spelling. The real catalog of 100 songs was not inspected, and no agent can read it.
- `jam_song.key` is a plain column, so the update needs **no schema change**. `observeJams`
  re-emits on a `jam_song` write, which the add path already relies on.
- `DefaultSetlistRepository` has one `ids` counter, which keeps failure-card ids unique across
  adds and key changes. The `LazyColumn` keys need that.
- Write latency is about 5 s (one live sample, session 073), so a pending signal is needed.
- `:feature:next-jam`'s amber allowlist is `{key}`. The picker needs no new role.

## Technical Approach

### Server: `backend/apps-script/src/Post.js` (the only src file that changes)

- Add `setKey: { write: true, run: setKey_ }`. The router guard and the lock cover it with no
  router change.
- `setKey_(request, spreadsheet, services)`, with request `{date, position, songId, key}`:
  - Validate in scenario 4's order, reusing `isCalendarDate_`, `SONG_ID_`, `KEY_` and
    `requireEditableJam_`.
  - Read the tab once with `getDataRange()` (display and raw values) and get the columns from
    `mapColumns(date, setlistSpecs_(), header)`.
  - Match rows whose `integerTextCell(raw posicion)` is `/^\d+$/` and numerically equals `position`.
  - Then: `cell = sheet.getRange(r + 1, columns.key + 1)`, `cell.setNumberFormat('@')`,
    `cell.setValue(key)`.
  - Answer `{ok:true}`.
  - It never opens `Catalogo` (D-08).
- New codes: `invalid_position`, `unknown_position`, `duplicate_position` and `song_moved`.
- The deploy check, under L1 (a): extend `checkSetlistWrite_`. After appending the marker row
  (`Bbm`), it sets the marker's key to `F#m` through the **same** cell-writer function `setKey_`
  uses (factor it as `writeKeyCell_(sheet, rowIndex, columns, key)`). It then reads the tab back and
  expects `F#m` with everything else unchanged. If remove-song also extends this check, the steps
  run in commit order. No new action name, so the `Known:` list gains only `setKey`.
- Update the header comment, the `module.exports` list if a constant is needed by tests, and the
  README's marker and `Known:` list.

### `:core:data`

- `cache/SetlistDao`:
  - `@Query("UPDATE jam_song SET key = :key WHERE jam_date = :date AND position = :position AND song_id = :songId") suspend fun updateKey(...): Int`;
  - `@Query("SELECT title FROM jam_song_resolved WHERE jam_date = :date AND position = :position AND song_id = :songId") suspend fun songTitle(...): String?`.
- `setlist/`:
  - `SetKeyOutcome`: `KeySet` (data object) or `NotSet(reason: WriteOutcome)`, where `reason` is
    never `Done`.
  - `KeyChange(id, jamDate, position, songId, title, key, state)`, where `state` is
    `KeyChange.State.Sending` or `Failed(reason)`.
- `SetlistRepository` gains:
  - `suspend fun setKey(jamDate: LocalDate, position: Int, songId: SongId, key: Key): SetKeyOutcome`;
  - `fun observeKeyChanges(): Flow<List<KeyChange>>`.

  `dismiss(id)` now removes a failed add **or** a failed key change; update its KDoc.
- `DefaultSetlistRepository.setKey`, structured exactly like `addSong`:
  1. `scope.async(UNDISPATCHED)`.
  2. Under `order`: take an id from the shared `ids`, resolve the title with
     `setlistDao.songTitle(...) ?: songId.value`, and publish `Sending`.
  3. Queue on `writes` and call `writer.write("setKey", {date, position, songId, key})`.
  4. On `Done`: `setlistDao.updateKey(...)` (0 rows is fine: the next refresh brings it; a
     `SQLException` is swallowed, as `store` does), remove the entry, and answer `KeySet`.
  5. Otherwise: mark the entry `Failed(reason)`, write nothing to Room, and answer `NotSet(reason)`.
- `require(position >= 1)`. `di/DataModule` is unchanged (same constructor).

### `:feature:next-jam`

- `NextJamPresenter`:
  - collects `observeKeyChanges()` and adds `keyChanges` to `AdminState`;
  - `Params(onAddSong, onSetKey: (LocalDate, Int) -> Unit = { _, _ -> }, onOpenSong)`, keeping
    `onOpenSong` last.
- `SongRowUiModel` gains `admin: SongRowAdminUiModel? = null`, built only for the admin, with:
  - `setKeyLabel` ("Cambiar tonalidad");
  - `keyStatus: String?` ("Cambiando a Bb…" for the latest `Sending` change of this date and
    position, else null);
  - `events: EventHandler<Event>` with `Event.OpenKeyPicker`, keyed by date and position.

  The row's `key` is always the cached, confirmed key (O1).
- `NextJamAdminUiModel.failures`: the failed adds and the failed key changes of this jam, sorted by
  `id`. A key failure maps to the existing `AddFailureUiModel` with the title
  `NextJamCopy.keyFailed(title)`. Its message comes from `failureMessage`, extended with
  `unknown_position`, `duplicate_position` and `song_moved` → `JAM_CHANGED` (the add codes are
  unchanged). Rename the type to `WriteFailureUiModel` only if it stays a mechanical rename.
- `NextJamScreen`:
  - `RowHeader` draws `keyStatus` under the title line in both states (`caption`, `textMuted`, a
    polite live region);
  - an expanded admin row appends a `Cambiar tonalidad` text action **after** `Ver detalle del
    tema`. It uses the same component shape (underlined `body` in `text`, 48dp, `Role.Button`), so
    nothing a musician sees moves.

  Sibling slices append their row actions after it.
- The picker: `SetKeyPresenter(JamsRepository, AdminSession, SetlistRepository)` with
  `Params(jamDate, position, onBack, onDone)`, `SetKeyUiModel`, `SetKeyScreen`, `SetKeyCopy` and
  `SetKeyDefaults`.
  - It finds the song in `snapshot.upcoming` by date and position, reading `jam.setlist` only while
    the flag is true.
  - States:
    - `Loading`: only `Volver`;
    - `Gone`: no admin, or no song at that date and position;
    - `Content`: the title, the current key, and two sections of 12 cells.
  - Layout:
    - `Volver` (`BackButton`);
    - the title `Cambiar tonalidad` (`h1`, a heading), then the song title (`songTitle`, two lines);
    - `TONALIDAD ACTUAL` (`caption`, `textMuted`) over the current key (`key` typography, colour
      `key`, the node "Tonalidad A");
    - the sections `MAYORES` and `MENORES` (`caption` headings);
    - each section is a 4-column grid of 3 rows, in chromatic order from C.
  - Cells:
    - `surfaceRaised` with `text` in `songTitle`, `shapes.md`, at least 48dp, `Role.Button`, the
      description `Tonalidad Bb`, and the click label `elegir esta tonalidad`;
    - the cell equal to the current key (exact string) is drawn `surface` with a `border` outline,
      the key in the `key` role, the caption `actual` and the state "actual", and is not clickable;
    - a non-canonical current key (`A#`) marks no cell.
  - A tap launches `setlist.setKey(...)` undispatched and calls `onDone()` once (first tap only, as
    `AddSongPresenter`).
  - The 24 keys are a constant list in `SetKeyDefaults` or `SetKeyCopy` (V1 spelling):
    - majors: C, Db, D, Eb, E, F, F#, G, Ab, A, Bb, B;
    - minors: Cm, C#m, Dm, Ebm, Em, Fm, F#m, Gm, G#m, Am, Bbm, Bm.
- `di/NextJamModule`: `factory { SetKeyPresenter(get(), get(), get()) }`.

### `:app`

- `AppRoutes`: `SET_KEY = "setKey/{jamDate}/{position}"`, `setKey(date, position)`, and
  `parseSetKey`, reusing the song-detail argument parsing.
- `AppNavHost`: a destination beside `addSong`, with the same slide, insets and `RESUMED` back guard.
  `onDone` pops by route.
- `TabsShell(onOpenSetKey)` → `NextJamScreen(onSetKey)`.

Konsist stays at 17 rules with no allowlist change. No Gradle, manifest or Room-version change, and
no new dependency.

## Expected File Changes

- Backend: `backend/apps-script/src/Post.js`, `test/setlist.test.js` (new `setKey` and check
  tests), and `README.md`.
- `:core:data`:
  - `cache/SetlistDao.kt`;
  - `setlist/SetlistRepository.kt`, `DefaultSetlistRepository.kt`, and the new `SetKeyOutcome.kt`
    and `KeyChange.kt`;
  - tests: `SetlistDaoTest`, `DefaultSetlistRepositoryTest`.
- `:feature:next-jam`:
  - `NextJamPresenter.kt`, `NextJamUiModel.kt`, `NextJamScreen.kt`, `NextJamCopy.kt`,
    `di/NextJamModule.kt`;
  - new `SetKeyPresenter.kt`, `SetKeyUiModel.kt`, `SetKeyScreen.kt`, `SetKeyCopy.kt` and
    `SetKeyDefaults.kt`, with a preview;
  - tests: `AdminFakes.kt`, `NextJamAdminTest`, a new `NextJamKeyChangeTest`,
    `SetKeyPresenterTest`, `SetKeyDefaultsTest`, and `NextJamModuleTest`.
- `:app`: `navigation/AppRoutes.kt`, `AppNavHost.kt`, `TabsShell.kt`, and `AppRoutesTest`.

## Visual Design Impact

- The row action is a quiet text action, never amber.
- In the picker, amber appears only on the current key (the `key` role, the same datum as the row's
  key). Every selectable cell is non-amber, so 24 amber cells never become decoration.
- The status line is muted. Failure cards reuse the add-song style.
- `DESIGN.md` gains "Key picker, as built" under Admin controls and an updated screen 2 line.
- Device evidence comes from the Pixel 5 with `demoUpcomingJam` and `debugAdmin`.

## Durable Documentation Impact

- `docs/apps-script-api.md`: a `setKey` section (request, validation order, codes, answer), the
  updated `checkSetlistWrite` steps, the `Known:` list, and `SetlistRepository.setKey` under
  client setlist mutations.
- `docs/sheet-schema.md`: `setKey` writes only the `tono` cell of one row (plain text); keys keep
  the spelling the admin picked.
- `docs/domain-model.md`: setting a key, with the identity (date, position) checked against
  `songId`, and the catalog never touched.
- `docs/risks-and-open-questions.md`: the refresh race applies to keys too; in-memory changes;
  musician devices see a change on their next refresh (R1).
- `.claude/skills/architecture/SKILL.md`: `setKey`, `KeyChange`, `SetKeyPresenter`, the `setKey`
  route, and the rule that row admin actions are appended after `Ver detalle del tema`.
- `DESIGN.md`: as above.
- `AGENTS.md`, `ARCHITECTURE.md`, `CONSTRAINTS.md`, `docs/user-and-access-model.md`: not needed.
  This is one more guarded write and the access model is unchanged.

## Implementation Plan

1. **Server.** Write `setKey_`, `writeKeyCell_` and the check step, then the Node tests:
   - a success test that changes one cell only, as plain text;
   - a table of each code in order with no write;
   - `Catalogo` never written;
   - `published` jams are editable;
   - leading-zero `posicion` `"02"` matches 2;
   - the `x` row is ignored;
   - the extended check.

   Run `node --test backend/apps-script/test/*.test.js` (113 before).
2. **Data.** Write the DAO queries and `setKey`. Test with real in-memory Room through the existing
   harness:
   - success updates the key;
   - every `WriteOutcome` leaves Room unchanged and publishes `Failed`;
   - a row whose `song_id` differs is not updated;
   - `catalog_song` is unchanged;
   - order with `addSong` is kept;
   - caller cancellation does not cancel the write;
   - `dismiss` works for key failures;
   - ids are unique across both kinds.
3. **UI.** Write the presenters, models, screen, picker and routes, with Molecule tests:
   - the musician model is unchanged (`admin` is null);
   - the status line comes from `Sending` changes only;
   - the row key stays the confirmed one while sending;
   - failure cards are merged by id;
   - the picker's states, current cell, single pick and gone cases.
4. Run `./gradlew ktlintFormat`, then `CI=true ./init.sh`.
5. **Failure demonstrations.** Mutate, see the named test fail, restore, and confirm with
   `sha1sum -c`:
   - (a) `setKey_` writes before the `song_moved` check;
   - (b) the repository updates Room on `Rejected`;
   - (c) `updateKey` drops `song_id` from its `WHERE`;
   - (d) the row shows the pending key instead of the confirmed one.
6. **User deploy**, as B1 decides: paste `Post.js` into `Post.gs`, then **Manage deployments → Edit
   → New version**.
7. **Live checks.** The script lives in the scratchpad, sends JSON built with `json.dumps`, and
   prints codes, counts and latencies only, never the URL, the passphrase or names. It never writes
   to a real jam.
   - L1: `{}` gives a `Known:` list that includes `setKey`.
   - L2: `setKey` with a wrong passphrase gives `invalid_passphrase` (this adds 1 to the W2 window).
   - L3: `checkSetlistWrite` gives `ok`; record its latency.
   - L4: `readJams` gives `ok`; print the upcoming date and its song count only.
   - L5: `setKey` probes, none of which can write:
     - a bad date, position `0`, a bad key, `1999-01-01` (`unknown_jam`) and a past jam
       (`jam_not_editable`);
     - with an upcoming jam: position `9999` (`unknown_position`), and an existing position with
       songId `zz-no-existe` (`song_moved`).
8. **Device** (Pixel 5, debug flags on; **never TalkBack or accessibility settings**):
   1. Expand a row and confirm `Cambiar tonalidad` sits after `Ver detalle del tema` and nothing
      above it moved.
   2. In the picker, check the grid, the `actual` cell, `Volver`, rotation, and the layout at font
      scale 2.0 (restore it afterwards).
   3. Tap a key: `Cambiando a …` shows, then the `AccessRefused` card appears, `Cerrar` removes it,
      and the key is unchanged.
   4. With the flag off, the musician row is unchanged.
   5. Capture a `uiautomator dump`.

## Verification Plan

- Node green (count rises by the new tests only); `CI=true ./init.sh` exit 0 with `konsist: wired`
  (17/17), `detekt: wired`, `ktlint: wired`.
- Feature checks:
  - "Persists to the Sheet": the Node success test, plus L3, which runs the same cell writer against
    real Sheets on `_prueba_lista`.
  - "Catalog `defaultKey` untouched": the Node no-`Catalogo`-write test and the JVM `catalog_song`
    test.
  - "Visible on a published jam immediately": a Node test that, on a `PUBLICADA` jam, runs `setKey`
    and then the GET and gets the new key. A JVM test that the row shows it once Room is updated.
    Under R1, musician devices see it on their next refresh.
- The device steps above. The success path on the device needs a stored passphrase, by design, so
  Node and the JVM cover it.

## Evidence To Capture

Record all of the following:

- Node and gate counts;
- the failure demonstrations (a)–(d) with their SHA-1 restores;
- L1–L5 codes, the L3 latency, and the deployed `Post.gs` SHA-1;
- screenshots and the dump;
- a statement that no URL, passphrase or musician name appeared in any output.

## Risks

- **Refresh race.** A refresh read before the write and stored after it shows the old key until the
  next refresh. The Sheet is correct.
- **In-memory entries.** Changes live in memory, so a failure while the process dies is silent.
- **Hand edits.** A hand edit between the admin's read and the tap gives `song_moved` or
  `unknown_position`. That is the intended guard, not last-write-wins on the wrong song.
- **Status line height.** The status line adds height to the row while sending, which pushes the
  rows below down. That is acceptable, because it is the admin's own action.

## Validator Checklist

- [ ] Only `Post.js` changes server-side. `setKey` is in `ACTIONS` with `write: true` and never
  calls the guard. No write happens before every check (Node, per code).
- [ ] Only the `tono` cell of the matched row is written. `Catalogo` is never opened (D-08).
- [ ] `setKey` is a public `SetlistRepository` function usable without UI (D-13). Room is updated
  only after `Done`, and only for the matching (date, position, songId).
- [ ] Musician models are unchanged. Admin controls are appended only (D-15). No feature-to-feature
  import, and Konsist stays at 17 rules with `{key}`.
- [ ] Amber appears only on keys. The copy is as approved, Rioplatense with *vos*.
- [ ] Three `wired`, Node green, live checks recorded, and no secret in the repo or the output.

## User Approvals

Not yet answered. Each question has a recommendation.

- **B1: one deploy for several slices (recommended: a).**
  - (a) Batch the `Post.js` changes of `admin-set-key` (`setKey`) and
    `admin-remove-song-from-setlist` (its own action name, from its spec) into **one** deploy, under
    the same exception as write-auth and add-song: both features may be `in_progress` at once until
    that deploy, and validation still runs per feature.
    1. Implement set-key in full.
    2. Implement remove-song in full, with its server half on top of set-key's `Post.js`.
    3. You deploy once, after add-song is accepted.
    4. One session runs both slices' live checks.
    5. Each is marked `passing` and validated separately.

    Reorder, clear, assign, adjust-lineup and publish are left out, because none of them is planned
    yet and waiting for them would hold these two back.
  - (b) As (a), but plan the server halves of all remaining setlist writes first, so there is one
    deploy for everything. This means the fewest deploys but much later UI, and server code that
    runs ahead of its specs.
  - (c) One deploy per slice.
- **V1: interaction, spelling and copy (recommended: as specified).**
  - Placement: the entry is `Cambiar tonalidad` at the end of the expanded admin row. The picker is a
    full-screen route that closes on a tap, and the current key's cell is not clickable. There is no
    key editing in the song detail yet.
  - Spelling: 24 fixed keys, chromatic from C, in the conventional spelling (fewest accidentals,
    with `F#` and `Ebm` on the ties). An existing key with another spelling is shown as is under
    `TONALIDAD ACTUAL`.
  - Copy (Rioplatense, *vos*):
    - row action: `Cambiar tonalidad`;
    - picker: `Cambiar tonalidad`, `TONALIDAD ACTUAL`, `MAYORES`, `MENORES`, `actual`, and the click
      label `elegir esta tonalidad`;
    - gone state: `Este tema ya no está en la lista` / `Volvé a la próxima jam para ver la actual.`;
    - status line: `Cambiando a Bb…`;
    - failure card: `No se pudo cambiar la tonalidad de «Crossroads»`, with the add-song messages
      and the new codes mapped to `La jam cambió en la planilla. Actualizá y probá de nuevo.`
- **O1: confirmed vs optimistic (recommended: a).**
  - (a) Confirmed: the row keeps the old key with `Cambiando a Bb…` until the server confirms
    (about 5 s). An amber key is never shown before it is true.
  - (b) Optimistic: show the new key at once, muted, and roll back on failure.
- **L1: live proof (recommended: a).**
  - (a) Extend the self-cleaning `checkSetlistWrite` with a key-change step through `setKey`'s own
    cell writer, plus the non-writing probes. No real jam is touched.
  - (b) A separate check action, `checkKeyWrite`.
- **R1: the meaning of "visible immediately" (recommended: accept).** It means no publish step: the
  Sheet and every later read show the key at once. A musician's app shows it on its next refresh:
  when the list is collected and the cache is older than 30 minutes, or on Retry after a failed
  refresh. This is an assumption from the architecture skill's description of `CatalogRepository`
  and `JamsRepository`, not re-verified in code. A faster path, such as pull-to-refresh or a shorter
  stale window on jam day, would be a new feature entry.
