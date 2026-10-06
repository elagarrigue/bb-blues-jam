/**
 * POST entry point (docs/apps-script-api.md, POST actions). The admin passphrase only ever travels
 * in a POST body, never in a URL.
 *
 * Every action in ACTIONS passes one passphrase guard, requirePassphrase_, in the router and before
 * the action runs (apps-script-write-auth). No action calls the guard itself, so an action added
 * later cannot forget it. The guard reads Config on every request and caches nothing, so rotating
 * the passphrase in the Sheet rejects every device at once. Failed guesses are rate limited with a
 * global counter in CacheService, and write actions run under the script lock.
 *
 * Uses SCHEMA_VERSION from Code.js, and ContractError and mapColumns from Normalize.js (shared global
 * scope in Apps Script). This file is the only one that opens the Config tab, and no response,
 * message or cache entry ever contains the stored or the submitted passphrase.
 */

var CONFIG_TAB = 'Config';
var PASSPHRASE_KEY = 'passphrase';

var CONFIG_COLUMNS = [
  { header: 'clave', field: 'key', required: true },
  { header: 'valor', field: 'value', required: true },
];

/** Rate limit (user approval W2): at most 10 failed guesses per fixed 10-minute window, globally. */
var RATE_LIMIT_MAX_FAILURES = 10;
var RATE_LIMIT_WINDOW_MS = 10 * 60 * 1000;
var RATE_LIMIT_TTL_SECONDS = 20 * 60;
var RATE_LIMIT_KEY_PREFIX = 'auth_failures_';

/** How long a write waits for another write to finish before answering `busy`. */
var WRITE_LOCK_TIMEOUT_MS = 10000;

/** The transient tab checkWriteAccess creates and deletes within one request. Never read. */
var WRITE_CHECK_TAB = '_prueba_escritura';

/**
 * The only POST actions. An action not listed here is `unknown_action`. Every one of them passes
 * the passphrase guard first; `write: true` also runs it under the script lock.
 */
var ACTIONS = {
  checkPassphrase: { write: false, run: checkPassphrase_ },
  checkWriteAccess: { write: true, run: checkWriteAccess_ },
};

/**
 * Apps Script calls this for POST requests. The body is JSON, `{"action": "...", ...}`. As with
 * doGet, every response is HTTP 200 and errors travel in the body.
 */
function doPost(e) {
  let body;
  try {
    const services = { cache: CacheService.getScriptCache(), lock: LockService.getScriptLock(), now: Date.now() };
    body = handlePost(parsePostBody_(e), SpreadsheetApp.getActiveSpreadsheet(), services);
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
 * Routes a POST request. `request` is the parsed body, `spreadsheet` the active spreadsheet, and
 * `services` is `{ cache, lock, now }` (a script cache, the script lock, the time in ms). In order:
 * invalid_request, unknown_action, the passphrase guard, the lock for a write action, the action.
 * Returns the response body as a plain object; never throws.
 */
function handlePost(request, spreadsheet, services) {
  if (request === null || typeof request !== 'object' || Array.isArray(request)) {
    return postErrorBody_('invalid_request', 'The body must be a JSON object');
  }
  const action = request.action;
  if (typeof action !== 'string' || !Object.prototype.hasOwnProperty.call(ACTIONS, action)) {
    return postErrorBody_('unknown_action', 'Unknown or missing action. Known: ' + Object.keys(ACTIONS).join(', '));
  }
  try {
    const entry = ACTIONS[action];
    requirePassphrase_(request, spreadsheet, services);
    const run = function () {
      return entry.run(request, spreadsheet, services);
    };
    const result = entry.write ? withWriteLock_(services.lock, run) : run();
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

/**
 * The guard every action passes, called only by handlePost. Throws `rate_limited` when the current
 * window already holds the maximum of failed guesses (Config is then not read), `passphrase_not_set`
 * when Config has no usable passphrase, and `invalid_passphrase` (counting one failure) when the
 * request does not carry it. The cache only ever holds a count under a key made of the window
 * number; a cache that throws is ignored (fails open), and the passphrase check still runs.
 */
function requirePassphrase_(request, spreadsheet, services) {
  const key = RATE_LIMIT_KEY_PREFIX + Math.floor(services.now / RATE_LIMIT_WINDOW_MS);
  const failures = failureCount_(services.cache, key);
  if (failures >= RATE_LIMIT_MAX_FAILURES) {
    throw new ContractError('rate_limited', 'Too many failed passphrase attempts. Try again later');
  }
  if (!passphraseMatches_(spreadsheet, request.passphrase)) {
    recordFailure_(services.cache, key, failures + 1);
    throw new ContractError('invalid_passphrase', 'The passphrase is not correct');
  }
}

/** The failed guesses counted under `key`; 0 when absent, unreadable or the cache throws. */
function failureCount_(cache, key) {
  try {
    const count = parseInt(cache.get(key), 10);
    return isNaN(count) ? 0 : count;
  } catch (ignored) {
    return 0;
  }
}

/**
 * Stores the new count. Not atomic: two failures at the same moment may count once, which only
 * loosens the limit slightly. A cache that throws is ignored.
 */
function recordFailure_(cache, key, count) {
  try {
    cache.put(key, String(count), RATE_LIMIT_TTL_SECONDS);
  } catch (ignored) {
    // Fail open: the passphrase check already ran.
  }
}

/** Runs `fn` holding the script lock; `busy` when another write holds it for too long. */
function withWriteLock_(lock, fn) {
  if (!lock.tryLock(WRITE_LOCK_TIMEOUT_MS)) {
    throw new ContractError('busy', 'Another write is in progress. Try again');
  }
  try {
    return fn();
  } finally {
    lock.releaseLock();
  }
}

/** The guard already accepted the passphrase; checking it is all this action does. */
function checkPassphrase_() {
  return { ok: true };
}

/**
 * A self-cleaning deploy check: creates the tab WRITE_CHECK_TAB (deleting a leftover first), writes
 * a marker to A1, reads it back and deletes the tab. Touches no other tab. A read-back that differs
 * is an Error, so `internal_error`, after the tab is deleted.
 */
function checkWriteAccess_(request, spreadsheet, services) {
  const leftover = spreadsheet.getSheetByName(WRITE_CHECK_TAB);
  if (leftover) {
    spreadsheet.deleteSheet(leftover);
  }
  const sheet = spreadsheet.insertSheet(WRITE_CHECK_TAB);
  const marker = 'write check ' + new Date(services.now).toISOString();
  let readBack;
  try {
    const cell = sheet.getRange('A1');
    cell.setValue(marker);
    readBack = cell.getDisplayValue();
  } finally {
    spreadsheet.deleteSheet(sheet);
  }
  if (readBack !== marker) {
    throw new Error('The write check read back a different value');
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
 * helpers to the Node tests and this file must not need Code.js to change.
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
  module.exports = {
    ACTIONS,
    WRITE_CHECK_TAB,
    doPost,
    handlePost,
    passphraseMatches_,
    readPassphrase_,
    requirePassphrase_,
  };
}
