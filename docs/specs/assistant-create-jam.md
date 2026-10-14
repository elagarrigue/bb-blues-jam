# Feature Implementation Spec: The assistant proposes creating the next jam

## Source Feature

- `id`: `assistant-create-jam`; area `assistant`; source `feature_list.json`.
- `depends_on`: `assistant-apply-proposal` (`accepted` 14 October 2026; its on-device apply run is an open,
  non-blocking gap). Status at planning: `not_started`.
- Decisions: D-25 (the app creates the `Jams` row, once, as `BORRADOR`; amends D-04), D-24 (the agent proposes, the
  admin applies), D-13 (every mutation is a repository function registered in `:app`), D-04, D-08, D-11, D-12, D-17,
  D-03. User decisions of 14 October 2026 (relayed by the orchestrator): the agent always asks for the date, proposes
  the `hora` and `lugar` of the last jam for the admin to accept or change; another future jam does not block creation,
  and the agent/UI must say which jam stays held back.

## Goal

In the Asistente chat the admin asks for a new jam. The agent asks for the date when it is missing, reads the last
jam's time and venue with a read-only tool, and answers with a jam proposal (fecha, hora, lugar) beside its usual
message/questions (and, optionally, a song list). The proposal is validated on the device before it is drawn. Under
it, **Crear la jam** opens a confirmation dialog; on confirm the row is appended to `Jams` as `BORRADOR` by a new
guarded Apps Script action `createJam`, reached only through the action registry. The new draft then shows in the
cache, so **Usar esta lista** can load a list into it.

## Non-Goals

- No editing or deleting a jam row from the app (D-25: created once; later edits in the Sheet). No publish change.
- `createJam` creates **no tab**; `addSong` still creates it with the first song.
- The agent never writes and never calls the creation path (D-24). No new Konsist rule (see Decision C6).
- No change to `:feature:next-jam`, Room schema version, `JamsMapper`, `Code.js`, `Jams.js`, `Catalog.js`, `Normalize.js`.
- No deeplink (D1 deferred). No persistence of chat or creation state across process death.

## Job Story

When the next jam date is set, I want to tell the assistant the day and have it fill in the usual time and venue,
so I can create the jam and load its list from the phone without opening the Sheet.

## Users And Permissions

- Admin: sees the jam card, the dialog and the result. The flag only draws; `createJam` is behind the router's
  passphrase guard and script lock like every write (D-11). No passphrase stored: `AdminWriter` sends nothing and
  answers `AccessRefused`.
- Musician: no tab. A created draft is withheld from musicians as every draft is (`setlistForMusicians()`).

## Decisions

**Server (Part A)**
- **A1 Action.** `ACTIONS.createJam = { write: true, run: createJam_ }` in `backend/apps-script/src/Post.js` (only file
  changed server-side). Request `{date, time, venue}`. Checks, all before any write, in order: `invalid_date`
  (`isCalendarDate_`), `past_date` (`date < todayIso_`, spreadsheet zone = Buenos Aires), `invalid_time`
  (`^([01]\d|2[0-3]):[0-5]\d$`, exactly `HH:MM`), `invalid_venue` (string; trimmed, inner whitespace collapsed; 1..80
  UTF-16 units; no control characters; must not start with `=`, `+`, `-`, `@`, as the `Otros` name rule), `missing_tab`
  (no `Jams`), `missing_header`/`duplicate_header` (`mapColumns(JAMS_TAB, JAMS_FIELDS, header)`), `jam_exists` (any
  `Jams` row whose fecha, normalized by `buildJams` as `requireEditableJam_` does, equals `date`, whatever its
  `estado`), `tab_exists` (a tab already named `date`: an orphan tab would silently become the draft's setlist; the
  admin cleans it in the Sheet). Another future jam is **not** a refusal (user decision).
- **A2 Write.** Shared helper `appendJamRow_(sheet, columns, jam, services)`: row = `getLastRow() + 1`; set `@`
  (plain text) on the four mapped cells first, then write `date`, `time`, the normalized venue and `BORRADOR`; other
  columns untouched; `services.flush()`; read the four cells back with `getDisplayValues`, a mismatch is an `Error`
  (`internal_error`). Answer `{ok:true, date, time, venue, status:'BORRADOR'}` (venue normalized).
- **A3 Deploy check.** `ACTIONS.checkCreateJam` (write), like `checkPublish`: temp tab `CREATE_JAM_CHECK_TAB =
  '_prueba_crear_jam'` (leftover deleted first) with the `Jams` header and one existing row, runs `appendJamRow_` for
  `2099-01-02`, reads it back with `buildJams`, deletes the tab in `finally`; any difference is `internal_error`.
  Never touches the real `Jams`.
- **A4 Deploy proof without the passphrase.** `unknown_action` is answered before the guard and lists every action,
  so after the user's redeploy the orchestrator POSTs `{"action":"deployProbe"}` (no passphrase, no write, no rate-limit
  count) and checks the message ends with `..., checkPublish, createJam, checkCreateJam`. The URL is read from
  `local.properties` and never printed. `checkCreateJam` is optional for the user (needs the passphrase).

**Data and contract (Part B)**
- **B1 Repository.** New public `SetlistJamCreationRepository { suspend fun createJam(date: LocalDate, startTime:
  LocalTime, venue: String): CreateJamOutcome }` in `:core:data` `com.bbbjam.core.data.setlist`, composed into
  `SetlistRepository` (as `SetlistPublishRepository` is), implemented by internal `SetlistJamCreations` built by
  `DefaultSetlistRepository` with the shared `queue.ids/order/writes` and `DataScope` (undispatched, so a create
  followed by an add is sent in call order). It uses `AdminWriter.send("createJam", {date, time, venue})`. Why
  `SetlistRepository`: the registry has one repository dependency and the mutation must queue with `addSong`.
  No `observe…` flow: no screen overlays a pending creation (the feature tracks its own run).
- **B2 Outcome.** `CreateJamOutcome`: `Created(date, startTime, venue)` (only when the answer echoes date, time and
  status `BORRADOR`; otherwise `NotCreated(Unavailable)`); `NotCreated(reason: WriteOutcome)`; `ScriptOutdated` when
  `AdminAnswer.Refused.failure == DataFailure.Service("unknown_action")` (old script). Failures log one safe line via
  `WriteFailureLog` with the date and the outcome name only, as `publishSetlist` does.
- **B3 Cache mirror.** On `Created` only, in one transaction: insert the jam (`DRAFT`, setlist `AVAILABLE`, no
  problem, 0 dropped rows, `sheet_order` = max + 1, time `HH:MM`) with `IGNORE`, so a row a refresh already brought
  wins. A `SQLException` is ignored (the refresh repairs it). Put the DAO function in `SetlistDao` or, if detekt's
  `TooManyFunctions` objects, a new small DAO registered in `BluesJamDatabase` (no schema/version change). Matches
  the admin read, where a draft with `missing_tab` is `Available(emptyList())`.
- **B4 Registry.** `SetlistMutation.CREATE_JAM`, `SetlistAction.CreateJam(date, startTime, venue)`,
  `SetlistActionResult.JamCreated(outcome)`, inventory 11, dispatch to `repository.createJam`.
  `SetlistActionRegistryTest` updated (11 entries, exact inputs/outcomes; it fails first against the old registry).
- **B5 Narrow contract.** `fun interface JamCreator { suspend fun createJam(date, startTime, venue): CreateJamOutcome }`
  in `com.bbbjam.core.data.setlist`, no `dataModule` binding; `:app` `RegistryJamCreator(SetlistActionRegistry)`
  dispatches `CreateJam` (same shape as `RegistrySongAdder`), bound `single<JamCreator>` in `appModule`.

**Agent (Part C, `com.bbbjam.core.data.assistant`, still read-only)**
- **C1 Tool `jamSchedule`** (`JamScheduleTool(JamsRepository, JamCalendar)`, a `SimpleTool` with no arguments or one
  ignored field if Koog requires one): pure `render(snapshot, today)` lines: `today YYYY-MM-DD (Buenos Aires)`;
  `upcoming YYYY-MM-DD HH:MM | venue | BORRADOR|PUBLICADA` or `upcoming none`; `last YYYY-MM-DD HH:MM | venue` (the
  latest-dated jam known: the upcoming one, else `past.first()`) or `last none`. Registered beside the two tools.
  `DefaultSetlistAssistant` gains a `JamCalendar` parameter (`dataModule` updated).
- **C2 DTO.** `ProposalDto.jam: JamProposalDto? = null` (`date` YYYY-MM-DD, `time` HH:MM, `venue`), with
  `@LLMDescription`. Default null so an answer without the key still parses. `songs/message/questions` unchanged.
- **C3 Prompt rules** (added to `AssistantPrompt.SYSTEM`): only fill `jam` when the admin asks to create a new jam;
  call `jamSchedule` first; if the admin has not given an exact day, leave `jam` null and ask for it in `questions`
  (never choose a date yourself); default time and venue to `last` unless the admin said otherwise and say in
  `message` they can change them; never a date before `today`; never a date `jamSchedule` already lists; if
  `upcoming` exists, say in `message` which jam stays on hold; otherwise `jam` is null.
- **C4 Validation.** Pure `JamProposalValidator.validate(dto, today)` returns a public `ProposedJam(date: LocalDate,
  startTime: LocalTime, venue: String)` or a `JamRejection` (`INVALID_DATE`, `PAST_DATE`, `INVALID_TIME`,
  `INVALID_VENUE`), using the same rules as A1 (venue normalized). `AssistantReply.Proposal` gains
  `jam: ProposedJam? = null` and `jamRejection: JamRejection? = null`; `toDto` re-encodes a valid jam for the history.
  The admin's confirmation of the date is the dialog (C3 is best effort; the dialog shows the date in words).
- **C5 Catalog.** An empty catalog still fails the turn (`CatalogEmpty`) as today; accepted.
- **C6 Konsist.** No new rule: `JamCreator`, `ProposedJam`, `JamCalendar` and `JamsRepository` are not banned by
  `assistant-reads-only` (setlist/admin/remote/cache packages) or rule 19 (`SetlistRepository`, `AdminWriter`). The
  agent package must not reference `JamCreator` (it is in `core.data.setlist`, already banned there). Both rules stay green.

**Feature (Part D, `:feature:assistant`)**
- **D1 Which turn.** The jam card is drawn on the latest answered proposal with a valid `jam`, only while no turn is
  pending (`creatableTurnId`, like `applicableTurnId`). A jam-only proposal (no songs) is valid. Older turns show the
  jam as text without the button. A `jamRejection` shows a muted note instead (`La fecha propuesta no era válida;
  pedile otra.` and per-kind variants).
- **D2 Plan.** Pure `jamPlan(jam, snapshot, today)`: `Unknown` (no snapshot) | `Blocked(PAST)` | `Blocked(EXISTS)`
  (upcoming or a past jam on that date) | `Ready(heldBack)` with `heldBack` = `None` (no upcoming), `NewWaits(upcomingDate)`
  (upcoming earlier: the new jam is held back), or `ExistingWaits(upcomingDate, published)` (new date earlier: the new
  jam becomes the next one and the existing one is held back). Today from `JamCalendar.today()` read once per snapshot.
- **D3 Create first, then the list.** For a turn that carries a jam, `Usar esta lista` is enabled only when
  `snapshot.upcoming?.date == jam.date`; otherwise a blocked note: not created yet → `Primero creá la jam; después
  podés cargar la lista.`; created but held back → `La lista iría a la jam del {upcoming}, no a la nueva: esta queda en
  espera. Cargala cuando sea la próxima.` Turns without a jam keep today's rules. `BLOCKED_NO_UPCOMING` becomes `No hay
  una próxima jam. Pedime que la cree o creala en la planilla.`
- **D4 Runner.** `JamCreationRunner(creator: JamCreator, jams: JamsRepository, scope)` a Koin `single` (scope like
  `ProposalApplier`): `creations: StateFlow<Map<turnId, JamCreation>>` (`Creating`, `Created`, `Failed(reason)`);
  `create(turnId, jam)` returns false while any creation runs; after `Created` it calls `jams.refresh()` once, outcome
  ignored (the mirror already shows the draft). Cancellation of the scope marks `Failed(Interrupted)`, never success.
  "Crear la jam" is disabled while an apply run runs, and "Usar esta lista" while a creation runs.
- **D5 Handlers.** `JamHandlers` (keyed by turn id, as `ApplyHandlers`): Request opens the dialog (one saveable
  `Long?` in the presenter, separate from the apply dialog); Confirm acts only while that turn's dialog is open, closes
  it, re-plans from the latest snapshot and calls `runner.create` only for `Ready`. The only caller of `create`.
- **D6 Dialog** (`AlertDialog`, colours from `ApplyDefaults.applyDialog()` or a sibling `jamDialog()`, no amber, no
  `error`): title `¿Crear la jam?`; lines `Fecha: sábado 28 de noviembre de 2026`, `Hora: 21:00`, `Lugar: La Macanuda`,
  `Se crea como borrador: los músicos no la ven hasta que publiques la lista.`, and the held-back line: NewWaits →
  `La próxima jam sigue siendo la del {fecha}; esta queda en espera hasta que pase.`; ExistingWaits → `Esta pasa a ser
  la próxima jam y la del {fecha} queda en espera{published: ": los músicos dejan de verla hasta que pase esta"}.`;
  confirm `Crear la jam`, cancel `Cancelar`.
- **D7 Result copy** (one polite live region under the card): `Creando la jam…`; `Jam creada como borrador.`;
  failures: `jam_exists` → `Ya hay una jam con esa fecha en la planilla.`; `tab_exists` → `Ya hay una pestaña con esa
  fecha en la planilla; revisala antes de crear la jam.`; `past_date` → `Esa fecha ya pasó.`; `invalid_*` → `La
  planilla rechazó los datos de la jam.`; `ScriptOutdated` → `El script de la planilla todavía no sabe crear jams.
  Actualizalo con la guía y probá de nuevo.`; AccessRefused/Offline/Unavailable reuse the apply failure copy. A
  `Reintentar` (same dialog flow) after a failure.
- **D8 Card visuals.** Under the message (before the song list), a `surfaceRaised` card (`shapes.md`): heading
  `NUEVA JAM` (`caption`, `textMuted`, heading), date in words (`songTitle`, `text`), `21:00 · La Macanuda` (`body`,
  `text`), held-back note (`body`, `textMuted`), full-width secondary button `Crear la jam` (`surface` fill so it
  shows on the raised card, `text` content, `shapes.md`, 48dp; disabled `surface`/`textMuted`). **Not amber**
  (allowlist unchanged); colours in a `JamDefaults` object with a JVM test.

## Repository Research (inspected)

`AGENTS.md`, `PROGRESS.md`, `feature_list.json`, bitácora D-03/D-04/D-13/D-23/D-24/D-25, `docs/phase-2-brief.md`,
`docs/apps-script-api.md` (transport, envelope, router/guard, `checkWriteAccess`, `readJams`, `AdminWriter`,
`publishSetlist`/`checkPublish`), `docs/sheet-schema.md` (cells, `Jams`, mapper rules), `docs/domain-model.md`
(authority table already has D-25; mutation inventory still says ten), `DESIGN.md` Asistente and Cargar la propuesta,
architecture skill, `backend/apps-script/src/Post.js` (router, `requireEditableJam_`, `findJamRow_`,
`writeJamStatus_`, `checkPublish_`, `isCalendarDate_`, `todayIso_`, `createSetlistTab_`), `Jams.js` (`JAMS_FIELDS`),
`test/setlist.test.js` (fake `gridSheet`/`fakeSpreadsheet`, `call`, `assertError`, `writes`), `test/post.test.js`
(lines 193, 490, 595 pin the action list), `test/helpers/load.js`, `docs/migrations/lineup-grow-any-instrument-sheet-migration.md`
Part C, `backend/apps-script/README.md` paste checks, `:core:data` `AdminWriter`, `ServiceCodes`, `DataFailure`,
`SetlistRepository`, `SetlistPublishes`, `SetlistDao.markPublished`, `JamEntity`, `JamCacheMapping`, `di/dataModule`,
`assistant/*` (`DefaultSetlistAssistant`, `AssistantPrompt`, `ProposalDto`, `ProposalValidator`, `PastJamsTool`,
`AssistantReply`), `:app` `SetlistActionRegistry`, `RegistrySongAdder`, `AppModule`, `SetlistActionRegistryTest`,
`:feature:assistant` (`AssistantChat`, `AssistantPresenter`, `ApplyPlan`, `ApplyHandlers`, `ApplyMapping`,
`ApplyUiModel`, `ApplyCopy`, `ApplyConfirmationDialog`, `AssistantUiModel`, `di/AssistantModule`), Konsist rule 18/19
regexes, `docs/evidence/assistant-apply-proposal/device-check.md`.
Not inspected: `AssistantTurn.kt`/`AssistantScreen.kt` drawing internals, `SetlistMutationQueue`, `WriteFailureLog`.

## Expected File Changes

- `backend/apps-script/src/Post.js` (header comment, `ACTIONS`, `CREATE_JAM_CHECK_TAB`, `createJam_`,
  `checkCreateJam_`, `appendJamRow_`, venue normalizer, exports); `test/setlist.test.js` (new section) and
  `test/post.test.js` (action list ×3).
- `:core:data` `setlist/`: `SetlistJamCreationRepository.kt`, `CreateJamOutcome.kt`, `SetlistJamCreations.kt`,
  `JamCreator.kt`, `SetlistRepository.kt`, `DefaultSetlistRepository.kt`; `cache/` DAO function (± new DAO +
  `BluesJamDatabase`); `assistant/`: `JamScheduleTool.kt`, `JamProposalValidator.kt`, `ProposalDto.kt`,
  `AssistantReply.kt` (+`ProposedJam`, `JamRejection`), `ProposalValidator.kt`, `AssistantPrompt.kt`,
  `DefaultSetlistAssistant.kt`; `di/dataModule`; tests for each.
- `:app`: `SetlistActionRegistry.kt`, `RegistryJamCreator.kt`, `AppModule.kt`; tests `SetlistActionRegistryTest`,
  `RegistryJamCreatorTest`.
- `:feature:assistant`: `JamPlan.kt`, `JamCreationRunner.kt`, `JamHandlers.kt`, `JamCopy.kt`, `JamUiModel.kt`,
  `JamMapping.kt`, `JamViews.kt`/dialog, updates to `ApplyMapping`, `ApplyCopy`, `AssistantUiModel`
  (`ReplyUiModel.Proposal.jam: JamCardUiModel? = null`), `AssistantPresenter` (+`JamCreationRunner`, `JamCalendar`),
  `AssistantHandlers`, `AssistantTurn`, `di/AssistantModule`; tests.
- Docs: `docs/apps-script-api.md`, `docs/sheet-schema.md`, `docs/domain-model.md` (inventory 11, a "Creating the next
  jam" scenario), `DESIGN.md` (new "Crear la jam" subsection), architecture skill ("As built by assistant-create-jam"),
  `backend/apps-script/README.md` (paste check), new `docs/migrations/assistant-create-jam-script-update.md`,
  `docs/phase-2-brief.md` (slice 5), `PROGRESS.md`, `feature_list.json` evidence. Bitácora: D-25 already written; add
  nothing unless a decision above is overridden. `CONTEXT.md`: add "jam en espera" if not defined (check first).
- No `ARCHITECTURE.md`/`CONSTRAINTS.md` exist; `AGENTS.md` not needed.

## Implementation Plan (ordered)

1. Baseline `CI=true ./init.sh` and `node --test backend/apps-script/test/*.test.js`; record counts.
2. **Part A, tests first**: add the `createJam`/`checkCreateJam` tests and the action-list updates; run node tests and
   save the failing output (`docs/evidence/assistant-create-jam/node-failing-run.txt`). Then implement; green.
3. **Part B**: outcome, repository, mirror, `JamCreator`, registry + `RegistryJamCreator`; update
   `SetlistActionRegistryTest` first and capture its failure (`registry-failing-run.txt`).
4. **Part C**: tool, DTO, validator, prompt, reply fields, `dataModule`.
5. **Part D**: plan, runner, handlers, mapping, UI, copy, Koin.
6. Docs and migration guide (Part E). Final gate; device check; evidence; status `passing` only.

## Tests (JVM / Node)

- Node (`setlist.test.js`, through `handlePost` so guard and lock run): happy path appends exactly the four mapped
  cells at `getLastRow()+1` in the tab's own column order, plain-text format set before values, answer shape, lock
  taken once; a Jams tab with an extra column keeps it untouched; each refusal (`invalid_date` incl. `2026-02-30`,
  `past_date` incl. today allowed and yesterday refused across Buenos Aires midnight, `invalid_time` `9:00`/`24:00`,
  `invalid_venue` blank/81 chars/`=SUM(1)`/control char, `missing_tab`, `missing_header`, `jam_exists` for a
  `PUBLICADA`, a `BORRADOR` and a typed-date row, `tab_exists`) writes nothing (`writes(log)` empty); a later future
  jam and an earlier future jam both allow creation; read-back mismatch (`lostCells`) is `internal_error`;
  `checkCreateJam` cleans a leftover, never writes `Jams`, deletes its tab even on failure; both new actions need the
  passphrase; `unknown_action` message lists them.
- `SetlistJamCreationsTest` (fake transport/`AdminWriter` as existing setlist tests do, in-memory Room): request
  fields; `Created` mirrors a DRAFT/AVAILABLE empty jam visible in `observeJams()` as upcoming; an existing cached row
  wins; echo mismatch → `NotCreated(Unavailable)` and no mirror; `unknown_action` → `ScriptOutdated`;
  `invalid_passphrase` → `NotCreated(AccessRefused)`; Offline; a `Rejected("jam_exists")`; call order with `addSong`.
- `JamScheduleToolTest` (render with/without upcoming and past), `JamProposalValidatorTest` (each rejection, today
  valid, venue normalization), `DefaultSetlistAssistantTest` (fake executor: a reply with `jam` → `Proposal.jam`;
  without the key → null; invalid → `jamRejection`; history re-encodes the jam).
- `:app`: registry test (11), `RegistryJamCreatorTest`.
- Feature: `JamPlanTest` (all branches), `JamCreationRunnerTest` (single flight, refresh once after success, failure
  kinds, interruption), presenter tests (card only on latest valid jam turn, not while pending; Request → dialog lines
  incl. both held-back variants; Cancel → no call; double Confirm → one call; list action blocked until upcoming ==
  jam date, then enabled; held-back note), mapping/defaults tests (copy, no amber role).
- Konsist: unchanged rules green (no new rule, so no failing demo required).

## Verification Plan

- `CI=true ./init.sh` exit 0, konsist/detekt/ktlint all `wired`, test count up from baseline, 0 failures.
- `node --test backend/apps-script/test/*.test.js` all pass; failing run captured before the implementation.
- **Device** (debug build; `bluesjam.debugAdmin=true`; never a write to the real Sheet; at most **one** real chat request):
  1. Safety preconditions as in `docs/evidence/assistant-apply-proposal/device-check.md`: `adb shell run-as com.bbbjam
     ls files/datastore` (stored passphrase?) and the startup `jams cache:` line (`(demo)`?). The user's Pixel currently
     has a stored passphrase and the real published 2026-10-31 jam, so expect them to fail.
  2. One request, e.g. `Creá la jam del sábado 28 de noviembre.` Screenshot the reply: card with the date, hora and
     lugar of the 2026-10-31 jam, and the "queda en espera" note. If the agent asks questions instead, record it; no
     second request.
  3. Tap `Crear la jam`: screenshot + `uiautomator dump` of the dialog (both lines, labels, no amber). `Cancelar`;
     `adb logcat -s BluesJam` shows no write line.
  4. **Tap the confirm only if both preconditions hold** (no stored passphrase and the demo jam): expected `No se pudo
     crear: La frase de acceso…` and no request leaves the device. Otherwise stop at the dialog and say so.
  5. `local.properties` md5 unchanged; no key or URL in any evidence file.
- **User redeploy** (owed, not by the agent): `docs/migrations/assistant-create-jam-script-update.md` — paste only
  `Post.gs`; check string `createJam: { write: true, run: createJam_ },` and `var CREATE_JAM_CHECK_TAB`; Deploy →
  Manage deployments → Edit → New version (never New deployment). Until then the app shows the `ScriptOutdated` copy.
  After the user confirms, the orchestrator runs the A4 probe (no passphrase, no write) and records the result.

## Evidence To Capture

`docs/evidence/assistant-create-jam/`: `gate-baseline.txt`, `gate-final.txt`, `node-failing-run.txt`,
`node-final.txt`, `registry-failing-run.txt`, `01-jam-proposal.png`, `02-dialog.png`, `dump-dialog.xml`,
`03-after-cancel.png`, `device-check.md` (preconditions output, whether the confirm was tapped and why).

## Validator Checklist

- [ ] `createJam` is an `ACTIONS` entry with `write: true`; no guard call inside; every refusal before any write;
      appends only the four mapped cells as plain text; `BORRADOR`; creates no tab; node failing run recorded.
- [ ] `checkCreateJam` never touches `Jams` and deletes its tab in `finally`.
- [ ] `createJam` is a `SetlistRepository` function queued with the other writes; Room changes only on a confirmed echo.
- [ ] Registry has 11 mutations and the completeness test checks them; `JamCreator`'s only implementation is
      `RegistryJamCreator` (D-13); `:feature:assistant` references neither `SetlistRepository` nor `AdminWriter`.
- [ ] `core.data.assistant` writes nothing; the new tool reads `JamsRepository`/`JamCalendar` only (D-24).
- [ ] A jam proposal is validated before drawing; no write before the dialog's confirm; the dialog states date, time,
      venue, draft status and the held-back jam.
- [ ] `Usar esta lista` never targets a jam other than the proposed one on a jam turn.
- [ ] Old script → `ScriptOutdated` copy; vos copy; no amber beyond the allowlist; tokens only (D-12, D-17).
- [ ] Docs and migration guide updated; gate with three tools wired; device check obeyed the write guard.

## Assumptions And Open Points

- **Decision flagged for the orchestrator:** a new date *earlier* than the current upcoming jam is allowed (the user's
  rule "the earliest stays upcoming" applied literally) with an explicit dialog warning that the existing jam, maybe
  published, stops being shown to musicians. If the user prefers, block it client-side as `Blocked(EARLIER)`; the
  server stays permissive either way.
- Assumed: Koog's `JsonStructure.create<ProposalDto>()` accepts a nullable `jam` with a default and the Manual parser
  decodes answers without the key; if not, make `jam` required-nullable and say "null when not creating a jam" in the
  description. The device request is the live proof.
- Assumed: "last jam" = the latest-dated jam in the snapshot (upcoming if present, else the newest past jam). Held-back
  jams are not in `JamsSnapshot`, so a duplicate of one is caught only by the server (`jam_exists`).
- Assumed: venue limit of 80 characters and the formula-prefix rule are acceptable (planner choice, mirrors `Otros`).
- Size: four parts in one slice, at the upper bound; they form one flow and cannot ship separately with value.

## Implementation Notes (orchestrator amendment, 14 October 2026)

A proposed date **earlier** than the current upcoming jam is **blocked** in the app (`JamPlan.Blocked(EARLIER)`) with a
note that it would put the existing jam on hold and that it can be added in the Sheet if really intended; the agent
prompt also tells the model not to propose it. This replaces the "Decision flagged for the orchestrator" above: there
is no `ExistingWaits` variant and the dialog has only the `NewWaits` held-back line. Later dates stay allowed with the
"queda en espera" note. The server keeps accepting either (Apps Script stays generic).
