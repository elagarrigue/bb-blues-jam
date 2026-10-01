/**
 * Cell normalization for the read endpoints (docs/sheet-schema.md, Reading cells).
 *
 * Apps Script loads every file of the project into one global scope, so these functions are
 * globals there. Node loads this file with require(); see the guarded export at the end.
 */

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

if (typeof module !== 'undefined') {
  module.exports = { isBlank, textCell, integerTextCell };
}
