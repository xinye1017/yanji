import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './framework/load-typescript.js';

const tokens = loadTypeScript('src/theme/tokens.ts');
const theme = { paper: {
  colors: tokens.YanjiPaperColors.light, typography: tokens.YanjiPaperTypography,
  layout: tokens.YanjiPaperLayout, radius: tokens.YanjiPaperRadius, motion: tokens.YanjiPaperMotion,
} };
const nodes = (tree, predicate) => {
  const result = [];
  const walk = node => {
    if (Array.isArray(node)) node.forEach(walk);
    else if (node && typeof node === 'object' && 'type' in node) {
      if (predicate(node)) result.push(node);
      walk(node.props.children);
    }
  };
  walk(tree);
  return result;
};
const text = node => Array.isArray(node) ? node.map(text).join('')
  : node && typeof node === 'object' ? text(node.props?.children)
    : typeof node === 'string' ? node : '';
const mocks = {
  react: { useEffect: fn => fn(), useRef: value => ({ current: value }), useState: value => [value, () => {}] },
  'react-native': { Modal: 'Modal', Pressable: 'Pressable', Text: 'Text', View: 'View', ScrollView: 'ScrollView' },
  'react-native-reanimated': {
    default: { View: 'Animated.View' }, useReducedMotion: () => true,
    useSharedValue: value => ({ value }), useAnimatedStyle: fn => fn(),
  },
  'react-native-safe-area-context': { useSafeAreaInsets: () => ({ bottom: 24, top: 24 }) },
  '../theme/ThemeProvider': { useYanjiTheme: () => theme },
  './YanjiUI': { YanjiIcon: 'YanjiIcon', YanjiHairline: 'YanjiHairline' },
};
const { MomentTimelineItem } = loadTypeScript('src/components/MomentTimelineItem.tsx', mocks);
const { MomentActionSheet } = loadTypeScript('src/components/MomentActionSheet.tsx', mocks);
const props = () => ({
  timeLabel: '17:56', categoryLabel: '灵感', content: '这是一个测试 ✨\nReact Native 2026',
  isFavorite: false, isFirst: true, isLast: true, disabled: false,
  onPressContent() {}, onToggleFavorite() {}, onMore() {},
});

function animatedItemHarness() {
  const refs = [], states = [], shared = [], effects = [], timing = [];
  let refIndex = 0, stateIndex = 0, sharedIndex = 0, effectIndex = 0;
  let reduced = false;
  const { MomentTimelineItem: Item } = loadTypeScript('src/components/MomentTimelineItem.tsx', {
    ...mocks,
    react: {
      useRef: initial => refs[refIndex++] ??= { current: initial },
      useState: initial => {
        const i = stateIndex++;
        if (!(i in states)) states[i] = initial;
        return [states[i], next => { states[i] = next; }];
      },
      useEffect: (fn, deps) => {
        const i = effectIndex++;
        if (!effects[i] || deps.some((dep, index) => !Object.is(dep, effects[i][index]))) fn();
        effects[i] = deps;
      },
    },
    'react-native-reanimated': {
      ...mocks['react-native-reanimated'],
      useReducedMotion: () => reduced,
      useSharedValue: value => shared[sharedIndex++] ??= { value },
      withTiming: (value, options) => { timing.push([value, options.duration]); return value; },
      withSequence: (...steps) => steps.at(-1),
    },
  });
  return {
    timing, reduceMotion: value => { reduced = value; },
    render: input => {
      refIndex = stateIndex = sharedIndex = effectIndex = 0;
      return Item({ ...props(), ...input });
    },
  };
}

test('timeline preview preserves exact content and exposes only favorite / more plus full-content entry', () => {
  for (const colors of Object.values(tokens.YanjiPaperColors)) {
    theme.paper.colors = colors;
    const calls = [];
    const input = { ...props(), onPressContent: () => calls.push('content'),
      onToggleFavorite: () => calls.push('favorite'), onMore: () => calls.push('more') };
    const tree = MomentTimelineItem(input);
    const body = nodes(tree, n => n.type === 'Text' && n.props.numberOfLines === 4)[0];
    assert.equal(text(body), input.content, 'no decorative quotes or text rewriting');
    assert.equal(body.props.ellipsizeMode, 'tail');
    const buttons = nodes(tree, n => n.type === 'Pressable');
    assert.equal(buttons.length, 3);
    buttons.forEach(button => button.props.onPress());
    assert.deepEqual(calls, ['favorite', 'more', 'content']);
    for (const button of buttons.slice(0, 2)) {
      const style = button.props.style({ pressed: false });
      assert.ok(style.width >= 32 && style.height >= 32);
    }
    assert.doesNotMatch(text(tree), /记录此刻|已收藏/);
    assert.ok(nodes(tree, n => n.type === 'View' && n.props.style?.backgroundColor === colors.elevated).length);
  }
});

test('favorite is controlled by persisted props; missing category creates no fake label', () => {
  const tree = MomentTimelineItem({ ...props(), categoryLabel: null, isFavorite: true, disabled: true });
  const star = nodes(tree, n => n.type === 'Pressable' && n.props.accessibilityState?.selected)[0];
  assert.equal(star.props.disabled, true);
  assert.equal(nodes(star, n => n.type === 'YanjiIcon')[0].props.filled, true);
  assert.equal(nodes(star, n => n.type === 'YanjiIcon')[0].props.color, theme.paper.colors.brandInk);
  assert.doesNotMatch(text(tree), /灵感|疑点/);
});

test('resolved doubt remains actionable independently of content and preserves its state label', () => {
  let toggles = 0;
  const tree = MomentTimelineItem({ ...props(), categoryLabel: '疑点 · 已解决', isResolved: true,
    onToggleResolved: () => toggles++ });
  const toggle = nodes(tree, n => n.type === 'Pressable' && n.props.accessibilityRole === 'checkbox')[0];
  assert.equal(toggle.props.accessibilityState.checked, true);
  toggle.props.onPress();
  assert.equal(toggles, 1);
  assert.match(text(toggle), /疑点 · 已解决/);
});

test('compact action sheet closes before editing/deleting and handles backdrop / Android back', () => {
  const calls = [];
  const tree = MomentActionSheet({ visible: true, disabled: false,
    onClose: () => calls.push('close'), onEdit: () => calls.push('edit'), onDelete: () => calls.push('delete') });
  tree.props.onRequestClose();
  const buttons = nodes(tree, n => n.type === 'Pressable');
  buttons.find(n => n.props.accessibilityLabel === '关闭记录操作').props.onPress();
  buttons.find(n => n.props.accessibilityLabel === '编辑记录').props.onPress();
  buttons.find(n => n.props.accessibilityLabel === '删除记录').props.onPress();
  assert.deepEqual(calls, ['close', 'close', 'close', 'edit', 'close', 'delete']);
  assert.equal(tree.props.animationType, 'none', 'reduce-motion preference disables modal animation');
  const sheet = nodes(tree, n => n.props.accessibilityViewIsModal)[0];
  assert.ok(sheet.props.style.paddingBottom >= 24, 'actions clear the Android navigation area');
});

test('favorite animates only after the stored state changes, in both directions, honoring reduced motion', () => {
  const item = animatedItemHarness();
  let tree = item.render({ isFavorite: false });
  nodes(tree, n => n.type === 'Pressable')[0].props.onPress();
  item.render({ isFavorite: false });
  assert.equal(item.timing.length, 0, 'a click without persistence cannot fake a selected state');
  item.render({ isFavorite: true });
  item.render({ isFavorite: false });
  assert.deepEqual(item.timing, [[1.1, 80], [1, 80], [0.9, 80], [1, 80]]);
  item.reduceMotion(true);
  tree = item.render({ isFavorite: true });
  assert.equal(item.timing.length, 4, 'reduced motion skips the entire scale sequence');
  assert.equal(nodes(tree, n => n.type === 'Pressable')[0].props.accessibilityState.selected, true);
});

test('timeline connects through the inter-item gap, follows a wrapped header and stops at the last node', () => {
  const item = animatedItemHarness();
  let tree = item.render({ isFirst: true, isLast: false });
  const lines = root => nodes(root, n => n.type === 'View' && n.props.style?.position === 'absolute' &&
    n.props.style?.width === theme.paper.layout.hairlineWidth);
  assert.equal(lines(tree).length, 1, 'first item has only the outgoing rail');
  assert.equal(lines(tree)[0].props.style.bottom, -tokens.YanjiPaperLayout.momentItemGap);
  nodes(tree, n => !!n.props.onLayout)[0].props.onLayout({ nativeEvent: { layout: { height: 96 } } });
  tree = item.render({ isFirst: false, isLast: false });
  assert.equal(lines(tree).length, 2);
  const dot = nodes(tree, n => n.props.style?.backgroundColor === theme.paper.colors.brand)[0];
  assert.equal(dot.props.style.top + tokens.YanjiPaperLayout.timelineNodeSize / 2, tokens.YanjiSpacing.xs + 96 / 2);
  tree = item.render({ isFirst: false, isLast: true });
  assert.equal(lines(tree).length, 1, 'last item has only the incoming rail');
  assert.equal(lines(tree)[0].props.style.bottom, undefined);
  assert.equal(tree.props.style.paddingBottom, 0);
  assert.equal(lines(item.render({ isFirst: true, isLast: true })).length, 0);
});
