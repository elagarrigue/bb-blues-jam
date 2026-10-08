# Apps Script backend

The only code that touches the Google Sheet. It runs on Google's servers as a web app bound to the
Sheet; the Android app calls its `/exec` URL. The contract it serves is
[`docs/apps-script-api.md`](../../docs/apps-script-api.md); the Sheet it reads is
[`docs/sheet-schema.md`](../../docs/sheet-schema.md).

It serves two reads, `GET <url>?resource=catalog` and `GET <url>?resource=jams`, and eight POST
actions: `checkPassphrase` (`admin-passphrase-login`), the self-cleaning deploy check
`checkWriteAccess` (`apps-script-write-auth`), the admin read `readJams`, the setlist write
`addSong` and its self-cleaning deploy check `checkSetlistWrite` (`admin-add-song-to-setlist`),
the setlist write `removeSong` with its self-cleaning deploy check `checkSetlistRemove`
(`admin-remove-song-from-setlist`), and the setlist write `setKey` (`admin-set-key`, proved on
deploy by an extra step in `checkSetlistWrite`). `removeSong`, `checkSetlistRemove` and `setKey`
are in `src/Post.js` but not deployed until the batched paste below.
Every POST action passes one passphrase guard in the router, with a rate limit of 10 failed
guesses per 10 minutes; write actions run under the script lock. The GET reads never open the
`Config` tab, and never read the tab of a jam whose `estado` is not exactly `PUBLICADA`; only the
guarded `readJams` reads a current or future `BORRADOR` jam's tab. Only `src/Post.js` opens
`Config`, and no answer ever contains the passphrase.

## Layout

| Path | What |
|---|---|
| `appsscript.json` | Manifest: V8, Buenos Aires time zone, the `spreadsheets.currentonly` scope, web app executed as the owner with anonymous access. |
| `src/Normalize.js` | Cell normalization (`textCell`, `integerTextCell`, `isoDateCell`, `timeCell`, `isBlank`, `isDateValue`), the header matcher `mapColumns` and `ContractError`. |
| `src/Catalog.js` | `buildCatalog(displayRows, rawRows)` and the `Catalogo` header-to-field table. |
| `src/Jams.js` | `buildJams(jamsDisplay, jamsRaw, readTab, formatDate)`, `buildSetlist`, the `Jams` and jam-tab header tables, and the draft rule. |
| `src/Code.js` | `doGet`, `handleGet(params, spreadsheet)` and the route table. |
| `src/Post.js` | `doPost`, `handlePost(request, spreadsheet, services)`, the `ACTIONS` table (`checkPassphrase`, `checkWriteAccess`, `readJams`, `addSong`, `checkSetlistWrite`, `removeSong`, `checkSetlistRemove`, `setKey`, `setSlotCount`, `assignSlot`), the guard `requirePassphrase_` (rate limit, `Config` re-read on every request), the write lock, `readPassphrase_` and `passphraseMatches_`, and the setlist write path (`addSong_`, `createSetlistTab_`, `appendSetlistRow_`, `removeSong_`, `removeSetlistRow_`, which deletes a row and renumbers the later `posicion` cells, `setKey_` and `writeKeyCell_`, which writes one `tono` cell; mutations find their row by `id_tema` with the shared `findSongRow_`). The only file that opens `Config`. |
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
`?resource=config` replies `unknown_resource` after the pending deployment, with the message ending in `Known: catalog, jams`
(the route list of the code now in `src/Code.js`); an older list means the deployment still runs
an old `Code.gs`. Since `admin-passphrase-login`, also check that `curl -sL -d '{}' "$URL"` replies
`unknown_action` after the pending deployment, with the message ending in `Known: checkPassphrase, checkWriteAccess, readJams, addSong, checkSetlistWrite, removeSong, checkSetlistRemove, setKey, setSlotCount, assignSlot, clearSlot`
(the action list of `src/Post.js` since `admin-set-key`; a list ending in `checkSetlistRemove`
means the `admin-remove-song-from-setlist` version without set-key, one ending in
`checkSetlistWrite` means the `admin-add-song-to-setlist` version, `Known: checkPassphrase,
checkWriteAccess` the `apps-script-write-auth` one, `Known: checkPassphrase` alone the one before
it); an HTML page instead means the deployment has no `Post.gs` (no `doPost`).

#### Verify the paste

In the Apps Script editor, open each file and search (Ctrl+F) for its distinctive line:

| File | Must contain | Must not contain |
|---|---|---|
| `Code.gs` | `jams: readJams_,` | |
| `Jams.gs` | `var PUBLISHED_STATUS = 'PUBLICADA';` | |
| `Normalize.gs` | `function isoDateCell(` and `class ContractError` | |
| `Catalog.gs` | | `class ContractError` |
| `Post.gs` | `checkWriteAccess: { write: true, run: checkWriteAccess_ },` **and** `addSong: { write: true, run: addSong_ },` **and** `removeSong: { write: true, run: removeSong_ },` **and** `setKey: { write: true, run: setKey_ },` | `checkPassphrase: checkPassphrase_,` |

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

### Redeploy for the passphrase check (`admin-passphrase-login`)

The script is already deployed with the two reads. This adds one file, `Post`, with the POST
action `checkPassphrase`. **No existing file changes**: leave `Code.gs`, `Normalize.gs`,
`Catalog.gs`, `Jams.gs` and `appsscript.json` as they are.

1. Open the Sheet, then **Extensions → Apps Script**.
2. Add one new file: **+ → Script**, name it `Post` (the editor adds `.gs`), delete the empty
   `myFunction` it starts with, and paste the whole of `backend/apps-script/src/Post.js`.
3. **Save** (Ctrl+S). The project now has five script files, `Code`, `Normalize`, `Catalog`,
   `Jams` and `Post`, plus `appsscript.json`.
4. **Verify the paste**: `Post.gs` must contain `checkPassphrase: checkPassphrase_,` (the marker
   of that version; since `apps-script-write-auth` the table in **Updating the code later** has
   the current one). If it does not, paste it again over its whole content and save.
5. **Deploy → Manage deployments** → select the existing deployment → **Edit** (pencil icon) →
   **Version: New version** → **Deploy**. Never choose **New deployment**: it creates a new URL,
   and the app keeps calling the old one until `local.properties` is updated and the app rebuilt.
6. The scope is unchanged (`spreadsheets.currentonly`), so no new authorization is expected.
7. Confirm in the Sheet that the `Config` tab has a row with `clave` `passphrase` and a non-empty
   `valor`. Never paste the passphrase into chat or into any file.
8. Check the deployment (**Check a live deployment**, never the real passphrase):
   `curl -sL -d '{}' "$URL"` replies `unknown_action` ending in `Known: checkPassphrase`; a
   `checkPassphrase` with an obviously wrong value replies `invalid_passphrase` (a
   `passphrase_not_set` means step 7 is not done); `?resource=config` still ends in
   `Known: catalog, jams`.

(5 October 2026: the user deployed this as a **new deployment**, so the URL changed; the
orchestrator updated `local.properties` and the app was rebuilt. Later updates go through
**Manage deployments → New version** on that new deployment.)

### Redeploy for write auth (`apps-script-write-auth`)

The script is already deployed with the two reads and `checkPassphrase`. This replaces **one**
file, `Post`: it adds the passphrase guard on every action, the rate limit, the write lock and the
`checkWriteAccess` deploy check. **No other file changes**: leave `Code.gs`, `Normalize.gs`,
`Catalog.gs`, `Jams.gs` and `appsscript.json` as they are.

1. Open the Sheet, then **Extensions → Apps Script**.
2. Open `Post.gs`, select all its content and replace it with the whole of
   `backend/apps-script/src/Post.js`. **Save** (Ctrl+S).
3. **Verify the paste** (Ctrl+F in `Post.gs`): it must contain
   `checkWriteAccess: { write: true, run: checkWriteAccess_ },` and must **not** contain
   `checkPassphrase: checkPassphrase_,` (the old version's line). If either check fails, paste
   again over the whole content and save.
4. **Deploy → Manage deployments** → select the current deployment → **Edit** (pencil icon) →
   **Version: New version** → **Deploy**. Never choose **New deployment**: it creates a new URL,
   and the app keeps calling the old one.
5. The new code uses `CacheService` and `LockService`, which are expected to need no scope beyond
   `spreadsheets.currentonly`. If Google asks to authorize anyway, accept as the first time and
   say so in chat.
6. Tell the orchestrator that the new version is deployed. It runs the checks in **Check a live
   deployment** (only obviously wrong values on the command line; the valid write reads the
   passphrase from `local.properties` inside a script and never prints it). The valid check
   creates a tab named `_prueba_escritura` and deletes it within the same request: if the Sheet is
   open you may see it flash for about a second.

### Redeploy for write auth and add song, in one paste (`apps-script-write-auth` + `admin-add-song-to-setlist`)

User exception of 6 October 2026: the write-auth `Post.js` was not deployed before Part A of
`admin-add-song-to-setlist` was built on top of it, so **one** paste and **one** new version deploy
both. Follow the write-auth steps above with this file, and verify **both** markers. **No other
file changes**: `Code.gs`, `Normalize.gs`, `Catalog.gs`, `Jams.gs` and `appsscript.json` stay as
they are, so the musicians' GET reads cannot regress.

1. Open the Sheet, then **Extensions → Apps Script**.
2. Open `Post.gs`, select all its content and replace it with the whole of
   `backend/apps-script/src/Post.js`. **Save** (Ctrl+S).
3. **Verify the paste** (Ctrl+F in `Post.gs`): it must contain
   `checkWriteAccess: { write: true, run: checkWriteAccess_ },` (the write-auth guard and lock) and
   `addSong: { write: true, run: addSong_ },` (the add-song actions), and must **not** contain
   `checkPassphrase: checkPassphrase_,`. If any check fails, paste again over the whole content and
   save.
4. **Deploy → Manage deployments** → select the current deployment → **Edit** (pencil icon) →
   **Version: New version** → **Deploy**. Never **New deployment**.
5. No new scope is expected (`spreadsheets.currentonly` covers inserting and deleting tabs). If
   Google asks to authorize anyway, accept as the first time and say so in chat.
6. Tell the orchestrator. It runs the write-auth checks, then the add-song checks (**Check a live
   deployment**). The valid checks create `_prueba_escritura` and `_prueba_lista` and delete each
   within its own request: if the Sheet is open you may see them flash for about a second. No
   check adds a song to a real jam.

### Redeploy for remove song and set key, in one paste (`admin-remove-song-from-setlist` + `admin-set-key`)

User decision B1 (a), 6 October 2026: remove-song's server half and set-key's server half ship in
**one** paste and **one** new version. Both halves are in `src/Post.js` now (set-key's added in
session 075; SHA-1 of the file to paste in `PROGRESS.md`). Both features stay `in_progress` until
this deploy. **No other file changes**: `Code.gs`, `Normalize.gs`, `Catalog.gs`, `Jams.gs` and `appsscript.json` stay as they
are.

1. Open the Sheet, then **Extensions → Apps Script**.
2. Open `Post.gs`, select all its content and replace it with the whole of
   `backend/apps-script/src/Post.js`. **Save** (Ctrl+S).
3. **Verify the paste** (Ctrl+F in `Post.gs`): it must contain
   `removeSong: { write: true, run: removeSong_ },` (remove-song) **and**
   `setKey: { write: true, run: setKey_ },` (set-key), still contain
   `addSong: { write: true, run: addSong_ },`, and must **not** contain
   `checkPassphrase: checkPassphrase_,`. If any check fails, paste again over the whole content
   and save.
4. **Deploy → Manage deployments** → select the current deployment → **Edit** (pencil icon) →
   **Version: New version** → **Deploy**. Never **New deployment**.
5. No new scope is expected (`spreadsheets.currentonly` covers deleting and inserting rows and
   writing a cell).
6. Tell the orchestrator. It runs remove-song's checks (`checkSetlistRemove` creates and deletes
   `_prueba_lista` within one request), then set-key's (`checkSetlistWrite`, now also rewriting
   its marker's key, does the same). After the deploy, `curl -sL -d '{}' "$URL"` must end in
   `Known: checkPassphrase, checkWriteAccess, readJams, addSong, checkSetlistWrite, removeSong,
   checkSetlistRemove, setKey, setSlotCount, assignSlot, clearSlot`. No check removes a song from or changes a key in a real jam.

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
Android Studio may escape `:` as `\:`; the commands never print it. The decode uses Python
(5 October 2026: in this Git Bash the earlier `sed 's/\\:/:/g'` left the backslash in place, and
`tr -d` with a backslash deleted every `r`).

```bash
URL=$(python -c "print(next(l.split('=',1)[1].strip().replace(chr(92),'') for l in open('local.properties',encoding='utf-8') if l.startswith('bluesjam.appsScriptUrl=')))")
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

Passphrase check (`admin-passphrase-login`). Only obviously wrong values; never the real one:

```bash
curl -sL -d '{}' "$URL"                                              # unknown_action, "... Known: checkPassphrase, checkWriteAccess, readJams, addSong, checkSetlistWrite, removeSong, checkSetlistRemove, setKey, setSlotCount, assignSlot, clearSlot"
curl -sL -d '{"action":"checkPassphrase","passphrase":"definitely-wrong"}' "$URL"   # invalid_passphrase
curl -sL -d 'not json' "$URL"                                        # invalid_request
```

Write guard (`apps-script-write-auth`). Each wrong value counts one failed guess: 10 in a
10-minute window lock **every** action, the real admin's login included, until the window ends
(`rate_limited`). Never loop these:

```bash
curl -sL -d '{"action":"checkWriteAccess"}' "$URL"                   # invalid_passphrase (no passphrase)
curl -sL -d '{"action":"checkWriteAccess","passphrase":"definitely-wrong"}' "$URL"  # invalid_passphrase
```

The valid write (`checkWriteAccess` with the real passphrase, answer `{"schemaVersion":1,"ok":true}`)
is never typed on a command line: the passphrase would land in the shell history. The agent runs it
from a scratchpad Python script that reads `bluesjam.debugAdminPassphrase` from `local.properties`
(user approval W1), builds the body with `json.dumps` and prints only `ok` or the error code.

Add song (`admin-add-song-to-setlist`). Only obviously wrong values on the command line; each
counts one failed guess toward the same 10-per-10-minutes limit:

```bash
curl -sL -d '{"action":"readJams"}' "$URL"                           # invalid_passphrase (no passphrase)
curl -sL -d '{"action":"addSong","passphrase":"definitely-wrong","date":"2026-10-31","songId":"crossroads","key":"A"}' "$URL"  # invalid_passphrase
```

`readJams`, the non-mutating `addSong` probes (a bad date, a bad key, `1999-01-01` for
`unknown_jam`, a past jam for `jam_not_editable`, the upcoming date with `zz-no-existe` for
`unknown_song`) and `checkSetlistWrite` need the real passphrase, so the agent runs them from the
same kind of scratchpad Python script, printing only codes, counts and latencies (for `readJams`:
the jam count, the statuses and the song count per jam, never a name). Every probe fails a check
before anything is written, so none changes the Sheet.

Remove song (`admin-remove-song-from-setlist`). One obviously wrong value on the command line (it
counts one failed guess):

```bash
curl -sL -d '{"action":"removeSong","passphrase":"definitely-wrong","date":"2026-10-31","songId":"crossroads"}' "$URL"  # invalid_passphrase
```

The probes with the real passphrase (a bad date, a bad id, `1999-01-01` for `unknown_jam`, a past
jam for `jam_not_editable`, the upcoming date with `zz-no-existe` for `song_not_in_setlist`) and
`checkSetlistRemove` run from the scratchpad script, printing only codes, counts and latencies,
with a `readJams` song count per jam before and after to show no real jam changed. **Never call
`removeSong` with a real song id.**

Set key (`admin-set-key`). One obviously wrong value on the command line (it counts one failed
guess):

```bash
curl -sL -d '{"action":"setKey","passphrase":"definitely-wrong","date":"2026-10-31","songId":"crossroads","key":"A"}' "$URL"  # invalid_passphrase
```

The probes with the real passphrase (a bad date, a bad id, a bad key `H`, `1999-01-01` for
`unknown_jam`, a past jam for `jam_not_editable`, the upcoming date with `zz-no-existe` for
`song_not_in_setlist`) and `checkSetlistWrite` (which now also rewrites its marker's key to `F#m`)
run from the scratchpad script, printing only codes, counts and latencies. **Never call `setKey`
with a real song id.**

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


## Adjust-lineup deployment pending

The current `src/Post.js` implements `setSlotCount` and extends `checkSetlistWrite`: the temporary
marker loses guitar 2, loses harmonica, then regains harmonica; readback is verified and the tab
cleaned up. Paste the complete source into `Post.gs`, save, and use **Manage deployments > Edit >
New version > Deploy**, preserving the `/exec` URL. L1-L5 in `docs/specs/admin-adjust-lineup.md`
remain required: Known includes `setSlotCount`, wrong-passphrase refusal, extended deploy check,
validation-only probes, identical real-jam counts and SHA-256 before/after. Never call
`setSlotCount` with a real song id. Use the existing private scratchpad for credentials; print only
codes, counts and latencies. The local feature remains in_progress until live evidence exists.

## Assign-musician endpoint (local until the app is complete)

`assignSlot` is a guarded write under the shared script lock. The request is
`{"action":"assignSlot","passphrase":"…","date":"2026-10-31","songId":"crossroads","instrument":"guitar","ordinal":1,"name":"Tincho"}`.
Validation is deterministic and write-free through date, song id, instrument/ordinal, normalized
name, editable jam, tab and headers, unique song row, active slot, and empty target. Names are
trimmed and internal whitespace is collapsed; accepted names contain a letter or digit, are at
most 40 UTF-16 units, and reject controls, `;`, parentheses, formula-leading `=`, `+`, `-`, `@`,
and blank values. Error codes are `invalid_date`, `invalid_song`, `invalid_slot`, `invalid_name`,
`unknown_jam`, `duplicate_date`, `jam_not_editable`, `missing_header`, `duplicate_header`,
`song_not_in_setlist`, `duplicate_song`, `slot_not_in_lineup`, and `slot_taken`.

`ordinal` is 1-based within the active columns for that instrument, in canonical instrument-column
order, after excluding `-` columns. Thus guitar ordinal 1 selects `Guitarra 2` when `Guitarra 1`
is absent. An occupied cell is never overwritten. Success writes only that one cell, with plain
text formatting set first, and returns `{"schemaVersion":1,"ok":true,"column":0,"name":"Tincho"}`;
`column` is the zero-based slot index in the canonical seven-slot order and `name` is the
normalized spelling written. `checkSetlistWrite` exercises that same cell writer against its
temporary `_prueba_lista` tab, checks normalized readback and cleans up, without touching a real
jam. Deployment and live validation are deferred until app implementation is complete; do not
send a real song id to this action during local verification.

## Clear-slot endpoint (local until the app is complete)

`clearSlot` is a guarded, locked write. It re-resolves the requested 1-based active instrument
ordinal using U1 and compares the trimmed current cell text with `expectedName`. An already empty
cell returns `slot_empty`; a changed musician returns `slot_changed`; neither writes. Success clears
one cell and returns the canonical seven slot fields. Existing legacy names are compared as-is,
without new-assignment validation. `checkSetlistWrite` now assigns then clears the disposable marker
musician and checks the open readback; `finally` deletes `_prueba_lista` even if that check fails.
No real jam is touched. Deployment and live validation are deferred until app implementation is
complete; do not call this action with a real song id during local verification.
