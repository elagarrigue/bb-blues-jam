# Migration guide: slots per instrument (`lineup-grow-any-instrument`, `lineup-max-per-instrument`)

For the person who owns the Google Sheet and the Apps Script project (the organizer). Do this once.
It has two parts, the Sheet and the script, and a checklist at the end to tell the agent what you
did. Nothing here is done by the agent: it does not edit the real Sheet, redeploy the script or call
the live write endpoint.

What changes for you: in `Cambiar formación` the admin will be able to add slots for **any**
instrument up to **3** (today only the guitar, up to 2), except `Batería` and `Teclados`, which stay at
**1** (`lineup-max-per-instrument`). To hold them, a jam tab gets **14 slot columns** instead of 7:
three each for Guitarra, Bajo, Voz and Armónica, one each for Batería and Teclados. A song that does not use a third slot of some
instrument has `-` in that cell, exactly like an unused second guitar does today.

Nothing breaks for the app you have installed if you do only part of this, with one exception
(below, "Order that matters"):

- A jam tab you never widen keeps working exactly as today (7 slot columns). In it, the admin can
  still add and remove up to the old default (2 guitars, 1 of each other instrument); asking for more
  shows `Esta hoja todavía no tiene columnas para ese cupo...` and writes nothing.
- The old header spellings keep working: `Bajo`, `Batería`, `Voz`, `Armónica`, `Teclados` count as
  `Bajo 1`, `Batería 1`, `Voz 1`, `Armónica 1`, `Teclados 1`. You do not have to rename them.
  (Never have **both** spellings of the same slot in one tab: the script reports `duplicate_header`.)

## Order that matters

The new script and the new app each understand the other's old version for **reading**, so the
order is not a hard requirement, but one order is cleaner.

- **New app against the old script (not yet deployed): works.** The app accepts the old seven-key
  `slots` objects as the default seven slots, with the other eleven columns as "not in the lineup".
  The editor's `+` past the old default is then answered by the old script as `invalid_count` (a
  failure card); nothing is written.
- **Old app (already installed) against the new script: reads keep working.** The new script sends
  the five old keys (`bass`, `drums`, `vocals`, `harmonica`, `keyboards`, equal to `Bajo 1`...
  `Teclados 1`) next to the new ones. Writes are different: the old app checks a confirmed `setSlotCount` or `clearSlot` answer's `slots` for exactly its seven keys, so the new script's longer object is `Unavailable` to it, and `assignSlot` compares the answer's `column` with its own seven-column index, which differs from the new script's index for every instrument but the guitar. 

  **So: update the script and the app together** (script first, then install the app), so nobody
  uses the old app against the new script. Concretely, with the old app against the new script:
  reading, adding a song, changing the key or the order work; `Cambiar formación` (lineup changes)
  and clearing a slot **are written to the Sheet** but the old app reports a failure and shows the
  old state until its next refresh; assigning a musician works for the two guitars and, for any other
  instrument, is written but reported as a failure.
- Widen the Sheet tabs (Part A) whenever you like, before or after: the script handles both layouts.

## Part A: widen the jam tab (the Sheet)

Do this for the tab of the **upcoming** jam, the one the admin edits (`Jams` row with the earliest
future `fecha`, the tab is named after it, e.g. `2026-10-31`). Past jam tabs can stay as they are:
the app never edits a past jam.

The 14 slot headers, **exactly** as written (capital letters and accents count, one space before the
digit), after `tono` and before `Otros`, in this order:

| # | Header | # | Header |
|---|---|---|---|
| 1 | `Guitarra 1` | 8 | `Voz 1` |
| 2 | `Guitarra 2` | 9 | `Voz 2` |
| 3 | `Guitarra 3` | 10 | `Voz 3` |
| 4 | `Bajo 1` | 11 | `Armónica 1` |
| 5 | `Bajo 2` | 12 | `Armónica 2` |
| 6 | `Bajo 3` | 13 | `Armónica 3` |
| 7 | `Batería` (or `Batería 1`) | 14 | `Teclados` (or `Teclados 1`) |

Your tab today has, in this order: `posicion`, `id_tema`, `titulo`, `artista`, `tono`, `Guitarra 1`,
`Guitarra 2`, `Bajo`, `Batería`, `Voz`, `Armónica`, `Teclados`, `Otros`. The result has 5 + 14 + 1 = 20
columns. A tab you already widened to 18 columns (with `Batería 2-3` and `Teclados 2-3`) keeps working
too: the script reads those columns, and the app never grows Batería or Teclados past one.

1. Open the Sheet and the tab of the upcoming jam.
2. Insert a column **to the right of `Guitarra 2`** and give it the header `Guitarra 3`.
3. Rename the header `Bajo` to `Bajo 1` (optional, both work). Insert two columns to its right:
   `Bajo 2`, `Bajo 3`.
4. Do the same for `Voz` and `Armónica`: rename to `... 1` (optional) and insert `... 2` and `... 3` to
   the right of each. Leave `Batería` and `Teclados` as they are (one column each).
5. **Fill every new cell of every existing song with a single dash `-`.** This is the step that
   matters: an **empty** cell means "open slot", so an empty `Guitarra 3` would show a third guitar
   slot to every musician. The 7 new columns are `Guitarra 3`, `Bajo 2`, `Bajo 3`, `Voz 2`,
   `Voz 3`, `Armónica 2`, `Armónica 3`. Select each
   new column's data cells, type `-` and press Ctrl+Enter, or fill them all at once.
6. Do not touch the other columns. The script finds columns by header name, so the order is only for
   your own reading; `posicion` ... `tono` and `Otros` stay where they are.

New songs added from the app after this get the 7 default slots open and the 7 extra cells written
`-` for you. A new jam tab created by the app already has the 14 slot headers (20 columns).

## Part B: update the Apps Script

Three files change: `Normalize`, `Jams` and `Post`. `Code`, `Catalog` and `appsscript.json` stay as
they are. This follows `backend/apps-script/README.md`, section "Updating the code later".

1. Open the Sheet, then **Extensions → Apps Script**.
2. Replace the **whole content** of each of these three files with its namesake from
   `backend/apps-script/src/`, then save (Ctrl+S):
   - `Normalize.gs` ← `src/Normalize.js`
   - `Jams.gs` ← `src/Jams.js`
   - `Post.gs` ← `src/Post.js`
3. **Verify the paste** (Ctrl+F in each file; copy-paste can silently leave a file on its old
   content). Each file must contain:

   | File | Must contain |
   |---|---|
   | `Normalize.gs` | `(spec.aliases \|\| [])` |
   | `Jams.gs` | `var NOT_IN_LINEUP_ = '-';` and `{ header: 'Teclados 3', field: 'keyboards3', required: false },` |
   | `Post.gs` | `var SLOT_CEILINGS_` and `instrument_not_expandable` |

   The other two checks of the README table still apply: `Code.gs` has `jams: readJams_,` and
   `Catalog.gs` does **not** have `class ContractError`. If any check fails, paste that file again
   over its whole content, save, and check again.
4. **Deploy → Manage deployments** → select the existing deployment → **Edit** (pencil icon) →
   **Version: New version** → **Deploy**. **Never** choose **New deployment**: it creates a second URL
   and the app would keep calling the old one. The URL stays the same, so `local.properties` does
   not change.
5. If Google asks to authorize, accept as the first time (the scope does not change, so it usually
   does not ask).
6. This version changes no route and no action name, so the `Known: ...` list that
   `curl -sL -d '{}' "$URL"` prints is the same before and after: it cannot tell you whether the
   deploy took. The paste check in step 3 and the read-only check below are what tell you.

## Part C: second deploy (`lineup-max-per-instrument`)

If you already deployed Part B before this slice, only **one file** changed since: `Post`. Your
2026-10-31 tab already has the 14 columns, so the Sheet needs nothing.

1. Open the Sheet, then **Extensions → Apps Script**.
2. Replace the **whole content** of `Post.gs` with `backend/apps-script/src/Post.js`, save (Ctrl+S).
3. Verify the paste: `Post.gs` must contain `var SLOT_CEILINGS_` (and no longer
   `MAX_SLOTS_PER_INSTRUMENT_`).
4. **Deploy → Manage deployments** → the existing deployment → **Edit** → **Version: New version** →
   **Deploy**. Never **New deployment**; the URL does not change.

After it, a `setSlotCount` for `drums` or `keyboards` with count 2 is answered `invalid_count`, and new
jam tabs are created with 14 slot columns. The agent does **not** try `setSlotCount` live; it can only
confirm the deploy took by the read-only GET, which does not show it, so the paste check is the proof.
The app works against the old script too: the editor stops at each ceiling by itself.

## Tell the agent

When both parts are done (or only Part B, if you do not want to widen a tab yet), tell the
orchestrator in chat:

- [ ] Part B done: the three files pasted and the deployment updated as a **new version** of the
      existing deployment.
- [ ] Part A done (or skipped), and for which tab (the `fecha`).
- [ ] Nothing was typed over any musician name; only `-` went into the new cells.

Never paste the deployment URL or the passphrase into the chat or into any file in the repository.

## What the agent does next (read-only live check)

Only after you confirm the checklist, the agent runs a **read-only** check against the real script:
`GET ?resource=jams`, which writes nothing. It records only codes and counts, never a musician name,
the passphrase or the URL:

- the response is `schemaVersion` 1 with no error;
- every song of a published jam has an 18-key `slots` object, unchanged by the ceilings (`guitar1` ... `keyboards3`);
- on a widened tab, the 11 new keys are `"-"` for every existing song, and names and open slots are
  unchanged from before;
- on a tab you did not widen, the same 18 keys come back with the 11 new ones as `"-"`;
- a before/after hash of the names and the song count shows no real jam changed.

It does **not** call `setSlotCount`, `addSong` or any other write against your real jams. The
disposable deploy checks (`checkSetlistWrite` and its siblings) work only on their own temporary
tab, which they delete afterwards; the agent runs one of them only if you ask for it.
