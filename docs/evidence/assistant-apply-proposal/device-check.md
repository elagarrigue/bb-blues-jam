# Device check: assistant-apply-proposal (14 October 2026, Pixel 5)

**The final confirm (`Agregar 10 temas`) was NOT tapped.** The safety preconditions failed, so the check stopped at the dialog, as instructed.

Preconditions (checked before any confirm):
- (a) `adb shell run-as com.bbbjam ls -l files/datastore` showed `admin_session.preferences_pb` (29 bytes, dated 13 Oct): a passphrase is stored on the device. FAILED.
- (b) Startup log: `jams cache: upcoming 2026-10-31, past 1, songs 25 (25 from catalog) admin (debug)` with `jams refresh: ... (admin read)`. No `(demo)` and no `draft`: the upcoming jam is the REAL published 2026-10-31 jam (12 songs), read with the stored passphrase. FAILED (the demo jam did not apply: a real jam wins).
- (c) Code: no Kotlin or Gradle file reads `bluesjam.debugAdminPassphrase`; `DebugAdminSession` never touches `AdminCredentialStore`/`AdminWriter` (it only wraps `observeIsAdmin`). OK, but (a) and (b) mean a confirm would have written to the real Sheet.

Steps done (no write possible before a confirm):
1. Temporarily added `bluesjam.demoUpcomingJamDraft=true` to `local.properties` (no effect, a real jam exists); restored byte-for-byte afterwards, md5 `569913aea7da6dcc6516a0822e41bfa4` before and after.
2. Asistente tab, ONE real chat request ("12 temas de blues nacional con inicio y final arriba"), answered in under 12 s with a 12-song proposal. No retry. `01-proposal-action.png`: `Usar esta lista` under the message.
3. Tapped `Usar esta lista`: dialog `02-dialog.png`, `dump-dialog.xml`: title `¿Cargar esta lista?`, `Se van a agregar 10 temas al final de la lista de la jam del sábado 31 de octubre, en este orden y con la tonalidad del catálogo...`, `La lista ya tiene 12 temas; los nuevos van después.`, `2 ya están en la lista y no se agregan: Blues del Equipaje, Café Madrid.`, `La lista ya está publicada: ...`, buttons `Cancelar` and `Agregar 10 temas`.
4. Tapped `Cancelar`: dialog gone (`03-after-cancel.png`, `dump-after-cancel.xml`); the BluesJam log shows no new line (no write, no refresh).

Not verified on a device: the run itself (progress captions, abort on `AccessRefused`, Detener, `Reintentar los que faltan`, state surviving a tab change). Those are covered by JVM tests only (ProposalApplierTest, ApplyPresenterTest). To run it safely, a device with no stored passphrase and the demo jam as upcoming is needed (the real jam must be absent, or the Sheet one must be a test jam).
The key appears in no evidence file.
