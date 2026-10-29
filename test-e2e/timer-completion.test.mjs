import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './framework/load-typescript.js';
import { BridgeSchemas } from './contracts/bridge-schema.js';

const settle = () => new Promise(resolve => setImmediate(resolve));
const sample = { sessionId: 'one', subjectName: '高等数学', elapsedSeconds: 1500, isExam: false };
function fixture({ initial = null, acknowledge = async () => true } = {}) {
  const states = [], effects = [], calls = [];
  let cursor = 0, listener, mounted = false;
  const module = loadTypeScript('src/components/TimerCompletionModal.tsx', {
    react: {
      useState(value) {
        const index = cursor++;
        if (!(index in states)) states[index] = value;
        return [states[index], next => { states[index] = typeof next === 'function' ? next(states[index]) : next; }];
      },
      useEffect(effect) { if (!mounted) effects.push(effect); },
    },
    'react-native': { Modal: 'Modal', ScrollView: 'ScrollView', Text: 'Text', View: 'View' },
    'react-native-reanimated': { useReducedMotion: () => true },
    '../bridge': {
      onTimerCompleted: callback => { listener = callback; return () => {}; },
      YanjiTimerNative: {
        getTimerCompletion: () => Promise.resolve(initial),
        acknowledgeTimerCompletion: id => { calls.push(id); return acknowledge(id); },
      },
    },
    '../theme/ThemeProvider': { useYanjiTheme: () => ({ paper: { colors: {}, typography: {}, radius: {}, layout: {} } }) },
    '../theme/tokens': { YanjiSpacing: {} },
    './YanjiUI': { YanjiPrimaryButton: 'PrimaryButton' },
  });
  return {
    module, calls,
    emit: value => listener(value),
    render(foreground = true) {
      cursor = 0;
      const tree = module.TimerCompletionModal({ foreground });
      if (!mounted) { mounted = true; effects.forEach(effect => effect()); }
      return tree;
    },
  };
}
function find(node, type) {
  if (!node || typeof node !== 'object') return null;
  if (Array.isArray(node)) return node.map(child => find(child, type)).find(Boolean);
  return node.type === type ? node : find(node.props?.children, type);
}
function text(node) {
  if (Array.isArray(node)) return node.map(text).join(' ');
  if (typeof node === 'string') return node;
  return node && typeof node === 'object' ? text(node.props?.children) : '';
}

test('completion contract accepts native facts and rejects missing or invalid duration', () => {
  assert.equal(BridgeSchemas.isValidTimerCompletion(sample), true);
  assert.equal(BridgeSchemas.isValidTimerCompletion({ ...sample, elapsedSeconds: NaN }), false);
  assert.equal(BridgeSchemas.isValidTimerCompletion({ subjectName: '数学' }), false);
});

test('foreground completion renders a centered result with the actual subject and duration', async () => {
  const f = fixture();
  assert.equal(f.render(), null);
  f.emit(sample);
  const tree = f.render();
  assert.ok(find(tree, 'Modal'));
  assert.match(text(tree), /已计时完成/);
  assert.match(text(tree), /高等数学/);
  assert.match(text(tree), /25 分钟/);
  await settle();
  assert.match(text(f.render()), /高等数学/, 'a stale initial read must not clear the event');
  assert.equal(f.render(false), null, 'background must not display an in-app dialog');
  await find(tree, 'PrimaryButton').props.onPress();
  await settle();
  assert.deepEqual(f.calls, ['one']);
  assert.equal(f.render(), null);
});

test('a pending foreground completion survives remount and failed dismissal remains retryable', async () => {
  const f = fixture({ initial: sample, acknowledge: async () => { throw new Error('native unavailable'); } });
  f.render(); await settle();
  await find(f.render(), 'PrimaryButton').props.onPress(); await settle();
  assert.match(text(f.render()), /暂时无法关闭/);
  assert.match(text(f.render()), /25 分钟/);
});

test('result duration keeps seconds and hours instead of rounding away short sessions', () => {
  const { completionDuration } = fixture().module;
  assert.equal(completionDuration(9), '9 秒');
  assert.equal(completionDuration(3661), '1 小时 1 分钟 1 秒');
});
