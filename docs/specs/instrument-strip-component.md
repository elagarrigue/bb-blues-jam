# Feature Implementation Spec: Add the instrument strip to each song row

## Source Feature

- `id`: instrument-strip-component
- `area`: feature-next-jam
- `depends_on`: `next-jam-read-only-list` (`accepted`)
- `status`: not_started at planning time
- `source`: `feature_list.json`

**Entry is stale on one point.** Its behavior, verification and notes say "icon", "~16dp icons" and
"dimmed means open, lit means filled". `DESIGN.md` (Instrument strip), `docs/design/README.md` and
`docs/risks-and-open-questions.md` (resolved 19 September 2026) replaced that with **labelled
chips**. This spec implements chips and reads the entry's verification as: previews all-open /
all-filled / mixed; open vs filled distinguishable without colour or luminance; every **chip** has a
content description. The orchestrator should reword the entry (planner does not edit it).

## Goal

Each collapsed row on Próxima jam shows, under its position/title/key line, one chip per slot of the
song's lineup in Sheet column order: an open slot reads `GTR: LIBRE` (amber `slotOpen` text on a 15%
amber fill, a dot), a filled slot reads `Gtr: Tincho` (`textMuted` on `slotFilled`, a check). The
strip is a reusable `:core:ui` component so `song-detail-screen` can reuse it without
feature→feature dependencies.

## Non-Goals

- Row tap/expansion, artist, open-first ordering, "Otros" (`song-row-expansion`).
- Filter bar, counts, the jam counter (`instrument-filter-chips`); strip chips are **not** filters.
- Loading/empty/error/offline, a dp-literal Konsist rule (`list-states` owns that proposal).
- Song detail (`song-detail-screen`); admin assign/clear (admin slices). No `:core:model`/`:core:data`
  change, no new colour token, no `init.sh` change.

## Job Story

When I open the setlist in the bar,
I want to see which instruments are still free on each song and who has the rest,
so I know where I can play without opening anything.

## Users And Permissions

Anyone, read-only. No events, no writes, so D-13 has nothing to register.

## Decisions

**1. Location: `:core:ui`, package `com.bbbjam.core.ui.strip`.** `song-detail-screen` (another
feature) will draw the same lineup; D-03 forbids sharing it between features, and the architecture
skill already names "the instrument strip" as a `:core:ui` shared component. Files:
`InstrumentChipUiModel.kt`, `InstrumentStripCopy.kt` (internal), `LineupChips.kt`,
`InstrumentStrip.kt`, `InstrumentStripDefaults.kt` (internal).

**2. API — no domain type in the composable.**

```kotlin
data class InstrumentChipUiModel(val label: String, val contentDescription: String, val isOpen: Boolean) : UiModel
fun Lineup.toInstrumentChips(): List<InstrumentChipUiModel>          // pure, JVM-testable
@Composable fun InstrumentStrip(chips: List<InstrumentChipUiModel>, modifier: Modifier = Modifier)
```

The mapper sits in `:core:ui` (which `api`-depends on `:core:model`) beside the shared copy, so both
features map identically. The presenter calls it; the screen only draws. `SongRowUiModel` gains
`val instruments: List<InstrumentChipUiModel>` (last parameter, no default);
`NextJamPresenter.toRow()` sets `instruments = lineup.toInstrumentChips()`. Labels are fully
formed strings (uppercase already applied), so the `UiModel` is what the phase 2 assistant reads.

**3. Mapping.** One chip per `Lineup.slots` element, **in list order** — that is Sheet column order
(`JamCacheMapping` sorts by `columnIndex`; `Lineup` KDoc). Not open-first: a fixed instrument order
makes strips comparable down the list; open-first belongs to the expanded row. Removed slots are
absent from the lineup, so they get no chip. An empty lineup → empty list → `InstrumentStrip` emits
nothing (no blank line). **Extra participants are not shown** (D-18: never slots; a "saxo: Juan" or
third-guitar chip beside the Gtr chips would read as a slot and break the fixed, comparable lineup);
the mapper's receiver is `Lineup`, so they are excluded structurally. Two guitars → two chips with
the same prefix, no numbering (column identity means nothing to a musician).

**4. Visuals** (tokens only; Stitch `pr_xima_jam_vista_m_sico/code.html` lines 126–158 for layout).
`FlowRow` (foundation, stable, no opt-in — prototyped), `spacedBy(spacing.xs)` both axes. Chip:
`Row`, `background(fill, shapes.sm)`, padding `spacing.sm` × `spacing.xs`, `spacedBy(spacing.xs)`,
centered; label `typography.caption`, `maxLines = 1`, `TextOverflow.Ellipsis`.

| State | Fill | Glyph | Text colour |
|---|---|---|---|
| Open | `colors.slotOpen.copy(alpha = OPEN_FILL_ALPHA)`, `OPEN_FILL_ALPHA = 0.15f` | filled circle, `slotOpen`, **static** (Decision 6) | `slotOpen` |
| Filled | `colors.slotFilled` | `Icons.Filled.Check`, `textMuted`, `contentDescription = null` | `textMuted` (full alpha) |

Do **not** copy Stitch's `on-surface-variant/60` filled text: measured 4.36:1, below AA. Glyph
sizes are derived from the label's font size, not dp literals, so they scale with the system font
(prototype with fixed 6/12dp looked undersized at font scale 1.3): `val em = with(LocalDensity.current)
{ caption.fontSize.toDp() }`; check = `em`, dot = `em * DOT_TO_EM` with `DOT_TO_EM = 0.5f` in
`InstrumentStripDefaults`. No new `DESIGN.md` token; the 15% comes from DESIGN.md prose.

In `NextJamScreen.SongRow`: wrap the existing `Row` in a `Column(padding(spacing.md × spacing.sm),
spacedBy(spacing.sm))`; the `Row` gets `fillMaxWidth()`; then `InstrumentStrip(row.instruments)`.
The strip spans the row width under the title line, as in Stitch.

**5. Amber and D-17.** The strip reads `slotOpen` **inside `:core:ui`**, where
`amber-roles-allowlisted` does not apply. `:feature:next-jam` never reads `slotOpen`, so
`AMBER_ROLE_ALLOWLIST` stays `{"feature/next-jam": {"key"}}`: adding `slotOpen` would grant a
permission nothing uses and weaken the rule. Kept as is, the existing rule now guarantees the
feature cannot draw open-slot amber outside the shared component. No new colour role (so
`BluesJamColorsTest` stays at 17 roles) and no Konsist change. Distinction never by brightness:
greyscale on the Pixel 5 (prototype) shows the two fills at almost the same grey, so the real
non-colour signals are **glyph** (dot vs check) and **wording/case** (`LIBRE` uppercase vs a name);
fill differs in hue only. DESIGN.md's "three signals" sentence is corrected accordingly (docs).

**6. Pulse: static dot (needs approval, P1).** Stitch's dot pulses (`animate-ping`). Rejected for
now: an infinite animation per open chip redraws every frame while the screen is visible (with real
data today every slot is open: ~90 chips, about a third on screen), costs battery in a phone held
all night, and perpetual motion beside content conflicts with WCAG 2.2.2 (Pause, Stop, Hide) and
with users who disable animations. The dot is the glyph; the pulse adds no information. Alternative
if the user wants it: one shared `rememberInfiniteTransition` alpha read in `graphicsLayer` (draw
phase only), skipped when `Settings.Global.ANIMATOR_DURATION_SCALE == 0` — its own follow-up.

**7. Not interactive, so no 48dp.** Strip chips have no click, no `Role`, no
`minimumInteractiveComponentSize`. Filtering uses the separate filter bar
(`instrument-filter-chips`, 48dp chips per DESIGN `chip-filter`); assignment happens on the
expanded row's slots. Each chip uses `clearAndSetSemantics { contentDescription = … }` so TalkBack
reads "Guitarra: libre", not "G T R dos puntos LIBRE"; the glyphs are decorative.

**8. Check glyph source (needs approval, D1).** `Icons.Filled.Check` compiles today only because
material3 1.3.2 pulls `material-icons-core` 1.7.8 transitively (prototyped); newer material3 drops
that dependency. Recommended: declare it — catalog `androidx-compose-material-icons-core = { group
= "androidx.compose.material", name = "material-icons-core" }` (BOM-managed) and
`implementation(libs.androidx.compose.material.icons.core)` in `core/ui/build.gradle.kts`. If the
user declines: keep it transitive and record the trap in the architecture skill.

## Copy (user approval requested, C1; Rioplatense, D-12)

`InstrumentStripCopy` (internal, `:core:ui`):

| Instrument | Abbrev. open (label) | Abbrev. filled | Name (a11y) |
|---|---|---|---|
| `GUITAR` | `GTR: LIBRE` | `Gtr: <nombre>` | Guitarra |
| `BASS` | `BAJO: LIBRE` | `Bajo: <nombre>` | Bajo |
| `DRUMS` | `BAT: LIBRE` | `Bat: <nombre>` | Batería |
| `VOCALS` | `VOZ: LIBRE` | `Voz: <nombre>` | Voz |
| `HARMONICA` | `ARM: LIBRE` | `Arm: <nombre>` | Armónica |
| `KEYBOARDS` | `TEC: LIBRE` | `Tec: <nombre>` | Teclados |

- Abbreviations are DESIGN.md's (`Gtr`, `Bajo`, `Bat`, `Voz`, `Arm`, `Tec`), as in Stitch; `Bajo`
  stays whole (four letters, no ambiguity). Names are the DESIGN.md filter-chip names.
- Open label: `<ABREV>: LIBRE` uppercase; filled: `<Abrev>: <nombre>`, the name exactly as in the
  Sheet. Long names ellipsize at the strip width (one chip per line at worst; never horizontal
  scroll); the description keeps the full name. No truncation in the presenter.
- Accessibility: open `"<Nombre>: libre"` ("Guitarra: libre", "Teclados: libre"); filled
  `"<Nombre>: <nombre>"` ("Guitarra: Tincho"). The colon form avoids agreement errors such as
  "Teclados libre". Two guitars are read twice, unnumbered.

## Acceptance Scenarios

1. **Default lineup, all open** (the real data today): a row shows 7 chips `GTR: LIBRE`, `GTR:
   LIBRE`, `BAJO: LIBRE`, `BAT: LIBRE`, `VOZ: LIBRE`, `ARM: LIBRE`, `TEC: LIBRE`, wrapping lines,
   no horizontal scroll.
2. **Mixed**: lineup [GTR open, GTR "Tincho", BAJO "Nico", BAT open, VOZ open, ARM "Mono"] (TEC
   removed) → 6 chips in that order, filled ones `Gtr: Tincho`, `Bajo: Nico`, `Arm: Mono`; no TEC.
3. **Only Guitarra 2 filled**: [GTR open, GTR "Seba"] keeps that order (`GTR: LIBRE`, `Gtr: Seba`).
4. **Extras ignored**: a song with `ExtraParticipant("Juan", "saxo")` has the same chips as without.
5. **Empty lineup**: no strip, row looks like today's.
6. **Greyscale**: with colour removed, every open chip has a dot and `LIBRE`, every filled chip a
   check and a name.
7. **Screen reader**: each chip is one node with the description of the copy table.

## Repository Research

### Files Inspected

`AGENTS.md`, `PROGRESS.md`, `feature_list.json` (this entry, `song-row-expansion`,
`instrument-filter-chips`, `list-states`, `song-detail-screen`), `DESIGN.md`,
`docs/design/README.md`, Stitch `pr_xima_jam_vista_m_sico/code.html` (chip markup) and all twelve
exports (chip labels), `docs/risks-and-open-questions.md`, `CONTEXT.md`, bitácora D-06, D-07, D-17,
D-18, 6.5, 6.14, `.claude/skills/architecture/SKILL.md`, `docs/specs/next-jam-read-only-list.md`,
`feature/next-jam` sources and `NextJamPresenterTest.kt`, `core/model` `Instrument`/`Lineup`/`Slot`/
`ExtraParticipant`/`JamSong`, `core/data` `JamCacheMapping.kt` (slot order), `core/ui`
`BluesJamColors`/`Palette`/`Dimens`/`Typography`, `ContrastTest`, `BluesJamColorsTest`,
`ModuleIsolationTest.kt` (amber rule), `core/ui/build.gradle.kts`, `gradle/libs.versions.toml`.

### Prototype (throwaway clone of `ef972d9` in the session scratchpad)

`:core:ui` strip + mapper + test: `CI=true ./init.sh` exit 0, konsist/detekt/ktlint `wired` (after
fixing detekt `MatchingDeclarationName` — defaults in their own file — and `MaxLineLength`).
Integrated into `:feature:next-jam` with a temporary demo model and installed on the Pixel 5:
default lineup wraps to 3 lines (4 at font scale 1.3), a long name fits on its own line, greyscale
(`magick -colorspace Gray`) keeps dot/check and wording, `uiautomator` shows one node per chip
("Guitarra libre", "Guitarra: Tincho"…). Contrast (WCAG, sRGB composite over `surface`): amber on
the 15% fill `#3C321C` ≈ 7.03; `textMuted` on `slotFilled` ≈ 7.22. The device was restored to a
HEAD build afterwards (no-upcoming line shown). **Not prototyped:** font-relative glyph sizes, the
updated presenter tests, the explicit icons dependency.

## Expected File Changes

- `core/ui/src/main/kotlin/com/bbbjam/core/ui/strip/` — create the five files of Decision 1, with
  `@Preview`s: all-open (default lineup), all-filled (incl. a long name), mixed (scenario 2).
- `core/ui/src/test/kotlin/com/bbbjam/core/ui/strip/LineupChipsTest.kt` — create.
- `core/ui/src/test/kotlin/com/bbbjam/core/ui/theme/ContrastTest.kt` — two pairs (below).
- `core/ui/build.gradle.kts`, `gradle/libs.versions.toml` — only if D1 approved.
- `feature/next-jam/.../NextJamUiModel.kt`, `NextJamPresenter.kt`, `NextJamScreen.kt` (layout,
  preview rows with strips), `NextJamPresenterTest.kt` — modify.
- Docs per below. Not touched: `:core:model`, `:core:data`, `:app`, `ModuleIsolationTest.kt`, `init.sh`.

## Visual Design Impact

UI: yes. Source `DESIGN.md` Instrument strip + tokens; Stitch for layout only. States: open,
filled, mixed, empty lineup, long name, font scale 1.3. No new token or design artifact.

## Durable Documentation Impact

- `DESIGN.md` — update Instrument strip: static dot (if P1 approved), filled text full `textMuted`
  on `slotFilled`, glyph and wording are the luminance-independent signals (fill is hue only);
  close the open question on a per-chip instrument glyph (no: width); wrapping confirmed on the
  Pixel 5.
- `.claude/skills/architecture/SKILL.md` — `:core:ui` gains `strip/` (`InstrumentStrip`,
  `toInstrumentChips`, shared instrument copy); shared UI copy used by two features lives in
  `:core:ui`; amber used inside a `:core:ui` component needs no allowlist entry; D1 outcome.
- `docs/risks-and-open-questions.md` — dense amber with all-open data (Risks); pulse decision.
- `PROGRESS.md`, `feature_list.json` — evidence; status `passing` only after the device check.
- `AGENTS.md`, `CONTEXT.md`, `domain-model.md` — not needed. No `ARCHITECTURE.md`/`CONSTRAINTS.md`.

## Implementation Plan

1. Baseline `CI=true ./init.sh` (38 result files, Konsist 15/15).
2. `InstrumentStripCopy`, `InstrumentChipUiModel`, `toInstrumentChips` + `LineupChipsTest` red→green.
3. `InstrumentStrip` + defaults + previews; `ContrastTest` pairs; D1 dependency if approved.
4. `SongRowUiModel.instruments`, presenter, screen, preview; update `NextJamPresenterTest`.
5. `./gradlew ktlintFormat`, gate, negative demonstrations, device check, docs.

## Verification Plan

- `CI=true ./init.sh` exit 0; `konsist: wired` (15/15, unchanged), `detekt: wired`, `ktlint: wired`;
  no baseline, `ignoreFailures`, `@Suppress` or rule disable.
- `LineupChipsTest` (expected strings written out, never read from the copy object): default lineup
  → the 7 labels and descriptions in order; every instrument open and filled (12 labels, 12
  descriptions); scenarios 2 and 3 order; removed slots absent; empty lineup → empty; long name
  kept verbatim in label and description.
- `NextJamPresenterTest`: existing expected rows gain `instruments` (seed songs use
  `Lineup.default()` → 7 open chips); new case: one song with scenario 2's lineup plus an extra
  participant → exactly scenario 2's chips (extras ignored, D-18).
- `ContrastTest`: `open slot on its chip fill` = `slotOpen` vs `slotOpen.copy(alpha =
  OPEN_FILL_ALPHA).compositeOver(surface)` (expected ≈ 7.03; record the measured value) and `muted
  text on a filled chip` = `textMuted` vs `slotFilled` (≈ 7.22). Both ≥ 4.5.
- Negative demonstrations (restore, record): reverse the slot order in the mapper → order tests
  fail; map extras into chips → the presenter extras test fails; `BluesJamTheme.colors.slotOpen` in
  any `:feature:next-jam` file → `amber-roles-allowlisted` violated (shows why the allowlist needs no
  `slotOpen`); `OPEN_FILL_ALPHA = 0.9f` → the new contrast test fails.
- Greps on `feature/next-jam/src/main` and `core/ui/.../strip`: no `.dp` literal, no `Color(`, no
  `MaterialTheme.`, no `infiniteTransition`/`animate` (P1), no tú forms.
- **Device (Pixel 5 `09281FDD4004U6`, manual, outside the gate):** `./gradlew :app:installDebug`,
  force-stop, `logcat -c`, `am start -W -n com.bbbjam/.MainActivity`, wait for `jams cache:`;
  `adb exec-out screencap -p`; `magick shot.png -colorspace Gray gray.png`; `adb shell uiautomator
  dump /sdcard/u.xml` (Git Bash: `MSYS_NO_PATHCONV=1`) and check one `content-desc` per chip; then
  `adb shell settings put system font_scale 1.3`, screenshot, **restore `1.0`**; AndroidRuntime log
  empty.

## User Manual Steps (real data has no musician names)

A. **Add a temporary jam:** in the Sheet duplicate tab `2026-07-25` as `2026-10-31`; in `Jams` add
   `2026-10-31 | 21:00 | La Macanuda | PUBLICADA` (plain text like the existing row).
B. **Type names in that tab only:** song 1 → `Guitarra 2` = `Tincho`; song 2 → `Bajo` = `Nico`,
   `Armónica` = `Mono`, `Guitarra 1` = `Maximiliano Fernández` (long name); song 3 → every slot
   cell filled with any names (all-filled row). If any song in the copy has a `-` cell, leave it
   (removed slot). Tell the agent.
C. The agent checks: song 1 `GTR: LIBRE`, `Gtr: Tincho`, …; song 2 order kept; song 3 checks only;
   other songs all open; screenshots colour, greyscale, font 1.3; uiautomator descriptions.
D. **Clean up:** delete the `Jams` row and the `2026-10-31` tab; tell the agent, who relaunches and
   confirms `upcoming none` and the no-upcoming line. The cleanup is part of the check.

## Evidence To Capture

Gate output and per-class counts; each negative demonstration's failing message; screenshots
(colour, greyscale, font 1.3, after cleanup); uiautomator excerpt; `BluesJam` log lines; measured
contrast values; the copy as shipped; the user's cleanup confirmation.

## Validator Checklist

- [ ] Strip in `:core:ui` `strip/`; composable takes only `InstrumentChipUiModel`s; no feature→feature.
- [ ] Column order, removed slots absent, extras absent, empty lineup draws nothing.
- [ ] Copy matches the approved table; chip descriptions per chip; glyphs decorative.
- [ ] `slotOpen` only inside `:core:ui`; allowlist unchanged; no colour/dp literal, no `MaterialTheme`.
- [ ] Static dot unless P1 said otherwise; chips not clickable.
- [ ] Contrast tests present; three `wired`; device evidence incl. greyscale and cleanup.
- [ ] No expansion, filter, Otros, list-states or admin work.

## Needs User Approval Before Implementation

- **C1** the copy table (abbreviations, `LIBRE` uppercase, names verbatim with ellipsis, "<Nombre>:
  libre" descriptions, unnumbered guitars) and **E1** extras not shown in the strip.
- **P1** static dot instead of Stitch's pulse (Decision 6).
- **D1** declare `material-icons-core` explicitly in `:core:ui` (Decision 8), or keep it transitive.
- No gate change, no new colour token, no Konsist change is proposed (Decision 5): confirm that
  keeping `AMBER_ROLE_ALLOWLIST` at `{key}` is acceptable rather than adding `slotOpen`.
- The device check needs steps A–D above from the user.

## Risks

- **Amber saturation:** today every slot is open, so every row shows 7 amber chips (3 lines); "if
  everything glows, nothing stands out". Real nights fill slots, but a fresh setlist will look like
  this. Worth the user's eye on the device; not solved here.
- Row height roughly doubles; 13 songs need more scrolling. Accepted in bitácora 6.5.
- A forgotten temporary jam (step D) would be shown to any musician.
- A future material3 upgrade removes the transitive icons if D1 is declined.
- `song-row-expansion` will make the row clickable; with per-chip `clearAndSetSemantics` the merged
  row description will list every chip — that slice decides whether that is too long.
