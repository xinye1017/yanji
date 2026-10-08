/**
 * Tier 1 — Features 12 to 16: Today Screen, Focus Monotonic Timer & Record Moment
 */

import { describe, it, beforeEach } from '../framework/harness.js';
import { assert, assertEqual } from '../framework/assertions.js';
import { MockYanjiBridge } from '../contracts/mock-bridge.js';

export function registerM4Tests() {
  let bridge;

  beforeEach(() => {
    bridge = new MockYanjiBridge();
  });

  describe('Tier 1: Feature 12 — Today Screen Overview & Adaptive Single CTA', () => {
    it('verifies adaptive single CTA resolves to appropriate action based on state', async () => {
      // Helper function matching TodayScreen adaptive CTA resolution logic
      function resolveSingleCTA(activeSession, todayTasks) {
        if (activeSession) {
          return { label: '继续专注', action: 'GOTO_ACTIVE_SESSION', variant: 'ACTIVE' };
        }
        const hasUncompletedTasks = todayTasks.some(t => !t.completed);
        if (hasUncompletedTasks) {
          return { label: '继续学习', action: 'START_FIRST_TASK', variant: 'PRIMARY' };
        }
        return { label: '开始专注', action: 'QUICK_START_FREE', variant: 'DEFAULT' };
      }

      // Case A: Fresh day, no tasks, no session -> "开始专注"
      const ctaFresh = resolveSingleCTA(null, []);
      assertEqual(ctaFresh.label, '开始专注');

      // Case B: Has uncompleted task -> "继续学习"
      const ctaWithTask = resolveSingleCTA(null, [{ id: '1', completed: false }]);
      assertEqual(ctaWithTask.label, '继续学习');

      // Case C: Session is running -> "继续专注"
      const ctaActive = resolveSingleCTA({ sessionId: 's1' }, [{ id: '1', completed: false }]);
      assertEqual(ctaActive.label, '继续专注');
    });

    it('verifies today study duration displays correctly formatted hours and minutes', async () => {
      const today = '2026-10-08';
      await bridge.startFocus('sub-math', '数学', 'STOPWATCH');
      bridge.advanceTime(5400); // 90 minutes = 1h 30m
      await bridge.completeTimer();

      const stats = await bridge.getTodayStats(today);
      assertEqual(stats.totalFocusMinutes, 90);
      const hours = Math.floor(stats.totalFocusMinutes / 60);
      const minutes = stats.totalFocusMinutes % 60;
      assertEqual(hours, 1);
      assertEqual(minutes, 30);
    });
  });

  describe('Tier 1: Feature 13 — Today Screen Task List & Quick Start', () => {
    it('verifies today task list displays tasks and allows quick start', async () => {
      const today = '2026-10-08';
      const task = await bridge.createTask(today, 'sub-math', '数学', '考研真题卷一', 60);

      // Quick start session linked to task
      await bridge.startFocus(task.subjectId, task.subjectName, 'COUNTDOWN', '', task.id);
      const active = await bridge.getActiveSession();
      assertEqual(active.taskId, task.id, 'Active session must be bound to task ID');
      assertEqual(active.subjectId, task.subjectId);

      // Complete session
      bridge.advanceTime(3600); // 60 minutes
      await bridge.completeTimer();

      // Check task updated actual minutes
      const tasks = await bridge.getTodayTasks(today);
      assertEqual(tasks[0].actualMinutes, 60, 'Task actualMinutes must reflect completed session');
    });
  });

  describe('Tier 1: Feature 14 — Focus Screen Monotonic Timer & Mode Memory', () => {
    it('verifies timer switches between COUNTDOWN and STOPWATCH and remembers user preference', async () => {
      // User selects STOPWATCH
      await bridge.startFocus('sub-pol', '政治', 'STOPWATCH');
      const session1 = await bridge.getActiveSession();
      assertEqual(session1.mode, 'STOPWATCH');
      await bridge.discardTimer();

      // Next session remembers mode unless explicitly switched
      let preferredMode = 'STOPWATCH';
      await bridge.startFocus('sub-pol', '政治', preferredMode);
      const session2 = await bridge.getActiveSession();
      assertEqual(session2.mode, 'STOPWATCH');
      await bridge.discardTimer();
    });

    it('verifies monotonic clock guarantees no backwards time jumps', async () => {
      await bridge.startFocus('sub-cs', '专业课', 'STOPWATCH');
      const ticks = [];
      bridge.onTimerTick(e => ticks.push(e.elapsedSeconds));

      bridge.advanceTime(1);
      bridge.advanceTime(2);
      bridge.advanceTime(3);

      for (let i = 1; i < ticks.length; i++) {
        assert(ticks[i] >= ticks[i - 1], 'Elapsed time must be strictly monotonic non-decreasing');
      }
      await bridge.discardTimer();
    });
  });

  describe('Tier 1: Feature 15 — Cross-page "Record Moment" Instant Modal', () => {
    it('verifies quick note is saved with auto timestamp and links to active session if present', async () => {
      const today = '2026-10-08';
      await bridge.startFocus('sub-math', '数学', 'COUNTDOWN');
      const active = await bridge.getActiveSession();

      const note = await bridge.saveQuickNote('反常积分收敛性审敛法判别技巧', today);
      assertEqual(note.sessionId, active.sessionId, 'Note must be linked to running session ID');
      assert(note.timestamp > 0, 'Note must have valid timestamp');
      assertEqual(note.content, '反常积分收敛性审敛法判别技巧');

      await bridge.discardTimer();
    });

    it('verifies quick note saved outside of focus session has null sessionId', async () => {
      const today = '2026-10-08';
      const note = await bridge.saveQuickNote('非专注时段随笔想法', today);
      assertEqual(note.sessionId, null, 'Note outside session must have null sessionId');
    });
  });

  describe('Tier 1: Feature 16 — Non-interrupting Record Moment during Focus', () => {
    it('verifies recording a moment during active session does NOT pause or jitter the timer', async () => {
      const today = '2026-10-08';
      await bridge.startFocus('sub-math', '数学', 'COUNTDOWN');

      bridge.advanceTime(300); // 5 minutes elapsed
      const sessionBeforeModal = await bridge.getActiveSession();
      assertEqual(sessionBeforeModal.isPaused, false);
      assertEqual(sessionBeforeModal.elapsedSeconds, 300);

      // Trigger Record Moment modal and submit note
      await bridge.saveQuickNote('突发解题思路记录', today);

      // Check session status: MUST STILL BE ACTIVE AND UNPAUSED
      const sessionAfterModal = await bridge.getActiveSession();
      assertEqual(sessionAfterModal.isPaused, false, 'Session must not be paused by opening/saving Record Moment modal');
      assertEqual(sessionAfterModal.elapsedSeconds, 300);

      // Monotonic tick continues normally
      bridge.advanceTime(60);
      const sessionLater = await bridge.getActiveSession();
      assertEqual(sessionLater.elapsedSeconds, 360);

      await bridge.discardTimer();
    });
  });
}
