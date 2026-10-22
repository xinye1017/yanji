import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { loadTypeScript } from './framework/load-typescript.js';

test('BottomTabBar source imports PanResponder and binds panHandlers', () => {
  const dock = readFileSync(new URL('../src/navigation/BottomTabBar.tsx', import.meta.url), 'utf8');
  assert.match(dock, /PanResponder/, 'BottomTabBar must import and use PanResponder');
  assert.match(dock, /panResponder\.panHandlers/, 'BottomTabBar must attach panHandlers to the gesture container');
  assert.match(dock, /calculateTargetSlotIndex/, 'BottomTabBar must export calculateTargetSlotIndex');
});

test('calculateTargetSlotIndex maps row coordinates to tab slots and rejects off-row points', () => {
  const { calculateTargetSlotIndex } = loadTypeScript('src/navigation/BottomTabBar.tsx', {
    react: {
      useState: initial => [initial, () => {}],
      useRef: initial => ({ current: initial }),
      useEffect: () => {},
      useMemo: fn => fn(),
    },
    'react-native': {
      View: 'View',
      Text: 'Text',
      Pressable: 'Pressable',
      StyleSheet: { absoluteFill: {} },
      PanResponder: { create: config => config },
    },
    'react-native-blur-overlay': 'BlurOverlay',
    'react-native-reanimated': {
      useReducedMotion: () => false,
    },
    './NavigationShell': {
      TABS: [
        { key: 'today', label: '今天', title: '今天' },
        { key: 'focus', label: '专注', title: '专注' },
        { key: 'review', label: '回顾', title: '回顾' },
      ],
      useNavigationActions: () => ({ selectTab: () => {} }),
      useNavigationState: () => ({ activeTab: 'today' }),
    },
    '../components/YanjiUI': { YanjiIcon: 'YanjiIcon' },
    '../theme/ThemeProvider': {
      useYanjiTheme: () => ({
        paper: { layout: { hairlineWidth: 1 }, colors: { brandInk: '#000', brand: '#f00', ink: '#333' } },
        spacing: { lg: 16 },
        shadow: { floating: () => ({}) },
        motion: { spring: { snappy: { damping: 14, stiffness: 240, mass: 0.8 } } },
        isDark: false,
      }),
    },
    '../theme/tokens': {
      YanjiRadius: { full: 9999 },
      YanjiSpacing: { xs: 4 },
      YanjiTouch: { min: 44 },
    },
    '../theme/liquidGlass': {
      YanjiLiquidGlass: {
        targetId: 'blur',
        padding: 4,
        itemPadding: 3,
        iconSize: 20,
        labelSize: 14,
        labelLineHeight: 20,
        labelGap: 3,
        light: { backdrop: '', border: '', tint: '' },
        dark: { backdrop: '', border: '', tint: '' },
      },
    },
    './useBottomTabLayout': { useBottomTabLayout: () => ({ height: 60, bottom: 16 }) },
  });

  const slotWidth = 100;
  const rowWidth = slotWidth * 3;
  const tabsCount = 3;

  // Inside Tab 0
  assert.equal(calculateTargetSlotIndex(0, rowWidth, slotWidth, tabsCount), 0);
  assert.equal(calculateTargetSlotIndex(99, rowWidth, slotWidth, tabsCount), 0);

  // Inside Tab 1
  assert.equal(calculateTargetSlotIndex(100, rowWidth, slotWidth, tabsCount), 1);
  assert.equal(calculateTargetSlotIndex(199, rowWidth, slotWidth, tabsCount), 1);

  // Inside Tab 2
  assert.equal(calculateTargetSlotIndex(200, rowWidth, slotWidth, tabsCount), 2);
  assert.equal(calculateTargetSlotIndex(299, rowWidth, slotWidth, tabsCount), 2);

  // Off-row points are not tab targets. A release there must not navigate.
  assert.equal(calculateTargetSlotIndex(-50, rowWidth, slotWidth, tabsCount), null);
  assert.equal(calculateTargetSlotIndex(300, rowWidth, slotWidth, tabsCount), null);
  assert.equal(calculateTargetSlotIndex(50, 0, slotWidth, tabsCount), null);
  assert.equal(calculateTargetSlotIndex(50, rowWidth, 0, tabsCount), null);
  assert.equal(calculateTargetSlotIndex(50, rowWidth, slotWidth, 0), null);
});
