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
function focusScreenHarness({ session = {}, tasks = [] } = {}) {
  const alerts = [];
  const calls = [];
  const noop = () => {};
  const active = session === null ? null : {
    sessionId: 'test-owned', subjectId: 'math', subjectName: '数学',
    mode: 'COUNTDOWN', taskId: null, startTime: 0,
    elapsedSeconds: 10, remainingSeconds: 100, phase: 'FOCUS',
    isPaused: true, isCountdown: true,
    ...session,
  };
  const paper = {
    colors: tokens.YanjiPaperColors.light,
    radius: tokens.YanjiPaperRadius,
    layout: tokens.YanjiPaperLayout,
    typography: tokens.YanjiPaperTypography,
    immersive: tokens.YanjiPaperImmersive,
  };
  const mocks = {
    react: {
      useCallback: fn => fn, useMemo: fn => fn(), useEffect: noop,
      useState: initial => [typeof initial === 'function' ? initial() : initial, noop],
    },
    'react-native': {
      Alert: { alert: (...args) => alerts.push(args) }, View: 'View',
      ScrollView: 'ScrollView', Pressable: 'Pressable', Text: 'Text',
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
      YanjiIcon: noop, YanjiPaperSegmentedControl: 'YanjiPaperSegmentedControl',
    },
    '../components/FocusVernierDial': { FocusVernierDial: 'FocusVernierDial' },
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

test('the ready screen offers the countdown ruler without preset or custom-duration buttons', () => {
  const { root, calls } = focusScreenHarness({ session: null });
  assert.equal(root.props.testID, 'focus-ready');
  const dials = findAll(root, n => n.type === 'FocusVernierDial');
  assert.equal(dials.length, 1);
  const dial = dials[0];
  assert.deepEqual({
    value: dial.props.valueMinutes, min: dial.props.minMinutes,
    max: dial.props.maxMinutes, step: dial.props.stepMinutes,
  }, { value: 45, min: 5, max: 180, step: 5 });
  assert.equal(dial.props.accessibilityLabel, '调整倒计时长度');
  const strip = findAll(root, n => n.props?.children === dial)[0];
  assert.ok(strip, 'the enlarged ruler is the strip’s only child');
  assert.equal(strip.props.style.marginHorizontal, -tokens.YanjiPaperLayout.pageGutter);
  assert.equal(findAll(root, n => n.props?.accessibilityRole === 'button').length, 2,
    'the empty ready state needs only record and start buttons');
  assert.equal(findAll(root, n => /^(预设 |自定义专注时长)/.test(n.props?.accessibilityLabel ?? '')).length, 0);
  assert.equal(findAll(root, n => n.type === 'Modal').length, 0, 'the removed custom picker leaves no hidden modal');
  assert.equal(findAll(root, n => n.props?.testID === 'focus-duration-value')[0].props.children, '45:00');
  assert.deepEqual(calls, [], 'rendering the ready state does not touch a native timer');
});

test('the immersion subtitle names the bound task and stays quiet when unbound', () => {
  const bound = focusScreenHarness({
    session: { taskId: 'task-1' },
    tasks: [{ id: 'task-1', title: '王道操作系统 · 页面置换算法' }],
  });
  const named = findAll(bound.root, n => n.props?.children === '王道操作系统 · 页面置换算法');
  assert.equal(named.length, 1, 'a bound session must name the task it is running for');

  // A session started without a task keeps the standing copy …
  const unbound = focusScreenHarness({ session: { taskId: null } });
  assert.equal(findAll(unbound.root, n => n.props?.children === '专注已暂歇 · 随时继续当前进度').length, 1);
  assert.equal(findAll(unbound.root, n => n.props?.children === '王道操作系统 · 页面置换算法').length, 0);

  // … and so does one whose task is not in today's snapshot (it outlived its
  // day). A stale or invented title would be worse than the quiet line.
  const stale = focusScreenHarness({
    session: { taskId: 'task-yesterday' },
    tasks: [{ id: 'task-1', title: '王道操作系统 · 页面置换算法' }],
  });
  assert.equal(findAll(stale.root, n => n.props?.children === '王道操作系统 · 页面置换算法').length, 0);
  assert.equal(findAll(stale.root, n => n.props?.children === '专注已暂歇 · 随时继续当前进度').length, 1);
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
