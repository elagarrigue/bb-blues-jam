/**
 * Web app entry point (docs/apps-script-api.md). GET only: this slice serves no POST.
 *
 * Uses CATALOG_TAB, ContractError and buildCatalog from Catalog.js (shared global scope in
 * Apps Script).
 */

var SCHEMA_VERSION = 1;

/** The only routes. A resource not listed here is `unknown_resource`. */
var ROUTES = {
  catalog: readCatalog_,
};

/**
 * Apps Script calls this for GET requests. ContentService cannot set an HTTP status, so every
 * response is 200 and errors travel in the body.
 */
function doGet(e) {
  let body;
  try {
    body = handleGet(e && e.parameter, SpreadsheetApp.getActiveSpreadsheet());
  } catch (err) {
    body = errorBody_('internal_error', errorMessage_(err));
  }
  return ContentService.createTextOutput(JSON.stringify(body)).setMimeType(ContentService.MimeType.JSON);
}

/**
 * Routes a request. `params` is the query parameter map, `spreadsheet` anything with
 * getSheetByName(name). Returns the response body as a plain object; never throws.
 */
function handleGet(params, spreadsheet) {
  const resource = params ? params.resource : undefined;
  if (typeof resource !== 'string' || !Object.prototype.hasOwnProperty.call(ROUTES, resource)) {
    return errorBody_('unknown_resource', 'Unknown or missing resource. Known: ' + Object.keys(ROUTES).join(', '));
  }
  try {
    const result = ROUTES[resource](spreadsheet);
    const body = { schemaVersion: SCHEMA_VERSION };
    Object.keys(result).forEach(function (key) {
      body[key] = result[key];
    });
    return body;
  } catch (err) {
    if (err instanceof ContractError) {
      return errorBody_(err.code, err.message);
    }
    return errorBody_('internal_error', errorMessage_(err));
  }
}

function readCatalog_(spreadsheet) {
  const sheet = spreadsheet.getSheetByName(CATALOG_TAB);
  if (!sheet) {
    throw new ContractError('missing_tab', 'The tab ' + CATALOG_TAB + ' does not exist');
  }
  const range = sheet.getDataRange();
  return buildCatalog(range.getDisplayValues(), range.getValues());
}

function errorBody_(code, message) {
  return { schemaVersion: SCHEMA_VERSION, error: { code: code, message: message } };
}

function errorMessage_(err) {
  if (err && typeof err.message === 'string') {
    return err.message;
  }
  return String(err);
}

if (typeof module !== 'undefined') {
  module.exports = { SCHEMA_VERSION, ROUTES, doGet, handleGet };
}
