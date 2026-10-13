# Feature Implementation Spec: Koog setlist agent with catalog and past-jam tools

## Source Feature

- `id`: assistant-agent-core
- `area`: assistant
- `depends_on`: kotlin-2-3-toolchain-upgrade (`accepted`, 13 October 2026)
- `status`: not_started
- `source`: `feature_list.json`; `docs/phase-2-brief.md`; D-21 to D-24 in `bb-blues-jam-bitacora.md`

## Goal

A Koog 1.3.0 agent, running on the device and pointed at DevExpert Inference (OpenAI-compatible
chat completions, model alias `chat`), answers an admin request with a typed, catalog-validated
setlist proposal. It reads data only through two read-only tools (catalog, past jams). A
conversation keeps its turns in memory so the admin can refine. No screen: `:feature:assistant` is
the next slice (`assistant-admin-tab`).

## Non-Goals

- No UI, no tab, no presenter, no `:feature:assistant` module, no navigation.
- No write of any kind: no `SetlistRepository`, `AdminWriter`, `SetlistActionRegistry` or Apps
  Script call reachable from the agent (D-24). "Usar esta lista" is `assistant-apply-proposal`.
- No chat persistence across process restarts, no streaming, no MusicBrainz/enrichment (D-09).
- No `koog-agents` umbrella artifact (it pulls Anthropic, Bedrock/AWS, Ollama, OpenTelemetry and
  more). No key entry UI, no key obfuscation (D-22 accepts the risk).
- Do not upgrade Compose, Koin, Navigation, coroutines, OkHttp or serialization.

## Job Story

When I (the admin) describe the jam I want in one sentence, I want an ordered list taken only from
our catalog, with a short reason per song and questions when my request is ambiguous, so I can build
the setlist faster and refine it by talking (`build-brief.md`, fifth problem).

## Users And Permissions

Only the admin will reach it (next slice gates the tab). The agent authorizes nothing and writes
nothing; Apps Script stays the write authority. The key is a build-time secret (D-22).

## Acceptance Scenarios

1. **Given** `devexpert.key` is blank or absent, **when** a conversation sends a request, **then** it
   returns `AssistantReply.NotConfigured`, makes no HTTP request, and nothing throws.
2. **Given** a configured key and a scripted gateway that first answers with `tool_calls` for
   `searchCatalog` and `pastJams`, then with a final JSON message, **when** a request is sent,
   **then** both tools run against the fake repositories, the second request carries their results,
   and the reply is `Proposal` with the songs in the model's order.
3. **Given** the final JSON lists an id not in the catalog, a malformed id and a repeated id,
   **then** the proposal contains only catalog songs (first occurrence kept) and
   `Proposal.rejectedSongIds` lists the others; title, artist and key come from the catalog
   (`Song.title`, `Song.artist`, `Song.defaultKey`), never from the model (D-08, D-14).
4. **Given** the final message is not valid JSON or misses a required field, **then** the reply is
   `Failed(AssistantFailure.InvalidResponse)`, never a crash and never a partial list.
5. **Given** the connection fails, **then** `Failed(Offline)`; HTTP 401/403 → `Failed(Unauthorized)`;
   any other non-2xx → `Failed(Unavailable(status))`; the model keeps calling tools past the
   iteration cap → `Failed(TooManySteps)`. Coroutine cancellation is rethrown, never mapped.
6. **Given** a first turn answered, **when** the same conversation sends "cambiá el tercero por algo
   de Pappo", **then** the request includes the first user text and the first validated proposal
   before the new text.
7. **Given** any run, **then** the key appears in no log line, no `toString`, no evidence file.
8. **Given** the real gateway, **when** the brief's example request runs once (opt-in live test),
   **then** evidence records the endpoint path, whether tools were called and whether the final
   answer parsed against the schema; no Sheet write happens.

## Repository Research (inspected 13 October 2026)

- `settings.gradle.kts`: modules `:app`, `:core:{model,ui,data}`, three features, `:konsist-test`.
- `konsist-test/.../ModuleIsolationTest.kt`: `core-import-allowlist` lets a `:core:*` module import
  only its own root and `:core:model`; `build-file-project-deps` lets `core/*` depend only on
  `:core:model`; `data-libraries-only-in-core-data` (+ `-qualified`) keeps `okhttp3.`,
  `androidx.room.`, `kotlinx.serialization.`, `androidx.datastore.` in `:core:data`.
- `core/data`: `CatalogRepository.observeCatalog(): Flow<CatalogSnapshot(songs: List<Song>, …)>`;
  `JamsRepository.observeJams(): Flow<JamsSnapshot(upcoming, past, freshness)>` (past: newest
  first, filled slots only); `AppsScriptEndpoint` (hides its URL in `toString`, the pattern to
  copy); `di/DataModule.kt`; `OkHttpAppsScriptTransport.client()`; `DataModuleTest`.
- `core/model`: `Song`, `SongId.parseOrNull`, `Key`, `Jam`, `JamSong` (`lineup`,
  `extraParticipants`), `Slot.musicianName`, `Setlist.Available`.
- `app/build.gradle.kts`: `local.properties` → `buildConfigField("String", "APPS_SCRIPT_URL", …)` in
  `defaultConfig` (escaped); `appModule` binds `AppsScriptEndpoint.of(BuildConfig.APPS_SCRIPT_URL)`.
  `local.properties` already has a `devexpert.key` line (value not read by the planner).
- `build-logic/.../ProjectExtensions.kt` `configureAndroidCommon`: Java 11 for every Android module;
  `AndroidDataConventionPlugin` (`bluesjam.android.data`) applies to `:core:data` only.
- Koog 1.3.0 on Maven Central (module metadata and `-sources.jar` downloaded and read):
  - `agents-core-android` runtime deps: `agents-tools`, `prompt-*`, `prompt-structure`,
    **`prompt-executor-openai-client`**, `kotlinx-io-core` 0.9.0, serialization-json 1.10.0,
    `ktor-client-content-negotiation`/`-logging`/`ktor-serialization-kotlinx-json` 3.3.3,
    **`ktor-server-sse` and `ktor-server-cio` 3.3.3**, `coroutines-jdk9` 1.10.2,
    **`serialization-jackson`** (→ `jackson-module-kotlin` 2.21.3), `kotlin-logging` 8.0.01,
    stdlib/bom 2.3.10. `agents-tools` adds `kotlin-reflect` and `kotlinx-schema-generator-json`
    0.4.4; `prompt-executor-clients` adds `org.slf4j:slf4j-simple` 2.0.17. No HTTP engine.
  - `http-client-ktor-android`: `KtorKoogHttpClient.Factory(baseClient: HttpClient = HttpClient(), …)`.
  - `koog-agents-android` (umbrella) adds `ktor-client-okhttp` and ~25 more Koog modules; not used.
  - No Compose, lifecycle or AndroidX artifact anywhere in the set. Every aar declares
    `minSdkVersion=23`, `coreLibraryDesugaringEnabled=false`.
  - **Every class file is Java 17 bytecode (major 61)**; this project compiles Android at Java 11.
  - `ktor-client-okhttp-jvm` 3.3.3 asks OkHttp 5.2.1, okio 3.16.2, stdlib 2.2.21: all resolve up to
    what the project has (OkHttp 5.5.0, okio 3.18.1, stdlib 2.3.21).
- Koog APIs this spec relies on (file paths inside the 1.3.0 sources jars):
  - `OpenAIClientSettings(baseUrl, timeoutConfig, chatCompletionsPath = "v1/chat/completions", …)`
    and `OpenAILLMClient(apiKey, settings, httpClientFactory: KoogHttpClient.Factory)`
    (`prompt-executor-openai-client/.../OpenAILLMClient.kt` l. 88–135).
  - Endpoint choice: `determineParams` picks chat completions when the model has
    `LLMCapability.OpenAIEndpoint.Completions` (l. 1060–1080); Responses only if declared.
  - `LLModel(provider = LLMProvider.OpenAI, id, capabilities, …)` (`prompt-llm/.../LLModel.kt`).
  - `Tool<TArgs, TResult>` / `SimpleTool<TArgs>(argsType, name, description)` with
    `suspend fun execute(args)`; `ToolRegistry { tool(…) }` (`agents-tools`).
  - `structuredOutputWithToolsStrategy<Output>(StructuredRequestConfig(default =
    StructuredRequest.Native(JsonStructure.create<Output>())))`
    (`agents-core/.../ext/agent/AIAgentStrategies.kt` l. 190–260): LLM ↔ tools loop, then parses
    the final text into `Output`. `StructuredRequest.Manual` puts the schema in the prompt instead.
  - `GraphAIAgent(promptExecutor, agentConfig = AIAgentConfig(prompt, model, maxAgentIterations,
    serializer = KotlinxSerializer()), strategy, toolRegistry)`; `MultiLLMPromptExecutor(mapOf(
    LLMProvider.OpenAI to client))`.
  - Response JSON uses `ignoreUnknownKeys = true` (`AbstractOpenAILLMClient` l. 116), so the
    gateway's `reasoning_content` parses; it is also sent back as `reasoning_content` on later
    assistant messages (l. 322) — see Risks.
  - Errors: `KoogHttpClientException(statusCode, errorBody)`, `LLMClientException`,
    `LLMStructuredParsingError`, `AIAgentMaxNumberOfIterationsReachedException`.
  - Test kit: `ai.koog:agents-test` (`MockPromptExecutor`, `getMockExecutor { … }`), depends on
    `kotlin-test`.

## Technical Approach

### Decision 1 — Placement: package `com.bbbjam.core.data.assistant` in `:core:data`, no new module

A `:core:assistant` module depending on `:core:data` would violate `core-import-allowlist` and
`build-file-project-deps`, and its `@Serializable` DTOs would violate the data-library rules: three
enforced rules would have to be relaxed. `:core:data` already owns HTTP, JSON and the repositories
the tools read, so the agent goes there with no relaxation. D-23 ("outside the feature module") holds.
The public surface exposes **no Koog, Ktor or serialization type**, so the next slice's feature sees
plain Kotlin types only.

### Decision 2 — Artifacts (catalog entries, `implementation` in `core/data/build.gradle.kts` only)

```
koog = "1.3.0"; ktor = "3.3.3"
koog-agents-core      = ai.koog:agents-core            (version.ref koog)
koog-http-client-ktor = ai.koog:http-client-ktor       (version.ref koog)
ktor-client-okhttp    = io.ktor:ktor-client-okhttp     (version.ref ktor)
koog-agents-test      = ai.koog:agents-test            (testImplementation, optional)
```
The Ktor client uses the OkHttp engine over a client built from the project's OkHttp
(`HttpClient(OkHttp) { engine { preconfigured = … } }`, read timeout 90 s, call timeout 120 s), and
`KtorKoogHttpClient.Factory(baseClient = thatClient)` is passed explicitly to `OpenAILLMClient`, so
Koog's `ServiceLoader` resolution is never used. Do **not** exclude `ktor-server-*`, Jackson or
slf4j-simple in this slice unless packaging fails; record the classpath diff instead (Risks).

### Decision 3 — Java 17 for `:core:data`

Koog ships Java 17 bytecode, and the agent calls Koog inline reified functions
(`JsonStructure.create<T>()`, `structuredOutputWithToolsStrategy<T>()`), which Kotlin refuses to
inline into a Java 11 target. Task 1 first reproduces the error at Java 11 and saves the output.
Then `AndroidDataConventionPlugin` sets `compileOptions` source/target to `VERSION_17` after the
common setup (module build files may not set `JavaVersion`; Konsist `build-file-applies-convention`).
Confirm `:core:data`'s Kotlin `jvmTarget` follows (AGP built-in Kotlin); if AGP rejects mixed levels,
raise `configureAndroidCommon` to 17 for all Android modules instead, and say so in the evidence.
minSdk 24 is unaffected (D8 desugars). JVM modules stay at 11.

### Decision 4 — Key wiring (D-22)

`app/build.gradle.kts` reads `devexpert.key` (trimmed, `""` when absent) and adds
`buildConfigField("String", "DEVEXPERT_KEY", …)` in `defaultConfig` (so every build type), escaped as
`APPS_SCRIPT_URL` is. Public `AssistantCredentials.of(raw)` in `:core:data` (`isConfigured` = non
blank; `toString()` = `AssistantCredentials(configured|not configured)`; key `internal`). `appModule`
binds `single { AssistantCredentials.of(BuildConfig.DEVEXPERT_KEY) }`. Never log it.

### Decision 5 — Model and gateway

`internal object AssistantModel`: `BASE_URL = "https://inference.devexpert.io"`, path
`"v1/chat/completions"`, `MODEL_ID = "chat"` (the one constant to change),
`LLModel(LLMProvider.OpenAI, "chat", capabilities = [Completion, Temperature, Tools, ToolChoice,
Schema.JSON.Standard, OpenAIEndpoint.Completions])` — **never `OpenAIEndpoint.Responses`**, so
`/v1/responses` is unreachable. `maxAgentIterations = 12`. Fallback if the live call shows the
gateway rejecting Koog's request shape: keep Koog's agent and tools but replace only the
`LLMClient` with an internal one over OkHttp + kotlinx-serialization (`/v1/chat/completions`,
`tools`, `response_format`), behind the same `PromptExecutor`; record why.

### Decision 6 — Structured output

Strategy `structuredOutputWithToolsStrategy<ProposalDto>` with `StructuredRequest.Native`
(`response_format: json_schema`, verified by raw curl). If the live call shows the model skipping
tools or the gateway refusing `tools` + `response_format` together, switch to
`StructuredRequest.Manual` (schema in the prompt, same parser) and record it. Internal DTOs,
all fields required, no defaults/nullables (strict schema):
`ProposalDto(songs: List<ProposedSongDto>, message: String, questions: List<String>)`,
`ProposedSongDto(songId, title, artist, key, reason: String)`, with `@LLMDescription`s.

### Decision 7 — Tools (read-only, compact)

Both are `SimpleTool<Args>` built with `CatalogRepository` / `JamsRepository` only, reading the
cache with `observe…().first()`:
- `searchCatalog(query: String)` — blank query = whole catalog. Match is case- and
  accent-insensitive on title or artist. One line per song: `id | title | artist | key | tempo`
  (tempo omitted when null), Sheet order, header line with the count. Empty cache → a line saying
  the catalog is not loaded.
- `pastJams(musician: String)` — blank = all. Past jams newest first, at most 24; per jam a
  `fecha YYYY-MM-DD` line, then per song `id | title | artist | key | players: Name (instrument), …`
  including extras (`Name (saxo)`); instrument = lowercase enum name. With a musician, only songs
  whose slot or extra name matches (same normalization), and jams with none are omitted.
No `upcoming` jam, no draft, no admin data in any tool.

### Decision 8 — Public API (`com.bbbjam.core.data.assistant`)

```kotlin
interface SetlistAssistant { val isConfigured: Boolean; fun startConversation(): AssistantConversation }
interface AssistantConversation { suspend fun send(request: String): AssistantReply }
sealed interface AssistantReply {
  data object NotConfigured : AssistantReply
  data class Proposal(val songs: List<ProposedSong>, val message: String,
                      val questions: List<String>, val rejectedSongIds: List<String>) : AssistantReply
  data class Failed(val failure: AssistantFailure) : AssistantReply
}
data class ProposedSong(val songId: SongId, val title: String, val artist: String, val key: Key, val reason: String)
sealed interface AssistantFailure { Offline; Unauthorized; Unavailable(status: Int); InvalidResponse; TooManySteps; CatalogEmpty }
```
`DefaultSetlistAssistant(credentials, catalog, jams, executorFactory)` (a Koin `single`; a
conversation is a plain object, not bound). `send` runs one agent per turn under a `Mutex`; the
prompt is the system prompt (English text instructing Rioplatense Spanish with *vos* for `reason`,
`message`, `questions`; catalog-only; use the tools; songs may be empty when asking questions) plus
up to the last 6 turns as `user` / `assistant` (the validated proposal re-encoded compactly), then
the new request. Only answered turns enter history. An empty catalog cache → `Failed(CatalogEmpty)`
before any request. Order of checks in `send`: key missing → `NotConfigured`; then
`require(request.isNotBlank())` (a caller bug, documented in KDoc; the next slice disables sending
blank text); then empty catalog → `Failed(CatalogEmpty)`; only then a request.
Error mapping walks the cause chain: `IOException` → `Offline`; `KoogHttpClientException` 401/403
→ `Unauthorized`, other → `Unavailable(status)`; `LLMStructuredParsingError`,
`SerializationException`, unparseable content → `InvalidResponse`;
`AIAgentMaxNumberOfIterationsReachedException` → `TooManySteps`. Catch only those types (plus
`LLMClientException`/`AIAgentException` to unwrap); never `Exception`, `RuntimeException`,
`IllegalStateException` or `Throwable`; `CancellationException` always propagates.
`ProposalValidator` (pure, internal) does Acceptance 3 and trims reasons/questions.

### Decision 9 — Konsist (two tightenings; rule count 17 → 18)

1. Add `ai.koog.` and `io.ktor.` to `DATA_LIBRARY_PREFIXES` and `QUALIFIED_DATA_LIBRARY`: no module
   but `:core:data` may touch the agent runtime.
2. New rule `assistant-reads-only`: files under `com.bbbjam.core.data.assistant` (main and test)
   import nothing from `com.bbbjam.core.data.setlist`, `.admin`, `.remote` or `.cache`, also as a
   fully qualified text match (D-24). Both must be shown failing (temporary violation, output saved,
   then reverted).

## Expected File Changes

- `gradle/libs.versions.toml` — `koog`, `ktor` versions and four libraries, with a pin comment.
- `build-logic/.../AndroidDataConventionPlugin.kt` — Java 17 (Decision 3).
- `core/data/build.gradle.kts` — Koog/Ktor deps; test task system property `bbbjam.assistantLive`
  from the Gradle property of the same name (default `false`).
- `core/data/src/main/kotlin/com/bbbjam/core/data/assistant/` — `SetlistAssistant.kt`,
  `AssistantConversation.kt`, `AssistantReply.kt` (+ `ProposedSong`, `AssistantFailure`),
  `AssistantCredentials.kt`, `AssistantModel.kt`, `AssistantPrompt.kt`, `ProposalDto.kt`,
  `ProposalValidator.kt`, `CatalogTool.kt`, `PastJamsTool.kt`, `DefaultSetlistAssistant.kt`,
  `AssistantHttp.kt` (Ktor/OkHttp client and executor factory), `AssistantErrors.kt`.
- `core/data/.../di/DataModule.kt` — `single<SetlistAssistant>`; needs `AssistantCredentials` from `:app`.
- `core/data/src/test/.../assistant/` — tests listed below; `DataModuleTest` binds credentials.
- `app/build.gradle.kts` — `DEVEXPERT_KEY`; `app/.../di/AppModule.kt` — credentials binding.
  `packaging.resources.excludes` only if `:app` packaging fails on duplicate `META-INF` files.
- `konsist-test/.../ModuleIsolationTest.kt` — Decision 9.
- Docs (below). No change to `init.sh`, `config/detekt/detekt.yml`, `.editorconfig`, any feature module.

## Visual Design Impact

None: no UI in this slice. `DESIGN.md` not read for this reason.

## Durable Documentation Impact

- `.claude/skills/architecture/SKILL.md` — update: "As built by `assistant-agent-core`" paragraph
  (package, public API, D-24 rule), Koog/Ktor pins, Java 17 for `:core:data`, data-library prefixes,
  rule count 18, `DEVEXPERT_KEY` beside the Apps Script URL bullet.
- `docs/technical-discovery.md` — update: Koog artifact set, gateway findings from the live call.
- `docs/risks-and-open-questions.md` — update: key in APK (D-22), gateway/alias dependence,
  dependency weight, live-call outcome.
- `CONTEXT.md` — update the Assistant entry with "Proposal" (validated, catalog-only list).
- `ARCHITECTURE.md`, `CONSTRAINTS.md` — not present; not needed. `AGENTS.md` — not needed (no rule or
  command changes). `docs/phase-2-brief.md` — not needed unless a fallback in Decision 5/6 is taken
  (then note it under Open risks).

## Implementation Plan / Tasks

1. Baseline `CI=true ./init.sh` (record test count). Add catalog entries and deps; save
   `:app:dependencies --configuration debugRuntimeClasspath` before/after. Confirm no Compose,
   lifecycle, coroutines, serialization, OkHttp downgrade or move.
2. Write one inline Koog call at Java 11, save the compiler error; apply Decision 3; build.
3. `AssistantCredentials` + BuildConfig + Koin binding + tests.
4. DTOs, `ProposalValidator` + tests. Tools + tests (fake repositories, compact format, filters).
5. `AssistantHttp`, `AssistantModel`, `DefaultSetlistAssistant`, error mapping; MockWebServer tests.
6. Konsist tightenings, each shown failing then passing.
7. Live call (opt-in), evidence. Apply a Decision 5/6 fallback only if the live call requires it.
8. Docs; `CI=true ./init.sh`; `./gradlew :app:assembleDebug :app:assembleRelease`; update
   `feature_list.json` (status `passing`, evidence) and `PROGRESS.md`. Do not commit.

## Verification Plan (JVM unless stated)

- `AssistantCredentialsTest`: blank/whitespace/absent → not configured; `toString` never contains the key.
- `ProposalValidatorTest`: unknown, malformed (`SongId.parseOrNull` null) and repeated ids dropped
  and listed in order; catalog fields override the model's; empty songs with questions is valid.
- `CatalogToolTest`, `PastJamsToolTest`: exact line format, accent/case-insensitive filters, extras
  counted, 24-jam cap, empty-cache text, no upcoming jam ever listed.
- `DefaultSetlistAssistantTest` with **MockWebServer** (already a `:core:data` test dep; localhost
  only) serving scripted chat-completions JSON through the real Koog client: Acceptance 1–6.
  Assert on the recorded requests: path `/v1/chat/completions`, `model == "chat"`, `tools` holds
  both names, `response_format.type == "json_schema"` (Native), `Authorization: Bearer <fake>`, the
  second request carries a `tool` role message, refinement history order. `NotConfigured` and
  `CatalogEmpty` assert `requestCount == 0`. A cancelled `send` throws `CancellationException`.
- `DataModuleTest`: resolves one `SetlistAssistant`; with `AssistantCredentials.of("")`,
  `isConfigured == false`.
- Konsist: 18 rules green; failing runs for both Decision 9 changes saved.
- **Live (opt-in, outside the gate)**: `AssistantLiveCallTest`, skipped by `Assume` unless system
  property `bbbjam.assistantLive == "true"`; reads the key itself from
  `${bbbjam.rootDir}/local.properties` (never through Gradle). Run:
  `./gradlew :core:data:testDebugUnitTest --tests '*AssistantLiveCallTest' -Pbbbjam.assistantLive=true --rerun`.
  Fake in-memory repositories seeded from `docs/sheet-seed` / `docs/api-samples` (no Sheet access,
  no write path exists). Request: the brief's example verbatim. An OkHttp interceptor records path,
  `tools`/`response_format` presence, tool names called and final content; stdout/stderr are
  captured and the test asserts the key substring appears nowhere. Writes
  `docs/evidence/assistant-agent-core/live-call.md` (no key, no Authorization header).
- Gate: `CI=true ./init.sh` exit 0, konsist/detekt/ktlint `wired`, test count ≥ baseline + new tests;
  `:app:assembleDebug` and `:app:assembleRelease` succeed (D8 over Java 17 Koog, minSdk 24).
- No device step in this slice (no UI); the next slice's device check is the Android runtime proof.

## Evidence To Capture (`docs/evidence/assistant-agent-core/`)

Dependency classpath diff; the Java 11 inline error; Konsist failing runs; gate output and test
count; assemble output; `live-call.md` (endpoint, tools called yes/no, structured output honored
yes/no, fallback taken, latency, the validated proposal, rejected ids). APK size before/after.

## Risks

- **Java 17 bytecode** (Decision 3) — a build-convention change; the architecture skill's "Java 11
  everywhere" becomes "Java 11, `:core:data` 17". Orchestrator: confirm this is acceptable.
- **Dependency weight on Android**: `ktor-server-cio/sse`, Jackson + `kotlin-reflect`, slf4j-simple,
  `coroutines-jdk9` (references `java.util.concurrent.Flow`, API 30; only loaded if used) land in the
  APK. Release is not minified (`optimization.enable = false`). Possible duplicate `META-INF` packaging.
- **Gateway compatibility**: tools + `response_format` together, and `reasoning_content` echoed back
  in later turns, were not verified by curl. The live call decides; Decisions 5/6 give fallbacks.
- **Logging**: slf4j-simple prints to stderr (logcat on device); Koog may log prompts (musician
  names). The live test checks the key; names in debug logs are a known limit to note.
- Alias `chat` may change gateway-side; tags are empty (D-20), so mood comes from model knowledge.
- `agents-test` pulls `kotlin-test`; if it clashes with JUnit 4, drop it (MockWebServer suffices).

## Validator Checklist

- [ ] No new module; agent in `com.bbbjam.core.data.assistant`; public API has no Koog/Ktor/serialization type.
- [ ] Only `agents-core`, `http-client-ktor`, `ktor-client-okhttp` (+ optional `agents-test`) added; no downgrade.
- [ ] Model declares `OpenAIEndpoint.Completions`, not `Responses`; recorded path is `/v1/chat/completions`.
- [ ] Tools take only `CatalogRepository`/`JamsRepository`; `assistant-reads-only` rule exists and was shown failing.
- [ ] Every returned song is in the catalog with catalog title/artist/key; rejected ids reported.
- [ ] Missing key → `NotConfigured`, zero requests; key in no log/`toString`/evidence.
- [ ] No catch of `Exception`/`RuntimeException`/`IllegalStateException`/`Throwable`; cancellation propagates.
- [ ] Live evidence exists and states tool-calling and structured-output outcomes honestly.
- [ ] `CI=true ./init.sh` green with three tools wired; assembles succeed; docs updated.

## Implementer Notes (13 October 2026)

- **Orchestrator amendment to Decision 3 (Java level):** no mixed Java levels. The Java 11 inline compile
  error was reproduced and saved first (`docs/evidence/assistant-agent-core/java11-inline-error.txt`),
  then every module was raised to Java 17 through the convention plugins (`configureAndroidCommon`
  and the JVM library plugin), not only `AndroidDataConventionPlugin`. The architecture skill now says
  "Java 17 everywhere" with a dated note.
- **Decision 6 fallback taken:** the live call showed the model skipping tools with `response_format`
  (Native), so the agent uses `StructuredRequest.Manual`. Decision 5's fallback was not needed.
- **Decision 2 deviation:** Jackson is excluded on the two Koog dependencies; dexing failed at minSdk 24
  (`assemble-failure-jackson.txt`). `ktor-server-core/cio/sse` 3.3.3 are on the debug and release runtime classpaths and in the APK (Decision 2 did not exclude them; they are unused).
- The Koog output-format instruction is a user message placed after the history and before the new
  request, so `roles` of a refinement are system, user, assistant, user (instruction), user (new text).
- **Cancellation test (repair):** `send` records its own outcome inside the `async`, then the test cancels
  and joins and asserts the outcome is a failure with `kotlinx.coroutines.CancellationException`. It passed
  against the real code (Koog does not wrap cancellation, no extra `ensureActive` was needed) and failed when
  production mapped the cancellation to `Failed(Offline)` (`cancellation-test-failing-run.txt`).
