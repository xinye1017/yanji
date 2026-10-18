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
  assert.match(dock, /calculatePillDeformation/, 'BottomTabBar must export calculatePillDeformation');
});

test('calculatePillDeformation produces wall collision squash without overflowing bounds', () => {
  const { calculatePillDeformation } = loadTypeScript('src/navigation/BottomTabBar.tsx', {
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
        motion: { spring: { snappy: { damping: 14, stiffness: 240, mass: 0.8 } } },
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
  const wallRight = tabsCount * slotWidth;

  // 1. At rest at slot 1
  const rest = calculatePillDeformation(1, 1, slotWidth, tabsCount);
  assert.equal(rest.translateX, 100, 'translateX is slot 1 position at rest');
  assert.equal(rest.scaleX, 1, 'scaleX is 1 at rest');
  assert.equal(rest.scaleY, 1, 'scaleY is 1 at rest');

  // 2. Dragging right in interior (lead ahead of trail: lead = 1.5, trail = 1.1)
  const draggingRight = calculatePillDeformation(1.5, 1.1, slotWidth, tabsCount);
  assert.ok(draggingRight.scaleX > 1, 'scaleX stretches in open interior');
  assert.ok(draggingRight.scaleY < 1, 'scaleY compresses in open interior');

  // 3. Wall collision on Left Wall: lead pulled negative (e.g. -0.4)
  const leftCollision = calculatePillDeformation(-0.4, 0, slotWidth, tabsCount);
  const leftVisualLeft = leftCollision.translateX + slotWidth / 2 - (slotWidth * leftCollision.scaleX) / 2;
  const leftVisualRight = leftCollision.translateX + slotWidth / 2 + (slotWidth * leftCollision.scaleX) / 2;
  assert.ok(Math.abs(leftVisualLeft) < 0.001, `visual left must be pinned to 0 on wall impact (saw ${leftVisualLeft})`);
  assert.ok(leftVisualRight <= wallRight, 'visual right must not exceed right boundary');
  assert.ok(leftCollision.scaleX < 1, 'pill must squash horizontally against left wall');
  assert.ok(leftCollision.scaleY > 1, 'pill must bulge vertically against left wall');

  // 4. Wall collision on Right Wall: lead pushed beyond tab 2 (e.g. 2.5)
  const rightCollision = calculatePillDeformation(2.5, 2.0, slotWidth, tabsCount);
  const rightVisualLeft = rightCollision.translateX + slotWidth / 2 - (slotWidth * rightCollision.scaleX) / 2;
  const rightVisualRight = rightCollision.translateX + slotWidth / 2 + (slotWidth * rightCollision.scaleX) / 2;
  assert.ok(Math.abs(rightVisualRight - wallRight) < 0.001, `visual right must be pinned to wallRight on wall impact (saw ${rightVisualRight})`);
  assert.ok(rightVisualLeft >= 0, 'visual left must not exceed left boundary');
  assert.ok(rightCollision.scaleX < 1, 'pill must squash horizontally against right wall');
  assert.ok(rightCollision.scaleY > 1, 'pill must bulge vertically against right wall');
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
        motion: { spring: { snappy: { damping: 14, stiffness: 240, mass: 0.8 } } },
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
        motion: { spring: { snappy: { damping: 14, stiffness: 240, mass: 0.8 } } },
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
