# Apps Script API

The contract between the Apps Script web app (`backend/apps-script/`) and the Android client in
`:core:data`. The Sheet behind it is described in `sheet-schema.md`. Deployment steps are in
`backend/apps-script/README.md`.

Current routes: two reads, `catalog` (`apps-script-read-endpoint`) and `jams`
(`apps-script-jams-read-endpoint`), and one POST action, `checkPassphrase`
(`admin-passphrase-login`). Writes, the guard on writes and the admin's read of a draft come with
`apps-script-write-auth`.

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
`?resource=jams`. How the admin reads a draft is not part of this route; it belongs to
`apps-script-write-auth` and must use POST with the passphrase in the body.

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

`POST <url>` with a JSON object body, `{"action": "<name>", …}`, content type
`application/json; charset=utf-8`. Served by `doPost`/`handlePost` in `src/Post.js`, the only file
that opens `Config`. The answer uses the same envelope (HTTP 200, `schemaVersion` 1, an `error` key
on failure). Actions are matched exactly; an unknown or missing one is `unknown_action`, whose
message ends `Known: checkPassphrase` (the list of the deployed `Post.gs`; a deploy check).

### `checkPassphrase` (`admin-passphrase-login`)

Request: `{"action":"checkPassphrase","passphrase":"…"}`. The client trims the passphrase before
sending; the server compares it **exactly** (case-sensitive) with the trimmed `valor` of the
`Config` row whose `clave` is `passphrase` (`sheet-schema.md`, read with `getDisplayValues`).

| Case | Body |
|---|---|
| match | `{"schemaVersion":1,"ok":true}` |
| body missing, not JSON, or not a JSON object | error `invalid_request` |
| `action` missing or unknown | error `unknown_action` (`… Known: checkPassphrase`) |
| `Config` tab, its `clave`/`valor` headers or the `passphrase` row missing, or `valor` blank after trimming | error `passphrase_not_set` |
| `passphrase` missing, not a string, or not equal | error `invalid_passphrase` |
| any other exception | error `internal_error` |

The stored value is read **before** comparing, so an unset passphrase rejects every attempt
(fail closed) and a blank submission can never match a blank cell. No message ever contains the
stored or the submitted value. There is no rate limit (`risks-and-open-questions.md`).
`readPassphrase_(spreadsheet)` and `passphraseMatches_(spreadsheet, submitted)` are named for
`apps-script-write-auth`, which adds write actions to `ACTIONS` and guards them with the same check.

Client (`:core:data`, `admin/`): `DefaultAdminSession.logIn` POSTs through
`AppsScriptPostTransport` (the same `OkHttpAppsScriptTransport` instance), decodes with
`AppsScriptEnvelope.decodeOk` and maps: `ok` → store, `Success`; `invalid_passphrase` →
`WrongPassphrase`; `Offline` → `Offline`; anything else (`NotConfigured`, `passphrase_not_set`,
`unknown_action`, an HTML page from a deployment without `doPost`, any invalid answer) →
`Unavailable`. Nothing is stored unless the server said `ok`.

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
