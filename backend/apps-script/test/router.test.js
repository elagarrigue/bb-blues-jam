'use strict';
// T3: the router, the error envelope, doGet, and the guarantee that the passphrase tab is never
// opened and never returned.

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { loadScript, SRC_DIR } = require('./helpers/load.js');
const { checkCatalogResponse } = require('./helpers/contract.js');

const { handleGet, doGet, ROUTES } = loadScript();

const PASSPHRASE = 's3cret';
const PASSPHRASE_TAB = 'Config';
const CATALOG_ROWS = [
  ['id', 'titulo', 'artista', 'tono_default', 'tempo', 'etiquetas', 'dificultad', 'songsterr_id'],
  ['crossroads', 'Crossroads', 'Eric Clapton', 'A', '', '', '', ''],
];

function fakeSheet(rows) {
  return {
    getDataRange() {
      return {
        getDisplayValues: () => rows.map((row) => row.map(String)),
        getValues: () => rows.map((row) => row.slice()),
      };
    },
  };
}

/**
 * A spreadsheet holding Catalogo and the passphrase tab. Every property read on it is recorded,
 * so a test can prove the script only ever called getSheetByName, and with which names.
 */
function fakeSpreadsheet({ catalog = true, throwOnRead = false } = {}) {
  const tabs = { [PASSPHRASE_TAB]: fakeSheet([['clave', 'valor'], ['passphrase', PASSPHRASE]]) };
  if (catalog) {
    tabs.Catalogo = fakeSheet(CATALOG_ROWS);
  }
  const accessed = [];
  const requested = [];
  const target = {
    getSheetByName(name) {
      requested.push(name);
      if (throwOnRead) {
        throw new Error('boom: service unavailable');
      }
      return tabs[name] || null;
    },
    getSheets() {
      return Object.values(tabs);
    },
  };
  const spreadsheet = new Proxy(target, {
    get(obj, prop) {
      accessed.push(String(prop));
      return obj[prop];
    },
  });
  return { spreadsheet, accessed, requested };
}

function assertError(body, code) {
  assert.deepStrictEqual(Object.keys(body), ['schemaVersion', 'error']);
  assert.equal(body.schemaVersion, 1);
  assert.deepStrictEqual(Object.keys(body.error), ['code', 'message']);
  assert.equal(body.error.code, code);
  assert.equal(typeof body.error.message, 'string');
}

test('the route table is exactly { catalog }', () => {
  assert.deepStrictEqual(Object.keys(ROUTES), ['catalog']);
});

test('resource=catalog returns the catalog and reads only Catalogo', () => {
  const fake = fakeSpreadsheet();
  const body = handleGet({ resource: 'catalog' }, fake.spreadsheet);
  assert.deepStrictEqual(checkCatalogResponse(body), []);
  assert.equal(body.songs.length, 1);
  assert.deepStrictEqual(fake.requested, ['Catalogo']);
  assert.deepStrictEqual(fake.accessed, ['getSheetByName']);
});

test('the passphrase tab, a missing resource and a wrong-case resource are unknown_resource', () => {
  const cases = [{ resource: 'config' }, { resource: PASSPHRASE_TAB }, { resource: 'CATALOG' }, {}, null, undefined, { resource: '' }, { resource: 'toString' }, { resource: '__proto__' }];
  for (const params of cases) {
    const fake = fakeSpreadsheet();
    const body = handleGet(params, fake.spreadsheet);
    assertError(body, 'unknown_resource');
    assert.deepStrictEqual(fake.accessed, [], `spreadsheet touched for ${JSON.stringify(params)}`);
  }
});

test('a missing Catalogo tab is missing_tab', () => {
  assertError(handleGet({ resource: 'catalog' }, fakeSpreadsheet({ catalog: false }).spreadsheet), 'missing_tab');
});

test('a structural error in the tab travels as its code', () => {
  const spreadsheet = { getSheetByName: () => fakeSheet([['id', 'titulo']]) };
  assertError(handleGet({ resource: 'catalog' }, spreadsheet), 'missing_header');
});

test('an unexpected exception is internal_error with its message', () => {
  const body = handleGet({ resource: 'catalog' }, fakeSpreadsheet({ throwOnRead: true }).spreadsheet);
  assertError(body, 'internal_error');
  assert.equal(body.error.message, 'boom: service unavailable');
});

test('no request ever opens the passphrase tab, and no serialized body contains its value', () => {
  const requests = [{ resource: 'catalog' }, { resource: 'config' }, { resource: PASSPHRASE_TAB }, {}, { resource: 'jams' }];
  for (const params of requests) {
    for (const options of [{}, { catalog: false }, { throwOnRead: true }]) {
      const fake = fakeSpreadsheet(options);
      const text = JSON.stringify(handleGet(params, fake.spreadsheet));
      assert.ok(!text.includes(PASSPHRASE), `passphrase leaked: ${text}`);
      assert.ok(!fake.requested.includes(PASSPHRASE_TAB), `requested ${fake.requested}`);
      assert.ok(fake.accessed.every((p) => p === 'getSheetByName'), `accessed ${fake.accessed}`);
    }
  }
});

test('no file in src/ names the passphrase tab, and none defines doPost', () => {
  const files = fs.readdirSync(SRC_DIR).filter((f) => f.endsWith('.js'));
  assert.ok(files.length >= 3, `found ${files}`);
  for (const file of files) {
    const source = fs.readFileSync(path.join(SRC_DIR, file), 'utf8');
    assert.ok(!source.includes(PASSPHRASE_TAB), `${file} contains "${PASSPHRASE_TAB}"`);
    assert.ok(!/passphrase/i.test(source), `${file} mentions the passphrase`);
    assert.ok(!/\bdoPost\b/.test(source), `${file} defines doPost`);
  }
});

test('doGet serializes handleGet as JSON with the active spreadsheet', () => {
  const fake = fakeSpreadsheet();
  const output = {};
  globalThis.SpreadsheetApp = { getActiveSpreadsheet: () => fake.spreadsheet };
  globalThis.ContentService = {
    MimeType: { JSON: 'application/json' },
    createTextOutput(text) {
      output.text = text;
      return {
        setMimeType(mime) {
          output.mime = mime;
          return output;
        },
      };
    },
  };
  try {
    const result = doGet({ parameter: { resource: 'catalog' } });
    assert.equal(result, output);
    assert.equal(output.mime, 'application/json');
    assert.deepStrictEqual(JSON.parse(output.text), handleGet({ resource: 'catalog' }, fakeSpreadsheet().spreadsheet));

    doGet(undefined);
    assertError(JSON.parse(output.text), 'unknown_resource');

    globalThis.SpreadsheetApp = {
      getActiveSpreadsheet() {
        throw new Error('no active spreadsheet');
      },
    };
    doGet({ parameter: { resource: 'catalog' } });
    assertError(JSON.parse(output.text), 'internal_error');
  } finally {
    delete globalThis.SpreadsheetApp;
    delete globalThis.ContentService;
  }
});

test('the src files run as Apps Script does: one shared scope, no module object', () => {
  const vm = require('node:vm');
  const { SRC_FILES } = require('./helpers/load.js');
  const fake = fakeSpreadsheet();
  const output = {};
  const context = vm.createContext({
    SpreadsheetApp: { getActiveSpreadsheet: () => fake.spreadsheet },
    ContentService: {
      MimeType: { JSON: 'application/json' },
      createTextOutput: (text) => ({ setMimeType: () => Object.assign(output, { text }) }),
    },
  });
  for (const file of SRC_FILES) {
    vm.runInContext(fs.readFileSync(path.join(SRC_DIR, file), 'utf8'), context, { filename: file });
  }
  assert.equal(vm.runInContext('typeof module', context), 'undefined');
  vm.runInContext("doGet({ parameter: { resource: 'catalog' } })", context);
  const body = JSON.parse(output.text);
  assert.deepStrictEqual(checkCatalogResponse(body), []);
  assert.equal(body.songs[0].id, 'crossroads');
  vm.runInContext('doGet({ parameter: {} })', context);
  assertError(JSON.parse(output.text), 'unknown_resource');
});

test('the manifest grants the bound Sheet only and serves anonymously as the owner', () => {
  const manifest = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'appsscript.json'), 'utf8'));
  assert.equal(manifest.runtimeVersion, 'V8');
  assert.equal(manifest.timeZone, 'America/Argentina/Buenos_Aires');
  assert.deepStrictEqual(manifest.oauthScopes, ['https://www.googleapis.com/auth/spreadsheets.currentonly']);
  assert.deepStrictEqual(manifest.webapp, { executeAs: 'USER_DEPLOYING', access: 'ANYONE_ANONYMOUS' });
});
