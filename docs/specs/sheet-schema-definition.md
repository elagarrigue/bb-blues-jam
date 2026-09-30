# Feature Implementation Spec: Define the Sheet schema and document it

## Source Feature

- `id`: sheet-schema-definition
- `area`: backend
- `depends_on`: domain-model-types (`accepted`)
- `status`: not_started (at planning time, 30 September 2026, HEAD `4acdbed`)
- `source`: `feature_list.json`

**Pending one user decision (U1, below).** Everything else can proceed. Only task 3 waits on U1.

## Goal

`docs/sheet-schema.md` and the accepted types in `:core:model` must describe the same data with no
gaps. Every type and field maps to a Sheet column, or is explicitly app-only, derived, or
enrichment kept out of the Sheet. Every enum value has exactly one Spanish Sheet value. Slot
identity is unambiguous. The committed seed in `docs/sheet-seed/` must conform to the schema, and
the user's hand check of the real Sheet is recorded as the manual evidence. This slice produces
documentation and verification only. Mapper rules are written down for the repository slices, but
not implemented.

## Non-Goals

- No Kotlin, Gradle, `init.sh` or Konsist change. The one exception is the KDoc line in U1, and only
  if the user picks option B.
- No mapper, parser, repository, Apps Script or JSON contract. Those belong to
  `apps-script-read-endpoint` and `catalog-repository-cache`.
- The following are not enforced here: at most one upcoming jam, `id_tema` existing in `Catalogo`,
  a `Jams` row matching its tab. They become documented mapper rules (task 4).
- No committed automated test for the seed (Decision P3).
- No edits to the real Sheet, and no claims about its contents beyond what the user reported.
- No new columns or tabs, and no change to the D-04 authority split.

## Job Story

When I write the read endpoint and the mappers,
I want one schema document that matches the domain types exactly, plus a seed known to conform,
so I never have to guess which column feeds which field or how an enum is spelled.

## Users And Permissions

There is no runtime behavior. `Config.passphrase` stays server-only: the seed's `valor` must remain
empty in the repo.

## Mapping Check (done while planning, against HEAD `4acdbed`)

| Type.field | Sheet source | Status |
|---|---|---|
| `Song.id: SongId` | `Catalogo.id` | OK in substance. The doc's "lowercase slug" is looser than the code regex `[a-z0-9]+(-[a-z0-9]+)*` (**M1**) |
| `Song.title` / `artist` | `titulo` / `artista` | OK |
| `Song.defaultKey: Key` | `tono_default` | OK. The **Keys** text and the regex `[A-G][#b]?m?` agree |
| `Song.tempo: Tempo?` | `tempo` | Values are listed, but the doc gives no value-to-enum mapping (**M2**) |
| `Song.tags` | `etiquetas` | The split, trim and drop-empty rule is undocumented (**M3**) |
| `Song.difficulty: Difficulty?` | `dificultad` | Same gap as M2 |
| `Song.songsterrId: Long?` | `songsterr_id` | Schema OK. `domain-model.md` wrongly calls it fetched enrichment (**M4**) |
| `Song.mbid`, `artistArea`, `deezerTrackId`, `previewUrl`, `artworkUrl` | not in the Sheet | OK. They are enrichment cached in Room (D-09, D-04) |
| `Jam.date: LocalDate` | `Jams.fecha` = the tab name | OK |
| `Jam.startTime: LocalTime` | `Jams.hora` | OK. Sheets may auto-convert the cell to a date or time type (**M7**) |
| `Jam.venue` | `Jams.lugar` | OK |
| `Jam.status: JamStatus` | `Jams.estado` | OK: `BORRADOR`→`DRAFT`, `PUBLICADA`→`PUBLISHED` |
| `Jam.setlist` | the rows of the tab named `fecha` | Derived. The `Jams` table does not say so (**M5**) |
| `Jam.isHistorical(today)` | derived from `fecha` | Derived. The "future `fecha`" sentence contradicts it on the jam's own day (**M6**) |
| `JamSong.position` | `posicion` | OK. The mapper sorts by it, and the code enforces 1..n in order |
| `JamSong.songId` | `id_tema` | OK |
| `JamSong.title` / `artist` | `Catalogo` via `id_tema`, falling back to the tab's `titulo` / `artista` | The doc shows the Field column as `—` although the fields exist (**M8**) |
| `JamSong.key: Key` | `tono` | OK (D-08) |
| `JamSong.lineup: Lineup` | seven slot columns | No header-to-`Instrument` table, and whether the headers are required is left to "see below" (**M9**) |
| `Slot.instrument` / `musicianName` | column header / cell (empty → `null`, `-` → no slot) | Slot identity is ambiguous when only one guitar slot is left (**U1**) |
| `JamSong.extraParticipants` | `Otros` | OK with `ExtraParticipant.init`. The empty-entry rule is undocumented (**M10**) |
| `Instrument` | `Guitarra 1`, `Guitarra 2`→`GUITAR`, `Bajo`→`BASS`, `Batería`→`DRUMS`, `Voz`→`VOCALS`, `Armónica`→`HARMONICA`, `Teclados`→`KEYBOARDS` | Matches `Lineup.DEFAULT_INSTRUMENTS` order. Needs to go into the doc (M9) |
| `Config.clave` / `valor` | no domain type | Server-only. Never returned by a read endpoint |

Mismatches and the required doc change for each:

- **M1** — The **Identifiers** section or the `Catalogo.id` row must give the exact alphabet: ASCII
  `a`–`z` and `0`–`9`, words joined by single hyphens, and no accents, spaces or uppercase. For
  example, `Café Madrid` becomes `cafe-madrid`.
- **M2** — Add the explicit mappings `lento`→`SLOW`, `medio`→`MEDIUM`, `rápido`→`FAST` and
  `fácil`→`EASY`, `media`→`MEDIUM`, `difícil`→`HARD`. Matching follows Decision P1.
- **M3** — `etiquetas`: split on `,`, trim each tag, drop empty ones, and keep the order.
- **M4** — In `docs/domain-model.md`, section **Song**: `songsterrId` is admin-entered in the Sheet
  (D-10) and is not fetched. Only `mbid`, `artistArea`, `deezerTrackId`, `previewUrl` and
  `artworkUrl` are background enrichment.
- **M5** — Add a `setlist` row to the `Jams` table: it comes from the rows of the tab named
  `fecha`.
- **M6** — Replace "At most one jam may have a future `fecha`" with "At most one jam may be
  non-historical, that is, have a `fecha` of today or later". That matches `Jam.isHistorical`.
- **M7** — State that the formats given are the values the read endpoint emits. If Sheets stores
  `fecha`, `hora` or `posicion` as a date, time or number, `apps-script-read-endpoint` normalizes
  them to `YYYY-MM-DD`, `HH:MM` and an integer. Recommend formatting those columns as plain text.
- **M8** — Set the Field column for the jam tab's `titulo` / `artista` to `title` / `artist`
  (fallback only). The prose already explains that `Catalogo` wins.
- **M9** — Add the header-to-`Instrument` table above. State that all seven slot headers are
  required in every jam tab and that their cells are optional (Decision P2).
- **M10** — `Otros`: split on `;` and trim each entry. Empty entries, including the one left by a
  trailing `;`, are ignored (Decision P2). Any other entry must match `Nombre (instrumento)`.
- **M11** — `domain-model.md` still says that the behavior for a song removed from the catalog is
  "an implementation-time question". The risks doc and the schema have resolved it, so point the
  line at `sheet-schema.md`.

### U1 — user decision: which guitar column a lone guitar slot belongs to

`Lineup` keeps its slots in order, and its KDoc says the first and second guitar slots "pair with
`Guitarra 1` and `Guitarra 2`". A `Slot` has no column identity, yet the schema identifies a slot by
(`fecha`, `posicion`, column header). Take a row with `Guitarra 1 = -` and `Guitarra 2 = Pedro`. It
reads as a single guitar slot. A later write — assign, clear or adjust lineup, done by Apps Script
on the upcoming jam — then has to know which column that slot is.

- **A (canonical).** A single guitar slot is always `Guitarra 1`, so `-` may appear only in
  `Guitarra 2`. The doc states it, and the mapper rejects `-` in `Guitarra 1` while `Guitarra 2`
  holds a slot. There is no code change. The cost is that the admin must follow the convention by
  hand.
- **B (ordinal, recommended).** A slot's identity is (`fecha`, `posicion`, instrument, ordinal
  among that instrument's slots). The k-th `GUITAR` slot is the k-th guitar column not holding `-`,
  in header order. Any layout the admin types is valid, and a write resolves the column the same
  way. The price is rewording one KDoc sentence in
  `core/model/src/main/kotlin/com/bbbjam/core/model/Lineup.kt`, which is a comment-only code change.

Until the user answers, the implementer does not edit the **Identifiers** section or `Lineup.kt`.

### Planner decisions (labelled, the user may overrule)

- **P1** — Enum and header matching is exact after trimming surrounding whitespace. It is
  case-sensitive and the accents are required (`rápido`, not `rapido`). An unknown value is rejected
  by the mapper, like a bad key. This follows the existing **Keys** rule: a typo shows up when the
  tab is read. Recommend Sheets data-validation dropdowns on `tempo`, `dificultad` and `estado`, as
  admin advice only.
- **P2** — Missing slot headers make the tab invalid. Empty `Otros` entries are ignored. Every cell
  is trimmed before it is interpreted.
- **P3** — No committed seed test, for three reasons. The real Sheet is now the authority and has
  already diverged from the seed (the user added songs). A test over `docs/sheet-seed/` would guard
  a one-time import file. It would also re-implement the mappers in parallel before they exist, and
  `:konsist-test` is for architecture rules, not data. The durable guard belongs to
  `catalog-repository-cache`: its mapper tests should use the seed CSVs as fixtures, so the
  production parser checks the seed. The implementer records this as a hand-off note in the risks
  doc.

## Mapper Rules To Document (not implemented here)

Collect these in a new `## Mapper rules` section of `docs/sheet-schema.md`, each marked "enforced
by the repository slice":

- at most one non-historical jam;
- every `id_tema` resolves in `Catalogo`, otherwise the tab copy is the fallback;
- `posicion` is unique and 1..n;
- `Catalogo.id` is unique;
- a `Jams` row has a tab and a jam tab has a `Jams` row. What happens on a mismatch is left **open**
  for `catalog-repository-cache` to decide — record it in the risks doc and do not invent it;
- cells are trimmed;
- enum matching follows P1, `Otros` parsing follows M10, and slot identity follows U1;
- empty slot cells in a past jam mean "not recorded" and are never shown as open.

## Repository Research

### Files Inspected

- `feature_list.json` — the entry, its verification items and notes.
- `PROGRESS.md` — current state.
- `AGENTS.md` and `CONTEXT.md` — project rules and domain language.
- `docs/domain-model.md` — where M4 and M11 were found.
- `docs/sheet-schema.md` — the schema itself.
- `docs/risks-and-open-questions.md` — the Sheet schema entry.
- `docs/sheet-seed/*.csv` — the four seed files.
- All 12 files in `core/model/src/main/kotlin/com/bbbjam/core/model/`.
- `docs/specs/domain-model-types.md` — the prior spec.
- The bitácora: D-04, D-08, D-10, D-18 and §6.8.

### Current Gaps

- Nobody in-session can read the real Sheet, which is private and has no connector. Its contents
  are known only from the user's report.
- It is unknown whether the real `fecha` and `hora` cells are text or date/time values (M7).

## Seed Check (run while planning)

Script: `seed_check.py` in the planner's scratchpad. The implementer rewrites it in their own
scratchpad and does not commit it. For every file it checks:

- the file is UTF-8 without a BOM;
- the exact header lists match the schema;
- `SongId` and `Key` match the code regexes;
- `tempo` and `dificultad` are empty or a known value;
- `songsterr_id` is empty or numeric;
- ids are unique and the artist is spelled the same way throughout;
- `fecha` and `hora` have the right format, `estado` is known, and every `Jams` row has a tab (and
  the reverse);
- positions are exactly 1..n;
- every `id_tema` exists in `Catalogo` and its title and artist copy equal the catalog's;
- slot cells are empty, `-` or a name;
- every non-empty `Otros` entry matches `\s*([^;()]*[^;()\s])\s*\(\s*([^;()]*[^;()\s])\s*\)\s*`;
- `Config.passphrase` is present with an empty `valor`.

Result at `4acdbed`: **conforms**, exit 0. `Catalogo` has 13 songs, `Jams` has 1 row, and
`2026-07-25` has 13 rows with positions 1..13. The seed has 0 filled slots, 0 `-` cells and 0
`Otros` entries. The line endings are mixed (LF in `Catalogo.csv`, CRLF elsewhere), which is
harmless.

Because the seed has no names, `-` cells or `Otros` entries, those checks pass vacuously. The
implementer must demonstrate the script **failing** on a mutated scratch copy, never on the
committed seed. The copy must contain a bad key `Bmaj`, a position gap, an unknown `id_tema`, the
`Otros` value `Juan saxo`, and a filled passphrase. Each must be reported, with a non-zero exit.

## Implementation Plan And Tasks

- [ ] 1. Re-run the mapping check against HEAD. Confirm M1–M11 still hold, and note any new
      finding.
- [ ] 2. Edit `docs/sheet-schema.md` for M1–M3, M5–M10 and P1–P2.
- [ ] 3. **Only after U1 is answered:** write slot identity into **Identifiers**. Under option B,
      also reword the `Lineup.kt` KDoc sentence (comment only).
- [ ] 4. Add the **Mapper rules** section.
- [ ] 5. Edit `docs/domain-model.md` for M4 and M11.
- [ ] 6. Update `docs/risks-and-open-questions.md` with three items:
  - the Sheet is imported and the catalog is growing;
  - the M7 cell-type question, handed to `apps-script-read-endpoint`;
  - the open `Jams`/tab mismatch rule and the P3 fixture hand-off, both for
    `catalog-repository-cache`.
- [ ] 7. Run the seed check on the committed seed (expect a pass), then on a mutated copy (expect
      each failure). Keep both outputs.
- [ ] 8. Run `CI=true ./init.sh`. It must exit 0 and still print `konsist: wired`, `detekt: wired`
      and `ktlint: wired`. This is required even for a docs-only change, and required twice over if
      `Lineup.kt` was touched.
- [ ] 9. Record the evidence in `feature_list.json` and `PROGRESS.md`, then set the status to
      `passing`.

## Expected File Changes

- `docs/sheet-schema.md` — modify.
- `docs/domain-model.md` — modify.
- `docs/risks-and-open-questions.md` — modify.
- `core/model/src/main/kotlin/com/bbbjam/core/model/Lineup.kt` — only under U1 = B, and KDoc only.
- `feature_list.json` and `PROGRESS.md` — evidence and status.
- `docs/sheet-seed/*` — **unchanged**. If the check fails at HEAD, report it; do not silently fix it.

## Visual Design Impact

No UI is involved. `DESIGN.md` does not apply.

## Durable Documentation Impact

- `ARCHITECTURE.md` and `CONSTRAINTS.md` do not exist and are not needed: no boundary changes.
- `AGENTS.md` is not needed: no workflow change.
- The schema, domain model and risks docs change as listed above.
- A bitácora entry is optional and is the orchestrator's call.

## Evidence To Capture

1. **Manual, attributed to the user (29–30 September 2026).** Record the user's own report, not an
   in-session observation:
   - the four seed CSVs were imported into the real Sheet;
   - the tabs were reviewed by hand;
   - the `Otros` column was added to the jam tab;
   - more songs were added to `Catalogo`.

   State plainly that no agent read the Sheet. If the user did not explicitly mention the `Config`
   tab, say so rather than claiming it.
2. The final mapping table, as written into the doc or the PROGRESS entry, with U1's answer.
3. Seed-check output for both the pass and the demonstrated failures.
4. The `CI=true ./init.sh` summary line and the three `wired` lines.

## Validator Checklist

- [ ] Every field of the 12 types appears in the mapping. None is left unexplained.
- [ ] The enum values in the doc exactly match the code's KDoc values.
- [ ] Every one of M1–M11 is fixed in the docs. U1 is either applied as the user chose or still
      recorded as pending. No code change was made beyond the U1-B KDoc.
- [ ] The mapper rules are documented and not implemented. No invented `Jams`/tab mismatch
      behavior.
- [ ] The seed check has a recorded pass and a recorded failure. The seed is unchanged and the
      passphrase is empty.
- [ ] The user's hand check is attributed to the user and not presented as agent-verified.
- [ ] The gate exits 0 with all three tools `wired`.

## User Approvals

Recorded 30 September 2026, before implementation:

- **U1 decided: Option B.** A slot is identified by its order among that instrument's slots: the
  k-th guitar slot is the k-th guitar column that does not hold `-`. Update the **Identifiers**
  section and the one-sentence KDoc in `Lineup.kt` accordingly.
- P1–P3 and the deferred mapper rules stand as proposed; the user did not overrule them.
- The user did not mention the `Config` tab in their hand check; record that plainly.
