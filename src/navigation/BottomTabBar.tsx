/**
 * Yanji bottom tab bar — exactly three destinations.
 *
 * Visual language: a floating glass lens over the real page, with the selected
 * destination marked by a soft accent pill that slides between slots on a
 * spring. Lifecycle: the bar is mounted for the whole session next to the three
 * always-mounted screens (see App.tsx), so `activeTab` — never a remount — is
 * what drives the selected state.
 */

import React, { useEffect, useState } from 'react';
import { LayoutChangeEvent, Pressable, Text, View } from 'react-native';
import BlurOverlay from 'react-native-blur-overlay';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  useReducedMotion,
  withSpring,
} from 'react-native-reanimated';
import { useNavigation, TABS } from './NavigationShell';
import type { TabKey } from './NavigationShell';
import { YanjiIcon } from '../components/YanjiUI';
import type { YanjiIconName } from '../components/icons';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiRadius, YanjiTouch } from '../theme/tokens';
import { YanjiLiquidGlass } from '../theme/liquidGlass';
import { useBottomTabLayout } from './useBottomTabLayout';

/** Tab glyph, kept here rather than in the shared registry: it is navigation-local. */
const TAB_ICONS: Record<TabKey, YanjiIconName> = {
  today: 'today',
  focus: 'focus',
  review: 'review',
};

/** Shared capsule inset keeps the selection aligned with the measured slots. */
const CAPSULE_PADDING = YanjiLiquidGlass.padding;

export function BottomTabBar(): React.JSX.Element {
  const theme = useYanjiTheme();
  const { height, bottom } = useBottomTabLayout();
  const reduceMotion = useReducedMotion();
  const material = theme.isDark ? YanjiLiquidGlass.dark : YanjiLiquidGlass.light;
  const { activeTab, selectTab } = useNavigation();

  const [slotWidth, setSlotWidth] = useState(0);
  const index = Math.max(0, TABS.findIndex(tab => tab.key === activeTab));

  /**
   * The pill slides by measured pixels rather than by a percentage of its own
   * width: Yoga resolves an absolutely-positioned child's percentage width
   * against the parent's inner box while its `left` inset is measured from the
   * padding edge, so `width: 33.33%` + `translateX: 100%` is not guaranteed to
   * land on a tab slot. Measuring once removes the ambiguity.
   */
  const onRowLayout = (event: LayoutChangeEvent) => {
    const rowWidth = event.nativeEvent.layout.width;
    const next = (rowWidth - CAPSULE_PADDING * 2) / TABS.length;
    if (next > 0 && Math.abs(next - slotWidth) > 0.5) setSlotWidth(next);
  };

  const offset = useSharedValue(index);
  useEffect(() => {
    offset.value = reduceMotion ? index : withSpring(index, theme.motion.spring.snappy);
  }, [index, offset, reduceMotion, theme.motion.spring.snappy]);

  const pillStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: offset.value * slotWidth }],
  }));

  return (
    <View
      testID="bottom-tab-bar"
      style={{
        position: 'absolute',
        left: theme.spacing.lg,
        right: theme.spacing.lg,
        bottom,
        height,
        borderRadius: height / 2,
        ...theme.shadow.floating(theme.isDark),
      }}
    >
      <BlurOverlay
        visible
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
        style={{ borderRadius: height / 2, overflow: 'hidden' }}
      >
        <View
          onLayout={onRowLayout}
          style={{
            flexDirection: 'row',
            width: '100%',
            height,
            borderRadius: YanjiRadius.full,
            padding: CAPSULE_PADDING,
          }}
        >
          {/* Sliding selection pill — behind the labels, one slot wide. */}
          {slotWidth > 0 ? (
            <Animated.View
              pointerEvents="none"
              style={[
                {
                  position: 'absolute',
                  top: CAPSULE_PADDING,
                  bottom: CAPSULE_PADDING,
                  left: CAPSULE_PADDING,
                  width: slotWidth,
                  borderRadius: YanjiRadius.full,
                  backgroundColor: material.selection,
                },
                pillStyle,
              ]}
            />
          ) : null}

          {TABS.map((tab, tabIndex) => {
            const isActive = tabIndex === index;
            const foreground = isActive && !theme.isDark
              ? theme.colors.accentStrong : theme.colors.textPrimary;
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
                  paddingHorizontal: CAPSULE_PADDING,
                  borderRadius: YanjiRadius.full,
                  minHeight: YanjiTouch.min,
                }}
              >
                <YanjiIcon
                  name={TAB_ICONS[tab.key]}
                  size={YanjiLiquidGlass.iconSize}
                  color={foreground}
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
    </View>
  );
}
