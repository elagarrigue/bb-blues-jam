# Spec: core-assistant-module

Move the setlist assistant agent out of `:core:data` into a new `:core:assistant` module.

## Source feature

- id `core-assistant-module`, area `core`, status `not_started`, depends_on `wiring-and-preview-hygiene` (accepted).
- Origin: organizer's code review of 17 October 2026, finding 9. Decision of the organizer, 17 October 2026:
  package `com.bbbjam.core.data.assistant` becomes module `:core:assistant`, package `com.bbbjam.core.assistant`,
  depending on `:core:model` and `:core:data`. This **deliberately amends Konsist** (it reverses the reason recorded in the
  "As built by `assistant-agent-core`" paragraph, which put the agent in `:core:data` because a `:core:assistant`
  would have broken `core-import-allowlist` and `build-file-project-deps`). No D-01..D-28 decision is contradicted:
  D-03 forbids feature-to-feature edges, not this one core edge.
- User-visible behavior: none.

## Goal and non-goals

Goal: `:core:data` holds no Koog, no Ktor and no agent code; the agent lives in `:core:assistant`; the gate stays green
with every amended rule shown failing first.

Non-goals: no behavior change (prompts, tools, streaming, errors, `AssistantModel` constants), no new core-to-core edge
beyond `core/assistant -> :core:data`, no widening of any library allowlist to another module, no change to rules 19, 20,
21, 22, no public API change in `:core:data` beyond what the inventory below says (none), no new convention plugin.

## Inspected (facts) and assumed

Inspected: `feature_list.json` entry, `AGENTS.md`, architecture skill (whole), `core/data/build.gradle.kts`,
`settings.gradle.kts`, `konsist-test/.../ModuleIsolationTest.kt` (rules and companion), every import of
`core/data/src/{main,test}/.../assistant`, `DataModule.kt`, `DataModuleTest.kt`, `AppModule.kt`, `BluesJamApp.kt`,
`feature/assistant/build.gradle.kts`, the list of files importing `core.data.assistant` in `feature` and `app`.
Not inspected: `docs/architecture.md` diagram source lines beyond grep hits, README module list beyond line 188,
`app/src/testDebug` and `AppModuleBindingsTest` contents, R8/proguard rules (grep found no `assistant` rule in `app/proguard*`;
assumed none).

### Inventory: `:core:data` types the agent uses today

All imports from `com.bbbjam.core.data.*` in the agent's **main** sources are already public. Nothing is made public.

| Used | Visibility | Where | Action |
|---|---|---|---|
| `catalog.CatalogRepository` | public | CatalogTool, DefaultSetlistAssistant | none |
| `jams.JamsRepository`, `jams.JamsSnapshot`, `jams.JamCalendar` | public | PastJamsTool, JamScheduleTool, DefaultSetlistAssistant | none |
| `jams.isValidTime`, `jams.normalizeVenue` (`jams/JamFields.kt`) | public top-level | JamProposalValidator | none |
| `DataFailure`, `Freshness`, `catalog.CatalogSnapshot/RefreshOutcome`, `jams.JamsRefreshOutcome` | public | `AssistantFakes.kt` (test) | none |
| `ProposalValidator`, `JamProposalValidator` | `internal`, **in the agent package** | they move with it | none; they use no `:core:data` internal and no internal repo |
| `AssistantCredentials.key` | `internal` in the agent package | moves with it | none |
| `core.data.Fixtures` (`internal object`, `core/data/src/test`) | test-internal | `AssistantLiveCallTest` only | cannot cross modules, see Task 8 |
| `catalog.CatalogMapper`, `jams.JamsMapper` (`internal object`) | internal | `AssistantLiveCallTest` only | same; do **not** make them public (they are the Sheet DTO mappers) |

### Library placement (decision this spec makes)

The agent main sources import `ai.koog.`, `io.ktor.` (via the build file and the HTTP factory), **`okhttp3.`** (`AssistantHttp`
builds the `OkHttpClient`, `AssistantStatusProbe` uses `Call`, `Callback`, `Request`, `Response`) and **`kotlinx.serialization.`**
(`ProposalDto` `@Serializable`, `Json`, `SerializationException`). `androidx.room.` and `androidx.datastore.` are not used.
So the allowlist, per module, becomes (a map `DATA_LIBRARY_ALLOWLIST: Map<String, List<String>>` replacing the single
module check in rules 1 and 2):

- `core/data`: `okhttp3.`, `androidx.room.`, `kotlinx.serialization.`, `androidx.datastore.` (loses `ai.koog.`, `io.ktor.`)
- `core/assistant`: `ai.koog.`, `io.ktor.`, `okhttp3.`, `kotlinx.serialization.` (never Room or DataStore)
- every other module: nothing (unchanged). `okhttp3.` is therefore allowed in exactly two modules, each for its own HTTP
  client; nothing widens to features, `:core:ui`, `:core:model` or `:app`.

## Acceptance scenarios

1. Given the gate, when `CI=true ./init.sh` runs, then it exits 0 and prints `konsist`, `detekt`, `ktlint` as `wired`.
2. Given `rg "ai\.koog|io\.ktor" core/data` (main, test, build file), then there is no match.
3. Given `:core:assistant`, when its tests run, then every test moved from `core/data/.../assistant` passes unchanged in
   behavior, and `AssistantLiveCallTest` still compiles and is skipped unless `-Pbbbjam.assistantLive=true`.
4. Given a planted `import androidx.room.Dao` in `core/assistant`, a planted `import ai.koog.agents.core.agent.AIAgent` in
   `core/data`, or a planted `import okhttp3.OkHttpClient` in `feature/assistant`, then Konsist fails (shown, then removed).
5. Given a planted import of `com.bbbjam.core.data.setlist.SetlistRepository` in `core/assistant` main, then
   `assistant-reads-only` fails; given the same in `core/ui`, `core-import-allowlist` fails.
6. Given the app, when it starts, then Koin resolves `SetlistAssistant` as one singleton (`AppModuleBindingsTest` or the new
   module test), not configured without a key.

## Technical approach

### Module `:core:assistant`

- `core/assistant/build.gradle.kts`: `id("bluesjam.android.data")` (assumption, see Open questions: it is the only existing
  convention that applies the kotlinx-serialization compiler plugin, which `ProposalDto` needs; `build-file-applies-convention`
  forbids a raw plugin line; KSP is applied with no processor, harmless). `android { namespace = "com.bbbjam.core.assistant" }`.
  Dependencies: `api(project(":core:model"))`, `implementation(project(":core:data"))`, `implementation(libs.kotlinx.serialization.json)`,
  `implementation(libs.okhttp)`, Koog and Ktor exactly as today (copy `koogWithoutJackson` and its Jackson comment),
  `implementation(platform(libs.koin.bom))`, `implementation(libs.koin.core)`; tests: `testImplementation(libs.okhttp.mockwebserver)`,
  `testImplementation(platform(libs.koin.bom))`, `testImplementation(libs.koin.test)`; no `ksp(...)`, no `testFixtures`, no Room.
  Keep the `tasks.withType<Test>` block with `bbbjam.rootDir` and the `bbbjam.assistantLive` system property (drop the
  `api-samples`/`sheet-seed` inputs unless Task 8 reads them). No SDK, Java or `buildFeatures` line (convention-owned).
- `core/assistant/.gitignore` with `/build`. No manifest. Sources in `src/main/kotlin`, `src/test/kotlin`.
- `settings.gradle.kts`: `include(":core:assistant")` after `:core:data`.
- Package `com.bbbjam.core.assistant` (and `.di`). Move with `git mv`: all 18 files of `core/data/src/main/.../data/assistant`
  and all 14 of `core/data/src/test/.../data/assistant` (list in "Expected file changes"); rewrite only the `package` line and
  imports (`com.bbbjam.core.data.assistant.X` -> `com.bbbjam.core.assistant.X`). Visibility of every declaration unchanged.

### Koin

- New `core/assistant/src/main/kotlin/com/bbbjam/core/assistant/di/AssistantCoreModule.kt`: `val assistantCoreModule = module { single<SetlistAssistant> { DefaultSetlistAssistant(get(), get(), get(), get(), AssistantHttp.executorFactory(), AssistantStatusProbe()) } }`
  (same arguments as `DataModule` today: credentials, catalog, jams, calendar). The name differs from the feature's `assistantModule`
  on purpose. Constructor injection only (D-16).
- `DataModule`: delete the `SetlistAssistant` binding, its four imports and the doc sentence about `AssistantCredentials`.
- `BluesJamApp.startKoin`: add `assistantCoreModule` to `modules(...)`.
- `AssistantCredentials` binding stays in `:app` `appModule` (`single { AssistantCredentials.of(BuildConfig.DEVEXPERT_KEY) }`);
  only its import changes to `com.bbbjam.core.assistant.AssistantCredentials`. `AssistantCredentials` stays public.
- Tests: `DataModuleTest` loses the `AssistantCredentials` binding (line ~48), the two imports and the "one assistant"
  assertions (lines ~87-90); a new `core/assistant/.../di/AssistantCoreModuleTest.kt` (koin-test, builds the module with fake
  `CatalogRepository`, `JamsRepository`, `JamCalendar` and `AssistantCredentials.of("")`) re-asserts singleton + `isConfigured == false`.
  The `DataModuleTest` test method stays (its other assertions remain), so counts only grow by the new test.

### Dependents

- `feature/assistant/build.gradle.kts`: add `implementation(project(":core:assistant"))`; keep `:core:data` (repositories and the
  narrow `Setlist*` contracts) and `:core:ui`. Update the build-file comment. Rewrite imports in the 29 feature main and test files
  that import `core.data.assistant` (grep list: `app/.../AppModule.kt` plus 28 under `feature/assistant`).
- `app/build.gradle.kts`: add `implementation(project(":core:assistant"))` (needs `assistantCoreModule` and `AssistantCredentials`).
- Debug/release overrides in `app/src/debug|release` do not reference the agent (grep returned none); recheck with a final grep.

### Konsist amendments (`ModuleIsolationTest.kt`), each shown failing first

Order: (a) edit the **tests** only for one rule at a time is not needed; instead, after moving the code with the **old**
rules, run `./gradlew :konsist-test:test` once and save the red output (expected: `core-import-allowlist`, `build-file-project-deps`,
`data-libraries-only-in-core-data(-qualified)` red; `assistant-reads-only` silently green because its filter matches no file, the
trap) to `docs/evidence/core-assistant-module/konsist-before.txt`. Then amend one rule at a time, rerun, and prove each can still fail.

1. `core-import-allowlist`: `allowed` also adds `packageRootOf(CORE_DATA)` when `import.modulePath == CORE_ASSISTANT`. No other edge.
2. `build-file-project-deps` (`isAllowedProjectDependency`): `modulePath == CORE_ASSISTANT -> dependency in setOf(":core:model", ":core:data")`,
   placed before the generic `CORE` branch. `core/data`, `core/ui` stay `:core:model` only, so nothing may depend on `:core:assistant` from core.
3. `data-libraries-only-in-core-data` and `-qualified`: replace `DATA_LIBRARY_PREFIXES` / `QUALIFIED_DATA_LIBRARY` use with the
   per-module allowlist above (violation = a prefix of the library set that is not in the module's allowlist). Keep the `-qualified`
   exemption of `konsist-test`. Rename the two `testName`s only if the names would lie; recommended: keep ids, update KDoc. The
   regex still lists all six prefixes.
4. `assistant-reads-only` (rule 18): `filter { it.modulePath == CORE_ASSISTANT }` (module, not package; `ASSISTANT_PACKAGE` constant
   removed or retargeted to `com.bbbjam.core.assistant`), same forbidden packages (`setlist`, `admin`, `remote`, `cache` under
   `com.bbbjam.core.data`) and same qualified regex. **Add a non-vacuity guard**: the rule fails if it scans zero files (the same
   trap that just made rule 18 pass silently). `core-import-allowlist` now lets the module import `core.data.*`, so this rule is the only guard.
5. Rules 19-22 untouched. Rule 19's `FEATURE_ASSISTANT` filter and `FEATURE_PUBLIC_API_ALLOWLIST` are unchanged (the feature adds no public name).
6. Failing-first proofs to save under `docs/evidence/core-assistant-module/`, each removed afterwards: (i) `import com.bbbjam.core.data.setlist.SetlistRepository` in a `core/assistant` main file -> `assistant-reads-only`; (ii) `import com.bbbjam.core.assistant.SetlistAssistant` in `core/ui` -> `core-import-allowlist`, and `project(":core:assistant")` in `core/ui/build.gradle.kts` -> `build-file-project-deps`; (iii) `import ai.koog...` in `core/data` and `import androidx.room.Dao` in `core/assistant` -> rules 1 and 2 (qualified twin with a fully qualified line); (iv) `import okhttp3.OkHttpClient` in `feature/assistant` -> rule 1. `git status` clean of the plants at the end.

## Expected file changes

- New: `core/assistant/{build.gradle.kts,.gitignore}`, `core/assistant/src/main/.../di/AssistantCoreModule.kt`, `core/assistant/src/test/.../di/AssistantCoreModuleTest.kt`.
- `git mv` main: AssistantCredentials, AssistantErrors, AssistantHttp, AssistantModel, AssistantPrompt, AssistantReply, AssistantStatus, AssistantStatusProbe, CatalogTool, DefaultSetlistAssistant, JamProposalValidator, JamScheduleTool, PastJamsTool, ProposalDto, ProposalStreamParser, ProposalValidator, SetlistAssistant, StreamingProposalStrategy.
- `git mv` test: AssistantCredentialsTest, AssistantFakes, AssistantLiveCallTest, AssistantPromptTest, AssistantStatusTest, CatalogToolTest, DefaultSetlistAssistantTest, DraftTrackerTest, JamProposalValidatorTest, JamScheduleToolTest, PastJamsToolTest, ProposalStreamParserTest, ProposalValidatorTest, SseFixtures, StreamingAssistantTest.
- Edited: `settings.gradle.kts`, `core/data/build.gradle.kts` (remove Koog, Ktor, `koogWithoutJackson`, the `assistantLive` property and its comment), `DataModule.kt`, `DataModuleTest.kt`, `feature/assistant/build.gradle.kts` and its 28 files' imports, `app/build.gradle.kts`, `app/.../AppModule.kt`, `BluesJamApp.kt`, `ModuleIsolationTest.kt`.
- Evidence: `docs/evidence/core-assistant-module/`.

## Implementation tasks (ordered)

1. Baseline: `CI=true ./init.sh`, record exit code and testcase total (last accepted: 1155) in `baseline.txt`; count `@Test`s per moved class.
2. Create the module skeleton, `settings.gradle.kts`, `git mv` main + test, fix package lines and imports. Do not touch Konsist yet.
3. Move Koog/Ktor/assistantLive from `core/data/build.gradle.kts`; add `AssistantCoreModule`, fix `DataModule`, `DataModuleTest`, add `AssistantCoreModuleTest`.
4. Update `feature/assistant` and `app` build files, imports, `startKoin`, `AppModule`.
5. Run `:core:assistant:testDebugUnitTest`, `:core:data:testDebugUnitTest`, `:feature:assistant:testDebugUnitTest`, `:app:assembleDebug`; fix compile only.
6. Konsist: save the "before" red run, then amend rules 1-4 one at a time (above), each with its failing proof.
7. Task 8 below, then docs, then the full gate.
8. `AssistantLiveCallTest` seeds from `Fixtures`/`CatalogMapper`/`JamsMapper` (all internal to `:core:data`). Default: replace the seed read with an in-code catalog and past jams built with `song(...)`, `jam(...)`, `jamSong(...)` from `AssistantFakes` (about a dozen songs including the three the Juan jam uses). The test stays opt-in and off in the gate; it only has to compile and skip. Do not export the mappers or `Fixtures`. Report that future live evidence is not byte-comparable with `docs/evidence/assistant-streaming/live-call.md`.

## Documentation impact

- `.claude/skills/architecture/SKILL.md`: update (module table row `:core:assistant` and `:core:data` row; "Dependency Rules" Koog/Ktor/okhttp3 allowlist sentence and the `assistant-agent-core` pointer paragraph, which must say the agent now lives in `:core:assistant` as of this slice; Konsist section: per-module library allowlist, `assistant-reads-only` retargeted and non-vacuous, rule count; Koog and Ktor pins "in `core/assistant/build.gradle.kts`"; plugin table row for `bluesjam.android.data`: "`:core:data`, `:core:assistant`"; Koin note; the `core/data` "not a `:core:assistant`" sentence). Keep the history paragraphs as history.
- `docs/architecture.md`: update only the module-specific Mermaid lines: add a `:core:assistant` node (label "Koog agent · tools · streaming"), edges `assistant --> coreassistant`, `coreassistant --> data & model`, remove "Koog agent" from the `:core:data` label (line ~22), `agent["Koog agent in :core:assistant..."]` (~57), the narrow-contract line stays, and the link at ~119 to the new path. Another session edited this file and `docs/diagrams/`: touch only those lines; the PNG is not regenerated (say so).
- `README.md` module list (line ~188 "agente" moves out of `:core:data`, new row); `docs/phase-2-brief.md` only if it names the package/module (line 54 concerns `:feature:assistant`; check with grep for `core.data.assistant`); `AGENTS.md` lists no modules, not needed. `PROGRESS.md`: add the implementer's entry only, never other sessions' entries. Never touch `presentations/`.

## Verification plan

- `rg "ai\.koog|io\.ktor" core/data` -> empty; `rg "core\.data\.assistant" --glob '!docs/**' --glob '!PROGRESS.md' .` -> empty outside history docs.
- `./gradlew :core:assistant:testDebugUnitTest :core:data:testDebugUnitTest :feature:assistant:testDebugUnitTest :konsist-test:test`, then `:app:assembleDebug :app:assembleRelease`.
- `CI=true ./init.sh` exits 0 with `konsist`, `detekt`, `ktlint` `wired`; also `./gradlew :app:dependencies --configuration debugRuntimeClasspath` diff shows no library added or version moved (the module only relocates Koog/Ktor).
- Test count: before and after per module; total = baseline + the new `AssistantCoreModuleTest` tests (state the exact number); no test removed or edited in behavior. Rule count: 22 before, 22 after (no new `@Test`, four amended), recorded in `rules.txt`. No device run needed; the agent has no UI.

## Evidence to capture

`baseline.txt`, `konsist-before.txt`, one failing-run file per proof in task 6, the greps, `final-init.txt` (tail with the three `wired` lines), test counts per module, rule count, `git status` showing renames (`R`), not delete+add.

## Validator checklist

- Moves are `git mv` renames; only package lines and imports differ in moved files (diff `-M`).
- No `ai.koog`/`io.ktor` in `:core:data`; `okhttp3.` allowed only in `core/data` and `core/assistant`; Room/DataStore not in `core/assistant`.
- Only the one new core edge (`core/assistant -> :core:data`); `:core:data` and `:core:ui` still `:core:model` only.
- `assistant-reads-only` scans the new module, is not vacuous, and each amended rule has a saved failing run; rules 19-22 unchanged; rule count recorded.
- `assistantCoreModule` listed in `startKoin`; credentials still bound in `:app`; no `get()`/`inject()` in classes.
- No internal `:core:data` type made public; live test compiles, skipped by default.
- Docs updated as listed; `presentations/` and other sessions' PROGRESS entries untouched.

## Open questions

1. Convention: `bluesjam.android.data` applies an unused KSP plugin to `:core:assistant`. Alternative is a new `bluesjam.android.serialization` convention (larger diff, touches `build-logic`). Spec assumes the former; confirm.
2. Live test seeds (task 8): in-code data (default) versus exposing seed loading through `:core:data` test fixtures (would need the mappers public or a public fixture parser). Spec assumes in-code.
