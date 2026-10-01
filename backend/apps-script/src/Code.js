/**
 * Web app entry point (docs/apps-script-api.md). GET only: this slice serves no POST.
 *
 * Uses ContractError from Normalize.js, CATALOG_TAB and buildCatalog from Catalog.js, and JAMS_TAB
 * and buildJams from Jams.js (shared global scope in Apps Script).
 */

var SCHEMA_VERSION = 1;

/** The only routes. A resource not listed here is `unknown_resource`. */
var ROUTES = {
  catalog: readCatalog_,
  jams: readJams_,
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
 * getSheetByName(name) and getSpreadsheetTimeZone(); nothing else of it is used. Returns the response body as a plain object; never throws.
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

/**
 * The Jams tab plus the tab of each published jam. Dates are formatted in the spreadsheet's own
 * time zone, the one Sheets used to build the typed cells, so they round-trip what the admin sees.
 */
function readJams_(spreadsheet) {
  const sheet = spreadsheet.getSheetByName(JAMS_TAB);
  if (!sheet) {
    throw new ContractError('missing_tab', 'The tab ' + JAMS_TAB + ' does not exist');
  }
  const timeZone = spreadsheet.getSpreadsheetTimeZone();
  const formatDate = function (date, pattern) {
    return Utilities.formatDate(date, timeZone, pattern);
  };
  const readTab = function (date) {
    const tab = spreadsheet.getSheetByName(date);
    if (!tab) {
      return null;
    }
    const tabRange = tab.getDataRange();
    return { display: tabRange.getDisplayValues(), raw: tabRange.getValues() };
  };
  const range = sheet.getDataRange();
  return buildJams(range.getDisplayValues(), range.getValues(), readTab, formatDate);
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
