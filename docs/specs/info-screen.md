# Feature Implementation Spec: Show the info screen

## Source Feature

- `id`: info-screen
- `area`: feature-info
- `depends_on`: `design-tokens-theme` (accepted)
- `status`: not_started at planning time
- `source`: `feature_list.json`

## Goal

The app opens on an Info screen that presents Bahía Blanca Blues, the monthly jam, the Hideaway
radio program, how to join as a musician, three social links that open outside the app, and a
discreet "Entrar como admin" entry. No venue (D-19), no station.

It is also the first `:feature:*` module, so it sets the template the other features copy: module
build file, composable presenter tested with Molecule, Koin module, screen with no logic, and
`:app` binding (D-02, D-03, D-16).

## Non-Goals

- Bottom navigation, tabs, a nav library or deeplinks (`bottom-navigation`).
- The passphrase login, the admin flag, `AdminSession`, logout, or any `:core:data` code
  (`admin-passphrase-login`). No mutation here, so D-13 does not apply; no auth.
- `build-logic` convention plugins (deferred, Decision 1).
- Venue, address, radio station or frequency, the festival, WhatsApp, a version footer, or anything
  else the Stitch screen shows that `docs/info-content.md` does not hold (bitácora 6.5).
- Icons, images, a logo, or a new `:core:ui` token.
- Changes to `init.sh`, the Konsist suite, detekt or ktlint config.

## Job Story

When I hear about the jam or open the app for the first time,
I want to see who runs it, how often it happens and how I get to play,
so I can show up and sign up without asking anyone.

## Users And Permissions

- Anyone: reads the screen and opens the links. No login, no network call by the app.
- Admin entry: visible to everyone; before `admin-passphrase-login` it only shows a notice.

## Decisions

**1. `build-logic` is deferred again.** The first feature module duplicates ~30 lines of
Android-library boilerplate already duplicated in `:core:ui` and `:core:data`. A convention plugin
for this slice would add an included build, touch four modules to be consistent, and move the
quality block the gate relies on, all inside a UI slice. Deferring forces no later undo: migrating
build files is mechanical. The root `subprojects {}` block already gave `:feature:info` detekt and
ktlint with no build code (prototype: `check --dry-run` schedules `:feature:info:detekt` and every
`ktlint*SourceSetCheck`). The implementer rewrites the skill's "No `build-logic` convention plugins
yet" bullet: deferred again by `info-screen`; it lands as its own slice, proposed as
`build-logic-conventions`, before or with the second `:feature:*` module (`next-jam-read-only-list`);
quality tools stay root-applied even then, because a per-module plugin must not be what the gate
depends on. Adding that feature entry is the orchestrator's call (not this slice).

**2. A presenter, not a plain composable.** The screen has behavior: opening a link (a side
effect that can fail), showing a link error, and the admin entry, which becomes the login trigger
and later reads the admin flag (D-15: the login lives in `:feature:info`). With `InfoPresenter`
now, `admin-passphrase-login` adds a constructor dependency and events; it changes nothing that
exists. The `UiModel` holds the copy and the links as plain values, so it is assistant-readable
(D-02) and the screen is logic-free.

**3. Koin 4.1.1 is introduced now** (D-16; needs approval as a new dependency). This is the first
presenter with a constructor dependency and the skill's feature template requires a Koin module and
`koinInject()`. Hand-wiring it in `:app` would be undone by whichever slice brought Koin. Scope:
`koin-bom` 4.1.1, `koin-core` + `koin-compose` in `:feature:info`, `koin-android` in `:app`,
`BluesJamApp : Application` calling `startKoin` with `appModule` and `infoModule`. **Why not 4.2.2
(latest):** the prototype showed it raises `kotlin-stdlib` to 2.3.20 and Compose runtime/ui to
1.10.4 over the pinned Kotlin 2.2.10 and BOM 2025.09.00 (lifecycle 2.9 → 2.10); 4.1.1 keeps
stdlib 2.2.10 and Compose 1.9.1 (lifecycle 2.9.0 → 2.9.3 only). Upgrade Koin with Kotlin/the BOM.
Lint reports one new `NewerVersionAvailable` warning for `koin-bom`, like the existing six.

**4. Links go through `ExternalLinkOpener`, a contract in `:core:ui`.**
`fun interface ExternalLinkOpener { fun open(url: String): Boolean }` in
`com.bbbjam.core.ui.link` (returns false when no app can open it). `:core:ui`, not `:feature:info`,
because `songsterr-browser-link` in `:feature:song-detail` needs the same thing and cannot import
this feature (D-03). The Android implementation `IntentLinkOpener(context)` lives in `:app`
(`ACTION_VIEW`, `url.toUri()`, `FLAG_ACTIVITY_NEW_TASK` because it holds the application context,
catches `ActivityNotFoundException` → false) and is bound in `appModule` as
`single<ExternalLinkOpener>`. The presenter never touches Android, so the JVM tests assert the exact
URLs through a fake. Not `LocalUriHandler`: reading it in a presenter would tie the presenter to a
UI CompositionLocal and make tests provide it.

**5. `:app` shows Info in place of the placeholder.** `MainActivity` calls
`BluesJamTheme { InfoScreen(contentPadding = WindowInsets.systemBars.asPaddingValues()) }`;
`PlaceholderScreen.kt` and the `placeholder_title` string are deleted. No switch, no nav. `InfoScreen`
takes `modifier`, `contentPadding` and `presenter: InfoPresenter = koinInject()`, and draws the
background edge to edge with the padding inside the scroll. **What `bottom-navigation` changes:**
`MainActivity` hosts the Scaffold with the bar and the three tabs, passes the Scaffold's inner
padding as `contentPadding`, and adds the other feature modules to `startKoin`. `InfoScreen`'s
signature does not change; its `rememberScrollState()` is saveable, so tab state survives.

**6. Copy in Kotlin, not string resources.** The app is Spanish-only with no translation planned
(D-12), the copy must be in the `UiModel` for the assistant, and the presenter must stay
Android-free and JVM-testable. So the copy lives in `internal object InfoCopy` in `:feature:info`
(same precedent as the presenter-pattern example's publish error). `app_name` stays a resource.
Record as a convention in the skill.

**7. No amber.** Info has no primary action, open slot, key, published state or active filter;
using `primaryAction` for links would be amber as decoration (DESIGN.md, D-17). Colors used:
`background`, `surface`, `text` (inherited), `textMuted`, `error`. Nothing from
`MaterialTheme.colorScheme`.

**8. The discreet admin entry, concretely.** Last element of the scroll, centered, after the links
card: the text "Entrar como admin" in `caption` (Chivo 12sp), color `textMuted`, no fill, no border,
no icon, padding `spacing.md` horizontally, height ≥ 48dp, `Role.Button`. Tapping it shows, below it,
in `caption`/`textMuted`: "El ingreso de admin todavía no está habilitado." It is honest, not dead
and not disabled. `admin-passphrase-login` replaces that event handling with opening the login.

**9. 48dp without a literal.** Clickable rows use
`Modifier.heightIn(min = LocalMinimumInteractiveComponentSize.current)` (Material 3, 48dp), not a
feature `48.dp`. No icon-only control exists, so no `contentDescription` is needed; each link row
is one clickable with `Role.Button` and `onClickLabel` "Abrir Instagram" / "Abrir YouTube" /
"Abrir Linktree". A decorative icon, if ever added, gets `contentDescription = null`.

## Copy (user review requested — facts from `docs/info-content.md` only)

| Element | Text |
|---|---|
| Title (`h1`) | Bahía Blanca Blues |
| Tagline (`body`, `textMuted`) | Comunidad de amantes del blues |
| Quién organiza | La jam la organiza Bahía Blanca Blues, una comunidad de amantes del blues que también arma festivales, conciertos y programas de radio. |
| La jam | Una jam de blues en Bahía Blanca, una vez por mes. El lugar puede cambiar de una jam a otra: lo ves junto a la fecha de cada una. |
| Hideaway | Hideaway es el programa de radio de Bahía Blanca Blues. Sale los martes de 20 a 22. |
| Cómo sumarte | Si tocás, venite a la jam y anotate ahí: la organización te suma a un tema. No necesitás cuenta ni registrarte en la app. |
| Redes (card title) | Redes |
| Link rows (label / destination) | Instagram / @bahiablancablues · YouTube / youtube.com · Linktree / linktr.ee/bahiablancablues |
| Link error (`caption`, `error`, tap to dismiss) | No se pudo abrir el enlace. |
| Admin entry / notice | Entrar como admin / El ingreso de admin todavía no está habilitado. |

URLs (`enum class SocialLink(val url: String)`): `https://www.instagram.com/bahiablancablues/`,
`https://www.youtube.com/channel/UCayS6srPr0FQ2FF4XoEZ-8w` (www form of the Linktree link),
`https://linktr.ee/bahiablancablues`. YouTube's destination shows the host only because the channel
name is not in `info-content.md`. "lo ves junto a la fecha de cada una" is true once the next-jam
header exists; until then the app shows only Info.

## Layout (from the Stitch `info_club_y_comunidad` screen, layout only)

`Column` with `verticalScroll`, `background` color, `contentPadding`, then `spacing.md` horizontal /
`spacing.lg` vertical padding, `spacedBy(spacing.md)`. Header: title + tagline. Then four section
cards and one links card: `Surface(color = surface, shape = shapes.md)`, inner padding `spacing.md`,
`spacedBy(spacing.sm)`, heading in `songTitle` (the only 24sp heading style; the name mismatch is
noted, no new token). Link row: label in `body`, destination in `caption`/`textMuted`. Then the admin
entry (Decision 8). Stitch's hero photo, map, venue, WhatsApp, "Fede", backline notes, lock icon and
version footer are rejected (bitácora 6.5, D-19).

## Acceptance Scenarios

1. **Content.** Given a fresh install, when the app launches, then Info shows the title, tagline,
   the four sections, the three links and "Entrar como admin", exactly the copy above, with no venue,
   address, station or amber.
2. **Links.** When each link row is tapped, then the opener receives exactly its URL (JVM test), and
   on the Pixel 5 an `ACTION_VIEW` intent for that host starts and the page or app opens; back
   returns to Info.
3. **Link failure.** Given the opener returns false, when a link is tapped, then "No se pudo abrir el
   enlace." appears, and tapping it removes it (JVM test).
4. **Admin entry.** When "Entrar como admin" is tapped, then the notice appears below it; nothing
   else changes (JVM test + device).
5. **Architecture.** `:feature:info` depends only on `:core:ui` (project-wise); Konsist's feature
   rules now check a real module; `CI=true ./init.sh` exits 0 with three `wired`.

## Repository Research

### Files Inspected

`AGENTS.md`, `PROGRESS.md`, `feature_list.json` (`info-screen`, `bottom-navigation`,
`admin-passphrase-login`), `docs/info-content.md`, bitácora D-12, D-15–D-19 and 6.5, `DESIGN.md`,
`bb-blues-jam-design-prompt.md` §6, `docs/design/screens/info.png` and
`docs/design/export/stitch_bb_blues_jam_app/info_club_y_comunidad/code.html`,
`.claude/skills/architecture/SKILL.md` + `references/presenter-pattern.md`, `settings.gradle.kts`,
root/`app`/`core/ui` build files, `gradle/libs.versions.toml`, `MainActivity.kt`,
`PlaceholderScreen.kt`, `AndroidManifest.xml`, `strings.xml`, `init.sh`, `ModuleIsolationTest.kt`,
`BluesJamColors.kt`, `BluesJamDimens.kt`, `ContrastTest.kt`, `.editorconfig`, `config/detekt/detekt.yml`.

### Prototype (throwaway clone in the session scratchpad, not the repo)

The whole design above, built on a clone of `9b148f9`:

- `CI=true ./init.sh` exit 0, `konsist: wired` (10/10), `detekt: wired`, `ktlint: wired`;
  `InfoPresenterTest` 3/3, `InfoModuleTest` 1/1. ktlint first failed on
  `blank-line-between-when-conditions` in the presenter; `./gradlew ktlintFormat` fixed it.
- Lint: `UseKtx` on `Uri.parse` (fixed with `toUri()`); otherwise only version warnings.
- Konsist non-vacuous: a probe file in `:feature:info` importing `com.bbbjam.MainActivity`,
  `com.bbbjam.feature.nextjam.NextJamScreen` and holding `Color(0xFF123456)` made
  `:konsist-test:test` report 10 tests, 3 failed: `feature-imports-app`, `feature-imports-feature`,
  `no-color-literal-outside-core-ui`. Removed; 10/10 again.
- Dependency trees for Koin 4.2.2 vs 4.1.1 as in Decision 3.
- **Not prototyped:** the device run (the Pixel 5 was not attached to adb during planning), so
  `koinInject()` resolving from `startKoin`'s global context and the intent/back behavior are
  verified by the implementer only.

## Expected File Changes

- `settings.gradle.kts` — `include(":feature:info")`.
- `gradle/libs.versions.toml` — `koin = "4.1.1"`; `koin-bom` (version.ref), `koin-core`,
  `koin-android`, `koin-compose` (BOM-managed, no version).
- `feature/info/.gitignore` (`/build`), `feature/info/build.gradle.kts` — `android-library` +
  `kotlin-compose`, namespace `com.bbbjam.feature.info`, SDKs from the catalog, Java 11,
  `compose = true`, `unitTests.isReturnDefaultValues = true`; `implementation(project(":core:ui"))`,
  koin BOM/core/compose, `androidx.compose.foundation`, tooling-preview (+ debug tooling); test:
  junit, molecule, turbine, coroutines-test.
- `feature/info/src/main/kotlin/com/bbbjam/feature/info/` — `SocialLink.kt`, `InfoUiModel.kt`
  (`InfoUiModel` with `title`, `tagline`, `sections`, `linksTitle`, `links`, `linkError`,
  `adminEntry`, `events`; `Event` = `OpenLink(link: SocialLink)`, `DismissLinkError`,
  `AdminEntryTapped`; `InfoSectionUiModel(title, body)`, `InfoLinkUiModel(link, label, destination,
  openLabel)`, `AdminEntryUiModel(label, notice)`), `InfoCopy.kt`, `InfoPresenter.kt`
  (`Presenter<InfoUiModel, Unit>`, constructor `ExternalLinkOpener`, `remember` for `linkError` and
  the notice), `InfoScreen.kt` (with a `@Preview` passing `InfoPresenter { true }`),
  `di/InfoModule.kt` (`val infoModule = module { factory { InfoPresenter(get()) } }`).
- `feature/info/src/test/kotlin/com/bbbjam/feature/info/` — `InfoPresenterTest.kt`,
  `InfoModuleTest.kt`.
- `core/ui/src/main/kotlin/com/bbbjam/core/ui/link/ExternalLinkOpener.kt` — create.
- `app/build.gradle.kts` — `implementation(project(":feature:info"))`, koin BOM + `koin-android`.
- `app/src/main/java/com/bbbjam/BluesJamApp.kt`, `link/IntentLinkOpener.kt`, `di/AppModule.kt` —
  create. `AndroidManifest.xml` — `android:name=".BluesJamApp"`. `MainActivity.kt` — Decision 5.
- `app/src/main/java/com/bbbjam/PlaceholderScreen.kt` — delete; `strings.xml` — drop
  `placeholder_title`.

## Visual Design Impact

- UI involved: yes. Sources: `DESIGN.md` (tokens, amber rules, 48dp, accessibility),
  design prompt §6, Stitch `info.png` for layout only.
- Screens: Info (default state), with the link-error and admin-notice states. No loading, empty or
  offline state: the content is static.
- No new design artifact or token.

## Durable Documentation Impact

- `.claude/skills/architecture/SKILL.md` — update: build-logic bullet (Decision 1); `:core:ui` row
  gains `ExternalLinkOpener`; `:app` row gains `BluesJamApp`, `appModule`, `IntentLinkOpener`; a
  Koin bullet (4.1.1 and why not 4.2; `koin-core` + `koin-compose` in features, `koin-android` in
  `:app`); copy-in-Kotlin convention (Decision 6); 48dp via `LocalMinimumInteractiveComponentSize`;
  `feature/info/build.gradle.kts` as the reference feature build file.
- `references/presenter-pattern.md` — update: name `InfoPresenter`/`InfoPresenterTest` as the first
  compiled feature example.
- `PROGRESS.md`, `feature_list.json` — update (evidence, status `passing`).
- `ARCHITECTURE.md`, `CONSTRAINTS.md` — not present; the skill is the architecture doc. `AGENTS.md`,
  `DESIGN.md`, `CONTEXT.md`, `docs/info-content.md` — not needed.

## Implementation Plan

1. Baseline `CI=true ./init.sh`.
2. Catalog, `settings.gradle.kts`, `ExternalLinkOpener`.
3. `:feature:info`: build file, model, copy, presenter, tests (red, then green), screen, Koin module.
4. `:app`: dependencies, `BluesJamApp`, manifest, `IntentLinkOpener`, `appModule`, `MainActivity`;
   delete the placeholder.
5. `./gradlew ktlintFormat`, then `CI=true ./init.sh`.
6. Negative demonstrations, device check, docs, evidence.

## Verification Plan

- `CI=true ./init.sh` exit 0; `konsist: wired`, `detekt: wired`, `ktlint: wired`.
- `InfoPresenterTest` (Molecule + Turbine, JVM, fake opener): (a) opening each link passes exactly
  the three URLs above, in order; (b) a failing opener sets `linkError` to the copy, `DismissLinkError`
  clears it (transition asserted); (c) `AdminEntryTapped` moves `adminEntry.notice` from null to the
  notice. Plus (d) the first model equals a full expected `InfoUiModel` built from the copy table
  with `EventHandler {}`, so a copy change fails a test. `InfoModuleTest`: `koinApplication` with
  `infoModule` + a fake opener module resolves `InfoPresenter`.
- Negative demonstrations (each restored, recorded): a wrong YouTube URL (m. form) fails (a);
  `linkError` never set fails (b); a probe import of `com.bbbjam.MainActivity` in `:feature:info`
  fails `feature-imports-app` in `:konsist-test:test`.
- Greps (validator repeats): in `feature/info/src/main` no `MaterialTheme.colorScheme`, no
  `primaryAction|slotOpen|key\b|published|activeFilter` color reads, no `dp` literal, no `tú`
  forms (`puedes|tienes|quieres|eres|ven\b`), no `Motivo|Macanuda|Moreno|dirección|FM|AM\b`.
- Device (Pixel 5, `adb -s 09281FDD4004U6`; manual, outside the gate): `./gradlew :app:installDebug`,
  `adb shell am start -n com.bbbjam/.MainActivity`, `adb logcat -c`, then for each link tap it and
  record `adb logcat -d -s ActivityTaskManager | grep "act=android.intent.action.VIEW"` (the `dat=`
  shows scheme and host; the full URL is proven by the JVM test) and the foreground app from
  `adb shell dumpsys activity activities | grep -E "topResumedActivity|ResumedActivity"`; press back
  and confirm Info returns. Tap the admin entry; `adb exec-out screencap -p > info.png` before and
  after, scrolled to show the entry. Check with TalkBack or `uiautomator dump` that each link node
  is clickable with its label. AndroidRuntime logcat empty.

## Evidence To Capture

Gate output with three `wired`; test counts per class; each negative demonstration and its failing
test; dependency-tree lines for stdlib and Compose runtime; logcat VIEW lines per host; screenshots
(top, bottom with notice); the copy as shipped. In `feature_list.json` → `info-screen` and
`PROGRESS.md`.

## Validator Checklist

- [ ] No venue, address, station, festival, WhatsApp or other fact beyond `docs/info-content.md`.
- [ ] Copy matches the table (or the user's approved revision) and uses *vos*.
- [ ] `InfoPresenter` holds all logic; `InfoScreen` only renders and forwards events.
- [ ] `:feature:info` project dependency is `:core:ui` only; Konsist 10/10, three `wired`.
- [ ] No amber role, no `MaterialTheme.colorScheme`, no color or dp literal in `:feature:info`.
- [ ] Every clickable ≥ 48dp with a role; links have `onClickLabel`.
- [ ] Koin is 4.1.1; stdlib stays 2.2.10 and Compose runtime 1.9.1 on `:app` debugRuntimeClasspath.
- [ ] Device evidence for all three links and the admin notice; back returns to Info.
- [ ] Skill updated per Durable Documentation Impact; no `build-logic`, no nav, no `:core:data` code.

## Needs User Approval Before Implementation

- **New dependency: Koin 4.1.1** (Decision 3), and introducing `startKoin` in this slice.
- **The copy table**, in particular the Hideaway schedule (from an automated Instagram summary),
  "la organización te suma a un tema", and the admin notice wording.
- **Convention: UI copy in Kotlin, not string resources** (Decision 6).
- **Behavior before login:** the admin entry shows a "not enabled yet" notice (Decision 8).
- Recommendation for the orchestrator: add a `build-logic-conventions` feature before
  `next-jam-read-only-list` (Decision 1).
