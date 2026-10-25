import type {
  DataChangedType,
  DailySnapshot,
  ExamCountdown,
  ReviewOverview,
  ReviewOverviewRequest,
  UserSettings,
  YanjiLaunchProperties,
} from '../bridge';

export type ReviewRequestKey = ReviewOverviewRequest;

interface SettingsSnapshot {
  settings: UserSettings;
  examCountdown: ExamCountdown;
}

interface DataRefreshLoaders {
  getDailySnapshot(date: string): Promise<DailySnapshot>;
  getReviewOverviews(requests: ReviewOverviewRequest[]): Promise<ReviewOverview[]>;
  getUserSettings(): Promise<UserSettings>;
  getExamCountdown(): Promise<ExamCountdown>;
}

type Listener = () => void;

function localDateIso(date = new Date()): string {
  return `${date.getFullYear()}-${`${date.getMonth() + 1}`.padStart(2, '0')}-${`${date.getDate()}`.padStart(2, '0')}`;
}

function effectivePeriodsBack(request: ReviewRequestKey): number {
  return request.scope === 'TODAY' ? 0 : Math.max(0, Math.trunc(request.periodsBack));
}

/** Same calendar window as ReviewOverviewReader.windowOf, independent of the display anchor. */
export function reviewWindowKey(request: ReviewRequestKey): string {
  const anchor = new Date(`${request.anchorDate}T12:00:00`);
  const start = Number.isNaN(anchor.getTime()) ? new Date() : anchor;
  const periodsBack = effectivePeriodsBack(request);
  if (request.scope === 'CALENDAR_WEEK') {
    start.setDate(start.getDate() - ((start.getDay() + 6) % 7) - periodsBack * 7);
  } else if (request.scope === 'CALENDAR_MONTH') {
    // Set the day first: subtracting a month from the 31st must not overflow February.
    start.setDate(1);
    start.setMonth(start.getMonth() - periodsBack);
  }
  const end = new Date(start);
  if (request.scope === 'CALENDAR_WEEK') end.setDate(end.getDate() + 6);
  else if (request.scope === 'CALENDAR_MONTH') {
    end.setMonth(end.getMonth() + 1, 0);
  }
  return `${request.scope}:${localDateIso(start)}:${localDateIso(end)}`;
}

/** Today affects flags/averages only while inside a window (including its last day). */
function reviewTodayBoundary(id: string): string {
  const [, first, last] = id.split(':');
  const today = localDateIso();
  return today < first ? 'future' : today > last ? 'past' : today;
}

/**
 * 保留多少份「已经没人看」的快照作为热缓存。
 *
 * 回顾页一次切日期要三个窗口（当前 / 环比 / 所选日所在周），预取还会额外读前后
 * 两个锚点。保留最近若干份，往回翻再翻回来就是同步命中；上限存在的原因是
 * `DailySnapshot` 自带当天的专注 / 计划 / 随笔明细，不能无界积累。
 */
const RETAINED_DAILY_LIMIT = 10;
const RETAINED_REVIEW_LIMIT = 24;

/**
 * Coordinates the app's Room-backed reads. This module has no React Native
 * imports so the same source can be exercised from Node.
 *
 * 快照的生命周期分三层，目的只有一个：页面切到某个日期 / 窗口时第一帧就有数据。
 *
 *  1. **live**：仍有读取方（可见页面或已挂载页面的订阅）在看的条目；
 *  2. **retained**：读取方离开后降级保留的热缓存；再次挂载直接命中，同时在
 *     dirty 时并行刷新（stale-while-revalidate）；
 *  3. **prefetched**：页面为相邻日期主动预取的结果，从未被读过也可以直接命中。
 *
 * 三层都共享同一份失效事实（`dailyDirty` / `reviewDirty`）：Room 表一变，
 * 已保留的条目一律标脏，下一次可见读取会用真实数据覆盖，绝不把旧快照当最终答案。
 */
export class DataRefreshCoordinator {
  private readonly dailyValues = new Map<string, DailySnapshot>();
  private readonly dailyRetained = new Map<string, DailySnapshot>();
  private readonly dailyRetainedDirty = new Set<string>();
  /** Dates whose live value came from the launch payload and has not been confirmed by a read. */
  private readonly dailySeeded = new Set<string>();
  private readonly dailyListeners = new Map<string, Set<Listener>>();
  private readonly dailyReaders = new Map<string, string>();
  private readonly dailyDirty = new Set<string>();
  private readonly dailyVersions = new Map<string, number>();
  private readonly dailyInFlight = new Map<string, Promise<void>>();
  /**
   * Dates whose most recent *completed* read failed.
   *
   * `ensureDaily` keeps the previous value when a read rejects (losing the page's
   * content to show a spinner would be worse than showing stale content), but
   * nothing used to record that the value it kept is no longer trustworthy.
   * This flag is exactly that record: pages read it to say 「数据可能不是最新」
   * instead of quietly presenting yesterday's numbers as today's.
   */
  private readonly dailyReadFailed = new Set<string>();

  private readonly reviewValues = new Map<string, ReviewOverview>();
  private readonly reviewRetained = new Map<string, ReviewOverview>();
  private readonly reviewRetainedDirty = new Set<string>();
  private readonly reviewAsOf = new Map<string, string>();
  // Request objects are owned by callers; keep useSyncExternalStore projections
  // stable without retaining every display anchor ever visited.
  private readonly reviewProjections = new WeakMap<ReviewRequestKey, {
    source: ReviewOverview;
    value: ReviewOverview;
  }>();
  private readonly reviewRequests = new Map<string, ReviewRequestKey>();
  private readonly reviewListeners = new Map<string, Set<Listener>>();
  private readonly reviewReaders = new Map<string, string[]>();
  private readonly reviewDirty = new Set<string>();
  private readonly reviewVersions = new Map<string, number>();
  private readonly reviewInFlight = new Map<string, Promise<void>>();
  /** Review windows whose most recent completed read failed (see `dailyReadFailed`). */
  private readonly reviewReadFailed = new Set<string>();

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
   * 只填**空槽**：内存里已经有值（包括已保留的热缓存），说明那是本进程真实读回来的，
   * 比进程启动时的载荷更新。因此重复调用是幂等的，不需要「一生只 seed 一次」的门闩
   * （那个门闩反而会让 Activity 重挂载后的新根组件既拿不到预热数据、也不再触发读取）。
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

    // 只填空槽：已有值说明本进程真实读过，绝不用旧载荷覆盖它。
    if (date && daily && daily.date === date && !this.hasDailyValue(date)) {
      this.dailyValues.set(date, daily);
      this.dailySeeded.add(date);
      if (stale) this.dailyDirty.add(date);
      else this.dailyDirty.delete(date);
    }
    if (settings && examCountdown && !this.settingsValue) {
      this.settingsValue = { settings, examCountdown };
      this.settingsDirty = stale;
    }
  }

  getDailySnapshot(date: string): DailySnapshot | null {
    return this.dailyValues.get(date) ?? this.dailyRetained.get(date) ?? null;
  }

  /**
   * Whether the last completed read for `date` failed.
   *
   * True means whatever `getDailySnapshot` returns is carried over from an
   * earlier successful read and may be out of date. It is false both before any
   * read (nothing has failed yet) and after a read succeeds.
   */
  getDailyReadFailed(date: string): boolean {
    return this.dailyReadFailed.has(date);
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

  /**
   * 预取若干日期的每日快照。
   *
   * 只为「下一步可能切到」的日期服务：当前日期正在被读取时不会重复发请求，
   * 已经新鲜的数据也不会重读。失败不产生失败横幅——页面真正切过去时还会按
   * 正常的可见读取路径重试一次。
   */
  async prefetchDailySnapshots(dates: ReadonlyArray<string>): Promise<void> {
    if (!this.foreground) return;
    const wanted: string[] = [];
    for (const date of dates) {
      if (this.isDailyFresh(date) || this.dailyInFlight.has(date) || wanted.includes(date)) continue;
      wanted.push(date);
    }
    await Promise.all(wanted.map(date => this.startDailyRead(date)));
  }

  getReviewOverview(request: ReviewRequestKey): ReviewOverview | null {
    const id = reviewWindowKey(request);
    const source = this.reviewValues.get(id) ?? this.reviewRetained.get(id);
    if (!source) return null;
    const previous = this.reviewProjections.get(request);
    if (previous?.source === source) return previous.value;
    const value = { ...source, scope: request.scope, periodsBack: effectivePeriodsBack(request) };
    this.reviewProjections.set(request, { source, value });
    return value;
  }

  /** Whether the last completed read for this window failed (see `getDailyReadFailed`). */
  getReviewReadFailed(request: ReviewRequestKey): boolean {
    return this.reviewReadFailed.has(reviewWindowKey(request));
  }

  subscribeReviewOverview(request: ReviewRequestKey, listener: Listener): () => void {
    const id = reviewWindowKey(request);
    this.reviewRequests.set(id, request);
    this.promoteReview(id);
    // Each subscription owns its slot, even if equivalent requests use the same callback.
    return this.subscribe(this.reviewListeners, id, () => listener(), () => this.pruneReview(id));
  }

  setReviewReader(readerId: string, requests: ReadonlyArray<ReviewRequestKey> | null): void {
    const previousIds = this.reviewReaders.get(readerId) ?? [];
    const nextRequests = requests ?? [];
    const nextIds = [...new Set(nextRequests.map(request => {
      const id = reviewWindowKey(request);
      this.reviewRequests.set(id, request);
      this.promoteReview(id);
      return id;
    }))];

    // Register next windows before releasing previous ones: switching between
    // equivalent anchors must not invalidate a shared read still in use.
    if (nextIds.length > 0) this.reviewReaders.set(readerId, nextIds);
    else this.reviewReaders.delete(readerId);
    for (const id of previousIds) {
      if (!this.isReviewVisible(id)) {
        this.invalidateReview(id);
        this.pruneReview(id);
      }
    }
    if (nextIds.length > 0) {
      // 一次切换涉及的三个窗口合并成一次桥调用：它们共享同一次区间扫描。
      // 已经有新鲜值（预取命中）的窗口不再重读。后台注册只登记不读取，
      // 由 `handleForeground` 在回到前台的那次跳变上补读。
      if (!this.foreground) return;
      const wanted = nextIds.filter(id => !this.isReviewFresh(id) && !this.reviewInFlight.has(id));
      if (wanted.length > 0) void this.readReviewIds(wanted);
    }
  }

  refreshReviewOverview(request: ReviewRequestKey): Promise<void> {
    const id = reviewWindowKey(request);
    this.reviewRequests.set(id, request);
    this.invalidateReview(id);
    if (this.isReviewVisible(id)) return this.ensureReview(id);
    this.pruneReview(id);
    return Promise.resolve();
  }

  /**
   * 预取若干回顾窗口（相邻日期、环比窗口等）。
   *
   * 这是「切日期第一帧直接出数」的关键：预取结果进入热缓存，页面下一次渲染时
   * `getReviewOverview` 同步命中，不会先画一个「—」再等桥回来。批量接口把
   * 所有预取窗口合并成一个区间并集，每张表只扫一次。
   *
   * 已经在读取中的窗口不会重复请求；已经新鲜（未失效）的窗口不会重读。
   */
  async prefetchReviewOverviews(requests: ReadonlyArray<ReviewRequestKey>): Promise<void> {
    if (!this.foreground) return;
    const wanted: string[] = [];
    for (const request of requests) {
      const id = reviewWindowKey(request);
      this.reviewRequests.set(id, request);
      if (this.isReviewFresh(id) || this.reviewInFlight.has(id) || wanted.includes(id)) continue;
      wanted.push(id);
    }
    if (wanted.length > 0) await this.readReviewIds(wanted);
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
      if (this.foreground) {
        const viewable = [...this.visibleReviewIds()]
          .filter(id => !this.isReviewFresh(id) && this.reviewRequests.has(id));
        if (viewable.length > 0) void this.readReviewIds(viewable);
      }
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
    // 后台期间可能错过了 Room 变更通知：所有保留条目（含热缓存）先标脏，
    // 但只对当前可见的条目立即重读；不可见的等下次挂载时再刷新。
    for (const date of this.knownDailyDates()) this.invalidateDaily(date);
    for (const date of this.visibleDailyDates()) void this.ensureDaily(date);
    for (const id of this.knownReviewIds()) this.invalidateReview(id);
    const viewable = [...this.visibleReviewIds()]
      .filter(id => !this.isReviewFresh(id) && this.reviewRequests.has(id));
    if (viewable.length > 0) void this.readReviewIds(viewable);
    if (this.settingsReaders.size > 0) {
      this.invalidateSettings();
      void this.ensureSettings();
    }
  }

  private hasDailyValue(date: string): boolean {
    return this.dailyValues.has(date) || this.dailyRetained.has(date);
  }

  private isDailyFresh(date: string): boolean {
    if (this.dailyValues.has(date) && !this.dailyDirty.has(date)) return true;
    return this.dailyRetained.has(date) && !this.dailyRetainedDirty.has(date);
  }

  private ensureDaily(date: string): Promise<void> {
    if (!this.foreground || !this.isDailyVisible(date)) return Promise.resolve();
    if (this.isDailyFresh(date)) return Promise.resolve();
    const existing = this.dailyInFlight.get(date);
    if (existing) return existing;
    return this.startDailyRead(date);
  }

  /**
   * 启动一次真实读取。可见读取与预取共用同一个 in-flight 槽位，
   * 因此两者不会对同一个日期各发一次请求。
   */
  private startDailyRead(date: string): Promise<void> {
    const existing = this.dailyInFlight.get(date);
    if (existing) return existing;

    const version = this.dailyVersions.get(date) ?? 0;
    const request = this.loaders.getDailySnapshot(date)
      .then(snapshot => {
        if ((this.dailyVersions.get(date) ?? 0) !== version) return;
        this.dailyValues.set(date, snapshot);
        this.dailySeeded.delete(date);
        this.dailyRetained.delete(date);
        this.dailyRetainedDirty.delete(date);
        this.dailyDirty.delete(date);
        // A real read landed, so the value is trustworthy again — and pages that
        // showed 「数据可能不是最新」 must be told to take the banner down.
        this.dailyReadFailed.delete(date);
        this.notify(this.dailyListeners, date);
      })
      .catch(() => {
        // Same version guard as the success path: an obsolete response must not
        // describe the state of a read that was superseded while in flight.
        if ((this.dailyVersions.get(date) ?? 0) !== version) return;
        this.dailyDirty.add(date);
        this.dailyReadFailed.add(date);
        this.notify(this.dailyListeners, date);
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

  private isReviewFresh(id: string): boolean {
    if (this.reviewAsOf.get(id) !== reviewTodayBoundary(id)) return false;
    if (this.reviewValues.has(id) && !this.reviewDirty.has(id)) return true;
    return this.reviewRetained.has(id) && !this.reviewRetainedDirty.has(id);
  }

  private ensureReview(id: string): Promise<void> {
    if (!this.foreground || !this.isReviewVisible(id)) return Promise.resolve();
    if (!this.reviewRequests.has(id)) return Promise.resolve();
    if (this.isReviewFresh(id)) return Promise.resolve();
    const existing = this.reviewInFlight.get(id);
    if (existing) return existing;
    return this.readReviewIds([id]);
  }

  /**
   * 批量读取一组窗口。所有窗口在同一次原生调用里共享一次区间扫描，
   * 每个 id 仍按自己的版本号判断结果是否已经过期。
   */
  private readReviewIds(ids: ReadonlyArray<string>): Promise<void> {
    const entries = [...new Set(ids)]
      // 已经在读的窗口不重复发请求：失效已经由版本号记录，旧读取完成时
      // 会因为版本不匹配被丢弃，并在 finally 里按需补一次新读取。
      .filter(id => !this.reviewInFlight.has(id))
      .map(id => ({
        id,
        request: this.reviewRequests.get(id),
        version: this.reviewVersions.get(id) ?? 0,
        asOf: reviewTodayBoundary(id),
      }))
      .filter((entry): entry is { id: string; request: ReviewRequestKey; version: number; asOf: string } =>
        entry.request !== undefined
      );
    if (entries.length === 0) return Promise.resolve();

    const request = this.loaders
      .getReviewOverviews(entries.map(entry => entry.request))
      .then(overviews => {
        entries.forEach((entry, index) => {
          if ((this.reviewVersions.get(entry.id) ?? 0) !== entry.version ||
              reviewTodayBoundary(entry.id) !== entry.asOf) return;
          const overview = overviews[index];
          if (!overview) return;
          this.reviewValues.set(entry.id, overview);
          this.reviewAsOf.set(entry.id, entry.asOf);
          this.reviewRetained.delete(entry.id);
          this.reviewRetainedDirty.delete(entry.id);
          this.reviewDirty.delete(entry.id);
          this.reviewReadFailed.delete(entry.id);
          this.notify(this.reviewListeners, entry.id);
        });
      })
      .catch(() => {
        entries.forEach(entry => {
          if ((this.reviewVersions.get(entry.id) ?? 0) !== entry.version ||
              reviewTodayBoundary(entry.id) !== entry.asOf) return;
          this.reviewDirty.add(entry.id);
          this.reviewReadFailed.add(entry.id);
          this.notify(this.reviewListeners, entry.id);
        });
      })
      .finally(() => {
        entries.forEach(entry => {
          if (this.reviewInFlight.get(entry.id) === request) this.reviewInFlight.delete(entry.id);
        });
        entries.forEach(entry => {
          if (((this.reviewVersions.get(entry.id) ?? 0) !== entry.version ||
               reviewTodayBoundary(entry.id) !== entry.asOf) && this.isReviewVisible(entry.id)) {
            void this.ensureReview(entry.id);
          } else {
            this.pruneReview(entry.id);
          }
        });
      });
    entries.forEach(entry => this.reviewInFlight.set(entry.id, request));
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
    if (this.dailyRetained.has(date)) this.dailyRetainedDirty.add(date);
  }

  private invalidateReview(id: string): void {
    this.reviewVersions.set(id, (this.reviewVersions.get(id) ?? 0) + 1);
    this.reviewDirty.add(id);
    if (this.reviewRetained.has(id)) this.reviewRetainedDirty.add(id);
  }

  private invalidateSettings(): void {
    this.settingsVersion += 1;
    this.settingsDirty = true;
  }

  private knownDailyDates(): Set<string> {
    return new Set([
      ...this.dailyValues.keys(),
      ...this.dailyRetained.keys(),
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
      ...this.reviewRetained.keys(),
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
      if (!keyListeners?.delete(listener)) return;
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
    const value = this.dailyValues.get(date);
    if (value) {
      // 启动载荷不是本进程的读取结果：降级为热缓存时必须标脏，
      // 让下一次挂载用真实读取确认，而不是把进程启动时的快照当成最终答案。
      this.retainDaily(date, value, this.dailyDirty.has(date) || this.dailySeeded.has(date));
    }
    this.dailySeeded.delete(date);
    this.dailyValues.delete(date);
    this.dailyDirty.delete(date);
    this.dailyVersions.delete(date);
    // The failure belongs to the released entry. Keeping it would let a later
    // mount of the same date start out claiming its data is stale.
    this.dailyReadFailed.delete(date);
  }

  private retainDaily(date: string, snapshot: DailySnapshot, dirty: boolean): void {
    // delete + set 把该条移到 Map 尾部，淘汰时从最旧的一份开始。
    this.dailyRetained.delete(date);
    this.dailyRetained.set(date, snapshot);
    if (dirty) this.dailyRetainedDirty.add(date);
    else this.dailyRetainedDirty.delete(date);
    while (this.dailyRetained.size > RETAINED_DAILY_LIMIT) {
      const oldest = this.dailyRetained.keys().next().value as string | undefined;
      if (oldest === undefined) break;
      this.dailyRetained.delete(oldest);
      this.dailyRetainedDirty.delete(oldest);
    }
  }

  private promoteReview(id: string): void {
    const value = this.reviewRetained.get(id);
    if (!value) return;
    this.reviewValues.set(id, value);
    if (this.reviewRetainedDirty.has(id)) this.reviewDirty.add(id);
    this.reviewRetained.delete(id);
    this.reviewRetainedDirty.delete(id);
  }

  private pruneReview(id: string): void {
    if (this.isReviewVisible(id) || this.reviewListeners.has(id) || this.reviewInFlight.has(id)) {
      return;
    }
    const value = this.reviewValues.get(id);
    if (value) {
      this.retainReview(id, value, this.reviewDirty.has(id));
    } else if (!this.reviewRetained.has(id)) {
      // Another equivalent subscription may release after this value moved to
      // the warm cache. Its request and freshness still belong to that value.
      this.reviewRequests.delete(id);
      this.reviewAsOf.delete(id);
    }
    this.reviewValues.delete(id);
    this.reviewDirty.delete(id);
    this.reviewVersions.delete(id);
    // Keep `reviewRequests` only for retained entries: it is the key needed to
    // refresh the window if a later mount finds it dirty.
    this.reviewReadFailed.delete(id);
  }

  private retainReview(id: string, overview: ReviewOverview, dirty: boolean): void {
    this.reviewRetained.delete(id);
    this.reviewRetained.set(id, overview);
    if (dirty) this.reviewRetainedDirty.add(id);
    else this.reviewRetainedDirty.delete(id);
    while (this.reviewRetained.size > RETAINED_REVIEW_LIMIT) {
      const oldest = this.reviewRetained.keys().next().value as string | undefined;
      if (oldest === undefined) break;
      this.reviewRetained.delete(oldest);
      this.reviewRetainedDirty.delete(oldest);
      this.reviewRequests.delete(oldest);
      this.reviewAsOf.delete(oldest);
    }
  }

  private notify(listeners: Map<string, Set<Listener>>, key: string): void {
    this.notifyAll(listeners.get(key));
  }

  private notifyAll(listeners: Set<Listener> | undefined): void {
    listeners?.forEach(listener => listener());
  }
}
