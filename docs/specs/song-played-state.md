# Feature Implementation Spec: Admin marks a setlist song as already played (reversible)

## Source Feature

- `id`: song-played-state
- `area`: feature-setlist
- `depends_on`: admin-drag-reorder (accepted), musician-filter (accepted)
- `status`: not_started at planning time (9 November 2026)
- `source`: `feature_list.json` plus the user decisions of 9 November 2026 relayed by the orchestrator

## Sizing

Large but one data model change plus one flow. Planned, like `admin-set-key`, as **Part A** (Apps Script +
`:core:model` + `:core:data` + `:app` binding, no UI) and **Part B** (UI, assistant guard, docs). One implementer
may do both in one pass; Part A must be green (`CI=true ./init.sh`) before Part B starts. If the orchestrator
prefers two features, split at that line; nothing in Part A depends on Part B.

## Goal

During the jam the admin ticks songs off as **ya tocado** and can untick them. A played song is drawn muted
for everyone and becomes read-only: key, lineup size, musician assign/clear, extras and removal are refused
(in the app and, authoritatively, by Apps Script); reordering (arrows and drag) still works. The state lives in
a new optional column `tocado` of the upcoming jam's tab, owned by the app like the rest of the upcoming
setlist (D-04). The mutation is a repository function (D-13).

## Non-Goals

- No automatic marking (by time, by live window, by order). No "now playing" state, no undo history.
- No played state for past jams in the UI (Anteriores and the past jam detail ignore it), no bulk mark/unmark.
- The phase 2 agent (`:core:assistant`) does not learn about played songs; only the apply plan guards (below).
- No Room migration code: the cache stays destructive (version bump only).
- No new module, no new Gradle dependency, no deeplink.

## Job Story

When the jam is running and a song has just been played,
I want to mark it as played from the list in one tap,
so everyone sees how far the night has gone and nobody edits a song that already happened.

## Users And Permissions

- Admin (passphrase stored): marks/unmarks; sees the toggle; edit controls of a played song are hidden.
  The local flag only decides what is drawn; `setSongPlayed` is authorized by the router's passphrase guard.
- Musician: sees played songs muted with a "Ya tocado" label; no toggle; nothing else changes.
- Apps Script: refuses every guarded write on a played row with `song_played`, whoever sends it (old app
  builds included).

## Decisions (planner defaults; see Open Questions)

- **P1 Sheet value.** Marking writes `sí` (plain text). Unmarking clears the cell (empty). Read rule, identical
  in JS and Kotlin: trim, lowercase, strip the accent; played **iff** the result is `si`. Empty, missing column,
  or any other text (`no`, `x`) = not played. Kotlin logs any other non-empty value as a row issue
  (`SetlistRowIssue.InvalidPlayed`, row kept, not dropped).
- **P2 Read contract.** Every setlist row gains one key, `played`, **after** `extraParticipants`: the raw trimmed
  `tocado` cell or `null` (empty or column missing). The script transports, the Kotlin mapper interprets (as
  for `-` and `Otros`). `schemaVersion` stays **1**: the change is additive; the client decodes with
  `ignoreUnknownKeys = true` (`remote/Envelope.kt`), so old builds ignore it, and the new DTO field defaults to
  `null`, so the new build reads an old script as "nothing played".
- **P3 Column creation.** `tocado` is an optional spec (`PLAYED_FIELD`, `required: false`) appended to
  `setlistSpecs_()` after `Otros`, so `createSetlistTab_` creates new tabs with it (21 columns) and
  `appendSetlistRow_` writes it empty. On an existing tab without the column, `setSongPlayed(played: true)`
  first writes the header `tocado` into the first column after `getLastColumn()` (plain text), then the cell;
  `played: false` on such a tab writes nothing and answers `changed: false`.
- **P4 Server action.** `setSongPlayed({date, songId, played})`, last entry of `ACTIONS`, `write: true`
  (router guard + lock). Checks before any write, in order: `invalid_date`, `invalid_song`, `invalid_played`
  (not a JSON boolean), `unknown_jam`/`duplicate_date`, `jam_not_editable` (`requireEditableJam_`: upcoming
  only, published or not), `song_not_in_setlist` (no tab), `missing_header`/`duplicate_header`,
  `song_not_in_setlist`/`duplicate_song` (`findSongRow_`). Same value already stored → `{ok, played, changed:
  false}` with no write. Otherwise write, read the cell back, answer `{schemaVersion:1, ok:true, played,
  changed:true}`; a read-back that disagrees is an `Error` (`internal_error`).
- **P5 Server guard.** New helper `requireNotPlayed_(sheet, row, columns, songId)` (reads the row's `tocado`
  cell via the P1 rule; missing column = not played) throws `ContractError('song_played', …)`. Called right
  after `findSongRow_` and before any write in `setKey_`, `setSlotCount_`, `assignSlot_`, `clearSlot_`,
  `extraWriteContext_` (covers both extras actions) and `removeSetlistRow_` (covers `removeSong`).
  `moveSong`/`moveSetlistRow_`, `addSong`, `publishSetlist`, `setJamStatus` and jam edits are **not** guarded.
  `updateJam` moving the date keeps the column (whole-tab rename) — verify, do not change.
- **P6 Deploy check.** No new check action: extend `checkSetlistWrite_` (precedent: `setKey`'s step) on its
  temporary tab: mark the marker played with the shared `writePlayedCell_`, assert `requireNotPlayed_` throws
  `song_played`, unmark, assert it passes, read back with `buildSetlist` and assert `played === null`.
- **P7 Toggle placement.** The toggle is in the list's compact `AdminRowBar` (a progress action done in
  sequence during the jam, an explicit exception to "song-level actions go in the detail") **and** in the song
  detail. Both send the same repository call.
- **P8 Open slots of a played song are not open.** The strip and the lineup panel draw them muted (no amber,
  DESIGN.md "amber only for what you can act on"); the instrument filter ("cupo libre") excludes played songs
  from matches and chip counts. The musician filter still includes them (who played what stays findable).
- **P9 Optimism.** Like the key: only a `Sending` entry overlays (the toggled value, `Guardando…`); a failure is
  the revert plus a dismissible card. Writes are FIFO on the shared queue, so an edit queued after an unmark
  reaches the server after it.
- **P10 Assistant.** `replacePlan` returns `Blocked(PLAYED_SONGS)` when `toRemove` contains a played song (the
  server would refuse the removal midway). Append and create are unchanged; moves of played songs are allowed.
  The agent, its tools and prompt are unchanged (it never sees the upcoming setlist's songs).

## Acceptance Scenarios

1. **Mark.** Given admin mode and an unplayed song in the upcoming list, when the admin taps its check icon,
   then the row shows `Guardando…` and the muted look at once, `setSongPlayed(date, id, true)` is sent, the
   Sheet cell becomes `sí`, Room's `jam_song.played` becomes true, and the trash icon is gone from that row.
2. **Unmark.** Given a played song, when the admin taps the icon again (state "Ya tocado"), then the cell is
   cleared, the row returns to normal colours and its removal and detail edit controls come back.
3. **Musician view.** Given a played song in a published jam, a musician sees its position, title and key in
   `archive`, a `Ya tocado` caption, a muted strip (no amber), and TalkBack reads "Ya tocado" in the header.
4. **Read-only detail.** Given a played song, the detail (admin and musician) shows `YA TOCADO`; for the admin
   the key block is not a button, there is no `FORMACIÓN` editor, no `+ Otro`, no `Anotar`/`Liberar`/`Quitar`
   line actions, and the played switch is shown.
5. **Reorder allowed.** Given a played song, the up/down arrows and long-press drag still move it.
6. **Server refuses.** Given a played row, `setKey`, `setSlotCount`, `assignSlot`, `clearSlot`,
   `addExtraParticipant`, `removeExtraParticipant`, `removeSong` each answer `song_played` and write nothing;
   `moveSong` succeeds.
7. **Old app.** An installed build without this feature sending `setKey` on a played song gets the generic
   "planilla rechazó" card (code `song_played` maps to `Unknown`); its reads keep working.
8. **Old script.** The new build against the old script: rows have no `played` → nothing is played;
   `setSongPlayed` answers `unknown_action` → `ScriptOutdated` → card "El script de la planilla todavía no sabe
   marcar temas tocados. Actualizalo con la guía y probá de nuevo." and nothing changes.
9. **Failure.** Offline / access refused / `jam_not_editable`: the optimistic look reverts, a dismissible card
   names the song; Room unchanged.
10. **Filter.** With the Guitarra chip selected, a played song with an open guitar slot is not listed and not
    counted; with a musician chosen, their played songs are listed (muted).
11. **Assistant.** Replace with a proposal that drops a played song → blocked with its own line; nothing sent.

## Repository Research

### Files Inspected

- `backend/apps-script/src/Post.js` — `ACTIONS` (lines 152-180), `setKey_`/`writeKeyCell_`, `findSongRow_`,
  `removeSong_`/`removeSetlistRow_`, `setSlotCount_`, `assignSlot_`, `clearSlot_`, `extraWriteContext_`,
  `moveSong_`, `setlistSpecs_`, `createSetlistTab_`, `appendSetlistRow_`, `newSongCell_`, `checkSetlistRemove_`.
- `backend/apps-script/src/Jams.js` — `SETLIST_FIELDS`, `EXTRA_FIELD`, `buildSetlist` (builds its own spec list).
- `backend/apps-script/src/Code.js` — `SCHEMA_VERSION = 1`.
- `core/model/.../JamSong.kt`; `core/data/.../remote/JamDto.kt` (`SetlistRowDto`), `remote/Envelope.kt`
  (`ignoreUnknownKeys`), `jams/SetlistMapper.kt`, `cache/JamEntities.kt` (`JamSongEntity`, view
  `jam_song_resolved`), `cache/BluesJamDatabase.kt` (version 2, destructive), `cache/SetlistDao.kt`
  (`updateKey`), `cache/SetlistJamEditDao.kt` (`copySongs` lists columns explicitly), `cache/JamCacheMapping.kt`.
- `core/data/.../setlist/` — `SetlistRepository` (union of 12), `DefaultSetlistRepository`, `key/*` (model for
  the new manager), `jamstatus/SetlistJamStatuses.kt` (`unknown_action` → `ScriptOutdated` via `send`),
  `admin/WriteRejection.kt`.
- `feature/next-jam/.../NextJamUiModel.kt` (`SongRowUiModel`, `SongRowAdminUiModel`, `MoveUiModel`),
  `songdetail/SongDetailUiModel.kt`, `songdetail/SongDetailDefaults.kt`, `overlay/SongOverlaySources.kt`,
  `NextJamPresenter.kt` (lines ~395-414, filter application).
- `core/ui/.../strip/InstrumentChipUiModel.kt`, `InstrumentStripDefaults.kt`, `lineup/LineupPanelUiModel.kt`,
  `action/IconRowActionIcon.kt`, `theme/BluesJamColors.kt` (`archive`).
- `feature/assistant/.../run/ReplacePlan.kt`; `core/assistant/...` (no tool lists upcoming songs).
- `app/src/debug/java/com/bbbjam/debug/DemoUpcomingJam.kt`.
- `docs/sheet-schema.md`, `docs/apps-script-api.md`, `docs/migrations/admin-catalog-tab-script-update.md`,
  `DESIGN.md` (Colors, Song row, Song detail), `bb-blues-jam-bitacora.md` (D-30 format), architecture skill.

### Existing Patterns To Follow

- A row mutation: validate → `requireEditableJam_` → `findSongRow_` → write mapped cell as plain text (`@`).
- A new mutation = subpackage of `core.data.setlist` with contract, outcome, entry type and manager on
  `queue.enqueue` + `PendingEntries`; Room only after confirmation; `ScriptOutdated` from `unknown_action`.
- One-statement guarded DAO `UPDATE` (`updateKey`), so the DAO stays under detekt's `TooManyFunctions`.
- Amber read only inside `:core:ui` components; features read `BluesJamTheme.colors`, copy in `strings.xml`.

### Current Gaps / Traps

- `SetlistJamEditDao.copySongs` names columns: **must add `played`** or a date edit loses the state in cache.
- `jam_song_resolved` must select `s.played`, and `JamSongResolved` gain it, or the read never sees it.
- `removeSetlistSong`/`moveSetlistSong` reinsert entity copies: test that `played` survives both.
- `docs/apps-script-api.md` says rows have "exactly these keys": update, and update `docs/api-samples/jams-*.json`
  if the `:core:data` sample tests compare key sets.

## Technical Approach

### Part A — backend and data

1. `Jams.js`: `PLAYED_FIELD = { header: 'tocado', field: 'played', required: false }`; `buildSetlist` adds it to
   its specs and sets `row.played = read(PLAYED_FIELD)` after `extraParticipants`; `isPlayedText_(text)` (P1),
   exported for tests and Post.js.
2. `Post.js`: `setlistSpecs_()` appends `PLAYED_FIELD`; `requireNotPlayed_` (P5) wired into the six places;
   `writePlayedCell_(sheet, row, columns, played)` (adds the header when missing, P3; plain text; `sí` or `''`);
   `setSongPlayed_` (P4); `checkSetlistWrite_` extra step (P6). Nothing else changes.
3. `:core:model`: `JamSong.played: Boolean = false` (last parameter, KDoc: app-owned, D-04, D-31).
4. `:core:data`: `SetlistRowDto.played: String? = null`; `SetlistMapper` parses with the P1 rule (a pure
   `isPlayedCell(text)` beside the mapper, unit-tested with the same table as JS) and the new row issue;
   `JamSongEntity.played: Boolean` (`@ColumnInfo(defaultValue = "0")`), view and `JamSongResolved`, cache mapping,
   `copySongs`; database `version = 3`. New `SetlistPlayedDao` (extended by `SetlistDao`) with
   `updatePlayed(date, songId, played): Int`, one `UPDATE` with the same `AVAILABLE` and exactly-one-id
   conditions as `updateKey`. `WriteRejection.SONG_PLAYED("song_played")`.
5. Package `core.data.setlist.played`: `SetlistPlayedRepository { suspend fun setSongPlayed(jamDate, songId,
   played: Boolean): SetSongPlayedOutcome; fun observePlayedChanges(): Flow<List<PlayedChange>>; fun
   dismissPlayedChange(id: Long) }`; `SetSongPlayedOutcome` (`PlayedSet(played)`, `NotSet(reason)`,
   `ScriptOutdated`); `PlayedChange(id, jamDate, songId, title, played, state)` with `Sending`, `Failed(reason)`,
   `Outdated`; internal `SetlistPlayedChanges` (uses `writer.send`, checks `answer.played == requested`, mirrors
   with `updatePlayed` then removes the entry; `SQLException` on mirror ignored like the key). Added to the
   `SetlistRepository` union (13 contracts) and `DefaultSetlistRepository` by delegation.
6. `:app` `appModule`: `single<SetlistPlayedRepository> { get<SetlistRepository>() }`; `AppModuleBindingsTest`
   adds the `assertSame`.

### Part B — UI, assistant, docs

1. `:core:ui`: `InstrumentStrip(chips, muted = false)` and `LineupPanelUiModel.muted: Boolean = false`;
   `InstrumentStripDefaults.mutedStyle(kind)` (every kind: `slotFilled` fill, `archive` text, no amber, no glyph
   change) used by both. `IconRowActionIcon.Played(Icons.Filled.CheckCircle)` (in material-icons-core). A
   toggle variant `IconRowToggle(icon, checked, description, stateDescription, enabled, onToggle)` with
   `Role.Switch`/`toggleable` semantics, checked tint `text`, unchecked `textMuted`, never amber.
2. `:feature:next-jam` (subpackages per architecture skill):
   - `SongRowUiModel` gains `played: Boolean` and `playedLabel: UiText?` (`Ya tocado`); colours in
     `NextJamDefaults`/row defaults: position, title and key in `archive` when played (the key leaves `key`
     amber), strip muted.
   - `SongRowAdminUiModel` gains `played: PlayedToggleUiModel(checked, enabled, description, stateDescription,
     events: Toggle)`; `removal` becomes nullable (null while played). Move stays.
   - New `overlay/PlayedOverlay.kt`: `List<PlayedChange>.pendingPlayed(date, songId)` (highest-id `Sending`);
     `SongOverlays`/`SongOverlaySources` gain the played source so list and detail share one rule; effective
     `played` = pending ?: confirmed; `isSaving` includes it; failures merged by id with copy from
     `FailureMessages` (`song_played` → "Ese tema ya está marcado como tocado; desmarcalo para cambiarlo.").
   - `admin/PlayedControls.kt`: handler launches `setSongPlayed` undispatched with `!effectivePlayed`; a second
     tap while that song has a `Sending` played entry does nothing (one write per tap pair).
   - `NextJamPresenter` instrument filter: rows and bar built from non-played songs for the instrument
     predicate (`!played && matchesInstrumentFilter`), after the musician predicate (P8).
   - Song detail: `SongDetailUiModel.Song.playedLabel: UiText?` (`YA TOCADO`, caption `archive`); for the admin
     `SongDetailAdminUiModel.played` (the switch row "Ya tocado"), and `setKey`, `lineup`, `extraParticipants`
     nullable (null while played) and the panel built without line actions, `muted = true`.
   - `SetKeyPresenter` and `AssignMusicianPresenter`: a played song is `Gone`.
3. `:feature:assistant`: `ReplacePlan.Blocked.Reason.PLAYED_SONGS` + its copy line ("La lista tiene temas que
   ya se tocaron y este reemplazo los sacaría. Desmarcalos o usá Agregar al final."), mapped where the other
   reasons are.
4. Debug: `DemoUpcomingJam` marks its first song played so the muted look is checkable on a device without
   Sheet data (fixture tests updated).
5. Strings (vos, `strings.xml`): `Ya tocado`, `Marcar como tocado`/`Desmarcar como tocado` (descriptions),
   state descriptions `Tocado`/`Sin tocar`, `YA TOCADO`, the outdated card, the rejection line, the assistant line.

## Expected File Changes

- `backend/apps-script/src/Jams.js`, `src/Post.js` — modify; `test/jams.test.js`, `test/setlist.test.js`,
  `test/router.test.js` (or a new `test/played.test.js`) — modify/create.
- `core/model/.../JamSong.kt` (+ test) — modify.
- `core/data/.../remote/JamDto.kt`, `jams/SetlistMapper.kt`, `jams/SetlistRowIssue` file, `cache/JamEntities.kt`,
  `cache/JamCacheMapping.kt`, `cache/BluesJamDatabase.kt`, `cache/SetlistDao.kt`, `cache/SetlistJamEditDao.kt`,
  `admin/WriteRejection.kt`, `setlist/SetlistRepository.kt`, `setlist/DefaultSetlistRepository.kt` — modify;
  `cache/SetlistPlayedDao.kt`, `setlist/played/*` (4 files) — create; tests beside them.
- `app/.../AppModule.kt`, `AppModuleBindingsTest` — modify; `app/src/debug/.../DemoUpcomingJam.kt` (+ testDebug).
- `core/ui/.../strip/*`, `lineup/*`, `action/IconRowActionIcon.kt`, new `action/IconRowToggle.kt` (+ defaults test).
- `feature/next-jam/...` — files named in Part B; tests `NextJamPlayedTest`, `SongDetailPlayedTest` (create).
- `feature/assistant/.../run/ReplacePlan.kt`, its copy/mapping, `ReplacePlanTest` — modify.
- Docs listed below.

## Visual Design Impact

- UI involved: yes. Source: `DESIGN.md` (Colors, Song row, Instrument strip, Lineup panel, Song detail, Admin
  controls). Tokens only (`BluesJamTheme.*`, D-17); no new amber use; Konsist `AMBER_ROLE_ALLOWLIST` unchanged.
- Played row: `archive` for position/title/key, `Ya tocado` caption (`caption`, `archive`) under the title,
  muted strip. Colour is never the only signal (WCAG 1.4.1): the caption and the header semantics carry it.
  `ContrastTest` gains `archive` on `background` and on `adminBackground` (expected about 5.8:1 and 5.5:1, ≥ 4.5).
- Admin bar: arrows | check toggle | gap | trash (trash absent while played). 48dp targets.
- New design artifact required: no; DESIGN.md gets a "Played song" subsection.

## Durable Documentation Impact

- `docs/sheet-schema.md` — update: `tocado` row in the jam tab table, P1 rule, P3 creation, guard list.
- `docs/apps-script-api.md` — update: `played` in Setlist row (exact key list), `setSongPlayed` section with the
  check table, `song_played` on the seven guarded actions, `checkSetlistWrite` step, client mutation list.
- `docs/domain-model.md` — update: `JamSong.played`, authority (app, upcoming only), read-only rule.
- `DESIGN.md` — update: Played song subsection, Song row bar, Song detail read-only state.
- `docs/migrations/song-played-state-script-update.md` — create, modeled on
  `admin-catalog-tab-script-update.md`: what breaks before the update (the toggle ends in the outdated card);
  files changed (`Jams.gs` **and** `Post.gs`); paste-verification strings (`setSongPlayed: { write: true, run:
  setSongPlayed_ },`, `function requireNotPlayed_`, `var PLAYED_FIELD`, `function isPlayedText_`); Manage
  deployments → New version (never New deployment); no-passphrase deploy probe: POST `{"action":"deployProbe"}`
  → `unknown_action` whose list ends `..., checkCatalogWrite, setSongPlayed`, and `GET ?resource=jams` rows now
  carry `"played":null`; optional `checkSetlistWrite` with the passphrase; the Sheet needs no manual change (the
  column is added on first mark). Note that old app builds keep working.
- `bb-blues-jam-bitacora.md` — add **D-31** after D-30 (text below).
- Architecture skill — add "As built by `song-played-state`" paragraph (new subpackage, 13 contracts, guard).
- `docs/risks-and-open-questions.md` — not needed unless a question stays open.
- `AGENTS.md` — update the decision range "D-01 … D-30" to D-31 where it appears. `ARCHITECTURE.md`,
  `CONSTRAINTS.md`: do not exist; not needed.

### D-31 text for the implementer (Spanish, bitácora)

```
### D-31 — Temas ya tocados (song-played-state)
Durante la jam el admin marca un tema de la próxima jam como "ya tocado" y lo puede desmarcar. Se guarda en una
columna opcional `tocado` de la pestaña de la jam (`sí` o vacío; si falta la columna, nada está tocado) y es de la
app, como el resto de la lista que viene (D-04). Es una mutación de repositorio (`setSongPlayed`, D-13) que Apps
Script valida con la frase de acceso. Mientras está tocado no se puede cambiar el tono, los cupos, los músicos ni
los Otros, ni sacarlo de la lista: el servidor lo rechaza con `song_played` aunque lo pida una versión vieja de la
app. Sí se puede mover. Todos lo ven apagado, y sus cupos libres dejan de contar como libres. El asistente no lo
conoce; solo se bloquea un reemplazo que sacaría un tema tocado. Decisiones del organizador del 9 de noviembre de 2026.
```

## Implementation Plan / Tasks

1. JS: `PLAYED_FIELD`, `isPlayedText_`, `buildSetlist` → node tests (missing column null, `sí`/`Si`/`SI ` played,
   other text raw).
2. JS: `setlistSpecs_`, `writePlayedCell_`, `requireNotPlayed_` in six places, `setSongPlayed_`, `ACTIONS`,
   `checkSetlistWrite_` step → node tests per check order, idempotence, header creation, read-back, each
   guarded action refused with no write, `moveSong` allowed, new tab has `tocado`.
3. Kotlin model + DTO + mapper + Room (v3, view, copySongs, DAO) → JVM tests (mapper table, DAO update guard,
   move/remove/copy keep `played`, decode a row without `played`).
4. `setlist/played` manager + union + `:app` binding → repository tests (Done mirrors then removes; Rejected
   and `unknown_action` leave Room; FIFO with a key change; dismiss own kind only). **Gate.**
5. `:core:ui` muted strip/panel, toggle, icon → defaults tests, ContrastTest.
6. `:feature:next-jam` overlay, controls, row/detail models, filter rule, pickers `Gone` → Molecule tests.
7. Assistant `PLAYED_SONGS` → `ReplacePlanTest`.
8. Demo fixture, strings, docs, migration guide, D-31, architecture skill. `./gradlew ktlintFormat`, then gate.

## Verification Plan

- `cd backend/apps-script && node --test` — all pass, new cases above.
- JVM: `NextJamPlayedTest` (mark/unmark optimistic and confirmed, failure revert + card, removal hidden, move
  present, musician muted label, filter exclusion and musician inclusion), `SongDetailPlayedTest` (read-only
  admin model, switch, musician label), mapper, DAO, repository, `ReplacePlanTest`, defaults/contrast tests.
  Each presenter test asserts at least one transition.
- `CI=true ./init.sh` exits 0 with `konsist`, `detekt`, `ktlint` all `wired`. No baseline, no rule disabled.
- Manual, device (cannot be in the gate; no Compose UI harness): with `bluesjam.demoUpcomingJam=true`, as a
  musician confirm the first demo song is muted with `Ya tocado` and TalkBack reads it (`uiautomator dump`).
  Admin toggling against the demo jam reaches the real script and will be refused (`song_not_in_setlist`);
  do not use real Sheet test data (user rule). The real end-to-end toggle is the user's step after the
  redeploy (migration guide, optional step), on the real upcoming jam.
- Live deploy probe after the user redeploys (no passphrase, no write): as in the migration guide.

## Evidence To Capture

- node test count and `init.sh` tail in `feature_list.json` evidence; new test names and counts per module.
- Proof that `copySongs` keeps `played` (test name). Deploy probe output once the user redeploys.
- What was NOT run (device, live script) stated explicitly.

## Validator Checklist

- [ ] Every guarded action calls `requireNotPlayed_` before its first write; `moveSong` does not.
- [ ] `setSongPlayed` is router-guarded (`write: true`), never calls the passphrase guard itself.
- [ ] `played` key present in GET and `readJams` rows; `schemaVersion` still 1; old-script decode tested.
- [ ] Room: view, entity, `copySongs`, version 3; mirror only after confirmation.
- [ ] 13 contracts, `:app` binding with `assertSame`; no feature injects `SetlistRepository` (Konsist 20).
- [ ] No amber added in features; `archive` contrast tested; copy in `strings.xml`, vos.
- [ ] Musicians see muted + label; admin edit controls absent while played (list and detail); reorder works.
- [ ] Docs, migration guide, D-31, architecture skill updated; `init.sh` three tools wired.

## Open Questions (defaults applied; confirm or override)

1. P8: a played song's open slots stop counting for the instrument filter and are drawn without amber. Default yes.
2. P7: toggle in both the list bar and the detail. Default both.
3. P3: the server adds the `tocado` header on first mark of an older tab. Default yes (alternative: refuse
   with `missing_header` and ask the admin to add the column by hand).
4. P1: only `sí`/`si` (any case) counts as played; `x` or `✓` typed by hand do not. Default yes.
