# Feature Implementation Spec: Debug-only demo upcoming jam

## Source Feature

- `id`: debug-demo-upcoming-jam
- `area`: harness
- `depends_on`: `song-detail-screen` (`accepted`)
- `source`: the user, 5 October 2026: "no quiero seguir siendo el blocker en tu desarrollo. ¿Podés
  obviar por código la próxima jam para no frenarte?" Written by the orchestrator (a small harness
  slice, no product question open).

## Goal

Device checks of screens that need an upcoming jam (Próxima jam, the song detail, later the admin
slices) stop depending on the user adding and deleting a temporary jam in the real Sheet. A debug
build, with a local flag on, shows a fixed demo upcoming jam built in code whenever the Sheet has
none. Release builds never contain the code.

## Non-Goals

- No change to `:core:data`, the Sheet, Apps Script or the cache. The demo jam is never written to
  Room and never sent anywhere.
- No UI to toggle it; no runtime switch; no product behaviour change in release.
- Not a test fixture for JVM tests (those keep their own fixtures).

## Decisions

1. **Flag.** `bluesjam.demoUpcomingJam=true` in the git-ignored `local.properties`, read in
   `app/build.gradle.kts` like `bluesjam.appsScriptUrl`. It becomes a `BuildConfig` boolean
   `DEMO_UPCOMING_JAM` that is the flag's value in `debug` and hard `false` in `release`. Absent
   means `false`.
2. **Where the code lives.** Only in `app/src/debug/` (Kotlin), so the release variant cannot
   compile or ship it. `app/src/release/` gets the matching no-op so `:app` main code calls one
   function with the same signature in both variants (e.g. `fun debugOverrides(): List<Module>`,
   empty in release). The binding happens in `:app` (D-03, D-16: constructor injection; the Koin
   override goes through a module loaded after `dataModule`, `allowOverride` as Koin needs).
3. **Decorator.** `DemoUpcomingJamRepository(real: JamsRepository, clock or calendar as needed) :
   JamsRepository`. `observeJams()` maps each real snapshot: if `upcoming == null`, set `upcoming`
   to the demo jam; otherwise pass the snapshot through unchanged. `past` and `freshness` are never
   touched. `refresh()` delegates. The real jam always wins, so a real upcoming jam is never hidden.
4. **Demo content.** A `Jam` dated a fixed number of days after "today" in Buenos Aires
   (`JamCalendar`) so it is always upcoming; venue `Demo (solo debug)` so a screenshot can never be
   mistaken for real data. Status published, a setlist of about 8 songs taken from titles that exist
   in `docs/api-samples/catalog-seed.json` (resolved titles written in the fixture, not looked up),
   covering what device checks need:
   - every instrument column at least once, with open and filled slots mixed;
   - one song with no open slot at all; one with every slot open;
   - an instrument with `-` (not in the lineup) in some songs;
   - one `Otros` entry with a normal instrument and one with a long free-text instrument (the
     clipping case fixed in `song-detail-screen`);
   - one song whose key has an accidental (e.g. `Bb`, `F#m`).
   Names are obviously fictional first names. The fixture is pure Kotlin data in the debug source
   set; no JSON parsing, no new dependency.
5. **Logging.** When the demo replaces an empty upcoming, the existing `jams cache:` debug log line
   must make it visible (e.g. `upcoming <date> (demo)`), so evidence can never confuse demo and real
   data.

## Verification

- Gate: `CI=true ./init.sh` exit 0, konsist/detekt/ktlint all `wired`, Konsist 17/17 (no new rule,
  no rule change).
- `./gradlew :app:assembleRelease` succeeds and the release APK/classes contain no
  `DemoUpcomingJam` class (check with the built intermediates or `apkanalyzer`/`unzip -l` + `grep`
  on dex strings; record the command).
- JVM tests in `app/src/testDebug/` (or `app/src/test` if the debug classes are visible there):
  real upcoming wins; demo fills an empty upcoming; `past` and `freshness` equal the real ones;
  `refresh()` delegates once. Failure demonstrations: (1) the decorator always replacing upcoming
  fails the "real wins" test; (2) setting `past = emptyList()` fails the "past untouched" test.
  Restore by SHA-1.
- Device (Pixel 5): with the flag on, a fresh install shows the demo jam on Próxima jam and the log
  line says `(demo)`; the song detail opens for a demo song. With the flag off (or absent), reinstall
  and the screen shows the Sheet's real state. At the end leave the flag **on** in `local.properties`
  (a real upcoming jam wins anyway) and record that in PROGRESS.
- `local.properties` stays git-ignored; nothing about the Apps Script URL changes or is printed.

## Docs To Update

- `.claude/skills/architecture/SKILL.md`: a short "Debug demo jam" note (where it lives, the flag,
  that device checks use it instead of Sheet test data).
- `docs/technical-discovery.md` verification section, and `AGENTS.md` only if a command changes
  (it should not).
- `feature_list.json` evidence and a new `PROGRESS.md` session.

## User Approvals

None pending: the user asked for this on 5 October 2026. No new dependency, no gate rule change, no
D-xx change.
