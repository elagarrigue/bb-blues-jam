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
