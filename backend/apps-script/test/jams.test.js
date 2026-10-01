'use strict';
// T5: the Jams tab and the published setlists as the jams response, against the committed samples
// in docs/api-samples, and the draft rule: a non-PUBLICADA jam's tab is never read.

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { loadScript } = require('./helpers/load.js');
const { readCsv } = require('./helpers/csv.js');
const { makeFormatter, BUENOS_AIRES } = require('./helpers/format.js');
const { checkJamsResponse, checkCatalogResponse, JAM_KEYS, ROW_KEYS, SLOT_KEYS } = require('./helpers/contract.js');
const { TABS, MARKER, TIME_ZONE, READABLE_TABS, TAB_HEADER } = require('./helpers/jams-edge-input.js');

const { buildJams, buildSetlist, ContractError, SCHEMA_VERSION } = loadScript();

const REPO = path.join(__dirname, '..', '..', '..');
const SEED_DIR = path.join(REPO, 'docs', 'sheet-seed');
const SAMPLES = path.join(REPO, 'docs', 'api-samples');
const SEED_SAMPLE = path.join(SAMPLES, 'jams-seed.json');
const EDGE_SAMPLE = path.join(SAMPLES, 'jams-edge.json');
const CHECKER = path.join(__dirname, '..', 'tools', 'check-response.js');
const JAMS_HEADER = ['fecha', 'hora', 'lugar', 'estado'];
const formatBA = makeFormatter(BUENOS_AIRES);

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, 'utf8'));
}

/** A readTab over a map of { display, raw } tabs that records every date it is asked for. */
function recordingReader(tabs) {
  const calls = [];
  const readTab = (date) => {
    calls.push(date);
    return Object.prototype.hasOwnProperty.call(tabs, date) ? tabs[date] : null;
  };
  return { readTab, calls };
}

/** A tab whose display and raw values are the same strings, as a CSV import would read them. */
function textTab(rows) {
  return { display: rows, raw: rows };
}

function slotRow(position, songId, extra = []) {
  return [position, songId, 'Título', 'Artista', 'A', '', '', '', '', '', '', ''].concat(extra);
}

function response(jamsRows, tabs, formatDate = formatBA) {
  const reader = recordingReader(tabs);
  const body = Object.assign({ schemaVersion: SCHEMA_VERSION }, buildJams(jamsRows.display, jamsRows.raw, reader.readTab, formatDate));
  return { body, calls: reader.calls };
}

function edgeResponse() {
  return response(TABS.Jams, TABS, makeFormatter(TIME_ZONE));
}

function seedResponse() {
  const jams = readCsv(path.join(SEED_DIR, 'Jams.csv'));
  const tab = readCsv(path.join(SEED_DIR, '2026-07-25.csv'));
  return response(textTab(jams), { '2026-07-25': textTab(tab) });
}

function assertContractError(fn, code) {
  assert.throws(fn, (err) => {
    assert.ok(err instanceof ContractError, `expected ContractError, got ${err}`);
    assert.equal(err.code, code);
    return true;
  });
}

test('the seed Jams and 2026-07-25 tabs build exactly docs/api-samples/jams-seed.json', () => {
  assert.deepStrictEqual(seedResponse().body, readJson(SEED_SAMPLE));
});

test('the seed jam is PUBLICADA with 13 rows, every slot and extraParticipants null', () => {
  const { body, calls } = seedResponse();
  assert.equal(body.jams.length, 1);
  const jam = body.jams[0];
  assert.equal(jam.status, 'PUBLICADA');
  assert.equal(jam.setlistError, null);
  assert.equal(jam.setlist.length, 13);
  assert.deepStrictEqual(jam.setlist.map((r) => r.position), ['1', '2', '3', '4', '5', '6', '7', '8', '9', '10', '11', '12', '13']);
  for (const row of jam.setlist) {
    assert.ok(Object.values(row.slots).every((v) => v === null));
    assert.equal(row.extraParticipants, null);
  }
  assert.deepStrictEqual(calls, ['2026-07-25']);
});

test('the edge input builds exactly docs/api-samples/jams-edge.json', () => {
  assert.deepStrictEqual(edgeResponse().body, readJson(EDGE_SAMPLE));
});

test('a non-PUBLICADA jam is withheld: its tab is never requested and its content never serialized', () => {
  const { body, calls } = edgeResponse();
  assert.deepStrictEqual(calls, READABLE_TABS);
  for (const date of ['2026-09-26', '2026-06-27', '2026-03-28', '2026-01-31', 'Config']) {
    assert.ok(!calls.includes(date), `requested ${date}`);
  }
  const text = JSON.stringify(body);
  assert.ok(!text.includes(MARKER), 'the draft marker leaked');
  assert.ok(!text.includes('zz-borrador'), 'the draft marker leaked');
  assert.ok(!text.includes('NO DEBE SALIR'), 'the draft title leaked');
  assert.ok(!text.includes('s3cret'), 'the settings tab leaked');
  for (const jam of body.jams.filter((j) => j.status !== 'PUBLICADA')) {
    assert.equal(jam.setlist, null, jam.date);
    assert.equal(jam.setlistError, null, jam.date);
  }
});

test('a withheld jam still carries its date, time, venue and status', () => {
  const draft = edgeResponse().body.jams.find((j) => j.status === 'BORRADOR');
  assert.deepStrictEqual(draft, { date: '2026-09-26', startTime: '21:00', venue: 'La Macanuda', status: 'BORRADOR', setlist: null, setlistError: null });
});

test('fail closed: any status other than exactly PUBLICADA (after trimming) withholds the setlist', () => {
  for (const status of ['Publicada', 'publicada ', 'PUBLICADO', '', 'BORRADOR', 'PUBLICADA.', 'PUBLI CADA']) {
    const jams = textTab([JAMS_HEADER, ['2026-07-25', '21:00', 'La Macanuda', status]]);
    const { body, calls } = response(jams, { '2026-07-25': textTab([TAB_HEADER, slotRow('1', MARKER)]) });
    assert.deepStrictEqual(calls, [], `requested a tab for status ${JSON.stringify(status)}`);
    assert.equal(body.jams[0].setlist, null);
    assert.equal(body.jams[0].setlistError, null);
    assert.ok(!JSON.stringify(body).includes(MARKER));
  }
  const padded = textTab([JAMS_HEADER, ['2026-07-25', '21:00', 'La Macanuda', ' PUBLICADA ']]);
  const { body } = response(padded, { '2026-07-25': textTab([TAB_HEADER, slotRow('1', 'crossroads')]) });
  assert.equal(body.jams[0].status, 'PUBLICADA');
  assert.equal(body.jams[0].setlist.length, 1);
});

test('typed cells: fecha, hora and posicion come back as YYYY-MM-DD, HH:MM and integer text', () => {
  const jam = edgeResponse().body.jams[0];
  assert.equal(jam.date, '2026-08-29');
  assert.equal(jam.startTime, '21:00');
  assert.deepStrictEqual(jam.setlist.map((r) => r.position), ['2', '1', '3']);
  const last = edgeResponse().body.jams.at(-1);
  assert.equal(last.date, '2026-02-28');
  assert.equal(last.startTime, '21:00');
  assert.equal(edgeResponse().body.jams[2].startTime, '09:30');
});

test('each per-jam error code, and every other jam still served', () => {
  const { body } = edgeResponse();
  const codes = body.jams.map((j) => (j.setlistError ? j.setlistError.code : null));
  assert.deepStrictEqual(codes, [null, null, null, 'missing_tab', 'missing_header', 'duplicate_date', null, 'invalid_date', null]);
  for (const jam of body.jams.filter((j) => j.setlistError !== null)) {
    assert.equal(jam.setlist, null);
    assert.deepStrictEqual(Object.keys(jam.setlistError), ['code', 'message']);
    assert.equal(typeof jam.setlistError.message, 'string');
  }
  assert.match(body.jams[4].setlistError.message, /Teclados/);
  assert.equal(body.jams.filter((j) => Array.isArray(j.setlist)).length, 2);
});

test('a duplicate mapped header in a jam tab is a per-jam duplicate_header', () => {
  const jams = textTab([JAMS_HEADER, ['2026-07-25', '21:00', 'X', 'PUBLICADA'], ['2026-08-29', '21:00', 'X', 'PUBLICADA']]);
  const tabs = {
    '2026-07-25': textTab([TAB_HEADER.concat([' Bajo ']), slotRow('1', 'a', ['', ''])]),
    '2026-08-29': textTab([TAB_HEADER, slotRow('1', 'b', [''])]),
  };
  const { body } = response(jams, tabs);
  assert.equal(body.jams[0].setlistError.code, 'duplicate_header');
  assert.equal(body.jams[1].setlist[0].songId, 'b');
});

test('an empty or header-less jam tab is a per-jam missing_header; a header-only tab is an empty setlist', () => {
  const jams = textTab([JAMS_HEADER, ['2026-07-25', '21:00', 'X', 'PUBLICADA'], ['2026-08-29', '21:00', 'X', 'PUBLICADA']]);
  const { body } = response(jams, { '2026-07-25': textTab([]), '2026-08-29': textTab([TAB_HEADER]) });
  assert.equal(body.jams[0].setlistError.code, 'missing_header');
  assert.deepStrictEqual(body.jams[1].setlist, []);
  assert.equal(body.jams[1].setlistError, null);
});

test('fecha Config or Catalogo with PUBLICADA is invalid_date and never names a tab', () => {
  const jams = textTab([JAMS_HEADER, ['Config', '21:00', 'X', 'PUBLICADA'], ['Catalogo', '21:00', 'X', 'PUBLICADA'], ['', '21:00', 'X', 'PUBLICADA'], ['2026-7-25', '21:00', 'X', 'PUBLICADA']]);
  const { body, calls } = response(jams, { Config: textTab([['clave', 'valor'], ['passphrase', 's3cret']]) });
  assert.deepStrictEqual(calls, []);
  assert.deepStrictEqual(body.jams.map((j) => j.setlistError.code), ['invalid_date', 'invalid_date', 'invalid_date', 'invalid_date']);
  assert.ok(!JSON.stringify(body).includes('s3cret'));
});

test('a date on two rows is duplicate_date for the PUBLICADA one, and its tab is never read', () => {
  const jams = textTab([JAMS_HEADER, ['2026-07-25', '21:00', 'X', 'BORRADOR'], ['2026-07-25', '21:00', 'Y', 'PUBLICADA']]);
  const { body, calls } = response(jams, { '2026-07-25': textTab([TAB_HEADER, slotRow('1', MARKER)]) });
  assert.deepStrictEqual(calls, []);
  assert.equal(body.jams[0].setlistError, null);
  assert.equal(body.jams[1].setlistError.code, 'duplicate_date');
  assert.ok(!JSON.stringify(body).includes(MARKER));
});

test('a missing Otros column gives extraParticipants null; the cell is passed through raw', () => {
  const header = TAB_HEADER.filter((h) => h !== 'Otros');
  assert.equal(buildSetlist([header, slotRow('1', 'a')], [header, slotRow('1', 'a')])[0].extraParticipants, null);
  const withOtros = [TAB_HEADER, slotRow('1', 'a', [' Juan saxo; '])];
  assert.equal(buildSetlist(withOtros, withOtros)[0].extraParticipants, 'Juan saxo;');
});

test('the jam, the row and the slots keep their documented key order', () => {
  const { body } = edgeResponse();
  for (const jam of body.jams) {
    assert.deepStrictEqual(Object.keys(jam), JAM_KEYS);
    for (const row of jam.setlist || []) {
      assert.deepStrictEqual(Object.keys(row), ROW_KEYS);
      assert.deepStrictEqual(Object.keys(row.slots), SLOT_KEYS);
    }
  }
});

test('slots map the seven headers by name, "-" passes through, rows stay in tab order', () => {
  const rows = edgeResponse().body.jams[0].setlist;
  assert.deepStrictEqual(rows[0].slots, { guitar1: 'Pedro', guitar2: '-', bass: null, drums: null, vocals: null, harmonica: null, keyboards: null });
  assert.equal(rows[1].slots.guitar1, 'Ana');
  assert.equal(rows[1].slots.harmonica, '-');
  assert.equal(rows[1].songId, 'the-thrill-is-gone');
});

test('blank Jams rows and blank setlist rows are skipped', () => {
  const jams = textTab([JAMS_HEADER, ['', '', '', ''], ['2026-07-25', '21:00', 'X', 'PUBLICADA'], [' ', '', '', '']]);
  const tab = textTab([TAB_HEADER, new Array(13).fill(''), slotRow('1', 'a', ['']), new Array(13).fill(' ')]);
  const { body } = response(jams, { '2026-07-25': tab });
  assert.equal(body.jams.length, 1);
  assert.equal(body.jams[0].setlist.length, 1);
});

test('the Jams tab itself: a missing header is missing_header, a duplicate is duplicate_header, empty is missing_header', () => {
  const noReads = () => {
    throw new Error('no tab may be read');
  };
  assertContractError(() => buildJams([['fecha', 'hora', 'lugar']], [['fecha', 'hora', 'lugar']], noReads, formatBA), 'missing_header');
  const twice = [JAMS_HEADER.concat([' estado '])];
  assertContractError(() => buildJams(twice, twice, noReads, formatBA), 'duplicate_header');
  assertContractError(() => buildJams([], [], noReads, formatBA), 'missing_header');
  assert.deepStrictEqual(buildJams([JAMS_HEADER], [JAMS_HEADER], noReads, formatBA), { jams: [] });
});

test('an unexpected exception from a tab read is not turned into a per-jam error', () => {
  const jams = textTab([JAMS_HEADER, ['2026-07-25', '21:00', 'X', 'PUBLICADA']]);
  const readTab = () => {
    throw new Error('service unavailable');
  };
  assert.throws(() => buildJams(jams.display, jams.raw, readTab, formatBA), /service unavailable/);
});

test('checkJamsResponse accepts both samples; strict rejects the edge sample only', () => {
  assert.deepStrictEqual(checkJamsResponse(readJson(SEED_SAMPLE)), []);
  assert.deepStrictEqual(checkJamsResponse(readJson(SEED_SAMPLE), { strict: true }), []);
  assert.deepStrictEqual(checkJamsResponse(readJson(EDGE_SAMPLE)), []);
  const strict = checkJamsResponse(readJson(EDGE_SAMPLE), { strict: true });
  assert.equal(strict.length, 5, strict.join('\n'));
  assert.ok(strict.some((v) => /jams\[7\]\.date "Config" is not YYYY-MM-DD/.test(v)));
  assert.deepStrictEqual(checkCatalogResponse(readJson(path.join(SAMPLES, 'catalog-seed.json'))), []);
  assert.deepStrictEqual(checkCatalogResponse(readJson(path.join(SAMPLES, 'catalog-edge.json'))), []);
});

test('checkJamsResponse enforces the draft rule and the shapes', () => {
  const sample = readJson(EDGE_SAMPLE);
  sample.jams[1].setlist = [];
  sample.jams[2].setlistError = { code: 'x', message: 'y' };
  sample.jams[3].setlistError = null;
  sample.jams[0].setlist[0].slots.tuba = null;
  sample.jams[0].setlist[1].position = 1;
  sample.jams[8].venue = '';
  const violations = checkJamsResponse(sample);
  assert.equal(violations.length, 6, violations.join('\n'));
  assert.ok(violations.some((v) => /jams\[1\] has status "BORRADOR" but carries a setlist/.test(v)));
  assert.ok(violations.some((v) => /jams\[2\] has status "Publicada" but carries a setlistError/.test(v)));
  assert.ok(violations.some((v) => /jams\[3\] is PUBLICADA with neither/.test(v)));
  assert.ok(violations.some((v) => /slots keys: unexpected tuba/.test(v)));
  assert.ok(violations.some((v) => /setlist\[1\]\.position is number/.test(v)));
  assert.ok(violations.some((v) => /jams\[8\]\.venue is an empty string/.test(v)));
  const error = { schemaVersion: 1, error: { code: 'missing_tab', message: 'x' } };
  assert.ok(checkJamsResponse(error).some((v) => /is an error: missing_tab/.test(v)));
});

test('check-response.js dispatches on jams or songs and honors --strict', () => {
  const run = (...args) => spawnSync(process.execPath, [CHECKER, ...args], { encoding: 'utf8' });
  const seed = run('--strict', SEED_SAMPLE);
  assert.equal(seed.status, 0, seed.stdout);
  assert.match(seed.stdout, /OK \(strict\): schemaVersion 1, 1 jams, 1 published with setlist, 0 withheld, 0 with errors, 13 setlist rows/);
  assert.equal(run(EDGE_SAMPLE).status, 0);
  assert.equal(run('--strict', EDGE_SAMPLE).status, 1);
  assert.equal(run(path.join(SAMPLES, 'catalog-seed.json')).status, 0);
  assert.equal(run().status, 2);
});
