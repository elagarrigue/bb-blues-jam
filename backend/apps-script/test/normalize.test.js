'use strict';
// T1: cell normalization (docs/sheet-schema.md, Reading cells).

const test = require('node:test');
const assert = require('node:assert/strict');
const { loadScript } = require('./helpers/load.js');

const { isBlank, textCell, integerTextCell } = loadScript();

test('textCell trims surrounding whitespace, including NBSP and tabs', () => {
  assert.equal(textCell('  Crossroads  '), 'Crossroads');
  assert.equal(textCell(' Café Madrid '), 'Café Madrid');
  assert.equal(textCell('\tB.B. King\n'), 'B.B. King');
});

test('textCell keeps inner whitespace and Sheet vocabulary untouched', () => {
  assert.equal(textCell(' shuffle,  12 compases '), 'shuffle,  12 compases');
  assert.equal(textCell('rápido'), 'rápido');
});

test('textCell turns empty, whitespace-only, null and undefined into null', () => {
  for (const value of ['', '   ', ' ', null, undefined]) {
    assert.equal(textCell(value), null, JSON.stringify(value));
  }
});

test('integerTextCell writes an integral number as its decimal string', () => {
  assert.equal(integerTextCell(12345), '12345');
  assert.equal(integerTextCell(0), '0');
  assert.equal(integerTextCell(9007199254740991), '9007199254740991');
});

test('integerTextCell writes a non-integral number with String(n), for the mapper to reject', () => {
  assert.equal(integerTextCell(12.5), '12.5');
});

test('integerTextCell trims a string and passes it through', () => {
  assert.equal(integerTextCell(' 42 '), '42');
  assert.equal(integerTextCell('abc'), 'abc');
});

test('integerTextCell turns empty, null and undefined into null', () => {
  for (const value of ['', '  ', null, undefined]) {
    assert.equal(integerTextCell(value), null, JSON.stringify(value));
  }
});

test('isBlank is true only for null, undefined and whitespace-only strings', () => {
  assert.equal(isBlank(null), true);
  assert.equal(isBlank(undefined), true);
  assert.equal(isBlank(' '), true);
  assert.equal(isBlank(0), false);
  assert.equal(isBlank('-'), false);
});

// T4: typed cells (fecha, hora) and the header matcher shared by every tab.

const { isoDateCell, timeCell, isDateValue, mapColumns, ContractError } = loadScript();
const { makeFormatter, zonedDate, BUENOS_AIRES } = require('./helpers/format.js');

const MADRID = 'Europe/Madrid';
const formatBA = makeFormatter(BUENOS_AIRES);

test('isoDateCell formats a typed date in the given zone', () => {
  assert.equal(isoDateCell(zonedDate(BUENOS_AIRES, 2026, 7, 25), '25/7/2026', formatBA), '2026-07-25');
});

test('isoDateCell round-trips a date built in another zone when formatted in that zone', () => {
  const madridMidnight = zonedDate(MADRID, 2026, 7, 25);
  assert.equal(isoDateCell(madridMidnight, '25/7/2026', makeFormatter(MADRID)), '2026-07-25');
  // The reason the spreadsheet's own zone is used: the wrong zone shifts the day.
  assert.equal(isoDateCell(madridMidnight, '25/7/2026', formatBA), '2026-07-24');
});

test('isoDateCell passes a text date through, trimmed, and blank as null', () => {
  assert.equal(isoDateCell('2026-07-25', '2026-07-25', formatBA), '2026-07-25');
  assert.equal(isoDateCell(' 2026-07-25 ', ' 2026-07-25 ', formatBA), '2026-07-25');
  assert.equal(isoDateCell('25/7/2026', '25/7/2026', formatBA), '25/7/2026');
  assert.equal(isoDateCell('', '', formatBA), null);
});

test('timeCell pads and trims a text time, and drops :00 seconds', () => {
  assert.equal(timeCell('21:00', '21:00', formatBA), '21:00');
  assert.equal(timeCell('21:00:00', '21:00:00', formatBA), '21:00');
  assert.equal(timeCell(' 9:30 ', ' 9:30 ', formatBA), '09:30');
  assert.equal(timeCell('0:05', '0:05', formatBA), '00:05');
});

test('timeCell reads the display first, so a typed time on the 1899 epoch keeps its minutes', () => {
  const ninePm = zonedDate(BUENOS_AIRES, 1899, 12, 30, 21, 0);
  assert.equal(timeCell(ninePm, '21:00:00', formatBA), '21:00');
  assert.equal(timeCell(ninePm, '9:00 p. m.', formatBA), '21:00');
  // Formatted in UTC the same instant reads 01:16: the historical offset of Buenos Aires is
  // -4:16:48 in 1899, which is why the display text wins when it is a plain time.
  assert.equal(timeCell(ninePm, '9:00 p. m.', makeFormatter('UTC')), '01:16');
});

test('timeCell passes an out-of-range or non-:00-seconds text time through for the mapper', () => {
  assert.equal(timeCell('25:00', '25:00', formatBA), '25:00');
  assert.equal(timeCell('21:30:15', '21:30:15', formatBA), '21:30:15');
  assert.equal(timeCell('21:75', '21:75', formatBA), '21:75');
  assert.equal(timeCell('de noche', 'de noche', formatBA), 'de noche');
  assert.equal(timeCell('', '  ', formatBA), null);
});

test('posicion uses integerTextCell: the number 3 gives "3", 2.5 gives "2.5"', () => {
  assert.equal(integerTextCell(3), '3');
  assert.equal(integerTextCell(2.5), '2.5');
  assert.equal(integerTextCell(' 3 '), '3');
});

test('isDateValue recognizes a Date from another realm and rejects an invalid one or text', () => {
  const vm = require('node:vm');
  assert.equal(isDateValue(vm.runInNewContext('new Date(0)')), true);
  assert.equal(isDateValue(new Date(NaN)), false);
  assert.equal(isDateValue('2026-07-25'), false);
  assert.equal(isDateValue(null), false);
});

test('mapColumns matches trimmed exact headers, reports missing and duplicate ones', () => {
  const specs = [
    { header: 'a', field: 'x', required: true },
    { header: 'b', field: 'y', required: false },
  ];
  assert.deepStrictEqual(mapColumns('T', specs, ['z', ' a ']), { x: 1 });
  assert.throws(() => mapColumns('T', specs, ['A']), (err) => err instanceof ContractError && err.code === 'missing_header' && /T is missing required headers: a/.test(err.message));
  assert.throws(() => mapColumns('T', specs, ['a', 'b', 'b ']), (err) => err instanceof ContractError && err.code === 'duplicate_header');
});

test('timeCell prefers a plain display time over the raw Date, even when the Date formats differently', () => {
  // 21:00 at a fixed -03:00 on the 1899 epoch, as Sheets may build a time-only cell. Formatted with
  // Buenos Aires' historical 1899 offset (-4:16:48) this instant reads 19:43, so a raw-first
  // timeCell would return '19:43' instead of the '21:00' the admin sees.
  const raw = new Date(Date.UTC(1899, 11, 31, 0, 0));
  assert.equal(formatBA(raw, 'HH:mm'), '19:43');
  assert.equal(timeCell(raw, '21:00:00', formatBA), '21:00');
  assert.equal(timeCell(raw, '21:00', formatBA), '21:00');
});
