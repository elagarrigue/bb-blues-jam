# Notas para presentar BB Blues Jam

Guion sugerido de **11 minutos y medio**, actualizado al **18 de octubre de 2026**, en primera persona para el organizador de la jam.
La presentación explica el proceso de desarrollo con el harness de IA; la app se muestra en vivo después, así que las slides no recorren pantallas.
Las cifras se contrastaron con el entregable, feature_list.json y las evidencias del proyecto.
En la presentación, **N** muestra u oculta estas notas en la misma pantalla. Para consultarlas en privado durante una proyección, usá este documento en otro dispositivo.

## Slide 1 · El harness detrás de la app

**Tiempo:** 40 segundos · acumulado: 0:40.

**Idea central:** anunciar que el tema es el proceso, y que la app viene después.

**Guion:**

“Organizo una jam de blues en Bahía Blanca, y para mi proyecto final de AI Expert construí una app para esa jam. Todo el código de producto lo escribieron agentes de IA: en cuatro semanas, ninguna línea a mano.

Pero hoy no voy a hablar de la app; la van a ver funcionando al final. Quiero contarles el lado B: la estructura que permitió que agentes sin memoria trabajaran durante semanas sin romper lo que ya estaba. A esa estructura la llamamos harness.”

**En pantalla:** señalá el disco: lado A el proceso, que es lo de hoy; lado B la app, que es la demo.

**Transición:** “Todo empezó antes de escribir código: eligiendo bien el problema.”

**Respaldo:** entregable §1.

## Slide 2 · Antes de generar, elegir bien el problema

**Tiempo:** 50 segundos · acumulado: 1:30.

**Idea central:** el problema se eligió con criterios que hacen verificable el trabajo de la IA.

**Guion:**

“Evalué ocho dominios antes de elegir: películas, recetas, deportes, playlists. De ese descarte salieron cuatro criterios. La IA tiene que decidir, no resumir. El resultado tiene que verificarse de un vistazo. La tarea manual tiene que ser realmente tediosa. E idealmente, un problema propio con usuarios reales.

Armar la lista de una jam cumple los cuatro: elegir doce temas que arranquen arriba, aflojen en el medio, cierren fuerte y no repitan la jam anterior es una decisión; cualquier músico la verifica leyéndola; y hoy depende de la memoria de una persona.”

**En pantalla:** recorré los cuatro criterios rápido; el énfasis va en “verificable”.

**Transición:** “Con el problema elegido, todavía faltaba decidir mucho antes de pedir código.”

**Respaldo:** entregable §2.1; bitácora §1.3.

## Slide 3 · Primero decidir, después generar

**Tiempo:** 1 minuto 15 segundos · acumulado: 2:45.

**Idea central:** la investigación y las decisiones se convierten en contratos que un agente sin contexto puede ejecutar.

**Guion:**

“Primero investigué diez APIs de música con NotebookLM y lo contrasté con la documentación oficial. La conclusión más importante fue negativa: ninguna da tonalidad ni tempo confiables, que es justo el dato central de una jam. Así que la tonalidad la pone el admin.

Después acoté el sistema conversando con Claude. Tres decisiones sostienen todo el proyecto: una sola fuente por dato, sin sincronización bidireccional; el asistente solo elige temas del catálogo propio, porque de Manal o de Memphis la Blusera el modelo sabe poco y alucina; y todo lo que el admin hace a mano, el asistente lo tiene que poder hacer también.

Eso se escribió en siete documentos para un agente sin contexto. Stitch generó las pantallas, pero en su resumen inventó un bar, un admin llamado Fede y una PWA. Lo atrapamos porque había decisiones escritas para contrastar.

Con eso, harness-starter generó un plan de 34 rebanadas. Una rebanada es una funcionalidad chica, de punta a punta. Al escribir el grafo de dependencias quedó a la vista que ocho podían arrancar ese mismo día.”

**En pantalla:** recorré los cuatro pasos de izquierda a derecha. Las 34 rebanadas son el plan inicial, no el total del proyecto.

**Transición:** “Ese contexto necesitaba un lugar estable donde vivir. Ahí aparece el harness.”

**Respaldo:** entregable §2.2 a §2.5. Las tres decisiones son D-04, D-14 y D-13 en la bitácora.

## Slide 4 · El agente no recuerda, el repositorio sí

**Tiempo:** 1 minuto 15 segundos · acumulado: 4:00.

**Idea central:** el harness es lo que permite que un agente sin memoria retome el proyecto en cualquier sesión.

**Guion:**

“Un agente arranca cada sesión sin recordar nada. Lo que necesita para seguir tiene que estar en el repositorio.

El harness tiene seis piezas. AGENTS.md es un índice corto: reglas con su motivo y qué documento leer en cada caso. Las skills guardan el workflow, más una skill propia de arquitectura. Y hay tres roles separados: planificar, implementar y validar.

Del otro lado están los controles: init.sh corre la verificación completa; Konsist convierte decisiones de arquitectura en tests; y el plan, el progreso y la evidencia dicen qué está aceptado y con qué se comprobó.

Cada sesión lee ese estado, trabaja y deja el siguiente punto de partida. Y como todo está en archivos, el mismo harness sirvió para Claude Code y para Codex.”

**En pantalla:** empezá por “una sesión nueva” en el centro; después la columna izquierda (contexto) y la derecha (controles). No leas los seis recuadros.

**Transición:** “De esas piezas, la que más pesó fue la separación de roles: quién puede dar algo por terminado.”

**Respaldo:** entregable §3. AGENTS.md tiene unas 150 líneas, con las reglas arriba y la Definition of Done al final.

## Slide 5 · Quien escribe no es quien acepta

**Tiempo:** 1 minuto 15 segundos · acumulado: 5:15.

**Idea central:** autoverificarse no es aceptar; el validador independiente encuentra lo que el implementador no ve.

**Guion:**

“Cada rebanada pasa por tres subagentes. El planner lee el repo, prueba supuestos en una copia descartable y escribe la spec. El implementer escribe el código, corre el gate y deja evidencia. El validator, que no puede editar nada, contrasta todo con la spec.

El implementer llega como mucho a passing: se autoverificó. Accepted lo da solo el validador, y solo con accepted se destraban las dependencias. Si el validador pudiera arreglar lo que juzga, dejaría de ser independiente.

Nueve rebanadas volvieron con revise. Un ejemplo: el documento de diseño decía que un cupo libre y uno ocupado se distinguían por tres señales. El validador midió la captura en escala de grises: 50 contra 53 de luminancia. El relleno no era una señal. Con el tiempo encontró menos errores de código y más diferencias entre lo que un documento afirma y lo que se verificó.”

**En pantalla:** seguí las flechas y volvé por revise. Pausa en “passing no es accepted”.

**Transición:** “Para que el validador pueda confiar en algo, la verificación misma tiene que ser confiable.”

**Respaldo:** entregable §3.3, §3.6 y §4.

## Slide 6 · Una regla que no puede fallar no prueba nada

**Tiempo:** 1 minuto · acumulado: 6:15.

**Idea central:** un verde solo vale si se vio que puede ponerse rojo.

**Guion:**

“El gate es init.sh: build, más de mil tests, lint y 22 reglas de arquitectura. Al final declara qué herramientas corren de verdad. Al principio decía NOT WIRED YET, porque era la verdad.

Cada regla nueva se mostró fallando antes de confiar en ella: se plantaba la violación, se veía el rojo con archivo y línea, y se reparaba. Lo mismo con los tests de producto: el implementer y el validator rompían el código a propósito, por ejemplo cambiando el ‘cualquiera’ del filtro por ‘todos’. Más de una vez los tests seguían en verde, y ese hueco se cerró antes de aceptar.

Y una regla del proyecto: el gate solo se volvió más estricto. Nunca se apagó una regla para que pasara.”

**En pantalla:** la terminal resume una verificación documentada; no es una ejecución en vivo.

**Transición:** “Todo esto corre solo. Pero solo no quiere decir sin humano.”

**Respaldo:** entregable §3.4 y §3.5. 1.156 tests JVM en la última corrida, incluido un test de llamada real que se omite por defecto.

## Slide 7 · La autonomía concentra el criterio humano

**Tiempo:** 1 minuto 15 segundos · acumulado: 7:30.

**Idea central:** el humano no aprueba cada paso; interviene donde una decisión cambia el proyecto.

**Guion:**

“El flujo corre las rebanadas de corrido, sin pedir permiso en cada etapa. Se detiene solo donde una decisión cambia el proyecto: meter una versión alfa en el gate, subir Kotlin, una regla nueva. Las preguntas pasaron de ‘¿sigo?’ a ‘¿esto?’.

Esas preguntas sacaron a la luz cosas que no estaban en ningún documento. El Instagram de la jam mostraba una jam los martes en otro lugar: era el programa de radio. Si el agente lo resolvía solo, la app mostraría hoy una jam que no existe.

Cuando yo era el cuello de botella de la verificación, la respuesta fue código: una jam de demo y una sesión de admin que existen solo en debug. El validador abrió el APK de release y comprobó que no estaban. La idea es sacar al humano de la verificación, no de las decisiones.

Y una lección cara: los subagentes no heredan contexto. Uno activó TalkBack en mi teléfono porque la restricción estaba en la memoria del orquestador, no en su prompt. Desde entonces cada prompt repite los límites.”

**En pantalla:** un recuadro por párrafo, en orden.

**Transición:** “Ese no fue el único error. Hubo varios, y me interesa más qué los atrapó.”

**Respaldo:** entregable §5.

## Slide 8 · Los errores, y qué los atrapó

**Tiempo:** 1 minuto · acumulado: 8:30.

**Idea central:** todos se equivocaron, incluido el orquestador; cada error dejó un cambio en el proceso.

**Guion:**

“Un registro honesto vale más que una lista de aciertos. Se equivocaron todos.

La herramienta generativa inventó datos; lo atrapó contrastar con decisiones escritas. El propio harness mentía: el gate podía decir ‘wired’ sin correr nada, y lo descubrió el planner al prototipar. El implementer escribió tests que no podían fallar, y lo atrapó el validador. El orquestador hizo un commit que se llevó cambios a medio hacer de un agente. Y en el backend, después de pegar el código nuevo, la URL seguía respondiendo con la versión vieja; lo delató un probe que no escribe nada.

Lo importante es la última columna: ninguno terminó en ‘tener más cuidado’. Cada uno terminó en un cambio del proceso.”

**En pantalla:** leé la columna “Lo atrapó” y la de “Qué cambió”; no hace falta leer cada fila entera.

**Transición:** “La prueba más grande del proceso fue meter IA dentro de la propia app.”

**Respaldo:** entregable §8 y §5.6.

## Slide 9 · La IA entró a la app sin tocar las features

**Tiempo:** 1 minuto 10 segundos · acumulado: 9:40.

**Idea central:** la fase 2 comprobó que las decisiones de la semana uno funcionaban.

**Guion:**

“La fase 2 fue un asistente que le propone al admin la lista de la jam. Lo van a ver en la demo; acá me interesa cómo entró.

El agente corre en el teléfono con Koog. Lee el catálogo, las jams pasadas y la agenda con herramientas de solo lectura. Propone temas, la app descarta todo lo que no esté en el catálogo, y el admin decide si aplica la propuesta.

Lo que muestra el diff: agregar el asistente no tocó ningún módulo de feature existente. Aplica propuestas con las mismas funciones que usa el admin a mano, porque eso se decidió en la primera semana. Y Konsist lo prueba en cada corrida.

Algunas cosas solo aparecieron al ejecutar. El gateway aceptaba herramientas pero el modelo no las usaba; la spec ya traía un plan B. Y cuando el gateway se cayó con 502, la rebanada quedó bloqueada, no ‘passing’: no se puede verificar lo que no corre.”

**En pantalla:** el número cero es el centro de la slide. El enlace abre el diagrama de arquitectura si preguntan por módulos.

**Transición:** “Con todo aceptado, faltaba una última tarea: mirar el conjunto.”

**Respaldo:** entregable §6; docs/architecture.md.

## Slide 10 · Validar por rebanada no alcanza

**Tiempo:** 1 minuto · acumulado: 10:40.

**Idea central:** cada validador juzga su rebanada; los problemas entre rebanadas necesitan una revisión del conjunto.

**Guion:**

“Cada validador juzga una rebanada contra su spec. Nadie había mirado el conjunto: lo que se repite entre rebanadas, o lo que cada una agregó sin romper nada sola.

Así que la última tarea fue un code review de todo el repositorio. Salieron nueve hallazgos, que se convirtieron en siete rebanadas nuevas con el mismo ciclo planner, implementer y validator.

Por ejemplo, había once copias del mismo mecanismo de escritura, y quedó uno. Las features exponían 122 declaraciones públicas cuando hacían falta 14. Y al romper a propósito el motor nuevo, dos roturas seguían en verde con los tests viejos: se agregó un test antes de aceptar. Tres de los hallazgos quedaron como reglas de Konsist, para que no vuelvan en silencio.”

**En pantalla:** recorré la línea 9 → 7 → 3 y después los tres números.

**Transición:** “Con eso, lo que me llevo.”

**Respaldo:** entregable §7. No hubo nueva prueba en el teléfono para esos refactors: la prueba fue el mismo gate, de 1124 a 1156 tests.

## Slide 11 · El harness importa más que el prompt

**Tiempo:** 50 segundos · acumulado: 11:30.

**Idea central:** cerrar con las lecciones y pasar a la demo.

**Guion:**

“Al 18 de octubre: 75 rebanadas, 74 aceptadas y una bloqueada por una decisión de alcance. Ninguna línea de producto escrita a mano.

Lo que mantuvo la calidad no fue un prompt bien redactado: fueron roles separados, reglas que pueden fallar, contexto escrito y un asistente acotado a datos propios. Las 29 decisiones las tomé yo; los agentes las convirtieron en código, tests y evidencia, y avisaron cada vez que algo las contradecía.

La IA multiplica el criterio. El criterio sigue siendo nuestro. Y ahora, vamos a la app.”

**En pantalla:** señalá el gráfico y la rebanada bloqueada. Al terminar, pasá a la demo.

**Transición a la demo:** “Les muestro primero lo que ve un músico, y después el modo admin con el asistente.”

**Respaldo:** entregable §1, §10 y §11; feature_list.json; snapshot.json. La bloqueada no se cuenta como aceptada. Los conteos son un corte documentado, no estadísticas en vivo.
