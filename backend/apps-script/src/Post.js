/**
 * POST entry point (docs/apps-script-api.md, POST actions). The admin passphrase only ever travels
 * in a POST body, never in a URL. Today the only action is checkPassphrase; apps-script-write-auth
 * adds the write actions to ACTIONS and guards them with passphraseMatches_.
 *
 * Uses SCHEMA_VERSION from Code.js, and ContractError and mapColumns from Normalize.js (shared global
 * scope in Apps Script). This file is the only one that opens the Config tab, and no response ever
 * contains the stored or the submitted passphrase.
 */

var CONFIG_TAB = 'Config';
var PASSPHRASE_KEY = 'passphrase';

var CONFIG_COLUMNS = [
  { header: 'clave', field: 'key', required: true },
  { header: 'valor', field: 'value', required: true },
];

/** The only POST actions. An action not listed here is `unknown_action`. */
var ACTIONS = {
  checkPassphrase: checkPassphrase_,
};

/**
 * Apps Script calls this for POST requests. The body is JSON, `{"action": "...", ...}`. As with
 * doGet, every response is HTTP 200 and errors travel in the body.
 */
function doPost(e) {
  let body;
  try {
    body = handlePost(parsePostBody_(e), SpreadsheetApp.getActiveSpreadsheet());
  } catch (err) {
    body = postErrorBody_('internal_error', postErrorMessage_(err));
  }
  return ContentService.createTextOutput(JSON.stringify(body)).setMimeType(ContentService.MimeType.JSON);
}

/** The parsed JSON object of the POST body, or null when there is none or it is not an object. */
function parsePostBody_(e) {
  const contents = e && e.postData ? e.postData.contents : undefined;
  if (typeof contents !== 'string') {
    return null;
  }
  try {
    return JSON.parse(contents);
  } catch (ignored) {
    return null;
  }
}

/**
 * Routes a POST request. `request` is the parsed body, `spreadsheet` anything with
 * getSheetByName(name). Returns the response body as a plain object; never throws.
 */
function handlePost(request, spreadsheet) {
  if (request === null || typeof request !== 'object' || Array.isArray(request)) {
    return postErrorBody_('invalid_request', 'The body must be a JSON object');
  }
  const action = request.action;
  if (typeof action !== 'string' || !Object.prototype.hasOwnProperty.call(ACTIONS, action)) {
    return postErrorBody_('unknown_action', 'Unknown or missing action. Known: ' + Object.keys(ACTIONS).join(', '));
  }
  try {
    const result = ACTIONS[action](request, spreadsheet);
    const body = { schemaVersion: SCHEMA_VERSION };
    Object.keys(result).forEach(function (key) {
      body[key] = result[key];
    });
    return body;
  } catch (err) {
    if (err instanceof ContractError) {
      return postErrorBody_(err.code, err.message);
    }
    return postErrorBody_('internal_error', postErrorMessage_(err));
  }
}

/** `{ok: true}` when the submitted passphrase matches; otherwise a ContractError. */
function checkPassphrase_(request, spreadsheet) {
  if (!passphraseMatches_(spreadsheet, request.passphrase)) {
    throw new ContractError('invalid_passphrase', 'The passphrase is not correct');
  }
  return { ok: true };
}

/**
 * True when `submitted` is a string exactly equal (case-sensitive) to the trimmed passphrase in
 * Config. The stored value is read first, so an unset passphrase rejects every attempt with
 * `passphrase_not_set` and a blank submission can never match a blank cell.
 */
function passphraseMatches_(spreadsheet, submitted) {
  const stored = readPassphrase_(spreadsheet);
  return typeof submitted === 'string' && submitted === stored;
}

/**
 * The trimmed passphrase from the Config tab. Fails closed: a missing tab, missing headers, a
 * missing row or a blank value is `passphrase_not_set`. Messages never include the value.
 */
function readPassphrase_(spreadsheet) {
  const sheet = spreadsheet.getSheetByName(CONFIG_TAB);
  if (!sheet) {
    throw notSet_();
  }
  const rows = sheet.getDataRange().getDisplayValues();
  if (rows.length === 0) {
    throw notSet_();
  }
  let columns;
  try {
    columns = mapColumns(CONFIG_TAB, CONFIG_COLUMNS, rows[0]);
  } catch (err) {
    if (err instanceof ContractError) {
      throw notSet_();
    }
    throw err;
  }
  for (let i = 1; i < rows.length; i++) {
    const key = String(rows[i][columns.key] === undefined ? '' : rows[i][columns.key]).trim();
    if (key === PASSPHRASE_KEY) {
      const value = String(rows[i][columns.value] === undefined ? '' : rows[i][columns.value]).trim();
      if (value === '') {
        throw notSet_();
      }
      return value;
    }
  }
  throw notSet_();
}

/**
 * The error envelope, as Code.js builds it. Kept here because Code.js does not export its own
 * helpers to the Node tests and this slice leaves Code.js untouched (it stays deployed as is).
 */
function postErrorBody_(code, message) {
  return { schemaVersion: SCHEMA_VERSION, error: { code: code, message: message } };
}

function postErrorMessage_(err) {
  if (err && typeof err.message === 'string') {
    return err.message;
  }
  return String(err);
}

function notSet_() {
  return new ContractError('passphrase_not_set', 'No admin passphrase is set');
}

if (typeof module !== 'undefined') {
  module.exports = { ACTIONS, doPost, handlePost, passphraseMatches_, readPassphrase_ };
}
