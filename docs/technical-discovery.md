# Technical Discovery

Split out from the build brief because the stack, the Sheets backend, and the external-API rate
limits materially shape the solution rather than merely implementing it.

## Product Surface

Native Android phone app, dark theme only, portrait. Kotlin Multiplatform was considered and
rejected: the goal is an Android app, and KMP setup cost roughly a week without contributing to the
result (D-01). No iOS, no tablet layout, no web.

Usage context drives several constraints: the app is opened standing in a dim bar, one-handed, often
on a weak connection. Minimum 48dp touch targets, genuinely high contrast, no horizontal scrolling,
and no hidden gestures without a visible alternative.

## Candidate Stack

- **Kotlin + Jetpack Compose**, Material 3 as a base with a distinct identity.
  Fonts (Barlow Condensed, Chivo) are bundled in the APK, not downloadable fonts, because the app
  is used offline in a bar and a fallback to Roboto would change widths and lose the identity (`docs/specs/design-tokens-theme.md`, Decision 4).
- **Composable presenters instead of ViewModels** (D-02). State lives in the Compose runtime,
  presenters are tested with Molecule without Android, and the `UiModel` stays a plain data object —
  which in phase 2 lets it be exposed as assistant context with no adapter layer.
- **Clean Architecture, multi-module, strictly isolated** (D-03). No feature module depends on
  another; shared contracts live in `:core:*` and binding happens in `:app`. Enforced with Konsist
  rather than documented and hoped for. This is the same constraint that later proves the assistant
  did not reach into feature modules.
- **Koin** for dependency injection (D-16). Constructor injection only; each module exposes its own
  Koin module and `:app` starts Koin with all of them. Presenters are `factory`, repositories
  `single`. Chosen because it needs no annotation processing and matches the reference presenter
  articles, which already inject presenters with `koinInject()`.
- **Admin is a state, not a module** (D-15). Each presenter reads the admin flag and adds admin
  controls to its own `UiModel`; there is no `:feature:admin`.
- **Room** for local caching of the catalog and enrichment data.
- **DataStore** for the admin flag.
- **Gradle** with a `check` task covering tests, Konsist, detekt, and ktlint.

Two corrections were made to the reference composable-presenter pattern: `hashCode` derived from
`handle` while `equals` compared `key`, which breaks the contract, and the `invoke` operator was
used in examples without being declared. The corrected pattern, with a full example, lives in
`.claude/skills/architecture/references/presenter-pattern.md`.

## Data and Storage

Google Sheets as the backend, reached through Apps Script (D-04). Not a conventional choice, and
deliberate: the admin already maintains the repertoire in a spreadsheet, and a spreadsheet is a
better catalog editor than any screen this project would build.

The design removes distributed-conflict handling rather than solving it, by giving every entity a
single authority:

| Entity | Authority | Direction |
|---|---|---|
| Song catalog | Sheet | Sheet → app, read-only |
| Past jams | Sheet | Sheet → app, read-only |
| Upcoming setlist | App | app → Sheet via Apps Script |
| Published status | App | app → Sheet |

Local Room cache backs offline reads; the app shows last-known data with a staleness indicator
rather than an error.

The Sheet's schema is defined in `sheet-schema.md`: a `Catalogo` tab with stable song ids, a `Jams`
index, one tab per jam date referencing catalog ids with one column per slot, and a `Config` tab
for the passphrase. What remains open is how much real repertoire is loaded, with keys and tags,
because the assistant's quality in week 5 depends directly on it.

## Integrations

Ten music APIs were researched with NotebookLM and verified against live documentation. Three
survived, all optional (D-09):

| API | Used for | Constraint |
|---|---|---|
| MusicBrainz | Canonical identity, artist area | 1 req/s — hard architectural constraint |
| Deezer | Artwork, 30s preview | Public catalog endpoints need no credentials |
| Last.fm | Similar artists | Contact required for research use |

Rejected: Spotify, Genius, Audius, IMSLP, TheAudioDB, Discogs. Two findings from live verification
changed decisions: Spotify deprecated `audio-features`, `recommendations`, and `related-artists` for
new apps and since February 2026 caps Development Mode at 5 Premium users; Deezer exposes public
catalog endpoints without credentials despite its portal implying otherwise.

**The conclusion that mattered most: no API provides reliable key or tempo** — precisely the central
datum of a jam. The key is set by the admin (D-08), and the consequence is that the MVP works with
no external API reachable at all.

MusicBrainz's 1 req/s limit means enrichment runs in the background when a song is added and is
cached in Room. Never while rendering a list.

Songsterr has no official API, so it is optional per song: the `songsterrId` is stored from phase 1
and the detail screen opens the tab in the browser (D-10).

## Authentication and Authorization

A shared passphrase stored in the Sheet, validated through Apps Script, with a flag in DataStore
(D-11). No Firebase Auth, no OAuth, no accounts. Details and edge cases are in
`user-and-access-model.md`; the one technical point that belongs here is that **Apps Script must
validate the passphrase on every write**, because the endpoint is reachable directly and the local
flag governs only which controls are drawn.

## Deployment and Operations

No server to operate: the backend is a Sheet plus an Apps Script deployment. Distribution is an open
question — a signed APK shared directly is likely sufficient for 20-60 people who see one another
monthly, and Play Store distribution has not been decided.

Operational ownership is the admin. Recovery from a bad state is editing the Sheet, which is a real
advantage of this backend: the fallback is a spreadsheet anyone can fix.

The Apps Script source lives in `backend/apps-script/` (not a Gradle module) and is deployed by
copy-paste into a script bound to the Sheet (`backend/apps-script/README.md`). The web app executes
as the Sheet owner with anonymous access (D-11: the app has no Google sign-in), so every call counts
against the owner's quotas; the binding one is 30 simultaneous executions per user
(`docs/apps-script-api.md`, **Quotas**). Later code changes are deployed as a new version of the
same deployment, which keeps the `/exec` URL; a new deployment would change it. The URL is never
committed; it lives in the git-ignored `local.properties`. Read latency, measured 1 October 2026
on the live catalog of 100 songs: ten warm calls min 2.10 s, median 2.50 s, max 4.51 s; first
observed call 2.96 s (not proven cold); an independent recheck saw 2.2–3.3 s warm and 5.16 s on a
first call after an unknown idle period. A true cold call after 30+ idle minutes is still
unmeasured. This settles the design: reads are cache-first with background refresh, and writes
need optimistic state — the input for `admin-add-song-to-setlist`.

The `jams` route (`apps-script-jams-read-endpoint`) reads the `Jams` tab plus one tab per
`PUBLICADA` jam with a unique ISO date, so a call costs one `getSheetByName` and one
`getDataRange` (display and raw values) per published jam on top of the `Jams` tab, and grows by
about 12 tabs a year. Its latency is not measured yet: it is live check L5 of that slice, recorded
here once the user has redeployed.

## Testing and Verification

- **Presenter tests with Molecule**, no Android instrumentation required (a direct benefit of D-02).
  Wired: Molecule 2.2.0, Turbine 1.2.1 and kotlinx-coroutines-test 1.10.2 as `testImplementation`
  in `:core:ui` (`SamplePresenterTest`, `EventHandlerTest`), run on the JVM by `./gradlew check`.
  Presenter modules set `unitTests.isReturnDefaultValues = true`, because the Android Compose
  runtime calls `android.os.Trace`; no Robolectric.
- **Konsist** for module isolation. This is evidence, not just hygiene: its output is part of the
  course deliverable. Wired: Konsist 0.17.3 in the test-only module `:konsist-test`
  (`ModuleIsolationTest`, 8 tests), run by `./gradlew check`.
- **detekt and ktlint** for static analysis and formatting. Wired: detekt 2.0.0-alpha.6 (no stable
  detekt runs on the Java 25 Gradle daemon) and ktlint 1.8.0 through ktlint-gradle 14.2.0, applied
  to every Kotlin module by the root `build.gradle.kts` and run by `./gradlew check`.
- **Apps Script tests on Node** (`node --test backend/apps-script/test/*.test.js`, built-in
  `node:test`, no `package.json`). They cover cell normalization, the catalog builder against the
  committed samples in `docs/api-samples/`, the router and the never-open-`Config` guarantee. They
  are **outside `init.sh`** (user decision, 1 October 2026), so the gate does not depend on Node;
  revisit at `apps-script-write-auth`. A live deployment is checked with
  `backend/apps-script/tools/check-response.js`.
- `init.sh` wraps the Gradle gate — `./gradlew build` and `./gradlew check` — and is run by
  `feature-flow` on every validation, so it must be fast and non-blocking.

## Observability

Deliberately minimal. No analytics or crash reporting is planned for the MVP: the user base is
small, co-located, and reachable in person, which makes direct reports faster than telemetry. The
one thing worth logging locally is Apps Script write failures, since a silent failed publish is the
worst outcome the app can produce — a musician reading a setlist the admin thinks they changed.

## Constraints

- **Timeline.** The course runs 14 September to 23 October 2026. The assistant lands in week 5, so
  phase 1 must close before it.
- **Apps Script latency** on writes is expected to be noticeable. Mitigation: optimistic state in
  the presenter with later confirmation.
- **MusicBrainz 1 req/s**, as above.
- **Scope is large for the timeframe** — two roles, a backend, published state, and a cache. The
  recorded mitigation is that Songsterr and API enrichment are both removable without touching the
  MVP.
- **UI copy in Rioplatense Spanish; code, commits, and technical docs in English** (D-12).
