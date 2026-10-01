#!/usr/bin/env node
'use strict';
// Checks a saved catalog response against the contract in docs/apps-script-api.md.
//
//   node backend/apps-script/tools/check-response.js catalog.local.json
//
// Exit 0: valid catalog response. Exit 1: violations, one per line. Exit 2: usage or unreadable file.

const fs = require('node:fs');
const path = require('node:path');
const { checkCatalogResponse } = require(path.join(__dirname, '..', 'test', 'helpers', 'contract.js'));

function main(argv) {
  if (argv.length !== 1) {
    console.error('usage: node check-response.js <response.json>');
    return 2;
  }
  let text;
  try {
    text = fs.readFileSync(argv[0], 'utf8');
  } catch (err) {
    console.error(`cannot read ${argv[0]}: ${err.message}`);
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
  const violations = checkCatalogResponse(body);
  if (violations.length > 0) {
    console.log(`FAIL: ${violations.length} violation(s)`);
    for (const v of violations) {
      console.log(`  - ${v}`);
    }
    return 1;
  }
  const songs = body.songs;
  const withNullOptional = songs.filter((s) => s.tempo === null || s.tags === null || s.difficulty === null || s.songsterrId === null).length;
  console.log(`OK: schemaVersion 1, ${songs.length} songs, ${withNullOptional} with at least one null optional field`);
  return 0;
}

process.exitCode = main(process.argv.slice(2));
