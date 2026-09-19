# Domain Model

Terms used here are defined in `../CONTEXT.md`. This document describes entities, relationships,
states, and the rules that govern transitions.

## Core Concepts

```
Jam(id, date, venue, status: DRAFT | PUBLISHED, songs: List<JamSong>)
JamSong(position, songId, key, lineup: List<Slot>)
Slot(instrument, musicianName?)          // open when musicianName is null
Song(id, title, artist, defaultKey, tempo, tags, difficulty,
     mbid?, artistArea?, deezerTrackId?, previewUrl?, artworkUrl?, songsterrId?)
```

### Jam

One monthly session: a date, a venue, a status, and an ordered setlist. At most one Jam is upcoming
at a time. Jams whose date has passed are historical and read-only.

### JamSong

One Song scheduled in one Jam. Carries the `position` in the setlist, the `key` chosen for that
night, and the `lineup` of slots. The key lives here rather than on Song because a jam's key is
whatever suits the person singing that night (D-08).

### Slot

One instrument position within a JamSong. Open when `musicianName` is null, filled otherwise. There
is no Musician entity: a musician is a name string on a slot. This is deliberate — musicians have no
accounts (D-05), so modeling them as entities would imply identity the product does not have.

### Song

A catalog entry, reused across Jams. The first group of fields is admin-maintained in the Sheet. The
last five (`mbid`, `artistArea`, `deezerTrackId`, `previewUrl`, `artworkUrl`) plus `songsterrId` are
optional enrichment, fetched in the background and cached; every one of them may be absent and the
app must render correctly without them.

`artistArea` earns its place: it lets "blues nacional argentino" be filtered by data rather than by
the model's memory, which is exactly where an LLM is weakest on this repertoire (D-14).

## Relationships

- A **Jam** has exactly one ordered setlist of **JamSongs**. Order is explicit via `position`.
- A **JamSong** references exactly one **Song** and owns exactly one **Lineup**.
- A **Lineup** is a list of **Slots**. Default: 2 guitars, 1 bass, 1 drums, 1 vocals, 1 harmonica,
  1 keyboards — adjustable per JamSong.
- A **Song** may appear in many **Jams**, with a different key and a different lineup each time.
- Deleting a JamSong from a setlist does not affect the Song in the catalog.

Without a declared lineup, "open slot" has no definition and the app's primary filter cannot exist
(D-06). The lineup is therefore not optional metadata; it is what makes the main screen work.

## States and Lifecycles

### Jam status

```
DRAFT ──publish──> PUBLISHED ──(date passes)──> archived (implicit)
```

- **DRAFT.** The admin is building the setlist. Musicians see the date and venue, and a message
  saying the list is being assembled — not an empty list, which would be misread as "no songs".
- **PUBLISHED.** The setlist is visible to musicians. The admin may still edit; edits to a published
  jam are visible immediately. There is no republish step and no unpublish in the MVP.
- **Archived** is not a stored status. A jam is historical when its date has passed; past jams are
  read from the Sheet, which is their authority.

The transition DRAFT → PUBLISHED is admin-initiated and writes through to the Sheet.

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
| Song catalog | Sheet | Sheet → app, read-only |
| Past jams | Sheet | Sheet → app, read-only |
| Upcoming setlist | App | app → Sheet via Apps Script |
| Published status | App | app → Sheet |

## Important Scenarios

### Building a setlist

The admin opens the upcoming jam in DRAFT, adds songs from the catalog, sets a key on each, adjusts
lineups where the default does not fit, assigns musicians to slots, reorders, and publishes. Every
one of these operations exists as a repository function or deeplink from phase 1, because the phase
2 assistant must be able to perform each of them (D-13).

### Finding a place to play

A musician opens the app, taps their instrument's filter chip, and sees only the songs with an open
slot for it, with a count of how many songs matched out of how many. Tapping a row expands it in
place, open slots listed first.

### Changing a key at the last minute

The admin edits the key on a JamSong of a published jam. The change is immediately visible to
everyone. The catalog Song's `defaultKey` is untouched — that night's key is a property of the
JamSong, not the Song.

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
  title and artist it needs to render, so the setlist stays readable. Behavior here is recorded as
  an implementation-time question in `risks-and-open-questions.md`.
- **No upcoming jam scheduled.** The Next jam tab shows an empty state with a concrete invitation,
  distinct from the DRAFT "list is being assembled" message.
- **Offline.** The app shows the last cached data with a staleness indicator rather than an error.
  Mutations while offline are an open question, recorded in `risks-and-open-questions.md`.
