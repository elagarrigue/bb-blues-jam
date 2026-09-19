# Build Brief

BB Blues Jam — an Android app to organize a monthly blues jam at La Macanuda (Moreno 223, Bahía
Blanca, Argentina). Between 20 and 60 musicians attend each date.

This project is the final deliverable of the DevExpert AI Expert course (September 2026 edition),
which requires two things: a working app and a documented development harness.

## Problem

The setlist is assembled by hand and shared over WhatsApp or on paper. Four concrete failures
follow from that:

1. **Musicians cannot prepare.** They do not know which songs will be played, or in which key, until
   they arrive.
2. **Open positions are invisible.** A bass player has no way to see which songs still need bass
   without asking the organizer directly.
3. **No single trustworthy version.** The list changes until the last minute and copies diverge.
4. **No history.** Past jams leave no record, so setlists repeat unintentionally.

A fifth problem belongs to the organizer rather than the musicians: building a setlist takes time
and draws on repertoire knowledge that exists only in one person's head.

## Current Workaround / Existing System

A handwritten or WhatsApp-distributed list, rebuilt from scratch each month. Prior assets that carry
into this project: an interactive mockup establishing the visual direction, a Google Sheets backend
reachable through Apps Script, and prior research into the Songsterr API.

## Target Users

### Musician — the majority, read-only

Opens the app to answer two questions: *what are we playing?* and *where is there room for me?*
Roughly half the time they open it standing in the bar, in low light, with one hand occupied. They
never write anything: they see an open slot and ask the admin for it in person.

### Admin — one or two people, the only writers

Builds the setlist before the jam, assigns keys, fills slots with musician names, reorders songs,
and decides when to publish. Sees exactly the same screens as the musician plus editing controls —
one app, not two.

## Goals

- Answer "where can I play?" without the musician opening or expanding anything.
- Keep the key visible at all times; it is the datum musicians seek most after open slots.
- Give the setlist a single authoritative version with an explicit published state.
- Preserve past jams as a browsable archive, so repetition becomes a choice rather than an accident.
- Let the admin build and publish a setlist entirely from the phone.
- Establish, in phase 1, the action contract that the phase 2 assistant will call (D-13).

## Non-Goals

Explicitly out of scope. These are product definition, not deferral of undecided matters.

- **Musician self-signup.** Only the admin assigns musicians (D-05). No identity, no concurrency
  control on the last slot, no open backend writes.
- **The assistant.** Phase 2. No AI chat screen ships in the MVP.
- **Tablature rendered in-app.** Songs store a `songsterrId` and the detail screen opens the browser
  (D-10).
- **User accounts, profiles, musician-to-musician chat, notifications, onboarding.**
- **Light theme.** Dark only; the app is used in a dark bar.
- **iOS and Kotlin Multiplatform** (D-01).
- **Bidirectional sync.** Each entity has one authority and one direction (D-04).
- **External APIs as a dependency.** Enrichment is optional and the MVP is fully functional without
  any network call to a music API (D-08, D-09).

## MVP Slice

Three tabs — Next jam, Past jams, Info — plus admin login and the admin editing controls layered
onto the same screens.

The MVP is complete when a full round trip works end to end:

1. The admin logs in with a passphrase.
2. The admin builds a setlist for the upcoming jam: adds songs from the catalog, sets the key per
   song, adjusts the lineup where needed, assigns musicians to slots, and reorders songs.
3. The admin publishes the setlist, which writes through to the Sheet.
4. A musician opens the app, sees the published setlist with keys and the instrument strip, filters
   by their instrument, and finds the songs with an open slot for it.
5. After the jam date, the jam appears in the past-jams archive, read from the Sheet.

That slice exercises both roles, the full lifecycle of a jam, the slot model, the publish
transition, and both directions of the Sheet authority split — which is the riskiest part of the
design.

The non-goals above are the boundary of this slice. Access rules are detailed in
`user-and-access-model.md`, stack and integration constraints in `technical-discovery.md`, and
visual direction in `../DESIGN.md`.

## Validation Plan

- **The manual flow, timed.** Record building a 12-song setlist by hand, end to end. This is the
  baseline the app and later the assistant are measured against.
- **Real use at a real jam.** The MVP is validated at an actual monthly session, not in a
  simulation. Success is the admin choosing the app over paper for the next date without being
  asked to.
- **The musician question test.** A musician who has never seen the app should be able to answer
  "which songs still need bass?" in under ten seconds, in the bar, without instruction.
- **Architecture verification.** Konsist proves feature modules do not depend on one another; the
  proof matters because it is what later demonstrates the assistant was added without touching them.

## Success Criteria

1. An admin builds and publishes a complete setlist from the phone, without touching the Sheet
   directly.
2. A musician finds every song with an open slot for their instrument using one filter chip.
3. The published setlist is identical for everyone who opens the app — one authoritative version.
4. Past jams are browsable, so setlist repetition is visible before it happens.
5. Every mutation in the app exists as a repository function or deeplink, so the phase 2 assistant
   requires no changes to feature modules (D-13).
6. The app is fully usable with no external music API reachable.

## Notes

Course context shapes two things worth stating plainly. First, the harness itself is a deliverable,
so discovery documents, specs, and agent configuration are evidence, not overhead. Second, the
schedule runs 14 September to 23 October 2026, with the assistant landing in week 5 — so phase 1
must close early enough to leave that week clear.

The project bitácora (`bb-blues-jam-bitacora.md`) is in Spanish and is presentation material; it
records the reasoning behind all 14 decisions and should be kept current as work proceeds.
