import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './framework/load-typescript.js';

/**
 * The timetable's drag affordances (#17).
 *
 * A block has always been movable by its body and resizable by its top/bottom
 * edges, but nothing on screen said so. These tests pin the visible half: the
 * grip marks the body, the two caps mark the edges, the edge zones stay
 * proportional to short blocks (so they never swallow the body), and the whole
 * decoration is inert to touch. The drag math itself lives in
 * `timetable-view-logic.test.mjs`.
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

const HOUR_HEIGHT = 80;

/** Descends through function components (TimelineBlock) so their views are reachable. */
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

const block = (startMinute, endMinute, overrides = {}) => ({
  id: `b-${startMinute}`,
  startMinute,
  endMinute,
  subjectIds: ['math'],
  subjectLabel: '数学',
  title: '核心知识复习',
  ...overrides,
});

function render(blocks) {
  const { ScheduleTimeline } = loadTypeScript('src/components/ScheduleTimeline.tsx', {
    react: { useMemo: fn => fn(), useRef: value => ({ current: value }), useState: value => [value, () => {}] },
    'react-native': {
      PanResponder: { create: config => ({ panHandlers: config }) },
      Pressable: 'Pressable',
      Text: 'Text',
      View: 'View',
    },
    '../theme/ThemeProvider': { useYanjiTheme: () => theme },
  });
  return ScheduleTimeline({
    blocks,
    disabled: false,
    onAdd() {},
    onEdit() {},
    onChange() {},
  });
}

/** The block container: the only absolute view that carries a top offset and a height. */
const blockView = tree => deepNodes(tree, node => node.type === 'View'
  && node.props.style?.position === 'absolute'
  && typeof node.props.style?.height === 'number'
  && node.props.style?.top !== undefined
  && typeof node.props.style?.zIndex === 'number')[0];

/** Scoped to the block: the 24-hour ruler rows also span left:0/right:0. */
const edgeZones = tree => deepNodes(blockView(tree), node => node.type === 'View'
  && node.props.style?.position === 'absolute'
  && node.props.style?.left === 0 && node.props.style?.right === 0
  && typeof node.props.style?.height === 'number'
  && (node.props.style?.top === 0 || node.props.style?.bottom === 0)
  && node.props.style?.zIndex === undefined);

const overlay = tree => deepNodes(tree, node => node.type === 'View'
  && node.props.pointerEvents === 'none')[0];

const bars = tree => deepNodes(tree, node => node.type === 'View'
  && node.props.style?.width === 12 && node.props.style?.height === 2);

const caps = tree => deepNodes(tree, node => node.type === 'View'
  && node.props.style?.width === 20 && node.props.style?.height === 3);

test('a 60-minute block shows a grip and both edge caps, with 16dp edge zones', () => {
  const tree = render([block(480, 540)]);

  const container = blockView(tree);
  assert.equal(container.props.style.height, HOUR_HEIGHT, 'a one-hour block is one hour tall');
  assert.equal(container.props.style.top, (480 / 60) * HOUR_HEIGHT);

  assert.equal(bars(tree).length, 3, 'the grip is three stacked bars');
  const gripBar = bars(tree)[0];
  assert.equal(gripBar.props.style.borderRadius, theme.paper.radius.pill);
  assert.equal(gripBar.props.style.backgroundColor, theme.paper.colors.brandInk);

  assert.equal(caps(tree).length, 2, 'one cap marks the top edge, one the bottom');
  for (const cap of caps(tree)) {
    assert.equal(cap.props.style.borderRadius, theme.paper.radius.pill);
  }

  const zones = edgeZones(tree);
  assert.equal(zones.length, 2, 'one resize zone per edge');
  assert.equal(zones[0].props.style.height, 16, 'the top zone is 16dp on a tall block');
  assert.equal(zones[1].props.style.height, 16, 'the bottom zone is 16dp on a tall block');
  assert.ok(zones[0].props.onStartShouldSetPanResponder, 'the zones own the resize gesture');
});

test('the decoration is inert: it never steals the drag it advertises', () => {
  const tree = render([block(480, 540)]);
  const layer = overlay(tree);
  assert.ok(layer, 'the grip and caps live in their own layer');
  assert.equal(layer.props.pointerEvents, 'none');
  assert.equal(layer.props.accessible, false);
});

test('short blocks drop the marks before the marks would crowd the body', () => {
  // 30 minutes = 40dp: both marks still fit, and the edge zones take a third.
  const half = render([block(480, 510)]);
  const halfZones = edgeZones(half);
  assert.equal(halfZones.length, 2);
  assert.equal(halfZones[0].props.style.height, 40 / 3, 'the edge zone shrinks with the block');
  assert.equal(bars(half).length, 3, 'a 40dp block keeps its grip');
  assert.equal(caps(half).length, 2, 'a 40dp block keeps its edge caps');

  // 15 minutes = 20dp: neither mark fits, and the body keeps a third of the
  // height so a short block can still be dragged.
  const quarter = render([block(480, 495)]);
  const quarterZones = edgeZones(quarter);
  assert.equal(quarterZones.length, 2);
  assert.ok(Math.abs(quarterZones[0].props.style.height - 20 / 3) < 1e-9);
  assert.equal(bars(quarter).length, 0, 'a 20dp block shows no grip');
  assert.equal(caps(quarter).length, 0, 'a 20dp block shows no caps');
});

test('the body names both gestures for a screen reader', () => {
  const tree = render([block(480, 540)]);
  const body = deepNodes(tree, node => node.type === 'View'
    && node.props.accessibilityRole === 'button'
    && typeof node.props.accessibilityLabel === 'string')[0];
  assert.match(body.props.accessibilityLabel, /^08:00至09:00，数学 核心知识复习，点击编辑$/);
  assert.equal(body.props.accessibilityHint, '上下拖动移动时间段，拖动上下边缘调整时长');
});
