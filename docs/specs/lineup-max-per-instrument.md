# Spec: lineup-max-per-instrument

Feature: "Per-instrument slot ceilings: Batería and Teclados stay at one" (`feature_list.json`, last entry).
Depends on `lineup-grow-any-instrument` and `extras-dialog-and-lines` (both accepted). Amends D-18 again.

## Goal and non-goals

Goal: Guitarra, Bajo, Voz and Armónica grow to 3 slots; Batería and Teclados never pass 1 (user decision,
12 Oct 2026, "batería y teclados, solo 1"). The editor's `+` and `withSlotCount` stop at each instrument's
own ceiling, the script refuses the same, and new jam tabs are created with 14 slot columns.

Non-goals: changing the wire key set, a Room migration, touching the real Sheet, a device write, shrinking
`SlotsDto`, changing Konsist rules.

## Context (inspected 12 Oct 2026; deploy state per the coordinator)

- The lineup-grow script IS deployed (live GET: 23-key `slots`, the 11 new keys `-`, mirrors match). The
  user's 2026-10-31 tab already has 14 slot columns (Guitarra 1-3, Bajo 1-3, Voz 1-3, Armónica 1-3,
  Batería, Teclados) and works with the deployed script (absent columns read `-`). So this slice needs a
  **second, small redeploy**, and the app change is independent of it.
- `Lineup` (`core/model/.../Lineup.kt`): `MAX_INSTRUMENTS` = 18 columns (3 per instrument),
  `maxCount(instrument)` counts columns, used by the `init` guard, `withSlotCount`, `LineupControls.kt:62`,
  `LineupReservations.kt:64` (`canAdd`), `SetlistLineupChanges.kt:41`.
- `SetlistMapper.lineupSlots` zips `MAX_INSTRUMENTS` with the 18 cells and builds `Lineup(...)`, which would
  throw if a hand-edited tab had two non-`-` Batería cells.
- `Post.js`: `SLOT_COLUMNS_`, `MAX_SLOTS_PER_INSTRUMENT_ = 3` (`setSlotCount_`, line ~930),
  `BEYOND_DEFAULT_SLOT_FIELDS_`, `setlistSpecs_()` (24 specs), `createSetlistTab_` (writes every spec's
  header), `appendSetlistRow_` (already writes only mapped columns, so a missing column is skipped).
  `Jams.js` `SLOT_FIELDS` and `withLegacySlotKeys_` need no change.

## Decisions

1. **The wire key set stays 18 (+5 mirrors, or legacy 7).** `drums2/3`, `keyboards2/3` are always `-` from
   a 14-column tab. Shrinking to 14 would be a third key-set rule, break `SlotKeys`, `SlotsDto`,
   `ConfirmedSlots`, the column indices of assign/clear (index in `SLOT_FIELDS`) and the already deployed
   script/live tab, to save four constant cells. `SlotKeys` and `SlotsDto` are not touched.
2. **Two numbers in `Lineup`:** columns (`MAX_INSTRUMENTS`, 18, unchanged: the wire shape and
   `DEFAULT_COLUMNS`) and ceilings. `maxCount(instrument)` now returns the **ceiling** (GUITAR 3, BASS 3,
   VOCALS 3, HARMONICA 3, DRUMS 1, KEYBOARDS 1) from a private `Map`, no longer a column count. Every
   existing caller (init guard, `withSlotCount`, editor `canAdd`/target check, `SetlistLineupChanges`)
   already calls `maxCount`, so it follows with no edit. Update the KDoc ("at most three" becomes
   "at most [maxCount]") and say that columns beyond the ceiling are never written by the app.
3. **Mapper tolerance (hypothetical tab with `Batería 2/3`, `Teclados 2/3`):** `lineupSlots` skips a column
   whose instrument ordinal exceeds `maxCount` (treated like `-`; a name there is not shown). Reading never
   throws and never grows past 1. Not reported as a row issue (assumption: no one has such a tab; the user
   can confirm).
4. **Server ceilings:** a `SLOT_CEILINGS_` map in `Post.js` (guitar/bass/vocals/harmonica 3,
   drums/keyboards 1) replaces `MAX_SLOTS_PER_INSTRUMENT_`. In `setSlotCount_`, after the instrument is
   validated and before any read of the Sheet: `count > ceiling` is `invalid_count`
   ("count must be an integer from 0 to N"). `instrument_not_expandable` stays for a missing column
   (e.g. an old 7-column tab asking Bajo 2). A tab that does have `Batería 2` reads it; `setSlotCount`
   to 1 shrinks it only if open (existing `planSlotCount_` rule), and a count of 2 is `invalid_count`.
   `SLOT_COLUMNS_` stays at 3 fields per instrument (shrink logic must still see them).
5. **`createSetlistTab_` emits 14 slot headers:** a `NOT_CREATED_SLOT_FIELDS_` list (`drums2`, `drums3`,
   `keyboards2`, `keyboards3`) filters the headers written (5 + 14 + Otros = 20 columns). `setlistSpecs_()`
   itself stays at 24 so reads/mapping still recognise those headers if a tab has them.
6. **`appendSetlistRow_`:** no logic change; it already writes only mapped columns, so a 14-column tab gets
   `-` in `guitar3`, `bass2`, `bass3`, `vocals2`, `vocals3`, `harmonica2`, `harmonica3` only. Update its
   comment and `BEYOND_DEFAULT_SLOT_FIELDS_` doc; keep the list (harmless for absent columns, still right
   for a widened tab).
7. **Deploy:** only `backend/apps-script/src/Post.js` changes, so only the `Post.gs` file is pasted
   (Part C of the guide). No Sheet change for the user (their tab is already 14 columns).

## Acceptance scenarios

- Given Batería has 1 slot, when the admin opens Cambiar formación, then Batería's `+` is disabled; same
  for Teclados. Guitarra, Bajo, Voz, Armónica enable `+` until 3.
- Given `withSlotCount(DRUMS, 2)`, then `IllegalArgumentException`; `withSlotCount(BASS, 3)` works.
- Given POST `setSlotCount` `drums` count 2 (or `keyboards` 2), then `invalid_count` and no write; `bass` 3
  on a tab with the columns succeeds; `bass` 3 on a 7-column tab is `instrument_not_expandable`.
- Given a new jam tab via `addSong`, then the header row has 20 columns with no `Batería 2/3`,
  `Teclados 2/3`, and the 11 cells of a row are `-` only for existing columns.
- Given a tab that has `Batería 2` holding `-`, then it reads and the app shows one Batería slot.

## File changes

- `core/model/.../Lineup.kt` and `LineupTest.kt` (ceilings; `withSlotCount` bounds; init guard rejects 2
  drums; a lineup of 3 bass is valid).
- `core/data/.../jams/SetlistMapper.kt` (+ `SetlistMapperTest`: drums2 filled on a hypothetical tab is
  skipped, no exception, row kept).
- `core/data/.../setlist/SetlistLineupChanges.kt`: no code change expected; add a test that a Drums count
  of 2 is refused locally, nothing sent.
- `feature/next-jam/.../LineupControls.kt`, `LineupReservations.kt`: no code change expected (they call
  `maxCount`); add tests in `NextJamLineupChangeTest`/`SongDetailAdminTest` that `canAdd` is false for
  drums/keyboards at 1 and true for bass at 2. Replace the `com.bbbjam.core.model.Lineup` fully qualified
  call only if touched.
- `backend/apps-script/src/Post.js` (decisions 4-6); tests in `backend/apps-script/test/setlist.test.js`
  (new: ceilings, `invalid_count` for drums/keyboards 2 with no write, 14-header `createSetlistTab_` via
  `addSong`, `-` only in existing columns, tab with `Batería 2` shrink/refuse; adapt tests that assert
  column numbers of a created tab, e.g. line ~1183 expecting column 18, and the 24-column assumptions).
  Run `node --test` in `backend/apps-script`.
- Docs: `docs/migrations/lineup-grow-any-instrument-sheet-migration.md` (Part A: 14 columns, with the
  table reduced to `Batería`, `Teclados` single columns, the 11 -> 7 new columns `Guitarra 3`, `Bajo 2-3`,
  `Voz 2-3`, `Armónica 2-3`, and the paste-check strings; note a 18-column tab also keeps working; add a
  short **Part C: second deploy** pasting only `Post.gs`, new version of the same deployment, check string
  `var SLOT_CEILINGS_`, read-only check that a `setSlotCount` drums 2 is not tried live by the agent);
  `docs/sheet-schema.md` and `docs/apps-script-api.md` (per-instrument ceilings, `invalid_count` ranges,
  tab creation with 14 slot headers, columns beyond the ceiling read but never grown);
  `DESIGN.md` (the `+` stop at each instrument's ceiling, if the lineup editor section mentions 3);
  `bb-blues-jam-bitacora.md` D-18 amendment (ceilings 3/3/3/3/1/1, wire kept at 18 and why);
  `.claude/skills/architecture/SKILL.md` "As built" line for this slice.

## Tasks (in order)

1. `Lineup` ceilings + tests. 2. Mapper skip + test. 3. Repository/presenter tests. 4. `Post.js`
(ceilings, `createSetlistTab_`) + Node tests. 5. Docs. 6. `CI=true ./init.sh` and `node --test`.

## Verification

- `CI=true ./init.sh` exits 0 with konsist, detekt, ktlint `wired`; `node --test` in
  `backend/apps-script` passes. A new Node test must fail first against the old `Post.js` (show it).
- Device plan (no Sheet write): debug demo jam, admin debug session, open a song, Cambiar formación:
  Batería and Teclados `+` disabled, Bajo reaches 3 and then disables. The demo jam never reaches
  the script. The real endpoint is not written to by the agent.

## Evidence and validator checklist

Evidence: test names for ceilings (Kotlin and Node), the failing-first Node run, the init.sh tail.
Validator: `maxCount` is a ceiling and `MAX_INSTRUMENTS` still 18; `SlotKeys`/`SlotsDto` untouched; server
`invalid_count` before any Sheet read; only `Post.js` changed in the backend; guide Part A/C accurate;
no ViewModel/feature dependency/amber change; UI copy unchanged (Spanish, vos).

## Needs human decision

None blocking. Confirm assumption 3 (a name in an out-of-ceiling `Batería 2/3` cell is silently hidden).

## Orchestrator Amendment, 12 October 2026

- **Decision 3 is replaced: never hide a name.** On a tab that has a column beyond an instrument's
  ceiling (for example `Batería 2`), an **open** cell there is ignored (it is not part of the lineup),
  but a **filled** cell is still read as a filled slot, so a musician who was assigned is always shown.
  The ceiling limits growth (`withSlotCount`, the editor's `+`, server `setSlotCount_`), not what is
  read; relax the `Lineup` init guard accordingly, or apply the ceiling only where the lineup grows.
  Cover both cases with a mapper test. No real tab has such a column today.
