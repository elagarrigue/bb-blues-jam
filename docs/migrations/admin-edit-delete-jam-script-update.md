# Script update: `updateJam`, `deleteJam` and `checkJamEdit` (admin-edit-delete-jam)

Until you do this, the app shows "El script de la planilla todavía no sabe editar ni borrar jams. Actualizalo con la
guía y probá de nuevo." when you confirm **Guardar** or **Borrar la jam**, and a status change on a held-back
jam card fails with the "jam changed" card. Nothing is written in either case. The Sheet itself needs no change.

What the new version does: `updateJam` edits the `fecha`, `hora` and `lugar` of a future jam (renaming its tab when the
date changes); `deleteJam` deletes a future jam's tab and row; `setJamStatus` also accepts BORRADOR and CREADA on
held-back jams. Past jams are never touched.

Only **one file** changed: `Post`.

1. Open the Sheet, then **Extensions → Apps Script**.
2. Replace the **whole content** of `Post.gs` with `backend/apps-script/src/Post.js`, save (Ctrl+S).
3. **Verify the paste** (Ctrl+F in `Post.gs`; a paste can silently leave the old content). It must contain all of
   these strings:
   - `updateJam: { write: true, run: updateJam_ },`
   - `deleteJam: { write: true, run: deleteJam_ },`
   - `var JAM_EDIT_CHECK_TAB`
   - `function requireFutureJam_`

   If one is missing, paste `Post.js` again over the whole content, save, and check again.
4. **Deploy → Manage deployments** → select the existing deployment → **Edit** (pencil icon) →
   **Version: New version** → **Deploy**. **Never** choose **New deployment**: it creates a second URL and the app
   would keep calling the old one. The URL stays the same, so `local.properties` does not change.
5. Tell the implementer/orchestrator the deploy is done. They check it **without the passphrase and without
   writing**: a POST of `{"action":"deployProbe"}` answers `unknown_action` and its message must end with
   `..., setJamStatus, updateJam, deleteJam, checkJamEdit`; a read-only `GET ?resource=jams&jamStatuses=3` must still
   show the same jams as before.
6. Optional deploy check (needs the passphrase, writes only temporary tabs that it deletes): POST
   `{"action":"checkJamEdit","passphrase":"..."}` must answer `{"ok":true}`. It never opens the real `Jams` tab.
   Never run `updateJam`, `deleteJam` or `setJamStatus` by hand against the real Sheet.

The redeploy is safe any time: installed app builds never call the new actions until the Jams tab offers them, and the
`setJamStatus` change only accepts more.
