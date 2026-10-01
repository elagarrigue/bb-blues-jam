/**
 * The Catalogo tab as the catalog response (docs/apps-script-api.md).
 *
 * The script transports and the Kotlin mapper interprets: headers are matched by trimmed exact
 * name and renamed to English, cells are trimmed and typed cells turned into text, and only
 * structural problems are rejected. Keys, ids, enum values, tags and duplicate ids are left to
 * the mapper in catalog-repository-cache.
 *
 * Uses ContractError, mapColumns, textCell and integerTextCell from Normalize.js (shared global
 * scope in Apps Script).
 */

var CATALOG_TAB = 'Catalogo';

/**
 * Header to field, in response order. `integer` columns are read from the raw value, every other
 * column from the display value.
 */
var CATALOG_FIELDS = [
  { header: 'id', field: 'id', required: true, integer: false },
  { header: 'titulo', field: 'title', required: true, integer: false },
  { header: 'artista', field: 'artist', required: true, integer: false },
  { header: 'tono_default', field: 'defaultKey', required: true, integer: false },
  { header: 'tempo', field: 'tempo', required: false, integer: false },
  { header: 'etiquetas', field: 'tags', required: false, integer: false },
  { header: 'dificultad', field: 'difficulty', required: false, integer: false },
  { header: 'songsterr_id', field: 'songsterrId', required: false, integer: true },
];

/**
 * Builds `{ songs }` from the tab's display values and raw values (same shape, row 1 is the
 * header row). Throws ContractError `missing_header` or `duplicate_header`.
 */
function buildCatalog(displayRows, rawRows) {
  const columns = catalogColumns_(displayRows.length > 0 ? displayRows[0] : []);
  const songs = [];
  for (let r = 1; r < displayRows.length; r++) {
    const displayRow = displayRows[r] || [];
    const rawRow = (rawRows && rawRows[r]) || [];
    const song = {};
    let empty = true;
    CATALOG_FIELDS.forEach(function (spec) {
      const col = columns[spec.field];
      let value = null;
      if (col !== undefined) {
        value = spec.integer ? integerTextCell(rawRow[col]) : textCell(displayRow[col]);
      }
      song[spec.field] = value;
      if (value !== null) {
        empty = false;
      }
    });
    if (!empty) {
      songs.push(song);
    }
  }
  return { songs: songs };
}

/** Maps each field to its column index; optional fields with no column are left out. */
function catalogColumns_(headerRow) {
  return mapColumns(CATALOG_TAB, CATALOG_FIELDS, headerRow);
}

if (typeof module !== 'undefined') {
  module.exports = { CATALOG_TAB, CATALOG_FIELDS, buildCatalog, catalogColumns_ };
}
