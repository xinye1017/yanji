import { useCallback, useEffect, useSyncExternalStore } from 'react';
import {
  YanjiTimerNative,
  onTimerStateChanged,
  onTimerTick,
} from '../bridge';
import type { ActiveSessionState, TimerTickEvent } from '../bridge';

type Listener = () => void;

let sessionSnapshot: ActiveSessionState | null = null;
let tickSnapshot: TimerTickEvent | null = null;
let foreground = true;
let sessionRevision = 0;
let tickRevision = 0;
let started = false;
let syncInFlight: Promise<void> | null = null;

const sessionListeners = new Set<Listener>();
const tickListeners = new Set<Listener>();

function notify(listeners: Set<Listener>): void {
  listeners.forEach(listener => listener());
}

function setSession(session: ActiveSessionState | null, preserveNewerTick = false): void {
  const keepTick = preserveNewerTick
    && session !== null
    && sessionSnapshot?.sessionId === session.sessionId
    && tickSnapshot !== null;
  sessionSnapshot = session;
  if (!keepTick) tickSnapshot = null;
  notify(sessionListeners);
  if (!keepTick && tickListeners.size > 0) notify(tickListeners);
}

function startNativeSubscriptions(): void {
  if (started) return;
  started = true;

  onTimerStateChanged(event => {
    sessionRevision += 1;
    setSession(event.session);
  });
  onTimerTick(event => {
    if (!foreground || tickListeners.size === 0) return;
    tickRevision += 1;
    tickSnapshot = event;
    notify(tickListeners);
  });
  void syncActiveSession();
}

export function syncActiveSession(): Promise<void> {
  if (syncInFlight) return syncInFlight;
  const revision = sessionRevision;
  const tickAtStart = tickRevision;
  const request = YanjiTimerNative.getActiveSession()
    .then(session => {
      if (sessionRevision === revision) {
        setSession(session, tickRevision !== tickAtStart);
      }
    })
    .catch(() => {
      // Keep the last native snapshot if the bridge read fails.
    })
    .finally(() => {
      if (syncInFlight === request) syncInFlight = null;
    });
  syncInFlight = request;
  return request;
}

export function setTimerForeground(isForeground: boolean): void {
  if (foreground === isForeground) {
    if (isForeground) void syncActiveSession();
    return;
  }
  foreground = isForeground;
  if (!foreground) {
    if (tickSnapshot) {
      tickSnapshot = null;
      notify(tickListeners);
    }
    return;
  }
  void syncActiveSession();
}

function subscribeSession(listener: Listener): () => void {
  sessionListeners.add(listener);
  startNativeSubscriptions();
  return () => sessionListeners.delete(listener);
}

function subscribeTick(listener: Listener): () => void {
  tickListeners.add(listener);
  startNativeSubscriptions();
  return () => tickListeners.delete(listener);
}

function getSessionSnapshot(): ActiveSessionState | null {
  return sessionSnapshot;
}

function getTickSnapshot(): TimerTickEvent | null {
  return tickSnapshot;
}

export function useActiveSession(visible: boolean): ActiveSessionState | null {
  const session = useSyncExternalStore(
    subscribeSession,
    getSessionSnapshot,
    getSessionSnapshot
  );

  useEffect(() => {
    if (visible) void syncActiveSession();
  }, [visible]);

  return session;
}

export function useTimerTick(visible: boolean): TimerTickEvent | null {
  const subscribe = useCallback(
    (listener: Listener) => visible ? subscribeTick(listener) : () => {},
    [visible]
  );
  const tick = useSyncExternalStore(subscribe, getTickSnapshot, getTickSnapshot);

  useEffect(() => {
    if (visible) void syncActiveSession();
  }, [visible]);

  return visible && foreground ? tick : null;
}
