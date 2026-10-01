#!/usr/bin/env node
'use strict';
// Checks a saved catalog or jams response against the contract in docs/apps-script-api.md.
//
//   node backend/apps-script/tools/check-response.js catalog.local.json
//   node backend/apps-script/tools/check-response.js --strict jams.local.json
//
// A body with a `jams` key is checked as a jams response, anything else as a catalog response.
// --strict (jams only) also rejects a date, startTime or position outside its documented format
// and any per-jam setlistError; a catalog check ignores it.
//
// Exit 0: valid response. Exit 1: violations, one per line. Exit 2: usage or unreadable file.

const fs = require('node:fs');
const path = require('node:path');
const { checkCatalogResponse, checkJamsResponse } = require(path.join(__dirname, '..', 'test', 'helpers', 'contract.js'));

function summarizeJams(jams) {
  const published = jams.filter((j) => Array.isArray(j.setlist)).length;
  const withheld = jams.filter((j) => j.status !== 'PUBLICADA').length;
  const withErrors = jams.filter((j) => j.setlistError !== null).length;
  const rows = jams.reduce((n, j) => n + (Array.isArray(j.setlist) ? j.setlist.length : 0), 0);
  return `${jams.length} jams, ${published} published with setlist, ${withheld} withheld, ${withErrors} with errors, ${rows} setlist rows`;
}

function main(argv) {
  const strict = argv.includes('--strict');
  const files = argv.filter((a) => a !== '--strict');
  if (files.length !== 1 || files[0].startsWith('--')) {
    console.error('usage: node check-response.js [--strict] <response.json>');
    return 2;
  }
  let text;
  try {
    text = fs.readFileSync(files[0], 'utf8');
  } catch (err) {
    console.error(`cannot read ${files[0]}: ${err.message}`);
    return 2;
  }
  if (text.charCodeAt(0) === 0xfeff) {
    text = text.slice(1);
  }
  let body;
  try {
    body = JSON.parse(text);
  } catch (err) {
    console.log(`FAIL: not JSON (${err.message}). It starts with: ${JSON.stringify(text.slice(0, 80))}`);
    console.log('An HTML page usually means a /dev URL, access not set to Anyone, or curl without -L.');
    return 1;
  }
  const isJams = body !== null && typeof body === 'object' && Object.prototype.hasOwnProperty.call(body, 'jams');
  const violations = isJams ? checkJamsResponse(body, { strict }) : checkCatalogResponse(body);
  if (violations.length > 0) {
    console.log(`FAIL: ${violations.length} violation(s)`);
    for (const v of violations) {
      console.log(`  - ${v}`);
    }
    return 1;
  }
  if (isJams) {
    console.log(`OK${strict ? ' (strict)' : ''}: schemaVersion 1, ${summarizeJams(body.jams)}`);
    return 0;
  }
  const songs = body.songs;
  const withNullOptional = songs.filter((s) => s.tempo === null || s.tags === null || s.difficulty === null || s.songsterrId === null).length;
  console.log(`OK: schemaVersion 1, ${songs.length} songs, ${withNullOptional} with at least one null optional field`);
  return 0;
}

process.exitCode = main(process.argv.slice(2));
