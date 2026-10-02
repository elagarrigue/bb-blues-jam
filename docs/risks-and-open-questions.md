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
- **Sheet cell types.** Settled. The catalog endpoint emits display text, and a raw whole number
  for `songsterr_id` (`apps-script-api.md`). The jams endpoint (`apps-script-jams-read-endpoint`)
  normalizes `fecha`, `hora` and `posicion` to `YYYY-MM-DD`, `HH:MM` and integer text whether
  the Sheet stores them as text or as date, time and number cells, formatting typed dates in the
  spreadsheet's own time zone (`sheet-schema.md`, **Reading cells**). Which form the real Sheet
  uses is still unobserved until the live strict check of that slice.
- **`Jams` row and jam tab mismatch.** The script half is settled (user approval A2 of
  `apps-script-jams-read-endpoint`): a `PUBLICADA` row with no tab is served with
  `setlistError` `missing_tab`, a tab with no `Jams` row is ignored (never read or served), and a
  per-jam error never fails the whole response. The app half is settled by `jams-repository-cache`
  (user approval P3, 2 October 2026): a published jam with a `setlistError` is a jam whose setlist
  is unavailable (`Setlist.Unavailable`), never hidden and never an empty list; the code is in the
  refresh log line. An orphan tab is silent: an admin who
  adds a tab but forgets the `Jams` row sees nothing. Rejected `Jams` rows and dropped setlist rows
  are also only in logcat until an admin surface shows them.
- **Seed as mapper fixtures.** No committed test guards `docs/sheet-seed/` on its own (decision P3
  of the `sheet-schema-definition` spec): the mapper tests use the seed as fixtures instead. Done
  for the catalog by `catalog-repository-cache`: `CatalogMapperTest` maps `Catalogo.csv` and
  `catalog-seed.json` and asserts the same result, and `catalog-edge.json` is a fixture too. Done
  for the jams by `jams-repository-cache`: `JamsMapperTest` maps `Jams.csv` + `2026-07-25.csv` and
  `jams-seed.json` to the same result, and `jams-edge.json` is a fixture too.
- **Time zone and "how long until" text.** The jam date boundary is settled by
  `jams-repository-cache` (user approval P7): "today" is the Buenos Aires date from `JamCalendar`,
  never the device zone or UTC, so a jam stays upcoming through its own night and turns historical
  at 00:00 of the next day. The phrasing is settled by `next-jam-read-only-list` (user approval
  C1, 2 October 2026): calendar days from `JamCalendar.today()`, "Esta noche" on the jam's own date
  for a start at 18:00 or later (still shown after the start time, until 00:00), "Hoy" for an
  earlier start, "Mañana", then "En n días" with no weeks. Still open: a screen left open across
  midnight keeps the old split and phrase until the next emission (no ticker).
- **Venue address.** `DESIGN.md` asks for the venue's name and address; the Sheet has one free-text
  `Jams.lugar`, shown as written (user approval C2 of `next-jam-read-only-list`, 2 October 2026). If
  an address is wanted, the admin types it into `lugar` ("La Macanuda, Moreno 223"); an address
  column would be a schema change and its own slice.
- **Passphrase rotation UX.** When a stale local flag meets a rotated passphrase, the failure should
  read as "your access changed", not as a generic network error.
- **Draft data must not reach unauthenticated clients.** Settled for anonymous reads by
  `apps-script-jams-read-endpoint`: the `jams` route serves a setlist only for a jam whose
  `estado` is exactly `PUBLICADA`, fails closed on any other value, and never even opens a draft's
  tab; a query parameter cannot unlock it. Still open, for `apps-script-write-auth` or an admin
  slice: how the admin reads a draft. That read must use POST with the passphrase in the body,
  never a GET parameter, or the passphrase lands in URLs and logs. Consequence of the rule (user
  approval A1): a past jam left in `BORRADOR` shows no setlist until the admin marks it
  `PUBLICADA`.

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
