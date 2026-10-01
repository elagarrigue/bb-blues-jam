'use strict';
// The shape of a successful catalog response (docs/apps-script-api.md). Checks shape only: key
// format, id alphabet and enum values are the Kotlin mapper's job, not this checker's.

const SONG_KEYS = ['id', 'title', 'artist', 'defaultKey', 'tempo', 'tags', 'difficulty', 'songsterrId'];
const TOP_KEYS = ['schemaVersion', 'songs'];

function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function sameList(a, b) {
  return a.length === b.length && a.every((item, i) => item === b[i]);
}

/** Returns a list of violations; an empty list means the response is a valid catalog. */
function checkCatalogResponse(body) {
  const violations = [];
  if (!isPlainObject(body)) {
    return ['the response is not a JSON object'];
  }
  if (isPlainObject(body.error)) {
    violations.push(`the response is an error: ${body.error.code}: ${body.error.message}`);
  }
  if (!sameList(Object.keys(body), TOP_KEYS)) {
    violations.push(`top-level keys are [${Object.keys(body).join(', ')}], expected [${TOP_KEYS.join(', ')}]`);
  }
  if (body.schemaVersion !== 1) {
    violations.push(`schemaVersion is ${JSON.stringify(body.schemaVersion)}, expected 1`);
  }
  if (!Array.isArray(body.songs)) {
    violations.push('songs is not an array');
    return violations;
  }
  body.songs.forEach((song, index) => {
    const where = `songs[${index}]`;
    if (!isPlainObject(song)) {
      violations.push(`${where} is not an object`);
      return;
    }
    const keys = Object.keys(song);
    if (!sameList(keys, SONG_KEYS)) {
      const missing = SONG_KEYS.filter((k) => !keys.includes(k));
      const extra = keys.filter((k) => !SONG_KEYS.includes(k));
      const detail = [];
      if (missing.length > 0) detail.push(`missing ${missing.join(', ')}`);
      if (extra.length > 0) detail.push(`unexpected ${extra.join(', ')}`);
      if (detail.length === 0) detail.push('keys out of order');
      violations.push(`${where} keys: ${detail.join('; ')}`);
    }
    for (const key of keys) {
      const value = song[key];
      if (value === null) {
        continue;
      }
      if (typeof value !== 'string') {
        violations.push(`${where}.${key} is ${typeof value} ${JSON.stringify(value)}, expected string or null`);
      } else if (value.trim() === '') {
        violations.push(`${where}.${key} is an empty string, expected null`);
      } else if (value !== value.trim()) {
        violations.push(`${where}.${key} is not trimmed: ${JSON.stringify(value)}`);
      }
    }
  });
  return violations;
}

module.exports = { checkCatalogResponse, SONG_KEYS };
