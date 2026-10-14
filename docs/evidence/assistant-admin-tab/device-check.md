# assistant-admin-tab device check (13 October 2026)

Device: Pixel 5 (redfin), Android 14 (API 34), 1080x2340, Wi-Fi online (ping 8.8.8.8 0% loss). Debug build, `bluesjam.debugAdmin=true` and
`bluesjam.demoUpcomingJam=true` were already in `local.properties`, so it was not edited: md5 before and after
`569913aea7da6dcc6516a0822e41bfa4`. `devexpert.key` present (`grep -c` = 1); the key value appears in no file of this directory (count 0 per file).
No Sheet write at any step; only the startup catalog/jams GETs ran.

| Step | Result |
|---|---|
| D1 | `01-tab-visible.png`: bar with Próxima jam, Anteriores, Info, Asistente and the MODO ADMIN banner. |
| D2 | `02-empty.png`: title, intro block, composer, `Enviar` disabled. |
| D3 | Brief's example typed ASCII-only (adb `input text` cannot type accents; the keyboard autocorrected "nacional" to "national" in the first attempt), sent with `Enviar`. `03-pending.png` (keyboard still up, the pending card is behind it) and `02b-typed.png` show the request turn and `Esperando respuesta…`. **The request did not return a proposal**: it ended after 120 s with `No hay conexión. Para usar el asistente necesitás internet.` (`04-timeout-failure.png`, the failure + `Reintentar` turn). |
| D6 | Two device requests were sent, both ended the same way. For the second a temporary class-name-only log showed the chain `ai.koog.prompt.executor.clients.LLMClientException <- io.ktor.client.plugins.HttpRequestTimeoutException`, 120.1 s after the Koog agent started (19:34:58.4 to 19:36:58.5), i.e. the 120 s request timeout in `AssistantHttp`; `HttpRequestTimeoutException` is an `IOException`, so `AssistantErrors` maps it to `Offline`. The log line was reverted (`AssistantErrors.kt` byte-identical to the accepted file). Filtered logcat in `logcat-device-check.txt` (no crash, no `NoClassDefFoundError`, no ServiceLoader error; the agent starts: "starting from __start__"). |
| D4 | uiautomator dump of the failure state was taken but not kept (`dump-answer.xml` is not in this directory); the pending live region and a proposal row's merged node were not captured because no proposal was ever returned. |
| D5 | Info, `Salir del modo admin`: `05-tab-gone.png` shows three tabs and no banner. |

Context for the failure: the same code answered in 7.06 s from the JVM (`AssistantLiveCallTest`, seed catalog of 13 songs; the accepted
`live-call.md` was backed up and restored byte-for-byte after that run). On the device the catalog has 100 songs and 26 past-jam songs. The device
check therefore does **not** prove the Jackson-excluded Koog path: no class is missing at runtime, but the request never completed. Candidates:
the gateway/model slow or stuck with the larger tool results, or an Android-only stall in the OkHttp engine path. Not diagnosed further (no
third request).

Deviation found by the device: the first typed screenshot showed the window panned under the status bar (no `windowSoftInputMode`); fixed with
`android:windowSoftInputMode="adjustResize"` on `MainActivity` (second screenshot `02b-typed.png` fits).

**Orchestrator update (gateway outage):** the DevExpert gateway was down during and after this check: every endpoint, including `/v1/models`, returned 502 with an empty body (verified by curl at about 20:14 to 20:20 local), and a JVM live run with the real 100-song catalog also got 502. D3 is therefore **pending a retry once the gateway is back**, not diagnosed as an app bug. The "slow gateway or Android stall" candidates above are superseded by this. No more real requests were sent after the outage was found.

Follow-up fix: `Enviar` now calls `LocalFocusManager.clearFocus()` before sending, which hides the keyboard so the pending card is visible. It is screen-only (the UiModel and presenter are unchanged, so there is no JVM test; there is no Compose UI harness); to be checked on the device with the retry.

## Amendment check: service status banner (13 October 2026, gateway still down)

Pixel 5, API 34, debug admin and demo jam, same `local.properties` (md5 `569913aea7da6dcc6516a0822e41bfa4` before and after). The status read is a GET of `/v1/models`, no tokens; **no chat request was sent**.

- `06-service-down.png`: Asistente opened with the gateway returning 502. The banner shows "El asistente no está disponible en este momento. El servicio de IA no responde; probá más tarde." with an underlined `Reintentar`, the intro below, and `Enviar` drawn disabled (`surfaceRaised`/`textMuted`). `dump-service-down.xml` has the banner text and `Reintentar`.
- `07-service-down-retry.png`: after tapping `Reintentar` once, the banner is still there (the gateway is still down), as expected. No amber on the banner.
- No logcat line of a chat completion (`grep` count 0).
- Not seen on the device: the Available, Offline, Unauthorized and Checking states (covered by the Molecule and mapping tests); D3 stays pending until the gateway is back.

## D3 retry, gateway back (14 October 2026): PASSED

Pixel 5, API 34, debug admin and demo jam, same build as the status amendment, `local.properties` md5 unchanged (`569913aea7da6dcc6516a0822e41bfa4`). The gateway was back (orchestrator curl: chat completions 200 in 1.4 s).

- `08-available.png`: Asistente opened, no banner (status `Available`); `08b-typed.png` with the brief's example typed (ASCII, keyboard up, `Enviar` amber).
- ONE real request sent with `Enviar`. The keyboard hid on the tap (focus cleared): `09-pending.png` shows the pending state with the keyboard gone; its dump has "Armando la propuesta…".
- **Latency:** the answer was on screen within 37 s of the tap (upper bound, polled with uiautomator dumps about every 3 s; the Koog agent ran tool rounds on the real 100-song catalog). No retry was needed.
- Reply (`10-proposal.png`, `11-scroll-2.png`, `11-scroll-5.png`): `PROPUESTA · 12 TEMAS`, rows `01 Ruta 66 (Pappo's Blues, A)`, `02 La Bifurcada (Memphis La Blusera, A)` ... `12 Tren de las 16 (Pappo's Blues, E)`, each with artist and a Rioplatense reason, keys in amber; then the message ("Te dejo una primera lista de blues nacional con arranque y cierre bien arriba y el medio más tranqui; no encontré ninguna jam con un músico llamado Juan, así que decime bien quién es y lo sumo."), `PREGUNTAS` with two questions, no discarded-ids caption (0 rejected). It answered "no encontré Juan" because the demo/real past jams have no musician named Juan; that is the model's honest answer, not an app error.
- **This proves the Jackson-excluded Koog path works on Android at runtime**: a real chat-completions request with two tools returned a validated proposal; no crash, no `NoClassDefFoundError`.
- D4 (`dump-end.xml`): each song row is one non-clickable grouped node (bounds 510-781 for row 12) holding position, title, the key with content-description `Tonalidad E`, artist and reason; the request text is a `Vos` card; nothing is clickable in the reply. The pending state was a polite live region in code (`dump-pending.xml` has its text); uiautomator does not print the liveRegion attribute, so it is not asserted from the dump.
- No Sheet write, no list applied (none exists). Key in no evidence file.
