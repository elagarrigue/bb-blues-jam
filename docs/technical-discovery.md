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
- **Composable presenters instead of ViewModels** (D-02). State lives in the Compose runtime,
  presenters are tested with Molecule without Android, and the `UiModel` stays a plain data object —
  which in phase 2 lets it be exposed as assistant context with no adapter layer.
- **Clean Architecture, multi-module, strictly isolated** (D-03). No feature module depends on
  another; shared contracts live in `:core:*` and binding happens in `:app`. Enforced with Konsist
  rather than documented and hoped for. This is the same constraint that later proves the assistant
  did not reach into feature modules.
- **Room** for local caching of the catalog and enrichment data.
- **DataStore** for the admin flag.
- **Gradle** with a `check` task covering tests, Konsist, detekt, and ktlint.

Two corrections were made to the reference composable-presenter pattern and are recorded in the
project's own documentation: `hashCode` derived from `handle` while `equals` compared `key`, which
breaks the contract, and the `invoke` operator was used in examples without being declared.

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

The Sheet's schema — catalog, jams, and assignment tabs — is not yet defined. It is the top blocking
item in `risks-and-open-questions.md`, because the assistant's quality in week 5 depends directly on
how much real repertoire is loaded by then.

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

## Testing and Verification

- **Presenter tests with Molecule**, no Android instrumentation required (a direct benefit of D-02).
- **Konsist** for module isolation. This is evidence, not just hygiene: its output is part of the
  course deliverable.
- **detekt and ktlint** for static analysis and formatting.
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
