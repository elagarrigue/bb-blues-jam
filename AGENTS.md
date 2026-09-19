# Project Agent Instructions

This repository contains BB Blues Jam, a native Android app for organizing a monthly blues jam:
the admin builds and publishes a setlist, and musicians read it to see what is played, in which
key, and where there is an open slot for their instrument.

Code, commits, and technical documentation are in English. User-facing interface copy is in
Rioplatense Spanish using *vos*, never *tú* (D-12).

## Read First

- `CONTEXT.md` — domain language. Read before naming anything.
- `docs/build-brief.md` — product goals, MVP slice, and non-goals.
- `docs/domain-model.md` — entities, states, and authority per entity.
- `docs/risks-and-open-questions.md` — current blockers and open questions.

Read optional docs only when relevant:

- `docs/user-and-access-model.md` — when touching roles, permissions, the admin passphrase, or
  anything that writes.
- `docs/technical-discovery.md` — when touching the stack, the Sheet, external APIs, or
  verification.
- `DESIGN.md` — when touching any UI. It carries the design tokens and the visual rules.
- `bb-blues-jam-design-prompt.md` — per-screen design requirements. Source of truth for screens.
- `bb-blues-jam-bitacora.md` — Spanish, presentation material. The 14 decisions (D-01 … D-14) and
  their reasoning live here. Consult when a change might contradict one.

## Startup Workflow

Before writing code:

1. Confirm the working directory with `pwd`.
2. Read `PROGRESS.md` for current verified state and next step.
3. Read `feature_list.json` and pick the first ready unfinished feature in list order.
4. Run `./init.sh`.
5. If baseline verification fails, fix the baseline before adding new feature work.

## Working Rules

- Work on one feature at a time.
- Do not mark a feature complete just because code was added.
- Keep changes inside the selected feature scope unless a blocker requires a narrow supporting fix.
- Do not silently change verification rules during implementation.
- Update durable repo artifacts instead of relying on chat summaries.

## Project Rules

These come from decisions already made. Each has its reasoning in the bitácora; do not reverse one
without saying so and waiting for confirmation.

- **No ViewModels.** Presentation uses composable presenters; state lives in the Compose runtime and
  presenters are tested with Molecule without Android (D-02).
- **No dependencies between feature modules.** Shared contracts go in `:core:*`, binding happens in
  `:app`, and Konsist enforces it (D-03).
- **No mutation may exist only in the UI.** Every mutation is a repository function or a deeplink,
  because the phase 2 assistant must perform anything the admin can do by hand (D-13).
- **One authority per entity.** The Sheet owns the catalog and past jams; the app owns the upcoming
  setlist and its published state. No bidirectional sync (D-04).
- **The admin sets the key.** Never derive it from an external API (D-08).
- **External music APIs are optional enrichment**, fetched in the background and cached. Never
  during list rendering — MusicBrainz allows 1 req/s (D-09).
- **Every write is validated server-side in Apps Script.** The local admin flag only controls which
  controls are drawn; it does not authorize anything.
- **Out of scope for the MVP:** musician self-signup, in-app tablature, the AI assistant, profiles,
  chat, notifications, onboarding, and a light theme.

## Definition Of Done

A feature is done only when all are true:

- target behavior is implemented,
- required verification actually ran,
- evidence is recorded in `feature_list.json` or `PROGRESS.md`,
- repository remains restartable from the standard startup path,
- relevant docs are updated if product behavior, domain rules, API, or verification changed.

## End Of Session

Before ending a session:

1. Update `PROGRESS.md`.
2. Update `feature_list.json`.
3. Record unresolved risks or blockers.
4. Leave the repo clean enough for the next session to run `./init.sh` immediately.
