# Device check - admin-set-key song detail amendment (17 October 2026)

Pixel 5 (09281FDD4004U6), run with the organizer's explicit approval of 17 October 2026 for this
feature only. `./gradlew :app:installDebug` (no data clear, no uninstall), debug build with
`bluesjam.debugAdmin` and `bluesjam.demoUpcomingJam` set, the demo jam (Sabado 31 de octubre, 6 songs).
No Sheet write: the picker was dismissed with Back, the extras dialog with `Cancelar`, and no
`Anotar`, `Liberar` or editor plus/minus was tapped. `local.properties` md5 569913ae unchanged.
Evidence: `refinement-device-*.png` and `refinement-device-*.xml` (`uiautomator dump`).

| Item | Observed | Evidence |
|---|---|---|
| Admin key block opens the picker | Tapping the key block of "Ruta 66" opened `Cambiar tonalidad` with `TONALIDAD ACTUAL A` and the key grid. Back returned to the detail; no key picked. | 02, 03, 04 |
| Key block node | One `clickable=true` node [44,654][227,1010] (about 183x356 px, well over 48dp) wrapping a child `content-desc="Tonalidad A"` with identical bounds. The clickable node itself has an empty `content-desc`; uiautomator shows the merged description on the child. `onClickLabel` is not in the dump (known limit, TalkBack not run). | 02 |
| `+ Otro` / `Agregar` row | After the last open slot (`Teclados: libre`) and before `CUPOS CUBIERTOS`: text `+ Otro` [110,1903][707,1955] and a `clickable` button, `content-desc="Agregar a Otros"`, text `Agregar`, 132 px tall (over 48dp). | 02 |
| Row opens the dialog | Dialog `Agregar a Otros` with `Nombre`, `Instrumento`, `Cancelar`, `Agregar`. `Cancelar` closed it; nothing sent. | 05, 06 |
| Formation editor | `FORMACION` heading and the instrument lines with `Sacar/Sumar un cupo de ...` buttons are in the first dump with no tap on an opener. No node `Cambiar formacion`, `Listo`, or a separate `Agregar a Otros` button text (the only `Agregar a Otros` is the row button's description and the dialog title). | 02, 07 |
| All-filled song | **Not checked on device**: songs 2-6 of the demo jam list every instrument as `libre` and song 1 has open slots, so no all-filled song exists. A5 is covered by the JVM test only. | 08-list-1..3 |
| Musician detail read-only | **Not checked on device**: reaching it needs leaving the admin session, which the organizer excluded. Covered by JVM tests (`admin == null`, no line actions). | - |

Result: the admin items passed. Not verified on device: the all-filled `CUPOS LIBRES` case and the
musician view.
