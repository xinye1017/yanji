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
import { Platform, Pressable, Text, View } from 'react-native';
import { useNavigation, TABS } from './NavigationShell';
import type { TabKey } from './NavigationShell';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiRadius } from '../theme/tokens';

const TAB_ICONS: Record<string, string> = {
  today: '☀️',
  focus: '⏱️',
  review: '📊',
};

export function BottomTabBar(): React.JSX.Element | null {
  const theme = useYanjiTheme();
  const { activeTab, selectTab } = useNavigation();

  return (
    <View
      style={{
        flexDirection: 'row',
        backgroundColor: theme.colors.bgSurface,
        borderRadius: YanjiRadius.full,
        marginHorizontal: 28,
        marginBottom: Platform.OS === 'android' ? 16 : 28,
        padding: 5,
        elevation: 4,
        // Zero border — strictly zero border per design system.
      }}
    >
      {TABS.map(tab => {
        const isActive = tab.key === activeTab;
        const icon = TAB_ICONS[tab.key];
        return (
          <Pressable
            key={tab.key}
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
              backgroundColor: isActive ? theme.colors.accentSoft : 'transparent',
            }}
          >
            {icon ? (
              <Text
                style={{
                  fontSize: 14,
                  marginRight: 6,
                  opacity: isActive ? 1 : 0.7,
                }}
              >
                {icon}
              </Text>
            ) : null}
            <Text
              style={{
                fontSize: 14,
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
  );
}
