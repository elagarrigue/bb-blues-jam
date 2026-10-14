# Feature Implementation Spec: The admin changes the jam status from the app (BORRADOR, CREADA, PUBLICADA)

## Source Feature

- `id`: admin-set-jam-status
- `area`: feature-next-jam (plus backend, `:core:data`, `:app` registry)
- `depends_on`: jam-status-created (accepted 14 October 2026)
- `status`: not_started at planning time
- `source`: `feature_list.json`; D-26 (amends D-04, D-25); user decisions of 14 October 2026

## Goal

On Próxima jam the admin sees the upcoming jam's status and can move it to any of `BORRADOR`, `CREADA`
or `PUBLICADA`, in any direction. Leaving `PUBLICADA` asks for confirmation, because musicians stop
seeing the list. Publishing keeps its rules and its flow (an empty or tab-less list cannot be published).
Every change is one server-validated mutation (passphrase guard, script lock, read-back), a repository
function and a registry entry (D-13). The badge changes only after the server confirms. Musicians see the
effect on their next refresh. This slice also finishes the one combined script redeploy guide for D-26.

## Non-Goals

- No status change for any jam except the admin's upcoming one: no past jam, no held-back later jam (D-04).
- No assistant tool or proposal that changes status. The agent stays read-only (D-24). The registry entry
  is the phase 2 surface.
- No change to `createJam` (it still writes `BORRADOR`), to the GET's visibility rules, or to `readJams`.
- No new module, no Konsist rule, no new amber role, no Room schema change.
- No writes to the real Sheet during verification. The redeploy is the user's step after acceptance.

## Job Story

When I created a jam by mistake as visible, or published a list too early, I want to move the jam back
to borrador or creada from the app, so I can fix it without opening the Sheet. Musicians stop seeing what I
withdrew the next time they refresh.

## Users And Permissions

- Admin (flag on): draws the controls. Apps Script authorizes every write (D-11). The flag only decides
  what is drawn.
- Musician: no control. Sees the new status on their next refresh (GET, D-26 rules unchanged).

## Decisions (planner; T1-T10)

- **T1 One new action `setJamStatus({date, status})`**: `status` is the Sheet text (`BORRADOR`, `CREADA`,
  `PUBLICADA`), exactly like `createJam`'s echo. Data-layer code is the only place `JamStatus` ↔ text is
  mapped. It is a `write: true` entry in `ACTIONS`, appended **last** (after `checkCreateJam`), so the router
  runs the guard and the lock. The action never calls the guard.
- **T2 Validation order, all before any write.** `invalid_date` (not a calendar `YYYY-MM-DD`), then
  `invalid_status` (missing, non-string, or not exactly one of the three; `borrador` and ` CREADA ` are
  refused, no trimming), then `requireEditableJam_` (`missing_tab`, `unknown_jam`, `duplicate_date`,
  `jam_not_editable`), then `findJamRow_`. If the cell already holds the target, the answer is
  `changed: false` with **no write** (idempotent, any status). For target `PUBLICADA` the publish rule
  follows: no tab or zero rows → `empty_setlist`. Then the write. Leaving `PUBLICADA` or moving between
  `BORRADOR` and `CREADA` needs no setlist.
- **T3 "Upcoming" for the admin** is `requireEditableJam_` as it stands: the earliest jam dated today or
  later (spreadsheet zone) whose `estado` is one of the three known values, **`BORRADOR` included**. It is
  the same jam as the admin snapshot's `upcoming` (the admin read keeps drafts). A later jam held back
  behind it (the one a musician may see, D-26 S1) is `jam_not_editable`. A jam with a typo `estado` is never
  the upcoming one and cannot be fixed from the app. That stays a Sheet edit.
- **T4 Write and read-back.** Generalize `writeJamStatus_(sheet, row, columns, services, status)`: plain-text
  format, set, flush, read back. A mismatch throws `Error` (`internal_error`). Only the `estado` cell of that
  one row is touched. Answer: `{ok: true, date, status, previous, changed}`, where `previous` is the
  trimmed text that was in the cell.
- **T5 `publishSetlist` stays, as a thin server alias.** `publishSetlist_` becomes "validate date →
  `setJamStatusCore_(…, PUBLISHED_STATUS)` → `{ok: true, alreadyPublished: !changed}`". It keeps the same
  refusals and the same answer shape, and its existing node tests must pass **unchanged**: that is the proof
  of the alias. The client `SetlistRepository.publishSetlist`, the `PUBLISH_SETLIST` registry entry and the
  amber `Publicar lista` flow stay as they are. Justification:
  - Old installed admin builds call `publishSetlist`, and the new app keeps publishing against the old
    script until the redeploy.
  - D-13 only asks that every UI mutation be a repository function in the registry. Both are.
  - The registry completeness test (`inventory` = `SetlistMutation.entries`) gains one entry, `SET_JAM_STATUS`
    (11 → 12), and loses none.
  - `setJamStatus(date, PUBLISHED)` exists for completeness and the assistant. It has the same server rules,
    but the UI does not use it.
- **T6 Client mutation.** New public `SetlistJamStatusRepository` (part of `SetlistRepository`, like
  `SetlistPublishRepository`):
  - `suspend fun setJamStatus(jamDate: LocalDate, status: JamStatus): SetJamStatusOutcome`
  - `fun observeJamStatusChanges(): Flow<List<JamStatusChange>>`
  - `fun dismissJamStatusChange(id: Long)`

  `SetJamStatusOutcome` is `StatusSet(status, changed)`, `NotSet(reason: WriteOutcome)` or
  `ScriptOutdated`. `JamStatusChange(id, jamDate, target, state)` has the states `Sending`,
  `Failed(reason)` and `Outdated`. The internal `SetlistJamStatuses` (on `SetlistMutationQueue`, undispatched
  in `DataScope`, like `SetlistJamCreations`) handles the answers:
  - `unknown_action` (`AdminAnswer.Refused` with `DataFailure.Service(UNKNOWN_ACTION)`) → `ScriptOutdated`.
  - An `Ok` whose echoed `date`/`status` differ, or whose `changed` is not a boolean →
    `NotSet(Unavailable)`.
  - A failure logs one `WriteFailureLog` line (`setJamStatus failed: date=… target=… outcome=…`).

  `DefaultSetlistRepository.dismiss(id)` also dismisses status entries.
- **T7 Room mirror only after the server confirms.** On `StatusSet`, the mirror depends on the target:
  - Target `PUBLISHED`: the existing `markPublished`.
  - Otherwise: new `SetlistJamDao.updateJamStatus(date, status): Int` (`UPDATE jam SET status = :status WHERE
    date = :date`).

  This happens before the entry is removed. An `SQLException` is ignored (a refresh repairs it). There is no
  optimistic badge: `Sending` shows only a status line.
- **T8 UI: action buttons, not a selector.** These go in the existing admin status block, under the badge
  and note:
  - `Publicar lista` (amber, unchanged, inline confirmation) for BORRADOR/CREADA with ≥1 song.
  - Neutral, underlined `text` actions for every *other* non-published target: `Pasar a creada` and
    `Pasar a borrador`. BORRADOR shows `Pasar a creada`, CREADA shows `Pasar a borrador`, and PUBLICADA
    shows both.

  BORRADOR ↔ CREADA is applied **at once** (no dialog: it is reversible and shows no list). Leaving
  PUBLICADA opens a modal confirmation `AlertDialog` (pattern: `RemovalConfirmationDialog`). While a status
  change or a publication for the jam is `Sending`, the publish button and the status actions are replaced by
  one status line. **User confirmation wanted:** whether BORRADOR ↔ CREADA should also get a dialog (U1).
- **T9 Old script.** Against an undeployed script, `setJamStatus` answers `unknown_action`. The client
  shows `Outdated` as a failure card with the guide message, and nothing is written. Publishing keeps
  working (`publishSetlist` exists in the old script).
- **T10 Assistant copy.** `JamCopy.DRAFT_LINE` becomes `Se crea como borrador: los músicos no la ven hasta
  que la pases a creada o publiques la lista.`, now that the app can do it. The assistant's logic is
  unchanged:
  - `jamPlan`'s held-back rule and `Usar esta lista`'s wait-until-upcoming read the raw snapshot, which
    is unaffected.
  - `ApplyMapping`'s published warning reads the live status.

## Copy (Rioplatense, vos; all in `NextJamCopy`)

- Actions: `TO_CREATED = "Pasar a creada"` and `TO_DRAFT = "Pasar a borrador"`. Sending:
  `STATUS_SAVING = "Cambiando el estado…"`.
- Dialog title: `UNPUBLISH_PROMPT = "¿Dejar de publicar la lista?"`. Body by target:
  - CREADA: `"Los músicos van a dejar de ver la lista cuando actualicen la app. Van a seguir viendo la fecha."`
  - BORRADOR: `"Los músicos van a dejar de ver esta jam, con su fecha y su lista, cuando actualicen la app."`
- Dialog buttons: confirm `Pasar a creada`/`Pasar a borrador` (`error` colour, like the removal `OK`) and
  `CANCEL`. Back or a tap outside equals `Cancelar`.
- Failure card: title `STATUS_FAILED = "No se pudo cambiar el estado"`; message `failureMessage(reason)`;
  consequence `STATUS_CONSEQUENCE = "La jam sigue como estaba."`; `Reintentar` and `Cerrar`. `Outdated`
  message: `STATUS_SCRIPT_OUTDATED = "El script de la planilla todavía no sabe cambiar el estado. Actualizalo
  con la guía y probá de nuevo."` with `Cerrar` only (retrying is pointless).
- `PUBLISH_IRREVERSIBLE` (it is no longer true) becomes `"Después podés volver a cambiar el estado desde
  acá."`. Keep the constant name or rename it to `PUBLISH_REVERSIBLE` with the model field. The validator
  only checks that the old sentence is gone everywhere (code, DESIGN.md).

## Acceptance Scenarios

1. **Server transitions (node).** For each of the 6 ordered pairs among the three statuses, an upcoming jam
   (with ≥1 song when the target is PUBLICADA) gets only its `estado` cell written, in plain text and read
   back. The answer echoes `date` and `status` with `changed: true` and `previous`. No other cell or tab is
   written.
2. **Idempotent (node).** Same target as the current one, for each status: `changed: false`, zero writes,
   and the lock is still taken and released (router).
3. **Publish rules (node).** Target PUBLICADA with no tab or an empty tab → `empty_setlist`, no write. The
   existing `publishSetlist` tests pass unmodified. `publishSetlist` on a CREADA jam still answers
   `alreadyPublished:false`.
4. **Refusals before any write (node).** `invalid_date`; `invalid_status` (missing, `5`, `borrador`,
   `ARCHIVADA`, `' CREADA'`); past jam; a later jam behind an upcoming BORRADOR → `jam_not_editable`;
   `unknown_jam`; `duplicate_date`; target row with a typo `estado` → `jam_not_editable`. An upcoming
   **BORRADOR** jam is accepted.
5. **Read-back mismatch (node).** A cell that reads back different → `internal_error`, and the lock is
   released.
6. **Guard (node).** `setJamStatus` is in `ACTION_NAMES` last, `write: true`. The every-action guard tests
   cover it (wrong or missing passphrase → the action never runs).
7. **Repository (JVM).** `Ok` echo → `StatusSet`, the cache mirrored (DRAFT/CREATED via `updateJamStatus`,
   PUBLISHED via `markPublished`), the entry gone. Any refusal → `Failed`, Room unchanged.
   `unknown_action` → `ScriptOutdated` with an `Outdated` entry, Room unchanged. A mismatched echo →
   `NotSet(Unavailable)`. Writes stay in call order with the other mutations, and a cancelled caller does
   not cancel the write.
8. **Registry (JVM).** `inventory` has 12 entries and covers `SetlistMutation.entries`. `SetJamStatus`
   dispatches the exact date and status and wraps the outcome in `SetlistActionResult.JamStatusSet`.
   The test is updated and shown failing first.
9. **Presenter: actions per state (Molecule).** The admin upcoming jam per state:
   - BORRADOR: `Pasar a creada` (+ `Publicar lista` if ≥1 song).
   - CREADA: `Pasar a borrador` (+ publish).
   - PUBLICADA: both actions, no publish.

   Musicians get no admin model at all (unchanged).
10. **Presenter: direct and confirmed (Molecule).**
    - BORRADOR → `Pasar a creada` calls `setJamStatus(date, CREATED)` once, and a double tap still calls it
      once.
    - PUBLICADA → `Pasar a borrador` calls nothing. It shows the confirming model (prompt, body for
      BORRADOR, labels). `Cancel` clears it without a call. `Confirm` calls `setJamStatus(date, DRAFT)` once.
    - The confirming state survives recomposition (saveable `"date|STATUS"` key).
11. **Presenter: confirmed only (Molecule).** While `Sending`: the badge stays the old one, a status line,
    no actions, no publish button. After the fake cache emits the new status: the new badge and the
    actions. `Failed` → the card with retry/dismiss. `Outdated` → the guide message, dismiss only.
12. **Device (read-only).** See the Verification Plan.

## Repository Research

### Files Inspected

- `bb-blues-jam-bitacora.md` (D-13, D-25, D-26).
- `docs/specs/jam-status-created.md` (S1-S9, notes) and `docs/migrations/jam-status-script-update.md`.
- `docs/migrations/assistant-create-jam-script-update.md` and
  `docs/evidence/assistant-create-jam/deploy-probe.md`: the deployProbe pattern. The current deploy lists
  19 actions ending `createJam, checkCreateJam`.
- `docs/apps-script-api.md` (POST, publish, createJam).
- `DESIGN.md` (Status badge, Admin controls, Publishing, removal dialog).
- `backend/apps-script/src/Post.js`: the header, `ACTIONS`, `publishSetlist_`, `checkPublish_`,
  `writeJamStatus_`, `findJamRow_`, `requireEditableJam_`, `createJam_`.
- `src/Jams.js` (status constants).
- `test/post.test.js`: the action list, unknown_action messages, the README-strings test.
- `test/setlist.test.js` (publish tests).
- `core/data/.../setlist/{SetlistPublishes,PublishOutcome,SetlistPublishRepository,SetlistJamCreations,
  CreateJamOutcome,SetlistRepository,DefaultSetlistRepository}.kt` and
  `cache/{SetlistDao,SetlistJamDao}.kt`.
- `app/.../actions/SetlistActionRegistry.kt` and its test.
- `feature/next-jam/.../{NextJamAdminMapping,NextJamAdminState,PublishUiModel,PublishControls,
  PublishStatusBlock,PublishDetails,FailureMessages,NextJamCopy,NextJamUiModel,AdminControls,AdminControlsDefaults}.kt`.
- `feature/assistant/.../{JamCopy,ApplyMapping,ApplyPlan}.kt`, `core/data/.../assistant/JamScheduleTool.kt`.

Not inspected: the real Sheet. From PROGRESS/the user: the 2026-10-31 jam is PUBLICADA. Assumption: the
redeploy changes nothing for it, because it is untouched by the code and remains served.

### Existing Patterns To Follow

- A write action is an `ACTIONS` entry. Refusals come before writes, and the answer is read back.
  The deploy proof extends an existing check, as `setKey` extended `checkSetlistWrite`.
- Mutation classes on `SetlistMutationQueue`, delegated into `DefaultSetlistRepository`.
- `ScriptOutdated` from `unknown_action`, as in `SetlistJamCreations`.
- A saveable confirming key plus a `consumed` guard (`PublishControls`, `removalKey`). A modal confirmation
  is `AlertDialog` with explicit token colours from `AdminControlsDefaults` (`removalDialog()`).
- Node tests written first and saved failing (`docs/evidence/<id>/node-failing-run.txt`).

## Technical Approach

**Server (`Post.js` only).**
- Add `setJamStatus_` and `setJamStatusCore_(spreadsheet, services, date, status)`, which returns
  `{previous, changed}` and does T2 from `requireEditableJam_` on.
- Add `JAM_STATUSES_WRITABLE_ = [DRAFT_STATUS, CREATED_STATUS, PUBLISHED_STATUS]`.
- Generalize `writeJamStatus_` with a `status` argument. Its callers are `publishSetlist_` and `checkPublish_`.
- `publishSetlist_` becomes the alias (T5).
- Extend `checkPublish_`. After the existing publish and read-back on its temp tab, it writes `CREADA` on
  the same row with `writeJamStatus_` and expects `[CREADA, BORRADOR]`. The temp tab is still deleted in
  `finally`. Its name and its passphrase need are unchanged.
- Add `setJamStatus: { write: true, run: setJamStatus_ },` last in `ACTIONS`.
- Header comment paragraph. `Code.js` and `Jams.js` are unchanged.

**`:core:data`.**
- `setlist/`: `SetlistJamStatusRepository`, `SetJamStatusOutcome`, `JamStatusChange` (public), and
  `SetlistJamStatuses` (internal). `SetlistRepository` extends the new interface. `DefaultSetlistRepository`
  delegates to it and dismisses through it.
- `cache/SetlistJamDao.updateJamStatus`.
- Status text mapping private to `SetlistJamStatuses`.

**`:app`.**
- `SetlistMutation.SET_JAM_STATUS` and `SetlistAction.SetJamStatus(jamDate, status)` (inventory sample:
  `SetJamStatus(DATE, JamStatus.CREATED)`).
- `SetlistActionResult.JamStatusSet(outcome)` and the dispatch arm.

**`:feature:next-jam`.**
- `AdminStatusUiModel` gains `statusChange: JamStatusUiModel?` (null for a non-upcoming jam, which cannot
  happen on Próxima jam, but keep it nullable).
- New `JamStatusUiModel`: `actions: List<StatusActionUiModel(label, target)>`, `confirming:
  ConfirmUiModel?(prompt, body, confirmLabel, cancelLabel)`, `sending: String?`, `failure:
  StatusFailureUiModel?` and one `EventHandler<Event>`, keyed by jam date. Events:
  - `Request(target)`
  - `Confirm`
  - `Cancel`
  - `Retry(id)`
  - `Dismiss(id)`
- New `JamStatusControls.kt` (`rememberJamStatusControls(setlist)`: saveable `"date|STATUS"` key, consumed
  guard, launches undispatched). Its decisions:
  - A `Request` from PUBLISHED sets the confirming key.
  - A `Request` otherwise calls `setJamStatus` at once.
- New `JamStatusMapping.kt` (pure `AdminState.jamStatusUiModel(jam)`). `AdminState` gains
  `statusChange: JamStatusState` (like `RemovalState`), and `rememberAdminState` collects
  `observeJamStatusChanges()`.
- `publishUiModel` returns null while a status change is `Sending`, and the status actions are hidden while
  a publish is `Sending`.
- New `JamStatusViews.kt` (`JamStatusActions`, `UnpublishDialog`) drawn by `AdminStatusBlock`. Colours
  come from the new `AdminControlsDefaults.statusAction()` (`text`) and `unpublishDialog()`
  (`surfaceRaised`, `text`, `textMuted`, `error`, `text`).

**`:feature:assistant`.** `JamCopy.DRAFT_LINE` (T10) and its test.

## Expected File Changes

- Modify `backend/apps-script/src/Post.js`, `test/post.test.js` (the action list, the two `Known:`
  messages, the README-strings test adds the `setJamStatus` line) and `test/setlist.test.js` (new
  `setJamStatus` tests; publish tests untouched). Changing a pre-existing assertion is allowed only for the
  action list and messages; list each one in the evidence.
- Create `core/data/.../setlist/{SetlistJamStatusRepository,SetJamStatusOutcome,JamStatusChange,SetlistJamStatuses}.kt`
  and `SetlistJamStatusesTest.kt`.
- Modify `SetlistRepository.kt`, `DefaultSetlistRepository.kt`, `cache/SetlistJamDao.kt` and the
  `SetlistDaoTest`.
- Every test fake implementing `SetlistRepository` gets the new stubs (narrow support):
  - `SetlistActionRegistryTest`
  - `DefaultSetlistRepositoryTest` (if it implements it)
  - the data tests listed by grep
  - `AdminFakes.kt`
- Modify `app/.../actions/SetlistActionRegistry.kt` and its test.
- Create `feature/next-jam/.../{JamStatusControls,JamStatusMapping,JamStatusViews}.kt`.
- Modify `NextJamUiModel.kt`, `NextJamAdminMapping.kt`, `NextJamAdminState.kt`, `PublishUiModel.kt`,
  `PublishStatusBlock.kt`, `AdminControlsDefaults.kt`, `NextJamCopy.kt` and the previews that build
  `AdminStatusUiModel`.
- Tests: `JamStatusAdminTest.kt` (scenarios 9-11) and `AdminControlsDefaultsTest` (no amber in the new
  styles).
- Modify `feature/assistant/.../JamCopy.kt` and its test.
- Modify `docs/migrations/jam-status-script-update.md` (the combined guide below) and
  `backend/apps-script/README.md` (the action list and count).

## Visual Design Impact

- DESIGN.md §Status badge becomes three values. §Publishing loses `Desde la app no se puede volver a
  borrador.` and gains a §Changing the status entry:
  - The neutral actions, 48dp, `Role.Button`, underlined `text`, **no amber**.
  - Direct BORRADOR↔CREADA.
  - The unpublish dialog (`surfaceRaised`, `text` title, `textMuted` body, `error` confirm, `text`
    `Cancelar`).
  - `Cambiando el estado…` as a polite live region.
  - The failure card the same as publish's.
- The amber allowlist for `feature/next-jam` is unchanged. Amber stays only on `Publicar lista` and the
  PUBLICADA badge.

## Durable Documentation Impact

- `.claude/skills/architecture/SKILL.md`: **update**, with an "As built by `admin-set-jam-status`" paragraph
  (T1, T5-T8, registry 12 mutations).
- `docs/apps-script-api.md`: **update**:
  - a `setJamStatus` section (request, refusal order, answer, idempotence);
  - the alias note under `publishSetlist`;
  - `checkPublish`'s extra step;
  - both `Known:` lists;
  - the client write path: `ScriptOutdated`.
- `docs/domain-model.md`: **update**. The lifecycle goes in any direction for the upcoming jam, and the
  authority row "Jam status" lists the app mutation `setJamStatus`.
- `docs/sheet-schema.md`: **update**. The `estado` is written by `setJamStatus` and `publishSetlist` (the
  upcoming row only).
- `docs/user-and-access-model.md`: **update**. The admin changes the status; the server authorizes it.
- `CONTEXT.md`: **update** Status (reversible from the app).
- `DESIGN.md`: as above.
- `docs/risks-and-open-questions.md`: **update** with U1 if it is unanswered, and the redeploy-order note.
- `AGENTS.md`: not needed. `ARCHITECTURE.md`/`CONSTRAINTS.md`: not present (the skill plays that role).
- Bitácora: not needed (D-26 already says it). Note T5 in the skill only.

## Implementation Plan

1. Write the node tests for scenarios 1-6 and the `checkPublish` extra step, run them failing, and save
   `docs/evidence/admin-set-jam-status/node-failing-run.txt`. Implement `Post.js` and run green: every
   pre-existing publish test passes unmodified.
2. Write the `SetlistJamStatusesTest`/DAO tests failing, then implement `:core:data`.
3. Update the registry test (12, `SetJamStatus`), run it failing, save the output, then implement the
   registry.
4. Write the presenter tests (scenarios 9-11) failing, then the mapping, controls, views, copy and defaults.
5. Do the assistant copy (T10), then the docs and the combined migration guide.
6. Run `CI=true ./init.sh`, then the device check.

## Verification Plan

- `node --test backend/apps-script/test/*.test.js`: the failing run is saved, then all pass. Report the
  count (199 before).
- `CI=true ./init.sh` exits 0, with konsist/detekt/ktlint `wired`. Konsist stays at 19 rules, no rule is
  loosened, no baseline. Report the test count (892 before).
- **Device (Pixel 5, never write to the real Sheet).** Save `local.properties` md5 before and after.
  - **V1, real data.** The stored passphrase gives admin; the real 2026-10-31 jam is PUBLICADA.
    1. The block shows `PUBLICADA` plus `Pasar a creada` and `Pasar a borrador`.
    2. Tap `Pasar a borrador`. The dialog opens: take a screenshot and a `uiautomator dump`.
    3. Tap `Cancelar`. Logcat must show no `setJamStatus`.
    4. Repeat with back.
  - **V2/V3, demo jam.** Set `demoUpcomingJamLive=true` with the draft flag (V2) and then the created flag
    (V3). Take a screenshot of the block (`BORRADOR` + `Pasar a creada` + `Publicar lista`; `CREADA` +
    `Pasar a borrador` + publish). **Do not tap any status action or publish confirm**: those are direct
    writes. Tapping `Publicar lista` then `Cancelar` is allowed.
  - Restore `local.properties` byte for byte. The script is not redeployed during the slice, so a real tap
    would answer `Outdated` anyway, but no tap is made.
- The deploy proofs (in the guide) are run by the orchestrator **after** the user redeploys, not by the
  implementer.

## Evidence To Capture

- Node failing run and final runs, with the list of amended pre-existing node assertions.
- The registry test's failing output.
- Gate output with counts.
- Device screenshots and the dump (`docs/evidence/admin-set-jam-status/`) and the logcat excerpt.
- `local.properties` md5 before and after.
- `feature_list.json` set to `passing`, and a PROGRESS entry saying the **combined redeploy is now owed**
  (guide ready).

## Migration Guide Content (rewrite `docs/migrations/jam-status-script-update.md`)

- **Title**: "Script update: three jam states and changing the status (jam-status-created +
  admin-set-jam-status, D-26)". Remove "Do not do this yet". This is the one redeploy for both slices.
- **Before you deploy**: open `Jams` and check the `estado` of 2026-10-31 (expected `PUBLICADA`; nothing
  changes for it). Nobody types `CREADA` until step 5 has passed.
- **Files**: replace the whole content of `Code.gs`, `Jams.gs` and `Post.gs` (Catalog and Normalize are
  unchanged). Check strings (Ctrl+F):
  - `Jams.gs`: `var CREATED_STATUS`
  - `Code.gs`: `musicianJams_(`
  - `Post.gs`: `readAllJams_(` **and** `setJamStatus: { write: true, run: setJamStatus_ },`
- **Deploy**: Manage deployments → Edit → **New version** → Deploy, never New deployment.
- **No-write proofs (the orchestrator runs these; no passphrase)**:
  1. `GET <url>?resource=jams&jamStatuses=9` → `invalid_jam_statuses` (proves Code and Jams; jams in the
     answer = old deploy; `internal_error` = a missing file).
  2. `POST {"action":"deployProbe"}` → `unknown_action` whose message ends `..., publishSetlist,
     checkPublish, createJam, checkCreateJam, setJamStatus` (proves Post).
  3. Read-only: `GET ?resource=jams&jamStatuses=3` still returns 2026-10-31 as `PUBLICADA` with its songs.
- **Optional (passphrase, temp tab only)**: `checkPublish` → `{"ok":true}`.
- Keep the existing "Existing rows" and "Old app versions" sections. Add:
  - Until the redeploy, the new app's `Pasar a …` shows the "todavía no sabe cambiar el estado" message,
    and publishing works.
  - Old admin builds keep publishing via `publishSetlist`.

## Validator Checklist

- [ ] T1-T10 as stated; `setJamStatus` is last in `ACTIONS`, router-guarded, refusals before writes,
      read-back, idempotent without a write; an upcoming BORRADOR is writable and a held-back later jam is
      not.
- [ ] Pre-existing `publishSetlist` node tests pass unmodified; the node failing run is saved.
- [ ] Registry has 12 mutations; the completeness test was updated and shown failing first.
- [ ] Room changes only after a confirmed echo; no optimistic badge; `Outdated` handled with vos copy.
- [ ] Leaving PUBLICADA always passes through the dialog; Cancel, back and outside write nothing; a double
      confirm writes once.
- [ ] No new amber role; Konsist 19 rules; `Desde la app no se puede volver a borrador.` is gone from code
      and DESIGN.md.
- [ ] Combined guide lists the three files, four check strings and both no-write proofs ending `setJamStatus`.
- [ ] `CI=true ./init.sh` green with three `wired`; the device check made no write (logcat, md5).

## Open Questions For The User

- **U1**: BORRADOR ↔ CREADA without a dialog (planner default, T8). Say if you also want a confirmation
  there: going to CREADA shows the date to musicians.

## Implementation Notes

- **U1 answered (orchestrator decision, 14 October 2026):** BORRADOR <-> CREADA applies immediately without a dialog,
  as T8 says. Only leaving PUBLICADA confirms.
- **Deviation (detekt `TooManyFunctions`, 11 max):** the two new colour styles live in a new internal
  `JamStatusDefaults` (`action()`, `dialog()`) instead of `AdminControlsDefaults.statusAction()`/`unpublishDialog()`.
  Same roles (`text`; `surfaceRaised`/`text`/`textMuted`/`error`/`text`); `JamStatusDefaultsTest` checks them.
- **Amended pre-existing node assertion:** the `checkPublish` test asserted one `flush`; the extra CREADA step makes it two.
- The publish confirmation's `PUBLISH_IRREVERSIBLE` was renamed `PUBLISH_REVERSIBLE` (field `reversibleNote`).
