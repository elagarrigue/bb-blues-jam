# User and Access Model

Split out from the build brief because access is a product risk here: the backend is a Google Sheet
reached through Apps Script, and the decision to let only the admin write is what keeps that backend
viable (D-05, D-11).

## User Types

### Musician

Read-only, unauthenticated, anonymous. Never identified by the app. Sees published setlists, past
jams, and info. A musician's name may appear on a slot, but that is data the admin entered, not an
account. The app has no concept of "the current musician".

### Admin

One or two people. The only role that can mutate anything. Authenticated by a shared passphrase, not
by an individual account, so the app cannot distinguish one admin from another — and does not need
to.

## Roles

There are exactly two, and they are not hierarchical in the usual sense: admin is musician plus
editing controls, on the same screens. There is no third role and no per-feature permission
granularity.

## Permissions

| Action | Musician | Admin |
|---|---|---|
| View published setlist | yes | yes |
| View draft setlist | no | yes |
| View past jams and info | yes | yes |
| Add or remove a song from a setlist | no | yes |
| Set the key on a song | no | yes |
| Adjust a song's lineup | no | yes |
| Assign or clear a musician on a slot | no | yes |
| Reorder songs | no | yes |
| Publish a setlist | no | yes |

The musician column is entirely "no" for mutation. That is the whole access model: read for
everyone, write for the passphrase holder.

## Ownership Boundaries

Ownership is by entity, not by user (D-04). The Sheet owns the catalog and past jams; the app owns
the upcoming setlist and its published status. No user owns a resource, so there is nothing to
transfer and no per-record access rules.

## Access Rules

- **Unauthenticated access is the default and the majority case.** The app opens straight to the
  next jam with no login wall. Authentication exists only to reveal editing controls.
- **Draft setlists are hidden from musicians**, who see the date, the venue, and a message that the
  list is being assembled. The data should not be delivered to an unauthenticated client at all,
  rather than being delivered and hidden in the UI.
- **Admin state is local.** A flag in DataStore after the passphrase validates against the Sheet
  through Apps Script.
- **Write endpoints must validate the passphrase server-side** in Apps Script. A client-side flag
  controls which controls are visible; it must not be what authorizes a write. Anyone can call the
  Apps Script URL directly, so the check belongs there.

## Revocation / Expiry

- **No session expiry in the MVP.** The admin flag persists until the app's data is cleared or the
  admin logs out. With one or two trusted people and no personal data at stake, timed expiry adds
  friction without reducing real risk.
- **Revocation is by rotating the passphrase** in the Sheet. This invalidates every device at once,
  which is the correct granularity when the credential is shared anyway. Devices holding a stale
  local flag will still show admin controls until their next write fails — the server-side check is
  what actually stops them.
- A visible logout action is available from the info screen so a borrowed or shared phone can be
  cleared deliberately.

## Edge Cases

- **A musician learns the passphrase.** Accepted risk. The consequence is setlist vandalism within a
  20-60 person community that knows one another, recoverable by rotating the passphrase and fixing
  the list. The alternative — real accounts — was rejected as disproportionate (D-11).
- **Two admins edit at once.** Last write wins. With two people who are usually in the same room,
  conflict resolution machinery is not warranted. Worth stating explicitly so no one builds locking
  on the assumption it was forgotten.
- **Admin controls on a past jam.** Past jams are read-only for everyone; the Sheet is their
  authority. Editing history is not supported.
- **Passphrase validation while offline.** Login requires the network. An admin who is already
  logged in keeps the local flag and stays in admin mode; whether their writes queue offline is an
  open question recorded in `risks-and-open-questions.md`.
- **A stale local admin flag after rotation.** The device still renders admin controls but every
  write fails. The failure should read as "your access changed", not as a generic network error.
