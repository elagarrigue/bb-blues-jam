# Context

Shared vocabulary for the BB Blues Jam project. This is a glossary only: no decisions, plans, or
implementation details. Technical documents and code are in English (D-12); user-facing interface
copy is in Rioplatense Spanish and is not governed by this file.

## Glossary

### Jam
A single monthly blues session at a venue, on a specific date. The unit everything else hangs off.
A Jam is either upcoming (at most one at a time) or past. Not to be confused with the event series
as a whole, which is called the BB Blues Jam.

### Setlist
The ordered list of songs to be played at one Jam. A Jam has exactly one Setlist. The Setlist is
the artifact the admin builds and the musician reads.

### JamSong
One song as it appears in one Jam's Setlist: its position, the key chosen for that night, and its
Lineup. The same Song can appear in many Jams as different JamSongs, with different keys and
different musicians.

### Song
An entry in the Catalog: title, artist, default key, tempo, tags, difficulty. A Song exists
independently of any Jam and is reused across Jams.

### Catalog
The full repertoire of Songs available to be programmed, maintained by the admin in the Sheet. The
Catalog is the only pool the assistant may select from.

### Lineup
The set of Slots defined for one JamSong — which instruments are needed and how many of each. The
default Lineup is 2 guitars, bass, drums, vocals, harmonica, keyboards, adjustable per song.

### Slot
One position for one instrument within a JamSong's Lineup. A Slot is either open or filled.

### Open Slot
A Slot with no musician assigned. This is the single most important datum in the app: the musician's
primary question is "where can I play?", and the answer is the set of open Slots.

### Filled Slot
A Slot with a musician's name assigned to it.

### Musician
A person who plays at a Jam. Represented only by a name on a Slot. Musicians have no accounts, no
profiles, and no login; they are data, not users of an authenticated system.

### Admin
The one or two people who organize the Jam. The only role that can mutate anything. Identified by a
passphrase, not by an account.

### Key
The musical key a song is played in on a given night. Set by the admin per JamSong, never derived
from an external API. The most-sought datum after open Slots.

### Published
The state in which a Jam's Setlist is visible to musicians. Before publishing, the Setlist is a
Draft and musicians see the date and venue but not the songs.

### Draft
The state in which the admin is still building the Setlist. Only the admin can see the songs.

### Venue
Where a Jam happens. For the MVP this is La Macanuda, Moreno 223, Bahía Blanca, but it is stored per
Jam rather than hardcoded.

### Sheet
The Google Sheets workbook that serves as the project's backend, reached through Apps Script. It is
the authority for the Catalog and past Jams; the app is the authority for the upcoming Setlist.

### Enrichment
Optional metadata fetched from external music APIs (MusicBrainz, Deezer, Last.fm) and cached
locally. Never required for a Song to be usable; the MVP works with the Sheet alone.

### Assistant
The phase 2 admin-only chat that composes Setlists from the Catalog under thematic constraints. Out
of scope for the MVP, but its action contract is honored from phase 1.

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
Too vague alone. Say `Jam status` (DRAFT or PUBLISHED) or `Slot state` (open or filled).
