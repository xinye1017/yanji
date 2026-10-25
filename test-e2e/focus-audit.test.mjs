import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './framework/load-typescript.js';

const tokens = loadTypeScript('src/theme/tokens.ts');

// Invoke the real production components through React hook/native adapters.
// No copied ruler algorithm and no user timer/session is touched.
function dialHarness({
  value = 37, step = 5, fontScale = 1, density = 1, isDark = false, disabled = false,
  reducedMotion = false, accessibilityLabel,
} = {}) {
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
    colors: tokens.YanjiPaperColors[isDark ? 'dark' : 'light'],
    radius: tokens.YanjiPaperRadius,
    layout: tokens.YanjiPaperLayout,
    typography: tokens.YanjiPaperTypography,
  };
  const component = loadTypeScript('src/components/FocusVernierDial.tsx', {
    react,
    'react-native': {
      View: 'View', Text: 'Text', ScrollView: 'ScrollView',
      PixelRatio: { roundToNearestPixel: size => Math.round(size * density) / density },
      useWindowDimensions: () => ({ width: 358, height: 720, scale: density, fontScale }),
    },
    'react-native-reanimated': { useReducedMotion: () => reducedMotion },
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
      minMinutes: 5, maxMinutes: 180, stepMinutes: step, disabled, accessibilityLabel,
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

  return { render, changed, scrollCalls, paper };
}

const scrollEvent = (x, velocity = 0) => ({
  nativeEvent: { contentOffset: { x }, velocity: { x: velocity } },
});

test('vernier centres endpoints and non-grid presets without mount, layout or controlled-value writes', () => {
  for (const fontScale of [1, 3]) {
    const h = dialHarness({ fontScale });
    let ui = h.render();
    assert.deepEqual(h.scrollCalls, [], 'wait for the measured viewport before scrolling');
    assert.deepEqual(h.changed, [], 'mount must preserve a non-grid task preset');

    for (const width of [320, 358, 430]) {
      ui.adjustable.props.onLayout({ nativeEvent: { layout: { width } } });
      for (const value of [5, 37, 180]) {
        const before = h.scrollCalls.length;
        ui = h.render(value);
        const cellWidth = ui.ticks[0].props.style.width;
        const inset = ui.ruler.props.contentContainerStyle.paddingHorizontal;
        const offset = h.scrollCalls.at(-1);
        assert.ok(h.scrollCalls.length > before, 'controlled values must drive a programmatic offset');
        assert.ok(cellWidth > 0);
        assert.equal(ui.ruler.props.snapToInterval, cellWidth, 'scroll snapping uses the rendered spacing');
        assert.ok(ui.ticks.every(cell => cell.props.style.width === cellWidth));
        assert.equal(inset, (width - cellWidth) / 2, 'both endpoints need symmetric padding');

        const lowIndex = ui.ticks.findLastIndex(cell => Number(cell.key) <= value);
        const highIndex = ui.ticks.findIndex(cell => Number(cell.key) >= value);
        assert.ok(lowIndex >= 0 && highIndex >= lowIndex, 'real marks bracket the selected value');
        const low = Number(ui.ticks[lowIndex].key);
        const high = Number(ui.ticks[highIndex].key);
        const fraction = high === low ? 0 : (value - low) / (high - low);
        const selectedCentre = inset + (lowIndex + fraction) * cellWidth + cellWidth / 2;
        assert.ok(Math.abs(selectedCentre - offset.x - width / 2) < 1e-8,
          `${value} minutes must sit at the pointer, including between real marks`);
        assert.equal(offset.animated, false);

        const maxOffset = inset * 2 + ui.ticks.length * cellWidth - width;
        assert.ok(offset.x >= 0 && offset.x <= maxOffset, 'no overscroll is needed to centre the endpoints');
        if (value === 5) assert.equal(offset.x, 0);
        if (value === 180) assert.equal(offset.x, maxOffset);
        if (value === 37) assert.ok(highIndex > lowIndex, '37 stays between 35 and 40');

        const needle = ui.nodes(n => n.props?.pointerEvents === 'none')[0];
        assert.ok(needle, 'selection has one fixed visual pointer');
        assert.equal(needle.props.style.alignSelf, 'center');
        const a11y = ui.adjustable.props.accessibilityValue;
        assert.deepEqual({ min: a11y.min, max: a11y.max, now: a11y.now, text: a11y.text },
          { min: 5, max: 180, now: value, text: `${value} 分钟` });

        ui.ruler.props.onScrollEndDrag(scrollEvent(offset.x));
        ui.ruler.props.onMomentumScrollEnd(scrollEvent(offset.x));
        assert.deepEqual(h.changed, [], 'layout, preset changes and programmatic scroll events never write a duration');
      }
    }
  }
});

test('vernier commits only intentional legal steps through slow drags, momentum and TalkBack', () => {
  for (const fontScale of [1, 3]) {
    const h = dialHarness({ fontScale });
    let ui = h.render();
    ui.adjustable.props.onLayout({ nativeEvent: { layout: { width: 358 } } });
    ui = h.render();
    const spacing = ui.ruler.props.snapToInterval;
    assert.deepEqual(h.changed, []);

    ui.ruler.props.onScrollBeginDrag();
    const callsBeforeDrag = h.scrollCalls.length;
    ui.adjustable.props.onLayout({ nativeEvent: { layout: { width: 430 } } });
    ui = h.render();
    assert.equal(h.scrollCalls.length, callsBeforeDrag, 'layout must not interrupt an active drag');
    ui.ruler.props.onScrollEndDrag(scrollEvent(6.7 * spacing, 0.5));
    assert.deepEqual(h.changed, [], 'a fling waits for momentum to settle');
    ui.ruler.props.onMomentumScrollEnd(scrollEvent(7.2 * spacing));
    assert.deepEqual(h.changed, [40], 'a deliberate fling snaps to the nearest mark');
    ui.ruler.props.onMomentumScrollEnd(scrollEvent(8 * spacing));
    assert.deepEqual(h.changed, [40], 'duplicate settle events do not write twice');

    ui = h.render(40);
    ui.ruler.props.onScrollBeginDrag();
    ui.ruler.props.onScrollEndDrag(scrollEvent(5.8 * spacing));
    assert.deepEqual(h.changed, [40, 35], 'a slow release commits without a momentum event');
    ui.ruler.props.onMomentumScrollEnd(scrollEvent(5.8 * spacing));
    assert.deepEqual(h.changed, [40, 35]);

    ui = h.render(35);
    ui.ruler.props.onScrollBeginDrag();
    ui.ruler.props.onScrollEndDrag(scrollEvent(6 * spacing));
    assert.deepEqual(h.changed, [40, 35], 'reselecting the controlled value is a no-op');

    for (const [x, expected] of [[-2 * spacing, 5], [100 * spacing, 180]]) {
      ui.ruler.props.onScrollBeginDrag();
      ui.ruler.props.onScrollEndDrag(scrollEvent(x));
      assert.equal(h.changed.at(-1), expected, 'overscroll clamps to a real endpoint');
      ui = h.render(expected);
    }

    for (const [value, action, expected] of [
      [37, 'increment', 40], [37, 'decrement', 35],
      [40, 'increment', 45], [40, 'decrement', 35],
      [5, 'increment', 10], [180, 'decrement', 175],
    ]) {
      ui = h.render(value);
      const before = h.changed.length;
      ui.adjustable.props.onAccessibilityAction({ nativeEvent: { actionName: action } });
      assert.deepEqual(h.changed.slice(before), [expected], `${action} from ${value} reaches ${expected}`);
      assert.equal(h.scrollCalls.at(-1).x, ui.ticks.findIndex(cell => Number(cell.key) === expected) * spacing);
      ui = h.render(expected);
      assert.equal(ui.adjustable.props.accessibilityValue.now, expected);
    }
    for (const [value, action] of [[5, 'decrement'], [180, 'increment'], [37, 'activate']]) {
      ui = h.render(value);
      const before = h.changed.length;
      ui.adjustable.props.onAccessibilityAction({ nativeEvent: { actionName: action } });
      assert.equal(h.changed.length, before, 'bounds and unrelated actions never emit a value');
    }
    assert.ok(h.changed.every(value => value % 5 === 0 && value >= 5 && value <= 180));
  }

  const disabled = dialHarness({ disabled: true });
  const ui = disabled.render();
  assert.equal(ui.ruler.props.scrollEnabled, false);
  assert.equal(ui.adjustable.props.accessibilityState.disabled, true);
  ui.ruler.props.onScrollBeginDrag();
  ui.ruler.props.onScrollEndDrag(scrollEvent(0));
  for (const actionName of ['increment', 'decrement']) {
    ui.adjustable.props.onAccessibilityAction({ nativeEvent: { actionName } });
  }
  assert.deepEqual(disabled.changed, [], 'disabled controls cannot change the duration');

  const reduced = dialHarness({ reducedMotion: true });
  const reducedUi = reduced.render();
  reducedUi.adjustable.props.onAccessibilityAction({ nativeEvent: { actionName: 'increment' } });
  assert.deepEqual(reduced.changed, [40]);
  assert.equal(reduced.scrollCalls.at(-1).animated, false);
});

test('native pixel snapping keeps a reselected mark centred at fractional font scales and densities', () => {
  for (const fontScale of [1.2, 1.3, 1.5]) {
    for (const density of [1, 1.5, 2.625, 3.5]) {
      const h = dialHarness({ value: 165, fontScale, density });
      let ui = h.render();
      ui.adjustable.props.onLayout({ nativeEvent: { layout: { width: 358 } } });
      ui = h.render();
      const nativeIntervalPx = Math.trunc(Math.fround(
        Math.fround(ui.ruler.props.snapToInterval) * Math.fround(density),
      ));
      const index = ui.ticks.findIndex(cell => Number(cell.key) === 165);
      const settledOffset = index * nativeIntervalPx / density;
      const cellWidth = ui.ticks[0].props.style.width;
      const inset = ui.ruler.props.contentContainerStyle.paddingHorizontal;
      assert.ok(Math.abs(cellWidth * density - nativeIntervalPx) < 1e-4,
        'rendered spacing must match Android’s integer-pixel snap interval');
      const centre = inset + index * cellWidth + cellWidth / 2 - settledOffset;
      assert.ok(Math.abs(centre - 358 / 2) < 1 / density, 'a late mark stays centred to within one physical pixel');
      ui.ruler.props.onScrollBeginDrag();
      ui.ruler.props.onMomentumScrollEnd(scrollEvent(settledOffset));
      assert.deepEqual(h.changed, [], 'reselecting the same mark must not rely on a state change to align it');
    }
  }
});

test('major ticks carry readable subtitle labels in an enlarged, single accessible ruler', () => {
  for (const isDark of [false, true]) {
    for (const fontScale of [1, 1.5, 2, 3]) {
      const h = dialHarness({ isDark, fontScale, accessibilityLabel: '调整倒计时长度' });
      let ui = h.render();
      ui.adjustable.props.onLayout({ nativeEvent: { layout: { width: 358 } } });
      ui = h.render();
      const labels = ui.nodes(n => n.type === 'Text');
      assert.deepEqual(labels.map(n => n.props.children),
        [15, 30, 45, 60, 75, 90, 105, 120, 135, 150, 165, 180],
        'only 15-minute major marks have numbers, with no redundant swipe hint');
      assert.equal(ui.nodes(n => n.props?.accessibilityRole === 'adjustable').length, 1);
      assert.equal(ui.nodes(n => n.props?.accessibilityRole === 'button').length, 0);
      assert.equal(ui.adjustable.props.accessible, true);
      assert.equal(ui.adjustable.props.accessibilityLabel, '调整倒计时长度');
      assert.equal(ui.adjustable.props.accessibilityValue.text, '37 分钟');
      assert.deepEqual(Array.from(ui.adjustable.props.accessibilityActions, action => [action.name, action.label]),
        [['increment', '增加5分钟'], ['decrement', '减少5分钟']]);
      assert.equal(ui.ruler.props.accessible, false);
      assert.equal(ui.ruler.props.importantForAccessibility, 'no-hide-descendants', 'visual numbers do not become extra TalkBack stops');
      assert.equal(ui.ruler.props.accessibilityElementsHidden, true);
      assert.ok(ui.adjustable.props.style.height >= 96, 'the scroll target fills the enlarged strip');
      assert.equal(ui.ruler.props.contentContainerStyle.height, ui.adjustable.props.style.height);

      for (const cell of ui.ticks) {
        const mark = cell.props.children[0];
        const label = cell.props.children[1];
        const major = Number(cell.key) % 15 === 0;
        assert.equal(Boolean(label), major, 'minor marks remain unlabelled');
        assert.equal(mark.props.style.backgroundColor, major ? h.paper.colors.tickMajor : h.paper.colors.tickMinor);
        if (!label) continue;
        const [type, placement] = label.props.style;
        assert.equal(type.fontSize, h.paper.typography.captionMeta.fontSize);
        assert.equal(type.lineHeight, h.paper.typography.captionMeta.lineHeight);
        assert.equal(placement.color, h.paper.colors.secondary, 'numbers use the readable colour tier in both themes');
        assert.equal(label.props.allowFontScaling, undefined, 'labels follow the system font size');
        assert.ok(placement.top + type.lineHeight * fontScale + tokens.YanjiSpacing.lg <= cell.props.style.height,
          `fontScale=${fontScale}: label and bottom padding fit in the actual cell`);
        assert.ok(placement.width >= type.fontSize * fontScale * 3, 'a three-digit mark has room at large font sizes');
        assert.ok(placement.width <= cell.props.style.width * 3, 'neighbouring major labels do not overlap');
        assert.ok(cell.props.style.height - mark.props.style.bottom < placement.top, 'labels sit below the tick baseline');
        assert.equal(label.props.children, Number(cell.key));
      }
    }
  }
});

/**
 * The real FocusScreen with native/time adapters mocked. Pass session: null for
 * the ready state. Both states read task titles from the shared daily snapshot.
 */
function focusScreenHarness({ session = {}, tasks = [], subjects = [], isDark = false, focusPreset = null, nowMs = null } = {}) {
  const alerts = [];
  const calls = [];
  const noop = () => {};
  const states = [];
  let stateIndex = 0;
  let effects = [];
  /**
   * Every write the page's own state setters perform.
   *
   * This is the measurable side of PERF-05: a drag must not commit anything to
   * the page root, so this counter has to stay still while the picker previews.
   * Counting renders is not possible without a renderer, and counting state
   * commits is the cost that actually re-rendered the whole ready screen.
   */
  const rootCommits = [];
  /**
   * The state slots the current component is allocating into.
   *
   * The `useState` mock is index-based, so a sub-component inspected through
   * `expand` must not allocate into — or later write into — the page's array.
   * Each owner therefore gets its own array, captured by its own setters: a
   * child that previews 27 marks writes its own slots, and the page root records
   * nothing.
   */
  let container = states;
  let owner = 'root';
  const childStates = new Map();
  const active = session === null ? null : {
    sessionId: 'test-owned', subjectId: 'math', subjectName: '数学',
    mode: 'COUNTDOWN', taskId: null, startTime: 0,
    elapsedSeconds: 10, remainingSeconds: 100, phase: 'FOCUS',
    isPaused: true, isCountdown: true,
    ...session,
  };
  const paper = {
    colors: tokens.YanjiPaperColors[isDark ? 'dark' : 'light'],
    radius: tokens.YanjiPaperRadius,
    layout: tokens.YanjiPaperLayout,
    typography: tokens.YanjiPaperTypography,
    immersive: tokens.YanjiPaperImmersive,
  };
  const mocks = {
    react: {
      useCallback: fn => fn, useMemo: fn => fn(), useEffect: effect => effects.push(effect), useRef: initial => ({ current: initial }),
      useState: initial => {
        const index = stateIndex++;
        const box = container;
        // Ownership is fixed when the slot is allocated, not when it is later
        // written: a child's setter is usually called from the parent's event
        // handler, long after `expand` restored the page as the current owner.
        const isRoot = owner === 'root';
        if (!(index in box)) box[index] = typeof initial === 'function' ? initial() : initial;
        return [box[index], next => {
          if (isRoot) rootCommits.push(index);
          box[index] = typeof next === 'function' ? next(box[index]) : next;
        }];
      },
    },
    'react-native': {
      Alert: { alert: (...args) => alerts.push(args) }, View: 'View',
      ScrollView: 'ScrollView', Pressable: 'Pressable', Text: 'Text',
      useWindowDimensions: () => ({ width: 358, height: 800, fontScale: 1 }),
      Animated: { Value: function(v) { this.val = v; this.interpolate = () => 0; }, timing: () => ({ start: fn => fn && fn() }), View: 'View' },
      Easing: { out: () => () => 0, cubic: () => 0, in: () => () => 0 },
    },
    'react-native-safe-area-context': { useSafeAreaInsets: () => ({ bottom: 24 }) },
    'react-native-svg': {
      default: 'Svg', Svg: 'Svg', Defs: 'Defs', LinearGradient: 'LinearGradient',
      Rect: 'Rect', Stop: 'Stop', Circle: 'Circle', Path: 'Path',
    },
    '../bridge': {
      YanjiDataNative: { getSubjects: async () => subjects },
      onDataChanged: () => noop,
      YanjiTimerNative: {
        startFocus: async (...args) => { calls.push(['start', ...args]); },
        discardTimer: async () => { calls.push('discard'); },
        completeTimer: async () => { calls.push('complete'); },
        resumeTimer: async () => { calls.push('resume'); },
        pauseTimer: async () => { calls.push('pause'); },
      },
    },
    '../components/YanjiUI': {
      YanjiIcon: noop, YanjiPaperSegmentedControl: 'YanjiPaperSegmentedControl',
      YanjiPrimaryButton: 'YanjiPrimaryButton',
    },
    '../components/FocusVernierDial': { FocusVernierDial: 'FocusVernierDial' },
    '../components/RollingTimeDisplay': { RollingTimeDisplay: 'RollingTimeDisplay' },
    '../components/RecordMomentModal': { RecordMomentModal: noop },
    // 补录专注是独立弹窗；这里只验证 FocusScreen 的装配，弹窗本身由自己的用例覆盖。
    '../components/BackfillFocusModal': { BackfillFocusModal: noop },
    '../navigation/NavigationShell': {
      // A preset is how a plan reaches the ready screen with its task attached.
      useNavigationState: () => ({ focusPreset }),
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
  const { FocusScreen } = loadTypeScript('src/screens/FocusScreen.tsx', mocks, nowMs === null ? {} : {
    // The projected end time is `now + duration`, so a printed minute can only
    // be asserted against a pinned clock. `loadTypeScript` runs the module in its
    // own VM context, so the pinned `Date` has to be injected there — patching
    // `globalThis` in this file would not reach it.
    Date: class extends Date {
      constructor(...args) {
        super(...(args.length ? args : [nowMs]));
      }
      static now() { return nowMs; }
    },
  });
  const render = (pageVisible = true) => {
    stateIndex = 0;
    effects = [];
    return FocusScreen({ visible: pageVisible });
  };
  /**
   * Instantiate a local sub-component for inspection with its own state slots.
   *
   * The `useState` mock is index-based, so calling a child during a tree walk
   * would allocate into the parent's array and later write over the parent's
   * values. Each inspected child gets a stable array keyed by its own type, and
   * the page keeps its own array and effect queue untouched, so a walk can look
   * inside a child without corrupting a later render.
   */
  const expand = node => {
    const key = node.type;
    if (!childStates.has(key)) childStates.set(key, []);
    const savedContainer = container;
    const savedIndex = stateIndex;
    const savedEffects = effects;
    container = childStates.get(key);
    stateIndex = 0;
    owner = 'child';
    try {
      return node.type(node.props);
    } finally {
      container = savedContainer;
      stateIndex = savedIndex;
      effects = savedEffects;
      owner = 'root';
    }
  };
  return {
    root: render(), alerts, calls, paper, render, expand,
    /** Page-root state commits since the last `clearCommits()`. */
    rootCommits,
    clearCommits: () => { rootCommits.length = 0; },
    /** Effects queued by the most recent render, for tests that need to settle them. */
    pendingEffects: () => effects,
    async loadSubjects() {
      for (const effect of effects) effect();
      await new Promise(resolve => setImmediate(resolve));
      render();
      for (const effect of effects) effect();
      return render();
    },
  };
}

/**
 * Walk the rendered tree, descending into the local sub-components that carry
 * the duration picker's own UI.
 *
 * `FocusScreen` now composes real sub-components (the duration picker and the
 * projected end-time label) that the harness does not mock, so their elements
 * appear as `type: <function>`. A host-string-only walk would report the ruler
 * as missing even though it renders.
 *
 * Only `DurationPicker`/`EstimatedEndTime` are expanded on purpose: expanding
 * every function component would also instantiate `FocusTimerReadout`, whose
 * `useTimerTick` is mocked to throw, and that guard exists to prove the *root*
 * screen does not subscribe to second ticks — it is not a statement about the
 * leaf that is supposed to.
 */
const EXPANDED_COMPONENTS = new Set(['DurationPicker', 'EstimatedEndTime']);

/** The text a rendered element carries, following nested `Text` children. */
function textOf(node) {
  if (Array.isArray(node)) return node.map(textOf).join('');
  if (node === null || node === undefined) return '';
  if (typeof node === 'string' || typeof node === 'number') return String(node);
  if (typeof node !== 'object' || !('type' in node)) return '';
  return textOf(node.props?.children);
}

function findAll(node, predicate, matches = [], expand = null) {
  if (Array.isArray(node)) for (const element of node) findAll(element, predicate, matches, expand);
  else if (node && typeof node === 'object' && 'type' in node) {
    if (predicate(node)) matches.push(node);
    const expandable = typeof node.type === 'function'
      && EXPANDED_COMPONENTS.has(node.type.name);
    if (expandable && expand) findAll(expand(node), predicate, matches, expand);
    else findAll(node.props?.children, predicate, matches, expand);
  }
  return matches;
}

test('the ready screen offers the countdown ruler without preset or custom-duration buttons', () => {
  const harness = focusScreenHarness({ session: null });
  const { root, calls } = harness;
  // Expanding a child needs its own state slots; the walk asks the harness for
  // that instead of calling the component inline.
  const walk = (node, predicate) => findAll(node, predicate, [], harness.expand);
  assert.equal(root.props.testID, 'focus-ready');
  const dials = walk(root, n => n.type === 'FocusVernierDial');
  assert.equal(dials.length, 1);
  const dial = dials[0];
  assert.deepEqual({
    value: dial.props.valueMinutes, min: dial.props.minMinutes,
    max: dial.props.maxMinutes, step: dial.props.stepMinutes,
  }, { value: 45, min: 5, max: 180, step: 5 });
  assert.equal(dial.props.accessibilityLabel, '调整倒计时长度');
  // The ruler stays full-bleed: the dial sits inside the strip that cancels the
  // page gutter, so the scale spans edge to edge. It is now nested one level
  // deeper (inside the duration picker), so match on the bleeding container
  // rather than on "whose only child is the dial".
  const strip = walk(root, n => n.props?.style?.marginHorizontal === -tokens.YanjiPaperLayout.pageGutter)[0];
  assert.ok(strip, 'the ruler is wrapped in the full-bleed strip');
  assert.equal(walk(strip, n => n.type === 'FocusVernierDial').length, 1,
    'the strip carries exactly one ruler');
  assert.equal(walk(root, n => n.props?.accessibilityRole === 'button' || n.type === 'YanjiPrimaryButton').length, 8,
    'the ready state includes record, start, and 6 quick preset chips');
  const presetChips = walk(root, n => typeof n.props?.testID === 'string' && n.props.testID.startsWith('focus-preset-'));
  assert.equal(presetChips.length, 6, 'provides 6 quick preset duration chips');
  const chip45 = walk(root, n => n.props?.testID === 'focus-preset-45')[0];
  assert.equal(chip45.props.accessibilityState.selected, true);
  assert.equal(walk(root, n => /^(预设 |自定义专注时长)/.test(n.props?.accessibilityLabel ?? '')).length, 0);
  assert.equal(walk(root, n => n.type === 'Modal').length, 0, 'the removed custom picker leaves no hidden modal');
  assert.equal(walk(root, n => n.props?.testID === 'focus-duration-value')[0].props.children, '45:00');
  assert.deepEqual(calls, [], 'rendering the ready state does not touch a native timer');
});

test('the linked-plan band keeps one fixed height, so the dial cannot shift', () => {
  // 曾经只给这条带子加了 minHeight（32dp），而胶囊自己的关闭按钮要 44dp 的
  // 触控下限，于是「有关联」时带子约 52dp、「取消关联」后回落到 32dp，
  // 数字会向上跳 20dp。带宽必须写死、与内容无关，才可能锚住下面的时间。
  // `root` is the first paint, before effects run; the preset lands in an
  // effect, so the state under test is the paint after that effect.
  const settle = h => {
    for (const effect of h.pendingEffects()) effect();
    h.render();
    for (const effect of h.pendingEffects()) effect();
    return h.render();
  };
  const withoutPlan = settle(focusScreenHarness({ session: null }));
  const withPlan = settle(focusScreenHarness({
    session: null,
    focusPreset: {
      taskId: 'task-1', subjectId: 'math', subjectName: '数学',
      title: '学完前三章', plannedMinutes: 45,
    },
  }));

  // The band is the direct parent of the chip, and of nothing else.
  const bands = tree => findAll(tree, n => n.props?.style?.height === 40 &&
    n.props?.style?.alignSelf === 'stretch');
  const bareBand = bands(withoutPlan)[0];
  const chipBand = bands(withPlan)[0];
  assert.ok(bareBand, 'the band renders even with no plan attached');
  assert.ok(chipBand, 'and stays the same height once a plan is attached');
  assert.equal(bareBand.props.style.height, chipBand.props.style.height,
    'attaching or clearing a plan never changes the band height');

  // The chip body is the same height as its slot, not a content-sized box.
  const chip = findAll(withPlan, n => {
    const s = n.props?.style;
    return s && s.height === 40 && s.flexDirection === 'row' && s.borderRadius !== undefined;
  })[0];
  assert.ok(chip, 'the chip is exactly as tall as the slot it sits in');
  const cancel = findAll(withPlan, n => n.props?.accessibilityLabel === '取消关联任务')[0];
  assert.ok(cancel, 'the plan can still be cleared');
  assert.ok(cancel.props.hitSlop, 'its touch target survives through hit slop, not by growing the chip');
});

function sharedControlsHarness(isDark = false) {
  const paper = {
    colors: tokens.YanjiPaperColors[isDark ? 'dark' : 'light'],
    layout: tokens.YanjiPaperLayout, radius: tokens.YanjiPaperRadius,
    typography: tokens.YanjiPaperTypography, motion: tokens.YanjiPaperMotion,
  };
  return loadTypeScript('src/components/YanjiUI.tsx', {
    react: { useEffect: () => {} },
    'react-native': { View: 'View', Text: 'Text', Pressable: 'Pressable' },
    'react-native-reanimated': {
      default: { View: 'AnimatedView' }, useReducedMotion: () => true,
      useSharedValue: value => ({ value }), useAnimatedStyle: fn => fn(),
    },
    'react-native-svg': {},
    '../theme/ThemeProvider': { useYanjiTheme: () => ({ paper, isDark, shadow: { sheet: () => ({}) } }) },
    './icons': {},
  });
}

test('the focus mode switch removes both outlines and enlarges its fill without shrinking radio targets', () => {
  const h = focusScreenHarness({ session: null });
  const props = findAll(h.root, n => n.type === 'YanjiPaperSegmentedControl')[0].props;
  assert.equal(props.borderless, true);
  const controls = sharedControlsHarness();
  const plain = controls.YanjiPaperSegmentedControl(props);
  const outlined = controls.YanjiPaperSegmentedControl({ ...props, borderless: false });
  const track = findAll(plain, n => n.props?.pointerEvents === 'none')[0];
  assert.equal(track.props.style.borderWidth, 0);
  const radioElement = plain.props.children[1][0];
  const radio = radioElement.type(radioElement.props);
  const oldRadioElement = outlined.props.children[1][0];
  const oldRadio = oldRadioElement.type(oldRadioElement.props);
  const fill = findAll(radio, n => n.type === 'AnimatedView')[0].props.style[0];
  const oldFill = findAll(oldRadio, n => n.type === 'AnimatedView')[0].props.style[0];
  assert.equal(fill.borderWidth, 0);
  assert.ok(fill.top < oldFill.top && fill.bottom < oldFill.bottom, 'selected fill occupies more of the track');
  assert.ok(radio.props.style({ pressed: false }).minHeight >= tokens.YanjiTouch.min);
});

test('ready subject selection uses clean brand border and indicator underline without colored dots', async () => {
  const subjects = [
    { id: 'math', name: 'Math', colorHex: '#356AE6', enabled: true, parentId: null, isCategory: true, sortOrder: 1 },
    { id: 'algebra', name: 'Algebra', colorHex: '#2D7A5E', enabled: true, parentId: 'math', isCategory: false, sortOrder: 2 },
    { id: 'calculus', name: 'Calculus', colorHex: '#875400', enabled: true, parentId: 'math', isCategory: false, sortOrder: 3 },
  ];
  for (const isDark of [false, true]) {
    const h = focusScreenHarness({ session: null, subjects, isDark });
    let tree = await h.loadSubjects();
    const subject = name => findAll(tree, n => n.props?.accessibilityLabel === name)[0];
    
    // Level 1 category check
    const cat = subject('Math');
    assert.equal(cat.props.accessibilityState.selected, true);
    const catStyle = typeof cat.props.style === 'function' ? cat.props.style({ pressed: false }) : cat.props.style;
    assert.equal(catStyle.borderColor, h.paper.colors.brand);
    assert.equal(catStyle.borderWidth, tokens.YanjiPaperLayout.hairlineWidth);
    assert.equal(findAll(cat, n => n.props?.style?.backgroundColor === subjects[0].colorHex).length, 0);

    // Level 2 sub-subject check
    const child = subject('Algebra');
    assert.equal(child.props.accessibilityState.selected, true);
    assert.ok(child.props.style({ pressed: false }).minHeight >= tokens.YanjiTouch.min);
    const indicator = findAll(child, n => n.props?.style?.backgroundColor === h.paper.colors.brand)[0];
    assert.ok(indicator, 'selected sub-subject has brand indicator underline');
    assert.equal(findAll(child, n => n.props?.style?.backgroundColor === subjects[1].colorHex).length, 0);

    subject('Calculus').props.onPress();
    tree = h.render();
    assert.equal(subject('Calculus').props.accessibilityState.selected, true);
    assert.equal(subject('Algebra').props.accessibilityState.selected, false);
  }
});

test('start button uses clean 开始专注 without detail duration', async () => {
  const subjects = [{ id: 'math', name: 'Math', colorHex: '#356AE6', enabled: true, parentId: null, isCategory: false, sortOrder: 1 }];
  const h = focusScreenHarness({ session: null, subjects });
  let tree = await h.loadSubjects();
  const start = findAll(tree, n => n.type === 'YanjiPrimaryButton')[0];
  assert.ok(start);
  assert.equal(start.props.label, '开始专注');
  assert.equal(start.props.detail, undefined);
  assert.equal(start.props.disabled, false);
  const controls = sharedControlsHarness();
  const focusButton = controls.YanjiPrimaryButton(start.props);
  const homeButton = controls.YanjiPrimaryButton({ label: '开始一轮专注', onPress: () => {} });
  const style = focusButton.props.style({ pressed: false })[0];
  const homeStyle = homeButton.props.style({ pressed: false })[0];
  assert.equal(style.backgroundColor, homeStyle.backgroundColor);
  assert.equal(style.borderRadius, homeStyle.borderRadius);
  await start.props.onPress();
  assert.deepEqual(h.calls[0].slice(0, 4), ['start', 'math', 'Math', 'COUNTDOWN']);
  tree = h.render();
  const mode = findAll(tree, n => n.type === 'YanjiPaperSegmentedControl')[0];
  mode.props.onChange('STOPWATCH');
  tree = h.render();
  assert.equal(findAll(tree, n => n.type === 'YanjiPrimaryButton')[0].props.detail, undefined);
});

test('the immersion subtitle names the bound task and stays quiet when unbound', () => {
  const bound = focusScreenHarness({
    session: { taskId: 'task-1' },
    tasks: [{ id: 'task-1', title: '王道操作系统 · 页面置换算法' }],
  });
  const named = findAll(bound.root, n => n.props?.children === '王道操作系统 · 页面置换算法');
  assert.equal(named.length, 1, 'a bound session must name the task it is running for');

  // A session started without a task keeps quiet without standing copy
  const unbound = focusScreenHarness({ session: { taskId: null } });
  assert.equal(findAll(unbound.root, n => n.props?.children === '专注已暂歇 · 随时继续当前进度').length, 0);
  assert.equal(findAll(unbound.root, n => n.props?.children === '王道操作系统 · 页面置换算法').length, 0);

  // … and so does one whose task is not in today's snapshot (it outlived its
  // day). A stale or invented title would be worse than the quiet line.
  const stale = focusScreenHarness({
    session: { taskId: 'task-yesterday' },
    tasks: [{ id: 'task-1', title: '王道操作系统 · 页面置换算法' }],
  });
  assert.equal(findAll(stale.root, n => n.props?.children === '王道操作系统 · 页面置换算法').length, 0);
  assert.equal(findAll(stale.root, n => n.props?.children === '专注已暂歇 · 随时继续当前进度').length, 0);
});

test('the horizon tide carries its alpha in stopOpacity, never in the colour', () => {
  // `react-native-svg` reads a gradient stop's alpha from `stopOpacity` alone —
  // it masks any alpha embedded in `stopColor` back out and defaults a missing
  // `stopOpacity` to fully opaque. An rgba() stop colour therefore painted the
  // tide as a hard-edged solid slab instead of a 14% wash. This guards the
  // shape that actually renders.
  const { root } = focusScreenHarness();
  const stops = findAll(root, n => n.type === 'Stop');
  assert.equal(stops.length, 2, 'the tide gradient needs its two stops');

  const [start, end] = stops;
  assert.equal(start.props.stopOpacity, 0, 'the top of the wash is fully transparent');
  assert.ok(
    typeof end.props.stopOpacity === 'number' && end.props.stopOpacity > 0 && end.props.stopOpacity < 1,
    `the bottom stop must carry a fractional alpha, saw ${end.props.stopOpacity}`,
  );
  for (const stop of stops) {
    assert.ok(
      !/^rgba\(/i.test(String(stop.props.stopColor)),
      `stop colour must not smuggle alpha: ${stop.props.stopColor}`,
    );
  }

  // The gradient axis must span the box, not collapse to a single user unit.
  const gradient = findAll(root, n => n.type === 'LinearGradient')[0];
  assert.equal(gradient.props.y2, '100%');
});

test('paused discard opens native confirmation; cancellation never calls discardTimer', async () => {
  const { root, alerts, calls } = focusScreenHarness();
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

test('dragging the ruler updates the picker without re-rendering the ready screen', () => {
  // 拖动曾经直接写页面根状态：每跨 5 分钟刻度，科目区、任务带、尺盘和静态渐变
  // 都跟着重渲染。现在即时值留在 DurationPicker 子树里，页面只在停稳时收到一次值。
  const harness = focusScreenHarness({ session: null });
  const walk = (node, predicate) => findAll(node, predicate, [], harness.expand);

  const dialOf = root => walk(root, n => n.type === 'FocusVernierDial')[0];
  const readoutOf = root => walk(root, n => n.props?.testID === 'focus-duration-value')[0];

  const dial = dialOf(harness.root);
  assert.ok(dial, 'the ready state renders the ruler');
  assert.equal(readoutOf(harness.root).props.children, '45:00');

  // The dial is controlled by the picker's own preview state, so a drag only
  // moves the value inside that subtree. `onChangeMinutes` is the live channel
  // and `onSettleMinutes` is the single commit the page hears.
  assert.equal(typeof dial.props.onChangeMinutes, 'function');
  assert.equal(typeof dial.props.onSettleMinutes, 'function');
  assert.notEqual(dial.props.onChangeMinutes, dial.props.onSettleMinutes,
    'the live preview and the committed value are separate channels');

  // Committing 60 is what the page keeps: the preset row and the projected end
  // time both follow it, and the readout agrees.
  dial.props.onSettleMinutes(60);
  const settled = harness.render();
  assert.equal(
    walk(settled, n => n.props?.testID === 'focus-preset-60')[0].props.accessibilityState.selected,
    true,
    'the confirmed value is what the preset row reflects'
  );
  assert.equal(dialOf(settled).props.valueMinutes, 60,
    'the dial is driven by the committed value after settling');
  assert.equal(readoutOf(settled).props.children, '60:00');

  // A task preset that is not on the 5-minute grid must survive as-is.
  const presetHarness = focusScreenHarness({
    session: null,
    focusPreset: { taskId: 't', subjectId: 'math', subjectName: '数学', plannedMinutes: 37 },
  });
  for (const effect of presetHarness.pendingEffects()) effect();
  const afterPreset = presetHarness.render();
  const presetDial = findAll(afterPreset, n => n.type === 'FocusVernierDial', [], presetHarness.expand)[0];
  assert.equal(presetDial.props.valueMinutes, 37,
    'a real planned duration is projected, not rounded to the nearest mark');
});

test('a live drag commits nothing to the page root', () => {
  // The measurable claim of PERF-05: dragging across marks used to write
  // `durationMinutes` into the page root on every 5-minute step, which redrew
  // the subject chips, the task band, the ruler and the static gradient. The
  // preview now lives in the picker subtree, so the page root must stay untouched
  // for the whole gesture.
  const harness = focusScreenHarness({ session: null });
  const walk = (node, predicate) => findAll(node, predicate, [], harness.expand);
  const dial = walk(harness.root, n => n.type === 'FocusVernierDial')[0];
  assert.ok(dial, 'the ready state renders the ruler');

  dial.props.onDragStateChange(true);
  harness.clearCommits();
  // 45 → 180 in 5-minute steps is 27 marks; the old code committed all of them.
  const marks = [50, 55, 60, 65, 70, 75, 80, 85, 90, 95, 100, 105, 110, 115, 120,
    125, 130, 135, 140, 145, 150, 155, 160, 165, 170, 175, 180];
  let dragging = harness.render();
  for (const mark of marks) {
    walk(dragging, n => n.type === 'FocusVernierDial')[0].props.onChangeMinutes(mark);
    dragging = harness.render();
  }
  assert.deepEqual(harness.rootCommits, [],
    'a whole drag gesture writes no page-root state');

  // The preview itself did move, so the silence is not a broken control.
  assert.equal(walk(dragging, n => n.props?.testID === 'focus-duration-value')[0].props.children, '180:00',
    'the live preview follows the finger without committing');
  // …and no preset chip claims the dragged value before it settles.
  for (const preset of [45, 60, 90, 120, 180]) {
    assert.equal(walk(dragging, n => n.props?.testID === `focus-preset-${preset}`)[0]
      .props.accessibilityState.selected, false,
    'no preset is marked selected while the finger is still moving');
  }
});

test('one settle commits the confirmed value once, and start uses it', async () => {
  const subjects = [{ id: 'math', name: '数学', colorHex: '#356AE6', enabled: true, parentId: null, sortOrder: 1 }];
  const harness = focusScreenHarness({ session: null, subjects });
  const walk = (node, predicate) => findAll(node, predicate, [], harness.expand);
  let tree = await harness.loadSubjects();
  const dial = walk(tree, n => n.type === 'FocusVernierDial')[0];

  // Preview far away from the committed value first, then settle on 90.
  dial.props.onDragStateChange(true);
  dial.props.onChangeMinutes(55);
  harness.render();
  harness.clearCommits();
  // A settle ends the gesture, so the picker clears its own drag flag as it
  // commits — that is what lets the preset row mark the confirmed value again.
  walk(harness.render(), n => n.type === 'FocusVernierDial')[0].props.onDragStateChange(false);
  walk(harness.render(), n => n.type === 'FocusVernierDial')[0].props.onSettleMinutes(90);
  assert.equal(harness.rootCommits.length, 2,
    'settling writes the duration pair exactly once (the value and its projection)');
  assert.equal(new Set(harness.rootCommits).size, 2,
    'the two writes are the two duration slots, not one slot twice');

  tree = harness.render();
  assert.equal(walk(tree, n => n.props?.testID === 'focus-preset-90')[0].props.accessibilityState.selected, true);
  assert.equal(walk(tree, n => n.props?.testID === 'focus-duration-value')[0].props.children, '90:00');

  // The started session carries the confirmed value, not the abandoned preview.
  // `startFocus(subjectId, subjectName, mode, note, taskId, plannedMinutes)`.
  await walk(tree, n => n.type === 'YanjiPrimaryButton')[0].props.onPress();
  assert.equal(harness.calls[0][0], 'start');
  assert.equal(harness.calls[0][6], 90,
    'the native session starts with the last confirmed duration');
});

test('the projected end time is its own minute leaf and corrects on return', () => {
  // 预计结束时间曾经由页面根状态每 15 秒刷新一次，整页跟着重绘。现在它是一条
  // 独立文字叶子：只有它自己按分钟更新，回到前台/重新可见时立即重读时钟。
  //
  // The projected value is `now + duration`, so the clock is pinned: 15:20 on
  // 2026-10-16, which is the same instant the daily snapshot fixtures use.
  const frozen = new Date('2026-10-16T15:20:00').getTime();
  const harness = focusScreenHarness({ session: null, nowMs: frozen });
  const walk = (node, predicate) => findAll(node, predicate, [], harness.expand);

  const labelOf = root => walk(root, n => n.props?.testID === 'focus-estimated-end')[0];
  const label = labelOf(harness.root);
  assert.ok(label, 'the ready state prints the projected end time as its own node');
  // 15:20 + 45 minutes = 16:05.
  assert.equal(textOf(label), '预计 16:05 结束本轮');

  // Its clock is inside the leaf, not the page: the page root owns no timer for
  // it, so becoming visible again re-reads instead of trusting a stale interval
  // that the platform may have throttled while hidden.
  const leaf = walk(harness.root, n => typeof n.type === 'function' && n.type.name === 'EstimatedEndTime')[0];
  assert.ok(leaf, 'the label is a real sub-component, so its timer can be isolated');
  assert.equal(leaf.props.durationMinutes, 45);
  assert.equal(leaf.props.active, true, 'the leaf follows page visibility');

  // Leaving the page stops the leaf clock; returning starts it again from a
  // fresh read rather than from a minute it accumulated while hidden.
  const hidden = harness.render(false);
  assert.equal(
    walk(hidden, n => typeof n.type === 'function' && n.type.name === 'EstimatedEndTime')[0].props.active,
    false,
    'a hidden page stops the projected-end clock'
  );

  // A non-grid committed duration projects exactly, without rounding.
  const presetHarness = focusScreenHarness({
    session: null,
    nowMs: frozen,
    focusPreset: { taskId: 't', subjectId: 'math', subjectName: '数学', plannedMinutes: 37 },
  });
  for (const effect of presetHarness.pendingEffects()) effect();
  const projected = findAll(presetHarness.render(),
    n => n.props?.testID === 'focus-estimated-end', [], presetHarness.expand)[0];
  // 15:20 + 37 minutes = 15:57 — not the nearest 5-minute mark (16:00).
  assert.equal(textOf(projected), '预计 15:57 结束本轮',
    'a 37-minute plan projects 15:20 + 37m, not the nearest 5-minute mark');
});
