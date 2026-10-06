# Feature Implementation Spec: Refresh often during the jam, and pull to refresh

## Source Feature

- `id`: live-refresh-during-jam
- `area`: feature-next-jam
- `depends_on`: `list-states` (`accepted`)
- `status`: not_started at planning time
- `source`: `feature_list.json` entry; user request of 6 October 2026: "Actualizar cada 30'' si el
  timestamp actual está entre media hora antes de que arranque el show y 4hs después. Además,
  agregar un pull to refresh." (30'' = 30 seconds.)
- **Sequencing:** `admin-remove-song-from-setlist` (`in_progress`) and `admin-set-key` edit
  `NextJamPresenter`, `NextJamUiModel` and `NextJamScreen`. Start only when no other slice has
  uncommitted changes in `:feature:next-jam`; re-read those three files before starting.
- **Status of this spec:** ready to implement once the User Approvals (L1–L4) are answered. Each has
  a recommended option; the spec is written for the recommended options.

## Goal

While "now" is inside a jam's **live window** — from 30 min before its start to 4 h after it, Buenos
Aires time — Próxima jam calls `JamsRepository.refresh()` every 30 s **while the screen is visible**,
so musicians see the admin's changes during the night. Próxima jam and Anteriores can also be
refreshed by pulling the list down.

## Non-Goals

- No refresh while the app is in the background, the screen is off, another tab is selected, or a
  screen is open above the tabs (song detail, past jam detail, admin screens). No WorkManager, no
  foreground service, no push, no connectivity listener.
- No pull to refresh on the song detail, past jam detail, add-song picker or Info (L2).
- No change to `JamsRepository`, `DefaultJamsRepository`, `Freshness`, `MIN_RETRY_INTERVAL`,
  `STALE_AFTER`, the Apps Script code or the catalog. No new mutation: every call is the existing
  read `JamsRepository.refresh()` (D-13 untouched).
- No ticker that ages the staleness text; no toast or snackbar for refresh outcomes.
- No new Gradle dependency, no new colour/type/spacing token, no `init.sh` change, no new Konsist
  rule.

## Job Story

When I am at the jam and the admin changes the list (adds a song, sets a key, puts me in a slot),
I want my phone to show it within half a minute without doing anything, and to force it by
pulling down when I'm impatient, so I never play from an old list.

## Users And Permissions

Anyone; read only. For an admin (passphrase stored) each refresh is the admin read (`readJams`
POST, see Decision 7). The local admin flag authorizes nothing.

## Repository Research (inspected)

- `JamsRepository`/`DefaultJamsRepository`: `refresh()` is single-flight (concurrent calls share one
  `Deferred` in `DataScope`), never throws, returns `JamsRefreshOutcome` (`Updated`/`Failed`).
  `MIN_RETRY_INTERVAL` (60 s) and `STALE_AFTER` (30 min) apply only to the refresh that **collecting**
  `observeJams()` starts (`SyncStates.isRefreshDue`); an explicit `refresh()` is never throttled. So
  a 30 s loop does not conflict with the 60 s rule; this spec reuses 60 s as the failure back-off.
- Admin read: with a passphrase stored, `refresh()` POSTs `readJams`; an `AccessRefused` stores the
  refused passphrase in memory and falls back to the GET for the rest of the process, so a rotated
  passphrase costs **one** failed guess per process, however many ticks run. `rate_limited`/`busy`
  map to `Unavailable` (a failed refresh, cache kept, no guess counted).
- `Jam.startTime: LocalTime` is non-null; the mapper rejects a jam with a missing or invalid `hora`
  (`JamIssue.MissingStartTime`). **A cached jam always has a start time**; there is no "no start
  time" case to handle.
- `JamsSnapshot.upcoming` is the earliest non-historical jam; a jam turns historical at 00:00 of the
  next day (Buenos Aires). A 21:00 jam's window runs to 01:00, so after midnight that jam is
  `past.first()`, not `upcoming` (Decision 2, L4).
- `JamCalendar(clock, zone)`: `now()`, `today()`, `zone` (Buenos Aires).
- `NextJamPresenter`/`PastJamsPresenter`: Retry pattern (`subscription` counter +
  `scope.launch { jams.refresh() }`); `toUiModel` maps `freshness.isRefreshing` into the skeleton/
  error choice and the notice's `Actualizando…`. `NextJamScreen` calls
  `presenter.present(Params(...))`; tabs live in `app/.../navigation/TabsShell.kt`, one
  `composable` destination per tab, so each tab screen has its own `NavBackStackEntry` lifecycle
  (RESUMED only while selected, the activity resumed and no outer destination above the tabs).
- Material 3 resolves to **1.3.2** (Compose BOM 2025.09.00; checked in the Gradle cache).
  `androidx.compose.material3.pulltorefresh.PullToRefreshBox(isRefreshing, onRefresh, modifier,
  state, contentAlignment, indicator, content)` and `PullToRefreshDefaults.Indicator(state,
  isRefreshing, modifier, containerColor, color, threshold)` exist there, marked
  `@ExperimentalMaterial3Api` (opt-in needed; no version bump).
- `app/src/debug/.../DemoUpcomingJamRepository` wraps the real repository; its `refresh()` delegates.
  `DemoUpcomingJam.on(today)` dates the demo 10 days ahead (window never open).
- Quotas (`docs/apps-script-api.md` Quotas): 30 simultaneous executions per user binding; no daily
  cap listed for web app executions. Latency (`technical-discovery.md`): warm median 2.5 s, max
  4.5 s, a first call 5.2 s.
- **Not inspected / assumed:** that `androidx.lifecycle.compose.LocalLifecycleOwner` and
  `currentStateAsState()` are on `:feature:next-jam`'s compile classpath transitively through
  Compose UI 1.9 (assumption; verify in task 1).

## Decisions

**1. Where the loop lives: the presenter, gated by the screen's lifecycle.** Not the repository (it
does not know what is visible) and not WorkManager (15 min minimum period, a new dependency, and it
would run with the screen off). `NextJamScreen` reads
`LocalLifecycleOwner.current.lifecycle.currentStateAsState()` and passes
`isResumed = state.isAtLeast(Lifecycle.State.RESUMED)` in `NextJamPresenter.Params` (new field,
default `false`, so a presenter nobody marks visible never polls). Backgrounding, screen off,
another tab or an outer destination all drop the tab's entry below RESUMED, which cancels the loop.
A refresh already in flight finishes in `DataScope` (harmless, one call).

**2. Window.** Pure, in `:feature:next-jam`, `LiveRefresh.kt`:
`internal object LiveRefresh { OPENS_BEFORE = 30 min; CLOSES_AFTER = 4 h; INTERVAL = 30 s;
FAILURE_INTERVAL = CatalogRepository.MIN_RETRY_INTERVAL /* 60 s */ }` and
`fun liveWindow(jam: Jam, zone: ZoneId): OpenEndRange<Instant>` =
`[date.atTime(startTime).atZone(zone).toInstant() − 30 min, … + 4 h)` — start inclusive, end
exclusive. Candidate jams: `snapshot.upcoming` **and** `snapshot.past.firstOrNull()` (L4), so the
window survives midnight. Zone is `calendar.zone`, never the device zone.

**3. Loop.** A plain suspend function, testable without Compose:
`internal suspend fun runLiveRefresh(windows: List<OpenEndRange<Instant>>, now: () -> Instant,
lastFetchedAt: () -> Instant?, jitter: () -> Duration, refresh: suspend () -> Boolean)`
(`refresh` returns true on `Updated`). Behaviour, in order:
1. If `now` is in no window: if a window opens later, `delay(opensAt − now + jitter())` and loop;
   otherwise return. `jitter()` is uniform in `[0, INTERVAL)` so phones whose screen is open when
   the window opens do not all call in the same second.
2. If inside a window, first iteration only: if `lastFetchedAt` is younger than `INTERVAL`, wait the
   remainder; otherwise refresh at once (opening the tab mid-jam shows fresh data).
3. Refresh; then `delay(INTERVAL)` after success, `delay(FAILURE_INTERVAL)` after a failure
   (offline included). The period is measured from when the refresh **returns**, so slow answers
   never overlap. Re-check the window each iteration; when it closes, go to step 1.

The presenter runs it in `LaunchedEffect(params.isResumed, windowKey)` where `windowKey` is the list
of `(date, startTime)` of the candidate jams — **not** the snapshot (each refresh emits a new
snapshot; keying on it would restart the loop and refresh immediately, forever). Nothing runs when
`isResumed` is false or the snapshot is null. `jitter` comes from a `random: Random = Random.Default`
constructor parameter of `NextJamPresenter` (Koin keeps `NextJamPresenter(get(), get(), get(),
get())`; tests pass a seeded or fixed one). `delay` uses the coroutine's clock; `now` is
`calendar.now()`.

**4. Quiet periodic refreshes.** A periodic refresh must not flash `Actualizando…` in the staleness
notice every 30 s (and a polite live region announcing it every 30 s is noise), nor flip `Failed`
back to the skeleton. The presenter keeps `periodicInFlight` and `userRefreshing` (Retry or pull)
in `remember { mutableStateOf(false) }`; when `periodicInFlight && !userRefreshing` it maps
`snapshot.copy(freshness = freshness.copy(isRefreshing = false))`. `toUiModel` is unchanged. The
refresh that collecting the flow starts and every Retry/pull keep showing `Actualizando…` as today.

**5. Pull to refresh.** Próxima jam and Anteriores (L2), in every state (loading, error, empty,
list). One shared `:core:ui` component, package `com.bbbjam.core.ui.state`:
`PullRefreshUiModel(isRefreshing: Boolean, events: EventHandler<Event>)` with `Event.Refresh`, a
`PullRefreshUiModel.IDLE`, and `@Composable RefreshableContent(model, modifier, content)` wrapping
`PullToRefreshBox` with `PullToRefreshDefaults.Indicator(containerColor = colors.surfaceRaised,
color = colors.text)` read inside an internal `PullRefreshDefaults` (no amber, no Konsist allowlist
change). Each `NextJamUiModel` and `PastJamsUiModel` variant gains
`val pullRefresh: PullRefreshUiModel = PullRefreshUiModel.IDLE` (abstract on the sealed interface;
the default keeps existing model-equality tests valid, since keyless handlers compare equal).
The handler does what Retry does (`subscription++` and `refresh()`), plus sets `pulling` and
`userRefreshing` true until that `refresh()` returns. `isRefreshing` of the model is `pulling` only
(the periodic loop never spins the indicator). A pull while `pulling` is true is ignored, so one pull
is exactly one `refresh()` call; a pull during a periodic call joins it (single-flight). Retry also
sets `userRefreshing` (not `pulling`). Anteriores has no periodic loop.

**6. Accessibility (L3).** A pull gesture is not reachable with TalkBack. `RefreshableContent` adds
`semantics { customActions = listOf(CustomAccessibilityAction(label) { onRefresh(); true }) }` with
the label `Actualizar` from an internal `PullRefreshCopy` in `:core:ui`.

**7. Admin and the guard budget.** No change needed: each admin tick is one `readJams` POST that
passes the router's guard with the right passphrase (counts nothing); a refused passphrase is
remembered for the process, so the loop spends at most one failed guess per process; `rate_limited`
is a failed refresh, which triggers the 60 s back-off.

**8. Offline.** Ticks keep running at the 60 s failure interval; an offline call fails locally in
milliseconds (no server load). The staleness notice appears after the first failure (Decision 4
keeps it steady), and the next successful tick removes it. Recovery takes at most 60 s.

## Load And Battery Estimate (goes into the risks doc, L1)

- Concurrency: N phones × call duration / period = 40 × 3 s / 30 s ≈ **4 simultaneous executions**
  on average, against the limit of 30. Phases are spread by when each screen opened, plus the jitter
  at window opening; reaching 30 needs a synchronization the design avoids. With failures backing
  off to 60 s, overload sheds load instead of amplifying it.
- Volume: upper bound 40 × 120/h × 4.5 h = **21,600 calls per night**, only if every phone keeps
  Próxima jam visible the whole window; realistic is a fraction (screens go off). Google lists no
  daily cap for web app executions, but quotas "may change without notice" and are not measured
  under load (assumption 6 in the risks doc stays open). Lever: `LiveRefresh.INTERVAL`, one constant.
- Growth: the `jams` route reads one tab per published jam, so latency (and concurrency) grows over
  the year; recorded as a risk, not solved here.
- Battery: only with the screen on and the app in front; one HTTPS call per 30 s keeps the radio in
  its high-power tail part of the time. The display dominates; acceptable for one night.

## User Approvals

Answered by the user on 6 October 2026 (recommended in all four): **L1** accept the load at 30 s;
**L2** pull to refresh on Próxima jam and Anteriores; **L3** the `Actualizar` accessibility action;
**L4** keep refreshing until start + 4 h after midnight.

The questions as asked:

- **L1 — Load trade-off.** Accept ~4 concurrent executions on average and up to ~21,600 calls per
  jam night, with jitter, the 60 s failure back-off and visible-only polling. *Recommended: accept.*
  Alternative: 60 s interval (halves both).
- **L2 — Pull to refresh scope.** *Recommended: Próxima jam and Anteriores only* (the two tab lists).
  Alternative: also the song detail and past jam detail.
- **L3 — Accessibility action copy** `Actualizar` (TalkBack custom action, Decision 6).
  *Recommended: yes.* No other new copy.
- **L4 — Window after midnight.** The jam that turned historical at 00:00 keeps its window until
  start + 4 h (refreshing although Próxima jam now shows the next jam or none). *Recommended: yes*,
  the literal rule. Alternative: windows only for `upcoming` (polling stops at midnight).

## Expected File Changes

- `core/ui/.../state/PullRefresh.kt` (model, `RefreshableContent`, internal `PullRefreshDefaults`,
  `PullRefreshCopy`); test `PullRefreshDefaultsTest` (colours are `surfaceRaised`/`text`, not amber).
- `feature/next-jam/.../LiveRefresh.kt` (constants, `liveWindow`, `runLiveRefresh`);
  `NextJamPresenter.kt` (Params `isResumed`, `random`, loop, quiet masking, pull);
  `NextJamUiModel.kt` (`pullRefresh`); `NextJamScreen.kt` (lifecycle → Params, `RefreshableContent`
  around every state, `@OptIn(ExperimentalMaterial3Api::class)` only where needed).
- `feature/past-jams/.../PastJamsPresenter.kt`, `PastJamsUiModel.kt`, `PastJamsScreen.kt` (pull only).
- Tests: `LiveRefreshWindowTest`, `LiveRefreshLoopTest` (next-jam), `NextJamLiveRefreshTest`
  (Molecule), `PastJamsPullRefreshTest`; `FakeJamsRepository` in both modules counts `refresh()`
  calls and can hold a call open (a `CompletableDeferred`).
- Debug only, `:app`: flag `bluesjam.demoUpcomingJamLive` → `BuildConfig.DEMO_UPCOMING_JAM_LIVE`
  (`false` in release), `DebugFlags.demoUpcomingJamLive`; with it the demo jam **replaces** the
  upcoming jam whatever the cache holds, dated today with start = now (Buenos Aires) truncated to the
  minute + 10 min, computed once per process; `DemoUpcomingJamRepository.refresh()` logs
  `demo jams refresh: <Updated|Failed> at <instant>` (tag `BluesJam`) while the flag is on. Extend
  `DemoUpcomingJamRepositoryTest`.
- Docs: see Durable Documentation.

## Durable Documentation Impact

- `docs/risks-and-open-questions.md` — **update**: the load estimate above, the growth risk, and the
  refresh/add race (Risks below).
- `docs/apps-script-api.md` Quotas — **update**: one paragraph on jam-night polling.
- `DESIGN.md` Required States — **update**: pull to refresh (indicator colours, all states, the
  `Actualizar` action); periodic refreshes are quiet; "Retry is a read" now also covers pull.
- `.claude/skills/architecture/SKILL.md` — **update**: the live-refresh pattern (loop in the
  presenter, `isResumed` from the screen, keyed by window jams, never the snapshot) and the
  `PullRefresh` component as the sixth `core.ui.state` example.
- `NextJamPresenter`/`PastJamsPresenter` KDoc — the "never refreshes on its own except Retry"
  sentence now lists pull and the live window.
- `CONTEXT.md` — **update** only if "live window" is used in copy or docs as a term; add it if so.
- `AGENTS.md`, `ARCHITECTURE.md`, `CONSTRAINTS.md` — not needed (no new rule, command or module).

## Implementation Tasks

1. Confirm `LocalLifecycleOwner` (androidx.lifecycle.compose) and `currentStateAsState` resolve in
   `:feature:next-jam` with no build change (`./gradlew :feature:next-jam:dependencies
   --configuration debugCompileClasspath`, look for `lifecycle-runtime-compose`). **If not, stop
   and report**: adding the library is a new dependency needing approval.
2. `LiveRefresh.kt` + `LiveRefreshWindowTest` and `LiveRefreshLoopTest` (`runTest`, virtual time;
   `now` = base instant + `testScheduler.currentTime`).
3. `PullRefresh.kt` in `:core:ui` + defaults test.
4. `NextJamPresenter` changes + `NextJamLiveRefreshTest`; then `NextJamScreen`.
5. `PastJamsPresenter`/`Screen` pull + test.
6. Debug flag and demo log; `DemoUpcomingJamRepositoryTest`.
7. Docs; `CI=true ./init.sh`; device check.

## Verification Plan

JVM (all inside `./gradlew check`):
- Window: a 21:00 jam on 2026-10-31 → closed at 20:29:59, open at 20:30:00 (BA), open at 00:59:59
  on 11-01, closed at 01:00:00; same instants built from UTC (23:30Z, 03:59:59Z, 04:00Z) to prove
  the zone; a device default zone of UTC changes nothing.
- Loop: inside the window, refresh at t0 then every 30 s after success (3 ticks in 90 s virtual), 60 s
  after a failure; a call that takes 5 s pushes the next tick to 35 s; outside every window **zero**
  calls over 24 h virtual; a window opening later waits until opening + jitter, then calls; the loop
  stops calling when the window closes; first iteration with data 10 s old waits 20 s.
- Presenter (Molecule): `isResumed = false` → zero refreshes inside the window; switching it true →
  calls start, back to false → no further call; a refresh emitting a new snapshot does not restart
  the loop (counts exact); with a fixed clock outside the window, zero calls; quiet masking: during a
  periodic call with a cached failure, the notice detail is the age, not `Actualizando…`, and a
  `Failed` model stays `Failed`; past.first() window keeps polling after midnight (L4).
- Pull: one `Refresh` event → exactly one `refresh()` call and `pullRefresh.isRefreshing` true until
  it returns, then false; a second pull while pending → still one call; pull during a periodic call
  → the fake sees one shared call; Anteriores the same, and no periodic call ever.
- Existing model-equality tests pass unchanged.
- Gate: `CI=true ./init.sh` exit 0, `konsist: wired` (17/17), `detekt: wired`, `ktlint: wired`.

Device (Pixel 5, manual, debug build; never enable TalkBack or any accessibility service; never ask
the user for Sheet data or the passphrase): set `bluesjam.demoUpcomingJamLive=true` in
`local.properties`, `./gradlew :app:installDebug`, open Próxima jam, `adb logcat -s BluesJam`:
1. A `demo jams refresh` line about every 30 s (+ call latency) while the tab is visible.
2. Home, then screen off: lines stop; back to the app: one line within 30 s, then periodic.
3. Anteriores tab, or open a song detail: lines stop; back to Próxima jam: they resume.
4. Airplane mode: `Failed` lines every ~60 s, notice steady without `Actualizando…` flashes; off:
   recovered within 60 s, notice gone. Restore airplane mode to off.
5. Pull on Próxima jam and on Anteriores: indicator shows, exactly one line per pull.
6. Remove the flag, reinstall: no `demo jams refresh` line; the real jam (if more than 30 min away)
   triggers no periodic call. Restore every device setting; empty crash buffer.

## Evidence To Capture

Gate output (three `wired`, test counts), new test class names and counts, the logcat excerpts for
device steps 1–5 with timestamps, `:app:assembleRelease` with no `DEMO_UPCOMING_JAM_LIVE = true`.

## Risks

- **Refresh vs an in-flight add/remove/set-key**: a read answered just before a write commits can
  replace the cache after the write's row lands, hiding it for up to one tick (30 s). Already
  possible with Retry; polling makes it likelier. The next tick self-heals. The admin slices'
  optimistic state must tolerate a refresh landing mid-write (flag to `admin-set-key`/remove-song).
- Quotas unmeasured under real load (L1); latency growth with published jams.
- `PullToRefreshBox` is experimental in 1.3.2; a BOM bump may change its API.

## Validator Checklist

- [ ] Loop only in `NextJamPresenter`, gated by `Params.isResumed` from the lifecycle; no WorkManager,
      no repository change, no new dependency.
- [ ] Window in Buenos Aires from `calendar.zone`; `[start − 30 min, start + 4 h)`; candidates
      `upcoming` and `past.first()`.
- [ ] Effect keyed by window jams, never the snapshot; 30 s after success, 60 s after failure,
      measured from return; jitter only at window opening.
- [ ] Periodic refreshes quiet (no `Actualizando…` flashes, `Failed` stays `Failed`).
- [ ] Pull on both tab lists, one call per pull, indicator only for pulls, `Actualizar` custom action,
      no amber, colours through `PullRefreshDefaults`.
- [ ] Debug flag absent from release; demo log only with the flag.
- [ ] Docs updated as listed; gate green with three `wired`; device evidence for steps 1–6.
