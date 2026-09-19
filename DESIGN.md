---
name: BB Blues Jam
description: Dark-only Android app with a near-black indigo base and a neon amber accent reserved strictly for what the user can act on. Neon light in a dark bar.
designAssets:
  sourceOfTruth:
    - path: bb-blues-jam-design-prompt.md
      role: Full design brief, per-screen requirements, and reading-priority rules
    - path: (pending) Stitch-generated screens
      role: Eight screens and five states, to be generated from the prompts in the bitácora section 5
      status: pending
  priorInspiration:
    - description: Earlier interactive mockup, dark indigo with neon amber
      status: accepted direction, not a rendered asset in this repo
  generatedConcepts: []
colors:
  background: "#0B0B14"
  surface: "#14141F"
  surfaceRaised: "#1D1D2B"
  primary: "#FFB020"
  onPrimary: "#0B0B14"
  text: "#F5F3EE"
  textMuted: "#A6A2B5"
  border: "#2A2A3A"
  slotOpen: "#FFB020"
  slotFilled: "#6E6A80"
  archive: "#8B879B"
  error: "#FF6B5A"
typography:
  h1:
    fontFamily: condensed sans with character
    fontSize: 28sp
    fontWeight: 700
  songTitle:
    fontFamily: neutral legible sans
    fontSize: 18sp
    fontWeight: 600
  key:
    fontFamily: neutral legible sans
    fontSize: 18sp
    fontWeight: 700
  body:
    fontFamily: neutral legible sans
    fontSize: 16sp
  caption:
    fontFamily: neutral legible sans
    fontSize: 13sp
rounded:
  sm: 8dp
  md: 12dp
  lg: 20dp
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
- **Stitch screens** — not yet generated. The prompts are written and live in section 5 of
  `bb-blues-jam-bitacora.md`. Once generated, save them and list them here with their authority.

The hex values in the front matter are a concrete starting palette derived from the stated
direction, not measured from the original mockup. If the mockup is recovered, reconcile against it
and update these tokens.

## Generated Concept Images

None, deliberately. Stitch is the generation path for screens, and parallel concept images would
compete with it as a reference.

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

The component the main screen depends on. One small icon per instrument in that song's lineup,
**dimmed when the slot is open and lit when it is filled**. It must fit in little space and be
understandable without a legend. This strip is what makes the collapsed list useful and the filter
legible at a glance.

Note the deliberate inversion: dimmed means available. It reads as "a space to fill", and pairs with
amber marking open slots in the expanded panel.

### Song row, expanded

Artist name, then **open slots first**, presented as available and tappable, then filled slots below
with the musician's name and instrument. Actionable content goes on top.

### Filter chip

Per instrument: Guitarra, Bajo, Batería, Voz, Armónica, Teclados. Active state uses amber.

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
6. **Info** — what the jam is, venue and directions, when it happens, social links, how to join, and
   a discreet admin entry point
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

- The exact hex values are a proposal derived from the written direction. Reconcile with the prior
  mockup if it is recovered.
- Icon set for the six instruments: source or draw. They must read at roughly 16dp.
- How the instrument strip degrades when a lineup is unusually large (for example four guitars).
- Drag-to-reorder versus explicit move actions, given that 48dp targets and dragging conflict on a
  dense list used one-handed.
