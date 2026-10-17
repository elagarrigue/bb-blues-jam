# Spec: feature-subpackages

## Source feature
- id `feature-subpackages`, area `project-hygiene`, status `not_started`, `depends_on: []`.
- Title: Split the large feature packages into per-flow subpackages. Pure package refactor approved by the organizer.
- Concurrent work: feature `admin-set-key` is also `in_progress` (a 16 Oct UX refinement already committed). This refactor must not alter its code beyond `package` and `import` lines. If the working tree has uncommitted edits to the `setkey` files, stop and report.

## Goal
Replace the flat packages of `:feature:next-jam` (60 files), `:feature:assistant` (43) and `:feature:past-jams` (37) with per-flow subpackages. Group by flow or screen, not by layer. Main and test sources mirror each other.

## Non-goals
- No class, function, UiModel, copy string, Koin binding, visibility or dependency change. No new module, Gradle dependency or library.
- Visibility is not narrowed. `internal` is module-wide, so only imports change.
- `:feature:info` stays flat. `NextJamCopy` stays in the root package. Gradle `namespace` values and `di/` packages do not change. Saved-tab state and Konsist module paths are untouched.
- No Konsist, detekt or ktlint rule or allowlist edit. `package-under-module-root` already allows subpackages, and `AMBER_ROLE_ALLOWLIST` keys on module path.
- No attempt to remove cyclic package dependencies (see Cycles).

## Acceptance scenarios
1. Given the refactor is done, when `CI=true ./init.sh` runs, then it exits 0 with `konsist`, `detekt` and `ktlint` all `wired`, and the unit test count equals the baseline.
2. Given any `.kt` file in the three modules, then its `package` equals its directory under `src/<set>/kotlin`.
3. Given `git diff -M --stat` against the pre-refactor commit, then every moved file shows as a rename with only `package`/`import` line changes (plus the few KDoc/comment references listed below).
4. Given `:app`, then it compiles with updated imports only.

## Repository research (inspected)
- Listed all `.kt` files of the four feature modules (main and test). Every main file in the approved layout was matched to a real file. Layout names `AddSong{Copy,Defaults,Presenter,Screen,UiModel}` and `SetKey{...}`, and there is no `AddSongValidation` or `SetKeyValidation`. Only `assign/` has a `Validation` file (`AssignMusicianValidation`).
- Ran a heuristic cross-reference script over top-level names (scratchpad `cyc.py`; word-match based, so it overstates edges for generic names such as `status` or `targets`). No file is unmapped.
- Outside the three modules, references to the moved packages exist only in:
  - `app/src/main/java/com/bbbjam/BluesJamApp.kt`: imports `...assistant.di.assistantModule`, `...nextjam.di.nextJamModule`, `...pastjams.di.pastJamsModule`. These do not change, because `di/` stays.
  - `app/src/main/java/com/bbbjam/navigation/AppNavHost.kt`: imports `AddSongScreen`, `AssignMusicianScreen`, `SetKeyScreen`, `SongDetailScreen` (nextjam) and `PastJamDetailScreen` (pastjams). These must follow to `nextjam.addsong`, `.assign`, `.setkey`, `.songdetail` and `pastjams.detail`.
  - `app/src/main/java/com/bbbjam/navigation/TabsShell.kt`: imports `AssistantScreen`, `NextJamScreen` and `PastJamsScreen`, all staying in the root. They need no change. Confirm that `AppNavHost` and `TabsShell` do not use other moved classes, for example `Params` classes. The compiler will report any.
  - `core/ui/.../action/IconRowAction.kt:26`: a KDoc mention of `com.bbbjam.feature.nextjam.RowTextAction`. Verify whether that class exists. If it does, update the KDoc to its new package. If it does not, leave the stale comment and note it.
  - `konsist-test/.../ModuleIsolationTest.kt:462`: a comment showing the module-to-package mapping. It stays correct for the module root.
  - `feature/*/build.gradle.kts`: only `namespace`, unchanged.
  - `.idea/workspace.xml` is ignored.
- `private` top-level declarations are file-local and cannot be referenced from another file, so none needs an import. Top-level `internal` extension functions and properties (mappers such as `toUiModel`, `toRow` and `displayEntries`) are the real import risks, because an extension called across the new package boundary needs an explicit import. The compiler reports each one. Add the import. Do not widen or move any declaration.
- Main files that share a name across packages in different modules (`AdminFakes`, `FakeJamsRepository` in tests of next-jam and past-jams) are in different modules, so there is no clash.

## Cycles (named and accepted)
Package-level cycles exist and are pervasive. The heuristic found two-way edges between most pairs inside each module. Examples:
- next-jam: root<->admin (`NextJamListContent` uses `AdminControls`; `AdminControls` uses `NextJamUiModel` and `NextJamCopy`), root<->overlay, root<->addsong (`NextJamPresenter` uses `AddSongPresenter.toRow`), root<->refresh, admin<->overlay, admin<->songdetail.
- past-jams: root<->admin, admin<->edit, admin<->status, admin<->create, edit<->status.
- assistant: root<->apply, root<->chat, root<->jam, apply<->run (`ApplyPlan` uses `ApplyMapping`/`ApplyTargets`), chat<->apply, jam<->apply. This includes `run/`, which is described as pure logic, so its pure-logic status is about Compose, not about dependencies.

Decision: **accept them.** The packages are an organization aid within one module, and Kotlin has no package cycle restriction. Konsist has no rule on subpackage cycles (verify by running the gate). Breaking the cycles would need class moves or interface extraction, which is a behavior-free but larger change and is out of scope. Do not move a file to dodge a cycle. The implementer records the cycles it observes in the evidence and adds no cycle-detection rule.

## Target layout
Adjust a file's group only with a written reason in the evidence. Names below are file names (one top-level subject per file); `{...}` expands to each suffix.

### :feature:next-jam (`com.bbbjam.feature.nextjam`)
- root: NextJamPresenter, NextJamUiModel, NextJamScreen, NextJamListContent, NextJamCopy, JamDateText, DraftSetlistBlock, DraftSetlistDefaults, NextJamDraftPreview, NextJamRemovalPreview, NextJamStatesPreview, `di/` (unchanged)
- `refresh`: LiveRefresh, NextJamRefreshes, AdminRefresh
- `admin`: NextJamAdminState, NextJamAdminMapping, AdminControls, AdminControlsDefaults, AdminStatusBlock, RemovalHandler, MoveControls, MoveControlsState, MoveCopy, FailureMessages
- `overlay`: SongOverlays, KeyChangeOverlay, LineupChangeOverlay, AssignmentOverlay, SlotClearOverlay, MoveOverlay
- `songdetail`: SongDetailPresenter, SongDetailUiModel, SongDetailScreen, SongDetailCopy, SongDetailDefaults, SongAdminState, LineupControls, LineupEditorControl, LineupReservations, ExtraParticipantControls, ExtraParticipantFormState, ExtraParticipantUiMapping, ExtraDialogDefaults
- `addsong`: AddSong{Copy,Defaults,Presenter,Screen,UiModel}
- `setkey`: SetKey{Copy,Defaults,Presenter,Screen,UiModel}
- `assign`: AssignMusician{Copy,Defaults,Presenter,Screen,UiModel,Validation}

### :feature:past-jams (`com.bbbjam.feature.pastjams`)
- root: PastJams{Presenter,UiModel,Screen,Copy,Defaults}, PastJamsStatesPreview, `di/`
- `detail`: PastJamDetail{Presenter,UiModel,Screen,Defaults,Preview}
- `admin`: JamsAdmin{State,Mapping,UiModel,Views,Defaults,Writes}, JamsFailureMessages
- `status`: JamStatus{Controls,Defaults,Mapping,UiModel,Views}, PublishControls, PublishMapping, PublishDetails
- `edit`: JamEdit{Controls,Copy,Form,Mapping,UiModel,Views}
- `create`: NewJam{Controls,Mapping,UiModel,Views}

### :feature:assistant (`com.bbbjam.feature.assistant`)
- root: AssistantPresenter, AssistantUiModel, AssistantScreen, AssistantPreview, AssistantCopy, AssistantDefaults, AssistantHandlers, `di/`
- `chat`: AssistantChat, AssistantMapping, AssistantTurn, AssistantSongs
- `run`: ProposalApplier, ApplySteps, ApplyRun, RunStore, RunLock, ApplyPlan, ReplacePlan, JamCreationRunner
- `apply`: ApplyHandlers, ApplyMapping, ApplyTargets, ApplyUiModel, ApplyViews, ApplyDialogs, ApplyConfirmationDialog, ApplyCopy, ApplyOutcomeCopy, ApplyDefaults, AddSummary, ReplaceMapping, ReplaceCopy, ReplaceOutcomeCopy
- `create`: CreateCopy, CreateFormMapping, CreateFormState, CreateSummary
- `jam`: JamCopy, JamDefaults, JamHandlers, JamMapping, JamUiModel, JamViews

### :feature:info: unchanged.

## Test mapping (src/test mirrors main; a test goes with its primary subject; shared fakes and fixtures with most users, else root)
**next-jam**: root: NextJamPresenterTest, NextJamCreatedTest, NextJamDraftTest, NextJamFilterTest, NextJamStatesTest, NextJamModuleTest (imports `nextJamModule` from `di`), DraftSetlistDefaultsTest, JamDateTextTest, AdminFakes, FakeJamsRepository, FilterBarExpectations. `refresh`: LiveRefreshLoopTest, LiveRefreshWindowTest, NextJamLiveRefreshTest. `admin`: NextJamAdminTest, NextJamMoveTest, NextJamRemoveTest, AdminControlsDefaultsTest. `overlay`: AssignmentOverlayTest, MoveOverlayTest, NextJamKeyChangeTest, NextJamLineupChangeTest. `songdetail`: SongDetail{Admin,Defaults,Mapping,ModelShape,Presenter}Test, SongDetailFixtures, ExtraDialogDefaultsTest. `addsong`: AddSong{Defaults,Presenter}Test. `setkey`: SetKey{Defaults,Presenter}Test. `assign`: AssignMusician{Defaults,Presenter,Suggestions}Test.
The shared fakes (`AdminFakes`, `FakeJamsRepository`, `FilterBarExpectations`) stay at root unless a grep shows a single subpackage owns all users.

**past-jams**: root: PastJams{Defaults,Module,Presenter,PullRefresh,States}Test, AdminFakes, FakeJamsRepository. `detail`: PastJamDetail{Defaults,Mapping,ModelShape,Presenter}Test, PastJamDetailFixtures. `admin`: JamsAdminTest, JamsAdminDefaultsTest. `status`: JamStatusDefaultsTest. `edit`: JamEditAdminTest, JamEditFormTest. `create`: NewJamCreateTest.

**assistant**: root: AssistantDefaultsTest, AssistantPresenterTest, AssistantModuleTest, StreamedAnswerTest, AssistantFixtures, ApplyFixtures, FakeSetlistAssistant (shared by several flows). `chat`: AssistantChatTest, AssistantMappingTest. `run`: ApplyPlanTest, ReplacePlanTest, ProposalApplierTest, ProposalApplierReplaceTest, JamCreationRunnerTest. `apply`: ApplyPresenterTest, ReplacePresenterTest. `create`: CreatePresenterTest. `jam`: JamDefaultsTest, JamPresenterTest.

**info**: unchanged. Any test not listed (none expected) follows its primary subject. Record any deviation.

## Work order (one module at a time; compile and test checkpoint each)
Use `git mv` for every file (main and test) so history follows. Edit only the `package` line and the needed imports after the move. Do not rewrite a moved file with Write. A move plus edit in one commit is fine, because commits are the orchestrator's job. Do not commit.
0. Baseline: before any move, run `CI=true ./init.sh` and record the exit code, the tool lines and the total test count. Count by summing `tests=` over `*/build/test-results/test/*.xml` (or the `init.sh` summary, if it prints one) across all modules. The last evidence says 1133; confirm and state the number actually measured. Also record the Konsist rule count (number of `@Test` functions in `:konsist-test`) and note the git HEAD.
1. `:feature:past-jams` (smallest). Move, fix imports, then `./gradlew :feature:past-jams:check :app:compileDebugKotlin`. `:app` only needs `PastJamDetailScreen` to follow.
2. `:feature:assistant`. Same, with `:feature:assistant:check`.
3. `:feature:next-jam`. Same, plus update `AppNavHost.kt` imports. Then `./gradlew :feature:next-jam:check :app:assembleDebug`.
4. Update docs (below), then run the full `CI=true ./init.sh`.
Fix import errors with the compiler. Wildcard imports are not allowed (ktlint). Keep imports sorted lexicographically (ktlint ordering) because the new imports shift positions. Run `./gradlew ktlintFormat` only if the repo defines it, and review the diff afterward. Explicit same-module imports replace the former implicit same-package access.

## Durable documentation impact
- `.claude/skills/architecture/SKILL.md`: update the places naming flat feature packages (about lines 307, 795, 1036: the `com.bbbjam.feature.nextjam` file lists and the package-under-module-root description). Describe the subpackage convention (group by flow or screen, `di/` and the primary presenter/screen/uimodel/copy at module root, `:feature:info` flat) and the accepted intra-module cycles. Check `.claude/skills/architecture/references/presenter-pattern.md` (line 22, directory path and any file paths) and other references the same way. Add a short rule: new files go in the subpackage of their flow.
- `PROGRESS.md`: add a session entry. Rewrite only package names where it describes the current layout. Do not touch history entries otherwise.
- `docs/specs/*.md` (old specs) and `bb-blues-jam-bitacora.md` are history and keep the old package names. Do not edit them.
- `AGENTS.md`, `DESIGN.md`, `docs/*`: grep for moved file names or package paths; update only if they describe the current layout. Expect "not needed".
- `feature_list.json`: set status and evidence for this feature only, as the implementer normally does.

## Verification
1. `CI=true ./init.sh` exits 0; `konsist`, `detekt` and `ktlint` each print `wired`.
2. Test count equals the baseline measured in step 0 (1133 expected). A drop means a test was lost or its package-less discovery broke.
3. Konsist rule count unchanged, and `git diff` shows no change under `konsist-test/`, detekt or ktlint config.
4. `git diff -M --stat` (staged or against the base commit) shows renames. State the number of renamed and the number of non-renamed (pure additions or deletions) files. Expect none non-renamed except the doc changes and `AppNavHost.kt`.
5. Package/path check: a script such as `grep -L` per file that confirms `package` equals the directory. Include the command and zero-mismatch output.
6. `git diff -M -U0` filtered to changed lines shows only `package`/`import` lines in `.kt` files (plus the `IconRowAction` KDoc if applicable). Paste the filtering command and output.
7. `app/src/main/**/AppNavHost.kt` is the only `:app` file whose imports change, unless the compiler requires more.
8. No device is required. Optionally, the debug demo jam on an emulator confirms the three tabs open, but this is not required for acceptance.

## Evidence to capture
Baseline and final test counts, tool lines, exit codes, `git diff -M --stat` summary, the package-path check, the list of cycles observed, any file whose group differs from this spec with the reason, and the `RowTextAction` finding.

## Validator checklist
- Layout matches this spec or deviations are justified. `:feature:info` and `NextJamCopy` untouched in place.
- Every file is a rename (history preserved). No class rename or body change (`git diff -M` content check).
- `admin-set-key` code differs only in package/import lines.
- Test count, Konsist rule count and wired tools match the baseline. No rule or allowlist edit.
- Docs updated without rewriting history sections.

## Risks
Large mechanical diff (about 140 renames) can hide an accidental edit, so the step 6 filter is the guard. Import ordering failures in ktlint are the most likely friction. A cross-file extension needing an import is caught by the compiler. Merge conflicts with `admin-set-key` if it has uncommitted edits.

## Open question
None blocking. Assumption: the baseline of 1133 is carried from earlier evidence and was not re-measured by the planner.
