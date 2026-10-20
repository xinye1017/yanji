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
  tagIcon: 'inspiration',
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
  Array.isArray(n.props.style) && n.props.style[0]?.position === 'absolute' &&
  n.props.style[0]?.maxWidth !== undefined && n.props.style[0]?.paddingVertical !== undefined)[0];

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

test('every tag carries a fixed-width icon slot: bulb, question or clock', () => {
  const h = rowHarness();
  const tagIcon = tree => nodes(tree, n => n.type === 'YanjiIcon' && n.props.name !== 'more')[0];

  const bulb = h.render();
  assert.equal(tagIcon(bulb).props.name, 'inspiration', '灵感 uses the lightbulb');
  const slot = nodes(bulb, n => n.type === 'View' && n.props.style?.width === tokens.YanjiPaperLayout.momentTagIconSlot)[0];
  assert.ok(slot, 'the glyph lives in a fixed-width slot');
  assert.equal(slot.props.style.alignItems, 'center');
  assert.equal(slot.props.style.justifyContent, 'center');

  // The chip keeps its old compact padding, so the icon can never grow the row.
  const chip = nodes(bulb, n => n.type === 'View' && n.props.style?.backgroundColor === theme.paper.colors.positiveSubtle)[0];
  assert.equal(chip.props.style.paddingVertical, tokens.YanjiPaperLayout.taskTagPadding);
  // The body is text only: no wash, no border, no radius to read as a card.
  const body = nodes(bulb, n => n.type === 'View' && n.props.style?.paddingLeft === tokens.YanjiSpacing.sm &&
    n.props.style?.flexDirection === 'row')[0];
  assert.ok(body, 'the entry sits in one plain inline row');
  assert.equal(body.props.style.backgroundColor, undefined, 'no surface is painted behind the entry');
  assert.equal(body.props.style.borderWidth, undefined, 'no border');
  assert.equal(body.props.style.borderRadius, undefined, 'no radius');

  const doubt = h.render({ tagLabel: '疑点 · 待解决', tagIcon: 'doubt', tagTone: 'doubt' });
  assert.equal(tagIcon(doubt).props.name, 'doubt', '疑点 uses the circled question mark');

  const session = h.render({ tagLabel: '数据结构', tagIcon: 'clock3', tagTone: 'neutral', metaText: '30分钟' });
  assert.equal(tagIcon(session).props.name, 'clock3', 'every focus subject shares the same clock icon');
  assert.match(text(session), /数据结构/, 'the actual subject name still follows the icon');
});

test('the clock holds a fixed gutter and only the day\'s latest row is live', () => {
  const h = rowHarness();
  const clock = tree => nodes(tree, n => n.type === 'Text' && text(n) === '19:43')[0];
  const clockColumn = tree => nodes(tree, n => n.type === 'View' &&
    n.props.children?.type === 'Text' && text(n.props.children) === '19:43')[0];

  const positive = h.render();
  assert.equal(clockColumn(positive).props.style.width, 40, 'every row reserves the same HH:MM gutter');
  assert.equal(clockColumn(positive).props.style.alignItems, 'flex-start');
  assert.equal(clock(positive).props.style[0], tokens.YanjiPaperTypography.captionMeta,
    'the clock is meta type, not body copy');
  assert.equal(clock(positive).props.style[1].color, theme.paper.colors.ink,
    'the latest row is the live one: ink, not secondary');
  assert.equal(clock(positive).props.style[1].fontWeight, '700');
  assert.equal(clock(h.render({ isFirst: false })).props.style[1].color, theme.paper.colors.secondary);
  assert.equal(clock(h.render({ isFirst: false })).props.style[1].fontWeight, undefined,
    'an older row keeps the base caption weight');

  assert.ok(nodes(positive, n => n.type === 'View' && n.props.style?.backgroundColor === theme.paper.colors.positiveSubtle)[0],
    '灵感 uses the green chip wash');
  const doubt = h.render({ tagLabel: '疑点 · 待解决', tagTone: 'doubt' });
  assert.ok(nodes(doubt, n => n.type === 'View' && n.props.style?.backgroundColor === theme.paper.colors.doubtSubtle)[0],
    'a waiting doubt carries the amber doubt wash, not the action accent');

  const session = h.render({ tagLabel: '数据结构', tagTone: 'neutral', metaText: '30分钟' });
  assert.ok(nodes(session, n => n.type === 'View' && n.props.style?.backgroundColor === theme.paper.colors.subtle)[0],
    'the subject tag itself stays neutral');
  assert.match(text(session), /30分钟/, 'the duration meta rides on the same line');
});

test('the page-edge rule runs down the clock column as one unbroken line', () => {
  const { TIMELINE_TIME_COLUMN } = loadTypeScript('src/components/TimelineCompactRow.tsx', {
    react: { useState: value => [value, () => {}], useRef: value => ({ current: value }) },
    'react-native': { Pressable: 'Pressable', Text: 'Text', View: 'View' },
    '../theme/ThemeProvider': { useYanjiTheme: () => theme },
    './YanjiUI': { YanjiIcon: 'YanjiIcon' },
  });
  const h = rowHarness();
  // The spine is the row's only absolutely positioned rule, sitting on the
  // gutter's right edge — no node dot, no per-segment stub.
  const spine = tree => nodes(tree, n => n.type === 'View' && n.props.style?.position === 'absolute');

  const only = h.render({ isFirst: false, isLast: false });
  assert.equal(spine(only).length, 1, 'one rule, and nothing else is taken out of flow');
  assert.equal(spine(only)[0].props.style.left, TIMELINE_TIME_COLUMN,
    'the rule rides the right edge of the fixed clock gutter');
  assert.equal(spine(only)[0].props.style.width, tokens.YanjiPaperLayout.hairlineWidth);
  assert.equal(spine(only)[0].props.style.backgroundColor, theme.paper.colors.hairline);
  assert.equal(spine(only)[0].props.style.top, 0, 'a middle row touches the row above');
  assert.equal(spine(only)[0].props.style.bottom, 0, '…and the row below');

  // First and last rows stop at the content edge rather than the row edge.
  assert.equal(spine(h.render({ isFirst: true, isLast: false }))[0].props.style.top, tokens.YanjiSpacing.xs);
  const last = h.render({ isFirst: false, isLast: true });
  assert.equal(spine(last)[0].props.style.bottom, tokens.YanjiSpacing.xs);
  assert.equal(spine(last)[0].props.style.top, 0);

  // A single event is both ends at once and still draws its own span.
  const single = spine(h.render({ isFirst: true, isLast: true }));
  assert.equal(single.length, 1);
  assert.equal(single[0].props.style.top, tokens.YanjiSpacing.xs);
  assert.equal(single[0].props.style.bottom, tokens.YanjiSpacing.xs);

  // The old node dot is gone: the tone survives only on the tag chip.
  assert.equal(nodes(only, n => n.props.style?.backgroundColor === theme.paper.colors.positive).length, 0,
    'no subject/category dot is drawn on the spine any more');
  assert.equal(nodes(only, n => n.props.style?.borderWidth !== undefined).length, 0,
    'the event body is no longer a bordered card');
});

// ---------------------------------------------------------------------------
// The anchored menu
// ---------------------------------------------------------------------------

test('the menu opens below the button, flips above near the bottom, and clamps to the edges', () => {
  const p = popoverHarness();
  const spacing = tokens.YanjiSpacing;
  const layout = tokens.YanjiPaperLayout;
  // Before onLayout the panel is positioned from the estimate; it then
  // shrink-wraps its widest row and repositions with the measured width.
  const estimatedWidth = layout.momentMenuWidth;

  const view = menuView(p.render());
  assert.equal(view.props.style[0].top, 300 - 24 + 44 + spacing.xs);
  assert.equal(view.props.style[0].left, 300 + 44 - estimatedWidth);
  assert.equal('minWidth' in view.props.style[0], false, 'the panel is not forced to a fixed width');

  const bottom = menuView(p.render({ anchor: { x: 300, y: 780, width: 44, height: 44 } }));
  const height = spacing.xs * 2 + 3 * layout.rowMinHeight + layout.hairlineWidth;
  assert.equal(bottom.props.style[0].top, 780 - 24 - height - spacing.xs);

  assert.equal(menuView(p.render({ anchor: { x: 0, y: 300, width: 44, height: 44 } })).props.style[0].left, spacing.sm);
  assert.equal(menuView(p.render({ anchor: { x: 380, y: 300, width: 44, height: 44 } })).props.style[0].left,
    390 - estimatedWidth - spacing.sm);
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

  const row = deepNodes(tree, n => n.type === 'Pressable' && n.props.accessibilityLabel === '编辑记录')[0];
  const rowStyle = row.props.style({ pressed: false });
  assert.equal(rowStyle.justifyContent, 'center', 'icon and label are centred as one group');
  const label = deepNodes(row, n => n.type === 'Text')[0];
  assert.equal('flex' in label.props.style[1], false, 'the label does not stretch the panel');
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
    const dark = tokens.YanjiPaperColors.dark;
    assert.ok(nodes(row, n => n.props.style?.backgroundColor === dark.hairline).length,
      'the page-edge rule and the row separator follow the dark hairline');
    assert.ok(nodes(row, n => n.props.style?.backgroundColor === dark.positiveSubtle).length,
      'the tag wash follows the dark palette');
    assert.equal(nodes(row, n => n.props.style?.backgroundColor === dark.elevated).length, 0,
      'the row paints no card surface in either theme');
    const menu = menuView(popoverHarness().render());
    assert.equal(menu.props.style[0].backgroundColor, tokens.YanjiPaperColors.dark.surface);
  } finally {
    theme.paper.colors = light;
  }
});
