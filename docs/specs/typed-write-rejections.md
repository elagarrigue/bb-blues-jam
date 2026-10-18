# Spec: typed-write-rejections

Source: `feature_list.json` entry `typed-write-rejections` (area `core-data`, `depends_on: []`, status
`not_started`). From the organizer's code review of 17 October 2026, finding 1. Blocks
`setlist-mutation-contracts` (it depends on this feature).

## Goal

Apps Script error codes stop being string literals in feature modules. `:core:data` exposes a typed
rejection mapped from the wire code in one place; the raw code stays available for logs and unknown
codes. The generic failure lines shared by several features live once in `:core:ui`. **No
user-visible string changes by a single byte** and the Apps Script contract is untouched.

Non-goals: no change to `backend/apps-script`, `docs/apps-script-api.md` (only a short cross-reference
is allowed), the outcome types (`AddSongOutcome`, ...), the Room cache, or any copy. No new module. No
merging of feature-specific wording (see "Copy that stays").

## Findings (inspected, not assumed)

- `core/data/.../admin/WriteOutcome.kt`: `Rejected(val code: String)` is public; about 60 test sites
  build `WriteOutcome.Rejected("...")` directly, so the constructor must stay.
- `core/data/.../admin/AdminWriter.kt` `DataFailure.toOutcome()` is the one place that creates
  `Rejected(code)` from the wire (`else -> WriteOutcome.Rejected(code)`).
- `core/data/.../remote/ServiceCodes.kt` is `internal`; it holds only the codes the client maps to
  non-Rejected outcomes plus `SONG_NOT_IN_SETLIST` and `SLOT_NOT_IN_LINEUP`.
- Code literals in feature main sources (grep `"[a-z]+_[a-z]+"` over `feature/*/src/main`):
  - `feature/next-jam/.../admin/FailureMessages.kt` (six functions: `failureMessage`, `keyFailureMessage`,
    `lineupFailureMessage`, `assignmentFailureMessage`, `slotClearFailureMessage`, `moveFailureMessage`)
  - `feature/past-jams/.../admin/JamsFailureMessages.kt` (`failureMessage`, `editFailureMessage`,
    `publishFailureMessage`), `.../create/NewJamMapping.kt` (`createFailureMessage`),
    `.../edit/JamEditMapping.kt:65` (`setlist_changed`)
  - `feature/assistant/.../apply/ApplyOutcomeCopy.kt` (`writeFailure`), `.../apply/ReplaceOutcomeCopy.kt:32`
    (`unordered_setlist`), `.../jam/JamCopy.kt` (`writeReason`), `.../run/ApplySteps.kt` (companion
    constants `DUPLICATE`, `NOT_LISTED`, `DUPLICATE_ID`, `PER_SONG_CODES`), `.../create/CreateSummary.kt:88`
    (`NOT_EDITABLE`)
- Distinct codes in use (25): `song_already_in_setlist`, `song_not_in_setlist`, `duplicate_song`,
  `unknown_song`, `unknown_jam`, `jam_not_editable`, `duplicate_date`, `slot_filled`,
  `instrument_not_expandable`, `invalid_name`, `slot_taken`, `slot_not_in_lineup`, `slot_empty`,
  `slot_changed`, `unordered_setlist`, `jam_exists`, `tab_exists`, `past_date`, `setlist_changed`,
  `empty_setlist`, `invalid_song`, `invalid_key`, `invalid_date`, `invalid_time`, `invalid_venue`.
  `editFailureMessage` and `createFailureMessage` also treat **any** code starting with `invalid_` as
  "invalid" (this covers codes such as `invalid_status`, `invalid_update`, which are not in the list).
- Generic lines duplicated verbatim, checked by reading the constants: `ACCESS_REFUSED`, `OFFLINE`,
  `UNAVAILABLE`, `JAM_CHANGED`, `SHEET_REFUSED` in `NextJamCopy` and `PastJamsCopy`; `ApplyOutcomeCopy`
  repeats `ACCESS_REFUSED`, `JAM_CHANGED`, `SHEET_REFUSED`, `NOT_IN_CATALOG` (= `NextJamCopy.NOT_IN_CATALOG`)
  and `WRITE_UNAVAILABLE` (= the `UNAVAILABLE` text).
- `:core:ui` depends only on `:core:model`, so it cannot see `WriteOutcome`: the shared copy is plain
  string constants and each feature keeps the `when` over `WriteOutcome`.
- `docs/apps-script-api.md` documents codes per action in tables; there is no single code list.

## Design decision

**Add a derived typed view; keep `code: String` as the stored field.** Do not replace the String.

- New public `enum class WriteRejection(val code: String)` in `com.bbbjam.core.data.admin` with one entry
  per code above plus `Unknown` (its `code` is `""`, never matched). Companion `fromCode(code): WriteRejection`
  (a prebuilt `associateBy` map; unknown codes give `Unknown`). Entry names are the upper-case code.
- `WriteOutcome.Rejected(val code: String)` is unchanged (constructor, equality, `toString`, ~60 test sites
  and every `Rejected("...")` in `:core:data` keep compiling). Inside the class body add
  `val rejection: WriteRejection get() = WriteRejection.fromCode(code)` and
  `val isInvalidInput: Boolean get() = code.startsWith("invalid_")`. Because both are body members, the data
  class equality is still by `code` only. The raw `code` remains for logs and unknown codes.
- Why not replace the String: it would rewrite every test and fake, force `AdminWriter` to invent a value
  for unknown codes (losing the raw code the logs and `Unknown` fallback need), and gain nothing the derived
  property does not. Why not a sealed type: the codes carry no data.
- The single mapping point is `WriteRejection.fromCode`. `AdminWriter.toOutcome` stays as is.
  `ServiceCodes.SONG_NOT_IN_SETLIST` and `SLOT_NOT_IN_LINEUP` may be deleted and their `:core:data` users pointed
  at `WriteRejection.X.code` (optional; keep the diff small if callers are many). The codes `ServiceCodes`
  maps to non-Rejected outcomes (`invalid_passphrase`, `rate_limited`, ...) are **not** enum entries.
- Shared copy: new `public object WriteFailureCopy` in `:core:ui`, package `com.bbbjam.core.ui.text`, file
  `core/ui/src/main/kotlin/com/bbbjam/core/ui/text/WriteFailureCopy.kt` (beside `SongListText`). Constants
  `ACCESS_REFUSED`, `OFFLINE`, `UNAVAILABLE`, `JAM_CHANGED`, `SHEET_REFUSED`, `NOT_IN_CATALOG`, with the exact
  text currently in `NextJamCopy`. Features read it into UiModels; screens never do.

## Copy that stays feature-specific (do not merge)

`ApplyOutcomeCopy.WRITE_OFFLINE` ("No pudimos confirmar si se agregó..."), `REMOVAL_OFFLINE`, `JamCopy.OFFLINE`
(lowercase, "creó"), `PastJamsCopy.PUBLISH_OFFLINE`, `AssistantCopy.OFFLINE/UNAVAILABLE` (assistant service, not
a write) and `AdminLoginCopy.OFFLINE/UNAVAILABLE` (login wording) differ on purpose. Inspected `AssistantCopy`
and `AdminLoginCopy`: neither duplicates the write lines, so they are untouched. `NextJamCopy.OFFLINE` and
`PastJamsCopy.OFFLINE` are identical and move to `WriteFailureCopy.OFFLINE`; `ApplyOutcomeCopy.ACCESS_REFUSED`,
`JAM_CHANGED`, `SHEET_REFUSED`, `NOT_IN_CATALOG` and `WRITE_UNAVAILABLE` (same text as `UNAVAILABLE`) move too.

## Expected file changes

- `core/data/.../admin/WriteRejection.kt` (new), `WriteOutcome.kt` (two body members).
- `core/ui/.../text/WriteFailureCopy.kt` (new).
- The five files named in the request plus `NewJamMapping.kt`, `JamEditMapping.kt`, `ReplaceOutcomeCopy.kt`,
  `JamCopy.kt`: replace every `reason.code == "x"` / `when (reason.code)` string with `reason.rejection`
  (`WriteRejection.X`), `isInvalidInput` for the prefix rule, and the generic lines with `WriteFailureCopy`.
  `ApplySteps` companion constants become `WriteRejection` references (`PER_SONG_CODES` a set of entries).
  `when (reason.rejection)` needs an `else` branch (Unknown and unlisted codes), which is the existing
  `SHEET_REFUSED`/generic fallback.
- Delete the now-duplicated constants from `NextJamCopy`, `PastJamsCopy`, `ApplyOutcomeCopy`; fix references
  (including `NextJamRemovalPreview.kt` and tests). Only references change in tests, never expected strings.
- Tests: `core/data/.../admin/WriteRejectionTest.kt`, `core/ui/.../text/WriteFailureCopyTest.kt`.

## Implementation tasks (ordered)

1. Record baseline: `CI=true ./init.sh`, save the testcase count.
2. Add `WriteRejection` and the two `Rejected` members; add `WriteRejectionTest`.
3. Add `WriteFailureCopy` and its test.
4. Convert next-jam, then past-jams, then assistant files. Behaviour must not change: keep each `when`'s
   branch order and the "first match wins" structure (`keyFailureMessage` falls through to `failureMessage`, etc.).
5. Grep gate (below), then full `CI=true ./init.sh`; record the final count.
6. Docs: one dated line in the architecture skill ("As built by `typed-write-rejections`", under the
   `apps-script-write-auth` paragraph: `WriteRejection`, `Rejected.rejection`, `WriteFailureCopy`), and a
   sentence in `docs/apps-script-api.md` pointing at `WriteRejection` as the client's list of mapped codes.
   AGENTS.md, CONSTRAINTS, DESIGN.md: not needed (no rule or visual change).

## Verification

- `WriteRejectionTest`: for each entry `fromCode(entry.code) == entry`; codes are unique and non-blank;
  `fromCode("anything_else")` and `fromCode("")` are `Unknown`; `Rejected("slot_taken").rejection ==
  SLOT_TAKEN` and `.code` is still `"slot_taken"`; `Rejected("invalid_status").isInvalidInput` true,
  `Rejected("jam_exists").isInvalidInput` false. Prove a mismatch case able to fail (temporarily change one
  entry's code, see it red, revert; note it in evidence).
- `WriteFailureCopyTest`: each constant `assertEquals` against the **string literal copied from the original
  source** (git history), so a typo in the move fails.
- Mapping per code: a table-driven test in each feature's existing failure-message tests (next-jam
  `NextJamAdminTest` area, past-jams, assistant `ApplyOutcomeCopy`/`JamCopy`) asserting every code listed in
  Findings still yields the same text as before the change (compute expected from the old source), plus one
  unknown code (`Rejected("zzz")`) giving `SHEET_REFUSED` in each of the four message families.
- Grep gate (must print nothing): `grep -rnE '"(song_|slot_|jam_|duplicate_|unknown_|invalid_|unordered_|tab_|past_|setlist_|empty_|instrument_)[a-z_]*"' feature/*/src/main`.
  Also `grep -rn "\.code ==\|\.code in\|reason.code" feature/*/src/main` prints nothing.
- No leftover duplicates: `grep -rn "La planilla rechazó el cambio" --include=*.kt .` shows exactly one main
  source hit (`WriteFailureCopy`) plus tests. Same for "El servidor no respondió. Probá de nuevo en un rato." in
  the three write-copy objects (`AssistantCopy`'s differs and stays).
- `CI=true ./init.sh` exits 0 with konsist, detekt, ktlint `wired`; Konsist rules unchanged (19); record the test
  count before and after (it rises by the new tests; no test deleted). Watch detekt `CyclomaticComplexMethod`
  and `TooManyFunctions` in the enum companion and the touched `when`s; do not add suppressions.
- No device step needed.

## Evidence to capture

Baseline and final `init.sh` output and counts; the red run of the `WriteRejectionTest` mutant; both grep
outputs; the list of constants moved. Under `docs/evidence/typed-write-rejections/`.

## Validator checklist

- `Rejected(code)` constructor and equality unchanged; `AdminWriter`, `ServiceCodes` mapping and the Apps
  Script backend untouched (diff clean under `backend/`).
- No string of any user-visible text changed (diff of removed vs added literals is a pure move).
- Feature modules still depend only on `:core:*`; `:core:ui` gained no dependency on `:core:data`.
- Unknown and unlisted codes still fall back to the generic Sheet-refused line; `invalid_*` prefix behaviour kept.
- Feature-specific lines listed above are still in their feature. Orchestrator owns `feature_list.json` status.
- `presentations/` and PROGRESS.md's "harness presentation ready" entry untouched.

## Assumptions / unknowns

- Assumed no other module reads `Rejected.code` besides those grepped (`core:data` tests and setlist
  managers); the implementer re-greps `\.code\b` over `app/` and `core/` before finishing.
- `JamCopy.writeReason` mixes jam-creation codes; the enum covers them (`jam_exists`, `tab_exists`, `past_date`,
  `invalid_date/time/venue`), listed in Findings. If the implementer finds a code not listed, add it to the enum
  rather than leaving a literal.
