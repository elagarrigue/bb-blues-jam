# START-HERE — BB Blues Jam

Documento de arranque para continuar el proyecto en Claude Code.
Pegá este archivo en la raíz del repo nuevo y empezá la sesión con: "Leé START-HERE.md".

Generado el 19 de septiembre de 2026. Estado: diseño cerrado, sin una línea de código escrita.

---

## 1. Qué es este proyecto

App Android para organizar la **BB Blues Jam**, una jam de blues mensual en La Macanuda
(Moreno 223, Bahía Blanca). Participan entre 20 y 60 músicos por fecha.

Es el proyecto final del curso **AI Expert** de DevExpert (edición septiembre 2026). El curso
pide dos entregables: una app funcional y un harness de desarrollo documentado.

### El problema

Hoy la lista de temas se arma a mano y se comparte por WhatsApp o en papel:

- El músico no sabe qué se toca ni en qué tonalidad, así que no puede prepararse.
- No hay forma de ver dónde hay lugar. Si sos bajista, no sabés en qué temas falta bajo.
- La lista cambia hasta último momento y no hay versión única confiable.
- No queda registro de jams anteriores, así que las listas se repiten sin querer.

### Las dos fases

**Fase 1 — app base.** Tres pestañas: próxima jam, jams anteriores, info. El admin arma la
lista; el músico la consulta.

**Fase 2 — el asistente.** Chat solo para el admin que arma listas de temas teniendo en cuenta
el historial y restricciones temáticas ("solo blues nacional argentino", "solo blues de 12
compases", "arrancar lento y subir"). El admin propone algunos temas base y el asistente
completa el resto, luego ejecuta las acciones para crear la lista.

---

## 2. Archivos que tenés que traer

Copiá estos dos al repo antes de empezar. Son el insumo del descubrimiento:

| Archivo | Para qué |
|---|---|
| `bb-blues-jam-bitacora.md` | Proceso, 14 decisiones con su fundamento, mapa a los módulos del curso |
| `bb-blues-jam-design-prompt.md` | Brief de diseño completo + prompts por pantalla para Stitch |

**Importante:** la bitácora es también el material de la presentación final. Mantenela
actualizada a medida que avanzás; no es documentación muerta.

---

## 3. Estado actual

### Hecho

- Problema elegido y validado contra alternativas descartadas
- Investigación de diez APIs musicales con NotebookLM, verificada después contra documentación viva
- Alcance del MVP definido
- 14 decisiones técnicas y de producto tomadas, con fundamento escrito
- Diseño de UI especificado: ocho pantallas, cinco estados, dirección visual
- Prompts de Stitch listos para generar las pantallas

### Pendiente inmediato

- Instalar las skills del curso
- Correr `build-brief` para generar los docs de descubrimiento
- Correr `harness-starter` para generar `feature_list.json`
- Escribir los tres subagentes para Claude Code
- Generar las pantallas en Stitch

---

## 4. Decisiones ya tomadas

No las reabras salvo que aparezca información nueva. Cada una tiene su fundamento completo en la
bitácora.

| # | Decisión |
|---|---|
| D-01 | Android nativo, sin KMP |
| D-02 | Presenters composables en lugar de ViewModels (patrón Doximity) |
| D-03 | Clean Architecture multi-módulo, features aisladas, verificado con Konsist |
| D-04 | Google Sheets vía Apps Script como backend, con autoridad única por entidad |
| D-05 | Solo el admin anota músicos; el músico solo lee |
| D-06 | Modelo de cupos por tema (formación configurable) |
| D-07 | Lista colapsada con tira de íconos de instrumento y filtro por cupo libre |
| D-08 | La tonalidad la define el admin, no una API |
| D-09 | MusicBrainz, Deezer y Last.fm como enriquecimiento opcional |
| D-10 | Songsterr fuera del MVP; se guarda `songsterrId` y se abre en el navegador |
| D-11 | Autenticación por frase de acceso, sin Firebase Auth ni OAuth |
| D-12 | UI en español rioplatense, código y docs técnicos en inglés |
| D-13 | Contrato de acciones definido antes del asistente |
| D-14 | El catálogo del Sheet es el pool de candidatos del LLM |

### Las dos que más condicionan el trabajo

**D-04 — autoridad única por entidad.** No hay sincronización bidireccional.

| Entidad | Autoridad | Dirección |
|---|---|---|
| Catálogo de temas | Sheet | Sheet → app, solo lectura |
| Jams pasadas | Sheet | Sheet → app, solo lectura |
| Lista de la próxima jam | App | app → Sheet vía Apps Script |
| Estado publicado | App | app → Sheet |

**D-13 — contrato de acciones.** Regla: *si el admin puede hacerlo a mano, el asistente tiene que
poder hacerlo también.* Toda mutación existe desde la fase 1 como deeplink o como función del
repositorio. Cuando llegue el asistente, se registran esas mismas funciones desde `:app` sin tocar
los módulos de feature.

---

## 5. Modelo de dominio, borrador

```
Jam(id, date, venue, status: DRAFT | PUBLISHED, songs)
JamSong(position, songId, key, lineup)
Slot(instrument, musicianName?)     // libre si musicianName es null
Song(id, title, artist, defaultKey, tempo, tags, difficulty,
     mbid?, artistArea?, deezerTrackId?, previewUrl?, artworkUrl?, songsterrId?)
```

Formación por defecto: 2 guitarras, bajo, batería, voz, armónica, teclados. Ajustable por tema.
Un **cupo libre** es un `Slot` sin músico asignado.

Los primeros campos de `Song` los carga el admin en el Sheet. Los últimos cinco son
enriquecimiento opcional en background.

---

## 6. Pasos inmediatos

### 6.1 Instalar las skills del curso

```bash
git clone --depth 1 https://github.com/devexpert-io/ai-expert-project.git /tmp/aiexp
mkdir -p .claude/skills
cp -r /tmp/aiexp/.agents/skills/* .claude/skills/
ls .claude/skills
```

Se instalan dentro del repo para que queden versionadas: son parte de la evidencia del harness que
pide el curso.

Deberías ver seis: `build-brief`, `harness-starter`, `feature-spec`, `feature-implementer`,
`feature-validator`, `feature-flow`.

### 6.2 Correr build-brief

```
Usá la skill build-brief. Leé primero bb-blues-jam-bitacora.md y
bb-blues-jam-design-prompt.md: ya contienen el problema, los usuarios, el alcance,
el modelo de dominio, las decisiones técnicas y la dirección de diseño.
No vuelvas a preguntar lo que esos archivos ya responden.
```

Produce: `CONTEXT.md`, `docs/build-brief.md`, `docs/domain-model.md`,
`docs/risks-and-open-questions.md`, y probablemente `docs/user-and-access-model.md`,
`docs/technical-discovery.md` y `DESIGN.md`.

La skill va a preguntar el idioma de los documentos. Ver sección 7.

### 6.3 Correr harness-starter

Produce `AGENTS.md`, `init.sh`, `PROGRESS.md` y `feature_list.json`.

**`feature_list.json` es el plan de desarrollo real.** El pipeline del curso no genera un plan por
fases: genera rebanadas verticales del tamaño de una sesión, y la spec de cada una se escribe justo
antes de implementarla.

### 6.4 Escribir los tres subagentes

`feature-flow` necesita tres subagentes que envuelvan las skills correspondientes:

| Subagente | Envuelve |
|---|---|
| `planner` | `feature-spec` |
| `implementer` | `feature-implementer` |
| `validator` | `feature-validator` |

Van en `.claude/agents/`. El repo del curso trae `agents/openai.yaml` en cada skill y un
`.opencode/agents/validator.md` como referencia, pero nada para Claude Code.

### 6.5 Generar las pantallas en Stitch

Usá los prompts de la sección 5 de `bb-blues-jam-design-prompt.md`. Guardá los resultados y
referencialos desde `DESIGN.md`.

---

## 7. Decisiones pendientes

**Idioma de los documentos del proyecto.** `build-brief` pregunta esto explícitamente y avisa de
no asumirlo por el idioma de la conversación. Postura acordada: **inglés** para código, commits y
documentación técnica; **español rioplatense** solo para los textos de la interfaz. La bitácora
queda en español porque es material de presentación.

**`init.sh`.** El proyecto de referencia del curso es Next.js con pnpm. El tuyo es Android, así
que `init.sh` tiene que envolver el gate de Gradle:

```bash
./gradlew build
./gradlew check     # tests + Konsist + detekt + ktlint
```

`feature-flow` lo ejecuta en cada validación, así que conviene que sea rápido y no bloqueante.

**Esquema del Sheet.** Definir las hojas de catálogo de temas, jams y asignaciones, y cargar el
repertorio real con tonalidades y etiquetas. Cuanto antes se cargue, mejor va a funcionar el
asistente en la semana 5.

---

## 8. Qué no hacer

- **No reabras el descubrimiento** sobre decisiones ya tomadas. Si algo parece mal, decilo y
  esperá confirmación antes de cambiarlo.
- **No escribas un plan por fases.** El artefacto de planificación es `feature_list.json`.
- **No uses ViewModels.** Ver D-02.
- **No agregues dependencias entre módulos de feature.** Los contratos compartidos van en
  `:core:*` y el binding en `:app`.
- **No implementes Songsterr, auto-anotación de músicos, ni el asistente** en la fase 1.
- **No dejes que una mutación exista solo en la UI.** Toda mutación pasa por el repositorio o por
  un deeplink. Ver D-13.

---

## 9. Evidencia para la presentación

Registrala mientras avanzás, porque después no se recupera:

- [ ] Captura de la app **sin** el asistente (el "antes")
- [ ] Vídeo del flujo manual cronometrado: armar una lista de 12 temas a mano
- [ ] El mismo resultado pedido al asistente en una frase, cronometrado
- [ ] Un caso donde el asistente se equivoca y cómo lo corrige el humano
- [ ] Diff de los módulos de feature al agregar el asistente, demostrando que no se tocaron
- [ ] Salida de Konsist probando el aislamiento entre features
- [ ] Una lista generada para una jam temática junto al repertorio de origen, mostrando que no
      inventó nada

---

## 10. Calendario del curso

Edición del 14 de septiembre al 23 de octubre de 2026.

| Semana | Módulo | Aplicación |
|---|---|---|
| 1 | Aprender a aprender con IA | Hecho: exploración de dominios, investigación de APIs, definición del problema |
| 2 | Prompt y context engineering | `build-brief`, `harness-starter`, subagentes, `DESIGN.md` |
| 3 | IDEs agénticos | Ciclo `feature-flow` sobre la app base |
| 4 | CLI y MCP | Skills propias; posible MCP para Google Sheets |
| 5 | APIs de IA | El asistente: function calling sobre el contrato de acciones |
| 6 | Prototipado y proyecto final | Cierre del MVP, Songsterr si hay tiempo, vídeo |
