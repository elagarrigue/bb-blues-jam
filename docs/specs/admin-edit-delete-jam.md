# Feature Implementation Spec: The admin edits the date, time and venue of a future jam, or deletes it

## Source Feature

- `id`: admin-edit-delete-jam
- `area`: the entry says `feature-next-jam`; the UI is in `:feature:past-jams` (the Jams tab). Also backend, `:core:data`,
  `:app`. Recommend the orchestrator set `area` to `feature-past-jams` (not a missing field; not changed here).
- `depends_on`: admin-jams-tab (accepted 14 October 2026, commit 7922812)
- `status`: not_started at planning time
- `source`: `feature_list.json`; user request of 14 October 2026 ("manage jams entirely from the app"); D-04, D-13, D-24,
  D-25, D-26, D-27; admin-jams-tab U1 folded in; **D-28** (below, confirmed by the user's request)

## Goal

In the Jams tab every future jam card (the upcoming one and the held-back ones) gets `Editar la jam` (fecha, hora,
lugar) and `Borrar la jam`. A date change moves the jam with its tab, setlist and musicians. Deleting always confirms,
names the song count, what musicians stop seeing, and which jam is the upcoming one afterwards. Held-back cards also
get the status actions (U1). Every write is a server-validated action (router guard, lock, refusals before any write,
read-back), a repository function and a registry entry (D-13); Room follows only a confirmed answer. Past jams stay
Sheet-only (D-04). The slice ends with a migration guide for the `Post.gs` redeploy.

## Non-Goals

- No edit or delete of a past jam, in the app or on the server. No edit of `estado` through `updateJam`.
- No change to `createJam`, `publishSetlist`, the GET, `readJams`, `Code.js`, `Jams.js`. `setJamStatus` changes only as U1.
- No assistant tool, proposal or prompt change (D-24); the registry entries are the phase 2 surface.
- No change to `:feature:next-jam` beyond fake stubs (see E8). No new module, Konsist rule, amber role, Room schema
  change or navigation route.
- No write to the real Sheet during verification. The redeploy is the user's step.

## Job Story

When the venue changes, a date moves or a jam is cancelled, I want to fix or remove it from the Jams tab, so I never
open the Sheet and musicians see the right thing on their next refresh.

## Users And Permissions

Admin (flag on) sees the controls; Apps Script authorizes every write (D-11). Musicians see no control and see the
result on their next refresh under the D-26 visibility rules (unchanged).

## Decision D-28 (user-confirmed; amends D-25 and D-04)

D-25 says "editarla después sigue siendo cosa del Sheet". The user explicitly asked to edit and delete jams from the
app, which the orchestrator records as the confirmation. Text to append to `bb-blues-jam-bitacora.md` after D-27:

*"### D-28 — El admin edita y borra las jams que vienen desde la app. Enmienda a D-25 y D-04. En la pestaña Jams, la
próxima jam y las que esperan detrás se pueden editar (fecha, hora y lugar, con `updateJam`) y borrar (`deleteJam`,
siempre con confirmación). Cambiar la fecha mueve la jam con su pestaña, su lista y sus músicos; borrarla borra la fila
y la pestaña. La edición nunca cambia cuál es la próxima jam. Las jams en espera también cambian de estado desde la app;
publicar sigue siendo solo para la próxima. Las jams pasadas siguen siendo del Sheet. El Sheet se puede seguir editando
a mano, pero ya no es el camino previsto: si los dos escriben, gana la última escritura, sin sincronización. Pedido del
organizador del 14 de octubre de 2026."*

## Decisions (planner)

Backend (re-checked against `Post.js` at 7922812):

- **E1 Actions.** `updateJam`, `deleteJam`, deploy check `checkJamEdit`, appended to `ACTIONS` in that order after
  `setJamStatus`, all `write: true` (router guard and lock; actions never call the guard).
- **E2 `updateJam({date, newDate?, time?, venue?})`.** Absent fields stay; the app always sends all three. Refusals, all
  before any write, in order: `invalid_date`; `invalid_new_date` (present, not a calendar `YYYY-MM-DD`, `isCalendarDate_`);
  `invalid_time` (present, not `POST_TIME_`); `invalid_venue` (present, `normalizeVenue_`); `invalid_update` (none
  present); then `requireFutureJam_` (new, below: `missing_tab`, `unknown_jam`, `duplicate_date`, `jam_not_editable`);
  `past_date` (newDate < `todayIso_`); `jam_exists` (another Jams row, any `estado`, has newDate, via `buildJams` as
  `createJam_` does); `tab_exists` (a sheet named newDate exists). All given values equal to the row's (venue after
  normalization) → `changed: false`, no write. Writes: (1) date changes and a tab named `date` exists → `setName(newDate)`,
  then check `getSheetByName(newDate)` is it and `getSheetByName(date)` is null; (2) only changed cells of `fecha`/`hora`/
  `lugar`, plain-text format first, values, `flush`, read back each. A mismatch restores the tab name (best effort, in
  `try`) and throws `Error` (`internal_error`). Answer `{ok, date: <new or same>, previousDate, time, venue, status,
  changed, tabRenamed}`.
- **E3 `requireFutureJam_(spreadsheet, services, date)`.** Like `requireEditableJam_` without the "no earlier jam" clause:
  `missing_tab`, `unknown_jam`, `duplicate_date`, then `jam_not_editable` for an unknown `estado` or a date before today.
  `requireEditableJam_` is unchanged (every setlist mutation keeps it: setlists are built only on the upcoming jam).
- **E4 `deleteJam({date, expectedSongs})`.** Refusals before any write: `invalid_date`, `invalid_expected_songs` (missing,
  not a non-negative integer), `requireFutureJam_` codes, any `buildSetlist` error of an unreadable tab (as-is),
  `setlist_changed` (tab song count, 0 with no tab, differs from `expectedSongs`). Writes: delete the jam tab if present and
  check it is gone, then `deleteRow` the Jams row, `flush`, read back (date no longer found, else `Error`). The tab is
  deleted, not kept: an orphan tab would make `createJam` refuse that date (`tab_exists`). Tab first, so a failure in
  between leaves a jam with an empty setlist (retryable), never an orphan tab. Answer `{ok, date, deletedSongs, tabDeleted}`.
- **E5 `checkJamEdit`.** Works only on tabs named `_prueba_editar_jam*` (`JAM_EDIT_CHECK_TAB = '_prueba_editar_jam'`):
  deletes leftovers first, builds a Jams-like tab with two rows and a jam tab, runs the shared helpers (rename, cell
  write, tab and row delete) with check names, reads back, deletes everything in `finally`, never opens `Jams`.
  The helpers therefore take the sheet and the tab names as parameters (`renameJamTab_`, `writeJamCells_`,
  `deleteJamTabAndRow_`), as `checkPublish_` reuses `findJamRow_`/`writeJamStatus_`.
- **U1 (folded in from admin-jams-tab) Held-back status.** `setJamStatusCore_` calls `requireFutureJam_` for targets
  BORRADOR and CREADA and keeps `requireEditableJam_` for PUBLICADA (also the `publishSetlist` alias). Publishing rules are
  unchanged: only the upcoming jam publishes, because only it has an app-built setlist (`addSong` is upcoming-only).
  Leaving PUBLICADA on a held-back jam (possible after a hand edit) is allowed. No concrete reason was found against it:
  the client (`SetlistJamStatuses`, `updateJamStatus`) has no upcoming-only assumption, and a held-back CREADA jam behind
  a BORRADOR upcoming jam becoming the musicians' next jam is exactly D-26's `forMusicians()` rule; the card says so (C3).
  Amended pre-existing node assertion: `{ date: LATER, status: 'CREADA' } → jam_not_editable` becomes accepted; a new
  `{ date: LATER, status: 'PUBLICADA' } → jam_not_editable` case is added.

Client (re-checked against `:core:data`/`:app` at 7922812):

- **E6 Repository.** New public `setlist/SetlistJamEditRepository` (part of `SetlistRepository`): `suspend fun
  updateJam(jamDate: LocalDate, newDate: LocalDate, startTime: LocalTime, venue: String): UpdateJamOutcome`, `suspend fun
  deleteJam(jamDate: LocalDate, expectedSongs: Int): DeleteJamOutcome`, `fun observeJamEdits(): Flow<List<JamEdit>>`,
  `fun dismissJamEdit(id: Long)`. `UpdateJamOutcome`: `Updated(date, startTime, venue, changed)`, `NotUpdated(reason:
  WriteOutcome)`, `ScriptOutdated`. `DeleteJamOutcome`: `Deleted(songs)`, `NotDeleted(reason)`, `ScriptOutdated`.
  `JamEdit(id, jamDate, change, state)`, `change` = `Update(newDate, startTime, venue)` | `Delete(expectedSongs)`, `state`
  = `Sending` | `Failed(reason)` | `Outdated`. Internal `SetlistJamEdits` (split if detekt asks) built by
  `DefaultSetlistRepository` on the shared `SetlistMutationQueue`, with exactly `SetlistJamStatuses`' pattern
  (`scope.async(UNDISPATCHED)` → `queue.order` → entry → `scope.async(UNDISPATCHED)` → `queue.writes` → `AdminWriter.send`;
  since c356bec the transport's `yield()` moves the request to IO, add no dispatcher). `unknown_action` → `ScriptOutdated`;
  an `Ok` whose echo differs (`date`, `previousDate`, `time`, non-blank `venue`, boolean `changed`; delete: `date`,
  integer `deletedSongs`) → `Unavailable`. One `WriteFailureLog` line per failure (`updateJam failed: date=… newDate=…
  outcome=…`, never the venue). `DefaultSetlistRepository.dismiss(id)` also dismisses jam edits.
- **E7 Room after confirmation only.** New `cache/SetlistJamEditDao`, extended by `SetlistDao`: same date →
  `UPDATE jam SET start_time, venue WHERE date`; new date → one `@Transaction moveJam(old, new, time, venue)`:
  `INSERT … SELECT` the `jam` row under the new date (same `sheet_order`, status, setlist state/problem, dropped rows),
  then `jam_song`, `jam_slot`, `jam_extra`, then `DELETE FROM jam WHERE date = old` (children cascade); nothing when the old
  jam is not cached or the new date already is (the next refresh repairs it); delete → `DELETE FROM jam WHERE date`. The
  snapshot split then makes the first held-back jam upcoming by itself. `SQLException` ignored (the Sheet is authoritative).
  No optimistic date, time, venue or removal. No schema change (version stays).
- **E8 Concurrent writes.** Writes are FIFO; a setlist write queued on Próxima jam after an edit or delete targets the old
  date and fails server-side (`unknown_jam`) with nothing written; overlays and failure cards in `:feature:next-jam`
  filter by jam date (verified: `NextJamAdminMapping`, `SongOverlays`, `*Overlay.kt`), so stale entries stop showing. An
  open song detail or picker on the old date shows its not-found state. So Próxima jam is **not** changed (the earlier
  draft hid its controls; dropped as unnecessary: no corruption is possible). Documented in risks. In the Jams tab, while an
  edit or delete of a jam is `Sending`, its card shows one sending line and no actions (status, publish, edit, delete);
  edit and delete are also hidden while a status change or publication of that jam is sending.
- **E9 Edit date rule (D-25's block, generalized): an edit never changes which jam is the upcoming one.** New pure
  `jams/JamEditPlan.kt` in `:core:data`, beside `jamCreationPlan`: `jamEditPlan(jamDate, newDate, snapshot, today):
  JamEditPlan` = `Unknown` (null snapshot) | `Blocked(reason, otherDate?)` | `Ready(dateChanged)`. Reasons: `GONE` (jamDate
  is not the raw snapshot's upcoming or a held-back jam), `PAST` (newDate < today), `EXISTS` (another upcoming, held-back
  or past jam has newDate), `LATER` (the upcoming jam moved on or after the first held-back jam; `otherDate` = that jam),
  `EARLIER` (a held-back jam moved on or before the upcoming jam; `otherDate` = upcoming). Held-back jams may reorder among
  themselves. Same date → `Ready(false)`. The server stays generic (accepts LATER/EARLIER), as `createJam` did.
- **E10 Registry.** `SetlistMutation.UPDATE_JAM`, `DELETE_JAM` (12 → 14); `SetlistAction.UpdateJam(jamDate, newDate,
  startTime, venue)`, `DeleteJam(jamDate, expectedSongs)`; `SetlistActionResult.JamUpdated`, `JamDeleted`. The test is
  updated and shown failing first. `jamSchedule` keeps reading the snapshot, so the assistant sees edits after they land.

UI (`:feature:past-jams`, re-planned for the Jams tab):

- **C1 Contracts (J1 pattern).** `:app` `appModule` adds `single<SetlistJamEditRepository> { get<SetlistRepository>() }`.
  To stay under detekt's `LongParameterList`, the feature groups its write contracts in a plain class
  `JamsAdminWrites(statuses, publishes, creator, edits)` (constructor injection, `factory` in `pastJamsModule`), and
  `PastJamsPresenter(jams, calendar, adminSession, writes)`; `rememberJamsAdminState(writes, today, isAdmin)`. Amended:
  presenter construction in the past-jams tests and `PastJamsModuleTest`. Still never `SetlistRepository`.
- **C2 Card actions.** Every `FutureJamUiModel` gets `controls` (status for all; `publish` only on the upcoming card, as
  today) and `manage: JamManageUiModel(editLabel, deleteLabel?, sending?, failure?, events)`. Neutral underlined `text`
  actions (`PublishTextAction`), 48dp, `Role.Button`, never amber, after the status actions and publish. `Borrar la jam`
  only when the admin setlist is `Available` (count known); with an unreadable setlist, edit only.
- **C3 Held-back card text.** `PastJamsCopy.HELD_BACK` becomes `En espera: la próxima jam es la del <sábado 31 de
  octubre>.` A held-back card whose status is CREADA or PUBLICADA while the upcoming jam is BORRADOR adds `Los músicos la
  ven como la próxima jam, porque la del <sábado 31 de octubre> está en borrador.` (pure, in the mapping).
- **C4 Edit dialog (reuses Nueva jam).** Section-level `JamsAdminUiModel.editForm: EditJamFormUiModel?` (one modal at a
  time). Opening seeds `Fecha` with new `formatFormDate(date)` (`DD/MM/AAAA`), `Hora` with `formatFormTime`, `Lugar` with
  the venue; fields saveable, keyed by a saveable editing-date key, reset on each open. Parsing reuses `parseFormDate`,
  `parseFormTime`, `venueProblem`/`normalizeVenue` (`NewJamForm.kt`); new pure `checkJamEdit(jam, input, snapshot, today)`
  in `JamEditForm.kt` over `jamEditPlan`, returning per-field problems, the values and `canSave` (valid, `Ready`, and
  something changed). Errors appear like Nueva jam (typed or touched). The views reuse `FormField` (made `internal`) and
  the dialog colours of `NewJamDialog` (extract a shared internal `JamFieldsDialog` if it removes duplication). `Guardar`
  closes the dialog and launches `edits.updateJam` undispatched behind an in-flight guard (double tap writes once). If the
  jam disappears from the snapshot while open, the dialog is not drawn and the key is cleared.
- **C5 Delete dialog.** Section-level `deleteConfirm: DeleteJamUiModel?`, saveable confirming-date key, modal `AlertDialog`
  like `UnpublishDialog` (`surfaceRaised`, `text` title, `textMuted` body, `error` confirm, `text` `Cancelar`; back/outside =
  Cancel). Confirm launches `edits.deleteJam(date, count)` undispatched behind the same in-flight guard.
- **C6 Results.** No success message: the card moves or disappears from Room. Failures are a card in the jam's card, the
  status card's component (assertive), `Reintentar` resends the same change (dismiss then send, as status retry), `Cerrar`
  dismisses. A failed update's card shows on the jam at its old date. Failure copy maps in `JamsFailureMessages.kt`.
- **C7 Composition loss.** As J9: if the tab's composition dies mid-call the write finishes in `DataScope`; the entry and
  its failure card come from the repository, so nothing is lost.

## Copy (Rioplatense, vos; new `internal object JamEditCopy` in `:feature:past-jams`)

- Actions `Editar la jam`, `Borrar la jam`; sending `Guardando los cambios…`, `Borrando la jam…` (polite).
- Edit dialog: title `Editar la jam`; labels/hints as Nueva jam (`Fecha` `DD/MM/AAAA`, `Hora` `HH:MM`, `Lugar`); note when
  the date differs `La lista y los músicos anotados pasan a la nueva fecha.`; note for CREADA/PUBLICADA `Los músicos ven
  el cambio cuando actualicen la app.`; buttons `Guardar`, `Cancelar`. Field errors reuse Nueva jam's lines (format, past,
  `Ya hay una jam ese día.`, time, venue) plus LATER `Tiene que ser antes de la jam del <viernes 13 de noviembre>: si no,
  esa pasaría a ser la próxima.` and EARLIER `Tiene que ser después de la próxima jam (<sábado 31 de octubre>): si no,
  la dejaría en espera.`
- Delete dialog: title `¿Borrar la jam del <sábado 31 de octubre>?`; songs `La lista está vacía.` / `Se pierde el tema de
  la lista y los músicos anotados.` / `Se pierden los <N> temas de la lista y los músicos anotados.`; PUBLICADA `Los
  músicos ya ven esta jam y su lista: la van a dejar de ver cuando actualicen la app.`; CREADA `Los músicos ven la fecha:
  la van a dejar de ver cuando actualicen la app.`; BORRADOR none; next line, upcoming card: `La próxima jam pasa a ser la
  del <viernes 13 de noviembre>.` or `No va a quedar ninguna jam programada.`; held-back card: `La próxima jam sigue siendo
  la del <sábado 31 de octubre>.`; then `No se puede deshacer.`; buttons `Borrar la jam` (`error`), `Cancelar`.
- Failure cards: `No se pudo guardar la jam` / `No se pudo borrar la jam`, consequence `La jam sigue como estaba.`,
  `Reintentar`, `Cerrar`. Reasons: `jam_exists`/`tab_exists` → `Ya hay una jam o una pestaña con esa fecha en la
  planilla.`; `past_date` → `Esa fecha ya pasó.`; `setlist_changed` → `La lista cambió mientras tanto. Revisala y probá de
  nuevo.` (`Cerrar` only); `invalid_*` → `PastJamsCopy.CREATE_INVALID`; others via `failureMessage`. Outdated: `El script
  de la planilla todavía no sabe editar ni borrar jams. Actualizalo con la guía y probá de nuevo.` (`Cerrar` only).

## Acceptance Scenarios

1. **Update, node.** Upcoming jam with a tab: time only → only `hora` written; venue only (`"  La  Macanuda "` → `La
   Macanuda`); date only → tab renamed and only `fecha` written, setlist rows untouched; all three; a held-back jam
   without a tab: date change writes only `fecha`. Each read back, `changed: true`, `estado` never written. Same values →
   `changed: false`, zero writes, lock taken and released.
2. **Update refusals, node, zero writes each:** every E2 code in order, incl. a past jam and a typo `estado`
   (`jam_not_editable`), newDate before today, newDate of a past row (`jam_exists`), a stray tab (`tab_exists`). A
   held-back jam, an upcoming BORRADOR and a LATER date are accepted. Read-back mismatch → `internal_error`, tab name restored.
3. **Delete, node.** Tab of 3 songs, `expectedSongs: 3`: tab then row, `deletedSongs: 3, tabDeleted: true`. No tab, `0`: row
   only. Every E4 refusal with zero writes; `setlist_changed` for 2 vs 3. A surviving row → `internal_error`.
4. **U1, node.** Held-back `CREADA`/`BORRADOR` accepted (only its `estado` cell written); held-back `PUBLICADA` →
   `jam_not_editable`; `publishSetlist` on a held-back jam still refused; every other existing `setJamStatus` test unchanged.
5. **Router and check, node.** The three names last in `ACTION_NAMES` and both `Known:` messages (the only other amended
   assertions; list them), `write: true`, guard-covered; `checkJamEdit` touches only `_prueba_editar_jam*`, cleans up in
   `finally` (also on failure and a leftover), never opens `Jams`.
6. **Repository, JVM.** `Updated` with new date: jam, songs, slots, extras under the new date, old gone; same date: time and
   venue updated; `Deleted`: jam and children gone and the next held-back jam is the snapshot's upcoming. Refusal: `Failed`,
   Room unchanged. `unknown_action` → `ScriptOutdated`/`Outdated`. Wrong echo → `Unavailable`. FIFO with other mutations; a
   cancelled caller does not cancel the write.
7. **Plan, JVM (pure).** `jamEditPlan`: every reason, held-back reorder allowed, earlier upcoming date allowed, today
   allowed, same date `Ready(false)`.
8. **Registry, JVM.** 14 entries cover `SetlistMutation.entries`; `UpdateJam`/`DeleteJam` dispatch exact arguments and wrap
   outcomes. Shown failing first.
9. **Form, JVM (pure).** `formatFormDate` round-trips `parseFormDate`; `checkJamEdit` errors per field, LATER/EARLIER only
   with the relevant jam, `canSave` false when nothing changed (venue compared normalized).
10. **Presenter, Molecule.** Admin: every future card has `Editar la jam`; `Borrar la jam` only with an available setlist;
   held-back cards have status actions (`Pasar a creada`) and no publish; C3 lines. Musician: no admin section (unchanged).
   Edit: open → seeded; `Cancelar` writes nothing; `Guardar` calls `updateJam` once with parsed values (double tap once).
   Delete: body per status, count and next-jam line for upcoming and held-back; `Cancelar` writes nothing; confirm calls
   `deleteJam(date, count)` once. `Sending`: one line, no actions on that card only. `Failed`, `Outdated`, `setlist_changed`
   cards. After the fake emits the moved jam, the card shows the new date.
11. **Device, read-only.** See Verification.

## Repository Research

Inspected at 7922812: `AGENTS.md`, `PROGRESS.md`, `feature_list.json` (both entries), the architecture skill (incl. "As
built by admin-jams-tab"), `docs/specs/admin-jams-tab.md`, the earlier draft of this spec, `DESIGN.md` (Jams tab, Changing
the status), `bb-blues-jam-bitacora.md` (D-26, D-27), `docs/migrations/assistant-create-jam-script-update.md`,
`backend/apps-script/src/Post.js` (header, `ACTIONS`, `setJamStatus_`, `setJamStatusCore_`, `publishSetlist_`,
`checkPublish_`, `normalizeVenue_`, `createJam_`, `appendJamRow_`, `checkCreateJam_`, `findJamRow_`, `writeJamStatus_`,
`todayIso_`, `isCalendarDate_`, `requireEditableJam_` and its 9 callers), `test/setlist.test.js` (fake has `insertSheet`,
`deleteSheet`, `deleteRow`, **no `setName`**: add it and log it in `WRITE_OPS`; setJamStatus tests 1810-1898),
`test/post.test.js` (action list lines 193, 493, 598, 649); `core/data` `jams/{JamCreationPlan,JamFields}.kt`,
`setlist/{SetlistJamStatuses,SetlistJamStatusRepository,SetlistMutationQueue,SetlistRepository,DefaultSetlistRepository}.kt`,
`cache/{SetlistJamDao,SetlistDao,JamEntities}.kt` (PKs carry `jam_date`, FKs cascade on delete only);
`feature/past-jams` `JamsAdmin{State,UiModel,Mapping,Views}.kt`, `NewJam{Form,Controls,Mapping,UiModel,Views}.kt`,
`JamStatus{Mapping,Controls,UiModel}.kt`, `PublishMapping.kt`, `JamsFailureMessages.kt`, `PastJamsPresenter.kt`,
`PastJamsCopy.kt`, `di/`, test `AdminFakes.kt`; `app` `di/AppModule.kt`, `actions/SetlistActionRegistry.kt`; the
classes implementing `SetlistRepository` in tests (registry test, 7 `core/data` tests, `feature/next-jam` `AdminFakes.kt`).
Not inspected: the real Sheet (PROGRESS: 2026-10-31 BORRADOR upcoming with 12 songs, 2026-11-13 BORRADOR held back);
`NewJamViews.kt` beyond the first 80 lines and its function list. Assumptions: `Sheet.setName` and `Sheet.deleteRow` behave
as documented (the check proves both after the redeploy); detekt defaults apply to `LongParameterList` (no override in
`config/detekt/detekt.yml`), which is why C1 groups the contracts.

## Expected File Changes

- **Part A.** `backend/apps-script/src/Post.js`; `test/setlist.test.js` (fake `setName`, new tests, the U1 amendment);
  `test/post.test.js` (action list and `Known:` messages); `backend/apps-script/README.md` if it lists actions;
  `docs/apps-script-api.md`, `docs/sheet-schema.md`; new `docs/migrations/admin-edit-delete-jam-script-update.md`.
- **Part B.** `core/data`: new `setlist/{SetlistJamEditRepository,UpdateJamOutcome,DeleteJamOutcome,JamEdit,SetlistJamEdits}.kt`,
  `cache/SetlistJamEditDao.kt`, `jams/JamEditPlan.kt`, tests (`SetlistJamEditsTest`, DAO test, `JamEditPlanTest`);
  modified `SetlistRepository.kt`, `DefaultSetlistRepository.kt`, `cache/SetlistDao.kt`; stubs in every test fake listed
  above. `app`: `SetlistActionRegistry.kt`, `di/AppModule.kt`, their tests.
- **Part C.** `feature/past-jams`: new `JamsAdminWrites.kt`, `JamEditForm.kt`, `JamEditControls.kt`, `JamEditMapping.kt`,
  `JamEditUiModel.kt`, `JamEditViews.kt`, `JamEditCopy.kt`; modified `PastJamsPresenter`, `di/PastJamsModule`,
  `JamsAdmin{State,UiModel,Mapping,Views}`, `NewJamForm` (`formatFormDate`), `NewJamViews` (`FormField` internal),
  `JamsFailureMessages`, `PastJamsCopy` (`HELD_BACK`), `JamsAdminDefaults` (dialog/destructive colours, no amber), previews;
  tests `JamEditFormTest`, `JamEditAdminTest`, `JamsAdminTest` (held-back actions, amended held-back line),
  `JamsAdminDefaultsTest`. Docs below.

## Visual Design Impact

DESIGN.md `### Jams tab`: replace the held-back sentence (status actions now on every future card; publish only on the
upcoming card; C3 lines). Add `### Editing and deleting a jam (admin-edit-delete-jam)`: the two neutral underlined 48dp
actions under the status block of each future card; the edit dialog (same as Nueva jam: `surfaceRaised`, labelled outlined
fields, `error` `caption` field errors, `textMuted` notes, `text` `Guardar` muted when disabled, `text` `Cancelar`); the
delete dialog (`textMuted` body, `error` confirm, `text` `Cancelar`); the polite sending line; the assertive failure card.
No amber; the allowlist is unchanged.

## Durable Documentation Impact

- `bb-blues-jam-bitacora.md`: **update** with D-28 (text above). `docs/domain-model.md`: **update** the authority row
  ("Future jam rows (D-25, D-28) | App | create, edit, delete via Apps Script") and the lifecycle. `docs/apps-script-api.md`:
  **update** (`updateJam`, `deleteJam`, `checkJamEdit`, refusal orders, answers, U1 rule, `Known:` list).
  `docs/sheet-schema.md`: **update** (app writes `fecha`/`hora`/`lugar`; tab renamed or deleted with its row).
  `docs/user-and-access-model.md`: **update** (admin edits/deletes future jams, held-back status). `docs/risks-and-open-
  questions.md`: **update** (E8 stale writes fail safely; the GET may see row and tab mid-rename; hand edits are
  last-writer-wins; close admin-jams-tab U1). `CONTEXT.md`: **update** only if the held-back term's definition changes.
  `DESIGN.md`: as above. Architecture skill: **update** ("As built by `admin-edit-delete-jam`": E3, E6, E7, E9, C1,
  registry 14). `AGENTS.md`: decision range `D-01 … D-26` → `… D-28` in its reminders. `ARCHITECTURE.md`/`CONSTRAINTS.md`:
  not present.

## Implementation Plan (three Parts, each ending with a green gate)

- **Part A — backend.** 1. Node tests for scenarios 1-5 first; save the failing run
  (`docs/evidence/admin-edit-delete-jam/node-failing-run.txt`). 2. `Post.js` (E1-E5, U1, header paragraph). 3. All node tests
  green (207 before). 4. API and sheet-schema docs, migration guide. 5. `CI=true ./init.sh` (Kotlin untouched, must stay green).
- **Part B — data and registry.** 6. `JamEditPlanTest`, `SetlistJamEditsTest` and DAO tests failing, then the code. 7. Registry
  test (14) failing and saved, then the registry and the `appModule` binding. 8. Fake stubs. 9. Gate.
- **Part C — UI and docs.** 10. Form and presenter tests failing, then C1-C7, copy, defaults, previews. 11. Docs, D-28,
  skill. 12. Gate. 13. Device check.

## Verification Plan

- `node --test backend/apps-script/test/*.test.js`: failing run saved, then all pass; report counts.
- `CI=true ./init.sh` after each Part: exit 0, konsist/detekt/ktlint `wired`, Konsist 19 rules, nothing loosened, no
  baseline; report JVM test counts (943 before).
- **Device (Pixel 5; never write to the real Sheet).** It holds a stored admin passphrase and real jams (2026-10-31 BORRADOR
  upcoming, 2026-11-13 BORRADOR held back). Record the md5 of `local.properties` before and after; no flag changes needed.
  **Never tap `Guardar`, `Borrar la jam` inside a dialog, `Pasar a creada`/`Pasar a borrador` (they write at once),
  `Publicar` or `Crear la jam`.** Steps:
  1. Jams tab: both cards show `Editar la jam` and `Borrar la jam`; the 13/11 card shows `Pasar a creada`, the new held-back
     line and no `Publicar lista`. Screenshot and `uiautomator dump`.
  2. 31/10 card → `Editar la jam`: seeded `31/10/2026`, real hora and lugar, `Guardar` disabled; type `14/11/2026` → LATER
     error; type `25:00` in hora → time error. Screenshot, dump. `Cancelar`; reopen and close with back.
  3. 13/11 card → `Editar la jam`: type `30/10/2026` → EARLIER error. Screenshot. `Cancelar`.
  4. 31/10 card → `Borrar la jam`: title, 12 songs line, next line `pasa a ser la del viernes 13 de noviembre`, `No se puede
     deshacer.` Screenshot, dump. `Cancelar`; reopen, back. 13/11 card → `Borrar la jam`: `La lista está vacía.` (or the
     cached count) and `sigue siendo la del sábado 31 de octubre`. `Cancelar`.
  5. Proof of no write (successful writes are not logged, so logcat alone proves nothing): the startup `jams refresh`/`jams
     cache` lines before any tap and a final dump show the same two jams, dates, times, venues and statuses; no
     `updateJam failed`/`deleteJam failed` line either.
- The deploy proofs are run by the orchestrator after the user redeploys (guide below), never by the implementer.

## Migration Guide Content (`docs/migrations/admin-edit-delete-jam-script-update.md`, written in Part A)

Only `Post.gs` changes; the Sheet needs no change. Until the redeploy: `Guardar` and `Borrar la jam` show the "todavía no
sabe editar ni borrar jams" card, and a status change on a held-back card fails with the "jam changed" card; nothing is
written. Steps as `assistant-create-jam-script-update.md`: replace the whole `Post.gs`; check the strings
`updateJam: { write: true, run: updateJam_ },`, `deleteJam: { write: true, run: deleteJam_ },`, `var JAM_EDIT_CHECK_TAB`
and `function requireFutureJam_`; Deploy → Manage deployments → Edit → New version (never New deployment). No-write
proofs (orchestrator, no passphrase): `POST {"action":"deployProbe"}` → `unknown_action` whose message ends with
`..., setJamStatus, updateJam, deleteJam, checkJamEdit`; read-only `GET ?resource=jams&jamStatuses=3` still shows the same
jams. Optional (passphrase, temporary tabs only): `checkJamEdit` → `{"ok":true}`. Never run `updateJam`/`deleteJam`/
`setJamStatus` by hand. The redeploy is safe any time after Part A is accepted: installed builds never call the new actions
and U1 only accepts more.

## Evidence To Capture

In `docs/evidence/admin-edit-delete-jam/`: node failing and final runs with the amended assertions listed, registry failing
output, gate output per Part with counts, device screenshots, dumps, startup log lines, md5 before/after.
`feature_list.json` `passing` with deviations; PROGRESS entry saying the `Post.gs` redeploy is owed.

## Validator Checklist

- [ ] D-28 recorded with the text above; D-25/D-04 amendments reflected in domain-model and API docs.
- [ ] E2/E4 refusal orders, zero writes on every refusal, read-back, idempotent update, tab renamed with the row, tab deleted
      before the row; past jams refused; `estado` never written by `updateJam`; `requireEditableJam_` unchanged.
- [ ] U1: held-back BORRADOR/CREADA accepted, PUBLICADA and `publishSetlist` still upcoming-only; only that assertion amended.
- [ ] Three actions last in `ACTIONS`, router-guarded; `checkJamEdit` self-cleaning on `_prueba_editar_jam*` only.
- [ ] Repository on the shared queue with the UNDISPATCHED pattern; Room only after a confirmed echo; a moved jam keeps
      songs, slots and extras; a deleted upcoming jam leaves the next held-back jam upcoming.
- [ ] `jamEditPlan` in `:core:data`; an edit never changes which jam is upcoming; the server stays generic.
- [ ] Registry 14, shown failing first; no assistant change beyond fakes. `:feature:past-jams` never references
      `SetlistRepository` or `AdminWriter`; `:feature:next-jam` unchanged except fakes.
- [ ] Delete always confirms with count, status line and next-jam line; double taps write once; sending hides that card's
      actions only.
- [ ] No new amber, 19 Konsist rules, `BluesJamTheme` tokens only, vos copy; gate green with three `wired` after each Part.
- [ ] Device check tapped no write (startup lines and final dump identical, md5 unchanged); migration guide present.

## Open Questions For The User

None blocking. Planner choices the orchestrator may overrule before implementation: E9 (an edit may not change which jam
is the upcoming one; the server accepts it, the Sheet remains the way to do it) and C3 (the extra line on a visible
held-back card behind a BORRADOR upcoming jam).
