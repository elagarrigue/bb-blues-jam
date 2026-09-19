# Risks and Open Questions

Questions still open after discovery, sorted by when they must be answered. Settled matters live in
the other discovery documents; the 14 decisions and their reasoning are in
`../bb-blues-jam-bitacora.md`.

## Blocking Next Phase

### The Sheet schema is not defined

The catalog, jams, and assignment tabs need columns, types, and identifier rules before any
repository code can be written, and before Apps Script endpoints can be shaped. This is the single
largest blocker.

It has a second deadline beyond implementation: the assistant's usefulness in week 5 depends on how
much real repertoire is loaded, with keys and tags. A catalog of twelve songs cannot produce an
interesting themed setlist. Loading should start as early as possible and run in parallel with
development.

### The Apps Script action surface is not specified

D-13 requires every mutation to exist as a repository function or deeplink from phase 1, which means
the list of mutations must be enumerated before features are sliced — not discovered feature by
feature. From the domain model, the set is: add song to setlist, remove song, set key, adjust
lineup, assign musician to slot, clear slot, reorder songs, publish. Each needs an Apps Script
endpoint and a repository function. Confirm this list is complete before writing `feature_list.json`.

### Instrument icons

The instrument strip is the load-bearing component of the main screen and it needs six icons legible
at roughly 16dp. Source or draw them. Blocking for the first UI slice, not for the build skeleton.

## Implementation-Time Questions

- **Offline mutations.** Reads are cached, but what happens when an admin edits with no connection?
  Options: block mutations offline with a clear message (simplest, defensible given the admin
  usually builds the list at home), or queue and replay (more work, and risks silent divergence).
  Recommendation: block in the MVP, and make the message specific.
- **Failed writes after optimistic update.** The mitigation for Apps Script latency is optimistic
  presenter state, which makes rollback the real question. A failed publish is the worst case in the
  product: the admin believes the list is live and musicians see something stale. Publish failure in
  particular needs an unmissable, non-transient error.
- **A song deleted from the catalog while scheduled** in an upcoming jam. The JamSong carries the
  title and artist it needs to render, so the setlist stays readable, but the intended behavior
  should be confirmed rather than left to emerge.
- **Musician name suggestions.** The assign-slot sheet suggests people who played before. Source:
  derived from past jams in the Sheet, or a separate list? Affects the Sheet schema, so it is worth
  answering alongside it.
- **Time zone and "how long until" text.** Phrases like "esta noche" depend on the device clock and
  a local definition of the jam date. Single-timezone community, so low risk, but the boundary
  behavior around midnight should be decided rather than emergent.
- **Passphrase rotation UX.** When a stale local flag meets a rotated passphrase, the failure should
  read as "your access changed", not as a generic network error.
- **Draft data must not reach unauthenticated clients.** Hiding the draft setlist in the UI is not
  sufficient; the endpoint should not serve it without a valid passphrase.

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
   monthly peaks. Not verified against the published limits.

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
| Multi-module setup consumes early weeks | Less time for product | The build skeleton is the first vertical slice, so the cost lands early and visibly |

## Research Tasks

- [ ] Define the Sheet schema: catalog, jams, assignments.
- [ ] Load real repertoire with keys, tags, and difficulty.
- [ ] Verify Apps Script quotas and typical write latency with a realistic payload.
- [ ] Confirm the full mutation list for the action contract before slicing features.
- [ ] Source or draw six instrument icons legible at 16dp.
- [ ] Confirm MusicBrainz and Deezer terms permit this use, and record the conclusion.
- [ ] Reconcile the proposed DESIGN.md palette against the prior mockup if it is recovered.
