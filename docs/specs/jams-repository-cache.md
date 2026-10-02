# Feature Implementation Spec: Read jams and setlists into the cache

## Source Feature

- `id`: jams-repository-cache · `area`: data · `source`: `feature_list.json`
- `depends_on`: catalog-repository-cache, apps-script-jams-read-endpoint (both `accepted`)
- `status`: not_started (planned 2 October 2026, HEAD `655c670`)

**The user must approve D1 (a domain-model change), P1–P8 (product rules) and, optionally, G1 (a
gate change) before work starts** (see "Needs the user's approval"). No new dependency is needed.

## Goal

`:core:data` reads `GET <url>?resource=jams`, maps every jam row and its setlist under the jam
Mapper rules of `docs/sheet-schema.md`, stores them in Room next to the catalog, and exposes a
cache-first `JamsRepository` that serves the upcoming jam and the past jams, offline included,
with `Freshness`. A jam whose setlist is withheld (draft) or broken (`setlistError`, bad rows) is
a jam with that state, never an empty list. Refreshes of **both** resources stop crashing the
process on a storage or mapping exception: those become failure outcomes.

## Non-Goals

- Any screen, presenter or Spanish copy (next-jam, past-jams, past-jam-detail, list-states).
- Writes, the admin read of a draft, the passphrase (`apps-script-write-auth`).
- Changes to the Apps Script or to the contract (`docs/apps-script-api.md` stays as is).
- Handling a Room exception on the **read** Flows (see Risks); only refresh paths are in scope.
- Re-splitting upcoming/past at midnight while a collector is open (no ticker; see Risks).

## Job Story

When I open the app in the bar with no signal, I want the jam and its songs I last loaded to still
be there, and a jam whose list is not ready to say so, so I never mistake "not published" or
"broken" for "no songs".

## Acceptance Scenarios

1. **Online.** Given an empty cache and `jams-seed.json`, when `refresh()` runs with today
   2026-07-20, then `observeJams()` emits `upcoming` = the 2026-07-25 jam, `Setlist.Available` with
   13 songs in position order, seven open slots each, and `fetchedAt` = the clock's instant.
2. **Offline.** Given a cached jam, when the transport fails (`IOException`), the same jams are
   emitted with the old `fetchedAt`, `lastFailure = Offline` and `isStale(now)` true.
3. **Withheld and broken.** `jams-edge.json` with today 2026-10-02 gives five jams, all past: 2026-09-26
   `DRAFT`/`Withheld`; 2026-05-30 `Unavailable(MISSING_TAB)`; 2026-04-25
   `Unavailable(INVALID_TAB)`; 2026-08-29 and 2026-02-28 `Available`; four rows rejected
   (`Publicada`, both 2026-03-28 rows, `Config`).
4. **One upcoming.** The same sample with today 2026-08-01: `upcoming` = 2026-08-29; 2026-09-26 is
   held back (in neither list, reported in the outcome); `past` is newest first.
5. **Storage failure.** A DAO that throws `SQLiteFullException` on replace gives
   `Failed(Storage("SQLiteFullException"))` for jams **and** catalog; the background refresh
   started by a collector does not crash; the cache is untouched.
6. **Catalog resolution.** A setlist row whose `songId` is in the cached catalog shows the
   catalog's title/artist; one that is not shows the tab copy; replacing the catalog re-emits the
   jams with the new titles without a jams refresh.

## Repository Research

### Files Inspected

`AGENTS.md`, `PROGRESS.md`, `feature_list.json` (this entry and the downstream
next-jam/past-jams/past-jam-detail/list-states/unpublished-setlist-state/admin entries),
`CONTEXT.md`, `docs/domain-model.md`, `docs/sheet-schema.md`, `docs/apps-script-api.md`,
`docs/api-samples/jams-{seed,edge}.json`, `docs/api-samples/catalog-seed.json` (ids),
`docs/sheet-seed/{Jams,2026-07-25}.csv`, `docs/risks-and-open-questions.md`, bitácora D-03–D-20,
`docs/specs/catalog-repository-cache.md` (incl. User Approvals), every file in
`core/model/src/main/.../model/`, `JamTest.kt`, every main file of `core/data`,
`core/data/build.gradle.kts`, `Fixtures.kt`, `DefaultCatalogRepositoryTest.kt`,
`DataModuleTest.kt`, `app/.../BluesJamApp.kt`, `app/.../di/AppModule.kt`,
`konsist-test/.../ModuleIsolationTest.kt`, `config/detekt/detekt.yml`, `init.sh` (tail),
`.claude/skills/architecture/SKILL.md` and `references/presenter-pattern.md` (grep only).
`Jam(` is constructed only in `JamTest` today, so D1 touches no other code. DESIGN.md not read (no UI).

### Prototype (throwaway clone in the session scratchpad, HEAD `655c670`)

Proven with real runs:

- **D1 compiles** and `:core:model:test` passes with `Jam.setlist: Setlist` and the adapted `JamTest`.
- **Room v2** with `jam`, `jam_song`, `jam_slot` (composite PKs, `ForeignKey` CASCADE) and a
  `@DatabaseView` `jam_song_resolved` (`LEFT JOIN catalog_song`, `COALESCE(c.title, s.title_copy)`)
  compiles under KSP 2.3.12 with **no Room warning**. A `@Transaction` query returning
  `JamWithChildren(@Embedded jam, @Relation songs: List<JamSongResolved>, @Relation slots)` works on
  the bundled driver: the Flow **re-emits when `catalog_song` is replaced** (title copy → catalog
  title) and deleting `jam` cascades to slots (count 0).
- **Exceptions on the JVM:** a constraint failure on the bundled driver surfaces as
  `android.database.SQLException`; `android.database.sqlite.SQLiteFullException("disk full")` (the
  device class, subclass of it) is constructible from the stub jar. `CancellationException` **is an
  `IllegalStateException`**, so catching `IllegalStateException` would swallow cancellation. A
  closed database throws `JobCancellationException` (not a usable test path).
- **Failing case today:** a `CatalogDao` delegate throwing `SQLiteFullException` makes both an
  explicit `refresh()` and the collector-started background refresh fail the test (uncaught). With
  `catch (e: android.database.SQLException)` → `DataFailure.Storage`, both pass, the 58 existing
  tests stay green and detekt accepts it (no `TooGenericExceptionCaught`).
- **DTOs** with the seven-key `slots` object decode both samples; the `Otros` entry regex below
  accepts `Juan (saxo)`, `Juan(saxo)`, ` Ana ( percusión ) `, `María José (voz 2)` and rejects
  `Juan saxo`, `(saxo)`, `Juan ()`, `Juan (saxo) x`, `Juan (sa(x)o)`.
- **Live** (JVM test reading the git-ignored URL, never printed): the `jams` route answered in
  5.3 s with one jam, `2026-07-25 21:00 PUBLICADA`, 13 rows, positions 1..13, 0 names, 0 `-`, 0
  `Otros`; all 13 `songId`s resolve in the live 100-song catalog. Today it is a past jam, so the
  device shows no upcoming jam.
- **Konsist gap (G1):** a feature file with a fully qualified `kotlinx.serialization.KSerializer`
  and no import **compiles** (serialization-core reaches `:feature:info` transitively) and passes
  all 12 rules. A text rule (below) caught it and has zero hits on the current tree.

Not prototyped: anything on the Pixel 5 (connected, but the planner did not replace the installed
build), the `jam_extra` table (same pattern as `jam_slot`), the full gate on the clone.

## Technical Approach

### D1 — Domain: `Jam.setlist: Setlist` (in `:core:model`, needs approval)

New file `Setlist.kt`, package `com.bbbjam.core.model`:

```kotlin
sealed interface Setlist {
    data class Available(val songs: List<JamSong>) : Setlist   // positions exactly 1..n, in order
    data object Withheld : Setlist                              // not served to this reader (draft)
    data class Unavailable(val problem: SetlistProblem) : Setlist
}
enum class SetlistProblem { MISSING_TAB, INVALID_TAB, INVALID_ROWS, UNKNOWN }
```

`Jam(date, startTime, venue, status, setlist: Setlist)`; the positions check moves from `Jam.init`
to `Setlist.Available.init` (same message shape), and `Jam.init` adds: a `PUBLISHED` jam is never
`Withheld`. A `DRAFT` may be `Available` (the admin read, later) or `Withheld`. `JamSong`,
`Lineup`, `Slot`, `ExtraParticipant`, `Key` are unchanged, so D-08 (key on `JamSong`, set by the
admin) and D-18 (lineup only shrinks, `Otros` never open) hold as they are. No `songs` shortcut
on `Jam`: an `orEmpty()` accessor would bring back the ambiguity this removes.

Why in the model, not a `:core:data` wrapper: "draft → message, not an empty list" is a domain
rule (domain-model.md, Jam status), it is what `unpublished-setlist-state` and the phase 2
assistant must see, and a wrapper would still need a `Jam` with a fake `emptyList()`. A nullable
list plus a reason was rejected: two fields that must agree, and `null` says nothing.

### D2 — Jam mapper (`jams/JamsMapper`, internal, pure, no clock)

`map(rows: List<JamDto>): MappedJams(jams, rejected: List<RejectedJam>, issues: List<SetlistIssue>)`.
Every cell is trimmed again, empty = null (endpoint trims too; not relied on). Matching is exact.

**Jam row** (a rejecting issue drops the jam; all issues collected, 1-based `index` in `jams`):

| Field | Rule | Issue |
|---|---|---|
| `date` | `[0-9]{4}-[0-9]{2}-[0-9]{2}` + `LocalDate.parse` (strict, `2026-02-30` fails) | `MissingDate` / `InvalidDate` |
| `startTime` | `([01][0-9]\|2[0-3]):[0-5][0-9]` → `LocalTime` | `MissingStartTime` / `InvalidStartTime` (P8) |
| `venue` | required | `MissingVenue` (P8) |
| `status` | exactly `BORRADOR` → `DRAFT`, `PUBLICADA` → `PUBLISHED` | `MissingStatus` / `InvalidStatus` |
| `date` unique | every row whose date parses and is shared is rejected, valid or not (P2) | `DuplicateDate` |

**Setlist** (only for a kept jam):

- `DRAFT` → `Withheld`, always. A non-null `setlist` on a draft is a contract breach: ignored
  (fail closed) and reported `DraftSetlistIgnored`.
- `PUBLISHED` + `setlistError`: `missing_tab` → `Unavailable(MISSING_TAB)`; `missing_header`,
  `duplicate_header` → `Unavailable(INVALID_TAB)` (duplicate slot headers are thereby settled
  upstream: the mapper never sees them); any other code → `Unavailable(UNKNOWN)`.
  (`invalid_date`/`duplicate_date` rows are already rejected above.)
- `PUBLISHED`, `setlist` null and no error → `Unavailable(UNKNOWN)`, reported `SetlistMissing`.
- `PUBLISHED` + array → rows below. **Any rejected row, duplicate or gap → the whole setlist is
  `Unavailable(INVALID_ROWS)`** with every row issue reported (P4). Else `Available`, sorted by
  position. `[]` → `Available(emptyList())`.

**Setlist row** (index = 1-based in that jam's `setlist`):

| Field | Rule | Issue (rejects the row) |
|---|---|---|
| `position` | `[0-9]+`, `toIntOrNull()` ≥ 1 (`2.5`, `0`, `-1` fail); unique; exactly 1..n after sorting | `MissingPosition`, `InvalidPosition`, `DuplicatePosition`, `PositionGap` |
| `songId` | `SongId.parseOrNull` | `MissingSongId` / `InvalidSongId` |
| `title`, `artist` | tab copies, required (schema) | `MissingTitle` / `MissingArtist` |
| `key` | `Key.parseOrNull`; **never** filled from the catalog default (D-08) | `MissingKey` / `InvalidKey` |
| `slots` | in order guitar1, guitar2, bass, drums, vocals, harmonica, keyboards = `Lineup.DEFAULT_INSTRUMENTS`; `null` → open `Slot`, `-` → no slot, other → `musicianName` | none |
| `extraParticipants` | split `;`, trim, drop empty; entry must match `([^;()]+?)\s*\(([^;()]+)\)` whole, both groups trimmed and non-blank → `ExtraParticipant`; order kept | `MalformedExtraParticipant(entry)`: **drops the entry only** (P1) |

U1 (slot identity) needs no extra state: because the slot keys are walked in header order and `-`
is skipped, the k-th slot of an instrument in `Lineup.slots` is the k-th non-`-` column of it.
The cache also keeps the column index, so a later write slice can check it.

The mapper outputs `Jam`s whose `JamSong.title/artist` are the **tab copies**; resolution against
the catalog happens at read time (D3), so the order of the catalog and jams refreshes never
matters and a later catalog fix shows up without refetching jams.

### D3 — Room cache (database version 1 → 2)

Tables (all `internal` entities in `cache/`):

- `jam`: `date` TEXT PK (ISO), `sheet_order`, `start_time` (`HH:MM`), `venue`, `status` (enum
  name), `setlist_state` (`AVAILABLE`/`WITHHELD`/`UNAVAILABLE`), `setlist_problem` TEXT NULL.
- `jam_song`: PK (`jam_date`, `position`), FK → `jam.date` CASCADE; `song_id`, `title_copy`,
  `artist_copy`, `key`.
- `jam_slot`: PK (`jam_date`, `position`, `column_index` 0..6), FK → `jam_song` CASCADE;
  `instrument`, `musician_name` NULL. One row per non-`-` column.
- `jam_extra`: PK (`jam_date`, `position`, `entry_order`), FK → `jam_song` CASCADE; `name`, `instrument`.
- View `jam_song_resolved`: `jam_song` LEFT JOIN `catalog_song` with `COALESCE` for title/artist.
- `sync_state` reused with `resource = "jams"`.

Normalized rather than a JSON column: slot identity is a natural key (sheet-schema Identifiers),
musician-name suggestions are a `SELECT DISTINCT musician_name` away, admin slices will update one
slot, and the cost is one more relation in a query already proven. `JamsDao`:
`@Transaction @Query observeJams(): Flow<List<JamWithChildren>>` ordered by `sheet_order` (three
`@Relation`s on `jam_date`: `jam_song_resolved`, `jam_slot`, `jam_extra`; grouped by position in
Kotlin), `@Transaction replaceJams(jams, songs, slots, extras, state)` (delete `jam` → cascade,
insert all, upsert state), `syncState`/`observeSyncState`/`upsertSyncState`/`recordFailure` for
`"jams"` (duplicated from `CatalogDao` rather than refactoring it). `version = 2`; the existing
`fallbackToDestructiveMigration(dropAllTables = true)` drops the v1 catalog too, which simply
refetches. Still no exported schema.

### D4 — Repository API (`jams/`, public unless noted)

```kotlin
interface JamsRepository {
    fun observeJams(): Flow<JamsSnapshot>      // cache-first; collecting starts a due refresh
    suspend fun refresh(): JamsRefreshOutcome  // single-flight; never throws but cancellation
}
data class JamsSnapshot(val upcoming: Jam?, val past: List<Jam>, val freshness: Freshness)
class JamCalendar(clock: Clock, zone: ZoneId) { fun today(): LocalDate; fun now(): Instant }
```

The split is in the repository, not the presenters, because the schema assigns "at most one
upcoming" and "past empty cells are not recorded" to this slice, and two features (next-jam,
past-jams) cannot share presenter code (D-03). On each emission, with `today = calendar.today()`:
`upcoming` = the earliest jam with `!isHistorical(today)` (P5: later future jams are held back);
`past` = historical jams by date descending; in every `Available` past setlist the lineup keeps
only filled slots (P6), so no consumer can draw an open slot in history. `JamCalendar` uses
`now.atZone(zone).toLocalDate()` (not `LocalDate.ofInstant`) and is bound with
`ZoneId.of("America/Argentina/Buenos_Aires")` (P7), not UTC: with UTC a 21:00 jam would turn
historical at 21:00 local on its own night. Presenters reuse `JamCalendar` later.

`JamsRefreshOutcome = Updated(jamCount, rejected: List<RejectedJam>, issues: List<SetlistIssue>,
heldBack: List<LocalDate>) | Failed(DataFailure)`, with `toLogLine()` →
`jams refresh: updated N jams, R rejected, I setlist issues, H held back; …` (dates, indexes and
issue names; never musician names, never the URL). Staleness, the 60 s retry rule and
single-flight are the catalog's (`Freshness.STALE_AFTER`, `CatalogRepository.MIN_RETRY_INTERVAL`);
the implementer may extract the shared `toFreshness`/`isRefreshDue` helpers into `cache/` as long
as the catalog tests stay unchanged and green.

### D5 — Refresh robustness, both repositories (`DataFailure.Storage`)

- `DataFailure` gains `data class Storage(detail: String)` (encoded `Storage:<detail>`; decode
  round-trips). Public sealed type of an accepted slice: additive, `toLogLine` covers it.
- In both `fetchAndStore`s: storing (`replace…`, `recordFailure`) runs in
  `try { … } catch (e: android.database.SQLException)` → `Failed(Storage(e.javaClass.simpleName))`,
  then a best-effort `recordFailure` in its own catch (a full disk may refuse that too).
- Mapping runs in `catch (e: IllegalArgumentException)` → `InvalidResponse("mapping: <first
  line>")`, cache untouched (a domain constructor throwing is a mapper bug; on jam night it must
  not kill the app).
- **Never** catch `Exception`, `RuntimeException`, `IllegalStateException` or `Throwable` (detekt,
  and `CancellationException` is an `IllegalStateException`).
- Last resort: `DataScope` gets a `CoroutineExceptionHandler` reporting through an injected
  `(Throwable) -> Unit` (`dataModule` passes `Log.e("BluesJam", …)`), and `BluesJamApp.appScope`
  gets one too, so an unforeseen exception in a background refresh is logged, not fatal.

### D6 — Wiring

`dataModule`: `single { JamCalendar(get(), ZoneId.of(JAM_ZONE)) }`, `single { get<BluesJamDatabase>().jamsDao() }`,
`single<JamsRepository> { DefaultJamsRepository(get(), get(), get(), get()) }`, the `DataScope`
handler. `BluesJamApp`: refresh catalog and jams in two concurrent launches, log both lines, then
log one line from the first `observeJams()` emission: `jams cache: upcoming <date|none>, past N,
songs S (C from catalog)` — counts only; it proves the view and the split on the framework SQLite.

## Expected File Changes

- `core/model/src/main/.../Setlist.kt` (new), `Jam.kt`; tests `SetlistTest.kt` (new), `JamTest.kt`.
- `core/data/src/main/.../DataFailure.kt`, `DataScope.kt`, `di/DataModule.kt`,
  `catalog/DefaultCatalogRepository.kt` (D5 only), `cache/BluesJamDatabase.kt` (v2, entities, view,
  `jamsDao()`), `cache/{JamEntities,JamsDao,JamCacheMapping}.kt`, `remote/JamDto.kt`,
  `remote/AppsScriptTransport.kt` (`Resources.JAMS = "jams"`),
  `jams/{JamsRepository,JamsSnapshot,JamCalendar,JamsRefreshOutcome,JamIssue,JamsMapper,DefaultJamsRepository}.kt`.
- `core/data/src/test/.../`: `jams/JamsMapperTest.kt`, `jams/DefaultJamsRepositoryTest.kt`,
  `cache/JamsDaoTest.kt`, `DataScopeTest.kt`, plus additions to `DefaultCatalogRepositoryTest`,
  the `DataFailure` encode test, `DataModuleTest`, `Fixtures` (`jamsBody`, seed-CSV reader).
- `app/src/main/java/com/bbbjam/BluesJamApp.kt`.
- G1 only if approved: `konsist-test/.../ModuleIsolationTest.kt`.

## Durable Documentation Impact

- `docs/domain-model.md`: `Jam(… setlist: Setlist)`, the three setlist states, published ⇒ never
  withheld, past empty slots = not recorded (dropped). `CONTEXT.md`: Setlist entry gains "withheld"
  and "unavailable" in one sentence each.
- `docs/sheet-schema.md`: `Jams` Mapper rules → "enforced by `jams-repository-cache`" with P1–P8
  as decided; Type-to-Sheet row for `Jam.setlist`.
- `docs/risks-and-open-questions.md`: `setlistError` display settled; jam seed fixtures done;
  midnight boundary settled by P7 (Buenos Aires date, jam historical from 00:00 next day).
- `.claude/skills/architecture/SKILL.md`: `:core:model` list adds `Setlist`, `SetlistProblem`;
  `:core:data` layout adds `jams/`, `JamCalendar`, Room v2 tables and view, `DataFailure.Storage`
  and the "catch `android.database.SQLException`, never `IllegalStateException`" rule; Konsist
  count if G1.
- `docs/apps-script-api.md`, `AGENTS.md`: not needed. `PROGRESS.md`, `feature_list.json`: evidence.

## Implementation Tasks

1. D1 in `:core:model` with tests; `./gradlew :core:model:test`.
2. DTOs, `JamsMapper`, `JamsMapperTest` (samples, seed CSV, every rule).
3. Entities, view, `JamsDao`, DB v2, `JamsDaoTest`.
4. `JamCalendar`, `DefaultJamsRepository`, `DefaultJamsRepositoryTest`.
5. D5 in both repositories + `DataScope` handler, with the failing-case demonstration first.
6. `dataModule`, `DataModuleTest`, `BluesJamApp`.
7. G1 if approved. `./gradlew ktlintFormat`, `CI=true ./init.sh`, device check, docs.

## Verification Plan

`CI=true ./init.sh` exits 0 with `konsist: wired` (12 rules; 13 with G1), `detekt: wired`,
`ktlint: wired`; no baseline, `ignoreFailures`, `@Suppress` or rule disable. JVM tests
(fixtures read from `docs/`, Room on `BundledSQLiteDriver`, fake transport + one MockWebServer):

- **`SetlistTest`/`JamTest`:** positions rules on `Available`; published + `Withheld` throws;
  draft + `Withheld` and draft + `Available` build.
- **`JamsMapperTest`:** `jams-seed.json` → 1 jam, 13 songs, 7 open slots each, copies as titles;
  `Jams.csv` + `2026-07-25.csv` turned into DTOs (by header, trim, empty → null; fail on `"`) give
  the same `MappedJams`; `jams-edge.json` → Scenario 3 exactly, plus 2026-08-29 sorted 1,2,3,
  `the-thrill-is-gone` lineup Ana, Luis (GUITAR), Marta, Diego, Sofía, keyboards open (no
  harmonica), extras `Juan/saxo`, `Ana/percusión`; `crossroads` one GUITAR slot `Pedro`, 5 open. One
  case per rule in D2's tables, incl. `2026-02-30`, `9:00`, `24:00`, `Publicada`, `2.5`, `0`,
  duplicate position, gap, `Bb m`, a key absent but catalog default present (still rejected, D-08),
  `" - "` as a slot, all seven `-` (empty lineup), `Juan(saxo)`, `Juan saxo` (entry dropped,
  song kept), `";;"`, a draft with a setlist (`Withheld` + issue), published with neither
  (`UNKNOWN`), an unknown error code, three rows sharing a date.
- **`JamsDaoTest`:** replace is one emission and cascades; view resolution + re-emit on catalog
  replace; `recordFailure("jams")` keeps rows and leaves the `"catalog"` row alone.
- **`DefaultJamsRepositoryTest`:** Scenarios 1–6; today = jam date → upcoming, next day → past;
  past newest first; past lineups have no open slot; service error/HTML/`schemaVersion` 2/missing
  `jams` leave the cache; `NotConfigured`; stale after 30 min triggers one background refresh;
  60 s retry; single-flight.
- **Catalog additions:** Scenario 5 for `DefaultCatalogRepository` (explicit and background).
- **`DataScopeTest`:** a launched `error("boom")` reaches the reporter and the scope stays active.
- **`DataModuleTest`:** resolves one `JamsRepository` and a `JamCalendar` in Buenos Aires.
- **Failing cases to show:** Scenario 5 tests red on the unmodified catalog repository (as in the
  prototype), then green; dropping the duplicate-date rule turns a mapper test red; with G1, a
  probe file with a qualified `kotlinx.serialization.` reference in `:feature:info` fails Konsist.

**Device check (Pixel 5 `09281FDD4004U6`, manual, outside the gate)**, `ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"`:

1. With the accepted v1 build still installed, `./gradlew :app:installDebug` (upgrade, no
   uninstall), `$ADB logcat -c`, launch. Expect no crash (destructive v1 → v2), then
   `catalog refresh: updated 100 …`, `jams refresh: updated 1 jams, 0 rejected …` and
   `jams cache: upcoming none, past 1, songs 13 (13 from catalog)` (numbers as of 2 October 2026).
2. Force-stop, pull `databases/bluesjam-cache.db` (+ `-wal`/`-shm`) with `run-as com.bbbjam`,
   inspect with Python `sqlite3`: `jam` 1, `jam_song` 13, `jam_slot` 91, `jam_extra` 0,
   `sync_state` rows `catalog` and `jams` with `fetched_at` set, `PRAGMA user_version` = 2.
3. Airplane mode on (allowed by the user for this check), force-stop, relaunch: both refreshes
   `failed Offline`, the `jams cache:` line unchanged; pulled DB has the same rows, `failure` =
   `Offline`, same `fetched_at`. Airplane mode off.
4. Record cold start and the crash buffer (empty) in `PROGRESS.md`. Never paste the URL or names.

## Evidence To Capture

`init.sh` tail with the three `wired`; new result files and counts (baseline 29 files unchanged
except the edited ones); the red→green storage failure output; the device log lines, DB counts and
`user_version`, online and offline.

## Validator Checklist

- [ ] `Jam.setlist` is a `Setlist`; no code path builds an empty `Available` for a draft or a
      broken tab; published ⇒ never `Withheld`.
- [ ] Every row of D2's tables has a test; `JamSong.key` never comes from `Song.defaultKey`.
- [ ] Today comes only from `JamCalendar` (injected clock, Buenos Aires zone); no `LocalDate.now()`.
- [ ] Past jams expose no open slot; at most one `upcoming`.
- [ ] One transaction per replace; a failed refresh never touches rows; DB version 2, destructive.
- [ ] No `catch` of `Exception`/`RuntimeException`/`IllegalStateException`/`Throwable`;
      `Storage` is reachable for both resources and tested.
- [ ] No URL or musician name in a log line, tracked file or `PROGRESS.md`.
- [ ] Docs above updated; P1–P8 recorded as decided.

## Needs The User's Approval

- **D1 (domain change to an accepted slice):** `Jam.setlist: List<JamSong>` → `Setlist`
  (`Available`/`Withheld`/`Unavailable(SetlistProblem)`), positions rule moved to `Available`, new
  rule "published is never withheld". Changes the documented shape in `domain-model.md`; no D-xx
  rule changes. Also additive: `DataFailure.Storage`.
- **P1** `Juan(saxo)` without a space is accepted; a malformed entry drops only itself. (Recommended.)
- **P2** A `fecha` on more than one row rejects every such row, even ones invalid for another reason.
- **P3** A published jam with a `setlistError` is shown as "setlist unavailable", never hidden or empty.
- **P4** Any invalid setlist row (bad key, bad id, duplicate/gapped positions) makes that jam's whole
  setlist `Unavailable(INVALID_ROWS)`. Recommended: positions are the write identity, so dropping
  and renumbering would aim later writes at the wrong row. Alternative: drop bad rows and allow gaps
  (relaxes the 1..n domain rule).
- **P5** Two or more non-historical jams: the earliest is `upcoming`, later ones are held back and
  only logged. Alternative: reject them all (no upcoming jam shown).
- **P6** In a past jam, empty slot cells ("not recorded") are dropped from the lineup.
- **P7** "Today" is the Buenos Aires date, not the device zone or UTC.
- **P8** A jam row with no valid `hora` or no `lugar` is rejected (schema: required), so an admin
  slip there hides that jam, visible only in the log.
- **G1 (optional gate change, recommended):** a 13th Konsist test,
  `data-libraries-only-in-core-data-qualified`: outside `:core:data` and `:konsist-test`, no
  non-import line may match `\b(okhttp3|androidx\.room|kotlinx\.serialization)\.`. Prototyped:
  catches the probe, zero false positives today. Without approval, the gap stays documented.

## Risks

- Sized at the top of one session (domain change + mapper + four tables + repository + robustness).
  If it runs long, split after task 3 (model + mapper + DAO) and task 7 (repository, D5, device).
- The view/relations are proven only on the bundled driver; the device check covers framework SQLite.
- The read Flows still throw on a Room read exception (corrupt file); left to `list-states`.
- No midnight re-split: a screen open across midnight keeps yesterday's split until re-collected.
- Two Apps Script calls per app start (catalog + jams): still far under 30 concurrent executions.
- No admin surface shows rejected jams or rows yet; P4/P8 rejections are visible only in logcat.
- Live data has no names, `-` or `Otros`; those paths are proven only by `jams-edge.json`.

## User Approvals

Recorded 2 October 2026, before implementation. Where they differ from the body, these win.

- **D1: approved.** `Jam.setlist` becomes the sealed `Setlist` (`Available`, `Withheld`,
  `Unavailable(SetlistProblem)`); a published jam is never `Withheld`. `DataFailure.Storage` is
  approved with it. Update `docs/domain-model.md`.
- **P4: the user chose the alternative — drop only the bad row.** An invalid setlist row is dropped
  and reported; the rest of the setlist stays `Available`. Consequences the implementer must apply:
  - `Setlist.Available` no longer requires positions exactly 1..n. It requires positions ≥ 1,
    unique and ascending; gaps are allowed. Positions are never renumbered: they stay the Sheet's
    `posicion`, which remains the write identity.
  - `Setlist.Available` also carries how many rows were dropped (for example `droppedRows: Int`), so
    a screen can say the list is incomplete instead of showing a silent gap.
  - A setlist whose every row is invalid is `Unavailable(INVALID_ROWS)`, never an empty
    `Available`.
  - Update the "positions 1..n" wording in `docs/domain-model.md` and the jam Mapper rules in
    `docs/sheet-schema.md` (the Sheet itself should still hold 1..n; the app tolerates a dropped
    row), and the existing `JamTest` cases that asserted gaps are rejected.
- **G1: approved.** The 13th Konsist test `data-libraries-only-in-core-data-qualified`, failing case
  demonstrated.
- **P1, P2, P3, P5, P6, P7, P8: approved as recommended.** `Juan(saxo)` without a space is valid; a
  duplicated `fecha` rejects every row with it; a broken tab shows as "setlist unavailable"; with
  several future jams the earliest is upcoming and the rest are logged; in a past jam an empty slot
  cell means "not recorded" and is dropped; "today" is the Buenos Aires date; a jam row with a
  missing or invalid `hora` or a missing `lugar` is rejected and logged.
