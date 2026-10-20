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
  adminBackground: "#2A1218"
  adminSurface: "#331A21"
  adminSurfaceRaised: "#371E25"
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
  chip-status:
    backgroundColor: "{colors.surfaceRaised}"
    textColor: "{colors.textMuted}"
    selectedTextColor: "{colors.text}"
    publishedBackgroundColor: "{colors.published}"
    publishedTextColor: "{colors.onPublished}"
    rounded: "{rounded.lg}"
    # pending chip (jam-status-chip-loading): surfaceRaised fill, textMuted label, 2dp textMuted outline pulsing 0.3 to 1
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

- **Band logo** — Logo blanco BBB.png is the supplied transparent white mark. The app reuses it
  without edits as the shared header in Próxima jam and Info.
- **Launcher icon** — app/src/main/res/drawable-nodpi/launcher_brand.png adapts the guitar and
  checklist artwork from JAM de BLUES Guitar Checklist.png with a safe margin for Android masking.

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

The launcher icon is a generated adaptation of the supplied guitar-and-checklist artwork. It is an
app asset, not a screen concept or a replacement for the written design direction.

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
- the published state (the Publicada badge, and the selected Publicada chip of the Jams tab)
- the active filter chip

Everything else is text, muted text, or surface. If everything glows, nothing stands out — let the
amber breathe. A filled slot is specifically *not* amber: it is information, not an opportunity.

Contrast must be real. No grey on grey; the app is read in a dark bar and on stage.

## Admin mode

Decided 12 October 2026 (`admin-mode-highlight`): the admin's background, surface and raised
surface switch app-wide to a clearly tinted deep-wine ramp (`adminBackground` `#2A1218`,
`adminSurface` `#331A21`, `adminSurfaceRaised` `#371E25`) whenever `AdminSession.observeIsAdmin()`
is true, replacing the musician `background`/`surface`/`surfaceRaised` everywhere, including in
every Material component. The hue (~346°) is deliberately far from the musician palette's cool
indigo hue (~223°) so the two are distinguishable without reading any text, while the luminance
stays close enough that every existing text/role contrast pairing in `ContrastTest` still passes at
essentially the same ratio.

A fixed "MODO ADMIN" strip is drawn once, directly under the status bar and above the tab bar and
every full-screen route's content (the song detail, a past jam's detail, the catalog/key pickers,
the assign-musician screen and the admin login) — never inside a scrolling container, so it never
scrolls away. Its fill is `adminSurfaceRaised` (reused, no fourth token) and its text is `text`; it
is a single static text node, read once by a screen reader, not a live region. No new amber role:
"we are in admin mode" is not one of amber's five uses, and the five existing roles keep their
value unchanged in admin mode. Logging out reverts both the background and the banner in the same
recomposition that drops every other admin control, with no separate state to keep in sync.

The Android window background resource (`bluesjam_window_background`, painted before Kotlin runs)
is unaffected: it always mirrors the musician `background` exactly, since the admin flag cannot be
known before the first Compose frame.

## Typography

Two families: a condensed sans with character for headings, and a neutral, highly legible sans for
data and lists. Song titles and keys must be readable at a glance in near-darkness, which makes the
key's weight and size a functional requirement rather than a stylistic one.

`keyDisplay` (96sp, line height 96sp, ExtraBold; `song-detail-screen`, K1 approved 4 October
2026) is used only by the song detail, where the key is the screen's main element and is read from
arm's length on stage. It is the largest style in the app; rows keep `key` (44sp).

## Layout

Three tabs in a bottom navigation bar: **Próxima jam**, **Jams**, **Info**. While the admin session is active the bar also has **Temas** (between Jams and Info, `admin-catalog-tab`) and **Asistente** (phase 2, last), so a musician sees three tabs and the admin five; Info moves one place right for the admin only.

### Bottom bar

Decided 5 October 2026 (`bottom-navigation`; I1, M1, D1):

- **Order and labels:** `Próxima jam`, `Jams` (called `Anteriores` until `admin-jams-tab`, D-27), `Info`, left to right, each with icon and
  label (always shown); in admin mode `Temas` sits between `Jams` and `Info` and `Asistente` is last. Icons from `material-icons-core` (I1): `List` (auto-mirrored), `DateRange`,
  outlined `Info`, `PlayArrow` for `Temas` (the core set has no music icon); the icon has no description, the label names the item.
- **Colors:** bar container `surface`; selected icon and label `text` on an indicator
  `surfaceRaised` (12.75:1); unselected icon and label `textMuted`. **No amber**: the selected tab
  is a location, not an action ("amber only for what the user can act on"); the export's amber
  active tab is not adopted. Label in `caption`, one centred line, cut with "…" when it does not fit
  at a large font scale (`TabBarDefaults.LABEL_MAX_LINES`), so it never wraps out of its item.
- **Size and semantics:** Material 3 `NavigationBar` (80dp, 24dp icons); each item is a
  screen-reader tab with its selected state and well over 48dp tall. The bar applies the
  navigation-bar inset itself.
- **Per-tab state:** each tab keeps its scroll, and Próxima jam its filter, across
  tab switches, the song detail, rotation and process death. Tapping the selected tab does nothing.
  Back from Jams or Info goes to Próxima jam; back from Próxima jam leaves the app.
- **Status bar:** the status-bar area is a solid `background` band above every tab and the song
  detail; content scrolls below it, never under the clock.
- **Motion (M1):** tab switch, a 150 ms crossfade; song detail, slides in from the end in 250 ms
  over the tabs (which stay drawn under it) and out to the end on back. No other motion, except the
  assistant's loading dots (`assistant-streaming`), shown only while a request runs, stopped by `Cancelar`
  (the same dots sit beside the summary of a run that loads a proposal into a jam, a user request, shown only
  while it runs and stopped by `Detener`; they carry the summary as their one polite node), and
  the pending status chip's outline (`jam-status-chip-loading`, a second exception requested by the user: it
  replaces a text line, is bounded to one chip while one write runs, stops on the server's answer, uses
  `textMuted`). Both are drawn static when the system animator scale is 0 (WCAG 2.2.2).
- **Deeplinks (D1):** none; still deferred. D-13 is met by `SetlistRepository` functions (`action-contract-registry`, whose registry was later removed by `remove-action-registry`).

The component is `TabBar` in `:core:ui` (`com.bbbjam.core.ui.nav`); its colors come only from
`TabBarDefaults`.

The next-jam screen is opened 90% of the time and must answer "where can I play?" without the user
opening anything:

1. Header — date, venue name and address, and time remaining in natural language ("en 5 días",
   "esta noche")
2. Filter chips by instrument, with a clear-filter affordance and a count of matching songs when a
   filter is active
3. The song list, always collapsed (D-07)

Rows never expand (`song-detail-replaces-row-expansion`, 11 October 2026): a tap on a row opens the
song detail, and back returns to the same tab, filter and scroll.

## Shapes

Rounded corners throughout per the `rounded` tokens. Rows and chips read as soft surfaces rather
than hard cards; the visual separation comes from surface elevation, not from heavy borders.

## Components

### Song row, collapsed

One line where possible: position number, title, **key** (always visible), and the instrument strip.
A long title (or any title at a large font scale) wraps to a second line before it is cut with "…";
the key stays vertically centred on it (`ui-polish-pass`, U8). There is no chevron: the row is
always collapsed (`song-detail-replaces-row-expansion`, 11 October 2026).

- **Tap opens the song detail.** The header (position, title, key, `Guardando…` when it applies,
  the strip) is one button node at least 56dp tall; its action is "ver el detalle del tema" (TalkBack:
  "Presioná dos veces para ver el detalle del tema"), with no state description. It still reads the
  position, title, key description and the strip's chips.
- **Admin compact bar** (P3; icons as of `admin-row-icon-actions`, 12 October 2026, overriding the
  text-action rule below): under the header, outside its click, one row with an up-arrow and a
  down-arrow 48dp icon button grouped at the start (`KeyboardArrowUp`/`Down`, content descriptions
  "subir un lugar"/"bajar un lugar", the edge one disabled, muted, no click but still drawn and
  announced disabled), a flexible gap, then a trash icon button (`Delete`, `error`, content
  description "Quitar de la lista") at the end. Tapping the bar never opens the detail. Tapping the
  trash icon opens a modal confirmation dialog over the bar (see "Removing a song"); while the
  removal is sent, `Quitando…` replaces the bar. The `Posición x de n` line is no longer drawn (the
  position label is in the header). Song-level admin controls live in the song detail.

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
- **Extra participant ("Otros")** — after all slot chips, in their `Otros` order: the same
  `slotFilled` fill as a filled slot, `textMuted` text, never amber, no check glyph, and a leading
  `+` as part of the label `+ saxo: Juan` (the instrument as the admin typed it). It is never a
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
"three signals: fill, glyph, and wording" overstated it. An extra shares the filled slot's fill and text colour; the leading `+` and the missing check separate it.
Contrast, measured by `ContrastTest`: amber on its composited fill 7.03:1, `textMuted` on
`slotFilled` 7.22:1, an extra's `textMuted` on `slotFilled` is the same 7.22:1.

The cost is width: chips take far more room than 16dp icons, so the strip wraps to a second line on
a busy song. Accepted, because a musician reading `VOZ: LIBRE` needs no legend, whereas an icon
strip does.

Note what the chips give away for free: a filled slot showing the musician's name answers "who is
playing this?" without opening the song.

### Lineup panel

Rows no longer expand (`song-detail-replaces-row-expansion`, 11 October 2026): the lineup panel the
expanded row drew (`song-row-expansion`, 3 October 2026) is now drawn by the song detail, for
musicians and the admin alike (P1). Its rules stay:

- **Order:** **open slots first**, presented as available, then filled slots with the musician's
  name and instrument, then the extra participants. Actionable content goes on top.
- **Sections:** `CUPOS LIBRES`, `CUPOS CUBIERTOS`, `OTROS` (caption, `textMuted`, uppercase, marked
  as headings), each drawn only when non-empty. With at least one open slot, a caption hint follows
  the open lines ("Para tocar, anotate en la jam: la organización te suma a un tema."); with none,
  "No quedan cupos libres." replaces the open section. The hint addresses musicians, so the admin's
  panel omits it. Lines inside a section are `xs` apart; a heading after anything already drawn gets
  an extra `sm` above it, so the groups read as groups.
- **Lines** are full width and compact: the strip's glyph and fill for the same kind, the full
  instrument name, then the detail — `LIBRE` in `slotOpen`, a musician's name in `text` (9.52:1 on
  `slotFilled`), an extra's name in `text` too with `+ saxo` as its instrument; since 12 October 2026 (`extras-dialog-and-lines`) an extra's line has the filled slot's fill and check glyph, and only the `+` prefix, the `Otros:` description and the `OTROS` heading mark it as an extra. Since 12 October 2026 (`strip-extra-chip-filled`) the collapsed row's strip chip for an extra also has the filled fill and differs from the panel line only by lacking the check. Long names wrap to
  two lines, then "…". Each line is one screen-reader node with the strip's description. An admin
  line with an action (`Anotar`, `Liberar`, `Quitar`) stacks the instrument above the detail, so
  the detail gets the full width and a large font never breaks `LIBRE` or a name mid-word; the
  action stays at the line's end.
- **Open slots look available, not tappable, in the musician view**: no click, no ripple, no role,
  no "Pedir cupo" (self-signup is out of scope). A control that looks tappable and does nothing is a
  false affordance. Lines have no 48dp minimum because nothing in the musician's panel is a touch
  target; the admin's line actions are 48dp.

The component is `LineupPanel` in `:core:ui` (`com.bbbjam.core.ui.lineup`). A line's instrument text
is cut with "…" at 160dp (`LineupPanelDefaults.INSTRUMENT_MAX_WIDTH`), so a long free-text extra
instrument cannot squeeze the name. (`ExpandIndicator`, the old row chevron, is deleted.)

### Song detail

Decided 4 October 2026 (`song-detail-screen`; N1, K1, C1, R1, K2) and reshaped 11 October 2026
(`song-detail-replaces-row-expansion`; P1, P2, P4): the detail now holds everything the expanded row
had.

- **Full screen** over the tabs, with no tab bar; it slides in from the end over the tabs in 250 ms
  and out to the end on back (M1, `bottom-navigation`). Opened by a tap on a Próxima jam row.
  A back arrow at the top start (`text`, never amber, 48dp target, described "Volver") and the
  system back both return to Próxima jam with the same tab, filter and scroll.
- **Order:** back arrow; title (`h1`, `text`, wraps, a heading); artist (`body`, `textMuted`) only
  when there is one; for the admin, `Guardando…` (`caption`, `textMuted`, polite) while a write of
  the song is sending; the key block — `TONALIDAD` (caption, `textMuted`, uppercase) over the key in
  `keyDisplay`, colour `key`, one screen-reader node "Tonalidad Bm" (for the admin, the pending key
  while one is saving). In admin mode the key block itself is a 48dp-minimum `Role.Button` with the
  click label `Cambiar tonalidad`; tapping it opens the key picker. There is no separate action under
  the key. Then the lineup on a `surface` block with `rounded.md`
  and padding `md`: the **Lineup panel** (above), with an admin `+ Otro` / `Agregar` action row at the
  end of `CUPOS LIBRES` (it opens the existing extras dialog and does not create a slot); then, for
  the admin, the always-visible `FORMACIÓN` editor after the panel; then,
  for the admin, the failure cards of this song only (`Cerrar` dismisses them here and on the list).
- **Lineup (P1):** the same open-first panel for musicians and the admin, replacing the earlier
  grouping by instrument (G1, `InstrumentGroups`, deleted). The admin's lines carry `Anotar` (opens
  the assign screen above the detail), `Liberar` and an extra's `Quitar`; the admin's panel has no
  hint.
- **Identity (P2):** the detail is named by the song id, so a move or a removal renumbering the list
  while it is open never switches it to another song; a repeated id falls back to the position.
- **States:** while the local read runs, and for everyone until the admin flag is read too, only the
  back arrow (no skeleton), so the admin never sees a draft's song as not found; a song that is no
  longer in the list (also live, when a refresh or a confirmed removal drops it), or a setlist that
  is not shown, is the empty block "Este tema ya no está en la lista" / "Puede que la organización
  haya cambiado la lista. Volvé a la próxima jam para ver la actual." Never an error, and no
  staleness notice, pull to refresh or live polling (the list has them).
- **Not shown** (D-20, D-09): tempo, tags, difficulty, songsterr, artwork, the jam's date or venue.

The components are `LineupPanel` (`com.bbbjam.core.ui.lineup`) and `BackButton`
(`com.bbbjam.core.ui.nav`) in `:core:ui`; the admin controls are the feature's own
(`AdminControls.kt`, `LineupEditorControl.kt`, `ExtraParticipantControls.kt`).

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
  and a new upcoming jam; it is lost on relaunch, and kept across tab switches and the song detail.

The component is `InstrumentFilterBar` in `:core:ui` (`com.bbbjam.core.ui.filter`); amber
`activeFilter` is read only there.

### Status badge

**Borrador**, **Creada** or **Publicada**. Published is amber; draft and created are muted. Próxima jam keeps this
read-only badge. The Jams tab's cards do not draw it: they draw the status chips below.

### Status chips (`chip-status`, `jam-status-chips`)

One row of three single-select chips, `Borrador` `Creada` `Publicada`, on every future card of the admin's Jams tab. The
selected chip is the jam's confirmed status. Each chip is at least 48dp tall and wide (`shapes.lg`, `body` label), in a
wrapping row that never scrolls horizontally. Shape always differs from brightness: an unselected chip is `surfaceRaised`
with a hairline `border` outline and a `textMuted` label; a selected `Borrador`/`Creada` chip is `surfaceRaised` with a
hairline `text` outline, a leading check glyph and a `text` label; the selected `Publicada` chip is amber (`published` /
`onPublished`, the published-state role, no outline) with the check. A disabled chip that is not selected has no fill and no
outline, a `textMuted` label; a disabled selected chip keeps its selected look and loses only the tap. The row has
`selectableGroup` and each chip is one `Role.RadioButton` node described `Estado: Creada`, so a screen reader announces the
state and the selection. Tapping the selected chip does nothing.

While a status change or a publication sends (`jam-status-chip-loading`) no text is drawn: all three chips are disabled, the
selection stays on the confirmed status and the chip being applied (the requested status, `Publicada` for a publication) is
the pending chip. It is `surfaceRaised` with a 2dp `textMuted` outline (never amber, also for `Publicada`) whose alpha pulses
from 0.3 to 1 over 1200 ms (the loading dots' timing, drawn in the draw phase), and a `textMuted` label with no glyph; it is the
only chip with an outline while the others are disabled. At animator scale 0 the outline is constant at alpha 0.6 and no
transition exists. The pending node is a polite live region described `Cambiando el estado a Creada…` (or `Publicando la
lista…`) instead of `Estado: Creada`; it is disabled, not selected. The animation stops when the server answers.

### Admin controls

On Próxima jam the admin sees the jam status read-only since `admin-jams-tab`: the shared badge, its note and the hint
`El estado y las fechas se manejan en la pestaña Jams.` (`caption`, `textMuted`). Layered onto the same screens — never a separate app, never a rearranged layout. Controls are added,
not substituted: a publish action prominent while in draft, an add-song button, a drag handle per
row, tappable open slots for assignment, and a clear action on filled slots.

As built (`admin-add-song-to-setlist`, 6 October 2026, V1 and C1 approved by the user), colours only
through `AdminControlsDefaults`, **no amber**:

- **Draft badge**: under the header, the muted `badge-draft` `BORRADOR` (`caption`, `textMuted` on
  `surfaceRaised`, `shapes.sm`) and the note `Los músicos todavía no ven esta jam.` (`caption`,
  `textMuted`). Only for a draft jam (D-26: musicians do not see it at all).
- **Created badge** (`jam-status-created`): the same muted `badge-draft` style, text `CREADA`, with the
  note `Los músicos ven la fecha, pero todavía no la lista.` No amber. Only for a created jam; the
  musician sees the draft card ("La lista se está armando") for it. A past draft jam is not listed in
  Anteriores for anyone, and its detail is the "ya no está en el archivo" state.
- After the rows, in this order: **pending rows** (a `surface` card, `shapes.md`, at least 48dp,
  title `songTitle` and `Agregando…` `body`, all `textMuted`, read politely), **failure cards** (a
  `surface` card: `No se pudo agregar «…»` in `error`, the reason in `textMuted`, and an underlined
  `Cerrar` in `text`, a 48dp target; it stays until closed) and the **`Agregar tema`** button
  (full width, at least 48dp, `surfaceRaised` with `text`, `shapes.md`, `Role.Button`). The
  instrument filter never hides the pending rows or the cards.
- The admin's empty list: `Todavía no hay temas` / `Agregá el primero desde el catálogo.`, then the
  button. With no upcoming jam: the no-date block plus the `caption` line `Para armar la lista,
  cargá la fecha en la pestaña Jams de la planilla.` and no button.
- **Catalog picker** (`AddSongScreen`, colours in `AddSongDefaults`): full screen over the tabs, a
  48dp `Volver`, the title `Agregar tema` (`h1`, a heading), an outlined field `Buscar por título o
  artista` with explicit token colours (no amber, as the admin login), then the catalog sorted by
  title ignoring case and accents, matching title or artist the same way. A row is a `surface` card
  (`shapes.md`, at least 48dp): the title (`songTitle`, two lines) with the default key at the end
  (`songTitle` size, the amber `key` role, read as `Tonalidad A`), the artist under it (`body`,
  `textMuted`); one merged node, `Role.Button`, click label `agregar a la lista`. A song already in
  the list is all `textMuted`, adds `Ya está en la lista` (`caption`) and is not clickable. States:
  skeleton `Cargando el catálogo`, error `No pudimos cargar el catálogo`, empty `El catálogo está
  vacío` / `Cargá temas en la pestaña Catalogo de la planilla.`, and `Ningún tema coincide con «…».`
  A pick closes the picker at once; the song shows on Próxima jam as a pending row.
- **Removing a song** (`admin-remove-song-from-setlist`, U1 approved by the user, 6 October 2026;
  **icon trigger and modal confirmation as of `admin-row-icon-actions`, 12 October 2026 — both
  reverse earlier lines in this entry, kept below for history**): admin only, last in the row's
  compact bar under its header (`song-detail-replaces-row-expansion`; it used to end an expanded
  row's panel). Colours in `AdminControlsDefaults.removal()`/`removalDialog()`, **no amber**. Idle:
  a trash icon button, content description `Quitar de la lista` (`error`, `IconRowActionIcon.Delete`,
  48dp, `Role.Button`) — no longer the "labelled text action" the 6 October entry described; the
  user asked for a trash icon instead, and font-scale wrapping of the text action was the reason
  (`song-detail-replaces-row-expansion`'s open follow-up). Tapping it changes nothing anywhere; the
  bar stays drawn and a Material 3 `AlertDialog` opens over it (`surfaceRaised` container, `text`
  title, `textMuted` details, never amber) — **not** the inline block the 6 October entry specified:
  the user asked for "un pop up con ok cancelar" on 12 October 2026. Title: the prompt `¿Quitar
  «Crossroads» de la lista?` (`text`). Body, only when it applies: `Se borra también el músico
  anotado.` / `Se borran también los n músicos anotados.` (filled slots plus extras) and `La lista
  está publicada: los músicos van a dejar de verlo.` (`textMuted`). Buttons: `OK` (`error`, calls
  the same confirm handler) and `Cancelar` (`text`, dismisses); back or a tap outside the dialog
  equals `Cancelar` (`AlertDialog.onDismissRequest`). Still no swipe and no undo. While sending,
  `Quitando…` (`body`, `textMuted`, a polite live region) replaces the bar; the row disappears only
  when the server confirms. A failure draws the row as before plus a card after the rows, `No se
  pudo quitar «…»` with the reason and `Cerrar`, as the add-song cards.
- **Setting a key** (`admin-set-key`, V1, O1 and C2 approved by the user, 6 October 2026): admin
  only, on the song detail (`song-detail-replaces-row-expansion`; it used to be in an expanded row):
  tapping the large key block opens the key picker. The block is a 48dp-minimum `Role.Button` with
  the click label `Cambiar tonalidad`; there is no separate text button. The key remains in the amber
  `key` role, and the control adds no other amber.
  **The optimistic rule**: after a pick the row and the detail show the new key at once in the amber
  `key` role, with `Guardando…` (`caption`, `textMuted`, a polite live region) under the title, until
  the server answers. Only a change still sending is drawn; the latest of two wins. On
  success the key stays and the line goes; on failure the row and the detail **revert** to the
  confirmed key and a card (after the rows, and on that song's detail) says `No se pudo cambiar la tonalidad de «…»` with the reason and `Cerrar`, as
  the other cards. Musicians never see a pending key.
- **Key picker, as built** (`SetKeyScreen`, colours in `SetKeyDefaults`): full screen over the tabs,
  scrollable; a 48dp `Volver`, the title `Cambiar tonalidad` (`h1`, a heading), the song title
  (`songTitle`, two lines, `text`), `TONALIDAD ACTUAL` (`caption`, `textMuted`) over the current key
  (`key` typography, the amber `key` role, read `Tonalidad A`; the pending key when one is saving, so
  it agrees with the row), then `MAYORES` and `MENORES` (`caption`, `textMuted`, headings), each a
  4-column grid of 3 rows, chromatic from C: C, Db, D, Eb, E, F, F#, G, Ab, A, Bb, B and Cm, C#m, Dm,
  Ebm, Em, Fm, F#m, Gm, G#m, Am, Bbm, Bm. A cell is `surfaceRaised` with the key in `songTitle`,
  `text`, `shapes.md`, at least 48dp, `Role.Button`, read `Tonalidad Bb` with the click label `elegir
  esta tonalidad`. The cell spelled exactly as the current key is `surface` with a hairline `border`
  outline, the key in the amber `key` role and the caption `actual` (`textMuted`), and is not
  clickable; a non-canonical key (`A#`) marks no cell. Amber is never on a selectable cell. A tap
  closes the picker at once. Gone state: `Este tema ya no está en la lista` / `Volvé a la próxima
  jam para ver la actual.`
- **Assigning a musician** (`admin-assign-musician`): each open line of the admin's song detail adds
  a 48dp `Anotar` action in the `slotOpen` role; filled lines, `Otros`, and musician rows stay
  read-only. It opens a full-screen scrollable `Anotar músico` flow with a 48dp `Volver`, song
  title, fixed `CUPO` instrument, `Nombre` field with Done IME, validation caption, and sections
  `ESTA JAM` and `JAMS ANTERIORES`. Suggestion rows and submit are at least 48dp; no instrument
  selector is drawn. Submit closes immediately; only the admin row shows `Guardando…` while the
  server confirms (the row and the detail). Failure reopens the slot and adds a dismissible card. Focus and button colors use
  neutral tokens; amber remains reserved for open-slot affordance and key. Gone state:
  `Ese cupo ya no está libre` / `Volvé a la próxima jam para ver la lista actual.`
- **Clearing a filled slot** (`admin-clear-slot`): each filled line of the admin's song detail adds a
  48dp `Liberar` action with an accessible instrument, musician and action description; musicians
  see no control. While sending, the admin projection moves that exact original ordinal into the
  open section as `Quitando…` and suppresses actions on that slot. A failure restores the confirmed
  musician and adds a dismissible `No se pudo liberar el cupo` card; stale targets say `Ese cupo ya
  no está ocupado. Actualizá la lista para ver cómo quedó.`. The immediate action
  uses the established neutral admin/action treatment, not amber.
- **Adjusting a lineup** (`admin-adjust-lineup`): on the admin song detail, the inline `FORMACIÓN`
  editor is always visible after the lineup panel (caption heading; no `Cambiar formación` button).
  It has one `surface` row per
  instrument, full name in `body`/`text`, count (`3 cupos`, `2 cupos`, `1 cupo`, `No va en este tema`)
  in `caption`/`textMuted`. The editor's lines are inset `spacing.md`, like the panel. Minus/plus are 48dp squares, `shapes.md`, glyphs in `songTitle`, `Role.Button`, with
  explicit instrument descriptions. An enabled one has a `surfaceRaised` fill, a hairline `border`
  outline (the fill alone is about 1.05:1 on `surface`) and a `text` glyph; a disabled one has no
  fill, no outline and a `textMuted` glyph, is not clickable and has disabled semantics, so the
  state shows in shape, not brightness alone. The plus is enabled up to each instrument's own ceiling (`lineup-grow-any-instrument`, `lineup-max-per-instrument`): three for Guitarra, Bajo, Voz and Armónica, one for Batería and Teclados, so those two have the plus disabled from the start; at the ceiling it is disabled like the minus at zero. A jam tab never widened to the three-column layout answers a plus past its columns with the dismissible failure card (`Esta hoja todavía no tiene columnas para ese cupo…`). Removing a filled-only instrument is disabled
  with `Tiene músico anotado. Liberá el cupo antes de sacarlo.` beneath it. There is no editor opener or closer; the count
  controls use `AdminControlsDefaults.lineupEditor()` and stay neutral.
  Sending counts update the strip, panel and filters at once for the admin only. One muted
  `Guardando…` line covers key and lineup together; failure reverts and leaves a persistent card.
- **Reordering songs** (`admin-reorder-songs`): `Subir`/`Bajar` are the first two actions of each
  admin row's compact bar, grouped at its start before the trash icon at the end
  (`song-detail-replaces-row-expansion`; icon buttons as of `admin-row-icon-actions`, 12 October
  2026; the `Posición x de n` line is no longer drawn). 48dp each, up/down arrow icons with content
  descriptions "subir un lugar"/"bajar un lugar" (unchanged wording from the earlier text action);
  the edge action is disabled with a muted icon tint, no click, and disabled semantics — still
  drawn, never hidden.
  The admin row moves optimistically with a polite `Guardando…` status and remains under the
  thumb. Failure restores the confirmed position and adds a dismissible card. Musicians see the
  confirmed order only. Colors use neutral admin action tokens, not amber.
- Text selection is non-amber app-wide (`BluesJamTheme` provides `text` handles and `text` at 40%).

## Core Screens

1. **Próxima jam, musician view** — the most important screen in the app
2. **Próxima jam, admin view** — same screen plus controls: the draft's songs, the status block,
   pending adds, failure cards, `Agregar tema` and each row's compact bar (up/down arrow icon
   buttons, a trash icon button for `Quitar de la lista` with its modal confirmation dialog); a row
   tap opens the song detail, where the song-level controls are (**Admin controls**)
3. **Song detail** — key displayed very large as the main element, and the full lineup panel (open
   slots first, then filled, then `OTROS`); for the admin, `Cambiar tonalidad`, the slot actions, an
   `+ Otro` / `Agregar` row at the end of `CUPOS LIBRES`, and the always-visible formation editor. No tags, tempo,
   difficulty or tab button for now (D-20)
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
   (never `0 temas`). Rows are one merged node each. A row with songs opens the jam
   (`past-jam-detail`): one clickable node, `Role.Button`, click label `ver la lista de temas`, at
   least 48dp, ripple only (no chevron, colours unchanged); a row with a line instead of songs is not
   clickable. States use the shared components: skeleton (`Cargando las jams anteriores`), error
   `No pudimos cargar las jams anteriores`, empty `Todavía no hay jams anteriores` /
   `Después de cada jam, su lista queda guardada acá para que veas qué se tocó.`, and the staleness
   notice above the rows. Reached through the middle tab, `Jams` (for the admin the tab also lists the jams to come, see "Jams tab (`admin-jams-tab`)"; a musician sees exactly this list, titled `Jams anteriores`).
5. **Past jam detail** — read-only, muted archive treatment, amber only on keys, no notion of open
   slots. As built (`past-jam-detail`, 5 October 2026), **the song list only**: the user's direction
   "para las jams pasadas no importa quién tocó, solo la lista de temas" replaced the lineup strip and
   the expansion of the design prompt §5. Full screen over the tabs (no bar), from a tap on an
   Anteriores row. A back button (`Volver`, 48dp), then the header: the date with the year
   (`Sábado 25 de julio de 2026`, `h1`, a heading), the venue (`body`, one line, ellipsis) with the
   count at the end (`13 temas`). Then one row per song in position order, spaced `sm`: a `surface`
   card (`shapes.md`, padding `md` × `sm`) with the position (`01`, `songTitle`), the title
   (`songTitle`, two lines, ellipsis) and the key at the end (`key` typography, the amber `key` role,
   read as `Tonalidad B`), the artist under them (`body`, drawn only when not blank). Archive colours
   (`PastJamDetailDefaults`): header, position and title `textMuted`, artist `archive`, the
   dropped-rows note `textMuted` (`caption`, `Faltan 2 temas: no se pudieron leer.`). Each row is one
   merged node, not clickable: no strip, no expansion, no lineup, no slot, no filter, no admin
   control. A past draft, an unreadable list or a list with no songs keeps the header (without a
   count) and shows the Anteriores line instead of rows. Loading draws only the back button (screen
   reader: `Cargando la jam`); a jam no longer in the archive shows `Esta jam ya no está en el
   archivo` / `Puede que la organización la haya cambiado. Volvé a Jams para ver las jams
   guardadas.` No staleness notice, as in the song detail.
6. **Info** — who organizes (Bahía Blanca Blues), what the jam is and that it is monthly, the
   Hideaway radio program, social links, how to join, and no visible admin entry. No venue:
   it can change, so it belongs to each jam, shown in the next-jam header (D-19). Info draws no admin
   text (`admin-entry-logo-taps`, 17 October 2026). Five taps on Info's band logo, each at most 1.5 s
   after the previous one, open the login (musician) or leave admin mode at once (admin, no
   confirmation); a longer pause restarts the count. No ripple and no feedback beyond the `MODO ADMIN`
   banner and palette going away. Próxima jam's logo is inert. The logo keeps its image semantics, so
   TalkBack does not offer the gesture (accepted tradeoff).
7. **Admin login** — a single passphrase field and a button; no registration, no recovery, no email;
   error state included. As built (`admin-passphrase-login`, 5 October 2026): full screen over the
   tabs (no bar), from the hidden logo gesture on Info. A back button (`Volver`, 48dp), the title `Entrar como admin` (`h1`, a
   heading), then a Material 3 outlined field labelled `Frase de acceso` with explicit token colours
   (`AdminLoginDefaults`: container `surface`, text and focused border and cursor `text`, label
   `textMuted`, unfocused border `border`, error border and supporting text `error`; **no amber**),
   masked by default, password keyboard, no autocorrect, `Done` submits, and a 48dp
   `Mostrar`/`Ocultar` text toggle at its end (`caption`, `textMuted`). The error shows under the
   field as supporting text, read politely: `La frase de acceso no es correcta.`,
   `No hay conexión. Para entrar como admin necesitás internet.` or
   `No se pudo verificar la frase de acceso. Probá de nuevo en un rato.`; typing clears it. Then the
   full-width `Entrar` button (`button-primary`: `primaryAction`/`onPrimaryAction`, `rounded.md`,
   at least 48dp), disabled (`surfaceRaised`/`textMuted`) while the field is blank, labelled
   `Verificando…` (disabled) while the server is asked. The only amber on the screen is that
   button. The content sits above the keyboard (`imePadding`). Success closes the screen back to
   Info.
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

How it is drawn (`unpublished-setlist-state`, 5 October 2026, V1 and C1 approved by the user): under
the header, a **draft card** — a `surface` fill (the empty block has none) with `shapes.md` and
`spacing.md` padding — holding, `spacing.sm` apart, the muted `badge-draft` (`EN PREPARACIÓN`,
`caption`, `textMuted` on `surfaceRaised`, `shapes.sm`, wrapped to its content), the title `La lista
se está armando` (`songTitle`, `text`, a heading) and the message `Cuando la organización la
publique, vas a ver acá los temas, las tonalidades y los cupos libres.` (`body`, `textMuted`). No
amber: draft is muted (Status badge). No song row, key, strip or filter bar. Three text nodes, none
clickable. Colours only through `DraftSetlistDefaults` in `:feature:next-jam`. The decision is made
from the jam's status (`Jam.setlistForMusicians()`), so a draft whose songs reached the app still
shows only the card. With a failed refresh the staleness notice sits above the header, as for any
cached data.

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
  ticked while the screen stays open. If the notice appears while the list is resting at the top,
  the list scrolls so the notice is visible instead of being inserted above the visible area
  (`offline-notice-visible`, 7 October 2026).
- **Empty** — `EmptyStateBlock`: a concrete title and an invitation (`Todavía no hay fecha` for no
  upcoming jam; `Todavía no hay temas` for a published setlist with no song, with no filter bar).
  No illustration.

Retry is a read: it re-subscribes to the jams and calls `JamsRepository.refresh()`; it writes
nothing. So is the pull to refresh.

**Pull to refresh and the live window** (`live-refresh-during-jam`, 6 October 2026, L1–L4 approved).
Próxima jam and Anteriores, in every state (loading, error, empty, list), can be pulled down to read
the jams again: one pull is one read, and a pull while one runs is ignored. The indicator is Material
3's pull indicator with explicit colours, read only inside `:core:ui` `PullRefreshDefaults`: a
`surfaceRaised` disc with a `text` arc. **No amber**: refreshing is not a primary action. It spins
only for a refresh the user pulled. A pull gesture is not reachable with a screen reader, so the list
carries the custom accessibility action `Actualizar`, which does the same. No pull on the song
detail, the past jam detail, the pickers or Info (L2). While Próxima jam is visible and "now" is in
a jam's live window (30 min before its start to 4 h after, Buenos Aires; the jam that turned
historical at midnight keeps its window, L4) it refreshes every 30 s, 60 s after a failure. These
periodic refreshes are **quiet**: no indicator, the staleness notice keeps its age instead of
flashing `Actualizando…` (and its polite live region stays silent), and the error block never turns
back into the skeleton. A Retry or a pull still shows `Actualizando…` as before. No toast or
snackbar reports a refresh's outcome; the notice appears after a failure and goes after the next
success. Previews: `NextJamStatesPreview.kt` (one per state), `NextJamDraftPreview.kt` (the draft
card, fresh and offline) and the component previews in
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
- Design self-signup, in-app tablature, profiles, chat, notifications, or an AI assistant screen for musicians (phase 2 adds the admin-only Asistente tab, see "Asistente").
  These are out of scope for the MVP.

## Asistente (phase 2, `assistant-admin-tab`)

An admin-only fourth tab, **Asistente**, a chat over the setlist assistant. It follows the admin palette
automatically and writes nothing by itself (D-24); loading a proposal is the admin's confirmed action (see "Usar la propuesta: tres destinos").

- Column: title `Asistente` (`h1`, `text`, heading), a `LazyColumn` of turns (`md` apart), the composer pinned at the
  bottom above the keyboard (`imePadding`; the bar is not drawn while the keyboard is up). The list scrolls to the
  newest turn when one is added or changes state.
- Request card: `surfaceRaised`, `shapes.md`, caption `VOS` (`caption`, `textMuted`), request in `body`/`text`.
  Reply card: `surface`, same shape.
- Proposal: heading `PROPUESTA · n temas` (`caption`, `textMuted`, heading); each song one merged, non-clickable node:
  position (`songTitle`, `textMuted`), title (`songTitle`, `text`, 2 lines), key at the end (`songTitle` size, amber
  `key` role, described "Tonalidad Bm"), artist (`body`, `textMuted`), reason (`body`, `text`). Then the message,
  `PREGUNTAS` with each question as `• …`, and a muted caption for discarded ids (ids are never shown).
- Pending: `LoadingDots` (`:core:ui`, `state`): three 8dp circles, `xs` apart, `textMuted` (never amber), fading in turn
  over 1200 ms (alpha 0.3 to 1, 200 ms stagger); static at alpha 0.6 when the animator duration scale is 0. One polite
  node described "El asistente está armando la propuesta. Puede tardar unos segundos." (no visible status text), then a
  `Cancelar` underlined 48dp text action in `text`. Failed/cancelled: message in `error` / `textMuted`, `Reintentar` underlined
  48dp text action in `text`, only on the last turn.
- Streamed songs (`assistant-streaming`): while pending, songs that already arrived are listed under `PROPUESTA · n temas`
  (same rows as a proposal, no targets), with the dots below the last row; the rows are plain nodes, not live regions. A broken,
  failed or cancelled answer that streamed songs becomes `PROPUESTA INCOMPLETA · n temas` (caption `textMuted`), the same
  rows, one line (`error`, or `textMuted` for a cancel: `La respuesta se cortó antes de terminar. Esto es lo que llegó; no está
  completa.`) and `Reintentar` on the last turn. Never a target or a jam card. The list follows new songs only if it was already
  at the end.
- Composer: outlined field `Tu pedido` with the admin login's colours, up to 5 lines; full-width `Enviar` button
  (`button-primary`, disabled `surfaceRaised`/`textMuted`, label `Esperando respuesta…` while pending).
- Service status (amendment, 13 October 2026): the tab reads the gateway's status when shown. While unavailable a banner sits under
  the title: a `surfaceRaised` card (`shapes.md`), message in `body`/`text` (one polite live region) and an underlined 48dp
  `Reintentar` text action in `text`; never amber and never `error`. ServiceDown: "El asistente no está disponible en este
  momento. El servicio de IA no responde; probá más tarde."; Offline and Unauthorized reuse the failure copy. While checking, a
  static `caption`/`textMuted` line "Comprobando el servicio…" (no spinner, polite live region) sits above the field. In every
  case but Available `Enviar` is disabled, and a turn's `Reintentar` is withheld.
- Amber on this screen: only `Enviar` and the keys (Konsist allowlist `feature/assistant`).
- Not configured and the intro use `EmptyStateBlock`.

### Crear la jam (`assistant-create-jam`)

Since `assistant-proposal-targets` (C5) a proposal that carries songs **and** a jam draws this card as information only: its own
`Crear la jam` button is never drawn, `Crear una jam nueva con esta lista` (below) creates the jam. A jam-only proposal keeps
the button and dialog described here.

- **Card** under the author caption of a proposal that carries a jam: `surfaceRaised`, `shapes.md`. Heading `NUEVA JAM`
  (`caption`, `textMuted`, heading), date in words `Sábado 28 de noviembre de 2026` (`songTitle`, `text`),
  `21:00 · La Macanuda` (`body`, `text`), the held-back note (`body`, `textMuted`): `Queda en espera: la próxima jam
  sigue siendo la del sábado 31 de octubre.`
- **Button** `Crear la jam` (`Reintentar` after a failure): full-width, `surface` fill so it shows on the raised card,
  `text` content, `shapes.md`, 48dp; disabled `surface`/`textMuted`. **Not amber**; the allowlist is unchanged. Only the
  latest turn with a valid jam, and not while a turn is pending; older turns show the jam as text.
- **Blocked** (no button): a date earlier than the upcoming jam (`... la dejaría en espera. Si de verdad querés eso,
  agregala en la planilla.`), a date already taken, a past date; `body`, `textMuted`. A rejected jam shows only a muted note.
- **Dialog** (`ApplyDefaults.applyDialog()`, `surfaceRaised`, `text` actions, no amber, no `error`): `¿Crear la jam?`, lines
  `Fecha:`, `Hora:`, `Lugar:`, the draft line and, when a jam is upcoming, the held-back line; `Crear la jam` / `Cancelar`.
- **Result**: one polite live region under the card (`Creando la jam…`, `Jam creada como borrador.`, or
  `No se pudo crear: ...` in `error`). `Usar esta lista` on a jam turn stays blocked until the jam is the upcoming one.

## Open Design Questions

- `textMuted`, `slotFilled`, `archive` and `error` are still derived rather than measured: the
  Stitch export described them in words ("muted slate grey") without giving values.
- How the strip degrades when a lineup is unusually large (for example four guitars). Wrapping is
  the current answer; confirm it stays legible.
- Drag-to-reorder versus explicit move actions, given that 48dp targets and dragging conflict on a
  dense list used one-handed.

### Jams tab (`admin-jams-tab`)

The `Nueva jam` and edit dialogs are the shared `core.ui.jamform.JamFormDialog` (colours in its internal `JamFormDefaults`, copy
in `JamFormText`); the assistant's `Crear una jam nueva con esta lista` form uses the same component.

The middle tab (`Jams`, D-27). A musician sees the past jams only, as before (title `Jams anteriores`). The admin sees the
title `Jams` (`h1`) and two sections: `PRÓXIMAS` and `ANTERIORES` (`caption`, `textMuted`, heading semantics), the second
with the unchanged past rows. `PRÓXIMAS` starts with a full-width `Nueva jam` button (`surfaceRaised` fill, `text`
content, 48dp, never amber), then the result of the last creation, then one card per future jam, latest first (user request,
20 October 2026: the tab reads newest to oldest, like `ANTERIORES`), so the upcoming jam is the last card, BORRADOR included (D-26); with none, the line `No hay ninguna jam programada.`. A card is on `surface`
(`shapes.md`): the date `Sábado 31 de octubre · 21:00` (`songTitle`, `text`, with the two icons at its end), the venue and `12 temas` / `1 tema` /
`Sin temas todavía` / `No se pudo leer la lista` (`body`, `textMuted`), the shared status badge (`:core:ui`, the
"Status chips" component, below the date line) and the status note (`caption`, `textMuted`). Every future card carries the
chips and, at the end of its date line, the edit and delete icons (see the next section); only the upcoming card can be
published. A held-back card has the
line `En espera: la próxima jam es la del sábado 31 de octubre.` (`caption`, `textMuted`); a held-back CREADA or PUBLICADA
card behind a BORRADOR upcoming jam adds `Los músicos la ven como la próxima jam, porque la del sábado 31 de octubre está
en borrador.` Cards are not tappable; the list is edited in Próxima jam.

`Nueva jam` opens a modal `AlertDialog` (`surfaceRaised`): three labelled outlined fields (`Fecha` `DD/MM/AAAA`, `Hora`
`HH:MM`, `Lugar`; fecha and hora use the numeric keyboard and accept `/`, `-`, `.` and `:`, `.`), field errors in `error`
`caption`, the notes in `textMuted`, `Crear la jam` and `Cancelar` in `text` (a disabled `Crear la jam` is `textMuted`).
Hora and lugar open with those of the latest-dated jam (`21:00` and empty with none); fecha opens empty. Blocked dates are
field errors: past, already a jam (also a held-back one), or before the upcoming jam. A later date shows `Queda en espera:
la próxima jam sigue siendo la del sábado 31 de octubre.`; every creation says `Se crea como borrador: los músicos no la
ven hasta que la pases a creada o publiques la lista.` `Crear la jam` closes the dialog and shows `Creando la jam…` (polite),
then `Jam creada como borrador.`, or an assertive failure card (`No se pudo crear la jam`, the reason, `La jam no se creó.`,
`Reintentar`, `Cerrar`; with an outdated script, `Cerrar` only). The new card appears only after the server answers.

### Temas tab (admin-catalog-tab)

The admin's catalog (D-30), a fifth bar item for the admin only; a musician never sees it and a session that ends while it
is selected returns to Próxima jam. One column of `LazyColumn` items, pull to refresh like the other tabs:

- **Header:** title `Temas` (`h1`, heading); the count `128 temas` / `1 tema` (`caption`, `textMuted`); a full-width
  `Agregar tema` button (`surfaceRaised`, `text`, 48dp, `shapes.md`, never amber, `textMuted` while a write runs).
- **Notice** under the button: the sending line (`Guardando el tema…` / `Borrando el tema…`) and the result line
  (`Tema agregado.`, `Cambios guardados.`, `Tema borrado.`) are `body` `textMuted` polite live regions; a failure is the
  assertive card of the Jams tab (`surface`, title `error` `No se pudo agregar el tema` / `No se pudo guardar el tema` / `No se pudo
  borrar el tema`, the reason in `textMuted`, `El catálogo sigue como estaba.`, underlined `text` actions `Reintentar`
  (only when sending the same request again can help) and `Cerrar`).
- **Search** `Buscar por título o artista`: outlined, every colour role set from tokens (as the admin login), `textMuted`
  label. It filters title or artist ignoring case and accents; no match reads `No hay temas que coincidan con «…».`
- **Rows** (`surface`, `shapes.md`): title (`songTitle`, `text`, two lines) and artist (`body`, `textMuted`) in one merged node
  with the default key at the end (`songTitle` size, amber `key`, described `Tonalidad A`), then the edit (`text`) and delete
  (`error`) icons as 48dp `IconRowAction`s described `Editar Crossroads` / `Borrar Crossroads`, drawn disabled (`textMuted`)
  while any catalog write runs. Sorted by title then artist, ignoring case and accents. The only amber in the module is `key`.
- **Add and edit form** (Material 3 `AlertDialog`, `surfaceRaised`): title `Agregar tema` / `Editar tema`; three outlined fields
  `Título`, `Artista`, `Tonalidad` (hint `Ej.: A, Bm, F#`; not the Próxima jam key grid). Field errors are `error` `caption`:
  `Escribí el título.`, `Escribí el artista.`, `Hasta 100 caracteres.`, `No puede empezar con =, +, - ni @.`, `Tiene que tener
  alguna letra o número.`, `Usá una tonalidad como A, Bm o F#.`, and under the artist `Ya hay un tema con ese título y ese
  artista.`. `textMuted` notes: add `El tempo, las etiquetas y la dificultad se cargan en la planilla.`; edit `Las jams pasadas
  muestran los datos nuevos. La tonalidad de cada jam no cambia.` `Agregar` / `Guardar` and `Cancelar` are `text`
  (`textMuted` when disabled; `Guardar` stays disabled until something differs). Back and a tap outside equal `Cancelar`.
- **Delete dialog:** `¿Borrar «Crossroads»?` with `textMuted` `caption` lines `Robert Johnson`, `Las jams pasadas lo siguen
  mostrando.`, `No se puede deshacer.`; `Borrar el tema` in `error`, `Cancelar` in `text`. If the song is in the readable
  list of the upcoming or a held-back jam the dialog is a refusal instead, and nothing is sent: `No se puede borrar
  «Crossroads»`, `Está en la lista de la jam del sábado 25 de julio.` (two jams: `de las jams del … y del …`; more: the first
  and `de n más`), `Sacalo de esa lista antes de borrarlo.`, one `Entendido` in `text`.
- **States:** the shared skeleton, error block, empty state (`Todavía no hay temas`) and staleness notice of `list-states`.
- Tokens only (`BluesJamTheme.*`), no dp or colour literals; colours in `CatalogDefaults`.

### Editing and deleting a jam (admin-edit-delete-jam)

At the end of the date line of every future card, two 48dp icon buttons (`Role.Button`, never amber): edit (`text`) and
delete (`error`), described `Editar la jam del sábado 31 de octubre` and `Borrar la jam del sábado 31 de octubre`; delete is
absent when the setlist is not readable, so its song count is unknown. While the jam is being written the card shows one
polite line (`Guardando los cambios…` / `Borrando la jam…`, `textMuted`) and both icons are drawn disabled (`textMuted`,
no tap), as they are while a status change or publication of that jam is sending. The edit dialog is
the `Nueva jam` dialog (`surfaceRaised`, three labelled outlined fields seeded with the jam, `error` `caption` field errors,
`textMuted` notes `La lista y los músicos anotados pasan a la nueva fecha.` and `Los músicos ven el cambio cuando
actualicen la app.`, `Guardar` in `text`, `textMuted` when disabled, and `Cancelar` in `text`). An edit may not change which
jam is the upcoming one: the date shows an `error` line naming the other jam. The delete dialog is a modal `AlertDialog` like
the unpublish dialog (`surfaceRaised`, `text` title `¿Borrar la jam del sábado 31 de octubre?`, `textMuted` `caption` lines:
the song count, what musicians stop seeing, which jam is next afterwards, `No se puede deshacer.`; `Borrar la jam` in
`error`, `Cancelar` in `text`). A failure is the assertive card of the status block (`No se pudo guardar la jam` / `No se pudo
borrar la jam`, the reason, `La jam sigue como estaba.`, `Reintentar`, `Cerrar`). No amber is added; the allowlist is unchanged.

Publishing and changing the status moved here from Próxima jam, with the same visuals, onto the upcoming card:

### Publishing (`admin-publish-setlist`, as built locally)

The upcoming jam's card shows its status: muted `BORRADOR` with `Los músicos todavía no ven esta jam.` for a draft, muted `CREADA` with `Los músicos ven la fecha, pero todavía no la lista.` for a created jam; amber `PUBLICADA` with `Los músicos ya ven esta lista.` after confirmed publication. An upcoming draft or created jam with at least one song can be published from its `Publicada` chip. Tapping it opens a modal `AlertDialog` titled `¿Publicar la lista?` (`surfaceRaised`, `text` title, `textMuted` caption lines with the song count and `Después podés volver a cambiar el estado desde acá.`). `Cancelar` uses `text`; `Publicar` uses amber `primaryAction`. Back or a tap outside equals `Cancelar`; the selection does not move and chips cannot be tapped while the dialog is open. While the server writes, the selection stays on the confirmed chip, the chips are drawn disabled and the `Publicada` chip is the pending one (no text line). Only confirmed server write/read-back flips the cache and the selected chip. A persistent assertive failure card remains in this same status block with the reason, `Los músicos siguen sin ver la lista.`, `Reintentar` and `Cerrar`. `Publicada` is disabled, with a `caption`/`textMuted` line under the note, on a held-back card (`Solo la próxima jam se puede publicar.`), an empty list (`Para publicar, agregá al menos un tema en Próxima jam.`) and an unreadable one (`Para publicar, la lista se tiene que poder leer.`). Amber is limited here to the `Publicar` action and the selected `Publicada` chip.

### Changing the status (`admin-set-jam-status`)

Since `jam-status-chips` the status block is the chip row (see "Status chips"); the `Pasar a…` actions are gone. Choosing
`Borrador` or `Creada` on a BORRADOR or CREADA jam changes at once, without a dialog (reversible, no list shown). Leaving PUBLICADA
(choosing either of them) opens a modal
`AlertDialog` (`surfaceRaised`, `text` title `¿Dejar de publicar la lista?`, `textMuted` body that says what musicians stop
seeing, `error` confirm `Pasar a creada`/`Pasar a borrador`, `text` `Cancelar`; back or a tap outside is `Cancelar`).
While a change, a publication or an edit of the jam is sending, the three chips and the two icons are drawn disabled and
no text is drawn: the chip being applied is the pending chip (see Status chips) and announces `Cambiando el estado a Creada…`
(or `Publicando la lista…`); the selected chip stays on the confirmed status until the server confirms.
A failure is the same assertive card as publishing's (`No se pudo cambiar el estado`, the reason, `La jam sigue como estaba.`,
`Reintentar`, `Cerrar`); with a script that does not know `setJamStatus` the card says `El script de la planilla todavía no
sabe cambiar el estado. Actualizalo con la guía y probá de nuevo.` with `Cerrar` only. No new amber role.

### Extra participants (`admin-edit-extra-participants`)

On the admin's song detail (an admin song row until `song-detail-replaces-row-expansion`), each extra in the panel's `OTROS` section is a line with a visible `Quitar` action at its end, in `text` like `Liberar`, with an accessible name identifying the participant and instrument (`Quitar a Juan, saxo`); the extras keep their order and are not listed a second time among the row actions (`ui-polish-pass`, U4). At the end of `CUPOS LIBRES`, the `+ Otro` / `Agregar` row uses the open-slot amber fill and color, with the same body type style for both texts, and opens a modal `AlertDialog` (12 October 2026, `extras-dialog-and-lines`; `surfaceRaised` container, `text` title/`Agregar`/`Cancelar`, muted disabled `Agregar`, colours from `AdminControlsDefaults.extraDialog()`, no amber) drawn beside the list, not as a list item, with two labelled outlined fields (`Nombre`, `Instrumento`; `AdminControlsDefaults.extraField()`). Back and a tap outside act as `Cancelar`. `Agregar` is enabled only when both trimmed fields are non-blank; disabled it is muted with no underline. `Cancelar` closes and clears the form without a write. Use Rioplatense Spanish with vos, theme tokens, neutral admin action colors, and touch targets of at least 48dp. While saving, show `Guardando…`; only the admin projection receives the sending overlay. Failed writes restore the last confirmed list and use the existing dismissible failure card. Musician rows continue to render extras as read-only `Otros` chips after all slot chips. Extras do not affect lineup filters, slot counts or open-slot counts.

### Usar la propuesta: tres destinos (`assistant-proposal-targets`, supersedes "Cargar la propuesta")

- **Targets block** under the latest proposal with songs (never while a turn is pending): full-width secondary buttons
  stacked `xs` apart, in the order `Agregar a la próxima jam` (`Reintentar los que faltan` after a failed, stopped or
  aborted Agregar run), `Reemplazar la lista de la próxima jam` (`Reintentar el reemplazo`), `Crear una jam nueva con
  esta lista`. `surfaceRaised` fill, `text` content, `shapes.md`, 48dp; disabled `surface`/`textMuted`.
  **Not amber**: `Enviar` stays the only primary action; the allowlist `feature/assistant = {key, primaryAction,
  onPrimaryAction}` is unchanged. After a successful run the buttons disappear (the summary stays); after any other end they return.
- **Crear** is always offered once the jams are read. It opens the **shared jam form** (`core.ui.jamform.JamFormDialog`, the
  look of the Jams tab's `Nueva jam` dialog, also used by its edit dialog): title `Nueva jam con esta lista`, fecha empty or the
  proposal jam's, hora and lugar from the proposal jam or the latest jam, the notes `Se crea como borrador y se cargan los N
  temas en este orden.`, the draft line and, for a date behind the upcoming jam, `Queda en espera detrás de la del ...`
  (still submittable); `PAST`, `EXISTS` and `EARLIER` dates are field errors in `error` and disable `Crear y cargar N temas`.
  The form is the confirmation: no extra dialog. After a create run that made the jam but did not load everything the turn
  offers only `Reintentar los que faltan`, for that jam's date.
- **Notes** under the buttons (`body`, `textMuted`): no upcoming jam (`No hay una próxima jam: podés crear una con esta lista.`; only
  Crear) or unreadable list (only Crear), every song
  already listed, the list already equal to the proposal, the list has repeated songs.
- **Confirmation dialogs**: Material 3 `AlertDialog`, `surfaceRaised` container, title `text`, lines `textMuted`,
  confirm and `Cancelar` in `text`. Agregar adds (not `error`). Reemplazar names the jam date, the removed titles, the
  musicians lost, the songs kept and the songs added; its confirm `Reemplazar` is drawn in `error` only when it
  removes something. Colours in `ApplyDefaults.applyDialog(destructive)`. Back, outside tap and `Cancelar` write nothing.
- **Blocked note** instead of the button (no upcoming jam, unreadable list, every song already listed): `body`, `textMuted`.
- **Confirmation dialog**: Material 3 `AlertDialog`, `surfaceRaised` container, title `text`, lines `textMuted`,
  confirm (`Agregar N temas`) and `Cancelar` in `text` (it adds, it does not destroy: not `error`, not amber).
  Lines: what is added and where, existing count, songs skipped as duplicates, and the published-list warning.
  Colours in `ApplyDefaults.applyDialog()`. Back, outside tap and `Cancelar` write nothing.
- **Replace progress**: removed songs get their own lines (title `body` `textMuted`, caption `caption`: `Quitando…`,
  `Quitado`, `Sin intentar` in `textMuted`, `No se quitó: ...` in `error`); kept songs read `Ya estaba; conserva sus músicos`.
  The summary never reads `Listo` unless the latest snapshot lists exactly the proposal in order.
- **Progress**: per-song caption (`caption`) under the reason, part of the song's merged node: `Agregando…`, `Agregado`,
  `Ya estaba en la lista`, `Sin intentar` in `textMuted`; `No se agregó: ...` in `error`. One polite live region
  summary (`body`): `text` while running and for `Listo`, `error` for partial or cut runs, `textMuted` when stopped;
  `Detener` is an underlined 48dp text action in `text` while running. Never `Listo` when any song failed.
