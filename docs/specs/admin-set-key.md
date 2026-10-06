# Feature Implementation Spec: Set the key on a scheduled song

## Source Feature

- `id`: admin-set-key
- `area`: feature-next-jam
- `depends_on`: admin-add-song-to-setlist (`accepted`, 7e56ed1)
- `status`: `not_started` at planning time (6 October 2026; revised the same day after the user's
  answers)
- `source`: `feature_list.json`. Notes: D-08, D-13. The key belongs to the JamSong, not the Song.

## Readiness

**Part A (the server and `:core:data`) builds on remove-song's Part A.** Under the user's B1 (a)
the two slices share one `Post.js` deploy, so implement them in this order:

1. `admin-remove-song-from-setlist` Part A: `removeSong`, `checkSetlistRemove`, `removeSetlistRow_`,
   `SetlistDao.songTitle(date, songId)` and `removeSetlistSong`.
2. This slice's Part A, on top of it.
3. The user's single paste and **New version**.
4. Each slice's live checks.
5. Each slice's Part B, in list order (remove-song first, then set-key), with validation per
   feature.

This is the write-auth/add-song exception again: both features may be `in_progress` until the
deploy.

**Part B builds on remove-song's Part B**: `ExpandedRows` keyed by (date, songId), and
`SongRowUiModel.admin: SongRowAdminUiModel?` with its `removal`. If remove-song is validated with
changes to any of these, re-read this spec first.

It also builds on what add-song accepted in 7e56ed1:

- `Post.js` `ACTIONS`, with the router guard and the write lock;
- `requireEditableJam_`, `setlistSpecs_`, the plain-text cell rule and `checkSetlistWrite_`;
- `AdminWriter.write` and `WriteOutcome` (W3);
- `DefaultSetlistRepository`, with `ids`, `order`, `writes` and `DataScope`;
- the admin layer of `NextJamPresenter`;
- K1 (a): a new song starts in the catalog default key, and this slice changes it.

Size: one feature in two parts, each about one session.

## Goal

On Próxima jam, in admin mode, an expanded row ends with **Cambiar tonalidad**. It opens a
full-screen key picker with the song's current key and 24 keys.

- **The tap.** Tapping a key closes the picker, and **the row shows the new key at once** (O1,
  optimistic). The key is drawn in the amber `key` role, with a muted `Guardando…` line under the
  title.
- **The write.** Apps Script finds the row by `id_tema` and writes only its `tono` cell, under the
  guard and the lock. The catalog's `tono_default` is never read or written (D-08).
- **On success,** Room's `jam_song.key` is updated and the line goes away. The key stays.
- **On failure,** the row **reverts** to the confirmed key and a persistent failure card says why.

The mutation is `SetlistRepository.setKey(jamDate, songId, key)` in `:core:data`, usable with no UI
(D-13).

## Non-Goals

- Editing the key from the song detail. That screen shows only the confirmed (cached) key, with no
  optimistic overlay.
- Writing a catalog `defaultKey` or anything in `Catalogo` (D-08, D-04).
- Suggesting a key from any API (D-08).
- Drawing `♭`/`♯`.
- Keys outside `[A-G][#b]?m?`.
- Offline queueing (decided: blocked).
- The other setlist mutations.
- Deeplinks and the registry.
- Faster musician refresh (R1): that is the new feature `live-refresh-during-jam`.

## Users And Permissions

- **Musician:** nothing changes. `SongRowUiModel.admin` is null, and there is no overlay.
- **Admin** (`observeIsAdmin()` true): sees the row action, the picker and the optimistic key. Apps
  Script authorizes the write.
- **Debug admin** (no passphrase stored): `setKey` returns `AccessRefused` and sends nothing. The
  device then shows the new key, the revert and the failure card.

## Acceptance Scenarios

1. **Optimistic, then saved.** The upcoming jam (draft or published) has `crossroads` in `A`. The
   admin picks `Bb`. The picker closes and the row shows `Bb` with `Guardando…`.
   - The server writes `Bb` to the `tono` cell of the one row whose trimmed `id_tema` is
     `crossroads`, as plain text, and nothing else.
   - Room then holds `Bb` for (date, `crossroads`), and the line goes away. The row shows `Bb`
     throughout.
   - `readJams` returns `Bb`, and so does the GET for a published jam.
2. **Revert on failure.** For `AccessRefused`, `Offline`, `Unavailable` or `Rejected(code)`:
   - the row goes back to `A` with no line;
   - the card `No se pudo cambiar la tonalidad de «Crossroads»` appears after the rows, with the
     outcome's message and `Cerrar`;
   - Room and the Sheet are unchanged;
   - the card stays until `Cerrar`, and the filter never hides it.
3. **Catalog untouched.** `Catalogo` has no write in the Node log, and Room's
   `catalog_song.default_key` is unchanged.
4. **Server validates before writing,** in this order. Each failure writes nothing.
   1. `invalid_date`.
   2. `invalid_song`.
   3. `invalid_key`.
   4. `unknown_jam` or `duplicate_date`.
   5. `jam_not_editable`.
   6. No tab gives `song_not_in_setlist`. Otherwise the tab's `missing_header` or `duplicate_header`.
   7. No row with that `id_tema` gives `song_not_in_setlist`. More than one gives `duplicate_song`.

   These codes and this order match remove-song's.
5. **Same key.** The current key's cell is marked `actual` and is not clickable. Nothing is sent.
6. **Ordered, latest wins.** Writes go in call order behind the shared mutexes, after any queued add
   or remove. Two quick changes on one song draw the later pending key, and the Sheet ends with it.
   If the later change fails and the earlier one succeeded, the row shows the earlier key, which is
   now confirmed.
7. **Survives the caller.** Leaving the picker or the tab never cancels the write: it runs in
   `DataScope`.
8. **Survives a removal renumber.** Because the row is found by `id_tema`, another admin removing an
   earlier song does not misdirect the write.

## Repository Research

These were inspected for the first version: `AGENTS.md`, `PROGRESS.md`, `feature_list.json`, the
architecture skill, `DESIGN.md` (Colors, amber, Song row, Admin controls), the design prompt §2 and
§3, `docs/design/screens/next-jam-admin.png`, `docs/domain-model.md`, `docs/sheet-schema.md`,
`docs/apps-script-api.md`, `docs/risks-and-open-questions.md`, `docs/specs/admin-add-song-to-setlist.md`
and `docs/sheet-seed/*.csv`. On the code side, they were `Post.js` and `Jams.js` (whole),
`Normalize.js` `integerTextCell`, `test/setlist.test.js`, `core/model` `Key`/`JamSong`, `core/data`
`setlist/*`, `AdminWriter`, `WriteOutcome`, `SetlistDao`, `JamEntities`, `JamsDao`, `SetlistMapper`,
`feature/next-jam` (presenter, models, row composables, copy, `AdminControls*`, `AddSongPresenter`)
and `AppRoutes` (signatures).

For this revision I also read `docs/specs/admin-remove-song-from-setlist.md`: its readiness, Parts
A1, A2 and B, B1, R1, and its copy. Remove-song's code does not exist yet, so its names
(`removeSetlistRow_`, `songTitle(date, songId)`, `SongRowAdminUiModel`, `ExpandedRows` keyed by
songId) are taken from that spec, not from code. `docs/user-and-access-model.md` and the bitácora
were not reopened.

Findings:

- **Identity (remove-song R1).** Setlist mutations locate a row by (`fecha`, `id_tema`). `addSong`
  keeps `id_tema` unique in app-built tabs, and a hand-made duplicate is `duplicate_song`. Positions
  can now shift, so they are not used.
- `Key` accepts `[A-G][#b]?m?`, so `A#` and `Bb` are both valid. The real catalog's spellings were
  not inspected; no agent can read it.
- `jam_song.key` is a plain column, so there is **no schema change**. `observeJams` re-emits on a
  `jam_song` write.
- One `ids` counter serves adds, removes and key changes, so the failure-card `LazyColumn` keys stay
  unique.
- Write latency is about 5 s (one live sample, session 073). That is how long an unsaved amber key
  can show.
- `:feature:next-jam`'s amber allowlist is `{key}`, so no new role is needed.

## Technical Approach — Part A: server and `:core:data`

### `backend/apps-script/src/Post.js` (the only src file that changes, after remove-song's A1)

- Extract remove-song's row scan (its step 4) as `findSongRow_(sheet, columns, values, songId)`. It
  returns the 1-based sheet row, or throws `song_not_in_setlist` or `duplicate_song`.
  `removeSetlistRow_` calls it. This is a refactor; remove-song's Node tests must stay green
  unchanged.
- `ACTIONS.setKey = { write: true, run: setKey_ }`, with request `{date, songId, key}`.
  - It validates in scenario 4's order, reusing `isCalendarDate_`, `SONG_ID_`, `KEY_`,
    `requireEditableJam_` and `mapColumns(date, setlistSpecs_(), header)`.
  - Then `writeKeyCell_(sheet, row, columns, key)`: `setNumberFormat('@')`, then `setValue(key)` on
    `(row, columns.key + 1)`.
  - The answer is `{ok:true}`. It never opens `Catalogo`.
- **The deploy check (L1 a).** `checkSetlistWrite_` gains one step. After appending the marker
  (`zz-prueba-lista`, `Bbm`), it calls `findSongRow_` and `writeKeyCell_(…, 'F#m')`. The read-back
  must equal the marker with key `F#m`. That is one real-Sheets proof of the finder and the cell
  writer on the transient `_prueba_lista`.
- There are no new codes; set-key reuses remove-song's. Update the header comment, the README
  marker and the `Known:` list, which gains `setKey`.

### `:core:data`

- `cache/SetlistDao`: `@Transaction suspend fun updateKey(date, songId, key): Boolean`. It updates
  only when the jam's `setlist_state` is `AVAILABLE` and exactly one cached song has that id, with
  `UPDATE jam_song SET key = :key WHERE jam_date = :date AND song_id = :songId`. It reuses
  remove-song's `songTitle(date, songId)`.
- `setlist/`:
  - `SetKeyOutcome` is `KeySet` (a data object) or `NotSet(reason: WriteOutcome)`, never `Done`.
  - `KeyChange(id, jamDate, songId, title, key, state)`, where `state` is `Sending` or
    `Failed(reason)`.
- `SetlistRepository` gains `suspend fun setKey(jamDate, songId: SongId, key: Key): SetKeyOutcome`
  and `fun observeKeyChanges(): Flow<List<KeyChange>>`. `dismiss(id)` now covers failed key changes
  too.
- `DefaultSetlistRepository.setKey` has the same shape as `addSong` and `removeSong`:
  1. `scope.async(UNDISPATCHED)`.
  2. Under `order`: take an id from `ids`, resolve the title (`songTitle` or `songId.value`), and
     publish `Sending`.
  3. Queue on `writes` and call `writer.write("setKey", {date, songId, key})`.
  4. On `Done`: **first** `updateKey` (a `SQLException` is swallowed), **then** remove the entry,
     and answer `KeySet`. Updating the cache first keeps the confirmed key in place before the
     overlay goes.
  5. Otherwise: mark the entry `Failed(reason)`, leave Room alone, and answer `NotSet(reason)`.
  - `song_not_in_setlist` changes nothing locally. The row reverts, and the card says the list
    changed.
- `di/DataModule`: unchanged.

## Technical Approach — Part B: UI (after remove-song's Part B)

- `NextJamPresenter`:
  - It collects `observeKeyChanges()` into `AdminState.keyChanges`.
  - `Params(onAddSong, onSetKey: (LocalDate, SongId) -> Unit = { _, _ -> }, onOpenSong)`, with
    `onOpenSong` kept last.
- **The overlay (O1).** For the admin, a row whose (date, songId) has `Sending` entries draws the key
  of the **latest** of them (highest id) as `key`, with `keyDescription` to match.
  `SongRowAdminUiModel` gains:
  - `keyStatus: String?` (`Guardando…`, only while overlaid);
  - `setKey: SetKeyActionUiModel(label, events)` (`Event.Open`, keyed by date and songId).

  A `Failed` entry never overlays, so failure **is** the revert. Musicians never get an overlay.
  The instrument filter is unaffected; the key does not filter.
- **Failures.** `NextJamAdminUiModel.failures` merges failed adds, removes and key changes for this
  jam, sorted by id.
  - The title is `NextJamCopy.keyFailed(title)`.
  - `failureMessage` adds two codes: `song_not_in_setlist` → `JAM_CHANGED`, and `duplicate_song` →
    remove-song's duplicate message (shared constant).
- **The screen.**
  - `RowHeader` draws `keyStatus` under the title line in both states (`caption`, `textMuted`, a
    polite live region).
  - The expanded admin panel draws `Cambiar tonalidad` after `Ver detalle del tema` and **before**
    remove-song's `Quitar de la lista`. It is the same underlined `body`/`text` 48dp action, never
    amber.
  - Nothing a musician sees moves.
- **The picker.** `SetKeyPresenter(JamsRepository, AdminSession, SetlistRepository)` with
  `Params(jamDate, songId, onBack, onDone)`, plus `SetKeyUiModel`, `SetKeyScreen`, `SetKeyCopy` and
  `SetKeyDefaults`.
  - It finds the song in `snapshot.upcoming.setlist` by songId, only while the flag is true.
  - States:
    - `Loading`: back only.
    - `Gone`: not admin, or no such song.
    - `Content`.
  - Layout:
    - `Volver`, then the title `Cambiar tonalidad` (`h1`), then the song title (`songTitle`).
    - `TONALIDAD ACTUAL` over the current key (`key` typography, colour `key`, the node
      "Tonalidad A"). This is the overlaid pending key if one exists, so the picker agrees with the
      row.
    - The sections `MAYORES` and `MENORES`.
  - Grid: 4 columns × 3 rows, chromatic from C.
    - Majors: C, Db, D, Eb, E, F, F#, G, Ab, A, Bb, B.
    - Minors: Cm, C#m, Dm, Ebm, Em, Fm, F#m, Gm, G#m, Am, Bbm, Bm.
  - Cells:
    - Each cell is `surfaceRaised`/`text` in `songTitle`, `shapes.md`, at least 48dp,
      `Role.Button`, described `Tonalidad Bb`, with the click label `elegir esta tonalidad`.
    - The cell equal to the current key (exact string) is drawn `surface` with a `border` outline,
      `key` amber and the caption `actual`, and is not clickable.
    - A non-canonical key (`A#`) marks no cell.
  - The first tap launches `setKey` undispatched and calls `onDone()`. Later taps are ignored.
- `di/NextJamModule`: `factory { SetKeyPresenter(get(), get(), get()) }`.
- `:app`:
  - `AppRoutes.SET_KEY = "setKey/{jamDate}/{songId}"`, with `setKey(date, songId)` and
    `parseSetKey` (an ISO date and `SongId.parseOrNull`; anything else is null).
  - An `AppNavHost` destination beside `addSong`: the same slide, insets and `RESUMED` guard.
    `onDone` pops by route.
  - `TabsShell(onOpenSetKey)` → `NextJamScreen(onSetKey)`.
- Konsist stays at 17 rules with `{key}`. No Gradle, manifest, Room-version or dependency change.

## Expected File Changes

- **Part A:**
  - `backend/apps-script/src/Post.js`, `test/setlist.test.js` (set-key tests and the extended
    check test), `README.md`;
  - `core/data/.../cache/SetlistDao.kt`, `setlist/SetlistRepository.kt`,
    `DefaultSetlistRepository.kt`, and the new `SetKeyOutcome.kt` and `KeyChange.kt`;
  - tests: `SetlistDaoTest` and `DefaultSetlistRepositoryTest`.
- **Part B:**
  - `feature/next-jam/.../NextJamPresenter.kt`, `NextJamUiModel.kt`, `NextJamScreen.kt`,
    `NextJamCopy.kt`, `di/NextJamModule.kt`;
  - new `SetKey{Presenter,UiModel,Screen,Copy,Defaults}.kt` and a preview;
  - tests: `AdminFakes.kt`, a new `NextJamKeyChangeTest`, `SetKeyPresenterTest`,
    `SetKeyDefaultsTest`, `NextJamModuleTest`;
  - `app/.../navigation/AppRoutes.kt`, `AppNavHost.kt`, `TabsShell.kt`, `AppRoutesTest`.

## Visual Design Impact

An unsaved key is drawn in amber for up to about 5 s. That is the user's choice (O1). Its only signal
is the muted `Guardando…` line. Everywhere else amber stays on keys alone:

- the picker's selectable cells are non-amber;
- only the current key is amber;
- the row action is a quiet text action.

`DESIGN.md` gains "Key picker, as built" and the optimistic rule under Admin controls.

## Durable Documentation Impact

- `docs/apps-script-api.md`:
  - the `setKey` section (request, order, codes, `{ok:true}`);
  - the `findSongRow_` note;
  - the extended `checkSetlistWrite`;
  - the `Known:` list;
  - `SetlistRepository.setKey` under client mutations.
- `docs/sheet-schema.md`: `setKey` writes only the `tono` cell of the row found by `id_tema`, as
  plain text, in the spelling the admin picked.
- `docs/domain-model.md`: setting a key, with the catalog never touched.
- `docs/risks-and-open-questions.md`: the risks below.
- `.claude/skills/architecture/SKILL.md`:
  - `setKey`, `KeyChange` and the overlay rule ("an optimistic value comes from `Sending` entries
    only; failure is the revert");
  - `SetKeyPresenter` and the `setKey` route;
  - the order of the row admin actions.
- `DESIGN.md`: as above.
- `AGENTS.md`, `ARCHITECTURE.md`, `CONSTRAINTS.md`, `docs/user-and-access-model.md`: not needed.

## Implementation Plan

1. **Part A, server.** Extract `findSongRow_`; remove-song's tests stay green. Write `setKey_`,
   `writeKeyCell_` and the check step, with Node tests:
   - success writes one cell only, as plain text;
   - the validation table writes nothing for any code;
   - `Catalogo` is never written;
   - on a `PUBLICADA` jam, `setKey` then the GET returns the new key;
   - a renumbered tab: the row is found by id after another row is removed;
   - the extended check.

   Run `node --test backend/apps-script/test/*.test.js`.
2. **Part A, data.** Test on in-memory Room:
   - `Done` updates only the matching (date, songId);
   - a duplicate cached id or a non-available setlist updates nothing;
   - every failure leaves Room unchanged and publishes `Failed`;
   - `catalog_song` is unchanged;
   - the cache is updated before the entry is removed (observe both);
   - order with `addSong` and `removeSong`;
   - caller cancellation;
   - `dismiss`;
   - unique ids.
3. **Part A failure demonstrations.** Mutate, see the named test fail, restore, and confirm the
   restore with `sha1sum -c`:
   - (a) `setKey_` writes before the `duplicate_song` check;
   - (b) `updateKey` drops `song_id` from its `WHERE`;
   - (c) the repository updates Room on `Rejected`.
4. Run `CI=true ./init.sh`. **Stop here for the batched deploy** (B1): the user pastes `Post.js` and
   chooses **New version**.
5. **Live checks.** The script lives in the scratchpad, builds its body with `json.dumps`, and prints
   only codes, counts and latencies, never the URL, the passphrase or names. **Never call `setKey`
   with a real song id.**
   - L1: `{}` gives a `Known:` list that includes `setKey`, `removeSong` and `checkSetlistRemove`.
   - L2: `setKey` with a wrong passphrase gives `invalid_passphrase` (adds 1 to W2).
   - L3: `checkSetlistWrite` gives `ok`; record its latency.
   - L4: `readJams` gives `ok`; print only the upcoming date and its song count.
   - L5: probes that cannot write:
     - a bad date, a bad song id, a bad key (`H`);
     - `1999-01-01` gives `unknown_jam`;
     - a past jam gives `jam_not_editable`;
     - the upcoming date with `zz-no-existe` gives `song_not_in_setlist` (or `unknown_jam` if no
       jam is upcoming).
6. **Part B.** Molecule tests:
   - the musician model is unchanged;
   - a `Sending` change overlays the row key and adds `Guardando…`;
   - the latest of two `Sending` changes wins;
   - **`Failed` reverts to the cached key** and adds a card;
   - after `Done` the cached key shows with no status;
   - failures are merged by id;
   - picker states, the `actual` cell (the overlaid key), single pick, gone.

   Then `AppRoutesTest`.
7. **Part B failure demonstration.** (d) The overlay also applies to `Failed` entries, so the row
   keeps the unsaved key after a failure: the "reverts on failure" test fails. Restore and check
   the SHA-1.
8. **Gate.** `CI=true ./init.sh`.
9. **Device check.** Pixel 5 with `demoUpcomingJam` and `debugAdmin`. **Never TalkBack or
   accessibility settings.**
   1. Expand a row: `Cambiar tonalidad` sits after `Ver detalle del tema` and before
      `Quitar de la lista`.
   2. In the picker, check the grid, the `actual` cell, back, rotation, and font scale 2.0 (restore
      it afterwards).
   3. Tap `Bb`. The row shows `Bb` with `Guardando…`, then reverts to the old key with the
      `AccessRefused` card. Take screenshots of both moments, then tap `Cerrar`.
   4. With the flag off, the musician view is unchanged.
   5. Capture a `uiautomator dump`.

## Verification Plan

- Node is green. `CI=true ./init.sh` exits 0 with `konsist: wired` (17/17), `detekt: wired` and
  `ktlint: wired`.
- "Persists to the Sheet": the Node success test, plus L3 against real Sheets.
- "Catalog `defaultKey` untouched": the Node no-`Catalogo`-write test and the JVM `catalog_song`
  test.
- "Visible on a published jam immediately": the Node GET-after-`setKey` test, and the JVM tests of
  the overlay and the cache. Musician devices see the key on their next refresh (R1; faster refresh
  is `live-refresh-during-jam`).
- The success path on a device needs a stored passphrase, by design. Node and the JVM cover it.

## Evidence To Capture

- Node and gate counts.
- Failure demonstrations (a)–(d), each with its SHA-1 restore.
- L1–L5 codes, the L3 latency and the deployed `Post.gs` SHA-1.
- The device screenshots (optimistic and reverted) and the dump.
- A statement that no URL, passphrase or name appeared in any output.

## Risks

- **An unsaved amber key** (the user chose this, O1). For up to about 5 s, or longer on a slow
  network, the admin's row shows a key the Sheet may never get. If the admin reads it aloud before
  the revert, musicians may play in the wrong key. Mitigations: the `Guardando…` line, the revert,
  and the persistent card. Only the admin's own device shows it.
- **Overlay hand-off.** Room is updated before the entry is removed. Room's flow re-queries
  asynchronously, so one frame of the old key is possible in theory. Observe it on the device; the
  JVM test checks the order.
- **Refresh race.** A refresh read before the write but stored after it shows the old key until the
  next refresh. The Sheet is correct.
- **In-memory entries.** A failure while the process dies is silent. A pending overlay dies with
  the process, and the cache then shows the key the Sheet last confirmed.
- **Status line.** `Guardando…` adds height to the row while sending.
- **Song detail.** It shows the confirmed key only, so for a few seconds it can disagree with the
  admin's row.

## Validator Checklist

- [ ] Only `Post.js` changes server-side. `setKey` is in `ACTIONS` with `write: true` and does not
  call the guard. No write happens before every check. The row is found by `id_tema` with
  remove-song's finder.
- [ ] Only the matched `tono` cell is written. `Catalogo` is never opened (D-08).
- [ ] `setKey` is a public `SetlistRepository` function usable without UI (D-13). Room changes only
  after `Done`, for exactly one (date, songId).
- [ ] The optimistic key comes only from `Sending` entries. A failure reverts it and shows a card.
  Musician models are unchanged (D-15).
- [ ] Amber appears only on keys. The copy is as approved. Admin actions are only appended.
- [ ] Three `wired`. Node is green. Live checks are recorded with no real jam touched. No secret
  appears anywhere.

## User Approvals

Answered by the user on 6 October 2026:

- **B1 (a):** one batched deploy with remove-song, with remove-song's server half first and
  set-key's on top.
- **V1:** as specified.
- **O1, optimistic:** the alternative, not the recommendation. The new key shows at once in the
  amber `key` role and reverts if the write fails.
- **L1 (a):** extend `checkSetlistWrite`.
- **R1:** accepted as the reading for this slice. The user asked for a new feature,
  `live-refresh-during-jam`: refresh every 30 seconds from 30 minutes before the show until 4 hours
  after, plus pull-to-refresh.
- **C2** (asked after the revision): the status line while saving is `Guardando…` (recommended).

Also, from remove-song's R1: find the row by `id_tema`, not by position. The spec above reflects all
of these.

**Open, new in this revision:**

- **C2: the status line under optimistic display (recommended: `Guardando…`).** V1 approved
  `Cambiando a Bb…` for the confirmed design. Next to a row that already shows `Bb`, that line
  repeats the key and reads as if nothing happened yet. `Guardando…` says only that the save is
  pending.

The questions as originally asked (B1, V1, O1, L1, R1) are in git history (e82f084).
