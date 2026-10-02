# Sheet Schema

The Google Sheet is the backend (D-04), reached only through Apps Script. This document is the
contract between the Sheet, the Apps Script endpoints, and the `:core:data` mappers.

Headers are in Spanish because the admin edits the Sheet by hand; it is user-facing (D-12). Code
maps each header to the English field named in the tables below. Headers are matched by name, not
by column position, so the admin may reorder or add columns without breaking the app.

Seed files for import live in `docs/sheet-seed/`.

## Reading cells

- **Every cell is trimmed** of surrounding whitespace before it is interpreted. A cell that is
  empty after trimming is empty.
- **Headers and enum values match exactly after trimming.** Matching is case-sensitive and accents
  are required: `rápido` is a tempo, `rapido` and `Rápido` are not. An unknown `estado` is rejected
  by the mapper, like a bad key. An unknown `tempo` or `dificultad` only drops that value: the song
  is kept without it and the value is reported in the refresh log (user approval Q2 of
  `catalog-repository-cache`; see **Mapper rules**). Admin advice, not enforced: put a Sheets
  data-validation dropdown on `tempo`, `dificultad` and `estado`.
- **The catalog endpoint emits display text.** `apps-script-read-endpoint` reads `Catalogo` with
  the cells' display values, trimmed, so what the admin sees is what the app gets, and a value
  Sheets auto-converted (say `7/4` into a date) cannot leak a date object. The one exception is
  `songsterr_id`, read from the raw value so that a whole number is emitted as plain digits
  (`12345`, never an es-AR `12.345`). See `apps-script-api.md`.
- **The formats below are the values the jam read endpoint emits.** Sheets may store a cell typed
  as `2026-07-25`, `21:00` or `3` as a date, a time or a number. The jams endpoint
  (`apps-script-jams-read-endpoint`) normalizes them either way:
  - `fecha`: a typed date is formatted `yyyy-MM-dd` in the **spreadsheet's own time zone** (the
    zone Sheets used to build the value; Buenos Aires for this Sheet); text is trimmed and passed
    through.
  - `hora`: the **display text first**. `H:MM`, `HH:MM` or `HH:MM:00` with a valid hour and
    minute is zero-padded to `HH:MM`. Otherwise a typed time (for example one shown as
    `9:00 p. m.`) is formatted `HH:mm` in the spreadsheet's zone; otherwise the trimmed text is
    passed through. Display first, because a time-only cell sits on Sheets' 1899-12-30 epoch, where
    the historical offset of Buenos Aires (-4:16:48) can shift the minutes.
  - `posicion`: the raw value, as for `songsterr_id` (`3` gives `"3"`, `2.5` gives `"2.5"` for
    the mapper to reject).
  - Every other jam and setlist cell is display text, trimmed.

  A value that is not in the documented format still reaches the mapper as text, which rejects it.
  Admin advice, strongly recommended: format the `fecha`, `hora` and `posicion` columns as plain
  text (Format → Number → Plain text). In the live test of 1 October 2026 the first test entry came back
  as `2026-01-01` with an empty time. The script emits the cells' own values, so that is what the
  cells held; the cause was not established (likely an input slip). Plain text avoids Sheets
  reinterpreting what is typed.

## Tabs

| Tab | One row per | Authority (D-04) | Written by |
|---|---|---|---|
| `Catalogo` | Song | Sheet | The admin, by hand. The app never writes it. |
| `Jams` | Jam | Sheet for past jams; app for the upcoming jam | Admin by hand; Apps Script for the upcoming jam |
| `YYYY-MM-DD`, one tab per jam | JamSong in that jam's setlist | Sheet for past jams; app for the upcoming jam | Admin by hand; Apps Script for the upcoming jam |
| `Config` | Setting | Sheet | The admin, by hand |

A jam tab is named with its ISO date, for example `2026-07-25`, so tabs sort chronologically and the
name is the join key with `Jams.fecha`. No other tab may use that name pattern.

## `Catalogo` → `Song`

| Header | Field | Required | Format |
|---|---|---|---|
| `id` | `id` | yes | Slug, unique, never changed after creation: see **Identifiers**. On a title clash, append the artist: `crossroads-robert-johnson`. |
| `titulo` | `title` | yes | Free text |
| `artista` | `artist` | yes | Free text, one spelling per artist across the whole catalog |
| `tono_default` | `defaultKey` | yes | See **Keys** |
| `tempo` | `tempo` | no | `lento` → `SLOW`, `medio` → `MEDIUM`, `rápido` → `FAST`. Empty → none |
| `etiquetas` | `tags` | no | Comma-separated: `shuffle, 12 compases, slow blues, blues nacional`. Split on `,`, trim each tag, drop empty tags, keep the order. Empty → no tags |
| `dificultad` | `difficulty` | no | `fácil` → `EASY`, `media` → `MEDIUM`, `difícil` → `HARD`. Empty → none |
| `songsterr_id` | `songsterrId` | no | Numeric id from the Songsterr URL, entered by the admin (D-10). Empty → none |

The id is what jam tabs reference, so fixing a typo in a title or an artist never breaks history.

MusicBrainz, Deezer and Last.fm enrichment (`mbid`, `artistArea`, `deezerTrackId`, `previewUrl`,
`artworkUrl`) is **not** stored in the Sheet: it is fetched in the background and cached in Room
(D-09). Storing it here would make the app a writer of the catalog, which D-04 forbids.
`songsterr_id` is not enrichment: the admin types it in the Sheet and nothing fetches it.

## `Jams` → `Jam`

| Header | Field | Required | Format |
|---|---|---|---|
| `fecha` | `date` | yes | `YYYY-MM-DD`, unique; also the name of the jam's tab |
| `hora` | `startTime` | yes | `HH:MM`, 24 hours |
| `lugar` | `venue` | yes | Free text |
| `estado` | `status` | yes | `BORRADOR` → `DRAFT`, `PUBLICADA` → `PUBLISHED` |
| — | `setlist` | — | Derived: the rows of the tab named `fecha`, sorted by `posicion` |

A jam is historical when `fecha` is before today; on its own date a jam is not historical, and there
is no archived status (domain model, `Jam.isHistorical`). At most one jam may be non-historical,
that is, have a `fecha` of today or later.

`startTime` is a precision over `docs/domain-model.md`, which has only `date`: the jam poster
carries a time, and the header of the next-jam screen needs it.

## Jam tab `YYYY-MM-DD` → `JamSong` and `Slot`

| Header | Field | Required | Format |
|---|---|---|---|
| `posicion` | `position` | yes | 1, 2, 3… with no gaps; row order in the tab is not trusted |
| `id_tema` | `songId` | yes | An `id` that exists in `Catalogo` |
| `titulo` | `title` (fallback only) | yes | Copy of the catalog title. Used only as a fallback, see below |
| `artista` | `artist` (fallback only) | yes | Copy of the catalog artist. Same fallback |
| `tono` | `key` | yes | See **Keys**. That night's key, not the catalog default (D-08) |
| `Guitarra 1`, `Guitarra 2`, `Bajo`, `Batería`, `Voz`, `Armónica`, `Teclados` | `lineup` | header yes, cells no | One column per slot of the default lineup (D-06); see **Slot columns** |
| `Otros` | `extraParticipants` | no | `Nombre (instrumento)` entries separated by `;`, e.g. `Juan (saxo); Ana (percusión)`. Empty or missing column = none (D-18); see **`Otros`** |

`Catalogo` is authoritative for title and artist: the app resolves `id_tema` there. The copies in
the jam tab exist so the admin can read the tab, and so a setlist stays readable if a song is later
removed from the catalog — the app falls back to them only when `id_tema` is not found. Keep them
as plain text rather than a lookup formula, or the fallback breaks exactly when it is needed.

### Slot columns

All seven slot headers are **required in every jam tab**; a tab missing one is invalid. Their cells
are optional. Each header maps to one `Instrument`, and the header order is the order of
`Lineup.DEFAULT_INSTRUMENTS`:

| Header | `Instrument` |
|---|---|
| `Guitarra 1` | `GUITAR` |
| `Guitarra 2` | `GUITAR` |
| `Bajo` | `BASS` |
| `Batería` | `DRUMS` |
| `Voz` | `VOCALS` |
| `Armónica` | `HARMONICA` |
| `Teclados` | `KEYBOARDS` |

| Cell | Meaning |
|---|---|
| empty | Open slot: part of this song's lineup, no musician yet |
| a name | Filled slot: `musicianName` |
| `-` | Not part of this song's lineup (the per-song adjustment in D-06) |

The lineup is built from the seven columns in the order above, skipping `-` cells. A song with no
harmonica has `-` under `Armónica`. A song with one guitar may have `-` under either guitar column;
**Identifiers** says which column its slot is. Slots are only ever removed from the default seven,
never added (D-18). Anyone playing outside the lineup — a sax, percussion, a third guitar — goes in
`Otros`.

For past jams the slot cells are optional history. An empty cell in a past jam means "not
recorded", and the app never presents a past jam as having open slots.

Musician name suggestions for the assign-slot sheet are derived from the names in past jam tabs,
which settles that open question without a separate musicians list (there is no Musician entity,
D-05).

### `Otros`

Split the cell on `;` and trim each entry. Empty entries are ignored, including the one a trailing
`;` leaves. Every other entry must be `Nombre (instrumento)`: a name, then an instrument in
parentheses. Both are required and may not contain `;`, `(` or `)` (`ExtraParticipant`). The mapper
rejects a malformed entry such as `Juan saxo` rather than guessing. Entries keep their order. Extra
participants are never open slots and never count for "where can I play?".

## `Config`

| Header | Meaning |
|---|---|
| `clave` | Setting name |
| `valor` | Setting value |

| `clave` | Use |
|---|---|
| `passphrase` | Admin passphrase (D-11). Rotating it here revokes every device. |

**No read endpoint may ever return `Config`.** Apps Script reads the passphrase to validate writes
and nothing else. The seed's `valor` stays empty in the repo.

## Keys

A key is a note letter `A`–`G`, an optional `#` or `b`, and an optional `m` for minor: `B`, `Bm`,
`F#`, `Bbm`. No spaces, no `maj`, no Spanish note names. The mapper rejects anything else, so a
typo shows up when the tab is read, not on stage.

## Identifiers

- `Song` → `Catalogo.id`. Its alphabet is exact: ASCII `a`–`z` and `0`–`9`, words joined by single
  hyphens (`[a-z0-9]+(-[a-z0-9]+)*`, as `SongId` enforces). No accents, spaces, uppercase, or
  leading, trailing or doubled hyphens. `Café Madrid` becomes `cafe-madrid`.
- `Jam` → `Jams.fecha`, which is also the tab name.
- `JamSong` → (`fecha`, `posicion`).
- `Slot` → (`fecha`, `posicion`, instrument, ordinal among that instrument's slots). The k-th slot
  of an instrument is the k-th column of that instrument, in header order, whose cell is not `-`.
  With `Guitarra 1 = -` and `Guitarra 2 = Pedro`, the song's only guitar slot is `Guitarra 2`;
  with `Guitarra 1 = Ana` and `Guitarra 2 = -`, it is `Guitarra 1`. Any layout the admin types is
  valid, and a write (assign, clear, adjust lineup) resolves the column the same way. Only the
  guitar has two columns; for every other instrument the ordinal is always 1.
- Extra participant → (`fecha`, `posicion`, its order among the non-empty `Otros` entries).

## Type-to-Sheet mapping

Every field of the `:core:model` types, and where it comes from. "Derived" fields are computed by
the mapper or the type; "enrichment" fields are never in the Sheet.

| Type.field | Sheet source |
|---|---|
| `Song.id: SongId` | `Catalogo.id` (**Identifiers**) |
| `Song.title` / `Song.artist` | `Catalogo.titulo` / `artista` |
| `Song.defaultKey: Key` | `Catalogo.tono_default` (**Keys**) |
| `Song.tempo: Tempo?` | `Catalogo.tempo`: `lento`, `medio`, `rápido` → `SLOW`, `MEDIUM`, `FAST` |
| `Song.tags` | `Catalogo.etiquetas`, split on `,` |
| `Song.difficulty: Difficulty?` | `Catalogo.dificultad`: `fácil`, `media`, `difícil` → `EASY`, `MEDIUM`, `HARD` |
| `Song.songsterrId: Long?` | `Catalogo.songsterr_id`, admin-entered (D-10) |
| `Song.mbid`, `artistArea`, `deezerTrackId`, `previewUrl`, `artworkUrl` | Not in the Sheet: background enrichment cached in Room (D-09, D-04) |
| `Jam.date: LocalDate` | `Jams.fecha`, also the tab name |
| `Jam.startTime: LocalTime` | `Jams.hora` |
| `Jam.venue` | `Jams.lugar` |
| `Jam.status: JamStatus` | `Jams.estado`: `BORRADOR`, `PUBLICADA` → `DRAFT`, `PUBLISHED` |
| `Jam.setlist` | Derived: the rows of the tab named `fecha`, sorted by `posicion` |
| `Jam.isHistorical(today)` | Derived from `fecha`; today comes from the caller |
| `JamSong.position` | `posicion` |
| `JamSong.songId: SongId` | `id_tema` |
| `JamSong.title` / `JamSong.artist` | `Catalogo.titulo` / `artista` via `id_tema`; the tab's `titulo` / `artista` only when `id_tema` is not found |
| `JamSong.key: Key` | `tono` (D-08) |
| `JamSong.lineup: Lineup` | The seven slot columns, skipping `-` |
| `Lineup.slots` | One `Slot` per slot column not holding `-`, in header order |
| `Lineup.openSlots`, `Lineup.hasOpenSlotFor` | Derived from `slots` |
| `Slot.instrument: Instrument` | The column header (**Slot columns**) |
| `Slot.musicianName: String?` | The cell; empty → `null` (open) |
| `Slot.isOpen` / `Slot.isFilled` | Derived from `musicianName` |
| `JamSong.extraParticipants` | `Otros`, split on `;` |
| `ExtraParticipant.name` / `instrument` | `Nombre` / `instrumento` of one `Otros` entry |
| `Key.value`, `Key.isMinor` | The trimmed key cell; `isMinor` derived |
| `SongId.value` | The trimmed `id` or `id_tema` cell |
| `Instrument` values | `GUITAR`, `BASS`, `DRUMS`, `VOCALS`, `HARMONICA`, `KEYBOARDS`, from the headers above |
| `Config.clave` / `valor` | No domain type. Server-only; never returned by a read endpoint |

## Mapper rules

Rules the schema states but that no domain type can check alone. Each is **enforced by a
repository slice** (`catalog-repository-cache` for `Catalogo`, `jams-repository-cache` for `Jams`
and the jam tabs), not by this document or the seed.

The split between the endpoints and the mapper: the Apps Script read endpoints
(`apps-script-read-endpoint`, `apps-script-jams-read-endpoint`) only trim cells, match headers by
trimmed exact name, turn typed cells into text, reject structural problems (a missing tab, a
missing required header, a mapped header twice), and withhold every setlist whose jam is not
`PUBLICADA` (below). Everything that interprets a value — keys, the id
alphabet, enum values, tag and `Otros` splitting, unique ids and positions, catalog resolution —
is the Kotlin mapper's, so there is one interpreter, tested in the gate against the domain types.

### `Catalogo` (enforced by `catalog-repository-cache`)

The catalog mapper is `CatalogMapper` in `:core:data`. It trims every cell again (empty means
none) and checks each row of the `catalog` response:

| Field | Rule | When it fails |
|---|---|---|
| `id` | required; the **Identifiers** alphabet | the song is rejected |
| `titulo`, `artista` | required | the song is rejected |
| `tono_default` | required; **Keys** | the song is rejected |
| `tempo` | empty, or exactly `lento`, `medio`, `rápido` | the value is dropped; the song is kept |
| `dificultad` | empty, or exactly `fácil`, `media`, `difícil` | the value is dropped; the song is kept |
| `songsterr_id` | empty, or decimal digits that fit a 64-bit integer (`12.5`, `1e3` and `-5` are not ids) | the value is dropped; the song is kept |
| `etiquetas` | split on `,`, each tag trimmed, empty tags dropped, order and duplicates kept | never fails |

- **A required field decides; an optional one never hides a song.** `tempo`, `dificultad` and
  `songsterr_id` are not used by the MVP (D-20), so a bad value there drops only that value (user
  approval Q2, 1 October 2026). Every problem of a row, kept or rejected, is reported with its
  1-based position in the response and its id, in the refresh outcome and the startup log line
  (`catalog refresh: …`). Nothing is persisted, and no admin surface shows them yet.
- **`Catalogo.id` is unique.** After the per-row checks, **every** valid row sharing an id is
  rejected, not only the later ones (user approval Q1): the mapper does not guess which row is
  right, and a setlist that references the id falls back to its tab's copy.
- A rejected row never stops the others. Only a broken response (not JSON, `schemaVersion` other
  than 1, an `error`, a missing key or a non-string value) fails the whole read, and then the cache
  is left as it was. A valid response replaces the cached catalog whole, even when it has no songs
  (the Sheet is the authority, D-04).

### `Jams` and jam tabs (for `jams-repository-cache`)

- At most one non-historical jam (a `fecha` of today or later). Enforced by the repository slice.
- Every `id_tema` resolves in `Catalogo`; when it does not, the tab's `titulo` / `artista` copy is
  the fallback. Enforced by the repository slice.
- `posicion` is unique and exactly 1..n within a tab. Enforced by the repository slice (and by
  `Jam` once built).
- A `Jams` row has a jam tab and a jam tab has a `Jams` row. The endpoint's half is settled: a
  `PUBLICADA` row with no tab arrives with `setlistError` `missing_tab`; a tab with no `Jams` row
  is ignored, never read or served; a per-jam error never fails the whole response. What the app
  shows for a jam with a `setlistError` is **open**, left to `jams-repository-cache` (see
  `risks-and-open-questions.md`).
- **Only a `PUBLICADA` jam's setlist is served.** The jams endpoint reads a jam tab only when the
  trimmed `estado` is exactly `PUBLICADA`; any other value, a typo or wrong case included, gives
  `setlist: null` with no error and the tab is never opened. This is the one value the endpoint
  interprets, because withholding a draft cannot be left to the client. The mapper still owns the
  `estado` enum and rejects a typo. A tab is looked up only by a `fecha` that is `YYYY-MM-DD`
  (else `invalid_date`) and that appears on one row only (else `duplicate_date`). Enforced by
  the endpoint.
- Every cell is trimmed before it is read (**Reading cells**). Done by the read endpoints, before
  the mapper sees a value.
- Enum and header matching is exact after trimming (**Reading cells**); `Otros` is parsed as in
  **`Otros`**; slot identity follows **Identifiers**. Enforced by the repository slice.
- All seven slot headers are present in every jam tab; a tab missing one is invalid. Detected by
  the jams endpoint per jam (`setlistError` `missing_header`, the other jams still served); what the
  app does with it is the repository slice's.
- Empty slot cells in a past jam mean "not recorded" and are never shown as open. Enforced by the
  repository slice.
