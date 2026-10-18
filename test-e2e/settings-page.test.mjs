import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './framework/load-typescript.js';

/**
 * The settings page's pure-logic contract: dates, durations and the exam-date
 * calendar grid.
 *
 * No React Native imports, so the same source runs here and in the app. The
 * page-level assertions (what the screen prints, what a save writes) live with
 * the screen itself, once it exists.
 */

const logic = loadTypeScript('src/state/settingsViewLogic.ts');

/**
 * Call arguments and returned values cross the VM boundary the shipped module
 * runs in, so their objects carry that realm's prototype. Compare the payload,
 * not the identity.
 */
const plain = value => JSON.parse(JSON.stringify(value));

test('an ISO date round-trips only when it is a real calendar day', () => {
  assert.deepEqual(plain(logic.parseIsoDate('2026-12-19')), { year: 2026, month: 12, day: 19 });
  assert.equal(logic.parseIsoDate('2026-02-31'), null, 'a rolled-over day is not a date');
  assert.equal(logic.parseIsoDate('2026-13-01'), null);
  assert.equal(logic.parseIsoDate(''), null);
  assert.equal(logic.parseIsoDate('2026/12/19'), null);
  assert.equal(logic.formatIsoDate({ year: 2026, month: 2, day: 9 }), '2026-02-09');
});

test('countdown distinguishes a future exam, the exam day, and a past date', () => {
  assert.equal(logic.examCountdownText('2026-12-19', '2026-10-17'), '距初试 63 天');
  assert.equal(logic.examCountdownText('2026-10-17', '2026-10-17'), '今天就是初试日');
  // The native payload clamps at 0 and so cannot tell these two apart.
  assert.equal(logic.examCountdownText('2026-10-01', '2026-10-17'), '已过设定日期 16 天');
  assert.equal(logic.examCountdownText('', '2026-10-17'), null);
});

test('a stored date prints as a Chinese date and an absent one prints as 未设置', () => {
  assert.equal(logic.formatExamDate('2026-12-19'), '2026年12月19日');
  assert.equal(logic.formatExamDate(''), '未设置');
  assert.equal(logic.formatExamDate('not-a-date'), '未设置');
  assert.equal(logic.weekdayLabelOf('2026-12-19'), '周六');
});

test('the goal reads back at the precision it was stored with', () => {
  assert.equal(logic.formatGoalDuration(6), '6 小时');
  assert.equal(logic.formatGoalDuration(6.5), '6 小时 30 分钟');
  assert.equal(logic.formatGoalDuration(0.5), '30 分钟');
  assert.equal(logic.formatGoalDuration(0), '未设置');
  assert.equal(logic.formatGoalDuration(undefined), '未设置');
  // Minutes are the sheet's unit; hours are the column's unit. Round-tripping
  // must not drift a 30-minute step into 29 or 31 minutes.
  assert.equal(logic.formatGoalDuration(logic.goalHoursFromMinutes(390)), '6 小时 30 分钟');
  assert.equal(logic.goalHoursFromMinutes(-30), 0);
});

test('the month grid is Monday-first and never changes height between months', () => {
  const december = logic.buildMonthGrid({ year: 2026, month: 12 });
  assert.equal(december[0][1], 1, '2026-12-01 is a Tuesday, so Monday stays blank');
  assert.ok(december.every(week => week.length === 7));
  const february = logic.buildMonthGrid({ year: 2027, month: 2 });
  assert.equal(february.flat().filter(day => day !== null).length, 28);
  const leapFebruary = logic.buildMonthGrid({ year: 2028, month: 2 });
  assert.equal(leapFebruary.flat().filter(day => day !== null).length, 29);

  // The sheet must not resize while paging. A 28-day February that starts on a
  // Monday fits in four rows, which is why this asserts the *floor* rather than
  // only the multiple-of-seven padding.
  const rowCounts = new Set();
  for (let year = 2024; year <= 2040; year++) {
    for (let month = 1; month <= 12; month++) {
      rowCounts.add(logic.buildMonthGrid({ year, month }).length);
    }
  }
  assert.deepEqual([...rowCounts].sort(), [5, 6],
    'every month renders five or six rows, so the sheet height is stable');
});

test('month paging crosses year boundaries in both directions', () => {
  assert.deepEqual(plain(logic.shiftMonth({ year: 2026, month: 1 }, -1)), { year: 2025, month: 12 });
  assert.deepEqual(plain(logic.shiftMonth({ year: 2026, month: 12 }, 1)), { year: 2027, month: 1 });
  assert.equal(logic.formatMonthTitle({ year: 2026, month: 12 }), '2026 年 12 月');
});

// ---------------------------------------------------------------------------
// Rendered screen
// ---------------------------------------------------------------------------
