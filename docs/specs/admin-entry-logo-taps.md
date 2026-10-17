# Feature Implementation Spec: Enter and leave admin mode with five taps on the Info logo

## Source Feature

- `id`: admin-entry-logo-taps
- `area`: feature-info
- `depends_on`: `admin-passphrase-login` (accepted), `debug-admin-session` (accepted)
- `status`: not_started at planning time
- `source`: `feature_list.json`; organizer request, 17 October 2026: "elimina el boton 'entrar en
  modo admin' y 'salir del modo admin', tambien el texto 'modo admin activo'. Para entrar al modo
  admin, hay que hacer 5 taps en el logo de bahia blanca blues en info. Para salir tambien."

## Goal

Info stops showing the admin line (`Entrar como admin`, `Modo admin activo`, `Salir del modo
admin`). Five consecutive taps on the Bahía Blanca Blues logo at the top of Info do what the line
did: open the existing passphrase login when the device is not in admin mode, and leave admin mode
(`AdminSession.logOut()`) when it is. Everything else on Info, the login screen, the session and
the backend stay as they are.

## Non-Goals

- No change to `AdminSession`, `DefaultAdminSession`, `AdminCredentialStore`, `AdminWriter`, the
  login screen/presenter, Apps Script, or `DebugAdminSession` (D-11, D-13: no new mutation; leaving
  admin stays `AdminSession.logOut()`, which is not a Sheet mutation).
- No change to `BandLogo` in `:core:ui` and no gesture on the logo of Próxima jam (`:feature:next-jam`
  headers). Only the logo instance drawn by `InfoScreen` gets the gesture.
- No confirmation dialog, Snackbar, toast, haptic or visual tap counter (decisions T2, T3).
- No change to the `AccessRefused` copy (`Salí del modo admin en Info y volvé a entrar.`) in
  `NextJamCopy`, `PastJamsCopy`, `ApplyOutcomeCopy` (decision T6).
- No change to `AdminBanner` / admin palette (`admin-mode-highlight`).

## Job Story

When I organize the jam and lend or show my phone,
I want the admin entry and exit to be invisible to musicians,
so the Info screen looks the same for everyone and only someone who knows the gesture gets in.

## Users And Permissions

- Musician: sees Info without any admin text. Five quick taps on the logo open the login; without
  the passphrase nothing changes (server-side check unchanged, D-11).
- Admin (stored passphrase or debug forced session): five quick taps on the logo leave admin mode
  at once. The local flag still only decides which controls are drawn.

## Decisions (planner defaults, recorded; none needs the organizer before implementing)

- **T1 Tap rule.** 5 taps, each within **1500 ms of the previous one**. A gap longer than 1500 ms
  resets the count to 0, so the late tap starts a new sequence and counts as 1 (see Technical
  Approach for the exact rule). On the 5th tap the action runs and
  the count resets to 0. Leaving Info (tab switch, opening the login) resets the count, because the
  counter lives in plain `remember` (not saveable); rotation also resets it. Constants:
  `LOGO_TAPS_REQUIRED = 5`, `LOGO_TAP_GAP_MILLIS = 1_500L` in the presenter.
- **T2 Leaving needs no confirmation.** The organizer asked for 5 taps to exit; A4 of
  `admin-passphrase-login` already said no confirmation. Five deliberate taps are the confirmation.
- **T3 Feedback.** None added. Entering shows the login screen; leaving is already unmistakable:
  the `MODO ADMIN` banner disappears and the admin palette reverts (`admin-mode-highlight`), and
  admin controls vanish. No new copy, so no copy approval needed.
- **T4 Accessibility (accepted tradeoff).** The logo keeps its current semantics: an image with
  `contentDescription = "Logo de Bahía Blanca Blues"`, no `Role.Button`, no click action, no
  `onClickLabel`. The gesture uses `pointerInput { detectTapGestures(onTap = …) }`, which adds no
  semantics, so TalkBack does not advertise it. Consequence: a TalkBack user cannot reach the
  login or the logout from Info unless TalkBack is paused (TalkBack double-tap sends a semantics
  click, which this node does not have). Accepted because the gesture is hidden on purpose and the
  admins are one or two known sighted people; recorded in `docs/risks-and-open-questions.md`.
- **T5 Debug admin session.** `bluesjam.debugAdmin=true` keeps working: five taps call the same
  `AdminSession.logOut()` the old button called, so `DebugAdminSession` turns its forced state off
  and calls the real logout; forced admin returns on the next process start. No `:app` change.
- **T6 `AccessRefused` copy unchanged.** "Salí del modo admin en Info y volvé a entrar" is still
  true (the exit is on Info) and names no button; only admins see it and they know the gesture.

## Acceptance Scenarios

### Scenario 1: The admin line is gone
Given Info in either state, When it is drawn, Then there is no `Entrar como admin`, `Modo admin
activo` or `Salir del modo admin` text and nothing replaces it; the rest of Info is unchanged.

### Scenario 2: Musician opens the login
Given not in admin mode, When the logo is tapped 5 times with gaps ≤ 1500 ms, Then
`Params.onOpenAdminLogin` (the current one) is called exactly once and `logOut` is not called.

### Scenario 3: Admin leaves
Given admin mode, When the logo is tapped 5 times with gaps ≤ 1500 ms, Then `AdminSession.logOut()`
is called exactly once and `onOpenAdminLogin` is not called.

### Scenario 4: Too few or too slow taps do nothing
Given either state, When the logo is tapped 4 times, or 5 times with one gap > 1500 ms, Then neither
action runs.

### Scenario 5: After an action the count restarts
Given 5 taps just ran an action, When 4 more quick taps follow, Then nothing runs; a 5th runs again.

### Scenario 6: Other logos are inert
Given Próxima jam, When its header logo is tapped 5+ times, Then nothing happens (no code change there).

## Repository Research

### Files Inspected

- `feature/info/src/main/kotlin/com/bbbjam/feature/info/InfoPresenter.kt` — `InfoPresenter(linkOpener,
  adminSession)`, `Params(onOpenAdminLogin)` read via `rememberUpdatedState`, `isAdmin` via
  `collectAsState(false)`, events `AdminEntryTapped` / `LogOut` (`scope.launch { adminSession.logOut() }`).
- `.../InfoUiModel.kt` — `adminEntry: AdminEntryUiModel` (`LoggedOut`, `LoggedIn`), the two events.
- `.../InfoScreen.kt` — `BandLogo()` first in the column; `AdminLine` + `CaptionAction` at the end.
- `.../InfoCopy.kt` — `ADMIN_ENTRY`, `ADMIN_ACTIVE`, `ADMIN_LOG_OUT`.
- `feature/info/src/test/.../InfoPresenterTest.kt`, `FakeAdminSession.kt` (`logOuts` counter,
  `isAdmin` MutableStateFlow), `InfoModuleTest.kt`; `di/InfoModule.kt` (`factory { InfoPresenter(get(), get()) }`).
- `core/ui/.../brand/BandLogo.kt` — `BandLogo(modifier)`: modifier applied to the outer 112dp Box;
  image has a content description. Also used by `NextJamScreen.kt` and `NextJamListContent.kt`.
- `core/data/.../admin/AdminSession.kt`, `DefaultAdminSession.kt` — `logOut()` clears the store, no request.
- `app/src/debug/java/com/bbbjam/debug/DebugAdminSession.kt`, `DebugOverrides.kt`, `app/build.gradle.kts`
  (`bluesjam.debugAdmin`, `applicationId = "com.bbbjam"`), `docs/specs/debug-admin-session.md`.
- `app/src/main/java/com/bbbjam/navigation/TabsShell.kt` (line 87, `InfoScreen(onOpenAdminLogin)`),
  `AppNavHost.kt` (`AdminBanner` when admin).
- `core/ui/.../presenter/EventHandler.kt` — equality by key, so `expected()` comparisons keep working.
- Docs mentioning the line: `DESIGN.md` (§6 Info, §7 login entry), `bb-blues-jam-design-prompt.md`
  (§6 "Acceso discreto a 'Entrar como admin'"), `docs/user-and-access-model.md` (lines ~114-118),
  `docs/risks-and-open-questions.md` (~159), `docs/apps-script-api.md` (~576),
  `core/data/.../admin/WriteOutcome.kt` KDoc (line 16), architecture skill (`AdminEntryUiModel`).
- Bitácora D-11, D-13, D-15, D-29.

### Existing Patterns To Follow

- Presenter decides, screen forwards events; Molecule + Turbine tests with `RecompositionMode.Immediate`
  under `runTest` (virtual time), expected models written from literal copy.
- Async side effects via `rememberCoroutineScope()`; callbacks via `rememberUpdatedState`.

### Current Gaps

- No clock abstraction exists; the timer below uses coroutine `delay`, which is virtual in `runTest`.
- No Compose UI test infrastructure in `:feature:info`; the gesture wiring is checked on the emulator.

## Technical Approach

All in `:feature:info` (allowed deps: `:core:ui`, `:core:data`; unchanged Gradle file).

1. **UiModel.** Remove `adminEntry` and `AdminEntryUiModel`. Replace events `AdminEntryTapped` and
   `LogOut` with one `data object LogoTapped : Event` (KDoc: hidden admin gesture, T1).
2. **Presenter.** Keep `isAdmin` collection and `Params(onOpenAdminLogin)`. Hold a tiny non-snapshot
   counter in `remember { LogoTapCounter() }` (internal class in `InfoPresenter.kt` or its own file:
   `var count: Int`, `var reset: Job?`), so taps do **not** recompose or emit a new model. On
   `LogoTapped`: cancel `reset`; `count++`; if `count >= LOGO_TAPS_REQUIRED` → `count = 0` and, read at
   call time, `if (isAdmin) scope.launch { adminSession.logOut() } else currentOnOpenAdminLogin()`;
   else `reset = scope.launch { delay(LOGO_TAP_GAP_MILLIS); count = 0 }`. This makes "gap > 1500 ms"
   mean the count is already 0 when the late tap arrives, so that tap counts as 1. Update the class
   KDoc (D-15, D-13, D-11, T1). Events from a stale model must act on the current admin state and
   current callback (the handler reads the `State`-backed `isAdmin` and `currentOnOpenAdminLogin`).
3. **Screen.** Delete `AdminLine` and `CaptionAction` (and now-unused imports such as
   `TextDecoration`, `TextAlign` if unused). Draw
   `BandLogo(modifier = Modifier.pointerInput(Unit) { detectTapGestures(onTap = { currentEvents(LogoTapped) }) })`
   with `val currentEvents by rememberUpdatedState(model.events)`. No `clickable`, no semantics
   change, no ripple, no amber, no new tokens (D-17). Update the screen KDoc ("discreet admin line"
   → hidden logo gesture). Preview stays.
4. **Copy.** Remove `ADMIN_ENTRY`, `ADMIN_ACTIVE`, `ADMIN_LOG_OUT` from `InfoCopy` (`AdminLoginCopy.TITLE`
   `Entrar como admin` stays: it is the login screen title).
5. **Koin.** `InfoModule` unchanged (constructor unchanged).

## Expected File Changes

- `feature/info/src/main/kotlin/com/bbbjam/feature/info/InfoUiModel.kt` — modify; remove `adminEntry`/`AdminEntryUiModel`, add `LogoTapped`.
- `.../InfoPresenter.kt` — modify; counter, constants, KDoc.
- `.../InfoScreen.kt` — modify; gesture on Info's logo, remove admin line.
- `.../InfoCopy.kt` — modify; drop three constants.
- `feature/info/src/test/kotlin/com/bbbjam/feature/info/InfoPresenterTest.kt` — modify; drop
  `adminEntry`, replace the two admin tests with the tap tests below.
- `core/data/src/main/kotlin/com/bbbjam/core/data/admin/WriteOutcome.kt` — KDoc line only
  ("taps 'Salir del modo admin' on Info" → "leaves admin mode with five taps on the Info logo").
- Docs listed under Durable Documentation Impact. Nothing in `:core:ui`, `:app`, `backend/`, Konsist.

## Visual Design Impact

- UI involved: yes. Design source: `DESIGN.md` §6 Info.
- Info loses its last element; the column now ends with the Redes card (its bottom padding `lg`
  unchanged). The logo looks identical; no pressed state.
- New design artifact required: no.

## Durable Documentation Impact

- `DESIGN.md` — update §6 Info (replace the admin-line paragraph with: no admin text; five taps,
  each ≤ 1.5 s apart, on Info's logo open the login (musician) or leave admin mode at once (admin);
  no ripple, no feedback beyond the banner/palette change; Próxima jam's logo is inert) and §7
  ("from Info" → "from the hidden logo gesture on Info"). Keep "Band logo" asset line.
- `bb-blues-jam-design-prompt.md` §6 — "Acceso discreto a 'Entrar como admin'" → "Acceso oculto al
  modo admin: 5 toques en el logo (entrar y salir), sin texto en pantalla".
- `docs/user-and-access-model.md` — the "visible logout action" bullet and the rotation recovery
  sentence: logout is now the five-tap gesture on Info's logo (hidden, not visible); recovery is
  five taps to leave, five taps to open the login.
- `docs/risks-and-open-questions.md` — update the recovery sentence (~159); add the T4 TalkBack
  tradeoff and "a musician who taps the logo five times sees the login; harmless without the
  passphrase".
- `docs/apps-script-api.md` (~576) — recovery wording as above.
- `.claude/skills/architecture/SKILL.md` — `InfoPresenter` no longer has `AdminEntryUiModel`; it
  handles `LogoTapped` (5 taps, 1.5 s gap) and calls `onOpenAdminLogin` or `AdminSession.logOut()`.
- `bb-blues-jam-bitacora.md` — optional short dated note under the D-15 area that the "entrada
  discreta" became a hidden gesture (organizer, 17 Oct 2026). Not a new D-number: D-15 still holds.
- `PROGRESS.md`, `feature_list.json` — evidence. `AGENTS.md`, `CONTEXT.md`: not needed. Old specs and
  evidence files are historical: do not edit.

## Implementation Tasks

1. Write the new presenter tests first and show them failing (save to
   `docs/evidence/admin-entry-logo-taps/red-presenter.txt`).
2. Change `InfoUiModel`, `InfoPresenter`, `InfoCopy`, `InfoScreen`; KDoc in `WriteOutcome.kt`.
3. `./gradlew ktlintFormat`, `./gradlew :feature:info:test`, then `CI=true ./init.sh`.
4. Update the docs listed above.
5. Emulator device check; evidence; update `PROGRESS.md` / `feature_list.json` to `passing`.

## Verification Plan

**JVM (Molecule, `runTest` virtual time, `FakeAdminSession`)** in `InfoPresenterTest`:
- first model equals the approved copy without any admin field (both `initiallyAdmin` false and true);
- musician: 5 taps (gaps 0 or `advanceTimeBy(1_400)`) → current callback called once (after a
  `Params` swap, the second one), `logOuts == 0`;
- admin: 5 taps → `logOuts == 1`, callback not called, model still has no admin text;
- 4 taps → nothing; 5 taps with one `advanceTimeBy(1_600)` gap in the middle → nothing; after that
  gap, 5 more quick taps → action once (the count restarted);
- after an action, 4 taps → nothing, 5th → action again (count reset on success);
- stale model: events from the first model after the session flips to admin → `logOut`, not login;
- taps emit no new UiModel (`expectNoEvents()` after 4 taps).
Avoid gaps exactly at 1500 ms (boundary is order-dependent in virtual time).

**Gate:** `CI=true ./init.sh` exit 0, `konsist`, `detekt`, `ktlint` all `wired`; Konsist rule count
unchanged (no rule added, so no failing-case demonstration needed).

**Device (emulator only, `Pixel_6a_API_34`).** The Pixel 5 holds a real admin session: do not
install on it, tap on it, log out on it or clear its data. `./gradlew :app:installDebug` installs on
**every** connected device, so do not use it while the Pixel is attached. Steps:
1. `./gradlew :app:assembleDebug`; record `md5sum local.properties` (must start `569913ae`) before
   and after; never print its contents or the passphrase; do not edit the flags.
2. Start the AVD, take its serial from `adb devices` (e.g. `emulator-5554`) and use `adb -s <serial>`
   for every command. Fresh install on the emulator: `adb -s <serial> uninstall com.bbbjam` (ignore
   "not installed") then `adb -s <serial> install app/build/outputs/apk/debug/app-debug.apk`.
3. Launch; the debug admin session starts in admin (`MODO ADMIN` banner; logcat line ends
   ` admin (debug)`). Open Info: no admin text at the end (uiautomator dump + screenshot).
4. Tap the logo 4 times quickly → still admin. Tap 5 times with ~2 s gaps → still admin.
5. Tap 5 times quickly (`adb input tap` in one shell line) → banner gone, musician palette, admin
   controls gone on Próxima jam. Screenshot.
6. On Info tap 5 times quickly → the login screen (`Entrar como admin`, `Frase de acceso`) opens.
   Type nothing; press `Volver` → back on Info, still musician.
7. Tap Próxima jam's logo 5 times → nothing.
8. `adb -s <serial> shell am force-stop com.bbbjam`, relaunch → admin again (debug forced state).
9. No write is possible (no passphrase stored on the emulator; no admin action tapped). Startup
   reads the Sheet only.
TalkBack is not enabled. Evidence in `docs/evidence/admin-entry-logo-taps/`.

## Evidence To Capture

Red test output; gate output; emulator dumps/screenshots for steps 3, 5, 6; `local.properties` md5
before/after; the emulator serial used and a note that the Pixel was untouched.

## Validator Checklist

- [ ] No `ADMIN_ENTRY`/`ADMIN_ACTIVE`/`ADMIN_LOG_OUT`, `AdminEntryUiModel`, `AdminLine` remain.
- [ ] Gesture only on Info's logo; `BandLogo.kt` and `:feature:next-jam` untouched.
- [ ] Logo node has no click semantics/role; no amber, only `BluesJamTheme` tokens (D-17).
- [ ] Logout still goes through `AdminSession.logOut()`; no new repository function, no ViewModel,
      no `get()` in classes, no feature-to-feature dependency, no `:core:data`/backend logic change.
- [ ] Tests cover both directions, too few, too slow, reset after success, stale model, no emission.
- [ ] Gate exit 0 with all three tools wired; device check on the emulator only, md5 unchanged.
- [ ] Docs in the list updated; historical specs/evidence untouched.

## Open Questions (for the organizer; defaults above apply until answered)

- Q1 (T4): accept that TalkBack users cannot use the gesture? Default yes.
- Q2 (T3): any feedback on exit beyond the banner disappearing? Default none.
- Q3 (T1): is 1.5 s between taps comfortable? Default yes; it is one constant.
- No contradiction with D-01 … D-29 found: D-15 keeps the login in `:feature:info` (its "entrada
  discreta" becomes hidden); D-13 holds (logout stays `AdminSession.logOut()`); D-11 holds.
