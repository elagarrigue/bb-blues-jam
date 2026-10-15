# Feature Implementation Spec: Single-select status chips and edit/delete icons on the Jams tab cards

## Source Feature

- `id`: jam-status-chips
- `area`: feature-past-jams
- `depends_on`: assistant-streaming (`accepted`)
- `status`: not_started at planning time
- `source`: `feature_list.json` (re-read after the user's scope addition: edit/delete icons)

## Goal

On every future jam card of the Jams tab (admin only) the status becomes **one row of three single-select chips**
(`Borrador`, `Creada`, `Publicada`) with the confirmed status selected. It replaces the badge, the `Pasar a…` text
actions and the full-width `Publicar lista` button on that card. The rules stay exactly as they are today. The
`Editar la jam` / `Borrar la jam` text actions become two 48dp icon buttons at the end of the card's date line.

The orchestrator chose **single-select chips** over a dropdown: the three options are always visible, the current
one is visible without opening anything, and a change takes one tap. The user asked for "a dropdown or chips".

## Non-Goals

- No backend change, no redeploy, no new mutation and no registry change. `setJamStatus` and `publishSetlist` are
  reached through the same `SetlistJamStatusRepository` / `SetlistPublishRepository` calls (D-13).
- Próxima jam is unchanged: it keeps the read-only `JamStatusBadge`, note and hint.
- No change to the unpublish dialog, the delete dialog, the edit dialog, `Nueva jam`, the failure cards or their copy.
- No new rule for when a write is allowed. The chips only redraw today's decisions.
- No Compose UI test harness. It remains a standing decision, so semantics are checked on the device.

## Job Story

When I open the Jams tab to set a jam's status,
I want to see its current status and the other two choices together and change it with one tap,
so I don't have to read `Pasar a…` labels and remember that publishing is a separate button.

## Users And Permissions

- Admin: sees the chips and icons on future cards. The local admin flag only decides what is drawn. Apps Script
  authorizes every write (passphrase guard, PUBLICADA upcoming-only, BORRADOR/CREADA on any future jam, U1/D-29).
- Musician: no change (no admin section).

## Decisions Taken In This Spec (planner)

- **P1 The badge is replaced** on Jams tab cards by the chip row. The selected chip is the status, and drawing both
  would repeat it. The note under it (`Los músicos todavía no ven esta jam.` and so on) stays.
- **P2 Amber by role (D-17).** Only the **selected `Publicada` chip** is amber (`published` / `onPublished`), which is
  the existing "published state" role that the badge used. A selected `Borrador`/`Creada` chip is neutral. Unselected
  and disabled chips are never amber. The inline publish confirmation keeps its amber `Publicar` button
  (`primaryAction` / `onPrimaryAction`). The amber is read only inside `:core:ui`, so the
  `feature/past-jams` allowlist stays `{key, primaryAction, onPrimaryAction}`. Only its comment changes, from
  `Publicar lista` to the `Publicar` confirmation button. No Konsist rule changes.
- **P3 Component location.** `InstrumentFilterBar`'s `FilterChip` (`:core:ui` `filter`) is private and
  checkbox-semantics, so it cannot be reused. It is the pattern to follow (48dp `heightIn`, `shapes.lg`, colours in a
  `Defaults` object). The new component is jam-status-specific and goes beside the badge in
  `com.bbbjam.core.ui.status`. That lets it reuse `JamStatusBadgeCopy` and keep the `published` amber inside `:core:ui`.
  A generic chip was rejected because it would let a feature choose amber.
- **P4 Visual states** (colours in an internal `JamStatusChipDefaults`, JVM-tested). Shape always differs, never
  brightness alone (U3):
  - unselected, enabled: `surfaceRaised` fill, hairline `border` outline (`Dp.Hairline`), `textMuted` label;
  - selected, `Borrador`/`Creada`: `surfaceRaised` fill, hairline `text` outline, `text` label, leading check glyph;
  - selected, `Publicada`: `published` fill, `onPublished` label and check glyph, no outline;
  - disabled (not selected): no fill, no outline, `textMuted` label. A disabled selected chip keeps its selected look
    and loses only the click.
  The check glyph is `Icons.Filled.Check` (material-icons-core, already an `implementation` dep of `:core:ui`).
  It is about 18dp, and its size constant lives in `:core:ui`. The label is `body`. Padding is `spacing.sm`
  horizontally, with `spacing.xs` between the glyph and the label.
- **P5 Layout and targets.** The chips sit in a `FlowRow` (spacing `xs`) that wraps at large font scales and never
  scrolls horizontally. Each chip is at least `LocalMinimumInteractiveComponentSize` (48dp) tall and wide.
- **P6 TalkBack.** The row has `Modifier.selectableGroup()`. Each chip uses
  `selectable(selected, enabled, role = Role.RadioButton)` and is a single node: the label and glyph have
  `clearAndSetSemantics {}` and the chip has `contentDescription = "Estado: Borrador"` (the visible label is contained
  in the name). The selected state and disabled come from `selectable`. Tapping the selected chip emits nothing.
- **P7 Publishing from the chip.** Tapping an enabled `Publicada` chip opens **today's inline confirmation** under the
  chips (`¿Publicar la lista?`, details, reversible note, amber `Publicar`, `Cancelar`). Selection does not move
  while it is open or while publishing. Only the confirmed snapshot moves it. Tapping `Publicada` again while
  confirming is a no-op. Tapping `Borrador`/`Creada` while confirming closes the confirmation (`publish.onCancel`) and
  then applies that chip's normal rule. `Publicando…`, the publish failure card and `Reintentar` are unchanged.
- **P8 `Publicada` disabled** (not selected, not busy) on: a held-back card; an upcoming card whose setlist is empty;
  an upcoming card whose setlist is unreadable (`Unavailable`, for example a missing tab). These are today's cases
  with no publish action. One `caption`/`textMuted` reason line goes under the note, and its copy is the planner's
  wording (the user may reword it):
  - held back: `Solo la próxima jam se puede publicar.`
  - empty: `Para publicar, agregá al menos un tema en Próxima jam.`
  - unreadable: `Para publicar, la lista se tiene que poder leer.`
- **P9 Busy = disabled, not hidden.** While a status change, publication or edit/delete of that jam is sending,
  all three chips **and** both icons are drawn disabled (`IconRowAction` muted tint, no click). The existing polite
  line (`Cambiando el estado…`, `Publicando…`, `Guardando los cambios…`, `Borrando la jam…`) is still shown. This
  replaces "hidden while sending" so the card does not jump.
- **P10 Icons.** At the end of the date line (the date gets `weight(1f)`): `IconRowAction(Edit)` with tint `text`, then
  `IconRowAction(Delete)` with tint `error`, muted `textMuted`. The content descriptions are
  `Editar la jam del sábado 31 de octubre` and `Borrar la jam del sábado 31 de octubre` (`PastJamsCopy.sentenceDate`).
  Delete stays **absent** (not disabled) when the song count is unknown, as today. Edit opens the edit dialog and
  delete opens the delete confirmation dialog, both unchanged. Add `Edit(Icons.Filled.Edit)` to `IconRowActionIcon`.
  `IconRowAction` is already in `:core:ui`, so no extraction is needed and D-03 holds.

## Acceptance Scenarios

1. **BORRADOR -> CREADA.** Given the upcoming jam is BORRADOR, when the admin taps `Creada`, then `setJamStatus(date,
   CREATED)` is called once (a double tap still calls once), no dialog opens, the chips are disabled with
   `Cambiando el estado…`, and `Borrador` stays selected until the snapshot says CREATED.
2. **CREADA -> BORRADOR** is immediate in the same way, and so are both changes on a held-back card.
3. **Leaving PUBLICADA.** Given PUBLICADA, when the admin taps `Creada` or `Borrador`, then `UnpublishDialog` opens
   with today's copy. `Cancelar` or back writes nothing. Confirm writes once.
4. **Publishing.** Given an upcoming BORRADOR/CREADA jam with at least one song, when the admin taps `Publicada`, then the
   inline confirmation appears and `publishSetlist` is not called. `Cancelar` writes nothing. `Publicar` calls it once.
   Selection moves only after the confirmed snapshot.
5. **Publicada disabled.** On a held-back card, on an empty list and on an unreadable list, `Publicada` is disabled
   and the matching P8 line is shown. A `Request(PUBLISHED)` event sent anyway calls nothing.
6. **Failure.** A failed status change or publication leaves the previous chip selected and shows today's failure card
   (`Reintentar`, `Cerrar`; the outdated script has `Cerrar` only).
7. **Icons.** Every future card shows edit and delete icons with date-naming descriptions. The delete icon is absent
   on an unreadable list. Both icons are disabled while that jam's status, publication or edit is sending. The delete
   icon opens the delete dialog and Cancelar writes nothing.
8. Musicians and Próxima jam are unchanged. Próxima jam still draws the badge.

## Repository Research

### Files Inspected

- `feature/past-jams/.../JamStatusControls.kt`, `JamStatusMapping.kt`, `JamStatusUiModel.kt`, `JamStatusViews.kt`,
  `JamStatusDefaults.kt`: request/confirm/in-flight logic, `statusTargets`, `actions`, the dialog.
- `PublishControls.kt`, `PublishMapping.kt`, `PublishDetails.kt`: `PublishUiModel.Idle/Confirming/Publishing/Failed`.
  Publish is null for empty/unreadable lists, while a status or edit is sending, and on PUBLISHED.
- `JamsAdminViews.kt` (`FutureJamCard`, `JamControls`, `PrimaryPublishButton`, `PublishTextAction`),
  `JamsAdminMapping.kt` (`toFutureCard`, publish only on the upcoming card), `JamsAdminUiModel.kt`, `JamsAdminDefaults.kt`,
  `JamsAdminState.kt`, `PastJamsCopy.kt`, `JamEditCopy.kt`, `JamEditMapping.kt` (`manageUiModel`), `JamEditUiModel.kt`,
  `JamEditViews.kt` (`JamManageActions`).
- `core/ui/.../status/JamStatusBadge*.kt`, `filter/InstrumentFilterBar.kt` + `InstrumentFilterDefaults.kt` (chip pattern),
  `action/IconRowAction.kt` + `IconRowActionIcon.kt`, `theme/BluesJamColors.kt`, `BluesJamDimens.kt`.
- `konsist-test/.../ModuleIsolationTest.kt` (`AMBER_ROLE_ALLOWLIST`), `feature/past-jams/build.gradle.kts`.
- Tests: `JamsAdminTest.kt` (labels(), `Idle`, `badge.label`, `actions`), `JamEditAdminTest.kt` (`editLabel`/`deleteLabel`).
- `DESIGN.md` (front-matter components, Colors, Status badge, Jams tab, Editing and deleting, Publishing, Changing the
  status), `bb-blues-jam-design-prompt.md` (Estructura item 2), `docs/user-and-access-model.md` (line about
  `Publicar lista`), architecture skill (as-built sections, amber allowlist rule).

### Existing Patterns To Follow

- Colours only through `*Defaults` objects with a `colors: BluesJamColors = BluesJamColors` parameter, JVM-tested.
- `EventHandler` with a key in `UiModel`s. The presenter decides and composables only forward.
- `IconRowAction` sets the description on the clickable `Box`, and a disabled action is still drawn.

### Current Gaps

- No single-select (radio) chip exists. No `Edit` glyph in `IconRowActionIcon`.

## Technical Approach

**`:core:ui` `status` (new files):**
- `JamStatusChipsUiModel(chips: List<JamStatusChipUiModel>, events: EventHandler<Event>)` with
  `Event.Select(status: JamStatus)`, and `JamStatusChipUiModel(status, label, description, selected, enabled,
  isPublished)`.
- Pure `jamStatusChips(current: JamStatus, enabled: Set<JamStatus>, events)`, which returns the chips in
  DRAFT, CREATED, PUBLISHED order with labels from `JamStatusBadgeCopy` and descriptions `Estado: <label>`.
- Internal `JamStatusChipDefaults.style(selected, published, enabled, colors)` per P4.
- `@Composable fun JamStatusChips(model, modifier)` per P5/P6 (a selected chip's `onClick` does nothing), with
  previews of all states.

**`:core:ui` `action`:** add `Edit(Icons.Filled.Edit)` to `IconRowActionIcon`.

**`:feature:past-jams` (deps unchanged: `:core:ui`, `:core:data`):**
- `JamStatusUiModel`: replace `actions: List<StatusActionUiModel>` with `chips: JamStatusChipsUiModel` and add
  `publishUnavailable: String?` (P8). Delete `StatusActionUiModel` and `statusTargets`. Keep `TO_CREATED`/`TO_DRAFT`
  as the dialog's confirm labels.
- `JamStatusMapping.jamStatusUiModel(jam, isUpcoming)`: if busy (as today, plus edit sending), `enabled` is empty;
  otherwise it is the other two statuses, plus PUBLISHED when the card is upcoming, the status is not PUBLISHED and the
  setlist is `Available` with at least one song. The chips' handler forwards `Select(s)` to `Event.Request(s)`. In
  `statusEvents`: `Request(current)` does nothing; `Request(PUBLISHED)` calls `publish.onRequest(date)` only when
  PUBLISHED is enabled; any other `Request` first calls `publish.onCancel(date)` when the publish confirmation is open
  for this date, then `status.onRequest(...)`.
- `PublishUiModel`: remove `Idle`. `publishUiModel` returns `Confirming`/`Publishing`/`Failed` or null, and the
  null conditions stay the same.
- `FutureJamUiModel`: remove `badge`, since `controls.status.chips` carries the status. `JamManageUiModel`:
  replace `editLabel`/`deleteLabel` with `edit: IconActionUiModel?` and `delete: IconActionUiModel?`, where
  `IconActionUiModel(description, enabled)` is in the same file; edit is always present and delete is null when the
  song count is unknown. Add `JamEditCopy.editDescription(date)` and `deleteDescription(date)`.
- Views: in `FutureJamCard`, the date line goes in a `Row` with the icons. The status column draws `JamStatusChips`,
  the note, the held-back/musician lines and the P8 line, then sending, failure, publish and dialogs.
  `PrimaryPublishButton` stays for `Publicar`. Remove the `JamStatusBadge` import from past-jams.
- `JamStatusDefaults` kdoc: amber stays on `Publicar` and the selected `Publicada` chip (in `:core:ui`).

## Expected File Changes

- `core/ui/src/main/kotlin/com/bbbjam/core/ui/status/JamStatusChips.kt`, `JamStatusChipsUiModel.kt`,
  `JamStatusChipDefaults.kt`: create.
- `core/ui/src/main/kotlin/com/bbbjam/core/ui/action/IconRowActionIcon.kt`: modify (`Edit`).
- `core/ui/src/test/kotlin/com/bbbjam/core/ui/status/JamStatusChipsTest.kt`: create (mapper and defaults).
- `feature/past-jams/src/main/kotlin/com/bbbjam/feature/pastjams/JamStatusUiModel.kt`, `JamStatusMapping.kt`,
  `JamStatusViews.kt`, `JamStatusDefaults.kt` (kdoc), `PublishMapping.kt`, `JamsAdminViews.kt`, `JamsAdminMapping.kt`,
  `JamsAdminUiModel.kt`, `JamEditUiModel.kt`, `JamEditMapping.kt`, `JamEditViews.kt`, `JamEditCopy.kt`,
  `PastJamsCopy.kt` (P8 lines), and `PastJamsStatesPreview.kt` if it builds these models: modify.
  `JamStatusControls.kt`/`PublishControls.kt` change only if routing needs it.
- `feature/past-jams/src/test/.../JamsAdminTest.kt`, `JamEditAdminTest.kt`: amend, and add tests (see Verification).
- `konsist-test/.../ModuleIsolationTest.kt`: comment only (P2).
- Docs (below).

## Visual Design Impact

- UI involved: yes. Source: `DESIGN.md` + P1–P10. Screens: the admin Jams tab future cards (upcoming and held back)
  in the idle, confirming publish, publishing, sending, failure, unpublish dialog, Publicada disabled and icons
  disabled states.
- New design artifact: no. `DESIGN.md` gets a `chip-status` component and the text below.

## Durable Documentation Impact

- `DESIGN.md`: update. Add `chip-status` to the front-matter components. "Status badge" now says Próxima jam keeps
  the badge and Jams cards use the chips. Rewrite the Jams-tab card paragraph, "Changing the status", "Publishing"
  (no `Publicar lista` button; the confirmation opens from the chip) and "Editing and deleting" (icons, P9/P10). In
  Colors, note that the selected `Publicada` chip is the published-state role.
- `bb-blues-jam-design-prompt.md`: update Estructura item 2 (status with single-select chips, edit/delete icons; the
  feature id).
- `docs/user-and-access-model.md`: update the `Publicar lista` sentence (the `Publicada` chip draws it).
- `.claude/skills/architecture/SKILL.md`: add an "As built by `jam-status-chips`" note (`JamStatusChips` in
  `:core:ui` `status` reads `published`; past-jams allowlist unchanged; `IconRowActionIcon.Edit`).
- `ARCHITECTURE.md`, `CONSTRAINTS.md`, `AGENTS.md`: not needed (no new boundary or rule). `CONTEXT.md`: not needed.
- `bb-blues-jam-bitacora.md`: not needed unless the user wants this recorded as a decision (open question 3).

## Implementation Plan

1. Baseline `CI=true ./init.sh`. Record the test count.
2. Write the `:core:ui` tests first (`jamStatusChips` order/labels/descriptions/selection/enabled; `JamStatusChipDefaults`
   per state, with amber only for selected+published), see them fail, then add the component and `IconRowActionIcon.Edit`.
3. Amend and add the presenter tests (red), then change the past-jams models, mapping and views.
4. Update the docs and the Konsist comment. Run the gate. Run the device check.

## Implementation Tasks

- [ ] T1 `:core:ui` chips model, mapper, defaults, composable and previews; `Edit` glyph; tests red then green.
- [ ] T2 `JamStatusUiModel.chips` + `publishUnavailable`; routing of `Request` (P7/P8); remove `Idle`, `badge`,
  `StatusActionUiModel`, `statusTargets`.
- [ ] T3 `JamManageUiModel` icons + descriptions; card date row; disabled-while-busy (P9).
- [ ] T4 Amend `JamsAdminTest`/`JamEditAdminTest` and add the new tests listed below.
- [ ] T5 Docs + Konsist comment; `CI=true ./init.sh`; device check; evidence in `docs/evidence/jam-status-chips/`.

## Verification Plan

- `CI=true ./init.sh` exits 0 and prints `konsist`, `detekt`, `ktlint` as `wired`. Compare the JVM test count with
  the baseline. `AMBER_ROLE_ALLOWLIST` entries are unchanged.
- Molecule tests (`:feature:past-jams`), one per acceptance scenario 1–7: the selected and enabled chips for
  DRAFT/CREATED/PUBLISHED upcoming and for held back; immediate DRAFT<->CREATED with a double tap calling once;
  unpublish confirm/cancel; `Publicada` -> inline confirmation, cancel writes nothing, confirm once; another chip
  while confirming cancels and applies; `Request(current)` is a no-op; held-back, empty and unreadable `Publicada`
  disabled with the P8 line and `Request(PUBLISHED)` calling nothing; while sending a change, a publication or an edit,
  all chips and both icons are disabled and the selection stays; a failure keeps the selection and the card; the
  icon descriptions name the date and delete is null on an unreadable list.
- Device (Pixel, debug build, **no write to the real Sheet**; real jams: 31/10 BORRADOR upcoming, 13/11 held back).
  Record the `local.properties` md5 before and after. Take a uiautomator dump of the Jams tab: the three chips per
  card, which is selected/checked, `Publicada` disabled on 13/11, the icon descriptions and the 48dp bounds. Take a
  screenshot. Use TalkBack, or the dump's `checkable`/`checked`/`selected` attributes, to record how the selected state
  is exposed. **Never tap a non-selected `Borrador`/`Creada` chip.** If 31/10 has at least one song, tap `Publicada`,
  see the confirmation, tap `Cancelar`. If its list is empty, record the disabled chip and the P8 line instead. Tap the
  edit icon and cancel the dialog. Tap the delete icon and cancel the dialog. Compare the startup `jams refresh` lines
  before and after: both jams unchanged. The debug demo jam may be used for the other states (memory: no Sheet test
  data).

## Evidence To Capture

Gate output, red runs (core and presenter), the dump and screenshot, the md5 before and after, the jams lines before
and after, and how the selected state is announced.

## Validator Checklist

- [ ] No backend, registry or repository-surface change. Writes only via existing functions (D-13).
- [ ] The selection comes only from the snapshot status. No optimistic chip.
- [ ] Amber: only the selected `Publicada` chip (inside `:core:ui`) and `Publicar`. The allowlist is unchanged.
- [ ] `selectableGroup` + `Role.RadioButton`, 48dp chips and icons, and the descriptions are vos Spanish and name the date.
- [ ] Unpublish dialog, delete dialog, failure cards and their copy are unchanged. Próxima jam still has the badge.
- [ ] No feature-to-feature dependency, no `MaterialTheme.colorScheme`, no `material.icons` import in a feature.
- [ ] Docs updated as listed. The device check made no write.

## Open Questions For The User (not blocking)

1. P8 copy for a disabled `Publicada` (the three lines) is the planner's wording.
2. P9 changes "hidden while sending" to "drawn disabled" for the status and the icons. Confirm or ask to keep hiding.
3. Should the chip choice be recorded as a decision in the bitácora (D-30)? The spec assumes not.
