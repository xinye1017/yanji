/**
 * Yanji Native Bridge — Strongly typed TypeScript bindings for Kotlin Native Modules.
 *
 * Contract authority: PROJECT.md § Interface Contracts + test-e2e/contracts/bridge-schema.js.
 * Any field rename must be mirrored here, in bridge-schema.js, and in the Kotlin module.
 */

import { NativeEventEmitter, NativeModules, Platform } from 'react-native';

// ---------------------------------------------------------------------------
// Domain models (field names mirror the Kotlin WritableMap keys exactly)
// ---------------------------------------------------------------------------

export type ThemeMode = 'SYSTEM' | 'LIGHT' | 'DARK';

export type TimerPhase = 'FOCUS' | 'BREAK' | 'IDLE';

export type TimerMode = 'COUNTDOWN' | 'STOPWATCH';

export interface ActiveSessionState {
  sessionId: string;
  subjectId: string;
  subjectName: string;
  mode: TimerMode;
  /** ISO date string `yyyy-MM-dd` of the session start, or null when unbound. */
  taskId: string | null;
  startTime: number;
  elapsedSeconds: number;
  remainingSeconds: number;
  phase: TimerPhase;
  isPaused: boolean;
  isCountdown: boolean;
}

export interface StudyTask {
  id: string;
  date: string;
  subjectId: string;
  subjectName: string;
  title: string;
  plannedMinutes: number;
  actualMinutes: number;
  completed: boolean;
  createdAt: number;
}

export interface NoteEntry {
  id: string;
  date: string;
  timestamp: number;
  content: string;
  isFavorite: boolean;
  /** Focus session id, or null when the note is not bound to a session. Never empty string. */
  sessionId: string | null;
}

export interface TodayStats {
  date: string;
  totalFocusSeconds: number;
  totalFocusMinutes: number;
  completedTasksCount: number;
  totalTasksCount: number;
  notesCount: number;
}

/**
 * 回顾页的趋势窗口。
 *
 * - `ROLLING_7` / `ROLLING_30`：以**今天**为终点的滚动窗口（不是自然周），
 *   这样柱状图的横轴不会随星期几漂移。
 * - `CALENDAR_WEEK`：自然周（周一起、周日止），`periodsBack` = 0 表示本周。
 * - `CALENDAR_MONTH`：自然月，`periodsBack` = 0 表示本月。
 */
export type ReviewScope = 'ROLLING_7' | 'ROLLING_30' | 'CALENDAR_WEEK' | 'CALENDAR_MONTH';

/** 窗口里的一天。日历视图里今天之后的格子 `isFuture` 为 true 且时长恒为 0。 */
export interface ReviewPeriodDay {
  /** ISO date `yyyy-MM-dd`，本地日历。 */
  date: string;
  /** `ROLLING_*` 为 `MM-DD`；自然周为「周一」…；自然月为 `D日`。 */
  dayLabel: string;
  durationSeconds: number;
  isToday: boolean;
  isFuture: boolean;
}

/** 一个科目桶在窗口内的占比。桶口径与科目统计一致（子类桶 + 展示名）。 */
export interface ReviewSubjectSlice {
  /** `SubjectCatalog.subcategoryBucketId` 归一后的桶 id。 */
  subjectId: string;
  subjectName: string;
  subjectColor: string;
  /** 整数分钟。窗口内**秒数累加后**才除以 60，零头不逐行丢弃。 */
  minutes: number;
  /** 0..1 占窗口总时长的比例；窗口总时长为 0 时为 0。 */
  share: number;
  /** 与 `ReviewOverview.days[].date` 同一批键，含 0 值日。 */
  dailyMinutes: Record<string, number>;
}

/**
 * 回顾页的趋势概览，取代旧的 `ReviewStats`。
 *
 * 旧契约里三个字段被有意移除：
 * - `activeDays`：阈值来自用户设置却无处可改也无处可见，渲染它等于在回顾页
 *   引入一个用户无法解释的连续天数框架。
 * - `totalFocusHours` / `dailyAverageMinutes`：分钟数先按秒截断再汇总，多天各丢
 *   一次零头。现在统一用秒承载时长，只在展示层换算。
 */
export interface ReviewOverview {
  scope: ReviewScope;
  periodsBack: number;
  /** 窗口的可读标题，如「最近 7 天」「2026年10月」。 */
  label: string;
  /** 等于 `days.length`。 */
  windowDays: number;
  /** 升序（早 → 晚），绝不出现被裁掉的过去日期。 */
  days: ReviewPeriodDay[];
  totalSeconds: number;
  /** 分母是窗口长度，不是有效天数。 */
  dailyAverageSeconds: number;
  examCount: number;
  /** 按 `minutes` 降序，相同再按 `subjectId` 字典序，保证渲染顺序稳定。 */
  subjectDistribution: ReviewSubjectSlice[];
}

export interface DailyTimelineSession {
  id: string;
  title: string;
  subjectId: string;
  subjectName: string;
  subjectColor: string;
  startTime: number;
  endTime: number;
  durationSeconds: number;
  isExam: boolean;
  note: string;
  pauseCount: number;
  mode: string;
}

export interface DailyTimeline {
  date: string;
  formattedDate: string;
  totalDurationSeconds: number;
  focusCount: number;
  examCount: number;
  sessions: DailyTimelineSession[];
  completedTasks: StudyTask[];
  notes: NoteEntry[];
}

export interface UserSettings {
  examDate: string;
  targetSchool: string;
  targetMajor: string;
  themePreference: ThemeMode;
}

export interface ExamCountdown {
  examDate: string;
  daysRemaining: number;
}

export interface ThemePreference {
  mode: ThemeMode;
  isDark: boolean;
}

export interface Subject {
  id: string;
  name: string;
  colorHex: string;
  sortOrder: number;
  enabled: boolean;
  parentId: string | null;
  isCategory: boolean;
}

// ---------------------------------------------------------------------------
// Event payloads
// ---------------------------------------------------------------------------

export interface TimerTickEvent {
  elapsedSeconds: number;
  remainingSeconds: number;
  phase: TimerPhase;
  isPaused: boolean;
}

export interface TimerStateChangedEvent {
  state: 'IDLE' | 'STARTING' | 'ACTIVE' | 'COMPLETING';
  session: ActiveSessionState | null;
}

export type DataChangedType = 'tasks' | 'notes' | 'sessions' | 'settings';

export interface DataChangedEvent {
  type: DataChangedType;
}

// ---------------------------------------------------------------------------
// Native module interfaces
// ---------------------------------------------------------------------------

interface YanjiTimerModuleNative {
  /**
   * 开始一次专注。
   *
   * `mode` 用类型化的 [TimerMode]，不再让调用方拼中文模式名：原生侧的
   * `FocusModes` 是给 Compose UI 看的展示层词汇，把「正向计时」这种字符串
   * 跨桥传递曾让 STOPWATCH 在 RN 侧完全不可达。
   *
   * `plannedMinutes` 是 `COUNTDOWN` 的目标时长；`STOPWATCH` 不限时长，
   * 该值被原生侧忽略。
   */
  startFocus(
    subjectId: string,
    subjectName: string,
    mode: TimerMode,
    note: string,
    taskId: string | null,
    plannedMinutes: number
  ): Promise<boolean>;
  pauseTimer(): Promise<boolean>;
  resumeTimer(): Promise<boolean>;
  completeTimer(): Promise<boolean>;
  discardTimer(): Promise<boolean>;
  getActiveSession(): Promise<ActiveSessionState | null>;
}

interface YanjiDataModuleNative {
  getTodayStats(date: string): Promise<TodayStats>;
  getTodayTasks(date: string): Promise<StudyTask[]>;
  createTask(
    date: string,
    subjectId: string,
    subjectName: string,
    title: string,
    plannedMinutes: number
  ): Promise<StudyTask>;
  toggleTask(taskId: string, completed: boolean): Promise<boolean>;
  deleteTask(taskId: string): Promise<boolean>;
  saveQuickNote(content: string, date: string, sessionId: string | null): Promise<NoteEntry>;
  getNotes(page: number, limit: number): Promise<NoteEntry[]>;
  getNotesForDate(date: string): Promise<NoteEntry[]>;
  toggleFavoriteNote(noteId: string): Promise<boolean>;
  deleteNote(noteId: string): Promise<boolean>;
  getSubjects(): Promise<Subject[]>;
  /**
   * 回顾页的趋势概览。
   *
   * `scope` 不在白名单内时原生侧 reject（`E_INVALID_SCOPE`），不做猜测回落。
   * `periodsBack` 只对 `CALENDAR_*` 有意义，滚动窗口强制为 0。
   */
  getReviewOverview(scope: ReviewScope, periodsBack: number): Promise<ReviewOverview>;
  /**
   * 编辑一条已完成专注的随笔。模考暂不支持（原生 reject `E_UNSUPPORTED`）。
   * 空内容 reject `E_EMPTY_NOTE`；找不到会话 reject `E_SESSION_NOT_FOUND`。
   */
  updateSessionNote(sessionId: string, isExam: boolean, note: string): Promise<boolean>;
  /** 删除一条专注/模考记录。找不到会话 reject `E_SESSION_NOT_FOUND`。 */
  deleteSessionRecord(sessionId: string, isExam: boolean): Promise<boolean>;
  getDailyTimeline(date: string): Promise<DailyTimeline>;
  getUserSettings(): Promise<UserSettings>;
  updateUserSettings(settings: Partial<UserSettings>): Promise<boolean>;
  getExamCountdown(): Promise<ExamCountdown>;
}

interface YanjiThemeModuleNative {
  getThemePreference(): Promise<ThemePreference>;
  setThemePreference(mode: ThemeMode): Promise<boolean>;
}

// ---------------------------------------------------------------------------
// Module resolution
// ---------------------------------------------------------------------------

const LINKING_ERROR =
  `The 'YanjiPackage' native package is not linked. Rebuild the Android app ` +
  `(./gradlew.bat assembleDebug) after installing the RN dependencies.`;

function requireNativeModule<T>(name: string): T {
  const module = (NativeModules as Record<string, T | undefined>)[name];
  if (!module) {
    throw new Error(LINKING_ERROR);
  }
  return module;
}

export const YanjiTimerNative = requireNativeModule<YanjiTimerModuleNative>('YanjiTimerModule');
export const YanjiDataNative = requireNativeModule<YanjiDataModuleNative>('YanjiDataModule');
export const YanjiThemeNative = requireNativeModule<YanjiThemeModuleNative>('YanjiThemeModule');

const emitter =
  Platform.OS === 'android' ? new NativeEventEmitter(NativeModules.YanjiTimerModule) : null;

// ---------------------------------------------------------------------------
// Event subscriptions (return unsubscribe functions)
// ---------------------------------------------------------------------------

export function onTimerTick(callback: (event: TimerTickEvent) => void): () => void {
  const subscription = emitter?.addListener('onTimerTick', (payload: object) =>
    callback(payload as TimerTickEvent)
  );
  return () => subscription?.remove();
}

export function onTimerStateChanged(
  callback: (event: TimerStateChangedEvent) => void
): () => void {
  const subscription = emitter?.addListener('onTimerStateChanged', (payload: object) =>
    callback(payload as TimerStateChangedEvent)
  );
  return () => subscription?.remove();
}

export function onDataChanged(callback: (event: DataChangedEvent) => void): () => void {
  const subscription = emitter?.addListener('onDataChanged', (payload: object) =>
    callback(payload as DataChangedEvent)
  );
  return () => subscription?.remove();
}

export function onThemeChanged(callback: (event: ThemePreference) => void): () => void {
  const subscription = emitter?.addListener('onThemeChanged', (payload: object) =>
    callback(payload as ThemePreference)
  );
  return () => subscription?.remove();
}
