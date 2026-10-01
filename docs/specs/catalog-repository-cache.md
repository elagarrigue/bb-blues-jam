# Feature Implementation Spec: Read the catalog into a Room cache

## Source Feature

- `id`: catalog-repository-cache
- `area`: data
- `depends_on`: apps-script-read-endpoint, domain-model-types, apps-script-jams-read-endpoint (all
  `accepted`)
- `status`: not_started (at planning time, 1 October 2026, HEAD `6f29c10`)
- `source`: `feature_list.json`

**Scope: the catalog only, plus the shared data infrastructure. The jams half needs a split**
(S1 below). **The user must approve the new dependencies, the gate changes and Q1–Q3 before
work starts** (see "Needs the user's approval").

## Goal

`:core:data` gets its first real code. It fetches `GET <url>?resource=catalog` over OkHttp and
maps the contract JSON to `Song` under every catalog Mapper rule in `sheet-schema.md`. It stores
the result in Room and exposes a cache-first `CatalogRepository`. That repository always returns
the last stored catalog, offline included, with a freshness value the UI can use for a staleness
indicator. The app refreshes the catalog once per process start. The infrastructure this slice
lays down is meant to be reused by the jams slice: the transport, the failure model,
`sync_state`, the database, desugaring, the data convention plugin and the base-URL wiring.

## Scope Check And Split (S1)

The feature title and its three verification lines are about the catalog only. The jams half is
not small. It needs the `estado` enum, date and time parsing, `posicion` validation and sorting,
the slots with `-` and their ordinals, `Otros`, catalog resolution with the fallback copy, "at
most one upcoming", `setlistError`, and the rule that a past jam's empty slots are "not recorded".
It also needs its own Room tables and a domain decision, because `Jam.setlist` is a non-null list
with no way to say "unavailable". Together that is a second data model with its own product
questions, well past one spec. **Recommendation for the orchestrator** (this spec does not edit
`feature_list.json`):

- Add `jams-repository-cache` (area `data`, `depends_on: ["catalog-repository-cache",
  "apps-script-jams-read-endpoint"]`). Its verification: the mapper covers `jams-seed.json`,
  `jams-edge.json` and the `2026-07-25.csv`/`Jams.csv` seed, the cached jams are served offline,
  and freshness is exposed. Move these inherited notes to it: duplicate slot headers, `Nombre(instrumento)`
  without a space, duplicate `Jams.fecha`, and the `Jams`/tab mismatch display. Proposed
  defaults, for the user to confirm there:
  - Duplicate slot headers are already settled upstream. The endpoint answers with the per-jam
    `duplicate_header`, and `slots` always has seven fixed keys, so the mapper cannot see them.
  - Accept `Juan(saxo)`. The rule is "a name, then an instrument in parentheses", and the space
    is not part of it.
  - Reject every jam row that shares a `fecha`. The jam's identity is its date, so the mapper
    cannot know which row is right.
  - Show a jam with a `setlistError` as a jam whose setlist is unavailable, never hidden and
    never shown as empty.
- Make `next-jam-read-only-list` and `past-jams-list` also depend on `jams-repository-cache`.
- Optionally, drop `apps-script-jams-read-endpoint` from this feature's `depends_on`.

## Non-Goals

- The jams route, `Jam`/`JamSong` mapping and jam tables: all of S1.
- Any UI, presenter or Spanish copy. Freshness is exposed as data only. A screen draws it later.
- Enrichment (MusicBrainz, Deezer). That is `enrichment-background-fetch`, which will use its own
  table keyed by song id, never columns on `catalog_song`, because a catalog replace wipes that
  table (D-09).
- Writes, the admin flag (DataStore) and the passphrase.
- Using tempo, tags, difficulty or `songsterrId` anywhere. They are parsed and stored only (D-20).
- A Room schema export or migrations (Decision 5).

## Job Story

When I open the app in a bar with no signal,
I want the songs I last saw to still be there, with a note of how old they are,
so I can keep using the app instead of facing an error.

## Acceptance Scenarios

1. **Online first fetch.** Given an empty cache and a configured URL, when `refresh()` runs, then
   the cache holds every valid song in response order. `observeCatalog()` emits those songs with
   `fetchedAt` = the clock's instant, and the outcome is `Updated(count, rejected)`.
2. **Offline.** Given a cached catalog, when the transport fails with an `IOException`, then
   `observeCatalog()` still emits the same songs. `fetchedAt` keeps the old value,
   `lastFailure = Offline`, and `isStale(now)` is true.
3. **Service error.** Given a body `{"schemaVersion":1,"error":{"code":"missing_header",…}}`, the
   cache is untouched and `lastFailure = Service("missing_header")`. The same holds for HTML,
   non-JSON, a non-2xx status, `schemaVersion` ≠ 1 and a missing key or a wrong type
   (`InvalidResponse`).
4. **Bad rows.** Given `catalog-edge.json`, the three valid songs are stored. `sin-tono` is
   rejected with the reasons `MissingDefaultKey`, `InvalidTempo` and `InvalidSongsterrId`, and
   the other songs are unaffected.
5. **Not configured.** Given a blank URL (CI, a fresh clone), `refresh()` makes no call and fails
   with `NotConfigured`. The app still starts.
6. **Redirect.** The client follows the web app's 302 to another host. This was proven live in
   the prototype.
7. **Background refresh.** Collecting `observeCatalog()` with `fetchedAt` older than 30 min (or
   null) starts one refresh. Concurrent collectors and calls share it (single-flight). No
   automatic retry happens within 60 s of the last attempt.

## Repository Research

### Files Inspected

`AGENTS.md`, `PROGRESS.md` (head), `feature_list.json` (this feature, `next-jam-read-only-list`
and the dependency graph), `docs/apps-script-api.md`, `docs/sheet-schema.md` (all of it),
`docs/domain-model.md`, `docs/technical-discovery.md`, `docs/risks-and-open-questions.md`,
`docs/api-samples/*.json`, `docs/sheet-seed/*.csv`, every file in
`core/model/src/main/kotlin/com/bbbjam/core/model/`,
`core/data/{build.gradle.kts,src/main/kotlin/com/bbbjam/core/data/CoreDataMarker.kt}`,
`app/{build.gradle.kts,src/main/AndroidManifest.xml,src/main/java/com/bbbjam/{BluesJamApp,di/AppModule}.kt,src/test/java/com/bbbjam/ModuleWiringTest.kt}`,
`feature/info/{build.gradle.kts,src/test/…/InfoModuleTest.kt}`, `build-logic/convention/**`,
`build.gradle.kts`, `settings.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`,
`init.sh`, `konsist-test/**/ModuleIsolationTest.kt`, `config/detekt/detekt.yml`,
`.claude/skills/architecture/SKILL.md`. I did not inspect DESIGN.md (no UI) or the Sheet itself
(no agent can read it).

### Prototype (throwaway clone in the session scratchpad, HEAD `6f29c10`)

Proven with real runs, not assumed:

- **KSP `2.2.10-2.0.2` fails** under AGP 9 built-in Kotlin: "Using kotlin.sourceSets DSL to add
  Kotlin sources is not allowed with built-in Kotlin". **KSP `2.3.12` works** with KGP 2.2.10.
  With it, Room 2.8.4 generates `BluesJamDatabase_Impl`/`CatalogDao_Impl`, including a
  `@Transaction` default method on a DAO interface.
- The `org.jetbrains.kotlin.plugin.serialization` 2.2.10 plugin, applied by a convention, works
  under built-in Kotlin. kotlinx-serialization 1.9.0 decodes both catalog samples.
- **Room on the JVM without Robolectric:** `Room.inMemoryDatabaseBuilder(ContextWrapper(null),
  …).setDriver(BundledSQLiteDriver())` with `testImplementation sqlite-bundled-jvm:2.6.2` and
  `isReturnDefaultValues = true`. It runs in about 0.26 s, and Turbine sees the Flow re-emit after each
  transactional replace, with no intermediate empty list. Robolectric 4.16 also worked but took
  about 34 s on its first run and downloads `android-all`, so it was rejected.
- OkHttp 5.1.0 + MockWebServer3: a cross-host 302 is followed. **Live** (JVM test reading the
  git-ignored `local.properties`): the real `/exec` redirect was followed, the response decoded
  in 5.3 s on the first call, and there were 100 songs, 0 invalid keys or ids, 0 duplicates and
  no optional field set. The `jams` route also answered.
- `buildFeatures.buildConfig = true` in the application convention, plus a `buildConfigField`
  from `java.util.Properties` (which unescapes `\:`), produces the URL. Without the key it
  produces `""`. The configuration cache notices a changed `local.properties` ("properties file …
  has changed").
- Desugaring in `configureAndroidCommon` (every Android module): `CI=true ./init.sh` exited 0 in
  38 s warm with konsist, detekt and ktlint all `wired`. detekt and ktlint did not lint the KSP
  output. **Failing case:** with desugaring off, `:core:data:lintDebug` fails `NewApi` on
  `java.time.Clock#systemUTC`. Lint adds only `GradleDependency`/`NewerVersionAvailable`
  warnings, the same kinds as today.
- Resolved versions: `kotlin-stdlib` stays at 2.2.10 and coroutines at 1.9.0 at runtime. The
  build classpath's stdlib 2.3.21 comes from AGP and was already there at baseline. There is one
  KGP (2.2.10) and one AGP (9.4.1).
- **Pinning reasons:** OkHttp 5.2.x+ needs stdlib 2.2.20+, and kotlinx-serialization 1.10 needs
  2.3.0. Either would push stdlib past the 2.2.10 compiler, the same trap as Koin 4.2. Room 2.8.4
  needs stdlib 2.1.20.

### Current Gaps

- `:core:data` holds only `CoreDataMarker`.
- The manifest has **no `INTERNET` permission**. Without it, a device fetch fails even though
  every JVM test passes.
- No BuildConfig, no desugaring, no KSP and no serialization exist yet.
- The Konsist rule `build-file-applies-convention` forbids raw plugins and `buildFeatures` and
  `isReturnDefaultValues` in module files. That is why KSP, serialization, the test flag and
  BuildConfig all go into conventions.

## Technical Approach

### Decision 1: Module placement, API and Koin

Everything lives in `:core:data` under `com.bbbjam.core.data`. The **public** types are:

- `catalog/CatalogRepository` (interface):
  - `fun observeCatalog(): Flow<CatalogSnapshot>`
  - `suspend fun refresh(): RefreshOutcome`
- `catalog/CatalogSnapshot(songs: List<Song>, freshness: Freshness)`
- `Freshness(fetchedAt: Instant?, lastFailure: DataFailure?, isRefreshing: Boolean)`, with
  `fun age(now: Instant): Duration?` and `fun isStale(now: Instant): Boolean`, which is
  `fetchedAt == null || lastFailure != null || age > 30 min`.
- `DataFailure` (sealed), with four values:
  - `NotConfigured`
  - `Offline`: any `IOException`, timeouts included.
  - `Service(code: String)`: an envelope error.
  - `InvalidResponse(detail: String)`: non-2xx, non-JSON, wrong shape or `schemaVersion` ≠ 1.
    The detail is for logs only.
- `RefreshOutcome`: `Updated(songCount: Int, rejected: List<RejectedSong>)` or
  `Failed(failure)`.
- `RejectedSong(index: Int, id: String?, reasons: List<RejectionReason>)`. `index` is 1-based in
  `songs`, not the Sheet row, because the script skips blank rows.
- `AppsScriptEndpoint`, with `companion fun of(raw: String): AppsScriptEndpoint`. A blank or
  non-`https` URL becomes `url = null`, which means not configured.
- `di/dataModule`.

`now` comes from the caller. Presenters inject a `Clock` later.

**Internal** types: the DTOs, `AppsScriptTransport` (`fun interface`, `suspend fun get(resource):
TransportResult`), `OkHttpAppsScriptTransport`, `CatalogMapper`, the Room classes and
`DefaultCatalogRepository`.

**Koin** (`dataModule`, constructor injection only, every binding `single`):

- `Clock.systemUTC()`
- `DataScope(CoroutineScope(SupervisorJob() + Dispatchers.IO))`
- the OkHttp client
- the transport, built from `get<AppsScriptEndpoint>()`
- `BluesJamDatabase.create(get<Context>())`. `androidContext()` registers `Context`, so
  `:core:data` needs `koin-core` only. No `koin-android`, which drags in ViewModel artifacts.
- `catalogDao`
- `CatalogRepository`

**`:app`** binds `single { AppsScriptEndpoint.of(BuildConfig.APPS_SCRIPT_URL) }` in `appModule`
and adds `dataModule` to `startKoin`. `BluesJamApp` then launches a startup refresh on an app
`CoroutineScope` and logs the outcome summary with `Log.i("BluesJam", …)`. The log carries counts
and the failure kind, never the URL.

### Decision 2: Base URL

`app/build.gradle.kts` reads `rootProject.file("local.properties")` with `java.util.Properties`,
when the file exists. It then calls `buildConfigField("String", "APPS_SCRIPT_URL", "\"$url\"")`,
where `url` is `bluesjam.appsScriptUrl`, trimmed, or `""`. `buildFeatures.buildConfig = true`
goes into `AndroidApplicationConventionPlugin`, because Konsist forbids `buildFeatures` in module
files. The URL then lives only in `app/build/` and in the APK. The endpoint is anonymous by
design, so that adds no exposure.

### Decision 3: HTTP and JSON

The client is **OkHttp 5.1.0**. It follows redirects by default (Scenario 6) and is
MockWebServer-testable. HttpURLConnection was rejected because it needs hand-written redirect
and test plumbing. Ktor was rejected because it adds an engine and a plugin layer for a single
GET.

- **Timeouts:** connect 15 s, read 30 s, `callTimeout` 45 s. The measured reads were 2.1–4.5 s
  warm and 5.2–5.3 s on a first call, and a true cold start is unmeasured. The cache means a
  long timeout never blocks the UI.
- **Request:** `GET` of `<url>` plus `resource` added with `HttpUrl.newBuilder()`. The call runs
  inside `withContext(Dispatchers.IO) { execute() }`, and the body is read as UTF-8.

The JSON library is **kotlinx.serialization 1.9.0**, configured as
`Json { ignoreUnknownKeys = true }`, so additive fields do not break old apps. The DTOs declare
all 8 song keys as `String?` **without defaults**. A missing key or a non-string value is
therefore an `InvalidResponse` for the whole response: that is a script bug, not admin data.

Envelope order:

1. Parse the body. Failure is `InvalidResponse`.
2. `schemaVersion` ≠ 1 is `InvalidResponse("schemaVersion N")`.
3. An `error` key is `Service(code)`.
4. A missing `songs` is `InvalidResponse`.

### Decision 4: Catalog mapper (`CatalogMapper.map(dto): MappedCatalog(songs, rejected)`)

Every cell is trimmed again, and empty means null. The endpoint already trims, but the mapper
does not rely on it. A row is rejected when any of the following holds, and **all** of its
reasons are collected:

| Field | Rule (sheet-schema.md) | Reason |
|---|---|---|
| `id` | required; `SongId.parseOrNull` | `MissingId` / `InvalidId` |
| `title`, `artist` | required | `MissingTitle` / `MissingArtist` |
| `defaultKey` | required; `Key.parseOrNull` (**Keys**) | `MissingDefaultKey` / `InvalidDefaultKey` |
| `tempo` | null or exactly `lento`/`medio`/`rápido` → `SLOW`/`MEDIUM`/`FAST` | `InvalidTempo` |
| `difficulty` | null or exactly `fácil`/`media`/`difícil` → `EASY`/`MEDIUM`/`HARD` | `InvalidDifficulty` |
| `tags` | split on `,`, trim, drop empty, keep order (duplicates kept); null → empty | none |
| `songsterrId` | null or `[0-9]+` that fits a `Long` (`"12.5"`, `"1e3"` rejected) | `InvalidSongsterrId` |
| `id` unique | after per-row checks, **every** valid row sharing an id is rejected (Q1) | `DuplicateId` |

The mapper matches exactly, with no case folding and no accent folding. Invalid rows are
rejected one by one and the rest are kept. The whole response fails only under Decision 3. A
valid envelope **replaces** the cache even when it holds zero songs, because the Sheet is the
authority (D-04). The rejections go into the `RefreshOutcome` and the startup log line, with id
and reasons. They are not persisted.

The mapper is a pure function, so it can be tested without Room or HTTP.

### Decision 5: Room cache

- **Database:** `BluesJamDatabase`, file `bluesjam-cache.db`, `version = 1`,
  `exportSchema = false`, `fallbackToDestructiveMigration(dropAllTables = true)`. Every table is
  a re-fetchable cache, so a schema bump simply refetches. That is also why no Room Gradle plugin
  and no schema directory are needed.
- **Why Room** over files or DataStore: it is already the decided stack (technical discovery,
  architecture skill). A Flow re-emits on a transactional replace. The jams and enrichment slices
  need per-row queries and joins by song id, and the assistant will need queries too.
- **Table `catalog_song`:**
  - `id` TEXT PK
  - `sheet_order` INT
  - `title`, `artist`, `default_key` TEXT
  - `tempo`, `difficulty` TEXT NULL, holding the enum `name`
  - `tags` TEXT, a JSON array through a kotlinx `TypeConverter`
  - `songsterr_id` INTEGER NULL
- **Table `sync_state`:**
  - `resource` TEXT PK (`"catalog"`; the jams slice adds `"jams"`)
  - `fetched_at` INTEGER NULL (epoch ms of the last success)
  - `attempted_at` INTEGER NULL
  - `failure` TEXT NULL (the kind, plus the code for `Service`)
- **DAO:**
  - `observeSongs()`, ordered by `sheet_order`.
  - `observeSyncState(resource)`.
  - `@Transaction replaceCatalog(songs, state)`, which deletes all rows, inserts the songs and
    upserts the state. Readers never see a partial catalog.
  - `recordFailure(resource, attemptedAt, failure)`, which leaves the songs untouched.
- **Repository:** `observeCatalog()` = `combine(observeSongs, observeSyncState, refreshing:
  StateFlow<Boolean>)`, mapped back to domain through the domain constructors. Its `onStart`
  triggers the background refresh when the cache is stale and the last attempt was more than
  60 s ago (Scenario 7). `refresh()` is single-flight: a `Mutex` plus a shared `Deferred`.
- **Desugaring:** `compileOptions.isCoreLibraryDesugaringEnabled = true` and
  `coreLibraryDesugaring(desugar_jdk_libs:2.1.5)` go in `configureAndroidCommon`, so every
  Android module and `:app` have it. That satisfies both rules in the architecture skill, lint
  `NewApi` per library and `checkDebugAarMetadata` in `:app`, and the next module to format a
  date (`:feature:next-jam`) is already covered. **This is a change to the architecture skill's
  wording, which says ":app and its own module".**

### Decision 6: Build convention `bluesjam.android.data` (new)

The plugin does the following:

- applies `bluesjam.android.library`, `org.jetbrains.kotlin.plugin.serialization` and
  `com.google.devtools.ksp`
- sets `testOptions.unitTests.isReturnDefaultValues = true`, which Room tests need for
  `ContextWrapper(null)`
- adds `testImplementation` junit, kotlinx-coroutines-test and turbine

The root `build.gradle.kts` declares `kotlin-serialization` and `ksp` with `apply false`.
`core/data/build.gradle.kts` uses `id("bluesjam.android.data")` and holds its own libraries:
`room-runtime`, `ksp(room-compiler)`, `okhttp`, `kotlinx-serialization-json`, the Koin BOM plus
`koin-core`, and for tests `mockwebserver3`, `sqlite-bundled-jvm` and the Koin BOM plus
`koin-test`. Its tests read `docs/api-samples` and `docs/sheet-seed`. So the module file adds
`tasks.withType<Test>().configureEach { inputs.dir(…).withPathSensitivity(RELATIVE);
systemProperty("bbbjam.rootDir", rootDir.absolutePath) }`, the same approach as `:konsist-test`.
Without it, editing a sample leaves the tests UP-TO-DATE.

## Expected File Changes

- `gradle/libs.versions.toml`: add these versions:
  - `ksp = "2.3.12"`
  - `room = "2.8.4"`
  - `okhttp = "5.1.0"`
  - `kotlinxSerialization = "1.9.0"`
  - `desugarJdkLibs = "2.1.5"`
  - `sqlite = "2.6.2"`

  Add the libraries `room-runtime`, `room-compiler`, `okhttp`, `okhttp-mockwebserver`
  (`mockwebserver3`), `kotlinx-serialization-json`, `desugar-jdk-libs`, `sqlite-bundled-jvm` and
  `koin-test`, and the plugins `kotlin-serialization` and `ksp`.
- `build.gradle.kts`: two `apply false` aliases.
- `build-logic/convention/build.gradle.kts`: register `androidData`.
- `…/AndroidDataConventionPlugin.kt`: new.
- `…/ProjectExtensions.kt`: desugaring.
- `…/AndroidApplicationConventionPlugin.kt`: `buildConfig`.
- `core/data/build.gradle.kts`: as in Decision 6.
- `core/data/src/main/kotlin/com/bbbjam/core/data/CoreDataMarker.kt`: **delete**.
- `core/data/src/main/kotlin/com/bbbjam/core/data/{AppsScriptEndpoint,DataFailure,Freshness}.kt`,
  `catalog/{CatalogRepository,CatalogSnapshot,RefreshOutcome,RejectedSong,CatalogMapper,DefaultCatalogRepository}.kt`,
  `remote/{Dtos,AppsScriptTransport,OkHttpAppsScriptTransport}.kt`,
  `cache/{BluesJamDatabase,CatalogSongEntity,SyncStateEntity,CatalogDao,Converters}.kt`,
  `di/DataModule.kt`: new. Splitting the files further is fine.
- `core/data/src/test/kotlin/com/bbbjam/core/data/…`: tests (see Verification).
- `app/build.gradle.kts`: `local.properties` read plus `buildConfigField`.
- `app/src/main/AndroidManifest.xml`: `INTERNET` permission.
- `app/src/main/java/com/bbbjam/{BluesJamApp,di/AppModule}.kt`: modify.
- `app/src/test/java/com/bbbjam/ModuleWiringTest.kt`: remove the `CoreDataMarker` import and line,
  and update its KDoc. The `JamStatus` line stays, so the test is kept.
- `core/model/src/test/kotlin/com/bbbjam/core/model/KeyTest.kt`: add `"B "` and `"Bm "` to the
  invalid list (inherited note).

## Visual Design Impact

UI involved: no. Freshness is data for a later screen.

## Durable Documentation Impact

- `.claude/skills/architecture/SKILL.md`: update Build Conventions with:
  - the 7th plugin, `bluesjam.android.data`
  - desugaring in every Android module
  - `buildConfig` in the application convention
  - the base URL path
  - the pins and their stdlib reasons
  - Room JVM tests on the bundled driver
  - the `:core:data` package layout
- `docs/apps-script-api.md` Transport: name the wiring (BuildConfig, `AppsScriptEndpoint`) and
  the client timeouts.
- `docs/sheet-schema.md` Mapper rules: duplicate ids reject every occurrence (Q1); optional-field
  handling (Q2).
- `docs/technical-discovery.md`: the data stack and versions; the testing bullet for the Room JVM
  driver.
- `docs/risks-and-open-questions.md`: mark "Seed as mapper fixtures" done for the catalog; move
  the jams items to S1.
- `PROGRESS.md` and `feature_list.json`: evidence.
- `AGENTS.md`: not needed.

## Implementation Tasks

1. Catalog entries, root aliases, the convention and the desugaring/buildConfig edits. Run
   `./gradlew help`.
2. Delete `CoreDataMarker` and its `ModuleWiringTest` line. Add the KeyTest cases.
3. DTOs, `CatalogMapper` and its tests.
4. Transport and MockWebServer tests.
5. Room entities, DAO, database and their tests on the bundled driver.
6. Repository, freshness and single-flight, with tests (fake transport, `MutableClock`, Turbine,
   `StandardTestDispatcher`).
7. `dataModule` and its Koin test. Then `:app`: BuildConfig, manifest, `appModule`, startup
   refresh.
8. `./gradlew ktlintFormat`, then `CI=true ./init.sh`. Demonstrate the `NewApi` failing case.
   Then the device check and the docs.

## Verification Plan

Everything runs on the JVM, inside `CI=true ./init.sh`, which must exit 0 with konsist (11
rules, or 12 with G2), detekt and ktlint all `wired`. There must be no baseline, no
`ignoreFailures`, no `@Suppress` and no rule disable. Tests:

- **`CatalogMapperTest`:**
  - `catalog-seed.json` gives 13 songs and 0 rejections.
  - **Seed CSV:** parse `docs/sheet-seed/Catalogo.csv` by header name. Fail if it contains `"`.
    Turn it into DTOs (trim, empty to null) and assert the same `MappedCatalog` as the JSON
    sample.
  - `catalog-edge.json` gives 3 songs, with the exact values: `Bm`, `SLOW`, tags
    `["slow blues","12 compases"]`, `MEDIUM`, `12345L`, and `42L` from the padded id. `sin-tono`
    is rejected with its three reasons.
  - One case per reason, including `rapido`, `Rápido`, `"B "` (trimmed, so valid) and `"B m"`.
  - Duplicate ids, where both rows are dropped.
  - Tags `" a,, b ,"` give `["a","b"]`.
  - `songsterrId` `"99999999999999999999"`, which overflows.
- **`EnvelopeTest`:** `error` → `Service`; HTML → `InvalidResponse`; `schemaVersion` 2; a missing
  `songs`; a missing key; a numeric value.
- **`OkHttpAppsScriptTransportTest` (MockWebServer):** the 302 to a second server; the query
  `resource=catalog`; a 500 gives `InvalidResponse`; a closed socket gives `Offline`; not
  configured makes no request.
- **`CatalogDaoTest` (bundled driver, in memory):** replace is atomic, with one emission per
  replace; ordering; `recordFailure` keeps the songs; a tags round-trip.
- **`DefaultCatalogRepositoryTest`:** Scenarios 1–5 and 7, including **"network down → cached
  catalog returned"** and freshness/`isStale` across a clock advance.
- **`DataModuleTest`:** `koinApplication` resolves `CatalogRepository` with `dataModule`, plus a
  test module for `Context` (`ContextWrapper(null)`), the in-memory database and the endpoint.
- **Failing cases:**
  - Turn desugaring off, and `:core:data:lintDebug` fails `NewApi`. Revert.
  - Run the G2 rule against a violating file, if G2 is approved.

**Manual device check (Pixel 5, outside the gate).** The device was not connected at planning
time, so this was not prototyped on a device. Steps, with
`ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"`:

1. Run `./gradlew :app:installDebug` with the URL in `local.properties`.
2. Run `$ADB logcat -c`, launch the app, and run `$ADB logcat -d -s BluesJam`. Expect `catalog
   refresh: updated N songs, R rejected` (N about 100 today). Check that the crash buffer is
   empty.
3. Pull the database with `$ADB exec-out run-as com.bbbjam cat databases/bluesjam-cache.db >
   cache.db`, plus the `-wal`/`-shm` files if present, or force-stop first. Count the rows in
   `catalog_song` and check that `sync_state.fetched_at` is set. Use Python `sqlite3` on the PC.
4. Run `$ADB shell cmd connectivity airplane-mode enable` (or switch Wi-Fi and data off by
   hand), then `am force-stop com.bbbjam` and relaunch. Expect `catalog refresh: failed Offline`.
   The pulled database must still have N rows, with the same `fetched_at` and `failure` =
   `Offline`.
5. Re-enable the network.
6. Record cold start and logcat in `PROGRESS.md`. Never paste the URL.

## Evidence To Capture

- The `init.sh` tail, with the three `wired` lines.
- The new test result files and their counts. The other suites must be unchanged: 20 baseline
  files.
- The `NewApi` failing case.
- `:app:dependencies` showing `kotlin-stdlib` at 2.2.10, OkHttp at 5.1.0 and serialization at
  1.9.0.
- The device log lines and the database row counts, online and offline.

## Validator Checklist

- [ ] Only `:core:data` imports `okhttp3`, `androidx.room` and `kotlinx.serialization`.
      Repositories are `single`, there is no `KoinComponent`, and the code reads no clock outside
      the injected `Clock`.
- [ ] Every module build file still applies only `bluesjam.*` ids. KSP and serialization live in
      the convention.
- [ ] No URL appears in a tracked file, a log or `PROGRESS.md`. `git grep -E 'script\.google\.com/macros'` is empty.
- [ ] Every catalog Mapper rule is covered by a test. Invalid rows are rejected and the rest are
      kept. Whole-response failures leave the cache intact.
- [ ] The seed CSV and both JSON samples are fixtures. The samples are test inputs.
- [ ] Freshness is exposed: `fetchedAt`, `lastFailure`, `isRefreshing`, `isStale(now)`.
- [ ] `CoreDataMarker` is gone. `KeyTest` has the trailing-space keys.
- [ ] The docs listed above are updated. S1 is reported to the orchestrator.

## Needs The User's Approval

- **New dependencies** (D1):
  - Main code: KSP plugin 2.3.12, Room 2.8.4, OkHttp 5.1.0, kotlinx-serialization 1.9.0 plus
    its plugin (Kotlin 2.2.10) and desugar_jdk_libs 2.1.5.
  - Test only: mockwebserver3 5.1.0, sqlite-bundled-jvm 2.6.2 and koin-test (BOM 4.1.1).
- **Gate and build changes:**
  - G1: a 7th convention plugin, plus desugaring and `buildConfig` in the existing conventions.
    No Konsist rule is weakened.
  - G2, optional and recommended: a 12th Konsist rule, `data-libraries-only-in-core-data`. No
    file outside `:core:data` may import `okhttp3.`, `androidx.room.` or
    `kotlinx.serialization.` (D-13: Sheet I/O lives only in repositories). Its failing case must
    be demonstrated.
- **Q1:** duplicate `Catalogo.id`. Reject every occurrence (recommended: the mapper does not
  guess, and setlists fall back to the tab copy), or keep the first.
- **Q2:** an invalid optional field (`tempo`, `difficulty`, `songsterrId`). Reject the row, as
  `sheet-schema.md` says today and as this spec recommends. Or, under D-20, drop the field to null
  and keep the song. Rejection can hide a song over an unused field, and no admin surface shows
  rejections yet, only the log.
- **Q3:** staleness threshold 30 min, automatic retry no sooner than 60 s, and a refresh on every
  process start (about one Apps Script call per launch, well under 30 concurrent executions).
- **S1:** the split above.

## Risks

- The pins hold the stdlib at 2.2.10, so a later Kotlin upgrade must move OkHttp, serialization
  and Koin together.
- KSP 2.3.x is decoupled from the Kotlin version. A future KSP may require a newer KGP.
- The Room JVM tests use the bundled SQLite, not Android's framework SQLite. The SQL dialect is
  the same, but the device check is the only proof of the framework path.
- `sqlite-bundled-jvm` loads a native library. It works on Windows x64 here, and the published
  jar also ships Linux and macOS libraries.
- An enum cell typed in decomposed Unicode (NFD) would fail the exact match. Sheets normally
  stores NFC. Not handled.
- The cold-start latency is unmeasured. The 45 s call timeout is an estimate.
- The device path (`INTERNET`, Koin `Context`, desugared `Clock` on API 34) was not run on a
  device during planning.
