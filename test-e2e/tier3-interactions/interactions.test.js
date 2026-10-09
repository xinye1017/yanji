/**
 * Tier 3 — Cross-Feature Interactions
 * Tests state synchronizations, concurrent lifecycles, and cross-module interactions.
 */

import { describe, it, beforeEach } from '../framework/harness.js';
import { assert, assertEqual } from '../framework/assertions.js';
import { MockYanjiBridge } from '../contracts/mock-bridge.js';

export function registerTier3Tests() {
  let bridge;

  beforeEach(() => {
    bridge = new MockYanjiBridge();
  });

  describe('Tier 3: Focus Timer + Record Moment + Today Stats + Room Sync', () => {
    it('verifies seamless interaction between active timer, multiple moment recordings, and stats update', async () => {
      const today = '2026-10-08';

      // 1. Create a study task on Today screen
      const task = await bridge.createTask(today, 'sub-math', '数学', '线性代数二次型标准化', 60);
      assertEqual(task.actualMinutes, 0);

      // 2. Start Focus session bound to this task
      await bridge.startFocus(task.subjectId, task.subjectName, 'COUNTDOWN', '专题复习', task.id);
      const active1 = await bridge.getActiveSession();
      assertEqual(active1.taskId, task.id);

      // 3. 15 minutes elapsed -> Record first moment
      bridge.advanceTime(900);
      const note1 = await bridge.saveQuickNote('正交变换法化二次型为标准型的计算步骤', today);
      assertEqual(note1.sessionId, active1.sessionId, 'First note must link to active session');

      // Check session is uninterrupted
      const active2 = await bridge.getActiveSession();
      assertEqual(active2.isPaused, false, 'Timer must NOT pause on saving moment');
      assertEqual(active2.elapsedSeconds, 900);

      // 4. Another 20 minutes elapsed -> Record second moment
      bridge.advanceTime(1200);
      const note2 = await bridge.saveQuickNote('配方法处理缺失交叉项的退化情况', today);
      assertEqual(note2.sessionId, active1.sessionId, 'Second note must link to active session');

      // Check elapsed is 35m total
      const active3 = await bridge.getActiveSession();
      assertEqual(active3.elapsedSeconds, 2100);

      // 5. Complete session
      await bridge.completeTimer();

      // 6. Verify Room sync:
      // - Task actual study minutes updated by 35 minutes
      const tasks = await bridge.getTodayTasks(today);
      assertEqual(tasks[0].actualMinutes, 35, 'Task actualMinutes must reflect completed session duration');

      // - Today stats reflect 35m total focus and 2 notes
      const stats = await bridge.getTodayStats(today);
      assertEqual(stats.totalFocusMinutes, 35);
      assertEqual(stats.notesCount, 2);

      // - Review timeline reflects 1 session and 2 linked notes
      const timeline = await bridge.getDailyTimeline(today);
      assertEqual(timeline.sessions.length, 1);
      assertEqual(timeline.sessions[0].durationMinutes, 35);
      assertEqual(timeline.notes.length, 2);
    });
  });

  describe('Tier 3: Navigation Tab Switching with Active Session Continuity', () => {
    it('verifies active focus session state survives tab switching and updates Today CTA', async () => {
      // 1. User starts session on Focus tab
      await bridge.startFocus('sub-eng', '英语', 'STOPWATCH');
      bridge.advanceTime(600); // 10 minutes

      // 2. User switches to Today tab
      // Today Screen reads active session from bridge
      const activeOnToday = await bridge.getActiveSession();
      assert(activeOnToday !== null, 'Active session must be present when Today screen mounts');
      assertEqual(activeOnToday.subjectName, '英语');
      assertEqual(activeOnToday.elapsedSeconds, 600);

      // Single CTA adapts to active session
      const cta = activeOnToday
        ? { label: '继续专注', action: 'NAVIGATE_TO_FOCUS' }
        : { label: '开始专注', action: 'START_FREE' };
      assertEqual(cta.label, '继续专注');

      // 3. User switches to Review tab
      const reviewOverview = await bridge.getReviewOverview('CALENDAR_WEEK', 0);
      // The active session is not a completed record yet, so it must not leak into
      // the review totals — the Review tab shows 0 until completion.
      assertEqual(reviewOverview.totalSeconds, 0, 'An in-flight session must not count as study time');
      assertEqual(reviewOverview.windowDays, 7);
      assertEqual(reviewOverview.days.filter(d => d.isToday).length, 1);

      // 4. User navigates back to Focus tab
      bridge.advanceTime(300); // 5 more minutes pass
      const activeBackOnFocus = await bridge.getActiveSession();
      assertEqual(activeBackOnFocus.elapsedSeconds, 900, 'Timer must reflect monotonic physical time');
      assertEqual(activeBackOnFocus.isPaused, false);

      await bridge.discardTimer();
    });
  });

  describe('Tier 3: Task Completion -> Stats & Review Timeline Synchronization', () => {
    it('verifies toggling task completion dynamically updates Today stats and Review timeline', async () => {
      const today = '2026-10-08';
      const task = await bridge.createTask(today, 'sub-pol', '政治', '肖秀荣1000题马原单选', 40);

      // Initial stats
      let stats = await bridge.getTodayStats(today);
      assertEqual(stats.completedTasksCount, 0);

      let timeline = await bridge.getDailyTimeline(today);
      assertEqual(timeline.completedTasks.length, 0);

      // Mark task as completed
      await bridge.toggleTask(task.id, true);

      // Updated stats
      stats = await bridge.getTodayStats(today);
      assertEqual(stats.completedTasksCount, 1, 'completedTasksCount must increment to 1');

      timeline = await bridge.getDailyTimeline(today);
      assertEqual(timeline.completedTasks.length, 1, 'timeline completedTasks must contain 1 item');
      assertEqual(timeline.completedTasks[0].id, task.id);

      // Toggle back to uncompleted
      await bridge.toggleTask(task.id, false);
      stats = await bridge.getTodayStats(today);
      assertEqual(stats.completedTasksCount, 0);
      timeline = await bridge.getDailyTimeline(today);
      assertEqual(timeline.completedTasks.length, 0);
    });
  });

  describe('Tier 3: Theme Preference Change with Session Continuity', () => {
    it('verifies theme switching does NOT restart or disrupt active timer session', async () => {
      await bridge.startFocus('sub-cs', '专业课', 'COUNTDOWN');
      bridge.advanceTime(120);

      // Switch theme to LIGHT
      await bridge.setThemePreference('LIGHT');
      const pref = await bridge.getThemePreference();
      assertEqual(pref.mode, 'LIGHT');

      // Active session must remain intact and monotonic
      const session = await bridge.getActiveSession();
      assert(session !== null, 'Session must not be lost upon theme change');
      assertEqual(session.elapsedSeconds, 120);
      assertEqual(session.isPaused, false);

      await bridge.discardTimer();
    });
  });
}
