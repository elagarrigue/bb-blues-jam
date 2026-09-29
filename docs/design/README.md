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
| Lineup is 6 slots, harmonica and keyboards sharing one | Default 7 slots, adjustable per song (D-06) | **Summary only.** The screens implement per-song lineups correctly; songs 02 and 05 show a Teclados slot. |
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

Real token values, now reconciled into `../../DESIGN.md`: background `#111318`, surfaces `#1A1B21`
and `#1E1F25`, border `#514532`, amber `#FFB300` — the export's Material 3 front matter, which the
review below settles on. (A first reconciliation took `#0C0E13` and `#282A30` from the export's
summary; the unpacked code uses neither.) And a named typeface for headings and keys,
**Barlow Condensed**.

## The export, reviewed

Unpacked 19 September 2026: twelve screens, each with `code.html` and `screen.png`, plus Stitch's
own `nocturna_blues_jam/DESIGN.md`.

### That file contradicts itself, and the code settles it

Its YAML front matter and its prose describe **different palettes**. The prose names a deep indigo
set (`#090B10`, `#181E2B`, `#202738`); the front matter declares a Material 3 theme
(`#111318`, `#1A1B21`, `#1E1F25`).

Checked against the generated HTML: the prose values appear **zero times** across all twelve
screens. The code uses the front matter throughout. Our tokens follow the code, because that is
what was actually drawn and what the PNGs show.

Worth remembering as a habit: when generated documentation disagrees with generated code, the code
is the artifact and the prose is a story told about it.

### The instrument strip came back better than specified

The brief asked for a strip of ~16dp icons, dimmed for open and lit for filled. Stitch produced
**labelled chips** instead:

| State | Fill | Glyph | Label |
|---|---|---|---|
| Open | 15% amber | pulsing dot | `GTR: LIBRE`, uppercase, amber |
| Filled | neutral surface | `check` | `Gtr: Tincho`, muted, with the musician's name |

This is the better answer and is adopted into `../../DESIGN.md`. Three independent signals separate
the states — fill, glyph and wording — so the distinction holds under glare, low vision, and in a
greyscale screenshot. **The brightness-only accessibility concern is resolved**, not by our fixing
it but by the generated design never having had it.

It costs width: chips wrap to a second line on a busy song, where icons would not. Accepted, since
a chip needs no legend and a filled chip answers "who is playing this?" without expanding the row.

### The lineup is right in the screens, and the summary was wrong about it

Reading only the first song's markup suggested Teclados had been dropped. The rendered screens say
otherwise: song 02 shows `TEC: LIBRE` and song 05 shows `TEC: LIBRE`, while song 01 genuinely has
no keyboards slot.

So the lineup **varies per song**, which is exactly D-06. Stitch got this right; its project
summary was wrong when it described a fixed six-slot lineup merging harmonica and keyboards. The
divergence table below is corrected accordingly.

Lesson worth keeping: one song's markup is a sample, not the model.

### A layout bug in the rendered header

`next-jam.png` has overlapping text in the header: the date line ("VIERNES 17 DE MAYO") collides
with the time and address beneath it. It is a generation defect, not a design choice — do not
reproduce it in Compose.

### Also present

- Fonts named and used: **Barlow Condensed** for headings and keys, **Chivo** for body and labels.
- The key badge is a real component: 48-56px, amber border, amber `display-key` text at 44px/800.
- Filter chips carry a numeric counter of available slots per instrument — a nice touch not in the
  brief, worth keeping.
- `data-instruments` attributes per song row, showing how filtering was intended to work.
- A header counter of the whole jam: "32 anotados / 18 cupos libres". Not in the brief, genuinely
  useful, and worth keeping — it answers "is there room tonight at all?" before any scrolling.
- Filter chips carry per-instrument counts (`Todos 14`, `Guitarra 4`, `Bajo 1`), which makes the
  filter bar itself an answer rather than only a control.

## Check on arrival

The instrument strip is the component the main screen depends on. When the export lands, check how
Stitch distinguished an open slot from a filled one.

If it used opacity or brightness alone, that needs fixing rather than copying: the distinction
fails in glare and for low-vision users, in the dark bar this app is designed for. It must also
carry shape or fill. See the accessibility baseline in `../../DESIGN.md`.
