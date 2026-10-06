'use strict';
// admin-add-song-to-setlist: the admin read `readJams`, the write `addSong` and the deploy check
// `checkSetlistWrite`, through handlePost (so behind the guard and, for writes, the lock), against a
// fake spreadsheet that holds real cell grids and logs every write.

const test = require('node:test');
const assert = require('node:assert/strict');
const { loadScript } = require('./helpers/load.js');
const { Utilities, BUENOS_AIRES } = require('./helpers/format.js');

const { handlePost, handleGet, ACTIONS, SETLIST_CHECK_TAB, buildSetlist } = loadScript();

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
const WRITE_OPS = ['insertSheet', 'deleteSheet', 'setValues', 'setValue', 'setNumberFormat'];

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
 */
function gridSheet(name, rows, log, convertAlways) {
  const cells = rows.map((r) => r.map((value) => ({ value, format: '' })));
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
              cell(r + i, c + j).value = value;
            });
          });
        },
        setValue(value) {
          log.push(['setValue', name, r, c, value]);
          const target = cell(r, c);
          const converts = convertAlways || target.format !== '@';
          target.value = converts && /^\d+\/\d+$/.test(String(value)) ? new Date(0) : value;
        },
        setNumberFormat(format) {
          log.push(['setNumberFormat', name, r, c, format]);
          cell(r, c).format = format;
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
function fakeSpreadsheet({ jams = JAMS, catalog = CATALOG, tabs = {}, convertAlways = false } = {}) {
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
      all[name] = gridSheet(name, [], log, convertAlways);
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
  return { cache: { get: (k) => store.get(k) || null, put: (k, v) => store.set(k, v) }, lock: fakeLock(), now };
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
  assert.equal(ops.length, 26);
  for (let i = 0; i < ops.length; i += 2) {
    assert.equal(ops[i][0], 'setNumberFormat');
    assert.equal(ops[i + 1][0], 'setValue');
    assert.deepStrictEqual(ops[i].slice(1, 4), ops[i + 1].slice(1, 4));
    assert.equal(ops[i][2], 5);
  }
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
  for (const action of ['readJams', 'addSong', 'checkSetlistWrite']) {
    const fake = fakeSpreadsheet();
    assertError(handlePost({ action, date: UPCOMING, songId: 'crossroads', key: 'A' }, fake.spreadsheet, services()), 'invalid_passphrase');
    assert.deepStrictEqual(fake.requested, ['Config']);
    assert.deepStrictEqual(writes(fake.log), []);
    assert.ok(Object.prototype.hasOwnProperty.call(ACTIONS, action));
  }
});
