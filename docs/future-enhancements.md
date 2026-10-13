# Future Enhancements

This document records work intentionally kept outside the active MVP implementation. The
enrichment item is removed from `feature_list.json`; Songsterr remains there as a D-20-blocked
optional slice. The phase 2 assistant remains a later product phase, outside the MVP backlog.

## Enrich artist country in the background

- **Source feature:** `enrichment-background-fetch` (removed from the active feature list on
  9 October 2026 at the user's request).
- **Purpose:** cache MusicBrainz artist country so the phase 2 admin assistant can filter for
  Argentine blues using data. No screen or user-visible behavior changes are currently planned.
- **Preserved spec:** [enrichment-background-fetch.md](future-enhancements/enrichment-background-fetch.md).
- **Open decisions before restarting:** confirm country-only scope versus omitting or separately
  designing artwork; choose a public contact for MusicBrainz's User-Agent; choose startup/network
  scheduling; decide whether to add a Konsist rule restricting music API hosts to `:core:data`.
- **Research:** MusicBrainz documents free non-commercial API use, a meaningful User-Agent, and a
  maximum of one request per second ([API terms and usage](https://musicbrainz.org/doc/MusicBrainz_API),
  [rate limiting](https://musicbrainz.org/doc/MusicBrainz_API/Rate_Limiting)). Deezer is not used by
  the country-only proposal; its terms need review only if artwork or previews are approved.

## Phase 2 admin assistant

- **Status:** phase 2 started 13 October 2026; excluded from the MVP. It composes upcoming setlists from the
  Sheet catalog under thematic constraints and uses the app-owned action contract without changing
  feature modules. See [build brief](build-brief.md), [domain model](domain-model.md), and
  [action contract registry spec](specs/action-contract-registry.md).
- **Prerequisite:** met on 13 October 2026 — see [phase 2 brief](phase-2-brief.md); slices are in
  `feature_list.json`.

## Songsterr browser link

- **Status:** remains in `feature_list.json` as blocked by D-20. The user set `songsterrId` aside;
  restore this optional link only if the user brings it back.
- **Scope if restored:** open the stored tab in the browser; no in-app tab rendering. See
  [song detail spec](specs/song-detail-screen.md).
