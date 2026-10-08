# Feature Implementation Spec: Clear a filled slot

## Source Feature

- `id`: `admin-clear-slot`
- `area`: `feature-next-jam`
- `depends_on`: `admin-assign-musician` (accepted)
- `status`: `not_started`
- `source`: `feature_list.json`

## Goal

In admin mode on Próxima jam, an admin can clear a filled musician slot from an expanded song row. The slot becomes open in the admin's lineup, strip, and active filter/count projections while the server write is pending; confirmation updates Room from the server, while rejection restores the prior musician and displays a dismissible failure. With admin mode off, filled lines remain read-only.

Expose this as a public `SetlistRepository.clearSlot(jamDate, songId, instrument, ordinal)` mutation in `:core:data`, callable without UI. Apps Script remains the authority for every write. Use existing `SlotPosition`, row/header resolution, mutation ordering, failure cards, and navigation/presentation patterns; do not introduce a ViewModel, module, or feature-to-feature dependency.

## Non-Goals

- Assigning or replacing a musician, changing the lineup, or clearing `Otros`.
- Editing past jams, offline queuing, adding a musician entity, or action-registry/deeplink work.
- Deploying Apps Script or performing live checks before app implementation is complete.

## Job Story

When a musician can no longer play a song, I want to clear their assigned slot so the jam group can see that it is open again.

## Users And Permissions

- Admin: may request clearing one filled slot from the upcoming editable jam; the server authenticates and validates the request.
- Musician: sees confirmed lineup only and has no clear action or optimistic mutation.

## Acceptance Scenarios

### Scenario 1: Clear succeeds

Given an expanded upcoming song has a filled slot and admin mode is on
When the admin activates that line's `Liberar` action
Then the admin's slot immediately appears open with `Quitando…`, the row's strip/filter/count projections agree, and after server confirmation Room reflects the returned slot fields and the pending indicator disappears.

### Scenario 2: Write fails or is stale

Given the admin starts clearing a filled slot
When the server rejects it, the connection fails, or the slot changed since the cached request was formed
Then Room remains unchanged, the pending open overlay disappears, and a dismissible failure card explains that the slot could not be cleared; a stale empty/different target is never overwritten or silently cleared.

### Scenario 3: No longer clearable

Given admin mode is off, the jam/song is no longer editable/available, the requested slot is already open, or a clear for the same slot is Sending
When the action is requested or the screen recomposes
Then no duplicate mutation is sent; unavailable targets show a clear `Volvé` path where applicable, and a Sending clear cannot be activated again.

### Scenario 4: Ordinal is stable

Given guitar slots are open-first in the expanded panel
When the admin clears a filled guitar line
Then the request uses its 1-based position in original lineup order, not its index in the filled/open presentation partition, and Apps Script resolves that ordinal among non-`-` guitar columns using U1.

### Scenario 5: Read-only musician

Given the same cached song is shown with admin mode off
When its expanded panel is drawn
Then no clear affordance or optimistic state is exposed.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md`, `feature_list.json` — project rules, current state, source metadata and deferred deployment policy.
- `CONTEXT.md`, `docs/build-brief.md`, `docs/domain-model.md` — domain language, MVP and slot semantics.
- `docs/sheet-schema.md`, `docs/apps-script-api.md` — canonical slot columns, U1 ordinal, guarded POST and local server contract.
- `DESIGN.md`, `bb-blues-jam-design-prompt.md` — expanded row, actions, visual tokens and screen rules.
- `.claude/skills/architecture/SKILL.md` — module boundaries, presenter and mutation contracts.
- `docs/specs/admin-assign-musician.md` — accepted prerequisite implementation/spec patterns.
- `core/model/.../SlotPosition.kt`, `MusicianName.kt` — validated 1-based ordinal and write-name value type already available.
- `core/ui/.../LineupPanelUiModel.kt`, `LineupPanelMapper.kt` — optional admin action metadata currently maps open slots only; filled slots lack slot identity/action.
- `core/data/.../SetlistRepository.kt`, `DefaultSetlistRepository.kt` — public assignment/lineup mutation APIs and shared ordering, ids, scope and write mutex.
- `feature/next-jam/.../NextJamPresenter.kt`, `AssignmentOverlay.kt`, `AssignMusicianPresenter.kt` — Sending-only projection, current failed-card mapping and admin state patterns.
- `backend/apps-script/src/Post.js` — current local `assignSlot`/`setSlotCount` actions, shared slot resolver and `checkSetlistWrite`; no `clearSlot` action exists yet.

### Existing Patterns And Current Gaps

- Slot identity is `(date, songId, instrument, SlotPosition)`; guitars resolve ordinal among the active non-`-` columns. Open-first rendering is a stable partition and must not define request identity.
- `:core:data` mutations publish Sending/Failed entries, share repository-wide call order and survive caller cancellation. Room changes only after a valid server success payload.
- Existing mutation composables use presenters for decisions, `BluesJamTheme` tokens, and admin-only UI metadata.
- Current local Apps Script deployment does not include the current `Post.js` clear action (not implemented); local assignment/lineup edits are also pending the user's deferred deployment/live-check phase.
- Verification available: `node --test backend/apps-script/test/*.test.js` and `CI=true ./init.sh`; no Compose UI test harness. A device run is optional and must use demo data only.

## Technical Approach

### Mutation contract and concurrency

Add `clearSlot(date, songId, instrument, ordinal, expectedName)` to the guarded Apps Script action surface. The client captures the current filled `MusicianName` as `expectedName`; the server validates date/song/instrument/ordinal and name text shape without requiring the old name to pass the stricter new-name rules, then validates unique editable jam, headers, unique row and active U1 slot. It clears only when the resolved cell is non-empty and exactly equals the expected cached cell text; otherwise return `slot_empty` or `slot_changed`. Both outcomes are write-free. This compare-before-clear contract prevents a stale admin cache from clearing a different musician after another admin changes the slot.

Success clears exactly one resolved slot cell to empty using the shared plain-text writer and answers with the canonical seven slot fields (same response validation shape as lineup adjustment), so the client mirrors the Sheet's confirmed state. Extend `checkSetlistWrite` to assign the marker musician, clear it through the public helper, read back the open slot, and clean the temporary tab in `finally`. Do not touch a real jam, `Catalogo`, or `Otros`.

In `:core:data`, compose the new `SetlistClear` mutation into the existing `DefaultSetlistRepository`, sharing its id counter, `order`, `writes`, and `DataScope`; add public `ClearSlotOutcome` and `SlotClear` Sending/Failed observable state. Validate cache target uniquely and require it to be filled before POST. After a well-formed successful server payload, transactionally replace the seven cached slots only for the uniquely cached song in an Available setlist, then remove the pending entry. Failures leave Room unchanged. Add a latest-Sending-per-slot `ClearSlotOverlay` that changes only the admin projection to open; do not expose it to musicians. Failed entries only drive dismissible cards.

Extend shared `LineupPanelUiModel` mapping with optional slot action metadata for filled lines, absent by default. Supply that action only from Próxima jam while admin is enabled and the slot is not already clearing. Show a 48dp-target `Liberar` control on each eligible filled line, retaining the musician name and existing open-first/filled order. The line event invokes the repository through the presenter; the composable only renders it. Keep the accessible description explicit (instrument, musician, clear action). Clearing is a single-action operation and needs no confirmation dialog.

## Expected File Changes

- `backend/apps-script/src/Post.js`, `backend/apps-script/test/setlist.test.js` (or focused slot tests), `backend/apps-script/README.md` — guarded `clearSlot`, compare-and-clear behavior, exact one-cell write, temporary self-check and API contract.
- `core/data/src/main/kotlin/com/bbbjam/core/data/setlist/SetlistRepository.kt`, `DefaultSetlistRepository.kt`, focused clear mutation/overlay files, `core/data/.../SetlistDao.kt` — public contract, shared ordering, cache transaction and clear state; focused JVM tests under `core/data/src/test`.
- `core/ui/src/main/kotlin/com/bbbjam/core/ui/lineup/LineupPanelUiModel.kt`, `LineupPanelMapper.kt`, `LineupPanel.kt` and tests — optional filled-slot action, read-only defaults, 48dp action rendering.
- `feature/next-jam/src/main/kotlin/com/bbbjam/feature/nextjam/NextJamPresenter.kt`, `NextJamUiModel.kt`, `NextJamScreen.kt`, `NextJamCopy.kt`, clear overlay/mutation helper and tests — admin callback, failure card, pending projection and action gating.
- `docs/apps-script-api.md`, `docs/sheet-schema.md`, `docs/domain-model.md`, `DESIGN.md`, `.claude/skills/architecture/SKILL.md` — endpoint, semantics and UI/architecture contract.
- `feature_list.json`, `PROGRESS.md` — implementation evidence/status only; do not edit during planning.

No new module or Gradle dependency. No navigation route is needed: clearing is an inline one-step action.

## Visual Design Impact

- UI involved: yes. Source: `DESIGN.md`, expanded song row and instrument strip rules.
- The expanded panel remains open slots, filled slots, then `Otros`. Each admin-actionable filled line adds a `Liberar` target of at least 48dp; the musician name remains visible. During Sending, the admin projection shows the slot as open with muted `Quitando…`; after failure the filled name returns. Existing musician rendering is unchanged.
- Use `BluesJamTheme.*` tokens only. Amber remains reserved for open-slot/key roles; `Liberar` uses the established admin/action treatment and does not recolor the filled name as an open slot before confirmation outside the admin-only pending projection. No new design artifact required.
- UI copy uses Rioplatense Spanish with vos: `Liberar`, `Quitando…`, failure title `No se pudo liberar el cupo`, and stale state `Ese cupo ya no está ocupado` with `Volvé a la próxima jam para ver la lista actual.`. Cards identify the song and may include the prior musician name; logs/evidence must not expose names.

## Durable Documentation

- `docs/apps-script-api.md`: update local POST action count/list, request, auth/lock, errors, success payload, and temporary check; state deployment/live verification is deferred.
- `docs/sheet-schema.md`: specify exact compare-and-clear semantics and that clearing leaves an empty open slot without changing the lineup.
- `docs/domain-model.md`: record the filled-to-open transition and stale-target outcomes.
- `DESIGN.md`: document filled-line clear affordance, minimum target, pending/revert states and copy.
- `.claude/skills/architecture/SKILL.md`: document public repository mutation/state and admin-only pending overlay.
- `docs/risks-and-open-questions.md`: update only if implementation discovers a material unresolved risk; no new open product decision is required by this plan.
- `AGENTS.md`, `CONTEXT.md`, user/access model: no change needed; existing rules cover this mutation and authorization.

## Implementation Tasks

1. Add server action and helpers with strict validation order, expected-name compare, `slot_empty`/`slot_changed` no-write errors, exactly one-cell clearing, and temporary-tab check steps; add Node tests first for success, reordered headers/U1, all rejection immutability, and cleanup.
2. Add `SetlistDao` transaction and public repository outcome/state; validate a unique cached filled target, share existing ids/order/write scope, require confirmed seven-field response, and test Done, malformed success, all failures, cancellation, ordering, and duplicate/missing cache targets.
3. Add the shared filled-line action metadata with absent-by-default behavior; verify original ordinal survives the open-first presentation partition and all existing callers remain read-only.
4. Add clear Sending overlay before admin strip/panel/filter/count derivation; expose `Liberar` in Próxima jam only for eligible filled lines, prevent repeated in-flight clears, show dismissible failure cards, and test admin/musician parity plus optimistic-revert behavior.
5. Update the durable docs above and record truthful local evidence. Do not change this feature's status in the planning task.
6. Run `node --test backend/apps-script/test/*.test.js` and `CI=true ./init.sh`; require all three tools wired. Deployment and live validation are deferred until app implementation is complete and must not be represented as run.

## Verification And Evidence

- Server tests prove only the uniquely resolved occupied cell is cleared, and rejected/stale requests leave every fake-sheet value unchanged; include U1 and reordered-header cases plus `checkSetlistWrite` cleanup even on an injected failure.
- Model/DAO/repository tests prove slot ordinal identity, unique filled target, a transaction updates only that song's seven slot values after server confirmation, no optimistic Room write, local/surface outcome mapping, shared write order, caller cancellation semantics and duplicate suppression.
- Presenter/panel/overlay tests prove `Liberar` is absent by default and for musicians, appears only for a filled actionable slot in admin mode, uses original lineup ordinal, only Sending clears overlay as open, and strip/filter/count agree; failure restores the name and yields one dismissible card.
- Run Node suite and standard gate; record actual counts, exit status and `konsist`/`detekt`/`ktlint` wired output. A local test does not establish deployment correctness.
- No live request or `local.properties` access is part of this feature implementation. Once app implementation is complete, coordinate deployment of the combined current `Post.js` and shared L1-L5 validation per the user's deferred instruction; until then report deployment/live status as deferred, not passed.
- Device verification is optional; if done, use demo data, restore original settings/config, and do not change accessibility settings.

## Risks And Unresolved Blockers

- Combined Apps Script changes for lineup adjustment, assignment, and clearing remain un-deployed until the user says the app implementation is complete; all three depend on one deployment and the shared live verification sequence.
- Compare-and-clear sends the cached musician text to guard against stale clears. Existing read parsing is permissive, so server validation must compare normalized display text without imposing `MusicianName` creation restrictions on legacy names.
- A refresh can arrive during a clear. The clear overlay must be scoped to the admin projection and latest Sending entry; completion must trust the validated server slot fields, while failure reveals the latest confirmed Room state on next refresh.
- No product ambiguity blocks planning. The design uses an immediate inline clear action without confirmation because the mutation can be reversed by assigning the slot again; report any changed product decision before implementation.

## Validator Checklist

- [ ] Clear is a public repository mutation; Apps Script auth, lock, date/jam/song/header/U1 checks precede its one-cell write.
- [ ] Stale or empty targets are write-free; server success is validated before one transaction updates the unique cached target.
- [ ] Pending clear affects admin-only projection; failure leaves Room unchanged and restores the musician line; musicians see no control/overlay.
- [ ] Identity uses `SlotPosition` in original instrument order despite presentation partitioning; no `Otros` mutation.
- [ ] 48dp action, established theme tokens, accessible action wording and Rioplatense copy are respected.
- [ ] Focused tests and standard gate ran; three tools are wired; evidence says deployment/live checks are deferred.
- [ ] Durable docs are updated; no module/dependency, navigation, or unrelated feature scope was added.
