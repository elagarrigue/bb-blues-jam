# Spec: jam-status-chip-loading

## Source feature
- id `jam-status-chip-loading`, area `feature-past-jams`, depends on `jam-status-chips` (commit b4b4571).
- Request: while a status change sends, remove the text, disable the chips, and show a loading
  animation, perhaps on the chips' border.

## Goal
While a status change or a publish sends from the Jams tab chips, no sending text is drawn. All chips
are disabled and the border of the chip being applied (the requested status) pulses until the server
answers. TalkBack hears the change is in progress through a polite live region.

## Non-goals
- The edit/delete lines `Guardando…` / `Borrando…` (`JamEditViews`) do not change. While an edit sends,
  chips stay disabled and no chip animates (there is no requested status).
- No new mutation, no repository, Sheet or Apps Script change, no new module or dependency.
- No other animation in the app. No new colour role, no amber.
- No retry/failure card change.

## Decisions (planner)
- Animated chip = the requested target: `JamStatusChange.target` of the Sending entry, or PUBLICADA for
  a Sending `SetlistPublish`. Publishing is included because it is chosen via the Publicada chip.
  The selected chip stays on the confirmed status (existing rule).
- Animation = the pending chip's outline pulses in alpha, reusing the loading dots' timing: alpha is
  `loadingDotAlpha(elapsed, index = 0)` (1200 ms cosine, 0.3 to 1). It is drawn as a 2dp outline,
  not hairline, which would be invisible. Colour is `textMuted` (loading is not amber, D-17), also for the
  Publicada chip. The pending chip also has the `surfaceRaised` fill; other disabled chips keep their
  current disabled style. Shape, not brightness alone, marks it: it is the only chip with an outline
  while the others are disabled.
- Reduced motion (animator scale 0, `rememberReducedMotion()`): the same 2dp outline at constant alpha
  `LoadingDotsDefaults.STATIC_ALPHA` (0.6), no transition created.
- Stops when the pending chip leaves the composition state (answer, failure) or the screen: the
  infinite transition lives only inside the pending chip's composable.
- Accessibility: the visible line is removed. The pending chip gets a polite live region with
  description `Cambiando el estado a Creada…` (or `Publicando la lista…` for PUBLICADA), replacing
  `Estado: Creada` for that node. It is disabled, not selected. Copy is vos-neutral Rioplatense.
- Amber: unchanged. Only the selected PUBLICADA chip uses `published`, still inside `:core:ui`; no feature
  needs an amber allowlist change.

## Acceptance scenarios
1. Given a CREADA jam on the upcoming card, when the admin taps Borrador and the write is sending, then
   no `Cambiando el estado…` text is drawn, all three chips are disabled, Creada stays selected and the
   Borrador chip has the pulsing outline.
2. Given a confirmed publish is sending, then no `Publicando…` text is drawn, the Publicada chip is the
   pending one and all chips are disabled.
3. Given the server answers (success or failure), then the outline stops, the selection moves only on
   success, and the failure card is unchanged.
4. Given animator scale 0, the pending chip shows a static outline.
5. Given TalkBack, the pending chip is announced politely once as in progress; idle chips read
   `Estado: X` as before.
6. Given an edit/delete is sending, chips are disabled and no chip animates; the edit line is as today.

## Repository research (inspected)
- `core/ui/.../status/JamStatusChips.kt` (`StatusChip` draws the `Row`, border only from `style.outline`
  at `Dp.Hairline`), `JamStatusChipDefaults.kt` (`style(selected, published, enabled)`),
  `JamStatusChipsUiModel.kt` (`jamStatusChips(current, enabled, events)`).
- `core/ui/.../state/LoadingDots.kt` and `LoadingDotsDefaults.kt`: `rememberReducedMotion()` and
  `rememberDotsClock()` are `internal`/`private` in `state`; `isReducedMotion`, `loadingDotAlpha`,
  `PERIOD_MILLIS`, `MIN_ALPHA`, `STATIC_ALPHA`. Same module, so reuse is possible without a public API.
- `feature/past-jams/.../JamStatusMapping.kt` (`busy`, `changeSending`, `publishSending`, `sending` line),
  `JamStatusViews.kt` (`JamStatusActions` draws the sending line), `PublishMapping.kt` and
  `JamsAdminViews.kt:140` (`PublishUiModel.Publishing` text), `PastJamsCopy.kt` (`STATUS_SAVING`,
  `PUBLISHING`).
- Tests: `JamsAdminTest.kt` lines 274, 304, 373 assert the sending texts; `JamStatusChipsTest.kt`,
  `LoadingDotsTest.kt` exist. Previews: `JamStatusChips.kt` has `SendingPreview` (no chip pending today).
- `DESIGN.md`: M1 (line ~216) names the assistant dots as the only motion; status block text at ~854;
  `chip-status` tokens at line 70. Not inspected: a debug-only preview screen for the Jams tab (none found
  in `feature/past-jams`; `PastJamsStatesPreview.kt` are plain `@Preview`s).

## Technical approach
`:core:ui` (`com.bbbjam.core.ui.status`, no new dependency):
- `JamStatusChipUiModel` gains `pending: Boolean`. `jamStatusChips(current, enabled, pending: PendingChip? = null,
  events)` where `PendingChip(status: JamStatus, description: String)` marks that chip, sets its
  `description` and `enabled = false`. Default null keeps other callers unchanged.
- `JamStatusChipDefaults.style(...)` gets a `pending` input (or a separate `pendingStyle`) returning fill
  `surfaceRaised`, outline `textMuted`, label `textMuted`, no glyph, plus a new `outlineWidth` (2dp
  pending, hairline otherwise). Add pure `pendingOutlineAlpha(elapsedMillis, reduced): Float` that calls
  `loadingDotAlpha(elapsed, 0)` or returns `STATIC_ALPHA`.
- `StatusChip`: for a pending chip draw the outline in the draw phase (`drawBehind`/`drawWithContent`,
  alpha read inside the draw scope only, like `LoadingDots`), clock from `rememberInfiniteTransition`
  only when not reduced; make the clock helper `internal` and share it with `LoadingDots` rather than
  duplicating it. Add `liveRegion = Polite` on the pending chip's semantics.
`:feature:past-jams` (depends only on `:core:*`):
- `jamStatusUiModel`: compute pending from the latest Sending `JamStatusChange.target`, else PUBLICADA if
  `publishSending`; pass to `jamStatusChips`; delete the `sending` field of `JamStatusUiModel` and its draw
  in `JamStatusActions`. `busy` still disables everything.
- `publishUiModel` returns null while sending; remove `PublishUiModel.Publishing` and its branch in
  `JamsAdminViews`. Copy: replace `STATUS_SAVING`/`PUBLISHING` with the two announcement strings.
- Mutations: none added; `setJamStatus` and `publishSetlist` stay the repository functions (D-13) and the
  server still validates every write.

## Visual design impact (DESIGN.md)
Update: the M1 motion bullet adds a second exception, "the pending status chip's outline", with reasoning:
the user asked for it, it replaces a text line, it is bounded (only while a write runs, one chip, stops on
answer), uses `textMuted`, and is static at animator scale 0 (WCAG 2.2.2). Update the status-chip section
(~line 854) to remove the sending line and describe the pending chip, the 2dp outline and the announcement;
add a `chip-status` pending note. Update the `LoadingDots` wording and the architecture skill's line 1133
("only moving element") to mention the second user. No token added.

## Expected file changes
Edit: `JamStatusChips.kt`, `JamStatusChipDefaults.kt`, `JamStatusChipsUiModel.kt`, `LoadingDots.kt`
(share the clock), `JamStatusMapping.kt`, `JamStatusViews.kt`, `JamStatusUiModel.kt`, `PublishMapping.kt`,
`JamsAdminViews.kt`, `PastJamsCopy.kt`, `JamsAdminTest.kt`, `JamStatusChipsTest.kt`, `DESIGN.md`,
`.claude/skills/architecture/SKILL.md` (one line), `PROGRESS.md`, `feature_list.json`. Create a
`SendingPreview` update (pending Borrador) plus a pending Publicada preview in `JamStatusChips.kt`.

## Implementation plan and tasks
1. Pure tests first (red): `pendingOutlineAlpha` bounds, periodicity and reduced = 0.6; style for pending
   (`textMuted`, never `published`, width 2dp, outline present); `jamStatusChips` pending identity.
2. Implement `:core:ui` model, defaults, composable, shared clock, previews.
3. Presenter tests (Molecule, in `JamsAdminTest`): requested chip pending and others disabled while a
   change sends; PUBLICADA pending while a publish sends; cleared on success and failure; no pending for
   edit sending; selected chip unchanged. Update the three existing text asserts.
4. Remove the text lines and the unused fields/copy.
5. DESIGN.md, SKILL.md, then run the gate.

## Verification plan
- `CI=true ./init.sh` exits 0 with `konsist`, `detekt`, `ktlint` printed `wired`.
- Tests listed above run on the JVM. No Konsist rule is added; existing token rules cover colours (no
  `MaterialTheme.colorScheme`, no `Color(` literals).
- Device (manual, no write): install debug, open Jams tab, confirm the idle chips are unchanged (selected,
  disabled Publicada, vos descriptions). Do not trigger a status change or publish on the real Sheet and do
  not ask the user to add or delete Sheet jams. The pending state is verified by JVM tests and the two
  `@Preview`s (Android Studio render, animated and with animator scale 0 on the preview/emulator settings
  if available; otherwise the previews as static evidence).

## Evidence to capture
Test names and results, `init.sh` tool lines, the preview file names, a note that the device check was
idle-only and why, the DESIGN.md diff.

## Validator checklist
- [ ] Pending chip equals the requested status, including PUBLICADA on publish.
- [ ] No `Cambiando el estado…` / `Publicando…` visible text anywhere; announcement is a polite live region.
- [ ] Only `textMuted` is used for the animation; no new amber; tokens only.
- [ ] Static at scale 0; the transition is created only when pending and not reduced.
- [ ] Edit/delete lines untouched; failure card unchanged; no new mutation.
- [ ] DESIGN.md and the SKILL.md line updated; no device-only claim; no real Sheet write.
