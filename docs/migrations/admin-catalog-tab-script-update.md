# Script update: the admin edits the song catalog (admin-catalog-tab, D-30)

Until you do this, the Temas tab lists the catalog but every `Agregar`, `Guardar` and `Borrar el tema` ends in the card
"El script de la planilla todavía no sabe editar el catálogo. Actualizalo con la guía y probá de nuevo." and nothing
changes in the Sheet. Everything else in the app works without this update. The Sheet itself needs no change.

What the new version does: four new actions, all behind the passphrase and under the script lock.

- `createSong` appends one `Catalogo` row (`id`, `titulo`, `artista`, `tono_default`). The server makes the id from the
  title (a slug); the app never sends one. It never writes `tempo`, `etiquetas`, `dificultad` or `songsterr_id`.
- `updateSong` changes only the title, artist or default key that differ. The id never changes.
- `deleteSong` removes the row, and refuses while the song is in the list of the upcoming jam or of a held-back jam.
- `checkCatalogWrite` is a self-cleaning deploy check on the temporary tab `_prueba_catalogo`.

Past jams, `Jams` and every jam tab are never touched. Only **one file** changed: `Post`.

1. Open the Sheet, then **Extensions → Apps Script**.
2. Replace the **whole content** of `Post.gs` with `backend/apps-script/src/Post.js`, save (Ctrl+S).
3. **Verify the paste** (Ctrl+F in `Post.gs`; a paste can silently leave the old content). It must contain all of
   these strings:
   - `createSong: { write: true, run: createSong_ },`
   - `deleteSong: { write: true, run: deleteSong_ },`
   - `var CATALOG_CHECK_TAB`
   - `function songSlug_`

   If one is missing, paste `Post.js` again over the whole content, save, and check again.
4. **Deploy → Manage deployments** → select the existing deployment → **Edit** (pencil icon) →
   **Version: New version** → **Deploy**. **Never** choose **New deployment**: it creates a second URL and the app
   would keep calling the old one. The URL stays the same, so `local.properties` does not change.
5. Tell the implementer/orchestrator the deploy is done. They check it **without the passphrase and without
   writing**: a POST of `{"action":"deployProbe"}` answers `unknown_action` and its message must end with
   `..., checkFutureSetlistWrite, createSong, updateSong, deleteSong, checkCatalogWrite`; a read-only
   `GET ?resource=catalog` must still return the same songs as before.
6. Optional deploy check (needs the passphrase, writes only a temporary tab that it deletes): POST
   `{"action":"checkCatalogWrite","passphrase":"..."}` must answer `{"ok":true}`. It never opens the real `Catalogo`.
   Never run `createSong`, `updateSong` or `deleteSong` by hand against the real Sheet.
7. Optional end-to-end check on the phone, in admin mode (this one **does** write the real `Catalogo`): in Temas add
   `Prueba Temas` / `BB Blues Jam` / `A`, edit its key to `B`, then delete it. The Sheet ends as it started.

The redeploy is safe any time: installed app builds keep working, and a build without the Temas tab never calls the new
actions.
