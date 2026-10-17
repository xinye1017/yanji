/**
 * Settings screen pure logic — dates, durations and the exam-date calendar grid.
 *
 * No React Native imports, so the same source runs in the Node test suite: the
 * date arithmetic that decides what the page prints is verified without a
 * device. All dates are local-calendar ISO keys (`yyyy-MM-dd`); comparisons go
 * through UTC epoch days so a DST shift can never move a day boundary.
 */

export interface IsoDateParts {
  year: number;
  month: number;
  day: number;
}

/** Calendar month the picker is showing; `month` is 1-based. */
export interface CalendarMonth {
  year: number;
  month: number;
}

const WEEKDAY_LABELS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'] as const;
/** Monday-first column order, matching the review filmstrip's 周一 → 周日 frame. */
const MONTH_GRID_COLUMNS = ['一', '二', '三', '四', '五', '六', '日'] as const;

export const WEEKDAY_COLUMNS: ReadonlyArray<string> = MONTH_GRID_COLUMNS;

/** Parse a strict `yyyy-MM-dd` key; anything else (or a rolled-over day) is null. */
export function parseIsoDate(iso: string): IsoDateParts | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso.trim());
  if (!match) return null;
  const year = Number(match[1]);
  const month = Number(match[2]);
  const day = Number(match[3]);
  if (month < 1 || month > 12 || day < 1 || day > 31) return null;
  // Round-trip through a real local date: `2026-02-31` parses as March 3rd, so
  // comparing the parts back is what rejects it instead of silently shifting.
  const probe = new Date(year, month - 1, day);
  if (probe.getFullYear() !== year || probe.getMonth() !== month - 1 || probe.getDate() !== day) {
    return null;
  }
  return { year, month, day };
}

export function formatIsoDate({ year, month, day }: IsoDateParts): string {
  return `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
}

export function todayIso(): string {
  const now = new Date();
  return formatIsoDate({ year: now.getFullYear(), month: now.getMonth() + 1, day: now.getDate() });
}

/** Whole days from `from` to `to`; negative when `to` is earlier. */
export function daysBetweenIso(from: string, to: string): number | null {
  const start = parseIsoDate(from);
  const end = parseIsoDate(to);
  if (!start || !end) return null;
  const startUtc = Date.UTC(start.year, start.month - 1, start.day);
  const endUtc = Date.UTC(end.year, end.month - 1, end.day);
  return Math.round((endUtc - startUtc) / 86_400_000);
}

export function weekdayLabelOf(iso: string): string | null {
  const parts = parseIsoDate(iso);
  if (!parts) return null;
  return WEEKDAY_LABELS[new Date(parts.year, parts.month - 1, parts.day).getDay()];
}

/** `2026年12月19日`, or `未设置` when the stored value is absent / unparsable. */
export function formatExamDate(iso: string): string {
  const parts = parseIsoDate(iso);
  return parts ? `${parts.year}年${parts.month}月${parts.day}日` : '未设置';
}

/**
 * The countdown line under the exam-date row.
 *
 * Computed from the stored date rather than the native countdown payload: the
 * native value is clamped at 0, which cannot distinguish "today is the exam"
 * from "the date has passed".
 */
export function examCountdownText(iso: string, today: string): string | null {
  const remaining = daysBetweenIso(today, iso);
  if (remaining === null) return null;
  if (remaining > 0) return `距初试 ${remaining} 天`;
  if (remaining === 0) return '今天就是初试日';
  return `已过设定日期 ${-remaining} 天`;
}

/** `6 小时`, `6 小时 30 分钟`, `未设置` when no positive goal is stored. */
export function formatGoalDuration(hours: number | undefined): string {
  if (hours === undefined || !Number.isFinite(hours) || hours <= 0) return '未设置';
  const totalMinutes = Math.round(hours * 60);
  const wholeHours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  if (wholeHours === 0) return `${minutes} 分钟`;
  return minutes === 0 ? `${wholeHours} 小时` : `${wholeHours} 小时 ${minutes} 分钟`;
}

/** Minutes → the fractional hours the native `dailyGoalHours` column stores. */
export function goalHoursFromMinutes(minutes: number): number {
  const safe = Number.isFinite(minutes) ? Math.max(0, Math.round(minutes)) : 0;
  return safe / 60;
}

export function monthOf(iso: string): CalendarMonth {
  const parts = parseIsoDate(iso);
  if (!parts) return monthOf(todayIso());
  return { year: parts.year, month: parts.month };
}

export function shiftMonth({ year, month }: CalendarMonth, delta: number): CalendarMonth {
  const zeroBased = year * 12 + (month - 1) + delta;
  return { year: Math.floor(zeroBased / 12), month: (zeroBased % 12 + 12) % 12 + 1 };
}

export function formatMonthTitle({ year, month }: CalendarMonth): string {
  return `${year} 年 ${month} 月`;
}

/**
 * The month laid out as Monday-first weeks.
 *
 * Always at least five rows and at most six, with blanks (`null`) where a day
 * does not exist, so the grid does not jump in height as the user pages
 * between months.
 */
export function buildMonthGrid({ year, month }: CalendarMonth): ReadonlyArray<ReadonlyArray<number | null>> {
  const daysInMonth = new Date(year, month, 0).getDate();
  // getDay() is Sunday-first; the grid starts on Monday.
  const leadingBlanks = (new Date(year, month - 1, 1).getDay() + 6) % 7;
  const cells: Array<number | null> = [
    ...Array.from({ length: leadingBlanks }, () => null),
    ...Array.from({ length: daysInMonth }, (_, index) => index + 1),
  ];
  while (cells.length % 7 !== 0) cells.push(null);

  const weeks: Array<ReadonlyArray<number | null>> = [];
  for (let index = 0; index < cells.length; index += 7) {
    weeks.push(cells.slice(index, index + 7));
  }
  return weeks;
}
