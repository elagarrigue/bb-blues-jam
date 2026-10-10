# Feature Implementation Spec: Whole-project cleanliness review with no behavior change

## Source Feature

- `id`: project-hygiene-pass
- `area`: repo
- `depends_on`: `action-contract-registry` (accepted 9 October 2026)
- `status`: `not_started` at planning time
- `source`: `feature_list.json`

## Goal

The user asked for a whole-project review ("revisá todo el proyecto, que sea clean"). This slice
fixes the concrete hygiene findings below (dead code, avoidable lint warnings, duplicated helpers,
one oversized file, stale orientation docs, small build leftovers) **with no behavior change**. Every
finding has a fix or a recorded reason to keep it. Large or risky items are listed under Deferred.

## Non-Goals

- No user-visible change. Copy strings, colours, layout, navigation, write paths and the Apps Script
  contract stay byte-identical in effect. `ui-polish-pass` (next slice) owns visual improvements.
- No change to `init.sh`, `config/detekt/detekt.yml`, `.editorconfig`, Konsist rules, lint
  configuration or any verification rule. No baseline file, no `@Suppress`, no `ignoreFailures`.
- No dependency version change (see Deferred D1). No new Gradle module.
- No change to `backend/apps-script/src/*.js` (see Deferred D2). No change to the bitácora.
- No touching of D-01 … D-20.

## Job Story

When the next agents (and the course reviewers) open this repo, I want the code, build and
orientation docs to say only what is true now, so they don't waste time on dead code, stale
status lines or warnings that could have been fixed.

## Acceptance Scenarios

### Scenario 1: behavior unchanged
Given the baseline gate is green (the orchestrator is running `CI=true ./init.sh` now)
When every task below is applied
Then `CI=true ./init.sh` exits 0 with `konsist`, `detekt`, `ktlint` all `wired`, the total JVM test
count equals the baseline plus only the tests added by T5, and no existing assertion's expected
value changed (tests may only change a reference to a moved or renamed symbol).

### Scenario 2: lint warnings
Given the lint reports listed under Findings
When the gate reruns
Then `:feature:next-jam` and `:feature:past-jams` lint report "No issues found" (4 → 0 and 1 → 0),
and `:app` has only `GradleDependency`/`NewerVersionAvailable` warnings (no new issue id).

### Scenario 3: docs trimmed without losing evidence
Given `PROGRESS.md` "Current Verified State" (lines 15–380 at HEAD `582027f`)
When it is replaced by a short current snapshot
Then every non-blank line of the old section still exists verbatim elsewhere in `PROGRESS.md`
(check in Verification step V3).

## Repository Research

### Inspected

`AGENTS.md`, `PROGRESS.md` (structure, lines 1–380 and the tail), `feature_list.json` (all
statuses: 40 accepted, `songsterr-browser-link` blocked by D-20, this slice and `ui-polish-pass`
not started), `START-HERE.md` (whole), `.claude/skills/architecture/SKILL.md` (whole),
`gradle/libs.versions.toml`, root/`settings`/every module `build.gradle.kts`, `init.sh`,
`config/detekt/detekt.yml`, `.editorconfig`, `.gitignore`, `backend/apps-script/` (file sizes,
`.gitignore`, README deploy section, `test/helpers/load.js`, a per-function reference scan of
`src/*.js`: no dead function), the lint text reports `*/build/reports/lint-results-debug.txt`
(dated 8–9 October 2026; I did not run Gradle), every Kotlin main file's line count, and a
script scan of every main-source declaration referenced only once in the repo. Files read in full:
`NextJamPresenter.kt`, `AdminRefresh.kt`, `NextJamListContent.kt` (head), `NextJamScreen.kt`
(signatures, `setlistItems`, `previewRow`), `PastJamsScreen.kt` (signature), the Copy objects of
the three features, `SetlistRemovals.kt`, `ServiceCodes.kt`, the `FakePost` classes of `:core:data`
tests, `NoticeVisibilityTest.kt`, `ThemeShowcase.kt`, `Converters.kt`, `app/src/androidTest`.

**Not inspected:** compiler warnings (needs a Gradle run), the detekt/ktlint HTML reports (both
tools fail the build on any finding, so a green gate means zero), `DESIGN.md` (no UI change),
`docs/design/**` contents, the bitácora.

### Findings (each with its disposition)

| # | Finding (file:line) | Disposition |
|---|---|---|
| F1 | Dead composable `AdminDraftBanner` — `feature/next-jam/.../AdminControls.kt:43-59`; replaced by `AdminStatusBlock` in `admin-publish-setlist` (spec line 189), no caller. | **Delete** (T2). `AdminControlsDefaults.draft()` stays: used at `AdminControls.kt:268` and in tests. |
| F2 | Unused copy `AssignMusicianCopy.UNKNOWN_SONG, SLOT_TAKEN, SLOT_GONE, SONG_GONE, REFUSED, OFFLINE` — `AssignMusicianCopy.kt:16,19-23` (failures use `NextJamCopy`/`FailureMessages.kt`). | **Delete** (T2). |
| F3 | Unused `NextJamCopy.CLEARING` — `NextJamCopy.kt:119`. | **Delete** (T2). |
| F4 | Lint `ComposableNaming`: `rememberAdminRefresh` returns Unit — `AdminRefresh.kt:13`. | **Rename** to `AdminRefreshEffect` (+ caller `NextJamAdminState.kt:48`) (T3). |
| F5 | Lint `ModifierParameter`: param named `fill` — `NextJamListContent.kt:21,36`. | **Rename** to `modifier` (callers `NextJamScreen.kt:147,149`) (T3). |
| F6 | Lint `ModifierParameter`: `modifier` after `onMoveAction` in private `SongRow` — `NextJamScreen.kt:219-223`. | **Reorder** (modifier first optional); call at `:163` uses named args (T3). |
| F7 | Lint `ModifierParameter`: `PastJamsScreen(onOpenJam, modifier, …)` — `PastJamsScreen.kt:47-52`. | **Reorder** to `modifier` first, like `NextJamScreen`; the only caller (`TabsShell.kt:79`) is named (T3). |
| F8 | `:app` lint: 15 warnings, all version checks (`GradleDependency` 7, `NewerVersionAvailable` 8) on `libs.versions.toml` 6–33. Earlier records say 14; the 15th is a new upstream release, not code. `NewerVersionAvailable` queries Maven at lint time, so the count can rise with no repo change. | **Keep** in this slice: every pin is deliberate (stdlib 2.2.10 trap, documented in the catalog and the architecture skill). Upgrade = Deferred D1; suppressing = human decision H1. |
| F9 | `NextJamPresenter.kt` is 626 lines; lines 295–598 are the admin mapping (`AdminState`, `RemovalState`, `removalKey`, `RowAdmin`, `AdminState.toUiModel`, `failures`, `decorate`, `removalModel`, `removalDetails`). The file holds 11 top-level functions (detekt `TooManyFunctions` default threshold 11). | **Split** (T4): move the admin mapping, unchanged, to a new `NextJamAdminMapping.kt`; `private` → `internal` only where a cross-file call needs it. Also merge the two identical `row.copy(...)` branches of `decorate` (`:546-560`) into one with `key`/`keyDescription` chosen by `pending`. Target: presenter file ≤ ~330 lines. |
| F10 | Same row text duplicated across three features: `"Tonalidad $key"` in `NextJamCopy.kt:159`, `PastJamsCopy.kt:39`, `SongDetailCopy.kt:20`; the 2-digit position label in `NextJamPresenter.kt:503,608`, `NextJamScreen.kt:334` (literal `2`), `PastJamDetailPresenter.kt:82,90`; `droppedRows(count)` identical in `NextJamCopy.kt:155` and `PastJamsCopy.kt:42`. | **Move to `:core:ui`** (T5) as public `object SongListText` in `com.bbbjam.core.ui.text` (beside `SpanishDateNames`, the precedent for text two features share): `positionLabel(position: Int)`, `keyDescription(key: String)`, `droppedRows(count: Int)`, same outputs. `SongDetailCopy.KEY_LABEL` stays (visible label). |
| F11 | Setlist request field names spelled two ways in `:core:data`: companion `DATE`/`SONG_ID`/`KEY` in `SetlistKeyChanges.kt:82-84`, `SetlistMoves.kt:88-89`, `SetlistPublishes.kt:94`, `SetlistRemovals.kt:83-84`, `SetlistSongAdds.kt:92-94`; raw `"date"`/`"songId"` in `SetlistAssignments.kt:85-86`, `SetlistExtraParticipants.kt:174-175`, `SetlistLineupChanges.kt:59-60`, `SetlistSlotClears.kt:116-117`. Error codes duplicated: `song_not_in_setlist` (`SetlistRemovals.kt:82`, `SetlistExtraParticipants.kt:213`), `slot_not_in_lineup` (`SetlistAssignments.kt:130`, `SetlistSlotClears.kt:150`). | **Consolidate** (T6): new internal `object SetlistRequestFields { DATE, SONG_ID, KEY }` in `setlist/`; the shared codes join `remote/ServiceCodes` (`SONG_NOT_IN_SETLIST`, `SLOT_NOT_IN_LINEUP`). Codes used by one file stay local. `DefaultJamsRepository.JAMS` (payload key) ≠ `Resources.JAMS` (route): **keep**, different meaning. |
| F12 | Five identical queued `FakePost` test doubles in `core/data/src/test/.../setlist/`: `DefaultSetlistRepositoryTest`, `SetlistExtraParticipantRepositoryTest`, `SetlistLineupChangesTest`, `SetlistMoveRepositoryTest`, `SetlistPublishRepositoryTest`. | **Extract** (T7) to one internal `QueuedPostTransport` in `core/data/src/test/kotlin/com/bbbjam/core/data/setlist/`. The two simpler `FakePost`s in `admin/` and `DefaultJamsRepositoryTest` differ (single answer, `actions()`): **keep**. |
| F13 | Mojibake: `core/ui/src/test/.../state/NoticeVisibilityTest.kt:92` (`"Actualizandoâ€¦"`), `.claude/skills/architecture/SKILL.md:751` (`PrÃ³xima`), `docs/specs/admin-assign-musician.md:28`, `docs/specs/admin-edit-extra-participants.md:23`. | **Fix** to `…`/`ó` (T1, T7). The test compares two notices by inequality only, so the fix keeps its intent. |
| F14 | Stale KDoc: `NextJamScreen.kt:82-83` says rows are "keyed by position"; since `admin-reorder-songs` they are keyed by `rowKey` (song id, position fallback for duplicates, `NextJamPresenter.kt:481`, `NextJamScreen.kt:162`). Same stale sentence in the architecture skill ("The lazy list keeps position keys", Where Each Piece Goes, per-row state bullet). | **Fix** wording (T3, T1). |
| F15 | Template instrumented test `app/src/androidTest/java/com/bbbjam/ExampleInstrumentedTest.kt`, never run by the gate (no device; UI-test harness declined, T1 of `song-row-expansion`), plus its only users: `testInstrumentationRunner` (`app/build.gradle.kts:52`), `androidTestImplementation` espresso/androidx-junit (`:95-96`), catalog `androidx-junit`, `androidx-espresso-core`, versions `junitVersion`, `espressoCore`. | **Delete** (T8). Reversible; reported to the orchestrator. |
| F16 | `:app` declares `ui-tooling-preview` and `debugImplementation ui-tooling` (`app/build.gradle.kts:92-93`) but has no `@Preview` and no tooling import. Feature modules declare their own. | **Remove** both lines (T8). |
| F17 | `START-HERE.md` stale: header "Actualizado el 29 de septiembre … Estado: semana 2 cerrada. Seis rebanadas `accepted`" (`:10-12`), §3 "Pendiente inmediato" (`:78-84`), §6.6 "SIGUIENTE" (`:220`), §10 week 2 "Falta la primera `feature-spec`" (`:291`). | **Update** (T1), keeping Spanish (it is the course orientation doc, existing language; D-12 is not touched) and keeping §6.1–6.5 as the record they say they are. |
| F18 | `PROGRESS.md` (3,734 lines) "Current Verified State" (`:15-380`) is mostly a historical "Before that…" chain; its "What exists" (`:166-372`) says "Six product modules", `passing`/`in_progress` states and "Not deployed yet"; "Reachable without unblocking the Sheet schema" (`:374-380`) is obsolete. | **Trim** (T1): a ≤ 50-line current snapshot replaces it; the old text moves **verbatim** to a new final section `## Archived Verified State (moved 10 October 2026)`. Session Log untouched. |
| F19 | Architecture skill: the five "As built locally by …" paragraphs (`SKILL.md:716-765`) sit after "Still Open", outside Module Layout; their qualifiers "(live deployment pending)/(deployment/live checks deferred)" are stale for reorder, publish, adjust-lineup, assign and clear-slot (shared live batch L1–L5 passed 9 October 2026, `PROGRESS.md:22-23`); `admin-edit-extra-participants` (not deployed, B2) and `action-contract-registry` have no paragraph; "Deeplink scheme … deferred to `action-contract-registry`" (`:711`) predates that slice's acceptance. | **Tidy** (T1): move the paragraphs into Module Layout after the `live-refresh-during-jam` one, correct each qualifier from `PROGRESS.md`, add one short paragraph each for the two missing slices from their specs, and say the deeplink scheme stays deferred (D1) after `action-contract-registry`. No rule text changes. |
| F20 | `docs/ejemplo jam.jpeg` (146 KB, filename with a space). | **Keep**: it is the source the seed CSVs were built from (`PROGRESS.md:495`); moving it into `docs/sheet-seed/` would add a file to a directory the Node and `:core:data` tests read and declare as test input. |
| F21 | Root `build/` (only `reports/configuration-cache`, `reports/problems`). | **Keep**: written by Gradle on every run, ignored by `/build`; nothing tracked. |
| F22 | Empty untracked dir `docs/evidence/offline-notice-visible/`. | **Delete locally** (no diff). |
| F23 | `ThemeShowcase` (`core/ui/.../theme/ThemeShowcase.kt`) has no screen caller. | **Keep**: documented device token-check tool (`design-tokens-theme` spec), public by design. |
| F24 | Converters `tagsToJson`/`jsonToTags`, `Key.isMinor`, `SongDetailDefaults.otherStyles`, `SetlistActionRegistry.registeredMutations/dispatch` look unused to a text scan. | **Keep**: Room `@TypeConverter`s; domain API; test hook; the phase 2 assistant surface (D-13). |
| F25 | `app/src/main/keepRules/rules.keep` is the AGP template (comments only); release has `optimization.enable = false`. | **Keep**: AGP's expected location for when R8 is enabled; harmless. |
| F26 | `settings.gradle.kts` foojay plugin while no module sets a toolchain. | **Keep**: `gradle/gradle-daemon-jvm.properties` carries foojay toolchain URLs for the daemon JVM. |
| F27 | `backend/apps-script`: `src/Post.js` 1,389 lines; ignored `*.local.json` saved responses in the folder; no dead function found. | **Keep** in this slice (D2). The `.local.json` files are local and ignored; leave them (user's data). |

## Technical Approach

Pure refactors and deletions, task by task, each verified before the next. No public API used by
another module changes except: `PastJamsScreen` parameter order (named caller only), the removal of
feature-local `keyDescription`/`droppedRows` in favour of `SongListText`, and new internal
symbols. Konsist: `SongListText` is in `com.bbbjam.core.ui.text` (package rule satisfied), reads
no colour and no dp. Detekt: the split reduces `TooManyFunctions` pressure; do not add suppressions.
If a step trips detekt or ktlint, fix the code (run `./gradlew ktlintFormat`), never the config.

## Expected File Changes

- `feature/next-jam/src/main/kotlin/com/bbbjam/feature/nextjam/`: `AdminControls.kt`,
  `AssignMusicianCopy.kt`, `NextJamCopy.kt`, `AdminRefresh.kt`, `NextJamAdminState.kt`,
  `NextJamListContent.kt`, `NextJamScreen.kt`, `NextJamPresenter.kt` (modify);
  `NextJamAdminMapping.kt` (create).
- `feature/past-jams/src/main/kotlin/com/bbbjam/feature/pastjams/`: `PastJamsScreen.kt`,
  `PastJamsCopy.kt`, `PastJamDetailPresenter.kt` (modify).
- `feature/song-detail/src/main/kotlin/com/bbbjam/feature/songdetail/SongDetailCopy.kt` and its
  caller(s) (modify).
- `core/ui/src/main/kotlin/com/bbbjam/core/ui/text/SongListText.kt` (create);
  `core/ui/src/test/kotlin/com/bbbjam/core/ui/text/SongListTextTest.kt` (create);
  `core/ui/src/test/.../state/NoticeVisibilityTest.kt` (modify, F13).
- `core/data/src/main/kotlin/com/bbbjam/core/data/setlist/SetlistRequestFields.kt` (create);
  `remote/ServiceCodes.kt` and the nine `setlist/Setlist*.kt` files of F11 (modify);
  `core/data/src/test/.../setlist/QueuedPostTransport.kt` (create) and the five tests of F12
  (modify: delete the nested class, use the shared one).
- Feature tests that reference a moved or renamed symbol (reference only).
- `app/build.gradle.kts`, `gradle/libs.versions.toml` (modify); `app/src/androidTest/` (delete).
- `START-HERE.md`, `PROGRESS.md`, `.claude/skills/architecture/SKILL.md`,
  `docs/specs/admin-assign-musician.md`, `docs/specs/admin-edit-extra-participants.md` (modify);
  `feature_list.json` (status/evidence).

## Visual Design Impact

- UI involved: code only; nothing drawn changes. `DESIGN.md` not needed. No design artifact.

## Durable Documentation Impact

- `ARCHITECTURE.md` / `CONSTRAINTS.md`: not present in this repo; the architecture skill is the
  durable architecture doc — update per F14/F19, and mention `SongListText` in the `:core:ui`
  text bullet (beside `SpanishDateNames`).
- `AGENTS.md`: not needed (workflow and commands unchanged).
- `docs/risks-and-open-questions.md`: not needed (no risk changes).

## Implementation Tasks (in order; verify each before the next)

- [ ] **T0 Baseline.** After the orchestrator's baseline gate finishes, record: total `tests=` and
  `failures=`/`errors=` over `*/build/test-results/**/TEST-*.xml` and `*/*/build/…` (exclude
  `build-logic`), and the issue ids of every `lint-results-debug.txt`.
- [ ] **T1 Docs (F13 docs part, F14 skill part, F17, F18, F19).** START-HERE: header date
  10 October 2026 and one line "Estado: 40 rebanadas `accepted`; el estado al día vive en
  `PROGRESS.md`"; §3 "Pendiente inmediato" → pointer to `PROGRESS.md` plus the still-true
  repertoire item; §6.6 → "HECHO"; §10 week 2/3 rows reflect that the flow ran. PROGRESS: new
  snapshot (root, gate command, slice counts from `feature_list.json`, latest gate result, the 8
  Gradle modules + `backend/apps-script` + `:konsist-test` with a pointer to the architecture
  skill, deployment state: shared router live for 15 actions, `addExtraParticipant`/
  `removeExtraParticipant` pending under B2, human checks still owed copied from the current
  text, the `:app` lint note of F8); the old lines 16–380 appended verbatim under
  `## Archived Verified State (moved 10 October 2026)`. Skill tidy per F19/F14. Spec mojibake.
- [ ] **T2 Dead code (F1–F3).** Delete; `./gradlew :feature:next-jam:check`.
- [ ] **T3 Lint and stale KDoc (F4–F7, F14 code part).** `./gradlew :feature:next-jam:lintDebug
  :feature:past-jams:lintDebug` → "No issues found" in both reports; then both modules' `check`.
- [ ] **T4 Split (F9).** Move only; merge the `decorate` branches. `:feature:next-jam:check`;
  no test file changes in this task.
- [ ] **T5 Shared row text (F10).** Create `SongListText` + `SongListTextTest` (outputs
  `"01"`, `"13"`, `"Tonalidad Bm"`, `"Falta 1 tema: no se pudo leer."`, `"Faltan 3 temas: no se
  pudieron leer."`); switch the callers in the three features; delete the feature copies.
  `./gradlew :core:ui:check :feature:next-jam:check :feature:past-jams:check
  :feature:song-detail:check`.
- [ ] **T6 Request fields and codes (F11).** `./gradlew :core:data:check`; request-body
  assertions in tests must pass untouched.
- [ ] **T7 Test doubles and test mojibake (F12, F13 test part).** `:core:data:check`,
  `:core:ui:check`; per-class test counts equal T0.
- [ ] **T8 Build leftovers (F15, F16).** Then `./gradlew :app:assembleDebug :app:check`.
- [ ] **T9 Final.** `CI=true ./init.sh`; compare with T0; record evidence; `feature_list.json`
  → `passing`; `git diff --check`. Delete F22 locally.

## Verification Plan

- V1 `CI=true ./init.sh` exits 0; `konsist: wired`, `detekt: wired`, `ktlint: wired` (Konsist
  still 17 tests). This is the gate; nothing in it changes.
- V2 Test totals: T9 total = T0 total + the `SongListTextTest` count; zero failures/errors;
  per-class counts unchanged for every pre-existing class.
- V3 Evidence kept: `python - <<'EOF'` script that reads `git show 582027f:PROGRESS.md` lines
  16–380 and asserts each non-blank line occurs in the new `PROGRESS.md` (multiset); print
  "missing: 0".
- V4 Lint: next-jam and past-jams "No issues found"; `:app` issue ids ⊆ {GradleDependency,
  NewerVersionAvailable}.
- V5 Behavior: `git diff 582027f -- '*.kt'` shows no changed string literal in `src/main`
  except moves (reviewer reads it); no file under `backend/apps-script/src` changed;
  `git diff --stat` matches Expected File Changes.
- V6 Node tests are not required (backend untouched); running
  `node --test backend/apps-script/test/*.test.js` is optional confirmation.
- No device check needed: nothing drawn changes and no UI test harness exists (T1 declined).

## Evidence To Capture

T0 and T9 test totals and lint ids, V3 output, the `init.sh` tool lines, line count of
`NextJamPresenter.kt` after T4, and the list of findings closed vs kept (this table's ids).

## Deferred (not in this slice)

- **D1 Dependency upgrades** (the 15 `:app` warnings): Kotlin 2.2.10 → 2.4, Koin 4.2, BOM 2026,
  Navigation 2.10, OkHttp 5.5, serialization 1.11 move together (stdlib trap documented in the
  catalog and the skill). Own slice, with a resolved-classpath diff.
- **D2 Split `backend/apps-script/src/Post.js`** (1,389 lines) into router + setlist files: the
  deploy is a per-file copy-paste with a "verify the paste" step (README), `load.js` lists files,
  and a shared deployment is pending (B2). Do it with that deployment (human decision H2).
- **D3 Shared test fakes across modules** (`FakeJamsRepository` ×3, `FakeAdminSession` ×3 in
  next-jam/past-jams/song-detail/info/app tests): needs test fixtures or a `:core:testing`
  module, a new build/Konsist surface.
- **D4 Generic mutation-entry helper** for the eight `setlist/Setlist*` managers (entries flow,
  mark failed, dismiss repeat): concurrency-sensitive, heavily tested; own refactor slice.
- **D5 Quality tools as a `bluesjam.quality` convention** (already noted in the skill): touches
  how the gate is wired; excluded by the no-verification-change rule.
- **D6 Launcher icon** is the AGP template (`app/src/main/res/mipmap-*`, `drawable/ic_launcher_*`):
  a design asset; `ui-polish-pass` or the user.
- **D7 Large test files** (`DefaultSetlistRepositoryTest` 775, `NextJamPresenterTest` 699 lines):
  keep; splitting churns history with no gain.

## Validator Checklist

- [ ] Every finding F1–F27 is either fixed as stated or still has its keep reason; nothing else
      in scope crept in (compare `git diff --stat` with Expected File Changes).
- [ ] V1–V5 rerun independently, results match the evidence.
- [ ] No D-01 … D-20 rule touched; no ViewModel, no feature→feature dependency, no copy change,
      no suppression or config change; `init.sh` untouched.
- [ ] `SongListText` lives in `:core:ui` `text/`, features no longer define the three helpers.
- [ ] `NextJamAdminMapping.kt` holds the admin mapping; visibility widened only to `internal`.
- [ ] `PROGRESS.md` snapshot is current and short; archived section verbatim (V3).
