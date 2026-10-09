/** 回顾页的时间粒度白名单：只有这四个值（与 TS `ReviewScope` 逐字一致）。 */
const REVIEW_SCOPES = ['ROLLING_7', 'ROLLING_30', 'CALENDAR_WEEK', 'CALENDAR_MONTH'];

const WEEKDAY_LABELS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];

/** 科目查表未命中时的中性灰（与 Kotlin `SubjectCatalog.DEFAULT_FALLBACK_COLOR` 同值）。 */
const NEUTRAL_SUBJECT_COLOR = '#667085';

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
      // 本地日历键：startTime 由虚拟时钟给出，绝不走 toISOString()（那是 UTC）。
      date: this._isoDateAt(session.startTime, 0),
      // 模考记录独立于专注记录；随笔初始为空串（缺字段会在读取时退化成 undefined）。
      isExam: false,
      note: session.note || '',
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
   * 回顾页趋势概览：`scope` + `periodsBack` 双参数（见 TS `ReviewOverview`）。
   *
   * 三条不可让步的口径（mock 是契约 oracle，不是 Kotlin 分类学的复刻）：
   *  1. **分钟只截断一次**：全程按 `elapsedSeconds` 累加，最后才 `seconds / 60`。
   *     逐行先截断再相加会让「每天 29 分 59 秒」的窗口凭空少掉几分钟。
   *  2. **日期键走本地日历**：全部经 `_isoDateAt` / `_shiftLocalDate` 推进，
   *     绝不用 `toISOString()`（那是 UTC，会在东八区的凌晨把「今天」标成昨天）。
   *  3. **`subjectDistribution` 排序确定**：分钟降序，同分按 `subjectId` 字典序升序，
   *     绝不依赖 map 插入顺序（同一份数据两次调用必须给出同一份数组）。
   *
   * 科目归一刻意**不**实现：mock 按记录自身的 `subjectId` / `subjectName` 分桶，
   * 它要锁的是「排序 / 截断 / 窗口」三条口径，不是 Kotlin `SubjectCatalog` 的分类学。
   */
  async getReviewOverview(scope = 'ROLLING_7', periodsBack = 0, anchorDate = null) {
    if (!REVIEW_SCOPES.includes(scope)) {
      throw new Error(`Invalid review scope: ${scope}`);
    }
    const back = Number.isFinite(periodsBack) ? Math.max(0, Math.floor(periodsBack)) : 0;
    // 滚动窗口不看 periodsBack：终点为 anchorDate（或今天），不存在「往前第几个窗口」。
    const resolvedBack = scope === 'ROLLING_7' || scope === 'ROLLING_30' ? 0 : back;

    const dates = this._reviewWindowDates(scope, resolvedBack, anchorDate);
    const todayIso = this._isoDateAt(this.virtualClockMs, 0);

    const daySecondsByDate = new Map(dates.map(d => [d, 0]));
    const buckets = new Map();
    let examCount = 0;

    for (const record of this.sessionRecords) {
      if (!daySecondsByDate.has(record.date)) continue;
      const seconds = Number.isFinite(record.elapsedSeconds)
        ? Math.max(0, record.elapsedSeconds)
        : 0;
      daySecondsByDate.set(record.date, daySecondsByDate.get(record.date) + seconds);

      let bucket = buckets.get(record.subjectId);
      if (!bucket) {
        bucket = {
          subjectId: record.subjectId,
          subjectName: record.subjectName,
          totalSeconds: 0,
          dailySeconds: new Map(),
        };
        buckets.set(record.subjectId, bucket);
      }
      bucket.totalSeconds += seconds;
      bucket.dailySeconds.set(record.date, (bucket.dailySeconds.get(record.date) || 0) + seconds);

      if (record.isExam === true) examCount++;
    }

    const days = dates.map(date => ({
      date,
      dayLabel: this._reviewDayLabel(scope, date),
      durationSeconds: daySecondsByDate.get(date) || 0,
      isToday: date === todayIso,
      // ISO 定长日期串，字典序即时间序。
      isFuture: date > todayIso,
    }));
    const totalSeconds = days.reduce((acc, day) => acc + day.durationSeconds, 0);

    const subjectDistribution = [...buckets.values()]
      .map(bucket => ({
        subjectId: bucket.subjectId,
        subjectName: bucket.subjectName,
        subjectColor: this._subjectColor(bucket.subjectId),
        // 唯一的截断点：秒先加完，再除 60。
        minutes: Math.floor(bucket.totalSeconds / 60),
        // share 走秒：用整数分钟会在总时长不足一分钟时除零，也会把占比放大。
        share: totalSeconds > 0 ? bucket.totalSeconds / totalSeconds : 0,
        // 键集合与 days[].date 完全一致：没有数据的那天也必须是 0，不能缺键。
        dailyMinutes: dates.reduce((acc, date) => {
          acc[date] = Math.floor((bucket.dailySeconds.get(date) || 0) / 60);
          return acc;
        }, {}),
      }))
      .sort((a, b) => {
        if (b.minutes !== a.minutes) return b.minutes - a.minutes;
        if (a.subjectId === b.subjectId) return 0;
        return a.subjectId < b.subjectId ? -1 : 1;
      });

    // 日均的分母：只数已经过去的日子。自然周 / 自然月的窗口含未来格子，
    // 用 dates.length 会把日均稀释（与 Kotlin `BridgeMappers.elapsedDays` 同口径）。
    const elapsedDays = Math.max(1, days.filter(d => !d.isFuture).length);

    return {
      scope,
      periodsBack: resolvedBack,
      label: this._reviewScopeLabel(scope, dates, todayIso),
      windowDays: dates.length,
      days,
      totalSeconds,
      dailyAverageSeconds: Math.floor(totalSeconds / elapsedDays),
      examCount,
      subjectDistribution,
    };
  }

  /** 编辑一条已完成记录的随笔。空内容 / 找不到会话都必须是硬失败，不做静默回落。 */
  async updateSessionNote(sessionId, isExam, note) {
    const cleanNote = typeof note === 'string' ? note.trim() : '';
    if (cleanNote.length === 0) {
      throw new Error('Session note cannot be empty');
    }
    const record = this._findSessionRecord(sessionId, isExam);
    if (!record) {
      throw new Error(`Session not found: ${sessionId}`);
    }
    record.note = cleanNote;
    this._emitDataChanged('sessions');
    return true;
  }

  /** 删除一条记录。找不到返回 false（与 deleteTask / deleteNote 同风格）。 */
  async deleteSessionRecord(sessionId, isExam) {
    const index = this.sessionRecords.findIndex(r => this._isSessionRecord(r, sessionId, isExam));
    if (index === -1) return false;
    this.sessionRecords.splice(index, 1);
    this._emitDataChanged('sessions');
    return true;
  }

  _findSessionRecord(sessionId, isExam) {
    return this.sessionRecords.find(r => this._isSessionRecord(r, sessionId, isExam)) || null;
  }

  _isSessionRecord(record, sessionId, isExam) {
    return record.id === sessionId && Boolean(record.isExam) === Boolean(isExam);
  }

  _subjectColor(subjectId) {
    const subject = this.subjects.find(s => s.id === subjectId);
    return subject ? subject.color : NEUTRAL_SUBJECT_COLOR;
  }

  /** 窗口内的本地日历日期键，升序、唯一。 */
  _reviewWindowDates(scope, periodsBack, anchorDate = null) {
    const anchor = anchorDate
      ? this._localDateFromIso(anchorDate)
      : new Date(this.virtualClockMs);

    if (scope === 'ROLLING_7' || scope === 'ROLLING_30') {
      const span = scope === 'ROLLING_7' ? 7 : 30;
      const dates = [];
      for (let i = span - 1; i >= 0; i--) {
        dates.push(this._isoOfLocalDate(this._shiftLocalDate(anchor, -i)));
      }
      return dates;
    }

    if (scope === 'CALENDAR_WEEK') {
      const weekAnchor = this._shiftLocalDate(anchor, -7 * periodsBack);
      const weekday = weekAnchor.getDay(); // 0 = 周日
      const monday = this._shiftLocalDate(weekAnchor, weekday === 0 ? -6 : 1 - weekday);
      const dates = [];
      // 周一起 7 天：跨月/跨年都靠本地日历推进，不会漏日也不会多日。
      for (let i = 0; i < 7; i++) dates.push(this._isoOfLocalDate(this._shiftLocalDate(monday, i)));
      return dates;
    }

    // CALENDAR_MONTH：先落到当月 1 号，再用「下月 0 号」求真实天数
    // （new Date(y, m, 0) 的月份是 0-based，m = 当月 1-based - 1）。
    const firstOfMonth = new Date(anchor.getFullYear(), anchor.getMonth() - periodsBack, 1);
    const lengthOfMonth = new Date(
      firstOfMonth.getFullYear(),
      firstOfMonth.getMonth() + 1,
      0
    ).getDate();
    const dates = [];
    for (let i = 0; i < lengthOfMonth; i++) {
      dates.push(this._isoOfLocalDate(this._shiftLocalDate(firstOfMonth, i)));
    }
    return dates;
  }

  /** 横轴标签：滚动窗口 → `MM-DD`，自然周 → `周一…周日`，自然月 → `D日`。 */
  _reviewDayLabel(scope, dateIso) {
    if (scope === 'ROLLING_7' || scope === 'ROLLING_30') return dateIso.slice(5);
    if (scope === 'CALENDAR_WEEK') {
      const d = this._localDateFromIso(dateIso);
      return WEEKDAY_LABELS[(d.getDay() + 6) % 7];
    }
    if (scope === 'CALENDAR_MONTH') return `${Number(dateIso.slice(8, 10))}日`;
    return dateIso;
  }

  /**
   * 窗口标题。自然周如实显示首尾日期（跨月也照实），不折算成「第 N 周」。
   * 滚动窗口只在终点确实是今天时才写「最近 N 天」：锚点落在过去时照抄这个标题，
   * 等于用假区间描述真区间（与 Kotlin `BridgeMappers.reviewScopeLabel` 同口径）。
   */
  _reviewScopeLabel(scope, dates, todayIso) {
    const first = dates[0];
    const last = dates[dates.length - 1];
    if (!first || !last) return '';
    if (scope === 'ROLLING_7' || scope === 'ROLLING_30') {
      return last === todayIso
        ? `最近 ${dates.length} 天`
        : `${this._monthDayLabel(first)} - ${this._monthDayLabel(last)}`;
    }
    if (scope === 'CALENDAR_WEEK') {
      return `${this._monthDayLabel(first)} - ${this._monthDayLabel(last)}`;
    }
    if (scope === 'CALENDAR_MONTH') {
      return `${Number(first.slice(0, 4))}年${Number(first.slice(5, 7))}月`;
    }
    return '';
  }

  _monthDayLabel(dateIso) {
    return `${Number(dateIso.slice(5, 7))}月${Number(dateIso.slice(8, 10))}日`;
  }

  _localDateFromIso(dateIso) {
    return new Date(
      Number(dateIso.slice(0, 4)),
      Number(dateIso.slice(5, 7)) - 1,
      Number(dateIso.slice(8, 10))
    );
  }

  /** 本地日历日推进：绝不用 24 小时倍数（跨夏令时会漂一天）。 */
  _shiftLocalDate(date, deltaDays) {
    const shifted = new Date(date.getTime());
    shifted.setDate(shifted.getDate() + deltaDays);
    return shifted;
  }

  _isoOfLocalDate(date) {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  /** Local-calendar yyyy-MM-dd shifted by deltaDays (never UTC-shifted). */
  _isoDateAt(epochMs, deltaDays) {
    return this._isoOfLocalDate(this._shiftLocalDate(new Date(epochMs), deltaDays));
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
