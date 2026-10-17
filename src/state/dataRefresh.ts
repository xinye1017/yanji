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
  getReviewOverview: (scope, periodsBack, anchorDate) =>
    YanjiDataNative.getReviewOverview(scope, periodsBack, anchorDate),
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
  useEffect(() => {
    dataRefreshCoordinator.setReviewReader(readerId, visible ? requests : null);
    return () => dataRefreshCoordinator.setReviewReader(readerId, null);
  }, [readerId, requests, visible]);
}

export function refreshDailySnapshot(date: string): Promise<void> {
  return dataRefreshCoordinator.refreshDailySnapshot(date);
}

export function refreshSettingsSnapshot(): Promise<void> {
  return dataRefreshCoordinator.refreshSettings();
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
