# Phase 2 Brief — Setlist Assistant

Phase 1 (the MVP) is closed: every feature in `feature_list.json` is `accepted` except the
Songsterr link, blocked by D-20. Phase 2 adds the assistant the MVP was built to receive (D-13,
D-14). Decided with the organizer on 13 October 2026; decisions D-21 to D-24 in the bitácora.

## Problem

Building a setlist takes the organizer time and draws on repertoire knowledge that lives in one
person's head (the fifth problem in `build-brief.md`). The assistant turns a one-sentence request
into a proposed setlist taken only from the Sheet catalog.

Example request, in the organizer's words:

> Generá una lista de temas de blues nacional, 12, con inicio y final bien arriba y temas más
> tranquis en el medio. Sumá algún tema que haya tocado Juan en alguna jam pasada.

## User

Only the admin. Musicians never see the assistant.

## Behavior

1. A new bottom-navigation tab, **Asistente**, drawn only while the admin session is active. It
   disappears with the admin flag; a musician build/session never shows it.
2. The tab is a chat. The admin writes a request; the agent answers.
3. Every agent answer is a **structured JSON response**:
   - `songs`: the proposed setlist in order — each item a catalog song id, plus the title, artist
     and key as shown in the catalog and a short reason in Spanish (*vos*);
   - `message`: a closing message for the admin;
   - `questions`: zero or more follow-up questions when the request is ambiguous.
   The chat keeps the conversation so the admin can refine ("cambiá el tercero por algo de Pappo").
4. The agent reads data through **tools**, not through a prompt dump: list/search the catalog, and
   read past jams (date, songs, and who played what) so "algún tema que haya tocado Juan" works.
5. Every proposed song id is **validated against the catalog** before it is shown (D-14). Songs
   outside the catalog are never presented as part of the list.
6. **Later slice:** a "Usar esta lista" action loads the proposal into the upcoming setlist through
   the action contract (`SetlistActionRegistry`, D-13), after the admin confirms. The agent itself
   never writes.

## Technical decisions

- **Agent runtime (D-21):** Koog, JetBrains' Kotlin agent framework, on the device. It is the Kotlin
  counterpart of the OpenAI Agents SDK seen in class (agent, tools, structured output). Current
  Koog (1.x) needs Kotlin 2.3, so the toolchain is raised from 2.2.10 first, as its own slice.
- **Inference endpoint:** DevExpert Inference, OpenAI-compatible, base URL
  `https://inference.devexpert.io/v1`, model alias `chat` (one constant, easy to change). Probed on
  13 October 2026: `mimo-v2.5` is listed by `/v1/models` but chat completions rejects it for this
  key ("Invalid model name"); `chat` and `deepseek-v4-flash` both returned a tool call and honored
  a strict `json_schema` response.
- **API key (D-22):** read from the git-ignored `local.properties` (`devexpert.key`) into
  `BuildConfig`, in every build type. Accepted risk: anyone holding the APK can extract it. A
  missing key makes the tab show a "not configured" state, never a crash.
- **Module (D-23):** `:feature:assistant`. It is a feature of its own whose tab depends on the admin
  flag, not the `:feature:admin` module D-15 rules out; admin controls on existing screens stay in
  their features. The agent, its tools and its client live outside the feature module (see the
  `architecture` skill for placement).
- **Writes (D-24):** the assistant proposes; only the admin's explicit confirmation applies a list,
  and only through the action contract. Apps Script remains the write authority.

## Non-goals

- No musician-facing assistant, no voice, no streaming requirement.
- No suggestions outside the catalog, no MusicBrainz enrichment (still optional, D-09).
- No persistence of chat history across app restarts in the first slices.
- No autonomous writes by the agent.

## Slices

1. `kotlin-2-3-toolchain-upgrade` — raise Kotlin and the pinned libraries; gate stays green.
2. `assistant-agent-core` — Koog agent, tools over catalog and past jams, structured response,
   catalog validation, key wiring.
3. `assistant-admin-tab` — `:feature:assistant` chat screen and the admin-only tab.
4. `assistant-apply-proposal` — "Usar esta lista" through the action contract. `assistant-proposal-targets` renames it
   `Agregar a la próxima jam` and adds `Reemplazar la lista de la próxima jam` (adds, removes and reorders through the same
   contract, confirmed, naming what is lost); `Crear una jam nueva con esta lista` follows in its Part C.
5. `assistant-create-jam` — the agent proposes the next jam (fecha, hora, lugar); the admin confirms
   and the app creates it as BORRADOR through the action contract (D-25).

## Open risks

- Tool calling and `json_schema` were verified with raw HTTP calls; whether Koog's OpenAI client
  sends requests the gateway accepts is verified with a live call in slice 2. The model alias
  `chat` may change on the gateway's side.
- Catalog tags are empty (D-20): "tranquis" vs "arriba" must come from the model's knowledge of the
  song plus title/artist, not from data. "Blues nacional" relies on artist knowledge too.
- The key ships in the APK (D-22).
