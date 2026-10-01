# Apps Script API

The contract between the Apps Script web app (`backend/apps-script/`) and the Android client in
`:core:data`. The Sheet behind it is described in `sheet-schema.md`. Deployment steps are in
`backend/apps-script/README.md`.

Current routes: one read, `catalog` (`apps-script-read-endpoint`). Jams and setlists come with
`apps-script-jams-read-endpoint`; writes and the passphrase check with `apps-script-write-auth`.

## Transport

- **URL:** the deployment's `/exec` URL. It is not committed; the client reads it from the
  git-ignored `local.properties` key `bluesjam.appsScriptUrl` (wired by
  `catalog-repository-cache`). Redeploying as a *new version* of the same deployment keeps the
  URL; a *new deployment* changes it.
- **Method:** `GET`, with the route in the query: `<url>?resource=catalog`. Matching is exact and
  case-sensitive.
- **Redirect:** the web app answers with a `302` to a one-time URL on
  `script.googleusercontent.com`. The client must follow redirects. OkHttp does by default; `curl`
  needs `-L`. (Google, "Content Service" guide, checked 1 October 2026.)
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
```

Error:

```json
{"schemaVersion":1,"error":{"code":"missing_header","message":"Catalogo is missing required headers: tono_default"}}
```

| `code` | When |
|---|---|
| `unknown_resource` | `resource` missing, empty, or not a route (`config`, `CATALOG`, `jams` today) |
| `missing_tab` | The route's tab does not exist (`Catalogo`) |
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
- `Config` is never opened and never returned, by this or any read route.

Samples, both produced by `buildCatalog` and asserted equal to its output by
`backend/apps-script/test/catalog.test.js`:

- [`api-samples/catalog-seed.json`](api-samples/catalog-seed.json): the response for
  `sheet-seed/Catalogo.csv`, 13 songs, every optional field `null`. The mapper's happy path.
- [`api-samples/catalog-edge.json`](api-samples/catalog-edge.json): the response for a messy tab
  (`test/helpers/edge-input.js`). Four songs: every optional field set with a numeric
  `songsterr_id`; none set; `rápido`/`fácil` with a padded string id; and `sin-tono`, with a `null`
  `defaultKey`, tempo `Rápido` and `songsterrId` `"12.5"`, which the mapper must reject.

To check a live response: `node backend/apps-script/tools/check-response.js <file>` (exit 0 when
valid). It checks shape only, like the samples' test.

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
