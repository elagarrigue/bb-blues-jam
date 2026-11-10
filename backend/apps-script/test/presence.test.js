'use strict';
// admin-presence-notice (D-32): announcePresence is advisory, cache-only, lock-free and guarded by the router.

const test = require('node:test');
const assert = require('node:assert/strict');
const { loadScript } = require('./helpers/load.js');

const { handlePost, ACTIONS } = loadScript();
const CONFIG_TAB = 'Config';

const STORED = 'Blues-Pass 42';
const NOW = Date.UTC(2026, 10, 9, 20, 0, 0);
const A = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
const B = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
const KEY = 'presence_2026-11-21_hoochie-coochie-man';

function sheetOf(rows) {
  return { getDataRange: () => ({ getDisplayValues: () => rows.map((row) => row.map(String)) }) };
}

function spreadsheet() {
  const tabs = { [CONFIG_TAB]: sheetOf([['clave', 'valor'], ['passphrase', STORED]]) };
  return { getSheetByName: (name) => tabs[name] || null };
}

function cache() {
  const store = new Map();
  const log = [];
  return {
    store,
    log,
    get(key) {
      log.push(['get', key]);
      return store.has(key) ? store.get(key) : null;
    },
    put(key, value, ttl) {
      log.push(['put', key, value, ttl]);
      store.set(key, value);
    },
    remove(key) {
      log.push(['remove', key]);
      store.delete(key);
    },
  };
}

function lock() {
  const l = { tries: [], tryLock(ms) { l.tries.push(ms); return true; }, releaseLock() {} };
  return l;
}

function announce(fields, { c = cache(), now = NOW, l = lock(), passphrase = STORED } = {}) {
  const request = Object.assign({ action: 'announcePresence', passphrase, date: '2026-11-21', songId: 'hoochie-coochie-man', holder: A }, fields);
  return handlePost(request, spreadsheet(), { cache: c, lock: l, now });
}

function code(body) {
  return body.error && body.error.code;
}

test('announcePresence is a guarded action that takes no lock', () => {
  assert.equal(ACTIONS.announcePresence.write, false);
  const l = lock();
  const c = cache();
  assert.equal(code(announce({}, { c, l, passphrase: 'wrong' })), 'invalid_passphrase');
  assert.equal(code(announce({}, { c, l, passphrase: null })), 'invalid_passphrase');
  assert.ok(!c.log.some((entry) => entry[1] === KEY), 'the action never touched the presence key');
  assert.deepStrictEqual(announce({}, { c, l }), { schemaVersion: 1, ok: true, others: 0 });
  assert.deepStrictEqual(l.tries, []);
});

test('validation happens in order before any cache call', () => {
  const c = cache();
  assert.equal(code(announce({ date: '2026-02-30' }, { c })), 'invalid_date');
  assert.equal(code(announce({ date: 5 }, { c })), 'invalid_date');
  assert.equal(code(announce({ songId: 'Bad Id' }, { c })), 'invalid_song');
  assert.equal(code(announce({ holder: 'short' }, { c })), 'invalid_holder');
  assert.equal(code(announce({ holder: 'x'.repeat(65) }, { c })), 'invalid_holder');
  assert.equal(code(announce({ holder: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaa!' }, { c })), 'invalid_holder');
  assert.equal(code(announce({ date: 'nope', songId: 'Bad', holder: 'x' }, { c })), 'invalid_date');
  assert.deepStrictEqual(c.log.filter((entry) => entry[1] === KEY), []);
});

test('first announce is alone, a second holder sees one other, the same holder never counts itself', () => {
  const c = cache();
  assert.equal(announce({}, { c }).others, 0);
  assert.deepStrictEqual(JSON.parse(c.store.get(KEY)), { [A]: NOW + 120000 });
  assert.deepStrictEqual(c.log.find((entry) => entry[0] === 'put').slice(3), [120]);
  assert.equal(announce({ holder: B }, { c, now: NOW + 1000 }).others, 1);
  assert.equal(announce({}, { c, now: NOW + 2000 }).others, 1);
  const single = cache();
  announce({}, { c: single });
  assert.equal(announce({}, { c: single, now: NOW + 30000 }).others, 0);
});

test('an expired entry is not counted and is dropped on write', () => {
  const c = cache();
  c.store.set(KEY, JSON.stringify({ [B]: NOW }));
  assert.equal(announce({}, { c }).others, 0);
  assert.deepStrictEqual(Object.keys(JSON.parse(c.store.get(KEY))), [A]);
  c.store.set(KEY, JSON.stringify({ [B]: NOW + 1 }));
  assert.equal(announce({}, { c }).others, 1);
});

test('leaving removes the holder, removes the key when empty, and still answers the others', () => {
  const c = cache();
  announce({}, { c });
  announce({ holder: B }, { c });
  assert.equal(announce({ holder: B, leaving: true }, { c }).others, 1);
  assert.deepStrictEqual(Object.keys(JSON.parse(c.store.get(KEY))), [A]);
  assert.equal(announce({ leaving: true }, { c }).others, 0);
  assert.equal(c.store.has(KEY), false);
  assert.ok(c.log.some((entry) => entry[0] === 'remove' && entry[1] === KEY));
  const d = cache();
  announce({}, { c: d });
  announce({ leaving: 'true' }, { c: d });
  assert.ok(d.store.has(KEY), 'only the boolean true releases');
});

test('garbage in the cache reads as empty and more than ten holders keeps the ten latest', () => {
  for (const garbage of ['not json', '[1,2]', '"x"', 'null', '{"a":"b"}']) {
    const c = cache();
    c.store.set(KEY, garbage);
    assert.equal(announce({}, { c }).others, 0);
  }
  const c = cache();
  for (let i = 0; i < 12; i++) {
    announce({ holder: 'holder-number-' + String(i).padStart(4, '0') }, { c, now: NOW + i });
  }
  const kept = Object.keys(JSON.parse(c.store.get(KEY)));
  assert.equal(kept.length, 10);
  assert.ok(!kept.includes('holder-number-0000') && !kept.includes('holder-number-0001'));
  assert.ok(kept.includes('holder-number-0011'));
});

test('a cache that throws is internal_error', () => {
  const c = cache();
  c.get = () => { throw new Error('boom'); };
  assert.equal(code(announce({}, { c })), 'internal_error');
});
