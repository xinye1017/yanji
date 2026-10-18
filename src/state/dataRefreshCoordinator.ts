import type {
  DataChangedType,
  DailySnapshot,
  ExamCountdown,
  ReviewOverview,
  ReviewScope,
  UserSettings,
  YanjiLaunchProperties,
} from '../bridge';

export interface ReviewRequestKey {
  scope: ReviewScope;
  periodsBack: number;
  anchorDate: string;
}

interface SettingsSnapshot {
  settings: UserSettings;
  examCountdown: ExamCountdown;
}

interface DataRefreshLoaders {
  getDailySnapshot(date: string): Promise<DailySnapshot>;
  getReviewOverview(
    scope: ReviewScope,
    periodsBack: number,
    anchorDate: string
  ): Promise<ReviewOverview>;
  getUserSettings(): Promise<UserSettings>;
  getExamCountdown(): Promise<ExamCountdown>;
}

type Listener = () => void;

function reviewRequestId(request: ReviewRequestKey): string {
  return `${request.scope}:${request.periodsBack}:${request.anchorDate}`;
}

/**
 * Coordinates the app's Room-backed reads. This module has no React Native
 * imports so the same source can be exercised from Node.
 */
export class DataRefreshCoordinator {
  private readonly dailyValues = new Map<string, DailySnapshot>();
  private readonly dailyListeners = new Map<string, Set<Listener>>();
  private readonly dailyReaders = new Map<string, string>();
  private readonly dailyDirty = new Set<string>();
  private readonly dailyVersions = new Map<string, number>();
  private readonly dailyInFlight = new Map<string, Promise<void>>();

  private readonly reviewValues = new Map<string, ReviewOverview>();
  private readonly reviewRequests = new Map<string, ReviewRequestKey>();
  private readonly reviewListeners = new Map<string, Set<Listener>>();
  private readonly reviewReaders = new Map<string, string[]>();
  private readonly reviewDirty = new Set<string>();
  private readonly reviewVersions = new Map<string, number>();
  private readonly reviewInFlight = new Map<string, Promise<void>>();

  private readonly settingsListeners = new Set<Listener>();
  private readonly settingsReaders = new Set<string>();
  private settingsValue: SettingsSnapshot | null = null;
  private settingsDirty = true;
  private settingsVersion = 0;
  private settingsInFlight: Promise<void> | null = null;

  private readonly pendingEvents = new Set<DataChangedType>();
  private flushScheduled = false;
  private foreground = true;
  /**
   * Whether `seed` has already run in this process.
   *
   * This is NOT the retired one-shot latch (which refused to seed again and left
   * a remounted root with nothing). It only records that the host payload has
   * been consumed once, so a later re-seed can be treated as possibly stale.
   */
  private hasSeeded = false;

  constructor(private readonly loaders: DataRefreshLoaders) {}

  /**
   * 更新前台状态，并在真正回到前台时补一次读取。
   *
   * 为什么补偿必须在这里：Activity 退出再进入（进程与 JS 上下文都还活着）时，React 树
   * 重新挂载，子组件的 effect **先于** `App` 的 effect 执行。于是读取方注册时
   * `foreground` 还是上一轮遗留的 `false`，那次注册被门控挡掉了；等 `App` 把前台状态
   * 补成 `true` 时，读取方不会再次注册（日期没变），若不在这里补一次，页面就永远停在
   * 「正在读取」。
   *
   * 只在 `false → true` 的跳变上补偿：冷启动时初值就是 `true`，重复调用不会多发读取。
   */
  setForeground(isForeground: boolean): void {
    const wasForeground = this.foreground;
    this.foreground = isForeground;
    if (isForeground && !wasForeground) this.handleForeground();
  }

  /**
   * 冷启动预热：把宿主在首帧前读好的真实数据直接装进缓存。
   *
   * 首页第一帧读的是 `getDailySnapshot()` / `getSettingsSnapshot()`，冷启动时它们是空的，
   * 于是只能先渲染「正在读取…」。宿主（`bridge/LaunchPreheat`）把同一份 Room 数据提前读好
   * 当 launch options 送进来，这里在首帧前播种，页面第一帧就是有数据的。
   *
   * 日期对不上的快照（跨过午夜）直接丢弃——宁可按原路径重新读。
   *
   * 只填**空槽**：缓存里已经有值，说明桥接读已经回来过，那才是更新的数据。
   * 因此重复调用是幂等的，不需要「一生只 seed 一次」的门闩（那个门闩反而会让
   * Activity 重挂载后的新根组件既拿不到预热数据、也不再触发读取）。
   *
   * 新鲜度分两种情况，因为宿主载荷只读一次（`Application.onCreate`），之后不会更新：
   *  - **本进程首次播种**：载荷与 Room 的偏差只有启动那一瞬，可信。保持不脏，
   *    首帧直接用，不发多余读取——这正是预热要省掉的那一次。
   *  - **重播种**（进程存活期间 Activity 重挂载）：距首次读已过去任意长时间，载荷可能
   *    比 Room 旧。标脏，让紧随其后的第一次可见读取用真实数据覆盖它；首帧仍有内容，
   *    但不会把过期快照当成最终答案。
   */
  seed(properties: Partial<YanjiLaunchProperties> | null | undefined): void {
    if (!properties) return;

    const date = properties.date;
    const daily = properties.dailySnapshot;
    const settings = properties.settings;
    const examCountdown = properties.examCountdown;
    // A re-seed reuses the payload the host read at process start, so anything
    // it fills now may already be older than Room.
    const stale = this.hasSeeded;
    this.hasSeeded = true;

    // 只填空槽：已有值说明桥接读回来过，那才是更新的数据，绝不用旧载荷覆盖它。
    if (date && daily && daily.date === date && !this.dailyValues.has(date)) {
      this.dailyValues.set(date, daily);
      if (stale) this.dailyDirty.add(date);
      else this.dailyDirty.delete(date);
    }
    if (settings && examCountdown && !this.settingsValue) {
      this.settingsValue = { settings, examCountdown };
      this.settingsDirty = stale;
    }
  }

  getDailySnapshot(date: string): DailySnapshot | null {
    return this.dailyValues.get(date) ?? null;
  }

  subscribeDailySnapshot(date: string, listener: Listener): () => void {
    return this.subscribe(this.dailyListeners, date, listener, () => this.pruneDaily(date));
  }

  setDailyReader(readerId: string, date: string | null): void {
    const previousDate = this.dailyReaders.get(readerId) ?? null;
    if (previousDate === date) return;

    if (previousDate) {
      this.dailyReaders.delete(readerId);
      if (!this.isDailyVisible(previousDate)) {
        this.invalidateDaily(previousDate);
        this.pruneDaily(previousDate);
      }
    }
    if (date) {
      this.dailyReaders.set(readerId, date);
      void this.ensureDaily(date);
    }
  }

  refreshDailySnapshot(date: string): Promise<void> {
    this.invalidateDaily(date);
    if (this.isDailyVisible(date)) return this.ensureDaily(date);
    this.pruneDaily(date);
    return Promise.resolve();
  }

  getReviewOverview(request: ReviewRequestKey): ReviewOverview | null {
    return this.reviewValues.get(reviewRequestId(request)) ?? null;
  }

  subscribeReviewOverview(request: ReviewRequestKey, listener: Listener): () => void {
    const id = reviewRequestId(request);
    this.reviewRequests.set(id, request);
    return this.subscribe(this.reviewListeners, id, listener, () => this.pruneReview(id));
  }

  setReviewReader(readerId: string, requests: ReadonlyArray<ReviewRequestKey> | null): void {
    const previousIds = this.reviewReaders.get(readerId) ?? [];
    const nextRequests = requests ?? [];
    const nextIds = nextRequests.map(request => {
      const id = reviewRequestId(request);
      this.reviewRequests.set(id, request);
      return id;
    });
    if (previousIds.length === nextIds.length && previousIds.every((id, i) => id === nextIds[i])) {
      return;
    }

    this.reviewReaders.delete(readerId);
    for (const id of previousIds) {
      if (!this.isReviewVisible(id)) {
        this.invalidateReview(id);
        this.pruneReview(id);
      }
    }
    if (nextIds.length > 0) {
      this.reviewReaders.set(readerId, nextIds);
      for (const id of nextIds) {
        if (this.reviewDirty.has(id) || !this.reviewValues.has(id)) {
          void this.ensureReview(id);
        }
      }
    }
  }

  refreshReviewOverview(request: ReviewRequestKey): Promise<void> {
    const id = reviewRequestId(request);
    this.reviewRequests.set(id, request);
    this.invalidateReview(id);
    if (this.isReviewVisible(id)) return this.ensureReview(id);
    this.pruneReview(id);
    return Promise.resolve();
  }

  getSettingsSnapshot(): SettingsSnapshot | null {
    return this.settingsValue;
  }

  subscribeSettings(listener: Listener): () => void {
    this.settingsListeners.add(listener);
    return () => this.settingsListeners.delete(listener);
  }

  setSettingsReader(readerId: string, visible: boolean): void {
    if (visible) {
      this.settingsReaders.add(readerId);
      void this.ensureSettings();
      return;
    }
    if (this.settingsReaders.delete(readerId) && this.settingsReaders.size === 0) {
      this.invalidateSettings();
    }
  }

  /**
   * Re-reads settings and **reports failure to the caller**.
   *
   * `ensureSettings` deliberately swallows a failed read: it also runs from
   * background paths (reader registration, data-changed events, resume) where
   * there is nobody to catch a rejection and the next event will simply retry.
   * But an explicit refresh is different — it is the user pressing 「重新读取」,
   * and if it fails silently the page that asked for it stays on 「—」 forever
   * with no way to tell them anything happened.
   *
   * So this waits for the attempt and rejects when the read did not produce a
   * value, while the background path keeps its fire-and-forget behaviour.
   */
  async refreshSettings(): Promise<void> {
    this.invalidateSettings();
    if (this.settingsReaders.size === 0) return;
    await this.ensureSettings();
    // `ensureSettings` resolved but may have failed internally; the dirty flag
    // is exactly the "we still have no usable settings" signal it leaves behind.
    if (this.settingsDirty) throw new Error('settings read failed');
  }

  enqueueDataChanged(type: DataChangedType): void {
    this.pendingEvents.add(type);
    if (this.flushScheduled) return;
    this.flushScheduled = true;
    void Promise.resolve().then(() => this.flushPendingEvents());
  }

  /** Exposed for deterministic source-level tests and explicit event flushing. */
  flushPendingEvents(): void {
    if (!this.flushScheduled && this.pendingEvents.size === 0) return;
    this.flushScheduled = false;
    const events = new Set(this.pendingEvents);
    this.pendingEvents.clear();

    if (events.has('tasks') || events.has('notes') || events.has('sessions') || events.has('subjects')) {
      for (const date of this.knownDailyDates()) this.invalidateDaily(date);
      for (const date of this.visibleDailyDates()) void this.ensureDaily(date);
    }

    if (events.has('sessions') || events.has('subjects')) {
      for (const id of this.knownReviewIds()) this.invalidateReview(id);
      for (const id of this.visibleReviewIds()) void this.ensureReview(id);
    }

    if (events.has('settings')) {
      this.invalidateSettings();
      if (this.settingsReaders.size > 0) void this.ensureSettings();
    }
  }

  /**
   * Re-reads everything a resume can have missed.
   *
   * Private: `setForeground` is the only legitimate trigger, because a resume is
   * exactly a `false → true` transition. Keeping a public entry point would let
   * a caller re-read without that transition and silently invalidate the read it
   * just started.
   */
  private handleForeground(): void {
    for (const date of this.visibleDailyDates()) {
      this.invalidateDaily(date);
      void this.ensureDaily(date);
    }
    for (const id of this.visibleReviewIds()) {
      this.invalidateReview(id);
      void this.ensureReview(id);
    }
    if (this.settingsReaders.size > 0) {
      this.invalidateSettings();
      void this.ensureSettings();
    }
  }

  private async ensureDaily(date: string): Promise<void> {
    if (!this.foreground || !this.isDailyVisible(date)) return;
    if (!this.dailyDirty.has(date) && this.dailyValues.has(date)) return;
    const existing = this.dailyInFlight.get(date);
    if (existing) return existing;

    const version = this.dailyVersions.get(date) ?? 0;
    const request = this.loaders.getDailySnapshot(date)
      .then(snapshot => {
        if ((this.dailyVersions.get(date) ?? 0) !== version) return;
        this.dailyValues.set(date, snapshot);
        this.dailyDirty.delete(date);
        this.notify(this.dailyListeners, date);
      })
      .catch(() => {
        this.dailyDirty.add(date);
      })
      .finally(() => {
        if (this.dailyInFlight.get(date) === request) this.dailyInFlight.delete(date);
        if ((this.dailyVersions.get(date) ?? 0) !== version && this.isDailyVisible(date)) {
          void this.ensureDaily(date);
        } else {
          this.pruneDaily(date);
        }
      });
    this.dailyInFlight.set(date, request);
    return request;
  }

  private async ensureReview(id: string): Promise<void> {
    if (!this.foreground || !this.isReviewVisible(id)) return;
    const requestKey = this.reviewRequests.get(id);
    if (!requestKey) return;
    if (!this.reviewDirty.has(id) && this.reviewValues.has(id)) return;
    const existing = this.reviewInFlight.get(id);
    if (existing) return existing;

    const version = this.reviewVersions.get(id) ?? 0;
    const request = this.loaders.getReviewOverview(
      requestKey.scope,
      requestKey.periodsBack,
      requestKey.anchorDate
    )
      .then(overview => {
        if ((this.reviewVersions.get(id) ?? 0) !== version) return;
        this.reviewValues.set(id, overview);
        this.reviewDirty.delete(id);
        this.notify(this.reviewListeners, id);
      })
      .catch(() => {
        this.reviewDirty.add(id);
      })
      .finally(() => {
        if (this.reviewInFlight.get(id) === request) this.reviewInFlight.delete(id);
        if ((this.reviewVersions.get(id) ?? 0) !== version && this.isReviewVisible(id)) {
          void this.ensureReview(id);
        } else {
          this.pruneReview(id);
        }
      });
    this.reviewInFlight.set(id, request);
    return request;
  }

  private async ensureSettings(): Promise<void> {
    if (!this.foreground || this.settingsReaders.size === 0) return;
    if (!this.settingsDirty && this.settingsValue) return;
    if (this.settingsInFlight) return this.settingsInFlight;

    const version = this.settingsVersion;
    const request = Promise.all([
      this.loaders.getUserSettings(),
      this.loaders.getExamCountdown(),
    ])
      .then(([settings, examCountdown]) => {
        if (this.settingsVersion !== version) return;
        this.settingsValue = { settings, examCountdown };
        this.settingsDirty = false;
        this.notifyAll(this.settingsListeners);
      })
      .catch(() => {
        this.settingsDirty = true;
      })
      .finally(() => {
        if (this.settingsInFlight === request) this.settingsInFlight = null;
        if (this.settingsVersion !== version && this.settingsReaders.size > 0) {
          void this.ensureSettings();
        }
      });
    this.settingsInFlight = request;
    return request;
  }

  private invalidateDaily(date: string): void {
    this.dailyVersions.set(date, (this.dailyVersions.get(date) ?? 0) + 1);
    this.dailyDirty.add(date);
  }

  private invalidateReview(id: string): void {
    this.reviewVersions.set(id, (this.reviewVersions.get(id) ?? 0) + 1);
    this.reviewDirty.add(id);
  }

  private invalidateSettings(): void {
    this.settingsVersion += 1;
    this.settingsDirty = true;
  }

  private knownDailyDates(): Set<string> {
    return new Set([
      ...this.dailyValues.keys(),
      ...this.dailyReaders.values(),
      ...this.dailyListeners.keys(),
      ...this.dailyInFlight.keys(),
    ]);
  }

  private visibleDailyDates(): Set<string> {
    return new Set(this.dailyReaders.values());
  }

  private knownReviewIds(): Set<string> {
    return new Set([
      ...this.reviewValues.keys(),
      ...Array.from(this.reviewReaders.values()).flat(),
      ...this.reviewListeners.keys(),
      ...this.reviewInFlight.keys(),
    ]);
  }

  private visibleReviewIds(): Set<string> {
    return new Set(Array.from(this.reviewReaders.values()).flat());
  }

  private isDailyVisible(date: string): boolean {
    return this.visibleDailyDates().has(date);
  }

  private isReviewVisible(id: string): boolean {
    return this.visibleReviewIds().has(id);
  }

  private subscribe(
    listeners: Map<string, Set<Listener>>,
    key: string,
    listener: Listener,
    onEmpty?: () => void
  ): () => void {
    let keyListeners = listeners.get(key);
    if (!keyListeners) {
      keyListeners = new Set();
      listeners.set(key, keyListeners);
    }
    keyListeners.add(listener);
    return () => {
      keyListeners?.delete(listener);
      if (keyListeners?.size === 0) {
        listeners.delete(key);
        onEmpty?.();
      }
    };
  }

  private pruneDaily(date: string): void {
    if (this.isDailyVisible(date) || this.dailyListeners.has(date) || this.dailyInFlight.has(date)) {
      return;
    }
    this.dailyValues.delete(date);
    this.dailyDirty.delete(date);
    this.dailyVersions.delete(date);
  }

  private pruneReview(id: string): void {
    if (this.isReviewVisible(id) || this.reviewListeners.has(id) || this.reviewInFlight.has(id)) {
      return;
    }
    this.reviewValues.delete(id);
    this.reviewDirty.delete(id);
    this.reviewVersions.delete(id);
    this.reviewRequests.delete(id);
  }

  private notify(listeners: Map<string, Set<Listener>>, key: string): void {
    this.notifyAll(listeners.get(key));
  }

  private notifyAll(listeners: Set<Listener> | undefined): void {
    listeners?.forEach(listener => listener());
  }
}
