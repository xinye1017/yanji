import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './framework/load-typescript.js';

const tokens = loadTypeScript('src/theme/tokens.ts');

function rollingDisplayHarness({ valueMinutes = 45, fontScale = 1, isDark = false, reducedMotion = false } = {}) {
  const paper = {
    colors: tokens.YanjiPaperColors[isDark ? 'dark' : 'light'],
    radius: tokens.YanjiPaperRadius,
    layout: tokens.YanjiPaperLayout,
    typography: tokens.YanjiPaperTypography,
  };

  const reanimated = {
    useReducedMotion: () => reducedMotion,
    useSharedValue: initial => ({ value: initial }),
    useAnimatedStyle: fn => fn(),
    withTiming: (toValue, config, cb) => {
      if (cb) cb(true);
      return toValue;
    },
    Easing: { out: () => () => 0, cubic: () => 0 },
    default: { View: 'AnimatedView' },
  };

  const component = loadTypeScript('src/components/RollingTimeDisplay.tsx', {
    react: {
      useState: initial => [initial, () => {}],
      useRef: initial => ({ current: initial }),
      useEffect: () => {},
    },
    'react-native': {
      View: 'View',
      Text: 'Text',
      useWindowDimensions: () => ({ width: 358, height: 720, fontScale }),
    },
    'react-native-reanimated': reanimated,
    '../theme/ThemeProvider': { useYanjiTheme: () => ({ paper }) },
  }).RollingTimeDisplay;

  function elements(node, predicate, out = []) {
    if (Array.isArray(node)) {
      for (const element of node) elements(element, predicate, out);
    } else if (node && typeof node === 'object' && 'type' in node) {
      if (predicate(node)) out.push(node);
      // Function components are invoked so the walk reaches the markup they
      // render — otherwise every `Text` inside a `DigitSlot` stays invisible to
      // a caller that only looks at `props.children`.
      if (typeof node.type === 'function') {
        elements(node.type(node.props), predicate, out);
      } else {
        elements(node.props?.children, predicate, out);
      }
    }
    return out;
  }

  /** The style array a `Text` was given, flattened to the merged object. */
  function mergedStyle(node) {
    const style = node.props?.style;
    return Array.isArray(style) ? Object.assign({}, ...style.filter(Boolean)) : (style ?? {});
  }

  function render(val = valueMinutes) {
    const tree = component({ valueMinutes: val });
    const texts = elements(tree, n => n.type === 'Text');
    const slots = elements(tree, n => typeof n.type === 'function' && n.props?.digit !== undefined);
    return { tree, texts, slots, textValues: texts.map(t => t.props?.children) };
  }

  return { render, mergedStyle };
}

test('RollingTimeDisplay renders correct digits and fixed suffix', () => {
  const harness = rollingDisplayHarness();

  // Test 5 minutes -> "0" "5" ":00"
  const ui5 = harness.render(5);
  assert.equal(ui5.slots.length, 2, '5 minutes has tens and units slots');
  assert.equal(ui5.slots[0].props.digit, '0');
  assert.equal(ui5.slots[1].props.digit, '5');
  assert.ok(ui5.textValues.includes('分'), 'includes the fixed 「分」 suffix');

  // Test 45 minutes -> "4" "5" ":00"
  const ui45 = harness.render(45);
  assert.equal(ui45.slots.length, 2);
  assert.equal(ui45.slots[0].props.digit, '4');
  assert.equal(ui45.slots[1].props.digit, '5');

  // Test 100 minutes -> "1" "0" "0" ":00"
  const ui100 = harness.render(100);
  assert.equal(ui100.slots.length, 3, '100 minutes renders hundreds slot');
  assert.equal(ui100.slots[0].props.digit, '1');
  assert.equal(ui100.slots[1].props.digit, '0');
  assert.equal(ui100.slots[2].props.digit, '0');

  // Test 180 minutes -> "1" "8" "0" ":00"
  const ui180 = harness.render(180);
  assert.equal(ui180.slots.length, 3);
  assert.equal(ui180.slots[0].props.digit, '1');
  assert.equal(ui180.slots[1].props.digit, '8');
  assert.equal(ui180.slots[2].props.digit, '0');
});

test('the dial scales once and sizes its slot to match', () => {
  // The digits are painted with an explicit `fontSize`/`lineHeight`, so the
  // platform's own font scaling has to be off or the two multiply and the
  // fixed-height, `overflow: hidden` slot clips the glyph. This pins both
  // halves: every `Text` opts out of native scaling, and the slot grows with
  // the scale the component applies itself.
  const heights = new Map();
  for (const fontScale of [1, 1.5, 3]) {
    const harness = rollingDisplayHarness({ fontScale });
    const { texts, slots } = harness.render();

    for (const node of texts) {
      assert.equal(node.props.allowFontScaling, false,
        `every dial Text opts out of native scaling at fontScale ${fontScale}`);
    }

    // The root box, the slots and the rendered text all agree on one height.
    const rootHeight = harness.render().tree.props.style[0].height;
    for (const slot of slots) {
      assert.equal(slot.props.height, rootHeight,
        'each digit slot is exactly as tall as the display');
    }
    assert.ok(texts.length > 0, 'the dial renders its digits');
    // Only the clipped glyphs must match the slot. The 「分」 unit hangs off the
    // digits' lower right on purpose, so it is sized by its own small metric and
    // must NOT be forced to the digit line box.
    const digitTexts = texts.filter(node => node.props.children !== '分');
    assert.ok(digitTexts.length > 0, 'the dial renders digit glyphs');
    for (const text of digitTexts) {
      assert.equal(harness.mergedStyle(text).lineHeight, rootHeight,
        'every digit line box matches the slot it is clipped to');
    }
    const unit = texts.find(node => node.props.children === '分');
    assert.ok(unit, 'the unit still renders');
    assert.ok(harness.mergedStyle(unit).lineHeight < rootHeight,
      'the unit keeps its own small line box beside the digits');

    heights.set(fontScale, rootHeight);
  }

  // Scaling is applied by the component: the slot does grow with the system
  // scale rather than staying fixed.
  assert.ok(heights.get(1.5) > heights.get(1),
    'a larger system font scale produces a taller display');

  // ...but it is clamped, so the oversized dial cannot push the page apart.
  const clamped = rollingDisplayHarness({ fontScale: 3 }).render().tree.props.style[0].height;
  const atCeiling = rollingDisplayHarness({ fontScale: 1.15 }).render().tree.props.style[0].height;
  assert.equal(clamped, atCeiling, 'the display stops growing at its scale ceiling');
});

test('FocusVernierDial updates dynamically on drag scroll across tick boundaries', () => {
  const changed = [];
  const paper = {
    colors: tokens.YanjiPaperColors.light,
    radius: tokens.YanjiPaperRadius,
    layout: tokens.YanjiPaperLayout,
    typography: tokens.YanjiPaperTypography,
  };

  const VernierDial = loadTypeScript('src/components/FocusVernierDial.tsx', {
    react: {
      useState: initial => [typeof initial === 'function' ? initial() : initial, () => {}],
      useRef: initial => ({ current: initial }),
      useEffect: () => {},
      useCallback: fn => fn,
      useMemo: fn => fn(),
    },
    'react-native': {
      View: 'View',
      Text: 'Text',
      ScrollView: 'ScrollView',
      PixelRatio: { roundToNearestPixel: s => s },
      useWindowDimensions: () => ({ width: 358, height: 720, fontScale: 1 }),
    },
    'react-native-reanimated': { useReducedMotion: () => false },
    '../theme/ThemeProvider': { useYanjiTheme: () => ({ paper }) },
  }).FocusVernierDial;

  let currentMinutes = 45;
  const tree = VernierDial({
    valueMinutes: currentMinutes,
    onChangeMinutes: next => {
      changed.push(next);
      currentMinutes = next;
    },
    minMinutes: 5,
    maxMinutes: 180,
    stepMinutes: 5,
  });

  function findNode(node, pred) {
    if (!node || typeof node !== 'object') return null;
    if (pred(node)) return node;
    if (Array.isArray(node.props?.children)) {
      for (const c of node.props.children) {
        const found = findNode(c, pred);
        if (found) return found;
      }
    } else if (node.props?.children) {
      return findNode(node.props.children, pred);
    }
    return null;
  }

  const ruler = findNode(tree, n => n.props?.testID === 'focus-vernier-ruler');
  assert.ok(ruler, 'ruler is present');

  const tickWidth = ruler.props.snapToInterval;
  assert.ok(tickWidth > 0);

  // Before dragging, programmatic or idle scroll events do nothing
  ruler.props.onScroll({ nativeEvent: { contentOffset: { x: 9 * tickWidth } } });
  assert.deepEqual(changed, [], 'idle scroll without active user drag does not change value');

  // Start user drag
  ruler.props.onScrollBeginDrag();

  // Scroll to tick index 9 (50 minutes) -> should trigger onChangeMinutes(50)
  ruler.props.onScroll({ nativeEvent: { contentOffset: { x: 9 * tickWidth } } });
  assert.deepEqual(changed, [50], 'scrolling past tick 50 triggers dynamic update');

  // Scroll within same tick boundary -> no redundant onChange
  ruler.props.onScroll({ nativeEvent: { contentOffset: { x: 9.1 * tickWidth } } });
  assert.deepEqual(changed, [50], 'sub-tick scroll does not emit duplicate value');

  // Scroll to tick index 10 (55 minutes)
  ruler.props.onScroll({ nativeEvent: { contentOffset: { x: 10 * tickWidth } } });
  assert.deepEqual(changed, [50, 55], 'scrolling past tick 55 triggers dynamic update');

  // Scroll backwards to tick index 8 (45 minutes)
  ruler.props.onScroll({ nativeEvent: { contentOffset: { x: 8 * tickWidth } } });
  assert.deepEqual(changed, [50, 55, 45], 'scrolling backwards dynamically decreases value');
});

test('FocusVernierDial defers onSettleMinutes until user releases touch / dragging ends', () => {
  const dynamicChanges = [];
  const settledChanges = [];
  const paper = {
    colors: tokens.YanjiPaperColors.light,
    radius: tokens.YanjiPaperRadius,
    layout: tokens.YanjiPaperLayout,
    typography: tokens.YanjiPaperTypography,
  };

  const VernierDial = loadTypeScript('src/components/FocusVernierDial.tsx', {
    react: {
      useState: initial => [typeof initial === 'function' ? initial() : initial, () => {}],
      useRef: initial => ({ current: initial }),
      useEffect: () => {},
      useCallback: fn => fn,
      useMemo: fn => fn(),
    },
    'react-native': {
      View: 'View',
      Text: 'Text',
      ScrollView: 'ScrollView',
      PixelRatio: { roundToNearestPixel: s => s },
      useWindowDimensions: () => ({ width: 358, height: 720, fontScale: 1 }),
    },
    'react-native-reanimated': { useReducedMotion: () => false },
    '../theme/ThemeProvider': { useYanjiTheme: () => ({ paper }) },
  }).FocusVernierDial;

  let current = 45;
  const tree = VernierDial({
    valueMinutes: current,
    onChangeMinutes: next => {
      dynamicChanges.push(next);
      current = next;
    },
    onSettleMinutes: next => {
      settledChanges.push(next);
    },
    minMinutes: 5,
    maxMinutes: 180,
    stepMinutes: 5,
  });

  function findNode(node, pred) {
    if (!node || typeof node !== 'object') return null;
    if (pred(node)) return node;
    if (Array.isArray(node.props?.children)) {
      for (const c of node.props.children) {
        const found = findNode(c, pred);
        if (found) return found;
      }
    } else if (node.props?.children) {
      return findNode(node.props.children, pred);
    }
    return null;
  }

  const ruler = findNode(tree, n => n.props?.testID === 'focus-vernier-ruler');
  assert.ok(ruler);
  const tickWidth = ruler.props.snapToInterval;

  // Start drag
  ruler.props.onScrollBeginDrag();

  // Scroll through tick 9 (50m), 10 (55m), 11 (60m)
  ruler.props.onScroll({ nativeEvent: { contentOffset: { x: 9 * tickWidth } } });
  ruler.props.onScroll({ nativeEvent: { contentOffset: { x: 10 * tickWidth } } });
  ruler.props.onScroll({ nativeEvent: { contentOffset: { x: 11 * tickWidth } } });

  // Dynamic updates happened, but settled remains empty while user is dragging
  assert.deepEqual(dynamicChanges, [50, 55, 60]);
  assert.deepEqual(settledChanges, [], 'estimated end time does not distract user while dragging');

  // User releases touch with low velocity (or momentum completes)
  ruler.props.onScrollEndDrag({ nativeEvent: { contentOffset: { x: 11 * tickWidth }, velocity: { x: 0 } } });
  assert.deepEqual(settledChanges, [60], 'settled value updates only upon touch release');
});

test('FocusVernierDial preserves active drag across mid-gesture pauses and signals drag state', () => {
  const dynamicChanges = [];
  const settledChanges = [];
  const dragStates = [];
  const paper = {
    colors: tokens.YanjiPaperColors.light,
    radius: tokens.YanjiPaperRadius,
    layout: tokens.YanjiPaperLayout,
    typography: tokens.YanjiPaperTypography,
  };

  const VernierDial = loadTypeScript('src/components/FocusVernierDial.tsx', {
    react: {
      useState: initial => [typeof initial === 'function' ? initial() : initial, () => {}],
      useRef: initial => ({ current: initial }),
      useEffect: () => {},
      useCallback: fn => fn,
      useMemo: fn => fn(),
    },
    'react-native': {
      View: 'View',
      Text: 'Text',
      ScrollView: 'ScrollView',
      PixelRatio: { roundToNearestPixel: s => s },
      useWindowDimensions: () => ({ width: 358, height: 720, fontScale: 1 }),
    },
    'react-native-reanimated': { useReducedMotion: () => false },
    '../theme/ThemeProvider': { useYanjiTheme: () => ({ paper }) },
  }).FocusVernierDial;

  let current = 5;
  const tree = VernierDial({
    valueMinutes: current,
    onChangeMinutes: next => {
      dynamicChanges.push(next);
      current = next;
    },
    onSettleMinutes: next => {
      settledChanges.push(next);
    },
    onDragStateChange: isDragging => {
      dragStates.push(isDragging);
    },
    minMinutes: 5,
    maxMinutes: 180,
    stepMinutes: 5,
  });

  function findNode(node, pred) {
    if (!node || typeof node !== 'object') return null;
    if (pred(node)) return node;
    if (Array.isArray(node.props?.children)) {
      for (const c of node.props.children) {
        const found = findNode(c, pred);
        if (found) return found;
      }
    } else if (node.props?.children) {
      return findNode(node.props.children, pred);
    }
    return null;
  }

  const ruler = findNode(tree, n => n.props?.testID === 'focus-vernier-ruler');
  assert.ok(ruler);
  const tickWidth = ruler.props.snapToInterval;

  // Touch begins: drag state is true
  ruler.props.onScrollBeginDrag();
  assert.deepEqual(dragStates, [true], 'drag state is true when dragging begins');

  // Pull to 5m tick (index 0) or overscroll slightly
  ruler.props.onScroll({ nativeEvent: { contentOffset: { x: 0 } } });

  // Pause mid-drag without lifting finger — no onScrollEndDrag
  // Resume dragging to index 1 (10m), index 2 (15m), index 17 (90m)
  ruler.props.onScroll({ nativeEvent: { contentOffset: { x: 1 * tickWidth } } });
  ruler.props.onScroll({ nativeEvent: { contentOffset: { x: 2 * tickWidth } } });
  ruler.props.onScroll({ nativeEvent: { contentOffset: { x: 17 * tickWidth } } });

  assert.deepEqual(dynamicChanges, [10, 15, 90], 'resuming drag after a pause continues to emit updates');
  assert.deepEqual(settledChanges, [], 'remains unsettled while finger is still held down');

  // Release at 90m
  ruler.props.onScrollEndDrag({ nativeEvent: { contentOffset: { x: 17 * tickWidth }, velocity: { x: 0 } } });
  assert.deepEqual(settledChanges, [90], 'commits settled duration on release');
  assert.deepEqual(dragStates, [true, false], 'drag state resets to false on settle');
});
