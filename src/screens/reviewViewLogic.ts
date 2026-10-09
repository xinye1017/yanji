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

import type { DailyTimeline, DailyTimelineSession, StudyTask } from '../bridge';

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

/** The chart column for today, when today falls inside the window at all. */
export function findTodayColumn<T extends { date: string; isToday: boolean }>(
  days: readonly T[]
): T | null {
  return days.find(day => day.isToday) ?? null;
}
