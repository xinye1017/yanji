/**
 * Tier 1 — Features 5 to 8: Native Bridge Layer & TypeScript Contracts
 */

import { describe, it, beforeEach } from '../framework/harness.js';
import { assert, assertEqual, assertDeepEqual, assertRejects } from '../framework/assertions.js';
import { MockYanjiBridge } from '../contracts/mock-bridge.js';
import { BridgeSchemas } from '../contracts/bridge-schema.js';

export function registerM2Tests() {
  let bridge;

  beforeEach(() => {
    bridge = new MockYanjiBridge();
  });

  describe('Tier 1: Feature 5 — Native Timer Module (YanjiTimerModule)', () => {
    it('verifies session lifecycle: start, tick, pause, resume, complete', async () => {
      // 1. Initially no active session
      const initial = await bridge.getActiveSession();
      assertEqual(initial, null, 'Active session should be null initially');

      // 2. Start countdown focus
      const started = await bridge.startFocus('sub-math', '数学', 'COUNTDOWN', '真题强化');
      assertEqual(started, true, 'startFocus should return true');

      const active = await bridge.getActiveSession();
      assert(BridgeSchemas.isValidActiveSessionState(active), 'Active session state must match schema');
      assertEqual(active.subjectId, 'sub-math');
      assertEqual(active.mode, 'COUNTDOWN');
      assertEqual(active.isPaused, false);
      assertEqual(active.elapsedSeconds, 0);

      // 3. Monotonic tick advances
      bridge.advanceTime(60); // 1 minute
      const after1m = await bridge.getActiveSession();
      assertEqual(after1m.elapsedSeconds, 60, 'Elapsed seconds should advance to 60');

      // 4. Pause session
      await bridge.pauseTimer();
      const paused = await bridge.getActiveSession();
      assertEqual(paused.isPaused, true, 'isPaused must be true after pauseTimer');

      // 5. Resume session
      await bridge.resumeTimer();
      const resumed = await bridge.getActiveSession();
      assertEqual(resumed.isPaused, false, 'isPaused must be false after resumeTimer');

      // 6. Complete session
      await bridge.completeTimer();
      const completed = await bridge.getActiveSession();
      assertEqual(completed, null, 'Active session should be cleared after completion');
    });

    it('verifies discarding an active session does not persist false study records', async () => {
      await bridge.startFocus('sub-eng', '英语', 'STOPWATCH', '临时开始');
      bridge.advanceTime(120); // 2 minutes

      await bridge.discardTimer();
      const active = await bridge.getActiveSession();
      assertEqual(active, null, 'Session should be null after discard');

      const today = new Date().toISOString().split('T')[0];
      const stats = await bridge.getTodayStats(today);
      assertEqual(stats.totalFocusSeconds, 0, 'Discarded session must not commit study seconds');
    });

    it('prevents starting a concurrent second session while one is already running', async () => {
      await bridge.startFocus('sub-math', '数学', 'COUNTDOWN');
      await assertRejects(async () => {
        await bridge.startFocus('sub-eng', '英语', 'COUNTDOWN');
      }, /already active/, 'Concurrent startFocus must reject to protect monotonic state');
    });
  });

  describe('Tier 1: Feature 6 — Native Data Module (YanjiDataModule)', () => {
    it('verifies task CRUD and completion toggle operations', async () => {
      const today = '2026-10-08';
      const created = await bridge.createTask(today, 'sub-math', '数学', '高等数学极限大题', 45);
      assert(BridgeSchemas.isValidStudyTask(created), 'Created task must match StudyTask schema');
      assertEqual(created.completed, false);

      const tasks = await bridge.getTodayTasks(today);
      assertEqual(tasks.length, 1);
      assertEqual(tasks[0].title, '高等数学极限大题');

      // Toggle completed
      await bridge.toggleTask(created.id, true);
      const updatedTasks = await bridge.getTodayTasks(today);
      assertEqual(updatedTasks[0].completed, true, 'Task completed status should be updated');

      // Delete task
      await bridge.deleteTask(created.id);
      const remainingTasks = await bridge.getTodayTasks(today);
      assertEqual(remainingTasks.length, 0, 'Task should be deleted');
    });

    it('verifies quick note saving and retrieval with timestamp', async () => {
      const today = '2026-10-08';
      const note = await bridge.saveQuickNote('微分中值定理构造辅助函数四种经典套路', today);
      assert(BridgeSchemas.isValidNoteEntry(note), 'Saved note must match NoteEntry schema');
      assertEqual(note.content, '微分中值定理构造辅助函数四种经典套路');

      const notes = await bridge.getNotesForDate(today);
      assertEqual(notes.length, 1);
      assertEqual(notes[0].id, note.id);
    });

    it('verifies user settings queries and partial updates', async () => {
      const settings = await bridge.getUserSettings();
      assert(BridgeSchemas.isValidUserSettings(settings), 'Settings must match UserSettings schema');

      await bridge.updateUserSettings({ targetMajor: '计算机科学与技术' });
      const updated = await bridge.getUserSettings();
      assertEqual(updated.targetMajor, '计算机科学与技术', 'targetMajor should update');

      // focusDurationMinutes / breakDurationMinutes were removed from the
      // contract: UserSettings has no such fields, so no value is legitimate.
      assert(!('focusDurationMinutes' in updated), 'focusDurationMinutes must not be part of UserSettings');
      assert(!('breakDurationMinutes' in updated), 'breakDurationMinutes must not be part of UserSettings');
    });

    it('verifies exam countdown returns valid days calculation', async () => {
      const countdown = await bridge.getExamCountdown();
      assert(typeof countdown.examDate === 'string', 'Exam date must be string');
      assert(typeof countdown.daysRemaining === 'number', 'Days remaining must be number');
      assert(countdown.daysRemaining >= 0, 'Days remaining must be non-negative');
    });
  });

  describe('Tier 1: Feature 7 — Native Theme Module (YanjiThemeModule)', () => {
    it('verifies reading theme preference and system mode bridge', async () => {
      const themePref = await bridge.getThemePreference();
      assertEqual(themePref.mode, 'SYSTEM');
      assertEqual(typeof themePref.isDark, 'boolean');
    });

    it('verifies setting theme preference to DARK / LIGHT with event emission', async () => {
      let notifiedTheme = null;
      bridge.onThemeChanged(event => {
        notifiedTheme = event;
      });

      await bridge.setThemePreference('DARK');
      assertEqual(notifiedTheme.mode, 'DARK');
      assertEqual(notifiedTheme.isDark, true);

      await bridge.setThemePreference('LIGHT');
      assertEqual(notifiedTheme.mode, 'LIGHT');
      assertEqual(notifiedTheme.isDark, false);
    });

    it('rejects invalid theme mode arguments', async () => {
      await assertRejects(async () => {
        await bridge.setThemePreference('SOLARIZED_YELLOW');
      }, /Invalid theme mode/);
    });
  });

  describe('Tier 1: Feature 8 — TypeScript Bridge Interface Contracts', () => {
    it('validates schema conformance for all returned data models', async () => {
      const today = '2026-10-08';
      const stats = await bridge.getTodayStats(today);
      assert(BridgeSchemas.isValidTodayStats(stats), 'TodayStats must strictly conform to contract');

      const reviewStats = await bridge.getReviewStats(7);
      assert(BridgeSchemas.isValidReviewStats(reviewStats), 'ReviewStats must strictly conform to contract');

      const timeline = await bridge.getDailyTimeline(today);
      assert(BridgeSchemas.isValidDailyTimeline(timeline), 'DailyTimeline must strictly conform to contract');
    });
  });
}
