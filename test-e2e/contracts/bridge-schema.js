/**
 * Yanji E2E Test Suite - Bridge Interface Schema & Validation
 * Defines and verifies interface contracts between Native Kotlin Core and React Native TypeScript.
 * Derived strictly from the shipped Kotlin ↔ JS contract in src/bridge/index.ts
 * and the Kotlin modules under app/src/main/java/com/example/yanji/bridge/**.
 */

/** 回顾页时间粒度白名单：与 TS `ReviewScope` / Kotlin `BridgeMappers.REVIEW_SCOPES` 逐字一致。 */
const REVIEW_SCOPES = ['TODAY', 'CALENDAR_WEEK', 'CALENDAR_MONTH'];

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
      typeof obj.subjectColor === 'string' &&
      typeof obj.title === 'string' &&
      typeof obj.plannedMinutes === 'number' &&
      typeof obj.actualMinutes === 'number' &&
      typeof obj.completed === 'boolean' &&
      (obj.carriedOverToId === null ||
        (typeof obj.carriedOverToId === 'string' && obj.carriedOverToId.length > 0)) &&
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
      // Contract (src/bridge/index.ts): sessionId is a nullable string.
      // null means the note is not bound to a focus session. Empty string is NOT a
      // valid representation of "unbound" - it must be null.
      (obj.sessionId === null || (typeof obj.sessionId === 'string' && obj.sessionId.length > 0)) &&
      (obj.category === 'INSPIRATION' || obj.category === 'DOUBT') &&
      typeof obj.isResolved === 'boolean' &&
      (obj.resolvedAt === null || typeof obj.resolvedAt === 'number')
    );
  },

  isValidReviewOverview(obj) {
    if (!obj || typeof obj !== 'object') return false;
    if (!REVIEW_SCOPES.includes(obj.scope)) return false;
    if (typeof obj.periodsBack !== 'number') return false;
    if (typeof obj.label !== 'string' || obj.label.length === 0) return false;
    if (typeof obj.windowDays !== 'number') return false;
    if (!Array.isArray(obj.days)) return false;
    // windowDays 是 days 的长度，不是另一个独立计算的数字。
    if (obj.windowDays !== obj.days.length) return false;
    if (typeof obj.totalSeconds !== 'number') return false;
    if (typeof obj.dailyAverageSeconds !== 'number') return false;
    if (typeof obj.examCount !== 'number') return false;
    if (!Array.isArray(obj.subjectDistribution)) return false;

    for (const day of obj.days) {
      if (!day || typeof day !== 'object') return false;
      if (typeof day.date !== 'string' || typeof day.dayLabel !== 'string') return false;
      if (typeof day.durationSeconds !== 'number') return false;
      if (typeof day.isToday !== 'boolean' || typeof day.isFuture !== 'boolean') return false;
    }

    for (const slice of obj.subjectDistribution) {
      if (!slice || typeof slice !== 'object') return false;
      if (typeof slice.subjectId !== 'string') return false;
      if (typeof slice.subjectName !== 'string') return false;
      if (typeof slice.subjectColor !== 'string') return false;
      if (typeof slice.minutes !== 'number') return false;
      if (typeof slice.share !== 'number') return false;
      if (!slice.dailyMinutes || typeof slice.dailyMinutes !== 'object') return false;
    }

    return true;
  },

  isValidDailySession(obj) {
    if (!obj || typeof obj !== 'object') return false;
    return (
      typeof obj.id === 'string' &&
      typeof obj.subjectId === 'string' &&
      typeof obj.subjectName === 'string' &&
      typeof obj.subjectColor === 'string' &&
      Number.isFinite(obj.startTime) &&
      Number.isFinite(obj.endTime) &&
      Number.isFinite(obj.durationSeconds) && obj.durationSeconds >= 0 &&
      typeof obj.isExam === 'boolean' &&
      typeof obj.note === 'string' &&
      Number.isInteger(obj.pauseCount) &&
      typeof obj.mode === 'string' &&
      // Same trap as NoteEntry.sessionId: null means "not bound to a plan".
      // Empty string is NOT a valid representation of unbound.
      (obj.taskId === null || (typeof obj.taskId === 'string' && obj.taskId.length > 0))
    );
  },

  isValidDailySnapshot(obj) {
    if (!obj || typeof obj !== 'object') return false;
    return (
      typeof obj.date === 'string' &&
      typeof obj.formattedDate === 'string' &&
      Number.isFinite(obj.totalDurationSeconds) && obj.totalDurationSeconds >= 0 &&
      Number.isInteger(obj.focusCount) && obj.focusCount >= 0 &&
      Number.isInteger(obj.focusParticipationCount) && obj.focusParticipationCount >= 0 &&
      Number.isInteger(obj.examCount) && obj.examCount >= 0 &&
      Array.isArray(obj.sessions) && obj.sessions.every(s => this.isValidDailySession(s)) &&
      Array.isArray(obj.tasks) && obj.tasks.every(task => this.isValidStudyTask(task)) &&
      Array.isArray(obj.notes) && obj.notes.every(note => this.isValidNoteEntry(note)) &&
      !('completedTasks' in obj) && !('totalFocusSeconds' in obj)
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
