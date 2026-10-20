import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './framework/load-typescript.js';

/**
 * The inline 「记录此刻」writer on Today.
 *
 * These exercise the shipped `RecordComposer`, not a copy: the harness runs the
 * real hook order and re-renders when a state write lands, so "cancel clears",
 * "a failed save keeps the text", "an explicit kind bypasses the decision
 * model" and "a chosen kind is written verbatim" are properties of the code
 * that ships.
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
};

function nodes(tree, predicate) {
  const found = [];
  const walk = node => {
    if (Array.isArray(node)) return node.forEach(walk);
    if (!node || typeof node !== 'object' || !('type' in node)) return;
    if (predicate(node)) found.push(node);
    // Descend into local function components too (the action pair is one), so
    // the assertions see the glyphs the user actually touches.
    if (typeof node.type === 'function') return walk(node.type(node.props));
    walk(node.props.children);
  };
  walk(tree);
  return found;
}

const text = tree => Array.isArray(tree) ? tree.map(text).join('')
  : tree && typeof tree === 'object' ? text(tree.props?.children)
    : typeof tree === 'string' || typeof tree === 'number' ? String(tree) : '';

function composerHarness({ save, classify } = {}) {
  const calls = { saveQuickNote: [], activeSessionReads: 0, classified: [], vibrations: [], keyboardDismissals: [] };
  const states = [];
  const refs = [];
  const effectSlots = [];
  const pendingEffects = [];
  const metrics = [];
  const keyboardListeners = {};
  let hookIndex = 0;
  let keyboardHeight = null;
  let backHandler = null;

  const react = {
    useState: initial => {
      const i = hookIndex++;
      if (!(i in states)) states[i] = typeof initial === 'function' ? initial() : initial;
      return [states[i], next => {
        states[i] = typeof next === 'function' ? next(states[i]) : next;
      }];
    },
    useRef: initial => {
      const i = hookIndex++;
      if (!(i in refs)) refs[i] = { current: initial };
      return refs[i];
    },
    useEffect: (fn, deps) => {
      const i = hookIndex++;
      const previous = effectSlots[i];
      const unchanged = previous && deps && previous.deps &&
        deps.length === previous.deps.length &&
        deps.every((value, index) => Object.is(value, previous.deps[index]));
      if (!unchanged) {
        // Real React tears an effect down before re-running it; listening for
        // the keyboard must stop when the editor collapses, so model that.
        previous?.cleanup?.();
        effectSlots[i] = { deps, fn, cleanup: undefined };
        pendingEffects.push({ slot: effectSlots[i], fn });
      }
    },
    // The shipped handlers are rebuilt each render, exactly like React would
    // after the state writes they perform; returning a memoised copy would make
    // a handler read stale state (e.g. an empty `text`) after `onChangeText`.
    useCallback: fn => (...args) => fn(...args),
  };

  const AnimatedValue = function (value) {
    this.value = value;
    this.setValue = next => { this.value = next; };
    this.stopAnimation = () => {};
  };
  const Animated = {
    Value: AnimatedValue,
    View: 'Animated.View',
    timing: value => ({ start: () => { value.value = 1; } }),
  };

  const scroller = { scrolled: [], scrollTo: ({ y }) => scroller.scrolled.push(y) };
  const card = {
    measureInWindow: callback => callback(0, metrics.shift() ?? 0, 300, 60),
  };
  // The field's imperative surface; `blur` is what collapsing and back use.
  const inputInstance = { focus() {}, blur() {} };
  const component = loadTypeScript('src/components/RecordComposer.tsx', {
    react,
    'react-native': {
      View: 'View', Text: 'Text', Pressable: 'Pressable', TextInput: 'TextInput',
      Animated,
      Easing: { out: value => value, quad: 'quad' },
      Keyboard: {
        metrics: () => (keyboardHeight === null ? null : { screenY: keyboardHeight }),
        addListener: (event, listener) => {
          keyboardListeners[event] = listener;
          return { remove() { delete keyboardListeners[event]; } };
        },
        dismiss: () => calls.keyboardDismissals.push('dismiss'),
      },
      BackHandler: {
        addEventListener: (event, listener) => {
          backHandler = listener;
          return { remove() { backHandler = null; } };
        },
      },
      Vibration: { vibrate: ms => calls.vibrations.push(ms) },
      useWindowDimensions: () => ({ fontScale: 1, width: 390, height: 844 }),
    },
    'react-native-reanimated': { useReducedMotion: () => true },
    '../bridge': {
      YanjiDataNative: {
        saveQuickNote: (...args) => {
          calls.saveQuickNote.push(args);
          return save ? save(...args) : Promise.resolve({ id: 'note-1' });
        },
      },
      YanjiTimerNative: {
        getActiveSession: () => {
          calls.activeSessionReads++;
          return Promise.resolve({ sessionId: 'session-live' });
        },
      },
    },
    '../services/decisionClient': {
      classifyMoment: async content => {
        calls.classified.push(content);
        return classify ? classify(content) : 'INSPIRATION';
      },
    },
    './YanjiUI': { YanjiIcon: 'YanjiIcon' },
    '../theme/ThemeProvider': { useYanjiTheme: () => theme },
    '../theme/tokens': { YanjiSpacing: tokens.YanjiSpacing, YanjiTouch: tokens.YanjiTouch },
  }).RecordComposer;

  // Real React assigns host refs after the render commits; `keepVisible` reads
  // `cardRef.current`, so the harness has to do the same.
  function attachRefs(node) {
    if (Array.isArray(node)) return node.forEach(attachRefs);
    if (!node || typeof node !== 'object' || !('type' in node)) return;
    const { ref, children } = node.props ?? {};
    if (node.type === 'View' && ref) ref.current = card;
    if (node.type === 'TextInput' && ref) ref.current = inputInstance;
    attachRefs(children);
  }

  let lastProps = { date: '2026-10-16' };
  function render(props = {}) {
    lastProps = { ...lastProps, ...props };
    let tree;
    for (let pass = 0; pass < 8; pass++) {
      hookIndex = 0;
      pendingEffects.length = 0;
      const before = [...states];
      tree = component(lastProps);
      attachRefs(tree);
      for (const effect of pendingEffects) effect.slot.cleanup = effect.fn();
      if (states.every((value, index) => Object.is(value, before[index]))) break;
    }
    return collect(tree);
  }

  function collect(tree) {
    const pressables = nodes(tree, node => node.type === 'Pressable');
    return {
      tree,
      text: text(tree),
      input: nodes(tree, node => node.type === 'TextInput')[0],
      pressable: label => pressables.find(node => node.props.accessibilityLabel === label),
      radios: nodes(tree, node => node.props?.accessibilityRole === 'radio'),
      kindsToggle: pressables.find(node => (node.props.accessibilityLabel ?? '').startsWith('记录类型')),
    };
  }

  return {
    render, calls, refs, scroller, card,
    setKeyboardScreenY: value => { keyboardHeight = value; },
    measureCardAt: y => metrics.push(y),
    keyboardListening: () => 'keyboardDidShow' in keyboardListeners,
    showKeyboard: () => keyboardListeners.keyboardDidShow?.(),
    pressBack: () => backHandler?.(),
  };
}

const typeInto = (ui, value) => {
  ui.input.props.onChangeText(value);
};

/**
 * The composer's save handler starts with `void save()`, so the press returns
 * before the request finishes — exactly like the shipped button. Flushing the
 * microtask queue is what lets the assertions observe the completed request.
 */
const settle = () => new Promise(resolve => setTimeout(resolve, 0));

test('the collapsed bar is one line with an invitation and a single add control', () => {
  const h = composerHarness();
  const ui = h.render();

  assert.equal(ui.input, undefined, 'no field is mounted before the user starts');
  assert.equal(ui.text, '记录此刻的想法…');
  assert.ok(ui.pressable('开始记录'), 'the + is the explicit entry point');
  assert.ok(ui.pressable('记录此刻的想法'), 'the placeholder line itself focuses the writer');
  assert.equal(ui.pressable('保存记录'), undefined, 'no save/cancel pair while collapsed');
  assert.equal(h.calls.saveQuickNote.length, 0);
});

test('tapping the bar enters the editor with save and cancel in reach', () => {
  const h = composerHarness();
  h.render().pressable('开始记录').props.onPress();
  const ui = h.render({ date: '2026-10-20' });

  assert.ok(ui.input, 'the field is mounted by the tap');
  assert.equal(ui.input.props.autoFocus, true, 'the keyboard comes up from the same tap');
  assert.equal(ui.input.props.multiline, true);
  assert.equal(ui.pressable('开始记录'), undefined);
  assert.ok(ui.pressable('保存记录'));
  assert.ok(ui.pressable('取消编辑'));
});

test('an empty capture refuses to save and says why', async () => {
  const h = composerHarness();
  h.render().pressable('开始记录').props.onPress();
  const ui = h.render();
  typeInto(ui, '   \n  ');
  await h.render().pressable('保存记录').props.onPress();

  assert.equal(h.calls.saveQuickNote.length, 0, 'whitespace never reaches Room');
  const after = h.render();
  assert.equal(after.input.props.value, '   \n  ', 'the rejected draft is still there');
  assert.match(after.text, /写点什么再保存吧/);
});

test('an unset kind keeps the decision model in charge and writes its answer verbatim', async () => {
  const h = composerHarness({ classify: async () => 'DOUBT' });
  h.render().pressable('开始记录').props.onPress();
  typeInto(h.render(), '  反常积分为什么在这里收敛？  ');
  h.render().pressable('保存记录').props.onPress();
  await settle();

  assert.deepEqual(h.calls.classified, ['反常积分为什么在这里收敛？']);
  assert.deepEqual(h.calls.saveQuickNote,
    [['反常积分为什么在这里收敛？', '2026-10-16', 'session-live', 'DOUBT']],
    'the note binds the live session and the classified category');
  assert.deepEqual(h.calls.vibrations, [10], 'a saved note ticks the hand once');
});

test('a chosen kind is written verbatim and never classified', async () => {
  const h = composerHarness({ classify: async () => 'DOUBT' });
  const opened = h.render();
  opened.kindsToggle.props.onPress();
  const kinds = h.render().radios;
  assert.deepEqual(kinds.map(node => node.props.accessibilityLabel.split('：')[0]), ['灵感', '疑点']);

  kinds.find(node => node.props.accessibilityLabel.startsWith('疑点')).props.onPress();
  h.render().pressable('记录此刻的想法').props.onPress();
  typeInto(h.render(), '泰勒展开的余项');
  h.render().pressable('保存记录').props.onPress();
  await settle();

  assert.deepEqual(h.calls.classified, [], 'an explicit 疑点 never asks the model');
  assert.equal(h.calls.saveQuickNote[0][3], 'DOUBT');
  assert.equal(h.calls.saveQuickNote[0][1], '2026-10-16');

  // The save consumed the choice: the next capture starts from automatic again.
  assert.match(h.render().kindsToggle.props.accessibilityLabel, /自动判断/);

  // Tapping the chosen kind inside one editing session returns to automatic.
  h.render().kindsToggle.props.onPress();
  h.render().radios.find(node => node.props.accessibilityLabel.startsWith('灵感')).props.onPress();
  const chosen = h.render();
  assert.match(chosen.kindsToggle.props.accessibilityLabel, /灵感/);
  chosen.kindsToggle.props.onPress();
  h.render().radios.find(node => node.props.accessibilityLabel.startsWith('灵感')).props.onPress();
  assert.match(h.render().kindsToggle.props.accessibilityLabel, /自动判断/);
});

test('a failed save keeps the text, exits no editor, and reports itself', async () => {
  const h = composerHarness({ save: () => Promise.reject(new Error('room write failed')) });
  h.render().pressable('开始记录').props.onPress();
  typeInto(h.render(), '保存失败也要留住');
  h.render().pressable('保存记录').props.onPress();
  await settle();

  const ui = h.render();
  assert.equal(ui.input.props.value, '保存失败也要留住');
  assert.match(ui.text, /保存失败，内容已保留/);
  assert.ok(ui.pressable('保存记录'), 'the editor stays open for a retry');
  assert.equal(h.calls.vibrations.length, 0, 'a failure must not celebrate');
});

test('repeated taps while saving issue exactly one write', async () => {
  let release;
  const h = composerHarness({ save: () => new Promise(resolve => { release = resolve; }) });
  h.render().pressable('开始记录').props.onPress();
  typeInto(h.render(), '一次就够');
  h.render().pressable('保存记录').props.onPress();

  // Between two real taps a render lands, so the button already knows it is
  // busy; a second press must not start a second write.
  const saving = h.render();
  // The in-flight control renames itself and disables in the same render, so a
  // second tap cannot even reach the handler.
  const inFlight = saving.pressable('正在保存记录');
  assert.ok(inFlight, 'the save control names its in-flight state');
  assert.equal(inFlight.props.accessibilityState.busy, true);
  assert.equal(inFlight.props.disabled, true);
  inFlight.props.onPress();
  await settle();
  release({ id: 'note-1' });
  await settle();

  assert.equal(h.calls.saveQuickNote.length, 1, 'the in-flight save absorbs the second tap');
});

test('a successful save clears the draft and collapses the bar', async () => {
  const h = composerHarness();
  h.render().pressable('开始记录').props.onPress();
  typeInto(h.render(), '收进今天');
  h.render().pressable('保存记录').props.onPress();
  await settle();

  const ui = h.render();
  assert.equal(ui.input, undefined, 'the editor collapsed after the save');
  assert.equal(ui.text, '记录此刻的想法…', 'the invitation is back, with no stale draft');
});

test('cancel discards the draft without writing anything', () => {
  const h = composerHarness();
  h.render().pressable('开始记录').props.onPress();
  typeInto(h.render(), '不要这一条');
  h.render().pressable('取消编辑').props.onPress();

  const ui = h.render();
  assert.equal(ui.input, undefined);
  assert.equal(ui.text, '记录此刻的想法…');
  assert.equal(h.calls.saveQuickNote.length, 0);
  assert.deepEqual(h.calls.keyboardDismissals, ['dismiss'], 'cancel closes the keyboard');
});

test('a draft survives collapsing the editor, and the system back does not discard it', () => {
  const h = composerHarness();
  h.render().pressable('开始记录').props.onPress();
  typeInto(h.render(), '切到回顾再回来');
  h.pressBack();

  const collapsed = h.render();
  assert.equal(collapsed.input, undefined, 'back collapses the editor');
  assert.equal(collapsed.text, '切到回顾再回来', 'the bar shows the unsaved draft');
  assert.ok(collapsed.pressable('继续编辑未保存的记录'), 'and names the state it is in');
});

test('the composer lifts itself above the keyboard instead of resizing the page', () => {
  const h = composerHarness();
  const scrollRef = { current: h.scroller };
  const scrollOffsetRef = { current: 400 };
  const props = { date: '2026-10-16', scrollRef, scrollOffsetRef };
  h.setKeyboardScreenY(500);
  // The card's bottom (480) plus the 12dp gap clears the IME top at 500, so
  // nothing moves.
  h.measureCardAt(420);
  h.render(props).pressable('开始记录').props.onPress();
  h.render(props);
  assert.equal(h.keyboardListening(), true, 'the editor follows the keyboard');
  h.showKeyboard();
  assert.deepEqual(h.scroller.scrolled, [], 'a visible writer is not moved');

  // The card now ends at 532: the page scrolls by the 32px overflow on top of
  // the live offset, so the writer sits exactly `YanjiSpacing.md` above the IME.
  h.measureCardAt(460);
  h.showKeyboard();
  assert.deepEqual(h.scroller.scrolled, [432]);

  // Collapsing tears the listener down; a later keyboard event moves nothing.
  h.render(props).pressable('取消编辑').props.onPress();
  h.render(props);
  assert.equal(h.keyboardListening(), false);
  h.showKeyboard();
  assert.deepEqual(h.scroller.scrolled, [432]);
});
