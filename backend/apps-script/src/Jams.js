/**
 * The Jams tab and the published jams' setlist tabs as the jams response (docs/apps-script-api.md).
 *
 * The script transports and the Kotlin mapper interprets, as for the catalog. The one value read
 * here is `estado`: a setlist is served only when it is exactly PUBLICADA after trimming. Any
 * other status, a typo included, withholds the setlist and its tab is never requested, so a draft
 * cannot reach an anonymous reader.
 *
 * A tab is requested only by a date that is YYYY-MM-DD, so no `fecha` can name another tab.
 *
 * Uses ContractError, mapColumns, textCell, integerTextCell, isoDateCell and timeCell from
 * Normalize.js (shared global scope in Apps Script).
 */

var JAMS_TAB = 'Jams';

/** The only status that releases a setlist. */
var PUBLISHED_STATUS = 'PUBLICADA';

/** Jams header to field, in response order. */
var JAMS_FIELDS = [
  { header: 'fecha', field: 'date', required: true },
  { header: 'hora', field: 'startTime', required: true },
  { header: 'lugar', field: 'venue', required: true },
  { header: 'estado', field: 'status', required: true },
];

/** Jam tab header to setlist row field, in response order. `integer` columns use the raw value. */
var SETLIST_FIELDS = [
  { header: 'posicion', field: 'position', required: true, integer: true },
  { header: 'id_tema', field: 'songId', required: true, integer: false },
  { header: 'titulo', field: 'title', required: true, integer: false },
  { header: 'artista', field: 'artist', required: true, integer: false },
  { header: 'tono', field: 'key', required: true, integer: false },
];

/** The seven slot columns, in Lineup.DEFAULT_INSTRUMENTS order: the order of the `slots` keys. */
var SLOT_FIELDS = [
  { header: 'Guitarra 1', field: 'guitar1', required: true },
  { header: 'Guitarra 2', field: 'guitar2', required: true },
  { header: 'Bajo', field: 'bass', required: true },
  { header: 'Batería', field: 'drums', required: true },
  { header: 'Voz', field: 'vocals', required: true },
  { header: 'Armónica', field: 'harmonica', required: true },
  { header: 'Teclados', field: 'keyboards', required: true },
];

/** The optional extra-participants column, passed through raw for the mapper to split. */
var EXTRA_FIELD = { header: 'Otros', field: 'extraParticipants', required: false };

var ISO_DATE_ = /^\d{4}-\d{2}-\d{2}$/;

/**
 * Builds the setlist rows of one jam tab from its display and raw values (row 1 is the header
 * row), in tab order. Throws ContractError `missing_header` or `duplicate_header`.
 */
function buildSetlist(displayRows, rawRows, tabName) {
  const name = tabName || 'The jam tab';
  const specs = SETLIST_FIELDS.concat(SLOT_FIELDS, [EXTRA_FIELD]);
  const columns = mapColumns(name, specs, displayRows.length > 0 ? displayRows[0] : []);
  const rows = [];
  for (let r = 1; r < displayRows.length; r++) {
    const displayRow = displayRows[r] || [];
    const rawRow = (rawRows && rawRows[r]) || [];
    let empty = true;
    const read = function (spec) {
      const col = columns[spec.field];
      let value = null;
      if (col !== undefined) {
        value = spec.integer ? integerTextCell(rawRow[col]) : textCell(displayRow[col]);
      }
      if (value !== null) {
        empty = false;
      }
      return value;
    };
    const row = {};
    SETLIST_FIELDS.forEach(function (spec) {
      row[spec.field] = read(spec);
    });
    const slots = {};
    SLOT_FIELDS.forEach(function (spec) {
      slots[spec.field] = read(spec);
    });
    row.slots = slots;
    row.extraParticipants = read(EXTRA_FIELD);
    if (!empty) {
      rows.push(row);
    }
  }
  return rows;
}

/**
 * Builds `{ jams }` from the Jams tab's display and raw values.
 *
 * `readTab(date)` returns `{ display, raw }` for the tab named `date`, or null when it does not
 * exist; it is called only for a PUBLICADA jam with a unique YYYY-MM-DD date.
 * `formatDate(date, pattern)` formats a typed cell in the spreadsheet's time zone.
 *
 * Throws ContractError only for the Jams tab itself. A problem with one jam's tab is reported in
 * that jam's `setlistError`, and the other jams are still served.
 */
function buildJams(jamsDisplay, jamsRaw, readTab, formatDate) {
  const columns = mapColumns(JAMS_TAB, JAMS_FIELDS, jamsDisplay.length > 0 ? jamsDisplay[0] : []);
  const jams = [];
  for (let r = 1; r < jamsDisplay.length; r++) {
    const displayRow = jamsDisplay[r] || [];
    const rawRow = (jamsRaw && jamsRaw[r]) || [];
    const jam = {
      date: isoDateCell(rawRow[columns.date], displayRow[columns.date], formatDate),
      startTime: timeCell(rawRow[columns.startTime], displayRow[columns.startTime], formatDate),
      venue: textCell(displayRow[columns.venue]),
      status: textCell(displayRow[columns.status]),
      setlist: null,
      setlistError: null,
    };
    if (jam.date !== null || jam.startTime !== null || jam.venue !== null || jam.status !== null) {
      jams.push(jam);
    }
  }

  const dateCount = {};
  jams.forEach(function (jam) {
    if (jam.date !== null) {
      dateCount[jam.date] = (dateCount[jam.date] || 0) + 1;
    }
  });

  jams.forEach(function (jam) {
    if (jam.status !== PUBLISHED_STATUS) {
      return;
    }
    if (jam.date === null || !ISO_DATE_.test(jam.date)) {
      jam.setlistError = setlistError_('invalid_date', 'fecha is not a YYYY-MM-DD date, so no tab is read');
      return;
    }
    if (dateCount[jam.date] > 1) {
      jam.setlistError = setlistError_('duplicate_date', 'Jams has the fecha ' + jam.date + ' on more than one row');
      return;
    }
    const tab = readTab(jam.date);
    if (!tab) {
      jam.setlistError = setlistError_('missing_tab', 'The tab ' + jam.date + ' does not exist');
      return;
    }
    try {
      jam.setlist = buildSetlist(tab.display, tab.raw, jam.date);
    } catch (err) {
      if (!(err instanceof ContractError)) {
        throw err;
      }
      jam.setlistError = setlistError_(err.code, err.message);
    }
  });
  return { jams: jams };
}

function setlistError_(code, message) {
  return { code: code, message: message };
}

if (typeof module !== 'undefined') {
  module.exports = { JAMS_TAB, PUBLISHED_STATUS, JAMS_FIELDS, SETLIST_FIELDS, SLOT_FIELDS, EXTRA_FIELD, buildSetlist, buildJams };
}
