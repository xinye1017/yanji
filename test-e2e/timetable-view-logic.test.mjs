/**
 * 每日时间表的纯逻辑验收：日期周条、当前时段、专注分摊、进度与编辑校验。
 *
 * 这些断言不需要模拟器，也不碰桥接；页面只负责把这里的结果画出来。
 */

import test from 'node:test';
import assert from 'node:assert/strict';
import {
  MINUTES_PER_DAY,
  adjustScheduleTime,
  addDays,
  blockActualSeconds,
  blockDurationMinutes,
  blocksTotalMinutes,
  compactDurationMinutes,
  currentBlockId,
  dayOfMonth,
  displayScheduleDate,
  focusOptionsForBlock,
  formatDurationMinutes,
  formatFocusSeconds,
  formatMinuteOfDay,
  isoDate,
  nextBlockDraft,
  parseIsoDate,
  scheduleProgress,
  sortBlocks,
  startOfWeek,
  subjectColorForBlock,
  uniqueTemplateName,
  validateBlockDraft,
  weekDates,
  weekRangeLabel,
  weekdayShort,
} from '../src/screens/timetableViewLogic.ts';

const SUBJECTS = [
  { id: 'math', name: '数学一', colorHex: '#111111', sortOrder: 0, enabled: true, parentId: null, isCategory: true },
  { id: 'calc', name: '高等数学', colorHex: '#111111', sortOrder: 0, enabled: true, parentId: 'math', isCategory: false },
  { id: 'algebra', name: '线性代数', colorHex: '#111111', sortOrder: 1, enabled: true, parentId: 'math', isCategory: false },
  { id: 'major', name: '408专业课', colorHex: '#222222', sortOrder: 1, enabled: true, parentId: null, isCategory: true },
  { id: 'ds', name: '数据结构', colorHex: '#222222', sortOrder: 0, enabled: true, parentId: 'major', isCategory: false },
  { id: 'english', name: '英语一', colorHex: '#333333', sortOrder: 2, enabled: true, parentId: null, isCategory: true },
  { id: 'politics', name: '政治', colorHex: '#444444', sortOrder: 3, enabled: true, parentId: null, isCategory: true },
  { id: 'old', name: '停用学科', colorHex: '#555555', sortOrder: 4, enabled: false, parentId: null, isCategory: true },
];

function block(id, startMinute, endMinute, subjectIds = ['math'], subjectLabel = '数学', title = '复习') {
  return { id, startMinute, endMinute, subjectIds, subjectLabel, title };
}

function session(startTime, endTime, durationSeconds) {
  return {
    id: `s-${startTime}`,
    title: '',
    subjectId: 'calc',
    subjectName: '高等数学',
    subjectColor: '#111111',
    startTime,
    endTime,
    durationSeconds,
    isExam: false,
    note: '',
    pauseCount: 0,
    mode: 'COUNTDOWN',
    taskId: null,
  };
}

test('week strip starts on Monday and crosses month and year boundaries', () => {
  assert.equal(startOfWeek('2026-10-29'), '2026-10-26');
  assert.deepEqual(weekDates('2026-10-29'), [
    '2026-10-26', '2026-10-27', '2026-10-28', '2026-10-29',
    '2026-10-30', '2026-10-31', '2026-11-01',
  ]);
  assert.equal(weekRangeLabel('2026-10-29'), '10月26日 – 11月1日');
  assert.equal(weekRangeLabel('2026-11-04'), '11月2日 – 8日');
  assert.equal(addDays('2026-12-31', 1), '2027-01-01');
  assert.equal(addDays('2026-03-01', -1), '2026-02-28');
  assert.equal(dayOfMonth('2026-11-01'), 1);
  assert.equal(weekdayShort('2026-10-29'), '四');
  assert.equal(displayScheduleDate('2026-10-29'), '10月29日 周四');
});

test('isoDate reads the local calendar day, not the UTC day', () => {
  const lateNight = new Date(2026, 9, 29, 23, 30);
  assert.equal(isoDate(lateNight), '2026-10-29');
  assert.equal(parseIsoDate('2026-10-29').getDate(), 29);
});

test('minute formatting keeps 24:00 as the legal end of day', () => {
  assert.equal(formatMinuteOfDay(0), '00:00');
  assert.equal(formatMinuteOfDay(510), '08:30');
  assert.equal(formatMinuteOfDay(1439), '23:59');
  assert.equal(formatMinuteOfDay(1440), '24:00');
  assert.equal(formatMinuteOfDay(2000), '24:00');
  assert.equal(MINUTES_PER_DAY, 1440);
});

test('duration formatting is human, compact and never negative', () => {
  assert.equal(formatDurationMinutes(480), '8 小时');
  assert.equal(formatDurationMinutes(150), '2 小时 30 分钟');
  assert.equal(formatDurationMinutes(45), '45 分钟');
  assert.equal(compactDurationMinutes(120), '2h');
  assert.equal(compactDurationMinutes(90), '1h30m');
  assert.equal(compactDurationMinutes(30), '30m');
  assert.equal(formatFocusSeconds(0), '0 分钟');
  assert.equal(formatFocusSeconds(30), '不足 1 分钟');
  assert.equal(formatFocusSeconds(3600), '1 小时');
  assert.equal(formatFocusSeconds(4800), '1 小时 20 分');
  assert.equal(formatDurationMinutes(-10), '0 分钟');
});

test('current block uses half-open bounds so adjacent blocks never overlap', () => {
  const blocks = [block('a', 510, 630), block('b', 630, 660)];
  assert.equal(currentBlockId(blocks, 509), null);
  assert.equal(currentBlockId(blocks, 510), 'a');
  assert.equal(currentBlockId(blocks, 629), 'a');
  assert.equal(currentBlockId(blocks, 630), 'b');
  assert.equal(currentBlockId(blocks, 660), null);
});

test('actual focus is allocated by wall-clock overlap, not by session start', () => {
  const date = '2026-10-29';
  const dayStart = new Date(`${date}T00:00:00`).getTime();
  const minute = 60_000;
  const blocks = [
    block('a', 540, 600), // 09:00–10:00
    block('b', 600, 660), // 10:00–11:00
    block('c', 780, 900), // 13:00–15:00
  ];
  const sessions = [
    session(dayStart + 540 * minute, dayStart + 630 * minute, 5400), // 09:00–10:30
    session(dayStart + 780 * minute, dayStart + 900 * minute, 7200), // 13:00–15:00
    session(dayStart + 1000 * minute, dayStart + 1030 * minute, 1800), // outside
  ];
  const actual = blockActualSeconds(blocks, sessions, date);
  assert.equal(actual.a, 3600);
  assert.equal(actual.b, 1800);
  assert.equal(actual.c, 7200);
  assert.equal(actual.outside, undefined);

  // A session whose endTime was not persisted still uses its duration.
  const noEnd = session(dayStart + 540 * minute, 0, 1800);
  assert.equal(blockActualSeconds(blocks, [noEnd], date).a, 1800);
});

test('a session that starts the previous day still counts the part after midnight', () => {
  const date = '2026-10-29';
  const dayStart = new Date(`${date}T00:00:00`).getTime();
  const minute = 60_000;
  const blocks = [block('early', 0, 60)];
  const crossing = session(dayStart - 30 * minute, dayStart + 30 * minute, 3600);
  assert.equal(blockActualSeconds(blocks, [crossing], date).early, 1800);
});

test('progress counts completed blocks and their planned minutes', () => {
  const blocks = [block('a', 510, 630), block('b', 630, 660), block('c', 780, 900)];
  assert.equal(blocksTotalMinutes(blocks), 270);
  assert.equal(blockDurationMinutes(blocks[0]), 120);
  const half = scheduleProgress(blocks, ['a']);
  assert.deepEqual(half, {
    completedCount: 1,
    totalCount: 3,
    completedMinutes: 120,
    totalMinutes: 270,
    ratio: 1 / 3,
  });
  assert.equal(scheduleProgress([], []).ratio, 0);
  assert.equal(scheduleProgress(blocks, ['missing']).completedCount, 0);
});

test('block draft validation matches the native rules', () => {
  const existing = block(null, 510, 630, [], '数学', '核心知识复习');
  const valid = { ...existing, id: null, startMinute: 645, endMinute: 675 };
  assert.equal(validateBlockDraft(valid, [existing]), null);
  // Adjacent blocks are legal.
  assert.equal(validateBlockDraft({ ...existing, startMinute: 630, endMinute: 660 }, [existing]), null);
  assert.match(
    validateBlockDraft({ ...existing, startMinute: 600, endMinute: 660 }, [existing]),
    /重叠/
  );
  assert.match(validateBlockDraft({ ...existing, endMinute: 510 }, [existing]), /结束时间/);
  assert.match(validateBlockDraft({ ...existing, endMinute: 1441 }, [existing]), /结束时间/);
  assert.match(validateBlockDraft({ ...existing, startMinute: 1440 }, [existing]), /开始时间/);
  assert.match(validateBlockDraft({ ...existing, startMinute: 510.5 }, [existing]), /整分钟/);
  assert.match(validateBlockDraft({ ...existing, title: '  ' }, [existing]), /做什么/);
  assert.equal(validateBlockDraft({ ...valid, subjectLabel: '', subjectIds: [] }, [existing]), null);
});

test('dragging snaps to 15 minutes and remains within the day', () => {
  const old = block('old', 485, 565);
  assert.deepEqual([old.startMinute, old.endMinute], [485, 565]);
  const moved = adjustScheduleTime(old, 'move', 30);
  assert.deepEqual([moved.startMinute, moved.endMinute], [510, 600]);
  const start = adjustScheduleTime(old, 'start', 1000);
  assert.deepEqual([start.startMinute, start.endMinute], [555, 570]);
  const end = adjustScheduleTime(old, 'end', 2000);
  assert.deepEqual([end.startMinute, end.endMinute], [480, 1440]);
  assert.equal(adjustScheduleTime(old, 'move', -1000).startMinute, 0);
});

test('a new block takes the first 60-minute gap from 08:00 and falls back when full', () => {
  assert.deepEqual(nextBlockDraft([]), {
    id: null, startMinute: 480, endMinute: 540, subjectIds: [], subjectLabel: '', title: '',
  });
  const morning = [block('a', 480, 540)];
  assert.equal(nextBlockDraft(morning).startMinute, 540);
  assert.equal(nextBlockDraft([block('old', 480, 545)]).startMinute, 555);
  const packed = [block('a', 0, 1440)];
  const fallback = nextBlockDraft(packed);
  assert.equal(fallback.startMinute, 0);
  assert.equal(fallback.endMinute, 30);
});

test('sorting is stable for equal starts and orders by start then end', () => {
  const sorted = sortBlocks([
    block('late', 780, 900),
    block('early', 510, 630),
    block('short', 510, 540),
  ]);
  assert.deepEqual(sorted.map(item => item.id), ['short', 'early', 'late']);
});

test('template copy names avoid collisions', () => {
  assert.equal(uniqueTemplateName(['复习冲刺'], '真题冲刺'), '真题冲刺');
  assert.equal(uniqueTemplateName(['真题冲刺'], '真题冲刺'), '真题冲刺 2');
  assert.equal(uniqueTemplateName(['真题冲刺', '真题冲刺 2'], '真题冲刺'), '真题冲刺 3');
});

test('focus options keep the block subject itself, including whole categories', () => {
  const options = focusOptionsForBlock(['math', 'english', 'major'], SUBJECTS);
  // A timetable block stores the category id, so the session records against
  // that category — not against a child the user never chose.
  assert.deepEqual(options.map(option => option.subjectId), ['math', 'english', 'major']);
  assert.deepEqual(options.map(option => option.label), ['数学一', '英语一', '408专业课']);

  // A disabled category yields nothing; a missing id is ignored.
  assert.deepEqual(focusOptionsForBlock(['old', 'ghost'], SUBJECTS), []);
  // A repeated id appears once; a category and its child are two real choices.
  assert.equal(focusOptionsForBlock(['math', 'math'], SUBJECTS).length, 1);
  assert.equal(focusOptionsForBlock(['math', 'calc'], SUBJECTS).length, 2);
  // A block with no subject cannot start focus.
  assert.deepEqual(focusOptionsForBlock([], SUBJECTS), []);
});

test('subject colour falls back to the neutral token when no subject matches', () => {
  assert.equal(subjectColorForBlock(['math'], SUBJECTS, '#999999'), '#111111');
  assert.equal(subjectColorForBlock(['ghost'], SUBJECTS, '#999999'), '#999999');
  assert.equal(subjectColorForBlock([], SUBJECTS, '#999999'), '#999999');
});
