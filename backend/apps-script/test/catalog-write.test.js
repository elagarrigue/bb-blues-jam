'use strict';
// admin-catalog-tab (D-30): createSong, updateSong, deleteSong and checkCatalogWrite, through
// handlePost (behind the guard and the lock), against a fake spreadsheet of real cell grids that
// logs every write.

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { loadScript, SRC_DIR } = require('./helpers/load.js');
const { Utilities, BUENOS_AIRES } = require('./helpers/format.js');

const { handlePost, ACTIONS, CATALOG_CHECK_TAB, songSlug_ } = loadScript();
globalThis.Utilities = Utilities;

const STORED = 'Blues-Pass 42';
const NOW = Date.UTC(2026, 9, 6, 15, 0, 0);
const UPCOMING = '2026-10-31';
const LATER = '2026-11-28';
const PAST = '2026-07-25';
const WRITE_OPS = ['insertSheet', 'deleteSheet', 'setName', 'setValues', 'setValue', 'setNumberFormat', 'deleteRow', 'insertRowAfter'];
const TAB_HEADER = ['posicion', 'id_tema', 'titulo', 'artista', 'tono', 'Guitarra 1', 'Guitarra 2', 'Bajo', 'Batería', 'Voz', 'Armónica', 'Teclados', 'Otros'];
const CATALOG_HEADER = ['id', 'titulo', 'artista', 'tono_default', 'tempo', 'etiquetas', 'dificultad', 'songsterr_id'];

const JAMS = [
  ['fecha', 'hora', 'lugar', 'estado'],
  [PAST, '21:00', 'La Macanuda', 'PUBLICADA'],
  [UPCOMING, '21:00', 'La Macanuda', 'BORRADOR'],
  [LATER, '21:00', 'La Macanuda', 'BORRADOR'],
];
const CATALOG = [
  CATALOG_HEADER,
  ['crossroads', 'Crossroads', 'Eric Clapton', 'A', 'rapido', 'clasico', 'facil', '123'],
  ['the-thrill-is-gone', 'The Thrill Is Gone', 'B.B. King', 'Bm', '', '', '', ''],
  ['  siete-cuartos ', '7/4', 'Prueba', 'C', '', '', '', ''],
  ['only-past', 'Only Past', 'Ana', 'E', '', '', '', ''],
  ['repetido', 'Uno', 'X', 'A', '', '', '', ''],
  ['repetido', 'Dos', 'Y', 'A', '', '', '', ''],
];
const tab = (...ids) => [TAB_HEADER].concat(ids.map((id, i) => [String(i + 1), id, id, 'X', 'A', '', '', '', '', '', '', '', '']));

function gridSheet(name, rows, log, opts = {}) {
  const cells = rows.map((r) => r.map((value) => ({ value, format: '' })));
  const cell = (r, c) => {
    while (cells.length < r) cells.push([]);
    const line = cells[r - 1];
    while (line.length < c) line.push({ value: '', format: '' });
    return line[c - 1];
  };
  const lastRow = () => {
    for (let r = cells.length; r >= 1; r--) if (cells[r - 1].some((x) => x.value !== '')) return r;
    return 0;
  };
  const lastColumn = () => Math.max(0, ...cells.map((line) => {
    for (let c = line.length; c >= 1; c--) if (line[c - 1].value !== '') return c;
    return 0;
  }));
  const shown = (x) => (x.value instanceof Date ? 'DATE' : String(x.value));
  const store = (target, value) => {
    const converts = opts.convertAlways || target.format !== '@';
    target.value = converts && /^\d+\/\d+$/.test(String(value)) ? new Date(0) : value;
  };
  return {
    cells,
    getLastRow: lastRow,
    getLastColumn: lastColumn,
    deleteRow(r) {
      log.push(['deleteRow', name, r]);
      if (!opts.brokenDelete) cells.splice(r - 1, 1);
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
          values.forEach((line, i) => line.forEach((value, j) => store(cell(r + i, c + j), value)));
        },
        setValue(value) {
          log.push(['setValue', name, r, c, value]);
          if (opts.lostValues && opts.lostValues.includes(value)) return;
          store(cell(r, c), value);
        },
        setNumberFormat(format) {
          log.push(['setNumberFormat', name, r, c, format]);
          for (let i = 0; i < numRows; i++) for (let j = 0; j < numCols; j++) cell(r + i, c + j).format = format;
        },
        getDisplayValues() {
          return Array.from({ length: numRows }, (_, i) => Array.from({ length: numCols }, (__, j) => shown(cell(r + i, c + j))));
        },
      };
    },
  };
}

function fakeSpreadsheet({ jams = JAMS, catalog = CATALOG, tabs = {}, ...opts } = {}) {
  const log = [];
  const requested = [];
  const all = {
    Config: gridSheet('Config', [['clave', 'valor'], ['passphrase', STORED]], log),
    Jams: gridSheet('Jams', jams, log),
    Catalogo: gridSheet('Catalogo', catalog, log, opts),
    [PAST]: gridSheet(PAST, tab('crossroads', 'past-only-extra'), log),
    [UPCOMING]: gridSheet(UPCOMING, tab('the-thrill-is-gone'), log),
    [LATER]: gridSheet(LATER, tab('held-back-song'), log),
  };
  for (const [name, rows] of Object.entries(tabs)) {
    if (rows === null) delete all[name];
    else all[name] = gridSheet(name, rows, log);
  }
  const spreadsheet = {
    getSheetByName(name) {
      requested.push(name);
      return all[name] || null;
    },
    getSpreadsheetTimeZone: () => BUENOS_AIRES,
    insertSheet(name) {
      log.push(['insertSheet', name]);
      all[name] = gridSheet(name, [], log, opts);
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

function services() {
  const store = new Map();
  return {
    cache: { get: (k) => store.get(k) || null, put: (k, v) => store.set(k, v) },
    lock: { tryLock: () => true, releaseLock() {} },
    now: NOW,
    flush() {},
  };
}

const call = (fake, action, fields = {}) => handlePost(Object.assign({ action, passphrase: STORED }, fields), fake.spreadsheet, services());
const create = (fake, fields) => call(fake, 'createSong', Object.assign({ title: 'Red House', artist: 'Jimi Hendrix', defaultKey: 'Bb' }, fields));
const update = (fake, fields) => call(fake, 'updateSong', Object.assign({ id: 'crossroads', title: 'Crossroads', artist: 'Eric Clapton', defaultKey: 'A' }, fields));
const writes = (log) => log.filter((op) => WRITE_OPS.includes(op[0]));
const catalogRows = (fake) => fake.tabs.Catalogo.getDataRange().getDisplayValues();

function assertError(body, code) {
  assert.deepStrictEqual(Object.keys(body), ['schemaVersion', 'error'], JSON.stringify(body));
  assert.equal(body.error.code, code, JSON.stringify(body));
}

test('songSlug_ vectors', () => {
  assert.equal(songSlug_('Café Madrid'), 'cafe-madrid');
  assert.equal(songSlug_('Ñandú'), 'nandu');
  assert.equal(songSlug_('7/4'), '7-4');
  assert.equal(songSlug_('  The  Thrill, Is Gone!! '), 'the-thrill-is-gone');
  assert.equal(songSlug_('!!!'), '');
});

test('the router lists the four catalog actions as guarded writes', () => {
  for (const name of ['createSong', 'updateSong', 'deleteSong', 'checkCatalogWrite']) {
    assert.equal(ACTIONS[name].write, true, name);
  }
  const unknown = handlePost({ action: 'deployProbe' }, fakeSpreadsheet().spreadsheet, services());
  assert.ok(unknown.error.message.endsWith('createSong, updateSong, deleteSong, checkCatalogWrite'), unknown.error.message);
  const fake = fakeSpreadsheet();
  assertError(handlePost({ action: 'createSong', title: 'A', artist: 'B', defaultKey: 'A' }, fake.spreadsheet, services()), 'invalid_passphrase');
  assert.deepStrictEqual(writes(fake.log), []);
});

test('Post.js holds the lines the migration guide tells the user to look for', () => {
  const source = fs.readFileSync(path.join(SRC_DIR, 'Post.js'), 'utf8');
  assert.ok(source.includes('createSong: { write: true, run: createSong_ },'));
  assert.ok(source.includes('deleteSong: { write: true, run: deleteSong_ },'));
  assert.ok(source.includes('var CATALOG_CHECK_TAB'));
  assert.ok(source.includes('function songSlug_'));
});

test('createSong appends one row with id, titulo, artista and tono_default only', () => {
  const fake = fakeSpreadsheet();
  const body = create(fake);
  assert.deepStrictEqual(body, { schemaVersion: 1, ok: true, song: { id: 'red-house', title: 'Red House', artist: 'Jimi Hendrix', defaultKey: 'Bb' } });
  const rows = catalogRows(fake);
  assert.equal(rows.length, CATALOG.length + 1);
  assert.deepStrictEqual(rows[rows.length - 1], ['red-house', 'Red House', 'Jimi Hendrix', 'Bb', '', '', '', '']);
  const touched = writes(fake.log).filter((op) => op[0] === 'setValue').map((op) => op[3]);
  assert.deepStrictEqual([...new Set(touched)].sort(), [1, 2, 3, 4]);
  assert.ok(writes(fake.log).every((op) => op[1] === 'Catalogo'));
});

test('createSong ignores a client id and normalizes text', () => {
  const fake = fakeSpreadsheet();
  const body = create(fake, { id: 'evil', title: '  Café   Madrid ', artist: 'Ñandú' });
  assert.equal(body.song.id, 'cafe-madrid');
  assert.equal(body.song.title, 'Café Madrid');
});

test('createSong refusals write nothing', () => {
  const cases = [
    [{ title: 5 }, 'invalid_title'], [{ title: '   ' }, 'invalid_title'], [{ title: 'x'.repeat(101) }, 'invalid_title'],
    [{ title: '=SUM(1)' }, 'invalid_title'], [{ title: '+1' }, 'invalid_title'], [{ title: '-x' }, 'invalid_title'], [{ title: '@x' }, 'invalid_title'],
    [{ title: 'a\u0007b' }, 'invalid_title'], [{ title: '!!!' }, 'invalid_title'],
    [{ artist: undefined }, 'invalid_artist'], [{ artist: '=x' }, 'invalid_artist'],
    [{ defaultKey: 'H' }, 'invalid_key'], [{ defaultKey: 'a' }, 'invalid_key'], [{ defaultKey: 4 }, 'invalid_key'],
    [{ title: 'crossroads', artist: 'ERIC  clapton' }, 'song_exists'],
    [{ title: 'Cróssroads', artist: 'Eric Clapton' }, 'song_exists'],
  ];
  for (const [fields, code] of cases) {
    const fake = fakeSpreadsheet();
    assertError(create(fake, fields), code);
    assert.deepStrictEqual(writes(fake.log), [], code);
  }
  const noTab = fakeSpreadsheet();
  delete noTab.tabs.Catalogo;
  assertError(create(noTab), 'missing_tab');
  const badHeader = fakeSpreadsheet({ catalog: [['id', 'titulo']] });
  assertError(create(badHeader), 'missing_header');
  const dup = fakeSpreadsheet({ catalog: [CATALOG_HEADER.concat(['id'])] });
  assertError(create(dup), 'duplicate_header');
  assert.deepStrictEqual(writes(badHeader.log), []);
});

test('createSong: title clash adds the artist, then a numeric suffix', () => {
  const fake = fakeSpreadsheet();
  assert.equal(create(fake, { title: 'Crossroads', artist: 'Cream' }).song.id, 'crossroads-cream');
  const again = fakeSpreadsheet({ catalog: CATALOG.concat([['crossroads-cream', 'Zzz', 'Zzz', 'A', '', '', '', '']]) });
  assert.equal(create(again, { title: 'Crossroads', artist: 'Cream' }).song.id, 'crossroads-cream-2');
  const third = fakeSpreadsheet({ catalog: CATALOG.concat([['crossroads-cream', 'Zzz', 'Zzz', 'A', '', '', '', ''], ['crossroads-cream-2', 'Yyy', 'Yyy', 'A', '', '', '', '']]) });
  assert.equal(create(third, { title: 'Crossroads', artist: 'Cream' }).song.id, 'crossroads-cream-3');
});

test('createSong never reuses an id found in a jam tab, past included, or in an invalid Catalogo row', () => {
  const fake = fakeSpreadsheet({ tabs: { [PAST]: tab('red-house') } });
  assert.equal(create(fake).song.id, 'red-house-jimi-hendrix');
  const hidden = fakeSpreadsheet({ catalog: CATALOG.concat([['red-house', '', '', '', '', '', '', '']]) });
  assert.equal(create(hidden).song.id, 'red-house-jimi-hendrix');
});

test('updateSong writes only the changed cells, never id or the Sheet-only columns', () => {
  const fake = fakeSpreadsheet();
  const before = catalogRows(fake);
  const body = update(fake, { artist: 'Cream' });
  assert.deepStrictEqual(body, { schemaVersion: 1, ok: true, song: { id: 'crossroads', title: 'Crossroads', artist: 'Cream', defaultKey: 'A' }, changed: true });
  const after = catalogRows(fake);
  assert.deepStrictEqual(after[1], ['crossroads', 'Crossroads', 'Cream', 'A', 'rapido', 'clasico', 'facil', '123']);
  assert.deepStrictEqual(after.slice(2), before.slice(2));
  const cols = writes(fake.log).filter((op) => op[0] === 'setValue').map((op) => op[3]);
  assert.deepStrictEqual(cols, [3]);
});

test('updateSong with equal values is changed:false and writes nothing; a key-only change writes one cell', () => {
  const fake = fakeSpreadsheet();
  const same = update(fake, { title: ' Crossroads ' });
  assert.equal(same.changed, false);
  assert.deepStrictEqual(writes(fake.log), []);
  const key = update(fake, { defaultKey: 'B' });
  assert.equal(key.changed, true);
  assert.equal(catalogRows(fake)[1][3], 'B');
});

test('updateSong refusals write nothing', () => {
  const cases = [
    [{ id: 'Bad Id' }, 'invalid_song'], [{ id: 5 }, 'invalid_song'], [{ id: 'nope' }, 'unknown_song'], [{ id: 'repetido' }, 'duplicate_id'],
    [{ title: '' }, 'invalid_title'], [{ artist: '@x' }, 'invalid_artist'], [{ defaultKey: 'Z' }, 'invalid_key'],
    [{ title: 'The Thrill Is Gone', artist: 'b.b. KING' }, 'song_exists'],
  ];
  for (const [fields, code] of cases) {
    const fake = fakeSpreadsheet();
    assertError(update(fake, fields), code);
    assert.deepStrictEqual(writes(fake.log), [], code);
  }
});

test('updateSong may keep its own title and artist (excludes the edited row from song_exists)', () => {
  const fake = fakeSpreadsheet();
  assert.equal(update(fake, { title: 'CROSSROADS', defaultKey: 'E' }).changed, true);
});

test('deleteSong refuses a song in the upcoming or a held-back jam and names the dates', () => {
  for (const [id, date] of [['the-thrill-is-gone', UPCOMING], ['held-back-song', LATER]]) {
    const fake = fakeSpreadsheet({ catalog: id === 'held-back-song' ? CATALOG.concat([[id, 'T', 'A', 'A', '', '', '', '']]) : CATALOG });
    const body = call(fake, 'deleteSong', { id });
    assertError(body, 'song_in_future_jam');
    assert.ok(body.error.message.includes(date), body.error.message);
    assert.deepStrictEqual(writes(fake.log), []);
  }
});

test('deleteSong deletes a song that is only in past jams and answers its id', () => {
  const fake = fakeSpreadsheet({ tabs: { [PAST]: tab('only-past') } });
  const before = catalogRows(fake).length;
  assert.deepStrictEqual(call(fake, 'deleteSong', { id: 'only-past' }), { schemaVersion: 1, ok: true, id: 'only-past' });
  const rows = catalogRows(fake);
  assert.equal(rows.length, before - 1);
  assert.ok(!rows.some((r) => r[0] === 'only-past'));
  assert.deepStrictEqual(writes(fake.log).map((op) => op[0]), ['deleteRow']);
});

test('deleteSong other refusals and a delete that did not happen', () => {
  for (const [id, code] of [['bad id', 'invalid_song'], ['nope', 'unknown_song'], ['repetido', 'duplicate_id']]) {
    const fake = fakeSpreadsheet();
    assertError(call(fake, 'deleteSong', { id }), code);
    assert.deepStrictEqual(writes(fake.log), []);
  }
  const broken = fakeSpreadsheet({ brokenDelete: true });
  assertError(call(broken, 'deleteSong', { id: 'only-past' }), 'internal_error');
});

test('deleteSong refuses when a future jam tab cannot be read', () => {
  const fake = fakeSpreadsheet({ tabs: { [UPCOMING]: [['foo']] } });
  assertError(call(fake, 'deleteSong', { id: 'only-past' }), 'missing_header');
  assert.deepStrictEqual(writes(fake.log), []);
});

test('create and update read-back failures are internal_error', () => {
  assertError(create(fakeSpreadsheet({ convertAlways: true }), { title: '7/4', artist: 'Otro' }), 'internal_error');
  assertError(update(fakeSpreadsheet({ lostValues: ['Cream'] }), { artist: 'Cream' }), 'internal_error');
});

test('checkCatalogWrite touches only the temporary tab, never Catalogo, and cleans up', () => {
  const fake = fakeSpreadsheet({ tabs: { [CATALOG_CHECK_TAB]: [['leftover']] } });
  const before = JSON.stringify(fake.tabs.Catalogo.cells);
  assert.deepStrictEqual(call(fake, 'checkCatalogWrite'), { schemaVersion: 1, ok: true });
  assert.ok(!fake.requested.includes('Catalogo'));
  assert.ok(writes(fake.log).every((op) => op[1] === CATALOG_CHECK_TAB));
  assert.equal(fake.tabs[CATALOG_CHECK_TAB], undefined);
  assert.equal(JSON.stringify(fake.tabs.Catalogo.cells), before);
  const broken = fakeSpreadsheet({ convertAlways: true });
  assertError(call(broken, 'checkCatalogWrite'), 'internal_error');
  assert.equal(broken.tabs[CATALOG_CHECK_TAB], undefined);
});
