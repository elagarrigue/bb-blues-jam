# Feature Implementation Spec: Admin-only Asistente tab with the chat

## Source Feature

- `id`: assistant-admin-tab
- `area`: assistant
- `depends_on`: assistant-agent-core (`accepted`, 13 October 2026)
- `status`: not_started
- `source`: `feature_list.json`; `docs/phase-2-brief.md`; D-21 to D-24 in `bb-blues-jam-bitacora.md`;
  public API of `docs/specs/assistant-agent-core.md`

## Goal

While the admin session is active, the bottom bar gains a fourth tab, **Asistente**. It is a chat
over the accepted `SetlistAssistant`: the admin types a request, sees a visible loading state for the
8 s+ a live call takes, and gets each reply drawn as the proposed list (position, title, artist, key,
reason), the closing message and the follow-up questions; further requests refine the same
conversation. The conversation lives in memory for the app process. When the session ends the tab
disappears. This slice also carries the device proof owed by `assistant-agent-core`: one real request
through the Jackson-excluded Koog path on Android.

## Non-Goals

- No "Usar esta lista", no write, no `SetlistRepository`/`SetlistActionRegistry` use from the feature
  (that is `assistant-apply-proposal`; D-24).
- No change to `:core:data`'s assistant package (agent, prompt, tools, validator, model), except a
  narrow runtime fix if the device check proves one is needed (see Verification, step D6).
- No persistence across process death, no "new conversation"/reset action, no streaming, no
  editing or deleting past turns, no copying a proposal, no tapping a song to open its detail.
- No musician-facing assistant; no change to Próxima jam, Anteriores or Info screens.
- No Compose UI test harness (declined, T1); no new dependency beyond the new module itself.

## Job Story

When I am logged in as admin and want to build next month's setlist,
I want to ask the assistant in one sentence and refine its answer in a chat,
so I get a catalog-only proposal without reconstructing the repertoire from memory.

## Users And Permissions

- Admin (flag `AdminSession.observeIsAdmin()` true, including the debug admin session): sees and uses
  the tab. The flag only decides drawing; the assistant writes nothing.
- Musician / logged out: no tab, no route reachable from the bar.

## Acceptance Scenarios

1. **Tab appears.** Given the admin flag is true, then the bar shows `Próxima jam`, `Anteriores`,
   `Info`, `Asistente` (in that order; the musician tabs never move). Given it is false, three tabs.
2. **Tab disappears.** Given the Asistente tab is selected, when the flag becomes false (logout, or the
   debug session's `logOut`), then the tab is removed and the shell selects Próxima jam. While the
   flag has not been read yet (process start / restore), the tab is not drawn and no redirect happens.
3. **Empty state.** Given no turn yet and a key configured, the screen shows the title `Asistente`, the
   intro block and the composer (`Enviar` disabled while the field is blank).
4. **Send and loading.** When the admin sends a non-blank request, the field clears, the request appears
   as a `Vos` turn, followed by `Armando la propuesta…` / `Puede tardar unos segundos.` and a
   `Cancelar` action; `Enviar` is disabled (label `Esperando respuesta…`) until the turn ends.
5. **Proposal.** Given the reply is `Proposal`, the turn shows `PROPUESTA · 12 temas` (singular
   `1 tema`), one row per song (`01`, title, key, artist, reason), then the message, then
   `PREGUNTAS` with each question when there are any. With `rejectedSongIds` non-empty a muted
   caption says `Se descartaron 2 temas que no están en el catálogo.` (singular `1 tema que no está`);
   ids are never shown. Zero songs: no heading and no rows, just message/questions.
6. **Refine.** A second request goes to the same `AssistantConversation` (history kept by core).
7. **Failure.** Given the reply is `Failed(f)`, the turn shows the copy for `f` (table below) and,
   for the last turn only, a `Reintentar` action that replaces that turn with a new pending one for the
   same request.
8. **Cancel.** Tapping `Cancelar` cancels the request; the turn shows `Cancelaste este pedido.` with
   `Reintentar`. Leaving the tab, rotating or backgrounding does **not** cancel (Decision 2); coming
   back shows the pending turn or its answer.
9. **Not configured.** Given `SetlistAssistant.isConfigured` is false, the screen shows only the title
   and the not-configured block; no composer, no request.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md`, `feature_list.json`, `docs/phase-2-brief.md`, bitácora D-21..D-24.
- `.claude/skills/architecture/SKILL.md` (module layout, presenter pattern, Konsist rules, Koin).
- `DESIGN.md` (tokens, admin mode, bottom bar, required states; line 699 still lists an "AI assistant
  screen" as out of scope for the MVP) and `bb-blues-jam-design-prompt.md` (no assistant/chat
  guidance; line 223 excludes it from the MVP).
- `core/data/.../assistant/SetlistAssistant.kt`, `AssistantReply.kt`, `DefaultSetlistAssistant.kt`
  (cancellation rethrown and not added to history; one turn at a time behind a Mutex; blank request
  throws), `AssistantCredentials.kt`, `di/DataModule.kt` (`single<SetlistAssistant>`).
- `app/.../navigation/AppTab.kt`, `TabsShell.kt`, `AppNavHost.kt`, `AppRoutes.kt`, `MainActivity.kt`,
  `BluesJamApp.kt`, `app/build.gradle.kts`, `settings.gradle.kts`, `app/src/test/.../AppTabTest.kt`.
- `core/ui/.../nav/TabBarUiModel.kt` (`enum TabIcon`), `TabBarDefaults.kt` (icon mapping),
  `state/ListStateUiModel.kt` (`EmptyStateUiModel`), `text/SongListText.kt`
  (`positionLabel`, `keyDescription`), `presenter/Presenter.kt`.
- `feature/past-jams/build.gradle.kts`, `feature/info/.../AdminLoginDefaults.kt` (field and primary
  button colours), `feature/info/src/test/.../InfoModuleTest.kt`.
- `konsist-test/.../ModuleIsolationTest.kt` (modules read from `settings.gradle.kts`;
  `AMBER_ROLE_ALLOWLIST` per module), `init.sh`.

### Existing Patterns To Follow

- Feature build file as `feature/past-jams/build.gradle.kts`; copy in `internal object …Copy`;
  colours in `internal object …Defaults` with JVM tests; Molecule + Turbine presenter tests with fakes.
- Primary button and outlined field colours exactly as `AdminLoginDefaults` (`button-primary`, field
  with no amber). Key in the amber `key` role read through `SongListText.keyDescription`.
- Tab icons are an `enum TabIcon` mapped to `material-icons-core` vectors only in `:core:ui`.
- Admin flag read once in `MainActivity` and passed down (`admin-mode-highlight`).

### Current Gaps

- No `:feature:assistant`; no `TabIcon` for it; `TabsShell`/`tabBarModel` take no admin flag.
- `MainActivity` collects the flag with `initial = false`, so "not read yet" and "false" are
  indistinguishable; the redirect of Scenario 2 needs them apart (Decision 4).
- Konsist has no amber entry for `feature/assistant`.

## Technical Approach

### Decision 1 — Where the conversation lives
A presenter is a Koin `factory` and its composition is disposed when the tab is left, so it cannot
hold a process-lifetime chat. A plain class **`AssistantChat`** in `:feature:assistant`, bound as a
Koin `single` in `assistantModule`, owns one `AssistantConversation` (created lazily on the first
send) and a `StateFlow<List<ChatTurn>>`:

```kotlin
class AssistantChat(private val assistant: SetlistAssistant, private val scope: CoroutineScope) {
    val isConfigured: Boolean                    // assistant.isConfigured
    val turns: StateFlow<List<ChatTurn>>
    fun send(request: String): Boolean           // false (no-op) if blank after trim, not configured, or a turn is Pending
    fun cancel(id: Long)                         // cancels that turn's job if Pending
    fun retry(id: Long): Boolean                 // only the last turn, only Answered(Failed)/Cancelled/Interrupted, none Pending
}
data class ChatTurn(val id: Long, val request: String, val state: State) {
    sealed interface State { data object Pending; data class Answered(val reply: AssistantReply);
        data object Cancelled; data object Interrupted }
}
```

`send` trims, appends a `Pending` turn (ids from a counter, never reused) and launches in `scope`:
`conversation.send(request)` → `Answered(reply)`; `CancellationException` → `Cancelled`, rethrown;
a `finally` marks a still-`Pending` turn `Interrupted` (an unexpected exception escaping core; it is
then logged by the scope's handler, never caught broadly). `retry` removes the last turn and sends its
request again. Logout does not clear the chat (it lives while the process lives, per the brief).
This is UI session state, not a mutation: nothing is written anywhere (D-13 unaffected).

### Decision 2 — Cancellation
Requests run in `AssistantChat`'s scope, not the composition, so a tab switch or a rotation (which
dispose the composition) does not throw away an 8 s+ paid call; the answer is waiting on return. The
explicit `Cancelar` on the pending turn cancels it. Scope in `assistantModule`:
`CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e ->
Log.e("BluesJam", "assistant request failed: ${e::class.java.name}") })` — class name only, never the
message (it could carry a URL or header). Tests pass `backgroundScope`/`TestScope`.

### Decision 3 — Presenter and UiModel
`AssistantPresenter(chat: AssistantChat) : Presenter<AssistantUiModel, Unit>`. It collects
`chat.turns`, holds the composer text in `rememberSaveable { mutableStateOf("") }` and maps with a pure
function `assistantModel(isConfigured, turns, draft, handlers)` (unit-tested without Compose).

```kotlin
sealed interface AssistantUiModel : UiModel {
    val title: String                                   // "Asistente"
    data class NotConfigured(override val title: String, val block: EmptyStateUiModel)
    data class Chat(override val title: String, val intro: EmptyStateUiModel?, // only with no turns
        val turns: List<TurnUiModel>, val composer: ComposerUiModel)
}
data class TurnUiModel(val id: Long, val authorLabel: String /* "Vos" */, val request: String, val reply: ReplyUiModel)
sealed interface ReplyUiModel : UiModel {
    data class Pending(status, hint, cancelLabel, events: EventHandler<Event>)          // Event.Cancel
    data class Proposal(authorLabel /* "Asistente" */, heading: String?, songs: List<ProposedSongUiModel>,
        message: String, questionsHeading: String?, questions: List<String>, discardedNote: String?)
    data class Failed(message: String, retry: RetryUiModel?)   // retry only on the last turn
    data class Cancelled(message: String, retry: RetryUiModel?)
}
data class ProposedSongUiModel(positionLabel, title, artist, key, keyDescription, reason)
data class ComposerUiModel(text, label, placeholder, sendLabel, sendEnabled, events)  // TextChanged(text), Send
```

`Interrupted` maps to `Failed` with its own copy. `NotConfigured` reply (should not happen once the
screen checks `isConfigured`) maps to the not-configured message as a `Failed` turn without retry.
Handlers: per-turn `Cancel`/`Retry` keyed by turn id and calling `chat`; composer handler keyless,
reading the latest draft through `rememberUpdatedState`. `Send` calls `chat.send(draft)` and clears
the draft only when it returns true (so a double tap sends once).

### Decision 4 — Tab gating in `:app`
- `MainActivity` collects `observeIsAdmin()` as `Boolean?` with `initial = null`; theme gets
  `isAdmin == true`; `AppNavHost(admin: Boolean?)` draws the banner when `admin == true` and passes
  `admin` to `TabsShell`.
- `AppTab` gains `ASSISTANT(AppRoutes.ASSISTANT /* "assistant" */, "Asistente", TabIcon.ASSISTANT)` as the
  **last** entry. Pure helpers in `AppTab.kt`: `visibleTabs(admin: Boolean?)` (ASSISTANT only when
  `admin == true`), `tabBarModel(selected, admin, onSelect)` over `visibleTabs`, and
  `shouldLeaveAssistant(selected, admin) = selected == ASSISTANT && admin == false`.
- `TabsShell(…, admin: Boolean?)`: registers `composable(AppRoutes.ASSISTANT)` always (navigation
  graphs must be stable); draws `AssistantScreen()` only when `admin == true`, else nothing;
  `LaunchedEffect(selected, admin) { if (shouldLeaveAssistant(selected, admin)) inner.select(NEXT_JAM) }`.
- Keyboard: the composer sits above the IME with `imePadding()`. To avoid a gap the height of the bar,
  `TabsShell` does not draw the `TabBar` while `WindowInsets.isImeVisible` (opt-in
  `ExperimentalLayoutApi` in `:app`). No other tab has a text field, so nothing else changes.
- `:core:ui`: `TabIcon.ASSISTANT` → `Icons.Filled.Star` in `TabBarDefaults.icon` (in
  `material-icons-core`; no new dependency).

### Decision 5 — Visual design (minimal, recorded in DESIGN.md)
No assistant guidance exists in the design prompt, so this slice defines it and records it in a new
DESIGN.md section "Asistente (phase 2)". Background/surfaces follow the admin palette automatically
(`BluesJamColors` already switches; nothing to branch).
- Column: title `Asistente` (`h1`, `text`, heading) at the top, then a `LazyColumn` of turns (weight 1,
  spacing `md`), then the composer pinned at the bottom (padding `md`). The list scrolls to the newest
  turn when a turn is added or its state changes (`animateScrollToItem(last)`).
- Request turn: `surfaceRaised` card, `shapes.md`, padding `md`; caption `VOS` (`caption`, `textMuted`),
  request in `body`/`text`. Reply turn: `surface` card, same shape; caption `ASISTENTE`.
- Proposal: heading `PROPUESTA · n temas` (`caption`, `textMuted`, heading); each song one merged,
  non-clickable node: position (`songTitle`, `textMuted`), title (`songTitle`, `text`, 2 lines, …),
  key at the end (`songTitle` size, amber `key` role, described `Tonalidad Bm`), artist (`body`,
  `textMuted`), reason (`body`, `text`). Rows `sm` apart. Then message (`body`, `text`), `PREGUNTAS`
  (`caption`, `textMuted`, heading) and each question as `• …` (`body`, `text`); discarded note
  (`caption`, `textMuted`).
- Pending: status (`body`, `text`) and hint (`caption`, `textMuted`), one polite live region, static
  (no spinner/shimmer, as SkeletonList); `Cancelar` underlined 48dp text action in `text`.
- Failed/Cancelled: message in `body`, `error` for failures and `textMuted` for cancelled;
  `Reintentar` underlined 48dp text action in `text` (not amber: the primary action is `Enviar`).
- Composer: Material 3 outlined field labelled `Tu pedido`, placeholder
  `Ej.: 12 temas de blues nacional, con inicio y final arriba`, up to 5 lines, colours as
  `AdminLoginDefaults.field()`; under it the full-width `Enviar` button (`button-primary`:
  `primaryAction`/`onPrimaryAction`, `rounded.md`, ≥48dp; disabled `surfaceRaised`/`textMuted`).
  Amber on this screen: only `Enviar` and the keys. Konsist: add
  `"feature/assistant" to setOf("key", "primaryAction", "onPrimaryAction")`.
- Not configured / intro: `EmptyStateBlock` from `:core:ui`.

### Copy (`AssistantCopy`, Rioplatense *vos*)

| Key | Text |
|---|---|
| intro | `Pedí una lista` / `Contale al asistente qué jam querés armar: cuántos temas, qué estilo, cómo arrancar y cómo cerrar. Solo propone temas del catálogo.` |
| not configured | `El asistente no está configurado` / `Esta versión de la app no tiene la clave del servicio, así que no se pueden pedir listas.` |
| pending | `Armando la propuesta…` / `Puede tardar unos segundos.` / `Cancelar` |
| send | `Enviar` / `Esperando respuesta…` (while pending) |
| Offline | `No hay conexión. Para usar el asistente necesitás internet.` |
| Unauthorized | `El servicio rechazó la clave del asistente.` |
| Unavailable | `El servicio del asistente no respondió. Probá de nuevo en un rato.` (status never shown) |
| InvalidResponse | `No se pudo leer la respuesta del asistente. Probá de nuevo.` |
| TooManySteps | `El asistente no llegó a una propuesta. Probá con un pedido más concreto.` |
| CatalogEmpty | `El catálogo todavía no se cargó. Abrí la app con conexión y volvé a probar.` |
| Interrupted | `No se pudo terminar este pedido. Probá de nuevo.` |
| Cancelled | `Cancelaste este pedido.` |
| retry | `Reintentar` |

## Expected File Changes

- `settings.gradle.kts` — include `:feature:assistant`.
- `feature/assistant/build.gradle.kts`, `.gitignore` (`/build`) — create; as past-jams (`:core:ui`,
  `:core:data`, foundation, tooling). Namespace `com.bbbjam.feature.assistant`.
- `feature/assistant/src/main/kotlin/com/bbbjam/feature/assistant/` — create `AssistantChat.kt`,
  `AssistantPresenter.kt`, `AssistantUiModel.kt`, `AssistantMapping.kt` (pure `assistantModel`),
  `AssistantScreen.kt`, `AssistantCopy.kt`, `AssistantDefaults.kt`, `di/AssistantModule.kt`
  (`single { AssistantChat(get(), scope) }`, `factory { AssistantPresenter(get()) }`), a preview file.
- `feature/assistant/src/test/kotlin/...` — `AssistantChatTest`, `AssistantMappingTest`,
  `AssistantPresenterTest`, `AssistantDefaultsTest`, `AssistantModuleTest`, `FakeSetlistAssistant`.
- `core/ui/.../nav/TabBarUiModel.kt`, `TabBarDefaults.kt` (+ its test if one asserts the icon map) —
  `TabIcon.ASSISTANT`.
- `app/build.gradle.kts` — `implementation(project(":feature:assistant"))`.
- `app/.../BluesJamApp.kt` — `assistantModule` in `startKoin`.
- `app/.../MainActivity.kt`, `navigation/AppNavHost.kt`, `TabsShell.kt`, `AppTab.kt`, `AppRoutes.kt` —
  Decision 4. `app/src/test/.../AppTabTest.kt` — updated and extended.
- `konsist-test/.../ModuleIsolationTest.kt` — the amber allowlist entry only.
- Docs: `DESIGN.md`, `.claude/skills/architecture/SKILL.md`, `PROGRESS.md`, `feature_list.json`,
  `docs/risks-and-open-questions.md` (close the Koog-on-Android residual risk or record the failure),
  `docs/evidence/assistant-admin-tab/`.

## Visual Design Impact

- UI involved: yes. Source: `DESIGN.md` tokens + Decision 5 (new section, written by the implementer).
- Screens/states: bottom bar (4th tab for the admin), Asistente: intro, not configured, pending,
  proposal, failure, cancelled.
- New design artifact required: no Stitch asset; the DESIGN.md section is the record. Also amend
  DESIGN.md line 699 and "Three tabs" (line 186) to say phase 2 adds the admin-only Asistente tab.

## Durable Documentation Impact

- `DESIGN.md` — update (Decision 5, bar section, out-of-scope line).
- `.claude/skills/architecture/SKILL.md` — update: module table/feature list gains `:feature:assistant`
  ("As built by `assistant-admin-tab`" paragraph: `AssistantChat` single, tab gating, amber entry).
- `AGENTS.md`, `CONTEXT.md`, `docs/domain-model.md` — not needed (no new rule or entity).
- `ARCHITECTURE.md`, `CONSTRAINTS.md` — do not exist; not needed.
- `docs/risks-and-open-questions.md` — update the "Dependency weight of Koog" row with the device result.

## Implementation Plan / Tasks

1. Run `CI=true ./init.sh` (baseline; record the test count).
2. Create the module and register it (settings, `:app` dependency, Koin). Build.
3. `AssistantChat` + `AssistantChatTest` (fake `SetlistAssistant` with a controllable `CompletableDeferred`
   reply): send appends Pending then Answered; blank/pending/not-configured sends return false; cancel →
   Cancelled and the fake saw cancellation; a thrown unexpected `IllegalStateException` → Interrupted;
   retry rules (only last, only finished non-success, replaces the turn, reuses the conversation); the
   conversation is created once and reused across turns.
4. Copy, mapping and `AssistantMappingTest`: every `AssistantFailure`, proposal with 0/1/n songs,
   questions present/absent, discarded note singular/plural/absent, intro only with no turns,
   not-configured model, `Enviar` enabled only with non-blank text and no pending turn, retry only on
   the last turn.
5. `AssistantPresenter` + Molecule test: at least the transition empty → pending → proposal, typing
   then Send clears the draft, a double Send sends once, Cancel reaches the chat.
6. `AssistantDefaults` + test (amber only on enabled send and keys; field colours equal the admin login's).
7. `AssistantScreen` and preview. `TabIcon.ASSISTANT`. `:app` Decision 4 + `AppTabTest` (visible tabs
   for `null`/`false`/`true`, order with ASSISTANT last, `shouldLeaveAssistant` truth table, bar
   selection with four tabs).
8. Konsist allowlist entry; show `amber-roles-allowlisted` failing first (e.g. run with the entry
   absent and the screen already reading `primaryAction`), then green.
9. `CI=true ./init.sh`; then the device check; then docs.

## Verification Plan

- Gate: `CI=true ./init.sh` exit 0, `konsist`, `detekt`, `ktlint` all `wired`; test count above baseline,
  0 failures. Konsist must still pass `build-file-project-deps` (no feature→feature) and the module
  must appear in the "Konsist scope covers every included module" test without editing it.
- `./gradlew :app:assembleDebug` succeeds.
- **Device check** (physical device or emulator, minSdk 24+; record API level). Setup: back up
  `local.properties` (copy to the scratchpad and record its md5), set `bluesjam.debugAdmin=true` and
  `bluesjam.demoUpcomingJam=true`; `devexpert.key` must already be present — never print, `cat`, grep
  with output, or log it (check presence with `grep -c '^devexpert.key=.\+' local.properties`).
  - D1. `./gradlew :app:installDebug`, launch with network. Screenshot: bar with four tabs and the admin
    banner (`01-tab-visible.png`).
  - D2. Open Asistente: intro and composer (`02-empty.png`).
  - D3. Send **one** request, the brief's example: `Generá una lista de temas de blues nacional, 12,
    con inicio y final bien arriba y temas más tranquis en el medio. Sumá algún tema que haya tocado
    Juan en alguna jam pasada.` Screenshot the pending state (`03-pending.png`) and the answer
    (`04-proposal.png`, plus scrolled shots as needed). Record the elapsed time. This is the carried-
    forward check that the Koog path works at runtime on Android without Jackson. No second request, no
    Reintentar unless the first failed for network reasons (then at most one retry, recorded).
  - D4. `uiautomator dump` of the answer: a song row is one node with the key description; the pending
    text was a live region (from the D3 dump if captured, else note it).
  - D5. Logout path: Info → `Salir del modo admin`; screenshot three tabs (`05-tab-gone.png`). Never tap
    anything that writes (no Próxima jam admin control, no Usar esta lista — it does not exist).
  - D6. If the request fails with a crash or `InvalidResponse`/`Interrupted` caused by the runtime
    (missing class, ServiceLoader, reflection), capture the logcat stack trace filtered to tag/package
    with the key not present, save it as evidence, and stop: report it as a blocker unless the fix is a
    narrow `:core:data` dependency/packaging change, which must then be listed as a deviation.
  - Teardown: restore `local.properties` byte-for-byte from the backup and compare md5; confirm the key
    appears in no evidence file (`grep -rc` over `docs/evidence/assistant-admin-tab/` for the key value
    read into a shell variable, printing counts only).
- No Sheet writes at any step; reads (catalog/jams GET at startup) are allowed.

## Evidence To Capture

`docs/evidence/assistant-admin-tab/`: gate output, Konsist failing-then-green run, screenshots
01–05, uiautomator dump excerpt, `device-check.md` (device, API level, elapsed time, outcome, songs and
discarded counts, md5 before/after, key-absent counts). Summary in `PROGRESS.md` and the feature's
`evidence` array; status `passing` only.

## Validator Checklist

- [ ] `:feature:assistant` depends only on `:core:ui`/`:core:data`; no ViewModel; presenter `factory`,
  `AssistantChat` `single`; constructor injection only.
- [ ] No `SetlistRepository`, `AdminWriter`, `SetlistActionRegistry` or write path referenced from the
  feature; no "Usar esta lista".
- [ ] Tab hidden for `null`/`false`, shown last for `true`; redirect only on a real `false`; tested.
- [ ] Leaving the tab does not cancel; `Cancelar` does; cancellation never becomes a failure copy.
- [ ] All copy in *vos*, from `AssistantCopy`; every `AssistantFailure` mapped; ids never drawn.
- [ ] Colours only via `BluesJamTheme`/`AssistantDefaults`; amber only on `Enviar` and keys; allowlist
  entry present and shown failing first.
- [ ] Gate green with three tools wired; device evidence includes one real answered request, and
  `local.properties` md5 unchanged; key absent from every tracked or evidence file.
- [ ] DESIGN.md, architecture skill and risks updated.

## Assumptions (not verified)

- `Icons.Filled.Star` is in `material-icons-core` 1.7.x (believed, not checked against the jar; if
  absent, use another core icon and record it).
- `WindowInsets.isImeVisible` behaves on API 24–29 as on newer APIs; checked on the device step only on
  the API level used.
- The live call duration (~8 s) comes from the core slice's evidence and can vary widely.

## Amendment (13 Oct 2026): service status

User-approved scope amendment ("podemos checkear si está activo y agregar un estado offline"), prompted by the DevExpert gateway
being down (Caddy 502, empty body, on `/v1/chat/completions` and `/v1/models`, even without a key) while D3 was being checked.
D3 stays pending until the gateway is back; this amendment adds a status check so the tab says so instead of waiting 120 s.

### `:core:data`
- `SetlistAssistant.checkStatus(): AssistantStatus` (suspend) and the sealed `AssistantStatus`: `Available`, `NotConfigured`,
  `Offline` (no network, an `IOException`), `ServiceDown` (HTTP 5xx or any other non-2xx, or a hung gateway: a timeout), `Unauthorized`
  (401/403).
- Implementation: one plain `GET <base>/v1/models` with `Authorization: Bearer <key>` over the assistant's `OkHttpClient` with a 10 s
  call timeout. It is a read that consumes no tokens (D-24 rule `assistant-reads-only` unaffected). `NotConfigured` makes **zero**
  requests. The key is never logged. Cancellation cancels the call and is rethrown.
- Tests (MockWebServer): 200 -> Available, 502 -> ServiceDown, 401 -> Unauthorized, dropped connection -> Offline, missing key ->
  NotConfigured with no request recorded, cancellation.
- **Timeout decision:** `AssistantHttp` call/request timeout lowered 120 s -> 60 s (read timeout 90 s -> 60 s), so a hung gateway
  fails in about a minute. A normal request took about 7 s in the live run; 60 s leaves a wide margin for tool rounds. Rule: only a
  timeout is `ServiceDown`; any other `IOException` is `Offline`.

### `:feature:assistant`
- `AssistantChat` holds a `StateFlow<ServiceState>` (`Checking` or `Known(AssistantStatus)`, initial `Checking`). `checkStatus()` runs in
  the chat scope, single-flight; it shows `Checking` only when the last known state was not `Available` (a re-check from `Available`
  is silent). The presenter asks for a check when the tab is shown and the banner's `Reintentar` asks again; a send that ends in
  `Failed(Offline | Unavailable)` triggers a re-check (other failures and proposals do not).
- `AssistantUiModel.Chat.status`: `None` (Available / NotConfigured), `Checking("Comprobando el servicio…")` (static, no spinner, polite live region), or
  `Banner(message, retryLabel, events)`: `ServiceDown` -> "El asistente no está disponible en este momento. El servicio de IA no responde; probá más tarde.",
  `Offline` -> the existing offline copy, `Unauthorized` -> the existing unauthorized copy, each with `Reintentar`.
- `Enviar` is disabled unless the status is `Available`; a turn's `Reintentar` is also withheld while a banner or the checking line shows
  (the banner's `Reintentar` re-checks first).
- Visual: the banner is a `surfaceRaised` card with `text` copy and an underlined `Reintentar` text action in `text`; never amber (D-17).
  Recorded in DESIGN.md "Asistente".
- Device check now (no paid request): debug admin + demo jam, open Asistente with the gateway down, screenshot the `ServiceDown` banner with `Enviar`
  disabled, tap `Reintentar` once, screenshot. No chat request while down.
