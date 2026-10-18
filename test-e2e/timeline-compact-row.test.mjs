import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './framework/load-typescript.js';

/**
 * The compact timeline row and its anchored menu — shared by Today's
 * 「记录此刻」 and the Review timeline.
 *
 * These are the contracts both screens depend on: one line per event
 * (clock · tag · preview · meta · 「···」), a dot/chip colour that comes from the
 * theme, and a floating menu that never changes the row's height while closing
 * before it acts.
 */

const tokens = loadTypeScript('src/theme/tokens.ts');
const theme = {
  isDark: false,
  paper: {
    colors: tokens.YanjiPaperColors.light,
    typography: tokens.YanjiPaperTypography,
    layout: tokens.YanjiPaperLayout,
    radius: tokens.YanjiPaperRadius,
    motion: tokens.YanjiPaperMotion,
  },
  shadow: tokens.YanjiShadow,
};

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

/** Also descends through component functions, so menu rows are reachable. */
const deepNodes = (tree, predicate) => {
  const result = [];
  const walk = node => {
    if (Array.isArray(node)) return node.forEach(walk);
    if (!node || typeof node !== 'object' || !('type' in node)) return;
    if (predicate(node)) result.push(node);
    if (typeof node.type === 'function') {
      walk(node.type(node.props));
      return;
    }
    walk(node.props.children);
  };
  walk(tree);
  return result;
};

const rowProps = () => ({
  timeLabel: '19:43',
  tagLabel: '灵感',
  tagTone: 'positive',
  content: '第一行\n第二行 ✨',
  isFirst: true,
  isLast: true,
  disabled: false,
  onPressContent() {},
  onMore() {},
});

function rowHarness(measure) {
  const states = [];
  const refs = [];
  let stateIndex = 0;
  let refIndex = 0;
  const { TimelineCompactRow } = loadTypeScript('src/components/TimelineCompactRow.tsx', {
    react: {
      useState: initial => {
        const i = stateIndex++;
        if (!(i in states)) states[i] = initial;
        return [states[i], next => { states[i] = next; }];
      },
      useRef: value => {
        const i = refIndex++;
        if (!(i in refs)) refs[i] = measure ? { current: measure } : { current: value };
        return refs[i];
      },
    },
    'react-native': { Pressable: 'Pressable', Text: 'Text', View: 'View' },
    '../theme/ThemeProvider': { useYanjiTheme: () => theme },
    './YanjiUI': { YanjiIcon: 'YanjiIcon' },
  });
  return {
    render: overrides => {
      stateIndex = 0;
      refIndex = 0;
      return TimelineCompactRow({ ...rowProps(), ...overrides });
    },
  };
}

function popoverHarness() {
  const backHandlers = [];
  const { TimelineActionPopover } = loadTypeScript('src/components/TimelineActionPopover.tsx', {
    react: { Fragment: 'Fragment', useEffect: fn => fn(), useState: value => [value, () => {}] },
    'react-native': {
      Pressable: 'Pressable', Text: 'Text', View: 'View',
      StyleSheet: { absoluteFill: { position: 'absolute', left: 0, right: 0, top: 0, bottom: 0 } },
      BackHandler: {
        addEventListener: (event, handler) => {
          backHandlers.push(handler);
          return { remove() {} };
        },
      },
    },
    '../theme/ThemeProvider': { useYanjiTheme: () => theme },
    './YanjiUI': { YanjiIcon: 'YanjiIcon', YanjiHairline: 'YanjiHairline' },
  });
  const calls = [];
  const actions = [
    { key: 'favorite', icon: 'star', label: '收藏记录', onPress: () => calls.push('favorite') },
    { key: 'edit', icon: 'compose', label: '编辑记录', onPress: () => calls.push('edit') },
    { key: 'delete', icon: 'delete', label: '删除记录', danger: true, onPress: () => calls.push('delete') },
  ];
  return {
    backHandlers,
    calls,
    actions,
    render: overrides => TimelineActionPopover({
      anchor: { x: 300, y: 300, width: 44, height: 44 },
      topInset: 24,
      containerWidth: 390,
      containerHeight: 820,
      bottomInset: 0,
      disabled: false,
      actions,
      onClose() {},
      ...overrides,
    }),
  };
}

const menuView = tree => nodes(tree, n => n.type === 'View' &&
  Array.isArray(n.props.style) && n.props.style[0]?.minWidth === tokens.YanjiPaperLayout.momentMenuWidth)[0];

// ---------------------------------------------------------------------------
// The compact row
// ---------------------------------------------------------------------------

test('an event is one line: clock, tag, single-line preview and 「···」', () => {
  const calls = [];
  const h = rowHarness({ measureInWindow: callback => callback(300, 200, 44, 44) });
  const tree = h.render({ onMore: anchor => calls.push(anchor) });

  const preview = nodes(tree, n => n.type === 'Text' && n.props.numberOfLines === 1 && n.props.ellipsizeMode === 'tail')[0];
  assert.ok(preview, 'the body is explicitly a one-line preview');
  assert.equal(text(preview), '第一行 第二行 ✨', 'line breaks collapse for display; the stored text is untouched');
  assert.match(text(tree), /19:43/);
  assert.match(text(tree), /灵感/);

  const presses = nodes(tree, n => n.type === 'Pressable');
  assert.equal(presses.length, 2, 'only the body and the 「···」 are targets');
  presses[1].props.onPress();
  assert.equal(calls.length, 1);
  assert.equal(calls[0].x, 300);
  assert.equal(calls[0].y, 200);
  assert.equal(calls[0].width, 44);
  assert.equal(calls[0].height, 44, 'the menu is anchored to the button that was actually pressed');
});

test('the clock sits on the page gutter, and the dot/chip follow the tag tone', () => {
  const h = rowHarness();
  const positive = h.render();
  const clockColumn = nodes(positive, n => n.type === 'View' &&
    n.props.children?.type === 'Text' && text(n.props.children) === '19:43')[0];
  assert.ok(clockColumn);
  assert.equal(clockColumn.props.style.alignItems, 'flex-start');
  assert.equal(clockColumn.props.style.marginRight, tokens.YanjiSpacing.xs);
  assert.ok(nodes(positive, n => n.type === 'View' && n.props.style?.backgroundColor === theme.paper.colors.positiveSubtle)[0],
    '灵感 uses the green chip wash');
  assert.ok(nodes(positive, n => n.type === 'View' && n.props.style?.backgroundColor === theme.paper.colors.positive)[0],
    '灵感 uses the green dot');

  const doubt = h.render({ tagLabel: '疑点 · 待解决', tagTone: 'brand' });
  assert.ok(nodes(doubt, n => n.type === 'View' && n.props.style?.backgroundColor === theme.paper.colors.brandSubtle)[0]);
  assert.ok(nodes(doubt, n => n.type === 'View' && n.props.style?.backgroundColor === theme.paper.colors.brand)[0]);

  const session = h.render({ tagLabel: '数据结构', tagTone: 'neutral', dotColor: '#2D7A5E', metaText: '30分钟' });
  assert.ok(nodes(session, n => n.type === 'View' && n.props.style?.backgroundColor === '#2D7A5E')[0],
    'a session keeps its subject-coloured dot');
  assert.ok(nodes(session, n => n.type === 'View' && n.props.style?.backgroundColor === theme.paper.colors.subtle)[0],
    'the subject tag itself stays neutral');
  assert.match(text(session), /30分钟/, 'the duration meta rides on the same line');
});

test('the spine follows the measured row, crosses the gap, and stops at both ends', () => {
  const h = rowHarness();
  const lines = tree => nodes(tree, n => n.type === 'View' && n.props.style?.position === 'absolute' &&
    n.props.style?.width === tokens.YanjiPaperLayout.hairlineWidth);

  let tree = h.render({ isFirst: true, isLast: false });
  assert.equal(lines(tree).length, 1);
  assert.equal(lines(tree)[0].props.style.bottom, -tokens.YanjiPaperLayout.momentItemGap);

  const measured = nodes(tree, n => typeof n.props.onLayout === 'function')[0];
  measured.props.onLayout({ nativeEvent: { layout: { height: 56 } } });
  tree = h.render({ isFirst: false, isLast: true });
  assert.equal(lines(tree).length, 1);
  assert.equal(lines(tree)[0].props.style.bottom, undefined);
  const dot = nodes(tree, n => n.props.style?.backgroundColor === theme.paper.colors.positive)[0];
  assert.equal(dot.props.style.top + tokens.YanjiPaperLayout.timelineNodeSize / 2, 28);

  assert.equal(lines(h.render({ isFirst: true, isLast: true })).length, 0);
});

// ---------------------------------------------------------------------------
// The anchored menu
// ---------------------------------------------------------------------------

test('the menu opens below the button, flips above near the bottom, and clamps to the edges', () => {
  const p = popoverHarness();
  const spacing = tokens.YanjiSpacing;
  const layout = tokens.YanjiPaperLayout;

  const view = menuView(p.render());
  assert.equal(view.props.style[0].top, 300 - 24 + 44 + spacing.xs);
  assert.equal(view.props.style[0].left, 300 + 44 - layout.momentMenuWidth);

  const bottom = menuView(p.render({ anchor: { x: 300, y: 780, width: 44, height: 44 } }));
  const height = spacing.xs * 2 + 3 * layout.rowMinHeight + layout.hairlineWidth;
  assert.equal(bottom.props.style[0].top, 780 - 24 - height - spacing.xs);

  assert.equal(menuView(p.render({ anchor: { x: 0, y: 300, width: 44, height: 44 } })).props.style[0].left, spacing.sm);
  assert.equal(menuView(p.render({ anchor: { x: 380, y: 300, width: 44, height: 44 } })).props.style[0].left,
    390 - layout.momentMenuWidth - spacing.sm);
});

test('every action closes first and then runs, with the destructive one ruled off', () => {
  const p = popoverHarness();
  const calls = [];
  const tree = p.render({ onClose: () => calls.push('close') });
  const press = label => deepNodes(tree, n => n.type === 'Pressable' && n.props.accessibilityLabel === label)[0].props.onPress();
  press('收藏记录');
  press('编辑记录');
  press('删除记录');
  assert.deepEqual(calls, ['close', 'close', 'close']);
  assert.deepEqual(p.calls, ['favorite', 'edit', 'delete']);

  const rules = deepNodes(tree, n => n.type === 'YanjiHairline');
  assert.equal(rules.length, 1, 'only the destructive action is separated by a rule');
});

test('the backdrop and Android back both close the menu; disabled rows are inert', () => {
  const p = popoverHarness();
  const calls = [];
  const tree = p.render({ onClose: () => calls.push('close') });
  const backdrop = nodes(tree, n => n.type === 'Pressable' && n.props.accessibilityLabel === '关闭记录操作菜单')[0];
  backdrop.props.onPress();
  assert.deepEqual(calls, ['close']);
  assert.equal(p.backHandlers.length, 1);
  assert.equal(p.backHandlers[0](), true);
  assert.deepEqual(calls, ['close', 'close']);

  const disabled = popoverHarness();
  const disabledTree = disabled.render({ disabled: true, onClose: () => {} });
  const edit = deepNodes(disabledTree, n => n.type === 'Pressable' && n.props.accessibilityLabel === '编辑记录')[0];
  assert.equal(edit.props.disabled, true);
  edit.props.onPress();
  assert.deepEqual(disabled.calls, [], 'a disabled menu cannot run an action');
});

test('the row and the menu read their surfaces from the active theme', () => {
  const light = theme.paper.colors;
  try {
    theme.paper.colors = tokens.YanjiPaperColors.dark;
    const row = rowHarness().render();
    assert.ok(nodes(row, n => n.props.style?.backgroundColor === tokens.YanjiPaperColors.dark.elevated).length);
    const menu = menuView(popoverHarness().render());
    assert.equal(menu.props.style[0].backgroundColor, tokens.YanjiPaperColors.dark.surface);
  } finally {
    theme.paper.colors = light;
  }
});
