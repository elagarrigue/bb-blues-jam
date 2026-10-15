# Feature Implementation Spec: The Anteriores tab becomes Jams; the admin manages upcoming jams there

## Source Feature

- `id`: admin-jams-tab
- `area`: feature-past-jams (plus `:core:ui`, `:core:data` pure helpers, `:app`, `:feature:next-jam`, `:feature:assistant`)
- `depends_on`: admin-set-jam-status (accepted 14 October 2026)
- `status`: not_started at planning time
- `source`: `feature_list.json`; user request of 14 October 2026 (orchestrator decisions: tab named Jams for everyone,
  musicians' view unchanged, status actions and `Publicar lista` move there, `Nueva jam` over the existing
  `createJam`, no backend change, Próxima jam keeps a read-only badge); D-03, D-13, D-17, D-24, D-25, D-26

## Goal

The middle tab is labelled `Jams`. Musicians see the past jams exactly as today. The admin sees, above the past
jams, the upcoming jam and every held-back future jam as cards with their status; the upcoming card carries the
status actions and `Publicar lista` moved from Próxima jam (same rules, same unpublish dialog, same
confirmed-only badge), and a `Nueva jam` action opens a form (fecha, hora, lugar; hora and lugar default from the
last jam) that creates the jam as BORRADOR through the existing `JamCreator` → registry → `createJam` path. On
Próxima jam the admin keeps all setlist editing and sees the status as a read-only badge.

## Non-Goals

- No backend change, no new repository function, no registry change (12 mutations stay), no Room change.
- No status change for a held-back jam (the server's `setJamStatus` accepts only the upcoming jam; see J3, U1).
- No edit or delete of a jam (`admin-edit-delete-jam`, a draft to revisit after this slice).
- No change to the musician's past list, the past jam detail, the assistant's behaviour or prompt.
- No module rename: the module stays `:feature:past-jams` (package `pastjams`); the route stays `AppRoutes.PAST_JAMS`
  (renaming would reset saved tab state and touch Konsist paths for no user value). Recorded in the skill.

## Job Story

When I organize the next dates, I want one place that lists the jams to come with their status and lets me create
the next one, so Próxima jam is only about building the setlist.

## Decisions (planner; J1-J12)

- **J1 Contracts (D-03).** `:feature:past-jams` keeps depending only on `:core:ui` and `:core:data`. It gets three
  narrow contracts, never `SetlistRepository`: `SetlistJamStatusRepository` and `SetlistPublishRepository` (public
  interfaces already in `:core:data`, implemented by `DefaultSetlistRepository`) and `JamCreator` (already bound in
  `:app` to `RegistryJamCreator`, so creating goes through the registry, D-13). `:app`'s `appModule` adds
  `single<SetlistJamStatusRepository> { get<SetlistRepository>() }` and the same for `SetlistPublishRepository`
  (the same instance, so the in-memory entries are shared with Próxima jam). The composition root chooses the
  narrow surface, like `JamCreator`. No Konsist rule is added (an optional follow-up: ban `SetlistRepository` in
  `feature/past-jams`, like rule 19).
- **J2 Admin section.** `PastJamsPresenter(jams, calendar, adminSession, statuses, publishes, creator)`. The admin
  flag is collected with `initial = false`. For a musician nothing changes: same model, same title
  `Jams anteriores`, rows from `forMusicians().past`. For the admin, `PastJamsUiModel.Jams` and `.Empty` carry
  `admin: JamsAdminUiModel?` (null for musicians; `Loading`/`Failed` unchanged), the title is `Jams`, and the
  section `PRÓXIMAS` is drawn above a heading `ANTERIORES` and the unchanged past rows (still `forMusicians().past`:
  a past BORRADOR stays hidden for everyone, as today). The upcoming cards come from the **raw** snapshot:
  `upcoming` then `heldBack` in date order, BORRADOR included (D-26 admin view).
- **J3 Cards.** Each future card: `Sábado 31 de octubre · 21:00` (`jamDateLabel` rule, no year), the venue, the
  status badge (J5), the status note, the song count (`12 temas`, `1 tema`, `Sin temas todavía`, or `No se pudo
  leer la lista` for an unavailable setlist). The **upcoming** card adds the status actions (`Pasar a creada`,
  `Pasar a borrador`, exactly `statusTargets`), `Publicar lista` (≥1 song and not PUBLICADA, inline confirmation
  with the count and `Después podés volver a cambiar el estado desde acá.`), the unpublish `AlertDialog`, the
  sending lines and the failure cards, all with today's behaviour and copy. A **held-back** card shows the badge and
  the line `En espera: su estado se cambia cuando sea la próxima jam.` and no action (the server refuses it).
  The cards are not tappable (setlist editing stays on Próxima jam).
- **J4 Moved, not copied.** From `:feature:next-jam` to `:feature:past-jams` (new names with a `Jams` prefix where
  needed): `JamStatusControls`, `JamStatusMapping`, `JamStatusViews`, `JamStatusDefaults`, `PublishControls`,
  `PublishUiModel`, `PublishDetails`, the publish/status part of `PublishStatusBlock` and of `AdminControlsDefaults.status()`,
  the status/publish copy of `NextJamCopy`, and the tests `JamStatusAdminTest`, `JamStatusDefaultsTest` and the publish
  tests of `NextJamAdminTest`. They are rewired to a `JamsAdminState` in `:feature:past-jams` (statuses,
  publishes, confirming keys, in-flight guards; same saveable `"date|STATUS"` key). The failure reason mapping the
  cards need (`AccessRefused`, `Offline`, `Unavailable`, `empty_setlist`, `unknown_jam`/`jam_not_editable`/
  `duplicate_date`, create codes of J8) is a small `JamsFailureMessages.kt`; `:feature:next-jam` keeps its own
  `failureMessage` for the setlist cards. Nothing status- or publish-related remains in `:feature:next-jam`.
- **J5 Shared badge in `:core:ui`.** Both features draw a status badge, so it moves to
  `com.bbbjam.core.ui.status`: `JamStatusBadge(model)`, `JamStatusBadgeUiModel(label, isPublished)`, the pure
  mapper `jamStatusBadge(status: JamStatus)` (`BORRADOR`, `CREADA`, `PUBLICADA`), internal `JamStatusBadgeCopy` and
  `JamStatusBadgeDefaults` (muted `textMuted` on `surfaceRaised`; PUBLICADA `published`/`onPublished`, read inside
  `:core:ui`, so no allowlist entry). The notes stay in each feature's copy.
- **J6 Próxima jam (admin).** `AdminStatusUiModel` becomes `(badge: JamStatusBadgeUiModel, note, hint)`: no
  `publish`, no `statusChange`. `hint = "El estado y las fechas se manejan en la pestaña Jams."`. `NextJamPresenter`
  stops collecting `observePublishes`/`observeJamStatusChanges`. `ADMIN_NO_UPCOMING` becomes `Para armar la lista,
  creá la próxima jam en la pestaña Jams.` Musicians' Próxima jam is unchanged.
- **J7 Nueva jam form.** A neutral full-width `Nueva jam` button at the top of `PRÓXIMAS` (also when there is no
  future jam) opens a modal `AlertDialog` with three outlined fields: `Fecha` (`DD/MM/AAAA`, empty, separators `/`,
  `-`, `.` accepted so the numeric keyboard works), `Hora` (`HH:MM`, `:` or `.`, one-digit hour accepted, normalized)
  and `Lugar` (trimmed, inner spaces collapsed, 1..80, no leading `=`, `+`, `-`, `@`, no control characters).
  Hora and lugar are seeded from the **latest-dated jam** in the raw snapshot (held back, upcoming or past);
  21:00 and empty if there is none. Field state is saveable and reset each time the dialog opens. Today is
  `JamCalendar.today()` read once per snapshot. `Crear la jam` is enabled only when the plan is `Ready`.
- **J8 Rules shared with the assistant (moved, not copied).** `jamPlan` (`JamPlan`, `JamHeldBack`) moves from
  `:feature:assistant` to `:core:data` `jams/JamCreationPlan.kt` as `jamCreationPlan(date, snapshot, today)`;
  the assistant calls it with `jam.date` and `JamPlanTest` moves with it (assertions unchanged). While moving, fix
  `EXISTS` to also check `heldBack` dates (today a held-back date passes as `Ready` and the server answers
  `jam_exists`); add that test. The venue and time rules move from the internal `JamProposalValidator` to a public
  `jams/JamFields.kt` (`normalizeVenue(text): String?`, `MAX_VENUE_LENGTH = 80`, `isValidTime(text)`), which the
  validator calls (its tests unchanged). `DD/MM/AAAA` parsing is UI input and stays in the feature
  (`NewJamForm.kt`, pure). Blocked plans are field errors: `PAST`, `EXISTS`, `EARLIER` (D-25 amendment kept).
  `Ready(NewWaits)` shows the held-back note; `Ready(None)` none.
- **J9 Create flow.** `Crear la jam` (the dialog's button is the confirmation) closes the dialog and launches
  `creator.createJam(date, time, venue)` undispatched with an in-flight guard (a double tap creates once). State in
  the composition (`remember`): `Creando la jam…` (polite), then `Jam creada como borrador.` on `Created`, or a
  failure card (`No se pudo crear la jam`, reason, `La jam no se creó.`, `Reintentar` resending the same values,
  `Cerrar`); `ScriptOutdated` keeps the guide line with `Cerrar` only. The new card appears from Room
  (`insertCreatedJam`, after the server echo), never optimistically. If the tab's composition dies mid-call, the
  write still finishes in `DataScope`; only the result line is lost (documented). Reason lines: `jam_exists`/
  `tab_exists` → `Ya hay una jam o una pestaña con esa fecha en la planilla.`; `past_date` → `Esa fecha ya pasó.`;
  `invalid_*` → `La planilla rechazó los datos. Revisalos y probá de nuevo.`; others via `JamsFailureMessages`.
- **J10 Amber (D-17).** `AMBER_ROLE_ALLOWLIST["feature/past-jams"]` gains `primaryAction`, `onPrimaryAction`
  (`Publicar lista` only). `feature/next-jam` loses `primaryAction`, `onPrimaryAction`, `published`, `onPublished` if no
  read remains (keep `key`). `Nueva jam`, `Crear la jam` and status actions are not amber. Still 19 rules.
- **J11 Tab.** `AppTab.PAST_JAMS` label `Jams` (icon `DateRange` unchanged, route unchanged); `AppTabTest` updated.
- **J12 Assistant.** Only the `jamPlan` import changes. `JamCopy.DRAFT_LINE` stays true.

## Copy (vos; `PastJamsCopy` unless noted)

`TITLE_ADMIN = "Jams"`, `UPCOMING_HEADING = "PRÓXIMAS"`, `PAST_HEADING = "ANTERIORES"`, `NO_FUTURE = "No hay ninguna jam
programada."`, held-back line (J3), song counts (J3). Notes per status (moved): BORRADOR `Los músicos todavía no ven esta
jam.`, CREADA `Los músicos ven la fecha, pero todavía no la lista.`, PUBLICADA `Los músicos ya ven esta lista.`. Form:
title `Nueva jam`, labels `Fecha`/`Hora`/`Lugar`, supporting `DD/MM/AAAA`/`HH:MM`; errors `Escribí la fecha como
DD/MM/AAAA.`, `Esa fecha ya pasó.`, `Ya hay una jam ese día.`, EARLIER `Es antes de la próxima jam (<sábado 31 de
octubre>) y la dejaría en espera. Si de verdad querés eso, agregala en la planilla.`, `Escribí la hora como HH:MM, por
ejemplo 21:00.`, `Escribí el lugar.`, `El lugar puede tener hasta 80 letras.`, `El lugar no puede empezar con =, +, - o
@.`; notes `Queda en espera: la próxima jam sigue siendo la del <sábado 31 de octubre>.` and `Se crea como borrador: los
músicos no la ven hasta que la pases a creada o publiques la lista.`; buttons `Crear la jam`, `Cancelar`. Outdated:
`El script de la planilla todavía no sabe crear jams. Actualizalo con la guía y probá de nuevo.` Moved status/publish
copy keeps its exact text.

## Acceptance Scenarios

1. **Musician (existing tests pass).** Every existing past-jams test passes with only constructor/fake changes (listed
   in the evidence); a musician model has `admin == null` and title `Jams anteriores`; held-back and BORRADOR jams
   never appear.
2. **Admin section (Molecule).** Raw snapshot with an upcoming CREADA (12 songs) and a held-back BORRADOR: title `Jams`,
   two cards in date order, badges, counts; the upcoming has `Pasar a borrador` and `Publicar lista`, the held-back has
   the line and no action. No future jam: `NO_FUTURE` and `Nueva jam`. Past rows unchanged under `ANTERIORES`.
3. **Status and publish (moved tests).** Every scenario of `admin-set-jam-status` 9-11 and the publish presenter tests,
   now against `PastJamsPresenter` with fakes of the three contracts: direct BORRADOR↔CREADA, dialog when leaving
   PUBLICADA, double taps write once, sending hides the actions, confirmed-only badge, failure and outdated cards.
4. **Form (pure, JVM).** Parsing (`5/11/2026`, `05-11-2026`, `21.00`, `9:30`), each field error, `EARLIER` with an
   upcoming jam, `EXISTS` for a past, upcoming or held-back date, today allowed, the held-back note, seeds from the
   latest jam, `canCreate` only when `Ready`.
5. **Create (Molecule).** Open → seeded hora/lugar, empty fecha; `Cancelar`/dismiss calls nothing; `Crear la jam` calls
   `createJam` once with parsed values (double tap once); `Created` → success line; `NotCreated` → card with retry;
   `ScriptOutdated` → `Cerrar` only.
6. **Próxima jam (Molecule).** The admin model has the badge, note and hint and no publish or status model; setlist
   controls unchanged; musician unchanged.
7. **Shared pieces (JVM).** `jamStatusBadge` per status; `JamStatusBadgeDefaults` roles; `jamCreationPlan` moved tests
   plus held-back EXISTS; `JamFields`; assistant tests green with the new import.
8. **`:app` (JVM).** `AppTabTest` label `Jams`; a Koin check (existing module test pattern) that the two narrow
   contracts resolve to the `SetlistRepository` instance.

## Repository Research

Inspected: `AGENTS.md`, `PROGRESS.md`, `feature_list.json` (both entries), the architecture skill, `DESIGN.md` (bottom
bar, Status badge, Publishing, Changing the status, Crear la jam), `docs/specs/admin-set-jam-status.md`,
`feature/past-jams/{build.gradle.kts, PastJamsPresenter, PastJamsUiModel, di/PastJamsModule}.kt`, its test list,
`feature/next-jam/.../{PublishStatusBlock,JamStatusMapping,JamStatusControls,NextJamAdminState,NextJamUiModel,
AdminControlsDefaults,NextJamCopy}.kt`, `feature/assistant/.../JamPlan.kt` (and callers `JamHandlers`, `JamMapping`),
`core/data/.../setlist/{SetlistPublishRepository,SetlistJamStatusRepository,JamCreator,CreateJamOutcome}.kt`,
`di/DataModule.kt`, `app/.../{di/AppModule,actions/RegistryJamCreator,navigation/AppTab}.kt`, the Konsist allowlist.
Not inspected: `PastJamsScreen.kt` layout details, `NextJamAdminTest` contents (assumed to hold the publish tests),
`JamProposalValidator` internals beyond its rules line. Assumption: icons-core `DateRange` is already the tab icon
(DESIGN.md says so).

## Expected File Changes

- `feature/past-jams`: modify `PastJamsPresenter`, `PastJamsUiModel`, `PastJamsScreen`, `PastJamsCopy`, `di/PastJamsModule`
  (factory with six `get()`), previews; new `JamsAdminState`, `JamsAdminMapping`, `JamsAdminViews` (cards, section),
  moved status/publish files, `JamsFailureMessages`, `NewJamForm`, `NewJamControls`, `NewJamDialog`, `JamsAdminDefaults`;
  tests `JamsAdminTest`, moved status/publish tests, `NewJamFormTest`, `NewJamCreateTest`, `JamsAdminDefaultsTest`
  (only allowed roles), fakes for the three contracts.
- `feature/next-jam`: delete the moved files; modify `NextJamUiModel`, `NextJamAdminState`, `NextJamAdminMapping`,
  `NextJamPresenter`, `PublishStatusBlock` (badge, note, hint only, may be renamed `AdminStatusBlock.kt`),
  `AdminControlsDefaults`, `NextJamCopy`, previews, tests.
- `core/ui`: new `status/{JamStatusBadge,JamStatusBadgeUiModel,JamStatusBadgeCopy,JamStatusBadgeDefaults}.kt` and tests.
- `core/data`: new `jams/{JamCreationPlan,JamFields}.kt` and tests; modify `assistant/JamProposalValidator.kt`.
- `feature/assistant`: delete `JamPlan.kt` and `JamPlanTest.kt` (moved); update imports.
- `app`: `di/AppModule.kt`, `navigation/AppTab.kt`, `AppTabTest`, module test.
- `konsist-test/.../ModuleIsolationTest.kt`: allowlist only (J10).

## Visual Design Impact

DESIGN.md: bottom bar label `Jams` (and every "Anteriores" tab mention); a new `### Jams tab (admin-jams-tab)`: title
`Jams` (`h1`), `PRÓXIMAS`/`ANTERIORES` headings (`caption`, `textMuted`, heading semantics), cards on `surface`,
`shapes.md`, date line `songTitle`/`text`, venue and count `body`/`textMuted`, badge from `:core:ui`; `Nueva jam`
full-width, `surfaceRaised` fill, `text` content, 48dp, never amber; the form dialog (`surfaceRaised`, labelled outlined
fields, field errors in `error` `caption`, notes `textMuted`, `text` buttons, disabled muted). Move §Publishing and
§Changing the status under the Jams tab (same visuals). Próxima jam: the status block is badge, note and hint
(`caption`, `textMuted`) only. Update `bb-blues-jam-design-prompt.md` tab names.

## Durable Documentation Impact

- `bb-blues-jam-bitacora.md`: **update** with **D-27** (Spanish): *"La pestaña Anteriores pasa a ser Jams. El admin
  maneja ahí las jams que vienen: estado, publicar y crear la nueva; Próxima jam queda para armar la lista y solo
  muestra el estado. Los músicos ven las jams anteriores igual que antes. Pedido del organizador del 14 de octubre
  de 2026."* (The edit/delete draft's decision is renumbered D-28.)
- Architecture skill: **update** ("As built by `admin-jams-tab`": J1, J4, J5, J8, module/route names kept, allowlists).
- `CONTEXT.md`: **update** if it names the tab (Anteriores → Jams). `docs/user-and-access-model.md`: **update** where
  the admin changes the status and creates jams. `docs/domain-model.md`, `docs/apps-script-api.md`,
  `docs/sheet-schema.md`: not needed (no data or contract change). `docs/risks-and-open-questions.md`: **update** (J9
  lost result line; U1 if unanswered). `AGENTS.md`: not needed.

## Implementation Plan

Two parts, each ending with a green gate (a natural split point if the session runs long):
- **Part A (move).** 1. `:core:ui` badge with tests. 2. `:app` bindings and tab label (tests failing first). 3. Move the
  status/publish code and tests into `:feature:past-jams` with `JamsAdminState`, cards and section; reduce Próxima jam
  (J6). 4. Allowlists. 5. `CI=true ./init.sh`.
- **Part B (create).** 6. Move `jamPlan` and the field rules into `:core:data` (moved tests green, held-back EXISTS test
  failing first). 7. Form, controls, dialog, create flow (tests first). 8. Docs, D-27. 9. Gate. 10. Device check.

## Verification Plan

- `CI=true ./init.sh` exits 0 with konsist, detekt and ktlint `wired`; 19 Konsist rules, no rule loosened beyond the
  J10 allowlist entries, no baseline; report the JVM test count before and after. Node tests are untouched (207).
- **Device (Pixel 5; never write to the real Sheet).** md5 of `local.properties` before and after. Stored passphrase =
  admin; the real upcoming 2026-10-31 jam is CREADA.
  1. The bar shows `Jams`. In Jams: title, the 2026-10-31 card (CREADA, count, `Pasar a borrador`, `Publicar lista`),
     the past rows below. Screenshot and `uiautomator dump`. **Never tap `Pasar a borrador`** (it writes at once).
  2. Tap `Publicar lista`: the inline confirmation shows; tap `Cancelar` (no write). **Never tap `Publicar`.**
  3. Tap `Nueva jam`: hora and lugar seeded from 2026-10-31; type `20/10/2026` → EARLIER error; type a date after
     31/10 → held-back note; screenshot and dump. **Never tap `Crear la jam`.** `Cancelar`, reopen, close with back.
  4. Próxima jam: badge `CREADA`, note, hint; no status action, no `Publicar lista`; setlist controls present.
  5. Logcat shows no `setJamStatus`, `publishSetlist` or `createJam`; the jam is unchanged.
  The musician view is not checked on the Pixel (logging out would clear the stored passphrase); it is covered by the
  unchanged JVM tests. Optional: `demoUpcomingJamLive=true` shows the real jam as a held-back card (screenshot only,
  no tap), then restore `local.properties` byte for byte.

## Evidence To Capture

Gate output with counts; failing-first outputs (app tests, held-back EXISTS); list of amended pre-existing tests;
device screenshots, dumps, logcat excerpt and md5 in `docs/evidence/admin-jams-tab/`; `feature_list.json` `passing`;
PROGRESS entry (no redeploy owed).

## Validator Checklist

- [ ] Musician Jams tab identical to the old Anteriores except the bar label; existing tests pass with only listed changes.
- [ ] `:feature:past-jams` references only `SetlistJamStatusRepository`, `SetlistPublishRepository`, `JamCreator` (no
      `SetlistRepository`, no `AdminWriter`), bound in `:app` to the shared instance; no feature imports another.
- [ ] Status/publish code moved, not copied: nothing of it left in `:feature:next-jam`; behaviour and copy unchanged.
- [ ] Held-back jams listed for the admin only, with no action; past BORRADOR still hidden.
- [ ] `Nueva jam`: D-25 rules (past, exists incl. held-back, earlier) as field errors, held-back note, seeds from the
      latest jam, creation through `JamCreator`, result only after the server answer, double tap creates once.
- [ ] `jamPlan` and the field rules live once, in `:core:data`; the assistant uses them.
- [ ] Badge in `:core:ui`; amber only on `Publicar lista` and the PUBLICADA badge; allowlists per J10; 19 rules.
- [ ] D-27 recorded; DESIGN.md and the skill updated; gate green with three `wired`; device check tapped no write.

## Open Questions For The User

- **U1 (held-back status).** The entry says each future jam gets the status actions, but the deployed `setJamStatus`
  only accepts the upcoming jam, and this slice has no backend change. Planner default: held-back cards show the badge
  and `En espera: su estado se cambia cuando sea la próxima jam.` If you want actions on them, it is a server change
  (relax `requireEditableJam_` for `setJamStatus` to any future jam), best folded into `admin-edit-delete-jam`, which
  already changes `Post.js` and needs a redeploy.
