import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './framework/load-typescript.js';

// Invoke the real production components through React hook/native adapters.
// No copied ruler algorithm and no user timer/session is touched.
function dialHarness({ value = 37, width = 358, fontScale = 1, step = 5 } = {}) {
  const states = [];
  const refs = [];
  const scrollCalls = [];
  const changed = [];
  let hookIndex = 0;
  let refIndex = 0;
  let effects = [];
  const react = {
    useState: initial => {
      const i = hookIndex++;
      if (!(i in states)) states[i] = typeof initial === 'function' ? initial() : initial;
      return [states[i], next => {
        states[i] = typeof next === 'function' ? next(states[i]) : next;
      }];
    },
    useRef: initial => {
      const i = refIndex++;
      if (!refs[i]) refs[i] = { current: initial };
      return refs[i];
    },
    useEffect: fn => { effects.push(fn); },
    useMemo: fn => fn(),
    useCallback: fn => fn,
  };
  const paper = {
    colors: {
      brand: '#c84b31', brandInk: '#b23a22', secondary: '#555', ink: '#111',
      subtle: '#eee', hairline: 'rgba(0,0,0,0.08)',
    },
    radius: { pill: 9999, tick: 1, surface: 14 },
    layout: { hairlineWidth: 1 },
    typography: {
      captionMeta: { fontSize: 12, lineHeight: 17, fontWeight: '500' },
      bodyPrimary: { fontSize: 14, lineHeight: 23, fontWeight: '500' },
    },
  };
  const component = loadTypeScript('src/components/FocusVernierDial.tsx', {
    react,
    'react-native': {
      View: 'View', Text: 'Text', Pressable: 'Pressable', ScrollView: 'ScrollView',
      useWindowDimensions: () => ({ width, height: 720, fontScale }),
    },
    'react-native-reanimated': { useReducedMotion: () => false },
    '../theme/ThemeProvider': { useYanjiTheme: () => ({ paper }) },
  }).FocusVernierDial;

  function elements(node, predicate, out = []) {
    if (Array.isArray(node)) {
      for (const element of node) elements(element, predicate, out);
    } else if (node && typeof node === 'object' && 'type' in node) {
      if (predicate(node)) out.push(node);
      elements(node.props?.children, predicate, out);
    }
    return out;
  }

  function render(nextValue = value) {
    value = nextValue;
    hookIndex = 0;
    refIndex = 0;
    effects = [];
    const tree = component({
      valueMinutes: value, onChangeMinutes: next => changed.push(next),
      minMinutes: 5, maxMinutes: 180, stepMinutes: step,
    });
    if (!refs[0].current) {
      refs[0].current = { scrollTo: options => scrollCalls.push(options) };
    }
    for (const effect of effects) effect();
    const ruler = elements(tree, e => e.props?.testID === 'focus-vernier-ruler')[0];
    const adjustable = elements(tree, e => e.props?.accessibilityRole === 'adjustable')[0];
    const ticks = ruler.props.children;
    return { tree, ruler, adjustable, ticks, nodes: predicate => elements(tree, predicate) };
  }

  return { render, changed, scrollCalls, width };
}

test('vernier centres first, last and fractional values using its own padded scroll domain', () => {
  for (const width of [320, 358, 430]) {
    for (const value of [5, 37, 180]) {
      const h = dialHarness({ width, value });
      let ui = h.render();
      ui.adjustable.props.onLayout({ nativeEvent: { layout: { width } } });
      ui = h.render();

      const expectedOffset = (value - 5) / 5 * 26;
      assert.ok(h.scrollCalls.length > 0, 'controlled value must drive a programmatic offset');
      assert.ok(Math.abs(h.scrollCalls.at(-1).x - expectedOffset) < 1e-8);
      assert.equal(h.scrollCalls.at(-1).animated, false);
      assert.equal(h.changed.length, 0, 'layout/hydration must not write a rounded value');

      const inset = ui.ruler.props.contentContainerStyle.paddingHorizontal;
      const cellWidth = ui.ticks[0].props.style.width;
      assert.equal(cellWidth, 26);
      assert.equal(inset, (width - cellWidth) / 2, 'symmetric inset includes trailing padding');
      const projectedCentre = inset + expectedOffset + cellWidth / 2 - h.scrollCalls.at(-1).x;
      assert.ok(Math.abs(projectedCentre - width / 2) < 1e-8,
        'selected or fractional value sits on centre pointer within floating-point precision');

      const highlighted = ui.ticks.filter(cell => cell.props.children[0].props.style.width === 2);
      assert.equal(highlighted.length, value === 37 ? 0 : 1, 'non-grid 37 must not highlight neighbouring 35');
      if (value === 5) assert.equal(highlighted[0], ui.ticks[0]);
      if (value === 180) assert.equal(highlighted[0], ui.ticks.at(-1));
      assert.deepEqual({
        min: ui.adjustable.props.accessibilityValue.min,
        max: ui.adjustable.props.accessibilityValue.max,
        now: ui.adjustable.props.accessibilityValue.now,
        text: ui.adjustable.props.accessibilityValue.text,
      }, { min: 5, max: 180, now: value, text: `${value} 分钟` });
    }
  }
});

test('vernier drag and adjustable actions emit only intentional legal snapped values', () => {
  const h = dialHarness({ value: 37, width: 358 });
  let ui = h.render();
  ui.adjustable.props.onLayout({ nativeEvent: { layout: { width: 358 } } });
  ui = h.render();
  assert.equal(h.changed.length, 0);

  ui.ruler.props.onScrollBeginDrag();
  ui.ruler.props.onMomentumScrollEnd({ nativeEvent: { contentOffset: { x: 7 * 26 } } });
  assert.equal(h.changed.at(-1), 40, 'intentional drag snaps to 40');
  assert.equal(h.changed.every(value => value % 5 === 0 && value >= 5 && value <= 180), true);

  h.changed.length = 0;
  ui.adjustable.props.onAccessibilityAction({ nativeEvent: { actionName: 'increment' } });
  assert.deepEqual(h.changed, [40], 'from 37, accessibility increment reaches next mark 40');
  h.changed.length = 0;
  ui.adjustable.props.onAccessibilityAction({ nativeEvent: { actionName: 'decrement' } });
  assert.deepEqual(h.changed, [35], 'from 37, accessibility decrement reaches previous mark 35');

  const plus = ui.nodes(n => n.props?.accessibilityLabel === '增加5分钟')[0];
  const minus = ui.nodes(n => n.props?.accessibilityLabel === '减少5分钟')[0];
  assert.ok(plus && minus);
  assert.ok(plus.props.style({ pressed: false }).minWidth >= 48);
  assert.ok(minus.props.style({ pressed: false }).minHeight >= 48);
});

test('major ruler labels have enough real container height at fontScale 2 and 3', () => {
  for (const fontScale of [1, 1.5, 2, 3]) {
    const h = dialHarness({ fontScale });
    const ui = h.render();
    const major = ui.ticks.find(cell => Array.isArray(cell.props.children) && cell.props.children[1]);
    assert.ok(major, 'there must be at least one labelled major tick');
    const label = major.props.children[1];
    const top = label.props.style[1].top;
    const height = major.props.style.height;
    assert.ok(top + 17 * fontScale + 8 <= height, `fontScale=${fontScale}: major label fits in cell`);
    assert.ok(ui.ruler.props.contentContainerStyle.minHeight >= height);
    assert.ok(ui.adjustable.props.style.minHeight >= height);
  }
});

/**
 * The immersive FocusScreen, driven through its real production source with the
 * native/time adapters mocked.
 *
 * `daily` is the same snapshot the screen already reads for today's per-subject
 * totals — the task-title assertions below therefore prove the subtitle
 * resolves against the one existing read rather than a second query.
 */
function immersiveHarness({ session = {}, tasks = [] } = {}) {
  const alerts = [];
  const calls = [];
  const noop = () => {};
  const active = {
    sessionId: 'test-owned', subjectId: 'math', subjectName: '数学',
    mode: 'COUNTDOWN', taskId: null, startTime: 0,
    elapsedSeconds: 10, remainingSeconds: 100, phase: 'FOCUS',
    isPaused: true, isCountdown: true,
    ...session,
  };
  const paper = {
    colors: { canvas: '#fff' }, radius: { pill: 9999, tick: 1 },
    layout: { pageGutter: 22, rowMinHeight: 48, sectionGap: 24 },
    typography: {
      captionMeta: {}, titleEditorial: {}, bodyPrimary: {}, displayTimer: {},
    },
    immersive: {
      running: { canvas: '#15181c', ink: '#fff', secondary: '#aaa', accent: '#e06d53' },
      paused: { canvas: '#1c1917', ink: '#fff', secondary: '#aaa', accent: '#dda059' },
    },
  };
  const mocks = {
    react: {
      useCallback: fn => fn, useMemo: fn => fn(), useEffect: noop,
      useState: initial => [typeof initial === 'function' ? initial() : initial, noop],
    },
    'react-native': {
      Alert: { alert: (...args) => alerts.push(args) }, View: 'View',
      ScrollView: 'ScrollView', Pressable: 'Pressable', Text: 'Text', Modal: 'Modal',
      useWindowDimensions: () => ({ width: 358, height: 800, fontScale: 1 }),
    },
    'react-native-safe-area-context': { useSafeAreaInsets: () => ({ bottom: 24 }) },
    'react-native-svg': {
      default: 'Svg', Svg: 'Svg', Defs: 'Defs', LinearGradient: 'LinearGradient',
      Rect: 'Rect', Stop: 'Stop',
    },
    '../bridge': {
      YanjiDataNative: { getSubjects: async () => [] },
      YanjiTimerNative: {
        discardTimer: async () => { calls.push('discard'); },
        completeTimer: async () => { calls.push('complete'); },
        resumeTimer: async () => { calls.push('resume'); },
        pauseTimer: async () => { calls.push('pause'); },
      },
    },
    '../components/YanjiUI': {
      YanjiEditorialHeader: noop, YanjiIcon: noop,
      YanjiPaperSectionHeading: noop, YanjiPaperSegmentedControl: noop,
      useYanjiKeyboardInset: () => 0,
    },
    '../components/FocusVernierDial': { FocusVernierDial: noop },
    '../components/RecordMomentModal': { RecordMomentModal: noop },
    '../navigation/NavigationShell': {
      useNavigationState: () => ({ focusPreset: null }),
      useNavigationActions: () => ({ clearFocusPreset: noop, selectTab: noop }),
    },
    '../navigation/useBottomTabLayout': { useBottomTabLayout: () => ({ contentPadding: 110 }) },
    '../theme/ThemeProvider': { useYanjiTheme: () => ({ paper }) },
    '../state/dataRefresh': { useDailySnapshot: () => ({ tasks }) },
    '../state/timerStore': {
      useActiveSession: () => active,
      useTimerTick: () => { throw new Error('Root FocusScreen must not subscribe to second ticks'); },
      syncActiveSession: async () => {},
    },
  };
  const { FocusScreen } = loadTypeScript('src/screens/FocusScreen.tsx', mocks);
  return { root: FocusScreen({ visible: true }), alerts, calls };
}

function findAll(node, predicate, matches = []) {
  if (Array.isArray(node)) for (const element of node) findAll(element, predicate, matches);
  else if (node && typeof node === 'object' && 'type' in node) {
    if (predicate(node)) matches.push(node);
    findAll(node.props?.children, predicate, matches);
  }
  return matches;
}

test('the immersion subtitle names the bound task and stays quiet when unbound', () => {
  const bound = immersiveHarness({
    session: { taskId: 'task-1' },
    tasks: [{ id: 'task-1', title: '王道操作系统 · 页面置换算法' }],
  });
  const named = findAll(bound.root, n => n.props?.children === '王道操作系统 · 页面置换算法');
  assert.equal(named.length, 1, 'a bound session must name the task it is running for');

  // A session started without a task keeps the standing copy …
  const unbound = immersiveHarness({ session: { taskId: null } });
  assert.equal(findAll(unbound.root, n => n.props?.children === '专注已暂歇 · 随时继续当前进度').length, 1);
  assert.equal(findAll(unbound.root, n => n.props?.children === '王道操作系统 · 页面置换算法').length, 0);

  // … and so does one whose task is not in today's snapshot (it outlived its
  // day). A stale or invented title would be worse than the quiet line.
  const stale = immersiveHarness({
    session: { taskId: 'task-yesterday' },
    tasks: [{ id: 'task-1', title: '王道操作系统 · 页面置换算法' }],
  });
  assert.equal(findAll(stale.root, n => n.props?.children === '王道操作系统 · 页面置换算法').length, 0);
  assert.equal(findAll(stale.root, n => n.props?.children === '专注已暂歇 · 随时继续当前进度').length, 1);
});

test('paused discard opens native confirmation; cancellation never calls discardTimer', async () => {
  const { root, alerts, calls } = immersiveHarness();
  const discard = findAll(root, n => n.props?.label === '放弃本轮')[0];
  const resume = findAll(root, n => n.props?.label === '继续专注')[0];
  const complete = findAll(root, n => n.props?.label === '提前结束')[0];
  assert.ok(discard && resume && complete, 'existing paused-state actions stay accessible');

  discard.props.onPress();
  assert.equal(alerts.length, 1, 'irreversible action must ask for confirmation');
  assert.equal(calls.length, 0, 'opening dialog does not call native discard');
  const [title, description, buttons, options] = alerts[0];
  assert.match(title, /放弃/);
  assert.match(description, /无法恢复/);
  assert.equal(options.cancelable, true);
  assert.equal(buttons[0].style, 'cancel');
  buttons[0].onPress?.();
  await Promise.resolve();
  assert.equal(calls.length, 0, 'cancel keeps the native session intact');

  assert.equal(buttons[1].style, 'destructive');
  buttons[1].onPress();
  await new Promise(resolve => setImmediate(resolve));
  assert.deepEqual(calls, ['discard'], 'only the confirmed action invokes native discard, once');
});
