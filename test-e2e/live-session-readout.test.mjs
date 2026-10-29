import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './framework/load-typescript.js';

/**
 * The running session's leaf, in its two shapes.
 *
 * The page used to own this clock inline on Today; Review now shows the same
 * session as a capsule. Both read one tick subscription, and both must keep
 * reading the native snapshot until the first tick arrives — a missing tick is
 * "not delivered yet", not "zero seconds".
 */

const tokens = loadTypeScript('src/theme/tokens.ts');
const theme = {
  paper: {
    colors: tokens.YanjiPaperColors.light,
    typography: tokens.YanjiPaperTypography,
    layout: tokens.YanjiPaperLayout,
    radius: tokens.YanjiPaperRadius,
  },
};

function harness({ session = null, tick = null } = {}) {
  const clock = { session, tick };
  const visibility = { active: [], tick: [] };
  const module = loadTypeScript('src/components/LiveSessionReadout.tsx', {
    react: { default: {} },
    'react-native': { View: 'View', Text: 'Text', Pressable: 'Pressable' },
    '../state/timerStore': {
      useActiveSession: visible => { visibility.active.push(visible); return clock.session; },
      useTimerTick: visible => { visibility.tick.push(visible); return clock.tick; },
    },
    '../theme/ThemeProvider': { useYanjiTheme: () => theme },
    '../theme/tokens': tokens,
    './YanjiUI': { YanjiIcon: 'YanjiIcon' },
  });
  return { ...module, clock, visibility };
}

function nodes(tree, predicate, matches = []) {
  if (Array.isArray(tree)) {
    for (const item of tree) nodes(item, predicate, matches);
    return matches;
  }
  if (!tree || typeof tree !== 'object' || !('type' in tree)) return matches;
  if (typeof tree.type === 'function') {
    nodes(tree.type(tree.props), predicate, matches);
    return matches;
  }
  if (predicate(tree)) matches.push(tree);
  nodes(tree.props?.children, predicate, matches);
  return matches;
}

function text(tree) {
  if (Array.isArray(tree)) return tree.map(text).join('');
  if (tree && typeof tree === 'object' && 'type' in tree) {
    if (typeof tree.type === 'function') return text(tree.type(tree.props));
    return text(tree.props?.children);
  }
  if (tree && typeof tree === 'object') return text(tree.props?.children);
  return typeof tree === 'string' || typeof tree === 'number' ? String(tree) : '';
}

const countdown = {
  sessionId: 's-1', subjectName: '概率论与数理统计', isPaused: false,
  isCountdown: true, remainingSeconds: 1200, elapsedSeconds: 60,
};

test('the readout prints countdown remaining and follows the live tick', () => {
  const h = harness({ session: countdown, tick: { remainingSeconds: 1199, elapsedSeconds: 61, isPaused: false } });
  const tree = h.LiveSessionReadout({ session: countdown, visible: true });
  assert.match(text(tree), /概率论与数理统计 正在专注/);
  assert.match(text(tree), /19:59/);
  assert.match(text(tree), /本轮剩余/);
  const readout = nodes(tree, node => node.props?.accessibilityHint === '本轮尚未计入今日已保存总量')[0];
  assert.equal(readout.props.accessibilityLabel, '本轮剩余 19:59，正在专注');
});

test('without a delivered tick the readout keeps the native snapshot, not zero', () => {
  const h = harness({ session: countdown, tick: null });
  const tree = h.LiveSessionReadout({ session: countdown, visible: true });
  assert.match(text(tree), /20:00/, 'the snapshot answers until the first tick arrives');
});

test('a paused stopwatch reads elapsed time and says it is paused', () => {
  const session = { ...countdown, isCountdown: false, elapsedSeconds: 3660 };
  const h = harness({ session, tick: { remainingSeconds: 1199, elapsedSeconds: 3661, isPaused: true } });
  const tree = h.LiveSessionReadout({ session, visible: true });
  assert.match(text(tree), /概率论与数理统计 已暂停/);
  assert.match(text(tree), /1:01:01/);
  assert.match(text(tree), /已持续/);
  const readout = nodes(tree, node => node.props?.accessibilityHint === '本轮尚未计入今日已保存总量')[0];
  assert.equal(readout.props.accessibilityLabel, '本轮已持续 1:01:01，已暂停');
});

test('both shapes hand their page visibility to the timer subscriptions', () => {
  const h = harness({ session: countdown, tick: null });
  h.LiveSessionCapsule({ visible: false, onPress: () => {} });
  h.LiveSessionReadout({ session: countdown, visible: false });
  assert.deepEqual(h.visibility.active, [false], 'a hidden page does not subscribe to session changes');
  assert.deepEqual(h.visibility.tick, [false], 'a hidden page does not subscribe to ticks');

  h.LiveSessionCapsule({ visible: true, onPress: () => {} });
  h.LiveSessionReadout({ session: countdown, visible: true });
  assert.deepEqual(h.visibility.active, [false, true]);
  assert.deepEqual(h.visibility.tick, [false, true], 'a visible page asks for its own ticks only');
});

test('the capsule renders nothing when no session is running', () => {
  const h = harness({ session: null });
  assert.equal(h.LiveSessionCapsule({ visible: true, onPress: () => {} }), null);
});

test('the capsule states the session, its live clock, and returns to focus on press', () => {
  const h = harness({ session: countdown, tick: { remainingSeconds: 1199, elapsedSeconds: 61, isPaused: false } });
  let pressed = 0;
  const tree = h.LiveSessionCapsule({ visible: true, onPress: () => { pressed += 1; } });
  const pressable = nodes(tree, node => node.type === 'Pressable')[0];
  assert.ok(pressable, 'the capsule is one press target');
  assert.equal(pressable.props.accessibilityLabel, '概率论与数理统计，正在专注，本轮剩余 19:59');
  assert.match(pressable.props.accessibilityHint, /返回专注工作台/);
  assert.match(text(tree), /概率论与数理统计 · 正在专注/);
  assert.match(text(tree), /19:59/);
  pressable.props.onPress();
  assert.equal(pressed, 1, 'one tap returns to the focus workbench');
});

test('a paused capsule says so in both the label and the visible line', () => {
  const session = { ...countdown, isPaused: true };
  const h = harness({ session, tick: null });
  const tree = h.LiveSessionCapsule({ visible: true, onPress: () => {} });
  const pressable = nodes(tree, node => node.type === 'Pressable')[0];
  assert.equal(pressable.props.accessibilityLabel, '概率论与数理统计，已暂停，本轮剩余 20:00');
  assert.match(text(tree), /概率论与数理统计 · 已暂停/);
});
