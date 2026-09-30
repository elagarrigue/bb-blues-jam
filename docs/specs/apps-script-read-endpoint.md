# Feature Implementation Spec: Expose a read endpoint for the song catalog

## Source Feature

- `id`: apps-script-read-endpoint
- `area`: backend
- `depends_on`: sheet-schema-definition (`accepted`)
- `status`: not_started (at planning time, 30 September 2026, HEAD `60a75dd`)
- `source`: `feature_list.json`

**Needs user answers before the manual half (U1–U6, at the end).** The repo half (script source,
Node tests, contract docs) can proceed on the recommended defaults. Deployment is the user's step
only: no agent can reach the Google account, the Sheet or the Apps Script editor.

## Goal

A GET request to the deployed Apps Script web app with `?resource=catalog` returns the `Catalogo`
tab as JSON. The JSON uses English field names and carries cell text that is trimmed and
normalized. A song with empty optional fields serializes as `null` for each of them. The script
source lives in the repo. Its pure logic is unit-tested on the JVM-free local Node runtime, and the
JSON contract is committed with sample responses. `catalog-repository-cache` uses those samples as
mapper fixtures. The user deploys the script and returns evidence: a response that the committed
checker accepts, and measured latency.

## Non-Goals

- **Jams, jam tabs and the upcoming jam are not served here.** The feature's title, behavior and
  verification all name only the catalog. Serving jams adds date, time and position normalization,
  jam-tab parsing and the DRAFT-hiding rule, which makes it a second slice. No feature in
  `feature_list.json` serves jams today, so this is a gap: see U5.
- No doPost, no write, no passphrase check. Any read of `Config`, even for validation, belongs to
  `admin-passphrase-login` and `apps-script-write-auth`.
- No row-level validation in the script: keys, id alphabet, enum values, duplicate ids or tag
  splitting. The Kotlin mapper in `catalog-repository-cache` owns interpretation (Decision P2).
- No Kotlin, Gradle, `init.sh`, Konsist or `feature_list.json` change. No Android client and no
  URL wiring into the app.
- No `CacheService` or other server-side caching. Measure first; see Risks.
- No enrichment fields (`mbid`, `artistArea`, `deezerTrackId`, `previewUrl`, `artworkUrl`). They
  are not in the Sheet (D-09), so the endpoint never emits them.

## Job Story

When I build the catalog cache,
I want a stable, documented JSON response for the catalog, with committed samples,
so I can write and test the mapper without access to the private Sheet.

## Users And Permissions

- Anyone holding the URL can read the catalog anonymously. The catalog is a public repertoire;
  nothing in it is private. The script runs as the Sheet owner, so the Sheet itself stays private.
- **`Config` is never opened by any code path in this slice.** A Node test enforces this (T3).
- Drafts are not affected, because no jam data is served.

## Acceptance Scenarios

1. **Catalog read.** Given the deployed script and a `Catalogo` tab, when
   `GET <url>?resource=catalog` is called, then the response is `200` with
   `{"schemaVersion":1,"songs":[…]}`. Songs keep Sheet row order, and each song has exactly the 8
   keys below.
2. **Missing optional fields.** Given a song whose `tempo`, `etiquetas`, `dificultad` and
   `songsterr_id` are empty, or whose optional column is missing entirely, then each of those keys
   is present with value `null`. This holds for every seed song.
3. **Typed and messy cells.** Given `songsterr_id` stored as the number `12345`, a title with
   surrounding spaces, fully blank rows, reordered columns and an extra unknown column, then the
   response carries `"12345"` and the trimmed title, skips the blank rows, and ignores the extra
   column.
4. **Structural error.** Given `Catalogo` lacks the `tono_default` header, or the tab is missing,
   then the body is `{"schemaVersion":1,"error":{"code":"missing_header"|"missing_tab","message":…}}`.
5. **No Config.** Given `?resource=config`, a missing `resource` or any other value, then the body is
   an `unknown_resource` error. No response contains a `Config` value.

## Decisions (planner, with reasoning; the user may overrule)

**P1 — Location and layout.** The source lives in `backend/apps-script/`, outside Gradle.
`settings.gradle.kts` includes only the listed modules, so Konsist, detekt, ktlint and lint never
see it. Files use `.js` (clasp-compatible, and Node can `require` them). The Apps Script editor
names them `.gs` when pasted. Each source file ends with a guarded export,
`if (typeof module !== 'undefined') { module.exports = {…}; }`. This is a no-op in Apps Script,
where `module` is undefined, and lets Node load the same file with no build step.

**P2 — The script transports, the Kotlin mapper interprets.** The script does four things only:
- matches headers by trimmed exact name;
- renames them to English;
- trims cells and turns typed cells into text;
- rejects structural problems (missing tab, missing required header, duplicate mapped header).

Values stay in Sheet vocabulary: `"tempo":"rápido"`, and `"tags":"shuffle, 12 compases"` as the raw
cell. Keys, id alphabet, enums, tag splitting, `songsterrId` parsing and duplicate ids are left to
`catalog-repository-cache`. Reasons:
1. One interpreter. The Kotlin one runs in the gate against the domain types, and the JS one would
   not.
2. The seed CSVs, which are P3 fixtures, are in Sheet vocabulary. The Kotlin mapper then parses the
   same vocabulary from CSV fixtures and from JSON.
3. The script stays small enough to paste by hand.

This is a deliberate reading of the `sheet-schema.md` **Mapper rules** line
"`apps-script-read-endpoint` / `catalog-repository-cache`". The endpoint enforces trimming, exact
header matching and typed-cell normalization. Everything else is the mapper's job (doc update, task
7).

**P3 — Text columns use display values; integer columns use raw values.** The script reads the
data range once with `getDisplayValues()` and once with `getValues()`:
- **Text fields** (`id`, `titulo`, `artista`, `tono_default`, `tempo`, `etiquetas`, `dificultad`)
  take the display string, trimmed. What the admin sees is what musicians get. Sheets'
  auto-conversion of something like `7/4` to a date cannot then leak a `Date` object.
- **`songsterr_id`** takes the raw value:
  - an integral number becomes its decimal string (`12345` → `"12345"`, never `"12.345"` from an
    es-AR number format);
  - a non-integral number becomes `String(n)`, which the mapper rejects;
  - a string is trimmed.

The date, time and position normalizers in **Reading cells** (`fecha`, `hora`, `posicion`) belong
to the jam slice (U5), and are not written here.

**P4 — Field mapping and null rule.**

| Header | Field | Header required |
|---|---|---|
| `id` | `id` | yes |
| `titulo` | `title` | yes |
| `artista` | `artist` | yes |
| `tono_default` | `defaultKey` | yes |
| `tempo` | `tempo` | no |
| `etiquetas` | `tags` | no |
| `dificultad` | `difficulty` | no |
| `songsterr_id` | `songsterrId` | no |

- Every song object has all 8 keys, in this order. Every value is a string or `null`.
- A cell that is empty after trimming, or a missing optional column, gives `null`. A missing
  **required header** is an error. An empty required **cell** is emitted as `null` for the mapper
  to reject, because row validity is P2's concern.
- A row whose 8 mapped cells are all empty is skipped. Unmapped columns are ignored.
- Duplicate mapped header → `duplicate_header`. Row 1 is the header row. An empty tab →
  `missing_header`. A tab with only headers → `"songs":[]`.

**P5 — Envelope and errors.** Success is `{"schemaVersion":1,"songs":[…]}`. Error is
`{"schemaVersion":1,"error":{"code":…,"message":…}}`, with these codes:
- `unknown_resource`
- `missing_tab`
- `missing_header`
- `duplicate_header`
- `internal_error`, for any uncaught exception, whose message is the exception message.

`message` is English and for logs only. The app maps `code` to Spanish copy later.

`ContentService` cannot set an HTTP status, so **every response is HTTP 200 and the client must
check for `error`**. The web app answers with a 302 to `script.googleusercontent.com`. OkHttp
follows it by default; `curl` needs `-L`. Both facts go in the contract doc for
`catalog-repository-cache`.

**P6 — Router.** `doGet(e)` is a thin wrapper around `handleGet(params, spreadsheet)`, which takes a
spreadsheet-like object (`getSheetByName`) and returns a plain object. `doGet` passes
`SpreadsheetApp.getActiveSpreadsheet()` and serializes the result with
`ContentService.createTextOutput(JSON.stringify(body)).setMimeType(ContentService.MimeType.JSON)`.
The route table is exactly `{ catalog }`. Later slices add `jams` and the login check to it.

**P7 — Container-bound script, copy-paste by default (U1).** The script is created from the Sheet
(Extensions → Apps Script), so `getActiveSpreadsheet()` needs no Sheet ID in the repo. With three
small files, copy-paste is cheaper than installing clasp, enabling the Apps Script API and running
`clasp login`. clasp stays possible. `backend/apps-script/.gitignore` ignores `.clasp.json` and
`.clasprc.json` either way, so a later switch cannot commit the script id or OAuth tokens. clasp
keeps tokens in the user's home directory, never in the repo.

**P8 — Manifest.** `appsscript.json` sets:
- `timeZone` `America/Argentina/Buenos_Aires`;
- `runtimeVersion` `V8`;
- `exceptionLogging` `STACKDRIVER`;
- `oauthScopes` `["https://www.googleapis.com/auth/spreadsheets.currentonly"]`, the least privilege
  that still allows the later write slices on the bound Sheet;
- `webapp` `{ "executeAs": "USER_DEPLOYING", "access": "ANYONE_ANONYMOUS" }`.

Why these access settings:
- **Execute as the owner**, so that anonymous callers can read a Sheet that is not shared.
- **Access "Anyone"** is forced by D-11. The app has no Google sign-in, and "Anyone with a Google
  account" would need OAuth in the app.
- **All executions count against the owner's quotas.**

**P9 — Node tests stay out of the gate (U4).** Tests use Node's built-in runner (`node:test`,
`node:assert/strict`). There is no `package.json`, no npm install and no `node_modules`. Node
v24.13.1 is installed on this machine, but it is not a project toolchain. Adding it to `init.sh`
would change verification and make the gate depend on Node, so it waits for the user's approval.
Revisit at `apps-script-write-auth`, where the security-critical passphrase check makes gating the
JS worth it.

**P10 — Contract artifacts.**
- `docs/apps-script-api.md` is the contract: routes, envelope, field table, null rule, error codes,
  HTTP-200 and redirect notes, deployment facts and quotas.
- `docs/api-samples/catalog-seed.json` is the exact response the script builds from
  `docs/sheet-seed/Catalogo.csv`, which is P3 of `sheet-schema-definition`. A test asserts deep
  equality.
- `docs/api-samples/catalog-edge.json` is the response for the inline edge input in T2: all optional
  fields filled, including a numeric `songsterr_id`, one song with none, and one `null` required
  cell. `catalog-repository-cache` gets both samples as mapper fixtures: one for the happy path, one
  that the mapper must partly reject.
- There is no JSON Schema. The Kotlin side has no validator, and the checker below covers the shape.

**P11 — URL storage (U3).** The URL is not a secret, and draft protection will come from the
passphrase, not from obscurity. Still, it is not committed in this slice. Recommendation for
`catalog-repository-cache`: a `bluesjam.appsScriptUrl` key in the git-ignored `local.properties`,
exposed as a `BuildConfig` field. The user keeps it privately until then.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md`: next ready feature, and the fact that deployment is the user's step.
- `feature_list.json`: this entry, plus `catalog-repository-cache`, `admin-passphrase-login`,
  `apps-script-write-auth`, the admin mutations and `past-jams-list`. Nothing serves jams.
- `docs/sheet-schema.md`: **Reading cells**, `Catalogo` table, Type-to-Sheet mapping, Mapper rules,
  and the "never return Config" rule.
- `docs/risks-and-open-questions.md`: cell types, drafts, `Jams`/tab mismatch, quotas as assumption 6.
- `docs/user-and-access-model.md`, `docs/technical-discovery.md`.
- `core/model/.../Song.kt`, `Tempo.kt`: 8 Sheet-backed fields plus 5 enrichment fields.
- `docs/sheet-seed/*.csv`: 13 songs, no optional values, no quoted fields.
- `.claude/skills/architecture/SKILL.md`, `settings.gradle.kts`, `init.sh`, root `.gitignore`.
- Toolchain: `node` v24.13.1 and `npm` present; `clasp` absent.

### Current Gaps

- No `backend/` directory, no JS anywhere outside `docs/design/` exports.
- No agent can observe the real Sheet. Its headers and cell types are known only from the user.

## Expected File Changes

- `backend/apps-script/appsscript.json`: create (P8).
- `backend/apps-script/src/Normalize.js`: create. `textCell(display)`, `integerTextCell(raw)`, and
  `isBlank`.
- `backend/apps-script/src/Catalog.js`: create. `CATALOG_FIELDS`, and
  `buildCatalog(displayRows, rawRows)`, which returns `{songs}` or throws a `ContractError(code, message)`.
- `backend/apps-script/src/Code.js`: create. `doGet`, `handleGet`, and the route table.
- `backend/apps-script/test/helpers/csv.js`: create. A small RFC 4180 parser for the seed.
- `backend/apps-script/test/helpers/contract.js`: create. `checkCatalogResponse(obj)` returns a list
  of violations.
- `backend/apps-script/test/{normalize,catalog,router}.test.js`: create (T1–T3).
- `backend/apps-script/tools/check-response.js`: create. `node … <file>` exits 0 on a valid response
  and prints violations otherwise.
- `backend/apps-script/README.md`: create. The user's deployment steps, copied from this spec.
- `backend/apps-script/.gitignore`: create. `.clasp.json`, `.clasprc.json`, `node_modules/`,
  `*.local.json`, the last for any saved live response.
- `docs/apps-script-api.md`, `docs/api-samples/catalog-seed.json`,
  `docs/api-samples/catalog-edge.json`: create (P10).
- Docs listed under Durable Documentation Impact. `feature_list.json` and `PROGRESS.md` get evidence
  only.

## Visual Design Impact

None. `DESIGN.md` does not apply.

## Durable Documentation Impact

- `docs/sheet-schema.md`: update.
  - **Reading cells**: the catalog endpoint emits display text, and raw integers for `songsterr_id`.
    `fecha`/`hora`/`posicion` normalization moves to the jam read slice. Name it only once U5 is
    answered; until then, say "the jam read slice".
  - **Mapper rules**: split the enforcement between endpoint and mapper, as in P2.
- `docs/technical-discovery.md`: update.
  - **Deployment and Operations**: `backend/apps-script/`, the settings, the redeploy-keeps-URL
    rule, and the measured latency.
  - **Testing**: the Node tests and the fact that they are outside the gate.
- `docs/risks-and-open-questions.md`: update.
  - Cell types: settled for the catalog, open for jams.
  - Draft data: still open, for the jam slice.
  - Assumption 6: the quota facts found.
  - Research task: read latency measured; write latency still open.
- `.claude/skills/architecture/SKILL.md`: update. Add `backend/apps-script` to the module layout
  table as "not a Gradle module; Apps Script, depends on nothing; the only code that touches the
  Sheet". Add one line under **Where Each Piece Goes**: "a new Sheet read or write → a route in
  `backend/apps-script` plus a repository function in `:core:data`".
- `AGENTS.md`: update. Add one optional-doc line: `docs/apps-script-api.md` — when touching the
  backend contract.
- `ARCHITECTURE.md` and `CONSTRAINTS.md` do not exist and are not needed.

## Implementation Plan And Tasks

- [ ] 1. Write `Normalize.js` and T1 (`normalize.test.js`). The cases:
  - trim, including NBSP;
  - empty to `null`;
  - `12345` → `"12345"`;
  - `12.5` → `"12.5"`;
  - `" 42 "` → `"42"`;
  - `""`, `null` and `undefined` → `null`.
- [ ] 2. Write `Catalog.js` and T2 (`catalog.test.js`):
  - the seed CSV, fed as both display and raw rows, deep-equals `catalog-seed.json`, and every seed
    song has 4 `null` optional fields;
  - the inline edge input deep-equals `catalog-edge.json`. It has reordered columns, an extra
    column, blank rows, padded cells, a numeric `songsterr_id`, and a song with all optionals set
    (`rápido`, `media`, tags);
  - a missing optional column gives `null`s;
  - `missing_header`, `duplicate_header` and an empty tab each throw the right code;
  - `checkCatalogResponse` accepts both samples.
- [ ] 3. Write `Code.js` and T3 (`router.test.js`), using a fake spreadsheet that has a `Config`
  tab with `passphrase = s3cret`:
  - `catalog` succeeds;
  - `config`, a missing param and `CATALOG` each give `unknown_resource`;
  - a missing `Catalogo` tab gives `missing_tab`;
  - a thrown fake gives `internal_error`;
  - the fake records every `getSheetByName` call and never sees `Config`;
  - no serialized body contains `s3cret`;
  - no file in `src/` contains the string `Config`.
- [ ] 4. Generate both samples from the builder once, review them by eye against P4, and commit
  them. From then on the tests compare against the committed files and never regenerate them.
- [ ] 5. **Demonstrate failure.** Edit a scratch copy of `catalog-seed.json`, for example by changing
  one title, and run the test against it: it must fail. Then run `check-response.js` on a copy with
  a missing key and a numeric value: it must exit non-zero. Keep both outputs.
- [ ] 6. Write `tools/check-response.js`, `appsscript.json`, `.gitignore` and `README.md`.
- [ ] 7. Update the docs listed above.
- [ ] 8. Run `node --test backend/apps-script/test/*.test.js` and `CI=true ./init.sh`. The gate must
  exit 0 with `konsist: wired`, `detekt: wired` and `ktlint: wired`. Nothing Gradle-side changed, so
  test counts must match session 033.
- [ ] 9. Hand the user the manual steps below. **Do not set `passing` until the user's evidence
  (M1–M5) is recorded.** Until then the feature stays `in_progress`, with the repo half's evidence
  recorded.

## Manual Steps For The User (the Google account is required)

1. Open the real Sheet → **Extensions → Apps Script**. If a script is already bound, say so (U6).
2. In **Project Settings**, tick "Show `appsscript.json` manifest file in editor". Replace the
   manifest with `backend/apps-script/appsscript.json`.
3. Create three script files named `Normalize`, `Catalog` and `Code`, and paste each `src/*.js`
   file into its namesake. Delete the default `myFunction`. Save.
4. **Deploy → New deployment → type Web app**. Set Execute as **Me**, Who has access **Anyone**,
   then Deploy. Authorize when asked; Google shows "Google hasn't verified this app" for a personal
   script, so choose Advanced → Go to …. Copy the `/exec` URL. Never use the `/dev` test URL, which
   requires a login.
5. After any later code change, use **Deploy → Manage deployments → Edit → Version: New version**.
   A *new deployment* changes the URL.
6. Hand back one of these:
   - **(a)** The URL in chat, not committed. The agent then runs the checks below from this
     machine.
   - **(b)** The outputs of these commands, from Git Bash:
     - `curl -sL "$URL?resource=catalog" -o catalog.local.json`
     - `node backend/apps-script/tools/check-response.js catalog.local.json`
     - `curl -sL "$URL?resource=config"`
     - ten runs of
       `curl -sL -o /dev/null -w "%{time_total}\n" "$URL?resource=catalog"`, plus the first call
       after at least 30 minutes idle.

Evidence recorded as M1–M5:
- **M1**: the checker exits 0 on the live response.
- **M2**: the song count, and that it roughly matches the Sheet.
- **M3**: at least one live song has `null` optional fields and passes. Seed songs qualify.
- **M4**: `resource=config` returns `unknown_resource`.
- **M5**: latency — cold first call, plus min, median and max of the ten warm calls.

Also report whether the real `Catalogo` headers match the eight exactly. If the checker fails, the
agent reports the violations and nothing is changed in the Sheet without the user.

## Verification Plan

- `node --test backend/apps-script/test/*.test.js`: all pass. This runs outside the gate (P9).
- The failure demonstrations from task 5.
- `CI=true ./init.sh`: exit 0, three `wired`, suite counts unchanged.
- The user's M1–M5.
- No E2E harness exists, and nothing on the device changes.

## Evidence To Capture

- The Node test summary, with counts per file, and both failure demonstrations.
- The `init.sh` summary and the three `wired` lines.
- M1–M5, attributed to the user or to the agent's curl as it actually happened. Latency goes into
  `feature_list.json` evidence and PROGRESS, flagged as the input to the optimistic-update decision
  in `admin-add-song-to-setlist`.
- The quota facts, checked on developers.google.com/apps-script/guides/services/quotas, with the
  date checked.

## Validator Checklist

- [ ] Only catalog is routed. No doPost. `Config` is absent from `src/`, and T3 proves it is never
      requested.
- [ ] Every song has 8 keys with string-or-null values. The seed sample equals the builder output.
      The failing case is demonstrated.
- [ ] No row-level validation duplicated in JS (P2). No enrichment fields.
- [ ] No Gradle, Kotlin, `init.sh` or Konsist change. The gate is green with three `wired`.
- [ ] No `.clasp.json`, credentials or URL committed.
- [ ] Manual evidence is attributed correctly. `passing` is set only with M1–M5 present.
- [ ] The docs are updated as listed.

## Risks

- **Quotas.** These are from memory and not verified: 30 simultaneous executions per user, 6 min per
  execution, and a daily cap on total runtime. Every anonymous read counts against the owner. With
  a cache in the app, 20–60 readers on jam night should fit. Verify and record (Evidence).
- **Cold starts** of several seconds are typical for Apps Script. If M5 is poor, the levers are the
  app cache (already planned) and `CacheService` in the script (a later decision).
- **The real Sheet has diverged from the seed.** The live check (M1) validates shape only. Bad keys
  or enums surface in `catalog-repository-cache`, not here.
- **A future GET-based passphrase check** would leak the passphrase into URLs and logs.
  `admin-passphrase-login` should use POST. This is recorded for that slice.

## User Decisions Needed

- **U1 — clasp or copy-paste.** Recommended: copy-paste (P7).
- **U2 — access "Anyone" (anonymous) plus execute as you.** This is required by D-11. Confirm that
  you accept the catalog being readable by anyone with the URL.
- **U3 — where the URL lives.** Recommended: not committed; `local.properties` later (P11). Also:
  is the repo public?
- **U4 — Node tests outside the gate.** Recommended: yes for now (P9). Putting them in `init.sh` is
  a verification change.
- **U5 — scope gap: nothing serves jams.** Recommended: add a feature
  `apps-script-jams-read-endpoint`, "Expose jams and setlists, withholding draft setlists from
  unauthenticated readers". It would depend on this slice, normalize `fecha`/`hora`/`posicion`, and
  be added to `catalog-repository-cache`'s `depends_on`. Admin reading of drafts needs the
  passphrase, so it coordinates with `apps-script-write-auth`. This is a `feature_list.json` change
  and not made here.
- **U6 — existing bound script.** Is there already an Apps Script project on the Sheet? Will you
  share the URL with the agent for measurement (6a) or run the commands yourself (6b)?
