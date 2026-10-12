# Spec: strip-extra-chip-filled

Requested by the user on 12 October 2026: in the song list, the "Otros" tags should look like
another chip, with the same background as the assigned instruments. Tiny styling change inside
`:core:ui`. No new module, presenter, mutation or dependency.

## Inspected

`InstrumentStripDefaults.kt`, `LineupPanelDefaults.kt`, `InstrumentStripDefaultsTest`,
`LineupPanelDefaultsTest`, `ContrastTest`, `DESIGN.md` lines 268-285 and 310, `feature_list.json`.
Not re-read, assumed unchanged: `LineupChips.kt` and `InstrumentStrip.kt` (they draw whatever
`style(kind)` returns; the implementer must confirm that a chip with `Glyph.NONE` and a solid fill
renders with the same shape and padding as a filled slot, and that nothing special-cases
`Transparent`).

## Decisions

- **Fill**: `colors.slotFilled`, the same value as `FILLED_SLOT`. Never amber (D-17).
- **Text and glyph colour**: `colors.textMuted`, unchanged. This is already what the filled slot
  uses, so the contrast pair is the existing `muted text on a filled chip` (7.22:1).
- **Glyph**: keep `Glyph.NONE`. The label already starts with `+` (`+ saxo: Juan`), and the missing
  check is the only visual cue that separates an extra from a filled slot now that the fill is
  shared (D-18: an extra is never a slot). A check would make it a copy of a filled chip.
  Assumption: it reads fine without a glyph, since the `+` is inside the label. The device
  screenshot is the check; if it looks broken, report it rather than adding a check.
- **Panel**: `LineupPanelDefaults.style(EXTRA)` must NOT delegate to the strip again, because the
  panel line keeps the check glyph (`extras-dialog-and-lines`) and the strip chip does not. Keep
  the override, but update its KDoc: the fill and colours now coincide with the strip, and only the
  glyph differs. Do not change `detailColor`. No panel behaviour change.
- **Unchanged**: `InstrumentChipKind.EXTRA`, the mapper, `Otros: instrumento, nombre` description,
  chip order, the filter and the open-slot counts (D-18).

## Files to change

1. `core/ui/src/main/kotlin/com/bbbjam/core/ui/strip/InstrumentStripDefaults.kt`: the `EXTRA`
   branch gets `fill = colors.slotFilled`; update the KDoc ("An extra has no fill..." becomes: an
   extra shares the filled slot's fill and text, has no slot glyph, and is never amber).
2. `core/ui/src/main/kotlin/com/bbbjam/core/ui/lineup/LineupPanelDefaults.kt`: KDoc only.
3. `core/ui/src/test/kotlin/com/bbbjam/core/ui/strip/InstrumentStripDefaultsTest.kt`:
   - `an extra has no fill...` becomes: fill `slotFilled`, text and glyph colour `textMuted`,
     `Glyph.NONE`.
   - `an extra never uses amber or the slot fills` becomes `an extra is never amber`: drop the
     `slotFilled` exclusion, keep the two amber assertions (`slotOpen` and its 15% fill).
   - Add: the extra's glyph differs from the filled slot's (`NONE` vs `CHECK`), and its fill equals
     the filled slot's.
   - Remove the now-unused `Color` import if nothing else needs it.
4. `core/ui/src/test/kotlin/com/bbbjam/core/ui/lineup/LineupPanelDefaultsTest.kt`: rename `the
   strip's own extra chip keeps its no-fill look` to `the strip's extra chip shares the filled fill
   but has no check`: fill `slotFilled`, `Glyph.NONE`. Drop the unused `Color` import.
5. `core/ui/src/test/kotlin/com/bbbjam/core/ui/theme/ContrastTest.kt`: no new pair. Update the
   comment on `muted text on a filled chip` (it says an extra has no fill). Add an assertion, read
   through `InstrumentStripDefaults.style(EXTRA)`, that `textColor` on `fill` still measures 7.22
   (the style the component actually draws, as `open slot on its chip fill` does).
6. `DESIGN.md`:
   - Line 268-271, "Extra participant": replace "no fill" with "the same `slotFilled` fill as a
     filled slot, `textMuted` text, never amber, no check glyph, and a leading `+` as part of the
     label". Keep "never a slot and never counts as open (D-18)".
   - Line 283: the "extra adds a third look (no fill, `+`)" sentence becomes: an extra shares the
     filled slot's fill and text colour; the leading `+` and the missing check separate it.
   - Line 285: an extra's `textMuted` on `slotFilled` is 7.22:1 (replace the 10.10:1 on the row
     surface for the extra).
   - Line 310: replace "The collapsed row's strip chip for an extra (no fill, leading `+`,
     above) is unchanged and out of scope" with a statement that, since 12 October 2026
     (`strip-extra-chip-filled`), the strip chip also has the filled fill and differs from the panel
     line only by lacking the check.
7. `.claude/skills/architecture/SKILL.md` line 278: the clause "the strip's own `EXTRA` style stay
   distinct" is stale on fill; update it to say they differ only by glyph.

## Verification

- `CI=true ./init.sh` exits 0 and prints `konsist`, `detekt` and `ktlint` as `wired`.
- The updated unit tests pass; `ContrastTest` passes. No new Konsist rule, so no failing case is
  needed.
- Grep confirms no remaining statement of "extra has no fill" in `DESIGN.md`, the KDoc or the tests.

## Device check (Pixel 5, no writes)

Debug build with the demo jam: set the flag `bluesjam.demoUpcomingJamLive` as the other slices did.
Do not add or delete Sheet data, and do not write anything. Open Próxima jam and screenshot a list
row whose strip shows a filled slot chip and an extra chip (`+ saxo: ...`) side by side. Confirm:
the extra's fill matches the filled chip's, the text is legible, the `+` is visible, the extra has no
check, and no amber appears on it. If the demo jam has no row with an extra, say so and report it as
unverified on device. Do not invent a write to produce one.

## Out of scope

Panel line styling, `detailColor`, filters, counts, a new glyph for extras, any data or copy change.
