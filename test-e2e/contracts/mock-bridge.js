/**
 * Threshold for counting a day as "active" in the rolling review window.
 * Mirrors StudyStatisticsRepository.activeDayThresholdSeconds (30 min).
 */
const ACTIVE_DAY_THRESHOLD_MINUTES = 30;

/**
 * Yanji E2E Test Suite - Reference Bridge Oracle & In-Memory Driver
 * Implements authoritative Room DB & ActiveSessionCoordinator behavior
 * derived from PROJECT.md § Interface Contracts.
 * Serves as the independent test harness driver and oracle.
 */

export class MockYanjiBridge {
  constructor(initialData = {}) {
    this.reset(initialData);
  }

  reset(initialData = {}) {
    this.activeSession = null;
    this.timerListeners = {
      tick: [],
      stateChanged: [],
    };
    this.dataListeners = [];
    this.themeListeners = [];

    // In-Memory Storage mirroring Room Database tables
    this.tasks = initialData.tasks ? [...initialData.tasks] : [];
    this.notes = initialData.notes ? [...initialData.notes] : [];
    this.sessionRecords = initialData.sessionRecords ? [...initialData.sessionRecords] : [];
    this.subjects = initialData.subjects ? [...initialData.subjects] : [
      { id: 'sub-math', name: '数学', color: '#48CAE4' },
      { id: 'sub-eng', name: '英语', color: '#52B788' },
      { id: 'sub-pol', name: '政治', color: '#E07A5F' },
      { id: 'sub-cs', name: '专业课', color: '#9B5DE5' },
    ];
    // Mirrors the domain UserSettings (Models.kt). There is deliberately no
    // focusDurationMinutes / breakDurationMinutes: those fields do not exist in
    // the domain model, so any value would be fabricated (§三.3).
    this.userSettings = initialData.userSettings ? { ...initialData.userSettings } : {
      examDate: '2026-12-26',
      targetSchool: '',
      targetMajor: '',
      themePreference: 'SYSTEM',
    };
    this.themeState = {
      mode: 'SYSTEM',
      isDark: true, // defaults to dark (Midnight Blue) for quiet focus
    };

    // Simulated Monotonic Clock (deterministic test anchor date)
    this.virtualClockMs = initialData.virtualClockMs || new Date('2026-10-08T12:00:00Z').getTime();
  }

  // --- Monotonic Physical Clock Helper ---
  advanceTime(seconds) {
    this.virtualClockMs += seconds * 1000;
    if (this.activeSession && !this.activeSession.isPaused) {
      this.activeSession.elapsedSeconds += seconds;
      if (this.activeSession.mode === 'COUNTDOWN') {
        this.activeSession.remainingSeconds = Math.max(0, this.activeSession.remainingSeconds - seconds);
      }
      this._emitTimerTick();
    }
  }

  // --- YanjiTimerModule Interface ---
  /**
   * Mirrors YanjiTimerModule.startFocus(subjectId, subjectName, mode, note,
   * taskId, plannedMinutes). `plannedMinutes` is the COUNTDOWN target; the
   * native side encodes it into the FocusModes name and derives seconds from
   * it, so the oracle must do the same rather than reading a settings default.
   */
  async startFocus(subjectId, subjectName, mode = 'COUNTDOWN', note = '', taskId = null, plannedMinutes = 0) {
    if (this.activeSession) {
      throw new Error('A focus session is already active');
    }
    const normalizedMode = String(mode).toUpperCase();
    if (normalizedMode !== 'COUNTDOWN' && normalizedMode !== 'STOPWATCH') {
      throw new Error(`Unknown timer mode '${mode}'`);
    }
    const isCountdown = normalizedMode === 'COUNTDOWN';
    const plannedSeconds = isCountdown ? Math.max(1, Math.floor(plannedMinutes)) * 60 : 0;
    this.activeSession = {
      sessionId: `session_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`,
      subjectId,
      subjectName,
      mode: normalizedMode,
      startTime: this.virtualClockMs,
      elapsedSeconds: 0,
      remainingSeconds: plannedSeconds,
      targetDurationSeconds: plannedSeconds,
      phase: 'FOCUS',
      isPaused: false,
      isCountdown,
      note,
      taskId: taskId || null,
    };
    this._emitTimerStateChanged('STARTED');
    this._emitTimerTick();
    return true;
  }

  async pauseTimer() {
    if (!this.activeSession) return false;
    this.activeSession.isPaused = true;
    this._emitTimerStateChanged('PAUSED');
    return true;
  }

  async resumeTimer() {
    if (!this.activeSession) return false;
    this.activeSession.isPaused = false;
    this._emitTimerStateChanged('RESUMED');
    return true;
  }

  async completeTimer() {
    if (!this.activeSession) return false;
    const session = this.activeSession;
    const durationMinutes = Math.floor(session.elapsedSeconds / 60);

    // Save session record to Room history
    this.sessionRecords.push({
      id: session.sessionId,
      subjectId: session.subjectId,
      subjectName: session.subjectName,
      mode: session.mode,
      startTime: session.startTime,
      durationMinutes,
      elapsedSeconds: session.elapsedSeconds,
      taskId: session.taskId,
      date: new Date(session.startTime).toISOString().split('T')[0],
    });

    // Update bound task actual study time if linked
    if (session.taskId) {
      const task = this.tasks.find(t => t.id === session.taskId);
      if (task) {
        task.actualMinutes += durationMinutes;
      }
    }

    this.activeSession = null;
    this._emitTimerStateChanged('COMPLETED');
    this._emitDataChanged('sessions');
    return true;
  }

  async discardTimer() {
    if (!this.activeSession) return false;
    this.activeSession = null;
    this._emitTimerStateChanged('DISCARDED');
    return true;
  }

  async getActiveSession() {
    return this.activeSession ? { ...this.activeSession } : null;
  }

  onTimerTick(callback) {
    this.timerListeners.tick.push(callback);
    return () => {
      this.timerListeners.tick = this.timerListeners.tick.filter(cb => cb !== callback);
    };
  }

  onTimerStateChanged(callback) {
    this.timerListeners.stateChanged.push(callback);
    return () => {
      this.timerListeners.stateChanged = this.timerListeners.stateChanged.filter(cb => cb !== callback);
    };
  }

  _emitTimerTick() {
    if (!this.activeSession) return;
    const event = {
      elapsedSeconds: this.activeSession.elapsedSeconds,
      remainingSeconds: this.activeSession.remainingSeconds,
      phase: this.activeSession.phase,
      isPaused: this.activeSession.isPaused,
    };
    for (const cb of this.timerListeners.tick) {
      cb(event);
    }
  }

  _emitTimerStateChanged(state) {
    const event = {
      state,
      session: this.activeSession ? { ...this.activeSession } : null,
    };
    for (const cb of this.timerListeners.stateChanged) {
      cb(event);
    }
  }

  // --- YanjiDataModule Interface ---
  async getTodayStats(date) {
    const todaySessions = this.sessionRecords.filter(s => s.date === date);
    const totalFocusSeconds = todaySessions.reduce((acc, s) => acc + s.elapsedSeconds, 0);
    const totalFocusMinutes = Math.floor(totalFocusSeconds / 60);

    const todayTasks = this.tasks.filter(t => t.date === date);
    const completedTasksCount = todayTasks.filter(t => t.completed).length;

    const todayNotes = this.notes.filter(n => n.date === date);

    return {
      date,
      totalFocusSeconds,
      totalFocusMinutes,
      completedTasksCount,
      totalTasksCount: todayTasks.length,
      notesCount: todayNotes.length,
    };
  }

  async getTodayTasks(date) {
    return this.tasks.filter(t => t.date === date).map(t => ({ ...t }));
  }

  async createTask(date, subjectId, subjectName, title, plannedMinutes) {
    if (!title || title.trim().length === 0) {
      throw new Error('Task title cannot be empty');
    }
    const newTask = {
      id: `task_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`,
      date,
      subjectId,
      subjectName,
      title: title.trim(),
      plannedMinutes: Math.max(1, plannedMinutes),
      actualMinutes: 0,
      completed: false,
      createdAt: this.virtualClockMs,
    };
    this.tasks.push(newTask);
    this._emitDataChanged('tasks');
    return { ...newTask };
  }

  async toggleTask(taskId, completed) {
    const task = this.tasks.find(t => t.id === taskId);
    if (!task) return false;
    task.completed = completed;
    this._emitDataChanged('tasks');
    return true;
  }

  async deleteTask(taskId) {
    const index = this.tasks.findIndex(t => t.id === taskId);
    if (index === -1) return false;
    this.tasks.splice(index, 1);
    this._emitDataChanged('tasks');
    return true;
  }

  async saveQuickNote(content, date, sessionId = null) {
    if (!content || content.trim().length === 0) {
      throw new Error('Note content cannot be empty');
    }
    const newNote = {
      id: `note_${Date.now()}_${Math.random().toString(36).substring(2, 7)}`,
      date,
      timestamp: this.virtualClockMs,
      content: content.trim(),
      sessionId: sessionId || (this.activeSession ? this.activeSession.sessionId : null),
      isFavorite: false,
    };
    this.notes.push(newNote);
    this._emitDataChanged('notes');
    return { ...newNote };
  }

  async getNotes(page = 1, limit = 20) {
    const start = (page - 1) * limit;
    return this.notes
      .slice()
      .sort((a, b) => b.timestamp - a.timestamp)
      .slice(start, start + limit)
      .map(n => ({ ...n }));
  }

  async getNotesForDate(date) {
    return this.notes.filter(n => n.date === date).map(n => ({ ...n }));
  }

  async toggleFavoriteNote(noteId) {
    const note = this.notes.find(n => n.id === noteId);
    if (!note) return false;
    note.isFavorite = !note.isFavorite;
    this._emitDataChanged('notes');
    return true;
  }

  async deleteNote(noteId) {
    const idx = this.notes.findIndex(n => n.id === noteId);
    if (idx === -1) return false;
    this.notes.splice(idx, 1);
    this._emitDataChanged('notes');
    return true;
  }

  async getSubjects() {
    return this.subjects.map(s => ({ ...s }));
  }

  /**
   * Rolling N-day window (mirrors YanjiTime.lastDaysRange(days)).
   *
   * Semantics that the native side must honour and this reference oracle enforces:
   *  - days is honoured: exactly N keys, from (today - (N-1)) through today.
   *  - Keys are local-calendar yyyy-MM-dd, derived by shifting the local date,
   *    never by subtracting 24h multiples (that drifts across DST).
   *  - No future-dated keys: tomorrow can never appear, even with a zero value.
   *  - No calendar-week clipping: the window is not aligned to Monday.
   *  - Sessions outside the window contribute to nothing.
   *  - Zero days with data is reported as 0, never faked (activeDays / average).
   */
  async getReviewStats(days = 7) {
    const windowDays = Math.max(1, Math.floor(days));
    const dailyFocusMinutes = {};
    const subjectDistribution = {};
    let totalMinutes = 0;
    let activeDays = 0;

    const windowDates = [];
    for (let i = windowDays - 1; i >= 0; i--) {
      const d = this._isoDateAt(this.virtualClockMs, -i);
      windowDates.push(d);
      dailyFocusMinutes[d] = 0;
    }

    for (const session of this.sessionRecords) {
      if (Object.prototype.hasOwnProperty.call(dailyFocusMinutes, session.date)) {
        dailyFocusMinutes[session.date] += session.durationMinutes;
        subjectDistribution[session.subjectName] =
          (subjectDistribution[session.subjectName] || 0) + session.durationMinutes;
        totalMinutes += session.durationMinutes;
      }
    }

    // An "active day" is a day with at least 30 minutes of recorded study,
    // matching StudyStatisticsRepository.activeDayThresholdSeconds.
    for (const d of windowDates) {
      if (dailyFocusMinutes[d] >= ACTIVE_DAY_THRESHOLD_MINUTES) activeDays++;
    }

    return {
      days: windowDays,
      dailyFocusMinutes,
      subjectDistribution,
      totalFocusHours: parseFloat((totalMinutes / 60).toFixed(1)),
      dailyAverageMinutes: Math.round(totalMinutes / windowDates.length),
      activeDays,
    };
  }

  /** Local-calendar yyyy-MM-dd shifted by deltaDays (never UTC-shifted). */
  _isoDateAt(epochMs, deltaDays) {
    const d = new Date(epochMs);
    d.setDate(d.getDate() + deltaDays);
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  async getDailyTimeline(date) {
    return {
      date,
      sessions: this.sessionRecords.filter(s => s.date === date).map(s => ({ ...s })),
      completedTasks: this.tasks.filter(t => t.date === date && t.completed).map(t => ({ ...t })),
      notes: this.notes.filter(n => n.date === date).map(n => ({ ...n })),
    };
  }

  async getUserSettings() {
    return { ...this.userSettings };
  }

  async updateUserSettings(settings) {
    this.userSettings = {
      ...this.userSettings,
      ...settings,
    };
    this._emitDataChanged('settings');
    return true;
  }

  async getExamCountdown() {
    const now = new Date(this.virtualClockMs);
    const exam = new Date(this.userSettings.examDate);
    const diffTime = exam.getTime() - now.getTime();
    const daysRemaining = Math.max(0, Math.ceil(diffTime / (1000 * 60 * 60 * 24)));
    return {
      examDate: this.userSettings.examDate,
      daysRemaining,
    };
  }

  onDataChanged(callback) {
    this.dataListeners.push(callback);
    return () => {
      this.dataListeners = this.dataListeners.filter(cb => cb !== callback);
    };
  }

  _emitDataChanged(type) {
    for (const cb of this.dataListeners) {
      cb({ type });
    }
  }

  // --- YanjiThemeModule Interface ---
  async getThemePreference() {
    return {
      mode: this.themeState.mode,
      isDark: this.themeState.isDark,
    };
  }

  async setThemePreference(mode) {
    if (!['SYSTEM', 'LIGHT', 'DARK'].includes(mode)) {
      throw new Error(`Invalid theme mode: ${mode}`);
    }
    this.themeState.mode = mode;
    this.themeState.isDark = mode === 'DARK' || (mode === 'SYSTEM' && true);
    for (const cb of this.themeListeners) {
      cb({ mode: this.themeState.mode, isDark: this.themeState.isDark });
    }
    return true;
  }

  onThemeChanged(callback) {
    this.themeListeners.push(callback);
    return () => {
      this.themeListeners = this.themeListeners.filter(cb => cb !== callback);
    };
  }
}
