'use strict';
// admin-passphrase-login: the POST router, the checkPassphrase action, and the guarantees that the
// passphrase is never returned, an unset passphrase rejects everything, and only Config is opened.
// apps-script-write-auth: the guard every action passes in the router, the rate limit (W2), the
// write lock and the checkWriteAccess deploy check.

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { loadScript, SRC_DIR, SRC_FILES } = require('./helpers/load.js');

const { handlePost, doPost, ACTIONS, WRITE_CHECK_TAB, requirePassphrase_, handleGet } = loadScript();

const STORED = 'Blues-Pass 42';
const CONFIG_TAB = 'Config';
const WINDOW_MS = 10 * 60 * 1000;
const NOW = Date.UTC(2026, 9, 5, 12, 3, 0);
const DEFAULT_CONFIG = [['clave', 'valor'], ['passphrase', STORED]];

function fakeSheet(rows) {
  return {
    getDataRange() {
      return { getDisplayValues: () => rows.map((row) => row.map(String)) };
    },
  };
}

/**
 * A spreadsheet with an optional Config tab; every property read, tab request and tab operation is
 * recorded. `readBack` replaces what A1 of an inserted tab reads back, to simulate a mismatch.
 */
function fakeSpreadsheet({ config = DEFAULT_CONFIG, throwOnRead = false, leftover = false, readBack } = {}) {
  const tabs = { Catalogo: fakeSheet([['id']]), Jams: fakeSheet([['fecha']]) };
  if (config) {
    tabs[CONFIG_TAB] = fakeSheet(config);
  }
  const ops = [];
  function writableSheet(name) {
    let a1 = '';
    return {
      name,
      getRange(a1Notation) {
        ops.push(['getRange', name, a1Notation]);
        return {
          setValue(value) {
            ops.push(['setValue', name, value]);
            a1 = String(value);
          },
          getDisplayValue() {
            ops.push(['getDisplayValue', name]);
            return readBack === undefined ? a1 : readBack;
          },
        };
      },
    };
  }
  if (leftover) {
    tabs[WRITE_CHECK_TAB] = writableSheet(WRITE_CHECK_TAB);
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
    insertSheet(name) {
      ops.push(['insertSheet', name]);
      assert.ok(!(name in tabs), `insertSheet: ${name} already exists`);
      tabs[name] = writableSheet(name);
      return tabs[name];
    },
    deleteSheet(sheet) {
      const name = Object.keys(tabs).find((key) => tabs[key] === sheet);
      ops.push(['deleteSheet', name]);
      delete tabs[name];
    },
  };
  const spreadsheet = new Proxy(target, {
    get(obj, prop) {
      accessed.push(String(prop));
      return obj[prop];
    },
  });
  return { spreadsheet, accessed, requested, ops, tabs };
}

/** A script cache over a Map; every call is logged. */
function fakeCache({ throwOnGet = false, throwOnPut = false } = {}) {
  const store = new Map();
  const log = [];
  return {
    store,
    log,
    get(key) {
      log.push(['get', key]);
      if (throwOnGet) {
        throw new Error('cache get failed');
      }
      return store.has(key) ? store.get(key) : null;
    },
    put(key, value, ttl) {
      log.push(['put', key, value, ttl]);
      if (throwOnPut) {
        throw new Error('cache put failed');
      }
      store.set(key, value);
    },
  };
}

/** The script lock: `acquire` decides what tryLock answers. */
function fakeLock({ acquire = true } = {}) {
  const lock = {
    tries: [],
    releases: 0,
    held: false,
    tryLock(timeoutMs) {
      lock.tries.push(timeoutMs);
      lock.held = acquire;
      return acquire;
    },
    releaseLock() {
      lock.releases += 1;
      lock.held = false;
    },
  };
  return lock;
}

function fakeServices({ cache = fakeCache(), lock = fakeLock(), now = NOW } = {}) {
  return { cache, lock, now };
}

/**
 * Runs `body` with every ACTIONS entry's `run` replaced by a spy that records its arguments and
 * whether the lock was held, then restores the real functions.
 */
function withSpies(body) {
  const originals = {};
  const calls = {};
  for (const name of Object.keys(ACTIONS)) {
    originals[name] = ACTIONS[name].run;
    calls[name] = [];
    ACTIONS[name].run = function (request, spreadsheet, services) {
      calls[name].push({ request, spreadsheet, services, lockHeld: services && services.lock ? services.lock.held : undefined });
      return { ok: true };
    };
  }
  try {
    body(calls);
  } finally {
    for (const name of Object.keys(originals)) {
      ACTIONS[name].run = originals[name];
    }
  }
}

function post(action, passphrase, options = {}, services = fakeServices()) {
  const request = { action };
  if (passphrase !== undefined) {
    request.passphrase = passphrase;
  }
  return handlePost(request, fakeSpreadsheet(options).spreadsheet, services);
}

function check(passphrase, options) {
  return post('checkPassphrase', passphrase, options);
}

function assertError(body, code) {
  assert.deepStrictEqual(Object.keys(body), ['schemaVersion', 'error']);
  assert.equal(body.schemaVersion, 1);
  assert.deepStrictEqual(Object.keys(body.error), ['code', 'message']);
  assert.equal(body.error.code, code);
  assert.equal(typeof body.error.message, 'string');
}

const ACTION_NAMES = Object.keys(ACTIONS);
const NOT_STRINGS = [undefined, null, 42, true, [STORED], { value: STORED }];
const WRONG = ['definitely-wrong', STORED.toLowerCase(), STORED.toUpperCase(), ` ${STORED}`, `${STORED} `, ''];

test('the action table is checkPassphrase (read) and checkWriteAccess (write), each with a run function', () => {
  assert.deepStrictEqual(ACTION_NAMES, ['checkPassphrase', 'checkWriteAccess']);
  assert.equal(ACTIONS.checkPassphrase.write, false);
  assert.equal(ACTIONS.checkWriteAccess.write, true);
  for (const name of ACTION_NAMES) {
    assert.deepStrictEqual(Object.keys(ACTIONS[name]).sort(), ['run', 'write']);
    assert.equal(typeof ACTIONS[name].run, 'function');
    assert.equal(typeof ACTIONS[name].write, 'boolean');
  }
});

test('the guard is called by the router only: no action calls it itself', () => {
  const source = fs.readFileSync(path.join(SRC_DIR, 'Post.js'), 'utf8');
  const calls = source.match(/requirePassphrase_\(/g) || [];
  assert.equal(calls.length, 2, 'one definition and one call, in handlePost');
  const handlePostBody = source.slice(source.indexOf('function handlePost('), source.indexOf('function requirePassphrase_('));
  assert.ok(handlePostBody.includes('requirePassphrase_(request, spreadsheet, services);'));
});

test('every action: a missing or non-string passphrase is invalid_passphrase; the action never runs, no lock, only Config read', () => {
  withSpies((calls) => {
    for (const action of ACTION_NAMES) {
      for (const passphrase of NOT_STRINGS) {
        const fake = fakeSpreadsheet();
        const services = fakeServices();
        const request = { action };
        if (passphrase !== undefined) {
          request.passphrase = passphrase;
        }
        assertError(handlePost(request, fake.spreadsheet, services), 'invalid_passphrase');
        assert.deepStrictEqual(fake.requested, [CONFIG_TAB]);
        assert.deepStrictEqual(fake.accessed, ['getSheetByName']);
        assert.deepStrictEqual(services.lock.tries, []);
        assert.equal(services.lock.releases, 0);
      }
      assert.equal(calls[action].length, 0, `${action} ran`);
    }
  });
});

test('every action: a wrong passphrase is invalid_passphrase and the action never runs', () => {
  withSpies((calls) => {
    for (const action of ACTION_NAMES) {
      for (const passphrase of WRONG) {
        const services = fakeServices();
        assertError(post(action, passphrase, {}, services), 'invalid_passphrase');
        assert.deepStrictEqual(services.lock.tries, []);
      }
      assert.equal(calls[action].length, 0, `${action} ran`);
    }
  });
});

test('every action: the right passphrase runs it once; a write action runs holding the lock, which is released', () => {
  withSpies((calls) => {
    for (const action of ACTION_NAMES) {
      const fake = fakeSpreadsheet();
      const services = fakeServices();
      const request = { action, passphrase: STORED };
      assert.deepStrictEqual(handlePost(request, fake.spreadsheet, services), { schemaVersion: 1, ok: true });
      assert.equal(calls[action].length, 1);
      const call = calls[action][0];
      assert.equal(call.request, request);
      assert.equal(call.spreadsheet, fake.spreadsheet);
      assert.equal(call.services, services);
      if (ACTIONS[action].write) {
        assert.equal(call.lockHeld, true, `${action} ran without the lock`);
        assert.deepStrictEqual(services.lock.tries, [10000]);
        assert.equal(services.lock.releases, 1);
      } else {
        assert.equal(call.lockHeld, false);
        assert.deepStrictEqual(services.lock.tries, []);
      }
    }
  });
});

test('every action: an unset passphrase is passphrase_not_set, the action never runs and no failure is counted', () => {
  const configs = [false, [], [['clave', 'valor']], [['clave', 'valor'], ['passphrase', '   ']]];
  withSpies((calls) => {
    for (const action of ACTION_NAMES) {
      for (const config of configs) {
        for (const passphrase of ['', STORED, undefined, 'definitely-wrong']) {
          const services = fakeServices();
          assertError(post(action, passphrase, { config }, services), 'passphrase_not_set');
          assert.ok(services.cache.log.every((entry) => entry[0] === 'get'), JSON.stringify(services.cache.log));
          assert.deepStrictEqual(services.lock.tries, []);
        }
      }
      assert.equal(calls[action].length, 0);
    }
  });
});

test('rotation: Config is read on every request, so the old passphrase is rejected as soon as it changes', () => {
  withSpies((calls) => {
    for (const action of ACTION_NAMES) {
      const services = fakeServices();
      const old = fakeSpreadsheet({ config: [['clave', 'valor'], ['passphrase', 'old-pass']] });
      assert.deepStrictEqual(handlePost({ action, passphrase: 'old-pass' }, old.spreadsheet, services), { schemaVersion: 1, ok: true });
      assert.deepStrictEqual(handlePost({ action, passphrase: 'old-pass' }, old.spreadsheet, services), { schemaVersion: 1, ok: true });
      assert.deepStrictEqual(old.requested, [CONFIG_TAB, CONFIG_TAB], 'Config read once per request');

      const rotated = fakeSpreadsheet({ config: [['clave', 'valor'], ['passphrase', 'new-pass']] });
      assertError(handlePost({ action, passphrase: 'old-pass' }, rotated.spreadsheet, services), 'invalid_passphrase');
      assert.deepStrictEqual(handlePost({ action, passphrase: 'new-pass' }, rotated.spreadsheet, services), { schemaVersion: 1, ok: true });
      assert.equal(calls[action].length, 3);
    }
  });
});

test('rate limit: the 10th failure in a window locks every action, the right passphrase included, without reading Config', () => {
  withSpies((calls) => {
    const cache = fakeCache();
    for (let i = 0; i < 9; i++) {
      assertError(post(ACTION_NAMES[i % ACTION_NAMES.length], 'definitely-wrong', {}, fakeServices({ cache })), 'invalid_passphrase');
    }
    // Nine failures: the right passphrase still works, and does not reset the count.
    assert.deepStrictEqual(post('checkPassphrase', STORED, {}, fakeServices({ cache })), { schemaVersion: 1, ok: true });
    assertError(post('checkPassphrase', 'definitely-wrong', {}, fakeServices({ cache })), 'invalid_passphrase');

    for (const action of ACTION_NAMES) {
      for (const passphrase of [STORED, 'definitely-wrong', undefined]) {
        const fake = fakeSpreadsheet();
        const services = fakeServices({ cache });
        const request = { action, passphrase };
        assertError(handlePost(request, fake.spreadsheet, services), 'rate_limited');
        assert.deepStrictEqual(fake.requested, [], 'Config was read while rate limited');
        assert.deepStrictEqual(services.lock.tries, []);
      }
    }
    assert.equal(calls.checkPassphrase.length, 1);
    assert.equal(calls.checkWriteAccess.length, 0);

    const window = Math.floor(NOW / WINDOW_MS);
    assert.deepStrictEqual([...cache.store.entries()], [[`auth_failures_${window}`, '10']]);
    const puts = cache.log.filter((entry) => entry[0] === 'put');
    assert.equal(puts.length, 10);
    assert.ok(puts.every((entry) => entry[3] === 1200), 'TTL 1200 s');

    // The last millisecond of the window is still locked; the next window accepts again.
    const lastMs = (window + 1) * WINDOW_MS - 1;
    assertError(post('checkPassphrase', STORED, {}, fakeServices({ cache, now: lastMs })), 'rate_limited');
    assert.deepStrictEqual(post('checkPassphrase', STORED, {}, fakeServices({ cache, now: lastMs + 1 })), { schemaVersion: 1, ok: true });
    assert.deepStrictEqual(post('checkWriteAccess', STORED, {}, fakeServices({ cache, now: lastMs + 1 })), { schemaVersion: 1, ok: true });
  });
});

test('rate limit fails open: a cache that throws or is missing is ignored, and the passphrase check still runs', () => {
  withSpies((calls) => {
    const caches = [fakeCache({ throwOnGet: true }), fakeCache({ throwOnPut: true }), fakeCache({ throwOnGet: true, throwOnPut: true }), null, undefined];
    for (const cache of caches) {
      for (let i = 0; i < 12; i++) {
        assertError(post('checkWriteAccess', 'definitely-wrong', {}, fakeServices({ cache })), 'invalid_passphrase');
        assertError(post('checkWriteAccess', undefined, {}, fakeServices({ cache })), 'invalid_passphrase');
      }
      assert.deepStrictEqual(post('checkWriteAccess', STORED, {}, fakeServices({ cache })), { schemaVersion: 1, ok: true });
      assertError(post('checkPassphrase', STORED, { config: false }, fakeServices({ cache })), 'passphrase_not_set');
    }
    assert.equal(calls.checkWriteAccess.length, caches.length);
  });
});

test('a non-numeric count in the cache reads as zero', () => {
  const cache = fakeCache();
  cache.store.set(`auth_failures_${Math.floor(NOW / WINDOW_MS)}`, 'garbage');
  assert.deepStrictEqual(post('checkPassphrase', STORED, {}, fakeServices({ cache })), { schemaVersion: 1, ok: true });
});

test('the guard itself throws the contract errors (exported for the tests)', () => {
  const fake = fakeSpreadsheet();
  assert.throws(() => requirePassphrase_({ passphrase: 'definitely-wrong' }, fake.spreadsheet, fakeServices()), (err) => err.code === 'invalid_passphrase');
  assert.equal(requirePassphrase_({ passphrase: STORED }, fake.spreadsheet, fakeServices()), undefined);
});

test('busy: a write that cannot take the lock in 10 s is busy, never runs and releases nothing', () => {
  withSpies((calls) => {
    const services = fakeServices({ lock: fakeLock({ acquire: false }) });
    assertError(post('checkWriteAccess', STORED, {}, services), 'busy');
    assert.deepStrictEqual(services.lock.tries, [10000]);
    assert.equal(services.lock.releases, 0);
    assert.equal(calls.checkWriteAccess.length, 0);
    // A read action never takes the lock, so it is never busy.
    assert.deepStrictEqual(post('checkPassphrase', STORED, {}, services), { schemaVersion: 1, ok: true });
  });
});

test('a write that throws still releases the lock and answers internal_error', () => {
  const original = ACTIONS.checkWriteAccess.run;
  ACTIONS.checkWriteAccess.run = () => {
    throw new Error('sheet exploded');
  };
  try {
    const services = fakeServices();
    const body = post('checkWriteAccess', STORED, {}, services);
    assertError(body, 'internal_error');
    assert.equal(body.error.message, 'sheet exploded');
    assert.equal(services.lock.releases, 1);
  } finally {
    ACTIONS.checkWriteAccess.run = original;
  }
});

test('checkWriteAccess creates the check tab, writes and reads back A1, deletes the tab and touches nothing else', () => {
  const fake = fakeSpreadsheet();
  const before = Object.keys(fake.tabs).sort();
  const body = handlePost({ action: 'checkWriteAccess', passphrase: STORED }, fake.spreadsheet, fakeServices());
  assert.deepStrictEqual(body, { schemaVersion: 1, ok: true });
  assert.equal(WRITE_CHECK_TAB, '_prueba_escritura');
  const marker = `write check ${new Date(NOW).toISOString()}`;
  assert.deepStrictEqual(fake.ops, [
    ['insertSheet', WRITE_CHECK_TAB],
    ['getRange', WRITE_CHECK_TAB, 'A1'],
    ['setValue', WRITE_CHECK_TAB, marker],
    ['getDisplayValue', WRITE_CHECK_TAB],
    ['deleteSheet', WRITE_CHECK_TAB],
  ]);
  assert.deepStrictEqual(Object.keys(fake.tabs).sort(), before, 'a tab was left behind or removed');
  assert.deepStrictEqual(fake.requested, [CONFIG_TAB, WRITE_CHECK_TAB]);
});

test('checkWriteAccess deletes a leftover check tab first', () => {
  const fake = fakeSpreadsheet({ leftover: true });
  const body = handlePost({ action: 'checkWriteAccess', passphrase: STORED }, fake.spreadsheet, fakeServices());
  assert.deepStrictEqual(body, { schemaVersion: 1, ok: true });
  assert.deepStrictEqual(fake.ops[0], ['deleteSheet', WRITE_CHECK_TAB]);
  assert.deepStrictEqual(fake.ops[1], ['insertSheet', WRITE_CHECK_TAB]);
  assert.ok(!(WRITE_CHECK_TAB in fake.tabs));
});

test('checkWriteAccess with a read-back mismatch is internal_error and still deletes the tab', () => {
  const fake = fakeSpreadsheet({ readBack: 'something else' });
  const services = fakeServices();
  const body = handlePost({ action: 'checkWriteAccess', passphrase: STORED }, fake.spreadsheet, services);
  assertError(body, 'internal_error');
  assert.ok(!(WRITE_CHECK_TAB in fake.tabs), 'the check tab was left behind');
  assert.deepStrictEqual(fake.ops.at(-1), ['deleteSheet', WRITE_CHECK_TAB]);
  assert.equal(services.lock.releases, 1);
});

test('the stored value is trimmed and found among other rows and extra columns', () => {
  const config = [['nota', 'clave', 'valor'], ['x', 'otra', 'y'], ['', '  passphrase ', `  ${STORED}  `]];
  assert.deepStrictEqual(check(STORED, { config }), { schemaVersion: 1, ok: true });
});

test('checkPassphrase: the right passphrase is ok and reads only Config', () => {
  const fake = fakeSpreadsheet();
  const body = handlePost({ action: 'checkPassphrase', passphrase: STORED }, fake.spreadsheet, fakeServices());
  assert.deepStrictEqual(body, { schemaVersion: 1, ok: true });
  assert.deepStrictEqual(fake.requested, [CONFIG_TAB]);
  assert.deepStrictEqual(fake.accessed, ['getSheetByName']);
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
    const services = fakeServices();
    assertError(handlePost(request, fake.spreadsheet, services), 'invalid_request');
    assert.deepStrictEqual(fake.accessed, []);
    assert.deepStrictEqual(services.cache.log, []);
  }
});

test('a missing or unknown action is unknown_action, lists the known actions and touches nothing', () => {
  for (const request of [{}, { action: '' }, { action: 'CHECKPASSPHRASE' }, { action: 'catalog' }, { action: 'toString' }, { action: '__proto__' }, { action: 42 }, { passphrase: STORED }]) {
    const fake = fakeSpreadsheet();
    const services = fakeServices();
    const body = handlePost(request, fake.spreadsheet, services);
    assertError(body, 'unknown_action');
    assert.ok(body.error.message.endsWith('Known: checkPassphrase, checkWriteAccess'), body.error.message);
    assert.deepStrictEqual(fake.accessed, []);
    assert.deepStrictEqual(services.cache.log, []);
  }
});

test('an unexpected exception is internal_error', () => {
  const body = check(STORED, { throwOnRead: true });
  assertError(body, 'internal_error');
  assert.equal(body.error.message, 'boom: service unavailable');
});

test('no response, message, cache key or cache value contains the stored or the submitted passphrase', () => {
  const submitted = ['definitely-wrong', STORED, ` ${STORED}`, 'zz-submitted-marker'];
  const configs = [undefined, false, [['clave', 'valor'], ['passphrase', '']], [['clave'], ['passphrase']]];
  for (const action of ACTION_NAMES) {
    for (const config of configs) {
      for (const passphrase of submitted) {
        for (const options of [{ config }, { config, throwOnRead: true }, { config, readBack: 'x' }]) {
          const fake = fakeSpreadsheet(options);
          const services = fakeServices();
          const body = handlePost({ action, passphrase }, fake.spreadsheet, services);
          for (const text of [JSON.stringify(body), JSON.stringify(services.cache.log)]) {
            assert.ok(!text.includes(STORED), `stored passphrase leaked: ${text}`);
            assert.ok(!text.includes('zz-submitted-marker') && !text.includes('definitely-wrong'), `submitted passphrase leaked: ${text}`);
          }
          assert.ok(fake.requested.every((name) => name === CONFIG_TAB || name === WRITE_CHECK_TAB), `requested ${fake.requested}`);
          for (const [key, value] of services.cache.store) {
            assert.match(key, /^auth_failures_\d+$/);
            assert.match(value, /^\d+$/);
          }
        }
      }
    }
  }
});

test('the GET routes still never open Config', () => {
  for (const resource of ['catalog', 'jams', 'config', CONFIG_TAB, 'checkPassphrase', 'checkWriteAccess']) {
    const fake = fakeSpreadsheet();
    handleGet({ resource }, { getSheetByName: fake.spreadsheet.getSheetByName, getSpreadsheetTimeZone: () => 'America/Argentina/Buenos_Aires' });
    assert.ok(!fake.requested.includes(CONFIG_TAB), `${resource} requested ${fake.requested}`);
  }
});

/** Installs fake Apps Script services on globalThis for doPost, and removes them afterwards. */
function withAppsScript(spreadsheetApp, run, { cache = fakeCache(), lock = fakeLock() } = {}) {
  const output = {};
  globalThis.SpreadsheetApp = spreadsheetApp;
  globalThis.CacheService = { getScriptCache: () => cache };
  globalThis.LockService = { getScriptLock: () => lock };
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
    run(output, { cache, lock });
  } finally {
    delete globalThis.SpreadsheetApp;
    delete globalThis.CacheService;
    delete globalThis.LockService;
    delete globalThis.ContentService;
  }
}

test('doPost parses the JSON body and serializes handlePost with the active spreadsheet, script cache and script lock', () => {
  const fake = fakeSpreadsheet();
  withAppsScript({ getActiveSpreadsheet: () => fake.spreadsheet }, (output, { cache, lock }) => {
    const result = doPost({ postData: { contents: JSON.stringify({ action: 'checkPassphrase', passphrase: STORED }) } });
    assert.equal(result, output);
    assert.equal(output.mime, 'application/json');
    assert.deepStrictEqual(JSON.parse(output.text), { schemaVersion: 1, ok: true });

    doPost({ postData: { contents: JSON.stringify({ action: 'checkWriteAccess', passphrase: STORED }) } });
    assert.deepStrictEqual(JSON.parse(output.text), { schemaVersion: 1, ok: true });
    assert.deepStrictEqual(lock.tries, [10000]);
    assert.equal(lock.releases, 1);
    assert.ok(!(WRITE_CHECK_TAB in fake.tabs));

    doPost({ postData: { contents: JSON.stringify({ action: 'checkPassphrase', passphrase: 'definitely-wrong' }) } });
    assertError(JSON.parse(output.text), 'invalid_passphrase');
    const before = Date.now();
    const window = Math.floor(before / WINDOW_MS);
    const keys = [...cache.store.keys()];
    assert.equal(keys.length, 1);
    assert.ok([`auth_failures_${window}`, `auth_failures_${window - 1}`].includes(keys[0]), `doPost used the current time: ${keys[0]}`);

    for (const e of [undefined, {}, { postData: {} }, { postData: { contents: 'not json {' } }, { postData: { contents: '' } }, { postData: { contents: '[1]' } }]) {
      doPost(e);
      assertError(JSON.parse(output.text), 'invalid_request');
    }

    doPost({ postData: { contents: '{}' } });
    const unknown = JSON.parse(output.text);
    assertError(unknown, 'unknown_action');
    assert.ok(unknown.error.message.endsWith('Known: checkPassphrase, checkWriteAccess'));
  });
  withAppsScript({
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
  const lock = fakeLock();
  const context = vm.createContext({
    SpreadsheetApp: { getActiveSpreadsheet: () => fake.spreadsheet },
    CacheService: { getScriptCache: () => fakeCache() },
    LockService: { getScriptLock: () => lock },
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
  context.body = JSON.stringify({ action: 'checkWriteAccess', passphrase: STORED });
  vm.runInContext('doPost({ postData: { contents: body } })', context);
  assert.deepStrictEqual(JSON.parse(output.text), { schemaVersion: 1, ok: true });
  assert.equal(lock.releases, 1);
  vm.runInContext("doPost({ postData: { contents: '{}' } })", context);
  assertError(JSON.parse(output.text), 'unknown_action');
  vm.runInContext("doGet({ parameter: { resource: 'config' } })", context);
  const get = JSON.parse(output.text);
  assertError(get, 'unknown_resource');
  assert.ok(get.error.message.endsWith('Known: catalog, jams'));
});

test('Post.js holds the lines the README tells the user to look for after pasting', () => {
  const source = fs.readFileSync(path.join(SRC_DIR, 'Post.js'), 'utf8');
  assert.ok(source.includes('checkWriteAccess: { write: true, run: checkWriteAccess_ },'));
  assert.ok(!source.includes('checkPassphrase: checkPassphrase_,'), 'the old marker must be gone, so an old paste is detectable');
});
