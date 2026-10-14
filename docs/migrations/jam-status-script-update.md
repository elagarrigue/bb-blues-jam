# Script update: three jam states (jam-status-created, D-26)

**Do not do this yet.** The script changes of this slice go out in **one** redeploy together with the next slice,
`admin-set-jam-status`, which edits `Post.gs` again; that slice extends this guide. Until then:

- new app builds already hide a BORRADOR jam from musicians on their own device;
- the old script still serves the BORRADOR row's date and venue (never its songs) to old and new builds alike,
  which is today's behaviour;
- **nobody types `CREADA` in the `estado` column before the redeploy.** The old script does not know it: that
  jam stops being editable from the app and an old app version hides it.

## What changes

Three files change: `Code`, `Jams` and `Post`. Replace the **whole content** of each.

1. Open the Sheet, then **Extensions → Apps Script**.
2. Replace the whole content of `Code.gs` with `backend/apps-script/src/Code.js`, of `Jams.gs` with
   `backend/apps-script/src/Jams.js` and of `Post.gs` with `backend/apps-script/src/Post.js`. Save each (Ctrl+S).
3. **Verify the paste** (Ctrl+F in each file; a paste can silently leave the old content). Each must contain:
   - `Jams.gs`: `var CREATED_STATUS`
   - `Code.gs`: `musicianJams_(`
   - `Post.gs`: `readAllJams_(`

   If one is missing, paste that file again over the whole content, save, and check again.
4. **Deploy → Manage deployments** → select the existing deployment → **Edit** (pencil icon) →
   **Version: New version** → **Deploy**. **Never** choose **New deployment**: it creates a second URL and the
   app would keep calling the old one. The URL stays the same, so `local.properties` does not change.

## Check the deploy (no passphrase, no write)

Open `<your web app URL>?resource=jams&jamStatuses=9` in a browser, or `GET` it with curl. The new script answers
`{"schemaVersion":1,"error":{"code":"invalid_jam_statuses", ...}}`. The old script answers the jams instead, which
means the deploy did not take. `internal_error` means one of the three files is missing or incomplete.

## Existing rows

- The 2026-10-31 jam is assumed to be PUBLICADA, so it is unaffected. Look at its `estado` first.
- After the redeploy, every `BORRADOR` row, past or future, **disappears for musicians**: the GET omits it, and so
  do Próxima jam and Anteriores. A row whose `estado` is empty or misspelled is omitted too (fail closed).
- To keep the old behaviour of a jam musicians should see as "fecha + la lista se está armando", change its
  `estado` to `CREADA`, **only after** the redeploy.
- A past jam that should still be listed in Anteriores also needs `CREADA` or `PUBLICADA`.
- The app creates new jams as `BORRADOR` (`createJam`) and publishes from `BORRADOR` or `CREADA`.

## Old app versions

An old build keeps working: without the `jamStatuses=3` parameter the script presents a `CREADA` jam as `BORRADOR`,
which that build already draws as "la lista se está armando". An old admin build sees a `CREADA` jam as `BORRADOR`
with its songs and can still publish it.
