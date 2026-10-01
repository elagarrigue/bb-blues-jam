# BB Blues Jam — Bitácora del proyecto

Registro del proceso y de las decisiones, para la presentación final de AI Expert.

Este documento está en español porque su destinatario es la presentación del curso. El código,
los commits y la documentación técnica del repositorio están en inglés.

Última actualización: 28 de septiembre de 2026 · Fase: harness contrastado con el curso, previo a
la primera línea de código de producto

---

# Parte I — El proceso

## 1. Selección del problema

### 1.1 El punto de partida

El curso pide un proyecto concreto, alcanzable, con un uso de IA que aporte valor real. La primera
idea fue una librería genérica: un asistente de IA embebible en cualquier app Android, capaz de
leer el contexto de la pantalla y ejecutar acciones. Técnicamente atractiva, pero sin usuarios ni
problema propio: una solución buscando un problema.

### 1.2 Exploración y descarte de dominios

Se evaluaron ocho dominios antes de elegir. El recorrido importa porque de ahí salieron los
criterios de selección.

| Alternativa | Por qué se descartó |
|---|---|
| Lector de Hacker News + notas | Datos poco atractivos, texto plano, nada que transformar |
| Catálogo de películas (TMDB) | La nota sobre una película es una reseña, y eso ya lo hacen otros mejor |
| Recetas + productos alimenticios | Buen cruce entre servicios, pero dominio ajeno y sin usuarios reales |
| Naturaleza, arte, historia | La nota quedaba pasiva; el asistente solo describía |
| Deportes y finanzas con predicciones | La conclusión del LLM es difícil de evaluar y roza el terreno de las apuestas |
| Carpintería y manualidades | No existen APIs públicas del dominio |
| Predictor de setlists (Setlist.fm + Ticketmaster) | Buena idea, pero sin usuarios propios |
| Creador de playlists (Deezer + Last.fm) | Sólido técnicamente, pero es un ejercicio, no un producto |

### 1.3 Criterios que emergieron

1. El asistente debe **transformar o decidir**, no resumir.
2. El resultado debe ser **verificable de un vistazo** por un humano.
3. La tarea manual equivalente debe ser **genuinamente tediosa**, para que el contraste se note.
4. Idealmente, usuarios reales y un problema propio.

### 1.4 El problema elegido

La BB Blues Jam es una jam de blues mensual en La Macanuda (Moreno 223, Bahía Blanca). Participan
entre 20 y 60 músicos por fecha.

Hoy la lista de temas se arma a mano y se comparte por WhatsApp o en papel. Problemas concretos:

- El músico no sabe de antemano qué se toca ni en qué tonalidad, así que no puede prepararse.
- No hay forma de ver dónde hay lugar. Si sos bajista, no sabés en qué temas falta bajo hasta que
  preguntás.
- La lista cambia hasta último momento y no hay una versión única confiable.
- No queda registro de jams anteriores, así que las listas se repiten sin querer.

La app resuelve esas cuatro cosas. El asistente resuelve una quinta: armar la lista lleva tiempo y
conocimiento que hoy vive únicamente en la cabeza del organizador.

**Ventaja adicional:** había trabajo previo reutilizable — un mockup interactivo, un backend en
Google Sheets vía Apps Script y una investigación hecha sobre la API de Songsterr.

---

## 2. Investigación de recursos con NotebookLM

### 2.1 Qué se cargó

Se armó un notebook con la documentación oficial de diez plataformas de música y metadatos:
Spotify, MusicBrainz, Discogs, Last.fm, Audius, Genius, TheAudioDB, Songsterr, IMSLP y Deezer.

### 2.2 Qué se le pidió

Una síntesis comparativa orientada a integración, no a marketing: para cada API, propósito, URL
base, capacidades, método de autenticación, límites de tasa y restricciones de uso comercial. Más
una tabla comparativa final.

### 2.3 Qué aportó que la búsqueda manual no hubiera dado

- **Comparabilidad.** Las diez APIs quedaron descriptas con el mismo esquema, lo que hizo evidente
  cuáles exigían OAuth y cuáles no.
- **Restricciones legales visibles.** Genius prohíbe el uso comercial sin licencia; Last.fm exige
  contacto previo para investigación. Son datos enterrados en los términos de servicio que en una
  lectura rápida se pasan por alto.
- **Límites de tasa juntos.** MusicBrainz a 1 req/s frente a Discogs a 60/min autenticado es una
  diferencia que cambia la arquitectura, y solo se ve al ponerlas lado a lado.
- **Trazabilidad.** Cada afirmación quedó referenciada a su fuente, lo que permitió verificar los
  puntos dudosos en lugar de confiar en el resumen.

### 2.4 Verificación posterior

Los hallazgos con impacto en el diseño se verificaron contra la documentación viva, porque el
notebook refleja lo que se le cargó y no el estado actual del servicio. Ahí aparecieron dos cosas
que el material no reflejaba:

- **Spotify** deprecó `audio-features`, `recommendations` y `related-artists` para apps nuevas, y
  desde febrero de 2026 limita el Development Mode a 5 usuarios con Premium obligatorio.
- **Deezer** expone sus endpoints públicos de catálogo sin credenciales, pese a que su portal habla
  de aceptar términos.

Ambas correcciones cambiaron decisiones. Ver D-09.

### 2.5 Resultado

De diez APIs quedaron tres, todas opcionales: MusicBrainz, Deezer y Last.fm. Y una conclusión más
importante que la selección: **ninguna API da tonalidad ni tempo confiables**, que es justo el dato
central de una jam. Ver D-08.

---

## 3. Definición del alcance con Claude

Conversación de diseño usada para convertir la idea en un alcance implementable. Lo que aportó, en
orden de impacto:

### 3.1 Recorte de alcance

Se descartaron, con fundamento, KMP (una semana de configuración sin aporte al resultado), iOS,
tablaturas renderizadas en la app y auto-anotación de músicos.

### 3.2 Eliminación de un problema en vez de resolverlo

La sincronización bidireccional con Google Sheets es un problema de conflictos distribuidos. En
lugar de resolverlo, se eliminó asignando **una sola autoridad por entidad**. No se pierde ninguna
capacidad pedida. Ver D-04.

### 3.3 Detección del punto ciego del LLM

El feature estrella —"jam de blues nacional argentino"— es exactamente donde el modelo es más
débil: conoce los estándares de blues, pero de Manal, Pappo o Memphis la Blusera sabe poco y
alucina títulos.

La solución fue acotar al asistente a elegir del catálogo propio cargado en el Sheet. Es la
decisión técnica más importante del proyecto. Ver D-14.

### 3.4 El contrato de acciones

Regla adoptada antes de escribir código: **si el admin puede hacerlo a mano, el asistente tiene
que poder hacerlo también.** Toda mutación existe desde la fase 1 como deeplink o como función del
repositorio. Ver D-13.

### 3.5 Corrección del patrón de presentación

Se detectaron dos errores en el patrón de presenters composables de referencia: `hashCode` derivaba
de `handle` mientras `equals` comparaba `key`, rompiendo el contrato; y el operador `invoke` se
usaba en los ejemplos sin estar declarado. Ambos quedaron corregidos en la documentación del
proyecto.

### 3.6 Consecuencias de diseño no evidentes

Del pedido "lista colapsada con filtro por instrumento libre" salieron tres implicancias que no
estaban en el pedido original:

- "Libre" no tiene definición sin un **modelo de cupos** por tema. Ver D-06.
- Si la fila colapsada muestra solo el título, el filtro queda opaco: hay que abrir tema por tema
  para ver dónde entrar. De ahí la **tira de íconos de instrumento**. Ver D-07.
- La **tonalidad no debe desaparecer** al colapsar: es el dato más buscado y ocupa tres caracteres.

### 3.7 Producción del harness

Once documentos de contexto para el agente implementador. Ver Parte III.

---

## 4. Diseño de UI

### 4.1 Punto de partida

Existía un mockup interactivo previo, con estética índigo oscuro y ámbar neón, que se mantuvo como
dirección visual.

### 4.2 Decisiones de diseño tomadas antes de generar pantallas

- **Tema oscuro únicamente.** Se usa en un bar con poca luz.
- **El ámbar no decora.** Se reserva para lo accionable: cupos libres, acción principal, estado
  publicado.
- **Prioridad de lectura:** cupos libres > tonalidad > título > artista. Es el orden en que un
  músico parado en el bar busca la información.
- **Una sola app, no dos.** Los controles de admin se suman sin reorganizar la pantalla.
- **Lista colapsada** con expansión en el lugar, cupos libres primero dentro de cada tema.

### 4.3 Pantallas definidas

Ocho: próxima jam en vista de músico, próxima jam en vista de admin, detalle de tema, lista de
jams anteriores, detalle de jam anterior, info, ingreso de admin, y asignación de músico a un cupo.

Más cinco estados: cargando, vacío, filtro sin resultados, error y sin conexión.

---

## 5. Prompt para Stitch

Stitch genera de a una pantalla y responde mejor a descripciones cortas y concretas que a un brief
largo. Por eso el material se organiza en dos partes: un estilo global que se repite en cada
prompt, y un prompt por pantalla.

El brief completo está en `bb-blues-jam-design-prompt.md`. Lo que sigue es la versión adaptada a
Stitch.

### 5.1 Estilo global (pegar al inicio de cada prompt)

> Android mobile app, dark theme only. Very dark indigo background, near black, with slightly
> lighter surface layers. Neon amber accent used sparingly, only for the primary action, active
> state and available slots. Condensed sans serif for headings, neutral highly legible sans for
> data. High contrast, designed to be read in a dark bar. Material 3 based but with its own
> identity. No stock photos. All UI copy in Spanish.

### 5.2 Prompts por pantalla

**Próxima jam, vista de músico**

> Screen showing the next blues jam session. Header with the date, venue name and address, and how
> long until the event in natural language. Below, a row of filter chips by instrument: Guitarra,
> Bajo, Batería, Voz, Armónica, Teclados. Below that, a collapsed list of songs. Each row shows the
> position number, the song title, the musical key highlighted, and a compact strip of small
> instrument icons where dimmed icons mean an open slot and lit icons mean a filled slot.

**Próxima jam, fila expandida**

> Same screen with one song row expanded in place. The expanded panel shows the artist name, then
> the open slots first, presented as available and tappable, then the filled slots below with the
> musician name and their instrument.

**Próxima jam, vista de admin**

> Same screen with admin controls. A status badge reading Borrador or Publicada, a prominent
> publish action, a button to add a song, and a drag handle on each row for reordering. In the
> expanded panel each open slot is tappable to assign a musician and each filled slot can be
> cleared.

**Detalle del tema**

> Song detail screen. Title, artist and artwork at top. The musical key displayed very large as the
> main element. Tags below it such as shuffle, 12 compases, slow blues, blues nacional. Tempo if
> available. Full lineup grouped by instrument showing open and filled slots. A button to open the
> guitar tab in the browser.

**Jams anteriores**

> Reverse chronological list of past jam sessions. Each row shows the date, venue, number of songs
> and the first few song titles. Muted archive treatment, no amber accent except on musical keys.

**Info**

> Information screen about a monthly blues jam. A short paragraph about what it is, the venue with
> address and directions, when it happens, social media links, how to join as a musician, and a
> discreet admin login entry at the bottom.

**Ingreso de admin**

> Minimal login screen with a single passphrase field and a button. No registration, no password
> recovery, no email. Show the error state.

**Asignar músico a un cupo**

> Bottom sheet to assign a musician to an open slot. The instrument is already determined by the
> slot and shown as a header. A name field with suggestions of musicians who played before.

### 5.3 Estados

> Loading state with skeleton rows, not a centered spinner.
> Empty state with a concrete invitation, not "nothing here yet".
> Filter with no results, indicating which filter is active and offering to clear it.
> Error state with a message and a retry button.
> Offline state showing cached data with a staleness indicator.

---

# Parte II — Decisiones

Cada decisión con su alternativa descartada y el motivo.

### D-01 — Android nativo, sin KMP
Se consideró Kotlin Multiplatform. Descartado: el objetivo real es una app Android y la
configuración costaba aproximadamente una semana sin aportar al resultado.

### D-02 — Presenters composables en lugar de ViewModels
El estado vive en el runtime de Compose, los presenters se testean con Molecule sin Android, y el
`UiModel` queda como objeto de datos plano — lo que en la fase 2 permite exponerlo como contexto
del asistente sin capas de adaptación.

### D-03 — Clean Architecture multi-módulo con aislamiento estricto
Ningún feature depende de otro. Contratos compartidos en `:core:*`, binding en `:app`, verificado
con Konsist y no solo documentado. Es la misma restricción que después demuestra que el asistente
no depende de las features.

### D-04 — Google Sheets como backend, con autoridad única por entidad

| Entidad | Autoridad | Dirección |
|---|---|---|
| Catálogo de temas | Sheet | Sheet → app, solo lectura |
| Jams pasadas | Sheet | Sheet → app, solo lectura |
| Lista de la próxima jam | App | app → Sheet vía Apps Script |
| Estado publicado | App | app → Sheet |

Eliminar el conflicto por diseño cuesta menos que resolverlo.

### D-05 — Solo el admin anota músicos
La auto-anotación obligaba a identidad, control de concurrencia sobre el último cupo y escritura
abierta al backend. Para 40 personas que se ven en persona, no se justifica.

### D-06 — Modelo de cupos por tema
Formación por defecto: 2 guitarras, bajo, batería, voz, armónica, teclados. Ajustable por tema. Sin
una formación declarada, "instrumento libre" no tiene definición y el filtro principal no puede
existir.

### D-07 — Lista colapsada con tira de instrumentos
Título, tonalidad y tira compacta de íconos por instrumento. La pregunta más frecuente del músico
es "¿dónde puedo tocar?", y la tira la responde sin expandir nada.

### D-08 — La tonalidad la define el admin, no una API
Ninguna API da tonalidad ni tempo confiables, y no importa: la tonalidad de una jam es la que canta
quien canta esa noche. Consecuencia: el Sheet es la autoridad de todo lo musicalmente relevante, y
el MVP funciona sin ninguna API externa.

### D-09 — APIs musicales como enriquecimiento opcional
MusicBrainz para identidad canónica y país del artista (permite filtrar "blues nacional" por dato y
no por memoria del modelo), Deezer para artwork y preview, Last.fm para artistas similares.
Descartadas Spotify, Genius, Audius, IMSLP, TheAudioDB y Discogs.

Restricción registrada: por el límite de 1 req/s de MusicBrainz, el enriquecimiento corre en
background al agregar un tema y se cachea en Room. Nunca al renderizar una lista.

### D-10 — Songsterr fuera del MVP, con el dato guardado desde ahora
Cada tema guarda su `songsterrId` y el detalle abre la tablatura en el navegador. El renderizado
interno queda para v2. Songsterr no tiene API oficial; tratarlo como opcional evita que una caída
bloquee el proyecto.

### D-11 — Autenticación por frase de acceso
Passphrase en el Sheet, validada vía Apps Script, flag en DataStore. Sin Firebase Auth ni OAuth.
Son una o dos personas administrando.

### D-12 — UI en español, código en inglés

### D-13 — Contrato de acciones definido antes del asistente
Toda mutación existe como deeplink o función del repositorio desde la fase 1. Al llegar el
asistente, se registran esas funciones desde `:app` sin tocar los módulos de feature.

### D-14 — El catálogo del Sheet es el pool de candidatos del LLM
El asistente elige del repertorio cargado, con título, artista, tonalidad, tempo, etiquetas y
dificultad. Acotar la selección a datos propios convierte el problema en filtrado y ranking sobre
información controlada, y elimina la fuente principal de error.

### D-15 — El admin es un estado, no un módulo
El admin es un músico con controles extra sobre las mismas pantallas. Cada *presenter* lee el flag
de admin y suma sus eventos y controles a su propio `UiModel`. Las mutaciones de la lista se dibujan
en `:feature:next-jam` y el ingreso por frase de acceso vive en `:feature:info`, donde está su
entrada discreta. Un `:feature:admin` separado chocaba con D-03: sus controles se dibujan sobre la
pantalla de otro módulo, que no podría importar.

### D-16 — Koin para inyección de dependencias
Sin procesamiento de anotaciones, y es lo que ya usan los artículos de referencia del patrón de
*presenters* (`koinInject()`). Solo inyección por constructor, para que los tests armen *presenters*
y repositorios a mano. Cada módulo expone su propio módulo de Koin y solo `:app` los arranca.
Descartados Hilt, por el costo de kapt/KSP y de la ceremonia de anotaciones para una app de este
tamaño, y la inyección manual, que con cuatro *features* y tres repositorios empieza a pesar en `:app`.

### D-17 — Material 3 por debajo, tokens propios por encima
`BluesJamTheme` envuelve `MaterialTheme` con un esquema oscuro armado íntegramente desde los tokens
de `DESIGN.md`, para que los componentes Material 3 hereden la paleta y las fuentes. Pero las
pantallas leen `BluesJamTheme.colors`, `.typography`, `.shapes` y `.spacing`, nunca
`MaterialTheme.colorScheme`. El ámbar no tiene nombre público: solo existe como rol
(`primaryAction`, `slotOpen`, `key`, `published`, `activeFilter`), que es la regla de diseño de que
el ámbar marca lo accionable y no decora. Se descartó usar solo Material 3, porque sus nombres de rol
(`primary`, `tertiary`) no dicen para qué se usa un color, y un sistema totalmente propio, porque
cada componente Material habría que reescribirlo. Sin tema claro ni color dinámico.

### D-18 — La formación solo se achica; los que sobran van en "Otros"
Por tema, los cupos solo se sacan de los siete por defecto, nunca se agregan. Quien toca fuera de la
formación —un saxo, percusión, una tercera guitarra— se anota en una lista "Otros" con nombre e
instrumento en texto libre. Esos participantes nunca son cupos: no están libres ni ocupados, y no
entran en "¿dónde puedo tocar?". Así el filtro principal (D-06, D-07) sigue respondiendo sobre una
formación fija y comparable entre temas, y la jam real, donde siempre se suma alguien, queda
registrada igual. La planilla lo guarda en una columna `Otros` como `Nombre (instrumento)` separados
por `;`. Se descartó permitir cupos extra por tema, porque obligaba a columnas variables en la
planilla y hacía que "hay lugar para guitarra" dependiera de cuántas guitarras se inventaron para ese
tema. Decidido por el organizador al revisar `domain-model-types`.

### D-19 — Info habla de la comunidad, no del lugar
La jam es mensual y hoy se hace en La Macanuda, pero el lugar puede cambiar. Por eso Info no muestra
lugar ni dirección: el lugar es un dato de cada jam (`Jams.lugar` en la planilla) y se ve en el
encabezado de la próxima jam. Info presenta a **Bahía Blanca Blues**, el grupo que organiza la jam y
las demás movidas de la comunidad, su programa de radio **Hideaway**, las redes y cómo sumarse:
llegar y anotarse ahí. Los hechos viven en `docs/info-content.md` con su fuente, para que ningún
agente los complete de memoria. Se descartó dejar el lugar en Info porque una pantalla estática con
una dirección vieja es exactamente la "versión no confiable" que la app vino a reemplazar. Decidido
por el organizador al revisar el contenido de Info.

### D-20 — Tempo, etiquetas, dificultad y Songsterr, fuera por ahora
El catálogo real llegó a 100 temas con título, artista y tonalidad, pero sin tempo, etiquetas,
dificultad ni `songsterr_id`. En vez de pedir que se carguen antes de seguir, el organizador decidió
no usarlos por ahora. Las columnas siguen en el esquema y los campos en el modelo, opcionales, así
que volver a usarlos no rompe nada: el detalle del tema muestra título, artista, la tonalidad bien
grande y la formación, y la rebanada del enlace a Songsterr queda bloqueada hasta que se retome.
Consecuencia a tener en cuenta para la fase 2: el asistente va a elegir del catálogo (D-14) con
título, artista, tonalidad e historial de jams, sin filtrar por etiquetas como "slow blues" o
"shuffle". "Blues nacional" sigue siendo posible por el país del artista que trae MusicBrainz (D-09).

---

# Parte III — Harness y seguimiento

## 6. Estado del harness

| Artefacto | Estado |
|---|---|
| `AGENTS.md` — contexto raíz para el agente | hecho — regenerado para la jam app en 6.3 |
| Arquitectura y reglas de dependencia | hecho, a adaptar |
| Patrón de presentación documentado | hecho |
| Contratos de API y sus trampas | hecho, a adaptar |
| Contrato de navegación y deeplinks | hecho, a adaptar |
| Contrato de acciones manual → asistente | hecho, a adaptar |
| Especificación de UI | hecho |
| Estrategia de testing y tests obligatorios | hecho |
| Definition of Done y condiciones de parada | hecho |
| Brief de diseño y prompts para Stitch | hecho |
| Repositorio git inicializado y publicado | hecho |
| Skills del curso instaladas en `.claude/skills/` | hecho |
| Documentos de descubrimiento (`build-brief`) | hecho — siete documentos, ver 6.2 |
| `feature_list.json` (`harness-starter`) | hecho — 34 rebanadas, ver 6.3 |
| Subagentes de Claude Code | hecho — tres en `.claude/agents/`, ver 6.4 |
| Skills reutilizables propias | primera hecha — `architecture`, ver 6.6; más en el módulo 4 |
| Evidencia de verificación | pendiente, en curso |

### 6.1 Sesión del 19 de septiembre — puesta en marcha del repositorio

El repo remoto `elagarrigue/bb-blues-jam` estaba creado pero vacío, así que no hubo nada que clonar:
la operación correcta fue al revés, inicializar git sobre el scaffold local de Android Studio y
publicarlo. Dos commits:

| Commit | Contenido |
|---|---|
| `19c1075` | Scaffold de Android Studio + los dos documentos de descubrimiento + `START-HERE.md` |
| `61eeeba` | Las seis skills del curso en `.claude/skills/`, más `/.idea/` al `.gitignore` |

El push por HTTPS falló: GitHub ya no acepta autenticación por contraseña, y el `gh` del sistema
estaba configurado con protocolo SSH, así que nunca instaló un credential helper para HTTPS. Se
resolvió apuntando `origin` a la URL SSH, que autenticaba bien. Queda anotado porque es el tipo de
fricción que reaparece en cualquier máquina nueva.

Las skills se instalaron dentro del repo y no en `~/.claude/skills/` para que queden versionadas:
son parte de la evidencia del harness que pide el curso. Se conservaron los `agents/openai.yaml` de
cada skill: para Claude Code son inertes, pero son el material de referencia para escribir los tres
subagentes.

El `AGENTS.md` funciona como índice y no como volcado, para que el agente lea solo el documento que
su tarea requiere. Cada regla incluye su motivo, porque una regla sin justificación se erosiona al
tercer refactor.

### 6.2 Sesión del 19 de septiembre — descubrimiento consolidado con `build-brief`

Se corrió la skill `build-brief` sobre los dos documentos de insumo. La premisa fue explícita: el
descubrimiento estaba cerrado y la tarea era consolidar, no reabrir. La skill está escrita para
entrevistar al usuario de a una pregunta por vez; acá esa entrevista ya había ocurrido —
parcialmente con NotebookLM, parcialmente en la conversación de diseño— y estaba registrada en la
bitácora y en el brief de diseño. El valor de la corrida no estuvo en preguntar sino en volcar ese
material a documentos que un agente implementador pueda leer sin contexto previo.

**Qué se generó.** Siete documentos, todos en inglés (D-12):

| Documento | Qué ambigüedad evita |
|---|---|
| `CONTEXT.md` | Deriva de vocabulario — 20 términos definidos y 7 descartados |
| `docs/build-brief.md` | Construir el producto equivocado |
| `docs/domain-model.md` | Entidades y estados inconsistentes |
| `docs/user-and-access-model.md` | Ambigüedad de permisos |
| `docs/technical-discovery.md` | Restricciones de plataforma ocultas |
| `DESIGN.md` | Que el agente improvise la UI |
| `docs/risks-and-open-questions.md` | Hacer pasar incógnitas por decisiones |

Se eligió el conjunto compacto más los tres documentos opcionales. El criterio de la skill para
separarlos es que el tema sea un riesgo de producto y no un detalle de implementación: los permisos
lo son porque D-05 y D-11 sostienen todo el modelo de escritura; la tecnología lo es porque D-04 y
el límite de 1 req/s de MusicBrainz cambian la arquitectura; el diseño lo es porque sin tokens
escritos el implementador inventa.

Se descartó generar imágenes de concepto con `imagegen`: las pantallas salen de Stitch y un set
paralelo de conceptos competiría con la fuente real.

**Qué apareció al escribir que no estaba explícito en los insumos.** Ninguna decisión nueva, pero sí
cuatro precisiones que estaban implícitas y que conviene tener escritas antes de codificar:

- **`Song` y `JamSong` son entidades distintas.** Confundirlas pierde justo lo que D-08 establece:
  la tonalidad es de la noche, no del tema. El catálogo tiene `defaultKey`; la noche tiene `key`.
- **No existe entidad `Musician`.** Un músico es un nombre en un `Slot`. Modelarlo como entidad
  implicaría una identidad que D-05 elimina a propósito.
- **"Archivada" no es un estado guardado.** Solo se persisten `DRAFT` y `PUBLISHED`; una jam es
  histórica cuando su fecha pasó. Guardar un tercer estado obligaría a alguien a mantenerlo.
- **La lista completa de mutaciones del contrato de acciones** (D-13): agregar tema, quitar tema,
  fijar tonalidad, ajustar formación, asignar músico, liberar cupo, reordenar y publicar. D-13 exige
  que todas existan desde la fase 1, así que la lista tiene que estar cerrada *antes* de rebanar las
  features, no descubrirse feature por feature. Quedó como bloqueante.

**Tres puntos levantados para confirmación, no resueltos por cuenta propia:**

1. Los valores hexadecimales de `DESIGN.md` son una propuesta derivada de la dirección escrita
   ("índigo oscuro, ámbar neón"), no medidos del mockup original. Si el mockup aparece, hay que
   reconciliar.
2. La tira de instrumentos distingue cupo libre de cupo cubierto **solo por brillo**, que es
   exactamente lo que falla con poca visión o con reflejo — en un bar oscuro, el contexto previsto.
   Se agregó un requisito de accesibilidad: que la distinción también use forma o relleno.
3. Una publicación que falla en silencio es el peor resultado posible del producto: el admin cree
   que la lista está publicada y los músicos leen otra cosa. Quedó registrado como riesgo con
   mitigación de error no ignorable.

**Lo que confirmó la corrida sobre el método.** Una skill de descubrimiento sobre un descubrimiento
ya hecho no es redundante, pero tampoco hace lo que anuncia: no descubre, traduce. El insumo estaba
en dos documentos escritos para humanos —una bitácora narrativa y un brief de diseño— y la salida
son siete documentos escritos para un agente sin contexto. El trabajo real fue decidir qué de lo
narrado era decisión estable y qué era todavía suposición, que es justo lo que separa
`build-brief.md` de `risks-and-open-questions.md`.

El bloqueante que quedó arriba de todo es el **esquema del Sheet**. Traba el código de repositorio y,
por separado, traba al asistente: la semana 5 necesita repertorio real cargado, y con doce temas no
se arma una lista temática interesante. Conviene empezarlo en paralelo.

### 6.3 Sesión del 19 de septiembre — harness mínimo con `harness-starter`

Se corrió `harness-starter` sobre los siete documentos de descubrimiento. Produce cuatro archivos y
tiene prohibido producir cualquier otro: `AGENTS.md`, `init.sh`, `PROGRESS.md` y
`feature_list.json`.

**`init.sh` adaptado a Gradle.** El proyecto de referencia del curso es Next.js con pnpm, así que el
script había que escribirlo de cero: envuelve `./gradlew build` y `./gradlew check`. Se le agregó
algo que no estaba en la plantilla: un chequeo que informa si Konsist, detekt y ktlint están
cableados. Hoy los tres dan `NOT WIRED YET`, que es la verdad — `check` corre tests unitarios y lint
y nada más. Un gate que dice correr herramientas que no corre es peor que no tener gate, porque da
una señal verde falsa en cada validación de `feature-flow`.

Se corrió: sale en verde, exit 0.

**`feature_list.json`: 34 rebanadas.** La skill pide rebanadas del tamaño de una sesión y advierte
contra dos errores opuestos: épicas que nunca terminan y tareas tan chicas que solo generan
administración. Para un MVP con dos roles, backend, estado publicado y caché, espera entre 12 y 30;
salieron 34, en el límite superior, porque el contrato de acciones de D-13 obliga a que cada
mutación sea su propia rebanada verificable: agregar tema, quitar tema, fijar tonalidad, ajustar
formación, asignar músico, liberar cupo, reordenar y publicar. Son ocho rebanadas que en otro
proyecto serían una sola llamada "edición de la lista".

**El estado `blocked` se usa una sola vez.** La tentación era marcar como bloqueadas las 27
rebanadas que dependen del esquema del Sheet, pero eso confunde dos cosas distintas: tener
dependencias sin cumplir es lo normal en un grafo, estar bloqueado es esperar una decisión humana.
Solo `sheet-schema-definition` está bloqueada de verdad. El resto queda `not_started` con sus
dependencias declaradas.

Esa distinción tiene una consecuencia práctica que valía la pena calcular: **ocho rebanadas son
alcanzables sin desbloquear el Sheet** — el baseline de Kotlin y Compose, el esqueleto de módulos,
Konsist, detekt y ktlint, el harness de presenters con Molecule, los tokens de diseño, los tipos de
dominio y la pantalla de info. Alcanza para varias sesiones de trabajo real mientras el esquema se
define en paralelo.

**Lo que confirmó sobre el método.** `build-brief` tradujo documentos para humanos a documentos para
un agente; `harness-starter` traduce esos documentos a un grafo ejecutable. El paso que agrega valor
no es generar los cuatro archivos sino ordenar el trabajo por dependencias reales: al escribir el
grafo aparece qué se puede hacer hoy y qué espera, que es exactamente la pregunta que un plan por
fases no responde. De ahí que el curso insista en que `feature_list.json` reemplaza al plan por
fases y no lo complementa.

### 6.4 Sesión del 19 de septiembre — los tres subagentes

El repo del curso trae un `agents/openai.yaml` por skill, pero resultaron ser solo metadatos de
interfaz: nombre para mostrar, descripción corta y un prompt por defecto. No hay nada de rol ahí.
El contrato real estaba en el `SKILL.md` de `feature-flow`, que nombra a los tres subagentes y
define el ciclo completo.

**Los archivos son delgados a propósito.** `feature-flow` dice explícitamente que no se dupliquen
los prompts de rol, porque las instrucciones estables ya viven en las skills. Cada subagente
entonces hace tres cosas: invoca su skill, declara su frontera de rol, y agrega lo único que la
skill no puede saber — las reglas de este proyecto.

Esa tercera parte es la que justifica el trabajo. Las decisiones D-01 a D-14 son invisibles para una
skill genérica: que no haya ViewModels, que ningún módulo de feature importe a otro, que ninguna
mutación exista solo en la UI. Cada subagente las recibe en la forma que le sirve — el `planner`
como restricciones que una spec debe respetar, el `implementer` como defectos aunque la spec calle,
el `validator` como lista de lo que un build en verde igual no detecta.

**El `validator` tiene herramientas de solo lectura más Bash.** Puede volver a correr el gate pero
no puede editar. Es una restricción deliberada: un validador que puede arreglar lo que juzga deja
de ser independiente, y la independencia es la única razón por la que el rol existe.

**Dos huecos que aparecieron al leer el contrato de `feature-flow`.** Ninguno era visible desde la
sesión anterior:

- El pipeline usa un estado `accepted` que `feature_list.json` no documentaba. La distinción
  importa: `passing` significa que el implementador se autoverificó, `accepted` que un validador
  independiente aceptó. Una dependencia solo se considera satisfecha cuando está `accepted` — si se
  confundieran, la autoverificación alcanzaría para desbloquear features, que es justo lo que el
  pipeline de tres roles busca evitar. Quedó documentado en `AGENTS.md`.
- `feature-flow` verifica con `CI=true ./init.sh`. El script no contemplaba esa variable, así que se
  le agregó una guarda para que en modo CI nunca intente arrancar la app.

**Lo que confirmó sobre el método.** El valor de los tres archivos no está en orquestar —de eso se
encarga la skill— sino en que el conocimiento del proyecto viaje con el rol. Escribir "no uses
ViewModels" una vez en `AGENTS.md` no alcanza, porque cada rol necesita esa misma regla en un
tiempo verbal distinto: planificar sin violarla, implementar sin violarla, y detectarla violada.

### 6.5 Sesión del 19 de septiembre — export de Stitch y reconciliación de tokens

Se generaron las pantallas en Stitch y se revisó lo que devolvió. El hallazgo principal no fue
visual: **el resumen del proyecto que escribe Stitch inventa hechos de producto.**

| Dice Stitch | Está decidido |
|---|---|
| El Motivo Bar | La Macanuda, Moreno 223 |
| El admin se llama "Fede" | No existe ese nombre; admin es un rol |
| Formación de 6 puestos, armónica y teclados compartiendo uno | 7 cupos por defecto, ajustable por tema (D-06) — **el resumen se equivoca, las pantallas están bien** |
| WebSockets o SSE para tiempo real | Sheets con autoridad única, sin sincronización (D-04) |
| IndexedDB / LocalStorage | Room (D-01) |
| PWA / Android | Android nativo (D-01) |
| Progresión I-IV-V, afinaciones, backline en el detalle | No están en el modelo de dominio |

Las últimas cuatro son coherentes entre sí: Stitch diseñó una PWA, que es lo que sabe hacer. Era
esperable y no invalida los valores visuales. Las dos primeras son alucinación lisa y llana sobre
datos que el prompt no le dio.

**La formación parecía la divergencia grave, y no lo era.** Leyendo el markup del primer tema
daba que faltaba Teclados, lo que habría roto D-06 en todas las pantallas. Al mirar el PNG
renderizado apareció lo contrario: el tema 02 muestra `TEC: LIBRE`, el 05 también, y el 01
efectivamente no lleva teclados. O sea que la formación **varía por tema**, que es exactamente lo
que D-06 pide. Stitch lo implementó bien; su resumen lo describió mal.

Queda como recordatorio de método: el markup de un tema es una muestra, no el modelo. Verificar
sobre lo renderizado y no sobre el primer fragmento que uno abre.

**Lo que sí aportó, y era el objetivo del export:** valores reales. La paleta de `DESIGN.md` dejó de
ser una propuesta derivada de una descripción escrita y pasó a estar medida — fondo `#111318`,
superficies `#1A1B21` y `#1E1F25`, borde `#514532`, ámbar `#FFB300` — más una tipografía con
nombre, Barlow Condensed, coherente con la dirección de cartelera vintage. Cuatro tokens siguen
derivados (`textMuted`, `slotFilled`, `archive`, `error`) porque el export los describió con
palabras y no con valores. Queda anotado cuáles son cuáles: un token medido y uno inventado no
merecen la misma confianza.

> *Corrección del 28 de septiembre.* Esta sección decía originalmente fondo `#0C0E13` y borde
> `#282A30`, tomados de una primera reconciliación contra el resumen del export. La revisión del
> código desempaquetado se quedó con el *front matter* Material 3, y `DESIGN.md` ya tenía los
> valores correctos; esta prosa no se había actualizado. Lo detectó el `planner` de
> `design-tokens-theme`, que además aclaró que los cuatro tokens "derivados" sí son valores del
> esquema Material 3 del export: lo derivado es qué rol se le asignó a cada uno.

También confirmó que la dirección sobrevivió el viaje de ida y vuelta: ámbar con semántica
funcional estricta y sus cuatro usos reservados, cupos cubiertos en gris, 48dp, sin scroll
horizontal, cupos libres arriba, y español rioplatense con *vos*.

**Un archivo que se contradice a sí mismo.** El export trae su propio `DESIGN.md`, y su front
matter YAML y su prosa describen paletas distintas: la prosa nombra un set índigo (`#090B10`,
`#181E2B`), el front matter declara un theme Material 3 (`#111318`, `#1A1B21`). Se resolvió
contando ocurrencias en el HTML de las doce pantallas: los valores de la prosa aparecen **cero
veces**. El código usa el front matter. Los tokens del proyecto siguen al código, porque es lo que
efectivamente se dibujó y lo que muestran los PNG.

Sirve como criterio general: cuando la documentación generada contradice al código generado, el
código es el artefacto y la prosa es un relato sobre él.

**La tira de instrumentos volvió mejor de lo especificado.** El brief pedía íconos de ~16dp,
apagados para libre y encendidos para cubierto — justo el esquema que había quedado anotado como
problema de accesibilidad, porque el brillo solo falla con poca visión y con reflejo. Stitch
devolvió otra cosa: **chips con texto**. Un cupo libre dice `GTR: LIBRE` en ámbar sobre fondo
tintado con un punto pulsante; uno cubierto dice `Gtr: Tincho` en gris con un check.

Tres señales distinguen los estados —relleno, glifo y texto— y ninguna depende del brillo. La
preocupación de accesibilidad quedó resuelta, no por haberla corregido sino porque el diseño
generado nunca la tuvo. Cuesta ancho: los chips envuelven a dos líneas en un tema cargado. Se
aceptó, porque un chip no necesita leyenda y además contesta "¿quién toca esto?" sin expandir.

**Se conectó el MCP de Stitch** en el scope de usuario, no en el repo: el comando lleva la API key
en un header, y una key versionada es una key filtrada.

**Lo que confirmó sobre el método.** Una herramienta generativa produce dos cosas a la vez —
artefactos y afirmaciones— y solo una de las dos es su trabajo. Stitch diseña; cuando además
describe el producto, está completando huecos con lo que le parece plausible. La lección práctica
es tener a qué contrastar: sin las 14 decisiones escritas, "El Motivo Bar" y la formación de seis
cupos habrían entrado al proyecto sin que nadie los notara, y el error de la formación habría
aparecido recién al implementar la tira de instrumentos.

### 6.6 Sesión del 28 de septiembre — el harness contra el material del curso

Terminada la semana 2, se contrastó el repo con las slides de las dos primeras clases. Se buscaba
lo que el curso pide explícitamente y el proyecto no hacía, no volver a validar lo ya decidido.

**Lo que ya cumplía.** `AGENTS.md` en 106 líneas, bajo el techo práctico de ~150 que da la clase y
lejos del límite duro de 32 KiB. Reglas verificables en lugar de adjetivos ("no ViewModels", no
"buena arquitectura"). Especificaciones largas en `docs/` con un enlace, vocabulario de dominio y
Definition of Done. El `CLAUDE.md` que propone el ejercicio no hizo falta: Claude Code lee
`AGENTS.md` cuando no hay `CLAUDE.md`, y en Windows un symlink trae más fricción que beneficio.

**Tres huecos, ninguno grave:**

- **Posición.** La clase insiste en el *layout* en U: los modelos atienden mejor al principio y al
  final, y el centro es zona de baja atención (*lost in the middle*). Las reglas innegociables del
  proyecto estaban justo en el centro, en la línea 46, y faltaban los comandos literales. Se
  reordenó: arriba rol, reglas y comandos; en el medio, referencia; al final, DoD y un recordatorio
  de lo crítico. La clase lo llama "repetición estratégica": dos líneas que aumentan la fiabilidad.
- **La skill de arquitectura.** Es un ejercicio explícito del módulo 6 y estaba planeada para la
  semana 4. Se adelantó porque este proyecto la necesita más que la tienda de ejemplo: sin ella, el
  `implementer` iba a improvisar la estructura del primer módulo en `module-skeleton`.
- **La primera `feature-spec`**, que es el cierre de la semana 2. Queda como siguiente paso.

**Qué apareció al escribir la skill.** Lo mismo que en 6.2: escribir obliga a notar lo que no está
decidido. Tres cosas quedaron marcadas como abiertas en lugar de inventadas:

- **El framework de inyección de dependencias** no figura en ninguna de las 14 decisiones.
- **El ejemplo de referencia del *presenter* no está en el repo.** La documentación dice que se
  corrigieron dos errores, pero el código corregido no existe. La skill describe el patrón hasta
  que `molecule-presenter-harness` deje un ejemplo canónico.
- **Un área del grafo no es un módulo.** `feature_list.json` separa `feature-admin` de
  `feature-next-jam`, pero los controles del admin se dibujan sobre la misma pantalla que ve el
  músico ("una sola app, no dos"), y un módulo de *feature* no puede importar a otro (D-03). Si
  cada área fuera un módulo, las dos decisiones chocarían. La skill fija un valor por defecto
  —controles de admin dentro de `:feature:next-jam`, mutaciones en `:core:data`— y deja la
  decisión final a la primera rebanada de admin.

**Un hueco que el pipeline iba a destapar recién al correr.** Ninguno de los tres subagentes
declara la herramienta `Skill` en su *frontmatter*, pero los tres tienen la instrucción de invocar su
skill. El error no se veía porque el pipeline todavía no se ejecutó ni una vez. Quedó registrado
como riesgo en `PROGRESS.md`, a confirmar en la primera corrida del `planner`.

**Material del curso fuera del repo.** Las slides quedaron en `docs/clases/` y en `.gitignore`: el
repo es público y el material no es nuestro para redistribuir.

**Lo que confirmó sobre el método.** Un `AGENTS.md` correcto en contenido puede estar mal en
forma. Cada regla se cumplía, pero estaba en el lugar donde el modelo la lee con menos atención.
Nada cambió de fondo y el archivo es más confiable: la clase lo resume en que un `AGENTS.md` no es
documentación sino ingeniería de contexto, y dónde está algo pesa tanto como qué dice.

### 6.7 Sesión del 28 de septiembre — Koin, admin como estado y el ejemplo del *presenter*

Tres decisiones del usuario cerraron dos de los puntos que la skill `architecture` había dejado
abiertos, y quedaron registradas como D-15 y D-16.

**El ejemplo del *presenter* salió de los dos artículos de Doximity que originan D-02.** Al leerlos
con atención aparecieron, en el propio material de referencia, los dos errores que la sección 3.5
anotaba: `EventHandler.equals` compara `key` mientras `hashCode` usa `handle`, y la vista llama
`uiModel.events(...)` sin que `invoke` esté declarado, algo que no compila. Hasta ahora la bitácora
decía que "quedaron corregidos en la documentación del proyecto", pero esa documentación no existía
en el repo. Ahora sí: `references/presenter-pattern.md`, con una tabla de cada desvío respecto de los
artículos y su motivo. Uno de esos desvíos es de actualización: Molecule renombró
`RecompositionClock` a `RecompositionMode`.

El ejemplo no es un contador: es la pantalla de la próxima jam, con un *presenter* hijo por fila y el
admin como estado. Así el patrón queda demostrado sobre el dominio real, con las reglas que importan
—mutaciones por repositorio (D-13), borrador visible como mensaje y no como lista vacía, y el error
de publicación nunca silenciado.

**Al escribirlo aparecieron dos errores propios, detectados antes del commit.** Un `listOf(position = …)`
que no es Kotlin válido, y un comentario que decía "cupos libres primero" sobre un código que no
ordenaba. El segundo es más interesante: al ordenar, el índice que viaja en el evento `ClearSlot`
dejaba de coincidir con el cupo real. Se resolvió haciendo que cada cupo lleve su índice original.
Es el tipo de error que un test habría encontrado y una lectura rápida no. Por eso el ejemplo se
declara como referencia no compilada, y `molecule-presenter-harness` lo tiene que convertir en
código con un test en verde.

**Admin como estado reordenó el grafo sin cambiarlo.** Las nueve rebanadas de `feature-admin` pasaron
a `feature-next-jam` (ocho mutaciones) y `feature-info` (el ingreso). Las dependencias no cambiaron:
confirma que el área era una etiqueta y no una frontera.

**Lo que confirmó sobre el método.** "Corregido en la documentación" no es evidencia si nadie puede
abrir esa documentación. La corrección existía en la cabeza y en una frase de la bitácora; recién al
escribir el ejemplo completo se volvió algo que un agente puede leer y un validador puede contrastar.

### 6.8 Sesión del 28 de septiembre — el esquema del Sheet, sembrado con una jam real

El bloqueante que venía arriba de todo desde 6.2 se resolvió con dos insumos del organizador: la
planilla que se va a usar y una foto del *setlist* real del sábado 25 de julio de 2026 en La
Macanuda, con trece temas, artista y tono.

**La propuesta era un tab por fecha, y se mantuvo con un ajuste.** Un tab por fecha es como el
organizador ya piensa la jam: una hoja, una noche, como el póster. El riesgo estaba en la misma
foto: "B.B King" en el tema 1 y "B.B. King" en el 5. Si cada tab repite título y artista como texto,
el mismo tema termina escrito de tres formas, y se rompen justo las dos cosas por las que existe el
historial: saber si un tema ya se tocó, y el *pool* de candidatos del asistente (D-14). La solución
fue un tab `Catalogo` con un identificador estable por tema, y que cada tab de fecha lo referencie.

**Lo que el esquema resolvió de paso.** Tres cosas que estaban abiertas:

- **El tab de asignaciones desapareció.** Los cupos son columnas del tab de fecha, una por
  instrumento de la formación por defecto: vacía es libre, un nombre es cubierto, `-` es que ese
  instrumento no va en ese tema. Es D-06 en tres símbolos que el admin puede tipear a mano.
- **Las sugerencias de nombres** para asignar un cupo salen de los tabs de fechas anteriores, sin
  una lista aparte de músicos y sin la entidad `Musician` que D-05 evita a propósito.
- **Un tema borrado del catálogo** no rompe una lista vieja: el tab de fecha guarda título y artista
  como texto, y la app los usa solo si el identificador ya no existe. Por eso esas columnas tienen
  que ser texto y no una fórmula de búsqueda: una fórmula falla exactamente cuando se la necesita.

**Lo que se dejó afuera del Sheet a propósito.** Los datos de MusicBrainz y Deezer. Si la app los
escribiera en el catálogo, pasaría a ser escritora de una entidad cuya autoridad es el Sheet, que es
la sincronización bidireccional que D-04 elimina. Viven en el caché de Room.

**Una precisión al modelo de dominio.** `Jam` gana `startTime`: el póster dice "21 hs" y el
encabezado de la próxima jam lo necesita. Era un dato que el modelo no tenía porque nunca se había
mirado un póster real.

**La planilla no se escribió desde acá.** Es privada y no había conector autenticado, así que la
sesión dejó cuatro CSV para importar. Los datos de la foto se normalizaron con confirmación del
organizador —"Memphis" es Memphis La Blusera, que importa porque en la fase 2 "blues nacional" se
filtra por artista— y lo que la foto no dice quedó vacío en lugar de inventado: tempo, etiquetas y
dificultad son carga del admin, y son precisamente lo que el asistente va a usar.

**Lo que confirmó sobre el método.** Un dato real vale más que el esquema mejor pensado en abstracto.
En una sola imagen aparecieron la hora que faltaba en el dominio y la inconsistencia de nombres que
justifica el catálogo. Ninguna de las dos salía de los documentos.

### 6.9 Sesión del 28 de septiembre — primera rebanada por el pipeline completo

`gradle-kotlin-compose-baseline` fue la primera *feature* que recorrió `planner` → `implementer` →
`validator`, con revisión humana de la spec en el medio. Terminó `accepted`.

**El pipeline encontró su propio defecto antes de producir nada.** Los tres subagentes tenían
instrucciones de invocar su skill, pero ninguno tenía la herramienta para hacerlo. Se corrigió en
6.6 y el `planner` lo confirmó en la primera corrida: cargó `feature-spec` sin leerlo por ruta.

**El `planner` leyó el repo antes de opinar.** Detectó que AGP 9 ya compila Kotlin, y que agregar el
plugin de Kotlin habría roto el build. También notó que el *manifest* no tenía ninguna *activity*,
con lo que "la app arranca" ni siquiera era cierto antes de empezar. Y encontró un problema de orden
en otra rebanada: `design-tokens-theme` necesitaba `:core:ui` y no dependía de quien lo crea. Se
corrigió con aprobación humana.

**El dispositivo real encontró lo que ningún documento anticipaba.** Con el teléfono en modo claro,
`enableEdgeToEdge()` sin argumentos dibujaba íconos oscuros sobre el fondo oscuro. El `implementer`
se desvió de la spec, lo declaró y lo registró; el `validator` lo reprodujo y lo juzgó justificado.
Es el caso que justifica que la desviación sea visible y no silenciosa.

**El `validator` también juzgó a la spec.** El checklist pedía cada color una sola vez, y la misma
spec pedía un literal en el `@Preview`, que no puede leer un recurso. Lo dictaminó como
inconsistencia de la spec y no defecto del código, y quedó registrado en la spec como desviación
aceptada.

**Lo que confirmó sobre el método.** Cada rol encontró algo que el anterior no podía ver: el
`planner`, lo que había en el repo; el `implementer`, lo que pasa en un teléfono; el `validator`, las
contradicciones del contrato. Ninguno de los tres hallazgos habría salido de un solo agente que
planifica, implementa y se autoevalúa.

### 6.10 Sesión del 28 de septiembre — el esqueleto, Konsist y el gate que decía la verdad

Dos rebanadas más cerraron `accepted`, `module-skeleton` y `konsist-isolation-rules`, y una tercera
quedó planificada y frenada a propósito. El patrón se repitió en las tres: el `planner` dejó de
escribir especificaciones sobre lo que creía y pasó a probarlas en una copia descartable del repo
antes de proponerlas.

**Una regla que no puede fallar no prueba nada.** Cada restricción se demostró rota y después
reparada: un `import android.*` en `:core:model` que no compila, y en Konsist ocho reglas, cada una
con su violación plantada. Para la regla central —ninguna *feature* importa otra— todavía no existían
módulos de *feature*. Se crearon dos descartables, `feature/probe-a` y `feature/probe-b`, se mostró
que el *gate* falla con un import cruzado indicando archivo y línea, y se borraron comprobando por
*hash* que el árbol quedó idéntico. Esa salida es la evidencia que en la semana 5 va a mostrar que el
asistente no tocó los módulos de *feature*.

**El *gate* podía mentir de dos maneras, y las dos aparecieron al prototipar.** Gradle daba por "al
día" el test de Konsist aunque se violara una regla en otro módulo, porque los fuentes ajenos no eran
entradas de la tarea: el *gate* quedaba verde con el aislamiento roto. Y `init.sh` marcaba Konsist como
"cableado" solo porque el módulo se llama `konsist-test`, aunque no corriera ningún test. Las dos se
corrigieron, y el cambio a `init.sh` se hizo recién con aprobación explícita: `AGENTS.md` prohíbe
cambiar reglas de verificación en silencio, y un *gate* que dice "wired" sin correr nada es peor que
no tener *gate*.

**El validador también encontró lo que la spec decía mal.** La spec pedía reemplazar "las dos
líneas" de un comentario que tenía una sola. El `implementer` aplicó lo aprobado al pie de la letra y
avisó del resto en lugar de reescribirlo por su cuenta; el `validator` lo dictaminó inconsistencia de
la spec, y el comentario se corrigió después con aprobación.

**Un bloqueo real, por primera vez.** Al planificar detekt, el prototipo mostró que ninguna versión
estable corre en este entorno: el *daemon* de Gradle está fijado en Java 25 por la plantilla de
Android Studio, y detekt 1.23.8 falla de tres maneras distintas. Solo corre la 2.0.0-alpha.6. Poner
una alfa en el *gate* o bajar el JDK del *daemon* es una decisión de proyecto, no de un agente, y el
flujo se detuvo ahí. También me encontré un olvido propio de la sesión del esquema:
`technical-discovery.md` seguía diciendo que el esquema no estaba definido.

**Lo que confirmó sobre el método.** Pedirle a un agente que pruebe antes de especificar cambió la
calidad de las specs más que cualquier instrucción de formato. Las tres decisiones más importantes de
estas rebanadas —que Konsist necesitaba declarar entradas, que `init.sh` mentía y que detekt estable
no corre— salieron de ejecutar, no de leer documentación.

### 6.11 Sesión del 28 de septiembre — el *gate* completo, el patrón compilado y el tema

Tres rebanadas más cerraron `accepted` —`detekt-ktlint-gate`, `molecule-presenter-harness` y
`design-tokens-theme`— con el flujo corriendo de corrido y frenando solo donde había una decisión
humana. Frenó dos veces, y las dos eran decisiones de verdad: poner una versión alfa de detekt en el
*gate* (ninguna estable corre sobre el Java 25 del *daemon*) y commitear fuentes de terceros. La
tercera, cómo se construye el tema, quedó registrada como D-17.

**El patrón de los artículos, por fin compilado.** Lo que en 6.7 era un ejemplo "de referencia, no
compilado" pasó a ser código con tests: `EventHandler` con `equals` y `hashCode` derivados de la
misma clave, `invoke` declarado, y un *presenter* de muestra probado con Molecule. Los dos errores de
los artículos de Doximity ahora tienen tests que fallan si vuelven; el `validator` los rompió de
formas que el `implementer` no había probado —un `equals` que ignora la clave, un `invoke` vacío— y
los tests los atraparon. La referencia de la skill dejó de ser prosa: copia los archivos reales, y
el `validator` lo verificó con un script, letra por letra.

**Un hallazgo que no estaba en ningún documento.** Los tests de Molecule fallaban en la JVM porque el
runtime de Compose de Android llama a `android.os.Trace`. Ni los artículos ni la documentación lo
mencionaban; apareció al ejecutar. Se resolvió con una línea que ahora es convención para todo módulo
con *presenters*, con su costo anotado: en esos tests, una llamada accidental a Android devuelve un
valor por defecto en lugar de fallar.

**El ámbar dejó de ser un color y pasó a ser un rol.** La regla de diseño de 4.2 —"el ámbar no decora,
marca lo accionable"— ahora es código: el ámbar no tiene nombre público, solo existe como
`primaryAction`, `slotOpen`, `key`, `published` y `activeFilter`, y un test falla si aparece en otro
rol. El `validator` lo probó pintando de ámbar el tinte de superficie y el color de archivo, y los dos
casos fallaron. El contraste se midió en vez de estimarse: ámbar sobre fondo 10,35, texto atenuado
sobre superficie 10,10, todo AA y AAA. Y en el teléfono apareció un detalle que ningún documento
podía anticipar: la pantalla del Pixel 5 trabaja en Display P3, así que el ámbar `#FFB300` se lee
`#F4B63F` en una captura; la spec calculó los valores esperados en P3 y coincidieron.

**El *gate* se volvió más estricto a cada paso, nunca más laxo.** Detekt y ktlint entraron sin
archivo de *baseline* que escondiera problemas; `init.sh` pasó a decir "cableado" solo si la
herramienta corre en todos los módulos, nombrando los que falten; y una regla nueva de Konsist impide
literales de color fuera de `:core:ui`. Los subagentes ahora tratan cualquier `NOT WIRED` como defecto.

**Lo que confirmó sobre el método.** La autonomía no reemplazó el criterio humano: lo concentró.
Corriendo sin pedir permiso en cada etapa, el flujo se detuvo exactamente donde una decisión
cambiaba el proyecto —una dependencia alfa, archivos de terceros, una regla de arquitectura nueva— y
en ningún otro lado. Las preguntas pasaron de "¿sigo?" a "¿esto?".

### 6.12 Sesiones del 29 y 30 de septiembre — el dominio, la planilla y la primera pantalla real

Tres rebanadas más cerraron `accepted`: `domain-model-types`, `sheet-schema-definition` e
`info-screen`, la primera pantalla con contenido propio y el primer módulo de *feature*. Lo más
valioso no fue el código sino dónde se frenó el flujo: cada vez que un agente tocó un hecho del mundo
real, se detuvo y preguntó.

**Dos decisiones de producto salieron de preguntas técnicas.** El `planner` de los tipos preguntó si
un tema podía tener más cupos que la formación por defecto; el organizador respondió con algo que
ningún documento tenía: los cupos solo se sacan, y quien toca fuera de la formación va en una lista
"Otros" con nombre e instrumento (D-18). Y al pedir el contenido de Info, el Instagram mostró una jam
los martes en otro lugar: resultó ser Hideaway, el programa de radio, y reveló que el lugar de la jam
puede cambiar, así que Info habla de la comunidad y el lugar va en cada jam (D-19). Si el agente
hubiera resuelto la contradicción por su cuenta, Info mostraría hoy una jam semanal que no existe.

**El esquema se reconcilió contra el código, no contra la memoria.** Con los tipos ya compilados, el
`planner` de la planilla recorrió campo por campo el esquema contra el código y encontró once
diferencias de documentación —un formato de id más estricto en el código que en la doc, valores de
enum sin mapeo escrito, una regla de "jam futura" que contradecía a `isHistorical` en el día de la
jam— y un hueco real: con una sola guitarra, la app no sabía en qué columna escribir. Lo decidió el
organizador. El `validator` escribió su propio chequeo del *seed*, independiente del
del `implementer`, y los dos coincidieron.

**La primera pantalla probó el camino completo.** Info trajo el primer módulo de *feature*, Koin y un
contrato compartido para abrir enlaces. En el Pixel 5 se abrieron Instagram, YouTube y Linktree, y
"atrás" volvió a la app en la misma posición. Los textos se aprobaron antes de escribirse y un test
falla si alguien cambia una palabra. Y el teléfono mostró algo que ningún documento podía saber: el
enlace de YouTube del Linktree abre un canal llamado "Radio Hideaway". No se corrigió en silencio:
quedó registrado para que lo confirme el organizador.

**El `validator` encontró el primer hueco del *gate* que importa.** Pintar de ámbar una pantalla o leer
`MaterialTheme.colorScheme` —justo lo que prohíbe D-17— pasa todas las verificaciones: solo lo atrapa
un `grep` de la spec. Quedó propuesto como regla de Konsist para la próxima pantalla, a aprobar por
el usuario. Es el tipo de hallazgo que justifica que el validador intente romper cosas en lugar de
confirmar las que ya pasaron.

**Lo que confirmó sobre el método.** "No inventar hechos" funcionó como regla solo cuando se volvió
un archivo. `docs/info-content.md` guarda cada dato de Info con su fuente —el usuario, el Instagram,
el teléfono— y los agentes escriben desde ahí. La lección de Stitch en 6.5 ("El Motivo Bar") se
convirtió en infraestructura.

## 7. Mapa a los módulos del curso

| Semana | Módulo | Aplicación |
|---|---|---|
| 1 | Aprender a aprender con IA | Exploración de dominios, investigación de diez APIs con NotebookLM, definición del problema |
| 2 | Prompt y context engineering | `AGENTS.md` en U, documentos de contexto, prompts de diseño, skill `architecture`, especificaciones |
| 3 | IDEs agénticos | Implementación de la app base con agentes en el editor |
| 4 | CLI y MCP | Skills propias; posible MCP para Google Sheets |
| 5 | APIs de IA | El asistente: function calling sobre el contrato de acciones, pool de candidatos del Sheet |
| 6 | Prototipado y proyecto final | Cierre del MVP, Songsterr si hay tiempo, vídeo |

## 8. Evidencia a capturar

Cosas que hay que registrar mientras se avanza, porque después no se recuperan:

- [ ] Captura de la app **sin** el asistente (el "antes")
- [ ] Vídeo del flujo manual completo, cronometrado: armar una lista de 12 temas a mano
- [ ] El mismo resultado pedido al asistente en una frase, cronometrado
- [ ] Un caso donde el asistente se equivoca y cómo lo corrige el humano — vale más que tres casos
      exitosos
- [ ] Diff de los módulos de feature al agregar el asistente, demostrando que no se tocaron
- [ ] Salida de Konsist probando el aislamiento entre features
- [ ] Una lista generada para una jam temática junto al repertorio de origen, mostrando que no
      inventó nada

## 9. Riesgos abiertos

| Riesgo | Mitigación |
|---|---|
| Alcance grande: dos roles, backend propio, estado publicado, sincronización | Songsterr y enriquecimiento por APIs son recortables sin afectar el MVP |
| Songsterr no tiene API oficial | Opcional por tema; la app funciona sin tablaturas |
| El repertorio del Sheet puede quedar chico para que el asistente proponga algo interesante | Cargar el catálogo durante las semanas 1 y 2 |
| Latencia de Apps Script en escrituras | Estado optimista en el presenter y confirmación posterior |
| El asistente propone temas fuera del catálogo | Validar toda sugerencia contra el catálogo antes de ejecutar la acción |
| Una publicación falla en silencio y los músicos leen una lista desactualizada | Error no ignorable al publicar; registrar localmente los fallos de escritura |
| La tira de instrumentos distingue libre de cubierto solo por brillo | Sumar forma o relleno a la distinción, más etiqueta accesible por ícono |

## 10. Próximos pasos

1. ~~Correr `build-brief` sobre los dos documentos de descubrimiento~~ — hecho, ver 6.2
2. ~~Correr `harness-starter`~~ — hecho, ver 6.3
3. ~~Escribir los tres subagentes de Claude Code~~ — hecho, ver 6.4
4. ~~Generar las pantallas en Stitch~~ — hecho, ver 6.5; export descomprimido en `docs/design/`
5. ~~Contrastar el harness con las clases 1 y 2~~ — hecho, ver 6.6
6. **Primera `feature-spec`** sobre `gradle-kotlin-compose-baseline`, confirmando que los
   subagentes cargan su skill
7. ~~Definir el esquema del Sheet~~ — hecho, ver 6.8; falta importar `docs/sheet-seed/` a la planilla
8. Cargar el repertorio real con tempo, etiquetas y dificultad, en paralelo con el desarrollo
9. Cerrar la lista de mutaciones del contrato de acciones antes de `action-contract-registry` (D-13)
10. Opcional, de la semana 1: prototipo del camino crítico en Google AI Studio
