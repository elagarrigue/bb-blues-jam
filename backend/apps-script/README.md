# Apps Script backend

The only code that touches the Google Sheet. It runs on Google's servers as a web app bound to the
Sheet; the Android app calls its `/exec` URL. The contract it serves is
[`docs/apps-script-api.md`](../../docs/apps-script-api.md); the Sheet it reads is
[`docs/sheet-schema.md`](../../docs/sheet-schema.md).

It serves two reads, `GET <url>?resource=catalog` and `GET <url>?resource=jams`. It never opens the
`Config` tab, and it never reads the tab of a jam whose `estado` is not exactly `PUBLICADA`.

## Layout

| Path | What |
|---|---|
| `appsscript.json` | Manifest: V8, Buenos Aires time zone, the `spreadsheets.currentonly` scope, web app executed as the owner with anonymous access. |
| `src/Normalize.js` | Cell normalization (`textCell`, `integerTextCell`, `isoDateCell`, `timeCell`, `isBlank`, `isDateValue`), the header matcher `mapColumns` and `ContractError`. |
| `src/Catalog.js` | `buildCatalog(displayRows, rawRows)` and the `Catalogo` header-to-field table. |
| `src/Jams.js` | `buildJams(jamsDisplay, jamsRaw, readTab, formatDate)`, `buildSetlist`, the `Jams` and jam-tab header tables, and the draft rule. |
| `src/Code.js` | `doGet`, `handleGet(params, spreadsheet)` and the route table. |
| `test/` | Node tests (`node:test`), run outside `init.sh`. `test/helpers/format.js` stands in for `Utilities.formatDate`. |
| `tools/check-response.js` | Checks a saved live response against the contract. |

Each `src/*.js` file ends with `if (typeof module !== 'undefined') { module.exports = … }`. Apps
Script has no `module`, so the line does nothing there; Node uses it to load the same file in the
tests. Apps Script loads every file into one shared global scope, so `Code.js` calls functions
from `Catalog.js` and `Jams.js` directly; `test/helpers/load.js` reproduces that in Node.

## Run the tests

Node 18 or newer, nothing to install (no `package.json`, no `node_modules`). From the repository
root:

```bash
node --test backend/apps-script/test/*.test.js
```

The tests are not part of `./init.sh` (decision U4 in `docs/specs/apps-script-read-endpoint.md`).
Run them whenever a file here changes. If a test changes the builder's output on purpose, update
`docs/api-samples/*.json` by hand and review the diff: the samples are fixtures for the Kotlin
mapper in `catalog-repository-cache`.

## Deploy (copy-paste, the chosen path)

Needs the Google account that owns the Sheet. Nothing here is stored in the repo: no script id,
no URL, no token.

1. Open the Sheet, then **Extensions → Apps Script**. This creates a script bound to the Sheet, so
   the code needs no Sheet id. If a script is already bound, stop and say so.
2. **Project Settings** (gear icon) → tick **Show "appsscript.json" manifest file in editor**.
   Back in **Editor**, open `appsscript.json` and replace its whole content with
   `backend/apps-script/appsscript.json`.
3. The project starts with one file, `Code.gs`, holding an empty `myFunction`. Replace its whole
   content with `src/Code.js`. Add three more files with **+ → Script**, named `Normalize`,
   `Catalog` and `Jams` (the editor adds `.gs`), and paste `src/Normalize.js`, `src/Catalog.js`
   and `src/Jams.js` into them. File order does not matter: no file runs code from another at
   load time. **Save** (Ctrl+S).
4. **Deploy → New deployment** → gear next to "Select type" → **Web app**. Set **Execute as: Me**
   and **Who has access: Anyone** (these match the manifest), then **Deploy**.
5. Authorize when asked. For a personal script Google shows "Google hasn't verified this app":
   choose **Advanced → Go to (project name) (unsafe)** and allow. The manifest asks for one scope,
   `spreadsheets.currentonly`: only the spreadsheet the script is bound to.
6. Copy the **Web app URL**, ending in `/exec`. Never use the `/dev` test URL: it requires a login
   and runs the latest saved code instead of the deployed version.
7. Hand the URL over in chat. It is not committed (the repo is treated as public); it is kept in
   the git-ignored `local.properties` as `bluesjam.appsScriptUrl`.

### Updating the code later

Paste the new files and save. Then **verify the paste** (below) before deploying: copy-paste can
silently leave a file on its old content. Then **Deploy → Manage deployments → (the deployment) →
Edit (pencil) → Version: New version → Deploy**. The URL stays the same. **New deployment** would
create a second URL, and the app would keep calling the old version. After deploying, check that
`?resource=config` replies `unknown_resource` with the message ending in `Known: catalog, jams`
(the route list of the code now in `src/Code.js`); an older list means the deployment still runs
an old `Code.gs`.

#### Verify the paste

In the Apps Script editor, open each file and search (Ctrl+F) for its distinctive line:

| File | Must contain | Must not contain |
|---|---|---|
| `Code.gs` | `jams: readJams_,` | |
| `Jams.gs` | `var PUBLISHED_STATUS = 'PUBLICADA';` | |
| `Normalize.gs` | `function isoDateCell(` and `class ContractError` | |
| `Catalog.gs` | | `class ContractError` |

If any check fails, paste that file again over its whole content, save, and check again.

### Redeploy for the jams route (`apps-script-jams-read-endpoint`)

The script is already deployed with the catalog route. This adds the `jams` route. The
`appsscript.json` manifest has not changed: leave it as it is.

1. Open the Sheet, then **Extensions → Apps Script**.
2. Replace the **whole content** of three existing files, each with its namesake from
   `backend/apps-script/src/`:
   - `Normalize.gs` ← `src/Normalize.js` (it now also holds `ContractError` and the header matcher);
   - `Catalog.gs` ← `src/Catalog.js` (`ContractError` moved out of it; pasting only one of these
     two files would define it twice or not at all, so paste both);
   - `Code.gs` ← `src/Code.js`.
3. Add one new file: **+ → Script**, name it `Jams` (the editor adds `.gs`), delete the empty
   `myFunction` it starts with, and paste the whole of `src/Jams.js`.
4. **Save** (Ctrl+S). The project now has four script files, `Code`, `Normalize`, `Catalog` and
   `Jams`, plus `appsscript.json`.
5. **Verify the paste** with the table in **Updating the code later**: one distinctive line per
   file (`Code.gs` has `jams: readJams_,`; `Jams.gs` has `var PUBLISHED_STATUS = 'PUBLICADA';`;
   `Normalize.gs` has `function isoDateCell(` and `class ContractError`; `Catalog.gs` does
   **not** have `class ContractError`). Fix any file that fails before deploying.
6. **Deploy → Manage deployments** → select the existing deployment → **Edit** (pencil icon) →
   **Version: New version** → **Deploy**. Never choose **New deployment**: it would create a new
   URL, and the app and the checks would keep calling the old version.
7. The scope is unchanged, so no new authorization is expected. If Google asks, authorize as the
   first time.
8. Check the deployment: `?resource=config` must reply `unknown_resource` with a message ending in
   `Known: catalog, jams` (**Check a live deployment** has the command). `Known: catalog` means
   the deployed `Code.gs` is still the old one: go back to step 5.
9. Tell the orchestrator in chat (never paste the URL into a file in the repo):
   - that the new version is deployed;
   - the Sheet's **File → Settings → Time zone** (expected `(GMT-03:00) Buenos Aires`);
   - which real jams are `BORRADOR`, if any, and whether every past jam is `PUBLICADA`.

### Temporary test jam for the draft check (user approval A3)

Sheet edits need no redeploy. Do each step only when the orchestrator asks for it.

1. **Add the draft.** In the `Jams` tab, add a row: `fecha` `2000-01-01`, `hora` `21:00`, `lugar`
   `Prueba`, `estado` `BORRADOR`. Add a tab named exactly `2000-01-01`, copy into its first row the
   13 headers of a real jam tab (`posicion`, `id_tema`, `titulo`, `artista`, `tono`, `Guitarra 1`,
   `Guitarra 2`, `Bajo`, `Batería`, `Voz`, `Armónica`, `Teclados`, `Otros`), and add one row:
   `posicion` `1`, `id_tema` `zz-borrador`, `titulo` `ZZ BORRADOR NO DEBE SALIR`, `artista`
   `Prueba`, `tono` `A`, every other cell empty. Tell the orchestrator; it runs check L3.
2. **Publish it.** Change that `Jams` row's `estado` to `PUBLICADA`. Tell the orchestrator; it runs
   L3 again, and this time the row must appear.
3. **Delete it.** Delete the `2000-01-01` row from `Jams` and delete the `2000-01-01` tab. Tell the
   orchestrator; it confirms the jam is gone.

The date is in the past on purpose: it cannot become a second upcoming jam.

## Check a live deployment

From Git Bash at the repository root. `URL` is read from the git-ignored `local.properties`, where
Android Studio may escape `:` as `\:`; the commands never print it.

```bash
URL=$(grep '^bluesjam.appsScriptUrl=' local.properties | cut -d= -f2- | sed 's/\\:/:/g' | tr -d '\r')
CHECK=backend/apps-script/tools/check-response.js
OUT=backend/apps-script
```

Catalog (and the passphrase tab is refused):

```bash
curl -sL "$URL?resource=catalog" -o $OUT/catalog.local.json
node $CHECK $OUT/catalog.local.json                                  # exit 0 = valid
curl -sL "$URL?resource=config"                                      # unknown_resource, "... Known: catalog, jams"
for i in $(seq 10); do curl -sL -o /dev/null -w "%{time_total}\n" "$URL?resource=catalog"; done
```

Jams (`apps-script-jams-read-endpoint`, checks L1 to L5):

```bash
# L1: the real jams pass the strict check (formats, and no per-jam error).
curl -sL "$URL?resource=jams" -o $OUT/jams.local.json
node $CHECK --strict $OUT/jams.local.json                            # exit 0; prints the counts

# L2: catalog still valid, config still refused (the catalog block above).

# L3, with the draft test jam present: withheld, and nothing of its tab in the body.
curl -sL "$URL?resource=jams" -o $OUT/jams-draft.local.json
node $CHECK $OUT/jams-draft.local.json                               # exit 0 (the draft rule holds)
grep -c 'zz-borrador\|NO DEBE SALIR' $OUT/jams-draft.local.json      # must print 0
node -e "const b=require('./$OUT/jams-draft.local.json'); console.log(JSON.stringify(b.jams.filter(j => j.date === '2000-01-01')))"
#   expect [{"date":"2000-01-01","startTime":"21:00","venue":"Prueba","status":"BORRADOR","setlist":null,"setlistError":null}]
# L3 again after the flip to PUBLICADA: the same node -e on a fresh body shows one setlist row
# with songId "zz-borrador". After deletion: it prints [].

# L4: a passphrase parameter changes nothing (compare with a body fetched right after L1).
curl -sL "$URL?resource=jams&passphrase=x" -o $OUT/jams-param.local.json
cmp $OUT/jams.local.json $OUT/jams-param.local.json && echo identical

# L5: latency. One first call, then ten warm calls; report min, median, max, plus the jam and
# published-tab counts that the L1 check printed.
for i in $(seq 10); do curl -sL -o /dev/null -w "%{time_total}\n" "$URL?resource=jams"; done | sort -n
```

`-L` is required: the web app answers with a 302 to `script.googleusercontent.com`. Every response
is HTTP 200, errors included, so the body decides. An HTML page instead of JSON usually means the
`/dev` URL, access not set to **Anyone**, or a missing `-L`. `*.local.json` files are git-ignored
inside `backend/apps-script/` only, so save live responses there, as above.
For the cold-start figure, run one request after at least 30 minutes with no calls.

## Optional: clasp

[clasp](https://github.com/google/clasp) pushes these files from the command line instead of
pasting them. It is not set up here and not needed: copy-paste is the chosen path. Using it would
need clasp installed with npm, the Apps Script API turned on in the Apps Script user settings,
`clasp login` (OAuth tokens saved in the home directory as `.clasprc.json`) and a `.clasp.json`
holding the script id. Both files are git-ignored here and must never be committed. This layout is
not clasp-ready as it stands: clasp pushes the script files under its root directory, which here
includes `test/` and `tools/`, so a `.claspignore` would be needed first. Deploying with clasp
still needs the **Manage deployments → New version** rule to keep the URL.
