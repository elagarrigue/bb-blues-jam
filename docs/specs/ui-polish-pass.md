# Feature Implementation Spec: Per-screen UI polish against DESIGN.md and the design prompt

## Source Feature

- `id`: ui-polish-pass
- `area`: ui
- `depends_on`: `project-hygiene-pass` (accepted 10 October 2026, commit c1c04b5)
- `status`: `not_started` at planning time
- `source`: `feature_list.json` (user request, 10 October 2026: "revisá la UI por mejoras, implementá las mejoras")

## Goal

Fix the concrete UI defects found in a per-screen audit (code read against `DESIGN.md`,
`bb-blues-jam-design-prompt.md` and `docs/design/screens/*.png`, plus a Pixel 5 run at font scale 1.0
and 2.0). Each task below cites its rule or its observed problem. No new product scope, no D-xx change.

## Non-Goals

- No new screen, component family, colour token, typography token or amber use. No Konsist
  allowlist change (`:feature:next-jam` stays `{key}`, `:feature:info` `{primaryAction, onPrimaryAction}`).
- No new mutation, repository function, Apps Script change, or `SetlistActionRegistry` change (D-13 is
  untouched: the extras removal keeps calling the same `removeExtraParticipant`).
- No item from "Deferred" or "Proposals needing user decision". No light theme, onboarding, self-signup.
- No verification-rule change (`init.sh`, detekt, ktlint, lint config, Konsist rules).

## Job Story

When I read Próxima jam standing in a dark bar, with a large system font, or edit a setlist as the
admin, I want every label to stay readable, every control to look like what it is (enabled, disabled,
removable) and every section to be visually grouped, so I never misread a slot or tap the wrong thing.

## Users And Permissions

Musicians (read-only) and the admin (same screens plus controls, D-15). No permission change; the
local admin flag still only decides what is drawn, Apps Script validates every write.

## Audit Method (what was inspected)

- Device: Pixel 5 (1080x2340), debug build, flags `demoUpcomingJam`, `demoUpcomingJamDraft`,
  `debugAdmin` toggled; font scale 1.0 and 2.0; Wi-Fi/data off for offline and error states. Screens
  captured: Próxima jam musician + admin (published real jam 31 Oct, demo draft offline), expanded row
  (musician/admin), lineup editor, extras form, key picker, song detail, offline notice, demo draft
  card, Anteriores list + error, past jam detail, Info (logged in/out), admin login. `local.properties`
  restored afterwards; no mutation tapped, nothing written to the Sheet. Screenshots live only in the
  planner's session scratchpad; the implementer recaptures "before" shots (Verification V1).
- Not captured on device (read in code only): add-song picker, assign-musician flow, publish
  confirmation/failure card, pending/failure cards, removal confirmation. They need a write or a real
  admin state to reach.
- Code read: `NextJamScreen.kt`, `NextJamListContent.kt`, `NextJamCopy.kt`, `AdminControls.kt`,
  `AdminControlsDefaults.kt`, `LineupEditorControl.kt`, `MoveControls.kt`, `ExtraParticipantControls.kt`,
  `ExtraParticipantFormState.kt`, `ExtraParticipantUiMapping.kt`, `ExtraParticipantCopy.kt`,
  `NextJamAdminMapping.kt` (rows), `NextJamUiModel.kt`, `AssignMusicianScreen.kt`/`Copy`/`Defaults`,
  `AddSongScreen.kt` (field colours), `:core:ui` `LineupPanel.kt`, `LineupPanelUiModel.kt`,
  `LineupPanelMapper.kt`, `LineupPanelCopy.kt`, `TabBar.kt`, theme files; `:feature:info`
  `InfoScreen.kt`, `InfoCopy.kt`, `AdminLoginDefaults.kt`; test file lists of `:core:ui`,
  `:feature:next-jam`, `:feature:info`.

## Prioritized Improvements (the tasks)

P1 = broken or misleading at a supported setting; P2 = inconsistency with a written rule; P3 = copy/doc.

| # | Pri | Problem (evidence) | Rule | Fix |
|---|---|---|---|---|
| U1 | P1 | Font scale 2.0: tab label `Próxima jam` wraps and is clipped at the screen's left edge; icon pushed up (Próxima jam, every tab) | DESIGN Accessibility (48dp, readable), Bottom bar "label always shown" | `TabBar` label `maxLines = 1`, `overflow = Ellipsis`, `textAlign = Center`; constant `TabBarDefaults.LABEL_MAX_LINES = 1` |
| U2 | P1 | Font scale 2.0, admin expanded row: `LIBRE` breaks letter by letter (`LI / B…`, `LIB / RE`) between the instrument and `Anotar` | DESIGN Song row expanded "Long names wrap to two lines, then …" | In `LineupPanel.Line`, when `action != null`, put instrument and detail in a `Column(Modifier.weight(1f))` (instrument above detail) so the detail gets the full width; lines without an action keep today's single row (musician view unchanged) |
| U3 | P1 | Disabled controls look enabled: lineup `−` disabled glyph is `textMuted` (#D6C4AC) next to enabled `text` (#E2E2E9); disabled `Subir`/`Bajar` stay underlined in `textMuted`. Also the `surfaceRaised` button fill is ~1.05:1 on the `surface` row, so enabled buttons have no visible boundary | DESIGN admin-adjust-lineup "disabled controls are muted"; Accessibility "not brightness alone" | `AdminControlsDefaults.lineupEditor()`: enabled button = `surfaceRaised` fill + hairline `border` outline + `text` glyph; disabled = no fill, no outline, `textMuted` glyph. `move()`: add `underlineDisabled = false`; a disabled move action draws no underline. Shape now differs, not only brightness |
| U4 | P1 | Extras editor (admin): extras listed twice (panel `OTROS` "+ bajo Juan" and editor "Juan · bajo"); its `Quitar` floats mid-row (weight 0.55) unlike `Liberar`; name/instrument fields run edge to edge of the card with placeholder-only 12sp labels that vanish when typing and a `textMuted` border; submit label equals the opener (`Agregar a Otros`); no way to close the form; blank fields can be submitted | DESIGN Extra participants "inline OTROS editor after the lineup slots… visible Quitar"; DESIGN Admin controls "no false affordance", 48dp | (a) `Quitar` moves onto the panel's `OTROS` lines, end-aligned like `Liberar`, colour `text`; the editor stops drawing its own list. (b) Form fields inset `spacing.md` like the rest of the row, Material 3 `OutlinedTextField` with a visible `label` and explicit colours from a new `AdminControlsDefaults.extraField()` (same roles as `AdminLoginDefaults.field()`, no amber). (c) New `Event.Cancel` + `Cancelar` action closes the form and clears it. (d) Submit label `Agregar` (`NextJamCopy.SUBMIT_EXTRA`), enabled only when both trimmed values are non-blank (`ExtraParticipantEditorUiModel.submitEnabled`); a disabled submit is muted with no underline (as U3) |
| U5 | P2 | The musician hint "Para tocar, anotate en la jam: la organización te suma a un tema." is shown to the admin, who *is* the organization | DESIGN Song row expanded (hint addresses musicians); copy must make sense to the reader | `AdminState.lineupPanel(...)` returns the panel with `hint = null`. Musician rows and the song detail keep it |
| U6 | P2 | Lineup panel sections spaced `xs` like lines: the hint sits on top of `CUPOS CUBIERTOS`, and `OTROS` on the last filled line | DESIGN Layout rhythm; Shapes "separation from elevation" | In `LineupPanel`, every section heading after the first gets `padding(top = spacing.sm)` (so `xs + sm` between groups, `xs` inside) |
| U7 | P2 | Lineup editor lines are wider than every other block in the row (instrument starts ~6dp left of the panel, `+` ends ~6dp past it) | Consistent row inset (`spacing.md`, as panel and actions) | `LineupEditor` `Editing` column gets `padding(horizontal = spacing.md)`; the heading's own start padding goes |
| U8 | P2 | Collapsed row title is cut to one line ("Sweet Little A…", "Walking Thru …" at 2.0; long titles even at 1.0) | Design prompt reading priority (title above artist); DESIGN "one line where possible"; add-song and past detail already allow two | `TitleLine` title `maxLines = 2` through a `private const val TITLE_MAX_LINES = 2` in `NextJamScreen.kt`; the key and chevron stay vertically centred on the title |
| U9 | P2 | Headings not marked for screen readers: Info title and its five card titles, Próxima jam header date | DESIGN screens use "a heading" for titles (Anteriores, detail, login all do) | `Modifier.semantics { heading() }` on those texts |
| U10 | P2 | Info admin line: `Salir del modo admin` / `Entrar como admin` are drawn exactly like the status `Modo admin activo` (caption, muted, no underline) | DESIGN "underlined like the other secondary text actions" | `CaptionAction` text gets `TextDecoration.Underline`; the status line stays plain |
| U11 | P2 | Header, admin status block and filter bar are spaced `sm`, the same as song rows, so the three groups do not read as groups (demo draft admin: note, `Publicar lista` and `Filtrá por cupo libre` stacked tight) | Design prompt hierarchy; DESIGN Layout order | Header item and admin status item get `padding(bottom = spacing.sm)` (group gap `md`); rows keep `sm` |
| U12 | P2 | Assign musician: submit is a Material `Button` with its default pill shape (every other button is `shapes.md`); field leaves unfocused border, label and container on Material defaults and uses `border` as the *focused* border; `AssignMusicianDefaults.instrumentName` duplicates `Instrument.fullName()` | D-17 "set colours explicitly from tokens"; architecture "UI copy shared … lives with the component" | `shape = BluesJamTheme.shapes.md`; field colours from new `AssignMusicianDefaults.field()` (= `AdminLoginDefaults.field()` roles: focused border `text`, unfocused `border`, label `textMuted`, container `surface`); `slotName` uses `Instrument.fullName()` and `instrumentName` is deleted |
| U13 | P3 | `SLOT_TAKEN` says "intentá de nuevo" where every other card says "probá de nuevo"; `CLEAR_STALE` says "Volvé a la próxima jam…" on a card that is drawn *on* Próxima jam | D-12 consistency | `SLOT_TAKEN = "Ese cupo ya está ocupado. Actualizá la lista y probá de nuevo."`; `CLEAR_STALE = "Ese cupo ya no está ocupado. Actualizá la lista para ver cómo quedó."` (the assign Gone screen's `GONE_MESSAGE` is on another screen and stays) |
| U14 | P3 | `DESIGN.md` lines 659 and 663 show `Publicando�` / `Guardando�` (mojibake of "…") | Doc accuracy | Replace with `Publicando…` / `Guardando…` |

## Acceptance Scenarios

1. **Large font**: Given font scale 2.0, when any tab is shown, then each tab label is one centred line
   (ellipsized if needed) fully inside its item; when the admin expands a row, `LIBRE`/names are never
   broken mid-word and `Anotar`/`Liberar`/`Quitar` stay at the line's end.
2. **Disabled controls**: Given the lineup editor with a filled-only instrument, then its `−` has no
   fill and no outline and the enabled `+` beside it has a fill and an outline; given the first row,
   `Subir` is not underlined and has disabled semantics.
3. **Extras**: Given an admin row with extra Juan/bajo, then `OTROS` shows "+ bajo  Juan  Quitar" once
   (described "Quitar a Juan, bajo"), and no second list appears among the actions; tapping it calls
   `removeExtraParticipant(date, songId, 1, "Juan", "bajo")` exactly as today. Opening `Agregar a Otros`
   shows two labelled, inset fields, `Agregar` (disabled until both are filled) and `Cancelar`;
   `Cancelar` closes and clears the form without any repository call.
4. **Hint**: Given the admin expands a row with an open slot, then no "Para tocar…" hint is drawn; a
   musician still sees it in the row and in the song detail.
5. **Unchanged**: Musician rows, colours, amber roles, the strip, the filter, list states and every
   write path behave as before; `ContrastTest` and Konsist pass unchanged.

## Technical Approach

- `:core:ui` (`com.bbbjam.core.ui.lineup`): `LineupLineUiModel` gains `actionDescription: String? = null`
  (read on the action button's semantics, so the participant-specific label survives).
  `Lineup.toLineupPanel` gains `onRemoveExtra: ((ordinal: Int, ExtraParticipant) -> Unit)? = null`
  (1-based ordinal in the list passed as `extras`); when present each extra line gets
  `actionLabel = LineupPanelCopy.REMOVE_EXTRA` ("Quitar"), `actionDescription = "Quitar a {name},
  {instrument}"` (move `extraRemoveAccessibilityLabel` into `LineupPanelCopy.removeExtraDescription`)
  and a handler keyed `remove-extra:{ordinal}:{name}:{instrument}`. `Line` draws the action colour
  `text` for every non-open kind (already the case). U2, U6 also live here. Musician callers pass no
  handler, so their models are equal to today's (existing mapper tests must stay green unchanged).
- `:feature:next-jam`: `AdminState.lineupPanel` passes `onRemoveExtra` that forwards to the existing
  `onExtraEvent(date, songId, Event.Remove(ordinal, name, instrument))`, and sets `hint = null` (U5).
  `ExtraParticipantEditorUiModel` drops `extras` (now drawn by the panel), gains `submitEnabled` and
  `Event.Cancel`; `ExtraParticipantFormState.handle(Cancel)` sets `formKey = null`, clears both values
  and `consumed`. `submit` returns early when either trimmed value is blank. Copy additions in
  `NextJamCopy` (`SUBMIT_EXTRA = "Agregar"`, reuse `CANCEL`). Field colours in `AdminControlsDefaults`.
- Presenters stay the only decision makers; composables only draw flags. No new Koin binding, module
  or Gradle dependency. Allowed dependencies unchanged (`:feature:next-jam` → `:core:ui`, `:core:data`).

## Expected File Changes

- `core/ui/.../lineup/LineupPanel.kt`, `LineupPanelUiModel.kt`, `LineupPanelMapper.kt`, `LineupPanelCopy.kt`
- `core/ui/.../nav/TabBar.kt`, `TabBarDefaults.kt`
- `core/ui/src/test/.../lineup/LineupPanelMapperTest.kt`, `nav/TabBarDefaultsTest.kt`
- `feature/next-jam/.../NextJamScreen.kt`, `NextJamListContent.kt`, `AdminControlsDefaults.kt`,
  `LineupEditorControl.kt`, `MoveControls.kt`, `ExtraParticipantControls.kt`, `ExtraParticipantFormState.kt`,
  `ExtraParticipantUiMapping.kt`, `ExtraParticipantCopy.kt` (deleted or emptied), `NextJamUiModel.kt`,
  `NextJamCopy.kt`, `AssignMusicianScreen.kt`, `AssignMusicianDefaults.kt`, previews that build these models
- `feature/next-jam/src/test/...`: `NextJamAdminTest.kt`, `AdminControlsDefaultsTest.kt`, a new
  `AssignMusicianDefaultsTest.kt` (planner grep: no test asserts `SLOT_TAKEN`/`CLEAR_STALE` text;
  `extraRemoveAccessibilityLabel` is used only by `ExtraParticipantControls.kt` and `NextJamAdminTest`)
- `feature/info/.../InfoScreen.kt`
- `DESIGN.md` (U14 plus the as-built notes below), `PROGRESS.md`, `feature_list.json`

## Visual Design Impact

Tokens only (D-17); no literal `dp`/`sp`/colour in features (Konsist). New neutral roles used: `border`
outline on enabled lineup buttons and on the extras fields. Amber unchanged: `Anotar` stays `slotOpen`
inside `:core:ui`; extras `Quitar` is `text` (it was `error`; it now matches `Liberar`, the other
"remove a person" action; `Quitar de la lista` stays `error` because it removes the song).

## Durable Documentation Impact

- `DESIGN.md`: update — U14; Extra participants paragraph (Quitar on `OTROS` lines in `text`, labelled
  fields, `Agregar`/`Cancelar`); admin-adjust-lineup (enabled outline, disabled no fill/no underline);
  Song row expanded (hint is musician-only; title up to two lines); Bottom bar (one-line label).
- `.claude/skills/architecture/SKILL.md`: update — one sentence under admin-edit-extra-participants that
  the extras' `Quitar` is a `LineupPanel` line action (`onRemoveExtra`, `actionDescription`).
- `AGENTS.md`, `ARCHITECTURE.md`, `CONSTRAINTS.md`, `CONTEXT.md`, domain docs: not needed (no rule,
  module or term changes).

## Implementation Tasks (in order)

1. V1 "before" screenshots (below). 2. U14, U13 (copy/doc; update any test asserting those strings).
3. U1. 4. U2, U6 and the `:core:ui` part of U4 with mapper tests. 5. U4 feature part, U5 with
presenter tests. 6. U3, U7. 7. U8, U11. 8. U9, U10. 9. U12. 10. Docs. 11. `CI=true ./init.sh`.
12. V2 "after" screenshots and comparison. 13. Evidence in `feature_list.json` and `PROGRESS.md`.

## Verification Plan

JVM (in `./gradlew check`):
- `LineupPanelMapperTest`: with `onRemoveExtra`, extra lines carry `actionLabel "Quitar"`,
  `actionDescription "Quitar a Juan, saxo"`, distinct per participant, and activating line 2 calls the
  handler with ordinal 2; without it, models equal today's (existing assertions untouched).
- `NextJamAdminTest`: admin panel `hint == null` while a musician's row keeps `LineupPanelCopy.HINT`;
  extras removal through the panel line calls `removeExtraParticipant` once with the ordinal, name and
  instrument; editor `submitEnabled` false for blank/whitespace, true for both filled; `Submit` with a
  blank field makes no repository call; `Cancel` closes the form and clears values with no call;
  musician rows still expose no editor and no extra action.
- `AdminControlsDefaultsTest`: enabled vs disabled lineup button styles differ in fill and outline (not
  only glyph); move style `underlineDisabled == false`; `extraField()` has no amber role (compare with
  `BluesJamColors` non-amber roles, as `PastJamsDefaultsTest` does).
- `AssignMusicianDefaultsTest`: `field()` roles as in `AdminLoginDefaultsTest`; `slotName(GUITAR, 2)`
  is "Guitarra 2", `slotName(BASS, 1)` is "Bajo".
- `TabBarDefaultsTest`: `LABEL_MAX_LINES == 1`.
- `ContrastTest`, Konsist (17 rules) unchanged and green.
- Gate: `CI=true ./init.sh` exits 0 with `konsist`, `detekt`, `ktlint` all `wired`; test count = baseline
  (657) plus the new tests, 0 failures.

Device (manual, Pixel 5; not part of the gate). Never tap a write control against the real jam; use the
demo jam offline for admin states. Back up and restore `local.properties`; restore font scale 1.0 and
Wi-Fi/data afterwards.
- V1/V2 before/after, same scroll positions, saved to the session scratchpad (not committed):
  (a) Próxima jam musician top at 1.0 and 2.0 (U1, U8, U9, U11); (b) admin expanded row with an extra,
  1.0 and 2.0 (U2, U4, U5, U6); (c) lineup editor open (U3, U7); (d) extras form open, empty and filled
  (U4); (e) first row's move block (U3); (f) Info bottom logged in and out (U10); (g) assign musician
  screen reached via `Anotar` on the demo jam, without submitting (U12).
  States: `debugAdmin=true` for admin; real jam online for published; airplane mode + `pm clear` with
  `demoUpcomingJam`+`demoUpcomingJamDraft` for the demo draft. Do not tap `Agregar`, `Quitar`,
  `Liberar`, `Anotar` submit, `+`/`−`, `Subir`/`Bajar` or `Publicar lista` while online.
- `uiautomator dump` on (b): one `Quitar` node per extra with content-desc `Quitar a {name}, {instrument}`;
  no "Para tocar" text node in the admin row; on (c) the disabled `−` reports `enabled="false"`.
- Headings (U9) have no `uiautomator` attribute: verify by code review; TalkBack heading navigation is
  an optional human check, recorded as owed if not done.

## Evidence To Capture

Gate output summary (exit, three `wired`, test totals), new/changed test names, the V1/V2 pairs listed
by item number with a one-line observation each, the `uiautomator` excerpts, `git diff --check`.

## Deferred (with reasons)

- **D1 Jam summary counter** ("32 anotados · 18 cupos libres", DESIGN.md, deferred by
  `instrument-filter-chips` and never built): a new header element with an amber (`slotOpen`) use and its
  own counting rules; its own slice (`jam-summary-counter`) in `feature_list.json`, after confirming it is
  still wanted.
- **D2 Tabular position numbers**: `01`, `02`, `10`, `11` have different widths, so titles start at
  different x (Próxima jam and past detail). Needs proof that the bundled Barlow Condensed TTF honours
  `tnum`, or a measured fixed width; small but uncertain.
- **D3 Song detail key block**: `TONALIDAD` floats ~24dp above the 96sp key because of `keyDisplay`'s
  line height; a `LineHeightStyle` trim changes a K1-approved token, so it needs its own check.
- **D4 Admin login**: `Mostrar` sits almost against the field's end; mockup's explanatory line ("Solo para
  la organización…") would be new copy. Low value.
- **D5 Screens not seen on device** (add-song picker, publish confirmation/failure, pending/failure
  cards, removal confirmation): audited in code only; a follow-up device pass can extend V2.

## Proposals Needing User Decision (not tasks)

- **P-A `Todos` chip amber by default.** With no filter, `Todos` is always an amber block, beside the
  amber `Publicar lista` and the keys. DESIGN.md says amber marks what the user acts on now; the default
  "no filter" state is not an action. Option: `Todos` selected = `surfaceRaised` + `text` with a check,
  amber only for selected instruments. Changes an approved filter decision (`instrument-filter-chips`).
- **P-B Draft badge visibility.** `BORRADOR`/`EN PREPARACIÓN` use `surfaceRaised` on `surface` (about
  1.05:1), so the badge reads as plain text. Option: a hairline `border` outline. Changes the
  `badge-draft` token definition in DESIGN.md.
- **P-C Venue address in the header.** The design prompt asks for venue name *and address*; the Sheet has
  one `lugar` string (`Jam.venue`). Needs a schema decision (D-04, `docs/sheet-schema.md`).
- **P-D Grouping the admin row actions.** An expanded admin row stacks seven text actions under the
  musician content with no separation. Option: an `EDITAR TEMA` caption heading before `Cambiar
  tonalidad`. New copy and a layout change to an approved order (D-15 "add, do not rearrange").

## Validator Checklist

- Every U1–U14 is done or explicitly reported as not done with a reason; nothing from Deferred or
  Proposals was implemented.
- No literal dp/sp/colour in features; no new amber role; Konsist allowlist unchanged; no ViewModel; no
  feature-to-feature dependency; no new mutation or registry change.
- Musician models unchanged except U8 (drawn only) and U9/U11 (drawn only); existing musician tests
  pass without edits.
- Extras removal still calls the same repository function with the same arguments; accessible
  `Quitar a {name}, {instrument}` preserved.
- All new copy is Rioplatense *vos* and lives in `…Copy` objects.
- Gate green with three `wired`; before/after evidence present for each device-checked item;
  `local.properties`, font scale and connectivity restored.
