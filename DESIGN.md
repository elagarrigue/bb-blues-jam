---
name: BB Blues Jam
description: Dark-only Android app with a near-black indigo base and a neon amber accent reserved strictly for what the user can act on. Neon light in a dark bar.
designAssets:
  sourceOfTruth:
    - path: bb-blues-jam-design-prompt.md
      role: Full design brief, per-screen requirements, and reading-priority rules
    - path: docs/design/
      role: Stitch export — screens in screens/, token source in export/
      status: in progress
  priorInspiration:
    - description: Earlier interactive mockup, dark indigo with neon amber
      status: accepted direction, not a rendered asset in this repo
  generatedConcepts: []
colors:
  background: "#111318"
  surface: "#1A1B21"
  surfaceRaised: "#1E1F25"
  primary: "#FFB300"
  onPrimary: "#281900"
  text: "#E2E2E9"
  textMuted: "#D6C4AC"
  border: "#514532"
  slotOpen: "#FFB300"
  slotFilled: "#33353A"
  archive: "#9E8E78"
  error: "#FFB4AB"
typography:
  h1:
    fontFamily: Barlow Condensed
    fontSize: 28sp
    fontWeight: 700
  songTitle:
    fontFamily: Barlow Condensed
    fontSize: 24sp
    fontWeight: 600
  key:
    fontFamily: Barlow Condensed
    fontSize: 44sp
    fontWeight: 800
  body:
    fontFamily: Chivo
    fontSize: 16sp
  caption:
    fontFamily: Chivo
    fontSize: 12sp
rounded:
  sm: 4dp
  md: 8dp
  lg: 12dp
spacing:
  xs: 4dp
  sm: 8dp
  md: 16dp
  lg: 24dp
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.onPrimary}"
    rounded: "{rounded.md}"
    minHeight: 48dp
  chip-filter:
    backgroundColor: "{colors.surfaceRaised}"
    textColor: "{colors.textMuted}"
    activeBackgroundColor: "{colors.primary}"
    activeTextColor: "{colors.onPrimary}"
    rounded: "{rounded.lg}"
    minHeight: 48dp
  song-row:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.md}"
    minHeight: 56dp
  badge-draft:
    backgroundColor: "{colors.surfaceRaised}"
    textColor: "{colors.textMuted}"
  badge-published:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.onPrimary}"
---

# Design Direction

## Overview

Persistent visual direction for implementation agents. Terms are defined in `CONTEXT.md`; product
rules are in `docs/build-brief.md`. Interface copy is Rioplatense Spanish using *vos*, never *tú*
(D-12). Written guidance and the tokens above are authoritative when any rendered asset disagrees.

## Existing Design Assets

- **`bb-blues-jam-design-prompt.md`** — the complete design brief: screen-by-screen requirements,
  the slot model as it affects layout, reading priority, and design rules. **Source of truth.**
- **Prior interactive mockup** — established the dark indigo plus neon amber direction. Accepted as
  direction; not present in this repo as a file.
- **Stitch export** — being generated. Prompts are in section 5 of `bb-blues-jam-bitacora.md`;
  `docs/design/README.md` says where each export form goes and what it is good for. PNGs are
  visual reference; the code export is the source of real token values; the markup itself is
  discarded, since this app is Compose and the export is HTML and Tailwind.

The hex values in the front matter were reconciled against the Stitch export on 19 September 2026
and are no longer a proposal. Background, surfaces, border and amber come from what Stitch actually
produced; `textMuted`, `slotFilled`, `archive` and `error` are still derived, because the export
described them in words rather than values.

Headings and the key use **Barlow Condensed**, named by the export and consistent with the
vintage-marquee direction. Body and data keep a neutral legible sans.

Generated copy is not authoritative: Stitch tends to write English or invent wording, while all UI
copy is Rioplatense Spanish using *vos* (D-12).

## Generated Concept Images

None from `imagegen`, deliberately: Stitch is the generation path, and a parallel set of concepts
would compete with it as a reference.

Stitch screens are catalogued in `docs/design/README.md`, which also fixes the file naming so a
screen is identifiable without opening it.

## Product Feel

A small tool for a community of 20-60 people who meet once a month, not a commercial product and not
a streaming app. Close and nightclub-like, never corporate. Neon light in a dark bar.

## Colors

Dark only; there is no light variant to design or maintain. The background is a very dark
near-black indigo, with slightly lighter surface layers separating content.

**Amber is not decoration.** It is reserved for what the user needs right now:

- open slots
- the key
- the primary action
- the published state
- the active filter chip

Everything else is text, muted text, or surface. If everything glows, nothing stands out — let the
amber breathe. A filled slot is specifically *not* amber: it is information, not an opportunity.

Contrast must be real. No grey on grey; the app is read in a dark bar and on stage.

## Typography

Two families: a condensed sans with character for headings, and a neutral, highly legible sans for
data and lists. Song titles and keys must be readable at a glance in near-darkness, which makes the
key's weight and size a functional requirement rather than a stylistic one.

## Layout

Three tabs in a bottom navigation bar: **Próxima jam**, **Anteriores**, **Info**.

The next-jam screen is opened 90% of the time and must answer "where can I play?" without the user
expanding anything:

1. Header — date, venue name and address, and time remaining in natural language ("en 5 días",
   "esta noche")
2. Filter chips by instrument, with a clear-filter affordance and a count of matching songs when a
   filter is active
3. The song list, collapsed by default

Expanding happens in place, and several rows may be expanded at once. Expansion must not displace
the rest of the list disorientingly — design the transition.

## Shapes

Rounded corners throughout per the `rounded` tokens. Rows and chips read as soft surfaces rather
than hard cards; the visual separation comes from surface elevation, not from heavy borders.

## Components

### Song row, collapsed

One line where possible: position number, title, **key** (always visible even collapsed), and the
instrument strip.

### Instrument strip

The component the main screen depends on, and the Stitch export resolved it as **labelled chips
rather than bare icons**. That is a better answer than the original icon-strip idea and is adopted:

- **Open slot** — amber text on a 15% amber fill, a small pulsing dot, and the label `GTR: LIBRE`
  in uppercase.
- **Filled slot** — muted text on a neutral surface fill, a `check` glyph, and the label
  `Gtr: Tincho` carrying the musician's name.

Three signals separate the two states: fill, glyph, and wording. None of them is brightness alone,
so the distinction survives glare, low vision and a greyscale screenshot. That was the open
accessibility question and it is now closed.

The cost is width: chips take far more room than 16dp icons, so the strip wraps to a second line on
a busy song. Accepted, because a musician reading `VOZ: LIBRE` needs no legend, whereas an icon
strip does.

Note what the chips give away for free: a filled slot showing the musician's name answers "who is
playing this?" without expanding the row.

### Song row, expanded

Artist name, then **open slots first**, presented as available and tappable, then filled slots below
with the musician's name and instrument. Actionable content goes on top.

### Jam summary counter

In the header, above the filter bar: the number of musicians signed up and the number of open slots
across the whole jam ("32 anotados · 18 cupos libres"), the second in amber. Not in the original
brief; adopted from the export because it answers "is there room tonight at all?" before any
scrolling.

### Filter chip

Per instrument: Guitarra, Bajo, Batería, Voz, Armónica, Teclados. Active state uses amber.

Each chip carries a count of songs with an open slot for that instrument (`Guitarra 4`, `Bajo 1`),
plus a `Todos` chip with the total. This makes the filter bar an answer in itself, not only a
control — a bass player reads `Bajo 1` and already knows the shape of their night.

### Status badge

**Borrador** or **Publicada**. Published is amber; draft is muted.

### Admin controls

Layered onto the same screens — never a separate app, never a rearranged layout. Controls are added,
not substituted: a publish action prominent while in draft, an add-song button, a drag handle per
row, tappable open slots for assignment, and a clear action on filled slots.

## Core Screens

1. **Próxima jam, musician view** — the most important screen in the app
2. **Próxima jam, admin view** — same screen plus controls
3. **Song detail** — key displayed very large as the main element; tags, tempo, full lineup grouped
   by instrument, and a button opening the tab in the browser
4. **Past jams list** — reverse chronological; date, venue, song count, and a hook such as the first
   few titles
5. **Past jam detail** — same structure, read-only, muted archive treatment, amber only on keys, no
   notion of open slots
6. **Info** — who organizes (Bahía Blanca Blues), what the jam is and that it is monthly, the
   Hideaway radio program, social links, how to join, and a discreet admin entry point. No venue:
   it can change, so it belongs to each jam, shown in the next-jam header (D-19)
7. **Admin login** — a single passphrase field and a button; no registration, no recovery, no email;
   error state included
8. **Assign musician to a slot** — bottom sheet; the instrument is already determined by the slot
   and shown as a header, with a name field suggesting musicians who have played before

## Required States

Five, each distinct:

- **Loading** — skeleton rows, not a centered spinner
- **Empty** — a concrete invitation, never "No hay nada todavía"
- **Filter with no results** — distinct from empty; name the active filter and offer to clear it
- **Error** — message plus a retry button
- **Offline** — show cached data with a staleness indicator

A sixth case is product-specific and easily missed: **an unpublished setlist** shows the musician
the date and venue plus a message that the list is being assembled. That is not the empty state and
must not look like one.

## Responsive Baseline

Phone, portrait. No tablet layout and no landscape-specific design for the MVP. The list must work
without horizontal scrolling at the narrowest supported width, which constrains how wide the
instrument strip can grow.

## Accessibility Baseline

- Touch targets at least 48dp — the app is used standing, in low light, one-handed.
- Real contrast against the near-black background; verify amber-on-dark and muted-text-on-surface.
- **The instrument strip must not rely on brightness alone.** Dimmed versus lit is a brightness
  distinction, which is exactly what fails for low-vision users and in glare. Pair it with shape,
  fill, or an accessible label per icon so open versus filled survives without color or luminance
  perception.
- Content descriptions on every icon-only control.
- No information conveyed by color alone — amber always co-occurs with text or position.

## Dos and Don'ts

**Do**

- Keep the key visible in the collapsed row. It is three characters and the most-sought datum.
- Put open slots above filled ones, always.
- Add admin controls without moving anything else.
- Design the empty, error, offline, no-results, and unpublished cases as first-class screens.

**Do not**

- Use amber for decoration.
- Design two separate apps for the two roles.
- Add a light theme, onboarding, or a welcome screen.
- Use stock photography — minimal illustration or none.
- Hide actions behind gestures with no visible alternative.
- Design self-signup, in-app tablature, profiles, chat, notifications, or an AI assistant screen.
  These are out of scope for the MVP.

## Open Design Questions

- `textMuted`, `slotFilled`, `archive` and `error` are still derived rather than measured: the
  Stitch export described them in words ("muted slate grey") without giving values.
- Chips carry an instrument abbreviation (`Gtr`, `Bajo`, `Bat`, `Voz`, `Arm`, `Tec`). Decide
  whether a glyph is still wanted alongside the text, which would cost width the strip does not
  have to spare.
- How the strip degrades when a lineup is unusually large (for example four guitars). Wrapping is
  the current answer; confirm it stays legible.
- Drag-to-reorder versus explicit move actions, given that 48dp targets and dragging conflict on a
  dense list used one-handed.
