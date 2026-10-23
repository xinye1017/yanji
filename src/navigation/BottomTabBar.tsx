/**
 * Yanji bottom tab bar — exactly three destinations.
 *
 * Visual language: a floating glass lens over the real page, with the selected
 * destination marked by its solid Heroicon and stronger label. Lifecycle: the
 * bar is mounted for the whole session next to the three always-mounted screens
 * (see App.tsx), so `activeTab` — never a remount — drives the selected state.
 */

import React, { useEffect, useMemo, useRef, useState } from 'react';
import { LayoutChangeEvent, PanResponder, Pressable, StyleSheet, Text, View } from 'react-native';
import BlurOverlay from 'react-native-blur-overlay';
import Svg, { Defs, LinearGradient, Rect, Stop } from 'react-native-svg';
import { useReducedMotion } from 'react-native-reanimated';
import { useNavigationActions, useNavigationState, TABS } from './NavigationShell';
import type { TabKey } from './NavigationShell';
import { YanjiIcon } from '../components/YanjiUI';
import type { YanjiIconName } from '../components/icons';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiRadius, YanjiSpacing, YanjiTouch } from '../theme/tokens';
import { YanjiLiquidGlass } from '../theme/liquidGlass';
import { useBottomTabLayout } from './useBottomTabLayout';

/** Tab glyph, kept here rather than in the shared registry: it is navigation-local. */
const TAB_ICONS: Record<TabKey, YanjiIconName> = {
  today: 'today',
  focus: 'focus',
  review: 'review',
};

/** Inset keeps glyphs clear of the capsule rim; slots span the padded row. */
const CAPSULE_PADDING = YanjiSpacing.xs;

/**
 * Maps the finger's horizontal coordinate within the row to a valid tab slot
 * index, or `null` outside the row so an off-dock release changes nothing.
 */
export function calculateTargetSlotIndex(
  rowX: number,
  rowWidth: number,
  slotWidth: number,
  tabsCount: number,
): number | null {
  if (rowWidth <= 0 || slotWidth <= 0 || tabsCount <= 0) return null;
  if (rowX < 0 || rowX >= rowWidth) return null;
  const slot = Math.floor(rowX / slotWidth);
  return slot >= 0 && slot < tabsCount ? slot : null;
}

export function BottomTabBar({ visible = true }: { visible?: boolean }): React.JSX.Element {
  const theme = useYanjiTheme();
  const { paper } = theme;
  const { height, bottom } = useBottomTabLayout();
  const reduceMotion = useReducedMotion();
  const material = theme.isDark ? YanjiLiquidGlass.dark : YanjiLiquidGlass.light;
  const { activeTab } = useNavigationState();
  const { selectTab } = useNavigationActions();

  const index = Math.max(0, TABS.findIndex(tab => tab.key === activeTab));

  const [dockWidth, setDockWidth] = useState(0);
  const isDraggingRef = useRef(false);
  const rowRef = useRef<React.ElementRef<typeof View>>(null);
  const rowPageXRef = useRef<number>(theme.spacing.lg);
  const slotWidthRef = useRef(0);
  const activeSlotRef = useRef(index);
  const selectTabRef = useRef(selectTab);

  useEffect(() => {
    activeSlotRef.current = index;
  }, [index]);

  useEffect(() => {
    selectTabRef.current = selectTab;
  }, [selectTab]);

  const onRowLayout = (event: LayoutChangeEvent) => {
    const rowWidth = event.nativeEvent.layout.width;
    const next = (rowWidth - 2 * CAPSULE_PADDING) / TABS.length;
    if (next > 0) {
      slotWidthRef.current = next;
    }
    rowRef.current?.measure((_x: number, _y: number, _w: number, _h: number, pageX: number) => {
      if (typeof pageX === 'number' && !isNaN(pageX)) {
        rowPageXRef.current = pageX;
      }
    });
  };

  const panResponder = useMemo(() => {
    const getRowX = (screenX: number) => (
      screenX - rowPageXRef.current - CAPSULE_PADDING
    );
    const getSlotAreaWidth = () => slotWidthRef.current * TABS.length;

    return PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onPanResponderGrant: () => {
        isDraggingRef.current = false;
      },
      onPanResponderMove: (_evt, gestureState) => {
        const sw = slotWidthRef.current;
        if (sw <= 0) return;

        if (Math.hypot(gestureState.dx, gestureState.dy) > 4) {
          isDraggingRef.current = true;
        }
      },
      onPanResponderRelease: (_evt, gestureState) => {
        const sw = slotWidthRef.current;
        const wasDragging = isDraggingRef.current;
        isDraggingRef.current = false;

        if (sw <= 0) return;

        const finalX = gestureState.moveX || (gestureState.x0 + gestureState.dx);
        const finalSlot = wasDragging
          ? calculateTargetSlotIndex(getRowX(finalX), getSlotAreaWidth(), sw, TABS.length)
          : calculateTargetSlotIndex(getRowX(gestureState.x0), getSlotAreaWidth(), sw, TABS.length);
        if (finalSlot === null) return;

        activeSlotRef.current = finalSlot;
        selectTabRef.current(TABS[finalSlot].key);
      },
      onPanResponderTerminate: () => {
        isDraggingRef.current = false;
      },
    });
  }, []);

  const contentHeight = height - 2 * paper.layout.hairlineWidth;

  return (
    <View
      testID="bottom-tab-bar"
      onLayout={event => setDockWidth(event.nativeEvent.layout.width)}
      pointerEvents={visible ? 'auto' : 'none'}
      accessibilityElementsHidden={!visible}
      importantForAccessibility={visible ? 'auto' : 'no-hide-descendants'}
      style={[{
        position: 'absolute',
        left: theme.spacing.lg,
        right: theme.spacing.lg,
        bottom,
        height,
        borderRadius: height / 2,
        backgroundColor: material.backdrop,
        borderWidth: paper.layout.hairlineWidth,
        borderColor: material.border,
        justifyContent: 'center',
        ...theme.shadow.floating(theme.isDark),
      }, { opacity: visible ? 1 : 0 }]}
    >
      <BlurOverlay
        visible={visible}
        blurMode="glass"
        blurTargetId={YanjiLiquidGlass.targetId}
        glassVariant="clear"
        glassTint={material.tint}
        blurRadius={YanjiLiquidGlass.blurRadius}
        downsampling={YanjiLiquidGlass.downsampling}
        maxUpdateFps={YanjiLiquidGlass.maxUpdateFps}
        saturation={YanjiLiquidGlass.saturation}
        interactive={!reduceMotion}
        fadeDuration={0}
        style={[{ ...StyleSheet.absoluteFill, borderRadius: height / 2, overflow: 'hidden' }]}
      >
        <View
          ref={rowRef}
          onLayout={onRowLayout}
          {...panResponder.panHandlers}
          style={{
            flexDirection: 'row',
            width: '100%',
            height: contentHeight,
            borderRadius: YanjiRadius.full,
            padding: CAPSULE_PADDING,
            overflow: 'hidden',
          }}
        >
          {TABS.map((tab, tabIndex) => {
            const isActive = tabIndex === index;
            const foreground = isActive && !theme.isDark
              ? paper.colors.brandInk
              : (isActive ? paper.colors.brand : paper.colors.ink);
            return (
              <Pressable
                key={tab.key}
                testID={`tab-${tab.key}`}
                onPress={() => selectTab(tab.key as TabKey)}
                accessibilityRole="tab"
                accessibilityState={{ selected: isActive }}
                accessibilityLabel={tab.label}
                style={{
                  flex: 1,
                  flexDirection: 'column',
                  alignItems: 'center',
                  justifyContent: 'center',
                  paddingVertical: YanjiLiquidGlass.itemPadding,
                  borderRadius: YanjiRadius.full,
                  minHeight: YanjiTouch.min,
                }}
              >
                <YanjiIcon
                  name={TAB_ICONS[tab.key]}
                  size={YanjiLiquidGlass.iconSize}
                  color={foreground}
                  filled={isActive}
                />
                <Text
                  style={{
                    fontSize: YanjiLiquidGlass.labelSize,
                    lineHeight: YanjiLiquidGlass.labelLineHeight,
                    marginTop: YanjiLiquidGlass.labelGap,
                    fontWeight: isActive ? '700' : '500',
                    color: foreground,
                    letterSpacing: 0.2,
                  }}
                >
                  {tab.label}
                </Text>
              </Pressable>
            );
          })}
        </View>
      </BlurOverlay>

      {/* The specular rim: the layer that separates glass from frosted plastic.
          A gradient stroke tracing the capsule's own edge, bright at the
          upper-left where the light is and fading to a trace around the rest.
          Drawn above the blur and below the glyphs, so it lights the material
          and never the icons; non-interactive, so it cannot swallow a tab
          press. A real glass surface catches light exactly here, which is what
          blur and refraction on their own cannot fake. */}
      {dockWidth > 0 ? (
        <Svg
          pointerEvents="none"
          style={StyleSheet.absoluteFill}
          width={dockWidth}
          height={height}
          accessibilityElementsHidden
          importantForAccessibility="no-hide-descendants"
        >
          <Defs>
            <LinearGradient id="yanji-dock-rim" x1="0" y1="0" x2="1" y2="1">
              <Stop offset="0" stopColor={YanjiLiquidGlass.rim.highlight} />
              <Stop offset="0.5" stopColor={YanjiLiquidGlass.rim.shade} />
              <Stop offset="1" stopColor={YanjiLiquidGlass.rim.shade} />
            </LinearGradient>
          </Defs>
          <Rect
            x={YanjiLiquidGlass.rim.width / 2}
            y={YanjiLiquidGlass.rim.width / 2}
            width={dockWidth - YanjiLiquidGlass.rim.width}
            height={height - YanjiLiquidGlass.rim.width}
            rx={(height - YanjiLiquidGlass.rim.width) / 2}
            ry={(height - YanjiLiquidGlass.rim.width) / 2}
            fill="none"
            stroke="url(#yanji-dock-rim)"
            strokeWidth={YanjiLiquidGlass.rim.width}
          />
        </Svg>
      ) : null}
    </View>
  );
}
