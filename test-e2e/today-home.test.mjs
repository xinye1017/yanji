import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './framework/load-typescript.js';
import { createFlashListDouble } from './framework/flash-list-double.js';

const tokens = loadTypeScript('src/theme/tokens.ts');
const theme = {
  paper: {
    colors: tokens.YanjiPaperColors.light,
    typography: tokens.YanjiPaperTypography,
    layout: tokens.YanjiPaperLayout,
    radius: tokens.YanjiPaperRadius,
    motion: tokens.YanjiPaperMotion,
  },
};
const logic = loadTypeScript('src/screens/todayViewLogic.ts');
const date = '2026-10-16';
const at = time => new Date(`${date}T${time}:00`).getTime();
const task = (changes = {}) => ({
  id: 'task-a', date, subjectId: 'math', subjectName: '数学一', subjectColor: '#356AE6',
  title: '积分练习', plannedMinutes: 90, actualMinutes: 0, completed: false,
  carriedOverToId: null, createdAt: at('08:00'),
  ...changes,
});
const session = (id, from, to, changes = {}) => ({
  id, title: '专注', subjectId: 'math', subjectName: '数学一', subjectColor: '#356AE6',
  startTime: at(from), endTime: at(to), durationSeconds: (at(to) - at(from)) / 1000,
  pauseCount: 0, isExam: false, mode: 'STOPWATCH', note: '', ...changes,
});

function nodes(tree, predicate) {
  const result = [];
  function walk(node) {
    if (Array.isArray(node)) node.forEach(walk);
    else if (node && typeof node === 'object' && 'type' in node) {
      if (predicate(node)) result.push(node);
      if (typeof node.type === 'function') walk(node.type(node.props));
      else {
        if (node.type === 'YanjiTextAction') return;
        walk(node.props.children);
      }
    }
  }
  walk(tree);
  return result;
}
function text(tree) {
  if (Array.isArray(tree)) return tree.map(text).join('');
  if (tree && typeof tree === 'object') {
    if (typeof tree.type === 'function') return text(tree.type(tree.props));
    if ('type' in tree && tree.props?.children === undefined) {
      return tree.props?.title ?? tree.props?.label ?? tree.props?.prompt ?? '';
    }
    return text(tree.props?.children);
  }
  return typeof tree === 'string' || typeof tree === 'number' ? String(tree) : '';
}
let spectrumWidth = 276;
const shared = loadTypeScript('src/components/YanjiUI.tsx', {
  react: { useState: () => [spectrumWidth, value => { spectrumWidth = value; }] },
  'react-native': { View: 'View', Text: 'Text', Pressable: 'Pressable' },
  'react-native-reanimated': {},
  'react-native-svg': {},
  '../theme/ThemeProvider': { useYanjiTheme: () => theme },
  './icons': { getYanjiIcon: name => name, YANJI_ICON_STROKE: 1.75 },
});

test('Chinese dates and durations retain truthful zero and subminute values', () => {
  assert.equal(logic.displayDate(date), '10月16日 周五');
  assert.match(logic.displayDate('2026-12-31'), /^12月31日/);
  // A daily goal is a wall-clock target, so it reads like one (`6:00`), not
  // like a timer's unit notation (`06h 00m`).
  assert.equal(logic.goalClock(21600), '6:00');
  assert.equal(logic.goalClock(21600 + 1800), '6:30');
  assert.equal(logic.compactDuration(3599), '59m');
  assert.equal(logic.compactDuration(10), '<1m');
  assert.equal(logic.taskMeta(task({ completed: true })), '实际专注 0m');
  assert.equal(logic.taskMeta(task({ actualMinutes: 45 })), '已投入 45m 预计 1h30m');
});

test('subject legend includes genuine planned subjects at zero and keeps their color', () => {
  const saved = [session('a', '09:00', '09:30')];
  const summary = logic.summarizeSubjects(saved, theme.paper.colors.secondary);
  const legend = logic.spectrumLegend(summary, [task(), task({ subjectId: 'politics', subjectName: '政治', subjectColor: '#DC2626' })]);
  assert.equal(legend.length, 2);
  assert.equal(legend[0].seconds, 1800);
  assert.equal(legend[1].seconds, 0);
  assert.equal(legend[1].color, '#DC2626');
  assert.equal(saved[0].durationSeconds, 1800);
  const reordered = logic.summarizeSubjects([
    session('b', '10:00', '10:50', { subjectId: 'english' }), ...saved,
  ], theme.paper.colors.secondary);
  assert.equal(reordered[1].id, 'math', 'series order must not depend on duration rank');
});

test('rhythm clips to each local hour, allocates paused spans faithfully, and aggregates dominant subject', () => {
  const sessions = [
    session('before', '05:45', '06:15'),
    session('cross', '09:45', '10:15'),
    session('one', '11:00', '11:10'),
    session('two', '11:10', '11:20'),
    session('other', '11:20', '11:35', { subjectId: 'english', subjectColor: '#16A34A' }),
    session('paused', '12:00', '12:40', { durationSeconds: 1200, pauseCount: 1 }),
    session('clock-mismatch', '13:00', '13:20', { durationSeconds: 300 }),
    session('last', '23:45', '24:00'),
  ];
  const buckets = logic.buildRhythmBuckets(date, sessions, theme.paper.colors.secondary);
  const hour = h => buckets.find(bucket => bucket.hour === h);
  assert.equal(buckets.length, 18);
  assert.equal(hour(6).seconds, 900);
  assert.equal(hour(9).seconds, 900);
  assert.equal(hour(10).seconds, 900);
  assert.equal(hour(11).seconds, 2100);
  assert.equal(hour(11).color, '#356AE6', 'two short math sessions together outweigh the English session');
  assert.equal(hour(12).seconds, 1200, 'paused session is faithfully allocated to its active hour');
  assert.equal(hour(13).seconds, 300, 'session with duration mismatch is faithfully allocated');
  assert.equal(hour(23).seconds, 900);
  assert.equal(logic.summarizeSubjects(sessions, theme.paper.colors.secondary).reduce((sum, item) => sum + item.seconds, 0),
    sessions.reduce((sum, item) => sum + item.durationSeconds, 0), 'timeline spans count toward saved investment');
});

test('spectrum shows goal remainder and fills distribution when no goal or over goal', () => {
  const segments = [
    { id: 'math', label: '数学', color: '#356AE6', seconds: 60 },
    { id: 'english', label: '英语', color: '#16A34A', seconds: 16 },
  ];
  const render = goalSeconds => shared.YanjiChronoSpectrum({ segments, totalSeconds: 76, goalSeconds });
  assert.equal(render(100).props.children.props.style.width, '76%');
  assert.equal(render(100).props.accessibilityValue.now, 76);
  assert.equal(render(null).props.children.props.style.width, '100%');
  assert.equal(render(50).props.children.props.style.width, '100%');
  assert.equal(render(50).props.accessibilityValue.now, 100);
  assert.match(render(50).props.accessibilityLabel, /已超出目标/);
  assert.equal(render(100).props.children.props.style.gap, tokens.YanjiPaperLayout.spectrumGap);
  const empty = shared.YanjiChronoSpectrum({ segments: [], totalSeconds: 0, goalSeconds: 100 });
  assert.equal(empty.props.children.props.style.width, '0%');
});

test('tiny spectrum progress reserves colored space and loading never announces fabricated zeros', () => {
  const segments = [
    { id: 'a', label: '数学', color: '#356AE6', seconds: 60 },
    { id: 'b', label: '英语', color: '#16A34A', seconds: 60 },
  ];
  const render = () => shared.YanjiChronoSpectrum({ segments, totalSeconds: 120, goalSeconds: 21600 });
  const tinyWidth = 276 * 120 / 21600;
  render().props.children.props.onLayout({ nativeEvent: { layout: { width: tinyWidth } } });
  const gap = render().props.children.props.style.gap;
  assert.ok(gap < tinyWidth / 2, 'gaps cannot consume all the progress width');
  const loading = shared.YanjiChronoSpectrum({ segments: [], totalSeconds: 0, goalSeconds: null, accessibilityLabel: '正在读取今日专注' });
  assert.equal(loading.props.accessibilityLabel, '正在读取今日专注');
  assert.equal(loading.props.accessibilityValue, undefined);
  spectrumWidth = 276;
});

test('task controls keep completion, editing, and focus separate; completed rows have no focus capsule', () => {
  const calls = [];
  const props = {
    title: '积分练习', subjectName: '数学一', accentColor: '#356AE6', meta: '实际专注 0m',
    onToggle: () => calls.push('toggle'), onEdit: () => calls.push('edit'), onStartFocus: () => calls.push('focus'),
  };
  const row = shared.YanjiTaskLedgerRow({ ...props, completed: false });
  const controls = nodes(row, node => node.type === 'Pressable');
  assert.equal(controls.length, 3);
  for (const control of controls) {
    control.props.onPress();
  }
  // The row is now flush along one baseline: an 18dp checkbox, the title, and a
  // circular play target. Their 44dp floor comes from `hitSlop` rather than
  // from padding, which is what let the visible box shrink to the title's edge.
  const slop = controls[0].props.hitSlop;
  assert.equal(controls[0].props.style({ pressed: false }).width, tokens.YanjiPaperLayout.taskCheckboxSize);
  assert.ok(slop && slop.top >= 8 && slop.bottom >= 8,
    'the checkbox keeps a full target through hit slop');
  const play = controls[2].props.style({ pressed: false });
  assert.equal(play.width, tokens.YanjiPaperLayout.compactActionHeight + 2);
  assert.equal(play.height, tokens.YanjiPaperLayout.compactActionHeight + 2);
  assert.deepEqual(calls, ['toggle', 'edit', 'focus']);
  const completed = shared.YanjiTaskLedgerRow({ ...props, completed: true });
  assert.equal(nodes(completed, node => node.type === 'Pressable').length, 2);
  assert.equal(nodes(completed, node => node.props.accessibilityRole === 'checkbox')[0].props.accessibilityState.checked, true);
});

test('task subject labels render as a rounded rectangle badge with the subject colour (Scheme A)', () => {
  const original = theme.paper.colors;
  try {
    for (const colors of Object.values(tokens.YanjiPaperColors)) {
      theme.paper.colors = colors;
      for (const accentColor of ['#16A34A', '#356AE6', '#DC2626']) {
        const row = shared.YanjiTaskLedgerRow({
          title: '积分练习', subjectName: '数学一', meta: '已投入 0m', accentColor,
          completed: false, onToggle() {}, onEdit() {}, onStartFocus() {},
        });
        const label = nodes(row, n => n.type === 'Text' && n.props.children === '数学一')[0];
        assert.equal(label.props.style.color, accentColor, 'Scheme A: subject label text carries the subject color');
        const badge = nodes(row, n => n.type === 'View' && n.props.style?.backgroundColor === tokens.toSubtleColor(accentColor, 0.14))[0];
        assert.ok(badge, 'Scheme A: subject label lives in a tinted rounded-rectangle badge');
        assert.equal(badge.props.style.borderRadius, theme.paper.radius.tag);

        const completedRow = shared.YanjiTaskLedgerRow({
          title: '积分练习', subjectName: '数学一', meta: '已投入 0m', accentColor,
          completed: true, onToggle() {}, onEdit() {}, onStartFocus() {},
        });
        const completedLabel = nodes(completedRow, n => n.type === 'Text' && n.props.children === '数学一')[0];
        assert.equal(completedLabel.props.style.color, colors.secondary, 'completed task recedes to secondary text');
      }
    }
  } finally {
    theme.paper.colors = original;
  }
});

function screenHarness(snapshot, bridge = {}, { readFailed = false } = {}) {
  const states = [];
  const refs = [];
  const sharedValues = [];
  const effects = [];
  const callbacks = [];
  const calls = [];
  let stateIndex = 0;
  let refIndex = 0;
  let effectIndex = 0;
  let callbackIndex = 0;
  let sharedValueIndex = 0;
  const dataListeners = [];
  const names = ['YanjiBadge', 'YanjiCaptureCard', 'YanjiCard', 'YanjiChronoSpectrum', 'YanjiDayRhythmChart',
    'YanjiEditorialHeader', 'YanjiEmptyLedgerRow', 'YanjiHairline', 'YanjiIcon', 'YanjiIconButton',
    'YanjiPaperSectionHeading', 'YanjiPaperSegmentedControl', 'YanjiPaperSheetHeading',
    'YanjiPrimaryButton', 'YanjiSectionToggle', 'YanjiSubjectProportionRow',
    'YanjiStaleDataNotice', 'YanjiTaskLedgerRow', 'YanjiTextAction'];
  const ui = Object.fromEntries(names.map(name => [name, name]));
  // The page's real list, replaced by a double that windows its cells. See
  // `framework/flash-list-double.js`: it mounts a bounded window and exposes how
  // many rows it was handed, which is what lets a test tell virtualization apart
  // from data truncation.
  const flashList = createFlashListDouble();
  let active = null;
  let tick;
  let visible = true;
  let tickReads = 0;
  // Retry paths are asserted per test, so they start as no-ops that record
  // nothing; `setRefreshDaily` installs the spy that a test cares about.
  let refreshDaily = async () => {};
  const refreshSettings = async () => {};
  let carryOverTasks = [];
  let getCarryOverImpl = async () => carryOverTasks;
  let carryTaskImpl = async id => task({ id: `copy-${id}` });
  let toggleTaskImpl = async () => true;
  const bridgeMethods = {
    ...bridge,
    getCarryOverTasks: (...args) => {
      calls.push(['getCarryOverTasks']);
      return getCarryOverImpl(...args);
    },
    carryTaskToToday: (...args) => {
      calls.push(['carryTaskToToday', ...args]);
      return carryTaskImpl(...args);
    },
    toggleTask: (...args) => {
      calls.push(['toggleTask', ...args]);
      return toggleTaskImpl(...args);
    },
  };
  const { TodayScreen } = loadTypeScript('src/screens/TodayScreen.tsx', {
    react: {
      useState: initial => {
        const i = stateIndex++;
        if (!(i in states)) states[i] = typeof initial === 'function' ? initial() : initial;
        return [states[i], next => { states[i] = typeof next === 'function' ? next(states[i]) : next; }];
      },
      useMemo: fn => fn(),
      useCallback: (fn, deps) => {
        const i = callbackIndex++;
        const previous = callbacks[i];
        const unchanged = previous && deps && previous.deps
          && deps.length === previous.deps.length
          && deps.every((value, itemIndex) => Object.is(value, previous.deps[itemIndex]));
        if (!unchanged) callbacks[i] = { fn, deps };
        return callbacks[i].fn;
      },
      useRef: value => {
        const i = refIndex++;
        if (!(i in refs)) refs[i] = { current: value };
        return refs[i];
      },
      useEffect: (effect, deps) => {
        const i = effectIndex++;
        const previous = effects[i];
        const unchanged = previous && deps && previous.deps
          && deps.length === previous.deps.length
          && deps.every((value, itemIndex) => Object.is(value, previous.deps[itemIndex]));
        if (!unchanged) effects[i] = { effect, deps, ran: false };
      },
    },
    'react-native': {
      View: 'View', Text: 'Text', Pressable: 'Pressable', ScrollView: 'ScrollView', Modal: 'Modal',
      RefreshControl: 'RefreshControl',
      useWindowDimensions: () => ({ width: 390, height: 844, fontScale: 1, scale: 2 }),
    },
    '@shopify/flash-list': flashList,
    'react-native-reanimated': {
      Easing: { out: value => value, in: value => value, cubic: 'cubic' },
      runOnJS: callback => callback,
      useAnimatedStyle: style => style(),
      useReducedMotion: () => true,
      useSharedValue: initial => {
        const i = sharedValueIndex++;
        if (!(i in sharedValues)) sharedValues[i] = { value: initial };
        return sharedValues[i];
      },
      withTiming: value => value,
      default: { View: 'AnimatedView' },
    },
    'react-native-safe-area-context': { useSafeAreaInsets: () => ({ top: 24, bottom: 0 }) },
    '../bridge': {
      YanjiDataNative: bridgeMethods,
      onDataChanged: callback => {
        dataListeners.push(callback);
        return () => {
          const index = dataListeners.indexOf(callback);
          if (index >= 0) dataListeners.splice(index, 1);
        };
      },
    },
    '../components/YanjiUI': ui,
    '../components/SettingsSheets': { DailyGoalSheet: 'DailyGoalSheet' },
    '../components/TodayScheduleCard': { TodayScheduleCard: 'TodayScheduleCard' },
    '../components/RecordMomentModal': { RecordMomentModal: 'RecordMomentModal' },
    '../components/LiveSessionReadout': { LiveSessionReadout: 'LiveSessionReadout', LiveSessionCapsule: 'LiveSessionCapsule' },
    '../components/TimelineCompactRow': { TimelineCompactRow: 'TimelineCompactRow' },
    '../components/TimelineActionPopover': { TimelineActionPopover: 'TimelineActionPopover' },
    '../components/TaskEditorModal': { TaskEditorModal: 'TaskEditorModal' },
    '../navigation/NavigationShell': {
      useNavigationActions: () => ({
        selectTab: tab => calls.push(['tab', tab]), requestFocusPreset: preset => calls.push(['preset', preset]),
        openSettings: () => calls.push(['openSettings']), openTimetable: () => calls.push(['openTimetable']),
        openTaskEditor: () => {}, closeTaskEditor: () => {},
      }),
      useNavigationState: () => ({ taskEditorOpen: false }),
    },
    '../navigation/useBottomTabLayout': { useBottomTabLayout: () => ({ contentPadding: 96 }) },
    '../theme/ThemeProvider': { useYanjiTheme: () => theme },
    '../state/dataRefresh': {
      useDailySnapshot: () => snapshot,
      useDailyReadFailed: () => readFailed,
      useSettingsSnapshot: () => ({ settings: { dailyGoalHours: 6 }, examCountdown: { examDate: '2027-12-25', daysRemaining: 435 } }),
      refreshDailySnapshot: async date => refreshDaily(date),
      refreshSettingsSnapshot: async () => refreshSettings(),
    },
    '../state/timerStore': {
      useActiveSession: () => active, useTimerTick: () => { tickReads++; return tick; },
      syncActiveSession: async () => {},
    },
  }, {
    Date: class extends Date { constructor(...args) { super(...(args.length ? args : [`${date}T15:00:00`])); } },
    setInterval: () => 1,
    clearInterval: () => {},
  });
  async function flush(turns = 4) {
    for (let turn = 0; turn < turns; turn += 1) {
      await new Promise(resolve => setTimeout(resolve, 0));
    }
  }

  function render() {
    stateIndex = 0;
    refIndex = 0;
    effectIndex = 0;
    callbackIndex = 0;
    sharedValueIndex = 0;
    return TodayScreen({ visible, foregroundRevision: 0 });
  }

  function pendingState() {
    return states.slice(0, 26).map(value => value instanceof Set ? `Set(${[...value].join(',')})` : value);
  }

  function runPendingEffects(cleanupBeforeRun = false) {
    for (const effect of effects.filter(item => item && !item.ran)) {
      if (cleanupBeforeRun) effect.cleanup?.();
      effect.ran = true;
      const cleanup = effect.effect();
      effect.cleanup = typeof cleanup === 'function' ? cleanup : undefined;
    }
  }

  async function mount() {
    render();
    for (let pass = 0; pass < 4; pass += 1) {
      runPendingEffects(pass > 0);
      await flush();
      render();
    }
    return render();
  }

  async function rerender() {
    render();
    runPendingEffects(true);
    await flush();
    render();
    runPendingEffects();
    await flush();
    return render();
  }

  return {
    calls, setActive: value => { active = value; }, get tickReads() { return tickReads; },
    setTick: value => { tick = value; },
    setVisible: value => { visible = value; },
    setRefreshDaily: next => { refreshDaily = next; },
    setCarryOverTasks: next => {
      carryOverTasks = next;
      getCarryOverImpl = async () => carryOverTasks;
    },
    setGetCarryOverTasks: next => { getCarryOverImpl = next; },
    setCarryTaskToToday: next => { carryTaskImpl = next; },
    setToggleTask: next => { toggleTaskImpl = next; },
    emitDataChanged: type => dataListeners.forEach(callback => callback({ type })),
    pendingState,
    render,
    mount,
    rerender,
    flush,
    /** The list double: how many rows were handed over, and how many mounted. */
    flashList,
    /**
     * The list as it was actually given.
     *
     * `FlashList` is a function component, so it only runs when the tree is
     * walked — the harness's `nodes` helper is what instantiates it. This walks
     * the current render first, so a caller that only wants the list does not
     * have to know that detail.
     */
    listRender: () => {
      nodes(render(), () => false);
      return flashList.lastRender('scroll-today');
    },
  };
}

const snapshot = changes => ({ date, totalDurationSeconds: 0, focusCount: 0, focusParticipationCount: 0, examCount: 0, sessions: [], notes: [], tasks: [], ...changes });

test('production homepage routes free focus and running focus without a page-level tick read', () => {
  const harness = screenHarness(snapshot());
  let tree = harness.render();
  nodes(tree, node => node.type === 'YanjiPrimaryButton' && node.props.label === '开始一轮专注')[0].props.onPress();
  assert.deepEqual(harness.calls, [['tab', 'focus']]);
  harness.setActive({ sessionId: 'active', subjectName: '数学', isPaused: true, isCountdown: false, elapsedSeconds: 120 });
  tree = harness.render();
  assert.equal(harness.tickReads, 0, 'the page root never subscribes to timer ticks');
  assert.equal(nodes(tree, node => node.type === 'YanjiPrimaryButton' && node.props.label === '开始一轮专注').length, 0);
  nodes(tree, node => node.type === 'Pressable' && node.props.accessibilityLabel === '返回专注工作台')[0].props.onPress();
  assert.deepEqual(harness.calls, [['tab', 'focus'], ['tab', 'focus']]);
});

test('pending tasks demote the free-focus CTA instead of removing it', () => {
  const harness = screenHarness(snapshot({ tasks: [task()] }));
  const tree = harness.render();
  const cta = nodes(tree, node => node.type === 'YanjiPrimaryButton' && node.props.label === '开始一轮专注')[0];
  assert.ok(cta, 'a planned day still offers a free session');
  assert.equal(cta.props.variant, 'secondary', 'it yields the primary tier to the plan rows');
  cta.props.onPress();
  assert.deepEqual(harness.calls, [['tab', 'focus']]);
});

test('the running session is handed to the live readout leaf, not ticked at the page root', () => {
  // The clock itself lives in the leaf (covered by live-session-readout.test.mjs).
  // What the page owns is the hand-off: the active snapshot goes to the readout
  // and the page root still never subscribes to ticks.
  const harness = screenHarness(snapshot({ totalDurationSeconds: 3600 }));
  const active = {
    sessionId: 'active', subjectName: '概率论与数理统计', isPaused: false,
    isCountdown: true, remainingSeconds: 1200, elapsedSeconds: 60,
  };
  harness.setActive(active);
  const tree = harness.render();
  const readout = nodes(tree, node => node.type === 'LiveSessionReadout')[0];
  assert.ok(readout, 'the active block mounts the live readout');
  assert.equal(readout.props.session, active, 'the leaf owns the clock, not the page');
  assert.equal(harness.tickReads, 0, 'the page root never subscribes to timer ticks');
  const saved = nodes(tree, n => n.props.accessibilityLabel === '今日已保存专注 1小时');
  assert.equal(saved.length, 1, 'a live tick does not inflate the persisted daily result');
});

test('production homepage shows truthful task metadata and expands rhythm only on request', () => {
  const completed = task({ id: 'done', completed: true });
  const pending = task();
  const harness = screenHarness(snapshot({ tasks: [completed, pending] }));
  let tree = harness.render();
  const rows = nodes(tree, node => node.type === 'YanjiTaskLedgerRow');
  assert.equal(rows[0].props.completed, false);
  assert.equal(rows[1].props.completed, true);
  assert.equal(rows[1].props.meta, '实际专注 0m');
  assert.equal(rows[0].props.accentColor, pending.subjectColor);
  rows[0].props.onStartFocus();
  assert.equal(harness.calls[0][1].taskId, pending.id);
  assert.equal(nodes(tree, node => node.type === 'YanjiPaperSegmentedControl').length, 0);
  // The expand control lives in the section heading's accessory slot, so the
  // walk has to enter that prop rather than the heading's children.
  const heading = () => nodes(tree, node => node.type === 'YanjiPaperSectionHeading' && node.props.title === '今日学习节奏')[0];
  const toggle = () => nodes(heading().props.right, node => node.type === 'Pressable')[0];
  toggle().props.onPress();
  tree = harness.render();
  assert.equal(nodes(tree, node => node.type === 'YanjiPaperSegmentedControl').length, 0,
    'expanding reveals the day parts directly, with no view switcher');
  assert.match(text(tree), /上午/, 'the expanded area is the 专注时段 strip');
  assert.equal(toggle().props.accessibilityState.expanded, true);
});

test('the expand control is named, and the name flips when it is collapsed', () => {
  const harness = screenHarness(snapshot());
  const heading = () => nodes(harness.render(), node => node.type === 'YanjiPaperSectionHeading' && node.props.title === '今日学习节奏')[0];
  const toggle = () => nodes(heading().props.right, node => node.type === 'Pressable')[0];
  assert.equal(toggle().props.accessibilityLabel, '展开今日学习节奏详情');
  toggle().props.onPress();
  assert.equal(toggle().props.accessibilityLabel, '收起今日学习节奏详情');
  toggle().props.onPress();
  assert.equal(toggle().props.accessibilityLabel, '展开今日学习节奏详情',
    'collapsing names the action that is available, not the state just left');
});

test('the rhythm section states its own window so the summary cannot overclaim', () => {
  const harness = screenHarness(snapshot());
  const tree = harness.render();
  // The collapsed section must name what it measures and what the strip means,
  // so the reader does not have to expand it to find out what the dots are.
  const heading = nodes(tree, node => node.type === 'YanjiPaperSectionHeading' && node.props.title === '今日学习节奏')[0];
  assert.ok(heading, 'the section must be a real section heading, not a bare caption row');
  const chart = nodes(tree, node => node.type === 'YanjiDayRhythmChart')[0];
  assert.equal(chart.props.startLabel, '06:00');
  assert.equal(chart.props.endLabel, '24:00');
  assert.equal(chart.props.buckets.length, 18);
  // The axis range is the section's scope. It sits inside a subtree that is
  // hidden from the a11y tree, so the chart must publish it separately or a
  // screen-reader user cannot learn it at all.
  assert.match(chart.props.accessibilityLabel ?? '', /06:00/);
  assert.match(chart.props.accessibilityLabel ?? '', /24:00/);
  assert.match(chart.props.accessibilityLabel, /暂无连续专注记录/);
  assert.doesNotMatch(text(tree), /暂无连续专注记录/);
});

test('the rhythm summary is spoken without displaying text below the chart', () => {
  const sessions = [session('a', '09:00', '09:40'), session('b', '14:00', '14:10')];
  const tree = screenHarness(snapshot({ sessions, totalDurationSeconds: 3000, focusCount: 2 })).render();
  const chart = nodes(tree, node => node.type === 'YanjiDayRhythmChart')[0];
  assert.match(chart.props.accessibilityLabel, /06:00 之后已确认 50 分钟 连续专注/);
  assert.match(chart.props.accessibilityLabel, /最集中的是 09:00–10:00/);
  assert.doesNotMatch(text(tree), /已确认.*连续专注|最集中的是/);
});

test('a pre-dawn session is excluded from the strip and the summary says so', () => {
  // The window starts at 06:00. A 05:00 session is real saved focus, but it is
  // not in the strip, so the sentence must not read as if it covered the day.
  const sessions = [session('dawn', '05:00', '05:40'), session('morning', '09:00', '09:30')];
  const tree = screenHarness(snapshot({ sessions, totalDurationSeconds: 4200, focusCount: 2 })).render();
  const summary = nodes(tree, node => node.type === 'YanjiDayRhythmChart')[0].props.accessibilityLabel;
  assert.match(summary, /06:00 之后/, 'the summary must name its window');
  assert.match(summary, /30 分钟/, 'only the in-window 30 分钟 is claimed, not the pre-dawn 40 分钟');
});

test('an empty rhythm strip marks no hour at all', () => {
  // A lone "current hour" tick on a day with no data is the same unexplained
  // mark the section was rebuilt to remove, so nothing may be painted. This is
  // asserted as geometry (`rhythmSlots`) rather than by inspecting the rendered
  // style objects, so no test-only prop has to exist on the component.
  const empty = Array.from({ length: 18 }, (_, i) => ({ hour: i + 6, seconds: 0 }));
  const slots = shared.rhythmSlots(empty, 20, tokens.YanjiPaperLayout.rhythmChartHeight,
    tokens.YanjiPaperLayout.rhythmEmptyHeight);
  assert.equal(slots.length, 18, 'every hour keeps its slot so the axis stays a clock');
  assert.deepEqual(slots.filter(slot => slot.height > 0), [],
    'no hour may draw a mark on an empty day');
  assert.deepEqual(slots.filter(slot => slot.isNow), [],
    'and the current hour is not singled out when there is nothing to relate it to');
});

test('the chart publishes the axis range as a spoken node, not just a prop', () => {
  // The range sits inside a `no-hide-descendants` subtree as painted text, so it
  // only reaches a screen reader if the component itself renders an accessible
  // node carrying it. Asserting the prop TodayScreen passes would not catch that
  // node being removed, so this walks the rendered component.
  const buckets = Array.from({ length: 18 }, (_, i) => ({ hour: i + 6, seconds: 600 }));
  const chart = shared.YanjiDayRhythmChart({
    buckets, currentHour: 20, startLabel: '06:00', endLabel: '24:00',
    accessibilityLabel: '今日学习节奏，时间范围 06:00 至 24:00。已确认 3h。',
  });
  const spoken = nodes(chart, node => node.props?.accessible === true && typeof node.props?.accessibilityLabel === 'string')[0];
  assert.ok(spoken, 'the chart must render an accessible node for its range');
  assert.match(spoken.props.accessibilityLabel, /06:00/);
  assert.match(spoken.props.accessibilityLabel, /24:00/);
  assert.equal(spoken.props.accessibilityRole, 'text');
});

test('the current-hour tick appears once the strip has real data', () => {
  const buckets = Array.from({ length: 18 }, (_, i) => ({ hour: i + 6, seconds: 0 }));
  buckets[4] = { hour: 10, seconds: 1800, color: '#356AE6' };
  const slots = shared.rhythmSlots(buckets, 20, tokens.YanjiPaperLayout.rhythmChartHeight,
    tokens.YanjiPaperLayout.rhythmEmptyHeight);
  assert.deepEqual(slots.filter(slot => slot.height > 0).map(slot => slot.hour), [10, 20],
    'the measured hour and the current hour are the only marks');
  assert.deepEqual(slots.filter(slot => slot.isNow).map(slot => slot.hour), [20]);
  // The measured bar is full height: 1800s is this strip's scale floor.
  assert.equal(slots.find(slot => slot.hour === 10).height, tokens.YanjiPaperLayout.rhythmChartHeight);
});

test('production homepage keeps loading distinct from an empty saved day', () => {
  const tree = screenHarness(null).render();
  assert.match(text(tree), /正在读取今日记录/);
  assert.equal(nodes(tree, node => node.type === 'YanjiPaperSectionHeading')[0].props.countText, '—');
  assert.equal(nodes(tree, node => node.type === 'YanjiChronoSpectrum')[0].props.accessibilityLabel, '正在读取今日专注');
});

test('the goal opens a page-owned sheet that writes the same stored value', async () => {
  // A goal is a number the user adjusts while looking at the day's progress, so
  // it opens where the number is. The sheet reuses the same component and the
  // same Room column as Settings — one writer, two doors.
  const writes = [];
  const harness = screenHarness(snapshot(), {
    updateUserSettings: async settings => { writes.push(settings); return true; },
  });
  let tree = harness.render();
  const goalTrigger = nodes(tree, node => node.props.accessibilityLabel === '调整日常学习目标')[0];
  assert.ok(goalTrigger, 'the metric block still offers a way to change the goal');
  goalTrigger.props.onPress();
  tree = harness.render();
  const sheet = nodes(tree, node => node.type === 'DailyGoalSheet')[0];
  assert.ok(sheet, 'the goal opens without leaving Today');
  assert.equal(sheet.props.visible, true);
  assert.equal(sheet.props.initialHours, 6, 'the sheet starts from the stored goal');
  assert.deepEqual(harness.calls, [], 'no navigation is involved');

  sheet.props.onSave(450);
  await harness.flush();
  tree = harness.render();
  assert.deepEqual(JSON.parse(JSON.stringify(writes)), [{ dailyGoalHours: 7.5 }],
    'the sheet writes the same column Settings writes');
  assert.equal(nodes(tree, node => node.type === 'DailyGoalSheet')[0].props.visible, false,
    'a saved goal closes the sheet');
  assert.match(text(tree), /目标 6:00/, 'the metric still reports the stored goal');
});

test('the timetable is reachable from Today and a block starts as a focus preset', () => {
  // The schedule used to be reachable only through Settings. It is now a
  // section of the day it governs: editing opens the timetable, and a block
  // with an enabled subject starts a focus session without leaving Today.
  const harness = screenHarness(snapshot());
  const tree = harness.render();
  const card = nodes(tree, node => node.type === 'TodayScheduleCard')[0];
  assert.ok(card, 'Today renders the timetable section');
  assert.equal(card.props.date, date);
  assert.equal(card.props.visible, true);
  card.props.onEdit();
  assert.deepEqual(harness.calls, [['openTimetable']], 'editing opens the timetable overlay');
  const preset = { taskId: null, subjectId: 'math', subjectName: '数学一', title: '积分练习', plannedMinutes: 45 };
  card.props.onStart(preset);
  assert.deepEqual(harness.calls.at(-1), ['preset', preset]);
});

test('the journal heading carries order and fold, and the plan keeps the reading order', () => {
  // The journal is an unboxed timeline section. Its heading accessory is the
  // reading order and the fold; the create entry moved into the list itself, so
  // it is no longer a text link competing with the title.
  const tree = screenHarness(snapshot()).render();
  const headings = nodes(tree, node => node.type === 'YanjiPaperSectionHeading');
  const journalHeading = headings.find(node => node.props.title === '今日随笔');
  assert.ok(journalHeading, 'the journal section has its own section heading');
  assert.equal(journalHeading.props.countText, '0 条', 'an empty day still states its count');

  // With nothing to order or fold, the accessory renders nothing at all rather
  // than two controls that cannot do anything.
  assert.equal(journalHeading.props.right, undefined,
    'no order/fold control is offered for an empty journal');

  // The plan keeps its own text action: adding a task is still the plan's one
  // entry, and it has not been moved into the row list.
  const planHeading = headings.find(node => node.props.title === '今日计划');
  const planAdd = nodes(planHeading.props.right, node => node.type === 'YanjiTextAction')[0];
  assert.equal(planAdd.props.tone, undefined, 'the plan action keeps the section accent');
  assert.equal(planAdd.props.label, '新建');

  // The write entry is the card at the end of the list, present on an empty day
  // because it *is* the empty state.
  const capture = nodes(tree, node => node.type === 'YanjiCaptureCard')[0];
  assert.ok(capture, 'the journal offers its capture card even with no notes');
  assert.equal(capture.props.title, '记录此刻');
  assert.equal(capture.props.hint, '一个想法、一处卡点、一点收获');
  assert.equal(capture.props.accessibilityLabel, '记录此刻，新建今日随笔');
  assert.doesNotMatch(text(tree), /还没有记录，想到什么就写一条/,
    'the invitation replaces the old empty-state sentence instead of repeating it');

  // Nothing on the page is lifted above the plan.
  const cards = nodes(tree, node => node.type === 'YanjiCard');
  for (const card of cards) {
    assert.notEqual(card.props.variant, 'hero', 'no page-level card may be lifted above the plan');
  }
  assert.match(text(tree), /还没有安排今天的计划/);
});

test('the journal orders and folds on request instead of truncating at two', () => {
  const notes = ['08:15', '11:40', '15:12', '17:56'].map((time, index) => ({
    id: `note-${index}`, date, timestamp: at(time), content: `记录 ${time}`,
    category: index % 2 ? 'DOUBT' : 'INSPIRATION', isFavorite: false, isResolved: false,
  }));
  const h = screenHarness(snapshot({ notes }));
  const rows = tree => nodes(tree, node => node.type === 'TimelineCompactRow');
  const heading = tree => nodes(tree, node => node.type === 'YanjiPaperSectionHeading')
    .find(node => node.props.title === '今日随笔');
  const control = (tree, testID) => nodes(heading(tree).props.right, node => node.props.testID === testID)[0];

  // All four are shown by default — nothing is hidden without being asked.
  // The rhythm row now sits above the journal, so the window needs one scroll
  // to mount the last note; the data itself was never truncated.
  h.listRender();
  h.flashList.scroll(2, 'scroll-today');
  let tree = h.render();
  assert.deepEqual(rows(tree).map(row => row.props.content),
    ['记录 17:56', '记录 15:12', '记录 11:40', '记录 08:15'], 'newest first, all of them');
  assert.equal(heading(tree).props.countText, '4 条');

  // 倒序 states the order currently in effect, not the order it would switch to.
  assert.equal(control(tree, 'journal-order').props.label, '倒序');
  assert.equal(control(tree, 'journal-order').props.icon, 'sortNewest');
  assert.equal(control(tree, 'journal-fold').props.label, '收起');
  assert.equal(control(tree, 'journal-fold').props.accessibilityState.expanded, true);

  // Folding keeps the newest two and says how many are behind the fold.
  control(tree, 'journal-fold').props.onPress();
  tree = h.render();
  assert.deepEqual(rows(tree).map(row => row.props.content), ['记录 17:56', '记录 15:12']);
  assert.equal(control(tree, 'journal-fold').props.label, '展开');
  assert.equal(control(tree, 'journal-fold').props.accessibilityState.expanded, false);
  assert.match(control(tree, 'journal-fold').props.accessibilityHint, /全部 4 条/);
  assert.equal(heading(tree).props.countText, '4 条', 'the count still reports the whole day');

  // Order flips to oldest-first and the folded window follows that order.
  control(tree, 'journal-order').props.onPress();
  tree = h.render();
  assert.equal(control(tree, 'journal-order').props.label, '正序');
  assert.equal(control(tree, 'journal-order').props.icon, 'sortOldest');
  assert.deepEqual(rows(tree).map(row => row.props.content), ['记录 08:15', '记录 11:40'],
    'the fold shows the first two of the order in effect');

  // The capture card is always present, and the fold never hides it.
  assert.equal(nodes(tree, node => node.type === 'YanjiCaptureCard').length, 1);
});

test('the capture card is the first row under the journal heading, before any note', () => {
  // Reading order inside the section: heading → write → notes. The entry used
  // to sit at the end of the list, which meant a long journal pushed it below
  // the fold. Writing is the section's own action, so it stays where the eye
  // lands first and never moves as notes accumulate.
  const notes = ['08:15', '17:56'].map((time, index) => ({
    id: `note-${index}`, date, timestamp: at(time), content: `记录 ${time}`,
    category: 'INSPIRATION', isFavorite: false, isResolved: false,
  }));
  const orderOf = tree => {
    // Walk the section's own subtree in document order and note which of the
    // two markers appears first; this is the rendered order, not a prop.
    const seen = [];
    const walk = node => {
      if (Array.isArray(node)) return node.forEach(walk);
      if (!node || typeof node !== 'object' || !('type' in node)) return;
      if (node.type === 'YanjiCaptureCard') seen.push('capture');
      if (node.type === 'TimelineCompactRow') seen.push('note');
      if (typeof node.type === 'function') walk(node.type(node.props));
      else walk(node.props?.children);
    };
    walk(tree);
    return seen;
  };

  const withNotes = orderOf(screenHarness(snapshot({ notes })).render());
  assert.deepEqual(withNotes, ['capture', 'note', 'note'],
    'the write entry comes first, then the notes beneath it');

  // An empty day has no notes at all, and the card is the whole section body.
  assert.deepEqual(orderOf(screenHarness(snapshot()).render()), ['capture']);

  // Folding removes rows but must not move the entry out of first position.
  const many = ['08:15', '11:40', '15:12', '17:56'].map((time, index) => ({
    id: `note-many-${index}`, date, timestamp: at(time), content: `记录 ${time}`,
    category: 'INSPIRATION', isFavorite: false, isResolved: false,
  }));
  const h = screenHarness(snapshot({ notes: many }));
  // The controls live in the heading's accessory slot, which is a prop rather
  // than a child, so the walk has to enter it explicitly.
  const fold = () => {
    const heading = nodes(h.render(), node => node.type === 'YanjiPaperSectionHeading'
      && node.props.title === '今日随笔')[0];
    return nodes(heading.props.right, node => node.props.testID === 'journal-fold')[0];
  };
  assert.ok(fold(), 'four notes make folding available');
  fold().props.onPress();
  assert.deepEqual(orderOf(h.render()), ['capture', 'note', 'note'],
    'a folded journal still opens with the write entry');
});

test('今日学习节奏 reads between the plan and the journal, not after every note', () => {
  // The rhythm section used to be the list footer, so a long journal buried it
  // below every note. It now closes the plan half of the page.
  const notes = ['08:15', '11:40', '15:12', '17:56'].map((time, index) => ({
    id: `rhythm-note-${index}`, date, timestamp: at(time), content: `记录 ${time}`,
    category: 'INSPIRATION', isFavorite: false, isResolved: false,
  }));
  const h = screenHarness(snapshot({ notes, tasks: [task(), task({ id: 'task-2', title: '计划二' })] }));
  const kinds = h.listRender().types;
  const rhythmAt = kinds.indexOf('rhythm');
  const headingAt = kinds.indexOf('journal-heading');
  assert.ok(rhythmAt > kinds.lastIndexOf('task'), 'the rhythm follows every plan row');
  assert.equal(rhythmAt, headingAt - 1, 'and sits immediately before the journal heading');
  assert.equal(kinds.indexOf('note'), headingAt + 2, 'notes stay below the heading and its capture entry');
});

test('the page section headings use the page type scale, not the compact one', () => {
  // 「今日计划」「今日随笔」「今日学习节奏」 are the page's three group titles. They
  // were on the compact sheet scale (13/900), which made them sit at the same
  // size as the metadata around them. The page tier is larger and stays a
  // separate token so sheet-internal headings do not grow with it.
  const tree = screenHarness(snapshot()).render();
  const pageHeadings = nodes(tree, node => node.type === 'YanjiPaperSectionHeading');
  assert.equal(pageHeadings.length, 3, 'the page has exactly its three group headings');
  for (const heading of pageHeadings) {
    assert.equal(heading.props.titleStyle, tokens.YanjiPaperTypography.titlePageSection,
      `${heading.props.title} must use the page section scale`);
  }
  assert.ok(tokens.YanjiPaperTypography.titlePageSection.fontSize
    > tokens.YanjiPaperTypography.titleSection.fontSize,
    'the page tier is larger than the compact sheet tier');
  assert.equal(tokens.YanjiPaperTypography.titlePageSection.fontWeight, '900',
    'a group title still outranks the body by weight');
});

test('order and fold controls appear only when they can do something', () => {
  const note = time => ({ id: `n-${time}`, date, timestamp: at(time), content: `记录 ${time}`, category: 'INSPIRATION' });
  const rightOf = count => {
    const tree = screenHarness(snapshot({ notes: Array.from({ length: count }, (_, i) => note(`0${i + 8}:00`)) })).render();
    const heading = nodes(tree, node => node.type === 'YanjiPaperSectionHeading')
      .find(node => node.props.title === '今日随笔');
    return heading.props.right;
  };
  assert.equal(rightOf(1), undefined, 'one note has nothing to order or fold');
  const two = nodes(rightOf(2), node => node.props.testID === 'journal-order');
  assert.equal(two.length, 1, 'two notes can be ordered');
  assert.equal(nodes(rightOf(2), node => node.props.testID === 'journal-fold').length, 0,
    'but two notes are not worth folding');
  assert.equal(nodes(rightOf(3), node => node.props.testID === 'journal-fold').length, 1,
    'a third note makes folding meaningful');
});


test('the journal section renders one compact timeline row per note', () => {
  const note = {
    id: 'note-1',
    timestamp: at('10:30'),
    content: '长难句核心词汇总结完毕',
    category: 'INSPIRATION',
    isFavorite: true,
    isResolved: false,
  };
  const tree = screenHarness(snapshot({ notes: [note] })).render();
  const item = nodes(tree, node => node.type === 'TimelineCompactRow')[0];
  assert.equal(item.props.timeLabel, '10:30');
  assert.equal(item.props.content, note.content);
  assert.equal(item.props.tagLabel, '灵感');
  assert.equal(item.props.tagIcon, 'inspiration');
  assert.equal(item.props.tagTone, 'positive');
  assert.equal(item.props.isFirst, true);
  assert.equal(item.props.isLast, true);
  assert.equal(typeof item.props.action.onOpen, 'function');
  // The row no longer owns favorite/edit/delete; the anchored menu does.
  assert.equal('onToggleFavorite' in item.props, false);
  assert.equal('onToggleResolved' in item.props, false);

  const doubt = { ...note, id: 'note-doubt', category: 'DOUBT', isResolved: false };
  const doubtItem = nodes(screenHarness(snapshot({ notes: [doubt] })).render(), n => n.type === 'TimelineCompactRow')[0];
  assert.equal(doubtItem.props.tagTone, 'brand');
  assert.equal(doubtItem.props.tagIcon, 'doubt');
  assert.match(doubtItem.props.tagLabel, /疑点/);
});

/** The popover closes first, then the chosen command runs — mimic that order. */
const pressMenuAction = (menu, label) => {
  const action = menu.props.actions.find(item => item.label === label);
  assert.ok(action, `the menu offers 「${label}」`);
  menu.props.onClose();
  action.onPress();
};

test('the anchored menu keeps note identity, persisted favorite and delete confirmation', async () => {
  const calls = [];
  const older = { id: 'older', date, timestamp: at('10:30'), content: '旧记录', category: 'INSPIRATION' };
  const latest = { ...older, id: 'latest', timestamp: at('17:56'), content: '最新记录', isFavorite: true };
  const hidden = { ...older, id: 'hidden', timestamp: at('09:00') };
  const h = screenHarness(snapshot({ notes: [older, hidden, latest] }), {
    toggleFavoriteNote: async id => { calls.push(['favorite', id]); return true; },
    setNoteCategory: async (id, category) => { calls.push(['category', id, category]); return true; },
    deleteNote: async id => { calls.push(['delete', id]); return true; },
  });
  const anchor = { x: 210, y: 420, width: 44, height: 44 };

  let tree = h.render();
  let items = nodes(tree, n => n.type === 'TimelineCompactRow');
  assert.deepEqual(items.map(n => n.props.content), ['最新记录', '旧记录', '旧记录']);
  assert.equal(items[0].props.isLast, false);
  assert.equal(items[1].props.isFirst, false);
  assert.equal(items[2].props.isLast, true);

  // Opening the menu from the second row anchors it to that row's button.
  items[1].props.action.onOpen(anchor);
  tree = h.render();
  let menu = nodes(tree, n => n.type === 'TimelineActionPopover')[0];
  assert.ok(menu, 'the row opens the anchored menu');
  assert.deepEqual(menu.props.anchor, anchor);
  assert.equal(menu.props.topInset, 24);
  assert.equal(menu.props.containerWidth, 390);
  assert.equal(menu.props.containerHeight, 820);
  assert.equal(menu.props.bottomInset, 96, 'the floating dock is reserved at the bottom');
  assert.deepEqual(JSON.parse(JSON.stringify(menu.props.actions.map(action => action.label))),
    ['编辑记录', '删除记录'], 'an inspiration has no resolution action');

  items = nodes(h.render(), n => n.type === 'TimelineCompactRow');
  items[0].props.action.onOpen(anchor);
  menu = nodes(h.render(), n => n.type === 'TimelineActionPopover')[0];
  pressMenuAction(menu, '编辑记录');
  tree = h.render();
  let editor = nodes(tree, n => n.type === 'RecordMomentModal')[0];
  assert.equal(editor.props.editingNote.id, 'latest');
  assert.equal(editor.props.editingNote.content, '最新记录');
  assert.equal(editor.props.initialConfirmDelete, false);
  assert.equal(nodes(tree, n => n.type === 'TimelineActionPopover').length, 0,
    'the menu closes before the editor opens');
  editor.props.onClose();

  items = nodes(h.render(), n => n.type === 'TimelineCompactRow');
  items[1].props.action.onOpen(anchor);
  menu = nodes(h.render(), n => n.type === 'TimelineActionPopover')[0];
  pressMenuAction(menu, '删除记录');
  tree = h.render();
  editor = nodes(tree, n => n.type === 'RecordMomentModal')[0];
  assert.equal(editor.props.editingNote.id, 'older');
  assert.equal(editor.props.initialConfirmDelete, true, 'delete only opens the existing confirmation');
  assert.deepEqual(calls, [], 'menu delete never deletes directly');
});

test('a failed resolution surfaces the existing error and keeps the note', async () => {
  const note = { id: 'note-1', date, timestamp: at('10:30'), content: '旧疑点', category: 'DOUBT', isResolved: false };
  const h = screenHarness(snapshot({ notes: [note] }), { toggleNoteResolved: async () => false });
  const items = nodes(h.render(), n => n.type === 'TimelineCompactRow');
  items[0].props.action.onOpen({ x: 10, y: 100, width: 44, height: 44 });
  const menu = nodes(h.render(), n => n.type === 'TimelineActionPopover')[0];
  pressMenuAction(menu, '标记为已解决');
  await new Promise(resolve => setTimeout(resolve, 0));
  const tree = h.render();
  assert.match(text(tree), /疑点状态未保存，请重试/);
  assert.equal(nodes(tree, n => n.type === 'TimelineCompactRow').length, 1, 'the note is not removed');
});

test('one anchored menu at a time, and scrolling closes it', () => {
  const older = { id: 'older', date, timestamp: at('10:30'), content: '旧记录', category: 'INSPIRATION' };
  const latest = { ...older, id: 'latest', timestamp: at('17:56'), content: '最新记录' };
  const h = screenHarness(snapshot({ notes: [older, latest] }));
  let items = nodes(h.render(), n => n.type === 'TimelineCompactRow');

  items[1].props.action.onOpen({ x: 10, y: 100, width: 44, height: 44 });
  items[0].props.action.onOpen({ x: 20, y: 200, width: 44, height: 44 });
  const tree = h.render();
  const menus = nodes(tree, n => n.type === 'TimelineActionPopover');
  assert.equal(menus.length, 1, 'opening another row replaces the menu instead of stacking');
  assert.deepEqual(menus[0].props.anchor, { x: 20, y: 200, width: 44, height: 44 });

  const scroll = nodes(tree, n => n.props?.testID === 'scroll-today')[0];
  scroll.props.onScrollBeginDrag();
  assert.equal(nodes(h.render(), n => n.type === 'TimelineActionPopover').length, 0,
    'a scroll invalidates the anchor, so the menu closes');
});

test('a morning focus session with pauses is properly displayed on the rhythm chart and day parts spectrum', () => {
  // Simulates a 30m session between 10:45 and 11:15 with 4 pauses
  const morningSession = session('morning-paused', '10:45', '11:15', {
    durationSeconds: 1800,
    pauseCount: 4,
    subjectId: 'ds',
    subjectName: '数据结构',
    subjectColor: '#2D7A5E',
  });
  const harness = screenHarness(snapshot({
    sessions: [morningSession],
    totalDurationSeconds: 1800,
    focusCount: 1,
    focusParticipationCount: 1,
  }));
  const tree = harness.render();

  // Rhythm chart has buckets allocated to hour 10 and hour 11
  const chart = nodes(tree, node => node.type === 'YanjiDayRhythmChart')[0];
  const h10 = chart.props.buckets.find(b => b.hour === 10);
  const h11 = chart.props.buckets.find(b => b.hour === 11);
  assert.ok(h10.seconds > 0, 'hour 10 must have allocated seconds');
  assert.ok(h11.seconds > 0, 'hour 11 must have allocated seconds');
  assert.equal(h10.seconds + h11.seconds, 1800, 'total allocated seconds across hours equals session duration');
  assert.equal(h10.color, '#2D7A5E', 'hour 10 takes the session subject color');
  assert.equal(h11.color, '#2D7A5E', 'hour 11 takes the session subject color');

  // The chart's spoken summary retains the measured focus time.
  assert.match(chart.props.accessibilityLabel, /06:00 之后已确认 30 分钟 连续专注/);

  // Expanding rhythm details reveals the session in the morning spectrum
  const heading = nodes(tree, node => node.type === 'YanjiPaperSectionHeading' && node.props.title === '今日学习节奏')[0];
  const toggle = nodes(heading.props.right, node => node.type === 'Pressable')[0];
  toggle.props.onPress();
  const expandedTree = harness.render();

  // The view switcher is gone: the expanded area is 专注时段 only.
  assert.equal(nodes(expandedTree, node => node.type === 'YanjiPaperSegmentedControl').length, 0,
    'the 学科投入 / 专注时段 switcher no longer exists');
  assert.doesNotMatch(text(expandedTree), /学科投入/);

  // Morning spectrum has the segment
  assert.match(text(expandedTree), /上午/);
  assert.match(text(expandedTree), /06:00—12:00/);
  // The strip is the whole detail: no second session list, no explanatory
  // caption and no trailing rule repeating what the tap already explains.
  assert.doesNotMatch(text(expandedTree), /10:45 起/);
  assert.doesNotMatch(text(expandedTree), /柱高按/);
  // Must NOT display "未绘连续区段"
  assert.doesNotMatch(text(expandedTree), /未绘连续区段/);

  // Tapping the highlighted span prints its exact interval and subject.
  const highlight = nodes(expandedTree, node => node.type === 'Pressable'
    && node.props.accessibilityLabel === '数据结构，10:45 至 11:15，30分钟')[0];
  assert.ok(highlight, 'each coloured span is its own press target');
  highlight.props.onPress();
  const detailTree = harness.render();
  assert.match(text(detailTree), /10:45—11:15  数据结构  30分钟/);
});





test('a page holding real content says when that content may be behind Room', () => {
  // The gap this pins: `snapshot` was non-null so the only retry affordance
  // (`{!snapshot ? <YanjiTextAction 重新读取/>}`) never rendered, and a failed
  // background read left the day's numbers on screen looking current.
  const stale = screenHarness(snapshot({ totalDurationSeconds: 3600, focusCount: 2 }), {}, { readFailed: true });
  const tree = stale.render();
  const notice = nodes(tree, node => node.type === 'YanjiStaleDataNotice')[0];
  assert.ok(notice, 'a stale-but-present snapshot must offer a retry');
  assert.match(notice.props.message, /可能不是最新/);
  assert.equal(notice.props.actionLabel, '重新读取');
  assert.equal(notice.props.busy, false);
  // The numbers themselves stay on the page — a failure must not blank real data.
  // The day's round count now reads 「已完整结束 N 轮」 under the hero.
  assert.match(text(tree), /已完整结束/);
  assert.match(text(tree), /2/);
});

test('a healthy read shows no staleness notice at all', () => {
  // The banner is a claim that something went wrong; on the normal path that
  // claim must be absent, otherwise it becomes noise nobody reads.
  const healthy = screenHarness(snapshot({ totalDurationSeconds: 3600, focusCount: 2 }));
  assert.equal(nodes(healthy.render(), node => node.type === 'YanjiStaleDataNotice').length, 0);
});

test('a failed first read with nothing to show keeps the loading retry, not the banner', () => {
  // Two different failures must not collapse into one message: no value at all
  // is 「正在读取」 plus a retry; a value that may be stale is the notice. With no
  // snapshot there is nothing to be stale about, so only the first may appear.
  const loading = screenHarness(null, {}, { readFailed: true });
  const tree = loading.render();
  assert.equal(nodes(tree, node => node.type === 'YanjiStaleDataNotice').length, 0);
  assert.match(text(tree), /正在读取今日记录/);
  assert.ok(nodes(tree, node => node.type === 'YanjiTextAction' && node.props.label === '重新读取')[0]);
});

test('pressing the staleness retry re-reads the day and disables itself while running', async () => {
  const retries = [];
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  const h = screenHarness(snapshot(), {}, { readFailed: true });
  h.setRefreshDaily(date => { retries.push(['daily', date]); return gate; });

  const notice = nodes(h.render(), node => node.type === 'YanjiStaleDataNotice')[0];
  notice.props.onAction();
  assert.deepEqual(retries, [['daily', date]], "the banner uses the page's existing retry path");

  // While the read is in flight the action must not invite a second press: the
  // same double-tap that duplicates a save would here stack concurrent reads.
  const busy = nodes(h.render(), node => node.type === 'YanjiStaleDataNotice')[0];
  assert.equal(busy.props.busy, true);
  assert.equal(busy.props.actionLabel, '读取中…');

  release();
  await new Promise(resolve => setTimeout(resolve, 0));
  const done = nodes(h.render(), node => node.type === 'YanjiStaleDataNotice')[0];
  assert.equal(done.props.busy, false, 'the action is available again once the read settles');
});

function carryOverTask(changes = {}) {
  return task({
    id: 'carry-source',
    date: '2026-10-15',
    title: '复习操作系统文件管理',
    plannedMinutes: 45,
    actualMinutes: 20,
    ...changes,
  });
}

const carryButton = (tree, suffix = 'carry-0') => {
  return nodes(tree, node =>
    node.type === 'YanjiTextAction' && node.props.testID === `carry-over-${suffix}`)[0];
};

function openCarryOver(h) {
  const trigger = nodes(h.render(), node => node.props.testID === 'open-carry-over')[0];
  assert.ok(trigger, 'real pending work has a reachable summary entry');
  trigger.props.onPress();
  return h.render();
}

test('today plans precede collapsed history, whose sheet keeps truthful dates and actions', async () => {
  const h = screenHarness(snapshot(), {}, {});
  h.setCarryOverTasks([
    carryOverTask(),
    carryOverTask({ id: 'older-source', date: '2026-10-13', title: '整理错题', actualMinutes: 0 }),
  ]);
  let tree = await h.mount();
  const order = nodes(tree, node => node.props.title === '今日计划' || node.props.testID === 'open-carry-over');
  assert.equal(order[0].props.title, '今日计划');
  assert.equal(order[1].props.testID, 'open-carry-over');
  assert.doesNotMatch(text(tree), /复习操作系统文件管理|整理错题/,
    'history must not displace today before the user chooses to handle it');
  const trigger = order[1];
  assert.match(trigger.props.accessibilityLabel, /待处理.*2/);
  assert.ok(trigger.props.style({ pressed: false }).minHeight >= 48);
  tree = openCarryOver(h);
  assert.equal(nodes(tree, node => node.type === 'Modal').length, 1);
  const modal = nodes(tree, node => node.type === 'Modal')[0];
  const animatedLayers = nodes(modal, node => node.type === 'AnimatedView');
  assert.equal(modal.props.animationType, 'none',
    'the platform modal must not translate the whole screen');
  assert.equal(animatedLayers.length, 2,
    'the fixed scrim and moving sheet need independent animation layers');
  assert.equal(animatedLayers[0].props.style[0].backgroundColor, theme.paper.colors.scrim);
  assert.equal(animatedLayers[0].props.style[1].opacity, 1,
    'the scrim settles at full tone without moving');
  assert.equal(animatedLayers[1].props.style[1].transform[0].translateY, 0,
    'only the sheet settles at its resting position');
  assert.match(text(tree), /昨天的计划 尚未完成/);
  assert.match(text(tree), /10月13日的计划 尚未完成/);
  assert.match(text(tree), /复习操作系统文件管理/);
  assert.match(text(tree), /已投入 20m 预计 45m/);
});

test('dismissing pending history through back, backdrop or header never writes or loses rows', async () => {
  const h = screenHarness(snapshot());
  h.setCarryOverTasks([carryOverTask()]);
  await h.mount();
  const before = h.calls.length;
  for (const closeBy of ['back', 'backdrop', 'header']) {
    const tree = openCarryOver(h);
    if (closeBy === 'back') nodes(tree, n => n.type === 'Modal')[0].props.onRequestClose();
    if (closeBy === 'backdrop') nodes(tree, n => n.props.testID === 'close-carry-over-backdrop')[0].props.onPress();
    if (closeBy === 'header') nodes(tree, n => n.type === 'YanjiPaperSheetHeading')[0].props.onClose();
    assert.equal(nodes(h.render(), n => n.type === 'Modal').length, 0);
    assert.ok(nodes(h.render(), n => n.props.testID === 'open-carry-over')[0]);
  }
  assert.equal(h.calls.length, before, 'opening and closing only changes presentation');
});

test('an empty pending read leaves no empty section behind', async () => {
  const h = screenHarness(snapshot());
  h.setCarryOverTasks([]);
  const tree = await h.mount();
  assert.equal(nodes(tree, node => node.type === 'YanjiPaperSectionHeading'
    && node.props.title === '待处理').length, 0);
  assert.equal(nodes(tree, node => node.props.testID === 'open-carry-over').length, 0);
});

test('hiding the page or exhausting history closes its sheet without reopening on the next read', async () => {
  const h = screenHarness(snapshot());
  h.setCarryOverTasks([carryOverTask()]);
  await h.mount();
  openCarryOver(h);
  h.setVisible(false);
  let tree = await h.rerender();
  assert.equal(nodes(tree, n => n.type === 'Modal').length, 0);
  h.setVisible(true);
  tree = await h.rerender();
  assert.equal(nodes(tree, n => n.type === 'Modal').length, 0);
  openCarryOver(h);
  h.setCarryOverTasks([]);
  h.emitDataChanged('tasks');
  await h.flush();
  await h.rerender();
  h.setCarryOverTasks([carryOverTask({ id: 'next-history' })]);
  h.emitDataChanged('tasks');
  await h.flush();
  tree = await h.rerender();
  assert.ok(nodes(tree, n => n.props.testID === 'open-carry-over')[0]);
  assert.equal(nodes(tree, n => n.type === 'Modal').length, 0);
});

test('carry-over waits for one durable write and then refreshes today', async () => {
  let release;
  const write = new Promise(resolve => { release = resolve; });
  const calls = [];
  const h = screenHarness(snapshot(), {}, {});
  h.setCarryOverTasks([carryOverTask()]);
  h.setCarryTaskToToday(async id => {
    calls.push(['carry', id]);
    return write;
  });
  let refreshed = 0;
  h.setRefreshDaily(() => { refreshed += 1; });

  await h.mount();
  let tree = openCarryOver(h);
  carryButton(tree).props.onPress();
  carryButton(tree).props.onPress();
  await h.flush();
  assert.deepEqual(calls, [['carry', 'carry-source']],
    'the synchronous gate blocks the second tap before either promise settles');
  tree = h.render();
  assert.equal(carryButton(tree).props.disabled, true);

  release(carryOverTask({ id: 'today-copy' }));
  await h.flush();
  tree = await h.rerender();
  assert.equal(nodes(tree, node => node.type === 'YanjiPaperSectionHeading'
    && node.props.title === '待处理').length, 0,
  'the source disappears only after the durable carry resolves');
  assert.equal(refreshed, 1, 'today is re-read after the write commits');
  assert.equal(nodes(tree, n => n.type === 'Modal').length, 0);
  assert.equal(nodes(tree, n => n.props.testID === 'open-carry-over').length, 0);
});

test('failed carry-over keeps the source visible and offers an honest retry', async () => {
  const h = screenHarness(snapshot(), {}, {});
  h.setCarryOverTasks([carryOverTask()]);
  h.setCarryTaskToToday(async () => { throw new Error('disk full'); });
  await h.mount();
  let tree = openCarryOver(h);
  carryButton(tree).props.onPress();
  await h.flush();
  tree = h.render();
  assert.match(text(tree), /未能加入今日，任务仍在待处理区，请重试/);
  assert.match(text(tree), /复习操作系统文件管理/);
  assert.equal(carryButton(tree).props.disabled, false);
});

test('completing historical work removes it only after the persisted write succeeds', async () => {
  const calls = [];
  const h = screenHarness(snapshot(), {}, {});
  h.setCarryOverTasks([carryOverTask()]);
  h.setToggleTask(async (id, completed) => {
    calls.push(['toggle', id, completed]);
    return true;
  });
  await h.mount();
  const tree = openCarryOver(h);
  carryButton(tree, 'complete-0').props.onPress();
  await h.flush();
  assert.deepEqual(calls, [['toggle', 'carry-source', true]]);
  assert.equal(nodes(h.render(), node => node.type === 'YanjiPaperSectionHeading'
    && node.props.title === '待处理').length, 0);
});

test('defer hides for this session without writing to Room', async () => {
  const h = screenHarness(snapshot(), {}, {});
  h.setCarryOverTasks([carryOverTask()]);
  await h.mount();
  let tree = openCarryOver(h);
  const before = h.calls.length;
  carryButton(tree, 'defer-0').props.onPress();
  tree = h.render();
  assert.equal(nodes(tree, node => node.type === 'YanjiPaperSectionHeading'
    && node.props.title === '待处理').length, 0);
  assert.equal(h.calls.length, before, 'temporarily preserving a row is a local view choice');
});

test('a failed pending read keeps old rows and exposes a retry; success clears it', async () => {
  let attempt = 0;
  const h = screenHarness(snapshot(), {}, {});
  h.setCarryOverTasks([carryOverTask()]);
  h.setGetCarryOverTasks(async () => {
    attempt += 1;
    if (attempt === 2) throw new Error('read failed');
    return [carryOverTask()];
  });
  await h.mount();
  let tree = openCarryOver(h);
  h.emitDataChanged('tasks');
  await h.flush();
  tree = h.render();
  const notice = nodes(tree, node => node.type === 'YanjiStaleDataNotice')
    .find(node => /待处理任务读取失败/.test(node.props.message));
  assert.ok(notice, 'a failed refresh must not silently keep stale rows');
  assert.match(text(tree), /复习操作系统文件管理/);

  notice.props.onAction();
  await h.flush();
  tree = h.render();
  assert.equal(nodes(tree, node => node.type === 'YanjiStaleDataNotice'
    && /待处理任务读取失败/.test(node.props.message)).length, 0);
});

test('the page hands every record to a windowed list instead of mounting them all', () => {
  // PERF-06 的可测部分：记录不再由 ScrollView 一次全挂载。
  //
  // 这个用例证明两件互相独立的事，缺一不可：
  //   1) 列表拿到的是**全部**行 —— 不是截断数据冒充优化；
  //   2) 首帧实际挂载的行数由窗口决定，而不是由当天记录数决定。
  // 只断言「用了 FlashList」是没有意义的：把 data 砍成两行同样能过。
  const notes = Array.from({ length: 40 }, (_, index) => ({
    id: `bulk-${index}`,
    date,
    timestamp: at('08:00') + index * 60_000,
    content: `记录 ${index}`,
    category: 'INSPIRATION',
    isFavorite: false,
    isResolved: false,
  }));
  const tasks = Array.from({ length: 12 }, (_, index) => task({
    id: `plan-${index}`,
    title: `计划 ${index}`,
  }));
  const h = screenHarness(snapshot({ notes, tasks }));
  h.render();

  const list = h.listRender();
  assert.ok(list, 'the page renders one vertical list');
  // 12 plan rows + the rhythm + the 今日随笔 heading + the capture entry + 40 notes.
  assert.equal(list.rowCount, 12 + 1 + 1 + 1 + 40,
    'every plan row and every note reaches the list — nothing is truncated away');
  assert.ok(list.mounted < list.rowCount,
    `the first paint mounts a window, not the whole day (mounted ${list.mounted} of ${list.rowCount})`);
  assert.ok(list.mounted <= 8,
    `the initial mount is bounded by the viewport, not by data size (was ${list.mounted})`);

  // Two pools: plan rows and journal rows recycle separately.
  assert.deepEqual([...new Set(list.types)].sort(), ['capture', 'journal-heading', 'note', 'rhythm', 'task'],
    'each row kind is declared, so cells recycle within their own type');

  // Scrolling extends the window; every row stays reachable.
  let mounted = list.mounted;
  for (let pass = 0; pass < 12 && mounted < list.rowCount; pass += 1) {
    mounted = h.flashList.scroll(6, 'scroll-today');
  }
  assert.equal(mounted, list.rowCount, 'scrolling reaches the last row — nothing is unreachable');
});

test('folding, ordering and the anchored menu behave the same on the virtualized page', async () => {
  // 虚拟化不能改变读者的行为。这组断言在窗口化列表上重跑折叠、排序与菜单，
  // 并证明折叠只是缩小交给列表的行数，而不是把数据丢掉。
  const notes = ['08:15', '11:40', '15:12', '17:56'].map((time, index) => ({
    id: `vnote-${index}`, date, timestamp: at(time), content: `记录 ${time}`,
    category: index % 2 ? 'DOUBT' : 'INSPIRATION', isFavorite: false, isResolved: false,
  }));
  const h = screenHarness(snapshot({ notes }));
  // The rhythm row now consumes a window slot above the journal, so the window
  // is grown one scroll before the four notes are read.
  h.listRender();
  h.flashList.scroll(2, 'scroll-today');
  const tree = h.render();
  const heading = () => nodes(tree, node => node.type === 'YanjiPaperSectionHeading')
    .find(node => node.props.title === '今日随笔');
  assert.ok(heading(), 'the journal heading still renders');
  assert.equal(heading().props.countText, '4 条', 'the heading counts the whole day, not the window');

  // Default order is newest first and the list receives all four.
  const list = h.listRender();
  assert.equal(list.rowCount, 1 + 1 + 1 + 4, 'rhythm + heading + write entry + four notes');
  const rows = nodes(tree, node => node.type === 'TimelineCompactRow');
  assert.deepEqual(rows.map(row => row.props.content),
    ['记录 17:56', '记录 15:12', '记录 11:40', '记录 08:15'],
    'newest first, and the window still shows the newest four');

  // Folding reduces the rows handed to the list, and keeps the newest two.
  const control = testID => nodes(heading().props.right, node => node.props.testID === testID)[0];
  control('journal-fold').props.onPress();
  const folded = h.render();
  assert.equal(h.listRender().rowCount, 1 + 1 + 1 + 2,
    'a folded journal hands the list two note rows — it does not hand over all four and hide them');
  assert.deepEqual(nodes(folded, node => node.type === 'TimelineCompactRow').map(row => row.props.content),
    ['记录 17:56', '记录 15:12']);
  assert.equal(heading().props.countText, '4 条', 'the count still reports the whole day after folding');

  // Reversing the order reverses what the list is given.
  control('journal-order').props.onPress();
  const reversed = h.render();
  assert.deepEqual(nodes(reversed, node => node.type === 'TimelineCompactRow').map(row => row.props.content),
    ['记录 08:15', '记录 11:40'], 'the folded window follows the order in effect');

  // The anchored menu still opens from a recycled row and carries its identity.
  const anchor = { x: 210, y: 420, width: 44, height: 44 };
  const target = nodes(reversed, node => node.type === 'TimelineCompactRow')[0];
  target.props.action.onOpen(anchor);
  const menu = nodes(h.render(), node => node.type === 'TimelineActionPopover')[0];
  assert.ok(menu, 'a row mounted from the recycling window still anchors its menu');
  assert.deepEqual(menu.props.anchor, anchor);
  assert.equal(nodes(h.render(), node => node.type === 'TimelineActionPopover').length, 1);
});
