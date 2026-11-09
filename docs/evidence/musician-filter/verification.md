# musician-filter: verification (implementer, 9 November 2026)

Commands that ran:

- `./gradlew ktlintFormat` before every gate.
- `CI=true ./init.sh` -> exit 0; ends with `konsist: wired`, `detekt: wired`, `ktlint: wired`, "Baseline OK".
  Earlier runs of the same gate failed and were fixed in code, with no rule change: detekt `MatchingDeclarationName`
  (moved `MusicianFilterCounts` into `MusicianFilterUiModel.kt`), then `LongMethod`/`CyclomaticComplexMethod` on
  `toUiModel` and `present` (extracted `songsModel` and `rememberMusicianChange`).
- JVM XML totals after the final run: core:model 63 tests, core:data 328, core:ui 170, feature:next-jam 220; 0 failures, 0 skipped.
  New suites: MusicianRosterTest 5, DefaultMusicianFilterPreferenceTest 4, MusicianFilterMapperTest 9,
  MusicianFilterDefaultsTest 2, NextJamMusicianFilterTest 7.

Design finding (observed, not planned): collecting the preference with its own `collectAsState(initial = null)` added
one extra identical `Loading` frame and failed 21 existing presenter tests (`ClassCastException` Loading -> Jam). The
presenter instead combines `observeJams()` with `observeSelected()` in one flow, so nothing shows before both were
read and no extra frame exists. One existing assertion in `NextJamPresenterTest` ("each row carries its strip...")
now also states the musician block it legitimately gets (that jam has names).

Not run: any device or emulator check (the spec's manual steps: pick a name, kill and relaunch, rotate, open a song
and go back, combine with a chip, clear). No Compose UI harness exists, so the dialog, the chip look and the 48dp
targets are unverified on device. No new Konsist rule was added, so there is no failing case to show.

## Independent validation (9 November 2026)

Validator verdict: accept. The device steps above marked NOT RUN were then run by the validator on the
Pixel 5 with the debug demo jam (filter taps only, no admin writes): picker with 9 names and counts;
Lucía selected gives "Quedan 5 de 8 temas con Lucía"; adding Guitarra gives 4 de 8; force-stop and
relaunch restores Lucía and drops the instrument; adding Bajo shows the combined empty state; "Ver todos
los temas" clears both and nothing is remembered after another relaunch. Rotation was inconclusive.
local.properties restored byte for byte.
