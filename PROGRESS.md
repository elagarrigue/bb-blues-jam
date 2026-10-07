# Progress Log

## Current Verified State

- Repository root: `C:/Users/Emmanuel/AndroidStudioProjects/BBBluesJam`
- Standard startup path: `./init.sh`
- Standard verification path: `CI=true ./init.sh`, which wraps `./gradlew build` and
  `./gradlew check`
- Current state: twenty-nine of 39 slices accepted, the latest `admin-add-song-to-setlist` (6 October
  2026). `admin-remove-song-from-setlist` and `admin-set-key` are both `in_progress`: each Part A
  (server half and `:core:data`) done and self-verified (sessions 074 and 075), in one `Post.js`
  that is **not deployed**; remove-song's Part B (UI) and set-key's Part B (UI) also done and
  self-verified (sessions 076 and 077). `live-refresh-during-jam` (needs no deploy) is `passing`
  since session 078, awaiting validation; **three features were `in_progress` at once** during that
  session (the two awaiting the deploy, by user-approved exception, plus this one).
  Next: the user's **one** batched paste and **New version** (user decision B1 (a); README
  "Redeploy for remove song and set key, in one paste"), then remove-song's live checks LR1–LR5 and
  validation, then set-key's live checks and validation.
- **User to-do, non-blocking:** delete the `2026-10-31` test jam (the `Jams` row and its tab).
- Current blocker: none. The script is deployed (user, 1 October 2026); its `/exec` URL is in the
  git-ignored `local.properties`. The seed was imported into the real Sheet and reviewed by hand by
  the user (29–30 September 2026, the user's report; no agent can read the Sheet).
- Last verified at: 7 October 2026 (session 078, `live-refresh-during-jam`, `passing`) —
  `CI=true ./init.sh` exit 0, `konsist: wired` (17/17, unchanged), `detekt: wired`,
  `ktlint: wired`; 92 result files, 576 tests, 0 failures. Pixel 5: device steps 1–6 of the spec;
  see session 078. Before that, 6 October 2026 (session 077, `admin-set-key` Part B, `in_progress`) —
  `CI=true ./init.sh` exit 0, `konsist: wired` (17/17, unchanged), `detekt: wired`,
  `ktlint: wired`; 87 result files, 538 tests, 0 failures. Pixel 5: picker, rotation, font scale
  2.0, `AccessRefused` revert and card, musician view; no DataStore dir before or after; settings
  restored. Before that, session 076 ( `admin-remove-song-from-setlist` Part B,
  `in_progress`) — `CI=true ./init.sh` exit 0, `konsist: wired` (17/17, unchanged), `detekt: wired`,
  `ktlint: wired`; 84 result files, 519 tests, 0 failures. Pixel 5: confirmation, rotation,
  `AccessRefused` card, no DataStore dir before or after; settings restored. Before that, session
  075 (`admin-set-key` Part A, `in_progress`) —
  `CI=true ./init.sh` exit 0, `konsist: wired` (17/17, unchanged), `detekt: wired`,
  `ktlint: wired`; 83 result files, 509 tests, 0 failures; Node 132/132. `src/Post.js` SHA-1
  `be205ea3f5d5081e5896666d3490ae30cab06119` (both Part A halves), **not deployed** (the deployed one is
  still `94bb9154…`). Before that, session 074 (`admin-remove-song-from-setlist` Part A,
  `in_progress`) — `CI=true ./init.sh` exit 0, `konsist: wired` (17/17, unchanged), `detekt: wired`,
  `ktlint: wired`; 83 result files, 498 tests, 0 failures; Node 124/124. `src/Post.js` SHA-1
  `faf0fd85d53ce9277b3882e6573fedfe6ad64256`, **not deployed** (the deployed one is still
  `94bb9154…`). Before that, session 073 (`admin-add-song-to-setlist` Parts A and B,
  `passing`) — `CI=true ./init.sh` exit 0, `konsist: wired` (17/17, unchanged), `detekt: wired`,
  `ktlint: wired`; 83 result files, 489 tests, 0 failures; Node 113/113. Deployed Post.js SHA-1
  `94bb91548469d3f9c6c868788f09d3d2684c5cf1`; live checks L1–L6 as expected. Pixel 5: admin view,
  picker, `AccessRefused` card, demo draft, musician view; settings restored. Before that, session
  072 (Part A): 78 result files, 464 tests.
  Before that, 5 October 2026 (session 070, `debug-admin-session`, since accepted) — `CI=true ./init.sh` exit 0, `konsist: wired` (17/17, unchanged), `detekt: wired`,
  `ktlint: wired`; 75 result files, 433 tests, 0 failures (new `DebugAdminSessionTest` 6). Release
  dex has no `DebugAdminSession`. Pixel 5: `Modo admin activo` with no login, flag left on. Before
  that, session 069 (`admin-passphrase-login`, since accepted) —
  `CI=true ./init.sh` exit 0, `konsist: wired` (17/17, unchanged), `detekt: wired`,
  `ktlint: wired`; 74 result files, 427 tests, 0 failures; Node 83/83. Before that, session 068
  (`past-jam-detail`, since accepted)
  — `CI=true ./init.sh` exit 0, `konsist: wired` (17/17, unchanged), `detekt: wired`,
  `ktlint: wired`; 70 result files, 397 tests, 0 failures (`AppRoutesTest` 6 → 9,
  `PastJamsPresenterTest` 5 → 6, `PastJamsStatesTest` 12 → 14, new `PastJamDetailMappingTest` 9,
  `PastJamDetailPresenterTest` 2, `PastJamDetailModelShapeTest` 3, `PastJamDetailDefaultsTest` 3).
  Pixel 5: Anteriores, then the 2026-07-25 detail with 13 rows and amber keys; back paths and
  rotation. Before that, session 067 (`unpublished-setlist-state`, since accepted) — `CI=true ./init.sh` exit 0, `konsist: wired` (17/17, unchanged), `detekt: wired`,
  `ktlint: wired`; 66 result files, 374 tests, 0 failures (`JamTest` 6 → 10, `NextJamPresenterTest`
  17 → 16, `NextJamStatesTest` 9 → 11, `SongDetailMappingTest` 7 → 8, `DemoUpcomingJamRepositoryTest`
  6 → 8, new `NextJamDraftTest` 4 and `DraftSetlistDefaultsTest` 3). Node 70/70. Pixel 5: the draft
  card on the draft demo jam. Before that, session 066 (`bottom-navigation`) — `CI=true ./init.sh`
  exit 0, `konsist: wired` (17/17, unchanged), `detekt: wired`,
  `ktlint: wired`; 64 result files, 359 tests, 0 failures (`ContrastTest` 14 → 15, `AppRoutesTest`
  5 → 6, new `AppTabTest` 4 and `TabBarDefaultsTest` 3). Pixel 5: per-tab state across tab
  switches, the song detail, rotation and process death. Before that, session 065
  (`past-jams-list`) —
  `CI=true ./init.sh` exit 0, `konsist: wired` (17/17, unchanged), `detekt: wired`, `ktlint: wired`;
  62 result files, 350 tests, 0 failures (the 57 previous files unchanged except `ContrastTest`
  13 → 14, plus `SpanishDateNamesTest` 2, `PastJamsStatesTest` 12, `PastJamsPresenterTest` 5,
  `PastJamsDefaultsTest` 2, `PastJamsModuleTest` 1). Pixel 5: Anteriores shows the 2026-07-25 row;
  offline relaunch shows the staleness notice above it. Before that, session 064
  (`debug-demo-upcoming-jam`) — `CI=true ./init.sh`
  exit 0, `konsist: wired` (17/17, unchanged), `detekt: wired`, `ktlint: wired`; 57 result files,
  327 tests, 0 failures (the 56 previous files plus `DemoUpcomingJamRepositoryTest` 6).
  `:app:assembleRelease` exit 0 with no demo class in the release dex. Pixel 5: the demo jam on
  Próxima jam and its song detail with the flag on, the Sheet's state with it off. Before that,
  4 October 2026 (session 063, `song-detail-screen`) — `CI=true ./init.sh` exit
  0, `konsist: wired` (17/17, new `navigation-only-in-app`), `detekt: wired`, `ktlint: wired`; 56
  result files, 321 tests, 0 failures. Navigation Compose 2.9.8 added to `:app` with no resolved
  version moving. Pixel 5 (5 October, temporary jam 2026-10-31): detail opened from an expanded
  row with a filter and a scroll set; key the tallest node, 48dp "Volver", one "Tonalidad A" node;
  Volver, system back, fast double taps and rotation all return an identical list; "Bm" on one line
  at font scale 2.0; settings restored, empty crash buffer. Before that, session 062 (`list-states`) — `CI=true ./init.sh` exit 0,
  `konsist: wired` (16/16, new `no-dp-literal-outside-core-ui`), `detekt: wired`, `ktlint: wired`;
  49 result files, 290 tests, 0 failures. Pixel 5: offline first launch shows the error block,
  Retry online shows the skeleton then the no-upcoming block, offline with cache shows the
  staleness notice; empty crash buffer, every setting restored. Before that, session 061
  (`instrument-filter-chips`) — `CI=true ./init.sh`
  exit 0, `konsist: wired` (15/15, unchanged), `detekt: wired`, `ktlint: wired`; 46 result files,
  263 tests, 0 failures: the 43 previous files unchanged except `ContrastTest` 9 → 11, plus
  `InstrumentFilterMapperTest` 8, `InstrumentFilterDefaultsTest` 3 and `NextJamFilterTest` 10. Lint
  `:core:ui` and `:feature:next-jam` no issues, `:app` the same 14 warnings. Pixel 5: cold start
  636 ms, `jams cache: upcoming 2026-10-31, past 1, songs 26`, spec step C done (session 061),
  empty crash buffer, every setting restored. Before that, 3 October 2026 (session 057,
  `song-row-expansion`) — `CI=true ./init.sh` exit 0,
  `konsist: wired` (15/15, unchanged), `detekt: wired`, `ktlint: wired`; 43 result files, 240 tests,
  0 failures: the 40 previous files unchanged except `ContrastTest` 8 → 9 and `NextJamPresenterTest`
  7 → 11, plus `LineupPanelMapperTest` 7, `LineupPanelDefaultsTest` 3 and `ExpandedRowsTest` 5. Lint
  `:core:ui` and `:feature:next-jam` no issues, `:app` the same 14 warnings. Pixel 5: installed, cold
  start 609 ms, empty crash buffer and AndroidRuntime log, `jams cache: upcoming none`, so only the
  no-upcoming line is drawn (no row to expand). Before that, 2 October 2026 (session 052,
  `instrument-strip-component`) — `CI=true
  ./init.sh` exit 0, `konsist: wired` (15/15, unchanged), `detekt: wired`, `ktlint: wired`; 40
  result files, 220 tests, 0 failures: the 38 previous files unchanged except `ContrastTest` 6 → 8
  and `NextJamPresenterTest` 6 → 7, plus `LineupChipsTest` 9 and `InstrumentStripDefaultsTest` 5.
  Lint `:core:ui` and `:feature:next-jam` no issues, `:app` the same 14 warnings. Pixel 5: installed,
  cold start 912 ms, empty crash buffer and AndroidRuntime log, `jams cache: upcoming none`, so only
  the no-upcoming line is drawn (no strip to check). Before that, session 048
  (`next-jam-read-only-list`, JVM only) —
  `CI=true ./init.sh` exit 0, `konsist: wired` (15/15, new `no-material-theme-outside-core-ui` and
  `amber-roles-allowlisted`), `detekt: wired`, `ktlint: wired`; 38 result files, 0 failures: the 35
  baseline files unchanged except `ModuleIsolationTest` 13 → 15, plus `JamDateTextTest` 7,
  `NextJamPresenterTest` 6, `NextJamModuleTest` 1. Lint: `:feature:next-jam` no issues, `:app` the
  same 14 pin warnings. No device run (Pixel 5 absent from `adb devices`). Before that, session 045
  (`jams-repository-cache`) — `CI=true ./init.sh`
  exit 0, `konsist: wired` (13/13, new `data-libraries-only-in-core-data-qualified`), `detekt:
  wired`, `ktlint: wired`; 35 result files, 0 failures (the 29 baseline files unchanged except
  `JamTest` 4 → 6, `DefaultCatalogRepositoryTest` 14 → 17, `ModuleIsolationTest` 12 → 13, plus six
  new classes). Pixel 5 (API 34): upgrade over the accepted v1 build, cold start 797 ms, `jams
  refresh: updated 1 jams …`, `jams cache: upcoming none, past 1, songs 13 (13 from catalog)`,
  database `user_version` 2; offline (airplane mode, restored to 0) both refreshes `failed Offline`,
  same rows and `fetched_at`. Before that, session 042 (`catalog-repository-cache`, JVM only) —
  `CI=true ./init.sh` exit 0, `konsist: wired` (12/12, new `data-libraries-only-in-core-data`),
  `detekt: wired`, `ktlint: wired`; 29 result files, 0 failures: the 20 baseline files unchanged
  except `ModuleIsolationTest` 11 → 12, plus nine new `:core:data` classes (58 tests). No device
  run. Before that, 1 October 2026 (session 039, `apps-script-jams-read-endpoint` repo half) —
  `node --test backend/apps-script/test/*.test.js` 68/68 (outside the gate); `CI=true ./init.sh`
  exit 0, three `wired`, the 20 test result files identical to the baseline. Before that, session 036 — live endpoint: checker OK on 100 songs,
  `config` → `unknown_resource`, warm read median ~2.5 s. Session 035 (`apps-script-read-endpoint` repo half) —
  `CI=true ./init.sh` exit 0, three `wired`, the 20 test result files identical in names and
  counts to the baseline; `node --test backend/apps-script/test/*.test.js` 33/33 (outside the
  gate). Before that, 30 September 2026 (session 033, `build-logic-conventions`) — `CI=true ./init.sh`
  exit 0, `konsist: wired` (11/11, new `build-file-applies-convention`), `detekt: wired`,
  `ktlint: wired`; every other suite unchanged (20 result files, 0 failures). Task plans,
  `buildEnvironment`, manifests, dependency sets and the debug APK (392 non-`META-INF` entries by
  SHA-1) identical to HEAD `095c88b`. Pixel 5 (API 34): cold start 872 ms, `Status: ok`, empty
  crash buffer and AndroidRuntime log, Info drawn unchanged.
  Before that, session 030 — `CI=true ./init.sh` exit 0, `konsist: wired`
  (10/10, now checking the real `:feature:info`), `detekt: wired`, `ktlint: wired`; new
  `InfoPresenterTest` (4) and `InfoModuleTest` (1), all other counts as below, 0 failures. Pixel 5
  (API 34): cold start 760 ms, empty crash buffer and AndroidRuntime logcat, Info drawn from the
  tokens (background `#111318`, no amber), each link starts an `ACTION_VIEW` for its host (Chrome for
  Instagram and Linktree, the YouTube app for YouTube) and back returns to Info; the admin notice
  appears. Link `onClickLabel`s are not verified on device (see session 030).
  Session 028 (docs and one KDoc sentence only) kept the gate green. Before that, 29 September 2026 (session 024) — `CI=true ./init.sh` exit 0; it prints
  `konsist: wired` (10 Konsist tests, 0 failures), `detekt: wired`, `ktlint: wired`; `:core:model`
  runs 30 tests in 9 classes on the JVM, 0 failures; `:core:ui` runs `EventHandlerTest` (6),
  `SamplePresenterTest` (3), `BluesJamColorsTest` (3), `BluesJamTypographyTest` (2),
  `ContrastTest` (6) and `WindowBackgroundTest` (1), 0 failures. Last launch check on the Pixel 5
  (API 34) was session 021: cold start, empty AndroidRuntime logcat, background `#111318`, label
  Barlow Condensed Bold. The display runs in Display P3, so saturated colors in screenshots read
  as their P3 encoding (amber `#FFB300` → `#F4B63F`). Session 024 changed nothing visible.

### What exists

Six product modules: `:app`, `:core:model`, `:core:ui`, `:core:data` (`module-skeleton`,
`accepted`), `:feature:info` (`info-screen`, `accepted`) and `:feature:next-jam`
(`next-jam-read-only-list`, `accepted`).
`:core:model` is a Kotlin JVM module with no Android; `:core:ui` and `:core:data` are Android
libraries (`com.android.library`, built-in Kotlin) that each `api`-depend on `:core:model`; `:app`
depends on all three. `:app`'s `ModuleWiringTest` proves `:core:model` is visible from `:app`
(through `JamStatus`); the `:core:data` marker is gone (session 042).

`:core:data` (`catalog-repository-cache`, `accepted`, convention `bluesjam.android.data`) reads
the catalog: `OkHttpAppsScriptTransport` (OkHttp 5.1.0) → `AppsScriptEnvelope`
(kotlinx-serialization 1.9.0) → `CatalogMapper` (the catalog Mapper rules, Q1 duplicates reject
every copy, Q2 a bad tempo/difficulty/songsterrId is dropped and the song kept) → Room 2.8.4
(`bluesjam-cache.db`, tables `catalog_song` and `sync_state`). `CatalogRepository` is
cache-first: `observeCatalog()` emits the cached songs with `Freshness` (`fetchedAt`,
`lastFailure`, `isRefreshing`, `isStale(now)`: stale after 30 min or a failure) and refreshes in
the background when stale, never within 60 s of the last attempt; `refresh()` is single-flight. A
failure (`NotConfigured`, `Offline`, `Service(code)`, `InvalidResponse`) never touches the cache.
`:app` reads `bluesjam.appsScriptUrl` from the git-ignored `local.properties` into
`BuildConfig.APPS_SCRIPT_URL`, binds `AppsScriptEndpoint` in `appModule`, starts `dataModule`,
refreshes once per process start and logs `catalog refresh: …` (tag `BluesJam`, never the URL).
The manifest now has `INTERNET`. Core library desugaring is on in every Android module.

`:core:data` also reads the jams (`jams-repository-cache`, `passing`): `GET ?resource=jams` →
`JamDto` → `JamsMapper` (the `Jams` rows: P2 a shared `fecha` rejects every such row, P8 `hora`
and `lugar` required) and `SetlistMapper` (one tab: P4 a bad row is dropped and reported, the rest
stay available, positions never renumbered; P1 `Juan(saxo)` valid, a malformed `Otros` entry
dropped alone) → Room v2 (`jam`, `jam_song`, `jam_slot` with the slot's column index, `jam_extra`,
foreign keys CASCADE, and the view `jam_song_resolved`, which resolves titles against the cached
catalog at read time). `JamsRepository.observeJams()` emits `JamsSnapshot(upcoming, past,
freshness)`: today comes from `JamCalendar` in Buenos Aires (P7), the earliest future jam is
upcoming and later ones are held back (P5), past jams are newest first with only filled slots (P6).
Both repositories now turn a refused write into `DataFailure.Storage` and a mapper exception into
`InvalidResponse("mapping: …")`, and `DataScope` and `BluesJamApp`'s scope log an escaped exception
instead of crashing. `BluesJamApp` refreshes catalog and jams concurrently and logs `jams refresh:
…` and `jams cache: …` (counts and dates only).

`:core:model` holds the domain types in `com.bbbjam.core.model` (`domain-model-types`, `accepted`):
`Jam` (date as identity, `setlist: Setlist`, `isHistorical(today)` with the date from the caller; a
published jam is never withheld), `Setlist` (`Available(songs, droppedRows)` with positions ≥ 1,
unique and ascending, gaps allowed, never empty when rows were dropped; `Withheld`;
`Unavailable(SetlistProblem)`, set by `jams-repository-cache`), `JamStatus` (`DRAFT`, `PUBLISHED` only), `JamSong` (position ≥ 1, `songId`,
`title`, `artist`, `key`, `lineup`, `extraParticipants` default empty), `Lineup` (`openSlots`,
`hasOpenSlotFor`, `default()` = seven open slots in Sheet column order; never more of an instrument
than the default, zero allowed — D-18), `Slot` (open when `musicianName` is null; blank names
rejected), `ExtraParticipant` (name and free-text instrument, both non-blank, no `;`, `(`, `)`;
never open), `Instrument` (six values, `KEYBOARDS` not `KEYS`), `Song` (optional catalog and
enrichment fields default to null), `Tempo`, `Difficulty`, and the validated value classes `Key`
(schema **Keys** format, `isMinor`, `parseOrNull`) and `SongId` (lowercase slug, `parseOrNull`).
Pure Kotlin with java.time; no serialization, no date parsing, no clock (Konsist
`core-model-no-system-clock`). Invalid values throw `IllegalArgumentException` naming the value.

`:core:ui` holds the presenter contracts of D-02 in `com.bbbjam.core.ui.presenter`: `UiModel`,
`UiEvent`, `Presenter` and `EventHandler` (`equals` and `hashCode` both from `key`, `operator invoke`
declared) (`molecule-presenter-harness`, `accepted`). It applies the Compose compiler plugin and
exposes the Compose BOM, `androidx.compose.runtime:runtime`, `ui` and `material3` (1.3.2) as `api`.

`:core:ui` also holds the design system in `com.bbbjam.core.ui.theme` (`design-tokens-theme`,
`passing`; D-17): `BluesJamTheme { }` wraps `MaterialTheme` with every dark-scheme role, the type
scale and the shapes mapped from the `DESIGN.md` tokens, and provides `LocalContentColor` = `text`.
Screens read `BluesJamTheme.colors` / `.typography` / `.shapes` / `.spacing`. The palette is
`internal`; amber is public only as `primaryAction`, `slotOpen`, `key`, `published`,
`activeFilter` (plus their `on…` colors). Fonts are bundled static TTFs (Barlow Condensed
SemiBold/Bold/ExtraBold, Chivo Regular) with their OFL texts in `assets/licenses`. The only XML
color is `bluesjam_window_background` in `core/ui/src/main/res/values/colors.xml`, guarded by
`WindowBackgroundTest`. `ThemeShowcase` (public, with a `@Preview`) draws every role and style.
Its test sources hold a sample presenter with no domain types, `SamplePresenterTest` (Molecule
2.2.0 + Turbine 1.2.1 + coroutines-test 1.10.2, asserting transitions after events) and
`EventHandlerTest`. Unit tests set `isReturnDefaultValues = true` because the Android Compose
runtime calls `android.os.Trace`; every presenter module needs the same (architecture skill).
`compileSdk` and `minSdk` come from the catalog. Build conventions are recorded in `.claude/skills/architecture/SKILL.md`.

Module build setup lives in `build-logic/` (`build-logic-conventions`, `accepted`): an included build
(`includeBuild("build-logic")` in `pluginManagement`) whose `:convention` subproject holds six binary
plugins in `com.bbbjam.buildlogic` — `bluesjam.jvm.library` (`:core:model`, `:konsist-test`),
`bluesjam.android.library` (`:core:data`), `bluesjam.android.application` + `bluesjam.android.compose`
(`:app`), `bluesjam.android.presenter` (`:core:ui`) and `bluesjam.android.feature` (`:feature:info`).
They set SDK levels (`compileSdk`, `minSdk`, and now `targetSdk` from the catalog), Java 11,
Compose, `isReturnDefaultValues`, the presenter test libraries and Koin for features. Module files
keep only `id("bluesjam.…")`, namespace, `:app`'s identity and build types, and their own
dependencies. The catalog is shared from `gradle/libs.versions.toml` (new `targetSdk`,
`android-gradlePlugin`, `kotlin-gradlePlugin`); detekt and ktlint are still applied from the root
`subprojects {}` block and do not lint `build-logic` itself.

A test-only module `:konsist-test` (`bluesjam.jvm.library`, no project dependency) holds
`ModuleIsolationTest`: 15 Konsist 0.17.3 tests (12th `data-libraries-only-in-core-data`, 13th
`data-libraries-only-in-core-data-qualified`, 14th `no-material-theme-outside-core-ui`, 15th
`amber-roles-allowlisted` with its per-module `AMBER_ROLE_ALLOWLIST`, today only
`feature/next-jam` → `key`) that enforce the architecture skill's dependency rules
(no feature→feature or feature→`:app` imports, `:core:*` import allowlist, no Android in
`:core:model`, no system clock in `:core:model`, no ViewModel, package roots `com.bbbjam.<module path>` without hyphens, allowed
`project(":…")` dependencies in `core/*`/`feature/*` build files, every module build file applying
its `bluesjam.*` convention with no raw plugin and no convention-owned setting, and no color literal
outside `:core:ui`). Module groups are read from paths,
so the first `:feature:*` module is covered without editing the suite. The test task declares every
`.kt`/`.kts` file as an input, so a change elsewhere reruns it (`konsist-isolation-rules`, `accepted`).

`:app` is on a Kotlin + Jetpack Compose baseline (`gradle-kotlin-compose-baseline`,
`accepted`). AGP 9.4.1 with its built-in Kotlin (no `org.jetbrains.kotlin.android`
plugin), Kotlin/KGP 2.2.10, the Compose compiler plugin `org.jetbrains.kotlin.plugin.compose` on the
same `kotlin` catalog key, Compose BOM 2025.09.00 (ui, foundation, ui-tooling-preview, ui-tooling for
debug only) and activity-compose 1.11.0. appcompat and Material Views are gone; Material 3 arrives
through `:core:ui`.

`MainActivity` (a `ComponentActivity`, the launcher) turns on edge-to-edge with dark system bars and
shows `TemporaryTabs` inside `BluesJamTheme`: Próxima jam (first) or Info above a plain two-cell tab
row (`selectable`, `Role.Tab`, 48dp, `text`/`textMuted`, no amber), each screen with the status-bar
insets as `contentPadding`; `bottom-navigation` deletes it. `BluesJamApp` (the manifest's
`android:name`) calls `startKoin` with `appModule`, `dataModule`, `infoModule` and `nextJamModule`; `appModule` binds `ExternalLinkOpener` to `IntentLinkOpener`. Koin is
4.1.1 (BOM); kotlin-stdlib stays 2.2.10 and Compose 1.9.1 on `:app`'s runtime classpath. `:app` has
no colors of its own: its `colors.xml` is gone, and the window
theme `Theme.BBBluesJam` (parent `android:Theme.Material.NoActionBar`) uses
`@color/bluesjam_window_background` from `:core:ui`. Dark only: `values-night` was deleted.

`./gradlew check` runs unit tests, Android lint, the Konsist suite, detekt 2.0.0-alpha.6 and ktlint
1.8.0 (ktlint-gradle 14.2.0). The root `build.gradle.kts` applies both tools to every module that
applies `kotlin-jvm` or an Android plugin, so a new module gets them with no build code; config is in
`.editorconfig` (`android_studio` style, Composable naming exception) and `config/detekt/detekt.yml`;
no baseline file. `init.sh` prints `konsist: wired` only when `:konsist-test:test` exists and its
results hold at least one test, and `detekt`/`ktlint: wired` only when `check --dry-run` schedules
the tool's task in every module that compiles Kotlin; otherwise it names the modules missing it.
The unit tests are the two template tests plus the nine `:core:model` classes (`ExtraParticipantTest`,
`JamSongTest`, `JamStatusTest`, `JamTest`, `KeyTest`, `LineupTest`, `SlotTest`, `SongIdTest`,
`SongTest`), `ModuleWiringTest`, `EventHandlerTest`, `SamplePresenterTest`, `BluesJamColorsTest`, `BluesJamTypographyTest`,
`ContrastTest`, `WindowBackgroundTest`, `LineupChipsTest`, `InstrumentStripDefaultsTest`, `LineupPanelMapperTest`, `LineupPanelDefaultsTest`, `:feature:info`'s `InfoPresenterTest` and `InfoModuleTest`,
`InstrumentFilterMapperTest`, `InstrumentFilterDefaultsTest`,
`:feature:next-jam`'s `JamDateTextTest`, `NextJamPresenterTest`, `NextJamFilterTest`, `ExpandedRowsTest` and `NextJamModuleTest`, and the
`:core:data` classes.

`:feature:info` (`info-screen`, `accepted`) is the first feature module and the template for the
others: `InfoPresenter` (`Presenter<InfoUiModel, Unit>`, constructor `ExternalLinkOpener` from
`:core:ui`), `InfoUiModel` with `OpenLink`/`DismissLinkError`/`AdminEntryTapped`, the approved copy
in `internal object InfoCopy`, `SocialLink` (three URLs), `InfoScreen` (renders only,
`koinInject()` default) and `infoModule`. It shows who organizes the jam, the jam, Hideaway, how to
join, three social links and "Entrar como admin", which only shows "El ingreso de admin todavía no
está habilitado." until `admin-passphrase-login`. No venue (D-19), no station, no amber.

`:feature:next-jam` (`next-jam-read-only-list`, `accepted`) is the first feature that reads data
(project deps `:core:ui` and `:core:data`): `NextJamPresenter(JamsRepository, JamCalendar)` maps
`observeJams().upcoming` to `NextJamUiModel` (`Loading` until something was ever fetched,
`NoUpcomingJam`, or `Jam(header, setlist)` with `Songs(rows, droppedRowsNote)` or `NotShown` for a
withheld or unavailable setlist). The header is "Sábado 31 de octubre · 21:00", the venue as
written in `lugar`, and the time remaining ("Esta noche" from 18:00 on its own date, "Hoy",
"Mañana", "En n días") from `JamCalendar.today()` read once per snapshot. Rows show the zero-padded
position, the title and the key in `typography.key` / `colors.key` (content description
"Tonalidad <key>"). Copy in `NextJamCopy` (approved C1), day and month names hand-written.
Under that line each row draws the instrument strip (`instrument-strip-component`, `accepted`):
`SongRowUiModel.instruments` comes from `lineup.toInstrumentChips(extraParticipants)` in the
presenter; the screen calls `InstrumentStrip`. `:feature:next-jam` still reads only the amber `key`.
Rows expand in place (`song-row-expansion`, `in_progress`): the presenter holds one
`ExpandedRows(jamDate, positions)` in `rememberSaveable` (keyed by the Sheet's position, scoped to
the jam date, never recreated) and passes it into `toUiModel(today, expanded, onToggle)`; each
`SongRowUiModel` adds `artist`, `isExpanded`, `stateDescription` ("expandido"/"contraído"),
`toggleLabel` ("ver los cupos"/"ocultar los cupos"), `lineup: LineupPanelUiModel` and `events`
(`ToggleExpanded`). The screen's header (title line with `ExpandIndicator`, then the strip collapsed
or the artist expanded) is the only clickable part (`Role.Button`, `onClickLabel`,
`stateDescription`); the `LineupPanel` sits below it, and the row animates its size.

`:core:ui` holds the instrument strip in `com.bbbjam.core.ui.strip` (`instrument-strip-component`,
`in_progress`): `InstrumentChipUiModel(label, contentDescription, kind)` with `InstrumentChipKind`
`OPEN_SLOT`/`FILLED_SLOT`/`EXTRA` (`isOpen` only for an open slot), the pure mapper
`Lineup.toInstrumentChips(extras)` (slots in Sheet column order, then the extras in `Otros` order),
the approved copy in `internal object InstrumentStripCopy` (`GTR: LIBRE` / `Gtr: Tincho` /
`+ saxo: Juan`; "Guitarra: libre" / "Guitarra: Tincho" / "Otros: saxo, Juan"),
`InstrumentStripDefaults` (`OPEN_FILL_ALPHA` 0.15, `DOT_TO_EM` 0.5, `style(kind)`), and the
`InstrumentStrip(chips)` composable: a `FlowRow` of non-interactive chips, one semantics node each,
static dot, `Icons.Filled.Check` (from `material-icons-core`, now declared explicitly), glyph sizes
from the caption font size, four `@Preview`s.

Próxima jam filters by instrument (`instrument-filter-chips`, `passing`): under the header,
`SetlistUiModel.Songs.filterBar` (null for a setlist with no song) draws `InstrumentFilterBar`: the
heading "Filtrá por cupo libre", `Todos` and six checkbox chips with per-instrument counts, then the
count line ("3 de 13 temas con cupo libre para bajo o voz") or the no-results block with "Ver todos
los temas". Multi-select with OR; the presenter holds a `Set<Instrument>` in
`rememberSaveable(InstrumentFilterSaver)`, never recreated and not tied to the jam date, applies
the `InstrumentFilterChange`s its handlers send, and filters rows with `matchesInstrumentFilter`;
expansion is untouched. `:core:ui` `com.bbbjam.core.ui.filter` holds the component, its `UiModel`s,
the mapper, `InstrumentFilterCopy` and `InstrumentFilterDefaults` (the only `activeFilter` reader).

`:core:ui` also holds the expanded lineup in `com.bbbjam.core.ui.lineup` (`song-row-expansion`,
`in_progress`): `LineupPanelUiModel(openSlots, filledSlots, extras, noOpenSlotsNote, hint)` of
`LineupLineUiModel(instrument, detail, contentDescription, kind)`, the pure mapper
`Lineup.toLineupPanel(extras)` (open slots, then filled, each in lineup order, then extras), the
approved copy in `internal object LineupPanelCopy` ("Cupos libres", "Cupos cubiertos", "Otros",
"LIBRE", "No quedan cupos libres.", the hint), `LineupPanelDefaults` (the strip's style per kind
plus the detail colour), `LineupPanel(model)` (headings, compact non-interactive lines, one
semantics node each, five `@Preview`s with the indicator's) and `ExpandIndicator(expanded)` (the
`KeyboardArrowDown` chevron, `textMuted`, turned 180° when expanded). `InstrumentStripCopy.name()`
is now visible inside `:core:ui`.

Product code: the `:core:model` domain types, the Info screen, the read-only Próxima jam screen and
the catalog and jams data layer (`:core:data`).

`backend/apps-script/` (not a Gradle module; `apps-script-read-endpoint`, `in_progress`) holds
the Apps Script web app: `appsscript.json` (V8, `spreadsheets.currentonly`, executes as the owner,
anonymous access) and `src/Normalize.js`, `Catalog.js`, `Code.js` — one route,
`GET ?resource=catalog`, which never opens `Config`. Node tests (33, `node:test`, no
`package.json`) run outside `init.sh`; `tools/check-response.js` checks a live response. The
contract is `docs/apps-script-api.md`, with mapper fixtures in `docs/api-samples/`. Not deployed
yet: deploying is the user's step.

The Sheet contract is `docs/sheet-schema.md` (`sheet-schema-definition`, `accepted`): tabs, headers,
exact Spanish enum values, cell reading rules, slot identity (the k-th slot of an instrument is the
k-th column of it not holding `-`), a Type-to-Sheet mapping of every `:core:model` field, and the
**Mapper rules** the repository slices must enforce. `docs/sheet-seed/` is the one-time import
file; the real Sheet is the authority and already holds more songs.

### Reachable without unblocking the Sheet schema

Eight slices, in plan order: `gradle-kotlin-compose-baseline`, `module-skeleton`,
`konsist-isolation-rules`, `detekt-ktlint-gate`, `molecule-presenter-harness`,
`design-tokens-theme`, `domain-model-types`, `info-screen`.

Everything from `apps-script-read-endpoint` onward waits on the Sheet schema.

## Session Log

### Session 001 — 19 September 2026

- Goal: consolidate closed discovery into durable documents.
- Completed: ran `build-brief`, producing `CONTEXT.md`, `docs/build-brief.md`,
  `docs/domain-model.md`, `docs/user-and-access-model.md`, `docs/technical-discovery.md`,
  `docs/risks-and-open-questions.md` and `DESIGN.md`. Updated the bitácora.
- Verification run: cross-reference check for dangling document links; no product code to verify.
- Evidence captured: seven documents committed in `2cfc5ac`.
- Known risk or unresolved issue: the Sheet schema is undefined and is the top blocker.
- Next best step: run `harness-starter`.

### Session 002 — 19 September 2026

- Goal: create the minimal startup harness.
- Completed: `AGENTS.md`, `init.sh`, `PROGRESS.md` and `feature_list.json` created.
- Verification run: `feature_list.json` validated as JSON; every `depends_on` resolves to an
  existing id, no self-references, no cycles, one `in_progress` at most. `bash -n init.sh` passed
  and the file is executable.
- Evidence captured: 34 features, 1 blocked, 8 reachable without the Sheet schema.
- Known risk or unresolved issue: `./init.sh` passes, but the gate is currently thin — `check`
  runs unit tests and lint only, and the three quality tools report as unwired. The baseline
  becomes meaningful once `konsist-isolation-rules` and `detekt-ktlint-gate` land.
- Next best step: implement `gradle-kotlin-compose-baseline`.

### Session 003 — 19 September 2026

- Goal: write the three Claude Code subagents.
- Completed: `.claude/agents/planner.md`, `implementer.md` and `validator.md`. Added a `CI=true`
  guard to `init.sh` so the verification command feature-flow expects never tries to start the app.
  Documented the feature status ladder, including `accepted`, in `AGENTS.md`.
- Verification run: `bash -n init.sh` and `CI=true ./init.sh`, both clean, exit 0. Frontmatter of
  the three agents checked for name, description, tools and model.
- Evidence captured: the validator holds read-only tools plus Bash, so it can rerun the gate but
  cannot quietly repair what it is meant to judge.
- Known risk or unresolved issue: the pipeline has not been exercised end to end yet. The first
  real test is running feature-flow on `gradle-kotlin-compose-baseline`.
- Next best step: run the flow on `gradle-kotlin-compose-baseline`, or settle the Sheet schema.

### Session 004 — 19 September 2026

- Goal: generate the screens in Stitch and reconcile `DESIGN.md` against the export.
- Completed: export unpacked under `docs/design/`; palette and typography in `DESIGN.md` replaced
  with measured values. Four tokens remain derived (`textMuted`, `slotFilled`, `archive`, `error`)
  and are marked as such.
- Verification run: token values counted against the HTML of all twelve exported screens; the
  export's own prose palette appears zero times in its code, so the front-matter values were used.
- Evidence captured: commits `2d23970`, `dfa4d6e`, `847c8d2`; bitácora section 6.5 lists the
  product facts Stitch invented and that were rejected.
- Known risk or unresolved issue: none new.
- Next best step: run the flow on `gradle-kotlin-compose-baseline`.

### Session 005 — 28 September 2026

- Goal: check the harness against the week 1 and week 2 course material and close the gaps.
- Completed:
  - `AGENTS.md` reordered in a U layout: role, non-negotiable rules and literal commands at the
    top; reference in the middle; Definition of Done and a reminder of the critical rules at the
    end. 106 → 124 lines, still under the ~150 ceiling.
  - New project skill `.claude/skills/architecture/`: module layout, where each piece goes,
    allowed dependencies, presenter pattern, feature template, anti-patterns. Referenced from
    `AGENTS.md` and from the three subagents.
  - Course PDFs kept out of git: `/docs/clases/` added to `.gitignore`.
  - `START-HERE.md` and the bitácora brought up to date.
- Verification run: `CI=true ./init.sh`, exit 0. The architecture skill is listed by Claude Code
  after creation.
- Evidence captured: this entry; bitácora section 6.6.
- Known risk or unresolved issue:
  - **The subagents could not invoke their skill.** None of the three listed `Skill` in its
    `tools:` frontmatter, yet each is told to invoke `feature-spec`, `feature-implementer` or
    `feature-validator`. Fixed by adding `Skill` to all three; still to be confirmed on the first
    planner run.
  - The architecture skill left three points open on purpose: the dependency injection
    framework, the navigation and deeplink scheme, and whether admin UI is its own module. The
    first and third were settled in session 006.
  - The reference presenter sample did not exist in the repo. Addressed in session 006.
- Next best step: run the planner on `gradle-kotlin-compose-baseline` and review the spec before
  implementing.

### Session 006 — 28 September 2026

- Goal: record the user's decisions on dependency injection and admin, and write the canonical
  presenter example.
- Completed:
  - **D-15, admin is a state, not a module.** The nine `feature-admin` slices moved to
    `feature-next-jam` (eight mutations) and `feature-info` (passphrase login). No dependency
    changed.
  - **D-16, Koin**, constructor injection only; presenters `factory`, repositories `single`, one
    Koin module per Gradle module, `startKoin` only in `:app`.
  - `.claude/skills/architecture/references/presenter-pattern.md`: contracts, a next-jam example
    with a child row presenter and admin state, the screen, Koin wiring, and Molecule tests with
    fakes. Adapted from Doximity's two articles, with a table of every deviation. Both bugs the
    bitácora records (`equals`/`hashCode` mismatch, undeclared `invoke`) are present in the
    articles themselves and are corrected here.
  - The architecture skill, `AGENTS.md`, `technical-discovery.md`, START-HERE and the bitácora
    updated for D-15 and D-16; decision range references moved to D-01 … D-16.
- Verification run: `feature_list.json` rewritten with its original 2-space indentation; the diff
  is exactly the nine `area` lines. The presenter example is **not compiled** — there is no Compose
  in the project yet.
- Evidence captured: this entry; bitácora sections D-15, D-16 and 6.7.
- Known risk or unresolved issue: the example may not compile as written. `molecule-presenter-harness`
  must turn it into real code with a green test, then update the reference to match the code.
- Next best step: run the planner on `gradle-kotlin-compose-baseline`.

### Session 007 — 28 September 2026

- Goal: settle the Sheet schema, the top blocker, and seed it with a real jam.
- Completed:
  - `docs/sheet-schema.md`: tabs `Catalogo`, `Jams`, one tab per jam named `YYYY-MM-DD`, and
    `Config`. The planned assignments tab folded into the jam tabs as one column per slot: empty is
    open, a name is filled, `-` is not in that song's lineup (D-06). Headers are Spanish because the
    admin edits the Sheet; code maps them by name.
  - `docs/sheet-seed/`: four CSVs built from `docs/ejemplo jam.jpeg`, the 25 July 2026 setlist at La
    Macanuda — thirteen songs with artist and key. Artists normalized (`B.B. King`,
    `Memphis La Blusera`), confirmed by the user.
  - `sheet-schema-definition` moved from `blocked` to `not_started`.
  - Two open questions closed by the schema: catalog-deleted songs fall back to the jam tab's
    title/artist copies, and musician suggestions come from past jam tabs.
  - `Jam` gains `startTime` in the domain model; the poster and the next-jam header both need it.
- Verification run: seed CSVs regenerated and inspected; `feature_list.json` diff limited to the
  one slice. The Sheet itself was not written: it is private and no connector was authenticated.
- Evidence captured: this entry; bitácora section 6.8.
- Known risk or unresolved issue:
  - The seed is not in the real Sheet yet. The user imports it by hand, or a later session does it
    with the Chrome extension or the Drive connector.
  - `tono_default` in the seed is that night's key, a stand-in. Tempo, tags and difficulty are empty
    and are the admin's to fill; the week 5 assistant depends on them.
- Next best step: run the planner on `gradle-kotlin-compose-baseline`; import the seed in parallel.

### Session 008 — 28 September 2026

- Goal: first real run of the pipeline — plan `gradle-kotlin-compose-baseline`.
- Completed:
  - The planner wrote `docs/specs/gradle-kotlin-compose-baseline.md` (Compose in `:app` only,
    Kotlin 2.2.10 matching AGP 9.4.1's built-in Kotlin, Compose BOM 2025.09.00, activity-compose
    1.11.0, a launcher `MainActivity`, and a `#111318` placeholder screen).
  - The user reports the seed CSVs are imported into the real Sheet. Not verified from this session:
    the Sheet is private to it.
- Verification run: orchestrator review of the spec against the repo — no Kotlin plugin applied, no
  activity in the manifest, and hex values matching `DESIGN.md`, all confirmed. `adb devices` lists
  no device; three AVDs exist (`Pixel_2_API_29`, `Pixel_2_API_30b`, `Pixel_6a_API_34`).
- Evidence captured: the planner loaded `feature-spec` through the Skill tool, which confirms the
  session 005 fix of adding `Skill` to the subagents' tools.
- Known risk or unresolved issue:
  - `design-tokens-theme` depended only on this slice, but the architecture skill puts tokens in
    `:core:ui`, which `module-skeleton` creates. Fixed with user approval: `module-skeleton` added
    to its `depends_on` (no cycle: `module-skeleton` depends only on this slice).
  - Manual launch check: the user connected a Pixel 5 (`adb devices` shows it); the implementer
    runs the launch scenario on it.
  - The course skill `feature-spec` points to a reference spec, `docs/specs/bootstrap-nextjs-shell.md`,
    that does not exist here. Harmless; noted.
- Next best step: spec approved by the user; run the implementer.

### Session 009 — 28 September 2026

- Goal: implement `gradle-kotlin-compose-baseline` (implementer subagent).
- Completed:
  - Catalog: added `kotlin` 2.2.10, `composeBom` 2025.09.00, `activityCompose` 1.11.0, the Compose
    libraries and the `kotlin-compose` plugin (`version.ref = "kotlin"`); removed appcompat and
    material. Root build declares the plugin `apply false`; `:app` applies it, sets
    `buildFeatures.compose = true` and swaps the dependencies.
  - `MainActivity` and `PlaceholderScreen` (with `@Preview`), launcher entry in the manifest,
    colors/strings/theme reworked, `values-night` deleted.
  - No version bump was needed.
- Verification run:
  - Before: `CI=true ./init.sh` exit 0.
  - After: `./gradlew :app:assembleDebug` successful; `CI=true ./init.sh` exit 0, the three tools
    `NOT WIRED YET`. Lint: 4 warnings, all newer-version notices.
  - `grep` for `appcompat|com.google.android.material` in the catalog and `app/build.gradle.kts`:
    no matches. `grep` for `MaterialComponents|AppCompat` in `app/src/main`: no matches.
    `values-night` and `res/layout` absent.
  - `./gradlew buildEnvironment`: `kotlin-gradle-plugin:2.2.10` and
    `compose-compiler-gradle-plugin:2.2.10`, neither resolved to another version.
  - Pixel 5, Android 14 (API 34), system light mode, 3-button nav, held in landscape: `installDebug`
    exit 0; cold `am start -W` Status ok, 694 ms; `logcat -s AndroidRuntime` empty (cleared first).
    The screenshot shows `#111318` edge to edge (pixel-sampled, including under both system bars),
    the centered label (sampled `#E2E2E8`, one step off `#E2E2E9`, capture rounding), no action bar,
    light system bar icons. Two screencaps right after `am start` caught the window transition with
    the surface already dark. No white frame was caught, but screencap only samples frames.
- Evidence captured: `feature_list.json` entry. Screenshots kept out of the repo.
- Deviation from the spec: the spec calls `enableEdgeToEdge()` with no arguments. On the light-mode
  device that drew dark status icons (nearly invisible) and a light grey navigation-bar scrim over
  the dark screen, contradicting "dark `#111318` edge to edge". `MainActivity` now passes
  `SystemBarStyle.dark(Color.TRANSPARENT)` for both bars, called before `super.onCreate`. This is
  permanent behavior for a dark-only app, not placeholder-only.
- Known risk or unresolved issue:
  - The `@Preview` carries `backgroundColor = 0xFF111318` as the spec requires, so that hex appears
    twice (once in `colors.xml`), against the validator checklist's "appear once". The annotation
    needs a compile-time constant, so it cannot read the resource. Goes away with
    `design-tokens-theme`.
  - Portrait was also checked (rotation locked to 0 for the shot, then restored): same result,
    AndroidRuntime empty. Dark system mode was not checked.
  - Layout Inspector (optional in the spec) was not run.
- Next best step: run the validator on `gradle-kotlin-compose-baseline`.

### Session 010 — 28 September 2026

- Goal: independent validation of `gradle-kotlin-compose-baseline`.
- Completed: validator verdict **accept**; status set to `accepted` by the orchestrator. This is the
  first feature to go through the full planner → implementer → validator pipeline.
- Verification run (by the validator, not the implementer): `CI=true ./init.sh` exit 0; the spec's
  grep checks clean; `buildEnvironment` shows KGP and the Compose compiler plugin both at 2.2.10;
  reinstall and cold start on the Pixel 5 in 636 ms with an empty AndroidRuntime logcat; dark
  surface edge to edge with light system-bar icons, confirmed by eye.
- Rulings recorded in the spec's "Accepted Deviations": the `SystemBarStyle.dark` edge-to-edge
  call is justified and in scope; the `@Preview` hex literal is a spec inconsistency.
- Known risk or unresolved issue: `design-tokens-theme` inherits the cleanup of the temporary
  colors and the preview literal (added to its notes). The gate is still unit tests and lint only.
- Next best step: plan `module-skeleton`, the next dependency-ready slice.

### Session 011 — 28 September 2026

- Goal: implement `module-skeleton` (implementer subagent).
- Completed:
  - Catalog: `compileSdk = "37"`, `minSdk = "24"` versions; `android-library` (agp) and `kotlin-jvm`
    (kotlin) plugins, both declared at the root `apply false`. `settings.gradle.kts` includes
    `:core:model`, `:core:ui`, `:core:data`.
  - `:core:model` on `kotlin-jvm` with Java/JVM target 11 and JUnit; `:core:ui` and `:core:data` on
    `android-library` with namespaces `com.bbbjam.core.ui`/`.data`, catalog SDK levels, Java 11,
    `api(project(":core:model"))`. Sources in `src/main/kotlin`; one `/build` `.gitignore` each.
  - Marker objects `CoreModelMarker`, `CoreUiMarker`, `CoreDataMarker`; `CoreModelMarkerTest`;
    `:app` reads SDK levels from the catalog, depends on all three, and has `ModuleWiringTest`.
  - Architecture skill: new "Build Conventions" section.
- Verification run:
  - Before: `CI=true ./init.sh` exit 0. After: `./gradlew build --rerun-tasks` successful (263
    tasks, no compiler warnings), `CI=true ./init.sh` exit 0, the three tools `NOT WIRED YET`. Both
    new tests: 1 test, 0 failures.
  - Dependency reports match Scenario 3: `:core:model` only `kotlin-stdlib`; `:core:ui`/`:core:data`
    exactly one `project ':core:model'`; `:app` all three. `buildEnvironment`: library plugin 9.4.1,
    KGP 2.2.10 only. No `org.jetbrains.kotlin.android` anywhere.
  - Negative check: `import android.content.Context` in `CoreModelMarker.kt` made
    `:core:model:compileKotlin` fail with `Unresolved reference 'android'` (exit 1); reverted, compiles
    again.
  - Pixel 5, API 34: `installDebug` exit 0, cold `am start -W` Status ok 775 ms, AndroidRuntime
    logcat empty; screenshot unchanged from the baseline (dark surface, centered label), by eye only.
- Evidence captured: `feature_list.json` entry. Screenshot kept in the scratchpad, not the repo.
- Deviation from the spec: none.
- Known risk or unresolved issue:
  - The dependency direction is checked only by hand-read Gradle reports until Konsist lands.
  - The marker objects and `ModuleWiringTest` lines are scaffolding; the slices named in their KDoc
    must delete them.
  - Screenshot colors were not pixel-sampled this time (no image library in the shell).
- Next best step: run the validator on `module-skeleton`.

### Session 012 — 28 September 2026

- Goal: independent validation of `module-skeleton`.
- Completed: validator verdict **accept**; status set to `accepted` by the orchestrator.
- Verification run (by the validator): `CI=true ./init.sh` exit 0; the new tests rerun with
  `--rerun` and passing; dependency reports matching the architecture skill; one KGP version; the
  negative check reproduced independently and the file restored byte-identical (SHA-1); Pixel 5 cold
  start in 686 ms, empty AndroidRuntime logcat, background pixels sampled at `#111318`.
- Known risk or unresolved issue: dependency direction is still checked by reading Gradle reports
  until Konsist lands. The validator did not rerun `./gradlew build --rerun-tasks`, so the
  implementer's "no compiler warnings" claim stands on the implementer's run alone.
- Next best step: plan `konsist-isolation-rules`.

### Session 013 — 28 September 2026

- Goal: implement `konsist-isolation-rules` (implementer subagent), spec
  `docs/specs/konsist-isolation-rules.md`.
- Completed:
  - Catalog `konsist = "0.17.3"` and library entry; `include(":konsist-test")`; module
    `konsist-test/` (`kotlin-jvm`, Java 11, `/build` `.gitignore`, JUnit + Konsist, no project
    dependency). Its `tasks.test` declares the project's `.kt`/`.kts` files as inputs and passes
    `bbbjam.rootDir`.
  - `ModuleIsolationTest` with the 8 specified tests; Windows `\` module separators normalised; no
    hard-coded module list.
  - `init.sh` changed exactly as the spec's Decision 4 (approved by the user): a `konsist_wired`
    check replaces the task-name grep for Konsist; detekt/ktlint detection untouched.
  - Architecture skill (`:konsist-test` row, enforcement note, package-root convention) and
    `docs/technical-discovery.md` (one line) updated.
- Verification run (full evidence in `feature_list.json`):
  - Before: `CI=true ./init.sh` exit 0, three tools `NOT WIRED YET`. After: exit 0, `konsist: wired`,
    `tests="8" failures="0" errors="0"`.
  - Scenario 5: `:konsist-test:test` UP-TO-DATE, then executed after a one-line touch in
    `CoreModelMarker.kt`.
  - Scenarios 2-3 with temporary `:feature:probe-a`/`probe-b`: clean probes green; a cross-feature
    dependency plus import made `CI=true ./init.sh` exit 1 with `feature-imports-feature` and
    `build-file-project-deps` failing (`ProbeA.kt:4:1`, `feature/probe-a/build.gradle.kts:23`); a fully
    qualified reference without import failed only the build-file test.
  - Scenario 4: each remaining violation (scope sanity, package root, feature→app, core allowlist,
    Android in `:core:model`, ViewModel import, ViewModel subclass, core build file) failed exactly
    its own test and passed again after the revert.
  - init.sh negative control: suite file moved out → `konsist: NOT WIRED YET`; restored → `wired`.
  - Restore: SHA-1s of every touched file match; `feature/` gone; `git status` shows only the
    slice's files. Logs kept in the scratchpad, not the repo.
- Deviation from the spec: none in behavior. Interpretation: Decision 4 says to replace "the two
  comment lines" about "unit tests and lint only"; only one line says that, so only it was replaced.
  The next two comment lines (tools "picked up here automatically, with no change to this script")
  are now stale for Konsist and were left for the orchestrator/user to reword.
- Known risk or unresolved issue:
  - Konsist 0.17.3 (Dec 2024) parses with `kotlin-compiler-embeddable` 2.0.21 while the project
    compiles with 2.2.10; syntax newer than 2.0 could fail to parse. JDK 25 prints `sun.misc.Unsafe`
    warnings from that jar (noise).
  - Rules over `feature/*` are vacuous until the first feature module lands (Konsist passes on empty
    lists); the probes are the only proof so far.
  - Konsist sees imports, not resolved references; the build-file rule covers fully qualified use.
  - Konsist failure lines print a `???` glyph in this Windows console (encoding); the message is intact.
- Next best step: run the validator on `konsist-isolation-rules`.

### Session 014 — 28 September 2026

- Goal: independent validation of `konsist-isolation-rules`.
- Completed: validator verdict **accept**; status set to `accepted` by the orchestrator.
- Verification run (by the validator): the `init.sh` diff matches Decision 4 exactly; the gate is
  green with `konsist: wired` and 8/8 tests; the up-to-date trap fix, the probe-module cross-feature
  failure (`init.sh` exit 1), the fully qualified variant, the `init.sh` honesty check, and four
  extra violations of the validator's own choosing were reproduced; SHA-1 of all 139 non-ignored
  files identical before and after.
- Also: `docs/technical-discovery.md` still said the Sheet schema was undefined — a miss from
  session 007, corrected now to point at `docs/sheet-schema.md`.
- Known risk or unresolved issue:
  - `init.sh` lines 23–24 said tools are picked up "with no change to this script", false for
    Konsist (a spec inconsistency). Reworded with the user's approval, comment only; `bash -n` and
    `CI=true ./init.sh` (exit 0, konsist wired) rerun afterwards.
  - Keep one `include(":x")` per line in `settings.gradle.kts`: the scope-sanity guard parses only
    single-argument includes.
  - Konsist 0.17.3 parses with Kotlin 2.0.21 while the project compiles with 2.2.10.
- Next best step: plan `detekt-ktlint-gate`.

### Session 015 — 28 September 2026

- Goal: plan `detekt-ktlint-gate`.
- Completed: `docs/specs/detekt-ktlint-gate.md`, prototyped on a throwaway clone. ktlint via
  ktlint-gradle 14.2.0 (ktlint 1.8.0, `android_studio` style); detekt applied from a root
  `subprojects {}` block to every Kotlin module; Compose naming exceptions in `.editorconfig` and
  `config/detekt/detekt.yml`; today's tree passes with no baseline after config plus small fixes.
- Finding: **no stable detekt runs here.** detekt 1.23.8 fails three ways on the Java 25 daemon
  (invalid `--jvm-target`, its Kotlin 2.0.21 compiler cannot parse `25.0.2`, and forcing Kotlin
  2.2.10 is rejected). detekt 2.0.0-alpha.6 works, including with the configuration cache.
- Decisions by the user: the spec's `init.sh` change is **approved** (a tool counts as wired only
  when its task is scheduled in every Kotlin module); **detekt 2.0.0-alpha.6 approved** over
  lowering the daemon JVM pin to 21.
- Next best step: the implementer runs the spec.

### Session 016 — 28 September 2026

- Goal: implement `detekt-ktlint-gate` (spec `docs/specs/detekt-ktlint-gate.md`).
- Completed: catalog entries (detekt 2.0.0-alpha.6, ktlint-gradle 14.2.0, ktlint 1.8.0); root
  `build.gradle.kts` quality block (Decision 4); `.editorconfig` and `config/detekt/detekt.yml`
  (Decision 2); explicit `assertEquals` import in both template tests, then `./gradlew ktlintFormat`
  (import order, final newlines, one joined signature in `ModuleIsolationTest`), formatting only;
  `init.sh` detection replaced exactly as the user-approved Decision 5; architecture skill Build
  Conventions and `docs/technical-discovery.md` §Testing updated.
- Verification run:
  - Baseline `CI=true ./init.sh` exit 0 (detekt/ktlint NOT WIRED YET); warm 7.41 / 6.97 / 7.16 s.
  - `./gradlew check --warning-mode all --rerun-tasks` exit 0, no Gradle deprecation warning;
    `detekt` and `ktlint*SourceSetCheck` ran in all five Kotlin modules.
  - After: `CI=true ./init.sh` exit 0, `konsist: wired`, `detekt: wired`, `ktlint: wired`; warm
    6.21 / 6.22 / 6.09 s (about 1 s faster: one `check --dry-run` replaced two `tasks --all`).
  - ktlint probe (`PATH:String   =   ...` in `CoreModelMarker.kt`): only
    `:core:model:ktlintMainSourceSetCheck` failed (`8:20 Missing spacing after ":"`);
    `CI=true ./init.sh` exit 1.
  - detekt probe (empty `if` appended): only `:core:model:detekt` failed
    (`CoreModelMarker.kt:12:20 ... [EmptyIfBlock]`); `CI=true ./init.sh` exit 1.
  - Each probe restored by copy, SHA-1 `bb307eb9…` matched, `CI=true ./init.sh` exit 0.
  - init.sh negative control: without the `com.android.base` hook it printed `NOT WIRED YET
    (missing from check in: :app :core:data :core:ui)` for both tools; with the HEAD root build file
    it named all five modules; restored (SHA-1 `2eebddfb…` match), three `wired` again.
- Evidence captured: `feature_list.json` entry, status `passing` (awaiting the validator).
- Known risk or unresolved issue:
  - detekt is an alpha (user-approved); no stable detekt runs on the Java 25 daemon. Revisit when
    detekt 2.0 ships.
  - ktlint-gradle keeps stale errors when a new file with violations is added and then deleted; the
    gate stays red until `./gradlew ktlintCheck --rerun-tasks`. Probe by modifying existing files.
  - `.claude/agents/*.md` still describe detekt/ktlint as "not wired until their slices land"; left
    for the orchestrator/user as the spec says.
  - Root build scripts are not linted; the cross-project `subprojects {}` block is incompatible with
    Gradle Isolated Projects (not enabled).
- Next best step: validate `detekt-ktlint-gate`.

### Session 017 — 28 September 2026

- Goal: independent validation of `detekt-ktlint-gate`.
- Completed: validator verdict **accept**; status set to `accepted`. The three subagent files no
  longer say the tools are unwired: the gate is complete, a `NOT WIRED YET` line is now a defect,
  and baselines, `ignoreFailures` or rule disables need the user's approval.
- Verification run (by the validator): `CI=true ./init.sh` exit 0 with three `wired`;
  `check --rerun-tasks` runs detekt and ktlint in all five Kotlin modules; its own probes — ktlint in
  `:core:data`, detekt in `:core:ui`, the missing-hook NOT WIRED case, and a non-Composable
  PascalCase function flagged by both tools — each failed as expected and were restored by SHA-1.
- Known risk or unresolved issue:
  - `init.sh` can exit 1 with no message if `./gradlew check --dry-run` prints nothing (e.g. a
    Gradle startup flake): under `set -euo pipefail` the empty grep aborts the script. It fails
    closed, never falsely green. Fixed 29 September with the user's approval (`|| true` on the grep
    in `modules_running`): an empty plan now prints `NOT WIRED YET (missing from check in: every
    module)` for both tools instead of aborting; `bash -n` clean and the full gate still exits 0
    with three `wired`.
  - detekt is an alpha; the ktlint-gradle stale-results quirk (see session 016).
- Next best step: plan `molecule-presenter-harness`.

### Session 018 — 28 September 2026

- Goal: implement `molecule-presenter-harness` (spec `docs/specs/molecule-presenter-harness.md`).
- Completed: catalog entries (molecule 2.2.0, turbine 1.2.1, coroutines-test 1.10.2, BOM-managed
  compose runtime); `core/ui/build.gradle.kts` exactly as Decision 2; the four contracts in
  `core/ui/src/main/kotlin/com/bbbjam/core/ui/presenter/`; `SamplePresenter`,
  `SamplePresenterTest` and `EventHandlerTest` in `core/ui/src/test`; `CoreUiMarker` deleted and
  removed from `ModuleWiringTest`; `presenter-pattern.md` (contracts verbatim, module setup,
  compiled example, next-jam labelled illustrative), architecture `SKILL.md` and
  `docs/technical-discovery.md` updated.
- Verification run:
  - Baseline `CI=true ./init.sh` exit 0, three `wired`.
  - `./gradlew ktlintFormat` exit 0; `./gradlew check --continue` exit 0; 9/9 `:core:ui` tests.
  - Trace probe (`isReturnDefaultValues = false`): 3/3 `SamplePresenterTest` fail with
    `Method beginSection in android.os.Trace not mocked`; restored (SHA-1 `fb191622…` match).
  - Scenario 2 (no-op toggle): `toggle expanded event changes the state` and `local state survives
    a new value from the source` fail, `No value produced in 3s`; restored (SHA-1 `2d82f6e4…`).
  - Scenario 3 (`hashCode` from `handle`): the unkeyed-hash, same-key-hash and hash-set tests fail
    (`expected:<3> but was:<5>` for the set); restored (SHA-1 `414efa5f…`).
  - Scenario 4 (no `invoke`): `compileDebugUnitTestKotlin` fails, `Expression 'events' … cannot be
    invoked as a function`; restored (SHA-1 `414efa5f…`), green.
  - `:core:ui` compile + lint with `--warning-mode all --rerun-tasks`: no warning, lint no issues.
  - Final `CI=true ./init.sh` exit 0, `konsist: wired` (8/8), `detekt: wired`, `ktlint: wired`;
    warm 6.41 / 6.19 s. No device used or needed.
- Evidence captured: `feature_list.json` entry, status `passing` (awaiting the validator).
- Known risk or unresolved issue:
  - `isReturnDefaultValues` makes any stubbed Android call in a presenter unit test return a default
    instead of throwing; presenters must not call Android (Decision 4).
  - The unit-test classpath of `:core:ui` resolves kotlin-stdlib 2.2.20 (Molecule's requirement)
    while the compiler is 2.2.10; main classpaths are unchanged.
  - Carried over: detekt alpha, ktlint-gradle stale-results quirk, `init.sh` empty-grep exit.
- Next best step: validate `molecule-presenter-harness`.

### Session 019 — 28 September 2026

- Goal: independent validation of `molecule-presenter-harness`.
- Completed: validator verdict **accept**; status set to `accepted`. The `CoreUiMarker.kt`
  deletion, left unstaged by the implementer after an accidental `git rm` and reset, is staged with
  this commit.
- Verification run (by the validator): gate exit 0 with three `wired`; `:core:ui` tests 9/9 and
  Konsist 8/8; its own negative controls — presenter ignoring new source values, toggle not
  flipping, `equals` ignoring the key, a no-op `invoke`, and `isReturnDefaultValues = false` — each
  failed as expected and were restored by SHA-1; the four contract blocks in `presenter-pattern.md`
  match the source files verbatim.
- Known risk or unresolved issue: `isReturnDefaultValues` makes a stubbed Android call in a
  presenter unit test return a default instead of failing; `:core:ui`'s unit-test classpath
  resolves kotlin-stdlib 2.2.20 against the 2.2.10 compiler (no warnings).
- Next best step: plan `design-tokens-theme`.

### Session 020 — 28 September 2026

- Goal: plan `design-tokens-theme`.
- Completed: `docs/specs/design-tokens-theme.md`, prototyped on a throwaway clone and on the Pixel 5.
  Material 3 underneath with plain token objects on top in `com.bbbjam.core.ui.theme`; amber exposed
  only through semantic roles; bundled fonts (Barlow Condensed SemiBold/Bold/ExtraBold, Chivo
  Regular) because the app is used offline; one XML color left for the pre-Compose window, guarded
  by a drift test; WCAG contrast measured (amber on background 10.35, textMuted on surface 10.10,
  onPrimary on primary 9.52 — all AA and AAA).
- Also: `docs/design/README.md` and bitácora §6.5 still quoted `#0C0E13`/`#282A30` from a first
  reconciliation; corrected to the values `DESIGN.md` already had.
- Decisions by the user: fonts **approved** (fetch and commit the pinned OFL TTFs and licences);
  the Konsist rule **approved**; the theme approach **recorded as D-17** (bitácora, START-HERE,
  AGENTS.md, subagents' decision range).
- Finding for later device checks: the Pixel 5 display runs in Display P3, so screenshots return
  P3-encoded values (amber `#FFB300` reads `#F4B63F`); the spec lists the expected P3 values.
- Next best step: the implementer runs the spec.

### Session 021 — 28 September 2026

- Goal: implement `design-tokens-theme` (spec `docs/specs/design-tokens-theme.md`).
- Completed: token sources in `core/ui/src/main/kotlin/com/bbbjam/core/ui/theme/`
  (`BluesJamPalette`, `BluesJamColors`, `BluesJamTypography` with `BluesJamFonts`, `BluesJamDimens`,
  `BluesJamTheme` with `BluesJamMaterial`, `ThemeShowcase`); the four OFL fonts and two licence
  texts fetched from the pinned commits, all six SHA-256 matching the spec;
  `bluesjam_window_background` in `:core:ui`; `material3` in the catalog and `ui`/`material3` as
  `api` in `:core:ui`; `:app` draws the placeholder from the tokens, and its `colors.xml` and the
  preview literal are gone; the Konsist rule `no-color-literal-outside-core-ui` (user-approved);
  architecture skill and `docs/technical-discovery.md` updated.
- Verification run: `CI=true ./init.sh` baseline exit 0; after `./gradlew ktlintFormat`, exit 0
  with three `wired` lines; `:core:ui` 21 tests, `ModuleIsolationTest` 9, 0 failures; detekt 0
  findings; `:core:ui` lint clean. Negative probes, each restored with a matching SHA-1: an extra
  amber role, a missing `secondary` (M3 baseline `#CCC2DC` leaks), a drifted window color, a
  low-contrast `textMuted` (2.15 and 2.33) and two color literals in `PlaceholderScreen.kt` (Konsist
  and `init.sh` exit 1) each failed the named test. Pixel 5, landscape, Display P3: cold start
  530 ms, empty AndroidRuntime log, background `#111318`, label `#E2E2E8`. In the `ThemeShowcase`
  probe every swatch was within 1 per channel of its token converted to P3, and the three Barlow
  weights and Chivo rendered distinctly. `MainActivity` was restored (SHA-1 OK) and reinstalled.
- Evidence captured: `feature_list.json` → `design-tokens-theme` (status `passing`); logs and
  screenshots in the session scratchpad only.
- Known risk or unresolved issue: `core.autocrlf=true` and no `.gitattributes`. The licence texts
  are stored as LF blobs that hash-match upstream, but a fresh Windows checkout writes CRLF working
  copies with different SHA-256 values (TTFs are binary and unaffected). The Konsist rule is
  textual: it misses `Color.Red`, `Color.parseColor` and XML colors, and would flag an ARGB hex in
  a comment. `ThemeShowcase` ignores window insets, so its first row sits under the status bar;
  it is a probe, not a screen.
- Next best step: independent validation of `design-tokens-theme`.

### Session 022 — 28 September 2026

- Goal: independent validation of `design-tokens-theme`.
- Completed: validator verdict **accept**; status set to `accepted`. Orchestrator follow-ups:
  `.gitattributes` keeps the OFL licence texts byte-identical to upstream on any checkout
  (`core.autocrlf=true` would otherwise write CRLF copies) and marks `*.ttf` binary; the
  architecture skill now lists the Konsist colour rule's named-argument gap (`Color(red = …)`); a
  stale `passing` for `konsist-isolation-rules` in "What exists" corrected.
- Verification run (by the validator): gate exit 0 with three `wired`; `:core:ui` 21/21 and
  Konsist 9/9; fonts and licences re-fetched from the pinned commits with matching SHA-256 on disk,
  in the blob and in the APK; contrast recomputed; its own probes (amber on `surfaceTint`, amber on
  `archive`, an Int ARGB literal and a numeric `Color(…)` in an `:app` test, two typography
  drifts) all caught; Pixel 5 background `#111318` and label `#E2E2E8` under Display P3.
- Known risk or unresolved issue: `BluesJamTypographyTest` does not check line heights; the
  ThemeShowcase swatch probe was run by the implementer only; M3 component defaults (e.g. the
  navigation bar indicator) are not design decisions — each component slice sets them from tokens.
- Next best step: plan `domain-model-types`.

### Session 023 — 28 September 2026

- Goal: plan `domain-model-types`.
- Completed: `docs/specs/domain-model-types.md`, prototyped on a throwaway clone. Types in
  `com.bbbjam.core.model`: `JamStatus`, `Instrument`, `Tempo`, `Difficulty`, value classes `Key` and
  `SongId` (validated against the schema formats), `Slot`, `Lineup`, `JamSong`, `Jam`, `Song`.
  Positions must be exactly 1..n when a `Jam` is built; "historical" takes today's date from the
  caller.
- Finding: java.time on minSdk 24 is safe in `:core:model` as long as it never parses dates or reads
  the clock; the first Android slice that does must enable core library desugaring in `:app` and in
  its own module (lint and AAR metadata checks block the unsafe cases, proven in the prototype).
- Decisions by the user (29 September): the Konsist rule `core-model-no-system-clock` **approved**;
  lineups **only shrink** from the default seven, and anyone playing outside the lineup goes in a
  per-song "Otros" list with name and instrument — recorded as **D-18** in the bitácora,
  `CONTEXT.md`, `docs/domain-model.md`, `docs/sheet-schema.md` (new optional `Otros` column), the
  seed CSV, START-HERE and the spec's "User Approvals". Adding and removing an extra participant
  join the D-13 mutation list (noted on `admin-adjust-lineup` and `action-contract-registry`).
- Also refreshed `START-HERE.md` (it still pointed at the first spec) and the `AGENTS.md` gate line
  (it still said the tools were unwired).
- Next best step: the implementer runs the spec with its User Approvals.
- Open questions recorded in the spec, not blocking: empty slot cells in past jams mean "not
  recorded" (handled by `past-jam-detail`); a 21:00 jam counts as historical from 00:00; a DRAFT jam
  in the past and a song twice in one setlist are not enforced anywhere yet.

### Session 024 — 29 September 2026

- Goal: implement `domain-model-types` (spec `docs/specs/domain-model-types.md`, with its User
  Approvals: Konsist rule approved, D-18).
- Completed: eleven spec types plus `ExtraParticipant` in `core/model/src/main/kotlin/com/bbbjam/core/model/`;
  `Lineup` rejects more of an instrument than the default (2 guitars, 1 of each other), zero stays
  valid; `JamSong.extraParticipants` (default empty) never touches the lineup; `CoreModelMarker` and
  `CoreModelMarkerTest` deleted, `ModuleWiringTest` now uses `JamStatus` (KDoc says the `:core:model`
  edge is also reachable through the `api` edges of `:core:ui`/`:core:data`); Konsist rule
  `core-model-no-system-clock` (a textual match for `.now(`, `Clock.system`,
  `System.currentTimeMillis(`, `System.nanoTime(` in any `:core:model` file). Docs: the Core
  Concepts sketch in `docs/domain-model.md` (no `Jam.id`, `setlist`, `JamSong` title/artist/
  `extraParticipants`, `Lineup`, `ExtraParticipant`), and the architecture skill (module row, clock
  rule, java.time/desugaring convention).
- Verification run: `CI=true ./init.sh` baseline exit 0; `./gradlew ktlintFormat` exit 0; final
  `CI=true ./init.sh` exit 0 with three `wired`. `:core:model` 30/30 (`ExtraParticipantTest` 3,
  `JamSongTest` 5, `JamStatusTest` 1, `JamTest` 4, `KeyTest` 3, `LineupTest` 7, `SlotTest` 3,
  `SongIdTest` 2, `SongTest` 2); `ModuleIsolationTest` 10/10; `ModuleWiringTest` 1/1; detekt 0
  findings; `grep -rn CoreModelMarker --include=*.kt` empty. Fourteen negative demonstrations, each a
  scripted mutation restored from a copy with matching SHA-1: loosened key and id regexes, inverted
  `isOpen`, no blank-name check, no position rule, position ≥ 0, same-day historical, `KEYBOARDS`
  missing from the defaults, no over-default check, no `;()` check, no blank check on extras, extras
  turned into an open slot, an `ARCHIVED` status, and `LocalDate.now()` in `Jam.kt` (Konsist 10
  tests, 1 failed: `core-model-no-system-clock`). Each failed the named test; details in
  `feature_list.json`.
- Finding fixed in scope: the `KEYBOARDS` probe showed `Lineup` threw `NoSuchElementException` for
  an instrument absent from the defaults; it now treats the default count as 0, so every rejection
  is an `IllegalArgumentException`.
- No device check: nothing visible changed.
- Evidence captured: `feature_list.json` → `domain-model-types` (status `passing`); logs and the probe
  script in the session scratchpad only.
- Open questions from the spec, not blocking: **A** decided as D-18. **B** an empty slot cell in a
  past jam means "not recorded", but `Slot.isOpen` is structural, so `past-jam-detail` must not draw
  open state. **C** a 21:00 jam is historical from 00:00 (already in
  `docs/risks-and-open-questions.md`). **D** a DRAFT jam in the past and a song twice in one setlist
  are not enforced; "at most one upcoming jam" belongs to the `Jams` mapper/repository and Apps
  Script.
- Known risk or unresolved issue: java.time on `minSdk` 24 is safe only while `:core:model` never
  parses a date or reads the clock; the first Android module that builds dates must enable core
  library desugaring in `:app` and in itself (architecture skill). The clock rule is textual: a
  statically imported `now()` or a clock reached under another spelling slips past it. The extras probe had to replace
  `JamSong` with a plain class, because `Lineup` cannot see extras by construction.
- Next best step: independent validation of `domain-model-types`.

### Session 025 — 29 September 2026

- Goal: independent validation of `domain-model-types`.
- Completed: validator verdict **accept**; status set to `accepted`.
- Verification run (by the validator): gate exit 0 with three `wired`, Konsist 10/10; `:core:model`
  30/30 with `--rerun`; its own probes — trailing-space, tab and Spanish-name keys, two basses,
  positions `[1,3,2]`, a third guitar as an extra participant, the clock rule via
  `Clock.systemDefaultZone()` and `LocalTime.now()` (a parameter named `now` passes), android and
  serialization imports failing to compile — with SHA-1 and git state identical afterwards.
- Known risk or unresolved issue: `KeyTest` does not list a trailing-space key, so a regex letting
  `"B "` through would pass the suite; added to `catalog-repository-cache`'s notes. The clock rule
  is textual (a static import of `now` would pass it).
- Next best step: `sheet-schema-definition` needs the user to confirm the real Sheet by hand
  (including the new `Otros` column); `info-screen` can proceed meanwhile.

### Session 026 — 30 September 2026

- Goal: unblock `sheet-schema-definition` and `info-screen`.
- Completed:
  - The user reports: the real Sheet reviewed, the `Otros` column added to the jam tab, and more
    songs added to `Catalogo`. Not verifiable from this session (private Sheet); recorded as the
    user's manual evidence. The planner is specifying `sheet-schema-definition` on that basis.
  - Info content gathered from the user and the public @bahiablancablues Instagram and Linktree
    into `docs/info-content.md`, each fact with its source. **D-19**: Info presents Bahía Blanca
    Blues (the organizing group), the monthly jam, the Hideaway radio program (Tuesdays 20–22),
    social links and how to join (come and sign up there); **no venue**, because it can change —
    it belongs to each jam. Updated DESIGN.md, the design prompt, `info-screen` in
    `feature_list.json`, CONTEXT.md (Venue, new "Bahía Blanca Blues"), the build brief, the
    bitácora and START-HERE; decision range now D-01 … D-19.
- Known risk or unresolved issue: the Hideaway radio station/frequency is unknown, and its schedule
  comes from an automated summary of Instagram — confirm with the user before release.
- Next best step: finish `sheet-schema-definition`; plan `info-screen`.

### Session 027 — 30 September 2026

- Goal: plan `sheet-schema-definition` and `info-screen`, and settle their decisions.
- Completed:
  - `docs/specs/sheet-schema-definition.md`: a field-by-field check of the schema against the
    domain types found eleven documentation mismatches (M1–M11) and one gap. The user decided the
    gap (U1): a slot is identified by its order among that instrument's non-`-` columns.
  - `docs/specs/info-screen.md`: `:feature:info` with a presenter, Koin 4.1.1 (4.2.2 would lift
    Kotlin and Compose above the pinned versions), a shared `ExternalLinkOpener` in `:core:ui`, copy
    in Kotlin from `docs/info-content.md` only. The user approved the copy table as written, Koin
    now, and splitting build-logic into its own slice.
  - New slice `build-logic-conventions` (35 features now), placed before `next-jam-read-only-list`,
    which depends on it; no cycles.
- Known risk or unresolved issue: the Pixel 5 is disconnected (`adb devices` empty); `info-screen`
  needs it for the launch and link checks.
- Next best step: implement `sheet-schema-definition`; then `info-screen` once the phone is back.

### Session 028 — 30 September 2026

- Goal: implement `sheet-schema-definition` (spec `docs/specs/sheet-schema-definition.md`, U1 = B).
- Completed:
  - Re-ran the mapping check at HEAD `ec90c8f`: M1–M11 still held; no new mismatch.
  - `docs/sheet-schema.md`: new **Reading cells** (trim every cell; exact, case- and
    accent-sensitive enum and header matching, P1; the endpoint normalizes `fecha`, `hora`,
    `posicion`, M7); exact `SongId` alphabet in **Identifiers** (M1); enum mappings for `tempo` and
    `dificultad` (M2); `etiquetas` split rule (M3); a derived `setlist` row in `Jams` (M5); "at most
    one non-historical jam" (M6); `title` / `artist` (fallback only) on the jam tab (M8); a **Slot
    columns** header-to-`Instrument` table, all seven headers required (M9, P2); an **`Otros`**
    parsing section (M10); slot identity by ordinal among that instrument's non-`-` columns (U1 =
    B); a **Type-to-Sheet mapping** table; and a **Mapper rules** section, each rule "enforced by
    the repository slice", with the `Jams`/tab mismatch behavior left open.
  - `docs/domain-model.md`: `songsterrId` is admin-entered, not enrichment (M4); a song removed from
    the catalog points at `sheet-schema.md` (M11).
  - `docs/risks-and-open-questions.md`: the import and growing catalog; the cell-type question for
    `apps-script-read-endpoint`; the open `Jams`/tab mismatch rule and the P3 seed-as-fixtures
    hand-off for `catalog-repository-cache`.
  - `Lineup.kt`: one KDoc sentence reworded for U1 = B. Comment only.
  - Manual evidence, **the user's own report, not agent-verified** (no agent can read the private
    Sheet): on 29–30 September 2026 the four seed CSVs were imported, the tabs reviewed by hand, the
    `Otros` column added to the jam tab, and more songs added to `Catalogo`. The user did not
    mention the `Config` tab; nothing is claimed about it.
- Verification:
  - Seed check (scratch `seed_check.py`, not committed) on `docs/sheet-seed`: `CONFORMS`, exit 0
    (13 songs, 1 `Jams` row, tab `2026-07-25` positions 1..13; 0 names, 0 `-`, 0 `Otros`). A scratch
    copy with names, `-` cells and `Juan (saxo); Ana (percusión);` also conforms, so those checks
    are not only vacuous.
  - Failures on mutated scratch copies, each exit 1: `tono "Bmaj" is not a key`; `posicion values
    [1, …, 12, 14] are not exactly 1..13`; `Otros entry "Juan saxo" is not "Nombre (instrumento)"`;
    `tempo "rapido" is not one of ['lento', 'medio', 'rápido']` and `estado "PUBLICADO" is not one
    of ['BORRADOR', 'PUBLICADA']`; `missing required headers ['Teclados']`; `id_tema
    "got-my-mojo-workin" is not in Catalogo`; `Config: passphrase valor is filled`. The committed
    seed is unchanged.
  - `./gradlew ktlintFormat` exit 0, no file changed. `CI=true ./init.sh` exit 0: `konsist: wired`,
    `detekt: wired`, `ktlint: wired`, `Baseline OK.`; `:core:model` 30/30, Konsist 10/10.
- Known risk or unresolved issue: whether the real `fecha`, `hora` and `posicion` cells are text or
  typed values is unknown until `apps-script-read-endpoint` reads them. Behavior on a `Jams`/tab
  mismatch is undecided (for `catalog-repository-cache`).
- Next best step: validate `sheet-schema-definition`; then `info-screen` once the phone is back.

### Session 029 — 30 September 2026

- Goal: independent validation of `sheet-schema-definition`.
- Completed: validator verdict **accept**; status set to `accepted`; the stale "import the seed"
  note replaced with the user's reported hand check. The former top blocker is closed.
- Verification run (by the validator): gate exit 0 with three `wired`; `Lineup.kt` diff KDoc-only;
  every field of the 12 domain types checked against the new Type-to-Sheet mapping with no
  remaining mismatch; its own seed checker, independent of the implementer's, conforms on the seed
  and catches a duplicate catalog id and a date tab with no `Jams` row.
- Known risk or unresolved issue: left undefined for the mapper slice (noted on
  `catalog-repository-cache`): duplicate slot headers, `Nombre(instrumento)` without a space,
  duplicate `Jams.fecha`. Under U1 = B, restoring a guitar slot into `Guitarra 1` shifts a named
  musician from the 1st to the 2nd guitar slot (noted on `admin-adjust-lineup`). Whether the real
  Sheet's `fecha`/`hora`/`posicion` cells are plain text is unknown; the user was advised to format
  them as plain text.
- Next best step: implement `info-screen` on the Pixel 5.

### Session 030 — 30 September 2026

- Goal: implement `info-screen` (spec `docs/specs/info-screen.md`, with the user's approvals: copy
  table verbatim, Koin 4.1.1 and `startKoin` now, copy in Kotlin, the admin notice, build-logic
  deferred to `build-logic-conventions`).
- Completed:
  - `:feature:info` (`settings.gradle.kts`, `feature/info/.gitignore`, `build.gradle.kts`):
    `SocialLink`, `InfoUiModel`, `InfoCopy`, `InfoPresenter`, `InfoScreen`, `di/InfoModule.kt`;
    tests `InfoPresenterTest` and `InfoModuleTest`.
  - `:core:ui`: `ExternalLinkOpener` (`com.bbbjam.core.ui.link`).
  - `:app`: `BluesJamApp`, `di/AppModule.kt`, `link/IntentLinkOpener.kt`, manifest
    `android:name`, `MainActivity` shows Info; `PlaceholderScreen.kt` and `placeholder_title`
    deleted. Catalog: `koin` 4.1.1, `koin-bom`, `koin-core`, `koin-android`, `koin-compose`.
  - Architecture skill: `:core:ui` and `:app` rows, `:feature:info` exists, link-opening bullet,
    build-logic bullet pointing at `build-logic-conventions`, reference feature build file, Koin
    4.1.1 (and why not 4.2), copy in Kotlin, 48dp via `LocalMinimumInteractiveComponentSize`.
    `presenter-pattern.md` names `InfoPresenter`/`InfoPresenterTest` as the first compiled feature
    example.
- Verification run:
  - Baseline `CI=true ./init.sh` exit 0 before any change. `./gradlew ktlintFormat` exit 0 (it
    re-wrapped one call in `InfoPresenter.kt`). `CI=true ./init.sh` exit 0, three `wired`.
    `InfoPresenterTest` 4/4, `InfoModuleTest` 1/1, Konsist 10/10, every other suite unchanged.
  - `:app:dependencies --configuration debugRuntimeClasspath`: Koin 4.1.1; all kotlin-stdlib →
    2.2.10; compose-bom 2025.09.00, Compose 1.9.1, material3 1.3.2; lifecycle 2.9.3. Lint adds one
    `NewerVersionAvailable` (koin-bom 4.2.2).
  - Failure demonstrations, each restored and SHA-1 checked (`sha1sum -c` OK on all three files):
    m.youtube URL → `each link row opens exactly its url` fails with expected/actual URL lists;
    `linkError` never set → `a link that cannot be opened…` fails, `No value produced in 3s`;
    `import com.bbbjam.MainActivity` in `InfoModule.kt` → Konsist `feature-imports-app` violated.
  - Spec greps on `feature/info/src/main` clean (the `AM` pattern only hits `INSTAGRAM`).
  - Pixel 5, manual: cold start, empty crash/AndroidRuntime logs; each link tapped from
    `uiautomator` coordinates gave `act=android.intent.action.VIEW` for `www.instagram.com`,
    `www.youtube.com` and `linktr.ee` (Chrome, YouTube app, Chrome); back returned to the same
    `MainActivity` record each time (Instagram and YouTube needed a second back for the page modal and
    YouTube's own Home). Admin notice shown, no activity started. Rows and admin entry are 48dp
    clickable nodes. Screenshots and dumps in the session scratchpad only.
- Known risk or unresolved issue:
  - Link `onClickLabel`s are not verified on device: `uiautomator` does not export action labels,
    and turning TalkBack on through `adb settings` was refused by the permission system (settings
    confirmed unchanged). A validator with TalkBack or an instrumented semantics test can close it.
  - The YouTube link opens a channel named "Radio Hideaway"; the row says only YouTube /
    youtube.com. Confirm with the user that this is the channel to show.
  - With the insets inside the scroll (spec Decision 5), scrolled content passes under the
    transparent status bar (the clock overlaps a heading mid-scroll). As specified; `bottom-navigation`
    may want a status-bar scrim or top inset outside the scroll.
  - Opening the links left two tabs in the phone's Chrome; no setting was changed.
- Next best step: independent validation of `info-screen`; then `build-logic-conventions`.

### Session 031 — 30 September 2026

- Goal: independent validation of `info-screen`.
- Completed: validator verdict **accept**; status set to `accepted`. The first `:feature:*` module
  is in, with Koin 4.1.1 started in `:app` and the approved copy.
- Verification run (by the validator): gate exit 0 with three `wired`, Konsist 10/10 now checking a
  real feature module; `:feature:info` tests 5/5 rerun; dependency report Koin 4.1.1 only,
  kotlin-stdlib 2.2.10 only; 23/23 copy strings match the approved table by script; its own probes
  caught a changed copy word, a feature→`:app` build dependency, and two wrong admin-entry
  behaviours; on the Pixel 5 the Linktree and YouTube intents opened and back returned, and the
  admin notice appeared.
- Known risk or unresolved issue:
  - **Gate gap:** an amber role or `MaterialTheme.colorScheme` in a screen passes the whole gate;
    D-17 is enforced only by per-spec greps. Proposed as a Konsist rule for
    `next-jam-read-only-list` (needs the user's approval).
  - Content scrolls under the transparent status bar; noted on `bottom-navigation`.
  - Link click labels verified on the JVM model and by code reading, not on device.
  - The YouTube link opens a channel named "Radio Hideaway"; recorded in `docs/info-content.md`
    for the user to confirm.
- Next best step: plan `build-logic-conventions`.

### Session 032 — 30 September 2026

- Goal: plan `build-logic-conventions`.
- Completed: `docs/specs/build-logic-conventions.md`, proven on throwaway clones: an included
  `build-logic` with six Kotlin convention plugins, detekt/ktlint kept root-applied, `init.sh`
  unchanged; task plans, dependency sets, 20 test classes and the debug APK (392 non-META-INF
  entries by SHA-1) identical before and after; the Pixel 5 launches unchanged. The user
  **approved** the new Konsist rule `build-file-applies-convention` (10 → 11).
- Known risk or unresolved issue: the Kotlin inside `build-logic` is not linted (outside the root
  build); the first build on a fresh clone is ~30 s slower once; Android Studio sync not tried.
- Next best step: the implementer runs the spec.

### Session 033 — 30 September 2026

- Goal: implement `build-logic-conventions` (spec `docs/specs/build-logic-conventions.md`; the user
  approved the Konsist rule `build-file-applies-convention`, 10 → 11).
- Completed:
  - `build-logic/settings.gradle.kts` (repositories as the root, `FAIL_ON_PROJECT_REPOS`, catalog
    `libs` from `../gradle/libs.versions.toml`), `build-logic/convention/build.gradle.kts`
    (`kotlin-dsl`, AGP/KGP `compileOnly`, six registrations), `build-logic/convention/.gitignore`
    (`/build`), `ProjectExtensions.kt` and the six `*ConventionPlugin` classes.
  - `settings.gradle.kts`: `includeBuild("build-logic")` first in `pluginManagement`. Catalog:
    `targetSdk = "37"`, `android-gradlePlugin`, `kotlin-gradlePlugin`.
  - Module files converted: `core/model`, `konsist-test`, `core/data`, `core/ui`, `feature/info`,
    `app`, as the spec's Expected File Changes.
  - `ModuleIsolationTest`: `build-file-applies-convention` and a KDoc paragraph on the build-file
    rules.
  - Architecture skill, Build Conventions: convention plugins table and rules, rewritten plugin /
    SDK / Java / presenter bullets, Isolated Projects note and the unlinted `build-logic` gap,
    reference feature build file, Konsist at 11 rules, template comment; `:konsist-test` row.
- Verification run (logs in the session scratchpad, `impl-blc/`):
  - Baseline on untouched HEAD `095c88b`: `CI=true ./init.sh` exit 0, three `wired`, Konsist 10.
  - `./gradlew ktlintFormat` exit 0 (no change). `CI=true ./init.sh` exit 0, three `wired`,
    `ModuleIsolationTest` `tests="11" failures="0"`, 20 result files with `failures="0"`.
    `build-logic` compiles with no `w:` line.
  - Spec item 2 grep over `*/build.gradle.kts */*/build.gradle.kts`: no output, exit 1.
  - Zero behaviour change, clean `./gradlew build` before (HEAD) and after, same capture script:
    byte-identical `check --dry-run` (353 lines) and `build --dry-run` (590) plans,
    `buildEnvironment`, merged manifests, AAR names, APK entry list (466) and SHA-1 of all 392
    non-`META-INF` entries, and 15 of 22 dependency reports (all `:core:*`, `:konsist-test`, `:app`
    `debugCompileClasspath`). The other seven (`:app` runtime ×3, `:feature:info` ×4) differ in
    order only and are identical as sorted coordinate sets. Test classes identical; only
    `ModuleIsolationTest` 10 → 11 tests.
  - Failure demonstrations, `./gradlew :konsist-test:test` exit 1 each, each file restored by `cp`
    and SHA-1 checked: `core/data` at its HEAD content → `build-file-applies-convention` violated 7
    times ("applies no bluesjam.* convention plugin", ":2 applies a plugin directly…", ":7 sets
    'compileSdk'…"); `feature/info` with `bluesjam.android.presenter` → "must apply
    bluesjam.android.feature (applies [bluesjam.android.presenter])"; `core/ui` with
    `defaultConfig { minSdk = 26 } // targetSdk…` → one violation, ":7 sets 'minSdk'" (the comment
    word is ignored); `core/data/build.gradle.kts` moved out → "is missing";
    `implementation(project(":feature:info"))` in `core/data` → `build-file-project-deps` still
    fails on the new file shape.
  - Timings, `CI=true ./init.sh` seconds, before / after: warm 7, 7 / 7, 7; after `./gradlew clean`
    48 / 40; one module build file edited (configuration cache miss) 20 / 14; one convention source
    edited — / 18. Single runs on a shared machine; read as "no slower", not as a speed-up.
  - Pixel 5 (API 34), manual: `./gradlew :app:installDebug`; the installed `base.apk` has the same
    SHA-1 as `app-debug.apk`; `am start -W` `Status: ok`, `LaunchState: COLD`, 872 ms; crash buffer
    and AndroidRuntime log empty; Info drawn unchanged (screenshot in the scratchpad). No device
    setting changed.
- Deviations: the four Android module files were converted in one step and built together, not one
  build per file (the two JVM modules were built individually); the result is the same.
- Known risk or unresolved issue:
  - `build-logic` Kotlin is not linted by detekt/ktlint and not counted by `init.sh` (spec
    Decision 3, left for a later slice with the user's say).
  - A fresh clone pays for compiling `build-logic` once (planner measured ~30 s); Android Studio
    sync and opening `build-logic` standalone were not tried.
  - `build-logic/.kotlin/` (Kotlin session dir, empty after a build) is not ignored, the same as the
    root `.kotlin/` today.
  - With `core.autocrlf=true`, `git checkout -- <file>` rewrites LF files as CRLF; restore probes by
    copying the saved bytes, and check with `sha1sum`.
- Next best step: independent validation of `build-logic-conventions`.

### Session 034 — 30 September 2026

- Goal: independent validation of `build-logic-conventions`.
- Completed: validator verdict **accept**; status set to `accepted`. Orchestrator follow-up:
  `.kotlin/` added to the root `.gitignore` (the Kotlin daemon writes error logs there, at the root
  and in `build-logic/`; the root gap predated this slice).
- Verification run (by the validator): gate exit 0 with three `wired`, Konsist 11/11; against its
  own clean clone of HEAD, plans, `buildEnvironment`, the APK's non-META-INF entries by SHA-1 and
  dependency coordinate sets identical; its probes showed the new rule catching five evasions,
  the presenter convention being load-bearing, and `build-file-project-deps` still working.
- Known risk or unresolved issue: the rule `build-file-applies-convention` misses backtick plugin
  ids, `pluginManager.apply` / `plugins.apply`, and `java { toolchain }` — a hardening needing the
  user's approval (gate change). The Kotlin inside `build-logic` is not linted.
- Next best step: `apps-script-read-endpoint` — plan it; deploying the script is the user's step.

### Session 035 — 1 October 2026

- Goal: implement the repository half of `apps-script-read-endpoint` (spec
  `docs/specs/apps-script-read-endpoint.md`, with the user approvals U2–U6 and U1 copy-paste).
- Completed:
  - `backend/apps-script/`: `appsscript.json` (P8), `src/Normalize.js`, `src/Catalog.js`,
    `src/Code.js` (guarded `module.exports`, P1/P6), `.gitignore` (`.clasp.json`,
    `.clasprc.json`, `node_modules/`, `*.local.json`), `README.md` (copy-paste deployment as the
    primary path, clasp optional and noted as not ready for this layout, redeploy-as-new-version,
    live check commands), `tools/check-response.js`, and the tests: `test/normalize.test.js` (8),
    `test/catalog.test.js` (14), `test/router.test.js` (11), with helpers `load.js`, `csv.js`,
    `contract.js`, `edge-input.js`.
  - `docs/apps-script-api.md` (contract, errors, HTTP-200 and redirect notes, quotas) and
    `docs/api-samples/catalog-seed.json` / `catalog-edge.json`, generated once from the builder,
    reviewed against P4, and from then on compared by the tests.
  - Docs: `sheet-schema.md` (**Reading cells**: catalog display text and raw `songsterr_id`;
    jam normalization now named for `apps-script-jams-read-endpoint`; **Mapper rules**: the
    endpoint/mapper split), `technical-discovery.md` (Deployment and Operations, Testing),
    `risks-and-open-questions.md` (cell types, drafts, assumption 6, research task), the
    architecture skill (`backend/apps-script` row, one **Where Each Piece Goes** line), `AGENTS.md`
    (optional-doc line).
- Verification run (logs in the session scratchpad):
  - Baseline on untouched HEAD `3d41000`: `CI=true ./init.sh` exit 0, three `wired`.
  - `node --test backend/apps-script/test/*.test.js`: exit 0, 33 tests, 0 fail.
  - Failure demonstrations, each probe restored from a saved copy with the SHA-1 checked:
    `textCell` without `.trim()` → 5 of 33 fail (`+ '  Crossroads  ' - 'Crossroads'`); one title
    changed in `catalog-seed.json` → the seed test fails; a `config` route reading `Config` →
    4 router tests fail, including `passphrase leaked: {"schemaVersion":1,"rows":[["clave","valor"],
    ["passphrase","s3cret"]]}` and `Code.js contains "Config"`; `readCatalog_` also calling
    `getSheets()` → 3 fail (`accessed getSheets,getSheetByName`). The checker exits 1 on a copy
    with a missing key and a numeric value, on an HTML page and on an error body; 0 on both
    samples; 2 with no argument. One real failure while writing: the doPost guard caught a
    comment in `Code.js` that mentioned doPost; reworded.
  - Quotas read from Google's page (last updated 3 September 2026) on 1 October 2026: 6 min per
    execution, 30 simultaneous executions per user, no daily cap listed for web apps.
  - `./gradlew ktlintFormat` exit 0; `CI=true ./init.sh` exit 0, three `wired`, the 20 test
    result files identical to the baseline in suite names and counts. No Gradle, Kotlin, Konsist
    or `init.sh` change. (No Kotlin changed, so Gradle may have reused up-to-date test results.)
- Deviations: two extra test helpers not listed in the spec — `load.js` reproduces Apps Script's
  shared global scope in Node (the files call each other as globals), and `edge-input.js` holds the
  edge input so the sample could be generated from the same data the test uses. Extra tests
  beyond T1–T3: the files run in one `vm` context with no `module`, a manifest guard, and no
  `doPost`/`passphrase` in `src/`. The README saves live responses under
  `backend/apps-script/`, where `*.local.json` is ignored (the spec's command would have saved it at
  the root, which ignores nothing of the kind). `unknown_resource` does not echo the requested value.
- Remaining, for the user (the Google account is required):
  1. Deploy by copy-paste as `backend/apps-script/README.md` says, and hand the `/exec` URL over in
     chat (it goes into `local.properties` as `bluesjam.appsScriptUrl`, never committed).
  2. Then, by whoever runs the curl: M1 checker exit 0 on the live catalog; M2 song count against
     the Sheet; M3 a live song with `null` optional fields passes; M4 `resource=config` gives
     `unknown_resource`; M5 one cold call after 30+ idle minutes plus min/median/max of ten warm
     calls (input to the optimistic-update decision in `admin-add-song-to-setlist`). Also report
     whether the real `Catalogo` headers match the eight exactly.
  3. Only then set `passing` and record M1–M5, attributed to whoever ran them.
- Known risk or unresolved issue:
  - Nothing has run on Apps Script itself: the `vm` test reproduces its shared scope, but V8 on
    Google's side, the consent screen and the redirect are unobserved until the deployment.
  - The real `Catalogo` may have headers that differ from the eight (`missing_header`) or values
    the mapper will reject; the checker checks shape only.
  - Node tests are outside the gate: a change to `backend/apps-script` can break them with
    `init.sh` still green. Revisit at `apps-script-write-auth`.
  - What an anonymous caller gets when the 30-concurrent limit is hit is not verified.
- Next best step: the user's deployment (step 1 above), then M1–M5, then validation.

### Session 036 — 1 October 2026

- Goal: deploy `apps-script-read-endpoint` and run the live checks.
- Completed: the user deployed the script by copy-paste and handed over the `/exec` URL, stored in
  the git-ignored `local.properties` (`bluesjam.appsScriptUrl`). Live checks M1–M4 pass: the
  checker accepts the catalog (100 songs), `config` returns `unknown_resource`, and every live
  song with null optional fields passes. A data check on the live body found no invalid id, key or
  enum value. Status set to `passing`.
- Finding: **latency is ~2.5 s per call warm** (min 2.10, max 4.51 s over ten calls). That supports
  the plan to serve reads from the Room cache and refresh in the background, and makes optimistic
  writes important.
- Known risk or unresolved issue: a true cold-start call (after 30+ idle minutes) was not measured;
  the first observed call took 2.96 s. tempo, tags, difficulty and songsterrId are empty for all
  100 songs — the week-5 assistant needs at least tags and tempo.
- Next best step: independent validation of `apps-script-read-endpoint`.

### Session 037 — 1 October 2026

- Goal: independent validation of `apps-script-read-endpoint`.
- Completed: first verdict **revise** (docs only): three durable docs and this file's header still
  said latency was unmeasured and the feature `in_progress` — the orchestrator had recorded the live
  evidence without updating them. Repaired; second verdict **accept**; status `accepted`.
- Verification run (by the validator): Node 33/33, gate green; live checks repeated independently
  (catalog OK, every odd `resource` value refused, nothing from Config, 8 keys per song); its own
  code probes caught; the script id is absent from the tree and the git history.
- Known risk or unresolved issue: a true cold call after 30+ idle minutes is still unmeasured.
- Next best step: plan `apps-script-jams-read-endpoint`.

### Session 038 — 1 October 2026

- Goal: record the user's decision on unused catalog fields.
- Completed: **D-20** — tempo, tags, difficulty and songsterrId are not used in the MVP; they stay
  optional in the schema, the endpoint contract and the domain model. `song-detail-screen` no
  longer shows them; `songsterr-browser-link` is `blocked` until the user brings it back.
  Recorded in the bitácora, START-HERE, CONTEXT.md, DESIGN.md, the design prompt, the risks doc and
  `feature_list.json`; decision range now D-01 … D-20.
- Known risk or unresolved issue: the phase 2 assistant will choose from the catalog without
  tags or tempo ("slow blues", "arrancar lento y subir" cannot be filtered by data yet).
- Next best step: plan `apps-script-jams-read-endpoint`.

### Session 039 — 1 October 2026

- Goal: implement the repository half of `apps-script-jams-read-endpoint` (spec
  `docs/specs/apps-script-jams-read-endpoint.md`, with the user approvals A1–A4).
- Completed:
  - `backend/apps-script/src/Normalize.js`: `ContractError` and the header matcher `mapColumns`
    moved in from `Catalog.js`; new `isDateValue`, `isoDateCell`, `timeCell` (display first,
    zero-padded `HH:MM`, typed values formatted in the spreadsheet's zone). `Catalog.js` now calls
    `mapColumns`; the 33 existing tests passed before any test file was touched.
  - `src/Jams.js` (new): `buildJams`, `buildSetlist`, the header tables, the draft rule (exact
    `PUBLICADA` after trimming, fail closed, the tab never requested otherwise), the ISO-date guard,
    `duplicate_date`, and per-jam `setlistError` (A2). `src/Code.js`: route `jams` →
    `readJams_`, which uses only `getSheetByName` and `getSpreadsheetTimeZone`.
  - Tests: `normalize.test.js` +9 (T4), `jams.test.js` new (21, T5 and the checker),
    `router.test.js` +5 (T6) with three existing tests extended; helpers `format.js`
    (`Utilities.formatDate` stand-in on `Intl`, `zonedDate`) and `jams-edge-input.js`; `load.js`
    loads four files. `contract.js`: `checkJamsResponse` (draft rule always, formats with
    `strict`). `tools/check-response.js`: dispatches on `jams`/`songs`, `--strict`, jams summary.
  - Samples `docs/api-samples/jams-seed.json` and `jams-edge.json`, generated once from the
    builder, reviewed by eye, then only compared.
  - Docs: `apps-script-api.md` (the `jams` section, draft rule, per-jam errors, routes),
    `sheet-schema.md` (**Reading cells** normalization, **Mapper rules**: the endpoint's half of
    the mismatch, the draft rule, slot headers detected per jam), `risks-and-open-questions.md`
    (cell types settled, draft data settled for anonymous reads, mismatch script half settled),
    `technical-discovery.md` (tabs read per call; latency pending L5), `user-and-access-model.md`
    (server-side withholding), `backend/apps-script/README.md` (layout, four-file first deploy,
    the redeploy steps for this slice, the A3 test-jam steps, live checks L1–L5 with `$URL` read
    from `local.properties`).
- Verification run (logs in the session scratchpad):
  - Baseline on untouched HEAD `9f2ce59`: `CI=true ./init.sh` exit 0, three `wired`, 20 result files.
  - `node --test backend/apps-script/test/*.test.js`: exit 0, 68 tests (normalize 17, catalog 14,
    jams 21, router 16), 0 fail.
  - Failure demonstrations, each restored from a saved copy with the SHA-1 checked: (a) status
    check disabled → 11 of 68 fail, including `draft leaked: {…"songId":"zz-borrador-secreto"…}`
    and `requested 2026-09-26`; (b) ISO guard dropped → 8 fail, including the passphrase-tab test
    `requested Jams,2026-07-25,Config,Catalogo` (a `fecha` of `Config` opens the passphrase tab);
    (c) `timeCell` without padding → 3 fail (`'9:30'` vs `'09:30'`); (d) the checker on a copy of
    `jams-edge.json` whose `BORRADOR` jam carries a setlist → exit 1, `jams[1] has status
    "BORRADOR" but carries a setlist: a draft must be withheld`.
  - `./gradlew ktlintFormat` exit 0; `CI=true ./init.sh` exit 0, three `wired`, the 20 result files
    identical in names and counts. No Gradle, Kotlin, Konsist or `init.sh` change.
- Deviations: `timeCell` also requires minutes ≤ 59 (the spec names only hour ≤ 23), so `21:75`
  passes through as text for the mapper to reject. `isDateValue` also rejects an invalid `Date`,
  which would otherwise make `Utilities.formatDate` throw and fail the whole response.
  `buildSetlist` takes an optional third argument, the tab name, for the error message. A
  non-contract exception from a tab read is not turned into a per-jam error: it stays
  `internal_error`, as for the catalog. The passphrase-tab test now also accepts
  `getSpreadsheetTimeZone` as an access (the catalog-only test still requires exactly
  `getSheetByName`).
- Remaining:
  1. User: redeploy as `backend/apps-script/README.md`, **Redeploy for the jams route**, says:
     paste `src/Normalize.js`, `src/Catalog.js` and `src/Code.js` over their namesakes, add a
     `Jams` file with `src/Jams.js`, save, **Manage deployments → Edit → New version → Deploy**.
     Report the Sheet's time zone, the real `BORRADOR` jams, and whether every past jam is
     `PUBLICADA`.
  2. Orchestrator: L1 (`--strict` on the live jams), L2 (catalog and `config`), L4 (passphrase
     parameter byte-identical), L5 (latency, jam and tab counts; record in
     `technical-discovery.md`).
  3. User and orchestrator: the A3 test jam (add as `BORRADOR` → L3; flip to `PUBLICADA` → L3;
     delete → confirm absent).
  4. Then `passing`, with each item attributed to whoever produced it, then validation.
- Known risk or unresolved issue:
  - Nothing new has run on Apps Script: the real cell types, `Utilities.formatDate` and the
    1899-epoch time path are unobserved until L1. A real `hora` shown in an unusual format falls
    to the raw `Date` path, whose offset behavior on Google's side is unverified; L1 strict would
    flag a wrong `startTime`.
  - Pasting `Normalize.js` without `Catalog.js` (or the reverse) defines `ContractError` twice or
    not at all; the README says to paste both.
  - Latency grows with every published jam (one tab read each); unmeasured until L5.
  - Node tests remain outside the gate.
- Next best step: the user's redeploy (step 1), then L1–L5, then validation.

### Session 040 — 1 October 2026

- Goal: redeploy the jams route and run the live checks.
- Completed: after two attempts that left `Code.gs` on the old version (the deployment kept serving
  "Known: catalog"), the user re-pasted all four files and published Version N; the route table now
  reads "Known: catalog, jams". Live: L1 strict OK on the real jam; L2 catalog and config refusal
  unchanged; L3 the user's test jam was withheld as `BORRADOR`, served with its one row once
  `PUBLICADA`, and gone after deletion; L4 a `passphrase` parameter changes nothing (byte-identical);
  L5 jams warm median 3.06 s. Status `passing`.
- Finding: copy-paste deployment is error-prone — a file can silently stay old. The README now has
  a **Verify the paste** step (one distinctive line per file) before deploying, and a post-deploy
  check that `?resource=config` lists `Known: catalog, jams` (`backend/apps-script/README.md`,
  **Updating the code later** and **Redeploy for the jams route**).
- Finding: the first test entry came back as `2026-01-01` with an empty time — what the cells held
  (cause not established, likely an input slip); plain-text formatting is now strongly recommended
  in the schema.
- Validator verdict **revise** (tests and docs only; the shipped src is correct and live behaviour
  matches). Repaired by the implementer, no file under `backend/apps-script/src/` changed (SHA-1
  `Normalize.js` 129a8a89…, `Jams.js` 9bd5b743…, unchanged), so no redeploy:
  - `normalize.test.js` +1: a raw time `Date.UTC(1899, 11, 31, 0, 0)` (21:00 at a fixed -03:00)
    that formats as `19:43` in Buenos Aires' 1899 offset, with display `21:00:00` / `21:00`, must
    give `21:00`. Probe: raw-Date branch moved before the display regex → exit 1, 1 of 70 fails,
    `actual: '19:43' expected: '21:00'`; restored, SHA-1 equal.
  - `jams.test.js` +1: PUBLICADA with fecha `x2026-07-25` and `2026-07-25b` → `invalid_date`, no
    tab read. Probe: unanchored `/d{4}-d{2}-d{2}/` → exit 1, 1 of 70 fails, readTab calls
    `[ 'x2026-07-25', '2026-07-25b' ]` instead of `[]`; restored, SHA-1 equal.
  - Docs: `technical-discovery.md` jams latency merged (stale "not measured yet" removed; 1 published
    jam, first call 3.97 s, recheck 2.27–2.84 s); `sheet-schema.md` live-test sentence reworded;
    README verify-the-paste steps; L5 evidence line completed.
  - Reruns: `node --test backend/apps-script/test/*.test.js` exit 0, 70/70; `CI=true ./init.sh`
    exit 0, three `wired`, the 20 result files identical to the baseline.
- Next best step: independent re-validation.

### Session 041 — 1 October 2026

- Goal: independent validation of `apps-script-jams-read-endpoint`.
- Completed: first verdict **revise** — the shipped code was correct, but two rules had no test that
  could fail (display-first time reading, the anchored ISO date guard) and three docs were stale or
  misleading. The implementer added two discriminating tests and fixed the docs without touching
  `src/` (no redeploy); second verdict **accept**; status `accepted`.
- Known risk or unresolved issue: the README's paste markers are tied to this version; any later
  `src` change must update them (noted on `apps-script-write-auth`). A true cold call is still
  unmeasured.
- Next best step: plan `catalog-repository-cache`.

### Session 042 — 2 October 2026

- Goal: implement `catalog-repository-cache` (spec `docs/specs/catalog-repository-cache.md`; its
  **User Approvals** win over the body: dependencies and G1 approved, G2 approved, Q1 reject every
  duplicate, **Q2 drop an invalid tempo/difficulty/songsterrId and keep the song**, Q3 30 min /
  every start / 60 s, S1 jams split out).
- Completed:
  - Build: catalog versions `ksp` 2.3.12, `room` 2.8.4, `okhttp` 5.1.0, `kotlinxSerialization`
    1.9.0, `desugarJdkLibs` 2.1.5, `sqlite` 2.6.2 and their libraries/plugins; root `apply false`
    for `kotlin-serialization` and `ksp`; new 7th convention `bluesjam.android.data`
    (`AndroidDataConventionPlugin`); desugaring in `configureAndroidCommon` (every Android
    module); `buildFeatures.buildConfig = true` in the application convention;
    `app/build.gradle.kts` reads `local.properties` into `BuildConfig.APPS_SCRIPT_URL`.
  - `:core:data`: `AppsScriptEndpoint`, `DataFailure`, `Freshness`, `DataScope`;
    `catalog/` (`CatalogRepository`, `CatalogSnapshot`, `RefreshOutcome` + `toLogLine()`,
    `SongIssue`, `RejectedSong`, `DroppedFields`, `CatalogMapper`, `DefaultCatalogRepository`);
    `remote/` (`AppsScriptTransport`, `OkHttpAppsScriptTransport`, `AppsScriptEnvelope`,
    `SongDto`); `cache/` (`BluesJamDatabase`, `CatalogSongEntity`, `SyncStateEntity`,
    `CatalogDao`, `Converters`, entity mapping); `di/dataModule`. `CoreDataMarker` deleted.
  - `:app`: `INTERNET` permission; `appModule` binds `AppsScriptEndpoint`; `BluesJamApp` starts
    `dataModule`, refreshes the catalog once per start and logs the outcome line.
    `ModuleWiringTest` lost its `CoreDataMarker` line (the `JamStatus` line stays).
  - `KeyTest`: `"B "` and `"Bm "` added to the invalid keys.
  - Konsist: 12th rule `data-libraries-only-in-core-data` (no `okhttp3.`, `androidx.room.`,
    `kotlinx.serialization.` import outside `:core:data`).
  - Docs: architecture skill (7th plugin, desugaring everywhere, buildConfig, URL path,
    `:core:data` layout, pins, Room JVM tests, 12 Konsist rules, the data-library rule),
    `apps-script-api.md` (client wiring and timeouts; `setlistError` display now
    `jams-repository-cache`'s), `sheet-schema.md` (Reading cells and a `Catalogo` **Mapper rules**
    table with Q1 and Q2; the jam rules under their own heading for `jams-repository-cache`),
    `technical-discovery.md` (data stack, freshness policy, JVM Room testing),
    `risks-and-open-questions.md` (seed fixtures done for the catalog; the jams items moved to S1).
- Verification run (logs in the session scratchpad):
  - Baseline on untouched HEAD `b295443`: `CI=true ./init.sh` exit 0, three `wired`, 20 files.
  - `./gradlew ktlintFormat` exit 0, then `CI=true ./init.sh` exit 0 (cold 1 m 19 s), `konsist:
    wired`, `detekt: wired`, `ktlint: wired`. 29 result files, 0 failures: the 20 baseline files
    identical in names and counts except `ModuleIsolationTest` 11 → 12 (`ModuleWiringTest` 1,
    `KeyTest` 3), plus `AppsScriptEndpointTest` 3, `FreshnessTest` 4, `CatalogDaoTest` 5,
    `CatalogMapperTest` 12, `DefaultCatalogRepositoryTest` 14, `RefreshOutcomeTest` 2,
    `DataModuleTest` 1, `EnvelopeTest` 11, `OkHttpAppsScriptTransportTest` 6. `:core:data` tests
    rerun 5 times (`--rerun`): 58/58 each. No baseline, `ignoreFailures`, `@Suppress` or rule
    disable added.
  - Lint: no errors; `:app` 6 `GradleDependency` + 8 `NewerVersionAvailable` warnings (the pins).
  - `:app:dependencies` (debug runtime): kotlin-stdlib 2.2.10 (every request resolves to it;
    release runtime too), OkHttp 5.1.0, serialization 1.9.0, coroutines 1.9.0, Room 2.8.4, Koin
    4.1.1. `buildEnvironment`: one KGP (2.2.10), one AGP (9.4.1), KSP 2.3.12.
  - Failure demonstrations, each an edit of an existing file, restored with the SHA-1 checked:
    Konsist (`import okhttp3.OkHttpClient` in `InfoPresenter.kt` → "Assert
    'data-libraries-only-in-core-data' was violated (1 time)"); desugaring off →
    `:core:data:lintDebug` 14 `NewApi` errors (`java.time.Duration#ofSeconds`,
    `Clock#systemUTC`, …); duplicate-id rule weakened → `every valid row sharing an id is
    rejected` fails; Q2 inverted (`InvalidTempo` rejects) → `an invalid tempo drops the field
    and keeps the song` fails (expected the song, was `[]`); `Key` accepting a trailing space →
    `KeyTest` fails on `"B "`. Details in `feature_list.json`.
  - URL hygiene: `git grep -E 'script\.google\.com/macros'` empty; no new file holds the URL;
    `BuildConfig` checked to hold an `https` URL without printing it.
  - Optional manual live check (outside the gate): curl of the catalog route, URL read from
    `local.properties` in the shell: 200 after one redirect, 2.70 s, checker OK on 100 songs; a
    Python approximation of the required-field rules found nothing to reject. The Kotlin mapper
    was not run on the live body.
- Deviations from the spec body, and why:
  - Q2 changes the outcome shape: `SongIssue` (with `rejectsSong`) replaces `RejectionReason`,
    `RejectedSong.issues` replaces `reasons` (it lists every issue of the row, as Scenario 4
    expects for `sin-tono`), and `DroppedFields` plus `RefreshOutcome.Updated.dropped` report
    the kept songs whose optional values were dropped.
  - `OkHttpAppsScriptTransport` takes the URL string (`AppsScriptEndpoint.url`, internal) instead
    of the endpoint, because `AppsScriptEndpoint` only accepts `https` and MockWebServer serves
    `http`. The https rule has its own test.
  - `sync_state` is written with `@Insert(onConflict = REPLACE)`, not `@Upsert`: Room's upsert
    recognizes the conflict by the exception message, which the stub
    `android.database.SQLException` of JVM tests drops (7 repository and 2 DAO tests failed with a
    bare `SQLException` until this changed). Same behavior on device for a one-row-per-resource
    table.
  - The background-refresh decision runs on the first snapshot of each collection (not in a
    separate `onStart` read), so it uses the same `sync_state` row it emits.
  - The test JVM prints a "restricted method System::loadLibrary" warning for the bundled SQLite
    driver; harmless today, not silenced.
- Remaining before `passing` — the Pixel 5 check (not connected: `adb devices` empty). With
  `ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"` and the URL in `local.properties`:
  1. `./gradlew :app:installDebug`.
  2. `$ADB logcat -c`, then `$ADB shell am start -W -n com.bbbjam/.MainActivity` (record
     `TotalTime`), wait about 10 s, `$ADB logcat -d -s BluesJam`: expect `catalog refresh: updated
     N songs, R rejected, D with dropped fields` (N about 100). `$ADB logcat -d -b crash` and
     `$ADB logcat -d -s AndroidRuntime` empty. This covers `INTERNET`, Koin's `Context` for the
     database, and the desugared `Clock`/`Instant` path (the APK is built for minSdk 24, so its
     java.time calls go through the desugared library on API 34 too).
  3. `$ADB shell am force-stop com.bbbjam`, then `$ADB exec-out run-as com.bbbjam cat
     databases/bluesjam-cache.db > cache.db` (plus `-wal`/`-shm` if present). With Python
     `sqlite3`: `catalog_song` has N rows; the `sync_state` row `catalog` has `fetched_at` set
     and `failure` NULL.
  4. `$ADB shell cmd connectivity airplane-mode enable` (or Wi-Fi and data off by hand),
     `$ADB logcat -c`, relaunch: expect `catalog refresh: failed Offline`, no crash. Force-stop
     and pull again: still N rows, the same `fetched_at`, a newer `attempted_at`, `failure` =
     `Offline` (so `isStale` is true: what a staleness indicator would show).
  5. `$ADB shell cmd connectivity airplane-mode disable`.
  6. Record cold start, the log lines and the row counts here (never the URL); then `passing` and
     validation.
- Known risk or unresolved issue:
  - The device path is unproven: framework SQLite (tests use the bundled driver), the `INTERNET`
    permission, `androidContext` → `Context` for Room, a real redirect from the app.
  - The pins hold kotlin-stdlib at 2.2.10; a Kotlin upgrade must move OkHttp, serialization and
    Koin together. KSP 2.3.x may later demand a newer KGP.
  - A `tempo`/`dificultad` cell typed in NFD Unicode would not match and would be dropped (Q2).
  - Rejections and drops are only in the log line; no admin surface shows them yet.
  - The 45 s call timeout is an estimate; a cold Apps Script start is unmeasured.
  - `combine` over two Room flows can emit once with the new songs and the old `fetchedAt` for an
    instant after a replace; never a partial catalog.
- Next best step: run the device check above, then independent validation.

### Session 043 — 2 October 2026

- Goal: the pending device check of `catalog-repository-cache`.
- Completed (orchestrator, Pixel 5 API 34, user consented to airplane mode): online the app
  fetched 100 songs, 0 rejected, 0 dropped, no crash; the on-device Room database held 100 rows and
  a clean `sync_state`. In airplane mode the app logged `failed Offline`, kept the 100 rows and the
  same `fetched_at`, and recorded the failure for the staleness indicator. Airplane mode restored.
  Status `passing`.
- Next best step: independent validation.

### Session 044 — 2 October 2026

- Goal: independent validation of `catalog-repository-cache`.
- Completed: validator verdict **accept**; status `accepted`. The app now reads the real catalog
  (100 songs) and keeps it offline.
- Verification run (by the validator): gate green, Konsist 12/12, 58 data tests rerun; five probes of
  its own (Q2 both directions, cache wiped on failure, empty list before the cache, error envelope
  as empty catalog) all caught; Pixel 5 reinstall fetched 100 songs; no URL in tree or history.
- Known risk or unresolved issue (moved to `jams-repository-cache`'s notes): unhandled non-IO
  exceptions in the refresh coroutines could crash the app; the data-libraries Konsist rule is
  import-only (the compile classpath backs it up except possibly for serialization-core).
- Next best step: plan `jams-repository-cache`.

### Session 045 — 2 October 2026

- Goal: implement `jams-repository-cache` (spec `docs/specs/jams-repository-cache.md`; its **User
  Approvals** win over the body: D1 sealed `Setlist` and `DataFailure.Storage` approved, **P4 drop
  only the bad row**, G1 the 13th Konsist rule approved, P1, P2, P3, P5, P6, P7, P8 as recommended).
- Completed (all seven spec tasks; no split was needed):
  - `:core:model`: new `Setlist` (`Available(songs, droppedRows)`, `Withheld`,
    `Unavailable(SetlistProblem)`) and `SetlistProblem`; `Jam.setlist: Setlist`, the positions rule
    moved to `Setlist.Available` (≥ 1, unique, ascending, gaps allowed; an empty `Available` with
    dropped rows is rejected) and `Jam` rejects a published jam with a withheld setlist. `JamTest`
    rewritten for P4 (gaps now build), new `SetlistTest`.
  - `:core:data`: `remote/JamDto.kt` (`JamDto`, `SetlistRowDto`, `SlotsDto`, `SetlistErrorDto`),
    `Resources.JAMS`; `jams/` (`JamsRepository`, `JamsSnapshot`, `JamCalendar`,
    `JamsRefreshOutcome` + `toLogLine()`, `JamIssue`/`RejectedJam`, `SetlistIssue`/`SetlistRowIssue`,
    `JamsMapper`, `SetlistMapper`, `DefaultJamsRepository`); `cache/` (`JamEntities.kt` with four
    entities, the `jam_song_resolved` view and `JamWithChildren`, `JamsDao`, `JamCacheMapping.kt`,
    `SyncStates.kt` with the helpers both repositories share); `BluesJamDatabase` version 2;
    `DataFailure.Storage`; `DataScope.create` with a `CoroutineExceptionHandler`;
    `DefaultCatalogRepository` gets the same storage and mapping handling (D5); `dataModule` binds
    `JamCalendar` (Buenos Aires), `JamsDao`, `JamsRepository` and the reporting `DataScope`.
  - `:app`: `BluesJamApp` refreshes catalog and jams concurrently, logs both lines and then `jams
    cache: upcoming …, past …, songs … (… from catalog)`; its scope has an exception handler.
  - Konsist: 13th rule `data-libraries-only-in-core-data-qualified`.
  - Tests: `JamsMapperTest` 19, `JamsDaoTest` 5, `DefaultJamsRepositoryTest` 19,
    `JamsRefreshOutcomeTest` 3, `DataScopeTest` 1, `SetlistTest` 5; `DefaultCatalogRepositoryTest`
    +3 (storage explicit, storage background, mapper exception), `FreshnessTest` (Storage
    round-trip), `DataModuleTest` (jams repository and Buenos Aires calendar), `Fixtures` (jams
    body, jams sample, seed CSV reader).
  - Docs: `docs/domain-model.md` (Setlist states, positions under P4, Buenos Aires day, past slots,
    held-back jams), `CONTEXT.md` (withheld, unavailable), `docs/sheet-schema.md` (jam Mapper rules
    now enforced, with a rules table and P1–P8; `posicion`, `Otros` and `Jam.setlist` rows),
    `docs/risks-and-open-questions.md` (`setlistError` display, jam fixtures and the midnight
    boundary settled), `.claude/skills/architecture/SKILL.md` (model types, jams layout, Room v2,
    the catch rule, 13 Konsist rules).
- Verification run (logs in the session scratchpad, `s045/`):
  - Baseline on untouched HEAD `c01a5f2`: `CI=true ./init.sh` exit 0, three `wired`, 29 files.
  - `./gradlew ktlintFormat` exit 0, then `CI=true ./init.sh` exit 0, `konsist: wired`, `detekt:
    wired`, `ktlint: wired`; 35 result files, 0 failures (see Current Verified State).
    `:core:data` + `:core:model` with `--rerun` five times: 145/145 each. Lint: `:core:data` no
    issues; `:app` the same 14 pin warnings. No baseline, `ignoreFailures`, `@Suppress` or rule
    disable; no catch of `Exception`, `RuntimeException`, `IllegalStateException` or `Throwable`.
  - Robustness red → green: with `DefaultCatalogRepository.kt` still the HEAD blob, the two new
    storage tests failed (`SQLiteFullException` thrown from `refresh()`; the background one escaped
    the refresh); after the fix, 17/17.
  - Failure demonstrations (each an edit of an existing file, restored with the SHA-1 checked):
    Konsist G1 (a qualified `kotlinx.serialization.KSerializer` typealias in `InfoPresenter.kt` →
    "Assert 'data-libraries-only-in-core-data-qualified' was violated (1 time)"); duplicate-date
    rule off → 8 tests fail; P4 "blank the whole setlist" → 3 fail; P4 "renumber" → `expected:<[1,
    3]> but was:<[1, 2]>`; a draft mapped to its setlist → `expected:<[Withheld, Withheld,
    Withheld]> but was:<[Available(…`. Details in `feature_list.json`.
  - Pixel 5 (API 34): the installed accepted build's database was `user_version` 1 with 100
    songs; `./gradlew :app:installDebug` upgraded it (first install time unchanged). Online: cold
    start 797 ms, `jams refresh: updated 1 jams, 0 rejected, 0 setlist issues, 0 held back`,
    `catalog refresh: updated 100 songs, 0 rejected, 0 with dropped fields`, `jams cache: upcoming
    none, past 1, songs 13 (13 from catalog)`; crash buffer and AndroidRuntime empty. Pulled
    database: `user_version` 2, `jam` 1, `jam_song` 13, `jam_slot` 91, `jam_extra` 0,
    `catalog_song` 100, `sync_state` `catalog` and `jams` with `fetched_at` set. Offline (airplane
    mode on through adb, with the user's consent): cold start 745 ms, both refreshes `failed
    Offline`, the `jams cache:` line unchanged, no crash; same rows, same `fetched_at`, newer
    `attempted_at`, `failure` `Offline`. Airplane mode then disabled: `airplane_mode_on` = 0.
  - URL hygiene: no tracked or new file and no log holds the URL.
- Deviations from the spec body, and why:
  - P4 (user approval) replaces the body's "any bad row makes the setlist `INVALID_ROWS`": a bad
    row is dropped and counted in `Setlist.Available.droppedRows`; `PositionGap` does not exist
    (gaps are allowed). Duplicate positions drop **every** row that shares a parsed position,
    valid or not, like P2 for dates, so the mapper never guesses which row owns a write identity.
  - Issues: `SetlistIssue` is a sealed type (`DraftSetlistIgnored`, `SetlistMissing`,
    `SetlistError(code)`, `RowIssues(index, issues)`) and `SetlistRowIssue` carries `dropsRow`.
    `MalformedExtraParticipant` holds no entry text, because an `Otros` entry is a person's name and
    log lines never hold names. A draft that arrives with a `setlistError` is reported as
    `DraftSetlistIgnored` too. Script `setlistError`s are counted as setlist issues in the log.
  - The mapper's output pairs each `Jam` with `slotColumns` (`MappedJam`), the column index the
    cache keeps for each slot, because `Lineup` cannot tell which guitar column a lone guitar is.
  - Both repositories take an optional `mapper` constructor parameter (default the real mapper) so
    the "mapper exception → `InvalidResponse`" path is tested; `dataModule` still passes four.
  - The catalog repository's private freshness helpers moved to `cache/SyncStates.kt`;
    `DefaultCatalogRepositoryTest`'s existing 14 test bodies are unchanged (its `repository()`
    helper now passes a delegating DAO that can fail on demand).
  - The `jams cache:` line counts "from catalog" as setlist songs whose id is in the cached catalog
    (computed in `:app`), since the domain `JamSong` does not say where its title came from.
- Known risk or unresolved issue:
  - The read Flows still throw on a Room read exception (corrupt file); left to `list-states`.
  - No midnight re-split: a screen open across midnight keeps the old split until it collects again.
  - Rejected jams, dropped rows and held-back jams are visible only in logcat until an admin
    surface exists.
  - The live data has no names, `-` or `Otros`; those paths are proven by `jams-edge.json` only.
  - The last-resort handler logs the throwable; an unforeseen exception message could in theory
    carry request data. Every expected failure is already a typed outcome that never holds the URL.
  - The v1 → v2 upgrade drops the cached catalog too (destructive); the next start refetches it.
- Next best step: independent validation of `jams-repository-cache`.

### Session 046 — 2 October 2026

- Goal: repair `jams-repository-cache` after the validator's `revise` (test and docs only; no device
  rerun needed). Status stays `passing`, awaiting re-validation.
- Completed:
  - F1: `JamsDaoTest` gains `a lone guitar keeps the column it was typed in, Guitarra 2 or Guitarra
    1` (a published jam whose row 1 is `-`/`Pedro` and row 2 `Ana`/`-` under the two guitar
    columns, through the mapper, `jamRows`, `replaceJams` and `observeJams`; the guitar slot's
    `column_index` is 1 and 0, and `toDomain()` equals the mapped jam).
  - F3: the `SetlistRowIssue.DuplicatePosition` KDoc now matches the rule (shared with another row,
    every such row dropped, valid or not).
  - F2 (docs only, rule unchanged): the known limits of
    `data-libraries-only-in-core-data-qualified` are stated in its KDoc and the architecture skill.
- Verification run:
  - Red demonstration: `cache/JamCacheMapping.kt` (sha1 `7ad9c125…`) with `zip(columns)` replaced
    by `DEFAULT_INSTRUMENTS.indexOf(instrument) + occurrence index` (the variant that passed all 108
    tests before) → 109 tests, 1 failed: `expected:<{1=1, 2=0}> but was:<{1=0, 2=0}>`. Restored,
    sha1 `7ad9c125f5fdb9069ef83c898492f546c1729997`.
  - `./gradlew ktlintFormat` exit 0; `CI=true ./init.sh` exit 0, three `wired`, Konsist 13/13, 35
    result files, 0 failures; `:core:model:test :core:data:testDebugUnitTest --rerun` 146/146.
- Next best step: re-validation of `jams-repository-cache`.

### Session 047 — 2 October 2026

- Goal: re-validation of `jams-repository-cache`.
- Completed: validator verdict **accept** after one revise round (a lone guitar's Sheet column was
  not guarded through the cache; now a test proves it, red under an independent mutation). Status
  `accepted`. The app now caches jams and setlists offline with withheld drafts, unavailable
  setlists and dropped rows represented in the domain (D1/P4).
- Known risk or unresolved issue: hardening `data-libraries-only-in-core-data-qualified` against
  names split across lines or with backticks needs the user's approval (limits documented). The
  only jam in the Sheet (2026-07-25) is past, so there is no upcoming jam to show until the user
  adds the next date.
- Next best step: the next ready slice in list order.

### Session 048 — 2 October 2026

- Goal: implement `next-jam-read-only-list` (spec `docs/specs/next-jam-read-only-list.md`, with the
  user approvals C1 copy as written, C2 venue as `lugar`, G1 both D-17 Konsist rules).
- Status: `in_progress`, not `passing`: everything but the device check is done and verified; the
  Pixel 5 (`09281FDD4004U6`) was absent from `adb devices` at three checks during the session, so
  spec steps A and B (and the tab switch on the device) have not run.
- Completed:
  - New `:feature:next-jam` (`feature/next-jam/`, package `com.bbbjam.feature.nextjam`):
    `NextJamCopy`, `JamDateText.kt` (`timeRemaining`, `jamDateLabel`), `NextJamUiModel.kt`,
    `NextJamPresenter.kt` (+ pure `JamsSnapshot.toUiModel(today)`), `NextJamScreen.kt` (public
    `NextJamScreen`, internal `NextJamContent`, a `@Preview`), `di/NextJamModule.kt`; tests
    `JamDateTextTest`, `NextJamPresenterTest`, `NextJamModuleTest`, `FakeJamsRepository`.
  - `:app`: `TemporaryTabs.kt` (Próxima jam first, Info), `MainActivity` draws it,
    `BluesJamApp` adds `nextJamModule`, `app/build.gradle.kts` and `settings.gradle.kts` include
    the module.
  - Konsist 13 → 15: `no-material-theme-outside-core-ui` and `amber-roles-allowlisted`
    (`AMBER_ROLE_ALLOWLIST = mapOf("feature/next-jam" to setOf("key"))`). `:feature:info` and
    `:app` pass both unchanged.
  - Docs: architecture `SKILL.md` (module exists, `TemporaryTabs`, today per snapshot, hand-written
    date names, the two rules with their limits and the allowlist convention, 15 rules),
    `references/presenter-pattern.md` (`NextJamPresenter` compiled; the sketch's
    `SetlistRepository`/catalog lookup superseded), `docs/risks-and-open-questions.md` (phrasing
    settled, ticker still open, venue address per C2).
- Verification run:
  - Baseline on untouched HEAD `62375ac`: `CI=true ./init.sh` exit 0, three `wired`, 35 result
    files, Konsist 13/13.
  - After: `./gradlew ktlintFormat` exit 0; `CI=true ./init.sh` exit 0 (19 s on the last run),
    `konsist: wired`, `detekt: wired`, `ktlint: wired`; 38 result files, 0 failures; the 35
    baseline files identical in names and counts except `ModuleIsolationTest` 13 → 15; new
    `JamDateTextTest` 7, `NextJamPresenterTest` 6, `NextJamModuleTest` 1. Lint `:feature:next-jam`
    "No issues found"; `:app` 0 errors, the same 6 GradleDependency + 8 NewerVersionAvailable.
  - Greps on `feature/next-jam/src/main` and `TemporaryTabs.kt`: no `MaterialTheme`, the only amber
    role is `colors.key` (`NextJamScreen.kt`), no `.dp`, no `Color(`/hex, no `LocalDate.now`,
    `Clock.system` or `.now(`, no tú forms, no "Motivo".
  - Failure demonstrations, each an edit of an existing file, restored, SHA-1 checked (`sha1sum`;
    for tracked `InfoScreen.kt` also `git hash-object` = HEAD blob `550c45b0…`):
    1. `MaterialTheme.colorScheme.primary` (+ import) as the tagline color in `feature/info`
       `InfoScreen.kt` → Konsist 15 tests, 1 failed: "Assert 'no-material-theme-outside-core-ui'
       was violated (1 time). Invalid files: └── File InfoScreen.kt …". Restored `35f1d950…`.
    2. `BluesJamTheme.colors.primaryAction` in `InfoScreen.kt` → 1 failed: "Assert
       'amber-roles-allowlisted' was violated (1 time) … InfoScreen.kt". Restored `35f1d950…`.
    3. `BluesJamColors.slotOpen` (+ import) in `InfoScreen.kt`, the `BluesJamColors.` branch alone
       (`Colors` is capitalized, so `colors.` cannot match) → 1 failed, same rule, InfoScreen.kt.
       Restored `35f1d950…`.
    4. `colors.slotOpen` instead of `colors.key` in `NextJamScreen.kt` (allowlisted module, role not
       allowed) → 1 failed, same rule, NextJamScreen.kt. Restored `bd776f70…`.
    5. Copy: `SETLIST_WITHHELD` "armando" → "preparando" in `NextJamCopy.kt` → 14 tests, 1 failed:
       `expected:<…NotShown(message=La lista de temas se está armando. …)> but was:<…se está
       preparando. …>`. Restored `cde25e42…`.
    6. UTC today: `calendar.today()` → `calendar.now().atZone(ZoneOffset.UTC).toLocalDate()` in
       `NextJamPresenter.kt` → 1 failed (`time remaining uses today in Buenos Aires, not UTC`):
       `expected:<[Mañana]> but was:<[Esta noche]>`. Restored `522ce58e…`.
    7. 18:00 split moved to 19:00 in `JamDateText.kt` → 1 failed: `expected:<[Esta noche]> but
       was:<[Hoy]>`. Restored `092643cb…`.
    8. `fetchedAt` guard removed (`false ->`) → 1 failed: `expected:<Loading> but
       was:<NoUpcomingJam(message=La próxima jam todavía no tiene fecha. …)>`. Restored `522ce58e…`.
    After all restores: `:feature:next-jam:testDebugUnitTest` and `:konsist-test:test` green, and
    the final gate above.
  - Device: **not run**. `adb devices` listed nothing (three checks). Nothing was installed.
- Pending device steps (agent, once the Pixel 5 is back): `./gradlew :app:installDebug`; `adb
  shell am force-stop com.bbbjam`; `adb logcat -c`; `adb shell am start -W -n
  com.bbbjam/.MainActivity`; wait for `jams cache:` in `adb logcat -d -s BluesJam`; check the
  crash buffer (`logcat -b crash -d`) is empty; screenshot and `uiautomator dump`. If the line says
  `upcoming none`: step A, the no-upcoming line. If it says `upcoming 2026-10-31` (the user's
  temporary jam): step B, header "Sábado 31 de octubre · 21:00", "La Macanuda", "En N días" (N
  from the device date; 29 on 2 October), 13 rows "01"…"13" with title and a key node whose
  `content-desc` is "Tonalidad <key>", key drawn in amber; scroll to the end. Then tap Info and
  back. Step E (the user deletes the temporary jam; relaunch shows `upcoming none`) is the
  orchestrator's.
- Deviations from the spec, and why:
  - Mapping tests for dropped rows, withheld/unavailable and never-fetched call the pure
    `JamsSnapshot.toUiModel(today)` directly (all 4 `SetlistProblem`s, droppedRows 0/1/2, empty
    `Available`), with Molecule transitions for loading → jam, no-upcoming → jam, never-fetched →
    fetched, withheld → unavailable and the two Buenos Aires clocks.
  - The never-fetched Molecule test does not assert one `Loading` per emission (Molecule may or may
    not re-emit an equal model); it asserts every model before the fetched snapshot is `Loading` and
    then the no-upcoming line.
  - The UTC demonstration uses `calendar.now()` at UTC instead of `LocalDate.now(UTC)` so the fixed
    test clock is used and the failure is exactly the boundary (Mañana vs Esta noche).
- Known risk or unresolved issue:
  - Device evidence (A, B, the tab switch, E) is missing; row height ≥ 56dp, long-title truncation
    and the key's amber are not seen on a device yet.
  - The user may have added the temporary 2026-10-31 jam to the Sheet; if it stays, any musician
    who installs the app sees it (step E pending).
  - First offline launch with an empty cache shows only the background (`Loading`) until
    `list-states`; no midnight ticker; `TemporaryTabs` drops each tab's scroll position.
  - The two new rules are text matches with the documented limits (aliases, `with(MaterialTheme)`,
    Material defaults).
- Next best step: reconnect the Pixel 5 and run the pending device steps; then set `passing` and
  hand to the validator.

### Session 049 — 2 October 2026

- Goal: the device check of `next-jam-read-only-list`.
- Completed (orchestrator, Pixel 5): with the user's temporary jam 2026-10-31 in the Sheet, the app
  showed "Sábado 31 de octubre · 21:00", "La Macanuda", "En 29 días" and all 13 rows with amber keys
  and "Tonalidad <key>" descriptions; the temporary tabs switch to Info and back; no crash. Status
  `passing`.
- Known risk or unresolved issue: the temporary jam must be deleted by the user (step E) once
  validation is done — until then any musician would see it.
- Next best step: independent validation, then step E.

### Session 050 — 2 October 2026

- Goal: independent validation of `next-jam-read-only-list`.
- Completed: validator verdict **accept**; status `accepted`. The validator reproduced the device
  evidence (header, 13 rows, amber keys at #F4B63F in Display P3, ~69dp rows) and confirmed the
  copy by script.
- Known risk or unresolved issue: step E pending (temporary jam still in the Sheet). Gate gaps
  found: an amber role added to the allowlist map passes the gate (reviewer-only), and a hardcoded
  dp literal is caught by nothing but per-spec greps (noted on `list-states`).

### Session 051 — 2 October 2026

- Goal: step E of `next-jam-read-only-list`.
- Completed: the user deleted the temporary jam; on the Pixel 5 the app now logs `upcoming none` and
  shows the approved no-upcoming line. The Sheet holds only the real 2026-07-25 jam again.

### Session 052 — 2 October 2026

- Goal: implement `instrument-strip-component` (spec `docs/specs/instrument-strip-component.md`,
  with its User Approvals: C1 copy as written; E1 extras **shown** after the slot chips in a distinct
  style; P1 static dot; D1 `material-icons-core` declared; amber allowlist unchanged).
- Status: `in_progress`, not `passing`. Everything but spec step C is done and verified. Step C
  needs an upcoming jam with names and an `Otros` cell; the Pixel 5 logs `jams cache: upcoming none,
  past 1, songs 13`, so the strip is not on screen. Pending the user's steps A/B (temporary
  `2026-10-31` jam with names, a long name, an all-filled song and `Juan (saxo)` in `Otros` on song
  1), then C (colour, greyscale, font scale 1.3 restored to 1.0, uiautomator), then D (cleanup).
- Completed:
  - `:core:ui` `com.bbbjam.core.ui.strip`: `InstrumentChipUiModel.kt` (with `InstrumentChipKind`),
    `InstrumentStripCopy.kt`, `LineupChips.kt`, `InstrumentStripDefaults.kt`, `InstrumentStrip.kt`
    (previews all-open, all-filled with a long name, mixed, mixed with two extras). Tests
    `LineupChipsTest` (9) and `InstrumentStripDefaultsTest` (5); `ContrastTest` + `open slot on its
    chip fill` (7.03) and `muted text on a filled chip` (7.22). An extra chip has no fill, so its
    contrast is the existing `muted text on a surface` (10.10); no duplicate test added.
  - `gradle/libs.versions.toml` + `androidx-compose-material-icons-core` (BOM-managed);
    `core/ui/build.gradle.kts` `implementation` of it.
  - `:feature:next-jam`: `SongRowUiModel.instruments` (last, no default), presenter maps
    `lineup.toInstrumentChips(extraParticipants)`, `SongRow` is a `Column` (title line `Row` with
    `fillMaxWidth`, then `InstrumentStrip`), preview rows with strips; `NextJamPresenterTest`
    expected rows gain the seven default chips, new case `each row carries its strip, slots in
    column order and then the extras` (scenario 2 + `Juan (saxo)`, plus an empty lineup).
  - Docs: `DESIGN.md` Instrument strip (static dot, filled text at full `textMuted`, extras style,
    "three signals" corrected to glyph and wording, the per-chip glyph question closed);
    architecture `SKILL.md` (strip as the shared-component example, shared copy in `:core:ui`, amber
    inside `:core:ui` needs no allowlist entry, the explicit icons dependency);
    `docs/risks-and-open-questions.md` (correction, pulse decision, amber saturation risk).
- Deviation from the spec body (the approvals win): the chip carries `kind:
  InstrumentChipKind` instead of `isOpen: Boolean` (a third, extra, style needed a third state;
  `isOpen` stays as a derived property), and the mapper is `Lineup.toInstrumentChips(extras)`.
  The extra's "+" is the first character of its label rather than a separate drawn icon, so the
  label is exactly `+ saxo: Juan` as approved. Chip styles live in `InstrumentStripDefaults.style`
  so a JVM test can check them.
- Verification run:
  - Baseline on untouched HEAD `d4e85f0`: `CI=true ./init.sh` exit 0, three `wired`, 38 files.
  - After: `./gradlew ktlintFormat` exit 0; `CI=true ./init.sh` exit 0, `konsist: wired` (15/15),
    `detekt: wired`, `ktlint: wired`; 40 result files, 220 tests, 0 failures (counts above).
  - Greps on `feature/next-jam/src/main` and `core/ui/.../strip`: no `.dp` literal, no `Color(`, no
    `MaterialTheme.`, no `animate`/`InfiniteTransition`, no tú forms; `slotOpen` appears only in
    `InstrumentStripDefaults.kt`.
  - Failure demonstrations, each an edit of an existing file, restored from a copy, `sha1sum -c`
    OK (`LineupChips.kt` `d3a18471…`, `InstrumentStripDefaults.kt` `80f1f8c2…`, `NextJamScreen.kt`
    `ee5d6197…`):
    1. `slots.reversed().map` in the mapper → `:core:ui` 37 tests, 5 failed (`LineupChipsTest`:
       default order, second guitar, mixed, extras, without extras) and `:feature:next-jam` 15, 4
       failed (every `NextJamPresenterTest` case with rows), `expected:<[…GTR: LIBRE…]> but was:<…>`.
    2. Extras mapped as slot chips (`kind = OPEN_SLOT`) → `:core:ui` 2 failed (`an extra never
       looks like an open slot`, `extras come after every slot chip…`) and `NextJamPresenterTest`
       1 failed (`each row carries its strip…`).
    3. `BluesJamTheme.colors.slotOpen` for the key in `NextJamScreen.kt` → Konsist 15, 1 failed:
       "Assert 'amber-roles-allowlisted' was violated (1 time). Invalid files: └── File
       NextJamScreen.kt".
    4. `OPEN_FILL_ALPHA = 0.9f` → `ContrastTest` `open slot on its chip fill`: "contrast
       1.196784838139893 is below AA text (4.5)"; `InstrumentStripDefaultsTest` open style fails.
    5. Extra text `slotOpen` → `InstrumentStripDefaultsTest` `an extra never uses amber or the slot
       fills`: "Values should be different. Actual: Color(1.0, 0.7019608, 0.0, 1.0 …)" and `an extra
       has no fill, muted text and no slot glyph` fail.
    6. Extra glyph `DOT` → `an extra has no fill, muted text and no slot glyph`: "expected:<NONE>
       but was:<DOT>".
  - Device (Pixel 5 `09281FDD4004U6`, API 34): `./gradlew :app:installDebug` exit 0, force-stop,
    `logcat -c`, `am start -W` COLD 912 ms; `BluesJam`: `catalog refresh: updated 100 songs…`,
    `jams refresh: updated 1 jams…`, `jams cache: upcoming none, past 1, songs 13 (13 from
    catalog)`; crash buffer and AndroidRuntime empty; screenshot (scratchpad) shows the approved
    no-upcoming line. `font_scale` read 1.0 and was not changed. Steps A–D not run.
- Next: the user runs steps A/B (including the `Otros` cell); then step C on the device, step D
  cleanup, and only then `passing`.

### Session 053 — 2 October 2026

- Goal: the device check of `instrument-strip-component`.
- Completed (orchestrator, Pixel 5): with the user's temporary jam carrying names and two `Otros`
  entries, the strip showed open, filled and extra chips in column order with one accessibility
  description each; at font scale 1.3 (restored to 1.0) the chips wrap without clipping. Status
  `passing`.
- Known risk or unresolved issue: a fully open song shows seven amber chips (song 4) — the amber
  density risk is for the user to judge; step D (delete the temporary jam) pending.

### Session 054 — 2 October 2026

- Goal: independent validation of `instrument-strip-component`.
- Completed: validator verdict **block**, on one human step only: the spec makes the cleanup of the
  temporary jam part of the device check. Code, copy, probes and device evidence all held; the
  validator also measured greyscale (open vs filled fills 50 vs 53 luma), confirming that glyph and
  wording carry the distinction. Status back to `in_progress`.
- Known risk or unresolved issue: a chip whose content description is cleared passes the gate (no
  Compose semantics test harness yet).
- Next best step: the user deletes the temporary jam; the orchestrator confirms the no-upcoming line
  and asks the validator for a narrow recheck.

### Session 055 — 2 October 2026

- Goal: step D of `instrument-strip-component`.
- Completed: the user deleted the temporary jam; on the Pixel 5 the app logs `upcoming none` and
  shows the no-upcoming line. Status `passing` again for the validator's recheck.

### Session 056 — 2 October 2026

- Goal: close `instrument-strip-component`.
- Completed: validator verdict **accept** after the narrow recheck; status `accepted`. The user kept
  the seven-amber-chip rows "for now" (recorded in the risks doc).

### Session 057 — 3 October 2026

- Goal: implement `song-row-expansion` (spec `docs/specs/song-row-expansion.md`, with its User
  Approvals: C1 copy as written with `contraído`; I1 the panel replaces the strip when expanded; I2
  open slots available but not interactive for musicians, compact lines, DESIGN.md's "tappable"
  moved to the admin view; H1 hint included; **T1 declined**: no Robolectric or Compose UI test
  dependencies).
- Status: `in_progress`, not `passing`. Everything but spec step C is done and verified. Step C
  needs an upcoming jam; the Pixel 5 logs `jams cache: upcoming none, past 1, songs 13`, so there is
  no row to expand. Pending: the user's steps A/B (temporary `2026-10-31` jam with names, a long
  name, an all-filled song and `Juan (saxo)` in `Otros`), then C (uiautomator bounds before/after,
  panel nodes, two rows expanded, a tap on a panel line doing nothing), then D (cleanup).
- Completed:
  - `:core:ui` `com.bbbjam.core.ui.lineup`: `LineupPanelUiModel.kt`, `LineupPanelMapper.kt`,
    `LineupPanelCopy.kt`, `LineupPanelDefaults.kt`, `LineupPanel.kt`, `ExpandIndicator.kt`;
    `InstrumentStripCopy.name()` no longer private. Tests `LineupPanelMapperTest` (7) and
    `LineupPanelDefaultsTest` (3); `ContrastTest` follow-up: `open slot on its chip fill` now
    composites `InstrumentStripDefaults.style(OPEN_SLOT).fill` (7.03), new `text on a filled slot`
    (measured 9.5196, recorded 9.52).
  - `:feature:next-jam`: `ExpandedRows.kt` (+ `ExpandedRowsTest`, 5), `SongRowUiModel` gains
    `artist`, `isExpanded`, `stateDescription`, `toggleLabel`, `lineup`, `events`; presenter state in
    `rememberSaveable`; `NextJamCopy` row state and action; `NextJamScreen` header/panel split
    (`SongRow`, `RowHeader`, `TitleLine`, preview rows from a helper). `NextJamPresenterTest`
    expected rows gain the new fields (helper `row(...)`), plus four cases: `rows start collapsed, a
    tap expands only that row, and two rows stay expanded at once`; `expansion follows the position
    through a refresh, and a new jam starts collapsed`; `an earlier model's handler still writes
    through the same state after a refresh`; `an expanded row carries the artist and its lineup with
    open slots before filled ones`.
  - Docs: `DESIGN.md` "Song row, expanded" (header-only toggle, panel replaces strip, sections,
    compact non-interactive lines, "tappable" only in the admin view, motion, screen readers);
    architecture `SKILL.md` (the `lineup` package, per-row state in the parent presenter, never
    recreate it, no Compose UI test harness); `references/presenter-pattern.md` (the
    `SongRowPresenter` sketch superseded for expansion, the call-order pitfall);
    `docs/risks-and-open-questions.md` (the T1 gap, panel height, no auto-scroll, tab switch).
- Deviations from the spec body (the approvals win where they differ): `LineupPanelUiModel` has a
  fifth field, `hint: String?` (H1 is conditional on an open slot, so the mapper decides it and the
  `UiModel` carries it); an extra's "+" is part of its instrument text (`+ saxo`), as in the strip's
  label, because `InstrumentStripDefaults` gives extras no glyph; `ExpandIndicator` lives in its own
  `ExpandIndicator.kt` (detekt `TooManyFunctions` 12 > 11 in `LineupPanel.kt`; split, not
  suppressed). No Robolectric/semantics test, per T1.
- Verification run:
  - Baseline on untouched HEAD `bcccd0e`: `CI=true ./init.sh` exit 0, three `wired`, 40 result
    files, 220 tests, 0 failures.
  - After: `./gradlew ktlintFormat` exit 0; first gate exit 1 on detekt `TooManyFunctions` (fixed
    by the split above); then `CI=true ./init.sh` exit 0, `konsist: wired` (15/15), `detekt:
    wired`, `ktlint: wired`; 43 result files, 240 tests, 0 failures (counts above).
  - Greps on `feature/next-jam/src/main` and `core/ui/.../lineup`: no `.dp`/`.sp` literal, no
    `Color(` literal, no `MaterialTheme.`, no `clickable`/`Role`/`onClick`/`heightIn` in `lineup/`,
    no "Pedir", no tú forms; `slotOpen` only in `LineupPanelDefaults.kt` (and the strip's defaults).
  - Failure demonstrations, each an edit of an existing file, run, restored from a copy, SHA-1
    equal before and after (`NextJamPresenter.kt` `f14bdedd…`, `ExpandedRows.kt` `c93863f2…`,
    `LineupPanelMapper.kt` `5b2ed0ea…`, `NextJamCopy.kt` `07bbf445…`, `InstrumentStripDefaults.kt`
    `80f1f8c2…`, `NextJamScreen.kt` `e9f38c34…`):
    1. Expansion keyed by list index (`songs.mapIndexed`, `isExpanded(date, index)`) →
       `NextJamPresenterTest` 2 of 11 failed: `expansion follows the position through a refresh…`
       (expected row 02 expanded and row 03 collapsed; got row 02 collapsed and row 03 expanded:
       the expansion moved to the neighbour) and `an expanded row carries the artist…`
       ("expected:<true> but was:<false>").
    2. State recreated, `remember(snapshot) { mutableStateOf(ExpandedRows.NONE) }` → 2 failed: `an
       earlier model's handler still writes through the same state after a refresh`
       ("TurbineAssertionError: No value produced in 3s") and the refresh test (row 02 collapsed).
    3. Toggle replacing the set (`copy(positions = setOf(position))`) → `ExpandedRowsTest` `two
       positions stay expanded together…` ("expected:<ExpandedRows(jamDate=2026-10-31,
       positions=[1, 3])> but was:<…positions=[3])>") and `NextJamPresenterTest` `rows start
       collapsed… two rows stay expanded at once` ("expected:<[1, 3]> but was:<[3]>").
    4. Open slots not first (`slots.partition { !it.isOpen }`) → `:core:ui` 48 tests, 6 failed
       (every `LineupPanelMapperTest` case with slots) and `:feature:next-jam` 24, 8 failed (every
       presenter case with rows).
    5. Extras counted as open (`openSlots = open.map … + extras.map …`) → `LineupPanelMapperTest`
       `open slots first…` and `extras are never open nor filled slots…`, `NextJamPresenterTest`
       `an expanded row carries…` and `each row carries its strip…`.
    6. Copy change, `ROW_COLLAPSED = "colapsado"` → `NextJamPresenterTest` 6 failed (every case
       comparing rows).
    7. The drawn fill changed in `InstrumentStripDefaults.style` (`alpha = 0.9f`, constant
       `OPEN_FILL_ALPHA` untouched, so the old ContrastTest would have passed) → `ContrastTest`
       `open slot on its chip fill`: "contrast 1.196784838139893 is below AA text (4.5)", plus
       `InstrumentStripDefaultsTest` and `LineupPanelDefaultsTest` open styles.
    8. `BluesJamTheme.colors.slotOpen` for the artist in `NextJamScreen.kt` → Konsist 15, 1 failed:
       "Assert 'amber-roles-allowlisted' was violated (1 time). Invalid files: File
       NextJamScreen.kt". The helper script crashed printing this message, before its own restore;
       the file was restored by hand from the copy and SHA-1 `e9f38c34…` confirmed.
    Not demonstrable without T1: dropping the header's `stateDescription`, a line's
    `clearAndSetSemantics`, or drawing the strip while expanded passes the gate (risks doc).
  - Device (Pixel 5 `09281FDD4004U6`): `./gradlew :app:installDebug` exit 0, force-stop,
    `logcat -c`, `am start -W` COLD 609 ms; `BluesJam`: `catalog refresh: updated 100 songs…`,
    `jams refresh: updated 1 jams…`, `jams cache: upcoming none, past 1, songs 13 (13 from
    catalog)`; crash buffer and AndroidRuntime empty; `uiautomator dump` shows only the
    no-upcoming line and the two tabs. `font_scale` read 1.0; no setting was changed. Steps A–D not
    run.
- Next: the user runs steps A/B; then step C on the device, step D cleanup, and only then `passing`.

### Session 058 — 3 October 2026

- Goal: step C of `song-row-expansion`.
- Completed (orchestrator, Pixel 5): rows above a tapped row do not move; the panel replaces the
  strip with artist, open slots first, the hint, filled slots and extras; tapping an open slot does
  nothing; two rows expand at once; expansion survives rotation; font scale 1.3 wraps cleanly. All
  settings changed for the check were restored (rotation automatic, user_rotation 0, font 1.0).
  The user's malformed `Otros` entry ("Uno") was dropped and logged, as approved. Status `passing`.
- Known risk or unresolved issue: the header's expanded/collapsed state description cannot be read
  by uiautomator and there is no semantics harness (T1 declined) — unverified on device.

### Session 059 — 3 October 2026

- Goal: independent validation of `song-row-expansion`.
- Completed: validator verdict **block**, on step D only; status back to `in_progress`. Code, copy,
  probes and device behaviour ready to accept.
- Known risk or unresolved issue: the header's state description and click label cannot be verified
  by uiautomator (T1 declined); the approval assumed they could — the user is told. Optional
  follow-ups: a copy test for the panel headings; ellipsis for a very long extra instrument.

### Session 060 — 3 October 2026

- Goal: step D of `song-row-expansion` and close it.
- Completed: the user deleted the temporary jam; the Pixel 5 shows the no-upcoming line again.
  Status `accepted` per the validator's ruling.

### Session 061 — 4 October 2026

- Goal: implement `instrument-filter-chips` (spec `docs/specs/instrument-filter-chips.md` with its
  User Approvals, which win: C1 copy with the heading `Filtrá por cupo libre`; **F1 multi-select
  with OR** (checkbox chips, `Todos` checked exactly when nothing is selected and clears, "Ver todos
  los temas" clears, names joined lowercase in chip order with ", " and " o "); F3 zero-count chips
  tappable; F4 superseded; F5 selection not tied to the jam date; S1 no-results shipped here).
- Status: `passing` (step C done on the device). **Step D pending**: the user deletes the temporary
  `2026-10-31` row and tab; then a relaunch must log `upcoming none`.
- Validator (commit a2c2c10): **accept, conditional on step D**. It reran the gate (exit 0, three
  wired), showed an `any`→`all` mutation fails 3 tests, and confirmed the corrected count line on
  the Pixel 5 after a reinstall, with no device setting changed.
- Step D done: the user deleted the test jam; the relaunch logs `upcoming none` and shows the
  no-upcoming line. **Status `accepted`.**
- Completed:
  - `:core:ui` `com.bbbjam.core.ui.filter`: `InstrumentFilterUiModel.kt`
    (`InstrumentFilterBarUiModel` with `Event.Clear`, `FilterChipUiModel` with `Event.Toggle`),
    `InstrumentFilterChange.kt` (`Toggle(instrument)`, `Clear`, `Set<Instrument>.updatedBy`),
    `InstrumentFilterMapper.kt` (`instrumentFilterBar(lineups, selected, onChange)`,
    `Lineup.matchesInstrumentFilter`), `InstrumentFilterCopy.kt`, `InstrumentFilterDefaults.kt`,
    `InstrumentFilterBar.kt` (FlowRow of `toggleable(Role.Checkbox)` chips at least 48dp tall,
    heading, live-region count line, no-results block, five `@Preview`s). Tests
    `InstrumentFilterMapperTest` (8), `InstrumentFilterDefaultsTest` (3), `ContrastTest` +2
    (`onActiveFilter` on `activeFilter` 9.52, `textMuted` on `surfaceRaised` 9.66).
  - `:feature:next-jam`: `Songs.filterBar`; presenter state `Set<Instrument>` with
    `InstrumentFilterSaver`; `toUiModel(…, filter, onFilterChange)`; screen item `"filter"` after
    the header. `NextJamPresenterTest` expectations gain the bar (`FilterBarExpectations.kt`), and
    the filter cases live in the new `NextJamFilterTest` (10).
  - Docs: `DESIGN.md` "Filter chip" (behaviour, layout, semantics, state, component); architecture
    `SKILL.md` (the `filter` package, change-not-set handlers, presenter-held selection);
    `docs/risks-and-open-questions.md` (bar height, not sticky, extra amber, lost on relaunch and tab
    switch, live region and TalkBack unverified).
- Deviations from the spec body (approvals and tooling): multi-select replaces the body's
  single-select and `Role.RadioButton` (approval F1); the chip event is `Toggle` and handlers send
  an `InstrumentFilterChange` instead of `onSelect(Instrument?)`, because an unchanged chip keeps
  an equal model and Compose may reuse its old handler, so a handler carrying a computed set would
  undo another chip (tested: a stale `Voz` handler adds to the current `Bajo`); no
  `selectableGroup` (a radio-group notion); the row predicate is shared from `:core:ui` so the count
  line always equals the rows drawn; the filter cases are in `NextJamFilterTest`, not
  `NextJamPresenterTest`, because detekt `LargeClass` (600 lines) failed the latter (split, not
  suppressed); `InstrumentFilterChange` has its own file (detekt `MatchingDeclarationName`).
  Singular rule, **corrected after the device run** (the orchestrator fixed an error in the
  approval): the count line's noun agrees with the total, so "1 de 13 temas…" and "tema" only for a
  one-song setlist ("1 de 1 tema…"). The chip descriptions keep agreeing with their own count
  ("Bajo: 1 tema con cupo libre"), as approved. The spec's demonstration "selected style `slotOpen`" cannot fail by value
  (`slotOpen` and `activeFilter` are the same amber); instead the defaults test pins the exact roles
  and that no filter chip uses the open-slot chip's fill or text colour.
- Verification run:
  - Baseline at `71f44b3`: `CI=true ./init.sh` exit 0, 43 files, 240 tests.
  - `./gradlew ktlintFormat` exit 0. First gate exit 1 (detekt: MagicNumber in previews,
    MatchingDeclarationName, MaxLineLength, LargeClass), fixed by restructuring. Final
    `CI=true ./init.sh` exit 0, three `wired`, Konsist 15/15, 46 files, 263 tests, 0 failures.
  - Greps on `feature/next-jam/src/main` and `core/ui/.../filter`: no `.dp`/`.sp` literal, no
    `Color(`, no `MaterialTheme.`, no `horizontalScroll`/`LazyRow`, no tú forms; `activeFilter` only
    in `InstrumentFilterDefaults.kt` (plus tokens, showcase, tests, Konsist). No Gradle, Konsist,
    `init.sh` or dependency change; no Robolectric/ui-test.
  - Failure demonstrations (edit an existing file, run, restore from a copy, SHA-1 equal before and
    after: `InstrumentFilterMapper.kt` `4377ab26…`, `NextJamPresenter.kt` `bf21864e…`,
    `InstrumentFilterCopy.kt` `ced1cc0c…`, `NextJamScreen.kt` `77c7267e…`):
    1. AND (`selected.all`) → `InstrumentFilterMapperTest` "expected:<[3 de 5 temas]…> but
       was:<[1 de 5 tema]…>"; `NextJamFilterTest` 2 failed "expected:<[2, 3, 6]> but was:<[3]>".
    2. Extras counted as open in the presenter's filter → `NextJamFilterTest` 7 of 10 failed, the
       D-18 case "expected:<false> but was:<true>", others "expected:<[2, 3]> but was:<[1, 2, 3]>".
    3. `Todos` never checked → `InstrumentFilterMapperTest` 1, `NextJamFilterTest` 5
       ("expected:<[Todos]> but was:<[]>"), `NextJamPresenterTest` 5.
    4. " y " for " o " → "expected:<…para bajo [o] voz> but was:<…para bajo [y] voz>" (mapper 1,
       filter test 2).
    5. `BluesJamTheme.colors.activeFilter` in `NextJamScreen.kt` → Konsist "Assert
       'amber-roles-allowlisted' was violated (1 time). Invalid files: File NextJamScreen.kt".
    6. Selection in `remember(snapshot)` → "the selection survives a refresh…" "expected:<[2]> but
       was:<[1, 2, 3, 4, 5, 6, 7]>" and the stale-handler test "TurbineAssertionError: No value
       produced in 3s".
  - Device (Pixel 5 `09281FDD4004U6`), step C with the user's jam (details in the feature's
    evidence): counts Todos 13, Guitarra 13, Bajo 2, Batería 13, Voz 1, Armónica 0, Teclados 13
    (the user's data, not the spec's suggested numbers); Bajo → rows 03, 07 and "2 de 13 temas con
    cupo libre para bajo"; + Voz → 03, 07, 09 and "…para bajo o voz"; − Bajo → 09, "1 de 13 tema…" (the old singular rule; see the
    correction below);
    Todos clears; expansion kept for shown and hidden rows; Armónica → no-results and the 48dp
    action, which clears; Bajo survives rotation; at 360dp the chips wrap to 3 rows at font 1.0 and
    1.3, all 48dp. Originals recorded and restored: font_scale 1.0, accelerometer_rotation 1,
    user_rotation 0, density 440 (`wm density reset`). Crash buffer empty.
  - Copy correction after the device run (copy-only, no device rerun): `InstrumentFilterCopy.summary`
    now picks "tema"/"temas" from the total, not the matched count; expectations written out
    literally ("1 de 5 temas…", "1 de 7 temas…", "1 de 1 tema…"). Demonstration: the matched-count
    rule restored for a run → `InstrumentFilterMapperTest` 1 failed "expected:<1 de 5 tema[s] con
    cupo libre para...> but was:<1 de 5 tema[] con cupo libre para...>", `NextJamFilterTest` 2
    failed (the per-instrument case and "expected:<1 de 7 tema[s]…> but was:<1 de 7 tema[]…>");
    `InstrumentFilterCopy.kt` restored, SHA-1 `ea7ed9f6…` before and after. `./gradlew
    ktlintFormat` exit 0; `CI=true ./init.sh` exit 0, three `wired`, 46 files, 263 tests, 0
    failures.
- Known risk or unresolved issue: step D; live regions and TalkBack wording unverified (T1).
- Next: the user deletes the temporary jam (step D) and tells the orchestrator; then the validator.

### Session 062 — 4 October 2026

- Goal: implement `list-states` (spec `docs/specs/list-states.md`; User Approvals answered 4
  October 2026: C1 copy as written, K1 the Konsist rule `no-dp-literal-outside-core-ui` in this
  slice, demonstrated failing first, S1 catch `SQLException` in the jams read flow).
- Status: **`accepted`** — the validator reran the gate, three mutations of its own and the four
  Pixel 5 steps (commit 071755e).
- Completed:
  - `:core:data`: `DefaultJamsRepository.observeJams()` ends in `Flow.catch` for
    `android.database.SQLException` only: one `JamsSnapshot(null, [], Freshness(null,
    Storage(<class>), false))`, then completes; anything else is rethrown. KDoc on
    `JamsRepository.observeJams()`. Catalog flow untouched.
  - `:core:ui` `com.bbbjam.core.ui.state`: `ListStateUiModel.kt` (`EmptyStateUiModel`,
    `ListErrorUiModel`, `StalenessNoticeUiModel`, each retry an `Event.Retry`), `ListStateMapper.kt`
    (`listError`, `stalenessNotice`, negative age clamped), internal `ListStateCopy.kt` and
    `ListStateDefaults.kt` (only reader of `primaryAction`), `SkeletonList.kt` (static, one
    semantics node) and `ListStateBlocks.kt` (`ListErrorBlock`, `StalenessNotice`,
    `EmptyStateBlock`, `ListStatePreviewFrame`), six `@Preview`s.
  - `:feature:next-jam`: `Loading(description)`, `Failed(error)`, `NoUpcomingJam(empty,
    staleness)`, `Jam(header, setlist, staleness)`, `SetlistUiModel.Empty(empty)`; presenter
    Decision 1 table, Retry (subscription counter + `rememberCoroutineScope().launch {
    refresh() }`), `now` from `JamCalendar` per snapshot; screen draws the four components (notice
    above the header); `NextJamStatesPreview.kt` with seven previews built through `toUiModel`.
    Copy in `NextJamCopy` (`LOADING`, `LOAD_FAILED`, `NO_UPCOMING_TITLE`, `EMPTY_SETLIST_TITLE`,
    `EMPTY_SETLIST`).
  - `:konsist-test`: `no-dp-literal-outside-core-ui` (16th rule).
  - Docs: `DESIGN.md` "Required States" (how each state is drawn, no notice on age alone, static
    skeleton, components and package); architecture `SKILL.md` (the `state` package as the fourth
    example, the retry pattern, the jams read-exception behaviour, the 16th rule);
    `docs/risks-and-open-questions.md` (frozen age, no automatic refresh after 30 min, catalog
    flow still throws, previews not rendered, TalkBack unverified); `docs/technical-discovery.md`
    (how screens read `Freshness`). `CONTEXT.md`, `AGENTS.md`, `domain-model.md`: not needed
    (spec).
- Deviations from the spec body: the composables are in two files, `SkeletonList.kt` and
  `ListStateBlocks.kt`, not one (detekt `TooManyFunctions`, split rather than suppressed);
  `toUiModel` gains `now` (default: the fetch time) and `onRetry` as trailing defaulted
  parameters so existing call sites keep compiling; demonstration 3 ("retry button reading
  `slotOpen`") cannot fail by colour value (every amber role is the same colour), so
  `ListStateDefaultsTest` reads the roles from `ListStateDefaults.kt`'s source, the way
  `WindowBackgroundTest` reads `colors.xml`; the spec's `Songs.filterBar` stays nullable, but the
  presenter no longer has a `songs.isEmpty()` branch for it (that case is now `Empty`).
- Verification run:
  - Baseline at `660c09f`: `CI=true ./init.sh` exit 0, 46 files, 263 tests, Konsist 15/15.
  - S1 red → green: the new read-failure test failed with `android.database.sqlite.SQLiteException`
    before the catch, passed after (`DefaultJamsRepositoryTest` 21/21).
  - `./gradlew ktlintFormat` exit 0. First gate exit 1 (detekt MagicNumber in preview dates,
    TooManyFunctions), fixed by restructuring. Final `CI=true ./init.sh` exit 0, three `wired`,
    Konsist 16/16, 49 files, 290 tests, 0 failures (per class in the feature's evidence).
  - K1 shown failing first: `4.dp` in `NextJamScreen.kt` → "Assert 'no-dp-literal-outside-core-ui'
    was violated (1 time). Invalid files: File NextJamScreen.kt"; restored (SHA-1 `dc8b595b…`).
  - Demonstrations 1–6 (isStale notice, Failed while refreshing, retry on `slotOpen`,
    `primaryAction` in the screen, no catch, copy change) each failed as expected and were restored
    by SHA-1; messages in the feature's evidence.
  - Greps: no dp/sp literal, `Color(`, `MaterialTheme.`, progress indicator, infinite transition or
    tú form in `feature/next-jam/src/main` and `core/ui/.../state`; `primaryAction` only in
    `ListStateDefaults.kt`.
  - Pixel 5: steps (a)–(d) of the spec with airplane mode (on and off twice, restored to 0; wifi,
    mobile data, screen timeout and accessibility unchanged), app data cleared once. Error block
    with a 48dp full-width `Reintentar`; skeleton captured right after Retry; notice "Sin conexión /
    Mostrando lo guardado hace 1 minuto. / Reintentar" above "Todavía no hay fecha"; notice gone
    after an online Retry. `Actualizando…` too brief to capture. Crash buffer empty.
- Known risk or unresolved issue: notice age frozen while the screen stays open; no TalkBack run;
  previews not rendered; catalog read flow still throws (no screen reads it).
- Next: the validator for `list-states`.

### Session 063 — 4 October 2026

- Goal: implement `song-detail-screen` (spec `docs/specs/song-detail-screen.md` at commit
  `72282d9`; User Approvals answered 4 October 2026: N1 Navigation Compose 2.9.8 in `:app` only,
  K1 `keyDisplay` 96sp, G1 grouped by instrument, C1 copy as written, R1 string routes, K2 the
  Konsist rule `navigation-only-in-app`, demonstrated failing first).
- Status: **`passing`** — implemented and self-verified; not accepted. Awaiting the validator and
  the device steps that need a temporary upcoming jam (listed below). The session resumed after a
  rate-limit cut; the tree was clean at resume, so nothing from the cut attempt survived.
- Completed:
  - Catalog `navigationCompose = "2.9.8"` + `androidx-navigation-compose`, with the pin's reason;
    `app/build.gradle.kts` declares it and `:feature:song-detail`; `settings.gradle.kts` includes
    the module.
  - `:core:ui`: `BluesJamTypography.keyDisplay` (Barlow Condensed ExtraBold 96/96sp);
    `com.bbbjam.core.ui.nav` (`BackButton`, `BackUiModel` + `Event.Back`, `backUiModel(onBack)`,
    internal `NavCopy.BACK = "Volver"`); `com.bbbjam.core.ui.lineup` `InstrumentGroups`,
    `InstrumentGroupsUiModel`/`InstrumentGroupUiModel`, `Lineup.toInstrumentGroups(extras)`;
    `LineupPanel.kt`'s `Line` internal with `showInstrument`, its instrument text ellipsized and
    bounded by `LineupPanelDefaults.INSTRUMENT_MAX_WIDTH` (160dp; the song-row-expansion
    validation note); the panel's note/hint and line builders shared as internal functions.
  - `:feature:song-detail` (new): `SongDetailPresenter` (`Params(jamDate, position, onBack)`,
    `onBack` through `rememberUpdatedState`), pure `JamsSnapshot.toSongDetail`, `SongDetailUiModel`
    (`Loading`, `NotFound`, `Song` with exactly title, artist?, keyLabel, key, keyDescription,
    lineup, back), `SongDetailScreen` + `SongDetailContent` and three previews, `SongDetailCopy`,
    `SongDetailDefaults`, `songDetailModule`; own `FakeJamsRepository` and fixtures.
  - `:feature:next-jam`: `SongRowUiModel.detailLabel` + `Event.OpenDetail`;
    `NextJamPresenter.Params(onOpenSong)` read through `rememberUpdatedState`; `toUiModel(…,
    onOpenSong)`; the expanded row draws "Ver detalle del tema" under the panel (underlined `text`,
    48dp, `Role.Button`); `NextJamScreen(onOpenSong = …)`; `NextJamCopy.OPEN_DETAIL`.
  - `:app`: `navigation/AppRoutes.kt` (`TABS`, `SONG_DETAIL = "song/{jamDate}/{position}"`,
    `songDetail`, `parseSongDetail`), `navigation/AppNavHost.kt` (two destinations, no
    transitions, the double-tap guard on `RESUMED`, defensive pop on bad arguments),
    `MainActivity` sets `AppNavHost()`, `TemporaryTabs(onOpenSong)`, `songDetailModule` in
    `startKoin`; `AppRoutesTest`.
  - `:konsist-test`: `navigation-only-in-app` (17th rule); `AMBER_ROLE_ALLOWLIST`
    `feature/song-detail -> {key}`.
  - Docs: `DESIGN.md` (`keyDisplay` in the front matter and Typography, the row's detail action,
    the panel's width bound, a "Song detail" section with the G1 exception); architecture
    `SKILL.md` (module table and feature list, the fifth `:core:ui` example, navigation callbacks in
    `Params`, the navigation pin, the 17th rule, the allowlist, "Still Open" resolved for the
    library, deeplink scheme still open); `docs/technical-discovery.md` (stack);
    `docs/risks-and-open-questions.md` (song detail risks). Not the bitácora (orchestrator).
- Deviations from the spec body: the dependency check ran right after adding the catalog entry and
  the `:app` line (before any other code, as the orchestrator asked), and again after wiring the
  feature; the shared back copy is reached through a public `backUiModel(onBack)` mapper, because
  `NavCopy` is internal to `:core:ui` (the `listError` pattern); `SongDetailDefaults` also exposes
  `keyColor(colors)` and `otherStyles`; the spec's "coroutines 1.10.2" is the test pin — the
  runtime classpath resolves 1.9.0 before and after; `AppRoutes.parseSongDetail` delegates the date
  parse to a private helper (detekt `ReturnCount`).
- Verification run:
  - Dependency check (reports in the feature's evidence): only `androidx.navigation` 2.9.8 and
    `lifecycle-viewmodel-compose` 2.9.3 added on both runtime classpaths; stdlib 2.2.10, Compose
    1.9.1, material3 1.3.2, activity-compose 1.11.0, serialization-core 1.9.0, lifecycle 2.9.3
    unchanged; nothing removed or moved.
  - `./gradlew ktlintFormat` exit 0. Gate runs: exit 1 (detekt `ReturnCount`, fixed), exit 1
    (Konsist 15/17 with "rootDir must be verified to be directory beforehand", passed alone and on
    the next run; recorded as a risk), then exit 0 with konsist/detekt/ktlint `wired`, Konsist
    17/17, 56 files, 321 tests, 0 failures.
  - Demonstrations 1–8 each failed as expected and were restored by SHA-1 (messages in the
    feature's evidence); K2's first demonstration also caught a real `96.sp` in a test.
  - Greps: no navigation outside `:app`; no serialization, `viewModel(` or `deepLinks` in `:app`;
    no D-20 words, dp/sp literals, `MaterialTheme.`, link intents or tú forms in the new feature.
  - Lint: `:app` 15 warnings (14 + the navigation 2.10.2 notice for the deliberate pin).
  - Pixel 5: install, start on the tabs destination, Info and back, rotation on Info, system back
    closes the app; rotation restored to `1`/`0`; empty crash buffer.
- Device steps with the temporary jam (5 October 2026; the user loaded it, no agent wrote the
  Sheet). Log: `jams cache: upcoming 2026-10-31, past 1, songs 26`. Settings before: rotation
  `1`/`0`, font_scale 1.0. Filter Guitarra on, list scrolled ~305 px, row 02 expanded, tap `Ver
  detalle del tema` → detail full screen, no tab bar; back target 132×132 px (48dp) described
  `Volver`; one node "Tonalidad A", 356 px tall against a 92 px title; groups Guitarra, Bajo,
  Batería, Voz, Teclados, then Otros. Volver → the list's 38 labelled nodes identical, bounds
  included, to the dump before opening (filter, expansion, scroll). System back → identical. Fast
  double tap on Volver, four tries → the identical list every time, never blank (the second tap's
  timing cannot be proven from adb; it never cleared the filter chip under it). Rotation on the
  detail → same song; back in landscape and in portrait → the list restored. Font scale 2.0 on row
  05 → "Bm" on one line, 368×397 px, still the tallest. Restored font_scale 1.0 and rotation `1`/`0`;
  crash buffer empty, no FATAL. Nothing failed. Step D (the user deletes the jam; relaunch logs
  `upcoming none`) is pending on purpose until the validator has looked at it.
- Known risk or unresolved issue: no staleness notice on the detail; NavHost behaviour only
  device-checked; previews compile but are not rendered; the one-off Konsist scan error above.
- Next: the validator for `song-detail-screen`, then step D (the user deletes the temporary
  jam). Note for `bottom-navigation` (orchestrator to record): the library is
  chosen; that slice replaces the `TABS` destination with per-tab routes.

### Session 064 — 5 October 2026

- Goal: implement `debug-demo-upcoming-jam` (spec `docs/specs/debug-demo-upcoming-jam.md` at
  commit `6a46ed8`; requested by the user on 5 October 2026, no approval pending).
- Status: **`passing`** — implemented and self-verified; not accepted. Awaiting the validator.
- Completed:
  - `app/build.gradle.kts`: reads `local.properties` once; `bluesjam.demoUpcomingJam` (absent →
    false) becomes `BuildConfig.DEMO_UPCOMING_JAM` in `debug`, hard `false` in `release`.
  - `app/src/debug/java/com/bbbjam/debug/`: `DemoUpcomingJam` (fixture: today in Buenos Aires + 10
    days, 21:00, venue `Demo (solo debug)`, published, 8 catalog-seed songs: every instrument open
    and filled somewhere, `got-my-mojo-working` with no open slot, `the-thrill-is-gone` all open,
    `-` columns in four songs, `Otros` `saxo` and a 60-character instrument, keys `Bb` and `F#m`);
    `DemoUpcomingJamRepository(real, calendar)` (fills only a null `upcoming`; `past`, `freshness`
    untouched; `refresh()` delegates); `DebugOverrides.kt` (`Koin.debugOverrides()`: with the flag
    on, resolves the real `JamsRepository` and `JamCalendar` and returns a module overriding
    `JamsRepository` with the decorator; `Jam.isDemo()` by venue).
  - `app/src/release/java/com/bbbjam/debug/DebugOverrides.kt`: the same two signatures, no module
    and `false`.
  - `BluesJamApp`: `koin.loadModules(koin.debugOverrides(), allowOverride = true)` right after
    `startKoin`, before anything resolves `JamsRepository` for use; the `jams cache:` line prints
    `upcoming <date> (demo)` for the demo.
  - `app/src/testDebug/java/com/bbbjam/debug/DemoUpcomingJamRepositoryTest` (6 tests).
  - Docs: architecture `SKILL.md` ("Debug demo jam"), `docs/technical-discovery.md` (Testing and
    Verification). `AGENTS.md` unchanged (no command changed).
- Deviations from the spec body: the variant function is `Koin.debugOverrides(): List<Module>`
  (receiver instead of no argument) because the override must wrap the real repository, whose
  implementation is internal to `:core:data`; the receiver lets the debug variant resolve it
  before overriding, and the release no-op has no unused parameter for detekt. The `(demo)` marker
  comes from a second variant function, `Jam.isDemo()`.
- Verification run:
  - `./gradlew ktlintFormat` exit 0 (it re-wrapped the fixture's argument lists).
  - `CI=true ./init.sh` exit 0; `konsist: wired`, `detekt: wired`, `ktlint: wired`; Konsist 17/17;
    57 result files, 327 tests, 0 failures. `:app` lint 0 errors, 15 warnings, all version notices
    in `libs.versions.toml` (Room 2.8.5 is a new notice since session 063; nothing from this code).
  - `./gradlew :app:assembleRelease :app:assembleDebug` exit 0. A Python `zipfile` scan of every
    `.dex` in each APK: release (4 dex) has no `Lcom/bbbjam/debug/DemoUpcomingJam;`, no
    `Lcom/bbbjam/debug/DemoUpcomingJamRepository;` and no `Demo (solo debug)`, only
    `Lcom/bbbjam/debug/DebugOverridesKt;` (the no-op); debug (23 dex) has all four.
    `app/build/intermediates/built_in_kotlinc/release/compileReleaseKotlin/classes/com/bbbjam/debug/`
    holds only `DebugOverridesKt.class` (debug: 7 classes).
  - Failure demonstrations (`DemoUpcomingJamRepository.kt` SHA-1
    `a8597e691cc1a1f7262b3e3ce73276cacfacc60c` before and after each): (1) always replacing
    `upcoming` → 6 run, 2 failed: `realUpcomingJamWinsAndPassesThroughUnchanged`,
    `everyEmissionIsDecoratedOnItsOwn`; (2) also `past = emptyList()` → 6 run, 1 failed:
    `pastAndFreshnessAreTheRealOnes`. Restored; the class reran 6/6.
- Device (Pixel 5, serial 09281FDD4004U6, 5 October 2026). Airplane mode before: `0`.
  - Flag on, online: `jams cache: upcoming 2026-10-31, past 1, songs 26 (26 from catalog)` (no
    `(demo)`), screen `Sábado 31 de octubre · 21:00` / `La Macanuda`, no `Demo (solo debug)` node:
    the real jam wins.
  - Flag on, airplane mode on, `pm clear com.bbbjam`: `jams refresh: failed Offline`,
    `jams cache: upcoming 2026-10-15 (demo), past 0, songs 8 (0 from catalog)`; screen `Jueves 15 de
    octubre · 21:00` / `Demo (solo debug)` / `En 10 días`, filter `Todos 8`, rows `Bb`, `E`, `F#m`
    as designed (screenshot checked). Row 06 Crossroads expanded → `Ver detalle del tema` → the
    detail: `Crossroads`, `Eric Clapton`, `Tonalidad A`, groups Guitarra, Bajo, Batería, Voz,
    Teclados, Otros; the long instrument ellipsized (`+ percusión de ma…  Camila`), node
    description complete.
  - Flag off (line removed), reinstall, still airplane mode, `pm clear`: `jams cache: upcoming
    none, past 0, songs 0`, the error block `No pudimos cargar la próxima jam` / `Reintentar`, no
    demo. Airplane mode restored (`0`), relaunch: `upcoming 2026-10-31`, `La Macanuda`.
  - Flag back on (`bluesjam.demoUpcomingJam=true`, left on as the spec asks), reinstalled, launch:
    `upcoming 2026-10-31` (real wins). Airplane mode `0`, crash buffer empty, `/sdcard/ui.xml`
    removed. `local.properties` was never printed; the URL line untouched.
- Known risk or unresolved issue: `Jam.isDemo()` matches by venue, so a real jam named
  `Demo (solo debug)` would be logged `(demo)` (debug builds only, log only). The demo shows on
  the device only while the Sheet has no upcoming jam; while the user's `2026-10-31` test jam is
  there, use airplane mode + clear data.
- Next: the validator for `debug-demo-upcoming-jam`, then `past-jams-list`.

### Session 065 — 5 October 2026

- Goal: implement `past-jams-list` (spec `docs/specs/past-jams-list.md`; user approvals C1 and P1
  answered 4 October 2026). Sequencing met: `song-detail-screen` and `debug-demo-upcoming-jam`
  accepted; the tabs are still `TemporaryTabs` inside the `tabs` destination, so Anteriores is a
  third tab value there (no route, no navigation dependency in the feature).
- Status: **`passing`** — implemented and self-verified; not accepted. Awaiting the validator.
- Baseline: `CI=true ./init.sh` exit 0, three `wired`, before any change.
- Completed:
  - `:core:ui` `com.bbbjam.core.ui.text.SpanishDateNames` (`day`, `month`, the tables moved verbatim
    from `NextJamCopy`) + `SpanishDateNamesTest` (7 days, 12 months). `JamDateText.jamDateLabel`
    uses it; `NextJamCopy` lost both tables. `JamDateTextTest` unchanged and green (7/7).
  - `ContrastTest`: `archive on a surface` = 5.39.
  - New module `:feature:past-jams` (`com.bbbjam.feature.pastjams`; deps `:core:ui`, `:core:data`
    only; own `.gitignore`; in `settings.gradle.kts`): `PastJamsCopy` (C1 and P1 strings,
    `pastJamDateLabel`), `PastJamsUiModel` (`Loading`/`Failed`/`Empty`/`Jams`, all with `title`;
    `PastJamRowUiModel`; `PastJamSummary.Songs`/`NotShown`), `PastJamsPresenter`
    (`Presenter<PastJamsUiModel, Unit>`, retry-counter pattern) and the pure
    `JamsSnapshot.toUiModel(now, onRetry)` (spec state table; `past.sortedByDescending { it.date }`;
    upcoming never read), `PastJamsDefaults.rowStyle` (surface / textMuted / archive),
    `PastJamsScreen` + `PastJamsContent` (LazyColumn: title heading, then the state; rows a merged,
    non-clickable `Surface`), `PastJamsStatesPreview` (5 previews), `di/pastJamsModule` (factory).
    Tests: `PastJamsStatesTest`, `PastJamsPresenterTest`, `PastJamsDefaultsTest`,
    `PastJamsModuleTest`, own `FakeJamsRepository` copy.
  - `:app`: `implementation(project(":feature:past-jams"))`, `pastJamsModule` in `startKoin`,
    `TemporaryTabs` gains `Tab.PAST_JAMS` ("Anteriores") between Próxima jam and Info, with the
    status-bar insets as `contentPadding`; KDoc now "three-tab switch".
  - Docs: architecture `SKILL.md` (module list, `SpanishDateNames`, third temporary tab, the amber
    rule reads tests), `DESIGN.md` (screen 4 as built), spec "Implementation Findings".
- Deviations (recorded in the spec): `PastJamsDefaultsTest` does not compare with `colors.key`
  because Konsist `amber-roles-allowlisted` scans test sources and this module has no allowlist
  entry; "never amber" is proven by "only surface / textMuted / archive" plus `BluesJamColorsTest`.
  `RowStyle` has a sixth colour, `message` (`textMuted`), for the `NotShown` line.
- Verification run:
  - `./gradlew ktlintFormat` exit 0 (twice).
  - First `CI=true ./init.sh` failed on detekt `MaxLineLength` in the `TemporaryTabs` KDoc I had
    edited; re-wrapped. Final `CI=true ./init.sh` exit 0; `konsist: wired`, `detekt: wired`,
    `ktlint: wired`; Konsist 17/17; 62 result files, 350 tests, 0 failures. Lint
    `:feature:past-jams` and `:core:ui` "No issues found"; `:app` 0 errors, 15 warnings (the same
    version notices as session 064).
  - Failure demonstrations, each restored and checked by SHA-1 (`PastJamsPresenter.kt`
    `01fa7892c20ee5ab6b78acc368824d3309b912b3`, `PastJamsScreen.kt`
    `567323120eb679e2d54a560e5d3f1d956cc5edaf`, `PastJamsDefaults.kt`
    `22f8ac62aa75ab81ca2ef9ef882f18d69d1f5a13`):
    (1) `past.map` without the sort → 20 run, 2 failed: `rows are newest first whatever the
    repository order` ("expected:<[2026-07-25, 2026-06-27, 2026-05-30]> but
    was:<[2026-05-30, 2026-07-25, 2026-06-27]>") and the presenter's out-of-order test;
    (2) `BluesJamTheme.colors.key` for the title in `PastJamsScreen.kt` → Konsist "Assert
    'amber-roles-allowlisted' was violated (1 time). Invalid files: PastJamsScreen.kt";
    (3) `hook = colors.key` in `PastJamsDefaults` → 2 failed, "Color(1.0, 0.7019608, 0.0, …) is
    not an archive role" and `the roles are the specified ones`;
    (4) the `fetchedAt != null -> Empty` branch removed → 5 failed (every empty-state assertion got
    `Loading`); (5) `Arrangement.spacedBy(4.dp)` in `PastJamsScreen.kt` → Konsist "Assert
    'no-dp-literal-outside-core-ui' was violated (1 time)".
  - Greps on `feature/past-jams/src/main`: no `n.dp`/`n.sp`, `Color(`, `MaterialTheme.`,
    spinner, navigation import, other-feature import, `LocalDate.now` or tú form; `clickable` and
    `published` appear only in KDoc. Build file: `:core:ui` and `:core:data` only.
- Device (Pixel 5, serial 09281FDD4004U6). Before: airplane mode `0`;
  `enabled_accessibility_services` `null`, `accessibility_enabled` `0`.
  - `:app:installDebug`, cold start 839 ms; `jams cache: upcoming 2026-10-31, past 1, songs 26 (26
    from catalog)`. Tab bar `Próxima jam`, `Anteriores`, `Info`. Anteriores: `Jams anteriores`, one
    row `Sábado 25 de julio de 2026` / `La Macanuda` / `13 temas` / `Sweet Little Angel, Walking Thru
    the Park, Dust My Broom y 10 más` (screenshot: muted colours, no amber). `uiautomator dump`: one
    container View per row ([44,316][1036,631]) holding the four texts, every node
    `clickable="false"`.
  - Merged-node focus with TalkBack: **not verified**. I enabled TalkBack temporarily through
    `settings`; it opened its tutorial over the app, so no focus check was possible. Restored at
    once (`enabled_accessibility_services` deleted → `null`, `accessibility_enabled` `0`,
    `dumpsys accessibility` "Enabled services:{}"), tutorial dismissed with Back. The merge is in
    code (`semantics(mergeDescendants = true)`), not proven on the device.
  - Airplane mode on, force-stop, relaunch, Anteriores (> 60 s after the online fetch): `jams
    refresh: failed Offline`; screen shows `Sin conexión` / `Mostrando lo guardado hace 1 minuto.` /
    `Reintentar` above the same row (screenshot). Airplane mode restored to `0`.
  - Crash buffer empty, no `FATAL EXCEPTION`; `/sdcard/ui.xml` removed. Draft, unreadable, empty and
    many-jam cases are covered on the JVM only (the live Sheet has one past jam; the demo was not
    extended). `local.properties` never printed.
- Known risk or unresolved issue: TalkBack reading of a row as one node is unverified on device
  (above). Scroll position is lost on tab switches until `bottom-navigation` (spec risk). Two
  presenters now collect `observeJams()`.
- Next: the validator for `past-jams-list`.

### Session 066 — 5 October 2026

- Goal: implement `bottom-navigation` (spec `docs/specs/bottom-navigation.md`, commit 8daf7fe;
  user approvals I1, M1, D1 answered 5 October 2026). No other slice in flight.
- Status: **`passing`** — implemented and self-verified; not accepted. Awaiting the validator.
- Baseline: `CI=true ./init.sh` exit 0, three `wired`, before any change; `:app:dependencies
  --configuration debugRuntimeClasspath` saved for comparison.
- Completed:
  - `:core:ui` `com.bbbjam.core.ui.nav`: `TabBar(model)` (Material 3 `NavigationBar`, every color
    from `TabBarDefaults`, label `caption`, `alwaysShowLabel`, icon without description, preview),
    `TabBarUiModel`/`TabUiModel` (`Event.Select`), `enum TabIcon` (SETLIST, ARCHIVE, INFO), internal
    `TabBarDefaults` (`colors()`: surface / text / surfaceRaised / textMuted; `icon()`:
    `AutoMirrored.Filled.List`, `Filled.DateRange`, `Outlined.Info`). Tests: `TabBarDefaultsTest`
    (3), `ContrastTest` + `text on a raised surface` = 12.75.
  - `:app` `navigation/`: `AppRoutes` `NEXT_JAM`/`PAST_JAMS`/`INFO`, `TABS` KDoc (the shell) and the
    D1 deeplink deferral; `AppTab` (bar order, labels moved from `TemporaryTabs`, `fromRoute`,
    pure `tabBarModel`); `AppMotion` (`TAB_FADE_MS = 150`, `DETAIL_SLIDE_MS = 250`); `TabsShell`
    (inner host + bar, `statusBarsPadding()` outside the scroll, select with
    `popUpTo(start) { saveState }`/`launchSingleTop`/`restoreState`, reselect no-op);
    `AppNavHost` rewritten (outer host: shell kept drawn under the detail with
    `ExitTransition.KeepUntilTransitionsFinished`; detail slides in from the end / out to the end,
    wrapped in a `background` `Box` with `statusBarsPadding()`, navigation-bar inset as
    `contentPadding`; `RESUMED` guard and null-args pop kept). `TemporaryTabs.kt` deleted;
    `MainActivity` comment only. Tests: `AppTabTest` (4), `AppRoutesTest` + tab routes.
  - Docs: `DESIGN.md` (new "Bottom bar" subsection, song detail motion, filter chip state),
    architecture `SKILL.md` (`:app` row, `nav` package, Still Open), `docs/risks-and-open-questions.md`
    (bottom-navigation section with the deeplink open question; song-detail risk updated).
- Verification run:
  - `./gradlew ktlintFormat` exit 0 (before each gate). Final `CI=true ./init.sh` exit 0;
    `konsist: wired`, `detekt: wired`, `ktlint: wired`; Konsist 17/17; 64 result files, 359 tests,
    0 failures. Lint `:core:ui` "No issues found"; `:app` 0 errors, 15 warnings (as session 065).
  - Static: `git grep -n "TemporaryTabs" -- '*.kt'` empty; `androidx.navigation` imported only in
    `app/` (`AppNavHost.kt`, `TabsShell.kt`; the Konsist test names it); `navigationCompose =
    "2.9.8"`; the `debugRuntimeClasspath` dependency report is identical before and after (`diff`
    empty). No Gradle, manifest, `:feature:*` or `:core:data` file changed.
  - Failure demonstrations, each restored and checked by SHA-1 (`TabBarDefaults.kt`
    `4311281f40b0b51dbb5fae3ca72553c9f906bc6d`, `TabsShell.kt`
    `f04e9eff0d8f6893ebd231772cf4b622a5f9c87a`, `AppTab.kt`
    `377b2f4d11638b5b9bb24b36358ded2bf4d4ee6a`): (1) `selectedContent = colors.activeFilter` → 3
    failed ("Color(1.0, 0.7019608, 0.0, …) is an amber role", the token equality, and contrast
    9.16 vs 12.75); (2) `.padding(top = 8.dp)` in `TabsShell` → Konsist "Assert
    'no-dp-literal-outside-core-ui' was violated (1 time)"; (3) label `Historial` → `AppTabTest`
    "expected:<[Próxima jam, Anteriores, Info]> but was:<[Próxima jam, Historial, Info]>".
- Device (Pixel 5, serial 09281FDD4004U6; manual, not part of the gate). Before: airplane `0`,
  `accelerometer_rotation` `1`, `user_rotation` `0`, `enabled_accessibility_services` `null`,
  `accessibility_enabled` `0`, window/transition scale `1.0`, `animator_duration_scale` `null`.
  The real `2026-10-31` jam won online (`jams cache: upcoming 2026-10-31, past 1, songs 26`), so the
  demo jam was not needed. `:app:installDebug`, cold start 954 ms.
  - A. `uiautomator dump`: three items `Próxima jam`, `Anteriores`, `Info`, `selected="true"` only on
    Próxima jam's item, each item [*,1988][*,2208] = 220 px = 80dp tall. Screenshot: bar on
    `surface`, selected `text` on a raised pill, others muted, no amber in the bar.
  - B. Próxima jam: filter `Bajo` ("2 de 13 temas con cupo libre para bajo"), row 07 Café Madrid
    expanded, scrolled; Info scrolled; Anteriores visited (one past jam, nothing to scroll, so its
    offset restoration was not observable); back to Próxima jam and Info: dumps identical to the
    ones taken before leaving (`diff` empty, raw XML identical in the first run).
  - C. Tapping the selected Info twice: dump unchanged; one system back then went to Próxima jam
    (so no entry was added) with its state; back from Anteriores also → Próxima jam with state;
    back from Próxima jam left the app (another app resumed).
  - D. `Ver detalle del tema`: detail with no bar; `Volver` tapped twice in one `adb shell` call and
    system back both return to an identical Próxima jam dump. With `animator_duration_scale` 10
    (temporarily; deleted afterwards → `null`): mid-pop screenshot shows the detail sliding out to
    the end **above** the tabs; mid-switch screenshot shows the tab crossfade.
  - E. Rotation (auto-rotate off, `user_rotation` 1 then 0): Info stays selected; after rotating
    back, Info and Próxima jam dumps identical. Process death: my first `am kill` right after HOME
    did not kill (same pid; that run is not counted); after 5 s in the background `am kill` left no
    pid, reopening from recents gave a new pid (19973): Info selected at the same offset, Próxima
    jam with filter, expansion and offset identical. Repeated with the detail open (new pid 20152):
    the detail restored, back to identical Próxima jam, Info identical. The spec's fallback was not
    needed.
  - F. Status bar: screenshots of Próxima jam and Info scrolled, and the detail, show a solid
    `background` band under the clock, content cut below it (the detail's content fits the screen,
    so it did not scroll).
  - G. `logcat -b crash` empty, no `FATAL EXCEPTION`; every setting read back equal to its original
    value (rotation restored to `1`/`0`, animator scale `null`); `/sdcard/ui.xml` removed.
    `local.properties` untouched and never printed; TalkBack and accessibility services never
    touched.
- Known risk or unresolved issue: Anteriores' scroll restoration unobserved on the device (one past
  jam); nested NavHosts have no JVM test (T1); deeplinks deferred (D1) and recorded as an open
  question. `past-jam-detail` must re-read `AppNavHost.kt`/`AppRoutes.kt` and put its route in the
  outer host.
- **Commit hygiene finding:** commit `062a4f4` ("Record the user's decisions for
  unpublished-setlist-state and past-jam-detail", made while this slice was in progress) swept in
  two of this slice's work-in-progress hunks: `feature_list.json` `bottom-navigation` →
  `in_progress`, and the `ContrastTest` test `text on a raised surface` with a placeholder
  `expected = 0.0` that references `TabBarDefaults`, which is not in that commit. So `062a4f4` and
  `3665c55` do not compile `:core:ui` tests on their own. The working tree has the correct value
  (12.75) and the class; committing this slice repairs `HEAD`.
- Next: the validator for `bottom-navigation`.

### Session 067 — 5 October 2026

- Goal: implement `unpublished-setlist-state` (spec `docs/specs/unpublished-setlist-state.md`; user
  approvals C1 copy as written and V1 surface card with muted badge, no amber, answered 5 October
  2026). `bottom-navigation` accepted (e9eaab1); this slice touches no `:app` navigation file. No
  other slice in flight.
- Status: **`passing`** — implemented and self-verified; not accepted. Awaiting the validator.
- Completed:
  - `:core:model` `Jam.setlistForMusicians()`: `Withheld` for every DRAFT jam, `setlist` itself
    otherwise. `JamTest` + 4 cases.
  - `:feature:next-jam`: `SetlistUiModel.NotShown` replaced by `Withheld(DraftSetlistUiModel)` and
    `Unavailable(message)`; `NextJamCopy.SETLIST_WITHHELD` replaced by `DRAFT_LABEL`, `DRAFT_TITLE`,
    `DRAFT_MESSAGE` (C1); the presenter maps `jam.setlistForMusicians()`; new
    `DraftSetlistDefaults` (surface / surfaceRaised / textMuted / text / textMuted) and
    `DraftSetlistBlock` (surface card, `shapes.md`, `spacing.md` padding, badge
    `label.uppercase()` in `caption` on `surfaceRaised` with `shapes.sm`, title `songTitle` as
    `heading()`, message `body`); new `NextJamDraftPreview.kt` (draft fresh and offline; the
    shared preview fixtures in `NextJamStatesPreview.kt` became `internal`). Tests: new
    `NextJamDraftTest` (scenarios 1, 2 incl. `toString()` leak check of title, artist, key,
    musician and extra names, filter-before-draft, 5) and `DraftSetlistDefaultsTest`;
    `NextJamStatesTest` (draft withheld/available + scenario 6, scenario 3 distinct-from-empty,
    unavailable); the old withheld test removed from `NextJamPresenterTest`.
  - `:feature:song-detail`: the lookup reads `jam?.setlistForMusicians()`; `SongDetailMappingTest`
    + scenario 4 (a DRAFT jam with available songs is `NotFound`).
  - `:app` debug: `bluesjam.demoUpcomingJamDraft` → `BuildConfig.DEMO_UPCOMING_JAM_DRAFT` (debug: the
    flag; release: `false`); `DemoUpcomingJam.on(today, draft)`; `DemoUpcomingJamRepository(real,
    calendar, draft)`; `Koin.debugOverrides()` passes the flag. Main: `toCacheLine` appends
    ` draft` after a DRAFT upcoming date. `DemoUpcomingJamRepositoryTest` + 2.
  - Docs: `DESIGN.md` (as-built draft card under Required States, previews line), architecture
    `SKILL.md` (musician screens read `setlistForMusicians()`; draft demo flag),
    `docs/domain-model.md` (DRAFT), `docs/technical-discovery.md` (draft demo flag),
    `docs/user-and-access-model.md` (the client-side layers).
- Verification run:
  - `./gradlew ktlintFormat` before each gate. First gate: detekt `TooManyFunctions` on
    `NextJamStatesPreview.kt` (13 > 11) and `LargeClass` on `NextJamPresenterTest`; fixed by
    splitting (new `NextJamDraftPreview.kt`, new `NextJamDraftTest`), no suppression or baseline.
    Final `CI=true ./init.sh` exit 0; `konsist: wired`, `detekt: wired`, `ktlint: wired`; Konsist
    17/17; 66 result files, 374 tests, 0 failures (counts in Current Verified State).
  - Static: no `NotShown`/`SETLIST_WITHHELD` in `feature/next-jam`, `feature/song-detail` or
    `app`; `\.setlist\b` in those mains matches only `model.setlist` (the UiModel) in
    `NextJamScreen.kt`. No change under `backend/`, `core/data/`, `feature/past-jams/`,
    `konsist-test/` or `init.sh`; `AMBER_ROLE_ALLOWLIST` unchanged.
  - Failure demonstrations, each restored and checked by SHA-1 (`NextJamPresenter.kt`
    `0d02e32669f2cd3eb82e949f0b51ac08852fcd72`, `Jam.kt` `5637490d153a8d101463e13cde72691e588d9fb2`):
    (a) presenter maps `jam.setlist` → 57 tests, 3 failed: `NextJamDraftTest` "scenario 2, a draft
    with available songs exposes none of them", "a filter selected before a draft emission still
    yields no rows and no bar", `NextJamStatesTest` "a draft jam exposes no song, withheld or
    available, plus the notice when failing"; (b) `setlistForMusicians() = setlist` → 41 tests, 1
    failed: `JamTest` "a draft with an available setlist is still withheld for musicians";
    (c) `Setlist.Withheld -> SetlistUiModel.Empty(...)` → 57 tests, 6 failed: `NextJamDraftTest`
    scenarios 1, 2, 5 and filter-before-draft, `NextJamStatesTest` "scenario 3, the draft card is
    not the empty block…" and the draft test.
  - Server side, outside the gate: `node --test backend/apps-script/test/*.test.js` 70 tests, 70
    pass, 0 fail, including "a non-PUBLICADA jam is withheld: its tab is never requested and its
    content never serialized", "fail closed: any status other than exactly PUBLICADA …", "a fecha
    naming the passphrase tab or Catalogo, a draft and an orphan tab are never requested" and "a
    passphrase query parameter changes nothing: same body, no draft, no secret". No backend file
    changed.
  - Release: `./gradlew :app:assembleRelease :app:assembleDebug` exit 0. Python zipfile scan of
    every `.dex`: `app-release-unsigned.apk` (4 dex) has no `Lcom/bbbjam/debug/DemoUpcomingJam;`,
    no `…DemoUpcomingJamRepository;`, no `Demo (solo debug)`, only `DebugOverridesKt` (the no-op);
    `app-debug.apk` (23 dex) has all four. Release `BuildConfig`: `DEMO_UPCOMING_JAM = false`,
    `DEMO_UPCOMING_JAM_DRAFT = false`.
- Device (Pixel 5, serial 09281FDD4004U6; manual, not part of the gate). Before: airplane `0`,
  `enabled_accessibility_services` `null`, `accelerometer_rotation` `1`, `font_scale` `1.0`.
  `local.properties` had `bluesjam.demoUpcomingJam=true` and no draft flag (checked by key with
  `grep`, file never printed).
  - Added `bluesjam.demoUpcomingJamDraft=true`, `:app:installDebug` (debug `BuildConfig` draft
    `true`). Online launch: `jams cache: upcoming 2026-10-31, past 1, songs 26` (the real jam wins).
    Airplane on (`cmd connectivity airplane-mode enable`, read back `1`), `pm clear com.bbbjam`,
    launch: `jams refresh: failed Offline`, `jams cache: upcoming 2026-10-15 draft (demo), past 0,
    songs 8 (0 from catalog)`.
  - Screenshot: header `Jueves 15 de octubre · 21:00`, `Demo (solo debug)`, `En 10 días`, then the
    card on `surface` with the `EN PREPARACIÓN` badge on a raised fill, the title `La lista se está
    armando` and the muted message; no song row, no filter bar, no amber anywhere on the screen
    (the bar too).
  - `uiautomator dump`: badge, title and message are three separate text nodes, all
    `clickable=false`; no node holds a demo song title (`Sweet Little Angel`, `Got My Mojo`,
    `Thrill`, `Crossroads`), a musician (`Martín`), a key (`Bb`, `F#m`) or `Filtrá`. The dump format
    has no heading attribute, so "the title is the only new heading" is **not observed on the
    device**; it rests on the code (`semantics { heading() }` on the title only). No accessibility
    service was enabled to check it.
  - **Not observed on the device:** the staleness notice above a draft. The demo fills only an empty
    upcoming and a cleared install has no `fetchedAt`, so the notice (drawn only when something was
    fetched and the refresh failed) cannot appear with the demo while the Sheet's real 2026-10-31 jam
    wins any cached read. Covered on the JVM (`NextJamStatesTest`, scenario 6) and by
    `DraftSetlistOfflinePreview`.
  - Removed the draft flag line, reinstalled (debug draft `false`), `pm clear`, launch still offline:
    `jams cache: upcoming 2026-10-15 (demo), past 0, songs 8`; dump has `Sweet Little Angel` and the
    filter bar, no `PREPARACIÓN` (the published demo is back).
  - Restored: airplane disabled, read back `0`; accessibility services `null`, rotation `1`, font
    `1.0` read back; `logcat -b crash -d` empty; relaunch online: `jams cache: upcoming 2026-10-31,
    past 1, songs 26`. `/sdcard/ui.xml` removed. App data was cleared twice (it refilled online).
    End state: `bluesjam.demoUpcomingJam=true`, draft flag absent, debug build reinstalled.
- Known risk or unresolved issue: heading semantics and the offline-draft notice not observed on
  the device (above). `past-jam-detail` should call `setlistForMusicians()` rather than its own
  guard.
- Next: the validator for `unpublished-setlist-state`.

### Session 068 — 5 October 2026

- Goal: implement `past-jam-detail` (spec `docs/specs/past-jam-detail.md`, revision 3: song list
  only — position, title, artist, key; no lineup, no expansion, no demo past jam). User approvals:
  S1 superseded by "para las jams pasadas no importa quién tocó, solo la lista de temas"; D1 not
  needed; C1 copy approved; K3 (keep the key) kept. `bottom-navigation` and
  `unpublished-setlist-state` accepted; `Jam.setlistForMusicians()` exists and is used.
- Status: **`passing`** — implemented and self-verified; not accepted. Awaiting the validator.
- Completed:
  - `:feature:past-jams` detail: `PastJamDetailPresenter(JamsRepository)` (`Params(jamDate,
    onBack)`, `rememberUpdatedState(onBack)`), pure `JamsSnapshot.toPastJamDetail(jamDate, onBack)`
    (past jams only, never `upcoming`; `jam.setlistForMusicians()`; `lineup`/`extraParticipants`
    never read), `PastJamDetailUiModel` (`Loading`, `NotFound`, `Jam(header, setlist, back)`),
    `PastJamHeaderUiModel`, `PastSetlistUiModel` (`Songs(rows, droppedRowsNote)`, `NotShown`),
    `PastSongRowUiModel(position, positionLabel, title, artist, key, keyDescription)`,
    `PastJamDetailDefaults` (archive colours, key in `key`), `PastJamDetailScreen`/`Content`,
    `PastJamDetailPreview.kt` (Loading, NotFound, Jam with blank artist + dropped note, Withheld).
    `PastJamsCopy` + `OPEN_JAM`, `DETAIL_LOADING`, `NOT_FOUND_TITLE`/`MESSAGE`, `keyDescription`,
    `droppedRows` (C1). `pastJamsModule` + `factory { PastJamDetailPresenter(get()) }`.
  - Anteriores entry point: `PastJamsPresenter : Presenter<PastJamsUiModel, Params(onOpenJam)>`
    (`rememberUpdatedState`); `PastJamRowUiModel` + `openLabel` (`ver la lista de temas`, null for
    `NotShown`) and `events` (`Event.Open`); the row summary now reads `setlistForMusicians()`.
    `PastJamsScreen(onOpenJam, …)`: a row with `openLabel` is one clickable node (`Role.Button`,
    click label, `heightIn(min = LocalMinimumInteractiveComponentSize.current)`), inside the
    `Surface` so the ripple is clipped; a `NotShown` row stays one merged non-clickable node.
  - `:app`: `AppRoutes.PAST_JAM_DETAIL = "pastJam/{jamDate}"`, `pastJamDetail(date)`,
    `parsePastJamDetail(String?)`; outer-host destination in a private
    `NavGraphBuilder.pastJamDetail(nav)` (extracted because detekt `LongMethod` flagged
    `AppNavHost` at 77 > 60 lines) with the song route's slide, `Box`/`statusBarsPadding`,
    `navigationBars` content padding, `RESUMED` back guard, and a pop on a null argument.
    `TabsShell(onOpenSong, onOpenPastJam)` passes it to `PastJamsScreen(onOpenJam = onOpenPastJam)`.
  - Konsist: `AMBER_ROLE_ALLOWLIST` + `"feature/past-jams" to setOf("key")` (rule count unchanged).
  - Tests: new `PastJamDetailMappingTest` 9, `PastJamDetailPresenterTest` 2,
    `PastJamDetailModelShapeTest` 3, `PastJamDetailDefaultsTest` 3, plus a test-only
    `PastJamDetailFixtures.kt` (not in the spec's file list; shared fixtures, no production code);
    `PastJamsPresenterTest` 5 → 6 (Open + stale handler), `PastJamsStatesTest` 12 → 14 (only Songs
    rows open; a DRAFT with available songs is the not-published line), `PastJamsModuleTest`
    (also resolves the detail presenter), `AppRoutesTest` 6 → 9.
  - Docs: `DESIGN.md` Core Screens 4 (rows with songs open the jam) and 5 (as built, song list
    only), architecture `SKILL.md` (`:feature:past-jams` detail, allowlist `{key}`, the outer
    route, `setlistForMusicians()` readers), `docs/risks-and-open-questions.md` (no staleness
    notice on the detail; duplicated copy; nav untested on the JVM).
- Verification run:
  - Baseline `CI=true ./init.sh` exit 0 before any change. `./gradlew ktlintFormat` before each
    gate. First gate: detekt `MagicNumber` (`LocalTime.of(21, 0)` in the preview, now
    `parse("21:00")`) and `MaxLineLength` (KDoc in `PastJamsModule.kt`); second: `LongMethod` on
    `AppNavHost` (fixed by the extraction above). No suppression, baseline or config change. Final
    `CI=true ./init.sh` exit 0; `konsist: wired`, `detekt: wired`, `ktlint: wired`; Konsist 17/17;
    70 result files, 397 tests, 0 failures.
  - Failure demonstrations, each restored by copy and checked with `sha1sum -c` (all OK):
    `PastJamDetailUiModel.kt` `39273d10…`, `PastJamDetailScreen.kt` `b14299f5…`,
    `ModuleIsolationTest.kt` `d66b3def…`, `PastJamsPresenter.kt` `64f36445…`,
    `PastJamDetailPresenter.kt` `d117ced1…`.
    (1) `val lineup: LineupPanelUiModel? = null` on `PastSongRowUiModel`: shape test 3 tests, 2
    failed: "forbidden field in PastSongRowUiModel: [..., lineup]" and "expected:<[position,
    positionLabel, title, artist, key, keyDescription]> but was:<[..., lineup]>".
    (2) `colors.slotOpen` for the key in `PastJamDetailScreen.kt`: Konsist 17 tests, 1 failed:
    "Assert 'amber-roles-allowlisted' was violated (1 time) … PastJamDetailScreen.kt".
    (3) allowlist entry removed: 1 failed: "'amber-roles-allowlisted' was violated (2 times) …
    PastJamDetailDefaults.kt … PastJamDetailDefaultsTest.kt".
    (4) `rememberUpdatedState` dropped in `PastJamsPresenter`: `PastJamsPresenterTest` 6 tests, 1
    failed: "expected:<[first 2026-06-27, second 2026-07-25]> but was:<[first 2026-06-27, first
    2026-07-25]>".
    (5) lookup in `listOfNotNull(upcoming) + past`: `PastJamDetailMappingTest` 9 tests, 1 failed:
    "no past jam with that date is not found, and the upcoming jam with the same date is ignored".
  - Greps on `feature/past-jams/src/main`: 0 hits for `androidx\.navigation`, `MaterialTheme\.`,
    `Color\(`, dp/sp/em literals, `InstrumentStrip`, `LineupPanel`, `AdminSession`,
    `InstrumentFilter`, tú forms. `lineup`: 4 hits, two KDoc lines saying it is never read or shown
    and two preview `JamSong(lineup = Lineup.default())` constructor arguments (required by the
    domain type). `\.setlist\b`: only `model.setlist` (the UiModel) in `PastJamDetailScreen.kt`; no
    `jam.setlist`.
- Device (Pixel 5, serial 09281FDD4004U6; manual, not part of the gate; Sheet untouched; no
  accessibility service enabled). Before: airplane `0`, `accelerometer_rotation` `1`,
  `user_rotation` `0`, `font_scale` `1.0`, `accessibility_enabled` `0`, services `null`.
  - `:app:installDebug` exit 0; cold start 788 ms; `jams cache: upcoming 2026-10-31, past 1`.
  - Anteriores: the 2026-07-25 row (`13 temas`, hook) is one `clickable=true focusable=true` node
    [44,316][1036,631] (315 px, above 132 px = 48dp) with a Button-role node; the texts sit under
    it. The click label is not exported by `uiautomator dump` (as recorded since `info-screen`); it
    rests on the model test and the code. Anteriores has one row and does not scroll, so "same
    scroll position" could not be exercised.
  - Tap: full screen, no tab bar (no `Próxima jam`/`Info` text in the dump): `Volver` (the only
    clickable node, [22,158][154,290] = 48dp), `Sábado 25 de julio de 2026`, `La Macanuda`,
    `13 temas`, rows `01`…`13` (across two dumps after scrolling), each with title, artist and a
    key node with `content-desc` `Tonalidad …` (B, A, D, C, Bm, A, G, E, C, A, C, E, A). No
    dropped-rows note. Screenshot: muted header and titles, lighter-muted artists, the keys the only
    amber.
  - `Volver`: Anteriores title, Anteriores tab `selected=true`; system back: the same; two fast
    taps on `Volver` (one `input` shell call): Anteriores selected, MainActivity resumed, not blank.
    Rotation (accelerometer off, `user_rotation` 1 then 0) keeps the detail open with header and
    count; back: Anteriores selected. Two early dumps taken 1.5 s after a tap came back empty (taken
    mid-transition); repeated with 3 s waits, as recorded above.
  - Restored: `accelerometer_rotation` `1`, `user_rotation` `0`; read back airplane `0`, font `1.0`,
    accessibility `0`/`null`. `logcat -b crash -d` empty; no `FATAL` in `AndroidRuntime:E`.
- Known risk or unresolved issue: no staleness notice on the detail (spec non-goal); click label and
  heading semantics not observable with `uiautomator dump`; Anteriores scroll restoration not
  exercised (one past jam).
- Next: the validator for `past-jam-detail`.

### Session 069 — 5 October 2026

- Goal: implement `admin-passphrase-login` (spec `docs/specs/admin-passphrase-login.md`, commit
  82322a5). User approvals A1–A7 answered with the recommended option. `past-jam-detail` accepted
  (navigation files re-read).
- Status: **`passing`** — implemented and self-verified (JVM, Node, live endpoint, device including
  the success path after the user typed the real passphrase once, A3); not accepted. Awaiting the
  validator.
- Completed:
  - Backend: new `backend/apps-script/src/Post.js` (`doPost`, `handlePost`, `ACTIONS =
    { checkPassphrase }`, `readPassphrase_`, `passphraseMatches_`; fail closed: the stored value is
    read and checked non-blank before comparing). No other script file changed. **Deviation:**
    `Post.js` has its own `postErrorBody_`/`postErrorMessage_` (same bodies as `Code.js`'s), because
    `Code.js` does not export its helpers to the Node loader and the spec forbids touching it.
    `test/helpers/load.js` + `Post.js`; new `test/post.test.js` (13 tests). **Spec gap:**
    `router.test.js`'s "no file in src/ names the passphrase tab, and none defines doPost" could not
    stay unchanged with an approved `doPost` file; it now excludes `Post.js` only (every other
    GET-side assertion unchanged).
  - `:core:data`: catalog `datastore = "1.2.1"` / `androidx-datastore-preferences`, `implementation`
    in `core/data` only. `admin/`: public `AdminSession`, `LoginOutcome`; internal
    `DefaultAdminSession`, `AdminCredentialStore` (file `admin_session`, key `admin_passphrase`).
    `remote/`: `AppsScriptPostTransport` (same `OkHttpAppsScriptTransport` instance; POST to the base
    URL, no query, `application/json; charset=utf-8`), `AppsScriptEnvelope.decodeOk`,
    `CheckPassphraseRequest` (kotlinx-serialization). `dataModule` binds the transport once for both
    interfaces, the store and `AdminSession`. **Deviation:** the store is built over `OkioStorage`
    with `PreferencesSerializer`, not `PreferenceDataStoreFactory.create(produceFile)`: the `File`
    storage replaces with `File.renameTo` below API 26 (the JVM tests), which on Windows fails when
    the file exists ("Unable to rename …", a save then a clear). Same file name and format;
    `createWithPath` was tried first and delegates to the `File` storage on Android/JVM.
  - `:feature:info` (+ `implementation(project(":core:data"))`): `AdminLoginPresenter`,
    `AdminLoginUiModel` (`toString` hides the passphrase, also in `PassphraseChanged`),
    `AdminLoginScreen` (outlined field with explicit token colours, Mostrar/Ocultar, error as polite
    supporting text, `Entrar` as `button-primary`, `imePadding`), `AdminLoginCopy`,
    `AdminLoginDefaults`. `InfoPresenter(linkOpener, adminSession)` + `Params(onOpenAdminLogin)`,
    sealed `AdminEntryUiModel` (`LoggedOut`, `LoggedIn`), `LogOut` event; `ADMIN_NOT_ENABLED`
    removed. Two-field updates in the login presenter use `Snapshot.withMutableSnapshot` (a test
    caught an intermediate model with the new text and the old error).
  - `:app`: `AppRoutes.ADMIN_LOGIN = "admin-login"`; outer-host `adminLogin(nav)` (slide, insets;
    back under the `RESUMED` guard). **Deviation:** a successful login pops with
    `popBackStack(ADMIN_LOGIN, inclusive = true)` rather than under the `RESUMED` guard, because it
    lands after a network round trip, possibly with the app in the background, where the guard
    would leave the login open over an already-stored session; popping a route that is gone is a
    no-op. `TabsShell(…, onOpenAdminLogin)`. `backup_rules.xml` and `data_extraction_rules.xml`
    (cloud-backup and device-transfer) exclude `datastore/admin_session.preferences_pb`.
  - Konsist (A6): `androidx.datastore.` in `DATA_LIBRARY_PREFIXES` and `QUALIFIED_DATA_LIBRARY`;
    `"feature/info" to setOf("primaryAction", "onPrimaryAction")`. Still 17 rules.
  - Docs: `docs/apps-script-api.md` (POST transport, `checkPassphrase`, the four codes, the client
    mapping), `backend/apps-script/README.md` (layout row, "Redeploy for the passphrase check",
    verify-paste row `Post.gs` → `checkPassphrase: checkPassphrase_,`, the `Known: checkPassphrase`
    deploy check, POST curl checks, and a **Python URL decode** replacing the `sed`/`tr` one-liner
    that fails in this Git Bash, per the orchestrator), `docs/user-and-access-model.md`,
    `docs/risks-and-open-questions.md` (no rate limit, passphrase in a UiModel, plaintext, the new
    deployment URL), `DESIGN.md` screens 6 and 7 as built, architecture `SKILL.md`.
- Verification run:
  - Dependency check first (A2): resolved-version lists of `:app` debug and release runtime
    classpaths before/after: only twelve `androidx.datastore` artifacts added. `dependencyInsight`
    (both): stdlib 2.2.10, coroutines-core 1.9.0, okio 3.15.0, compose runtime 1.9.1,
    lifecycle-runtime 2.9.3, all unchanged.
  - `./gradlew ktlintFormat` before each gate. Gate 1: detekt `MaxLineLength` (AppNavHost KDoc);
    gate 2: `ReturnCount` (`logIn`, split into `check`/`save`). Final `CI=true ./init.sh` exit 0,
    three tools `wired`, Konsist 17/17, 74 result files, 427 tests, 0 failures. Lint `:core:data` and
    `:feature:info` no issues; `:app` the same 15 version notices. Node 83/83.
  - Failure demonstrations, restored and checked with `sha1sum -c` (all OK): (1) a probe in
    `:feature:info` importing and qualifying `androidx.datastore.core.DataStore` passed Konsist
    before the list change (17/0) and failed both data-library rules after it; probe deleted.
    (2) Failing first: `AdminLoginDefaults` reading `primaryAction` without the entry →
    `amber-roles-allowlisted` violated 2 times (`AdminLoginDefaults.kt`,
    `AdminLoginDefaultsTest.kt`); entry added → 17/17. (3) `DefaultAdminSession` (`9fef4ff3…`)
    storing before the outcome: 3 of 8 failed ("expected null, but was:<…>"). (4) `Post.js`
    (`a8e13c69…`) without the blank check: Node 1 of 83 failed ("an unset passphrase rejects every
    attempt, blank included").
  - Live (orchestrator after the user's deploy, then this session; Python urllib, codes only, URL
    never printed): `{}` → `unknown_action … Known: checkPassphrase`; wrong and empty →
    `invalid_passphrase` (so `Config` holds a non-empty passphrase); `not json` →
    `invalid_request`; `?resource=config` → `… Known: catalog, jams`.
  - Greps: no `Log.` call mentions a passphrase; `rememberSaveable` only in a KDoc saying it is not
    used; no navigation, `MaterialTheme.`, `Color(` or dp/sp literal in `feature/info/src/main`;
    `Code.js`, `Normalize.js`, `Catalog.js`, `Jams.js`, `appsscript.json` unchanged.
- Device (Pixel 5, 09281FDD4004U6; no accessibility service; Sheet untouched; only the test string
  `definitely-wrong`). Before: airplane `0`, `accelerometer_rotation` `1`, `user_rotation` `0`,
  `font_scale` `1.0`, `accessibility_enabled` `0`, services `null`, `touch_exploration_enabled` `0`.
  - `:app:installDebug` (new URL) exit 0; cold start 818 ms. Info → `Entrar como admin` (132 px
    clickable) → full-screen login, no tab bar: `Volver` 132 px, title, `EditText password=true`,
    `Mostrar` 132 px, `Entrar` 132 px and disabled while blank.
  - Typed the test string: 16 dots, `Entrar` enabled. Tap: `Verificando…` (disabled), then
    `La frase de acceso no es correcta.` under the field, text kept. Screenshot: amber only on
    `Entrar`; red border and error text; no amber in the field. One more character cleared the
    error. Airplane on: `No hay conexión. Para entrar como admin necesitás internet.`; airplane off
    again. `Mostrar` showed the test text, toggle `Ocultar`. `Volver` and system back return to Info
    with `Entrar como admin`. `files/datastore` never created. Crash buffer empty; logcat 0 hits for
    the test string or `passphrase`, no `FATAL`. Settings read back as before.
  - Success path (A3): the user typed the real passphrase on the Pixel 5 and tapped `Entrar`; the
    login closed to Info (the agent never saw, typed or printed it). Agent: Info shows
    `Modo admin activo` and `Salir del modo admin`; `files/datastore/admin_session.preferences_pb`
    exists (existence only, never read). `am force-stop` (no pid) + relaunch (cold start 1028 ms):
    still `Modo admin activo`; the logout is a 132 px clickable node. `Salir del modo admin`:
    `Entrar como admin` at once; the file stays but is 0 bytes (`stat` size only; DataStore writes an
    empty preference set); after another force-stop + relaunch still `Entrar como admin`. Left
    logged out. Crash buffer empty; logcat 9578 lines (since the session's clear, covering the
    user's login), 0 matches for `passphrase` or the test string, no `FATAL` (counts only).
    Settings read back unchanged.
- Not run: the `No se pudo verificar…` copy live (the user deployed before the device check; it
  rests on `DefaultAdminSessionTest`'s HTML-page case and `AdminLoginPresenterTest`).
- Known risk or unresolved issue: the user made a **new deployment** (new URL) instead of a new
  version; other installed builds keep the old URL. The outlined field's floating label sits on a
  `background`-coloured notch over the `surface` container (Material behaviour; cosmetic).
- Next: the validator for `admin-passphrase-login`. To be admin again on the Pixel 5, the user
  types the passphrase once more (the device was left logged out).

### Session 070 — 5 October 2026

- Goal: implement `debug-admin-session` (spec `docs/specs/debug-admin-session.md`, commit ca5d8f0),
  requested by the user on 5 October 2026 so device checks of admin controls need no passphrase.
- Status: **`passing`** — implemented and self-verified; not accepted. Awaiting the validator.
- Completed:
  - `app/build.gradle.kts`: `bluesjam.debugAdmin` → `BuildConfig.DEBUG_ADMIN` (debug: the flag,
    `false` when absent; release hard `false`).
  - `app/src/debug/.../DebugAdminSession.kt`: `DebugAdminSession(real: AdminSession)`; a
    `MutableStateFlow(true)` forced state combined with the real flow (`forced || real`,
    `distinctUntilChanged`); `logOut()` clears the forced state then calls the real one; `logIn`
    delegates. No passphrase stored, sent or known.
  - `DebugOverrides.kt` (debug): `DebugFlags(demoUpcomingJam, demoUpcomingJamDraft, debugAdmin)`
    with `fromBuildConfig`; `Koin.debugOverrides()` delegates to `debugOverrides(flags)`, which adds
    one module per flag on, independently (previously nothing unless the demo flag was on).
    `debugAdminLogSuffix()` (debug: ` admin (debug)` when the flag is on; release no-op `""`).
  - `BluesJamApp`: the `jams cache` startup line ends with `debugAdminLogSuffix()`. No UI copy
    change; no `:core:data`, backend or feature change.
  - Tests: `app/src/testDebug/.../DebugAdminSessionTest.kt` (6): forced true over a logged-out real
    session; logout ends forcing, calls the real logout once, then follows the real value; `logIn`
    delegates once with the same argument; admin flag alone overrides only `AdminSession`; demo flag
    alone only `JamsRepository`; both / none (Koin `koinApplication` with fakes).
  - Docs: architecture `SKILL.md` (debug admin session, independent overrides),
    `docs/technical-discovery.md` (verification bullet), `docs/user-and-access-model.md` (debug-only
    flag draws controls, authorizes nothing).
- Verification run:
  - `./gradlew ktlintFormat` (no changes), `CI=true ./init.sh` exit 0 twice (flag off; then flag on,
    the final state): three tools `wired`, Konsist 17/17, 75 result files, 433 tests, 0 failures;
    `:app` lint 0 errors, the same 15 version notices.
  - Failure demonstrations, restored and checked with `sha1sum -c` (both OK;
    `DebugAdminSession.kt` `f9057915…`, `DebugOverrides.kt` `c348e360…`): (1) `logOut` without
    `real.logOut()` → 1 of 6 failed, `logOutEndsTheForcedStateAndCallsTheRealLogOutOnce`
    ("expected:<1> but was:<0>"); (2) `if (!flags.demoUpcomingJam) return emptyList()` gating the
    admin override again → 1 of 6 failed, `adminFlagAloneOverridesOnlyTheAdminSession`.
  - Release: `:app:assembleRelease :app:assembleDebug` exit 0; release `BuildConfig.DEBUG_ADMIN =
    false`. Python zipfile scan: `app-release-unsigned.apk` (5 dex) has no `DebugAdminSession`,
    `DebugFlags`, ` admin (debug)` or demo class, only the no-op `DebugOverridesKt`; `app-debug.apk`
    (24 dex) has them (` admin (debug)` only with the flag on, otherwise constant-folded).
- Device (Pixel 5, 09281FDD4004U6; no accessibility service; Sheet untouched; no passphrase typed).
  Before: airplane `0`, `accelerometer_rotation` `1`, `user_rotation` `0`, `font_scale` `1.0`,
  `accessibility_enabled` `0`, services `null`, `touch_exploration_enabled` `0`.
  - Added `bluesjam.debugAdmin=true` (keys checked by grep, file never printed); `installDebug`,
    `pm clear com.bbbjam`, launch (863 ms): `jams cache: upcoming 2026-10-31, past 1, songs 26 (26
    from catalog) admin (debug)`. Info, scrolled: `Modo admin activo` and `Salir del modo admin`,
    no login (screenshot, muted text as before).
  - `Salir del modo admin` → `Entrar como admin` at once; `files/datastore` does not exist (the
    debug session stored nothing; the real logout on an empty store wrote no file).
    `am force-stop` + relaunch: log ends ` admin (debug)`, Info shows `Modo admin activo` again.
  - Flag line removed, `installDebug` (debug `DEBUG_ADMIN = false`), relaunch: log line without the
    suffix; Info shows `Entrar como admin`.
  - Flag restored, `installDebug` (`DEBUG_ADMIN = true`), relaunch: ` admin (debug)`, `Modo admin
    activo`. Crash buffer empty; logcat 0 lines matching `passphrase|FATAL`. Settings read back as
    before; `/sdcard/ui.xml` removed. App data cleared once (refilled online).
  - End state: `local.properties` has `bluesjam.demoUpcomingJam=true` and
    `bluesjam.debugAdmin=true`; the flag-on debug build is installed.
- Known risk or unresolved issue: a write from a debug-admin session without a stored passphrase
  will be rejected by the server; how write slices verify real writes remains their own question
  (spec non-goal).
- Next: the validator for `debug-admin-session`; then `apps-script-write-auth`.

### Session 071 — 5 October 2026

- Feature: `apps-script-write-auth` (spec `docs/specs/apps-script-write-auth.md`, revision 7caaf69;
  user approvals W1 (a), W2 (a), W3 = a refused write keeps admin mode). Status `in_progress`:
  everything but the live checks is done.
- What changed:
  - `backend/apps-script/src/Post.js` (the only `src` file changed; `Code.js`, `Jams.js`,
    `Catalog.js`, `Normalize.js` untouched): `ACTIONS` entries are `{ write, run }`
    (`checkPassphrase` read, `checkWriteAccess` write). `handlePost(request, spreadsheet, services)`
    runs `invalid_request`, `unknown_action`, the guard `requirePassphrase_` for every action, the
    script lock (`tryLock(10000)`, `busy`, release in `finally`) for write actions, then the action.
    The guard: rate limit `auth_failures_<floor(now/600000)>` ≥ 10 → `rate_limited` without reading
    `Config`; `readPassphrase_` on every request; a mismatch counts one failure (TTL 1200 s) and is
    `invalid_passphrase`; cache exceptions ignored (fail open). `checkWriteAccess_` deletes a
    leftover `_prueba_escritura`, inserts it, writes and reads back A1, deletes it; a mismatch is
    `internal_error` after the delete. `doPost` builds `services` from `CacheService`,
    `LockService` and `Date.now()`. Final SHA-1 `c4c12993306704e1e15276ea0e703783b93eb904`.
  - `backend/apps-script/test/post.test.js`: fake cache, lock and writable spreadsheet; table-driven
    guard tests over `Object.keys(ACTIONS)` with spy `run`s; rotation; rate-limit window, edge
    millisecond and fail-open; busy and release; `checkWriteAccess` ops, leftover and mismatch; no
    passphrase in responses, cache keys or values; doPost and the vm (Apps Script scope) run with
    `CacheService`/`LockService`; the README marker (and the old one gone).
  - `:core:data` `admin/`: public `WriteOutcome`, internal `AdminWriter` (reads the stored
    passphrase; none → `AccessRefused`, no request; `JsonObject` body; never saves or clears the
    store); `remote/ServiceCodes` (shared with `DefaultAdminSession`); `dataModule`
    `single { AdminWriter(get(), get()) }`. Tests: new `AdminWriterTest` (10), `DefaultAdminSessionTest`
    (`rate_limited`, `busy` → `Unavailable`), `DataModuleTest` (resolves one `AdminWriter`).
  - Docs: `docs/apps-script-api.md` (router and guard, `checkWriteAccess`, `rate_limited`, `busy`,
    `AdminWriter` mapping), `docs/user-and-access-model.md` (guard as built, W3, revocation, debug
    session sends nothing), `docs/risks-and-open-questions.md` (W2 lockout trade-off, W3 bullet,
    scope assumption and write latency pending the live checks), `docs/sheet-schema.md`
    (`_prueba_escritura`), architecture `SKILL.md` (`AdminWriter`, `WriteOutcome`, router-guarded
    actions), `backend/apps-script/README.md` (**Redeploy for write auth**, Verify-the-paste marker
    `checkWriteAccess: { write: true, run: checkWriteAccess_ },` and the old marker as must-not,
    `Known: checkPassphrase, checkWriteAccess`, live commands with wrong values only).
- Verification run:
  - `node --test backend/apps-script/test/*.test.js`: 97/97 (83 before; `post.test.js` 13 → 27).
  - Failure demonstrations, each restored and checked with `sha1sum -c` (OK): (a) guard skipped for
    write actions → 7 of 27 fail; (b1) the stored passphrase cached across requests → 8 of 27 fail,
    rotation among them; (b2) the passphrase put in the cache → 4 of 27 fail, the leak test among
    them; (c) `AdminWriter` calling `store.clear()` on `AccessRefused` → `AdminWriterTest` 2 of 10
    fail ("a refused write keeps the stored passphrase and admin mode": expected the value, was
    null), `AdminWriter.kt` `e0d892de…` restored. (An early restore of Post.js by `git checkout`
    briefly reverted it to the committed version; it was rewritten and matched the recorded SHA-1
    before any demonstration was counted.)
  - `./gradlew ktlintFormat`, `CI=true ./init.sh` exit 0: `konsist: wired` (17/17, unchanged),
    `detekt: wired`, `ktlint: wired`; 76 result files, 443 tests, 0 failures.
  - No Gradle, Konsist, manifest, UI or `:app` change. No device check (nothing on screen changes);
    the device was not touched.
  - `local.properties`: `bluesjam.appsScriptUrl` present, `bluesjam.debugAdminPassphrase` absent
    (`grep -c`, values never printed). No build reads the passphrase key.
- Live checks (6 October 2026, after the user's deploy on the same URL). The deployed `Post.gs` is
  not `c4c12993…`: by a user-approved single-deploy exception it is the Part A version of
  `admin-add-song-to-setlist` (commit 29d3b54, `Post.js` SHA-1
  `94bb91548469d3f9c6c868788f09d3d2684c5cf1`, the working tree's file at the time of the checks),
  with the write-auth router, guard, rate limit and lock unchanged. Scratchpad Python script (URL
  decoded from `local.properties`, nothing but codes and `Known:` tails printed; one call per
  check, 3 counted failures, no lockout):
  - L1 `{}` → `unknown_action`, `Known: checkPassphrase, checkWriteAccess, readJams, addSong,
    checkSetlistWrite` (the five actions of the deployed file).
  - L2 `checkWriteAccess` with no passphrase → `invalid_passphrase`.
  - L3 `checkWriteAccess` with `definitely-wrong` → `invalid_passphrase`.
  - L4 `checkPassphrase` with `definitely-wrong` → `invalid_passphrase` (not `internal_error`:
    `doPost` obtained the script cache and lock without extra authorization; cache get/put and
    `tryLock` themselves are not proven live, see the risks doc).
  - L5 `?resource=config` → `unknown_resource`, `Known: catalog, jams`; `?resource=jams` saved to
    the git-ignored `backend/apps-script/jams.local.json`, `check-response.js --strict` exit 0
    ("2 jams, 2 published with setlist, 0 withheld, 0 with errors, 26 setlist rows").
  - L6, first pass **skipped**: `bluesjam.debugAdminPassphrase` absent (`grep -c` = 0). After the
    user added it (W1; key presence checked with `grep -c`, value never read into output), L6 ran
    once: `checkWriteAccess` with the stored passphrase → `ok`, 4.94 s end to end. The lock, tab
    create, write, read-back and delete work live; no new authorization was asked. Rotation,
    lockout and busy not run live, by design.
  - No passphrase was read or printed; the URL never appeared in output.
- Docs: risks doc scope status (checked live, L4 and L6; cache get/put not provable from outside)
  and write latency (one sample, 4.94 s).
- Status `passing` (implemented and self-verified, L1–L6 live), awaiting the validator.
- Next: the validator for `apps-script-write-auth`.

### Session 072 — 6 October 2026

- Feature: `admin-add-song-to-setlist`, **Part A only** (backend and `:core:data`, no UI), spec
  `docs/specs/admin-add-song-to-setlist.md` (a60039d). User approvals: S1 (b) one feature over two
  sessions (this one is Part A), K1 (a) the catalog default key (applied by Part B's picker; the
  repository and the server take the key they are given), J1 all three edge rules, V1 and C1 for
  Part B, L1 (a) `checkSetlistWrite` on `_prueba_lista` plus the probes and the `readJams` read.
  **User exception (6 October 2026):** built on top of the undeployed, unaccepted
  `apps-script-write-auth` Post.js (c9ca15f) so one paste and one deploy cover both. Both features
  stay `in_progress`. Write-auth's recorded SHA-1 `c4c12993…` is superseded for deployment by the
  combined file below; its behaviour is unchanged.
- What changed:
  - `backend/apps-script/src/Post.js` (the only `src` file changed; `Code.js`, `Jams.js`,
    `Catalog.js`, `Normalize.js` byte-identical, `git diff --quiet HEAD` on them exit 0). New
    `ACTIONS` entries, guarded by the unchanged router: `readJams` (read; `ROUTES.jams` plus the
    tab of every exactly-`BORRADOR` jam dated today or later in the spreadsheet zone, with the GET's
    per-jam guards; answers `{ok, jams}`), `addSong` (write; validation in the spec's order before
    any write: `invalid_date`, `invalid_song`, `invalid_key`, `unknown_jam`/`duplicate_date`,
    `jam_not_editable`, `unknown_song`, `missing_header`/`duplicate_header`,
    `song_already_in_setlist`; creates a missing tab with the documented header; appends at
    1 + max valid `posicion`, mapped columns only, `@` format before each value, key from the
    request, seven empty slots; answers `{ok, position, title, artist}`) and `checkSetlistWrite`
    (write; self-cleaning on `_prueba_lista` with marker title `7/4` and key `Bbm`, read back with
    `buildSetlist`). Helpers `todayIso_`, `isCalendarDate_`, `requireEditableJam_` (dates through
    `buildJams` with no tab reads), `catalogSong_` (never reads `tono_default`), `nextPosition_`,
    `createSetlistTab_`, `appendSetlistRow_`. Final SHA-1
    `94bb91548469d3f9c6c868788f09d3d2684c5cf1`.
  - `backend/apps-script/test/setlist.test.js` (new, 16 tests): a grid-backed fake spreadsheet that
    turns `d/m` into a date unless the cell is `@`; readJams (current/future drafts only, today's
    boundary at Buenos Aires midnight, per-jam errors, lowercase status not a draft, GET
    unchanged); addSong (append, op order, column order, `7/4`, tab creation, consecutive adds,
    30 validation cases each with no write and the lock released, editable rules, key never from
    `tono_default`); checkSetlistWrite (ok, leftover, a converting Sheet → `internal_error` with
    the tab deleted); the guard on all three.
  - `backend/apps-script/test/post.test.js`: only enumerations changed (write-auth behaviour
    untouched): the action-table test now lists five actions, the two `Known:` assertions carry
    the new list, the leak test's allow-list of opened tabs adds `_prueba_lista`, `Jams` and
    `Catalogo` (the tabs the new actions are documented to open), and the README-marker test also
    looks for `addSong: { write: true, run: addSong_ },`.
  - `:core:data`: `remote/Envelope.kt` `decodeOkObject` (same checks as `decodeOk`, which now
    delegates to it); `admin/AdminWriter.kt` `send(action, fields): AdminAnswer`
    (`Ok(body)`/`Refused(outcome, failure)`; `write` = `send` with `Ok` → `Done`; W3 unchanged);
    `jams/JamsMapper.map(rows, includeDrafts = false)` (a draft maps like a published jam when
    true, `missing_tab` → `Available(emptyList())`); `jams/DefaultJamsRepository` takes
    `AdminWriter` and `AdminCredentialStore`: with a passphrase stored the refresh is the
    `readJams` POST; `AccessRefused` remembers the refused passphrase in memory and falls back to
    the GET; any other failure is a failed refresh with the cache untouched and no GET;
    `JamsRefreshOutcome.Updated`/`Failed` gain `adminRead` and the log line ends ` (admin read)`;
    `cache/CatalogDao.song(id)`; new `cache/SetlistDao` (`insertSetlistSong`: IGNORE, only while
    the cached setlist is `AVAILABLE`, one transaction) registered in `BluesJamDatabase` with no
    schema or version change (a separate DAO because detekt's `TooManyFunctions` limit of 11 on
    `JamsDao`; the spec put it in `JamsDao`); new `setlist/` (`SetlistRepository`,
    `AddSongOutcome`, `SetlistAdd`, `DefaultSetlistRepository(AdminWriter, SetlistDao, CatalogDao,
    DataScope)`); `dataModule` binds `SetlistDao` and `SetlistRepository` as singles.
  - Tests: `EnvelopeTest` +1, `AdminWriterTest` +1 (`send`), `JamsMapperTest` +3,
    `JamsRefreshOutcomeTest` +1, `CatalogDaoTest` +1, `DefaultJamsRepositoryTest` +5 (admin read,
    empty draft, refused fallback remembered per passphrase, failures keep the cache, no POST
    without a passphrase), `DataModuleTest` (the store over a temp file; one `SetlistRepository`),
    new `SetlistDaoTest` 1 and `DefaultSetlistRepositoryTest` 8 (no-UI add with the Sending entry
    and the POST body, 12 failing answers with no phantom row, dismiss, unknown song, no
    passphrase, call order, caller cancellation, refresh-wins and non-available jam).
  - Docs: `docs/apps-script-api.md` (routes, `readJams`, `addSong` with its validation order,
    `checkSetlistWrite`, `AdminWriter.send`, how the admin reads a draft, `SetlistRepository`,
    the `Known:` list), `docs/sheet-schema.md` (`_prueba_lista`, how Apps Script writes a jam tab,
    the `readJams` half of the draft rule), `backend/apps-script/README.md` (intro, layout, Known
    list, Verify-the-paste with both markers, **Redeploy for write auth and add song, in one
    paste**, add-song live commands), architecture `SKILL.md` (Part A as built). Not touched yet,
    left for Part B with the UI: `docs/domain-model.md`, `docs/user-and-access-model.md`,
    `docs/risks-and-open-questions.md`, `DESIGN.md`.
- Verification run:
  - `node --test backend/apps-script/test/*.test.js`: 113/113 (97 before; `setlist.test.js` 16).
  - Failure demonstrations on the final Post.js, each restored and checked with
    `sha1sum -c` (Post.js `94bb9154…` OK every time): (a) `addSong_` creates the tab before the
    catalog check → 1 fail ("addSong validates in order and writes nothing on any failure":
    `unknown_song {"songId":"zz-no-existe"} wrote`); (b1) `readJamsAsAdmin_` without the
    `date < today` guard (releases a past draft) → 2 fail; (b2) every non-`PUBLICADA` status read
    as a draft → 1 fail (the lowercase `borrador` test); (e) the `@` format not set → 4 fail
    (append, `7/4`, both `checkSetlistWrite` tests). An earlier variant "PUBLICADA also re-read"
    passed all tests: re-reading a published tab returns what the GET already served, so it is not
    observable, and was replaced by (b2).
  - Kotlin demonstrations, restored with `sha1sum -c` (OK): (c) `DefaultSetlistRepository` storing
    a row on a refused answer → `DefaultSetlistRepositoryTest` 2 of 8 fail ("every answer but a
    well-formed ok leaves no phantom row…", "with no passphrase stored…": expected `[1]` was
    `[1, 2]`), file `4fc9861f…`; (d) `includeDrafts` defaulting to `true` → 4 fail
    (`JamsMapperTest` 3, `JamsRefreshOutcomeTest` 1), `JamsMapper.kt` `df9da60b…`; (f) the admin
    read falling back to the GET on every refusal → `DefaultJamsRepositoryTest` 1 of 26 fails
    ("any other admin read failure…": `Updated` instead of `Failed(Offline, adminRead = true)`),
    `DefaultJamsRepository.kt` `eaa1708d…`.
  - `./gradlew ktlintFormat`, then `CI=true ./init.sh` exit 0: `konsist: wired` (ModuleIsolationTest
    17/17, no rule change), `detekt: wired`, `ktlint: wired`; 78 result files, 464 tests, 0
    failures (443 before: +21). The first gate run failed detekt (6 findings: `TooManyFunctions` on
    `JamsDao`, two `ReturnCount`, `ComplexCondition`, two `MaxLineLength`); all fixed in code, no
    baseline or suppression.
  - `DefaultSetlistRepositoryTest`, `DefaultJamsRepositoryTest` and `DataModuleTest` re-run three
    times with `--rerun`: no failure.
  - No Gradle dependency, Konsist, manifest, Room version, UI or `:app` change. No device work; the
    device was not touched. No live check (nothing is deployed yet). The passphrase, the URL and
    musician names appear in no output, file or log of this session.
- Not run yet: live checks L1–L6 of this feature and of write-auth (they need the one paste and
  deploy). Part B (UI) not started.
- Next: the user pastes `src/Post.js` into `Post.gs`, verifies both markers
  (`checkWriteAccess: { write: true, run: checkWriteAccess_ },` and
  `addSong: { write: true, run: addSong_ },`, and that `checkPassphrase: checkPassphrase_,` is
  absent), deploys **Manage deployments → Edit → New version**; then write-auth's L1–L6 (its
  `Known:` check now expects the five-action list), validation of write-auth, this feature's
  L1–L6, then Part B.

### Session 073 — 6 October 2026

- Feature: `admin-add-song-to-setlist`: Part A live checks, the review note, then **Part B (UI)**.
  The user deployed the combined Post.js (SHA-1 `94bb9154…`, same URL) and `apps-script-write-auth`
  was accepted before this session's work.
- Live checks (scratchpad script reading `local.properties`; it printed only codes, counts, dates
  and timings, never the URL, the passphrase or a name):
  - L1 `{}`: `unknown_action`, tail `Known: checkPassphrase, checkWriteAccess, readJams, addSong,
    checkSetlistWrite` (1.74 s).
  - L2 `readJams` with no passphrase: `invalid_passphrase` (3.14 s). L3 `addSong` with
    `definitely-wrong`: `invalid_passphrase` (2.85 s). These were the only two deliberate wrong
    guesses.
  - L4 `readJams` with the passphrase: `ok`, 2 jams: `2026-07-25 PUBLICADA` 13 songs and
    `2026-10-31 PUBLICADA` 13 songs, no `setlistError` (3.78, 3.62, 3.69 s). No draft exists, so
    the draft branch was not exercised live (Node covers it).
  - L5 non-mutating `addSong` probes, all as expected: `31/10/2026` → `invalid_date` (3.06 s);
    key `H` → `invalid_key` (2.44 s); `1999-01-01` → `unknown_jam` (2.60 s); `2026-07-25` →
    `jam_not_editable` (4.21 s); `2026-10-31` with `zz-no-existe` → `unknown_song` (5.11 s).
  - L6 `checkSetlistWrite`: `ok` (4.91, 4.43, 5.09 s). No song was added to a real jam.
- Review note (one failing `readJams` per process start after a rotation): **kept, recorded as an
  accepted risk** in `docs/risks-and-open-questions.md`. One guess per cold start of a stale admin
  device; persisting the refusal would mean storing something derived from the passphrase.
- Part A change: `DefaultSetlistRepository.addSong` now runs in `DataScope` from its first
  instruction (`scope.async(start = UNDISPATCHED)`), so a caller cancelled before the song is
  resolved still adds it (the picker closes at once). New test "a caller cancelled at once…".
- Part B, what changed:
  - `:feature:next-jam`: `NextJamPresenter(jams, calendar, AdminSession, SetlistRepository)`,
    `Params(onAddSong, onOpenSong)`; admin layer `NextJamAdminUiModel` (badge, note, pending rows,
    failure cards, `AddSongActionUiModel`), `NoUpcomingJam.adminHint`, admin empty copy, failure
    messages by outcome (C1); one `jams.refresh()` per login from the flag's real values. New
    `AdminControls.kt`, `AdminControlsDefaults.kt`, `AddSongPresenter.kt`, `AddSongUiModel.kt`,
    `AddSongScreen.kt`, `AddSongCopy.kt`, `AddSongDefaults.kt`; `NextJamScreen` appends the admin
    items after the rows (the setlist and admin items moved into `LazyListScope` functions for
    detekt's `CyclomaticComplexMethod`/`LongMethod`/`TooManyFunctions`); `nextJamModule` binds both
    presenters.
  - `:app`: `AppRoutes.ADD_SONG`/`addSong`/`parseAddSong`, the `addSong` destination (the song
    detail destination moved into its own `NavGraphBuilder` function for detekt `LongMethod`),
    `TabsShell(onOpenAddSong)`.
  - `:core:ui`: `BluesJamTheme` provides non-amber `LocalTextSelectionColors`.
  - Tests: new `NextJamAdminTest` 10, `AddSongPresenterTest` 7, `AdminControlsDefaultsTest` 2,
    `AddSongDefaultsTest` 3, `TextSelectionColorsTest` 1, `AppRoutesTest` +1, `NextJamModuleTest`
    (both presenters), `DefaultSetlistRepositoryTest` +1; shared fakes `AdminFakes.kt`; the three
    existing presenter tests construct the presenter with a musician `FakeAdminSession`.
  - Docs: `DESIGN.md` (Admin controls as built, screen 2), architecture `SKILL.md` (Part B),
    `docs/risks-and-open-questions.md` (offline mutations decided, draft read settled, add-song
    risks, write latency), `docs/domain-model.md`, `docs/user-and-access-model.md`.
- Verification run:
  - `./gradlew ktlintFormat`, then `CI=true ./init.sh` exit 0: `konsist: wired` (17/17, no rule or
    allowlist change), `detekt: wired`, `ktlint: wired`; 83 result files, 489 tests, 0 failures
    (464 before). Earlier gate runs failed detekt (`LongMethod` on `AppNavHost` and
    `NextJamContent`, `CyclomaticComplexMethod`, then `TooManyFunctions` on `NextJamScreen.kt`),
    all fixed by extraction; no baseline or suppression. A first test run failed because the
    nullable admin flag added an extra recomposition to musician models (24 failures); fixed by
    collecting the flag separately in the effect.
  - Node 113/113; Post.js SHA-1 unchanged.
  - Failure demonstrations, restored with `sha1sum -c` (OK): (g) musicians mapped from
    `jam.setlist` → 5 fail (`NextJamDraftTest` 2, `NextJamStatesTest` 1, `NextJamAdminTest` 2),
    `NextJamPresenter.kt` `f34a8968…`; (h) no double-tap guard → `AddSongPresenterTest` 1 fails,
    `AddSongPresenter.kt` `e05bb183…`; (c') a row stored on a refused answer → 2 of 9 fail, and (j)
    `addSong` run in the caller instead of `DataScope` → 1 of 9 fails,
    `DefaultSetlistRepository.kt` `58cee21f…`.
  - Device, Pixel 5 (debug build; flags recorded first: `demoUpcomingJam=true`, `debugAdmin=true`,
    `demoUpcomingJamDraft` absent; `local.properties` backed up, edited only to add or flip those
    flags, restored byte-identical by SHA-1 and the backup deleted). No passphrase stored on the
    device at any point (`run-as … ls files/datastore`: no such directory, before and after the
    pick), so no add could reach the server:
    1. Online, `debugAdmin`: the real published 2026-10-31 jam with its 13 rows unchanged and
       `Agregar tema` after row 13 (no badge, published). Startup line `… songs 26 (26 from
       catalog) admin (debug)`.
    2. Picker: title, field, catalog sorted by title; listed songs muted with `Ya está en la
       lista` and not clickable (dump: no clickable node for them); `CLAPTON` filtered to the
       Clapton songs; rotation kept the query; system back returns to the list.
    3. Picked `Cocaine`: the picker closed and the card `No se pudo agregar «Cocaine»` with the
       `AccessRefused` message and `Cerrar` (clickable node 132 px tall, 48dp) appeared above the
       button; the pending row was too short-lived to capture (no request is sent). `Cerrar`
       removed it.
    4. Airplane mode + cleared data + `demoUpcomingJamDraft=true`: the demo draft with `BORRADOR`
       and `Los músicos todavía no ven esta lista.` under the header, its 8 songs and the button;
       the picker offline showed `No pudimos cargar el catálogo` with `Reintentar`.
    5. `debugAdmin=false`: the same demo draft shows the musician card (`EN PREPARACIÓN`), no admin
       control.
    Restored: original flags rebuilt and installed, airplane mode 0, rotation auto (1/0), font
    scale 1.0, accessibility off (`enabled_accessibility_services null`, `accessibility_enabled
    0`, `touch_exploration_enabled 0`), empty crash buffer, `/sdcard/ui.xml` removed. TalkBack and
    accessibility settings were never touched. App data was cleared twice (a re-fetchable cache);
    the last launch online repopulated it (`upcoming 2026-10-31`).
- Status: `passing` (not accepted). Next: independent validation.

### Session 074 — 6 October 2026

- Feature: `admin-remove-song-from-setlist`, **Part A only** (server half and `:core:data`, no UI),
  spec `docs/specs/admin-remove-song-from-setlist.md`. User approvals recorded in 00aa192: R1 (a)
  find the row by `id_tema` and renumber 1..n; U1 as specified (Part B); B1 (a) one batched
  `Post.js` deploy with `admin-set-key`, remove-song's server half first.
  **User-approved exception (B1 (a), as write-auth/add-song):** set-key's server half is added on
  top of this `Post.js` by a later implementer, and both features stay `in_progress` until the one
  paste and deploy. Dependency `admin-add-song-to-setlist` is `accepted` (7e56ed1); deployed
  `Post.js` `94bb9154…`.
- What changed:
  - `backend/apps-script/src/Post.js` (the only `src` file changed; the other four byte-identical,
    `git diff --quiet HEAD` exit 0): `ACTIONS.removeSong` and `ACTIONS.checkSetlistRemove` (both
    `write: true`, guarded by the unchanged router). `removeSong_` validates `invalid_date`,
    `invalid_song`, `requireEditableJam_` (unchanged), then no tab → `song_not_in_setlist`;
    `removeSetlistRow_(sheet, songId)` maps the header (`missing_header`/`duplicate_header`), finds
    the row by trimmed `id_tema` (`song_not_in_setlist`, `duplicate_song`), and only then writes:
    `insertRowAfter` when the row is the grid's last, `deleteRow`, and the later whole-number
    positions rewritten as `posicion - 1` in plain text, in ascending order, **batched** by runs of
    consecutive rows (`positionRuns_`: one `setNumberFormat` and one `setValues` per run; an
    app-built tab is one run). Answers `{ok, position}` (number or null). `checkSetlistRemove_` on
    `_prueba_lista`: three markers appended with `appendSetlistRow_`, the second removed with
    `removeSetlistRow_`, read back with `buildSetlist`, tab deleted in `finally`. SHA-1
    `faf0fd85d53ce9277b3882e6573fedfe6ad64256`.
  - `backend/apps-script/test/setlist.test.js` +11 tests; the fake sheet gained `getMaxRows`,
    `deleteRow`, `insertRowAfter` and range-wide `setNumberFormat`. `post.test.js`: enumerations
    only (seven actions, `Known:` x2, the README marker test).
  - `:core:data`: `SetlistRepository.removeSong`/`observeRemoves`, `dismiss` for either kind;
    new `RemoveSongOutcome`, `SetlistRemove`, internal `SetlistRemovals` (entries, request, cache
    mirror; built by `DefaultSetlistRepository`, which keeps the shared mutexes, ids and
    `DataScope`; constructor and `dataModule` unchanged); `SetlistDao.removeSetlistSong` (one
    transaction, explicit deletes children first, later rows reinserted one position up) with
    `songsOf`, `slotsFrom`, `extrasFrom`, `deleteRows`, `insertRows`. No schema or version change.
    **Deviation:** no `SetlistDao.songTitle`; the title is resolved in `SetlistRemovals` by the
    `jam_song_resolved` rule (catalog title, else the tab copy, else the id), because a twelfth DAO
    function trips detekt `TooManyFunctions`.
  - Test-only support: `feature/next-jam` `AdminFakes.FakeSetlistRepository` implements the two new
    interface members (no production change in `:feature:next-jam`).
  - KDoc: `core/model` `Setlist.kt` and `SetlistMapper.kt` now say positions are the read identity
    and are never renumbered **by the read path**.
  - Docs: `docs/apps-script-api.md`, `backend/apps-script/README.md` (marker
    `removeSong: { write: true, run: removeSong_ },`, Known list, new batched-redeploy section that
    set-key will extend), `docs/sheet-schema.md` ("(`fecha`, `posicion`) is the read identity",
    rows located by `id_tema`, how `removeSong` writes, `_prueba_lista`), `docs/domain-model.md`
    (identity sentence), architecture `SKILL.md` (Part A as built). Part B owns `DESIGN.md`,
    `user-and-access-model.md`, `risks-and-open-questions.md`.
- Verification run:
  - `node --test backend/apps-script/test/*.test.js`: 124/124 (113 before).
  - Failure demonstrations, each restored from a byte copy in the scratchpad (not `git checkout`,
    because of `core.autocrlf`) and checked with `sha1sum -c` (OK): (a) a row deleted before the
    `duplicate_song` throw → Node 1 fail (`duplicate_song … wrote`); (b) renumbering `setValues`
    skipped → Node 7 fail; Post.js `faf0fd85…` OK and 124/124 again; (c) `SetlistRemovals`
    mirroring into Room on every outcome → `DefaultSetlistRepositoryTest` 2 of 15 fail,
    `SetlistRemovals.kt` `c45fae3b…` OK and the suite green again. (d) is Part B.
  - `./gradlew ktlintFormat`, then `CI=true ./init.sh` exit 0: `konsist: wired`
    (ModuleIsolationTest 17/17, no rule change), `detekt: wired`, `ktlint: wired`; 83 result files,
    498 tests, 0 failures (489 before; `SetlistDaoTest` 1 → 4, `DefaultSetlistRepositoryTest`
    9 → 15). The first gate failed detekt `TooManyFunctions` on `DefaultSetlistRepository` (13);
    fixed by extracting `SetlistRemovals`, no baseline or suppression. One targeted test run failed
    on a test-fixture bug (the LATER jam was `AVAILABLE`), fixed in the fixture.
  - No Gradle dependency, Konsist, manifest, Room version, UI or `:app` change. No device touched.
    No live check (not deployed). The passphrase, the URL and musician names appear in no output.
- Not run yet: live checks LR1–LR5 (after the batched deploy), Part B (UI), device checks.
- Next: `admin-set-key`'s server half on top of this `Post.js` (it adds its own README marker to
  the batched-redeploy section and extends the `Known:` list), then the user's single paste and
  **New version** deploy, then remove-song's LR1–LR5 (LR1 `Known:` must include `removeSong,
  checkSetlistRemove` plus set-key's actions), then Part B.
### Session 075 — 6 October 2026

- Feature: `admin-set-key`, **Part A only** (server half and `:core:data`, no UI), spec
  `docs/specs/admin-set-key.md`. Approvals recorded through f026cc3: B1 (a) one batched deploy with
  remove-song, V1, O1 optimistic, L1 (a) extend `checkSetlistWrite`, R1, C2 `Guardando…`, row found
  by `id_tema`. Built on remove-song's Part A (e3c7287, `Post.js` `faf0fd85…`, not deployed).
  **Same user-approved exception as session 074:** both features stay `in_progress` until the one
  paste and deploy. Dependency `admin-add-song-to-setlist` is `accepted` (7e56ed1).
- What changed:
  - `backend/apps-script/src/Post.js` (the only `src` file changed; the other four byte-identical,
    `git diff --quiet HEAD` exit 0): `ACTIONS.setKey` (`write: true`, guarded by the unchanged
    router). Remove-song's row scan extracted as `findSongRow_(sheet, columns, values, songId)`
    (1-based row; `song_not_in_setlist`, `duplicate_song`); `removeSetlistRow_` calls it and
    remove-song's Node tests pass unchanged. `setKey_` validates `invalid_date`, `invalid_song`,
    `invalid_key`, `requireEditableJam_`, no tab → `song_not_in_setlist`, header mapping, then
    `findSongRow_`, and only then `writeKeyCell_` (`setNumberFormat('@')`, `setValue(key)` on the
    one `tono` cell). Never opens `Catalogo`. Answers `{ok:true}`. `checkSetlistWrite_` gains one
    step: after appending the `Bbm` marker, `findSongRow_` + `writeKeyCell_(…, 'F#m')`; the
    read-back must be the marker with `F#m`. Header comment updated. SHA-1
    `be205ea3f5d5081e5896666d3490ae30cab06119`.
  - `backend/apps-script/test/setlist.test.js` +8 tests; the fake sheet gained an additive
    `lostValues` option (a dropped `setValue`). `post.test.js`: enumerations only (eight actions,
    `Known:` x2, the README marker `setKey: { write: true, run: setKey_ },`).
  - `:core:data`: `SetlistRepository.setKey`/`observeKeyChanges`, `dismiss` for all three kinds;
    new `SetKeyOutcome` (`KeySet`, `NotSet(reason)`), `KeyChange` (`Sending`, `Failed(reason)`),
    internal `SetlistKeyChanges` (entries, request, mirror; built by `DefaultSetlistRepository`,
    constructor and `dataModule` unchanged) and internal `displayedTitle` in `SongTitles.kt` (the
    `jam_song_resolved` title rule, moved out of `SetlistRemovals`, which now calls it).
    `SetlistDao.updateKey(date, songId, key): Int`. On `Done` the cache is updated first, then the
    entry removed; every other answer leaves Room alone. No schema or version change.
    **Deviation from the spec:** the spec named `@Transaction suspend fun updateKey(…): Boolean`
    reusing `songTitle` and a separate `UPDATE`. `songTitle` does not exist (remove-song's
    deviation), and a second DAO function would push `SetlistDao` past detekt `TooManyFunctions`
    (10 → 12). Instead `updateKey` is **one** `@Query UPDATE` whose `WHERE` carries the `AVAILABLE`
    and exactly-one-cached-id conditions as subqueries (atomic as one statement), returning the
    rows changed (1 or 0). Same behaviour, no suppression.
  - Test-only support: `feature/next-jam` `AdminFakes.FakeSetlistRepository` implements the two
    new interface members (no production change in `:feature:next-jam`).
  - Docs: `docs/apps-script-api.md` (`setKey` section, `findSongRow_` note, extended
    `checkSetlistWrite`, `Known:` x2, client key change), `backend/apps-script/README.md` (intro,
    file table, `Known:` x3, verify-the-paste table, the batched-redeploy section with both markers
    and the final `Known:` list, set-key probes), `docs/sheet-schema.md` (`setKey` writes one
    `tono` cell; `_prueba_lista`), `docs/domain-model.md` (setting a key as built), architecture
    `SKILL.md` (Part A as built, the overlay rule). Part B owns `DESIGN.md` and
    `risks-and-open-questions.md`.
- Verification run:
  - `node --test backend/apps-script/test/*.test.js`: 132/132 (124 before).
  - Failure demonstrations, each restored from a byte copy in the scratchpad (not `git checkout`)
    and checked with `sha1sum -c` (OK): (a) `setKey_` writes the first matching row before
    `findSongRow_` → Node 4 fail, among them `duplicate_song {"songId":"the-thrill-is-gone"}
    wrote`; `Post.js` `be205ea3…` OK and 132/132 again. (b) `updateKey` without `song_id` in its
    `WHERE` → `SetlistDaoTest` "updateKey sets only the key of the matching song of that date"
    (expected 1, was 4) and the repository success test fail; `SetlistDao.kt` `5a1f1256…` OK.
    (c) `SetlistKeyChanges` mirroring into Room on `Rejected` → "every failed key change leaves
    the cache and the catalog as they were…" fails; `SetlistKeyChanges.kt` `222e8fbf…` OK.
  - `./gradlew ktlintFormat` (no change), then `CI=true ./init.sh` exit 0: `konsist: wired`
    (ModuleIsolationTest 17/17, no rule change), `detekt: wired`, `ktlint: wired`; 83 result
    files, 509 tests, 0 failures (498 before; `SetlistDaoTest` 4 → 6,
    `DefaultSetlistRepositoryTest` 15 → 24).
  - No Gradle dependency, Konsist, manifest, Room version, UI or `:app` change. No device touched.
    No live check (not deployed). The passphrase, the URL and musician names appear in no output.
- Not run yet: the deploy, remove-song's LR1–LR5 and set-key's L1–L5, both Parts B, device checks.
- Next: the user pastes `src/Post.js` (SHA-1 `be205ea3f5d5081e5896666d3490ae30cab06119`) into `Post.gs`,
  verifies the markers `removeSong: { write: true, run: removeSong_ },` and
  `setKey: { write: true, run: setKey_ },` (and that `checkPassphrase: checkPassphrase_,` is
  absent), and deploys **New version**; then the live checks (L1 `Known:` ends `removeSong,
  checkSetlistRemove, setKey`), then remove-song's Part B, then set-key's Part B.

### Session 076 — 6 October 2026

- Feature: `admin-remove-song-from-setlist`, **Part B (UI)**, spec
  `docs/specs/admin-remove-song-from-setlist.md` (approvals R1 (a), U1 as specified, B1 (a)). Built
  on a2c9a0e (set-key Part A on top of remove-song Part A). Status stays `in_progress`: the batched
  `Post.js` is not deployed and live checks LR1–LR5 are the orchestrator's after the deploy.
- What changed (`:feature:next-jam`, plus one `:core:ui` test):
  - `ExpandedRows` keyed by (date, song id text), not position (scenario 9); Saver stores strings.
    The lazy list keeps position keys (a hand-edited tab may repeat a song id).
  - `SongRowUiModel.admin: SongRowAdminUiModel?` (null for musicians) with `removal:
    RemovalUiModel` — `Idle`, `Confirming(prompt, details, confirmLabel, cancelLabel)`,
    `Removing(status)`; events `RequestRemove`, `Confirm`, `Cancel`.
  - `NextJamPresenter`: collects `observeRemoves()`; the confirming row is one `rememberSaveable`
    String (`removalKey` = "date|songId"); `Confirm` acts only while that key is the row's, clears
    it and launches `removeSong` undispatched (double tap removes once). `AdminState.removal:
    RemovalState`. Failures merge failed adds and removes in id order (`AddFailureUiModel` kept);
    `failureMessage` adds `song_not_in_setlist` and `duplicate_song`.
  - Screen: `AdminRowActions` after `Ver detalle del tema` in an expanded row (set-key's `Cambiar
    tonalidad` goes inside it, before the removal); the confirmation block and `Quitando…` with
    polite live regions; colours `AdminControlsDefaults.removal()` (`error`, `text`, `textMuted`; no
    amber); 48dp `Role.Button` targets. Copy in `NextJamCopy` as approved. New preview file
    `NextJamRemovalPreview.kt`.
  - Tests: new `NextJamRemoveTest` (9), `ExpandedRowsTest` for song ids, three `ExpandedRows(...)`
    call sites in `NextJamDraftTest`/`NextJamPresenterTest`, `AdminControlsDefaultsTest` (removal
    roles), `ContrastTest` `error on a surface` 10.12:1.
  - Docs: `DESIGN.md`, `docs/user-and-access-model.md`, `docs/risks-and-open-questions.md`,
    `docs/domain-model.md` (removal edge case), architecture `SKILL.md`.
- Verification run:
  - Failure demonstrations, `NextJamPresenter.kt` restored from a byte copy in the scratchpad (not
    `git checkout`), `sha1sum -c` OK each time: (d) expansion keyed by position → 2 of 88 fail,
    scenario 9 among them; (e) `Confirm` without the key check → 2 of 88 fail (double tap; stale
    Confirm after Cancel). Green after each restore.
  - `./gradlew ktlintFormat`, then `CI=true ./init.sh` exit 0: `konsist: wired` (17/17,
    `konsist-test` and the amber allowlist unchanged), `detekt: wired`, `ktlint: wired`; 84 result
    files, 519 tests, 0 failures (509 before). The first gate failed detekt on `NextJamScreen.kt`
    (`TooManyFunctions` 12/11, one KDoc `MaxLineLength`); fixed by moving the preview out, no
    suppression.
  - Device, Pixel 5, debug build (`demoUpcomingJam`, `debugAdmin` on; the demo published jam, 13
    songs): no `files/datastore` before. Expanded row 02 → `Quitar de la lista` (48dp); tap → the
    prompt, `Se borran también los 3 músicos anotados.`, the published line, `Quitar`/`Cancelar`;
    `Cancelar` → back; requested again, rotated to landscape and back, still confirming; `Quitar` →
    row back to the action, card `No se pudo quitar «Walking Thru the Park»` with the access copy
    and `Cerrar`, 13 rows intact; `Cerrar` → card gone. `Quitando…` not observable (no request is
    made without a passphrase). No `files/datastore` after. Settings recorded and read back equal
    (rotation, airplane, accessibility 0, no services, touch exploration 0, timeout, stay-on).
    TalkBack never touched. Not done on the device: flag off (covered by the JVM musician test).
  - No live check (not deployed). No secret, URL or real musician name in any output.
- Next: the user's batched deploy (`Post.js` `be205ea3…`), then remove-song's LR1–LR5 (including
  `removeSong` latency for the risks doc) and validation; then set-key's live checks and Part B.
  Note for set-key Part B: its spec maps `song_not_in_setlist` to `JAM_CHANGED` for a key change,
  while `failureMessage` now maps it to remove-song's `Ese tema ya no estaba en la lista.`; set-key
  needs a per-kind message for that code.
- **Live checks LR1–LR5** (7 October 2026, after the user's batched deploy of `Post.js`
  `be205ea3…`; script in the scratchpad, URL and passphrase read inside it, never printed):
  - LR1 `{}` → `unknown_action`, `Known:` ends `removeSong, checkSetlistRemove, setKey`.
  - LR2 wrong passphrase → `invalid_passphrase` (one deliberate guess).
  - LR3, all refused before any write: `invalid_date`, `invalid_song`, `unknown_jam`
    (1999-01-01), `jam_not_editable` (past 2026-07-25), `song_not_in_setlist` (upcoming
    2026-10-31, `zz-no-existe`); 2.3–3.5 s each.
  - LR4 `checkSetlistRemove` → `ok` twice, 7.12 s and 4.91 s.
  - LR5 `readJams` before and after: both jams 13 rows, counts and full body identical (no real jam
    modified). `removeSong` was never called with a real song id.
- Every check in the spec has now run; the feature is ready for independent validation.

### Session 077 — 6 October 2026

- Feature: `admin-set-key`, **Part B (UI)**, spec `docs/specs/admin-set-key.md` (approvals B1 (a), V1
  as specified, O1 optimistic, L1 (a), R1, C2 `Guardando…`; row found by `id_tema`; route
  `setKey/{jamDate}/{songId}`). Built on 1aca145 (remove-song Part B). Status stays `in_progress`:
  the batched `Post.js` (`be205ea3…`) is not deployed and the live checks L1–L5 are pending.
- What changed:
  - `:feature:next-jam`: `NextJamPresenter.Params(onAddSong, onSetKey, onOpenSong)`; collects
    `observeKeyChanges()` into `AdminState.keyChanges`; for the admin, a row with a `Sending` change
    draws the latest one's key (highest id) and `keyDescription`, with
    `SongRowAdminUiModel.keyStatus` `Guardando…`. A `Failed` change never overlays (the revert).
    `SongRowAdminUiModel(setKey: SetKeyActionUiModel, removal, keyStatus)`. Failure cards merge
    adds, removals and key changes by id; key changes use `keyFailureMessage`
    (`song_not_in_setlist` and the jam codes → `La jam cambió en la planilla…`, `duplicate_song` →
    the shared duplicate line, else `failureMessage`), so remove-song keeps `Ese tema ya no estaba
    en la lista.` New `KeyChangeOverlay.kt` (`pendingKey`) and `FailureMessages.kt` (moved
    `failureMessage` out of `NextJamPresenter.kt` for detekt `TooManyFunctions`).
  - Screen: `KeyStatusLine` under the title line (`caption`, `textMuted`, polite live region);
    `AdminRowActions` draws `Cambiar tonalidad` (`AdminControlsDefaults.keyChange()`, `text`) before
    the removal, which stays last.
  - Picker: `SetKeyPresenter(JamsRepository, AdminSession, SetlistRepository)`, `SetKeyUiModel`
    (`Loading`, `Gone`, `Content`), `SetKeyScreen` (scrollable, preview inside), `SetKeyCopy`,
    `SetKeyDefaults` (24 keys, `COLUMNS = 4`, cell and header colours; `key` only on the current key;
    outline `BorderStroke(Dp.Hairline, border)`, no dp literal). `di/NextJamModule` factory.
  - `:app`: `AppRoutes.SET_KEY`, `setKey(date, songId)`, `parseSetKey` (ISO date,
    `SongId.parseOrNull`) and `SetKeyArgs`; `AppNavHost` `setKey` destination beside `addSong` (same
    slide, insets, `RESUMED` back guard; `onDone` pops by route); `TabsShell(onOpenSetKey)` →
    `NextJamScreen(onSetKey)`.
  - Tests: new `NextJamKeyChangeTest` 9, `SetKeyPresenterTest` 7, `SetKeyDefaultsTest` 2;
    `AppRoutesTest` 10 → 11, `NextJamModuleTest` (SetKeyPresenter factory),
    `AdminControlsDefaultsTest` (keyChange roles).
  - Docs: `DESIGN.md` (Setting a key, the optimistic rule, Key picker as built, Core Screens item 2),
    `docs/risks-and-open-questions.md` (Set-key risks), architecture `SKILL.md` (Part B as built).
- Verification run:
  - Failure demonstration (d): `pendingKey` without the `Sending` filter (a `Failed` entry overlays)
    → 3 of 106 `:feature:next-jam` tests fail: `a sending change draws the new key with Guardando,
    and a failure reverts it and adds a card` (expected A, was Bb), `when the later change fails
    after the earlier one succeeded…` (expected Bb, was C) and the picker's `a pending change is the
    current key… and a failed one is not` (expected A, was Bb). `KeyChangeOverlay.kt` restored from
    a byte copy in the scratchpad, `sha1sum -c` OK (`40bf33ad897df61023fe1d7d2822d431d412c109`).
  - `./gradlew ktlintFormat`, then `CI=true ./init.sh` exit 0: `konsist: wired` (17/17,
    `konsist-test` unchanged, so `AMBER_ROLE_ALLOWLIST` still `{key}` for `:feature:next-jam`),
    `detekt: wired`, `ktlint: wired`; 87 result files, 538 tests, 0 failures (519 before). Two
    earlier gate runs failed detekt (a KDoc `MaxLineLength` in `NextJamScreen.kt`; `ReturnCount` 3/2
    in `setKeyModel`); fixed by wrapping and restructuring, no suppression.
  - Device, Pixel 5, debug build (`demoUpcomingJam`, `debugAdmin` on). The upcoming jam was the
    Sheet's own `2026-10-31` test jam (startup line `upcoming 2026-10-31 … admin (debug)`, no
    `(demo)`), 13 songs; no passphrase stored, so nothing was sent. No `files/datastore` before.
    Expanded row 02 (Walking Thru the Park, A): `Ver detalle del tema`, `Cambiar tonalidad`, `Quitar
    de la lista`, in that order. The picker: `Volver`, `Cambiar tonalidad`, the song title,
    `TONALIDAD ACTUAL` over an amber `A`, `MAYORES`/`MENORES` 4×3 grids in the approved spelling,
    the `A` cell outlined, amber, `actual`; `uiautomator dump`: 24 clickable nodes (Volver + 23
    cells, the `A` cell not clickable), every cell described `Tonalidad X`, cell height 132 px (50dp).
    Landscape (`user_rotation 1`) and back: the picker redrawn the same; in landscape the right-hand
    navigation bar overlaps the last column's edge (recorded as a risk; the add-song picker shares
    the inset pattern). Font scale 2.0: everything fits, `actual` on one line, the screen scrolls;
    restored to 1.0. Tap `Bb` → the picker closed, row 02 shows `A` with no status line, and the
    card `No se pudo cambiar la tonalidad de «Walking Thru the Park»` / `La frase de acceso cambió o
    no es válida. Salí del modo admin en Info y volvé a entrar.` / `Cerrar` sits after the rows,
    before `Agregar tema`. The optimistic `Bb` with `Guardando…` was **not** captured: three
    back-to-back screencaps right after the tap already show `A` (AccessRefused returns without a
    request); the overlay and the revert are proven by the JVM tests and demonstration (d).
    `Cerrar` → card gone. Info → `Salir del modo admin` (ends the debug forced state) → Próxima jam:
    expanded row 02 shows only `Ver detalle del tema` (musician view unchanged). App force-stopped
    and relaunched: `admin (debug)` again. No `files/datastore` after; crash buffer empty.
  - Settings: recorded before (`font_scale 1.0`, `accelerometer_rotation 1`, `user_rotation 0`,
    `enabled_accessibility_services null`, `screen_off_timeout 1800000`,
    `stay_on_while_plugged_in 7`) and read back equal after. `accelerometer_rotation` was 0 during
    the rotation check and restored to 1. TalkBack and accessibility never touched.
    `local.properties`, the URL and the passphrase never printed.
  - No live check (not deployed).
- Next: the user's batched deploy (`Post.js` `be205ea3…`), remove-song's LR1–LR5 and validation,
  then set-key's L1–L5 (with the L3 latency for the risks doc) and validation.
- Addendum, 7 October 2026: live checks after the user's batched deploy (`Post.js`
  `be205ea3f5d5081e5896666d3490ae30cab06119`, local SHA-1 read back equal). Scratchpad script
  `live_setkey.py` (SHA-1 `4d97e857…`): URL and passphrase read inside Python from
  `local.properties`, `json.dumps` bodies, only codes, counts and timings printed; `setKey` never
  called with a real song id.
  - L1 `{}` → `unknown_action`; the `Known:` list (8 actions) includes `setKey`, `removeSong` and
    `checkSetlistRemove`.
  - L2 `setKey` with a wrong passphrase → `invalid_passphrase` (one wrong guess).
  - L3 `checkSetlistWrite` (now including the key rewrite on `_prueba_lista`) → `ok` in 4439 ms.
  - L4 `readJams` → `ok`, upcoming `2026-10-31` with 13 songs, 1 past jam.
  - L5, each writing nothing: `invalid_date` (2026-02-30), `invalid_song` (`No Valido`),
    `invalid_key` (`H`), `unknown_jam` (1999-01-01), `jam_not_editable` (the past jam),
    `song_not_in_setlist` (upcoming date, `zz-no-existe`). Latencies 1.9–6.5 s.
  - `readJams` after: song counts and SHA-256 of the jams JSON identical to before.
  - No URL, passphrase or name in any output. Next: independent validation of `admin-set-key`.

### Session 078 — 6–7 October 2026

- Feature: `live-refresh-during-jam`, spec `docs/specs/live-refresh-during-jam.md` (approvals L1–L4
  recorded in c6feb11, all as recommended). Built on b981cbf. **Three features `in_progress` at
  once** during the session: `admin-remove-song-from-setlist` and `admin-set-key` (code-complete,
  waiting for the user's batched deploy and live checks, user-approved exception) and this one,
  which needs no deploy. Status now **`passing`**, awaiting independent validation. The device run
  was interrupted on 6 October by the phone's secure lock screen and finished on 7 October.
- Task 1 (dependency check): `./gradlew :feature:next-jam:dependencies --configuration
  debugCompileClasspath` shows `androidx.lifecycle:lifecycle-runtime-compose:2.8.7 -> 2.9.3` under
  `androidx.compose.ui:ui:1.9.1` (an `api` of `:core:ui`); `LocalLifecycleOwner` and
  `currentStateAsState` compiled in `:feature:next-jam` with no build file change. No new dependency.
- What changed:
  - `:core:ui` `state/PullRefresh.kt`: `PullRefreshUiModel` (`Event.Refresh`, `IDLE`),
    `RefreshableContent` (Material 3 `PullToRefreshBox`, opt-in inside `:core:ui` only, custom
    accessibility action `Actualizar`), internal `PullRefreshDefaults` (`surfaceRaised`, `text`) and
    `PullRefreshCopy`.
  - `:feature:next-jam`: `LiveRefresh.kt` (constants, `liveWindow`, `liveJams`, `jitter`,
    `runLiveRefresh`); `NextJamRefreshes.kt` (`rememberNextJamRefreshes`: the loop in
    `LaunchedEffect(isResumed, liveJams)`, quiet masking, pull and Retry as user refreshes; after a
    periodic call it stays quiet until the snapshot says "not refreshing", bounded by 30 s, so the
    notice cannot flash for one frame); `NextJamPresenter` (`Params.isResumed` default false, fifth
    constructor parameter `random = Random.Default`, KDoc); `NextJamUiModel` (`pullRefresh` on every
    variant); `NextJamScreen` (lifecycle to `isResumed`, `RefreshableContent` around every state; the
    loading and error states scroll).
  - `:feature:past-jams`: pull only (`PastJamsPresenter`, `PastJamsUiModel`, `PastJamsScreen`).
  - `:app` debug: `bluesjam.demoUpcomingJamLive` to `BuildConfig.DEMO_UPCOMING_JAM_LIVE` (release
    hard `false`), `DebugFlags.demoUpcomingJamLive`; the demo decorator in live mode replaces the
    upcoming jam with the demo dated today, start = now (BA, minute) + 10 min, computed once, and
    logs `demo jams refresh: <Updated|Failed> at <instant>`. The flag works on its own.
  - Tests: new `PullRefreshDefaultsTest` 4, `LiveRefreshWindowTest` 5, `LiveRefreshLoopTest` 9,
    `NextJamLiveRefreshTest` 13 (including the requested race check: a refresh landing mid-write
    leaves the optimistic key, `Guardando…` and `Quitando…` in place; and a refresh already holding
    an added song while its add is sending shows the row and the pending row until the add ends),
    `PastJamsPullRefreshTest` 3; `DemoUpcomingJamRepositoryTest` 8 → 11, `DebugAdminSessionTest`
    6 → 7. Both `FakeJamsRepository` fakes are now single-flight with `fetches`, `hold` and `fails`.
    Existing admin tests (add, remove, set key) unchanged and green.
  - Docs: `docs/risks-and-open-questions.md` (Live refresh risks: load estimate, growth, battery,
    the race, TalkBack, the out-of-view notice, experimental API; assumption 6),
    `docs/apps-script-api.md` Quotas (jam-night polling), `DESIGN.md` Required States (pull and live
    window), `CONTEXT.md` (Live Window), architecture `SKILL.md` (live-refresh pattern, sixth
    `core.ui.state` example, the debug flag), presenter KDoc.
- Verification run:
  - `./gradlew ktlintFormat`, then `CI=true ./init.sh` exit 0: `konsist: wired` (17/17, unchanged),
    `detekt: wired`, `ktlint: wired`; 92 result files, 576 tests, 0 failures (538 before). The first
    gate run failed detekt (`MaxLineLength` in a `PastJamsScreen` KDoc; `TooManyFunctions` 12/11 in
    `NextJamScreen.kt` from a new helper); fixed by wrapping and inlining, no suppression. No source
    file changed after that run (docs and harness only).
  - Failure demonstrations, each restored from a byte copy in the scratchpad with `sha1sum -c` OK:
    (a) loop keyed on the snapshot: 2 of 133 fail (`a refresh that emits a new snapshot does not
    restart the loop`, `a Retry during a periodic refresh shows Actualizando`: 3 calls, expected 2);
    (b) no quiet masking: 2 fail (`a periodic refresh is quiet` saw `Actualizando…`; `a Failed model
    stays Failed` got `Loading`); (c) no pull guard: 1 fails (the second pull made 2 calls). Restored
    `NextJamRefreshes.kt` SHA-1 `200edac5cb34f773a173d3726abc991376443209`. (d) `liveJams` without
    `past.first()`: 3 fail (window candidates, the after-midnight test, `a Failed model stays
    Failed`); restored `LiveRefresh.kt` SHA-1 `d78bb93a94c28754740e4d61b89d61d7de1b7ae7`.
  - `:app:assembleRelease` exit 0; release `BuildConfig.DEMO_UPCOMING_JAM_LIVE = false`; no
    `demo jams refresh` or `DemoUpcomingJamRepository` string in any release dex (the debug dex has
    the string, so the check can find it).
  - Device, Pixel 5, debug build. `local.properties` backed up once; `bluesjam.demoUpcomingJamLive=true`
    appended for each run (the file already had the demo and debug-admin flags on) and the file
    restored from the byte copy after each run (SHA-1 match, `cmp` identical). The real Sheet still
    has the 2026-10-31 test jam; the live demo replaced it, as designed.
    - First run, 6 October. Startup `jams cache: upcoming 2026-10-06 (demo) … admin (debug)`, the
      screen `Martes 6 de octubre · 15:18`, `Demo (solo debug)`, `Hoy`. Step 1: three
      `demo jams refresh: Updated` lines at `15:08:57.412` (the startup refresh, the debug-admin
      login refresh and the first periodic call joined one fetch: one `jams refresh: updated 2 jams`
      line), then `15:09:30`, `15:10:02`, `15:10:35` (every 32–33 s: 30 s plus the call). Step 2:
      Home at 15:10:37, no line until 15:11:47; screen off at 15:11:47, no line until 15:12:59. The
      screen-off brought up the phone's secure lock screen (`deviceLocked=1`, screencap blank); the
      agent cannot unlock it and did not try, so that run stopped (no polling behind the lock screen,
      as intended).
    - Second run, 7 October (phone unlocked). Launch 08:57:54, demo jam `Miércoles 7 de octubre ·
      09:07`; lines 08:57:59 (three joined) and 08:58:33.
    - Step 2: Home at 08:58:43, no line until back at 08:59:48; then 08:59:52 (data older than 30 s,
      so at once) and 09:00:26.
    - Step 3: Anteriores at 09:00:29, no line until back on Próxima jam at 09:01:34; then 09:01:37
      and 09:02:10.
    - Step 4: airplane on 09:02:30: `Failed` at 09:02:40 and 09:03:40 (60 s); off at 09:04:31,
      `Updated` at 09:04:44, then 09:05:17. Repeated to read the notice (inserted above the header
      while the list stays anchored on the header, it starts just out of view; two short swipes under
      the pull threshold revealed it, and no extra refresh line appeared): `uiautomator dump` at
      09:14:00, 09:14:23 (one second after the failed tick at 09:14:22) and every ~6 s to 09:15:03
      read `Sin conexión` / `Mostrando lo guardado hace menos de un minuto.`, then `… hace 1 minuto.`
      / `Reintentar`, never `Actualizando…`. Off at 09:15:06, `Updated` at 09:15:26; the dump at
      09:16:16 had no notice. Airplane mode read back 0 after each run.
    - Step 5: pull on Próxima jam at 09:05:42, one line at 09:05:46 (the 09:05:51 line is the
      periodic tick due 30 s after 09:05:17 plus the call); pull on Anteriores at 09:05:55, one line
      at 09:06:03. Both screenshots show the indicator: a raised-surface disc with a light arc, no
      amber.
    - Step 6: `local.properties` restored, flagless build reinstalled, launched 09:16:40: startup
      lines only (`jams cache: upcoming 2026-10-31, … admin (debug)`, the real jam, more than 30 min
      away) and no `demo jams refresh` and no further `jams refresh` line through 09:18:26 (106 s).
  - Settings read back equal to the values recorded before (`airplane_mode_on 0`, `font_scale 1.0`,
    `accelerometer_rotation 1`, `user_rotation 0`, `enabled_accessibility_services null`,
    `accessibility_enabled 0`, `screen_off_timeout 1800000`, `stay_on_while_plugged_in 7`); crash
    buffer empty. TalkBack and accessibility never touched (the `Actualizar` custom action is not
    checked on a device); `local.properties`, the URL and the passphrase never printed.
  - Finding outside this slice: a staleness notice that appears while the list is at its top is out
    of view until the user scrolls up (a `list-states` behaviour that polling makes more frequent).
    Recorded in the risks doc.
- Next: independent validation of `live-refresh-during-jam`; the two admin slices still wait for the
  batched deploy.

## Notes For The Next Session

- The Sheet schema is settled (`docs/sheet-schema.md`) and the seed is imported (user's report).
  What a human still owns, in parallel with coding: **loading real repertoire** with tempo, tags and
  difficulty.
  Week 5 needs a catalog large enough for the assistant to produce an interesting themed setlist.
- Before `action-contract-registry` runs, confirm the mutation list is complete. The current list is
  add song, remove song, set key, adjust lineup, assign musician, clear slot, reorder, publish
  (D-13).
- Capture course evidence as it appears rather than reconstructing it later: the Konsist output, the
  feature-module diff when the assistant lands, and a timed recording of the manual flow before the
  app replaces it.

- **Orchestrator note:** the implementer briefly enabled TalkBack via adb, against a standing
  constraint (no TalkBack via adb) that the orchestrator's prompt failed to restate. It opened its
  tutorial; the implementer turned it off at once. Read back by the orchestrator: no accessibility
  service enabled, `accessibility_enabled 0`, `touch_exploration_enabled 0`, airplane 0. Future
  device prompts restate the constraint.
