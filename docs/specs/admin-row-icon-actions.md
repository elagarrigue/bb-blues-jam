# Feature Implementation Spec: Icon actions in the admin row bar

## Source Feature

- `id`: admin-row-icon-actions
- `area`: next-jam
- `depends_on`: song-detail-replaces-row-expansion (accepted, HEAD abedd90)
- `status`: not_started at planning time
- `source`: `feature_list.json` (last entry), user request of 12 October 2026

## Goal

The admin's compact row bar on Próxima jam currently draws `Subir`, `Bajar` and `Quitar de la
lista` as three equal-width underlined text actions (`AdminRowBar` in `AdminControls.kt`). The
user asked to replace `Subir`/`Bajar` with up/down arrow icon buttons and `Quitar de la lista`
with a trash icon button, each keeping its accessible label, its disabled/edge behavior and the
existing inline removal confirmation. This also resolves the open follow-up from
`song-detail-replaces-row-expansion` that `Quitar de la lista`'s text wraps to two or three lines
at larger font scales inside the shared-width bar: an icon button has a fixed visual size
regardless of font scale.

**User decision overriding DESIGN.md (12 October 2026):** DESIGN.md (`admin-remove-song-from-setlist`,
line ~418) says "no dialog, no swipe, no undo, no trash icon (the export's red icon became a
labelled text action)". The user now explicitly wants a trash icon for this control. This spec
records that reversal; it is not reopening the decision, it is implementing the user's new
instruction. DESIGN.md must be updated to say so (see Durable Documentation Impact), not silently
contradicted.

## Non-Goals

- No change to the removal flow itself: still no dialog, no swipe, no undo. Tapping the trash icon
  still shows the same inline confirmation (`RemovalUiModel.Confirming`) in place of the bar, and
  `Quitando…` still replaces it while sending. No new repository function, Apps Script action or
  Room change (D-13): `Subir`/`Bajar` still call `SetlistRepository.moveSong`; `Quitar`/the trash
  icon still calls `SetlistRepository.removeSong`.
- No change to the song detail's controls (`Cambiar tonalidad`, `Anotar`, `Liberar`, extras,
  `Cambiar formación`) — those stay text actions, unaffected by this slice.
- No change to the confirmation step's own two actions (`Quitar`/`Cancelar` inside
  `RemovalUiModel.Confirming`): the spec only asks to change `Subir`/`Bajar`/the idle
  `Quitar de la lista` row. Confirm/Cancel stay as 48dp underlined text actions unless the
  implementer finds a design reason to also iconify them — if so, stop and ask, since the user's
  instruction named only the idle bar's three actions.
- No new Gradle dependency: `material-icons-core` is already an `implementation` dependency of
  `:core:ui` (declared for `Icons.Filled.Check` in the instrument strip). This slice adds a new
  shared `:core:ui` component that reads it; no feature module gains the dependency.
- No change to `AMBER_ROLE_ALLOWLIST` or any Konsist rule: the icons use `text`/`textMuted`/`error`
  tokens, never amber.
- No change to `MoveCopy`, `removalKey`, `RemovalHandler`, `MoveControlsState` logic, or the
  `SetlistMove`/`SetlistRemove` repository contracts — only how the actions are drawn and their
  accessible labels.

## Job Story

When I'm running the jam as admin and reordering or removing songs from the list, I want
`Subir`/`Bajar`/remove drawn as compact icon buttons instead of text that can wrap across lines at
a larger font size, so the row bar stays visually stable and reads at a glance as move/delete
controls.

## Users And Permissions

- Musician: unaffected. Rows have no admin bar (`SongRowUiModel.admin == null`).
- Admin (local flag, D-15): sees the same three actions, now as icon buttons. The local flag still
  only controls what is drawn; Apps Script still validates every write (D-11).

## Acceptance Scenarios

### Scenario 1: Arrows replace Subir/Bajar

Given an admin row that is not first or last in the setlist
When the row bar is drawn
Then it shows an up-arrow icon button and a down-arrow icon button, each at least 48dp, with
content descriptions "subir un lugar" and "bajar un lugar" (unchanged from today's click labels),
both enabled and tinted `text`.

### Scenario 2: Edge rows disable the matching arrow

Given the admin row is the first song in the setlist
When the row bar is drawn
Then the up-arrow button is disabled (muted `textMuted`, no click), still rendered (not hidden),
and reports disabled to accessibility services; the down arrow stays enabled unless it is also the
only song. The symmetric case holds for the last song and the down arrow.

### Scenario 3: Trash icon replaces "Quitar de la lista"

Given an admin row with the bar idle (`RemovalUiModel.Idle`)
When the row bar is drawn
Then it shows a trash icon button, at least 48dp, tinted `error`, with content description
"Quitar de la lista" (unchanged wording), positioned after the two arrows and visually separated
from them (not sharing their row group).

### Scenario 4: Trash icon still opens the existing inline confirmation

Given the bar's idle state with the trash icon
When the admin taps it
Then nothing is written; the bar is replaced by the same `RemovalUiModel.Confirming` block
(prompt, detail lines, `Quitar`/`Cancelar` text actions) that exists today — unchanged by this
slice.

### Scenario 5: Confirm still calls the same repository function

Given the admin taps `Quitar` in the confirmation
When the removal is sent
Then `SetlistRepository.removeSong(jamDate, songId)` is called exactly as before, the bar shows
"Quitando…", and the row disappears only when the server confirms (no optimistic hide) — unchanged
by this slice.

### Scenario 6: Arrow taps still call the same move function

Given the admin taps the up or down arrow
When the tap is enabled
Then `SetlistRepository.moveSong(jamDate, songId, targetPosition)` is called exactly as before
(same `MoveControlsState.onMove` logic, same optimistic `Guardando…` on the row).

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md` (tail), `feature_list.json` (`admin-row-icon-actions`,
  `song-detail-replaces-row-expansion` entries), `.claude/skills/architecture/SKILL.md`.
- `DESIGN.md`: "Song row, collapsed" (admin compact bar, lines ~214-223), "Removing a song" (lines
  ~408-422, the "no trash icon" sentence at line 418), "Reordering songs" (lines ~474-480),
  Accessibility Baseline (line ~642, "Content descriptions on every icon-only control"), tab bar
  icon rules (lines ~165-173, I1 precedent for `material-icons-core`).
- `feature/next-jam/src/main/kotlin/com/bbbjam/feature/nextjam/`: `MoveControls.kt`
  (`MoveAction` composable), `MoveControlsState.kt` (`rememberMoveControls`, `moveAction`,
  `moveModel`), `MoveCopy.kt`, `AdminControls.kt` (`AdminRowBar`, `RowTextAction`,
  `RemovalControl`), `AdminControlsDefaults.kt` (`move()`, `removal()` styles), `NextJamCopy.kt`
  (`MOVE_UP`, `MOVE_DOWN`, `REMOVE`, `CONFIRM_REMOVE`, `removePrompt`), `RemovalHandler.kt`,
  `NextJamUiModel.kt` (`SongRowAdminUiModel`, `MoveUiModel`, `MoveActionUiModel`,
  `RemovalUiModel`), `NextJamScreen.kt` (`SongRow`, call site of `AdminRowBar`).
- `core/ui/src/main/kotlin/com/bbbjam/core/ui/nav/BackButton.kt` — the existing precedent for a
  single icon-only button component in `:core:ui` (48dp via
  `LocalMinimumInteractiveComponentSize`, `Icon` with `contentDescription`, `text` tint, no amber).
- `core/ui/src/main/kotlin/com/bbbjam/core/ui/lineup/LineupLine.kt`,
  `core/ui/src/main/kotlin/com/bbbjam/core/ui/strip/InstrumentStrip.kt`,
  `core/ui/src/main/kotlin/com/bbbjam/core/ui/nav/TabBarDefaults.kt` — existing
  `material-icons-core` usage inside `:core:ui` (`Icons.Filled.Check`, `Icons.AutoMirrored.Filled.List`,
  `Icons.Filled.DateRange`, `Icons.Outlined.Info`).
- `core/ui/build.gradle.kts` — confirms `material-icons-core` is `implementation`, not `api`, in
  `:core:ui` only (comment: "Declared, not taken transitively from material3... A feature that
  needs an icon gets it from a `:core:ui` component, or declares the dependency itself").
- `konsist-test/src/test/kotlin/com/bbbjam/konsist/ModuleIsolationTest.kt` —
  `AMBER_ROLE_ALLOWLIST` (`feature/next-jam` → `key`, `primaryAction`, `onPrimaryAction`,
  `published`, `onPublished`; unaffected by this slice, since the icon colours are not amber).
- Verified directly: `Icons.Filled.KeyboardArrowUp`, `Icons.Filled.KeyboardArrowDown` and
  `Icons.Filled.Delete` all exist inside the resolved `material-icons-core-release-api.jar`
  (checked by listing its class entries); no extended-icons dependency is needed.
- `docs/specs/song-detail-replaces-row-expansion.md` — the accepted spec that created the current
  compact bar (P3) and recorded the "`Quitar de la lista` wraps" observation as a follow-up.

### Existing Patterns To Follow

- **Icon-only button lives in `:core:ui`, not the feature.** `BackButton` is the exact precedent:
  a `Box` sized with `LocalMinimumInteractiveComponentSize.current` (48dp, no literal), `clip`ped
  to a shape, `clickable(role = Role.Button)`, drawing one `Icon` with `contentDescription` and a
  token colour, never amber. A new shared `IconActionButton`-style component in `:core:ui` (e.g.
  `com.bbbjam.core.ui.nav` beside `BackButton`, or a new small package) follows this shape exactly,
  parameterized with `imageVector`, `contentDescription`, `tint`, `enabled`.
- **`material-icons-core` stays an `implementation` dependency of `:core:ui` only.** A feature
  draws an icon only by calling a `:core:ui` composable; it never imports
  `androidx.compose.material.icons.*` itself. This is already how `InstrumentStrip`, `LineupLine`,
  `TabBarDefaults` and `BackButton` work, and the architecture skill's "Where Each Piece Goes"
  explicitly says: "A component used by two features → `:core:ui`. Never copy it between
  features." Two call sites inside `:feature:next-jam` (the move arrows and the trash button)
  justify a shared `:core:ui` component rather than a feature-local one, and keep the dependency
  boundary the convention already states.
- **Disabled state keeps the control drawn, muted, with disabled semantics**, never hidden
  (`ui-polish-pass` U3: "disabled no fill/no outline... the state shows in shape, not brightness
  alone"). The existing `RowTextAction`/`MoveAction` disabled behavior (`style.disabled` =
  `textMuted`, no underline) is the model: the new icon button must have the same "still there,
  muted, `enabled = false`" behavior, not a conditional composable that omits it.
- **Content descriptions and click labels are unchanged wording.** `NextJamCopy.MOVE_UP` =
  "Subir", `MOVE_DOWN` = "Bajar" are currently both the *visible label* and the accessible name
  (Text node). Once the visible glyph is an icon, the same strings become the icon's
  `contentDescription` (and, per `MoveActionUiModel.clickLabel`, "subir un lugar"/"bajar un lugar"
  stay as today — see Current Gaps on which string to use where). `REMOVE` = "Quitar de la lista"
  becomes the trash icon's `contentDescription`.
- **Colours only from `AdminControlsDefaults`**, never a literal: `move()` (`action = text`,
  `disabled = textMuted`) and `removal()` (`action = error`) already have the right roles; the
  icon button reads the same fields, so no new color token or role is introduced.
- **Konsist's `no-dp-literal-outside-core-ui`**: the new `:core:ui` component may use
  `LocalMinimumInteractiveComponentSize.current` for sizing exactly as `BackButton` does; no `.dp`
  literal appears in the feature.

### Current Gaps

- **Two strings today, one survives.** `MoveAction`'s visible `Text` currently shows `model.label`
  ("Subir"/"Bajar") while `model.clickLabel` ("subir un lugar"/"bajar un lugar") is the
  `onClickLabel`; an icon-only button has room for only one accessible string. Resolved as I1
  below: `clickLabel` becomes the icon's `contentDescription`; `label` stays in the `UiModel`
  unused by the composable (kept for any copy/test that still names "Subir"/"Bajar" textually).
- **`contentDescription` on the `Icon`, not `onClickLabel` on `clickable`.** `BackButton` sets the
  description on the `Icon` itself; follow that, so `Role.Button` plus the icon's description is
  the only accessible string (not two). The resulting TalkBack phrasing is shorter than today's —
  expected, not a defect; call it out in device evidence.
- **Layout ("icons grouped, trash separated").** Today's bar divides evenly into three
  `weight(1f)` thirds; icons are small and fixed-size, so that leaves large empty padding per
  button. Resolved as L1 below.

## Technical Approach

### Planner decisions (labelled; implementer may adjust layout details, not behavior, without asking)

- **I1 — icon choice.** `Icons.Filled.KeyboardArrowUp` / `Icons.Filled.KeyboardArrowDown` for
  move, `Icons.Filled.Delete` for remove. All three verified present in
  `material-icons-core` (no extended-icons dependency). Content descriptions: up = "subir un
  lugar", down = "bajar un lugar" (today's `clickLabel`s, unchanged strings), trash =
  "Quitar de la lista" (today's `REMOVE` label, unchanged string).
- **L1 — layout.** One `Row` (`Modifier.fillMaxWidth()`), the two move icon buttons adjacent at
  the start, then a flexible gap, then the trash icon button at the end. No `weight(1f)` per
  action (unlike today's three equal thirds): icons are small and fixed-size, so evenly dividing
  the row's width would leave large empty padding inside each button. Implementer picks the exact
  `Arrangement`/`Spacer` combination; the only hard requirement is "arrows together, trash visibly
  separated," matching the task brief and keeping the confirmation/`Quitando…` replacement
  (`RemovalControl`) visually in the same place it is today (full width, left-aligned text).
- **I2 — new shared component.** Add `IconRowAction` (or similar name) to `:core:ui`, next to
  `BackButton` in `com.bbbjam.core.ui.nav` (same package is reasonable since it is the same shape
  of control; the implementer may instead create `com.bbbjam.core.ui.action` if `nav` reads as
  navigation-only — either is fine, pick one and keep `BackButton` and the new component
  consistent). Signature: `IconRowAction(imageVector: ImageVector, contentDescription: String,
  tint: Color, enabled: Boolean = true, mutedTint: Color, onClick: () -> Unit)` — matching
  `BackButton`'s shape (`Box` sized to `LocalMinimumInteractiveComponentSize.current`, `clickable`,
  `Icon`), but with an `enabled` branch: when `false`, `clickable(enabled = false, ...)` and
  `tint = mutedTint`, so the disabled state is drawn (not omitted) and reports disabled to
  accessibility, following `RowTextAction`'s existing disabled pattern. No color literal; the
  feature passes `AdminControlsDefaults.move().action`/`.disabled` and `.removal().action` as
  `tint`/`mutedTint`.
- **I3 — `MoveAction` and the removal's idle action are redrawn, not deleted at the model level.**
  `MoveActionUiModel(label, clickLabel, enabled, events)` keeps its shape (tests and
  `MoveControlsState.moveAction` reference `label`/`clickLabel`); only the composable `MoveAction`
  in `MoveControls.kt` changes to call `IconRowAction` instead of drawing a `Text`. Internally it
  should use `model.clickLabel` as the icon's `contentDescription` (per the I1 decision) and keep
  `model.label` unused by the composable (or drop `label` from the UiModel if the implementer
  confirms no test or failure-card copy still reads it — check `MoveCopy.failed` and any place that
  formats "Subir"/"Bajar" by name before removing the field; if any remaining consumer needs the
  textual name, keep `label` and simply stop drawing it).
- **I4 — `AdminRowBar`'s idle branch calls the new icon button for the trash action** instead of
  `RowTextAction(removal.label, ...)`. Note: `RemovalControl`'s own `Idle` arm (also
  `RowTextAction(model.label, ...)`) is called only from `AdminRowBar`'s `else` branch today, which
  already never reaches it for `Idle` (`AdminRowBar` special-cases `Idle` itself first). That arm
  is pre-existing dead code, not introduced by this slice — confirm with a grep for other
  `RemovalControl` callers before touching it, and only remove it if trivial; do not expand scope
  chasing it otherwise.
- **I5 — Confirming/Removing states untouched.** `RemovalControl`'s `Confirming` and `Removing`
  arms keep their current `RowTextAction` rendering (text, not icons) exactly as today; only the
  idle trigger becomes an icon. This matches the Non-Goals section and the task brief, which names
  only the idle bar's three actions.

### Visual size and the `animateContentSize` bar-height match

`AdminRowBar`'s `Row` currently has implicit height from its three `LocalMinimumInteractiveComponentSize`-tall
children with `bottom = spacing.xs`. Keep both icon buttons and the trash button at the same
`LocalMinimumInteractiveComponentSize.current` height with the same `spacing.xs` bottom inset, so
the row's height before/after this slice is unchanged and the confirmation's
`animateContentSize` transition is not affected.

## Expected File Changes

- `core/ui/src/main/kotlin/com/bbbjam/core/ui/nav/IconRowAction.kt` (or a new package, see I2) —
  create: the shared 48dp icon-button component, with a `@Preview` showing enabled and disabled.
- `core/ui/src/test/kotlin/com/bbbjam/core/ui/nav/` — create a small defaults/shape test if the
  component carries any logic worth a JVM test (e.g. a pure function choosing `tint` by
  `enabled`); if the component is purely compositional with no branchable logic beyond
  `enabled`, a Compose-free unit test may not be meaningful — covered instead by the feature's
  existing `MoveActionUiModel`/`RemovalUiModel` tests, which are unchanged by this slice (the
  model layer does not change, only the composable).
- `feature/next-jam/src/main/kotlin/com/bbbjam/feature/nextjam/MoveControls.kt` — modify:
  `MoveAction` composable now draws `IconRowAction` with `Icons.Filled.KeyboardArrowUp`/`Down`
  instead of `Text`.
- `feature/next-jam/src/main/kotlin/com/bbbjam/feature/nextjam/AdminControls.kt` — modify:
  `AdminRowBar`'s layout (L1) and its idle branch drawing the trash `IconRowAction` with
  `Icons.Filled.Delete` instead of `RowTextAction(removal.label, ...)`.
- `feature/next-jam/src/main/kotlin/com/bbbjam/feature/nextjam/AdminControlsDefaults.kt` — no
  structural change expected; `move()`/`removal()` already expose the right colour roles. Only
  touch if a new field is genuinely needed (e.g. a muted tint alias) — prefer reusing
  `move().disabled` for the muted icon tint everywhere, including the trash button's would-be
  disabled state (today the trash action has no disabled state — removal is always available while
  idle — so this likely needs no change).
- `feature/next-jam/src/test/kotlin/com/bbbjam/feature/nextjam/` — modify existing presenter/model
  tests only if they assert the removed `Text`-driven `label` field is drawn (they should already
  assert on the `UiModel`, not the composable, per the "no Compose UI test harness" rule — check
  `NextJamPresenterTest`/`MoveControlsStateTest`/model-shape tests for any string assertion on
  `"Subir"`/`"Bajar"`/`"Quitar de la lista"` that must still pass since those strings remain the
  content descriptions).
- `DESIGN.md` — modify: rewrite the "no trash icon" sentence (line ~418) and the admin compact bar
  description (lines ~214-223, ~474-480) to describe icon buttons with content descriptions,
  recording the user's 12 October 2026 decision explicitly.
- `bb-blues-jam-bitacora.md` — modify: one-line note under the relevant decision (D-07 area or a
  short dated note) recording the reversal of the "no trash icon" line, per the orchestrator's
  instruction.
- `feature_list.json`, `PROGRESS.md` — evidence and status updates (implementer's job, not this
  spec's).

## Visual Design Impact

- UI involved: yes.
- Design source: `DESIGN.md` ("Song row, collapsed" admin bar section, "Removing a song",
  "Reordering songs"), this spec's I1/L1 decisions (icon choice and layout), the existing
  `BackButton` precedent for the component shape.
- Screens or states affected: Próxima jam, admin view only (the compact row bar). Musician view is
  untouched (no admin bar).
- New design artifact required: no. Device screenshots at font scale 1.0 and 2.0 are the evidence
  (an icon button's size does not change with font scale the way text did, which is the point —
  screenshots at both scales prove the bar no longer wraps).

## Durable Documentation Impact

- `ARCHITECTURE.md` / `CONSTRAINTS.md`: not present in this repo; the architecture skill
  (`.claude/skills/architecture/SKILL.md`) plays that role. Update it: add an "As built by
  `admin-row-icon-actions`" paragraph noting the new shared `:core:ui` icon-button component and
  that `Subir`/`Bajar`/the idle removal action are now icon buttons, so a later reader does not
  rediscover this from the diff alone.
- `AGENTS.md`: not needed — no workflow, startup path or repo-wide rule changes.
- `DESIGN.md`: update (see Expected File Changes) — this is the one durable doc whose content this
  slice directly contradicts and must correct, per the orchestrator's explicit instruction to
  "record it, don't reopen it."
- `bb-blues-jam-bitacora.md`: update — a one-line note recording the user's 12 October 2026
  decision to reverse the "no trash icon" rule for this control, with reasoning (font-scale
  wrapping of the text action).
- Other docs: none. `bb-blues-jam-design-prompt.md` mentions `Subir`/`Bajar`/`Quitar de la lista`
  as action names, not as a visual description of text-vs-icon rendering; leave it unless the
  implementer finds a sentence that specifically says "text action" for these three — if so, a
  one-line amendment there too, dated, is in scope.

## Implementation Plan

1. Baseline gate (`CI=true ./init.sh`), record the current JVM test count and class count.
2. Add the shared `:core:ui` icon-button component (I2), modeled exactly on `BackButton`, with
   enabled/disabled branches and a `@Preview` for both states (Konsist: no new amber role, no `.dp`
   literal outside `:core:ui`, `material-icons-core` stays `implementation` in `:core:ui`).
3. Wire `MoveAction` (`MoveControls.kt`) to use the new component with
   `KeyboardArrowUp`/`KeyboardArrowDown`, keeping `MoveActionUiModel`'s existing fields and the
   `clickLabel` as the icon's `contentDescription` (I1).
4. Wire `AdminRowBar`'s idle branch (`AdminControls.kt`) to use the new component with `Delete` for
   `Quitar de la lista`, and update the bar's layout per L1 (arrows grouped, trash at the end).
5. Resolve the `RemovalControl.Idle` dead-arm question (I4) — remove it only if confirmed
   unreachable, otherwise leave a short comment explaining why it stays.
6. Update/verify tests: presenter and model-shape tests must still pass unmodified wherever they
   assert on `UiModel` fields (`label`, `clickLabel`, `events`), since those are unchanged; update
   only tests that assumed a `Text`-rendered composable (there should be none, per the "no Compose
   UI test harness" rule — everything testable is already in the `UiModel`).
7. Update `DESIGN.md` and the bitácora note (Durable Documentation Impact).
8. Device verification (Pixel 5, debug demo jam, debug admin) at font scale 1.0 and 2.0.
9. Record evidence in `feature_list.json`/`PROGRESS.md`, status `passing`.

## Implementation Tasks

- [ ] T0 Baseline gate; record JVM test/class counts.
- [ ] T1 Add `IconRowAction` (or chosen name) to `:core:ui`, modeled on `BackButton`; preview both
  enabled and disabled states; confirm it compiles with no new Konsist violation.
- [ ] T2 `MoveControls.kt`: `MoveAction` draws the icon button (up/down arrows); keep
  `MoveActionUiModel` unchanged; verify `moveModel`/`moveAction` callers still compile.
- [ ] T3 `AdminControls.kt`: `AdminRowBar`'s idle branch draws the icon button (trash) instead of
  `RowTextAction(removal.label, ...)`; apply the L1 layout (arrows grouped, trash separated,
  end-aligned or via a flexible spacer); confirm the `Confirming`/`Removing` arms are untouched.
- [ ] T4 Resolve the `RemovalControl.Idle` dead-arm question; remove or document, whichever is
  correct after checking all call sites.
- [ ] T5 Run the full gate; fix any test that assumed the old `Text` rendering at the `UiModel`
  level (should be none, since the model is unchanged — this task is a verification step, not
  expected to need a code change).
- [ ] T6 Update `DESIGN.md` (reverse the "no trash icon" line, describe the icon bar) and
  `bb-blues-jam-bitacora.md` (one dated note); update the architecture skill's "As built" trail.
- [ ] T7 Device verification: Pixel 5, debug demo jam (`bluesjam.demoUpcomingJamLive=true`),
  `bluesjam.debugAdmin=true`; screenshots at font scale 1.0 and 2.0; `uiautomator dump` confirming
  content descriptions and disabled state on the edge rows; confirm the trash icon still opens the
  inline confirmation and `Cancelar` returns to the bar; never tap `Quitar` (confirm), any move
  action, or any other write control for real.
- [ ] T8 Record evidence in `feature_list.json` and `PROGRESS.md`; status `passing`.

## Verification Plan

- `CI=true ./init.sh` exits 0 with `konsist`, `detekt` and `ktlint` all `wired` (unit tests,
  Android lint, the 17-rule Konsist suite, detekt, ktlint — the standard gate, no rule change).
- JVM tests: existing `MoveControlsStateTest`/presenter/model-shape tests (names to be confirmed by
  the implementer against current test files) must still pass with no change, since
  `MoveActionUiModel`/`RemovalUiModel`/`SongRowAdminUiModel` are unchanged; add or extend a test
  only if a new pure function is introduced by I2 (unlikely — the component is purely
  compositional). No persistent E2E harness exists in this repo (`docs/risks-and-open-questions.md`
  / architecture skill record "No Compose UI test harness" as a standing decision, T1 declined by
  the user on 3 October 2026); device screenshots and `uiautomator` dumps are the closest
  equivalent to E2E here and are required (see T7).
- `./init.sh` continues to run only the non-blocking standard gate; this slice does not change
  `init.sh` itself.
- Lint: `:feature:next-jam` and `:core:ui` must show no new issue.
- `git diff --check` clean (no trailing whitespace/mixed line endings introduced).
- Device (Pixel 5; adb at `"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"`). Before any check,
  back up `local.properties`, then set `bluesjam.demoUpcomingJamLive=true` and
  `bluesjam.debugAdmin=true` (debug-only flags; release hard `false`); confirm the logcat startup
  line shows the demo jam before checking anything. Never create or delete a real Sheet jam for
  this check (standing rule: device checks use the debug demo jam, never the real Sheet). Steps:
  1. Confirm the row bar shows two arrow icons and a trash icon, visually grouped as per L1, at
     font scale 1.0.
  2. `uiautomator dump` the first row (up arrow disabled, reports `enabled=false`) and the last row
     (down arrow disabled); confirm content descriptions read "subir un lugar", "bajar un lugar",
     "Quitar de la lista" exactly.
  3. Tap the trash icon on a middle row: confirm the inline confirmation appears (prompt, details,
     `Quitar`/`Cancelar` as text actions, unchanged); tap `Cancelar`: bar returns to the three
     icons. Do not tap `Quitar` (confirm), `Subir` or `Bajar` — those are real writes; verify their
     presence, enabled/disabled state and content description only via `uiautomator`.
  4. Set font scale to 2.0 (`adb shell settings put system font_scale 2.0`); re-screenshot the bar;
     confirm no wrapping and no overlap (an icon's fixed visual size is the point of this slice).
  5. Restore font scale to 1.0 and `local.properties` byte-for-byte (checksum before/after, as in
     prior slices); restore any other device setting touched (rotation, connectivity).
  6. Save screenshots at both font scales into `docs/evidence/admin-row-icon-actions/`.

## Evidence To Capture

- Gate output summary (`CI=true ./init.sh`, test/class counts before and after).
- Lint output for `:feature:next-jam` and `:core:ui`.
- `uiautomator dump` excerpts proving content descriptions and disabled state.
- Screenshots in `docs/evidence/admin-row-icon-actions/` at font scale 1.0 and 2.0 (admin bar,
  idle and confirming states).
- `local.properties` checksum before/after (unchanged).
- List of write controls not tapped (`Subir`, `Bajar`, `Quitar` confirm, any other admin write).

## Risks

- TalkBack phrasing changes from today's two-string announcement ("Subir, botón, subir un lugar")
  to a single-string one ("subir un lugar, botón"), because an icon button has no separate visible
  label. This is expected and is not a regression — it is a necessary consequence of replacing text
  with an icon — but it should be noted in evidence so a later reviewer does not mistake it for an
  unplanned behavior change.
- If the `RemovalControl.Idle` arm turns out to still be reachable from an untouched call site, I4
  must be revisited; the implementer should grep before assuming it is dead code.
- The DESIGN.md edit reverses an explicit, reasoned prior decision ("the export's red icon became
  a labelled text action"). The edit must record the new decision and its date, not just delete
  the old sentence, so the document stays an honest history.

## Needs A Human Decision

None identified. The user's 12 October 2026 instruction is itself the human decision that resolves
the DESIGN.md conflict; this spec implements it rather than reopening it. If the implementer finds
that `Confirming`'s `Quitar`/`Cancelar` should also become icons for visual consistency, that is a
new question outside this spec's scope (see Non-Goals) and should be raised, not decided silently.

## Amendment, 12 October 2026: removal confirmation as a dialog

User instruction, given while this slice was being implemented: "la confirmación «quitar» debería
ser un pop up con ok cancelar". This overrides the "inline confirmation (no dialog)" wording in this
spec and in DESIGN.md's removal section, and puts the Confirming step back in scope.

- **Trigger and state:** unchanged. The trash icon emits the same event; `RemovalUiModel.Confirming`
  (prompt, details, confirm/cancel handlers) keeps its shape. While a row is `Confirming`, the bar
  stays drawn and the screen shows a modal dialog built from that model instead of the inline
  block. No new presenter state; nothing is written until OK.
- **Dialog:** a Material 3 `AlertDialog` with every colour set explicitly from `BluesJamTheme`
  tokens (container `surfaceRaised`, title/body `text`, details `textMuted`; never amber, D-17),
  colours in `AdminControlsDefaults` (e.g. `removalDialog()`). Title: the existing prompt
  `¿Quitar «{título}» de la lista?`. Body: the existing details lines, only when they apply.
  Buttons: `OK` (confirm, `error`) and `Cancelar` (dismiss, `text`), each at least 48dp. Back, or a
  tap outside, equals `Cancelar`. `OK` calls the same confirm handler, which calls
  `SetlistRepository.removeSong` (D-13).
- **Copy:** add `NextJamCopy` constants for `OK` and reuse `Cancelar`; both are as the user wrote them.
- **Sending and failure:** unchanged. `Quitando…` replaces the bar inline in the row while sending;
  the failure card is unchanged. The dialog closes as soon as OK or Cancelar is tapped.
- **Tests:** presenter tests for confirm → `removeSong` call and cancel → no call keep passing; add a
  defaults test for the dialog colour roles (neutral or `error`, never amber).
- **Docs:** DESIGN.md's removal section records the dialog (dated, user decision), replacing "No
  dialog"; the bitácora note mentions both changes.
- **Device:** a screenshot of the open dialog at font scale 1.0 and 2.0; tap only `Cancelar` and the
  system back, never `OK`.

The Validator Checklist line "still opens the existing inline confirmation (no dialog…)" now reads:
the trash icon opens the confirmation dialog; `OK` calls `removeSong`, `Cancelar`/back/outside call
nothing.

## Validator Checklist

- [ ] `Subir`/`Bajar` render as up/down arrow icon buttons (`Icons.Filled.KeyboardArrowUp`/`Down`)
      and `Quitar de la lista` as a trash icon button (`Icons.Filled.Delete`), each at least 48dp.
- [ ] Each icon button's content description is unchanged wording ("subir un lugar", "bajar un
      lugar", "Quitar de la lista"); the first row's up and last row's down are disabled, drawn
      muted (not hidden), and report disabled to accessibility.
- [ ] The trash icon opens the confirmation dialog (see the 12 October amendment; no swipe, no undo)
      and `Confirm` still calls `SetlistRepository.removeSong`; arrows still call
      `SetlistRepository.moveSong` (D-13 — no new or bypassed repository function).
- [ ] The new icon-button component lives in `:core:ui` (not duplicated per feature);
      `material-icons-core` stays `implementation` in `:core:ui` only; no feature imports
      `androidx.compose.material.icons.*` directly.
- [ ] Colours come from `AdminControlsDefaults` tokens (`text`/`textMuted`/`error`), never amber;
      `AMBER_ROLE_ALLOWLIST` unchanged.
- [ ] Presenter/model tests pass with no change to `MoveActionUiModel`/`RemovalUiModel`/
      `SongRowAdminUiModel` shapes; `CI=true ./init.sh` exits 0 with `konsist`, `detekt`, `ktlint`
      all `wired`.
- [ ] `DESIGN.md`'s "no trash icon" line is corrected to record the user's decision, dated; a short
      bitácora note exists.
- [ ] Device evidence: Pixel 5 screenshots at font scale 1.0 and 2.0 with the debug demo jam and
      debug admin flag, `local.properties` restored, no write control tapped for real.
- [ ] `feature_list.json`/`PROGRESS.md` updated; status `passing`, not `accepted`.
