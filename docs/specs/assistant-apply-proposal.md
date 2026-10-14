# Feature Implementation Spec: Load the assistant's proposal into the upcoming setlist

## Source Feature

- `id`: `assistant-apply-proposal`; area: `assistant`; source `feature_list.json`.
- `depends_on`: `assistant-admin-tab` (`accepted` 14 October 2026). Status at planning: `not_started`.
- Decisions: D-08 (admin sets the key), D-13 (mutations are repository functions registered in `:app`),
  D-14 (catalog-only), D-03 (no feature imports another or `:app`), D-24 (the agent proposes, the admin applies),
  D-11 (Apps Script authorizes), D-12 (vos), D-17 (tokens, amber by role).

## Goal

Under the latest proposal in the Asistente chat the admin taps **Usar esta lista**, sees a confirmation
dialog naming the upcoming jam and how many songs will be added, confirms, and the songs are appended to
that jam's setlist one at a time, in proposal order, each with the catalog key the proposal already carries.
Every song shows its own progress and result; the run is never reported as a success when any song failed.
The write happens only on the admin's confirm tap and only through the action contract (D-13, D-24).

## Non-Goals

- No replace mode: existing songs are never removed or reordered (decision A2 below).
- No key choice in the dialog (D-08: the admin changes keys afterwards on the song detail).
- No publish, no lineup or musician edits, no new Apps Script action or contract change.
- The agent never calls the apply path; no tool, prompt or `core.data.assistant` change.
- No persistence of runs across process death; no deeplink scheme (D1 stays deferred).
- No change to `:feature:next-jam` (its pending rows and failure cards may show these adds; that is the
  repository's existing behavior, not new work).

## Job Story

When the assistant has proposed a setlist I like,
I want to load it into the next jam with one confirmed action,
so I do not have to add twelve songs by hand, and I know exactly which ones made it.

## Users And Permissions

- Admin (flag on): sees the action, the dialog and the results. The flag only decides drawing; every add is
  the existing guarded `addSong` (passphrase guard, lock, `jam_not_editable`, `song_already_in_setlist`).
- Musician: the tab does not exist (`assistant-admin-tab`); nothing changes.
- The agent: no access. The apply path lives in `:feature:assistant` and `:app`, never in
  `com.bbbjam.core.data.assistant`; the Konsist `assistant-reads-only` rule stays unchanged and green.

## Decisions

- **A1 Contract (D-13, D-03).** A narrow public `fun interface SetlistSongAdder { suspend fun addSong(jamDate:
  LocalDate, songId: SongId, key: Key): AddSongOutcome }` in `:core:data`, package `com.bbbjam.core.data.setlist`
  (beside the outcome it returns). `:app` binds it to a `RegistrySongAdder(SetlistActionRegistry)` that dispatches
  `SetlistAction.AddSong` and unwraps `SetlistActionResult.SongAdded`. Why not `SetlistRepository` directly, as
  next-jam does: the brief and D-13 say the assistant goes through the registry ("se registran esas funciones desde
  `:app`"), and least privilege — the assistant feature gets the one mutation it needs and cannot reach remove,
  publish or clear. Why not move the registry into `:core:data`: it is an accepted `:app` adapter; moving ten action
  types is churn with no gain. The interface is not bound in `dataModule`, so the only implementation is the
  registry-backed one. A new Konsist rule makes this checkable (A8).
- **A2 Append, never replace.** Songs go after the existing ones (`addSong` appends at `max(posicion)+1`).
  Replacing would be a batch of destructive removals the admin did not see; a non-empty list is normal (a manual
  song or two). The dialog says so when the list is not empty.
- **A3 Duplicates.** Proposal songs whose id is already in the upcoming setlist (admin view, `jam.setlist`) are
  skipped before the run, named in the dialog and shown as `Ya estaba en la lista`. A server
  `Rejected("song_already_in_setlist")` during the run (a race) is the same skipped state, not a failure. The
  proposal itself has no repeats (`ProposalValidator`).
- **A4 Target and blocking** (from `JamsRepository.observeJams()`, `snapshot.upcoming`, read with `jam.setlist`):
  | Upcoming state | Result |
  |---|---|
  | snapshot not read yet | no action drawn (no flash of a blocked note) |
  | `upcoming == null` | blocked: `No hay una próxima jam cargada. Creala en la planilla y volvé a intentar.` |
  | setlist `Withheld` (draft read without the admin read) or `Unavailable` | blocked: `No pude leer la lista de la próxima jam. Abrí Próxima jam para actualizarla y volvé.` |
  | every proposed song already in the list | blocked: `Todos estos temas ya están en la lista de la próxima jam.` |
  | `DRAFT`, `Available` | ready |
  | `PUBLISHED`, `Available` | ready, and the dialog adds `La lista ya está publicada: los músicos van a ver los temas apenas se agreguen.` |
  Published is allowed because a manual add on a published jam is allowed (the server accepts both); the assistant must
  not be weaker than the admin's hands (D-13), and the warning makes the consequence explicit.
- **A5 Failure handling, per song.** The run awaits each add before the next (order = proposal order).
  | Outcome | Song state | Run |
  |---|---|---|
  | `Added(position)` | `Agregado` | continue |
  | `Rejected("song_already_in_setlist")` | `Ya estaba en la lista` | continue |
  | `Rejected` `unknown_song`, `invalid_song`, `invalid_key` | `No se agregó: <message>` | continue |
  | `AccessRefused`, `Offline`, `Unavailable`, any other `Rejected` (`unknown_jam`, `jam_not_editable`, …) | `No se agregó: <message>` | **abort**: the rest become `Sin intentar` |
  Abort on systemic outcomes because every later add would fail the same way, and `Offline` may be a timeout after the
  server wrote: continuing could stack writes the admin cannot see. Unknown codes abort (fail safe).
- **A6 Stop.** While running, a `Detener` text action requests a stop; the song in flight finishes (an add started
  in `DataScope` is never cancelled by its caller) and its result is recorded; the rest become `Sin intentar`.
- **A7 Retry by re-applying.** When a run ends with any failure, stop or abort and its turn is still the latest
  proposal, the action returns as `Reintentar los que faltan`; it re-plans from the current snapshot, so songs
  already added are skipped as duplicates (A3), and opens the same dialog. A fully successful run shows no action.
- **A8 Konsist rule `assistant-feature-applies-through-contract`** (19th rule): in `:feature:assistant` main and
  test sources, no import or qualified reference of `com.bbbjam.core.data.setlist.SetlistRepository` or
  `com.bbbjam.core.data.admin.AdminWriter`. Shown failing first (temporary import in a test file), then restored.
- **A9 Which proposal.** The action is drawn only under the latest turn whose reply is a `Proposal` with at least
  one song, and only while no chat turn is pending. A run's state stays drawn under its own turn after newer turns.
  At most one run at a time in the process; the action is drawn disabled while another run is running.

## Acceptance Scenarios

1. **Happy path.** Given a draft upcoming jam `2026-10-31` with 2 songs and a 12-song proposal sharing 1 id with it,
   when the admin taps `Usar esta lista`, then a dialog shows `¿Cargar esta lista?` and
   `Se van a agregar 11 temas al final de la lista de la jam del sábado 31 de octubre, en este orden y con la tonalidad
   del catálogo. Después podés cambiar cualquier tonalidad.`, `La lista ya tiene 2 temas; los nuevos van después.`,
   `1 ya está en la lista y no se agrega: Crossroads.`, buttons `Agregar 11 temas` and `Cancelar`. Nothing is
   written yet. On `Agregar 11 temas`, `addSong` is called 11 times in proposal order with each proposal key; rows
   show `Agregando…` then `Agregado`; the summary ends `Listo: se agregaron 11 temas a la jam del sábado 31 de
   octubre. 1 ya estaba en la lista.`
2. **Cancel.** `Cancelar`, back or tapping outside closes the dialog; zero calls.
3. **Partial failure.** Song 3 returns `Rejected("unknown_song")`: it shows `No se agregó: Ese tema ya no está en el
   catálogo.`, the rest continue; the summary is `Se agregaron 10 de 11 temas. 1 no se agregó.` in `error`; never `Listo`.
4. **Abort.** Song 1 returns `AccessRefused`: song 1 shows the access message, songs 2–11 `Sin intentar`, summary
   `Se cortó la carga: se agregaron 0 de 11 temas.` plus the reason; exactly one call.
5. **Stop.** `Detener` during song 4: song 4's outcome is recorded, 5–11 `Sin intentar`, exactly 4 calls,
   summary `Detuviste la carga: se agregaron 4 de 11 temas.` (`textMuted`).
6. **Double tap.** Two confirm taps before recomposition start one run; a second `apply` while one runs returns false.
7. **Blocked states** of A4 draw the note and no button; published draws the extra dialog line.
8. **Survives leaving the tab.** Leaving and returning mid-run shows the same run (state lives in a Koin `single`).
9. **Musician / agent.** No flag, no tab; no code in `core.data.assistant` changes; the agent cannot trigger a write.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md`, `feature_list.json` (entry and verification), `docs/phase-2-brief.md`,
  `bb-blues-jam-bitacora.md` D-13, D-14, D-23, D-24, `.claude/skills/architecture/SKILL.md`, `DESIGN.md` "Asistente".
- `docs/specs/action-contract-registry.md`, `app/src/main/java/com/bbbjam/actions/SetlistActionRegistry.kt`,
  `app/src/main/java/com/bbbjam/di/AppModule.kt` (registry is a `single` there).
- `feature/assistant/**` (`AssistantChat` single with its own scope, `AssistantPresenter`, `assistantModel`,
  `AssistantUiModel`, `AssistantDefaults`, `di/AssistantModule.kt`, `AssistantModuleTest`), `feature/assistant/build.gradle.kts`.
- `core/data/.../assistant/AssistantReply.kt` (`ProposedSong` carries `songId`, catalog `title`, `artist`, `key`),
  `ProposalValidator.kt` (key = `Song.defaultKey`, non-null; repeats removed).
- `core/data/.../setlist/SetlistRepository.kt`, `AddSongOutcome.kt`, `SetlistSongAdds.kt` (DataScope, FIFO,
  `unknown_song` pre-check, Room from the answer), `admin/WriteOutcome.kt`, `admin/AdminWriter.kt` (no stored
  passphrase → `AccessRefused`, nothing sent).
- `docs/apps-script-api.md` `addSong` (checks 1–8: `jam_not_editable` allows BORRADOR and PUBLICADA upcoming,
  `song_already_in_setlist`), `core/model` `Setlist`, `Jam`, `JamStatus`; `jams/JamsSnapshot.kt`.
- `feature/next-jam/.../FailureMessages.kt`, `NextJamCopy.kt`, `JamDateText.kt`, `AdminControlsDefaults.removalDialog()`.
- `docs/specs/admin-publish-setlist.md` and `debug-admin-session` (device pattern: debug admin, no stored
  passphrase → `AccessRefused` with no request), `app/src/debug/java/com/bbbjam/debug/*`, `ModuleIsolationTest.kt`.

### Patterns To Follow

- Process-lived plain class as a Koin `single` with a module-supplied scope (`AssistantChat`), presenter `factory`.
- Removal dialog: `AlertDialog` with explicit token colours (`AdminControlsDefaults.removalDialog()`).
- Copy in `AssistantCopy`, colours in `AssistantDefaults`, everything checkable in the `UiModel`.
- `SpanishDateNames` for "sábado 31 de octubre" (lower-case day inside a sentence).
- `EventHandler` keys from stable ids; one state object, created once.

### Gaps

- No Compose UI test harness (T1): the dialog and live regions are checked on the device with `uiautomator dump`.
- The real success path (stored passphrase, real upcoming jam) is not run by agents.

## Technical Approach

1. **`:core:data`**: add `setlist/SetlistSongAdder.kt` (the `fun interface`, KDoc: bound in `:app` to the action
   registry; D-13). No implementation in `:core:data`, no `dataModule` binding.
2. **`:app`**: `actions/RegistrySongAdder.kt` implements it with `registry.dispatch(SetlistAction.AddSong(...))`, a
   `when` that returns `SongAdded.outcome` and `error(...)` for any other result (unreachable). `AppModule`:
   `single<SetlistSongAdder> { RegistrySongAdder(get()) }`.
3. **`:feature:assistant`, pure planning** (`ApplyPlan.kt`): `applyPlan(songs: List<ProposedSong>, snapshot:
   JamsSnapshot?): ApplyPlan`, with `ApplyPlan.Unknown` (snapshot null), `Blocked(reason)` (enum per A4) and
   `Ready(jamDate, status, toAdd: List<ProposedSong>, skipped: List<ProposedSong>, existingCount)`; duplicates by
   `SongId` against `(upcoming.setlist as Available).songs`.
4. **Coordinator** (`ProposalApplier.kt`, Koin `single`, constructor `(adder: SetlistSongAdder, scope: CoroutineScope)`,
   scope built in `assistantModule` like the chat's): `runs: StateFlow<Map<Long, ApplyRun>>` keyed by turn id;
   `apply(turnId, plan: ApplyPlan.Ready): Boolean` (false when any run is `Running`); `stop(turnId)`. `ApplyRun(jamDate,
   songs: List<SongApply>, state)` with `SongApply(song, state)`; song states `Waiting`, `Adding`, `Added(position)`,
   `Skipped` (plan or server duplicate), `Failed(reason: WriteOutcome)`, `NotAttempted`; run states `Running`,
   `Finished`, `Stopped`, `Aborted(reason)`, `Interrupted`. The loop is sequential, checks the stop flag between songs,
   classifies per A5, and on `CancellationException` marks the run `Interrupted` (remaining `NotAttempted`) and rethrows.
   Never `catch (Exception)`.
5. **Presenter**: `AssistantPresenter(chat, applier, jams: JamsRepository)`. Collects `jams.observeJams()`
   (`initial = null`) and `applier.runs`; holds `confirmingTurnId` in `rememberSaveable` (a `Long?`). Handlers (created
   once): `RequestApply(turnId)` sets it; `Cancel` clears it; `Confirm` acts only when it equals the turn id, clears it,
   recomputes the plan from the latest snapshot (read through `rememberUpdatedState`) and calls `apply` only for
   `Ready`; `Stop` calls `stop`.
6. **Mapping**: `assistantModel(...)` gains `runs`, `snapshot`, `confirmingTurnId`; `ReplyUiModel.Proposal` gains
   `apply: ApplyUiModel` (`None`, `Blocked(note)`, `Action(label, enabled, events)`, with `confirmation:
   ConfirmationUiModel?` (title, lines, confirmLabel, cancelLabel)) and each `ProposedSongUiModel` gains
   `applyStatus: String?` and `applyStatusKind` (`PROGRESS`, `DONE`, `SKIPPED`, `FAILED`, `NOT_ATTEMPTED`); the
   proposal also gets `applySummary: ApplySummaryUiModel?` (text, kind, `stopLabel`/events while running).
7. **Screen**: under the proposal, the action (secondary button), the blocked note, the summary (one polite live
   region) and `Detener`; per song the status caption under the reason; the dialog drawn beside the list when
   `confirmation != null`.
8. **Konsist**: add A8 in `ModuleIsolationTest`; demonstrate failure first.

## Expected File Changes

- `core/data/src/main/kotlin/com/bbbjam/core/data/setlist/SetlistSongAdder.kt` — create.
- `app/src/main/java/com/bbbjam/actions/RegistrySongAdder.kt` — create; `app/src/main/java/com/bbbjam/di/AppModule.kt` — modify.
- `app/src/test/java/com/bbbjam/actions/RegistrySongAdderTest.kt` — create; `app/src/test/java/com/bbbjam/ModuleWiringTest.kt` — resolve `SetlistSongAdder` as `RegistrySongAdder` (extend if it assembles modules).
- `feature/assistant/src/main/kotlin/com/bbbjam/feature/assistant/`: `ApplyPlan.kt`, `ProposalApplier.kt` (create);
  `AssistantPresenter.kt`, `AssistantHandlers.kt`, `AssistantMapping.kt` (split into `ApplyMapping.kt` if detekt asks),
  `AssistantUiModel.kt`, `AssistantScreen.kt`/`AssistantTurn.kt` (+ a new `ApplyConfirmationDialog.kt`),
  `AssistantCopy.kt`, `AssistantDefaults.kt`, `AssistantPreview.kt`, `di/AssistantModule.kt` (modify).
- `feature/assistant/src/test/...`: `ApplyPlanTest.kt`, `ProposalApplierTest.kt`, `FakeSetlistSongAdder.kt`,
  `FakeJamsRepository` (or reuse a fixture), updates to `AssistantPresenterTest`, `AssistantMappingTest`,
  `AssistantDefaultsTest`, `AssistantModuleTest`.
- `konsist-test/src/test/kotlin/com/bbbjam/konsist/ModuleIsolationTest.kt` — one rule.
- Docs: `DESIGN.md`, `.claude/skills/architecture/SKILL.md`, `feature_list.json`, `PROGRESS.md`, `docs/evidence/assistant-apply-proposal/`.

## Visual Design Impact

UI: yes; source `DESIGN.md` "Asistente". Update that section (drop "is a later slice"):
- `Usar esta lista` / `Reintentar los que faltan`: full-width secondary button under the questions, `surfaceRaised` fill,
  `text` content, `shapes.md`, 48dp; disabled `surface`/`textMuted`. **Not amber**: `Enviar` stays the only primary
  action, and the allowlist `feature/assistant = {key, primaryAction, onPrimaryAction}` does not change.
- Blocked note: `body`, `textMuted`. Summary: `body`; `text` for `Listo…` and while running, `error` for partial/abort,
  `textMuted` for stopped; one polite live region, static (no spinner). `Detener`: underlined 48dp text action in `text`.
- Per-song status caption (`caption`): `Agregando…`/`Agregado`/`Ya estaba en la lista` and `Sin intentar` in
  `textMuted`, `No se agregó: …` in `error`; part of the song's merged node.
- Dialog: Material 3 `AlertDialog`, `surfaceRaised` container, title `text`, lines `textMuted`, confirm and cancel in
  `text` (not `error`: it adds, it does not destroy; not amber). Colours in `AssistantDefaults.applyDialog()`.
- Copy (vos, singular/plural handled): failure messages mirror `NextJamCopy` wording (`ACCESS_REFUSED`, `OFFLINE`
  becomes `No pudimos confirmar si se agregó: no hay conexión o el servidor tardó.`, `UNAVAILABLE`, `NOT_IN_CATALOG`,
  `JAM_CHANGED`, `SHEET_REFUSED`), duplicated in `AssistantCopy` (no import from next-jam, D-03).

## Durable Documentation Impact

- `ARCHITECTURE.md`, `CONSTRAINTS.md`: not present in this repo; the architecture skill is the durable home.
- `AGENTS.md`: not needed (no workflow change).
- `.claude/skills/architecture/SKILL.md`: add an "As built by `assistant-apply-proposal`" paragraph (contract, coordinator,
  rule 19; update the rule count).
- `DESIGN.md`: as above. `docs/apps-script-api.md`, `docs/domain-model.md`: not needed (no contract or entity change).
- `docs/phase-2-brief.md`: not needed (behavior 6 is what is built).

## Implementation Tasks

- [ ] Baseline `CI=true ./init.sh`; record test count.
- [ ] `SetlistSongAdder`, `RegistrySongAdder`, `AppModule` binding, `RegistrySongAdderTest`, wiring test.
- [ ] `ApplyPlan` + `ApplyPlanTest`; `ProposalApplier` + `ProposalApplierTest` (fake adder with scripted outcomes and
  a gate to suspend a call).
- [ ] UiModel, copy, defaults, mapping, presenter, Koin module; Molecule tests.
- [ ] Screen, dialog, previews.
- [ ] Konsist rule, failing demonstration, restore.
- [ ] Docs, gate, device check, evidence.

## Verification Plan

JVM (all in `CI=true ./init.sh`; it must end with konsist, detekt, ktlint `wired`):
- `ApplyPlanTest`: null snapshot → Unknown; no upcoming, Withheld, Unavailable, all duplicates → each Blocked;
  draft/published Ready with `toAdd` in proposal order, `skipped`, `existingCount`, status.
- `ProposalApplierTest` (`runTest`, fake adder recording calls): ordering (calls equal proposal order minus skipped,
  keys equal the proposal keys); duplicates (skipped never sent; server `song_already_in_setlist` → Skipped, continue);
  partial failure (per-song `Rejected` continues, summary never Finished-success); abort on `AccessRefused`,
  `Offline`, `Unavailable`, `jam_not_editable`, unknown code (exactly one call, rest NotAttempted); stop mid-song
  (in-flight result kept, call count); second `apply` while running → false and no extra call; scope cancellation →
  `Interrupted`, not success.
- `AssistantPresenterTest` (Molecule + Turbine, fakes): action only on the latest non-empty proposal and not while a
  turn is pending; RequestApply → confirmation lines (date, counts, existing, skipped names, published line); Cancel →
  no call; Confirm twice in one frame → one run; progress transitions per song; summary kinds; `Reintentar los que
  faltan` after a failure and absent after full success; blocked notes.
- `AssistantMappingTest`/`AssistantDefaultsTest`: copy and colours (no amber role in the new styles).
- `RegistrySongAdderTest`: one `addSong` on the recording repository with unchanged arguments, outcome unchanged.
- Konsist: A8 failing run captured, then green; `assistant-reads-only` untouched and green.

Device (Pixel 5, debug build; flags `bluesjam.debugAdmin=true`, `bluesjam.demoUpcomingJam=true`,
`bluesjam.demoUpcomingJamDraft=true` in `local.properties`, restored afterwards; never TalkBack settings):
1. **Safety preconditions, checked before any confirm tap:** `adb shell run-as com.bbbjam ls files/datastore` shows no
   `admin_session.preferences_pb` (no stored passphrase, so `AdminWriter` sends nothing and returns `AccessRefused`),
   and the startup log line shows `upcoming <date> (demo) draft ... admin (debug)`. If either fails, **do not tap the
   final confirm** (do not log out or clear data to fix it); capture up to the dialog and report.
2. Asistente: send **one** real request (the brief's example); no second request, no retry.
3. Screenshot the proposal with `Usar esta lista`; tap it; screenshot the dialog (date of the demo jam, counts, the
   "ya tiene N temas" line, any skipped demo songs); `uiautomator dump`. Tap `Cancelar`; reopen.
4. **Tap `Agregar N temas` (the final confirm is tapped, under the preconditions above).** Expected: song 1
   `No se agregó: La frase de acceso…`, the rest `Sin intentar`, summary `Se cortó la carga: se agregaron 0 de N
   temas.`; no request leaves the device (no passphrase), and the demo date is not a Sheet jam anyway. Screenshot,
   `uiautomator dump`, `adb logcat -s BluesJam` excerpt. Próxima jam shows the repository's one failure card (expected).
5. `Reintentar los que faltan` is drawn; leave the tab and return: state kept. Do not tap it again.
6. `local.properties` md5 restored; no key in any evidence file.

## Evidence To Capture

Baseline and final gate output and test counts; Konsist failing run (`konsist-failing-run.txt`); screenshots
`01-proposal-action.png`, `02-dialog.png`, `03-aborted-run.png`, `04-after-return.png`; dumps; the precondition
commands' output; `device-check.md` stating that the final confirm was tapped with no stored passphrase.

## Validator Checklist

- [ ] `:feature:assistant` depends only on `:core:ui`, `:core:data`; never imports `:app`, `SetlistRepository` or `AdminWriter` (rule 19).
- [ ] The only `SetlistSongAdder` implementation is `RegistrySongAdder` over `SetlistActionRegistry` (D-13).
- [ ] No write before the confirm tap; `com.bbbjam.core.data.assistant` unchanged; agent cannot trigger it (D-24).
- [ ] Keys sent are the proposal's catalog keys, never altered or derived from an API (D-08).
- [ ] Per-song outcomes and summary follow A5; no success copy when any song failed, aborted or was stopped.
- [ ] Append only; duplicates skipped and reported; blocked states per A4 with vos copy.
- [ ] No amber outside `key` and `Enviar`; allowlist unchanged; tokens only (D-17).
- [ ] Gate ran with three tools wired; device check followed the safety preconditions; evidence recorded; status `passing` only.

## Assumptions

- Assumed: `ProposedSong.key` (catalog `defaultKey`) always satisfies the server's `^[A-G][#b]?m?$`; if not, the song
  fails as `invalid_key` and the run continues (A5).
- Assumed: the jams snapshot the tab reads is the same cache Próxima jam refreshes per login; this slice adds no refresh.
- Not inspected: `AssistantScreen.kt`/`AssistantTurn.kt` drawing details beyond DESIGN.md; the implementer follows them.
