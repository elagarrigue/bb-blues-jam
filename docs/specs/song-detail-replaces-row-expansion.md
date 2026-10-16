# Feature Implementation Spec: Remove the expanded row and move its logic into the song detail

## Source Feature

- `id`: song-detail-replaces-row-expansion
- `area`: next-jam
- `depends_on`: ui-polish-pass (accepted, HEAD 9cb75ac)
- `status`: not_started at planning time
- `source`: `feature_list.json` (last entry), user request of 11 October 2026

## Goal

Próxima jam rows no longer expand. A row tap opens the song detail, which now holds everything the
expanded panel had: artist, key, the lineup panel with `OTROS` and, for the admin, `Cambiar
tonalidad`, assign (`Anotar`), clear (`Liberar`), `Cambiar formación` and add/remove extras, with
their pending (`Guardando…`) and failure states. The list keeps the collapsed row with the
instrument strip (D-07). Admin list rows keep `Subir`/`Bajar` and `Quitar de la lista` (with its
inline confirmation) as a compact bar under the row. `:feature:song-detail` is merged into
`:feature:next-jam` and the module is deleted.

User decisions already made (do not reopen): merge the module into `:feature:next-jam` (D-15 stays
as written, D-03 still holds); list-level actions stay on the list; everything song-level moves to
the detail; a row tap opens the detail directly; D-07 stays.

## Non-Goals

- No new mutation, repository function, Apps Script action or Room change. Every write used here
  already exists in `SetlistRepository` and the `:app` action registry (D-13); none is added.
- No change to the pickers (`SetKeyScreen`, `AssignMusicianScreen`, `AddSongScreen`) beyond the
  caller that opens them. No change to publishing, adding songs, the filter, pull/live refresh.
- No pull-to-refresh, staleness notice or live polling on the detail.
- No change to `init.sh`, the gate, detekt/ktlint config or any Konsist rule logic.
- Past jams: untouched (their detail has no lineup and never opens the song detail).

## Job Story

When I see the setlist at the jam, I want one tap on a song to show me everything about it (and,
as admin, let me edit it there), so the list stays a fast overview and the song's controls live in
one place.

## Users And Permissions

- Musician: row tap opens a read-only detail (the lineup panel with its hint; no control). A draft
  jam's song is never shown (`setlistForMusicians()`), unchanged.
- Admin (local flag, D-15): the same detail plus song-level controls; list rows add the compact
  move/removal bar. The flag only decides what is drawn; Apps Script authorizes every write.

## Acceptance Scenarios

1. **Row opens the detail.** Given Próxima jam with songs, when any user taps a row's header, then
   the detail of that song opens (no expansion, no chevron, no in-place panel exists anywhere).
2. **Back keeps the list.** Given a filter is selected and the list scrolled, when the user opens a
   detail and goes back (arrow or system back), then the same tab, filter and scroll are shown.
3. **Musician detail.** Given a musician, the detail shows back, title, artist (if any), the key
   block, and the lineup panel (`CUPOS LIBRES`, `CUPOS CUBIERTOS`, `OTROS`, the musician hint or
   "No quedan cupos libres."), with no control.
4. **Admin detail.** Given the admin, the key itself is tappable and announced with the action
   `Cambiar tonalidad`; there is no separate button. The detail also shows `Anotar` on
   open lines, `Liberar` on filled lines, `Quitar` on each extra line, `Cambiar formación` (inline
   editor). At the end of `CUPOS LIBRES`, an amber `+ Otro` / `Agregar` row uses the same type style
   for both texts and opens the existing extra
   participant dialog; this action is not a slot and does not affect slot counts or filters. The panel
   has no musician hint.
5. **Detail → picker → detail.** Given the admin on a detail, when they tap the key (or `Anotar`),
   the picker opens above the detail; a pick or `Volver` returns to the detail, whose key
   (or slot) shows the pending value with `Guardando…`.
6. **List and detail agree.** Given a pending key/lineup/assign/clear/extras change of a song, the
   list row (key, strip, `Guardando…`) and the detail (key, panel, `Guardando…`) show the same
   overlaid values; a failure reverts both, is a card on the list (as today) and on that song's
   detail, and `Cerrar` in either removes it from both.
7. **Compact admin bar.** Given the admin list, each row shows `Subir`, `Bajar` and `Quitar de la
   lista` under the header without expanding; `Quitar de la lista` swaps the bar for the inline
   confirmation; `Quitando…` replaces it while sending. Tapping the bar never opens the detail.
8. **Song disappears.** Given a detail is open, when a refresh drops the song (or a confirmed
   removal), then the detail turns live into "Este tema ya no está en la lista" with back.
9. **Identity survives renumbering.** Given the admin moved or removed another song, when a refresh
   renumbers positions while a detail is open, the detail keeps showing (and editing) the same song.
10. **Admin never flashes not-found.** Given the admin opens a draft's song, no frame shows the
    not-found block (the detail stays `Loading` until both the flag and the jams are read).

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md`, `feature_list.json`, `.claude/skills/architecture/SKILL.md`,
  `.claude/skills/feature-spec/references/spec-template.md`.
- `DESIGN.md` (Song row collapsed/expanded, Song detail, Admin controls, Core Screens, Extra
  participants), `bb-blues-jam-design-prompt.md` §1–3 and "Reglas de diseño",
  `bb-blues-jam-bitacora.md` (D-07, D-15, 6.18), `CONTEXT.md` (no row/expansion term),
  `docs/risks-and-open-questions.md` (expanded row, song detail, removal and set-key risks),
  `docs/domain-model.md` l.159, `docs/user-and-access-model.md` l.142, `docs/design/README.md`.
- `feature/song-detail/**` (presenter, `toSongDetail`, UiModel, screen, copy, defaults, Koin, 7
  test files), `feature/next-jam`: `NextJamPresenter.kt`, `NextJamAdminState.kt`,
  `NextJamAdminMapping.kt`, `NextJamUiModel.kt`, `NextJamScreen.kt`, `NextJamListContent.kt`,
  `AdminControls.kt`, `LineupControls.kt`, `MoveControlsState.kt`, `ExtraParticipantUiMapping.kt`,
  `ExtraParticipantFormState.kt`, `RemovalHandler.kt`, `AdminRefresh.kt`, `ExpandedRows.kt`,
  `NextJamCopy.kt`, `di/NextJamModule.kt`, test names of every next-jam test class.
- `app/.../navigation/AppNavHost.kt`, `TabsShell.kt`, `AppRoutes.kt`, `AppRoutesTest.kt`,
  `BluesJamApp.kt`, `app/build.gradle.kts`, `settings.gradle.kts`.
- `konsist-test/.../ModuleIsolationTest.kt` (scope sanity, 17 rules, `AMBER_ROLE_ALLOWLIST`).
- `core/ui/.../lineup/*` (`ExpandIndicator`, `InstrumentGroups*`, `LineupLine.showInstrument`),
  `core/data/.../setlist/*` (every pending entry type carries `jamDate` and `songId`).

### Existing Patterns To Follow

- Navigation callbacks in presenter `Params`, read through `rememberUpdatedState` (song detail,
  next jam). `:app` binds them; features never import `androidx.navigation`.
- `SetKeyPresenter.setKeyModel`: `Loading` until the flag and the jams are read, `Gone` otherwise.
- Overlays come from repository `Sending` entries only; `Failed` is the revert (key, lineup,
  assignment, clear, extras). All entries are process-wide singletons in `SetlistRepository`, so two
  presenters observing them see the same state.
- Mutations are launched `CoroutineStart.UNDISPATCHED` and run in `DataScope`, so leaving a screen
  never cancels a write (architecture skill, add-song Part A).
- Per-song admin UI keys use `removalKey(date, songId)`; handlers keyed by (date, songId, step).
- No Compose UI test harness: everything checkable goes in the UiModel; drawn semantics on device.

### Current Gaps / Findings

- The detail route is `song/{jamDate}/{position}` and the lookup is by position. The list passes the
  pre-overlay cached position. Once the detail hosts mutations, a move or removal confirmed while
  it is open would silently switch the detail to another song (and its controls would edit it).
- `InstrumentGroups` (`:core:ui`) is used only by the detail; `ExpandIndicator` only by the row.
- Konsist `AMBER_ROLE_ALLOWLIST` has a `"feature/song-detail" to setOf("key")` entry.
- `SongDetailModelShapeTest` asserts "the presenter takes only the jams repository": it must change.

## Technical Approach

### Planner decisions (labelled; change only with the orchestrator)

- **P1 — detail lineup is `LineupPanel` for everyone** (open-first sections), replacing
  `InstrumentGroups` (reverses the detail's G1 grouping and the prompt's "agrupados por
  instrumento"). Reason: the user moved the panel's logic to the detail; admin actions exist only on
  panel lines; a different musician layout would break "same screen, controls added". See H1.
- **P2 — the detail is identified by song id.** Route `song/{jamDate}/{songId}/{position}`;
  lookup: songs with that id; exactly one → it (position ignored, so it follows moves); several (a
  hand-edited duplicate) → the one at `position`; none → NotFound. Same rule as R1 for mutations.
- **P3 — compact admin bar** (one `Row` under the header, outside its click): `Subir`, `Bajar`,
  `Quitar de la lista`, each a `RowTextAction` with `Modifier.weight(1f)`, inset `spacing.md`,
  bottom `spacing.xs`; disabled edge move as today. The visible `Posición x de n` line is no longer
  drawn (the position label is in the header); drop `MoveUiModel.positionLine` and `MoveCopy.position`
  if then unused. While `Confirming`, the confirmation block replaces the bar; while `Removing`,
  `Quitando…` replaces it (move already hidden).
- **P4 — failure cards**: the list keeps every card as today; the detail additionally shows the
  failed key, lineup, assignment, clear and extras entries of its own (date, songId), after its
  controls, with the same `AddFailureCard` and `setlist.dismiss(id)`.
- **P5 — row accessibility**: the header is one `Role.Button` node, `onClickLabel = "ver el detalle
  del tema"` (TalkBack: "Presioná dos veces para ver el detalle del tema"); no `stateDescription`.
  The node still reads position, title, key description, `Guardando…` and the strip chips.

### Module merge

Move `feature/song-detail/src/**` into `feature/next-jam` under package
`com.bbbjam.feature.nextjam` (file names `SongDetailPresenter.kt`, `SongDetailUiModel.kt`,
`SongDetailScreen.kt`, `SongDetailCopy.kt`, `SongDetailDefaults.kt`); register the presenter in
`nextJamModule`; delete `feature/song-detail/` (incl. `.gitignore`, build file), its `include` in
`settings.gradle.kts`, its line in `app/build.gradle.kts`, `songDetailModule` in `BluesJamApp`.
Merge the two test `FakeJamsRepository`s (keep next-jam's; port anything song-detail's adds).

### Shared overlay (one rule for list and detail)

Extract a pure holder, e.g. `SongOverlays(keyChanges, lineupChanges, assignments, slotClears,
extraParticipantChanges)` with `overlaid(date, song): JamSong` (lineup: pending lineup → assignments
→ slot clears; extras: pending extras), `pendingKey(date, songId)`, `isSaving(date, songId)` (key,
lineup, assignment, clear, extras; the list adds its move/removal sending) and
`failures(date, songId)`. Plus `@Composable rememberSongOverlays(setlist)` collecting the five flows.
`NextJamPresenter` and `SongDetailPresenter` both use it. The detail additionally applies its own
`LineupReservations` (from `rememberLineupControls`) on top, as the list did; the list no longer has
reservations. Musicians never get an overlay.

### Detail presenter

`SongDetailPresenter(jams: JamsRepository, adminSession: AdminSession, setlist: SetlistRepository)`,
`Params(jamDate, songId, position, onBack, onSetKey: (LocalDate, SongId) -> Unit, onAssignSlot:
(LocalDate, SongId, Instrument, SlotPosition) -> Unit)`. Flag collected with `initial = null`;
`Loading` until flag and snapshot are non-null. Musician: lookup in `setlistForMusicians()` of the
upcoming or past jam (as today). Admin: lookup in the upcoming jam's `jam.setlist` (drafts included);
admin controls only for the upcoming jam's songs. Reuse unchanged: `rememberLineupControls`
(editor key + counts + reservations), `ExtraParticipantFormState` (saveable key/name/instrument),
`clearSlot` launched undispatched, `extraEditor`, `lineupEditor`, `lineupPanel(...).copy(hint =
null)`; adapt their receiver from the list's `AdminState` to a song-level state (e.g.
`SongAdminState`) holding only what the detail needs. Do not call `AdminRefreshEffect` here (the
list owns the per-login refresh).

`SongDetailUiModel.Song(title, artist?, keyLabel, key, keyDescription, lineup: LineupPanelUiModel,
back, admin: SongDetailAdminUiModel? = null)`; `SongDetailAdminUiModel(saveStatus: String?, setKey:
SetKeyActionUiModel, lineup: LineupEditorUiModel, extraParticipants: ExtraParticipantEditorUiModel,
failures: List<AddFailureUiModel>)`. `key`/`keyDescription` show the pending key for the admin.

### Detail screen (order)

Back; title (`h1`, heading); artist; admin `Guardando…` (`SaveStatusLine`); key block (`keyDisplay`,
`key` role; in admin mode the block itself is a 48dp-minimum `Role.Button` with click label
`Cambiar tonalidad`); the
lineup `surface` block (`shapes.md`, padding `md`) with `LineupPanel`; at the end of its open-slot
section, the admin sees the `+ Otro` / `Agregar` row, which opens the extra participant dialog. The
action does not create a lineup slot. After the panel, admin `LineupEditor`; admin failure cards.
Reuse the existing composables from
`AdminControls.kt`, `LineupEditorControl.kt`, `ExtraParticipantControls.kt`. `SongDetailDefaults`
keeps the key the largest text (its test now covers the panel and admin styles).

### List

`SongRowUiModel` loses `isExpanded`, `stateDescription`, `toggleLabel`, `lineup`, `artist`,
`detailLabel`; gains `openDetailLabel`; `Event` keeps only `OpenDetail`. `openDetail` calls
`onOpenSong(date, song.songId, entry.cachedPosition)`. `SongRowAdminUiModel(removal, move,
saveStatus)` only. `NextJamPresenter.Params` drops `onSetKey`/`onAssignSlot`; `onOpenSong:
(LocalDate, SongId, Int)` stays last. `AdminState` drops the song-level handlers/form/editor fields
(keeps the overlay lists for strip, key, `Guardando…` and cards). Delete `ExpandedRows.kt`,
`NextJamCopy.ROW_EXPANDED/ROW_COLLAPSED/SHOW_SLOTS/HIDE_SLOTS/OPEN_DETAIL` (replace with
`OPEN_DETAIL = "ver el detalle del tema"`), `OpenDetailAction`, `AdminRowActions` (replaced by the
compact bar). The header keeps `Modifier.clickable(onClickLabel, role = Button)` and the 56dp rule;
`animateContentSize` stays only if the bar's confirmation needs it (fine to keep).

### `:app`

`AppRoutes.SONG_DETAIL = "song/{jamDate}/{songId}/{position}"`, `songDetail(date, songId,
position)`, `parseSongDetail(jamDate, songId, position)` → `SongDetailArgs(date, songId, position)`
(ISO date, `SongId.parseOrNull`, position ≥ 1). `AppNavHost.songDetail` passes `onSetKey`/
`onAssignSlot` navigating to the existing routes (`launchSingleTop`); pickers keep popping by route,
landing back on the detail. `TabsShell` drops `onOpenSetKey`/`onOpenAssignSlot`; KDocs lose
"expanded rows".

### Konsist (flagged rule-data edit)

Remove `"feature/song-detail" to setOf("key")` from `AMBER_ROLE_ALLOWLIST`. Justification: the
module no longer exists; the entry is dead and would silently allow amber to a future module of that
name. No rule logic, count (17) or allowlist of `:feature:next-jam` changes (`key` is already
allowed; the moved `SongDetailDefaults.keyColor` reads `key` only). Demonstrate the remaining rules
still fail: temporarily add `implementation(project(":feature:past-jams"))` to
`feature/next-jam/build.gradle.kts`, run `./gradlew :konsist-test:test`, record the
`build-file-project-deps` failure, revert.

## Expected File Changes

- `settings.gradle.kts`, `app/build.gradle.kts`, `BluesJamApp.kt` — drop `:feature:song-detail`.
- `feature/song-detail/**` — delete (sources move, see above).
- `feature/next-jam/src/main/.../`: create `SongDetailPresenter.kt`, `SongDetailUiModel.kt`,
  `SongDetailScreen.kt`, `SongDetailCopy.kt`, `SongDetailDefaults.kt`, `SongOverlays.kt`; modify
  `NextJamPresenter.kt`, `NextJamAdminState.kt`, `NextJamAdminMapping.kt`, `NextJamUiModel.kt`,
  `NextJamScreen.kt`, `NextJamListContent.kt`, `AdminControls.kt`, `ExtraParticipantUiMapping.kt`,
  `LineupEditorControl.kt`, `LineupControls.kt`, `MoveControlsState.kt`, `MoveCopy.kt`,
  `NextJamCopy.kt`, previews, `di/NextJamModule.kt`; delete `ExpandedRows.kt`.
- `feature/next-jam/src/test/...` — see Verification.
- `core/ui/.../lineup/ExpandIndicator.kt`, `InstrumentGroups*.kt`, `InstrumentGroupsMapperTest.kt`
  — delete (P1); `LineupLine.kt` drop `showInstrument` if unused; `LineupPanel*` KDocs.
- `app/.../navigation/AppRoutes.kt`, `AppNavHost.kt`, `TabsShell.kt`, `AppRoutesTest.kt`.
- `konsist-test/.../ModuleIsolationTest.kt` — the allowlist entry only.
- Docs below; `feature_list.json`, `PROGRESS.md`.

## Visual Design Impact

- UI involved: yes. Source: `DESIGN.md` tokens and existing components; no new token, colour or
  amber role. Screens: Próxima jam (musician, admin), song detail (musician, admin).
- Rules to keep: amber only `key` (row and detail key), `slotOpen` inside `:core:ui`; admin controls
  neutral (`AdminControlsDefaults`); 48dp targets; `spacing.md` insets; voseo copy; no dp literals.
- New artifact: no. Device screenshots are the evidence.

### Copy

| Where | Copy |
|---|---|
| Row click label (P5) | `ver el detalle del tema` |
| Detail (moved, unchanged) | `Tonalidad`, `Cargando el tema`, `Este tema ya no está en la lista`, `Puede que la organización haya cambiado la lista. Volvé a la próxima jam para ver la actual.` |
| Admin controls (reused) | `Cambiar tonalidad`, `Cambiar formación`, the `+ Otro` / `Agregar` row (accessible action `Agregar a Otros`), `Guardando…`, `Subir`, `Bajar`, `Quitar de la lista` and the existing confirmation/card copy |

## Durable Documentation Impact

- `ARCHITECTURE.md` / `CONSTRAINTS.md`: not present; the architecture skill plays that role.
- `AGENTS.md`: not needed (no workflow change; D-15 and the module rule unchanged).
- `.claude/skills/architecture/SKILL.md`: update module table and feature list (no
  `:feature:song-detail`; the detail lives in `:feature:next-jam`), route `SONG_DETAIL` by song id,
  amber allowlist line, the "Per-row UI state" paragraph (expansion removed; filter still held so),
  the song-detail and lineup-panel examples (`InstrumentGroups`/`ExpandIndicator` gone), the
  "a later row action goes inside `AdminRowActions`" guidance (song-level actions go in the detail,
  list-level in the compact bar), and add an "As built by `song-detail-replaces-row-expansion`"
  paragraph (SongOverlays, detail presenter deps, Loading rule). `references/presenter-pattern.md`:
  mark the expansion note as historical/superseded.
- `DESIGN.md`: rewrite "Song row, collapsed" (tap opens the detail, P5 semantics, admin compact bar
  P3); replace "Song row, expanded" with a short note pointing to the detail and keep the panel rules
  (sections, lines, admin line actions) under "Song detail"; rewrite "Song detail" (P1 panel, admin
  section order, Loading rule, live not-found, back keeps tab/filter/scroll); update the Admin
  controls bullets that say "expanded row" (removal, set key, assign, clear, lineup, reorder, extras
  section) and Core Screens 2–3.
- `bb-blues-jam-design-prompt.md`: amend §1 ("Al tocar una fila, se abre el detalle del tema"),
  §2 (cupos, formación y tonalidad se editan en el detalle; en la fila quedan Subir/Bajar y Quitar),
  §3 (the detail carries the lineup panel and admin edits), the "Expandir y colapsar" rule and the
  deliverables line, each marked "(enmienda del 11 de octubre de 2026)".
- `bb-blues-jam-bitacora.md`: a one-line amendment under D-07 and a short Spanish section "6.19
  Sesión del 11 de octubre — la fila deja de expandirse" (why: the expanded row had more logic than
  the detail; decisions: merge modules, list actions on the list, P1/P2).
- `docs/risks-and-open-questions.md`: mark the expanded-row section superseded; resolve "the song
  detail shows the confirmed key only"; the removal risk "Quitando… only while expanded" is gone;
  add new risks (below). `docs/domain-model.md` l.159, `docs/user-and-access-model.md` l.142,
  `docs/design/README.md` (`next-jam-expanded` is historical): one-line fixes.
- `CONTEXT.md`: not needed (no domain term changes). `PROGRESS.md` current-state module count
  (seven Gradle modules) and the session entry.

## Implementation Tasks

Run `CI=true ./init.sh` after each task; it must stay green before the next.

- [ ] T0 Baseline gate; record the JVM test count (668 in 108 classes at 9cb75ac).
- [ ] T1 Merge the module with no behavior change: move sources/tests into next-jam, rename package,
  Koin, `:app` imports, settings/app build files, Konsist allowlist entry; rewrite
  `SongDetailModuleTest` into `NextJamModuleTest`. Prove the Konsist failing case (above).
- [ ] T2 Route by song id (P2): `AppRoutes` + test, lookup + mapping tests (unique id, duplicate id
  by position, renumbered position, missing), `onOpenSong(date, songId, position)` through
  `NextJamPresenter`/`NextJamScreen`/`TabsShell`/`AppNavHost`.
- [ ] T3 Extract `SongOverlays` and use it in the list; existing overlay tests stay green unchanged.
- [ ] T4 Detail admin: presenter deps, Loading rule, P1 panel for everyone, admin model and screen,
  `onSetKey`/`onAssignSlot` wired in `AppNavHost`. The expanded row still works at this point.
- [ ] T5 Remove the expansion from the list (row model, events, screen, compact bar P3, copy P5,
  `Params` pruning, `ExpandedRows` deleted); move/delete the list tests listed below.
- [ ] T6 `:core:ui` cleanup: delete `ExpandIndicator`, `InstrumentGroups*` and its test; drop
  `showInstrument` if unused.
- [ ] T7 Durable docs (section above).
- [ ] T8 Device verification; T9 evidence in `feature_list.json` and `PROGRESS.md`, status `passing`.

## Verification Plan

Gate: `CI=true ./init.sh` exit 0 with `konsist`, `detekt`, `ktlint` all `wired`; lint
`:feature:next-jam` no new issues; `git diff --check` clean.

Tests (JVM, Molecule/Turbine, fakes):
- Delete: `ExpandedRowsTest`; `NextJamPresenterTest` "rows start collapsed…", "expansion follows…",
  "an earlier model's handler still writes…" (expansion), "an expanded row carries the artist…".
- Rewrite in list tests: OpenDetail tests now assert `(date, songId, position)`, including after a
  pending move; a row has only `OpenDetail` and the P5 label; admin rows carry `move` and `removal`
  and no key/lineup/extras controls; a musician row has `admin == null`; the strip/key/`Guardando…`
  overlays of `NextJamKeyChangeTest`, `NextJamLineupChangeTest`, `AssignmentOverlayTest` keep their
  list assertions.
- Move to detail tests (new `SongDetailAdminTest` and friends): `NextJamAdminTest` extras form,
  panel `Quitar`, blank form/`Cancelar`, admin-hint tests; `NextJamKeyChangeTest` "Cambiar
  tonalidad opens the picker"; `NextJamLineupChangeTest` editor open/close and distinct counts,
  filled-slot block; `AssignmentOverlayTest` "admin panel renders the cleared slot"; the moved
  `SongDetail*Test`s (shape test now names the three dependencies; defaults test covers panel and
  admin styles).
- Add: musician vs admin detail model; admin Loading until the flag is read (draft song never
  NotFound); `Anotar` calls `onAssignSlot(date, songId, instrument, ordinal)`; `Liberar` calls
  `clearSlot` once; pending key shows with `Guardando…` in the detail; a list and a detail presenter
  over the same fake repository show the same key and chips for the same Sending entries; detail
  failures are only its song's, `Cerrar` calls `dismiss(id)`; song dropped → NotFound live; an
  earlier model's handlers call the current `onBack`/`onSetKey`/`onAssignSlot`.

Device (Pixel 5; adb `"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"`). Set in the git-ignored
`local.properties`: `bluesjam.demoUpcomingJamLive=true` (the demo replaces any real upcoming jam),
first `bluesjam.debugAdmin=false`, then `true`; `./gradlew :app:installDebug`; confirm the logcat
startup line `jams cache: upcoming <date> (demo)` before any check. **Never tap a write control**:
`Subir`, `Bajar`, `Quitar` (confirm), a key cell, the assign submit, `Liberar`, `−`/`+`, `Agregar`,
an extra's `Quitar`, `Publicar`. Never write to the real Sheet.
1. Musician: list has no chevron; `uiautomator dump` shows the row as one clickable node with no
   `expandido`/`contraído`; tap a row → detail (panel with hint); select a filter chip, scroll, open
   a detail, back → same filter and scroll (screenshots before/after); rotate in the detail, back.
2. Admin: rows show the compact bar; `Quitar de la lista` → confirmation → `Cancelar`; tap a row →
   detail with `Cambiar tonalidad`, `Anotar`, `Liberar`, extra `Quitar` (`Quitar a Valeria, saxo`
   node), `Cambiar formación` (open → `Listo`), the `+ Otro` / `Agregar` row (opens the dialog →
   `Cancelar`); `Cambiar
   tonalidad` → picker → `Volver` → detail; `Anotar` → assign → `Volver` → detail; back → list state.
3. Font scale 2.0: screenshots of the admin bar and the admin detail; restore 1.0.
4. Restore `local.properties`. TalkBack of the row action stays a human follow-up if not run.

## Evidence To Capture

Gate output summary and test counts (before/after, per class moved/deleted/added); the Konsist
failing-case output; `uiautomator` excerpts; screenshots in
`docs/evidence/song-detail-replaces-row-expansion/`; list of write controls not tapped.

## Risks

- Admin edits now take a navigation step; the list shows pending/failed state only as `Guardando…`
  and cards (accepted by the user's decision).
- Detail failure cards sit after the controls; on a long panel they may be off-screen (polite live
  region announces them).
- The detail has no pull/live refresh: during the jam an admin on the detail sees the cache until
  back (writes still mirror into Room).
- A removal sending from the list does not block opening that row's detail; the detail turns
  not-found when confirmed.
- Saved-state bundles from the old build contain an `ExpandedRows` entry that is no longer read
  (harmless); saved `song/{date}/{position}` back stacks fail to parse and pop (handled branch).
- Size: the slice is at the upper bound; the T1–T6 order keeps each step green.

## Needs A Human Decision

- **H1 — resolved by the user on 11 October 2026:** P1. Musician and admin see the same
  open-first panel; `InstrumentGroups` is deleted.
- **Konsist allowlist — approved by the user on 11 October 2026:** remove the dead
  `"feature/song-detail"` entry from `AMBER_ROLE_ALLOWLIST`; rule logic and count (17) unchanged.
- P2–P5 are planner decisions within scope; P3's dropped `Posición x de n` line and P5's copy are
  the ones worth a glance.

## Validator Checklist

- [ ] No expansion state, `ExpandIndicator`, `ExpandedRows` or in-place panel remains; row tap opens
      the detail by song id; back keeps filter and scroll (device).
- [ ] Detail shows the panel with `OTROS`; admin controls call only existing repository functions
      or navigation (D-13); no `if (isAdmin)` in composables.
- [ ] List and detail use one overlay function; scenario 6 covered by a JVM test.
- [ ] `:feature:song-detail` gone from settings, `:app`, Koin; no feature-to-feature dependency;
      Konsist 17 rules; allowlist edit justified and failing case shown; `init.sh` all `wired`.
- [ ] Docs updated as listed, bitácora in Spanish, UI copy in voseo; no write tapped on device.
- [ ] `feature_list.json`/`PROGRESS.md` evidence; status `passing`, not `accepted`.
