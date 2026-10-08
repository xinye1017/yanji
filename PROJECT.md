# Project: 研迹 (Yanji) React Native Add-to-App Refactoring

## Architecture
- **Native Android / Kotlin Business Core**:
  - Room Database v20 (`YanjiDatabase.kt`, 19 migrations preserved, zero fallbackToDestructiveMigration).
  - Monotonic Timing Engine (`FocusTimerService`, `ActiveSessionCoordinator`, `TimerMachine`, `SystemMonotonicClock` via `SystemClock.elapsedRealtime`).
  - Forefront Notification with system Chronometer.
  - Preserved 47 JVM unit test classes in `app/src/test/`.
- **Native Bridge Layer (`com.example.yanji.bridge`)**:
  - `YanjiTimerModule`: Exposes session control (`startFocus`, `pauseTimer`, `resumeTimer`, `completeTimer`, `discardTimer`, `getActiveSession`) and timer tick/state events.
  - `YanjiDataModule`: Exposes Room data queries and mutations for tasks (`StudyTaskEntity`), notes (`NoteEntryEntity`), subjects, and settings without bypassing repository or business coordinators.
  - `YanjiThemeModule`: Exposes theme mode reading and change events.
  - `YanjiPackage`: Integrates modules into `ReactNativeHost`.
- **React Native Application (`src/`)**:
  - React Native 0.87.1 + React 19 + TypeScript 5.8 + NativeWind v4 + Tailwind CSS 3.4.
  - Design Tokens: Midnight Blue dark mode (no `#000000`), soft blue-gray light mode, card zero-border, `YanjiRadius` scale.
  - Information Architecture: Strict 3-tab navigation (`Today`, `Focus`, `Review`), Settings accessed from Today top-right.
  - Shared Component: Cross-page `RecordMomentModal` saving instantly to Room without interrupting focus timer.

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | RN Add-to-App Gradle & Maven setup | Integrate react-android 0.87.1 AAR into AGP 9.0.1 / Gradle 9.1.0 build | M1 | Survey |
| 2 | Offline Standalone JS Bundle | Package `assets/index.android.bundle` for offline APK assembly | M1 | Survey |
| 3 | Material3 Theme Compatibility | Update `Theme.Yanji` parent to `Theme.Material3.DayNight.NoActionBar` | M1 | Survey |
| 4 | React Native Host Activity & App | `YanjiApplication` implements `ReactApplication`, `MainActivity` extends `ReactActivity` | M1 | Survey |
| 5 | Native Timer Module (`YanjiTimerModule`) | Bridge `ActiveSessionCoordinator` and `FocusTimerService` to JS | M2 | Survey |
| 6 | Native Data Module (`YanjiDataModule`) | Bridge Room tasks, notes, subjects, and study stats to JS | M2 | Survey |
| 7 | Native Theme Module (`YanjiThemeModule`) | Bridge system uiMode and app theme preference to JS | M2 | Survey |
| 8 | TypeScript Bridge Interface Contracts | Strongly typed TypeScript APIs and event listeners | M2 | Survey |
| 9 | Design Tokens (Midnight Blue, Radius, Zero-border) | NativeWind theme extension with exact color, radius, and zero-border rules | M3 | Survey |
| 10 | Three-tab Navigation Shell | Strict 3-tab dock: Today, Focus, Review | M3 | Survey |
| 11 | Settings Screen & Navigation Flow | Clean preferences screen accessed from Today top-right icon | M3 | Survey |
| 12 | Today Screen Overview & Adaptive Single CTA | Date, exam countdown, today study stats, and single adaptive action button | M4 | Survey |
| 13 | Today Screen Task List & Quick Start | Today tasks list, completion toggle, and quick-start actions | M4 | Survey |
| 14 | Focus Screen Monotonic Timer & Mode Memory | Large timer digits, stopwatch vs countdown toggle with memory, session controls | M4 | Survey |
| 15 | Cross-page "Record Moment" Instant Modal | Shared modal for quick text capture with auto timestamp and active session binding | M4 | Survey |
| 16 | Non-interrupting Record Moment during Focus | Pop up Record Moment during active timer without pausing or jitter | M4 | Survey |
| 17 | Review Screen Deterministic Daily Timeline | Aggregated focus sessions, completed tasks, and notes by day from Room | M5 | Survey |
| 18 | Review Screen 7-Day Trend & Subject Breakdown | 7-day focus chart and subject distribution from Room statistics | M5 | Survey |
| 19 | Clean AI Capability Placeholder | Graceful future AI expansion slot without fake dialogue or mock data | M5 | Survey |
| 20 | E2E Test Suite Pass (Tiers 1-4) | Pass 100% of requirement-driven opaque-box test suite | Final Milestone | Survey |
| 21 | Adversarial Coverage Hardening (Tier 5) | White-box adversarial testing and coverage hardening | Final Milestone | Survey |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | RN Add-to-App Infra & Build System | package.json, TypeScript, NativeWind, Gradle integration, ReactApplication, ReactActivity, themes.xml, asset bundling | none | ✅ COMPLETE |
| 2 | Native Bridge Layer & Contracts | YanjiTimerModule, YanjiDataModule, YanjiThemeModule, YanjiPackage, TypeScript bridge definitions | M1 | ✅ COMPLETE |
| 3 | Design System & Navigation Shell | Tailwind/NativeWind config, theme tokens, 3-tab bottom bar, Settings screen | M1, M2 | ✅ COMPLETE |
| 4 | Today, Focus & Record Moment Views | TodayScreen (overview, CTA, tasks), FocusScreen (timer, modes), RecordMomentModal (cross-page, non-pausing) | M2, M3 | ✅ COMPLETE |
| 5 | Review View & Historical Timeline | ReviewScreen (daily timeline, 7-day trend, subject breakdown, AI expansion slot) | M2, M3 | PLANNED |
| Final | E2E Test Suite & Adversarial Hardening | Pass 100% of E2E test suite (Tiers 1-4) + Tier 5 adversarial coverage hardening | M4, M5, TEST_READY | PLANNED |

## Interface Contracts

### `YanjiTimerModule` (Native ↔ JS)
- `startFocus(subjectId: string, subjectName: string, mode: string, note: string, taskId?: string): Promise<boolean>`
- `pauseTimer(): Promise<boolean>`
- `resumeTimer(): Promise<boolean>`
- `completeTimer(): Promise<boolean>`
- `discardTimer(): Promise<boolean>`
- `getActiveSession(): Promise<ActiveSessionState | null>`
- Events:
  - `onTimerTick(event: { elapsedSeconds: number, remainingSeconds: number, phase: string, isPaused: boolean })`
  - `onTimerStateChanged(event: { state: string, session: ActiveSessionState | null })`

### `YanjiDataModule` (Native ↔ JS)
- `getTodayStats(date: string): Promise<TodayStats>`
- `getTodayTasks(date: string): Promise<StudyTask[]>`
- `createTask(date: string, subjectId: string, subjectName: string, title: string, plannedMinutes: number): Promise<StudyTask>`
- `toggleTask(taskId: string, completed: boolean): Promise<boolean>`
- `deleteTask(taskId: string): Promise<boolean>`
- `saveQuickNote(content: string, date: string, sessionId?: string | null): Promise<NoteEntry>`
- `getNotes(page: number, limit: number): Promise<NoteEntry[]>`
- `getNotesForDate(date: string): Promise<NoteEntry[]>`
- `toggleFavoriteNote(noteId: string): Promise<boolean>`
- `deleteNote(noteId: string): Promise<boolean>`
- `getSubjects(): Promise<Subject[]>`
- `getReviewStats(days: number): Promise<ReviewStats>`
- `getDailyTimeline(date: string): Promise<DailyTimeline>`
- `getUserSettings(): Promise<UserSettings>`
- `updateUserSettings(settings: Partial<UserSettings>): Promise<boolean>`
- `getExamCountdown(): Promise<{ examDate: string, daysRemaining: number }>`
- Events:
  - `onDataChanged(event: { type: 'tasks' | 'notes' | 'sessions' | 'settings' })`
#### Shared type contracts
- `TimerPhase = 'FOCUS' | 'BREAK' | 'IDLE'` — the only phase vocabulary across the bridge. A paused countdown is still `FOCUS` with `isPaused: true`; it is never a separate `PAUSED` phase.
- `TimerMode = 'COUNTDOWN' | 'STOPWATCH'` — timer mode. Planned seconds come from the mode name (FocusModes.targetSeconds), not from UserSettings.
- `NoteEntry.sessionId: string | null` — the focus session the note is bound to, or `null` when the note is not bound to a session. It is never an empty string.
- `UserSettings` fields: `examDate`, `targetSchool`, `targetMajor`, `themePreference`. There is deliberately **no** `focusDurationMinutes` / `breakDurationMinutes` — the domain UserSettings has no such fields, so any value would be fabricated.
- `ReviewStats` fields: `days`, `dailyFocusMinutes`, `subjectDistribution`, `totalFocusHours`, `dailyAverageMinutes`, `activeDays`.
  - getReviewStats(days) returns a **rolling N-day window**: exactly days entries, from (today - (days - 1)) through today.
  - Keys are local-calendar yyyy-MM-dd, produced by shifting the local date — never by subtracting 24h multiples (which drifts across DST).
  - No future-dated keys: tomorrow can never appear, not even as a zero-valued bar.
  - No calendar-week clipping: the window is not aligned to Monday, so the x-axis does not drift with the weekday.
  - activeDays counts days with at least 30 recorded minutes. A window with no data reports activeDays: 0 and dailyAverageMinutes: 0, never a faked value.
  - dailyAverageMinutes is totalMinutes / days, rounded.

### `YanjiThemeModule` (Native ↔ JS)
- `getThemePreference(): Promise<{ mode: 'SYSTEM' | 'LIGHT' | 'DARK', isDark: boolean }>`
- `setThemePreference(mode: 'SYSTEM' | 'LIGHT' | 'DARK'): Promise<boolean>`
- Events:
  - `onThemeChanged(event: { mode: string, isDark: boolean })`

## Code Layout
- `package.json`, `tsconfig.json`, `tailwind.config.js`, `babel.config.js`, `metro.config.js`: Project Root
- `app/src/main/java/com/example/yanji/bridge/`: Native Modules (`YanjiTimerModule.kt`, `YanjiDataModule.kt`, `YanjiThemeModule.kt`, `YanjiPackage.kt`)
- `app/src/main/java/com/example/yanji/YanjiApplication.kt`: ReactApplication host
- `app/src/main/java/com/example/yanji/MainActivity.kt`: ReactActivity host
- `app/src/main/assets/index.android.bundle`: Compiled standalone offline JS bundle
- `src/`: React Native TypeScript source root
  - `src/bridge/`: Strongly typed Native Module bindings & mock stubs for web/testing
  - `src/theme/`: NativeWind theme tokens, colors, radius, typography
  - `src/navigation/`: Navigation Shell & 3-tab Bottom Bar
  - `src/screens/`: TodayScreen, FocusScreen, ReviewScreen, SettingsScreen
  - `src/components/`: RecordMomentModal, YanjiCard, YanjiButton, StatBar, TaskItem
- `e2e/` or `test-e2e/`: E2E Test Suite (Tiers 1-4)
