# Script update: `addSong` accepts a held-back jam (assistant-proposal-targets, D-29)

Until you do this, `Crear una jam nueva con esta lista` with a date **behind** the upcoming jam (a held-back date) creates
the jam as BORRADOR and then stops with "La jam se creó, pero el script de la planilla todavía no sabe cargar listas en una
jam en espera. Actualizalo con la guía y reintentá." The jam stays in the Sheet with no songs. Creating a jam that
becomes the next one, `Agregar` and `Reemplazar` work without this update. The Sheet itself needs no change.

What the new version does: `addSong` accepts any future jam (the upcoming one or a held-back one) instead of only the
upcoming one, so the assistant can load a list into a jam it has just created. Every other setlist change
(`removeSong`, `moveSong`, `setKey`, `setSlotCount`, `assignSlot`, `clearSlot`, the extras) and publishing stay
upcoming-only. It also adds the deploy check `checkFutureSetlistWrite`. Past jams are never touched.

Only **one file** changed: `Post`.

1. Open the Sheet, then **Extensions → Apps Script**.
2. Replace the **whole content** of `Post.gs` with `backend/apps-script/src/Post.js`, save (Ctrl+S).
3. **Verify the paste** (Ctrl+F in `Post.gs`; a paste can silently leave the old content). It must contain all of
   these strings:
   - `checkFutureSetlistWrite: { write: true, run: checkFutureSetlistWrite_ },`
   - `var FUTURE_SETLIST_CHECK_TAB`
   - `function futureJamIn_`

   If one is missing, paste `Post.js` again over the whole content, save, and check again.
4. **Deploy → Manage deployments** → select the existing deployment → **Edit** (pencil icon) →
   **Version: New version** → **Deploy**. **Never** choose **New deployment**: it creates a second URL and the app
   would keep calling the old one. The URL stays the same, so `local.properties` does not change.
5. Tell the implementer/orchestrator the deploy is done. They check it **without the passphrase and without
   writing**: a POST of `{"action":"deployProbe"}` answers `unknown_action` and its message must end with
   `..., checkJamEdit, checkFutureSetlistWrite`; a read-only `GET ?resource=jams&jamStatuses=3` must still show the same
   jams as before.
6. Optional deploy check (needs the passphrase, writes only temporary tabs that it deletes): POST
   `{"action":"checkFutureSetlistWrite","passphrase":"..."}` must answer `{"ok":true}`. It never opens the real `Jams`
   tab or a real jam tab. Never run `addSong` by hand against the real Sheet.

The redeploy is safe any time: the change only accepts more, and installed app builds keep working.
