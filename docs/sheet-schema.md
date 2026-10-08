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
| `_prueba_escritura` | Nothing (transient) | Apps Script | Created and deleted by the `checkWriteAccess` deploy check within one request (`apps-script-write-auth`); never read by any route, never served |
| `_prueba_lista` | Nothing (transient) | Apps Script | Created with the jam tab header, given one marker row and deleted by the `checkSetlistWrite` deploy check within one request (`admin-add-song-to-setlist`), or given three marker rows, one removed, and deleted by the `checkSetlistRemove` deploy check (`admin-remove-song-from-setlist`); since `admin-set-key` the `checkSetlistWrite` marker's `tono` is also rewritten (`Bbm` to `F#m`) before the read-back; it has no `Jams` row, so no route ever serves it |

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
| — | `setlist` | — | Derived: the rows of the tab named `fecha`, sorted by `posicion` (see **Mapper rules** for withheld and unavailable setlists) |

A jam is historical when `fecha` is before today; on its own date a jam is not historical, and there
is no archived status (domain model, `Jam.isHistorical`). Today is the date in Buenos Aires. At
most one jam may be non-historical, that is, have a `fecha` of today or later; when several are, the
app shows the earliest and holds the others back (**Mapper rules**).

`startTime` is a precision over `docs/domain-model.md`, which has only `date`: the jam poster
carries a time, and the header of the next-jam screen needs it.

## Jam tab `YYYY-MM-DD` → `JamSong` and `Slot`

| Header | Field | Required | Format |
|---|---|---|---|
| `posicion` | `position` | yes | 1, 2, 3… with no gaps; row order in the tab is not trusted. The app never renumbers: a row it drops leaves a gap (**Mapper rules**) |
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

**How Apps Script writes a jam tab** (`addSong`, `admin-add-song-to-setlist`, and `removeSong`,
`admin-remove-song-from-setlist`; contract in `apps-script-api.md`). `addSong` appends and never
edits an existing row; `removeSong` deletes one row and rewrites later `posicion` cells (below).
Only the
upcoming jam's tab is written (its `Jams` row is the earliest with a known `estado` that is today or
later, `BORRADOR` or `PUBLICADA`); a past jam's tab never (D-04). When the tab does not exist yet it
is created with the header row above, in that order. The new row goes after the last row, at
`posicion` = 1 + the largest whole-number `posicion` (invalid cells ignored), into the mapped
columns only (found by header, so an extra column the admin added is left alone), with every
written cell set to plain text (`@`) first: `posicion` is stored as text, which the read path
accepts. `id_tema`, `titulo` and `artista` come from `Catalogo` (exactly one row with that `id`, a
title and an artist), `tono` is the key the app sent (never `tono_default`, D-08), the seven slot
cells and `Otros` are written empty (the default lineup, all open, D-18). A song already in the
tab is refused (`song_already_in_setlist`). Every check runs before the first write.

`removeSong` (user decision R1 (a)) finds its row by the trimmed `id_tema`, never by `posicion`
(no row: `song_not_in_setlist`; more than one: `duplicate_song`; both before any write), deletes
that whole row (its slots and `Otros` with it) and rewrites every later whole-number `posicion` as
`posicion - 1`, in plain text, in ascending order, so the tab stays numbered 1..n. A failure midway
can leave a gap, never a duplicate. `Catalogo` is never opened. **Every later setlist mutation that
changes an existing row also locates it by `id_tema`.**

`setKey` (`admin-set-key`) finds its row the same way (the shared `findSongRow_`, same codes, all
before any write) and writes **only that row's `tono` cell**, as plain text (`@` first, then the
value), in the spelling the admin picked (`A#` and `Bb` are both kept as sent; `[A-G][#b]?m?`).
No other cell is written and `Catalogo`, so `tono_default`, is never opened (D-08).

`setSlotCount` (`admin-adjust-lineup`) also finds one row by `id_tema`. It only removes open
slots, writing `-` to the last open column first; restoration writes an empty plain-text cell
to the first `-` column first. A name is never written or moved; insufficient open slots are
`slot_filled` before any write. Zero of any instrument is valid, never above the default.
Restoring Guitarra 1 before filled Guitarra 2 changes the name's ordinal to second without moving
its cell. Later ordinal-based assignment and clearing must resolve the current non-`-` columns
server-side and refuse if the resolved cell is no longer in the expected state.
The `_prueba_lista` deploy marker now ends with Guitarra 2 absent and all other slots open,
after harmonica removal/restoration; its temporary tab is still cleaned up. Deployment is pending.

`assignSlot` (`admin-assign-musician`) locates the same unique row by `id_tema`, resolves the
1-based ordinal among non-`-` columns for that instrument in canonical header order (U1), checks the
target is still empty, then writes the normalized musician name to that one slot cell as plain text.
It never overwrites an occupied cell and never touches `Catalogo`. For example, if Guitarra 1 is
`-` and Guitarra 2 is active, guitar ordinal 1 resolves to Guitarra 2. This ordinal is independent
of the app's open-first display order. Rejections happen before any cell is written; success returns
the canonical seven-slot array index and normalized name. The assignment step in
`checkSetlistWrite` uses only the disposable marker tab. Deployment and live validation remain
deferred until app implementation is complete.

`clearSlot` (`admin-clear-slot`) resolves the same 1-based active-column ordinal with U1 and
compare-and-clears only when the trimmed display text equals the expected cached musician name.
`slot_empty` and `slot_changed` reject a stale target without writing. Success clears exactly the
resolved cell to an empty open slot; it does not shift other musicians or change `Otros`. The
expected name uses existing display text as-is and is not checked against new-assignment rules.
`checkSetlistWrite` assigns then clears its disposable marker and verifies the cleared cell before
its `finally` cleanup. Deployment and live checks are deferred until app implementation is complete.

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
parentheses, with or without a space between them (`Juan(saxo)` is valid). Both are required and
may not contain `;`, `(` or `)` (`ExtraParticipant`). The mapper drops a malformed entry such as
`Juan saxo` rather than guessing, and keeps the song and the other entries (user approval P1 of
`jams-repository-cache`). Entries keep their order. Extra participants are never open slots and
never count for "where can I play?".

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
- `JamSong` → (`fecha`, `posicion`) is the **read identity**: the order of the setlist and the
  key of the cached row. Setlist mutations locate a row by (`fecha`, `id_tema`) instead (user
  decision R1 (a), `admin-remove-song-from-setlist`): `addSong` keeps `id_tema` unique within a
  tab, and a removal renumbers later positions, so a position is not stable across writes.
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
| `Jam.setlist: Setlist` | Derived. `Available`: the valid rows of the tab named `fecha`, sorted by `posicion`, with the count of dropped rows; `Withheld`: a `BORRADOR` jam; `Unavailable(problem)`: a `PUBLICADA` jam whose tab is missing or invalid, or whose rows are all invalid |
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

### `Jams` and jam tabs (enforced by `jams-repository-cache`)

The jam mappers are `JamsMapper` (the `Jams` rows) and `SetlistMapper` (one jam's tab rows) in
`:core:data`. They trim every cell again (empty means none) and match values exactly. The user's
decisions P1–P8 of 2 October 2026 are recorded in `docs/specs/jams-repository-cache.md`.

| Field | Rule | When it fails |
|---|---|---|
| `fecha` | required; `YYYY-MM-DD` and a real date (`2026-02-30` is not) | the jam is rejected |
| `fecha` unique | a `fecha` that parses and is on more than one row rejects every such row, valid or not (P2) | every such row is rejected |
| `hora` | required; `HH:MM`, 24 hours, zero-padded (`9:00`, `24:00` are not) | the jam is rejected (P8) |
| `lugar` | required | the jam is rejected (P8) |
| `estado` | exactly `BORRADOR` or `PUBLICADA` (`Publicada` is not) | the jam is rejected |
| `posicion` | required; digits only, at least 1 (`2.5`, `0`, `-1` are not) | the row is dropped |
| `posicion` unique | a `posicion` that parses and is on more than one row drops every such row, valid or not | every such row is dropped |
| `id_tema` | required; the **Identifiers** alphabet | the row is dropped |
| `titulo`, `artista` | required (they are the fallback copies) | the row is dropped |
| `tono` | required; **Keys**. Never filled from `tono_default` (D-08) | the row is dropped |
| slot cells | empty = open slot, `-` = no slot, anything else = the musician's name | never fails |
| `Otros` | **`Otros`** above | a malformed entry is dropped; the row is kept (P1) |

- **A rejected `Jams` row never stops the others**, and a dropped setlist row never stops the
  rest of its setlist (user approval P4): the setlist stays available with its valid rows, sorted by
  `posicion` and **never renumbered by the read path**, because (`fecha`, `posicion`) is the read
  identity of a JamSong. A gap is therefore possible; the setlist carries how many rows were dropped so a screen
  can say it is incomplete. A setlist whose rows are all invalid is **unavailable**
  (`INVALID_ROWS`), never an empty list. The Sheet itself should still number 1..n; `removeSong`
  keeps it so.
- **The setlist state.** A `BORRADOR` jam is always **withheld**; a setlist or error that arrives
  with one breaks the contract, is ignored (fail closed) and is reported. A `PUBLICADA` jam with a
  `setlistError` is **unavailable** (P3): `missing_tab` → `MISSING_TAB`, `missing_header` and
  `duplicate_header` → `INVALID_TAB` (so duplicate slot headers are settled by the endpoint; the
  mapper never sees them), any other code → `UNKNOWN`. A `PUBLICADA` jam with neither a setlist nor
  an error is unavailable (`UNKNOWN`). A published jam is never withheld. A published tab with
  headers and no rows is an empty available setlist.
- **At most one upcoming jam** (P5). With today's date in Buenos Aires (P7), the earliest jam that
  is not historical is the upcoming one; later future jams are held back, shown in neither list,
  and reported in the refresh log. Past jams are listed newest first.
- **Catalog resolution.** Every `id_tema` is resolved in the cached `Catalogo` when the jams are
  read (a Room view), so a later catalog fix shows without refetching the jams; when it is not
  there, the tab's `titulo` / `artista` copy is the fallback.
- **Past slots.** An empty slot cell in a past jam means "not recorded" and is dropped from the
  lineup, so no past jam ever shows an open slot (P6).
- **The endpoint's half.** Only a `PUBLICADA` jam's setlist is served: the jams endpoint reads a
  jam tab only when the trimmed `estado` is exactly `PUBLICADA`; any other value, a typo or wrong
  case included, gives `setlist: null` with no error and the tab is never opened. A tab is looked up
  only by a `fecha` that is `YYYY-MM-DD` (else `invalid_date`) and that appears on one row only
  (else `duplicate_date`); a `PUBLICADA` row with no tab arrives with `missing_tab`, a tab missing
  a slot header with `missing_header`; a tab with no `Jams` row is ignored. A per-jam error never
  fails the whole response. Enforced by the endpoint. The guarded POST `readJams`
  (`admin-add-song-to-setlist`) additionally reads the tab of every jam whose `estado` is exactly
  `BORRADOR` and whose `fecha` is today or later, with the same guards; only a device holding the
  passphrase receives it, and the app maps it with `includeDrafts`, where a draft with no tab yet
  is an empty setlist. Musician screens still withhold every draft (`setlistForMusicians()`).
- Every rejected row, dropped row, malformed `Otros` entry, ignored draft setlist, `setlistError`
  and held-back jam is reported in the refresh outcome and the startup log line
  (`jams refresh: …`), by index and date, never with a musician's name. Nothing is persisted, and no
  admin surface shows them yet. Only a broken response (not JSON, `schemaVersion` other than 1, an
  `error`, a missing key or a wrong type) fails the whole read, and then the cache is left as it
  was; a valid response replaces the cached jams whole.
