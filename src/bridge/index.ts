/**
 * Yanji Native Bridge — Strongly typed TypeScript bindings for Kotlin Native Modules.
 *
 * Contract authority: the native module mappings + test-e2e/contracts/bridge-schema.js.
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
  /** Bound study task id, or null when unbound. */
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
  subjectColor: string;
  title: string;
  plannedMinutes: number;
  actualMinutes: number;
  completed: boolean;
  createdAt: number;
}

export type NoteCategory = 'INSPIRATION' | 'DOUBT';

export interface NoteEntry {
  id: string;
  date: string;
  timestamp: number;
  content: string;
  isFavorite: boolean;
  /** Focus session id, or null when the note is not bound to a session. Never empty string. */
  sessionId: string | null;
  /** Category determined by Decision Model or default: INSPIRATION / DOUBT */
  category: NoteCategory;
  /** Whether a doubt has been resolved (only meaningful for DOUBT) */
  isResolved: boolean;
  /** Timestamp when the doubt was resolved, or null */
  resolvedAt: number | null;
}

/**
 * 回顾页的趋势窗口。
 *
 * - `TODAY`：`anchorDate`（缺省为今天）那一天的单日窗口。
 * - `CALENDAR_WEEK`：自然周（周一起、周日止），`periodsBack` = 0 表示本周。
 * - `CALENDAR_MONTH`：自然月，`periodsBack` = 0 表示本月。
 */
export type ReviewScope = 'TODAY' | 'CALENDAR_WEEK' | 'CALENDAR_MONTH';

/** 窗口里的一天。日历视图里今天之后的格子 `isFuture` 为 true 且时长恒为 0。 */
export interface ReviewPeriodDay {
  /** ISO date `yyyy-MM-dd`，本地日历。 */
  date: string;
  /** 单日 / 自然月为「D日」；自然周为「周一」…。 */
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
  /**
   * 窗口的可读标题，如「最近 7 天」「2026年10月」。
   * 滚动窗口只在终点确实是今天时才写「最近 N 天」，否则如实报首尾日期。
   */
  label: string;
  /** 等于 `days.length`，含自然周 / 自然月里还没发生的日子。 */
  windowDays: number;
  /** 升序（早 → 晚），绝不出现被裁掉的过去日期。 */
  days: ReviewPeriodDay[];
  totalSeconds: number;
  /**
   * 分母是**已过去的天数**（`days` 中 `!isFuture` 的个数），不是 `windowDays`。
   * 自然月的窗口一直排到月末，用窗口长度当分母会把日均稀释成无法解释的数。
   */
  dailyAverageSeconds: number;
  examCount: number;
  /** 按 `minutes` 降序，相同再按 `subjectId` 字典序，保证渲染顺序稳定。 */
  subjectDistribution: ReviewSubjectSlice[];
}

export interface DailySession {
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
  /**
   * 关联的「今日计划」id，未挂计划时为 null（绝不是空串）。
   *
   * 时间轴上用它把一段专注标注成「达成关联任务」——不靠标题或学科反推匹配。
   * 模考恒为 null：模考不挂计划。
   */
  taskId: string | null;
}

/** One Room transaction, shared by Today and Review. */
export interface DailySnapshot {
  date: string;
  formattedDate: string;
  totalDurationSeconds: number;
  focusCount: number;
  /**
   * 当天「参与」过的专注次数：按时间区间相交统计，跨午夜的一条会在两天各计一次；
   * 时间轴与总时长仍按开始日期归属，所以同一历史记录只出现、只累计一次。
   */
  focusParticipationCount: number;
  examCount: number;
  sessions: DailySession[];
  /** All plans for the requested day; completed plans are filtered by the view. */
  tasks: StudyTask[];
  notes: NoteEntry[];
}

/**
 * 宿主冷启动预热载荷（`MainActivity.getLaunchOptions()` → 根组件 props）。
 *
 * 与 [DailySnapshot]、[UserSettings]、[ExamCountdown] 逐字段一致，只是**提前**到了
 * JS 首帧之前：首页第一帧直接渲染真实数据，不再先显示「正在读取…」再过桥刷新一次。
 * 预热失败时这些 props 不存在，页面照原路径过桥读取。
 */
export interface YanjiLaunchProperties {
  /** 预热时原生侧认定的「今天」（`yyyy-MM-dd`）；与 dailySnapshot.date 不符时 JS 侧丢弃。 */
  date: string;
  dailySnapshot: DailySnapshot;
  settings: UserSettings;
  examCountdown: ExamCountdown;
}

export interface UserSettings {
  examDate: string;
  targetSchool: string;
  targetMajor: string;
  themePreference: ThemeMode;
  /** Default daily focus target, persisted in Room. Optional for older native hosts. */
  dailyGoalHours?: number;
  /** Decision Model provider ('openrouter' | 'typesafe') */
  decisionProvider?: 'openrouter' | 'typesafe';
  /** Decision Model name */
  decisionModel?: string;
  /** Decision API endpoint */
  decisionEndpoint?: string;
  /** Whether an API Key is currently stored securely in Android Keystore */
  hasDecisionApiKey?: boolean;
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

export type DataChangedType = 'tasks' | 'notes' | 'sessions' | 'settings' | 'subjects';

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
   * `FocusModes` 是 Room 里持久化的模式名（同时也是展示文案），把「正向计时」
   * 这种字符串跨桥传递曾让 STOPWATCH 在 RN 侧完全不可达。
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
  getDailySnapshot(date: string): Promise<DailySnapshot>;
  createTask(
    date: string,
    subjectId: string,
    subjectName: string,
    title: string,
    plannedMinutes: number
  ): Promise<StudyTask>;
  toggleTask(taskId: string, completed: boolean): Promise<boolean>;
  updateTask(taskId: string, title: string, plannedMinutes: number): Promise<boolean>;
  deleteTask(taskId: string): Promise<boolean>;
  saveQuickNote(
    content: string,
    date: string,
    sessionId: string | null,
    category?: string | null
  ): Promise<NoteEntry>;
  toggleFavoriteNote(noteId: string): Promise<boolean>;
  toggleNoteResolved(noteId: string): Promise<boolean>;
  deleteNote(noteId: string): Promise<boolean>;
  /**
   * 编辑一条「记录此刻」的正文。空内容 reject `E_EMPTY_NOTE`；
   * id 不存在 reject `E_NOTE_NOT_FOUND`。日期、归属会话与收藏状态不变。
   */
  updateNote(noteId: string, content: string): Promise<boolean>;
  getSubjects(): Promise<Subject[]>;
  renameSubject(subjectId: string, newName: string): Promise<boolean>;
  addSubSubject(parentId: string, name: string): Promise<Subject | null>;
  addSubjectCategory(name: string): Promise<Subject | null>;
  deleteSubject(subjectId: string): Promise<boolean>;
  restoreDefaultSubjects(): Promise<boolean>;
  /**
   * 回顾页的趋势概览。
   *
   * `scope` 不在白名单内时原生侧 reject（`E_INVALID_SCOPE`），不做猜测回落。
   * `periodsBack` 只对 `CALENDAR_*` 有意义，滚动窗口强制为 0。
   * `anchorDate`（缺省为今天）决定窗口落在哪：`TODAY` 就是那一天，
   * `CALENDAR_*` 用它选出是哪一周 / 哪一月。
   */
  getReviewOverview(
    scope: ReviewScope,
    periodsBack: number,
    anchorDate?: string | null
  ): Promise<ReviewOverview>;
  /**
   * 编辑一条已完成专注的随笔。模考暂不支持（原生 reject `E_UNSUPPORTED`）。
   * 空内容 reject `E_EMPTY_NOTE`；找不到会话 reject `E_SESSION_NOT_FOUND`。
   */
  updateSessionNote(sessionId: string, isExam: boolean, note: string): Promise<boolean>;
  /** 删除一条专注/模考记录。找不到会话 reject `E_SESSION_NOT_FOUND`。 */
  deleteSessionRecord(sessionId: string, isExam: boolean): Promise<boolean>;
  getUserSettings(): Promise<UserSettings>;
  updateUserSettings(settings: Partial<UserSettings>): Promise<boolean>;
  saveDecisionApiKey(apiKey: string): Promise<boolean>;
  getDecisionApiKey(): Promise<string>;
  clearDecisionApiKey(): Promise<boolean>;
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
