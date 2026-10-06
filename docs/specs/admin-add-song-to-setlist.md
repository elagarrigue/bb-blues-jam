# Feature Implementation Spec: Add a song from the catalog to the setlist

## Source Feature

- `id`: admin-add-song-to-setlist
- `area`: feature-next-jam
- `depends_on`: apps-script-write-auth (`in_progress`, implemented in c9ca15f, **not accepted**),
  next-jam-read-only-list (`accepted`)
- `status`: `not_started` at planning time (5 October 2026)
- `source`: `feature_list.json`

## Readiness

**Implementation must not start yet.** It waits for two things, in this order:

1. `apps-script-write-auth` is `accepted`, which needs the user's redeploy of its `Post.js` and
   live checks L1–L6. This spec builds on that `Post.js` (the `ACTIONS` table with `{write, run}`,
   the router guard and the script lock) and on `AdminWriter` and `WriteOutcome` as committed in
   c9ca15f. If acceptance changes either one, re-read this spec before you start.
2. A **separate** user redeploy of this slice's `Post.js`, done after write-auth's live checks pass
   and batched with nothing else. Only `Post.gs` changes. `Code.js`, `Jams.js`, `Catalog.js` and
   `Normalize.js` stay byte-identical, so the musicians' read path cannot regress.

**Size: too large for one session** (approval S1). The slice combines two data flows, the admin read
of a draft and the add mutation, with one new screen. The spec is written in two parts with their
own evidence. Part A is the backend and `:core:data` with no UI. Part B is the UI. Recommended: split
Part A into a new feature entry. If the user declines, implement A and then B as two sessions of
this one feature.

## Goal

The admin, on Próxima jam, sees the upcoming jam's songs even while the jam is a draft. They tap
**Agregar tema**, pick a song from the catalog, and are taken back to the list right away. The song
shows at the end of the list as pending ("Agregando…"). Apps Script appends it to the jam tab, under
the passphrase guard and the lock, at the next position, in the key the admin chose, with the
default lineup of seven open slots. When the write succeeds, the row becomes a normal row, stored in
the cache from the server's answer. When it fails, the pending row disappears and a persistent card
says why. No row is left in Room or in the Sheet.

The mutation is `SetlistRepository.addSong(jamDate, songId, key)` in `:core:data`. It works without
any UI, so `action-contract-registry` can register it later (D-13).

## Non-Goals

- Creating a jam. With no upcoming jam there is no add button (approval J1). The admin creates the
  `Jams` row by hand in the Sheet.
- Remove, set key, adjust lineup, assign, clear, reorder and publish: their own slices. There is no
  key picker here (approval K1). The **Publicada** badge belongs to `admin-publish-setlist`.
- Offline queueing. An add made offline fails with `Offline` and shows the failure card. This
  settles the open question in the risks doc the way that doc recommends: blocked, with a specific
  message.
- Deeplinks (deferred to `action-contract-registry`, D1) and the registry itself.
- Seeding the passphrase into debug builds. No build reads `bluesjam.debugAdminPassphrase`.
- Editing past jams (D-04) and any write to `Catalogo`.

## Users And Permissions

- Musician: nothing changes. Every musician screen still reads `Jam.setlistForMusicians()`. A
  draft's songs reach only a device that stores a passphrase, and are never drawn without the admin
  flag.
- Admin (`AdminSession.observeIsAdmin()` true): Próxima jam reads `jam.setlist` and draws the admin
  controls. Every write and the draft read go through `AdminWriter`. Apps Script authorizes them,
  never the flag.
- Debug admin (`bluesjam.debugAdmin`): the controls are drawn. No passphrase is stored, so jams are
  read anonymously and an add returns `AccessRefused` without sending a request. This is the
  device-check path for rollback.

## Acceptance Scenarios

1. **Draft visible to the admin only.** The upcoming jam is `BORRADOR` with songs, and a passphrase
   is stored. After a refresh, Room holds the draft's songs. In admin mode, Próxima jam shows them
   with the muted badge `BORRADOR` and the line `Los músicos todavía no ven esta lista.` After
   logout, the same cache shows the draft card, and the song detail shows nothing.
2. **Musician read unchanged.** With no passphrase stored, the refresh is the anonymous GET, as
   today. The GET response for any spreadsheet is byte-identical to before.
3. **Add succeeds.** Given an editable upcoming jam, when the admin picks `crossroads` (default key
   `A`), the picker closes. A pending row `Crossroads · Agregando…` sits at the end of the list. The
   server appends a row with `posicion = max + 1`, `id_tema`, the catalog's `titulo`/`artista`,
   `tono = A`, seven empty slot cells and an empty `Otros`, and answers
   `{ok, position, title, artist}`. Room then holds that row with seven open slots, and the pending
   row is gone. `readJams` returns the row.
4. **Add fails, no phantom row.** For `AccessRefused`, `Offline`, `Unavailable` or `Rejected(code)`,
   the pending row disappears. The admin sees a failure card titled
   `No se pudo agregar «Crossroads»` with the outcome's message and `Cerrar`. Room and the Sheet are
   unchanged. The card stays until the admin taps `Cerrar`.
5. **Server validates before any write.** The checks run in this order, and each failure leaves the
   spreadsheet untouched:
   - a bad `date` gives `invalid_date`;
   - a bad `songId` gives `invalid_song`;
   - a bad `key` gives `invalid_key`;
   - no `Jams` row for the date gives `unknown_jam`, and two rows give `duplicate_date`;
   - a jam that is historical, has an unknown `estado`, or is not the earliest non-historical jam
     gives `jam_not_editable`;
   - an id that is not in `Catalogo` exactly once, or whose title or artist is blank, gives
     `unknown_song`;
   - a broken tab gives `missing_header` or `duplicate_header`;
   - an id already in the tab gives `song_already_in_setlist` (J1).
6. **First song of a fresh draft.** The jam has a `Jams` row and no tab. The admin read shows an
   empty list with the add button. `addSong` creates the tab, with the header row in documented
   order, and appends at position 1.
7. **Writes are serialized.** Two quick adds are sent one after the other, in tap order, and get
   consecutive positions. The router lock does the same across devices.
8. **Survives the caller.** Leaving the picker or Próxima jam does not cancel a write in flight. The
   write runs in `DataScope`.

## Repository Research

### Files Inspected

`AGENTS.md`, `PROGRESS.md` (head, latency notes), `feature_list.json` (this entry, write-auth,
`action-contract-registry` and the admin slices), `CONTEXT.md`, `.claude/skills/architecture/SKILL.md`,
`DESIGN.md` (front matter, Colors, Components, Core Screens, Required States), the design prompt
(§1, §2, rules), `docs/design/README.md` and the export `pr_xima_jam_vista_admin/screen.png`,
`docs/domain-model.md`, `docs/sheet-schema.md`, `docs/apps-script-api.md`,
`docs/user-and-access-model.md`, `docs/risks-and-open-questions.md`, `docs/technical-discovery.md`
(latency), the bitácora D-04 to D-19, and `docs/specs/apps-script-write-auth.md`. I also read this
code:

- `backend/apps-script/src/Post.js`, `Jams.js`, `Code.js` (head), `Normalize.js` (outline),
  `appsscript.json`, `test/helpers/load.js`, `format.js`;
- `core/data`: `admin/*`, `jams/*` (repository, mapper, snapshot), `cache/JamEntities.kt`,
  `JamsDao.kt`, `JamCacheMapping.kt`, `remote/Envelope.kt`, `di/DataModule.kt`, `catalog/`
  interfaces;
- `core/model`: `Jam`, `Setlist`, `JamSong`, `Lineup`;
- `feature/next-jam`: presenter, UiModel, screen (head), copy, module, build file;
- `app`: `AppRoutes`, `AppNavHost`, `TabsShell` (grep), `debug/DebugOverrides.kt`,
  `DemoUpcomingJam.kt`.

The specs for next-jam-read-only-list, song-row-expansion, unpublished-setlist-state,
admin-passphrase-login and debug-admin-session were **not reopened**. Their as-built rules came from
the architecture skill and `DESIGN.md`.

### Findings

- The GET `jams` route withholds every non-`PUBLICADA` setlist and never opens its tab.
  `JamsMapper` maps a DRAFT to `Withheld` and ignores a setlist sent with one. The cache already
  stores a DRAFT jam with an `AVAILABLE` setlist (`JamEntity.setlistState`), and `Jam` allows it, so
  **no Room schema change** is needed.
- `refresh()` replaces the whole cache from one response. A draft read that was separate from the
  refresh would be wiped by the next GET, so for the admin the draft read must **be** the refresh.
- `AdminWriter.write` returns `WriteOutcome` with no payload, and `decodeOk` returns `Unit`. The add
  needs the position back, and the draft read needs `jams`, so both need a payload path.
- `Jams.js` exports `buildSetlist` and the field lists, and every src file shares one global scope,
  in Node too. `Post.js` can therefore reuse `readJams_`, `buildSetlist`, `mapColumns`,
  `isoDateCell` and `SETLIST_FIELDS`/`SLOT_FIELDS`/`EXTRA_FIELD` without editing those files.
  `test/helpers/format.js` fakes `Utilities`.
- The debug demo draft jam carries `Setlist.Available` songs. With `debugAdmin`, the admin view
  draws them, and an add to it returns `AccessRefused` with no request.
- `NextJamPresenter(JamsRepository, JamCalendar)` reads no admin flag today. `SetlistUiModel.Empty`
  holds musician copy ("La lista está publicada…"), which is wrong for an admin's empty draft.

## Technical Approach — Part A: server and `:core:data` (no UI)

### A1. `backend/apps-script/src/Post.js` (the only src file that changes)

New `ACTIONS` entries. The router guard covers them with no change to the router.

- `readJams: { write: false, run: readJamsAsAdmin_ }`. It takes the result of `readJams_(spreadsheet)`,
  the same function as the GET. Then, for each jam whose `status` is exactly `BORRADOR` **and** whose
  `date` is today or later (`todayIso_`), it fills `setlist`/`setlistError` with the same guards as
  `buildJams`: `invalid_date`, `duplicate_date`, `missing_tab`, and `buildSetlist` errors. Every other
  jam is left exactly as the GET built it. It answers `{ok:true, jams:[…]}`.
- `addSong: { write: true, run: addSong_ }`, with request `{date, songId, key}`. It validates in the
  order of scenario 5, using `SONG_ID_` `/^[a-z0-9]+(-[a-z0-9]+)*$/`, `KEY_` `/^[A-G][#b]?m?$/` and
  `todayIso_`. It reads `Jams` through `readJams_` (normalized dates), and `Catalogo` with display
  values and trimmed `id`. Nothing is written until every check passes. Then:
  - When the tab is missing, insert it with the header row `posicion, id_tema, titulo, artista, tono,
    Guitarra 1, Guitarra 2, Bajo, Batería, Voz, Armónica, Teclados, Otros`.
  - Compute `position = 1 + max(valid integer posicion)`, ignoring invalid cells.
  - On row `getLastRow() + 1`, set the number format `@` (plain text) on the mapped cells **before**
    `setValues`, so Sheets cannot convert a title like `7/4`. Write the values into the mapped
    columns only.
  - The answer is `{ok:true, position, title, artist}`.
  - The server never reads `tono_default`. The key is what the client sent (D-08).
- `checkSetlistWrite: { write: true, run: checkSetlistWrite_ }`. It is used only for the live check,
  if approval L1(a) is chosen. It runs `addSong`'s own tab-creation and row-append functions on the
  transient tab `_prueba_lista`: it deletes a leftover, creates the tab, appends a marker row, reads
  it back with `buildSetlist`, compares, deletes the tab and answers `{ok:true}`. It has no
  repository function.
- `todayIso_(spreadsheet, services)` = `Utilities.formatDate(new Date(services.now),
  spreadsheet.getSpreadsheetTimeZone(), 'yyyy-MM-dd')`.
- New codes: `invalid_date`, `invalid_song`, `invalid_key`, `unknown_jam`, `jam_not_editable`,
  `unknown_song`, `song_already_in_setlist`. `duplicate_date`, `missing_header` and
  `duplicate_header` are reused. Messages are English and never contain the passphrase.
- Update the header comment, `module.exports` and the README's marker and `Known:` list.

### A2. `:core:data`

- `remote/Envelope.kt`: add `decodeOkObject(body): Decoded<JsonObject>`. It does the same checks
  as `decodeOk` and returns the root object.
- `admin/AdminWriter.kt`: add `internal suspend fun send(action, fields): AdminAnswer`, where
  `AdminAnswer` is `Ok(body: JsonObject)` or `Refused(outcome: WriteOutcome)`, never `Done`. It
  uses the same store, body and mapping rules. `write` becomes `send` with `Ok` mapped to `Done`.
  W3 is unchanged: no answer touches the store. Rename the KDoc to "the admin POST path".
- `jams/JamsMapper.map(rows, includeDrafts: Boolean = false)`. With `true`, a DRAFT jam's
  setlist and error are mapped like a published one's, with one difference: DRAFT plus
  `missing_tab` gives `Available(emptyList())`. With `false`, nothing changes. A published jam is
  mapped identically either way.
- `jams/DefaultJamsRepository` gains `AdminWriter` and `AdminCredentialStore` (both internal, same
  module). In `fetchAndStore`:
  - If a passphrase is stored and it is not the one refused earlier in this process, POST
    `readJams`. On `Ok`, decode `jams` with `JamDto` and map with `includeDrafts = true`.
  - If the server refuses the passphrase (`AccessRefused`), remember that refusal in memory only
    (compare the stored value; never persist or log it) and fall back to the GET for this refresh.
    That way a stale device does not burn the W2 rate limit on every refresh.
  - On `Offline`, `Unavailable` or `Rejected`, record a failed refresh and leave the cache
    untouched. Never fall back to the GET there: it would wipe the admin's draft songs, and `busy`
    can happen mid-edit.
  - Otherwise, the GET as today.
  - `JamsRefreshOutcome.toLogLine()` appends ` (admin read)`. The log never shows the passphrase or
    a name.
- New package `setlist/`:
  - The public `SetlistRepository`:
    - `suspend fun addSong(jamDate: LocalDate, songId: SongId, key: Key): AddSongOutcome`;
    - `fun observeAdds(): Flow<List<SetlistAdd>>`;
    - `fun dismiss(id: Long)`.
  - `AddSongOutcome` is `Added(position)` or `NotAdded(reason: WriteOutcome)`, where `reason` is
    never `Done`.
  - `SetlistAdd` is `(id, jamDate, songId, title, artist, key, state)`, where `state` is `Sending`
    or `Failed(reason)`.
  - The internal `DefaultSetlistRepository(AdminWriter, JamsDao, CatalogDao, DataScope)`:
    1. Resolve the title and artist from the catalog cache. Add a `CatalogDao` `suspend fun
       song(id)`. If the song is not cached, the answer is `NotAdded(Rejected("unknown_song"))`,
       no request is sent, and a `Failed` entry is published.
    2. Publish a `Sending` entry.
    3. Run the write in `scope.async`, serialized by a `Mutex` so the order follows the calls, and
       await it. The caller's cancellation does not cancel the write.
    4. On `Ok`, parse `position`, `title` and `artist`. A malformed answer gives `NotAdded(Unavailable)`.
    5. Insert into Room in one transaction with `JamsDao.insertSetlistSong(song, slots)`. Use
       `@Insert(onConflict = IGNORE)`, so a refresh that already brought the row wins. Insert only
       while the cached jam's `setlist_state` is `AVAILABLE`. Otherwise, `refresh()` brings the row
       later.
    6. Insert seven open `JamSlotEntity` rows, columns 0..6, from `Lineup.DEFAULT_INSTRUMENTS`
       (D-18). Then remove the entry.
    7. On any other outcome, replace the entry with `Failed(reason)` and write nothing to Room.
  - The entries live in memory (a `MutableStateFlow`). They are lost on process death, which is
    documented as a risk.
- `di/DataModule`: `single<SetlistRepository> { DefaultSetlistRepository(get(), get(), get(), get()) }`.
  `DefaultJamsRepository` takes its two new arguments.

## Technical Approach — Part B: UI in `:feature:next-jam` and `:app`

- `NextJamPresenter(jams, calendar, adminSession, setlist)`, with
  `Params(onOpenSong, onAddSong: (LocalDate) -> Unit)` read through `rememberUpdatedState`:
  - It collects `observeIsAdmin()` with an initial value of `false`, and `observeAdds()`.
  - When `isAdmin` becomes true, `LaunchedEffect(isAdmin)` calls `jams.refresh()`, so a fresh login
    gets the draft songs.
  - For the admin, the list maps `jam.setlist`; for musicians it maps `jam.setlistForMusicians()`,
    unchanged.
  - `NextJamUiModel.Jam` gains `admin: NextJamAdminUiModel?`, which is null for musicians, so every
    existing test model stays equal. It holds:
    - `draftBadge` and `draftNote`, set only for a DRAFT jam;
    - `pending: List<PendingRowUiModel>`, the `Sending` entries for this date;
    - `failures: List<AddFailureUiModel>` (title, message, `Dismiss` event);
    - `addSong: AddSongActionUiModel?`, set only when the setlist is `Available`, empty included.
  - An admin's empty Available setlist uses the admin empty copy, not the musician's.
  - `NoUpcomingJam` gains an admin hint line (J1). There is no add button there.
  - Failure messages map from `WriteOutcome` and the rejected code (C1).
- The order of what the screen draws is header, filter bar, rows, pending rows, failure cards, then
  the **Agregar tema** button. Everything is appended after the existing rows, so nothing above
  moves (DESIGN "Admin controls"). The filter never hides pending rows or failures. The draft
  badge and note sit under the header.
- Colours go through a new `AdminControlsDefaults`:
  - button: `surfaceRaised` with `text`, `shapes.md`, 48dp, `Role.Button`, **no amber** (V1);
  - pending row: `surface` with `textMuted`;
  - failure card: `surface`, with the title in `error` and the message in `textMuted`, and
    `Cerrar` as a 48dp text action;
  - badge: the `badge-draft` tokens, the same as `DraftSetlistDefaults`.
- The picker (V1):
  - `AddSongScreen(jamDate, onBack, onAdded)` with `AddSongPresenter(catalog: CatalogRepository,
    jams: JamsRepository, setlist: SetlistRepository)` and `AddSongUiModel`.
  - The screen shows `Volver`, the title `Agregar tema` and an outlined search field. Matching is
    case- and accent-insensitive "contains" on the title or the artist.
  - Catalog songs are sorted by title, case- and accent-insensitive. Each row shows the title, the
    artist and the default key in the `key` role, with the click label `agregar a la lista`.
  - A song whose id is in the jam's setlist or in a `Sending` entry is drawn muted with
    `Ya está en la lista` and is not clickable.
  - Tapping a song calls `scope.launch { setlist.addSong(jamDate, song.id, song.defaultKey) }` and
    then `onAdded()` at once. `:app` pops the route.
  - Loading, error and empty use the `:core:ui` state components. An empty search shows a
    no-results line. The state follows the cached catalog; there is no automatic network call
    beyond what collecting the catalog flow starts.
- `:core:ui` theme: `BluesJamTheme` provides `LocalTextSelectionColors` with non-amber values
  (handle `text`, background `text` at 40% alpha). This closes the admin-passphrase-login note for
  both fields at once. `ContrastTest` and `BluesJamColorsTest` are unaffected; add a test that
  reads the values.
- `:app`:
  - `AppRoutes.ADD_SONG = "addSong/{jamDate}"`, with `addSong(date)` and `parseAddSong`;
  - an `addSong` destination in `AppNavHost`, beside the admin login: the same slide, the same
    insets and the `RESUMED` back guard; `onAdded` pops by route;
  - `TabsShell(onOpenAddSong)`, passed through `NextJamScreen(onAddSong)`.
- `nextJamModule`: `factory { NextJamPresenter(get(), get(), get(), get()) }` and
  `factory { AddSongPresenter(get(), get(), get()) }`.
- Konsist stays at 17 rules, with no allowlist change: `:feature:next-jam` stays `{key}`.

## Expected File Changes

- Part A:
  - `backend/apps-script/src/Post.js`, `test/post.test.js` (or a new `test/setlist.test.js`
    loaded the same way), `README.md`;
  - `core/data/.../remote/Envelope.kt`, `admin/AdminWriter.kt`, `jams/JamsMapper.kt`,
    `jams/DefaultJamsRepository.kt`, `jams/JamsRefreshOutcome.kt`, `cache/JamsDao.kt`,
    `cache/CatalogDao.kt`, `di/DataModule.kt`;
  - new `setlist/SetlistRepository.kt`, `AddSongOutcome.kt`, `SetlistAdd.kt` and
    `DefaultSetlistRepository.kt`;
  - tests: `EnvelopeTest`, `AdminWriterTest`, `JamsMapperTest`, `DefaultJamsRepositoryTest`,
    `JamsDaoTest`, `CatalogDaoTest`, `DataModuleTest`, and a new `DefaultSetlistRepositoryTest`.
- Part B:
  - `feature/next-jam/.../NextJamPresenter.kt`, `NextJamUiModel.kt`, `NextJamScreen.kt`,
    `NextJamCopy.kt` and `di/NextJamModule.kt`;
  - new `AdminControls.kt`, `AdminControlsDefaults.kt`, `AddSongPresenter.kt`,
    `AddSongUiModel.kt`, `AddSongScreen.kt`, `AddSongCopy.kt` and `AddSongDefaults.kt`, plus
    previews;
  - tests: `FakeJamsRepository` and new fakes for `AdminSession` and `SetlistRepository`,
    `NextJamAdminTest`, `AddSongPresenterTest`, `AdminControlsDefaultsTest`,
    `AddSongDefaultsTest`, and `NextJamModuleTest`;
  - `core/ui/.../theme/BluesJamTheme.kt` and a test;
  - `app/.../navigation/AppRoutes.kt`, `AppNavHost.kt` and `TabsShell.kt`, with `AppRoutesTest`.
- No Gradle, Konsist, manifest or Room-version change. No new dependency.

## Visual Design Impact

There is UI, in Part B. The tokens come only from `BluesJamTheme`, and the amber stays on keys alone
(V1). `DESIGN.md` gets "Admin controls, as built" and screen 2 as built. Device evidence: the Pixel
5 with `bluesjam.demoUpcomingJam`, `demoUpcomingJamDraft` and `debugAdmin` set to `true`.

## Durable Documentation Impact

- `docs/apps-script-api.md`: add `readJams`, `addSong`, `checkSetlistWrite`, the new codes, the
  validation order and the `Known:` list. Rewrite "How the admin reads a draft".
- `docs/sheet-schema.md`: `addSong` writes jam tabs (append only, plain text, a tab created when
  missing); add the transient `_prueba_lista`.
- `docs/domain-model.md`: an add appends at the next position with the default lineup and the key
  the admin sent; the admin read of a draft; duplicates refused.
- `docs/user-and-access-model.md`: draft songs are cached on a passphrase-holding device, still
  hidden by `setlistForMusicians()`; the refused admin read falls back to the GET.
- `docs/risks-and-open-questions.md`:
  - offline mutations decided (blocked);
  - the draft-read question closed;
  - new risks (Risks below);
  - write latency from the live checks.
- `.claude/skills/architecture/SKILL.md`: `SetlistRepository`, `AdminWriter.send`, the admin
  refresh path, `AddSongScreen` and the `addSong` route.
- `backend/apps-script/README.md`: a "Redeploy for add song" section.
- `AGENTS.md`, `ARCHITECTURE.md`, `CONSTRAINTS.md`: not needed.

## Implementation Plan

1. **Part A, server.** Write `Post.js` and the Node tests, then run
   `node --test backend/apps-script/test/*.test.js`. The table-driven guard tests cover the new
   actions with no edit.
2. **Part A, client.** Write the `:core:data` changes and their tests. Run `./gradlew ktlintFormat`,
   then `CI=true ./init.sh`.
3. **Part A, failure demonstrations.** Make each mutation, watch its test fail, then restore it and
   confirm the file's SHA-1 with `sha1sum -c`:
   - (a) `addSong_` creates the tab before the catalog check: "no write on `unknown_song`" fails;
   - (b) `readJamsAsAdmin_` releases `Publicada` or a past draft;
   - (c) the repository inserts into Room on `Rejected`: "no phantom row" fails;
   - (d) `includeDrafts` defaults to `true`: the musician mapper tests fail.
4. **User batch** (after write-auth is accepted, about 3 minutes, nothing else in it):
   1. Paste `src/Post.js` into `Post.gs`, save, and check the README marker.
   2. Run **Manage deployments → Edit → Version: New version → Deploy**.
5. **Live checks** (agent, `local.properties` holds W1's key). The check script lives in the
   scratchpad, uses `json.dumps` and prints only codes, counts and latencies:
   - L1: `{}` gives `Known: checkPassphrase, checkWriteAccess, readJams, addSong, checkSetlistWrite`.
   - L2: `readJams` with no passphrase gives `invalid_passphrase`.
   - L3: `addSong` with a wrong passphrase gives `invalid_passphrase`.
   - L4: `readJams` with the passphrase gives `ok`. Print only the jam count, statuses and song
     counts, and save nothing.
   - L5: non-mutating `addSong` probes: a bad date, a bad key, `1999-01-01` (`unknown_jam`), a past
     jam (`jam_not_editable`), the upcoming date with `zz-no-existe` (`unknown_song`).
   - L6: as approved in L1. With L1(a), `checkSetlistWrite` gives `ok` and its latency.
   - L2 and L3 add 2 to the W2 window.
6. **Part B.** Write the UI, presenters and tests, then run the gate.
7. **Device** (Pixel 5, debug flags on):
   1. Check the admin draft view, the badge and the add button, and that the list did not move.
   2. Open the picker: search, sort order, an already-listed song, back, rotation.
   3. Pick a song: the pending row appears, then rolls back to the `AccessRefused` card.
      `Cerrar` removes it.
   4. Flag off: the musician view is unchanged.
   5. Run `uiautomator dump` for the nodes and labels.

   **No TalkBack or accessibility settings, ever.**

## Verification Plan

- Node: all green (97 before, plus the new tests). This is outside `init.sh`, as before.
- `CI=true ./init.sh`: exit 0, `konsist: wired` (17/17), `detekt: wired`, `ktlint: wired`. Counts go
  up by the new and changed tests only.
- JVM proof of the feature's three checks:
  - the song appears locally: `DefaultSetlistRepositoryTest` through a real in-memory Room, and
    `NextJamAdminTest`;
  - it is a repository function: `addSong` is called with no UI in the test;
  - a failed write is visible with no phantom row: Room is unchanged and a `Failed` entry is
    published for every non-`Ok` answer.
- "Present in the Sheet after a reload": Node shows the appended row is read back by
  `buildSetlist`. Live, it is covered as L1 decides.
- Device: the steps of Implementation Plan §7, with screenshots and a dump. The success path on the
  device is not checkable without a stored passphrase, by design. The JVM and Node tests cover it.

## Evidence To Capture

Record all of the following in `feature_list.json` evidence and `PROGRESS.md`:

- Node and gate counts;
- the failure demonstrations (a)–(d), each with its SHA-1 restore;
- L1–L6 codes, L6 and `addSong`-probe latencies, and the deployed `Post.gs` SHA-1;
- device screenshots and the dump;
- a statement that the passphrase and musician names never appeared in any output.

## Risks

- **Rate limit interplay (W2).** Each admin refresh passes the guard. A stale passphrase now costs
  one failure per process, which is the mitigation. A wrong passphrase typed into the login still
  counts.
- **Refresh race.** A refresh whose read began before an add committed, and that stores after the
  local insert, hides the new row until the next refresh. The Sheet is correct.
- In-memory pending and failed entries are lost on process death. A write that reached the server
  shows up on the next refresh, but a failure that happened while the app was dying is silent.
- Draft songs sit in the Room cache of the admin's device, and stay there after logout until the
  next refresh. They are never drawn without the flag.
- The `@` text format makes `posicion` text in the Sheet. The read path already accepts that
  (`integerTextCell`).
- Two production check actions now exist (`checkWriteAccess`, `checkSetlistWrite`). Both are
  guarded, self-cleaning and never listed.

## Validator Checklist

- [ ] `Code.js`, `Jams.js`, `Catalog.js` and `Normalize.js` are byte-identical. The GET answers are
  unchanged, and the draft rule holds for anonymous reads.
- [ ] Every new action goes through the router guard, and `addSong` runs under the lock. No write
  happens before all validations; Node proves it per code.
- [ ] The key comes only from the request, never from `tono_default` (D-08). The lineup is the
  default seven (D-18).
- [ ] `addSong` is a public `:core:data` repository function usable without UI (D-13). No Room row
  is written on any failure.
- [ ] Musician screens still read `setlistForMusicians()`. The admin view needs the flag (D-15). No
  `:feature:admin`, and no feature imports another feature.
- [ ] No amber outside the keys. The copy is as approved. Admin controls only append below the
  rows.
- [ ] The gate prints three `wired`. Node is green. The live checks are recorded. No secret, URL or
  name appears in the repo or in any output.

## User Approvals

Each question has a recommendation. Ask before implementing.

- **S1: split (recommended: a).**
  - (a) Add a new feature `admin-setlist-write-path` (Part A, area `backend`, depends on
    `apps-script-write-auth`) before this one, and make this feature (Part B) depend on it. Two
    sessions, one redeploy (in A).
  - (b) One feature, implemented in two sessions.
- **K1: the key when adding (D-08; recommended: a).**
  - (a) The song's catalog default key. The admin typed it in `Catalogo`, so it is the admin's own
    data, not an API's. It is shown on the picker row before the tap and on the new row. Changing
    it is `admin-set-key`.
  - (b) A key step in the add flow: every valid key, preset to the default.
  - (c) The admin must always pick a key.
- **J1: edge cases (recommended: all as listed).**
  - With no upcoming jam: no add button, and the admin line
    `Para armar la lista, cargá la fecha en la pestaña Jams de la planilla.` Adding never creates a
    jam.
  - With a `Jams` row but no tab: the first add creates the tab.
  - The same song twice in one setlist: refused (`song_already_in_setlist`), and shown as
    `Ya está en la lista` in the picker.
- **V1: interaction and visuals (recommended: as specified).**
  - The picker is a full-screen route that closes after one pick.
  - Songs are sorted by title.
  - **Agregar tema** is a non-amber button after the last row.
  - The pending row and the failure card sit in the same place. The failure card persists until
    `Cerrar`.
  - The admin draft badge is `BORRADOR` plus a note.
  - Text selection colours are non-amber app-wide.
- **C1: copy (Rioplatense, vos; recommended as written).**
  - Button and pending row: `Agregar tema`, `Agregando…`.
  - Draft badge and note: `Borrador`, `Los músicos todavía no ven esta lista.`
  - Admin empty state: `Todavía no hay temas`, `Agregá el primero desde el catálogo.`
  - Picker: `Agregar tema`, the field `Buscar por título o artista`, `Ya está en la lista`, the
    click label `agregar a la lista`, and `Ningún tema coincide con «…».`
  - Picker states: `Cargando el catálogo`, `No pudimos cargar el catálogo`, and
    `El catálogo está vacío` / `Cargá temas en la pestaña Catalogo de la planilla.`
  - Failure title: `No se pudo agregar «…»`. Messages by outcome:

    | Outcome | Message |
    |---|---|
    | AccessRefused | `La frase de acceso cambió o no es válida. Salí del modo admin en Info y volvé a entrar.` |
    | Offline | `No hay conexión. Probá de nuevo cuando tengas internet.` |
    | Unavailable | `El servidor no respondió. Probá de nuevo en un rato.` |
    | song_already_in_setlist | `Ese tema ya está en la lista.` |
    | unknown_song | `Ese tema ya no está en el catálogo.` |
    | unknown_jam, jam_not_editable, duplicate_date | `La jam cambió en la planilla. Actualizá y probá de nuevo.` |
    | any other code | `La planilla rechazó el cambio. Revisala y probá de nuevo.` |

    The action is `Cerrar`.
- **L1: live proof of the write (recommended: a).**

  Background: a "far-past test jam the agent creates and removes" is not possible. No action
  creates a `Jams` row, `addSong` refuses historical jams by design, and a test `Jams` row would be
  served to musicians while it exists.
  - (a) The self-cleaning `checkSetlistWrite` on the transient tab `_prueba_lista`, never served.
    It runs the real create-tab and append code against real Sheets. Add the non-mutating
    `addSong` probes (L5) and the `readJams` read (L4). Only the glue between the `Jams` row and
    the tab name is left to Node.
  - (b) The agent adds one song to the real upcoming jam, only if it is `BORRADOR` (invisible to
    musicians), confirms it through `readJams`, and you delete the row by hand afterwards.
  - (c) Node plus the probes only. No live write.
