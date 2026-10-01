'use strict';
// T2: the Catalogo tab as the catalog response, against the committed samples in docs/api-samples.

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { loadScript } = require('./helpers/load.js');
const { readCsv } = require('./helpers/csv.js');
const { checkCatalogResponse, SONG_KEYS } = require('./helpers/contract.js');
const { EDGE_DISPLAY, EDGE_RAW } = require('./helpers/edge-input.js');

const { buildCatalog, ContractError, SCHEMA_VERSION } = loadScript();

const REPO = path.join(__dirname, '..', '..', '..');
const SEED_CSV = path.join(REPO, 'docs', 'sheet-seed', 'Catalogo.csv');
const SEED_SAMPLE = path.join(REPO, 'docs', 'api-samples', 'catalog-seed.json');
const EDGE_SAMPLE = path.join(REPO, 'docs', 'api-samples', 'catalog-edge.json');
const OPTIONAL = ['tempo', 'tags', 'difficulty', 'songsterrId'];
const REQUIRED_HEADERS = ['id', 'titulo', 'artista', 'tono_default'];

function readJson(file) {
  return JSON.parse(fs.readFileSync(file, 'utf8'));
}

function response(rowsDisplay, rowsRaw) {
  return Object.assign({ schemaVersion: SCHEMA_VERSION }, buildCatalog(rowsDisplay, rowsRaw || rowsDisplay));
}

function assertContractError(fn, code) {
  assert.throws(fn, (err) => {
    assert.ok(err instanceof ContractError, `expected ContractError, got ${err}`);
    assert.equal(err.code, code);
    assert.equal(typeof err.message, 'string');
    return true;
  });
}

test('the seed Catalogo builds exactly docs/api-samples/catalog-seed.json', () => {
  const seed = readCsv(SEED_CSV);
  assert.deepStrictEqual(response(seed), readJson(SEED_SAMPLE));
});

test('every seed song has the 8 keys in order and its 4 optional fields null', () => {
  const seed = readCsv(SEED_CSV);
  const { songs } = buildCatalog(seed, seed);
  assert.equal(songs.length, seed.length - 1);
  for (const song of songs) {
    assert.deepStrictEqual(Object.keys(song), SONG_KEYS);
    for (const key of OPTIONAL) {
      assert.equal(song[key], null, `${song.id}.${key}`);
    }
  }
});

test('the edge input builds exactly docs/api-samples/catalog-edge.json', () => {
  assert.deepStrictEqual(response(EDGE_DISPLAY, EDGE_RAW), readJson(EDGE_SAMPLE));
});

test('typed and messy cells: raw integer, trimmed text, blank rows skipped, extra column ignored', () => {
  const { songs } = buildCatalog(EDGE_DISPLAY, EDGE_RAW);
  assert.deepStrictEqual(songs.map((s) => s.id), ['the-thrill-is-gone', 'crossroads', 'got-my-mojo-working', 'sin-tono']);
  assert.equal(songs[0].songsterrId, '12345');
  assert.equal(songs[0].title, 'The Thrill Is Gone');
  assert.equal(songs[2].title, 'Got My Mojo Working');
  for (const song of songs) {
    assert.ok(!('notas' in song));
  }
});

test('values stay in Sheet vocabulary: no enum, key or id validation in the script', () => {
  const { songs } = buildCatalog(EDGE_DISPLAY, EDGE_RAW);
  assert.equal(songs[2].tempo, 'rápido');
  assert.equal(songs[3].tempo, 'Rápido');
  assert.equal(songs[0].tags, 'slow blues, 12 compases');
  assert.equal(songs[3].defaultKey, null);
});

test('a missing optional column gives null for its field', () => {
  const rows = [REQUIRED_HEADERS, ['crossroads', 'Crossroads', 'Eric Clapton', 'A']];
  const { songs } = buildCatalog(rows, rows);
  assert.deepStrictEqual(songs, [
    { id: 'crossroads', title: 'Crossroads', artist: 'Eric Clapton', defaultKey: 'A', tempo: null, tags: null, difficulty: null, songsterrId: null },
  ]);
});

test('a short row reads its missing cells as empty', () => {
  const rows = [REQUIRED_HEADERS.concat(['tempo']), ['crossroads', 'Crossroads']];
  const { songs } = buildCatalog(rows, rows);
  assert.equal(songs[0].artist, null);
  assert.equal(songs[0].tempo, null);
});

test('a tab with only the header row gives an empty song list', () => {
  const rows = [REQUIRED_HEADERS];
  assert.deepStrictEqual(buildCatalog(rows, rows), { songs: [] });
});

test('a missing required header is missing_header and names it', () => {
  const rows = [['id', 'titulo', 'artista', 'tempo'], ['a', 'A', 'B', 'lento']];
  assertContractError(() => buildCatalog(rows, rows), 'missing_header');
  assert.throws(() => buildCatalog(rows, rows), /tono_default/);
});

test('a header that differs in case or accent does not match', () => {
  const rows = [['id', 'Titulo', 'artista', 'tono_default']];
  assertContractError(() => buildCatalog(rows, rows), 'missing_header');
});

test('an empty tab is missing_header', () => {
  assertContractError(() => buildCatalog([], []), 'missing_header');
  assertContractError(() => buildCatalog([['']], [['']]), 'missing_header');
});

test('a mapped header twice, even with padding, is duplicate_header; an unmapped one twice is fine', () => {
  const twice = [['id', 'titulo', 'artista', 'tono_default', ' id ']];
  assertContractError(() => buildCatalog(twice, twice), 'duplicate_header');
  const extra = [REQUIRED_HEADERS.concat(['notas', 'notas'])];
  assert.deepStrictEqual(buildCatalog(extra, extra), { songs: [] });
});

test('checkCatalogResponse accepts both committed samples', () => {
  assert.deepStrictEqual(checkCatalogResponse(readJson(SEED_SAMPLE)), []);
  assert.deepStrictEqual(checkCatalogResponse(readJson(EDGE_SAMPLE)), []);
});

test('checkCatalogResponse rejects a missing key, a number, an empty string, an extra field and an error body', () => {
  const sample = readJson(EDGE_SAMPLE);
  delete sample.songs[0].tempo;
  sample.songs[1].songsterrId = 12345;
  sample.songs[2].tags = '';
  sample.songs[3].mbid = 'x';
  const violations = checkCatalogResponse(sample);
  assert.equal(violations.length, 4, violations.join('\n'));
  assert.match(violations[0], /songs\[0\] keys: missing tempo/);
  assert.match(violations[1], /songs\[1\]\.songsterrId is number/);
  assert.match(violations[2], /songs\[2\]\.tags is an empty string/);
  assert.match(violations[3], /songs\[3\] keys: unexpected mbid/);
  const error = { schemaVersion: 1, error: { code: 'missing_tab', message: 'x' } };
  assert.ok(checkCatalogResponse(error).some((v) => /is an error: missing_tab/.test(v)));
});
