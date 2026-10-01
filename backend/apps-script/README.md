# Apps Script backend

The only code that touches the Google Sheet. It runs on Google's servers as a web app bound to the
Sheet; the Android app calls its `/exec` URL. The contract it serves is
[`docs/apps-script-api.md`](../../docs/apps-script-api.md); the Sheet it reads is
[`docs/sheet-schema.md`](../../docs/sheet-schema.md).

This slice serves one read: `GET <url>?resource=catalog`. It never opens the `Config` tab.

## Layout

| Path | What |
|---|---|
| `appsscript.json` | Manifest: V8, Buenos Aires time zone, the `spreadsheets.currentonly` scope, web app executed as the owner with anonymous access. |
| `src/Normalize.js` | Cell normalization: `textCell`, `integerTextCell`, `isBlank`. |
| `src/Catalog.js` | `buildCatalog(displayRows, rawRows)`, the header-to-field table, `ContractError`. |
| `src/Code.js` | `doGet`, `handleGet(params, spreadsheet)` and the route table. |
| `test/` | Node tests (`node:test`), run outside `init.sh`. |
| `tools/check-response.js` | Checks a saved live response against the contract. |

Each `src/*.js` file ends with `if (typeof module !== 'undefined') { module.exports = … }`. Apps
Script has no `module`, so the line does nothing there; Node uses it to load the same file in the
tests. Apps Script loads every file into one shared global scope, so `Code.js` calls functions
from `Catalog.js` directly; `test/helpers/load.js` reproduces that in Node.

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
   content with `src/Code.js`. Add two more files with **+ → Script**, named `Normalize` and
   `Catalog` (the editor adds `.gs`), and paste `src/Normalize.js` and `src/Catalog.js` into
   them. File order does not matter: no file runs code from another at load time. **Save** (Ctrl+S).
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

Paste the new files, save, then **Deploy → Manage deployments → (the deployment) → Edit (pencil)
→ Version: New version → Deploy**. The URL stays the same. **New deployment** would create a
second URL, and the app would keep calling the old version.

## Check a live deployment

From Git Bash at the repository root, with `URL` set to the `/exec` URL:

```bash
curl -sL "$URL?resource=catalog" -o backend/apps-script/catalog.local.json
node backend/apps-script/tools/check-response.js backend/apps-script/catalog.local.json  # exit 0 = valid
curl -sL "$URL?resource=config"                                       # must be unknown_resource
for i in $(seq 10); do curl -sL -o /dev/null -w "%{time_total}\n" "$URL?resource=catalog"; done
```

`-L` is required: the web app answers with a 302 to `script.googleusercontent.com`. Every response
is HTTP 200, errors included, so the body decides. An HTML page instead of JSON usually means the
`/dev` URL, access not set to **Anyone**, or a missing `-L`. `*.local.json` files are git-ignored inside
`backend/apps-script/` only, so save live responses there, as above.
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
