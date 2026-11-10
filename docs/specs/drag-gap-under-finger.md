# Spec: drag-gap-under-finger

## Source feature

- id `drag-gap-under-finger`, area `feature-setlist`, status `not_started`, depends on `admin-drag-live-preview` (accepted).
- Follow-up found by the validator of `admin-drag-live-preview` on the Pixel 5: with rows of different heights the lifted row
  can be drawn up to about 3/4 of a row away from its gap.
- Target module: `:core:ui`, package `com.bbbjam.core.ui.reorder`. `:feature:next-jam` needs no change unless a test says so.
  No new module, dependency, mutation, backend or `:core:data` change (D-13: `moveSong` stays the only mutation).

## Goal and non-goals

Goal: the gap (the lifted row's vacated slot in the preview order) always sits directly under where the lifted row is drawn, for
any mix of row heights, and does not flap at a boundary.

Non-goals: auto-scroll, lift look, haptics, the preview/numbering code (`DragPreview.kt`), the hold-after-drop logic, the drop
position contract (`Drop(toPosition)`), musician screens, new Konsist rules.

## Cause (inspected)

`ReorderState.dropSlotState` builds `others` = visible, accepted items except the lifted one, with their current layout bounds
(the layout already contains the gap), then calls `ReorderMath.dropSlot(pointerY, others)`, where `pointerY` is the lifted
row's drawn **centre**. The rule is "before the first other row whose centre is below `pointerY`". That compares the lifted
centre with neighbour centres, ignoring the lifted row's own height and where the gap currently is. With a tall lifted row and short
neighbours (or the reverse) the gap slot flips when the centre crosses a neighbour's centre, not when the lifted row has moved
half a step away from the gap, so the two drift apart.

## Rule to implement (stateless, geometric)

Let `H` = lifted row height (`itemSize`), `c` = drawn centre (`pointerY`), `gapTop` = the lifted row's placed top in the current
layout (that is where the gap is, because the preview order already puts it there), `d = (c - H/2) - gapTop` = how far the drawn
row is from its gap. Neighbours of the gap, from `others`: below ones in order (`top >= gapTop + H`), above ones nearest first
(`bottom <= gapTop`).

- Moving down past the nearest below neighbour `N` moves the gap to `gapTop' = N.bottom - H` (N takes the gap's place, spacing
  between rows included). Shift `s = gapTop' - gapTop`. Swap when `d > s/2 + margin`. Repeat with the new `gapTop` while it holds,
  so one frame can cross several rows.
- Moving up past the nearest above neighbour `U` gives `gapTop' = U.top`, `s = gapTop' - gapTop` (negative). Swap when
  `d < s/2 - margin`. Repeat likewise.
- Result: `Before(first other below the final gap)`, else `After(last other)`; null with no others; no swap keeps the current slot
  (the lifted row's current neighbours).
- `margin` (hysteresis): a small dead band, default `0f` in the pure function's parameter with the caller passing a named
  constant (suggest 4 dp as px via `LocalDensity`, or a fraction such as 0.05 of the neighbour's extent; implementer's choice,
  documented). Reason: after a swap the displacement relative to the new gap is `d - s`, so the back-swap boundary equals the
  forward one; the margin separates them by `2 * margin`, so sub-pixel jitter cannot flap.
- Equivalent statement: swap when the drawn row's edge has overlapped the neighbour by more than half the neighbour's extent
  (the drawn row moved more than half a neighbour-step away from its gap). Both edges and the centre agree for equal heights, so
  existing uniform-height behaviour is unchanged.

`ReorderState` passes what the math needs: `pointerY`, `itemSize`, and the lifted row's current placed top. Read the placed top
from the dragged key's own entry in `listState.layoutInfo.visibleItemsInfo` when present (same snapshot as `others`, so no
one-frame mismatch with the `onPlaced` callback); fall back to the existing `placedTop` state, and, if the lifted row is not
visible (pinned far away), to the current centre rule so there is always a slot. This fallback is an assumption; confirm on
the device by auto-scrolling a long drag.

## Invariants to keep

- Drop = previewed slot: `finish` still reports `dropSlot` as last seen, never recomputed.
- `List<K>.withDragged`, `DropSlot`, `previewKeys`, `Songs.previewed` and `DragPreviewTest` (equivalence with `displayEntries`)
  are untouched and must pass unchanged.
- Derived state stays pure over snapshot state, no side effects; no new dependency.

## Acceptance scenarios

1. Given a tall lifted row between short rows, when its drawn centre moves down by half the first short neighbour's step, then
   the slot becomes after that neighbour and the gap's top is within half a neighbour extent of the drawn top.
2. Given a short lifted row among tall rows, when dragged up past half the tall neighbour's extent, the slot is before it, not
   earlier (no premature swap at the neighbour's centre).
3. Given the drawn row exactly on a boundary, jittering by less than `margin` either way never changes the slot.
4. Given uniform heights, slots equal the old rule's results for the existing `ReorderMathTest` cases.
5. Given a fast move across several mixed-height rows in one frame, the slot is the one whose gap is nearest the drawn row.
6. Releasing drops at the previewed slot (existing behaviour).

## Expected file changes

- `core/ui/src/main/kotlin/com/bbbjam/core/ui/reorder/ReorderMath.kt`: new pure `dropSlot` with `liftedSize`, `gapTop`, `margin`
  (keep the old centre rule as a private fallback or a separate function used when `gapTop` is unknown).
- `core/ui/src/main/kotlin/com/bbbjam/core/ui/reorder/ReorderState.kt`: feed the new inputs in `dropSlotState`.
- `core/ui/src/test/kotlin/com/bbbjam/core/ui/reorder/ReorderMathTest.kt`: update the old `dropSlot` cases to the new signature
  and add the mixed-height ones below.
- `ReorderModifier.kt`, `ReorderPreview.kt`, `DropSlot.kt`, `:feature:next-jam`: no change expected.

## Implementation tasks

1. Write the failing mixed-height tests first (tall among short, short among tall, boundary jitter, multi-row jump) against
   the current rule and record that at least two fail.
2. Implement the rule in `ReorderMath`; keep the function internal and pure.
3. Wire `ReorderState`; keep the fallback; keep `ItemBounds`.
4. Add a simulation test: a loop that, for each pointer position in a sweep (down then back up), builds the layout implied by
   the previous slot (using `withDragged`-style reordering of heights) and checks (a) `|drawn top - gap top| <= max(neighbour
   extent)/2 + margin` always, (b) the slot sequence over a monotone sweep never reverses, (c) sweeping back up crosses at
   boundaries differing by the margin (no flap).
5. Run `CI=true ./init.sh`.

## Verification plan

JVM (gate): `./gradlew :core:ui:testDebugUnitTest` for the reorder package, then `CI=true ./init.sh` exits 0 with `konsist`,
`detekt` and `ktlint` printed `wired`. `ReorderPreviewTest` and `DragPreviewTest` pass unmodified. No Konsist rule added.

Device (Pixel 5, cannot run on the JVM). Use only the debug demo live jam: `local.properties` with
`bluesjam.demoUpcomingJam=true` and `bluesjam.demoUpcomingJamLive=true`, admin via the real login. **Before any drag, confirm
the screen shows "Demo (solo debug)" as the venue; if it does not (a real jam is shown), stop and do not drag.** Never release a
drag on a real jam, and never ask for Sheet data. The demo has songs whose rows differ in height (open slots, long titles; use
the instrument filter or a jam with extras if needed).
1. Long-press a tall row and drag slowly down across short rows: the drawn row stays within about half a row of its gap the
   whole way; the gap never lags behind by 3/4 of a row.
2. Repeat with a short row dragged across tall rows, both directions.
3. Hold the row still at a boundary: no flicker; small finger tremor does not move the gap.
4. Drag to the edge and auto-scroll a few rows: the gap keeps following, no oscillation.
5. Release on the demo jam: the row ends where the gap was (the demo's own order change is harmless; the demo never writes to
   the Sheet). If the demo is not shown at any step, cancel by dragging back to the origin and releasing there.

## Evidence to capture

Failing-then-passing test names, the `init.sh` tail with three `wired`, and Pixel 5 notes for the five steps, in
`feature_list.json` evidence and `PROGRESS.md`.

## Durable documentation impact

- `.claude/skills/architecture/SKILL.md`: update the `admin-drag-live-preview` paragraph (the `dropSlot` rule) in one sentence.
- `AGENTS.md`, `DESIGN.md`, `CONSTRAINTS.md`, domain docs, backend docs: not needed (no behaviour contract, token or API change).

## Validator checklist

- The rule uses the lifted row's size and gap, not only neighbour centres; mixed-height tests exist and failed on the old rule.
- A margin or equivalent dead band exists and a jitter test covers it.
- Drop still reports the last seen slot; `DragPreviewTest` and `ReorderPreviewTest` unchanged and green.
- Fallback when the lifted row is not in `visibleItemsInfo` returns a slot.
- No diff outside `core/ui/.../reorder` (plus docs); no new dependency, mutation or backend change; `init.sh` shows 3 `wired`.
- Device notes show "Demo (solo debug)" confirmed before dragging and no release on a real jam.

## Assumptions and unknowns

- Assumed (not run): `visibleItemsInfo` offset of the lifted key equals its placed (gap) top while it is visible; if it is
  affected by the graphics-layer translation (it should not, layout offsets exclude `graphicsLayer`), use `placedTop`.
- Unknown: best margin value; choose by the simulation test and the device check.
- Inspected: `ReorderMath`, `ReorderState`, `ReorderModifier`, `ReorderPreview`, `DropSlot`, `ReorderMathTest`,
  `ReorderPreviewTest`, `feature/next-jam/.../DragPreviewHost.kt`, the `admin-drag-live-preview` spec. Not read in full:
  `DragPreview.kt`, `DragPreviewTest` (named as invariants only).
