# played-toggle-polish

Small follow-up of `song-played-state` (user request, 10 Nov 2026: "arreglá el ícono del switch de ya tocado").

## Problem

`IconRowActionIcon.Played` is `Icons.Filled.CheckCircle`, and `IconRowToggle` draws the same glyph on and off, changing
only the tint (`text` vs `textMuted`, sampled 226,226,232 vs 211,197,175). The state is hard to read. Also the KDoc of
`LineupPanelUiModel.muted` says "every line in `archive`", but `LineupPanelDefaults.detailColor` only switches the open
slot to `archive` (filled and extra lines stay `text`).

## Scope

Presentation only. No repository, mutation, Sheet or Apps Script change (the mutation `setSongPlayed` already exists, D-13).
Modules touched: `:core:ui` (the glyph and the toggle) and `:feature:next-jam` only if its call site needs a signature
change. No new Gradle dependency: `material-icons-core` already holds `Icons.Outlined.CheckCircle` (`Outlined.Info` is
already used in the project). Features still never import `androidx.compose.material.icons.*` (D-03).

## Design

1. `IconRowActionIcon`: keep `Played` as the **on** glyph (`Icons.Filled.CheckCircle`) and add the **off** glyph
   (`Icons.Outlined.CheckCircle`). Suggested shape: `enum class IconRowActionIcon(internal val imageVector: ImageVector,
   internal val uncheckedImageVector: ImageVector = imageVector)` with `Played(Filled.CheckCircle, Outlined.CheckCircle)`,
   so the other values are unchanged. Equivalent shapes are fine if the choice stays testable.
2. `IconRowToggle`: draw `icon.imageVector` when `checked`, `icon.uncheckedImageVector` otherwise. Extract the choice into
   an `internal` pure function (for example `IconRowToggleDefaults.glyph(icon, checked)`) so a JVM test can assert it.
3. Unchanged: content description (`Marcar como tocado` / `Desmarcar como tocado`), state description (`Tocado` /
   `Sin tocar`), `Role.Switch`, 48dp target, tints from `PlayedRowDefaults.toggle` (`text` / `textMuted`, tokens only
   D-17, never amber). The icon `contentDescription` stays null (the Box carries the semantics).
4. The song detail switch row and `AdminRowBar` both go through `PlayedToggle`/`IconRowToggle`, so they pick the fix up;
   verify there is no second place that draws the check (grep `IconRowActionIcon.Played`).
5. KDoc of `LineupPanelUiModel.muted`: replace "every line in `archive`" with wording that matches `detailColor`: no
   amber; the open-slot lines switch to `archive`, filled and extra lines keep `text`. Do not change behaviour.
6. `DESIGN.md`, section "Played song", Admin bar bullet: say the toggle is `CheckCircle` **filled when on, outlined when
   off**, with `text` / `textMuted` tints as before. Also check the "Song detail" bullet and any other mention of the
   switch, and the `song-played-state` note in `.claude/skills/architecture/SKILL.md` (~line 522) only if it describes
   the glyph.

## Verification

- JVM test in `:core:ui` (next to the existing action tests): for `Played`, the glyph for `checked = true` differs from
  `checked = false`; the on glyph is `Icons.Filled.CheckCircle` and the off one `Icons.Outlined.CheckCircle`; for the
  other icons both are the same. Demonstrate the failing case once (make the function return the same glyph, see it
  fail, restore).
- Existing tests that read tints (`PlayedRowDefaults`, ContrastTest) stay green; no amber added (the amber allowlist test
  is unchanged).
- `CI=true ./init.sh` exits 0 with `konsist`, `detekt` and `ktlint` all `wired`.
- Manual (optional, device; uses the debug demo jam, no Sheet data): admin login, open the demo setlist, confirm the
  row bar toggle is an outline circle with check when off and a filled one when on, both in the row bar and in the
  song detail. Not part of the gate.

## Assumptions

- Assumed `Icons.Outlined.CheckCircle` is in `material-icons-core` (as `Outlined.Info` is); if it is not, use
  `Icons.Outlined.RadioButtonUnchecked` only if it is also in core, otherwise stop and report rather than adding the
  extended icons dependency.
- Assumed no test currently asserts the glyph of `Played`; none was found by name, but the implementer should run the
  suite.

## Out of scope

Colour changes, new tokens, copy changes, any change to row muting or to the played mutation.
