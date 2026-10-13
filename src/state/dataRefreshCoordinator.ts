import type {
  DataChangedType,
  DailySnapshot,
  ExamCountdown,
  ReviewOverview,
  ReviewScope,
  UserSettings,
} from '../bridge';

export interface ReviewRequestKey {
  scope: ReviewScope;
  periodsBack: number;
  anchorDate: string;
}

export interface SettingsSnapshot {
  settings: UserSettings;
  examCountdown: ExamCountdown;
}

export interface DataRefreshLoaders {
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

export function reviewRequestId(request: ReviewRequestKey): string {
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

  constructor(private readonly loaders: DataRefreshLoaders) {}

  setForeground(isForeground: boolean): void {
    this.foreground = isForeground;
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

  refreshSettings(): Promise<void> {
    this.invalidateSettings();
    return this.settingsReaders.size > 0 ? this.ensureSettings() : Promise.resolve();
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

    if (events.has('tasks') || events.has('notes') || events.has('sessions')) {
      for (const date of this.knownDailyDates()) this.invalidateDaily(date);
      for (const date of this.visibleDailyDates()) void this.ensureDaily(date);
    }

    if (events.has('sessions')) {
      for (const id of this.knownReviewIds()) this.invalidateReview(id);
      for (const id of this.visibleReviewIds()) void this.ensureReview(id);
    }

    if (events.has('settings')) {
      this.invalidateSettings();
      if (this.settingsReaders.size > 0) void this.ensureSettings();
    }
  }

  handleForeground(): void {
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
