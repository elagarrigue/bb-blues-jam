# Progress Log

## Current Verified State

- Repository root: `C:/Users/Emmanuel/AndroidStudioProjects/BBBluesJam`
- Standard startup path: `./init.sh`
- Standard verification path: `CI=true ./init.sh`, which wraps `./gradlew build` and
  `./gradlew check`
- Current next ready features: `sheet-schema-definition` (needs the user's hand check of the real
  Sheet) and `info-screen`. Accepted: `gradle-kotlin-compose-baseline`, `module-skeleton`,
  `konsist-isolation-rules`, `detekt-ktlint-gate`, `molecule-presenter-harness`,
  `design-tokens-theme`, `domain-model-types`.
- Current blocker: none. `sheet-schema-definition` was unblocked on 28 September 2026; it closes
  once the seed is imported into the real Sheet and read back by hand.
- Last verified at: 29 September 2026 (session 024) — `CI=true ./init.sh` exit 0; it prints
  `konsist: wired` (10 Konsist tests, 0 failures), `detekt: wired`, `ktlint: wired`; `:core:model`
  runs 30 tests in 9 classes on the JVM, 0 failures; `:core:ui` runs `EventHandlerTest` (6),
  `SamplePresenterTest` (3), `BluesJamColorsTest` (3), `BluesJamTypographyTest` (2),
  `ContrastTest` (6) and `WindowBackgroundTest` (1), 0 failures. Last launch check on the Pixel 5
  (API 34) was session 021: cold start, empty AndroidRuntime logcat, background `#111318`, label
  Barlow Condensed Bold. The display runs in Display P3, so saturated colors in screenshots read
  as their P3 encoding (amber `#FFB300` → `#F4B63F`). Session 024 changed nothing visible.

### What exists

Four modules: `:app`, `:core:model`, `:core:ui` and `:core:data` (`module-skeleton`, `accepted`).
`:core:model` is a Kotlin JVM module with no Android; `:core:ui` and `:core:data` are Android
libraries (`com.android.library`, built-in Kotlin) that each `api`-depend on `:core:model`; `:app`
depends on all three. `:core:data` still holds only a placeholder marker object; `:app`'s
`ModuleWiringTest` proves `:core:data` and `:core:model` are visible from `:app` (the latter through
`JamStatus`).

`:core:model` holds the domain types in `com.bbbjam.core.model` (`domain-model-types`, `accepted`):
`Jam` (date as identity, setlist positions exactly 1..n in order, `isHistorical(today)` with the
date from the caller), `JamStatus` (`DRAFT`, `PUBLISHED` only), `JamSong` (position ≥ 1, `songId`,
`title`, `artist`, `key`, `lineup`, `extraParticipants` default empty), `Lineup` (`openSlots`,
`hasOpenSlotFor`, `default()` = seven open slots in Sheet column order; never more of an instrument
than the default, zero allowed — D-18), `Slot` (open when `musicianName` is null; blank names
rejected), `ExtraParticipant` (name and free-text instrument, both non-blank, no `;`, `(`, `)`;
never open), `Instrument` (six values, `KEYBOARDS` not `KEYS`), `Song` (optional catalog and
enrichment fields default to null), `Tempo`, `Difficulty`, and the validated value classes `Key`
(schema **Keys** format, `isMinor`, `parseOrNull`) and `SongId` (lowercase slug, `parseOrNull`).
Pure Kotlin with java.time; no serialization, no date parsing, no clock (Konsist
`core-model-no-system-clock`). Invalid values throw `IllegalArgumentException` naming the value.

`:core:ui` holds the presenter contracts of D-02 in `com.bbbjam.core.ui.presenter`: `UiModel`,
`UiEvent`, `Presenter` and `EventHandler` (`equals` and `hashCode` both from `key`, `operator invoke`
declared) (`molecule-presenter-harness`, `accepted`). It applies the Compose compiler plugin and
exposes the Compose BOM, `androidx.compose.runtime:runtime`, `ui` and `material3` (1.3.2) as `api`.

`:core:ui` also holds the design system in `com.bbbjam.core.ui.theme` (`design-tokens-theme`,
`passing`; D-17): `BluesJamTheme { }` wraps `MaterialTheme` with every dark-scheme role, the type
scale and the shapes mapped from the `DESIGN.md` tokens, and provides `LocalContentColor` = `text`.
Screens read `BluesJamTheme.colors` / `.typography` / `.shapes` / `.spacing`. The palette is
`internal`; amber is public only as `primaryAction`, `slotOpen`, `key`, `published`,
`activeFilter` (plus their `on…` colors). Fonts are bundled static TTFs (Barlow Condensed
SemiBold/Bold/ExtraBold, Chivo Regular) with their OFL texts in `assets/licenses`. The only XML
color is `bluesjam_window_background` in `core/ui/src/main/res/values/colors.xml`, guarded by
`WindowBackgroundTest`. `ThemeShowcase` (public, with a `@Preview`) draws every role and style.
Its test sources hold a sample presenter with no domain types, `SamplePresenterTest` (Molecule
2.2.0 + Turbine 1.2.1 + coroutines-test 1.10.2, asserting transitions after events) and
`EventHandlerTest`. Unit tests set `isReturnDefaultValues = true` because the Android Compose
runtime calls `android.os.Trace`; every presenter module needs the same (architecture skill).
`compileSdk` and `minSdk` come from the catalog. Build conventions are recorded in `.claude/skills/architecture/SKILL.md`.

A fifth, test-only module `:konsist-test` (`kotlin-jvm`, no project dependency) holds
`ModuleIsolationTest`: 10 Konsist 0.17.3 tests that enforce the architecture skill's dependency rules
(no feature→feature or feature→`:app` imports, `:core:*` import allowlist, no Android in
`:core:model`, no system clock in `:core:model`, no ViewModel, package roots `com.bbbjam.<module path>` without hyphens, allowed
`project(":…")` dependencies in `core/*`/`feature/*` build files, and no color literal outside
`:core:ui`). Module groups are read from paths,
so the first `:feature:*` module is covered without editing the suite. The test task declares every
`.kt`/`.kts` file as an input, so a change elsewhere reruns it (`konsist-isolation-rules`, `accepted`).

`:app` is on a Kotlin + Jetpack Compose baseline (`gradle-kotlin-compose-baseline`,
`accepted`). AGP 9.4.1 with its built-in Kotlin (no `org.jetbrains.kotlin.android`
plugin), Kotlin/KGP 2.2.10, the Compose compiler plugin `org.jetbrains.kotlin.plugin.compose` on the
same `kotlin` catalog key, Compose BOM 2025.09.00 (ui, foundation, ui-tooling-preview, ui-tooling for
debug only) and activity-compose 1.11.0. appcompat and Material Views are gone; Material 3 arrives
through `:core:ui`.

`MainActivity` (a `ComponentActivity`, the launcher) turns on edge-to-edge with dark system bars and
shows `PlaceholderScreen` inside `BluesJamTheme`: the background token full screen with a centered
`BB Blues Jam` label in the `h1` style (Barlow Condensed Bold 28sp), drawn with an M3 `Text` that
inherits the `text` color. `:app` has no colors of its own: its `colors.xml` is gone, and the window
theme `Theme.BBBluesJam` (parent `android:Theme.Material.NoActionBar`) uses
`@color/bluesjam_window_background` from `:core:ui`. Dark only: `values-night` was deleted.

`./gradlew check` runs unit tests, Android lint, the Konsist suite, detekt 2.0.0-alpha.6 and ktlint
1.8.0 (ktlint-gradle 14.2.0). The root `build.gradle.kts` applies both tools to every module that
applies `kotlin-jvm` or an Android plugin, so a new module gets them with no build code; config is in
`.editorconfig` (`android_studio` style, Composable naming exception) and `config/detekt/detekt.yml`;
no baseline file. `init.sh` prints `konsist: wired` only when `:konsist-test:test` exists and its
results hold at least one test, and `detekt`/`ktlint: wired` only when `check --dry-run` schedules
the tool's task in every module that compiles Kotlin; otherwise it names the modules missing it.
The unit tests are the two template tests plus the nine `:core:model` classes (`ExtraParticipantTest`,
`JamSongTest`, `JamStatusTest`, `JamTest`, `KeyTest`, `LineupTest`, `SlotTest`, `SongIdTest`,
`SongTest`), `ModuleWiringTest`, `EventHandlerTest`, `SamplePresenterTest`, `BluesJamColorsTest`, `BluesJamTypographyTest`,
`ContrastTest` and `WindowBackgroundTest`.

The only product code is the `:core:model` domain types; the placeholder screen is scaffolding.

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

### Session 015 — 28 September 2026

- Goal: plan `detekt-ktlint-gate`.
- Completed: `docs/specs/detekt-ktlint-gate.md`, prototyped on a throwaway clone. ktlint via
  ktlint-gradle 14.2.0 (ktlint 1.8.0, `android_studio` style); detekt applied from a root
  `subprojects {}` block to every Kotlin module; Compose naming exceptions in `.editorconfig` and
  `config/detekt/detekt.yml`; today's tree passes with no baseline after config plus small fixes.
- Finding: **no stable detekt runs here.** detekt 1.23.8 fails three ways on the Java 25 daemon
  (invalid `--jvm-target`, its Kotlin 2.0.21 compiler cannot parse `25.0.2`, and forcing Kotlin
  2.2.10 is rejected). detekt 2.0.0-alpha.6 works, including with the configuration cache.
- Decisions by the user: the spec's `init.sh` change is **approved** (a tool counts as wired only
  when its task is scheduled in every Kotlin module); **detekt 2.0.0-alpha.6 approved** over
  lowering the daemon JVM pin to 21.
- Next best step: the implementer runs the spec.

### Session 016 — 28 September 2026

- Goal: implement `detekt-ktlint-gate` (spec `docs/specs/detekt-ktlint-gate.md`).
- Completed: catalog entries (detekt 2.0.0-alpha.6, ktlint-gradle 14.2.0, ktlint 1.8.0); root
  `build.gradle.kts` quality block (Decision 4); `.editorconfig` and `config/detekt/detekt.yml`
  (Decision 2); explicit `assertEquals` import in both template tests, then `./gradlew ktlintFormat`
  (import order, final newlines, one joined signature in `ModuleIsolationTest`), formatting only;
  `init.sh` detection replaced exactly as the user-approved Decision 5; architecture skill Build
  Conventions and `docs/technical-discovery.md` §Testing updated.
- Verification run:
  - Baseline `CI=true ./init.sh` exit 0 (detekt/ktlint NOT WIRED YET); warm 7.41 / 6.97 / 7.16 s.
  - `./gradlew check --warning-mode all --rerun-tasks` exit 0, no Gradle deprecation warning;
    `detekt` and `ktlint*SourceSetCheck` ran in all five Kotlin modules.
  - After: `CI=true ./init.sh` exit 0, `konsist: wired`, `detekt: wired`, `ktlint: wired`; warm
    6.21 / 6.22 / 6.09 s (about 1 s faster: one `check --dry-run` replaced two `tasks --all`).
  - ktlint probe (`PATH:String   =   ...` in `CoreModelMarker.kt`): only
    `:core:model:ktlintMainSourceSetCheck` failed (`8:20 Missing spacing after ":"`);
    `CI=true ./init.sh` exit 1.
  - detekt probe (empty `if` appended): only `:core:model:detekt` failed
    (`CoreModelMarker.kt:12:20 ... [EmptyIfBlock]`); `CI=true ./init.sh` exit 1.
  - Each probe restored by copy, SHA-1 `bb307eb9…` matched, `CI=true ./init.sh` exit 0.
  - init.sh negative control: without the `com.android.base` hook it printed `NOT WIRED YET
    (missing from check in: :app :core:data :core:ui)` for both tools; with the HEAD root build file
    it named all five modules; restored (SHA-1 `2eebddfb…` match), three `wired` again.
- Evidence captured: `feature_list.json` entry, status `passing` (awaiting the validator).
- Known risk or unresolved issue:
  - detekt is an alpha (user-approved); no stable detekt runs on the Java 25 daemon. Revisit when
    detekt 2.0 ships.
  - ktlint-gradle keeps stale errors when a new file with violations is added and then deleted; the
    gate stays red until `./gradlew ktlintCheck --rerun-tasks`. Probe by modifying existing files.
  - `.claude/agents/*.md` still describe detekt/ktlint as "not wired until their slices land"; left
    for the orchestrator/user as the spec says.
  - Root build scripts are not linted; the cross-project `subprojects {}` block is incompatible with
    Gradle Isolated Projects (not enabled).
- Next best step: validate `detekt-ktlint-gate`.

### Session 017 — 28 September 2026

- Goal: independent validation of `detekt-ktlint-gate`.
- Completed: validator verdict **accept**; status set to `accepted`. The three subagent files no
  longer say the tools are unwired: the gate is complete, a `NOT WIRED YET` line is now a defect,
  and baselines, `ignoreFailures` or rule disables need the user's approval.
- Verification run (by the validator): `CI=true ./init.sh` exit 0 with three `wired`;
  `check --rerun-tasks` runs detekt and ktlint in all five Kotlin modules; its own probes — ktlint in
  `:core:data`, detekt in `:core:ui`, the missing-hook NOT WIRED case, and a non-Composable
  PascalCase function flagged by both tools — each failed as expected and were restored by SHA-1.
- Known risk or unresolved issue:
  - `init.sh` can exit 1 with no message if `./gradlew check --dry-run` prints nothing (e.g. a
    Gradle startup flake): under `set -euo pipefail` the empty grep aborts the script. It fails
    closed, never falsely green. The fix (`|| true` on the grep in `modules_running`) changes
    approved text, so it waits for the user.
  - detekt is an alpha; the ktlint-gradle stale-results quirk (see session 016).
- Next best step: plan `molecule-presenter-harness`.

### Session 018 — 28 September 2026

- Goal: implement `molecule-presenter-harness` (spec `docs/specs/molecule-presenter-harness.md`).
- Completed: catalog entries (molecule 2.2.0, turbine 1.2.1, coroutines-test 1.10.2, BOM-managed
  compose runtime); `core/ui/build.gradle.kts` exactly as Decision 2; the four contracts in
  `core/ui/src/main/kotlin/com/bbbjam/core/ui/presenter/`; `SamplePresenter`,
  `SamplePresenterTest` and `EventHandlerTest` in `core/ui/src/test`; `CoreUiMarker` deleted and
  removed from `ModuleWiringTest`; `presenter-pattern.md` (contracts verbatim, module setup,
  compiled example, next-jam labelled illustrative), architecture `SKILL.md` and
  `docs/technical-discovery.md` updated.
- Verification run:
  - Baseline `CI=true ./init.sh` exit 0, three `wired`.
  - `./gradlew ktlintFormat` exit 0; `./gradlew check --continue` exit 0; 9/9 `:core:ui` tests.
  - Trace probe (`isReturnDefaultValues = false`): 3/3 `SamplePresenterTest` fail with
    `Method beginSection in android.os.Trace not mocked`; restored (SHA-1 `fb191622…` match).
  - Scenario 2 (no-op toggle): `toggle expanded event changes the state` and `local state survives
    a new value from the source` fail, `No value produced in 3s`; restored (SHA-1 `2d82f6e4…`).
  - Scenario 3 (`hashCode` from `handle`): the unkeyed-hash, same-key-hash and hash-set tests fail
    (`expected:<3> but was:<5>` for the set); restored (SHA-1 `414efa5f…`).
  - Scenario 4 (no `invoke`): `compileDebugUnitTestKotlin` fails, `Expression 'events' … cannot be
    invoked as a function`; restored (SHA-1 `414efa5f…`), green.
  - `:core:ui` compile + lint with `--warning-mode all --rerun-tasks`: no warning, lint no issues.
  - Final `CI=true ./init.sh` exit 0, `konsist: wired` (8/8), `detekt: wired`, `ktlint: wired`;
    warm 6.41 / 6.19 s. No device used or needed.
- Evidence captured: `feature_list.json` entry, status `passing` (awaiting the validator).
- Known risk or unresolved issue:
  - `isReturnDefaultValues` makes any stubbed Android call in a presenter unit test return a default
    instead of throwing; presenters must not call Android (Decision 4).
  - The unit-test classpath of `:core:ui` resolves kotlin-stdlib 2.2.20 (Molecule's requirement)
    while the compiler is 2.2.10; main classpaths are unchanged.
  - Carried over: detekt alpha, ktlint-gradle stale-results quirk, `init.sh` empty-grep exit.
- Next best step: validate `molecule-presenter-harness`.

### Session 019 — 28 September 2026

- Goal: independent validation of `molecule-presenter-harness`.
- Completed: validator verdict **accept**; status set to `accepted`. The `CoreUiMarker.kt`
  deletion, left unstaged by the implementer after an accidental `git rm` and reset, is staged with
  this commit.
- Verification run (by the validator): gate exit 0 with three `wired`; `:core:ui` tests 9/9 and
  Konsist 8/8; its own negative controls — presenter ignoring new source values, toggle not
  flipping, `equals` ignoring the key, a no-op `invoke`, and `isReturnDefaultValues = false` — each
  failed as expected and were restored by SHA-1; the four contract blocks in `presenter-pattern.md`
  match the source files verbatim.
- Known risk or unresolved issue: `isReturnDefaultValues` makes a stubbed Android call in a
  presenter unit test return a default instead of failing; `:core:ui`'s unit-test classpath
  resolves kotlin-stdlib 2.2.20 against the 2.2.10 compiler (no warnings).
- Next best step: plan `design-tokens-theme`.

### Session 020 — 28 September 2026

- Goal: plan `design-tokens-theme`.
- Completed: `docs/specs/design-tokens-theme.md`, prototyped on a throwaway clone and on the Pixel 5.
  Material 3 underneath with plain token objects on top in `com.bbbjam.core.ui.theme`; amber exposed
  only through semantic roles; bundled fonts (Barlow Condensed SemiBold/Bold/ExtraBold, Chivo
  Regular) because the app is used offline; one XML color left for the pre-Compose window, guarded
  by a drift test; WCAG contrast measured (amber on background 10.35, textMuted on surface 10.10,
  onPrimary on primary 9.52 — all AA and AAA).
- Also: `docs/design/README.md` and bitácora §6.5 still quoted `#0C0E13`/`#282A30` from a first
  reconciliation; corrected to the values `DESIGN.md` already had.
- Decisions by the user: fonts **approved** (fetch and commit the pinned OFL TTFs and licences);
  the Konsist rule **approved**; the theme approach **recorded as D-17** (bitácora, START-HERE,
  AGENTS.md, subagents' decision range).
- Finding for later device checks: the Pixel 5 display runs in Display P3, so screenshots return
  P3-encoded values (amber `#FFB300` reads `#F4B63F`); the spec lists the expected P3 values.
- Next best step: the implementer runs the spec.

### Session 021 — 28 September 2026

- Goal: implement `design-tokens-theme` (spec `docs/specs/design-tokens-theme.md`).
- Completed: token sources in `core/ui/src/main/kotlin/com/bbbjam/core/ui/theme/`
  (`BluesJamPalette`, `BluesJamColors`, `BluesJamTypography` with `BluesJamFonts`, `BluesJamDimens`,
  `BluesJamTheme` with `BluesJamMaterial`, `ThemeShowcase`); the four OFL fonts and two licence
  texts fetched from the pinned commits, all six SHA-256 matching the spec;
  `bluesjam_window_background` in `:core:ui`; `material3` in the catalog and `ui`/`material3` as
  `api` in `:core:ui`; `:app` draws the placeholder from the tokens, and its `colors.xml` and the
  preview literal are gone; the Konsist rule `no-color-literal-outside-core-ui` (user-approved);
  architecture skill and `docs/technical-discovery.md` updated.
- Verification run: `CI=true ./init.sh` baseline exit 0; after `./gradlew ktlintFormat`, exit 0
  with three `wired` lines; `:core:ui` 21 tests, `ModuleIsolationTest` 9, 0 failures; detekt 0
  findings; `:core:ui` lint clean. Negative probes, each restored with a matching SHA-1: an extra
  amber role, a missing `secondary` (M3 baseline `#CCC2DC` leaks), a drifted window color, a
  low-contrast `textMuted` (2.15 and 2.33) and two color literals in `PlaceholderScreen.kt` (Konsist
  and `init.sh` exit 1) each failed the named test. Pixel 5, landscape, Display P3: cold start
  530 ms, empty AndroidRuntime log, background `#111318`, label `#E2E2E8`. In the `ThemeShowcase`
  probe every swatch was within 1 per channel of its token converted to P3, and the three Barlow
  weights and Chivo rendered distinctly. `MainActivity` was restored (SHA-1 OK) and reinstalled.
- Evidence captured: `feature_list.json` → `design-tokens-theme` (status `passing`); logs and
  screenshots in the session scratchpad only.
- Known risk or unresolved issue: `core.autocrlf=true` and no `.gitattributes`. The licence texts
  are stored as LF blobs that hash-match upstream, but a fresh Windows checkout writes CRLF working
  copies with different SHA-256 values (TTFs are binary and unaffected). The Konsist rule is
  textual: it misses `Color.Red`, `Color.parseColor` and XML colors, and would flag an ARGB hex in
  a comment. `ThemeShowcase` ignores window insets, so its first row sits under the status bar;
  it is a probe, not a screen.
- Next best step: independent validation of `design-tokens-theme`.

### Session 022 — 28 September 2026

- Goal: independent validation of `design-tokens-theme`.
- Completed: validator verdict **accept**; status set to `accepted`. Orchestrator follow-ups:
  `.gitattributes` keeps the OFL licence texts byte-identical to upstream on any checkout
  (`core.autocrlf=true` would otherwise write CRLF copies) and marks `*.ttf` binary; the
  architecture skill now lists the Konsist colour rule's named-argument gap (`Color(red = …)`); a
  stale `passing` for `konsist-isolation-rules` in "What exists" corrected.
- Verification run (by the validator): gate exit 0 with three `wired`; `:core:ui` 21/21 and
  Konsist 9/9; fonts and licences re-fetched from the pinned commits with matching SHA-256 on disk,
  in the blob and in the APK; contrast recomputed; its own probes (amber on `surfaceTint`, amber on
  `archive`, an Int ARGB literal and a numeric `Color(…)` in an `:app` test, two typography
  drifts) all caught; Pixel 5 background `#111318` and label `#E2E2E8` under Display P3.
- Known risk or unresolved issue: `BluesJamTypographyTest` does not check line heights; the
  ThemeShowcase swatch probe was run by the implementer only; M3 component defaults (e.g. the
  navigation bar indicator) are not design decisions — each component slice sets them from tokens.
- Next best step: plan `domain-model-types`.

### Session 023 — 28 September 2026

- Goal: plan `domain-model-types`.
- Completed: `docs/specs/domain-model-types.md`, prototyped on a throwaway clone. Types in
  `com.bbbjam.core.model`: `JamStatus`, `Instrument`, `Tempo`, `Difficulty`, value classes `Key` and
  `SongId` (validated against the schema formats), `Slot`, `Lineup`, `JamSong`, `Jam`, `Song`.
  Positions must be exactly 1..n when a `Jam` is built; "historical" takes today's date from the
  caller.
- Finding: java.time on minSdk 24 is safe in `:core:model` as long as it never parses dates or reads
  the clock; the first Android slice that does must enable core library desugaring in `:app` and in
  its own module (lint and AAR metadata checks block the unsafe cases, proven in the prototype).
- Decisions by the user (29 September): the Konsist rule `core-model-no-system-clock` **approved**;
  lineups **only shrink** from the default seven, and anyone playing outside the lineup goes in a
  per-song "Otros" list with name and instrument — recorded as **D-18** in the bitácora,
  `CONTEXT.md`, `docs/domain-model.md`, `docs/sheet-schema.md` (new optional `Otros` column), the
  seed CSV, START-HERE and the spec's "User Approvals". Adding and removing an extra participant
  join the D-13 mutation list (noted on `admin-adjust-lineup` and `action-contract-registry`).
- Also refreshed `START-HERE.md` (it still pointed at the first spec) and the `AGENTS.md` gate line
  (it still said the tools were unwired).
- Next best step: the implementer runs the spec with its User Approvals.
- Open questions recorded in the spec, not blocking: empty slot cells in past jams mean "not
  recorded" (handled by `past-jam-detail`); a 21:00 jam counts as historical from 00:00; a DRAFT jam
  in the past and a song twice in one setlist are not enforced anywhere yet.

### Session 024 — 29 September 2026

- Goal: implement `domain-model-types` (spec `docs/specs/domain-model-types.md`, with its User
  Approvals: Konsist rule approved, D-18).
- Completed: eleven spec types plus `ExtraParticipant` in `core/model/src/main/kotlin/com/bbbjam/core/model/`;
  `Lineup` rejects more of an instrument than the default (2 guitars, 1 of each other), zero stays
  valid; `JamSong.extraParticipants` (default empty) never touches the lineup; `CoreModelMarker` and
  `CoreModelMarkerTest` deleted, `ModuleWiringTest` now uses `JamStatus` (KDoc says the `:core:model`
  edge is also reachable through the `api` edges of `:core:ui`/`:core:data`); Konsist rule
  `core-model-no-system-clock` (a textual match for `.now(`, `Clock.system`,
  `System.currentTimeMillis(`, `System.nanoTime(` in any `:core:model` file). Docs: the Core
  Concepts sketch in `docs/domain-model.md` (no `Jam.id`, `setlist`, `JamSong` title/artist/
  `extraParticipants`, `Lineup`, `ExtraParticipant`), and the architecture skill (module row, clock
  rule, java.time/desugaring convention).
- Verification run: `CI=true ./init.sh` baseline exit 0; `./gradlew ktlintFormat` exit 0; final
  `CI=true ./init.sh` exit 0 with three `wired`. `:core:model` 30/30 (`ExtraParticipantTest` 3,
  `JamSongTest` 5, `JamStatusTest` 1, `JamTest` 4, `KeyTest` 3, `LineupTest` 7, `SlotTest` 3,
  `SongIdTest` 2, `SongTest` 2); `ModuleIsolationTest` 10/10; `ModuleWiringTest` 1/1; detekt 0
  findings; `grep -rn CoreModelMarker --include=*.kt` empty. Fourteen negative demonstrations, each a
  scripted mutation restored from a copy with matching SHA-1: loosened key and id regexes, inverted
  `isOpen`, no blank-name check, no position rule, position ≥ 0, same-day historical, `KEYBOARDS`
  missing from the defaults, no over-default check, no `;()` check, no blank check on extras, extras
  turned into an open slot, an `ARCHIVED` status, and `LocalDate.now()` in `Jam.kt` (Konsist 10
  tests, 1 failed: `core-model-no-system-clock`). Each failed the named test; details in
  `feature_list.json`.
- Finding fixed in scope: the `KEYBOARDS` probe showed `Lineup` threw `NoSuchElementException` for
  an instrument absent from the defaults; it now treats the default count as 0, so every rejection
  is an `IllegalArgumentException`.
- No device check: nothing visible changed.
- Evidence captured: `feature_list.json` → `domain-model-types` (status `passing`); logs and the probe
  script in the session scratchpad only.
- Open questions from the spec, not blocking: **A** decided as D-18. **B** an empty slot cell in a
  past jam means "not recorded", but `Slot.isOpen` is structural, so `past-jam-detail` must not draw
  open state. **C** a 21:00 jam is historical from 00:00 (already in
  `docs/risks-and-open-questions.md`). **D** a DRAFT jam in the past and a song twice in one setlist
  are not enforced; "at most one upcoming jam" belongs to the `Jams` mapper/repository and Apps
  Script.
- Known risk or unresolved issue: java.time on `minSdk` 24 is safe only while `:core:model` never
  parses a date or reads the clock; the first Android module that builds dates must enable core
  library desugaring in `:app` and in itself (architecture skill). The clock rule is textual: a
  statically imported `now()` or a clock reached under another spelling slips past it. The extras probe had to replace
  `JamSong` with a plain class, because `Lineup` cannot see extras by construction.
- Next best step: independent validation of `domain-model-types`.

### Session 025 — 29 September 2026

- Goal: independent validation of `domain-model-types`.
- Completed: validator verdict **accept**; status set to `accepted`.
- Verification run (by the validator): gate exit 0 with three `wired`, Konsist 10/10; `:core:model`
  30/30 with `--rerun`; its own probes — trailing-space, tab and Spanish-name keys, two basses,
  positions `[1,3,2]`, a third guitar as an extra participant, the clock rule via
  `Clock.systemDefaultZone()` and `LocalTime.now()` (a parameter named `now` passes), android and
  serialization imports failing to compile — with SHA-1 and git state identical afterwards.
- Known risk or unresolved issue: `KeyTest` does not list a trailing-space key, so a regex letting
  `"B "` through would pass the suite; added to `catalog-repository-cache`'s notes. The clock rule
  is textual (a static import of `now` would pass it).
- Next best step: `sheet-schema-definition` needs the user to confirm the real Sheet by hand
  (including the new `Otros` column); `info-screen` can proceed meanwhile.

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
