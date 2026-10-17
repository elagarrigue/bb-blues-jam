# Spec: remove-action-registry

Source: `feature_list.json` entry `remove-action-registry` (area `app`, status `not_started`, depends on
`action-contract-registry` and `assistant-proposal-targets`, both accepted). Decision: the organizer,
17 October 2026, option A.

## Goal

Delete the `:app` action registry (`SetlistActionRegistry`, `SetlistAction`, `SetlistMutation`,
`SetlistActionResult`, `RegistrySongAdder`, `RegistrySongRemover`, `RegistrySongMover`,
`RegistryJamCreator`) and bind the four narrow `:core:data` contracts (`SetlistSongAdder`,
`SetlistSongRemover`, `SetlistSongMover`, `JamCreator`) directly to the single `SetlistRepository`
instance in `AppModule.kt`.

Why (for the bitacora note): the LLM emits proposals, not `SetlistAction`s. `ProposalApplier` calls the
narrow contracts. The registry only wrapped and unwrapped the same call. `SetlistRepository` already is the
mutation inventory, so D-13's core ("every mutation is a repository function or a deeplink") still holds.

## Non-goals

- No behavior change anywhere. No change to `:feature:assistant`, `:feature:past-jams`, `:core:data`
  logic, Apps Script, or the narrow contracts and their KDoc semantics.
- Konsist rule 19 (`assistant-feature-applies-through-contract`, `ModuleIsolationTest.kt`) is unchanged;
  rule count unchanged.
- `SetlistMutationQueue` in `:core:data` is unrelated and stays (a grep for `SetlistMutation` matches it).
- Do not touch `presentations/` or the uncommitted top `PROGRESS.md` entry "harness presentation ready"
  (another session owns them). Do not commit.
- Do not rewrite history: old specs, `docs/evidence/**`, the "As built by" paragraphs and bitacora original
  decisions stay as written.

## Acceptance scenarios

1. Given `appModule` plus a fake `SetlistRepository`, when the four contracts are resolved, then each is
   usable and calling it invokes the matching repository function once with the exact arguments and
   returns the exact outcome object the fake returned (success and failure outcomes both unchanged).
2. Given the same setup, when the other narrow contracts are resolved, then they remain the same repository
   instance (existing assertion, kept).
3. Given the source tree, when searching main and test sources, then none of the eight deleted type names
   remain (other than `SetlistMutationQueue`, which is a different type).
4. Given the assistant, then its behavior (load, replace, reorder, create jam) is unchanged: its JVM tests
   pass untouched.

## Repository research (inspected)

- `app/src/main/java/com/bbbjam/di/AppModule.kt`: lines 32-36 bind `SetlistActionRegistry(get<SetlistRepository>())`
  and the four contracts to `Registry*` adapters; lines 40-42 bind three narrow contracts as
  `single<X> { get<SetlistRepository>() }` because `SetlistRepository` extends them.
- `SetlistRepository` extends `SetlistSlotClearRepository`, `SetlistMoveRepository`,
  `SetlistPublishRepository`, `SetlistJamCreationRepository`, `SetlistJamStatusRepository`,
  `SetlistJamEditRepository`, `SetlistExtraParticipantRepository`. It declares `addSong` and `removeSong`
  itself; `moveSong` comes from `SetlistMoveRepository`, `createJam` from `SetlistJamCreationRepository`.
  The four `fun interface`s have identical signatures to those functions:
  `addSong(LocalDate, SongId, Key): AddSongOutcome`, `removeSong(LocalDate, SongId): RemoveSongOutcome`,
  `moveSong(LocalDate, SongId, Int): MoveSongOutcome`, `createJam(LocalDate, LocalTime, String): CreateJamOutcome`
  (all `suspend`).
- `app/src/test/java/com/bbbjam/actions/`: `SetlistActionRegistryTest` (3 `@Test`), `RegistrySongAdderTest` (3),
  `RegistryJamCreatorTest` (3), `RegistrySongMoverTest` (2), `RegistrySongRemoverTest` (2) = 13 tests deleted.
  `SetlistActionRegistryTest.kt` also holds the test `app module binds the narrow status and publish contracts
  to the same SetlistRepository instance` and the helper `RecordingSetlistRepository` (a full fake that
  depends on `SetlistMutation`/`RecordedCall`). Both must survive in a new file without the deleted types.
- Other tests in `app/src/test`: `ModuleWiringTest`, `AppRoutesTest`, `AppTabTest`, `ExampleUnitTest` (not
  inspected beyond the grep; none matched the deleted names).
- Comment-only mentions of "the action registry" (no code dependency): `AppRoutes.kt:16`,
  `AssistantModule.kt:33,50` (in `:feature:assistant`), `PastJamsModule.kt:12`, `NewJamUiModel.kt:12`,
  `core/data/.../SetlistSongAdder.kt:11`, `SetlistSongRemover.kt:9`, `SetlistSongMover.kt:9`, `JamCreator.kt:9`.
- Not inspected: the other `app` test files' content, Konsist rule bodies other than the name of rule 19.

## Technical approach

Binding choice: SAM lambdas that delegate to the single repository, repository untouched.

```
single<SetlistSongAdder> { val r = get<SetlistRepository>(); SetlistSongAdder { d, s, k -> r.addSong(d, s, k) } }
```
and the same shape for `SetlistSongRemover`, `SetlistSongMover`, `JamCreator`.

Justification: the alternative (make `SetlistRepository` extend the four interfaces and bind
`single<X> { get<SetlistRepository>() }` like the other three) is also valid since signatures match, but it
edits `:core:data`'s public interface for no behavior gain and the task prefers an untouched repository.
Lambdas are a few lines, resolve `SetlistRepository` through constructor-style `get()` in the composition
root only (allowed, D-16 forbids it inside classes), and still delegate to the one instance. The
implementer should not use callable references (`r::addSong`) for the suspend SAM conversion unless it
compiles cleanly; a plain lambda is the safe form. If the implementer finds the lambdas noisy, switching to
the supertype approach is acceptable only with a one-line justification in PROGRESS.md and the same test.
Update the KDoc comment above `appModule` bindings accordingly; keep the comment about the Jams tab.

No `:feature:*` module changes functionally. Dependencies: `:app` already depends on `:core:data`; none added.

## Expected file changes

- Delete `app/src/main/java/com/bbbjam/actions/` (all files: registry, action, mutation, result, four
  adapters; confirm directory is empty afterwards) and the five test files above.
- Edit `app/src/main/java/com/bbbjam/di/AppModule.kt`: drop the five `com.bbbjam.actions.*` imports and the
  five bindings; add the four lambda bindings.
- New `app/src/test/java/com/bbbjam/di/AppModuleBindingsTest.kt` (name free) with:
  - a minimal fake `SetlistRepository` (move the fake out of the deleted file; it may be trimmed of
    `SetlistMutation`/`RecordedCall`, replacing recording with plain lists of calls per function and
    configurable outcomes; keep it `internal` to the test source set);
  - the surviving test "app module binds the narrow status and publish contracts to the same
    SetlistRepository instance" (unchanged assertions);
  - new test "narrow write contracts delegate to the same SetlistRepository with outcomes unchanged": build
    `koinApplication { allowOverride(true); modules(appModule, module { single<SetlistRepository> { fake } }) }`,
    resolve the four contracts, call each inside `runBlocking`, assert the fake saw exactly the arguments
    once and `assertSame` on the returned outcome (use a non-default outcome per function, including a failure
    variant for at least one, so a swallowing or remapping adapter fails). Close the application in `finally`.
- Comment-only edits (replace "action registry" with "the setlist repository"): `AppRoutes.kt`,
  `AssistantModule.kt` (comment lines only; no code change, rule 19 and tests unaffected),
  `PastJamsModule.kt`, `NewJamUiModel.kt`, and the four `:core:data` KDocs listed above.
  `AppRoutes.kt` text: D-13 is met by repository functions; an exported URI scheme still needs its own design.

## Documentation impact

- `bb-blues-jam-bitacora.md`: add under D-13 (after line 343, before D-14) a Spanish note in the style of the
  D-15 one: `*Nota, 17 de octubre de 2026:* ...` It amends the second sentence only: el asistente no registra
  las funciones en `:app`; los contratos angostos de `:core:data` (`SetlistSongAdder`, `SetlistSongRemover`,
  `SetlistSongMover`, `JamCreator`) se atan directo a `SetlistRepository`, porque el LLM emite propuestas y no
  acciones, y el registro solo envolvia y desenvolvia la misma llamada (`remove-action-registry`). El inventario
  de mutaciones es `SetlistRepository`; el nucleo de D-13 (toda mutacion es una funcion del repositorio o un
  deeplink) sigue vigente. Do not edit the original two sentences. Rioplatense, use accents properly.
  Other bitacora mentions of "contrato de acciones" (lines 143, 443, 453, 577, 623, 829, 1429) are history: leave.
- `.claude/skills/architecture/SKILL.md`: update current text. Line 24 (module table "the action registry (D-13)")
  and the "Where Each Piece Goes" mutation bullet (around line 531, "then registered in the `:app` action
  registry") to say: a mutation is a `SetlistRepository` function (and a narrow `fun interface` in `:core:data`
  when a feature other than its owner must apply it), bound in `:app` directly to the repository; there is no
  registry. Fix current-tense mentions at ~422, 433, 452-453, 460-462, 501, 1054, 1086 only where they present
  the registry as current (e.g. "dispatches through the registry"); "As built by" paragraphs (297-300, 1120,
  1151) stay and get a one-line "superseded by `remove-action-registry`" pointer at the first one. Check every
  grep hit of `[Rr]egistry|SetlistAction|SetlistMutation\b`; keep the "14 mutations" inventory statement only as history.
- `docs/architecture.md`: Mermaid nodes at lines 13, 56, 71-72, text at 88-90 and link at 111: remove the
  registry node, route "shared action contracts" to `SetlistRepository`; drop the link to the deleted file.
  `docs/diagrams/app-architecture.svg` line 22 labels `SetlistActionRegistry`: relabel or note; if the PNG
  cannot be regenerated, say so in PROGRESS.md and leave the PNG (the presentation session may use it).
- `docs/phase-2-brief.md` line 38: "the action contract (`SetlistActionRegistry`, D-13)" becomes the narrow
  contracts over `SetlistRepository`.
- `docs/domain-model.md` line 268-273: "The app-owned `SetlistActionRegistry` exposes exactly these eleven
  mutations" becomes `SetlistRepository` is the mutation inventory; keep the list; add the pointer.
- `docs/risks-and-open-questions.md` lines 24-28, 184-186, 442: resolved items are history; add "superseded by
  `remove-action-registry`" where they read as current (line 184 especially).
- `AGENTS.md`: line 42 mentions D-13 but not the registry; no edit needed unless a grep shows otherwise.
- `docs/entregable-harness-ia.md` line 384 states that applying a proposal goes through `SetlistActionRegistry`.
  It is a course deliverable describing history: add a short mention only if the line presents the registry as
  current (it reads as current); one sentence, "since `remove-action-registry` the contracts are bound directly
  to the repository". Otherwise leave it.
- `PROGRESS.md`: add a new entry below the existing top entries without editing the "harness presentation ready"
  entry. `feature_list.json`: set status per the flow (`passing` by the implementer), record evidence.
- Old specs and `docs/evidence/**`: untouched.

## Implementation tasks (ordered)

1. Baseline: `CI=true ./init.sh`; record exit code, wired tools and total testcase count (PROGRESS.md shows
   1135 at the last run; use the real number).
2. Red first: write the new test file with the fake and the delegation test while the registry still exists.
   Run `./gradlew :app:testDebugUnitTest`: the delegation test must pass for the registry adapters only if
   they delegate; to prove it can fail, temporarily bind one contract (e.g. `SetlistSongMover`) to a lambda
   that passes a wrong argument or returns a fixed outcome and show the test red, then restore. Save output
   under `docs/evidence/remove-action-registry/`.
3. Delete the registry files and the five old tests; edit `AppModule.kt` to the lambda bindings.
4. Comment edits, then docs edits as listed.
5. Final grep (pattern `SetlistActionRegistry|\bSetlistAction\b|\bSetlistMutation\b|SetlistActionResult|RegistrySong(Adder|Remover|Mover)|RegistryJamCreator`)
   over `app/src`, `feature`, `core`, `konsist-test`: no hits. `SetlistMutationQueue` must not match (word boundary).
6. `CI=true ./init.sh`.

## Verification plan

- `CI=true ./init.sh` exit 0; `konsist`, `detekt`, `ktlint` printed as `wired`.
- Testcase count before and after recorded; expected drop is 13 deleted tests minus the new ones added (the
  moved test counts as unchanged-in-spirit; state both numbers). No unexpected drop elsewhere.
- Konsist rule count unchanged (count the `testName =` entries in `konsist-test` before and after).
- Grep evidence from task 5 saved in the evidence directory.
- Red-first evidence from task 2.
- Device check: not required. The assistant's behavior is covered by its JVM tests (`ProposalApplier`,
  `JamCreationRunner` unchanged) and the binding by the Koin test. A short smoke test is optional and of low
  value; if the organizer wants one, the manual steps are: install debug, open the admin Assistant tab, apply
  one Agregar proposal and one Crear jam on the debug demo jam (no Sheet writes; never ask to add or delete
  Sheet jams). Not part of the gate.

## Validator checklist

- Eight types and five test files gone; no reference in main/test; `SetlistMutationQueue` intact.
- `AppModule.kt` binds the four contracts to the single repository; the new test fails when the delegation is
  broken (evidence) and asserts `assertSame` on outcomes.
- `:feature:assistant` diff is comments only (or empty); Konsist rule 19 unchanged; rule count equal.
- Bitacora note is a dated, Spanish, additive note under D-13; original text unchanged.
- Architecture skill, architecture.md, phase-2-brief, domain-model, risks docs no longer present the registry as
  current; history untouched.
- `presentations/` and the "harness presentation ready" entry untouched; no commit made by the implementer.
- `init.sh` all three tools wired; counts reported honestly.

## Unknowns and risks

- The SVG/PNG diagram may not be regenerable in this environment (assumption: only the SVG can be edited).
- Whether lambda SAM construction of a `suspend fun interface` compiles as written is expected (Kotlin 1.5+)
  but not compiled by the planner; the fallback is the supertype approach.
- Skill line numbers are from this session's grep and may shift; use the grep, not the numbers.
