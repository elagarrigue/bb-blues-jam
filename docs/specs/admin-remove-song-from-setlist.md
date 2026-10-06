# Feature Implementation Spec: Remove a song from the setlist

## Source Feature

- `id`: admin-remove-song-from-setlist
- `area`: feature-next-jam
- `depends_on`: admin-add-song-to-setlist (`passing`, Part A in 29d3b54 and Part B in 2163aab,
  being validated at planning time, **not accepted**)
- `status`: `not_started` at planning time (6 October 2026)
- `source`: `feature_list.json`

## Readiness

**Implementation must not start yet.** It waits for:

1. `admin-add-song-to-setlist` is `accepted`. This spec builds on its code as committed in 2163aab:
   `Post.js` (`ACTIONS`, router guard, write lock, `requireEditableJam_`, `createSetlistTab_`,
   `appendSetlistRow_`, `SETLIST_CHECK_TAB`), `AdminWriter.write`/`WriteOutcome`,
   `DefaultSetlistRepository` (its `order`/`writes` mutexes, `DataScope`, `dismiss`), `SetlistDao`,
   and the admin layer of `NextJamPresenter` (`AdminState`, `NextJamAdminUiModel`,
   `AdminControls.kt`, `failureMessage`). If validation changes any of them, re-read this spec first.
2. The user's answers to **R1**, **U1** and **B1** (User Approvals).
3. A user redeploy of `Post.js`. Only `Post.gs` changes; `Code.js`, `Jams.js`, `Catalog.js` and
   `Normalize.js` stay byte-identical. Under B1 (a) this deploy is shared with `admin-set-key`.

**Size:** one feature. The server half (Part A: `Post.js`, Node, `:core:data`) must be done before
the deploy; the UI (Part B) can follow in the same or the next session. No split recommended: there
is no new screen, no new read path and no schema change.

## Goal

On Próxima jam the admin expands a song, taps **Quitar de la lista**, confirms inline, and the row
shows **Quitando…** until Apps Script answers. On success the row is gone, later songs move up one
position (the Sheet stays numbered 1..n), and the cache reflects exactly that. On failure the row
stays as it was and a persistent card says why. The catalog (`Catalogo`) is never touched.

The mutation is `SetlistRepository.removeSong(jamDate, songId)` in `:core:data`, usable with no UI
(D-13).

## Non-Goals

- Undo (U1). Recovery is adding the song again (its key and assignments are not restored).
- Removing from a past jam (D-04) or from any jam but the upcoming one; deleting a jam or its tab.
- Reorder, set key, lineup, assign/clear, publish: their own slices. No drag handle here.
- Offline queueing (decided in add-song: blocked, with a message). Deeplinks and the registry
  (`action-contract-registry`).
- Bulk removal or "clear the list".

## Job Story

When the setlist I am building has a song we are not going to play,
I want to take it off from my phone,
so musicians reading the list never prepare a song that is not happening.

## Users And Permissions

- Musician: nothing changes. Rows have no admin part; `setlistForMusicians()` as today. A removal
  from a published list reaches them on their next refresh (cache-first, stale after 30 min), not
  instantly.
- Admin (`observeIsAdmin()` true): the remove action in the expanded panel. Apps Script authorizes
  the write (router guard), never the flag. A refused write keeps admin mode (W3).
- Debug admin (`bluesjam.debugAdmin`): drawn; no passphrase stored, so `removeSong` returns
  `AccessRefused` without a request. This is the device path for the failure card.

## Acceptance Scenarios

1. **Remove succeeds and renumbers (R1 a).** Upcoming jam tab rows 1 `crossroads`, 2 `hoochie`,
   3 `thrill`, 4 `pride` (tab order arbitrary). `removeSong(date, hoochie)` deletes that Sheet row
   and rewrites `posicion` 3→2 and 4→3 (plain text). Answer `{ok:true, position:2}`. Room: `hoochie`
   and its slots and extras are gone; `thrill` and `pride` sit at 2 and 3 with their own key, slots
   (musicians included) and extras. `readJams` returns the same three songs.
2. **Catalog untouched.** `Catalogo` cells are identical before and after (Node: no write op on that
   tab; the write log names only the jam tab).
3. **Server validates before any write**, in this order, each leaving the spreadsheet untouched:
   `invalid_date`, `invalid_song`, `unknown_jam`/`duplicate_date`, `jam_not_editable` (same rule as
   `addSong`), `missing_header`/`duplicate_header`, `song_not_in_setlist` (no row with that trimmed
   `id_tema`, or no tab at all), `duplicate_song` (the id on more than one row).
4. **Failure leaves everything as it was.** For `AccessRefused`, `Offline`, `Unavailable` or
   `Rejected(code)`: the row is drawn normally again, Room is unchanged, and a card
   `No se pudo quitar «Hoochie Coochie Man»` with the outcome's message and `Cerrar` stays until
   closed. One exception: `song_not_in_setlist` also removes the cached row (with the same shift),
   because the server says the Sheet no longer has it; the card still shows (C copy below).
5. **Confirmation (U1).** Tapping `Quitar de la lista` changes nothing anywhere; it replaces the
   action with the inline confirmation. `Cancelar` returns to the action. Only `Quitar` sends. A
   double tap on `Quitar` removes once. The confirmation survives rotation.
6. **Published list.** On a `PUBLICADA` jam the confirmation adds the published line. Removal is
   allowed. A musician device shows the new list after its next refresh.
7. **Assigned musicians.** A song with filled slots or extras shows the count line in the
   confirmation; after removal those assignments are gone with the row (no orphan data).
8. **Serialized with adds.** An add and a remove issued back to back on one device are sent in call
   order (the existing `writes` mutex); across devices the script lock serializes them. Because the
   row is found by `id_tema`, a renumber by another admin's removal never makes this one hit the
   wrong song.
9. **Expansion follows the song, not the position.** After removing an expanded song, the song that
   moves up into its position is not drawn expanded.
10. **Last song.** Removing the only song leaves the header row; the admin sees the admin empty
    state, musicians of a published jam the published-empty copy.

## Repository Research

### Files Inspected

`AGENTS.md`, `PROGRESS.md` (head), `feature_list.json` (this entry, add-song, set-key, reorder and
the other admin slices), `.claude/skills/architecture/SKILL.md` (module table, admin sections,
Where Each Piece Goes), `DESIGN.md` (Song row expanded, Status badge, Admin controls, Core
Screens), `bb-blues-jam-design-prompt.md` §2 and the rules, `docs/design/screens/next-jam-admin.png`,
`docs/domain-model.md` (invariants, relationships, edge cases), `docs/sheet-schema.md`
(Identifiers, Mapper rules, How Apps Script writes a jam tab), `docs/apps-script-api.md` (grep),
`docs/risks-and-open-questions.md` (grep), `docs/user-and-access-model.md` (grep),
`docs/specs/admin-add-song-to-setlist.md` (whole). Code: `backend/apps-script/src/Post.js` (whole),
`Jams.js` (outline), `Normalize.js` (exports), `test/setlist.test.js` (head), `README.md` (grep);
`core/data`: `setlist/*`, `admin/AdminWriter.kt`, `admin/WriteOutcome.kt`, `cache/SetlistDao.kt`,
`cache/JamEntities.kt` (keys, FKs, `jam_song_resolved`), `cache/JamsDao.kt` (outline);
`core/model/Setlist.kt`; `feature/next-jam`: `NextJamPresenter.kt`, `NextJamUiModel.kt`,
`NextJamCopy.kt`, `AdminControlsDefaults.kt`, `ExpandedRows.kt`, `NextJamScreen.kt` and
`AdminControls.kt` (outlines). Not opened: `CONTEXT.md` beyond grep, the bitácora (D-04, D-13, D-15
taken from `AGENTS.md` and the architecture skill), `docs/technical-discovery.md`, song detail code.

### Findings

- The documented write identity of a JamSong is (`fecha`, `posicion`), and the read path never
  renumbers. But rows draw `positionLabel` zero-padded, so a gap left by a removal shows musicians
  `01, 02, 04`; and `admin-reorder-songs` already requires contiguous positions. Hence R1.
- `addSong` refuses a song already in the tab, so `id_tema` is unique in any app-built setlist; a
  hand-edited tab may duplicate it (the mapper does not drop duplicate ids).
- Slot and extra rows have FKs to `(jam_date, position)` with `onDelete = CASCADE` and no
  `onUpdate`; shifting a position in Room means delete and reinsert, not an UPDATE.
- `ExpandedRows` is keyed by (date, position). With renumbering, the next song would inherit the
  removed song's expansion (scenario 9).
- `SongRowUiModel` has no admin part; `NextJamAdminUiModel.failures` is `List<AddFailureUiModel>`,
  whose shape fits any failed write.
- `jam_song_resolved` already gives the displayed title per (date, position, song_id).

## Technical Approach

### Part A1 — `backend/apps-script/src/Post.js` (only src file that changes)

- `ACTIONS.removeSong = { write: true, run: removeSong_ }`, request `{date, songId}`.
  1. Validate `date` (`isCalendarDate_` → `invalid_date`) and `songId` (`SONG_ID_` → `invalid_song`).
  2. `requireEditableJam_(spreadsheet, services, date)` (unchanged).
  3. No tab named `date` → `song_not_in_setlist`. Otherwise read display and raw values, map the
     header with `mapColumns(date, setlistSpecs_(), header)` (header errors as the reads).
  4. Scan data rows: `textCell(display[r][songId col]) === songId`. Zero → `song_not_in_setlist`;
     more than one → `duplicate_song`. Nothing is written before this point.
  5. `p` = the matched row's `posicion` through `integerTextCell(raw)`, a whole number ≥ 1 or null.
     Collect the other rows whose `posicion` is a whole number `> p` (none when `p` is null).
  6. If the matched row is the sheet's last row (`getMaxRows()`), `insertRowAfter` it first, so a
     trimmed or frozen-header tab never refuses the delete. Then `sheet.deleteRow(rowNumber)`.
  7. Renumber in ascending `posicion` order, adjusting row numbers below the deleted one by −1: set
     the cell's format to `@`, then `String(q - 1)`. Ascending order never creates a duplicate
     even if a write fails midway (worst case a gap, which the read path tolerates).
  8. Answer `{ok:true, position: p}` (`p` a JSON number, or null).
  - Extract steps 4–7 as `removeSetlistRow_(sheet, songId)` returning `p`, shared with the check.
- `ACTIONS.checkSetlistRemove = { write: true, run: checkSetlistRemove_ }`, no repository function,
  never listed by a read. On `SETLIST_CHECK_TAB` (`_prueba_lista`): delete a leftover, create it with
  `createSetlistTab_`, append markers at 1, 2, 3 (`zz-prueba-uno`, `-dos`, `-tres`) with
  `appendSetlistRow_`, run `removeSetlistRow_(sheet, 'zz-prueba-dos')`, read back with
  `buildSetlist`, delete the tab in `finally`, and require exactly `uno` at `"1"` and `tres` at
  `"2"`; otherwise throw (`internal_error`) after the delete.
- New codes `song_not_in_setlist`, `duplicate_song`; messages in English with no passphrase. Update
  the header comment, `module.exports` (`removeSong_` testable through `handlePost` only), README
  marker and `Known:` list.

### Part A2 — `:core:data`

- `setlist/SetlistRepository` gains:
  - `suspend fun removeSong(jamDate: LocalDate, songId: SongId): RemoveSongOutcome`;
  - `fun observeRemoves(): Flow<List<SetlistRemove>>`;
  - `dismiss(id)` now removes a failed add **or** a failed remove (ids share one counter).
- New `RemoveSongOutcome`: `Removed` or `NotRemoved(reason: WriteOutcome)` (never `Done`).
- New `SetlistRemove(id, jamDate, songId, title, state)` with `State.Sending` / `State.Failed(reason)`.
- `DefaultSetlistRepository.removeSong`: same shape as `addSong` — `scope.async(UNDISPATCHED)`,
  under `order` resolve the title (`SetlistDao.songTitle(date, songId)` from `jam_song_resolved`,
  else `songId.value`), publish `Sending`, queue on `writes`, then `writer.write("removeSong",
  {date, songId})`:
  - `Done` → `SetlistDao.removeSetlistSong(date, songId)`; drop the entry; `Removed`.
  - `Rejected("song_not_in_setlist")` → also `removeSetlistSong`; entry `Failed`; `NotRemoved`.
  - anything else → entry `Failed(reason)`; nothing in Room; `NotRemoved`.
  - A `SQLException` from Room never changes the outcome (the Sheet is authoritative; the next
    refresh fixes the cache), as in `store`.
- `cache/SetlistDao.removeSetlistSong(date, songId): Boolean`, one `@Transaction`: only when the
  jam's `setlist_state` is `AVAILABLE` and exactly one cached song has that id; take its position
  `p`; load songs, slots and extras of that date with position `> p`; delete slots, extras and songs
  with position `>= p` explicitly (do not rely on cascade); reinsert the loaded ones at position −1.
  Plus `songTitle(date, songId): String?`. No schema or version change.

### Part B — `:feature:next-jam`

- `ExpandedRows` keyed by (date, `songId` text) instead of position; `toggle`, `isExpanded` and the
  `Saver` follow. KDoc explains why (a removal renumbers). Existing tests adapt.
- `SongRowUiModel` gains `admin: SongRowAdminUiModel? = null` (null for musicians, so musician
  models stay equal). `SongRowAdminUiModel.removal` is one of:
  - `Idle(label, events)` → event `RequestRemove`;
  - `Confirming(prompt, details: List<String>, confirmLabel, cancelLabel, events)` → `Confirm`,
    `Cancel`;
  - `Removing(status)` while a `Sending` `SetlistRemove` exists for (date, songId).
- `NextJamPresenter` collects `observeRemoves()`, holds `confirming: String?` ("date|songId") in
  `rememberSaveable`. `Confirm` acts only when `confirming` still equals the row's key, clears it and
  `scope.launch { setlist.removeSong(date, songId) }` (a double tap removes once). `AdminState`
  carries removes and the handlers; handlers keyed by (date, songId).
- Failures: `NextJamAdminUiModel.failures` merges failed adds and removes in id order. Keep the type
  name `AddFailureUiModel` (KDoc: "a failed add or remove") to avoid churn; titles from
  `NextJamCopy.addFailed`/`removeFailed`. `failureMessage` gains the two new codes.
- Screen: in the expanded panel, after `Ver detalle del tema`, admin only: the remove action, or the
  confirmation block, or the status line (polite live region). Collapsed rows and everything above
  are unchanged (DESIGN "added, not substituted"). Colours in `AdminControlsDefaults.removal()`:
  action and `Quitar` in `error`, `Cancelar` in `text`, prompt `text`, details and `Quitando…`
  `textMuted`; **no amber**; every target 48dp, `Role.Button`. Add the `error`-on-panel pair to
  `ContrastTest` (≥ 4.5:1).
- `:app`: no change (no route). Konsist stays 17 rules, allowlists unchanged.

### Copy (C, Rioplatense, vos)

- Action `Quitar de la lista`; prompt `¿Quitar «Crossroads» de la lista?`
- Details: with n filled slots plus extras, `Se borra también el músico anotado.` (n = 1) or
  `Se borran también los n músicos anotados.`; on a published jam
  `La lista está publicada: los músicos van a dejar de verlo.`
- Buttons `Quitar`, `Cancelar`; status `Quitando…`; failure title `No se pudo quitar «…»`.
- `song_not_in_setlist` → `Ese tema ya no estaba en la lista.`;
  `duplicate_song` → `Ese tema está repetido en la planilla. Corregilo ahí y probá de nuevo.`;
  every other outcome reuses add-song's C1 messages.

## Expected File Changes

- Part A: `backend/apps-script/src/Post.js`, `test/setlist.test.js` (or new `test/remove.test.js`
  loaded the same way; the fake sheet gains `deleteRow`, `insertRowAfter`, `getMaxRows`), `README.md`;
  `core/data/.../setlist/SetlistRepository.kt`, `DefaultSetlistRepository.kt`, new
  `RemoveSongOutcome.kt`, `SetlistRemove.kt`; `cache/SetlistDao.kt`; tests
  `DefaultSetlistRepositoryTest` and `SetlistDaoTest` (or the existing DAO test). `DataModule` is
  unchanged (same constructor).
- Part B: `feature/next-jam/.../ExpandedRows.kt`, `NextJamPresenter.kt`, `NextJamUiModel.kt`,
  `NextJamScreen.kt`, `AdminControls.kt`, `AdminControlsDefaults.kt`, `NextJamCopy.kt`, previews;
  tests `ExpandedRowsTest`, `NextJamAdminTest` (or new `NextJamRemoveTest`), `AdminControlsDefaultsTest`,
  `AdminFakes.kt`; `core/ui` `ContrastTest`.
- `core/model/Setlist.kt` and `SongRowUiModel` KDoc: "never renumbered **by the read path**".
- No Gradle, manifest, Room version, Konsist or dependency change.

## Visual Design Impact

UI in the expanded panel only. Tokens from `BluesJamTheme`; amber stays on keys. `DESIGN.md`
"Admin controls, as built" gains the remove action, confirmation and status. The design export's
red trash icon becomes a text action: icon-only destructive buttons need a label anyway, and no
icon set is in the project.

## Durable Documentation Impact

- `docs/apps-script-api.md`: `removeSong`, `checkSetlistRemove`, codes, validation order, answer,
  `Known:` list, the client mapping.
- `docs/sheet-schema.md`: jam tab writes are no longer append-only — `removeSong` deletes one row and
  rewrites later `posicion` cells; setlist mutations locate a row by `id_tema` (R1). Identifiers
  section updated.
- `docs/domain-model.md`: removal rule, renumbering, identity note; invariant text "never renumbered"
  scoped to reads.
- `docs/user-and-access-model.md`: published removal reaches musicians on refresh.
- `docs/risks-and-open-questions.md`: new risks below; latency of `removeSong` from live checks.
- `.claude/skills/architecture/SKILL.md`: `removeSong`, `SetlistRemove`, `removeSetlistSong`,
  `ExpandedRows` keyed by song id, "setlist mutations identify a row by songId".
- `DESIGN.md` as above. `AGENTS.md`, `ARCHITECTURE.md`, `CONSTRAINTS.md`: not needed.

## Implementation Plan

1. Part A1: `Post.js` + Node tests; `node --test backend/apps-script/test/*.test.js`.
2. Part A2: `:core:data` + tests; `./gradlew ktlintFormat`; `CI=true ./init.sh`.
3. Failure demonstrations (mutate, watch the test fail, restore, `sha1sum -c`):
   (a) `removeSong_` deletes before the duplicate check → "no write on `duplicate_song`" fails;
   (b) renumbering skipped → scenario 1 Node test fails;
   (c) the repository edits Room on `Offline` → "no change on failure" fails;
   (d) `ExpandedRows` keyed by position again → scenario 9 test fails.
4. User deploy (per B1): paste `src/Post.js` into `Post.gs`, check the README marker, **Manage
   deployments → Edit → New version → Deploy**. Record the deployed SHA-1.
5. Live checks (agent; script in the scratchpad; URL and passphrase read from `local.properties`,
   never printed; JSON built with `json.dumps`; print only codes, counts, latencies):
   - LR1 `{}` → `Known:` includes `removeSong, checkSetlistRemove` (and set-key's under B1 a).
   - LR2 `removeSong` with a wrong passphrase → `invalid_passphrase` (counts 1 toward W2).
   - LR3 probes with the passphrase, all refused before any write: bad date, bad id,
     `1999-01-01` (`unknown_jam`), a past jam date from the public GET (`jam_not_editable`), the
     upcoming date with `zz-no-existe` (`song_not_in_setlist`; `unknown_jam` if none is upcoming).
   - LR4 `checkSetlistRemove` → `ok` and latency.
   - LR5 `readJams` before LR3 and after LR4: per-jam song counts identical (no real jam modified).
   **Never call `removeSong` with a real song id.**
6. Part B + tests; gate.
7. Device (Pixel 5, `demoUpcomingJam` + `debugAdmin`): expand, action, confirm block with details,
   `Cancelar`, rotation in confirmation, `Quitar` → `Quitando…` → `AccessRefused` card, row intact,
   `Cerrar`; flag off → no action. If the outcome is not `AccessRefused`, stop and report. Screenshots
   and `uiautomator dump`. **No TalkBack or accessibility settings.**

## Verification Plan

- Node green (count before + new). `CI=true ./init.sh` exit 0, `konsist: wired` (17/17),
  `detekt: wired`, `ktlint: wired`.
- Feature check 1 ("disappears locally and gone from the Sheet after a reload"): Node scenario 1
  (row gone, later rows renumbered, read back by `buildSetlist`); `SetlistDao` test on in-memory
  Room; repository test end to end; live LR4 proves `deleteRow` and renumbering on real Sheets.
- Feature check 2 ("does not affect the catalog"): Node asserts no write op on `Catalogo` and its
  grid unchanged; `CatalogDao` rows unchanged in the repository test.
- D-13: `removeSong` called with no UI in tests.

## Evidence To Capture

Node and gate counts; failure demonstrations (a)–(d) with SHA-1 restores; LR1–LR5 codes and
latencies, deployed `Post.gs` SHA-1; screenshots and dump; a statement that no URL, passphrase or
musician name appeared in any output.

## Risks

- **Partial renumber.** Apps Script has no transaction; a failure after `deleteRow` can leave a gap
  (never a duplicate). The read path tolerates it; the next removal or reorder closes it.
- **Refresh race** (as add-song): a refresh read before the removal and stored after brings the row
  back until the next refresh.
- **Latency** grows with the songs after the removed one (one cell write each); measured in LR4.
- In-memory entries are lost on process death.
- `song_not_in_setlist` mirrors the removal locally; if the Sheet changed in another way the cache
  stays off until the next refresh.

## Validator Checklist

- [ ] `Code.js`, `Jams.js`, `Catalog.js`, `Normalize.js` byte-identical; GET answers unchanged.
- [ ] `removeSong` is in `ACTIONS` with `write: true`; no write before every check; Node proves it
      per code. `Catalogo` never written.
- [ ] Row located by `id_tema`; later positions renumbered ascending; plain-text format.
- [ ] `removeSong` is a public repository function (D-13); Room changes only from the server's
      answer (and the `song_not_in_setlist` mirror); no change on any other failure.
- [ ] Musician models unchanged (`admin` null); no feature imports another; no `:feature:admin`.
- [ ] Inline confirmation before any write; double tap removes once; expansion keyed by song id.
- [ ] No amber outside keys; copy as approved; 48dp targets.
- [ ] Three `wired`; live checks recorded; no real jam modified (LR5); no secret in repo or output.

## User Approvals

Answered by the user on 6 October 2026 (recommended in all three): **R1 (a)** find the row by
`id_tema` and renumber 1..n (set-key also finds its row by song id); **U1** as specified; **B1 (a)**
one batched Post.js deploy for remove-song and set-key, remove-song's server half first.

The questions as asked:

Ask before implementing. Each has a recommendation.

- **R1: positions and row identity after a removal (recommended: a).**
  - (a) The server finds the row by (`fecha`, `id_tema`), deletes it and shifts later positions up,
    so the Sheet stays 1..n and musicians never see `01, 02, 04`. Every later setlist mutation
    (set key, reorder, lineup, assign, clear) also locates its row by `id_tema`, so a renumber by
    one admin can never make another admin's write hit the wrong song. `admin-set-key` must adopt
    the same identity; the docs' "(`fecha`, `posicion`) is the write identity" becomes "the read
    identity".
  - (b) Delete by (`fecha`, `posicion`) with `id_tema` as a guard and leave a gap; reorder closes it
    later. Simpler write, but musicians see the gap in the zero-padded numbers.
  - (c) As (b) but renumber: positions shift under position-based writes, which then need the guard
    and fail more often.
- **U1: interaction (recommended: as specified).** The action lives in the expanded panel (no swipe:
  the design rules forbid hidden gestures). An inline two-step confirmation, no dialog, naming the
  musicians that will be unassigned and whether the list is published. No undo. Pending state
  `Quitando…` in place; the row only disappears when the server confirms (no optimistic hide).
  Copy as in **Copy** above.
- **B1: one batched `Post.js` deploy (recommended: a).**
  - (a) Batch exactly `admin-remove-song-from-setlist` and `admin-set-key`, after add-song is
    accepted. Implement remove-song's Part A, then set-key's server half on top of it (the
    write-auth/add-song exception again: two features `in_progress` until the deploy), then one paste
    and one **New version**, then each feature's live checks, Part B and validation separately, in
    list order. About 3 minutes of your time once.
  - (b) Also wait for reorder, assign and clear: fewer deploys, but they are unplanned and carry open
    design questions (drag versus move buttons, where name suggestions come from), so both ready
    slices would wait several sessions, and the paste would be much larger to review.
  - (c) One deploy per slice.
  - With (a), a second batch later can cover reorder, lineup, assign, clear and publish together.
