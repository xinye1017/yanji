/**
 * Yanji settings screen — a secondary destination reached from the Today header.
 *
 * Scope discipline: only settings the 2.0 product actually needs. Achievements,
 * mascots, AI configuration and backup are not part of this phase's surface.
 */

import React, { useCallback, useEffect, useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { YanjiThemeNative, onThemeChanged } from '../bridge';
import type { ThemeMode, UserSettings } from '../bridge';
import { YanjiCard, YanjiSectionHeader, YanjiPrimaryButton } from '../components/YanjiUI';
import { useNavigation } from '../navigation/NavigationShell';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiRadius, YanjiSpacing } from '../theme/tokens';

const THEME_OPTIONS: Array<{ mode: ThemeMode; label: string }> = [
  { mode: 'SYSTEM', label: '跟随系统' },
  { mode: 'LIGHT', label: '浅色' },
  { mode: 'DARK', label: '深色' },
];

export function SettingsScreen(): React.JSX.Element {
  const theme = useYanjiTheme();
  const { closeSettings } = useNavigation();
  const [settings, setSettings] = useState<UserSettings | null>(null);
  const [mode, setMode] = useState<ThemeMode>(theme.mode);

  const refresh = useCallback(async () => {
    try {
      setSettings(await (await import('../bridge')).YanjiDataNative.getUserSettings());
      const pref = await YanjiThemeNative.getThemePreference();
      setMode(pref.mode);
    } catch {
      // Native bridge unavailable — leave the panel empty rather than faking values.
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    return onThemeChanged(pref => setMode(pref.mode));
  }, []);

  const handleThemeChange = useCallback(async (next: ThemeMode) => {
    setMode(next);
    try {
      await YanjiThemeNative.setThemePreference(next);
    } catch {
      // Revert on failure.
      const pref = await YanjiThemeNative.getThemePreference();
      setMode(pref.mode);
    }
  }, []);

  return (
    <View style={{ flex: 1, backgroundColor: theme.colors.bgPrimary }}>
      <ScrollView contentContainerStyle={{ padding: YanjiSpacing.xl, paddingBottom: 120 }}>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
          <Text style={{ color: theme.colors.textPrimary, fontSize: 24, fontWeight: '700' }}>设置</Text>
          <Pressable onPress={closeSettings} accessibilityRole="button" accessibilityLabel="返回" style={{ padding: 8 }}>
            <Text style={{ color: theme.colors.textSecondary, fontSize: 15 }}>完成</Text>
          </Pressable>
        </View>

        <YanjiSectionHeader title="外观" />
        <YanjiCard>
          <View style={{ flexDirection: 'row' }}>
            {THEME_OPTIONS.map(option => {
              const active = option.mode === mode;
              return (
                <Pressable
                  key={option.mode}
                  onPress={() => void handleThemeChange(option.mode)}
                  accessibilityRole="button"
                  accessibilityState={{ selected: active }}
                  style={{
                    paddingVertical: 8,
                    paddingHorizontal: 14,
                    borderRadius: YanjiRadius.full,
                    marginRight: YanjiSpacing.sm,
                    backgroundColor: active ? theme.colors.accentPrimary : theme.colors.bgElevated,
                  }}
                >
                  <Text
                    style={{
                      color: active ? theme.colors.onAccent : theme.colors.textSecondary,
                      fontSize: 14,
                      fontWeight: active ? '600' : '400',
                    }}
                  >
                    {option.label}
                  </Text>
                </Pressable>
              );
            })}
          </View>
          <Text style={{ color: theme.colors.textTertiary, fontSize: 12, marginTop: YanjiSpacing.md }}>
            研迹只读取系统深浅色，不会修改你的系统设置。
          </Text>
        </YanjiCard>

        <YanjiSectionHeader title="备考" />
        <YanjiCard>
          <Text style={{ color: theme.colors.textSecondary, fontSize: 13 }}>目标考试日期</Text>
          <Text
            style={{
              color: theme.colors.textPrimary,
              fontSize: 17,
              fontWeight: '600',
              marginTop: 4,
            }}
          >
            {settings?.examDate && settings.examDate.length > 0 ? settings.examDate : '未设置'}
          </Text>
        </YanjiCard>

        <View style={{ marginTop: YanjiSpacing.xxl }}>
          <YanjiPrimaryButton label="返回今天" onPress={closeSettings} />
        </View>
      </ScrollView>
    </View>
  );
}
