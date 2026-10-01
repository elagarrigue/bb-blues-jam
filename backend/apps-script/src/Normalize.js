/**
 * Cell normalization and header matching for the read endpoints (docs/sheet-schema.md, Reading
 * cells).
 *
 * Apps Script loads every file of the project into one global scope, so these functions are
 * globals there. Node loads this file with require(); see the guarded export at the end.
 */

/** A structural problem with a stable `code` from the contract's error list. */
class ContractError extends Error {
  constructor(code, message) {
    super(message);
    this.name = 'ContractError';
    this.code = code;
  }
}

/** True when the value is null, undefined, or a string that is empty after trimming. */
function isBlank(value) {
  return value === null || value === undefined || String(value).trim() === '';
}

/**
 * A text cell as the admin sees it: the display string, trimmed (String.prototype.trim also
 * removes NBSP). Empty after trimming, null or undefined gives null.
 */
function textCell(display) {
  if (isBlank(display)) {
    return null;
  }
  return String(display).trim();
}

/**
 * An integer column read from the raw value (getValues), so a locale number format such as
 * es-AR "12.345" never reaches the response. An integral number gives its decimal string, any
 * other number gives String(n) for the mapper to reject, and a string is trimmed. Empty gives null.
 */
function integerTextCell(raw) {
  if (typeof raw === 'number') {
    if (Number.isInteger(raw)) {
      return raw.toFixed(0);
    }
    return String(raw);
  }
  return textCell(raw);
}

/**
 * True for a valid Date. Uses the internal class tag rather than instanceof, so a Date built in
 * another realm (the vm test, or the Sheets service) is still recognized.
 */
function isDateValue(value) {
  return Object.prototype.toString.call(value) === '[object Date]' && !isNaN(value.getTime());
}

/**
 * A date cell as YYYY-MM-DD. A typed date cell (raw Date) is formatted with `formatDate(date,
 * pattern)`, which formats in the spreadsheet's time zone; a text cell is trimmed and passed
 * through for the mapper to judge.
 */
function isoDateCell(raw, display, formatDate) {
  if (isDateValue(raw)) {
    return formatDate(raw, 'yyyy-MM-dd');
  }
  return textCell(display);
}

var TIME_TEXT_ = /^(\d{1,2}):(\d{2})(:00)?$/;

/**
 * A time cell as HH:MM, 24 hours. The display text is tried first: H:MM, HH:MM or HH:MM:00 with
 * a valid hour and minute is zero-padded. Otherwise a typed time (raw Date) is formatted with
 * `formatDate`; otherwise the trimmed text is passed through for the mapper to judge.
 *
 * Display first, because Sheets stores a time-only cell on its 1899-12-30 epoch, where the
 * historical offset of a zone can shift the minutes when the Date is formatted.
 */
function timeCell(raw, display, formatDate) {
  const text = textCell(display);
  const match = text === null ? null : TIME_TEXT_.exec(text);
  if (match !== null) {
    const hour = Number(match[1]);
    const minute = Number(match[2]);
    if (hour <= 23 && minute <= 59) {
      return (hour < 10 ? '0' : '') + hour + ':' + match[2];
    }
  }
  if (isDateValue(raw)) {
    return formatDate(raw, 'HH:mm');
  }
  return text;
}

/**
 * Maps each field of `specs` ({ header, field, required }) to its column index in `headerRow`.
 * Headers match by trimmed exact name (case and accents count); unmapped columns are ignored and
 * optional fields with no column are left out. Throws ContractError `duplicate_header` when a
 * mapped header appears twice, and `missing_header` when a required one is absent.
 */
function mapColumns(tabName, specs, headerRow) {
  const byHeader = {};
  specs.forEach(function (spec) {
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
      throw new ContractError('duplicate_header', tabName + ' has the header "' + name + '" twice');
    }
    columns[field] = index;
  });
  const missing = specs.filter(function (spec) {
    return spec.required && columns[spec.field] === undefined;
  }).map(function (spec) {
    return spec.header;
  });
  if (missing.length > 0) {
    throw new ContractError('missing_header', tabName + ' is missing required headers: ' + missing.join(', '));
  }
  return columns;
}

if (typeof module !== 'undefined') {
  module.exports = { ContractError, isBlank, textCell, integerTextCell, isDateValue, isoDateCell, timeCell, mapColumns };
}
