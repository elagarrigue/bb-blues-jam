'use strict';
// Loads the script files the way Apps Script does: one shared global scope, in the editor's file
// order. Each src file exports its globals through its guarded module.exports; copying them onto
// globalThis lets a function in one file call a function from another, exactly as in Apps Script.

const path = require('node:path');

const SRC_DIR = path.join(__dirname, '..', '..', 'src');
const SRC_FILES = ['Normalize.js', 'Catalog.js', 'Code.js'];

let loaded = null;

function loadScript() {
  if (loaded === null) {
    loaded = {};
    for (const file of SRC_FILES) {
      const exports = require(path.join(SRC_DIR, file));
      Object.assign(globalThis, exports);
      Object.assign(loaded, exports);
    }
  }
  return loaded;
}

module.exports = { loadScript, SRC_DIR, SRC_FILES };
