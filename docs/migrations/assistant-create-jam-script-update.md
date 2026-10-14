# Script update: `createJam` (assistant-create-jam)

Until you do this, the app shows "El script de la planilla todavía no sabe crear jams. Actualizalo con la
guía y probá de nuevo." when you confirm **Crear la jam**. Nothing else breaks. The Sheet itself needs no
change: `createJam` appends a row to `Jams` (columns `fecha`, `hora`, `lugar`, `estado`) and creates no tab.

Only **one file** changed: `Post`.

1. Open the Sheet, then **Extensions → Apps Script**.
2. Replace the **whole content** of `Post.gs` with `backend/apps-script/src/Post.js`, save (Ctrl+S).
3. **Verify the paste** (Ctrl+F in `Post.gs`; a paste can silently leave the old content). It must contain
   both of these strings:
   - `createJam: { write: true, run: createJam_ },`
   - `var CREATE_JAM_CHECK_TAB`

   If one is missing, paste `Post.js` again over the whole content, save, and check again.
4. **Deploy → Manage deployments** → select the existing deployment → **Edit** (pencil icon) →
   **Version: New version** → **Deploy**. **Never** choose **New deployment**: it creates a second URL and
   the app would keep calling the old one. The URL stays the same, so `local.properties` does not change.
5. Tell the implementer/orchestrator the deploy is done. They check it **without the passphrase and without
   writing**: a POST of `{"action":"deployProbe"}` answers `unknown_action` and its message must end with
   `..., publishSetlist, checkPublish, createJam, checkCreateJam`.
6. Optional deploy check (needs the passphrase, writes only a temporary tab that it deletes):
   POST `{"action":"checkCreateJam","passphrase":"..."}` must answer `{"ok":true}`. It never touches the
   real `Jams` tab. Do not run `createJam` by hand against the real Sheet to test it: it would create a jam.
