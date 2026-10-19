# BB Blues Jam — Harness presentation

An eleven-slide, Spanish presentation based on `docs/entregable-harness-ia.md`. It explains the
development process with the AI harness; the app itself is shown live afterwards, so the slides do
not walk through screens. Arc: the problem choice, discovery to plan, the six harness pieces, the
per-slice cycle, verification, the human in the loop, errors and what caught them, phase 2 as proof
of the plan, the final whole-system review, and lessons. Internal decision codes (D-xx) appear only
in the speaker notes, never on the slides.
The presentation is independent of the Android application and has no build or runtime dependencies.

Open `dist/index.html` directly in a browser. Fonts, branding and the architecture diagram
are bundled locally; the presentation works offline. To serve it locally, run
`python -m http.server 8080 --directory dist` from this directory.

Controls: left/right arrows, Page Up/Down or Space; 1–9 to jump; Home/End;
F for fullscreen, G for the slide index and N for speaker notes. On touchscreens,
swipe horizontally. Notes include source sections and talking points.
`notas-presentador.md` contains the full Spanish presenter script (about eleven and a half minutes), with timings,
visual cues, transitions and supporting references for each slide; it is the source for the
embedded notes: run `python sync-notes.py` after editing it. Notes appear on the same presentation screen when N is pressed, so use the
separate document on another device when sharing a screen with the audience.
Use the browser print dialog to export eleven landscape pages to PDF; enable background graphics.

The reported numbers are the snapshot in the source deliverable (18 October 2026),
not live repository counters: 75 features, 74 accepted, one blocked by scope and none
in progress; 74 specs; 29 decisions; over 240 commits; 1,156 JVM testcases and 22 Konsist rules.
The JVM total includes the opt-in live testcase that is skipped by default. `snapshot.json`
records the figures and their source. Feature counts and specs were checked against the repository.
The presentation does not change feature statuses or claim new product acceptance.

Brand tokens and bundled fonts come from `DESIGN.md` and `core/ui/src/main/res/`.
This folder is the only copy of the presentation; edit it here.

The refreshed narrative includes the final code review: nine findings resolved in seven
slices, feature public declarations reduced from 122 to 14, and the Koog agent moved into
`:core:assistant`. There are nine production modules. The phase 2 slide's architecture link
opens a bundled overview focused on responsibilities and principal flows. DI and write-binding
details are deliberately omitted from that audience-facing SVG.

Run `node verify.mjs` for browser QA: slide count, notes, absence of D-xx codes on slides, overflow at
four viewports, navigation, and an eleven-page print export. Screenshots of every slide land in `.qa/`.
