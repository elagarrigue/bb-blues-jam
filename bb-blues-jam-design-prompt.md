# Prompt de diseño — App BB Blues Jam

> Pegá todo lo que sigue en Claude Design, Sketch AI o la herramienta que uses.

---

## Contexto

Diseñá las pantallas de una app Android para organizar una jam de blues mensual que se hace en
un bar de Bahía Blanca, Argentina. La app la usan los músicos que van a tocar y la persona que
organiza la jam.

No es un producto comercial ni una app de streaming. Es una herramienta chica, para una comunidad
de entre 20 y 60 personas que se juntan una vez por mes. El tono debe sentirse cercano y de club
nocturno, no corporativo.

**Todos los textos de la interfaz van en español rioplatense.** Usá "vos", no "tú".

---

## Usuarios y roles

**Músico** (la mayoría). Entra para responder dos preguntas: *¿qué se toca?* y *¿dónde hay lugar
para mí?*. Solo lectura. La mitad de las veces abre la app parado en el bar, con poca luz y una
mano ocupada.

**Admin** (una o dos personas). Arma la lista de temas antes de la jam, asigna tonalidades, anota
músicos en los cupos, reordena temas y decide cuándo publicar la lista.

**Solo el admin anota músicos.** El músico no se anota desde la app: mira dónde hay lugar y se lo
pide al admin. No diseñes flujos de auto-anotación, ni registro, ni perfiles.

El admin ve exactamente las mismas pantallas que el músico, más los controles de edición. **No
diseñes dos apps distintas**: una sola app, con controles que aparecen cuando hay sesión de admin.
Mostrá ambos estados de las pantallas que cambian.

---

## Dirección visual

- **Tema oscuro únicamente.** No hace falta variante clara.
- **Paleta:** fondo índigo muy oscuro, casi negro, con capas de superficie apenas más claras para
  separar contenido. Acento en ámbar neón, usado con moderación: solo para la acción principal, el
  estado activo y los datos que más importan (tonalidad, cupo libre, estado "publicado").
- La sensación es luz de neón en un bar oscuro. Que el ámbar respire; si todo brilla, nada resalta.
- **Tipografía:** una sans serif condensada o con carácter para títulos, una neutra y muy legible
  para datos y listas. Títulos de temas y tonalidades tienen que leerse de un vistazo en penumbra.
- **Contraste alto de verdad.** Se usa en un bar oscuro y en un escenario. Nada de gris sobre gris.
- Material 3 como base, pero no un tema Material genérico. Debe tener identidad propia.
- **Sin fotos de stock.** Ilustración mínima o nada.

---

## Modelo de cupos (leer antes de diseñar la lista)

Cada tema define una formación: una lista de puestos por instrumento. Por defecto:

| Instrumento | Cupos |
|---|---|
| Guitarra | 2 |
| Bajo | 1 |
| Batería | 1 |
| Voz | 1 |
| Armónica | 1 |
| Teclados | 1 |

El admin puede ajustar esta formación por tema. Un **cupo libre** es un puesto sin nadie asignado.

Este modelo es la base de toda la pantalla principal: la vista colapsada, el filtro y el orden
dentro del tema expandido dependen de él.

---

## Estructura

Tres pestañas en una barra de navegación inferior:

1. **Próxima jam**
2. **Anteriores**
3. **Info**

---

## Pantallas a diseñar

### 1. Próxima jam — vista de músico

La pantalla más importante de la app. Es la que se abre 90% de las veces. Tiene que responder
"¿dónde puedo tocar?" sin que el usuario tenga que abrir nada.

**Encabezado:**
- Fecha de la jam, destacada
- Nombre y dirección del lugar
- Cuánto falta, en lenguaje natural ("en 5 días", "esta noche")

**Barra de filtro**, debajo del encabezado:
- Chips por instrumento: Guitarra, Bajo, Batería, Voz, Armónica, Teclados
- Al activar un chip, la lista muestra solo los temas con cupo libre de ese instrumento
- Un chip para limpiar el filtro
- Cuando hay filtro activo, indicá cuántos temas quedan de cuántos
- Diseñá el estado en que el filtro no devuelve ningún tema

**Lista de temas, colapsada por defecto.** Cada fila muestra, en una sola línea si se puede:
- Número de orden
- Título del tema
- **Tonalidad** — dato destacado, siempre visible aunque esté colapsado
- **Tira compacta de instrumentos:** un ícono chico por instrumento de la formación, apagado si
  hay cupo libre y encendido si está cubierto

Esa tira es lo que hace que la lista sirva sin expandir nada y que el filtro sea legible de un
vistazo. Resolvela para que entre en poco espacio y se entienda sin leyenda.

**Al tocar una fila, se expande en el lugar** y muestra los cupos, en este orden:

1. **Primero los cupos libres**, presentados como algo disponible
2. **Después los cupos cubiertos**, con nombre del músico e instrumento

Lo accionable va arriba. Varias filas pueden estar expandidas a la vez.

En la fila expandida también: artista del tema y acceso al detalle completo.

**Estado sin publicar:** si la lista todavía no fue publicada, el músico ve la fecha y el lugar,
pero en lugar de temas ve un mensaje de que la lista se está armando.

### 2. Próxima jam — vista de admin

La misma pantalla, con:
- Distintivo de estado: **Borrador** o **Publicada**
- Acción para publicar, prominente cuando está en borrador
- Botón para agregar tema
- Manija de arrastre en cada fila para reordenar
- Dentro de la fila expandida: cada cupo libre es tocable para asignar un músico, y cada cupo
  cubierto permite quitar a esa persona
- Acceso a editar la formación del tema y la tonalidad

Mostrá una fila expandida en modo admin y la misma en modo músico, lado a lado.

### 3. Detalle del tema

Complementa la fila expandida: acá va todo lo que no entra en la lista.

- Título, artista, artwork si hay
- **Tonalidad, bien grande.** Es el dato principal de esta pantalla.
- Etiquetas del tema (shuffle, 12 compases, slow blues, blues nacional, etc.)
- Tempo, si está cargado
- Formación completa, con cupos libres y cubiertos agrupados por instrumento
- Botón para abrir la tablatura en el navegador (sale de la app)
- En modo admin: editar tonalidad, formación y asignaciones

### 4. Jams anteriores — lista

Lista cronológica inversa. Cada fila: fecha, lugar, cantidad de temas, y algún dato que dé ganas
de entrar (los primeros temas, cuánta gente tocó).

### 5. Jam anterior — detalle

La misma estructura que la próxima jam, pero solo lectura, sin filtro y con tratamiento visual de
archivo: más apagada, sin ámbar salvo en las tonalidades. Los cupos se muestran cubiertos, sin
noción de libre.

### 6. Info

- Qué es la jam, en un párrafo
- Lugar, dirección, cómo llegar
- Cuándo se hace
- Enlaces a redes sociales
- Cómo sumarse como músico
- Acceso discreto a "Entrar como admin"

### 7. Ingreso de admin

Pantalla mínima: campo de frase de acceso y un botón. Sin registro, sin recuperar contraseña, sin
email. Mostrá el estado de error.

### 8. Asignar músico a un cupo (solo admin)

Cómo el admin llena un cupo libre: escribir un nombre, con sugerencias de gente que ya tocó antes.
El instrumento ya está determinado por el cupo que tocó, así que no hace falta elegirlo. Puede ser
una hoja inferior o una pantalla completa — elegí y justificá brevemente.

---

## Estados que hay que diseñar

- **Cargando** — esqueletos, no un spinner centrado
- **Vacío** — con una invitación concreta, no "No hay nada todavía"
- **Filtro sin resultados** — distinto del vacío: indicá qué filtro está activo y ofrecé limpiarlo
- **Error** — mensaje más botón de reintentar
- **Sin conexión** — la app muestra los últimos datos guardados; indicá que están desactualizados

---

## Reglas de diseño

- Prioridad de lectura en la fila colapsada: **cupos libres > tonalidad > título > artista**
- El ámbar no se usa para decorar. Si un elemento es ámbar, es porque el usuario lo necesita ahora.
  Un cupo libre es un buen candidato a ámbar; un cupo cubierto no.
- Expandir y colapsar no debe mover el resto de la lista de forma desorientadora. Pensá la
  transición.
- Áreas táctiles de 48dp como mínimo. Se usa parado, con poca luz.
- Los controles de admin no deben reorganizar la pantalla al aparecer. La disposición es la misma;
  se suman controles.
- Nada de scroll horizontal en la lista. Nada de gestos ocultos sin alternativa visible.
- Sin modo claro, sin onboarding, sin pantalla de bienvenida.

---

## Qué entregar

1. Las ocho pantallas listadas, tamaño teléfono
2. Próxima jam en cuatro variantes: colapsada, con una fila expandida, con filtro activo, y en
   modo admin
3. Los cinco estados de lista
4. Una hoja de estilo: colores con su rol, escala tipográfica, la tira de instrumentos como
   componente, y la fila de tema en sus dos estados

---

## Fuera de alcance

No diseñes: auto-anotación de músicos, tablaturas renderizadas dentro de la app, perfiles de
usuario, chat entre músicos, notificaciones, ni pantalla de asistente de IA. Van en una versión
posterior.
