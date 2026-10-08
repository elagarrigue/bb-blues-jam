# Feature Implementation Spec: Reorder songs in the setlist

## Source Feature

- `id`: admin-reorder-songs
- `area`: feature-next-jam
- `depends_on`: admin-add-song-to-setlist (`accepted`). Also builds on the accepted
  `admin-remove-song-from-setlist`, `admin-set-key`, `apps-script-write-auth` and
  `live-refresh-during-jam`.
- `status`: `not_started` at planning time (8 October 2026)
- `source`: `feature_list.json`, with user decisions recorded here (8 October 2026): M1 = visible
  `Subir`/`Bajar` buttons; O2 = move optimistically and revert with a failure notice; B2 = one
  shared deployment for reorder, publish, adjust-lineup and assign after app implementation is
  complete. D-13; `appendSetlistRow_` makes 26 separate cell calls, so batch range writes where
  this slice touches rows.

## Readiness

Ready for implementation. The user selected M1 (visible `Subir` and `Bajar` buttons), O2
(optimistic row movement with rollback and a failure notice), and B2 (one shared deployment after
app implementation is complete).

**Server action name: `moveSong`**, which moves one song to a new position. It comes with the
self-cleaning deploy check `checkSetlistMove`.

The server and app portions are both implemented and validated locally before any deployment.
Deployment and live checks are explicitly deferred by the user until app implementation is
complete. At that point, include this feature's server change in one shared deployment with
`admin-publish-setlist`, `admin-adjust-lineup` and `admin-assign-musician`; run the batch's live
checks together then. Do not make a per-feature deployment or live request during this feature.
Size: one feature in two local implementation parts (A: `Post.js`, Node and `:core:data`; B:
`:feature:next-jam`), about one session each. No split is needed: there is no new screen, no schema
change and no new read path.

## Goal

On Próxima jam, in admin mode, an expanded row ends with a move block:

- the line `Posición 3 de 12`;
- two 48dp actions side by side, **`Subir`** and **`Bajar`**.

Tapping one moves the song one place in the full list:

- **Optimistic display (O2).** The row redraws at its new place at once, with `Guardando…` under
  the title. It stays under the admin's thumb.
- **The server write.** Apps Script finds the row by `id_tema`, moves it to the requested position
  and rewrites `posicion` so the tab stays 1..n. The writes are batched, and no intermediate state
  ever holds a duplicate position.
- **On success,** Room mirrors the new order and the status line goes.
- **On failure,** the row goes back to its confirmed place and a persistent card says why.

The mutation is `SetlistRepository.moveSong(jamDate, songId, toPosition)` in `:core:data`, usable
with no UI (D-13). Its target is an **absolute position**, so the phase 2 assistant can say "put
Crossroads first", and a repeated or replayed call has the same result.

## Non-Goals

- No drag handle, no swipe, no long-press drag (M1 selected visible buttons).
- No "move to position…" picker, and no multi-song or bulk reordering.
- No sorting by key or title.
- No reorder of a past jam (D-04). No offline queue (blocked, as add-song decided).
- No undo beyond moving back.
- Publish, lineup, assign and clear are their own slices. So are deeplinks and the registry.
- Musicians get no faster propagation than `live-refresh-during-jam` already gives.
- No change to how the song detail is addressed (it uses a position; see Risks).

## Job Story

When the band decides to swap two songs, before the jam or during it,
I want to move a song up or down from my phone with one hand,
so the order musicians read is the order we actually play.

## Users And Permissions

- **Musician:** nothing visible changes. `SongRowUiModel.admin` stays null and there is no overlay.
  A new order reaches musicians on their next refresh (every 30 s inside the live window).
- **Admin** (`observeIsAdmin()` true): gets the move block. Apps Script authorizes the write through
  the router guard; the flag authorizes nothing. A refused write keeps admin mode (W3).
- **Debug admin** (`bluesjam.debugAdmin`, no passphrase stored): `moveSong` returns `AccessRefused`
  with no request sent. This is the device path for the optimistic move, the revert and the card.

## Acceptance Scenarios

1. **Move up, contiguous tab.** The tab has rows in sheet order 1 `crossroads`, 2 `hoochie`,
   3 `thrill`, 4 `pride`. `moveSong(date, thrill, 1)` writes the `posicion` cells of the first
   three data rows (sheet rows 2–4) in
   **one** `setNumberFormat('@')` and **one** `setValues` call: `thrill` 1, `crossroads` 2,
   `hoochie` 3. `pride` is untouched.
   - The answer is `{ok:true, position:1}`.
   - Slots, extras, keys and every other cell are unchanged. `buildSetlist` reads the new order.
2. **Move down.** On the same tab, `moveSong(date, crossroads, 3)` gives `hoochie` 1, `thrill` 2,
   `crossroads` 3, `pride` 4.
3. **Scattered rows (park path).** The tab's sheet order is not its `posicion` order, so the changed
   rows are not one contiguous block.
   - The moved row is first parked at `max + 1`.
   - Rows whose position decreases are written in ascending target order.
   - Rows whose position increases are written in descending target order.
   - The moved row is written to its target last.
   - Consecutive sheet rows that are consecutive in that order share one call.
   - The Node fake checks after **every** write call that no whole `posicion` appears twice.
4. **Clamp and no-op.** A `toPosition` larger than n moves the song last and answers its real
   position. A move to the song's own place in a tab already numbered 1..n writes nothing and
   answers `{ok:true, position}`.
5. **Gaps close.** On a tab numbered 1, 2, 4, 5 (a partial remove), any `moveSong` renumbers the
   rows with a valid `posicion` to 1..4.
6. **Validation before any write,** in this order. Each failure leaves the spreadsheet untouched.
   1. `invalid_date`.
   2. `invalid_song`.
   3. `invalid_position`: `toPosition` is not a JSON integer of at least 1. `"2"`, `1.5` and `0`
      are refused.
   4. `unknown_jam` or `duplicate_date`.
   5. `jam_not_editable`.
   6. No tab gives `song_not_in_setlist`.
   7. `missing_header` or `duplicate_header`.
   8. No row with that `id_tema` gives `song_not_in_setlist`.
   9. `duplicate_song`.
   10. **`unordered_setlist`** (new): the moved row's `posicion` is not a whole number of at least
       1, or a whole `posicion` is on more than one row. The read path drops such rows, and
       guessing an order would reveal them.

   `Catalogo` is never opened.
7. **Optimistic, then saved.**
   - The admin taps `Subir` on `thrill` (shown at 3). The row is drawn at 2 at once, with
     `Guardando…` and `Posición 2 de 4`. Positions renumber in the displayed order.
   - On `Done`, Room holds the new order: songs, slots and extras moved together. Then the entry is
     removed, and the row stays at 2 throughout.
8. **Revert on failure.** For `AccessRefused`, `Offline`, `Unavailable` or `Rejected(code)`, the row
   goes back to its cached place with no status line. The card `No se pudo mover «Thrill Is Gone»`
   appears with the reason and `Cerrar`, and stays until closed. Room and the Sheet are unchanged.
9. **Rapid taps.** Two `Subir` taps from the same model move the song two places, because the
   target is computed from the displayed order at event time. Writes go in call order behind the
   shared `order`/`writes` mutexes. If the first write fails and the second succeeds, the song ends
   where the second put it, and one card shows.
10. **Edges.**
    - On the displayed first row, `Subir` is drawn disabled: `textMuted`, not clickable, read as
      disabled.
    - On the last row, `Bajar` is drawn disabled in the same way.
    - With one song, both are disabled.
    - The layout never changes between these states.
11. **Refresh mid-write.** A live refresh lands after the server wrote but before the answer. The
    cache then already has the new order, and the overlay is applied again. Moving to an absolute
    position the song already holds changes nothing, so the row does not jump.
12. **Another admin.** Writes are serialized by the script lock, and the row is found by id. A
    removal or move by another admin never misdirects this one. The target applies to the Sheet's
    order at write time, clamped to n. The last write wins. Each device sees the other's change on
    its next refresh.
13. **Interplay with remove.** While a removal of the song is sending, the move block is not drawn,
    and `Quitando…` stays. While its confirmation is open, the move block stays.
14. **Filter.** Moves are in the full list. With an instrument filter active, the row may move past
    hidden rows; `Posición x de n` always counts the full list.

## Repository Research

Inspected:

- Docs: `AGENTS.md`, `PROGRESS.md` (head and greps), `feature_list.json` (this entry and the four
  batch slices), `DESIGN.md` (Admin controls as built, Dos and Don'ts, Open Design Questions), the
  design prompt §2 and its rules, `docs/sheet-schema.md` (writes, mapper rules),
  `docs/apps-script-api.md` (POST, row identity, `removeSong`), `docs/risks-and-open-questions.md`
  (remove-song risks), architecture skill (greps), `CONTEXT.md` (grep).
- Specs: `admin-remove-song-from-setlist.md` and `admin-set-key.md` (whole), and the head and greps
  of `live-refresh-during-jam.md`.
- Code: `Post.js` (whole); `SetlistRepository`, `DefaultSetlistRepository`, `SetlistRemovals`,
  `SetlistKeyChanges`, `KeyChange`, `SetlistDao`, `JamEntities` (keys and FKs); `NextJamPresenter`,
  `KeyChangeOverlay`, `ExpandedRows`, `AdminControls`, `AdminControlsDefaults`, `FailureMessages`,
  `NextJamCopy` (constants), `NextJamUiModel` (row and admin models), `NextJamScreen` (list keys).
- Not opened: the bitácora, the Node test bodies, the `SetlistMapper` body, `DataModule`, and the
  `:app` debug repository.

Findings:

- **Row identity.** Mutations find rows by `findSongRow_`. `wholePosition_` and `positionRuns_`
  already exist; `positionRuns_` is shaped for removal (−1 shifts), so the move needs its own run
  builder.
- **Song row keys.** `NextJamScreen` keys song rows by `position` (`items(setlist.rows, key =
  { it.position })`). A move changes positions, so the key must become the song: no item animation
  or scroll anchoring works otherwise. Ids can repeat in a hand-edited tab, so the key needs a
  fallback.
- **Song detail.** `onOpenSong(date, position)` addresses the detail by cached position.
- **Room positions.** Room keys are (`jam_date`, `position`) with cascade-only FKs.
  `removeSetlistSong` shows the delete-and-reinsert pattern.
- **Optimistic pattern.** Set-key's optimistic pattern (entries `Sending`/`Failed`, cache first
  then entry removal, `pendingKey`) is the model to follow.
- **Write latency.** About 5 s per write was measured live.
- **`appendSetlistRow_`** makes 2 calls per mapped column (26). This slice batches it; its deploy
  check appends four markers.

## Technical Approach — Part A (server and `:core:data`)

### `backend/apps-script/src/Post.js` (the only src file that changes)

1. `ACTIONS.moveSong = { write: true, run: moveSong_ }` takes the request
   `{date, songId, toPosition}`.
   - It validates in scenario 6's order, reusing `isCalendarDate_`, `SONG_ID_`,
     `requireEditableJam_`, `mapColumns`, `setlistSpecs_` and `findSongRow_`.
   - It then calls `moveSetlistRow_(sheet, songId, toPosition)` and answers `{ok:true, position}`.
2. **`moveSetlistRow_`** (shared with the check):
   1. Read the display and raw values.
   2. Find the row.
   3. Collect the data rows with a whole `posicion` (`wholePosition_`). If the moved row has none,
      or a value repeats, throw `unordered_setlist`.
   4. Sort by `posicion`, take the moved row out, and insert it at `min(toPosition, n) − 1`.
      Targets are 1..n in that order.
   5. `changed` is the set of rows whose target differs from their position. If it is empty, no
      write.
   6. If the changed rows' sheet numbers form one contiguous block, write that block in one call
      pair (`setNumberFormat(PLAIN_TEXT_FORMAT)` and `setValues`), so there is no intermediate state.
   7. Otherwise, use the park sequence from scenario 3. Each run of rows is consecutive in
      processing order and on consecutive sheet rows, in either direction, and costs one call pair.
   8. KDoc carries the argument for why this never duplicates: non-moved rows keep their relative
      order, so a row whose position decreases never targets a value held by one whose position
      increases. The parked value `max + 1` is above every target.
3. **`checkSetlistMove`** is `{ write: true }`, with no repository function, and no read ever lists
   it.
   1. On `_prueba_lista`, delete any leftover tab and create the tab.
   2. Append `zz-prueba-uno` through `zz-prueba-cuatro` at 1–4.
   3. Run `moveSetlistRow_(cuatro → 1)`. This is the contiguous path; the order is cuatro, uno,
      dos, tres.
   4. Run `moveSetlistRow_(dos → 1)`. The changed rows are sheet rows 2, 3 and 5, so this is the
      park path.
   5. Read back with `buildSetlist`, and delete the tab in `finally`.
   6. Require exactly dos 1, cuatro 2, uno 3, tres 4. Otherwise throw (`internal_error`).
4. **Batch `appendSetlistRow_`** (from the feature notes): one `setNumberFormat` and one `setValues`
   per run of contiguous mapped columns. An app-built tab is one run of 13 columns. Unmapped admin
   columns are never touched. `addSong` and every existing check keep their Node tests unchanged.
5. **Housekeeping:** the new codes `invalid_position` and `unordered_setlist`, with English messages
   and no passphrase. Update the header comment, the `Known:` list, the `module.exports` (unchanged
   unless tests need more) and the README marker and verify table.

### `:core:data`

- `SetlistRepository` gains:
  - `suspend fun moveSong(jamDate, songId: SongId, toPosition: Int): MoveSongOutcome`;
  - `fun observeMoves(): Flow<List<SetlistMove>>`.

  `dismiss(id)` now also covers failed moves (one `ids` counter).
- New types:
  - `MoveSongOutcome`: `Moved` or `NotMoved(reason: WriteOutcome)`, never `Done`.
  - `SetlistMove(id, jamDate, songId, title, toPosition, state)`, with `Sending` or
    `Failed(reason)`.
- New `SetlistMoves` (internal), shaped like `SetlistKeyChanges`. `DefaultSetlistRepository.moveSong`
  has set-key's shape (`async(UNDISPATCHED)`, `order` and then `writes`) and runs
  `writer.write("moveSong", {date, songId, toPosition})`.
  - A `toPosition` below 1 is published `Failed(Rejected("invalid_position"))` with no request.
  - `Done`: first `SetlistDao.moveSetlistSong(date, songId, toPosition)` (a `SQLException` is
    swallowed), then remove the entry; the outcome is `Moved`.
  - Anything else: the entry turns `Failed` and Room is untouched.
- `SetlistDao.moveSetlistSong(date, songId, toPosition): Boolean` is one `@Transaction`, and acts
  only when the setlist is `AVAILABLE` and exactly one cached song has the id.
  1. Take the songs in position order and apply the same move, with the clamp.
  2. Renumber 1..n.
  3. For the songs whose position changes, delete their slots, extras and songs explicitly, then
     reinsert them at the new positions, parents first.
  - There is no schema or version change.
- `DataModule` is unchanged.

## Technical Approach — Part B (`:feature:next-jam`)

- **`MoveOverlay.kt`** (like `KeyChangeOverlay.kt`) holds
  `List<SetlistMove>.displayOrder(date, songs: List<JamSong>): List<JamSong>`. It applies the
  `Sending` moves of `date` in id order, each taking the song out and inserting it at its clamped
  target. `Failed` entries never apply, so a failure is the revert. Musicians never get an overlay.
- **`NextJamPresenter`**:
  - It collects `observeMoves()` into `AdminState.moves`.
  - For the admin, the rows follow `displayOrder`. `position` and `positionLabel` come from the
    displayed index; `openDetail` keeps the song's **cached** position.
  - One handler `onMove(date, songId, direction)` computes the target from the **current** snapshot
    and moves at call time, as `displayed ∓ 1`. It ignores an edge and launches `moveSong`
    undispatched. This works because handlers keyed by song may be stale.
- **Row model**:
  - `SongRowUiModel` gains `rowKey: String`: the song id when it is unique in the setlist, else
    `"p<position>"`. Musicians get the field too, with no visible change.
  - `SongRowAdminUiModel` gains `move: MoveUiModel?` (null while a removal of the song is sending)
    with `positionLine`, `up` and `down`. Each is a `MoveActionUiModel(label, clickLabel, enabled,
    events)`.
  - `keyStatus` is renamed `savingStatus`: `Guardando…` while a key change **or** a move of the row
    is sending.
- **Failures**: `failures(date)` merges the failed moves by id. The title is
  `NextJamCopy.moveFailed(title)`. `moveFailureMessage`:
  - `song_not_in_setlist` → `JAM_CHANGED`;
  - `duplicate_song` → `DUPLICATE_SONG`;
  - `unordered_setlist` → `UNORDERED_SETLIST`;
  - anything else → `failureMessage`.
- **Screen**:
  - `items(rows, key = { it.rowKey })`, with `Modifier.animateItem()` on song rows.
  - Admin row actions, in order: `Cambiar tonalidad`, the move block, then the removal (still last).
  - The move block has the position line (`caption`, `textMuted`, a polite live region, so a screen
    reader hears the new place), then a `Row` of two `weight(1f)` 48dp underlined `body` text
    actions in `text`. A disabled one is in `textMuted`, uses `clickable(enabled = false)` and has
    the `Role.Button` role. Colours come from `AdminControlsDefaults.move()`. **No amber.**
- **Thumb anchoring**: the moved row stays where the thumb is.
  1. On a move tap, the screen records the row's `rowKey` and its viewport offset from
     `listState.layoutInfo`.
  2. When that key's index changes, it scrolls by the offset difference (`scrollBy`), or calls
     `scrollToItem` if the row left the viewport.
  3. If this proves unreliable on the device, keep the row visible, record it in the evidence, and
     flag it to the validator. Do not drop it silently.
- No `:app`, Konsist, Gradle, manifest or dependency change. Konsist stays at 17 rules with the
  `{key}` amber allowlist.

### Copy (Rioplatense, vos)

- The actions are `Subir` and `Bajar`. Their click labels are `subir un lugar` and
  `bajar un lugar`.
- The position line is `Posición 3 de 12`. The status line is `Guardando…`, reused.
- The failure title is `No se pudo mover «…»`.
- The new message for `UNORDERED_SETLIST` is
  `Las posiciones de la planilla están desordenadas. Corregilas ahí y probá de nuevo.`

## Expected File Changes

- **Part A**:
  - `backend/apps-script/src/Post.js`, `backend/apps-script/README.md`;
  - the new `test/move.test.js`, or `setlist.test.js` extended. The fake sheet records the
    positions after each call.
  - `core/data/.../setlist/{SetlistRepository, DefaultSetlistRepository}.kt` and the new
    `SetlistMoves.kt`, `SetlistMove.kt`, `MoveSongOutcome.kt`; `cache/SetlistDao.kt`;
  - tests: `DefaultSetlistRepositoryTest` and `SetlistDaoTest`.
- **Part B**:
  - `feature/next-jam/.../NextJamPresenter.kt`, `NextJamUiModel.kt`, `NextJamScreen.kt`,
    `AdminControls.kt`, `AdminControlsDefaults.kt`, `NextJamCopy.kt`, `FailureMessages.kt`, the new
    `MoveOverlay.kt`, and the previews;
  - tests: `AdminFakes.kt`, the new `NextJamMoveTest` and `MoveOverlayTest`, and the existing tests
    that use `keyStatus` or row keys.
  - `core/ui` `ContrastTest`, only if a new colour pair appears. `text` and `textMuted` on `surface`
    are probably already covered.

## Visual Design Impact

The move block lives in the expanded panel only, and nothing a musician sees moves. Amber stays on
keys. Rows animate to their new place.

`DESIGN.md` changes in three places:

- "Admin controls" stops saying "a drag handle per row";
- "Admin controls, as built" gains "Reordering";
- the drag Open Design Question closes with M1.

## Durable Documentation Impact

- **`docs/apps-script-api.md`:** the `moveSong` and `checkSetlistMove` sections; the codes, the
  order, the answer and the batched `appendSetlistRow_`; `Known:`; the client mapping.
- **`docs/sheet-schema.md`:** `moveSong` rewrites only `posicion` cells, never moves sheet rows, and
  closes gaps. Partial-failure semantics. `unordered_setlist`.
- **`docs/domain-model.md`:** the reorder rule, with absolute targets.
- **`docs/risks-and-open-questions.md`:** the Risks below, plus the measured latency.
- **The architecture skill:** `moveSong`, `SetlistMove`, `moveSetlistSong`, `displayOrder`,
  `rowKey`, and the row action order.
- **`DESIGN.md`:** as above.
- **Not needed:** `AGENTS.md`, `ARCHITECTURE.md`, `CONSTRAINTS.md` and the access model.

## Implementation Plan

1. **Part A, server.** Write `moveSong_`, `moveSetlistRow_`, `checkSetlistMove` and the batched
   append. Node tests cover:
   - scenarios 1–6;
   - the no-duplicate invariant after every call, on both paths;
   - no write per refusal code;
   - `Catalogo` never written;
   - a `PUBLICADA` jam followed by a GET;
   - the check.

   Run `node --test backend/apps-script/test/*.test.js`.
2. **Part A, data.** Test on in-memory Room:
   - the DAO move up, move down, clamp and no-op, with slots and extras following their song;
   - an unavailable setlist or a duplicate id writes nothing;
   - the repository order: cache, then entry removal;
   - no Room change on failure;
   - mutex ordering with add, remove and key changes;
   - caller cancellation, `dismiss`, and unique ids.
3. **Failure demonstrations.** For each: mutate, watch the named test fail, restore, and run
   `sha1sum -c`.
   - (a) `moveSong_` writes before the `unordered_setlist` check.
   - (b) The park path writes in plain ascending row order. The invariant test fails.
   - (c) The repository mirrors Room on `Rejected`.
4. Run `CI=true ./init.sh` (three `wired`) for the server/data portion, then continue with Part B.
5. **No deployment or live checks in this feature run.** The user explicitly deferred them until
   app implementation is complete. Keep the server action and check local; do not read credentials
   or contact Apps Script. When the four-feature app batch is complete, the orchestrator coordinates
   one shared deployment and its live checks for reorder, publish, adjust-lineup and assign.
6. **Live-check contract for that later batch.** The script lives in the scratchpad. The URL and
   passphrase come from `local.properties` and are never printed. The body is built with `json.dumps`.
   Print only codes, counts, latencies and hash prefixes.
   - LM1: `{}` gives a `Known:` list that includes `moveSong` and `checkSetlistMove`.
   - LM2: **one** `moveSong` call with a wrong passphrase gives `invalid_passphrase`. It is the only
     wrong-passphrase call of this slice.
   - LM3: probes with the passphrase, all refused before any write:
     - `invalid_date`, `invalid_song`;
     - `toPosition` of `0` and `"2"`;
     - `1999-01-01` gives `unknown_jam`;
     - a past jam gives `jam_not_editable`;
     - the upcoming date with `zz-no-existe` gives `song_not_in_setlist` (or `unknown_jam` if no
       jam is upcoming).
   - LM4: `checkSetlistMove`, `checkSetlistWrite` and `checkSetlistRemove` all give `ok` (the last
     two prove the batched append). Record their latencies.
   - LM5: `readJams` before LM3 and after LM4. For each jam, compare the SHA-1 of its ordered song
     ids; they must be identical.

   **Never call `moveSong` on a real jam's tab.**
7. **Part B.** Molecule tests:
   - the musician model is unchanged apart from `rowKey`;
   - `Sending` reorders the rows and labels and adds `Guardando…`;
   - `Failed` reverts and adds a card;
   - after `Done` the cached order shows with no status;
   - two taps from one model move two places;
   - the edges are disabled;
   - the move block is hidden while a removal is sending;
   - `rowKey` falls back on duplicate ids;
   - an idempotent overlay after a refresh that already holds the move.

   Failure demonstration (d): the overlay also applies `Failed` entries, so the revert test fails.
   Then run the gate.
8. **Device check.** Pixel 5, with `bluesjam.demoUpcomingJam` and `bluesjam.debugAdmin` in
   `local.properties`. **Never TalkBack or accessibility settings.**
   1. Expand row 3. The block reads `Posición 3 de N`.
   2. Tap `Subir`. The row animates to 2 with `Guardando…` and stays under the thumb.
   3. The row reverts to 3, and the `AccessRefused` card appears. Take screenshots of both moments.
   4. Check that `Subir` is disabled on row 1 and `Bajar` on the last row.
   5. Rotate the device.
   6. Set font scale 2.0, then restore it.
   7. With the flag off, the musician view is unchanged.
   8. Capture a `uiautomator dump`. It must show each action at least 132 px tall (48dp at 2.75×),
      and the disabled ones marked `enabled="false"`.

   Restore every setting.

## Verification Plan

- Node is green. `CI=true ./init.sh` exits 0 with `konsist: wired` (17/17), `detekt: wired` and
  `ktlint: wired`.
- **"Persists to the Sheet":** Node scenarios 1–3 and read back with `buildSetlist`. Live LM4 on
  the real Sheet is explicitly deferred to the four-feature batch.
- **"Contiguous, no gaps or duplicates":** Node scenarios 1–5, the after-every-call invariant, and
  the DAO renumber test.
- **"48dp":** the code uses `heightIn(min = LocalMinimumInteractiveComponentSize)`; the device dump
  measures the bounds.
- **D-13:** `moveSong` is called with no UI in the repository tests.

## Evidence To Capture

- Node and gate counts.
- Failure demonstrations (a)–(d), each with its SHA-1 restore.
- The later shared deployment's `Post.gs` SHA-1 and LM1–LM5 codes, latencies and hash equality
  (pending by explicit user deferral; do not fetch or report them in this feature run).
- The device screenshots and the dump.
- A statement that no URL, passphrase or name appeared in any output.

## Risks

- **Partial failure on the park path.** It can leave the moved song last and a gap, never a
  duplicate. Any later `moveSong` closes it. The contiguous path is one call.
- **An optimistic order that never lands.** For about 5 s, the admin may read out an order the
  Sheet refuses. The revert and the card are the signal. Only the admin's device shows it.
- **Rapid taps.** The entry is published after a Room title lookup, so a second tap within a few
  milliseconds could compute from the old order. That is human-impossible in practice; the test
  covers taps after publication.
- **Song detail by position.** A detail screen opened before a confirmed move may show the song
  that now holds that position (the same class of issue as remove-song).
- **Read-dropped rows.** Rows the read drops for another reason (for example a bad key) still have
  whole positions, so they are renumbered. The cache's 1..n then differs until the next refresh.
- **In memory.** Pending and failed moves live in memory and are lost with the process.

## Validator Checklist

- [ ] Only `Post.js` changes server-side. `moveSong` and `checkSetlistMove` are in `ACTIONS` with
      `write: true`. No write happens before every check, and Node proves the no-duplicate
      invariant on both paths. `Catalogo` is never opened.
- [ ] `appendSetlistRow_` is batched, and the add-song and check tests are unchanged and green.
- [ ] `moveSong` is a public repository function (D-13). Room changes only after `Done`, cache
      first. A failure is the revert.
- [ ] Musician rows are unchanged except `rowKey`. No feature imports another. Rows are keyed by
      `rowKey`.
- [ ] Visible 48dp `Subir`/`Bajar`, with no hidden gesture; the edges are disabled, not removed. No
      amber. The copy is as approved.
- [ ] Three `wired`. No deployment or live request occurred during app implementation; the grouped
      live checks remain a documented follow-up. No secret appears anywhere.

## User Decisions (8 October 2026)

The user explicitly selected the following options; no further approval is needed for these
behaviors or the deployment grouping:

- **M1:** visible `Subir` and `Bajar` buttons in the expanded panel, with `Posición x de n`;
  each action is at least 48dp.
- **O2:** move the row immediately; if the write fails, restore its confirmed position and show a
  dismissible failure notice.
- **B2:** after app implementation is complete, use one paste and one **New version** for the
  server halves of `admin-reorder-songs`, `admin-publish-setlist`, `admin-adjust-lineup` and
  `admin-assign-musician`. The local server portions are implemented in this order: reorder, publish,
  adjust-lineup, assign, with the existing Node tests kept green at every step. Implement and
  validate all app portions locally before that single deployment; run the grouped live checks after
  it. `admin-clear-slot` is not in the batch. Do not deploy a partial batch to work around a feature
  blocker.
