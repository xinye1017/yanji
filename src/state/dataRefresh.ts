import { useCallback, useEffect, useSyncExternalStore } from 'react';
import { YanjiDataNative } from '../bridge';
import type { YanjiLaunchProperties } from '../bridge';
import type { ReviewRequestKey } from './dataRefreshCoordinator';
import { DataRefreshCoordinator } from './dataRefreshCoordinator';

const noSubscribe = (_listener: () => void): (() => void) => () => {};
const subscribeSettings = (listener: () => void) =>
  dataRefreshCoordinator.subscribeSettings(listener);
const getSettingsSnapshot = () => dataRefreshCoordinator.getSettingsSnapshot();

export const dataRefreshCoordinator = new DataRefreshCoordinator({
  getDailySnapshot: date => YanjiDataNative.getDailySnapshot(date),
  getReviewOverviews: requests => YanjiDataNative.getReviewOverviews(requests),
  getUserSettings: () => YanjiDataNative.getUserSettings(),
  getExamCountdown: () => YanjiDataNative.getExamCountdown(),
});

export function useDailySnapshot(
  readerId: string,
  date: string,
  visible: boolean
) {
  // Mounted panes retain their snapshot; only visible readers trigger Room reads.
  const subscribe = useCallback(
    (listener: () => void) => dataRefreshCoordinator.subscribeDailySnapshot(date, listener),
    [date]
  );
  const getSnapshot = useCallback(
    () => dataRefreshCoordinator.getDailySnapshot(date),
    [date]
  );
  const snapshot = useSyncExternalStore(subscribe, getSnapshot, getSnapshot);

  useEffect(() => {
    dataRefreshCoordinator.setDailyReader(readerId, visible ? date : null);
    return () => dataRefreshCoordinator.setDailyReader(readerId, null);
  }, [date, readerId, visible]);

  return snapshot;
}

/**
 * Whether the coordinator's last completed read for `date` failed.
 *
 * Deliberately a separate hook rather than a field on `useDailySnapshot`'s
 * return value: a snapshot read and its freshness are two different things a
 * page may need at different times, and folding them into one object would
 * force every existing reader (Focus, Review, and their test doubles) to
 * destructure a new shape for something only the banner cares about.
 *
 * It subscribes to the same channel as `useDailySnapshot`, because the
 * coordinator notifies listeners on failure as well as on success — that is
 * what lets a page that is holding stale content learn it went stale.
 */
export function useDailyReadFailed(date: string): boolean {
  const subscribe = useCallback(
    (listener: () => void) => dataRefreshCoordinator.subscribeDailySnapshot(date, listener),
    [date]
  );
  const getSnapshot = useCallback(
    () => dataRefreshCoordinator.getDailyReadFailed(date),
    [date]
  );
  return useSyncExternalStore(subscribe, getSnapshot, getSnapshot);
}

export function useReviewOverview(request: ReviewRequestKey) {
  const subscribe = useCallback(
    (listener: () => void) => dataRefreshCoordinator.subscribeReviewOverview(request, listener),
    [request.anchorDate, request.periodsBack, request.scope]
  );
  const getSnapshot = useCallback(
    () => dataRefreshCoordinator.getReviewOverview(request),
    [request.anchorDate, request.periodsBack, request.scope]
  );
  return useSyncExternalStore(subscribe, getSnapshot, getSnapshot);
}

/**
 * Whether the coordinator's last completed read for this window failed, so the
 * page can say the figures it is showing may be older than they look. See
 * `useDailyReadFailed` for why this is its own hook.
 */
export function useReviewReadFailed(request: ReviewRequestKey): boolean {
  const subscribe = useCallback(
    (listener: () => void) => dataRefreshCoordinator.subscribeReviewOverview(request, listener),
    [request.anchorDate, request.periodsBack, request.scope]
  );
  const getSnapshot = useCallback(
    () => dataRefreshCoordinator.getReviewReadFailed(request),
    [request.anchorDate, request.periodsBack, request.scope]
  );
  return useSyncExternalStore(subscribe, getSnapshot, getSnapshot);
}

export function useSettingsSnapshot(readerId: string, visible: boolean) {
  const subscribe = useCallback(
    (listener: () => void) => visible ? subscribeSettings(listener) : noSubscribe(listener),
    [visible]
  );
  const snapshot = useSyncExternalStore(subscribe, getSettingsSnapshot, getSettingsSnapshot);

  useEffect(() => {
    dataRefreshCoordinator.setSettingsReader(readerId, visible);
    return () => dataRefreshCoordinator.setSettingsReader(readerId, false);
  }, [readerId, visible]);

  return snapshot;
}

export function useReviewDataVisibility(
  readerId: string,
  requests: ReadonlyArray<ReviewRequestKey>,
  visible: boolean
): void {
  // Update the reader atomically so retained week/month windows survive changes
  // to the other requested windows. Only unmount/reader-id changes release all.
  useEffect(() => {
    dataRefreshCoordinator.setReviewReader(readerId, visible ? requests : null);
  }, [readerId, requests, visible]);
  useEffect(() => () => dataRefreshCoordinator.setReviewReader(readerId, null), [readerId]);
}

export function refreshDailySnapshot(date: string): Promise<void> {
  return dataRefreshCoordinator.refreshDailySnapshot(date);
}

export function refreshReviewOverview(request: ReviewRequestKey): Promise<void> {
  return dataRefreshCoordinator.refreshReviewOverview(request);
}

export function refreshSettingsSnapshot(): Promise<void> {
  return dataRefreshCoordinator.refreshSettings();
}

/**
 * 预取相邻日期的每日快照，供回顾页日期切换时首帧直接出数。
 *
 * 结果进入协调器的热缓存；页面真正切过去时 `useDailySnapshot` 会同步命中，
 * 不会先渲染一屏空值。
 */
export function prefetchDailySnapshots(dates: ReadonlyArray<string>): Promise<void> {
  return dataRefreshCoordinator.prefetchDailySnapshots(dates);
}

/** 预取回顾窗口（当前 / 环比 / 相邻日期），批量合并为一次原生读取。 */
export function prefetchReviewOverviews(
  requests: ReadonlyArray<ReviewRequestKey>
): Promise<void> {
  return dataRefreshCoordinator.prefetchReviewOverviews(requests);
}

/**
 * 播种宿主冷启动预热读好的真实数据。
 *
 * 必须在首页第一次渲染**之前**调用（根组件 `App` 里做），否则首帧仍是空的、
 * 之后才被刷新。预热失败时 `properties` 为 undefined，此时不播种，页面照旧
 * 由 `useDailySnapshot` / `useSettingsSnapshot` 过桥读取。
 */
export function seedLaunchSnapshot(
  properties: Partial<YanjiLaunchProperties> | null | undefined
): void {
  dataRefreshCoordinator.seed(properties);
}
