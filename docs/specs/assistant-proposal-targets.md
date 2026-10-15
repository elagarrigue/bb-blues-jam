# Feature Implementation Spec: Three targets for the assistant's song list

## Source Feature

- `id`: `assistant-proposal-targets`; area `assistant`; source `feature_list.json` (last entry).
- `depends_on`: `admin-edit-delete-jam` (`accepted`, commit 0ec60cf). Status at planning: `not_started`.
- Decisions respected: D-02, D-03, D-08 (admin sets the key), D-11, D-12, D-13, D-17, D-24 (agent proposes, admin
  applies), D-25/D-26 (every created jam is `BORRADOR`). **Amends** the admin-jams-tab / admin-edit-delete-jam
  decision U1 ("every setlist mutation stays upcoming-only"): user answer to Q1 (15 October 2026), `addSong` also
  accepts a held-back future jam; recorded as **D-29** (Part C docs task). One more script redeploy is accepted.
- Q2 answered: kept songs keep the admin's key (D-08). Q3 answered: order Agregar, Reemplazar, Crear.

## Goal

Under a song-list proposal in the Asistente chat the admin picks one of three targets:

1. **Agregar a la próxima jam**: today's `Usar esta lista` (append in order, skip songs already listed), renamed.
2. **Reemplazar la lista de la próxima jam**: after a destructive confirmation that names the songs and the
   musician assignments that are lost, the upcoming setlist ends equal to the proposal, in proposal order.
3. **Crear una jam nueva con esta lista**: a form (fecha, hora, lugar, seeded), then `createJam` as `BORRADOR`,
   then the songs are loaded into **the date the server echoed**, also when the new jam is held back behind the
   upcoming one (D-29).

Each path writes only after a confirm tap, only through narrow `:core:data` contracts bound in `:app` to the
`SetlistActionRegistry`, reports progress per song/step, and never reports success when anything failed.

## Non-Goals

- Replace and Agregar need **no server change** (R1); they keep targeting the upcoming jam only. The only server
  change is Part C's (S1-S3): `addSong`'s guard plus one no-write-to-real-data deploy check.
- No other setlist mutation on held-back jams: `removeSong`, `moveSong`, `setKey`, `setSlotCount`, `assignSlot`,
  `clearSlot`, the extras and `publishSetlist`/PUBLICADA stay upcoming-only (`requireEditableJam_`). No UI to edit a
  held-back setlist (the Jams tab card already shows its song count).
- No key changes: added songs carry the proposal's catalog key (as today); kept songs keep the admin's key (D-08, Q2).
- No change to the agent package `core.data.assistant` (D-24: no tool, prompt or DTO change).
- No change to `:feature:next-jam` (its failure cards may show failed removes/moves the run caused: existing
  repository behaviour).
- No persistence of runs across process death; no deeplinks.

## Job Story

When the assistant proposes a setlist I like, I want to choose whether it starts a new jam, replaces the next
jam's list, or extends it, so I don't rebuild twelve songs by hand, and I know exactly what changed and what was lost.

## Decisions (planner)

- **R1 Replace is a client-side diff of existing actions, not a server action.** Plan from the latest snapshot:
  `toAdd` = proposal songs not listed (proposal order), `toRemove` = listed songs not in the proposal (list order),
  `kept` = the rest. Execution order **adds → removes → reorder** (`moveSong`), one call at a time.
  Justification: (a) kept songs keep their row, so their key, lineup counts, assignments and extras survive
  (remove-all-then-add would wipe every assignment, the whole point of the confirmation); (b) adding first means a cut
  run never leaves fewer songs than before: nothing is destroyed until every add was tried; (c) each call is already
  guarded, locked and read back server-side, every intermediate state is a valid setlist, and **Reintentar re-plans
  from the latest snapshot**, so the composition converges; (d) a server `replaceSetlist` would be atomic but costs a
  redeploy and a migration guide, and the non-atomic window (a few seconds per song, behind `Detener`) only matters
  if two admins edit at once, which the MVP does not have. The non-atomicity is recorded in risks.
- **R2 Reorder** is computed by the pure `reorderMoves(current, target)`: for `i` in `target.indices`, if
  `current[i] != target[i]`, emit `Move(target[i], toPosition = i + 1)` and apply it to the simulated list (remove,
  insert at `i`), the server's `moveSetlistRow_` semantics. At most `n - 1` moves. `current` is simulated by the
  runner from the plan's snapshot order plus confirmed adds (appended) minus confirmed removes; `target` = proposal
  order filtered to ids present in `current` (a failed add is simply absent).
- **R3 Final check.** A replace run that `Finished` is a success only if the latest snapshot's upcoming jam (same
  date) lists exactly `target` in order (Room mirrors each confirmed write before the outcome returns:
  `SetlistMoves.send`, `SetlistRemovals`, `SetlistSongAdds`). Otherwise the summary is the mismatch line (failure tone).
  Done in the pure mapping from `ApplyInputs.snapshot`; no extra read.
- **R4 Refusals.** Add: as today (`unknown_song`/`invalid_song`/`invalid_key` per song, `song_already_in_setlist` →
  Skipped/Kept, else abort). Remove: `song_not_in_setlist` counts as removed (Room already drops the row);
  `duplicate_song` is per-song failure; everything else aborts. Move: any non-`Moved` aborts the reorder phase
  (`unordered_setlist` has its own line). An abort in the add phase **skips removals** (they are `NotAttempted`).
- **R5 Replace blocks** (pure `replacePlan`): `Unknown` (no snapshot), `NO_UPCOMING`, `UNREADABLE` (setlist not
  `Available`), `REPEATED_IDS` (the cached list repeats a song id: removal by id would be refused), `ALREADY_EQUAL`
  (ids in the same order). An **empty** list is Ready with only adds.
- **R6 Lost assignments** = for each `toRemove` song, `lineup.slots.count { it.isFilled } + extraParticipants.size`.
- **C1 Crear is always offered** (user answer to Q1, D-29), subject only to `jamCreationPlan`: `PAST`, `EXISTS`
  (upcoming, held back or past) and `EARLIER` (before the upcoming jam, the orchestrator amendment of
  assistant-create-jam) are form errors; `Ready(None)` makes it the upcoming jam; `Ready(NewWaits)` creates it held
  back, and the form shows the held-back note. In both Ready cases the list is loaded into the new jam.
- **C2 Create-then-load** is one run: `JamCreator.createJam(values)`; on `Created(date, …)` the adds go to that echoed
  date, all proposal songs, in order; on `NotCreated`/`ScriptOutdated`/cancel nothing else is attempted. A held-back
  target refused with `jam_not_editable` means the deployed script predates S1: the run aborts with the
  outdated-script line (`La jam se creó, pero el script de la planilla todavía no sabe cargar listas en una jam en
  espera. Actualizalo con la guía y reintentá.`); the jam stays (no rollback, no delete).
- **C6 Retry of a create run.** After a create run that created the jam but did not load everything, the Crear button
  is gone (the jam exists, `EXISTS`) and the turn shows `Reintentar los que faltan` **for that run's jam date**
  (`ApplyRun.jamDate`, upcoming or held back), planned with the same pure `applyPlan` logic generalized to a target
  date: `applyPlanFor(songs, snapshot, date)` looks the jam up in `upcoming` then `heldBack`. Agregar and Reemplazar
  themselves keep targeting the upcoming jam only.
- **S1 Server: `addSong_` calls `requireFutureJam_` instead of `requireEditableJam_`.** It is the only mutation the
  create-then-load path needs (a new jam has no rows to remove, move, re-key or assign). Every other setlist mutation,
  `publishSetlist` and PUBLICADA in `setJamStatus` keep `requireEditableJam_`: they are driven from Próxima jam,
  which only shows the upcoming jam, and publishing a held-back jam would make musicians see a jam that is not the
  next one (D-26). `requireFutureJam_` still refuses past jams, unknown `estado` and unknown/duplicate dates, so the
  change only accepts more; `jam_not_editable` for `addSong` now means "not a future jam".
- **S2 Deploy detectability.** S1 adds no action name, so `deployProbe` could not tell the versions apart. Add the
  self-cleaning check action `checkFutureSetlistWrite` (`write: true`, last in `ACTIONS`): on a temporary Jams-shaped
  tab `_prueba_lista_futura` with two BORRADOR rows dated 2099-01-01 and 2099-01-02 it proves the 2099-01-02 row
  passes the future guard and fails the upcoming guard, appends a marker row with `appendSetlistRow_` to a temporary
  setlist tab, reads it back with `buildSetlist`, and deletes both tabs in `finally`. To run against a temporary tab
  the two guards' decisions are extracted into pure functions over built jams (`futureJamIn_(jams, date, today)`,
  `upcomingJamIn_(jams, date, today)`); `requireFutureJam_`/`requireEditableJam_` keep their signatures, refusal
  codes and order and call them. Never opens the real `Jams` tab or a real jam tab.
- **S3 Kotlin needs no data-layer change for held-back targets (verified by reading, to be proven by tests).**
  `SetlistRepository.addSong(jamDate, …)`/the registry already take any date; `SetlistSongAdds` mirrors into Room by
  date "only while the cached setlist is `AVAILABLE`"; a held-back BORRADOR/CREADA jam's setlist is read by `readJams`
  (future BORRADOR and CREADA, `readJamsAsAdmin_`) and a just-created jam is cached `AVAILABLE` and empty
  (`SetlistJamDao.insertCreatedJam`). The KDoc of `SetlistRepository`/`SetlistSongAdder` that says "upcoming" is
  updated for `addSong`. A JVM test in `:core:data` proves an add to a held-back cached jam is mirrored into that jam
  and not the upcoming one. A held-back PUBLICADA jam (only typeable by hand) is out of scope.
- **C3 Seeds.** Fecha = the proposal's `jam.date` if present, else empty; Hora/Lugar = the proposal jam's, else
  `newJamSeed(snapshot)` (latest-dated jam, else 21:00 and empty).
- **C4 Shared form.** The Jams tab's pure form rules move to `:core:data` `jams/JamForm.kt` (public, unchanged
  logic) and its dialog to `:core:ui` `com.bbbjam.core.ui.jamform` (a component two features use, never copied).
- **C5 Proposal jam card.** For a proposal with songs **and** a jam the card's own `Crear la jam` button is never
  drawn (Part C; the Crear action, seeded from the card, replaces it); the card stays as information.
  `jamListGate` (list waits for the proposed jam) is removed: the admin now chooses the target explicitly and every
  dialog names the jam date. Jam-only proposals keep `assistant-create-jam` behaviour exactly.
- **U1 Placement and order.** Under the proposed songs, where `Usar esta lista` is: three full-width secondary buttons
  stacked `xs` apart, order **Agregar, Reemplazar, Crear** (least destructive first). One run at a time in the
  process (also exclusive with `JamCreationRunner`); while any runs, all buttons are disabled. After a **successful**
  run of a turn its actions disappear (summary stays); after any other end they return.
- **K1 Konsist rule 19 is widened, not added**: `ASSISTANT_FEATURE_FORBIDDEN` also bans every
  `core.data.setlist.Setlist…Repository` (regex `setlist\.(Setlist\w*Repository|\*)`), so the feature cannot bypass
  the registry through `SetlistMoveRepository` etc. Still 19 rules. Failing case shown first.

## Acceptance Scenarios

1. Given an upcoming jam with 12 songs and a 10-song proposal sharing 6, when the admin taps Reemplazar, then a dialog
   names the jam date, the 4 removed titles, the lost musician count, the 6 kept and the 4 added; Cancelar, back or
   outside tap writes nothing.
2. Given that dialog, when Reemplazar is confirmed, then 4 adds, then 4 removes, then the needed moves run in that
   order through the registry, and on finish with the snapshot equal to the proposal the summary is `Listo: …`.
3. Given an empty upcoming list, Reemplazar's dialog says the list is empty and the run only adds.
4. Given a list already equal to the proposal, Reemplazar is disabled with `La lista de la próxima jam ya es igual a esta.`
5. Given an add aborts (offline) during a replace, then no removal is attempted, they show `Sin intentar`, and the
   summary says nothing was removed; Reintentar re-plans from the latest snapshot.
6. Given a remove fails mid-replace, the summary counts added/removed, names the failure, never `Listo`.
7. Given a move is refused, the summary says the songs are loaded but not in the proposed order.
8. Given no upcoming jam, then Agregar and Reemplazar are not drawn, the note `No hay una próxima jam: podés crear una
   con esta lista.` is shown and Crear opens the form seeded per C3.
9. Given the form's Crear, when the server creates the jam, the songs are added to the echoed date in order; if the
   create fails nothing is added; if some adds fail the summary says the jam was created and how many loaded, and
   `Reintentar los que faltan` targets that jam (C6).
10. Given an upcoming jam on 31/10, Crear is offered; 28/11 shows the held-back note and, on submit, creates the jam
    held back and loads the list into 2026-11-28 (the upcoming list is untouched); 24/10 is the `EARLIER` error and
    13/11 the `EXISTS` error, submit disabled.
10b. Given the old script (no S1), the held-back load aborts with the outdated-script line and the jam stays created.
11. Agregar behaves exactly as `Usar esta lista` did (dialog, skipped duplicates, captions, retry label).
12. A musician never sees the tab (unchanged); the agent package gains no write reference (rule 18 unchanged).

## Repository Research (inspected)

- `feature/assistant/src/main/.../`: `ApplyPlan.kt`, `ProposalApplier.kt` (one run at a time, per-song states,
  `Aborted`/`Stopped`/`Interrupted`, finally-guard), `ApplyHandlers.kt` (Confirm is the only `apply` caller, re-plans),
  `ApplyMapping.kt` (`applicableTurnId`, `jamListGate`, summary copy), `ApplyUiModel.kt`, `ApplyViews.kt`,
  `ApplyDefaults.kt`, `ApplyCopy.kt`, `ApplyOutcomeCopy.kt`, `JamMapping.kt`, `JamHandlers.kt`,
  `JamCreationRunner.kt`, `JamCopy.kt`, `AssistantPresenter.kt`, `AssistantUiModel.kt`, `di/AssistantModule.kt`.
- `core/data/.../setlist/`: `SetlistSongAdder.kt`, `JamCreator.kt` (pattern: `fun interface`, no data binding),
  `RemoveSongOutcome.kt`, `MoveSongOutcome.kt`, `CreateJamOutcome.kt`, `SetlistMoves.kt` (mirror before return).
  `jams/JamCreationPlan.kt`, `jams/JamFields.kt`; `cache/SetlistJamDao.kt` (created jam cached `AVAILABLE`, empty).
- `app/.../actions/`: `RegistrySongAdder.kt`, `RegistryJamCreator.kt`, `SetlistActionRegistry.kt` (`RemoveSong`,
  `MoveSong`, results `SongRemoved`, `SongMoved`); `di/AppModule.kt`.
- `backend/apps-script/src/Post.js`: `ACTIONS`, `addSong_`, `removeSong_`, `moveSong_`/`moveSetlistRow_`,
  `requireEditableJam_`, `requireFutureJam_`, `readJamsAsAdmin_` (future BORRADOR/CREADA setlists), `checkPublish_`
  (temporary Jams-shaped tab pattern), file header; `docs/migrations/admin-edit-delete-jam-script-update.md`.
- `feature/past-jams/.../JamsAdminMapping.kt` (held-back cards show the song count).
- `feature/past-jams/.../NewJamForm.kt`, `NewJamMapping.kt`, `NewJamUiModel.kt`, `NewJamControls.kt`,
  `NewJamViews.kt` (`NewJamDialog`, `FormField`), `JamsAdminDefaults.form()`, `PastJamsCopy` form strings.
- `konsist-test/.../ModuleIsolationTest.kt` (`ASSISTANT_FEATURE_FORBIDDEN`), `DESIGN.md` Asistente sections.
- Not inspected: `AssistantTurn.kt` beyond the `JamCard`/`ApplyContent` call sites; existing test bodies.

## Technical Approach and Expected File Changes

### Part A: contracts, plans and runner (no UI)

- `:core:data` `setlist/SetlistSongRemover.kt` (`fun interface { suspend fun removeSong(jamDate, songId):
  RemoveSongOutcome }`) and `setlist/SetlistSongMover.kt` (`suspend fun moveSong(jamDate, songId, toPosition):
  MoveSongOutcome`), KDoc as `SetlistSongAdder`; no `dataModule` binding.
- `:app` `actions/RegistrySongRemover.kt`, `actions/RegistrySongMover.kt` (dispatch `RemoveSong`/`MoveSong`, unwrap
  `SongRemoved`/`SongMoved`, `error(...)` otherwise); `AppModule` binds `single<SetlistSongRemover>`,
  `single<SetlistSongMover>`. Registry unchanged (14 mutations). Tests beside `RegistrySongAdderTest` if it exists,
  else in `app/src/test/.../actions/`.
- `:feature:assistant`:
  - `ReplacePlan.kt`: `replacePlan(songs, snapshot): ReplacePlan` (`Unknown`, `Blocked(reason)` R5, `Ready(jamDate,
    status, toAdd, toRemove: List<JamSong>, kept, lostAssignments, existingCount, target: List<SongId>)`) and
    `reorderMoves(current, target): List<SongMove>`.
  - `ProposalApplier` (keep the class name and Koin `single`): constructor `(adder, remover, mover, creator, scope)`;
    `ApplyRun` gains `mode: ApplyMode { ADD, REPLACE, CREATE }`, `jamDate: LocalDate?` (null until created),
    `removals: List<SongRemoval>` (`Waiting`, `Removing`, `Removed`, `Failed(reason)`, `NotAttempted`),
    `order: OrderStep?` (`Waiting`, `Moving(done, total)`, `Ordered`, `Failed(reason)`, `NotAttempted`),
    `jam: JamStep?` (`Creating`, `Created(date)`, `Failed(JamFailure)`), `target: List<SongId>`;
    `SongApply.State.Kept` (replace). Entry points `apply(turnId, ApplyPlan.Ready)`, `replace(turnId,
    ReplacePlan.Ready)`, `create(turnId, date, startTime, venue, songs)` (plain values: the Part C form type does
    not exist yet); each returns false while any run runs.
    `isSuccess`: `Finished`, no failed song/removal, `order` null or `Ordered`, `jam` null or `Created`. `stop` stops
    after the call in flight; remaining steps `NotAttempted`. Finally-guard as today (in-flight step → failed as
    Offline/`JamFailure.Interrupted`, run `Interrupted`).
  - `di/AssistantModule.kt`: pass the new `get()`s.
- Konsist K1 in `ModuleIsolationTest.kt`.
- Tests: `ReplacePlanTest` (empty list, non-empty, all present, equal order, repeated ids, lost-assignment count,
  keys of kept songs untouched), `ReorderMovesTest` (identity, reverse, single move, with failed adds),
  `ProposalApplierTest` extended (call order adds→removes→moves with fakes recording calls; add abort skips removes;
  `song_not_in_setlist` counts as removed; remove abort; move abort; stop between phases; create failure adds nothing;
  create then adds to the **echoed** date when it differs from the requested one; partial adds after create;
  cancellation; one run at a time across modes).

Gate: `CI=true ./init.sh` exit 0, three tools `wired`.

### Part B: Agregar and Reemplazar in the chat

- `ApplyUiModel` becomes the targets block: `ApplyUiModel.Targets(actions: List<TargetActionUiModel>, notes:
  List<String>, confirmation: ConfirmationUiModel?)` plus `None`; `TargetActionUiModel(target: ApplyMode, label,
  enabled, events)` with `Event.Request`. `ConfirmationUiModel` gains `destructive: Boolean` (confirm drawn in
  `error`). The confirming state becomes one saveable `String?` key `"$turnId|$MODE"` (replaces the `Long?`).
- `ApplyMapping.kt`: per target, Agregar from `applyPlan` (ALL_PRESENT → disabled + existing note), Reemplazar from
  `replacePlan`, Crear hidden in Part B. `jamListGate` removed (C5). Summary per mode (copy below) including the R3
  mismatch. `ReplyUiModel.Proposal` gains `removals: List<ApplyStatusLineUiModel>` (title + caption, drawn under the
  summary) and `order: String?`.
- `ApplyHandlers`: Confirm re-plans from the latest snapshot for the confirmed mode and calls `apply`/`replace` only
  when Ready; same turn-id guards.
- `ApplyViews.kt`/`ApplyConfirmationDialog.kt`: three stacked buttons, notes, removal lines; `ApplyDefaults` gains
  `applyDialog(destructive)` (confirm `error` when destructive, else `text`) and removal-line colours. No amber.
- `JamMapping.kt`: **no change in Part B** (the card's `Crear la jam` stays until Part C's Crear replaces it, C5).
  Part B draws no Crear button and no Crear note; with no upcoming jam it keeps today's `BLOCKED_NO_UPCOMING` note
  (Part C replaces it with `No hay una próxima jam: podés crear una con esta lista.`).
- Tests: `ApplyPresenterTest`/`AssistantMappingTest`/`JamPresenterTest` updated (amended tests listed in
  `feature_list.json` evidence), new Molecule tests: dialog → confirm → replace calls in order → success line; cancel
  writes nothing; double Confirm replaces once; mismatch line when the fake snapshot differs.

Gate as Part A.

### Part C: held-back loads (server), shared jam form and Crear

Backend, written test-first (save the failing `node --test` run to
`docs/evidence/assistant-proposal-targets/node-failing-run.txt`):

- `backend/apps-script/src/Post.js`: S1 (`addSong_` → `requireFutureJam_`, KDoc and the file header's
  "every setlist mutation stays upcoming-only" line updated: only `addSong` accepts held-back jams), S2
  (`futureJamIn_`, `upcomingJamIn_`, `checkFutureSetlistWrite_`, `var FUTURE_SETLIST_CHECK_TAB =
  '_prueba_lista_futura'`, the `ACTIONS` entry last). No change to `Code.js`, `Jams.js`, `Catalog.js`,
  `Normalize.js`, the GET or `readJams`.
- `backend/apps-script/test/setlist.test.js` / `post.test.js` (whichever holds `addSong` and the guards): held-back
  BORRADOR and CREADA accepted (row appended to that jam's tab, tab created when missing, upcoming tab untouched);
  past jam, unknown `estado`, unknown and duplicate dates still refused before any write; `removeSong`, `moveSong`,
  `setKey`, `assignSlot`, `publishSetlist` still `jam_not_editable` on a held-back jam (regression guards);
  `checkFutureSetlistWrite` answers `{ok:true}`, leaves no tab behind (also when the read-back fails), never touches
  `Jams` or a real jam tab; router lists it as a guarded write. All existing node tests pass unmodified, except the
  ones asserting `addSong` refuses a held-back jam (list them as amended).
- `docs/migrations/assistant-proposal-targets-script-update.md`: modelled on the admin-edit-delete-jam guide. Only
  `Post` changes; paste-verification strings `checkFutureSetlistWrite: { write: true, run: checkFutureSetlistWrite_ },`,
  `var FUTURE_SETLIST_CHECK_TAB`, `function futureJamIn_`; **new version of the existing deployment, never a new
  deployment**; then the orchestrator's no-write check: `POST {"action":"deployProbe"}` → `unknown_action` whose
  message ends with `..., checkJamEdit, checkFutureSetlistWrite`, and a read-only `GET ?resource=jams&jamStatuses=3`
  showing the same jams. Optional passphrase check `checkFutureSetlistWrite` (temporary tabs only). Until the deploy,
  creating with a held-back date creates the jam and stops with the outdated-script line (C2); everything else works.
  Safe any time: the change only accepts more.

Kotlin:

- `:core:data`: KDoc of `SetlistRepository.addSong`/`SetlistSongAdder` (any future jam); a JVM test in the
  `DefaultSetlistRepository` suite: an add to a held-back cached jam (upcoming 31/10 and held-back 28/11 in Room)
  mirrors into 28/11 only (S3). No DAO or schema change.
- `:core:data` `jams/JamForm.kt`: move from `NewJamForm.kt`, made public with `JamForm` names: `JamFormInput`,
  `JamFormValues`, `JamDateProblem`, `JamFormCheck`, `JamFormSeed`, `jamFormSeed`, `parseFormDate`, `parseFormTime`,
  `formatFormDate`, `formatFormTime`, `checkJamForm`. Logic unchanged; `NewJamFormTest` moves to `core/data` tests.
- `:core:ui` `com.bbbjam.core.ui.jamform`: `JamFormUiModel(title, date, time, venue: JamFormFieldUiModel, notes,
  submitLabel, cancelLabel, canSubmit, events)` with `Event.Edit(field, text)`, `Submit`, `Cancel`;
  `JamFormFieldUiModel(label, value, hint, error)`; `enum JamFormField`; `JamFormDialog(model)` (from `NewJamDialog`
  + `FormField`); internal `JamFormDefaults` (from `JamsAdminDefaults.form()`, no amber, JVM test); public
  `JamFormText` (labels, hints, field errors, `earlierError(date)`; like `SongListText`).
- `:feature:past-jams`: `NewJamUiModel.form: JamFormUiModel?`, mapping uses `JamFormText`; `NewJamViews` draws
  `JamFormDialog`; delete moved code. Behaviour identical; tests change only names/imports.
- `:feature:assistant`: Crear target always drawn (C1), form state in the presenter (saveable open turn id, three
  texts, touched mask, as `NewJamControls`), seeded per C3; date errors from `checkJamForm` (`PAST`, `EXISTS`,
  `EARLIER`); a `NewWaits` date shows the note `Queda en espera detrás de la del {sábado 31 de octubre}; la lista se
  carga en esta jam nueva y la vas a ver en Próxima jam cuando pase esa.` and stays submittable. Submit closes the
  form and calls `applier.create` (re-checks with the latest snapshot first). No separate confirmation dialog: the
  form is the confirmation (it lists the draft note, the held-back note and the song count). C5 card rule. C6
  retry via `applyPlanFor(songs, snapshot, run.jamDate)`. C2's outdated-script classification in `ProposalApplier`
  (a create run's first add refused `jam_not_editable` on the jam it just created → `Aborted` with
  `ScriptOutdated`-style reason).
- Tests: form seeding (proposal jam vs latest jam vs default), Submit disabled on `PAST`/`EXISTS`/`EARLIER`, a
  held-back date submits and the fakes see `createJam` then adds to the echoed held-back date (never the upcoming),
  outdated-script abort keeps the created jam, C6 retry targets the run's date, card button absent on songs+jam turns.

Gate as Part A, then the device check.

## Copy (vos, D-12)

- Buttons: `Agregar a la próxima jam` (retry `Reintentar los que faltan`), `Reemplazar la lista de la próxima jam`
  (retry `Reintentar el reemplazo`), `Crear una jam nueva con esta lista`.
- Notes: no upcoming (Part C, once Crear exists) `No hay una próxima jam: podés crear una con esta lista.`; Replace
  blocks: `La lista de la próxima jam ya es igual a esta.`, `La lista de la próxima jam tiene temas repetidos;
  arreglala en la planilla para poder reemplazarla.`, unreadable reuses `BLOCKED_UNREADABLE`.
- Replace dialog: title `¿Reemplazar la lista?`; lines `La lista de la jam del {fecha} va a quedar igual a esta: N
  temas, en este orden.` / `Se quitan M temas: A, B, C.` / `Se pierden K músicos anotados en esos temas.` (K = 0:
  `Ninguno de esos temas tiene músicos anotados.`) / `J ya estaban: conservan su tonalidad y sus músicos.` /
  `Se agregan P con la tonalidad del catálogo.` / empty list `La lista está vacía: se cargan los N temas.` / published
  `La lista está publicada: los músicos ven cada cambio apenas se guarda.`; confirm `Reemplazar` (destructive only
  when M > 0), `Cancelar`.
- Replace summaries: running `Reemplazando la lista de la jam del {fecha}: paso X de Y.`; success `Listo: la lista
  de la jam del {fecha} quedó igual a la propuesta.`; cut in adds `Se cortó el reemplazo antes de quitar temas: se
  agregaron X de N y no se quitó ninguno. {motivo}`; cut later `Se cortó el reemplazo: se agregaron X y se quitaron
  Y de M. {motivo}`; order `Los temas quedaron cargados, pero no en el orden propuesto. {motivo}` (`unordered_setlist`:
  `La planilla tiene posiciones repetidas o vacías; ordená la lista a mano.`); stopped `Detuviste el reemplazo: se
  agregaron X y se quitaron Y.`; mismatch `La lista de la próxima jam no quedó igual a la propuesta. Revisala en
  Próxima jam.` Removal captions: `Quitando…`, `Quitado`, `No se quitó: {motivo}`, `Sin intentar`; kept song caption
  `Ya estaba; conserva sus músicos`.
- Crear form: title `Nueva jam con esta lista`, notes `Se crea como borrador y se cargan los N temas en este orden.`
  + `JamCopy.DRAFT_LINE`; submit `Crear y cargar N temas`. Summaries: `Creando la jam…`, `Cargando temas en la jam
  del {fecha}: X de N.`, `Listo: se creó la jam del {fecha} como borrador y se cargaron los N temas.`, `No se pudo
  crear la jam: {motivo} No se cargó ningún tema.`, `La jam del {fecha} se creó como borrador, pero se cargaron X de
  N temas. {F no se agregaron | motivo}`. Held back: success `Listo: se creó la jam del {fecha} como borrador, en
  espera detrás de la del {próxima}, y se cargaron los N temas.`; outdated script: the C2 line.

## Visual Design Impact

DESIGN.md "Asistente": replace "Cargar la propuesta" with "Usar la propuesta: tres destinos" (buttons as the existing
secondary style, `surfaceRaised`/`text`, disabled `surface`/`textMuted`, 48dp, `xs` apart; notes `body`
`textMuted`; Replace dialog confirm in `error` only when it removes; removal lines `caption`, failures `error`), and
amend "Crear la jam" for C5. Shared form: one section under Jams tab saying the dialog is `core.ui.jamform`.
Konsist amber allowlist unchanged.

## Durable Documentation Impact

- `DESIGN.md`: update (above). `.claude/skills/architecture/SKILL.md`: update ("As built by
  `assistant-proposal-targets`": the two contracts, `ProposalApplier` modes, `jamform` in `:core:ui`, `JamForm.kt`
  in `:core:data`, K1). `docs/phase-2-brief.md`: update the apply behaviour. `docs/risks-and-open-questions.md`:
  replace is non-atomic (R1); held-back loads are invisible on Próxima jam until that jam is next (only the Jams
  card's song count shows them). `docs/apps-script-api.md`: update (`addSong` guard row "a future jam, upcoming or
  held back", the `jam_not_editable` meaning for it, new `checkFutureSetlistWrite`, and the sentence "The setlist
  mutations keep `requireEditableJam_`" amended to "all but `addSong`"). `docs/domain-model.md`: update the authority
  note (the app owns the setlists of future jams it builds, still never past ones; D-04 intact). `docs/user-and-access-model.md`:
  update the held-back line. `docs/sheet-schema.md`: not needed (no column change). `AGENTS.md`, `ARCHITECTURE.md`,
  `CONSTRAINTS.md`: not needed.
- **Bitácora `bb-blues-jam-bitacora.md`: add D-29** (Spanish, after D-28): "La app carga listas en una jam en
  espera. Enmienda a U1 (admin-jams-tab, admin-edit-delete-jam)": only `addSong` accepts any future jam so the
  assistant can create a jam with its list even when another is next; every other setlist mutation and publishing stay
  upcoming-only; cost accepted: one more script redeploy (user, 15 October 2026). AGENTS.md's "D-01 … D-26" range
  is not touched by this slice.

## Verification Plan

- Each Part ends with `CI=true ./init.sh` exit 0 and `konsist`, `detekt`, `ktlint` printed `wired`.
- K1: add a temporary reference to `com.bbbjam.core.data.setlist.SetlistMoveRepository` in an assistant source,
  show `assistant-feature-applies-through-contract` failing, save to
  `docs/evidence/assistant-proposal-targets/konsist-failing-run.txt`, remove it.
- Tests written first for `replacePlan`, `reorderMoves` and the runner phase order; record the failing run.
- Part C: `node --test` failing first for S1/S2, then all pass (count recorded); the user redeploys per the guide;
  the orchestrator runs the passphrase-free `deployProbe` and the read-only GET and saves them to
  `docs/evidence/assistant-proposal-targets/deploy-probe.md`. Never run `addSong` or the check against real data by hand.
- **Device (Pixel, admin passphrase stored, real jams: 31/10 BORRADOR upcoming with 12 songs, 13/11 held back).
  Never write to the real Sheet.** Record md5 of `local.properties` before/after (unchanged). Steps: open Asistente,
  ask for a list; check the block shows Agregar, Reemplazar and Crear in that order; open Agregar's dialog, Cancelar;
  open Reemplazar's dialog, compare removed titles and the lost-musician count with Próxima jam, then Cancelar and
  also back; open Crear's form, check the seeds (latest jam's hora/lugar, or the proposal's), type 24/10/2026
  (`EARLIER` error), 13/11/2026 (`EXISTS` error), 28/11/2026 (held-back note, submit enabled), then **Cancelar
  without submitting**. Stop there. Save `uiautomator dump` of the block, both dialogs and the form in each state, the
  startup `jams cache` lines before and after (same jams, 12 songs on 31/10), and a logcat grep with no failure lines.
  Every run path is JVM-only (Molecule + fakes).

## Evidence To Capture

`docs/evidence/assistant-proposal-targets/`: failing-first test runs, Konsist failing run, the three gate outputs (or
their tails), device dumps and screenshots, md5 lines; amended existing tests listed in `feature_list.json`.

## Validator Checklist

- [ ] Parts A/B: no `Post.js` change. Part C: only `addSong_` changes guard; every other setlist mutation and
      PUBLICADA still `requireEditableJam_` (node regression tests); `checkFutureSetlistWrite` self-cleaning; guide,
      `deployProbe` evidence and D-29 present. No new registry entry; every write dispatches through the registry.
- [ ] `:feature:assistant` references no `Setlist…Repository`, `AdminWriter`, other feature or `:app`; rule widened
      and shown failing; `core.data.assistant` unchanged.
- [ ] Every write starts from a confirm/submit handler that re-plans from the latest snapshot; double tap writes once.
- [ ] Replace order adds → removes → moves; add abort skips removes; kept songs untouched (no setKey, no remove).
- [ ] Lost-assignment count = filled slots + extras of removed songs; dialog names removed titles.
- [ ] Create loads into the echoed date, held back included, never into the upcoming jam; create failure loads
      nothing; `EARLIER`/`EXISTS`/`PAST` block submit; old-script abort keeps the jam and says so.
- [ ] No summary reads `Listo` when any step failed, was not attempted, or the R3 check fails.
- [ ] Past-jams' `Nueva jam` behaves as before (tests renamed only); form dialog lives once in `:core:ui`.
- [ ] Tokens only, no new amber, vos copy; gate green after each Part; device check wrote nothing.

## Answered Questions

- **Q1** (15 October 2026): allow loading a list into a held-back future jam, one more redeploy accepted → C1, C2,
  C6, S1-S3, D-29. **Q2**: keep the admin's key on kept songs (D-08). **Q3**: order Agregar, Reemplazar, Crear.

## Changes Relayed To The Parts A/B Implementer (revision of 15 October 2026)

1. Part A: `ProposalApplier.create` takes plain `(turnId, date, startTime, venue, songs)`, not `JamFormValues`.
2. Part B: do **not** touch `JamMapping.kt` (the card's `Crear la jam` stays until Part C); no Crear note; the
   no-upcoming case keeps `BLOCKED_NO_UPCOMING`. The dropped "esperá a que pase…" note must not be added.
Nothing else in Parts A/B changes.
