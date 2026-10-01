/**
 * The Catalogo tab as the catalog response (docs/apps-script-api.md).
 *
 * The script transports and the Kotlin mapper interprets: headers are matched by trimmed exact
 * name and renamed to English, cells are trimmed and typed cells turned into text, and only
 * structural problems are rejected. Keys, ids, enum values, tags and duplicate ids are left to
 * the mapper in catalog-repository-cache.
 *
 * Uses textCell and integerTextCell from Normalize.js (shared global scope in Apps Script).
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

/** A structural problem with a stable `code` from the contract's error list. */
class ContractError extends Error {
  constructor(code, message) {
    super(message);
    this.name = 'ContractError';
    this.code = code;
  }
}

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
  const byHeader = {};
  CATALOG_FIELDS.forEach(function (spec) {
    byHeader[spec.header] = spec;
  });
  const columns = {};
  headerRow.forEach(function (cell, index) {
    const name = cell === null || cell === undefined ? '' : String(cell).trim();
    if (!Object.prototype.hasOwnProperty.call(byHeader, name)) {
      return;
    }
    const field = byHeader[name].field;
    if (columns[field] !== undefined) {
      throw new ContractError('duplicate_header', CATALOG_TAB + ' has the header "' + name + '" twice');
    }
    columns[field] = index;
  });
  const missing = CATALOG_FIELDS.filter(function (spec) {
    return spec.required && columns[spec.field] === undefined;
  }).map(function (spec) {
    return spec.header;
  });
  if (missing.length > 0) {
    throw new ContractError('missing_header', CATALOG_TAB + ' is missing required headers: ' + missing.join(', '));
  }
  return columns;
}

if (typeof module !== 'undefined') {
  module.exports = { CATALOG_TAB, CATALOG_FIELDS, ContractError, buildCatalog, catalogColumns_ };
}
