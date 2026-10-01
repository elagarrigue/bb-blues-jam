# Risks and Open Questions

Questions still open after discovery, sorted by when they must be answered. Settled matters live in
the other discovery documents; the 20 decisions and their reasoning are in
`../bb-blues-jam-bitacora.md`.

## Blocking Next Phase

### The Sheet schema — resolved, repertoire still thin

Resolved 28 September 2026 in `sheet-schema.md`: a `Catalogo` tab with stable song ids, a `Jams`
index, one tab per jam date referencing catalog ids with one column per slot, and a `Config` tab
for the passphrase. Seed data for the 25 July 2026 jam is in `sheet-seed/`.

Imported 29–30 September 2026, by the user's own report (no agent can read the private Sheet): the
four seed CSVs were imported, the tabs reviewed by hand, the `Otros` column added to the jam tab,
and more songs added to `Catalogo`. The real Sheet is now the authority and has already diverged
from the seed, which is a one-time import file. The user did not mention the `Config` tab.

What remains open is the second deadline: the assistant's usefulness in week 5 depends on how much
real repertoire is loaded, with keys (tempo, tags and difficulty are set aside for now, D-20). The seed has thirteen songs and no
tags; the real catalog is growing past it. Loading should run in parallel with development.

### The Apps Script action surface is not specified

D-13 requires every mutation to exist as a repository function or deeplink from phase 1, which means
the list of mutations must be enumerated before features are sliced — not discovered feature by
feature. From the domain model, the set is: add song to setlist, remove song, set key, adjust
lineup, assign musician to slot, clear slot, reorder songs, publish — and, since D-18, add and
remove an extra participant ("Otros"). Each needs an Apps Script
endpoint and a repository function. Confirm this list is complete before writing `feature_list.json`.

### Instrument strip: chips, not icons — resolved

Resolved 19 September 2026 by the Stitch export. The strip is labelled chips rather than ~16dp
icons, so no icon set needs sourcing. An open slot reads `GTR: LIBRE` in amber on a tinted fill
with a pulsing dot; a filled slot reads `Gtr: Tincho` in muted text with a check glyph.

This also closes the accessibility concern: fill, glyph and wording all distinguish the two states,
so neither depends on brightness. Spec is in `../DESIGN.md`.

Remaining minor question: whether to add an instrument glyph beside the chip text, which would cost
width the strip cannot spare.

## Implementation-Time Questions

- **Offline mutations.** Reads are cached, but what happens when an admin edits with no connection?
  Options: block mutations offline with a clear message (simplest, defensible given the admin
  usually builds the list at home), or queue and replay (more work, and risks silent divergence).
  Recommendation: block in the MVP, and make the message specific.
- **Failed writes after optimistic update.** The mitigation for Apps Script latency is optimistic
  presenter state, which makes rollback the real question. A failed publish is the worst case in the
  product: the admin believes the list is live and musicians see something stale. Publish failure in
  particular needs an unmissable, non-transient error.
- **A song deleted from the catalog while scheduled** — resolved by the schema: each jam tab keeps
  plain-text copies of title and artist, used as a fallback when the song id is no longer in
  `Catalogo`.
- **Musician name suggestions** — resolved by the schema: derived from the names in past jam tabs,
  with no separate musicians list.
- **Sheet cell types.** Settled for the catalog: `apps-script-read-endpoint` emits display text,
  and a raw whole number for `songsterr_id` (`apps-script-api.md`). Still open for jams (for
  `apps-script-jams-read-endpoint`): it is unknown whether the real Sheet stores `fecha`, `hora`
  and `posicion` as text or as date, time and number values. That endpoint must normalize them to
  `YYYY-MM-DD`, `HH:MM` and an integer either way (`sheet-schema.md`, **Reading cells**); check
  the real cells when it is written.
- **`Jams` row and jam tab mismatch** (for `catalog-repository-cache`). A `Jams` row with no tab, or
  a date-named tab with no `Jams` row, is invalid by the schema, but what the app does about it —
  skip the jam, show it without a setlist, or fail the read — is not decided. Decide it in that
  slice; `sheet-schema.md` lists it under **Mapper rules** as open.
- **Seed as mapper fixtures** (for `catalog-repository-cache`). No committed test guards
  `docs/sheet-seed/` (decision P3 of the `sheet-schema-definition` spec): the real Sheet is the
  authority, and a separate seed test would re-implement the mappers before they exist. Instead,
  that slice's mapper tests should use the seed CSVs as fixtures, so the production parser checks
  the seed.
- **Time zone and "how long until" text.** Phrases like "esta noche" depend on the device clock and
  a local definition of the jam date. Single-timezone community, so low risk, but the boundary
  behavior around midnight should be decided rather than emergent.
- **Passphrase rotation UX.** When a stale local flag meets a rotated passphrase, the failure should
  read as "your access changed", not as a generic network error.
- **Draft data must not reach unauthenticated clients.** Hiding the draft setlist in the UI is not
  sufficient; the endpoint should not serve it without a valid passphrase. Still open, for
  `apps-script-jams-read-endpoint`: the catalog endpoint serves no jam data. Any passphrase check
  must use POST, never a GET parameter, or the passphrase lands in URLs and logs.

## Later / Not MVP

- The assistant, in full — phase 2, week 5.
- In-app tablature rendering. `songsterrId` is stored from phase 1; the detail screen opens the
  browser (D-10).
- Musician self-signup (D-05). This is a product non-goal, not a deferral.
- Distribution. A signed APK shared directly is likely sufficient for 20-60 co-located people; Play
  Store distribution has not been decided and does not block the MVP.
- Analytics and crash reporting. Deliberately absent; the user base is reachable in person.
- Multiple concurrent upcoming jams.
- Editing past jams.

## Assumptions

Stated so they can be challenged rather than silently relied upon.

1. **One upcoming jam at a time.** The model assumes a single next jam; two scheduled dates would
   require navigation the MVP does not have.
2. **Musicians are a trusted, co-located group.** This underwrites the shared passphrase, the
   absence of accounts, and last-write-wins.
3. **The admin has connectivity when building the setlist**, typically at home before the jam.
4. **Names are adequate identity.** No disambiguation between two musicians sharing a name.
5. **The Sheet remains a comfortable catalog editor for the admin.** If the repertoire grows past a
   few hundred entries, that assumption may weaken.
6. **Apps Script quotas are sufficient** for this traffic — tens of readers, a couple of writers,
   monthly peaks. Checked against Google's published limits on 1 October 2026 (page updated
   3 September 2026): 6 min per execution, 30 simultaneous executions per user, no daily cap listed
   for web app executions (`apps-script-api.md`, **Quotas**). Every anonymous call runs as the
   owner, so the 30-concurrent limit is the one to watch; still unmeasured under real load.

## Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Scope is large for six weeks: two roles, backend, published state, cache | Phase 1 overruns into week 5, which the assistant needs | Songsterr and API enrichment are both removable without touching the MVP |
| The Sheet catalog stays too small for the assistant to propose anything interesting | The headline demo falls flat | Load real repertoire during weeks 1-2, in parallel with development |
| The assistant proposes songs outside the catalog | Undermines the project's core claim | Validate every suggestion against the catalog before executing any action (D-14) |
| Apps Script write latency | Sluggish admin experience | Optimistic presenter state with later confirmation |
| Songsterr has no official API | Tab links break | Optional per song; the app works with no tabs at all (D-10) |
| MusicBrainz 1 req/s | Blocked rendering if misused | Enrichment runs in background on song add and is cached in Room; never during list rendering (D-09) |
| A silent failed publish | Musicians read a setlist the admin thinks they changed — the worst product outcome | Publish failure must be unmissable; log write failures locally |
| Generated design output contradicts decided product facts | Screens get built to the wrong model — the Stitch export merged harmonica and keyboards into one slot, breaking D-06 | Review generated output against the decisions before using it; divergences logged in `design/README.md` |
| Multi-module setup consumes early weeks | Less time for product | The build skeleton is the first vertical slice, so the cost lands early and visibly |

## Research Tasks

- [x] Define the Sheet schema — `sheet-schema.md`. The seed was imported into the real Sheet and
  reviewed by hand by the user (29–30 September 2026, user's report).
- [ ] Load real repertoire with keys. Tempo, tags and difficulty are not used for now (D-20).
- [ ] Verify Apps Script quotas and typical write latency with a realistic payload. Quotas checked
      (assumption 6). Read latency measured (warm median about 2.5 s; see
      `technical-discovery.md`); a true cold-start figure is still open. Write latency: still
      open, for the first write slice.
- [ ] Confirm the full mutation list for the action contract before slicing features.
- [x] Instrument strip resolved as labelled chips by the Stitch export; no icon set needed.
- [ ] Confirm MusicBrainz and Deezer terms permit this use, and record the conclusion.
- [x] Reconcile the DESIGN.md palette — done 19 September 2026 against the Stitch export.
      Background, surfaces, border and amber now carry real values; `textMuted`, `slotFilled`,
      `archive` and `error` remain derived.
