/**
 * Tier 1 — Review screen pure view logic (load-bearing).
 *
 * The rest of the suite drives `MockYanjiBridge`, which proves the DATA contract
 * but nothing about how the review page turns that data into what the user sees.
 * These cases reach the real `src/screens/reviewViewLogic.ts` module — the same
 * approach `m8-shipped-tokens.test.js` takes for `src/theme/tokens.ts` — and pin
 * the three behaviours that were silently wrong before:
 *
 *  1. the date could be pushed past today, rendering a record from the future;
 *  2. sessions and notes were stacked lists instead of one timeline;
 *  3. a day whose only content was an unfinished plan read as "nothing happened".
 *
 * Node 22 strips TypeScript types natively, so the module loads directly.
 */

import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { describe, it, beforeEach } from '../framework/harness.js';
import { assert, assertEqual, assertDeepEqual } from '../framework/assertions.js';

const PROJECT_ROOT = path.resolve(process.cwd());
const LOGIC_PATH = path.join(PROJECT_ROOT, 'src', 'screens', 'reviewViewLogic.ts');

/** Local-clock epoch for a given wall time, so cases do not depend on the runner's timezone. */
function at(year, month, day, hour = 0, minute = 0) {
  return new Date(year, month - 1, day, hour, minute, 0, 0).getTime();
}

function session(id, startTime) {
  return {
    id,
    title: '高等数学 专注',
    subjectId: 'math_advanced',
    subjectName: '高等数学',
    subjectColor: '#2453BF',
    startTime,
    endTime: startTime + 1500_000,
    durationSeconds: 1500,
    isExam: false,
    note: '',
    pauseCount: 0,
    mode: '25分钟专注',
  };
}

function task(id, createdAt, completed = true) {
  return {
    id,
    date: '2026-10-08',
    subjectId: 'english',
    subjectName: '英语一',
    title: '长难句精炼',
    plannedMinutes: 30,
    actualMinutes: completed ? 28 : 0,
    completed,
    createdAt,
  };
}

function note(id, timestamp, category = 'INSPIRATION') {
  return {
    id,
    date: '2026-10-08',
    timestamp,
    content: '倒装语序还是没搞懂',
    isFavorite: false,
    sessionId: null,
    category,
    isResolved: false,
    resolvedAt: null,
  };
}

function timeline(overrides = {}) {
  return {
    date: '2026-10-08',
    formattedDate: '2026年10月8日 星期四',
    totalDurationSeconds: 3000,
    focusCount: 2,
    focusParticipationCount: 2,
    examCount: 0,
    sessions: [],
    tasks: [],
    notes: [],
    ...overrides,
  };
}

export function registerReviewViewLogicTests() {
  let logic = null;

  beforeEach(async () => {
    logic = await import(pathToFileURL(LOGIC_PATH).href);
  });

  describe('Tier 1: Review view logic — date navigation cannot escape today', () => {
    it('steps forward normally while the selected date is in the past', () => {
      assertEqual(
        logic.stepForwardWithinToday('2026-10-06', '2026-10-08'),
        '2026-10-07',
        'Stepping forward from yesterday must land on today'
      );
    });

    it('refuses to step past today instead of browsing an empty future', () => {
      // The bug: pressing 后一天 at today walked into the future, where
      // getDailySnapshot returns an empty summary and the page reads as blank.
      assertEqual(
        logic.stepForwardWithinToday('2026-10-08', '2026-10-08'),
        '2026-10-08',
        'Stepping forward from today must stay on today'
      );
      assertEqual(
        logic.stepForwardWithinToday('2026-10-09', '2026-10-08'),
        '2026-10-09',
        'A date already beyond today must not be silently rewritten by the stepper'
      );
    });

    it('repeated presses at today never advance the date', () => {
      let date = '2026-10-08';
      for (let i = 0; i < 30; i += 1) {
        date = logic.stepForwardWithinToday(date, '2026-10-08');
      }
      assertEqual(date, '2026-10-08', 'Thirty forward presses must leave the date unchanged');
    });

    it('shifting by a negative delta walks backwards across a month boundary', () => {
      assertEqual(logic.shiftIsoDate('2026-10-01', -1), '2026-09-30', 'Must cross into September');
      assertEqual(logic.shiftIsoDate('2026-03-01', -1), '2026-02-28', 'Must cross into February');
    });

    it('month navigation steps by whole calendar months, not 30 days', () => {
      // The bug: CALENDAR_MONTH stepped by ±30 days, so from the 31st the
      // window drifted into the middle of the neighbouring month.
      assertEqual(logic.shiftMonthAnchor('2026-10-31', -1), '2026-09-30',
        'September has no 31st; the anchor must converge to its last day');
      assertEqual(logic.shiftMonthAnchor('2026-10-15', -1), '2026-09-15',
        'A mid-month anchor keeps its day number');
      assertEqual(logic.shiftMonthAnchor('2026-01-15', -1), '2025-12-15',
        'Backward month steps must cross the year boundary');
      assertEqual(logic.shiftMonthAnchor('2026-03-31', -1), '2026-02-28',
        'A common-year February clamps the 31st to the 28th');
      assertEqual(logic.shiftMonthAnchor('2028-03-31', -1), '2028-02-29',
        'A leap-year February clamps the 31st to the 29th');
      assertEqual(logic.shiftMonthAnchor('2026-10-15', 1), '2026-11-15',
        'Forward month steps land in the next calendar month');
    });

    it('forward month navigation never walks past today', () => {
      assertEqual(logic.stepMonthForwardWithinToday('2026-08-10', '2026-10-05'), '2026-09-10',
        'When the next month is still in the past, step into it');
      assertEqual(logic.stepMonthForwardWithinToday('2026-10-05', '2026-10-21'), '2026-10-21',
        'When the next month has not started, clamp to today');
      assertEqual(logic.stepMonthForwardWithinToday('2026-10-21', '2026-10-21'), '2026-10-21',
        'Already at today, a forward press must not move into the future');
    });
  });

  describe('Tier 1: Review view logic — one chronological timeline', () => {
    it('interleaves sessions and notes by their real event times', () => {
      const entries = logic.buildTimelineEntries(
        timeline({
          // Deliberately listed sessions-first, newest-first inside each group —
          // the merge must not inherit any of that ordering.
          sessions: [session('s-late', at(2026, 10, 8, 14, 0)), session('s-early', at(2026, 10, 8, 9, 0))],
          notes: [note('n-noon', at(2026, 10, 8, 12, 0)), note('n-night', at(2026, 10, 8, 21, 0))],
        })
      );

      assertDeepEqual(
        entries.map(entry => entry.key),
        ['note-n-night', 'session-s-late', 'note-n-noon', 'session-s-early'],
        'Entries must come out newest-first across both sources, not grouped by type'
      );
    });

    it('keeps a deterministic order for entries that share a timestamp', () => {
      const sameTime = at(2026, 10, 8, 9, 0);
      const built = () =>
        logic
          .buildTimelineEntries(
            timeline({
              sessions: [session('s-b', sameTime)],
              notes: [note('n-a', sameTime)],
            })
          )
          .map(entry => entry.key);

      const first = built();
      // Reversing the source arrays must not change the result: the tie-break is
      // the entry key, not the arrival order.
      const reversed = logic
        .buildTimelineEntries(
          timeline({
            notes: [note('n-a', sameTime)],
            sessions: [session('s-b', sameTime)],
          })
        )
        .map(entry => entry.key);
      assertDeepEqual(reversed, first, 'Tie-break ordering must be stable across calls');
      assertDeepEqual(
        first,
        ['note-n-a', 'session-s-b'],
        'Equal timestamps must fall back to the entry key in ascending order'
      );
    });

    it('returns nothing for a day the bridge has not loaded yet', () => {
      assertDeepEqual(logic.buildTimelineEntries(null), [], 'A null timeline must yield no entries');
    });

    it('preserves each entry kind so the row renderer can tell them apart', () => {
      const entries = logic.buildTimelineEntries(
        timeline({
          sessions: [session('s1', at(2026, 10, 8, 9, 0))],
          notes: [note('n1', at(2026, 10, 8, 11, 0))],
        })
      );
      assertDeepEqual(
        entries.map(entry => entry.kind),
        ['note', 'session'],
        'The kind discriminator must survive the merge'
      );
    });

    it('keeps plans out of the timeline while leaving the snapshot untouched', () => {
      // A plan has no completion timestamp in the domain model, so the screen
      // renders plans in their own sections instead of guessing a slot for them
      // in the timeline.
      const snapshot = timeline({ tasks: [
        task('done', at(2026, 10, 8, 9), true),
        task('pending', at(2026, 10, 8, 10), false),
      ] });
      const entries = logic.buildTimelineEntries(snapshot);
      assertDeepEqual(entries.map(entry => entry.key), [], 'No plan may enter the timeline');
      assertEqual(snapshot.tasks.length, 2, 'Building the timeline must not mutate the snapshot');
      assert(logic.dayHasRecords(entries, [snapshot.tasks[1]]), 'An unfinished plan is still a day record');
    });
  });

  describe('Tier 1: Review view logic — an unfinished plan is still a record', () => {
    it('counts a day that has only an uncompleted task', () => {
      // The bug: hasRecords only looked at sessions / completedTasks / notes, so
      // a planned-but-unfinished day rendered the "这一天还没有记录" empty state.
      const entries = logic.buildTimelineEntries(timeline());
      assert(
        !logic.dayHasRecords(entries, []),
        'A genuinely empty day must still report no records'
      );
      assert(
        logic.dayHasRecords(entries, [task('t-open', at(2026, 10, 8, 8, 0), false)]),
        'An uncompleted plan means the day happened'
      );
    });

    it('ignores future cells when counting days that hold study time', () => {
      const days = [
        { durationSeconds: 1800, isFuture: false },
        { durationSeconds: 0, isFuture: false },
        { durationSeconds: 3600, isFuture: true },
      ];
      assertEqual(logic.countWorkedDays(days), 1, 'A calendar-month cell after today must not count');
    });

    it('counts elapsed days with the same caliber as the worked-day numerator', () => {
      // 自然月的窗口一直排到月末，但今天只过去了其中几天。分母用 days.length 会让
      // 「有记录」显示 8 / 31 —— 分子已经过滤掉未来格，分母没有，两者不是一个口径。
      const days = [
        { durationSeconds: 1800, isFuture: false },
        { durationSeconds: 0, isFuture: false },
        { durationSeconds: 3600, isFuture: true },
      ];
      assertEqual(logic.countElapsedDays(days), 2, 'Future cells never join the denominator');
      assert(
        logic.countWorkedDays(days) <= logic.countElapsedDays(days),
        'Worked days are a subset of elapsed days, so the ratio can never exceed 1'
      );
      assertEqual(logic.countElapsedDays([]), 0, 'An empty window has no elapsed days');
    });

    it('stacks each elapsed day by subject and drops days that have not happened', () => {
      const days = [
        { date: '2026-10-07', dayLabel: '10-07', isToday: false, isFuture: false, durationSeconds: 4800 },
        { date: '2026-10-08', dayLabel: '10-08', isToday: true, isFuture: false, durationSeconds: 0 },
        { date: '2026-10-09', dayLabel: '10-09', isToday: false, isFuture: true, durationSeconds: 0 },
      ];
      const distribution = [
        {
          subjectId: 'math', subjectName: '数学', subjectColor: '#111111', minutes: 80, share: 0.8,
          dailyMinutes: { '2026-10-07': 60, '2026-10-08': 0, '2026-10-09': 0 },
        },
        {
          subjectId: 'eng', subjectName: '英语', subjectColor: '#222222', minutes: 20, share: 0.2,
          dailyMinutes: { '2026-10-07': 20, '2026-10-08': 0, '2026-10-09': 0 },
        },
      ];

      const columns = logic.buildTrendColumns(days, distribution);

      assertEqual(columns.length, 2, 'The future cell must never become a column');
      assertEqual(columns[0].totalMinutes, 80, 'A column sums its own segments');
      assertDeepEqual(
        columns[0].segments.map(s => s.subjectId),
        ['math', 'eng'],
        'Segment order follows the distribution so one subject keeps one colour'
      );
      assertEqual(columns[1].totalMinutes, 0, 'A zero day keeps its slot with no segments');
      assertEqual(columns[1].segments.length, 0);
    });

    it('names the tallest day and stays silent when nothing was studied', () => {
      const columns = [
        { date: 'a', dayLabel: '10-06', isToday: false, totalMinutes: 133, segments: [] },
        { date: 'b', dayLabel: '10-08', isToday: true, totalMinutes: 40, segments: [] },
      ];
      assertEqual(logic.peakTrendDay(columns).dayLabel, '10-06');
      assertEqual(logic.peakTrendDay([{ ...columns[1], totalMinutes: 0 }]), null);
    });

    it('compares against the previous period in whole minutes, not percent', () => {
      assertEqual(logic.periodDelta(7200, 3600).label, '较上一周期 +1 小时');
      assertEqual(logic.periodDelta(7200, 3600).tone, 'up');
      assertEqual(logic.periodDelta(3600, 7200).label, '较上一周期 -1 小时');
      assertEqual(logic.periodDelta(3600, 7200).tone, 'down');
      assertEqual(logic.periodDelta(3600, 3600).label, '与上一周期持平');
      assertEqual(logic.periodDelta(3600, 3600).tone, 'flat');
      assertEqual(logic.periodDelta(3600, 0).label, '上一周期没有记录');
      assertEqual(logic.periodDelta(3600, 0).tone, null, 'Nothing to compare against carries no tone');
      assertEqual(logic.periodDelta(3600, null).tone, null);
    });

    it('steps the single-day window back one day and calendar scopes by one period', () => {
      assertDeepEqual(logic.previousWindowArgs('TODAY', '2026-10-10'), {
        scope: 'TODAY',
        periodsBack: 0,
        anchorDate: '2026-10-09',
      });
      assertDeepEqual(logic.previousWindowArgs('CALENDAR_WEEK', '2026-10-10'), {
        scope: 'CALENDAR_WEEK',
        periodsBack: 1,
        anchorDate: '2026-10-10',
      });
      assertDeepEqual(logic.previousWindowArgs('CALENDAR_MONTH', '2026-10-10'), {
        scope: 'CALENDAR_MONTH',
        periodsBack: 1,
        anchorDate: '2026-10-10',
      });
    });

    it('spreads a session across the hours it actually touches', () => {
      // 13:40–15:10 is 20m in 13时, 60m in 14时, 10m in 15时. Attributing the
      // whole session to its start hour would put 90 minutes at 13时 and lie
      // about when the day's focus happened.
      const buckets = logic.buildHourBuckets([
        { startTime: at(2026, 10, 8, 13, 40), durationSeconds: 90 * 60 },
      ]);

      assertEqual(buckets.length, 24, 'A day always renders all 24 hour slots');
      assertEqual(buckets[13].minutes, 20);
      assertEqual(buckets[14].minutes, 60);
      assertEqual(buckets[15].minutes, 10);
      assertEqual(
        buckets.reduce((sum, bucket) => sum + bucket.minutes, 0),
        90,
        'The hours must add back up to the session length'
      );
      assertEqual(logic.peakHourBucket(buckets).hour, 14, 'The busiest hour is the full one');
      assertEqual(
        logic.peakHourBucket(buckets.map(b => ({ ...b, minutes: 0 }))),
        null,
        'An empty day has no busiest hour and must report null, not hour 0'
      );
    });
  });

  describe('Tier 1: Review view logic — human-readable labels', () => {
    it('names the recent days relatively and stops helping past a month', () => {
      assertEqual(logic.relativeDayLabel('2026-10-08', '2026-10-08'), '今天', 'Today');
      assertEqual(logic.relativeDayLabel('2026-10-07', '2026-10-08'), '昨天', 'Yesterday');
      assertEqual(logic.relativeDayLabel('2026-10-06', '2026-10-08'), '前天', 'Two days back');
      assertEqual(logic.relativeDayLabel('2026-10-03', '2026-10-08'), '5 天前', 'Within a month');
      assertEqual(
        logic.relativeDayLabel('2026-08-01', '2026-10-08'),
        null,
        'Beyond 30 days a relative name stops helping and the absolute date is shown'
      );
      assertEqual(logic.relativeDayLabel('2026-10-08', '2026-10-08'), '今天', 'Never negative for a future date');
    });

    it('refuses to invent a name for a malformed date', () => {
      assertEqual(logic.relativeDayLabel('not-a-date', '2026-10-08'), null, 'Malformed input must not become "NaN 天前"');
    });

    it('formats durations without pretending a fraction of a minute is zero', () => {
      assertEqual(logic.formatDuration(0), '0 分钟', 'No record at all');
      assertEqual(logic.formatDuration(20), '不足 1 分钟', 'Sub-minute work must not read as nothing');
      assertEqual(logic.formatDuration(1500), '25 分钟', 'Whole minutes');
      assertEqual(logic.formatDuration(5400), '1 小时 30 分', 'Hours and minutes');
      assertEqual(logic.formatDuration(7200), '2 小时', 'Exact hours drop the minutes');
    });

    it('renders a share that is too small to round up as "<1%" rather than 0%', () => {
      assertEqual(logic.formatPercent(0), '0%', 'Zero share');
      assertEqual(logic.formatPercent(0.000_4), '<1%', 'A real but tiny slice must not read as nothing');
      assertEqual(logic.formatPercent(0.5), '50%', 'Half the window');
      assertEqual(logic.formatPercent(Number.NaN), '0%', 'NaN must never reach the screen');
    });
  });

  describe('Tier 1: Review view logic — a subject drill-down reconciles with its own row', () => {
    it('keeps dailyMinutes summing to the subject minutes reported by the bridge', async () => {
      const { MockYanjiBridge } = await import('../contracts/mock-bridge.js');
      const bridge = new MockYanjiBridge();

      await bridge.startFocus('sub-math', '数学', 'STOPWATCH');
      bridge.advanceTime(3600);
      await bridge.completeTimer();
      await bridge.startFocus('sub-eng', '英语', 'STOPWATCH');
      bridge.advanceTime(1800);
      await bridge.completeTimer();

      const overview = await bridge.getReviewOverview('CALENDAR_WEEK', 0);
      const windowDates = overview.days.map(day => day.date);

      for (const slice of overview.subjectDistribution) {
        const summed = windowDates.reduce(
          (total, date) => total + (slice.dailyMinutes[date] ?? 0),
          0
        );
        assertEqual(
          summed,
          slice.minutes,
          `Subject ${slice.subjectName}: per-day minutes must add up to its own total`
        );
        assertDeepEqual(
          Object.keys(slice.dailyMinutes).sort(),
          [...windowDates].sort(),
          `Subject ${slice.subjectName}: dailyMinutes must cover every window day`
        );
      }
    });
  });

  describe('Tier 1: Review view logic — axis labels thin out on crowded windows', () => {
    it('keeps every label on a 7-day window', async () => {
      const logic = await import(pathToFileURL(LOGIC_PATH).href);
      const days = Array.from({ length: 7 }, (_, i) => ({
        date: `2026-10-0${i + 1}`,
        dayLabel: `0${i + 1}日`,
        isToday: i === 6,
      }));
      assertEqual(logic.axisLabels(days).length, 7, 'A week must label every column');
    });

    it('thins a 31-day month but never drops today', async () => {
      const logic = await import(pathToFileURL(LOGIC_PATH).href);
      const days = Array.from({ length: 31 }, (_, i) => ({
        date: `2026-10-${String(i + 1).padStart(2, '0')}`,
        dayLabel: `${i + 1}日`,
        isToday: i === 30,
      }));

      const labelled = logic.axisLabels(days);
      // On the real device every「N日」was drawn in a ~30px column and the axis
      // became an unreadable wall of overlapping text.
      assert(labelled.length < 31, 'A 31-day month must not label every single day');
      assert(labelled.length >= 6, 'Thinning must still leave enough labels to read the axis');

      const labelledDates = labelled.map(day => day.date);
      assert(
        labelledDates.includes(days[30].date),
        'Today must always be labelled, otherwise the highlighted column is ambiguous'
      );
      assertEqual(labelled[0].date, days[0].date, 'The window must start on a labelled column');
      assertEqual(new Set(labelledDates).size, labelledDates.length, 'Labels must not repeat a date');
    });

    it('thins a 30-day window too, because its columns are just as narrow', async () => {
      const logic = await import(pathToFileURL(LOGIC_PATH).href);
      const days = Array.from({ length: 30 }, (_, i) => ({
        date: `2026-09-${String(i + 1).padStart(2, '0')}`,
        dayLabel: String(i + 1),
        isToday: false,
      }));
      assert(
        logic.axisLabels(days).length < 30,
        '30 columns are just as crowded as 31 and must be thinned the same way'
      );
    });

    it('never labels adjacent columns next to today, avoiding overlapping text', async () => {
      const logic = await import(pathToFileURL(LOGIC_PATH).href);
      // Index 9 (10th day) is today, while index 10 would normally hit stride 5 (10 % 5 === 0)
      const days = Array.from({ length: 31 }, (_, i) => ({
        date: `2026-10-${String(i + 1).padStart(2, '0')}`,
        dayLabel: `${i + 1}日`,
        isToday: i === 9,
      }));
      const labelled = logic.axisLabels(days);
      const labelledDates = new Set(labelled.map(d => d.date));
      assert(labelledDates.has(days[9].date), 'Today must always be labelled');
      assert(!labelledDates.has(days[10].date), 'Column adjacent to today must not be labelled');
      assert(!labelledDates.has(days[8].date), 'Column adjacent to today must not be labelled');
    });
  });

  describe('Tier 1: Review view logic — date and average duration formatting', () => {
    it('formats ISO dates as MM月/DD日 without YY', async () => {
      const logic = await import(pathToFileURL(LOGIC_PATH).href);
      assertEqual(logic.formatMonthDaySlash('2026-10-10'), '10月/10日');
      assertEqual(logic.formatMonthDaySlash('2026-05-08'), '05月/08日');
    });

    it('formats daily average duration with per-day suffix', async () => {
      const logic = await import(pathToFileURL(LOGIC_PATH).href);
      assertEqual(logic.formatDailyAverageDuration(0), '0 分钟/天');
      assertEqual(logic.formatDailyAverageDuration(720), '12 分钟/天');
      assertEqual(logic.formatDailyAverageDuration(3600), '1 小时/天');
      assertEqual(logic.formatDailyAverageDuration(5400), '1 小时 30 分/天');
    });

    it('computes collision-free layout for chart axis labels', async () => {
      const logic = await import(pathToFileURL(LOGIC_PATH).href);

      // 7-day window
      const weekDays = Array.from({ length: 7 }, (_, i) => ({
        date: `2026-10-0${i + 1}`,
        dayLabel: `10-0${i + 1}`,
        isToday: i === 6,
      }));
      const weekLayout = logic.computeAxisLabelsLayout(weekDays, 340, { labelWidth: 38, gap: 4, minSpacing: 2 });
      assertEqual(weekLayout.length, 7, 'All 7 days should be laid out on 340px width');

      // Verify no overlaps in weekLayout
      for (let i = 0; i < weekLayout.length - 1; i++) {
        assert(
          weekLayout[i].left + weekLayout[i].width <= weekLayout[i + 1].left + 0.1,
          `Label ${i} and ${i + 1} must not overlap`
        );
      }

      // 30-day window
      const monthDays = Array.from({ length: 30 }, (_, i) => ({
        date: `2026-09-${String(i + 1).padStart(2, '0')}`,
        dayLabel: `09-${String(i + 1).padStart(2, '0')}`,
        isToday: i === 29,
      }));
      const monthLayout = logic.computeAxisLabelsLayout(monthDays, 340, { labelWidth: 38, gap: 4, minSpacing: 2 });
      assert(monthLayout.length >= 5 && monthLayout.length <= 8, 'Should produce 5-8 thinned labels');
      assert(monthLayout.some(item => item.isToday), 'Today must be present in layout');

      // Verify no overlaps in monthLayout
      for (let i = 0; i < monthLayout.length - 1; i++) {
        assert(
          monthLayout[i].left + monthLayout[i].width <= monthLayout[i + 1].left + 0.1,
          `Month label ${i} and ${i + 1} must not overlap`
        );
      }

      // Zero width or empty array returns empty
      assertEqual(logic.computeAxisLabelsLayout([], 340).length, 0);
      assertEqual(logic.computeAxisLabelsLayout(monthDays, 0).length, 0);
    });
  });

  describe('Tier 1: Review view logic — the date filmstrip', () => {
    it('frames the calendar week containing the selected day, Monday first', () => {
      // 2026-10-14 is a Wednesday; its week is Mon 12 → Sun 18.
      const dates = logic.weekDatesOf('2026-10-14');
      assertDeepEqual(dates, [
        '2026-10-12', '2026-10-13', '2026-10-14', '2026-10-15',
        '2026-10-16', '2026-10-17', '2026-10-18',
      ]);
      assertEqual(logic.weekDatesOf('2026-10-12')[0], '2026-10-12', 'A Monday starts its own week');
      // A Sunday belongs to the week that began six days earlier, not the next one.
      assertEqual(logic.weekDatesOf('2026-10-18')[0], '2026-10-12');
      assertEqual(logic.weekDatesOf('2026-11-01')[0], '2026-10-26', 'The frame rolls across a month boundary');
    });

    it('marks the selected, today and future cells without inventing a dot', () => {
      const dots = new Map([['2026-10-13', '#16A34A']]);
      const cells = logic.buildFilmstrip('2026-10-14', '2026-10-14', '2026-10-14', dots);
      assertEqual(cells.length, 7);
      assertEqual(cells[2].date, '2026-10-14');
      assertEqual(cells[2].isSelected, true);
      assertEqual(cells[2].isToday, true);
      assertEqual(cells[2].dayOfMonth, '14', 'The cell shows the day of month, not the ISO string');
      assertEqual(cells[2].dotColor, null, 'A day with no study time gets no dot at all');
      assertEqual(cells[1].dotColor, '#16A34A', 'The studied day keeps its subject colour');
      assertEqual(cells[3].isFuture, true, 'Days after today are not selectable');
      assertEqual(cells[6].isFuture, true);
      assertEqual(cells[0].weekdayLabel, '一', 'Columns are labelled by weekday initial');
      assertEqual(cells[6].weekdayLabel, '日');
    });

    it('shows a paged week while the selection stays on its own day', () => {
      // The strip was rolled back one week; the timeline still reads 10-14.
      const cells = logic.buildFilmstrip('2026-10-05', '2026-10-14', '2026-10-14', new Map());
      assertEqual(cells[0].date, '2026-10-05');
      assertEqual(cells[6].date, '2026-10-11');
      assertEqual(cells.some(cell => cell.isSelected), false,
        'A paged-back week holds no selected cell — the selection is elsewhere');
      assertEqual(cells.every(cell => !cell.isFuture), true,
        'A fully past week has no future cells');
    });

    it('refuses to page past the current week but always allows going back', () => {
      // 2026-10-14 is a Wednesday; its week is Mon 12 → Sun 18.
      assertEqual(logic.weekStartOf('2026-10-14'), '2026-10-12');
      assertEqual(logic.weekStartOf('2026-10-18'), '2026-10-12', 'A Sunday belongs to the week that opened six days earlier');
      assertEqual(logic.canPageWeek('2026-10-12', '2026-10-14', 1), false,
        'The week containing today is the forward ceiling');
      assertEqual(logic.canPageWeek('2026-10-05', '2026-10-14', 1), true,
        'An earlier week may roll forward');
      assertEqual(logic.canPageWeek('2026-10-12', '2026-10-14', -1), true,
        'The current week may always roll back');
      assertEqual(logic.canPageWeek('2026-10-05', '2026-10-14', -1), true,
        'Backwards there is no floor — an empty week is a legitimate answer');
      assertEqual(logic.canPageWeek('2026-09-28', '2026-10-05', 1), true,
        'A week immediately before the current one may roll forward onto it');
    });

    it('picks each day\'s dominant subject and ignores zero-minute slices', () => {
      const days = [
        { date: '2026-10-12', durationSeconds: 3600 },
        { date: '2026-10-13', durationSeconds: 1800 },
        { date: '2026-10-14', durationSeconds: 0 },
      ];
      const distribution = [
        { subjectId: 'math', subjectColor: '#356AE6', dailyMinutes: { '2026-10-12': 30, '2026-10-13': 10 } },
        { subjectId: 'english', subjectColor: '#16A34A', dailyMinutes: { '2026-10-12': 30, '2026-10-13': 20 } },
      ];
      const colors = logic.dominantSubjectColors(days, distribution);
      // A tie resolves by subject id ('english' < 'math'), so two renders cannot
      // swap the dot colour depending on the order the slices arrived in.
      assertEqual(colors.get('2026-10-12'), '#16A34A', 'A tie breaks deterministically toward the lower id');
      assertEqual(colors.get('2026-10-13'), '#16A34A', 'The larger slice wins outright');
      assertEqual(colors.has('2026-10-14'), false, 'A zero day has no dominant subject');
    });
  });

  describe('Tier 1: Review view logic — the day summary and bound plans', () => {
    it('renders the day duration as one big-minute number without rounding a real sub-minute to zero', () => {
      assertEqual(logic.durationMetricMinutes(0), '0');
      assertEqual(logic.durationMetricMinutes(30), '<1');
      assertEqual(logic.durationMetricMinutes(60), '1');
      assertEqual(logic.durationMetricMinutes(1800), '30');
      assertEqual(logic.durationMetricMinutes(4500), '75');
      assertEqual(logic.durationMetricMinutes(-5), '0');
    });

    it('names a session\'s bound plan by id alone, never by title or subject', () => {
      const tasks = [
        { id: 't-1', title: '王道操作系统第四章' },
        { id: 't-2', title: '王道操作系统第四章' },
      ];
      assertEqual(logic.boundTaskTitle('t-2', tasks), '王道操作系统第四章');
      // Two same-named plans are exactly why a title/subject match would mislabel.
      assertEqual(logic.boundTaskTitle('t-missing', tasks), null, 'A deleted plan leaves no chip');
      assertEqual(logic.boundTaskTitle(null, tasks), null, 'An unbound session leaves no chip');
    });
  });

  describe('Tier 1: Review view logic — period-over-period comparison', () => {
    it('reports a signed percent only when there is a baseline to divide by', () => {
      assertEqual(logic.periodDeltaPercent(7200, 3600), 1);
      assertEqual(logic.periodDeltaPercent(1800, 3600), -0.5);
      assertEqual(logic.periodDeltaPercent(3600, 3600), 0);
      // No baseline: an absolute delta is the honest number, so the percent is absent.
      assertEqual(logic.periodDeltaPercent(3600, 0), null);
      assertEqual(logic.periodDeltaPercent(3600, null), null);
    });

    it('counts goal-reaching days over elapsed days only', () => {
      const days = [
        { durationSeconds: 7200, isFuture: false },
        { durationSeconds: 3600, isFuture: false },
        { durationSeconds: 10800, isFuture: true },
      ];
      assertDeepEqual(logic.goalReachedDays(days, 3600), { reached: 2, elapsed: 2 });
      assertDeepEqual(logic.goalReachedDays(days, 7200), { reached: 1, elapsed: 2 });
      // No goal configured: the caller omits the line rather than claiming 0/2.
      assertEqual(logic.goalReachedDays(days, null), null);
      assertEqual(logic.goalReachedDays(days, 0), null);
    });
  });
}

