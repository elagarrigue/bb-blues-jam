# Feature Implementation Spec: Keep the offline notice in view when it appears

## Source Feature

- `id`: offline-notice-visible
- `area`: feature-next-jam
- `depends_on`: `list-states` (`accepted`), `live-refresh-during-jam` (`accepted`)
- `status`: not_started at planning time
- `source`: `feature_list.json` entry and notes; found by the `live-refresh-during-jam` implementer
  and recommended by its validator (7 October 2026). PROGRESS.md session 078 records the exact
  observation: with the list scrolled to the top, a `StalenessNotice` item inserted above the header
  "starts just out of view" once it appears; "two short swipes under the pull threshold revealed
  it", with "no extra refresh line" (confirming nothing re-fetched — the list simply did not scroll).

## Goal

When a `StalenessNoticeUiModel` newly appears (offline, or any other refresh failure) while the
list is scrolled to its top, the list scrolls so the notice is the first visible item, instead of
being inserted above the visible area while the previously-topmost item (the header, the title, or
the empty block) stays exactly where it was. This applies to Próxima jam (`NextJamScreen`) and
Anteriores (`PastJamsScreen`), the two screens that share the `list-states` and
`live-refresh-during-jam` components.

## Non-Goals

- No change to *when* the notice appears or disappears (Decision 1 of `list-states`, Decision 4 of
  `live-refresh-during-jam`): still exactly when something is cached and the latest refresh failed;
  periodic refreshes stay quiet; age alone still draws nothing. This slice only fixes where the
  list is scrolled to when the notice's presence changes.
- No change to `StalenessNotice`, `StalenessNoticeUiModel`, `ListStateMapper`, or any copy.
- No auto-scroll while the user is reading further down the list: if the first visible item is not
  the header/title/empty block (index > 0, or index 0 with non-zero scroll offset beyond a resting
  top), nothing scrolls. A notice appearing while the user has scrolled away stays off-screen until
  they scroll up themselves, same as today — this slice only covers the case recorded in the
  finding, where the user was at the top.
- No change to pull-to-refresh, the live-refresh loop, or `RefreshableContent`/`PullToRefreshBox`.
- No new Gradle dependency, colour/type/spacing token, copy, or `init.sh` change. No new Konsist
  rule.
- No scroll-to-top when the notice *disappears* (a successful refresh removing it). The finding and
  the verification are about the notice *appearing* out of view; leaving the list where it is when
  the notice goes away does not hide anything (the list only gets shorter at the top, the current
  first visible item, if it is below the old notice position, stays visible or moves up into view —
  never out of view).
- `NextJamUiModel.NoUpcomingJam` and `PastJamsUiModel.Empty` also place the notice first (same
  pattern); this slice covers them the same way as the `Jam`/`Jams` branches, not as a special case.

## Job Story

When I'm at the jam with the admin polling every 30 s and the wifi drops, I want to actually see
the "Sin conexión" notice when it appears, without having to notice the list look clipped and swipe
up to find it, so I know right away that what I'm seeing might be stale.

## Users And Permissions

Anyone; read only. No mutation, no admin flag involved.

## Repository Research

**Inspected:**
- `feature_list.json` entry `offline-notice-visible` and its notes; `list-states` and
  `live-refresh-during-jam` entries and evidence (session 078's device finding, verbatim above).
- `docs/specs/list-states.md` (Decision 1 state table, Decision 7 layout, the notice item key and
  position), `docs/specs/live-refresh-during-jam.md` (Decision 4 quiet periodic refresh, Decision 5
  pull, the risks section — does not mention this finding; it surfaced only in the device check
  that followed).
- `DESIGN.md` "Required States" (notice placement: "above the cached data (above the header)"; no
  mention of scroll behaviour — this is a gap the finding exposed, not a documented rule being
  violated).
- `.claude/skills/architecture/SKILL.md` "Where Each Piece Goes" (list-states and pull-to-refresh
  entries), module layout table, Anti-Patterns, "Still Open" (bottom-navigation note: "a list whose
  UiModel branch changes while away starts at the top" — a related but distinct behaviour; both
  `LazyColumn`s use the plain default `rememberLazyListState()` today, confirmed by
  `docs/specs/bottom-navigation.md` line 91 and by reading both screens below).
- `feature/next-jam/src/main/kotlin/com/bbbjam/feature/nextjam/NextJamScreen.kt` (the `NextJamContent`
  `when` branches; `NoUpcomingJam` and `Jam` each build a `LazyColumn` with no explicit
  `LazyListState`, and conditionally emit `item(key = STALENESS_KEY) { StalenessNotice(notice) }`
  first), `NextJamPresenter.kt`, `NextJamRefreshes.kt` (`NextJamRefreshes.quiet`, the periodic-vs-user
  refresh distinction — irrelevant to where the list scrolls, only to whether `Actualizando…` shows).
- `feature/past-jams/src/main/kotlin/com/bbbjam/feature/pastjams/PastJamsScreen.kt` (`PastJamsList`,
  one `LazyColumn` for every branch, title item always first, then the branch's own items; `Empty`
  and `Jams` both place the staleness item right after the title, same pattern, same missing state).
- `core/ui/src/main/kotlin/com/bbbjam/core/ui/state/PullRefresh.kt` (`RefreshableContent` wraps the
  `LazyColumn`; it does not touch scroll position, only the pull gesture and indicator — confirmed no
  interaction with a fix inside the `LazyColumn`).
- `core/ui/src/main/kotlin/com/bbbjam/core/ui/state/ListStateBlocks.kt`,
  `core/ui/src/main/kotlin/com/bbbjam/core/ui/state/ListStateUiModel.kt` (`StalenessNoticeUiModel`
  shape: `title`, `detail`, `retryLabel`, `events`; no scroll-related field, none needed).
- `docs/risks-and-open-questions.md` (T1: "no Compose semantics test harness", declined by the user —
  governs what can be JVM-tested here; the bottom-navigation risk "Anteriores' scroll restoration was
  not observable on the device" — a different, already-known gap).
- Grep: no existing `LazyListState`, `rememberLazyListState`, `scrollToItem` or
  `animateScrollToItem` anywhere in `feature/next-jam`, `feature/past-jams` or `core/ui` — this slice
  introduces the first use.

**Not inspected:** a device; Android Studio preview rendering; whether Compose's `LazyColumn` would
have auto-kept the notice in view with a different key strategy (ruled out by design below: Compose
preserves scroll by item key/index, it does not infer "this is new content the user should see").

## Decisions

**1. Where the state lives: the screen, not the presenter.** Scroll position is UI state with no
domain meaning (D-02's reasoning for presenters extends here: a presenter returns a plain
`UiModel`, it does not hold a `LazyListState`, which is a Compose UI type). `NextJamContent` and
`PastJamsContent` each create one `LazyListState` with `rememberLazyListState()` (unkeyed, so it
survives recomposition and rotation like today's implicit default) and pass it to their
`LazyColumn(state = ...)`.

**2. Detecting "the notice newly appeared".** A pure, JVM-testable function,
`core/ui/.../state/NoticeVisibility.kt`:

```kotlin
/** Whether a staleness notice that just started being drawn should pull the list back to the top,
 * so it is not left above the visible area. True only when the notice is new (was absent, now
 * present) and the user was already at or near the top. */
internal fun shouldRevealNotice(
    staleness: StalenessNoticeUiModel?,
    previousStaleness: StalenessNoticeUiModel?,
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
): Boolean {
    val appeared = staleness != null && previousStaleness == null
    val atTop = firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset <= REST_THRESHOLD_PX
    return appeared && atTop
}

private const val REST_THRESHOLD_PX = 0
```

`REST_THRESHOLD_PX = 0` (exactly resting at the top) is the simplest correct choice and matches the
finding ("the list stays anchored on the header" — offset 0). A non-zero threshold would need a
dp-to-px conversion with no design input; kept at 0 unless device testing shows a visible gap (see
Risks). This function takes primitives, not `LazyListState`, so it is unit-testable with no Compose
UI harness (T1).

**3. Wiring it in each screen**, inside `NextJamContent`'s `Jam` and `NoUpcomingJam` branches and
inside `PastJamsContent`'s `PastJamsList` (`Empty` and `Jams` branches — the one `LazyColumn` there
serves every state, so the effect lives around the whole list, gated on which branch is active):

```kotlin
val listState = rememberLazyListState()
var previousStaleness by remember { mutableStateOf<StalenessNoticeUiModel?>(null) }
LaunchedEffect(staleness) {
    if (shouldRevealNotice(staleness, previousStaleness, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)) {
        listState.animateScrollToItem(0)
    }
    previousStaleness = staleness
}
```

`staleness` is the branch's nullable `StalenessNoticeUiModel` (`model.staleness` for `NextJamUiModel`,
likewise for `PastJamsUiModel`). `LaunchedEffect(staleness)` re-runs only when the notice's identity
changes (it is a `data class`, so a periodic refresh that leaves it unchanged — same title, detail,
retryLabel — does not restart the effect; a change from `null` to non-null, or between different
detail text such as the age advancing, does restart it, but `shouldRevealNotice` only returns true
on the `null` → non-null edge). `animateScrollToItem(0)` scrolls to the notice's item (always key
`"staleness"` at index 0 when present, confirmed by reading Decision 7 of `list-states` and both
screens' current code: the notice is always the first item emitted in every branch that can carry
one). Using `animateScrollToItem` (not `scrollToItem`) gives the user a visible cue that something
moved, rather than a silent jump — consistent with `RefreshableContent`'s existing indicator
animation; no new design token needed (it is a built-in Compose scroll animation, not a visual
style).

**4. Why not something simpler.** Two alternatives considered and rejected, so the implementer does
not re-litigate them:
- *Always keep the list scrolled to index 0 when a notice is present* (no "newly appeared" check):
  would re-snap the list to the top on every recomposition while the notice is showing, fighting a
  user who deliberately scrolled down to read a song while offline. Rejected.
- *Reorder the notice to float as an overlay instead of a list item*: a layout change touching
  `list-states`' and `live-refresh-during-jam`'s approved placement ("above the cached data"); out of
  scope (`DESIGN.md` is not being revised here) and a much larger change for the same symptom.
  Rejected.

**5. Scope confirmation.** `SkeletonList` (`Loading`) and `ListErrorBlock` (`Failed`) are not inside
a `LazyColumn` today (they sit in a `Box` with `verticalScroll`, per `list-states` Decision 7) and
never carry a notice (the notice only exists when something is cached, which rules out `Loading` and
`Failed`); nothing to change there. The empty-setlist case (`SetlistUiModel.Empty`, drawn as an item
inside the `Jam` branch's `LazyColumn`) is covered because it is still inside the same list whose
first item is the notice.

## Acceptance Scenarios

1. **Próxima jam, at the top, notice appears.** The list is scrolled to `firstVisibleItemIndex == 0`
   offset 0 (resting position, header/title visible). A refresh fails and `staleness` goes from
   `null` to non-null. The list scrolls so the notice is the first visible item (verified at the
   pure-function level: `shouldRevealNotice(nonNullNotice, null, 0, 0) == true`).
2. **Próxima jam, scrolled down, notice appears.** `firstVisibleItemIndex > 0` (or `== 0` with
   `firstVisibleItemScrollOffset > 0`). The same transition does not scroll
   (`shouldRevealNotice(...) == false`); the user's position is undisturbed.
3. **Notice already showing, detail text changes (e.g., age advances or a retry starts showing
   "Actualizando…").** `previousStaleness` is non-null, `staleness` is non-null (different content):
   `shouldRevealNotice` returns false (not an appearance), even though the two models are unequal.
4. **Notice disappears (a refresh succeeds).** `staleness` goes non-null → null: no scroll (not
   covered, matches the non-goal); whatever is currently visible stays visible.
5. **Anteriores, same two cases** (1 and 2) with `PastJamsUiModel.Jams`/`Empty.staleness`.
6. **First emission, notice already present** (e.g., a cold start with cached, stale data and a
   failed first read): `previousStaleness` starts `null` (the `remember` default) and `staleness` is
   non-null on the very first composition of the branch — `appeared` is true. If the list is at its
   resting top (true on first layout), this scrolls to index 0, which is a no-op position-wise (the
   list opens there already); harmless, and avoids a special case for "is this the first frame".
7. **Device**, airplane mode during the live window (as the original finding): with the list at its
   resting top, the notice becomes visible without the user swiping. Repeating the finding's own
   steps (toggle airplane mode on/off a few times while watching the top of the list) should show
   the notice every time it appears, with no manual scroll needed.

## Expected File Changes

- `core/ui/src/main/kotlin/com/bbbjam/core/ui/state/NoticeVisibility.kt` — create: `shouldRevealNotice`
  (Decision 2), internal, `com.bbbjam.core.ui.state` (the same package as `StalenessNoticeUiModel`,
  so it stays the one place both features read the rule from — the fifth thing that package exports
  after the four `list-states` components, no new Konsist allowlist entry needed since it reads no
  colour or amber role).
- `core/ui/src/test/kotlin/com/bbbjam/core/ui/state/NoticeVisibilityTest.kt` — create.
- `feature/next-jam/src/main/kotlin/com/bbbjam/feature/nextjam/NextJamScreen.kt` — modify:
  `NextJamContent`'s `NoUpcomingJam` and `Jam` branches gain the `LazyListState` and the
  `LaunchedEffect` of Decision 3.
- `feature/past-jams/src/main/kotlin/com/bbbjam/feature/pastjams/PastJamsScreen.kt` — modify:
  `PastJamsList` gains the same, scoped to `Empty` and `Jams`.
- No change expected to `NextJamUiModel.kt`, `NextJamPresenter.kt`, `NextJamRefreshes.kt`,
  `PastJamsUiModel.kt`, `PastJamsPresenter.kt`, `ListStateBlocks.kt`, `ListStateUiModel.kt`,
  `ListStateMapper.kt`, `PullRefresh.kt`, or any Copy/Defaults file.
- `konsist-test/.../ModuleIsolationTest.kt` — not expected (no new dependency, colour, dp literal or
  cross-module import).

## Visual Design Impact

None by DESIGN.md's own terms: the notice's look, copy and position in the list (first item, above
the header) are unchanged; this slice only changes which item the viewport shows when that first
item newly exists. No new token, no new component, no copy change. `DESIGN.md` "Required States"
gains one sentence recording the scroll rule (Durable Documentation Impact below) so a future reader
does not have to infer it from code.

## Durable Documentation Impact

- `DESIGN.md` "Required States" — **update**: one sentence after the existing notice paragraph,
  e.g. "If the notice appears while the list is resting at the top, the list scrolls so the notice
  is visible instead of being inserted above the visible area." No new token or component.
- `.claude/skills/architecture/SKILL.md` — **update**: a short addition to the `list-states` "Where
  Each Piece Goes" bullet (or its own short paragraph after the `live-refresh-during-jam` one)
  naming `shouldRevealNotice` in `com.bbbjam.core.ui.state` and the screen-owned `LazyListState` +
  `LaunchedEffect(staleness)` pattern, so a later screen that reuses `StalenessNotice` reuses this
  too instead of re-deriving it.
- `docs/risks-and-open-questions.md` — **update**: remove or resolve the `live-refresh-during-jam`
  entry's "out-of-view notice" risk line (session 078's addendum: "Finding outside this slice: a
  staleness notice that appears while the list is at its top is out of view..."); if the device step
  cannot fully confirm the fix (see Verification Plan), replace it with a narrower residual risk
  instead of deleting it outright.
- `PROGRESS.md`, `feature_list.json` — evidence, as usual.
- `AGENTS.md`, `CONTEXT.md`, `docs/domain-model.md` — not needed (no domain, naming or product-scope
  change).

## Implementation Plan

1. Baseline `CI=true ./init.sh` (record result files, test count, Konsist rule count — expect
   unchanged, 17/17 per the last recorded run).
2. `NoticeVisibility.kt` + `NoticeVisibilityTest.kt`: write the failing cases first (appeared at top →
   true; appeared scrolled away → false; unchanged content → false; disappeared → false; first
   emission at top → true), then the function.
3. `NextJamScreen.kt`: add the `LazyListState`, the `previousStaleness` remember, the
   `LaunchedEffect(staleness)` in both branches that can carry a notice. Keep the existing
   `LazyColumn` calls' other parameters unchanged.
4. `PastJamsScreen.kt`: the same, scoped to the one shared `LazyColumn` (gate the effect so it only
   fires for `Empty`/`Jams`, since `Loading`/`Failed` never carry a notice and the list there has no
   stable `staleness` value to key on outside those branches — simplest is to compute
   `val staleness = (model as? PastJamsUiModel.Empty)?.staleness ?: (model as? PastJamsUiModel.Jams)?.staleness`
   once, which is `null` for `Loading`/`Failed` and therefore never triggers an appearance).
5. `./gradlew ktlintFormat`; `CI=true ./init.sh`; failure demonstrations (Verification Plan); device
   check.
6. Docs (Durable Documentation Impact); final gate run.

## Verification Plan

JVM (inside `./gradlew check`):
- `NoticeVisibilityTest`: every scenario of the Acceptance Scenarios list, with real
  `StalenessNoticeUiModel` instances (not mocks) built the way `NextJamStatesTest` builds them —
  `appeared && atTop` → true; `appeared && !atTop` (index > 0) → false; `appeared && !atTop` (index 0,
  offset > 0) → false; `!appeared` (both non-null, different detail text) → false; `!appeared`
  (non-null → null) → false; `!appeared` (null → null) → false.
- Gate: `CI=true ./init.sh` exit 0; `konsist: wired` (17/17, unchanged), `detekt: wired`,
  `ktlint: wired`.
- **Not JVM-testable** (T1, no Compose semantics/UI test harness in this repo): that
  `LaunchedEffect(staleness)` actually calls `listState.animateScrollToItem(0)` at the right time
  inside a real composition, and that the `LazyColumn`'s visible viewport actually changes. The
  `shouldRevealNotice` function is the full extent of what can be asserted on the JVM; the wiring in
  `NextJamScreen.kt`/`PastJamsScreen.kt` must be read by a human (implementer and validator) against
  Decision 3's snippet, and confirmed on the device below.
- **Failure demonstrations** (edit, run, restore, sha1): (1) drop the `atTop` check from
  `shouldRevealNotice` (always scroll on appearance) → the "appeared scrolled away" test fails; (2)
  drop the `appeared` check (scroll whenever non-null) → the "unchanged content" and "first emission"
  distinctions collapse, at least one existing test fails; (3) flip the threshold comparison
  (`offset >= REST_THRESHOLD_PX`) → the offset-0 "at top" case starts failing.

**Device (Pixel 5, outside the gate)**, debug build, never enable TalkBack or any accessibility
service, never ask the user for Sheet data or the passphrase:
1. Install, open Próxima jam with a cached upcoming jam (real data or `bluesjam.demoUpcomingJamLive`
   as in `live-refresh-during-jam`'s own device steps), list resting at the top (header visible,
   nothing scrolled).
2. Airplane mode on; wait for a failed refresh (periodic tick, or pull once to force one). Screenshot
   immediately after the notice should appear: confirm `Sin conexión` is visible without any swipe
   (`uiautomator dump` should show it in the same dump that first shows the failure, not only after a
   manual scroll, which is the exact repro from session 078).
3. Airplane mode off; wait for recovery; confirm the notice disappears and the list does not jump.
4. Scroll down into the song rows, then toggle airplane mode on: confirm the list does **not** snap
   back to the top (Scenario 2) — the user's scroll position is respected.
5. Repeat steps 1-3 on Anteriores (pull to refresh, since it has no live window).
6. Restore airplane mode off; empty crash buffer; no setting left changed.

Record whether `animateScrollToItem`'s motion is visible or too fast to screenshot meaningfully;
either way the end state (notice visible, no extra swipe needed) is the pass/fail criterion, not the
animation itself.

## Evidence To Capture

Gate output (three `wired`, test counts, Konsist count unchanged); `NoticeVisibilityTest` count and
each demonstration's failure message with SHA-1 restore proof; device screenshots/uiautomator dumps
for steps 2 and 4 above (notice visible with no scroll; scrolled position undisturbed), with
timestamps; confirmation that airplane mode and every device setting were restored.

## Validator Checklist

- [ ] `shouldRevealNotice` lives in `:core:ui` `state/`, takes primitives (no `LazyListState`
      parameter), and is covered for every row of the Acceptance Scenarios list.
- [ ] The notice does not re-trigger a scroll on every recomposition while already showing (only on
      the null → non-null edge); a user scrolled away from the top is never pulled back.
- [ ] Both `NextJamScreen.kt` and `PastJamsScreen.kt` use the same function and the same
      `LaunchedEffect(staleness)` pattern; no divergent ad-hoc logic in either screen.
- [ ] No change to `list-states`' or `live-refresh-during-jam`'s approved notice appearance/copy/
      placement rules; no new dependency, token, or Konsist rule.
- [ ] Gate green with three `wired`, Konsist count unchanged; failure demonstrations recorded.
- [ ] Device steps 1-5 recorded, including the "scrolled away is undisturbed" check, not only the
      "at top becomes visible" check.
- [ ] `DESIGN.md` and the architecture skill updated as listed; the risks doc's "out of view" finding
      resolved or narrowed, not silently left stale.

## Risks

- `REST_THRESHOLD_PX = 0` (Decision 2) means a list scrolled even 1px away from the very top does not
  trigger the reveal. This matches the finding (which described the list as "anchored", i.e. at
  exactly 0) but is a narrow definition of "at the top"; if device testing shows a user can rest at a
  small nonzero offset (e.g., overscroll settle) and miss the reveal, widening the threshold is a
  one-constant follow-up, not a redesign.
- `animateScrollToItem` runs inside `LaunchedEffect(staleness)`; if a user is actively dragging the
  list at the exact moment the effect fires, Compose's gesture-vs-animation interaction is not
  something this spec tests (no UI test harness, T1) — a device check only, not provable on the JVM.
- The device verification for this slice is inherently about *timing* (catching the notice at the
  moment it appears), which is harder to screenshot deterministically than a static state; the
  implementer should prefer `uiautomator dump` polling (as session 078 did for the original finding)
  over relying on a single screenshot.

## User Approvals

None needed. This is a bug-fix-shaped slice with one reasonable design (scroll to top only on the
appear-while-at-top transition); no copy, token, dependency or behavioural trade-off requires a
decision beyond what Decision 2-4 already settle. If the device check in Scenario 7/step 2 shows the
0px threshold is too strict in practice, the recommended fix is widening the constant, not asking the
user — flagged as a risk above, not a blocking question.
