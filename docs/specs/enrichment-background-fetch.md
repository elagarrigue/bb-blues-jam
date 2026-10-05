# Feature Implementation Spec: Enrich the catalog's artists from MusicBrainz in the background

## Source Feature

- `id`: enrichment-background-fetch
- `area`: data
- `depends_on`: catalog-repository-cache (`accepted`)
- `status`: not_started (at planning time, 5 October 2026, HEAD `b421fbc`)
- `source`: `feature_list.json`

**Planner's recommendation: do not start this slice before the phase 1 critical path.** It has
**no visible effect** in the app (see Goal), its only consumer is the phase 2 assistant (D-14,
D-20), and the feature entry itself says it is "fully removable if the timeline tightens". With
24 of 38 slices accepted, the admin chain (passphrase, write auth, seven mutations, action
registry) still open and the assistant due in week 5, run it only when the critical path is waiting
on a human, or move it to the start of phase 2. The spec is still implementation-ready if the user
approves it (**U1–U4 below must be answered first**).

**The feature entry does not match the approved design** and must be reworded by the orchestrator
once U1 is answered. It says "its artwork and canonical metadata appear once fetched", but
`DESIGN.md` (song detail, "Not shown (D-20, D-09): … artwork …") shows no artwork anywhere, and no
screen shows any enrichment field. Proposed wording, for U1 = narrow:

- `user_visible_behavior`: "Nothing visible. Each catalog artist's country is fetched from
  MusicBrainz in the background and cached, so the phase 2 assistant can filter blues nacional by
  data; the app is fully usable if the fetch never succeeds."
- `verification`: keep the three lines, reading "on song add" as "when a catalog refresh brings an
  artist that was never looked up" (the Sheet owns the catalog, D-04; the app adds no songs).

## Goal

After the catalog is cached, `:core:data` looks up each **distinct catalog artist** once on
MusicBrainz, at no more than one request per 1.1 s, and caches the artist's country in Room. From
then on, `CatalogRepository.observeCatalog()` emits each `Song` with `artistArea` filled (e.g.
`"AR"` for Pappo, `"US"` for B.B. King) or null. Nothing is drawn; no screen changes.

Why `artistArea` and nothing else: it is the only enrichment field with a decided consumer. D-20
says "'Blues nacional' sigue siendo posible por el país del artista que trae MusicBrainz", and
`domain-model.md` says `artistArea` "earns its place" for D-14. Artwork and preview (Deezer) have no
screen that shows them; similar artists (Last.fm) need an API key, which a public repo cannot hold.

## Non-Goals

- Deezer (artwork, preview, `deezerTrackId`), Last.fm, and any image loader. A future slice, only if
  the user wants artwork drawn (U1 alternative b).
- `Song.mbid` (recording identity), `previewUrl`, `artworkUrl`, `deezerTrackId`: stay null.
- Any UI, presenter, copy, or `:feature:*` change. No filter by country.
- WorkManager or any scheduling beyond "once per process start" (U3).
- Writing anything to the Sheet (D-04: enrichment is never stored there, `sheet-schema.md`).
- Keys, tempo, tags, difficulty, songsterrId (D-08, D-20). MusicBrainz is never asked for a key.

## Job Story

When the assistant later builds "una jam de blues nacional",
I want each catalog artist's country already cached on the device,
so the selection is filtered by data, not by the model's memory, and never waits on MusicBrainz.

## Decisions

**E1 — Keyed by artist, not by song.** One request per distinct artist (the seed has 9 artists for
13 songs). Table `artist_enrichment`, PK `artist_key` = the catalog artist normalized: trim,
collapse inner whitespace, NFD with combining marks removed, `lowercase(Locale.ROOT)`. A separate
table, never columns on `catalog_song`, so a catalog replace never wipes it (as the
`catalog-repository-cache` spec required). Columns: `artist_key` TEXT PK, `status` TEXT
(`MATCHED` | `NO_MATCH`), `mbid` TEXT NULL (the artist's MBID, kept for audit only), `country` TEXT
NULL, `checked_at` INTEGER (epoch ms).

**E2 — What `artistArea` holds.** The MusicBrainz artist's `country` field, an ISO 3166-1 alpha-2
code (`"AR"`), or null when the match has none. Not `area.name`: an area can be a city or a
subdivision, and a code is what a filter compares. `domain-model.md` gets one sentence saying so.

**E3 — Conservative matching (wrong data is worse than none).** Request
`GET https://musicbrainz.org/ws/2/artist?query=artist:"<name>"&fmt=json&limit=5` (inside the
phrase, escape `\` and `"`). `ArtistMatcher` (pure) returns MATCHED only when exactly one result
has `score == 100` **and** a normalized `name` equal to the `artist_key`; otherwise NO_MATCH
("B.B. King & Eric Clapton", "Tradicional", two homonyms). A NO_MATCH is permanent-absent, not an
error (`domain-model.md`, Edge Cases); it is looked up again after 30 days. A MATCHED row is never
looked up again.

**E4 — Rate limit and failure policy.** Requests are strictly serial through an internal
`RateLimiter` (min 1 100 ms between request starts, via an injected `TimeSource` and `delay`).
- 200 with a decodable body: write the row (one insert per artist, `@Insert(onConflict =
  REPLACE)`, not `@Upsert`; see `technical-discovery.md`, Room JVM note).
- 200 with an undecodable body: skip that artist, write nothing, continue.
- Any `IOException`, 503 (MusicBrainz's rate-limit answer) or other non-2xx: **stop the run**,
  write nothing for that artist. The next process start retries. Offline therefore costs one request.
- `User-Agent` on every request: the value bound from `:app` (U2). OkHttp client: the existing
  `single` client is reused through `newBuilder()` with connect 10 s / call 20 s.

**E5 — Trigger: after the startup catalog refresh, never from a read path.** Public
`EnrichmentRepository.enrichPending(): EnrichmentOutcome` (single-flight: `Mutex` + shared
`Deferred` in `DataScope`, like `refresh()`). It reads the **cached** catalog's artists, computes
pending keys (absent, or NO_MATCH older than 30 days), and runs them. `BluesJamApp` calls it on its
`appScope` after `catalogDone.await()` and logs `outcome.toLogLine()`. `observeCatalog()` never
calls it, nor does any presenter; a test proves collecting the catalog makes zero MusicBrainz
requests. "On song add" is met: a new artist in the Sheet is enriched on the next start.

**E6 — Exposure on `Song`.** `DefaultCatalogRepository.observeCatalog()` adds
`dao.observeArtistEnrichment()` to its `combine` and sets `artistArea` by `artist_key`. Every
other field and the emission order are unchanged. During a run the catalog Flow re-emits once per
artist; today only `BluesJamApp`'s one-shot `first()` collects it, so this is harmless. The jams
view (`jam_song_resolved`) does not read the new table, so jams do not re-emit.

**E7 — Room version 3, destructive.** Add the entity and DAO methods to `CatalogDao` (or a new
`EnrichmentDao`); bump `BluesJamDatabase` to `version = 3`. Per existing policy (no migrations,
destructive fallback), upgrading drops the catalog and jams caches; the next online start refetches
them. Consequence to state in evidence: the first launch after this upgrade, if offline, shows the
"never fetched" error block until a fetch succeeds.

**E8 — Public surface and Koin.** Public in `com.bbbjam.core.data.enrichment`:
`EnrichmentRepository`, `EnrichmentOutcome` (`Completed(matched, noMatch, skipped, pending)` |
`Stopped(failure: DataFailure, matched, noMatch)` | `NothingPending`) with `toLogLine()`
(`enrichment: matched M, no match N, skipped S[, stopped Offline]`, counts only, never artist names),
and `MusicBrainzConfig(userAgent: String)`. Internal: `MusicBrainzClient`, `MusicBrainzDtos`,
`ArtistMatcher`, `ArtistKey`, `RateLimiter`, `DefaultEnrichmentRepository`,
`ArtistEnrichmentEntity`. `dataModule` binds them as `single`, constructor-injected (D-16).
`appModule` binds `single { MusicBrainzConfig(userAgent = "BBBluesJam/${BuildConfig.VERSION_NAME}
( <contact from U2> )") }`. No new library.

## Acceptance Scenarios

1. **First run.** Given a cached catalog with artists A, B, C and an empty enrichment table, when
   `enrichPending()` runs against a server answering A exact (score 100, country AR), B with two
   score-100 homonyms, C with no result, then rows are A MATCHED/AR, B NO_MATCH, C NO_MATCH;
   `observeCatalog()` emits A's songs with `artistArea = "AR"` and the others null.
2. **Rate.** With 5 pending artists, consecutive request starts are ≥ 1 100 ms apart (virtual time).
3. **Offline.** The first request throws `IOException`: the run stops after 1 request, writes
   nothing, returns `Stopped(Offline, 0, 0)`; the catalog emission is unchanged.
4. **503.** The second request answers 503: the first row is kept, the run stops, no third request.
5. **Nothing pending.** All keys MATCHED, or NO_MATCH < 30 days: zero requests, `NothingPending`.
   A NO_MATCH checked 31 days ago is retried.
6. **Read path is clean.** Collecting `observeCatalog()` (fresh or stale cache) makes zero calls to
   the MusicBrainz client.
7. **Catalog replace keeps enrichment.** After `refresh()` replaces the catalog, rows survive and
   songs of an already-enriched artist come back with `artistArea` set; a new artist is pending.
8. **Normalization.** "Memphis La Blusera", " memphis  la blusera ", "Memphis Lá Blusera" share one key.
9. **Request shape.** MockWebServer sees the path `/ws/2/artist`, `fmt=json`, `limit=5`, the
   escaped phrase query and the exact `User-Agent` from `MusicBrainzConfig`.
10. **App unaffected.** With every API unreachable the app starts, all screens behave as today.

## Repository Research

Inspected: `AGENTS.md`, `PROGRESS.md` (head), `feature_list.json` (this entry,
`songsterr-browser-link`, statuses), `CONTEXT.md` (Enrichment), `docs/technical-discovery.md`,
`docs/domain-model.md`, `docs/sheet-schema.md` (enrichment lines), `docs/risks-and-open-questions.md`,
`DESIGN.md` (artwork lines), `bb-blues-jam-design-prompt.md` (song detail),
`bb-blues-jam-bitacora.md` (§2.4–2.5, D-08, D-09, D-10, D-14, D-20),
`docs/specs/catalog-repository-cache.md`, `.claude/skills/architecture/SKILL.md`,
`core/model/.../Song.kt` (has `mbid`, `artistArea`, `deezerTrackId`, `previewUrl`, `artworkUrl`),
`core/data/build.gradle.kts`, the `core/data/src` file list, `CatalogRepository.kt`,
`DefaultCatalogRepository.kt` (head), `DataModule.kt`, `DataScope.kt`, `BluesJamDatabase.kt`
(version 2), `app/.../BluesJamApp.kt`, `gradle/libs.versions.toml` (OkHttp 5.1.0, Room 2.8.4),
`docs/sheet-seed/Catalogo.csv` (artists), `git remote` (`github.com/elagarrigue/bb-blues-jam`).

Findings: nothing outside `BluesJamApp` collects `observeCatalog()`; no screen reads an enrichment
field; Konsist has 17 rules and none names a music-API host; OkHttp, serialization, Room and
coroutines are already in `:core:data`, so no dependency is added.

**Assumptions, not verified live (no request was made while planning):**
- The MusicBrainz artist-search JSON has `artists[]` with `id`, `name`, `score` (int), `country`
  (nullable) — from memory of the documented `ws/2` format. The implementer must capture one live
  answer (a single `curl` with the U2 User-Agent, e.g. `query=artist:"Pappo"`) and commit it as a
  test fixture; if the shape differs, adapt the DTO and note it in Implementation Findings.
- MusicBrainz core data (artists, countries) is CC0 and the API is free for this non-commercial use
  given a meaningful User-Agent and ≤ 1 req/s. This closes the MusicBrainz half of the
  `risks-and-open-questions.md` "terms" item only after the implementer reads the live terms page
  and records the conclusion there; Deezer stays open.

## Expected File Changes

- `core/data/src/main/kotlin/com/bbbjam/core/data/enrichment/`: `EnrichmentRepository.kt`,
  `EnrichmentOutcome.kt`, `MusicBrainzConfig.kt`, `DefaultEnrichmentRepository.kt`,
  `ArtistMatcher.kt`, `ArtistKey.kt`, `RateLimiter.kt` (new).
- `core/data/src/main/kotlin/com/bbbjam/core/data/remote/MusicBrainzClient.kt`,
  `MusicBrainzDtos.kt` (new; base URL injectable for MockWebServer).
- `core/data/src/main/kotlin/com/bbbjam/core/data/cache/ArtistEnrichmentEntity.kt` (new), DAO
  methods, `BluesJamDatabase.kt` (entity, version 3).
- `core/data/.../catalog/DefaultCatalogRepository.kt` (E6), `di/DataModule.kt` (E8).
- `core/data/src/test/...`: `ArtistKeyTest`, `ArtistMatcherTest`, `RateLimiterTest`,
  `MusicBrainzClientTest` (MockWebServer), `DefaultEnrichmentRepositoryTest`, extend
  `DefaultCatalogRepositoryTest` (scenarios 6, 7) and `DataModuleTest`; fixtures in
  `core/data/src/test/resources/musicbrainz/` (one live capture + hand-made variants, labelled).
- `app/src/main/java/com/bbbjam/BluesJamApp.kt` (call and log), `di/AppModule.kt` (config).
- `konsist-test/.../ModuleIsolationTest.kt`: the 18th rule, only if U4 is approved.

## Visual Design Impact

None. No screen, token or copy changes. Say so in the evidence; do not invent a UI to show it.

## Durable Documentation Impact

- `docs/domain-model.md`: `artistArea` is the MusicBrainz artist country code (E2), looked up per
  artist (E1).
- `docs/technical-discovery.md` Integrations: MusicBrainz wired (endpoint, User-Agent, rate,
  trigger, matching); Deezer and Last.fm not wired, with the reasons.
- `.claude/skills/architecture/SKILL.md`: `:core:data` `enrichment/` package, Room version 3,
  and the U4 rule if approved.
- `docs/risks-and-open-questions.md`: the terms item (MusicBrainz half) and the MusicBrainz risk row.
- `CONTEXT.md`, `AGENTS.md`, `DESIGN.md`, `docs/sheet-schema.md`: not needed (no term, rule, UI or
  Sheet change).
- `PROGRESS.md`, `feature_list.json`: evidence, and the reworded entry (above).

## Implementation Tasks

1. `ArtistKey` + test; `ArtistMatcher` + test (exact, homonyms, score 99, no result, no country).
2. `RateLimiter` + virtual-time test (gaps ≥ 1 100 ms; first request not delayed).
3. Capture one live MusicBrainz answer (one request, U2 User-Agent); DTOs; `MusicBrainzClient` +
   MockWebServer test (scenario 9, 503, non-2xx, `IOException`, bad JSON).
4. Entity, DAO, DB version 3; DAO test on the bundled driver.
5. `DefaultEnrichmentRepository` (single-flight, pending rule, stop rules) + scenarios 1–5.
6. `observeCatalog()` join (E6) + scenarios 6–8.
7. Koin bindings, `DataModuleTest`; `:app` config and the startup call.
8. If U4: the Konsist rule and its failing case. `./gradlew ktlintFormat`, `CI=true ./init.sh`,
   device check, docs.

## Verification Plan

- `CI=true ./init.sh` exits 0 with `konsist`, `detekt`, `ktlint` all `wired`; no baseline,
  suppression or `ignoreFailures`. Konsist 17/17 (18/18 with U4). No test reaches the network.
- Scenarios 1–10 are JVM tests (`runTest`, Turbine, MockWebServer, in-memory Room) except 10.
- **U4 failing case** (if approved): put the literal `musicbrainz.org` in a `:feature:next-jam`
  main file, show the rule failing, revert.
- **Dependency check:** `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` shows
  no new artifact versus HEAD.
- **Device (Pixel 5, manual, ~3 min).** No Sheet edits; no TalkBack or accessibility services.
  1. Install debug online; launch; logcat tag `BluesJam` shows `catalog refresh: …` then
     `enrichment: matched M, no match N, skipped 0` with M + N = distinct artists, and its timing
     consistent with ≥ 1.1 s per request (log the run's duration in the line).
  2. Kill and relaunch online: `enrichment: nothing pending`.
  3. Airplane mode, kill, launch: the app shows the cached lists exactly as before; restore
     airplane mode off. (The enrichment line may say `nothing pending`; to see `stopped Offline`,
     clear app data first, then launch offline — the cached lists are then gone until online, which
     is E7's expected state. Restore and relaunch online.)
  4. Empty crash buffer (`adb logcat -b crash -d`).

## Evidence To Capture

`init.sh` tail and test counts; the U4 failing-case output; the live fixture's capture command (no
secrets); the dependency diff; the device log lines with M, N and the run duration; a note that
nothing visible changed.

## Validator Checklist

- No call to MusicBrainz outside `:core:data` `enrichment/`/`remote/`; none from `observeCatalog`,
  any presenter or composable (scenario 6 test exists and is meaningful).
- Serial requests, ≥ 1 100 ms apart, proven by test; run stops on offline/503.
- User-Agent bound from `:app`, matches U2, has no secret; no API key anywhere in the diff.
- `artistArea` only from an exact, unambiguous match; `Song.mbid`, artwork, preview untouched.
- Enrichment survives a catalog replace; nothing written to the Sheet; key untouched (D-08).
- Constructor injection only; no new library; no UI change; docs updated as listed.

## Risks

- MusicBrainz names differ from the Sheet's (e.g. "Memphis la Blusera" vs a variant): NO_MATCH, so
  the assistant cannot filter that artist by data. Acceptable; aliases are a later refinement.
- The DB bump empties the cache once (E7).
- MusicBrainz may block a vague User-Agent; U2 makes it meaningful.

## User Approvals

Answered by the user on 5 October 2026: **U1 — defer** until after the admin chain or the start of
phase 2. U2–U4 stay open until then.

The questions as asked:

- **U1 — Scope and timing.** *Recommended:* (a) narrow to MusicBrainz artist country only, no
  visible effect, and schedule it after the admin chain or at the start of phase 2. Alternatives:
  (b) add Deezer artwork shown on the song detail — reverses `DESIGN.md`'s "Not shown: artwork",
  needs an image loader (Coil) and a design decision, so it would be a separate UI slice; (c) drop
  the slice and let the assistant work without country data.
- **U2 — User-Agent contact** (MusicBrainz requires one; the repo is public). *Recommended:*
  `BBBluesJam/<versionName> ( https://github.com/elagarrigue/bb-blues-jam )`. Alternative: an email
  address, which would be published in the repo and the APK.
- **U3 — Scheduling and network.** *Recommended:* in-process once per start, any network
  (≈ one small JSON per artist, about 100 at most, once), no WorkManager. Alternative: WorkManager
  with an unmetered constraint (a new dependency in `:core:data`, survives process death).
- **U4 — Gate rule.** *Recommended:* add Konsist rule 18, `music-api-hosts-only-in-core-data`:
  the literals `musicbrainz.org`, `deezer.com`, `audioscrobbler.com` appear only in `:core:data`
  main sources (and `:konsist-test`), turning D-09's "only from background enrichment in
  `:core:data`" into a gate. Alternative: rely on `internal` visibility and review.
