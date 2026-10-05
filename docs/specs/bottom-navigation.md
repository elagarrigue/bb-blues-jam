# Feature Implementation Spec: Wire the three tabs into bottom navigation

## Source Feature

- `id`: bottom-navigation
- `area`: app
- `depends_on`: `next-jam-read-only-list`, `past-jams-list`, `info-screen` (all `accepted`)
- `status`: not_started at planning time
- `source`: `feature_list.json` (entry, verification, notes: status-bar overlap found by the
  `info-screen` validation; N1 Navigation Compose 2.9.8 already in `:app` with string routes;
  replace `TemporaryTabs` with per-tab routes using `saveState`/`restoreState`, keep the song route
  above the tabs, own transitions and the deeplink scheme; Konsist `navigation-only-in-app`)
- **Sequencing (shared `:app` files).** This slice rewrites `app/src/main/java/com/bbbjam/navigation/
  AppNavHost.kt` and `AppRoutes.kt`, edits `app/src/test/java/com/bbbjam/navigation/AppRoutesTest.kt`
  and `MainActivity.kt` (comment only), and **deletes** `app/src/main/java/com/bbbjam/TemporaryTabs.kt`.
  `past-jam-detail` will very likely touch the same three main files (a route, and an `onOpenJam`
  callback passed to `PastJamsScreen` from wherever the tabs are hosted): it conflicts directly.
  It also edits `core/ui/src/test/kotlin/com/bbbjam/core/ui/theme/ContrastTest.kt`, `DESIGN.md` and
  the `architecture` skill, which `unpublished-setlist-state` may also edit (text conflicts only).
  Recommended order: this slice before `past-jam-detail`; whichever lands second re-reads these
  files. A past-jam detail route belongs in the **outer** host, beside the song route (Approach 2).

## Goal

The musician moves between **Próxima jam**, **Anteriores** and **Info** with a real bottom bar, and
each tab keeps its own state when they come back to it: scroll position, and on Próxima jam the
expanded rows and the instrument filter. Content no longer scrolls under the status bar. The song
detail stays full screen above the tabs, and the tab-switch and detail transitions are designed.

## Non-Goals

- Deeplinks: no `deepLinks` on any destination, no manifest intent filter (Approach 6, D1).
- Predictive-back animation (`android:enableOnBackInvokedCallback`), scroll-to-top on reselecting the
  current tab, badges on tabs, a top app bar, landscape or tablet layouts (navigation rail).
- Any change inside `:feature:*` (screens, presenters, copy) or `:core:data`. Screens are called
  with their existing signatures.
- A past-jam detail route (`past-jam-detail`), admin entry points, the action registry.
- Moving the navigation pin: Navigation Compose stays **2.9.8** (2.10 raises Compose and
  activity-compose). No new dependency (icons come from the existing `material-icons-core`).

## Job Story

When I am at the jam switching between tonight's setlist and the info or past jams,
I want each tab to be where I left it,
so I do not have to scroll back and re-apply my instrument filter every time.

## Users And Permissions

Musician and admin see the same bar. Nothing writes; the admin flag is not read.

## Acceptance Scenarios

1. **Bar.** On launch the bar shows, left to right, `Próxima jam`, `Anteriores`, `Info`, each with
   icon and label; Próxima jam is selected. Selected content is `text` on an indicator
   `surfaceRaised`; unselected content is `textMuted`; the bar container is `surface`. No amber.
   Each item is at least 48dp tall and is a screen-reader tab with its selected state.
2. **Per-tab state.** Given Próxima jam scrolled down, a row expanded and the `Bajo` filter on, when
   the user goes to Anteriores, scrolls, goes to Info, scrolls, then back to Próxima jam, then the
   list is at the same scroll offset with the same row expanded and the same filter; Anteriores and
   Info, revisited, are at their own previous offsets.
3. **Reselect.** Tapping the selected tab does nothing (no new entry, no reset, no animation).
4. **Back.** From Anteriores or Info, system back goes to Próxima jam with its state. From Próxima
   jam, back leaves the app. Back never shows an empty host.
5. **Song detail above the tabs.** From an expanded row on Próxima jam, `Ver detalle del tema`
   opens the detail full screen with **no bar**; `Volver` or system back returns to Próxima jam with
   the bar and all state of scenario 2. Fast double taps on `Volver` still never pop the tabs.
6. **Rotation and process death.** After rotation, and after the process is killed in the background
   and the app is reopened from recents, the selected tab and each tab's state from scenario 2 are
   restored (same for the detail and the tabs under it).
7. **Status bar.** Scrolling any tab or the detail never draws content under the clock and status
   icons: the status-bar area is a solid `background` band, the scroll starts below it.
8. **Transitions** (M1). Tab switch: a short crossfade. Detail: slides in from the end over the
   tabs, which stay visible under it; on back it slides out to the end. No other motion.

## Repository Research

### Files Inspected

`AGENTS.md`, `PROGRESS.md`, `feature_list.json`, `.claude/skills/architecture/SKILL.md`,
`DESIGN.md` (tokens, Layout, Song detail, Filter chip state, Accessibility),
`bb-blues-jam-design-prompt.md` (Estructura, Reglas de diseño),
`docs/design/export/.../pr_xima_jam_vista_m_sico/code.html` (export nav bar: icons
`queue_music`/`history`/`local_bar`, active tab amber — **not adopted**, see Visual Design),
`app/build.gradle.kts`, `gradle/libs.versions.toml`, `app/src/main/AndroidManifest.xml`,
`MainActivity.kt`, `navigation/AppNavHost.kt`, `navigation/AppRoutes.kt`, `TemporaryTabs.kt`,
`AppRoutesTest.kt`, `core/ui/.../theme/BluesJamTheme.kt` (Material mapping: `surfaceTint` =
background, so tonal elevation adds no tint), `BluesJamColors.kt`, `core/ui/.../nav/BackButton.kt`,
`BackUiModel.kt`, `presenter/EventHandler.kt`, `ContrastTest.kt`, the three tab screens'
signatures (`InfoScreen`, `NextJamScreen`, `PastJamsScreen`: all take `contentPadding`, applied
inside their scroll; Info uses `rememberScrollState()`, the lists `LazyColumn` with the default
saveable `rememberLazyListState()`), and the contents of the `material-icons-core` 1.7.8 AAR in the
Gradle cache (the Filled set: no `QueueMusic`, `History` or `LocalBar`).
Not inspected: the specs of `song-detail-screen`, `info-screen`, `next-jam-read-only-list` in full
(only via the architecture skill and DESIGN.md, which record their decisions).

### Existing Patterns To Follow

- Navigation only in `:app`, string routes built and parsed only in `AppRoutes` (R1).
- Icons and Material components drawn in `:core:ui` components with explicit token colors
  (`BackButton`); features and `:app` never see `material-icons-core`.
- UiModel + `EventHandler` for a component; visual rules in a `…Defaults` object a JVM test reads.
- No dp/sp literals outside `:core:ui` (Konsist), no `MaterialTheme.colorScheme` outside `:core:ui`.

### Current Gaps

- `TemporaryTabs` is a state switch: switching disposes the other screen, losing its scroll, rows
  and filter. Its top inset is passed inside the scroll (the clock overlaps headings).
- No icons are reachable from `:app` (`material-icons-core` is `implementation` in `:core:ui`).
- No Compose UI test harness (T1 declined): per-tab restoration is proven on the device.

## Technical Approach

1. **Two hosts.** The outer `NavHost` (in `AppNavHost`) keeps two destinations: `AppRoutes.TABS`
   (`"tabs"`, now the tabs shell) and `AppRoutes.SONG_DETAIL` (unchanged pattern and parsing). The
   shell (`navigation/TabsShell.kt`, internal) is a `Column` on `background`: an inner `NavHost`
   with its own `rememberNavController()` (weight 1) and the `TabBar` below it. Rationale: the detail
   covers the bar without hiding or resizing it, and the inner controller with each tab's saved
   state is saved inside the outer `tabs` entry, so it survives the detail, rotation and process
   death. The inner NavHost's own back handler is composed later and wins while its back stack has
   more than one entry; while the detail shows, the shell is not composed.
2. **Tab routes** in `AppRoutes`: `NEXT_JAM = "next-jam"` (inner start), `PAST_JAMS = "past-jams"`,
   `INFO = "info"`. `navigation/AppTab.kt`: `internal enum class AppTab(route, label, icon:
   TabIcon)` in bar order with labels `Próxima jam`, `Anteriores`, `Info` (already approved copy,
   moved from `TemporaryTabs`), `AppTab.fromRoute(route: String?): AppTab?`, and the pure mapper
   `tabBarModel(selected: AppTab?, onSelect: (AppTab) -> Unit): TabBarUiModel` (one handler per tab
   that calls `onSelect(tab)`; the shell passes `onSelect` through `rememberUpdatedState`). The
   selected tab is read from `inner.currentBackStackEntryAsState()` via `destination.hierarchy`
   matched against `AppTab.route`.
3. **Selecting a tab** (only if it is not already selected):
   `inner.navigate(tab.route) { popUpTo(inner.graph.findStartDestination().id) { saveState = true };
   launchSingleTop = true; restoreState = true }`. This is the per-tab `saveState`/`restoreState`
   the note asks for and yields scenario 4's back behaviour.
4. **Tab destinations** call `NextJamScreen(onOpenSong = …)`, `PastJamsScreen()`, `InfoScreen()`
   with no `contentPadding` (default `PaddingValues()`); `onOpenSong` calls the **outer** controller:
   `outer.navigate(AppRoutes.songDetail(date, position)) { launchSingleTop = true }`.
5. **Insets.** The inner NavHost gets `Modifier.statusBarsPadding()` (top inset outside the scroll,
   over the shell's `background`): the solid band of scenario 7. The bar handles the navigation-bar
   inset itself (Material `NavigationBar` default `windowInsets`). The detail destination wraps
   `SongDetailScreen` in a `background` `Box` with `statusBarsPadding()` and passes
   `contentPadding = WindowInsets.navigationBars.asPaddingValues()` (bottom still inside the scroll).
   Keep the existing `RESUMED` guard on `onBack` and the null-args `popBackStack`.
6. **Deeplinks deferred** (D1). D-13 is satisfied by repository functions registered in the `:app`
   action registry (architecture skill, "A mutation → …"); a deeplink is the alternative, not a
   requirement. An exported URI entry point needs its own validation design and is best decided with
   `action-contract-registry`. This slice only keeps every route string in `AppRoutes` and records
   the deferral; `AppRoutes`' KDoc says so.
7. **Transitions** (M1), constants in `navigation/AppMotion.kt` (internal object, named `Int`
   millisecond constants, no magic numbers for detekt): inner NavHost `fadeIn(tween(TAB_FADE_MS))` /
   `fadeOut(tween(TAB_FADE_MS))` for enter/exit and pop, `TAB_FADE_MS = 150`. Outer: the detail
   `slideIntoContainer(Start, tween(DETAIL_SLIDE_MS))` on enter and `slideOutOfContainer(End, …)` on
   pop exit, `DETAIL_SLIDE_MS = 250`; the shell stays visible underneath on push and pop (no exit
   motion; e.g. `ExitTransition.KeepUntilTransitionsFinished` / `EnterTransition.None`). The
   implementer must confirm on the device that the popping detail is drawn **above** the shell.
8. **`:core:ui` component** in `com.bbbjam.core.ui.nav`: `TabBar(model, modifier)` built on Material
   3 `NavigationBar`/`NavigationBarItem`, colors only from `TabBarDefaults` (container `surface`,
   selected icon/label `text`, indicator `surfaceRaised`, unselected icon/label `textMuted`), label
   `Text` in `BluesJamTheme.typography.caption`, `alwaysShowLabel = true`, icon
   `contentDescription = null` (the label names the item; `NavigationBarItem` already gives
   `Role.Tab` and the selected state). `TabBarUiModel(tabs: List<TabUiModel>)`,
   `TabUiModel(label, icon: TabIcon, selected, events: EventHandler<Event>)` with
   `Event.Select : UiEvent` (navigation only, never a write). `enum class TabIcon { SETLIST, ARCHIVE,
   INFO }` mapped to `ImageVector`s **inside** `:core:ui` (I1: `Icons.AutoMirrored.Filled.List`,
   `Icons.Filled.DateRange`, `Icons.Outlined.Info`). No dp literal needed (Material defaults: 80dp
   bar, 24dp icon). Nothing here imports `androidx.navigation`.
9. **Delete `TemporaryTabs.kt`.** `MainActivity`'s comment is updated; its code is unchanged.

## Expected File Changes

| File | Change |
|---|---|
| `app/src/main/java/com/bbbjam/navigation/AppNavHost.kt` | Rewrite: outer host, shell + detail, insets, detail motion |
| `app/src/main/java/com/bbbjam/navigation/TabsShell.kt` | New: inner host, three tab routes, bar |
| `app/src/main/java/com/bbbjam/navigation/AppTab.kt` | New: `AppTab`, `fromRoute`, `tabBarModel` |
| `app/src/main/java/com/bbbjam/navigation/AppMotion.kt` | New: durations |
| `app/src/main/java/com/bbbjam/navigation/AppRoutes.kt` | Tab routes, `TABS` KDoc, deeplink note |
| `app/src/main/java/com/bbbjam/TemporaryTabs.kt` | Delete |
| `app/src/main/java/com/bbbjam/MainActivity.kt` | Comment only |
| `app/src/test/java/com/bbbjam/navigation/AppRoutesTest.kt` | Tab route constants |
| `app/src/test/java/com/bbbjam/navigation/AppTabTest.kt` | New |
| `core/ui/src/main/kotlin/com/bbbjam/core/ui/nav/TabBar.kt`, `TabBarUiModel.kt`, `TabBarDefaults.kt` | New |
| `core/ui/src/test/kotlin/com/bbbjam/core/ui/nav/TabBarDefaultsTest.kt` | New |
| `core/ui/src/test/kotlin/com/bbbjam/core/ui/theme/ContrastTest.kt` | + `text` on `surfaceRaised` |
| `DESIGN.md`, `.claude/skills/architecture/SKILL.md`, `docs/risks-and-open-questions.md` | See below |

No Gradle file changes. No new Konsist rule (the suite stays at 17).

## Visual Design Impact

- New DESIGN.md subsection "Bottom bar": order, labels, icons (I1), colors of Approach 8, no amber
  (the selected tab is a location, not an action; the export's amber active tab is rejected under
  "amber only for what the user can act on"), 48dp+ items, the status-bar band, and the motion (M1).
- Song detail bullet: "no transition (motion is `bottom-navigation`'s)" becomes the slide of M1.
- Filter chip "State": "lost on relaunch and on the temporary tab switch" becomes "lost on
  relaunch; kept across tab switches and the song detail".

## Durable Documentation Impact

- `.claude/skills/architecture/SKILL.md`: update — `:app` row (two hosts, `TabsShell`, `AppTab`,
  `TemporaryTabs` gone), `:core:ui` gains `TabBar` in `nav`, Still Open: deeplinks deferred to
  `action-contract-registry` (D1), navigation resolved.
- `DESIGN.md`: update (Visual Design Impact).
- `docs/risks-and-open-questions.md`: add the deeplink deferral as an open question.
- `AGENTS.md`, `CONTEXT.md`, `docs/domain-model.md`, `docs/apps-script-api.md`: not needed (no rule,
  term, entity or contract changes). `ARCHITECTURE.md`/`CONSTRAINTS.md` do not exist; the
  architecture skill plays that role.
- `PROGRESS.md`, `feature_list.json`: implementer, as usual.

## Implementation Plan

1. `:core:ui` component, defaults, tests (gate green before touching `:app`).
2. `AppRoutes` tab routes, `AppTab` + mapper + tests.
3. `TabsShell`, then `AppNavHost` rewrite; delete `TemporaryTabs.kt`.
4. Motion constants and transitions.
5. Gate, device checks, docs.

## Implementation Tasks

1. Add `TabBarUiModel.kt`, `TabBarDefaults.kt`, `TabBar.kt` (with a `@Preview`) per Approach 8.
2. `TabBarDefaultsTest`: each default equals its token (`surface`, `text`, `surfaceRaised`,
   `textMuted`) and none equals an amber role; `ContrastTest`: `text` on `surfaceRaised`.
3. `AppRoutes`: `NEXT_JAM`, `PAST_JAMS`, `INFO`; KDoc for `TABS` (the shell) and deeplinks deferred.
4. `AppTab.kt` and `AppTabTest`: order and labels; `fromRoute` for each route, `null`, an unknown
   route and the song pattern; `tabBarModel` marks exactly one selected (none for `null`), and
   invoking each tab's handler calls `onSelect` with that tab.
5. `TabsShell.kt` (Approaches 1–5); `AppNavHost.kt` rewrite; delete `TemporaryTabs.kt`; fix the
   `MainActivity` comment.
6. `AppMotion.kt` and the transitions (Approach 7).
7. `CI=true ./init.sh`; device checks; docs; evidence.

## Verification Plan

- **Gate:** `CI=true ./init.sh` exit 0, ending with `konsist: wired` (17/17, unchanged),
  `detekt: wired`, `ktlint: wired`. Expected new result files: `AppTabTest`, `TabBarDefaultsTest`;
  changed counts: `ContrastTest` +1, `AppRoutesTest` (same or +1). Every other file unchanged.
- **Static:** `git grep -n "TemporaryTabs" -- '*.kt'` empty; `git grep -n "androidx.navigation"`
  only under `app/`; `navigationCompose = "2.9.8"` unchanged; `./gradlew :app:dependencies
  --configuration debugRuntimeClasspath` shows no resolved version different from the baseline
  (compare before/after).
- **Device (Pixel 5, manual; not part of the gate).** Use the debug demo jam
  (`bluesjam.demoUpcomingJam=true` in `local.properties`, debug build, `./gradlew :app:installDebug`);
  never add or delete Sheet jams, never enable TalkBack or any accessibility service.
  A. Scenario 1: `adb shell uiautomator dump`: three items, labels as listed, `selected="true"` only
     on Próxima jam, each item bounds ≥ 48dp tall; screenshot shows no amber in the bar.
  B. Scenario 2 (scroll Próxima jam, expand a row, filter `Bajo`; Anteriores scroll; Info scroll;
     back to each), screenshots before and after each return.
  C. Scenarios 3 and 4 (reselect; back from Info → Próxima jam; back from Próxima jam → launcher).
  D. Scenario 5 including rapid double tap on `Volver`; watch the slide-out is drawn above the tabs.
  E. Scenario 6: rotate (auto-rotate restored afterwards); then HOME, `adb shell am kill com.bbbjam`,
     reopen from recents.
  F. Scenario 7: scroll each tab and the detail, screenshot the status-bar area.
  G. `adb logcat -b crash -d` empty; every setting restored; `local.properties` flag restored.

## Evidence To Capture

Gate summary lines and result-file counts; static grep outputs; dependency comparison; device steps
A–G with screenshot/dump notes. Never record the Apps Script URL.

## Validator Checklist

- [ ] `TemporaryTabs.kt` deleted; three inner tab routes with `saveState`/`restoreState`; song route
      in the outer host, no bar on the detail.
- [ ] No `:feature:*` file or `:core:data` file changed; no Gradle file changed; pin at 2.9.8.
- [ ] `androidx.navigation` only in `:app`; `:core:ui` `TabBar` has no navigation import; no dp/sp
      literal or `MaterialTheme.colorScheme` in `:app`.
- [ ] Bar colors from `TabBarDefaults`, no amber, test proves it; labels exactly as approved.
- [ ] Back behaviour of scenario 4; reselect is a no-op.
- [ ] Status bar band on tabs and detail (device evidence F).
- [ ] Transitions match the M1 answer; durations named.
- [ ] No deeplink, no manifest change; deferral recorded in architecture skill and risks doc.
- [ ] Gate green with three `wired`; device evidence A–G recorded, including process death.

## Risks

- Nested NavHosts: the inner controller's saved state lives in the outer entry; if restoration
  after process death fails, fall back to one host with the bar shown only for tab routes, and
  record it (ask before switching if it changes the scenarios).
- Pop z-order of the detail over the shell in Navigation Compose 2.9.8 (device check D).
- Whether Compose honours the system "remove animations" setting was not verified; out of scope.
- Re-entering Próxima jam re-subscribes its presenter (non-saveable `remember` state such as the
  retry counter resets); expected and harmless. A list whose UiModel branch changes while away
  (no-upcoming → jam) starts at the top; acceptable.

## User Approvals

Answered by the user on 5 October 2026 (recommended option in all three): **I1** icons from
`material-icons-core` as listed; **M1** 150 ms tab crossfade and 250 ms detail slide; **D1**
deeplinks deferred to `action-contract-registry`.

The questions as asked:

- **I1 — tab icons.** No new dependency; from `material-icons-core`, drawn in `:core:ui`:
  Próxima jam `List` (setlist), Anteriores `DateRange` (calendar), Info `Info` (outlined).
  *Recommended:* approve. Alternatives: (b) hand-copy the export's `queue_music`/`history`/
  `local_bar` Material Symbols paths as `ImageVector`s in `:core:ui` (Apache 2.0 notice added);
  (c) add `material-icons-extended` (rejected by the planner: very large, and release shrinking is
  off).
- **M1 — motion.** Tab switch: 150 ms crossfade. Song detail: slides in from the end in 250 ms over
  the tabs, slides out on back. *Recommended:* approve. Alternative: no motion anywhere (as today).
- **D1 — deeplinks.** Defer the deeplink scheme to `action-contract-registry` (or a slice of its
  own); D-13 is met by repository functions in the action registry. *Recommended:* defer.
  Alternative: define now a `bluesjam://` scheme for read-only destinations (tabs, song), which adds
  an exported intent filter and parsing of untrusted input in this slice.
