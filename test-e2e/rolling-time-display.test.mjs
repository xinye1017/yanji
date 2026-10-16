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
      elements(node.props?.children, predicate, out);
    }
    return out;
  }

  function render(val = valueMinutes) {
    const tree = component({ valueMinutes: val });
    const texts = elements(tree, n => n.type === 'Text');
    const slots = elements(tree, n => typeof n.type === 'function' && n.props?.digit !== undefined);
    return { tree, texts, slots, textValues: texts.map(t => t.props?.children) };
  }

  return { render };
}

test('RollingTimeDisplay renders correct digits and fixed suffix', () => {
  const harness = rollingDisplayHarness();

  // Test 5 minutes -> "0" "5" ":00"
  const ui5 = harness.render(5);
  assert.equal(ui5.slots.length, 2, '5 minutes has tens and units slots');
  assert.equal(ui5.slots[0].props.digit, '0');
  assert.equal(ui5.slots[1].props.digit, '5');
  assert.ok(ui5.textValues.includes(':00'), 'includes fixed :00 suffix');

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
