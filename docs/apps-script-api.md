# Apps Script API

The contract between the Apps Script web app (`backend/apps-script/`) and the Android client in
`:core:data`. The Sheet behind it is described in `sheet-schema.md`. Deployment steps are in
`backend/apps-script/README.md`.

Current routes: two reads, `catalog` (`apps-script-read-endpoint`) and `jams`
(`apps-script-jams-read-endpoint`), and fifteen POST actions in local source: `checkPassphrase`,
`checkWriteAccess`, `readJams`, `addSong`, `checkSetlistWrite`, `removeSong`, `checkSetlistRemove`,
`setKey`, `setSlotCount`, `assignSlot`, `clearSlot`, `moveSong`, `checkSetlistMove`, `publishSetlist` and `checkPublish`. Every POST
action passes the passphrase guard in the router. Earlier actions have the deployment history below;
the latest local mutations are not live-verified. The user deferred one shared deployment and live
checks until app implementation is complete.

## Transport

- **URL:** the deployment's `/exec` URL. It is not committed; the client reads it from the
  git-ignored `local.properties` key `bluesjam.appsScriptUrl`. Redeploying as a *new version* of
  the same deployment keeps the URL; a *new deployment* changes it.
- **Client wiring** (`catalog-repository-cache`): `app/build.gradle.kts` reads that key with
  `java.util.Properties` (so `\:` escapes are undone) into `BuildConfig.APPS_SCRIPT_URL`, `""` when
  the file or the key is missing. `:app`'s `appModule` binds `AppsScriptEndpoint.of(…)` from it; a
  blank or non-`https` URL means not configured, and every read then fails with `NotConfigured`
  without a request (CI and a fresh clone build and start). The URL is never logged:
  `AppsScriptEndpoint.toString()` hides it and the log line carries only counts and failure kinds.
  Changing `local.properties` invalidates the configuration cache, so the next build picks it up.
- **Client:** OkHttp 5.1.0 in `:core:data` (`OkHttpAppsScriptTransport`), with timeouts connect
  15 s, read 30 s and a whole-call 45 s. Any `IOException` (timeouts included) is `Offline`; a
  non-2xx status is `InvalidResponse`. Requests are `GET <url>?resource=<route>`, built with
  `HttpUrl.newBuilder()`, and the body is read as UTF-8.
- **Method:** `GET` for reads, with the route in the query: `<url>?resource=catalog`. Matching is
  exact and case-sensitive. `POST` for actions (see **POST actions**), with a JSON body and no
  query. **A passphrase only ever travels in a POST body, never in a URL** (a URL ends up in logs
  and histories).
- **Redirect:** the web app answers with a `302` to a one-time URL on
  `script.googleusercontent.com`. The client must follow redirects. OkHttp does by default; `curl`
  needs `-L`. (Google, "Content Service" guide, checked 1 October 2026.) After a POST, the `302`
  is followed as a `GET` (OkHttp's default for a 302, and `curl -L -d` does the same), which is
  what Apps Script expects; the body is not sent again and the passphrase is in no URL.
- **Status:** `ContentService` cannot set an HTTP status, so **every response is HTTP 200, errors
  included**. The client decides by the body: an `error` key means failure. A non-JSON body (an
  HTML page from Google) is a transport failure, not a contract error.
- **Content type:** `application/json`, UTF-8.
- **Access:** anonymous. The script executes as the Sheet owner, so the Sheet itself stays private
  and every call counts against the owner's quotas (below).

## Envelope

Every body has `schemaVersion` first. It is `1` for this contract and changes only on a breaking
change.

Success:

```json
{"schemaVersion":1,"songs":[ … ]}
{"schemaVersion":1,"jams":[ … ]}
```

Error:

```json
{"schemaVersion":1,"error":{"code":"missing_header","message":"Catalogo is missing required headers: tono_default"}}
```

| `code` | When |
|---|---|
| `unknown_resource` | `resource` missing, empty, or not a route (`config`, `CATALOG`, `Jams`) |
| `missing_tab` | The route's tab does not exist (`Catalogo`, `Jams`) |
| `missing_header` | A required header is missing, or the tab is empty |
| `duplicate_header` | A mapped header appears twice (after trimming) |
| `internal_error` | Any other exception; `message` is the exception message |

`message` is English and for logs only. The app maps `code` to Spanish copy; it never shows
`message`.

## `GET ?resource=catalog`

The `Catalogo` tab, one object per non-blank row, in Sheet row order.

| Sheet header | Field | Header required | Value |
|---|---|---|---|
| `id` | `id` | yes | string or null |
| `titulo` | `title` | yes | string or null |
| `artista` | `artist` | yes | string or null |
| `tono_default` | `defaultKey` | yes | string or null |
| `tempo` | `tempo` | no | string or null, Sheet vocabulary (`lento`, `medio`, `rápido`) |
| `etiquetas` | `tags` | no | string or null, the raw comma-separated cell |
| `dificultad` | `difficulty` | no | string or null, Sheet vocabulary (`fácil`, `media`, `difícil`) |
| `songsterr_id` | `songsterrId` | no | string or null, decimal digits when the cell is a whole number |

Rules:

- **Every song has exactly these 8 keys, in this order.** Every value is a non-empty trimmed
  string or `null`. There are no enrichment fields (`mbid`, `artistArea`, `deezerTrackId`,
  `previewUrl`, `artworkUrl`): they are not in the Sheet (D-09).
- **Null rule.** A cell that is empty after trimming, or an optional column that does not exist,
  gives `null`. A missing *required header* is a `missing_header` error. An empty *required cell*
  is still emitted, as `null`, and the mapper rejects that row.
- A row whose 8 mapped cells are all empty is skipped. Columns with other headers are ignored.
  Headers are matched by trimmed exact name: case and accents count.
- Text fields carry the cell's display text, trimmed (NBSP included). `songsterrId` comes from the
  cell's raw value: a whole number `12345` gives `"12345"` whatever the cell's number format
  (never `"12.345"`), any other number gives `String(n)` (`"12.5"`), a text cell is trimmed.
- **The script does not interpret values** (decision P2 of the spec). Keys, the id alphabet, enum
  values, tag splitting, `songsterrId` parsing and duplicate ids are checked by the Kotlin mapper in
  `catalog-repository-cache`, which turns this response into `Song` (`sheet-schema.md`, **Mapper
  rules**).
- `Config` is never opened and never returned, by this or any read route (the `jams` route
  included: see **The draft rule**). Only the POST actions open it (`src/Post.js`), and none
  returns its value.

Samples, both produced by `buildCatalog` and asserted equal to its output by
`backend/apps-script/test/catalog.test.js`:

- [`api-samples/catalog-seed.json`](api-samples/catalog-seed.json): the response for
  `sheet-seed/Catalogo.csv`, 13 songs, every optional field `null`. The mapper's happy path.
- [`api-samples/catalog-edge.json`](api-samples/catalog-edge.json): the response for a messy tab
  (`test/helpers/edge-input.js`). Four songs: every optional field set with a numeric
  `songsterr_id`; none set; `rápido`/`fácil` with a padded string id; and `sin-tono`, with a `null`
  `defaultKey`, tempo `Rápido` and `songsterrId` `"12.5"`, which the mapper rejects for its
  missing key, reporting all three problems (`sheet-schema.md`, **Mapper rules**).

To check a live response: `node backend/apps-script/tools/check-response.js <file>` (exit 0 when
valid). It checks shape only, like the samples' test.

## `GET ?resource=jams`

Every row of the `Jams` tab, in Sheet row order, each with its setlist read from the tab named by
its date. Body: `{"schemaVersion":1,"jams":[ … ]}`. Rows whose four mapped cells are all empty are
skipped. The script does not split jams into upcoming and past: that is `Jam.isHistorical(today)`
in the app, with today from the caller.

### The draft rule

**A setlist is served only for a jam whose `estado`, trimmed, is exactly `PUBLICADA`.** For any
other value (`BORRADOR`, `Publicada`, a typo, empty) the jam is served with `setlist: null` and
`setlistError: null`, and its tab is never even requested. This is the one value the script
interprets: withholding a draft cannot be left to the client. Failing closed is deliberate, so a
past jam left in `BORRADOR` shows no setlist until the admin marks it `PUBLICADA` (user approval
A1). No query parameter changes this: `?resource=jams&passphrase=…` gives the same body as
`?resource=jams`. The admin reads a draft through the POST action `readJams` (below), with the
passphrase in the body; this route never changes for it.

A tab is requested only by a `date` that is `YYYY-MM-DD`, so a `fecha` such as `Config` or
`Catalogo` can never name another tab. The spreadsheet is accessed only through `getSheetByName`
and `getSpreadsheetTimeZone`; tabs are never listed, so a date-named tab with no `Jams` row is
never read or served.

### Jam

Every jam has exactly these keys, in this order:

| Field | Sheet source | Value |
|---|---|---|
| `date` | `fecha` | `YYYY-MM-DD` from a typed date or a text cell; any other text passed through trimmed; `null` when empty |
| `startTime` | `hora` | `HH:MM`; any other text passed through trimmed; `null` when empty |
| `venue` | `lugar` | trimmed display text or `null` |
| `status` | `estado` | trimmed display text or `null`, Sheet vocabulary (`BORRADOR`, `PUBLICADA`); the mapper owns the enum |
| `setlist` | the tab named `date` | an array of setlist rows, or `null` (withheld or broken) |
| `setlistError` | none | `null`, or `{"code","message"}` (below); always `null` for a withheld jam |

`setlist` is an array only when `status` is `PUBLICADA` and `setlistError` is `null`. An empty
array is a published jam whose tab has its headers and no rows. Required `Jams` headers: `fecha`,
`hora`, `lugar`, `estado`.

How typed cells become text (the spreadsheet's own time zone, display text first for `hora`) is
in `sheet-schema.md`, **Reading cells**.

### Setlist row

Every row has exactly these keys, in this order, and `slots` has exactly these seven:

| Field | Jam tab header | Value |
|---|---|---|
| `position` | `posicion` | integer text from the raw value (`3` gives `"3"`, `2.5` gives `"2.5"`), or `null` |
| `songId` | `id_tema` | text or `null` |
| `title` | `titulo` | text or `null`: the fallback copy; the mapper resolves the catalog |
| `artist` | `artista` | text or `null`: same fallback |
| `key` | `tono` | text or `null` |
| `slots` | the seven slot columns | `{"guitar1","guitar2","bass","drums","vocals","harmonica","keyboards"}` from `Guitarra 1`, `Guitarra 2`, `Bajo`, `Batería`, `Voz`, `Armónica`, `Teclados`; each `null` (empty), `"-"` (raw) or a name |
| `extraParticipants` | `Otros` | the raw trimmed cell, or `null` when empty or when the column is missing (D-18) |

Rows come in tab order, not sorted: the mapper sorts by `position`. A row whose mapped cells are
all empty is skipped, and unmapped columns are ignored. The key order of `slots` is the column
order of `Lineup.DEFAULT_INSTRUMENTS`, so the mapper can compute a slot's ordinal (the k-th
non-`-` column of that instrument). `-` and `Otros` are passed through raw: the Kotlin mapper is
their only parser. Required tab headers: `posicion`, `id_tema`, `titulo`, `artista`, `tono` and
all seven slot columns; `Otros` is optional.

### Errors

Whole-response errors use the envelope above and concern the `Jams` tab only: `missing_tab`,
`missing_header` (an empty tab, or a missing `fecha`, `hora`, `lugar` or `estado`) and
`duplicate_header`. Any uncaught exception is `internal_error`.

A problem with one published jam is reported in that jam's `setlistError`, with `setlist: null`,
and every other jam is still served, so one broken past tab cannot blank the app (user approval
A2):

| `setlistError.code` | When (only for a `PUBLICADA` jam) |
|---|---|
| `invalid_date` | `date` is empty or not `YYYY-MM-DD`; no tab is requested |
| `duplicate_date` | the same `date` is on more than one `Jams` row; that tab is not read for any of them |
| `missing_tab` | no tab has that name |
| `missing_header` | the tab is empty or lacks a required header, a slot column included |
| `duplicate_header` | a mapped header appears twice in the tab (after trimming) |

`message` is English and for logs only. What the app shows for a jam with a `setlistError` is
`jams-repository-cache`'s decision. The script still does not interpret values: keys, ids, the
`estado` enum, `-`, `Otros`, unique or contiguous positions, catalog resolution and "at most one
upcoming jam" are the mapper's (`sheet-schema.md`, **Mapper rules**).

Samples, both produced by `buildJams` and asserted equal to its output by
`backend/apps-script/test/jams.test.js`:

- [`api-samples/jams-seed.json`](api-samples/jams-seed.json): the response for
  `sheet-seed/Jams.csv` and `sheet-seed/2026-07-25.csv`. One `PUBLICADA` jam with 13 rows, every
  slot and `extraParticipants` `null`. The mapper's happy path.
- [`api-samples/jams-edge.json`](api-samples/jams-edge.json): the response for a messy spreadsheet
  (`test/helpers/jams-edge-input.js`), nine jams. A published jam with typed `fecha`, `hora` and
  `posicion`, names, a `-`, an `Otros` list with a trailing `;`, padded cells and positions out of
  row order; a `BORRADOR` jam and a `Publicada` typo, both withheld (their tabs hold a marker song
  that is absent from the sample); `missing_tab`; `missing_header` (no `Teclados`);
  `duplicate_date` with its `BORRADOR` twin; `invalid_date` for a `fecha` of `Config`; and a
  published jam whose tab has no `Otros` column and whose `hora` is a typed time shown as
  `9:00 p. m.`.

To check a live response: `node backend/apps-script/tools/check-response.js [--strict] <file>`. It
dispatches on `jams` or `songs`, always enforces the draft rule (a `status` other than `PUBLICADA`
implies `setlist: null` and `setlistError: null`), and with `--strict` also rejects a `date`,
`startTime` or `position` outside its format and any `setlistError`. `jams-edge.json` passes
without `--strict` and fails with it, by design.

## POST actions

`POST <url>` with a JSON object body, `{"action": "<name>", "passphrase": "…", …}`, content type
`application/json; charset=utf-8`. Served by `doPost`/`handlePost` in `src/Post.js`, the only file
that opens `Config`. The answer uses the same envelope (HTTP 200, `schemaVersion` 1, an `error` key
on failure). Actions are matched exactly; an unknown or missing one is `unknown_action`, whose
message ends `Known: checkPassphrase, checkWriteAccess, readJams, addSong, checkSetlistWrite, removeSong, checkSetlistRemove, setKey, setSlotCount, assignSlot, clearSlot, addExtraParticipant, removeExtraParticipant, moveSong, checkSetlistMove, publishSetlist, checkPublish` (the list of the locally implemented
`Post.gs`; a deploy check).

### The router and the guard (`apps-script-write-auth`)

`ACTIONS` maps each name to `{ write: boolean, run: fn }`. `handlePost(request, spreadsheet,
services)` runs, in order:

1. `invalid_request` when the body is not a JSON object;
2. `unknown_action` when the action is not in `ACTIONS`;
3. **the passphrase guard** `requirePassphrase_`, for **every** action. No action calls it itself,
   so an action added later cannot forget it;
4. for a `write: true` action, the **script lock** (`LockService.getScriptLock().tryLock(10000)`),
   otherwise `busy`; it is released in a `finally`;
5. the action's `run(request, spreadsheet, services)`.

`services` is `{ cache, lock, now }`; `doPost` builds it from `CacheService.getScriptCache()`,
`LockService.getScriptLock()` and `Date.now()`, and the Node tests pass fakes.

The guard, in order:

1. **Rate limit** (user approval W2). The key is `auth_failures_<floor(now / 600000)>`: one global
   counter per fixed 10-minute window. When it already holds 10 failures, the answer is
   `rate_limited` and `Config` is not read, **whatever the passphrase**, the right one included,
   until the window ends.
2. `readPassphrase_` reads `Config` (`passphrase_not_set` when unusable). It is read on **every**
   request and never cached, so rotating the passphrase rejects every device at once.
3. The submitted `passphrase` must be a string exactly equal (case-sensitive) to the stored one.
   A mismatch, a missing or a non-string value increments the counter (TTL 1200 s) and answers
   `invalid_passphrase`. `passphrase_not_set` counts nothing.
4. Any exception from the cache is ignored (**fails open**): the passphrase check still runs. The
   increment is not atomic, so simultaneous failures may count once.

The cache only ever holds a number under a key made of the window number: never the stored or the
submitted passphrase. No response or message contains either.

| Case (any action) | Body |
|---|---|
| body missing, not JSON, or not a JSON object | error `invalid_request` |
| `action` missing or unknown | error `unknown_action` (`… Known: checkPassphrase, checkWriteAccess, readJams, addSong, checkSetlistWrite, removeSong, checkSetlistRemove, setKey, setSlotCount, assignSlot, clearSlot, addExtraParticipant, removeExtraParticipant, moveSong, checkSetlistMove, publishSetlist, checkPublish`) |
| 10 failed guesses already in the current 10-minute window | error `rate_limited` |
| `Config` tab, its `clave`/`valor` headers or the `passphrase` row missing, or `valor` blank after trimming | error `passphrase_not_set` |
| `passphrase` missing, not a string, or not equal | error `invalid_passphrase` |
| a write action while another write holds the lock for more than 10 s | error `busy` |
| any other exception | error `internal_error` |

### `checkPassphrase` (`admin-passphrase-login`)

Request: `{"action":"checkPassphrase","passphrase":"…"}`. The client trims the passphrase before
sending; the server compares it with the trimmed `valor` of the `Config` row whose `clave` is
`passphrase` (`sheet-schema.md`, read with `getDisplayValues`). Not a write: no lock. After the
guard it just answers `{"schemaVersion":1,"ok":true}`. The stored value is read **before**
comparing, so an unset passphrase rejects every attempt (fail closed) and a blank submission can
never match a blank cell.

Client (`:core:data`, `admin/`): `DefaultAdminSession.logIn` POSTs through
`AppsScriptPostTransport` (the same `OkHttpAppsScriptTransport` instance), decodes with
`AppsScriptEnvelope.decodeOk` and maps: `ok` → store, `Success`; `invalid_passphrase` →
`WrongPassphrase`; `Offline` → `Offline`; anything else (`NotConfigured`, `passphrase_not_set`,
`rate_limited`, `unknown_action`, an HTML page from a deployment without `doPost`, any invalid
answer) → `Unavailable` ("Probá de nuevo en un rato."). Nothing is stored unless the server said
`ok`.

### `checkWriteAccess` (`apps-script-write-auth`)

Request: `{"action":"checkWriteAccess","passphrase":"…"}`. A write action, so it runs under the
lock. A **self-cleaning deploy check**, not a product mutation (no repository function): it
deletes a leftover `_prueba_escritura` tab if one exists, creates `_prueba_escritura`, writes
`write check <ISO time>` to A1, reads A1 back with `getDisplayValue`, deletes the tab and answers
`{"schemaVersion":1,"ok":true}`. A read-back that differs is `internal_error`, after the tab is
deleted. It touches no other tab. It proves the deployment can create, write, read back and
delete a tab with the owner's authorization.

### `readJams` (`admin-add-song-to-setlist`)

Request: `{"action":"readJams","passphrase":"…"}`. A read action: no lock. The answer is
`{"schemaVersion":1,"ok":true,"jams":[…]}`, the `jams` array in exactly the GET's shape (**Jam**,
**Setlist row**), built by the same route function (`ROUTES.jams`, so the `Jams` tab errors are
the GET's), with one difference: every jam whose `status` is exactly `BORRADOR` **and** whose
`date` is `YYYY-MM-DD` and today or later in the spreadsheet's time zone (`todayIso_`, from
`services.now`) also carries its tab's rows, with the GET's per-jam guards: `duplicate_date`,
`missing_tab`, and `buildSetlist`'s `missing_header`/`duplicate_header`, all in `setlistError`.
Every other jam is left exactly as the GET built it: a past draft, a `Publicada` typo or a draft
whose `fecha` is not a date never has its tab opened. Only the admin's device receives draft songs.

### `addSong` (`admin-add-song-to-setlist`)

Request: `{"action":"addSong","passphrase":"…","date":"2026-10-31","songId":"crossroads","key":"A"}`.
A write action, under the lock. Every check runs **before anything is written**, in this order:

| Order | Check | Code |
|---|---|---|
| 1 | `date` is a string, `YYYY-MM-DD`, a real calendar day | `invalid_date` |
| 2 | `songId` is a string matching `^[a-z0-9]+(-[a-z0-9]+)*$` | `invalid_song` |
| 3 | `key` is a string matching `^[A-G][#b]?m?$` | `invalid_key` |
| 4 | the `Jams` tab has exactly one row with that `fecha` (dates normalized as the reads do) | `unknown_jam`, `duplicate_date` (and the `Jams` tab's own `missing_tab`/`missing_header`) |
| 5 | that jam's `estado` is `BORRADOR` or `PUBLICADA`, its date is today or later, and no jam with a known `estado` falls between today and it (it is the upcoming jam) | `jam_not_editable` |
| 6 | `songId` is on exactly one `Catalogo` row (trimmed `id`), with a non-blank `titulo` and `artista` | `unknown_song` (and `Catalogo`'s own `missing_tab`/`missing_header`) |
| 7 | the jam's tab, when it exists, has every required header once | `missing_header`, `duplicate_header` |
| 8 | `songId` is not already in the tab (user approval J1) | `song_already_in_setlist` |

Then: when the tab does not exist it is inserted with the header row `posicion, id_tema, titulo,
artista, tono, Guitarra 1, Guitarra 2, Bajo, Batería, Voz, Armónica, Teclados, Otros`. The row goes
on `getLastRow() + 1`, at `posicion` = 1 + the largest whole-number `posicion` in the tab (invalid
cells ignored; 1 for an empty tab). Only the mapped columns are written, found by header as the
reads find them; each cell is set to the plain-text format `@` **before** its value, so Sheets
cannot turn a title such as `7/4` into a date. `posicion` is written as text (the read path
accepts it, `integerTextCell`). `titulo` and `artista` are the catalog's; `tono` is the request's
`key`, never `tono_default` (D-08); the seven slot cells and `Otros` are written empty (open,
D-18). The answer is `{"schemaVersion":1,"ok":true,"position":4,"title":"Crossroads",
"artist":"Eric Clapton"}`, `position` a JSON number.

Codes are English and never contain the passphrase. A Sheets failure after the checks (rare) is
`internal_error`; a tab created just before such a failure stays, empty, and reads as an empty
setlist.

### `checkSetlistWrite` (`admin-add-song-to-setlist`)

Request: `{"action":"checkSetlistWrite","passphrase":"…"}`. A write action, under the lock. A
**self-cleaning deploy check** for `addSong`'s write path (user approval L1 (a)), with no
repository function: it deletes a leftover `_prueba_lista` tab, creates `_prueba_lista` with the
jam tab header (the same function `addSong` uses), appends the marker row `1, zz-prueba-lista,
7/4, Prueba <ISO time>, Bbm` with `addSong`'s own append function, reads it back with
`buildSetlist`, deletes the tab and answers `{"schemaVersion":1,"ok":true}`. Since
`admin-set-key` (user approval L1 (a)) one step sits between the append and the read-back: the
marker row is found with `setKey`'s own finder (`findSongRow_`) and its key rewritten to `F#m` with
`setKey`'s own cell writer (`writeKeyCell_`), so the read-back must be the marker with key `F#m`.
A read-back that differs (a title turned into a date, a slot not open, the key not rewritten) is
`internal_error`, after the tab is deleted. `_prueba_lista` has no `Jams` row, so no read ever serves it. Only the glue between a
`Jams` row and its tab name is left to the Node tests.

### Row identity of setlist mutations (`admin-remove-song-from-setlist`, user decision R1 (a))

Every setlist mutation that changes an existing row (remove now; set key, reorder, lineup, assign
and clear later) **locates the row by its trimmed `id_tema`** in the jam's tab, never by
`posicion`. (`fecha`, `posicion`) stays the **read identity** (the order and the cache key);
`addSong` keeps `id_tema` unique within a tab. A renumber by one admin therefore never makes
another admin's write hit the wrong song. A tab where the id is on more than one row (a hand edit)
is refused with `duplicate_song`.

The scan is one shared function, `findSongRow_(sheet, columns, values, songId)` (since
`admin-set-key`): it returns the 1-based sheet row of the one data row whose trimmed `id_tema` is
`songId`, or throws `song_not_in_setlist` (none) or `duplicate_song` (more than one). It only
reads. `removeSong` (through `removeSetlistRow_`) and `setKey` both call it.

### `removeSong` (`admin-remove-song-from-setlist`)

Request: `{"action":"removeSong","passphrase":"…","date":"2026-10-31","songId":"crossroads"}`. A
write action, under the lock. Every check runs **before anything is written**, in this order:

| Order | Check | Code |
|---|---|---|
| 1 | `date` is a string, `YYYY-MM-DD`, a real calendar day | `invalid_date` |
| 2 | `songId` is a string matching `^[a-z0-9]+(-[a-z0-9]+)*$` | `invalid_song` |
| 3 | the `Jams` tab has exactly one row with that `fecha` | `unknown_jam`, `duplicate_date` (and the `Jams` tab's own `missing_tab`/`missing_header`) |
| 4 | that jam is the upcoming jam (the same rule as `addSong`; a `PUBLICADA` jam is allowed) | `jam_not_editable` |
| 5 | the jam's tab has every required header once | `missing_header`, `duplicate_header` |
| 6 | the tab exists and a row's trimmed `id_tema` equals `songId` | `song_not_in_setlist` |
| 7 | only one row has it | `duplicate_song` |

Then, with `p` the removed row's `posicion` read as the reads read it (`integerTextCell`; a whole
number of at least 1, otherwise null): when the row is the grid's last row (`getMaxRows()`) a row
is inserted after it first, because Sheets refuses to delete the only non-frozen row of a trimmed
tab; the row is deleted with `deleteRow`; and every other row whose `posicion` is a whole number
above `p` gets `posicion - 1`, written as plain text (`@` format, then the value). The renumbering
goes in **ascending `posicion` order**, batched: consecutive positions on consecutive rows are one
`setNumberFormat` and one `setValues` call (an app-built tab is one run whatever its length), so a
failure midway can leave a gap, which the reads tolerate, but never two rows with one position.
When `p` is null nothing is renumbered. Slots and `Otros` go with the deleted row. `Catalogo` is
never opened. The answer is `{"schemaVersion":1,"ok":true,"position":2}`, `position` the removed
`p` as a JSON number, or `null`.

Codes are English and never contain the passphrase. A Sheets failure after the checks (rare) is
`internal_error`.

### `checkSetlistRemove` (`admin-remove-song-from-setlist`)

Request: `{"action":"checkSetlistRemove","passphrase":"…"}`. A write action, under the lock. A
**self-cleaning deploy check** for `removeSong`'s write path, with no repository function: it
deletes a leftover `_prueba_lista` tab, creates it with the jam tab header, appends three marker
rows at 1, 2 and 3 (`zz-prueba-uno`, `zz-prueba-dos`, `zz-prueba-tres`) with `addSong`'s append
function, removes `zz-prueba-dos` with `removeSong`'s own row function (`removeSetlistRow_`),
reads the tab back with `buildSetlist`, deletes it and answers `{"schemaVersion":1,"ok":true}`.
Anything but exactly `zz-prueba-uno` at `"1"` and `zz-prueba-tres` at `"2"` is `internal_error`,
after the tab is deleted. It proves `deleteRow` and the renumbering on real Sheets.

### `setKey` (`admin-set-key`)

Request: `{"action":"setKey","passphrase":"…","date":"2026-10-31","songId":"crossroads","key":"Bb"}`.
A write action, under the lock. Every check runs **before anything is written**, in this order (the
same codes and order as `removeSong`, plus `invalid_key`):

| Order | Check | Code |
|---|---|---|
| 1 | `date` is a string, `YYYY-MM-DD`, a real calendar day | `invalid_date` |
| 2 | `songId` is a string matching `^[a-z0-9]+(-[a-z0-9]+)*$` | `invalid_song` |
| 3 | `key` is a string matching `^[A-G][#b]?m?$` | `invalid_key` |
| 4 | the `Jams` tab has exactly one row with that `fecha` | `unknown_jam`, `duplicate_date` (and the `Jams` tab's own `missing_tab`/`missing_header`) |
| 5 | that jam is the upcoming jam (the same rule as `addSong`; a `PUBLICADA` jam is allowed) | `jam_not_editable` |
| 6 | the jam's tab exists, then has every required header once | `song_not_in_setlist` (no tab), then `missing_header`, `duplicate_header` |
| 7 | exactly one row's trimmed `id_tema` equals `songId` (`findSongRow_`) | `song_not_in_setlist`, `duplicate_song` |

Then `writeKeyCell_` writes **only that row's `tono` cell**: `setNumberFormat('@')`, then
`setValue(key)`, in the spelling the admin picked (`A#` stays `A#`; nothing is normalized). No other
cell, row or tab is written, and `Catalogo` is never opened, so `tono_default` is never read or
written (D-08). The answer is `{"schemaVersion":1,"ok":true}`. Because the row is found by id, a
removal that renumbered the tab in between never misdirects the write. A published jam's new key
is what the next GET returns.

### `setSlotCount` (`admin-adjust-lineup`)

Request: `{"action":"setSlotCount","passphrase":"?","date":"2026-10-31","songId":"crossroads","instrument":"guitar","count":1}`.
A guarded write under the shared script lock. Before any write it validates, in order: real ISO
calendar date (`invalid_date`), canonical song id (`invalid_song`), one of `guitar`, `bass`,
`drums`, `vocals`, `harmonica`, `keyboards` (`invalid_instrument`), JSON integer count from zero
to two for guitar or one for the rest (`invalid_count`), unique editable jam (`unknown_jam`,
`duplicate_date`, `jam_not_editable`), tab and headers, and exactly one song row through
`findSongRow_` (`song_not_in_setlist`, `duplicate_song`). A missing song tab is
`song_not_in_setlist`; malformed headers use the existing `missing_header`/`duplicate_header`.

`presentSlotFields_` resolves non-`-` columns in header-mapped slot order. `planSlotCount_`
removes **last open column first**, or refuses `slot_filled` before writing if names would need
removing; restore reopens **first absent column first**. Same count writes nothing. Only changed
slot cells receive `setNumberFormat('@')` then `setValue('-')` or `setValue('')`; names, key,
position, title, `Otros` and `Catalogo` are untouched. Restoring the first guitar can change a
name's ordinal without moving its cell; future assignment/clear mutations must re-resolve ordinal
on the server and check the resolved cell's expected state.

Success rereads all seven cells, including concurrent names, and answers
`{"schemaVersion":1,"ok":true,"slots":{"guitar1":null,"guitar2":"-","bass":null,"drums":null,"vocals":null,"harmonica":null,"keyboards":null}}`.
Cells use trimmed display text: empty is `null`, absent is `"-"`, filled is a nonblank name.
The extended `checkSetlistWrite` removes marker guitar 2, removes harmonica then restores it,
using the same planner/writer and checking readback; it expects only guitar 2 absent before
cleanup. It never changes a real jam. This extension awaits deployment/live evidence.

### `assignSlot` (`admin-assign-musician`)

Request: `{"action":"assignSlot","passphrase":"?","date":"2026-10-31","songId":"crossroads","instrument":"guitar","ordinal":1,"name":" Tincho "}`.
The guarded write uses the shared lock and validates before writing in this order: calendar date
(`invalid_date`), canonical song id (`invalid_song`), instrument and positive integer ordinal
(`invalid_slot`), normalized musician name (`invalid_name`), unique editable jam, tab and headers,
unique song row, active slot by U1 ordinal (`slot_not_in_lineup`), and empty target (`slot_taken`).
Names are trimmed and whitespace runs collapse to one space; they must be at most 40 UTF-16 units,
contain a letter or digit, contain no control characters, `;`, `(` or `)`, and must not start with
`=`, `+`, `-` or `@`.

The ordinal is the 1-based position among the requested instrument's non-`-` columns, in canonical
header mapping order, not the drawn open-first order. Thus guitar ordinal 1 targets Guitarra 2 when
Guitarra 1 is absent. An occupied cell is never overwritten. Success formats and writes exactly that
one cell as plain text and answers
`{"schemaVersion":1,"ok":true,"column":1,"name":"Tincho"}`, where `column` is the zero-based
canonical seven-slot array index and `name` is the normalized spelling. Every rejection is
write-free. It never opens `Catalogo`. The extended `checkSetlistWrite` assigns and reads back a
marker in its disposable tab before deleting the tab; live verification is deferred until the app
implementation is complete.

### `clearSlot` (`admin-clear-slot`)

Request: `{"action":"clearSlot","passphrase":"?","date":"2026-10-31","songId":"crossroads","instrument":"guitar","ordinal":1,"expectedName":"Tincho"}`.
The guarded write uses the shared lock and validates the calendar date, canonical song id,
instrument, positive default-lineup ordinal and nonblank expected name before checking the unique
editable jam, tab, headers and song row. It resolves the 1-based ordinal among active non-`-`
columns with U1. An empty target returns `slot_empty`; a cell whose trimmed display text differs
from `expectedName` returns `slot_changed`. Both rejections are write-free. Success clears exactly
that cell as plain text, rereads all seven slot fields and returns `{schemaVersion, ok, column,
slots}`. Existing Sheet names are compared as-is and do not pass through the new-assignment name
validator. The extended `checkSetlistWrite` assigns and clears a disposable marker musician, checks
the open-slot readback and removes the temporary tab in `finally`. Deployment/live validation is
deferred until app implementation is complete.

### `moveSong` and `checkSetlistMove` (`admin-reorder-songs`)

`moveSong` takes `{date, songId, toPosition}` and returns the moved song's actual 1-based position.
It validates the positive integer target before opening the jam, then verifies an editable jam,
required headers and a unique song row by `id_tema`. It rejects a malformed moved position or any
duplicate whole position as `unordered_setlist`. The final ordered valid rows are numbered 1..n;
contiguous changed rows are batched into one range write. When sheet row order differs from position
order, the moved row is parked at max+1, decreased positions are written low to high, increased
positions high to low, and the moved row is committed last. This avoids duplicate positions after
each write. The action never opens `Catalogo` and never moves Sheet rows. `checkSetlistMove` applies
the algorithm to a disposable `_prueba_lista` tab, verifies the resulting `buildSetlist` order, and
removes the marker in `finally`. Live verification is deferred until app implementation is done.

### Client write path (`AdminWriter`)

Every admin mutation repository writes through the internal `AdminWriter(AppsScriptPostTransport,
AdminCredentialStore)` in `:core:data` `admin/`: `suspend fun write(action, fields):
WriteOutcome`. It reads the stored passphrase (with none stored it returns `AccessRefused` and
sends nothing), builds a `JsonObject` with `action`, `passphrase` and the fields (a field named
`action` or `passphrase` is a `require` failure), encodes it with `AppsScriptEnvelope.json`, POSTs
it and decodes with `decodeOk`:

| Answer | `WriteOutcome` |
|---|---|
| `ok` | `Done` |
| `invalid_passphrase`, `passphrase_not_set` | `AccessRefused` |
| `rate_limited`, `busy`, `unknown_action`, `NotConfigured`, an HTML page, any invalid answer | `Unavailable` |
| `Offline` | `Offline` |
| any other service code | `Rejected(code)` |

`AdminWriter` never saves or clears the stored passphrase, whatever the answer (user decision
W3): a refused write leaves the device in admin mode, and the admin recovers on Info with "Salir
del modo admin" and "Entrar como admin". It never logs.

`AdminWriter.send(action, fields): AdminAnswer` (`admin-add-song-to-setlist`) is the same path for
an action whose answer carries a payload: `AdminAnswer.Ok(body)` with the whole answer object
(decoded by `AppsScriptEnvelope.decodeOkObject`, the same checks as `decodeOk`), or
`AdminAnswer.Refused(outcome, failure)` with the `WriteOutcome` above (never `Done`) and the
`DataFailure` behind it (null when nothing was sent). `write` is `send` with `Ok` mapped to `Done`.

### How the admin reads a draft (`admin-add-song-to-setlist`)

The admin read **is** the jams refresh. `DefaultJamsRepository.refresh()` with a passphrase stored
POSTs `readJams` through `AdminWriter.send` and stores the answer in place of the GET's, mapped
with `JamsMapper.map(rows, includeDrafts = true)`: a draft's setlist and `setlistError` are mapped
like a published jam's, and a draft with `missing_tab` is an empty available setlist (no song
added yet). A separate draft read would be wiped by the next GET, which replaces the whole cache.

| Admin read answer | Refresh |
|---|---|
| `ok` | stores it; `JamsRefreshOutcome.Updated(…, adminRead = true)`, log line ends ` (admin read)` |
| `AccessRefused` (`invalid_passphrase`, `passphrase_not_set`) | remembers the refused passphrase **in memory** for this process (compared, never persisted or logged) and falls back to the anonymous GET; the next refresh goes straight to the GET until another passphrase is stored, so a stale device spends one guess per process, not one per refresh (W2) |
| `Offline`, `Unavailable` (`busy`, `rate_limited`, an HTML page, a body with no `jams`), `Rejected` | a failed refresh (`Failed(…, adminRead = true)`, recorded in `sync_state`); the cache, draft songs included, is untouched and the GET is **not** tried, because it would wipe the draft the admin is editing |

With no passphrase stored the refresh is the anonymous GET, as before. Musician screens keep
reading `Jam.setlistForMusicians()`, which withholds every draft whatever the cache holds.

### Client setlist mutations (`SetlistRepository`)

`:core:data` `setlist/` holds the public `SetlistRepository` (`addSong(jamDate, songId, key):
AddSongOutcome`, `observeAdds(): Flow<List<SetlistAdd>>`, `dismiss(id)`), `AddSongOutcome`
(`Added(position)`, `NotAdded(reason: WriteOutcome)`, never `Done`), `SetlistAdd(id, jamDate,
songId, title, artist, key, state)` with `State.Sending`/`State.Failed(reason)`, and the internal
`DefaultSetlistRepository(AdminWriter, SetlistDao, CatalogDao, DataScope)`:

1. The title and artist come from the cached catalog (`CatalogDao.song(id)`). A song not cached
   is `NotAdded(Rejected("unknown_song"))`: a `Failed` entry, no request.
2. A `Sending` entry is published, and the write is queued in `DataScope` behind a fair mutex, so
   writes go one at a time in call order and the caller's cancellation does not cancel one.
3. `ok` with a JSON-number `position` above 0 and non-blank `title`/`artist` → the song and seven
   open slots (`Lineup.DEFAULT_INSTRUMENTS`, columns 0..6) are inserted in one transaction by
   `SetlistDao.insertSetlistSong` (IGNORE: a row a refresh already brought wins; only while the
   cached jam's setlist is `AVAILABLE`), the entry is removed, and the answer is `Added`. A
   malformed `ok` is `NotAdded(Unavailable)`.
4. Anything else → the entry becomes `Failed(reason)` and **nothing** is written to Room.
   `dismiss(id)` removes a failed entry. Entries live in memory and are lost with the process.

Removal (`admin-remove-song-from-setlist`, Part A): `removeSong(jamDate, songId):
RemoveSongOutcome` (`Removed` or `NotRemoved(reason: WriteOutcome)`, never `Done`),
`observeRemoves(): Flow<List<SetlistRemove>>` and `SetlistRemove(id, jamDate, songId, title,
state)` with `State.Sending`/`State.Failed(reason)`. The internal `SetlistRemovals` (built by the
repository, no binding of its own) holds the entries, the request and the cache mirror:

1. Under the same order mutex as the adds, the title is resolved as `jam_song_resolved` does (the
   cached catalog's, else the tab's copy from `SetlistDao.songsOf`, else the id) and a `Sending`
   entry is published; the write queues on the same write mutex, so adds and removes are sent one
   at a time in call order. Ids share one counter with the adds; `dismiss(id)` removes a failed
   entry of either kind.
2. `AdminWriter.write("removeSong", {date, songId})`. `Done` → `SetlistDao.removeSetlistSong` (one
   transaction, only while the cached setlist is `AVAILABLE` and exactly one cached song has that
   id: the song, its slots and extras are deleted explicitly, and every later song with its own
   slots and extras is reinserted one position up, since positions are part of the primary keys),
   the entry is removed, `Removed`.
3. `Rejected("song_not_in_setlist")` → the same cache mirror (the Sheet no longer has the song),
   and the entry turns `Failed`; `NotRemoved`.
4. Anything else → `Failed(reason)` and the cache is untouched. A Room `SQLException` in the
   mirror never changes the outcome; the next refresh fixes the cache.

Key change (`admin-set-key`, Part A): `setKey(jamDate, songId, key): SetKeyOutcome` (`KeySet` or
`NotSet(reason: WriteOutcome)`, never `Done`), `observeKeyChanges(): Flow<List<KeyChange>>` and
`KeyChange(id, jamDate, songId, title, key, state)` with `State.Sending`/`State.Failed(reason)`.
The internal `SetlistKeyChanges` (built by the repository like `SetlistRemovals`) holds the entries,
the request and the cache mirror:

1. Under the same order mutex, the title is resolved by the same rule as a removal's (the shared
   internal `displayedTitle`) and a `Sending` entry is published; the write queues on the same
   write mutex, after any queued add or removal. Ids share the one counter; `dismiss(id)` removes a
   failed entry of any kind.
2. `AdminWriter.write("setKey", {date, songId, key})`. `Done` → **first**
   `SetlistDao.updateKey(date, songId, key)` (one `UPDATE` statement on `jam_song.key`, only while
   the cached setlist is `AVAILABLE` and exactly one cached song of that date has the id; a Room
   `SQLException` is swallowed), **then** the entry is removed, `KeySet`. Updating first keeps the
   confirmed key in place before the pending one goes.
3. Anything else, `song_not_in_setlist` included → `Failed(reason)` and Room is untouched; the
   failed entry is the revert. `catalog_song` is never written.

A `Sending` entry's `key` is the only source of an optimistic key (user decision O1, drawn by
Part B).

Reorder (`admin-reorder-songs`): `moveSong(jamDate, songId, toPosition): MoveSongOutcome`,
`observeMoves(): Flow<List<SetlistMove>>`, with `Moved`/`NotMoved(reason)` and entries in
`Sending`/`Failed(reason)` state. It shares the repository's mutation ids and FIFO write barriers.
On a valid confirmed response, the Room mirror rekeys changed songs and their slots/extras in one
transaction before removing the entry. The admin presenter overlays Sending moves in call order and
renumbers displayed rows; failed entries leave cached order in place and create dismissible cards.
Musicians never receive the overlay. A move target is absolute, so a replay has the same result.

`addSong`, `removeSong`, `setKey` and `moveSong` are plain repository functions, usable with no UI (D-13);
`action-contract-registry` registers them later.

## Quotas

From Google's "Quotas for Google Services" page (developers.google.com/apps-script/guides/services/quotas,
page last updated 3 September 2026, read 1 October 2026). Google states all quotas may change
without notice. Limits for a consumer (gmail.com) account:

| Limit | Value |
|---|---|
| Script runtime | 6 min per execution |
| Simultaneous executions per user | 30 |
| Simultaneous executions per script | 1,000 |
| Triggers total runtime | 90 min per day (triggers only; this web app uses no trigger) |
| URL Fetch calls | 20,000 per day (not used by this script) |

The page lists no daily runtime or request cap for web app executions. With "execute as me",
every anonymous call runs as the owner, so the binding limit for jam night is **30 simultaneous
executions for the owner's account**, shared with any other script the owner runs. A read lasts
about as long as its latency (measured at deployment, see `technical-discovery.md`), and the app
caches the catalog, so 20–60 readers stay well under it unless they all refresh in the same
second. A call over the limit fails with "There are too many scripts running simultaneously for
this Google user account" (Google's wording). It is thrown outside `doGet`, so it does not arrive
as a contract error; what an anonymous caller receives then (most likely an HTML page) is not
verified. The client should treat any non-JSON body as a transport failure and keep its cache.

**Jam-night polling** (`live-refresh-during-jam`, user approval L1, 6 October 2026). While Próxima
jam is visible and "now" is between 30 min before a jam's start and 4 h after it (Buenos Aires),
each phone reads the jams every 30 s, every 60 s after a failure, measured from when the previous
answer arrived (so one phone never overlaps itself), with a random delay of up to 30 s when the window
opens. For an admin each read is the guarded `readJams` POST; for everyone else the `jams` GET.
Estimate for 40 phones and a 3 s call: about 4 simultaneous executions on average against the 30
per user, and at most about 21,600 reads in one night if every screen stays on the whole window. A
call refused over the limit is a failed refresh: the phone keeps its cache and waits 60 s. The
interval is one constant (`LiveRefresh.INTERVAL`) if the estimate proves wrong.


Lineup change (`admin-adjust-lineup`): `setSlotCount(jamDate, songId, instrument, count)` returns
`SetSlotCountOutcome.SlotCountSet` or `NotSet(reason)`; `observeLineupChanges()` exposes
`LineupChange` entries (`Sending`/`Failed`) sharing all mutation ids and `dismiss`. It runs in
`DataScope` with the same order/write mutexes, surviving caller cancellation. Out-of-range counts
fail locally with `invalid_count`, without a POST. `AdminWriter.send` supplies the guarded request.
A successful payload must contain exactly seven slot fields, each null, `-`, or a nonblank string;
malformed success is `Unavailable`. First `SetlistDao.replaceSlots` transactionally mirrors the
server's columns for exactly one cached (date, songId) while `AVAILABLE`, then the entry disappears.
A cache SQLException waits for refresh; failure never changes Room. Only Sending entries overlay
the admin's lineup (latest per instrument), before filters, counts, strip and panel; musicians
never see the overlay. One `saveStatus` covers key and lineup writes together.

Assignment (`admin-assign-musician`): `assignSlot(jamDate, songId, instrument, ordinal,
musicianName)` returns `AssignSlotOutcome.Assigned` or `NotAssigned(reason)`; `observeAssignments()`
exposes `Assignment` entries with `Sending`/`Failed`. The action receives the validated normalized
name and 1-based `SlotPosition`; the server resolves U1 again under the lock. A valid success body
must match `schemaVersion`, the cached canonical slot index and normalized name before
`SetlistDao.assignOpenSlot` updates one still-open slot for the uniquely cached song. Pending
assignment overlays the admin's matching slot before the strip, panel, filter and count projections;
failure reverts and produces a dismissible named card. Suggestions read cached upcoming and past jams,
including current `Otros` names, but those extras are never assignable slots. The user's deferred
shared deployment means this is local behavior and has not been live-verified yet.

### `publishSetlist` and `checkPublish` (`admin-publish-setlist`)

`publishSetlist` is a guarded, locked write with `{date}`. It rejects invalid, unknown, duplicate or non-upcoming jams before writing; a retry for an already `PUBLICADA` jam succeeds idempotently. A draft must have a valid Jams row and at least one setlist row. The server writes only that row's `estado` cell, flushes, and reads back `PUBLICADA` before returning `{ok:true, alreadyPublished:false}`; a retry returns `alreadyPublished:true` without writing. Missing tabs or no songs return `empty_setlist`. `checkPublish` exercises the same write/read-back against its temporary `_prueba_publicar` Jams tab and always removes that tab; it never changes a real jam. The action is in local source; deployment and live checks are deferred to the user's shared four-feature deployment.

The public `SetlistRepository.publishSetlist(jamDate)` mutation returns `PublishOutcome.Published(alreadyPublished)` only when the server confirms its result. Room changes only after confirmation and only for a cached `DRAFT` jam with an `AVAILABLE` setlist. It queues with other writes and exposes `SetlistPublish` Sending/Failed state for the admin status block; failure logs one safe `BluesJam` line and never optimistically marks the jam published.

### `addExtraParticipant` and `removeExtraParticipant` (`admin-edit-extra-participants`)

Both are passphrase guarded and run under the script lock. They validate date and `songId`, require the unique editable jam and unique song row, validate the `Otros` header, parse its semicolon-separated `Nombre (instrumento)` entries, and write only that cell as plain text. `Otros` remains outside the lineup and never contributes an open slot.

`addExtraParticipant` takes `{date, songId, name, instrument}`. Both values are trimmed, whitespace is collapsed, each is at most 40 UTF-16 units, and neither may contain controls, `;`, `(` or `)`. A name also cannot start with `=`, `+`, `-` or `@` and must contain a letter or digit. The list is limited to 20 entries. Success returns `{ok:true, schemaVersion:1, extras:[{name,instrument},...]}` in canonical order; identical entries are allowed. Errors include `invalid_extra`, `invalid_name` and `extra_limit`.

`removeExtraParticipant` takes `{date, songId, ordinal, expectedName, expectedInstrument}`. The 1-based ordinal and exact normalized pair are compared with the list read under the lock. A mismatch returns `extra_changed` without a write; it never removes a different equal pair. Success returns the same canonical `extras` payload. Both actions refuse malformed current lists rather than rewriting data they cannot interpret.

The public `SetlistRepository` functions share the mutation id, FIFO order/write locks and `AdminWriter`. Only a valid success payload is mirrored by a single Room transaction into the uniquely cached available song; otherwise Room stays unchanged and refresh repairs a cache mirror failure. Sending entries optimistically alter only the admin's `Otros` projection; failures revert and remain dismissible. The local admin flag draws controls only; server authorization remains authoritative.

The self-cleaning `checkSetlistWrite` deploy check now exercises extra append/readback, stale ordinal refusal without a cell write, current ordinal removal/readback, the 20-entry limit and final `Otros` cell value on its disposable marker tab. It deletes the marker tab in `finally`; it does not touch a real jam. This check has been exercised by the local Apps Script harness, but no live deployment check was run for this implementation.
