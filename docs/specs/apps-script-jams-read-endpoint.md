# Feature Implementation Spec: Expose jams and setlists through Apps Script

## Source Feature

- `id`: apps-script-jams-read-endpoint
- `area`: backend
- `depends_on`: apps-script-read-endpoint (`accepted`)
- `status`: not_started (at planning time, 1 October 2026, HEAD `280b857`)
- `source`: `feature_list.json`

**Needs the user's approval on A1–A4 (end of this spec) before `passing`.** The repo half can
proceed on the recommended defaults. The redeploy and the live draft check are the user's steps,
because no agent can reach the Google account or the Sheet.

## Goal

`GET <url>?resource=jams` returns every row of the `Jams` tab, with each jam's setlist read from
its date-named tab. **A setlist is served only for a jam whose `estado` is exactly `PUBLICADA`.**
For any other jam, the script does not even open its tab. `fecha`, `hora` and `posicion` come back
as `YYYY-MM-DD`, `HH:MM` and integer text, whether the Sheet stores them as text or as typed
date, time and number cells. The contract, the samples and the checker are extended so that
`catalog-repository-cache` can write its jam mapper without access to the Sheet.

## Non-Goals

- **How the admin reads a draft.** That is out of scope here. It belongs to `apps-script-write-auth`
  or an admin slice, and it must use **POST with the passphrase in the body, never a GET
  parameter**. This slice adds no POST, no passphrase check and no `Config` access. A GET
  parameter can never unlock a draft (T6).
- **Interpreting values** (P2 of the previous spec still holds). This slice does not validate keys,
  ids, enums, the `Otros` split, `-` slots, unique or contiguous positions, catalog resolution or
  "at most one upcoming jam". All of that is the Kotlin mapper's job in `catalog-repository-cache`.
  The one exception is the publish check (D2), which is security, not interpretation.
- **Upcoming versus past.** The server never decides by today's date (D1).
- No Kotlin, Gradle, `init.sh`, Konsist or `feature_list.json` change, and no Android client.
- No `CacheService`, no `getSheets()` listing, and no change to the `catalog` response.
- D-20: there are no catalog fields in this response, so tempo, tags, difficulty and `songsterrId`
  are untouched.

## Job Story

When I build the jam cache and the next-jam and past-jam screens,
I want one documented JSON response with every jam and only the published setlists,
so the mapper can be written against committed samples and no draft can reach a musician's phone.

## Users And Permissions

- The response is anonymous, like `catalog`. It is safe to serve because it contains only:
  - each jam's date, time, venue and status (`docs/user-and-access-model.md`: musicians see these
    for a draft);
  - the setlists of `PUBLICADA` jams.
- Draft setlists are never read, so they cannot leak (D2, T5).
- `Config` is never opened (D3, T6).

## Acceptance Scenarios

1. **Seed read.** Given the seed `Jams.csv` and the tab `2026-07-25`, the response deep-equals
   `docs/api-samples/jams-seed.json`. It has one jam, `PUBLICADA`, with 13 setlist rows, every slot
   `null` and `extraParticipants` `null`.
2. **Draft withheld.** Given a `BORRADOR` jam whose tab holds a marker song, the jam object carries
   its `date`, `startTime`, `venue` and `status`, with `setlist: null` and `setlistError: null`.
   The fake records no `getSheetByName` call for that tab, and the serialized body does not contain
   the marker.
3. **Fail closed.** Given an `estado` of `Publicada`, `publicada `, empty, or a typo, the setlist is
   withheld, as for a draft.
4. **Typed cells.** Given `fecha` stored as a `Date`, `hora` as a time `Date` whose display text is
   `21:00:00` or `9:00 p. m.`, and `posicion` as the number `3`, the response carries
   `"2026-07-25"`, `"21:00"` and `"3"`. Text cells `2026-07-25`, ` 9:30 ` and `3` give
   `"2026-07-25"`, `"09:30"` and `"3"`.
5. **Per-jam structural problems.** A `PUBLICADA` jam with no tab, a tab missing a required header,
   a tab with a duplicate mapped header, a non-ISO `fecha`, or a `fecha` shared by two rows gets
   `setlist: null` and a `setlistError` with a code. The other jams are still served.
6. **Whole-response errors.** A missing `Jams` tab gives `missing_tab`. A missing `Jams` header
   gives `missing_header`, and a duplicate one gives `duplicate_header`.
7. **No escape hatch.** `fecha` = `Config` or `Catalogo` with `PUBLICADA` never opens that tab.
   `?resource=jams&passphrase=…` behaves exactly like `?resource=jams`.

## Decisions (planner, with reasoning)

**D1 — One route, `resource=jams`, with no server-side upcoming/past split.**
- Upcoming versus past is `Jam.isHistorical(today)`, where today comes from the caller, and the
  midnight question (risks doc) is an app concern. If the script classified jams, it would need
  its own clock and would add a second interpreter of "today".
- The app needs both lists at once (two tabs, one cache), so one call is cheaper than two at about
  2.5 s each.
- Body: `{"schemaVersion":1,"jams":[…]}`. `schemaVersion` stays `1`, because a new route is
  additive.
- Jams keep `Jams` row order. Rows whose four mapped cells are all empty are skipped.

**D2 — Draft protection: an exact `PUBLICADA`, fail closed, and draft tabs are never opened.**
- The script serves a setlist only when the trimmed display value of `estado` is exactly
  `PUBLICADA`. Anything else gives `setlist: null` and `setlistError: null`, and the jam's tab is
  never requested: `BORRADOR`, a typo, wrong case, or empty.
- This is the one value the script interprets, because withholding cannot be delegated to the
  client.
- `status` is emitted as the raw trimmed text, `"BORRADOR"` or `"PUBLICADA"`, so the mapper still
  owns the enum and rejects typos.
- **Consequence (A1):** a past jam left in `BORRADOR` shows no setlist in Anteriores until the
  admin marks it `PUBLICADA`. Status, not date, decides, so a forgotten upcoming draft can never
  leak because its date passed.

**D3 — The tab lookup is guarded.**
- A tab is looked up only when the normalized `fecha` matches `^\d{4}-\d{2}-\d{2}$`. Otherwise the
  jam gets `setlistError` `invalid_date`. Without this guard, a `fecha` typed as `Config` would make
  the script read the passphrase tab and serve it as a setlist.
- A `fecha` that appears on more than one row gives every `PUBLICADA` row with that date
  `duplicate_date`, with no setlist. Two rows such as `PUBLICADA` and `BORRADOR` for the same tab
  must not leak that tab. The mapper still owns rejecting duplicate dates.
- The spreadsheet is accessed only through `getSheetByName` and `getSpreadsheetTimeZone`.

**D4 — Jam object, exactly these keys in this order:**

| Field | Source | Value |
|---|---|---|
| `date` | `fecha` | `YYYY-MM-DD` when typed or text (D6), else the trimmed text; `null` when empty |
| `startTime` | `hora` | `HH:MM` (D6), else the trimmed text; `null` when empty |
| `venue` | `lugar` | trimmed display text or `null` |
| `status` | `estado` | trimmed display text or `null` (Sheet vocabulary) |
| `setlist` | tab `date` | array of setlist rows, or `null` (withheld or broken) |
| `setlistError` | — | `null`, or `{"code","message"}` (D7); always `null` when withheld by D2 |

`setlist` is an array only when `status === "PUBLICADA"` and `setlistError === null`. An empty
array means a published jam whose tab has headers but no rows.

**D5 — Setlist row, exactly these keys in this order.** The `slots` object has exactly the seven
keys below, in this order.

| Field | Header | Value |
|---|---|---|
| `position` | `posicion` | integer text from the raw value (`integerTextCell`), or `null` |
| `songId` | `id_tema` | text or `null` |
| `title` | `titulo` | text or `null` (the fallback copy; the mapper resolves the catalog) |
| `artist` | `artista` | text or `null` |
| `key` | `tono` | text or `null` |
| `slots` | the seven slot columns | `{"guitar1","guitar2","bass","drums","vocals","harmonica","keyboards"}` from `Guitarra 1`, `Guitarra 2`, `Bajo`, `Batería`, `Voz`, `Armónica`, `Teclados`; each `null` (empty), `"-"` (passed through raw), or a name |
| `extraParticipants` | `Otros` | the raw trimmed cell or `null`; a missing column also gives `null` (D-18) |

Why these choices:
- Slots are a fixed object, so the U1 ordinal ("k-th non-`-` column of that instrument") is
  computable by the mapper from the column order, which the key order and the documented table
  both preserve.
- `-` and `Otros` stay raw, like `tags` in the catalog, so there is one parser and it is Kotlin.
- Required headers are `posicion`, `id_tema`, `titulo`, `artista`, `tono` and all seven slots.
  `Otros` is optional.
- Rows are emitted in tab order, not sorted, because the mapper sorts by position. A row whose
  mapped cells are all empty is skipped. Unmapped columns are ignored.
- Text cells use display values and `posicion` uses the raw value, as P3 did for the catalog.

**D6 — Typed-cell normalization (new pure functions in `Normalize.js`).** The time zone `tz` is
`spreadsheet.getSpreadsheetTimeZone()`. A formatter `formatDate(date, pattern)` is injected:
`Utilities.formatDate(d, tz, p)` in Apps Script, and an `Intl`-based fake in Node (helper
`test/helpers/format.js`). Detect a date with `Object.prototype.toString.call(v) === '[object
Date]'`, not `instanceof`, so the check holds across realms in the vm test.

- `isoDateCell(raw, display, formatDate)`:
  - a raw `Date` gives `formatDate(raw, 'yyyy-MM-dd')`;
  - otherwise `textCell(display)`.
- `timeCell(raw, display, formatDate)`:
  - (1) if the trimmed display matches `^(\d{1,2}):(\d{2})(:00)?$` with hour ≤ 23, the result is
    the zero-padded `HH:MM`;
  - (2) else, for a raw `Date`, the result is `formatDate(raw, 'HH:mm')`;
  - (3) else, `textCell(display)`.
  - The display-first order avoids the 1899-12-30 epoch that Sheets uses for time-only cells, where
    the historical LMT offset of Buenos Aires (−4:16:48) can shift minutes.
- `posicion` uses the existing `integerTextCell`.

Why the spreadsheet's zone and not the manifest's: Sheets builds the `Date` in the spreadsheet's
own zone. Formatting in that zone round-trips what the admin sees even if the two zones differ.
The expected zone is Buenos Aires, the same as the manifest, and the user confirms it (A4, M2).

**D7 — Errors.**
- **Whole-response** errors use the existing envelope and codes. They cover only the `Jams` tab:
  `missing_tab`, `missing_header` (an empty tab or a missing `fecha`/`hora`/`lugar`/`estado`), and
  `duplicate_header`. Any uncaught exception is `internal_error`.
- **Per-jam** errors appear in `setlistError` and apply only to `PUBLICADA` jams:
  - `invalid_date`
  - `duplicate_date`
  - `missing_tab`
  - `missing_header`, including a missing slot header
  - `duplicate_header`

  A per-jam error sets `setlist: null` and lets every other jam through, so one broken past tab
  cannot blank the whole app.
- **A tab with no `Jams` row** is never opened and never served. It is invisible, because the script
  does not list tabs.
- What the app shows for a jam with a `setlistError` remains `catalog-repository-cache`'s decision.
  This slice gives it the data to decide. Proposed split: A2.

**D8 — Code layout.**
- `Normalize.js` gains `ContractError`, moved from `Catalog.js`, and a generic `mapColumns(tabName,
  specs, headerRow)`, the trimmed exact header match with `duplicate_header` and `missing_header`.
  `Catalog.js` uses it. Its behavior and its tests stay unchanged.
- New `src/Jams.js` holds:
  - `JAMS_TAB`, `JAMS_FIELDS`, `SETLIST_FIELDS` and `SLOT_FIELDS`;
  - `buildSetlist(displayRows, rawRows)`;
  - `buildJams(jamsDisplay, jamsRaw, readTab, formatDate)`. It is pure; `readTab(date)` returns
    `{display, raw}` or `null`.
- `Code.js` adds `jams: readJams_` to `ROUTES`. `readJams_` resolves the zone and wires
  `readTab` to `getSheetByName(date).getDataRange()`.
- File load order: `Normalize`, `Catalog`, `Jams`, `Code`, in `test/helpers/load.js` and in the vm
  test.
- No source file may contain `Config`, `passphrase` (any case) or `doPost`, and that includes
  comments. The existing T3 guard enforces it.

## Repository Research (inspected)

- `AGENTS.md`, `PROGRESS.md` and `feature_list.json`: this entry, plus `catalog-repository-cache`,
  `next-jam-read-only-list`, `unpublished-setlist-state`, `past-jams-list`, `past-jam-detail`,
  `apps-script-write-auth`, `admin-*`.
- `docs/specs/apps-script-read-endpoint.md`: P1–P11 and the user's answers.
- `backend/apps-script/`: the three `src` files, `README.md`, `appsscript.json`, `.gitignore`, the
  test files and helpers (`load.js`, `contract.js`, `csv.js`, `edge-input.js`), and
  `tools/check-response.js`.
- `docs/apps-script-api.md`, `docs/sheet-schema.md`, `docs/domain-model.md`,
  `docs/user-and-access-model.md` and `docs/risks-and-open-questions.md`.
- The bitácora's D-04, D-05, D-11, D-18, D-19 and D-20.
- `docs/sheet-seed/Jams.csv` and `2026-07-25.csv`: one `PUBLICADA` jam with 13 rows, all slot
  cells empty, and an `Otros` header.
- `technical-discovery.md` §Deployment/Testing and the architecture skill's `backend/apps-script`
  row.
- **Not inspected, and not inspectable:** the real Sheet. Its jam tabs, statuses and cell types are
  unknown. Assumption: a CSV import auto-converts `2026-07-25`, `21:00` and `1` into a date, a time
  and a number, so typed cells are likely.

## Expected File Changes

All of these are in `backend/apps-script/` unless the path says otherwise.
- `src/Normalize.js`: update (D6, D8).
- `src/Catalog.js`: update, refactor only (D8).
- `src/Jams.js`: create.
- `src/Code.js`: update.
- `test/helpers/load.js`: add `Jams.js`.
- `test/helpers/format.js`: create. It holds `makeFormatter(tz)` using `Intl.DateTimeFormat`.
- `test/helpers/jams-edge-input.js`: create (the fake tabs for the edge sample).
- `test/helpers/contract.js`: add `checkJamsResponse(body, {strict})` and `JAM_KEYS`, `ROW_KEYS`
  and `SLOT_KEYS`.
- `test/normalize.test.js`: update (T4).
- `test/jams.test.js`: create (T5).
- `test/router.test.js`: update (T6).
- `tools/check-response.js`: update. Detect `songs` or `jams` and add a `--strict` flag.
- `README.md`: update the layout, the paste list (four files), the redeploy steps and the live
  checks.
- `docs/apps-script-api.md`: add a `GET ?resource=jams` section, the per-jam error table and the
  draft rule. Update "Current routes" and the `unknown_resource` example.
- `docs/api-samples/jams-seed.json` and `docs/api-samples/jams-edge.json`: create.

**`jams-edge.json` content.** The jams are:
- a `PUBLICADA` jam with typed `fecha`, `hora` and `posicion`, a slot name, a `-`, empty slots,
  `Otros` `Juan (saxo); Ana (percusión);`, padded cells, an extra column, a blank row, and
  positions out of row order;
- a `BORRADOR` jam whose tab holds the marker `zz-borrador-secreto` (it must be absent from the
  sample);
- a `Publicada` typo, withheld;
- a `PUBLICADA` jam with no tab;
- a tab missing `Teclados`;
- a duplicate date;
- `fecha` = `Config`, giving `invalid_date`;
- a tab with no `Otros` column.

The fake also holds an orphan tab and a `Config` tab, neither of which is read.

## Durable Documentation Impact

- `docs/sheet-schema.md`: update.
  - **Reading cells**: the normalization is implemented, with the display-first time rule and the
    spreadsheet zone.
  - **Mapper rules**: the endpoint detects jam-tab structural problems per jam (D7), ignores orphan
    tabs, and withholds non-`PUBLICADA` setlists. The mismatch line is updated per the user's A2
    answer.
- `docs/risks-and-open-questions.md`: update.
  - Cell types: settled.
  - Draft data: settled for anonymous reads; the admin's read of a draft is still open, POST only,
    for `apps-script-write-auth`.
  - Mismatch: the script half settled; the app half still open for `catalog-repository-cache`.
- `docs/technical-discovery.md`: update. Add the `jams` latency (M5) and the number of tabs read
  per call.
- `docs/user-and-access-model.md`: update. One line saying that drafts are withheld server-side by
  the `jams` route, which also fails closed on any status other than `PUBLICADA`.
- `AGENTS.md`, the architecture skill, `DESIGN.md`, `ARCHITECTURE.md` and `CONSTRAINTS.md`: not
  needed. The routing is already documented, there is no UI, and the last two do not exist.

## Implementation Tasks

- [ ] 1. In `Normalize.js`, add `isDateValue`, `isoDateCell` and `timeCell`, and move in
  `ContractError` and `mapColumns`. Write T4 in `normalize.test.js` with these cases:
  - a `Date` in `America/Argentina/Buenos_Aires` gives `2026-07-25`;
  - the same calendar date built in `Europe/Madrid` and formatted with the Madrid zone still gives
    `2026-07-25`;
  - text `2026-07-25` passes through;
  - `21:00`, `21:00:00` and ` 9:30 ` give `21:00`, `21:00` and `09:30`;
  - a display of `9:00 p. m.` with a raw `Date` gives `21:00`;
  - `25:00` and `21:30:15` pass through as text;
  - the number `3` gives `"3"` and `2.5` gives `"2.5"`;
  - blank gives `null`.
- [ ] 2. Refactor `Catalog.js` onto `mapColumns`. All 33 existing tests must pass unchanged.
- [ ] 3. Write `Jams.js` and T5 in `jams.test.js`:
  - the seed (CSV text, display equal to raw) deep-equals `jams-seed.json`;
  - the edge fake deep-equals `jams-edge.json`;
  - **for every `BORRADOR` or non-`PUBLICADA` jam, the draft tab is absent from the recorded
    `readTab` calls, and the marker is absent from `JSON.stringify(result)`**;
  - each `setlistError` code;
  - a missing `Otros` column gives `null`;
  - the key order of the jam, the row and `slots`;
  - blank rows are skipped;
  - whole-response `missing_header` and `duplicate_header` on `Jams`.
- [ ] 4. Update `Code.js` and T6 in `router.test.js`:
  - the route table is exactly `{catalog, jams}`;
  - `resource=jams` on a Proxy spreadsheet (with `Config`, an orphan tab and a draft tab) accesses
    only `getSheetByName` and `getSpreadsheetTimeZone`, and requests only `Jams` and the published
    dates;
  - `fecha` = `Config` or `Catalogo` is never requested;
  - `{resource:'jams', passphrase:'s3cret'}` deep-equals the response without the parameter, and
    neither contains the marker or `s3cret`;
  - a missing `Jams` tab gives `missing_tab`;
  - the vm test loads four files and serves `jams` with a fake `Utilities`;
  - the existing passphrase-tab test now includes `jams` in its request list.
- [ ] 5. Generate both samples once, review them against D4 and D5 by eye, and commit them. From
  then on the tests only compare against the committed files.
- [ ] 6. Write `checkJamsResponse`. It covers shape, string-or-null values, trimming, the `slots`
  keys and `setlistError` shape. It must check **the draft rule: `status !== "PUBLICADA"` implies
  `setlist === null`**, and that `setlist` is an array only when `setlistError` is `null`. With
  `strict`, it also flags any `date`, `startTime` or `position` not in its documented format, and
  any `setlistError`. Update `check-response.js` to:
  - dispatch on `songs` or `jams`;
  - pass `--strict`;
  - print an OK summary: jams, published-with-setlist, withheld, with errors, total rows.

  Add tests: both samples pass non-strict; `jams-edge.json` fails strict; the catalog samples still
  pass.
- [ ] 7. **Failure demonstrations.** Restore each change from a saved copy and record the SHA-1
  before and after.
  - (a) Serve the setlist regardless of status: T5 and T6 must fail on the marker or the draft tab
    request.
  - (b) Drop the ISO guard: T6 must fail on `Config`.
  - (c) Make `timeCell` skip padding: T4 must fail.
  - (d) The checker on a copy of `jams-edge.json` with a draft given a setlist must exit 1.
- [ ] 8. Update `README.md`, `apps-script-api.md` and the docs listed above.
- [ ] 9. Run `node --test backend/apps-script/test/*.test.js` (all pass, with counts per file) and
  `CI=true ./init.sh` (exit 0, `konsist: wired`, `detekt: wired`, `ktlint: wired`, and the 20 test
  result files identical in names and counts to the baseline).
- [ ] 10. Hand over the manual steps. **The status stays `in_progress` until M1–M6 are recorded.**

## Manual Steps (the user; Google account needed)

1. Open the Sheet → **Extensions → Apps Script**. Paste `src/Normalize.js`, `src/Catalog.js` and
   `src/Code.js` over their namesakes. Add a file with **+ → Script** named `Jams`, paste
   `src/Jams.js` into it, and save.
2. **Deploy → Manage deployments → (the existing deployment) → Edit (pencil) → Version: New
   version → Deploy.** Never choose **New deployment**, because that changes the URL. The scope is
   unchanged, so no new authorization is expected. If Google asks, authorize as before.
3. Tell the orchestrator:
   - the Sheet's **File → Settings → Time zone** (expected GMT-03:00 Buenos Aires);
   - which real jams are `BORRADOR`, if any;
   - whether every past jam is `PUBLICADA` (A1).
4. **Draft check with a throwaway jam (A3)**, only if the real Sheet has no draft:
   - Add a `Jams` row `2000-01-01 | 21:00 | Prueba | BORRADOR`, and a tab `2000-01-01` with the 13
     jam-tab headers and one row `1 | zz-borrador | ZZ BORRADOR NO DEBE SALIR | Prueba | A`.
   - The orchestrator runs L3. Then change the row to `PUBLICADA` and the orchestrator runs L3
     again as a positive control.
   - **Then delete the row and the tab.** The orchestrator confirms that the jam is gone.
   - A past date avoids a second upcoming jam. No redeploy is needed for Sheet edits.

## Live Checks (orchestrator, URL from `local.properties`, never printed into tracked files)

- **L1**: `curl -sL "$URL?resource=jams" -o backend/apps-script/jams.local.json`, then
  `check-response.js --strict` must exit 0.
- **L2**: `catalog` still passes the checker, and `config` is still `unknown_resource`.
- **L3**: The draft check.
  - With the draft present: its jam object has `setlist: null` and `setlistError: null`, and the
    body contains neither `zz-borrador` nor the title.
  - After it is flipped to `PUBLICADA`: the setlist holds that row.
  - After deletion: the date is absent.
  - If the user declines A3, record that the draft rule is proven only by T5, T6 and the checker's
    rule on the real jams.
- **L4**: `?resource=jams&passphrase=x` is byte-identical to L1's body, apart from any Sheet edit in
  between.
- **L5**: Latency. The first call, plus the min, median and max of ten warm `jams` calls, together
  with the number of jams and published tabs read.

Evidence M1–M6 maps to L1–L5 plus the user's step 3 answers. Attribute each item to whoever
produced it.

## Verification Plan

- `node --test backend/apps-script/test/*.test.js` runs outside the gate, as approved under U4.
  This slice does not change verification rules.
- The four failure demonstrations (task 7).
- `CI=true ./init.sh` must exit 0 with the three `wired` lines and unchanged suites. No Gradle file
  is touched.
- L1–L5. There is no device and no E2E harness, because nothing changes in the app.

## Validator Checklist

- [ ] The route table is `{catalog, jams}`. There is no `doPost`. No `src` file contains `Config`
      or `passphrase`. The spreadsheet accesses are limited to `getSheetByName` and
      `getSpreadsheetTimeZone`.
- [ ] A non-`PUBLICADA` jam's tab is never requested, which T5 and T6 prove. Demonstration (a) was
      run. The ISO guard exists, and demonstration (b) was run.
- [ ] The jam, row and slot key sets and orders match D4 and D5. Both samples equal the builder's
      output.
- [ ] The typed-cell cases in T4 pass, and the time-zone source is the spreadsheet's.
- [ ] There is no value interpretation beyond D2, D3 and D6: the mapper still parses `Otros`, `-`
      and the enums.
- [ ] The catalog response is unchanged: its samples and its 33 tests pass.
- [ ] The gate is green with three `wired`. No URL or script id was committed. `*.local.json` is
      ignored.
- [ ] Docs are updated per the list above. `passing` is set only with M1–M6 or a recorded A3
      refusal.

## Risks

- **Latency grows with history.** Each published jam adds about three Sheet calls (`getDataRange`,
  display, raw). A monthly jam adds about 12 tabs a year. Measure in L5. If it is slow, a later
  slice can add `?resource=jams&date=` or `CacheService`. Do not add either now.
- **The time representation in the real Sheet is unverified.** Display-first covers the usual
  `HH:MM[:SS]` and 12-hour formats. An unusual format falls through to the raw `Date` path, where
  the 1899 offset behavior is unverified. L1 strict catches a wrong `startTime`.
- **Fail closed hides the data of a mistyped status.** That is by design (A1). The mapper's enum
  rejection makes the typo visible to the admin later.
- **Orphan tabs are silent.** An admin who adds a tab but forgets the `Jams` row sees nothing. That
  is part of A2.
- **Quotas.** The read now holds the owner's 30 concurrent executions a little longer. The app
  cache is the mitigation, as before.

## User Decisions Needed

- **A1, product: status alone gates the setlist.** Only an exact `PUBLICADA` is served. A past jam
  left in `BORRADOR` shows no setlist. Recommended: yes. Please confirm that past jams in the real
  Sheet are `PUBLICADA`.
- **A2, product: the `Jams`/tab mismatch, script half.** The proposal:
  - a `PUBLICADA` row with no tab is served with `setlistError` `missing_tab`;
  - a tab with no `Jams` row is ignored, never read or served;
  - per-jam errors never fail the whole response.

  What the app shows for a jam with a `setlistError` stays with `catalog-repository-cache`.
  Recommended: approve. This settles half of the open question in `risks-and-open-questions.md`.
- **A3, manual: a temporary test jam in the real Sheet** for L3, if no real draft exists. The steps
  are in Manual Steps 4.
- **A4: the time zone is the spreadsheet's own, not the manifest's** (D6). They are expected to be
  equal (Buenos Aires). Using the spreadsheet's zone makes dates round-trip even if they are not.
  Recommended: approve.
