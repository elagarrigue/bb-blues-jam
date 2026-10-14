# Device check: assistant-create-jam (14 October 2026, Pixel 5)

**The final confirm (`Crear la jam` in the dialog) was NOT tapped.** The safety preconditions failed, so the check stopped at the dialog.

Preconditions:
- (a) `adb shell run-as com.bbbjam ls -l files/datastore`: `admin_session.preferences_pb` (29 bytes): a passphrase is stored. FAILED.
- (b) Startup log: `jams cache: upcoming 2026-10-31, past 1, songs 25 (25 from catalog) admin (debug)`, no `(demo)`: the upcoming jam is the REAL published 2026-10-31 jam. FAILED.

Steps (debug build installed over the existing app; no app data cleared, no logout; `local.properties` md5 prefix 569913ae before and after, never printed):
1. Asistente tab, ONE real chat request: `Crea LA jam del 28 de noviembre de 2026` (typed with `adb input text`, hence no accents). No retry.
2. The reply arrived in under 10 s and carried a VALID jam proposal: `NUEVA JAM`, `Sábado 28 de noviembre de 2026`, `21:00 · La Macanuda`
   (the time and venue of the 2026-10-31 jam, read by the `jamSchedule` tool), the note `Queda en espera: la próxima jam sigue siendo la
   del sábado 31 de octubre.`, the `Crear la jam` button, and (unrequested) an 18-song proposal below it. `01-jam-proposal.png`.
3. Tapped `Crear la jam`: dialog `¿Crear la jam?` with `Fecha: sábado 28 de noviembre de 2026`, `Hora: 21:00`, `Lugar: La Macanuda`, the draft line,
   the held-back line, buttons `Cancelar` and `Crear la jam`, no amber. `02-dialog.png`, `dump-dialog.xml`.
4. Tapped `Cancelar`: dialog gone, card still there (`03-after-cancel.png`, `dump-after-cancel.xml`). `adb logcat -s BluesJam` printed no line after the
   request and the whole log has no `createJam` match: no write was made.

Not verified on a device: the creation itself (progress, result, failure copy, `Reintentar`, `ScriptOutdated`) and the list gate after creation; they are
covered by JVM tests (`JamPresenterTest`, `JamCreationRunnerTest`, `SetlistJamCreationsTest`). The live Apps Script was not called with any write action
(and still has the old `Post.gs`). The key and the Apps Script URL appear in no evidence file.
