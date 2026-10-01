'use strict';
// The shape of a successful catalog or jams response (docs/apps-script-api.md). Checks shape only:
// key format, id alphabet and enum values are the Kotlin mapper's job, not this checker's. The jams
// check also enforces the draft rule, and with { strict: true } the documented text formats.

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

const JAMS_TOP_KEYS = ['schemaVersion', 'jams'];
const JAM_KEYS = ['date', 'startTime', 'venue', 'status', 'setlist', 'setlistError'];
const ROW_KEYS = ['position', 'songId', 'title', 'artist', 'key', 'slots', 'extraParticipants'];
const SLOT_KEYS = ['guitar1', 'guitar2', 'bass', 'drums', 'vocals', 'harmonica', 'keyboards'];
const ERROR_KEYS = ['code', 'message'];
const PUBLISHED = 'PUBLICADA';
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
const TIME = /^([01]\d|2[0-3]):[0-5]\d$/;
const POSITION = /^[1-9]\d*$/;

function keyViolation(where, keys, expected) {
  if (sameList(keys, expected)) {
    return null;
  }
  const missing = expected.filter((k) => !keys.includes(k));
  const extra = keys.filter((k) => !expected.includes(k));
  const detail = [];
  if (missing.length > 0) detail.push(`missing ${missing.join(', ')}`);
  if (extra.length > 0) detail.push(`unexpected ${extra.join(', ')}`);
  if (detail.length === 0) detail.push('keys out of order');
  return `${where} keys: ${detail.join('; ')}`;
}

/** Pushes a violation unless the value is a non-empty trimmed string or null. */
function checkText(violations, where, value) {
  if (value === null) {
    return;
  }
  if (typeof value !== 'string') {
    violations.push(`${where} is ${typeof value} ${JSON.stringify(value)}, expected string or null`);
  } else if (value.trim() === '') {
    violations.push(`${where} is an empty string, expected null`);
  } else if (value !== value.trim()) {
    violations.push(`${where} is not trimmed: ${JSON.stringify(value)}`);
  }
}

function checkRow(violations, where, row, strict) {
  if (!isPlainObject(row)) {
    violations.push(`${where} is not an object`);
    return;
  }
  const keys = keyViolation(where, Object.keys(row), ROW_KEYS);
  if (keys !== null) violations.push(keys);
  for (const key of Object.keys(row)) {
    if (key !== 'slots') {
      checkText(violations, `${where}.${key}`, row[key]);
    }
  }
  if (!isPlainObject(row.slots)) {
    violations.push(`${where}.slots is not an object`);
  } else {
    const slotKeys = keyViolation(`${where}.slots`, Object.keys(row.slots), SLOT_KEYS);
    if (slotKeys !== null) violations.push(slotKeys);
    for (const key of Object.keys(row.slots)) {
      checkText(violations, `${where}.slots.${key}`, row.slots[key]);
    }
  }
  if (strict && !(typeof row.position === 'string' && POSITION.test(row.position))) {
    violations.push(`${where}.position ${JSON.stringify(row.position)} is not a positive integer`);
  }
}

function checkSetlistError(violations, where, setlistError) {
  if (!isPlainObject(setlistError)) {
    violations.push(`${where}.setlistError is ${typeof setlistError}, expected an object or null`);
    return;
  }
  const keys = keyViolation(`${where}.setlistError`, Object.keys(setlistError), ERROR_KEYS);
  if (keys !== null) violations.push(keys);
  for (const key of ERROR_KEYS) {
    if (typeof setlistError[key] !== 'string' || setlistError[key] === '') {
      violations.push(`${where}.setlistError.${key} is not a non-empty string`);
    }
  }
}

/**
 * Returns a list of violations; an empty list means the response is a valid jams response.
 *
 * Always enforced, the draft rule: a jam whose status is not exactly PUBLICADA carries
 * `setlist: null` and `setlistError: null`. A PUBLICADA jam carries exactly one of the two.
 *
 * With { strict: true }, also flags any date, startTime or position not in its documented format
 * and any setlistError, so a live response from a healthy Sheet must pass it.
 */
function checkJamsResponse(body, { strict = false } = {}) {
  const violations = [];
  if (!isPlainObject(body)) {
    return ['the response is not a JSON object'];
  }
  if (isPlainObject(body.error)) {
    violations.push(`the response is an error: ${body.error.code}: ${body.error.message}`);
  }
  if (!sameList(Object.keys(body), JAMS_TOP_KEYS)) {
    violations.push(`top-level keys are [${Object.keys(body).join(', ')}], expected [${JAMS_TOP_KEYS.join(', ')}]`);
  }
  if (body.schemaVersion !== 1) {
    violations.push(`schemaVersion is ${JSON.stringify(body.schemaVersion)}, expected 1`);
  }
  if (!Array.isArray(body.jams)) {
    violations.push('jams is not an array');
    return violations;
  }
  body.jams.forEach((jam, index) => {
    const where = `jams[${index}]`;
    if (!isPlainObject(jam)) {
      violations.push(`${where} is not an object`);
      return;
    }
    const keys = keyViolation(where, Object.keys(jam), JAM_KEYS);
    if (keys !== null) violations.push(keys);
    for (const key of ['date', 'startTime', 'venue', 'status']) {
      checkText(violations, `${where}.${key}`, jam[key]);
    }
    const setlist = jam.setlist === undefined ? null : jam.setlist;
    const setlistError = jam.setlistError === undefined ? null : jam.setlistError;
    if (setlist !== null && !Array.isArray(setlist)) {
      violations.push(`${where}.setlist is ${typeof setlist}, expected an array or null`);
    }
    if (setlistError !== null) {
      checkSetlistError(violations, where, setlistError);
    }
    if (jam.status !== PUBLISHED) {
      if (setlist !== null) {
        violations.push(`${where} has status ${JSON.stringify(jam.status)} but carries a setlist: a draft must be withheld`);
      }
      if (setlistError !== null) {
        violations.push(`${where} has status ${JSON.stringify(jam.status)} but carries a setlistError: a withheld setlist has none`);
      }
    } else if (setlist === null && setlistError === null) {
      violations.push(`${where} is ${PUBLISHED} with neither a setlist nor a setlistError`);
    } else if (setlist !== null && setlistError !== null) {
      violations.push(`${where} carries both a setlist and a setlistError`);
    }
    if (Array.isArray(setlist)) {
      setlist.forEach((row, r) => checkRow(violations, `${where}.setlist[${r}]`, row, strict));
    }
    if (strict) {
      if (!(typeof jam.date === 'string' && ISO_DATE.test(jam.date))) {
        violations.push(`${where}.date ${JSON.stringify(jam.date)} is not YYYY-MM-DD`);
      }
      if (!(typeof jam.startTime === 'string' && TIME.test(jam.startTime))) {
        violations.push(`${where}.startTime ${JSON.stringify(jam.startTime)} is not HH:MM`);
      }
      if (isPlainObject(setlistError)) {
        violations.push(`${where} (${jam.date}) has setlistError ${setlistError.code}: ${setlistError.message}`);
      }
    }
  });
  return violations;
}

module.exports = { checkCatalogResponse, checkJamsResponse, SONG_KEYS, JAM_KEYS, ROW_KEYS, SLOT_KEYS };
