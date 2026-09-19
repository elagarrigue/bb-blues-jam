# Design assets from Stitch

Where Stitch output goes and what each part is for. `../../DESIGN.md` holds the authoritative
tokens and rules; everything here is source material for them.

## Layout

```
docs/design/
  screens/     PNG per screen and per state. Visual reference.
  export/      The unzipped Stitch export. Source of real token values.
  README.md    This file.
```

## Naming

Use the screen ids below so a file is identifiable without opening it. Add `-admin` for the admin
variant and `-<state>` for a state.

| Id | Screen |
|---|---|
| `next-jam` | Próxima jam, musician view |
| `next-jam-admin` | Próxima jam, admin view |
| `next-jam-expanded` | A song row expanded in place |
| `next-jam-filtered` | An instrument filter active |
| `song-detail` | Song detail |
| `past-jams` | Past jams list |
| `past-jam-detail` | Past jam detail |
| `info` | Info |
| `admin-login` | Admin passphrase login |
| `assign-musician` | Assign a musician to a slot |

States: `-loading`, `-empty`, `-no-results`, `-error`, `-offline`, `-unpublished`.

Examples: `next-jam-expanded.png`, `next-jam-admin.png`, `next-jam-no-results.png`.

## What each export form is for

| Form | Use | Authority |
|---|---|---|
| `.zip` | Unzip into `export/`. Read the CSS for real hex, type sizes and spacing. | Token values only |
| Project summary | Compare against `../../DESIGN.md` to find divergence. | None, it is a cross-check |
| MCP | Read screens directly, no copy-paste. | Same as the zip |
| Figma | Editing and the style sheet. Link it from `../../DESIGN.md`. | Layout and components |
| PNG | Per-screen visual reference in `screens/`. | Reference only |
| Netlify / Lovable / Bolt | Not used. They deploy or keep building a web app; the target is Compose. | — |

## Rules

- **The markup is not the implementation.** Stitch emits HTML and Tailwind; this app is Jetpack
  Compose. Take the values, discard the structure.
- **Generated copy is not authoritative.** Stitch tends to write English or invent wording. All UI
  copy is Rioplatense Spanish using *vos* (D-12), and `../../bb-blues-jam-design-prompt.md` is the
  source for what each screen says.
- **Exact spacing and placement are directional**, not a spec, unless a value is promoted into
  `../../DESIGN.md`.
- **Export the states too.** The five states plus the expanded row and the unpublished setlist are
  where the design is actually decided, and they are the easiest to skip.

## Open question this export should close

`../../DESIGN.md` currently carries a proposed palette derived from the written direction ("dark
indigo, neon amber"), not measured from any rendered asset. Recorded as an open question in
`../risks-and-open-questions.md`.

Once the export lands, reconcile: pull the real values out of `export/`, update the tokens in
`../../DESIGN.md`, and close the question.

## Divergences in the Stitch project summary

Reviewed 19 September 2026. The export invented product facts, so **treat its summary as a design
artifact, not as a product description**. Take colors, type and layout from it; take nothing else.

| What the summary says | What is decided | Action |
|---|---|---|
| Venue is "El Motivo Bar" | La Macanuda, Moreno 223, Bahía Blanca | Ignore. Invented. |
| Admin is named "Fede" | No such person; admin is a role, not a name | Ignore. Invented. Copy examples using the name must be rewritten. |
| Lineup is 6 slots, harmonica and keyboards sharing one | 7 slots: 2 guitars, bass, drums, vocals, harmonica, keyboards (D-06) | **Fix in every screen.** The instrument strip is wrong wherever it shows six. |
| WebSockets or SSE for real-time sync | Sheets via Apps Script, one authority per entity, no sync (D-04) | Ignore. |
| IndexedDB / LocalStorage | Room (D-01, native Android) | Ignore. |
| PWA / Android | Native Android only, no KMP (D-01) | Ignore. Explains why the markup is discarded. |
| Song detail shows I-IV-V, bar count, tunings, backline | Not in the domain model | Ignore unless deliberately added later. |

The lineup one is the only divergence that damages the screens themselves rather than just the
prose. Check it first.

### What the summary got right

Worth keeping, because it confirms the direction survived the round trip: amber with strict
functional semantics and its four reserved uses, filled slots in muted grey, 48dp targets, no
horizontal scroll, open slots ordered above filled ones, and Rioplatense Spanish using *vos*.

Its copy examples are good and usable once the invented name is removed: *"Traé tu viola y cable
jack"*.

### What it contributed

Real token values, now reconciled into `../../DESIGN.md`: background `#0C0E13`, surfaces `#111318`
and `#1A1B21`, border `#282A30`, amber `#FFB300`. And a named typeface for headings and keys,
**Barlow Condensed**.

## Check on arrival

The instrument strip is the component the main screen depends on. When the export lands, check how
Stitch distinguished an open slot from a filled one.

If it used opacity or brightness alone, that needs fixing rather than copying: the distinction
fails in glare and for low-vision users, in the dark bar this app is designed for. It must also
carry shape or fill. See the accessibility baseline in `../../DESIGN.md`.
