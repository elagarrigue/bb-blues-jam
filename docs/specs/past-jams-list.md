# Feature Implementation Spec: List past jams in reverse chronological order

## Source Feature

- `id`: past-jams-list
- `area`: feature-past-jams
- `depends_on`: `catalog-repository-cache`, `design-tokens-theme`, `jams-repository-cache` (all
  `accepted`)
- `status`: not_started at planning time
- `source`: `feature_list.json` (entry, verification, notes: "D-04: past jams are read-only, Sheet
  is their authority")
- **Sequencing:** do not start implementing until `song-detail-screen` is `accepted`. That slice
  adds Navigation Compose and the first route host in `:app` and edits `TemporaryTabs.kt`,
  `BluesJamApp.kt`, `app/build.gradle.kts`, `settings.gradle.kts` and `feature/next-jam/` (copy,
  presenter, screen). This slice edits the same files (Technical Approach 1 and 8). Re-read them
  before starting.

## Goal

A musician can open **Anteriores** and see the jams that already happened, newest first. Each row
shows the date (with the year), the venue, how many songs were played and the first few titles, in
a muted archive treatment with no amber. Loading, error, empty and offline reuse the `list-states`
components. Read-only: the Sheet owns past jams (D-04), and there is nothing to mutate (D-13).

## Non-Goals

- Opening a past jam (`past-jam-detail`): rows are **not tappable** in this slice. That slice adds
  the row action, its route and a `Params` callback, as `song-detail-screen` did for Próxima jam.
- The bottom bar, a navigation route for this screen, per-tab state, scroll restoration across tab
  switches (`bottom-navigation`). Anteriores is reached through the temporary tab switch.
- Keys, lineups, musicians, the number of people who played (the design prompt's alternative
  hook), tempo, tags, difficulty, Songsterr (D-20), artwork or any external API (D-09).
- Admin controls of any kind; editing history is not supported (`docs/user-and-access-model.md`).
- Grouping by year, search, paging. Any change to `:core:data`, the Apps Script or the Sheet.

## Job Story

When I am deciding what to propose or checking what was played last month,
I want to scroll through past jams with their date, venue and first songs,
so I can see what has already been played and avoid repeating it by accident.

## Users And Permissions

- Musician and admin: identical read-only view. The admin flag is not read (D-15 does not apply:
  there are no controls to add).

## Acceptance Scenarios

1. **Newest first.** Given a snapshot whose `past` holds 2026-05-30, 2026-07-25, 2026-06-27 (out of
   order on purpose), when the presenter emits, then the rows are 2026-07-25, 2026-06-27,
   2026-05-30. The presenter sorts by date descending itself; it does not rely on the repository's
   order.
2. **Row content.** Given the 2026-07-25 jam at venue "X" with 13 available songs, then its row
   reads date "Sábado 25 de julio de 2026", venue "X", count "13 temas", and the hook with the
   first three titles in position order followed by "y 10 más" (C1).
3. **Short setlists.** One song → "1 tema" and the hook is that title; three songs → three titles,
   no "y n más"; four → three titles + "y 1 más".
4. **Withheld / unavailable past setlist.** A past `DRAFT` (`Setlist.Withheld`) or
   `Setlist.Unavailable` still gets a row (date, venue) with one line instead of count and hook
   (C1, P1); never "0 temas".
5. **Upcoming excluded.** `snapshot.upcoming` never appears in the list, whatever its date.
6. **States** (Technical Approach 3 table): skeleton before any read; error block with retry when nothing was
   ever fetched and the read failed; empty block when fetched and `past` is empty; the staleness
   notice above the rows exactly when `fetchedAt != null && lastFailure != null`.
7. **Retry** re-subscribes to `observeJams()` and calls `refresh()` once; no refresh without it.
8. **No amber.** No amber role is read in `:feature:past-jams`; `AMBER_ROLE_ALLOWLIST` has no entry
   for it, and the row colours come from `PastJamsDefaults` (Technical Approach 5).

## Repository Research

### Files Inspected

`AGENTS.md`, `PROGRESS.md`, `feature_list.json`, `CONTEXT.md`, `DESIGN.md`,
`bb-blues-jam-design-prompt.md` (screens 4 and 5, states), `.claude/skills/architecture/SKILL.md`,
`docs/specs/list-states.md`, `docs/specs/jams-repository-cache.md` (grep: withheld/past),
`docs/specs/song-detail-screen.md` (grep: files and navigation), `docs/sheet-schema.md` (grep),
`docs/domain-model.md` (grep); code: `NextJamPresenter.kt`, `NextJamUiModel.kt`, `NextJamCopy.kt`,
`JamDateText.kt`, `NextJamScreen.kt` (head), `NextJamModule.kt`, `NextJamModuleTest.kt`,
`FakeJamsRepository.kt` (signatures), `feature/next-jam/build.gradle.kts`, `core/ui/.../state/*`,
`BluesJamColors.kt`, `ContrastTest.kt` (test names), `ModuleIsolationTest.kt` (allowlist),
`Jam.kt`, `Setlist.kt`, `JamsSnapshot.kt`, `JamsRepository.kt`, `TemporaryTabs.kt`,
`BluesJamApp.kt`, `app/build.gradle.kts`, `settings.gradle.kts`. Not inspected: `docs/domain-model.md`
and `docs/sheet-schema.md` in full, the Stitch export.

### Existing Patterns To Follow

- `NextJamPresenter` + `JamsSnapshot.toUiModel(...)`: a pure mapping tested without Molecule, the
  retry counter pattern, `calendar.now()` read once per snapshot, `listError`/`stalenessNotice`.
- `JamsSnapshot.past` is already newest first and its available lineups keep only filled slots
  (P6); titles are catalog-resolved. Past `Setlist` may be `Available`, `Withheld` (past draft) or
  `Unavailable` (`jams-edge.json` has all three).
- Copy in an `internal object <Name>Copy`; colours in a JVM-testable `…Defaults` object
  (`ListStateDefaults`); `BluesJamColors` is an `object`, so a feature test can read roles.
- Konsist reads module groups from paths and checks every included module; a module with no
  allowlist entry may read no amber role.

### Current Gaps

- Spanish day and month names live in `NextJamCopy` (internal to `:feature:next-jam`); a second
  feature cannot use them (Technical Approach 1).
- `ContrastTest` has no case for the `archive` token (measured here: 5.39:1 on `surface`).

## Technical Approach

**1. Shared date names (`:core:ui`).** Move `dayName(DayOfWeek)` and `monthName(Month)` verbatim
from `NextJamCopy` to a public `object SpanishDateNames` in `com.bbbjam.core.ui.text`
(`day(...)`, `month(...)`), with `SpanishDateNamesTest` covering all 7 days and 12 months.
`JamDateText.jamDateLabel` calls it; `NextJamCopy` loses the two tables. `JamDateTextTest` must
pass unchanged (it proves no regression). Reason: the architecture rule "a thing used by two
features goes to `:core:ui`, never copied"; still hand-written tables, not `DateTimeFormatter`.

**2. Module `:feature:past-jams`**, package `com.bbbjam.feature.pastjams`, build file a copy of
`feature/next-jam/build.gradle.kts` with namespace `com.bbbjam.feature.pastjams`; project deps
`:core:ui` and `:core:data` only; own `.gitignore` (`/build`). Included in `settings.gradle.kts`.

**3. Presenter and mapping.** `class PastJamsPresenter(jams: JamsRepository, calendar: JamCalendar)
: Presenter<PastJamsUiModel, Unit>`, same body shape as `NextJamPresenter` minus expansion and
filter: subscription counter, `remember(subscription) { jams.observeJams() }.collectAsState(null)`,
`now = remember(snapshot) { calendar.now() }`, `onRetry` = `subscription++` +
`scope.launch { jams.refresh() }`. `internal fun JamsSnapshot.toUiModel(now, onRetry)`:

| Condition (checked in order) | Model |
|---|---|
| `past` non-empty | `Jams(rows, staleness)` |
| `fetchedAt != null` (and `past` empty) | `Empty(EmptyStateUiModel, staleness)` |
| `lastFailure != null && !isRefreshing` | `Failed(listError(LOAD_FAILED, isOffline, onRetry))` |
| otherwise (incl. no emission) | `Loading(LOADING)` |

`staleness` = `stalenessNotice(...)` exactly when `fetchedAt != null && lastFailure != null`,
`isOffline = lastFailure is DataFailure.Offline` (identical to Próxima jam). Rows:
`past.sortedByDescending { it.date }.map { it.toRow() }`.

**4. UiModel** (`PastJamsUiModel.kt`): `sealed interface PastJamsUiModel : UiModel` with
`title: String` on every variant ("Jams anteriores"), variants `Loading(title, description)`,
`Failed(title, error)`, `Empty(title, empty, staleness)`, `Jams(title, rows, staleness)`.
`PastJamRowUiModel(date: LocalDate, dateLabel, venue, summary: PastJamSummary)` with
`sealed interface PastJamSummary : UiModel { Songs(countLabel, hook); NotShown(message) }`.
`hook` = first `HOOK_TITLES = 3` titles in position order joined by ", ", then " y n más" when
there are more (C1). `Withheld` → `NotShown(SETLIST_NOT_PUBLISHED)`, `Unavailable` →
`NotShown(SETLIST_UNAVAILABLE)`, and an `Available` with no songs (rare, but legal) →
`NotShown(EMPTY_SETLIST)`, never a "0 temas" count (C1). Dropped rows
are not counted: the count is the readable songs, matching the rows `past-jam-detail` will draw
with its own dropped-rows note.

**5. Archive treatment** (`PastJamsDefaults`, internal, in the feature): `rowStyle(colors:
BluesJamColors = BluesJamColors)` returns fill `surface`, date `textMuted`, venue and count
`textMuted`, hook `archive`. `PastJamsDefaultsTest`: every colour is one of `surface`,
`textMuted`, `archive`, and none equals the amber palette value (compare with `colors.key`). The
screen reads colours only through it. `ContrastTest` (`:core:ui`) gains `archive on a surface`
(expected 5.39) — the hook is `body` size, so ≥ 4.5 holds.

**6. Screen** (`PastJamsScreen(modifier, contentPadding, presenter = koinInject())` →
`PastJamsContent(model, modifier, contentPadding)`), padding as in `NextJamContent`. A
`LazyColumn`: the title (`h1`, `text`, heading semantics; it is screen chrome, not archive
content, and is drawn in every state), then by state:
`SkeletonList(description)`; `ListErrorBlock`; the notice item (key `"staleness"`) then
`EmptyStateBlock` or the rows (key = ISO date), spaced `spacing.sm`. Row: `Surface(fill,
shapes.md)`, padding `spacing.md` × `spacing.sm`, `Column`: date (`songTitle`), a `Row` with venue
(`body`, weight 1, 1 line, ellipsis) and count (`body`), then the hook (`body`, max 2 lines,
ellipsis) or the `NotShown` message (`body`, `textMuted`). Not clickable, no role, no ripple, no
chevron; `semantics(mergeDescendants = true)` so each row is one node read in visual order. No dp,
sp or colour literal, no `MaterialTheme`. `PastJamsStatesPreview.kt`: Loading, Error offline,
Empty, Jams (three rows incl. a withheld one), Offline with rows.

**7. Koin.** `di/PastJamsModule.kt`: `val pastJamsModule = module { factory { PastJamsPresenter(get(),
get()) } }`; `PastJamsModuleTest` like `NextJamModuleTest` (factory, not single).

**8. Reachable before `bottom-navigation`** (`:app`). Add `implementation(project(":feature:past-jams"))`,
`pastJamsModule` to `startKoin`, and **Anteriores** as the middle entry of the temporary tab switch
(order Próxima jam, Anteriores, Info, as DESIGN.md's bar), passing the status-bar insets as
`contentPadding`. If `song-detail-screen` left the tabs as a state switch, add a `Tab` value; if it
turned them into Navigation Compose destinations, add one top-level destination with no argument
the same way. No new dependency, no deeplink, no change to the song-detail route. Update the
KDoc ("three-tab switch").

## Expected File Changes

- `core/ui/src/main/kotlin/com/bbbjam/core/ui/text/SpanishDateNames.kt` — create; test beside it.
- `core/ui/src/test/.../theme/ContrastTest.kt` — modify (one case).
- `feature/next-jam/.../NextJamCopy.kt`, `JamDateText.kt` — modify (Technical Approach 1 only).
- `feature/past-jams/` — create: `.gitignore`, `build.gradle.kts`, `PastJamsPresenter.kt`,
  `PastJamsUiModel.kt`, `PastJamsCopy.kt`, `PastJamsDefaults.kt`, `PastJamsScreen.kt`,
  `PastJamsStatesPreview.kt`, `di/PastJamsModule.kt`; tests `PastJamsStatesTest`,
  `PastJamsPresenterTest`, `PastJamsDefaultsTest`, `PastJamsModuleTest`, `FakeJamsRepository`
  (own copy: test fakes are not shared across features).
- `settings.gradle.kts`, `app/build.gradle.kts`, `BluesJamApp.kt`, `TemporaryTabs.kt` (or the file
  `song-detail-screen` leaves for the tabs) — modify.
- `konsist-test/` — no change (no new rule; allowlist untouched).
- Docs: see below.

## Visual Design Impact

- UI involved: yes. Source: `DESIGN.md` ("Past jams list", "Required States", tokens) and
  `bb-blues-jam-design-prompt.md` §4. New artifact: no.
- States: loading, error (offline/other), empty, list, list with staleness notice.
- No amber anywhere on this screen: the list shows no keys, so "amber only on keys" means none
  here. `past-jam-detail` will add `key` to the allowlist for `feature/past-jams`.

## Durable Documentation Impact

- `.claude/skills/architecture/SKILL.md`: update — `:feature:past-jams` exists (deps), the shared
  `SpanishDateNames`, the third temporary tab.
- `DESIGN.md`: update "Past jams list" with the row layout, archive colours and the C1 copy.
- `PROGRESS.md`, `feature_list.json`: update (evidence, status `passing`).
- `ARCHITECTURE.md`, `CONSTRAINTS.md`: not present in this repo; not needed. `AGENTS.md`: not
  needed. `CONTEXT.md`, `docs/domain-model.md`: not needed (no new term or rule).

## Implementation Plan

1. Confirm `song-detail-screen` is `accepted`; run `CI=true ./init.sh` for a baseline.
2. Technical Approach 1 (`SpanishDateNames`), gate green with `JamDateTextTest` untouched.
3. Module skeleton, copy, UiModel, pure mapping and its tests; then presenter and its tests.
4. Defaults, `ContrastTest` case, screen, previews, Koin module and test.
5. `:app` wiring and the temporary tab; failure demonstrations; docs; final gate.

## Implementation Tasks

- [ ] `SpanishDateNames` + test; `NextJamCopy`/`JamDateText` use it.
- [ ] `:feature:past-jams` module included, convention applied, deps `:core:ui` + `:core:data`.
- [ ] `PastJamsCopy` with the approved C1 strings; `pastJamDateLabel(date)` →
      "Sábado 25 de julio de 2026".
- [ ] `toUiModel` table, row mapping, hook, `NotShown` cases; `PastJamsStatesTest`.
- [ ] `PastJamsPresenterTest` (Molecule + Turbine): out-of-order input → newest first; Loading →
      Jams transition; Failed → Retry (`refreshCalls == 1`, `subscriptions == 2`) → Loading while
      refreshing → Jams; notice on failure, gone on success; no notice on age alone; no refresh
      without Retry; empty snapshot after a fetch → `Empty`; upcoming never listed.
- [ ] `PastJamsDefaults` + test; `ContrastTest` `archive on a surface`.
- [ ] Screen, previews, Koin module + test, `:app` wiring, temporary tab.
- [ ] Docs updates.

## Verification Plan

- `CI=true ./init.sh` exit 0, ending `konsist: wired` (16/16), `detekt: wired`, `ktlint: wired`;
  every previous result file unchanged in counts except `ContrastTest` +1, plus the new classes.
- Failure demonstrations (edit, run, restore; record each message): (1) drop the
  `sortedByDescending` and feed out-of-order input → the ordering test fails; (2) `colors.key` in
  `PastJamsScreen.kt` → `amber-roles-allowlisted` fails for `feature/past-jams`; (3) `colors.key`
  as the hook colour in `PastJamsDefaults` → `PastJamsDefaultsTest` fails; (4) remove the empty
  branch → the empty-state test fails; (5) a `4.dp` in `PastJamsScreen.kt` →
  `no-dp-literal-outside-core-ui` fails.
- Greps on `feature/past-jams/src/main`: no `.dp`/`.sp` literal, no `Color(`, no `MaterialTheme.`,
  no `clickable`, no amber role, no spinner, no tú forms.
- Previews compile in the gate; not rendered by it (no Compose UI test harness, T1 declined).
- **Device, outside the gate, optional** (Pixel 5 if attached; the planner did not use it): install,
  open Anteriores → one row for 2026-07-25 ("Sábado 25 de julio de 2026", its venue, its song count,
  three titles); `uiautomator dump` shows one node per row, not clickable; airplane mode + relaunch
  after > 60 s → the notice above the row. Restore settings; crash buffer empty. Never log the URL.

## Evidence To Capture

Gate output and counts, each demonstration's failure message, the contrast value, grep results,
and (if run) the device dump excerpt and screenshot, in `feature_list.json` and `PROGRESS.md`.

## Validator Checklist

- [ ] Waited for `song-detail-screen`; no navigation library or route added by this slice.
- [ ] `:feature:past-jams` depends only on `:core:ui` and `:core:data`; no feature→feature import.
- [ ] Presenter sorts newest first, tested with out-of-order input; upcoming never listed.
- [ ] State table matches Technical Approach 3; Retry is a read; no notice on age alone.
- [ ] No amber read, no allowlist entry; row colours only from `PastJamsDefaults`; rows not tappable.
- [ ] Copy matches C1; date names come from `SpanishDateNames`; `JamDateTextTest` unchanged.
- [ ] No mutation, no admin control, no external API, no `:core:data` change.
- [ ] Demonstrations recorded; three `wired`; docs updated.

## Risks

- The temporary tab switch disposes the screen, so the list's scroll position is lost on a tab
  change (as today for the other tabs) until `bottom-navigation`.
- Two presenters now collect `observeJams()`; each collection may start the repository's throttled
  background refresh (single-flight, 60 s), so at most one extra request.
- Titles are catalog-resolved at read time: a catalog edit changes a past jam's hook. Accepted (the
  Sheet owns both).

## User Approvals

Answered by the user on 4 October 2026: **C1** copy approved as written; **P1** list a past draft
with the line `La lista de esta jam no se publicó.` (recommended).

The questions as asked:

- **C1 — copy** (Rioplatense, vos). *Recommended:* approve as written.

  | Use | Text |
  |---|---|
  | Tab label and screen title | `Anteriores` (tab), `Jams anteriores` (title) |
  | Date | `Sábado 25 de julio de 2026` (day, date, year; no time) |
  | Count | `1 tema` / `13 temas` |
  | Hook | `Sweet Home Chicago, The Thrill Is Gone, Pride and Joy y 10 más` |
  | Past draft (`Withheld`) | `La lista de esta jam no se publicó.` |
  | `Unavailable` | `No se pudo leer la lista de esta jam.` |
  | Available, no songs | `Esta jam no tiene temas cargados.` |
  | Loading (screen reader) | `Cargando las jams anteriores` |
  | Error title | `No pudimos cargar las jams anteriores` |
  | Empty title / message | `Todavía no hay jams anteriores` / `Después de cada jam, su lista queda guardada acá para que veas qué se tocó.` |

- **P1 — past drafts.** A past jam whose status is still `BORRADOR` is listed with the "no se
  publicó" line (C1). *Recommended:* list it — the jam happened and the Sheet says so; hiding it
  would make the archive silently incomplete. Alternative: omit past drafts from the list.
