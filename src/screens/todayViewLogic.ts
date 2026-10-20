import type { DailySession, StudyTask } from '../bridge';
import type { YanjiDayRhythmBucket, YanjiSpectrumSegment } from '../components/YanjiUI';

export const DAY_PARTS = [
  { name: '凌晨', from: 0, to: 6 }, { name: '上午', from: 6, to: 12 },
  { name: '下午', from: 12, to: 18 }, { name: '晚上', from: 18, to: 24 },
] as const;

/** The strip's window; anything outside it is not drawn and must not be claimed. */
const RHYTHM_FIRST_HOUR = 6;
const RHYTHM_LAST_HOUR = 24;

const RHYTHM_HOURS = Array.from(
  { length: RHYTHM_LAST_HOUR - RHYTHM_FIRST_HOUR },
  (_, index) => index + RHYTHM_FIRST_HOUR,
);
const CHINESE_DIGITS = '零一二三四五六七八九';

function chineseNumber(value: number): string {
  if (value < 10) return CHINESE_DIGITS[value];
  return `${value < 20 ? '' : CHINESE_DIGITS[Math.floor(value / 10)]}十${value % 10 ? CHINESE_DIGITS[value % 10] : ''}`;
}

export function displayDate(date: string): string {
  const day = new Date(`${date}T12:00:00`);
  return `${chineseNumber(day.getMonth() + 1)}月${chineseNumber(day.getDate())}日 · 周${'日一二三四五六'[day.getDay()]}`;
}

export function greeting(hour: number): string {
  if (hour < 5) return '夜深露重，注意休整。';
  if (hour < 11) return '晨光微熹，步履从容。';
  if (hour < 14) return '日当正午，笃定前行。';
  if (hour < 18) return '午后沉静，步履不停。';
  return '灯火温润，归卷沉思。';
}

/**
 * The one duration voice the app speaks.
 *
 * It used to be seven — `2h15m`, `08h 00m`, `1小时5分`, `1 小时 5 分`,
 * `1 小时 5 分/天`, `6 小时 30 分钟` and `04:21` — all describing the same quantity,
 * so the same goal read differently on Today and in Settings. Chinese units, a
 * space around the numbers, and no unit that is zero.
 */
export function formatDuration(seconds: number): string {
  const whole = Math.max(0, Math.floor(seconds));
  const minutes = Math.floor(whole / 60);
  if (minutes === 0) return whole > 0 ? '不足 1 分钟' : '0 分钟';
  const hours = Math.floor(minutes / 60);
  const remainder = minutes % 60;
  if (hours === 0) return `${minutes} 分钟`;
  return remainder ? `${hours} 小时 ${remainder} 分` : `${hours} 小时`;
}

/**
 * A duration where the space or the surrounding sentence already names the
 * unit: the hero's two numbers, the ledger's left margin, a subject legend.
 */
export function compactDuration(seconds: number): string {
  const whole = Math.max(0, Math.floor(seconds));
  const minutes = Math.floor(whole / 60);
  if (minutes === 0) return whole > 0 ? '<1 分' : '0 分';
  const hours = Math.floor(minutes / 60);
  const remainder = minutes % 60;
  if (hours === 0) return `${minutes} 分`;
  return remainder ? `${hours} 小时 ${remainder} 分` : `${hours} 小时`;
}

/** The goal, written the same way every other duration is. */
export function goalClock(seconds: number): string {
  return formatDuration(seconds);
}

export function taskMeta(task: StudyTask): string {
  const actual = compactDuration(task.actualMinutes * 60);
  if (task.completed) return `实际专注 ${actual}`;
  const planned = compactDuration(task.plannedMinutes * 60);
  return task.actualMinutes > 0 ? `已投入 ${actual} · 预计 ${planned}` : `预计 ${planned}`;
}

export function summarizeSubjects(
  sessions: readonly DailySession[],
  fallbackColor: string,
): YanjiSpectrumSegment[] {
  const subjects = new Map<string, YanjiSpectrumSegment>();
  for (const session of sessions) {
    if (session.durationSeconds <= 0) continue;
    const previous = subjects.get(session.subjectId);
    if (previous) previous.seconds += session.durationSeconds;
    else subjects.set(session.subjectId, {
      id: session.subjectId,
      label: session.subjectName,
      seconds: session.durationSeconds,
      color: session.subjectColor || fallbackColor,
    });
  }
  return [...subjects.values()].sort((a, b) => a.id.localeCompare(b.id));
}

export function spectrumLegend(
  subjects: readonly YanjiSpectrumSegment[],
  tasks: readonly StudyTask[],
): YanjiSpectrumSegment[] {
  const legend = new Map(subjects.map(subject => [subject.id, subject]));
  for (const task of tasks) {
    if (!legend.has(task.subjectId)) legend.set(task.subjectId, {
      id: task.subjectId,
      label: task.subjectName,
      seconds: 0,
      color: task.subjectColor,
    });
  }
  return [...legend.values()];
}

function sessionPrecise(session: DailySession): boolean {
  // A session is plottable whenever it has recorded duration and valid timestamps.
  return session.durationSeconds > 0
    && (session.endTime > session.startTime || session.durationSeconds > 0);
}

export function buildRhythmBuckets(
  date: string,
  sessions: readonly DailySession[],
  fallbackColor: string,
): YanjiDayRhythmBucket[] {
  const plottable = sessions.filter(sessionPrecise);
  return RHYTHM_HOURS.map(hour => {
    const start = new Date(`${date}T00:00:00`);
    start.setHours(hour, 0, 0, 0);
    const end = new Date(`${date}T00:00:00`);
    end.setHours(hour + 1, 0, 0, 0);
    const bySubject = new Map<string, { seconds: number; color: string }>();
    let seconds = 0;
    for (const session of plottable) {
      const sStart = session.startTime;
      const sEnd = session.endTime > sStart ? session.endTime : sStart + session.durationSeconds * 1000;
      const spanMs = Math.max(1000, sEnd - sStart);
      const overlapMs = Math.max(0, Math.min(end.getTime(), sEnd) - Math.max(start.getTime(), sStart));
      if (overlapMs <= 0) continue;
      const ratio = overlapMs / spanMs;
      const allocated = Math.round(session.durationSeconds * ratio);
      if (allocated <= 0) continue;
      seconds += allocated;
      const previous = bySubject.get(session.subjectId);
      if (previous) previous.seconds += allocated;
      else bySubject.set(session.subjectId, { seconds: allocated, color: session.subjectColor || fallbackColor });
    }
    // Several short sessions of one subject can outweigh a single longer one.
    let dominantId = '';
    let dominantSeconds = 0;
    let color: string | undefined;
    for (const [id, subject] of bySubject) {
      if (subject.seconds > dominantSeconds || (subject.seconds === dominantSeconds && id.localeCompare(dominantId) < 0)) {
        dominantId = id;
        dominantSeconds = subject.seconds;
        color = subject.color;
      }
    }
    return { hour, seconds, color };
  });
}

/**
 * One sentence stating what the rhythm strip actually shows.
 *
 * The collapsed section used to be a title and an unlabelled row of ticks, so
 * the reader had to expand it to learn whether the day had any shape at all.
 * This says it in the collapsed state — including the honest empty case — and
 * names the busiest hour whenever the strip has something to point at.
 *
 * The window only starts at 06:00, so a session that ended before it is not in
 * the strip at all. Saying "已确认 X 连续专注" without qualification would claim a
 * total that omits those hours and then contradict 今日累计专注 on the same
 * screen, so the sentence names its own window whenever it reports a figure.
 */
export function rhythmSummary(
  buckets: readonly YanjiDayRhythmBucket[],
  loaded: boolean,
  hourLabel: (hour: number) => string,
): string {
  if (!loaded) return '正在读取今日节奏…';
  const measured = buckets.filter(bucket => bucket.seconds > 0);
  if (measured.length === 0) return '暂无连续专注记录，开启一轮专注点亮今日节奏。';
  const total = measured.reduce((sum, bucket) => sum + bucket.seconds, 0);
  // Ties resolve to the earlier hour: the first time the day reached its peak
  // is the more useful statement than an arbitrary pick among equals.
  const peak = measured.reduce((best, bucket) => (bucket.seconds > best.seconds ? bucket : best), measured[0]);
  const window = `${String(RHYTHM_FIRST_HOUR).padStart(2, '0')}:00 之后`;
  return `${window}已确认 ${compactDuration(total)} 连续专注，分布在 ${measured.length} 个小时；最集中的是 ${hourLabel(peak.hour)}。`;
}

/** 24h clock label for a bucket hour, e.g. `09:00–10:00`. */
export function hourRangeLabel(hour: number): string {
  const start = String(hour).padStart(2, '0');
  const end = String(hour + 1).padStart(2, '0');
  return `${start}:00–${end}:00`;
}
