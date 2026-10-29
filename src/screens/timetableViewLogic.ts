/**
 * 每日时间表的纯视图逻辑。
 *
 * 这一层不碰 React、不碰桥接：日期周条、当前时段、进度、专注时长分摊和
 * 编辑校验都可以在 Node 里逐条断言。页面只负责把结果画出来。
 */

import type {
  DailySession,
  ScheduleBlock,
  ScheduleBlockInput,
  Subject,
} from '../bridge';

export const MINUTES_PER_DAY = 24 * 60;
export const SCHEDULE_MINUTE_STEP = 15;
export const SCHEDULE_MAX_BLOCKS = 32;

/** Snap both endpoints only after the user actively drags an existing block. */
export function adjustScheduleTime<T extends { startMinute: number; endMinute: number }>(block: T, mode: 'move' | 'start' | 'end', deltaMinutes: number): T {
  const snap = (minute: number) => Math.round(minute / SCHEDULE_MINUTE_STEP) * SCHEDULE_MINUTE_STEP;
  const start = Math.max(0, Math.min(MINUTES_PER_DAY - SCHEDULE_MINUTE_STEP, snap(block.startMinute)));
  const end = Math.max(start + SCHEDULE_MINUTE_STEP, Math.min(MINUTES_PER_DAY, snap(block.endMinute)));
  if (mode === 'move') {
    const duration = end - start;
    const next = Math.max(0, Math.min(MINUTES_PER_DAY - duration, snap(block.startMinute + deltaMinutes)));
    return { ...block, startMinute: next, endMinute: next + duration };
  }
  if (mode === 'start') return { ...block, startMinute: Math.max(0, Math.min(end - SCHEDULE_MINUTE_STEP, snap(block.startMinute + deltaMinutes))), endMinute: end };
  return { ...block, startMinute: start, endMinute: Math.min(MINUTES_PER_DAY, Math.max(start + SCHEDULE_MINUTE_STEP, snap(block.endMinute + deltaMinutes))) };
}

export interface ScheduleFocusOption {
  /** 真正写进专注记录的学科 id（与时间表所选科目一致）。 */
  subjectId: string;
  subjectName: string;
  /** 时间表里展示的科目名，例如「数学」「408」。 */
  label: string;
  colorHex: string;
}

function pad2(value: number): string {
  return String(value).padStart(2, '0');
}

/** 本地日历日期（yyyy-MM-dd），不受 UTC 偏移影响。 */
export function isoDate(date: Date): string {
  return `${date.getFullYear()}-${pad2(date.getMonth() + 1)}-${pad2(date.getDate())}`;
}

/** 用中午构造，避开夏令时午夜不存在 / 重复的边界。 */
export function parseIsoDate(iso: string): Date {
  return new Date(`${iso}T12:00:00`);
}

export function addDays(iso: string, days: number): string {
  const date = parseIsoDate(iso);
  date.setDate(date.getDate() + days);
  return isoDate(date);
}

/** 周一为一周之始，与中文日历习惯一致。 */
export function startOfWeek(iso: string): string {
  const date = parseIsoDate(iso);
  const offset = (date.getDay() + 6) % 7;
  date.setDate(date.getDate() - offset);
  return isoDate(date);
}

export function weekDates(iso: string): string[] {
  const start = startOfWeek(iso);
  return Array.from({ length: 7 }, (_, index) => addDays(start, index));
}

export function weekdayShort(iso: string): string {
  return '日一二三四五六'[parseIsoDate(iso).getDay()];
}

/** 「10月29日 周四」。 */
export function displayScheduleDate(iso: string): string {
  const date = parseIsoDate(iso);
  return `${date.getMonth() + 1}月${date.getDate()}日 周${weekdayShort(iso)}`;
}

/** 当天 0 点起的分钟数 → 「08:30」；1440 显示为「24:00」。 */
export function formatMinuteOfDay(minute: number): string {
  const safe = Math.max(0, Math.min(MINUTES_PER_DAY, Math.round(minute)));
  return `${pad2(Math.floor(safe / 60))}:${pad2(safe % 60)}`;
}

export function minutesOfDay(date: Date): number {
  return date.getHours() * 60 + date.getMinutes();
}

export function blockDurationMinutes(
  block: Pick<ScheduleBlock, 'startMinute' | 'endMinute'>
): number {
  return Math.max(0, block.endMinute - block.startMinute);
}

export function blocksTotalMinutes(
  blocks: ReadonlyArray<Pick<ScheduleBlock, 'startMinute' | 'endMinute'>>
): number {
  return blocks.reduce((sum, block) => sum + blockDurationMinutes(block), 0);
}

/** 概览用：「8 小时」「2 小时 30 分钟」「45 分钟」。 */
export function formatDurationMinutes(minutes: number): string {
  const safe = Math.max(0, Math.round(minutes));
  const hours = Math.floor(safe / 60);
  const rest = safe % 60;
  if (hours === 0) return `${rest} 分钟`;
  return rest === 0 ? `${hours} 小时` : `${hours} 小时 ${rest} 分钟`;
}

/** 时间轴左栏用：「2h」「30m」「1h30m」。 */
export function compactDurationMinutes(minutes: number): string {
  const safe = Math.max(0, Math.round(minutes));
  if (safe < 60) return `${safe}m`;
  const hours = Math.floor(safe / 60);
  const rest = safe % 60;
  return rest === 0 ? `${hours}h` : `${hours}h${rest}m`;
}

/** 一段真实专注秒数 → 「1 小时 20 分」「45 分钟」。 */
export function formatFocusSeconds(seconds: number): string {
  const total = Math.max(0, Math.floor(seconds));
  if (total === 0) return '0 分钟';
  if (total < 60) return '不足 1 分钟';
  const minutes = Math.floor(total / 60);
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours === 0) return `${minutes} 分钟`;
  return rest === 0 ? `${hours} 小时` : `${hours} 小时 ${rest} 分`;
}

/** 当前时段：开始 <= 现在 < 结束。没有则返回 null（不在任何时段内）。 */
export function currentBlockId(
  blocks: ReadonlyArray<Pick<ScheduleBlock, 'id' | 'startMinute' | 'endMinute'>>,
  nowMinute: number
): string | null {
  const match = blocks.find(
    block => nowMinute >= block.startMinute && nowMinute < block.endMinute
  );
  return match?.id ?? null;
}

/**
 * 每个时间段的实际专注秒数：按会话与时段的时间重叠比例分摊。
 *
 * 不新增「会话属于哪一段」的绑定字段：用户可能在时段中途开始、跨段结束，
 * 墙钟重叠是唯一不依赖额外事实的口径。落在所有时段之外的专注不会计入任何段，
 * 但仍会计入当天的总专注。
 */
export function blockActualSeconds(
  blocks: readonly ScheduleBlock[],
  sessions: readonly DailySession[],
  date: string
): Record<string, number> {
  const result: Record<string, number> = {};
  if (blocks.length === 0) return result;
  const dayStart = new Date(`${date}T00:00:00`).getTime();
  for (const session of sessions) {
    if (session.durationSeconds <= 0) continue;
    const start = session.startTime;
    const end = session.endTime > start
      ? session.endTime
      : start + session.durationSeconds * 1000;
    const span = Math.max(1000, end - start);
    for (const block of blocks) {
      const blockStart = dayStart + block.startMinute * 60_000;
      const blockEnd = dayStart + block.endMinute * 60_000;
      const overlap = Math.max(0, Math.min(end, blockEnd) - Math.max(start, blockStart));
      if (overlap <= 0) continue;
      const allocated = Math.round(session.durationSeconds * (overlap / span));
      if (allocated <= 0) continue;
      result[block.id] = (result[block.id] ?? 0) + allocated;
    }
  }
  return result;
}

export interface ScheduleProgress {
  completedCount: number;
  totalCount: number;
  completedMinutes: number;
  totalMinutes: number;
  /** 0..1，按已完成段数计算；没有时间段时为 0。 */
  ratio: number;
}

export function scheduleProgress(
  blocks: readonly ScheduleBlock[],
  completedIds: readonly string[]
): ScheduleProgress {
  const completed = new Set(completedIds);
  let completedCount = 0;
  let completedMinutes = 0;
  let totalMinutes = 0;
  for (const block of blocks) {
    const minutes = blockDurationMinutes(block);
    totalMinutes += minutes;
    if (completed.has(block.id)) {
      completedCount += 1;
      completedMinutes += minutes;
    }
  }
  return {
    completedCount,
    totalCount: blocks.length,
    completedMinutes,
    totalMinutes,
    ratio: blocks.length === 0 ? 0 : completedCount / blocks.length,
  };
}

export function sortBlocks<T extends Pick<ScheduleBlock, 'startMinute' | 'endMinute'>>(
  blocks: readonly T[]
): T[] {
  return [...blocks].sort(
    (a, b) => a.startMinute - b.startMinute || a.endMinute - b.endMinute
  );
}

/**
 * 编辑中的时间段校验；返回面向用户的错误文案，null 表示可以保存。
 *
 * 与原生 `ScheduleValidation` 同口径：结束晚于开始、标题/科目非空、不重叠。
 * 客户端先拦一次是为了即时反馈；原生仍会再校验一次，不能只靠 UI。
 */
export function validateBlockDraft(
  draft: ScheduleBlockInput,
  others: readonly ScheduleBlockInput[]
): string | null {
  if (!Number.isInteger(draft.startMinute) || !Number.isInteger(draft.endMinute)) {
    return '时间必须是整分钟';
  }
  if (draft.startMinute < 0 || draft.startMinute >= MINUTES_PER_DAY) {
    return '开始时间必须在 00:00 到 23:59 之间';
  }
  if (draft.endMinute <= draft.startMinute || draft.endMinute > MINUTES_PER_DAY) {
    return '结束时间必须晚于开始时间，且不晚于 24:00';
  }
  if (draft.title.trim().length === 0) return '先填写这一段时间要做什么';
  const overlap = others.find(other =>
    other !== draft && draft.startMinute < other.endMinute && other.startMinute < draft.endMinute
  );
  if (overlap) {
    return `与 ${formatMinuteOfDay(overlap.startMinute)}–${formatMinuteOfDay(overlap.endMinute)} 的时间段重叠`;
  }
  return null;
}

/**
 * 「添加时间段」的默认值：从 08:00 起找第一个能放下 60 分钟的空档。
 * 一天排满时退到 00:00–00:30，至少给用户一个可编辑的起点。
 */
export function nextBlockDraft(
  blocks: ReadonlyArray<Pick<ScheduleBlock, 'startMinute' | 'endMinute'>>
): ScheduleBlockInput {
  const ordered = sortBlocks(blocks);
  let cursor = 8 * 60;
  for (const block of ordered) {
    if (cursor + 60 <= block.startMinute) break;
    cursor = Math.max(cursor, Math.ceil(block.endMinute / SCHEDULE_MINUTE_STEP) * SCHEDULE_MINUTE_STEP);
  }
  if (cursor + 60 <= MINUTES_PER_DAY) {
    return {
      id: null,
      startMinute: cursor,
      endMinute: cursor + 60,
      subjectIds: [],
      subjectLabel: '',
      title: '',
    };
  }
  return {
    id: null,
    startMinute: 0,
    endMinute: 30,
    subjectIds: [],
    subjectLabel: '',
    title: '',
  };
}

/** 复制 / 新建模板时的去重名称。 */
export function uniqueTemplateName(existing: readonly string[], base: string): string {
  const names = new Set(existing);
  if (!names.has(base)) return base;
  let index = 2;
  while (names.has(`${base} ${index}`)) index += 1;
  return `${base} ${index}`;
}

/**
 * 一个时间段可以启动的专注学科。
 *
 * 时间表只让人选大类（数学一 / 408专业课），所以「整个大类」就是这段计划
 * 真正记录到的学科——不能再悄悄解析到第一个子类。停用或缺失的 id 被忽略，
 * 同一学科只出现一次；展示仍用时间表的科目名（「数学」），让用户看到的是
 * 自己写下的计划。
 */
export function focusOptionsForBlock(
  subjectIds: readonly string[],
  subjects: readonly Subject[]
): ScheduleFocusOption[] {
  const byId = new Map(subjects.map(subject => [subject.id, subject]));
  const options: ScheduleFocusOption[] = [];
  const seen = new Set<string>();
  for (const id of subjectIds) {
    const subject = byId.get(id);
    if (!subject || !subject.enabled || seen.has(subject.id)) continue;
    seen.add(subject.id);
    options.push({
      subjectId: subject.id,
      subjectName: subject.name,
      label: subject.name,
      colorHex: subject.colorHex,
    });
  }
  return options;
}

/** 时间轴科目标签的颜色；找不到学科时回落到中性色。 */
export function subjectColorForBlock(
  subjectIds: readonly string[],
  subjects: readonly Subject[],
  fallback: string
): string {
  for (const id of subjectIds) {
    const subject = subjects.find(item => item.id === id);
    if (subject) return subject.colorHex;
  }
  return fallback;
}

/** 周条里的日期数字。 */
export function dayOfMonth(iso: string): number {
  return parseIsoDate(iso).getDate();
}

/** 「10月27日 – 11月2日」；同一月内省略后一个月份。 */
export function weekRangeLabel(iso: string): string {
  const dates = weekDates(iso);
  const first = parseIsoDate(dates[0]);
  const last = parseIsoDate(dates[6]);
  const head = `${first.getMonth() + 1}月${first.getDate()}日`;
  const tail =
    last.getMonth() === first.getMonth()
      ? `${last.getDate()}日`
      : `${last.getMonth() + 1}月${last.getDate()}日`;
  return `${head} – ${tail}`;
}
