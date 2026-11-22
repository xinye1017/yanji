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
    immersive: {
      running: {
        canvas: '#12100C',
        ink: '#F7F1E7',
        secondary: '#C9C0B1',
        subtle: 'rgba(255,246,232,0.08)',
        pillBg: 'rgba(255,246,232,0.05)',
        pillBorder: 'rgba(255,246,232,0.09)',
        accent: '#9DBAD4',
        primaryActionBg: 'rgba(157,186,212,0.16)',
        primaryActionBorder: 'rgba(157,186,212,0.30)',
        cardSurface: '#221E19',
        cardHairline: 'rgba(255,246,232,0.10)',
        cardBone: 'rgba(255,246,232,0.10)',
        cardTape: 'rgba(255,246,232,0.12)',
      },
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
  toggleFavoriteNote = null,
} = {}) {
  const calls = {
    getRandomNotes: 0,
    toggleFavoriteNote: [],
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
      toggleFavoriteNote: id => {
        calls.toggleFavoriteNote.push(id);
        return toggleFavoriteNote ? toggleFavoriteNote(id) : Promise.resolve(true);
      },
    },
  };

  const module = loadTypeScript('src/components/RoamingNotesModal.tsx', {
    react,
    'react-native': {
      Modal: 'Modal',
      Pressable: 'Pressable',
      Text: 'Text',
      View: 'View',
      Image: 'Image',
      ScrollView: 'ScrollView',
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
      RadialGradient: 'RadialGradient',
      Rect: 'Rect',
      Stop: 'Stop',
    },
    'react-native-safe-area-context': {
      useSafeAreaInsets: () => ({ top: 40, bottom: 20 }),
    },
    'react-native-reanimated': {
      useReducedMotion: () => false,
    },
    '../bridge': bridge,
    './YanjiUI': {
      YanjiIcon: 'YanjiIcon',
      YanjiSkeleton: 'YanjiSkeleton',
      YanjiSkeletonBone: 'YanjiSkeletonBone',
      YanjiSkeletonText: 'YanjiSkeletonText',
    },
    '../theme/ThemeProvider': {
      useYanjiTheme: () => theme,
    },
    '../theme/tokens': {
      YanjiSpacing: { xs: 4, sm: 8, md: 12, lg: 16, xl: 24 },
      YanjiRadius: { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, full: 9999 },
      YanjiTouch: { min: 44, comfortable: 48 },
    },
  });
  const component = module.RoamingNotesModal;

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
      find: predicate => walk(tree, predicate)[0],
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

  return { render, calls, states, refs, module };
}

test('RoamingNotesModal renders active note with Scheme A photo and wax seal when visible', async () => {
  const harness = roamingHarness();
  const ui = await harness.render({ visible: true });

  assert.ok(ui.tree, 'Modal should render');
  // Counter
  assert.ok(ui.allText.some(t => t.includes('第 1 / 3 篇')), 'Should display note counter');
  // Note 1 content
  assert.ok(ui.allText.some(t => t.includes('泰勒展开求极限先看分母主阶数')), 'Should display active note content');
  // The seal is one legible glyph now; the unreadable 6.5sp 「YANJI」 line and the
  // 7.5sp 「YANJI ARCHIVE」 on the tape are gone.
  assert.ok(ui.allText.some(t => t.includes('迹')), 'Wax seal contains 迹');
  assert.ok(!ui.allText.some(t => String(t).includes('YANJI')), 'No decorative Latin text is left on the card');
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

test('RoamingNotesModal uses the always-dark immersive palette for the field and controls', async () => {
  const harness = roamingHarness();
  const ui = await harness.render({ visible: true });

  // The scrim is the immersive canvas, not a hardcoded cold grey, so it stays
  // on the warm paper family and is independent of the app light/dark theme.
  const scrim = ui.find(node => node.type === 'View' && node.props?.accessibilityViewIsModal);
  assert.ok(scrim, 'Should find the modal scrim');
  assert.equal(scrim.props.style[1].backgroundColor, '#12100C', 'Scrim uses immersive canvas');

  // The ambient glow reads the tide accent so it stays visible on the dark field.
  const radialStop = ui.find(node => node.type === 'Stop' && node.props?.stopOpacity === '0.14');
  assert.ok(radialStop, 'Should render the ambient glow with the immersive accent');
  assert.equal(radialStop.props.stopColor, '#9DBAD4');

  // Icon-only controls reach the minimum touch target.
  const closeBtn = ui.findButtonByLabel('关闭漫游随笔');
  const closeStyle = closeBtn.props.style({ pressed: false });
  assert.equal(closeStyle[0].width, 44);
  assert.equal(closeStyle[0].height, 44);
});

test('RoamingNotesModal draws the card as a wireframe instead of the empty state while fetching', async () => {
  // A deferred batch lets us observe the loading render before it resolves.
  let resolveBatch;
  const deferred = new Promise(resolve => { resolveBatch = resolve; });
  const harness = roamingHarness({ getRandomNotes: () => deferred });
  const ui = await harness.render({ visible: true });

  const skeleton = ui.find(node => node.type === 'YanjiSkeleton');
  assert.ok(skeleton, 'Should draw the card wireframe while fetching');
  assert.equal(skeleton.props.accessibilityLabel, '正在拾取随笔', 'The wireframe announces what it waits for');
  assert.ok(!ui.allText.some(t => t.includes('暂无随笔')), 'Should not flash empty state while loading');

  resolveBatch(harnessStatesNotes());
  // Resolve with a real note so the card renders after loading.
  function harnessStatesNotes() {
    return [{
      id: 'note-x', content: '加载完成后的随笔', category: 'INSPIRATION',
      timestamp: 1729755120000, imageUri: null, isFavorite: false, isResolved: false,
    }];
  }
  const settled = await harness.render();
  assert.ok(settled.allText.some(t => t.includes('加载完成后的随笔')), 'Should render the note once loaded');
});

// ---------------------------------------------------------------------------
// The card itself: dark paper, a legible date, its own actions, and a cap so a
// long note scrolls inside it instead of covering the controls.
// ---------------------------------------------------------------------------

function flatten(style) {
  if (Array.isArray(style)) return Object.assign({}, ...style.map(flatten));
  return style && typeof style === 'object' ? style : {};
}

test('roamingDateLabel adds the year for another year and a calendar-day relative time', () => {
  const { roamingDateLabel } = roamingHarness().module;
  const now = new Date(2026, 10, 22, 0, 10).getTime();

  const today = roamingDateLabel(new Date(2026, 10, 22, 0, 5).getTime(), now);
  assert.equal(today.date, '11月22日 00:05', 'this year carries no year');
  assert.equal(today.relative, '今天');
  // Counted between local midnights: 23:50 last night is yesterday, not today.
  assert.equal(roamingDateLabel(new Date(2026, 10, 21, 23, 50).getTime(), now).relative, '昨天');
  assert.equal(roamingDateLabel(new Date(2026, 10, 12, 9, 0).getTime(), now).relative, '10 天前');
  assert.equal(roamingDateLabel(new Date(2026, 9, 1, 9, 0).getTime(), now).relative, '1 个月前');

  const lastYear = roamingDateLabel(new Date(2025, 10, 20, 15, 30).getTime(), now);
  assert.equal(lastYear.date, '2025年11月20日 15:30', 'an older year is spelled out');
  assert.equal(lastYear.relative, '1 年前');
  // A clock that ran ahead never produces a negative 「-1 天前」.
  assert.equal(roamingDateLabel(new Date(2026, 10, 23, 9, 0).getTime(), now).relative, '');
});

test('the card is dark paper from the immersive palette, not the light theme surface', async () => {
  const ui = await roamingHarness().render({ visible: true });
  const card = ui.find(node => node.type === 'AnimatedView'
    && 'maxHeight' in flatten(node.props.style));
  assert.ok(card, 'the active card is rendered');
  assert.equal(flatten(card.props.style).backgroundColor, '#221E19', 'card uses immersive cardSurface');
  assert.notEqual(flatten(card.props.style).backgroundColor, theme.paper.colors.surface);
});

test('a long note is capped to the stage and scrolls inside the card', async () => {
  const harness = roamingHarness();
  let ui = await harness.render({ visible: true });
  const cardOf = tree => tree.find(node => node.type === 'AnimatedView'
    && 'maxHeight' in flatten(node.props.style));
  assert.equal(flatten(cardOf(ui).props.style).maxHeight, undefined, 'no cap before the stage is measured');

  const stage = ui.find(node => node.type === 'View' && typeof node.props?.onLayout === 'function');
  stage.props.onLayout({ nativeEvent: { layout: { height: 520 } } });
  ui = await harness.render();
  // 520 minus the deck's overhang (14) and the stage's own padding (12 × 2).
  assert.equal(flatten(cardOf(ui).props.style).maxHeight, 520 - 14 - 24);

  const scroll = ui.find(node => node.type === 'ScrollView');
  assert.ok(scroll, 'the note sits in a scroll view');
  assert.equal(scroll.key, 'note-1', 'keyed by note, so a new card opens at its first line');
  assert.ok(ui.find(node => node.type === 'Text' && node.props.children === '泰勒展开求极限先看分母主阶数'));
});

test('a short note still draws a card, and the floor never outgrows a short stage', async () => {
  // On device a one-line note made the card a thin banner in an empty field.
  const harness = roamingHarness();
  let ui = await harness.render({ visible: true });
  const cardOf = tree => tree.find(node => node.type === 'AnimatedView'
    && 'maxHeight' in flatten(node.props.style));
  assert.equal(flatten(cardOf(ui).props.style).minHeight, 240, 'a minimum card height before layout');

  const stage = () => ui.find(node => node.type === 'View' && typeof node.props?.onLayout === 'function');
  stage().props.onLayout({ nativeEvent: { layout: { height: 520 } } });
  ui = await harness.render();
  assert.equal(flatten(cardOf(ui).props.style).minHeight, 240);

  // A short stage (split screen, large font) caps the floor at the stage limit.
  stage().props.onLayout({ nativeEvent: { layout: { height: 220 } } });
  ui = await harness.render();
  const style = flatten(cardOf(ui).props.style);
  assert.equal(style.minHeight, style.maxHeight, 'min never exceeds max');
  assert.equal(style.maxHeight, 220 - 14 - 24);
});

test('favourite on the card writes Room and only flips on a confirmed write', async () => {
  const harness = roamingHarness();
  let ui = await harness.render({ visible: true });
  // note-1 starts favourited.
  const unfavorite = ui.findButtonByLabel('取消收藏这篇随笔');
  assert.ok(unfavorite, 'a favourited note offers to remove it');
  assert.equal(unfavorite.props.accessibilityState.selected, true);
  unfavorite.props.onPress();
  ui = await harness.render();
  assert.deepEqual(harness.calls.toggleFavoriteNote, ['note-1']);
  assert.ok(ui.findButtonByLabel('收藏这篇随笔'), 'the confirmed write flips the card');
  assert.ok(ui.findText('收藏'), 'the visible label follows');

  // A note Room no longer has (false) or a failed write leaves the card as stored.
  for (const reply of [async () => false, async () => { throw new Error('room'); }]) {
    const h = roamingHarness({ toggleFavoriteNote: reply });
    let tree = await h.render({ visible: true });
    tree.findButtonByLabel('取消收藏这篇随笔').props.onPress();
    tree = await h.render();
    assert.ok(tree.findButtonByLabel('取消收藏这篇随笔'), 'an unconfirmed write changes nothing');
  }
});

test('editing is a named button on the card that hands over the current note', async () => {
  const harness = roamingHarness();
  const ui = await harness.render({ visible: true });
  const edit = ui.findButtonByLabel('编辑这篇随笔');
  assert.ok(edit, 'the card offers an explicit edit button');
  assert.ok(ui.findText('编辑'), 'with a visible label');
  edit.props.onPress();
  assert.equal(harness.calls.onEditNote.length, 1);
  assert.equal(harness.calls.onEditNote[0].id, 'note-1');
  assert.equal(harness.calls.onEditNote[0].imageUri,
    'file:///data/user/0/com.example.yanji/files/journal_photos/photo1.jpg');
});
