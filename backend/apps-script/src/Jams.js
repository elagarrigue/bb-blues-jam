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

/** What a slot reads as when its column is not in the tab or the song does not use it. */
var NOT_IN_LINEUP_ = '-';

/**
 * The eighteen slot columns, three per instrument, in the order of the `slots` keys and of
 * Lineup.MAX_INSTRUMENTS in the app (D-18, amended 12 October 2026). The original seven are
 * required, so a tab that was never widened still reads; the other eleven are optional. The
 * first column of the five single-slot instruments was `Bajo`, `Batería`, `Voz`, `Armónica` and
 * `Teclados` before the widening: that spelling stays accepted as an alias of `... 1`.
 */
var SLOT_FIELDS = [
  { header: 'Guitarra 1', field: 'guitar1', required: true },
  { header: 'Guitarra 2', field: 'guitar2', required: true },
  { header: 'Guitarra 3', field: 'guitar3', required: false },
  { header: 'Bajo 1', field: 'bass1', required: true, aliases: ['Bajo'] },
  { header: 'Bajo 2', field: 'bass2', required: false },
  { header: 'Bajo 3', field: 'bass3', required: false },
  { header: 'Batería 1', field: 'drums1', required: true, aliases: ['Batería'] },
  { header: 'Batería 2', field: 'drums2', required: false },
  { header: 'Batería 3', field: 'drums3', required: false },
  { header: 'Voz 1', field: 'vocals1', required: true, aliases: ['Voz'] },
  { header: 'Voz 2', field: 'vocals2', required: false },
  { header: 'Voz 3', field: 'vocals3', required: false },
  { header: 'Armónica 1', field: 'harmonica1', required: true, aliases: ['Armónica'] },
  { header: 'Armónica 2', field: 'harmonica2', required: false },
  { header: 'Armónica 3', field: 'harmonica3', required: false },
  { header: 'Teclados 1', field: 'keyboards1', required: true, aliases: ['Teclados'] },
  { header: 'Teclados 2', field: 'keyboards2', required: false },
  { header: 'Teclados 3', field: 'keyboards3', required: false },
];

/**
 * The key each of the five single-slot instruments had before the three-slot layout, and the key it
 * is now: `bass` is `bass1`, and so on. Responses carry both, with the same value, so an app that
 * predates the layout keeps reading `slots` (`guitar1` and `guitar2` never changed).
 */
var LEGACY_SLOT_KEYS_ = {
  bass: 'bass1', drums: 'drums1', vocals: 'vocals1', harmonica: 'harmonica1', keyboards: 'keyboards1',
};

/** `slots` with the five legacy keys appended, each equal to its `...1` key. */
function withLegacySlotKeys_(slots) {
  const out = Object.assign({}, slots);
  Object.keys(LEGACY_SLOT_KEYS_).forEach(function (legacy) {
    out[legacy] = slots[LEGACY_SLOT_KEYS_[legacy]];
  });
  return out;
}

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
      slots[spec.field] = columns[spec.field] === undefined ? NOT_IN_LINEUP_ : read(spec);
    });
    row.slots = withLegacySlotKeys_(slots);
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
  module.exports = { JAMS_TAB, PUBLISHED_STATUS, JAMS_FIELDS, SETLIST_FIELDS, NOT_IN_LINEUP_, LEGACY_SLOT_KEYS_, withLegacySlotKeys_, SLOT_FIELDS, EXTRA_FIELD, buildSetlist, buildJams };
}
