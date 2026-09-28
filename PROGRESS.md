# Progress Log

## Current Verified State

- Repository root: `C:/Users/Emmanuel/AndroidStudioProjects/BBBluesJam`
- Standard startup path: `./init.sh`
- Standard verification path: `CI=true ./init.sh`, which wraps `./gradlew build` and
  `./gradlew check`
- Current next ready feature: `detekt-ktlint-gate` (also ready: `molecule-presenter-harness`,
  `design-tokens-theme`, `domain-model-types`). Accepted: `gradle-kotlin-compose-baseline`,
  `module-skeleton`, `konsist-isolation-rules`.
- Current blocker: none. `sheet-schema-definition` was unblocked on 28 September 2026; it closes
  once the seed is imported into the real Sheet and read back by hand.
- Last verified at: 28 September 2026 — `CI=true ./init.sh` exit 0 with `:konsist-test` added;
  it prints `konsist: wired` (8 Konsist tests, 0 failures); detekt and ktlint still `NOT WIRED YET`.
  Last launch check on a Pixel 5 (API 34) was in session 011; this slice changed nothing visible.

### What exists

Four modules: `:app`, `:core:model`, `:core:ui` and `:core:data` (`module-skeleton`, `accepted`).
`:core:model` is a Kotlin JVM module with no Android; `:core:ui` and `:core:data` are Android
libraries (`com.android.library`, built-in Kotlin) that each `api`-depend on `:core:model`; `:app`
depends on all three. Each `:core` module holds only a placeholder marker object; `CoreModelMarkerTest`
and `:app`'s `ModuleWiringTest` prove they compile and are wired. `compileSdk` and `minSdk` come from
the catalog. Build conventions are recorded in `.claude/skills/architecture/SKILL.md`.

A fifth, test-only module `:konsist-test` (`kotlin-jvm`, no project dependency) holds
`ModuleIsolationTest`: 8 Konsist 0.17.3 tests that enforce the architecture skill's dependency rules
(no feature→feature or feature→`:app` imports, `:core:*` import allowlist, no Android in
`:core:model`, no ViewModel, package roots `com.bbbjam.<module path>` without hyphens, and allowed
`project(":…")` dependencies in `core/*`/`feature/*` build files). Module groups are read from paths,
so the first `:feature:*` module is covered without editing the suite. The test task declares every
`.kt`/`.kts` file as an input, so a change elsewhere reruns it (`konsist-isolation-rules`, `passing`).

`:app` is on a Kotlin + Jetpack Compose baseline (`gradle-kotlin-compose-baseline`,
`accepted`). AGP 9.4.1 with its built-in Kotlin (no `org.jetbrains.kotlin.android`
plugin), Kotlin/KGP 2.2.10, the Compose compiler plugin `org.jetbrains.kotlin.plugin.compose` on the
same `kotlin` catalog key, Compose BOM 2025.09.00 (ui, foundation, ui-tooling-preview, ui-tooling for
debug only) and activity-compose 1.11.0. appcompat and Material Views are gone, and there is no
Material 3 yet.

`MainActivity` (a `ComponentActivity`, the launcher) turns on edge-to-edge with dark system bars and
shows `PlaceholderScreen`: `#111318` full screen with a centered `BB Blues Jam` label in `#E2E2E9`,
drawn with foundation `BasicText`. Both colors live temporarily in `res/values/colors.xml`, also used
by the window theme `Theme.BBBluesJam` (parent `android:Theme.Material.NoActionBar`), until
`design-tokens-theme` replaces them. Dark only: `values-night` was deleted.

`./gradlew check` runs unit tests, lint and the Konsist suite; detekt and ktlint are not wired yet,
and `init.sh` reports them as missing rather than pretending they run. `init.sh` prints
`konsist: wired` only when `:konsist-test:test` exists and its results hold at least one test (with
the suite removed it prints `NOT WIRED YET`). The unit tests are the two template tests plus
`CoreModelMarkerTest` and `ModuleWiringTest`.

No product code has been written; the placeholder screen is scaffolding.

### Reachable without unblocking the Sheet schema

Eight slices, in plan order: `gradle-kotlin-compose-baseline`, `module-skeleton`,
`konsist-isolation-rules`, `detekt-ktlint-gate`, `molecule-presenter-harness`,
`design-tokens-theme`, `domain-model-types`, `info-screen`.

Everything from `apps-script-read-endpoint` onward waits on the Sheet schema.

## Session Log

### Session 001 — 19 September 2026

- Goal: consolidate closed discovery into durable documents.
- Completed: ran `build-brief`, producing `CONTEXT.md`, `docs/build-brief.md`,
  `docs/domain-model.md`, `docs/user-and-access-model.md`, `docs/technical-discovery.md`,
  `docs/risks-and-open-questions.md` and `DESIGN.md`. Updated the bitácora.
- Verification run: cross-reference check for dangling document links; no product code to verify.
- Evidence captured: seven documents committed in `2cfc5ac`.
- Known risk or unresolved issue: the Sheet schema is undefined and is the top blocker.
- Next best step: run `harness-starter`.

### Session 002 — 19 September 2026

- Goal: create the minimal startup harness.
- Completed: `AGENTS.md`, `init.sh`, `PROGRESS.md` and `feature_list.json` created.
- Verification run: `feature_list.json` validated as JSON; every `depends_on` resolves to an
  existing id, no self-references, no cycles, one `in_progress` at most. `bash -n init.sh` passed
  and the file is executable.
- Evidence captured: 34 features, 1 blocked, 8 reachable without the Sheet schema.
- Known risk or unresolved issue: `./init.sh` passes, but the gate is currently thin — `check`
  runs unit tests and lint only, and the three quality tools report as unwired. The baseline
  becomes meaningful once `konsist-isolation-rules` and `detekt-ktlint-gate` land.
- Next best step: implement `gradle-kotlin-compose-baseline`.

### Session 003 — 19 September 2026

- Goal: write the three Claude Code subagents.
- Completed: `.claude/agents/planner.md`, `implementer.md` and `validator.md`. Added a `CI=true`
  guard to `init.sh` so the verification command feature-flow expects never tries to start the app.
  Documented the feature status ladder, including `accepted`, in `AGENTS.md`.
- Verification run: `bash -n init.sh` and `CI=true ./init.sh`, both clean, exit 0. Frontmatter of
  the three agents checked for name, description, tools and model.
- Evidence captured: the validator holds read-only tools plus Bash, so it can rerun the gate but
  cannot quietly repair what it is meant to judge.
- Known risk or unresolved issue: the pipeline has not been exercised end to end yet. The first
  real test is running feature-flow on `gradle-kotlin-compose-baseline`.
- Next best step: run the flow on `gradle-kotlin-compose-baseline`, or settle the Sheet schema.

### Session 004 — 19 September 2026

- Goal: generate the screens in Stitch and reconcile `DESIGN.md` against the export.
- Completed: export unpacked under `docs/design/`; palette and typography in `DESIGN.md` replaced
  with measured values. Four tokens remain derived (`textMuted`, `slotFilled`, `archive`, `error`)
  and are marked as such.
- Verification run: token values counted against the HTML of all twelve exported screens; the
  export's own prose palette appears zero times in its code, so the front-matter values were used.
- Evidence captured: commits `2d23970`, `dfa4d6e`, `847c8d2`; bitácora section 6.5 lists the
  product facts Stitch invented and that were rejected.
- Known risk or unresolved issue: none new.
- Next best step: run the flow on `gradle-kotlin-compose-baseline`.

### Session 005 — 28 September 2026

- Goal: check the harness against the week 1 and week 2 course material and close the gaps.
- Completed:
  - `AGENTS.md` reordered in a U layout: role, non-negotiable rules and literal commands at the
    top; reference in the middle; Definition of Done and a reminder of the critical rules at the
    end. 106 → 124 lines, still under the ~150 ceiling.
  - New project skill `.claude/skills/architecture/`: module layout, where each piece goes,
    allowed dependencies, presenter pattern, feature template, anti-patterns. Referenced from
    `AGENTS.md` and from the three subagents.
  - Course PDFs kept out of git: `/docs/clases/` added to `.gitignore`.
  - `START-HERE.md` and the bitácora brought up to date.
- Verification run: `CI=true ./init.sh`, exit 0. The architecture skill is listed by Claude Code
  after creation.
- Evidence captured: this entry; bitácora section 6.6.
- Known risk or unresolved issue:
  - **The subagents could not invoke their skill.** None of the three listed `Skill` in its
    `tools:` frontmatter, yet each is told to invoke `feature-spec`, `feature-implementer` or
    `feature-validator`. Fixed by adding `Skill` to all three; still to be confirmed on the first
    planner run.
  - The architecture skill left three points open on purpose: the dependency injection
    framework, the navigation and deeplink scheme, and whether admin UI is its own module. The
    first and third were settled in session 006.
  - The reference presenter sample did not exist in the repo. Addressed in session 006.
- Next best step: run the planner on `gradle-kotlin-compose-baseline` and review the spec before
  implementing.

### Session 006 — 28 September 2026

- Goal: record the user's decisions on dependency injection and admin, and write the canonical
  presenter example.
- Completed:
  - **D-15, admin is a state, not a module.** The nine `feature-admin` slices moved to
    `feature-next-jam` (eight mutations) and `feature-info` (passphrase login). No dependency
    changed.
  - **D-16, Koin**, constructor injection only; presenters `factory`, repositories `single`, one
    Koin module per Gradle module, `startKoin` only in `:app`.
  - `.claude/skills/architecture/references/presenter-pattern.md`: contracts, a next-jam example
    with a child row presenter and admin state, the screen, Koin wiring, and Molecule tests with
    fakes. Adapted from Doximity's two articles, with a table of every deviation. Both bugs the
    bitácora records (`equals`/`hashCode` mismatch, undeclared `invoke`) are present in the
    articles themselves and are corrected here.
  - The architecture skill, `AGENTS.md`, `technical-discovery.md`, START-HERE and the bitácora
    updated for D-15 and D-16; decision range references moved to D-01 … D-16.
- Verification run: `feature_list.json` rewritten with its original 2-space indentation; the diff
  is exactly the nine `area` lines. The presenter example is **not compiled** — there is no Compose
  in the project yet.
- Evidence captured: this entry; bitácora sections D-15, D-16 and 6.7.
- Known risk or unresolved issue: the example may not compile as written. `molecule-presenter-harness`
  must turn it into real code with a green test, then update the reference to match the code.
- Next best step: run the planner on `gradle-kotlin-compose-baseline`.

### Session 007 — 28 September 2026

- Goal: settle the Sheet schema, the top blocker, and seed it with a real jam.
- Completed:
  - `docs/sheet-schema.md`: tabs `Catalogo`, `Jams`, one tab per jam named `YYYY-MM-DD`, and
    `Config`. The planned assignments tab folded into the jam tabs as one column per slot: empty is
    open, a name is filled, `-` is not in that song's lineup (D-06). Headers are Spanish because the
    admin edits the Sheet; code maps them by name.
  - `docs/sheet-seed/`: four CSVs built from `docs/ejemplo jam.jpeg`, the 25 July 2026 setlist at La
    Macanuda — thirteen songs with artist and key. Artists normalized (`B.B. King`,
    `Memphis La Blusera`), confirmed by the user.
  - `sheet-schema-definition` moved from `blocked` to `not_started`.
  - Two open questions closed by the schema: catalog-deleted songs fall back to the jam tab's
    title/artist copies, and musician suggestions come from past jam tabs.
  - `Jam` gains `startTime` in the domain model; the poster and the next-jam header both need it.
- Verification run: seed CSVs regenerated and inspected; `feature_list.json` diff limited to the
  one slice. The Sheet itself was not written: it is private and no connector was authenticated.
- Evidence captured: this entry; bitácora section 6.8.
- Known risk or unresolved issue:
  - The seed is not in the real Sheet yet. The user imports it by hand, or a later session does it
    with the Chrome extension or the Drive connector.
  - `tono_default` in the seed is that night's key, a stand-in. Tempo, tags and difficulty are empty
    and are the admin's to fill; the week 5 assistant depends on them.
- Next best step: run the planner on `gradle-kotlin-compose-baseline`; import the seed in parallel.

### Session 008 — 28 September 2026

- Goal: first real run of the pipeline — plan `gradle-kotlin-compose-baseline`.
- Completed:
  - The planner wrote `docs/specs/gradle-kotlin-compose-baseline.md` (Compose in `:app` only,
    Kotlin 2.2.10 matching AGP 9.4.1's built-in Kotlin, Compose BOM 2025.09.00, activity-compose
    1.11.0, a launcher `MainActivity`, and a `#111318` placeholder screen).
  - The user reports the seed CSVs are imported into the real Sheet. Not verified from this session:
    the Sheet is private to it.
- Verification run: orchestrator review of the spec against the repo — no Kotlin plugin applied, no
  activity in the manifest, and hex values matching `DESIGN.md`, all confirmed. `adb devices` lists
  no device; three AVDs exist (`Pixel_2_API_29`, `Pixel_2_API_30b`, `Pixel_6a_API_34`).
- Evidence captured: the planner loaded `feature-spec` through the Skill tool, which confirms the
  session 005 fix of adding `Skill` to the subagents' tools.
- Known risk or unresolved issue:
  - `design-tokens-theme` depended only on this slice, but the architecture skill puts tokens in
    `:core:ui`, which `module-skeleton` creates. Fixed with user approval: `module-skeleton` added
    to its `depends_on` (no cycle: `module-skeleton` depends only on this slice).
  - Manual launch check: the user connected a Pixel 5 (`adb devices` shows it); the implementer
    runs the launch scenario on it.
  - The course skill `feature-spec` points to a reference spec, `docs/specs/bootstrap-nextjs-shell.md`,
    that does not exist here. Harmless; noted.
- Next best step: spec approved by the user; run the implementer.

### Session 009 — 28 September 2026

- Goal: implement `gradle-kotlin-compose-baseline` (implementer subagent).
- Completed:
  - Catalog: added `kotlin` 2.2.10, `composeBom` 2025.09.00, `activityCompose` 1.11.0, the Compose
    libraries and the `kotlin-compose` plugin (`version.ref = "kotlin"`); removed appcompat and
    material. Root build declares the plugin `apply false`; `:app` applies it, sets
    `buildFeatures.compose = true` and swaps the dependencies.
  - `MainActivity` and `PlaceholderScreen` (with `@Preview`), launcher entry in the manifest,
    colors/strings/theme reworked, `values-night` deleted.
  - No version bump was needed.
- Verification run:
  - Before: `CI=true ./init.sh` exit 0.
  - After: `./gradlew :app:assembleDebug` successful; `CI=true ./init.sh` exit 0, the three tools
    `NOT WIRED YET`. Lint: 4 warnings, all newer-version notices.
  - `grep` for `appcompat|com.google.android.material` in the catalog and `app/build.gradle.kts`:
    no matches. `grep` for `MaterialComponents|AppCompat` in `app/src/main`: no matches.
    `values-night` and `res/layout` absent.
  - `./gradlew buildEnvironment`: `kotlin-gradle-plugin:2.2.10` and
    `compose-compiler-gradle-plugin:2.2.10`, neither resolved to another version.
  - Pixel 5, Android 14 (API 34), system light mode, 3-button nav, held in landscape: `installDebug`
    exit 0; cold `am start -W` Status ok, 694 ms; `logcat -s AndroidRuntime` empty (cleared first).
    The screenshot shows `#111318` edge to edge (pixel-sampled, including under both system bars),
    the centered label (sampled `#E2E2E8`, one step off `#E2E2E9`, capture rounding), no action bar,
    light system bar icons. Two screencaps right after `am start` caught the window transition with
    the surface already dark. No white frame was caught, but screencap only samples frames.
- Evidence captured: `feature_list.json` entry. Screenshots kept out of the repo.
- Deviation from the spec: the spec calls `enableEdgeToEdge()` with no arguments. On the light-mode
  device that drew dark status icons (nearly invisible) and a light grey navigation-bar scrim over
  the dark screen, contradicting "dark `#111318` edge to edge". `MainActivity` now passes
  `SystemBarStyle.dark(Color.TRANSPARENT)` for both bars, called before `super.onCreate`. This is
  permanent behavior for a dark-only app, not placeholder-only.
- Known risk or unresolved issue:
  - The `@Preview` carries `backgroundColor = 0xFF111318` as the spec requires, so that hex appears
    twice (once in `colors.xml`), against the validator checklist's "appear once". The annotation
    needs a compile-time constant, so it cannot read the resource. Goes away with
    `design-tokens-theme`.
  - Portrait was also checked (rotation locked to 0 for the shot, then restored): same result,
    AndroidRuntime empty. Dark system mode was not checked.
  - Layout Inspector (optional in the spec) was not run.
- Next best step: run the validator on `gradle-kotlin-compose-baseline`.

### Session 010 — 28 September 2026

- Goal: independent validation of `gradle-kotlin-compose-baseline`.
- Completed: validator verdict **accept**; status set to `accepted` by the orchestrator. This is the
  first feature to go through the full planner → implementer → validator pipeline.
- Verification run (by the validator, not the implementer): `CI=true ./init.sh` exit 0; the spec's
  grep checks clean; `buildEnvironment` shows KGP and the Compose compiler plugin both at 2.2.10;
  reinstall and cold start on the Pixel 5 in 636 ms with an empty AndroidRuntime logcat; dark
  surface edge to edge with light system-bar icons, confirmed by eye.
- Rulings recorded in the spec's "Accepted Deviations": the `SystemBarStyle.dark` edge-to-edge
  call is justified and in scope; the `@Preview` hex literal is a spec inconsistency.
- Known risk or unresolved issue: `design-tokens-theme` inherits the cleanup of the temporary
  colors and the preview literal (added to its notes). The gate is still unit tests and lint only.
- Next best step: plan `module-skeleton`, the next dependency-ready slice.

### Session 011 — 28 September 2026

- Goal: implement `module-skeleton` (implementer subagent).
- Completed:
  - Catalog: `compileSdk = "37"`, `minSdk = "24"` versions; `android-library` (agp) and `kotlin-jvm`
    (kotlin) plugins, both declared at the root `apply false`. `settings.gradle.kts` includes
    `:core:model`, `:core:ui`, `:core:data`.
  - `:core:model` on `kotlin-jvm` with Java/JVM target 11 and JUnit; `:core:ui` and `:core:data` on
    `android-library` with namespaces `com.bbbjam.core.ui`/`.data`, catalog SDK levels, Java 11,
    `api(project(":core:model"))`. Sources in `src/main/kotlin`; one `/build` `.gitignore` each.
  - Marker objects `CoreModelMarker`, `CoreUiMarker`, `CoreDataMarker`; `CoreModelMarkerTest`;
    `:app` reads SDK levels from the catalog, depends on all three, and has `ModuleWiringTest`.
  - Architecture skill: new "Build Conventions" section.
- Verification run:
  - Before: `CI=true ./init.sh` exit 0. After: `./gradlew build --rerun-tasks` successful (263
    tasks, no compiler warnings), `CI=true ./init.sh` exit 0, the three tools `NOT WIRED YET`. Both
    new tests: 1 test, 0 failures.
  - Dependency reports match Scenario 3: `:core:model` only `kotlin-stdlib`; `:core:ui`/`:core:data`
    exactly one `project ':core:model'`; `:app` all three. `buildEnvironment`: library plugin 9.4.1,
    KGP 2.2.10 only. No `org.jetbrains.kotlin.android` anywhere.
  - Negative check: `import android.content.Context` in `CoreModelMarker.kt` made
    `:core:model:compileKotlin` fail with `Unresolved reference 'android'` (exit 1); reverted, compiles
    again.
  - Pixel 5, API 34: `installDebug` exit 0, cold `am start -W` Status ok 775 ms, AndroidRuntime
    logcat empty; screenshot unchanged from the baseline (dark surface, centered label), by eye only.
- Evidence captured: `feature_list.json` entry. Screenshot kept in the scratchpad, not the repo.
- Deviation from the spec: none.
- Known risk or unresolved issue:
  - The dependency direction is checked only by hand-read Gradle reports until Konsist lands.
  - The marker objects and `ModuleWiringTest` lines are scaffolding; the slices named in their KDoc
    must delete them.
  - Screenshot colors were not pixel-sampled this time (no image library in the shell).
- Next best step: run the validator on `module-skeleton`.

### Session 012 — 28 September 2026

- Goal: independent validation of `module-skeleton`.
- Completed: validator verdict **accept**; status set to `accepted` by the orchestrator.
- Verification run (by the validator): `CI=true ./init.sh` exit 0; the new tests rerun with
  `--rerun` and passing; dependency reports matching the architecture skill; one KGP version; the
  negative check reproduced independently and the file restored byte-identical (SHA-1); Pixel 5 cold
  start in 686 ms, empty AndroidRuntime logcat, background pixels sampled at `#111318`.
- Known risk or unresolved issue: dependency direction is still checked by reading Gradle reports
  until Konsist lands. The validator did not rerun `./gradlew build --rerun-tasks`, so the
  implementer's "no compiler warnings" claim stands on the implementer's run alone.
- Next best step: plan `konsist-isolation-rules`.

### Session 013 — 28 September 2026

- Goal: implement `konsist-isolation-rules` (implementer subagent), spec
  `docs/specs/konsist-isolation-rules.md`.
- Completed:
  - Catalog `konsist = "0.17.3"` and library entry; `include(":konsist-test")`; module
    `konsist-test/` (`kotlin-jvm`, Java 11, `/build` `.gitignore`, JUnit + Konsist, no project
    dependency). Its `tasks.test` declares the project's `.kt`/`.kts` files as inputs and passes
    `bbbjam.rootDir`.
  - `ModuleIsolationTest` with the 8 specified tests; Windows `\` module separators normalised; no
    hard-coded module list.
  - `init.sh` changed exactly as the spec's Decision 4 (approved by the user): a `konsist_wired`
    check replaces the task-name grep for Konsist; detekt/ktlint detection untouched.
  - Architecture skill (`:konsist-test` row, enforcement note, package-root convention) and
    `docs/technical-discovery.md` (one line) updated.
- Verification run (full evidence in `feature_list.json`):
  - Before: `CI=true ./init.sh` exit 0, three tools `NOT WIRED YET`. After: exit 0, `konsist: wired`,
    `tests="8" failures="0" errors="0"`.
  - Scenario 5: `:konsist-test:test` UP-TO-DATE, then executed after a one-line touch in
    `CoreModelMarker.kt`.
  - Scenarios 2-3 with temporary `:feature:probe-a`/`probe-b`: clean probes green; a cross-feature
    dependency plus import made `CI=true ./init.sh` exit 1 with `feature-imports-feature` and
    `build-file-project-deps` failing (`ProbeA.kt:4:1`, `feature/probe-a/build.gradle.kts:23`); a fully
    qualified reference without import failed only the build-file test.
  - Scenario 4: each remaining violation (scope sanity, package root, feature→app, core allowlist,
    Android in `:core:model`, ViewModel import, ViewModel subclass, core build file) failed exactly
    its own test and passed again after the revert.
  - init.sh negative control: suite file moved out → `konsist: NOT WIRED YET`; restored → `wired`.
  - Restore: SHA-1s of every touched file match; `feature/` gone; `git status` shows only the
    slice's files. Logs kept in the scratchpad, not the repo.
- Deviation from the spec: none in behavior. Interpretation: Decision 4 says to replace "the two
  comment lines" about "unit tests and lint only"; only one line says that, so only it was replaced.
  The next two comment lines (tools "picked up here automatically, with no change to this script")
  are now stale for Konsist and were left for the orchestrator/user to reword.
- Known risk or unresolved issue:
  - Konsist 0.17.3 (Dec 2024) parses with `kotlin-compiler-embeddable` 2.0.21 while the project
    compiles with 2.2.10; syntax newer than 2.0 could fail to parse. JDK 25 prints `sun.misc.Unsafe`
    warnings from that jar (noise).
  - Rules over `feature/*` are vacuous until the first feature module lands (Konsist passes on empty
    lists); the probes are the only proof so far.
  - Konsist sees imports, not resolved references; the build-file rule covers fully qualified use.
  - Konsist failure lines print a `???` glyph in this Windows console (encoding); the message is intact.
- Next best step: run the validator on `konsist-isolation-rules`.

### Session 014 — 28 September 2026

- Goal: independent validation of `konsist-isolation-rules`.
- Completed: validator verdict **accept**; status set to `accepted` by the orchestrator.
- Verification run (by the validator): the `init.sh` diff matches Decision 4 exactly; the gate is
  green with `konsist: wired` and 8/8 tests; the up-to-date trap fix, the probe-module cross-feature
  failure (`init.sh` exit 1), the fully qualified variant, the `init.sh` honesty check, and four
  extra violations of the validator's own choosing were reproduced; SHA-1 of all 139 non-ignored
  files identical before and after.
- Also: `docs/technical-discovery.md` still said the Sheet schema was undefined — a miss from
  session 007, corrected now to point at `docs/sheet-schema.md`.
- Known risk or unresolved issue:
  - `init.sh` lines 23–24 said tools are picked up "with no change to this script", false for
    Konsist (a spec inconsistency). Reworded with the user's approval, comment only; `bash -n` and
    `CI=true ./init.sh` (exit 0, konsist wired) rerun afterwards.
  - Keep one `include(":x")` per line in `settings.gradle.kts`: the scope-sanity guard parses only
    single-argument includes.
  - Konsist 0.17.3 parses with Kotlin 2.0.21 while the project compiles with 2.2.10.
- Next best step: plan `detekt-ktlint-gate`.

## Notes For The Next Session

- The Sheet schema is settled (`docs/sheet-schema.md`). What a human still owns, in parallel with
  coding: **importing the seed** and **loading real repertoire** with tempo, tags and difficulty.
  Week 5 needs a catalog large enough for the assistant to produce an interesting themed setlist.
- Before `action-contract-registry` runs, confirm the mutation list is complete. The current list is
  add song, remove song, set key, adjust lineup, assign musician, clear slot, reorder, publish
  (D-13).
- Capture course evidence as it appears rather than reconstructing it later: the Konsist output, the
  feature-module diff when the assistant lands, and a timed recording of the manual flow before the
  app replaces it.
