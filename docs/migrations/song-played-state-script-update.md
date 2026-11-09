# Script update: the admin marks songs as already played (song-played-state, D-31)

Until you do this, the app draws a played song muted if the Sheet already says so, but the new check button on each
row of Próxima jam ends in the card "El script de la planilla todavía no sabe marcar temas tocados. Actualizalo con la
guía y probá de nuevo." and nothing changes in the Sheet. Everything else in the app works without this update. The
Sheet itself needs no manual change: the `tocado` column is added by the script the first time the admin marks a song.

What the new version does:

- **A new action, `setSongPlayed`**, behind the passphrase and under the script lock. It writes `sí` (or clears the cell)
  in the `tocado` column of the **upcoming** jam's tab, and reads the cell back. A tab created by the app from now on
  already has that column; on an older tab the script adds the header `tocado` after the last column on the first mark.
- **The server refuses to change a played song.** `setKey`, `setSlotCount`, `assignSlot`, `clearSlot`,
  `addExtraParticipant`, `removeExtraParticipant` and `removeSong` answer `song_played` and write nothing for a played
  row, whichever app build sends them. `moveSong` still works.
- **The jams read carries one more key per setlist row, `played`**, after `extraParticipants` (`null` when the cell is
  empty or the column is missing). `schemaVersion` stays 1.
- `checkSetlistWrite` (the optional deploy check) has one more step that proves the refusal on its temporary tab.

Two files changed: **`Jams` and `Post`**. Both must be pasted.

1. Open the Sheet, then **Extensions → Apps Script**.
2. Replace the **whole content** of `Jams.gs` with `backend/apps-script/src/Jams.js`, and the **whole content** of
   `Post.gs` with `backend/apps-script/src/Post.js`. Save (Ctrl+S).
3. **Verify the paste** (Ctrl+F; a paste can silently leave the old content). `Post.gs` must contain:
   - `setSongPlayed: { write: true, run: setSongPlayed_ },`
   - `function requireNotPlayed_`

   and `Jams.gs` must contain:
   - `var PLAYED_FIELD`
   - `function isPlayedText_`

   If one is missing, paste that file again over the whole content, save, and check again.
4. **Deploy → Manage deployments** → select the existing deployment → **Edit** (pencil icon) →
   **Version: New version** → **Deploy**. **Never** choose **New deployment**: it creates a second URL and the app would
   keep calling the old one. The URL stays the same, so `local.properties` does not change.
5. Tell the implementer/orchestrator the deploy is done. They check it **without the passphrase and without writing**:
   a POST of `{"action":"deployProbe"}` answers `unknown_action` and its message must end with
   `..., checkFutureSetlistWrite, createSong, updateSong, deleteSong, checkCatalogWrite, setSongPlayed`; and a read-only
   `GET ?resource=jams` must return the same jams as before, now with `"played":null` in every setlist row.
6. Optional deploy check (needs the passphrase, writes only a temporary tab that it deletes): POST
   `{"action":"checkSetlistWrite","passphrase":"..."}` must answer `{"ok":true}`. Never run `setSongPlayed` by hand
   against the real Sheet.
7. Optional end-to-end check on the phone, in admin mode, on the real upcoming jam (this one **does** write the real tab):
   tap the check on one song, see it muted with `Ya tocado`, then tap it again. The cell ends empty (the `tocado` column
   stays, empty).

The redeploy is safe any time: installed app builds keep working. A build without this feature that tries to change a
played song gets the generic "la planilla rechazó el cambio" card, and its reads ignore the new `played` key.
