# Feature Implementation Spec: Implement the dark theme and design tokens

## Source Feature

- `id`: design-tokens-theme
- `area`: ui
- `depends_on`: gradle-kotlin-compose-baseline (`accepted`), module-skeleton (`accepted`)
- `status`: not_started (at planning time, 28 September 2026, HEAD `25a9b27`)
- `source`: `feature_list.json`

## Goal

`:core:ui` holds the design tokens from `DESIGN.md` (colors, typography, rounded, spacing) as Kotlin,
plus a `BluesJamTheme` composable: dark only, built on Material 3, with the two bundled typefaces.
Amber is public only under the names of its five reserved uses. `:app` draws the placeholder screen
from the tokens, and the temporary XML colors and the preview literal from the baseline slice go
away. JVM tests prove the semantic-amber rule, the Material mapping, the type scale, and the
measured contrast. A new Konsist rule stops color literals outside `:core:ui`.

## Stale Notes In The Feature Entry (corrected here, do not follow them)

- "The hex values there are derived … not measured": outdated. Since session 004 the palette in
  `DESIGN.md` is measured from the Stitch export. Four tokens are still marked derived: `textMuted`,
  `slotFilled`, `archive`, `error`. **Finding:** all four values do appear in the export's Material 3
  scheme (`on-surface-variant`, `surface-container-highest`, `outline`, `error`). What is derived is
  which role each value was given, not the hex. They are implemented exactly as written in `DESIGN.md`,
  with a KDoc note saying they are derived. Contrast is measured for them too, so a later change is
  a one-line token edit that the tests re-check.
- The `CoreUiMarker` cleanup and adding Compose to `:core:ui` were done by `molecule-presenter-harness`.
  Nothing to do here.

## Non-Goals

- No components. That means no instrument strip, chips, buttons, badges, song rows or bottom bar.
  The `components` tokens in `DESIGN.md` are for the slices that build those components. The 15%
  amber fill of an open slot belongs to the instrument-strip slice.
- No light scheme, no `values-night`, no dynamic color, no `isSystemInDarkTheme()`.
- No new `:feature:*` module, no navigation, no Koin, no domain types.
- No changes to `init.sh`, the root build file, `.editorconfig`, `detekt.yml`. No baseline, no
  suppression, no rule disable.
- No screenshot-testing library (Paparazzi, Roborazzi, the AGP screenshot plugin).

## Job Story

When a later slice draws a real screen,
I want colors, type, shapes and spacing to be named tokens that already render correctly,
so I can build the screen without inventing values, and amber stays reserved for what the user can act on.

## Users And Permissions

No roles, writes or mutations are involved. D-13 does not apply.

## Acceptance Scenarios

### Scenario 1: Gate green
Given the tree after this slice
When `CI=true ./init.sh` runs
Then it exits 0 and prints `konsist: wired`, `detekt: wired`, `ktlint: wired`. `:core:ui` runs the
existing 9 tests plus `BluesJamColorsTest` (3), `BluesJamTypographyTest` (2), `ContrastTest` (6) and
`WindowBackgroundTest` (1), all passing. `ModuleIsolationTest` runs 9 tests with 0 failures.

### Scenario 2: Amber only through semantic roles
Given `val accent: Color = BluesJamPalette.Amber` is added to `BluesJamColors`
When `:core:ui:testDebugUnitTest` runs
Then `amber is reachable only through its five semantic roles` fails. Restore; green.

### Scenario 3: No Material baseline color leaks
Given the `secondary = …` line is removed from the `darkColorScheme(…)` call
Then `every Material color role comes from a token` fails, because the M3 baseline purple comes back.
Restore; green.

### Scenario 4: The window color cannot drift
Given `bluesjam_window_background` is changed to `#FF000000`
Then `window background resource equals the background token` fails. Restore; green.

### Scenario 5: Contrast is guarded
Given `TextMuted` is changed to `0xFF55504A`
Then `muted text on a surface` and `muted text on the background` fail. Restore; green.

### Scenario 6: No color literal outside `:core:ui`
Given `@Preview(showBackground = true, backgroundColor = 0xFF111318)` is put back in
`PlaceholderScreen.kt`, or `color = Color(0xFFFFB300)` is added to its `Text`
When `./gradlew :konsist-test:test` runs (or `CI=true ./init.sh`)
Then `colors outside core ui come from its tokens` fails with `Assert 'no-color-literal-outside-core-ui'
was violated … PlaceholderScreen.kt`, and `init.sh` exits 1. Restore; green.

### Scenario 7: On the Pixel 5 the app launches dark, with the theme's fonts
Given the debug APK is installed on the Pixel 5 (serial `09281FDD4004U6`, API 34)
When the app is launched and the screen is captured
Then background pixels read `#111318`, the label is Barlow Condensed Bold in `#E2E2E9` (±1 per
channel), the system bars have light icons over the dark background, and no white frame is seen.

## Repository Research

### Files Inspected
`AGENTS.md`, `PROGRESS.md`, `feature_list.json` (this entry), `DESIGN.md`, `docs/design/README.md`,
`docs/design/export/…/nocturna_blues_jam/DESIGN.md` and `pr_xima_jam_vista_m_sico/code.html` (the
Tailwind config: line heights, weights, radii), `bb-blues-jam-design-prompt.md` (Dirección visual:
"Material 3 como base, pero … identidad propia"), `docs/risks-and-open-questions.md`,
`.claude/skills/architecture/SKILL.md`, `docs/specs/gradle-kotlin-compose-baseline.md` (Accepted
Deviations), `docs/specs/molecule-presenter-harness.md`, `gradle/libs.versions.toml`, root, `app` and
`core/ui` `build.gradle.kts`, `app/src/main/{AndroidManifest.xml,res/values/*.xml}`,
`MainActivity.kt`, `PlaceholderScreen.kt`, `ModuleWiringTest.kt`, `ModuleIsolationTest.kt`, `init.sh`,
the Compose BOM 2025.09.00 POM (material3 **1.3.2**).

### Prototype (verified, not assumed)
Built on a clone of `25a9b27` in the session scratchpad (`scratchpad/dt`), with exactly the changes
listed under Expected File Changes:
- `CI=true ./init.sh` exited 0 with three `wired` lines. Test counts were as in Scenario 1. The warm
  run took 6.7 s. `./gradlew ktlintFormat` only reflowed one line. detekt reported 0 findings.
  Android lint on `:core:ui` found no issues, and `:app` showed only the six version warnings it
  already had at HEAD.
- Scenarios 2–6 each failed exactly as written and passed again after the restore.
- `:app:debugCompileClasspath` resolves `material3:1.3.2` through `:core:ui`'s `api`. `:app` needs
  no new dependency line.
- APK: `res/font/*.ttf` totals 445,088 bytes, and `assets/licenses/OFL-*.txt` are packaged.
- **Pixel 5 launch:** the background at 5 sample points read `#111318`. The label's dominant pixels
  were `#111318` (22,951) and `#E2E2E8` (9,183). The label is visibly Barlow Condensed Bold. This
  proves the `LocalContentColor` fix; see Decision 1.
- **Pixel 5 showcase probe:** `MainActivity` was temporarily switched to `ThemeShowcase`. All three
  Barlow weights and Chivo rendered as distinct faces. Neutral swatches came back within 3 per channel
  of their tokens. Saturated ones came back shifted, for example amber `#FFB300` read as `#F4B63F`.
  **Finding:** this display runs `ColorMode::DISPLAY_P3` (checked with `dumpsys SurfaceFlinger`), so
  `screencap` returns P3-encoded values. Converting each token from sRGB to P3 predicts every sampled
  value within 1 (`FFB300→F4B63F`, `281900→261A04`, `FFB4AB→F4B7AE`, `D6C4AC→D3C5AF`,
  `9E8E78→9B8F7B`, `514532→4F4534`). Pixel checks must compare against these values, or be done with
  the display set to Natural (sRGB).

### Current Gaps
- `docs/design/README.md` ("What it contributed") and bitácora §6.5 still say background `#0C0E13`
  and border `#282A30`. That contradicts `DESIGN.md` (`#111318`, `#514532`). `#0C0E13` is the export's
  `surface-container-lowest`, used only for the header. `DESIGN.md` is authoritative, and its values
  match the export's `body` background. **This slice follows `DESIGN.md`. The stale text is out of
  scope and has been reported to the orchestrator.**

## Technical Approach

### Decision 1: Material 3 underneath, static token objects on top, no custom CompositionLocals
`BluesJamTheme(content)` calls `MaterialTheme(colorScheme, typography, shapes)`. Every M3 role is
mapped from a token, so M3 components (text fields, bottom sheets, navigation bar) pick up the
palette and fonts without any per-screen code. That is the "Material 3 as a base with its own identity" the
brief asks for. The tokens are plain `object`s (`BluesJamColors`, `BluesJamTypography`,
`BluesJamShapes`, `BluesJamSpacing`), reached through `object BluesJamTheme { colors, typography,
shapes, spacing }`, which mirrors `MaterialTheme.colorScheme`. There are no custom CompositionLocals: with one
palette, dark only and no dynamic color, a local would never hold a second value. If a subtree ever
needs different values (for example the archive treatment), adding a local is an additive change.
Inside the theme there is one `CompositionLocalProvider(LocalContentColor provides BluesJamColors.text)`, because
`MaterialTheme` does not set `LocalContentColor`. Without it, a bare M3 `Text` outside a `Surface`
defaults to black on `#111318`. The prototype's label only reads `#E2E2E9` because of this line.
Screens read `BluesJamTheme.*`. `MaterialTheme.colorScheme` exists for M3 component defaults, and
component defaults are not design decisions: each component slice sets its colors from `BluesJamColors`.

### Decision 2: amber is internal, public only under its roles
`internal object BluesJamPalette` holds the 11 distinct `DESIGN.md` hex values (`slotOpen` equals
`primary`), with names `Background, Surface, SurfaceRaised, Amber, OnAmber, Text, TextMuted,
Border, SlotFilled, Archive, Error`. The KDoc marks the four derived tokens. The public
`BluesJamColors` has neutral roles (`background, surface, surfaceRaised, text, textMuted, border,
slotFilled, archive, error`) and the amber roles, which are the five uses in `DESIGN.md` plus their
content colors: `primaryAction`/`onPrimaryAction`, `slotOpen`, `key`, `published`/`onPublished`,
`activeFilter`/`onActiveFilter`. The word "amber" appears in no public name. The compiler keeps
`BluesJamPalette` out of features, and `BluesJamColorsTest` fixes the set of roles that may be amber.

M3 mapping (`internal object BluesJamMaterial`, `darkColorScheme` with every parameter set):
primary, primaryContainer, inversePrimary → Amber; onPrimary, onPrimaryContainer → OnAmber;
secondary, tertiary → TextMuted; onSecondary, onTertiary, onError, inverseOnSurface, scrim,
surfaceDim, surfaceContainerLowest, background, surface, **surfaceTint** → Background (no tonal
tint: elevation comes from explicit surface tokens, per DESIGN.md Shapes); secondaryContainer,
tertiaryContainer, errorContainer, surfaceContainer, surfaceContainerHigh → SurfaceRaised;
onSecondaryContainer, onTertiaryContainer, onBackground, onSurface, inverseSurface → Text;
surfaceVariant, surfaceBright, surfaceContainerHighest → SlotFilled; onSurfaceVariant → TextMuted;
surfaceContainerLow → Surface; error, onErrorContainer → Error; outline → Archive; outlineVariant →
Border. Typography: display* → key; headline* → h1; title* → songTitle; bodyLarge, bodyMedium,
labelLarge → body; bodySmall, labelMedium, labelSmall → caption. Shapes: extraSmall, small → sm;
medium → md; large, extraLarge → lg.

### Decision 3: tokens
Typography, with line heights from the export's Tailwind config, which measures the same sizes:
`h1` Barlow Condensed 700 28/32sp, `songTitle` BC 600 24/28, `key` BC 800 44/44, `body` Chivo
400 16/24, `caption` Chivo 400 12/16. Weights for `body` and `caption` are not given in `DESIGN.md`,
so they follow the export (400). Rounded: `sm 4dp`, `md 8dp`, `lg 12dp` as `CornerBasedShape`
(`RoundedCornerShape`). Spacing: `xs 4`, `sm 8`, `md 16`, `lg 24` dp.

### Decision 4: fonts are bundled, not downloadable
The app is used offline in a bar. Downloadable fonts need Google Play services and network on first
use. Without them, text falls back to Roboto, which is wider than Barlow Condensed: titles would wrap
differently, and the identity would disappear exactly where the app is used. Bundling costs about 445 KB.
Static TTFs are used, not variable fonts: `minSdk` 24 cannot apply weight variation below API 26. Files,
each saved as `core/ui/src/main/res/font/<name>`:

| Resource | Source (pinned commit) | SHA-256 |
|---|---|---|
| `barlow_condensed_semibold.ttf` | google/fonts `60824dce48f7dd28fe7d65559f2da1f6e04e585b` `ofl/barlowcondensed/BarlowCondensed-SemiBold.ttf` | `7b619d14…d9` |
| `barlow_condensed_bold.ttf` | same dir, `BarlowCondensed-Bold.ttf` | `e476562e…65` |
| `barlow_condensed_extrabold.ttf` | same dir, `BarlowCondensed-ExtraBold.ttf` | `724c9c25…60` |
| `chivo_regular.ttf` | Omnibus-Type/Chivo `dc61c468d79781eb5183426e88e844af16cdc3e5` `fonts/Chivo/ttf/Chivo-Regular.ttf` | `66310888…5f` |

google/fonts ships Chivo only as a variable font, so the static Regular comes from the upstream repo
that google/fonts cites. Licences are SIL OFL 1.1. `OFL.txt` from each of those two directories goes to
`core/ui/src/main/assets/licenses/OFL-BarlowCondensed.txt` and `OFL-Chivo.txt` (SHA-256 `186d750e…3f`
and `c9b69fa1…1c`), so the licence ships inside the APK with the fonts, as the OFL requires. The
implementer fetches them with
`curl -fL -o <dest> https://raw.githubusercontent.com/<repo>/<commit>/<path>` and checks the full
SHA-256 values listed in Evidence. **Fetching third-party binaries into the repo needs the user's OK.**

### Decision 5: the one remaining XML color
The platform draws the window before any Kotlin runs, so `windowBackground` needs a color resource.
Using `@android:color/black` would flash `#000` to `#111318`, and duplicating a hex in `:app` would
drift unnoticed. Instead, `core/ui/src/main/res/values/colors.xml` defines
`bluesjam_window_background = #FF111318`, next to the tokens it mirrors, with a comment. `:app`'s
`themes.xml` references it, which works because library resources are merged into the app. `WindowBackgroundTest` reads
that file and fails if the value differs from `BluesJamPalette.Background`. `app/src/main/res/values/colors.xml`
is deleted, and the `text` color disappears with it.

### Decision 6: dependencies (catalog, BOM-managed, no new version)
Catalog: `androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }`
(BOM 2025.09.00 → 1.3.2). `core/ui/build.gradle.kts` adds, after the runtime line:
```kotlin
    // Tokens expose Color, TextStyle, Dp and Shape; screens draw with the themed Material 3 components.
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
```
`api` for ui: the public token types come from ui-graphics, ui-text and ui-unit. `api` for material3:
`:core:ui` is the design system, and a module that depends on it should get the components the theme
styles without having to choose a component library itself. Foundation (the type of `CornerBasedShape`) arrives through material3.
There is no Google Fonts artifact. `:app/build.gradle.kts` is unchanged.

### Decision 7: Konsist rule `no-color-literal-outside-core-ui`
The architecture skill says: "A color … value → `:core:ui` tokens … Never a literal in a feature." The
rule costs one test of about 7 lines. It scans every Kotlin file whose module is not `core/ui` or
`konsist-test` (so `:app`, every `:feature:*`, and the other cores) for
`Regex("""\bColor\(\s*(0x|\d)|\b0x[0-9A-Fa-f]{8}L?\b""")`: a `Color(…)` built from a number, or an ARGB
hex literal such as a preview `backgroundColor`. It also keeps the baseline cleanup from coming back.
Add constants `CORE_UI = "core/ui"` and `KONSIST_TEST = "konsist-test"` plus
`COLOR_LITERAL` with a KDoc line, and add the test before the build-file test (see the prototype diff shape in
Implementation Tasks). Known limits: it does not catch `Color.Red`, `Color.parseColor`, or XML colors.
Adding a rule to the gate needs the user's OK (see the report).

### Decision 8: `:app`
`MainActivity`: `setContent { BluesJamTheme { PlaceholderScreen() } }`. The edge-to-edge dark bars
stay exactly as they are. Reword the comment's "over #111318" to "over the dark background".
`PlaceholderScreen`: `Box(fillMaxSize().background(BluesJamTheme.colors.background))` with an M3
`Text(stringResource(R.string.placeholder_title), style = BluesJamTheme.typography.h1)`. No explicit
color: it inherits `LocalContentColor`. Its `@Preview` has no arguments and wraps the screen in
`BluesJamTheme`. The KDoc drops the colors.xml sentence.

### Decision 9: preview
`ThemeShowcase(modifier)` is public in `:core:ui`. It is a `Column` on `background`: a 48×20dp
`sm`-rounded swatch plus a caption for each of the 17 roles, then one line per text style
(`h1 Próxima jam`, `songTitle Stormy Monday`, `key E7` in `colors.key`, `body Traé tu viola y cable`,
`caption 32 anotados`). There is also a private `@Preview(widthDp = 360, heightDp = 720)` wrapped in the theme.
It is public so the device check can show it by changing a single line in `MainActivity`.

## Expected File Changes
- `gradle/libs.versions.toml`: modify; one library entry.
- `core/ui/build.gradle.kts`: modify; Decision 6.
- `core/ui/src/main/kotlin/com/bbbjam/core/ui/theme/`: create `BluesJamPalette.kt`,
  `BluesJamColors.kt`, `BluesJamTypography.kt` (`internal object BluesJamFonts` plus the styles),
  `BluesJamDimens.kt` (`BluesJamShapes`, `BluesJamSpacing`), `BluesJamTheme.kt` (the fun, the object,
  and `internal object BluesJamMaterial`), `ThemeShowcase.kt`.
- `core/ui/src/main/res/font/*.ttf` (4), `core/ui/src/main/assets/licenses/OFL-*.txt` (2),
  `core/ui/src/main/res/values/colors.xml`: create.
- `core/ui/src/test/kotlin/com/bbbjam/core/ui/theme/`: create `BluesJamColorsTest.kt`,
  `BluesJamTypographyTest.kt`, `ContrastTest.kt`, `WindowBackgroundTest.kt`.
- `app/src/main/res/values/colors.xml`: delete. `app/src/main/res/values/themes.xml`: modify (one ref).
- `app/src/main/java/com/bbbjam/MainActivity.kt`, `PlaceholderScreen.kt`: modify.
- `konsist-test/.../ModuleIsolationTest.kt`: modify (Decision 7).
- `.claude/skills/architecture/SKILL.md`, `docs/technical-discovery.md`, `PROGRESS.md`,
  `feature_list.json`: update (see below).

## Visual Design Impact
- UI involved: yes. Source: `DESIGN.md` front matter and prose on amber. The only screen affected is the placeholder,
  which should look as it does now except for the font (Barlow Condensed Bold 28sp instead of the system default).
- New design artifact required: no.

## Durable Documentation Impact
- `ARCHITECTURE.md` / `CONSTRAINTS.md`: not needed (they do not exist; the architecture skill plays that role).
- `AGENTS.md`: not needed.
- `.claude/skills/architecture/SKILL.md`: update. Build Conventions: `:core:ui` exposes ui and
  material3 as `api`, fonts are bundled. Where each piece goes: screens read `BluesJamTheme.*`, never
  `MaterialTheme.colorScheme` or the internal palette, and component slices set M3 component colors
  from `BluesJamColors`. Dependency Rules: Konsist now also enforces no color literal outside `:core:ui`.
- `docs/technical-discovery.md`: one line saying fonts are bundled (offline), not downloadable.
- `DESIGN.md`: not needed. Optional one-liner that the derived four match the export's M3 values;
  leave that to the orchestrator.

## Implementation Tasks
- [ ] Baseline `CI=true ./init.sh` (exit 0, three `wired`).
- [ ] Fetch the 4 fonts and 2 licences (Decision 4) and verify their SHA-256 values.
- [ ] Catalog entry and `core/ui/build.gradle.kts` (Decision 6).
- [ ] Theme sources (Decisions 1–3, 9) and `core/ui/.../values/colors.xml` (Decision 5).
- [ ] Tests. `BluesJamColorsTest` reads Color getters by reflection (Compose `Color` compiles to
  `long get<Name>-<hash>()`: take zero-parameter methods that return a primitive `long` and have a
  `-` in the name, and wrap with `Color(value.toULong())`). It asserts (a) the `BluesJamColors`
  properties equal to `Amber` are exactly `{primaryAction, slotOpen, key, published, activeFilter}`
  and those equal to `OnAmber` are exactly `{onPrimaryAction, onPublished, onActiveFilter}`; (b) every
  `ColorScheme` role (at least 30 found) is a palette value; (c) the amber `ColorScheme` roles are exactly
  `{primary, primaryContainer, inversePrimary}`. `BluesJamTypographyTest` checks the five styles'
  family, size and weight, and that all 15 M3 `Typography` styles (by reflection) use one of the two
  families. `ContrastTest` computes WCAG contrast with `Color.luminance()`, asserts at least 4.5 and
  equality with the recorded value ±0.01, for the six pairs in Evidence. `WindowBackgroundTest` is Decision 5.
- [ ] `:app` changes (Decision 8). Delete the `:app` colors.xml.
- [ ] Konsist rule (Decision 7).
- [ ] `./gradlew ktlintFormat`, then `CI=true ./init.sh`.
- [ ] Run each negative probe (Scenarios 2–6). Copy the file aside, edit, observe the failure, copy it back, check the SHA-1.
- [ ] Device check (Scenario 7 and the showcase), then docs, then `PROGRESS.md` and `feature_list.json` (`passing`).

## Verification Plan
- `CI=true ./init.sh` exits 0 with three `wired` lines, and the test counts match Scenario 1.
- Scenarios 2–6, each with its restore. Scenario 6 also through `init.sh` (exit 1).
- Device check (manual, on the Pixel 5; not part of the gate). adb is at
  `"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" -s 09281FDD4004U6`. Run `./gradlew :app:installDebug`,
  then `am force-stop com.bbbjam`, then `am start -W -n com.bbbjam/.MainActivity`, wait 3 s, and run `exec-out screencap -p`.
  Sample the PNG with a small Python PNG decoder, like the one earlier sessions used (for example,
  the scratchpad's `px.py`). Background should be `#111318`, label pixels about `#E2E2E9`, and
  the status-bar icons light. Then change the one `setContent` line to show `ThemeShowcase` in a
  `verticalScroll`, install, and capture twice (once scrolled). Check that the swatches match the P3
  values listed in Repository Research (±2), or the sRGB tokens if the display is set to Natural. Check by eye
  that the three Barlow weights and Chivo are distinct and are not Roboto. Restore `MainActivity`, check the SHA-1, and reinstall.
  The device rotates: record the orientation you captured in.
- A Compose `@Preview` renders inside Android Studio only. The device showcase stands in for it and
  is the evidence that the preview composable renders.

## Evidence To Capture

The contrast values below were measured (WCAG 2.x, from the hex values) and must match `ContrastTest`.
All six pairs are used for text, so each needs 4.5:1 for AA:

| Pair | Ratio | AA text (4.5) | AAA (7) |
|---|---|---|---|
| key / slotOpen `#FFB300` on background `#111318` | 10.35 | pass | pass |
| slotOpen `#FFB300` on surface `#1A1B21` | 9.57 | pass | pass |
| textMuted `#D6C4AC` on surface `#1A1B21` | 10.10 | pass | pass |
| textMuted `#D6C4AC` on background `#111318` | 10.93 | pass | pass |
| onPrimaryAction `#281900` on primaryAction `#FFB300` | 9.52 | pass | pass |
| text `#E2E2E9` on background `#111318` | 14.41 | pass | pass |

For reference, not tested: `archive` on background is 5.83 (AA only, which is enough for the muted
archive treatment); `border` on surface is 1.84, so it must never be the only boundary of a control
(WCAG 1.4.11 asks for 3:1), which matches DESIGN.md's "separation from surface elevation, not borders".
Also record: the gate output and test counts, each probe's failing test name, the device PNG
paths with sampled values, and the full SHA-256 of the six fetched files (prototype:
`7b619d14bc2327509a9ef32b0890f709626f7ecc9ff61191c2a4314c5499d2d9` SemiBold,
`e476562ec9c1e16cf16475895b511f08c804f438cc9a9f80a44ea50a0eeb5b65` Bold,
`724c9c25952d5f4a2d87185d9767aa006144c5f0d944dc05bf7d5d603551c260` ExtraBold,
`6631088892f830831df8217f593aff171c0526d778be2b404d1df01b9bf925f4` Chivo Regular,
`186d750eb496a4c17a76385f82be6aea2ac1cf2de074a811d63786cf374ea73f` OFL Barlow Condensed,
`c9b69fa18c372df2b187b49efc57b1ea643b86a938e5af32f6b5a7af1017c891` OFL Chivo).

## Validator Checklist
- [ ] No hex literal or numeric `Color(…)` outside `core/ui` (the Konsist rule is green, and a probe fails it).
- [ ] `BluesJamPalette` is `internal`, and no public name contains "amber".
- [ ] Every `darkColorScheme` parameter is set; there is no light scheme, no dynamic color, no `values-night`.
- [ ] Fonts are static TTFs matching the SHA-256 values, and the OFL texts are in `assets/licenses`.
- [ ] `app/src/main/res/values/colors.xml` is gone. The only XML color is `bluesjam_window_background`, guarded by a test.
- [ ] Edge-to-edge dark bars are unchanged. Device evidence is present, with P3 or sRGB noted.
- [ ] The gate is green with three `wired` lines. No baseline, no suppression. Scope matches Expected File Changes.
- [ ] The architecture skill and technical discovery are updated. `feature_list.json` is `passing`, not `accepted`.

## Pending User Decisions

Recorded 28 September 2026. Implementation waits on these:

- Fetching and committing the four third-party TTFs and two SIL OFL 1.1 licence texts from GitHub at
  pinned commits (google/fonts for Barlow Condensed, Omnibus-Type/Chivo for Chivo).
- Adding the Konsist rule `no-color-literal-outside-core-ui` to the gate (tightens verification).
- Whether "Material 3 underneath, plain `BluesJamTheme.*` tokens on top; screens never read
  `MaterialTheme.colorScheme`" becomes a project decision (D-17) or stays in the architecture skill.
