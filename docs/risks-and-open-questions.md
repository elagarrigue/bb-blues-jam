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
with a dot (Stitch pulses it; the app keeps it static, see below); a filled slot reads
`Gtr: Tincho` in muted text with a check glyph.

This also closes the accessibility concern, with one correction made on 2 October 2026
(`instrument-strip-component`): the separating signals are the **glyph** (dot versus check) and the
**wording** (`LIBRE` versus a name). The fills differ in hue only and come out almost the same grey
in greyscale, so fill is not a luminance-independent signal. Spec is in `../DESIGN.md`.

Settled in the same slice: the dot is **static**, not pulsing (an infinite animation per open chip
costs battery all night and conflicts with WCAG 2.2.2; the pulse carries no information); no
instrument glyph beside the chip text (width); extra participants ("Otros") are shown after the
slot chips in a distinct style (no fill, muted text, a leading `+`), never as slots.

Risk, not solved: **amber saturation**. A fresh setlist has every slot open, so every row shows
seven amber chips over about three lines; "if everything glows, nothing stands out". Real nights
fill slots, but this is how a newly published list will look. The user saw it on the Pixel 5 on
2 October 2026 and chose to keep it as is "for now"; revisit if it reads as noise at a real jam.

### Expanded song row (`song-row-expansion`, 3 October 2026)

Settled with the user before implementation: the panel replaces the strip when a row is expanded;
open slots look available but are not interactive for musicians; lines are compact (no 48dp
minimum); a hint line follows the open slots. Spec in `../DESIGN.md`.

Known gap, accepted (T1 declined by the user): **no Compose semantics test harness**. There is no
Robolectric or Compose UI test in the build, so nothing automatic checks the drawn semantics: the
header's state description ("expandido" / "contraído") and click label, a panel line or strip chip
losing its content description, a panel line becoming clickable, or the strip still being drawn
while expanded. The gate covers what reaches the `UiModel` (JVM tests on the strings, order and
state); the drawn tree is checked by hand on the Pixel 5 with `uiautomator dump`, which shows
content descriptions and clickability but not every semantics property. Revisit if a regression
slips through, or when a later slice needs a composable test anyway.

Risks, not solved: an all-open song's panel is seven amber lines under one heading (the amber
saturation risk again, now per expanded row); expanding a row near the bottom grows below the fold
and the user scrolls by hand (no auto-scroll, by design); expansion survives rotation
(`rememberSaveable`) but not the switch between the temporary tabs until `bottom-navigation` owns
per-tab state; Compose honouring the system "remove animations" setting is assumed, not verified.

### Instrument filter (`instrument-filter-chips`, 4 October 2026)

Settled with the user before implementation: multi-select with OR semantics (checkbox chips,
`Todos` clears), the copy with the heading `Filtrá por cupo libre`, zero-count chips tappable, the
selection not tied to the jam date, and the plain no-results block shipped here rather than in
`list-states`. Spec in `../DESIGN.md` "Filter chip".

Risks, not solved: the bar takes 2–3 rows of 48dp chips (about 150–200dp) on a 360dp screen or a
large font, pushing the first song down; it is not sticky, so after scrolling the active filter and
the count line are off screen; the selected chip adds one more amber element beside the open-slot
amber of the shown rows; the selection is lost on relaunch and on the temporary tab switch (until
`bottom-navigation` owns per-tab state). Unverified (no TalkBack run, T1 declined): the live-region
announcements of the count line and the no-results message, and TalkBack's checkbox wording.

### List states (`list-states`, 4 October 2026)

Settled with the user before implementation: the copy (C1), the Konsist rule
`no-dp-literal-outside-core-ui` (K1), and catching `SQLException` in the jams read flow (S1).
Spec in `../DESIGN.md` "Required States".

Risks, not solved: the staleness notice's age is computed when the data changes, so a screen left
open for an hour keeps "hace 2 minutos"; data older than 30 minutes with no failure draws no notice
and refreshes only when the flow is collected again (relaunch, tab switch), so nothing tells the
musician in between; the **catalog** read Flow still throws on a Room read exception (only the jams
flow is caught; no screen reads the catalog yet); the previews (`NextJamStatesPreview.kt` and the
`:core:ui` state previews) compile in the gate but are not rendered by it. Unverified (no TalkBack
run, T1 declined): the error block's and notice's TalkBack wording and the `Actualizando…` live
region; on the Pixel 5, `Actualizando…` was too brief to capture offline (the refresh fails at
once).

### Song detail (`song-detail-screen`, 4 October 2026)

Settled with the user before implementation: Navigation Compose now (N1, 2.9.8 in `:app` only),
`keyDisplay` 96sp (K1), grouped by instrument (G1), the copy (C1), string routes (R1) and the
Konsist rule `navigation-only-in-app` (K2).

Risks, not solved: the detail draws **no staleness notice**, so a musician who opens it offline
sees no age (the list did); the NavHost's behaviour (back stack, state restored on return, the
double-tap guard, rotation, process death) has **no JVM test** (no Robolectric, T1 declined) and is
checked only on the device, and the user-flow steps on the device need a temporary upcoming jam in
the Sheet; a typo in a route argument name inside `AppNavHost` is a runtime error that only
`AppRoutesTest` and the device would catch (R1's cost); the previews (`SongDetailScreen.kt`,
`InstrumentGroups.kt`, `BackButton.kt`) compile in the gate but are not rendered by it; 2.9.8 ends
the 2.9 line, so staying on the pinned BOM blocks navigation fixes; switching to Info still loses
Próxima jam's state (the two temporary tabs are one destination until `bottom-navigation`); a 96sp
key at 200% font scale is assumed to fit 360dp (three condensed characters at most), not yet seen.
Observed once: a gate run failed 15 of the 17 Konsist tests with
`AssertionError: rootDir must be verified to be directory beforehand` (Konsist's project scan); the
suite passed alone and on the next full gate run. Not reproduced; recorded in case it recurs.

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
