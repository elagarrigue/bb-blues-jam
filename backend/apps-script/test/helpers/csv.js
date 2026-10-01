'use strict';
// A small RFC 4180 parser for the seed CSVs: quoted fields, doubled quotes, CRLF or LF line ends.
// Every cell stays a string, as getDisplayValues() would return it.

const fs = require('node:fs');

function parseCsv(text) {
  const rows = [];
  let row = [];
  let cell = '';
  let quoted = false;
  let i = 0;
  if (text.charCodeAt(0) === 0xfeff) {
    i = 1;
  }
  for (; i < text.length; i++) {
    const c = text[i];
    if (quoted) {
      if (c === '"' && text[i + 1] === '"') {
        cell += '"';
        i++;
      } else if (c === '"') {
        quoted = false;
      } else {
        cell += c;
      }
    } else if (c === '"') {
      quoted = true;
    } else if (c === ',') {
      row.push(cell);
      cell = '';
    } else if (c === '\r' || c === '\n') {
      if (c === '\r' && text[i + 1] === '\n') {
        i++;
      }
      row.push(cell);
      rows.push(row);
      row = [];
      cell = '';
    } else {
      cell += c;
    }
  }
  if (quoted) {
    throw new Error('Unterminated quoted field');
  }
  if (cell !== '' || row.length > 0) {
    row.push(cell);
    rows.push(row);
  }
  return rows;
}

function readCsv(file) {
  return parseCsv(fs.readFileSync(file, 'utf8'));
}

module.exports = { parseCsv, readCsv };
