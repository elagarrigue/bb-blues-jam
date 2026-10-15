# Gateway streaming probe (`assistant-streaming`, planning)

Date: 15 October 2026, by the planner. Read-only: four `POST https://inference.devexpert.io/v1/chat/completions`
requests with `"model": "chat"` and `"stream": true`, sent from a Python `urllib` script in the session scratchpad
that read `devexpert.key` from `local.properties` and never printed it (only `key present: True`). Bodies were
JSON-encoded in the script (UTF-8, accented characters in the prompts). No tool result was ever sent back; no
app data was involved. Total cost: four short completions.

## Result: the gateway streams (SSE), including tool calls

| Probe | Request | Status / Content-Type | Outcome |
|---|---|---|---|
| plain | one-line Spanish question, `stream_options.include_usage: true` | 200, `text/event-stream; charset=utf-8` | 56 `data:` lines, first at 1.43 s, done at 1.97 s |
| tool | one dummy tool `searchCatalog(query)`, prompt asking to use it | 200, same | tool call streamed, `finish_reason: "tool_calls"`, done at 1.27 s |
| json-content | prompt asking for the proposal JSON shape (songs first) | 200, same | 19 content chunks, `finish_reason: "stop"`, 2.20 s |
| tool-args | prompt asking for two parallel calls of the dummy tool | 200, same | 8 tool-call deltas, two calls (index 0 and 1), `finish_reason: "tool_calls"` |

## Chunk shape (OpenAI chat-completions streaming, with extra reasoning fields)

- Every event is `data: {json}`; the stream ends with `data: [DONE]`.
- `object: "chat.completion.chunk"`, `model: "chat"`, one `choices[0]` with `index: 0` and a `delta`.
- `delta` fields seen: `role` (first chunk only, `"assistant"`), `content`, `reasoning_content`, `reasoning`,
  `reasoning_details` (list of `{type: "reasoning.text", text, format, index}`), `tool_calls`.
- The model **reasons first**: the first chunks carry `reasoning_content`/`reasoning` with `content: ""`. Answer
  text then arrives in `content` deltas.
- Content chunk granularity is a few tokens and **splits JSON anywhere**, inside keys and strings, e.g.
  `{"songs":[` / `{"songId":"s1` / `","key":"Mi menor","` / `reason":"Blues lento` / ... / `o."},{"songId":"`.
- Tool calls stream as `delta.tool_calls[{index, id, type: "function", function: {name, arguments}}]`: the first
  delta of a call has `id` and `name` with `arguments: ""`; later deltas have only `index` and argument fragments
  (`""`, `"{"`, `"\"query\":\"B.B. King\"}"`). Parallel calls use distinct `index` values.
- `finish_reason` arrives in its own chunk with an empty `delta`: `"stop"` for an answer, `"tool_calls"` for a
  tool round.
- `usage` arrives in a separate final chunk (empty `delta`) **when `stream_options.include_usage` is set**:
  `completion_tokens`, `prompt_tokens`, `total_tokens`, `completion_tokens_details.reasoning_tokens`, ... (the last
  two probes did not set it; usage was not checked there).

## Consequences for the design

- Streaming is available: the spec does not need the "loading animation only" fallback, but keeps it as the
  documented degradation if the gateway or Koog's parser misbehaves (see the spec's Risks).
- The time to first answer byte is dominated by reasoning and tool rounds; songs can only appear once the final
  round's `content` starts. The loading animation covers everything before that.
- An incremental parser must tolerate chunk boundaries anywhere, including inside escaped strings.
- The unknown `reasoning*` delta fields must be ignored by the client's decoder (to be proven by the
  implementer's first test, which replays chunks of this exact shape).
- The gateway was up during the probe (it had 502 outages on 13 and 15 October 2026).
