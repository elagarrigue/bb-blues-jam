# Feature Implementation Spec: Raise the Kotlin toolchain to 2.3 for phase 2

## Source Feature

- `id`: kotlin-2-3-toolchain-upgrade
- `area`: build
- `depends_on`: strip-extra-chip-filled (`accepted`)
- `status`: not_started
- `source`: `feature_list.json`; context `docs/phase-2-brief.md`, D-21 in `bb-blues-jam-bitacora.md`

## Goal

Compile the whole build with Kotlin **2.3.21** (KGP, compiler, Compose compiler and serialization
plugins), and raise the libraries that were held back only to keep `kotlin-stdlib` at 2.2.10, so
the next slice (`assistant-agent-core`) can add `ai.koog:koog-agents:1.3.0` without moving the
compiler. No behavior changes: every screen renders as before and the gate stays as strict as today.

## Non-Goals

- **Do not add Koog, Ktor, kotlin-logging or any new library.** That is `assistant-agent-core`.
- Do not raise the Compose BOM (2025.09.00), Koin (4.1.1), Navigation (2.9.8), activity-compose,
  Molecule, Turbine or coroutines. Their remaining reason is the Compose 1.9 line, not the stdlib
  (Koin 4.2 and Navigation 2.10 pull Compose 1.10). A Compose upgrade is its own slice.
- Do not upgrade AGP (9.4.1), Gradle (9.6.0), the daemon JDK (25), detekt, ktlint or Konsist unless
  a gate failure forces it (see Risks); never Kotlin 2.4.x.
- Do not change source code beyond what the new compiler strictly requires; no refactors.
- No change to `init.sh`, `config/detekt/detekt.yml`, `.editorconfig` or any Konsist rule.

## Job Story

When the phase 2 assistant slice adds Koog 1.3.0 (which requires `kotlin-stdlib`/`kotlin-bom`
2.3.10 and kotlinx-serialization 1.10.0),
I want the build to already compile with Kotlin 2.3 and serialization at or above 1.10,
so that adding Koog moves no compiler and no version backwards.

## Users And Permissions

- No user-facing change. Musicians and admin see the same app. No writes are involved.

## Acceptance Scenarios

### Scenario 1: one Kotlin 2.3.21 everywhere
Given the catalog sets `kotlin = "2.3.21"`
When `./gradlew -q buildEnvironment` runs
Then exactly one `org.jetbrains.kotlin:kotlin-gradle-plugin` version is on the root classpath,
2.3.21 (AGP's own 2.2.10 request shows as `2.2.10 -> 2.3.21`), and AGP stays 9.4.1.

### Scenario 2: runtime stdlib is 2.3.x, nothing forced down
Given the upgraded catalog
When `./gradlew :app:dependencyInsight --configuration debugRuntimeClasspath --dependency org.jetbrains.kotlin:kotlin-stdlib`
(and the same for `releaseRuntimeClasspath`) runs
Then the selected version is 2.3.21, selected by conflict resolution or by request, never "forced"
or "by constraint" to a lower one; no `force`, `strictly`, `resolutionStrategy` or
`exclude` was added to any build file.

### Scenario 3: gate green with the same rules
Given the upgrade
When `CI=true ./init.sh` runs
Then it exits 0, prints `konsist: wired`, `detekt: wired`, `ktlint: wired`, and the JVM test count
is at least the pre-slice count (698 at the last recorded run; re-measure before changing anything).

### Scenario 4: no rule loosened
Given the diff of the slice
When the validator reads it
Then `config/detekt/detekt.yml`, `.editorconfig`, `init.sh`, `konsist-test/src/**` are unchanged,
no `detekt-baseline*.xml`/ktlint baseline exists, no `@Suppress` was added, and no
`ignoreFailures`/`allWarningsAsErrors=false`/`lint { abortOnError = false }` was introduced.

### Scenario 5: device renders the three tabs (debug demo jam)
Given a debug build with `bluesjam.demoUpcomingJam=true` in `local.properties`
When the app is installed on a device and each tab is opened
Then Próxima jam shows the demo jam list, Jams pasadas shows the cached past jams, Info renders; a
song detail opens from Próxima jam. No admin control is tapped, nothing posts to Apps Script.

## Repository Research

### Files Inspected

- `gradle/libs.versions.toml` — single `kotlin` key drives KGP, `kotlin-jvm`, `kotlin-compose`,
  `kotlin-serialization` plugins and build-logic's `kotlin-gradlePlugin`; pin comments on KSP,
  OkHttp, serialization, Navigation, DataStore.
- `build.gradle.kts` (root) — all plugins `apply false` from the catalog; detekt/ktlint applied to
  every Kotlin module. Putting `kotlin-jvm` 2.3.21 here is what overrides AGP's KGP 2.2.10.
- `build-logic/convention/build.gradle.kts`, `build-logic/settings.gradle.kts` — AGP and KGP
  `compileOnly` from the shared catalog; `kotlin-dsl` uses Gradle 9.6's embedded Kotlin (2.3.21 per
  `docs/specs/module-skeleton.md`), so a 2.3.21 KGP compileOnly is readable; a 2.4.x KGP might not be.
- `build-logic/convention/src/main/kotlin/com/bbbjam/buildlogic/*.kt` — Android modules use AGP 9
  built-in Kotlin (no `org.jetbrains.kotlin.android`); `JvmLibraryConventionPlugin` applies
  `org.jetbrains.kotlin.jvm` with `compilerOptions { jvmTarget }` (no deprecated `kotlinOptions`).
- `app/build.gradle.kts`, `core/data/build.gradle.kts`, `konsist-test/build.gradle.kts`,
  `core/model/build.gradle.kts` — no Kotlin version or compiler flags of their own.
- `gradle.properties`, `gradle/wrapper/gradle-wrapper.properties` (Gradle 9.6.0),
  `gradle/gradle-daemon-jvm.properties` (JDK 25). No `allWarningsAsErrors`/`freeCompilerArgs` anywhere.
- `init.sh` — gate and tool-wiring report (unchanged by this slice).
- `.claude/skills/architecture/SKILL.md` (Build Conventions, Data stack/Navigation/DataStore/Koin
  pins), `docs/technical-discovery.md` lines 36-47, `docs/specs/catalog-repository-cache.md`,
  `docs/specs/detekt-ktlint-gate.md`, `docs/specs/gradle-kotlin-compose-baseline.md`,
  `docs/phase-2-brief.md`, bitácora D-21.
- Maven Central / Google Maven / Plugin Portal metadata, queried 13 Oct 2026 (findings below).
- Local Gradle cache: `kotlin-gradle-plugin` 2.2.10 and 2.3.21 are both already present.

### Findings (queried, not assumed)

| Artifact | Fact |
|---|---|
| Kotlin 2.3.x | Stable: 2.3.0, 2.3.10, 2.3.20, **2.3.21** (latest 2.3). 2.4.21 exists; out of scope. |
| `ai.koog:koog-agents:1.3.0` | Imports `kotlin-bom` 2.3.10, needs `kotlin-stdlib` 2.3.10, `kotlinx-serialization-json` 1.10.0, coroutines 1.10.2, Ktor 3.3.3, kotlinx-io 0.9.0. |
| AGP 9.4.1 | Depends on `kotlin-gradle-plugin` and `kotlin-stdlib` 2.2.10. |
| kotlinx-serialization 1.10.0 / 1.11.0 | Need stdlib 2.3.0 / 2.3.20. 1.11.0 is the latest stable. |
| OkHttp 5.3.2 / 5.5.0 | Need stdlib 2.2.21; 5.5.0 needs okio 3.18.1. 5.5.0 is latest. |
| KSP | 2.3.12 is the latest; KSP2 is decoupled from the Kotlin version. Keep. |
| Room | 2.8.5 exists (compiler stdlib 2.2.0). Keep 2.8.4 unless KSP/Room fails. |
| Konsist | 0.17.3 embeds compiler 2.0.21; 0.18.1 embeds 2.4.21 (would put a 2.4 stdlib in `:konsist-test`). Keep 0.17.3. |
| detekt / ktlint-gradle / ktlint | 2.0.0-alpha.6 / 14.2.0 / 1.8.0 are still the latest. They parse in their own workers (detekt's compiler 2.4.10, ktlint's 2.2.21), independent of the project compiler. |

### Existing Patterns To Follow

- Versions only in `gradle/libs.versions.toml`, with a comment stating why a version is held.
- One KGP and one AGP in `buildEnvironment` (architecture skill, Build Conventions).
- Upgrade proofs in earlier specs used `dependencyInsight` / `dependencies` diffs on `:app`'s
  debug and release runtime classpaths; repeat that.

### Current Gaps

- No automated check that `kotlin-stdlib` matches the compiler; the evidence is manual output.
- No Compose UI test harness (user decision); rendering is verified on a device.

## Technical Approach

**How Kotlin is set with AGP 9 built-in Kotlin.** AGP 9.4.1 brings KGP 2.2.10 as a runtime
dependency. The root `build.gradle.kts` already declares `alias(libs.plugins.kotlin.jvm)`,
`kotlin.compose` and `kotlin.serialization` with `apply false`, all on `version.ref = "kotlin"`, so
they land on the same root classpath as AGP and Gradle picks the highest KGP. Raising the single
`kotlin` key to 2.3.21 therefore moves built-in Kotlin (Android modules), `kotlin.jvm`
(`:core:model`, `:konsist-test`), the Compose compiler plugin, the serialization plugin, and
build-logic's `compileOnly` KGP together. No build-logic code and no module build file changes.
Do **not** add `org.jetbrains.kotlin.android`, a `buildscript {}` block or `kotlin.compiler.version`
properties. `kotlin-stdlib` follows the compiler through KGP's default stdlib dependency.

**Version moves (catalog only):**

| Key | From | To | Reason |
|---|---|---|---|
| `kotlin` | 2.2.10 | **2.3.21** | Latest 2.3 patch; satisfies Koog's 2.3.10; equals Gradle 9.6's embedded Kotlin used by `kotlin-dsl` in build-logic. |
| `kotlinxSerialization` | 1.9.0 | **1.11.0** | Koog needs ≥ 1.10.0; 1.11.0 needs stdlib 2.3.20 ≤ 2.3.21. |
| `okhttp` (and mockwebserver3 via the same ref) | 5.1.0 | **5.5.0** | Pin was stdlib-only; needs stdlib 2.2.21. Fallback 5.3.2 if 5.5.0 breaks a test, recorded in PROGRESS. |
| `ksp` | 2.3.12 | 2.3.12 | Already latest; update the comment only. |
| `room`, `datastore`, `navigationCompose`, `koin`, `composeBom` | — | unchanged | Rewrite their comments: the stdlib reason is gone; what holds them now is the Compose 1.9 line (Koin, Navigation) or nothing urgent (Room, DataStore). |

Rewrite the `# Data stack` comment block so it no longer claims stdlib is held at 2.2.10; state
that `kotlin` is the single source for KGP/compiler/plugins, that 2.3.21 is chosen to match Gradle's
embedded Kotlin and Koog's 2.3.10 floor, and that 2.4.x waits for a Gradle whose embedded Kotlin can
read it.

**If the new compiler reports new errors** in project code, fix the code minimally (no suppressions)
and list each change in PROGRESS. New warnings are not errors here (no `allWarningsAsErrors`); do
not silence them with flags.

**Risks the implementer must check, in this order, and how to react:**
1. *AGP built-in Kotlin with a newer KGP.* Expected to work (higher KGP on classpath is the
   supported override). If AGP refuses it, stop and report; do not switch to the KGP Android plugin.
2. *KSP 2.3.12 + Room 2.8.4 under KGP 2.3.21.* If `:core:data:kspDebugKotlin` fails, try Room
   2.8.5; if still failing, stop and report.
3. *Compose compiler 2.3.21 against Compose runtime 1.9.1 (BOM 2025.09.00).* Expected compatible.
   A failure here means the BOM must move: stop and report, do not bump the BOM in this slice.
4. *detekt 2.0.0-alpha.6, ktlint 1.8.0, Konsist 0.17.3* parse sources with their own embedded
   compilers; unchanged sources mean they should pass. If one fails on the new KGP (for example a
   detekt Gradle plugin/KGP API mismatch), report the exact error; a tool bump is allowed only to the
   versions above (none newer exists except Konsist 0.18.1, which needs the user's approval because
   it puts a 2.4 stdlib in `:konsist-test`).
5. *Android lint 9.4.1 reading 2.3 metadata.* If lint fails with a metadata/version error, report;
   never set `abortOnError = false` or add a lint baseline.
6. *Configuration cache.* The first run after the catalog edit misses it; the second must reuse it.

## Expected File Changes

- `gradle/libs.versions.toml` — modify: `kotlin`, `kotlinxSerialization`, `okhttp`; rewrite the pin
  comments (Data stack, Navigation, DataStore) as described.
- Source files under `app/`, `core/`, `feature/` — only if the 2.3 compiler rejects them; none expected.
- `.claude/skills/architecture/SKILL.md` — modify the Data stack pins, Navigation pin, DataStore
  pin and Koin bullets: Kotlin 2.3.21, stdlib 2.3.21, serialization 1.11.0, OkHttp 5.5.0, and that
  the remaining holds are about Compose 1.10.
- `docs/technical-discovery.md` — modify lines ~36-47 to the new versions and reasons.
- `feature_list.json`, `PROGRESS.md` — evidence and status.
- `docs/evidence/kotlin-2-3-toolchain-upgrade/` — create: before/after dependency outputs and screenshots.

## Visual Design Impact

- UI involved: no (rendering must be unchanged). Design source: not applicable.
- Screens checked on device: Próxima jam, Jams pasadas, Info, one song detail.
- New design artifact required: no.

## Durable Documentation Impact

- `ARCHITECTURE.md` / `CONSTRAINTS.md`: not present in this repo; not needed — the architecture
  skill is the durable architecture doc and is updated as above.
- `AGENTS.md`: not needed — workflow and rules unchanged.
- `.claude/skills/architecture/SKILL.md`, `docs/technical-discovery.md`: update (version facts).
- Bitácora: not needed (D-21 already records the decision). Old specs stay as historical records.

## Implementation Plan

1. Baseline: run `CI=true ./init.sh`, record exit code and the JVM test count (sum `tests=` over
   `**/build/test-results/**/TEST-*.xml`). Save `./gradlew -q buildEnvironment` and
   `./gradlew :app:dependencies --configuration debugRuntimeClasspath` (and `releaseRuntimeClasspath`,
   `:konsist-test:dependencies --configuration testRuntimeClasspath`) to
   `docs/evidence/kotlin-2-3-toolchain-upgrade/before-*.txt`.
2. Edit the catalog (versions + comments).
3. `./gradlew build` and fix only what the compiler forces; walk the Risks list on any failure.
4. Run `CI=true ./init.sh` twice (second must reuse the configuration cache) and record the result.
5. Capture the same outputs as `after-*.txt`; run the `dependencyInsight` commands below.
6. Device check with the demo jam; screenshots.
7. Update the architecture skill, technical discovery, PROGRESS, feature_list.

## Implementation Tasks

- [ ] T1 Baseline gate, test count, before-outputs saved.
- [ ] T2 `kotlin = "2.3.21"`, `kotlinxSerialization = "1.11.0"`, `okhttp = "5.5.0"`; comments rewritten.
- [ ] T3 `./gradlew build` green; any forced source fix listed.
- [ ] T4 `CI=true ./init.sh` exit 0, three tools `wired`, test count ≥ baseline.
- [ ] T5 After-outputs and `dependencyInsight` evidence saved; diff summarized in PROGRESS.
- [ ] T6 Device check (demo jam, read-only), screenshots saved; `local.properties` restored byte-for-byte.
- [ ] T7 Docs updated (architecture skill, technical-discovery); status `passing` with evidence.

## Verification Plan

- `CI=true ./init.sh` — exit 0; `konsist: wired`, `detekt: wired`, `ktlint: wired`.
- `./gradlew -q buildEnvironment | grep -E "kotlin-gradle-plugin|com.android.tools.build:gradle:"` —
  one KGP resolved at 2.3.21, AGP 9.4.1.
- `./gradlew :app:dependencyInsight --configuration debugRuntimeClasspath --dependency org.jetbrains.kotlin:kotlin-stdlib`
  and the same on `releaseRuntimeClasspath` — 2.3.21.
- Same command for `kotlinx-serialization-json` (1.11.0), `com.squareup.okhttp3:okhttp` (5.5.0),
  `androidx.compose.runtime:runtime` (1.9.1, unchanged) and `androidx.lifecycle:lifecycle-runtime`
  (unchanged from before).
- `diff before-app-debugRuntimeClasspath.txt after-app-debugRuntimeClasspath.txt` — only Kotlin,
  serialization, OkHttp/okio lines move up; nothing moves down; no Compose/lifecycle/Koin change.
- `:konsist-test:dependencies --configuration testRuntimeClasspath` — Konsist 0.17.3 still resolves;
  record the stdlib it gets.
- `git diff --stat` shows no change to `init.sh`, `config/`, `.editorconfig`, `konsist-test/src`.
- Device (manual, not part of the gate): `./gradlew :app:installDebug` with
  `bluesjam.demoUpcomingJam=true` (set temporarily if absent, restore afterwards); open Próxima jam,
  a song detail, Jams pasadas, Info. No admin action, no Sheet write, no new Sheet data.
- No E2E harness exists; JVM tests plus the device check cover this no-behavior-change slice.

## Evidence To Capture

- Baseline and final test counts; `init.sh` tool lines and exit codes (both runs).
- `before-*.txt` / `after-*.txt` dependency and buildEnvironment outputs, plus `dependencyInsight`
  results, in `docs/evidence/kotlin-2-3-toolchain-upgrade/`.
- List of any source edits the compiler forced, and any fallback taken from the Risks list.
- Screenshots of the three tabs and a song detail; `local.properties` md5 before/after.

## Validator Checklist

- [ ] Only `kotlin`, `kotlinxSerialization`, `okhttp` versions changed (plus a fallback recorded with its reason); no Koog/Ktor or other new dependency.
- [ ] Pin comments in the catalog are accurate for the new versions.
- [ ] Rerun `CI=true ./init.sh`: exit 0, three tools `wired`, test count ≥ baseline.
- [ ] `buildEnvironment` shows one KGP at 2.3.21; stdlib 2.3.21 on `:app` debug and release runtime classpaths; nothing forced down.
- [ ] No detekt/ktlint/lint/Konsist rule disabled, baselined or suppressed; `init.sh` unchanged.
- [ ] Compose BOM, Koin, Navigation, DataStore, Room (unless fallback), AGP, Gradle unchanged.
- [ ] Device screenshots present, taken read-only with the demo jam.
- [ ] Architecture skill and technical-discovery updated; `feature_list.json` and `PROGRESS.md` record the evidence; status `passing`, not `accepted`.
