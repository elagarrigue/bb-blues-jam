/**
 * POST entry point (docs/apps-script-api.md, POST actions). The admin passphrase only ever travels
 * in a POST body, never in a URL.
 *
 * Every action in ACTIONS passes one passphrase guard, requirePassphrase_, in the router and before
 * the action runs (apps-script-write-auth). No action calls the guard itself, so an action added
 * later cannot forget it. The guard reads Config on every request and caches nothing, so rotating
 * the passphrase in the Sheet rejects every device at once. Failed guesses are rate limited with a
 * global counter in CacheService, and write actions run under the script lock.
 *
 * admin-add-song-to-setlist adds the admin read `readJams` (the GET's jams plus the songs of every
 * current or future BORRADOR jam), the first setlist write `addSong` and its self-cleaning deploy
 * check `checkSetlistWrite`. They reuse the read path's builders unchanged: a jam tab is read with
 * buildSetlist, so what addSong appends is exactly what the reads return.
 *
 * admin-remove-song-from-setlist adds `removeSong` and its self-cleaning deploy check
 * `checkSetlistRemove`. A setlist mutation locates its row by `id_tema` (user decision R1), never by
 * `posicion`: removeSong deletes that row and moves every later `posicion` up by one, so the tab
 * stays numbered 1..n and a renumber by one admin can never make another admin's write hit the
 * wrong song. (`fecha`, `posicion`) remains the read identity.
 *
 * admin-set-key adds `setKey`, which writes only the `tono` cell of the row found by `id_tema`
 * (findSongRow_, shared with removeSong) and never opens Catalogo (D-08). Its deploy proof is one
 * more step in `checkSetlistWrite`: the marker row's key is changed with the same finder and cell
 * writer and read back.
 * admin-adjust-lineup adds `setSlotCount`: only empty slots can be removed (`-`), and removed
 * columns reopen in place. Names never move. Invalid instruments/counts and filled slots are
 * rejected before writing. The deploy check removes guitar 2 and removes/restores harmonica.
 * admin-assign-musician adds `assignSlot`: it resolves an instrument ordinal among active U1
 * columns and writes one normalized name to one empty slot cell.
 * admin-reorder-songs adds `moveSong` and `checkSetlistMove`; positions are rewritten in safe
 * batches without moving Sheet rows or ever exposing duplicate positions.
 * admin-publish-setlist adds `publishSetlist` and `checkPublish`; publication writes and reads back
 * only the `estado` cell of the upcoming jam's Jams row.
 *
 * Uses SCHEMA_VERSION and ROUTES from Code.js, PUBLISHED_STATUS, JAMS_TAB, SETLIST_FIELDS,
 * SLOT_FIELDS, EXTRA_FIELD, buildJams and buildSetlist from Jams.js, CATALOG_TAB and catalogColumns_
 * from Catalog.js, and ContractError, mapColumns and textCell from Normalize.js (shared global scope
 * in Apps Script). This file is the only one that opens the Config tab, and no response, message or
 * cache entry ever contains the stored or the submitted passphrase.
 */

var CONFIG_TAB = 'Config';
var PASSPHRASE_KEY = 'passphrase';

var CONFIG_COLUMNS = [
  { header: 'clave', field: 'key', required: true },
  { header: 'valor', field: 'value', required: true },
];

/** Rate limit (user approval W2): at most 10 failed guesses per fixed 10-minute window, globally. */
var RATE_LIMIT_MAX_FAILURES = 10;
var RATE_LIMIT_WINDOW_MS = 10 * 60 * 1000;
var RATE_LIMIT_TTL_SECONDS = 20 * 60;
var RATE_LIMIT_KEY_PREFIX = 'auth_failures_';

/** How long a write waits for another write to finish before answering `busy`. */
var WRITE_LOCK_TIMEOUT_MS = 10000;

/** The transient tab checkWriteAccess creates and deletes within one request. Never read. */
var WRITE_CHECK_TAB = '_prueba_escritura';

/** The transient jam tab checkSetlistWrite creates and deletes within one request. Never served. */
var SETLIST_CHECK_TAB = '_prueba_lista';

/** The transient Jams tab checkPublish creates and deletes within one request. Never served. */
var PUBLISH_CHECK_TAB = '_prueba_publicar';

/** The status of a jam whose setlist only the admin reads. */
var DRAFT_STATUS = 'BORRADOR';

/** Request formats for addSong (docs/sheet-schema.md, Identifiers and Keys). */
var POST_ISO_DATE_ = /^\d{4}-\d{2}-\d{2}$/;
var SONG_ID_ = /^[a-z0-9]+(-[a-z0-9]+)*$/;
var KEY_ = /^[A-G][#b]?m?$/;

/** The key checkSetlistWrite gives its marker row with setKey's cell writer (the marker starts in Bbm). */
var CHECK_KEY_ = 'F#m';

/** Sheets' plain-text number format: a value such as 7/4 or 007 stays the text it was sent as. */
var PLAIN_TEXT_FORMAT = '@';

/** Default slot fields in Sheet column order (D-18). */
var SLOT_COLUMNS_ = {
  guitar: ['guitar1', 'guitar2'], bass: ['bass'], drums: ['drums'],
  vocals: ['vocals'], harmonica: ['harmonica'], keyboards: ['keyboards'],
};

/**
 * The only POST actions. An action not listed here is `unknown_action`. Every one of them passes
 * the passphrase guard first; `write: true` also runs it under the script lock.
 */
var ACTIONS = {
  checkPassphrase: { write: false, run: checkPassphrase_ },
  checkWriteAccess: { write: true, run: checkWriteAccess_ },
  readJams: { write: false, run: readJamsAsAdmin_ },
  addSong: { write: true, run: addSong_ },
  checkSetlistWrite: { write: true, run: checkSetlistWrite_ },
  removeSong: { write: true, run: removeSong_ },
  checkSetlistRemove: { write: true, run: checkSetlistRemove_ },
  setKey: { write: true, run: setKey_ },
  setSlotCount: { write: true, run: setSlotCount_ },
  assignSlot: { write: true, run: assignSlot_ },
  clearSlot: { write: true, run: clearSlot_ },
  moveSong: { write: true, run: moveSong_ },
  checkSetlistMove: { write: true, run: checkSetlistMove_ },
  publishSetlist: { write: true, run: publishSetlist_ },
  checkPublish: { write: true, run: checkPublish_ },
};

/**
 * Apps Script calls this for POST requests. The body is JSON, `{"action": "...", ...}`. As with
 * doGet, every response is HTTP 200 and errors travel in the body.
 */
function doPost(e) {
  let body;
  try {
    const services = {
      cache: CacheService.getScriptCache(),
      lock: LockService.getScriptLock(),
      now: Date.now(),
      flush: function () { SpreadsheetApp.flush(); },
    };
    body = handlePost(parsePostBody_(e), SpreadsheetApp.getActiveSpreadsheet(), services);
  } catch (err) {
    body = postErrorBody_('internal_error', postErrorMessage_(err));
  }
  return ContentService.createTextOutput(JSON.stringify(body)).setMimeType(ContentService.MimeType.JSON);
}

/** The parsed JSON object of the POST body, or null when there is none or it is not an object. */
function parsePostBody_(e) {
  const contents = e && e.postData ? e.postData.contents : undefined;
  if (typeof contents !== 'string') {
    return null;
  }
  try {
    return JSON.parse(contents);
  } catch (ignored) {
    return null;
  }
}

/**
 * Routes a POST request. `request` is the parsed body, `spreadsheet` the active spreadsheet, and
 * `services` is `{ cache, lock, now }` (a script cache, the script lock, the time in ms). In order:
 * invalid_request, unknown_action, the passphrase guard, the lock for a write action, the action.
 * Returns the response body as a plain object; never throws.
 */
function handlePost(request, spreadsheet, services) {
  if (request === null || typeof request !== 'object' || Array.isArray(request)) {
    return postErrorBody_('invalid_request', 'The body must be a JSON object');
  }
  const action = request.action;
  if (typeof action !== 'string' || !Object.prototype.hasOwnProperty.call(ACTIONS, action)) {
    return postErrorBody_('unknown_action', 'Unknown or missing action. Known: ' + Object.keys(ACTIONS).join(', '));
  }
  try {
    const entry = ACTIONS[action];
    requirePassphrase_(request, spreadsheet, services);
    const run = function () {
      return entry.run(request, spreadsheet, services);
    };
    const result = entry.write ? withWriteLock_(services.lock, run) : run();
    const body = { schemaVersion: SCHEMA_VERSION };
    Object.keys(result).forEach(function (key) {
      body[key] = result[key];
    });
    return body;
  } catch (err) {
    if (err instanceof ContractError) {
      return postErrorBody_(err.code, err.message);
    }
    return postErrorBody_('internal_error', postErrorMessage_(err));
  }
}

/**
 * The guard every action passes, called only by handlePost. Throws `rate_limited` when the current
 * window already holds the maximum of failed guesses (Config is then not read), `passphrase_not_set`
 * when Config has no usable passphrase, and `invalid_passphrase` (counting one failure) when the
 * request does not carry it. The cache only ever holds a count under a key made of the window
 * number; a cache that throws is ignored (fails open), and the passphrase check still runs.
 */
function requirePassphrase_(request, spreadsheet, services) {
  const key = RATE_LIMIT_KEY_PREFIX + Math.floor(services.now / RATE_LIMIT_WINDOW_MS);
  const failures = failureCount_(services.cache, key);
  if (failures >= RATE_LIMIT_MAX_FAILURES) {
    throw new ContractError('rate_limited', 'Too many failed passphrase attempts. Try again later');
  }
  if (!passphraseMatches_(spreadsheet, request.passphrase)) {
    recordFailure_(services.cache, key, failures + 1);
    throw new ContractError('invalid_passphrase', 'The passphrase is not correct');
  }
}

/** The failed guesses counted under `key`; 0 when absent, unreadable or the cache throws. */
function failureCount_(cache, key) {
  try {
    const count = parseInt(cache.get(key), 10);
    return isNaN(count) ? 0 : count;
  } catch (ignored) {
    return 0;
  }
}

/**
 * Stores the new count. Not atomic: two failures at the same moment may count once, which only
 * loosens the limit slightly. A cache that throws is ignored.
 */
function recordFailure_(cache, key, count) {
  try {
    cache.put(key, String(count), RATE_LIMIT_TTL_SECONDS);
  } catch (ignored) {
    // Fail open: the passphrase check already ran.
  }
}

/** Runs `fn` holding the script lock; `busy` when another write holds it for too long. */
function withWriteLock_(lock, fn) {
  if (!lock.tryLock(WRITE_LOCK_TIMEOUT_MS)) {
    throw new ContractError('busy', 'Another write is in progress. Try again');
  }
  try {
    return fn();
  } finally {
    lock.releaseLock();
  }
}

/** The guard already accepted the passphrase; checking it is all this action does. */
function checkPassphrase_() {
  return { ok: true };
}

/**
 * A self-cleaning deploy check: creates the tab WRITE_CHECK_TAB (deleting a leftover first), writes
 * a marker to A1, reads it back and deletes the tab. Touches no other tab. A read-back that differs
 * is an Error, so `internal_error`, after the tab is deleted.
 */
function checkWriteAccess_(request, spreadsheet, services) {
  const leftover = spreadsheet.getSheetByName(WRITE_CHECK_TAB);
  if (leftover) {
    spreadsheet.deleteSheet(leftover);
  }
  const sheet = spreadsheet.insertSheet(WRITE_CHECK_TAB);
  const marker = 'write check ' + new Date(services.now).toISOString();
  let readBack;
  try {
    const cell = sheet.getRange('A1');
    cell.setValue(marker);
    readBack = cell.getDisplayValue();
  } finally {
    spreadsheet.deleteSheet(sheet);
  }
  if (readBack !== marker) {
    throw new Error('The write check read back a different value');
  }
  return { ok: true };
}

/**
 * The admin read (`readJams`, a read action: no lock), answering `{ ok, jams }`: the GET's jams,
 * built by the same route function, plus the setlist of every jam whose status is exactly BORRADOR
 * and whose date is today or later in the spreadsheet's zone, with the GET's per-jam guards
 * (`duplicate_date`, `missing_tab`, and buildSetlist's header errors). Every other jam is left
 * exactly as the GET built it: a past draft's songs are never served, and a draft whose date is not
 * YYYY-MM-DD is untouched.
 */
function readJamsAsAdmin_(request, spreadsheet, services) {
  const result = ROUTES.jams(spreadsheet);
  const today = todayIso_(spreadsheet, services);
  const dateCount = {};
  result.jams.forEach(function (jam) {
    if (jam.date !== null) {
      dateCount[jam.date] = (dateCount[jam.date] || 0) + 1;
    }
  });
  result.jams.forEach(function (jam) {
    if (jam.status !== DRAFT_STATUS || jam.date === null || !POST_ISO_DATE_.test(jam.date) || jam.date < today) {
      return;
    }
    if (dateCount[jam.date] > 1) {
      jam.setlistError = adminSetlistError_('duplicate_date', 'Jams has the fecha ' + jam.date + ' on more than one row');
      return;
    }
    const tab = spreadsheet.getSheetByName(jam.date);
    if (!tab) {
      jam.setlistError = adminSetlistError_('missing_tab', 'The tab ' + jam.date + ' does not exist');
      return;
    }
    try {
      const range = tab.getDataRange();
      jam.setlist = buildSetlist(range.getDisplayValues(), range.getValues(), jam.date);
    } catch (err) {
      if (!(err instanceof ContractError)) {
        throw err;
      }
      jam.setlistError = adminSetlistError_(err.code, err.message);
    }
  });
  return { ok: true, jams: result.jams };
}

/**
 * Appends one catalog song to the upcoming jam's tab (`addSong`, a write action: under the lock).
 * Request `{ date, songId, key }`. Every check runs before anything is written, in this order:
 * `invalid_date`, `invalid_song`, `invalid_key`, `unknown_jam` / `duplicate_date`,
 * `jam_not_editable`, `unknown_song`, `missing_header` / `duplicate_header`,
 * `song_already_in_setlist`. Then the tab is created when missing (header row in documented order)
 * and the row is appended at `posicion` = 1 + the largest valid one, with the catalog's title and
 * artist, the key from the request (never `tono_default`, D-08) and seven open slots (D-18).
 * Answers `{ ok, position, title, artist }`.
 */
function addSong_(request, spreadsheet, services) {
  const date = request.date;
  if (typeof date !== 'string' || !isCalendarDate_(date)) {
    throw new ContractError('invalid_date', 'date must be a YYYY-MM-DD calendar date');
  }
  const songId = request.songId;
  if (typeof songId !== 'string' || !SONG_ID_.test(songId)) {
    throw new ContractError('invalid_song', 'songId must be a lowercase slug such as sweet-little-angel');
  }
  const key = request.key;
  if (typeof key !== 'string' || !KEY_.test(key)) {
    throw new ContractError('invalid_key', 'key must be A-G, an optional # or b, and an optional m');
  }

  requireEditableJam_(spreadsheet, services, date);
  const song = catalogSong_(spreadsheet, songId);

  const existing = spreadsheet.getSheetByName(date);
  let rows = [];
  if (existing) {
    const range = existing.getDataRange();
    rows = buildSetlist(range.getDisplayValues(), range.getValues(), date);
    const listed = rows.some(function (row) {
      return row.songId === songId;
    });
    if (listed) {
      throw new ContractError('song_already_in_setlist', songId + ' is already in the setlist of ' + date);
    }
  }

  const position = nextPosition_(rows);
  const sheet = existing || createSetlistTab_(spreadsheet, date);
  appendSetlistRow_(sheet, { position: position, songId: songId, title: song.title, artist: song.artist, key: key });
  return { ok: true, position: position, title: song.title, artist: song.artist };
}

/**
 * A self-cleaning deploy check for addSong, setKey, setSlotCount and assignSlot write paths: creates the tab
 * SETLIST_CHECK_TAB (deleting a leftover first) with the jam tab header, appends a marker row with
 * the same function as addSong, changes its key to CHECK_KEY_ with setKey's finder and cell writer,
 * reads it back with buildSetlist and deletes the tab. The marker title `7/4` proves the plain-text
 * format: without it Sheets turns it into a date. A read-back that differs (the marker with key
 * CHECK_KEY_) is an Error, so `internal_error`, after the tab is deleted. Touches no other tab and is
 * never listed by a read.
 */
function checkSetlistWrite_(request, spreadsheet, services) {
  const leftover = spreadsheet.getSheetByName(SETLIST_CHECK_TAB);
  if (leftover) {
    spreadsheet.deleteSheet(leftover);
  }
  const marker = {
    position: 1,
    songId: 'zz-prueba-lista',
    title: '7/4',
    artist: 'Prueba ' + new Date(services.now).toISOString(),
    key: 'Bbm',
  };
  const sheet = createSetlistTab_(spreadsheet, SETLIST_CHECK_TAB);
  let rows;
  try {
    appendSetlistRow_(sheet, marker);
    const written = sheet.getDataRange().getDisplayValues();
    const columns = mapColumns(SETLIST_CHECK_TAB, setlistSpecs_(), written[0]);
    writeKeyCell_(sheet, findSongRow_(sheet, columns, written, marker.songId), columns, CHECK_KEY_);
    const markerRow = findSongRow_(sheet, columns, written, marker.songId);
    [['guitar', 1], ['harmonica', 0], ['harmonica', 1]].forEach(function (step) {
      const cells = readSlotCells_(sheet, markerRow, columns);
      writeSlotCells_(sheet, markerRow, columns, planSlotCount_(cells, SLOT_COLUMNS_[step[0]], step[1]));
      const expected = Object.assign({}, cells);
      Object.assign(expected, planSlotCount_(cells, SLOT_COLUMNS_[step[0]], step[1]));
      Object.keys(expected).forEach(function (field) { expected[field] = textCell(expected[field]); });
      const actual = readSlotCells_(sheet, markerRow, columns);
      if (!Object.keys(expected).every(function (field) { return expected[field] === actual[field]; })) {
        throw new Error('The setlist slot check read back different cells');
      }
    });
    assignSlotToRow_(sheet, markerRow, columns, 'guitar', 1, normalizeMusicianName_(' Prueba   Musico '));
    try {
      clearSlotFromRow_(sheet, markerRow, columns, 'guitar', 1, 'Prueba Musico');
    } catch (ignored) {
      throw new Error('The setlist clear check could not clear its marker slot');
    }
    const range = sheet.getDataRange();
    rows = buildSetlist(range.getDisplayValues(), range.getValues(), SETLIST_CHECK_TAB);
  } finally {
    spreadsheet.deleteSheet(sheet);
  }
  const expectedSlots = {};
  SLOT_FIELDS.forEach(function (spec) {
    expectedSlots[spec.field] = spec.field === 'guitar2' ? '-' : null;
  });
  if (!isMarkerRow_(rows, Object.assign({}, marker, { key: CHECK_KEY_ }), expectedSlots)) {
    throw new Error('The setlist write check read back a different row');
  }
  return { ok: true };
}

/** True when `rows` is exactly the marker, with the expected slots and no Otros. */
function isMarkerRow_(rows, marker, expectedSlots) {
  if (rows.length !== 1) {
    return false;
  }
  const row = rows[0];
  const slotsOpen = Object.keys(row.slots).every(function (field) {
    return row.slots[field] === expectedSlots[field];
  });
  return row.position === String(marker.position) && row.songId === marker.songId && row.title === marker.title &&
    row.artist === marker.artist && row.key === marker.key && slotsOpen && row.extraParticipants === null;
}

/**
 * Removes one song from the upcoming jam's tab (`removeSong`, a write action: under the lock).
 * Request `{ date, songId }`. Every check runs before anything is written, in this order:
 * `invalid_date`, `invalid_song`, `unknown_jam` / `duplicate_date`, `jam_not_editable` (the same
 * rule as addSong), `missing_header` / `duplicate_header`, `song_not_in_setlist` (no row with that
 * trimmed `id_tema`, or no tab at all), `duplicate_song`. Then removeSetlistRow_ deletes the row and
 * renumbers the later ones. The catalog is never opened. Answers `{ ok, position }`, the removed
 * row's `posicion` as a number, or null when it was not a whole number.
 */
function removeSong_(request, spreadsheet, services) {
  const date = request.date;
  if (typeof date !== 'string' || !isCalendarDate_(date)) {
    throw new ContractError('invalid_date', 'date must be a YYYY-MM-DD calendar date');
  }
  const songId = request.songId;
  if (typeof songId !== 'string' || !SONG_ID_.test(songId)) {
    throw new ContractError('invalid_song', 'songId must be a lowercase slug such as sweet-little-angel');
  }

  requireEditableJam_(spreadsheet, services, date);
  const sheet = spreadsheet.getSheetByName(date);
  if (!sheet) {
    throw notInSetlist_(songId, date);
  }
  return { ok: true, position: removeSetlistRow_(sheet, songId) };
}

/**
 * Deletes the one row of `sheet` whose trimmed `id_tema` is `songId` and moves every row whose
 * `posicion` is a whole number above the removed one's up by one, so the tab stays 1..n. Returns
 * the removed `posicion` as a number, or null when it was not a whole number (then nothing is
 * renumbered). Header errors (as the reads), `song_not_in_setlist` and `duplicate_song` are thrown
 * before anything is written.
 *
 * Renumbering writes in ascending `posicion` order with as few range calls as possible: consecutive
 * positions on consecutive rows are one `@` format call plus one setValues. Ascending order means a
 * failure midway can leave a gap (which the reads tolerate) but never two rows with one position.
 * The grid's last row is never deleted directly: a row is inserted after it first, because Sheets
 * refuses to delete the only non-frozen row of a trimmed tab.
 */
function removeSetlistRow_(sheet, songId) {
  const range = sheet.getDataRange();
  const display = range.getDisplayValues();
  const raw = range.getValues();
  const columns = mapColumns(sheet.getName(), setlistSpecs_(), display.length > 0 ? display[0] : []);
  const index = findSongRow_(sheet, columns, display, songId) - 1;
  const removed = wholePosition_((raw[index] || [])[columns.position]);
  const later = [];
  if (removed !== null) {
    for (let r = 1; r < raw.length; r++) {
      const position = r === index ? null : wholePosition_((raw[r] || [])[columns.position]);
      if (position !== null && position > removed) {
        later.push({ row: r + 1, position: position });
      }
    }
  }

  const rowNumber = index + 1;
  if (rowNumber >= sheet.getMaxRows()) {
    sheet.insertRowAfter(rowNumber);
  }
  sheet.deleteRow(rowNumber);

  later.sort(function (a, b) {
    return a.position - b.position;
  });
  positionRuns_(later, rowNumber).forEach(function (run) {
    const cells = sheet.getRange(run.row, columns.position + 1, run.values.length, 1);
    cells.setNumberFormat(PLAIN_TEXT_FORMAT);
    cells.setValues(run.values);
  });
  return removed;
}

/**
 * Moves one row by id to an absolute 1-based position. All data rows must have unique whole
 * positions before any write; otherwise the move would guess which rows the read intentionally
 * dropped. A contiguous changed block is written atomically. For scattered rows, park the moved
 * row above the current maximum, then fill vacancies from the nearest safe direction: targets that
 * decrease in ascending order, then targets that increase in descending order, then the moved row.
 * Thus each individual write preserves unique whole positions.
 */
function moveSetlistRow_(sheet, songId, toPosition) {
  const range = sheet.getDataRange();
  const display = range.getDisplayValues();
  const raw = range.getValues();
  const columns = mapColumns(sheet.getName(), setlistSpecs_(), display.length > 0 ? display[0] : []);
  const movedRow = findSongRow_(sheet, columns, display, songId);
  const movedIndex = movedRow - 2;
  const rows = [];
  const seen = new Set();
  for (let r = 1; r < raw.length; r++) {
    const position = wholePosition_((raw[r] || [])[columns.position]);
    if (position === null) {
      if (r + 1 === movedRow) {
        throw new ContractError('unordered_setlist', 'the moved song must have a whole position');
      }
      continue;
    }
    if (seen.has(position)) {
      throw new ContractError('unordered_setlist', 'setlist positions must be whole and unique before moving a song');
    }
    seen.add(position);
    rows.push({ row: r + 1, position: position, songId: textCell((display[r] || [])[columns.songId]) });
  }
  const ordered = rows.slice().sort(function (a, b) { return a.position - b.position; });
  const moved = ordered.find(function (item) { return item.row === movedRow; });
  const from = ordered.indexOf(moved);
  ordered.splice(from, 1);
  const targetIndex = Math.min(toPosition, ordered.length + 1) - 1;
  ordered.splice(targetIndex, 0, moved);
  const changed = ordered.map(function (item, index) {
    return { row: item.row, oldPosition: item.position, target: index + 1, moved: item === moved };
  }).filter(function (item) { return item.oldPosition !== item.target; });
  if (changed.length === 0) {
    return moved.position;
  }
  const minRow = Math.min.apply(null, changed.map(function (item) { return item.row; }));
  const maxRow = Math.max.apply(null, changed.map(function (item) { return item.row; }));
  if (maxRow - minRow + 1 === changed.length) {
    writePositionRun_(sheet, columns.position, minRow, changed.sort(function (a, b) { return a.row - b.row; }));
    return targetIndex + 1;
  }

  const maxPosition = Math.max.apply(null, rows.map(function (item) { return item.position; }));
  writePositionValues_(sheet, columns.position, [{ row: moved.row, target: maxPosition + 1 }]);
  const decreasing = changed.filter(function (item) { return !item.moved && item.target < item.oldPosition; })
    .sort(function (a, b) { return a.target - b.target; });
  const increasing = changed.filter(function (item) { return !item.moved && item.target > item.oldPosition; })
    .sort(function (a, b) { return b.target - a.target; });
  writePositionRuns_(sheet, columns.position, decreasing);
  writePositionRuns_(sheet, columns.position, increasing);
  writePositionValues_(sheet, columns.position, [{ row: moved.row, target: targetIndex + 1 }]);
  return targetIndex + 1;
}

/** Writes a sorted sequence of rows in maximal sheet-row runs, allowing either row direction. */
function writePositionRuns_(sheet, positionColumn, ordered) {
  let run = [];
  let direction = 0;
  function flush() {
    if (run.length > 0) {
      writePositionRun_(sheet, positionColumn, Math.min.apply(null, run.map(function (item) { return item.row; })), run);
      run = [];
      direction = 0;
    }
  }
  ordered.forEach(function (item) {
    if (run.length > 0) {
      const delta = item.row - run[run.length - 1].row;
      if (Math.abs(delta) !== 1 || (direction !== 0 && delta !== direction)) {
        flush();
      } else {
        direction = delta;
      }
    }
    run.push(item);
  });
  flush();
}

function writePositionRun_(sheet, positionColumn, firstRow, ordered) {
  const sorted = ordered.slice().sort(function (a, b) { return a.row - b.row; });
  writePositionValues_(sheet, positionColumn, sorted);
}

function writePositionValues_(sheet, positionColumn, values) {
  if (values.length === 0) return;
  const sorted = values.slice().sort(function (a, b) { return a.row - b.row; });
  const firstRow = sorted[0].row;
  const cells = sheet.getRange(firstRow, positionColumn + 1, sorted.length, 1);
  cells.setNumberFormat(PLAIN_TEXT_FORMAT);
  cells.setValues(sorted.map(function (item) { return [String(item.target)]; }));
}

function moveSong_(request, spreadsheet, services) {
  const date = request.date;
  if (typeof date !== 'string' || !isCalendarDate_(date)) {
    throw new ContractError('invalid_date', 'date must be a YYYY-MM-DD calendar date');
  }
  const songId = request.songId;
  if (typeof songId !== 'string' || !SONG_ID_.test(songId)) {
    throw new ContractError('invalid_song', 'songId must be a lowercase slug such as sweet-little-angel');
  }
  const toPosition = request.toPosition;
  if (typeof toPosition !== 'number' || !Number.isInteger(toPosition) || toPosition < 1) {
    throw new ContractError('invalid_position', 'toPosition must be a JSON integer of at least 1');
  }
  requireEditableJam_(spreadsheet, services, date);
  const sheet = spreadsheet.getSheetByName(date);
  if (!sheet) throw notInSetlist_(songId, date);
  return { ok: true, position: moveSetlistRow_(sheet, songId, toPosition) };
}

/** Self-cleaning check of contiguous and park-path moves; never reads or writes a real jam tab. */
function checkSetlistMove_(request, spreadsheet, services) {
  const leftover = spreadsheet.getSheetByName(SETLIST_CHECK_TAB);
  if (leftover) spreadsheet.deleteSheet(leftover);
  const sheet = createSetlistTab_(spreadsheet, SETLIST_CHECK_TAB);
  const markerIds = ['uno', 'dos', 'tres', 'cuatro'].map(function (name) { return 'zz-prueba-' + name; });
  let rows;
  try {
    markerIds.forEach(function (songId, index) {
      appendSetlistRow_(sheet, { position: index + 1, songId: songId, title: songId, artist: 'Prueba', key: 'A' });
    });
    moveSetlistRow_(sheet, markerIds[3], 1);
    moveSetlistRow_(sheet, markerIds[1], 1);
    const range = sheet.getDataRange();
    rows = buildSetlist(range.getDisplayValues(), range.getValues(), SETLIST_CHECK_TAB);
  } finally {
    spreadsheet.deleteSheet(sheet);
  }
  const expected = [markerIds[1], markerIds[3], markerIds[0], markerIds[2]];
  rows.sort(function (a, b) { return Number(a.position) - Number(b.position); });
  if (rows.length !== expected.length || !rows.every(function (row, index) {
    return row.songId === expected[index] && row.position === String(index + 1);
  })) throw new Error('The setlist move check read back a different order');
  return { ok: true };
}

/** Publishes the upcoming setlist by changing and reading back only the Jams `estado` cell. */
function publishSetlist_(request, spreadsheet, services) {
  const date = request.date;
  if (typeof date !== 'string' || !isCalendarDate_(date)) {
    throw new ContractError('invalid_date', 'date must be a YYYY-MM-DD calendar date');
  }
  requireEditableJam_(spreadsheet, services, date);
  const sheet = spreadsheet.getSheetByName(JAMS_TAB);
  const columns = mapColumns(JAMS_TAB, JAMS_FIELDS, sheet.getDataRange().getDisplayValues()[0] || []);
  const jam = findJamRow_(sheet, columns, date, spreadsheet);
  if (jam.status === PUBLISHED_STATUS) return { ok: true, alreadyPublished: true };

  const tab = spreadsheet.getSheetByName(date);
  if (!tab) throw new ContractError('empty_setlist', 'The jam has no setlist tab');
  const range = tab.getDataRange();
  const rows = buildSetlist(range.getDisplayValues(), range.getValues(), date);
  if (rows.length === 0) throw new ContractError('empty_setlist', 'The jam has no songs');
  writeJamStatus_(sheet, jam.row, columns, services);
  return { ok: true, alreadyPublished: false };
}

/** Self-cleaning proof that a draft Jams row can be published and read back. */
function checkPublish_(request, spreadsheet, services) {
  const leftover = spreadsheet.getSheetByName(PUBLISH_CHECK_TAB);
  if (leftover) spreadsheet.deleteSheet(leftover);
  const sheet = spreadsheet.insertSheet(PUBLISH_CHECK_TAB);
  sheet.getRange(1, 1, 3, 4).setValues([
    ['fecha', 'hora', 'lugar', 'estado'],
    ['2099-01-01', '21:00', 'Prueba', DRAFT_STATUS],
    ['2099-01-02', '21:00', 'Prueba', DRAFT_STATUS],
  ]);
  const columns = mapColumns(PUBLISH_CHECK_TAB, JAMS_FIELDS, ['fecha', 'hora', 'lugar', 'estado']);
  let published;
  try {
    const jam = findJamRow_(sheet, columns, '2099-01-01', spreadsheet);
    writeJamStatus_(sheet, jam.row, columns, services);
    const range = sheet.getDataRange();
    const jams = buildJams(range.getDisplayValues(), range.getValues(), function () { return null; }, function (value, pattern) {
      return Utilities.formatDate(value, spreadsheet.getSpreadsheetTimeZone(), pattern);
    }).jams;
    published = jams;
  } finally {
    spreadsheet.deleteSheet(sheet);
  }
  if (!published || published.length !== 2 || published[0].status !== PUBLISHED_STATUS || published[1].status !== DRAFT_STATUS) {
    throw new Error('The publish check read back a different status');
  }
  return { ok: true };
}

/** Finds the unique Jams row by normalized date without relying on a blank-row-skipping row index. */
function findJamRow_(sheet, columns, date, spreadsheet) {
  const range = sheet.getDataRange();
  const display = range.getDisplayValues();
  const raw = range.getValues();
  const formatDate = function (value, pattern) {
    return Utilities.formatDate(value, spreadsheet.getSpreadsheetTimeZone(), pattern);
  };
  const matches = [];
  for (let index = 1; index < display.length; index++) {
    const row = display[index] || [];
    if (isoDateCell((raw[index] || [])[columns.date], row[columns.date], formatDate) === date) {
      matches.push({ row: index + 1, status: textCell(row[columns.status]) });
    }
  }
  if (matches.length === 0) throw new ContractError('unknown_jam', 'Jams has no row with the fecha ' + date);
  if (matches.length > 1) throw new ContractError('duplicate_date', 'Jams has the fecha ' + date + ' on more than one row');
  return matches[0];
}

/** Writes the one `estado` cell as text, flushes, and refuses success unless its value reads back. */
function writeJamStatus_(sheet, row, columns, services) {
  const cell = sheet.getRange(row, columns.status + 1);
  cell.setNumberFormat(PLAIN_TEXT_FORMAT);
  cell.setValue(PUBLISHED_STATUS);
  services.flush();
  if (cell.getDisplayValues()[0][0] !== PUBLISHED_STATUS) throw new Error('The published status did not read back');
}

/**
 * Groups the rows to renumber (sorted by position) into runs of consecutive sheet rows, keeping the
 * ascending order across runs. Rows below `deletedRow` have moved up by one. Each run is
 * `{ row, values }`, `values` the new `posicion` texts as a one-column grid.
 */
function positionRuns_(sorted, deletedRow) {
  const runs = [];
  let run = null;
  sorted.forEach(function (item) {
    const row = item.row > deletedRow ? item.row - 1 : item.row;
    if (run === null || row !== run.row + run.values.length) {
      run = { row: row, values: [] };
      runs.push(run);
    }
    run.values.push([String(item.position - 1)]);
  });
  return runs;
}

/** A `posicion` cell as a whole number of at least 1, read as the reads read it; null otherwise. */
function wholePosition_(raw) {
  const text = integerTextCell(raw);
  if (text === null || !/^\d+$/.test(text) || Number(text) < 1) {
    return null;
  }
  return Number(text);
}

/**
 * The 1-based sheet row of the one data row of `sheet` whose trimmed `id_tema` is `songId`, scanned
 * in `values` (the tab's display values, header first, mapped by `columns`). Throws
 * `song_not_in_setlist` when no row has it and `duplicate_song` when more than one does. Reads only;
 * shared by removeSong and setKey (user decision R1: a mutation finds its row by id, never by
 * position).
 */
function findSongRow_(sheet, columns, values, songId) {
  const matches = [];
  for (let r = 1; r < values.length; r++) {
    if (textCell((values[r] || [])[columns.songId]) === songId) {
      matches.push(r);
    }
  }
  if (matches.length === 0) {
    throw notInSetlist_(songId, sheet.getName());
  }
  if (matches.length > 1) {
    throw new ContractError('duplicate_song', songId + ' is on more than one row of ' + sheet.getName());
  }
  return matches[0] + 1;
}

function notInSetlist_(songId, tabName) {
  return new ContractError('song_not_in_setlist', songId + ' is not in the setlist of ' + tabName);
}

/**
 * A self-cleaning deploy check for removeSong's write path: creates the tab SETLIST_CHECK_TAB
 * (deleting a leftover first) with the jam tab header, appends three marker rows at 1, 2 and 3 with
 * addSong's function, removes the second with removeSong's function, reads the tab back with
 * buildSetlist and deletes it. Anything but the first marker at "1" and the third at "2" is an
 * Error, so `internal_error`, after the tab is deleted. Touches no other tab and is never listed by
 * a read.
 */
function checkSetlistRemove_(request, spreadsheet, services) {
  const leftover = spreadsheet.getSheetByName(SETLIST_CHECK_TAB);
  if (leftover) {
    spreadsheet.deleteSheet(leftover);
  }
  const stamp = new Date(services.now).toISOString();
  const markers = ['uno', 'dos', 'tres'].map(function (name, i) {
    return { position: i + 1, songId: 'zz-prueba-' + name, title: 'Prueba ' + name, artist: 'Prueba ' + stamp, key: 'A' };
  });
  const sheet = createSetlistTab_(spreadsheet, SETLIST_CHECK_TAB);
  let rows;
  try {
    markers.forEach(function (marker) {
      appendSetlistRow_(sheet, marker);
    });
    removeSetlistRow_(sheet, markers[1].songId);
    const range = sheet.getDataRange();
    rows = buildSetlist(range.getDisplayValues(), range.getValues(), SETLIST_CHECK_TAB);
  } finally {
    spreadsheet.deleteSheet(sheet);
  }
  const ok = rows.length === 2 &&
    rows[0].songId === markers[0].songId && rows[0].position === '1' &&
    rows[1].songId === markers[2].songId && rows[1].position === '2';
  if (!ok) {
    throw new Error('The setlist remove check read back different rows');
  }
  return { ok: true };
}

/**
 * Sets the key of one song of the upcoming jam's tab (`setKey`, a write action: under the lock).
 * Request `{ date, songId, key }`. Every check runs before anything is written, in this order (the
 * same codes and order as removeSong, plus `invalid_key`): `invalid_date`, `invalid_song`,
 * `invalid_key`, `unknown_jam` / `duplicate_date`, `jam_not_editable`, `song_not_in_setlist` (no
 * tab), `missing_header` / `duplicate_header`, `song_not_in_setlist` (no row with that trimmed
 * `id_tema`), `duplicate_song`. Then only that row's `tono` cell is written, as plain text, in the
 * spelling the admin picked. Catalogo, and so `tono_default`, is never opened (D-08). Answers
 * `{ ok }`.
 */
function setKey_(request, spreadsheet, services) {
  const date = request.date;
  if (typeof date !== 'string' || !isCalendarDate_(date)) {
    throw new ContractError('invalid_date', 'date must be a YYYY-MM-DD calendar date');
  }
  const songId = request.songId;
  if (typeof songId !== 'string' || !SONG_ID_.test(songId)) {
    throw new ContractError('invalid_song', 'songId must be a lowercase slug such as sweet-little-angel');
  }
  const key = request.key;
  if (typeof key !== 'string' || !KEY_.test(key)) {
    throw new ContractError('invalid_key', 'key must be A-G, an optional # or b, and an optional m');
  }

  requireEditableJam_(spreadsheet, services, date);
  const sheet = spreadsheet.getSheetByName(date);
  if (!sheet) {
    throw notInSetlist_(songId, date);
  }
  const display = sheet.getDataRange().getDisplayValues();
  const columns = mapColumns(date, setlistSpecs_(), display.length > 0 ? display[0] : []);
  writeKeyCell_(sheet, findSongRow_(sheet, columns, display, songId), columns, key);
  return { ok: true };
}

/** Writes `key` to the `tono` cell of sheet row `row` (1-based), plain text first, then the value. */
function writeKeyCell_(sheet, row, columns, key) {
  const cell = sheet.getRange(row, columns.key + 1);
  cell.setNumberFormat(PLAIN_TEXT_FORMAT);
  cell.setValue(key);
}

/** Adjusts only open slots, found by song id; validates every input before writing. */
function setSlotCount_(request, spreadsheet, services) {
  const date = request.date;
  if (typeof date !== 'string' || !isCalendarDate_(date)) {
    throw new ContractError('invalid_date', 'date must be a YYYY-MM-DD calendar date');
  }
  const songId = request.songId;
  if (typeof songId !== 'string' || !SONG_ID_.test(songId)) {
    throw new ContractError('invalid_song', 'songId must be a lowercase slug');
  }
  const instrument = request.instrument;
  if (typeof instrument !== 'string' || !Object.prototype.hasOwnProperty.call(SLOT_COLUMNS_, instrument)) {
    throw new ContractError('invalid_instrument', 'instrument must be a default instrument');
  }
  const fields = SLOT_COLUMNS_[instrument];
  const count = request.count;
  if (typeof count !== 'number' || !Number.isInteger(count) || count < 0 || count > fields.length) {
    throw new ContractError('invalid_count', 'count must be an integer within the default lineup');
  }
  requireEditableJam_(spreadsheet, services, date);
  const sheet = spreadsheet.getSheetByName(date);
  if (!sheet) { throw notInSetlist_(songId, date); }
  const display = sheet.getDataRange().getDisplayValues();
  const columns = mapColumns(date, setlistSpecs_(), display.length > 0 ? display[0] : []);
  const row = findSongRow_(sheet, columns, display, songId);
  const changes = planSlotCount_(readSlotCells_(sheet, row, columns), fields, count);
  writeSlotCells_(sheet, row, columns, changes);
  return { ok: true, slots: readSlotCells_(sheet, row, columns) };
}

/** Present column identities, in column order; shared with later assignment mutations. */
function presentSlotFields_(cells, fields) {
  return fields.filter(function (field) { return textCell(cells[field]) !== '-'; });
}

/** Pure plan: remove the last open columns, restore the first absent ones; never move a name. */
function planSlotCount_(cells, fields, count) {
  const present = presentSlotFields_(cells, fields);
  const changes = {};
  if (count < present.length) {
    const open = present.filter(function (field) { return textCell(cells[field]) === null; }).reverse();
    const needed = present.length - count;
    if (open.length < needed) {
      throw new ContractError('slot_filled', 'A slot has a musician; clear it before removing it');
    }
    open.slice(0, needed).forEach(function (field) { changes[field] = '-'; });
  } else if (count > present.length) {
    fields.filter(function (field) { return textCell(cells[field]) === '-'; })
      .slice(0, count - present.length).forEach(function (field) { changes[field] = ''; });
  }
  return changes;
}

/** Write only planned cells, plain text first. */
function writeSlotCells_(sheet, row, columns, changes) {
  Object.keys(changes).forEach(function (field) {
    const cell = sheet.getRange(row, columns[field] + 1);
    cell.setNumberFormat(PLAIN_TEXT_FORMAT);
    cell.setValue(changes[field]);
  });
}

/** Same trimmed display text / empty-to-null rule as the read path. */
function readSlotCells_(sheet, row, columns) {
  const display = sheet.getRange(row, 1, 1, sheet.getLastColumn()).getDisplayValues()[0];
  const cells = {};
  SLOT_FIELDS.forEach(function (spec) { cells[spec.field] = textCell(display[columns[spec.field]]); });
  return cells;
}

/** Assigns a normalized name to a 1-based ordinal among active columns, never replacing a value. */
function assignSlotToRow_(sheet, row, columns, instrument, ordinal, name) {
  const fields = SLOT_COLUMNS_[instrument];
  const cells = readSlotCells_(sheet, row, columns);
  const active = presentSlotFields_(cells, fields);
  if (ordinal > active.length) {
    throw new ContractError('slot_not_in_lineup', 'The requested slot is not in this lineup');
  }
  const field = active[ordinal - 1];
  if (cells[field] !== null) {
    throw new ContractError('slot_taken', 'The requested slot already has a musician');
  }
  const cell = sheet.getRange(row, columns[field] + 1);
  cell.setNumberFormat(PLAIN_TEXT_FORMAT);
  cell.setValue(name);
  return { field: field };
}

/** Validates and normalizes a musician display name without changing existing read parsing. */
function normalizeMusicianName_(value) {
  if (typeof value !== 'string' || /[\u0000-\u001f\u007f-\u009f]/.test(value)) {
    throw new ContractError('invalid_name', 'name must be text');
  }
  const name = value.trim().replace(/\s+/g, ' ');
  if (name.length === 0 || name.length > 40 || /[;()]/.test(name) ||
      /^[=+\-@]/.test(name) || !/[\p{L}\p{N}]/u.test(name)) {
    throw new ContractError('invalid_name', 'name must be a plain musician name of at most 40 characters');
  }
  return name;
}

/** Assigns one musician to an active slot in the upcoming jam. All rejection checks precede writes. */
function assignSlot_(request, spreadsheet, services) {
  const date = request.date;
  if (typeof date !== 'string' || !isCalendarDate_(date)) {
    throw new ContractError('invalid_date', 'date must be a YYYY-MM-DD calendar date');
  }
  const songId = request.songId;
  if (typeof songId !== 'string' || !SONG_ID_.test(songId)) {
    throw new ContractError('invalid_song', 'songId must be a lowercase slug');
  }
  const instrument = request.instrument;
  if (typeof instrument !== 'string' || !Object.prototype.hasOwnProperty.call(SLOT_COLUMNS_, instrument)) {
    throw new ContractError('invalid_slot', 'instrument must identify a default instrument');
  }
  const ordinal = request.ordinal;
  if (typeof ordinal !== 'number' || !Number.isInteger(ordinal) || ordinal < 1 || ordinal > SLOT_COLUMNS_[instrument].length) {
    throw new ContractError('invalid_slot', 'ordinal must identify a default lineup slot');
  }
  const name = normalizeMusicianName_(request.name);
  requireEditableJam_(spreadsheet, services, date);
  const sheet = spreadsheet.getSheetByName(date);
  if (!sheet) { throw notInSetlist_(songId, date); }
  const display = sheet.getDataRange().getDisplayValues();
  const columns = mapColumns(date, setlistSpecs_(), display.length > 0 ? display[0] : []);
  const row = findSongRow_(sheet, columns, display, songId);
  const assigned = assignSlotToRow_(sheet, row, columns, instrument, ordinal, name);
  return { ok: true, column: SLOT_FIELDS.findIndex(function (spec) { return spec.field === assigned.field; }), name: name };
}

/** Clears one occupied active slot only when it still contains the caller's expected cached name. */
function clearSlot_(request, spreadsheet, services) {
  const date = request.date;
  if (typeof date !== 'string' || !isCalendarDate_(date)) {
    throw new ContractError('invalid_date', 'date must be a YYYY-MM-DD calendar date');
  }
  const songId = request.songId;
  if (typeof songId !== 'string' || !SONG_ID_.test(songId)) {
    throw new ContractError('invalid_song', 'songId must be a lowercase slug');
  }
  const instrument = request.instrument;
  if (typeof instrument !== 'string' || !Object.prototype.hasOwnProperty.call(SLOT_COLUMNS_, instrument)) {
    throw new ContractError('invalid_slot', 'instrument must identify a default instrument');
  }
  const ordinal = request.ordinal;
  if (typeof ordinal !== 'number' || !Number.isInteger(ordinal) || ordinal < 1 || ordinal > SLOT_COLUMNS_[instrument].length) {
    throw new ContractError('invalid_slot', 'ordinal must identify a default lineup slot');
  }
  const expectedName = request.expectedName;
  if (typeof expectedName !== 'string' || expectedName.trim().length === 0 || /[\u0000-\u001f\u007f-\u009f]/.test(expectedName)) {
    throw new ContractError('invalid_name', 'expectedName must be non-blank text');
  }
  requireEditableJam_(spreadsheet, services, date);
  const sheet = spreadsheet.getSheetByName(date);
  if (!sheet) { throw notInSetlist_(songId, date); }
  const display = sheet.getDataRange().getDisplayValues();
  const columns = mapColumns(date, setlistSpecs_(), display.length > 0 ? display[0] : []);
  const row = findSongRow_(sheet, columns, display, songId);
  const cleared = clearSlotFromRow_(sheet, row, columns, instrument, ordinal, expectedName);
  return { ok: true, column: SLOT_FIELDS.findIndex(function (spec) { return spec.field === cleared.field; }), slots: readSlotCells_(sheet, row, columns) };
}

/** Compare-and-clear by active U1 ordinal. Rejections never write to the Sheet. */
function clearSlotFromRow_(sheet, row, columns, instrument, ordinal, expectedName) {
  const fields = SLOT_COLUMNS_[instrument];
  const cells = readSlotCells_(sheet, row, columns);
  const active = presentSlotFields_(cells, fields);
  if (ordinal > active.length) {
    throw new ContractError('slot_not_in_lineup', 'The requested slot is not in this lineup');
  }
  const field = active[ordinal - 1];
  if (cells[field] === null) {
    throw new ContractError('slot_empty', 'The requested slot is already open');
  }
  if (cells[field] !== expectedName.trim()) {
    throw new ContractError('slot_changed', 'The requested slot has a different musician');
  }
  const cell = sheet.getRange(row, columns[field] + 1);
  cell.setNumberFormat(PLAIN_TEXT_FORMAT);
  cell.setValue('');
  return { field: field };
}

/** Today in the spreadsheet's time zone, as YYYY-MM-DD: the zone the admin's dates are typed in. */
function todayIso_(spreadsheet, services) {
  return Utilities.formatDate(new Date(services.now), spreadsheet.getSpreadsheetTimeZone(), 'yyyy-MM-dd');
}

/** True for YYYY-MM-DD naming a real calendar day (2026-02-30 is not). */
function isCalendarDate_(text) {
  if (!POST_ISO_DATE_.test(text)) {
    return false;
  }
  const parts = text.split('-').map(Number);
  const day = new Date(Date.UTC(parts[0], parts[1] - 1, parts[2]));
  return day.getUTCFullYear() === parts[0] && day.getUTCMonth() === parts[1] - 1 && day.getUTCDate() === parts[2];
}

/**
 * Throws unless `date` is on exactly one Jams row and that jam is the upcoming one: a known status,
 * not historical (today or later), and no other jam with a known status between today and it.
 * Dates go through buildJams, so they are normalized as the reads normalize them; no jam tab is
 * opened here.
 */
function requireEditableJam_(spreadsheet, services, date) {
  const sheet = spreadsheet.getSheetByName(JAMS_TAB);
  if (!sheet) {
    throw new ContractError('missing_tab', 'The tab ' + JAMS_TAB + ' does not exist');
  }
  const timeZone = spreadsheet.getSpreadsheetTimeZone();
  const formatDate = function (value, pattern) {
    return Utilities.formatDate(value, timeZone, pattern);
  };
  const noTab = function () {
    return null;
  };
  const range = sheet.getDataRange();
  const jams = buildJams(range.getDisplayValues(), range.getValues(), noTab, formatDate).jams;
  const matches = jams.filter(function (jam) {
    return jam.date === date;
  });
  if (matches.length === 0) {
    throw new ContractError('unknown_jam', 'Jams has no row with the fecha ' + date);
  }
  if (matches.length > 1) {
    throw new ContractError('duplicate_date', 'Jams has the fecha ' + date + ' on more than one row');
  }
  const today = todayIso_(spreadsheet, services);
  const known = function (jam) {
    return jam.status === DRAFT_STATUS || jam.status === PUBLISHED_STATUS;
  };
  const earlier = jams.some(function (jam) {
    return known(jam) && jam.date !== null && POST_ISO_DATE_.test(jam.date) && jam.date >= today && jam.date < date;
  });
  if (!known(matches[0]) || date < today || earlier) {
    throw new ContractError('jam_not_editable', 'The jam ' + date + ' is not the upcoming jam');
  }
}

/**
 * The catalog row of `songId`: it must be on exactly one row (matched by trimmed id) with a title
 * and an artist, or `unknown_song`. Only the id, title and artist are used; `tono_default` is never
 * read for the setlist (D-08).
 */
function catalogSong_(spreadsheet, songId) {
  const sheet = spreadsheet.getSheetByName(CATALOG_TAB);
  if (!sheet) {
    throw new ContractError('missing_tab', 'The tab ' + CATALOG_TAB + ' does not exist');
  }
  const rows = sheet.getDataRange().getDisplayValues();
  const columns = catalogColumns_(rows.length > 0 ? rows[0] : []);
  const matches = [];
  for (let r = 1; r < rows.length; r++) {
    const row = rows[r] || [];
    if (textCell(row[columns.id]) === songId) {
      matches.push({ title: textCell(row[columns.title]), artist: textCell(row[columns.artist]) });
    }
  }
  if (matches.length !== 1 || matches[0].title === null || matches[0].artist === null) {
    throw new ContractError('unknown_song', songId + ' is not on exactly one Catalogo row with a titulo and an artista');
  }
  return matches[0];
}

/** 1 + the largest posicion that is a whole number; invalid cells are ignored. */
function nextPosition_(rows) {
  let max = 0;
  rows.forEach(function (row) {
    if (row.position !== null && /^\d+$/.test(row.position)) {
      max = Math.max(max, Number(row.position));
    }
  });
  return max + 1;
}

/** The jam tab specs in documented order: the five setlist columns, the seven slots, Otros. */
function setlistSpecs_() {
  return SETLIST_FIELDS.concat(SLOT_FIELDS, [EXTRA_FIELD]);
}

/** Inserts the tab `name` with the jam tab header row. */
function createSetlistTab_(spreadsheet, name) {
  const headers = setlistSpecs_().map(function (spec) {
    return spec.header;
  });
  const sheet = spreadsheet.insertSheet(name);
  sheet.getRange(1, 1, 1, headers.length).setValues([headers]);
  return sheet;
}

/**
 * Appends `song` on the row after the last one, in the mapped columns only (the tab's own column
 * order, found by header as the reads find it). Each cell is set to plain text before its value is
 * written, so Sheets keeps a title such as 7/4 as text. Slots and Otros are written empty: open.
 */
function appendSetlistRow_(sheet, song) {
  const specs = setlistSpecs_();
  const header = sheet.getRange(1, 1, 1, sheet.getLastColumn()).getDisplayValues()[0];
  const columns = mapColumns(sheet.getName(), specs, header);
  const values = { position: String(song.position), songId: song.songId, title: song.title, artist: song.artist, key: song.key };
  const row = sheet.getLastRow() + 1;
  const cells = [];
  specs.forEach(function (spec) {
    const col = columns[spec.field];
    if (col === undefined) {
      return;
    }
    cells.push({ column: col + 1, value: Object.prototype.hasOwnProperty.call(values, spec.field) ? values[spec.field] : '' });
  });
  cells.sort(function (a, b) { return a.column - b.column; });
  let run = [];
  function flush() {
    if (run.length === 0) return;
    const range = sheet.getRange(row, run[0].column, 1, run.length);
    range.setNumberFormat(PLAIN_TEXT_FORMAT);
    range.setValues([run.map(function (cell) { return cell.value; })]);
    run = [];
  }
  cells.forEach(function (cell) {
    if (run.length > 0 && cell.column !== run[run.length - 1].column + 1) flush();
    run.push(cell);
  });
  flush();
}

function adminSetlistError_(code, message) {
  return { code: code, message: message };
}

/**
 * True when `submitted` is a string exactly equal (case-sensitive) to the trimmed passphrase in
 * Config. The stored value is read first, so an unset passphrase rejects every attempt with
 * `passphrase_not_set` and a blank submission can never match a blank cell.
 */
function passphraseMatches_(spreadsheet, submitted) {
  const stored = readPassphrase_(spreadsheet);
  return typeof submitted === 'string' && submitted === stored;
}

/**
 * The trimmed passphrase from the Config tab. Fails closed: a missing tab, missing headers, a
 * missing row or a blank value is `passphrase_not_set`. Messages never include the value.
 */
function readPassphrase_(spreadsheet) {
  const sheet = spreadsheet.getSheetByName(CONFIG_TAB);
  if (!sheet) {
    throw notSet_();
  }
  const rows = sheet.getDataRange().getDisplayValues();
  if (rows.length === 0) {
    throw notSet_();
  }
  let columns;
  try {
    columns = mapColumns(CONFIG_TAB, CONFIG_COLUMNS, rows[0]);
  } catch (err) {
    if (err instanceof ContractError) {
      throw notSet_();
    }
    throw err;
  }
  for (let i = 1; i < rows.length; i++) {
    const key = String(rows[i][columns.key] === undefined ? '' : rows[i][columns.key]).trim();
    if (key === PASSPHRASE_KEY) {
      const value = String(rows[i][columns.value] === undefined ? '' : rows[i][columns.value]).trim();
      if (value === '') {
        throw notSet_();
      }
      return value;
    }
  }
  throw notSet_();
}

/**
 * The error envelope, as Code.js builds it. Kept here because Code.js does not export its own
 * helpers to the Node tests and this file must not need Code.js to change.
 */
function postErrorBody_(code, message) {
  return { schemaVersion: SCHEMA_VERSION, error: { code: code, message: message } };
}

function postErrorMessage_(err) {
  if (err && typeof err.message === 'string') {
    return err.message;
  }
  return String(err);
}

function notSet_() {
  return new ContractError('passphrase_not_set', 'No admin passphrase is set');
}

if (typeof module !== 'undefined') {
  module.exports = {
    ACTIONS,
    PUBLISH_CHECK_TAB,
    SETLIST_CHECK_TAB,
    WRITE_CHECK_TAB,
    doPost,
    handlePost,
    passphraseMatches_,
    readPassphrase_,
    requirePassphrase_,
  };
}
