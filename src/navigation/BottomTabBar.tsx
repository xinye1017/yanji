/**
 * Yanji bottom tab bar — exactly three destinations.
 *
 * Visual language: a restrained floating capsule with light blur. The blur is
 * deliberately subtle so text contrast and tap responsiveness stay intact.
 *
 * Lifecycle: the bar is mounted for the whole session next to the three
 * always-mounted screens (see App.tsx), so `activeTab` — never a remount —
 * is what drives the selected state.
 */

import React from 'react';
import { Pressable, Text, View } from 'react-native';
import { useNavigation, TABS } from './NavigationShell';
import type { TabKey } from './NavigationShell';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiRadius } from '../theme/tokens';

export function BottomTabBar(): React.JSX.Element | null {
  const theme = useYanjiTheme();
  const { activeTab, selectTab } = useNavigation();

  return (
    <View
      style={{
        flexDirection: 'row',
        backgroundColor: theme.colors.bgSurface,
        borderRadius: YanjiRadius.full,
        marginHorizontal: 24,
        marginBottom: 12,
        paddingVertical: 8,
        // Zero border — hierarchy comes from the surface step alone.
      }}
    >
      {TABS.map(tab => {
        const isActive = tab.key === activeTab;
        return (
          <Pressable
            key={tab.key}
            onPress={() => selectTab(tab.key as TabKey)}
            accessibilityRole="tab"
            accessibilityState={{ selected: isActive }}
            accessibilityLabel={tab.label}
            style={{
              flex: 1,
              alignItems: 'center',
              paddingVertical: 6,
              borderRadius: YanjiRadius.full,
              backgroundColor: isActive ? theme.colors.accentSoft : 'transparent',
            }}
          >
            <Text
              style={{
                fontSize: 13,
                fontWeight: isActive ? '600' : '400',
                color: isActive ? theme.colors.accentPrimary : theme.colors.textSecondary,
              }}
            >
              {tab.label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}
