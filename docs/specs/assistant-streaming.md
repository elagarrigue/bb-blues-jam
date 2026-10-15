# Feature Implementation Spec: Stream the assistant's answer, with a loading animation

## Source Feature

- `id`: assistant-streaming
- `area`: assistant
- `depends_on`: `jam-date-copy-polish` (accepted)
- `status`: not_started at planning time
- `source`: `feature_list.json` (last entry)

## Goal

While the agent works, the pending reply shows an animated loading indicator instead of the
`Armando la propuesta…` text. Once the final answer starts streaming, each proposed song appears as soon
as its JSON object is complete, already validated against the catalog (D-14). When the answer is complete,
the final parse of the whole text replaces the streamed list and only then do the targets
(`Agregar…`, `Reemplazar…`, `Crear…`) appear. A broken or cancelled stream shows what arrived, marked as
incomplete, with `Reintentar`, and never enables a write.

Three Parts, each ending with a green `CI=true ./init.sh`: **A** loading indicator (UI only, no streaming),
**B** streaming core in `:core:data`, **C** streamed songs and the incomplete state in `:feature:assistant`.
A is shippable alone; if B's first step (the spike) fails, stop after A and report: that is the documented
fallback (the animation without streaming).

## Non-Goals

- Streaming the closing `message` or the `questions` text character by character (they appear with the
  final answer). Streaming reasoning text or tool activity to the UI.
- Any write, any change to the targets, `ProposalApplier`, the registry or Apps Script (D-24, D-13).
- Native structured output (`StructuredRequest.Native`) or a new model/gateway (D-21; Manual stays).
- Any other loading state in the app (`SkeletonList`, `Comprobando el servicio…` stay static).
- A Compose UI test harness (declined, T1).

## Job Story

When I ask the assistant for a setlist and it takes 10-30 s, I want to see that it is working and
watch the songs appear as they come, so I can start reading the proposal before it is complete without
risking loading a half answer.

## Users And Permissions

- Admin only (the Asistente tab). Streaming is read-only; the agent package stays read-only (D-24,
  Konsist `assistant-reads-only`, unchanged). Targets are offered only on a complete, validated answer.

## Acceptance Scenarios

1. **Loading**: Given a sent request with nothing streamed, Then the reply card shows the three-dot
   indicator and `Cancelar`, no visible status text; TalkBack reads one polite node
   "El asistente está armando la propuesta. Puede tardar unos segundos." (`PENDING_LABEL`).
2. **Reduced motion**: Given the system animator duration scale is 0 ("Quitar animaciones"), Then the
   dots are drawn static (same layout, no motion), label unchanged.
3. **Songs stream**: Given the final answer is streaming, When a song's JSON object closes and its id is
   a catalog song not yet listed, Then it appears under `PROPUESTA` with the catalog's title, artist and
   key and the model's reason, numbered in arrival order; the indicator stays below the last song; no
   target button, no message, no questions yet.
4. **Unknown/malformed/repeated ids** never appear while streaming (the final reply's discarded note counts them).
5. **Complete**: When the stream ends and the full text parses, Then the turn shows the final validated
   proposal (exactly what `send` returns today, which replaces the streamed list even if they differ),
   then the message, questions and targets, as before this slice.
6. **Tool rounds**: Given the model calls tools first, Then the indicator shows during every round;
   text streamed in a round that ends in tool calls is discarded (the draft resets to empty).
7. **Broken stream**: Given a stream that fails after N≥1 streamed songs (socket closed, no end frame,
   HTTP error mid-way, invalid final JSON), Then the turn shows those N songs under
   `PROPUESTA INCOMPLETA · N temas`, the line `La respuesta se cortó antes de terminar. Esto es lo que llegó; no está completa.`
   (or the failure's own line when the cause is known, see Copy), `Reintentar` on the last turn, and
   **no** targets. With N = 0 it is the existing failed reply.
8. **Cancel**: When the admin taps `Cancelar` mid-stream, Then the HTTP call is cancelled, the turn
   keeps the streamed songs under the incomplete heading with `Cancelaste este pedido.` and `Reintentar`;
   no target. `Reintentar` replaces the turn (existing rule) and the new turn starts empty.
9. **History**: only a complete validated proposal enters the conversation history (unchanged).

## Repository Research

### Files Inspected

- `feature_list.json`, `PROGRESS.md`, `AGENTS.md`, `DESIGN.md` (M1 motion rule, list states, Asistente,
  targets), `.claude/skills/architecture/SKILL.md`.
- `core/data/.../assistant/DefaultSetlistAssistant.kt` (agent built per turn with
  `structuredOutputWithToolsStrategy<ProposalDto>` + `StructuredRequest.Manual`, catches, history),
  `AssistantHttp.kt` (OkHttp → Ktor → `KtorKoogHttpClient.Factory`, 60 s call timeout),
  `AssistantModel.kt`, `AssistantErrors.kt`, `SetlistAssistant.kt`, `AssistantReply.kt`,
  `ProposalDto.kt` (`songs` is the first field), `ProposalValidator.kt`, `AssistantPrompt.kt`.
- `core/data/src/test/.../assistant/DefaultSetlistAssistantTest.kt` (MockWebServer + real executor).
- `feature/assistant/.../AssistantChat.kt`, `AssistantUiModel.kt`, `AssistantMapping.kt`,
  `AssistantTurn.kt`, `AssistantScreen.kt` (scroll `LaunchedEffect`), `AssistantCopy.kt`,
  `AssistantPresenter.kt`, `ApplyMapping.kt`/`JamMapping.kt` (targets hidden while any turn is Pending),
  test `FakeSetlistAssistant.kt`.
- Koog 1.3.0 Android AARs in the Gradle cache (`ai.koog/*-android/1.3.0`), read with `javap` (no sources jar).

### Koog 1.3.0 streaming, as found in the bytecode (findings, not guesses)

- `PromptExecutor.executeStreaming(prompt, model, tools): Flow<StreamFrame>`; the OpenAI client
  (`AbstractOpenAILLMClient.executeStreaming`) **sends the tools** (`toOpenAIChatTool`, tool choice), posts
  over `KoogHttpClient.sse`, decodes each `data:` until `[DONE]`, and ends with `requireEndFrame`
  (a flow without `StreamFrame.End` throws `ai.koog.prompt.streaming.IncompleteStreamException`).
  Exceptions are wrapped in `LLMClientException`.
- `OpenAILLMClient.processStreamingResponse` maps `delta.content` → `TextDelta`, `reasoning_content` →
  `ReasoningDelta`, `tool_calls` → `ToolCallDelta` (assembled into `ToolCallComplete(id, name, content)`
  by `StreamFrameFlowBuilder`), `finish_reason` + `usage` → `End(finishReason, metaInfo)`.
- `KtorKoogHttpClient.Factory` installs Ktor's `SSE` plugin itself.
- Agent side: `functionalStrategy<Input, Output>(name) { input -> … }` exists. Inside it,
  `llm.writeSession { requestLLMStreaming(definition) }` sets the prompt to prompt + the structure
  definition, then streams **with the session's tools**; `requestLLMStreaming()` streams without
  appending. Streaming calls **do not** append the answer to the prompt: Koog's own
  `llmRequestStreamingAndSendResults` collects the frames, calls `StreamFrameExt.toMessageResponse(frames)`
  (→ `Message.Assistant` with text and tool-call parts) and appends it. `PromptBuilder.message(Message)` and
  `toolResult(MessagePart.Tool.Result)` exist; the context has `getToolCalls(Message.Assistant)`,
  `executeTools(calls, parallel)` and `ReceivedToolResult.toMessagePart()`; the write session has
  `parseResponseToStructuredResponse(message, StructuredRequestConfig, fixingParser)`, the same parse the
  current strategy's Manual mode uses.
- Not verified (no sources): whether `maxAgentIterations` limits a functional strategy. The spec
  therefore counts LLM calls itself.

### Gateway (probe of 15 October 2026, `docs/evidence/assistant-streaming/gateway-probe.md`)

SSE works for `chat` with and without tools; tool-call deltas stream (id + name first, then argument
fragments, parallel calls by index); `finish_reason` arrives in its own chunk (`stop` / `tool_calls`);
`usage` arrives in a final chunk when `stream_options.include_usage` is set. The model reasons first
(`reasoning_content`, `reasoning`, `reasoning_details` with `content: ""`), and content chunks split the JSON
anywhere.

### Existing Patterns To Follow

- `AssistantChat` (Koin `single`, own scope) owns turns; presenters stay stateless over it (D-02).
- Pure mapping `assistantModel(...)` + Molecule tests with `FakeSetlistAssistant`.
- `…Defaults` objects hold colours so JVM tests can read them; no dp/colour literal outside `:core:ui`.
- Catch only named exception types; rethrow `CancellationException` (architecture: refresh robustness).

## Technical Approach

### Part A: loading indicator (`:core:ui` + `:feature:assistant`)

- `:core:ui` package `com.bbbjam.core.ui.state`: `LoadingDots(description: String, modifier)`. Three
  circles, diameter `spacing.sm` (8dp), `spacing.xs` apart, colour `BluesJamTheme.colors.textMuted`
  (never amber: loading is not an amber role, D-17). Each dot's alpha follows the pure
  `loadingDotAlpha(elapsedMillis, index): Float` (period `LoadingDotsDefaults.PERIOD_MILLIS = 1200`,
  per-dot delay `STAGGER_MILLIS = 200`, alpha between `MIN_ALPHA = 0.3f` and `1f`), driven by one
  `rememberInfiniteTransition`. With reduced motion every dot is drawn at `STATIC_ALPHA = 0.6f` and
  no transition is created.
- Reduced motion: `rememberReducedMotion(): Boolean` in the same package reads
  `Settings.Global.ANIMATOR_DURATION_SCALE` (default 1) through `LocalContext` once per composition
  entry; pure `isReducedMotion(scale: Float) = scale == 0f`. Do not rely on Compose to stop an
  infinite transition by itself.
- Semantics: one node, `contentDescription = description`, `liveRegion = Polite`; the dots are not
  focusable separately. Internal `LoadingDotsDefaults` holds the constants and the colour role.
- `ReplyUiModel.Pending` becomes `Pending(loadingLabel, cancelLabel, events)` (drop `status`/`hint`;
  part C adds `songs`). `AssistantCopy.PENDING_LABEL = "El asistente está armando la propuesta. Puede tardar unos segundos."`
  replaces `PENDING_STATUS`/`PENDING_HINT`. `PendingContent` draws `LoadingDots(loadingLabel)` then
  `Cancelar`. `AssistantPreview` updated.
- `DESIGN.md`: amend M1 ("No other motion" → "except the assistant's loading dots") and the Asistente
  Pending bullet; record the WCAG 2.2.2 reasoning: it is the only moving element, shown only while a
  request runs, stopped by `Cancelar`, static under reduced motion.

### Part B: streaming core (`:core:data`, package `assistant`)

- **Public API (plain Kotlin)**: `AssistantConversation.send(request: String, onDraft: (AssistantDraft) -> Unit = {}): AssistantReply`.
  `data class AssistantDraft(val songs: List<ProposedSong>)`: the cumulative catalog-validated songs of
  the current answer, in arrival order; called again with an empty list when a round turns out to be a
  tool round. Calls come from the request's coroutine, in order, never after `send` returns.
  `AssistantFailure` gains `data object Incomplete` (the stream ended without its end frame).
  The return value is unchanged in meaning: the final, fully parsed and validated reply is the source of truth.
- **`StreamingProposalStrategy.kt`** (internal): replaces `structuredOutputWithToolsStrategy` in
  `runAgent` with `functionalStrategy<String, ProposalDto>`. Loop, at most
  `AssistantModel.MAX_AGENT_ITERATIONS` LLM calls:
  1. first call: `appendPrompt { user(input) }`, then `requestLLMStreaming(JsonStructure.create<ProposalDto>())`
     (the Manual definition, appended once); later calls `requestLLMStreaming()`.
  2. collect the frames into a list; feed each `TextDelta.text` to a `ProposalStreamParser`; for each
     completed song object, validate (below) and call `onDraft` when the accepted list grew.
  3. `toMessageResponse(frames)` → append with `message(...)`. If it has tool calls: `onDraft(empty)` if
     anything was emitted, reset the parser, `executeTools(calls)`, append each result with
     `toolResult(result.toMessagePart())`, next iteration. Otherwise parse the message with
     `parseResponseToStructuredResponse(message, StructuredRequestConfig(default = Manual(structure)), null)`
     and return the DTO.
  4. Past the cap throw a private `TooManyRoundsException` mapped to `TooManySteps`.
  Exact Koog member names are as listed in Research; if one differs, use the equivalent and record it.
- **`ProposalStreamParser`** (internal, pure, no Koog): `feed(chunk: String): List<String>` returns the
  raw text of each `songs` element object completed by that chunk. A character state machine: ignore
  everything before the first `{` (tolerates a ```json fence); track depth, in-string and escape state
  (a `"` after `\` does not close; chunk boundaries anywhere, including inside `á`); recognise the
  top-level key `songs` (depth 1, string token followed by `:` and `[`); capture each `{…}` that is a
  direct element of that array; stop after its `]`. Elements of any other key are ignored. Never throws;
  `reset()` clears it.
- **Validation per song (D-14)**: each captured object is decoded as `ProposedSongDto` with a lenient
  `Json { ignoreUnknownKeys = true }`; a decode failure drops it. Extract the per-song step of
  `ProposalValidator.validate` into `ProposalValidator.Accumulator(catalog)` with
  `offer(dto): ProposedSong?` (same trimming, `SongId.parseOrNull`, catalog lookup, first occurrence of a
  repeat wins, catalog title/artist/key); `validate` uses the same accumulator so streamed and final
  validation cannot drift. The catalog is the list `send` already reads at turn start.
- **Errors**: `send` also catches `IncompleteStreamException`; `AssistantErrors` maps it to
  `Incomplete` (cause-chain walk unchanged: an `IOException` found first stays `Offline`, an HTTP
  status stays `Unauthorized`/`Unavailable`). Cancellation is rethrown; the Ktor/OkHttp call is
  cancelled with the coroutine (prove it, below).
- Timeouts unchanged (60 s call/read per LLM call; each streamed round is one call).
- `AssistantLiveCallTest` (opt-in) switches to streaming and records the number of drafts seen.

### Part C: streamed songs and the incomplete state (`:feature:assistant`)

- `ChatTurn` gains `draft: List<ProposedSong> = emptyList()` (not in `State`, so every
  `state == Pending` check still holds). `AssistantChat.run` passes `onDraft` that updates the pending
  turn's draft only while it is still Pending (a late call after cancel is ignored). `finish` keeps the
  draft. `retry` drops the turn as today (the new turn starts empty).
- Mapping (`AssistantMapping.kt`):
  - `Pending` → `ReplyUiModel.Pending(loadingLabel, songs: List<ProposedSongUiModel>, songsHeading: String?, cancelLabel, events)`;
    `songsHeading = proposalHeading(n)` when n > 0. Songs mapped exactly like a final proposal's rows,
    `applyStatus = null`.
  - `Answered(Proposal)` → unchanged (the draft is ignored: final parse wins).
  - `Answered(Failed)`, `Cancelled`, `Interrupted` with a non-empty draft →
    `ReplyUiModel.Incomplete(heading = incompleteHeading(n), songs, message, retry)`; with an empty draft,
    unchanged. Incomplete never carries `ApplyUiModel` or a jam card; `ApplyMapping`/`JamMapping` only
    look at `Answered(Proposal)` turns, so no target can appear.
- `AssistantTurn.kt`: `PendingContent` draws heading + `SongRow`s (existing composable), then
  `LoadingDots`, then `Cancelar`; `IncompleteContent` draws heading, rows, the message in
  `textMuted` (cancelled) or `error` (failed) like `NoticeContent`, and `Reintentar`. The song nodes are
  not live regions (no announcement per song).
- `AssistantScreen.kt` scroll: add the pending turn's song count to the effect key, and when it grows
  scroll so the bottom of the last item stays visible **only if** the list could not scroll forward
  before the change (the admin had not scrolled up). Pure helper `shouldFollow(wasAtEnd, grew)` tested on the JVM.
- Copy (`AssistantCopy`, vos): `PENDING_LABEL` (Part A); `incompleteHeading(n)` =
  `PROPUESTA INCOMPLETA · n temas` (`1 tema`); `INCOMPLETE` = "La respuesta se cortó antes de terminar.
  Esto es lo que llegó; no está completa."; the line under incomplete songs is `INCOMPLETE` for
  `Incomplete`, `InvalidResponse` and `Interrupted`, the existing failure line for `Offline`,
  `Unauthorized`, `Unavailable`, `TooManySteps`, followed by " Lo que llegó no está completo.", and
  `CANCELLED` for a cancel. `AssistantFailure.Incomplete.message()` without a draft = `INCOMPLETE`'s
  first sentence + " Probá de nuevo.".

## Expected File Changes

- Part A: `core/ui/src/main/kotlin/com/bbbjam/core/ui/state/LoadingDots.kt` (create; composable,
  `LoadingDotsDefaults`, `loadingDotAlpha`, `isReducedMotion`, `rememberReducedMotion`),
  `core/ui/src/test/kotlin/com/bbbjam/core/ui/state/LoadingDotsTest.kt` (create);
  `feature/assistant/.../AssistantUiModel.kt`, `AssistantMapping.kt`, `AssistantTurn.kt`,
  `AssistantCopy.kt`, `AssistantPreview.kt` (modify); affected tests in `feature/assistant/src/test`.
- Part B: `core/data/.../assistant/StreamingProposalStrategy.kt`, `ProposalStreamParser.kt`,
  `AssistantDraft` (in `SetlistAssistant.kt` or its own file) (create); `DefaultSetlistAssistant.kt`,
  `SetlistAssistant.kt`, `AssistantReply.kt`, `AssistantErrors.kt`, `ProposalValidator.kt` (modify);
  tests `ProposalStreamParserTest.kt`, `StreamingAssistantTest.kt` (create), `DefaultSetlistAssistantTest.kt`,
  `ProposalValidatorTest.kt`, `AssistantLiveCallTest.kt`, SSE fixtures under `core/data/src/test/resources`
  if used (modify/create); `feature/assistant/src/test/.../FakeSetlistAssistant.kt` (signature only).
- Part C: `feature/assistant/.../AssistantChat.kt`, `AssistantUiModel.kt`, `AssistantMapping.kt`,
  `AssistantTurn.kt`, `AssistantScreen.kt`, `AssistantCopy.kt`, `AssistantPreview.kt`; tests
  `AssistantChatTest.kt`, `AssistantMappingTest.kt`, `AssistantPresenterTest.kt`, `FakeSetlistAssistant.kt`.
- No Gradle, Koin, Konsist, `:app` or backend change. No new dependency (Koog streaming, Ktor SSE and
  `Settings.Global` are already on the classpath).

## Visual Design Impact

- UI: yes. Source `DESIGN.md` (update: M1 exception, Asistente Pending/Incomplete bullets, `LoadingDots`
  in the list-state components). Colours `textMuted`/`text`/`error`/`key` only as today; amber allowlist
  `feature/assistant = {key, primaryAction, onPrimaryAction}` unchanged. Sizes from `spacing` tokens.

## Durable Documentation Impact

- `ARCHITECTURE.md`/`CONSTRAINTS.md`: not present in this repo; not needed.
- `AGENTS.md`: not needed (no rule or workflow change).
- `.claude/skills/architecture/SKILL.md`: add "As built by `assistant-streaming`" (functional streaming
  strategy, `onDraft`, parser + shared accumulator, final parse wins, `ChatTurn.draft`, `LoadingDots` and
  reduced motion in `:core:ui`).
- `DESIGN.md`: as above. `docs/risks-and-open-questions.md`: record the streaming risks below with their outcome.
- `docs/technical-discovery.md`: one line that the gateway streams (link the probe).

## Implementation Plan and Tasks

**Part A**
- [ ] A1 `loadingDotAlpha` and `isReducedMotion` tests first (red), then `LoadingDots` + defaults.
- [ ] A2 `Pending` model/copy/mapping/turn/preview; update the mapping and presenter tests.
- [ ] A3 DESIGN.md amendments. `CI=true ./init.sh` green → `docs/evidence/assistant-streaming/part-a-gate.txt`.

**Part B**
- [ ] B1 **Spike first**: a MockWebServer test replaying the probe's chunk shape (reasoning fields,
  `content: ""`, a tool round with split arguments, then a JSON answer split mid-string) through the real
  `AssistantHttp.executorFactory` and the new strategy; it must show the tool executed, a second request
  carrying the tool result, the schema text present once per request, and the final DTO. If Koog cannot
  decode the gateway's chunks or stream with tools, stop, keep Part A, and report (fallback).
- [ ] B2 Parser tests first (red): boundaries at every index of a 3-song answer (all splits of the
  string fed char by char and in random chunk sizes give the same objects), escaped quotes/backslashes,
  `\u` split, braces inside strings, a fence before `{`, `songs` not first, nested arrays in `reason`
  text, a malformed element, an unterminated tail, `reset`.
- [ ] B3 Accumulator extraction; `ProposalValidatorTest` unchanged and green; streamed = final for the same DTO.
- [ ] B4 Strategy + `send(onDraft)` + `Incomplete`. Tests (MockWebServer SSE): drafts grow 1,2,3 then the
  reply; unknown, malformed and repeated ids never in any draft; a tool round's text resets the draft;
  connection closed mid-stream after two songs → `Failed(Incomplete or Offline)` and drafts held two;
  HTTP 502 → `Unavailable(502)`; invalid final JSON after streamed songs → `InvalidResponse`, history
  unchanged; cancellation mid-stream throws `CancellationException`, no `onDraft` after it, and the
  server sees the connection closed; too many rounds → `TooManySteps`; key never in any output.
- [ ] B5 Gate → `part-b-gate.txt`.

**Part C**
- [ ] C1 `AssistantChatTest`: drafts reach the pending turn; a draft after cancel is ignored; the final
  reply keeps the turn Answered with the final list; failure/cancel keep the draft; retry starts empty.
- [ ] C2 Mapping tests: pending with songs (heading, rows, no targets, indicator label); incomplete per
  failure kind and cancel; answered ignores the draft; targets and jam card absent on incomplete and while pending.
- [ ] C3 Molecule `AssistantPresenterTest`: one transition Pending(empty) → Pending(2 songs) → Proposal
  with targets; and Pending(2) → Incomplete with Reintentar, no target. `shouldFollow` JVM test.
- [ ] C4 Turn/screen composables, preview, docs (architecture skill, risks, technical discovery).
- [ ] C5 Gate → `part-c-gate.txt`; device check.

## Verification Plan

- After each Part: `CI=true ./init.sh` exits 0 and prints `konsist`, `detekt`, `ktlint` as `wired`;
  JVM test count recorded (baseline 1055 testcases).
- Red-first evidence for A1, B2, B4 saved in `docs/evidence/assistant-streaming/`.
- Device check (Pixel, debug build, admin via the stored passphrase or `bluesjam.debugAdmin`), **one live
  read-only request, no target or dialog tapped, never print the key** (record only the md5 of
  `local.properties` before/after):
  1. Asistente, send a short request (e.g. "Seis temas lentos para cerrar"). Screenshot/`uiautomator dump`
     while pending: dots node with the `PENDING_LABEL` description; while streaming: songs listed with no
     message and no targets yet (screenshots/dumps taken in a loop about 1 s apart; no content logged).
  2. Final: targets present, list equals the final proposal. Do not tap any target.
  3. Reduced motion on the device needs a second request (a pending state exists only during one), so it
     is **outside the one-request budget**: covered by the JVM tests and recorded as not run, unless the
     orchestrator approves a second request (then read `adb shell settings get global animator_duration_scale`,
     set 0, send, dump, and restore the original value).
  4. If the gateway is down (502), record it, run nothing else, and rely on the MockWebServer tests.
- Cancel and broken-stream paths are verified on the JVM only (MockWebServer), stated as such.

## Evidence To Capture

- `docs/evidence/assistant-streaming/gateway-probe.md` (done at planning), red runs, three gate outputs,
  device notes and screenshots/dumps, `local.properties` md5 unchanged.

## Risks

- Koog's chunk decoder may reject the gateway's `reasoning_details` fields: B1 proves it on the probe's shape.
- `functionalStrategy` member names may differ slightly from the bytecode reading; B1 settles them.
- A model that writes `message` before `songs` (despite the schema order) streams no songs early; the
  final answer is still correct. Not a defect.
- WCAG 2.2.2 for the animation: mitigated as described; the reasoning goes in DESIGN.md.

## Validator Checklist

- [ ] Targets, apply and jam card are reachable only from `Answered(Proposal)`; no write path changed.
- [ ] Every streamed song went through the same accumulator as the final validation; tests cover
  unknown/malformed/repeated ids and chunk boundaries inside strings and escapes.
- [ ] The final parse replaces the streamed list; the history holds only complete proposals.
- [ ] Broken, failed and cancelled streams with songs show the incomplete heading and Reintentar, never targets.
- [ ] Cancellation is rethrown and closes the HTTP call (JVM test).
- [ ] `LoadingDots` uses only tokens, never amber, has a vos description, is static with scale 0.
- [ ] Konsist rules unchanged and green (`assistant-reads-only`, rule 19, amber allowlist).
- [ ] Three gate outputs with the tools `wired`; device check done or explicitly recorded as not run.
- [ ] DESIGN.md, architecture skill, risks updated.
