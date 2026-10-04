# Feature Implementation Spec: Filter the setlist by instrument with an open slot

## Source Feature

- `id`: instrument-filter-chips
- `area`: feature-next-jam
- `depends_on`: `instrument-strip-component` (`accepted`); builds on `song-row-expansion` (`accepted`)
- `status`: not_started at planning time
- `source`: `feature_list.json` (entry, verification and notes)

## Goal

On Próxima jam, under the header, a filter bar answers "where can I play?": a `Todos` chip and one
chip per instrument (Guitarra, Bajo, Batería, Voz, Armónica, Teclados), each with the number of
songs that have an open slot for it. Tapping an instrument shows only those songs, with a line
"n de m temas con cupo libre para bajo". `Todos` clears the filter. When no song matches, a message
names the instrument and offers "Ver todos los temas".

## Non-Goals

- Loading, empty-setlist, error and offline states (`list-states`). The withheld/unavailable line
  stays as is (`unpublished-setlist-state`).
- Remembering "my instrument" across app launches (DataStore, a profile-like feature; out of scope).
- Multi-select, a sticky bar, item add/remove animations, the jam summary counter ("32 anotados ·
  18 cupos libres", DESIGN.md), per-tab state (`bottom-navigation`), admin anything.
- No `:core:model`, `:core:data`, `:app`, Gradle, `init.sh` or Konsist change. No new colour token.
  No mutation: the filter is local presentation state, so D-13 registers nothing.

## Job Story

When I open the list in the bar holding my bass, I want one tap to show only the songs that still
need a bass, and how many there are, so I know in seconds whether and where I can play.

## Ownership of "filter with no results" (read this)

`list-states` covers **loading, empty, error, offline** (its entry and verification); it does not
mention no-results. This entry's verification does: "The no-results case is distinct from the empty
case and names the active filter". So **this slice ships the no-results state** (message naming the
instrument + "Ver todos los temas" + the `Todos` chip still visible). It is deliberately plain
(text and a text action, Decision 5). `list-states` keeps: the empty-setlist invitation (a published
setlist with zero songs, where this slice draws **no** bar), skeleton loading, error with retry,
offline staleness; it may restyle the no-results block to match its own treatment, but owns no
behaviour of it.

## Decisions

**1. Single-select, radio semantics (needs approval, F1).** The domain ("taps their instrument's
filter chip"), the success criterion ("using one filter chip") and DESIGN.md's `Todos` chip all
describe one choice at a time. Multi-select raises an AND/OR question with no right answer for a
musician who plays one instrument, and muddles "n de m". Selecting a chip replaces the selection;
`Todos` selects none (the clear). **Tapping the already-selected chip does nothing** (F4): with
`Role.RadioButton` Compose removes the click action from a selected node (observed in the
prototype: `checked=true clickable=false`), so a toggle-off would exist for touch but not for
TalkBack; one clear path (`Todos` / "Ver todos los temas") serves both.

**2. State in `NextJamPresenter`.** `var filter by rememberSaveable(stateSaver = InstrumentFilterSaver)
{ mutableStateOf<Instrument?>(null) }` beside `expanded` (saver stores `instrument?.name`, restores
with `Instrument.valueOf`; define it as a small `internal val` in the feature). Passed into the pure
mapping as `toUiModel(today, expanded, onToggle, filter = null, onSelect: (Instrument?) -> Unit = {})`
(defaults keep direct-call tests). Same rules as `ExpandedRows`: created once, never wrapped in
`remember(snapshot)`, so an earlier model's handler still writes through (test it).
- **Not scoped to the jam date** (F5): the filter describes the musician, not the jam; it survives
  refreshes, rotation and a change of upcoming jam. It does not survive the `TemporaryTabs` switch
  or a relaunch (accepted). While the setlist is withheld/unavailable/absent the state is kept and
  nothing is drawn.
- **Filtering:** rows are `songs.filter { filter == null || it.lineup.hasOpenSlotFor(filter) }`
  — `hasOpenSlotFor` only, so extras ("Otros") never count (D-18). A song with no open slots is
  excluded under any filter and listed under `Todos` (entry notes).
- **Expansion is untouched:** `ExpandedRows` is keyed by position; shown rows keep their state,
  hidden rows keep theirs and reappear as they were. No pruning.

**3. UiModel.** `SetlistUiModel.Songs(rows, droppedRowsNote, filterBar: InstrumentFilterBarUiModel?)`
(new last field, no default). `rows` are the filtered rows; `droppedRowsNote` unchanged. `filterBar`
is `null` when the setlist has zero songs (empty is `list-states`'), otherwise always present.

**4. Component in `:core:ui`, new package `com.bbbjam.core.ui.filter`** — so `activeFilter` is read
only inside `:core:ui` and `AMBER_ROLE_ALLOWLIST` stays `{"feature/next-jam": {"key"}}`. Reasoning:
the architecture skill allows either (a feature may add `activeFilter` to its allowlist in the same
diff), but the component form is stronger: the allowlist can't judge which element is amber, while
a `…Defaults` object confines amber to the selected chip and is JVM-testable; the instrument names
are already in `:core:ui` (`InstrumentStripCopy.name`), and `chip-filter` is a design-system token
in DESIGN.md. Files:
- `InstrumentFilterUiModel.kt`: `InstrumentFilterBarUiModel(heading: String, chips:
  List<FilterChipUiModel>, summary: String?, noResults: String?, clearLabel: String, events:
  EventHandler<Event>)` with `Event.Clear`; `FilterChipUiModel(label, count: String,
  contentDescription, isSelected, events: EventHandler<Event>)` with `Event.Select`.
- `InstrumentFilterMapper.kt`: pure `fun instrumentFilterBar(lineups: List<Lineup>, selected:
  Instrument?, onSelect: (Instrument?) -> Unit): InstrumentFilterBarUiModel`. Chips: `Todos`
  (count = `lineups.size`) then the six in `Instrument.entries` order (count = `lineups.count {
  it.hasOpenSlotFor(i) }`). Each chip's `Select` calls `onSelect(itsInstrument)` (`Todos` → null);
  `Clear` calls `onSelect(null)`. `summary` only when selected and n ≥ 1; `noResults` only when
  selected and n = 0 (never both).
- `InstrumentFilterCopy.kt` (internal): the copy table; names from `InstrumentStripCopy.name()`,
  lowercased (`lowercase()`) inside sentences.
- `InstrumentFilterDefaults.kt` (internal): `chipStyle(selected, colors)`: selected → fill
  `activeFilter`, content `onActiveFilter`; unselected → fill `surfaceRaised`, content `textMuted`
  (DESIGN.md `chip-filter`). Never `slotOpen`.
- `InstrumentFilterBar.kt`: `@Composable fun InstrumentFilterBar(model, modifier)` — layout below,
  plus `@Preview`s: none selected, Bajo selected, Armónica with no results, two-digit counts.

**5. Layout (Prototype-measured).** `Column(spacedBy(spacing.sm))`: heading (caption, `textMuted`,
`semantics { heading() }`); `FlowRow(Modifier.selectableGroup(), spacedBy(spacing.xs) both ways)`
of chips; then either `summary` (body, `text`) or the no-results block: `noResults` (body,
`textMuted`) and a text action `clearLabel` (body, `text`, underlined, `heightIn(min =
LocalMinimumInteractiveComponentSize.current)`, `clickable(role = Role.Button)` → `Event.Clear`).
Summary/no-results text carry `liveRegion = LiveRegionMode.Polite` so TalkBack announces the new
count (not verifiable on device; recorded). **Chip:** `Row(heightIn(min =
LocalMinimumInteractiveComponentSize.current) /* 48dp */, background(fill, shapes.lg),
semantics { contentDescription }, selectable(selected, role = Role.RadioButton) { Select },
padding(horizontal = spacing.sm), spacedBy(spacing.xs), CenterVertically)` = label (body) + count
(caption), both `clearAndSetSemantics {}`. No dp literal; no Material `FilterChip` (its defaults are
not design decisions).
- **No horizontal scroll** (DESIGN.md, design prompt): the bar wraps. Measured on the Pixel 5 with
  single-digit counts: 393dp/1.0 → 2 rows; 360dp (`wm density 480`)/1.0 → 2 rows with ~4dp to
  spare; 360dp/1.3 → 3 rows. With two-digit counts (a real 13-song list) expect 3 rows at 360dp.
  Wrapping to 3–4 rows (≈150–210dp) is accepted, not a defect; the bar scrolls with the list (item
  after the header, key `"filter"`), not sticky.

**6. Semantics readable by uiautomator.** Observed: each chip is a node with `checkable="true"`
and `checked` = selected (RadioButton maps `selected` to checked), `clickable` true unless selected;
its description appears on a child node with the same bounds. The device check reads `checked` and
the description; `stateDescription` is not used.

**7. Contrast.** `ContrastTest` gains `content on the active filter` (`onActiveFilter` on
`activeFilter`, measured 9.52 from the tokens) and `muted text on a raised surface` (`textMuted` on
`surfaceRaised`, 9.66), both read through `InstrumentFilterDefaults.chipStyle`, like the strip test.

## Copy (user approval requested, C1; Rioplatense, D-12)

| Where | Text |
|---|---|
| Bar heading (caption, a11y heading) | `Filtrá por cupo libre` |
| Chips (label + count) | `Todos 13`, `Guitarra 4`, `Bajo 1`, `Batería 0`, `Voz 2`, `Armónica 3`, `Teclados 5` |
| `Todos` description | `Todos los temas: 13` |
| Instrument description | `Bajo: 2 temas con cupo libre` · `Bajo: 1 tema con cupo libre` · `Bajo: ningún tema con cupo libre` |
| Count line (selected, n ≥ 1) | `2 de 13 temas con cupo libre para bajo` (m = 1: `1 de 1 tema con cupo libre para bajo`) |
| No results (selected, n = 0) | `Ningún tema tiene cupo libre para armónica.` |
| Clear action | `Ver todos los temas` |

Lowercase names in sentences: guitarra, bajo, batería, voz, armónica, teclados. "Cupo libre"
matches the panel's "Cupos libres" and the strip's "LIBRE". TalkBack adds the role and checked
state itself ("botón de selección, marcado"); no copy for it.

## Acceptance Scenarios

1. Given 13 songs, nothing selected → `Todos` checked, all 13 rows, no count line.
2. Given Bajo open only on songs 3 and 7, when Bajo is tapped → Bajo checked, rows 03 and 07, line
   "2 de 13 temas con cupo libre para bajo"; chip reads `Bajo 2`.
3. Song 2 with Bajo filled and `Juan (bajo)` in Otros → not shown under Bajo (D-18).
4. A song with every slot filled → listed under `Todos`, hidden under every instrument.
5. Armónica with 0 → tapping it shows "Ningún tema tiene cupo libre para armónica.", "Ver todos los
   temas", no rows; tapping the action (or `Todos`) restores all rows with `Todos` checked.
6. Row 03 expanded, Bajo selected, then `Todos` → row 03 still expanded; rows hidden while expanded
   come back expanded.
7. Bajo selected, a refresh fills song 3's bass → row 03 disappears, line "1 de 13…"; a new upcoming
   jam keeps Bajo selected.
8. Tapping the selected chip → nothing changes.
9. Empty published setlist → no bar; withheld → no bar.

## Repository Research

**Inspected:** `AGENTS.md`, `PROGRESS.md` (state, sessions 053–060), `feature_list.json` (this
entry, `list-states`, `bottom-navigation`), `DESIGN.md` (front-matter `chip-filter`, Layout, Filter
chip, Required States, Responsive and Accessibility baselines), `bb-blues-jam-design-prompt.md`
(Barra de filtro, rules), `docs/design/README.md`, Stitch `next-jam.png` and `next-jam-filtered.png`
(layout only: horizontal-scroll chips and "Limpiar filtro" chip rejected), `CONTEXT.md`,
`docs/domain-model.md` (Finding a place to play), `docs/build-brief.md`, bitácora D-06, D-07, D-12,
D-17, D-18, `docs/risks-and-open-questions.md`, architecture `SKILL.md`, `docs/specs/song-row-expansion.md`,
`feature/next-jam` main and test sources, `core/ui` strip/lineup/theme sources, `ContrastTest`,
`core/model` `Lineup`/`Instrument`, `TemporaryTabs.kt`.

**Prototype** (throwaway clone of `cfb686b` in the session scratchpad; demo snapshot hard-wired):
Decisions 3–6 roughly built and installed on the Pixel 5. Observed the chip node attributes above,
the selected chip losing `clickable`, the counts and filtered rows (Bajo → rows 02 and 06 of the
demo, line "2 de 6 temas con cupo libre para bajo"), the row counts of Decision 5. **Not
prototyped:** Molecule tests, no-results block, heading, live region, detekt/ktlint. Device restored
(font 1.0, density reset, HEAD build reinstalled, `upcoming none`).

## Expected File Changes

- `core/ui/src/main/kotlin/com/bbbjam/core/ui/filter/` — create the five files of Decision 4.
- `core/ui/src/test/.../filter/InstrumentFilterMapperTest.kt`, `InstrumentFilterDefaultsTest.kt` —
  create; `core/ui/src/test/.../theme/ContrastTest.kt` — two pairs.
- `feature/next-jam/.../NextJamUiModel.kt`, `NextJamPresenter.kt`, `NextJamScreen.kt` (filter item;
  preview `Songs(...)` gains a bar) — modify; `NextJamPresenterTest.kt` — expected `Songs` gain the
  bar (helper), new cases.

## Durable Documentation Impact

- `DESIGN.md` "Filter chip": single-select with `Todos` as clear, re-tap no-op, counts, wrap (no
  horizontal scroll, 2–4 rows), heading, count line, no-results block, radio semantics, component
  `InstrumentFilterBar` in `com.bbbjam.core.ui.filter`.
- Architecture `SKILL.md`: the `filter` package; amber `activeFilter` read only inside `:core:ui`;
  presenter-held selection (not date-scoped, never recreated).
- `docs/risks-and-open-questions.md`: bar height on small screens; not sticky; selection lost on
  relaunch and tab switch; live region unverified.
- `PROGRESS.md`, `feature_list.json` — evidence; `passing` only after the device check and cleanup.
- `AGENTS.md`, `CONTEXT.md`, `domain-model.md` — not needed.

## Implementation Plan

1. Baseline `CI=true ./init.sh` (43 result files, 240 tests, Konsist 15/15).
2. `:core:ui` filter copy, mapper, defaults with tests (red → green); `ContrastTest`.
3. `InstrumentFilterBar` + previews.
4. Presenter state, mapping, `Songs.filterBar`; presenter tests.
5. Screen item; `./gradlew ktlintFormat`; gate; failure demonstrations; greps; device; docs.

## Verification Plan

- `CI=true ./init.sh` exit 0; `konsist: wired` (15/15, unchanged), `detekt: wired`, `ktlint: wired`;
  no baseline, `ignoreFailures`, `@Suppress` or rule disable. Record per-class counts.
- `InstrumentFilterMapperTest` (strings written out, never read from the copy): chip order, labels,
  counts and descriptions (0, 1, n); selected flags for none and each instrument; summary vs
  noResults exclusivity; m = 1; extras never counted; all-filled song counted nowhere; handlers call
  `onSelect` with the chip's instrument / null.
- `InstrumentFilterDefaultsTest`: selected = `activeFilter`/`onActiveFilter`; unselected =
  `surfaceRaised`/`textMuted`; no style uses `slotOpen`.
- `NextJamPresenterTest` (Molecule): **for each of the six instruments** (a fixture where each is
  open on a distinct set of positions) selecting it yields exactly those rows and the right line;
  `Todos` and `Clear` restore all rows; re-selecting is a no-op (no new emission or equal model);
  scenario 3–9 cases; expansion kept through filter changes; filter across a refresh and a new jam
  date; an earlier model's chip handler still works after a refresh; empty setlist → `filterBar`
  null; withheld → `NotShown`. The number of rows equals the selected chip's count.
- **Failure demonstrations** (edit, run, restore, sha1): (1) filter by `openSlots.isNotEmpty()` →
  per-instrument test fails; (2) count extras by instrument text → D-18 test fails; (3) filter state
  in `remember(snapshot)` → refresh/stale-handler test fails; (4) `colors.activeFilter` in
  `NextJamScreen.kt` → `amber-roles-allowlisted` fails; (5) selected style `slotOpen` → defaults
  test fails; (6) a copy change → presenter test fails.
- Greps on `feature/next-jam/src/main` and `core/ui/.../filter`: no `.dp`/`.sp` literal, no `Color(`,
  no `MaterialTheme.`, no `horizontalScroll`/`LazyRow`, no tú forms; `activeFilter` only in
  `InstrumentFilterDefaults.kt` (and the tokens/test).
- **Device (Pixel 5 `09281FDD4004U6`, outside the gate), after steps A/B:** `./gradlew
  :app:installDebug`; force-stop; `logcat -c`; `am start -W -n com.bbbjam/.MainActivity`; wait for
  `jams cache: upcoming 2026-10-31`. With `MSYS_NO_PATHCONV=1`, `uiautomator dump` + screenshot after
  each step: initial (Todos `checked`, counts Todos 13, Guitarra 12, Bajo 2, Batería 12, Voz 12,
  Armónica 0, Teclados 12); tap Bajo (checked, line "2 de 13…", rows 03 and 07 only); tap row 03's
  header, then Todos (13 rows, 03 expanded); tap Armónica (no-results text, action, no rows); tap
  "Ver todos los temas" (Todos checked); tap Bajo then rotate (`accelerometer_rotation 0`,
  `user_rotation 1`, dump: Bajo still checked; restore 0). Size: `wm density 480` (360dp) at
  `font_scale` 1.0 and 1.3 — every chip's bounds height ≥ 144 px (48dp at 3.0), right edge within the
  screen, no chip clipped; record row count; restore `wm density reset`, font 1.0. Crash buffer and
  AndroidRuntime empty. `liveRegion` and TalkBack wording are not verifiable this way; record it.

## User Manual Steps (the real Sheet has only a past jam and no names)

A. Duplicate tab `2026-07-25` as `2026-10-31`; in `Jams` add `2026-10-31 | 21:00 | La Macanuda |
   PUBLICADA` (plain text like the existing row).
B. In `2026-10-31` only: `Armónica` = `-` on every song; `Bajo` = any name on every song **except 3
   and 7**; song 5: every remaining slot cell a name; song 2: `Otros` = `Juan (bajo)`; song 4:
   `Guitarra 1` = `Tincho`. Tell the agent.
C. The agent runs the device check above.
D. **Clean up:** delete the `Jams` row and the `2026-10-31` tab; tell the agent, who relaunches and
   confirms `upcoming none`. The cleanup is part of the check.

## Evidence To Capture

Gate output and counts; each failure demonstration's message; measured contrast; uiautomator
excerpts (checked flags, descriptions, rows, bounds) and screenshots per step and size; rotation
result; `BluesJam` log lines; copy as shipped; the user's cleanup confirmation.

## Validator Checklist

- [ ] Bar in `:core:ui` `filter/`; composable takes only UiModels; allowlist unchanged (`{key}`).
- [ ] Single-select, `Todos` and the no-results action clear, re-tap no-op; `hasOpenSlotFor` only.
- [ ] State in `NextJamPresenter`, never recreated, not date-scoped; expansion unaffected.
- [ ] Chips ≥ 48dp, wrap with no horizontal scroll at 360dp/1.3; radio semantics visible as `checked`.
- [ ] No-results distinct from empty (no bar on an empty setlist) and names the instrument.
- [ ] Copy matches the approved table; no colour/dp literal, no `MaterialTheme`; tests per instrument.
- [ ] Demonstrations recorded; three `wired`; device steps done; cleanup done.

## Needs User Approval Before Implementation

- **C1** the copy table, including the heading `Filtrá por cupo libre` (or none).
- **F1** single-select (vs multi-select).
- **F2** per-chip counts and the `Todos` chip (DESIGN.md already says so; confirming it).
- **F3** chips with count 0 stay tappable and lead to the no-results message (vs disabled).
- **F4** tapping the selected chip does nothing; `Todos` is the clear.
- **F5** the selection survives a new upcoming jam and rotation, not relaunch or tab switch.
- **S1** no-results is shipped here (minimal), not by `list-states`, per this entry's verification.
- No Konsist, gate, Gradle or colour-token change; `AMBER_ROLE_ALLOWLIST` stays `{key}`.
- Steps A–D from the user.

## Risks

- The bar takes 2–4 rows (≈100–210dp) on small screens or large fonts, pushing the first song down.
- Not sticky: after scrolling, the active filter is off screen (the count line too).
- Amber: the active chip adds one amber element beside the amber open-slot chips of the shown rows.
- `liveRegion` announcements and TalkBack's radio wording are unverified (T1 declined).
- A forgotten temporary jam (step D) is shown to every musician.

## User Approvals

Recorded 4 October 2026, before implementation. Where they differ from the body, these win.

- **C1: copy approved as written, with the heading** `Filtrá por cupo libre`.
- **F1: the user chose MULTI-select, with OR semantics.** A song is shown when it has an open slot
  for **any** selected instrument (`selected.any { lineup.hasOpenSlotFor(it) }`; extras never count,
  D-18). Consequences, defined by the orchestrator:
  - State is a set of instruments (`rememberSaveable`, saved by enum names), not a single value.
  - Each instrument chip toggles independently: tapping a selected chip deselects it. Semantics are
    a checkbox (`toggleable`, `Role.Checkbox`), so uiautomator reads `checkable`/`checked` and
    TalkBack can toggle both ways. This replaces F4.
  - `Todos` is checked exactly when the set is empty; tapping it clears the set. "Ver todos los
    temas" clears the set too.
  - Per-chip counts stay per instrument (unchanged).
  - Count line and no-results text join the selected instrument names in lowercase, in the fixed
    chip order, with ", " and a final " o ": one → `para bajo`; two → `para bajo o voz`; three →
    `para guitarra, bajo o voz`. Examples: `5 de 13 temas con cupo libre para bajo o voz`,
    `Ningún tema tiene cupo libre para bajo o voz.` The noun agrees with the **total**: `1 de 13
    temas…`, `2 de 13 temas…`; `tema` is singular only when the setlist has exactly one song
    (`1 de 1 tema…`). (Corrected 4 October 2026: an earlier wording here said `1 de 13 tema…`, an
    orchestrator error; the body's copy table had it right.)
  - Tests cover selecting two and three instruments, deselecting one, Todos clearing, OR counting
    (a song open only for voz is shown under bajo+voz), and the joined copy.
- **F2: confirmed** (count per chip and the `Todos` chip).
- **F3: approved** — zero-count chips stay tappable and lead to the no-results block.
- **F4: superseded** by the multi-select toggle above.
- **F5: approved** — selection not tied to the jam date; lost on relaunch and on the temporary tab
  switch.
- **S1: approved** — the plain no-results block ships in this slice.
- Amber allowlist unchanged (`{key}`); the active chip reads `activeFilter` inside `:core:ui`.
- Device check: adjust step B/C so the multi-select OR is visible (e.g. select Bajo, then add Voz
  and see more songs), plus the user's temporary jam and its cleanup (step D).
