# BB Blues Jam: el harness y el proceso de desarrollo con IA

Entregable del proyecto final de **AI Expert** (DevExpert, edición septiembre 2026).

Este documento resume cómo se construyó la app con agentes de IA. Se basa en los requerimientos
(`docs/build-brief.md`, `docs/phase-2-brief.md`), el plan (`feature_list.json`, `PROGRESS.md`) y,
sobre todo, la bitácora (`bb-blues-jam-bitacora.md`), donde está el detalle de cada sesión y de
cada decisión. La funcionalidad de la app está en el [README](../README.md).

---

## 1. En números

| | |
|---|---|
| Período | 19 de septiembre al 17 de octubre de 2026, cuatro semanas |
| Rebanadas (features) en `feature_list.json` | 65: 63 `accepted`, 1 `blocked` por decisión de alcance (D-20), 1 `in_progress` |
| Especificaciones escritas por el `planner` | 64, en `docs/specs/` |
| Decisiones registradas con su fundamento | 29 (D-01 … D-29) |
| Commits | más de 210 |
| Tests JVM en la última corrida del gate | 1128 |
| Reglas de arquitectura ejecutables (Konsist) | 19 |
| Módulos Gradle | 8, más `:konsist-test` y el build incluido `build-logic` |
| Acciones del backend (Apps Script) | más de 20, todas validadas en el servidor |
| Líneas de código de producto escritas a mano | ninguna |

Todo el código de producto lo escribieron agentes. El humano (el organizador de la jam, que además
es el desarrollador) decidió, revisó, probó en el teléfono e hizo los pasos que solo se pueden hacer
con su cuenta de Google.

---

## 2. Del problema al plan

### 2.1 Elegir un problema que valga la IA

Se evaluaron ocho dominios antes de elegir: películas, recetas, deportes, setlist.fm, playlists y
otros. De ese descarte salieron cuatro criterios (bitácora §1.3):

1. el asistente tiene que **transformar o decidir**, no resumir;
2. el resultado tiene que ser **verificable de un vistazo**;
3. la tarea manual equivalente tiene que ser **realmente tediosa**;
4. idealmente, un problema propio y usuarios reales.

La BB Blues Jam cumple los cuatro. Armar una lista de 12 temas que arranque arriba, afloje en el
medio, cierre fuerte y no repita la jam anterior es una decisión. Cualquier músico verifica el
resultado leyéndolo. Y hoy esa tarea depende de la memoria de una persona.

### 2.2 Investigación con NotebookLM

Se cargó la documentación oficial de diez APIs de música: Spotify, MusicBrainz, Discogs, Last.fm,
Deezer, Genius y otras. Se pidió una síntesis comparativa orientada a integración. Lo valioso fue
la **comparabilidad**: límites de tasa, autenticación y restricciones legales, todo con el mismo
esquema y con fuentes. Igual se verificó contra la documentación viva, y aparecieron dos cosas que
el notebook no reflejaba:

- Spotify había deprecado `audio-features` y limitado el Development Mode.
- Deezer no pide credenciales para su catálogo público.

La conclusión más importante fue negativa: **ninguna API da tonalidad ni tempo confiables**, que es
justo el dato central de una jam. De ahí salió D-08: la tonalidad la define el admin.

### 2.3 Definir el alcance conversando con Claude

La conversación de diseño aportó tres decisiones que sostienen todo el proyecto:

- **Eliminar un problema en vez de resolverlo (D-04).** La sincronización bidireccional con Google
  Sheets se reemplazó por **una sola autoridad por entidad**: la planilla es dueña del catálogo y de
  las jams pasadas, y la app, de la lista de la próxima jam.
- **Detectar el punto ciego del LLM (D-14).** El pedido estrella, "una jam de blues nacional", es
  justo donde el modelo alucina: de Manal o de Memphis la Blusera sabe poco. Por eso el asistente
  **solo elige del catálogo propio**, y el problema pasa a ser filtrar y ordenar datos controlados.
- **El contrato de acciones (D-13).** Se fijó antes de escribir código: si el admin puede hacerlo a
  mano, el asistente tiene que poder hacerlo también. Toda mutación existe desde la fase 1 como
  función del repositorio. En la fase 2, el asistente se conectó sin tocar las features.

### 2.4 Diseño con Stitch

Las pantallas se generaron en Stitch con un estilo global y un prompt por pantalla (bitácora §5).
La lección de método fue que **una herramienta generativa produce artefactos y también
afirmaciones**. Las pantallas estaban bien. El resumen del proyecto que escribió Stitch, en cambio,
inventó un bar ("El Motivo Bar"), un admin llamado "Fede", WebSockets y una PWA. Sin las decisiones
escritas para contrastar, esos datos habrían entrado al proyecto sin que nadie lo notara (§6.5).

### 2.5 Del descubrimiento al grafo de trabajo

Dos skills del curso convirtieron el descubrimiento en algo ejecutable:

- **`build-brief`** pasó dos documentos escritos para humanos (la bitácora y el brief de diseño) a
  siete documentos para un agente sin contexto:
  - `CONTEXT.md`, con el vocabulario del dominio;
  - el brief del producto;
  - el modelo de dominio;
  - el modelo de acceso;
  - el descubrimiento técnico;
  - `DESIGN.md`;
  - riesgos y preguntas abiertas.

  La skill no descubrió nada: tradujo. El trabajo real fue separar lo que era decisión estable de
  lo que todavía era suposición.
- **`harness-starter`** generó `AGENTS.md`, `init.sh`, `PROGRESS.md` y `feature_list.json`, con 34
  rebanadas iniciales ordenadas por dependencias reales. Al escribir el grafo quedó a la vista qué
  se podía hacer ese mismo día (ocho rebanadas sin depender del esquema de la planilla) y qué tenía
  que esperar. Un plan por fases no responde esa pregunta.

---

## 3. El harness

El harness es lo que permite que un agente sin memoria retome el proyecto en cualquier sesión y
trabaje sin romper lo que ya estaba. Tiene seis piezas.

### 3.1 Contexto: `AGENTS.md` como índice, no como volcado

- **Corto:** unas 150 líneas, dentro del límite práctico que da el curso.
- **Layout en U:** arriba van el rol, las reglas innegociables y los comandos literales; en el
  medio, la referencia; al final, la Definition of Done y un recordatorio de lo crítico. En la
  primera versión las reglas estaban en la línea 46, en la zona de menor atención (*lost in the
  middle*). Se reordenó sin cambiar el contenido (§6.6).
- **Reglas verificables con su motivo:** "No ViewModels (D-02)", no "buena arquitectura". Una regla
  sin justificación se erosiona al tercer refactor.
- **Lectura bajo demanda:** cada documento de `docs/` dice cuándo leerlo ("cuando toques la
  planilla", "cuando toques UI").
- **Compartido entre agentes:** Claude Code lo lee directamente. Codex usa las mismas skills a
  través de un junction, `.agents/skills → .claude/skills`. Un mismo harness sirvió a dos
  herramientas.

### 3.2 Skills: conocimiento estable y reutilizable

- **Skills del curso,** versionadas en `.claude/skills/`: `build-brief`, `harness-starter`,
  `feature-flow`, `feature-spec`, `feature-implementer` y `feature-validator`.
- **Skill propia, `architecture`:** dónde va cada pieza (módulo, presenter, repositorio, mutación,
  dependencia Gradle), el patrón de presenters con su ejemplo compilado y una tabla de
  anti-patrones. Se adelantó una semana porque, sin ella, el `implementer` iba a improvisar la
  estructura del primer módulo.

### 3.3 Tres subagentes con roles separados

| Subagente | Envuelve | Produce | No puede |
|---|---|---|---|
| `planner` | `feature-spec` | `docs/specs/<id>.md` | escribir código |
| `implementer` | `feature-implementer` | código, evidencia, estado `passing` | declarar aceptación ni commitear |
| `validator` | `feature-validator` | veredicto `accept` / `revise` / `block` | editar archivos (solo lectura + Bash) |

El orquestador (la sesión principal) coordina los tres y es el único que commitea.

Los archivos de los subagentes son **delgados a propósito**. Cada uno invoca su skill, declara la
frontera de su rol y agrega lo único que una skill genérica no puede saber: las reglas de este
proyecto. Cada rol recibe la misma regla en un tiempo verbal distinto. El `planner` la recibe como
restricción de la spec. El `implementer`, como defecto aunque la spec no lo diga. El `validator`,
como algo que un build en verde no detecta (§6.4).

**El validador no puede editar.** Un validador que arregla lo que juzga deja de ser independiente,
y la independencia es la única razón por la que el rol existe.

### 3.4 El gate: `init.sh` que dice la verdad

`CI=true ./init.sh` envuelve `./gradlew build` y `./gradlew check`: tests JVM, Android lint,
Konsist, detekt y ktlint. Termina informando si cada herramienta está **cableada**:

```
  konsist: wired
  detekt: wired
  ktlint: wired
```

Al principio las tres decían `NOT WIRED YET`, porque era la verdad. Durante el proyecto el gate
encontró dos formas en que podía mentir, y las dos se corrigieron (§6.10):

- Gradle daba por "al día" el test de Konsist aunque se rompiera una regla en otro módulo: los
  fuentes ajenos no eran entradas de la tarea.
- `init.sh` marcaba Konsist como cableado solo porque el módulo se llamaba `konsist-test`.

Desde entonces, una herramienta cuenta como cableada solo si corre en todos los módulos, y el
script nombra los que faltan. **El gate solo se volvió más estricto, nunca más laxo:** sin archivos
de baseline que escondan problemas y sin reglas apagadas para que pase. Cuando detekt fallaba, el
`implementer` partía archivos; nunca cambió una regla.

### 3.5 Reglas de arquitectura como código (Konsist)

Las decisiones que un build en verde no detecta se volvieron tests. Algunas de las 19 reglas:

| Regla | Decisión |
|---|---|
| Ningún módulo de feature importa a otro | D-03 |
| Nada de `android.*` en `:core:model` | D-03 |
| Ningún literal de color fuera de `:core:ui`; las pantallas no leen `MaterialTheme.colorScheme` | D-17 |
| El ámbar solo aparece en sus roles (`primaryAction`, `slotOpen`, `key`, `published`, `activeFilter`) | D-17 |
| Ninguna feature importa la navegación | D-03 |
| El agente no puede referenciar repositorios de escritura (`assistant-reads-only`) | D-24 |
| La feature del asistente solo aplica propuestas a través del contrato de acciones | D-13 |

**Regla de método: una regla que no puede fallar no prueba nada.** Cada regla nueva se mostró
fallando antes de confiar en que pasara. Se plantaba la violación, se veía el gate en rojo con el
archivo y la línea, y se reparaba. Para la regla central (ninguna feature importa otra) todavía no
había dos features. Se crearon dos módulos descartables con un import cruzado y se borraron
después, comprobando por hash que el árbol quedó idéntico.

Lo mismo se aplicó a los tests de producto. En cada rebanada, el `implementer` y el `validator`
rompían el código a propósito para comprobar que algún test se ponía en rojo. Por ejemplo,
cambiaban el "cualquiera" del filtro por "todos", o hacían que el aviso de "sin conexión" se
disparara por antigüedad y no por falla. Más de una vez los tests seguían en verde, y ese hueco se
cerró antes de aceptar.

### 3.6 Estado durable: `feature_list.json`, `PROGRESS.md`, specs y evidencia

- **`feature_list.json`** es el grafo con la escalera de estados `not_started → in_progress →
  passing → accepted`, más `blocked`.
  - **`passing` no es `accepted`.** `passing` significa que el implementador se autoverificó;
    `accepted`, que un validador independiente aceptó.
  - Una dependencia solo cuenta como satisfecha cuando está `accepted`. Si no, la autoverificación
    alcanzaría para desbloquear trabajo.
  - **`blocked` es esperar una decisión humana,** no tener dependencias sin cumplir. Al inicio la
    tentación era marcar 27 rebanadas como bloqueadas; solo una lo estaba de verdad.
- **`PROGRESS.md`** guarda el estado verificado y la siguiente rebanada lista. Cada sesión empieza
  leyéndolo.
- **`docs/specs/<id>.md`** es el contrato que juzga el validador. Sin spec, no hay validación.
- **`docs/evidence/<id>/`** guarda capturas del dispositivo, salidas del gate, corridas de Konsist
  fallando, probes del backend y diffs de classpath. Lo que se afirma en `feature_list.json` tiene
  que poder abrirse.
- **La memoria del orquestador** guarda preferencias que valen entre sesiones: correr las rebanadas
  de corrido, nunca pedir datos de prueba en la planilla real, commitear rutas explícitas y usar
  Sonnet para los implementadores.

---

## 4. El ciclo por rebanada

```
 feature_list.json ──▶ planner ──▶ spec ──▶ implementer ──▶ passing ──▶ validator
        ▲                │ (prueba en una copia                │            │
        │                │  descartable antes de proponer)     │      accept│revise│block
        │                ▼                                     ▼            │
        │        preguntas al humano              gate + dispositivo        │
        │                                          + evidencia              │
        └─────────────── orquestador: persiste accepted y commitea ◀────────┘
```

Cada rol encontró cosas que el anterior no podía ver. Ya en la primera rebanada (§6.9):

- **El `planner` leyó el repo antes de opinar.** Notó que AGP 9 ya compila Kotlin, así que agregar
  el plugin rompía el build. Notó que el manifest no tenía ninguna activity. Y encontró una
  dependencia mal ordenada en otra rebanada.
- **El `implementer` probó en el teléfono real.** Con el sistema en modo claro, los íconos de la
  barra de estado salían oscuros sobre el fondo oscuro. Se desvió de la spec, lo declaró, y el
  validador juzgó que la desviación estaba justificada.
- **El `validator` juzgó también a la spec.** Encontró que la spec se contradecía y lo registró
  como inconsistencia de la spec, no como defecto del código.

Con el tiempo, el validador encontró **menos errores de código y más diferencias entre lo que un
documento afirma y lo que se verificó**. Algunos casos:

- `DESIGN.md` decía que libre y ocupado se distinguían por tres señales. El validador midió la
  captura en escala de grises: 50 contra 53 de luminancia, así que el relleno no era una señal.
- Una evidencia decía "latencia sin medir" al lado de la medición.
- Había un conteo de tests mal copiado.

Nueve rebanadas volvieron con `revise` al menos una vez. Ninguna se aceptó con el resumen del
implementador como única prueba.

**El `planner` prototipa antes de especificar.** Pedirle al agente que pruebe en una copia
descartable del repo cambió la calidad de las specs más que cualquier instrucción de formato. Las
decisiones más importantes del arranque salieron de ejecutar, no de leer documentación (§6.10):

- Konsist necesitaba declarar sus entradas.
- `init.sh` mentía.
- Ninguna versión estable de detekt corría sobre el Java 25 del daemon.

---

## 5. El humano en el circuito

### 5.1 La autonomía concentra el criterio humano en vez de reemplazarlo

El flujo corre las rebanadas de corrido, sin pedir permiso en cada etapa. **Se detiene solo donde
una decisión cambia el proyecto.** Por ejemplo:

- meter una versión alfa de detekt en el gate;
- commitear fuentes de terceros;
- subir Kotlin para poder usar Koog;
- una regla de arquitectura nueva;
- cualquier cambio que contradiga una de las D-xx.

Las preguntas al humano pasaron de "¿sigo?" a "¿esto?" (§6.11).

### 5.2 Las preguntas concretas sacan a la luz lo que el usuario quiere

Varias decisiones de producto no estaban en ningún documento. Aparecieron cuando un agente
necesitaba la respuesta para seguir:

- **"Otros" (D-18).** El `planner` de los tipos de dominio preguntó si un tema podía tener más
  cupos que la formación. La respuesta inventó el concepto: quien toca fuera de la formación va en
  una lista aparte.
- **Info habla de la comunidad (D-19).** El Instagram mostraba una jam los martes en otro lugar.
  Era el programa de radio. El lugar puede cambiar, así que es un dato de cada jam. Si el agente
  hubiera resuelto la contradicción solo, Info mostraría hoy una jam semanal que no existe.
- **"Para las jams pasadas no importa quién tocó, solo la lista de temas".** Una pregunta sobre un
  texto achicó una spec entera. Rehacer una spec es barato; implementar la equivocada, no.
- **Filtro con varios instrumentos y lógica "cualquiera".** La spec proponía filtrar de a uno. El
  organizador eligió otra cosa, porque es la pregunta real de quien toca dos instrumentos.

### 5.3 No inventar hechos: los hechos viven en archivos

"No inventar" funcionó como regla recién cuando se volvió un archivo. `docs/info-content.md` guarda
cada dato de Info con su fuente (el usuario, el Instagram, el teléfono), y los agentes escriben
desde ahí. La lección de Stitch se convirtió en infraestructura (§6.12).

### 5.4 Sacar al humano del circuito de verificación

Hubo dos "no quiero seguir siendo el blocker". La respuesta fueron rebanadas de harness, no más
pasos manuales:

- **Jam de demo solo en debug.** Antes, cada rebanada con datos necesitaba que el organizador
  cargara una jam de prueba en la planilla real y la borrara después. La jam de demo aparece solo si
  la planilla no tiene una real. El validador abrió el APK de release y comprobó que la clase no
  está ahí.
- **Sesión de admin solo en debug.** Dibuja los controles pero no conoce la frase de acceso. El
  servidor sigue validando cada escritura, así que el atajo no abre nada.

El patrón quedó claro: cada vez que una verificación depende de una persona, se busca un reemplazo
en código que no exista en la versión que usan los músicos.

### 5.5 Límites de seguridad explícitos

Los subagentes arrancan sin contexto: **lo que el orquestador no les dice, no existe**. En una
rebanada, un implementer activó TalkBack por adb aunque el usuario había pedido no hacerlo. La
restricción estaba en la memoria del orquestador, pero no en el prompt (§6.17). Desde entonces,
cada prompt repite los límites:

- nunca imprimir la key de inferencia, la frase de admin ni los valores de `local.properties`, que
  además se comprueba por hash que quede idéntico;
- nunca escribir en la planilla real durante una verificación;
- nunca tocar una acción de escritura en el teléfono, cerrar la sesión ni borrar datos de la app;
- respetar los permisos denegados, sin buscarles la vuelta.

La planilla real se usó como dato de verificación **sin ensuciarla**. Cuando hubo jams de prueba,
borrarlas fue parte de la prueba, y el validador bloqueó la aceptación hasta que se borraron: una
jam falsa visible para 40 músicos es un defecto aunque el código esté perfecto.

### 5.6 Cuando el sistema vive fuera del repo

El backend es un Apps Script pegado a una planilla privada. El agente no puede ni leerlo ni
desplegarlo. Cada cambio de backend se resolvió así:

1. **Una guía de migración para alguien que nunca abrió Apps Script.** Dice qué archivo pegar,
   qué textos buscar para confirmar que el pegado quedó bien, y que hay que desplegar una versión
   nueva, nunca una implementación nueva.
2. **Un probe sin escritura.** Un `POST {"action":"deployProbe"}` responde `unknown_action` con la
   lista de acciones que conoce el script desplegado. Si falta la acción nueva, el código viejo
   sigue arriba. Un `GET` de solo lectura confirma que los datos siguen intactos.

Así se detectó el fallo más silencioso del proyecto: después de pegar el código, la URL seguía
respondiendo con la versión vieja. La respuesta "Known: catalog" del endpoint delató que había
quedado el código anterior (§6.13). La verificación cambia de forma: todo chequeo se diseña para
ser observable desde afuera.

---

## 6. Fase 2: la IA dentro del producto

El MVP se construyó para recibir al asistente: contrato de acciones (D-13), catálogo propio (D-14) y
`UiModel` planos. La fase 2 lo comprobó.

### 6.1 Decisiones

| | Decisión | Alternativa descartada |
|---|---|---|
| D-21 | Agente en el teléfono con **Koog** (JetBrains), el equivalente en Kotlin del Agents SDK visto en clase | Un servidor aparte con `openai-agents`, o un bucle propio sobre OkHttp |
| D-21 | Subir Kotlin 2.2 → 2.3 primero, en su propia rebanada | Usar Koog 0.5, anterior a la 1.0 |
| D-22 | Key de DevExpert Inference en `BuildConfig`, con el riesgo aceptado y escrito | Pedirla en la app, o solo en debug |
| D-23 | `:feature:assistant` con un tab solo para el admin; el agente vive en `:core:data` | Un `:feature:admin` (choca con D-15) |
| D-24 | **El asistente propone; el admin aplica** | Que el agente escriba |
| D-25 a D-29 | La app crea, edita, borra y cambia el estado de las jams; las listas se cargan en jams en espera | Seguir dependiendo de la planilla para eso |

### 6.2 Cómo está hecho el agente

- **Dos herramientas de solo lectura:** `searchCatalog` y `pastJams`. El agente lee datos con
  herramientas, no con un volcado en el prompt. Así funciona "algún tema que haya tocado Juan".
- **Salida estructurada:** temas con id del catálogo, motivo, mensaje de cierre y preguntas.
- **`ProposalValidator`:** cada id se valida contra el catálogo antes de mostrarse. Título, artista
  y tonalidad se toman del catálogo, nunca del modelo, y lo que no existe se descarta.
- **Streaming:** un parser incremental muestra cada tema validado apenas llega. El parseo final es
  la fuente de verdad.
- **Aplicar una propuesta** (Agregar, Reemplazar o Crear jam) pasa por el `SetlistActionRegistry`,
  el mismo contrato de 14 mutaciones que usa el admin a mano. Dos reglas de Konsist garantizan que
  el agente no puede escribir y que la feature solo aplica a través del contrato.

### 6.3 Lo que solo apareció al ejecutar

- **El gateway ignoraba las herramientas.** Con `response_format: json_schema` aceptaba `tools`,
  pero el modelo no las usaba y devolvía una propuesta vacía. La spec ya traía el plan B:
  `StructuredRequest.Manual`, con el esquema en el prompt. Con eso el modelo llamó a las dos
  herramientas.
- **Jackson no se puede dexar con minSdk 24.** Se excluyó de Koog y se usó el serializer de
  kotlinx.
- **Inline de bytecode Java 17 sobre Java 11.** Primero se reprodujo el error. Después se subieron
  todos los módulos a Java 17 juntos, sin niveles mezclados.
- **El gateway se cayó, con 502 en todos los endpoints.** La rebanada quedó `blocked` y no
  `passing`, y se le agregó a la app un chequeo de estado del servicio con aviso y Reintentar. La
  prueba en el dispositivo se hizo cuando el gateway volvió: una propuesta real de 12 temas
  validados.
- **El clasificador de permisos negó tocar "Enviar"** con un pedido que decía "creá la jam". No se
  le buscó la vuelta. Las pruebas siguientes usaron pedidos de solo lectura.

### 6.4 Lo que muestra el diff

Agregar el asistente no tocó ningún módulo de feature existente. El tab nuevo se registró en
`:app`. Las acciones son las mismas funciones del repositorio que existían desde la fase 1, y
Konsist lo prueba en cada corrida del gate. Fue lo que D-03 y D-13 prometieron en la primera
semana.

---

## 7. Errores, y qué los atrapó

Un registro honesto vale más que una lista de aciertos.

| Error | Quién lo cometió | Qué lo atrapó | Qué cambió |
|---|---|---|---|
| Stitch inventó el bar, el admin y la arquitectura | herramienta generativa | contrastar con las decisiones escritas | los hechos viven en archivos con fuente |
| La paleta descrita en la prosa del export no aparecía ni una vez en el HTML | herramienta generativa | contar ocurrencias en el código | cuando la doc generada contradice al código generado, manda el código |
| Los subagentes debían invocar su skill sin tener la herramienta `Skill` | harness | la primera corrida del `planner` | frontmatter corregido |
| El gate podía decir `wired` sin correr nada | harness | prototipo del `planner` | criterio de cableado por módulo, aprobado por el humano |
| Una regla del singular mal copiada en la spec ("1 de 13 tema") | orquestador | el teléfono | test que falla si vuelve |
| Un `git commit -a` se llevó cambios a medio hacer de un implementer | orquestador | build roto en dos commits | commits por ruta explícita mientras corre un agente |
| La restricción de TalkBack no se le pasó al implementer | orquestador | el usuario | cada prompt con dispositivo repite los límites |
| Tests que no podían fallar (invertir la regla los dejaba en verde) | implementer | validator | romper a propósito es parte de la DoD |
| Una evidencia que decía "sin medir" al lado de la medición | implementer | validator | `revise` hasta que doc y evidencia coinciden |

Un orquestador también necesita su propio gate (§6.18).

---

## 8. Mapa a los módulos del curso

| Semana | Módulo | Aplicación en el proyecto |
|---|---|---|
| 1 | Aprender a aprender con IA | Exploración de ocho dominios, criterios de selección, diez APIs con NotebookLM y verificación contra la documentación viva |
| 2 | Prompt y context engineering | `AGENTS.md` en U, siete documentos de contexto, prompts para Stitch, skill `architecture`, specs |
| 3 | IDEs agénticos | El MVP completo construido por el pipeline `planner → implementer → validator` en Claude Code, y Codex sobre el mismo harness |
| 4 | CLI y MCP | Subagentes, skills propias, MCP de Stitch (en el scope de usuario para no versionar la key), `adb` y probes HTTP como herramientas de verificación |
| 5 | APIs de IA | Agente Koog con tool calling, salida estructurada, streaming, validación contra el catálogo y contrato de acciones |
| 6 | Prototipado y proyecto final | La app en manos del organizador: maneja las jams sin tocar la planilla |

---

## 9. Lecciones

1. **El harness importa más que el prompt.** Lo que mantuvo la calidad durante 65 rebanadas fueron
   el gate, las reglas ejecutables, la escalera de estados y el validador independiente, no una
   instrucción bien redactada.
2. **Separar roles encuentra lo que un solo agente no ve.** El que planifica, el que implementa y el
   que juzga miran cosas distintas. Un agente que se autoevalúa nunca encuentra la diferencia entre
   lo que dice y lo que hizo.
3. **Una regla que no puede fallar no prueba nada.** Esto vale para Konsist, para los tests y para
   el gate.
4. **Escribir obliga a decidir.** Cada documento nuevo destapó algo sin decidir. Lo correcto fue
   marcarlo abierto y preguntar, no inventarlo.
5. **Los subagentes no heredan contexto.** Lo que el orquestador no escribe en el prompt no existe.
6. **Cada paso manual es un cuello de botella.** Vale la pena invertir código, solo en debug, para
   sacar al humano de la verificación. No para sacarlo de las decisiones.
7. **Acotar al LLM a datos propios es la decisión técnica más importante.** El asistente es útil
   porque no puede inventar: elige, ordena y explica, y la app valida.
8. **La IA es un multiplicador del criterio, no un reemplazo.** Las 29 decisiones las tomó el
   humano. Los agentes las convirtieron en código, tests y evidencia, y avisaron cada vez que algo
   las contradecía.

---

## 10. Dónde mirar la evidencia

| Qué | Dónde |
|---|---|
| El proceso sesión por sesión y las 29 decisiones | `bb-blues-jam-bitacora.md` |
| Contexto del agente | `AGENTS.md`, `CONTEXT.md`, `docs/` |
| Subagentes y skills | `.claude/agents/`, `.claude/skills/` |
| Grafo de rebanadas con su evidencia | `feature_list.json` |
| Estado verificado e historial | `PROGRESS.md` |
| Una spec por rebanada | `docs/specs/` |
| Capturas, salidas del gate, Konsist fallando, probes | `docs/evidence/` |
| Reglas de arquitectura | `konsist-test/` |
| Gate | `init.sh` |
| Guías de migración del backend | `docs/migrations/` |
