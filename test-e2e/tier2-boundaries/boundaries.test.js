/**
 * Tier 2 — Boundary & Corner Cases
 * Tests empty datasets, zero values, extreme thresholds, date boundaries, and stress inputs.
 */

import { describe, it, beforeEach } from '../framework/harness.js';
import { assert, assertEqual, assertRejects } from '../framework/assertions.js';
import { MockYanjiBridge } from '../contracts/mock-bridge.js';

export function registerTier2Tests() {
  let bridge;

  beforeEach(() => {
    bridge = new MockYanjiBridge();
  });

  describe('Tier 2: Empty Data & Zero State Boundaries', () => {
    it('verifies day 1 first launch with 0 tasks, 0 notes, and 0 focus time returns valid zero states without NaN', async () => {
      const today = '2026-10-08';
      const stats = await bridge.getTodayStats(today);

      assertEqual(stats.totalFocusSeconds, 0);
      assertEqual(stats.totalFocusMinutes, 0);
      assertEqual(stats.completedTasksCount, 0);
      assertEqual(stats.totalTasksCount, 0);
      assertEqual(stats.notesCount, 0);

      const tasks = await bridge.getTodayTasks(today);
      assertEqual(tasks.length, 0);

      const timeline = await bridge.getDailyTimeline(today);
      assertEqual(timeline.sessions.length, 0);
      assertEqual(timeline.completedTasks.length, 0);
      assertEqual(timeline.notes.length, 0);

      const overview = await bridge.getReviewOverview('ROLLING_7', 0);
      assertEqual(overview.totalSeconds, 0);
      assert(!Number.isNaN(overview.totalSeconds), 'totalSeconds must never be NaN');
      assertEqual(overview.dailyAverageSeconds, 0, 'A zero window must average 0, not NaN');
      assertEqual(overview.days.length, 7, 'The zero state must still carry the full window');
    });
  });

  describe('Tier 2: Timer Threshold & Lifecycle Boundaries', () => {
    it('verifies countdown timer reaching 00:00:00 clamps remainingSeconds to 0 and does not underflow', async () => {
      await bridge.startFocus('sub-math', '数学', 'COUNTDOWN', '', null, 25);
      const plannedSeconds = 25 * 60;

      // Advance by more than planned time
      bridge.advanceTime(plannedSeconds + 600);
      const active = await bridge.getActiveSession();

      assertEqual(active.remainingSeconds, 0, 'Remaining seconds must clamp to 0 and never go negative');
      assert(active.elapsedSeconds >= plannedSeconds, 'Elapsed seconds must continue advancing');

      await bridge.discardTimer();
    });

    it('verifies repeated pause-resume cycles maintain accurate elapsed time', async () => {
      await bridge.startFocus('sub-eng', '英语', 'STOPWATCH');

      // 5 rapid pause-resume cycles
      for (let i = 0; i < 5; i++) {
        bridge.advanceTime(10);
        await bridge.pauseTimer();
        bridge.advanceTime(10); // time advanced while paused should not count toward session if stopped
        await bridge.resumeTimer();
      }

      const active = await bridge.getActiveSession();
      assertEqual(active.isPaused, false);
      assertEqual(active.elapsedSeconds, 50, 'Elapsed time should only accumulate active ticks');

      await bridge.discardTimer();
    });
  });

  describe('Tier 2: Date & Countdown Boundaries', () => {
    it('verifies exam date equal to today yields exactly 0 days remaining', async () => {
      const todayDate = new Date(bridge.virtualClockMs).toISOString().split('T')[0];
      await bridge.updateUserSettings({ examDate: todayDate });

      const countdown = await bridge.getExamCountdown();
      assertEqual(countdown.daysRemaining, 0, 'Exam today must result in 0 days remaining');
    });

    it('verifies exam date in the past clamps to 0 days remaining rather than negative values', async () => {
      await bridge.updateUserSettings({ examDate: '2020-01-01' });

      const countdown = await bridge.getExamCountdown();
      assertEqual(countdown.daysRemaining, 0, 'Past exam date must clamp to 0 days');
    });

    it('verifies leap year date (Feb 29) handling', async () => {
      await bridge.updateUserSettings({ examDate: '2028-02-29' });
      const countdown = await bridge.getExamCountdown();
      assert(countdown.daysRemaining > 0, 'Leap year exam date must calculate positive days remaining');
    });
  });

  describe('Tier 2: Text Length & Special Character Boundaries', () => {
    it('verifies empty or whitespace-only note submission is strictly rejected', async () => {
      await assertRejects(async () => {
        await bridge.saveQuickNote('', '2026-10-08');
      }, /Note content cannot be empty/);

      await assertRejects(async () => {
        await bridge.saveQuickNote('   \n\t  ', '2026-10-08');
      }, /Note content cannot be empty/);
    });

    it('verifies long notes (10,000+ characters) and special unicode symbols are preserved with fidelity', async () => {
      const longContent = '考研复习知识点总结：' + 'A'.repeat(10000) + ' 🚀🎉💯🔥【重点】';
      const note = await bridge.saveQuickNote(longContent, '2026-10-08');

      assertEqual(note.content, longContent);
      const retrieved = await bridge.getNotes(1, 10);
      assertEqual(retrieved[0].content, longContent);
    });
  });

  describe('Tier 2: Theme Switching Boundaries', () => {
    it('verifies rapid cycling through SYSTEM -> LIGHT -> DARK -> SYSTEM maintains state integrity', async () => {
      await bridge.setThemePreference('LIGHT');
      let pref = await bridge.getThemePreference();
      assertEqual(pref.mode, 'LIGHT');
      assertEqual(pref.isDark, false);

      await bridge.setThemePreference('DARK');
      pref = await bridge.getThemePreference();
      assertEqual(pref.mode, 'DARK');
      assertEqual(pref.isDark, true);

      await bridge.setThemePreference('SYSTEM');
      pref = await bridge.getThemePreference();
      assertEqual(pref.mode, 'SYSTEM');
    });
  });
}
