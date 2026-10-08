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
      typeof obj.isFavorite === 'boolean'
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
      typeof obj.totalFocusHours === 'number'
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
    return (
      typeof obj.examDate === 'string' &&
      typeof obj.focusDurationMinutes === 'number' &&
      typeof obj.breakDurationMinutes === 'number' &&
      (obj.themePreference === 'SYSTEM' || obj.themePreference === 'LIGHT' || obj.themePreference === 'DARK')
    );
  },
};
