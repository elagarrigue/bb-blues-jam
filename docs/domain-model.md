# Domain Model

Terms used here are defined in `../CONTEXT.md`. This document describes entities, relationships,
states, and the rules that govern transitions.

## Core Concepts

```
Jam(date, startTime, venue, status: DRAFT | CREATED | PUBLISHED, setlist: Setlist)   // date is the identity
Setlist = Available(songs: List<JamSong>, droppedRows) | Withheld | Unavailable(problem)
SetlistProblem = MISSING_TAB | INVALID_TAB | INVALID_ROWS | UNKNOWN
JamSong(position, songId, title, artist, key, lineup: Lineup,
        extraParticipants: List<ExtraParticipant> = [])
Lineup(slots: List<Slot>)                // at most 3 of each instrument (D-18, amended)
Slot(instrument, musicianName?)          // open when musicianName is null
ExtraParticipant(name, instrument)       // free-text instrument; never open (D-18)
Song(id, title, artist, defaultKey, tempo?, tags, difficulty?,
     mbid?, artistArea?, deezerTrackId?, previewUrl?, artworkUrl?, songsterrId?)
```

`Key` follows the **Keys** format and `SongId` the `Catalogo.id` slug of `sheet-schema.md`. The
types live in `:core:model` (package `com.bbbjam.core.model`), and `Jam.isHistorical(today)` takes
today's date from the caller. In an available setlist, positions are at least 1, unique and
ascending; gaps are allowed and positions are never renumbered by the read path, because they are
the Sheet's `posicion`, the read identity of a JamSong (user approval P4 of `jams-repository-cache`,
2 October 2026). The Sheet should still number 1..n; a gap means a row was dropped as invalid.
Setlist mutations locate a row by its song id, not its position, and a removal renumbers the later
songs so the Sheet stays 1..n (user decision R1 (a), `admin-remove-song-from-setlist`).

### Jam

One monthly session: a date, a start time, a venue, a status, and an ordered setlist. At most one Jam is upcoming
at a time. Jams whose date has passed are historical and read-only.

### Setlist

What a reader gets of a Jam's songs is a state, never a bare list, so "not published" or "broken"
is never read as "no songs" (`jams-repository-cache`, decision D1):

- **Available** — the songs in position order, plus how many tab rows were dropped as invalid
  (`droppedRows`), so a screen can say the list is incomplete instead of showing a silent gap. A
  published tab with headers and no rows is an empty available setlist.
- **Withheld** — the Jam is a draft, so the list is not served to musicians. A published Jam is
  never withheld; a draft may be withheld (a musician's read) or available (the admin's read,
  `readJams`, since `admin-add-song-to-setlist`; a draft with no tab yet is an empty available
  setlist for the admin).
- **Unavailable(problem)** — the Jam is published but the setlist cannot be shown: its tab is
  missing (`MISSING_TAB`), lacks or doubles a header (`INVALID_TAB`), has rows of which none is
  valid (`INVALID_ROWS`, never an empty available list), or the reason is unknown (`UNKNOWN`).

### JamSong

One Song scheduled in one Jam. Carries the `position` in the setlist, the `key` chosen for that
night, and the `lineup` of slots. The key lives here rather than on Song because a jam's key is
whatever suits the person singing that night (D-08).

### Slot

One instrument position within a JamSong. Open when `musicianName` is null, filled otherwise. There
is no Musician entity: a musician is a name string on a slot. This is deliberate — musicians have no
accounts (D-05), so modeling them as entities would imply identity the product does not have.

### Song

A catalog entry, reused across Jams. The first group of fields, including `songsterrId`, is
admin-maintained in the Sheet; `songsterrId` is typed by the admin (D-10) and is never fetched. The
last five (`mbid`, `artistArea`, `deezerTrackId`, `previewUrl`, `artworkUrl`) are optional
enrichment, fetched in the background and cached, never stored in the Sheet (D-09). Every optional
field may be absent and the app must render correctly without it.

`artistArea` earns its place: it lets "blues nacional argentino" be filtered by data rather than by
the model's memory, which is exactly where an LLM is weakest on this repertoire (D-14).

## Relationships

- A **Jam** has exactly one ordered setlist of **JamSongs**. Order is explicit via `position`.
- **Adding a song** (`admin-add-song-to-setlist`, `SetlistRepository.addSong`) appends a catalog
  song to a future jam (the upcoming one, or a held-back one since `assistant-proposal-targets`, D-29), at `position` = 1 + the largest existing one, in the key the admin
  sent (the picker sends the catalog default, K1 (a); D-08) and with the default lineup, all seven
  slots open (D-18). The same song twice in one setlist is refused. Adding never creates a jam.
- A **JamSong** references exactly one **Song** and owns exactly one **Lineup**.
- A **Lineup** is a list of **Slots**. Default: 2 guitars, 1 bass, 1 drums, 1 vocals, 1 harmonica,
  1 keyboards — adjustable per JamSong by removing open Slots or adding Slots up to 3 of each instrument
  (D-18, amended 12 October 2026; `Lineup.maxCount`, distinct from `Lineup.defaultCount`).
- A **JamSong** may also list **Extra Participants** ("Otros"): a name and a free-text instrument
  for anyone playing outside the Lineup. They are never open and never count for the open-slot
  filter (D-18).
- A **Song** may appear in many **Jams**, with a different key and a different lineup each time.
- Deleting a JamSong from a setlist does not affect the Song in the catalog.
- **Deleting a Song** (`admin-catalog-tab`, D-30) is refused while it is in the setlist of the upcoming jam or of a
  held-back jam, by the app and again by Apps Script. A Song only in past jams can be deleted: each jam tab keeps
  plain-text copies of title and artist, and its id is never given to another song.

Without a declared lineup, "open slot" has no definition and the app's primary filter cannot exist
(D-06). The lineup is therefore not optional metadata; it is what makes the main screen work.

## States and Lifecycles

### Jam status

```
DRAFT (BORRADOR) <──> CREATED (CREADA) <──> PUBLISHED (PUBLICADA) ──(date passes)──> archived (implicit)
DRAFT (BORRADOR) <───────────────────────> PUBLISHED
```

Three states (D-26, `jam-status-created`, which amends D-04 and D-25):

- **DRAFT (`BORRADOR`).** The jam does not exist for musicians: no date, no list, not in Próxima jam
  and not in Anteriores, for a future or a past jam. The admin sees and edits it with a `BORRADOR`
  badge. `createJam` (D-25) still writes this state. Hidden in three places: Apps Script (the anonymous
  GET omits the row, an empty or misspelled `estado` too), the mapper (an anonymous read drops a valid
  BORRADOR row, `MappedJams.hiddenDrafts`) and the musician screens (`JamsSnapshot.forMusicians()`).
  When the earliest future jam is a draft and a later one is CREATED or PUBLISHED, a musician sees the
  later one as the upcoming jam; the admin still sees the draft as upcoming, because writes target it.
- **CREATED (`CREADA`).** Musicians see the date, time and venue, and a message saying the list is
  being assembled — not an empty list, which would be misread as "no songs". Every musician screen
  reads `Jam.setlistForMusicians()` (`unpublished-setlist-state`), which is `Withheld` for any jam that
  is not PUBLISHED whatever setlist the read returned; only the admin slices read `Jam.setlist`
  directly, behind the admin flag. The admin sees the songs, with a `CREADA` badge. A past CREATED jam
  is listed in Anteriores with "La lista de esta jam no se publicó."
- **PUBLISHED.** The setlist is visible to musicians. The admin may still edit; edits to a published
  jam are visible immediately. Since `admin-set-jam-status` the admin can also leave PUBLISHED from the app
  (to CREATED or DRAFT), after a confirmation; musicians stop seeing what was withdrawn on their next refresh.
  The upcoming jam can move in any direction between the three states (`setJamStatus`); the authority row
  "Jam status" is the app for the upcoming jam, by `setJamStatus` and `publishSetlist`.
- **Archived** is not a stored status. A jam is historical when its date has passed; past jams are
  read from the Sheet, which is their authority. "Today" is the date in Buenos Aires
  (`JamCalendar`), so a jam is upcoming through its own night and historical from 00:00 of the
  next day. In a past jam an empty slot means "not recorded": the lineup keeps only filled slots,
  and no past jam ever shows an open slot.
- **More than one future jam.** The earliest one is the upcoming jam; later ones are held back
  (shown in neither list) and reported in the refresh log. Since `admin-edit-delete-jam` (D-28) the admin edits the
  date, time and venue of a future jam or deletes it from the Jams tab; a date change moves the jam with its tab, and an
  edit never changes which jam is the upcoming one. A held-back jam can also change status (not to PUBLICADA), and the app can load songs into it (`addSong` only, D-29: the app owns the setlists of future jams it builds, never past ones; D-04 intact).

The transition DRAFT or CREATED → PUBLISHED is admin-initiated and writes through to the Sheet. Until `admin-set-jam-status`, publishing is the only status change made from the app; the others are made in the Sheet.

### Slot state

```
open ──assign musician──> filled ──clear──> open
```

Both transitions are admin-only. A slot has no intermediate or reserved state: reservation would
require musician identity and concurrency control, which D-05 removes by design.

### Authority and direction of travel

No entity is written from both sides. This is the rule that removes distributed-conflict handling
from the project entirely (D-04).

| Entity | Authority | Direction |
|---|---|---|
| Song catalog | App and Sheet (D-30) | title, artist and default key: app → Sheet via Apps Script (add, edit, delete); tempo, tags, difficulty and `songsterrId`: Sheet only; both may be edited, last write wins |
| Past jams | Sheet | Sheet → app, read-only |
| Future jam rows (D-25, D-28) | App | create, edit (date, time, venue) and delete via Apps Script; past jams stay in the Sheet |
| Upcoming setlist | App | app → Sheet via Apps Script |
| Published status | App | app → Sheet |

## Important Scenarios

### Building a setlist

The admin opens the upcoming jam in DRAFT, adds songs from the catalog, sets a key on each, adjusts
lineups where the default does not fit, assigns musicians to slots, reorders, and publishes. Every
one of these operations exists as a repository function or deeplink from phase 1, because the phase
2 assistant must be able to perform each of them (D-13).

### Reordering the setlist

`SetlistRepository.moveSong(jamDate, songId, toPosition)` changes the absolute 1-based position of
one song in the upcoming jam. A confirmed Room mirror moves its slots and extras with the song in a
single transaction. The admin sees a Sending-only optimistic order and a failure card on rejection;
musicians continue to read the confirmed cache until refresh. Sheet rows are identified by song id,
and their positions are written in safe batches without duplicate positions.

### Finding a place to play

A musician opens the app, taps their instrument's filter chip, and sees only the songs with an open
slot for it, with a count of how many songs matched out of how many. Tapping a row opens the song
detail, open slots listed first (`song-detail-replaces-row-expansion`; rows no longer expand).

### Changing a key at the last minute

The admin edits the key on a JamSong of a published jam. The change is immediately visible to
everyone. The catalog Song's `defaultKey` is untouched — that night's key is a property of the
JamSong, not the Song.

As built (`admin-set-key`, `SetlistRepository.setKey(jamDate, songId, key)`): only the upcoming
jam's JamSongs (draft or published) can change key. Apps Script finds the row by song id and
writes only its `tono` cell; the catalog is never read or written (D-08). The cache changes only
after the server confirms. "Immediately visible" means the next read: the admin's own device shows
the new key at once (optimistic, user decision O1, reverting on failure), and musicians see it on
their next refresh (faster refresh is `live-refresh-during-jam`).

### Adjusting a lineup

`SetlistRepository.setSlotCount` changes an upcoming JamSong's instrument count from zero to the
default, in draft or published state. Removal drops open slots last-first; restoration reopens
absent columns first-first. Filled slots are never removed (`slot_filled`); the admin must clear
one first once that mutation exists. Names stay in their Sheet columns, even when restoration
changes their guitar ordinal. Extras are independent and unchanged. The server's seven cells
replace the confirmed local lineup after success. Sending changes temporarily overlay the admin's
strip, panel, filter and counts; failure reverts and leaves a card. Musicians keep the confirmed
lineup. A song with every slot removed remains a valid song with no open slots.

### Assigning a musician

`SetlistRepository.assignSlot(date, songId, instrument, ordinal, musicianName)` fills one fixed,
open slot on the upcoming jam. `SlotPosition` is the 1-based ordinal among that instrument's
current lineup columns, before open/filled display partitioning; `MusicianName.parseOrNull` trims
and collapses whitespace and validates a plain name of at most 40 UTF-16 units. Apps Script
re-resolves that ordinal against active U1 columns and refuses occupied cells. Room changes only
after the server confirms; a Sending entry overlays the admin's lineup until confirmation, and a
failure reverts it. Suggestions use cached names from the current and past jams; `Otros` names are
included in suggestions but never become slots. No `Musician` entity is introduced.

### Clearing a filled slot

`SetlistRepository.clearSlot(date, songId, instrument, ordinal, expectedName)` frees one filled slot
on the upcoming jam. The ordinal is the 1-based position in the instrument's original active
lineup, before open-first display ordering. The server compares the current cell with the expected
cached name and refuses an empty or changed target without writing. During Sending, only the admin
projection shows the slot as open; Room changes only after a valid server response and failed writes
restore the confirmed name. `Otros` is never a slot and musicians remain read-only. The expected name
is compared as legacy display text, without applying rules intended for newly assigned names.

### The same song in a later jam

Adding a Song already played in a past jam creates a new JamSong with a fresh lineup and its own
key. The archive makes the prior appearance visible, which is what turns repetition into a
deliberate choice.

## Edge Cases

- **A song with no open slots** still appears in the list; it is simply excluded when a filter is
  active. Full songs are information, not noise.
- **A lineup with zero slots for an instrument** is valid — a song may need no harmonica. The
  instrument strip shows only instruments present in that song's lineup.
- **Two musicians with the same name** are not disambiguated. Names are display strings, and with
  40 people who know one another, disambiguation is a human matter.
- **An enrichment field that never resolves** is permanent-absent, not an error. No screen blocks on
  artwork, preview, or MBID.
- **A song removed from the catalog while scheduled** in an upcoming jam: the JamSong holds the
  title and artist it needs to render, so the setlist stays readable. Resolved in `sheet-schema.md`:
  each jam tab keeps plain-text copies of title and artist, used only when `id_tema` is no longer in
  `Catalogo`.
- **A song removed from the setlist** (`admin-remove-song-from-setlist`): its JamSong, Slots and
  extra participants go with the row (no orphan assignment), later songs move up one position, and
  the catalog Song is untouched. Allowed on a draft or a published upcoming jam, never on a past one
  (D-04). There is no undo: adding the song again starts from the default lineup and a new key.
- **No upcoming jam scheduled.** The Next jam tab shows an empty state with a concrete invitation,
  distinct from the DRAFT "list is being assembled" message.
- **Offline.** The app shows the last cached data with a staleness indicator rather than an error.
  Mutations while offline are an open question, recorded in `risks-and-open-questions.md`.

### Publishing a setlist

`SetlistRepository.publishSetlist(date)` is the public D-13 mutation that moves the upcoming jam from `DRAFT` or `CREATED` to `PUBLISHED`. Apps Script validates that the jam is upcoming and its available tab contains at least one song, then writes and reads back only the Jams status cell. `Published` is returned only after server confirmation; no optimistic status exists. The confirmed Room mirror updates only a cached `DRAFT` or `CREATED` jam whose setlist is `AVAILABLE`, so an offline or withheld cache remains safe until refresh. Publishing is idempotent. `SetlistRepository.setJamStatus(date, status)` (`admin-set-jam-status`, D-26) moves the upcoming jam to any of the three states; `publishSetlist` stays as a server alias of it for installed admin builds.

### Creating the next jam (`assistant-create-jam`, D-25)

The admin asks the Asistente for a new jam and gives the day; the agent fills the time and venue of the last jam
and the admin confirms in a dialog. The app appends the `Jams` row once, as `BORRADOR`, through `createJam`; later
edits happen in the Sheet. A date earlier than the upcoming jam is blocked in the app (it would put the existing
jam on hold; it can be added in the Sheet). A later date is allowed and the new jam is "en espera": it is not the
upcoming jam until the earlier one passes. The list proposal can be loaded only into the upcoming jam.

### Complete setlist mutation inventory (D-13)

`SetlistRepository` is the mutation inventory: exactly these fourteen repository-callable mutations (the `SetlistActionRegistry` that once listed them was removed by `remove-action-registry`, 17 October 2026):
add song, remove song, set key, adjust lineup slot count, assign musician, clear slot, add extra
participant under `Otros`, remove extra participant under `Otros`, reorder song, publish setlist, create the next jam (a `BORRADOR` `Jams` row, D-25), set the jam status (D-26), update a future jam and delete a future jam (D-28).
Each returns its own typed outcome. `SetlistRepository` is the union of twelve narrow contracts, one
per mutation (adding and removing an extra participant share one, as do updating and deleting a
future jam), so a presenter receives only the
contracts it uses. The assistant reaches four of them (add, remove, move, create
jam) through narrow `:core:data` contracts that `:app` binds to the same repository. Read-only refresh/observe operations, failure dismissal and admin login are not setlist
mutations. Apps Script remains the write authority; the repository is not a credential or protocol
boundary. A deeplink scheme remains deferred under D1.
