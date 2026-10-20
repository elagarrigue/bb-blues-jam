# Context

Shared vocabulary for the BB Blues Jam project. This is a glossary only: no decisions, plans, or
implementation details. Technical documents and code are in English (D-12); user-facing interface
copy is in Rioplatense Spanish and is not governed by this file.

## Glossary

### Jam
A single monthly blues session at a venue, on a specific date. The unit everything else hangs off.
A Jam is upcoming (at most one at a time), held back ("en espera", a later future jam) or past. Not to be confused with the event series
as a whole, which is called the BB Blues Jam.

### Setlist
The ordered list of songs to be played at one Jam. A Jam has exactly one Setlist. The Setlist is
the artifact the admin builds and the musician reads. A Setlist is **withheld** while its Jam is a
draft: a musician sees that it is being assembled, not an empty list. A Setlist is **unavailable**
when its Jam is published but its tab cannot be read, or none of its rows is valid.

### JamSong
One song as it appears in one Jam's Setlist: its position, the key chosen for that night, and its
Lineup. The same Song can appear in many Jams as different JamSongs, with different keys and
different musicians.

### Song
An entry in the Catalog: title, artist, default key, and optional tempo, tags and difficulty, which
the MVP does not use (D-20). A Song exists
independently of any Jam and is reused across Jams.

### Catalog
The full repertoire of Songs available to be programmed, maintained by the admin in the Sheet and, for title,
artist and default key, from the app's **Temas** tab (D-30). The Catalog is the only pool the assistant may select from.

### Lineup
The set of Slots defined for one JamSong — which instruments are needed and how many of each. The
default Lineup is 2 guitars, bass, drums, vocals, harmonica, keyboards. Adjusting it per song only
removes or adds Slots; a Lineup never has more than three of an instrument (D-18, amended 12 October 2026).

### Slot
One position for one instrument within a JamSong's Lineup. A Slot is either open or filled.

### Open Slot
A Slot with no musician assigned. This is the single most important datum in the app: the musician's
primary question is "where can I play?", and the answer is the set of open Slots.

### Filled Slot
A Slot with a musician's name assigned to it.

### Extra Participant
Someone who plays a JamSong outside its Lineup, recorded as a name and a free-text instrument (a
sax, percussion, a third guitar). Shown in the UI under "Otros". Not a Slot: never open, never part
of "where can I play?", and added only by the Admin (D-05, D-18).

### Musician
A person who plays at a Jam. Represented only by a name on a Slot or an Extra Participant. Musicians have no accounts, no
profiles, and no login; they are data, not users of an authenticated system.

### Admin
The one or two people who organize the Jam. The only role that can mutate anything. Identified by a
passphrase, not by an account.

### Key
The musical key a song is played in on a given night. Set by the admin per JamSong, never derived
from an external API. The most-sought datum after open Slots.

### Published
The state in which a Jam's Setlist is visible to musicians (`PUBLICADA` in the Sheet). Before it, the
Jam is a Draft or Created. The admin can move the upcoming Jam between the three states from the app, in either
direction (`admin-set-jam-status`).

### Draft
The state (`BORRADOR`, D-26) in which the Jam does not exist for musicians: no date, no list. Only the
admin sees it. The app creates every new Jam as a Draft (D-25).

### Created
The state (`CREADA`, D-26) in which musicians see the date, time and venue of the Jam and that the
Setlist is being assembled, but not the songs. The admin sees and edits the songs.

### Venue
Where a Jam happens. Currently La Macanuda, Moreno 223, Bahía Blanca, but it can change, so it is
stored per Jam and never hardcoded — not even on the Info tab (D-19).

### Bahía Blanca Blues
The group that organizes the Jam and the community's other activities (festivals, concerts, the
Hideaway radio program). What the Info tab presents; facts in `docs/info-content.md`.

### Sheet
The Google Sheets workbook that serves as the project's backend, reached through Apps Script. It is
the authority for past Jams and the Catalog's optional fields; the catalog's title, artist and default key are
written by the app too (D-30), and the app is the authority for the upcoming Setlist.

### Live Window
The time around a Jam when Próxima jam refreshes itself often: from 30 minutes before its start to
4 hours after, Buenos Aires time, even after the Jam turned past at midnight. Outside it the app only
refreshes when opened, on Retry or on a pull.

### Enrichment
Optional metadata fetched from external music APIs (MusicBrainz, Deezer, Last.fm) and cached
locally. Never required for a Song to be usable; the MVP works with the Sheet alone.

### Assistant
The phase 2 admin-only chat that composes Setlists from the Catalog under thematic constraints. Out
of scope for the MVP, but its action contract is honored from phase 1. It is an admin-only tab
(`assistant-admin-tab`, 13 October 2026) whose agent answers a request with a **Proposal**, a
validated, catalog-only ordered list of songs (each with the catalog's title, artist and key plus a
short reason), a closing message, follow-up questions and the ids it had to reject. A Proposal changes
nothing; loading it into the setlist is a separate, confirmed step through the action contract.

### Jam en espera
A future jam that exists in the Sheet while an earlier jam is still the upcoming one. It is not shown as the
next jam until the earlier one passes. The app creates a jam only as a draft (`BORRADOR`, D-25, hidden from musicians until it is created or published, D-26) and says
when the new jam will be "en espera".

### Action Contract
The rule that every mutation is reachable as a repository function or deeplink, so the Assistant can
perform anything the admin can do by hand without modifying feature modules.

## Rejected / Ambiguous Terms

### User
Too broad here. Use `Musician` (reads only) or `Admin` (mutates). The distinction is a product rule,
not a UI detail.

### Playlist
Use `Setlist`. "Playlist" implies recorded-music streaming, which this project is not.

### Song (when a specific night is meant)
Use `JamSong`. A Song is catalog-level and has a default key; a JamSong is a Song scheduled in one
Jam with the key chosen for that night. Conflating them loses the fact that keys change per night.

### Slot (when the person is meant)
A `Slot` is the position, not the person. The person is a `Musician` whose name fills the Slot.

### Signup / Registration
Not applicable. Musicians do not enroll themselves; only the Admin assigns them (D-05). Avoid the
term entirely so no flow implies self-service.

### Sync
Avoid. It suggests bidirectional reconciliation, which the project deliberately does not do. Each
entity has a single authority and one direction of travel (D-04).

### Status
Too vague alone. Say `Jam status` (DRAFT, CREATED or PUBLISHED) or `Slot state` (open or filled).
