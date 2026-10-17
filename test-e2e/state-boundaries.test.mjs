import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './framework/load-typescript.js';

const { DataRefreshCoordinator } = loadTypeScript('src/state/dataRefreshCoordinator.ts');
const settle = () => new Promise(resolve => setImmediate(resolve));
const daily = date => ({ date, formattedDate: date, totalDurationSeconds: 0,
  focusCount: 0, examCount: 0, sessions: [], tasks: [], notes: [] });
const deferred = () => {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
};

function timerFixture() {
  const native = { tick: null, state: null, tickSubscriptions: 0, stateSubscriptions: 0, reads: 0 };
  const reads = [];
  const observers = [];
  const effects = [];
  const react = {
    useCallback: fn => fn,
    useEffect: fn => { effects.push(fn); },
    useSyncExternalStore: (subscribe, get) => {
      const observer = { calls: 0, get, cleanup: null };
      observer.cleanup = subscribe(() => observer.calls++);
      observers.push(observer);
      return get();
    },
  };
  const bridge = {
    YanjiTimerNative: { getActiveSession: () => {
      native.reads++; const read = deferred(); reads.push(read); return read.promise;
    } },
    onTimerStateChanged: fn => { native.stateSubscriptions++; native.state = fn; return () => { native.state = null; }; },
    onTimerTick: fn => { native.tickSubscriptions++; native.tick = fn; return () => { native.tick = null; }; },
  };
  const store = loadTypeScript('src/state/timerStore.ts', { react, '../bridge': bridge });
  return { store, native, reads, observers, effects };
}
const session = (elapsedSeconds = 0) => ({ sessionId: 'owned', subjectId: 'math', subjectName: '数学',
  mode: 'COUNTDOWN', taskId: null, startTime: 0, elapsedSeconds, remainingSeconds: 600 - elapsedSeconds,
  phase: 'FOCUS', isPaused: false, isCountdown: true });
const tick = elapsedSeconds => ({ elapsedSeconds, remainingSeconds: 600 - elapsedSeconds, phase: 'FOCUS', isPaused: false });

test('App shell reconciles a foreground transition that happened before its listener attached', () => {
  const effects = [], states = [], gates = [];
  const dummy = () => null;
  const appState = { currentState: 'background', addEventListener: () => {
    appState.currentState = 'active'; // Native cache changed before the shell could receive the event.
    return { remove() {} };
  } };
  const mocks = {
    react: {
      useEffect: fn => effects.push(fn),
      useState: initial => {
        const updates = []; states.push(updates);
        return [typeof initial === 'function' ? initial() : initial, value => updates.push(value)];
      },
    },
    'react-native': { AppState: appState, BackHandler: { addEventListener: () => ({ remove() {} }) },
      View: 'View', StatusBar: 'StatusBar', StyleSheet: { absoluteFill: {} } },
    'react-native-safe-area-context': { SafeAreaProvider: dummy, useSafeAreaInsets: () => ({ top: 0 }) },
    'react-native-blur-overlay': { BlurTarget: dummy },
    './src/theme/liquidGlass': { YanjiLiquidGlass: { targetId: 'test' } },
    './src/navigation/BottomTabBar': { BottomTabBar: dummy },
    './src/navigation/NavigationShell': { NavigationProvider: dummy,
      useNavigationState: () => ({ activeTab: 'today', settingsOpen: false, taskEditorOpen: false }),
      useNavigationActions: () => ({ closeSettings: dummy, closeTaskEditor: dummy }) },
    './src/theme/ThemeProvider': { YanjiThemeProvider: dummy,
      useYanjiTheme: () => ({ isDark: false,
        paper: { colors: { canvas: '#f6f4ef' }, immersive: {
          running: { canvas: '#15181c' }, paused: { canvas: '#1c1917' },
        } },
      }) },
    './src/bridge': { onDataChanged: () => dummy },
    './src/state/dataRefresh': { dataRefreshCoordinator: {
      setForeground: value => gates.push(value), enqueueDataChanged: dummy, handleForeground: dummy,
    } },
    './src/state/timerStore': { setTimerForeground: dummy, useActiveSession: () => null },
  };
  for (const screen of ['Today', 'Focus', 'Review', 'Settings']) {
    mocks[`./src/screens/${screen}Screen`] = { [`${screen}Screen`]: dummy };
  }
  const app = loadTypeScript('App.tsx', mocks);
  let element = app.default();
  while (element.type.name !== 'AppShell') element = element.props.children;
  element.type();
  effects[0]();
  assert.equal(gates.at(-1), true);
  assert.equal(states[0].at(-1), true);
});

test('App shell keeps one dock mounted and gates its visibility using native session transitions', () => {
  // Run the actual AppShell with deterministic hook outputs. This tests the
  // element props it produces, rather than mirroring its boolean expression.
  function harness(initialAppState = 'active') {
    const dummy = () => null;
    function MockDock() { return null; }
    const nav = { activeTab: 'focus', settingsOpen: false, taskEditorOpen: false };
    let nativeSession = null;
    const sessionReaderGates = [];
    const appState = {
      currentState: initialAppState,
      addEventListener: () => ({ remove() {} }),
    };
    const paper = {
      colors: { canvas: '#paper' },
      immersive: {
        running: { canvas: '#running' },
        paused: { canvas: '#paused' },
      },
    };
    const mocks = {
      react: {
        useEffect: () => {},
        useState: initial => [typeof initial === 'function' ? initial() : initial, dummy],
      },
      'react-native': {
        AppState: appState, BackHandler: { addEventListener: () => ({ remove() {} }) },
        View: 'View', StatusBar: 'StatusBar', StyleSheet: { absoluteFill: {} },
      },
      'react-native-safe-area-context': {
        SafeAreaProvider: dummy, useSafeAreaInsets: () => ({ top: 0 }),
      },
      'react-native-blur-overlay': { BlurTarget: dummy },
      './src/theme/liquidGlass': { YanjiLiquidGlass: { targetId: 'test' } },
      './src/navigation/BottomTabBar': { BottomTabBar: MockDock },
      './src/navigation/NavigationShell': {
        NavigationProvider: dummy,
        useNavigationState: () => nav,
        useNavigationActions: () => ({ closeSettings: dummy, closeTaskEditor: dummy }),
      },
      './src/theme/ThemeProvider': {
        YanjiThemeProvider: dummy,
        useYanjiTheme: () => ({ isDark: false, paper }),
      },
      './src/bridge': { onDataChanged: () => dummy },
      './src/state/dataRefresh': { dataRefreshCoordinator: {
        setForeground: dummy, enqueueDataChanged: dummy, handleForeground: dummy,
      } },
      './src/state/timerStore': {
        setTimerForeground: dummy,
        useActiveSession: enabled => {
          sessionReaderGates.push(enabled);
          return nativeSession;
        },
        useTimerTick: () => { throw new Error('AppShell must not subscribe to second ticks'); },
      },
    };
    for (const screen of ['Today', 'Focus', 'Review', 'Settings']) {
      mocks[`./src/screens/${screen}Screen`] = { [`${screen}Screen`]: dummy };
    }
    const app = loadTypeScript('App.tsx', mocks);
    let element = app.default();
    while (element.type.name !== 'AppShell') element = element.props.children;

    function findNodes(node, predicate, found = []) {
      if (Array.isArray(node)) {
        for (const child of node) findNodes(child, predicate, found);
      } else if (node && typeof node === 'object' && 'type' in node) {
        if (predicate(node)) found.push(node);
        findNodes(node.props?.children, predicate, found);
      }
      return found;
    }
    function render() {
      const shell = element.type();
      const docks = findNodes(shell, node => node.type === MockDock);
      const statuses = findNodes(shell, node => node.type === 'StatusBar');
      const panes = findNodes(shell, node => /^pane-(today|focus|review)$/.test(node.props?.testID ?? ''));
      assert.equal(docks.length, 1, 'hidden dock must remain mounted');
      assert.equal(statuses.length, 1);
      assert.equal(panes.length, 3, 'all three tab panes must stay mounted');
      return {
        visible: docks[0].props.visible,
        barStyle: statuses[0].props.barStyle,
        backdrop: shell.props.style.backgroundColor,
        activePanes: panes.filter(node => node.props.active).map(node => node.props.testID),
        readerGate: sessionReaderGates.at(-1),
      };
    }
    return {
      nav, render, sessionReaderGates,
      setSession: value => { nativeSession = value; },
    };
  }

  const f = harness();
  assert.deepEqual(f.render(), {
    visible: true, barStyle: 'dark-content', backdrop: '#paper',
    activePanes: ['pane-focus'], readerGate: true,
  });

  f.setSession(session(30));
  assert.deepEqual(f.render(), {
    visible: false, barStyle: 'light-content', backdrop: '#running',
    activePanes: ['pane-focus'], readerGate: true,
  });

  f.setSession({ ...session(40), isPaused: true });
  assert.deepEqual(f.render(), {
    visible: false, barStyle: 'light-content', backdrop: '#paused',
    activePanes: ['pane-focus'], readerGate: true,
  });

  f.nav.activeTab = 'today';
  assert.deepEqual(f.render(), {
    visible: true, barStyle: 'dark-content', backdrop: '#paper',
    activePanes: ['pane-today'], readerGate: false,
  });
  f.nav.activeTab = 'review';
  assert.deepEqual(f.render(), {
    visible: true, barStyle: 'dark-content', backdrop: '#paper',
    activePanes: ['pane-review'], readerGate: false,
  });

  f.nav.activeTab = 'focus';
  f.setSession({ ...session(42), phase: 'BREAK' });
  assert.equal(f.render().visible, true, 'non-focus phases must not hide the dock');
  f.setSession(null);
  assert.equal(f.render().visible, true, 'a completed/absent native session restores the dock');

  f.setSession(session(60));
  f.nav.settingsOpen = true;
  assert.deepEqual(f.render(), {
    visible: true, barStyle: 'dark-content', backdrop: '#paper',
    activePanes: [], readerGate: false,
  });
  f.nav.settingsOpen = false;
  assert.equal(f.render().visible, false, 'closing settings restores focus immersion');

  const background = harness('background');
  background.setSession(session(60));
  assert.deepEqual(background.render(), {
    visible: true, barStyle: 'dark-content', backdrop: '#paper',
    activePanes: [], readerGate: false,
  });
  assert.equal(f.sessionReaderGates.length, 9, 'one low-frequency session read per AppShell render');
});

test('native timer subscriptions are shared and ticks notify only visible clock readers', async () => {
  const f = timerFixture();
  f.store.useActiveSession(true);
  f.store.useActiveSession(false);
  f.store.useTimerTick(true);
  f.store.useTimerTick(false);
  assert.equal(f.native.tickSubscriptions, 1);
  assert.equal(f.native.stateSubscriptions, 1);
  f.reads[0].resolve(session());
  await settle();
  const before = f.observers.map(observer => observer.calls);
  f.native.tick(tick(12));
  assert.equal(f.observers[0].calls, before[0]);
  assert.equal(f.observers[1].calls, before[1]);
  assert.equal(f.observers[2].calls, before[2] + 1);
  assert.equal(f.observers[3].calls, before[3]);
});

test('background stops clock notifications and foreground resyncs the native session', async () => {
  const f = timerFixture();
  f.store.useTimerTick(true);
  f.reads[0].resolve(session());
  await settle();
  f.store.setTimerForeground(false);
  const before = f.observers[0].calls;
  f.native.tick(tick(42));
  assert.equal(f.observers[0].calls, before);
  f.store.setTimerForeground(true);
  assert.equal(f.native.reads, 2);
  f.reads[1].resolve(session(42));
  await settle();
});

test('an old hydration response cannot replace a newer native state transition', async () => {
  const f = timerFixture();
  f.store.useActiveSession(true);
  f.native.state({ state: 'ACTIVE', session: session(60) });
  f.reads[0].resolve(null);
  await settle();
  assert.equal(f.observers[0].get().elapsedSeconds, 60);
});

test('a hydration response cannot roll the display back after a newer native tick', async () => {
  const f = timerFixture();
  f.store.useActiveSession(true);
  f.store.useTimerTick(true);
  f.reads[0].resolve(session(20));
  await settle();
  const syncing = f.store.syncActiveSession();
  f.native.tick(tick(22));
  f.reads[1].resolve(session(21));
  await syncing;
  assert.equal(f.observers[1].get()?.elapsedSeconds, 22);
});
function fixture(overrides = {}) {
  const reads = { daily: [], review: [], settings: 0, exam: 0 };
  const coordinator = new DataRefreshCoordinator({
    getDailySnapshot: async date => { reads.daily.push(date); return daily(date); },
    getReviewOverview: async (...args) => { reads.review.push(args); return { totalSeconds: 0 }; },
    getUserSettings: async () => { reads.settings++; return { examDate: '' }; },
    getExamCountdown: async () => { reads.exam++; return { examDate: '', daysRemaining: 0 }; },
    ...overrides,
  });
  return { coordinator, reads };
}

test('same-date readers share one request and one native write batch causes one reread', async () => {
  const { coordinator, reads } = fixture();
  coordinator.setDailyReader('today', '2026-10-10');
  coordinator.setDailyReader('review', '2026-10-10');
  await settle();
  assert.deepEqual(reads.daily, ['2026-10-10']);
  coordinator.enqueueDataChanged('tasks');
  coordinator.enqueueDataChanged('tasks');
  coordinator.enqueueDataChanged('notes');
  await settle();
  assert.deepEqual(reads.daily, ['2026-10-10', '2026-10-10']);
});

test('hidden pages and background readers issue no reads; activation repairs their snapshots', async () => {
  const { coordinator, reads } = fixture();
  coordinator.setDailyReader('review', '2026-10-09');
  await settle();
  coordinator.setDailyReader('review', null);
  coordinator.enqueueDataChanged('sessions');
  coordinator.enqueueDataChanged('tasks');
  coordinator.handleForeground();
  await settle();
  assert.equal(reads.daily.length, 1);
  coordinator.setDailyReader('review', '2026-10-09');
  await settle();
  assert.equal(reads.daily.length, 2);
});

test('foreground gating stops native reads immediately even before React effects detach readers', async () => {
  const { coordinator, reads } = fixture();
  coordinator.setDailyReader('today', '2026-10-10');
  await settle();
  coordinator.setForeground(false);
  coordinator.enqueueDataChanged('tasks');
  coordinator.enqueueDataChanged('sessions');
  await settle();
  assert.equal(reads.daily.length, 1);
  coordinator.setForeground(true);
  coordinator.handleForeground();
  await settle();
  assert.equal(reads.daily.length, 2);
});

test('event types refresh only their dependent reads', async () => {
  const { coordinator, reads } = fixture();
  coordinator.setDailyReader('review', '2026-10-10');
  coordinator.setReviewReader('review', [{ scope: 'CALENDAR_WEEK', periodsBack: 0, anchorDate: '2026-10-10' }]);
  coordinator.setSettingsReader('today', true);
  await settle();
  coordinator.enqueueDataChanged('tasks');
  await settle();
  assert.equal(reads.daily.length, 2);
  assert.equal(reads.review.length, 1);
  assert.equal(reads.settings, 1);
  coordinator.enqueueDataChanged('sessions');
  await settle();
  assert.equal(reads.daily.length, 3);
  assert.equal(reads.review.length, 2);
  coordinator.enqueueDataChanged('settings');
  await settle();
  assert.equal(reads.daily.length, 3);
  assert.equal(reads.review.length, 2);
  assert.equal(reads.settings, 2);
  assert.equal(reads.exam, 2);
});

test('a write during a request suppresses the stale response and schedules a fresh read', async () => {
  const first = deferred(), second = deferred();
  let calls = 0, notifications = 0;
  const { coordinator } = fixture({ getDailySnapshot: () => (++calls === 1 ? first.promise : second.promise) });
  coordinator.subscribeDailySnapshot('2026-10-10', () => notifications++);
  coordinator.setDailyReader('today', '2026-10-10');
  coordinator.enqueueDataChanged('notes');
  await settle();
  first.resolve({ ...daily('2026-10-10'), totalDurationSeconds: 60 });
  await settle();
  assert.equal(calls, 2);
  assert.equal(notifications, 0);
  assert.equal(coordinator.getDailySnapshot('2026-10-10'), null);
  second.resolve({ ...daily('2026-10-10'), totalDurationSeconds: 120 });
  await settle();
  assert.equal(coordinator.getDailySnapshot('2026-10-10').totalDurationSeconds, 120);
  assert.equal(notifications, 1);
});

test('switching date suppresses the obsolete response and never rereads the hidden date', async () => {
  const old = deferred(), next = deferred();
  const calls = [];
  const { coordinator } = fixture({ getDailySnapshot: date => {
    calls.push(date); return date === '2026-10-09' ? old.promise : next.promise;
  } });
  coordinator.setDailyReader('review', '2026-10-09');
  coordinator.setDailyReader('review', '2026-10-10');
  next.resolve(daily('2026-10-10'));
  old.resolve(daily('2026-10-09'));
  await settle();
  assert.equal(coordinator.getDailySnapshot('2026-10-09'), null);
  assert.equal(coordinator.getDailySnapshot('2026-10-10').date, '2026-10-10');
  assert.deepEqual(calls, ['2026-10-09', '2026-10-10']);
});

function dataHookFixture() {
  const reads = { daily: [], review: [] };
  const effects = [];
  let observer;
  const hooks = loadTypeScript('src/state/dataRefresh.ts', {
    react: {
      useCallback: fn => fn,
      useEffect: fn => effects.push(fn),
      useSyncExternalStore: (subscribe, get) => {
        observer = { subscribe, get };
        return get();
      },
    },
    '../bridge': { YanjiDataNative: {
      getDailySnapshot: date => {
        const read = deferred(); reads.daily.push({ date, ...read }); return read.promise;
      },
      getReviewOverview: (...args) => {
        const read = deferred(); reads.review.push({ args, ...read }); return read.promise;
      },
    } },
  });
  return { hooks, reads, effects, observer: () => observer };
}

test('mounted daily hook retains loaded content through hiding, invalidation and an in-flight read', async () => {
  const f = dataHookFixture();
  const date = '2026-10-10';
  const coordinator = f.hooks.dataRefreshCoordinator;
  f.hooks.useDailySnapshot('today', date, true);
  let unsubscribe = f.observer().subscribe(() => {});
  let detach = f.effects.pop()();
  f.reads.daily[0].resolve(daily(date));
  await settle();
  const shown = f.observer().get();
  assert.ok(shown);

  // React changes the subscription before running passive reader cleanup.
  f.hooks.useDailySnapshot('today', date, false);
  unsubscribe();
  unsubscribe = f.observer().subscribe(() => {});
  detach();
  detach = f.effects.pop()();
  coordinator.enqueueDataChanged('notes');
  await coordinator.refreshDailySnapshot(date);
  await settle();
  assert.equal(f.reads.daily.length, 1, 'a mounted hidden page does not query Room');
  assert.equal(f.observer().get(), shown, 'hiding must not replace real content with loading content');

  f.hooks.useDailySnapshot('today', date, true);
  detach();
  detach = f.effects.pop()();
  assert.equal(f.reads.daily.length, 2);
  assert.equal(f.observer().get(), shown, 'keep content while reactivation refreshes');

  f.hooks.useDailySnapshot('today', date, false);
  unsubscribe();
  unsubscribe = f.observer().subscribe(() => {});
  detach();
  detach = f.effects.pop()();
  f.reads.daily[1].resolve({ ...daily(date), focusCount: 1 });
  await settle();
  assert.equal(f.observer().get(), shown, 'a superseded in-flight read cannot prune a mounted snapshot');
  assert.equal(f.reads.daily.length, 2);
  unsubscribe();
  detach();
  assert.equal(coordinator.getDailySnapshot(date), null, 'unmount releases the retained date');
});

test('mounted Review overview stays available while hidden and releases obsolete windows', async () => {
  const f = dataHookFixture();
  const request = { scope: 'CALENDAR_WEEK', periodsBack: 0, anchorDate: '2026-10-10' };
  const coordinator = f.hooks.dataRefreshCoordinator;
  f.hooks.useReviewOverview(request);
  const unsubscribe = f.observer().subscribe(() => {});
  f.hooks.useReviewDataVisibility('review', [request], true);
  const detach = f.effects.pop()();
  f.reads.review[0].resolve({ totalSeconds: 600 });
  await settle();
  const shown = f.observer().get();
  detach();
  coordinator.enqueueDataChanged('sessions');
  await coordinator.refreshReviewOverview(request);
  await settle();
  assert.equal(f.observer().get(), shown);
  assert.equal(f.reads.review.length, 1, 'hidden overview is retained without a query');
  unsubscribe();
  assert.equal(coordinator.getReviewOverview(request), null);
});

test('daily subscriptions retain only dates still mounted while Review browses history', async () => {
  const { coordinator } = fixture();
  let unsubscribe = () => {};
  for (const date of ['2026-10-01', '2026-10-02', '2026-10-03', '2026-10-04', '2026-10-05', '2026-10-06']) {
    unsubscribe();
    unsubscribe = coordinator.subscribeDailySnapshot(date, () => {});
    coordinator.setDailyReader('review', date);
    await settle();
  }
  coordinator.setDailyReader('review', null);
  for (const date of ['2026-10-01', '2026-10-02', '2026-10-03', '2026-10-04', '2026-10-05']) {
    assert.equal(coordinator.getDailySnapshot(date), null, 'obsolete dates are released');
  }
  assert.ok(coordinator.getDailySnapshot('2026-10-06'));
  unsubscribe();
  assert.equal(coordinator.getDailySnapshot('2026-10-06'), null);
});

test('changing Review scope prevents an obsolete overview from replacing the new scope', async () => {
  const old = deferred(), next = deferred();
  const week = { scope: 'CALENDAR_WEEK', periodsBack: 0, anchorDate: '2026-10-10' };
  const month = { ...week, scope: 'CALENDAR_MONTH' };
  const { coordinator } = fixture({ getReviewOverview: scope => scope === week.scope ? old.promise : next.promise });
  coordinator.setReviewReader('review', [week]);
  coordinator.setReviewReader('review', [month]);
  next.resolve({ scope: month.scope, totalSeconds: 180 });
  old.resolve({ scope: week.scope, totalSeconds: 60 });
  await settle();
  assert.equal(coordinator.getReviewOverview(week), null);
  assert.equal(coordinator.getReviewOverview(month).totalSeconds, 180);
});

test('failed reads preserve the last real snapshot and retry on later activation', async () => {
  let calls = 0;
  const { coordinator } = fixture({ getDailySnapshot: async date => {
    if (++calls === 2) throw new Error('offline');
    return { ...daily(date), totalDurationSeconds: calls * 60 };
  } });
  coordinator.setDailyReader('today', '2026-10-10');
  await settle();
  await coordinator.refreshDailySnapshot('2026-10-10');
  assert.equal(coordinator.getDailySnapshot('2026-10-10').totalDurationSeconds, 60);
  coordinator.setDailyReader('today', null);
  coordinator.setDailyReader('today', '2026-10-10');
  await settle();
  assert.equal(coordinator.getDailySnapshot('2026-10-10').totalDurationSeconds, 180);
});
