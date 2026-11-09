# Spec: admin-drag-live-preview

## Source feature

- id `admin-drag-live-preview`, area `feature-setlist`, status `not_started`.
- depends_on `admin-drag-reorder`, `song-played-state` (both accepted).
- Origin: device feedback of 9 November 2026 on `admin-drag-reorder` (Pixel 5): "el drag and drop funciona bien, pero es
  difícil de colocar donde se quiere. Podemos agregar un espacio vacío donde quedaría luego el item? ... También sería
  bueno actualizar el número de orden del item en donde quedaría al hacer el drop."
- Decisions respected: D-02 (no ViewModel; per-frame drag state stays out of `UiModel`), D-03, D-04, D-13 (one
  `moveSong`, no new mutation, no backend change), D-17 (tokens only, no amber), D-12.

## Goal and non-goals

Goal: while an admin drags a row, the other rows animate apart so the dragged row's own slot is an empty gap at the
position where it would land; the dragged row and every shifted row show their would-be position numbers live; releasing
commits exactly one `moveSong(toPosition)` to that position. The row no longer flashes back to its old slot on release.

Non-goals: no Apps Script or `:core:data` change; no new mutation, repository function, Koin binding, module or Gradle
dependency; no change to the arrows (`Subir`/`Bajar`), to the long-press gesture, lift visuals or auto-scroll; no drag for
musicians; no dashed or tinted placeholder drawn in the gap (the vacated slot is simply empty background, see Decisions);
no change to the persistent row `position` (still used for detail navigation and arrow edges).

## Acceptance scenarios

1. Given an admin on Próxima jam with 5 songs, when they long-press song 2 and drag it down past the centre of song 3,
   then song 3 animates up into slot 2, the gap is at slot 3, the dragged row shows "03" and the row that moved shows "02".
2. Given the same drag, when the finger moves back above song 2's original place, then rows return and the dragged row
   shows "02"; releasing there calls nothing.
3. Given a drag to a new place, when the admin releases, then `moveSong(jamDate, songId, toPosition)` is called once with
   the previewed 1-based position, and the list never shows the old order between release and the Sending overlay.
4. Given an instrument or musician filter hiding some songs, when the admin drags a visible row between two visible rows,
   then numbers shown are full-setlist positions (hidden rows still count) and the drop target is the full-setlist
   position the preview showed.
5. Given played songs in the list, then they are ordinary rows for the preview (they can be reordered, keep their muted
   style, and renumber like any other); a played dragged row works too.
6. Given a move still Sending (overlay active), when the admin starts a new drag, then the preview starts from the
   overlaid order and a drop is computed against it.
7. Given a musician, no drag, no preview (unchanged: the row has no `move`).
8. Given the move fails server-side, then the existing failure card appears and the list reverts to the confirmed order
   (unchanged behaviour); the preview hold never outlives its timeout.

## Repository research (inspected)

- `core/ui/.../reorder/ReorderMath.kt`: `ItemBounds`, `hoveredKey` (nearest centre), `liftTranslation`, `edgeScrollDelta`.
- `ReorderState.kt`: `draggedKey`, private `pointerY`/`placedTop`, `finish(commit)` finds `hoveredKey` among visible
  `isTarget` rows and calls `onDrop(fromKey, toKey)` if different; `rememberReorderState(listState, isTarget, onDrop)`.
- `ReorderModifier.kt`: `reorderable(...)`, pinned dragged item, `graphicsLayer` lift translation from pointer and placed
  top (so the dragged row's layout slot is already vacated visually: the translation is what makes the gap).
- `feature/next-jam/.../NextJamListContent.kt` (`JamContent` builds the state; drop resolves the row's `admin.move.reorder`
  and fires `Event.Drop(toKey)`), `NextJamScreen.kt` (`setlistItems`: `items(rows, key = rowKey)` with
  `Modifier.animateItem()`; `TitleLine` draws `row.positionLabel`), `NextJamPresenter.kt` (`songsModel`: `shownEntries` =
  overlaid full order, `shown` = filtered rows, `rowKey` = song id or `"p$position"` for repeated ids, `positionLabel` from
  the displayed full position), `admin/MoveControlsState.kt` (`moveTo`, `onDrop` resolves `toRowKey` to an index in the
  displayed full order), `overlay/MoveOverlay.kt` (`displayEntries`: remove + insert at `toPosition - 1`).
- Not inspected: existing test files for `onDrop`/`Event.Drop` (implementer greps `toRowKey` and updates them).
- Assumption (verify on device): `LazyListLayoutInfo.visibleItemsInfo[].offset` reports the final layout offset, not the
  `animateItem` in-flight one. If it reports the animated one the count rule below is still stable; if the gap flaps,
  record it in evidence and compute against the settled order only.

## Technical approach

Target module: `:core:ui` (generic helper) and `:feature:next-jam` (mapping). Allowed deps unchanged (`:feature:next-jam`
depends on `:core:*` only). No Konsist change; amber allowlists unchanged.

### 1. Pure drop slot and preview order (`:core:ui`, package `com.bbbjam.core.ui.reorder`)

- `sealed interface DropSlot { data class Before(val key: Any); data class After(val key: Any) }`.
- `ReorderMath.dropSlot(pointerY: Float, others: List<ItemBounds>): DropSlot?`: `others` are the laid-out target rows
  without the dragged one. Slot is `Before(first row, by top, whose centre is below pointerY)`; if none, `After(last
  row)`; null when `others` is empty. Counting centres above the finger (not nearest centre) keeps the rule stable when
  the neighbour moves into the gap.
- `fun <K> List<K>.withDragged(dragged: K, slot: DropSlot?): List<K>`: removes `dragged`, inserts it before/after the slot
  key; returns the list unchanged when the slot is null, its key is absent or equals `dragged`. Public (feature uses it).
- `ReorderState` keeps `draggedKey`, adds `val dropSlot: DropSlot?` (null when not dragging), read as snapshot state
  (`derivedStateOf` over `pointerY`, `draggedKey`, `listState.layoutInfo`) so it updates with scroll and finger, and
  `onDrop` becomes `(fromKey: Any, slot: DropSlot?) -> Unit`, called from `finish(commit)` with the slot **the user last
  saw** (`dropSlot.value`, never recomputed at release). `hoveredKey` may be removed if unused (replace its test).

### 2. Preview in the feature (`:feature:next-jam`)

- `SetlistUiModel.Songs` gains `orderKeys: List<String>`: the `rowKey` of every entry of `shownEntries` (full displayed
  order, filtered rows included). Display data, not a handler, so `UiModel` rules hold. Default `emptyList()` keeps
  existing model-equality tests compiling.
- `ReorderActionUiModel.Event.Drop(toRowKey: String)` becomes `Drop(toPosition: Int)` (1-based, full setlist).
- New file `admin/DragPreview.kt`, pure and internal:
  `previewKeys(orderKeys, draggedKey, slot): List<String>` (= `withDragged`) and
  `Songs.previewed(previewKeys: List<String>?): Songs`: returns `this` when `previewKeys` is null or its key set differs
  from `orderKeys`; else reorders `rows` by `previewKeys` index and sets each row's `positionLabel =
  SongListText.positionLabel(indexInPreviewKeys + 1)`. Row `position`, `rowKey`, handlers, `admin` are untouched.
- `JamContent`: while `reorder.draggedKey != null`, `shown = songs.previewed(previewKeys(orderKeys, dragged,
  reorder.dropSlot))`; the same preview feeds `setlistItems` (rows keep `animateItem()`, so the shift is animated). The
  drop callback computes `toPosition = previewKeys(...).indexOf(from) + 1` and fires `Drop(toPosition)` only when it
  differs from the dragged row's index in `orderKeys` + 1.
- **Settling (the flash fix)**: on a drop that fires `Drop`, `JamContent` stores `held = previewKeys` and
  `heldFrom = orderKeys`. While `held != null` the list shows `songs.previewed(held)` instead of the committed order.
  A `LaunchedEffect(orderKeys, held)` clears `held` as soon as `orderKeys != heldFrom` (the Sending overlay, or a
  confirmed snapshot, reached the model), and a fallback `delay(HOLD_MS = 2000)` clears it regardless (failed or queued
  write; the existing failure card covers the failure). `previewed` ignores a held order whose key set no longer matches,
  so a concurrent add or remove cannot corrupt the list. Starting a new drag clears `held`.
  Known limit: rows of a setlist with repeated song ids are keyed `"p$position"`, which change on reorder; the hold then
  only clears by timeout. Accepted.
- `MoveControlsState.onDrop(date, songId, toPosition: Int)`: `songs = songsOf(date)`, `displayed = currentMoves
  .displayOrder(...)`; `moveTo(date, songId, toPosition, songs.size)` only when the song is found and `toPosition !=
  index + 1` (`moveTo` already bounds `1..size`). The row-key resolution code is deleted. `RowAdmin.moveModel` forwards
  `event.toPosition`. Drop must stay the only caller path besides `onMove`; `moveSong` still has no caller outside
  `MoveControlsState.kt` (D-13).
- Equivalence invariant (tested): applying `toPosition` through `displayEntries` (remove, insert at `toPosition - 1`)
  yields exactly the previewed key order, with and without hidden rows.

### 3. The gap and numbers

The dragged row's layout slot sits at the previewed index while its drawn position follows the finger (`graphicsLayer`
translation already derived from pointer and placed top), so the vacated slot is the gap; neighbours open it through
`animateItem()`. No new colours, copy or strings. Numbers use the existing `positionLabel` text style. Arrows stay as
today (their enabled flags use the row's real `position`; they cannot be tapped mid-drag).

## Expected file changes

- `core/ui/.../reorder/ReorderMath.kt` (dropSlot, `DropSlot`), `ReorderPreview.kt` (new, `withDragged`),
  `ReorderState.kt` (dropSlot, onDrop signature), `ReorderModifier.kt` (only if signature ripples).
- `core/ui/src/test/.../reorder/ReorderMathTest.kt` (replace `hoveredKey` cases), new `ReorderPreviewTest.kt`.
- `feature/next-jam/.../NextJamUiModel.kt` (`orderKeys`, `Drop(toPosition)`), `NextJamPresenter.kt` (fill `orderKeys`),
  `NextJamListContent.kt` (preview, held order), `admin/DragPreview.kt` (new), `admin/MoveControlsState.kt`.
- Tests: new `DragPreviewTest` (pure), updated move/drop presenter tests (grep `toRowKey`).
- Docs: `PROGRESS.md`, `feature_list.json` (status and evidence). `.claude/skills/architecture/SKILL.md`: append an "As built
  by `admin-drag-live-preview`" paragraph (DropSlot, `withDragged`, `orderKeys`, `Drop(toPosition)`, held order).
  `DESIGN.md`, `docs/apps-script-api.md`, `docs/domain-model.md`: not needed (no token, backend or domain change).

## Implementation tasks (in order)

1. Failing tests first: `ReorderMathTest` dropSlot cases; `ReorderPreviewTest`; then implement both pure parts.
2. `ReorderState.dropSlot` and the new `onDrop` signature; keep `./gradlew :core:ui:check` green.
3. Add `orderKeys` and `Drop(toPosition)`; update presenter and `MoveControlsState.onDrop`; update existing tests.
4. `DragPreview.kt` with tests, including the equivalence invariant against `displayEntries`.
5. Wire preview and held order in `JamContent`; confirm `animateItem` rows shift.
6. `CI=true ./init.sh`; device check; docs and evidence.

## Verification plan

JVM (standard gate):
- dropSlot: finger above all centres gives `Before(first)`; below all gives `After(last)`; between rows gives
  `Before(next)`; empty `others` gives null; stable after a swap (neighbour's centre moved by the dragged height keeps the
  same slot).
- `withDragged`: move down, move up, same place, null slot, unknown key, dragged equal to slot key.
- `previewed`: labels equal full index + 1; rows reordered; filtered subset keeps full numbers (hidden rows counted);
  played rows included; mismatched key set returns the input unchanged; null returns input.
- Equivalence with `displayEntries` for several (from, slot) pairs, including a Sending overlay in the base order.
- `onDrop(toPosition)`: calls `moveSong` once with that position; origin position calls nothing; unknown song nothing;
  rapid drops queue targets in call order (existing behaviour preserved).
- `CI=true ./init.sh` exits 0 with `konsist`, `detekt`, `ktlint` printed `wired`. No Konsist rule is added or changed.

Device (cannot run on the JVM; use the debug demo jam, never Sheet data; admin mode via the real login):
1. Long-press a middle song and drag slowly down: gap opens under the finger, rows slide, numbers renumber live.
2. Drag to the edge: auto-scroll reveals rows and the gap keeps following; no flicker or oscillation near row boundaries.
3. Release: no flash to the old slot; the row ends where the gap was; "Guardando…" then confirmed.
4. With an instrument filter on, drag between two visible rows: numbers are full-setlist numbers; the drop lands there.
5. Drag over and release at the origin: nothing is sent. TalkBack is not required for this slice.

## Evidence to capture

Test names and counts, the `init.sh` tail showing three `wired`, and the device notes (Pixel 5) for the five checks, in
`feature_list.json` evidence and `PROGRESS.md`.

## Validator checklist

- `moveSong` still only called from `MoveControlsState.kt`; one call per drop; no backend or `:core:data` diff.
- No per-frame state in a `UiModel`; `orderKeys` is plain data; no ViewModel; no `get()`/`inject()`.
- Preview, numbering and drop come from the same pure order (invariant test present and meaningful).
- Hidden rows counted in numbers under filters; played rows reorderable and still muted.
- Held order clears on model change and by timeout; mismatched key sets are ignored.
- No colour, dp or string literal added outside tokens and `strings.xml`; musicians unaffected.

## Open questions (defaults chosen, change only if the user objects)

- Gap look: the vacated slot is plain background. Default chosen; a faint outlined placeholder is a follow-up if the device
  check shows the gap is not visible enough.
- Hold timeout 2000 ms.
