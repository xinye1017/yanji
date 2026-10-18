/**
 * Yanji "Review" pure view logic — everything the review screen computes rather
 * than renders.
 *
 * Why this file exists: the timeline merge, the date clamp and the relative day
 * naming are the parts that can silently be WRONG (a future date renders a blank
 * day, a session sorted by the wrong clock lands in the wrong slot). Keeping them
 * out of the .tsx means `test-e2e/tier1-features/m9-review-view-logic.test.js`
 * can import and assert them directly — the same trick
 * `m8-shipped-tokens.test.js` uses to reach `src/theme/tokens.ts`.
 *
 * No React and no React Native imports live here on purpose.
 */

import type {
  DailySession,
  DailySnapshot,
  NoteCategory,
  ReviewPeriodDay,
  ReviewScope,
  ReviewSubjectSlice,
  StudyTask,
} from '../bridge';

const MS_PER_DAY = 86_400_000;

export function shiftIsoDate(iso: string, deltaDays: number): string {
  const parsed = new Date(`${iso}T00:00:00`);
  parsed.setDate(parsed.getDate() + deltaDays);
  const month = `${parsed.getMonth() + 1}`.padStart(2, '0');
  const day = `${parsed.getDate()}`.padStart(2, '0');
  return `${parsed.getFullYear()}-${month}-${day}`;
}

/**
 * Today is a hard ceiling: a day that has not happened yet holds no record, so
 * stepping forward must stop here. Comparing ISO strings is exact for the
 * fixed-width `yyyy-MM-dd` form and needs no date parsing.
 */
export function stepForwardWithinToday(iso: string, today: string): string {
  return iso < today ? shiftIsoDate(iso, 1) : iso;
}

/**
 * "今天 / 昨天 / 前天 / N 天前" — or null once the date is old enough that a
 * relative name stops helping. An empty day still deserves a real name.
 */
export function relativeDayLabel(iso: string, today: string): string | null {
  const parsed = new Date(`${iso}T00:00:00`);
  if (Number.isNaN(parsed.getTime())) return null;
  const diff = Math.round((new Date(`${today}T00:00:00`).getTime() - parsed.getTime()) / MS_PER_DAY);
  if (diff === 0) return '今天';
  if (diff === 1) return '昨天';
  if (diff === 2) return '前天';
  if (diff > 2 && diff <= 30) return `${diff} 天前`;
  return null;
}

export function formatClockFromEpoch(epochMs: number): string {
  const date = new Date(epochMs);
  return `${`${date.getHours()}`.padStart(2, '0')}:${`${date.getMinutes()}`.padStart(2, '0')}`;
}

export function formatDuration(totalSeconds: number): string {
  const minutes = Math.round(totalSeconds / 60);
  if (minutes <= 0) return totalSeconds > 0 ? '不足 1 分钟' : '0 分钟';
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours > 0 && rest > 0) return `${hours} 小时 ${rest} 分`;
  if (hours > 0) return `${hours} 小时`;
  return `${rest} 分钟`;
}

/** Share of the window, rounded for reading; a zero-length window stays at 0. */
export function formatPercent(share: number): string {
  if (!Number.isFinite(share) || share <= 0) return '0%';
  const percent = share * 100;
  if (percent < 1) return '<1%';
  return `${Math.round(percent)}%`;
}

// ---------------------------------------------------------------------------
// The merged timeline
// ---------------------------------------------------------------------------

export type TimelineEntry =
  | { key: string; kind: 'session'; at: number; session: DailySession }
  | {
      key: string;
      kind: 'note';
      at: number;
      noteId: string;
      timestamp: number;
      content: string;
      isFavorite: boolean;
      category?: NoteCategory;
      isResolved?: boolean;
      resolvedAt?: number | null;
    };

/**
 * One chronological stream out of two Room sources.
 *
 * Sort keys are the real event times: a session starts at `startTime`, a note
 * was written at `timestamp`.
 *
 * Newest first. The key breaks ties so the order never depends on the order in
 * which the two source arrays happened to arrive.
 */
export function buildTimelineEntries(snapshot: DailySnapshot | null): TimelineEntry[] {
  if (!snapshot) return [];
  const entries: TimelineEntry[] = [
    ...snapshot.sessions.map(
      (session): TimelineEntry => ({
        key: `session-${session.id}`,
        kind: 'session',
        at: session.startTime,
        session,
      })
    ),
    ...snapshot.notes.map(
      (note): TimelineEntry => ({
        key: `note-${note.id}`,
        kind: 'note',
        at: note.timestamp,
        noteId: note.id,
        timestamp: note.timestamp,
        content: note.content,
        isFavorite: note.isFavorite,
        category: note.category,
        isResolved: note.isResolved,
        resolvedAt: note.resolvedAt,
      })
    ),
  ];
  return entries.sort((a, b) => b.at - a.at || a.key.localeCompare(b.key));
}

/**
 * A day has a record when anything about it is real — including a plan the user
 * never completed. Gating only on sessions/tasks/notes made a day with an
 * unfinished task render as "nothing happened here".
 */
export function dayHasRecords(entries: TimelineEntry[], dayTasks: StudyTask[]): boolean {
  return entries.length > 0 || dayTasks.length > 0;
}

/**
 * The summary strip's first cell: the day's effective focus time as one big
 * number of minutes plus its unit. A single number keeps the three-column strip
 * scannable; splitting hours and minutes into four glyph groups made the middle
 * column wider than the others and broke the shared baseline.
 *
 * Sub-minute work still reports 「<1 分钟」 rather than rounding a real record
 * down to zero.
 */
export function durationMetricMinutes(totalSeconds: number): string {
  const safe = Math.max(0, totalSeconds);
  if (safe <= 0) return '0';
  if (safe < 60) return '<1';
  return `${Math.round(safe / 60)}`;
}

/**
 * A completed plan's own title, for the「达成关联任务」chip on a timeline row.
 *
 * `taskId` is the only key: matching a session to a plan by title or subject
 * would mislabel one of two same-named plans. A session whose plan was deleted
 * (or that was never bound) resolves to null and the chip is simply not drawn.
 */
export function boundTaskTitle(
  taskId: string | null,
  dayTasks: ReadonlyArray<{ id: string; title: string }>
): string | null {
  if (!taskId) return null;
  return dayTasks.find(task => task.id === taskId)?.title ?? null;
}

// ---------------------------------------------------------------------------
// The week filmstrip
// ---------------------------------------------------------------------------

export interface FilmstripDay {
  date: string;
  /** 周一 … 周日 — the column's fixed header. */
  weekdayLabel: string;
  dayOfMonth: string;
  isSelected: boolean;
  isToday: boolean;
  isFuture: boolean;
  /**
   * The day's dominant subject colour, or null when the day holds no study
   * time. A dot is a claim that something happened, so a zero day gets none —
   * never a grey placeholder dot that reads as "a little work".
   */
  dotColor: string | null;
}

/** 周一 … 周日 in `Date.getDay()` order (0 = Sunday). */
const WEEKDAY_LABELS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'] as const;

/** The single-character column header above each cell — AB-06's「一 二 三 …」. */
const WEEKDAY_INITIALS = ['日', '一', '二', '三', '四', '五', '六'] as const;

export function weekdayLabelOf(iso: string): string {
  return WEEKDAY_LABELS[new Date(`${iso}T12:00:00`).getDay()];
}

function weekdayInitialOf(iso: string): string {
  return WEEKDAY_INITIALS[new Date(`${iso}T12:00:00`).getDay()];
}

/**
 * The calendar week (周一 → 周日) that contains `iso`.
 *
 * A calendar week, not a trailing seven days: the strip is a fixed frame the
 * eye can learn, and stepping the selected day across a Sunday→Monday boundary
 * rolls the whole frame rather than sliding one cell at a time.
 */
export function weekDatesOf(iso: string): string[] {
  const monday = shiftIsoDate(iso, -((new Date(`${iso}T12:00:00`).getDay() + 6) % 7));
  return Array.from({ length: 7 }, (_, index) => shiftIsoDate(monday, index));
}

/**
 * The filmstrip's seven cells.
 *
 * `dotColorsByDate` carries one colour per date (the day's dominant subject).
 * It comes from the week overview the trend view already reads, so the dots
 * never need a query of their own.
 */
export function buildFilmstrip(
  anchorDate: string,
  today: string,
  dotColorsByDate: ReadonlyMap<string, string>
): FilmstripDay[] {
  return weekDatesOf(anchorDate).map(date => ({
    date,
    weekdayLabel: weekdayInitialOf(date),
    dayOfMonth: String(Number(date.slice(8))),
    isSelected: date === anchorDate,
    isToday: date === today,
    isFuture: date > today,
    dotColor: dotColorsByDate.get(date) ?? null,
  }));
}

/**
 * Each date's dominant subject colour, for the filmstrip dots.
 *
 * The dominant subject is the one with the most minutes that day. Ties resolve
 * by subject id so a re-render cannot swap the dot's colour.
 */
export function dominantSubjectColors(
  days: ReadonlyArray<{ date: string; durationSeconds: number }>,
  distribution: ReadonlyArray<{ subjectId: string; subjectColor: string; dailyMinutes: Record<string, number> }>
): Map<string, string> {
  const colors = new Map<string, string>();
  for (const day of days) {
    if (day.durationSeconds <= 0) continue;
    let best: { subjectId: string; color: string; minutes: number } | null = null;
    for (const slice of distribution) {
      const minutes = slice.dailyMinutes[day.date] ?? 0;
      if (minutes <= 0) continue;
      if (!best || minutes > best.minutes || (minutes === best.minutes && slice.subjectId < best.subjectId)) {
        best = { subjectId: slice.subjectId, color: slice.subjectColor, minutes };
      }
    }
    if (best) colors.set(day.date, best.color);
  }
  return colors;
}

/** Days that actually hold study time — future cells never count. */
export function countWorkedDays(
  days: ReadonlyArray<{ durationSeconds: number; isFuture: boolean }>
): number {
  return days.filter(day => !day.isFuture && day.durationSeconds > 0).length;
}

/**
 * Days that have already happened — the denominator for every ratio on this page.
 *
 * A calendar week/month window runs out to Sunday / the last of the month, so it
 * carries future cells that can never hold a record. Dividing by `days.length`
 * instead reported「8 / 31 天」on 10 月 10 日, and diluted the native
 * `dailyAverageSeconds` by the same factor.
 */
export function countElapsedDays(days: ReadonlyArray<{ isFuture: boolean }>): number {
  return days.filter(day => !day.isFuture).length;
}

// ---------------------------------------------------------------------------
// The trend chart's columns
// ---------------------------------------------------------------------------

interface TrendColumnSegment {
  subjectId: string;
  color: string;
  minutes: number;
}

interface TrendColumn {
  date: string;
  dayLabel: string;
  isToday: boolean;
  totalMinutes: number;
  segments: TrendColumnSegment[];
}

/**
 * The chart's columns: elapsed days only, each stacked by subject.
 *
 * Future cells are dropped instead of drawn as empty stubs — a day that has not
 * happened yet cannot hold a record, and 21 stubs made「本月」read as noise.
 *
 * Segment order follows `distribution` (already minutes-desc), so one subject
 * keeps one colour and one vertical order across every column and the legend.
 * Heights are integer minutes because `dailyMinutes` is the only per-day
 * per-subject source the bridge ships; mixing it with raw seconds would let a
 * column's stack disagree with its own legend row.
 */
export function buildTrendColumns(
  days: ReadonlyArray<ReviewPeriodDay>,
  distribution: ReadonlyArray<ReviewSubjectSlice>
): TrendColumn[] {
  return days
    .filter(day => !day.isFuture)
    .map(day => {
      const segments = distribution
        .map(
          (slice): TrendColumnSegment => ({
            subjectId: slice.subjectId,
            color: slice.subjectColor,
            minutes: slice.dailyMinutes[day.date] ?? 0,
          })
        )
        .filter(segment => segment.minutes > 0);
      return {
        date: day.date,
        dayLabel: day.dayLabel,
        isToday: day.isToday,
        totalMinutes: segments.reduce((sum, segment) => sum + segment.minutes, 0),
        segments,
      };
    });
}

/** The single tallest column — the caption under the chart names it. */
export function peakTrendDay(columns: ReadonlyArray<TrendColumn>): TrendColumn | null {
  const worked = columns.filter(column => column.totalMinutes > 0);
  if (worked.length === 0) return null;
  return worked.reduce((a, b) => (b.totalMinutes > a.totalMinutes ? b : a));
}

interface PeriodDelta {
  /** Signed whole minutes; 0 when the two periods match after rounding. */
  deltaMinutes: number;
  label: string;
  /** null when there is nothing to compare against, so no tone is applied. */
  tone: 'up' | 'down' | 'flat' | null;
}

/**
 * 「较上一周期」— the one number that answers "am I improving?".
 *
 * A duration delta, not a percent: a percent is meaningless when the previous
 * period was empty, and a study journal reads better in hours and minutes.
 */
export function periodDelta(currentSeconds: number, previousSeconds: number | null): PeriodDelta {
  if (previousSeconds === null || previousSeconds <= 0) {
    return { deltaMinutes: 0, label: '上一周期没有记录', tone: null };
  }
  const deltaMinutes = Math.round(currentSeconds / 60) - Math.round(previousSeconds / 60);
  if (deltaMinutes === 0) return { deltaMinutes: 0, label: '与上一周期持平', tone: 'flat' };
  const sign = deltaMinutes > 0 ? '+' : '-';
  return {
    deltaMinutes,
    label: `较上一周期 ${sign}${formatDuration(Math.abs(deltaMinutes) * 60)}`,
    tone: deltaMinutes > 0 ? 'up' : 'down',
  };
}

/**
 * Which window counts as "the previous period" for a given scope.
 *
 * The single-day window steps back one day; calendar scopes step one whole
 * week / month back through the bridge's existing `periodsBack`.
 */
export function previousWindowArgs(
  scope: ReviewScope,
  anchorDate: string
): { scope: ReviewScope; periodsBack: number; anchorDate: string } {
  if (scope === 'TODAY') return { scope, periodsBack: 0, anchorDate: shiftIsoDate(anchorDate, -1) };
  return { scope, periodsBack: 1, anchorDate };
}

/**
 * The percent change against the previous period, or null when there is no
 * baseline to divide by.
 *
 * `periodDelta` reports the absolute difference, which is the honest number
 * when the previous window was empty or nearly so —「+2 小时」from a 5-minute
 * base is a 2400% jump that says nothing about the studying. This is the
 * companion figure for windows that do have a base worth comparing against.
 */
export function periodDeltaPercent(currentSeconds: number, previousSeconds: number | null): number | null {
  if (previousSeconds === null || previousSeconds <= 0) return null;
  return (currentSeconds - previousSeconds) / previousSeconds;
}

/**
 * 「达标 N / M 天」— days that reached the daily goal, over elapsed days.
 *
 * The goal is the app-level daily target. A window with no goal set (or a
 * goal of zero) reports null so the caller can omit the line rather than
 * claim「达标 0 / 6 天」against a target the user never chose.
 */
export function goalReachedDays(
  days: ReadonlyArray<{ durationSeconds: number; isFuture: boolean }>,
  goalSeconds: number | null
): { reached: number; elapsed: number } | null {
  if (goalSeconds === null || goalSeconds <= 0) return null;
  const elapsed = days.filter(day => !day.isFuture);
  return {
    reached: elapsed.filter(day => day.durationSeconds >= goalSeconds).length,
    elapsed: elapsed.length,
  };
}

// ---------------------------------------------------------------------------
// The single-day view: how one day's focus sits across its 24 hours
// ---------------------------------------------------------------------------

interface HourBucket {
  hour: number;
  minutes: number;
}

/**
 * 一天 24 个小时格子的专注分钟数。
 *
 * 跨小时的会话按重叠秒数摊进每个小时（13:40–15:10 同时落在 13 / 14 / 15 时）。
 * 只记开始小时会让曲线在整点处凭空尖峰，并把时长记错时段。
 */
export function buildHourBuckets(
  sessions: ReadonlyArray<{ startTime: number; durationSeconds: number }>
): HourBucket[] {
  const msByHour = new Array<number>(24).fill(0);
  for (const session of sessions) {
    let remainingMs = session.durationSeconds * 1000;
    let cursor = session.startTime;
    while (remainingMs > 0) {
      const start = new Date(cursor);
      const boundary = new Date(cursor);
      boundary.setHours(start.getHours() + 1, 0, 0, 0);
      const inHourMs = Math.min(remainingMs, Math.max(0, boundary.getTime() - cursor));
      msByHour[start.getHours()] += inHourMs;
      remainingMs -= inHourMs;
      cursor = boundary.getTime();
    }
  }
  return msByHour.map((ms, hour) => ({ hour, minutes: Math.round(ms / 60_000) }));
}

/** 一天里最专注的小时，用于曲线下的注记。 */
export function peakHourBucket(buckets: ReadonlyArray<HourBucket>): HourBucket | null {
  const worked = buckets.filter(bucket => bucket.minutes > 0);
  if (worked.length === 0) return null;
  return worked.reduce((a, b) => (b.minutes > a.minutes ? b : a));
}

export function formatMonthDaySlash(iso: string): string {
  const parts = iso.split('-');
  if (parts.length < 3) return iso;
  return `${parts[1]}月/${parts[2]}日`;
}

export function formatDailyAverageDuration(totalSeconds: number): string {
  if (!Number.isFinite(totalSeconds) || totalSeconds <= 0) return '0 分钟/天';
  const minutes = Math.round(totalSeconds / 60);
  if (minutes <= 0) return totalSeconds > 0 ? '<1 分钟/天' : '0 分钟/天';
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours > 0 && rest > 0) return `${hours} 小时 ${rest} 分/天`;
  if (hours > 0) return `${hours} 小时/天`;
  return `${rest} 分钟/天`;
}

/**
 * Which axis labels to draw for a window.
 *
 * Prevents adjacent labels from overlapping (such as「10日11日」).
 * In crowded windows (length > 14), labels are thinned with a stride of 5,
 * keeping today while suppressing any stride candidate within 1 slot of today.
 */
export function axisLabels<T extends { date: string; dayLabel: string; isToday: boolean }>(
  days: readonly T[]
): T[] {
  if (days.length <= 14) {
    return [...days];
  }
  const stride = axisLabelStride(days.length);
  const todayIndex = days.findIndex(day => day.isToday);

  return days.filter((day, index) => {
    if (day.isToday) return true;
    // Suppress regular labels directly adjacent to today to avoid collision
    if (todayIndex !== -1 && Math.abs(index - todayIndex) < 2) {
      return false;
    }
    return index % stride === 0;
  });
}

function axisLabelStride(dayCount: number): number {
  if (dayCount <= 14) return 1;
  if (dayCount <= 20) return 2;
  return 5;
}

interface PlacedAxisLabel {
  date: string;
  dayLabel: string;
  isToday: boolean;
  left: number;
  width: number;
}

/**
 * Computes non-overlapping absolute layout positions for chart axis labels.
 *
 * Prevents truncation (such as「09-1」instead of「09-15」) by assigning
 * a fixed width to each label and ensuring no two labels collide.
 * Today is always given priority, followed by the start of the window.
 */
export function computeAxisLabelsLayout<
  T extends { date: string; dayLabel: string; isToday: boolean }
>(
  days: readonly T[],
  chartWidth: number,
  options?: {
    labelWidth?: number;
    gap?: number;
    minSpacing?: number;
    /** X centers of the plotted points; defaults to bar-column centers. */
    centers?: ReadonlyArray<number>;
  }
): PlacedAxisLabel[] {
  if (days.length === 0 || chartWidth <= 0) return [];

  const labelWidth = options?.labelWidth ?? 38;
  const gap = options?.gap ?? 4;
  const minSpacing = options?.minSpacing ?? 4;

  const barWidth = Math.max(2, (chartWidth - gap * (days.length - 1)) / days.length);

  const getClampedLeft = (index: number) => {
    const centerX = options?.centers
      ? options.centers[index]
      : index * (barWidth + gap) + barWidth / 2;
    return Math.max(0, Math.min(chartWidth - labelWidth, centerX - labelWidth / 2));
  };

  const candidateIndices: number[] = [];
  const todayIndex = days.findIndex(d => d.isToday);

  if (days.length <= 7) {
    for (let i = 0; i < days.length; i++) {
      candidateIndices.push(i);
    }
  } else {
    const stride = axisLabelStride(days.length);
    for (let i = 0; i < days.length; i++) {
      if (i % stride === 0 || i === days.length - 1) {
        candidateIndices.push(i);
      }
    }
  }

  const placed: Array<{ index: number; left: number; right: number; item: T }> = [];

  const canPlace = (left: number, right: number) => {
    return !placed.some(
      p => left < p.right + minSpacing && right > p.left - minSpacing
    );
  };

  // 1. High priority: today
  if (todayIndex !== -1) {
    const left = getClampedLeft(todayIndex);
    placed.push({
      index: todayIndex,
      left,
      right: left + labelWidth,
      item: days[todayIndex],
    });
  }

  // 2. Start of window (index 0)
  if (candidateIndices.includes(0) && (todayIndex !== 0 || placed.length === 0)) {
    const left = getClampedLeft(0);
    const right = left + labelWidth;
    if (canPlace(left, right)) {
      placed.push({
        index: 0,
        left,
        right,
        item: days[0],
      });
    }
  }

  // 3. Remaining candidates
  for (const idx of candidateIndices) {
    if (idx === todayIndex || idx === 0) continue;
    const left = getClampedLeft(idx);
    const right = left + labelWidth;
    if (canPlace(left, right)) {
      placed.push({
        index: idx,
        left,
        right,
        item: days[idx],
      });
    }
  }

  placed.sort((a, b) => a.index - b.index);

  return placed.map(p => ({
    date: p.item.date,
    dayLabel: p.item.dayLabel,
    isToday: p.item.isToday,
    left: Math.round(p.left * 10) / 10,
    width: labelWidth,
  }));
}

interface DurationMetricParts {
  hours: number;
  minutes: number;
  formattedHours: string;
  formattedMinutes: string;
}

export function formatDurationMetric(totalSeconds: number): DurationMetricParts {
  const safeSeconds = Math.max(0, totalSeconds);
  const minutesTotal = Math.round(safeSeconds / 60);
  const hours = Math.floor(minutesTotal / 60);
  const minutes = minutesTotal % 60;
  return {
    hours,
    minutes,
    formattedHours: `${hours}`.padStart(2, '0'),
    formattedMinutes: `${minutes}`.padStart(2, '0'),
  };
}

export function weekNumberOfYear(date: Date): number {
  const target = new Date(Date.UTC(date.getFullYear(), date.getMonth(), date.getDate()));
  const dayNr = (target.getUTCDay() + 6) % 7;
  target.setUTCDate(target.getUTCDate() - dayNr + 3);
  const firstThursday = target.valueOf();
  target.setUTCMonth(0, 1);
  if (target.getUTCDay() !== 4) {
    target.setUTCMonth(0, 1 + ((4 - target.getUTCDay()) + 7) % 7);
  }
  return 1 + Math.ceil((firstThursday - target.valueOf()) / (7 * 86_400_000));
}

export function scopeDateRangeLabel(scope: ReviewScope, anchorDate: string): string {
  const parsed = new Date(`${anchorDate}T12:00:00`);
  if (Number.isNaN(parsed.getTime())) return '';
  if (scope === 'TODAY') {
    return `${parsed.getMonth() + 1}月${parsed.getDate()}日`;
  }
  if (scope === 'CALENDAR_WEEK') {
    const dates = weekDatesOf(anchorDate);
    if (dates.length < 7) return '';
    const start = new Date(`${dates[0]}T12:00:00`);
    const end = new Date(`${dates[6]}T12:00:00`);
    return `${start.getMonth() + 1}月${start.getDate()}日 – ${end.getMonth() + 1}月${end.getDate()}日`;
  }
  // CALENDAR_MONTH
  const year = parsed.getFullYear();
  const month = parsed.getMonth();
  const lastDay = new Date(year, month + 1, 0).getDate();
  return `${month + 1}月01日 – ${month + 1}月${lastDay}日`;
}

