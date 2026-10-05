# Feature Implementation Spec: Log in as admin with a passphrase

## Source Feature

- `id`: admin-passphrase-login
- `area`: feature-info
- `depends_on`: apps-script-read-endpoint (accepted), design-tokens-theme (accepted)
- `status`: not_started at planning time (5 October 2026)
- `source`: `feature_list.json`; notes: D-11, no Firebase Auth/OAuth/accounts, a visible logout on
  Info, the local flag only draws controls and authorizes nothing.
- **Sequencing:** touches `AppRoutes.kt`, `AppNavHost.kt` and `TabsShell.kt`, which `past-jam-detail`
  is changing now. Start only after `past-jam-detail` is committed (ideally accepted).
- **Blocked on:** the User Approvals below (A1 needs one manual redeploy by the user).

## Goal

The admin taps "Entrar como admin" on Info, types the shared passphrase on a minimal login screen and
taps "Entrar". Apps Script checks it against the `Config` tab; only a server "ok" stores it on the
device. Info then shows that admin mode is on and offers "Salir del modo admin". A wrong
passphrase shows an error and stores nothing. The state survives an app restart. This slice adds the
`AdminSession` contract every later admin slice reads (D-15) and the stored passphrase the write
slices will send; it draws no setlist control.

## Non-Goals

- Any write route, any guard on writes, the admin read of a draft, "your access changed" on a
  rejected write: all `apps-script-write-auth` and later. This slice only **checks** a passphrase.
- Admin controls on Próxima jam or the song detail; reading `AdminSession` anywhere but Info.
- Session expiry, rate limiting or lockout, passphrase recovery, accounts, the action registry.
- Re-validating the stored passphrase on start (a rotated passphrase is caught by the first write).
- Changing `Code.js`, `Catalog.js`, `Jams.js`, `Normalize.js` or `appsscript.json`.

## Job Story

When I am about to build the setlist on my phone, I want to unlock admin mode with the passphrase
the organizers share, so I see the editing controls without an account, and can switch them off on a
borrowed phone.

## Users And Permissions

- Musician: never sees the login unless they tap the discreet entry; a wrong passphrase reveals
  nothing. Admin: the passphrase holder. The local flag is not authorization (D-11); the server
  check is, and from `apps-script-write-auth` on every write re-checks it.

## Acceptance Scenarios

1. **Correct passphrase.** Given Info in musician mode, when the user opens the login, types the
   passphrase and taps "Entrar", then "Verificando…" shows, the screen closes back to Info, and Info
   shows "Modo admin activo" and "Salir del modo admin" instead of "Entrar como admin".
2. **Wrong passphrase.** Then "La frase de acceso no es correcta." shows under the field, the field
   keeps the text, nothing is stored, Info stays in musician mode. Typing clears the error.
3. **Offline / service unavailable / script not redeployed yet.** Then the offline or generic error
   copy shows; nothing is stored.
4. **Blank field.** "Entrar" is disabled; no request is made.
5. **Restart.** Logged in, the app is force-stopped and relaunched: Info still shows admin mode.
6. **Logout.** "Salir del modo admin" clears the stored passphrase; Info shows "Entrar como admin".
7. **Back.** "Volver" or system back closes the login with nothing stored.
8. **Server.** A POST with the right passphrase returns `ok: true`; a wrong, empty or missing one
   returns `invalid_passphrase`; an unset `Config` passphrase rejects every attempt
   (`passphrase_not_set`); no response ever contains the stored or submitted passphrase; the two
   GET routes still never open `Config`.

## Repository Research

### Files Inspected

`AGENTS.md`, `PROGRESS.md`, `feature_list.json` (this entry and `apps-script-write-auth`),
`docs/user-and-access-model.md`, `docs/apps-script-api.md`, `docs/sheet-schema.md` (`Config`),
`docs/technical-discovery.md` (DataStore for the flag), `docs/risks-and-open-questions.md`,
`CONTEXT.md`, `DESIGN.md`, `bb-blues-jam-design-prompt.md` (screens 6 and 7), bitácora D-11,
`.claude/skills/architecture/SKILL.md` and `references/presenter-pattern.md` (`AdminSession`
sketch), `backend/apps-script/README.md`, `src/Code.js`, `test/router.test.js`,
`test/helpers/load.js`, `appsscript.json`, `core/data` (`AppsScriptTransport`,
`OkHttpAppsScriptTransport`, `Envelope.kt`, `DataFailure`, `di/DataModule.kt`, build file),
`feature/info` (all sources, build file), `app/.../navigation/AppRoutes.kt`, `AppNavHost.kt`,
`TabsShell.kt` (Info call site only), `AndroidManifest.xml`, `res/xml/*_rules.xml`, Konsist
`DATA_LIBRARY_PREFIXES`, `gradle/libs.versions.toml`, specs `apps-script-read-endpoint`,
`apps-script-jams-read-endpoint`, `past-jam-detail`.

### Findings

- No route checks a passphrase today; `doGet` only, and "Writes, the passphrase check and the
  admin's read of a draft come with `apps-script-write-auth`" (`apps-script-api.md`). A server check
  is impossible without a backend change: the passphrase lives only in the Sheet and the repo is
  public, so no local comparison (and no hash in the APK) is acceptable.
- `apps-script-api.md` and the risks doc already require that a passphrase travel in a **POST
  body**, never a GET parameter. Apps Script answers a POST with a 302; OkHttp follows it as a GET
  (its default for 302), which is what Apps Script needs; `curl -L -d` does the same.
- `router.test.js` asserts the read routes never open `Config`; it must stay green unchanged.
- `load.js` loads a fixed `SRC_FILES` list; a new file must be added there.
- `AppsScriptTransport` is a `fun interface` with `get(resource)` that existing tests implement as
  lambdas; `AppsScriptEnvelope.decode` decodes list payloads only.
- DataStore is the decided store (D-11, technical discovery, architecture skill) but is not in the
  catalog yet. Konsist `DATA_LIBRARY_PREFIXES` lists okhttp3, Room and serialization only.
- `InfoPresenter` has a placeholder `AdminEntryTapped` → "El ingreso de admin todavía no está
  habilitado." meant to be replaced here. `:feature:info` depends only on `:core:ui`.
- Backups are on (`allowBackup="true"`) with empty sample rules.

## Technical Approach

### 1. Backend: one new file, `backend/apps-script/src/Post.js` (A1)

`doPost(e)` parses `e.postData.contents` as JSON and calls `handlePost(request, spreadsheet)`,
returning JSON exactly as `doGet` does (reusing `SCHEMA_VERSION`, `errorBody_`, `errorMessage_`,
`ContractError`, `mapColumns` from the shared scope). `ACTIONS = { checkPassphrase:
checkPassphrase_ }`. Request: `{"action":"checkPassphrase","passphrase":"…"}`. Answers:

| Case | Body |
|---|---|
| match | `{"schemaVersion":1,"ok":true}` |
| body not a JSON object | error `invalid_request` |
| `action` missing or unknown | error `unknown_action`, message ending `Known: checkPassphrase` |
| `Config` tab, `clave`/`valor` headers or the `passphrase` row missing, or `valor` blank after trim | error `passphrase_not_set` (fail closed, checked **before** comparing, so blank never matches blank) |
| `passphrase` missing, not a string, or not equal to the trimmed `valor` (exact, case-sensitive) | error `invalid_passphrase` |
| any other exception | `internal_error` |

The stored value is read with `getDisplayValues`, trimmed; the submitted one is compared as sent
(the client trims). No message ever includes either value. `readPassphrase_(spreadsheet)` and
`passphraseMatches_(spreadsheet, submitted)` are named for reuse by `apps-script-write-auth`, which
will add write actions to `ACTIONS` and guard them. No other `src` file changes, so the deployed
`Code.gs`, `Catalog.gs`, `Jams.gs` and `Normalize.gs` stay as they are and the GET check still reads
`Known: catalog, jams`.

### 2. `:core:data`: `AdminSession` (package `com.bbbjam.core.data.admin`)

```kotlin
interface AdminSession {
    fun observeIsAdmin(): Flow<Boolean>          // true while a verified passphrase is stored
    suspend fun logIn(passphrase: String): LoginOutcome
    suspend fun logOut()
}
sealed interface LoginOutcome { Success; WrongPassphrase; Offline; Unavailable }  // data objects
```

- `DefaultAdminSession(transport: AppsScriptPostTransport, store: AdminCredentialStore)`, internal.
  `logIn` trims; blank → `WrongPassphrase` with no request. Otherwise POSTs, decodes with a new
  `AppsScriptEnvelope.decodeOk(body)` (schemaVersion 1, `error` → `Service(code)`, `ok == true`
  else `InvalidResponse`). Mapping: ok → store the trimmed passphrase, `Success`;
  `Service("invalid_passphrase")` → `WrongPassphrase`; `Offline` → `Offline`; everything else
  (`NotConfigured`, other codes including `passphrase_not_set`, `InvalidResponse`, an HTML page
  from a deployment without `doPost`) → `Unavailable`. Nothing is stored unless `Success`.
  `logOut` clears. No logging of the passphrase, ever; no log line is required.
- `AdminCredentialStore`, internal: DataStore Preferences, file `admin_session` (one string key
  `admin_passphrase`). `isAdmin` = stored value non-blank, so flag and credential cannot disagree.
  An `IOException` on read emits `false`; `ReplaceFileCorruptionHandler { emptyPreferences() }`.
  Exposes `internal suspend fun passphrase(): String?` for the later write slices. Built in
  `dataModule` with `PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile("admin_session") })`
  as one `single`; tests build it over a temp file on the JVM.
- New `internal fun interface AppsScriptPostTransport { suspend fun post(json: String): TransportResult }`,
  implemented by `OkHttpAppsScriptTransport` (POST to the base URL with no query, body
  `application/json; charset=utf-8`, same timeouts and failure mapping as `get`). Separate from
  `AppsScriptTransport` so existing lambda fakes compile unchanged. The request JSON is built with
  kotlinx-serialization (`@Serializable CheckPassphraseRequest(action, passphrase)`), never by string
  concatenation.
- `dataModule`: `single<AppsScriptPostTransport> { get<OkHttpAppsScriptTransport>() }` style binding
  sharing the one transport instance, the store, and `single<AdminSession> { DefaultAdminSession(get(), get()) }`.

### 3. `:feature:info`: login screen and Info admin state (D-15)

- `build.gradle.kts`: add `implementation(project(":core:data"))`.
- `AdminLoginPresenter(adminSession)` : `Presenter<AdminLoginUiModel, Params(onBack, onLoggedIn)>`;
  callbacks through `rememberUpdatedState`. State: `passphrase` and `masked` in plain `remember`
  (never `rememberSaveable`: the passphrase must not enter the saved-state Bundle; rotation clears the
  field, accepted), `phase` Idle/Verifying/Error(kind). `Submit` while Verifying or blank is ignored;
  otherwise `scope.launch { logIn }` → `Success` calls `onLoggedIn`, else Error. `PassphraseChanged`
  clears the error. `AdminLoginUiModel`: `title`, `fieldLabel`, `passphrase`, `masked`,
  `visibilityToggleLabel`, `submitLabel`, `submitEnabled`, `isVerifying`, `error: String?`,
  `back: BackUiModel`, `events` (`PassphraseChanged(text)`, `ToggleVisibility`, `Submit`, `Back`).
- `AdminLoginScreen(onBack, onLoggedIn, contentPadding, presenter = koinInject())`: `BackButton`,
  title (`h1`), a Material 3 `OutlinedTextField` with **explicit token colors** (container
  `surface`, text `text`, label and unfocused border `textMuted`/`border`, focused border and cursor
  `text`, error border and supporting text `error`; no amber), `PasswordVisualTransformation` when
  masked, `KeyboardType.Password`, `ImeAction.Done` → `Submit`, autocorrect off; a 48dp text toggle
  "Mostrar"/"Ocultar"; the error as supporting text (live region polite); a full-width "Entrar"
  button (`primaryAction`/`onPrimaryAction`, `rounded.md`, min 48dp, the `button-primary` token;
  disabled: `surfaceRaised`/`textMuted`), label "Verificando…" while verifying. `imePadding()` so
  the button stays above the keyboard. No logic in the composable.
- `InfoPresenter(linkOpener, adminSession)`, `Params(onOpenAdminLogin)`. `AdminEntryUiModel`
  becomes sealed: `LoggedOut(label)` (event `AdminEntryTapped` → `onOpenAdminLogin`) and
  `LoggedIn(status, logoutLabel)` (event `LogOut` → `scope.launch { adminSession.logOut() }`). Same
  discreet caption style in both; logout is a 48dp text action, no confirmation (A4). Remove
  `ADMIN_NOT_ENABLED`.
- `infoModule`: `factory { InfoPresenter(get(), get()) }`, `factory { AdminLoginPresenter(get()) }`.

### 4. `:app` navigation

`AppRoutes.ADMIN_LOGIN = "admin-login"`; `AppNavHost` adds it to the **outer** host beside the song
detail, same slide and insets pattern, `onBack`/`onLoggedIn` both pop guarded by
`entry.lifecycle.currentState == RESUMED`; `TabsShell` gains `onOpenAdminLogin` and passes it to
`InfoScreen`. `AppRoutesTest` gains the constant's check.

### 5. Gate and backup

- Konsist: add `"androidx.datastore."` to `DATA_LIBRARY_PREFIXES` and `androidx\.datastore` to
  `QUALIFIED_DATA_LIBRARY` (A6; rule count stays 17). Add `:feature:info` →
  `{primaryAction, onPrimaryAction}` to `AMBER_ROLE_ALLOWLIST` (A6).
- Catalog: `androidx-datastore-preferences` at the newest version that leaves `kotlin-stdlib`
  2.2.10, coroutines, Compose and lifecycle unmoved on `:app`'s runtime classpath (prove with
  `dependencyInsight` before/after); `implementation` in `core/data/build.gradle.kts` only.
- `backup_rules.xml` and `data_extraction_rules.xml` (cloud-backup and device-transfer): exclude
  `datastore/admin_session.preferences_pb`, so the passphrase never leaves the device.

## Expected File Changes

- Create `backend/apps-script/src/Post.js`, `backend/apps-script/test/post.test.js`; modify
  `test/helpers/load.js` (`SRC_FILES` + `Post.js`), `backend/apps-script/README.md`.
- Create in `core/data/src/main/kotlin/com/bbbjam/core/data/admin/`: `AdminSession.kt`,
  `LoginOutcome.kt`, `DefaultAdminSession.kt`, `AdminCredentialStore.kt`; modify
  `remote/AppsScriptTransport.kt`, `remote/OkHttpAppsScriptTransport.kt`, `remote/Envelope.kt`,
  new `remote/CheckPassphraseRequest.kt`, `di/DataModule.kt`, `core/data/build.gradle.kts`,
  `gradle/libs.versions.toml`. Tests: `admin/DefaultAdminSessionTest.kt`,
  `admin/AdminCredentialStoreTest.kt`, additions to the transport and envelope tests.
- `feature/info`: create `AdminLoginPresenter.kt`, `AdminLoginUiModel.kt`, `AdminLoginScreen.kt`,
  `AdminLoginCopy.kt` (or extend `InfoCopy`), tests `AdminLoginPresenterTest.kt`; modify
  `InfoPresenter.kt`, `InfoUiModel.kt`, `InfoScreen.kt`, `InfoCopy.kt`, `di/InfoModule.kt`,
  `build.gradle.kts`, `InfoPresenterTest.kt`, `InfoModuleTest.kt`.
- `app`: `navigation/AppRoutes.kt`, `AppNavHost.kt`, `TabsShell.kt`, `AppRoutesTest`,
  `res/xml/backup_rules.xml`, `res/xml/data_extraction_rules.xml`.
- `konsist-test/.../ModuleIsolationTest.kt` (two data lists, see 5).
- Docs listed below. Nothing else; no file under `docs/sheet-seed` changes.

## Copy (Rioplatense, vos; A5)

| Where | Text |
|---|---|
| Login title / Info entry | `Entrar como admin` |
| Field label | `Frase de acceso` |
| Toggle | `Mostrar` / `Ocultar` |
| Button | `Entrar`; while checking `Verificando…` |
| Wrong | `La frase de acceso no es correcta.` |
| Offline | `No hay conexión. Para entrar como admin necesitás internet.` |
| Unavailable | `No se pudo verificar la frase de acceso. Probá de nuevo en un rato.` |
| Info, logged in | `Modo admin activo` and the action `Salir del modo admin` |
| Back | the shared `Volver` (`NavCopy`) |

## Visual Design Impact

UI: yes. Source: `DESIGN.md` tokens and `button-primary`, design prompt screen 7 ("pantalla mínima:
campo de frase de acceso y un botón… estado de error") and screen 6 ("acceso discreto"). Amber only
on "Entrar". Info's layout does not move: the admin line swaps text in place. No new token.

## Durable Documentation Impact

- `docs/apps-script-api.md`: update — `POST` transport, the `checkPassphrase` action, the four new
  codes, "passphrase only in a POST body", `Config` read only by POST actions.
- `backend/apps-script/README.md`: update — "Redeploy for the passphrase check" (add one file
  `Post`, paste, save, verify, Manage deployments → New version), **Verify the paste** row `Post.gs`
  must contain `checkPassphrase: checkPassphrase_,`, and a live check
  `curl -sL -d '{}' "$URL"` → `unknown_action … Known: checkPassphrase`; the GET check stays
  `Known: catalog, jams`.
- `docs/user-and-access-model.md`: update — what is stored (the verified passphrase, DataStore,
  excluded from backup), no re-check on start, logout on Info.
- `.claude/skills/architecture/SKILL.md`: update — `admin/` package, `AdminSession` as built,
  `AppsScriptPostTransport`, DataStore pin, the `:feature:info` amber entry, `ADMIN_LOGIN` route.
- `docs/risks-and-open-questions.md`: add — no rate limit on passphrase guesses (accepted for the
  MVP, revisit in `apps-script-write-auth`); the login UiModel carries the typed passphrase, which
  the phase 2 assistant context must exclude.
- `DESIGN.md`: update — the login screen and the Info admin line as built.
- `ARCHITECTURE.md` / `CONSTRAINTS.md`: not present; not needed. `AGENTS.md`: not needed.

## Implementation Plan

1. Backend `Post.js` + `post.test.js` + `load.js`; `node --test backend/apps-script/test/*.test.js`.
2. `:core:data`: catalog entry, transport POST, envelope `decodeOk`, store, session, module, tests.
3. Konsist list changes with their failing demonstrations.
4. `:feature:info`: Info admin state, login presenter/model/screen, copy, module, tests.
5. `:app`: route, host, shell, backup rules, `AppRoutesTest`.
6. Docs; gate; README redeploy steps handed to the user; device checks after the user's redeploy.

## Verification Plan

- `CI=true ./init.sh` exit 0, `konsist: wired` (17/17), `detekt: wired`, `ktlint: wired`.
- `node --test backend/apps-script/test/*.test.js`: previous 70 pass unchanged; `post.test.js`
  covers every row of the table in 1, asserts no body or message contains the stored or submitted
  passphrase, that `handlePost` only calls `getSheetByName('Config')`, and `doPost` on a
  non-JSON body.
- `DefaultAdminSessionTest` (fake post transport + temp-file store): each outcome mapping; stored
  only on `Success`; blank sends nothing; request JSON escapes quotes and backslashes and carries
  the trimmed value; `logOut` clears; `observeIsAdmin` false → true → false.
- `AdminCredentialStoreTest`: write with one instance, read `true` with a **new** instance over the
  same file (restart); corrupt file reads `false`.
- `OkHttpAppsScriptTransport` (MockWebServer): POST with the JSON body and no query; a 302 is
  followed as a GET; the passphrase never appears in any request URL.
- `AdminLoginPresenterTest` (Molecule + Turbine): Idle → Verifying → Error(wrong) with text kept;
  typing clears the error; Verifying → `onLoggedIn` called once; double Submit sends one login;
  blank disables Submit; `ToggleVisibility` flips `masked`; `Back` calls the current `onBack`.
- `InfoPresenterTest`: LoggedOut → LoggedIn when the fake session flips (a transition); `LogOut`
  calls `logOut`; `AdminEntryTapped` calls `onOpenAdminLogin`.
- **Failure demonstrations** (edit, run, restore; record messages): (1) import
  `androidx.datastore` in `:feature:info` → `data-libraries-only-in-core-data` fails; (2) remove
  the new allowlist entry → `amber-roles-allowlisted` fails on `primaryAction`; (3) store before
  checking the outcome → `DefaultAdminSessionTest` fails; (4) compare before the blank check in
  `Post.js` → `post.test.js` fails.
- Greps: no `passphrase` in any `Log.` call; `doGet`/`handleGet` unchanged (`git diff --stat
  backend/apps-script/src` shows only `Post.js`).
- **Live, after the user's redeploy (agent, curl):** `?resource=config` still `Known: catalog,
  jams`; `curl -sL -d '{}' "$URL"` → `unknown_action … Known: checkPassphrase`;
  `-d '{"action":"checkPassphrase","passphrase":"definitely-wrong"}'` → `invalid_passphrase`.
  The agent never sends the real passphrase.
- **Device, Pixel 5 (never edit the Sheet, never enable TalkBack or an accessibility service):**
  agent: Info → "Entrar como admin" → login; wrong passphrase via `adb shell input text` shows the
  error; airplane mode shows the offline copy (restore); `uiautomator dump` shows "Entrar" ≥ 48dp,
  field masked. **User (A3):** types the real passphrase once and taps Entrar. Agent: Info shows
  "Modo admin activo"; `am force-stop` + relaunch → still admin; "Salir del modo admin" → musician
  mode; crash buffer empty; settings restored; no passphrase in logcat.

## Evidence To Capture

Gate summary and counts, Node count, demonstration messages, `dependencyInsight` before/after,
curl outputs (codes only, never the URL), device dumps, in `feature_list.json` and `PROGRESS.md`.

## Validator Checklist

- [ ] Started after `past-jam-detail` committed; the login is in the outer host.
- [ ] Only `Post.js` changed under `backend/apps-script/src`; GET routes never open `Config`.
- [ ] Nothing stored unless the server said `ok`; blank never matches; no passphrase in a URL,
      log, message or saved-state Bundle; DataStore file excluded from backup.
- [ ] `AdminSession` in `:core:data`; no `:feature:admin`; constructor injection only.
- [ ] Amber only on "Entrar", allowlisted; text field colors explicit; copy as the table.
- [ ] Demonstrations recorded; three `wired`; docs updated; no write route added.

## Risks

- No rate limit: the public URL allows guessing (accepted, D-11; recorded).
- The stored passphrase is plaintext in app-private storage (a rooted device can read it).
- A device logged in before a rotation keeps showing admin mode until a write fails (by design).
- Until the user redeploys, login always answers `Unavailable` (HTML "doPost not found").

## User Approvals

- **A1 — Server-side check (recommended: yes).** Add a POST `checkPassphrase` action in a new file
  `Post.js`; no existing script file changes. Manual step for you: in Apps Script add a file `Post`,
  paste `src/Post.js`, save, verify one line, Manage deployments → New version (same URL). The
  alternative, checking nothing until the first write, cannot reject a wrong passphrase here.
- **A2 — Storage (recommended: DataStore Preferences, plaintext, backup-excluded).** Store the
  verified passphrase itself (writes will need to send it) with `androidx.datastore:datastore-preferences`
  (the D-11 choice, new dependency). Not `security-crypto`: deprecated by Google. Not Room: its
  destructive migrations would log the admin out.
- **A3 — Device check (recommended).** Confirm `Config` has a non-empty `passphrase`; you type it
  once on the Pixel 5 when asked. The agent never sees or sends it.
- **A4 — Lifetime and logout (recommended).** No expiry, no re-check on start; "Salir del modo admin"
  on Info, no confirmation dialog.
- **A5 — Copy** as in the Copy table.
- **A6 — Gate data changes (recommended).** `androidx.datastore.` joins the data-library rules, and
  `:feature:info` gets `{primaryAction, onPrimaryAction}` in the amber allowlist for "Entrar".
  Konsist stays at 17 rules.
- **A7 — Screen placement (recommended: full-screen route).** A separate screen in the outer host
  (design prompt screen 7; touches the three navigation files, so it waits for `past-jam-detail`).
  Alternative: an inline field inside Info's admin line, with no navigation change.
