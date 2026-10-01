'use strict';
// The edge input behind docs/api-samples/jams-edge.json: what getDisplayValues() and getValues()
// would return for a messy Jams tab and its jam tabs, in a spreadsheet whose zone is Buenos Aires.
//
// Jams rows, in order:
//  1. 2026-08-29 PUBLICADA, typed cells: fecha a Date shown '29/8/2026', hora a time shown
//     '21:00:00'. Its tab has typed posicion numbers out of row order, a name, a '-', empty slots,
//     Otros 'Juan (saxo); Ana (percusión);', padded cells and header, an extra column, a blank row.
//  2. a blank row (skipped);
//  3. 2026-09-26 BORRADOR: its tab holds the marker song, and must never be read;
//  4. 2026-06-27 'Publicada' (wrong case): withheld like a draft, its tab never read;
//  5. 2026-05-30 PUBLICADA with no tab: missing_tab;
//  6. 2026-04-25 PUBLICADA whose tab lacks Teclados: missing_header;
//  7. and 8. 2026-03-28 twice, PUBLICADA and BORRADOR: duplicate_date, the tab never read;
//  9. fecha 'Config', PUBLICADA: invalid_date, no tab requested by that name;
// 10. 2026-02-28 PUBLICADA, hora a time shown '9:00 p. m.', tab with no Otros column.
//
// The spreadsheet also holds an orphan tab (no Jams row) and the settings tab; neither is read.

const { zonedDate, BUENOS_AIRES } = require('./format.js');

const MARKER = 'zz-borrador-secreto';
const TIME_ZONE = BUENOS_AIRES;

const SLOT_HEADERS = ['Guitarra 1', 'Guitarra 2', 'Bajo', 'Batería', 'Voz', 'Armónica', 'Teclados'];
const TAB_HEADER = ['posicion', 'id_tema', 'titulo', 'artista', 'tono'].concat(SLOT_HEADERS, ['Otros']);

function time(hour, minute) {
  return zonedDate(TIME_ZONE, 1899, 12, 30, hour, minute);
}

/** A tab given as [display, raw] pairs per row; a row given as one array is both. */
function tab(header, rows) {
  const display = [header];
  const raw = [header];
  for (const row of rows) {
    const pair = Array.isArray(row[0]) ? row : [row, row];
    display.push(pair[0]);
    raw.push(pair[1]);
  }
  return { display, raw };
}

function blankRow(width) {
  return new Array(width).fill('');
}

// Jams: columns reordered, ' lugar ' padded, an unmapped 'notas' column.
const JAMS_HEADER = ['fecha', 'hora', ' lugar ', 'estado', 'notas'];
const JAMS = tab(JAMS_HEADER, [
  [
    ['29/8/2026', '21:00:00', ' La Macanuda ', 'PUBLICADA ', 'tipada'],
    [zonedDate(TIME_ZONE, 2026, 8, 29), time(21, 0), ' La Macanuda ', 'PUBLICADA ', 'tipada'],
  ],
  blankRow(5),
  ['2026-09-26', '21:00', 'La Macanuda', 'BORRADOR', 'próxima'],
  ['2026-06-27', ' 9:30 ', 'La Macanuda', 'Publicada', ''],
  ['2026-05-30', '21:00', 'La Macanuda', 'PUBLICADA', 'sin pestaña'],
  ['2026-04-25', '21:00', 'La Macanuda', 'PUBLICADA', 'sin Teclados'],
  ['2026-03-28', '21:00', 'La Macanuda', 'PUBLICADA', ''],
  ['2026-03-28', '21:00', 'Otro bar', 'BORRADOR', 'fecha repetida'],
  ['Config', '21:00', 'La Macanuda', 'PUBLICADA', 'fecha mal escrita'],
  [
    ['28/2/2026', '9:00 p. m.', 'La Macanuda', 'PUBLICADA', ''],
    [zonedDate(TIME_ZONE, 2026, 2, 28), time(21, 0), 'La Macanuda', 'PUBLICADA', ''],
  ],
]);

const MARKER_ROW = ['1', MARKER, 'ZZ BORRADOR NO DEBE SALIR', 'Prueba', 'A'].concat(blankRow(7), ['']);

const TABS = {
  Jams: JAMS,
  // Header padded and reordered (Otros before the slots), an unmapped 'notas' column.
  '2026-08-29': tab(
    [' posicion ', 'id_tema', 'titulo', 'artista', ' tono ', 'Otros', 'notas'].concat(SLOT_HEADERS),
    [
      [
        ['2', 'crossroads', 'Crossroads', 'Eric Clapton', 'A', '', 'segunda', 'Pedro', '-', '', '', '', '', ''],
        [2, 'crossroads', 'Crossroads', 'Eric Clapton', 'A', '', 'segunda', 'Pedro', '-', '', '', '', '', ''],
      ],
      blankRow(14),
      [
        ['1', ' the-thrill-is-gone ', 'The Thrill Is Gone', 'B.B. King', 'Bm', 'Juan (saxo); Ana (percusión);', '', ' Ana ', 'Luis', 'Marta', 'Diego', 'Sofía', '-', ''],
        [1, ' the-thrill-is-gone ', 'The Thrill Is Gone', 'B.B. King', 'Bm', 'Juan (saxo); Ana (percusión);', '', ' Ana ', 'Luis', 'Marta', 'Diego', 'Sofía', '-', ''],
      ],
      [
        ['3', 'got-my-mojo-working', 'Got My Mojo Working', 'Muddy Waters', 'E', '', 'solo nota', '', '', '', '', '', '', ''],
        [3, 'got-my-mojo-working', 'Got My Mojo Working', 'Muddy Waters', 'E', '', 'solo nota', '', '', '', '', '', '', ''],
      ],
    ],
  ),
  '2026-09-26': tab(TAB_HEADER, [MARKER_ROW]),
  '2026-06-27': tab(TAB_HEADER, [MARKER_ROW]),
  '2026-04-25': tab(TAB_HEADER.filter((h) => h !== 'Teclados'), [['1', 'crossroads', 'Crossroads', 'Eric Clapton', 'A', '', '', '', '', '', '', '']]),
  '2026-03-28': tab(TAB_HEADER, [MARKER_ROW]),
  '2026-02-28': tab(TAB_HEADER.filter((h) => h !== 'Otros'), [['1', 'crossroads', 'Crossroads', 'Eric Clapton', 'A', '', '', '', '', '', '', '']]),
  // Orphan tab: no Jams row names it.
  '2026-01-31': tab(TAB_HEADER, [MARKER_ROW]),
  Config: tab(['clave', 'valor'], [['passphrase', 's3cret']]),
};

/** The tabs a correct script may request through readTab: the published, unique, ISO dates. */
const READABLE_TABS = ['2026-08-29', '2026-05-30', '2026-04-25', '2026-02-28'];

module.exports = { TABS, MARKER, TIME_ZONE, READABLE_TABS, TAB_HEADER, SLOT_HEADERS };
