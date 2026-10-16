import type { DailySession, StudyTask } from '../bridge';
import type { YanjiMiniRhythmBucket, YanjiSpectrumSegment } from '../components/YanjiUI';

export const DAY_PARTS = [
  { name: '凌晨', from: 0, to: 6 }, { name: '上午', from: 6, to: 12 },
  { name: '下午', from: 12, to: 18 }, { name: '晚上', from: 18, to: 24 },
] as const;

const RHYTHM_HOURS = Array.from({ length: 18 }, (_, index) => index + 6);
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

export function compactDuration(seconds: number): string {
  const minutes = Math.max(0, Math.floor(seconds / 60));
  if (minutes === 0) return seconds > 0 ? '<1m' : '0m';
  const hours = Math.floor(minutes / 60);
  const remainder = minutes % 60;
  return hours > 0 ? `${hours}h${remainder ? `${remainder}m` : ''}` : `${minutes}m`;
}

export function goalClock(seconds: number): string {
  const minutes = Math.max(0, Math.floor(seconds / 60));
  return `${String(Math.floor(minutes / 60)).padStart(2, '0')}h ${String(minutes % 60).padStart(2, '0')}m`;
}

export function taskMeta(task: StudyTask): string {
  const actual = compactDuration(task.actualMinutes * 60);
  if (task.completed) return `实际专注 ${actual}`;
  const planned = compactDuration(task.plannedMinutes * 60);
  return task.actualMinutes > 0 ? `已投入 ${actual} / 预计 ${planned}` : `预计 ${planned}`;
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

export function sessionPrecise(session: DailySession): boolean {
  // Pause intervals are unavailable. Only uninterrupted, clock-consistent spans
  // have a known position; every saved duration still contributes to the total.
  return session.durationSeconds > 0 && session.pauseCount === 0
    && session.endTime > session.startTime
    && Math.abs((session.endTime - session.startTime) / 1000 - session.durationSeconds) <= 2;
}

export function buildRhythmBuckets(
  date: string,
  sessions: readonly DailySession[],
  fallbackColor: string,
): YanjiMiniRhythmBucket[] {
  const precise = sessions.filter(sessionPrecise);
  return RHYTHM_HOURS.map(hour => {
    const start = new Date(`${date}T00:00:00`);
    start.setHours(hour, 0, 0, 0);
    const end = new Date(`${date}T00:00:00`);
    end.setHours(hour + 1, 0, 0, 0);
    const bySubject = new Map<string, { seconds: number; color: string }>();
    let seconds = 0;
    for (const session of precise) {
      const overlap = Math.max(0, Math.min(end.getTime(), session.endTime) - Math.max(start.getTime(), session.startTime)) / 1000;
      if (overlap <= 0) continue;
      seconds += overlap;
      const previous = bySubject.get(session.subjectId);
      if (previous) previous.seconds += overlap;
      else bySubject.set(session.subjectId, { seconds: overlap, color: session.subjectColor || fallbackColor });
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
