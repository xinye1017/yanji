import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './framework/load-typescript.js';
import { YanjiLiquidGlass } from '../src/theme/liquidGlass.ts';

function find(node, predicate) {
  if (Array.isArray(node)) return node.flatMap(child => find(child, predicate));
  if (!node || typeof node !== 'object' || !node.props) return [];
  return [...(predicate(node) ? [node] : []), ...find(node.props.children, predicate)];
}

/** Run the actual component and its handlers with controlled layout and hooks. */
function fixture() {
  const slots = [], effects = [], calls = [];
  let cursor = 0, tree, handlers;
  const nav = { activeTab: 'today' };
  const preferences = { reduceMotion: false, highContrast: false };
  const theme = {
    isDark: false, spacing: { lg: 16 }, shadow: { floating: () => ({}) },
    paper: { colors: { brandInk: '#B23A22', ink: '#1C1E21', secondary: '#565960', elevated: '#FAF8F4' } },
  };
  const react = {
    useState(initial) {
      const i = cursor++;
      if (!(i in slots)) slots[i] = typeof initial === 'function' ? initial() : initial;
      return [slots[i], next => { slots[i] = typeof next === 'function' ? next(slots[i]) : next; }];
    },
    useRef(initial) {
      const i = cursor++;
      return slots[i] ??= { current: initial };
    },
    useMemo(fn, deps) {
      const i = cursor++;
      if (!slots[i] || deps.some((value, index) => value !== slots[i].deps[index])) {
        slots[i] = { value: fn(), deps };
      }
      return slots[i].value;
    },
    useEffect(fn, deps) {
      const i = cursor++;
      if (!slots[i] || deps.some((value, index) => value !== slots[i][index])) effects.push(fn);
      slots[i] = deps;
    },
  };
  const { BottomTabBar, calculateTargetSlotIndex } = loadTypeScript('src/navigation/BottomTabBar.tsx', {
    react,
    'react-native': {
      View: 'View', Text: 'Text', Pressable: 'Pressable', StyleSheet: { absoluteFill: {} },
      PanResponder: { create: config => { handlers = config; return { panHandlers: config }; } },
    },
    'react-native-blur-overlay': { default: 'BlurOverlay' },
    './NavigationShell': {
      TABS: [{ key: 'today', label: '今天' }, { key: 'focus', label: '专注' }, { key: 'review', label: '回顾' }],
      useNavigationState: () => nav,
      useNavigationActions: () => ({ selectTab: key => { calls.push(key); nav.activeTab = key; } }),
    },
    '../components/YanjiUI': { YanjiIcon: 'YanjiIcon' },
    '../theme/ThemeProvider': { useYanjiTheme: () => theme },
    '../theme/tokens': { YanjiRadius: { full: 9999 }, YanjiTouch: { comfortable: 48 } },
    '../theme/liquidGlass': { YanjiLiquidGlass },
    './useBottomTabLayout': { useBottomTabLayout: () => ({ height: 60, bottom: 16 }) },
    './useNavigationAccessibility': { useNavigationAccessibility: () => preferences },
  });
  return {
    calls, preferences, theme, calculateTargetSlotIndex,
    get handlers() { return handlers; },
    render(visible = true) {
      cursor = 0;
      tree = BottomTabBar({ visible });
      const row = find(tree, node => Boolean(node.props.onLayout))[0];
      row.props.ref.current = { measure: fn => fn(0, 0, 308, 60, 20, 700) };
      row.props.onLayout({ nativeEvent: { layout: { width: 308, height: 60 } } });
      for (const effect of effects.splice(0)) effect();
      return tree;
    },
    tab(key) { return find(tree, node => node.props.testID === `tab-${key}`)[0]; },
    label(key) { return find(this.tab(key), node => node.type === 'Text')[0]; },
    glass() { return find(tree, node => node.type === 'BlurOverlay')[0]; },
  };
}

const position = (x, y = 730) => ({ moveX: x, moveY: y });

test('slot geometry includes the first pixel and rejects invalid or off-row coordinates', () => {
  const { calculateTargetSlotIndex: slot } = fixture();
  for (const [x, expected] of [[0, 0], [99, 0], [100, 1], [199, 1], [200, 2], [299, 2], [-1, null], [300, null]]) {
    assert.equal(slot(x, 300, 100, 3), expected);
  }
  for (const args of [[50, 0, 100, 3], [50, 300, 0, 3], [50, 300, 100, 0]]) assert.equal(slot(...args), null);
});

test('ordinary taps remain Pressable actions and vertical movements do not capture them', () => {
  const f = fixture(); f.render();
  assert.equal(f.handlers.onStartShouldSetPanResponder?.() ?? false, false);
  for (const [dx, dy] of [[0, 0], [4, 0], [8, 20], [20, 20]]) {
    assert.equal(f.handlers.onMoveShouldSetPanResponderCapture(null, { dx, dy }), false);
  }
  assert.equal(f.handlers.onMoveShouldSetPanResponderCapture(null, { dx: 20, dy: 2 }), true);
  f.tab('focus').props.onPress();
  assert.deepEqual(f.calls, ['focus']);
});

test('horizontal dragging previews a destination without changing selection until release', () => {
  const f = fixture(); f.render();
  f.handlers.onPanResponderGrant(null, position(64));
  f.handlers.onPanResponderMove(null, position(170));
  f.render();
  assert.deepEqual(f.calls, []);
  assert.equal(f.label('focus').props.style.fontWeight, '600');
  assert.equal(f.tab('today').props.accessibilityState.selected, true);
  assert.equal(f.tab('focus').props.accessibilityState.selected, false);
  f.handlers.onPanResponderRelease(null, position(170));
  f.render();
  assert.deepEqual(f.calls, ['focus']);
  assert.equal(f.tab('focus').props.accessibilityState.selected, true);
  assert.equal(f.label('focus').props.style.fontWeight, '700');
});

test('leaving any side of the dock clears the preview and cancels navigation', () => {
  for (const [x, y] of [[170, 699], [170, 760], [23, 730], [324, 730]]) {
    const f = fixture(); f.render();
    f.handlers.onPanResponderGrant(null, position(64));
    f.handlers.onPanResponderMove(null, position(170));
    f.handlers.onPanResponderMove(null, position(x, y));
    f.render();
    assert.equal(f.label('focus').props.style.fontWeight, '500');
    f.handlers.onPanResponderRelease(null, position(x, y));
    assert.deepEqual(f.calls, []);
  }
});

test('a terminated gesture or a hidden dock cannot commit a preview', () => {
  const f = fixture(); f.render();
  f.handlers.onPanResponderGrant(null, position(170));
  f.handlers.onPanResponderTerminate();
  f.render();
  assert.equal(f.label('focus').props.style.fontWeight, '500');
  assert.deepEqual(f.calls, []);
  f.handlers.onPanResponderGrant(null, position(170));
  f.render(false);
  f.handlers.onPanResponderRelease(null, position(170));
  assert.deepEqual(f.calls, []);
});

test('high contrast uses an opaque semantic surface and reduced motion disables interactive light', () => {
  const f = fixture(); f.render();
  assert.equal(f.glass().props.glassVariant, 'regular');
  assert.equal(f.glass().props.interactive, true);
  assert.equal(f.label('review').props.style.color, f.theme.paper.colors.secondary);
  assert.equal(f.glass().props.glassTint, YanjiLiquidGlass.light.tint);
  f.theme.isDark = true; f.render();
  assert.equal(f.glass().props.glassVariant, 'regular');
  assert.equal(f.glass().props.glassTint, YanjiLiquidGlass.dark.tint);
  f.preferences.reduceMotion = true; f.render();
  assert.equal(f.glass().props.interactive, false);
  f.preferences.reduceMotion = false;
  f.preferences.highContrast = true; f.render();
  assert.equal(f.glass().props.glassTint, f.theme.paper.colors.elevated);
  assert.equal(f.glass().props.interactive, false);
  assert.equal(f.glass().props.saturation, 1);
  assert.equal(f.label('review').props.style.color, f.theme.paper.colors.ink);
  assert.equal(f.tab('focus').props.style({ pressed: true }).opacity, YanjiLiquidGlass.pressedOpacity);
});
