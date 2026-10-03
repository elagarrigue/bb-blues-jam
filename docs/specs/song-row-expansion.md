# Feature Implementation Spec: Expand a song row in place

## Source Feature

- `id`: song-row-expansion
- `area`: feature-next-jam
- `depends_on`: `instrument-strip-component` (`accepted`)
- `status`: not_started at planning time
- `source`: `feature_list.json` (entry and notes, incl. the two follow-ups from the strip validation)

## Goal

On Próxima jam, tapping a song row's header expands it in place: the artist, then **open slots
first**, then filled slots with instrument and musician, then the extra participants ("Otros").
Tapping the header again collapses it. Several rows can be expanded at once, and the expansion of a
position survives a background refresh. Read-only: nothing inside the panel is interactive.

## Non-Goals

- Admin anything: tapping an open slot to assign (`admin-assign-musician`), clearing a filled slot
  (`admin-clear-slot`), `AdminSession`, `isAdmin`/`canEdit`, a slot index for events. No mutation
  exists here, so D-13 registers nothing: expansion is local presentation state, not data.
- The link to the song detail ("acceso al detalle completo", design prompt §1) — `song-detail-screen`.
- Filter bar, counts, jam counter (`instrument-filter-chips`); list-states; bottom navigation and
  per-tab state (`bottom-navigation`). Past jams reusing the panel (`past-jam-detail` decides).
- No `:core:model`, `:core:data`, `:app`, `init.sh` or Konsist change. No new colour token.

## Job Story

When a song's strip tells me there is room, I want to open it and read who is already on it and
what is free, in a clear order, without leaving the list, so I know what I would be joining.

## Users And Permissions

Anyone, read-only. The panel draws nothing an admin would act on; admin controls arrive later on
top of the same layout (D-15).

## Decisions

**1. State lives in `NextJamPresenter`, not in a child presenter.** One
`var expanded by rememberSaveable(stateSaver = ExpandedRows.Saver) { mutableStateOf(ExpandedRows.NONE) }`
in `present()`, passed into the existing pure mapping as
`JamsSnapshot.toUiModel(today, expanded = ExpandedRows.NONE, onToggle: (LocalDate, Int) -> Unit = { _, _ -> })`
(defaults keep the existing direct-call tests). Reasoning: the reference sketch's `SongRowPresenter`
called in a `map {}` keys its `remember` by **call order**, so removing or inserting a row would
shift expansion to a neighbour unless every call is wrapped in `key(position)`; a child presenter
pays off only when a row has its own dependencies (the admin slot mutations), which is a later
slice's call. A single state object in the parent keeps the mapping pure and JVM-testable.
- `internal data class ExpandedRows(val jamDate: LocalDate?, val positions: Set<Int>)` in
  `:feature:next-jam`, with `isExpanded(date, position)` (false unless `date == jamDate`) and
  `toggle(date, position)` (another date → `ExpandedRows(date, setOf(position))`). **Keyed by
  position** (the Sheet's `posicion`, never renumbered, already the `LazyColumn` key), so a refresh
  that fills a name or drops another row keeps the right row open; a new upcoming jam reads as all
  collapsed without recreating state. Stale positions are harmless and not pruned.
- **Never recreate the state object** (no `remember(snapshot)`/`remember(jam.date)` around it):
  row `EventHandler`s have no key, so all compare equal and Compose may keep an earlier row's
  handler; that handler must still write through the same state (prototype test proves it).
- `rememberSaveable` (with a `Saver` to `[dateString, ArrayList<Int>]`): survives rotation and other
  configuration changes; in Molecule there is no registry, so it behaves as `remember` (prototyped).
  The tab switch in `TemporaryTabs` still drops it (accepted; `bottom-navigation` owns per-tab state).
- **Many expanded at once** (entry, DESIGN.md, design prompt): toggling one row never touches another.
- Events: `SongRowUiModel.events: EventHandler<SongRowUiModel.Event>` with
  `data object ToggleExpanded`; the handler calls `onToggle(jam.date, position)`. No state in
  composables; the screen only forwards the event.

**2. `SongRowUiModel` gains** (after `instruments`, no defaults): `artist: String`,
`isExpanded: Boolean`, `stateDescription: String`, `toggleLabel: String`,
`lineup: LineupPanelUiModel`, `events: EventHandler<Event>`. The panel model is built for every
row (cheap, and the whole setlist stays visible to the phase 2 assistant as data, D-02).

**3. Panel component in `:core:ui`, new package `com.bbbjam.core.ui.lineup`** — not a reuse of the
strip. Reasoning: the expanded panel reorders (open first, the strip is column order by approved
decision), uses full instrument names, and gives each slot a full-width line; past-jam-detail and
song-detail may draw lineups too (D-03 forbids sharing from a feature). Files:
- `LineupPanelUiModel.kt`: `LineupPanelUiModel(openSlots, filledSlots, extras: List<LineupLineUiModel>, noOpenSlotsNote: String?)`
  and `LineupLineUiModel(instrument: String, detail: String, contentDescription: String, kind: InstrumentChipKind)`
  (reuses the strip's three kinds).
- `LineupPanelMapper.kt`: pure `fun Lineup.toLineupPanel(extras: List<ExtraParticipant>)`. Open
  slots in lineup order, then filled in lineup order (a stable partition — two guitars keep their
  column order), then extras in `Otros` order. Guitars unnumbered. Descriptions are exactly the
  strip's ("Guitarra: libre", "Guitarra: Tincho", "Otros: saxo, Juan").
- `LineupPanelCopy.kt` (internal) — copy table below. Instrument names come from
  `InstrumentStripCopy.name()`, made `internal` (visibility only; no copy change).
- `LineupPanelDefaults.kt` (internal): line fill/glyph from `InstrumentStripDefaults.style(kind)`,
  plus `detailColor(kind, colors)`: open → `slotOpen`, filled → `text`, extra → `textMuted`.
- `LineupPanel.kt`: `@Composable fun LineupPanel(model, modifier)` and
  `@Composable fun ExpandIndicator(expanded: Boolean, modifier)` (`Icons.Filled.KeyboardArrowDown`
  from the declared `material-icons-core`, rotated 180° when expanded via a named constant,
  `textMuted`, `contentDescription = null`). Features cannot reach the icons dependency, so the
  chevron lives here. `@Preview`s: mixed + extra, all open, all filled with a long name, empty lineup.

**4. Panel layout and what an open slot looks like to a musician (needs approval, I2).**
`Column(spacedBy(spacing.xs))`: section heading (caption, `textMuted`, drawn uppercase,
`semantics { heading() }`) shown only when its section is non-empty: "Cupos libres", "Cupos
cubiertos", "Otros". Each line: `Row(fillMaxWidth, background(fill, shapes.sm), padding(sm × xs),
spacedBy(sm))` = glyph (open dot / filled check / extra "+", sized from `body` font size like the
strip) + instrument (`body`, style text colour) + detail (`body`, `detailColor`, `weight(1f)`,
`maxLines = 2`, ellipsis); `clearAndSetSemantics { contentDescription = line.contentDescription }`.
**Open slots look available, not tappable:** amber text on the 15% amber fill, the dot and
"LIBRE" — but no click, no ripple, no `Role`, no chevron/plus, no elevation, no "Pedir cupo"
(Stitch invented that; self-signup is out of scope, D-05). A control that looks tappable and does
nothing is a false affordance; DESIGN.md's "tappable" belongs to the admin view. **Compact lines,
no 48dp minimum**: the prototype with 48dp lines made one 7-slot panel ~1610 px of the Pixel 5's
2340 px; non-interactive content needs no touch target. `admin-assign-musician` decides how its
targets reach 48dp. Zero open slots → the caption `noOpenSlotsNote` instead of the open section.
Optional hint (H1) after the open lines, caption `textMuted`, only when ≥ 1 open slot.

**5. Row layout.** `SongRow` = `Surface` → `Column(Modifier.animateContentSize())` →
(a) **header** `Column` (`fillMaxWidth`, `clickable(onClickLabel = row.toggleLabel, role =
Role.Button)`, `semantics { stateDescription = row.stateDescription }`, the existing padding and
spacing): the title line (position, title, key, then `ExpandIndicator`), then **collapsed: the
strip; expanded: the artist** (`body`, `textMuted`; omitted when blank); (b) when expanded,
`LineupPanel(row.lineup, padding(start/end/bottom = spacing.md))` **outside** the clickable.
Extract the title line into its own composable (detekt `LongMethod` 60 hit the prototype) and
build preview rows with a helper (the preview also hit `LongMethod`).
- **Strip replaced by the panel when expanded (I1).** The panel is a superset of the strip; showing
  both duplicates every slot on screen and for TalkBack. As in Stitch's expanded row.
- Only the header toggles, so a stray tap on the panel never collapses it, and future admin taps on
  slots never fight the row's click. Header ≥ 56dp already (key line), no new min-height.
- **Accessibility, read once.** Collapsed: the header is one merged button node whose children are
  position, title, "Tonalidad B" and one description per chip, then state "contraído" and the
  action "ver los cupos". Expanded: the header node reads position, title, key, artist, "expandido";
  the panel's headings and one node per line follow as separate nodes. Each slot is spoken once in
  each state. Prototype evidence: Robolectric merged tree and the Pixel 5 uiautomator dump (below).
- **Motion:** `animateContentSize()` only; the list key is already `position`. The tapped header's
  top and everything above it must not move; rows below slide by the animated height. No
  auto-scroll (a scroll the user did not ask for is itself disorienting). Measured in the
  prototype: row 1 header top 420 px and title `[180,475][785,554]` identical collapsed/expanded.

**6. D-17.** Every amber read stays inside `:core:ui` (`InstrumentStripDefaults`, the panel
defaults). `:feature:next-jam` still reads only `key`; `AMBER_ROLE_ALLOWLIST` stays
`{"feature/next-jam": {"key"}}`. Chevron and headings are `textMuted`, never amber.

**7. Tests and the follow-ups.**
- `ContrastTest` follow-up (cheap, included): `open slot on its chip fill` composites
  `InstrumentStripDefaults.style(InstrumentChipKind.OPEN_SLOT).fill` instead of rebuilding it from
  `OPEN_FILL_ALPHA`; new pair `text on a filled slot` (`text` vs `slotFilled`, expected ≈ 9.5,
  record the measured value).
- Compose semantics harness follow-up: **proposed, needs approval (T1)** — see below.

## Copy (user approval requested, C1; Rioplatense, D-12)

Row strings in `NextJamCopy`; panel strings in `LineupPanelCopy`.

| Where | Text |
|---|---|
| Row state (a11y `stateDescription`) | `expandido` / `contraído` |
| Row action (a11y `onClickLabel`; TalkBack: "Presioná dos veces para …") | `ver los cupos` / `ocultar los cupos` |
| Section headings (drawn uppercase) | `Cupos libres`, `Cupos cubiertos`, `Otros` |
| Open line | `<Instrumento>` + `LIBRE` (e.g. `Guitarra  LIBRE`) |
| Filled line | `<Instrumento>` + name as in the Sheet (`Guitarra  Tincho`) |
| Extra line | `+` + instrument as typed + name (`+ saxo  Juan`) |
| No open slot | `No quedan cupos libres.` |
| Line descriptions | the approved strip ones: `Guitarra: libre`, `Guitarra: Tincho`, `Otros: saxo, Juan` |
| Optional hint H1 (after open lines) | `Para tocar, anotate en la jam: la organización te suma a un tema.` |

"contraído" is the word Android's Spanish TalkBack uses for collapsed; "colapsado" is the
alternative if the user prefers it. H1 echoes the approved Info "Cómo sumarte" text.

## Acceptance Scenarios

1. Given a row collapsed, when its header is tapped, then it shows the artist and the panel, the
   strip is hidden, the chevron points up, and the state description is "expandido".
2. Given rows 1 and 3 expanded, when row 3 is tapped, then only row 3 collapses.
3. Given lineup [GTR open, GTR "Tincho", BAJO "Nico", BAT open, VOZ open, ARM "Mono"] and extra
   `Juan (saxo)`, the panel lists Guitarra, Batería, Voz (LIBRE), then Guitarra Tincho, Bajo Nico,
   Armónica Mono, then `+ saxo Juan`.
4. Given row 2 expanded, when a refresh fills a name on row 2 and drops row 1, then row 2 stays
   expanded and shows the name; when the upcoming jam's date changes, every row is collapsed.
5. All-filled lineup → "No quedan cupos libres." and the filled section; empty lineup, no extras →
   the note only.
6. Tapping a panel line does nothing; tapping the header collapses.
7. The tapped header and the rows above it do not move when it expands or collapses.

## Repository Research

**Inspected:** `AGENTS.md`, `PROGRESS.md` (sessions 048–056), `feature_list.json` (this entry,
`instrument-filter-chips`, `song-detail-screen`, `admin-assign-musician`, `admin-clear-slot`),
`DESIGN.md`, `bb-blues-jam-design-prompt.md` §1–2 and rules, `docs/design/README.md`, Stitch
`next-jam-expanded.png` (layout only; "Pedir cupo", "Pedíselo a Fede" and numbered guitars
rejected), `CONTEXT.md`, bitácora D-02, D-03, D-05, D-07, D-12, D-15, D-17, D-18, architecture
`SKILL.md` and `references/presenter-pattern.md`, `docs/specs/instrument-strip-component.md`,
`feature/next-jam` sources and tests, `core/ui` strip sources, `ContrastTest`, `core/model`
`JamSong`/`Lineup`/`Slot`/`ExtraParticipant`, `gradle/libs.versions.toml`, module build files,
`TemporaryTabs.kt`, the manifest (no orientation lock), `InfoCopy`, earlier specs on Robolectric.

**Prototype** (throwaway clone of `0667f63` in the session scratchpad): Decisions 1–5 built; a
Molecule test of toggle → two expanded → refresh (row removed, name filled) → stale first-model
handler → new jam date passed with `rememberSaveable`. With T1's dependencies, a Robolectric
(`@Config(sdk = [35])`) test showed the collapsed header as one merged `Role.Button` node with
`StateDescription 'contraído'` and the chip descriptions once, and expanded as `'expandido'` with
seven separate `Guitarra: libre`… nodes; `testImplementation` of `ui-test-manifest` suffices (no
debug-APK change). `build check` (detekt of next-jam excluded until the preview was split): Konsist
15/15, lint clean; first Robolectric run ~45 s (downloads android-all once), then ~6 s per test
class. Pixel 5 with demo data: screenshots and uiautomator dump (header clickable, children carry
the descriptions, panel nodes separate). **Not prototyped:** compact lines (prototype used 48dp),
H1, rotation survival on the device, the reduced-motion setting. Device restored to a HEAD build
(`upcoming none`).

## Expected File Changes

- `core/ui/src/main/kotlin/com/bbbjam/core/ui/lineup/` — create the five files of Decision 3.
- `core/ui/.../strip/InstrumentStripCopy.kt` — `name()` private → internal.
- `core/ui/src/test/.../lineup/LineupPanelMapperTest.kt`, `LineupPanelDefaultsTest.kt` — create;
  `core/ui/src/test/.../theme/ContrastTest.kt` — modify (Decision 7).
- `feature/next-jam/.../ExpandedRows.kt` — create; `NextJamUiModel.kt`, `NextJamPresenter.kt`,
  `NextJamCopy.kt`, `NextJamScreen.kt` — modify; tests `ExpandedRowsTest.kt` (create),
  `NextJamPresenterTest.kt` (modify; expected rows gain the new fields).
- Only if T1: `gradle/libs.versions.toml` (+ `robolectric` 4.16, `androidx-compose-ui-test-junit4`,
  `androidx-compose-ui-test-manifest`, BOM-managed), `feature/next-jam/build.gradle.kts`
  (`testImplementation` ×3, `testOptions.unitTests.isIncludeAndroidResources = true`),
  `feature/next-jam/src/test/.../SongRowSemanticsTest.kt`.

## Visual Design Impact

UI: yes. Tokens only; Stitch for layout. States: collapsed, expanded mixed + extra, all open, all
filled (no-open note), empty lineup, long name (two lines), two rows expanded, font scale 1.3.

## Durable Documentation Impact

- `DESIGN.md` — "Song row, expanded": strip replaced by the panel, headings, open slots available
  but not interactive for musicians (tappable only in admin view), compact lines, header-only
  toggle, chevron, motion rule; close nothing else.
- Architecture `SKILL.md` — `:core:ui` gains `lineup/` (`LineupPanel`, `toLineupPanel`,
  `ExpandIndicator`); expansion state in the parent presenter keyed by position, why not a child
  presenter in a loop, never recreate state behind keyless handlers; T1 outcome (where the
  semantics harness lives, `@Config(sdk = [35])`). `references/presenter-pattern.md` — mark the
  `SongRowPresenter` sketch as superseded for expansion and note the `key()` pitfall.
- `docs/risks-and-open-questions.md` — panel height on all-open songs; Robolectric download (T1).
- `PROGRESS.md`, `feature_list.json` — evidence; `passing` only after the device check and cleanup.
- `AGENTS.md`, `CONTEXT.md`, `domain-model.md` — not needed. No `ARCHITECTURE.md`/`CONSTRAINTS.md`.

## Implementation Plan

1. Baseline `CI=true ./init.sh` (40 result files, 220 tests, Konsist 15/15).
2. `:core:ui` lineup mapper + copy + defaults with their tests (red → green), `ContrastTest`.
3. `LineupPanel`, `ExpandIndicator`, previews.
4. `ExpandedRows` + test; `SongRowUiModel` fields; presenter state and mapping; presenter tests.
5. Screen (Decision 5); T1 test if approved.
6. `./gradlew ktlintFormat`, gate, failure demonstrations, greps, device check, docs.

## Verification Plan

- `CI=true ./init.sh` exit 0; `konsist: wired` (15/15, unchanged), `detekt: wired`, `ktlint: wired`;
  no baseline, `ignoreFailures`, `@Suppress` or rule disable. Record per-class counts.
- `LineupPanelMapperTest` (expected strings written out, never read from the copy): scenario 3
  order and texts; two guitars with only the second filled keep order in each section; all open;
  all filled → note; empty lineup → all empty + note; extras never in open/filled; long name verbatim.
- `LineupPanelDefaultsTest`: open = `slotOpen` text on the open fill with a dot; filled detail
  `text` on `slotFilled` with a check; extra never amber nor `slotFilled`.
- `ExpandedRowsTest`: toggle on/off, two positions, other date resets, Saver round-trip.
- `NextJamPresenterTest` (Molecule): all collapsed at first with "contraído"/"ver los cupos"; toggle
  one → expanded with "expandido"/"ocultar los cupos"; two expanded at once; collapse one leaves the
  other; refresh keeps position-keyed expansion and shows new data (scenario 4); the first model's
  handler still works after a refresh; a new jam date collapses all; open slots ordered before filled
  in `lineup`; `artist` carried.
- **T1 (if approved)** `SongRowSemanticsTest` (Robolectric, `NextJamContent` with a model whose
  handler flips a local state): collapsed header has click action, `Role.Button`, state
  "contraído", and each chip description exactly once; after `performClick`, "expandido", strip
  descriptions gone from the header, each panel line its own node with its description; panel
  lines have no click action; a chip with no description fails it.
- **Failure demonstrations** (edit, run, restore, sha1): (1) filled sorted before open in the
  mapper → mapper and presenter order tests fail; (2) state wrapped in `remember(snapshot)` →
  refresh test fails; (3) toggle replacing the set → two-rows test fails; (4)
  `BluesJamTheme.colors.slotOpen` in `NextJamScreen.kt` → `amber-roles-allowlisted`; (5)
  `OPEN_FILL_ALPHA = 0.9f` → `ContrastTest` via `style()` fails; (6, T1) drop `stateDescription`
  or a line's `clearAndSetSemantics`, and show the strip also when expanded → semantics test fails.
- Greps on `feature/next-jam/src/main` and `core/ui/.../lineup`: no `.dp` literal, no `Color(`, no
  `MaterialTheme.`, no `clickable`/`Role` inside `lineup/`, no tú forms, no "Pedir".
- **Device (Pixel 5 `09281FDD4004U6`, outside the gate), with the user's temporary jam:**
  `./gradlew :app:installDebug`; force-stop; `logcat -c`; `am start -W -n com.bbbjam/.MainActivity`;
  wait for `jams cache: upcoming 2026-10-31`. `uiautomator dump` (Git Bash `MSYS_NO_PATHCONV=1` for
  `/sdcard`, Windows path for `adb pull`): record bounds of row 1 and row 2 headers; `input tap` on
  row 2's header; dump again: row 1 bounds identical, row 2 header top identical, panel nodes with
  the descriptions of scenario 3; tap row 4 → both expanded; tap a panel line → nothing changes;
  screenshots (expanded mixed, all-filled, font scale 1.3 restored to 1.0). Rotation:
  `settings put system accelerometer_rotation 0`, `user_rotation 1`, dump (still expanded),
  restore `user_rotation 0`. Optional, recorded only: `animator_duration_scale 0` (restore 1).
  Crash buffer and AndroidRuntime empty. Without T1, state descriptions are not visible to
  uiautomator; record that gap.

## User Manual Steps (the real Sheet has only a past jam and no names)

A. In the Sheet, duplicate tab `2026-07-25` as `2026-10-31`; in `Jams` add
   `2026-10-31 | 21:00 | La Macanuda | PUBLICADA` (plain text like the existing row).
B. In `2026-10-31` only: song 2 → `Guitarra 2` = `Tincho`, `Bajo` = `Nico`, `Armónica` = `Mono`,
   `Teclados` = `Maximiliano Fernández de la Torre`, `Otros` = `Juan (saxo)`; song 3 → every slot
   cell filled with any names; leave the rest empty. Tell the agent.
C. The agent runs the device check above.
D. **Clean up:** delete the `Jams` row and the `2026-10-31` tab; tell the agent, who relaunches and
   confirms `upcoming none` and the no-upcoming line. The cleanup is part of the check.

## Evidence To Capture

Gate output and per-class counts; each failure demonstration's message; measured contrast values;
uiautomator excerpts with bounds before/after; screenshots; rotation result; `BluesJam` log lines;
copy as shipped; the user's cleanup confirmation.

## Validator Checklist

- [ ] Panel in `:core:ui` `lineup/`; composables take only UiModels; no feature→feature import.
- [ ] State in `NextJamPresenter`, keyed by position and jam date, never recreated; many expanded.
- [ ] Open first, then filled, then extras; strip hidden when expanded; header-only toggle.
- [ ] Panel lines not clickable, no 48dp minimum, no "Pedir cupo"; one node per line.
- [ ] Copy matches the approved table; allowlist unchanged; no colour/dp literal, no `MaterialTheme`.
- [ ] Tests and demonstrations recorded; three `wired`; device bounds unchanged; cleanup done.
- [ ] No admin, filter, detail link, list-states or navigation work.

## Needs User Approval Before Implementation

- **C1** the copy table (incl. "contraído" vs "colapsado").
- **I1** the strip is replaced by the panel when expanded (vs. both shown).
- **I2** open slots look available but are not interactive for musicians, compact lines without a
  48dp minimum; DESIGN.md's "tappable" reworded to the admin view.
- **H1** the optional hint line after the open slots (recommended: include).
- **T1** Compose semantics harness in `:feature:next-jam`: Robolectric 4.16 + `ui-test-junit4` +
  `ui-test-manifest` (all `testImplementation`), `isIncludeAndroidResources = true`, `@Config(sdk =
  [35])` because `compileSdk` 37 is beyond Robolectric 4.16 (assumption: not tested without it).
  Cost: three test dependencies, ~45 s and a large one-time download of `android-all` on a clean
  machine (an offline first run fails), ~6–10 s per gate run after. Earlier slices declined
  Robolectric for presenters; this is the first test that renders a composable. It closes the
  strip validation's gap (a chip losing its description) and is the only automatic check of the
  state description. Declined → JVM tests cover state and order, the device dump covers
  descriptions, the state description stays unverified without TalkBack.
- No Konsist, colour-token or `init.sh` change; the amber allowlist stays `{key}`.
- Steps A–D from the user.

## Risks

- An all-open song's panel is seven amber lines under one heading: tall and
  amber-heavy (the saturation risk again, now per expanded row).
- Expanding a row near the bottom grows below the fold; without auto-scroll the user scrolls.
- `rememberSaveable` survives rotation but not `TemporaryTabs` switching (until bottom-navigation).
- Compose's honouring of the system "remove animations" setting is assumed, not verified.
- T1: Robolectric's SDK support lags `compileSdk`; a Compose BOM upgrade may need a matching
  Robolectric bump.
- A forgotten temporary jam (step D) is shown to every musician.

## User Approvals

Recorded 3 October 2026, before implementation. Where they differ from the body, these win.

- **C1: copy approved as written**, with `contraído` (not `colapsado`).
- **I1: approved** — when a row is expanded the strip is replaced by the artist line and the panel.
- **I2: approved** — open slots look available but are not interactive for musicians; compact lines;
  reword `DESIGN.md`'s "tappable" to apply to the admin view.
- **H1: approved** — the hint line is included.
- **T1: declined.** No Robolectric or Compose UI test harness. The semantics checks stay on the JVM
  model plus device uiautomator; the state description (`expandido`/`contraído`) and the merged
  row description are verified on the Pixel 5 via uiautomator dumps. Record this as a known gap in
  the risks doc.
- The device check needs the user's temporary jam (steps A/B) and its cleanup (step D), which is
  part of the check.
