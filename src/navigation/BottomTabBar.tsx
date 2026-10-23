/**
 * Yanji bottom tab bar — exactly three destinations.
 *
 * Visual language: a floating glass lens over the real page, with the selected
 * destination marked by its solid Heroicon and stronger label. Lifecycle: the
 * bar is mounted for the whole session next to the retained screen panes
 * (see App.tsx), so `activeTab` — never a remount — drives the selected state.
 */

import React, { useEffect, useMemo, useRef, useState } from 'react';
import { PanResponder, Pressable, StyleSheet, Text, View } from 'react-native';
import BlurOverlay from 'react-native-blur-overlay';
import { useNavigationActions, useNavigationState, TABS } from './NavigationShell';
import type { TabKey } from './NavigationShell';
import { YanjiIcon } from '../components/YanjiUI';
import type { YanjiIconName } from '../components/icons';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiRadius, YanjiTouch } from '../theme/tokens';
import { YanjiLiquidGlass } from '../theme/liquidGlass';
import { useBottomTabLayout } from './useBottomTabLayout';
import { useNavigationAccessibility } from './useNavigationAccessibility';

/** Tab glyph, kept here rather than in the shared registry: it is navigation-local. */
const TAB_ICONS: Record<TabKey, YanjiIconName> = {
  today: 'today',
  focus: 'focus',
  review: 'review',
};

/** Inset keeps glyphs clear of the capsule rim; slots span the padded row. */
const CAPSULE_PADDING = YanjiLiquidGlass.padding;

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
  const { reduceMotion, highContrast } = useNavigationAccessibility();
  const material = theme.isDark ? YanjiLiquidGlass.dark : YanjiLiquidGlass.light;
  const { activeTab } = useNavigationState();
  const { selectTab } = useNavigationActions();

  const index = Math.max(0, TABS.findIndex(tab => tab.key === activeTab));

  const [previewIndex, setPreviewIndex] = useState<number | null>(null);
  const rowRef = useRef<React.ElementRef<typeof View>>(null);
  const rowBoundsRef = useRef({ x: 0, y: 0, width: 0, height: 0 });
  const selectTabRef = useRef(selectTab);
  const visibleRef = useRef(visible);
  visibleRef.current = visible;

  useEffect(() => {
    selectTabRef.current = selectTab;
  }, [selectTab]);

  useEffect(() => {
    setPreviewIndex(null);
  }, [visible, index]);

  const onRowLayout = () => {
    rowRef.current?.measure((_x, _y, width, rowHeight, pageX, pageY) => {
      if ([width, rowHeight, pageX, pageY].every(Number.isFinite)) {
        rowBoundsRef.current = { x: pageX, y: pageY, width, height: rowHeight };
      }
    });
  };

  const panResponder = useMemo(() => {
    const targetSlot = (screenX: number, screenY: number) => {
      const bounds = rowBoundsRef.current;
      if (!visibleRef.current || screenY < bounds.y || screenY >= bounds.y + bounds.height) {
        return null;
      }
      const width = bounds.width - 2 * CAPSULE_PADDING;
      return calculateTargetSlotIndex(
        screenX - bounds.x - CAPSULE_PADDING, width, width / TABS.length, TABS.length,
      );
    };

    return PanResponder.create({
      // Pressable owns taps and accessibility activation. Only an intentional
      // horizontal drag cancels its press and transfers ownership to the row.
      onMoveShouldSetPanResponderCapture: (_evt, { dx, dy }) => visibleRef.current
        && Math.abs(dx) > YanjiLiquidGlass.dragThreshold && Math.abs(dx) > Math.abs(dy),
      onPanResponderGrant: (_evt, gestureState) => {
        setPreviewIndex(targetSlot(gestureState.moveX, gestureState.moveY));
      },
      onPanResponderMove: (_evt, gestureState) => {
        setPreviewIndex(targetSlot(gestureState.moveX, gestureState.moveY));
      },
      onPanResponderRelease: (_evt, gestureState) => {
        const finalSlot = targetSlot(gestureState.moveX, gestureState.moveY);
        setPreviewIndex(null);
        if (finalSlot === null) return;
        selectTabRef.current(TABS[finalSlot].key);
      },
      onPanResponderTerminate: () => {
        setPreviewIndex(null);
      },
    });
  }, []);

  return (
    <View
      testID="bottom-tab-bar"
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
        justifyContent: 'center',
        ...theme.shadow.floating(theme.isDark),
      }, { opacity: visible ? 1 : 0 }]}
    >
      <BlurOverlay
        visible={visible}
        blurMode="glass"
        blurTargetId={YanjiLiquidGlass.targetId}
        // Navigation needs the regular material in both themes. Our native
        // patch derives white lift and reflection from the tint's brightness.
        glassVariant="regular"
        glassTint={highContrast ? paper.colors.elevated : material.tint}
        blurRadius={YanjiLiquidGlass.blurRadius}
        downsampling={YanjiLiquidGlass.downsampling}
        maxUpdateFps={YanjiLiquidGlass.maxUpdateFps}
        saturation={highContrast ? 1 : YanjiLiquidGlass.saturation}
        interactive={!reduceMotion && !highContrast}
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
            height,
            borderRadius: YanjiRadius.full,
            padding: CAPSULE_PADDING,
            overflow: 'hidden',
          }}
        >
          {TABS.map((tab, tabIndex) => {
            const isActive = tabIndex === index;
            const isPreview = previewIndex === tabIndex;
            const foreground = isActive ? paper.colors.brandInk
              : (isPreview || highContrast ? paper.colors.ink : paper.colors.secondary);
            return (
              <Pressable
                key={tab.key}
                testID={`tab-${tab.key}`}
                onPress={() => selectTab(tab.key as TabKey)}
                accessibilityRole="tab"
                accessibilityState={{ selected: isActive }}
                accessibilityLabel={tab.label}
                style={({ pressed }) => ({
                  flex: 1,
                  flexDirection: 'column',
                  alignItems: 'center',
                  justifyContent: 'center',
                  paddingVertical: YanjiLiquidGlass.itemPadding,
                  borderRadius: YanjiRadius.full,
                  minHeight: YanjiTouch.comfortable,
                  opacity: pressed ? YanjiLiquidGlass.pressedOpacity : 1,
                })}
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
                    fontWeight: isActive ? '700' : (isPreview ? '600' : '500'),
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
    </View>
  );
}
