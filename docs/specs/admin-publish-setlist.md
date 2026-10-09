# Feature Implementation Spec: Publish the setlist

## Source Feature

- `id`: admin-publish-setlist
- `area`: feature-next-jam
- `depends_on`: admin-add-song-to-setlist (`accepted`), unpublished-setlist-state (`accepted`)
- `status`: `not_started` at planning time (8 October 2026)
- `source`: `feature_list.json`. Notes: D-13, the highest-stakes mutation in the product; publish
  state changes only after explicit confirmation from the server. User decisions E1 and C1 are
  recorded below. U1 is resolved by `docs/domain-model.md` (no unpublish in the MVP). The user
  selected one shared deployment after app implementation completes for reorder, publish,
  adjust-lineup and assign.

## Readiness

Historical readiness (8 October 2026): implementation-ready. U1 is settled: no unpublish in the
MVP. E1 and C1 are resolved below. The user selected one shared deployment for the server halves
of reorder, publish, adjust-lineup and assign after app implementation was complete. That shared
deployment and the batch live checks completed on 9 October 2026 (see evidence below).
Deployment-deferral instructions in the implementation plan and checklist below describe the
pre-deployment workflow, not the current state.

Size: one feature in two local implementation parts (A: server and `:core:data`; B: UI), about one
session each.

## Goal

On Próxima jam, in admin mode, a draft jam with at least one song shows under its `BORRADOR` badge an
amber **Publicar lista** button. Tapping it shows an inline
confirmation; **Publicar** sends
`publishSetlist`. While sending, `Publicando…` replaces the controls and the badge stays `BORRADOR`.
**Only when Apps Script confirms** (it wrote `PUBLICADA` and read it back) does the cache flip the jam
to `PUBLISHED`, the badge become the amber `PUBLICADA` and the note `Los músicos ya ven esta lista.`
No optimistic state. On failure a persistent card **in the status block under the header** (not after
the rows) says the list was not published and why, with `Reintentar` and `Cerrar`, and the failure
is logged to logcat.

The mutation is `SetlistRepository.publishSetlist(jamDate)` in `:core:data` (D-13).

## Non-Goals

- **Unpublishing** (U1). `docs/domain-model.md` already says "no unpublish in the MVP".
- Creating, editing or deleting a `Jams` row or its `fecha`, `hora`, `lugar`. Publishing writes one
  `estado` cell, `BORRADOR` → `PUBLICADA`, never anything else.
- Publishing an empty or tab-less list, a past jam, or any jam but the upcoming one (E1: an empty
  draft cannot be published; the action is hidden and the server returns `empty_setlist`).
- Notifying musicians (out of MVP scope). The design export's "WhatsApp alerts" tip is not built.
- Faster musician refresh (already `live-refresh-during-jam`), offline queueing, deeplinks and the
  registry (`action-contract-registry`).

## Job Story

When the setlist for the next jam is ready, I want to release it from my phone and know for certain
it is live, so musicians never read a list I only think I published.

## Users And Permissions

- **Musician:** no code change. `setlistForMusicians()` withholds the draft (`En preparación`) until
  their next refresh reads `PUBLICADA` (open, pull, 30 min staleness, or the 30 s live window poll).
  Then they see the songs. There is no in-between state: the GET serves a setlist only for
  `PUBLICADA` (fail closed).
- **Admin** (`observeIsAdmin()` true): the button, confirmation, status and failure card. Apps Script
  authorizes the write (router guard); the flag never does.
- **Debug admin** (no passphrase stored): `publishSetlist` returns `AccessRefused` without a request.
  This is the device path.

## Acceptance Scenarios

1. **Publish succeeds.** The upcoming jam `2026-10-31` is `BORRADOR` with 3 songs. The admin taps
   `Publicar lista`, then `Publicar`. The server writes `PUBLICADA` to that one `Jams` row's `estado`
   cell, reads it back and answers `{ok:true, alreadyPublished:false}`. Nothing else in the
   spreadsheet changes (Node: the write log names one cell). Room's `jam.status` becomes `PUBLISHED`
   for that date only; the badge reads `PUBLICADA`. The GET now serves its setlist.
2. **Confirmation.** `Publicar lista` changes nothing anywhere; it shows the prompt, details,
   `Publicar` and `Cancelar`. `Cancelar` returns to the button. A double tap on `Publicar` sends once.
   The confirmation survives rotation.
3. **No optimistic state.** While `Sending`, the badge stays `BORRADOR`, the note stays, and the
   block shows `Publicando…` with no controls. The musician view (flag off) on the same device still
   shows `En preparación`.
4. **Failure is unmissable.** For `AccessRefused`, `Offline`, `Unavailable` or `Rejected(code)`: Room
   and the Sheet are unchanged (for `Offline`, the Sheet may have been written, see 6); the badge
   stays `BORRADOR`; the status block shows the failure card (title in `error`, the reason, the line
   `Los músicos siguen sin ver la lista.`, `Reintentar`, `Cerrar`) as an **assertive** live region.
   It stays until `Reintentar` or `Cerrar`; the filter, scrolling past or a refresh never hides it.
   One logcat line `BluesJam` W: `publishSetlist failed: date=2026-10-31 outcome=Rejected(empty_setlist)`
   with no URL, passphrase or song data.
5. **Server validates before writing**, in this order, each writing nothing: `invalid_date`;
   `unknown_jam` / `duplicate_date` / `jam_not_editable` (`requireEditableJam_`, unchanged); then an
   already `PUBLICADA` jam answers `{ok:true, alreadyPublished:true}` with no write; then
   `missing_header` / `duplicate_header`; then no tab or a setlist with no songs returns
   `empty_setlist` before any write.
6. **Idempotent retry.** A timeout after the server wrote (`Offline` on the device) is followed by
   `Reintentar`: the server answers `alreadyPublished:true`, the client treats it as `Published`.
   Two devices publishing at once: the lock serializes them, the second gets `alreadyPublished`.
7. **Ordered with other writes.** A publish queues on the same `order`/`writes` mutexes, so an add
   issued before it reaches the Sheet first.
8. **Published jam.** The admin sees the amber `PUBLICADA` badge and note, no publish action. A
   failed publish entry for a jam the cache now shows as published is not drawn.
9. **Empty draft.** A draft with no songs shows the badge and note but no `Publicar lista`; the server
   rejects no-tab/no-song requests as `empty_setlist` without writing.

## Repository Research

Inspected: `AGENTS.md`, `PROGRESS.md` (head), `feature_list.json`, the feature-spec skill;
`docs/specs/admin-set-key.md` and `admin-remove-song-from-setlist.md` (whole); `DESIGN.md` (tokens,
Colors, Status badge, Admin controls), the design prompt §2, `docs/design/screens/next-jam-admin.png`;
greps of `docs/domain-model.md` (Jam status, authority table), `docs/sheet-schema.md` (`estado`,
writes), `docs/apps-script-api.md` (§readJams, client mutations), `docs/risks-and-open-questions.md`,
`docs/user-and-access-model.md`, `docs/technical-discovery.md` (Observability), `CONTEXT.md`.
Code: `Post.js` (header, ACTIONS, router, `readJamsAsAdmin_`, `setKey_`, `requireEditableJam_`,
helpers), `Jams.js` (whole), `Normalize.js` exports; `:core:data` `SetlistRepository`,
`DefaultSetlistRepository`, `SetlistKeyChanges`, `KeyChange`, `SetlistDao` (tail), `JamEntities`
and `JamCacheMapping` (grep), `WriteOutcome`, `DataModule`; `:core:model` `Jam`; `:feature:next-jam`
`NextJamPresenter`, `NextJamUiModel`, `AdminControls` (head), `AdminControlsDefaults`,
`FailureMessages`, `NextJamCopy` (grep), `NextJamScreen` (grep); `BluesJamColors` (grep);
`ModuleIsolationTest` (amber allowlist); `DebugOverrides` (flags). Not opened: the bitácora (D-04,
D-13, D-15 from `AGENTS.md`), `AdminWriter`, the Node test helpers, `DemoUpcomingJamRepository`.

Findings:

- **`Jam` throws** for `PUBLISHED` with `Setlist.Withheld` (`init`). The Room mirror must flip
  `status` only while `setlist_state = 'AVAILABLE'`, or the next read of the cache crashes.
- `requireEditableJam_` accepts `BORRADOR` and `PUBLICADA` and uses `buildJams`, which skips blank
  rows, so it cannot give the sheet row. A separate finder is needed.
- A `PUBLICADA` jam with no tab is served with `missing_tab` → musicians see "unavailable". The user
  chose to prevent this state from being created through the app by refusing empty/tab-less drafts.
- The admin status block today is `AdminDraftBanner`, drawn only when `admin.draftBadge` is set (a
  draft); a published jam shows no badge to the admin.
- `:feature:next-jam` may read only amber `key` (Konsist). `BluesJamColors` already has
  `primaryAction`/`onPrimaryAction` ("publish, add song") and `published`/`onPublished`.
- No write failure is logged anywhere yet; `DataModule` already uses `android.util.Log` (`BluesJam`).
- Real-Sheet write latency is about 5 s (session 073).

## Technical Approach — Part A: server and `:core:data`

### `backend/apps-script/src/Post.js` (the only src file that changes)

- `ACTIONS.publishSetlist = { write: true, run: publishSetlist_ }`, request `{date}`, scenario 5's
  order. After the checks: `findJamRow_(sheet, formatDate, date)` maps `JAMS_FIELDS` with
  `mapColumns(JAMS_TAB, …)`, normalizes each data row's date with `isoDateCell` exactly as
  `buildJams`, and returns the 1-based row of the one match (`duplicate_date` if not exactly one).
  `writeJamStatus_(sheet, row, columns)`: `setNumberFormat('@')`, `setValue(PUBLISHED_STATUS)`,
  `SpreadsheetApp.flush()` (through `services`, so Node can fake it), then `getDisplayValue()`
  trimmed must equal `PUBLICADA`, else an `Error` (`internal_error`). Answer `{ok:true,
  alreadyPublished}`. Only the `Jams` tab is written; no jam tab, no `Catalogo`, no row created.
- `ACTIONS.checkPublish = { write: true, run: checkPublish_ }` (no repository function): delete a
  leftover `_prueba_publicar` (`PUBLISH_CHECK_TAB`), create it with the four `Jams` headers and two
  rows (`2099-01-01`/`BORRADOR` and `2099-01-02`/`BORRADOR`), run `findJamRow_` and
  `writeJamStatus_` for `2099-01-01`, read back with `buildJams(…, noTab, formatDate)`, delete the
  tab in `finally`, and require exactly `PUBLICADA` then `BORRADOR`. No route serves that tab (the GET
  reads `Jams` and tabs named by a `PUBLICADA` row's ISO date).
- Add `empty_setlist` for no tab/no songs and update the header comment, README marker and `Known:`
  list (E1: refusal).

### `:core:data`

- `setlist/PublishOutcome`: `Published(alreadyPublished: Boolean)` or `NotPublished(reason:
  WriteOutcome)`, never `Done`. `setlist/SetlistPublish(id, jamDate, state)` with `Sending` /
  `Failed(reason)`.
- `SetlistRepository` gains `suspend fun publishSetlist(jamDate: LocalDate): PublishOutcome`,
  `fun observePublishes(): Flow<List<SetlistPublish>>`; `dismiss(id)` also covers failed publishes.
- Internal `SetlistPublishes(writer, setlistDao, log: WriteFailureLog)` like `SetlistKeyChanges`;
  `DefaultSetlistRepository.publishSetlist` has the `setKey` shape (`scope.async(UNDISPATCHED)`,
  `order` → id from `ids`, `Sending` → queue on `writes`). It uses `AdminWriter.send("publishSetlist",
  {date})`: `Ok` → **first** `SetlistDao.markPublished(date)` (SQLException swallowed), **then**
  drop the entry, `Published(body.alreadyPublished == true)`. `Refused` → entry `Failed`, Room
  untouched, one `log` line, `NotPublished`.
- `cache/SetlistDao.markPublished(date): Int`: `UPDATE jam SET status = 'PUBLISHED' WHERE date =
  :date AND status = 'DRAFT' AND setlist_state = 'AVAILABLE'`. No schema or version change.
- `WriteFailureLog` (internal `fun interface`, `fun write(line: String)`); `DataModule` binds
  `WriteFailureLog { Log.w(LOG_TAG, it) }` and passes it to `DefaultSetlistRepository`. Line format
  as scenario 4 (`outcome` is `AccessRefused`, `Offline`, `Unavailable` or `Rejected(<code>)`).

## Technical Approach — Part B: UI

- **Model.** Replace `NextJamAdminUiModel.draftBadge`/`draftNote` with `status: AdminStatusUiModel(badge,
  isPublished, note, publish: PublishUiModel?)`. `PublishUiModel` is `Idle(label, events)`,
  `Confirming(prompt, details, confirmLabel, cancelLabel, events)`, `Publishing(status)` or
  `Failed(title, message, consequence, retryLabel, dismissLabel, events)`; events `RequestPublish`,
  `Confirm`, `Cancel`, `Retry`, `Dismiss`. `publish` is null for a published jam and for a draft
  whose setlist is not `Available` or has no song. Existing tests on `draftBadge` adapt.
- **Presenter.** Collects `observePublishes()` into `AdminState.publishes`; `publishConfirming:
  String?` (the date) in `rememberSaveable`. `Confirm` acts only while it equals the jam's date,
  clears it and launches `publishSetlist` undispatched (single send). `Retry` dismisses the failed
  entry and launches `publishSetlist` directly (the admin already confirmed). Precedence for the
  jam's date: a `Sending` entry → `Publishing`; else the latest `Failed` (only while the jam is
  `DRAFT`) → `Failed`; else confirming → `Confirming`; else `Idle`. Musician models are unchanged.
- **Screen.** `AdminStatusBlock` replaces `AdminDraftBanner` in the same `LazyColumn` slot under the
  header: badge, note, then the publish part. Badge: draft `badge-draft` (as today); published
  `badge-published` (`published` fill, `onPublished` text), `caption`, uppercase. The block is a
  polite live region so the flip to `PUBLICADA` is announced. `Publicar lista` and `Publicar`:
  `button-primary` (`primaryAction`/`onPrimaryAction`, `shapes.md`, full width / side by side,
  48dp, `Role.Button`). `Cancelar`, `Reintentar`, `Cerrar`: underlined `body` `text` actions, 48dp.
  Prompt `body` `text`, details and `Publicando…` `caption`/`body` `textMuted`. Failure card:
  `surface`, title in `error`, message and consequence `textMuted`, assertive live region.
- `AdminControlsDefaults` gains `status()`/`publish()`; its KDoc no longer says "no amber".
  `ModuleIsolationTest.AMBER_ROLE_ALLOWLIST["feature/next-jam"]` becomes `{key, primaryAction,
  onPrimaryAction, published, onPublished}` with a comment. Still 17 rules. `ContrastTest` gains
  `onPublished` on `published` (`onPrimaryAction` on `primaryAction` exists for `:feature:info`).
- `:app`: no change. No route.

### Approved Copy and Placement (C1, 8 October 2026; Rioplatense, vos)

- Button `Publicar lista`; prompt `¿Publicar la lista?`; details `Los músicos van a ver los 12 temas
  cuando abran o actualicen la app.` (`el tema` for 1) and `Desde la app no se puede volver a
  borrador.`; `Publicar`, `Cancelar`; status `Publicando…`.
- Published badge `Publicada`, note `Los músicos ya ven esta lista.`
- Failure: title `No se pudo publicar la lista`; consequence `Los músicos siguen sin ver la lista.`;
  `Reintentar`, `Cerrar`. Messages: `Offline` → `No pudimos confirmar la publicación: no hay conexión
  o el servidor tardó. Reintentá; si ya estaba publicada, no pasa nada.`; `empty_setlist` → `La lista
  no tiene temas. Agregá al menos uno antes de publicar.`; everything else via `failureMessage`.

## Expected File Changes

- Part A: `backend/apps-script/src/Post.js`, new `test/publish.test.js` (fake `Jams` sheet with cell
  writes, `flush`), `README.md`; `core/data/.../setlist/SetlistRepository.kt`,
  `DefaultSetlistRepository.kt`, new `PublishOutcome.kt`, `SetlistPublish.kt`, `SetlistPublishes.kt`,
  `WriteFailureLog.kt`; `cache/SetlistDao.kt`; `di/DataModule.kt`; tests `SetlistDaoTest`,
  `DefaultSetlistRepositoryTest` (or a new `SetlistPublishTest`).
- Part B: `feature/next-jam/.../NextJamPresenter.kt`, `NextJamUiModel.kt`, `NextJamScreen.kt`,
  `AdminControls.kt`, `AdminControlsDefaults.kt`, `NextJamCopy.kt`, `FailureMessages.kt`, previews;
  tests `AdminFakes.kt`, new `NextJamPublishTest`, `AdminControlsDefaultsTest`, adapted admin tests;
  `core/ui` `ContrastTest`; `konsist-test/.../ModuleIsolationTest.kt` (allowlist only).

## Durable Documentation Impact

- `docs/apps-script-api.md`: `publishSetlist`, `checkPublish`, `empty_setlist`, idempotence, client
  mapping, `Known:` list. `docs/sheet-schema.md`: the one write to `Jams` (`estado` only).
- `docs/domain-model.md`: the publish transition as built (confirmed, idempotent, rejects empty
  drafts, no unpublish). `docs/user-and-access-model.md`: musicians see it on their next refresh.
- `docs/risks-and-open-questions.md`: the "silent failed publish" row gets its mitigation; risks below.
- `.claude/skills/architecture/SKILL.md`: `publishSetlist`, `SetlistPublish`, `markPublished`,
  `WriteFailureLog`, the allowlist change. `DESIGN.md`: "Publishing, as built" under Admin controls,
  Status badge as built, Core Screens item 2.
- `AGENTS.md`, `ARCHITECTURE.md`, `CONSTRAINTS.md`: not needed.

## Implementation Plan

1. Part A server + Node tests: success writes one cell and reads back; each code writes nothing;
   `alreadyPublished`; read-back mismatch → `internal_error`; no `Catalogo` or jam tab write; GET
   serves the setlist afterwards; `checkPublish` cleans up on success and on failure. `node --test
   backend/apps-script/test/*.test.js`.
2. Part A data tests (in-memory Room, fake writer and log): `Ok` flips only that date and only when
   `DRAFT`+`AVAILABLE`; a `WITHHELD` cache is left alone and still reads; failures leave Room
   unchanged, publish `Failed` and log exactly one line with no secret; order behind an `addSong`;
   caller cancellation; `dismiss`; mirror before entry removal.
3. Failure demonstrations (mutate, see the named test fail, restore, `sha1sum -c`): (a) write before
   the `empty_setlist` check; (b) `markPublished` without the `setlist_state` condition (the
   `WITHHELD` test crashes); (c) the repository flips Room on `Offline`.
4. `CI=true ./init.sh` (three tools `wired`) for the server/data portion, then continue with Part B.
   Do not deploy or contact Apps Script during app implementation; do not read credentials.
5. Part B + Molecule tests: musician model unchanged; draft → `Idle`; confirm/cancel/double tap;
   `Sending` → `Publishing` with badge `BORRADOR`; `Failed` card, `Retry`, `Dismiss`; published →
   amber badge, no action; empty draft has no action; failed entry hidden once published.
6. Failure demonstration (d): the presenter shows `PUBLICADA` while `Sending` → the no-optimism test
   fails. Restore and check SHA-1. Then the Konsist allowlist: show a `published` read failing the
   rule before the entry is added.
7. `CI=true ./init.sh`.
8. Device (Pixel 5, `bluesjam.demoUpcomingJam`, `bluesjam.demoUpcomingJamDraft`,
   `bluesjam.debugAdmin`; **never TalkBack or accessibility settings**): badge, amber `Publicar
   lista`, confirmation, rotation, `Cancelar`; `Publicar` → `Publicando…` → `AccessRefused` card in
   the status block, badge still `BORRADOR`; `adb logcat -s BluesJam` shows the one line;
   `Reintentar` fails again; `Cerrar`. Flag off: `En preparación`. Screenshots, `uiautomator dump`.
   Restore flags.
9. Historical implementation sequence: after app implementation was complete, include the
    combined server source for this feature in the user's one shared deployment with reorder,
    adjust-lineup and assign. The planned deferred live checks were then run with the batch (see above).
    checks as part of that batch (never publish a real jam, and never call `publishSetlist` with a
    date that is today or later): `{}` lists `publishSetlist, checkPublish`; one wrong-passphrase
    request returns `invalid_passphrase`; `checkPublish` returns `ok`; invalid date, unknown jam and
    latest past jam are refused; before/after `readJams` status and song counts match. Use the
    scratchpad runner without printing URL/passphrase. No per-feature deployment.

## Verification Plan

- Node green; `CI=true ./init.sh` exit 0 with `konsist: wired` (17/17), `detekt: wired`,
  `ktlint: wired`.
- "Persists to the Sheet and musicians see the setlist": Node success + GET-after test; JVM
  musician view after the mirror shows the songs. Real-sheet checks were deferred to, and completed
  in, the shared four-feature deployment batch (see above).
- "A failed publish is unmissable and does not report success": JVM presenter tests, (c), (d), device.
- "The write failure is logged locally": JVM log capture plus the device logcat line.
- The real success path on a device needs a stored passphrase and a real draft; not run by agents.

## Evidence To Capture

Node and gate counts; (a)–(d) and the allowlist demonstration with SHA-1 restores; screenshots,
dump, logcat line; a statement that no URL, passphrase or name appeared. After the app-wide batch,
record the shared deployment and live-check evidence (see the addendum); the remote source hash
  is not exposed by the API.

## Risks

- **Refresh race.** An admin read started before the publish and stored after it shows `BORRADOR`
  again until the next refresh. Publishing again is harmless (`alreadyPublished`).
- **Ambiguous timeout.** `Offline` may follow a successful write; the copy says so and retry is safe.
- **Mapper-invalid rows.** The server counts non-blank rows; a tab whose rows the app all rejects
  publishes as `INVALID_ROWS` for musicians. App-built tabs cannot produce it.
- **Logcat is volatile** (ring buffer); acceptable under the minimal observability decision.
- **Stale musicians.** Until each device refreshes it shows `En preparación`; by design.

## Validator Checklist

- [ ] Only `Post.js` changes server-side; `publishSetlist` in `ACTIONS` with `write: true`; every
      check before the write; one `estado` cell written and read back; no row created.
- [ ] `publishSetlist` is a public repository function (D-13); Room flips only after `Ok`, only
      `DRAFT`+`AVAILABLE`; nothing optimistic anywhere.
- [ ] Failure card in the status block, persistent, assertive; one log line with no secret.
- [ ] Amber only on the publish buttons, the published badge and keys; allowlist change reviewed.
- [ ] Musician models unchanged; no feature imports another; three `wired`; no deployment/live
      check occurred during app implementation; no secret in repo or output. The subsequent live
      evidence is recorded above.

## User Decisions (8 October 2026)

- **U1: resolved by durable product documentation.** `docs/domain-model.md` says there is no
  unpublish in the MVP. Keep unpublish out of scope; no confirmation needed.
- **E1: refuse empty lists.** The user chose to reject drafts with no songs or no tab using
  `empty_setlist`, and hide the publish action for such a draft. The app must ask the admin to add at
  least one song first.
- **C1: approved as specified.** The user approved the Rioplatense copy and placement:
  amber `Publicar lista` beneath the `BORRADOR` badge; inline confirmation with song count and the
  irreversible-action note; persistent failure card in the status block beneath the header with
  `Reintentar` and `Cerrar`. See **Approved Copy and Placement** for exact strings.
- **B1: superseded/resolved (8 October 2026; completed 9 October).** The user explicitly chose one shared deployment after app
  implementation was complete for `admin-reorder-songs`, `admin-publish-setlist`,
  `admin-adjust-lineup` and `admin-assign-musician`. The deployment and live checks are complete;
  the original instruction prohibited a per-feature or partial deployment.

## Post-deployment verification (9 October 2026)

L1 listed `publishSetlist` and `checkPublish`; the wrong-passphrase probe returned
`invalid_passphrase`. `checkPublish` returned `ok` in 5.729 s. Invalid date, unknown jam, and the
latest past jam were refused with the expected codes. Before/after `readJams` matched (two jams,
26 songs; aggregate SHA-256 prefix `42CFABE0DE3C8D71`). No real jam was published. The user deployed
the source; the remote file hash was not independently observable.
