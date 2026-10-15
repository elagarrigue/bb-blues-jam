# jam-date-copy-polish

Source: `feature_list.json` entry `jam-date-copy-polish` (area assistant, depends on `assistant-proposal-targets`).
Single slice, no Parts.

## Goal

1. The assistant's free text (message, questions, reasons) names dates the way the app does ("sábado 31 de octubre"), not ISO.
2. A new jam dated before the upcoming jam no longer sends the admin to the Sheet; the copy explains it and names the in-app path.

Non-goals: no change to `jamCreationPlan` / `jamEditPlan` rules; no change to JSON fields (`jam.date`, song fields stay ISO / structured); no new mutation, tool or Gradle dependency; other "planilla" copies stay.

## Story

As the admin, when the assistant talks about a date I read "sábado 31 de octubre", and when I try a date before the upcoming jam I am told what to do inside the app, not to edit the Sheet.

## Acceptance scenarios

1. Given the upcoming jam is 2026-10-31, when the assistant answers in message/questions, then it writes "sábado 31 de octubre" (live check; model output, not deterministic).
2. Given the `jamSchedule` tool result, then every date line carries the display date next to the ISO one (deterministic, unit-tested).
3. Given a form (Jams tab "Nueva jam" or Asistente create form) with a date before the upcoming jam, then the error is the new copy below and contains no "planilla".
4. Given the assistant proposes a jam earlier than the upcoming one (blocked plan), then the blocked text is the new copy, no "planilla".
5. Given the same inputs as before, `jamCreationPlan` / `jamEditPlan` return the same results (their tests untouched).

## Findings (inspected)

- Prompt: `core/data/src/main/kotlin/com/bbbjam/core/data/assistant/AssistantPrompt.kt` (`SYSTEM`, internal object). Lines 35-37 also say "it can be added in the Sheet if it is really intended" (obsolete in the same way). Date rule line 31 "Write jam.date as YYYY-MM-DD" is the JSON field; keep.
- Tool: `core/data/.../assistant/JamScheduleTool.kt`, `render(snapshot, today)` is pure and returns ISO only (`today 2026-10-15 (Buenos Aires)`, `upcoming 2026-10-31 21:00 | venue | CREADA`, `last ...`). The model copies these, hence "del 2026-10-31".
- `SpanishDateNames` lives in `:core:ui`; `:core:data` must not depend on it (architecture skill). 
- Old copy: `core/ui/.../jamform/JamFormText.kt:31-33` `earlierError(upcoming)` (used by `NewJamMapping.kt:92`, `CreateFormMapping.kt:68`) and `feature/assistant/.../JamCopy.kt:53-55` `blockedEarlier(upcoming)` (used by `JamMapping.kt:51`).
- Key finding on the rules: `jamEditPlan` (`core/data/.../jams/JamEditPlan.kt`) allows the UPCOMING jam to move to any non-past, non-taken date earlier than itself (the EARLIER block applies only to held-back jams; the upcoming one is only blocked from moving LATER than the first held-back jam). `jamCreationPlan` blocks any new date earlier than the upcoming one. So moving the upcoming jam later does not unblock creation; the real in-app path for "I want a jam on an earlier date" is to move the upcoming jam itself to that date in the Jams tab. A truly separate earlier jam while keeping the upcoming one (putting it on hold) remains Sheet-only, rare, and is not offered by the copy. Edge: the upcoming jam can be moved only if its date is not past and the new date is not taken; copy need not repeat that (the edit form reports it).
- Tab label is "Jams" (`AppTab.PAST_JAMS`); existing copy writes "la pestaña Jams" (`NextJamCopy`).

## Technical approach

Prompt plus tool, both. The prompt alone makes the model compute weekdays itself, which LLMs get wrong; a display date supplied by the tool removes that risk.

- `JamScheduleTool.render`: after each ISO date add the display date in parentheses, e.g. `today 2026-10-15 (jueves 15 de octubre, Buenos Aires)`, `upcoming 2026-10-31 (sábado 31 de octubre) 21:00 | venue | CREADA`, same for `last`. Implement day/month names locally in `:core:data` (private lowercase maps in the tool file, or `java.time.format.TextStyle` with `Locale("es","AR")` if the test proves stable on the JVM; implementer chooses, must be deterministic in tests). ISO stays so the model can still copy `jam.date`.
- `AssistantPrompt.SYSTEM`: add a rule: in message, questions and reasons write dates in Spanish long form without the year ("sábado 31 de octubre", using the display date jamSchedule gives), never YYYY-MM-DD; ISO only inside `jam.date`. Replace lines 35-37 so the earlier-date explanation says the admin can change the upcoming jam's date in the Jams tab if they want a jam earlier (no Sheet). Keep prompt in English (it is model instruction); only the model's output is Spanish.
- Copy (Spanish, vos, D-12):
  - `JamFormText.earlierError(upcoming)`: "Es antes de la próxima jam (sábado 31 de octubre). Si querés adelantar esa jam, editá su fecha en Jams." This function is shared by the Jams tab form and the Asistente form, so the wording must stay tab-neutral (it reads fine in both); keep `sentenceDate(upcoming)`, drop "planilla". Final wording may be adjusted.
  - `JamCopy.blockedEarlier(upcoming)`: "Esa fecha es anterior a la próxima jam (<jamDate>). Si querés adelantarla, cambiá la fecha de esa jam en la pestaña Jams." Does not say "pedime otra" because the assistant cannot edit dates.
- Mutation rule (D-13): no mutation added; editing the date already exists as the `admin-edit-delete-jam` repository function. Nothing UI-only.

## Expected file changes

- `core/data/src/main/kotlin/com/bbbjam/core/data/assistant/AssistantPrompt.kt` (rules).
- `core/data/src/main/kotlin/com/bbbjam/core/data/assistant/JamScheduleTool.kt` (display dates).
- `core/ui/src/main/kotlin/com/bbbjam/core/ui/jamform/JamFormText.kt`, `feature/assistant/src/main/kotlin/com/bbbjam/feature/assistant/JamCopy.kt`.
- Tests to amend (old copy asserted): `core/ui/src/test/.../jamform/JamFormDefaultsTest.kt:31`, `feature/past-jams/src/test/.../NewJamCreateTest.kt:128`, `feature/assistant/src/test/.../CreatePresenterTest.kt:158`, `JamPresenterTest.kt:157`. Tests in `core/data/src/test/.../assistant/` (`DefaultSetlistAssistantTest.kt` and any `JamScheduleTool` test): assert the new `render` lines and that `AssistantPrompt.SYSTEM` contains the long-date instruction, still says `jam.date as YYYY-MM-DD`, and no longer contains "Sheet".
- No UI/visual change (text only); `DESIGN.md` not needed. Docs: `docs/phase-2-brief.md` / `PROGRESS.md` update only if they quote the old behavior; `docs/apps-script-api.md` and `docs/sheet-schema.md` not needed.

## Tasks

1. Add display dates to `JamScheduleTool.render`; update its tests.
2. Update `AssistantPrompt.SYSTEM`; add the prompt test.
3. Rewrite the two earlier-date copies; amend the four tests above.
4. Run the gate; run the device check.

## Verification

- `CI=true ./init.sh` exits 0, with `konsist`, `detekt`, `ktlint` printed `wired`.
- Unit tests as listed; plan tests (`JamCreationPlanTest`, `JamEditPlanTest`, `JamFormTest`) unchanged and green.
- Grep: `rg "agregala en la planilla"` over `*.kt` returns nothing.
- Device check (read-only): one live Asistente request (DevExpert answers) asking for a setlist that makes the assistant mention the upcoming jam's date; confirm long form in message. No write taps (no Aplicar/Crear), never print the key, no Sheet jams added or deleted (use the debug demo jam context). Model output is non-deterministic: if one reply still shows ISO, repeat once; the deterministic guarantee is the tool and prompt tests.

## Other "planilla" copies (listed, left alone)

All concern errors only fixable in the Sheet (script outdated, sheet rejected data, duplicate or unordered rows, tab exists, `INTERRUPTED` "revisá la planilla", `AddSongCopy.EMPTY` catalog). `JamCopy.JAM_EXISTS` ("ya hay una jam con esa fecha en la planilla") is borderline: the admin could fix it in the Jams tab, but it is a server-side race-rejection message and not obsolete; leave it.

## Validator checklist

- No "planilla"/"Sheet" in the two earlier copies nor the prompt's earlier-date rule; copy uses vos and the Jams tab.
- `render` keeps ISO and adds display dates; `jam.date` instruction still ISO.
- Plans and their tests untouched; no new dependency; `:core:data` does not import `:core:ui`.
- Gate output and device-check evidence recorded.
