# Script update: aviso de otro admin en el mismo tema (admin-presence-notice, D-32)

Until you do this, the app simply never shows the notice "Otro admin está editando este tema": the announce gets
`unknown_action`, the app ignores it silently, and nothing else changes. The Sheet needs no manual change and the script
never writes the Sheet for this feature: presence lives only in Apps Script's cache and expires by itself after 120 s.

What the new version does:

- **A new action, `announcePresence`**, behind the passphrase and **without** the script lock. An admin's open song detail
  announces itself every 30 s; the answer carries only how many other admins announced the same song in the last 120 s.
- It refuses nothing and authorizes nothing: no write is ever blocked because of presence (last write wins, as before).

One file changed: **`Post`**.

1. Open the Sheet, then **Extensions → Apps Script**.
2. Replace the **whole content** of `Post.gs` with `backend/apps-script/src/Post.js`. Save (Ctrl+S).
3. **Verify the paste** (Ctrl+F; a paste can silently leave the old content). `Post.gs` must contain:
   - `announcePresence: { write: false, run: announcePresence_ },`
   - `function announcePresence_`

   If one is missing, paste the file again over the whole content, save, and check again.
4. **Deploy → Manage deployments** → select the existing deployment → **Edit** (pencil icon) →
   **Version: New version** → **Deploy**. **Never** choose **New deployment**: it creates a second URL and the app would
   keep calling the old one. The URL stays the same, so `local.properties` does not change.
5. Tell the implementer/orchestrator the deploy is done. They check it **without the passphrase and without writing**:
   a POST of `{"action":"deployProbe"}` answers `unknown_action` and its message must end with
   `..., checkCatalogWrite, setSongPlayed, announcePresence`.
6. Optional check on two admin phones: open the same song of the upcoming jam on both; each shows the notice within 30 s.
   Leave on one; the other's notice disappears within 30 s. Never call `announcePresence` by hand with a real passphrase
   in a shared place.

The redeploy is safe any time: installed app builds keep working, and a build without this feature never calls the action.
