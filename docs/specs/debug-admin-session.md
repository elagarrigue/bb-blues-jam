# Feature Implementation Spec: Debug-only admin session

## Source Feature

- `id`: debug-admin-session
- `area`: harness
- `depends_on`: `admin-passphrase-login` (`accepted`), `debug-demo-upcoming-jam` (`accepted`)
- `source`: the user, 5 October 2026: "no quiero ser bloqueante, setea el modo admin vía código
  para hacer las pruebas". Written by the orchestrator, following the pattern of
  `debug-demo-upcoming-jam`. No product question open.

## Goal

Device checks of admin controls (the `admin-*` slices) stop depending on the user typing the real
passphrase on the Pixel 5. A debug build with a local flag on reports admin mode from start. Release
builds never contain the code.

## Non-Goals

- **Authorizing anything.** The local flag only decides which controls are drawn (AGENTS.md: every
  write is validated server-side in Apps Script). This slice never stores, sends, or knows the real
  passphrase. A write from a debug-admin session without a stored passphrase must fail as the
  server decides; how write slices verify real writes is their own question.
- No change to `:core:data`, the backend, the login screen or Info's behaviour in release.

## Decisions

1. **Flag.** `bluesjam.debugAdmin=true` in the git-ignored `local.properties`, read in
   `app/build.gradle.kts` like the demo flags, into `BuildConfig.DEBUG_ADMIN`: the flag's value in
   `debug`, hard `false` in `release`, `false` when absent.
2. **Where the code lives.** Only `app/src/debug/`. `Koin.debugOverrides()` is restructured so each
   override is independent: today it returns nothing unless `DEMO_UPCOMING_JAM` is on; after this
   slice it builds the list from each flag on its own (demo jam, draft, admin). The release no-op is
   unchanged.
3. **Decorator.** `DebugAdminSession(real: AdminSession) : AdminSession`. `observeIsAdmin()` emits
   `true` while a debug "forced" state is on, otherwise the real value; the forced state starts on.
   `logOut()` turns the forced state off **and** calls the real `logOut()`, so the logout flow can
   still be checked on the device (forced admin returns on the next process start). `logIn`
   delegates to the real session unchanged. Constructor injection; no `get()` inside the class.
4. **Visibility in evidence.** Info, while forced, must make it impossible to confuse with a real
   login in screenshots and logs: the startup log line gains ` admin (debug)` when the flag is on.
   No UI copy change (no user approval needed).

## Verification

- Gate: `CI=true ./init.sh` exit 0, konsist/detekt/ktlint `wired`, Konsist 17/17 (no rule change).
- `:app:assembleRelease`: release `BuildConfig.DEBUG_ADMIN` is `false` and the release dex has no
  `DebugAdminSession` class (same scan as `debug-demo-upcoming-jam`).
- JVM tests in `app/src/testDebug/`: forced on → `observeIsAdmin` emits true even when the real one
  is false; after `logOut()` it emits the real value and the real `logOut` ran once; `logIn` delegates
  once with the same argument; each flag independent (admin flag on, demo flag off → only the admin
  override is loaded, and vice versa). Failure demonstrations, restored by SHA-1: (1) `logOut` not
  calling the real one fails its test; (2) gating the admin override on `DEMO_UPCOMING_JAM` again
  fails the independence test.
- Device (Pixel 5): flag on → fresh install shows "Modo admin activo" on Info with no login; log line
  shows ` admin (debug)`; "Salir del modo admin" returns to "Entrar como admin"; a relaunch shows
  admin again. Flag off → reinstall shows "Entrar como admin". Leave the flag **on** at the end and
  record it. Never enable TalkBack or accessibility services; never print `local.properties`.

## Docs To Update

- `.claude/skills/architecture/SKILL.md`: the debug overrides note (both flags, independent).
- `docs/technical-discovery.md` verification bullet; `docs/user-and-access-model.md`: one line that a
  debug-only flag draws admin controls in development builds and authorizes nothing.
- `feature_list.json` evidence and a new `PROGRESS.md` session.

## User Approvals

None pending: requested by the user on 5 October 2026. No new dependency, no gate rule change, no
D-xx change.
