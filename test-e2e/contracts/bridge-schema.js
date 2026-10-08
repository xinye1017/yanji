/**
 * Yanji E2E Test Suite - Bridge Interface Schema & Validation
 * Defines and verifies interface contracts between Native Kotlin Core and React Native TypeScript.
 * Derived strictly from PROJECT.md § Interface Contracts.
 */

export const BridgeSchemas = {
  isValidActiveSessionState(obj) {
    if (!obj || typeof obj !== 'object') return false;
    const hasRequiredFields =
      typeof obj.sessionId === 'string' &&
      typeof obj.subjectId === 'string' &&
      typeof obj.subjectName === 'string' &&
      (obj.mode === 'COUNTDOWN' || obj.mode === 'STOPWATCH') &&
      typeof obj.startTime === 'number' &&
      typeof obj.elapsedSeconds === 'number' &&
      typeof obj.remainingSeconds === 'number' &&
      (obj.phase === 'FOCUS' || obj.phase === 'BREAK' || obj.phase === 'IDLE') &&
      typeof obj.isPaused === 'boolean';
    return hasRequiredFields;
  },

  isValidStudyTask(obj) {
    if (!obj || typeof obj !== 'object') return false;
    return (
      typeof obj.id === 'string' &&
      typeof obj.date === 'string' &&
      typeof obj.subjectId === 'string' &&
      typeof obj.subjectName === 'string' &&
      typeof obj.title === 'string' &&
      typeof obj.plannedMinutes === 'number' &&
      typeof obj.actualMinutes === 'number' &&
      typeof obj.completed === 'boolean' &&
      typeof obj.createdAt === 'number'
    );
  },

  isValidNoteEntry(obj) {
    if (!obj || typeof obj !== 'object') return false;
    return (
      typeof obj.id === 'string' &&
      typeof obj.date === 'string' &&
      typeof obj.timestamp === 'number' &&
      typeof obj.content === 'string' &&
      typeof obj.isFavorite === 'boolean' &&
      // Contract (BATCH_FIX_PLAN / PROJECT.md): sessionId is a nullable string.
      // null means the note is not bound to a focus session. Empty string is NOT a
      // valid representation of "unbound" - it must be null.
      (obj.sessionId === null || (typeof obj.sessionId === 'string' && obj.sessionId.length > 0))
    );
  },

  isValidTodayStats(obj) {
    if (!obj || typeof obj !== 'object') return false;
    return (
      typeof obj.date === 'string' &&
      typeof obj.totalFocusSeconds === 'number' &&
      typeof obj.totalFocusMinutes === 'number' &&
      typeof obj.completedTasksCount === 'number' &&
      typeof obj.totalTasksCount === 'number' &&
      typeof obj.notesCount === 'number'
    );
  },

  isValidReviewStats(obj) {
    if (!obj || typeof obj !== 'object') return false;
    return (
      typeof obj.days === 'number' &&
      typeof obj.dailyFocusMinutes === 'object' &&
      typeof obj.subjectDistribution === 'object' &&
      typeof obj.totalFocusHours === 'number' &&
      typeof obj.dailyAverageMinutes === 'number' &&
      typeof obj.activeDays === 'number'
    );
  },

  isValidDailyTimeline(obj) {
    if (!obj || typeof obj !== 'object') return false;
    return (
      typeof obj.date === 'string' &&
      Array.isArray(obj.sessions) &&
      Array.isArray(obj.completedTasks) &&
      Array.isArray(obj.notes)
    );
  },

  isValidUserSettings(obj) {
    if (!obj || typeof obj !== 'object') return false;
    // focusDurationMinutes / breakDurationMinutes were removed: the domain
    // UserSettings has no such fields, so any value would be fabricated.
    // Reject payloads that still carry them rather than silently tolerating drift.
    return (
      typeof obj.examDate === 'string' &&
      typeof obj.targetSchool === 'string' &&
      typeof obj.targetMajor === 'string' &&
      !('focusDurationMinutes' in obj) &&
      !('breakDurationMinutes' in obj) &&
      (obj.themePreference === 'SYSTEM' || obj.themePreference === 'LIGHT' || obj.themePreference === 'DARK')
    );
  },
};
