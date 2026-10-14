# Feature Implementation Spec: Three jam states (BORRADOR hidden, CREADA date only, PUBLICADA full)

## Source Feature

- `id`: jam-status-created
- `area`: core-data
- `depends_on`: assistant-create-jam (accepted 14 October 2026)
- `status`: not_started at planning time
- `source`: `feature_list.json`; decision D-26 (bitácora, 14 October 2026), amending D-04 and D-25


## Implementer Notes (orchestrator confirmations, 14 October 2026)

- **S8** combined redeploy with `admin-set-jam-status`: approved. The script changes go out once; this slice
  writes the migration guide and the next slice extends it. The user is not asked to redeploy yet.
- **S4** the GET also omits typo and empty `estado` rows (fail closed): approved.
- **S3** a past BORRADOR is hidden for everyone, admin included: approved.
- **S5** the `jamStatuses=3` compatibility parameter: approved.

## Goal

`Jam.status` gets a third value. `BORRADOR` (DRAFT) now means the jam does not exist for musicians:
no date, no list, not in Próxima jam and not in Anteriores. `CREADA` (new, CREATED) is what DRAFT
meant until now: musicians see the date, time and venue and the "La lista se está armando" card.
`PUBLICADA` (PUBLISHED) is unchanged. The admin sees and edits the setlist in all three states, with a
three-valued status badge. The jam is hidden in three places: Apps Script (the anonymous GET never
serves a BORRADOR row), the mapper (an anonymous read drops one) and the musician screens (they read a
musician projection of the snapshot). The admin flag only decides what is drawn.

## Non-Goals

- No status-change mutation and no unpublish: those are `admin-set-jam-status`. The only status write
  is still `publishSetlist`, and `createJam` still writes `BORRADOR` (D-25, unchanged).
- No new Konsist rule, no new amber role, no Room schema change, no new module.
- The assistant's prompt, `jamPlan`, `applyPlan` and the create dialog copy stay as they are (see S9).
- No edits to the real Sheet. The redeploy is a user step, combined with the next slice (S8).

## Job Story

When I create next month's jam and start building its list, I want musicians not to see it at all until
I decide to announce it (CREADA) or publish it (PUBLICADA), so I can prepare dates without
announcing them by accident.

## Users And Permissions

- Musician (no admin flag): sees CREADA and PUBLICADA jams only, and songs only for PUBLICADA.
- Admin (flag on): sees all three states with their songs on Próxima jam. Every write is still
  authorized by the Apps Script passphrase guard (D-11). The flag never authorizes anything.

## Decisions (planner; S1-S9)

- **S1 Musician's upcoming jam.** For a musician it is the earliest jam that is not historical and not
  DRAFT. When the earliest future jam is BORRADOR and a later one is CREADA/PUBLICADA, the musician sees
  the later one; with none, the no-upcoming state. Justification: the server GET omits BORRADOR rows, so
  an anonymous device *can only* compute it this way. The client rule must give the same answer from
  an admin-read cache, for example after logout, or two devices would disagree. The admin still sees the
  BORRADOR jam as upcoming, because writes target it (`requireEditableJam_` is unchanged apart from CREADA).
- **S2 Snapshot projection.** `JamsSnapshot` gains `heldBack: List<Jam> = emptyList()`, the future jams
  after `upcoming` in date order. It is the last parameter, so positional constructors still compile.
  A new public pure `JamsSnapshot.forMusicians()` in `com.bbbjam.core.data.jams` drops every DRAFT jam
  from upcoming + heldBack and from past, then re-splits. It is idempotent and keeps `freshness`. Musician
  screens apply it, and the admin pickers and the assistant keep the raw snapshot.
- **S3 Anteriores.** A past BORRADOR is hidden for **everyone**, admin included. `:feature:past-jams` has
  no admin flag, and past jams belong to the Sheet. A past CREADA is listed like today's past draft
  (`La lista de esta jam no se publicó.`). The detail of a hidden past jam shows the existing "ya no está
  en el archivo" state.
- **S4 Server GET, fail closed.** `GET ?resource=jams` serves only rows whose trimmed `estado` is exactly
  `CREADA` or `PUBLICADA`. `BORRADOR`, typos and empty values are omitted entirely, so not even the
  date or venue goes out. Diagnostics for typos are kept, because the admin read (`readJams`) still
  returns every row and the mapper still rejects an unknown `estado` (`InvalidStatus`, unchanged).
  `buildJams` is **not** filtered, because the admin read, the writes and the checks use it. The filter is
  applied in the GET route only. Duplicate-date detection still runs on the full table.
- **S5 Old installed builds.** Today an old build's `JamsMapper` **rejects** an unknown `estado`, so
  `CREADA` would make the jam vanish. The fix: the new client declares `jamStatuses=3` (GET query
  parameter; `"jamStatuses": 3` in the `readJams` body). Without it, the server presents
  `CREADA` as `BORRADOR`, and an old build then shows the draft card, which is exactly CREADA's
  meaning. BORRADOR is omitted for every version. With any other value, `String(v) !== '3'`, the answer
  is the error `invalid_jam_statuses`; the client never sends that, and it doubles as the deploy proof.
  An old admin build sees CREADA as BORRADOR with its songs and can still publish it.
- **S6 Room.** `jam.status` is TEXT holding the enum name (`JamCacheMapping`), so `CREATED` needs no
  schema change, no migration and no version bump (it stays version 2). An anonymous read never stores
  a DRAFT jam: `JamsMapper.map(rows, includeDrafts = false)` drops valid BORRADOR rows and lists them
  in `MappedJams.hiddenDrafts`. They are logged as `; hidden draft <date>` with a count, never with a
  venue. An admin read stores them, and only `forMusicians()` keeps them out of musician views. That
  covers a cache left from an old server or by a logout.
- **S7 Publish.** `publishSetlist` stays the only status write. The server allows it from `BORRADOR` and
  from `CREADA` to `PUBLICADA`. `PUBLICADA` still answers `alreadyPublished`, an unknown `estado` is still
  `jam_not_editable`, and an empty or tab-less list is still `empty_setlist`. The client draws
  `Publicar lista` for DRAFT and CREATED (`status != PUBLISHED`). `SetlistDao.markPublished` mirrors from
  `status IN ('DRAFT','CREATED')`. The copy is unchanged ("Desde la app no se puede volver a borrador." is
  still true until the next slice).
- **S8 Redeploy, combined.** The script changes go out in **one** redeploy together with
  `admin-set-jam-status`, which edits `Post.gs` again. This slice writes
  `docs/migrations/jam-status-script-update.md` (Code, Jams and Post), and the next slice extends it.
  Until then: new builds hide BORRADOR on the client (S6), and the old GET already withholds every
  draft's songs, so the only exposure is the date and venue of a BORRADOR row on old builds, which is
  today's behaviour. Nobody must type `CREADA` in the Sheet before the redeploy. An old server treats it
  as unknown, so the jam is not editable and an old app would hide it.
- **S9 Assistant.** It is admin-only and keeps the raw snapshot. `JamScheduleTool.statusText` becomes an
  exhaustive `when` (`BORRADOR`/`CREADA`/`PUBLICADA`). The admin read serves the CREADA tab, so
  `Usar esta lista` works on CREATED like on DRAFT (`applyPlan` reads `jam.setlist`).

## Acceptance Scenarios

1. **BORRADOR is invisible to musicians.** Given a cached upcoming DRAFT jam with songs and no
   other future jam, when a musician opens Próxima jam, then they see `NoUpcomingJam` (no date, no card).
2. **Fall-through (S1).** Given DRAFT 2026-11-07 and CREATED 2026-11-28, a musician sees 2026-11-28
   with the draft card, and the admin sees 2026-11-07 with the `BORRADOR` badge.
3. **CREADA.** Given a CREATED upcoming jam whose cached setlist is Available (an admin read), a
   musician sees header plus `SetlistUiModel.Withheld` (the "La lista se está armando" card), and the
   song detail is not found.
4. **Admin, three badges.** With the admin flag the list shows the songs in each state. The badge is
   `BORRADOR`, `CREADA` or `PUBLICADA` with its note (copy below). `Publicar lista` appears for DRAFT and
   CREATED with ≥1 song; only PUBLICADA uses the amber `published` role.
5. **Anteriores.** A past DRAFT is in neither the list nor the detail. A past CREATED is listed with
   `La lista de esta jam no se publicó.`
6. **GET (node).** A Jams tab with BORRADOR, CREADA, PUBLICADA, `Borrador` and empty `estado` rows:
   the GET serves only CREADA and PUBLICADA. With `jamStatuses=3` CREADA is `CREADA`, and
   without the parameter it is `BORRADOR` with `setlist: null`. The tab of a BORRADOR or CREADA jam is
   never requested, and `jamStatuses=9` returns `invalid_jam_statuses`.
7. **readJams (node).** It returns every row and reads the tabs of future BORRADOR **and** CREADA jams.
   The same parameter rule applies, and a past CREADA's tab is never opened.
8. **Writes (node).** For `addSong`/`setKey`/…, a CREADA jam counts as known, so it can be the upcoming
   one and is editable. `publishSetlist` on CREADA writes `PUBLICADA` and reads it back.
9. **Anonymous mapper.** `map(rows, includeDrafts = false)` drops a valid BORRADOR row (in
   `hiddenDrafts`), maps `CREADA` to CREATED with `Withheld`, and ignores a setlist sent with CREADA
   (`DraftSetlistIgnored`). With `includeDrafts = true` it keeps DRAFT and CREATED. Either one with
   `missing_tab` becomes `Available(emptyList())`.

## Repository Research

### Files Inspected

`bb-blues-jam-bitacora.md` (D-04, D-25, D-26); `docs/domain-model.md`; `docs/user-and-access-model.md`;
`docs/sheet-schema.md`; `docs/apps-script-api.md` (outline, draft rule, readJams, publish, createJam);
`DESIGN.md` (status badge, publishing, draft card, Anteriores); `CONTEXT.md` (Draft, Status);
`backend/apps-script/src/{Code,Jams,Post}.js`; `test/{jams,router}.test.js`; `test/helpers/load.js`;
`docs/migrations/assistant-create-jam-script-update.md`; `core/model/.../{Jam,JamStatus}.kt`;
`core/data/.../jams/{JamsMapper,JamsSnapshot,DefaultJamsRepository,JamsRepository,JamIssue,JamsRefreshOutcome}.kt`;
`cache/{SetlistDao,SetlistJamDao,JamCacheMapping,JamEntities}.kt`; `remote/{AppsScriptTransport,OkHttpAppsScriptTransport}.kt`;
`assistant/JamScheduleTool.kt`; `feature/next-jam/.../{NextJamPresenter,NextJamAdminMapping,NextJamCopy,NextJamUiModel}.kt`;
`feature/past-jams/.../{PastJamsPresenter,PastJamDetailPresenter}.kt`;
`feature/assistant/.../{JamPlan,ApplyPlan,ApplyMapping,JamCopy}.kt`; `app/src/debug/.../{DemoUpcomingJam*,DebugOverrides}.kt`;
`app/.../BluesJamApp.kt`. I did **not** inspect the real Sheet. It is an assumption, from PROGRESS, that
the 2026-10-31 jam is PUBLICADA, and it is unknown whether past rows are BORRADOR.

### Existing Patterns To Follow

- Musician screens decide visibility from the status (`setlistForMusicians()`), not from the read.
- A Sheet value travels as text, and only the Kotlin mapper interprets it. The GET's status rule is the
  documented exception, like the PUBLICADA-only setlist rule.
- Node tests come first and are saved failing (`docs/evidence/<id>/node-failing-run.txt`).

## Technical Approach

**Server.** In `Jams.js`, add `DRAFT_STATUS = 'BORRADOR'` (move it out of `Post.js`: one definition)
and `CREATED_STATUS = 'CREADA'`. Add `MUSICIAN_STATUSES_ = [CREATED_STATUS, PUBLISHED_STATUS]`, plus two
pure helpers: `musicianJams_(jams)` (the S4 filter) and `presentStatuses_(jams, jamStatuses)`. The second
throws `ContractError('invalid_jam_statuses')` when the value is present and not `'3'`, and maps CREADA
to BORRADOR when the value is absent.

In `Code.js`, `handleGet` passes `params` to the route. The jams route keeps the unfiltered build in a
`readAllJams_(spreadsheet)` helper and returns `presentStatuses_(musicianJams_(…), params.jamStatuses)`.
The value is validated **before** the Sheet is opened.

In `Post.js`, `readJamsAsAdmin_` calls `readAllJams_` (not `ROUTES.jams`), reads the tabs of future
jams whose status is BORRADOR or CREADA, and applies `presentStatuses_(…, request.jamStatuses)` last.
`requireEditableJam_`'s `known` adds CREADA. Update the file header comments.

**`:core:model`.** `JamStatus.CREATED` sits between DRAFT and PUBLISHED, with KDoc. In `Jam`, add the
`isVisibleToMusicians` property (`status != DRAFT`); `setlistForMusicians()` returns `Withheld` unless
the jam is PUBLISHED. Update the KDocs.

**`:core:data`.**
- `JamsMapper`: `STATUSES` gains `"CREADA" to CREATED`. In `mapSetlist`, "draft" becomes
  `status != PUBLISHED`. Without `includeDrafts`, a valid DRAFT row goes to `MappedJams.hiddenDrafts`
  (dates) and never to `jams`.
- `JamsRefreshOutcome.Updated` gains `hiddenDrafts: List<LocalDate>`, and the log adds
  `N hidden drafts` and `; hidden draft <date>`.
- `JamsSnapshot.heldBack` plus `forMusicians()` (S2); `CachedJams.toSnapshot` fills `heldBack`.
- The GET sends `jamStatuses=3`. Change `AppsScriptTransport` to `get(resource, parameters: Map<String,
  String>)`, a fun interface with no default, so the catalog passes `emptyMap()`. Update the fakes.
- The admin read: `writer.send(READ_JAMS, mapOf("jamStatuses" to JsonPrimitive(3)))`.
- `SetlistDao.markPublished`: `status IN ('DRAFT','CREATED')`.
- `JamScheduleTool.statusText`: an exhaustive `when` (S9).

**`:feature:next-jam`.** In `JamsSnapshot.toUiModel`, when `admin == null` it starts from
`forMusicians()`. In `AdminState.toUiModel`, the badge and note come from a `when (jam.status)`.
`isPublished = status == PUBLISHED` and publish is offered when `status != PUBLISHED`. New copy in
`NextJamCopy`:
- `CREATED_BADGE = "Creada"`
- `CREATED_NOTE = "Los músicos ven la fecha, pero todavía no la lista."`
- `DRAFT_NOTE` becomes `"Los músicos todavía no ven esta jam."`

CREADA uses the muted draft style (no amber). `SongDetailPresenter` needs no change: CREATED is withheld
by `setlistForMusicians()`.

**`:feature:past-jams`.** `toUiModel` and `toPastJamDetail` start from `forMusicians()`.

**`:app`.**
- `toCacheLine` appends ` created` for CREATED.
- Debug: add `bluesjam.demoUpcomingJamCreated` → `BuildConfig.DEMO_UPCOMING_JAM_CREATED` (release hard
  `false`). `DebugFlags.demoUpcomingJamCreated` exists, and `DemoUpcomingJam.on/startingAt` take a
  `status: JamStatus` (draft wins if both flags are on).
- In live mode the decorator moves the replaced real upcoming jam to the front of `heldBack` (when its
  date differs), so S1's fall-through is visible on a device.

## Expected File Changes

- `backend/apps-script/src/{Jams,Code,Post}.js`: modify (above). `test/{jams,router,post,setlist}.test.js`:
  add the failing tests first, then amend the existing assertions that expect a BORRADOR row in the GET.
  List each amended test in the evidence.
- `core/model/.../{JamStatus,Jam}.kt` and tests: modify.
- `core/data/.../jams/{JamsMapper,JamsSnapshot,DefaultJamsRepository,JamsRefreshOutcome}.kt`,
  `remote/{AppsScriptTransport,OkHttpAppsScriptTransport}.kt`, `catalog/DefaultCatalogRepository.kt`,
  `cache/SetlistDao.kt`, `assistant/JamScheduleTool.kt` and tests: modify.
- `feature/next-jam/.../{NextJamPresenter,NextJamAdminMapping,NextJamCopy}.kt` and tests:
  modify. `PublishStatusBlock`: only if the style keys need it.
- `feature/past-jams/.../{PastJamsPresenter,PastJamDetailPresenter}.kt` and tests: modify.
- `app/build.gradle.kts`, `app/src/debug/.../{DemoUpcomingJam,DemoUpcomingJamRepository,DebugOverrides}.kt`,
  `app/src/release/...` (no-op parity, only if a signature changes), `BluesJamApp.kt` and `testDebug`
  tests: modify.
- `docs/migrations/jam-status-script-update.md`: create (S8).
- `docs/api-samples/*`: **unchanged**. They stay the unfiltered `buildJams` output; say so in apps-script-api.

## Visual Design Impact

- UI involved: yes. The source is `DESIGN.md` §Status badge, §Publishing, §Admin controls and the
  draft card. The admin status block gets a third muted value, `CREADA`, which uses the `badge-draft`
  style through `AdminControlsDefaults`/`PublishStatusBlock` (no amber, so the allowlist is unchanged).
  The musician draft card is unchanged and is now drawn for CREADA. No new screen and no new artifact.

## Durable Documentation Impact

- `ARCHITECTURE.md` / `CONSTRAINTS.md`: not present in the repo. The architecture skill plays that
  role. **Update** `.claude/skills/architecture/SKILL.md` with an "As built by `jam-status-created`"
  paragraph, and change the `setlistForMusicians()` bullet to add "and the snapshot through
  `forMusicians()`".
- `AGENTS.md`: not needed.
- Update these docs:
  - `docs/domain-model.md`: Core Concepts, Jam status lifecycle, the authority row "Jam status"
    (D-26), publishing from CREADA.
  - `docs/user-and-access-model.md`: the three hiding layers and old builds.
  - `docs/sheet-schema.md`: `estado` values, the mapper table, the endpoint's half, the upcoming rule
    for writes, migration of existing rows.
  - `docs/apps-script-api.md`: the GET status rule, `jamStatuses`, readJams, the addSong row 5 rule,
    publish from CREADA, the samples note.
  - `CONTEXT.md`: Draft and Created, Status.
  - `DESIGN.md`: the badge, the publishing block, past drafts hidden.
  - `docs/risks-and-open-questions.md`: old builds and the redeploy ordering.
  - The bitácora already holds D-26, so it is not needed there.

## Implementation Plan

1. Write the node tests for scenarios 6-8 and run them **failing**, saving
   `docs/evidence/jam-status-created/node-failing-run.txt`. Implement the server, then run green.
2. Write the `:core:model` and `JamsMapper`/`forMusicians` tests failing (unresolved `CREATED` counts as
   failing), then implement `:core:model` and `:core:data`, including the transport parameter and the DAO.
3. Next, the presenter tests (next-jam scenarios 1-4, past-jams scenario 5), then the features.
4. Then debug flags and the decorator, and the startup line.
5. Write the docs and the migration guide.
6. Run `CI=true ./init.sh`, then the device check.

## Verification Plan

- `node --test backend/apps-script/test/*.test.js`: failing run first, then every test passes.
- `CI=true ./init.sh` exits 0 with konsist, detekt and ktlint `wired`. Konsist stays at 19 rules. No
  rule loosened, no baseline.
- JVM tests that must exist:
  - `JamTest`: visibility and `setlistForMusicians` for 3 statuses.
  - `JamsMapperTest`: CREADA, hidden drafts, `includeDrafts`.
  - `forMusicians` tests: fall-through, past, idempotent.
  - `DefaultJamsRepositoryTest`: `heldBack`; GET query `jamStatuses=3` via the fake;
    readJams body field; log line.
  - `OkHttpAppsScriptTransportTest`: the query is sent and the catalog sends none.
  - `SetlistDao` test: `markPublished` from CREATED.
  - NextJam presenter or mapping tests for scenarios 1-4.
  - PastJams tests for scenario 5.
  - `JamScheduleTool` test.
  - `testDebug`: the created flag, and live mode moving the real upcoming jam to `heldBack`.
- **Device (read-only, no Sheet writes).** Musician views need the admin flag off. If the Pixel holds a
  stored passphrase, do **not** log out. Use an emulator with a fresh install, or report the step as
  not run. Use `demoUpcomingJamLive=true` with:
  - D1, draft flag: the musician sees the real 2026-10-31 jam (fall-through).
  - D2, created flag: the musician sees the draft card.
  - D3, no flag: full list.
  - D4, `debugAdmin=true`: the badges `BORRADOR`, `CREADA` and `PUBLICADA` above the songs.
  Take screenshots and an `uiautomator dump` for D4. Tap no admin control. Restore `local.properties`
  afterwards (compare the md5).

## Evidence To Capture

Node failing and final runs; gate output with the test count; the list of amended pre-existing tests;
device screenshots D1-D4 (or the reason they were not run); the startup log line; `local.properties` md5
before and after. Status `passing` in `feature_list.json`, plus a PROGRESS entry that says the script
redeploy is **owed, combined with admin-set-jam-status**.

## Migration Guide Content (`docs/migrations/jam-status-script-update.md`)

- Replace the whole content of `Code.gs`, `Jams.gs` and `Post.gs`, with Ctrl+F strings per file:
  - `Jams.gs`: `var CREATED_STATUS`
  - `Code.gs`: `musicianJams_(`
  - `Post.gs`: `readAllJams_(`
- Deploy as a **new version of the same deployment**.
- Proof without a passphrase or a write: `GET ?resource=jams&jamStatuses=9` answers `invalid_jam_statuses`.
  The old deploy returns jams instead, and `internal_error` means a file is missing.
- Existing rows: the 2026-10-31 jam (assumed PUBLICADA) is unaffected. After the redeploy, every
  `BORRADOR` row, past or future, disappears for musicians. To keep the old "fecha + la lista se está
  armando" behaviour, change it to `CREADA`, **only after** the redeploy. A past jam to be listed in
  Anteriores also needs `CREADA` or `PUBLICADA`.
- Old app versions keep working (S5).

## Validator Checklist

- [ ] S1-S9 implemented as stated, and no status mutation beyond `publishSetlist` added.
- [ ] The GET omits every non-CREADA/PUBLICADA row; the BORRADOR/CREADA tabs are never opened by the GET;
      the node failing run is saved.
- [ ] No musician path reads `snapshot.upcoming`/`past` without `forMusicians()` (next-jam list, past
      list, past detail); the admin pickers and the assistant are unchanged.
- [ ] No Room version change, Konsist at 19 rules, no new amber role.
- [ ] Docs and the migration guide updated; PROGRESS records the owed combined redeploy.
- [ ] `CI=true ./init.sh` green with three `wired`; device evidence is read-only or honestly reported
      as not run.
