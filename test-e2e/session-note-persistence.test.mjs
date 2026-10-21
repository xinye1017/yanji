import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './framework/load-typescript.js';

/**
 * Production SessionNoteModal save boundary.
 *
 * The modal must stay mounted and retain its draft until the native bridge has
 * confirmed that Room accepted the write. These tests drive the shipped
 * component through React hook and native adapters, so a later promise-based
 * Kotlin change keeps the same JS contract without weakening the UI tests.
 */

const tokens = loadTypeScript('src/theme/tokens.ts');
const theme = {
  isDark: false,
  shadow: { floating: () => ({}) },
  paper: {
    colors: tokens.YanjiPaperColors.light,
    typography: tokens.YanjiPaperTypography,
    layout: tokens.YanjiPaperLayout,
    radius: tokens.YanjiPaperRadius,
    motion: tokens.YanjiPaperMotion,
  },
};

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, resolve, reject };
}

const flush = () => new Promise(resolve => setImmediate(resolve));

function nodes(tree, predicate) {
  const result = [];
  const rendered = new Map();
  function walk(node) {
    if (Array.isArray(node)) { node.forEach(walk); return; }
    if (!node || typeof node !== 'object' || !('type' in node)) return;
    if (predicate(node)) result.push(node);
    if (typeof node.type === 'function') {
      if (!rendered.has(node)) rendered.set(node, node.type(node.props));
      walk(rendered.get(node));
      return;
    }
    walk(node.props?.children);
  }
  walk(tree);
  return result;
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

function noteHarness({ updateSessionNote = async () => true } = {}) {
  const calls = [];
  const states = {};
  const refs = {};
  let stateIndex = 0;
  let refIndex = 0;
  let updateImpl = updateSessionNote;
  const react = {
    useState: initial => {
      const i = stateIndex++;
      if (!(i in states)) states[i] = typeof initial === 'function' ? initial() : initial;
      return [states[i], next => {
        states[i] = typeof next === 'function' ? next(states[i]) : next;
      }];
    },
    useRef: initial => {
      const i = refIndex++;
      if (!(i in refs)) refs[i] = { current: initial };
      return refs[i];
    },
    useCallback: fn => fn,
    useEffect: () => {},
  };
  const { SessionNoteModal } = loadTypeScript('src/components/SessionNoteModal.tsx', {
    react,
    'react-native': { Modal: 'Modal', Pressable: 'Pressable', Text: 'Text', View: 'View' },
    'react-native-safe-area-context': { useSafeAreaInsets: () => ({ bottom: 0 }) },
    'react-native-reanimated': { useReducedMotion: () => true },
    '../bridge': {
      YanjiDataNative: {
        updateSessionNote: (...args) => {
          calls.push(args);
          return updateImpl(...args);
        },
      },
    },
    './YanjiUI': {
      YanjiHairline: 'YanjiHairline',
      YanjiPaperSheetHeading: 'YanjiPaperSheetHeading',
      YanjiPrimaryButton: 'YanjiPrimaryButton',
      YanjiTextInput: 'YanjiTextInput',
      useYanjiKeyboardInset: () => 0,
    },
    '../theme/ThemeProvider': { useYanjiTheme: () => theme },
    '../theme/tokens': tokens,
  });

  const props = {
    visible: true,
    sessionId: 'focus-a',
    sessionTitle: '数据结构',
    initialNote: '原始随笔',
    examSession: false,
    onClose: () => calls.push(['close']),
    onSaved: () => calls.push(['saved']),
  };
  let draft = props.initialNote;

  return {
    calls,
    setUpdateSessionNote: next => { updateImpl = next; },
    render: (changes = {}) => {
      Object.assign(props, changes);
      stateIndex = 0;
      refIndex = 0;
      const tree = SessionNoteModal(props);
      const field = nodes(tree, node => node.type === 'YanjiTextInput')[0];
      const input = {
        ...field,
        props: {
          ...field.props,
          value: draft,
          onChangeText: next => {
            draft = next;
            field.props.onChangeText(next);
          },
        },
      };
      const save = nodes(tree, node => node.type === 'YanjiPrimaryButton')[0];
      const heading = nodes(tree, node => node.type === 'YanjiPaperSheetHeading')[0];
      const closePressables = nodes(tree, node =>
        node.type === 'Pressable' && node.props.accessibilityLabel === '关闭');
      return {
        tree,
        input,
        save,
        heading,
        closePressables,
        error: nodes(tree, node => node.type === 'Text' &&
          text(node).includes('保存失败'))[0],
      };
    },
  };
}

test('session-note save stays open pending and sends one same-tick request', async () => {
  const write = deferred();
  const harness = noteHarness({ updateSessionNote: () => write.promise });
  let ui = harness.render();
  ui.input.props.onChangeText('要保存的随笔');
  ui = harness.render();

  const first = ui.save.props.onPress();
  const second = ui.save.props.onPress();
  await flush();
  ui = harness.render();

  assert.equal(harness.calls.filter(call => Array.isArray(call)).length, 1,
    'same-tick taps produce one bridge call');
  assert.equal(ui.save.props.disabled, true);
  assert.equal(ui.save.props.label, '保存中');
  assert.equal(ui.input.props.editable, false,
    'the draft cannot change while its save context is pending');
  assert.equal(ui.heading.props.disabled, true,
    'the heading close control exposes its pending disabled state');
  for (const close of ui.closePressables) {
    assert.equal(close.props.disabled, true,
      'every close path is blocked while the write is pending');
    assert.equal(close.props.accessibilityState.disabled, true);
  }
  assert.equal(harness.calls.some(call => call[0] === 'close'), false,
    'pending does not report success or close');
  assert.equal(harness.calls.some(call => call[0] === 'saved'), false);

  ui = harness.render({ sessionId: 'focus-b', initialNote: '新记录' });
  assert.equal(ui.input.props.value, '要保存的随笔',
    'a parent context prop cannot reseed the draft during a pending save');

  write.resolve(true);
  await Promise.all([first, second]);
  await flush();
  assert.deepEqual(harness.calls.filter(call => call[0] === 'saved' || call[0] === 'close'),
    [['saved'], ['close']], 'only a true result reports success and closes');
});

test('session-note false or rejection keeps the draft and error, then retry succeeds', async () => {
  for (const failure of [false, new Error('room note failed')]) {
    const write = deferred();
    const harness = noteHarness({ updateSessionNote: () => write.promise });
    let ui = harness.render();
    ui.input.props.onChangeText('失败仍保留');
    ui = harness.render();

    const attempt = ui.save.props.onPress();
    await flush();
    if (failure instanceof Error) write.reject(failure);
    else write.resolve(failure);
    await attempt;
    await flush();
    ui = harness.render();

    assert.equal(ui.input.props.value, '失败仍保留');
    assert.equal(ui.save.props.disabled, false);
    assert.ok(ui.error, 'failure shows a retryable error');
    assert.match(text(ui.error), /保存失败/);
    assert.equal(harness.calls.some(call => call[0] === 'close'), false,
      'failure leaves the modal open');
    assert.equal(harness.calls.some(call => call[0] === 'saved'), false,
      'failure never reports success');

    const retry = deferred();
    harness.setUpdateSessionNote(() => retry.promise);
    const retryAttempt = ui.save.props.onPress();
    await flush();
    retry.resolve(true);
    await retryAttempt;
    await flush();
    assert.deepEqual(harness.calls.filter(call => call[0] === 'saved' || call[0] === 'close'),
      [['saved'], ['close']]);
  }
});
