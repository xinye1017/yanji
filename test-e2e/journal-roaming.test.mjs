import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './framework/load-typescript.js';

const theme = {
  isDark: false,
  radius: { sm: 8, md: 12, lg: 16, xl: 24, full: 9999 },
  shadow: {
    floating: () => ({}),
    sheet: () => ({}),
  },
  paper: {
    colors: {
      canvas: '#F6F4EF',
      surface: '#FFFFFF',
      hairline: 'rgba(28,30,33,0.08)',
      scrim: 'rgba(21,20,18,0.88)',
      brand: '#C84B31',
      brandSubtle: 'rgba(200,75,49,0.08)',
      ink: '#1C1E21',
      secondary: '#565960',
      muted: '#94969C',
      danger: '#be3232',
    },
    radius: { sheet: 26, pill: 9999, tag: 8 },
    layout: { pageGutter: 20 },
    typography: {
      captionMeta: { fontSize: 11 },
      titleGroup: { fontSize: 15, fontWeight: '700' },
      bodyJournal: { fontSize: 13 },
      bodyWriting: { fontSize: 15, lineHeight: 24 },
    },
  },
};

function roamingHarness({
  notes = [
    {
      id: 'note-1',
      content: '泰勒展开求极限先看分母主阶数',
      category: 'INSPIRATION',
      timestamp: 1729755120000,
      imageUri: 'file:///data/user/0/com.example.yanji/files/journal_photos/photo1.jpg',
      isFavorite: true,
      isResolved: false,
    },
    {
      id: 'note-2',
      content: '二重积分雅可比行列式的几何意义',
      category: 'DOUBT',
      timestamp: 1729861800000,
      imageUri: null,
      isFavorite: false,
      isResolved: false,
    },
    {
      id: 'note-3',
      content: '红黑树左旋右旋与颜色反转',
      category: 'DOUBT',
      timestamp: 1729685880000,
      imageUri: 'file:///data/user/0/com.example.yanji/files/journal_photos/photo3.jpg',
      isFavorite: true,
      isResolved: true,
    },
  ],
  getRandomNotes = null,
} = {}) {
  const calls = {
    getRandomNotes: 0,
    onClose: 0,
    onEditNote: [],
  };

  const states = [];
  const refs = [];
  const effectSlots = [];
  const pendingEffects = [];
  let hookIndex = 0;
  let refIndex = 0;
  let lastProps = {
    visible: true,
    onClose: () => { calls.onClose++; },
    onEditNote: note => { calls.onEditNote.push(note); },
  };

  const react = {
    useState: initial => {
      const i = hookIndex++;
      if (!(i in states)) states[i] = typeof initial === 'function' ? initial() : initial;
      return [states[i], next => {
        states[i] = typeof next === 'function' ? next(states[i]) : next;
      }];
    },
    useRef: initial => {
      const i = refIndex++;
      if (!refs[i]) refs[i] = { current: initial };
      return refs[i];
    },
    useEffect: (fn, deps) => {
      const i = hookIndex++;
      const previous = effectSlots[i];
      const unchanged = previous && deps && previous.deps &&
        deps.length === previous.deps.length &&
        deps.every((value, index) => Object.is(value, previous.deps[index]));
      if (!unchanged) {
        effectSlots[i] = { deps, fn };
        pendingEffects.push(fn);
      }
    },
    useCallback: fn => fn,
    useMemo: fn => fn(),
  };

  const bridge = {
    YanjiDataNative: {
      getRandomNotes: (limit = 30) => {
        calls.getRandomNotes++;
        if (getRandomNotes) return getRandomNotes(limit);
        return Promise.resolve(notes);
      },
      getAllNotes: () => Promise.resolve(notes),
    },
  };

  const component = loadTypeScript('src/components/RoamingNotesModal.tsx', {
    react,
    'react-native': {
      Modal: 'Modal',
      Pressable: 'Pressable',
      Text: 'Text',
      View: 'View',
      Image: 'Image',
      StyleSheet: { absoluteFill: {}, create: s => s },
      Animated: {
        Value: class {
          constructor(val) { this.val = val; }
          setValue(val) { this.val = val; }
          interpolate() { return this; }
        },
        View: 'AnimatedView',
        timing: () => ({ start: fn => fn?.() }),
        spring: () => ({ start: fn => fn?.() }),
        sequence: animations => ({ start: fn => { for (const a of animations) a?.start?.(); fn?.(); } }),
      },
      PanResponder: {
        create: config => ({ panHandlers: {}, config }),
      },
    },
    'react-native-svg': {
      default: 'Svg',
      Svg: 'Svg',
      Defs: 'Defs',
      LinearGradient: 'LinearGradient',
      Rect: 'Rect',
      Stop: 'Stop',
    },
    'react-native-safe-area-context': {
      useSafeAreaInsets: () => ({ top: 40, bottom: 20 }),
    },
    '../bridge': bridge,
    './YanjiUI': {
      YanjiIcon: 'YanjiIcon',
    },
    '../theme/ThemeProvider': {
      useYanjiTheme: () => theme,
    },
    '../theme/tokens': {
      YanjiSpacing: { xs: 4, sm: 8, md: 12, lg: 16, xl: 24 },
      YanjiRadius: { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, full: 9999 },
    },
  }).RoamingNotesModal;

  function collect(tree) {
    function walk(node, predicate, found = []) {
      if (Array.isArray(node)) {
        for (const child of node) walk(child, predicate, found);
      } else if (node && typeof node === 'object' && 'type' in node) {
        if (predicate(node)) found.push(node);
        walk(node.props?.children, predicate, found);
      }
      return found;
    }

    return {
      tree,
      allText: walk(tree, node => node.type === 'Text')
        .map(n => (Array.isArray(n.props.children) ? n.props.children.join('') : n.props.children))
        .filter(Boolean),
      images: walk(tree, node => node.type === 'Image'),
      pressables: walk(tree, node => node.type === 'Pressable'),
      icons: walk(tree, node => node.type === 'YanjiIcon'),
      findText: text =>
        walk(tree, node => node.type === 'Text' &&
          (node.props.children === text ||
            (Array.isArray(node.props.children) && node.props.children.join('').includes(text))))[0],
      findButtonByLabel: label =>
        walk(tree, node => node.type === 'Pressable' && node.props?.accessibilityLabel === label)[0],
    };
  }

  async function render(props = {}) {
    lastProps = { ...lastProps, ...props };
    let ui;
    for (let pass = 0; pass < 6; pass++) {
      hookIndex = 0;
      refIndex = 0;
      pendingEffects.length = 0;
      const before = [...states];
      const tree = component(lastProps);
      for (const effect of pendingEffects) {
        const cleanup = await effect();
        if (typeof cleanup === 'function') {
          // ignore cleanup for test
        }
      }
      await new Promise(resolve => setImmediate(resolve));
      await new Promise(resolve => setImmediate(resolve));
      ui = collect(tree);
      if (states.every((value, index) => Object.is(value, before[index]))) break;
    }
    return ui;
  }

  return { render, calls, states, refs };
}

test('RoamingNotesModal renders active note with Scheme A photo and wax seal when visible', async () => {
  const harness = roamingHarness();
  const ui = await harness.render({ visible: true });

  assert.ok(ui.tree, 'Modal should render');
  // Counter
  assert.ok(ui.allText.some(t => t.includes('第 1 / 3 篇')), 'Should display note counter');
  // Note 1 content
  assert.ok(ui.allText.some(t => t.includes('泰勒展开求极限先看分母主阶数')), 'Should display active note content');
  // Wax seal: contains YANJI and 迹
  assert.ok(ui.allText.some(t => t.includes('YANJI')), 'Wax seal contains YANJI');
  assert.ok(ui.allText.some(t => t.includes('迹')), 'Wax seal contains 迹');
  // Washi tape contains YANJI ARCHIVE
  assert.ok(ui.allText.some(t => t.includes('YANJI ARCHIVE')), 'Washi tape contains YANJI ARCHIVE');
  // Photo exists for note 1
  assert.equal(ui.images.length, 1, 'Should render photo for note 1');
  assert.equal(ui.images[0].props.source.uri, 'file:///data/user/0/com.example.yanji/files/journal_photos/photo1.jpg');

  // Verify zero emojis
  const emojiRegex = /[\u{1F300}-\u{1F9FF}\u{2600}-\u{26FF}\u{2700}-\u{27BF}]/u;
  for (const text of ui.allText) {
    assert.ok(!emojiRegex.test(text), `Text "${text}" must not contain emojis`);
  }
});

test('RoamingNotesModal switches to next and previous notes and handles non-photo note cleanly', async () => {
  const harness = roamingHarness();
  let ui = await harness.render({ visible: true });

  // Click next button
  const nextBtn = ui.findButtonByLabel('下一篇随笔');
  assert.ok(nextBtn, 'Should have next button');
  nextBtn.props.onPress();

  // Re-render
  ui = await harness.render();
  assert.ok(ui.allText.some(t => t.includes('第 2 / 3 篇')), 'Should advance counter to 2');
  assert.ok(ui.allText.some(t => t.includes('二重积分雅可比行列式的几何意义')), 'Should display note 2 content');
  // Note 2 has no photo (imageUri: null) -> 0 images rendered!
  assert.equal(ui.images.length, 0, 'Non-photo note 2 should have 0 images rendered');

  // Click prev button
  const prevBtn = ui.findButtonByLabel('上一篇随笔');
  assert.ok(prevBtn, 'Should have prev button');
  prevBtn.props.onPress();

  ui = await harness.render();
  assert.ok(ui.allText.some(t => t.includes('第 1 / 3 篇')), 'Should return to note 1');
});

test('RoamingNotesModal random draw button jumps to note or fetches fresh batch', async () => {
  const harness = roamingHarness();
  let ui = await harness.render({ visible: true });

  const randomBtn = ui.findButtonByLabel('随机抽取随笔');
  assert.ok(randomBtn, 'Should have random draw button');
  randomBtn.props.onPress();

  ui = await harness.render();
  assert.ok(ui.allText.some(t => t.includes('篇')), 'Should remain in valid state after random draw');
});

test('RoamingNotesModal close button calls onClose', async () => {
  const harness = roamingHarness();
  const ui = await harness.render({ visible: true });

  const closeBtn = ui.findButtonByLabel('关闭漫游随笔');
  assert.ok(closeBtn, 'Should have close button');
  closeBtn.props.onPress();

  assert.equal(harness.calls.onClose, 1, 'onClose should be called');
});

test('RoamingNotesModal renders empty state when no notes exist', async () => {
  const harness = roamingHarness({ notes: [] });
  const ui = await harness.render({ visible: true });

  assert.ok(ui.allText.some(t => t.includes('暂无随笔')), 'Should display empty state text');
});
