# Feature Implementation Spec: Hide a draft setlist from musicians

## Source Feature

- `id`: unpublished-setlist-state
- `area`: feature-next-jam
- `depends_on`: `list-states` (`accepted`)
- `status`: not_started at planning time
- `source`: `feature_list.json` (entry, verification, notes); `DESIGN.md` "Required States" (sixth
  case); `bb-blues-jam-design-prompt.md` §1 "Estado sin publicar"; `docs/domain-model.md` (DRAFT).
- **Sequencing:** `past-jam-detail` and `bottom-navigation` are being planned at the same time. This
  slice edits `:core:model` `Jam.kt`, `:feature:next-jam`, one line of `:feature:song-detail` and
  the `:app` debug demo. If one of those slices lands first, re-read the files listed below before
  starting.

## Goal

While the upcoming jam is a draft, a musician on Próxima jam sees the header (date and time,
venue, time remaining) and a **designed draft block** saying that the list is being assembled.
It shows no song, no key, no strip and no filter bar. The block must look different from the empty
state ("Todavía no hay temas").

Most of the hiding is already done and stays as it is. The Apps Script `jams` route never serves
a non-`PUBLICADA` setlist, and `JamsMapper` turns every `DRAFT` into `Setlist.Withheld`, ignoring
and reporting any setlist that arrives with one. This slice adds a third, client-side layer. The
musician screens decide from **`Jam.status`**, not only from the setlist state, so a draft that
reaches them with an `Available` setlist still shows no song. A later admin read will produce
exactly that state (`Jam` KDoc: "a draft may be withheld (a musician's read) or available (the
admin's)").

## Non-Goals

- The admin view of a draft: the `Borrador`/`Publicada` badge, publish action, admin read
  (`admin-passphrase-login`, `apps-script-write-auth`, `admin-publish-setlist`). No `AdminSession`.
- Any change to `backend/apps-script/`, `:core:data`, the Room cache or the Sheet. Server-side
  withholding is already in place and is re-verified only (Verification §3).
- Past jams: Anteriores and `past-jam-detail` own their draft wording ("La lista de esta jam no se
  publicó."). This slice does not touch `:feature:past-jams`.
- No mutation (D-13), no new Gradle dependency, no new token, no Konsist rule, no `init.sh` change.
- The unavailable setlist line keeps its copy and its look (one `body` line in `textMuted`).

## Job Story

When I open the app days before the jam and the organizers are still choosing songs,
I want to see the date and place and know the list is on its way,
so I don't read an empty screen as "no songs this month", and I come back later.

## Users And Permissions

- Musician (anonymous): sees header + draft block; never a draft's songs, from any source.
- Admin: no admin state exists yet; the admin sees what a musician sees until the admin slices.

## Decisions

1. **The musician rule lives in `:core:model`.** Add to `Jam`:
   `fun setlistForMusicians(): Setlist = if (status == JamStatus.DRAFT) Setlist.Withheld else setlist`
   with KDoc: a draft is never shown to a musician, whatever the reader received. It lives here
   because "draft → not shown" is a domain rule (domain-model.md, Jam status) that every musician
   screen applies, `past-jam-detail` included. The admin slices will read `jam.setlist`
   directly when the admin flag is on.
2. **Callers.** `NextJamPresenter`'s `toUiModel` maps `jam.setlistForMusicians()` instead of
   `jam.setlist`. In `SongDetailPresenter.kt`, `toSongDetail` looks the song up in
   `jam?.setlistForMusicians()`. Without that, the detail route of a draft song would still open
   (scenario 4). The detail's existing `NotFound` block is the result, and no new copy is needed.
3. **UiModel split.** `SetlistUiModel.NotShown(message)` is removed and replaced by:
   - `data class Withheld(val draft: DraftSetlistUiModel) : SetlistUiModel`
   - `data class Unavailable(val message: String) : SetlistUiModel` (same copy as today)
   - new `data class DraftSetlistUiModel(val label: String, val title: String, val message: String) : UiModel`
     in `NextJamUiModel.kt`.
   Every non-`Available` setlist and every `DRAFT` jam produces no `SongRowUiModel` and no
   `filterBar`. `staleness` keeps its current rule, so it is drawn above the header when a refresh
   failed. Expansion and filter state are untouched; they simply have no rows to apply to.
4. **Visual (V1, user approval requested).** A `DraftSetlistBlock(model)` composable (private, in
   `NextJamScreen.kt` or a new `DraftSetlistBlock.kt` in the feature), drawn as the item after the
   header:
   - a `Surface` with `surface` fill and `shapes.md`, inner padding `spacing.md`, children spaced
     `spacing.sm`. The empty block has no fill, so this card is the first visible difference;
   - a **badge** (DESIGN.md `badge-draft`): `label.uppercase()` in `caption`, `textMuted` on a
     `surfaceRaised` fill, `shapes.sm`, padding `spacing.sm` × `spacing.xs`, wrapped to its
     content. It names the state and is the second visible difference;
   - the **title** in `songTitle`, `text`, marked `heading()`;
   - the **message** in `body`, `textMuted`.
   **No amber.** Draft is muted per DESIGN.md "Status badge", and `:feature:next-jam` stays at
   `{key}`. Colours are read only through a new `internal object DraftSetlistDefaults` with
   `style(colors: BluesJamColors = BluesJamColors): DraftStyle(cardFill, badgeFill, badgeText,
   title, message)` = `surface`, `surfaceRaised`, `textMuted`, `text`, `textMuted`, following the
   `PastJamsDefaults` pattern. No dp literal is needed (Konsist `no-dp-literal-outside-core-ui`).
   Contrast is already measured by `ContrastTest`: `textMuted` on `surfaceRaised` ("muted text on a
   raised surface") and on `surface` (10.10:1).
5. **Screen-reader order.** Badge, then title (heading), then message, as three nodes. Nothing in
   the block is clickable.
6. **Copy (C1, user approval requested).** It replaces `NextJamCopy.SETLIST_WITHHELD` with three
   constants (below). `SETLIST_UNAVAILABLE` is unchanged.
7. **Debug demo, draft variant (device check only).** A new flag in the git-ignored
   `local.properties`, `bluesjam.demoUpcomingJamDraft=true`, becomes `BuildConfig
   .DEMO_UPCOMING_JAM_DRAFT`: the flag's value in `debug` and hard `false` in `release`, read in
   `app/build.gradle.kts` like the existing flag. It has effect only when
   `bluesjam.demoUpcomingJam=true`. Then `DemoUpcomingJam.on(today, draft = true)` builds the same
   demo jam with `status = DRAFT` and the **same `Available` songs**. That is deliberately the
   strongest case: the songs are in the data, and the screen must still hide them.
   `DemoUpcomingJamRepository` takes the `draft` boolean in its constructor, and
   `Koin.debugOverrides()` passes `BuildConfig.DEMO_UPCOMING_JAM_DRAFT`. "A real upcoming jam
   always wins" is unchanged. The startup line `jams cache:` appends ` draft` after an upcoming
   date whose status is `DRAFT` (for example `upcoming 2026-10-15 draft (demo)`, real jams
   included). The release no-ops are unchanged. Main code changes only in `toCacheLine`.

## Copy (user approval requested, C1; Rioplatense, vos, D-12)

| Constant (`NextJamCopy`) | Text |
|---|---|
| `DRAFT_LABEL` | En preparación (drawn uppercase: `EN PREPARACIÓN`) |
| `DRAFT_TITLE` | La lista se está armando |
| `DRAFT_MESSAGE` | Cuando la organización la publique, vas a ver acá los temas, las tonalidades y los cupos libres. |

Compare the empty state: `Todavía no hay temas` / `La lista está publicada pero todavía no tiene
temas. ¿Tenés uno en mente? Contáselo a la organización.` The draft copy never says "no hay".

## Acceptance Scenarios

1. **Draft, withheld (the real path).** Given an upcoming `DRAFT` jam with `Setlist.Withheld`, then
   the model is `Jam(header, Withheld(DraftSetlistUiModel(DRAFT_LABEL, DRAFT_TITLE, DRAFT_MESSAGE)),
   staleness = null)`. The model holds no rows and no filter bar.
2. **Draft, available (defence in depth).** Given an upcoming `DRAFT` jam whose setlist is
   `Available` with songs, then the model is the same as in scenario 1. No title, key or musician
   name of those songs appears anywhere in the model (assert on `toString()`).
3. **Distinct from empty.** Given a `PUBLISHED` jam with `Available(emptyList())`, the model is
   `Empty(...)`, not `Withheld`. The two copies share no sentence, and the draft card has a fill
   while the empty block has none.
4. **Detail closed.** Given a `DRAFT` upcoming jam with `Available` songs, then
   `toSongDetail(date, 1)` is `NotFound`.
5. **Published unchanged.** `PUBLISHED` + `Available`/`Unavailable` behave as before (rows; the
   unavailable line, now `Unavailable(message)`).
6. **Offline with a cached draft.** Given a cached draft and a failed refresh, the staleness notice
   sits above the header and the draft block follows.
7. **Server and mapper.** The existing Node and mapper tests still show that a non-`PUBLICADA`
   jam's tab is never requested or serialized, and that a setlist sent with a draft is ignored.

## Repository Research

Inspected: `AGENTS.md`, `PROGRESS.md`, `feature_list.json`, `CONTEXT.md` (via AGENTS),
`.claude/skills/architecture/SKILL.md`, `DESIGN.md` (tokens, Status badge, Required States),
`bb-blues-jam-design-prompt.md` §1–2, `docs/domain-model.md`, `docs/user-and-access-model.md`,
`docs/apps-script-api.md` (the draft rule), `docs/sheet-schema.md` (mapper setlist state), specs
`next-jam-read-only-list`, `list-states`, `jams-repository-cache`, `debug-demo-upcoming-jam`;
code `feature/next-jam/src/main/.../{NextJamPresenter,NextJamUiModel,NextJamCopy,NextJamScreen,
NextJamStatesPreview}.kt`, its tests `NextJamPresenterTest` (l. 439 withheld test) and
`NextJamStatesTest` (l. 158), `core/model/.../Jam.kt`, `core/ui/.../state/ListStateBlocks.kt`
(`EmptyStateBlock`: title + message, no fill), `feature/past-jams/.../PastJamsDefaults.kt` and its
test, `feature/song-detail/.../SongDetailPresenter.kt` (l. 50–51) and `SongDetailMappingTest`,
`app/src/debug/.../{DebugOverrides,DemoUpcomingJam,DemoUpcomingJamRepository}.kt`,
`app/src/release/.../DebugOverrides.kt`, `app/src/testDebug/.../DemoUpcomingJamRepositoryTest.kt`,
`app/build.gradle.kts`, `app/src/main/java/com/bbbjam/BluesJamApp.kt`, `ContrastTest` names,
`backend/apps-script/test/{jams,router}.test.js` (draft tests), `JamsMapperTest` l. 196.

Findings: a withheld setlist currently draws `NotShown(SETLIST_WITHHELD)` as one muted `body`
line. That line looks almost the same as the empty block's message. The presenter branches on the
setlist state only and never reads `status`. No Compose UI test harness exists (declined, T1 of
`song-row-expansion`), so drawn semantics are checked on the device with `uiautomator dump`.

## Expected File Changes

- `core/model/src/main/kotlin/com/bbbjam/core/model/Jam.kt`: add `setlistForMusicians()`.
- `core/model/src/test/kotlin/com/bbbjam/core/model/JamTest.kt`: four cases (below).
- `feature/next-jam/.../NextJamUiModel.kt`: `Withheld`, `Unavailable` and `DraftSetlistUiModel`;
  remove `NotShown`.
- `feature/next-jam/.../NextJamCopy.kt`: the C1 constants; remove `SETLIST_WITHHELD`.
- `feature/next-jam/.../NextJamPresenter.kt`: map `jam.setlistForMusicians()`; new branches.
- `feature/next-jam/.../NextJamScreen.kt` (+ optional `DraftSetlistBlock.kt`): draw both states.
- `feature/next-jam/.../DraftSetlistDefaults.kt`: create.
- `feature/next-jam/.../NextJamStatesPreview.kt`: add `DraftSetlistPreview` and
  `DraftSetlistOfflinePreview`.
- `feature/next-jam/src/test/.../NextJamPresenterTest.kt` and `NextJamStatesTest.kt`: update the
  two `NotShown` tests and add the scenarios.
- `feature/next-jam/src/test/.../DraftSetlistDefaultsTest.kt`: create.
- `feature/song-detail/.../SongDetailPresenter.kt` (one line) and `SongDetailMappingTest.kt`
  (one test).
- `app/build.gradle.kts`, `app/src/debug/.../{DebugOverrides,DemoUpcomingJam,
  DemoUpcomingJamRepository}.kt`, `app/src/main/java/com/bbbjam/BluesJamApp.kt` (`toCacheLine`),
  `app/src/testDebug/.../DemoUpcomingJamRepositoryTest.kt`.

## Visual Design Impact

- UI involved: yes. Source: `DESIGN.md` (tokens, `badge-draft`, Status badge, Required States).
- States affected: Próxima jam with a draft upcoming jam, and the same with the staleness notice.
- No new design artifact. `DESIGN.md` gains an "as built" paragraph under Required States.

## Durable Documentation Impact

- `DESIGN.md` (update): the unpublished-setlist paragraph gets the as-built treatment (card,
  badge, copy, no amber).
- `.claude/skills/architecture/SKILL.md` (update): one "Where each piece goes" line saying that
  musician screens read `Jam.setlistForMusicians()`, never `jam.setlist`, and that the admin slices
  read `setlist` behind the admin flag. Also add the draft demo flag to the "Debug demo jam" note.
- `docs/domain-model.md` (update): one sentence under DRAFT naming `setlistForMusicians()`.
- `docs/technical-discovery.md` (update): the draft demo flag beside the existing demo flag note.
- `docs/user-and-access-model.md` (update): the draft rule bullet gains the third,
  client-side layer.
- `ARCHITECTURE.md` and `CONSTRAINTS.md` do not exist: not needed. `AGENTS.md`: not needed, because
  no command changes. `CONTEXT.md`: not needed, because no new term is added ("withheld" and
  "draft" are already defined).
- `PROGRESS.md` and `feature_list.json`: the implementer's evidence; status `passing`, never `accepted`.

## Implementation Plan

1. `:core:model`: add `setlistForMusicians()` and its tests.
2. `:feature:next-jam`: UiModel split, copy, presenter mapping, `DraftSetlistDefaults`, screen
   block, previews, tests. Grep for `NotShown`/`SETLIST_WITHHELD` until none remain.
3. `:feature:song-detail`: the one-line lookup change and its test.
4. `:app` debug: the flag, `on(today, draft)`, the decorator parameter, the log suffix and tests.
5. Failure demonstrations (Verification §2), docs, then the gate and the device check.

## Verification Plan

1. **Gate.** `CI=true ./init.sh` exit 0, printing `konsist: wired` (17/17, unchanged),
   `detekt: wired`, `ktlint: wired`. Report result-file and test counts against the previous
   baseline (62 files, 350 tests as of session 065, or the latest accepted baseline).
2. **JVM tests** (all inside the gate):
   - `JamTest`: `DRAFT`+`Withheld` → `Withheld`; `DRAFT`+`Available(songs)` → `Withheld`;
     `PUBLISHED`+`Available` → the same instance; `PUBLISHED`+`Unavailable` → the same instance.
   - `NextJamPresenterTest`: scenarios 1, 2 (including the `toString()` check that no song title
     or musician name appears) and 5, both through `toUiModel` and through a Molecule flow; a
     filter selected before a draft emission still yields no rows and no bar.
   - `NextJamStatesTest`: scenarios 3 and 6; this is the **feature's "presenter test asserts no
     songs are exposed for a DRAFT jam"**.
   - `DraftSetlistDefaultsTest`: the roles are exactly those of Decision 4. Every colour is in
     `{surface, surfaceRaised, text, textMuted}`, so it is never amber, with `BluesJamColorsTest`.
     The card fill is non-null and `surface`, which is the fill the empty block lacks.
   - `SongDetailMappingTest`: scenario 4.
   - `DemoUpcomingJamRepositoryTest`: `draft = true` fills an empty upcoming with a `DRAFT` jam
     whose songs equal the published demo's; `draft = false` is unchanged; a real upcoming still
     wins with `draft = true`.
   - **Failure demonstrations**, each reverted and confirmed by SHA-1. (a) Map `jam.setlist`
     instead of `setlistForMusicians()` in `NextJamPresenter`: the scenario 2 test fails. (b) Make
     `setlistForMusicians()` return `setlist`: the `JamTest` draft+available case fails. (c) Use
     `Setlist.Withheld -> SetlistUiModel.Empty(...)`: the scenario 1 or 3 test fails. Record each
     failing test name.
3. **Server-side, outside the gate.** Run `node --test backend/apps-script/test/*.test.js` and
   record the totals. The draft tests in `jams.test.js` (l. 97, 114, 120) and `router.test.js`
   (l. 147, 171, 182) must pass unchanged, which is the feature's "read endpoint does not serve
   draft songs". No backend file changes. Never print or write the Apps Script URL.
4. **Release.** Run `./gradlew :app:assembleRelease`, which must exit 0, and confirm no
   `DemoUpcomingJam` class is in the release dex, using the same command recorded by
   `debug-demo-upcoming-jam`.
5. **Device (Pixel 5, manual, outside the gate).** Never add or delete jams in the Sheet, and never
   enable TalkBack or any accessibility service.
   - Set `bluesjam.demoUpcomingJam=true` and `bluesjam.demoUpcomingJamDraft=true`, then run
     `./gradlew :app:installDebug` and launch. In logcat (tag `BluesJam`), `jams cache:` must read
     `upcoming <date> draft (demo)`. If it shows a real date without `(demo)`, the Sheet has a
     real upcoming jam: clear the app's data in airplane mode and relaunch, as the architecture
     skill says. Do not touch the Sheet.
   - Screenshot Próxima jam. It must show the header with venue `Demo (solo debug)`, then the card
     with `EN PREPARACIÓN`, the title and the message. It must show no song row, no filter bar and
     no amber.
   - Run `adb shell uiautomator dump`. The badge, title and message must be separate text nodes,
     the title must be the only new heading, and no node may hold a demo song title (for example
     "Sweet Little Angel").
   - Airplane mode on, then relaunch: the staleness notice must appear above the header and the
     card follows. Restore airplane mode.
   - Set `bluesjam.demoUpcomingJamDraft=false` (keep the demo flag on as PROGRESS records),
     reinstall, and confirm the published demo list is back. Leave the draft flag off or absent.
     Confirm the crash buffer is empty (`adb logcat -b crash -d`).

## Evidence To Capture

The gate summary (three `wired`, counts), the new and updated test names with their counts, the
three failure demonstrations (failing test names and SHA-1 restore), the Node totals, the release
dex check, the device log line, the screenshot description, the dump findings, and the restored
settings, all recorded in `PROGRESS.md` and the feature's `evidence`.

## Validator Checklist

- [ ] No `NotShown` or `SETLIST_WITHHELD` remains, and `Withheld` and `Unavailable` are separate types.
- [ ] Musician screens never read `jam.setlist` for an upcoming or detail lookup, only
      `setlistForMusicians()` (grep `\.setlist\b` in `feature/next-jam` and `feature/song-detail`
      main).
- [ ] A `DRAFT` jam with `Available` songs exposes no song in the next-jam model or the detail.
- [ ] The draft block reads colours only through `DraftSetlistDefaults` and reads no amber.
      `AMBER_ROLE_ALLOWLIST` is unchanged.
- [ ] The copy matches C1 as approved, or as the user amended it.
- [ ] No change under `backend/`, `core/data/`, `feature/past-jams/`, or to Konsist and `init.sh`.
- [ ] The demo draft code is only in `app/src/debug`. Release has `DEMO_UPCOMING_JAM_DRAFT = false`
      and no demo class in the dex.
- [ ] The device evidence uses the demo, not Sheet edits, and enables no accessibility service.

## Risks

- **Concurrent slices.** `past-jam-detail` may also want a draft guard. It should call
  `setlistForMusicians()` rather than write its own, but this spec does not edit that spec.
- **The `toCacheLine` format changes** (` draft` suffix). Only logs and evidence read it; no test
  in `app/src/test` or `app/src/testDebug` parses it (checked at planning time).
- **The Sheet may hold a real upcoming jam.** In that case the demo needs the clear-data-offline
  step, and PROGRESS still lists the user's 2026-10-31 to-do.

## User Approvals

- **C1 — Draft copy.** Approve the three strings in the Copy table? *Recommended: approve as
  written.* Alternatives: keep only today's approved line (`La lista de temas se está armando.
  Cuando se publique, la vas a ver acá.`) as the message, under the new title and badge.
- **V1 — Draft treatment.** A `surface` card with a muted `badge-draft` (`EN PREPARACIÓN`), a
  heading and a muted message, with no amber? *Recommended: yes.* Alternative: no card, only the
  badge above today's muted line. That is less distinct from the empty state.

No new dependency, no gate or verification-rule change, and no D-xx affected. Decision 1 applies
D-04 and the access model ("draft setlists are hidden from musicians") on the client as well. The
debug flag is harness-only, in the debug source set.
