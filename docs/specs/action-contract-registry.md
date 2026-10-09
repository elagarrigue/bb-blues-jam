# Feature Implementation Spec: Register the setlist action contract

## Source feature

- `id`: `action-contract-registry`; area: `app`.
- Dependencies: `admin-remove-song-from-setlist`, `admin-set-key`, `admin-adjust-lineup`, `admin-edit-extra-participants`, `admin-clear-slot`, `admin-reorder-songs`, and `admin-publish-setlist`; all are accepted. `admin-add-song-to-setlist` and `admin-assign-musician` are also accepted and are part of the mutation inventory.
- User-visible behavior: every admin mutation is reachable through one registry of repository functions, verified complete against the documented list.
- Decisions: D-13 (repository-callable mutations registered in `:app`, so phase 2 can use them without changing feature modules); D-18 (extra participants are mutations, separate from slots). D1 explicitly deferred choosing a deeplink scheme.

## Readiness and findings

There is no action registry in the repository yet. `:core:data` exposes all setlist writes through `SetlistRepository`, including inherited mutation interfaces. `DefaultSetlistRepository` implements them and routes writes through `AdminWriter` to Apps Script. `:app` already depends directly on `:core:data` and owns application wiring in `AppModule.kt` / `BluesJamApp.kt`. Features call repository contracts and must remain unchanged for this slice.

The source feature metadata says “all eight mutations,” but D-18 added `addExtraParticipant` and `removeExtraParticipant`. The accepted repository API and the domain list establish ten supported operations. Implement and test all ten, and update the stale feature verification wording when recording implementation evidence. Read-only flows such as `refresh`, `dismiss`, `observe*`, and admin login are not setlist mutations and are not entries.

## Goal and non-goals

Create one app-owned registry that makes every current setlist mutation callable from a single stable contract. The registry delegates directly to the existing public repository functions, preserving each function’s typed arguments, outcomes, validation and asynchronous behavior. No new UI or user-visible copy is needed.

Do not change feature modules, add a feature dependency, duplicate mutation logic, create a second persistence/network path, add a ViewModel, add an assistant/chat flow, or expose admin credentials. Do not implement a deeplink, intent filter, external JSON protocol, or URI parser: D1 deferred the scheme, and D-13’s repository-function option is the selected contract.

## Registry contract

Place a typed action model and registry in `:app`, for example `com.bbbjam.actions.SetlistActionRegistry`. Use a sealed input type with one variant per operation and a single suspend dispatch entry point. The registry constructor receives `SetlistRepository`; each variant delegates once to exactly the corresponding repository method and returns its existing outcome as a typed result. Keep repository interfaces and mutation behavior in `:core:data`; the registry is an app adapter, not a second use-case implementation.

Register exactly these ten operations and their existing argument types:

| Action | Repository call |
|---|---|
| Add song | `addSong(jamDate, songId, key)` |
| Remove song | `removeSong(jamDate, songId)` |
| Set key | `setKey(jamDate, songId, key)` |
| Adjust lineup | `setSlotCount(jamDate, songId, instrument, count)` |
| Assign musician | `assignSlot(jamDate, songId, instrument, ordinal, musicianName)` |
| Clear slot | `clearSlot(jamDate, songId, instrument, ordinal, expectedName)` |
| Add extra participant | `addExtraParticipant(jamDate, songId, name, instrument)` |
| Remove extra participant | `removeExtraParticipant(jamDate, songId, ordinal, expectedName, expectedInstrument)` |
| Reorder song | `moveSong(jamDate, songId, toPosition)` |
| Publish setlist | `publishSetlist(jamDate)` |

Names and arguments must use the existing domain values (`LocalDate`, `SongId`, `Key`, `Instrument`, `SlotPosition`, `MusicianName`) and repository parameter meaning. Preserve `ExtraParticipantOutcome`, `PublishOutcome`, and the other specific outcomes; do not collapse errors into a generic success/failure that loses reason or confirmed result. The registry does not authorize locally: every write still goes through `AdminWriter`, and Apps Script remains authoritative. Never include a passphrase in an action, result, `toString`, log or registry metadata.

Register the registry as a singleton in `appModule` using constructor injection. Keep it independent of navigation, Android `Context`, feature presenters and composables. There are no repository changes or Gradle dependencies expected.

## Acceptance scenarios

1. **Complete inventory:** the registry exposes exactly the ten operations in the table; no duplicate names and no missing D-18 extra-participant operations.
2. **Delegation:** dispatching each typed action calls its matching repository method once with arguments unchanged and returns that method’s typed outcome unchanged.
3. **Failures:** rejected, offline, unavailable and access-refused outcomes pass through unchanged; the registry never reports success before the repository does.
4. **No UI-only mutation:** every write operation used by the admin is represented by the app registry and has a public `:core:data` repository function. A completeness test fails when an operation is missing from the registry or its dispatch.
5. **No feature changes:** no feature module source or build file changes. Existing UI continues to call the same repository methods.
6. **Authorization remains server-side:** invoking from a local non-admin/debug session cannot bypass `AdminWriter` or the Apps Script passphrase guard; do not add a client-side flag as authorization.
7. **Wiring:** Koin resolves one `SetlistActionRegistry` backed by the same singleton `SetlistRepository` used by the app.
8. **Deeplinks:** no URI route or manifest filter is added; repository registration alone satisfies D-13 for this slice.

## Technical approach and expected files

- `app/src/main/java/com/bbbjam/actions/SetlistActionRegistry.kt` (or equivalent `actions/` package): sealed typed action inputs, typed result wrapper if needed, registry interface/class and exhaustive dispatch. Keep this in `:app`, which may depend on `:core:data`, `:core:model`, and `:core:ui` under current architecture.
- `app/src/main/java/com/bbbjam/di/AppModule.kt`: singleton binding with constructor injection.
- `app/src/test/java/com/bbbjam/actions/SetlistActionRegistryTest.kt`: use a fake/recording `SetlistRepository` to verify one-to-one argument and outcome delegation for all ten actions, duplicate-free inventory, and completeness.
- `konsist-test/src/test/kotlin/com/bbbjam/konsist/ModuleIsolationTest.kt` only if needed to add a focused static contract check. Prefer a test in `:app` that derives completeness from the typed action inventory and dispatch; avoid brittle source-text searches. Keep the existing rules and demonstrate a failing omission case if adding a Konsist rule.
- `app/src/test/java/com/bbbjam/ModuleWiringTest.kt` may be extended to check Koin binding if the registry’s module assembly is not already covered elsewhere.
- `feature_list.json`: update the stale “eight” verification criterion to ten and record actual evidence/status as implementation progresses. Do not alter feature dependency order or mark accepted without independent validation.

No UI impact: `DESIGN.md` does not need an update. Update `docs/domain-model.md` if needed so its complete mutation inventory explicitly lists both extra-participant operations alongside the other eight. Update `docs/risks-and-open-questions.md` to close the mutation-inventory question based on these ten operations; leave the deeplink scheme explicitly deferred. `AGENTS.md`, `docs/apps-script-api.md`, and backend code need no changes because the server endpoints and authorization are already implemented.

## Implementation and verification plan

1. Add the typed app-level action variants and exhaustive registry dispatch over the existing repository contract.
2. Wire a singleton through `appModule`; do not modify `BluesJamApp` unless its current module aggregation requires an explicit new module (expected: it does not).
3. Add focused JVM tests for all ten dispatches, identity of arguments/results, inventory completeness and Koin resolution. Include an omission check that fails if one mutation is not registered.
4. Run `CI=true ./init.sh`; it must exit successfully and report `konsist`, `detekt` and `ktlint` as wired. Run focused `:app:testDebugUnitTest` if the gate output does not make the registry tests’ execution clear. No device check is required because UI and navigation do not change.
5. Review the final diff to confirm there are no `feature/` changes and that repository contracts, Apps Script authorization, and stored credential behavior are untouched. Update `feature_list.json`, `PROGRESS.md`, and the domain/risk docs with evidence as appropriate.

## Validator checklist

- Exactly ten current setlist mutations are registered, including both `Otros` operations; the stale eight-operation feature wording is corrected.
- Every registry variant delegates to the matching repository function exactly once and preserves its typed outcome.
- Registry is app-owned and Koin-wired by constructor injection; no feature module imports `:app` or another feature.
- No mutation logic, transport, passphrase, or authorization check is duplicated in the registry; Apps Script remains write authority.
- No feature-module source/build changes, UI changes, deeplink scheme, or unrelated dependency changes.
- Focused JVM tests and `CI=true ./init.sh` actually ran; evidence is recorded and the status remains `passing` until independent validation.

## Assumptions and risks

The completed feature inventory and `SetlistRepository` interfaces resolve the older “confirm full mutation list” note: this registry includes ten operations, not eight. D-13 does not require a deeplink; existing navigation notes explicitly defer the scheme. No human decision blocks this implementation. This registry is typed Kotlin wiring, not the future assistant’s JSON schema or authorization policy; that protocol belongs to the later assistant slice and must consume this registry without changing feature modules.
