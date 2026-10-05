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
  keyDisplay:
    fontFamily: Barlow Condensed
    fontSize: 96sp
    lineHeight: 96sp
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

`keyDisplay` (96sp, line height 96sp, ExtraBold; `song-detail-screen`, K1 approved 4 October
2026) is used only by the song detail, where the key is the screen's main element and is read from
arm's length on stage. It is the largest style in the app; rows keep `key` (44sp).

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

- **Open slot** — amber (`slotOpen`) text on a 15% amber fill, a small **static** dot, and the
  label `GTR: LIBRE` in uppercase. The Stitch export pulses the dot; the app does not (decided 2
  October 2026, `instrument-strip-component`): with real data every slot can be open, an infinite
  animation per chip redraws every frame on a phone held all night, and perpetual motion beside
  content conflicts with WCAG 2.2.2. The pulse adds no information.
- **Filled slot** — `textMuted` text at full alpha on a `slotFilled` fill, a `check` glyph, and the
  label `Gtr: Tincho` carrying the musician's name. Not Stitch's `on-surface-variant/60` text, which
  measures 4.36:1, below AA.
- **Extra participant ("Otros")** — after all slot chips, in their `Otros` order: no fill,
  `textMuted` text, never amber and never `slotFilled`, and a leading `+` instead of the dot or the
  check, as part of the label `+ saxo: Juan` (the instrument as the admin typed it). It is never a
  slot and never counts as open (D-18).

Chips are not interactive (no click, no 48dp target: filtering is the filter bar's job). Each is one
screen-reader node: "Guitarra: libre", "Guitarra: Tincho", "Otros: saxo, Juan". Slot chips follow
the Sheet's column order, not open-first, so strips are comparable down the list; guitars are not
numbered; long names are cut with "…" and kept whole in the description. Glyph sizes derive from
the caption's font size, so they scale with the system font. The component is `InstrumentStrip` in
`:core:ui` (`com.bbbjam.core.ui.strip`).

Two signals separate open from filled without colour or brightness: the **glyph** (dot versus
check) and the **wording** (`LIBRE` in uppercase versus a name). The fills differ in hue only: in
greyscale the 15% amber fill and `slotFilled` come out almost the same grey, so the earlier claim of
"three signals: fill, glyph, and wording" overstated it. An extra adds a third look (no fill, `+`).
Contrast, measured by `ContrastTest`: amber on its composited fill 7.03:1, `textMuted` on
`slotFilled` 7.22:1, an extra's `textMuted` on the row surface 10.10:1.

The cost is width: chips take far more room than 16dp icons, so the strip wraps to a second line on
a busy song. Accepted, because a musician reading `VOZ: LIBRE` needs no legend, whereas an icon
strip does.

Note what the chips give away for free: a filled slot showing the musician's name answers "who is
playing this?" without expanding the row.

### Song row, expanded

Artist name, then **open slots first**, presented as available, then filled slots below with the
musician's name and instrument, then the extra participants. Actionable content goes on top.
Decided 3 October 2026 (`song-row-expansion`):

- **Header-only toggle.** Tapping the row's header (position, title, key, and a `textMuted` chevron
  that points down collapsed and up expanded) expands or collapses it; a tap on the panel does
  nothing, so a stray touch never collapses a row and future admin taps on slots never fight the
  row's click. Several rows may be expanded at once.
- **The panel replaces the strip.** Expanded, the header shows the artist (`body`, `textMuted`)
  instead of the strip; the panel is a superset of the strip, and showing both would repeat every
  slot on screen and for screen readers.
- **Sections:** `CUPOS LIBRES`, `CUPOS CUBIERTOS`, `OTROS` (caption, `textMuted`, uppercase, marked
  as headings), each drawn only when non-empty. With at least one open slot, a caption hint follows
  the open lines ("Para tocar, anotate en la jam: la organización te suma a un tema."); with none,
  "No quedan cupos libres." replaces the open section.
- **Lines** are full width and compact: the strip's glyph and fill for the same kind, the full
  instrument name, then the detail — `LIBRE` in `slotOpen`, a musician's name in `text` (9.52:1 on
  `slotFilled`), an extra's name in `textMuted` with `+ saxo` as its instrument. Long names wrap to
  two lines, then "…". Each line is one screen-reader node with the strip's description.
- **Open slots look available, not tappable, in the musician view**: no click, no ripple, no role,
  no "Pedir cupo" (self-signup is out of scope). A control that looks tappable and does nothing is a
  false affordance. Lines have no 48dp minimum because nothing in the panel is a touch target. Open
  slots become tappable only in the admin view (`admin-assign-musician`, which decides how its
  targets reach 48dp).
- **Motion:** the row animates its own height; the tapped header and everything above it stay
  still, the rows below slide. No auto-scroll.
- **Screen readers:** the header is one button whose state is "expandido" / "contraído" and whose
  action is "ver los cupos" / "ocultar los cupos"; the panel's headings and lines follow as separate
  nodes, so each slot is spoken once in either state.

The component is `LineupPanel` in `:core:ui` (`com.bbbjam.core.ui.lineup`), with `ExpandIndicator`
for the chevron. A line's instrument text is cut with "…" at 160dp (`LineupPanelDefaults
.INSTRUMENT_MAX_WIDTH`), so a long free-text extra instrument cannot squeeze the name.

Under the panel, an expanded row ends with **`Ver detalle del tema`**: a full-width text action
(`body`, `text`, underlined like the other secondary text actions, at least 48dp tall, a button for
screen readers), never amber. It opens the song detail (`song-detail-screen`).

### Song detail

Decided 4 October 2026 (`song-detail-screen`; N1, K1, G1, C1, R1, K2):

- **Full screen** over the tabs, with no tab bar and no transition (motion is `bottom-navigation`'s).
  A back arrow at the top start (`text`, never amber, 48dp target, described "Volver") and the
  system back both return to Próxima jam with the same tab, expanded rows, filter and scroll.
- **Order:** back arrow; title (`h1`, `text`, wraps); artist (`body`, `textMuted`) only when there
  is one; the key block — `TONALIDAD` (caption, `textMuted`, uppercase) over the key in `keyDisplay`,
  colour `key`, one screen-reader node "Tonalidad Bm"; then the lineup on a `surface` block with
  `rounded.md`.
- **Lineup grouped by instrument** (G1): the note ("No quedan cupos libres.") or the hint first,
  then one uppercase caption heading per instrument in the Sheet's column order (an instrument with
  no slot has no group) with its lines, then `OTROS` and the extras. Within a group, **open first**,
  then filled. This is the detail's one exception to "open above filled, always": a filled Guitarra
  group sits above an open Bajo slot. Extras are never merged into a group, even "guitarra" typed as
  free text. A slot line draws its glyph and detail only (the heading names the instrument); an
  extra line draws `+ saxo` and the name. Not interactive, compact lines, amber only inside
  `:core:ui`.
- **States:** while the local read runs, only the back arrow (no skeleton); a song that is no longer
  in the list, or a setlist that is not shown, is the empty block "Este tema ya no está en la
  lista" / "Puede que la organización haya cambiado la lista. Volvé a la próxima jam para ver la
  actual." Never an error, and no staleness notice (the list shows it).
- **Not shown** (D-20, D-09): tempo, tags, difficulty, songsterr, artwork, the jam's date or venue.

The components are `InstrumentGroups` (`com.bbbjam.core.ui.lineup`) and `BackButton`
(`com.bbbjam.core.ui.nav`) in `:core:ui`.

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

Behaviour (`instrument-filter-chips`, approved 4 October 2026):

- **Multi-select with OR.** Each instrument chip toggles on its own; a song is shown when it has an
  open slot for **any** selected instrument. Only slots count: an extra in `Otros` never makes a song
  match, and a song with every slot filled shows only under `Todos`.
- **`Todos`** is checked exactly when no instrument is selected; tapping it clears the selection.
  Counts stay per instrument whatever is selected. A chip with count 0 stays tappable.
- **Above the chips**, the heading `Filtrá por cupo libre` (caption, muted, a heading for screen
  readers). **Below them**, while something is selected: the count line ("2 de 13 temas con cupo
  libre para bajo"; several names join in chip order with ", " and a final " o ": "para guitarra,
  bajo o voz"), or, when nothing matches, the no-results block: "Ningún tema tiene cupo libre para
  armónica." and the text action "Ver todos los temas" (underlined, 48dp tall), which clears. No
  bar at all on a published setlist with no songs: that is the empty state, not no-results.
- **Layout.** The chips wrap (2 rows at 393dp, 3 at 360dp; never a horizontal scroll); each chip is
  at least 48dp tall, `rounded.lg`, label in body and count in caption. Selected: `activeFilter`
  fill with `onActiveFilter` content; unselected: `surfaceRaised` with `textMuted`. The bar is the
  list's second item and scrolls with it (not sticky).
- **Screen readers.** Each chip is one checkbox node (`Role.Checkbox`, checked = selected) with a
  description ("Bajo: 2 temas con cupo libre", "Todos los temas: 13"); the count line and the
  no-results message are polite live regions.
- **State.** The selection belongs to the musician, not to the jam: it survives refreshes, rotation
  and a new upcoming jam; it is lost on relaunch and on the temporary tab switch.

The component is `InstrumentFilterBar` in `:core:ui` (`com.bbbjam.core.ui.filter`); amber
`activeFilter` is read only there.

### Status badge

**Borrador** or **Publicada**. Published is amber; draft is muted.

### Admin controls

Layered onto the same screens — never a separate app, never a rearranged layout. Controls are added,
not substituted: a publish action prominent while in draft, an add-song button, a drag handle per
row, tappable open slots for assignment, and a clear action on filled slots.

## Core Screens

1. **Próxima jam, musician view** — the most important screen in the app
2. **Próxima jam, admin view** — same screen plus controls
3. **Song detail** — key displayed very large as the main element, and the full lineup grouped by
   instrument. No tags, tempo, difficulty or tab button for now (D-20)
4. **Past jams list** — reverse chronological; date, venue, song count, and a hook such as the first
   few titles. As built (`past-jams-list`, 5 October 2026): the title `Jams anteriores` (`h1`,
   `text`, a heading, drawn in every state), then one row per past jam, newest first, spaced `sm`.
   A row is a `surface` card (`shapes.md`, padding `md` × `sm`): the date with the year
   (`Sábado 25 de julio de 2026`, `songTitle`), then the venue (`body`, one line, ellipsis) with the
   count at the end (`13 temas` / `1 tema`), then the hook — the first three titles in position
   order, `, `-separated, then `y 10 más` when there are more (`body`, two lines, ellipsis). Archive
   colours (`PastJamsDefaults`): date, venue and count `textMuted`, hook `archive` (5.39:1 on
   `surface`). **No amber**: the list shows no keys. A past draft is listed with
   `La lista de esta jam no se publicó.` instead of count and hook; an unreadable list says
   `No se pudo leer la lista de esta jam.`, a list with no songs `Esta jam no tiene temas cargados.`
   (never `0 temas`). Rows are one merged, non-clickable node each until `past-jam-detail` makes them
   open the jam. States use the shared components: skeleton (`Cargando las jams anteriores`), error
   `No pudimos cargar las jams anteriores`, empty `Todavía no hay jams anteriores` /
   `Después de cada jam, su lista queda guardada acá para que veas qué se tocó.`, and the staleness
   notice above the rows. Reached through the middle tab, `Anteriores`.
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

How they are drawn (`list-states`, 4 October 2026). Four shared components in `:core:ui`, package
`com.bbbjam.core.ui.state`, each taking only its UiModel, so `past-jams-list` draws the same states:

- **Loading** — `SkeletonList`: a header placeholder (three bars) and five row placeholders the size
  of a collapsed song row (`surfaceRaised` bars on `surface` rows, at least 64dp, no dp literal).
  **Static**: no shimmer or pulse (battery, and WCAG 2.2.2 for moving content). A screen reader
  reads the whole list as one node ("Cargando la próxima jam"). Shown while nothing was ever fetched
  and no read has failed, or while a retry runs; never "no jam" before a first read.
- **Error** — `ListErrorBlock`: only when nothing is cached and the read failed. Title
  (`songTitle`), message (`body`, `textMuted`; one for offline, one for any other failure, never the
  failure's kind or detail), then a full-width `Reintentar` button, at least 48dp, in the
  `primaryAction` amber (read only inside `ListStateDefaults`). At the top of the screen, not
  centred. Not a Material `Button`.
- **Offline** — `StalenessNotice` above the cached data (above the header), on `surfaceRaised`:
  `Sin conexión` (or `No se pudo actualizar`), `Mostrando lo guardado hace 3 horas.`, and an
  underlined text action `Reintentar` (not amber: the data is the content). While the retry runs the
  detail reads `Actualizando…` (a polite live region) and the action is hidden. The notice is drawn
  **only when the latest refresh failed**; data older than 30 minutes with no failure draws none,
  because opening the screen already refreshes it. The age is computed when the data changes, not
  ticked while the screen stays open.
- **Empty** — `EmptyStateBlock`: a concrete title and an invitation (`Todavía no hay fecha` for no
  upcoming jam; `Todavía no hay temas` for a published setlist with no song, with no filter bar).
  No illustration.

Retry is a read: it re-subscribes to the jams and calls `JamsRepository.refresh()`; it writes
nothing. Previews: `NextJamStatesPreview.kt` (one per state) and the component previews in
`:core:ui`; they compile in the gate but are not rendered by it.

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
- How the strip degrades when a lineup is unusually large (for example four guitars). Wrapping is
  the current answer; confirm it stays legible.
- Drag-to-reorder versus explicit move actions, given that 48dp targets and dragging conflict on a
  dense list used one-handed.
