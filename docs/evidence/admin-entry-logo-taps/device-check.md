# Device check — admin-entry-logo-taps (17 October 2026)

The emulator could not start on this machine (no hypervisor, see `device-not-run.txt`), so the
device check ran on the Pixel 5 (serial 09281FDD4004U6) **with the organizer's explicit approval
on 17 October 2026**, which amends the spec's emulator-only device section. The organizer accepted
that leaving admin deletes the stored passphrase on the Pixel and that they re-enter it afterwards.

Install: the implementer's `:app:assembleDebug` APK (built 13:28, after the last `feature/info`
edit at 13:26), installed with `adb -s 09281FDD4004U6 install -r` (no uninstall, no data clear, no
`installDebug`). Debug build with `bluesjam.debugAdmin` enabled; real Sheet; no write tapped;
`local.properties` untouched. Logo tapped at (538, 491) with `input tap` chained in one
`adb shell` call (well under 1.5 s between taps).

| Step | Action | Observed | Screenshot |
|---|---|---|---|
| 1 | Open Info in admin | `MODO ADMIN` banner, wine background, logo; no admin line anywhere | `device-01-info-admin.png` |
| 2 | 4 quick taps on the logo | still admin | `device-02-after-4-taps.png` |
| 3 | 5 taps, 2.5 s apart | still admin (gap resets the count) | `device-03-after-5-slow-taps.png` |
| 4 | 5 quick taps | admin left: banner, wine background and Asistente tab gone | `device-04-after-5-taps-left-admin.png` |
| 5 | Info scrolled to the bottom as a musician | ends at Redes; no `Entrar como admin` line | `device-09-info-bottom-musician.png` |
| 6 | 5 quick taps on the Próxima jam logo | nothing happens | `device-05-next-jam-logo-inert.png` |
| 7 | 5 quick taps on the Info logo as a musician | `Entrar como admin` login opens; nothing typed | `device-06-login-opened.png` |
| 8 | Back | returns to Info, still musician | `device-07-back-from-login.png` |
| 9 | Force-stop and relaunch | debug admin session forces admin again (T5) | `device-08-relaunch.png` |

Result: every step of the spec's device plan passed. The stored passphrase on the Pixel was
cleared by step 4, as expected; the organizer re-enters it with five taps and the passphrase.
