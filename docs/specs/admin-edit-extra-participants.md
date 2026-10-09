# Feature Implementation Spec: Add and remove extra participants

## Source Feature

- `id`: `admin-edit-extra-participants`; area: `feature-next-jam`.
- Dependencies: `admin-adjust-lineup` (`accepted`). Source: `feature_list.json`.
- User-visible behavior: the admin adds or removes a named extra participant with a free-text instrument under `Otros`, without changing the song lineup or open slots.
- Decisions: D-05 (only the admin mutates), D-13 (repository mutation), D-18 (`Otros` is outside the lineup and never an open slot), D-12 (Rioplatense Spanish with vos).

## Readiness and repository findings

Decision B2 applies: implementer-local behavior and its disposable-tab deploy check are required
before this feature can pass. The shared Apps Script deployment and live L1-L5 checks belong to the
single app-complete batch deployment after all app implementation is complete. Do not deploy this
feature on its own.

The implementation builds on the accepted `admin-adjust-lineup` and `admin-assign-musician` patterns. `:core:data` already has public `SetlistRepository`, `DefaultSetlistRepository`, FIFO `SetlistMutationQueue` (`ids`, `order`, `writes`), `AdminWriter.send`, and cache mirroring in `SetlistDao`. `JamSong.extraParticipants` is already cached in `jam_extra`; no Room schema change is needed. `SetlistMapper` parses `Otros` by semicolon, trims each entry, ignores blanks, keeps order, and accepts `Nombre (instrumento)` or `Nombre(instrumento)`; malformed entries are dropped without dropping the song.

Apps Script already routes every write through the passphrase guard and script lock. Existing row identity is `date + songId` via `findSongRow_`; duplicate or missing song rows are refused before writing. The schema defines `Otros` as optional and its complete cell value is a semicolon-separated list. The server must preserve any unrelated columns and update only this cell.

## Goal and non-goals

In the expanded PrÃ³xima jam admin row, add a clear `Agregar a Otros` control and an inline form for a name and free-text instrument. Each existing extra also gets an accessible `Quitar` action. The admin sees a Sending-only optimistic row change with `Guardando…`; a confirmed server response updates Room, while a failed write restores confirmed data and creates a dismissible failure card. The musician projection never receives pending values or edit controls.

Do not add or remove lineup slots, assign musicians to slots, change suggestions, edit song detail, alter past jams, support offline writes, add a route, or add an action-registry/deeplink entry. Do not create a feature module, ViewModel, dependency, or Room migration. No changes to `Catalogo` or the Jams row.

## Mutation and validation contract

Expose two plain repository functions in `:core:data`:

```kotlin
addExtraParticipant(jamDate, songId, name, instrument): ExtraParticipantOutcome
removeExtraParticipant(jamDate, songId, ordinal, expectedName, expectedInstrument): ExtraParticipantOutcome
```

Use typed domain values if they fit existing `:core:model` validation conventions; otherwise validate at both repository and server boundaries. Normalize name and instrument by trimming and collapsing whitespace runs. Proposed implementation limits (feature has none in existing contracts): nonblank name up to 40 UTF-16 units, instrument up to 40 UTF-16 units, no control characters or `;`, `(`, `)` in either field, and at most 20 parsed entries per song. Reject a name beginning with `=`, `+`, `-`, or `@` and require at least one letter or digit, matching the existing safe musician-name rules. Keep read parsing permissive for legacy Sheet values. Add returns `invalid_extra` or `extra_limit` locally/server-side; these checks happen before a cell write.

The add action resolves the upcoming editable jam, uniquely finds the song by `id_tema`, parses the current cell with the schema grammar, appends the normalized pair in order, serializes as `Name (instrument); Name (instrument)`, writes only `Otros` as plain text, and returns the canonical resulting entry list. Empty/missing optional `Otros` means an empty list. Do not reject an identical pair: duplicate musicians or instruments may be intentional.

The remove action carries the 1-based ordinal in the latest confirmed list plus the exact expected normalized name and instrument. Under the same script lock it validates the current list, then removes only when that ordinal still contains that exact pair. A missing/mismatched target returns `extra_changed` without writing; do not remove the first equal pair by value alone or shift another entry based on stale UI state. Successful removal returns the canonical remaining list. Add always appends to the latest server value under lock, so concurrent admins do not overwrite one another. Error order follows existing writes: invalid date/song/fields, jam existence/uniqueness/editability, tab/header validity, unique song row, then list limit or stale target; no rejected request writes a cell.

`AdminWriter.send` carries the response payload. Treat malformed `ok` payloads as `Unavailable`. Mirror successful canonical extras with one Room transaction for exactly one cached available setlist song `(date, songId)`; update only that song's extras, preserving slots, song key, position and every other song. If Room cannot uniquely identify the row or the mirror throws, keep server success as the outcome and let the next refresh repair cache. On any server/client failure, Room remains unchanged. Do not clear the admin session on `AccessRefused`.

## Acceptance scenarios

1. **Add:** with one extra present, admin adds ` Ana ` / ` percusión `. It displays optimistically under `OTROS` and `Guardando…`; the Sheet cell becomes `Juan (saxo); Ana (percusión)`, Room receives the canonical response, and status clears.
2. **Remove:** admin removes the second of two extras. Only that entry disappears after confirmation; first entry, slots, song and order remain identical.
3. **Stale cross-device removal:** another admin changes the list before this request. The server returns `extra_changed` and writes nothing; local state reverts and shows a dismissible failure.
4. **Concurrent adds:** two clients add different entries. The lock makes both additions append to the latest cell; neither silently replaces the other's entry.
5. **Validation and bounds:** blank/invalid text cannot submit; over-limit text and a 21st extra are rejected without a POST/write as applicable. Server direct calls enforce the same limits.
6. **Failure:** `AccessRefused`, offline, unavailable, malformed response, and server rejection never alter Room after revert; a dismissible card identifies the failed operation and reason.
7. **Domain invariant:** extras remain last under `Otros`; neither add nor remove changes slot counts, instrument filters, open-slot counts, or lineup chips. Existing D-18 projections remain unchanged.
8. **Authorization:** musician view has no mutation controls or pending overlay. Calls go through `AdminWriter`; Apps Script rejects unauthorized writes even if called directly.
9. **Serialization:** add/remove share the existing FIFO repository queue with all setlist mutations; each UI intent is submitted once, and cancellation of the screen does not cancel a started write.

## Technical approach and expected files

No new modules or Gradle dependencies. Keep allowed dependency direction `:feature:next-jam -> :core:data/:core:model/:core:ui`; `:core:data -> :core:model`; bind existing repository in `:app` only if a new binding is actually required (expected: none).

- `backend/apps-script/src/Post.js`: add guarded `addExtraParticipant` and `removeExtraParticipant` actions; reuse router authorization, write lock, jam validation, row/header helpers and plain-text cell writer. Add a self-cleaning deploy check on a temporary marker tab that covers add, remove, stale ordinal, limits and final cell readback. Do not touch a real jam.
- `backend/apps-script/test/setlist.test.js` (or focused extra-participant test file), `backend/apps-script/README.md`, and `docs/apps-script-api.md`: cover request/response/error contract and validation.
- `:core:model`: add validated input value types only if needed to make limits/normalization a reusable domain contract; add focused JVM tests. Do not change read mapping of legacy `ExtraParticipant` values.
- `:core:data` `SetlistRepository.kt`, `DefaultSetlistRepository.kt`, `SetlistDao.kt` / `SetlistSlotDao.kt`: add outcomes, mutation state flows, shared IDs/order/writes, `AdminWriter.send` helpers and atomic `replaceExtras(date, songId, extras)` cache mirror. Reuse `jam_extra` and the repository's existing queue; do not create a second writer or Koin binding.
- `:feature:next-jam`: update `NextJamAdminState`, `NextJamAdminUiModel`/row admin model, `NextJamPresenter`, `NextJamScreen`, admin controls and copy/defaults to expose the form and remove actions. Overlay only Sending extras in the admin model before strip/panel mapping. Share the existing save-status and dismissible failure-card patterns. Use theme tokens, neutral admin action colors, 48dp targets, accessible descriptions, and Rioplatense copy with vos.
- Tests: repository concurrency, stale/error response, cache transaction and failure non-mutation; presenter optimistic/revert/musician isolation; mapping tests proving extras remain outside slot/filter counts; Apps Script direct and deploy-check tests.

## Durable documentation

- Update `docs/apps-script-api.md` with actions, fields, response, limits, error codes, locking and deploy check.
- Update `docs/sheet-schema.md` only if its existing `Otros` grammar needs the new validation/serialization limits clarified.
- Update `docs/user-and-access-model.md` permissions table to include add/remove `Otros`.
- Update `DESIGN.md` with the final inline form, action order, copy, error and accessibility behavior.
- `AGENTS.md`, `CONTEXT.md`, and `docs/domain-model.md`: no change expected; the existing authority and domain distinction already cover this feature.
- Update `PROGRESS.md` and `feature_list.json` with actual verification evidence/status during implementation, not during planning.

## Implementation sequence and verification

1. Implement and test Apps Script validation, canonical list handling, lock-protected writes, stale remove behavior and temporary-tab check.
2. Implement model validation if needed, repository outcomes/flows and the atomic Room extras replacement; test serialization, caller cancellation, returned payload validation, and no cache mutation on failure.
3. Implement admin-only inline UI, optimistic overlays, submit guarding, revert cards, copy and accessibility; add Molecule presenter tests and Compose/model tests.
4. Update API/design/access docs and record actual evidence. Run `CI=true ./init.sh` (must report `konsist`, `detekt`, `ktlint` wired), focused Apps Script Node tests, and the local disposable-tab deploy check; the check must never modify a real jam. Defer shared deployment and live L1-L5 checks under B2 until the app-complete batch. Record device smoke (add, remove, stale failure/revert, musician view and font scale) as a human follow-up when a device is available; missing `adb` does not block implementation passing.

## Validator checklist

- Both mutations are callable repository functions, share the existing FIFO queue and pass through `AdminWriter`; Apps Script validates authorization and all inputs under its lock.
- Server updates only the optional `Otros` cell and returns canonical parsed entries; stale removals fail without writes; concurrent appends preserve both values.
- Room changes only after a valid confirmed response, transactionally on the uniquely cached song; failures revert and do not change Room.
- UI exposes controls only to admin, optimistic values only while Sending, and dismissible failures; musician UI remains unchanged.
- Extras never become slots, never affect filters/counts, and remain under `Otros`.
- No feature dependency, ViewModel, new module, unnecessary Gradle dependency, or unrelated scope change.
- Required local gate, Node tests and disposable-tab check actually ran; evidence is recorded in durable progress artifacts. Shared deployment/live L1-L5 checks are deferred under B2. Device smoke is a tracked human follow-up when a device is available.

## Planning assumptions and risks

The source feature requires limits and concurrency semantics but existing decisions do not set them. The limits above are implementation defaults for this slice, not new product decisions; if repository validation conventions differ, preserve the stronger existing safety rules and document the exact chosen limits. `Otros` has no stable per-entry ID, so the expected-value ordinal check is required to make stale removals safe. The shared deployment and live checks are deferred under B2 until app implementation is complete; local checks do not prove that the future deployment is current.
