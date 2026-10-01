'use strict';
// The edge input behind docs/api-samples/catalog-edge.json: what getDisplayValues() and
// getValues() would return for a messy Catalogo tab.
//
// - columns reordered, header ' titulo ' padded, an unmapped column 'notas';
// - fully blank rows, and a row whose only content is in the unmapped column (skipped);
// - cells padded with spaces and NBSP;
// - songsterr_id typed as a number (12345) whose display is the es-AR format '12.345';
//   a string with spaces (' 42 '); a non-integral number (12.5);
// - one song with every optional field set, one with none, one with an empty required cell;
// - values in Sheet vocabulary, including one the mapper must reject ('Rápido').

const HEADER = ['dificultad', ' titulo ', 'id', 'notas', 'artista', 'tono_default', 'songsterr_id', 'etiquetas', 'tempo'];

// [display row, raw row]
const ROWS = [
  [
    ['media', '  The Thrill Is Gone ', 'the-thrill-is-gone', 'abre la noche', 'B.B. King', 'Bm', '12.345', 'slow blues, 12 compases', 'lento'],
    ['media', '  The Thrill Is Gone ', 'the-thrill-is-gone', 'abre la noche', 'B.B. King', 'Bm', 12345, 'slow blues, 12 compases', 'lento'],
  ],
  [
    ['', '', '', '', '', '', '', '', ''],
    ['', '', '', '', '', '', '', '', ''],
  ],
  [
    ['', 'Crossroads', 'crossroads', '', 'Robert Johnson', 'A', '', '', ''],
    ['', 'Crossroads', 'crossroads', '', 'Robert Johnson', 'A', '', '', ''],
  ],
  [
    ['', '', '', 'solo una nota', '', '', '', '', ''],
    ['', '', '', 'solo una nota', '', '', '', '', ''],
  ],
  [
    ['fácil', ' Got My Mojo Working ', ' got-my-mojo-working', '', 'Muddy Waters ', 'E', ' 42 ', ' shuffle ', 'rápido'],
    ['fácil', ' Got My Mojo Working ', ' got-my-mojo-working', '', 'Muddy Waters ', 'E', ' 42 ', ' shuffle ', 'rápido'],
  ],
  [
    ['difícil', 'Sin Tono', 'sin-tono', '', 'Alguien', '  ', '12,5', '', 'Rápido'],
    ['difícil', 'Sin Tono', 'sin-tono', '', 'Alguien', '  ', 12.5, '', 'Rápido'],
  ],
  [
    ['  ', ' ', '', '', ' ', '', '', '', ''],
    ['  ', ' ', '', '', ' ', '', '', '', ''],
  ],
];

const EDGE_DISPLAY = [HEADER].concat(ROWS.map((pair) => pair[0]));
const EDGE_RAW = [HEADER].concat(ROWS.map((pair) => pair[1]));

module.exports = { EDGE_DISPLAY, EDGE_RAW };
