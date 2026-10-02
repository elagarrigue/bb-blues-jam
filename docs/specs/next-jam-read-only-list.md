# Feature Implementation Spec: Show the next jam setlist collapsed

## Source Feature

- `id`: next-jam-read-only-list
- `area`: feature-next-jam
- `depends_on`: `catalog-repository-cache`, `design-tokens-theme`, `molecule-presenter-harness`,
  `build-logic-conventions`, `jams-repository-cache` (all `accepted`)
- `status`: not_started at planning time
- `source`: `feature_list.json`

## Goal

A musician opens the app on **Próxima jam** and sees the upcoming jam's date and start time, its
venue, how long until it in natural language, and its songs as collapsed rows with position, title
and key (the key in the amber `key` role). The data is `JamsRepository.observeJams().upcoming`,
already cached, split in Buenos Aires and title-resolved by `jams-repository-cache`.

It also creates the second feature module, `:feature:next-jam`, the first to read data.

## Non-Goals

- Instrument strip (`instrument-strip-component`), artist, slots, expansion and any row click
  (`song-row-expansion`), filter chips and the jam counter (`instrument-filter-chips`).
- Skeleton loading, error with retry, offline/staleness, a designed empty state, any use of
  `Freshness` beyond "was anything ever fetched" (`list-states`).
- The designed draft treatment and its tests (`unpublished-setlist-state`); here a withheld setlist
  is one plain line (Decision 3).
- Real bottom navigation, a nav library, per-tab state preservation, the Anteriores tab, the
  status-bar scrim (`bottom-navigation`). Admin anything, `AdminSession`, mutations (none here, so
  D-13 has nothing to register). No change to `:core:data`, `:core:model`, `:core:ui` or `init.sh`.
- A midnight ticker (still open in `docs/risks-and-open-questions.md`).

## Job Story

When I am about to go to the jam, or standing in the bar,
I want to see at a glance when and where it is and what is played in which key,
so I can get ready without asking anyone.

## Users And Permissions

Anyone: reads only. No login. The presenter never triggers network work itself; collecting
`observeJams()` may start the repository's own background refresh, as designed.

## Decisions

**1. Module.** `:feature:next-jam`, package `com.bbbjam.feature.nextjam`, build file like
`feature/info/build.gradle.kts` plus `implementation(project(":core:data"))` (allowed by
`build-file-project-deps`; the skill already foresees it). `:core:model` arrives through
`:core:data`'s `api`. Constructor: `NextJamPresenter(jams: JamsRepository, calendar: JamCalendar)`,
both public in `:core:data` and bound by `dataModule`. No `CatalogRepository`: titles are resolved
by the cache view (the presenter-pattern sketch predates that). No child row presenter yet: rows
have no state or events; `song-row-expansion` introduces `SongRowPresenter`.

**2. UiModel** (`NextJamUiModel.kt`; all `: UiModel`, no events in this slice):

```kotlin
sealed interface NextJamUiModel : UiModel {
    data object Loading : NextJamUiModel
    data class NoUpcomingJam(val message: String) : NextJamUiModel
    data class Jam(val header: JamHeaderUiModel, val setlist: SetlistUiModel) : NextJamUiModel
}
data class JamHeaderUiModel(val date: String, val venue: String, val timeRemaining: String) : UiModel
sealed interface SetlistUiModel : UiModel {
    data class Songs(val rows: List<SongRowUiModel>, val droppedRowsNote: String?) : SetlistUiModel
    data class NotShown(val message: String) : SetlistUiModel
}
data class SongRowUiModel(
    val position: Int, val positionLabel: String, val title: String,
    val key: String, val keyDescription: String,
) : UiModel
```

**3. State mapping** (the only branches this slice owns):

| Input | Model |
|---|---|
| no emission yet | `Loading` (screen draws only the background) |
| `upcoming == null` and `freshness.fetchedAt == null` | `Loading` — nothing was ever read, so the app must not claim "no jam"; `list-states` turns this into error/offline |
| `upcoming == null`, `fetchedAt != null` | `NoUpcomingJam(NO_UPCOMING_JAM)` — plain line; `list-states` designs it |
| `Setlist.Available(songs, dropped)` | `Jam(header, Songs(rows, note))`; note null when `dropped == 0`; positions kept as-is (gaps allowed, P4) |
| `Available(emptyList())` | `Songs(emptyList(), null)` — header only; `list-states` owns that empty case |
| `Setlist.Withheld` | `Jam(header, NotShown(SETLIST_WITHHELD))` — no songs exist in the domain value |
| `Setlist.Unavailable(any problem)` | `Jam(header, NotShown(SETLIST_UNAVAILABLE))`; the problem is not shown to musicians |

`unpublished-setlist-state` later gives `Withheld` its own designed treatment (it may split
`NotShown` into `Withheld`/`Unavailable`); `list-states` adds skeleton, error, offline and staleness.

**4. Time remaining.** Pure `internal fun timeRemaining(today: LocalDate, date: LocalDate,
startTime: LocalTime): String` in `JamDateText.kt`. `days = ChronoUnit.DAYS.between(today, date)`
in calendar days, clamped at 0 (only a millisecond race at midnight can make it negative; a crash or
"hace 1 día" would be worse). `0` → `"Esta noche"` if `startTime >= 18:00`, else `"Hoy"`; `1` →
`"Mañana"`; `n ≥ 2` → `"En n días"` (no weeks: exact and short). `today` comes only from
`JamCalendar.today()` (Buenos Aires, P7), read once per snapshot as
`remember(snapshot) { calendar.today() }` so header and upcoming/past split agree; never
`LocalDate.now()`. On the jam's own night it says "Esta noche" until 00:00, when the jam turns past.

**5. Date label.** `"<Día> <d> de <mes> · HH:MM"`, e.g. `"Sábado 31 de octubre · 21:00"`, no year
(the upcoming jam is weeks away and the time-remaining line removes doubt). Day and month names are
hand-written `when` tables in `NextJamCopy`, not `DateTimeFormatter`/`Locale`: desugared java.time
on API 24–25 and the JVM may render locale text differently, and the copy must be identical on the
device and in tests. Time with `padStart(2, '0')`, not `String.format` (locale-sensitive).

**6. Layout** (Stitch `next-jam.png` for layout only; its "El Motivo", address, edition number,
counter, amber "ESTA NOCHE" badge, artist line and genre labels are rejected). `NextJamScreen(
modifier, contentPadding, presenter = koinInject())` calls an `internal NextJamContent(model, …)`,
which a `@Preview` drives with a hand-built model. One `LazyColumn` (background `background`,
`contentPadding`, then `spacing.md` horizontal / `spacing.lg` vertical, `spacedBy(spacing.sm)`):
header item — date `h1`, venue `body` in `text`, time remaining `body` in `textMuted`; then rows
keyed by `position`; then the dropped-rows note (`caption`, `textMuted`) when present. A
`NotShown`/`NoUpcomingJam` message is one `body` `Text` in `textMuted`. Row: `Surface(surface,
shapes.md)`, padding `spacing.md` × `spacing.sm`, `Row` centered vertically: `positionLabel`
(`songTitle`, `textMuted`), title (`songTitle`, `text`, `maxLines = 1`, ellipsis, `weight(1f)`),
key (`typography.key`, `colors.key`, `semantics { contentDescription = keyDescription }`). The key
style's 44sp line plus 2 × `spacing.sm` gives a ≥ 56dp row (DESIGN `song-row`) without a dp
literal. `positionLabel` is two-digit zero-padded (`"01"`), as in Stitch.

**7. Amber and D-17.** Only `BluesJamTheme.colors.key` is amber on this screen. Time remaining is
not one of the five amber uses, so Stitch's amber "ESTA NOCHE" badge is not copied. No
`MaterialTheme.*`, no palette, no color or dp literal.

**8. :app before bottom-navigation.** New `app/src/main/java/com/bbbjam/TemporaryTabs.kt`
(`internal`): `rememberSaveable` selected tab (start: **Próxima jam**), a `Column` with the selected
screen in `weight(1f)` (`contentPadding` = status-bar insets) and a bottom `Row` on `surface` with
`navigationBarsPadding()`, `selectableGroup()`, two equal cells `selectable(role = Role.Tab)`,
`heightIn(min = LocalMinimumInteractiveComponentSize.current)`, labels in `body`: selected `text`,
unselected `textMuted` (no amber, no icons). `MainActivity` calls `BluesJamTheme { TemporaryTabs()
}`; `BluesJamApp` adds `nextJamModule`; `app/build.gradle.kts` adds the project. Labels are private
constants in that file. **`bottom-navigation` then** deletes `TemporaryTabs.kt`, adds the bar with
three tabs and icons, the nav library decision, per-tab scroll state (switching here disposes the
other screen's scroll position), and the status-bar scrim. Neither screen's signature changes.

**9. Koin.** `di/NextJamModule.kt`: `val nextJamModule = module { factory { NextJamPresenter(get(),
get()) } }`.

**10. Copy in `internal object NextJamCopy`** (info-screen convention), table below.

## Copy (user approval requested; Rioplatense, vos)

| Key | Text |
|---|---|
| Tab labels (`:app`) | Próxima jam · Info |
| Date label | `<Día> <d> de <mes> · HH:MM` → "Sábado 31 de octubre · 21:00" |
| Days | Lunes, Martes, Miércoles, Jueves, Viernes, Sábado, Domingo |
| Months | enero … diciembre (lowercase; "septiembre") |
| `TONIGHT` / `TODAY` / `TOMORROW` | Esta noche / Hoy / Mañana |
| In n days | En n días |
| `NO_UPCOMING_JAM` | La próxima jam todavía no tiene fecha. Cuando se confirme, la vas a ver acá. |
| `SETLIST_WITHHELD` | La lista de temas se está armando. Cuando se publique, la vas a ver acá. |
| `SETLIST_UNAVAILABLE` | No se pudo leer la lista de temas de esta jam. Avisale a la organización. |
| Dropped rows (1 / n) | Falta 1 tema: no se pudo leer. / Faltan n temas: no se pudieron leer. |
| Key a11y | Tonalidad `<key>` |

Venue is `Jam.venue` verbatim (`Jams.lugar`). DESIGN.md asks for name **and address**; the Sheet has
one free-text `lugar`, currently "La Macanuda". No schema change: if the user wants the address,
they type it into `lugar` ("La Macanuda, Moreno 223").

## Acceptance Scenarios

1. **Published jam.** Given a cached upcoming published jam on 2026-10-31 21:00 at La Macanuda with
   the 13 songs of `2026-07-25`, and today 2026-10-02 in Buenos Aires, when Próxima jam opens, then
   the header reads "Sábado 31 de octubre · 21:00", "La Macanuda", "En 29 días", and 13 rows show
   "01"…"13", title and key (key in amber, `key` style), in position order.
2. **Boundaries.** Its own date ≥ 18:00 start → "Esta noche"; < 18:00 → "Hoy"; the day before →
   "Mañana"; at 23:30 Buenos Aires the night before (02:30 UTC on the jam date) → "Mañana", not
   "Esta noche".
3. **No upcoming jam** (the real Sheet today): after a successful fetch, the no-upcoming line; with
   nothing ever fetched, `Loading`.
4. **Dropped rows.** `Available(songs, droppedRows = 2)` → rows plus "Faltan 2 temas: no se
   pudieron leer."; positions with a gap stay "01", "03".
5. **Withheld / Unavailable.** Header plus the matching line; no row.
6. **App.** Launch opens Próxima jam; the tab row switches to Info and back; Info unchanged.
7. **Architecture.** `:feature:next-jam` depends on `:core:ui` and `:core:data` only; Konsist,
   detekt, ktlint `wired`.

## Repository Research

### Files Inspected

`AGENTS.md`, `PROGRESS.md` (sessions 045–047), `feature_list.json` (this entry and
`instrument-strip-component`, `song-row-expansion`, `instrument-filter-chips`, `list-states`,
`unpublished-setlist-state`, `bottom-navigation`), `DESIGN.md`, `bb-blues-jam-design-prompt.md` §1,
`docs/design/README.md`, `docs/design/screens/next-jam.png`, Stitch `pr_xima_jam_vista_m_sico/
code.html`, `CONTEXT.md`, `docs/domain-model.md`, `docs/sheet-schema.md` (`Jams`),
`docs/risks-and-open-questions.md`, `docs/api-samples/jams-seed.json`, `docs/sheet-seed/Jams.csv`,
bitácora D-01–D-20, architecture `SKILL.md` + `references/presenter-pattern.md`,
`docs/specs/info-screen.md`, all of `feature/info`, `app/` sources and build file,
`settings.gradle.kts`, `:core:model` `Jam`/`Setlist`/`JamSong`/`Key`/`Lineup`, `:core:data`
`JamsRepository`/`JamsSnapshot`/`JamCalendar`/`Freshness`/`DataFailure`/`DataModule`,
`:core:ui` `BluesJamColors`/`BluesJamTypography`/`BluesJamDimens`/`BluesJamTheme`,
`ModuleIsolationTest.kt`.

### Prototype (throwaway clone of `18fff24` in the session scratchpad; not the repo)

Decisions 1–4, 8, 9 and both proposed Konsist rules: `CI=true ./init.sh` exit 0, `konsist: wired`
(15/15), `detekt: wired`, `ktlint: wired`; `NextJamPresenterTest` 2/2 (loading → no upcoming →
full `Jam` model equality incl. "Sábado 31 de octubre · 21:00", "En 29 días", "01", "Tonalidad
Bm", "Falta 1 tema: no se pudo leer."; time-remaining boundaries); `:feature:next-jam` lint "No
issues found" (java.time fine with the convention's desugaring). Fixes needed on the way: detekt
`MaxLineLength` and `MayBeConstant` in the Konsist constants (wrap; `const val`). Probes (removed
after): `MaterialTheme.colorScheme.primary` and `BluesJamTheme.colors.primaryAction` in
`:feature:info`, `colors.slotOpen` in `:feature:next-jam` → `no-material-theme-outside-core-ui`
violated 1 time, `amber-roles-allowlisted` violated 2 times; removed → green. **Not prototyped:**
the device run (the Pixel 5 dropped off adb; nothing was installed), the `BluesJamColors.` branch
of the amber regex, the preview.

## Expected File Changes

- `settings.gradle.kts` — `include(":feature:next-jam")`.
- `feature/next-jam/.gitignore` (`/build`), `build.gradle.kts` (Decision 1).
- `feature/next-jam/src/main/kotlin/com/bbbjam/feature/nextjam/` — `NextJamUiModel.kt`,
  `NextJamCopy.kt`, `JamDateText.kt` (`timeRemaining`, `jamDateLabel`), `NextJamPresenter.kt`,
  `NextJamScreen.kt` (+ `@Preview`), `di/NextJamModule.kt`.
- `feature/next-jam/src/test/kotlin/com/bbbjam/feature/nextjam/` — `NextJamPresenterTest.kt`,
  `JamDateTextTest.kt`, `NextJamModuleTest.kt`, `FakeJamsRepository.kt`.
- `app/build.gradle.kts`, `BluesJamApp.kt`, `MainActivity.kt`, new `TemporaryTabs.kt` (Decision 8).
- `konsist-test/.../ModuleIsolationTest.kt` — only if the user approves G1 (below).
- Docs per Durable Documentation Impact.

## Visual Design Impact

UI: yes. Sources `DESIGN.md` (tokens, amber rules, song-row, 48dp, a11y), design prompt §1, Stitch
for layout only. States drawn: published list, dropped-rows note, the three plain lines, blank
loading. No new token or design artifact.

## Durable Documentation Impact

- `.claude/skills/architecture/SKILL.md` — update: `:feature:next-jam` exists (reads `:core:data`);
  `:app` row gains `TemporaryTabs` (until `bottom-navigation`); "today" for screens via
  `JamCalendar.today()` per snapshot; Spanish date names hand-written; if G1 approved, the two
  rules, their limits, the amber allowlist convention, 15 rules.
- `references/presenter-pattern.md` — update: `NextJamPresenter` is now compiled; the sketch's
  `SetlistRepository`/catalog lookup are superseded by `JamsRepository`; admin parts stay a sketch.
- `docs/risks-and-open-questions.md` — update: the "esta noche" phrasing settled; ticker still open;
  venue address question (whatever the user decides).
- `PROGRESS.md`, `feature_list.json` — evidence, status `passing`.
- `AGENTS.md`, `CONTEXT.md`, `DESIGN.md`, `domain-model.md` — not needed (unless the user changes
  the venue rule). `ARCHITECTURE.md`/`CONSTRAINTS.md` do not exist; the skill is the architecture doc.

## Implementation Plan

1. Baseline `CI=true ./init.sh` (expect 35 result files, Konsist 13/13).
2. Module skeleton, `NextJamCopy`, `JamDateText` + `JamDateTextTest` (red → green).
3. UiModel, presenter, `FakeJamsRepository`, `NextJamPresenterTest`, Koin module + test.
4. Screen + preview; `:app` wiring and `TemporaryTabs`.
5. If approved: Konsist G1 rules with their failure demonstrations.
6. `./gradlew ktlintFormat`, `CI=true ./init.sh`; negative demonstrations; device check; docs.

## Verification Plan

- `CI=true ./init.sh` exit 0, `konsist: wired` (13/13, or 15/15 with G1), `detekt: wired`,
  `ktlint: wired`; no baseline, `ignoreFailures`, `@Suppress` or rule disable.
- `JamDateTextTest`: all boundaries of Decision 4 (same day 21:00, 18:00, 17:59; +1; +2; +29; year
  rollover 2026-12-31 → 2027-01-02 = "En 2 días"; −1 clamps to the same-day phrase); date labels
  for all seven days and twelve months, and "Lunes 1 de febrero · 09:05" padding.
- `NextJamPresenterTest` (Molecule + Turbine, `FakeJamsRepository` on `MutableSharedFlow(replay =
  1)`, `JamCalendar(Clock.fixed(…), BUENOS_AIRES)`): (a) Loading, then a published jam as a full
  expected `NextJamUiModel.Jam` (scenario 1); (b) transition no-upcoming → jam appears; (c)
  never-fetched (with and without `lastFailure`) stays `Loading`; (d) dropped note 0/1/2, gap
  labels; (e) Withheld and each `SetlistProblem` → `NotShown`, header present; (f) Buenos Aires:
  clock `2026-10-31T02:30:00Z`, jam 2026-10-31 → "Mañana"; clock `2026-10-31T23:00:00Z` → "Esta
  noche". `NextJamModuleTest`: `nextJamModule` + fake repository/calendar resolves two distinct
  presenters.
- Negative demonstrations (restore, record SHA-1): `calendar.today()` replaced by
  `LocalDate.now(ZoneOffset.UTC)`-style UTC date → (f) fails; the `fetchedAt` guard removed → (c)
  fails; with G1, the three prototype probes fail their rules, plus `BluesJamColors.slotOpen` in
  `:feature:info`.
- Greps on `feature/next-jam/src/main` and `TemporaryTabs.kt`: no `MaterialTheme`, no amber role
  except `colors.key`, no `.dp` literal, no `LocalDate.now`/`Clock.system`, no tú forms
  (`puedes|tienes|quieres|eres|\bven\b`), no "Motivo".
- **Device (Pixel 5 `09281FDD4004U6`, manual, outside the gate)** — see User Manual Steps; the
  agent part: `./gradlew :app:installDebug`; `adb shell am force-stop com.bbbjam`; `adb logcat -c`;
  `adb shell am start -W -n com.bbbjam/.MainActivity`; wait for `jams cache:` in `adb logcat -d -s
  BluesJam`; `adb exec-out screencap -p`; `adb shell uiautomator dump` and check a node per row
  with text "01"…"13", the title, and the key node's `content-desc` "Tonalidad <key>"; tap Info and
  back; AndroidRuntime log empty.

## User Manual Steps (the device check needs real future data)

A. **Now (no upcoming jam):** nothing to do. The agent records the no-upcoming line and `jams cache:
   upcoming none, past 1`.
B. **Add a temporary jam** (or the real next one, if already decided — then skip E): in the Sheet,
   duplicate the tab `2026-07-25`, rename the copy `2026-10-31` (any future date works; the agent
   adjusts the expected text); in `Jams` add a row `2026-10-31 | 21:00 | La Macanuda | PUBLICADA`,
   cells as plain text like the existing row. Tell the agent. Expected: `jams refresh: updated 2
   jams …`, `jams cache: upcoming 2026-10-31, past 1, songs 26 (26 from catalog)`, header
   "Sábado 31 de octubre · 21:00 / La Macanuda / En N días" (N from the device date; 29 on 2 Oct),
   13 rows with the 25/07 titles and keys.
C. Optional: set that row's `estado` to `BORRADOR` → header plus "La lista de temas se está
   armando…", no rows. Then back to `PUBLICADA`.
D. Optional: rename the temporary tab and `fecha` to the check day's date → "Esta noche".
E. **Clean up:** delete the temporary `Jams` row and the tab, tell the agent; it relaunches and
   confirms `upcoming none` and the no-upcoming line again. Record that the Sheet is restored (the
   user's report; no agent reads the Sheet).

## Evidence To Capture

Gate output; per-class test counts; each negative demonstration with its failing message;
screenshots for A, B (top and scrolled to the end), C/D if done, and E; `BluesJam` log lines;
uiautomator excerpts; the copy as shipped. In `feature_list.json` and `PROGRESS.md`.

## Validator Checklist

- [ ] No strip, artist, expansion, filter, skeleton/error/offline, admin or nav library.
- [ ] Mapping table of Decision 3 holds; never-fetched is not "no jam".
- [ ] Today only from `JamCalendar`; boundary tests present and failing under a UTC date.
- [ ] Copy matches the approved table; vos; no invented venue fact.
- [ ] Only `colors.key` amber; no `MaterialTheme.*`, color or dp literal in the feature or tabs.
- [ ] Project deps `:core:ui`, `:core:data` only; three `wired`; G1 rules only if approved, each
      shown failing.
- [ ] Device evidence A, B, E with the user's cleanup confirmed.

## Needs User Approval Before Implementation

- **C1 Copy table** above, in particular: "Esta noche" vs "Hoy" split at 18:00, and "Esta noche"
  still shown after the start time; "En n días" with no weeks; the time as "21:00" (vs "21 h" or
  "21:00 hs"); the dropped-rows note wording (or not showing it at all); the unavailable line asking
  musicians to tell the organizers; zero-padded positions.
- **C2 Venue address:** show `lugar` verbatim and, if wanted, the admin writes the address into it —
  or add an address column later (a schema change, its own slice).
- **G1 D-17 Konsist rules (a verification change):**
  - `no-material-theme-outside-core-ui`: every file outside `core/ui` and `konsist-test` must not
    match `\bMaterialTheme\s*\.\s*(colorScheme|typography|shapes)\b`. Failing case: `color =
    MaterialTheme.colorScheme.primary` in `:feature:info` (prototyped: violated 1 time).
  - `amber-roles-allowlisted`: outside `core/ui` and `konsist-test`, every match of
    `\b(colors|BluesJamColors)\s*\.\s*(primaryAction|onPrimaryAction|slotOpen|key|published|onPublished|activeFilter|onActiveFilter)\b`
    must be in `AMBER_ROLE_ALLOWLIST` for its module; initially `mapOf("feature/next-jam" to
    setOf("key"))`. Each later slice that adds an amber use (`slotOpen`, `activeFilter`,
    `published`, `primaryAction`) adds it to the map in its own diff, so amber use becomes a
    reviewed decision. Failing case: `BluesJamTheme.colors.primaryAction` in `:feature:info`
    (prototyped: violated).
  - Limits: text match; an alias (`val p = BluesJamTheme.colors; p.key`) escapes; Material
    components left on default colors (amber `primary`) escape; it cannot judge whether an allowed
    role is used for the right element. Options: both (recommended), the first only, or neither
    (then the greps above stay the only guard).

## Risks

- No upcoming jam in the real Sheet: real-data proof depends on the user's temporary jam and its
  cleanup; a forgotten temporary jam would be shown to any musician who installs the app.
- A screen left open across midnight keeps the old split and phrase until the next emission.
- First offline launch with an empty cache shows a blank screen (`Loading`) until `list-states`.
- `TemporaryTabs` loses each tab's scroll position on switch; accepted until `bottom-navigation`.
- Two `songTitle`-sized texts plus a 44sp key may truncate long titles on a narrow phone
  (ellipsis by design); check on the device.
