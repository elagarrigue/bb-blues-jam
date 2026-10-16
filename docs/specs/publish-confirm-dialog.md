# Spec: publish-confirm-dialog

## Source feature

- id: `publish-confirm-dialog` (area `feature-past-jams`), depends on `jam-status-chip-loading` (accepted per the
  feature list history; verify `status` before starting).
- Behavior: tapping the Publicada chip on the upcoming jam opens a modal dialog `¿Publicar la lista?` instead of the
  inline block under the chips. Same content, copy and publish rules; styled like the unpublish dialog.
- Module: `:feature:past-jams` only. No backend, registry, repository or `:core:*` change (D-03, D-13 untouched).

## Goal and non-goals

Goal: replace the inline `PublishUiModel.Confirming` rendering with a Material 3 `AlertDialog`, keeping every
write rule (explicit confirm, one publication, pending chip animation) as it is.

Non-goals: new copy, changing `SetlistPublishRepository`, changing the failure card (`PublishUiModel.Failed` stays
inline), changing the unpublish dialog, new UI-model types, any Sheet/Apps Script work.

## Acceptance scenarios

1. Given the upcoming jam is Borrador/Creada with songs, when the admin taps Publicada, then a modal dialog opens
   with title `¿Publicar la lista?`, the details line (`Los músicos van a ver los N temas cuando abran o actualicen
   la app.` / singular variant), the note `Después podés volver a cambiar el estado desde acá.`, and the buttons
   `Cancelar` and `Publicar`. No inline block is drawn.
2. When the admin taps `Cancelar`, presses back, or taps outside, then the dialog closes and nothing is written.
3. When the admin taps `Publicar`, then the publication starts exactly once and the dialog closes as sending
   begins; the Publicada chip shows the pending animation (unchanged behavior).
4. When `Publicar` is tapped twice quickly, then `publishSetlist` is called once.
5. When the open dialog's jam leaves the snapshot, becomes published, loses its songs, or a write starts, then the
   dialog closes without writing, and does not reappear when the jam returns without a new tap on Publicada.
6. When the device rotates with the dialog open, then the dialog is still open afterwards.

## Repository research (inspected)

- `PublishControls.kt`: `rememberSaveable confirming: String?` + non-saved `consumed` guard; `onConfirm` is
  guarded by `confirming == date && !consumed`, launches `UNDISPATCHED`, clears `confirming` afterwards.
  `onRequest` sets `confirming` and resets `consumed`.
- `PublishMapping.kt`: `publishUiModel` returns null when `songCount == 0` or any write is sending; else
  `Failed` before `Confirming`. `Confirming(prompt, details, reversibleNote, confirmLabel, cancelLabel, events)`.
  It does not check `jam.status == PUBLISHED`.
- `JamsAdminViews.kt`: `JamControls` calls `JamStatusActions`, then `PublishControl(it)` (inline `Confirming`
  Column with `PrimaryPublishButton` and `PublishTextAction`), then `UnpublishDialog` from `status.confirming`.
  `PrimaryPublishButton` is private and used only by the inline block.
- `JamStatusViews.kt`: `UnpublishDialog(model, events)` is the pattern (`AlertDialog`, `onDismissRequest = Cancel`,
  `JamStatusDefaults.dialog()` colours: `surfaceRaised`, `text` title, `textMuted` body, `error` confirm, `text`
  cancel). `JamStatusDefaults.kt` holds the colours so JVM tests can assert them.
- `JamStatusMapping.kt` `chipEvents`: Publicada opens the confirmation only if `canPublish && !confirmingPublish`;
  any other chip first calls `publish.onCancel` when confirming. The key includes `publish.confirming`.
- Tests: `JamsAdminTest.kt` lines ~357-388 (confirmation, cancel, double confirm), ~391 (single song), ~487
  ("another chip while the publish confirmation is open closes it"). Not inspected: `JamStatusDefaultsTest.kt`
  contents, the presenter wiring that builds `JamsAdminState`, previews/screenshot files for this module.
- Docs with the inline wording: `DESIGN.md` ("Publishing", the paragraph that starts `The upcoming jam's card shows
  its status`), `docs/specs/jam-status-chips.md` (historical, do not edit). `bb-blues-jam-design-prompt.md` has no
  text on this confirmation (only generic "acción para publicar"); not needed.

## Decisions

- **Model unchanged in shape.** Keep `PublishUiModel.Confirming` and its `events`; the dialog is a new composable
  fed by it. The strings stay in the model (`prompt`, `details`, `reversibleNote`, labels). Update KDoc that says
  "inline".
- **Where it is drawn.** In `JamControls`, like `UnpublishDialog`: `PublishConfirmDialog(model)` for
  `PublishUiModel.Confirming`; `PublishFailureCard` stays inline. `PublishControl` keeps only the `Failed` arm
  (or is split so the dialog sits beside the card, not inside the card's Column).
- **Content.** Title `model.prompt` (`body` typography, `text`), body a Column of `details` then `reversibleNote`
  (`caption`, `textMuted`), cancel `TextButton` `Cancelar`, confirm `TextButton` `Publicar`.
- **Styling reuse.** Add `JamStatusDefaults.publishDialog(colors)` (or a `confirm` parameter on `dialog()`) that
  returns the same `DialogStyle` with `confirm = colors.primaryAction` instead of `error`. Publishing is not
  destructive, so no red; amber appears only on `Publicar`, by role (D-17). Read colours only through this
  function, no `MaterialTheme.colorScheme`. Add a JVM assertion in `JamStatusDefaultsTest`. Confirm that
  `primaryAction` text on `surfaceRaised` reads acceptably on the device; if not, report it, do not invent a colour.
- **Dismissal.** `onDismissRequest` (back, outside tap) and `Cancelar` both emit `PublishUiModel.Event.Cancel`;
  nothing is written. Default `DialogProperties`.
- **Chip routing simplified.** A modal blocks chip taps, so remove the "another chip closes the open publish
  confirmation" branch and `publish.confirming` from the `chipEvents` key in `JamStatusMapping.kt`. Publicada keeps
  `if (canPublish) publish.onRequest(date)`; make `onRequest` a no-op when that date is already confirming (keeps
  `consumed` from resetting). Update the KDoc of `chipEvents` and `jamStatusUiModel`.
- **Saveable state.** `confirming` stays `rememberSaveable` (rotation reopens the dialog). `consumed` stays
  unsaved: after a rotation it is false, harmless because the first confirm sets it and the model turns null once
  the publish is Sending.
- **Double tap.** Existing `consumed` guard plus the dialog leaving composition once `publishes` has a Sending entry.
  Keep the guard; add the test below.
- **Closing when the jam leaves.** The model is null (dialog gone) when the jam is absent, has no songs or a write is
  sending. Additionally, `publishUiModel` must return null for `jam.status == PUBLISHED`. To avoid reopening on
  return, clear a stale `confirming` when the date is no longer an eligible future jam: the implementer picks the
  smallest place after inspecting the presenter (a `LaunchedEffect` keyed on the snapshot that calls
  `publish.onCancel(date)`), and records the choice. If that proves to need a signature change in `:core:*`, stop and
  report instead.

## Expected file changes (all under `feature/past-jams/src`)

- `main/.../JamsAdminViews.kt`: dialog composable, drop inline Confirming arm, delete `PrimaryPublishButton` if
  unused; `JamControls` draws the dialog.
- `main/.../JamStatusDefaults.kt`: publish dialog style. `JamsAdminDefaults.publish()` is removed only if nothing
  else uses it (grep first); update its KDoc ("inline confirmation's button").
- `main/.../JamStatusMapping.kt`, `PublishMapping.kt`, `PublishControls.kt`, `JamStatusUiModel.kt` (KDoc).
- `test/.../JamsAdminTest.kt`, `JamStatusDefaultsTest.kt`.
- A preview of the dialog beside the existing unpublish dialog preview, if one exists.

## Visual design impact

`DESIGN.md` "Publishing" paragraph: replace the inline confirmation sentences with: tapping Publicada opens a modal
dialog `¿Publicar la lista?` (`surfaceRaised`, `text` title, `textMuted` `caption` lines with the song count and
`Después podés volver a cambiar el estado desde acá.`, `Cancelar` in `text`, `Publicar` in amber `primaryAction`);
back or an outside tap equals `Cancelar`; the selection does not move; chips cannot be tapped while it is open.
Remove "choosing Borrador or Creada instead closes it". Amber list: still the `Publicar` action and the selected
Publicada chip. Also update the amber-by-role mention if it names an "inline" button. No change to the design prompt.

## Documentation impact

- `DESIGN.md`: update (above). `PROGRESS.md`: record the slice and evidence. `feature_list.json`: status/evidence.
- `AGENTS.md`, `CONTEXT.md`, `docs/*`, `architecture` skill: not needed (no module, mutation or contract change).
  `.claude/skills/architecture/SKILL.md`: not needed unless it describes this inline block (grep `inline`).

## Implementation tasks

1. Read the files listed above; run `CI=true ./init.sh` for a baseline.
2. Add the publish dialog style and its JVM test.
3. Add `PublishConfirmDialog`, wire it in `JamControls`, remove the inline arm and dead code.
4. Simplify `chipEvents`, make `onRequest` idempotent, drop the `PUBLISHED` hole in `publishUiModel`, add the
   stale-state clearing.
5. Amend/add Molecule tests; update `DESIGN.md`, KDoc, `PROGRESS.md`, `feature_list.json`.
6. Run the gate and the device check.

## Verification plan

Tests (Molecule, JVM, `JamsAdminTest`):
- open: Publicada select yields `Confirming` with the same strings; no write.
- cancel: `Cancel` clears it, `publishes.calls` empty; reopening works.
- dismissal: the dialog's dismiss path is the same `Cancel` event (assert via the model; the composable binds
  `onDismissRequest` to it).
- confirm: `Confirm` calls `publishSetlist` once; selected chip stays confirmed, pending is Publicada.
- double confirm: two `Confirm` events, one call (keep the existing assertion).
- jam leaves: confirming open, then a snapshot without that jam / with it published / with an empty list gives a
  null `publish`; re-adding the jam does not reopen it.
- replace the "another chip closes it" test with: Publicada while confirming is a no-op; no status write.
- `JamStatusDefaultsTest`: publish dialog colours (amber confirm only).

Gate: `CI=true ./init.sh` exits 0 with `konsist`, `detekt`, `ktlint` all `wired`.

Device check (debug build, no writes): on the Jams tab, upcoming jam with songs (31/10, 12 songs), tap the Publicada
chip, confirm the dialog layout and copy, tap `Cancelar`; reopen and press back; reopen and tap outside; rotate with
it open. NEVER tap `Publicar` or any other chip. If the upcoming jam is already published or the list is empty the
chip is not openable: report that instead of changing data (no Sheet test data).

## Evidence to capture

Test counts for `:feature:past-jams`, the three `wired` lines, a device screenshot of the open dialog, the
statement that Publicar was never tapped, the chosen place for stale-state clearing.

## Validator checklist

- Dialog is modal, copy identical to before, back/outside = `Cancelar`, nothing written on dismissal.
- Amber only on `Publicar`; colours from `JamStatusDefaults`, no `MaterialTheme.colorScheme` (D-17); vos copy (D-12).
- No new write path, no change outside `:feature:past-jams` and docs; no feature-module dependency (D-03).
- Dead inline code and unused styles removed; no test deleted without replacement; no rule weakened.
- `DESIGN.md` no longer describes the inline confirmation.

## Assumptions and risks

- Assumed `primaryAction` is legible as text on `surfaceRaised` (not verified).
- Assumed the presenter has a convenient place to clear stale `confirming`; unverified.
- Risk: `consumed` is not saved, so a rotation during an in-flight publish could allow a second confirm only if the
  dialog were still visible; it is not, because Sending nulls the model.
