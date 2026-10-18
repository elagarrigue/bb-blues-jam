# Spec: feature-api-surface

Source: `feature_list.json` entry `feature-api-surface` (area `features`, depends on `shared-test-fixtures`, which is `accepted`). Code review finding 6 of 17 October 2026. Status at planning time: `not_started`.

## Goal

Shrink each `:feature:*` module's public API to what `:app` imports. Behavior, UI, Koin wiring, navigation and tests do not change. New verification rule: a Konsist allowlist of public top-level declarations per feature, shown failing first (Decision 2).

Non-goals: moving or renaming any declaration; touching `:core:*` visibility; `wiring-and-preview-hygiene` (RunLock defaults, preview fixtures out of `*Screen.kt` stay for the next feature, so do not move previews, only repoint calls); changing test assertions.

## Repository research (inspected)

Measured on 17 October 2026 with `grep -rhE '^(modifiers)*(class|interface|object|fun|val|var|typealias|const val)'` over `feature/<m>/src/main`, dropping lines that start with `private` or `internal` (top-level only; nested members of public types are not counted):

| Module | Public top-level declarations (before) | Target (after) |
|---|---|---|
| `:feature:next-jam` | 43 | 6 |
| `:feature:past-jams` | 27 | 3 |
| `:feature:info` | 10 | 3 |
| `:feature:assistant` | 42 | 2 |
| Total | 122 | 14 |

The implementer re-measures with the same command before editing and records both tables in evidence. Treat the 122 as my measurement, not a fixed number.

`:app` imports from features (all of `app/src`, tests included; no other module imports a feature): `NextJamScreen`, `AddSongScreen`, `AssignMusicianScreen`, `SetKeyScreen`, `SongDetailScreen`, `PastJamsScreen`, `PastJamDetailScreen`, `InfoScreen`, `AdminLoginScreen`, `AssistantScreen`, and the Koin vals `nextJamModule`, `pastJamsModule`, `infoModule`, `assistantModule`. `NextJamSetlistWrites`, `SongOverlaySources` and `JamsAdminWrites` are not referenced from `:app` (grep empty). Feature tests and the four `*ModuleTest` live in the same module, so they see `internal`. The `*Content` functions are already `internal`.

Cause (read in `NextJamScreen.kt`, `InfoScreen.kt` and the other screens): each public `fun XScreen(..., presenter: XPresenter = koinInject())` exposes the presenter type, so Kotlin's exposed-visibility rule ("public function exposes its internal parameter type") forces the presenter, its nested `Params`, its `UiModel` and their constructor parameter types (e.g. `NextJamSetlistWrites`) to be public.

Kotlin rules that matter (language knowledge, to be confirmed by the compiler):
- A public function may not have an internal type in its signature, including a parameter's default value type. Hence the presenter parameter must leave the public function.
- A public Koin `val module = module { factory { InternalClass(get()) } }` is fine: the lambda body is not part of the signature, and the val's type is `org.koin.core.module.Module`.
- Internal classes may implement public `:core:ui` interfaces (`Presenter<M, P>`, `UiModel`, `UiEvent`); `override` members need no extra change. A public nested class inside an internal class is effectively internal.
- `internal` members get a mangled JVM name; nothing here uses reflection or Java callers on them, but see Risks.

## Technical approach

1. Public screen keeps only what `:app` passes (navigation callbacks, `modifier`, `contentPadding`, route arguments) and delegates:
   `fun XScreen(<public params>) { XScreen(<same params>, presenter = koinInject()) }`
   plus `internal fun XScreen(<same params>, presenter: XPresenter, ...)` (presenter required, no default) containing the old body (build `Params`, call `presenter.present`, call `XContent`). If same-name overload resolution is ambiguous or confusing, name the internal one `XScreenWith`; implementer picks one convention for all ten and records it. Keep the KDoc on the public function.
2. Previews and `*Preview.kt` call the internal overload with a presenter where they did before (`InfoScreen`/`AdminLoginScreen` previews pass `presenter =`); previews already calling `XContent` are untouched.
3. Mark `internal` every other top-level declaration that is public in the four modules: presenters, UiModels, Params, mappers, `*Copy`, `*Defaults`, holders (`NextJamSetlistWrites`, `SongOverlaySources`, `JamsAdminWrites`), pure functions (`addSongContent`, `assistantModel`, ...), `AssistantChat`, `ProposalApplier`, `JamCreationRunner`, `RunLock`-style classes. Nothing else changes: not a signature, not a Koin line. `di/*Module.kt` stays public.
4. Compile fixes that follow are only visibility ones: a now-internal declaration exposing a still-private type needs the same or wider visibility; fix by making the private type `internal`, not by widening anything.
5. Tests need no edit. If a test fails to compile because of visibility, stop and report: that means a declaration is used across a module boundary that this spec missed (for example from `:app` tests or a test fixture) and must be reconsidered, not silently re-publicized.

## Decision 2: verification rule (explicit API mode vs Konsist)

- Rejected: Kotlin explicit API mode in a convention. It forces a visibility modifier on every declaration but does not prevent writing `public`, so it checks that a choice was made, not that the surface is minimal. It needs a `kotlin { explicitApi() }` configuration through `bluesjam.android.feature` in `build-logic`, a build-rule change affecting all feature modules and recompiling `build-logic` (about 18 s once), it adds `public` noise to the 14 remaining declarations plus public members of Koin holders, and its configuration under AGP 9 built-in Kotlin was not tried. Not worth it for four modules.
- Chosen: one new Konsist rule (rule 21) in `ModuleIsolationTest`, `feature-public-api-allowlisted`: for every file under `feature/*/src/main`, each top-level declaration (class, interface, object, function, property, typealias) that is public (no `private`/`internal`) must be named in an `FEATURE_PUBLIC_API_ALLOWLIST` map keyed by module path, with exactly the names listed in the table below. A new public name is a reviewed decision, as `AMBER_ROLE_ALLOWLIST` already does. Use Konsist's declaration API (`topLevelDeclarations()` with a visibility check), not a text match, if available in the pinned version; otherwise a text match on top-level lines, stating the limit.
  Allowlist: `feature/next-jam`: `NextJamScreen, AddSongScreen, AssignMusicianScreen, SetKeyScreen, SongDetailScreen, nextJamModule`; `feature/past-jams`: `PastJamsScreen, PastJamDetailScreen, pastJamsModule`; `feature/info`: `InfoScreen, AdminLoginScreen, infoModule`; `feature/assistant`: `AssistantScreen, assistantModule`. Test sources are not checked. The rule must fail for a module not in the map that has any public declaration, so a new feature module is covered (it adds its entry in the same diff).
- Failing first, mandatory: write the rule before step 3 of the approach, run `./gradlew :konsist-test:test` (or the gate) and save the red output listing the ~108 offenders to `docs/evidence/feature-api-surface/konsist-failing-run.txt`; then do the visibility changes until green. Also demonstrate the negative case after green: add a temporary public class to one feature, show red, remove it.
- Update the rule count (20 to 21) in `.claude/skills/architecture/SKILL.md` and wherever "20 rules" is stated (grep for it).

## Expected file changes

- ~10 screen files (`NextJamScreen.kt`, `addsong/AddSongScreen.kt`, `assign/AssignMusicianScreen.kt`, `setkey/SetKeyScreen.kt`, `songdetail/SongDetailScreen.kt`, `PastJamsScreen.kt`, `detail/PastJamDetailScreen.kt`, `InfoScreen.kt`, `AdminLoginScreen.kt`, `AssistantScreen.kt`): public wrapper + internal overload.
- Roughly every other main file in the four feature modules: add `internal` (no other change).
- `konsist-test/src/test/kotlin/com/bbbjam/konsist/ModuleIsolationTest.kt`: rule 21 and the allowlist.
- `:app`: no change (verify by diff). Feature test sources: no change (verify by diff).
- Docs: `.claude/skills/architecture/SKILL.md` (a short "feature public API" paragraph and the rule count); `PROGRESS.md` (append a new entry only; do not edit other sessions' entries); `feature_list.json` status and evidence for this feature only. `AGENTS.md`: not needed.

## Implementation tasks

1. Baseline: `CI=true ./init.sh` exit 0; record testcase count (was 1153 after `shared-test-fixtures`; use the actual) and the per-module public count (command above) in `docs/evidence/feature-api-surface/`.
2. Add rule 21 and the allowlist; capture the failing run.
3. Split the ten screens (approach 1, 2).
4. Add `internal` across the four modules until rule 21 and the compiler are green. Do it module by module, compiling `:feature:<m>:compileDebugKotlin` and `:feature:<m>:testDebugUnitTest` after each.
5. Negative case for the rule, then removal.
6. Re-measure counts (expect 6, 3, 3, 2 = 14), run `CI=true ./init.sh`, record testcases (expect equal to baseline), `git diff --stat -- app feature/*/src/test` (expect empty), and `git diff -- ':!*.kt'` for stray changes.
7. Docs and `feature_list.json`; set status `passing`, not `accepted`.

## Verification plan

- Public declaration counts before and after, per module, same command (evidence file).
- Rule 21 red before, green after, plus the temporary-public negative case.
- All four `*ModuleTest` (Koin resolution) and every presenter test pass unchanged; test count equal before and after (no test added or removed; the Konsist rule is one test in `:konsist-test`, so the total rises by exactly that one: record it as such, "+1 Konsist rule, none other").
- `CI=true ./init.sh` exits 0 with `konsist`, `detekt`, `ktlint` all `wired`; also a release assemble is not required, but `./gradlew :app:assembleDebug` must compile (the `:app` imports are the whole contract).
- No device needed: no runtime behavior changes. Optional smoke: `:app:installDebug` and open each tab; skipped is acceptable and must be said.

## Risks and assumptions

- Assumption (not verified): the Android unit-test compilation of a library module sees `internal` main declarations; existing tests already call internal `*Content` and mappers, so this holds.
- Assumption: no `app` debug/release source set (`app/src/debug`, `testDebug`) uses a feature type beyond the 14; the grep covered all of `app/src` and found only those imports.
- Risk: `@Preview` functions and KDoc `[Type]` links to now-internal types are fine; detekt `UndocumentedPublicClass` is not enabled (check `config/detekt/detekt.yml` if it complains).
- Risk: same-name overloads of a `@Composable` could confuse the Compose compiler/IDE; use the `XScreenWith` fallback if the build or a preview complains.
- Risk: a declaration the compiler reports as exposing a private type signals a hidden coupling; resolve by narrowing, never by widening.

## Validator checklist

- Before/after counts reproduce with the command; after equals 6/3/3/2.
- Public screens' parameters contain no presenter, UiModel or `Params`; ten internal overloads exist; `:app` and feature test sources show an empty diff.
- Rule 21 exists, its red evidence lists real offenders, and a planted public class turns the gate red.
- Gate `exit 0`, three tools `wired`, testcases = baseline + 1; no assertion changed; no rule weakened; no baseline/suppression added.
- Koin module vals still public and resolve (module tests green); no behavior diff in non-visibility lines (`git diff` skim: only `internal`, the screen split and the rule).
