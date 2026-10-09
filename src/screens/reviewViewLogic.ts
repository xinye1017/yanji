/**
 * Yanji "Review" pure view logic — everything the review screen computes rather
 * than renders.
 *
 * Why this file exists: the timeline merge, the date clamp and the relative day
 * naming are the parts that can silently be WRONG (a future date renders a blank
 * day, a task sorted by the wrong clock lands in the wrong slot). Keeping them
 * out of the .tsx means `test-e2e/tier1-features/m9-review-view-logic.test.js`
 * can import and assert them directly — the same trick
 * `m8-shipped-tokens.test.js` uses to reach `src/theme/tokens.ts`.
 *
 * No React and no React Native imports live here on purpose.
 */

import type {
  DailyTimeline,
  DailyTimelineSession,
  ReviewPeriodDay,
  ReviewScope,
  ReviewSubjectSlice,
  StudyTask,
} from '../bridge';

const MS_PER_DAY = 86_400_000;

export function todayIso(): string {
  const now = new Date();
  const month = `${now.getMonth() + 1}`.padStart(2, '0');
  const day = `${now.getDate()}`.padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

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
  | { key: string; kind: 'session'; at: number; session: DailyTimelineSession }
  | { key: string; kind: 'task'; at: number; task: StudyTask }
  | {
      key: string;
      kind: 'note';
      at: number;
      noteId: string;
      timestamp: number;
      content: string;
      isFavorite: boolean;
    };

/**
 * One chronological stream out of three Room sources.
 *
 * Sort keys are the real event times: a session starts at `startTime`, a task
 * was created at `createdAt`, a note was written at `timestamp`. The domain
 * model has no completion timestamp for a task, so a completed task sits where
 * it was *planned* — the bridge does not expose when it was ticked off, and
 * guessing one would be a second source of truth.
 *
 * Newest first. The key breaks ties so the order never depends on the order in
 * which the three source arrays happened to arrive.
 */
export function buildTimelineEntries(timeline: DailyTimeline | null): TimelineEntry[] {
  if (!timeline) return [];
  const entries: TimelineEntry[] = [
    ...timeline.sessions.map(
      (session): TimelineEntry => ({
        key: `session-${session.id}`,
        kind: 'session',
        at: session.startTime,
        session,
      })
    ),
    ...timeline.completedTasks.map(
      (task): TimelineEntry => ({ key: `task-${task.id}`, kind: 'task', at: task.createdAt, task })
    ),
    ...timeline.notes.map(
      (note): TimelineEntry => ({
        key: `note-${note.id}`,
        kind: 'note',
        at: note.timestamp,
        noteId: note.id,
        timestamp: note.timestamp,
        content: note.content,
        isFavorite: note.isFavorite,
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

export interface TrendColumnSegment {
  subjectId: string;
  color: string;
  minutes: number;
}

export interface TrendColumn {
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

export interface PeriodDelta {
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

// ---------------------------------------------------------------------------
// The single-day view: how one day's focus sits across its 24 hours
// ---------------------------------------------------------------------------

export interface HourBucket {
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

/** 折线图第 i 个点的 x 中心：首尾贴边，中间等距。 */
export function lineCenters(count: number, width: number): number[] {
  if (count <= 0 || width <= 0) return [];
  if (count === 1) return [width / 2];
  const step = width / (count - 1);
  return Array.from({ length: count }, (_, index) => index * step);
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

export interface PlacedAxisLabel {
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


export interface PieSliceData {
  key: string;
  label: string;
  color: string;
  value: number;
  share: number;
  path: string;
}

export function computePieSlices(
  slices: ReadonlyArray<{
    subjectId: string;
    subjectName: string;
    subjectColor: string;
    minutes: number;
    share: number;
  }>,
  radius: number,
  innerRadius: number = 0,
  cx: number = radius,
  cy: number = radius
): PieSliceData[] {
  const filtered = slices.filter(s => s.share > 0);
  if (filtered.length === 0) return [];

  if (filtered.length === 1) {
    const s = filtered[0];
    return [
      {
        key: s.subjectId,
        label: s.subjectName,
        color: s.subjectColor,
        value: s.minutes,
        share: s.share,
        path: '',
      },
    ];
  }

  let currentAngle = -Math.PI / 2;
  return filtered.map(s => {
    const angle = s.share * 2 * Math.PI;
    const startAngle = currentAngle;
    const endAngle = currentAngle + angle;
    currentAngle = endAngle;

    const x1 = cx + radius * Math.cos(startAngle);
    const y1 = cy + radius * Math.sin(startAngle);
    const x2 = cx + radius * Math.cos(endAngle);
    const y2 = cy + radius * Math.sin(endAngle);

    const largeArcFlag = angle > Math.PI ? 1 : 0;

    let path = '';
    if (innerRadius > 0) {
      const ix1 = cx + innerRadius * Math.cos(endAngle);
      const iy1 = cy + innerRadius * Math.sin(endAngle);
      const ix2 = cx + innerRadius * Math.cos(startAngle);
      const iy2 = cy + innerRadius * Math.sin(startAngle);
      path = `M ${x1} ${y1} A ${radius} ${radius} 0 ${largeArcFlag} 1 ${x2} ${y2} L ${ix1} ${iy1} A ${innerRadius} ${innerRadius} 0 ${largeArcFlag} 0 ${ix2} ${iy2} Z`;
    } else {
      path = `M ${cx} ${cy} L ${x1} ${y1} A ${radius} ${radius} 0 ${largeArcFlag} 1 ${x2} ${y2} Z`;
    }

    return {
      key: s.subjectId,
      label: s.subjectName,
      color: s.subjectColor,
      value: s.minutes,
      share: s.share,
      path,
    };
  });
}
