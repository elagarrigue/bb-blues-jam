# Progress Log

## Current Verified State

- Repository root: `C:/Users/Emmanuel/AndroidStudioProjects/BBBluesJam`
- Standard startup path: `./init.sh`
- Standard verification path: `CI=true ./init.sh`, which wraps `./gradlew build` and
  `./gradlew check`
- Current feature: `apps-script-read-endpoint`, `in_progress`. The repo half is done and
  self-verified (session 035); it waits for the user to deploy the script and hand over the
  `/exec` URL, then for the live checks M1–M5. Not `passing` until then.
  Accepted: `gradle-kotlin-compose-baseline`, `module-skeleton`,
  `konsist-isolation-rules`, `detekt-ktlint-gate`, `molecule-presenter-harness`,
  `design-tokens-theme`, `domain-model-types`, `sheet-schema-definition`, `info-screen`,
  `build-logic-conventions`.
- Current blocker: the live half of `apps-script-read-endpoint` waits on the user (deployment
  and the `/exec` URL). The seed was imported into the real Sheet and reviewed by hand by the user
  (29–30 September 2026, the user's report; no agent can read the Sheet).
- Last verified at: 1 October 2026 (session 035, `apps-script-read-endpoint` repo half) —
  `CI=true ./init.sh` exit 0, three `wired`, the 20 test result files identical in names and
  counts to the baseline; `node --test backend/apps-script/test/*.test.js` 33/33 (outside the
  gate). Before that, 30 September 2026 (session 033, `build-logic-conventions`) — `CI=true ./init.sh`
  exit 0, `konsist: wired` (11/11, new `build-file-applies-convention`), `detekt: wired`,
  `ktlint: wired`; every other suite unchanged (20 result files, 0 failures). Task plans,
  `buildEnvironment`, manifests, dependency sets and the debug APK (392 non-`META-INF` entries by
  SHA-1) identical to HEAD `095c88b`. Pixel 5 (API 34): cold start 872 ms, `Status: ok`, empty
  crash buffer and AndroidRuntime log, Info drawn unchanged.
  Before that, session 030 — `CI=true ./init.sh` exit 0, `konsist: wired`
  (10/10, now checking the real `:feature:info`), `detekt: wired`, `ktlint: wired`; new
  `InfoPresenterTest` (4) and `InfoModuleTest` (1), all other counts as below, 0 failures. Pixel 5
  (API 34): cold start 760 ms, empty crash buffer and AndroidRuntime logcat, Info drawn from the
  tokens (background `#111318`, no amber), each link starts an `ACTION_VIEW` for its host (Chrome for
  Instagram and Linktree, the YouTube app for YouTube) and back returns to Info; the admin notice
  appears. Link `onClickLabel`s are not verified on device (see session 030).
  Session 028 (docs and one KDoc sentence only) kept the gate green. Before that, 29 September 2026 (session 024) — `CI=true ./init.sh` exit 0; it prints
  `konsist: wired` (10 Konsist tests, 0 failures), `detekt: wired`, `ktlint: wired`; `:core:model`
  runs 30 tests in 9 classes on the JVM, 0 failures; `:core:ui` runs `EventHandlerTest` (6),
  `SamplePresenterTest` (3), `BluesJamColorsTest` (3), `BluesJamTypographyTest` (2),
  `ContrastTest` (6) and `WindowBackgroundTest` (1), 0 failures. Last launch check on the Pixel 5
  (API 34) was session 021: cold start, empty AndroidRuntime logcat, background `#111318`, label
  Barlow Condensed Bold. The display runs in Display P3, so saturated colors in screenshots read
  as their P3 encoding (amber `#FFB300` → `#F4B63F`). Session 024 changed nothing visible.

### What exists

Five product modules: `:app`, `:core:model`, `:core:ui`, `:core:data` (`module-skeleton`,
`accepted`) and `:feature:info` (`info-screen`, `accepted`).
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

Module build setup lives in `build-logic/` (`build-logic-conventions`, `accepted`): an included build
(`includeBuild("build-logic")` in `pluginManagement`) whose `:convention` subproject holds six binary
plugins in `com.bbbjam.buildlogic` — `bluesjam.jvm.library` (`:core:model`, `:konsist-test`),
`bluesjam.android.library` (`:core:data`), `bluesjam.android.application` + `bluesjam.android.compose`
(`:app`), `bluesjam.android.presenter` (`:core:ui`) and `bluesjam.android.feature` (`:feature:info`).
They set SDK levels (`compileSdk`, `minSdk`, and now `targetSdk` from the catalog), Java 11,
Compose, `isReturnDefaultValues`, the presenter test libraries and Koin for features. Module files
keep only `id("bluesjam.…")`, namespace, `:app`'s identity and build types, and their own
dependencies. The catalog is shared from `gradle/libs.versions.toml` (new `targetSdk`,
`android-gradlePlugin`, `kotlin-gradlePlugin`); detekt and ktlint are still applied from the root
`subprojects {}` block and do not lint `build-logic` itself.

A test-only module `:konsist-test` (`bluesjam.jvm.library`, no project dependency) holds
`ModuleIsolationTest`: 11 Konsist 0.17.3 tests that enforce the architecture skill's dependency rules
(no feature→feature or feature→`:app` imports, `:core:*` import allowlist, no Android in
`:core:model`, no system clock in `:core:model`, no ViewModel, package roots `com.bbbjam.<module path>` without hyphens, allowed
`project(":…")` dependencies in `core/*`/`feature/*` build files, every module build file applying
its `bluesjam.*` convention with no raw plugin and no convention-owned setting, and no color literal
outside `:core:ui`). Module groups are read from paths,
so the first `:feature:*` module is covered without editing the suite. The test task declares every
`.kt`/`.kts` file as an input, so a change elsewhere reruns it (`konsist-isolation-rules`, `accepted`).

`:app` is on a Kotlin + Jetpack Compose baseline (`gradle-kotlin-compose-baseline`,
`accepted`). AGP 9.4.1 with its built-in Kotlin (no `org.jetbrains.kotlin.android`
plugin), Kotlin/KGP 2.2.10, the Compose compiler plugin `org.jetbrains.kotlin.plugin.compose` on the
same `kotlin` catalog key, Compose BOM 2025.09.00 (ui, foundation, ui-tooling-preview, ui-tooling for
debug only) and activity-compose 1.11.0. appcompat and Material Views are gone; Material 3 arrives
through `:core:ui`.

`MainActivity` (a `ComponentActivity`, the launcher) turns on edge-to-edge with dark system bars and
shows `InfoScreen` inside `BluesJamTheme`, with the system-bar insets as `contentPadding` (the
placeholder screen is gone). `BluesJamApp` (the manifest's `android:name`) calls `startKoin` with
`appModule` and `infoModule`; `appModule` binds `ExternalLinkOpener` to `IntentLinkOpener`. Koin is
4.1.1 (BOM); kotlin-stdlib stays 2.2.10 and Compose 1.9.1 on `:app`'s runtime classpath. `:app` has
no colors of its own: its `colors.xml` is gone, and the window
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
`ContrastTest`, `WindowBackgroundTest`, and `:feature:info`'s `InfoPresenterTest` and `InfoModuleTest`.

`:feature:info` (`info-screen`, `accepted`) is the first feature module and the template for the
others: `InfoPresenter` (`Presenter<InfoUiModel, Unit>`, constructor `ExternalLinkOpener` from
`:core:ui`), `InfoUiModel` with `OpenLink`/`DismissLinkError`/`AdminEntryTapped`, the approved copy
in `internal object InfoCopy`, `SocialLink` (three URLs), `InfoScreen` (renders only,
`koinInject()` default) and `infoModule`. It shows who organizes the jam, the jam, Hideaway, how to
join, three social links and "Entrar como admin", which only shows "El ingreso de admin todavía no
está habilitado." until `admin-passphrase-login`. No venue (D-19), no station, no amber.

Product code: the `:core:model` domain types and the Info screen.

`backend/apps-script/` (not a Gradle module; `apps-script-read-endpoint`, `in_progress`) holds
the Apps Script web app: `appsscript.json` (V8, `spreadsheets.currentonly`, executes as the owner,
anonymous access) and `src/Normalize.js`, `Catalog.js`, `Code.js` — one route,
`GET ?resource=catalog`, which never opens `Config`. Node tests (33, `node:test`, no
`package.json`) run outside `init.sh`; `tools/check-response.js` checks a live response. The
contract is `docs/apps-script-api.md`, with mapper fixtures in `docs/api-samples/`. Not deployed
yet: deploying is the user's step.

The Sheet contract is `docs/sheet-schema.md` (`sheet-schema-definition`, `accepted`): tabs, headers,
exact Spanish enum values, cell reading rules, slot identity (the k-th slot of an instrument is the
k-th column of it not holding `-`), a Type-to-Sheet mapping of every `:core:model` field, and the
**Mapper rules** the repository slices must enforce. `docs/sheet-seed/` is the one-time import
file; the real Sheet is the authority and already holds more songs.

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
    closed, never falsely green. Fixed 29 September with the user's approval (`|| true` on the grep
    in `modules_running`): an empty plan now prints `NOT WIRED YET (missing from check in: every
    module)` for both tools instead of aborting; `bash -n` clean and the full gate still exits 0
    with three `wired`.
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

### Session 026 — 30 September 2026

- Goal: unblock `sheet-schema-definition` and `info-screen`.
- Completed:
  - The user reports: the real Sheet reviewed, the `Otros` column added to the jam tab, and more
    songs added to `Catalogo`. Not verifiable from this session (private Sheet); recorded as the
    user's manual evidence. The planner is specifying `sheet-schema-definition` on that basis.
  - Info content gathered from the user and the public @bahiablancablues Instagram and Linktree
    into `docs/info-content.md`, each fact with its source. **D-19**: Info presents Bahía Blanca
    Blues (the organizing group), the monthly jam, the Hideaway radio program (Tuesdays 20–22),
    social links and how to join (come and sign up there); **no venue**, because it can change —
    it belongs to each jam. Updated DESIGN.md, the design prompt, `info-screen` in
    `feature_list.json`, CONTEXT.md (Venue, new "Bahía Blanca Blues"), the build brief, the
    bitácora and START-HERE; decision range now D-01 … D-19.
- Known risk or unresolved issue: the Hideaway radio station/frequency is unknown, and its schedule
  comes from an automated summary of Instagram — confirm with the user before release.
- Next best step: finish `sheet-schema-definition`; plan `info-screen`.

### Session 027 — 30 September 2026

- Goal: plan `sheet-schema-definition` and `info-screen`, and settle their decisions.
- Completed:
  - `docs/specs/sheet-schema-definition.md`: a field-by-field check of the schema against the
    domain types found eleven documentation mismatches (M1–M11) and one gap. The user decided the
    gap (U1): a slot is identified by its order among that instrument's non-`-` columns.
  - `docs/specs/info-screen.md`: `:feature:info` with a presenter, Koin 4.1.1 (4.2.2 would lift
    Kotlin and Compose above the pinned versions), a shared `ExternalLinkOpener` in `:core:ui`, copy
    in Kotlin from `docs/info-content.md` only. The user approved the copy table as written, Koin
    now, and splitting build-logic into its own slice.
  - New slice `build-logic-conventions` (35 features now), placed before `next-jam-read-only-list`,
    which depends on it; no cycles.
- Known risk or unresolved issue: the Pixel 5 is disconnected (`adb devices` empty); `info-screen`
  needs it for the launch and link checks.
- Next best step: implement `sheet-schema-definition`; then `info-screen` once the phone is back.

### Session 028 — 30 September 2026

- Goal: implement `sheet-schema-definition` (spec `docs/specs/sheet-schema-definition.md`, U1 = B).
- Completed:
  - Re-ran the mapping check at HEAD `ec90c8f`: M1–M11 still held; no new mismatch.
  - `docs/sheet-schema.md`: new **Reading cells** (trim every cell; exact, case- and
    accent-sensitive enum and header matching, P1; the endpoint normalizes `fecha`, `hora`,
    `posicion`, M7); exact `SongId` alphabet in **Identifiers** (M1); enum mappings for `tempo` and
    `dificultad` (M2); `etiquetas` split rule (M3); a derived `setlist` row in `Jams` (M5); "at most
    one non-historical jam" (M6); `title` / `artist` (fallback only) on the jam tab (M8); a **Slot
    columns** header-to-`Instrument` table, all seven headers required (M9, P2); an **`Otros`**
    parsing section (M10); slot identity by ordinal among that instrument's non-`-` columns (U1 =
    B); a **Type-to-Sheet mapping** table; and a **Mapper rules** section, each rule "enforced by
    the repository slice", with the `Jams`/tab mismatch behavior left open.
  - `docs/domain-model.md`: `songsterrId` is admin-entered, not enrichment (M4); a song removed from
    the catalog points at `sheet-schema.md` (M11).
  - `docs/risks-and-open-questions.md`: the import and growing catalog; the cell-type question for
    `apps-script-read-endpoint`; the open `Jams`/tab mismatch rule and the P3 seed-as-fixtures
    hand-off for `catalog-repository-cache`.
  - `Lineup.kt`: one KDoc sentence reworded for U1 = B. Comment only.
  - Manual evidence, **the user's own report, not agent-verified** (no agent can read the private
    Sheet): on 29–30 September 2026 the four seed CSVs were imported, the tabs reviewed by hand, the
    `Otros` column added to the jam tab, and more songs added to `Catalogo`. The user did not
    mention the `Config` tab; nothing is claimed about it.
- Verification:
  - Seed check (scratch `seed_check.py`, not committed) on `docs/sheet-seed`: `CONFORMS`, exit 0
    (13 songs, 1 `Jams` row, tab `2026-07-25` positions 1..13; 0 names, 0 `-`, 0 `Otros`). A scratch
    copy with names, `-` cells and `Juan (saxo); Ana (percusión);` also conforms, so those checks
    are not only vacuous.
  - Failures on mutated scratch copies, each exit 1: `tono "Bmaj" is not a key`; `posicion values
    [1, …, 12, 14] are not exactly 1..13`; `Otros entry "Juan saxo" is not "Nombre (instrumento)"`;
    `tempo "rapido" is not one of ['lento', 'medio', 'rápido']` and `estado "PUBLICADO" is not one
    of ['BORRADOR', 'PUBLICADA']`; `missing required headers ['Teclados']`; `id_tema
    "got-my-mojo-workin" is not in Catalogo`; `Config: passphrase valor is filled`. The committed
    seed is unchanged.
  - `./gradlew ktlintFormat` exit 0, no file changed. `CI=true ./init.sh` exit 0: `konsist: wired`,
    `detekt: wired`, `ktlint: wired`, `Baseline OK.`; `:core:model` 30/30, Konsist 10/10.
- Known risk or unresolved issue: whether the real `fecha`, `hora` and `posicion` cells are text or
  typed values is unknown until `apps-script-read-endpoint` reads them. Behavior on a `Jams`/tab
  mismatch is undecided (for `catalog-repository-cache`).
- Next best step: validate `sheet-schema-definition`; then `info-screen` once the phone is back.

### Session 029 — 30 September 2026

- Goal: independent validation of `sheet-schema-definition`.
- Completed: validator verdict **accept**; status set to `accepted`; the stale "import the seed"
  note replaced with the user's reported hand check. The former top blocker is closed.
- Verification run (by the validator): gate exit 0 with three `wired`; `Lineup.kt` diff KDoc-only;
  every field of the 12 domain types checked against the new Type-to-Sheet mapping with no
  remaining mismatch; its own seed checker, independent of the implementer's, conforms on the seed
  and catches a duplicate catalog id and a date tab with no `Jams` row.
- Known risk or unresolved issue: left undefined for the mapper slice (noted on
  `catalog-repository-cache`): duplicate slot headers, `Nombre(instrumento)` without a space,
  duplicate `Jams.fecha`. Under U1 = B, restoring a guitar slot into `Guitarra 1` shifts a named
  musician from the 1st to the 2nd guitar slot (noted on `admin-adjust-lineup`). Whether the real
  Sheet's `fecha`/`hora`/`posicion` cells are plain text is unknown; the user was advised to format
  them as plain text.
- Next best step: implement `info-screen` on the Pixel 5.

### Session 030 — 30 September 2026

- Goal: implement `info-screen` (spec `docs/specs/info-screen.md`, with the user's approvals: copy
  table verbatim, Koin 4.1.1 and `startKoin` now, copy in Kotlin, the admin notice, build-logic
  deferred to `build-logic-conventions`).
- Completed:
  - `:feature:info` (`settings.gradle.kts`, `feature/info/.gitignore`, `build.gradle.kts`):
    `SocialLink`, `InfoUiModel`, `InfoCopy`, `InfoPresenter`, `InfoScreen`, `di/InfoModule.kt`;
    tests `InfoPresenterTest` and `InfoModuleTest`.
  - `:core:ui`: `ExternalLinkOpener` (`com.bbbjam.core.ui.link`).
  - `:app`: `BluesJamApp`, `di/AppModule.kt`, `link/IntentLinkOpener.kt`, manifest
    `android:name`, `MainActivity` shows Info; `PlaceholderScreen.kt` and `placeholder_title`
    deleted. Catalog: `koin` 4.1.1, `koin-bom`, `koin-core`, `koin-android`, `koin-compose`.
  - Architecture skill: `:core:ui` and `:app` rows, `:feature:info` exists, link-opening bullet,
    build-logic bullet pointing at `build-logic-conventions`, reference feature build file, Koin
    4.1.1 (and why not 4.2), copy in Kotlin, 48dp via `LocalMinimumInteractiveComponentSize`.
    `presenter-pattern.md` names `InfoPresenter`/`InfoPresenterTest` as the first compiled feature
    example.
- Verification run:
  - Baseline `CI=true ./init.sh` exit 0 before any change. `./gradlew ktlintFormat` exit 0 (it
    re-wrapped one call in `InfoPresenter.kt`). `CI=true ./init.sh` exit 0, three `wired`.
    `InfoPresenterTest` 4/4, `InfoModuleTest` 1/1, Konsist 10/10, every other suite unchanged.
  - `:app:dependencies --configuration debugRuntimeClasspath`: Koin 4.1.1; all kotlin-stdlib →
    2.2.10; compose-bom 2025.09.00, Compose 1.9.1, material3 1.3.2; lifecycle 2.9.3. Lint adds one
    `NewerVersionAvailable` (koin-bom 4.2.2).
  - Failure demonstrations, each restored and SHA-1 checked (`sha1sum -c` OK on all three files):
    m.youtube URL → `each link row opens exactly its url` fails with expected/actual URL lists;
    `linkError` never set → `a link that cannot be opened…` fails, `No value produced in 3s`;
    `import com.bbbjam.MainActivity` in `InfoModule.kt` → Konsist `feature-imports-app` violated.
  - Spec greps on `feature/info/src/main` clean (the `AM` pattern only hits `INSTAGRAM`).
  - Pixel 5, manual: cold start, empty crash/AndroidRuntime logs; each link tapped from
    `uiautomator` coordinates gave `act=android.intent.action.VIEW` for `www.instagram.com`,
    `www.youtube.com` and `linktr.ee` (Chrome, YouTube app, Chrome); back returned to the same
    `MainActivity` record each time (Instagram and YouTube needed a second back for the page modal and
    YouTube's own Home). Admin notice shown, no activity started. Rows and admin entry are 48dp
    clickable nodes. Screenshots and dumps in the session scratchpad only.
- Known risk or unresolved issue:
  - Link `onClickLabel`s are not verified on device: `uiautomator` does not export action labels,
    and turning TalkBack on through `adb settings` was refused by the permission system (settings
    confirmed unchanged). A validator with TalkBack or an instrumented semantics test can close it.
  - The YouTube link opens a channel named "Radio Hideaway"; the row says only YouTube /
    youtube.com. Confirm with the user that this is the channel to show.
  - With the insets inside the scroll (spec Decision 5), scrolled content passes under the
    transparent status bar (the clock overlaps a heading mid-scroll). As specified; `bottom-navigation`
    may want a status-bar scrim or top inset outside the scroll.
  - Opening the links left two tabs in the phone's Chrome; no setting was changed.
- Next best step: independent validation of `info-screen`; then `build-logic-conventions`.

### Session 031 — 30 September 2026

- Goal: independent validation of `info-screen`.
- Completed: validator verdict **accept**; status set to `accepted`. The first `:feature:*` module
  is in, with Koin 4.1.1 started in `:app` and the approved copy.
- Verification run (by the validator): gate exit 0 with three `wired`, Konsist 10/10 now checking a
  real feature module; `:feature:info` tests 5/5 rerun; dependency report Koin 4.1.1 only,
  kotlin-stdlib 2.2.10 only; 23/23 copy strings match the approved table by script; its own probes
  caught a changed copy word, a feature→`:app` build dependency, and two wrong admin-entry
  behaviours; on the Pixel 5 the Linktree and YouTube intents opened and back returned, and the
  admin notice appeared.
- Known risk or unresolved issue:
  - **Gate gap:** an amber role or `MaterialTheme.colorScheme` in a screen passes the whole gate;
    D-17 is enforced only by per-spec greps. Proposed as a Konsist rule for
    `next-jam-read-only-list` (needs the user's approval).
  - Content scrolls under the transparent status bar; noted on `bottom-navigation`.
  - Link click labels verified on the JVM model and by code reading, not on device.
  - The YouTube link opens a channel named "Radio Hideaway"; recorded in `docs/info-content.md`
    for the user to confirm.
- Next best step: plan `build-logic-conventions`.

### Session 032 — 30 September 2026

- Goal: plan `build-logic-conventions`.
- Completed: `docs/specs/build-logic-conventions.md`, proven on throwaway clones: an included
  `build-logic` with six Kotlin convention plugins, detekt/ktlint kept root-applied, `init.sh`
  unchanged; task plans, dependency sets, 20 test classes and the debug APK (392 non-META-INF
  entries by SHA-1) identical before and after; the Pixel 5 launches unchanged. The user
  **approved** the new Konsist rule `build-file-applies-convention` (10 → 11).
- Known risk or unresolved issue: the Kotlin inside `build-logic` is not linted (outside the root
  build); the first build on a fresh clone is ~30 s slower once; Android Studio sync not tried.
- Next best step: the implementer runs the spec.

### Session 033 — 30 September 2026

- Goal: implement `build-logic-conventions` (spec `docs/specs/build-logic-conventions.md`; the user
  approved the Konsist rule `build-file-applies-convention`, 10 → 11).
- Completed:
  - `build-logic/settings.gradle.kts` (repositories as the root, `FAIL_ON_PROJECT_REPOS`, catalog
    `libs` from `../gradle/libs.versions.toml`), `build-logic/convention/build.gradle.kts`
    (`kotlin-dsl`, AGP/KGP `compileOnly`, six registrations), `build-logic/convention/.gitignore`
    (`/build`), `ProjectExtensions.kt` and the six `*ConventionPlugin` classes.
  - `settings.gradle.kts`: `includeBuild("build-logic")` first in `pluginManagement`. Catalog:
    `targetSdk = "37"`, `android-gradlePlugin`, `kotlin-gradlePlugin`.
  - Module files converted: `core/model`, `konsist-test`, `core/data`, `core/ui`, `feature/info`,
    `app`, as the spec's Expected File Changes.
  - `ModuleIsolationTest`: `build-file-applies-convention` and a KDoc paragraph on the build-file
    rules.
  - Architecture skill, Build Conventions: convention plugins table and rules, rewritten plugin /
    SDK / Java / presenter bullets, Isolated Projects note and the unlinted `build-logic` gap,
    reference feature build file, Konsist at 11 rules, template comment; `:konsist-test` row.
- Verification run (logs in the session scratchpad, `impl-blc/`):
  - Baseline on untouched HEAD `095c88b`: `CI=true ./init.sh` exit 0, three `wired`, Konsist 10.
  - `./gradlew ktlintFormat` exit 0 (no change). `CI=true ./init.sh` exit 0, three `wired`,
    `ModuleIsolationTest` `tests="11" failures="0"`, 20 result files with `failures="0"`.
    `build-logic` compiles with no `w:` line.
  - Spec item 2 grep over `*/build.gradle.kts */*/build.gradle.kts`: no output, exit 1.
  - Zero behaviour change, clean `./gradlew build` before (HEAD) and after, same capture script:
    byte-identical `check --dry-run` (353 lines) and `build --dry-run` (590) plans,
    `buildEnvironment`, merged manifests, AAR names, APK entry list (466) and SHA-1 of all 392
    non-`META-INF` entries, and 15 of 22 dependency reports (all `:core:*`, `:konsist-test`, `:app`
    `debugCompileClasspath`). The other seven (`:app` runtime ×3, `:feature:info` ×4) differ in
    order only and are identical as sorted coordinate sets. Test classes identical; only
    `ModuleIsolationTest` 10 → 11 tests.
  - Failure demonstrations, `./gradlew :konsist-test:test` exit 1 each, each file restored by `cp`
    and SHA-1 checked: `core/data` at its HEAD content → `build-file-applies-convention` violated 7
    times ("applies no bluesjam.* convention plugin", ":2 applies a plugin directly…", ":7 sets
    'compileSdk'…"); `feature/info` with `bluesjam.android.presenter` → "must apply
    bluesjam.android.feature (applies [bluesjam.android.presenter])"; `core/ui` with
    `defaultConfig { minSdk = 26 } // targetSdk…` → one violation, ":7 sets 'minSdk'" (the comment
    word is ignored); `core/data/build.gradle.kts` moved out → "is missing";
    `implementation(project(":feature:info"))` in `core/data` → `build-file-project-deps` still
    fails on the new file shape.
  - Timings, `CI=true ./init.sh` seconds, before / after: warm 7, 7 / 7, 7; after `./gradlew clean`
    48 / 40; one module build file edited (configuration cache miss) 20 / 14; one convention source
    edited — / 18. Single runs on a shared machine; read as "no slower", not as a speed-up.
  - Pixel 5 (API 34), manual: `./gradlew :app:installDebug`; the installed `base.apk` has the same
    SHA-1 as `app-debug.apk`; `am start -W` `Status: ok`, `LaunchState: COLD`, 872 ms; crash buffer
    and AndroidRuntime log empty; Info drawn unchanged (screenshot in the scratchpad). No device
    setting changed.
- Deviations: the four Android module files were converted in one step and built together, not one
  build per file (the two JVM modules were built individually); the result is the same.
- Known risk or unresolved issue:
  - `build-logic` Kotlin is not linted by detekt/ktlint and not counted by `init.sh` (spec
    Decision 3, left for a later slice with the user's say).
  - A fresh clone pays for compiling `build-logic` once (planner measured ~30 s); Android Studio
    sync and opening `build-logic` standalone were not tried.
  - `build-logic/.kotlin/` (Kotlin session dir, empty after a build) is not ignored, the same as the
    root `.kotlin/` today.
  - With `core.autocrlf=true`, `git checkout -- <file>` rewrites LF files as CRLF; restore probes by
    copying the saved bytes, and check with `sha1sum`.
- Next best step: independent validation of `build-logic-conventions`.

### Session 034 — 30 September 2026

- Goal: independent validation of `build-logic-conventions`.
- Completed: validator verdict **accept**; status set to `accepted`. Orchestrator follow-up:
  `.kotlin/` added to the root `.gitignore` (the Kotlin daemon writes error logs there, at the root
  and in `build-logic/`; the root gap predated this slice).
- Verification run (by the validator): gate exit 0 with three `wired`, Konsist 11/11; against its
  own clean clone of HEAD, plans, `buildEnvironment`, the APK's non-META-INF entries by SHA-1 and
  dependency coordinate sets identical; its probes showed the new rule catching five evasions,
  the presenter convention being load-bearing, and `build-file-project-deps` still working.
- Known risk or unresolved issue: the rule `build-file-applies-convention` misses backtick plugin
  ids, `pluginManager.apply` / `plugins.apply`, and `java { toolchain }` — a hardening needing the
  user's approval (gate change). The Kotlin inside `build-logic` is not linted.
- Next best step: `apps-script-read-endpoint` — plan it; deploying the script is the user's step.

### Session 035 — 1 October 2026

- Goal: implement the repository half of `apps-script-read-endpoint` (spec
  `docs/specs/apps-script-read-endpoint.md`, with the user approvals U2–U6 and U1 copy-paste).
- Completed:
  - `backend/apps-script/`: `appsscript.json` (P8), `src/Normalize.js`, `src/Catalog.js`,
    `src/Code.js` (guarded `module.exports`, P1/P6), `.gitignore` (`.clasp.json`,
    `.clasprc.json`, `node_modules/`, `*.local.json`), `README.md` (copy-paste deployment as the
    primary path, clasp optional and noted as not ready for this layout, redeploy-as-new-version,
    live check commands), `tools/check-response.js`, and the tests: `test/normalize.test.js` (8),
    `test/catalog.test.js` (14), `test/router.test.js` (11), with helpers `load.js`, `csv.js`,
    `contract.js`, `edge-input.js`.
  - `docs/apps-script-api.md` (contract, errors, HTTP-200 and redirect notes, quotas) and
    `docs/api-samples/catalog-seed.json` / `catalog-edge.json`, generated once from the builder,
    reviewed against P4, and from then on compared by the tests.
  - Docs: `sheet-schema.md` (**Reading cells**: catalog display text and raw `songsterr_id`;
    jam normalization now named for `apps-script-jams-read-endpoint`; **Mapper rules**: the
    endpoint/mapper split), `technical-discovery.md` (Deployment and Operations, Testing),
    `risks-and-open-questions.md` (cell types, drafts, assumption 6, research task), the
    architecture skill (`backend/apps-script` row, one **Where Each Piece Goes** line), `AGENTS.md`
    (optional-doc line).
- Verification run (logs in the session scratchpad):
  - Baseline on untouched HEAD `3d41000`: `CI=true ./init.sh` exit 0, three `wired`.
  - `node --test backend/apps-script/test/*.test.js`: exit 0, 33 tests, 0 fail.
  - Failure demonstrations, each probe restored from a saved copy with the SHA-1 checked:
    `textCell` without `.trim()` → 5 of 33 fail (`+ '  Crossroads  ' - 'Crossroads'`); one title
    changed in `catalog-seed.json` → the seed test fails; a `config` route reading `Config` →
    4 router tests fail, including `passphrase leaked: {"schemaVersion":1,"rows":[["clave","valor"],
    ["passphrase","s3cret"]]}` and `Code.js contains "Config"`; `readCatalog_` also calling
    `getSheets()` → 3 fail (`accessed getSheets,getSheetByName`). The checker exits 1 on a copy
    with a missing key and a numeric value, on an HTML page and on an error body; 0 on both
    samples; 2 with no argument. One real failure while writing: the doPost guard caught a
    comment in `Code.js` that mentioned doPost; reworded.
  - Quotas read from Google's page (last updated 3 September 2026) on 1 October 2026: 6 min per
    execution, 30 simultaneous executions per user, no daily cap listed for web apps.
  - `./gradlew ktlintFormat` exit 0; `CI=true ./init.sh` exit 0, three `wired`, the 20 test
    result files identical to the baseline in suite names and counts. No Gradle, Kotlin, Konsist
    or `init.sh` change. (No Kotlin changed, so Gradle may have reused up-to-date test results.)
- Deviations: two extra test helpers not listed in the spec — `load.js` reproduces Apps Script's
  shared global scope in Node (the files call each other as globals), and `edge-input.js` holds the
  edge input so the sample could be generated from the same data the test uses. Extra tests
  beyond T1–T3: the files run in one `vm` context with no `module`, a manifest guard, and no
  `doPost`/`passphrase` in `src/`. The README saves live responses under
  `backend/apps-script/`, where `*.local.json` is ignored (the spec's command would have saved it at
  the root, which ignores nothing of the kind). `unknown_resource` does not echo the requested value.
- Remaining, for the user (the Google account is required):
  1. Deploy by copy-paste as `backend/apps-script/README.md` says, and hand the `/exec` URL over in
     chat (it goes into `local.properties` as `bluesjam.appsScriptUrl`, never committed).
  2. Then, by whoever runs the curl: M1 checker exit 0 on the live catalog; M2 song count against
     the Sheet; M3 a live song with `null` optional fields passes; M4 `resource=config` gives
     `unknown_resource`; M5 one cold call after 30+ idle minutes plus min/median/max of ten warm
     calls (input to the optimistic-update decision in `admin-add-song-to-setlist`). Also report
     whether the real `Catalogo` headers match the eight exactly.
  3. Only then set `passing` and record M1–M5, attributed to whoever ran them.
- Known risk or unresolved issue:
  - Nothing has run on Apps Script itself: the `vm` test reproduces its shared scope, but V8 on
    Google's side, the consent screen and the redirect are unobserved until the deployment.
  - The real `Catalogo` may have headers that differ from the eight (`missing_header`) or values
    the mapper will reject; the checker checks shape only.
  - Node tests are outside the gate: a change to `backend/apps-script` can break them with
    `init.sh` still green. Revisit at `apps-script-write-auth`.
  - What an anonymous caller gets when the 30-concurrent limit is hit is not verified.
- Next best step: the user's deployment (step 1 above), then M1–M5, then validation.

## Notes For The Next Session

- The Sheet schema is settled (`docs/sheet-schema.md`) and the seed is imported (user's report).
  What a human still owns, in parallel with coding: **loading real repertoire** with tempo, tags and
  difficulty.
  Week 5 needs a catalog large enough for the assistant to produce an interesting themed setlist.
- Before `action-contract-registry` runs, confirm the mutation list is complete. The current list is
  add song, remove song, set key, adjust lineup, assign musician, clear slot, reorder, publish
  (D-13).
- Capture course evidence as it appears rather than reconstructing it later: the Konsist output, the
  feature-module diff when the assistant lands, and a timed recording of the manual flow before the
  app replaces it.
