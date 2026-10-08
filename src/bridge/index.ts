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

export type TimerPhase = 'IDLE' | 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'CANCELLED';

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
  sessionId: string;
}

export interface TodayStats {
  date: string;
  totalFocusSeconds: number;
  totalFocusMinutes: number;
  completedTasksCount: number;
  totalTasksCount: number;
  notesCount: number;
}

export interface ReviewStats {
  days: number;
  dailyFocusMinutes: Record<string, number>;
  subjectDistribution: Record<string, number>;
  totalFocusHours: number;
  dailyAverageMinutes: number;
  activeDays: number;
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
  focusDurationMinutes: number;
  breakDurationMinutes: number;
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
  startFocus(
    subjectId: string,
    subjectName: string,
    mode: string,
    note: string,
    taskId: string | null
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
  getReviewStats(days: number): Promise<ReviewStats>;
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
