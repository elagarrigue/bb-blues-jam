---
name: Nocturna Blues Jam
colors:
  surface: '#111318'
  surface-dim: '#111318'
  surface-bright: '#37393f'
  surface-container-lowest: '#0c0e13'
  surface-container-low: '#1a1b21'
  surface-container: '#1e1f25'
  surface-container-high: '#282a2f'
  surface-container-highest: '#33353a'
  on-surface: '#e2e2e9'
  on-surface-variant: '#d6c4ac'
  inverse-surface: '#e2e2e9'
  inverse-on-surface: '#2e3036'
  outline: '#9e8e78'
  outline-variant: '#514532'
  surface-tint: '#ffba38'
  primary: '#ffd79b'
  on-primary: '#432c00'
  primary-container: '#ffb300'
  on-primary-container: '#6b4900'
  inverse-primary: '#7e5700'
  secondary: '#bdc6e2'
  on-secondary: '#273046'
  secondary-container: '#3d465d'
  on-secondary-container: '#acb5d0'
  tertiary: '#cedff9'
  on-tertiary: '#213145'
  tertiary-container: '#b2c3dc'
  on-tertiary-container: '#405066'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#ffdeac'
  primary-fixed-dim: '#ffba38'
  on-primary-fixed: '#281900'
  on-primary-fixed-variant: '#604100'
  secondary-fixed: '#d9e2ff'
  secondary-fixed-dim: '#bdc6e2'
  on-secondary-fixed: '#121b30'
  on-secondary-fixed-variant: '#3d465d'
  tertiary-fixed: '#d3e4fe'
  tertiary-fixed-dim: '#b7c8e1'
  on-tertiary-fixed: '#0b1c30'
  on-tertiary-fixed-variant: '#38485d'
  background: '#111318'
  on-background: '#e2e2e9'
  surface-variant: '#33353a'
typography:
  headline-lg:
    fontFamily: Barlow Condensed
    fontSize: 36px
    fontWeight: '700'
    lineHeight: 40px
  headline-lg-mobile:
    fontFamily: Barlow Condensed
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 32px
  headline-md:
    fontFamily: Barlow Condensed
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 28px
  headline-sm:
    fontFamily: Barlow Condensed
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 24px
  body-lg:
    fontFamily: Chivo
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-md:
    fontFamily: Chivo
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Chivo
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
  label-lg:
    fontFamily: Chivo
    fontSize: 14px
    fontWeight: '700'
    lineHeight: 18px
  label-md:
    fontFamily: Chivo
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
  label-sm:
    fontFamily: Chivo
    fontSize: 10px
    fontWeight: '700'
    lineHeight: 12px
  display-key:
    fontFamily: Barlow Condensed
    fontSize: 44px
    fontWeight: '800'
    lineHeight: 44px
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  gutter: 1rem
  margin: 1rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 1rem
  space-lg: 1.5rem
  space-xl: 2rem
---

## Brand & Style

The design system embodies the sensory environment of a subterranean Rioplatense blues cellar in Bahía Blanca: beer-stained wood, low-wattage vintage filament bulbs, tube amplifier hum, and stage smoke. It serves local musicians, session players, and blues aficionados managing and tracking live monthly jam line-ups on the fly under heavy bar darkness.

The aesthetic blends **Dark Industrial Minimalism** with **Nightclub Functional Tactility**:
- Strictly dark mode: UI surfaces mimic heavy iron-clad stage racks, dark acoustic treatment, and deep night indigo shadows.
- Visual hierarchy driven by stark luminescence: amber filament glow punctures deep blue shadows strictly to telegraph vital performance state (open slots, key signatures, active stages).
- Non-corporate, direct, gritty yet legible: no generic SaaS gradients or polite decorative fluff. Every pixel is calibrated for one-handed operation in dim lighting while balancing an instrument or a drink.

## Colors

The palette is engineered specifically for low-light legibility and strict cognitive load reduction in bar environments.

### Canvas & Surface Architecture
- **Base Background (`#090B10`)**: Deep void indigo. Used for screen backgrounds, canvas baselines, and root viewports.
- **Surface Level 1 (`#0F131C`)**: Slightly lifted substrate for app bars, bottom sheets, and secondary grouped containers.
- **Surface Level 2 (`#181E2B`)**: Primary module backgrounds, list row baselines, and resting state items.
- **Surface Level 3 / Card Elevated (`#202738`)**: Active interactive cards, song item rows, instrument line-up containers.
- **Surface High Contrast (`#2A3349`)**: Selected row states, modal cards, and pressed visual feedback.
- **Structural Outlines (`#343F59`)**: Low-contrast borders (1px) used to partition cards and inputs cleanly without introducing optical glare.

### Functional Amber Core
- **Primary Accent (`#FFB300`)**: Neon amber tube-amp glow. Strictly reserved for primary CTAs ("Anotarme en la Jam", "Confirmar"), active slots remaining, and published status.
- **Key & Dynamic Highlight (`#FFC107`)**: Luminous gold used for musical keys (`E7`, `A`, `G`), high-priority state indicators, and active filter selections.
- **Amber Deep Pressed (`#FFA000`)**: Touch state interaction for buttons and pressed badges.

### Supporting States
- **Occupied & Neutralized (`#64748B`)**: Muted slate for taken slots, completed stages, and inactive instruments.
- **Secondary Data Text (`#94A3B8`)**: High-contrast secondary text for musician names, role subtags, and session rules.
- **White Pure (`#F8FAFC`)**: High-contrast text on deep indigo surfaces for song names and stage headers.

## Typography

Typographic hierarchy balances condensed stage impact with crisp technical readouts.

- **Headlines & Tonality (`Barlow Condensed`)**: Dense, punchy, vintage gig-poster character. The condensed proportions permit extended track titles ("Born Under a Bad Sign", "Stormy Monday") to sit comfortably without wrapping prematurely on mobile viewports.
- **Body & Labels (`Chivo`)**: Sharp, modern grotesque with open apertures that retain high contrast even when phone brightness is lowered inside a dim venue.
- **Musical Key Token (`display-key`)**: Sized at 44px extra-bold condensed uppercase with amber contrast to guarantee at-a-glance reading from a stage stand or bar table.

## Layout & Spacing

The layout utilizes an Android-first single-column fluid structure that guarantees tap targets remain comfortable during dynamic club conditions.

### Breakpoints & Adaptive Strategy
- **Mobile Handheld (320px - 480px)**: Primary target. Fluid single-column layout with fixed 16px screen-edge margins. Song lineups, instrument slot arrays, and navigation maintain a bottom-anchored reach envelope.
- **Tablet / Stage Stand (600px - 840px)**: Reflows into a 2-column dashboard layout (Active Jam Lineup on the left 7 columns, Lineup Roster and Setlist Queue on the right 5 columns) with 24px margins.

### Spacing Scale Rules
- Elements within the same logical data unit (e.g., musical key badge adjacent to song title) use `space-xs` (4px) or `space-sm` (8px).
- Instrument slot pill stacks and metadata groupings utilize `space-md` (16px).
- Separate song entries and session blocks are separated by `space-lg` (24px) for distinct optical separation without requiring heavy dividers.

## Elevation & Depth

To uphold the dark subterranean venue atmosphere, elevation is expressed strictly through **tonal layering** and **tactile structural outlines**, never through standard fuzzy drop shadows which muddy dark UI screens.

1. **Surface 0 (Background)**: `#090B10` — Infinite stage backdrop.
2. **Surface 1 (Structural Shells & Nav)**: `#0F131C` with an upper border of `1px solid #343F59`.
3. **Surface 2 (Resting Cards & Song Rows)**: `#181E2B` with a perimeter stroke of `1px solid #343F59`.
4. **Surface 3 (Interactive / Hovered Cards)**: `#202738` with an illuminated `1px solid #FFB300` border when active or containing open slots.
5. **Amber Atmospheric Bloom**: Exclusively for key callouts and active slots. A subtle inner-tinted border glow: `box-shadow: 0 0 12px rgba(255, 179, 0, 0.15)`.

## Shapes

The design system implements a **Soft Industrial (`roundedness: 1`)** geometry.
- Base interactive components (slots, buttons, input fields, badges) use 4px (`rounded`) border radii, echoing rugged rack-mounted audio gear, flight cases, and vintage guitar stompboxes.
- Structural parent cards and bottom sheets step up to 8px (`rounded-lg`) to soften boundary collisions on OLED displays while maintaining an unapologetic, mechanical punch.
- Circular treatment is applied strictly to instrumental glyph icons and avatar markers to break horizontal rhythm.

## Components

### 1. Song Cards & Jam Lineup Rows
- **Container**: Surface `#202738`, border 1px `#343F59`, border radius 8px.
- **Layout**: Two-column flex header. Left contains the Song Title (`headline-md`, White) and Artist reference (`body-sm`, `#94A3B8`). Right holds the **Key Signature Badge**.
- **Body**: Holds the dynamic Instrument Strip.

### 2. Giant Tonality Badge (Key Signature)
- Sized 56px wide by 48px high.
- Background: `#0F131C` surrounded by a prominent 1.5px border of `#FFB300`.
- Text: Displays key (e.g. `A`, `E7`, `C#m`) rendered in `display-key` using `#FFB300`.

### 3. Instrument Strip & Slot Chips
- Glazed horizontal flex container displaying standard blues slots: Guitarra 1, Guitarra 2, Bajo, Batería, Voz, Armónica, Teclados.
- **Slot Status: Available (Cupo Libre)**:
  - Border: 1px dashed `#FFB300`.
  - Fill: `rgba(255, 179, 0, 0.08)`.
  - Content: Instrument vector icon + text "Libre" in `#FFB300` (`label-md`). Pulsing dot indicator.
- **Slot Status: Occupied (Cupo Cubierto)**:
  - Border: 1px solid `#343F59`.
  - Fill: `#181E2B`.
  - Content: Instrument glyph in `#64748B` + Musician's alias in `#94A3B8`.

### 4. Primary & Secondary CTA Buttons
- **Primary CTA ("Anotarme en la Jam")**:
  - Background `#FFB300`, text `#090B10` (`label-lg`), minimum height 48px, 4px border radius.
  - Active press state: `#FFA000`.
- **Secondary / Cancel Button**:
  - Background transparent, border 1px solid `#343F59`, text `#94A3B8`.

### 5. Filter Chips
- Height: 36px. Border radius: 4px.
- Unselected: `#181E2B`, border 1px `#343F59`, text `#94A3B8`.
- Active: `#2A3349`, border 1px solid `#FFB300`, text `#FFB300`, accompanied by a numeric amber counter badge representing available spots for that filter.

### 6. Android Material 3 Bottom Navigation Bar
- Fixed at bottom. Background `#0F131C`, top border 1px `#343F59`.
- 3 Tabs: **Próxima Jam**, **Anteriores**, **Info & Bar**.
- Inactive items: Glyph and label in `#64748B`.
- Active item: Active pill indicator `rgba(255, 179, 0, 0.12)` with glyph and label highlighted in `#FFB300`.