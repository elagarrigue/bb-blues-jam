# Spec: admin-presence-notice

Planned 9 November 2026. Planner inspected the repository; assumptions are labelled **(assumption)**.

## Source feature

- id `admin-presence-notice`, area `feature-setlist`, status `not_started`, depends on `song-played-state` (accepted).
- Title: Warn an admin when another admin is in the same song detail (advisory, no lock).
- User request (9 Nov 2026): "¿Podemos soportar múltiples admins escribiendo a la vez? ... tal vez algún lock de escritura?"
  User decision when asked: **advisory only, no lock**.
- Verification (from the entry): node tests for the guarded action, TTL expiry and own-id exclusion; presenter JVM tests
  (heartbeat while open in admin mode, stops on leave, notice only for another id, failures silent); musicians never send;
  no write refused because of presence; `CI=true ./init.sh` exit 0 with konsist, detekt, ktlint `wired`.

## Goal and non-goals

Goal: while an admin has the admin song detail of an upcoming-jam song open and visible, the app tells the server "I am
here" every 30 s; another admin on the same song sees `Otro admin está editando este tema` under the title.

Non-goals (do not build):
- Any lock, refusal or write gating. Writes stay last-write-wins under the existing script lock. No new `WriteRejection`.
- Presence on Próxima jam rows, the Jams tab, Temas, the pickers or the assistant. Only the song detail.
- Names or counts of other admins in the UI (there are no admin identities; one shared passphrase).
- Storing presence in the Sheet or Room. Retry, backoff, error cards or logs for presence failures.
- A deeplink or narrow contract in `:app` for presence; the assistant never announces presence.

## Job story and acceptance scenarios

When two organizers edit the next jam from their phones at the same time, I want to know that someone else is on the song I
opened, so I can coordinate before overwriting their change, without anything being blocked.

1. **Notice for another admin.** Given admin A has song S (upcoming jam) open and visible, when admin B opens S's detail,
   then within B's first announce answer B sees `Otro admin está editando este tema`; A sees it at A's next heartbeat (≤ 30 s).
2. **Alone.** Given nobody else announced S, when an admin opens S, then no notice is drawn.
3. **Own id excluded.** Given the same install announced S before (e.g. it left and came back, or the process restarted),
   then it never sees a notice caused by its own holder id.
4. **Leaving clears.** Given A and B on S, when B leaves the detail (back, another screen above it, app to background, logout),
   then B's heartbeat stops at once and B sends a best-effort release; A's notice disappears at A's next heartbeat (≤ 30 s), or at
   the latest when B's entry expires (120 s) if the release was lost.
5. **Musicians never send.** Given a device not in admin mode, when it opens any song detail, then `SongPresence` is never called.
6. **Past jam / not found.** Given an admin opens a past jam's song (read-only detail) or a not-found song, then nothing is sent.
7. **Failures silent.** Given offline, an old script (`unknown_action`), `rate_limited`, `internal_error` or a malformed
   answer, then no card, no error text and no log line; the notice keeps its last known value until the detail is left.
8. **Nothing refused.** Given A and B both on S, when both change the key, then both writes go through as today (the later wins).

## Repository research (inspected)

- `AGENTS.md`, `PROGRESS.md` (9 Nov entries), `feature_list.json` (entry above), `.claude/skills/architecture/SKILL.md`.
- `backend/apps-script/src/Post.js`: `ACTIONS` (lines ~156–185, `setSongPlayed` last), `doPost` builds
  `services = { cache: CacheService.getScriptCache(), lock, now, flush }`, `handlePost` runs the guard for every action and the
  lock only for `write: true`; `requirePassphrase_` uses the cache for `auth_failures_<window>`; `addSong_` shows the
  `isCalendarDate_` / `SONG_ID_` validation and `invalid_date` / `invalid_song` codes.
- `backend/apps-script/test/post.test.js`: `fakeCache` (Map; `get`, `put`, no `remove` yet), `fakeServices`, `post(...)`.
- `docs/apps-script-api.md`: router/guard section, `unknown_action` Known lists, Client write path (`AdminWriter`), Quotas.
- `docs/migrations/song-played-state-script-update.md` (format of the migration guide and deploy probe).
- `core/data/.../admin/AdminWriter.kt` (`send(action, fields): AdminAnswer`, `Ok(body)` / `Refused`), `AdminCredentialStore.kt`
  (okio DataStore factory), `musician/DefaultMusicianFilterPreference.kt` (own DataStore file through that factory),
  `DataScope.kt`, `di/DataModule.kt`.
- `feature/next-jam/.../songdetail/SongDetailPresenter.kt`, `SongDetailUiModel.kt`, `SongDetailScreen.kt` (title block draws
  artist, `playedLabel`, `SaveStatusLine`), `di/NextJamModule.kt` (`factory { SongDetailPresenter(get(), get(), get()) }`),
  `NextJamScreen.kt` (`isResumed` from `LocalLifecycleOwner` lifecycle, pattern of `live-refresh-during-jam`).
- `app/src/main/res/xml/backup_rules.xml`, `data_extraction_rules.xml` (exclude `admin_session`).
- `bb-blues-jam-bitacora.md`: last decision is D-31.

Not inspected: `router.test.js` in detail (it may assert the action list; update it if so), `AppNavHost.kt` beyond the
`SongDetailScreen` call (no change expected there).

## Technical approach

### Server (`backend/apps-script/src/Post.js`)

New action `announcePresence: { write: false, run: announcePresence_ }`, **last** in `ACTIONS`. `write: false` on purpose: it
never touches the Sheet, so it must not take the script lock (it would wait behind writes and could make writes `busy`). The
router's passphrase guard runs as for every action (and counts wrong passphrases as today).

Request: `{"action":"announcePresence","passphrase":"…","date":"YYYY-MM-DD","songId":"<slug>","holder":"<id>","leaving":true?}`.
Validation, in order, before touching the cache: `invalid_date` (`isCalendarDate_`), `invalid_song` (`SONG_ID_`),
`invalid_holder` (string matching `^[A-Za-z0-9-]{16,64}$`). `leaving` counts only when it is exactly `true`. No jam or song
existence check (no Sheet read beyond the guard's `Config` read).

Cache, pure helpers so node can test them with `services.now` and `fakeCache`:
- Key `presence_<date>_<songId>` (`PRESENCE_KEY_PREFIX_`); value JSON `{ "<holder>": <expiresAtMs>, … }`.
- Constants `PRESENCE_TTL_MS_ = 120000`, `PRESENCE_TTL_SECONDS_ = 120`, `PRESENCE_MAX_HOLDERS_ = 10`.
- `run`: read and parse (unparseable or non-object reads as `{}`), drop entries with `expiresAt <= now`, count `others` = entries
  whose holder ≠ request holder; then if `leaving` delete the holder, else set it to `now + TTL`; keep at most the 10 latest
  expiries; write back with `cache.put(key, json, 120)`, or `cache.remove(key)` when empty. Answer `{ ok: true, others }`.
- Not atomic (no lock): two announces in the same instant may lose one entry; the next heartbeat restores it. Documented.
- A cache exception propagates to the router as `internal_error` (the client treats it as unknown, silently).

Quota note: one POST per open admin detail every 30 s plus one on leave; with 2–3 admins this is negligible next to jam-night
polling. CacheService is not the Sheet: it never changes data and needs no migration of the Sheet.

### `:core:data`, new package `presence/`

- Public `SongPresence`: `suspend fun announce(jamDate: LocalDate, songId: SongId): PresenceAnswer` and
  `fun leave(jamDate: LocalDate, songId: SongId)` (fire-and-forget, launched in `DataScope` so it survives the screen leaving).
- Public `sealed interface PresenceAnswer { data object Alone; data object OthersHere; data object Unknown }`.
- Internal `DefaultSongPresence(writer: AdminWriter, holders: PresenceHolderStore, scope: DataScope)`: sends
  `writer.send("announcePresence", fields)` with `date`, `songId`, `holder` (and `leaving = true` for `leave`). `Ok(body)` with
  an integer `others >= 0` → `OthersHere` when `> 0`, else `Alone`; anything else (`Refused`, missing/invalid `others`, no
  holder id) → `Unknown`. `AdminWriter` already sends nothing without a stored passphrase. Catch nothing broad (follow the
  "never catch `Exception`" rule; `IOException` from the store → `Unknown`). Never logs.
- Internal `PresenceHolderStore(dataStore)`: DataStore file `presence_holder` (built with `AdminCredentialStore.dataStore { … }`),
  key `holder_id`; `suspend fun holderId(): String?` returns the stored id or creates `UUID.randomUUID().toString()` inside one
  `dataStore.edit` (atomic), so the id is stable per install. An `IOException` returns null.
- `dataModule`: `single { PresenceHolderStore(PresenceHolderStore.dataStore(get())) }` and
  `single<SongPresence> { DefaultSongPresence(get(), get(), get()) }`. Not part of `SetlistRepository`, not on
  `SetlistMutationQueue` (presence must not wait behind writes), no `:app` binding.

**D-13:** presence is not a domain mutation. It changes no entity the Sheet or the app owns, is never persisted, authorizes
nothing and has no effect a human admin needs the phase 2 assistant to reproduce. It still lives behind a repository
function in `:core:data` (never a UI-only lambda), so the letter of D-13 holds as well.

### `:feature:next-jam` (`songdetail/`)

- `SongDetailPresenter(jams, adminSession, sources, presence: SongPresence)`; Koin `factory { SongDetailPresenter(get(), get(), get(), get()) }`.
- `Params.isResumed: Boolean = false` (same convention as `NextJamPresenter`: a presenter nobody marks visible never sends).
  `SongDetailScreen` reads `LocalLifecycleOwner.current.lifecycle.currentStateAsState()` and passes
  `isResumed = state.isAtLeast(RESUMED)`, as `NextJamScreen` does. No `:app` change.
- `active = isAdmin == true && params.isResumed && model is Song && model.admin != null` (the admin detail of an upcoming-jam
  song; played songs included, since move and un-play still apply). Build the model first, then derive `active`, then add the notice.
- New file `songdetail/SongPresenceEffect.kt`:
  - pure `suspend fun runPresenceHeartbeat(interval: Duration, announce: suspend () -> PresenceAnswer, onAnswer: (PresenceAnswer) -> Unit)`:
    loops forever: `onAnswer(announce())`, then `delay(interval)` (measured from the answer, so one device never overlaps itself).
  - `PresenceHeartbeat.INTERVAL = 30.seconds` (the server TTL is 120 s: three heartbeats may be lost before the entry expires).
  - `@Composable rememberOthersHere(presence, active, jamDate, songId): Boolean`: a `LaunchedEffect(active, jamDate, songId)`;
    when `active`, `try { runPresenceHeartbeat(...) { answer -> when (answer) { OthersHere -> true; Alone -> false; Unknown -> keep } } }
    finally { presence.leave(jamDate, songId); othersHere = false }`. `othersHere` is plain `remember` state (not saveable).
- `SongDetailAdminUiModel.presenceNotice: UiText? = null` = `UiText.res(R.string.song_detail_presence_notice)` while `othersHere`.
- Screen: drawn in the title block after `SaveStatusLine`, `caption` style, colour `SongDetailDefaults.presenceNoticeColor(colors)`
  = `colors.text` (never amber, never `error`: it is information, not a failure), polite live region.
- String (`feature/next-jam/src/main/res/values/strings.xml`): `song_detail_presence_notice` = `Otro admin está editando este tema`.

## Expected file changes

| File | Change |
|---|---|
| `backend/apps-script/src/Post.js` | `announcePresence` entry, `announcePresence_`, pure presence helpers, constants, header comment |
| `backend/apps-script/test/presence.test.js` (new) or `post.test.js` | node tests below; `fakeCache` gains `remove` |
| `backend/apps-script/test/router.test.js` | only if it asserts the action list |
| `core/data/src/main/kotlin/com/bbbjam/core/data/presence/` (new) | `SongPresence.kt`, `PresenceAnswer.kt`, `DefaultSongPresence.kt`, `PresenceHolderStore.kt` |
| `core/data/src/main/kotlin/com/bbbjam/core/data/di/DataModule.kt` | two `single`s |
| `core/data/src/test/kotlin/com/bbbjam/core/data/presence/` (new) | `DefaultSongPresenceTest`, `PresenceHolderStoreTest` |
| `feature/next-jam/.../songdetail/SongDetailPresenter.kt`, `SongDetailUiModel.kt`, `SongDetailScreen.kt`, `SongDetailDefaults.kt`, `SongPresenceEffect.kt` (new) | as above |
| `feature/next-jam/.../di/NextJamModule.kt` | fourth `get()` |
| `feature/next-jam/src/main/res/values/strings.xml` | one string |
| `feature/next-jam/src/test/kotlin/com/bbbjam/feature/nextjam/` | `FakeSongPresence` (test root, one consumer), `SongPresenceTest`, existing `SongDetailPresenter` tests construct with the fake |
| `app/src/main/res/xml/backup_rules.xml`, `data_extraction_rules.xml` | exclude `datastore/presence_holder.preferences_pb` (a restored id on a second phone would hide two admins from each other) |

No Gradle, module, Room or Konsist change. `:feature:next-jam` gains no amber role.

## Implementation tasks (in order)

1. Server: helpers + action + node tests (red first for own-id exclusion and expiry). `node --test backend/apps-script/test`.
2. `:core:data` `presence/` + DataModule + JVM tests.
3. Presenter, effect, UiModel, screen, string, defaults, Koin; JVM tests; update existing detail tests for the new constructor.
4. Backup rules.
5. Docs (below). 6. `./gradlew ktlintFormat`, then `CI=true ./init.sh`.

## Verification plan

Node (`node --test`, part of the gate only if `init.sh` already runs it; run it explicitly either way):
- guard: without/with wrong passphrase → `invalid_passphrase`, cache untouched by the action; the action takes no lock (`lock.tries` empty).
- validation: `invalid_date`, `invalid_song`, `invalid_holder` before any cache call.
- first announce → `others: 0`, entry stored with TTL 120; second holder → `others: 1`; same holder again → `others: 0` (own id excluded).
- expiry: an entry with `expiresAt <= now` is not counted and is dropped on write.
- `leaving: true` removes the holder (and `remove`s the key when empty) and still answers the count of others.
- garbage cache value reads as empty; more than 10 holders keeps the 10 latest; `deployProbe` message ends with `setSongPlayed, announcePresence`.

JVM (`:core:data`): fields sent (`date`, `songId`, `holder`, `leaving` only on leave); `others` 0/2/missing/negative/non-int →
`Alone`/`OthersHere`/`Unknown`; `Refused` → `Unknown`; holder id created once and stable across a store restart (cancel scope,
reopen, same id); `leave` completes after the caller is cancelled (runs in `DataScope`).

JVM (`:feature:next-jam`, Molecule + `runTest`):
- `runPresenceHeartbeat` in virtual time: announces at 0, 30 s, 60 s; next delay measured from the answer.
- admin + resumed + upcoming song → `announce` called, notice null for `Alone`, set for `OthersHere`.
- `Unknown` after `OthersHere` keeps the notice; `Unknown` first → no notice; no failure card in either case.
- musician (resumed) → zero calls; admin with `isResumed = false` → zero calls; admin on a past jam's song → zero calls.
- resumed → not resumed (and admin → logged out) → `leave` called once, heartbeat stops (no further announces), notice cleared.
- the detail's writes are unchanged (an existing key-change test still passes with presence answering `OthersHere`).

Gate: `CI=true ./init.sh` exit 0, tail shows `konsist: wired`, `detekt: wired`, `ktlint: wired`.

Device (manual, optional, needs two admin devices; not in the gate): open the same song on both, see the notice on each within
30 s; back on one, the other's notice clears within 30 s. The live script must be redeployed first.

## Evidence to capture

Node and Gradle test counts, the `init.sh` tail, and after the user's redeploy the deploy probe line (no passphrase, no write).

## Durable documentation impact

- `docs/apps-script-api.md`: update (new `announcePresence` section: request, validation, cache shape, TTL, non-atomicity, not a
  write, no lock; append to both `unknown_action` Known lists; a Quotas line).
- `docs/migrations/admin-presence-notice-script-update.md`: create (only `Post.gs` changes; paste-verify strings
  `announcePresence: { write: false, run: announcePresence_ },` and `function announcePresence_`; Manage deployments → New version;
  probe `{"action":"deployProbe"}` must end `..., setSongPlayed, announcePresence`; until redeployed the app simply never shows the
  notice, nothing else changes; never write the Sheet).
- `bb-blues-jam-bitacora.md`: add **D-32 — Aviso de otro admin en el mismo tema, sin bloqueo** (Spanish): advisory presence
  only, CacheService not the Sheet, 120 s expiry, protected by the passphrase, authorizes nothing, writes last-write-wins;
  decision of the organizer of 9 November 2026. It is a product rule about concurrent admins, so it earns a number.
- `DESIGN.md` (Song detail order): add the notice line after `Guardando…`.
- `.claude/skills/architecture/SKILL.md`: "As built by `admin-presence-notice`" paragraph.
- `docs/user-and-access-model.md`: one line (presence is passphrase-guarded, never authorizes).
- `PROGRESS.md`, `feature_list.json`: evidence. `AGENTS.md`, `CONTEXT.md`, `docs/domain-model.md`: not needed (no entity, no term).

## Validator checklist

- No lock, no refusal, no `WriteRejection` added; `announcePresence` is `write: false` and guarded by the router only.
- Presence never reaches the Sheet or Room; holder id in its own DataStore file, excluded from backup and transfer.
- Musicians, non-resumed screens and past-jam details send nothing; heartbeat 30 s, TTL 120 s, release on leave.
- Failures produce no card, text or log; notice copy in `strings.xml`, vos-neutral Spanish, never amber.
- `SongPresence` is not in `SetlistRepository`, not on the mutation queue, not bound in `:app`.
- Docs above updated, D-32 present; `init.sh` all three `wired`.

## Open points (defaults chosen, change only if the user objects)

1. Heartbeat 30 s / TTL 120 s; the other admin learns of an arrival or a departure within ≤ 30 s.
2. Explicit release on leave (best effort) in addition to TTL.
3. A failed announce keeps the last known notice rather than hiding it.
4. Played songs still announce presence.
5. The answer carries only a count, never holder ids.
