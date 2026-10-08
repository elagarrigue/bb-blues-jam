---
name: architecture
description: Canonical architecture and design patterns for BB Blues Jam — module layout, allowed dependencies, where each piece goes, the composable presenter pattern, the mutation contract, and anti-patterns. Use when creating or modifying a module, a feature, a presenter, a repository, a mutation, or a Gradle dependency, and when planning, implementing, or validating any feature that touches code structure.
---

# Architecture

The rules below come from decisions D-02, D-03, D-04, D-09, D-13, D-15, D-16 and D-17, with their reasoning in
`bb-blues-jam-bitacora.md`. Do not reverse one without saying so and waiting for confirmation.

## Canonical Pattern

Clean Architecture, multi-module, with feature modules strictly isolated from each other.
Presentation is a composable presenter that returns a plain `UiModel`; there are no ViewModels.

## Module Layout

| Module | Holds | May depend on |
|---|---|---|
| `:core:model` | Domain types: `Jam`, `JamStatus`, `Setlist`, `SetlistProblem`, `JamSong`, `Lineup`, `Slot`, `ExtraParticipant`, `Instrument`, `Song`, `Tempo`, `Difficulty`, `Key`, `SongId`. Pure Kotlin, no Android, no serialization, no date parsing, no clock. | nothing |
| `:core:data` | Repository interfaces (the contracts features use) and their implementations: Apps Script client, Room cache, DataStore admin flag. | `:core:model` |
| `:core:ui` | Presenter contracts (`Presenter`, `UiModel`, `UiEvent`, `EventHandler`), design tokens, theme, shared components such as the instrument strip, and UI-side contracts features share, such as `ExternalLinkOpener` (`com.bbbjam.core.ui.link`). | `:core:model` |
| `:feature:<name>` | One screen or flow: its presenters, `UiModel`s, composables, and one Koin module. | `:core:*` only |
| `:app` | Navigation (Navigation Compose, only here, all internal in `navigation/`: the string routes in `AppRoutes.kt`; `AppNavHost.kt`, the outer host with the tabs shell (`AppRoutes.TABS`) and, above it, the song detail (`SONG_DETAIL`) the past jam detail (`PAST_JAM_DETAIL`, `pastJam/{jamDate}`, same insets and double-tap guard) and the admin login (`ADMIN_LOGIN`, `admin-login`, no argument; back pops under the `RESUMED` guard, a successful login pops by route with `popBackStack(ADMIN_LOGIN, inclusive = true)`, a no-op once gone); `TabsShell(onOpenSong, onOpenPastJam, onOpenAdminLogin)` hands the callbacks to the tabs); `TabsShell.kt`, an inner host with one route per tab (`NEXT_JAM` start, `PAST_JAMS`, `INFO`; switching with `popUpTo(start) { saveState }`, `launchSingleTop`, `restoreState`) above the `TabBar`, with the status-bar inset outside the scroll; `AppTab.kt`, the tabs in bar order with labels, icons and the pure `tabBarModel`; `AppMotion.kt`, the named durations), `BluesJamApp` (`startKoin` with every module), `appModule` (Android implementations of `:core` contracts, such as `IntentLinkOpener`), and the action registry (D-13). Since `bottom-navigation` a new top-level destination goes in the **outer** host, beside the song route, as the past jam detail does. | everything |
| `backend/apps-script` | Not a Gradle module. The Apps Script web app (`src/*.js`, `appsscript.json`), its Node tests and `tools/`. The only code that touches the Sheet; its contract is `docs/apps-script-api.md`. | nothing |
| `:konsist-test` | Test-only JVM module (`bluesjam.jvm.library`, no `src/main`) holding the Konsist architecture suite `ModuleIsolationTest`. It reads every module's sources from disk. | nothing (no project dependency) |

Feature modules are added by the slice that first needs them, not up front. `:feature:info` exists
(set by `info-screen`), `:feature:next-jam` exists (set by `next-jam-read-only-list`; the first
feature that reads data, depending on `:core:ui` and `:core:data`), `:feature:song-detail`
exists (set by `song-detail-screen`, same dependencies) and `:feature:past-jams` exists (set by
`past-jams-list`, same dependencies; Anteriores, read-only; its row colours come only from
`PastJamsDefaults`). `past-jam-detail` adds the past jam's detail to `:feature:past-jams`:
`PastJamDetailPresenter(JamsRepository)` (its only dependency, so it cannot read the admin flag),
`PastJamDetailUiModel`, the pure mapping `JamsSnapshot.toPastJamDetail(jamDate, onBack)` (past jams
only, through `setlistForMusicians()`; rows are position, title, artist and key, never a lineup),
`PastJamDetailScreen` and `PastJamDetailDefaults` (archive colours, the key in `key`); the list
rows gain an `Open` event through `PastJamsPresenter.Params(onOpenJam)`.

**Admin is a state, not a module.** An admin is a musician with extra controls on the same screens
(one app, not two). Each presenter reads the admin flag from `AdminSession` in `:core:data` and adds
admin events and controls to its own `UiModel`. The setlist mutations are drawn in
`:feature:next-jam`; the passphrase login lives in `:feature:info`, where its discreet entry point
is. There is no `:feature:admin`.

As built by `admin-passphrase-login`: `:core:data` package `admin/` holds the public
`AdminSession` (`observeIsAdmin(): Flow<Boolean>`, `suspend logIn(passphrase): LoginOutcome`,
`suspend logOut()`) and `LoginOutcome` (`Success`, `WrongPassphrase`, `Offline`, `Unavailable`),
and the internal `DefaultAdminSession(AppsScriptPostTransport, AdminCredentialStore)` and
`AdminCredentialStore` (DataStore Preferences, file `admin_session`, key `admin_passphrase`; admin
mode is "a non-blank passphrase is stored"; `passphrase()` for the write slices). `logIn` trims,
sends nothing when blank, POSTs `checkPassphrase` and stores only on `ok`. `remote/` gains
`AppsScriptPostTransport` (`suspend post(json)`, implemented by the same
`OkHttpAppsScriptTransport` instance), `AppsScriptEnvelope.decodeOk` and the serializable
`CheckPassphraseRequest`. `:feature:info` (now also depending on `:core:data`) holds
`InfoPresenter(linkOpener, adminSession)` with `Params(onOpenAdminLogin)` and a sealed
`AdminEntryUiModel` (`LoggedOut`, `LoggedIn`), and `AdminLoginPresenter(adminSession)` with
`Params(onBack, onLoggedIn)`, `AdminLoginUiModel` (its `toString` hides the passphrase),
`AdminLoginScreen`, `AdminLoginCopy` and `AdminLoginDefaults`. The typed passphrase lives in plain
`remember`, never `rememberSaveable`. Every later admin slice reads `AdminSession.observeIsAdmin()`.

As built by `apps-script-write-auth`: `admin/` adds the public `WriteOutcome` (`Done`,
`AccessRefused`, `Offline`, `Unavailable`, `Rejected(code)`) and the internal
`AdminWriter(AppsScriptPostTransport, AdminCredentialStore)` with `suspend write(action, fields):
WriteOutcome` (a `single` in `dataModule`). It sends the stored passphrase with the action in a
`JsonObject` body (never concatenated; `action` and `passphrase` are reserved field names), sends
nothing when none is stored, and **never saves or clears the store**: a refused write
(`AccessRefused`) keeps admin mode on (user decision W3). `remote/ServiceCodes` holds the error
codes the client maps, shared with `DefaultAdminSession`. **A mutation repository calls
`AdminWriter`** and returns its `WriteOutcome` (or its own outcome mapped from it); it never builds
a POST itself. **A new server action is guarded by the router**: add an entry `{ write: true, run }`
to `ACTIONS` in `backend/apps-script/src/Post.js` and never call the passphrase guard from the
action; `handlePost` runs the guard (rate limit, `Config` re-read) for every action and the script
lock for every write. `checkWriteAccess` is a deploy check with no repository function.

As built by `admin-add-song-to-setlist` Part A (backend and `:core:data`, no UI yet): `AdminWriter`
gains `internal suspend fun send(action, fields): AdminAnswer` (`Ok(body: JsonObject)` or
`Refused(outcome, failure)`, never `Done`; `write` is `send` with `Ok` → `Done`), over
`AppsScriptEnvelope.decodeOkObject`. **A mutation whose answer carries a payload uses `send`.**
The jams refresh **is** the admin read when a passphrase is stored: `DefaultJamsRepository(transport,
dao, calendar, scope, AdminWriter, AdminCredentialStore, mapper)` POSTs `readJams` and maps with
`JamsMapper.map(rows, includeDrafts = true)` (a draft maps like a published jam; a draft with
`missing_tab` is `Available(emptyList())`); `AccessRefused` remembers the refused passphrase in
memory and falls back to the GET; any other failure is a failed refresh that keeps the cache (no
GET fallback, it would wipe the draft); `JamsRefreshOutcome.Updated`/`Failed` carry `adminRead`
and the log line ends ` (admin read)`. The new package `setlist/` holds the public
`SetlistRepository` (`addSong(jamDate, songId, key): AddSongOutcome`, `observeAdds()`,
`dismiss(id)`), `AddSongOutcome` (`Added(position)`, `NotAdded(reason: WriteOutcome)`) and
`SetlistAdd` (`State.Sending`, `State.Failed(reason)`), and the internal
`DefaultSetlistRepository(AdminWriter, SetlistDao, CatalogDao, DataScope)`: title and artist from
`CatalogDao.song(id)` (not cached → `Rejected("unknown_song")`, nothing sent), writes in
`DataScope` behind fair mutexes (call order, the caller's cancellation does not cancel one), the
confirmed row and seven open slots inserted by `cache/SetlistDao.insertSetlistSong` (IGNORE, only
while the cached setlist is `AVAILABLE`, one transaction), and nothing in Room on any failure.
`SetlistDao` is its own DAO beside `JamsDao` (a mutation adds rows; a read replaces them), with no
schema change. The entries live in memory. **A later setlist mutation goes in `SetlistRepository`
and writes Room only from the server's answer**, through `SetlistDao`. Server side, `Post.js`
holds `readJams` (read), `addSong` and the deploy check `checkSetlistWrite` (writes), and the
shared `createSetlistTab_`/`appendSetlistRow_` (plain-text cells set before values, mapped columns
only); `Code.js`, `Jams.js`, `Catalog.js` and `Normalize.js` were not touched. `addSong` runs in
`DataScope` from its first instruction (undispatched), so a presenter may launch it and close its
screen at once.

As built by `admin-add-song-to-setlist` Part B: `NextJamPresenter(jams, calendar, AdminSession,
SetlistRepository)` with `Params(onAddSong, onOpenSong)` (`onOpenSong` last, so a trailing lambda
still names it). The flag is collected for drawing (`initial = false`) and, separately, in a
`LaunchedEffect(Unit)` over the flag's real values, which asks for one `jams.refresh()` per login
(a saveable `adminRefreshed`; a logout resets it), so returning to the tab does not refresh again.
For the admin the list maps `jam.setlist` and `NextJamUiModel.Jam.admin` holds
`NextJamAdminUiModel` (badge, note, pending rows, failure cards, `AddSongActionUiModel`); for
musicians `admin` is null and nothing else changes. `NoUpcomingJam.adminHint` (J1). The picker is
`AddSongPresenter(CatalogRepository, JamsRepository, SetlistRepository)` with
`Params(jamDate, onBack, onAdded)`, the pure `addSongContent(...)` and `listedSongs(...)`, and
`AddSongScreen`; a pick launches `addSong` undispatched and calls `onAdded` once (a double tap adds
once). Composables in `AdminControls.kt` (`adminItems` appends to the list), colours in
`AdminControlsDefaults` (no amber) and `AddSongDefaults` (`key` only), copy in `NextJamCopy` and
`AddSongCopy`. `:app`: `AppRoutes.ADD_SONG` (`addSong/{jamDate}`, `addSong(date)`,
`parseAddSong`), an `addSong` destination in `AppNavHost` beside the admin login (same slide,
insets and `RESUMED` back guard; a pick pops by route), `TabsShell(onOpenAddSong)` →
`NextJamScreen(onAddSong)`. `:core:ui` `BluesJamTheme` provides non-amber `LocalTextSelectionColors`.
Konsist unchanged (17 rules, `:feature:next-jam` still `{key}`).

As built by `admin-remove-song-from-setlist` Part A (backend and `:core:data`, no UI yet):
**setlist mutations identify a row by `songId` (`id_tema`), never by position** (user decision
R1 (a)); (`fecha`, `posicion`) is only the read identity. `Post.js` adds `removeSong` (deletes the
row, renumbers later `posicion` cells in ascending order with batched range writes) and the deploy
check `checkSetlistRemove`, sharing `removeSetlistRow_`. `SetlistRepository` gains
`removeSong(jamDate, songId): RemoveSongOutcome` (`Removed`, `NotRemoved(reason)`),
`observeRemoves()` with `SetlistRemove` (`State.Sending`, `State.Failed(reason)`), and `dismiss(id)`
for either kind (one id counter). `DefaultSetlistRepository` keeps the shared `order`/`writes`
mutexes, ids and `DataScope`, and delegates the removal's entries, request and cache mirror to the
internal `SetlistRemovals` it builds itself (no Koin binding; split out for detekt's
`TooManyFunctions`). `SetlistDao.removeSetlistSong(date, songId)` mirrors a confirmed removal in
one transaction (only while `AVAILABLE` and exactly one cached song has the id): explicit deletes of
the song, slots and extras from that position, then the later ones reinserted one position up
(positions are in the primary keys; the foreign keys have no `onUpdate`). Room changes only on
`Done` or `Rejected("song_not_in_setlist")`.

As built by `admin-remove-song-from-setlist` Part B (UI): `SongRowUiModel.admin:
SongRowAdminUiModel?` (null for musicians, so their rows are unchanged) holds `removal:
RemovalUiModel` (`Idle`, `Confirming(prompt, details, confirmLabel, cancelLabel)`, `Removing(status)`;
events `RequestRemove`, `Confirm`, `Cancel`). `NextJamPresenter` collects `observeRemoves()` and
holds the confirming row as one saveable `String` key (`removalKey(date, songId)`, "date|songId");
`Confirm` acts only while that key is the row's, clears it and launches `removeSong` undispatched,
so a double tap removes once. `AdminState` gains `removal: RemovalState(removes, confirming,
onEvent)`; row handlers are keyed by (date, songId, step). `NextJamAdminUiModel.failures` merges
failed adds and removes in id order (type name `AddFailureUiModel` kept). The screen draws
`AdminRowActions(row.admin)` after `Ver detalle del tema` in an expanded row; **a later row action
goes inside `AdminRowActions`, before the removal**, which stays last. Colours in
`AdminControlsDefaults.removal()` (`error`, `text`, `textMuted`; no amber).

As built by `admin-set-key` Part A (backend and `:core:data`, no UI yet): `Post.js` adds `setKey`
(writes only the matched row's `tono` cell, plain text, never opens `Catalogo`) and extracts the
row scan into **`findSongRow_(sheet, columns, values, songId)`**, the one finder every later
row-changing setlist mutation reuses (`song_not_in_setlist`, `duplicate_song`, before any write).
Its deploy proof is an extra step in `checkSetlistWrite` (the marker's key rewritten with
`writeKeyCell_`), not a new check action. `SetlistRepository` gains `setKey(jamDate, songId, key):
SetKeyOutcome` (`KeySet`, `NotSet(reason)`), `observeKeyChanges()` with `KeyChange`
(`State.Sending`, `State.Failed(reason)`), and `dismiss(id)` covers all three kinds (one id
counter). The internal `SetlistKeyChanges` holds the entries, the request and the mirror, built by
the repository like `SetlistRemovals`; the shared internal `displayedTitle` resolves an entry's
title by the `jam_song_resolved` rule. `SetlistDao.updateKey(date, songId, key): Int` is one
`UPDATE` statement whose `WHERE` carries the `AVAILABLE` and exactly-one-id conditions (one DAO
function, not a `@Transaction` pair, so the DAO stays under detekt's `TooManyFunctions`). On `Done`
the cache is updated **before** the entry is removed; any other answer leaves Room alone. **An
optimistic value comes from `Sending` entries only; a failure is the revert** (Part B draws it).

As built by `admin-set-key` Part B (UI): `KeyChangeOverlay.kt` holds
`List<KeyChange>.pendingKey(date, songId)` (the highest-id `Sending` entry, else null), the one
overlay rule both Próxima jam and the picker use; `FailureMessages.kt` holds `failureMessage` and
`keyFailureMessage` (a key change maps `song_not_in_setlist` to the jam-changed line, while a
removal keeps "Ese tema ya no estaba en la lista."). `NextJamPresenter.Params(onAddSong, onSetKey,
onOpenSong)` collects `observeKeyChanges()` into `AdminState.keyChanges`; for the admin a row with a
pending change draws its key and `keyDescription` with `SongRowAdminUiModel.keyStatus`
(`Guardando…`); `SongRowAdminUiModel(setKey: SetKeyActionUiModel, removal, keyStatus)`. Musicians
never get an overlay. Row admin actions, in order: `Cambiar tonalidad`, then the removal (last).
Failure cards merge adds, removals and key changes by id. The picker is
`SetKeyPresenter(JamsRepository, AdminSession, SetlistRepository)` with `Params(jamDate, songId,
onBack, onDone)` and the pure `setKeyModel(...)` (`Loading` until the flag and the jams are read,
`Gone` for a musician or a song no longer in the upcoming readable setlist, else `Content`); a pick
launches `setKey` undispatched and calls `onDone` once, and the current cell does nothing. Keys and
colours in `SetKeyDefaults` (`key` only, on the current key), copy in `SetKeyCopy`. `:app`:
`AppRoutes.SET_KEY` (`setKey/{jamDate}/{songId}`, `setKey(date, songId)`, `parseSetKey` with an ISO
date and `SongId.parseOrNull`), a `setKey` destination beside `addSong` (same slide, insets and
`RESUMED` back guard; a pick pops by route), `TabsShell(onOpenSetKey)` → `NextJamScreen(onSetKey)`.
Konsist unchanged (17 rules, `:feature:next-jam` still `{key}`).

As built by `live-refresh-during-jam`: **periodic refresh lives in the presenter, gated by the
screen's lifecycle**, never in a repository (it does not know what is visible), WorkManager or a
service. `NextJamScreen` reads `LocalLifecycleOwner.current.lifecycle.currentStateAsState()`
(`androidx.lifecycle.compose`, already on the classpath through Compose UI; no new dependency) and
passes `NextJamPresenter.Params(isResumed = …)` (default `false`, so a presenter nobody marks
visible never polls); each tab is its own `NavBackStackEntry`, so another tab, an outer destination,
the background or the screen off all stop it. `LiveRefresh.kt` holds the pure parts: `LiveRefresh`
(30 min before, 4 h after, 30 s, 60 s = `CatalogRepository.MIN_RETRY_INTERVAL`), `liveWindow(jam,
zone)` (`[start − 30 min, start + 4 h)` in `calendar.zone`), `JamsSnapshot.liveJams()` (`upcoming`
and `past.first()`, as date and start time only) and the suspend `runLiveRefresh(windows, now,
lastFetchedAt, jitter, refresh)`, testable in virtual time. `NextJamRefreshes.kt`
(`rememberNextJamRefreshes`) runs it in a `LaunchedEffect(isResumed, liveJams)`: **keyed by the
window jams, never by the snapshot** (each refresh emits a new snapshot; a snapshot key would restart
the loop and refresh at once, forever). Periodic refreshes are quiet: while one runs and no user
refresh (Retry or pull) does, the snapshot is drawn with `isRefreshing = false`. `NextJamPresenter`
takes a fifth constructor parameter `random: Random = Random.Default` for the jitter (Koin keeps four
`get()`s). Anteriores has the pull only. The debug flag `bluesjam.demoUpcomingJamLive` (Build
Conventions, Debug demo jam) makes the window observable on a device.

## Where Each Piece Goes

- A new domain type or rule → `:core:model`, with a unit test.
- A read or a write against the Sheet → a repository function in `:core:data`.
- A new Sheet read or write → a route in `backend/apps-script` plus a repository function in
  `:core:data`. A write is an `ACTIONS` entry guarded by the router and a repository
  function that calls `AdminWriter`.
- **A mutation** → a repository function in `:core:data`, then registered in the `:app` action
  registry. Never a lambda that only exists in a composable.
- A color, spacing or type value → `:core:ui` tokens from `DESIGN.md`. Never a literal in a feature.
  Screens wrap in `BluesJamTheme { }` (already done in `MainActivity`) and read `BluesJamTheme.colors`,
  `.typography`, `.shapes` and `.spacing` (package `com.bbbjam.core.ui.theme`), never
  `MaterialTheme.colorScheme` and never the internal palette (D-17). Amber has no public name; pick
  the role (`primaryAction`, `slotOpen`, `key`, `published`, `activeFilter` and their `on…`
  colors). A component slice that uses a Material 3 component sets its colors explicitly from
  `BluesJamColors`: Material defaults are mapped from tokens but are not design decisions.
- Amber in a feature is allowlisted per module (Konsist `amber-roles-allowlisted`,
  `AMBER_ROLE_ALLOWLIST` in `ModuleIsolationTest`): `:feature:next-jam` and `:feature:song-detail`
  may read `key` only; `:feature:past-jams` may read `key` only since `past-jam-detail` (the detail
  rows' key, through `PastJamDetailDefaults`; the Anteriores list reads no amber role);
  `:feature:info` may read `primaryAction` and `onPrimaryAction` since `admin-passphrase-login`
  (the login's "Entrar", `button-primary`, through `AdminLoginDefaults`; the field reads none). The rule
  also reads test sources, so a test in a module without an entry cannot name an amber role either
  (`PastJamsDefaultsTest` proves the list "never amber" by allowed roles plus `BluesJamColorsTest`). A
  slice that adds an amber use (`slotOpen`, `activeFilter`, `published`, `primaryAction`) adds that
  role for its module in the same diff, so each amber use is a reviewed decision.
  Amber read **inside a `:core:ui` component** needs no allowlist entry: the instrument strip reads
  `slotOpen` in `com.bbbjam.core.ui.strip`, so `:feature:next-jam` stays at `{key}` and the rule
  guarantees the feature cannot draw open-slot amber outside the shared component. The lineup
  panel (`com.bbbjam.core.ui.lineup`) does the same.
- A musician screen reads a jam's songs through **`Jam.setlistForMusicians()`**
  (`unpublished-setlist-state`), never `jam.setlist`: it is `Withheld` for every DRAFT jam, whatever
  the read returned. `NextJamPresenter`, the song detail lookup, the Anteriores row summary and the past jam
  detail (`past-jam-detail`) do so. The admin slices read `jam.setlist` behind the admin flag.
  On Próxima jam a draft is `SetlistUiModel.Withheld(DraftSetlistUiModel)` (the draft card, colours
  in `DraftSetlistDefaults`, no amber) and an unreadable setlist is `SetlistUiModel.Unavailable`.
- "Today" in a screen → `JamCalendar.today()` (Buenos Aires), read once per snapshot as
  `remember(snapshot) { calendar.today() }` so a header agrees with the repository's upcoming/past
  split. Never `LocalDate.now()` or the device zone.
- Spanish day and month names → `SpanishDateNames.day(...)`/`.month(...)` in `:core:ui`
  (`com.bbbjam.core.ui.text`, moved there from `NextJamCopy` by `past-jams-list` because two
  features write dates), hand-written `when` tables, not `DateTimeFormatter` with a `Locale`: desugared java.time on API 24–25 and the JVM may render
  locale text differently, and the copy must be identical on the device and in tests. Times are
  padded with `padStart`, not `String.format` (locale-sensitive).
- A component used by two features → `:core:ui`. Never copy it between features.
  The instrument strip is the example (`instrument-strip-component`): `InstrumentStrip(chips)` draws
  only `InstrumentChipUiModel`s (no domain type in the composable), and the pure mapper
  `Lineup.toInstrumentChips(extras)` sits beside it so every feature maps a lineup identically; the
  presenter calls the mapper, the screen only draws. UI copy shared by two features lives with the
  component in `:core:ui` (`internal object InstrumentStripCopy`), not in a feature's `<Name>Copy`.
  The expanded lineup is the second example (`song-row-expansion`): `com.bbbjam.core.ui.lineup`
  holds `LineupPanel(model)`, the pure mapper `Lineup.toLineupPanel(extras)` (open slots first, then
  filled, then extras), `LineupPanelUiModel`/`LineupLineUiModel`, internal `LineupPanelCopy` and
  `LineupPanelDefaults` (line styles reuse `InstrumentStripDefaults.style`), and `ExpandIndicator`,
  the row chevron, which is here because features do not depend on the icons library.
- The instrument filter is the third example (`instrument-filter-chips`): `com.bbbjam.core.ui.filter`
  holds `InstrumentFilterBar(model)`, `InstrumentFilterBarUiModel`/`FilterChipUiModel`, the pure
  mapper `instrumentFilterBar(lineups, selected, onChange)`, the row predicate
  `Lineup.matchesInstrumentFilter(selected)` (OR over `hasOpenSlotFor`; extras never count), the
  change type `InstrumentFilterChange` (`Toggle(instrument)`, `Clear`) with
  `Set<Instrument>.updatedBy(change)`, internal `InstrumentFilterCopy` and
  `InstrumentFilterDefaults` (the only reader of `activeFilter`, so `:feature:next-jam` stays at
  `{key}`). Handlers carry the **change**, never a precomputed set: an unchanged chip compares equal
  to its previous model, Compose may keep its old handler, and a stale set would undo another
  chip's selection.
- The list states are the fourth example (`list-states`): `com.bbbjam.core.ui.state` holds
  `SkeletonList(description)`, `ListErrorBlock(model)`, `StalenessNotice(model)` and
  `EmptyStateBlock(model)`, their UiModels (`EmptyStateUiModel`, `ListErrorUiModel`,
  `StalenessNoticeUiModel`, each retry an `Event.Retry`), the pure mappers
  `listError(title, isOffline, onRetry)` and `stalenessNotice(isOffline, age, isRefreshing, onRetry)`,
  internal `ListStateCopy` (shared copy and ages) and `ListStateDefaults` (the only reader of
  `primaryAction`, for the error block's retry button, so `:feature:next-jam` stays at `{key}`).
  Feature-specific titles and the skeleton's description stay in the feature's `<Name>Copy`.
  **Retry pattern**: the presenter holds `var subscription by remember { mutableIntStateOf(0) }`
  and collects `remember(subscription) { repo.observe…() }`; `onRetry` increments it (re-subscribing
  recovers from a failed local read) and `scope.launch { repo.refresh() }` with
  `rememberCoroutineScope()`, ignoring the outcome, which comes back through `Freshness`. The counter
  is created once, so an earlier model's handler still works. The staleness notice is drawn only
  when `fetchedAt != null && lastFailure != null`, never on age alone.
- Pull to refresh is the sixth example (`live-refresh-during-jam`): `com.bbbjam.core.ui.state`
  holds `PullRefreshUiModel(isRefreshing, events)` (`Event.Refresh`, `IDLE` with a keyless handler),
  `RefreshableContent(model, modifier, content)` over Material 3's experimental `PullToRefreshBox`
  (the opt-in stays inside `:core:ui`), internal `PullRefreshDefaults` (`surfaceRaised` disc, `text`
  arc, no amber) and `PullRefreshCopy` (the `Actualizar` custom accessibility action). Every
  `NextJamUiModel` and `PastJamsUiModel` variant has `pullRefresh` (abstract on the sealed interface,
  defaulting to `IDLE`, so model-equality tests built without it still pass). The handler does what
  Retry does (re-subscribe, one `refresh()`), ignores a pull while its own is running, and only a
  pull spins the indicator. Content that is not a list scrolls (`verticalScroll`) so the gesture
  reaches the box.
- Keeping the staleness notice in view is the seventh example (`offline-notice-visible`):
  `com.bbbjam.core.ui.state` holds `shouldRevealNotice(staleness, previousStaleness,
  firstVisibleItemIndex, firstVisibleItemScrollOffset, firstVisibleItemIsTopAnchor)`, a pure
  function (true only on the notice's null → non-null edge while the list rests at index 0 or keeps
  its keyed top anchor at offset 0; unit-tested with no Compose UI harness), and
  `rememberRevealingLazyListState(staleness, topAnchorKey)`, a `@Composable` that builds one
  `LazyListState` plus the `remember`ed previous notice and a `LaunchedEffect(staleness)` calling
  `listState.animateScrollToItem(0)` when the function says so. `NextJamScreen` passes its keyed
  header as `topAnchorKey` because Compose may preserve it at index 1 after inserting the notice at
  index 0; `PastJamsScreen`'s title stays at index 0. Each calls the helper once per `LazyColumn`
  (their only `LazyListState`, replacing the implicit default) and passes the result as
  `LazyColumn(state = …)`; neither screen re-derives the rule. A user scrolled away from the top is
  never pulled back, and the notice disappearing never scrolls.
- The song detail is the fifth example (`song-detail-screen`): `com.bbbjam.core.ui.lineup` adds
  `InstrumentGroups(model)`, `InstrumentGroupsUiModel`/`InstrumentGroupUiModel` and the pure mapper
  `Lineup.toInstrumentGroups(extras)` (groups in first-appearance order, open before filled inside a
  group, extras never merged), reusing the panel's internal `Line` (now with a `showInstrument` flag
  and an ellipsis bounded by `LineupPanelDefaults.INSTRUMENT_MAX_WIDTH`). `com.bbbjam.core.ui.nav`
  holds `BackButton(model)`, `BackUiModel` (`Event.Back`), the mapper `backUiModel(onBack)` and
  internal `NavCopy`; `nav` names the back control, not the library, and nothing in `:core:ui`
  imports navigation. `bottom-navigation` adds the bar there: `TabBar(model)` (Material 3
  `NavigationBar`), `TabBarUiModel`/`TabUiModel` (`Event.Select`, navigation only), `enum TabIcon`
  (mapped to `material-icons-core` vectors inside `:core:ui`, I1) and internal `TabBarDefaults`
  (colors: `surface`, `text`, `surfaceRaised`, `textMuted`; no amber). `:app` owns the labels and
  routes (`AppTab`). `BluesJamTypography.keyDisplay` (96sp) is used only by the detail.
- **Navigation callbacks in presenter `Params`** (`song-detail-screen`): a screen takes plain
  callbacks (`onOpenSong(jamDate, position)`, `onBack`) and passes them to its presenter's `Params`
  (`NextJamPresenter.Params`, `SongDetailPresenter.Params`). The presenter reads each through
  `val current by rememberUpdatedState(params.callback)` and builds handlers that call `current`, so
  an earlier model's handler (Compose may keep it, handlers compare equal) still calls the current
  callback. `:app` binds the callbacks to `NavController` calls; routes are built and parsed only in
  `AppRoutes`. Features never import `androidx.navigation` (Konsist `navigation-only-in-app`).
- Per-row UI state of a list (which rows are expanded) → **one state object in the parent
  presenter**, keyed by a stable id, passed into the pure mapping; not a child presenter called in a
  loop. A `remember` inside a child presenter called in `map {}` is keyed by call order, so removing
  or inserting a row moves its state to a neighbour unless each call is wrapped in `key(id)`.
  `NextJamPresenter` holds `ExpandedRows(jamDate, songIds)` in
  `rememberSaveable(stateSaver = ExpandedRows.Saver)` (survives rotation; in Molecule it behaves as
  `remember`), keyed by the song id and scoped to the jam date (since
  `admin-remove-song-from-setlist`: a removal renumbers positions, and a position key would hand the
  removed song's expansion to the song that moves up). The lazy list keeps position keys, because a
  hand-edited tab may repeat a song id and lazy keys must be unique. **Never recreate such a
  state object** (`remember(snapshot) { … }`): handlers without a key compare equal, so Compose may
  keep an earlier model's handler, and it must still write through the same state. A child
  presenter pays off only when a row has its own dependencies (the admin slot mutations).
  The filter selection is held the same way, beside it: `Set<Instrument>` in
  `rememberSaveable(stateSaver = InstrumentFilterSaver)` (enum names), created once, never
  recreated, and **not** scoped to the jam date (it describes the musician). The presenter filters
  rows with `matchesInstrumentFilter` and builds the bar from every song, so the count line equals
  the rows drawn; a setlist with no song gets no bar.
- Wiring an implementation to its interface → the Koin module of the module that owns the
  implementation; `:app` only lists modules in `startKoin`.
- Anything that needs two features to talk → `:app` navigation or a `:core` contract.
- Opening a web link → `ExternalLinkOpener` from `:core:ui`, injected into the presenter and called
  from an event handler; `:app` binds `IntentLinkOpener`. Never `LocalUriHandler` or an `Intent`
  in a feature.

## Dependency Rules

- `:feature:a` → `:feature:b` is forbidden, in any direction and for any reason.
- `:core:*` never depends on `:feature:*` or `:app`.
- `:core:model` has no Android dependency.
- `:core:model` never reads the system clock: no `.now(`, `Clock.system…`,
  `System.currentTimeMillis(` or `System.nanoTime(` in its main or test sources. The caller passes
  today's date (`Jam.isHistorical(today)`) and owns the time zone.
- External music APIs (MusicBrainz, Deezer, Last.fm) are called only from background enrichment in
  `:core:data`, cached in Room, never from a presenter or during list rendering (D-09).
- Only `:core:data` imports or references `okhttp3.`, `androidx.room.`,
  `kotlinx.serialization.` or `androidx.datastore.`: Sheet I/O and stored state live only in
  repositories (D-13). A feature sees repository
  interfaces and domain types.

Konsist enforces the first four rules (the clock one as `core-model-no-system-clock`, a textual
match on each `:core:model` file), the data-library rule (`data-libraries-only-in-core-data` on imports, and
`data-libraries-only-in-core-data-qualified`, a text match on every non-import line outside
`:core:data` and `:konsist-test`, because kotlinx-serialization-core reaches a feature transitively
and a fully qualified reference there compiles; known limits: it is a line-based text match, so a
qualified name split across lines at a dot, or with a backticked segment, is not caught, and the
compile classpath still blocks okhttp3, Room, serialization-json and DataStore but not
serialization-core; `androidx.datastore.` joined both lists with `admin-passphrase-login`), the
package roots, the ViewModel ban and
`no-color-literal-outside-core-ui` (no numeric `Color(…)` and no ARGB hex literal in any module but
`:core:ui`; it does not catch `Color.Red`, `Color.parseColor`, named-argument `Color(red = …)` or XML
colors), and the two D-17 screen rules from `next-jam-read-only-list`:
`no-material-theme-outside-core-ui` (no `MaterialTheme.colorScheme`, `.typography` or `.shapes`
outside `:core:ui` and `:konsist-test`) and `amber-roles-allowlisted` (outside `:core:ui` and
`:konsist-test`, every `colors.<role>` or `BluesJamColors.<role>` read of an amber role —
`primaryAction`, `onPrimaryAction`, `slotOpen`, `key`, `published`, `onPublished`, `activeFilter`,
`onActiveFilter` — must be in `AMBER_ROLE_ALLOWLIST` for its module). Their known limits: text
matches, so an import alias of `MaterialTheme`, `with(MaterialTheme) { … }` or an alias of the
colors object (`val c = BluesJamTheme.colors; c.key`) escapes; a Material component left on its
default colors (amber `primary`) escapes; and the rule cannot judge whether an allowed role is
drawn on the right element. `no-dp-literal-outside-core-ui` (`list-states`, K1) is the same
shape for sizes (it also reads test sources, so a test compares `fontSize.value`, not `96.sp`): outside `:core:ui` and `:konsist-test`, no `<number>.dp`, `.sp`, `.em` (also
`0.5f.dp`) and no `Dp(<number>`; known limits: a text match, so `n.dp` on a variable or a
constant escapes, and literals inside `:core:ui` components are not checked. All of them live in
`konsist-test/src/test/kotlin/com/bbbjam/konsist/ModuleIsolationTest.kt`, which runs inside
`./gradlew check` (so inside `./init.sh`). It checks imports and also every `project(":…")` in
`core/*` and `feature/*` build files, and reads module groups from paths, so a new `:feature:*` is
covered without editing it. It holds 17 rules (the 12th, `data-libraries-only-in-core-data`, came
with `catalog-repository-cache`; the 13th, `data-libraries-only-in-core-data-qualified`, with
`jams-repository-cache`; the 14th and 15th, `no-material-theme-outside-core-ui` and
`amber-roles-allowlisted`, with `next-jam-read-only-list`; the 16th,
`no-dp-literal-outside-core-ui`, with `list-states`; the 17th, `navigation-only-in-app` (outside
`:app` and `:konsist-test`, no import of `androidx.navigation.`; an import text match, a fully
qualified use escapes but would not compile, because only `:app` declares the library), with
`song-detail-screen`); the two that read build files are
`build-file-project-deps` and `build-file-applies-convention` (see Build Conventions). **A new
dependency rule means a new test in that class**, proven able
to fail before it is trusted.

## Build Conventions

Set by `module-skeleton` (spec `docs/specs/module-skeleton.md`); moved into convention plugins by
`build-logic-conventions` (spec `docs/specs/build-logic-conventions.md`). Every new module follows
them.

- **Convention plugins.** `build-logic/` is an included build (`includeBuild("build-logic")` first
  in `pluginManagement`, not `buildSrc`) with one subproject, `:convention` (`kotlin-dsl`, binary
  plugins in `com.bbbjam.buildlogic`). It reads the root catalog from `../gradle/libs.versions.toml`
  and compiles against AGP and KGP as `compileOnly` (catalog `android-gradlePlugin`,
  `kotlin-gradlePlugin`). A module build file applies only `id("bluesjam.…")` plugins and keeps what
  is its own: `namespace`, `:app`'s identity and build types, project dependencies and
  module-specific libraries.

  | Plugin | Applies / sets | Used by |
  |---|---|---|
  | `bluesjam.jvm.library` | `org.jetbrains.kotlin.jvm`; `java {}` source/target 11; `jvmTarget = JVM_11` | `:core:model`, `:konsist-test` |
  | `bluesjam.android.library` | `com.android.library`; common Android setup (SDKs, Java 11, core library desugaring) | through `.data`, `.presenter` |
  | `bluesjam.android.application` | `com.android.application`; common Android setup; `targetSdk` from the catalog; `buildFeatures.buildConfig = true` | `:app` |
  | `bluesjam.android.compose` | requires an Android plugin first; Compose compiler plugin; `buildFeatures.compose = true` | `:app` (and through the two below) |
  | `bluesjam.android.presenter` | `.android.library` + `.android.compose`; `unitTests.isReturnDefaultValues = true`; `testImplementation` junit, molecule-runtime, turbine, kotlinx-coroutines-test | `:core:ui` |
  | `bluesjam.android.feature` | `.android.presenter`; `implementation` Koin BOM, `koin-core`, `koin-compose` | every `:feature:*` |
  | `bluesjam.android.data` | `.android.library` + `org.jetbrains.kotlin.plugin.serialization` + `com.google.devtools.ksp`; `unitTests.isReturnDefaultValues = true`; `testImplementation` junit, kotlinx-coroutines-test, turbine | `:core:data` |

  A convention adds a dependency only when every module of its kind needs it. Optional or
  module-specific libraries (Compose BOM, foundation, tooling, `koin-android`, junit in JVM modules
  and `:app`) stay in the module file. **Project dependencies never go into a convention**:
  `build-file-project-deps` reads them from module files (D-03). The Konsist rule
  `build-file-applies-convention` fails a module that applies no `bluesjam.*` plugin, an `:app`
  without `bluesjam.android.application`, a `:feature:*` without `bluesjam.android.feature`, a raw
  plugin (`alias(libs.plugins…)`, another `id`, `kotlin("…")`, `apply(plugin…)`), a
  convention-owned setting (`compileSdk`, `minSdk`, `targetSdk`, `JavaVersion`, `JvmTarget`,
  `jvmTarget`, `jvmToolchain`, `sourceCompatibility`, `targetCompatibility`, `buildFeatures`,
  `isReturnDefaultValues`) outside a `//` comment, or a missing build file. Editing a convention
  recompiles `build-logic` and misses the configuration cache once (about 18 s of `init.sh`
  instead of 7).
- **Plugin per module kind.** A pure Kotlin module applies `bluesjam.jvm.library`. An Android
  library applies `bluesjam.android.library` (or `.presenter`/`.feature`, which include it) and
  relies on AGP 9's built-in Kotlin. Never apply `org.jetbrains.kotlin.android`, and never
  `com.android.kotlin.multiplatform.library`: the project is Android-only.
- **Every plugin is declared at the root** `build.gradle.kts` with `apply false`, from the catalog
  with `alias(...)`. The conventions compile against AGP and KGP `compileOnly` and apply plugins by
  id, so at runtime they use the copies the root puts on the classpath: without `kotlin.compose`
  there, configuration fails ("An exception occurred applying plugin request [id:
  'bluesjam.android.compose']"). It also keeps one AGP and one KGP in `buildEnvironment`.
- **SDK levels come from the catalog**, set by the conventions: `compileSdk` and `minSdk` in every
  Android module (`configureAndroidCommon` in `ProjectExtensions.kt`), `targetSdk` in `:app` only
  through `bluesjam.android.application`. Change a level in `[versions]`, never in a module.
  `configureAndroidCommon` also turns on core library desugaring (below).
- **Java 11 everywhere**, set by the conventions: `compileOptions` `VERSION_11` in Android modules;
  `java {}` `VERSION_11` plus `jvmTarget = JVM_11` in JVM modules. No `jvmToolchain(11)`, which
  would make foojay download a JDK 11 for nothing. `build-logic` itself targets the daemon JVM
  (pinned by `gradle/gradle-daemon-jvm.properties`); do not add a toolchain there either.
- **New modules keep sources in `src/main/kotlin`** (and `src/test/kotlin`). `:app` keeps
  `src/main/java`. Libraries need no `AndroidManifest.xml`; the namespace comes from the DSL,
  `com.bbbjam.<path>` (for example `com.bbbjam.core.ui`).
- **Package root is `com.bbbjam.<module path>` with hyphens removed** (`:feature:next-jam` →
  `com.bbbjam.feature.nextjam`), and every source file in `:core:*` and `:feature:*` declares a package
  equal to or under it. Enforced by the `package-under-module-root` Konsist rule.
- **Dependencies**: `:core:ui` and `:core:data` use `api(project(":core:model"))`, because their
  public contracts expose domain types. `:app` lists each module it uses with
  `implementation(project(...))`. Use `project(":…")`, not type-safe project accessors.
- **Each module has its own `.gitignore`** with `/build`; the root one only ignores the root build.
- **Quality tools come from the root build file** (set by `detekt-ktlint-gate`, spec
  `docs/specs/detekt-ktlint-gate.md`). The root `build.gradle.kts` applies detekt and ktlint to
  every module that applies `kotlin-jvm` or an Android plugin, so a new module needs no tool
  configuration and gets both in its `check`. Configuration lives in `.editorconfig` (ktlint,
  `android_studio` style, Composable naming exception) and `config/detekt/detekt.yml` (overrides on
  top of detekt's defaults). `./gradlew ktlintFormat` fixes formatting. Never add a baseline file,
  `ignoreFailures`, or a blanket rule disable without the user's approval. The tools stay
  root-applied after `build-logic-conventions`, not in a convention. That root block is the build's
  only Isolated Projects blocker; moving the tools into a `bluesjam.quality` convention applied by
  the three base conventions was proven to work (spec Decision 3) and is left to its own slice.
  Known gap: the Kotlin in `build-logic` is outside the root build, so detekt and ktlint do not
  lint it.
- **A module with presenters** (set by `molecule-presenter-harness`, spec
  `docs/specs/molecule-presenter-harness.md`) applies `bluesjam.android.presenter` (a feature applies
  `bluesjam.android.feature`, which includes it). The convention applies the Compose compiler with
  `buildFeatures.compose = true`, adds `testImplementation` junit, molecule-runtime, turbine,
  kotlinx-coroutines-test, and sets `testOptions.unitTests.isReturnDefaultValues = true`; the
  Compose runtime arrives through `:core:ui` (which exposes the BOM and
  `androidx.compose.runtime:runtime` as `api`). Without the flag every Molecule test fails on
  `android.os.Trace` "not mocked", because the Android Compose runtime calls it.
- **`:core:ui` is the design system** (set by `design-tokens-theme`, spec
  `docs/specs/design-tokens-theme.md`). Besides the runtime it exposes `androidx.compose.ui:ui` and
  `androidx.compose.material3:material3` as `api` (BOM-managed, material3 1.3.2), so a module that
  depends on it gets the token types and the themed components with no dependency line of its own.
  `androidx.compose.material:material-icons-core` is declared explicitly as `implementation` in
  `:core:ui` (catalog `androidx-compose-material-icons-core`, BOM-managed) for `Icons.Filled.Check`:
  material3 1.3.2 pulls it transitively, but later material3 versions drop it, so relying on the
  transitive copy would break an upgrade. A feature that needs an icon gets it from a `:core:ui`
  component, or declares the dependency itself.
  Fonts are bundled static TTFs in `core/ui/src/main/res/font`, with their SIL OFL 1.1 texts in
  `core/ui/src/main/assets/licenses`; no downloadable fonts, because the app is used offline. The
  only XML color is `bluesjam_window_background` in `core/ui/src/main/res/values/colors.xml`, used by
  the `:app` window theme and guarded by `WindowBackgroundTest` against drift from the token.
- **java.time and desugaring** (set by `domain-model-types`, spec
  `docs/specs/domain-model-types.md`; moved into the conventions by `catalog-repository-cache`).
  `minSdk` is 24 and java.time arrives at API 26. `configureAndroidCommon` sets
  `isCoreLibraryDesugaringEnabled = true` and adds `coreLibraryDesugaring` desugar_jdk_libs 2.1.5
  (catalog `desugar-jdk-libs`) in **every** Android module, `:app` included: lint `NewApi` fails a
  library that uses java.time without it even when `:app` has it, and a library that enables it
  without `:app` fails `:app:checkDebugAarMetadata`. Proven: with it off, `:core:data:lintDebug`
  fails `NewApi` on `java.time.Clock#systemUTC` and 13 more calls. `:core:model` (JVM, no Android)
  still never parses a date or reads the clock.
- **`:core:data` layout** (set by `catalog-repository-cache`, spec
  `docs/specs/catalog-repository-cache.md`). Package `com.bbbjam.core.data`: public
  `AppsScriptEndpoint`, `DataFailure`, `Freshness`; `catalog/` (public `CatalogRepository`,
  `CatalogSnapshot`, `RefreshOutcome`, `RejectedSong`, `DroppedFields`, `SongIssue`; internal
  `CatalogMapper`, `DefaultCatalogRepository`); `remote/` (internal `AppsScriptTransport`,
  `OkHttpAppsScriptTransport`, `AppsScriptEnvelope`, DTOs); `cache/` (internal Room database
  `bluesjam-cache.db`, entities, DAO; version 1, no exported schema, destructive fallback, because
  every table is a re-fetchable cache); `di/dataModule` (every binding `single`; it binds the one
  `java.time.Clock`, `Clock.systemUTC()`, and a process-wide `DataScope`). `dataModule` needs
  `Context` (`androidContext()`) and `AppsScriptEndpoint` from `:app`, so `:core:data` uses
  `koin-core` only. Repositories are cache-first: a Flow from Room plus a single-flight
  `refresh()`; a failure never touches the cache.
- **`:core:data` jams** (set by `jams-repository-cache`, spec
  `docs/specs/jams-repository-cache.md`). `jams/`: public `JamsRepository` (`observeJams()`,
  `refresh()`), `JamsSnapshot` (`upcoming`, `past`, `freshness`; the split is computed on each
  emission, at most one upcoming, past newest first with only filled slots), `JamCalendar`
  (`today()`/`now()` from the bound `Clock` in `JamCalendar.BUENOS_AIRES`; the only source of
  "today" for jams, which presenters reuse; never `LocalDate.now()`), `JamsRefreshOutcome` +
  `toLogLine()`, `JamIssue`, `RejectedJam`, `SetlistIssue`, `SetlistRowIssue`; internal
  `JamsMapper`, `SetlistMapper`, `DefaultJamsRepository`. `remote/JamDto.kt` holds the jams DTOs.
  Room database **version 2** (destructive from 1): tables `jam`, `jam_song` (PK `jam_date`,
  `position`), `jam_slot` (PK + `column_index` 0..6, the slot's Sheet identity) and `jam_extra`, with
  `ForeignKey` CASCADE from `jam`; the view `jam_song_resolved` resolves titles against
  `catalog_song` at read time, so a catalog replace re-emits the jams; `JamsDao.observeJams()` is a
  `@Transaction` query with three `@Relation`s. `cache/SyncStates.kt` holds the helpers both
  repositories share (`toFreshness`, `isRefreshDue`, `recordedFailure`, `toStorageFailure`,
  `mappingFailure`).
- **Refresh robustness** (`jams-repository-cache`, both repositories). A write the cache refuses is
  caught as `android.database.SQLException` (a full disk is `SQLiteFullException`) and becomes
  `DataFailure.Storage(<exception class>)`; a mapper exception is caught as
  `IllegalArgumentException` and becomes `InvalidResponse("mapping: …")`. **Never catch
  `Exception`, `RuntimeException`, `IllegalStateException` or `Throwable`**: coroutine cancellation
  is an `IllegalStateException`. As a last resort `DataScope.create` (bound in `dataModule`) and
  `BluesJamApp`'s scope carry a `CoroutineExceptionHandler` that logs instead of crashing.
  A Room **read** exception: `JamsRepository.observeJams()` catches `android.database.SQLException`
  only (`Flow.catch`, `list-states`), emits one snapshot with no jams and
  `Freshness(null, Storage(<class>), isRefreshing = false)`, and completes; collecting it again
  retries. Anything else is rethrown. The catalog read Flow still throws on a Room read exception
  (no screen reads it yet).
- **The Apps Script URL** never enters a tracked file. `app/build.gradle.kts` reads
  `bluesjam.appsScriptUrl` from the git-ignored `local.properties` into
  `BuildConfig.APPS_SCRIPT_URL` (`""` when missing), and `appModule` binds
  `AppsScriptEndpoint.of(BuildConfig.APPS_SCRIPT_URL)`. Never log it (`AppsScriptEndpoint` hides
  it in `toString`).
- **Data stack pins** (`catalog-repository-cache`): KSP 2.3.12, Room 2.8.4, OkHttp 5.1.0,
  kotlinx-serialization 1.9.0, desugar_jdk_libs 2.1.5; test only mockwebserver3 5.1.0,
  sqlite-bundled-jvm 2.6.2, koin-test (BOM). Held so kotlin-stdlib stays at the 2.2.10 compiler on
  `:app`'s runtime classpath: OkHttp 5.2+ needs stdlib 2.2.20+, serialization 1.10 needs 2.3.0
  (the Koin 4.2 trap again). KSP 2.2.10-2.0.2 fails under built-in Kotlin ("Using
  kotlin.sourceSets DSL to add Kotlin sources is not allowed"). Upgrade them with Kotlin.
- **Navigation pin** (`song-detail-screen`, N1): Navigation Compose 2.9.8 (catalog
  `androidx-navigation-compose`), declared in `app/build.gradle.kts` only, never in a convention or
  another module. The last 2.9.x; it resolves with no version moving up (stdlib 2.2.10, Compose
  1.9.1, activity-compose 1.11.0, serialization 1.9.0, lifecycle 2.9.3), adding only
  `androidx.navigation` and `lifecycle-viewmodel-compose` 2.9.3. 2.10.x raises Compose to 1.10 and
  activity-compose to 1.13 (the Koin 4.2 trap again). It brings ViewModel artifacts transitively;
  that does not relax D-02 (no `viewModel()`, no `hiltViewModel`). Routes are strings (R1): typed
  routes would need the serialization plugin in `:app` and break the data-library rules. Upgrade it
  with Kotlin, the BOM and Koin.
- **DataStore pin** (`admin-passphrase-login`, A2): `androidx.datastore:datastore-preferences`
  1.2.1 (catalog `androidx-datastore-preferences`), `implementation` in `core/data/build.gradle.kts`
  only. It asks for stdlib 2.0.21, coroutines 1.9.0 and okio 3.9.1, all at or below what resolves;
  the debug and release runtime classpaths of `:app` only gained the twelve `androidx.datastore`
  artifacts (stdlib 2.2.10, coroutines 1.9.0, okio 3.15.0, Compose 1.9.1, lifecycle 2.9.3 unmoved).
  `AdminCredentialStore.dataStore(...)` builds it over `OkioStorage` with `PreferencesSerializer`
  (same file and format as the default), not the `File` storage: below API 26, the JVM tests
  included, that one replaces with `File.renameTo`, which fails on Windows when the file exists.
  Tests use the same factory over a temp file and cancel its scope to simulate a restart.
  The file is excluded from backup and device transfer in `:app`'s `res/xml` rules.
- **Debug demo jam** (`debug-demo-upcoming-jam`): device checks that need an upcoming jam use a
  demo jam built in code, never temporary Sheet data. `bluesjam.demoUpcomingJam=true` in the
  git-ignored `local.properties` becomes `BuildConfig.DEMO_UPCOMING_JAM` (debug only; release is
  hard `false`). The code lives only in `app/src/debug/java/com/bbbjam/debug/`
  (`DemoUpcomingJam` fixture, `DemoUpcomingJamRepository` decorator, `Koin.debugOverrides()`,
  `Jam.isDemo()`); `app/src/release/` has the same two functions as no-ops, so a release build
  cannot ship it. `BluesJamApp` loads `koin.debugOverrides()` with `allowOverride = true` after
  `startKoin`. The decorator fills only a null `upcoming` (a real jam always wins) and never
  touches `past`, `freshness`, Room or the Sheet; the startup log marks it `upcoming <date> (demo)`.
  Tests in `app/src/testDebug/`. With a real upcoming jam in the Sheet, see the demo by clearing
  the app's data in airplane mode. `bluesjam.demoUpcomingJamDraft=true` (`unpublished-setlist-state`,
  `BuildConfig.DEMO_UPCOMING_JAM_DRAFT`, release hard `false`, effective only with the demo flag)
  makes the demo a DRAFT with the same songs (`DemoUpcomingJam.on(today, draft)`,
  `DemoUpcomingJamRepository(real, calendar, draft)`); the startup line appends ` draft` after any
  DRAFT upcoming date. Leave it off or absent. `bluesjam.demoUpcomingJamLive=true` (`live-refresh-during-jam`,
  `BuildConfig.DEMO_UPCOMING_JAM_LIVE`, release hard `false`, works on its own) installs the same
  decorator in live mode: the demo jam **replaces** the upcoming jam whatever the cache holds,
  dated today with its start 10 minutes after the process started (Buenos Aires, truncated to the
  minute, computed once), so the live window is open, and every `refresh()` logs `demo jams
  refresh: <Updated|Failed> at <instant>` (tag `BluesJam`). Leave it off or absent.
- **Debug admin session** (`debug-admin-session`): device checks of admin controls never need the
  real passphrase. `bluesjam.debugAdmin=true` becomes `BuildConfig.DEBUG_ADMIN` (debug only;
  release hard `false`). `DebugAdminSession(real: AdminSession)` in `app/src/debug/` emits true
  while a forced state is on (starts on), else the real value; `logOut()` ends the forced state and
  calls the real one; `logIn` delegates. It never stores, sends or knows the passphrase and
  authorizes nothing. **The overrides are independent**: `Koin.debugOverrides()` delegates to
  `debugOverrides(DebugFlags)`, which adds one module per flag on (demo jam, admin), so a test turns
  each flag on alone (`DebugAdminSessionTest`). The release `debugAdminLogSuffix()` returns `""`; in
  debug the startup `jams cache` line ends with ` admin (debug)` when the flag is on.
- **No Compose UI test harness** (`song-row-expansion`, T1 declined by the user on 3 October 2026):
  no Robolectric, `ui-test-junit4` or `ui-test-manifest`. Put everything a test must check into the
  `UiModel` (copy, state descriptions, order, kinds) and test it on the JVM; keep the visual rules
  in a `…Defaults` object a JVM test can read. Drawn semantics (content descriptions, clickability,
  merged nodes) are checked on the device with `uiautomator dump`. The gap is recorded in
  `docs/risks-and-open-questions.md`.
- **Room tests run on the JVM**: `Room.inMemoryDatabaseBuilder(ContextWrapper(null), …)
  .setDriver(BundledSQLiteDriver())`, no Robolectric. The stub `android.database.SQLException`
  loses its message, so avoid Room features that parse it (`@Upsert`; use
  `@Insert(onConflict = REPLACE)`). A module test task that reads repository files declares them
  as inputs (`core/data/build.gradle.kts` does so for `docs/api-samples` and `docs/sheet-seed`).
- **Reference feature build file**: `feature/info/build.gradle.kts` (set by `info-screen`, reshaped
  by `build-logic-conventions`): `id("bluesjam.android.feature")`, `android { namespace =
  "com.bbbjam.feature.<name>" }`, its only project dependency `implementation(project(":core:ui"))`
  (plus `implementation(project(":core:data"))` once a feature reads data, as
  `feature/next-jam/build.gradle.kts` does), `androidx.compose.foundation` and tooling-preview
  (+ debug tooling). SDKs, Java 11, Compose, the test flag, Koin and the presenter test libraries
  come from the convention.
- **Koin 4.1.1** (set by `info-screen`, approved 30 September 2026; D-16). Catalog: `koin-bom`
  (versioned) and `koin-core`, `koin-android`, `koin-compose` (BOM-managed). Feature modules use
  `koin-core` + `koin-compose` (for `koinInject()`); `:app` uses `koin-android` (for
  `androidContext`). Not 4.2.x: it raises `kotlin-stdlib` to 2.3.20 and Compose to 1.10.x over the
  pinned Kotlin 2.2.10 and BOM 2025.09.00; upgrade Koin together with Kotlin and the BOM. 4.1.1
  only moves lifecycle 2.9.0 → 2.9.3. `koin-android` brings `koin-core-viewmodel` and the lifecycle
  ViewModel artifacts transitively; that does not relax the ViewModel ban (D-02).
- **UI copy lives in Kotlin, not string resources** (set by `info-screen`, Decision 6): an
  `internal object <Name>Copy` in the feature, read by the presenter into the `UiModel`. The app is
  Spanish-only (D-12), the copy must be in the `UiModel` for the assistant, and presenters stay
  Android-free and JVM-testable. `app_name` stays a resource.
- **48dp touch targets without a literal**: clickable rows use
  `Modifier.heightIn(min = LocalMinimumInteractiveComponentSize.current)` (Material 3, 48dp), with
  `Role.Button` and an `onClickLabel` when the row's action is not obvious from its text.

## Presenter Pattern

Read `references/presenter-pattern.md` before writing a presenter. It holds the contracts, the
module setup, a compiled sample test, and an illustrative next-jam example with a child presenter
and admin state, the screen, the Koin wiring, and Molecule tests. The contracts are real code in
`:core:ui`, package `com.bbbjam.core.ui.presenter`; the compiled example is `SamplePresenter` with
`SamplePresenterTest` in `core/ui/src/test/kotlin/com/bbbjam/core/ui/presenter/`. In short:

- A presenter is a class implementing `Presenter<Model, Params>` with a `@Composable present(params)`
  that returns an immutable `UiModel`. Dependencies arrive through the constructor via Koin; runtime
  inputs arrive as `Params`. State lives in the Compose runtime.
- A `UiModel` holds display values and `EventHandler<Event>`s, with events as a sealed
  `Event : UiEvent`. No repositories, no Android types, no raw lambdas. In phase 2 it is exposed as
  assistant context as-is.
- Parent presenters compose child presenters by calling `child.present(params)`.
- Tests use Molecule and Turbine on the JVM with fake repositories, and assert at least one state
  transition, not only the first emission.
- `EventHandler.equals` and `hashCode` both derive from `key`, and `invoke` is declared. The source
  articles get both wrong (bitácora 3.5).

## Dependency Injection — Koin

- Presenters are `factory`: they hold no state; the composition does. Repositories are `single`.
- Screens get their presenter with `koinInject()`, as a default parameter so previews and tests can
  pass one in.
- Each module exposes its own Koin `module { }`. Only `:app` calls `startKoin`, and a feature never
  references another feature's Koin module.
- No Koin in `:core:model`, and no `KoinComponent`/`inject()` inside presenters or repositories:
  constructor injection only, so tests build them by hand.

## Minimal Feature Template

```
feature/<name>/
  build.gradle.kts                     # id("bluesjam.android.feature"); depends on :core:* only
  src/main/.../<Name>Presenter.kt      # class <Name>Presenter : Presenter<<Name>UiModel, Params>
  src/main/.../<Name>UiModel.kt        # sealed or data class : UiModel, with sealed Event
  src/main/.../<Name>Screen.kt         # renders the UiModel, forwards events, holds no logic
  src/main/.../di/<Name>Module.kt      # val <name>Module = module { factory { … } }
  src/test/.../<Name>PresenterTest.kt  # Molecule + Turbine, fakes, JVM
```

Then include the module in `settings.gradle.kts` and add its Koin module to `startKoin` in `:app`.

## Anti-Patterns

| Do not | Instead |
|---|---|
| Add a `ViewModel` or `AndroidViewModel` | A composable presenter (D-02) |
| Create `:feature:admin` or a separate admin screen for setlist edits | Admin state inside the existing presenter (D-15) |
| Call `get()`/`inject()` inside a presenter or repository | Constructor injection, wired in a Koin module (D-16) |
| Put logic or `if (isAdmin)` decisions in a composable | The presenter decides; the screen draws `canEdit` |
| Import one feature module from another | Move the contract to `:core:*`, wire in `:app` (D-03) |
| Perform a write from a composable click handler | Call a `:core:data` repository function (D-13) |
| Sync a Sheet-owned entity back to the Sheet | The catalog and past jams are read-only in the app (D-04) |
| Fill `key` from an API or from `Song.defaultKey` silently | The admin sets `JamSong.key` (D-08) |
| Treat a missing enrichment field as an error | Every enrichment field is optional (D-09) |
| Use the local admin flag as authorization | Apps Script validates every write (D-11) |
| Add a `Musician` entity or an `ARCHIVED` status | A musician is a name on a `Slot`; archived is derived from the date |
| Hard-code colors, spacing or copy in `tú` | `:core:ui` tokens; Rioplatense Spanish with *vos* (D-12) |

## Still Open

- Navigation library: **resolved** by `song-detail-screen` (N1, 4 October 2026): Navigation Compose
  2.9.8 in `:app` only, string routes in `AppRoutes` (R1). Tabs **resolved** by `bottom-navigation`
  (5 October 2026): two hosts, per-tab routes with `saveState`/`restoreState`, the song detail
  above the tabs, M1 motion; `TemporaryTabs` deleted. Per-tab state survived rotation and process
  death on the device.
- Deeplink scheme: still open, **deferred to `action-contract-registry`** (D1, 5 October 2026). No
  destination declares `deepLinks`, no manifest intent filter; D-13 is met by repository functions
  in the action registry.


As built locally by `admin-adjust-lineup` (live deployment pending): `Lineup.defaultCount`, `count`
and `withSlotCount` mirror last-open-first removal and canonical restoration, never remove filled
slots or exceed defaults. `SetlistRepository.setSlotCount`/`observeLineupChanges` use public
`SetSlotCountOutcome` and `LineupChange` (`Sending`/`Failed`); `SetlistLineupChanges` is built by
`DefaultSetlistRepository`, sharing its ids/order/writes/scope, no Koin binding. Confirmed seven
slot fields replace only one available cached song's slots through `SetlistDao.replaceSlots`,
before clearing the entry. `LineupChangeOverlay` applies latest Sending per instrument before
admin filtering/counts/strip/panel; Failed is the revert. Row actions: detail, key, lineup editor,
removal last. `SongRowAdminUiModel.saveStatus` unifies key and lineup saving. Editor owns one
saveable row key; retained handlers read latest counts and synchronously reserve targets so two
pre-recomposition taps queue distinct counts. `Instrument.fullName` exports the shared name copy.
Server `setSlotCount` is router-guarded, reuses `findSongRow_`, and writes only changed plain-text
slot cells; future ordinal mutations reuse `presentSlotFields_` and refuse unexpected cell state.

As built locally by `admin-assign-musician` (deployment/live checks deferred): `SlotPosition` is a
1-based instrument ordinal in original lineup order and `MusicianName.parseOrNull` is the write-time
normalizer/validator. `SetlistRepository.assignSlot` and `observeAssignments` use public
`Assignment`/`AssignSlotOutcome` types and share `DefaultSetlistRepository`'s write ordering, ids and
`DataScope`; a confirmed `{schemaVersion,column,name}` response is required before its guarded
single-slot DAO update. Sending assignments overlay only the admin's matching open slot before
filter/count/strip/panel derivation; failed assignments revert and become dismissible cards.
`LineupPanelUiModel` keeps optional slot action metadata absent by default, and the expanded Próxima
jam panel supplies actions only in admin mode. `AssignMusicianPresenter` owns form validation and
suggestions from cached upcoming/past jams; `AssignMusicianScreen` is a full-screen flow in the
existing feature. `AppRoutes.ASSIGN_SLOT` carries ISO date, `SongId`, instrument and positive
ordinal in the outer host. No new module or Koin binding is introduced.

As built locally by `admin-clear-slot` (deployment/live checks deferred):
`SetlistRepository.clearSlot` is a public compare-and-clear mutation exposed through
`SetlistSlotClearRepository`, with `ClearSlotOutcome` and `SlotClear` (`Sending`/`Failed`). It shares
the repository's mutation ids, FIFO order/write barriers and `DataScope`. The client captures the
expected cached display name as text so legacy names do not pass through new-assignment validation;
the server re-resolves U1 and compare-checks that exact cell. A valid seven-field response is mirrored
transactionally to the unique cached song. Sending clears overlay only the admin lineup before
strip/panel/filter/count mapping; failures leave Room unchanged and produce dismissible cards.
`LineupPanelUiModel`'s filled-line action stays absent by default and is supplied only on PrÃ³xima jam
for eligible admin slots; ordinal metadata follows the source lineup through the stable partition.

As built locally by `admin-reorder-songs` (deployment/live checks deferred): `SetlistRepository.moveSong`
uses public `SetlistMove`/`MoveSongOutcome` types and the same mutation id, order and write queues.
`SetlistDao.moveSetlistSong` rekeys only changed songs and their child slots/extras in one
transaction after confirmation. Sending entries overlay the admin order by id and call order; failed
entries never overlay. Song rows use stable song ids (position fallback for duplicate ids), retain the
pre-overlay position for detail navigation, and anchor the moved row in the viewport. Move actions
are explicit 48dp `Subir`/`Bajar` buttons and remain absent for musicians.

As built locally by `admin-publish-setlist` (deployment/live checks deferred): Apps Script's `publishSetlist` validates and writes/read-backs only the upcoming Jams `estado` cell; `checkPublish` uses a self-cleaning temporary Jams tab. `SetlistRepository.publishSetlist` returns `PublishOutcome`, shares the `DefaultSetlistRepository` mutation id/order/write barriers and `DataScope`, and exposes `SetlistPublish` Sending/Failed state. `SetlistDao.markPublished` changes only a cached DRAFT/AVAILABLE jam after confirmation. No optimistic status is projected. `WriteFailureLog` records a safe local line. In `:feature:next-jam`, `AdminStatusUiModel` keeps the confirmed badge, `PublishUiModel` holds saveable inline confirmation and failure state, and the persistent assertive card lives in the under-header status block. Only the approved `primaryAction`, `onPrimaryAction`, `published` and `onPublished` amber roles are added to its Konsist allowlist.
