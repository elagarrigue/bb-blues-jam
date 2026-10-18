# Spec: setlist-mutation-contracts

One narrow contract per setlist mutation, each with its write, observe and dismiss. `SetlistRepository`
becomes their union. `:feature:next-jam` injects only the contracts it calls; `:app` binds every
contract to the one `SetlistRepository` instance.

Source: organizer's code review of 17 October 2026, finding 2. Depends on `typed-write-rejections`
(accepted). Next slice `setlist-mutation-engine` deduplicates the managers and splits packages, so
this slice stays on **contracts and injection**.

## What was inspected (findings)

- `core/data/.../setlist/SetlistRepository.kt` declares `addSong`/`observeAdds`, `removeSong`/
  `observeRemoves`, `setKey`/`observeKeyChanges`, `setSlotCount`/`observeLineupChanges`,
  `assignSlot`/`observeAssignments` and one shared `dismiss(id)`; it also extends
  `SetlistSlotClearRepository`, `SetlistMoveRepository`, `SetlistPublishRepository`,
  `SetlistJamCreationRepository`, `SetlistJamStatusRepository`, `SetlistJamEditRepository` and
  `SetlistExtraParticipantRepository`.
- Only three sub-interfaces have a dismiss: `dismissPublish`, `dismissJamStatusChange`,
  `dismissJamEdit`. `SetlistSlotClearRepository`, `SetlistMoveRepository` and
  `SetlistExtraParticipantRepository` have none, so their failures are dismissed only through the
  shared `dismiss(id)`. `SetlistJamCreationRepository` is write-only (no entries, no observe, no
  dismiss; `createJam` returns its outcome and logs through `WriteFailureLog`).
- `DefaultSetlistRepository.dismiss(id)` fans out to 11 collaborators (additions, removals,
  keyChanges, lineupChanges, assignments, extraChanges, slotClears, moves, `dismissPublish`,
  `dismissJamStatusChange`, `dismissJamEdit`). Each manager's own `dismiss` removes only a `Failed`
  entry with that id and ignores `Sending` ones and unknown ids.
- Ids come from one `AtomicLong` in `SetlistMutationQueue.ids`, shared by every manager. Next-jam
  merges failure cards from different kinds and sorts them by id (`failureCards`, `NextJamAdminMapping`).
- The only callers of `SetlistRepository.dismiss` in features are `setlist::dismiss` at
  `admin/NextJamAdminState.kt:46` and `songdetail/SongAdminState.kt:67`. `:feature:past-jams` already
  uses the typed dismisses through `JamsAdminWrites`.
- `SetlistRepository` is referenced (code, not comments) in `:feature:next-jam` main by:
  `NextJamPresenter`, `AddSongPresenter`, `SetKeyPresenter`, `AssignMusicianPresenter`,
  `SongDetailPresenter`, `rememberMoveControls` (`admin/MoveControlsState.kt`), `removalHandler`
  (`admin/RemovalHandler.kt`), `rememberNextJamAdminState` (`admin/NextJamAdminState.kt`),
  `rememberSongOverlays` (`overlay/SongOverlays.kt`), `LineupControls` (`songdetail/LineupControls.kt`),
  `ExtraParticipantFormState` (`songdetail/ExtraParticipantFormState.kt`), `SongAdminState`
  (`songdetail/SongAdminState.kt`). In tests: `AdminFakes.kt` (`FakeSetlistRepository`),
  `NextJamModuleTest` and several presenter tests.
- `:app` `AppModule` already binds `SetlistJamStatusRepository`, `SetlistJamEditRepository`,
  `SetlistPublishRepository` with `single<X> { get<SetlistRepository>() }`, and the four assistant
  `fun interface`s with lambdas. `AppModuleBindingsTest` asserts `assertSame` for the first three.
- `DefaultSetlistRepositoryTest`, `SetlistExtraParticipantRepositoryTest`, `SetlistMoveRepositoryTest`,
  `SetlistLineupChangesTest`, `SetlistJamEditsTest`, `SetlistJamStatusesTest`,
  `SetlistPublishRepositoryTest` call `repository.dismiss(...)` or the typed dismisses.

## Decisions

**D1. Ids stay global.** One counter, shared by every manager, is what lets Próxima jam order merged
failure cards and mutation entries by call order. Per-kind counters would break that ordering and
collide in `failureCards`. No change to `SetlistMutationQueue.ids`.

**D2. Dismiss is per contract, named `dismiss<Noun>(id)`, and the shared `dismiss(id)` is deleted.**
Routing is by type, not by lookup: a failure card is always built from an entry of one known kind, so
the code that builds the card already knows which contract owns it, and passes that contract's dismiss.
Semantics are unchanged from today's managers: removes a `Failed` entry with that id from that kind
only; a `Sending` entry or unknown id is left alone; dismissing an id of another kind is a no-op.
A dismiss of the wrong kind is therefore harmless, which is why the global fan-out can go.

**D3. Contract set and names.** Existing names are kept (they are already in tests, `:app` and
`:feature:past-jams`); five new ones follow the same `Setlist<Thing>Repository` pattern. Do not name
them like the assistant's `SetlistSongAdder`/`SetlistSongRemover`/`SetlistSongMover` (those stay
untouched; rule 19 already bans every `Setlist...Repository` in `:feature:assistant`).

| Contract | Write | Observe | Dismiss | Status |
|---|---|---|---|---|
| `SetlistSongAddRepository` | `addSong` | `observeAdds` | `dismissAdd` | new |
| `SetlistSongRemoveRepository` | `removeSong` | `observeRemoves` | `dismissRemove` | new |
| `SetlistKeyChangeRepository` | `setKey` | `observeKeyChanges` | `dismissKeyChange` | new |
| `SetlistLineupRepository` | `setSlotCount` | `observeLineupChanges` | `dismissLineupChange` | new |
| `SetlistAssignmentRepository` | `assignSlot` | `observeAssignments` | `dismissAssignment` | new |
| `SetlistSlotClearRepository` | `clearSlot` | `observeSlotClears` | `dismissSlotClear` | add dismiss |
| `SetlistMoveRepository` | `moveSong` | `observeMoves` | `dismissMove` | add dismiss |
| `SetlistExtraParticipantRepository` | `addExtraParticipant`, `removeExtraParticipant` | `observeExtraParticipantChanges` | `dismissExtraParticipantChange` | add dismiss |
| `SetlistPublishRepository` | `publishSetlist` | `observePublishes` | `dismissPublish` | unchanged |
| `SetlistJamStatusRepository` | `setJamStatus` | `observeJamStatusChanges` | `dismissJamStatusChange` | unchanged |
| `SetlistJamEditRepository` | `updateJam`, `deleteJam` | `observeJamEdits` | `dismissJamEdit` | unchanged |
| `SetlistJamCreationRepository` | `createJam` | none | none | unchanged (write-only: no entries exist) |

Each new interface file moves the matching KDoc from `SetlistRepository` verbatim (the ordering,
undispatched and cancellation promises are part of the contract). Signatures of every write and observe
are unchanged.

`SetlistRepository` becomes an empty-bodied union of the 12 interfaces (keep its KDoc as the mutation
inventory sentence, D-13). It declares no function of its own.

**D4. Implementation.** `DefaultSetlistRepository` keeps every write and its queue code as is (that is
the engine slice's job). It overrides the new dismisses by delegating to the existing manager `dismiss`
(`override fun dismissAdd(id) = additions.dismiss(id)`, and so on; the three typed ones already
exist). The manager classes' own `dismiss` methods keep their names (internal). For the three
interfaces that gain a dismiss, the delegating managers (`SetlistSlotClears`, `SetlistMoves`,
`SetlistExtraParticipants`) implement the new method directly under `by` delegation, renaming their
`fun dismiss` to the typed name with `override`, as `SetlistPublishes` does. If
`DefaultSetlistRepository` trips detekt's `TooManyFunctions`, delegate more with `by` (as it does
today for six interfaces) rather than suppress; no baseline, no `@Suppress` (architecture skill).

**D5. Feature injection.** A class receives exactly the contracts whose functions it calls, as separate
constructor or function parameters. Required mapping (observe-only counts as calling):

| Class (all `:feature:next-jam`) | Takes |
|---|---|
| `AddSongPresenter` | `SetlistSongAddRepository` |
| `SetKeyPresenter` | `SetlistKeyChangeRepository` |
| `AssignMusicianPresenter` | `SetlistAssignmentRepository` |
| `rememberMoveControls` | `SetlistMoveRepository` |
| `removalHandler` | `SetlistSongRemoveRepository` |
| `LineupControls` | `SetlistLineupRepository` |
| `ExtraParticipantFormState` | `SetlistExtraParticipantRepository` |
| `SongAdminState` | `SetlistSlotClearRepository` (clear and its dismiss) plus what it needs to dismiss the other song-level failure cards, see D6 |
| `rememberSongOverlays` | the five song-level observe sources: key, lineup, assignment, slot clear, extra participant |
| `rememberNextJamAdminState` | `SetlistSongRemoveRepository` (via `removalHandler`) and the dismisses of D6; it no longer takes a whole repository |
| `SongDetailPresenter`, `NextJamPresenter` | the union of what the helpers they call need |

Constructors that would exceed detekt's `LongParameterList` threshold group the song-level sources in
one internal feature class (recommended: `SongOverlaySources(keyChanges, lineups, assignments,
slotClears, extras)` in `overlay/`, constructed by a Koin `factory` in `nextJamModule` or from five
`get()`s), following the precedent of `JamsAdminWrites` in `:feature:past-jams`. Grouping is allowed
only of contracts the presenter really uses; a holder must not re-expose `SetlistRepository`. Koin
stays constructor-only (D-16): `nextJamModule` gains `get()`s for the narrow interfaces, which `:app`
resolves.

**D6. How a failure card's dismiss reaches the right manager.** `OverlayFailure` (in
`overlay/SongOverlays.kt`) gains a `dismiss: (Long) -> Unit` field, set where the failure is built
(`SongOverlays.failures` knows the entry type: key changes get `dismissKeyChange`, lineups
`dismissLineupChange`, assignments `dismissAssignment`, clears `dismissSlotClear`, extras
`dismissExtraParticipantChange`). `failureCards` drops its `onDismiss` parameter and builds
`EventHandler(key = id) { failure.dismiss(id) }`. `SongOverlays` therefore needs the five dismiss
functions: it receives the five contracts (it already receives their lists from
`rememberSongOverlays`); its constructor keeps defaults so tests that build it from lists only keep
compiling (`SongOverlays.failures` then uses no-op dismisses). `AdminState.onDismiss` becomes one
`(Long) -> Unit` per card source: the add failures use `dismissAdd`, the removal failures
`dismissRemove`, the move failures `dismissMove`; wherever `AdminState` builds add/remove/move cards it
passes the matching function. The id carried by `EventHandler(key = id)` is unchanged, so card
equality, ordering and tests keep their meaning.

## Scope

In scope: `:core:data` interfaces and `DefaultSetlistRepository` overrides; `:feature:next-jam`
constructor and parameter types and their call sites; `AppModule` bindings; `nextJamModule`; test
fakes; tests below; docs. Out of scope: any change to write behavior, queueing, mirrors, ids,
`SetlistMutationQueue`, managers' internals, package layout (engine slice), the assistant's
`fun interface`s and `AppModule` lambdas (unchanged), `:feature:past-jams` (already narrow; only its
imports if a type moved, which should not happen), UI copy, backend/Apps Script.

## Implementation steps

1. `:core:data`: add the five interfaces; add `dismiss*` to `SetlistSlotClearRepository`,
   `SetlistMoveRepository`, `SetlistExtraParticipantRepository`; rewrite `SetlistRepository` as the
   union without the shared `dismiss`; update `DefaultSetlistRepository` (delete the 11-way `dismiss`,
   add delegating overrides) and the three delegating managers. Move KDoc, do not lose it.
2. Update `:core:data` tests that call `repository.dismiss(...)` (`DefaultSetlistRepositoryTest` lines
   near 238, 459, 634, 673; `SetlistLineupChangesTest`; `SetlistExtraParticipantRepositoryTest`;
   `SetlistMoveRepositoryTest`) to the typed names. Assertions stay the same.
3. `:feature:next-jam`: re-type per D5, rework the dismiss path per D6. No `UiModel` shape change, no
   copy change.
4. `:app` `AppModule`: add `single<X> { get<SetlistRepository>() }` for each of the nine contracts not
   yet bound (the five new, `SetlistSlotClearRepository`, `SetlistMoveRepository`,
   `SetlistExtraParticipantRepository`, `SetlistJamCreationRepository`). Keep the three existing ones and
   the four assistant lambdas as they are. Update the comment above them.
5. Test fakes, see below. 6. Konsist, see below. 7. Docs, see below.

## Test fakes

`feature/next-jam/src/test/.../AdminFakes.kt` defines `FakeSetlistRepository : SetlistRepository` and
overrides everything, including `createJam`, `updateJam`, `deleteJam`, `observeJamEdits`,
`dismissJamEdit` and the shared `dismiss` (recorded in `dismissed`). Adaptation:

- Delete `override fun dismiss(id)` (it no longer exists); every typed dismiss the fake implements
  records into `dismissed` and also removes the matching `Failed` entry from its `MutableStateFlow`, as
  `dismissPublish` already does, so existing presenter tests asserting `dismissed` or the card
  disappearing keep their meaning.
- Add overrides for `dismissAdd`, `dismissRemove`, `dismissKeyChange`, `dismissLineupChange`,
  `dismissAssignment`, `dismissSlotClear`, `dismissMove`, `dismissExtraParticipantChange`.
- Keep one fake class implementing `SetlistRepository`: tests pass the same instance for every narrow
  parameter. Presenter tests then change only in how they construct the presenter (the same fake several
  times, or the grouped holder). The test sources of `:feature:next-jam` may keep naming
  `SetlistRepository` (see Konsist below); the production code may not.
- `NextJamModuleTest` currently binds `single<SetlistRepository>`; it must also bind each narrow
  interface to that instance (as `:app` does) so `nextJamModule` resolves. Add a second assertion there
  that every narrow `get()` in `nextJamModule` resolves.
- `AppModuleBindingsTest.FakeSetlistRepository` (in `:app`) gains the new typed dismisses and drops
  `dismiss`.
- Search every other `: SetlistRepository` implementer with Grep before finishing (`DefaultSetlistRepositoryTest`
  helpers, past-jams tests); the gate will name any missed.

## Konsist rule (decision: add it)

Worth adding as rule 20, `next-jam-injects-narrow-setlist-contracts`: in `:feature:next-jam` and
`:feature:past-jams` **main** sources no import or qualified reference to
`com.bbbjam.core.data.setlist.SetlistRepository` (import plus fully qualified text match, like rule 19's
shape, ignoring KDoc is not required: import lines and `com.bbbjam.core.data.setlist.SetlistRepository`
text only, so a bare `[SetlistRepository]` in a comment without an import passes). Restricting to main
sources lets test fakes implement the union. Rationale: without it the next edit can quietly take the
whole repository again, which is exactly the finding. It is a new verification rule, so it must be
shown failing first: before step 3, add the rule (or run it at the start) and record its failing run
against the current tree (the 12 files above), saved under `docs/evidence/setlist-mutation-contracts/
konsist-failing-run.txt`; it must go green after step 3. Known limit to record in the rule's KDoc and
the architecture skill: text match, an import alias or a bare comment mention escapes. The rule count
becomes 20; update the "19 rules" statements in the architecture skill and `AGENTS.md`/docs where they
appear (grep "19 rules" and "rule 19"). Rule 19 and its regex stay unchanged.
This is an additive rule, not a change to an existing verification rule; if the implementer finds it
needs to loosen anything existing, stop and ask.

## Verification

1. Baseline `CI=true ./init.sh` exit 0 with konsist, detekt and ktlint `wired`; record the test count
   (typed-write-rejections ended at 1146 testcases).
2. Konsist rule 20 failing run recorded before the feature module is edited, then green.
3. New `:core:data` test (in `DefaultSetlistRepositoryTest` or a new `SetlistDismissTest`): create a
   `Failed` entry of each of the 11 kinds with entries, and for each typed dismiss assert it removes
   only that kind's failed entry; that dismissing a `Sending` entry or id 999 does nothing; that
   dismissing id N through the wrong kind's contract leaves entry N in place. A mutant (swap
   `dismissKeyChange` to call the removals manager) must fail it; record the red run.
4. `AppModuleBindingsTest`: for all 12 contracts, `assertSame(repository, koin.get<X>())` against the one
   bound `SetlistRepository`, including the new nine (this is the "all resolve to one instance" proof).
   Also assert a write through a narrow binding lands on the same fake (shared entries) for at least two
   unrelated contracts, e.g. `SetlistKeyChangeRepository.observeKeyChanges()` and `SetlistRepository`'s
   are the same flow instance.
5. `NextJamModuleTest`: each presenter in `nextJamModule` resolves with the narrow bindings.
6. Behavior unchanged: every existing presenter test in `:feature:next-jam` and `:feature:past-jams`
   passes with assertions untouched (only construction and fake plumbing may change); a card "Cerrar" test
   per source (add, removal, move, key, lineup, assignment, clear, extras) asserts the right typed dismiss
   was called with the card's id and no other (new or extended in the presenter tests).
7. Greps print nothing: `SetlistRepository` in `feature/next-jam/src/main` outside comments; `\.dismiss\(`
   on a repository in features; `fun dismiss(id: Long)` in `SetlistRepository.kt`.
8. `CI=true ./init.sh` exits 0 with konsist, detekt, ktlint `wired`; test count before and after
   recorded in `feature_list.json` evidence (expected: more, none removed).
9. No device needed.

## Docs to update

`.claude/skills/architecture/SKILL.md` (module table `:app` row, the `SetlistRepository` narrative: it is
now the union; typed dismisses; rule count 20; the ":feature:next-jam still `{key}`" amber lines stay),
`AGENTS.md` (rule counts if stated), `PROGRESS.md` only for this feature's own entry (do not touch other
sessions' uncommitted entries), `feature_list.json` evidence for this id only. Never touch `presentations/`.

## Risks and assumptions

- Assumption: nothing outside `:feature:next-jam` calls the shared `dismiss(id)`; a Grep showed only the
  two `setlist::dismiss` sites, but the implementer must re-run it before deleting the function.
- Assumption: the 11 managers' `dismiss` semantics are identical (failed-only filter by id), read in
  `SetlistRemovals`; the new `dismiss*` test (step 3 of Verification) is what proves it for all.
- Risk: `NextJamPresenter`/`SongDetailPresenter` get many parameters; use the grouped holder of D5 rather
  than a `@Suppress`.
- Risk: renaming `fun dismiss` in the three managers changes their own tests; keep their assertions.
- Not decided here (left for the engine slice): collapsing the managers, a generic pending-entries holder,
  and any per-mutation subpackage.
