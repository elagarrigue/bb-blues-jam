# Spec: setlist-mutation-engine

Feature: `setlist-mutation-engine` (area core-data). Depends on `setlist-mutation-contracts` (accepted, 6177305).
Origin: organizer's code review of 17 October 2026, findings 3, 4 and the queue part of 7.
Pure refactor of `:core:data`. No user-visible change, no server change, no Room schema change.

## Goal

1. One generic pending-entries holder replaces the `MutableStateFlow<List<E>>` + Sending/Failed/dismiss code copied in the 11 managers.
2. One queue helper replaces the ordered undispatched write copied about 25 times.
3. Every manager takes `SetlistMutationQueue` whole (never `ids`/`order`/`writes` separately).
4. `SetlistMutationQueue` and `WriteFailureLog` are required constructor parameters of `DefaultSetlistRepository`; no manager instance is built in a default parameter.
5. `DefaultSetlistRepository` is pure delegation: no `override` of its own, all twelve contracts via `by`.
6. `core.data.setlist` (59 main files) is split into per-mutation subpackages, none above ~20 files.

Non-goals: any change to a public type, function signature, KDoc meaning, outcome mapping, request field, Room mirror, log line, ordering or cancellation behaviour; any `:feature:*` logic change; Apps Script; new mutations; changing the 12 contracts; touching `presentations/`.

## Repository findings (inspected)

- `core/data/src/main/kotlin/com/bbbjam/core/data/setlist/`: 59 files, 2604 lines. `SetlistMutationQueue` = `ids: AtomicLong`, `order: Mutex`, `writes: Mutex`.
- Ordered write exists inline in: `DefaultSetlistRepository` (removeSong, setKey, assignSlot) and `SetlistSongAdds`, `SetlistMoves`, `SetlistPublishes` (all three take `ids`, `order`, `writes` separately), `SetlistLineupChanges`, `SetlistSlotClears`, `SetlistExtraParticipants`, `SetlistJamStatuses`, `SetlistJamCreations`, `SetlistJamEdits` (take `queue`). `grep UNDISPATCHED` in setlist main: 9 files, 24 occurrences.
- Variations the helper must preserve, found by reading:
  - Add (`SetlistSongAdds.enqueue`): under `order` it resolves the song from `CatalogDao`; an unknown song publishes a Failed entry and returns `NotAdded(Rejected("unknown_song"))` **without taking `writes`** and without queuing. So the helper needs an immediate-result path.
  - Move: `start` may publish an already Failed entry (`toPosition < 1`); `send` still runs under `writes` and returns `NotMoved` without a request. Keep (it is a normal Send).
  - Assign: `start` returns a triple `(entry, target, localError)` passed to `send`.
  - JamStatus: dismiss removes any non-Sending entry (Failed and Outdated); others remove only Failed. Remove mirrors even on `song_not_in_setlist` yet fails the entry; Key and Move mirror then remove the entry on Done; add mirrors then removes.
  - `WriteFailureLog` is used only by JamStatuses, JamEdits, JamCreations and Publishes.
- `DefaultSetlistRepository` already delegates 9 contracts with `by`; Remove, Key, Assignment are still its own functions over `SetlistRemovals`/`SetlistKeyChanges`/`SetlistAssignments` (which are not contract implementers). Defaults today: `queue`, `failureLog`, and seven manager instances.
- Constructed in: `core/data/.../di/DataModule.kt` line 58 (`DefaultSetlistRepository(get(), get(), get(), get(), failureLog = get())`) and 9 test files in `core/data/src/test/kotlin/com/bbbjam/core/data/setlist/` (`DefaultSetlistRepositoryTest`, `SetlistDismissTest`, `SetlistExtraParticipantRepositoryTest`, `SetlistJamCreationsTest`, `SetlistJamEditsTest`, `SetlistJamStatusesTest`, `SetlistLineupChangesTest`, `SetlistMoveRepositoryTest`, `SetlistPublishRepositoryTest`). Only `:core:data` constructs it.
- 59 files in `app`, `feature/*`, `core/ui` reference `com.bbbjam.core.data.setlist` (imports).
- Konsist (`konsist-test/.../ModuleIsolationTest.kt`): rule 18 `assistant-reads-only` regex `\bcom\.bbbjam\.core\.data\.(setlist|admin|remote|cache)\b` matches subpackages, so it stays valid. Rule 19 regex `\bcom\.bbbjam\.core\.data\.(setlist\.(Setlist\w*Repository|\*)|admin\.(AdminWriter|\*))` would **stop matching** once `SetlistXRepository` lives in `setlist.<sub>`. Rule 20 matches `core.data.setlist.SetlistRepository`, which stays at the root. `package-under-module-root` is satisfied by any subpackage.
- Assumption: detekt defaults apply (`config/detekt/detekt.yml` only overrides naming): TooManyFunctions 11 per class, LongParameterList 6 for functions / 7 for constructors. No baseline, no suppression.

## Design

### Shared engine (package `com.bbbjam.core.data.setlist.engine`, all `internal`)

- `SetlistMutationQueue` moves here, unchanged fields, plus the helper:
  `suspend fun <T> SetlistMutationQueue.enqueue(scope: DataScope, start: suspend (id: Long) -> QueuedWrite<T>): T`
  Body = the existing pattern exactly: `scope.async(UNDISPATCHED) { order.withLock { val w = start(ids.incrementAndGet()); when (w) { Send -> scope.async(UNDISPATCHED) { writes.withLock { w.block() } }; Immediate -> deferred of value } }.await() }.await()`. The `Immediate` branch must not touch `writes` (add's unknown song). `start` runs under `order`, so a manager publishes its entry there.
- `sealed interface QueuedWrite<T>`: `Send(block: suspend () -> T)` and `Immediate(value: T)`.
- `PendingEntries<E : Any>(idOf: (E) -> Long, canDismiss: (E) -> Boolean)`: `observe(): StateFlow<List<E>>` (callers keep returning `Flow` as today), `add(entry)`, `remove(id)` (the "Done" path), `fail(id, transform: (E) -> E)` (replace the entry with that id), `dismiss(id)` (removes only an entry where `canDismiss` holds). Managers keep their own mirror call and decide the order: mirror, then `remove`, as today. The holder never mirrors.
- Moved here as well because they are shared: `SetlistRequestFields`, `SongTitles` (`displayedTitle`), `ConfirmedSlots`, `WriteFailureLog` (the file; type stays internal).

### Per-mutation subpackages (user's constraint: public types keep their names; only the package changes)

`setlist` root keeps only `SetlistRepository` (the union), `DefaultSetlistRepository`. Everything else of a mutation goes together, contract + outcome + entry type + manager:

| Subpackage | Files (current names) |
|---|---|
| `setlist.add` | SetlistSongAddRepository, SetlistSongAdder, AddSongOutcome, SetlistAdd, SetlistSongAdds |
| `setlist.remove` | SetlistSongRemoveRepository, SetlistSongRemover, RemoveSongOutcome, SetlistRemove, SetlistRemovals |
| `setlist.key` | SetlistKeyChangeRepository, SetKeyOutcome, KeyChange, SetlistKeyChanges |
| `setlist.lineup` | SetlistLineupRepository, SetSlotCountOutcome, LineupChange, SetlistLineupChanges |
| `setlist.assign` | SetlistAssignmentRepository, AssignSlotOutcome, Assignment, SetlistAssignments |
| `setlist.clear` | SetlistSlotClearRepository, ClearSlotOutcome (wherever it is declared today), SlotClear, SetlistSlotClears |
| `setlist.move` | SetlistMoveRepository, SetlistSongMover, MoveSongOutcome, SetlistMove, SetlistMoves |
| `setlist.extras` | SetlistExtraParticipantRepository, ExtraParticipantMutation, SetlistExtraParticipants |
| `setlist.publish` | SetlistPublishRepository, PublishOutcome, SetlistPublish (wherever declared), SetlistPublishes |
| `setlist.jamstatus` | SetlistJamStatusRepository, SetJamStatusOutcome, JamStatusChange, SetlistJamStatuses |
| `setlist.jamcreate` | SetlistJamCreationRepository, JamCreator, CreateJamOutcome, SetlistJamCreations |
| `setlist.jamedit` | SetlistJamEditRepository, JamEdit, JamEditLogNames, UpdateJamOutcome, DeleteJamOutcome, SetlistJamEdits |

The implementer places any file not listed by the same rule (its mutation's subpackage) and reports the final count per package. `package` equals the directory. Justification for moving public types too: leaving them in `setlist` keeps about 40 files in one package and fails the "no package above ~20 files" check; the cost is import-only edits in `:feature:*`, `:app` and `core/ui` (about 59 files), mechanical and with no logic change. A reviewer must see those files differ only in `import` lines (and wildcard imports, if any, fixed to the new package).

### Managers

- Each manager: `internal class SetlistX(writer, setlistDao, [catalogDao,] scope, queue, [failureLog]) : SetlistXRepository`, at most 6 constructor parameters, holds a `PendingEntries<Entry>`, and each write is `queue.enqueue(scope) { id -> ...start...; QueuedWrite.Send { send(entry) } }`. `SetlistRemovals`, `SetlistKeyChanges`, `SetlistAssignments` become contract implementers (they take `scope` and `queue` and own `removeSong`/`setKey`/`assignSlot` with the KDoc now in `DefaultSetlistRepository`, kept meaning-for-meaning).
- Keep the existing `scope.async(UNDISPATCHED)` KDoc claim ("runs in the data scope from the first instruction") on the helper.
- Where a manager would exceed 10 functions (detekt TooManyFunctions), split a private helper file/object in the same subpackage; no `@Suppress`, no baseline.

### DefaultSetlistRepository

`internal class DefaultSetlistRepository(writer, setlistDao, catalogDao, scope, queue, failureLog) : SetlistRepository, <11 contracts> by <Manager>(...)` built in the supertype list, no default values, no body, no `override`. `SetlistRepository` is still the union (`setlist-mutation-contracts`), unchanged. The single place that builds `SetlistMutationQueue()` is `DataModule` (a `single`, or an inline argument; the Koin graph must still give every contract the one `DefaultSetlistRepository`, `:app` unchanged). Update the class KDoc to say it delegates.

## Tasks (ordered)

1. Baseline: `CI=true ./init.sh`; record exit code, three tools `wired`, testcase count (`docs/evidence/setlist-mutation-engine/baseline-init.txt`). Record `grep -rc UNDISPATCHED` and `ls | wc -l` per package.
2. Add `engine` package: `SetlistMutationQueue` (moved), `QueuedWrite`, `enqueue`, `PendingEntries`. Write `EngineTest`s only if the mutant step below shows a survivor (see Verification).
3. Convert managers one at a time (add, remove, key, assign, then the rest), each green before the next: replace the inline pattern with `enqueue`, the entry list with `PendingEntries`, constructor with `queue`. Do not alter request fields, outcome mapping, mirror order, log lines.
4. Move files into subpackages (`git mv`, then package lines and imports). Fix imports in `:app`, `:feature:*`, `core/ui` and `core/data` tests (test files that test a moved class move to the mirrored subpackage; edits limited to `package`/imports/constructor plumbing).
5. Make `DefaultSetlistRepository` pure delegation with required `queue`/`failureLog`; update `DataModule` and the nine test builders (add `SetlistMutationQueue()` and `WriteFailureLog { }` or the test's own log; this construction plumbing is the only allowed test change).
6. Amend Konsist rule 19's regex to `\bcom\.bbbjam\.core\.data\.(setlist\.(\w+\.)*(Setlist\w*Repository|\*)|admin\.(AdminWriter|\*))` and show it failing first: against the post-move tree with the old regex, a temporary import of `core.data.setlist.add.SetlistSongAddRepository` in `:feature:assistant` main must NOT be caught (demonstrates the gap), then caught with the new regex, then removed. Rule 18 and 20 need no change; state that after reading them. Keep 20 rules.
7. Docs: architecture `SKILL.md` as-built paragraph (engine, `PendingEntries`, `enqueue`, subpackage map, rule 19 regex, the sentence that `DefaultSetlistRepository` delegated only partly is replaced); update the `:core:data` layout mention of `setlist/`.

## Verification

- `CI=true ./init.sh` exit 0, `konsist`, `detekt`, `ktlint` all `wired`; testcase count before and after (expected unchanged unless a survivor mutant forces a new test; none removed).
- Existing `core/data/src/test/.../setlist` tests pass with assertions unchanged. `git diff` of those tests shows only `package`, `import` and constructor-argument lines. Feature/app diffs show only `import` lines (`git diff -U0 -- feature app core/ui | grep '^[+-]' | grep -v 'import\|^+++\|^---'` prints nothing).
- Greps recorded: `grep -rn "UNDISPATCHED" core/data/src/main/.../setlist` hits only the engine helper (2 occurrences); `grep -rn "queue.ids\|queue.order\|queue.writes\|ids: AtomicLong\|order: Mutex"` hits only the engine; no `= SetlistMutationQueue()` or `= WriteFailureLog` default anywhere; `DefaultSetlistRepository.kt` has no `override`, no `fun`; file count per package <= ~20; no `@Suppress` and no baseline added.
- Mutants on the shared helpers, each run against the **unchanged** setlist tests, each must go red, restored after (save the failing output in `docs/evidence/setlist-mutation-engine/`):
  1. `enqueue` drops the `order.withLock` (ordering and entry-publication order).
  2. `enqueue` drops `CoroutineStart.UNDISPATCHED` on the outer `async` (undispatched start / caller-cancellation tests).
  3. `enqueue` takes `writes` outside the inner `async` or removes the inner `async` (FIFO and cancellation).
  4. `Immediate` branch acquires `writes` (the unknown-song add must not wait behind earlier writes).
  5. `PendingEntries.dismiss` ignores `canDismiss` (`SetlistDismissTest`: a sending entry or another kind is left alone).
  6. `PendingEntries.remove` and `fail` swapped, or `fail` replaces the wrong id (Done/Failed state tests).
  7. In one manager, `remove` before the Room mirror (key or add; "mirror first" tests).
  A mutant no existing test kills is a gap in the old suite: add a focused test in a new file (`EngineTest` or the manager's test), show it failing under the mutant, and record the count change. Do not weaken or edit an existing assertion.
- Rule 19 failing-first evidence as in task 6. No device needed.

## Evidence to capture

Baseline and final init outputs; per-package file counts; grep outputs; the import-only diff check; mutant run logs (7); rule 19 failing and green runs; testcase counts.

## Validator checklist

- Public names, signatures and KDoc meaning unchanged (diff of moved public files is `package` line only).
- One `UNDISPATCHED` pair, in the engine; add's unknown-song path still skips `writes`.
- Mirror-before-remove, remove-mirrors-on-`song_not_in_setlist`, JamStatus `Outdated` dismissal, Move's pre-failed entry, Assign's `localError` all preserved (read the diffs of those managers).
- `DefaultSetlistRepository` pure delegation, `queue` and `failureLog` required; `DataModule` builds the queue once; `AppModuleBindingsTest` and `DataModuleTest` green.
- Rule 19 amended and shown failing first; 18 and 20 unchanged and reasoned; `assistant-reads-only` still bans every moved path (regex matches `setlist` as a prefix).
- No suppression, baseline, or edited existing assertion; independent mutant of the validator's own on `PendingEntries` goes red.
- Orchestrator persists status and PROGRESS entry; this spec does not.

## Risks and assumptions

- Assumption (not verified): moving test files to mirrored subpackages is allowed by existing conventions; if it conflicts with a rule, leave them at the root `setlist` test package with added imports.
- Risk: `by`-delegation of 12 interfaces plus the union may hit a Kotlin conflict if two contracts declare the same member name; each `dismissX` is already distinct (setlist-mutation-contracts), so none is expected.
- Risk: a generic holder can subtly change `StateFlow` equality emissions; keep `MutableStateFlow.update` semantics identical.
- If the import churn proves too risky for one session, split: engine + `DefaultSetlistRepository` first, subpackage move second (recommend this in `feature_list.json` rather than shrinking silently).
