import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './framework/load-typescript.js';

const settle = () => new Promise(resolve => setImmediate(resolve));
function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return { promise, resolve, reject };
}

function fixture(platform = 'android') {
  const state = [], listeners = new Map(), effects = [], removed = [];
  const motion = deferred(), contrast = deferred();
  let cursor = 0, reads = 0;
  const { useNavigationAccessibility } = loadTypeScript('src/navigation/useNavigationAccessibility.ts', {
    react: {
      useState: initial => {
        const i = cursor++;
        if (!(i in state)) state[i] = initial;
        return [state[i], next => { state[i] = next; }];
      },
      useEffect: fn => effects.push(fn),
    },
    'react-native-reanimated': { useReducedMotion: () => true },
    'react-native': {
      Platform: { OS: platform },
      AccessibilityInfo: {
        addEventListener: (event, fn) => {
          listeners.set(event, fn);
          return { remove: () => { listeners.delete(event); removed.push(event); } };
        },
        isReduceMotionEnabled: () => motion.promise,
        isHighTextContrastEnabled: () => { reads++; return contrast.promise; },
      },
    },
  });
  function render() {
    cursor = 0;
    const result = useNavigationAccessibility();
    effects.length = 0;
    return result;
  }
  useNavigationAccessibility();
  const cleanup = effects[0]();
  return { render, motion, contrast, listeners, removed, cleanup, get contrastReads() { return reads; } };
}

test('navigation reads accessibility preferences and follows changes without a restart', async () => {
  const f = fixture();
  assert.deepEqual({ ...f.render() }, { reduceMotion: true, highContrast: false });
  f.motion.resolve(false); f.contrast.resolve(true);
  await settle();
  assert.deepEqual({ ...f.render() }, { reduceMotion: false, highContrast: true });
  f.listeners.get('reduceMotionChanged')(true);
  f.listeners.get('highTextContrastChanged')(false);
  assert.deepEqual({ ...f.render() }, { reduceMotion: true, highContrast: false });
  f.cleanup();
  assert.equal(f.listeners.size, 0);
  assert.equal(f.removed.length, 2);
});

test('a slow initial query cannot overwrite a newer accessibility event', async () => {
  const f = fixture();
  f.listeners.get('reduceMotionChanged')(false);
  f.listeners.get('highTextContrastChanged')(true);
  f.motion.resolve(true); f.contrast.resolve(false);
  await settle();
  assert.deepEqual({ ...f.render() }, { reduceMotion: false, highContrast: true });
  f.cleanup();
});

test('late query results are ignored after unmount and unavailable queries keep safe defaults', async () => {
  const f = fixture();
  f.cleanup();
  f.motion.resolve(false); f.contrast.resolve(true);
  await settle();
  assert.deepEqual({ ...f.render() }, { reduceMotion: true, highContrast: false });
  const failed = fixture();
  failed.motion.reject(new Error('unavailable')); failed.contrast.reject(new Error('unavailable'));
  await settle();
  assert.deepEqual({ ...failed.render() }, { reduceMotion: true, highContrast: false });
  failed.cleanup();
});

test('the Android high text contrast API is never queried on iOS', async () => {
  const f = fixture('ios');
  f.motion.resolve(false);
  await settle();
  assert.equal(f.contrastReads, 0);
  assert.equal(f.listeners.has('highTextContrastChanged'), false);
  assert.deepEqual({ ...f.render() }, { reduceMotion: false, highContrast: false });
  f.cleanup();
});
