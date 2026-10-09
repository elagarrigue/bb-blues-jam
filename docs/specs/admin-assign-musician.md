# Feature Implementation Spec: Assign a musician to an open slot

## Source Feature

- `id`: `admin-assign-musician`; area: `feature-next-jam`.
- Dependencies: `admin-add-song-to-setlist` and `song-row-expansion`, both accepted.
- User-visible behavior: admin taps an open slot, enters a musician name with prior-jam suggestions, and fills that slot.
- Decisions: D-05 (only admin assigns), D-13 (public repository mutation), D-18 (Otros are not slots). Past jam tabs are the suggestion source; same-name musicians are not disambiguated.

## Readiness and Current State

Historical implementation-time state (8 October 2026): the local implementation was underway and
the user had deferred the shared deployment/live validation until app implementation was complete.
That shared deployment and live validation completed on 9 October 2026 (see evidence below and in
`feature_list.json`); this feature was independently accepted. Deferral instructions in the plan
below describe the implementation-time workflow and are not the current deployment state.

Current shared implementation to reuse:

- `:core:model` now has validated `SlotPosition` and `MusicianName` types and resolves ordinals against the original lineup order.
- Apps Script `Post.js` implements guarded/locked `setSlotCount` and `assignSlot`, sharing `findSongRow_`, canonical slot headers, `presentSlotFields_` and `checkSetlistWrite`. The latter tests both writes on a disposable tab. The 9 October live check passed; the remote file hash was not independently observable.
- `:core:data` composes `SetlistAssignments` inside the existing `DefaultSetlistRepository`; its mutations share repository IDs, ordering, writer and scope.
- `:core:ui` `LineupPanelUiModel` has optional action metadata, absent by default. `Lineup.toLineupPanel(extras)` retains the open-first display order while action identity comes from original lineup order.
- `:feature:next-jam` overlays only Sending assignments before filters/counts/strip/panel derivation and uses its dismissible failure cards. The dedicated presenter reads suggestions only from cached jams.

## Goal

In admin mode on PrÃ³xima jam, each open line in an expanded song row offers a 48dp `Anotar` action. It opens a full-screen `Anotar mÃºsico` flow for that fixed slot. A valid submission closes the screen and optimistically fills the admin's row; success confirms the cached slot, while failure restores the open slot and shows a dismissible error. Musicians see no new controls or optimistic data.

The mutation is `SetlistRepository.assignSlot(date, songId, instrument, ordinal, musicianName)` in `:core:data`, callable without UI (D-13). The server authorizes and validates every write.

## Non-Goals

- Clearing/replacing a filled slot or changing the lineup; clearing belongs to `admin-clear-slot`.
- Adding/removing `Otros`, musician self-signup, a musicians entity, profiles, or disambiguation.
- Assignment from song detail, offline queueing, external music APIs, new sheet reads, deeplinks, or an action-registry change.
- Any app-specific behavior that requires a live deployment to implement or locally test.

## Domain and Server Contract

A slot identity is `(date, songId, instrument, ordinal)`. Ordinal is the 1-based order among the instrument's lineup slots, never the drawn index (the panel stable-partitions open and filled lines). The server maps that ordinal to the corresponding instrument column among columns whose row value is not `-` (U1). For example, if `Guitarra 1` is `-` and `Guitarra 2` is active, ordinal 1 resolves to `Guitarra 2`.

Normalize a name by trimming and collapsing whitespace runs to one space. Accept only a non-empty name of at most 40 UTF-16 units, with no control characters, `;`, `(` or `)`, not starting with `=`, `+`, `-` or `@`, and containing at least one letter or digit. Mirror this validation in `:core:model`; keep read parsing permissive for names already in the Sheet. Assignment never overwrites: an occupied target returns `slot_taken`.

Server validation order must be deterministic and write-free until all checks pass: date, song id, instrument/ordinal, name, jam existence/uniqueness/editability, tab and required headers, unique song row, active U1 slot, then empty target cell. Preserve existing API error conventions (`invalid_date`, `invalid_song`, `invalid_slot`, `invalid_name`, `unknown_jam`, `duplicate_date`, `jam_not_editable`, `song_not_in_setlist`, `missing_header`, `duplicate_header`, `duplicate_song`, `slot_not_in_lineup`, `slot_taken`). Use `setValue` on the one slot cell with normalized plain text. Never write `Catalogo`.

## Acceptance Scenarios

1. **Successful assignment:** Admin opens an open slot, enters ` Tincho `, submits, and returns to the list. The admin row immediately shows `Tincho` as filled with `Guardando…`; on `Done`, Room contains that name and status clears.
2. **Fixed slot and U1:** instrument is presented as context, with no selector. The submitted ordinal comes from lineup order. In the U1 example above, server writes `Guitarra 2`.
3. **Failure/revert:** `AccessRefused`, `Offline`, `Unavailable` and `Rejected(code)` leave Room unchanged, remove the pending overlay, and show a dismissible card naming the musician/song and an appropriate localized reason.
4. **No overwrite:** concurrent assignment to an occupied cell returns `slot_taken`, changes no cell and does not replace the cached name.
5. **Suggestions:** `ESTA JAM` lists names already in the upcoming jam, including `Otros`; `JAMS ANTERIORES` lists prior jam names excluding current jam matches. Lists deduplicate by accent/case-insensitive search key, preserve most recent spelling, and sort prior names by recent appearance then alphabetically. Typing filters by a word prefix; at most 8 entries per section. Tapping a suggestion submits that spelling once.
6. **Validation:** blank submit is disabled. Invalid nonblank input displays one inline caption and submit/IME do nothing. The slot is fixed.
7. **Gone state:** disabled admin flag, absent upcoming song/slot, a filled cached slot, or an assignment already sending to that slot shows `Ese cupo ya no está libre` and a `Volvé` action.
8. **Musician state:** no assignment action or optimistic overlay is exposed when admin mode is off.
9. **Ordering and single submit:** writes use existing repository ordering; double taps and repeated suggestion taps send at most once; leaving the screen does not cancel an accepted write.

## Technical Approach and Expected Changes

Use existing modules and allowed dependency direction only; no new module or Gradle dependency.

- `backend/apps-script/src/Post.js`, `backend/apps-script/test/setlist.test.js` (or focused slot test), and `README.md`: add `assignSlot` action, name/U1 validation, single-cell write and extend `checkSetlistWrite` to exercise assignment on its temporary tab. Reuse the current writer/auth/lock and row/header helpers. At spec time, server changes remained local pending the user's shared deployment.
- `:core:model`: add `SlotPosition` and validated `MusicianName` plus JVM tests; add only the lineup-to-slot ordinal mapping needed by assignment. Existing `Lineup.withSlotCount` remains the adjust-lineup implementation.
- `:core:data`: add assignment DAO/cache operation, public outcome and pending entry types, repository contract and `DefaultSetlistRepository` implementation. Reuse existing repository ids, scope, writer ordering, cache transaction patterns and composition; do not create a Koin binding solely for the helper.
- `:core:ui`: extend `LineupLineUiModel` and `toLineupPanel` with optional slot identity/status/action decoration, defaulting to current read-only behavior. Update mapper/composable tests. Existing musician rendering must remain unchanged.
- `:feature:next-jam`: add the Sending-only assignment overlay before strip/panel/filter/count derivation; add open-slot action in admin rows, failure message mapping and assignment screen presenter/UI/copy/defaults/suggestions. Preserve current row action order and shared failure-card behavior.
- `:app`: add a typed route/parser for `(date, songId, instrument, ordinal)`, destination with existing slide/inset/RESUMED conventions, and callback wiring from the tab shell.

### Screen and copy requirements

Use `BluesJamTheme` tokens only. Full-screen, scrollable content with `imePadding`; 48dp back and submit actions; heading `Anotar músico`; song title; `CUPO` plus fixed instrument; outlined `Nombre` field with Done IME; inline validation; sections `ESTA JAM` and `JAMS ANTERIORES`; suggestion rows at least 48dp. Amber remains for open-slot affordance and key only; pending/filled names use filled-slot styling and muted `Guardando…`.

Use Rioplatense Spanish with vos. Copy includes `Anotar`, `Anotar músico`, `CUPO`, `Nombre`, `ESTA JAM`, `JAMS ANTERIORES`, `Guardando…`, `Usá hasta 40 caracteres.`, `Escribí un nombre con letras o números.`, `La planilla no aceptó ese nombre.`, and `Ese cupo ya no está libre` / `Volvé a la próxima jam para ver la lista actual.`. Error cards identify the attempted musician/song and map `slot_taken`, `slot_not_in_lineup`, `invalid_name` and `song_not_in_setlist` to clear user-facing messages.

## Durable Documentation

- Update `docs/apps-script-api.md`, `docs/sheet-schema.md`, and `docs/domain-model.md` for the endpoint, write target, U1 identity and name validation.
- Update `DESIGN.md` for the assignment action and screen.
- Update `.claude/skills/architecture/SKILL.md` for new model/data/UI contracts, overlays and route.
- Update `docs/risks-and-open-questions.md` only for material new risks or unresolved decisions. `AGENTS.md`, `CONTEXT.md`, and user/access rules need no change.
- Update `feature_list.json` and `PROGRESS.md` with truthful local verification and the pending shared deployment/live checks; do not mark accepted or claim live evidence. These updates belong to implementation/handoff, not this planning task.

## Implementation Tasks

1. Implement model validation and ordinal mapping with focused JVM tests.
2. Implement the Apps Script action, validation order, U1 resolver, write-free failures and temporary-tab self-check; run Node tests locally.
3. Implement repository/cache mutation and assignment outcomes, maintaining existing write order and cancellation semantics; test cache targeting, validation, ordering, duplicate/missing slots and failure paths.
4. Add shared panel slot identity/actions and admin-only assignment overlay, failure UI, suggestions and screen; test mapper, filter/count consistency, Sending-only overlay and musician invariance.
5. Add route parsing and navigation wiring with route tests.
6. Update durable docs and evidence. Run `node --test backend/apps-script/test/*.test.js` and `CI=true ./init.sh`; record actual results. Do not deploy or perform live checks yet.
7. Historical implementation sequence: once app work was complete, coordinate the shared Apps Script deployment and live validation across affected features, then run independent validation. The batch checks are now recorded below; retain their dated evidence.

## Verification and Evidence

Local verification: successful Node suite and `CI=true ./init.sh` with `konsist`, `detekt` and `ktlint` all `wired`. Add focused tests for name rules; U1 and inactive/missing slot cases; exactly one-cell writes and no writes on every rejection; cache changes only after server `Done`; transaction targeting by date/song/slot; caller cancellation and shared write ordering; Sending overlay/latest entry, Failed revert, filter/count consistency, screen Gone/validation/single-submit/suggestion sorting and matching, musician invariance, and route parsing.

At implementation time, capture actual Node and gate counts/results, failure demonstrations with verified restores, and state that deployment/live checks are not run yet. Do not include passphrases, deployment URLs or musician names in logs/evidence. No device run is required for local completion; if later used, use only demo data and do not alter accessibility settings.

## Risks and Unresolved Blockers

- Historical implementation-time status (8 October 2026): the shared Apps Script deploy and live checks awaited app completion, and deployment-sensitive acceptance remained unverified. The batch completed 9 October (see evidence above).
- Historical implementation-time status (8 October 2026): `admin-adjust-lineup` was locally implemented and awaiting live validation/independent acceptance. Its server changes established shared helpers/patterns used by assignment.
- Assignment and lineup adjustment both touch slot columns. Keep the U1 resolver and per-cell writes consistent with `setSlotCount` and existing header mapping; batch only if an existing server helper supports it and tests prove the same validation/write semantics. Assignment itself must change exactly one slot cell.
- `SetlistRepository` is already large; if detekt trips, split by concern through an explicit architecture review rather than suppressing the rule.
- Suggestions are display names; a past spelling may be inaccurate and equal names remain ambiguous by product decision.

## Validator Checklist

- [ ] Every write is authenticated/validated server-side, uses U1 and changes at most one plain-text slot cell after all checks.
- [ ] Mutation is a public `SetlistRepository` function; Room changes only on `Done`; pending overlay applies only Sending and failure reverts.
- [ ] Slot ordinal is derived before presentation partitioning; no instrument selector; musician output is unchanged.
- [ ] Suggestions use cached current/past jams only and follow dedupe/order/filter limits.
- [ ] Theme tokens and amber roles are respected; copy uses vos.
- [ ] Focused tests and standard gate actually ran; all three tools wired; dated implementation evidence and subsequent live validation are recorded accurately.
- [ ] Relevant durable docs are updated; no unrelated feature scope or new module/dependency.

## Implementation File Map

Expected additions should follow the package names already used in the repository:

- `core/model/src/main/kotlin/com/bbbjam/core/model/SlotPosition.kt`, `MusicianName.kt`, and corresponding tests under `core/model/src/test`.
- `core/data/src/main/kotlin/com/bbbjam/core/data/cache/SetlistDao.kt`, `setlist/SetlistRepository.kt`, `DefaultSetlistRepository.kt`, and focused assignment model/helper files under `setlist/`; corresponding DAO and repository tests under `core/data/src/test`.
- `core/ui/src/main/kotlin/com/bbbjam/core/ui/lineup/LineupPanelUiModel.kt`, `LineupPanelMapper.kt`, and `LineupPanel.kt`; mapper/default tests under `core/ui/src/test`.
- `feature/next-jam/src/main/kotlin/com/bbbjam/feature/nextjam/NextJamPresenter.kt`, `NextJamUiModel.kt`, `NextJamScreen.kt`, `NextJamCopy.kt`, a Sending-only assignment overlay, and focused assignment screen/suggestion files and tests.
- `app/src/main/java/com/bbbjam/navigation/AppRoutes.kt`, `AppNavHost.kt`, and `TabsShell.kt`, with `AppRoutesTest` coverage.
- `backend/apps-script/src/Post.js`, the existing setlist test suite or a focused slot test, and `backend/apps-script/README.md` for the local endpoint contract.

Reuse the current Koin wiring and existing public repository boundary. Add no feature-to-feature dependency. If a shared UI contract change affects song-row expansion or other callers, retain defaults so existing callers stay read-only and compile without importing assignment behavior.

## Focused Test Cases

- Name normalization is deterministic; reject blank, over-40 UTF-16 units, control characters, separators, formula prefixes and strings without a letter/digit. Verify accepted surrounding/repeated whitespace normalizes as specified.
- Slot-position mapping keeps instrument ordinal when open/filled partition changes drawn order; reject ordinals outside that instrument's current lineup.
- Server success writes one slot cell as plain text. Cover U1 (`Guitarra 1 = -`, active second guitar), reordered headers, dash cells, occupied cells, duplicate/missing headers, duplicate song rows and every validation error; assert all rejected requests leave the fake Sheet unchanged.
- `checkSetlistWrite` exercises the assignment write/readback/cleanup path on its disposable tab without touching a real jam row.
- DAO updates exactly the matching date/song/instrument/ordinal; missing, duplicate, filled and unavailable setlist cases leave cache unchanged. Repository tests cover invalid input sends nothing, server `Done` updates cache before pending removal, failure retains no optimistic cache mutation, call ordering, cancellation and dismiss.
- UI mapper tests preserve existing musician models and panel ordering while exposing slot identities only on actionable admin lines. Overlay tests cover newest Sending entry per slot, Failed revert, and agreement among strip, panel, filter and counts.
- Presenter/screen tests cover Gone states, fixed slot label, invalid/blank input, one submit only, current and past suggestion content, recency/dedupe/accent-insensitive word-prefix matching, limits and tap-to-submit behavior.
- Route tests accept a valid ISO date, parsed `SongId`, known `Instrument` and in-range ordinal; malformed/out-of-range arguments do not navigate.

## Post-deployment verification (9 October 2026)

L1 listed `assignSlot`. `checkSetlistWrite` returned `ok` in 6.378 s and exercises assignment
write/readback/cleanup on a disposable tab. Before/after `readJams` matched (two jams, 26 songs;
aggregate SHA-256 prefix `42CFABE0DE3C8D71`). No real jam was assigned. The user deployed the source;
the remote file hash was not independently observable.
