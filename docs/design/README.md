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

## Check on arrival

The instrument strip is the component the main screen depends on. When the export lands, check how
Stitch distinguished an open slot from a filled one.

If it used opacity or brightness alone, that needs fixing rather than copying: the distinction
fails in glare and for low-vision users, in the dark bar this app is designed for. It must also
carry shape or fill. See the accessibility baseline in `../../DESIGN.md`.
