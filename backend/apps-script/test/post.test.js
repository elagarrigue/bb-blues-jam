'use strict';
// admin-passphrase-login: the POST router, the checkPassphrase action, and the guarantees that the
// passphrase is never returned, an unset passphrase rejects everything, and only Config is opened.

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { loadScript, SRC_DIR, SRC_FILES } = require('./helpers/load.js');

const { handlePost, doPost, ACTIONS, handleGet } = loadScript();

const STORED = 'Blues-Pass 42';
const CONFIG_TAB = 'Config';

function fakeSheet(rows) {
  return {
    getDataRange() {
      return { getDisplayValues: () => rows.map((row) => row.map(String)) };
    },
  };
}

/** A spreadsheet with an optional Config tab; every property read and tab request is recorded. */
function fakeSpreadsheet({ config = [['clave', 'valor'], ['passphrase', STORED]], throwOnRead = false } = {}) {
  const tabs = { Catalogo: fakeSheet([['id']]), Jams: fakeSheet([['fecha']]) };
  if (config) {
    tabs[CONFIG_TAB] = fakeSheet(config);
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
  };
  const spreadsheet = new Proxy(target, {
    get(obj, prop) {
      accessed.push(String(prop));
      return obj[prop];
    },
  });
  return { spreadsheet, accessed, requested };
}

function check(passphrase, options) {
  const request = { action: 'checkPassphrase' };
  if (passphrase !== undefined) {
    request.passphrase = passphrase;
  }
  return handlePost(request, fakeSpreadsheet(options).spreadsheet);
}

function assertError(body, code) {
  assert.deepStrictEqual(Object.keys(body), ['schemaVersion', 'error']);
  assert.equal(body.schemaVersion, 1);
  assert.deepStrictEqual(Object.keys(body.error), ['code', 'message']);
  assert.equal(body.error.code, code);
  assert.equal(typeof body.error.message, 'string');
}

test('the action table is exactly { checkPassphrase }', () => {
  assert.deepStrictEqual(Object.keys(ACTIONS), ['checkPassphrase']);
});

test('the right passphrase is ok and reads only Config', () => {
  const fake = fakeSpreadsheet();
  const body = handlePost({ action: 'checkPassphrase', passphrase: STORED }, fake.spreadsheet);
  assert.deepStrictEqual(body, { schemaVersion: 1, ok: true });
  assert.deepStrictEqual(fake.requested, [CONFIG_TAB]);
  assert.deepStrictEqual(fake.accessed, ['getSheetByName']);
});

test('the stored value is trimmed and found among other rows and extra columns', () => {
  const config = [['nota', 'clave', 'valor'], ['x', 'otra', 'y'], ['', '  passphrase ', `  ${STORED}  `]];
  assert.deepStrictEqual(check(STORED, { config }), { schemaVersion: 1, ok: true });
});

test('a wrong, differently cased, padded, empty, missing or non-string passphrase is invalid_passphrase', () => {
  const wrong = ['definitely-wrong', STORED.toLowerCase(), STORED.toUpperCase(), ` ${STORED}`, `${STORED} `, '', undefined, null, 42, true, [STORED], { value: STORED }];
  for (const passphrase of wrong) {
    assertError(check(passphrase), 'invalid_passphrase');
  }
});

test('an unset passphrase rejects every attempt, blank included, as passphrase_not_set', () => {
  const configs = [
    false,
    [],
    [['clave', 'valor']],
    [['clave'], ['passphrase']],
    [['valor'], [STORED]],
    [['clave', 'valor'], ['otra', STORED]],
    [['clave', 'valor'], ['passphrase', '']],
    [['clave', 'valor'], ['passphrase', '   ']],
    [['clave', 'valor', 'clave'], ['passphrase', STORED, 'x']],
  ];
  for (const config of configs) {
    for (const passphrase of ['', '   ', STORED, undefined, 'definitely-wrong']) {
      assertError(check(passphrase, { config }), 'passphrase_not_set');
    }
  }
});

test('a body that is not a JSON object is invalid_request and touches nothing', () => {
  for (const request of [null, undefined, 'checkPassphrase', 42, true, [], [{ action: 'checkPassphrase' }]]) {
    const fake = fakeSpreadsheet();
    assertError(handlePost(request, fake.spreadsheet), 'invalid_request');
    assert.deepStrictEqual(fake.accessed, []);
  }
});

test('a missing or unknown action is unknown_action, lists the known actions and touches nothing', () => {
  for (const request of [{}, { action: '' }, { action: 'CHECKPASSPHRASE' }, { action: 'catalog' }, { action: 'toString' }, { action: '__proto__' }, { action: 42 }, { passphrase: STORED }]) {
    const fake = fakeSpreadsheet();
    const body = handlePost(request, fake.spreadsheet);
    assertError(body, 'unknown_action');
    assert.ok(body.error.message.endsWith('Known: checkPassphrase'), body.error.message);
    assert.deepStrictEqual(fake.accessed, []);
  }
});

test('an unexpected exception is internal_error', () => {
  const body = check(STORED, { throwOnRead: true });
  assertError(body, 'internal_error');
  assert.equal(body.error.message, 'boom: service unavailable');
});

test('no response contains the stored or the submitted passphrase, and only Config is ever opened', () => {
  const submitted = ['definitely-wrong', STORED, ` ${STORED}`, 'zz-submitted-marker'];
  const configs = [undefined, false, [['clave', 'valor'], ['passphrase', '']], [['clave'], ['passphrase']]];
  for (const config of configs) {
    for (const passphrase of submitted) {
      for (const options of [{ config }, { config, throwOnRead: true }]) {
        const fake = fakeSpreadsheet(options);
        const body = handlePost({ action: 'checkPassphrase', passphrase }, fake.spreadsheet);
        const text = JSON.stringify(body);
        assert.ok(!text.includes(STORED), `stored passphrase leaked: ${text}`);
        assert.ok(!text.includes('zz-submitted-marker') && !text.includes('definitely-wrong'), `submitted passphrase leaked: ${text}`);
        assert.ok(fake.requested.every((name) => name === CONFIG_TAB), `requested ${fake.requested}`);
        assert.ok(fake.accessed.every((p) => p === 'getSheetByName'), `accessed ${fake.accessed}`);
      }
    }
  }
});

test('the GET routes still never open Config', () => {
  for (const resource of ['catalog', 'jams', 'config', CONFIG_TAB, 'checkPassphrase']) {
    const fake = fakeSpreadsheet();
    handleGet({ resource }, { getSheetByName: fake.spreadsheet.getSheetByName, getSpreadsheetTimeZone: () => 'America/Argentina/Buenos_Aires' });
    assert.ok(!fake.requested.includes(CONFIG_TAB), `${resource} requested ${fake.requested}`);
  }
});

function withServices(spreadsheetApp, run) {
  const output = {};
  globalThis.SpreadsheetApp = spreadsheetApp;
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
    run(output);
  } finally {
    delete globalThis.SpreadsheetApp;
    delete globalThis.ContentService;
  }
}

test('doPost parses the JSON body and serializes handlePost with the active spreadsheet', () => {
  const fake = fakeSpreadsheet();
  withServices({ getActiveSpreadsheet: () => fake.spreadsheet }, (output) => {
    const result = doPost({ postData: { contents: JSON.stringify({ action: 'checkPassphrase', passphrase: STORED }) } });
    assert.equal(result, output);
    assert.equal(output.mime, 'application/json');
    assert.deepStrictEqual(JSON.parse(output.text), { schemaVersion: 1, ok: true });

    doPost({ postData: { contents: JSON.stringify({ action: 'checkPassphrase', passphrase: 'definitely-wrong' }) } });
    assertError(JSON.parse(output.text), 'invalid_passphrase');

    for (const e of [undefined, {}, { postData: {} }, { postData: { contents: 'not json {' } }, { postData: { contents: '' } }, { postData: { contents: '[1]' } }]) {
      doPost(e);
      assertError(JSON.parse(output.text), 'invalid_request');
    }

    doPost({ postData: { contents: '{}' } });
    const unknown = JSON.parse(output.text);
    assertError(unknown, 'unknown_action');
    assert.ok(unknown.error.message.endsWith('Known: checkPassphrase'));
  });
  withServices({
    getActiveSpreadsheet() {
      throw new Error('no active spreadsheet');
    },
  }, (output) => {
    doPost({ postData: { contents: JSON.stringify({ action: 'checkPassphrase', passphrase: STORED }) } });
    assertError(JSON.parse(output.text), 'internal_error');
  });
});

test('Post.js runs as Apps Script does: one shared scope with the other files, no module object', () => {
  assert.ok(SRC_FILES.includes('Post.js'));
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
  context.body = JSON.stringify({ action: 'checkPassphrase', passphrase: STORED });
  vm.runInContext('doPost({ postData: { contents: body } })', context);
  assert.deepStrictEqual(JSON.parse(output.text), { schemaVersion: 1, ok: true });
  vm.runInContext("doPost({ postData: { contents: '{}' } })", context);
  assertError(JSON.parse(output.text), 'unknown_action');
  vm.runInContext("doGet({ parameter: { resource: 'config' } })", context);
  const get = JSON.parse(output.text);
  assertError(get, 'unknown_resource');
  assert.ok(get.error.message.endsWith('Known: catalog, jams'));
});

test('Post.js holds the line the README tells the user to look for after pasting', () => {
  const source = fs.readFileSync(path.join(SRC_DIR, 'Post.js'), 'utf8');
  assert.ok(source.includes('checkPassphrase: checkPassphrase_,'));
});
