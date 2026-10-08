/**
 * Tier 4 — Real-World Workflows (Application Scenarios)
 * Simulates complete end-to-end user journeys mirroring actual daily usage.
 */

import { describe, it, beforeEach } from '../framework/harness.js';
import { assert, assertEqual } from '../framework/assertions.js';
import { MockYanjiBridge } from '../contracts/mock-bridge.js';

export function registerTier4Tests() {
  let bridge;

  beforeEach(() => {
    bridge = new MockYanjiBridge();
  });

  describe('Tier 4: Scenario 1 — 全天备考闭环 (Day in the Life of a Graduate Exam Candidate)', () => {
    it('executes full morning-to-night study cycle through all tabs and features', async () => {
      const today = '2026-10-08';

      // -------------------------------------------------------------
      // 1. Morning Routine (07:30): Check countdown & create tasks
      // -------------------------------------------------------------
      const countdown = await bridge.getExamCountdown();
      assert(countdown.daysRemaining > 0, 'Morning check: valid days remaining until exam');

      const morningStats = await bridge.getTodayStats(today);
      assertEqual(morningStats.totalFocusMinutes, 0, 'Morning check: 0 minutes studied');

      // Create 3 study tasks for the day
      const taskMath = await bridge.createTask(today, 'sub-math', '数学', '高等数学真题强化卷一', 60);
      const taskEng = await bridge.createTask(today, 'sub-eng', '英语', '英语二阅读真题2020 Text 1', 45);
      const taskCS = await bridge.createTask(today, 'sub-cs', '专业课', '数据结构树与二叉树算法', 50);

      const morningTasks = await bridge.getTodayTasks(today);
      assertEqual(morningTasks.length, 3, 'Today tasks list should have 3 tasks');

      // -------------------------------------------------------------
      // 2. Forenoon Study: 60-minute Math session + Record Moment
      // -------------------------------------------------------------
      await bridge.startFocus(taskMath.subjectId, taskMath.subjectName, 'COUNTDOWN', '上午数学专场', taskMath.id);

      // Halfway through (at 30 minutes), student gets an idea and records moment
      bridge.advanceTime(1800);
      const noteMath = await bridge.saveQuickNote('泰勒展开Peano余项在极限运算中的解题技巧', today);
      assert(noteMath.sessionId !== null, 'Note must capture running session ID');

      // Finish remaining 30 minutes
      bridge.advanceTime(1800);
      await bridge.completeTimer();

      // Math task actual minutes updated
      const tasksAfterMath = await bridge.getTodayTasks(today);
      const updatedMath = tasksAfterMath.find(t => t.id === taskMath.id);
      assertEqual(updatedMath.actualMinutes, 60, 'Math task should have 60 actual study minutes');

      // -------------------------------------------------------------
      // 3. Afternoon Study: 45-minute English session + Mark Completed
      // -------------------------------------------------------------
      await bridge.startFocus(taskEng.subjectId, taskEng.subjectName, 'COUNTDOWN', '下午英语真题', taskEng.id);
      bridge.advanceTime(2700); // 45m
      await bridge.completeTimer();

      // Student marks English task as completed
      await bridge.toggleTask(taskEng.id, true);

      // -------------------------------------------------------------
      // 4. Evening Review: Switch to Review tab and inspect stats
      // -------------------------------------------------------------
      // Today Stats check
      const eveningStats = await bridge.getTodayStats(today);
      assertEqual(eveningStats.totalFocusMinutes, 105, 'Total study time must be 105 minutes (60m + 45m)');
      assertEqual(eveningStats.completedTasksCount, 1, '1 task completed');
      assertEqual(eveningStats.notesCount, 1, '1 note recorded');

      // Daily Timeline check
      const timeline = await bridge.getDailyTimeline(today);
      assertEqual(timeline.sessions.length, 2, 'Timeline must show 2 sessions');
      assertEqual(timeline.completedTasks.length, 1, 'Timeline must show 1 completed task');
      assertEqual(timeline.completedTasks[0].title, '英语二阅读真题2020 Text 1');
      assertEqual(timeline.notes.length, 1, 'Timeline must show 1 note');

      // 7-Day Trend & Subject Breakdown check
      const reviewStats = await bridge.getReviewStats(7);
      assertEqual(reviewStats.totalFocusHours, 1.8, '105 minutes / 60 = 1.75 -> 1.8h');
      assertEqual(reviewStats.subjectDistribution['数学'], 60, 'Math study time 60m');
      assertEqual(reviewStats.subjectDistribution['英语'], 45, 'English study time 45m');

      // -------------------------------------------------------------
      // 5. Night Settings: Check preferences & return
      // -------------------------------------------------------------
      const settings = await bridge.getUserSettings();
      assertEqual(settings.examDate, '2026-12-26', 'Exam target date must round-trip');
      assert(
        !('focusDurationMinutes' in settings) && !('breakDurationMinutes' in settings),
        'UserSettings must not expose fabricated focus/break duration fields'
      );
    });
  });

  describe('Tier 4: Scenario 2 — 无任务快速开始专注 (Zero-Configuration Quick Start)', () => {
    it('executes spontaneous focus session without any prior task setup', async () => {
      const today = '2026-10-08';

      // 1. Fresh state with 0 tasks
      const tasks = await bridge.getTodayTasks(today);
      assertEqual(tasks.length, 0);

      // 2. Direct start via Single CTA
      await bridge.startFocus('sub-cs', '专业课', 'STOPWATCH', '自由专注');
      const active = await bridge.getActiveSession();
      assertEqual(active.mode, 'STOPWATCH');
      assertEqual(active.taskId, null);

      // 3. User studies for 25 minutes
      bridge.advanceTime(1500);
      await bridge.completeTimer();

      // 4. Verification in Today & Review
      const stats = await bridge.getTodayStats(today);
      assertEqual(stats.totalFocusMinutes, 25);

      const timeline = await bridge.getDailyTimeline(today);
      assertEqual(timeline.sessions.length, 1);
      assertEqual(timeline.sessions[0].subjectName, '专业课');
    });
  });

  describe('Tier 4: Scenario 3 — 异常中断与舍弃流 (Interrupted Session & Discard Flow)', () => {
    it('handles unexpected phone call or interruption by discarding without corrupting records', async () => {
      const today = '2026-10-08';

      // User starts session
      await bridge.startFocus('sub-pol', '政治', 'COUNTDOWN');
      bridge.advanceTime(180); // 3 minutes in

      // Discard session
      await bridge.discardTimer();

      // Active session cleared
      const active = await bridge.getActiveSession();
      assertEqual(active, null);

      // No phantom record created in Today stats
      const stats = await bridge.getTodayStats(today);
      assertEqual(stats.totalFocusSeconds, 0);
      assertEqual(stats.totalFocusMinutes, 0);

      // No phantom session in Review timeline
      const timeline = await bridge.getDailyTimeline(today);
      assertEqual(timeline.sessions.length, 0);
    });
  });
}
