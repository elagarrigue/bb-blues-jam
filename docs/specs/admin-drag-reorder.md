# Spec: admin-drag-reorder

## Source feature

- id: `admin-drag-reorder` (area `feature-setlist`, status `not_started`)
- depends_on: `admin-reorder-songs`, `admin-catalog-tab` (both inspected as built in the architecture skill; this spec assumes both are `accepted`, which the orchestrator must confirm in `feature_list.json`).
- Request: user, 9 November 2026. Long-press a row in admin mode, drag, drop: one `moveSong` through the existing mutation. Arrows stay.

## Goal and non-goals

Goal: an admin on Proxima jam can long-press a song row, drag it vertically and drop it at a new position. The drop commits exactly one `SetlistMoveRepository.moveSong(jamDate, songId, toPosition)` (1-based target), the same mutation the arrows call (D-13).

Non-goals:
- No new backend action, no Apps Script change, no new `:core:data` function, no Room change. `moveSong` already validates server-side and mirrors Room only on confirmation.
- No drag for musicians, for rows of a non-upcoming jam, or for a row that is `Removing`.
- No multi-row drag, no drag of pending/failure cards or of the add-song row.
- No change to the arrows, to removal, to key/lineup actions or to the song detail.
- No new third-party dependency (see Decision 1).

## Story

As the admin, when I notice the setlist order is wrong by several places, I want to drag a song to where it belongs instead of tapping an arrow many times, so I can fix the order quickly before the jam.

## Acceptance scenarios

1. Given admin mode and a setlist of 5 songs, when I long-press row 4 and drag it above row 2 and release, then one `moveSong(date, songId(row 4), 2)` is called, and while it is Sending the list shows the dropped order (existing `displayOrder` overlay).
2. Given a drag, when I release on the row's original position, then nothing is called and no card appears.
3. Given a drag that is dropped and the server refuses (or offline), then the list returns to the cached order and the usual dismissible failure card appears (existing behaviour of `observeMoves`; no new code path).
4. Given a musician (no admin flag), then rows have no drag gesture and `SongRowUiModel.admin` is null, exactly as today.
5. Given admin mode, then `Subir`/`Bajar` still work as before, including rapid taps (queued targets in `MoveControlsState`).
6. Given an instrument filter is active and shows a subset of rows, when I drop a row over another visible row, then the target position is that row's position in the **full** setlist (positions in `SongRowUiModel.position` are full positions; arrows already count the full setlist).
7. Given a row whose removal is in progress (`RemovalUiModel.Removing`), then it cannot be dragged and `moveModel` already returns null for it.
8. Given a plain tap on a row (no long press), then the song detail opens as today; a long press that ends in a drag never opens the detail.
9. Given a drag near the top or bottom edge of the viewport, then the list auto-scrolls so rows beyond the viewport are reachable.

## Repository research

Inspected: `AGENTS.md`, `PROGRESS.md`-routed architecture skill, `feature_list.json` entry, `DESIGN.md` (grep only, lines ~456, 562, 819 mention drag as considered-and-deferred; the implementer must read the "Reordering songs" paragraph and add the drag rule there), and these sources:

- `feature/next-jam/.../admin/MoveControlsState.kt`: `rememberMoveControls` owns `onMove(date, songId, direction)`, `moveTargets`/`moveCounts` (queue of rapid taps), `observeMoves()`, and `RowAdmin.moveModel` building `MoveUiModel(up, down)`. `displayOrder(date, songs)` (in `overlay/`) already overlays Sending moves in call order.
- `feature/next-jam/.../NextJamUiModel.kt`: `MoveUiModel`, `MoveActionUiModel` (`Event.Move`).
- `feature/next-jam/.../NextJamListContent.kt`: `JamContent` LazyColumn, `moveAnchor` scroll anchoring on arrow moves, `setlistItems(...)`.
- `feature/next-jam/.../NextJamScreen.kt` (`items(setlist.rows, key = { it.rowKey })`, ~lines 177 and 253) and `admin/AdminControls.kt` (`AdminRowBar`, `MoveAction`).
- `feature/next-jam/build.gradle.kts`: has `androidx.compose.foundation`; no reorder library. `gradle/libs.versions.toml`: Compose BOM 2025.09.00 (Compose 1.9), no reorderable library.
- Tests present: `feature/next-jam/src/test/.../admin/NextJamMoveTest.kt`, `NextJamAdminTest.kt`.

Not inspected (assumption, verify): exact shape of `RowAdmin` and `setlistItems` parameters; whether `SongRowUiModel.position` is the full position under a filter (the arrows' "edges count the full setlist" comment says yes). Compose 1.9's `LazyColumn` has no built-in reorder, so some gesture code is required.

## Decisions (orchestrator: decision 1 needs your confirmation)

1. **No new dependency; small in-repo gesture helper.** Recommended. `sh.calvin.reorderable` (version to be pinned by the orchestrator at implementation time, I could not verify the current release; 3.x line targets Compose Multiplatform 1.7+) would work, but: it adds a library to a module graph that is pinned to Compose 1.9/BOM 2025.09.00 (its transitive Compose floor may move versions, which this repo treats as a dedicated upgrade slice); it interacts with the existing `moveAnchor` scroll logic and keyed rows in ways we would have to learn; and the need is one vertical single-item drag. The helper is about 120 lines over `detectDragGesturesAfterLongPress`, `LazyListState.layoutInfo` and `graphicsLayer`. If the user prefers the library, it is a catalog entry plus `implementation` in `:feature:next-jam` only, and a Konsist/build review; flag it back to the planner.
2. **The helper lives in `:core:ui`**, package `com.bbbjam.core.ui.reorder` (precedent: `RefreshableContent`, `IconRowAction`): it is generic, needs `dp`/`sp` literals that `no-dp-literal-outside-core-ui` forbids in features, and its pure math is JVM-testable there. It knows nothing of `Jam`, `SongId` or the repository.
3. **Gesture state is ephemeral UI state, the commit is a presenter event.** The dragged key, offset and hovered index live in the helper's state in composition (never in the UiModel; it changes every frame). The drop calls a presenter-built `EventHandler`; the presenter decides (D-02, "the presenter decides, the screen draws").
4. **Drop target is the hovered row's full position**, which the presenter resolves from its `displayOrder` list, so filtered views and Sending overlays stay consistent.
5. **Accessibility:** long-press drag is not usable with TalkBack; the arrows remain the accessible path and are not removed. No new custom accessibility action in this slice.
6. **Copy (D-12):** only if a hint is added; default is no new visible text. If the implementer adds a content-description/hint string, it goes in `feature/next-jam/src/main/res/values/strings.xml` as `next_jam_drag_*`, in vos ("Mantené presionado y arrastrá para mover el tema").

## Technical approach

**Target modules and dependencies.** `:core:ui` (new package `reorder`, no new dependency, uses existing `foundation` via `ui`/material3 api) and `:feature:next-jam` (depends on `:core:ui` and `:core:data` already; no new project dependency, no Gradle change). No new module, no Koin binding (D-03, D-16 untouched). Konsist: adding public names to `:feature:next-jam` is not needed (everything stays `internal`), so `FEATURE_PUBLIC_API_ALLOWLIST` is unchanged; amber allowlist unchanged (lift styling uses no amber; `surfaceRaised`/`text` via `BluesJamTheme.colors` or a `:core:ui` default).

**Mutation location (D-13).** No new mutation. The drop reuses `SetlistMoveRepository.moveSong` in `:core:data` `setlist.move`, called only from `rememberMoveControls` in the presenter layer, never from the composable.

**`:core:ui` `reorder`:**
- Pure `ReorderMath` (internal, JVM-tested): `hoveredIndex(draggedCenter, itemBounds)`, `edgeScrollDelta(pointerY, viewportTop, viewportBottom, thresholds)`, and `clampTo(range)`.
- `rememberReorderState(listState, isEnabled, onDrop: (fromKey: Any, toKey: Any) -> Unit)` returning a state with `draggedKey`, `dragOffsetY`; and `Modifier.reorderable(state, key)` that adds `pointerInput(key, enabled) { detectDragGesturesAfterLongPress(...) }`, applies `graphicsLayer { translationY; scale; shadowElevation }` and `zIndex` to the dragged item, and performs `LongPress` haptic on start. It must not consume taps, so the row's existing `Role.Button` click keeps working (scenario 8). While dragging it auto-scrolls the `LazyListState` with `scrollBy` in a coroutine tied to the gesture; cancel/abort restores with no call.
- Only rows are registered as drop targets; rows are identified by their LazyColumn `key` (`rowKey`). Neighbouring rows are not animated in this slice beyond the dragged row following the finger (insertion preview via `animateItem()` is optional if cheap; not required).

**`:feature:next-jam`:**
- `NextJamUiModel.kt`: add to `MoveUiModel` a third member `reorder: ReorderActionUiModel(events: EventHandler<Event>)`, `Event.Drop(toRowKey: String)`. It is non-null exactly where `moveModel` is non-null (so null while Removing, null for musicians since `admin` is null).
- `MoveControlsState.kt`: generalize the internal move to an absolute target. Extract `moveTo(date, songId, target)` (the body currently after computing `target`, including `moveTargets`/`moveCounts` bookkeeping) and keep `onMove(date, songId, direction)` computing its target and delegating, so arrows are unchanged. Add the drop handler in `RowAdmin.moveModel`: resolve `toRowKey` against the displayed order (`currentMoves.displayOrder(date, songs)` with the `rowKey` rule: song id, position fallback for repeated ids); if the dragged song's displayed position equals the target position, or either is not found, or target not in `1..songs.size`, do nothing; else call `moveTo`. Handler key: `"${removalKey(date, songId)}|drop"`; it reads latest state through `rememberUpdatedState` like `onMove` (handlers compare equal by key, so no captured stale values).
- `NextJamListContent.kt` / `NextJamScreen.kt`: create the reorder state once per `JamContent`, pass `Modifier.reorderable(state, row.rowKey)` to each admin row only when `row.admin?.move?.reorder != null`; the state's `onDrop(fromKey, toKey)` forwards to the dragged row's `reorder.events(Event.Drop(toKey))`. The existing `moveAnchor` logic should be bypassed for drops (the dragged row ends where the finger left it); verify that the arrow anchoring still behaves and that a Sending overlay does not cause a scroll jump.
- No composable decides admin or enabled: it reads `reorder != null`.

## Expected file changes

- New: `core/ui/src/main/kotlin/com/bbbjam/core/ui/reorder/{ReorderState.kt, ReorderModifier.kt, ReorderMath.kt}` and `core/ui/src/test/kotlin/com/bbbjam/core/ui/reorder/ReorderMathTest.kt`.
- Edit: `feature/next-jam/.../NextJamUiModel.kt`, `admin/MoveControlsState.kt`, `NextJamListContent.kt`, `NextJamScreen.kt` (row modifier), `NextJamRemovalPreview.kt` (fixtures constructing `MoveUiModel`), and any test or preview that constructs `MoveUiModel`.
- Tests: extend `feature/next-jam/src/test/.../admin/NextJamMoveTest.kt`.
- Docs: see below.

## Visual design impact

Read `DESIGN.md` (reordering paragraph near line 562 and the open question near line 819) before coding. The lifted row uses `surfaceRaised`, a slight scale (about 1.02) and shadow, never amber (D-17); literals only inside `:core:ui`. Rows keep the 48dp-minimum header; the drag gesture is the whole row, so no handle is drawn and the arrows keep their 48dp targets (this resolves the "48dp targets versus dragging" question in DESIGN.md, which the implementer should record). Motion: the lift and follow are direct manipulation, not decorative animation (M1 holds); with animator scale 0 nothing extra animates.

## Durable documentation impact

- `DESIGN.md`: update (drag gesture, lift style, arrows remain, resolve the open question).
- `.claude/skills/architecture/SKILL.md`: update with an "As built by `admin-drag-reorder`" paragraph (the `reorder` package in `:core:ui`, the `ReorderActionUiModel` event, the `moveTo` generalization).
- `PROGRESS.md`, `feature_list.json` evidence: update.
- `docs/apps-script-api.md`, `docs/domain-model.md`, `docs/sheet-schema.md`: not needed (no backend or domain change).
- `ARCHITECTURE.md`/`CONSTRAINTS.md`: do not exist in this repo (not needed).
- Bitacora: not needed unless the orchestrator picks the library (then add a decision).

## Implementation plan and tasks

1. Write `ReorderMath` and its failing-first tests (hover index at boundaries, edge scroll sign and zero in the middle, clamping).
2. Generalize `MoveControlsState` to `moveTo`; run the existing `NextJamMoveTest` unchanged to prove arrows are untouched.
3. Add `ReorderActionUiModel` and the drop handler in `moveModel`; add Molecule tests (below).
4. Build the `:core:ui` state and modifier; wire it into `JamContent` and the row.
5. Update previews that construct `MoveUiModel`.
6. Docs updates; run the gate.

## Verification plan

JVM (gate): `CI=true ./init.sh` exits 0 with `konsist`, `detekt` and `ktlint` printed `wired`. No new Konsist rule is planned; if the implementer adds one, its failing case must be shown.

Molecule/presenter tests in `NextJamMoveTest` (fake `SetlistMoveRepository` recording calls):
- Drop of row 4 over row 2 calls `moveSong(date, id4, 2)` exactly once.
- Drop over the same row, an unknown key, or a row after the setlist was shortened: no call.
- Drop while an earlier move is Sending uses the displayed (overlaid) order for the position.
- Filtered view: drop target equals the hovered row's full position.
- Failure: fake returns `NotMoved`; the entry becomes `Failed` and the model order returns to the cached order with the failure card (assert at least one state transition, not only the first emission).
- `reorder` is null for musicians (`admin == null`) and for a `Removing` row; arrows (`up`/`down`) behave exactly as in the existing tests, including rapid queued taps.
- Grep check: no new `moveSong` caller outside `MoveControlsState.kt`; no repository access in `core/ui/reorder` (D-13).

Manual device steps (the drag gesture itself has no Compose UI harness here, per the "No Compose UI test harness" decision): with the debug demo jam and a real admin login, (a) long-press and drag a row down two places and release: row lands, "Guardando…" shows, then confirmed; (b) drag to the top/bottom edge with a 10-song list: auto-scroll works; (c) airplane mode drop: order reverts and the failure card shows; (d) a quick tap still opens the detail; (e) musician build or logged-out: long press does nothing; (f) arrows still work. Do not ask the user to add Sheet data (use the demo jam; server validation is already proven by `admin-reorder-songs`).

## Evidence to capture

Test names and results for the cases above, the `init.sh` tail showing three `wired`, the grep for `moveSong` callers, and a short device log of the manual steps (a to f).

## Validator checklist

- Drop calls `moveSong` once with a 1-based target; same-position drop calls nothing.
- No new mutation or repository function; `:core:data` and `backend/apps-script` diff is empty.
- Musicians get no gesture; drag only when `reorder != null`.
- Arrows unchanged and tests for them untouched.
- No new Gradle dependency and no feature-to-feature import; no `ViewModel`.
- No dp/sp literal or color literal in `:feature:next-jam`; no amber; copy (if any) in `strings.xml`, vos.
- `DESIGN.md` and the architecture skill updated; `init.sh` all `wired`.

## Open questions

1. Confirm Decision 1 (in-repo helper rather than `sh.calvin.reorderable`). Default: in-repo.
2. Is it acceptable that TalkBack users keep to the arrows (no accessibility drag action)? Default: yes.

## Orchestrator decisions (9 November 2026)

1. Decision 1 confirmed: in-repo helper in `:core:ui`, no new library.
2. Question 2 confirmed: TalkBack users keep the arrows; no accessibility drag action. Recorded in `DESIGN.md`.
