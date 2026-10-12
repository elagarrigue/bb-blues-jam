# Feature Implementation Spec: Add Otros Through A Dialog And Show Each Extra Like A Filled Slot

## Source Feature

- `id`: extras-dialog-and-lines
- `area`: next-jam
- `depends_on`: lineup-grow-any-instrument
- `status`: not_started
- `source`: `feature_list.json`

Note on the dependency: `lineup-grow-any-instrument` is planned in parallel by another planner and
changes slot column counts and the lineup editor's +/− limits. Nothing in this spec reads slot
counts, the lineup editor, or column layout; it only touches the extras ("Otros") add/remove UI and
the `EXTRA` line style. This spec is written to be correct regardless of how many slots a song ends
up with.

## Goal

Replace the admin's inline "Agregar a Otros" form (expanding at the end of the song detail's lineup
card) with a modal dialog, and make each extra participant render with the same line style as a
filled slot (fill, glyph, full-opacity text) instead of its current distinct muted/no-fill look.
`removeExtraParticipant` stays wired exactly as it is today; only how "add" is triggered and how
every line looks changes.

## Non-Goals

- Changing `SetlistRepository.addExtraParticipant` / `removeExtraParticipant` signatures, the
  Apps Script `addExtraParticipant`/`removeExtraParticipant` actions, or the Sheet's `Otros` column
  format (`Nombre (instrumento)`, `;`-separated). This is a UI-only rework.
- Changing D-18 (extras are never slots, never counted, never create room). The extra's *section*
  (`OTROS`, last) and its exclusion from filters/counts are unchanged; only its *line style* and the
  *entry point* for adding one change.
- Touching slot counts, the lineup editor (`Cambiar formación`), assign/clear, or set-key. Those are
  `lineup-grow-any-instrument`'s and earlier slices' territory.
- Adding a second way to remove an extra, or a confirmation dialog for removal (the existing
  one-tap `Quitar` stays as is; `admin-row-icon-actions`'s dialog pattern is reused only for the
  *add* dialog's shell, not required for removal).
- Any device check that writes to the real Sheet (D-13's validation is unchanged, but this spec's
  device plan never taps `Agregar` for real; see Verification Plan).

## Job Story

When the admin opens a song's detail and wants to note someone playing outside the fixed lineup
(a sit-in sax, a second percussionist), I want to tap one clear "Agregar a Otros" action that opens
a small focused dialog instead of scrolling to an inline form wedged into the lineup card, and I
want every extra I've already added to look like the musicians already confirmed for the song — not
like a lesser, greyed-out afterthought — so I can read the full lineup, slots and extras alike, at a
glance.

## Users And Permissions

- **Musician**: unaffected. Sees each extra as a lineup-panel line (now styled like a filled slot:
  fill, check glyph, full-opacity text) under `OTROS`, after `CUPOS CUBIERTOS`. No action, no dialog,
  no change in what is readable.
- **Admin**: sees the same lines plus a `Quitar` action per extra (unchanged content description
  `Quitar a {nombre}, {instrumento}`), and a single `Agregar a Otros` opener under the lineup panel
  that opens a modal dialog instead of expanding an inline form. The dialog's `Agregar` calls the
  same `SetlistRepository.addExtraParticipant` (D-13); `Cancelar`, the system back button, or a tap
  outside the dialog write nothing.

## Acceptance Scenarios

### Scenario 1: Opening and submitting the dialog

Given the admin is on a song's detail and the lineup card shows `Cambiar formación` and
`Agregar a Otros`
When the admin taps `Agregar a Otros`
Then a modal dialog opens over the detail with a title, a labelled `Nombre` field, a labelled
`Instrumento` field, and `Agregar`/`Cancelar` actions; `Agregar` is disabled until both fields,
trimmed, are non-blank
And typing a name and an instrument enables `Agregar`
And tapping `Agregar` closes the dialog, shows `Guardando…` under the title (as today), and on a
confirmed server answer the new extra appears as its own line in the `OTROS` section styled like a
filled slot (fill, check glyph, instrument, name)

### Scenario 2: Cancelling writes nothing

Given the dialog is open with some text typed in `Nombre` and/or `Instrumento`
When the admin taps `Cancelar`, presses the system back button, or taps outside the dialog
Then the dialog closes, no write is sent, and reopening `Agregar a Otros` shows the fields empty
again (the form state is discarded, not preserved)

### Scenario 3: A failed add reverts and still shows a dismissible card

Given the admin submitted the dialog and the server answer is a failure
Then the dialog has already closed (as in Scenario 1: it closes on submit, before the answer
arrives) and the optimistic line disappears; the detail's confirmed extras list is unchanged and a
dismissible failure card appears exactly as today's `addExtraParticipant` failure path

### Scenario 4: Each extra reads like a filled slot, not a different kind of line

Given a song has both filled slots and extras
When the admin or a musician opens its detail
Then every filled-slot line and every extra line share the same fill, glyph and full-opacity text
color; only the section heading (`CUPOS CUBIERTOS` vs `OTROS`) and the instrument text
(`Guitarra` vs the free-text `+ saxo` or equivalent) distinguish them
And the extra's screen-reader content description still identifies it as an extra (so a screen
reader user is not told a slot exists that is not in the lineup); the admin's `Quitar` action and
its description (`Quitar a {nombre}, {instrumento}`) are unchanged

## Repository Research

### Files Inspected

- `feature/next-jam/src/main/kotlin/com/bbbjam/feature/nextjam/ExtraParticipantControls.kt` — the
  current inline editor: `ExtraParticipantEditor` draws `Agregar a Otros` as a `RowTextAction` when
  closed, or two `OutlinedTextField`s (`Nombre`, `Instrumento`) plus `Agregar`/`Cancelar` when open,
  inset `spacing.md` inside the lineup card. This whole composable's "open form" branch is replaced
  by a dialog; the "closed" branch (the opener) stays, relabelled as a dialog trigger.
- `feature/next-jam/src/main/kotlin/com/bbbjam/feature/nextjam/ExtraParticipantFormState.kt` — the
  presenter-side form state machine: `Open`/`NameChanged`/`InstrumentChanged`/`Submit`/`Cancel`/
  `Remove` events, one saveable `formKey`/`name`/`instrument`/`consumed` set of states, `submit`
  guarded by `isSubmittable` and a `consumed` latch so a double tap adds once, `addExtraParticipant`
  launched `CoroutineStart.UNDISPATCHED`. This state machine's shape does not need to change: a
  dialog's `Cancelar`/back/outside can all call the existing `Cancel` event, and `Open`/`Submit`
  behave the same whether the fields are drawn inline or in a dialog. Only the composable changes.
- `feature/next-jam/src/main/kotlin/com/bbbjam/feature/nextjam/ExtraParticipantUiMapping.kt` —
  `SongAdminState.extraEditor(date, song)` builds `ExtraParticipantEditorUiModel` from the saveable
  form state and `overlays.isSavingExtras`; `lineupPanel(date, song, extras)` builds the panel via
  `Lineup.toLineupPanel(onRemoveExtra = …)`. Neither needs a shape change; `formVisible` becomes
  "dialog visible" (same boolean).
- `feature/next-jam/src/main/kotlin/com/bbbjam/feature/nextjam/SongAdminState.kt` — holds
  `extraFormKey`/`extraName`/`extraInstrument`/`onExtraEvent` and builds them via
  `rememberSongAdminState`. Unchanged; it has no opinion on how the form is drawn.
- `feature/next-jam/src/main/kotlin/com/bbbjam/feature/nextjam/SongDetailScreen.kt` — `songItems`
  draws, inside the lineup `Surface`: `LineupPanel(model.lineup, …)`, then
  `admin?.let { LineupEditor(it.lineup); ExtraParticipantEditor(it.extraParticipants) }`. This is
  where the inline form currently sits "at the end of the detail" per the user's complaint; the
  dialog must be hoisted out of this `Column` (a dialog is not a list item) and drawn as a sibling
  overlay, the same way `RemovalConfirmationDialog` is drawn as a sibling of `AdminRowBar`'s `Row`
  rather than inside it.
- `feature/next-jam/src/main/kotlin/com/bbbjam/feature/nextjam/AdminControls.kt` — the reference
  dialog pattern from `admin-row-icon-actions`: `RemovalConfirmationDialog` is a private
  `@Composable` taking only the UI model, rendered as `if (removal is RemovalUiModel.Confirming) { RemovalConfirmationDialog(removal) }` beside (not inside) the row's `Row`. It uses Material 3
  `AlertDialog` with `onDismissRequest` mapped to the same `Cancel` event used by the dismiss
  button — this is the exact shape to copy for the extras-add dialog (`onDismissRequest` covers back
  and outside-tap in one place). `AdminControlsDefaults.removalDialog()` is the sibling pattern for
  a `…DialogStyle` data class read only from `BluesJamTheme` tokens.
- `feature/next-jam/src/main/kotlin/com/bbbjam/feature/nextjam/AdminControlsDefaults.kt` — holds
  `RemovalDialogStyle`/`removalDialog()` (container `surfaceRaised`, title/body `text`, details
  `textMuted`, confirm `error`, cancel `text`) and `FieldStyle`/`extraField()` (container `surface`,
  text `text`, label `textMuted`, unfocused border `border`, focused border `text`, cursor `text`).
  The new extras-add dialog needs its own style function (its "confirm" role is not destructive, so
  it should not reuse `error`; see Technical Approach) built the same way, plus `extraField()`
  reused unchanged for the two text fields.
- `core/ui/src/main/kotlin/com/bbbjam/core/ui/lineup/LineupPanelMapper.kt` — `Slot.toLine` and
  `ExtraParticipant.toLine`: the function that assigns `InstrumentChipKind.OPEN_SLOT`/`FILLED_SLOT`
  to a slot and `InstrumentChipKind.EXTRA` to an extra. Showing an extra "like a filled slot" is a
  styling question, not a kind question (see Technical Approach for why `EXTRA` stays its own enum
  value); only `LineupPanelDefaults.style`/`detailColor` for `EXTRA` need to change, not this mapper.
- `core/ui/src/main/kotlin/com/bbbjam/core/ui/lineup/LineupPanelDefaults.kt` — the one function that
  decides a line's fill/glyph/text colour per `InstrumentChipKind`, delegating to
  `InstrumentStripDefaults.style`. This is the file to change so `EXTRA` reads fill/glyph/colour
  identically to `FILLED_SLOT`.
- `core/ui/src/main/kotlin/com/bbbjam/core/ui/strip/InstrumentStripDefaults.kt` — the underlying
  `style(kind)` table shared by the strip chips and the panel lines. `EXTRA` currently gets
  `fill = Color.Transparent, textColor = colors.textMuted, glyphColor = colors.textMuted,
  glyph = Glyph.NONE`; `FILLED_SLOT` gets `fill = colors.slotFilled, textColor = colors.textMuted,
  glyphColor = colors.textMuted, glyph = Glyph.CHECK`. **This file is shared by the instrument
  strip on Próxima jam's collapsed row**, which this spec does not touch (see Technical Approach for
  how to change the panel's look without changing the strip's).
- `core/ui/src/main/kotlin/com/bbbjam/core/ui/lineup/LineupLine.kt` — the `Line` composable that
  reads `LineupPanelDefaults.style(line.kind)` for fill/glyph/text colour and
  `LineupPanelDefaults.detailColor(line.kind)` for the detail text colour. No change needed if the
  defaults for `EXTRA` are changed to match `FILLED_SLOT`; `Line` already treats every kind
  uniformly.
- `core/ui/src/main/kotlin/com/bbbjam/core/ui/lineup/LineupPanelMapper.kt`'s `ExtraParticipant.toLine`
  — sets `instrument = LineupPanelCopy.extraInstrument(instrument)` (`"+ $instrument"`),
  `detail = name`, `contentDescription = InstrumentStripCopy.extraDescription(instrument, name)`
  (`"Otros: $instrument, $name"`). These stay: the user asked the extra to look like a filled slot
  ("mostrarlos igual que cupos cubiertos"), not to be indistinguishable from one. Keeping the `+`
  prefix and the `Otros:` description preserves D-18 (an extra must never read as an open slot or as
  part of the fixed lineup) while the *fill/glyph/colour* now matches a filled slot exactly.
- `core/ui/src/test/kotlin/com/bbbjam/core/ui/lineup/LineupPanelDefaultsTest.kt` — the test to update:
  `an extra line is never amber nor slotFilled and has no slot glyph` currently asserts
  `style.fill == Color.Transparent`, `style.glyph == Glyph.NONE`,
  `detailColor(EXTRA) == colors.textMuted`. These assertions are the ones this feature deliberately
  reverses for fill/glyph/detail colour (while keeping "never amber").
- `core/ui/src/test/kotlin/com/bbbjam/core/ui/lineup/LineupPanelMapperTest.kt` — builds expected
  `LineupLineUiModel`s with `InstrumentChipKind.EXTRA`; its assertions are about kind, order,
  content description and action wiring, not fill/glyph color, so it should keep passing unchanged
  once `EXTRA`'s *defaults* (not its *kind*) move — confirm this while implementing.
- `DESIGN.md` lines 241-244 (strip chip, "no fill… a leading `+`"), 253-258 (contrast numbers
  including "an extra's `textMuted` on the row surface 10.10:1"), 281-287 (lineup panel line rules,
  "an extra's name in `textMuted`"), 314 (`Agregar a Otros` "or its form"), lines 688-690
  (`### Extra participants` narrative section: "each extra in the panel's `OTROS` section is a line
  with a visible `Quitar` action… `Agregar a Otros` expands inline to two labelled outlined
  fields… `Agregar`/`Cancelar`"). All of these describe the *current* no-fill/muted extra style and
  the *current* inline form; every one needs a dated update (see Durable Documentation Impact).
  Note: DESIGN.md's strip-chip section (241-244, 253-258) describes the **collapsed-row strip**,
  which is a separate component (`InstrumentStrip`) from the **lineup panel** this spec changes; see
  Technical Approach for why the strip's extra chip is explicitly out of scope and DESIGN.md must
  say so to avoid an implementer over-reaching.
- `bb-blues-jam-bitacora.md` D-18 (lines 374-383, Spanish): "Esos participantes nunca son cupos: no
  están libres ni ocupados, y no entran en '¿dónde puedo tocar?'". This decision is about counting
  and filtering, not about visual style; this spec's line-style change does not amend D-18, but the
  bitácora needs a dated note recording the "looks like a filled slot" decision so a future reader
  does not assume D-18 itself changed.

### Existing Patterns To Follow

- **Dialog shell**: Material 3 `AlertDialog`, rendered as a sibling (not a descendant) of the content
  it floats over, gated by a boolean/nullable UI-model field, `onDismissRequest` wired to the same
  `Cancel` event the dismiss button uses (covers back and outside-tap in one line) — copy
  `RemovalConfirmationDialog`'s shape exactly.
- **Dialog styling**: a dedicated `…DialogStyle` data class in `AdminControlsDefaults`, every colour
  named from `BluesJamColors`, read with a `fun …Dialog(colors: BluesJamColors = BluesJamColors)`
  default-parameter constructor so a JVM test can call it with no Compose runtime — copy
  `RemovalDialogStyle`/`removalDialog()`'s shape.
- **Form fields inside a dialog**: reuse `AdminControlsDefaults.extraField()` and the existing
  private `ExtraTextField` composable (label, single line, `BluesJamTheme.typography.body`,
  `BluesJamTheme.shapes.md`) unchanged; only their container (inline `Column` → dialog `text` slot)
  moves.
- **Enabled-only-when-both-filled submit**: `isSubmittable(name, instrument)` in
  `ExtraParticipantFormState.kt` already does exactly this (`name.isNotBlank() && instrument.isNotBlank()`); reuse it verbatim for the dialog's `Agregar` `enabled` state.
- **One state object, never recreated**: `SongAdminState`'s `extraFormKey`/`extraName`/
  `extraInstrument`/`extraAddConsumed` are already `rememberSaveable`/`remember`ed once in
  `rememberSongAdminState` and never recreated; the dialog must read/write through this same state,
  not create its own `remember` inside the dialog composable (architecture skill, "Per-row UI state…
  never recreate such a state object").
- **Line styling for `EXTRA`**: a line's visuals come entirely from
  `LineupPanelDefaults.style(kind)`/`detailColor(kind)`, which delegate to
  `InstrumentStripDefaults.style(kind)`. Changing what `EXTRA` looks like in the *panel* without
  touching the *strip chip* requires decoupling the panel's `EXTRA` style from the shared
  `InstrumentStripDefaults.style` table (see Technical Approach) rather than editing the shared table
  in place, because `InstrumentStripDefaults.style` is also read by the collapsed-row strip
  (`LineupChips.kt`) which this feature does not touch.
- **Amber allowlist discipline**: `:feature:next-jam` is currently allowlisted for `key` only
  (and the publication roles). Neither the dialog nor the restyled line read amber; nothing changes
  in `AMBER_ROLE_ALLOWLIST`.
- **Copy in Kotlin, not resources**: new dialog strings go in `NextJamCopy` (or a dialog-specific
  object beside `ExtraParticipantControls.kt`, matching how `RemovalUiModel`'s dialog strings live on
  the model itself built from `NextJamCopy`/`NextJamAdminMapping.kt`), Rioplatense Spanish, *vos*.

### Current Gaps

- There is no existing `…DialogStyle` for a *non-destructive* confirm action (every current dialog
  style, `removalDialog()`, has a destructive `error` confirm). The implementer must decide the
  extras dialog's `Agregar` colour; this spec's recommendation (Technical Approach) is `text`, not
  `error` and not amber, consistent with `AddSongButton`'s existing non-destructive `text`-on-
  `surfaceRaised` styling.
- No existing test asserts dialog-visibility wiring to a `UiModel` boolean (there is no Compose UI
  test harness per the architecture skill's "No Compose UI test harness" rule); as with
  `RemovalUiModel.Confirming`, the dialog's open/closed state and its enabled/disabled `Agregar` must
  be fully provable from the `UiModel` on the JVM, and the drawn dialog is checked only by device
  screenshot/uiautomator, not by an automated UI test.

## Technical Approach

### 1. Decouple the panel's `EXTRA` style from the strip's `EXTRA` style

`InstrumentStripDefaults.style(EXTRA)` is read by both the lineup panel (`LineupPanelDefaults.style`)
and the collapsed-row instrument strip (`LineupChips.kt`, via `InstrumentChipUiModel`). The user's
request is specific to the panel ("en el detalle… mostrarlos igual que cupos cubiertos"); the
collapsed-row strip chip for an extra is not mentioned and should not change without a separate
decision (changing it would also touch `DESIGN.md`'s strip-chip section and its own contrast
numbers, which is out of scope here).

Therefore: stop `LineupPanelDefaults.style`/`detailColor` from delegating to
`InstrumentStripDefaults.style` for the `EXTRA` kind specifically. Two options, pick the one that
keeps `LineupPanelDefaultsTest` simplest:

- **Recommended**: in `LineupPanelDefaults.style(kind)`, map `EXTRA` to the *same* `ChipStyle` as
  `FILLED_SLOT` (`InstrumentStripDefaults.style(InstrumentChipKind.FILLED_SLOT, colors)`), and in
  `LineupPanelDefaults.detailColor(kind)`, map `EXTRA` to `colors.text` (the same branch as
  `FILLED_SLOT`). `InstrumentStripDefaults.style`'s own `EXTRA` branch (read by the strip) is
  untouched.
- This keeps the "never amber" contract (`FILLED_SLOT` was never amber either) and keeps
  `InstrumentChipKind.EXTRA` as a distinct enum value (so `LineupPanelMapperTest`'s kind-based
  assertions, D-18's "never a slot" filtering, and the instrument text/content description that
  still say `+ saxo`/`Otros: …` are all unaffected).

### 2. Replace the inline form with a dialog, keep the state machine

- `ExtraParticipantEditorUiModel` (defined beside `SongDetailUiModel`/`ExtraParticipantUiMapping.kt`)
  keeps its shape: `formVisible` (now "dialog visible"), `name`, `instrument`, `submitEnabled`,
  `saving`, `events: EventHandler<Event>`. No field is added or removed; `Open`/`NameChanged`/
  `InstrumentChanged`/`Submit`/`Cancel`/`Remove` events are unchanged in
  `ExtraParticipantFormState.handle`.
- Split `ExtraParticipantControls.kt`'s `ExtraParticipantEditor` into:
  - The **opener**: when `!model.formVisible`, draw `Agregar a Otros` as today (a `RowTextAction`),
    unchanged, plus the `Guardando…` line when `model.saving`.
  - The **dialog**: when `model.formVisible`, render a new `AddExtraDialog(model)` composable (same
    file) using Material 3 `AlertDialog`, modeled on `RemovalConfirmationDialog`:
    - `onDismissRequest = { model.events(ExtraParticipantEditorUiModel.Event.Cancel) }` (covers back
      and outside-tap in one place, per Scenario 2).
    - `title`: a short heading, e.g. `"Agregar a Otros"` (reuse `NextJamCopy.ADD_EXTRA`, no new
      string needed for the title).
    - `text`: a `Column` with the two `ExtraTextField`s (`NextJamCopy.EXTRA_NAME`,
      `NextJamCopy.EXTRA_INSTRUMENT`), unchanged field composable and colours
      (`AdminControlsDefaults.extraField()`).
    - `confirmButton`: `TextButton` labelled `NextJamCopy.SUBMIT_EXTRA` ("Agregar"),
      `enabled = model.submitEnabled`, `onClick = { model.events(Event.Submit) }`.
    - `dismissButton`: `TextButton` labelled `NextJamCopy.CANCEL` ("Cancelar"),
      `onClick = { model.events(Event.Cancel) }`.
    - Colours from a new `AdminControlsDefaults.extraDialog()` (see below); never amber.
  - **Caller site**: in `SongDetailScreen.kt`'s `songItems`, the `LINEUP_KEY` item's `Surface`/
    `Column` keeps `LineupEditor(it.lineup)` (owned by `lineup-grow-any-instrument`/earlier slices,
    untouched by this spec) and the extras **opener** only (not the dialog). The dialog itself is
    rendered as a sibling of that `Column`/`Surface` — e.g. immediately after the `LINEUP_KEY` item
    block inside the same `LazyListScope.songItems`, outside the `Surface`'s content — exactly as
    `AdminRowBar` draws `RemovalConfirmationDialog` beside, not inside, its `Row`. This is the fix
    for "no vista al final del detalle": the dialog floats over the whole screen, it is not another
    scrollable item.
- `ExtraParticipantFormState.handle`'s `Open` event still sets `formKey`/clears `name`/`instrument`/
  `consumed`; `Cancel` still clears them (Scenario 2's "reopening shows the fields empty again" is
  already guaranteed by `open()` always resetting `name`/`instrument` to `""`, not by `cancel()`
  alone — confirm this is still true, since `open()` already does it today, nothing to change there).
- `SongAdminState`/`rememberSongAdminState`/`ExtraParticipantUiMapping.kt`'s `SongAdminState.extraEditor`:
  unchanged. `ExtraParticipantFormState` unchanged.

### 3. New dialog style

Add to `AdminControlsDefaults.kt`:

```kotlin
data class ExtraDialogStyle(
    val container: Color,
    val title: Color,
    val confirm: Color,
    val cancel: Color,
)

/**
 * The "Agregar a Otros" dialog (`extras-dialog-and-lines`): `surfaceRaised` container, title in
 * `text`, "Agregar" in `text` (not destructive, never amber) and "Cancelar" in `text`. The two
 * fields inside reuse `extraField()` unchanged.
 */
fun extraDialog(colors: BluesJamColors = BluesJamColors): ExtraDialogStyle = ExtraDialogStyle(
    container = colors.surfaceRaised,
    title = colors.text,
    confirm = colors.text,
    cancel = colors.text,
)
```

(Exact colour choice for `confirm` is the implementer's call within "tokens only, never amber";
`text` is recommended because adding an extra is not destructive, unlike `removalDialog()`'s `error`
confirm.)

### 4. What stays exactly as is

- `SetlistRepository.addExtraParticipant`/`removeExtraParticipant`, `AdminWriter`, the Apps Script
  actions, the Sheet column format: untouched (D-13, D-4).
- `LineupPanelMapper.toLineupPanel`'s `onRemoveExtra` wiring, `ExtraParticipant.toLine`'s
  `actionLabel`/`action`/`actionDescription` (`Quitar`, `Quitar a {nombre}, {instrumento}`):
  untouched. The admin's `Quitar` per extra keeps working exactly as today; only the line's paint
  changes (step 1).
  The `+ {instrument}` prefix on the instrument text and the `"Otros: {instrument}, {name}"` content
  description: untouched, so a screen reader still distinguishes an extra from an actual slot even
  though the two now look alike visually.
- `overlays.isSavingExtras`, the `Guardando…` line, the failure-card path on a rejected/failed write:
  untouched (Scenario 3 is the existing behavior, just reframed around the dialog closing on submit
  instead of the inline form closing on submit).

## Expected File Changes

- `core/ui/src/main/kotlin/com/bbbjam/core/ui/lineup/LineupPanelDefaults.kt` — modify: `style(EXTRA)`
  returns the `FILLED_SLOT` `ChipStyle`; `detailColor(EXTRA)` returns `colors.text`.
- `core/ui/src/test/kotlin/com/bbbjam/core/ui/lineup/LineupPanelDefaultsTest.kt` — modify: rewrite
  the extra-line test to assert it now matches the filled-slot fill/glyph/detail colour (while still
  asserting it is never amber — reuse the same "never amber" assertion style).
- `core/ui/src/test/kotlin/com/bbbjam/core/ui/lineup/LineupPanelMapperTest.kt` — verify unchanged
  (kind/order/description/action assertions); adjust only if a helper hard-codes a colour
  expectation (none currently do).
- `feature/next-jam/src/main/kotlin/com/bbbjam/feature/nextjam/ExtraParticipantControls.kt` —
  modify: split into the opener (`RowTextAction` + `Guardando…`, when `!formVisible`) and a new
  `AddExtraDialog` composable (`AlertDialog`, when `formVisible`); keep `ExtraTextField` unchanged.
- `feature/next-jam/src/main/kotlin/com/bbbjam/feature/nextjam/SongDetailScreen.kt` — modify:
  render `AddExtraDialog` as a sibling of the lineup card's content (inside `songItems`, outside the
  `Surface`/`Column` that holds `LineupPanel`/`LineupEditor`), not nested inside it; keep the opener
  call site inside the card where `Agregar a Otros` is today.
- `feature/next-jam/src/main/kotlin/com/bbbjam/feature/nextjam/AdminControlsDefaults.kt` — modify:
  add `ExtraDialogStyle`/`extraDialog()`.
- `feature/next-jam/src/test/kotlin/com/bbbjam/feature/nextjam/AdminControlsDefaultsTest.kt` —
  modify: cover `extraDialog()`'s roles (no amber, exact roles as specified) following the existing
  pattern for `removalDialog()`.
- `feature/next-jam/src/test/kotlin/com/bbbjam/feature/nextjam/SongDetailAdminTest.kt` and/or a new
  focused test — modify/create: assert the dialog-visibility boolean and `submitEnabled` transitions
  (Open → both blank → `submitEnabled=false`; one field filled → still false; both trimmed non-blank
  → true; Cancel → fields cleared) at the `ExtraParticipantEditorUiModel` level, reusing
  `AdminFakes.kt`/`SongDetailFixtures.kt` fixtures. No Compose UI test harness exists, so this stays
  a `UiModel`-level assertion (architecture skill, "No Compose UI test harness").
- `DESIGN.md` — modify: update the "Extra participants" narrative section (lines 688-690) to
  describe the dialog instead of the inline form, and the lineup-panel line rules (around line 283)
  to say an extra's line now matches a filled slot's fill/glyph/colour, keeping only the `+` prefix
  and `Otros:` description as what still marks it an extra. Add a dated note that the strip chip
  (collapsed row, lines 241-244/253-258) is unchanged and intentionally out of scope. Update the
  Song detail section's line about `Agregar a Otros` ("or its form" → "or its dialog").
- `bb-blues-jam-bitacora.md` — modify: a new dated subsection recording the user's 12 October 2026
  request and the two decisions (dialog instead of inline form; extra line styled like a filled
  slot) without amending D-18's text (D-18 is about counting/filtering, unaffected).
- `.claude/skills/architecture/SKILL.md` — modify: append to the existing
  "As built by `admin-edit-extra-participants`" paragraph (or add a new "As built by
  `extras-dialog-and-lines`" paragraph immediately after it) recording: the dialog replaces the
  inline form (same `ExtraParticipantFormState`/events, new composable only), and
  `LineupPanelDefaults.style(EXTRA)`/`detailColor(EXTRA)` now match `FILLED_SLOT` while
  `InstrumentChipKind.EXTRA` and the strip's own `EXTRA` style stay distinct.

No change to `:core:data`, `backend/apps-script`, `:core:model`, Konsist rules, or any Gradle
dependency.

## Visual Design Impact

- UI involved: yes.
- Design source: `DESIGN.md` (extras, lineup panel, dialogs), `bb-blues-jam-bitacora.md` D-18,
  `admin-row-icon-actions`'s dialog precedent.
- Screens or states affected: the song detail (`SongDetailScreen`), admin and musician states of the
  lineup panel's `OTROS` section; the admin's `Agregar a Otros` entry point.
- New design artifact required: no — this follows the existing dialog pattern
  (`RemovalConfirmationDialog`) and the existing filled-slot line style; both are already specified
  in DESIGN.md and need only a dated update, not new design work.

## Durable Documentation Impact

- `ARCHITECTURE.md`: not needed — this repo's durable architecture doc is
  `.claude/skills/architecture/SKILL.md`, updated above.
- `CONSTRAINTS.md`: not needed — no new MUST/MUST NOT rule; D-13 and D-18 already cover the
  invariants this feature preserves.
- `AGENTS.md`: not needed — no change to the agent workflow, startup path, or repo-wide rules.
- `DESIGN.md`: update, as listed in Expected File Changes.
- `bb-blues-jam-bitacora.md`: update, as listed in Expected File Changes.
- `.claude/skills/architecture/SKILL.md`: update, as listed in Expected File Changes.

## Implementation Plan

1. Change `LineupPanelDefaults.style`/`detailColor` so `EXTRA` reads identically to `FILLED_SLOT`;
   update `LineupPanelDefaultsTest` to match; confirm `LineupPanelMapperTest` still passes unchanged.
2. Split `ExtraParticipantEditor` into the opener and a new `AddExtraDialog` (`AlertDialog`), reusing
   `ExtraTextField` and the existing event handling; add `AdminControlsDefaults.extraDialog()` and
   its test.
3. Move the dialog's render site in `SongDetailScreen.kt` so it is a sibling of the lineup card's
   content, not nested inside it — the direct fix for "no vista al final del detalle".
4. Add/extend presenter-level tests asserting dialog-visibility and `submitEnabled` transitions
   through `ExtraParticipantEditorUiModel`, plus the Cancel-clears-fields behavior.
5. Update `DESIGN.md`, `bb-blues-jam-bitacora.md`, and the architecture skill with dated notes.
6. Run `CI=true ./init.sh`; fix any detekt/ktlint/Konsist finding.
7. Device check per the Verification Plan below (never tapping `Agregar` for real).

## Implementation Tasks

- [ ] `LineupPanelDefaults.style(EXTRA)` and `detailColor(EXTRA)` match `FILLED_SLOT`; strip's
  `InstrumentStripDefaults.style(EXTRA)` untouched.
- [ ] `LineupPanelDefaultsTest` rewritten for the new extra-line expectation; still asserts "never
  amber".
- [ ] `ExtraParticipantControls.kt`: opener unchanged in behavior; new `AddExtraDialog` composable
  using Material 3 `AlertDialog`, `onDismissRequest` → `Cancel`.
- [ ] `AdminControlsDefaults.extraDialog()` added, no amber, covered by a test.
- [ ] `SongDetailScreen.kt`: dialog rendered as a sibling of the lineup card, not nested inside its
  scrollable content.
- [ ] Presenter/UiModel-level tests cover: dialog closed by default; `Open` shows it empty;
  `submitEnabled` false until both trimmed fields are non-blank; `Submit` with the dialog open and
  enabled calls `addExtraParticipant` once and closes the dialog; `Cancel` (mapped from dialog
  dismiss, outside tap, or explicit Cancelar) clears fields and writes nothing.
- [ ] `DESIGN.md`, `bb-blues-jam-bitacora.md`, `.claude/skills/architecture/SKILL.md` updated with
  dated notes.
- [ ] `CI=true ./init.sh` exits 0 with konsist, detekt and ktlint wired.
- [ ] Device screenshots captured per Verification Plan.

## Verification Plan

- `CI=true ./init.sh` — expected exit 0, konsist/detekt/ktlint all `wired`; this is the repo's
  standard non-blocking gate and starts no long-running process.
- `./gradlew :core:ui:test :feature:next-jam:test` (subset, during development) then the full
  `./init.sh` gate before calling the slice done.
- No persistent E2E harness exists in this repo (no Compose UI test harness per the architecture
  skill); JVM presenter/UiModel/Defaults tests are the only automated coverage, consistent with
  every prior UI slice in this project (`admin-row-icon-actions`, `song-detail-replaces-row-
  expansion`).
- Device plan (Pixel 5, debug build, per the project's "No Sheet test data" rule): enable
  `bluesjam.demoUpcomingJam=true` (and `bluesjam.demoUpcomingJamDraft`/`bluesjam.debugAdmin=true` as
  needed to reach an admin view of an upcoming jam) in the git-ignored `local.properties`, back it up
  first and restore it byte-for-byte afterward. On the song detail as admin:
  - Open `Agregar a Otros`: confirm the dialog appears over the detail (not a scrolled-to inline
    form), with `Nombre`/`Instrumento` labelled fields and `Agregar` disabled.
  - Type into `Nombre` only: `Agregar` stays disabled. Type into `Instrumento` too: `Agregar`
    becomes enabled.
  - Tap **Cancelar**: dialog closes, no write. Reopen: fields are empty again.
  - Reopen, type both fields, press the **system back button**: dialog closes, no write.
  - Reopen, type both fields, tap **outside the dialog**: dialog closes, no write.
  - **Never tap `Agregar`** (it would write to the real Sheet; per the project's no-Sheet-test-data
    rule, do not add or remove real Otros entries on a device check).
  - Screenshot an existing extra (from a song that already has one, e.g. via the existing demo/real
    upcoming jam data) and confirm visually it now has the same fill/glyph as a filled slot line,
    distinguished only by its `+ {instrument}` text and the `OTROS` heading; confirm the admin's
    `Quitar` on that line is still present with its usual look.
  - Capture uiautomator dump or screenshot evidence at font scale 1.0; font scale 2.0 is optional but
    recommended given the dialog's two fields plus two buttons must stay reachable.
  - Restore `local.properties` from the backup (byte-for-byte) and any changed device settings
    (font scale, admin flags) afterward.

## Evidence To Capture

- `CI=true ./init.sh` output (konsist/detekt/ktlint wired) before and after.
- Test run output naming the new/changed test classes and their pass counts.
- `LineupPanelDefaultsTest`'s before/after assertion text for the `EXTRA` kind.
- Screenshots: dialog closed→open, `Agregar` disabled→enabled, Cancelar/back/outside-tap all
  closing with no write, and the restyled extra line beside a filled slot line.
- Confirmation (byte-for-byte hash or diff) that `local.properties` was restored after the device
  check.

## Validator Checklist

- [ ] Implementation stays within this feature's scope (no slot-count, lineup-editor, or Sheet-schema
  change; those belong to `lineup-grow-any-instrument`).
- [ ] Acceptance scenarios 1-4 pass.
- [ ] `addExtraParticipant`/`removeExtraParticipant` repository calls are unchanged (D-13); the
  dialog calls the same function the inline form called.
- [ ] The dialog is rendered as a true sibling overlay, not a list item that scrolls with the
  detail (the user's specific complaint).
- [ ] The extra line's fill/glyph/text colour now matches a filled slot's; its instrument text keeps
  the `+` prefix and its content description keeps identifying it as `Otros`, so D-18 ("never a
  slot, never counted") is preserved in substance even though the look changed.
- [ ] No amber role is read outside the existing allowlist; dialog colours come only from
  `BluesJamTheme` tokens.
- [ ] Voseo Spanish copy throughout (D-12); no new string in a resource file.
- [ ] `DESIGN.md`, `bb-blues-jam-bitacora.md` and the architecture skill carry dated updates.
- [ ] `feature_list.json` and `PROGRESS.md` updated correctly with real evidence (test names/counts,
  device screenshots, gate output), not a restated plan.
- [ ] No unrelated product behavior or extra feature work was added (in particular: no change to
  slot counts, the lineup editor, or any Apps Script action).
