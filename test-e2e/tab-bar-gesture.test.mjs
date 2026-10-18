import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { loadTypeScript } from './framework/load-typescript.js';

test('BottomTabBar source imports PanResponder and binds panHandlers', () => {
  const dock = readFileSync(new URL('../src/navigation/BottomTabBar.tsx', import.meta.url), 'utf8');
  assert.match(dock, /PanResponder/, 'BottomTabBar must import and use PanResponder');
  assert.match(dock, /panResponder\.panHandlers/, 'BottomTabBar must attach panHandlers to the gesture container');
  assert.match(dock, /calculateTabDragOffset/, 'BottomTabBar must export calculateTabDragOffset');
  assert.match(dock, /calculateTargetSlotIndex/, 'BottomTabBar must export calculateTargetSlotIndex');
});

test('calculateTabDragOffset centers pill on finger and clamps within tab bounds', () => {
  const { calculateTabDragOffset } = loadTypeScript('src/navigation/BottomTabBar.tsx', {
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
      useSharedValue: initial => ({ value: initial }),
      useAnimatedStyle: fn => fn(),
      useReducedMotion: () => false,
      withSpring: v => v,
      default: { View: 'AnimatedView' },
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
        motion: { spring: { snappy: {} } },
        isDark: false,
      }),
    },
    '../theme/tokens': { YanjiRadius: { full: 9999 }, YanjiTouch: { min: 44 } },
    '../theme/liquidGlass': {
      YanjiLiquidGlass: {
        targetId: 'blur',
        padding: 5,
        itemPadding: 8,
        iconSize: 20,
        labelSize: 14,
        labelLineHeight: 20,
        labelGap: 4,
        light: { backdrop: '', border: '', tint: '', selection: '' },
        dark: { backdrop: '', border: '', tint: '', selection: '' },
      },
    },
    './useBottomTabLayout': { useBottomTabLayout: () => ({ height: 72, bottom: 16 }) },
  });

  const slotWidth = 100;
  const tabsCount = 3;

  // Center of Tab 0 (slotAreaX = 50) -> offset 0
  assert.equal(calculateTabDragOffset(50, slotWidth, tabsCount), 0);

  // Boundary between Tab 0 and Tab 1 (slotAreaX = 100) -> offset 0.5
  assert.equal(calculateTabDragOffset(100, slotWidth, tabsCount), 0.5);

  // Center of Tab 1 (slotAreaX = 150) -> offset 1
  assert.equal(calculateTabDragOffset(150, slotWidth, tabsCount), 1);

  // Center of Tab 2 (slotAreaX = 250) -> offset 2
  assert.equal(calculateTabDragOffset(250, slotWidth, tabsCount), 2);

  // Left overshoot -> clamped to 0
  assert.equal(calculateTabDragOffset(-30, slotWidth, tabsCount), 0);

  // Right overshoot -> clamped to tabsCount - 1 (2)
  assert.equal(calculateTabDragOffset(400, slotWidth, tabsCount), 2);

  // Safe against 0 or negative slotWidth
  assert.equal(calculateTabDragOffset(50, 0, tabsCount), 0);
});

test('calculateTargetSlotIndex maps horizontal touch coordinates to valid tab slots', () => {
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
      useSharedValue: initial => ({ value: initial }),
      useAnimatedStyle: fn => fn(),
      useReducedMotion: () => false,
      withSpring: v => v,
      default: { View: 'AnimatedView' },
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
        motion: { spring: { snappy: {} } },
        isDark: false,
      }),
    },
    '../theme/tokens': { YanjiRadius: { full: 9999 }, YanjiTouch: { min: 44 } },
    '../theme/liquidGlass': {
      YanjiLiquidGlass: {
        targetId: 'blur',
        padding: 5,
        itemPadding: 8,
        iconSize: 20,
        labelSize: 14,
        labelLineHeight: 20,
        labelGap: 4,
        light: { backdrop: '', border: '', tint: '', selection: '' },
        dark: { backdrop: '', border: '', tint: '', selection: '' },
      },
    },
    './useBottomTabLayout': { useBottomTabLayout: () => ({ height: 72, bottom: 16 }) },
  });

  const slotWidth = 100;
  const tabsCount = 3;

  // Inside Tab 0
  assert.equal(calculateTargetSlotIndex(0, slotWidth, tabsCount), 0);
  assert.equal(calculateTargetSlotIndex(99, slotWidth, tabsCount), 0);

  // Inside Tab 1
  assert.equal(calculateTargetSlotIndex(100, slotWidth, tabsCount), 1);
  assert.equal(calculateTargetSlotIndex(199, slotWidth, tabsCount), 1);

  // Inside Tab 2
  assert.equal(calculateTargetSlotIndex(200, slotWidth, tabsCount), 2);
  assert.equal(calculateTargetSlotIndex(299, slotWidth, tabsCount), 2);

  // Clamped bounds
  assert.equal(calculateTargetSlotIndex(-50, slotWidth, tabsCount), 0);
  assert.equal(calculateTargetSlotIndex(500, slotWidth, tabsCount), 2);
});
