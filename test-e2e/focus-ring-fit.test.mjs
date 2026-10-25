import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './framework/load-typescript.js';

/**
 * The immersion field's ring has to fit the window it lands in.
 *
 * Android freeform windows, split screen and landscape all hand this screen far
 * less height than a phone in portrait. The ring used to be sized from the
 * window width alone with a 260dp floor, so a short window kept its portrait
 * diameter and painted it over the subject badge above and the action row
 * below. These cases pin the sizing rule, and then the wiring that feeds it the
 * measured slot rather than assuming one.
 */

const logic = loadTypeScript('src/screens/focusViewLogic.ts');

test('a portrait phone gets the designed ring, centred and capped', () => {
  // A 393dp-wide phone with room to spare is the design this page was drawn at.
  assert.equal(logic.preferredRingSize(393), 299);
  // The cap holds on a tablet, where 0.76 of the width would be a diagram.
  assert.equal(logic.preferredRingSize(800), 320);
  assert.equal(logic.focusRingSize({ windowWidth: 393, freeWidth: 349, freeHeight: 520 }), 299);
});

test('an unmeasured slot paints the designed ring instead of collapsing to zero', () => {
  // Before the first layout pass there is nothing to fit into. Answering 0 here
  // would flash an empty field on every mount and every rotation.
  assert.equal(logic.focusRingSize({ windowWidth: 393, freeWidth: null, freeHeight: null }), 299);
});

test('a slot measured as empty is honoured, not treated as "unknown"', () => {
  // `null` means "not measured"; a measured 0 means the window really gave the
  // ring no room. Conflating the two is how the full portrait ring came back on
  // a window that had already said it had nothing to give.
  assert.equal(logic.focusRingSize({ windowWidth: 393, freeWidth: 0, freeHeight: 0 }), 0);
  assert.equal(logic.focusRingSize({ windowWidth: 393, freeWidth: 349, freeHeight: 0 }), 0);
});

test('a short window shrinks the ring to the space it actually has', () => {
  // A freeform window 640x420dp: the slot between header and actions is only
  // 236dp tall, which is where the old 260dp floor overflowed both neighbours.
  const ring = logic.focusRingSize({ windowWidth: 640, freeWidth: 596, freeHeight: 236 });
  assert.ok(ring <= 236, `the ring must fit the slot it was given, saw ${ring}`);
  assert.equal(ring, 204, 'slot minus the gap on each side');
  // Height is the binding constraint here, not width.
  assert.ok(ring < logic.preferredRingSize(640));
});

test('a narrow window is bounded by width even when it is tall', () => {
  // Split screen in landscape: plenty of height, very little width.
  const ring = logic.focusRingSize({ windowWidth: 320, freeWidth: 180, freeHeight: 500 });
  assert.equal(ring, 148, 'the narrow slot wins over the generous height');
});

test('a very short slot shrinks the gap instead of vanishing the ring', () => {
  // 28dp of slot cannot afford 16dp of air on each side. A fixed gap collapsed
  // the ring to 0 there, so the clock disappeared rather than getting small —
  // a worse failure than a tight fit.
  const ring = logic.focusRingSize({ windowWidth: 400, freeWidth: 356, freeHeight: 28 });
  assert.ok(ring > 0, `a slot with room for something must still paint, saw ${ring}`);
  assert.ok(ring <= 28, 'and it must still fit the slot');
});

test('the ring never exceeds its designed size just because the slot is huge', () => {
  assert.equal(logic.focusRingSize({ windowWidth: 393, freeWidth: 1200, freeHeight: 1200 }), 299);
});

test('the clock is set from its ring, so a shrunk ring takes its numbers with it', () => {
  const portrait = logic.focusClockType(299);
  assert.equal(portrait.fontSize, 72);
  // The portrait display style is 76/82; the line box must scale with the face
  // or a small ring inherits an 82dp line box it cannot hold.
  assert.ok(portrait.lineHeight > portrait.fontSize);
  assert.ok(portrait.lineHeight < portrait.fontSize * 1.2);

  const small = logic.focusClockType(148);
  assert.ok(small.fontSize < portrait.fontSize, 'a smaller ring gets a smaller clock');
  assert.ok(small.lineHeight < portrait.lineHeight);

  // The bounds: a huge ring still caps its face, and a tiny one keeps a floor.
  assert.equal(logic.focusClockType(320).fontSize, 77);
  assert.equal(logic.focusClockType(400).fontSize, 78);
  assert.equal(logic.focusClockType(20).fontSize, 12);
});

test('the immersive readout renders the fitted ring, not the portrait one', () => {
  // Drive the real FocusScreen and read the ring back off the rendered tree.
  // The window here is the freeform case above; the slot is measured through
  // the real `onLayout` handler, so this also proves the wiring.
  const harness = focusScreenHarness({ windowWidth: 640, windowHeight: 420 });
  let tree = harness.render();
  // Before layout the designed ring is painted, which is the pre-measurement
  // answer and not the bug: nothing has told the ring how much room it has.
  assert.equal(ringDiameterOf(tree), logic.preferredRingSize(640));

  // The slot between the header and the actions, as the real layout reports it.
  tree = harness.layoutSlot({ width: 596, height: 236 });
  assert.equal(ringDiameterOf(tree), 204, 'the measured slot shrinks the ring');
  // … and the clock came down with it.
  const clock = findAll(tree, node => node.props?.testID === 'focus-native-clock')[0];
  assert.equal(clock.props.style[1].fontSize, logic.focusClockType(204).fontSize);

  // Rotating back to a tall window restores the designed ring.
  tree = harness.layoutSlot({ width: 596, height: 560 });
  assert.equal(ringDiameterOf(tree), logic.preferredRingSize(640));
});

function ringDiameterOf(tree) {
  const progress = findAll(tree, node => node.props?.accessibilityRole === 'progressbar')[0];
  assert.ok(progress, 'the immersive readout must expose its ring');
  return progress.props.style.width;
}

function findAll(node, predicate, matches = []) {
  if (Array.isArray(node)) for (const element of node) findAll(element, predicate, matches);
  else if (node && typeof node === 'object' && 'type' in node) {
    if (predicate(node)) matches.push(node);
    if (typeof node.type === 'function') findAll(node.type(node.props), predicate, matches);
    else findAll(node.props?.children, predicate, matches);
  }
  return matches;
}

/**
 * The real FocusScreen with native/time adapters mocked, plus a way to report a
 * measured slot the way `onLayout` would.
 */
function focusScreenHarness({ windowWidth = 393, windowHeight = 800 } = {}) {
  const tokens = loadTypeScript('src/theme/tokens.ts');
  const noop = () => {};
  const states = [];
  let stateIndex = 0;
  let effects = [];
  const active = {
    sessionId: 'test-owned', subjectId: 'math', subjectName: '数学',
    mode: 'COUNTDOWN', taskId: null, startTime: 0,
    elapsedSeconds: 10, remainingSeconds: 100, phase: 'FOCUS',
    isPaused: true, isCountdown: true,
  };
  const paper = {
    colors: tokens.YanjiPaperColors.light,
    radius: tokens.YanjiPaperRadius,
    layout: tokens.YanjiPaperLayout,
    typography: tokens.YanjiPaperTypography,
    immersive: tokens.YanjiPaperImmersive,
  };
  const mocks = {
    react: {
      useCallback: fn => fn, useMemo: fn => fn(),
      useEffect: effect => effects.push(effect),
      useRef: initial => ({ current: initial }),
      useState: initial => {
        const index = stateIndex++;
        if (!(index in states)) states[index] = typeof initial === 'function' ? initial() : initial;
        return [states[index], next => {
          states[index] = typeof next === 'function' ? next(states[index]) : next;
        }];
      },
    },
    'react-native': {
      Alert: { alert: noop }, View: 'View', ScrollView: 'ScrollView',
      Pressable: 'Pressable', Text: 'Text', PixelRatio: { roundToNearestPixel: value => value },
      useWindowDimensions: () => ({ width: windowWidth, height: windowHeight, fontScale: 1 }),
      Animated: {
        Value: function (value) { this.val = value; this.interpolate = () => 0; },
        timing: () => ({ start: fn => fn && fn() }), View: 'View',
      },
      Easing: { out: () => () => 0, cubic: () => 0, in: () => () => 0 },
    },
    'react-native-safe-area-context': { useSafeAreaInsets: () => ({ top: 24, bottom: 24 }) },
    'react-native-svg': {
      default: 'Svg', Svg: 'Svg', Defs: 'Defs', LinearGradient: 'LinearGradient',
      Rect: 'Rect', Stop: 'Stop', Circle: 'Circle', Path: 'Path',
    },
    '../bridge': {
      YanjiDataNative: { getSubjects: async () => [] },
      onDataChanged: () => noop,
      YanjiTimerNative: {
        startFocus: async () => {}, discardTimer: async () => {},
        completeTimer: async () => {}, resumeTimer: async () => {}, pauseTimer: async () => {},
      },
    },
    '../components/YanjiUI': {
      YanjiIcon: noop, YanjiPaperSegmentedControl: 'YanjiPaperSegmentedControl',
      YanjiPrimaryButton: 'YanjiPrimaryButton',
    },
    '../components/FocusVernierDial': { FocusVernierDial: 'FocusVernierDial' },
    '../components/RollingTimeDisplay': { RollingTimeDisplay: 'RollingTimeDisplay' },
    '../components/RecordMomentModal': { RecordMomentModal: noop },
    '../components/BackfillFocusModal': { BackfillFocusModal: noop },
    '../navigation/NavigationShell': {
      useNavigationState: () => ({ focusPreset: null }),
      useNavigationActions: () => ({ clearFocusPreset: noop, selectTab: noop }),
    },
    '../navigation/useBottomTabLayout': { useBottomTabLayout: () => ({ contentPadding: 110 }) },
    '../theme/ThemeProvider': { useYanjiTheme: () => ({ paper }) },
    '../state/dataRefresh': { useDailySnapshot: () => ({ tasks: [] }) },
    '../state/timerStore': {
      useActiveSession: () => active,
      useTimerTick: () => ({ elapsedSeconds: 10, remainingSeconds: 100, isPaused: true }),
      syncActiveSession: async () => {},
    },
  };
  const { FocusScreen } = loadTypeScript('src/screens/FocusScreen.tsx', mocks);
  const render = () => {
    stateIndex = 0;
    effects = [];
    return FocusScreen({ visible: true });
  };
  return {
    root: render(),
    render,
    /** Report a measured slot through the real handler, then re-render. */
    layoutSlot(rect) {
      const slot = findAll(render(), node => typeof node.props?.onLayout === 'function'
        && node.props.style?.flex === 1
        && node.props.style?.alignItems === 'center');
      assert.ok(slot.length > 0, 'the ring slot must report its layout');
      slot[0].props.onLayout({ nativeEvent: { layout: rect } });
      return render();
    },
  };
}
