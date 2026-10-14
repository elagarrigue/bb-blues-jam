# Script update: three jam states and changing the status (jam-status-created + admin-set-jam-status, D-26)

This is the **one** redeploy for both slices. It is owed after `admin-set-jam-status` is accepted. Until it is done:

- new app builds already hide a BORRADOR jam from musicians on their own device;
- the old script still serves the BORRADOR row's date and venue (never its songs) to old and new builds alike,
  which is today's behaviour;
- the new app's `Pasar a creada` / `Pasar a borrador` shows "El script de la planilla todavía no sabe cambiar el
  estado" and writes nothing; `Publicar lista` keeps working (the old script has `publishSetlist`);
- **nobody types `CREADA` in the `estado` column before the deploy check below (all three steps) has passed.** The old script does not know it:
  that jam stops being editable from the app and an old app version hides it.

## Before you deploy

Open the `Jams` tab and look at the `estado` of the 2026-10-31 jam. It must be `PUBLICADA`. Nothing changes for it:
the new code does not touch it and keeps serving it with its songs.

## What changes

Three files change: `Code`, `Jams` and `Post` (`Catalog` and `Normalize` are unchanged). Replace the **whole
content** of each.

1. Open the Sheet, then **Extensions → Apps Script**.
2. Replace the whole content of `Code.gs` with `backend/apps-script/src/Code.js`, of `Jams.gs` with
   `backend/apps-script/src/Jams.js` and of `Post.gs` with `backend/apps-script/src/Post.js`. Save each (Ctrl+S).
3. **Verify the paste** (Ctrl+F in each file; a paste can silently leave the old content). Each must contain:
   - `Jams.gs`: `var CREATED_STATUS`
   - `Code.gs`: `musicianJams_(`
   - `Post.gs`: `readAllJams_(` **and** `setJamStatus: { write: true, run: setJamStatus_ },`

   If one is missing, paste that file again over the whole content, save, and check again.
4. **Deploy → Manage deployments** → select the existing deployment → **Edit** (pencil icon) →
   **Version: New version** → **Deploy**. **Never** choose **New deployment**: it creates a second URL and the
   app would keep calling the old one. The URL stays the same, so `local.properties` does not change.

## Check the deploy (no passphrase, no write)

The orchestrator runs these after you deploy. None needs the passphrase and none writes.

1. `GET <your web app URL>?resource=jams&jamStatuses=9` answers
   `{"schemaVersion":1,"error":{"code":"invalid_jam_statuses", ...}}`. That proves `Code` and `Jams` are new. If it
   answers the jams instead, the deploy did not take; `internal_error` means one of the three files is missing or
   incomplete.
2. `POST {"action":"deployProbe"}` answers `unknown_action` with a message that ends
   `..., publishSetlist, checkPublish, createJam, checkCreateJam, setJamStatus`. That proves `Post` is new.
3. Read only: `GET <your web app URL>?resource=jams&jamStatuses=3` still returns the 2026-10-31 jam as `PUBLICADA`
   with its songs.

Optional, with the passphrase and a temporary tab only: the `checkPublish` action answers `{"ok":true}`. It publishes
and then moves a throwaway `Jams` row to `CREADA`, reads both back and deletes its tab.

## Existing rows

- The 2026-10-31 jam is PUBLICADA, so it is unaffected.
- After the redeploy, every `BORRADOR` row, past or future, **disappears for musicians**: the GET omits it, and so
  do Próxima jam and Anteriores. A row whose `estado` is empty or misspelled is omitted too (fail closed).
- To keep the old behaviour of a jam musicians should see as "fecha + la lista se está armando", change its
  `estado` to `CREADA`, **only after** the redeploy.
- A past jam that should still be listed in Anteriores also needs `CREADA` or `PUBLICADA`.
- The app creates new jams as `BORRADOR` (`createJam`). From the app, the admin can move the upcoming jam to
  `BORRADOR`, `CREADA` or `PUBLICADA` in any direction. Only the upcoming jam can be changed; a jam with a
  misspelled `estado` is fixed in the Sheet.

## Old app versions

An old build keeps working: without the `jamStatuses=3` parameter the script presents a `CREADA` jam as `BORRADOR`,
which that build already draws as "la lista se está armando". An old admin build sees a `CREADA` jam as `BORRADOR`
with its songs and can still publish it: `publishSetlist` stays in the script as a thin alias of `setJamStatus`
and answers as before.
