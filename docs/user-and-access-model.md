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
| Add or remove an extra participant under `Otros` | no | yes |
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
  rather than being delivered and hidden in the UI. The Apps Script `jams` route enforces this
  server-side: it serves a setlist only for an `estado` of exactly `PUBLICADA` and fails closed on
  any other value, a typo included (`apps-script-api.md`). The app adds two more layers:
  `JamsMapper` turns every DRAFT into `Setlist.Withheld` and ignores any setlist sent with one, and
  the musician screens decide from the jam's status through `Jam.setlistForMusicians()`
  (`unpublished-setlist-state`), so a draft whose songs reached the app (the admin read, below) still
  shows no song on Próxima jam or in the song detail.
- **Admin state is local.** A flag in DataStore after the passphrase validates against the Sheet
  through Apps Script. As built (`admin-passphrase-login`, 5 October 2026): the login on Info POSTs
  the trimmed passphrase to the `checkPassphrase` action (`docs/apps-script-api.md`), and only an
  `ok` answer stores it. What is stored is **the verified passphrase itself** (the write slices
  must send it), in DataStore Preferences, file `admin_session` (key `admin_passphrase`), plaintext
  in app-private storage, **excluded from cloud backup and device transfer** (`:app`'s
  `backup_rules.xml` and `data_extraction_rules.xml`). Admin mode is "a non-blank passphrase is
  stored", so the flag and the credential cannot disagree. A wrong passphrase, no connection or an
  unavailable check stores nothing. The passphrase is never logged, never in a URL, and never in
  the saved-state Bundle (the login field is plain `remember`; a rotation clears it).
- **Write endpoints must validate the passphrase server-side** in Apps Script. A client-side flag
  controls which controls are visible; it must not be what authorizes a write. Anyone can call the
  Apps Script URL directly, so the check belongs there. As built (`apps-script-write-auth`,
  5 October 2026): every POST action passes one guard in the router of `src/Post.js`, before it
  runs, and no action calls it itself. The guard reads `Config` on every request and caches
  nothing. Failed guesses are limited globally to 10 per fixed 10-minute window (W2); after that
  every action, login included, answers `rate_limited` until the window ends, even with the right
  passphrase. Writes run under the script lock. On the device, every mutation goes through
  `AdminWriter` in `:core:data`, which sends the stored passphrase in the POST body.
- **A refused write keeps admin mode** (user decision W3). When the server answers
  `invalid_passphrase` or `passphrase_not_set` to a write, the outcome is `AccessRefused`; the
  device keeps the stored passphrase and its admin controls. Nothing logs the device out
  automatically.
- **Debug-only admin flag** (`debug-admin-session`): a development build with
  `bluesjam.debugAdmin=true` in the git-ignored `local.properties` starts in admin mode without a
  login, so device checks of admin controls need no passphrase typed. It only draws controls and
  authorizes nothing: it never stores, sends or knows the passphrase, so a write from such a session
  sends no request and returns `AccessRefused` (`AdminWriter`, `apps-script-write-auth`). Release
  builds never contain it.

## Revocation / Expiry

- **No session expiry in the MVP.** The admin flag persists until the app's data is cleared or the
  admin logs out. With one or two trusted people and no personal data at stake, timed expiry adds
  friction without reducing real risk.
- **Revocation is by rotating the passphrase** in the Sheet. This invalidates every device at once,
  which is the correct granularity when the credential is shared anyway. A device logged in before
  the rotation keeps its admin controls, before and after its first rejected write (W3): every write
  fails with `AccessRefused` until the admin taps "Salir del modo admin" on Info and logs in again
  with the new passphrase ("Entrar como admin"). The server-side check is what actually stops it.
- A visible logout action is available from the info screen so a borrowed or shared phone can be
  cleared deliberately. As built: "Salir del modo admin" under "Modo admin activo", no
  confirmation (A4); it deletes the stored passphrase and makes no request.
- **No re-check on start** (`admin-passphrase-login`): a device keeps admin mode across restarts
  without asking the server again. After a rotation every write is rejected (`AccessRefused`),
  and the device stays in admin mode until a manual logout (W3).

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
  write fails with `AccessRefused`, and it keeps doing so after the first failure (W3). The failure
  should read as "your access changed" and point to logging out and in again from Info, not as a
  generic network error; that copy comes with the first mutation slice.

## Admin read and add song (`admin-add-song-to-setlist`)

- On a device that stores a passphrase, the jams refresh is the guarded POST `readJams`, so the
  Room cache holds the current and future drafts' songs. They are drawn only while the admin flag
  is on; musician screens decide from `Jam.setlistForMusicians()`. After logout they stay cached,
  never drawn, until the next refresh (the anonymous GET) replaces them.
- A refused admin read (rotated passphrase) falls back to the anonymous GET for the rest of the
  process; the refused value is remembered in memory only.
- "Agregar tema" is drawn from the admin flag; the add itself is authorized by Apps Script. With the
  debug admin flag and no stored passphrase, an add answers `AccessRefused` without a request.

## Remove song (`admin-remove-song-from-setlist`)

- "Quitar de la lista" is drawn from the admin flag, in an expanded row; the removal is authorized by
  Apps Script (router guard), never by the flag. A refused removal keeps admin mode (W3). With the
  debug admin flag and no stored passphrase, a removal answers `AccessRefused` without a request.
- Removing from a published list is allowed; the confirmation says musicians stop seeing the song.
  It reaches musician devices on their next refresh (cache-first, stale after 30 minutes), not
  instantly. Musicians never see the admin's row part (`SongRowUiModel.admin` is null for them).

## Publish the setlist

The admin flag only draws `Publicar lista`; Apps Script's passphrase guard authorizes `publishSetlist`. The admin must confirm the irreversible transition. While sending, the app keeps the cached `BORRADOR` badge; it shows `PUBLICADA` only after the server writes and reads back that status. A timeout can follow a completed server write, so retry is safe and the server answers `alreadyPublished`. Empty and tab-less drafts are refused server-side and have no publish action in the app. Musicians see the published setlist on their next refresh. Deployment and live checks remain deferred to the shared app-complete deployment batch.
