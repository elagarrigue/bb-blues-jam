# Feature Implementation Spec: Loading, empty, error and offline states

## Source Feature

- `id`: list-states
- `area`: feature-next-jam
- `depends_on`: `next-jam-read-only-list` (`accepted`)
- `status`: not_started at planning time
- `source`: `feature_list.json` (entry, verification and notes)
- **Sequencing:** builds on the code of `instrument-filter-chips` (commit `a2c2c10`, `passing`,
  being validated). Both edit `NextJamPresenter`, `NextJamUiModel` and `NextJamScreen`, so do not
  start implementing until that slice is `accepted`. If the validator asks for a revision, re-read
  those three files before you start.

## Goal

Próxima jam draws every non-happy path as its own designed state, not a blank background:
**skeleton rows** while nothing has been read yet, a **concrete invitation** when there is nothing
to list, an **error block with a retry button** when nothing is cached and the read failed, and the
**cached data with a staleness notice** when a refresh fails but something is cached. The skeleton,
error block, staleness notice and empty block are `:core:ui` components, because `past-jams-list`
needs the same four states.

## Non-Goals

- **Filter with no results** belongs to `instrument-filter-chips` (S1 of that spec, approved). This
  slice does not restyle it or change its behaviour. `SetlistUiModel.Songs.filterBar` stays nullable
  and untouched, even though `Songs` is never empty after this slice.
- The withheld (draft) and unavailable setlist lines stay as they are (`unpublished-setlist-state`).
- Pull-to-refresh, a periodic refresh, a ticker that ages the staleness text while the screen stays
  open, a connectivity listener, a refresh outcome toast or snackbar.
- The catalog's flow and every other screen. `past-jams-list` reuses the components later.
- No mutation (D-13): retrying a **read** calls `JamsRepository.refresh()`, an existing function.
- No new Gradle dependency, colour token, typography or spacing token. No `init.sh` change.

## Job Story

When I open Próxima jam in a basement bar with no signal, I want to see the last list I had and
know how old it is, or, if I never had one, a clear message and a way to try again, so I am never
left looking at a blank screen or a list I wrongly think is current.

## Users And Permissions

Anyone; read only. The retry action re-reads the jams; it writes nothing and needs no admin flag.

## Decisions

**1. State table** (presenter, pure `JamsSnapshot.toUiModel`). `f` is `snapshot.freshness`; `now`
is `calendar.now()` read beside `today` in `remember(snapshot)`.

| Input | Model |
|---|---|
| no emission yet | `Loading(description)` |
| `upcoming == null`, `f.fetchedAt == null`, and `f.lastFailure == null` or `f.isRefreshing` | `Loading(description)` |
| `upcoming == null`, `f.fetchedAt == null`, `f.lastFailure != null`, not refreshing | `Failed(error)` (offline vs other, Decision 5) |
| `upcoming == null`, `f.fetchedAt != null` | `NoUpcomingJam(empty, staleness)` |
| upcoming, `Setlist.Available(emptyList())` | `Jam(header, SetlistUiModel.Empty(empty), staleness)` |
| upcoming, any other setlist | as today (`Songs` / `NotShown`), plus `staleness` |

`staleness` is non-null exactly when `f.fetchedAt != null && f.lastFailure != null`. Age alone
(older than 30 minutes, no failure) draws **no** notice: collecting the flow starts a refresh in
that case, and a notice that flashes on every open would be noise. The current test asserting
`Loading` for `fetchedAt == null` + `Offline` changes to `Failed`: that is this slice's point.
`Setlist.Available` with no songs always has `droppedRows == 0` (domain invariant), so `Empty`
never loses a dropped-rows note.

**2. UiModel changes** (`NextJamUiModel.kt`):
- `Loading` becomes `data class Loading(val description: String)` (copy in the model, D-12 rule).
- New `data class Failed(val error: ListErrorUiModel)`.
- `NoUpcomingJam(val empty: EmptyStateUiModel, val staleness: StalenessNoticeUiModel?)` replaces
  `NoUpcomingJam(message)`; the approved no-upcoming sentence becomes `empty.message`.
- `Jam(header, setlist, staleness: StalenessNoticeUiModel?)`.
- `SetlistUiModel.Empty(val empty: EmptyStateUiModel)`.

**3. `:core:ui` components, new package `com.bbbjam.core.ui.state`** (shared with
`past-jams-list`; `primaryAction` read only here, so `AMBER_ROLE_ALLOWLIST` stays `{key}`):
- `ListStateUiModel.kt`: `EmptyStateUiModel(title, message)`; `ListErrorUiModel(title, message,
  retryLabel, events: EventHandler<Event>)` with `Event.Retry`; `StalenessNoticeUiModel(title,
  detail, retryLabel: String?, events: EventHandler<Event>)` with `Event.Retry` (`retryLabel` null
  while refreshing, so no action is drawn).
- `ListStateMapper.kt` (pure, JVM-tested): `listError(title, isOffline, onRetry)` and
  `stalenessNotice(isOffline, age: Duration, isRefreshing, onRetry)`. A negative age (clock skew)
  is clamped to zero.
- `ListStateCopy.kt` (internal): the generic copy (error messages, notice titles, ages, labels).
  Feature-specific titles and the skeleton description stay in `NextJamCopy`.
- `ListStateDefaults.kt` (internal): `retryButtonStyle(colors)` = fill `primaryAction`, content
  `onPrimaryAction`; `skeletonFill(colors)` = `surfaceRaised` bars on `surface` rows;
  `noticeFill(colors)` = `surfaceRaised`. JVM-tested; no style uses `slotOpen` or `activeFilter`.
- Composables, each taking only its UiModel, each with `@Preview`s:
  - `SkeletonList(description, modifier)`: a header placeholder (three bars) and **five** row
    placeholders. Row: `Surface(surface, shapes.md)`, padding `spacing.md` × `spacing.sm`,
    `Column(spacedBy(spacing.sm))` with a title bar (height `spacing.lg`, width fraction from
    `ListStateDefaults`) and a strip bar (height `spacing.md`): at least 64dp, close to a real
    collapsed row, with no dp literal. **Static, no shimmer or pulse** (battery and WCAG 2.2.2, as
    the strip's dot). The whole list is one node: `clearAndSetSemantics { contentDescription =
    description }`.
  - `ListErrorBlock(model)`: title (`songTitle`, `text`, heading semantics), message (`body`,
    `textMuted`), then the retry button: full width, `heightIn(min =
    LocalMinimumInteractiveComponentSize.current)`, `shapes.md`, the defaults' fill, label `body`,
    `clickable(role = Role.Button)` → `Event.Retry`. No Material `Button` (its defaults are not
    design decisions), no icon.
  - `StalenessNotice(model)`: `Surface(surfaceRaised, shapes.md)`, padding `spacing.md`; title
    (`body`, `text`, heading), detail (`body`, `textMuted`, `liveRegion = Polite` so "Actualizando…"
    is announced), then, when `retryLabel != null`, a text action like the filter's clear action
    (`body`, `text`, underlined, 48dp min height, `Role.Button`). **Not amber**: on the offline
    screen the data is the content, the retry is secondary (Stitch `next-jam-offline.png`).
  - `EmptyStateBlock(model)`: title (`songTitle`, `text`, heading) and message (`body`,
    `textMuted`). No illustration (DESIGN.md: minimal illustration or none).

**4. Retry** (presenter). `val scope = rememberCoroutineScope()`; `var subscription by remember {
mutableIntStateOf(0) }`; `remember(subscription) { jams.observeJams() }.collectAsState(null)`.
`onRetry` = `subscription++` then `scope.launch { jams.refresh() }`. Re-subscribing recovers from a
failed local read (Decision 6); `refresh()` bypasses the 60 s retry throttle and is single-flight,
so a double tap costs one request. The outcome is ignored: the flow reports it through `Freshness`
(`isRefreshing` turns `Failed` into `Loading` and the notice into "Actualizando…"). Like the other
presenter state, the handler is created once and must keep working from an earlier model (test it).
The presenter still never starts a refresh on its own: `refreshCalls == 0` until a Retry event.

**5. Offline vs other failure.** `isOffline = f.lastFailure is DataFailure.Offline`. Every other
kind (`NotConfigured`, `Service`, `InvalidResponse`, `Storage`) is "other". The kind and any detail
never reach the musician (as with `SetlistProblem`).

**6. A failed local read must not crash (`:core:data`, narrow supporting fix).** The architecture
skill records that "the read Flows still throw on a Room read exception (left to `list-states`)";
today `collectAsState` would crash the app. In `DefaultJamsRepository.observeJams()`, catch
`android.database.SQLException` only (never `Exception`; cancellation is an
`IllegalStateException`) with `Flow.catch` and emit one `JamsSnapshot(null, emptyList(),
Freshness(null, DataFailure.Storage(<class>), isRefreshing = false))`, then complete. That reaches
`Failed`, and Retry re-subscribes. Other throwables are rethrown. The catalog flow is not touched
(not read by this screen). Needs approval S1 because it leaves the feature's area.

**7. Layout** (`NextJamScreen.kt`). `Loading` → `SkeletonList` inside the existing padding.
`Failed` → `ListErrorBlock` at the top of the padded area (not vertically centred: it stays under
the thumb-free top, like the other messages). `NoUpcomingJam` → a `LazyColumn` with the notice item
(key `"staleness"`) when present, then `EmptyStateBlock`. `Jam` → the notice item first, above the
header (Stitch offline layout), then header, then the setlist; `SetlistUiModel.Empty` →
`EmptyStateBlock` item (key `"empty"`), no filter bar. Previews: one new file
`NextJamStatesPreview.kt` with a `@Preview` per state — Loading, Empty (no upcoming jam), Empty
(published setlist with no songs), Error offline, Error other, Offline with data, Offline while
refreshing — all through `NextJamContent`.

**8. Contrast.** `ContrastTest` gains `content on the retry button` (`onPrimaryAction` on
`primaryAction`, read through `ListStateDefaults.retryButtonStyle`) and `muted text on the notice`
(`textMuted` on `noticeFill`), with the measured values. Existing pairs are unchanged.

## Copy (user approval requested, C1; Rioplatense, vos, D-12)

| Where | Text |
|---|---|
| Skeleton description (NextJamCopy) | `Cargando la próxima jam` |
| Error title (NextJamCopy) | `No pudimos cargar la próxima jam` |
| Error message, offline (ListStateCopy) | `No hay conexión y todavía no hay nada guardado. Revisá los datos o el wifi y probá de nuevo.` |
| Error message, other | `Algo falló al leer los datos. Probá de nuevo en un rato; si sigue fallando, avisale a la organización.` |
| Retry (button and notice action) | `Reintentar` |
| Notice title, offline / other | `Sin conexión` / `No se pudo actualizar` |
| Notice detail | `Mostrando lo guardado hace 3 horas.` |
| Notice detail while refreshing | `Actualizando…` |
| Ages | `hace menos de un minuto`, `hace 1 minuto`, `hace n minutos`, `hace 1 hora`, `hace n horas`, `hace 1 día`, `hace n días` (whole units, rounded down; < 60 min minutes, < 24 h hours) |
| No upcoming jam: title / message | `Todavía no hay fecha` / the approved `La próxima jam todavía no tiene fecha. Cuando se confirme, la vas a ver acá.` |
| Empty setlist: title / message | `Todavía no hay temas` / `La lista está publicada pero todavía no tiene temas. ¿Tenés uno en mente? Contáselo a la organización.` |

## Acceptance Scenarios

1. First launch, network up: before any fetch the screen shows skeleton rows, never a spinner and
   never "no jam"; then the jam.
2. First launch, no network: skeleton, then the offline error with `Reintentar`; with the network
   back, tapping it shows the skeleton again, then the jam. `refreshCalls` is 1 after one tap.
3. Same with `Service`/`InvalidResponse`/`NotConfigured`/`Storage`: the "other" message.
4. Cached jam, refresh fails offline: the header and rows are drawn (filter bar included) with
   `Sin conexión` / `Mostrando lo guardado hace 2 horas.` / `Reintentar` above the header; no
   error block. Tapping it shows `Actualizando…` and no action; success removes the notice.
5. Cached jam older than 30 minutes, no failure: no notice.
6. Cached, no upcoming jam, refresh failed: notice plus the no-upcoming empty block.
7. Published upcoming jam with zero songs: header, then the empty-setlist block; no filter bar.
8. A filter that matches nothing still shows the filter's own no-results block, not `Empty`.
9. The local read throws `SQLException`: no crash; the "other" error; Retry re-subscribes and, once
   the read works, the jam appears.
10. Withheld and unavailable setlists render exactly as before, plus the notice when failing.

## Repository Research

**Inspected:** `AGENTS.md`, `PROGRESS.md` (state, sessions 050–061), `feature_list.json` (this
entry and its notes, `unpublished-setlist-state`, `past-jams-list`), architecture `SKILL.md`,
`DESIGN.md` (tokens, Colors, Components, Required States, Accessibility, Dos and Don'ts),
`bb-blues-jam-design-prompt.md` ("Estados que hay que diseñar"), `docs/design/README.md`, Stitch
`next-jam-error.png` and `next-jam-offline.png` (layout only; rejected: "Fede", the HTTP code, "Ver
datos guardados anteriormente", the tips box, icons, the disabled "Anotarme" buttons),
`docs/risks-and-open-questions.md`, `docs/domain-model.md` (Offline), `docs/technical-discovery.md`
(cache), specs `next-jam-read-only-list`, `song-row-expansion`, `instrument-filter-chips`;
`feature/next-jam` main sources and `FakeJamsRepository`, `NextJamPresenterTest` (head and Loading
cases); `:core:data` `Freshness`, `DataFailure`, `JamsRepository`, `JamsSnapshot`,
`DefaultJamsRepository`, `SyncStates`, `JamCalendar`, `DefaultJamsRepositoryTest`
(`FailingJamsDao`); `:core:ui` `EventHandler`, `BluesJamColors`, `BluesJamDimens`,
`ContrastTest` names; `ModuleIsolationTest` (colour rule shape). Grep: no `.dp`/`.sp` literal in
`app/` or `feature/*` today; in `:core:ui` only in `theme/` (`BluesJamDimens`, `BluesJamTypography`,
`ThemeShowcase`). **Not inspected:** a device; Android Studio preview rendering.

## Expected File Changes

- `core/ui/src/main/kotlin/com/bbbjam/core/ui/state/` — create the files of Decision 3.
- `core/ui/src/test/.../state/ListStateMapperTest.kt`, `ListStateDefaultsTest.kt` — create;
  `core/ui/src/test/.../theme/ContrastTest.kt` — two pairs.
- `feature/next-jam/.../NextJamUiModel.kt`, `NextJamPresenter.kt`, `NextJamCopy.kt`,
  `NextJamScreen.kt` — modify; `NextJamStatesPreview.kt` — create.
- `feature/next-jam/src/test/.../NextJamPresenterTest.kt` (Loading/NoUpcomingJam expectations, new
  cases), `NextJamFilterTest.kt` (`Loading` literal), `FakeJamsRepository.kt` (comment; a
  subscription counter) — modify; `NextJamStatesTest.kt` — create.
- `core/data/.../jams/DefaultJamsRepository.kt` and `DefaultJamsRepositoryTest.kt` — Decision 6.
- Only if K1 is approved: `konsist-test/.../ModuleIsolationTest.kt` — one rule.

## Durable Documentation Impact

- `DESIGN.md` "Required States": what each state draws, the no-notice-on-age rule, static
  skeleton, the components and their package.
- Architecture `SKILL.md`: the `state` package as the fourth shared-component example; the retry
  pattern (Decision 4); replace "the read Flows still throw … (left to `list-states`)" with the
  jams-flow behaviour (catalog flow still throws); the 16th rule if K1 is approved.
- `docs/risks-and-open-questions.md`: notice age frozen while the screen stays open; no automatic
  refresh after 30 minutes without re-entering; catalog read flow still throws; previews not
  verified by the gate.
- `docs/technical-discovery.md`: one sentence on how the staleness notice reads `Freshness`.
- `PROGRESS.md`, `feature_list.json` — evidence. `AGENTS.md`, `CONTEXT.md`, `domain-model.md` —
  not needed.

## Implementation Plan

1. Baseline `CI=true ./init.sh` (record result files, test count, Konsist 15/15).
2. `:core:data` catch (Decision 6) with a test, red → green.
3. `:core:ui` `state` copy, mapper, defaults and tests; `ContrastTest`; composables and previews.
4. UiModel and presenter (Decisions 1, 2, 4, 5); presenter and states tests.
5. Screen, `NextJamStatesPreview.kt`; `./gradlew ktlintFormat`; gate; demonstrations; greps.
6. If K1 approved: the rule, its failing case, gate. Then device check and docs.

## Verification Plan

- `CI=true ./init.sh` exit 0; `konsist: wired` (15/15, or 16/16 with K1), `detekt: wired`,
  `ktlint: wired`; no baseline, `ignoreFailures`, `@Suppress` or rule disable. Per-class counts.
- `ListStateMapperTest` (strings written out): offline/other error; notice titles; every age
  boundary (0 s, 59 s, 60 s, 59 min, 60 min, 23 h 59, 24 h, 48 h, negative); refreshing → detail
  `Actualizando…` and `retryLabel == null`; both handlers call `onRetry`.
- `ListStateDefaultsTest`: retry = `primaryAction`/`onPrimaryAction`; skeleton and notice fills;
  no style uses `slotOpen` or `activeFilter`.
- `NextJamStatesTest` (pure `toUiModel`, every row of the Decision 1 table, strings written out)
  and Molecule cases in `NextJamPresenterTest`: skeleton → Failed → Retry (`refreshCalls == 1`,
  re-subscribed) → Loading while refreshing → Jam; notice appears on failure and disappears on
  success; no notice on age alone; an earlier model's Retry handler still works; no refresh without
  a Retry event; `Empty` vs filter no-results; withheld unchanged.
- `DefaultJamsRepositoryTest`: a DAO whose `observeJams()` throws `SQLException` → one snapshot with
  `Storage` and `fetchedAt == null`; a non-`SQLException` is rethrown.
- **Failure demonstrations** (edit, run, restore, sha1): (1) notice on `isStale(now)` instead of
  `lastFailure` → the age-only test fails; (2) `Failed` drawn while refreshing → a presenter test
  fails; (3) retry button reading `slotOpen` → defaults test fails; (4) `colors.primaryAction` in
  `NextJamScreen.kt` → `amber-roles-allowlisted` fails; (5) catch `Exception` replaced by no catch →
  the repository test fails; (6) one copy change → a test fails; (7) with K1, a `4.dp` in
  `NextJamScreen.kt` → the new rule fails.
- Greps on `feature/next-jam/src/main` and `core/ui/.../state`: no `.dp`/`.sp` literal, no `Color(`,
  no `MaterialTheme.`, no `CircularProgressIndicator`/`LinearProgressIndicator`, no infinite
  transition, no tú forms; `primaryAction` only in `ListStateDefaults.kt`.
- Previews: none runs in the gate; they must compile (they do in `build`). Record that they were
  not rendered unless the implementer can render them.
- **Device (Pixel 5, outside the gate)**, with `uiautomator dump` + screenshot each step: (a) clear
  app data, airplane mode on, cold start → error block offline copy, `Reintentar` clickable ≥ 48dp;
  (b) airplane off, tap → skeleton or jam, then the jam or the no-upcoming block; (c) airplane on,
  force-stop, wait > 60 s, cold start → cached data with the notice; tap `Reintentar` → notice back
  with `Sin conexión`; (d) airplane off, tap → notice gone. Restore airplane mode off; crash buffer
  and AndroidRuntime log empty. The skeleton may be too brief to capture; record it if so. The
  empty-setlist state is JVM-only (it would need another temporary Sheet jam).

## Evidence To Capture

Gate output and counts; each demonstration's failure message; contrast values; uiautomator excerpts
and screenshots per device step; `BluesJam` log lines (never the script URL); copy as shipped.

## Validator Checklist

- [ ] Components in `:core:ui` `state/`, composables take only UiModels; allowlist still `{key}`.
- [ ] Every row of the Decision 1 table is tested; no notice on age alone; no spinner, no shimmer.
- [ ] Retry re-subscribes and calls `refresh()`; no refresh without the event; handler not stale.
- [ ] `SQLException` caught only in the jams flow, nothing broader; no crash on a failed read.
- [ ] No-results untouched; `Empty` drawn without a filter bar.
- [ ] Copy matches the approved table; no colour/dp literal, no `MaterialTheme`.
- [ ] Preview file covers the four states; demonstrations recorded; three `wired`; device steps.

## Risks

- The notice's age is computed per emission: a screen open for an hour keeps "hace 2 minutos".
- Without a failure, data older than 30 minutes refreshes only when the flow is re-collected
  (relaunch, tab switch); nothing tells the musician in between.
- The error block and offline notice are verified on the device only by hand; TalkBack wording and
  the live region are unverified (no Compose UI test harness, T1 declined).

## User Approvals

Answered by the user on 4 October 2026 (recommended option taken in all three):

- **C1:** copy table approved as written.
- **K1:** yes — add `no-dp-literal-outside-core-ui` in this slice, demonstrated failing first
  (Konsist 16/16).
- **S1:** yes — catch `SQLException` in the jams read flow in `:core:data` in this slice.

The questions as asked:

- **C1 — copy.** Approve the copy table above (Rioplatense, vos), or give replacements.
  *Recommended:* approve as written.
- **K1 — new Konsist rule `no-dp-literal-outside-core-ui`** (a verification-rule change, proposed
  in this entry's notes). Text match, same shape as `no-color-literal-outside-core-ui`: outside
  `:core:ui` and `:konsist-test`, no `<number>.dp`, `<number>.sp`, `<number>.em` or `Dp(<number>`.
  Known limits: `n.dp` on a variable, a literal in `:core:ui` components. Passes today (grep). Must
  be shown failing before it is trusted. *Recommended:* add it in this slice. Alternative: keep the
  per-spec greps only.
- **S1 — touch `:core:data`** to catch `SQLException` in the jams read flow (Decision 6), the one
  case the error state exists for besides a first launch offline. *Recommended:* yes, in this
  slice. Alternative: leave the crash path and record it as a risk for a later slice.
- No new dependency, colour token or D-xx change. Retry as a secondary, non-amber action in the
  offline notice and amber only on the error block's retry button follow DESIGN.md's "primary
  action" role; say so if you want otherwise.
