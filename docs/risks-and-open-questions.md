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
the 2.9 line, so staying on the pinned BOM blocks navigation fixes; switching to Info lost
Próxima jam's state until `bottom-navigation` (resolved there, per-tab routes); a 96sp
key at 200% font scale is assumed to fit 360dp (three condensed characters at most), not yet seen.
Observed once: a gate run failed 15 of the 17 Konsist tests with
`AssertionError: rootDir must be verified to be directory beforehand` (Konsist's project scan); the
suite passed alone and on the next full gate run. Not reproduced; recorded in case it recurs.

### Past jam detail (`past-jam-detail`, 5 October 2026)

The song list only, per the user ("para las jams pasadas no importa quién tocó, solo la lista de
temas"). Risks, not solved: like the song detail it draws **no staleness notice**, so a musician who
opens a past jam offline sees cached data with no age (Anteriores shows the notice above the rows);
`PastJamsCopy` repeats two strings of `NextJamCopy` (the key description and the dropped-rows note),
so a wording change must touch both; the outer-host route `pastJam/{jamDate}`, its back stack and the
double-tap guard have no JVM test (T1) and are checked only on the device.

### Admin login (`admin-passphrase-login`, 5 October 2026)

Settled with the user (A1–A7): a server-side check in a new `Post.js` (`checkPassphrase`), the
verified passphrase stored in DataStore and excluded from backups, no expiry, logout on Info, a
full-screen login route. Risks, not solved:

- **Rate limit on passphrase guesses, with a lockout cost** (`apps-script-write-auth`, user
  approval W2). A global counter in CacheService allows 10 failed guesses per fixed 10-minute
  window, about 1,440 a day; after that every action answers `rate_limited`, the right passphrase
  included, until the window ends. The trade-off: anyone with the `/exec` URL can lock the admins
  out for up to 10 minutes at a time, repeatedly. A cache failure fails open (the passphrase check
  still runs), and the increment is not atomic, so simultaneous guesses may count once.
- **The login UiModel carries the typed passphrase** (`AdminLoginUiModel.passphrase`, so the screen
  stays a pure renderer). Its `toString` hides it, but the phase 2 assistant context, which exposes
  UiModels as-is, must exclude it.
- The stored passphrase is plaintext in app-private storage: a rooted device or a debuggable build
  (`run-as`) can read it.
- A device logged in before a rotation keeps showing admin mode, **after** its first rejected write
  too (user decision W3, `apps-script-write-auth`): every write answers `AccessRefused` and nothing
  logs it out. Recovery is Info → "Salir del modo admin" → "Entrar como admin" with the new
  passphrase; the first mutation slice's error copy must say so.
- **CacheService and LockService scopes** (`apps-script-write-auth`): assumed to need no OAuth
  scope beyond `spreadsheets.currentonly`. Status: checked live (6 October 2026, checks L4 and
  L6), no new authorization asked. L4: a wrong `checkPassphrase` answered `invalid_passphrase`, not
  `internal_error`, so `doPost` obtains the script cache and lock with no extra scope. L6: a valid
  `checkWriteAccess` answered `ok`, so `tryLock`, `insertSheet`, the A1 write and read-back and
  `deleteSheet` work under the manifest. Still not provable from outside: that `cache.get`/`put`
  succeed, because the guard fails open and a failing cache would answer the same.
- The login route and its back stack have no JVM test (T1) and are checked only on the device.
- 5 October 2026: the user deployed `Post.js` as a **new deployment**, so the `/exec` URL changed
  (the README asks for **Manage deployments → New version**). `local.properties` was updated; any
  other installed build still calls the old URL, which has no `doPost`, and its login answers
  "No se pudo verificar…".

### Bottom navigation (`bottom-navigation`, 5 October 2026)

Settled with the user: icons from `material-icons-core` (I1), a 150 ms tab crossfade and a 250 ms
detail slide (M1), and **deeplinks deferred** (D1).

Open question: **the deeplink scheme.** No destination declares `deepLinks` and the manifest has
no intent filter. D-13 is met by repository functions registered in the `:app` action registry; a
deeplink is the alternative, not a requirement. An exported URI entry point parses untrusted input
and needs its own validation design, so it is decided with `action-contract-registry` (or a slice
of its own).

Risks, not solved: the two nested NavHosts (per-tab state, back, process death) have no JVM test
(T1 declined) and are checked only on the device; Anteriores' scroll restoration was not observable
on the device (the Sheet has one past jam, nothing to scroll); re-entering Próxima jam
re-subscribes its presenter (non-saveable `remember` state such as the retry counter resets), and a
list whose UiModel branch changes while away starts at the top; whether Compose honours the system
"remove animations" setting was not verified.

## Implementation-Time Questions

- **Offline mutations — decided** (`admin-add-song-to-setlist`, 6 October 2026): blocked, with a
  specific message. An add made offline fails at once (`WriteOutcome.Offline`) and the failure card
  says `No hay conexión. Probá de nuevo cuando tengas internet.` No queue, no replay.
- **Add-song risks** (`admin-add-song-to-setlist`, as built):
  - *Refresh race*: a refresh whose read began before an add committed and stores after the local
    insert hides the new row until the next refresh. The Sheet is correct.
  - Pending and failed adds live in memory: a failure while the app is dying is silent; a write that
    reached the server shows on the next refresh.
  - Draft songs sit in the Room cache of a passphrase-holding device and stay after logout until the
    next refresh; they are never drawn without the flag (`setlistForMusicians()`).
  - After a passphrase rotation, a device that still stores the old one spends **one** failed guess
    per process start (its first refresh is the admin read, refused, then remembered in memory and
    the GET is used). Accepted, not fixed (review note, 6 October 2026): it costs at most one of
    the 10 guesses per 10 minutes per cold start of a stale admin device (one or two devices), the
    admin is told by the first refused add to log out and in, and persisting the refusal would mean
    storing something derived from the passphrase, which W3 and the store design avoid.
  - `addSong` writes `posicion` as plain text (`@`); the read path already accepts it.
  - Two production deploy checks exist (`checkWriteAccess`, `checkSetlistWrite`), both guarded and
    self-cleaning, neither listed by a read.
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
  read as "your access changed", not as a generic network error. Since `apps-script-write-auth` the
  client distinguishes it (`WriteOutcome.AccessRefused`), and the device stays in admin mode (W3),
  so the copy must also tell the admin to log out and in again from Info; it comes with the first
  mutation slice.
- **Draft data must not reach unauthenticated clients.** Settled for anonymous reads by
  `apps-script-jams-read-endpoint`: the `jams` route serves a setlist only for a jam whose
  `estado` is exactly `PUBLICADA`, fails closed on any other value, and never even opens a draft's
  tab; a query parameter cannot unlock it. **The admin's read is settled** by
  `admin-add-song-to-setlist`: the guarded POST action `readJams` (passphrase in the body, never a
  URL) adds the songs of current and future `BORRADOR` jams, and with a passphrase stored the jams
  refresh **is** that read (`apps-script-api.md`). Consequence of the rule (user approval A1): a
  past jam left in `BORRADOR` shows no setlist until the admin marks it `PUBLICADA`; `readJams`
  does not release it either.

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
      `technical-discovery.md`); a true cold-start figure is still open. Write latency: one
      sample, 6 October 2026: `checkWriteAccess` (guard, lock, create tab, write and read A1,
      delete tab) took 4.94 s end to end (`apps-script-write-auth` L6). A single call, not a
      median, and heavier than one cell edit. `admin-add-song-to-setlist` live checks (6 October
      2026, same deployment): `checkSetlistWrite` (guard, lock, create a jam tab, append a row,
      read it back, delete) 4.91, 4.43 and 5.09 s; `readJams` 3.78, 3.62, 3.69 s; the
      non-mutating `addSong` probes 2.4–5.1 s (the deeper the check, the slower). An add is
      therefore about 5 s, which the pending row covers.
- [ ] Confirm the full mutation list for the action contract before slicing features.
- [x] Instrument strip resolved as labelled chips by the Stitch export; no icon set needed.
- [ ] Confirm MusicBrainz and Deezer terms permit this use, and record the conclusion.
- [x] Reconcile the DESIGN.md palette — done 19 September 2026 against the Stitch export.
      Background, surfaces, border and amber now carry real values; `textMuted`, `slotFilled`,
      `archive` and `error` remain derived.

- **Server and client "today" can disagree** (admin-add-song validation, 6 October 2026): Apps
  Script decides whether a jam is past in the spreadsheet's time zone, the app in Buenos Aires
  (`JamCalendar`). They agree only while the Sheet's time zone is Buenos Aires; keep it so.
