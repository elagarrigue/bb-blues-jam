# Feature Implementation Spec: Convert the scaffold to a Kotlin + Compose baseline

## Source Feature

- `id`: gradle-kotlin-compose-baseline
- `area`: bootstrap
- `depends_on`: none
- `status`: not_started (at planning time, 28 September 2026)
- `source`: `feature_list.json`

## Goal

Replace the Views-based Android Studio template in `:app` with a Kotlin + Jetpack Compose baseline.
After this slice the app has a launcher `MainActivity` that sets Compose content. It renders one
placeholder screen filled with the project background `#111318` (`colors.background` in
`DESIGN.md`), and no appcompat or Material Views dependency remains. Every later slice builds on
this: `module-skeleton` and `design-tokens-theme` both depend on it.

## Non-Goals

- No `:core:*` or `:feature:*` modules. That is `module-skeleton`. `settings.gradle.kts` still
  includes only `:app`.
- No Konsist, detekt or ktlint (`konsist-isolation-rules`, `detekt-ktlint-gate`).
- No Koin, Molecule, Turbine, presenters, `UiModel`s or ViewModels (`molecule-presenter-harness`,
  D-02, D-16).
- No design tokens, no Material 3 theme, no fonts (Barlow Condensed/Chivo), no type scale. Only
  `background` and, for the placeholder label, `text` from `DESIGN.md`. The theme proper belongs to
  `design-tokens-theme`.
- No Material 3 dependency. `design-tokens-theme` decides how `MaterialTheme` is built.
- No navigation, bottom bar, product copy, domain types, networking, Room or DataStore.
- No light theme or `values-night` variant (dark only, `DESIGN.md`).
- No change to `init.sh`, `AGENTS.md` or the verification rules.
- No SDK level changes (`compileSdk`/`targetSdk` 37, `minSdk` 24 stay as they are).

## Job Story

When an agent starts the next slice (modules, theme, presenters),
I want `:app` to already compile Kotlin and Compose from the version catalog,
so I can add composables and modules without first undoing a Views template.

## Users And Permissions

- Any user: opens the app and sees the placeholder screen. No roles, no writes, no mutations. D-13
  does not apply because this slice has no mutation.

## Acceptance Scenarios

### Scenario 1: The gate is green on the new baseline

Given the repository after this slice
When `CI=true ./init.sh` runs
Then build and check both succeed with exit 0, and Konsist, detekt and ktlint still print
`NOT WIRED YET`. This is expected and correct for this slice.

### Scenario 2: The app launches into a Compose screen

Given a debug build installed on a device or emulator
When the user taps the launcher icon
Then a full-screen `#111318` surface appears with a centered label `BB Blues Jam` in `#E2E2E9`,
with no action bar, no white flash before first frame, and no crash.

### Scenario 3: No View-system UI dependency remains

Given `app/build.gradle.kts` and `gradle/libs.versions.toml`
When they are searched for `appcompat` and `com.google.android.material`
Then there are no matches, and nothing in `app/src/main` references `Theme.MaterialComponents`,
`AppCompatActivity` or a layout XML.

### Scenario 4: Compose compiler and Kotlin are aligned

Given the version catalog
When the build runs
Then the Compose compiler Gradle plugin version equals the Kotlin Gradle plugin version on the
build classpath, and the build prints no Kotlin/Compose compiler version-mismatch error.

## Repository Research

### Files Inspected

- `settings.gradle.kts` — foojay toolchain plugin 1.0.0, `FAIL_ON_PROJECT_REPOS`, google +
  mavenCentral, includes only `:app`.
- `build.gradle.kts` (root) — only `alias(libs.plugins.android.application) apply false`.
- `app/build.gradle.kts` — AGP 9 DSL (`compileSdk { version = release(37) }`,
  `optimization { enable = false }`), namespace/applicationId `com.bbbjam`, minSdk 24, targetSdk 37,
  Java 11, depends on appcompat, core-ktx, material, junit, espresso, androidx-junit.
- `gradle/libs.versions.toml` — agp 9.4.1, coreKtx 1.19.0, junit 4.13.2, junitVersion 1.3.0,
  espressoCore 3.7.0, appcompat 1.8.0, material 1.14.0. Only one plugin: `android-application`.
- `gradle/wrapper/gradle-wrapper.properties` — Gradle 9.6.0.
- `gradle.properties` — configuration cache on, `kotlin.code.style=official`.
- `app/src/main/AndroidManifest.xml` — `<application>` with **no activity at all**. The app
  currently has no launcher entry, so "the app launches" is not yet true today.
- `app/src/main/res/values/themes.xml`, `values-night/themes.xml` — `Theme.BBBluesJam` with parent
  `Theme.MaterialComponents.DayNight.DarkActionBar`, which comes from the `material` library being
  removed.
- `app/src/main/res/values/colors.xml` — template purple/teal/black/white, all unused after this
  slice.
- `app/src/main/res/values/strings.xml` — `app_name = BBBluesJam`.
- `app/src/main/keepRules/rules.keep` — empty template R8 rules. Leave it untouched.
- `app/src/test/java/com/bbbjam/ExampleUnitTest.kt`,
  `app/src/androidTest/java/com/bbbjam/ExampleInstrumentedTest.kt` — template tests in Kotlin.
- `init.sh` — runs `./gradlew build` and `./gradlew check` quietly, reports the three tools, and
  never installs when `CI=true`.
- `./gradlew buildEnvironment` output — AGP 9.4.1 pulls
  `org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.10` onto the build classpath.
- Local Gradle cache — `compose-bom` 2025.09.00 (maps to Compose UI/runtime 1.9.1, material3
  1.3.2), `activity-compose` 1.11.0, and core-ktx 1.19.0 (compiled against kotlin-stdlib 2.1.20)
  have all been resolved on this machine before.
- `DESIGN.md`, `.claude/skills/architecture/SKILL.md`, `docs/technical-discovery.md`,
  `PROGRESS.md`, `feature_list.json`.

### Existing Patterns To Follow

- All versions go through `gradle/libs.versions.toml`, and plugins are applied with `alias(...)`.
  The root file declares plugins `apply false`.
- **AGP 9 built-in Kotlin is already active.** The existing `.kt` tests compile with no Kotlin
  plugin applied. Do **not** add `org.jetbrains.kotlin.android`. AGP 9 rejects it when built-in
  Kotlin is on, and it is redundant.
- Package `com.bbbjam`. Sources live under `src/main/java/com/bbbjam/` (the existing test source
  sets use `java/`, so keep that directory name for consistency).

### Current Gaps

- No `MainActivity`, no launcher intent filter, no Compose.
- No `docs/specs/` directory existed before this spec. The skill's reference spec
  `docs/specs/bootstrap-nextjs-shell.md` does not exist in this repo.
- No JVM-level way to assert that a composable rendered. There is no Robolectric or Compose UI test
  harness, and adding one is out of scope. The rendering check is manual.

## Technical Approach

**Versions (all new entries go in the catalog):**

| Entry | Version | Why |
|---|---|---|
| `kotlin` | `2.2.10` | Exactly the KGP that AGP 9.4.1 already puts on the classpath. Pinning it avoids overriding AGP's built-in Kotlin. |
| plugin `kotlin-compose` = `org.jetbrains.kotlin.plugin.compose` | `version.ref = "kotlin"` | Since Kotlin 2.0 the Compose compiler ships with Kotlin and **must** match the KGP version. Referencing the same `kotlin` key keeps them locked together. |
| `composeBom` = `androidx.compose:compose-bom` | `2025.09.00` | Compose 1.9.1. Its libraries are compiled with a Kotlin older than 2.2, so a 2.2.10 compiler reads their metadata. It has already been resolved on this machine. |
| `activityCompose` = `androidx.activity:activity-compose` | `1.11.0` | Provides `ComponentActivity.setContent` and `enableEdgeToEdge`. Compatible with compileSdk 37 and minSdk 24. Already resolved locally. |

Compose libraries, versioned by the BOM: `androidx.compose.ui:ui`,
`androidx.compose.foundation:foundation`, `androidx.compose.ui:ui-tooling-preview`, and
`androidx.compose.ui:ui-tooling` as `debugImplementation` only.

Do not bump these versions to newer ones during implementation unless the build forces it. A
Compose BOM built with Kotlin 2.4 would fail metadata reads under a 2.2.10 compiler. If a bump is
unavoidable, raise `kotlin` and the KGP on the classpath together, and record the reason in
`PROGRESS.md`.

**Build wiring:**

- Root `build.gradle.kts`: add `alias(libs.plugins.kotlin.compose) apply false`.
- `app/build.gradle.kts`: apply `alias(libs.plugins.kotlin.compose)`. Add
  `buildFeatures { compose = true }`. Replace appcompat and material with activity-compose, the BOM
  platform and the Compose libraries above. Keep core-ktx and the test dependencies.
- Catalog: add the entries above and **delete** the `appcompat` and `material` versions and
  libraries.

**Placeholder screen (`:app` only):**

- `app/src/main/java/com/bbbjam/MainActivity.kt`: `class MainActivity : ComponentActivity()`. In
  `onCreate`, call `enableEdgeToEdge()`, then `setContent { PlaceholderScreen() }`.
- `PlaceholderScreen` goes in the same file or in `app/src/main/java/com/bbbjam/PlaceholderScreen.kt`.
  It is a `Box(Modifier.fillMaxSize().background(colorResource(R.color.background)))` with a
  centered `BasicText(stringResource(R.string.placeholder_title))` in
  `colorResource(R.color.text)`. Use foundation `BasicText`, not Material `Text`: Material 3 is out
  of scope. Add a `@Preview` with `showBackground = true` and `backgroundColor = 0xFF111318`.
- The placeholder label is a product name, not Spanish copy. `BB Blues Jam` complies with D-12 as
  it stands.
- **Colors in this slice live once, as XML resources.** `res/values/colors.xml` gets
  `background = #FF111318` and `text = #FFE2E2E9`, both from `DESIGN.md`. Compose reads them through
  `colorResource`, and the window theme reads the same `background`. This keeps one literal per
  value. `design-tokens-theme` replaces this with Kotlin tokens in `:core:ui`. Mark the colors with
  a comment such as `<!-- Temporary: superseded by design-tokens-theme -->`.
- **Window theme without Material Components.** Change `Theme.BBBluesJam` to parent
  `android:Theme.Material.NoActionBar`, with `android:windowBackground = @color/background`, so the
  pre-Compose window is already dark with no white flash. Delete `res/values-night/themes.xml`: the
  app is dark only, and a night variant would reintroduce the removed parent.
- Manifest: declare `.MainActivity` with `android:exported="true"` and the MAIN/LAUNCHER intent
  filter.

## Expected File Changes

- `gradle/libs.versions.toml` — modify. Add `kotlin`, `composeBom`, `activityCompose`, the Compose
  libraries and the `kotlin-compose` plugin. Remove appcompat and material.
- `build.gradle.kts` — modify. Add the `kotlin-compose` plugin with `apply false`.
- `app/build.gradle.kts` — modify. Apply the compose plugin, enable `buildFeatures.compose`, and
  swap the dependencies.
- `app/src/main/AndroidManifest.xml` — modify. Add the launcher `MainActivity`.
- `app/src/main/java/com/bbbjam/MainActivity.kt` — create.
- `app/src/main/java/com/bbbjam/PlaceholderScreen.kt` — create. May instead live in
  `MainActivity.kt`.
- `app/src/main/res/values/themes.xml` — modify. Change the parent to
  `android:Theme.Material.NoActionBar` and set the window background.
- `app/src/main/res/values-night/themes.xml` — delete.
- `app/src/main/res/values/colors.xml` — modify. Replace the template colors with `background` and
  `text`.
- `app/src/main/res/values/strings.xml` — modify. Add `placeholder_title`.
- `feature_list.json`, `PROGRESS.md` — update status and evidence at the end.

Not touched: `settings.gradle.kts`, `gradle.properties`, the wrapper, `init.sh`,
`keepRules/rules.keep`, and both template test files.

## Visual Design Impact

- UI involved: yes, one placeholder screen.
- Design source: `DESIGN.md` front matter, `colors.background` (`#111318`) and `colors.text`
  (`#E2E2E9`) only.
- Screens or states affected: launch window and the placeholder screen. No product screen exists
  yet.
- New design artifact required: no. Amber is not used here: nothing on this screen is actionable.

## Durable Documentation Impact

- `ARCHITECTURE.md` and `CONSTRAINTS.md`: do not exist, not needed. No boundary or rule changes. The
  architecture lives in `.claude/skills/architecture/SKILL.md`, which this slice does not alter.
- `AGENTS.md`: not needed. The workflow and startup path are unchanged.
- `PROGRESS.md`: update the "What exists" section (Compose baseline, the chosen versions) and add a
  session entry.
- `docs/technical-discovery.md`: not needed. The stack is already described, and the catalog is the
  source of truth for versions.

## Implementation Plan

1. Run `CI=true ./init.sh` on the untouched tree and confirm the baseline is green.
2. Edit the catalog and both Gradle files, then run `./gradlew :app:assembleDebug` to catch plugin
   and version errors early.
3. Rework the resources (colors, strings, theme, delete `values-night`) and the manifest.
4. Add `MainActivity` and `PlaceholderScreen` with its preview.
5. Run `CI=true ./init.sh`, then do the manual launch check.
6. Update `feature_list.json` (status `passing`, evidence) and `PROGRESS.md`.

## Implementation Tasks

- [ ] Baseline `CI=true ./init.sh` recorded before any change.
- [ ] Catalog: add `kotlin = "2.2.10"`, `composeBom = "2025.09.00"`, `activityCompose = "1.11.0"`,
      the libraries and the `kotlin-compose` plugin. Remove appcompat and material.
- [ ] Root plugin block: `kotlin-compose` with `apply false`.
- [ ] `:app`: apply the compose plugin, `buildFeatures { compose = true }`, and the new
      dependencies (BOM as `platform(...)`, `ui-tooling` as `debugImplementation`).
- [ ] Resources: colors, `placeholder_title`, platform-parent theme with window background, and
      `values-night` deleted.
- [ ] Manifest: exported launcher `MainActivity`.
- [ ] `MainActivity` and `PlaceholderScreen` with `@Preview`.
- [ ] `CI=true ./init.sh` green, and the grep checks below are clean.
- [ ] Manual launch on a device or emulator.
- [ ] Evidence recorded in `feature_list.json` and `PROGRESS.md`.

## Verification Plan

JVM, no device, part of the standard gate:

- `CI=true ./init.sh` exits 0. Build and check (unit tests and lint) pass, and the three tools
  print `NOT WIRED YET`. No Compose-specific JVM test is added: there is no JVM harness able to
  render a composable, and adding Robolectric is out of scope.
- `grep -nE "appcompat|com\.google\.android\.material" app/build.gradle.kts gradle/libs.versions.toml`
  returns nothing.
- `grep -rnE "MaterialComponents|AppCompat" app/src/main` returns nothing.
- `ls app/src/main/res/values-night` fails (directory gone).
- `./gradlew buildEnvironment | grep -E "kotlin-gradle-plugin:|compose-compiler-gradle-plugin:"`
  shows the same version (2.2.10) for both.

Needs a device or emulator, manual, outside the gate:

- `./gradlew :app:installDebug`, then launch "BBBluesJam" from the launcher. Expect a dark
  `#111318` screen edge to edge, the centered `BB Blues Jam` label, no action bar, no white flash,
  and no crash in `adb logcat -s AndroidRuntime`.
- Optional: open Layout Inspector and confirm the tree is a `ComposeView` holding the `Box`, not a
  View layout.

E2E: the repo has no persistent E2E command, and none is justified for a placeholder screen.

## Evidence To Capture

- `CI=true ./init.sh` tail output with exit code, before and after.
- Output of the three grep checks and the `buildEnvironment` version line.
- The final versions table (Kotlin, Compose plugin, BOM, activity-compose).
- A screenshot or written observation of the launch (device/emulator model, API level). If no
  device was available, record that plainly. Scenario 2 then stays unverified and must not be
  claimed.

## Validator Checklist

- [ ] Only `:app` changed. No new modules, and `settings.gradle.kts` is untouched.
- [ ] No Koin, Molecule, Material 3, Konsist, detekt, ktlint or font/type tokens were introduced.
- [ ] Every new dependency and plugin comes from `libs.versions.toml`, and the Compose plugin
      version references the `kotlin` key.
- [ ] No `org.jetbrains.kotlin.android` plugin was added (AGP 9 built-in Kotlin).
- [ ] The appcompat and material entries are gone from both the catalog and `app/build.gradle.kts`.
- [ ] Hex values match `DESIGN.md` (`#111318`, `#E2E2E9`) and appear once, in `colors.xml`.
- [ ] `values-night` is gone, so there is no light/day-night theme path.
- [ ] `CI=true ./init.sh` rerun by the validator exits 0.
- [ ] Manual launch evidence exists, or its absence is stated honestly.
- [ ] `feature_list.json` status is `passing` (not `accepted`), and `PROGRESS.md` is updated.
