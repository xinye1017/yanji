import { useCallback, useEffect, useSyncExternalStore } from 'react';
import { YanjiDataNative } from '../bridge';
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
  const subscribe = useCallback(
    (listener: () => void) => visible
      ? dataRefreshCoordinator.subscribeDailySnapshot(date, listener)
      : noSubscribe(listener),
    [date, visible]
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

export function useReviewOverview(request: ReviewRequestKey, visible: boolean) {
  const subscribe = useCallback(
    (listener: () => void) => visible
      ? dataRefreshCoordinator.subscribeReviewOverview(request, listener)
      : noSubscribe(listener),
    [request.anchorDate, request.periodsBack, request.scope, visible]
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
