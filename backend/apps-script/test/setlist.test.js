'use strict';
// admin-add-song-to-setlist: the admin read `readJams`, the write `addSong` and the deploy check
// `checkSetlistWrite`, through handlePost (so behind the guard and, for writes, the lock), against a
// fake spreadsheet that holds real cell grids and logs every write.
// admin-remove-song-from-setlist: the write `removeSong` and the deploy check `checkSetlistRemove`,
// against the same fake, which also deletes and inserts rows.
// admin-set-key: the write `setKey` and the extended `checkSetlistWrite`, against the same fake.
// admin-assign-musician: the write `assignSlot` and its temporary-tab deploy proof.

const test = require('node:test');
const assert = require('node:assert/strict');
const { loadScript } = require('./helpers/load.js');
const { Utilities, BUENOS_AIRES } = require('./helpers/format.js');

const { handlePost, handleGet, ACTIONS, PUBLISH_CHECK_TAB, SETLIST_CHECK_TAB, buildSetlist } = loadScript();

// The actions read today's date and typed cells with Apps Script's Utilities global.
globalThis.Utilities = Utilities;

const STORED = 'Blues-Pass 42';
// 6 October 2026, 12:00 in Buenos Aires.
const NOW = Date.UTC(2026, 9, 6, 15, 0, 0);
const UPCOMING = '2026-10-31';
const LATER = '2026-11-28';
const PAST = '2026-07-25';
const PAST_DRAFT = '2026-09-26';
const SLOT_HEADERS = ['Guitarra 1', 'Guitarra 2', 'Bajo', 'Batería', 'Voz', 'Armónica', 'Teclados'];
const TAB_HEADER = ['posicion', 'id_tema', 'titulo', 'artista', 'tono'].concat(SLOT_HEADERS, ['Otros']);
const DRAFT_MARKER = 'zz-borrador-secreto';
const WRITE_OPS = ['insertSheet', 'deleteSheet', 'setValues', 'setValue', 'setNumberFormat', 'deleteRow', 'insertRowAfter'];

const JAMS = [
  ['fecha', 'hora', 'lugar', 'estado'],
  [PAST, '21:00', 'La Macanuda', 'PUBLICADA'],
  [PAST_DRAFT, '21:00', 'La Macanuda', 'BORRADOR'],
  [UPCOMING, '21:00', 'La Macanuda', 'BORRADOR'],
  [LATER, '21:00', 'La Macanuda', 'BORRADOR'],
];
const CATALOG = [
  ['id', 'titulo', 'artista', 'tono_default', 'tempo'],
  ['crossroads', 'Crossroads', 'Eric Clapton', 'A', 'rapido'],
  ['the-thrill-is-gone', 'The Thrill Is Gone', 'B.B. King', 'Bm', ''],
  ['  siete-cuartos ', '7/4', 'Prueba', 'C', ''],
  ['sin-artista', 'Sin artista', '  ', 'A', ''],
  ['repetido', 'Uno', 'X', 'A', ''],
  ['repetido', 'Dos', 'Y', 'A', ''],
];

function row(position, songId, title, artist, key, slots = ['', '', '', '', '', '', ''], extra = '') {
  return [position, songId, title, artist, key].concat(slots, [extra]);
}

const UPCOMING_TAB = [
  TAB_HEADER,
  row('1', 'the-thrill-is-gone', 'The Thrill Is Gone', 'B.B. King', 'Bm', ['Ana', '', '', '', '', '', '']),
  row('x', 'sweet-home-chicago', 'Sweet Home Chicago', 'Robert Johnson', 'E'),
  row('3', 'red-house', 'Red House', 'Jimi Hendrix', 'Bb'),
];
const PAST_DRAFT_TAB = [TAB_HEADER, row('1', DRAFT_MARKER, 'ZZ BORRADOR VIEJO', 'Prueba', 'A')];
const PAST_TAB = [TAB_HEADER, row('1', 'crossroads', 'Crossroads', 'Eric Clapton', 'A')];

/**
 * One tab as a grid of `{ value, format }` cells, 1-based like Sheets. A value written to a cell
 * that is not plain text and looks like `d/m` becomes a date, as Sheets would turn it into one, so a
 * test can prove the format is set first. `convertAlways` simulates a Sheet that ignores the format.
 * The grid has exactly as many rows as `rows` (a trimmed tab): `getMaxRows` is that count, so a
 * trailing blank row in `rows` is a spare row. `brokenDelete` simulates a deleteRow that does nothing.
 * `lostValues` lists values whose setValue is logged but not stored (a write Sheets dropped).
 */
function gridSheet(name, rows, log, convertAlways, brokenDelete, lostValues = [], lostValueAt = -1, lostCells = []) {
  const cells = rows.map((r) => r.map((value) => ({ value, format: '' })));
  let valueWriteCount = 0;
  const cell = (r, c) => {
    while (cells.length < r) {
      cells.push([]);
    }
    const line = cells[r - 1];
    while (line.length < c) {
      line.push({ value: '', format: '' });
    }
    return line[c - 1];
  };
  const lastRow = () => {
    for (let r = cells.length; r >= 1; r--) {
      if (cells[r - 1].some((x) => x.value !== '')) {
        return r;
      }
    }
    return 0;
  };
  const lastColumn = () => Math.max(0, ...cells.map((line) => {
    for (let c = line.length; c >= 1; c--) {
      if (line[c - 1].value !== '') {
        return c;
      }
    }
    return 0;
  }));
  const shown = (x) => (x.value instanceof Date ? 'DATE' : String(x.value));
  const sheet = {
    name,
    cells,
    getName: () => name,
    getLastRow: lastRow,
    getLastColumn: lastColumn,
    getMaxRows: () => cells.length,
    deleteRow(r) {
      log.push(['deleteRow', name, r]);
      assert.ok(r >= 1 && r <= cells.length, `deleteRow ${r} outside the grid`);
      assert.ok(cells.length > 1, 'Sheets refuses to delete the only row');
      if (!brokenDelete) {
        cells.splice(r - 1, 1);
      }
    },
    insertRowAfter(r) {
      log.push(['insertRowAfter', name, r]);
      cells.splice(r, 0, []);
    },
    getDataRange() {
      const height = lastRow();
      const width = lastColumn();
      const grid = (read) => Array.from({ length: height }, (_, r) => Array.from({ length: width }, (__, c) => read(cell(r + 1, c + 1))));
      return { getDisplayValues: () => grid(shown), getValues: () => grid((x) => x.value) };
    },
    getRange(r, c, numRows = 1, numCols = 1) {
      return {
        setValues(values) {
          log.push(['setValues', name, r, c, values]);
          assert.equal(values.length, numRows);
          values.forEach((line, i) => {
            assert.equal(line.length, numCols);
            line.forEach((value, j) => {
              const target = cell(r + i, c + j);
              const converts = convertAlways || target.format !== '@';
              target.value = converts && /^\d+\/\d+$/.test(String(value)) ? new Date(0) : value;
            });
          });
        },
        setValue(value) {
          log.push(['setValue', name, r, c, value]);
          valueWriteCount++;
          if (lostValues.includes(value) || valueWriteCount === lostValueAt ||
              lostCells.some(target => target.sheet === name && target.row === r && target.column === c && target.value === value)) {
            return;
          }
          const target = cell(r, c);
          const converts = convertAlways || target.format !== '@';
          target.value = converts && /^\d+\/\d+$/.test(String(value)) ? new Date(0) : value;
        },
        setNumberFormat(format) {
          log.push(['setNumberFormat', name, r, c, format]);
          for (let i = 0; i < numRows; i++) {
            for (let j = 0; j < numCols; j++) {
              cell(r + i, c + j).format = format;
            }
          }
        },
        getDisplayValues() {
          return Array.from({ length: numRows }, (_, i) => Array.from({ length: numCols }, (__, j) => shown(cell(r + i, c + j))));
        },
      };
    },
  };
  return sheet;
}

/** A spreadsheet of grid tabs; `requested` lists every getSheetByName, `log` every write. */
function fakeSpreadsheet({ jams = JAMS, catalog = CATALOG, tabs = {}, convertAlways = false, brokenDelete = false, lostValues = [], lostValueAt = -1, lostCells = [] } = {}) {
  const log = [];
  const requested = [];
  const all = {
    Config: gridSheet('Config', [['clave', 'valor'], ['passphrase', STORED]], log),
    Jams: gridSheet('Jams', jams, log),
    Catalogo: gridSheet('Catalogo', catalog, log),
    [PAST]: gridSheet(PAST, PAST_TAB, log),
    [PAST_DRAFT]: gridSheet(PAST_DRAFT, PAST_DRAFT_TAB, log),
    [UPCOMING]: gridSheet(UPCOMING, UPCOMING_TAB, log),
  };
  for (const [name, rows] of Object.entries(tabs)) {
    if (rows === null) {
      delete all[name];
    } else {
      all[name] = gridSheet(name, rows, log);
    }
  }
  const spreadsheet = {
    getSheetByName(name) {
      requested.push(name);
      return all[name] || null;
    },
    getSpreadsheetTimeZone: () => BUENOS_AIRES,
    insertSheet(name) {
      log.push(['insertSheet', name]);
      assert.ok(!(name in all), `insertSheet: ${name} already exists`);
    all[name] = gridSheet(name, [], log, convertAlways, brokenDelete, lostValues, lostValueAt, lostCells);
      return all[name];
    },
    deleteSheet(sheet) {
      const name = Object.keys(all).find((key) => all[key] === sheet);
      log.push(['deleteSheet', name]);
      delete all[name];
    },
  };
  return { spreadsheet, tabs: all, log, requested };
}

function fakeLock() {
  const lock = {
    tries: 0,
    releases: 0,
    tryLock() {
      lock.tries += 1;
      return true;
    },
    releaseLock() {
      lock.releases += 1;
    },
  };
  return lock;
}

function services(now = NOW) {
  const store = new Map();
  return {
    cache: { get: (k) => store.get(k) || null, put: (k, v) => store.set(k, v) },
    lock: fakeLock(),
    now,
    flush() {},
  };
}

function call(fake, action, fields = {}, svc = services()) {
  return handlePost(Object.assign({ action, passphrase: STORED }, fields), fake.spreadsheet, svc);
}

function addSong(fake, fields, svc) {
  return call(fake, 'addSong', Object.assign({ date: UPCOMING, songId: 'crossroads', key: 'A' }, fields), svc);
}

function writes(log) {
  return log.filter((op) => WRITE_OPS.includes(op[0]));
}

function assertError(body, code) {
  assert.deepStrictEqual(Object.keys(body), ['schemaVersion', 'error'], JSON.stringify(body));
  assert.equal(body.error.code, code, JSON.stringify(body));
  assert.equal(typeof body.error.message, 'string');
}

function readTab(fake, name) {
  const range = fake.tabs[name].getDataRange();
  return buildSetlist(range.getDisplayValues(), range.getValues(), name);
}

// ---- admin-publish-setlist ----
test('publishSetlist writes only the upcoming Jams status cell and confirms its read-back', () => {
  const fake = fakeSpreadsheet();
  const result = call(fake, 'publishSetlist', { date: UPCOMING });
  assert.deepStrictEqual(result, { schemaVersion: 1, ok: true, alreadyPublished: false });
  assert.deepStrictEqual(writes(fake.log), [
    ['setNumberFormat', 'Jams', 4, 4, '@'],
    ['setValue', 'Jams', 4, 4, 'PUBLICADA'],
  ]);
  const jam = handleGet({ resource: 'jams' }, fake.spreadsheet).jams.find((item) => item.date === UPCOMING);
  assert.equal(jam.status, 'PUBLICADA');
  assert.deepStrictEqual(jam.setlist.map((item) => item.songId), ['the-thrill-is-gone', 'sweet-home-chicago', 'red-house']);
});

test('publishSetlist refuses bad, non-upcoming, tab-less, and empty drafts before any write', () => {
  const cases = [
    [{ date: '2026-02-30' }, 'invalid_date'],
    [{ date: PAST }, 'jam_not_editable'],
    [{ date: '1999-01-01' }, 'unknown_jam'],
    [{ date: UPCOMING }, 'empty_setlist', { tabs: { [UPCOMING]: null } }],
    [{ date: UPCOMING }, 'empty_setlist', { tabs: { [UPCOMING]: [TAB_HEADER] } }],
  ];
  for (const [fields, code, options = {}] of cases) {
    const fake = fakeSpreadsheet(options);
    assertError(call(fake, 'publishSetlist', fields), code);
    assert.deepStrictEqual(writes(fake.log), [], code);
  }
  const missingJamsHeader = fakeSpreadsheet({ jams: [['fecha', 'hora', 'lugar'], [UPCOMING, '21:00', 'Lugar']] });
  assertError(call(missingJamsHeader, 'publishSetlist', { date: UPCOMING }), 'missing_header');
  assert.deepStrictEqual(writes(missingJamsHeader.log), []);
});

test('publishSetlist retries idempotently and checkPublish cleans up and verifies its marker rows', () => {
  const publishedJams = JAMS.map((jam) => jam.slice());
  publishedJams[3][3] = 'PUBLICADA';
  const retry = fakeSpreadsheet({ jams: publishedJams });
  assert.deepStrictEqual(call(retry, 'publishSetlist', { date: UPCOMING }), {
    schemaVersion: 1,
    ok: true,
    alreadyPublished: true,
  });
  assert.deepStrictEqual(writes(retry.log), []);

  const fake = fakeSpreadsheet();
  const svc = services();
  let flushes = 0;
  svc.flush = () => { flushes++; };
  assert.deepStrictEqual(call(fake, 'checkPublish', {}, svc), { schemaVersion: 1, ok: true });
  assert.equal(flushes, 1);
  assert.ok(fake.log.some((op) => op[0] === 'setValue' && op[1] === PUBLISH_CHECK_TAB && op[4] === 'PUBLICADA'));
  assert.ok(fake.log.every((op) => !WRITE_OPS.includes(op[0]) || op[1] === PUBLISH_CHECK_TAB));
  assert.equal(fake.tabs[PUBLISH_CHECK_TAB], undefined);

  const dropped = fakeSpreadsheet({ lostCells: [{ sheet: PUBLISH_CHECK_TAB, row: 2, column: 4, value: 'PUBLICADA' }] });
  assertError(call(dropped, 'checkPublish'), 'internal_error');
  assert.equal(dropped.tabs[PUBLISH_CHECK_TAB], undefined);
});

// ---- readJams ----

test('readJams: the GET jams plus the songs of every current or future draft, read only, no lock', () => {
  const fake = fakeSpreadsheet();
  const svc = services();
  const body = call(fake, 'readJams', {}, svc);
  const get = handleGet({ resource: 'jams' }, fakeSpreadsheet().spreadsheet);
  assert.deepStrictEqual(Object.keys(body), ['schemaVersion', 'ok', 'jams']);
  assert.equal(body.ok, true);
  assert.equal(body.jams.length, 4);
  // The published jam and the past draft are exactly as the GET built them.
  assert.deepStrictEqual(body.jams[0], get.jams[0]);
  assert.deepStrictEqual(body.jams[1], get.jams[1]);
  assert.equal(body.jams[1].setlist, null);
  assert.ok(!JSON.stringify(body).includes(DRAFT_MARKER), 'a past draft leaked');
  // The upcoming draft carries its tab, the later draft a missing_tab error.
  assert.deepStrictEqual(body.jams[2].setlist.map((r) => [r.position, r.songId]), [['1', 'the-thrill-is-gone'], ['x', 'sweet-home-chicago'], ['3', 'red-house']]);
  assert.equal(body.jams[2].setlist[0].slots.guitar1, 'Ana');
  assert.equal(body.jams[2].setlistError, null);
  assert.equal(body.jams[3].setlist, null);
  assert.equal(body.jams[3].setlistError.code, 'missing_tab');
  assert.equal(svc.lock.tries, 0);
  assert.deepStrictEqual(writes(fake.log), []);
  assert.ok(!fake.requested.includes(PAST_DRAFT), 'the past draft tab was opened');
});

test('readJams: a draft for today is current; the GET still withholds every draft', () => {
  const today = '2026-10-06';
  const jams = [JAMS[0], [today, '21:00', 'Lugar', 'BORRADOR']];
  const tabs = { [today]: [TAB_HEADER, row('1', 'crossroads', 'Crossroads', 'Eric Clapton', 'A')] };
  const body = call(fakeSpreadsheet({ jams, tabs }), 'readJams');
  assert.equal(body.jams[0].setlist.length, 1);
  const get = handleGet({ resource: 'jams' }, fakeSpreadsheet({ jams, tabs }).spreadsheet);
  assert.equal(get.jams[0].setlist, null);
  assert.equal(get.jams[0].setlistError, null);
  // One millisecond before midnight of the next day it is still today; at midnight it is past.
  const nextMidnightBA = Date.UTC(2026, 9, 7, 3, 0, 0);
  assert.equal(call(fakeSpreadsheet({ jams, tabs }), 'readJams', {}, services(nextMidnightBA - 1)).jams[0].setlist.length, 1);
  assert.equal(call(fakeSpreadsheet({ jams, tabs }), 'readJams', {}, services(nextMidnightBA)).jams[0].setlist, null);
});

test('readJams: duplicate draft dates and broken draft tabs are per-jam errors; a non-exact status is not a draft', () => {
  const jams = [
    JAMS[0],
    [UPCOMING, '21:00', 'A', 'BORRADOR'],
    [UPCOMING, '21:00', 'B', 'BORRADOR'],
    [LATER, '21:00', 'C', 'BORRADOR'],
    ['2026-12-19', '21:00', 'D', 'borrador'],
    ['no es fecha', '21:00', 'E', 'BORRADOR'],
  ];
  const tabs = {
    [LATER]: [TAB_HEADER.slice(1)],
    '2026-12-19': [TAB_HEADER, row('1', DRAFT_MARKER, 'x', 'y', 'A')],
  };
  const body = call(fakeSpreadsheet({ jams, tabs }), 'readJams');
  assert.equal(body.jams[0].setlistError.code, 'duplicate_date');
  assert.equal(body.jams[1].setlistError.code, 'duplicate_date');
  assert.equal(body.jams[2].setlistError.code, 'missing_header');
  assert.equal(body.jams[3].setlist, null);
  assert.equal(body.jams[3].setlistError, null);
  assert.equal(body.jams[4].setlist, null);
  assert.equal(body.jams[4].setlistError, null);
  assert.ok(!JSON.stringify(body).includes(DRAFT_MARKER));
});

// ---- addSong ----

test('addSong appends the catalog song at max + 1 with the request key, seven open slots, plain-text cells', () => {
  const fake = fakeSpreadsheet();
  const svc = services();
  const body = addSong(fake, { key: 'E' }, svc);
  assert.deepStrictEqual(body, { schemaVersion: 1, ok: true, position: 4, title: 'Crossroads', artist: 'Eric Clapton' });
  assert.equal(svc.lock.tries, 1);
  assert.equal(svc.lock.releases, 1);

  const tab = fake.tabs[UPCOMING];
  assert.deepStrictEqual(tab.cells[4].map((x) => x.value), ['4', 'crossroads', 'Crossroads', 'Eric Clapton', 'E', '', '', '', '', '', '', '', '']);
  assert.ok(tab.cells[4].every((x) => x.format === '@'), 'every mapped cell is plain text');
  // Each cell's format is set before its value, and nothing outside row 5 is written.
  const ops = writes(fake.log);
  assert.equal(ops.length, 2, 'one contiguous mapped-column run is batched');
  assert.deepStrictEqual(ops, [
    ['setNumberFormat', UPCOMING, 5, 1, '@'],
    ['setValues', UPCOMING, 5, 1, [['4', 'crossroads', 'Crossroads', 'Eric Clapton', 'E', '', '', '', '', '', '', '', '']]],
  ]);
  // The read path returns the new row; the key is the request's, never tono_default (D-08).
  const read = readTab(fake, UPCOMING);
  assert.deepStrictEqual(read[3], {
    position: '4', songId: 'crossroads', title: 'Crossroads', artist: 'Eric Clapton', key: 'E',
    slots: { guitar1: null, guitar2: null, bass: null, drums: null, vocals: null, harmonica: null, keyboards: null },
    extraParticipants: null,
  });
  const admin = call(fake, 'readJams');
  assert.equal(admin.jams[2].setlist.at(-1).songId, 'crossroads');
});

test('addSong writes into the tab\'s own column order and leaves unmapped columns alone', () => {
  const header = ['notas', 'tono', 'id_tema', 'posicion', 'artista', 'titulo'].concat(SLOT_HEADERS.slice().reverse(), ['Otros']);
  const tabs = { [UPCOMING]: [header, ['ojo', 'A', 'red-house', '2', 'Jimi Hendrix', 'Red House', '', '', '', '', '', '', '', '']] };
  const fake = fakeSpreadsheet({ tabs });
  assert.equal(addSong(fake, { songId: 'the-thrill-is-gone', key: 'Bm' }).position, 3);
  const written = fake.tabs[UPCOMING].cells[2];
  assert.deepStrictEqual(written.slice(0, 6).map((x) => x.value), ['', 'Bm', 'the-thrill-is-gone', '3', 'B.B. King', 'The Thrill Is Gone']);
  assert.equal(written[0].format, '', 'the unmapped column was touched');
  assert.ok(!writes(fake.log).some((op) => op[3] === 1), 'column A was written');
});

test('addSong keeps a title such as 7/4 as text because the format is set first', () => {
  const fake = fakeSpreadsheet();
  const body = addSong(fake, { songId: 'siete-cuartos', key: 'C' });
  assert.equal(body.title, '7/4');
  assert.equal(readTab(fake, UPCOMING).at(-1).title, '7/4');
});

test('addSong to a jam with no tab creates it with the header in documented order and appends at 1', () => {
  const fake = fakeSpreadsheet({ tabs: { [UPCOMING]: null } });
  const body = addSong(fake, {});
  assert.equal(body.position, 1);
  assert.deepStrictEqual(fake.log[0], ['insertSheet', UPCOMING]);
  assert.deepStrictEqual(fake.log[1], ['setValues', UPCOMING, 1, 1, [TAB_HEADER]]);
  assert.deepStrictEqual(readTab(fake, UPCOMING).map((r) => [r.position, r.songId, r.key]), [['1', 'crossroads', 'A']]);
  // The same with a header-only tab.
  const empty = fakeSpreadsheet({ tabs: { [UPCOMING]: [TAB_HEADER] } });
  assert.equal(addSong(empty, {}).position, 1);
  assert.ok(!empty.log.some((op) => op[0] === 'insertSheet'));
});

test('two adds in a row get consecutive positions, each under the lock', () => {
  const fake = fakeSpreadsheet();
  const svc = services();
  assert.equal(addSong(fake, {}, svc).position, 4);
  assert.equal(addSong(fake, { songId: 'siete-cuartos', key: 'C' }, svc).position, 5);
  assert.equal(svc.lock.tries, 2);
  assert.equal(svc.lock.releases, 2);
});

test('addSong validates in order and writes nothing on any failure', () => {
  const brokenTab = { [UPCOMING]: [TAB_HEADER.filter((h) => h !== 'tono')] };
  const duplicatedHeader = { [UPCOMING]: [TAB_HEADER.concat(['Bajo'])] };
  const jamsWith = (...extra) => ({ jams: JAMS.concat(extra) });
  const cases = [
    [{ date: undefined }, 'invalid_date'],
    [{ date: 20261031 }, 'invalid_date'],
    [{ date: '31/10/2026' }, 'invalid_date'],
    [{ date: '2026-02-30', songId: 'BAD', key: 'H' }, 'invalid_date'],
    [{ date: ' 2026-10-31' }, 'invalid_date'],
    [{ songId: undefined }, 'invalid_song'],
    [{ songId: 'Crossroads', key: 'H' }, 'invalid_song'],
    [{ songId: 'cross--roads' }, 'invalid_song'],
    [{ songId: ' crossroads' }, 'invalid_song'],
    [{ key: undefined }, 'invalid_key'],
    [{ key: 'H' }, 'invalid_key'],
    [{ key: 'a' }, 'invalid_key'],
    [{ key: 'Am7' }, 'invalid_key'],
    [{ key: ' A' }, 'invalid_key'],
    [{ date: '1999-01-01', songId: 'zz-no-existe' }, 'unknown_jam'],
    [{ date: PAST }, 'jam_not_editable'],
    [{ date: PAST_DRAFT }, 'jam_not_editable'],
    [{ date: LATER }, 'jam_not_editable'],
    [{ songId: 'zz-no-existe' }, 'unknown_song'],
    [{ songId: 'sin-artista' }, 'unknown_song'],
    [{ songId: 'repetido' }, 'unknown_song'],
    [{ songId: 'the-thrill-is-gone' }, 'song_already_in_setlist'],
    [{ songId: 'zz-no-existe' }, 'unknown_song', { tabs: brokenTab }],
    [{ songId: 'zz-no-existe' }, 'unknown_song', { tabs: { [UPCOMING]: null } }],
    [{ songId: 'sin-artista' }, 'unknown_song', { tabs: { [UPCOMING]: null } }],
    [{}, 'missing_header', { tabs: brokenTab }],
    [{}, 'duplicate_header', { tabs: duplicatedHeader }],
    [{ songId: 'zz-no-existe' }, 'duplicate_date', jamsWith([UPCOMING, '22:00', 'Otro', 'BORRADOR'])],
    [{}, 'jam_not_editable', jamsWith(['2026-10-10', '21:00', 'Antes', 'PUBLICADA'])],
    [{ date: '2026-12-19' }, 'jam_not_editable', { jams: [JAMS[0], ['2026-12-19', '21:00', 'X', 'CANCELADA']] }],
    [{}, 'missing_tab', { jams: [] , tabs: { Jams: null } }],
    [{}, 'missing_tab', { tabs: { Catalogo: null } }],
  ];
  for (const [fields, code, options = {}] of cases) {
    const fake = fakeSpreadsheet(options);
    const svc = services();
    const body = addSong(fake, fields, svc);
    assertError(body, code);
    assert.deepStrictEqual(writes(fake.log), [], `${code} ${JSON.stringify(fields)} wrote`);
    assert.equal(svc.lock.releases, svc.lock.tries, 'the lock was left held');
  }
});

test('an unknown estado earlier than the upcoming jam does not block it', () => {
  const jams = JAMS.concat([['2026-10-10', '21:00', 'X', 'Cancelada']]);
  assert.equal(addSong(fakeSpreadsheet({ jams }), {}).position, 4);
});

test('a published upcoming jam is editable too', () => {
  const jams = [JAMS[0], [UPCOMING, '21:00', 'La Macanuda', 'PUBLICADA']];
  assert.equal(addSong(fakeSpreadsheet({ jams }), {}).position, 4);
});

test('the server never reads tono_default: the stored key is the request\'s for every song', () => {
  for (const [songId, key] of [['crossroads', 'Bbm'], ['the-thrill-is-gone', 'C#']]) {
    const fake = fakeSpreadsheet({ tabs: { [UPCOMING]: [TAB_HEADER] } });
    addSong(fake, { songId, key });
    assert.equal(readTab(fake, UPCOMING)[0].key, key);
  }
});

// ---- checkSetlistWrite ----

test('checkSetlistWrite creates the check tab, appends the marker, reads it back and deletes the tab', () => {
  const fake = fakeSpreadsheet();
  const before = Object.keys(fake.tabs).sort();
  const svc = services();
  assert.deepStrictEqual(call(fake, 'checkSetlistWrite', {}, svc), { schemaVersion: 1, ok: true });
  assert.equal(SETLIST_CHECK_TAB, '_prueba_lista');
  assert.deepStrictEqual(fake.log[0], ['insertSheet', SETLIST_CHECK_TAB]);
  assert.deepStrictEqual(fake.log[1], ['setValues', SETLIST_CHECK_TAB, 1, 1, [TAB_HEADER]]);
  assert.deepStrictEqual(fake.log.at(-1), ['deleteSheet', SETLIST_CHECK_TAB]);
  assert.ok(fake.log.every((op) => op[1] === SETLIST_CHECK_TAB), 'another tab was touched');
  assert.deepStrictEqual(Object.keys(fake.tabs).sort(), before);
  assert.equal(svc.lock.tries, 1);
  assert.equal(svc.lock.releases, 1);
});

test('checkSetlistWrite deletes a leftover first', () => {
  const fake = fakeSpreadsheet({ tabs: { [SETLIST_CHECK_TAB]: [['viejo']] } });
  assert.deepStrictEqual(call(fake, 'checkSetlistWrite'), { schemaVersion: 1, ok: true });
  assert.deepStrictEqual(fake.log[0], ['deleteSheet', SETLIST_CHECK_TAB]);
  assert.deepStrictEqual(fake.log[1], ['insertSheet', SETLIST_CHECK_TAB]);
  assert.ok(!(SETLIST_CHECK_TAB in fake.tabs));
});

test('checkSetlistWrite with a Sheet that converts 7/4 is internal_error and still deletes the tab', () => {
  const fake = fakeSpreadsheet({ convertAlways: true });
  const svc = services();
  assertError(call(fake, 'checkSetlistWrite', {}, svc), 'internal_error');
  assert.ok(!(SETLIST_CHECK_TAB in fake.tabs));
  assert.equal(svc.lock.releases, 1);
});

test('the new actions need the passphrase like every other', () => {
  for (const action of ['readJams', 'addSong', 'checkSetlistWrite', 'removeSong', 'checkSetlistRemove']) {
    const fake = fakeSpreadsheet();
    assertError(handlePost({ action, date: UPCOMING, songId: 'crossroads', key: 'A' }, fake.spreadsheet, services()), 'invalid_passphrase');
    assert.deepStrictEqual(fake.requested, ['Config']);
    assert.deepStrictEqual(writes(fake.log), []);
    assert.ok(Object.prototype.hasOwnProperty.call(ACTIONS, action));
  }
});

// ---- removeSong ----

const HOOCHIE = 'hoochie-coochie-man';
const PRIDE = 'pride-and-joy';
const FILLED = ['Ana', '', 'Beto', '', '', '', ''];

function removeSong(fake, fields, svc) {
  return call(fake, 'removeSong', Object.assign({ date: UPCOMING, songId: HOOCHIE }, fields), svc);
}

function moveSong(fake, fields, svc) {
  return call(fake, 'moveSong', Object.assign({ date: UPCOMING, songId: HOOCHIE, toPosition: 1 }, fields), svc);
}

function setValuesOps(log) {
  return log.filter((op) => op[0] === 'setValues');
}

function orderedRows(fake, name = UPCOMING) {
  return readTab(fake, name).slice().sort((left, right) => Number(left.position) - Number(right.position));
}

/** Scenario 1's tab: positions 1..4 in an arbitrary tab order, each song with its own lineup. */
const FOUR_SONGS = [
  TAB_HEADER,
  row('3', 'the-thrill-is-gone', 'The Thrill Is Gone', 'B.B. King', 'Bm', ['', 'Caro', '', '', '', '', '']),
  row('1', 'crossroads', 'Crossroads', 'Eric Clapton', 'A', FILLED),
  row('4', PRIDE, 'Pride and Joy', 'Stevie Ray Vaughan', 'E', ['', '', '', 'Dani', '', '', ''], 'Eva (Saxo)'),
  row('2', HOOCHIE, 'Hoochie Coochie Man', 'Muddy Waters', 'A', ['Fede', '', '', '', 'Gabi', '', ''], 'Hugo (Trompeta)'),
];

test('moveSong writes one contiguous block, changes positions only, and keeps the result contiguous', () => {
  const contiguous = [TAB_HEADER,
    row('1', 'crossroads', 'Crossroads', 'Eric Clapton', 'A'),
    row('2', HOOCHIE, 'Hoochie Coochie Man', 'Muddy Waters', 'A'),
    row('3', 'the-thrill-is-gone', 'The Thrill Is Gone', 'B.B. King', 'Bm'),
    row('4', PRIDE, 'Pride and Joy', 'Stevie Ray Vaughan', 'E')];
  const fake = fakeSpreadsheet({ tabs: { [UPCOMING]: contiguous } });
  const before = readTab(fake, UPCOMING);
  assert.deepStrictEqual(moveSong(fake, { songId: 'the-thrill-is-gone', toPosition: 1 }),
    { schemaVersion: 1, ok: true, position: 1 });
  assert.deepStrictEqual(orderedRows(fake).map((item) => [item.songId, item.position]), [
    ['the-thrill-is-gone', '1'], ['crossroads', '2'], [HOOCHIE, '3'], [PRIDE, '4'],
  ]);
  assert.deepStrictEqual(writes(fake.log), [
    ['setNumberFormat', UPCOMING, 2, 1, '@'],
    ['setValues', UPCOMING, 2, 1, [['2'], ['3'], ['1']]],
  ]);
  const after = readTab(fake, UPCOMING);
  after.forEach((item) => {
    const old = before.find((rowBefore) => rowBefore.songId === item.songId);
    assert.deepStrictEqual(Object.assign({}, item, { position: old.position }), old, 'only position changed');
  });
});

test('moveSong down, clamps, no-ops, and closes gaps', () => {
  const fake = fakeSpreadsheet({ tabs: { [UPCOMING]: FOUR_SONGS } });
  assert.equal(moveSong(fake, { songId: 'crossroads', toPosition: 3 }).position, 3);
  assert.deepStrictEqual(orderedRows(fake).map((item) => [item.songId, item.position]), [
    [HOOCHIE, '1'], ['the-thrill-is-gone', '2'], ['crossroads', '3'], [PRIDE, '4'],
  ]);
  assert.equal(moveSong(fake, { songId: HOOCHIE, toPosition: 99 }).position, 4);
  const writesBefore = writes(fake.log).length;
  assert.equal(moveSong(fake, { songId: HOOCHIE, toPosition: 4 }).position, 4);
  assert.equal(writes(fake.log).length, writesBefore, 'same-position move writes nothing');

  const gap = fakeSpreadsheet({ tabs: { [UPCOMING]: [
    TAB_HEADER, row('1', 'a-uno', 'Uno', 'X', 'A'), row('2', 'a-dos', 'Dos', 'X', 'A'),
    row('4', 'a-cuatro', 'Cuatro', 'X', 'A'), row('5', 'a-cinco', 'Cinco', 'X', 'A'),
  ] } });
  assert.equal(moveSong(gap, { songId: 'a-dos', toPosition: 2 }).position, 2);
  assert.deepStrictEqual(orderedRows(gap).map((item) => item.position), ['1', '2', '3', '4']);
});

test('moveSong park path never duplicates any whole position after an individual write', () => {
  const shuffled = [TAB_HEADER,
    row('4', 'a-cuatro', 'Cuatro', 'X', 'A'), row('2', 'a-dos', 'Dos', 'X', 'A'),
    row('5', 'a-cinco', 'Cinco', 'X', 'A'), row('1', 'a-uno', 'Uno', 'X', 'A'),
    row('3', 'a-tres', 'Tres', 'X', 'A')];
  const fake = fakeSpreadsheet({ tabs: { [UPCOMING]: shuffled } });
  assert.equal(moveSong(fake, { songId: 'a-dos', toPosition: 1 }).position, 1);
  let positions = shuffled.slice(1).map((item) => Number(item[0]));
  for (const operation of writes(fake.log).filter((item) => item[0] === 'setValues' && item[3] === 1)) {
    const start = operation[2] - 2;
    operation[4].forEach((line, index) => { positions[start + index] = Number(line[0]); });
    assert.equal(new Set(positions).size, positions.length, `duplicate positions after ${JSON.stringify(operation)}`);
  }
  assert.deepStrictEqual(orderedRows(fake).map((item) => [item.songId, item.position]), [
    ['a-dos', '1'], ['a-uno', '2'], ['a-tres', '3'], ['a-cuatro', '4'], ['a-cinco', '5'],
  ]);
});

test('moveSong validates date, id, JSON integer, jam, headers, row and ordering before any write', () => {
  const cases = [
    [{ date: '31/10/2026' }, 'invalid_date'],
    [{ songId: 'Crossroads' }, 'invalid_song'],
    [{ toPosition: '2' }, 'invalid_position'],
    [{ toPosition: 1.5 }, 'invalid_position'],
    [{ toPosition: 0 }, 'invalid_position'],
    [{ date: '1999-01-01' }, 'unknown_jam'],
    [{ date: PAST }, 'jam_not_editable'],
    [{ songId: 'zz-no-existe' }, 'song_not_in_setlist'],
    [{ songId: 'the-thrill-is-gone' }, 'duplicate_song', { tabs: { [UPCOMING]: FOUR_SONGS.concat([row('5', ' the-thrill-is-gone', 'Otra vez', 'B.B.', 'C')]) } }],
    [{ songId: 'sweet-home-chicago' }, 'unordered_setlist'],
  ];
  for (const [fields, code, options = {}] of cases) {
    const fake = fakeSpreadsheet(options);
    assertError(moveSong(fake, fields), code);
    assert.deepStrictEqual(writes(fake.log), [], `${code} wrote`);
    assert.ok(!fake.requested.includes('Catalogo'), `${code} opened catalog`);
  }
});

test('checkSetlistMove exercises contiguous and park paths, then removes its temporary tab', () => {
  const fake = fakeSpreadsheet();
  const before = Object.keys(fake.tabs).sort();
  assert.deepStrictEqual(call(fake, 'checkSetlistMove'), { schemaVersion: 1, ok: true });
  assert.ok(fake.log.every((operation) => operation[1] === SETLIST_CHECK_TAB));
  assert.deepStrictEqual(Object.keys(fake.tabs).sort(), before);
  assert.deepStrictEqual(fake.log.at(-1), ['deleteSheet', SETLIST_CHECK_TAB]);
});

test('removeSong deletes the row found by id_tema and renumbers the later positions 1..n, as plain text', () => {
  const fake = fakeSpreadsheet({ tabs: { [UPCOMING]: FOUR_SONGS } });
  const catalogBefore = JSON.stringify(fake.tabs.Catalogo.cells);
  const before = readTab(fake, UPCOMING);
  const svc = services();

  const body = removeSong(fake, {}, svc);

  assert.deepStrictEqual(body, { schemaVersion: 1, ok: true, position: 2 });
  assert.equal(svc.lock.tries, 1);
  assert.equal(svc.lock.releases, 1);
  const after = readTab(fake, UPCOMING);
  assert.deepStrictEqual(after.map((r) => [r.position, r.songId]), [['2', 'the-thrill-is-gone'], ['1', 'crossroads'], ['3', PRIDE]]);
  // Every other field of every kept row is exactly what it was: key, slots (musicians) and Otros.
  const strip = (r) => Object.assign({}, r, { position: null });
  assert.deepStrictEqual(after.map(strip), before.filter((r) => r.songId !== HOOCHIE).map(strip));
  assert.ok(!JSON.stringify(after).includes('Fede') && !JSON.stringify(after).includes('Hugo'), 'the removed row left data');
  // The renumbered cells are plain text; the untouched row keeps its format.
  const tab = fake.tabs[UPCOMING];
  assert.equal(tab.cells[1][0].format, '@');
  assert.equal(tab.cells[3][0].format, '@');
  assert.equal(tab.cells[2][0].format, '');
  // The admin read returns the same three songs.
  const admin = call(fake, 'readJams');
  assert.deepStrictEqual(admin.jams[2].setlist.map((r) => r.songId), ['the-thrill-is-gone', 'crossroads', PRIDE]);
  // The catalog is never opened, let alone written; every write is on the jam tab.
  assert.equal(JSON.stringify(fake.tabs.Catalogo.cells), catalogBefore);
  assert.ok(!fake.requested.includes('Catalogo'), 'the catalog was opened');
  assert.ok(writes(fake.log).every((op) => op[1] === UPCOMING), JSON.stringify(writes(fake.log)));
});

test('removeSong on an app-built tab renumbers with one format call and one setValues for the whole run', () => {
  const tabs = {
    [UPCOMING]: [
      TAB_HEADER,
      row('1', 'crossroads', 'Crossroads', 'Eric Clapton', 'A'),
      row('2', HOOCHIE, 'Hoochie Coochie Man', 'Muddy Waters', 'A'),
      row('3', 'the-thrill-is-gone', 'The Thrill Is Gone', 'B.B. King', 'Bm'),
      row('4', PRIDE, 'Pride and Joy', 'Stevie Ray Vaughan', 'E'),
    ],
  };
  const fake = fakeSpreadsheet({ tabs });
  assert.equal(removeSong(fake, { songId: 'crossroads' }).position, 1);
  assert.deepStrictEqual(writes(fake.log), [
    ['deleteRow', UPCOMING, 2],
    ['setNumberFormat', UPCOMING, 2, 1, '@'],
    ['setValues', UPCOMING, 2, 1, [['1'], ['2'], ['3']]],
  ]);
  assert.deepStrictEqual(readTab(fake, UPCOMING).map((r) => [r.position, r.songId]), [['1', HOOCHIE], ['2', 'the-thrill-is-gone'], ['3', PRIDE]]);
});

test('removeSong renumbers in ascending position order even when the tab order is shuffled', () => {
  const tabs = {
    [UPCOMING]: [
      TAB_HEADER,
      row('4', 'a-cuatro', 'Cuatro', 'X', 'A'),
      row('2', 'a-dos', 'Dos', 'X', 'A'),
      row('5', 'a-cinco', 'Cinco', 'X', 'A'),
      row('1', 'a-uno', 'Uno', 'X', 'A'),
      row('3', 'a-tres', 'Tres', 'X', 'A'),
    ],
  };
  const fake = fakeSpreadsheet({ tabs });
  assert.equal(removeSong(fake, { songId: 'a-uno' }).position, 1);
  // Each write sets the next position up: 1, 2, 3, 4 in that order, so a failure midway can leave
  // a gap but never two rows with one position.
  assert.deepStrictEqual(setValuesOps(fake.log).map((op) => op[4]), [[['1']], [['2']], [['3']], [['4']]]);
  assert.deepStrictEqual(setValuesOps(fake.log).map((op) => op[2]), [3, 5, 2, 4]);
  assert.deepStrictEqual(readTab(fake, UPCOMING).map((r) => [r.songId, r.position]), [['a-cuatro', '3'], ['a-dos', '1'], ['a-cinco', '4'], ['a-tres', '2']]);
});

test('removeSong never deletes the grid\'s last row directly, and a spare row avoids the insert', () => {
  const trimmed = fakeSpreadsheet({ tabs: { [UPCOMING]: [TAB_HEADER, row('1', HOOCHIE, 'Hoochie Coochie Man', 'Muddy Waters', 'A')] } });
  assert.equal(removeSong(trimmed, {}).position, 1);
  assert.deepStrictEqual(writes(trimmed.log), [['insertRowAfter', UPCOMING, 2], ['deleteRow', UPCOMING, 2]]);
  // The last song: the header stays and the reads return an empty setlist.
  assert.deepStrictEqual(readTab(trimmed, UPCOMING), []);
  assert.deepStrictEqual(trimmed.tabs[UPCOMING].cells[0].map((x) => x.value), TAB_HEADER);
  assert.deepStrictEqual(call(trimmed, 'readJams').jams[2].setlist, []);

  const spare = fakeSpreadsheet({ tabs: { [UPCOMING]: [TAB_HEADER, row('1', HOOCHIE, 'H', 'M', 'A'), row('', '', '', '', '')] } });
  assert.equal(removeSong(spare, {}).position, 1);
  assert.deepStrictEqual(writes(spare.log), [['deleteRow', UPCOMING, 2]]);
});

test('removeSong matches a trimmed id_tema, reads typed positions, and renumbers nothing after an invalid one', () => {
  const typed = {
    [UPCOMING]: [
      TAB_HEADER,
      row(1, ' crossroads ', 'Crossroads', 'Eric Clapton', 'A'),
      row(2, HOOCHIE, 'Hoochie Coochie Man', 'Muddy Waters', 'A'),
      row(3, PRIDE, 'Pride and Joy', 'Stevie Ray Vaughan', 'E'),
    ],
  };
  const fake = fakeSpreadsheet({ tabs: typed });
  assert.equal(removeSong(fake, { songId: 'crossroads' }).position, 1);
  assert.deepStrictEqual(readTab(fake, UPCOMING).map((r) => [r.position, r.songId]), [['1', HOOCHIE], ['2', PRIDE]]);

  // UPCOMING_TAB holds 1, x and 3: removing the x row answers null and leaves 1 and 3 alone.
  const invalid = fakeSpreadsheet();
  assert.deepStrictEqual(removeSong(invalid, { songId: 'sweet-home-chicago' }), { schemaVersion: 1, ok: true, position: null });
  assert.deepStrictEqual(writes(invalid.log), [['deleteRow', UPCOMING, 3]]);
  assert.deepStrictEqual(readTab(invalid, UPCOMING).map((r) => [r.position, r.songId]), [['1', 'the-thrill-is-gone'], ['3', 'red-house']]);
});

test('removes and adds in a row keep the tab numbered 1..n', () => {
  const fake = fakeSpreadsheet({ tabs: { [UPCOMING]: FOUR_SONGS } });
  const svc = services();
  assert.equal(removeSong(fake, { songId: 'crossroads' }, svc).position, 1);
  assert.equal(removeSong(fake, { songId: PRIDE }, svc).position, 3);
  assert.equal(addSong(fake, { songId: 'crossroads', key: 'G' }, svc).position, 3);
  const positions = readTab(fake, UPCOMING).map((r) => r.position).sort();
  assert.deepStrictEqual(positions, ['1', '2', '3']);
  assert.equal(svc.lock.tries, 3);
  assert.equal(svc.lock.releases, 3);
});

test('removeSong validates in order and writes nothing on any failure', () => {
  const brokenTab = { [UPCOMING]: [TAB_HEADER.filter((h) => h !== 'tono')] };
  const duplicatedHeader = { [UPCOMING]: [TAB_HEADER.concat(['Bajo'])] };
  const duplicatedSong = { [UPCOMING]: UPCOMING_TAB.concat([row('4', ' the-thrill-is-gone', 'Otra vez', 'B.B. King', 'C')]) };
  const jamsWith = (...extra) => ({ jams: JAMS.concat(extra) });
  const cases = [
    [{ date: undefined }, 'invalid_date'],
    [{ date: 20261031 }, 'invalid_date'],
    [{ date: '31/10/2026' }, 'invalid_date'],
    [{ date: '2026-02-30', songId: 'BAD' }, 'invalid_date'],
    [{ songId: undefined }, 'invalid_song'],
    [{ songId: 'Crossroads', date: '1999-01-01' }, 'invalid_song'],
    [{ songId: ' crossroads' }, 'invalid_song'],
    [{ date: '1999-01-01', songId: 'zz-no-existe' }, 'unknown_jam'],
    [{ date: PAST, songId: 'crossroads' }, 'jam_not_editable'],
    [{ date: PAST_DRAFT, songId: DRAFT_MARKER }, 'jam_not_editable'],
    [{ date: LATER }, 'jam_not_editable'],
    [{ songId: 'the-thrill-is-gone' }, 'duplicate_date', jamsWith([UPCOMING, '22:00', 'Otro', 'BORRADOR'])],
    [{ songId: 'the-thrill-is-gone' }, 'jam_not_editable', jamsWith(['2026-10-10', '21:00', 'Antes', 'PUBLICADA'])],
    [{ songId: 'zz-no-existe' }, 'missing_header', { tabs: brokenTab }],
    [{ songId: 'zz-no-existe' }, 'duplicate_header', { tabs: duplicatedHeader }],
    [{ songId: 'zz-no-existe' }, 'song_not_in_setlist'],
    [{ songId: 'crossroads' }, 'song_not_in_setlist'],
    [{}, 'song_not_in_setlist', { tabs: { [UPCOMING]: null } }],
    [{}, 'song_not_in_setlist', { tabs: { [UPCOMING]: [TAB_HEADER] } }],
    [{ songId: 'the-thrill-is-gone' }, 'duplicate_song', { tabs: duplicatedSong }],
    [{}, 'missing_tab', { jams: [], tabs: { Jams: null } }],
  ];
  for (const [fields, code, options = {}] of cases) {
    const fake = fakeSpreadsheet(options);
    const svc = services();
    const body = removeSong(fake, fields, svc);
    assertError(body, code);
    assert.deepStrictEqual(writes(fake.log), [], `${code} ${JSON.stringify(fields)} wrote`);
    assert.equal(svc.lock.releases, svc.lock.tries, 'the lock was left held');
    assert.ok(!fake.requested.includes('Catalogo'), `${code} opened the catalog`);
  }
});

test('a published upcoming jam allows removal too', () => {
  const jams = [JAMS[0], [UPCOMING, '21:00', 'La Macanuda', 'PUBLICADA']];
  assert.equal(removeSong(fakeSpreadsheet({ jams }), { songId: 'the-thrill-is-gone' }).position, 1);
});

// ---- checkSetlistRemove ----

test('checkSetlistRemove appends three markers, removes the second, reads back 1 and 2, and deletes the tab', () => {
  const fake = fakeSpreadsheet();
  const before = Object.keys(fake.tabs).sort();
  const svc = services();
  assert.deepStrictEqual(call(fake, 'checkSetlistRemove', {}, svc), { schemaVersion: 1, ok: true });
  assert.deepStrictEqual(fake.log[0], ['insertSheet', SETLIST_CHECK_TAB]);
  assert.deepStrictEqual(fake.log[1], ['setValues', SETLIST_CHECK_TAB, 1, 1, [TAB_HEADER]]);
  assert.ok(fake.log.some((op) => op[0] === 'deleteRow' && op[2] === 3), 'the second marker was not deleted');
  assert.ok(fake.log.some((op) => op[0] === 'setValues' && op[2] === 3 && JSON.stringify(op[4]) === '[["2"]]'), 'the third marker was not renumbered');
  assert.deepStrictEqual(fake.log.at(-1), ['deleteSheet', SETLIST_CHECK_TAB]);
  assert.ok(fake.log.every((op) => op[1] === SETLIST_CHECK_TAB), 'another tab was touched');
  assert.deepStrictEqual(Object.keys(fake.tabs).sort(), before);
  assert.equal(svc.lock.tries, 1);
  assert.equal(svc.lock.releases, 1);
});

test('checkSetlistRemove deletes a leftover first', () => {
  const fake = fakeSpreadsheet({ tabs: { [SETLIST_CHECK_TAB]: [['viejo']] } });
  assert.deepStrictEqual(call(fake, 'checkSetlistRemove'), { schemaVersion: 1, ok: true });
  assert.deepStrictEqual(fake.log[0], ['deleteSheet', SETLIST_CHECK_TAB]);
  assert.deepStrictEqual(fake.log[1], ['insertSheet', SETLIST_CHECK_TAB]);
  assert.ok(!(SETLIST_CHECK_TAB in fake.tabs));
});

test('checkSetlistRemove with a Sheet whose deleteRow does nothing is internal_error and still deletes the tab', () => {
  const fake = fakeSpreadsheet({ brokenDelete: true });
  const svc = services();
  assertError(call(fake, 'checkSetlistRemove', {}, svc), 'internal_error');
  assert.ok(!(SETLIST_CHECK_TAB in fake.tabs));
  assert.equal(svc.lock.releases, 1);
});

// ---- setKey ----

function setKey(fake, fields, svc) {
  return call(fake, 'setKey', Object.assign({ date: UPCOMING, songId: HOOCHIE, key: 'Bb' }, fields), svc);
}

test('setKey writes only the tono cell of the row found by id_tema, as plain text, format first', () => {
  const fake = fakeSpreadsheet({ tabs: { [UPCOMING]: FOUR_SONGS } });
  const catalogBefore = JSON.stringify(fake.tabs.Catalogo.cells);
  const before = readTab(fake, UPCOMING);
  const svc = services();

  assert.deepStrictEqual(setKey(fake, {}, svc), { schemaVersion: 1, ok: true });

  assert.equal(svc.lock.tries, 1);
  assert.equal(svc.lock.releases, 1);
  // HOOCHIE is on sheet row 5; tono is column 5. Exactly two calls, on that one cell.
  assert.deepStrictEqual(writes(fake.log), [
    ['setNumberFormat', UPCOMING, 5, 5, '@'],
    ['setValue', UPCOMING, 5, 5, 'Bb'],
  ]);
  const after = readTab(fake, UPCOMING);
  assert.deepStrictEqual(after, before.map((r) => (r.songId === HOOCHIE ? Object.assign({}, r, { key: 'Bb' }) : r)));
  // The admin read returns it too. Catalogo, and so tono_default, is never opened (D-08).
  assert.equal(call(fake, 'readJams').jams[2].setlist.find((r) => r.songId === HOOCHIE).key, 'Bb');
  assert.equal(JSON.stringify(fake.tabs.Catalogo.cells), catalogBefore);
  assert.ok(!fake.requested.includes('Catalogo'), 'the catalog was opened');
});

test('setKey keeps the admin spelling and finds the cell in the tab own column order', () => {
  const header = ['notas', 'tono', 'id_tema', 'posicion', 'artista', 'titulo'].concat(SLOT_HEADERS, ['Otros']);
  const tabs = {
    [UPCOMING]: [
      header,
      ['ojo', 'A', ' crossroads ', '1', 'Eric Clapton', 'Crossroads', '', '', '', '', '', '', '', ''],
      ['', 'E', 'red-house', '2', 'Jimi Hendrix', 'Red House', '', '', '', '', '', '', '', ''],
    ],
  };
  for (const key of ['A#', 'Bb', 'C#m', 'Ebm']) {
    const fake = fakeSpreadsheet({ tabs });
    assert.deepStrictEqual(setKey(fake, { songId: 'crossroads', key }), { schemaVersion: 1, ok: true });
    assert.deepStrictEqual(writes(fake.log), [['setNumberFormat', UPCOMING, 2, 2, '@'], ['setValue', UPCOMING, 2, 2, key]]);
    assert.deepStrictEqual(readTab(fake, UPCOMING).map((r) => [r.songId, r.key]), [['crossroads', key], ['red-house', 'E']]);
    assert.equal(fake.tabs[UPCOMING].cells[1][0].value, 'ojo', 'the unmapped column was touched');
  }
});

test('setKey on a published upcoming jam is what the musicians GET returns next', () => {
  const jams = [JAMS[0], [UPCOMING, '21:00', 'La Macanuda', 'PUBLICADA']];
  const fake = fakeSpreadsheet({ jams, tabs: { [UPCOMING]: FOUR_SONGS } });
  const before = handleGet({ resource: 'jams' }, fake.spreadsheet).jams.find((j) => j.date === UPCOMING).setlist.find((r) => r.songId === HOOCHIE);
  assert.equal(before.key, 'A');
  assert.deepStrictEqual(setKey(fake, { key: 'Bb' }), { schemaVersion: 1, ok: true });
  const get = handleGet({ resource: 'jams' }, fake.spreadsheet).jams.find((j) => j.date === UPCOMING);
  assert.equal(get.setlist.find((r) => r.songId === HOOCHIE).key, 'Bb');
  assert.deepStrictEqual(get.setlist.map((r) => r.songId), ['the-thrill-is-gone', 'crossroads', PRIDE, HOOCHIE]);
});

test('setKey after another admin removed an earlier song still hits the right row', () => {
  const fake = fakeSpreadsheet({ tabs: { [UPCOMING]: FOUR_SONGS } });
  const svc = services();
  // PRIDE is on sheet row 4; removing crossroads (row 3) moves it to row 3 and renumbers.
  assert.equal(removeSong(fake, { songId: 'crossroads' }, svc).position, 1);
  fake.log.length = 0;
  assert.deepStrictEqual(setKey(fake, { songId: PRIDE, key: 'G' }, svc), { schemaVersion: 1, ok: true });
  assert.deepStrictEqual(writes(fake.log), [['setNumberFormat', UPCOMING, 3, 5, '@'], ['setValue', UPCOMING, 3, 5, 'G']]);
  assert.deepStrictEqual(readTab(fake, UPCOMING).map((r) => [r.songId, r.position, r.key]), [
    ['the-thrill-is-gone', '2', 'Bm'],
    [PRIDE, '3', 'G'],
    [HOOCHIE, '1', 'A'],
  ]);
});

test('setKey validates in order and writes nothing on any failure', () => {
  const brokenTab = { [UPCOMING]: [TAB_HEADER.filter((h) => h !== 'tono')] };
  const duplicatedHeader = { [UPCOMING]: [TAB_HEADER.concat(['Bajo'])] };
  const duplicatedSong = { [UPCOMING]: UPCOMING_TAB.concat([row('4', ' the-thrill-is-gone', 'Otra vez', 'B.B. King', 'C')]) };
  const jamsWith = (...extra) => ({ jams: JAMS.concat(extra) });
  const cases = [
    [{ date: undefined }, 'invalid_date'],
    [{ date: 20261031 }, 'invalid_date'],
    [{ date: '31/10/2026' }, 'invalid_date'],
    [{ date: '2026-02-30', songId: 'BAD', key: 'H' }, 'invalid_date'],
    [{ songId: undefined }, 'invalid_song'],
    [{ songId: 'Crossroads', key: 'H', date: '1999-01-01' }, 'invalid_song'],
    [{ songId: ' crossroads' }, 'invalid_song'],
    [{ key: undefined }, 'invalid_key'],
    [{ key: 'H', date: '1999-01-01' }, 'invalid_key'],
    [{ key: 'a' }, 'invalid_key'],
    [{ key: 'Am7' }, 'invalid_key'],
    [{ key: ' A' }, 'invalid_key'],
    [{ key: '' }, 'invalid_key'],
    [{ date: '1999-01-01', songId: 'zz-no-existe' }, 'unknown_jam'],
    [{ date: PAST, songId: 'crossroads' }, 'jam_not_editable'],
    [{ date: PAST_DRAFT, songId: DRAFT_MARKER }, 'jam_not_editable'],
    [{ date: LATER }, 'jam_not_editable'],
    [{ songId: 'the-thrill-is-gone' }, 'duplicate_date', jamsWith([UPCOMING, '22:00', 'Otro', 'BORRADOR'])],
    [{ songId: 'the-thrill-is-gone' }, 'jam_not_editable', jamsWith(['2026-10-10', '21:00', 'Antes', 'PUBLICADA'])],
    [{}, 'song_not_in_setlist', { tabs: { [UPCOMING]: null } }],
    [{ songId: 'zz-no-existe' }, 'missing_header', { tabs: brokenTab }],
    [{ songId: 'zz-no-existe' }, 'duplicate_header', { tabs: duplicatedHeader }],
    [{ songId: 'zz-no-existe' }, 'song_not_in_setlist'],
    [{ songId: 'crossroads' }, 'song_not_in_setlist'],
    [{}, 'song_not_in_setlist', { tabs: { [UPCOMING]: [TAB_HEADER] } }],
    [{ songId: 'the-thrill-is-gone' }, 'duplicate_song', { tabs: duplicatedSong }],
    [{}, 'missing_tab', { jams: [], tabs: { Jams: null } }],
  ];
  for (const [fields, code, options = {}] of cases) {
    const fake = fakeSpreadsheet(options);
    const svc = services();
    const body = setKey(fake, fields, svc);
    assertError(body, code);
    assert.deepStrictEqual(writes(fake.log), [], `${code} ${JSON.stringify(fields)} wrote`);
    assert.equal(svc.lock.releases, svc.lock.tries, 'the lock was left held');
    assert.ok(!fake.requested.includes('Catalogo'), `${code} opened the catalog`);
  }
});

test('setKey needs the passphrase like every other action', () => {
  const fake = fakeSpreadsheet();
  assertError(handlePost({ action: 'setKey', date: UPCOMING, songId: 'the-thrill-is-gone', key: 'A' }, fake.spreadsheet, services()), 'invalid_passphrase');
  assert.deepStrictEqual(fake.requested, ['Config']);
  assert.deepStrictEqual(writes(fake.log), []);
  assert.equal(ACTIONS.setKey.write, true);
});

// ---- checkSetlistWrite, extended by admin-set-key ----

test('checkSetlistWrite changes the marker key with the setKey finder and cell writer before reading it back', () => {
  const fake = fakeSpreadsheet();
  assert.deepStrictEqual(call(fake, 'checkSetlistWrite'), { schemaVersion: 1, ok: true });
  // The marker row is appended (Bbm), then only its tono cell is rewritten to F#m, format first.
  const keyOps = fake.log.filter((op) => op[2] === 2 && op[3] === 5);
  assert.deepStrictEqual(keyOps, [
    ['setNumberFormat', SETLIST_CHECK_TAB, 2, 5, '@'],
    ['setValue', SETLIST_CHECK_TAB, 2, 5, 'F#m'],
  ]);
  assert.ok(fake.log.some((op) => op[0] === 'setValues' && op[1] === SETLIST_CHECK_TAB && op[2] === 2 && op[3] === 1 && op[4][0][4] === 'Bbm'));
  assert.ok(fake.log.every((op) => op[1] === SETLIST_CHECK_TAB), 'another tab was touched');
  assert.ok(!(SETLIST_CHECK_TAB in fake.tabs));
});

test('checkSetlistWrite with a Sheet that drops the key write is internal_error and still deletes the tab', () => {
  const fake = fakeSpreadsheet({ lostValues: ['F#m'] });
  const svc = services();
  assertError(call(fake, 'checkSetlistWrite', {}, svc), 'internal_error');
  assert.ok(fake.log.some((op) => op[0] === 'setValue' && op[4] === 'F#m'), 'the key write was not attempted');
  assert.ok(!(SETLIST_CHECK_TAB in fake.tabs));
  assert.equal(svc.lock.releases, 1);
});

// ---- admin-adjust-lineup ----
function setSlotCount(fake, fields = {}, svc) {
  return call(fake, 'setSlotCount', Object.assign({ date: UPCOMING, songId: 'crossroads', instrument: 'guitar', count: 1 }, fields), svc);
}

const LINEUP_CASES = [
  ['last open guitar removed', ['', ''], 1, ['', '-'], 7],
  ['filled first guitar stays', ['Martin', ''], 1, ['Martin', '-'], 7],
  ['filled second guitar stays', ['', 'Pedro'], 1, ['-', 'Pedro'], 6],
  ['first absent guitar restored', ['-', '-'], 1, ['', '-'], 6],
  ['second absent guitar restored', ['', '-'], 2, ['', ''], 7],
  ['restore before second filled guitar', ['-', 'Pedro'], 2, ['', 'Pedro'], 6],
];
for (const [label, guitars, count, expected, column] of LINEUP_CASES) {
  test('setSlotCount ' + label + ', only the changed cell as plain text', () => {
    const slots = guitars.concat(['Bass name', '', '', '', '']);
    const fake = fakeSpreadsheet({ tabs: { [UPCOMING]: [TAB_HEADER, row('1', 'crossroads', 'Crossroads', 'Eric', 'A', slots, 'Guest: sax')] } });
    const before = fake.tabs[UPCOMING].getDataRange().getDisplayValues();
    const body = setSlotCount(fake, { count });
    assert.equal(body.ok, true);
    assert.deepStrictEqual([body.slots.guitar1, body.slots.guitar2], expected.map(x => x === '' ? null : x));
    assert.deepStrictEqual(writes(fake.log), [
      ['setNumberFormat', UPCOMING, 2, column, '@'],
      ['setValue', UPCOMING, 2, column, expected[column - 6]],
    ]);
    const after = fake.tabs[UPCOMING].getDataRange().getDisplayValues();
    before[1][column - 1] = after[1][column - 1];
    assert.deepStrictEqual(after, before);
    assert.ok(!fake.requested.includes('Catalogo'));
  });
}

test('setSlotCount filled slots refused without any partial writes', () => {
  for (const guitars of [['Martin', 'Pedro'], ['Martin', '']]) {
    const fake = fakeSpreadsheet({ tabs: { [UPCOMING]: [TAB_HEADER, row('1', 'crossroads', 'Crossroads', 'Eric', 'A', guitars.concat(['', '', '', '', '']))] } });
    assertError(setSlotCount(fake, { count: 0 }), 'slot_filled');
    assert.deepStrictEqual(writes(fake.log), []);
  }
});

test('setSlotCount remove and restore every default instrument; same count is no-op', () => {
  const instruments = { guitar: ['guitar1', 'guitar2'], bass: ['bass'], drums: ['drums'], vocals: ['vocals'], harmonica: ['harmonica'], keyboards: ['keyboards'] };
  for (const [instrument, fields] of Object.entries(instruments)) {
    const fake = fakeSpreadsheet({ tabs: { [UPCOMING]: [TAB_HEADER, row('1', 'crossroads', 'Crossroads', 'Eric', 'A')] } });
    assert.equal(setSlotCount(fake, { instrument, count: fields.length }).ok, true);
    assert.deepStrictEqual(writes(fake.log), []);
    const removed = setSlotCount(fake, { instrument, count: 0 });
    fields.forEach(field => assert.equal(removed.slots[field], '-'));
    const restored = setSlotCount(fake, { instrument, count: fields.length });
    fields.forEach(field => assert.equal(restored.slots[field], null));
  }
});

test('setSlotCount validates in order and writes nothing on failures', () => {
  const cases = [
    [{ date: '2026-02-30', songId: 'BAD', instrument: 'kazoo', count: -1 }, 'invalid_date'],
    [{ songId: 'BAD', instrument: 'kazoo', count: -1 }, 'invalid_song'],
    ...[undefined, 'kazoo', 'Guitar', '__proto__', 7].map(instrument => [{ instrument, count: -1 }, 'invalid_instrument']),
    ...[undefined, '1', null, true, -1, 3, 1.5, NaN, Infinity].map(count => [{ count, date: '1999-01-01' }, 'invalid_count']),
    [{ instrument: 'bass', count: 2 }, 'invalid_count'],
    [{ date: '1999-01-01' }, 'unknown_jam'],
    [{ date: PAST }, 'jam_not_editable'],
    [{ date: LATER }, 'jam_not_editable'],
    [{}, 'duplicate_date', { jams: JAMS.concat([[UPCOMING, '22:00', 'Other', 'BORRADOR']]) }],
    [{}, 'song_not_in_setlist', { tabs: { [UPCOMING]: null } }],
    [{}, 'missing_header', { tabs: { [UPCOMING]: [TAB_HEADER.filter(h => h !== 'Bajo')] } }],
    [{}, 'duplicate_header', { tabs: { [UPCOMING]: [TAB_HEADER.concat(['Bajo'])] } }],
    [{}, 'song_not_in_setlist'],
    [{ songId: 'the-thrill-is-gone' }, 'duplicate_song', { tabs: { [UPCOMING]: UPCOMING_TAB.concat([UPCOMING_TAB[1]]) } }],
    [{ songId: 'the-thrill-is-gone', count: 0 }, 'slot_filled'],
  ];
  for (const [fields, code, options = {}] of cases) {
    const fake = fakeSpreadsheet(options);
    const svc = services();
    assertError(setSlotCount(fake, fields, svc), code);
    assert.deepStrictEqual(writes(fake.log), [], code);
    assert.ok(!fake.requested.includes('Catalogo'));
    assert.equal(svc.lock.tries, svc.lock.releases);
  }
});

test('setSlotCount uses mapped headers and id after removal renumber, GET mirrors published slots', () => {
  const header = TAB_HEADER.slice().reverse();
  const song = row('2', 'crossroads', 'Crossroads', 'Eric', 'A').reverse();
  const earlier = row('1', 'red-house', 'Red House', 'Jimi', 'A').reverse();
  const fake = fakeSpreadsheet({ jams: JAMS.map(r => r[0] === UPCOMING ? [UPCOMING, '21:00', 'Place', 'PUBLICADA'] : r), tabs: { [UPCOMING]: [header, earlier, song] } });
  assert.equal(call(fake, 'removeSong', { date: UPCOMING, songId: 'red-house' }).ok, true);
  fake.log.length = 0;
  const result = setSlotCount(fake, { instrument: 'harmonica', count: 0 });
  assert.equal(result.slots.harmonica, '-');
  const get = handleGet({ resource: 'jams' }, fake.spreadsheet);
  const jam = get.jams.find(j => j.date === UPCOMING);
  assert.equal(jam.setlist[0].slots.harmonica, '-');
  assert.equal(jam.setlist[0].songId, 'crossroads');
  assert.equal(jam.setlist[0].position, '1');
  assert.deepStrictEqual(writes(fake.log), [['setNumberFormat', UPCOMING, 2, header.indexOf('Armónica') + 1, '@'], ['setValue', UPCOMING, 2, header.indexOf('Armónica') + 1, '-']]);
});

test('checkSetlistWrite exercises guitar removal and harmonica removal then restore', () => {
  const fake = fakeSpreadsheet();
  assert.equal(call(fake, 'checkSetlistWrite').ok, true);
  const values = fake.log.filter(op => op[0] === 'setValue' && (op[3] === 7 || op[3] === 11));
  assert.deepStrictEqual(values.map(op => [op[3], op[4]]), [[7, '-'], [11, '-'], [11, '']]);
});

test('checkSetlistWrite catches a dropped slot write and cleans up', () => {
  const fake = fakeSpreadsheet({ lostValues: ['-'] });
  assertError(call(fake, 'checkSetlistWrite'), 'internal_error');
  assert.ok(!(SETLIST_CHECK_TAB in fake.tabs));
});

test('setSlotCount rereads trimmed display cells including names assigned during the write', () => {
  const fake = fakeSpreadsheet({ tabs: { [UPCOMING]: [TAB_HEADER, row('1', 'crossroads', 'Crossroads', 'Eric', 'A')] } });
  const sheet = fake.tabs[UPCOMING];
  const getRange = sheet.getRange;
  sheet.getRange = function (...args) {
    const range = getRange(...args);
    const setValue = range.setValue;
    range.setValue = function (value) {
      setValue(value);
      sheet.cells[1][7].value = '  Concurrent bass  ';
    };
    return range;
  };
  const result = setSlotCount(fake);
  assert.equal(result.slots.bass, 'Concurrent bass');
  assert.equal(result.slots.guitar2, '-');
  assert.equal(writes(fake.log).length, 2);
});

// ---- admin-assign-musician ----
function assignSlot(fake, fields = {}, svc) {
  return call(fake, 'assignSlot', Object.assign({ date: UPCOMING, songId: 'crossroads', instrument: 'guitar', ordinal: 1, name: 'Tincho' }, fields), svc);
}

test('assignSlot resolves ordinal among active U1 columns and writes one normalized plain-text cell', () => {
  const fake = fakeSpreadsheet({ tabs: { [UPCOMING]: [TAB_HEADER, row('1', 'crossroads', 'Crossroads', 'Eric', 'A', ['-', '', '', '', '', '', ''])] } });
  assert.deepStrictEqual(assignSlot(fake, { name: '  Zoë   Núñez  ' }), { schemaVersion: 1, ok: true, column: 1, name: 'Zoë Núñez' });
  assert.deepStrictEqual(writes(fake.log), [
    ['setNumberFormat', UPCOMING, 2, 7, '@'],
    ['setValue', UPCOMING, 2, 7, 'Zoë Núñez'],
  ]);
  assert.equal(fake.tabs[UPCOMING].getDataRange().getDisplayValues()[1][6], 'Zoë Núñez');
  assert.ok(!fake.requested.includes('Catalogo'));
});

test('assignSlot refuses occupied, missing active ordinal and invalid names without writes', () => {
  const cases = [
    [{ instrument: 'guitar', ordinal: 1 }, ['Martin', ''], 'slot_taken'],
    [{ instrument: 'guitar', ordinal: 2 }, ['', '-'], 'slot_not_in_lineup'],
    ...['', '   ', 'x'.repeat(41), 'no;pe', 'hola (che)', '=1+1', '+hola', '-hola', '@hola', '\u0001hola', 'Ana\tMaria', '---'].map(name => [{ name }, ['', ''], 'invalid_name']),
  ];
  for (const [fields, guitars, code] of cases) {
    const fake = fakeSpreadsheet({ tabs: { [UPCOMING]: [TAB_HEADER, row('1', 'crossroads', 'Crossroads', 'Eric', 'A', guitars.concat(['', '', '', '', '']))] } });
    assertError(assignSlot(fake, fields), code);
    assert.deepStrictEqual(writes(fake.log), [], code);
  }
});

test('assignSlot validates inputs in deterministic order before jam or tab access', () => {
  const cases = [
    [{ date: '2026-02-30', songId: 'BAD', instrument: 'kazoo', ordinal: 0, name: '' }, 'invalid_date'],
    [{ songId: 'BAD', instrument: 'kazoo', ordinal: 0, name: '' }, 'invalid_song'],
    [{ instrument: 'kazoo', ordinal: 0, name: '' }, 'invalid_slot'],
    [{ instrument: 'guitar', ordinal: 0, name: '' }, 'invalid_slot'],
    [{ instrument: 'guitar', ordinal: 3, name: '' }, 'invalid_slot'],
    [{ date: '1999-01-01', name: '' }, 'invalid_name'],
    [{ date: '1999-01-01' }, 'unknown_jam'],
    [{ date: PAST }, 'jam_not_editable'],
    [{}, 'duplicate_date', { jams: JAMS.concat([[UPCOMING, '22:00', 'Other', 'BORRADOR']]) }],
    [{}, 'song_not_in_setlist', { tabs: { [UPCOMING]: null } }],
    [{}, 'missing_header', { tabs: { [UPCOMING]: [TAB_HEADER.filter(h => h !== 'Bajo')] } }],
    [{}, 'duplicate_header', { tabs: { [UPCOMING]: [TAB_HEADER.concat(['Bajo'])] } }],
    [{ songId: 'missing-song' }, 'song_not_in_setlist'],
    [{ songId: 'the-thrill-is-gone' }, 'duplicate_song', { tabs: { [UPCOMING]: UPCOMING_TAB.concat([UPCOMING_TAB[1]]) } }],
  ];
  for (const [fields, code, options = {}] of cases) {
    const fake = fakeSpreadsheet(options);
    const svc = services();
    assertError(assignSlot(fake, fields, svc), code);
    assert.deepStrictEqual(writes(fake.log), [], code);
    assert.ok(!fake.requested.includes('Catalogo'));
    assert.equal(svc.lock.tries, svc.lock.releases);
  }
});

test('assignSlot uses mapped headers and does not overwrite occupied cells', () => {
  const header = TAB_HEADER.slice().reverse();
  const song = row('2', 'crossroads', 'Crossroads', 'Eric', 'A', ['-', '', '', '', '', '', '']).reverse();
  const fake = fakeSpreadsheet({ tabs: { [UPCOMING]: [header, song] } });
  assert.deepStrictEqual(assignSlot(fake, { ordinal: 1, name: 'Mora' }), { schemaVersion: 1, ok: true, column: 1, name: 'Mora' });
  assert.equal(fake.tabs[UPCOMING].getDataRange().getDisplayValues()[1][header.indexOf('Guitarra 2')], 'Mora');
  assert.deepStrictEqual(writes(fake.log), [
    ['setNumberFormat', UPCOMING, 2, header.indexOf('Guitarra 2') + 1, '@'],
    ['setValue', UPCOMING, 2, header.indexOf('Guitarra 2') + 1, 'Mora'],
  ]);
});

test('assignSlot is an authenticated locked write and checkSetlistWrite proves assignment on its temporary tab', () => {
  const fake = fakeSpreadsheet();
  const svc = services();
  const result = call(fake, 'checkSetlistWrite', {}, svc);
  assert.deepStrictEqual(result, { schemaVersion: 1, ok: true });
  assert.ok(fake.log.some(op => op[0] === 'setValue' && op[1] === SETLIST_CHECK_TAB && op[4] === 'Prueba Musico'));
  assert.ok(fake.log.every(op => op[1] === SETLIST_CHECK_TAB), 'a real tab was touched');
  assert.ok(!(SETLIST_CHECK_TAB in fake.tabs));
  assert.equal(ACTIONS.assignSlot.write, true);

  const denied = fakeSpreadsheet();
  assertError(handlePost({ action: 'assignSlot', date: UPCOMING, songId: 'crossroads', instrument: 'guitar', ordinal: 1, name: 'Tincho' }, denied.spreadsheet, services()), 'invalid_passphrase');
  assert.deepStrictEqual(writes(denied.log), []);
  const busy = services();
  busy.lock.tryLock = () => false;
  assertError(assignSlot(denied, {}, busy), 'busy');
  assert.deepStrictEqual(writes(denied.log), []);
});

test('checkSetlistWrite detects a dropped assignment and still cleans up its temporary tab', () => {
  const fake = fakeSpreadsheet({ lostValues: ['Prueba Musico'] });
  assertError(call(fake, 'checkSetlistWrite'), 'internal_error');
  assert.ok(fake.log.some(op => op[0] === 'setValue' && op[1] === SETLIST_CHECK_TAB && op[4] === 'Prueba Musico'));
  assert.ok(!(SETLIST_CHECK_TAB in fake.tabs));
});

// ---- admin-clear-slot ----
function clearSlot(fake, fields = {}, svc) {
  return call(fake, 'clearSlot', Object.assign({ date: UPCOMING, songId: 'crossroads', instrument: 'guitar', ordinal: 1, expectedName: 'Tincho' }, fields), svc);
}

test('clearSlot compare-and-clears only its occupied U1 cell and returns canonical slots', () => {
  const fake = fakeSpreadsheet({ tabs: { [UPCOMING]: [TAB_HEADER, row('1', 'crossroads', 'Crossroads', 'Eric', 'A', ['Tincho', 'Mora', '', '', '', '', ''])] } });
  const result = clearSlot(fake);
  assert.equal(result.ok, true);
  assert.equal(result.column, 0);
  assert.equal(result.slots.guitar1, null);
  assert.equal(result.slots.guitar2, 'Mora');
  assert.deepStrictEqual(writes(fake.log), [
    ['setNumberFormat', UPCOMING, 2, 6, '@'],
    ['setValue', UPCOMING, 2, 6, ''],
  ]);
});

test('clearSlot respects active ordinal after removed guitar column and reordered headers', () => {
  const header = TAB_HEADER.slice().reverse();
  const song = row('2', 'crossroads', 'Crossroads', 'Eric', 'A', ['-', 'Mora', '', '', '', '', '']).reverse();
  const fake = fakeSpreadsheet({ tabs: { [UPCOMING]: [header, song] } });
  const result = clearSlot(fake, { expectedName: 'Mora' });
  assert.equal(result.column, 1);
  assert.equal(fake.tabs[UPCOMING].getDataRange().getDisplayValues()[1][header.indexOf('Guitarra 2')], '');
  assert.deepStrictEqual(writes(fake.log).map(op => op.slice(0, 5)), [
    ['setNumberFormat', UPCOMING, 2, header.indexOf('Guitarra 2') + 1, '@'],
    ['setValue', UPCOMING, 2, header.indexOf('Guitarra 2') + 1, ''],
  ]);
});

test('clearSlot leaves empty, changed, absent, malformed, and invalid targets untouched', () => {
  const cases = [
    [{}, ['', ''], 'slot_empty'],
    [{}, ['Otra', ''], 'slot_changed'],
    [{ ordinal: 2 }, ['-', ''], 'slot_not_in_lineup'],
    [{ expectedName: '' }, ['Tincho', ''], 'invalid_name'],
    [{ expectedName: '\u0001' }, ['Tincho', ''], 'invalid_name'],
  ];
  for (const [fields, guitars, code] of cases) {
    const fake = fakeSpreadsheet({ tabs: { [UPCOMING]: [TAB_HEADER, row('1', 'crossroads', 'Crossroads', 'Eric', 'A', guitars.concat(['', '', '', '', '']))] } });
    assertError(clearSlot(fake, fields), code);
    assert.deepStrictEqual(writes(fake.log), [], code);
  }
});

test('clearSlot validates inputs before accessing jam tabs and is authenticated under lock', () => {
  const cases = [
    [{ date: '2026-02-30', songId: 'BAD', instrument: 'kazoo', ordinal: 0, expectedName: '' }, 'invalid_date'],
    [{ songId: 'BAD', instrument: 'kazoo', ordinal: 0, expectedName: '' }, 'invalid_song'],
    [{ instrument: 'kazoo', ordinal: 0 }, 'invalid_slot'],
    [{ ordinal: 0 }, 'invalid_slot'],
    [{ date: '1999-01-01' }, 'unknown_jam'],
    [{ date: PAST }, 'jam_not_editable'],
  ];
  for (const [fields, code] of cases) {
    const fake = fakeSpreadsheet();
    assertError(clearSlot(fake, fields), code);
    assert.deepStrictEqual(writes(fake.log), [], code);
  }
  const denied = fakeSpreadsheet();
  assertError(handlePost({ action: 'clearSlot' }, denied.spreadsheet, services()), 'invalid_passphrase');
  assert.deepStrictEqual(writes(denied.log), []);
  const busy = services();
  busy.lock.tryLock = () => false;
  const fake = fakeSpreadsheet();
  assertError(clearSlot(fake, {}, busy), 'busy');
  assert.deepStrictEqual(writes(fake.log), []);
  assert.equal(ACTIONS.clearSlot.write, true);
});

test('checkSetlistWrite proves clearSlot and always deletes the temporary tab', () => {
  const fake = fakeSpreadsheet();
  assert.deepStrictEqual(call(fake, 'checkSetlistWrite'), { schemaVersion: 1, ok: true });
  assert.ok(fake.log.some(op => op[0] === 'setValue' && op[1] === SETLIST_CHECK_TAB && op[4] === ''));
  assert.ok(fake.log.every(op => op[1] === SETLIST_CHECK_TAB));
  assert.ok(!(SETLIST_CHECK_TAB in fake.tabs));
  const failed = fakeSpreadsheet({ lostCells: [{ sheet: SETLIST_CHECK_TAB, row: 2, column: 6, value: '' }] });
  assertError(call(failed, 'checkSetlistWrite'), 'internal_error');
  assert.ok(!(SETLIST_CHECK_TAB in failed.tabs));
});

test('setSlotCount needs passphrase and write lock; neither refusal writes', () => {
  const fake = fakeSpreadsheet();
  assertError(handlePost({ action: 'setSlotCount', date: UPCOMING, songId: 'crossroads', instrument: 'guitar', count: 1 }, fake.spreadsheet, services()), 'invalid_passphrase');
  assert.deepStrictEqual(fake.requested, ['Config']);
  const svc = services();
  svc.lock.tryLock = () => false;
  assertError(setSlotCount(fake, {}, svc), 'busy');
  assert.deepStrictEqual(writes(fake.log), []);
  assert.equal(ACTIONS.setSlotCount.write, true);
});
