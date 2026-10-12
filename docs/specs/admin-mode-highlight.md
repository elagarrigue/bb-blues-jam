# Feature Implementation Spec: Make admin mode obvious with its own background and a MODO ADMIN banner

## Source Feature

- `id`: admin-mode-highlight
- `area`: core-ui+app
- `depends_on`: admin-row-icon-actions (accepted, HEAD 8bfd0b7)
- `status`: not_started at planning time
- `source`: `feature_list.json` (last entry), user request of 12 October 2026

## Goal

Today the only visible sign that a device is in admin mode is the presence of extra controls
(draft badges, `Agregar tema`, row action bars, etc.) scattered across each screen. The user asked
for something impossible to miss: "tiene que ser obvio que estamos en modo admin, cambiemos el
fondo por ejemplo para que resalte el estado" ("it has to be obvious we're in admin mode, let's
change the background, for instance, to make the state stand out"). The user's follow-up decision
fixes the shape of the solution: **a distinct admin background for the whole app, plus a fixed
"MODO ADMIN" strip at the top.** Amber stays reserved for its five roles (D-17); there is still no
light theme.

This is a `:core:ui` + `:app` slice: a new admin palette lives in the design-token layer, and the
banner plus the single read of the admin flag live at the app's composition root, so **no feature
screen adds an `if (isAdmin)` branch for background colour** — every screen already reads
`BluesJamTheme.colors.background/.surface/.surfaceRaised`, and this slice makes those three tokens
switch for the whole composition when admin mode is on.

## Non-Goals

- No change to which controls are drawn for the admin — that is each presenter's existing
  `AdminSession.observeIsAdmin()` read (D-15). This slice only changes *how admin mode looks*, not
  *what it can do*.
- No new amber role and no change to `AMBER_ROLE_ALLOWLIST`: the admin background/surfaces and the
  banner are explicitly **not** amber (D-17 — amber is reserved for the open slot, the key, the
  primary action, the published state and the active filter chip; "we are in admin mode" is not one
  of those five things).
- No change to the Android window background XML resource
  (`core/ui/src/main/res/values/colors.xml`, `bluesjam_window_background`) or `WindowBackgroundTest`.
  That resource paints the very first frame before any Kotlin runs and before the admin flag can be
  known (it is read asynchronously from a Koin-provided `AdminSession`); it must keep mirroring the
  musician `BluesJamPalette.Background` exactly as today. The admin background only applies once
  Compose has installed the theme.
- No change to system status/navigation bar *icon* style (`MainActivity`'s
  `SystemBarStyle.dark(Color.TRANSPARENT)`): icons stay light-on-dark in both modes. Whether the
  system bar *background scrim* should also shift to the admin tone is an open question, resolved
  below (Technical Approach, "System bars") rather than silently decided.
- No change to any existing colour role's *value* (`primaryAction`, `slotOpen`, `key`, `published`,
  `activeFilter`, `text`, `textMuted`, `border`, `slotFilled`, `archive`, `error` keep their current
  hex values). Only `background`, `surface` and `surfaceRaised` gain an admin variant.
- No new mutation, no repository change, no Apps Script change (D-13 not implicated): this is pure
  presentation, driven by the same `AdminSession.observeIsAdmin()` every admin-aware presenter
  already reads.
- No change to `AdminControlsDefaults`/`AddSongDefaults`/etc.'s own colours (the neutral admin
  *action* tokens used for `Subir`/`Bajar`/`Quitar`/etc.): those already read `colors.text`,
  `colors.textMuted`, `colors.error`, `colors.surface`, `colors.surfaceRaised` with no argument, so
  they automatically pick up the new admin surface/background values once the token layer switches
  (see Technical Approach) — they need no code change, only re-verification that their contrast
  still holds against the new admin surfaces (Verification Plan).

## Job Story

When I am logged in as admin and moving between Próxima jam, Anteriores, Info, the song detail and
the pickers, I want every screen's background to read visibly different from musician mode, and a
persistent "MODO ADMIN" strip at the top, so I never forget that the controls I am looking at write
to the real Sheet.

## Users And Permissions

- Musician (not logged in, and the debug build without `bluesjam.debugAdmin`): sees no change at
  all. `AdminSession.observeIsAdmin()` emits `false`; the app renders pixel-identical to before this
  slice.
- Admin (local flag on, D-15, authorizes nothing by itself — D-11): every screen's background,
  surface and raised-surface tokens switch to the admin palette, and a fixed banner reading "MODO
  ADMIN" is drawn above the tab bar/full-screen routes, below the status bar. Logging out reverts
  both immediately.

## Acceptance Scenarios

### Scenario 1: Admin background applies app-wide with no per-screen branch

Given the admin flag is true (`AdminSession.observeIsAdmin()` emits `true`)
When any screen is drawn — Próxima jam (musician and admin rows), Anteriores, Info, the song
detail, the past jam detail, the admin login, the catalog picker, the key picker, the assign-musician
screen
Then every surface that today reads `BluesJamTheme.colors.background`, `.surface` or
`.surfaceRaised` renders the admin variant of that token, with no `if (isAdmin)` branch added to any
feature screen or `…Defaults` object.

### Scenario 2: Musician mode is pixel-identical

Given the admin flag is false
When any of the same screens are drawn
Then every colour value read is byte-identical to the values before this slice (same hex, same
`BluesJamPalette` constants) — this is the regression check that the new seam has no effect when
off.

### Scenario 3: The banner is always visible, never covers content

Given the admin flag is true
When the user is on any tab, the song detail, a past jam's detail, or a full-screen picker (catalog
picker, key picker, assign-musician, admin login)
Then a fixed strip reading "MODO ADMIN" is drawn directly under the status bar and above the tab bar
or the screen's own content, pushing content down rather than overlapping it, and it does not move
or disappear on scroll.

### Scenario 4: The banner is announced once, not repeatedly

Given the admin flag turns true (login completes, or a debug build starts with
`bluesjam.debugAdmin=true`)
When a screen reader is active
Then the banner's text is read once as a static node when it appears/when the screen using it
first composes it — it is not a polite live region that re-announces on every recomposition, and it
is not re-read on every tab switch beyond the normal act of landing on a new screen.

### Scenario 5: Logout reverts both the background and the banner immediately

Given the admin flag is true and the user taps "Salir del modo admin" on Info
When the logout completes
Then the background/surface tokens and the banner both disappear within the same recomposition that
drops admin mode elsewhere (the same `observeIsAdmin()` emission every other admin-aware control
already reacts to) — no separate state to keep in sync.

### Scenario 6: Contrast holds on the new admin tokens

Given the admin background/surface/surfaceRaised values
When every existing text/role pairing in `ContrastTest` is recomputed against the admin tokens
Then every pairing still reaches at least WCAG AA text contrast (4.5:1), and the admin pairs are
added to `ContrastTest` with their own recorded expected ratios (not just re-using the musician
ratios, since the admin values differ from the current ones).

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md` (tail), `feature_list.json` (`admin-mode-highlight` and
  `admin-row-icon-actions` entries), `.claude/skills/architecture/SKILL.md`, `DESIGN.md` (colours
  front matter, "Colors" section, bottom-bar status-bar rule, song detail insets).
- `core/ui/src/main/kotlin/com/bbbjam/core/ui/theme/BluesJamPalette.kt` — the eleven raw hex
  constants (`internal object BluesJamPalette`); `BluesJamColors.kt` — the public role object
  (`object BluesJamColors`, plain `val`s, no `@Composable`); `BluesJamTheme.kt` — `BluesJamTheme { }`
  wraps `MaterialTheme` with every role mapped from `BluesJamColors`/`BluesJamPalette`, and the
  `BluesJamTheme` companion object exposing `.colors`/`.typography`/`.shapes`/`.spacing`; no
  `admin` parameter exists yet.
- `core/ui/src/test/kotlin/com/bbbjam/core/ui/theme/ContrastTest.kt` — 16 contrast assertions, each
  with a recorded expected ratio (tolerance 0.01), reused directly for Verification.
- `core/ui/src/test/kotlin/com/bbbjam/core/ui/theme/BluesJamColorsTest.kt` — **reflection-based**:
  asserts `roles.size == 17` and `palette.size == 11` by iterating every `Color`-typed property of
  `BluesJamColors`/`BluesJamPalette`/`BluesJamMaterial.colorScheme`. Adding admin tokens to these
  same objects would silently change these counts and is why the admin tokens must live in a
  **separate** object (Technical Approach), not inside `BluesJamColors`/`BluesJamPalette` — it
  keeps "amber is reachable only through its five roles" scoped to exactly what it tests today.
- `core/ui/src/test/kotlin/com/bbbjam/core/ui/theme/WindowBackgroundTest.kt` and
  `core/ui/src/main/res/values/colors.xml` — the one XML colour, read before Compose runs; confirmed
  out of scope above.
- **Read-site survey (`grep -rn` across every `.kt` file in the repo)**: every production read of
  `BluesJamColors.background`/`.surface`/`.surfaceRaised` (or `BluesJamTheme.colors.*` for the same
  three) happens either (a) directly inside a `@Composable` function, or (b) inside one of ~20
  `…Defaults` objects' functions (`AdminControlsDefaults`, `AddSongDefaults`, `SetKeyDefaults`,
  `DraftSetlistDefaults`, `PastJamsDefaults`, `PastJamDetailDefaults`, `AdminLoginDefaults`,
  `TabBarDefaults`, `InstrumentFilterDefaults`, `ListStateDefaults`, `PullRefreshDefaults`, etc.),
  each declared `fun …(colors: BluesJamColors = BluesJamColors): …` and in every production call
  site invoked with **no argument** — i.e. always resolving to the plain singleton at call time,
  which is itself always inside a composable (confirmed: zero reads from a non-composable context,
  a companion-object initializer, or test-collection-time code in `src/main`). **This is the load-bearing
  fact that makes the chosen approach safe**: if even one of these functions were called from
  outside a composition, a `CompositionLocal`-backed token could not be read there.
- `app/src/main/java/com/bbbjam/MainActivity.kt` — the single `BluesJamTheme { AppNavHost() }` call,
  the one place `admin: Boolean` can be threaded in with no navigation change.
- `app/src/main/java/com/bbbjam/navigation/AppNavHost.kt` — six `Box(...).background(BluesJamTheme.colors.background).statusBarsPadding()` call
  sites (song detail, past jam detail, admin login, add-song, set-key, assign-slot), each already
  reading the token dynamically — no change needed there beyond the token itself switching.
- `app/src/main/java/com/bbbjam/navigation/TabsShell.kt` — the tabs shell's own
  `Column(...).background(BluesJamTheme.colors.background)`, and where `TabBar` is drawn; this is
  the natural anchor point for the banner on the tabs (it already composes `NavHost` + `TabBar` in a
  `Column` with the status-bar inset applied to the `NavHost`, not the `Column`).
- `core/data/src/main/kotlin/com/bbbjam/core/data/admin/AdminSession.kt` — the contract
  (`observeIsAdmin(): Flow<Boolean>`), in `:core:data`. `:core:ui` cannot depend on `:core:data`
  (D-03: `:core:ui` → `:core:model` only), so `BluesJamTheme` cannot take an `AdminSession`
  directly — it must take a plain `Boolean`, read once in `:app` and passed down, exactly as the
  architecture skill's own suggestion in the orchestrator's brief says
  ("`BluesJamTheme(admin = true)` swapping tokens at the root in `:app`").
- `feature/info/src/main/kotlin/com/bbbjam/feature/info/InfoPresenter.kt:35` — the existing read
  pattern: `val isAdmin by remember { adminSession.observeIsAdmin() }.collectAsState(initial = false)`.
  The same pattern, at the `:app` composition root, is how `MainActivity`/`AppNavHost` will read the
  flag.
- `app/build.gradle.kts` — `:app` currently depends on `koin-android` only, not `koin-compose`;
  `gradle/libs.versions.toml` already declares `koin-compose` (BOM-managed) as a catalog alias, so
  adding `implementation(libs.koin.compose)` to `:app` is a one-line, already-catalogued addition
  (not a new pin), needed so `MainActivity`/`AppNavHost` can `koinInject<AdminSession>()`.
- `app/src/debug/java/com/bbbjam/debug/DebugAdminSession.kt` and `DebugOverrides.kt` — the
  `bluesjam.debugAdmin=true` flag rebinds the Koin `AdminSession` singleton itself
  (`debugOverrides`), so reading `AdminSession.observeIsAdmin()` at the composition root
  automatically sees the forced debug state with no extra wiring — confirmed no separate "is this a
  debug build" check is needed in `:app`'s theme wiring.
- `docs/specs/admin-row-icon-actions.md`, `docs/specs/design-tokens-theme.md` — format and depth
  precedent for this spec; the former also documents the current `IconRowAction`/`BackButton`
  pattern for `:core:ui` components that read `BluesJamTheme.colors.background` for their own
  surface.
- `bb-blues-jam-bitacora.md` §6.20 — the most recent dated decision note, used as the format
  precedent for the new note this slice must add.

### Existing Patterns To Follow

- **Tokens, never literals, and never per-screen conditionals** (`BluesJamTheme.*`, D-17). This
  slice must keep that rule intact for *every other* screen: the only new conditional in the whole
  codebase is the one read of `AdminSession.observeIsAdmin()` at the `:app` composition root.
- **`…Defaults` objects already take an optional `colors: BluesJamColors` parameter purely for JVM
  testability**, always defaulted at runtime. This slice does not need to touch any of those ~20
  objects: once `BluesJamColors.background/.surface/.surfaceRaised` themselves become
  context-sensitive (see Technical Approach), every caller that uses the default parameter
  automatically sees the right value, because that default is always resolved inside a composable.
- **Material 3 is mapped from tokens, never left on defaults** (`BluesJamMaterial.colorScheme`
  inside `BluesJamTheme.kt`). Since `background`/`surface`/`onSurface`/`surfaceTint`/`surfaceDim`/
  `surfaceContainerLowest` are all mapped from `BluesJamPalette.Background` and the raised/surface
  variants, these Material-level mappings must also follow the admin switch, or a stray Material
  default (e.g. an un-styled `Surface`) would leak the musician colour in admin mode.
- **A new colour value needs a recorded, dated decision** (`BluesJamPalette`'s own KDoc records
  which tokens are "derived" vs "reconciled against Stitch"). The admin tokens are new and have no
  export to reconcile against; record them as the user's own direction, computed to satisfy
  `ContrastTest`, exactly as this spec's Technical Approach does below.
- **One-line KDoc precedent for "why a value is what it is"** — follow `BluesJamPalette`'s existing
  style when adding the new object.

### Current Gaps

- No existing `CompositionLocal` carries a Boolean/mode flag in `:core:ui` today (`BluesJamTheme`
  only installs `LocalContentColor` and `LocalTextSelectionColors`). This slice adds the first one.
- No existing test asserts "a `BluesJamColors` property differs by context" — `BluesJamColorsTest`'s
  reflection currently assumes every `Color`-typed property is a fixed value; see Technical Approach
  for how this is kept true for the *existing* object while admin tokens live elsewhere.
- No banner-shaped component exists in `:core:ui` yet (the closest precedent is `StalenessNotice`,
  which is a *dismissible, polite-live-region* notice over cached data, not a fixed always-on
  strip — a different shape, so this slice adds a new component rather than reusing that one).

## Technical Approach

### T1 — Admin tokens live in a new, separate object, not inside `BluesJamColors`/`BluesJamPalette`

Add `internal object BluesJamAdminPalette` (beside `BluesJamPalette.kt`) holding three new raw hex
constants, and `object BluesJamAdminColors` (beside `BluesJamColors.kt`, mirroring its KDoc style)
exposing exactly the three tokens that switch:

```kotlin
internal object BluesJamAdminPalette {
    val Background = Color(0xFF1A120B)
    val Surface = Color(0xFF231A14)
    val SurfaceRaised = Color(0xFF271E18)
}
```

Keeping these in their own objects (not added as new properties of `BluesJamColors`/
`BluesJamPalette`) means `BluesJamColorsTest`'s reflection counts (`roles.size == 17`,
`palette.size == 11`) and its "amber is reachable only through its five roles" assertion are
**unaffected** — they keep testing exactly what they test today, and the amber-role invariant does
not need to reason about a second set of properties.

**Computed values (WCAG 2.x, same formula `ContrastTest` uses: relative luminance, `(L1+0.05)/(L2+0.05)`).**
`BluesJamAdminPalette.Background` (`#1A120B`) was chosen as a warm, dark umber near-black — a
visibly different hue from the existing cool indigo `#111318`, at almost the same luminance
(0.00676 vs 0.00651) so every existing text/role contrast pairing is preserved almost exactly.
`Surface`/`SurfaceRaised` are derived by applying the *same* per-channel delta the musician palette
already uses from `Background` to `Surface` (+9,+8,+9) and to `SurfaceRaised` (+13,+12,+13), so the
three-step elevation ramp keeps the same relative "lift" in the warm hue. Recomputed existing pairs
on the admin tokens (all still ≥ AA 4.5, all within a few tenths of their musician value):

| Pairing | Musician (today) | Admin (this slice) |
|---|---|---|
| `key`/`slotOpen`/`published` (amber) on `background` | 10.35 | 10.31 |
| `slotOpen` on `surface` | 9.57 | 9.52 |
| `textMuted` on `surface` | 10.10 | 10.05 |
| `textMuted` on `background` | 10.93 | 10.88 |
| `text` on `background` | 14.41 | 14.35 |
| `archive` on `surface` | 5.39 | 5.36 |
| `error` on `surface` | 10.12 | 10.06 |
| open-slot chip fill (15% amber over `surface`) | 7.03 | 6.91 |
| `text` on `surfaceRaised` (new, for the banner) | — | 12.68 |
| `textMuted` on `surfaceRaised` | — | 9.61 |

All pairings that exist today stay ≥ AA on the admin tokens; none crosses below 4.5:1. The two new
rows (`text`/`textMuted` on `surfaceRaised`) back the banner's own content colour (T3).
`slotFilled` (filled-slot chips/panel lines), `border`, `error`, `archive` and every amber role keep
their current values and are **not** part of this computed table beyond the "still passes" check —
they are never swapped, only the surfaces behind them change.

### T2 — The three background/surface tokens become context-sensitive inside `BluesJamColors`, driven by a `CompositionLocal` installed by `BluesJamTheme(admin = …)`

Add `internal val LocalAdminMode = staticCompositionLocalOf { false }` in `BluesJamTheme.kt` (or a
new small `AdminMode.kt` in the `theme` package). Change `BluesJamTheme`'s signature to
`BluesJamTheme(admin: Boolean = false, content: @Composable () -> Unit)`, and have it provide
`LocalAdminMode provides admin` alongside its existing `CompositionLocalProvider` call, wrapping
`content` (the admin flag must be in scope for `BluesJamMaterial.colorScheme`-equivalent reads too —
see below).

Change exactly these three properties of `object BluesJamColors` from stored `val` to
`@Composable get()`:

```kotlin
val background: Color @Composable get() = if (LocalAdminMode.current) BluesJamAdminColors.background else BluesJamPalette.Background
val surface: Color @Composable get() = if (LocalAdminMode.current) BluesJamAdminColors.surface else BluesJamPalette.Surface
val surfaceRaised: Color @Composable get() = if (LocalAdminMode.current) BluesJamAdminColors.surfaceRaised else BluesJamPalette.SurfaceRaised
```

Every other property of `BluesJamColors` (`text`, `textMuted`, `border`, `slotFilled`, `archive`,
`error`, and all five amber-role pairs) is **unchanged**: a plain `val`, no admin variant, per the
Non-Goals.

This is the one change that makes Scenario 1 true with zero feature-level edits: every
`BluesJamTheme.colors.background`/`.surface`/`.surfaceRaised` read and every `…Defaults` function's
defaulted `colors: BluesJamColors = BluesJamColors` parameter already executes inside a composable
(confirmed by the read-site survey above), so they all pick up the admin value automatically. No
`…Defaults` file needs to change.

**Material 3 mapping (`BluesJamMaterial.colorScheme` inside `BluesJamTheme.kt`).** Because
`darkColorScheme(...)` is built once as a `val` (not `@Composable`), it cannot read
`BluesJamColors.background` directly without becoming a function. Change
`internal object BluesJamMaterial` to expose `colorScheme` as `@Composable get()` too (same shape:
`if (LocalAdminMode.current) …`), rebuilding the same `darkColorScheme(...)` call but substituting
`BluesJamAdminPalette.Background`/`Surface`/`SurfaceRaised`-derived parameters in place of
`BluesJamPalette.Background`/`Surface`/`SurfaceRaised` wherever the musician version uses them (nine
`ColorScheme` parameters currently read `Background`/`SurfaceRaised`/`SlotFilled` — `SlotFilled` is
not swapped per the Non-Goals, so re-check which of those nine actually need the admin substitution
versus which were only using `Background`/`SurfaceRaised` incidentally). `typography`, `shapes` and
`textSelectionColors` are untouched (no admin variant).

**Why not a non-`CompositionLocal` approach** (considered and rejected): passing `admin: Boolean`
down through every presenter's `Params` and every `…Defaults` function call would touch ~20 files
and reintroduce exactly the per-screen branching D-17/this spec are meant to avoid. Passing a second
`BluesJamColors`-shaped value explicitly at the ~116 existing `BluesJamTheme.colors.*` call sites is
the same problem at a larger scale. The `CompositionLocal` seam is the only option that satisfies
"no screen branches on `isAdmin`" literally, and it mirrors how Compose's own `MaterialTheme`
already propagates `colorScheme`.

**`BluesJamColorsTest` impact.** Its reflection iterates `Color`-typed *properties* (methods whose
name matches the JVM getter shape for a `Color` value class). A `@Composable get()` property
compiles to a method that additionally takes a `Composer` parameter — the test's
`it.parameterCount == 0` filter will **exclude** these three properties once they become
`@Composable`, changing `roles.size` from 17 to 14. The implementer must update that one assertion
(`17` → `14`) and the "amber is reachable only through its five roles" set (unaffected — amber roles
are untouched). This is an expected, reviewed consequence of T2, not a silent breakage: call it out
in the PR/evidence, do not just make the number pass.

### T3 — The "MODO ADMIN" banner

Add `com.bbbjam.core.ui.admin` package in `:core:ui` with:

- `AdminBannerUiModel` (if any state is needed — most likely **stateless**, since the banner only
  needs the Boolean already threaded through `LocalAdminMode`/the composition root; prefer a plain
  `@Composable fun AdminBanner(modifier: Modifier = Modifier)` with no UiModel, since it has no
  events and no variable content — "MODO ADMIN" is a constant string, not admin-session-derived
  copy).
- Internal `AdminBannerCopy` holding the one string `"MODO ADMIN"` (D-12: already vos-neutral,
  no verb — no grammatical mood to get wrong) and `AdminBannerDefaults` with `fill = colors.surfaceRaised`
  (reused, no new token: T1's `surfaceRaised` already looks visually distinct from the body
  background underneath it in admin mode) and `content = colors.text` — both read with **no admin
  check inside the component**: when the banner is drawn at all, `colors.*` already resolves to the
  admin variant because it is only ever drawn while `LocalAdminMode.current` is true (see placement,
  below), so the component itself needs no conditional.
- A fixed height from `LocalMinimumInteractiveComponentSize.current` (48dp via the existing
  no-literal convention, `no-dp-literal-outside-core-ui`), full width, centred `caption`-or-`body`
  text (pick `body` for legibility at a glance; `caption` is also acceptable — not a load-bearing
  choice, implementer decides and records it), with a single `Modifier.semantics { heading() }`-free
  plain text node (not a live region — Scenario 4: announced once per screen as a normal static
  node, not a `LiveRegionMode.Polite` region that would re-announce on every recomposition the way
  `StalenessNotice`/`InstrumentFilterBar`'s count line do).

**Placement.** The banner is drawn **once at the outer composition root**, not duplicated per
screen, directly under the status-bar inset and above everything else:

- In `MainActivity`/at the top of `AppNavHost`'s composable tree: wrap the existing `NavHost` in a
  `Column` (or a `Box` with the banner as a sibling placed first) that, when `LocalAdminMode.current`
  (or the plain `admin: Boolean` already read at this level) is true, draws `AdminBanner()` first,
  then the status-bar inset, then the rest. Concretely: the banner sits **inside** the status-bar
  padding boundary from the content's perspective — i.e. below the status bar, above the tab bar
  and above every full-screen route's own content — because every full-screen route
  (`AppNavHost`'s six `Box(...).statusBarsPadding()` sites) and `TabsShell`'s `Column` currently
  apply `statusBarsPadding()` independently. The cleanest seam: apply the banner and the
  status-bar inset **once**, at the single point both the tabs shell and every full-screen route sit
  under — i.e. restructure `AppNavHost`'s top-level `Box`/`Column` so the status bar inset and (when
  admin) the banner wrap the whole `NavHost`, and remove the per-route `.statusBarsPadding()` calls
  that would otherwise double the inset. **This restructuring touches `AppNavHost.kt` and
  `TabsShell.kt` and must be done carefully so the status-bar band (a solid `background` per
  `DESIGN.md`'s bottom-navigation decision) is not applied twice and no screen's content shifts
  when admin mode is off.** The implementer should screenshot-diff a musician-mode run before and
  after this restructuring to confirm Scenario 2 (pixel-identical when off).
- The banner does **not** sit inside each tab's scrolling content (it must not scroll away) and does
  **not** sit inside the song detail's own `Box` (it must appear consistently across tabs *and* the
  detail *and* the pickers, per Scenario 3) — this is why it belongs at the single shared point above
  all of `AppNavHost`'s destinations, not inside `TabsShell` alone (which would miss the song detail,
  the pickers and the admin login, all of which are separate `AppNavHost` destinations, not children
  of `TabsShell`).

### T4 — Reading the admin flag once, at the root

`MainActivity` (or a small composable `AppRoot` it calls, implementer's choice) adds
`val adminSession = koinInject<AdminSession>()` (needs `implementation(libs.koin.compose)` added to
`app/build.gradle.kts`, already catalogued) and
`val isAdmin by remember { adminSession.observeIsAdmin() }.collectAsState(initial = false)`
(mirroring `InfoPresenter`'s existing pattern), then calls `BluesJamTheme(admin = isAdmin) { AppNavHost(admin = isAdmin) }`
(or threads `isAdmin` however the implementer structures T3's placement — the only hard requirement
is **one** read of `observeIsAdmin()` at the composition root, not one per screen). `:app` already
depends on `:core:data` transitively through its feature modules, so `AdminSession` is on its
classpath; this is the first time `:app` injects a `:core:data` contract directly rather than only
wiring Koin modules, which is consistent with "D-16: constructor injection... `koinInject()` as a
default parameter" already used by every feature screen — `:app` doing the same at its own root is
not a new pattern, just a new call site.

`initial = false` matches every other admin-aware presenter's convention (admin controls/the admin
background never flash on before the flag is actually known).

### System bars

**Decision needed from research, stated plainly rather than assumed:** `MainActivity` currently
calls `enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT))`
once, before `setContent`, before the admin flag is known (it is read asynchronously from Koin/
DataStore). `SystemBarStyle.dark(...)` only controls icon *style* (light icons), with a transparent
scrim — the actual colour showing through is whatever Compose draws under it, which is already
`BluesJamTheme.colors.background` at the top of the content (per `DESIGN.md`'s "status bar: solid
`background` band" rule). **Because the scrim is transparent and the admin banner/background is a
Compose-drawn colour under it, the status bar already visually follows admin mode with no change to
`MainActivity`'s `enableEdgeToEdge` call** — confirm this by device screenshot (Verification Plan)
rather than assuming it; if the status bar area shows through as the old musician colour instead of
the new admin one, the fix is to make sure whatever is drawn immediately under the status bar inset
in admin mode is the admin `background`/`surfaceRaised`, not a literal. The navigation bar at the
bottom follows the same rule, under the tab bar's own `surface` background. No `SystemBarStyle`
value itself needs to change for either bar.

## Expected File Changes

- `core/ui/src/main/kotlin/com/bbbjam/core/ui/theme/BluesJamAdminPalette.kt` — create: the three
  admin hex constants (T1).
- `core/ui/src/main/kotlin/com/bbbjam/core/ui/theme/BluesJamAdminColors.kt` — create: the public
  three-token admin role object (T1), or fold into the same file as the palette if the implementer
  prefers one file (follow the existing `BluesJamPalette.kt`/`BluesJamColors.kt` split for
  consistency).
- `core/ui/src/main/kotlin/com/bbbjam/core/ui/theme/BluesJamTheme.kt` — modify: add `LocalAdminMode`,
  the `admin: Boolean` parameter, `@Composable get()` on `BluesJamColors.background/.surface/.surfaceRaised`,
  and the `BluesJamMaterial.colorScheme` admin substitution (T2).
- `core/ui/src/main/kotlin/com/bbbjam/core/ui/admin/AdminBanner.kt` (new package) — create: the
  banner component, copy and defaults (T3), with a `@Preview` showing it over the admin background.
- `core/ui/src/test/kotlin/com/bbbjam/core/ui/theme/BluesJamColorsTest.kt` — modify: `roles.size`
  assertion `17` → `14` (T2's documented, reviewed consequence), with a comment explaining why.
- `core/ui/src/test/kotlin/com/bbbjam/core/ui/theme/ContrastTest.kt` — modify: add the admin pairs
  (Scenario 6) with their own recorded expected ratios from the T1 table.
- `core/ui/src/test/kotlin/com/bbbjam/core/ui/admin/` — create: a JVM test for the banner's pure
  parts if any exist (colour role test on `AdminBannerDefaults`, following the `…DefaultsTest`
  convention); if the component is purely compositional with no branchable logic, state that
  explicitly rather than writing a vacuous test (same rule `admin-row-icon-actions` followed for
  `IconRowAction`).
- `app/build.gradle.kts` — modify: add `implementation(libs.koin.compose)` (T4, already catalogued).
- `app/src/main/java/com/bbbjam/MainActivity.kt` — modify: read the admin flag once, pass `admin`
  into `BluesJamTheme` (T4).
- `app/src/main/java/com/bbbjam/navigation/AppNavHost.kt` — modify: restructure the single shared
  status-bar/banner wrapping point (T3); remove per-route duplicate `.statusBarsPadding()` only if
  the restructuring makes them redundant — verify this carefully, screen by screen.
- `app/src/main/java/com/bbbjam/navigation/TabsShell.kt` — modify only if the chosen placement needs
  it to stop applying its own status-bar inset once `AppNavHost` applies the shared one; otherwise
  leave as is.
- `app/src/test/java/com/bbbjam/` — add a small test (JVM, no Robolectric, per the "no Compose UI
  test harness" standing decision) if any pure logic is introduced at this layer; most of this
  slice's logic is compositional and already covered by `:core:ui`'s own tests.
- `DESIGN.md` — modify: add the admin background/surface hex values and the banner to the `colors`
  front matter and a new "Admin mode" prose section (Visual Design Impact).
- `bb-blues-jam-bitacora.md` — modify: one dated note (§6.21) recording the admin palette and banner
  decision, following §6.20's format.
- `.claude/skills/architecture/SKILL.md` — modify: an "As built by `admin-mode-highlight`" paragraph
  (the `LocalAdminMode` seam, where the admin tokens live, where the one flag read happens) so a
  later reader does not rediscover this from the diff alone — this is exactly the kind of seam the
  skill's "Where Each Piece Goes" section is for.
- `feature_list.json`, `PROGRESS.md` — evidence and status updates (implementer's job, not this
  spec's).

## Visual Design Impact

- UI involved: yes — every screen's background/surface, plus a new banner.
- Design source: `DESIGN.md` ("Colors" section, D-17 rule; the bottom-navigation status-bar rule for
  banner placement), this spec's T1 computed values, the existing `BackButton`/`StalenessNotice`
  component shapes as placement/sizing precedent.
- Screens or states affected: every screen, in admin mode only — Próxima jam (both rows views),
  Anteriores, Info, song detail, past jam detail, admin login, catalog picker, key picker,
  assign-musician. Musician mode: none (Scenario 2).
- New design artifact required: no. The computed hex values and contrast table in T1 are the design
  artifact; device screenshots of each tab and the song detail in both modes are the evidence
  (Verification Plan), matching `admin-row-icon-actions`' precedent.

## Durable Documentation Impact

- `ARCHITECTURE.md` / `CONSTRAINTS.md`: not present in this repo; the architecture skill plays that
  role (see Expected File Changes — update it).
- `AGENTS.md`: not needed — no workflow, startup path or repo-wide rule change.
- `DESIGN.md`: update — new colour values and the admin-mode prose section (see above). This is a
  durable design-token document, not optional.
- `bb-blues-jam-bitacora.md`: update — one dated note under a new §6.21, recording the user's 12
  October 2026 request and the decision (admin tokens in a separate object, `CompositionLocal` seam,
  banner placement), following §6.20's style.
- Other docs: none. `bb-blues-jam-design-prompt.md` does not currently describe admin-mode
  background at all; leave it unless the implementer finds a sentence it must now contradict.

## Implementation Plan

1. Baseline gate (`CI=true ./init.sh`), record current JVM test/class counts.
2. Add `BluesJamAdminPalette`/`BluesJamAdminColors` (T1) with the computed hex values and KDoc
   explaining the derivation (same-delta ramp from the admin background).
3. Add `LocalAdminMode`, the `admin: Boolean` parameter on `BluesJamTheme`, and the `@Composable get()`
   conversion of `background`/`surface`/`surfaceRaised` plus the `BluesJamMaterial.colorScheme`
   admin substitution (T2). Fix `BluesJamColorsTest`'s `roles.size` assertion and comment why.
4. Add the admin pairs to `ContrastTest` with the T1 table's expected ratios; run `:core:ui`'s test
   suite and confirm every existing assertion still passes unmodified except the one documented
   `roles.size` change.
5. Add the `AdminBanner` component, copy and defaults in a new `com.bbbjam.core.ui.admin` package
   (T3), with a `@Preview`.
6. Wire `:app`: add `koin-compose`, read the flag once in `MainActivity` (T4), restructure
   `AppNavHost`/`TabsShell` so the banner and the single status-bar inset wrap every destination
   (T3 placement) without doubling the inset or shifting musician-mode layout.
7. Confirm the system-bar scrim question (System bars) by device screenshot; adjust only if the
   musician colour shows through in admin mode.
8. Full gate; fix any new Konsist/detekt/ktlint finding (expect none — no amber role added, no `.dp`
   literal outside `:core:ui`, no `MaterialTheme.colorScheme` read outside `:core:ui`).
9. Update `DESIGN.md`, the bitácora note, and the architecture skill's "As built" trail.
10. Device verification (Pixel 5, debug demo jam, debug admin) — every tab and the song detail, both
    modes, screenshots; confirm Scenario 2 (musician mode pixel-identical) with a before/after diff.
11. Record evidence in `feature_list.json`/`PROGRESS.md`, status `passing`.

## Implementation Tasks

- [ ] T0 Baseline gate; record JVM test/class counts.
- [ ] T1 `BluesJamAdminPalette`/`BluesJamAdminColors`: add the three tokens with the computed hex
  values and derivation KDoc.
- [ ] T2 `BluesJamTheme.kt`: `LocalAdminMode`, `admin: Boolean` parameter, `@Composable get()` on the
  three tokens, `BluesJamMaterial.colorScheme` admin substitution; fix `BluesJamColorsTest`.
- [ ] T3 `ContrastTest.kt`: add the admin pairs with their recorded expected ratios.
- [ ] T4 `AdminBanner` component + copy + defaults in `:core:ui`, with a preview; a defaults test if
  any pure logic exists, otherwise state why not.
- [ ] T5 `:app`: add `koin-compose`; read `AdminSession.observeIsAdmin()` once in `MainActivity`;
  pass `admin` into `BluesJamTheme`.
- [ ] T6 `:app`: restructure `AppNavHost`/`TabsShell` so the banner (when admin) and a single
  status-bar inset wrap every destination with no doubled inset and no musician-mode layout shift.
- [ ] T7 Confirm/adjust the system-bar scrim behavior by device screenshot.
- [ ] T8 Full gate (`CI=true ./init.sh`); confirm `konsist`/`detekt`/`ktlint` wired, no new
  violation.
- [ ] T9 Update `DESIGN.md`, `bb-blues-jam-bitacora.md` (§6.21), the architecture skill's "As built"
  trail.
- [ ] T10 Device verification: Pixel 5, debug demo jam, debug admin flag on and off; screenshots of
  each tab and the song detail in both modes; confirm the banner's placement, that it does not cover
  content, and a `uiautomator`/TalkBack check that it is announced once, not repeatedly; confirm
  musician-mode screenshots are pixel-identical to a pre-slice baseline.
- [ ] T11 Record evidence in `feature_list.json`/`PROGRESS.md`; status `passing`.

## Verification Plan

- `CI=true ./init.sh` exits 0 with `konsist`, `detekt` and `ktlint` all `wired` (unit tests, Android
  lint, the 17-rule Konsist suite, detekt, ktlint — the standard gate, no rule change expected; if
  `amber-roles-allowlisted` or `no-material-theme-outside-core-ui` fires, that is a real defect to
  fix, not a rule to relax).
- `:core:ui` JVM tests: `BluesJamColorsTest` (updated count), `ContrastTest` (16 existing + new admin
  pairs), a new `AdminBannerDefaultsTest` if applicable — all must pass with documented, reviewed
  changes only (no silently-adjusted tolerance).
- No persistent E2E harness exists in this repo (`docs/risks-and-open-questions.md` / architecture
  skill: "No Compose UI test harness" standing decision). Device screenshots and `uiautomator`
  dumps are the closest equivalent and are required (see T10).
- `./init.sh` continues to run only the non-blocking standard gate; this slice does not change
  `init.sh` itself.
- Lint: `:core:ui` and `:app` must show no new issue.
- `git diff --check` clean.
- Device (Pixel 5; adb at `"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"`). Before any check,
  back up `local.properties` byte-for-byte, then set `bluesjam.demoUpcomingJamLive=true` so an
  upcoming jam with songs is visible without touching the real Sheet; confirm the logcat startup
  line shows the demo jam before checking anything (standing rule: device checks use the debug demo
  jam, never the real Sheet, never asking the user to add/delete Sheet jams). Steps:
  1. With `bluesjam.debugAdmin` **absent/false**, screenshot every tab (Próxima jam, Anteriores,
     Info) and the song detail: confirm they match the pre-slice baseline exactly (Scenario 2) —
     ideally by a pixel diff against screenshots taken before this slice's changes.
  2. Set `bluesjam.debugAdmin=true`, reinstall, relaunch; confirm the logcat startup line still ends
     ` admin (debug)`. Screenshot every tab, the song detail, the past jam detail, the catalog
     picker, the key picker, the assign-musician screen and the admin login: confirm the admin
     background/surfaces are visibly distinct from musician mode and the "MODO ADMIN" banner is
     present, under the status bar, above content, not overlapping anything, not scrolling away.
  3. `uiautomator dump` the banner: confirm it is one static text node, not a live region; toggle
     admin off via "Salir del modo admin" on Info and confirm the banner disappears immediately and
     the background reverts, with no separate flag left stale.
  4. Confirm the status bar/navigation bar area visually follows the admin tone (System bars); if
     not, note it as a follow-up rather than silently leaving musician-coloured bars in admin mode.
  5. Restore `local.properties` byte-for-byte (checksum before/after) and any other device setting
     touched.
  6. Save screenshots into `docs/evidence/admin-mode-highlight/` (musician baseline, admin — each
     tab and the song detail at minimum).

## Evidence To Capture

- Gate output summary (`CI=true ./init.sh`, test/class counts before and after).
- `ContrastTest` and `BluesJamColorsTest` diffs, with the new/changed assertions and the recorded
  ratios from the T1 table.
- Lint output for `:core:ui` and `:app`.
- Screenshots in `docs/evidence/admin-mode-highlight/`: musician baseline (pixel-identical check)
  and admin mode for every tab, the song detail, the past jam detail, each picker and the admin
  login.
- `uiautomator` excerpt proving the banner is a single static node.
- `local.properties` checksum before/after (unchanged).
- A note on the System bars finding (did the scrim follow admin mode with no `MainActivity` change,
  or did it need one).

## Orchestrator Amendments, 12 October 2026

These override the matching parts above.

- **A1, the background must be obvious at a glance.** The user asked for the background to *stand out*
  ("cambiemos el fondo para que resalte el estado"). `#1A120B` has almost the same luminance as the
  musician `#111318` and reads as nearly the same black on a phone. Pick a clearly tinted dark admin
  ramp instead, in the deep wine or deep blue family (for example a background around `#2A1218` or
  `#0F1C33`, with `surface`/`surfaceRaised` stepped from it the same way). Every existing ContrastTest
  pairing, including amber `key` and the 15%-amber open-slot fill, must still meet its current threshold.
  Compute and record the final values and ratios; neither tone is amber or amber-adjacent (D-17). The
  side-by-side screenshots must make the difference visible without being told.
- **A2, do not weaken `BluesJamColorsTest`.** If the `@Composable get()` change drops properties out of
  the reflection filter, keep the test's intent intact: assert the full role set explicitly (by name),
  including the three switching roles, rather than just lowering the count from 17 to 14. If a
  CompositionLocal-provided colours instance avoids the reflection change altogether while keeping
  `BluesJamTheme.colors.*` call sites unchanged, prefer it. Record which approach you used and why.

## Validator Checklist

- [ ] Admin background/surface/surfaceRaised switch for the whole app while
  `AdminSession.observeIsAdmin()` is true, with no `if (isAdmin)` branch added to any feature screen
  or `…Defaults` object (the seam is `LocalAdminMode`/`BluesJamTheme(admin = …)` only).
- [ ] Musician mode (flag false) is pixel-identical to the pre-slice baseline (Scenario 2).
- [ ] No new amber role; `AMBER_ROLE_ALLOWLIST` unchanged; the admin tokens are not amber (D-17).
- [ ] Every existing `ContrastTest` pairing still passes; the new admin pairs are present with their
  own recorded expected ratios, not copied from the musician values.
- [ ] `BluesJamColorsTest`'s updated count is explained in a comment, not silently adjusted.
- [ ] The "MODO ADMIN" banner is drawn once per composition, under the status bar, above the tab bar
  and above every full-screen route's content (song detail, past jam detail, pickers, admin login),
  never overlapping content, never scrolling away, announced once (not a repeating live region).
- [ ] Logging out reverts both the background and the banner in the same recomposition, with no
  stale state.
- [ ] `CI=true ./init.sh` exits 0 with `konsist`, `detekt`, `ktlint` all `wired`.
- [ ] `DESIGN.md`, `bb-blues-jam-bitacora.md` (§6.21) and the architecture skill's "As built" trail
  are updated.
- [ ] Device evidence: Pixel 5 screenshots of each tab and the song detail in both modes with the
  debug demo jam and debug admin flag; `local.properties` restored; no real Sheet jam created or
  touched.
- [ ] `feature_list.json`/`PROGRESS.md` updated; status `passing`, not `accepted`.

## Needs A Human Decision

- **None blocking.** The user's 12 October 2026 instruction (background change + fixed "MODO ADMIN"
  banner) is itself the decision that resolves what to build; the specific hex values in T1 are this
  spec's own computed proposal, consistent with the user's direction and with D-17/`ContrastTest`,
  not a reopening of an existing decision. If the implementer finds the chosen umber hue reads too
  close to the musician background on a real device (subjective, screen-dependent), that is worth
  raising before shipping, but the underlying approach (separate token object, `CompositionLocal`
  seam, banner at the shared composition root) should not be re-litigated without a new reason.
- **Flagged, not blocking:** whether the status/navigation bar scrim itself needs an explicit
  `MainActivity` change (System bars) is deliberately left to be confirmed by device screenshot
  during implementation rather than decided here, since it depends on how edge-to-edge actually
  composites on the target device/OS version.
