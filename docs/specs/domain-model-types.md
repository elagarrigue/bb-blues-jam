# Feature Implementation Spec: Define the domain types in :core:model

## Source Feature

- `id`: domain-model-types
- `area`: domain
- `depends_on`: module-skeleton (`accepted`)
- `status`: not_started (at planning time, 28 September 2026, HEAD `3ca110e`)
- `source`: `feature_list.json`

## Goal

`:core:model` holds the domain types every later slice builds on — `Jam`, `JamStatus`, `JamSong`,
`Lineup`, `Slot`, `Instrument`, `Song`, `Tempo`, `Difficulty`, `Key`, `SongId` — as pure Kotlin with
the rules that make them meaningful: open vs filled slot, the default lineup of seven slots, an
adjustable lineup per JamSong, the key format of `docs/sheet-schema.md`, gap-free setlist positions,
and "historical" derived from the date against an injected `today`. Unit tests cover each rule, and
a Konsist rule keeps the system clock out of the model. The `CoreModelMarker` scaffolding goes away.

## Non-Goals

- No mapping from the Sheet or JSON: no kotlinx.serialization, no Spanish header or status parsing
  (`BORRADOR`/`PUBLICADA`, `-` cells, `lento`…). That is the mapper in `catalog-repository-cache`.
- No repository, no mutation functions (assign, clear, reorder, publish…): each is its own slice
  with a `:core:data` repository function (D-13). Pure `copy()` suffices until then.
- No "at most one upcoming jam" check (a collection rule, see Decision 6), no duplicate-song check.
- No Android, Compose, Koin, `@Immutable`/`@Stable` or any annotation from a framework in `:core:model`.
- No core library desugaring and no `minSdk` change (Decision 8). No `:core:data`, `:core:ui` or
  feature code. No change to `init.sh`, detekt/ktlint config, root build file; no suppressions.

## Job Story

When a later slice maps the Sheet, draws the setlist or performs an admin mutation,
I want one tested vocabulary of domain types whose invalid states cannot be built,
so I can write mappers, presenters and repositories without re-deciding what a slot or a key is.

## Users And Permissions

No end-user behavior and no writes; D-13 does not apply. Types carry no admin notion.

## Types To Create

All in package `com.bbbjam.core.model`, one top-level type per file named after it, under
`core/model/src/main/kotlin/com/bbbjam/core/model/`. Every `require` throws
`IllegalArgumentException` with a message naming the bad value.

```kotlin
enum class JamStatus { DRAFT, PUBLISHED }                  // no ARCHIVED
enum class Instrument { GUITAR, BASS, DRUMS, VOCALS, HARMONICA, KEYBOARDS }
enum class Tempo { SLOW, MEDIUM, FAST }                     // Sheet: lento, medio, rápido
enum class Difficulty { EASY, MEDIUM, HARD }                // Sheet: fácil, media, difícil

@JvmInline value class Key(val value: String)       // init: require(Regex("[A-G][#b]?m?").matches(value))
    // val isMinor: Boolean; toString() = value; companion fun parseOrNull(text: String): Key?
@JvmInline value class SongId(val value: String)    // init: require(Regex("[a-z0-9]+(-[a-z0-9]+)*").matches(value))
    // toString() = value; companion fun parseOrNull(text: String): SongId?

data class Slot(val instrument: Instrument, val musicianName: String? = null)
    // init: require(musicianName == null || musicianName.isNotBlank())
    // val isOpen = musicianName == null; val isFilled = !isOpen
data class Lineup(val slots: List<Slot>)
    // val openSlots: List<Slot>; fun hasOpenSlotFor(instrument: Instrument): Boolean
    // companion: val DEFAULT_INSTRUMENTS = [GUITAR, GUITAR, BASS, DRUMS, VOCALS, HARMONICA, KEYBOARDS]
    //            fun default(): Lineup  (seven open slots, that order = Sheet column order)
data class JamSong(val position: Int, val songId: SongId, val title: String, val artist: String,
                   val key: Key, val lineup: Lineup)      // init: require(position >= 1); no defaults
data class Jam(val date: LocalDate, val startTime: LocalTime, val venue: String,
               val status: JamStatus, val setlist: List<JamSong>)
    // init: require(setlist.map { it.position } == (1..setlist.size).toList())
    // fun isHistorical(today: LocalDate): Boolean = date.isBefore(today)
data class Song(val id: SongId, val title: String, val artist: String, val defaultKey: Key,
                val tempo: Tempo? = null, val tags: List<String> = emptyList(),
                val difficulty: Difficulty? = null, val songsterrId: Long? = null,
                val mbid: String? = null, val artistArea: String? = null,
                val deezerTrackId: Long? = null, val previewUrl: String? = null,
                val artworkUrl: String? = null)
```

This shape was compiled and passed the full gate on a throwaway clone (see Repository Research).

## Decisions

1. **Key is a validated value class on `JamSong`; `Song` has `defaultKey`** (D-08). The regex is
   exactly the schema's **Keys** rule. No trimming: the mapper trims and calls `parseOrNull` to
   report a cell error without catching exceptions. `E#`, `Cb` pass, as the schema's format allows.
2. **`SongId` validates the schema's lowercase slug.** Jam tabs reference it, so a malformed id
   must fail at the edge. The accent-free seed ids (`cafe-madrid`) all match.
3. **`Instrument` has six values; the lineup has seven slots.** "2 guitars" is two `GUITAR` slots,
   not `GUITAR_1`/`GUITAR_2`: the domain says "which instruments and how many of each". Slot order
   in `Lineup.slots` is kept, so a mapper can pair `Guitarra 1`/`Guitarra 2` with the first and
   second guitar slot. `KEYBOARDS`, not `KEYS`, per CONTEXT.md ("keyboards") and to avoid
   confusion with `Key`.
4. **`Lineup` is a type, not a bare `List<Slot>`.** It is a glossary term, and the "where can I
   play" rule (`openSlots`, `hasOpenSlotFor`) needs one home that the filter chips and the
   instrument strip will share. It places no cap on counts and allows zero slots of an instrument
   (domain edge case) or an empty lineup. The seven-column limit is a Sheet limit (schema: "outside
   the default seven is out of scope for the MVP") enforced by the mapper — see Open Question A.
5. **A blank `musicianName` is rejected.** Otherwise `""` would be "filled" by the type and "open"
   in the Sheet (empty cell). Open/filled is the product's most important fact, so it may not
   depend on whitespace. The mapper maps an empty cell to `null`.
6. **Invariants in the types vs. later slices.** In the types: key format, id format, non-blank
   name, `position >= 1`, and setlist positions exactly `1..n` in list order (`Jam.init`). The last
   one makes a gap or a duplicate unrepresentable, so reorder/remove repositories must renumber, and
   a mapper must sort by `posicion` before building a `Jam` (the schema says row order is not
   trusted). **Not** in the types: "at most one upcoming jam" (a property of the jam collection, not
   of one `Jam` — it belongs to the `Jams` mapper/repository in `catalog-repository-cache` and to
   Apps Script); "required" Sheet cells such as blank title/venue (the mapper reports them per
   cell); catalog existence of `songId` (needs the catalog).
7. **`Jam` has no `id` field; `date` is its identity**, as the schema says (`Jam` → `Jams.fecha`).
   A separate id would be a second key that can disagree with the date. `JamSong`'s identity is
   (`date`, `position`); `Slot`'s is (`date`, `position`, slot index) — not modeled as types now.
8. **java.time is used (`LocalDate`, `LocalTime`); desugaring is deferred.** Verified on the clone:
   `:core:model` is JVM, so its own use passes the gate. `minSdk` 24 lacks java.time below API 26,
   but the build catches every consumer: lint `NewApi` fails any Android module that constructs or
   parses a date ("Call requires API level 26, or core library desugaring"), enabling desugaring
   only in `:app` does **not** silence a library's lint, and a library that enables it without
   `:app` fails `:app:checkDebugAarMetadata`. A consumer cannot obtain a `LocalDate` without
   constructing one, so no silent API 24 crash is reachable — **provided `:core:model` never builds a
   date from a string or the clock** (hence no parse helpers here). The first Android slice that
   touches dates (expected `catalog-repository-cache`) enables `isCoreLibraryDesugaringEnabled` +
   `coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")` in `:app` and in its own
   module; this slice records that convention in the architecture skill. kotlinx-datetime was not
   chosen: it wraps java.time on the JVM and needs the same desugaring.
9. **No system time in the model.** `isHistorical(today)` takes the date; the caller owns the clock
   and the time zone. A new Konsist rule `core-model-no-system-clock` fails when any `:core:model`
   file (main or test) contains `.now(`, `Clock.system` or `System.currentTimeMillis(` /
   `System.nanoTime(`. On the jam's own date the jam is **not** historical (`date < today`, the
   schema's wording).
10. **`JamSong` carries `title` and `artist`.** domain-model.md's edge case says "the JamSong holds
    the title and artist it needs to render", and the schema resolves them from `Catalogo` with the
    jam tab's copy as fallback. Its Core Concepts line omits them; that line is updated.
11. **`Jam.setlist`, not `songs`**, per CONTEXT.md ("A Jam has exactly one Setlist"). The domain
    doc's sketch is updated to match.
12. **Enrichment is flat and nullable on `Song`** (`mbid`, `artistArea`, `deezerTrackId`,
    `previewUrl`, `artworkUrl`, plus Sheet-owned `songsterrId`), all defaulting to null, matching
    domain-model.md (D-09). URLs stay `String?` (no `java.net.URI` parsing in the model).
13. **`ModuleWiringTest` keeps `:core:model` coverage** by replacing the marker line with a real
    type: `assertEquals(JamStatus.DRAFT, JamStatus.valueOf("DRAFT"))`. Its KDoc must say honestly
    that this proves `:core:model` is visible from `:app`, which is also true through the `api`
    edges of `:core:ui`/`:core:data` — exactly as the marker line already was; the direct edge is
    the `implementation(project(":core:model"))` line in `app/build.gradle.kts`.

## Open Questions (record in the spec's evidence; none blocks this slice)

- **A. Lineup counts.** domain-model.md/CONTEXT.md allow "how many of each" (e.g. three guitars);
  the Sheet allows only removal from the fixed seven. The type allows any count; the user should
  confirm whether adding slots beyond the default is ever wanted (affects `admin-adjust-lineup`).
- **B. Empty slots in past jams.** The schema says an empty cell in a past jam means "not recorded"
  and the app never presents it as open, but the type rule says no name = open. This slice keeps
  `Slot.isOpen` structural; `past-jam-detail` must not render open state. A third state was
  rejected because the domain forbids intermediate slot states.
- **C. Midnight.** A 21:00 jam becomes historical at 00:00 while still playing. The boundary is
  already an open question in `docs/risks-and-open-questions.md`; the type follows the schema.
- **D. Not enforced, not specified:** a DRAFT jam in the past; the same song twice in one setlist.

## Acceptance Scenarios

1. **Gate green.** `CI=true ./init.sh` exits 0 printing `konsist: wired`, `detekt: wired`,
   `ktlint: wired`; `ModuleIsolationTest` 10 tests, 0 failures; the `:core:model` test results hold
   the test classes below, 0 failures; `CoreModelMarker` no longer exists anywhere
   (`grep -r CoreModelMarker --include=*.kt` empty).
2. **Slot state.** `Slot(BASS).isOpen` is true and `isFilled` false; `Slot(BASS, "Tincho")` is the
   reverse; `Slot(BASS, " ")` and `Slot(BASS, "")` throw.
3. **Jam status.** `JamStatus.entries == listOf(DRAFT, PUBLISHED)`.
4. **Default and adjusted lineup.** `Lineup.default()` has 7 open slots in the order
   GUITAR, GUITAR, BASS, DRUMS, VOCALS, HARMONICA, KEYBOARDS; a `JamSong` holds a lineup with no
   harmonica, and `hasOpenSlotFor(HARMONICA)` is false while `hasOpenSlotFor(GUITAR)` is true;
   a lineup whose guitars are both filled reports `hasOpenSlotFor(GUITAR)` false; `openSlots`
   lists only the open ones.
5. **Keys.** `B`, `Bm`, `F#`, `Bbm`, `Ab`, `C#m`, `G` accepted; `""`, `H`, `b`, `bm`, `Bmaj`, `B m`,
   ` B`, `Do`, `F##`, `Bm7`, `BM`, `Si` rejected (constructor throws, `parseOrNull` returns null);
   `isMinor` true for `Bbm`, false for `Bb`.
6. **Song ids.** `sweet-little-angel`, `crossroads-robert-johnson`, `abc123` accepted;
   `Sweet-Little-Angel`, `café-madrid`, `-a`, `a-`, `a--b`, `a b`, `""` rejected.
7. **Setlist positions.** A `Jam` with positions `[1,2,3]` builds; `[2]`, `[1,3]`, `[1,1]` and
   `[2,1]` throw; an empty setlist builds; `JamSong(position = 0, …)` throws.
8. **Historical.** A jam on 2026-07-25 is historical for today 2026-07-26, not for 2026-07-25 nor
   2026-07-24.
9. **Song optionals.** A `Song` built with only id, title, artist and defaultKey has every optional
   field null and `tags` empty; `JamSong.key` can differ from the song's `defaultKey`.
10. **Clock rule can fail.** Adding `fun x() = java.time.LocalDate.now()` to a `:core:model` file
    makes `:konsist-test:test` report 10 tests, 1 failed (`core-model-no-system-clock`). Revert.

## Repository Research

### Files Inspected

- `AGENTS.md`, `PROGRESS.md`, `feature_list.json` — status, rules, inherited marker cleanup.
- `CONTEXT.md`, `docs/domain-model.md`, `docs/sheet-schema.md`, `docs/sheet-seed/*.csv` — names,
  states, key/id formats, slot cells, jam identity; seed ids and keys all pass the planned regexes.
- `docs/user-and-access-model.md`, `docs/risks-and-open-questions.md` — no model impact; midnight
  question found.
- `.claude/skills/architecture/SKILL.md` — `:core:model` depends on nothing, no Android, conventions.
- `core/model/build.gradle.kts` (kotlin-jvm, JVM 11, junit only), `CoreModelMarker.kt`,
  `CoreModelMarkerTest.kt`, `app/src/test/java/com/bbbjam/ModuleWiringTest.kt`,
  `app/build.gradle.kts`, `core/data/build.gradle.kts`, `gradle/libs.versions.toml` (minSdk 24).
- `konsist-test/.../ModuleIsolationTest.kt` — 9 tests; `core-model-no-android` is import-based.

### Prototype (throwaway clone in the session scratchpad, not the repo)

- The type shape above, marker removed, `ModuleWiringTest` line replaced: `CI=true ./init.sh` exit
  0, all three tools `wired`, 6 prototype model tests green, no detekt/ktlint finding.
- `LocalDate.parse` in `:core:data`: `lint` fails with `NewApi`; desugaring in `:app` only still
  fails; desugaring in `:core:data` too passes; desugaring in `:core:data` without `:app` fails
  `:app:checkDebugAarMetadata`. Basis for Decision 8.
- `core-model-no-system-clock` added: 10/10 green; with `LocalDate.now()` in `Jam.kt`, 10 tests,
  1 failed. Basis for Decision 9.

### Existing Patterns To Follow

- Sources in `src/main/kotlin` / `src/test/kotlin`, package `com.bbbjam.core.model`; JUnit 4
  (`libs.junit`, already `testImplementation`). No new dependency.
- Konsist rules use `assertFalse(testName = "…")` with a regex constant in the companion (see
  `COLOR_LITERAL`); filter `:core:model` files with `it.modulePath == CORE_MODEL`.

## Expected File Changes

- `core/model/src/main/kotlin/com/bbbjam/core/model/{Jam,JamStatus,JamSong,Lineup,Slot,Instrument,Song,Tempo,Difficulty,Key,SongId}.kt` — create.
- `core/model/src/main/kotlin/com/bbbjam/core/model/CoreModelMarker.kt` — delete.
- `core/model/src/test/kotlin/com/bbbjam/core/model/CoreModelMarkerTest.kt` — delete.
- `core/model/src/test/kotlin/com/bbbjam/core/model/{SlotTest,LineupTest,KeyTest,SongIdTest,JamTest,JamStatusTest,SongTest}.kt` — create.
- `app/src/test/java/com/bbbjam/ModuleWiringTest.kt` — modify (Decision 13).
- `konsist-test/src/test/kotlin/com/bbbjam/konsist/ModuleIsolationTest.kt` — add one test.
- `core/model/build.gradle.kts` — no change expected.

## Visual Design Impact

- UI involved: no. `DESIGN.md` not applicable.

## Durable Documentation Impact

- `docs/domain-model.md`: update Core Concepts sketch — `Jam(date, startTime, venue, status,
  setlist)` with date as identity, `JamSong(position, songId, title, artist, key, lineup: Lineup)`,
  `Key` format pointer to the schema. Reconciles it with the approved schema; no rule changes.
- `.claude/skills/architecture/SKILL.md`: update — the new Konsist rule in Dependency Rules; a Build
  Conventions line on java.time and desugaring (Decision 8); `:core:model` row lists the new types.
- `CONTEXT.md`, `docs/sheet-schema.md`, `AGENTS.md`: not needed. `ARCHITECTURE.md`/`CONSTRAINTS.md`
  do not exist; not needed.
- `PROGRESS.md`, `feature_list.json`: implementer updates status/evidence; also remove the
  "`CoreModelMarkerTest`" mentions and record Open Questions A–D in `PROGRESS.md`.

## Implementation Plan

1. Delete the marker and its test; update `ModuleWiringTest`.
2. Create the value types and enums, then `Slot`, `Lineup`, `JamSong`, `Jam`, `Song`.
3. Write the tests of the scenarios 2–9, one class per type; run `./gradlew :core:model:test`.
4. Add `core-model-no-system-clock`; demonstrate scenario 10; revert.
5. Run the negative demonstrations below; revert each; run `./gradlew ktlintFormat` then
   `CI=true ./init.sh`.
6. Update the docs listed above, `PROGRESS.md`, `feature_list.json`.

## Verification Plan

- `CI=true ./init.sh` (the gate), plus `./gradlew :core:model:test :konsist-test:test` while
  iterating. All JVM; no device needed.
- Negative demonstrations (mutate, observe failures by test name, revert, record):
  - `Slot.isOpen` = `musicianName != null` → `SlotTest` fails.
  - Remove the blank-name `require` → blank-name test fails.
  - Key regex loosened to `[A-G].*` → rejection tests fail.
  - SongId regex loosened to `.+` → rejection tests fail.
  - Drop `KEYBOARDS` from `DEFAULT_INSTRUMENTS` → default-lineup test fails.
  - Remove `Jam`'s position `require` → gap/duplicate/order tests fail.
  - `isHistorical` = `!date.isAfter(today)` → same-day test fails.
  - Add `ARCHIVED` to `JamStatus` → `JamStatusTest` fails.
  - Scenario 10 for the Konsist rule.

## Evidence To Capture

- Gate tail (three `wired`, exit 0); `ModuleIsolationTest` 10/0; per-class test counts in
  `core/model/build/test-results/test/`; each negative demonstration's failing test names;
  empty `grep` for `CoreModelMarker`.

## Validator Checklist

- [ ] Types match "Types To Create"; no `ARCHIVED`, no `Musician`, key on `JamSong` only.
- [ ] `:core:model` imports only `kotlin.*`, `java.time.*` (main) and `org.junit.*` (test); no
      annotations from a framework; `build.gradle.kts` unchanged.
- [ ] No mapper, repository, serialization, desugaring or `minSdk` change slipped in.
- [ ] Every rule has a test and a recorded negative demonstration; Konsist 10/10 with its failure shown.
- [ ] `ModuleWiringTest` references a real domain type and its KDoc states what it proves.
- [ ] Docs updated as listed; Open Questions A–D recorded in `PROGRESS.md`.

## User Approvals

Recorded 29 September 2026, before implementation. These amend the spec above; where they
conflict, this section wins.

- **Konsist rule `core-model-no-system-clock`: approved** as written (Konsist 9 → 10).
- **Open question A, lineup counts — decided as D-18.** A Lineup is the default seven Slots with
  some removed, never more of an instrument than the default (2 guitars, and 1 of each other
  instrument). Anyone playing outside it goes in a new list of **Extra Participants** ("Otros").
  Changes to this slice:
  - `Lineup` rejects a lineup with more Slots of an instrument than the default, with a unit test
    and a negative demonstration. Zero of an instrument stays valid.
  - New type `ExtraParticipant(name: String, instrument: String)` in `com.bbbjam.core.model`: both
    non-blank, neither may contain `;`, `(` or `)` (the Sheet cell format in
    `docs/sheet-schema.md`), with tests including a negative demonstration.
  - `JamSong` gains `extraParticipants: List<ExtraParticipant>` (default empty). Extra participants
    are never open and never affect `Lineup.openSlots` / `hasOpenSlotFor` — a test proves that.
  - No parsing of the `Otros` cell in this slice; that is the mapper's job.
  - `CONTEXT.md`, `docs/domain-model.md`, `docs/sheet-schema.md` and the seed CSV were already
    updated by the orchestrator for D-18; the domain-model Core Concepts edit this spec asks for
    must include `extraParticipants` on `JamSong`.
- Open questions B, C and D stay recorded as in this spec; they do not block this slice.
