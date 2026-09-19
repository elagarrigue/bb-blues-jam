# BB Blues Jam — Bitácora del proyecto

Registro del proceso y de las decisiones, para la presentación final de AI Expert.

Este documento está en español porque su destinatario es la presentación del curso. El código,
los commits y la documentación técnica del repositorio están en inglés.

Última actualización: 19 de septiembre de 2026 · Fase: harness en armado, previo a la primera línea
de código de producto

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

---

# Parte III — Harness y seguimiento

## 6. Estado del harness

| Artefacto | Estado |
|---|---|
| `AGENTS.md` — contexto raíz para el agente | hecho, a adaptar a la jam app |
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
| Documentos de descubrimiento (`build-brief`) | pendiente, próximo paso |
| `feature_list.json` (`harness-starter`) | pendiente |
| Subagentes de Claude Code | pendiente |
| Skills reutilizables propias | pendiente, módulo 4 |
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

## 7. Mapa a los módulos del curso

| Semana | Módulo | Aplicación |
|---|---|---|
| 1 | Aprender a aprender con IA | Exploración de dominios, investigación de diez APIs con NotebookLM, definición del problema |
| 2 | Prompt y context engineering | `AGENTS.md`, documentos de contexto, prompts de diseño, especificaciones |
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

## 10. Próximos pasos

1. Correr `build-brief` sobre los dos documentos de descubrimiento, sin reabrir lo ya decidido
2. Correr `harness-starter` para generar `AGENTS.md`, `init.sh`, `PROGRESS.md` y `feature_list.json`
3. Escribir los tres subagentes de Claude Code: `planner`, `implementer`, `validator`
4. Generar las pantallas en Stitch a partir de los prompts de la sección 5
5. Definir el esquema del Sheet: catálogo de temas, jams, asignaciones
6. Cargar el repertorio real con etiquetas y tonalidades
7. Primera rebanada de `feature_list.json`: esqueleto de build y módulos
