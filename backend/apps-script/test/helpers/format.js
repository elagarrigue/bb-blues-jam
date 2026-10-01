'use strict';
// Stand-ins for Apps Script's Utilities.formatDate(date, timeZone, pattern), built on Intl, and a
// way to build the Date that Sheets would hand over for a wall-clock value in a given zone.
//
// Only the two patterns the script uses are supported: 'yyyy-MM-dd' and 'HH:mm'.

function wallClock(date, timeZone) {
  const parts = {};
  const format = new Intl.DateTimeFormat('en-US', {
    timeZone,
    hourCycle: 'h23',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  });
  for (const part of format.formatToParts(date)) {
    parts[part.type] = part.value;
  }
  return parts;
}

/** Returns formatDate(date, pattern) for `timeZone`, as Utilities.formatDate would. */
function makeFormatter(timeZone) {
  return function formatDate(date, pattern) {
    const p = wallClock(date, timeZone);
    if (pattern === 'yyyy-MM-dd') {
      return `${p.year}-${p.month}-${p.day}`;
    }
    if (pattern === 'HH:mm') {
      return `${p.hour}:${p.minute}`;
    }
    throw new Error(`format.js does not support the pattern ${pattern}`);
  };
}

/** A fake Utilities global whose formatDate takes the zone per call, like the real one. */
const Utilities = {
  formatDate(date, timeZone, pattern) {
    return makeFormatter(timeZone)(date, pattern);
  },
};

/**
 * The instant whose wall clock in `timeZone` is the given local date and time, the way Sheets
 * builds a typed cell in the spreadsheet's zone. Month is 1-based. Historical offsets with
 * seconds (Buenos Aires in 1899: -4:16:48) are honored because the offset is read back from Intl.
 */
function zonedDate(timeZone, year, month, day, hour = 0, minute = 0) {
  const wanted = Date.UTC(year, month - 1, day, hour, minute, 0);
  let instant = wanted;
  for (let i = 0; i < 3; i++) {
    const p = wallClock(new Date(instant), timeZone);
    const seen = Date.UTC(Number(p.year), Number(p.month) - 1, Number(p.day), Number(p.hour), Number(p.minute), Number(p.second));
    instant += wanted - seen;
  }
  return new Date(instant);
}

const BUENOS_AIRES = 'America/Argentina/Buenos_Aires';

module.exports = { makeFormatter, zonedDate, Utilities, BUENOS_AIRES };
