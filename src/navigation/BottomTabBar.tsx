/**
 * Yanji bottom tab bar — exactly three destinations.
 *
 * Visual language: a floating sheet lifted clear of the desk, with the selected
 * destination marked by a soft accent pill that slides between slots on a
 * spring. Lifecycle: the bar is mounted for the whole session next to the three
 * always-mounted screens (see App.tsx), so `activeTab` — never a remount — is
 * what drives the selected state.
 */

import React, { useEffect, useState } from 'react';
import { LayoutChangeEvent, Pressable, Text, View } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withSpring,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useNavigation, TABS } from './NavigationShell';
import type { TabKey } from './NavigationShell';
import { YanjiIcon } from '../components/YanjiUI';
import type { YanjiIconName } from '../components/icons';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiRadius, YanjiTouch } from '../theme/tokens';

/** Tab glyph, kept here rather than in the shared registry: it is navigation-local. */
const TAB_ICONS: Record<TabKey, YanjiIconName> = {
  today: 'today',
  focus: 'focus',
  review: 'review',
};

/** Inner padding of the capsule, matching the `padding: 5` on the row below. */
const CAPSULE_PADDING = 5;

export function BottomTabBar(): React.JSX.Element {
  const theme = useYanjiTheme();
  const insets = useSafeAreaInsets();
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
    offset.value = withSpring(index, theme.motion.spring.snappy);
  }, [index, offset, theme.motion.spring.snappy]);

  const pillStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: offset.value * slotWidth }],
  }));

  return (
    <View
      testID="bottom-tab-bar"
      style={{
        paddingHorizontal: theme.spacing.lg,
        paddingBottom: Math.max(insets.bottom, theme.spacing.md),
        paddingTop: theme.spacing.sm,
        backgroundColor: theme.colors.bgPrimary,
      }}
    >
      <View
        onLayout={onRowLayout}
        style={[
          {
            flexDirection: 'row',
            backgroundColor: theme.colors.bgFloating,
            borderRadius: YanjiRadius.full,
            padding: CAPSULE_PADDING,
          },
          theme.shadow.floating(theme.isDark),
        ]}
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
                backgroundColor: theme.colors.accentSoft,
              },
              pillStyle,
            ]}
          />
        ) : null}

        {TABS.map((tab, tabIndex) => {
          const isActive = tabIndex === index;
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
                flexDirection: 'row',
                alignItems: 'center',
                justifyContent: 'center',
                paddingVertical: 10,
                paddingHorizontal: 12,
                borderRadius: YanjiRadius.full,
                minHeight: YanjiTouch.min,
              }}
            >
              <YanjiIcon
                name={TAB_ICONS[tab.key]}
                size={17}
                color={isActive ? theme.colors.accentPrimary : theme.colors.textSecondary}
              />
              <Text
                style={{
                  fontSize: 14,
                  marginLeft: 6,
                  fontWeight: isActive ? '700' : '500',
                  color: isActive ? theme.colors.accentPrimary : theme.colors.textSecondary,
                  letterSpacing: 0.2,
                }}
              >
                {tab.label}
              </Text>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}
