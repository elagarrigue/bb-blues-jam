# Feature Implementation Spec: Validate the passphrase on every write endpoint

## Source Feature

- `id`: apps-script-write-auth
- `area`: backend
- `depends_on`: admin-passphrase-login (`accepted`)
- `status`: `not_started` at planning time (5 October 2026)
- `source`: `feature_list.json`

## Goal

Make the Apps Script POST router the only gate for writes. Every POST action, the write actions the
later slices add included, passes one passphrase guard **in the router**, before the action runs, so
a later slice cannot forget it. The guard reads `Config` on every request and caches nothing, so
rotating the passphrase rejects every device at once. Writes also share one script lock, and failed
guesses are rate limited (W2). This slice ships one write: `checkWriteAccess`, a self-cleaning deploy
check that proves the deployment can create, write, read back and delete a tab.

On the client, `:core:data` gets the shared write path every mutation repository will use. It takes
the stored passphrase and the action, POSTs them and maps the answer to a public `WriteOutcome`. A
rejected passphrase comes back as `AccessChanged` ("your access changed"), not as a network error
(W3).

## Non-Goals

- The admin mutations (add, remove, set key, adjust lineup, assign, clear, reorder, publish): later
  slices. Each one adds an entry to `ACTIONS` and a repository function that calls `AdminWriter`.
- The admin's read of a draft setlist (POST with the passphrase). Recommended home:
  `admin-add-song-to-setlist`, the first slice that must show a draft's songs to the admin. This
  slice changes nothing on the read side: `Code.js`, `Jams.js`, `Catalog.js` and `Normalize.js` stay
  as they are.
- Any UI or copy. "Your access changed" copy and the write error states belong to the first
  mutation slice. The login copy does not change, because `rate_limited` already maps to
  `Unavailable` ("Probá de nuevo en un rato.").
- The login field's amber `TextSelectionColors` (admin-passphrase-login validation note).
  Recommended home: `admin-assign-musician`, the next admin text field, so the fix lands once in a
  shared place.
- Seeding the passphrase into debug builds. `app/build.gradle.kts` must **not** read
  `bluesjam.debugAdminPassphrase` in this slice. The first slice that writes from the device
  decides on that.
- Offline write queueing, deeplinks (D-13: deferred), the action registry (`action-contract-registry`).

## Job Story

When anyone, admin or not, calls the public `/exec` URL to change the Sheet, I want Apps Script to
refuse it unless the request carries the current passphrase. Then the local admin flag stays
cosmetic, and rotating the passphrase revokes every device.

## Users And Permissions

- Anonymous caller (musician, script, attacker): can make reads and can call POST actions. Every
  POST action answers `invalid_passphrase` unless the request carries the current passphrase. After
  W2's limit of failed guesses, every action answers `rate_limited` until the window ends.
- Admin device: the shared write path sends the stored passphrase. If the passphrase was rotated,
  the first write is rejected and the device forgets it (W3).
- Debug admin session (`bluesjam.debugAdmin`): no passphrase is stored, so a write returns
  `AccessChanged` and sends no request. It authorizes nothing.

## Acceptance Scenarios

1. **No passphrase.** Given any action in `ACTIONS`, when the body has no `passphrase` or a
   non-string one, then the answer is `invalid_passphrase`, the action function never runs, the lock
   is never taken, and the spreadsheet is touched only to read `Config`.
2. **Stale passphrase after rotation.** Given `Config` held `old` and now holds `new`, when a write
   sends `old`, then the answer is `invalid_passphrase`. When it sends `new`, then it runs. `Config`
   is read on every request.
3. **Valid write.** Given the current passphrase, when `checkWriteAccess` runs, then under the
   script lock it creates the tab `_prueba_escritura` (deleting a leftover first), writes a marker to
   A1, reads it back, deletes the tab and answers `{"schemaVersion":1,"ok":true}`. If the read-back
   does not match, the answer is `internal_error`.
4. **Unset passphrase.** Given `Config` with no usable passphrase, every action answers
   `passphrase_not_set`.
5. **Rate limit (W2).** Given 10 failed guesses in the current 10-minute window, when any action
   arrives in that window, even with the right passphrase, then the answer is `rate_limited` and
   `Config` is not read. The next window accepts again. If the cache throws, the guard proceeds
   without counting (fails open; the passphrase check still runs).
6. **Busy.** Given another write holds the lock for more than 10 s, when a write arrives, then the
   answer is `busy` and the action does not run.
7. **Nothing leaks.** No response, error message or cache key or value contains the stored or the
   submitted passphrase.
8. **Client.** `AdminWriter.write(action, fields)` behaves as follows:
   - `ok` gives `Done`.
   - `invalid_passphrase` or `passphrase_not_set` gives `AccessChanged` and clears the stored
     passphrase.
   - `rate_limited`, `busy`, `unknown_action`, `NotConfigured`, an HTML page or any invalid answer
     gives `Unavailable`, and the store is kept.
   - `Offline` gives `Offline`.
   - Any other service code gives `Rejected(code)`.
   - With nothing stored it gives `AccessChanged` and sends no request.
9. **Unchanged.** The two GET routes give the same answers. `checkPassphrase` gives the same
   answers apart from `rate_limited`.

## Repository Research

### Files Inspected

`AGENTS.md`, `PROGRESS.md`, `feature_list.json` (this entry, `admin-passphrase-login`,
`debug-admin-session`), `CONTEXT.md` (head), `.claude/skills/architecture/SKILL.md`,
`docs/user-and-access-model.md`, `docs/apps-script-api.md`, `docs/sheet-schema.md` (Tabs, Config,
Identifiers, Mapper rules), `docs/domain-model.md` (grep), `docs/risks-and-open-questions.md`,
`bb-blues-jam-bitacora.md` (D-04, D-11, D-13, D-15), `docs/specs/admin-passphrase-login.md`
(Goal, Non-Goals, Technical Approach §1, Risks, User Approvals), `DESIGN.md` (screen 7),
`backend/apps-script/README.md`, `appsscript.json`, `src/Post.js`, `src/Code.js`,
`test/helpers/load.js`, `test/post.test.js` (test names), `test/router.test.js` (the Post.js
exclusion), `core/data/.../admin/*.kt`, `remote/AppsScriptTransport.kt`, `remote/Envelope.kt`,
`remote/CheckPassphraseRequest.kt`, `DataFailure.kt`, `di/DataModule.kt`, and
`feature/info/.../AdminLoginCopy.kt`, `AdminLoginDefaults.kt`, `AdminLoginScreen.kt` (the field).

### Findings

- `Post.js` already has `readPassphrase_` (fails closed, trimmed, display values) and
  `passphraseMatches_`. They are called only by `checkPassphrase_` today. `handlePost(request,
  spreadsheet)` returns plain objects and never throws. Node loads `src` files in one scope
  (`load.js`).
- `AdminCredentialStore.passphrase()` exists "for the write slices". `AppsScriptPostTransport` and
  `AppsScriptEnvelope.decodeOk` are reusable as they are.
- The manifest has the single scope `spreadsheets.currentonly`. **Assumption, not verified:**
  `CacheService` and `LockService` need no extra OAuth scope. If one did, saving the new version
  would ask for authorization, or live check L4 would answer `internal_error`. Report it if either
  happens.
- No route lists tabs (`getSheetByName` only), so a transient `_prueba_escritura` tab is never
  served. Its name is not `YYYY-MM-DD`, so it can never be taken for a jam.

## Technical Approach

### 1. `backend/apps-script/src/Post.js` (the only `src` file that changes)

- `ACTIONS` entries become `{ write: boolean, run: fn }`:
  - `checkPassphrase: { write: false, run: checkPassphrase_ }`, which now just returns `{ok:true}`;
  - `checkWriteAccess: { write: true, run: checkWriteAccess_ }`.
- `handlePost(request, spreadsheet, services)` runs these steps in order:
  1. `invalid_request`.
  2. `unknown_action`.
  3. `requirePassphrase_(request, spreadsheet, services)`, the guard. Every action passes it; no
     action calls it itself.
  4. For a write action, `withWriteLock_(services.lock, fn)`: `tryLock(10000)`, otherwise `busy`;
     `releaseLock` in `finally`.
  5. `run(request, spreadsheet, services)`.
- `services` is `{ cache, lock, now }`. `doPost` builds it as `{ CacheService.getScriptCache(),
  LockService.getScriptLock(), Date.now() }`. Node tests pass fakes.
- `requirePassphrase_` works in this order:
  1. The rate limit (W2). Key `auth_failures_<floor(now / 600000)>`. If the count is 10 or more, the
     answer is `rate_limited`.
  2. `readPassphrase_` (`passphrase_not_set`).
  3. Compare. A mismatch (missing or non-string included) increments the count, with a TTL of
     1200 s, and the answer is `invalid_passphrase`.
  4. Any exception from `cache.get` or `cache.put` is caught and ignored (fail open).
  5. The passphrase is never cached, and the counter key and value never contain it.
- `checkWriteAccess_` works on the tab `WRITE_CHECK_TAB = '_prueba_escritura'`:
  1. Delete the tab if it exists, then `insertSheet(name)`.
  2. Write `'write check ' + new Date(now).toISOString()` to `getRange('A1')` with `setValue`.
  3. Read A1 back with `getDisplayValue()` and compare.
  4. `deleteSheet`.
  5. Answer `{ok:true}`. A mismatch throws an `Error`, which becomes `internal_error`, after trying
     to delete the tab. The step never touches any other tab.
- New error codes: `rate_limited` and `busy`. Messages are English and never contain a value.
- The header comment and `module.exports` are updated (export `requirePassphrase_` for the tests).

### 2. `:core:data`, package `com.bbbjam.core.data.admin`

- Public `sealed interface WriteOutcome { Done; AccessChanged; Offline; Unavailable; data class
  Rejected(val code: String) }`, each with KDoc. These are the outcomes later mutation repositories
  return to presenters.
- Internal `AdminWriter(transport: AppsScriptPostTransport, store: AdminCredentialStore)` with
  `suspend fun write(action: String, fields: Map<String, JsonElement> = emptyMap()): WriteOutcome`:
  1. If `store.passphrase()` is null, return `AccessChanged` and send no request.
  2. Build a `JsonObject` with `action`, `passphrase` and the fields. A field named `action` or
     `passphrase` is a `require` failure. Encode it with `AppsScriptEnvelope.json`, never by
     concatenation.
  3. POST it and decode with `decodeOk`.
  4. Map the answer as in scenario 8. On `AccessChanged` from the server, call `store.clear()` and
     ignore an `IOException`.
  5. Never log. No type holds the passphrase in a `toString`.
- `remote/`: constants for the codes the client maps (`invalid_passphrase`, `passphrase_not_set`,
  `rate_limited`, `busy`, `unknown_action`), shared with `DefaultAdminSession`.
- `di/dataModule`: `single { AdminWriter(get(), get()) }`.
- `checkWriteAccess` has no repository function. It is a deploy check that leaves nothing in the
  Sheet, not a product mutation (D-13 covers the admin's mutations).

### 3. Live checks (agent, no user step except W1)

Write a Python `urllib` script in the scratchpad, never in the repo. It reads `bluesjam.appsScriptUrl`
and, for L6, `bluesjam.debugAdminPassphrase` from `local.properties` with the `replace(chr(92),'')`
decode. It builds bodies with `json.dumps` and prints **only** the error `code` or `ok`. No
`set -x`, no echo, and the value is never on a command line.

## Expected File Changes

- `backend/apps-script/src/Post.js`: modify (§1).
- `backend/apps-script/test/post.test.js`: modify. Add a fake `services` helper. Update the "action
  table" test. Add table-driven guard tests over `Object.keys(ACTIONS)` with spy `run`s, plus
  rotation, rate-limit window and fail-open, lock busy and release, `checkWriteAccess` with a fake
  spreadsheet (`insertSheet`, `deleteSheet`, `getRange`), the leftover tab, read-back mismatch, and
  "no passphrase in cache keys or values".
- `backend/apps-script/README.md`: modify. Add a **Redeploy for write auth** section (paste `Post.gs`
  only, verify, **Manage deployments → Edit → New version**). Update the Verify-the-paste row for
  `Post.gs`, the `Known: checkPassphrase, checkWriteAccess` check and the live commands (only wrong
  values on the command line).
- `core/data/src/main/kotlin/com/bbbjam/core/data/admin/WriteOutcome.kt`, `AdminWriter.kt`: create.
- `core/data/.../admin/DefaultAdminSession.kt`, `remote/…` (a code-constant object), `di/DataModule.kt`:
  modify.
- `core/data/src/test/.../admin/AdminWriterTest.kt`: create. Cover each row of scenario 8, the exact
  body keys, no request when logged out, reserved keys and a passphrase with quotes or a backslash.
- `DefaultAdminSessionTest.kt` (`rate_limited` gives `Unavailable` and stores nothing) and
  `DataModuleTest.kt` (resolves `AdminWriter`): modify.
- Docs, see below. No Gradle, Konsist, manifest or `:app` change.

## Visual Design Impact

UI involved: no. `DESIGN.md` is not touched.

## Durable Documentation Impact

- `docs/apps-script-api.md`: update the POST actions section with the router guard and its order,
  the write request shape, `checkWriteAccess`, `rate_limited`, `busy`, the lock, and "no rate limit"
  replaced. Add the client `AdminWriter` mapping.
- `docs/user-and-access-model.md`: add the write guard as built and W3. Rewrite the debug-session
  sentence to say a write sends nothing and returns `AccessChanged`.
- `docs/risks-and-open-questions.md`: replace the rate-limit bullet with W2 and its lockout
  trade-off. Mark the CacheService/LockService scope assumption as checked or not. Record write
  latency from L6 (the "Write latency: still open" research task).
- `docs/sheet-schema.md` (Tabs): add `_prueba_escritura` as transient, created and deleted by
  `checkWriteAccess` in one request and never read.
- `.claude/skills/architecture/SKILL.md`: add `AdminWriter` and `WriteOutcome` to the admin
  paragraph, and add "a mutation repository calls `AdminWriter`; a new action is guarded by the
  router".
- `backend/apps-script/README.md`: as above. `AGENTS.md`, `ARCHITECTURE.md`, `CONSTRAINTS.md`: not
  needed (no workflow or new repo-wide rule; the architecture skill holds the layout).

## Implementation Plan

1. **Server.** Post.js and the Node tests. Run `node --test backend/apps-script/test/*.test.js` until
   green.
2. **Failure demonstrations.** Mutate, see the test fail, then restore and check with
   `sha1sum -c`:
   - (a) skip the guard for write actions in the router;
   - (b) cache the passphrase or omit the `Config` re-read;
   - (c) `AdminWriter` not clearing on `invalid_passphrase`.
3. **Client.** `WriteOutcome`, `AdminWriter`, DI and tests. Then `./gradlew ktlintFormat` and
   `CI=true ./init.sh`.
4. Update the docs and README.
5. **Hand off to the user.** Ask for the one batched manual step (below). The implementation must
   start only after the concurrent `debug-admin-session` validation has been persisted, because both
   touch `user-and-access-model.md` and the architecture skill.
6. **Live checks L1–L6** after the user confirms. Then record the evidence and set status `passing`.

### Manual steps for the user (one batch, about 3 minutes)

1. In Apps Script, replace the whole content of `Post.gs` with `src/Post.js`, save, and check that
   it contains the README's marker line.
2. **Deploy → Manage deployments → Edit → Version: New version → Deploy** on the current deployment.
   If Google asks to authorize, accept and say so.
3. (W1) Add the line `bluesjam.debugAdminPassphrase=<the passphrase>` to the git-ignored
   `local.properties` and say "listo". Never paste it into chat.

## Verification Plan

- `node --test backend/apps-script/test/*.test.js`: all pass (83 before, plus the new tests). This is
  outside `init.sh`, as before (U4).
- `CI=true ./init.sh`: exit 0, with `konsist: wired` (17/17, unchanged), `detekt: wired` and
  `ktlint: wired`. Result-file and test counts go up only by `AdminWriterTest` and the changed tests.
- Live checks (Python, only codes printed):
  - L1: `{}` gives `unknown_action … Known: checkPassphrase, checkWriteAccess`.
  - L2: `checkWriteAccess` with no passphrase gives `invalid_passphrase`.
  - L3: `checkWriteAccess` with `definitely-wrong` gives `invalid_passphrase`.
  - L4: `checkPassphrase` with `definitely-wrong` gives `invalid_passphrase`, not `internal_error`
    (CacheService works under the manifest).
  - L5: `?resource=config` gives `Known: catalog, jams`, and `?resource=jams` still passes
    `check-response.js --strict`.
  - L6 (W1): `checkWriteAccess` with the stored passphrase gives `ok:true`. Report its latency.

  L2–L4 add 3 failures to the window, well under 10. **Not run live, by design:**
  - Rotation: changing `Config` is a user step. Node scenario 2 covers it, and a stale passphrase is
    just a wrong string to the server.
  - Triggering the lockout: it would lock the real admin out for up to 10 minutes. Node covers it.
  - Busy: Node covers it.
- No device check: nothing on screen changes. TalkBack and accessibility are never enabled.

## Evidence To Capture

Node and gate counts. The three failure demonstrations with SHA-1 restores. The L1–L6 codes, L6
latency, and the deployed `Post.gs` SHA-1 matching the repo. That the passphrase never appeared in
any output, by stating it, never by grepping the value into a log. Everything goes in
`feature_list.json` evidence and `PROGRESS.md`.

## Validator Checklist

- [ ] Every `ACTIONS` entry passes the guard in `handlePost`, and no action calls it itself. The
  table-driven test covers future actions without edits.
- [ ] `Config` is read on every request. The passphrase never reaches the cache, a response or a
  message.
- [ ] Write actions run under the lock, after the guard. `checkWriteAccess` leaves no tab.
- [ ] Rate limit as approved in W2. Cache failure fails open, and that is tested.
- [ ] `AdminWriter` follows scenario 8. It clears only on `invalid_passphrase` or
  `passphrase_not_set`, and it sends no request when logged out.
- [ ] No UI, Gradle, Konsist, manifest or `:app` change. `Code.js`, `Jams.js`, `Catalog.js` and
  `Normalize.js` are byte-identical.
- [ ] The README marker and `Known:` list match the code. The docs are updated as listed.
- [ ] Gate three `wired`, Node green, live L1–L6 recorded, no secret in the repo or the outputs.

## User Approvals

- **W1: how a valid write is verified live (recommended: a).**
  - (a) You add `bluesjam.debugAdminPassphrase` once to the git-ignored `local.properties`. Only the
    agent's scratchpad check script reads it, without printing it. No build reads it in this slice.
    `checkWriteAccess` creates the tab `_prueba_escritura` in the real Sheet and deletes it within
    the same request. Musicians never see it, because no route lists tabs. Trade-offs: the
    passphrase sits in plaintext on your PC next to the URL, and agent tooling handles it. If you
    happen to have the Sheet open, you may see the tab flash for about a second.
  - (b) No live valid write: Node tests plus the negative live checks only. The first mutation slice
    then faces the same question.
  - (c) You run the L6 command yourself.
- **W2: rate limiting (recommended: a).**
  - (a) A global counter in CacheService: 10 failed guesses per fixed 10-minute window. After that,
    every passphrase check answers `rate_limited` until the window ends: login and writes, the right
    passphrase included. Cache failures fail open. That caps guessing at about 1,440 a day. The cost
    is that anyone with the URL can lock the admins out for up to 10 minutes at a time, repeatedly.
    The login shows the existing "Probá de nuevo en un rato."
  - (b) No limit (keep the D-11 acceptance).
  - (c) A sleep on each failure: it burns the owner's 30 simultaneous executions, so not advised.
- **W3: a rejected write logs the device out (recommended: yes).** On `invalid_passphrase` or
  `passphrase_not_set` from a write, the device forgets the stored passphrase. The admin controls
  disappear, and the outcome is `AccessChanged`, whose copy comes with the first mutation slice.
  Alternative: keep the passphrase and keep failing every write until a manual logout.
