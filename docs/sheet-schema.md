# Sheet Schema

The Google Sheet is the backend (D-04), reached only through Apps Script. This document is the
contract between the Sheet, the Apps Script endpoints, and the `:core:data` mappers.

Headers are in Spanish because the admin edits the Sheet by hand; it is user-facing (D-12). Code
maps each header to the English field named in the tables below. Headers are matched by name, not
by column position, so the admin may reorder or add columns without breaking the app.

Seed files for import live in `docs/sheet-seed/`.

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
| `id` | `id` | yes | Lowercase slug, unique, never changed after creation: `sweet-little-angel`. On a title clash, append the artist: `crossroads-robert-johnson`. |
| `titulo` | `title` | yes | Free text |
| `artista` | `artist` | yes | Free text, one spelling per artist across the whole catalog |
| `tono_default` | `defaultKey` | yes | See **Keys** |
| `tempo` | `tempo` | no | `lento`, `medio` or `rápido` |
| `etiquetas` | `tags` | no | Comma-separated: `shuffle, 12 compases, slow blues, blues nacional` |
| `dificultad` | `difficulty` | no | `fácil`, `media` or `difícil` |
| `songsterr_id` | `songsterrId` | no | Numeric id from the Songsterr URL (D-10) |

The id is what jam tabs reference, so fixing a typo in a title or an artist never breaks history.

MusicBrainz, Deezer and Last.fm enrichment (`mbid`, `artistArea`, `deezerTrackId`, `previewUrl`,
`artworkUrl`) is **not** stored in the Sheet: it is fetched in the background and cached in Room
(D-09). Storing it here would make the app a writer of the catalog, which D-04 forbids.

## `Jams` → `Jam`

| Header | Field | Required | Format |
|---|---|---|---|
| `fecha` | `date` | yes | `YYYY-MM-DD`, unique; also the name of the jam's tab |
| `hora` | `startTime` | yes | `HH:MM`, 24 hours |
| `lugar` | `venue` | yes | Free text |
| `estado` | `status` | yes | `BORRADOR` → `DRAFT`, `PUBLICADA` → `PUBLISHED` |

A jam is historical when `fecha` is before today; there is no archived status (domain model). At
most one jam may have a future `fecha`.

`startTime` is a precision over `docs/domain-model.md`, which has only `date`: the jam poster
carries a time, and the header of the next-jam screen needs it.

## Jam tab `YYYY-MM-DD` → `JamSong` and `Slot`

| Header | Field | Required | Format |
|---|---|---|---|
| `posicion` | `position` | yes | 1, 2, 3… with no gaps; row order in the tab is not trusted |
| `id_tema` | `songId` | yes | An `id` that exists in `Catalogo` |
| `titulo` | — | yes | Copy of the catalog title. Used only as a fallback, see below |
| `artista` | — | yes | Copy of the catalog artist. Same fallback |
| `tono` | `key` | yes | See **Keys**. That night's key, not the catalog default (D-08) |
| `Guitarra 1`, `Guitarra 2`, `Bajo`, `Batería`, `Voz`, `Armónica`, `Teclados` | `lineup` | see below | One column per slot of the default lineup (D-06) |

`Catalogo` is authoritative for title and artist: the app resolves `id_tema` there. The copies in
the jam tab exist so the admin can read the tab, and so a setlist stays readable if a song is later
removed from the catalog — the app falls back to them only when `id_tema` is not found. Keep them
as plain text rather than a lookup formula, or the fallback breaks exactly when it is needed.

### Slot cells

| Cell | Meaning |
|---|---|
| empty | Open slot: part of this song's lineup, no musician yet |
| a name | Filled slot: `musicianName` |
| `-` | Not part of this song's lineup (the per-song adjustment in D-06) |

A song with no harmonica has `-` under `Armónica`. A slot for an instrument outside the default
seven is out of scope for the MVP.

For past jams the slot cells are optional history. An empty cell in a past jam means "not
recorded", and the app never presents a past jam as having open slots.

Musician name suggestions for the assign-slot sheet are derived from the names in past jam tabs,
which settles that open question without a separate musicians list (there is no Musician entity,
D-05).

## `Config`

| Header | Meaning |
|---|---|
| `clave` | Setting name |
| `valor` | Setting value |

| `clave` | Use |
|---|---|
| `passphrase` | Admin passphrase (D-11). Rotating it here revokes every device. |

**No read endpoint may ever return `Config`.** Apps Script reads the passphrase to validate writes
and nothing else.

## Keys

A key is a note letter `A`–`G`, an optional `#` or `b`, and an optional `m` for minor: `B`, `Bm`,
`F#`, `Bbm`. No spaces, no `maj`, no Spanish note names. The mapper rejects anything else, so a
typo shows up when the tab is read, not on stage.

## Identifiers

- `Song` → `Catalogo.id`.
- `Jam` → `Jams.fecha`, which is also the tab name.
- `JamSong` → (`fecha`, `posicion`).
- `Slot` → (`fecha`, `posicion`, column header).
