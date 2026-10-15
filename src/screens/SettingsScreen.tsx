/**
 * Yanji settings screen — a secondary destination reached from the Today header.
 *
 * Scope discipline: only settings the 2.0 product actually needs. Achievements,
 * mascots, AI configuration, backup files, fluid-cloud switches and notification
 * toggles are not part of this phase's surface, so none of them are offered —
 * an inert switch would be a lie about what the app can do.
 *
 * Every value shown here is read from the native snapshot (`UserSettings`), and
 * the one write this screen performs (the theme preference) goes straight to the
 * native module. The daily goal is displayed from the same Room row that Today
 * edits, so there is exactly one writer for it.
 */

import React, { useCallback, useEffect, useState } from 'react';
import { ScrollView, Text, View } from 'react-native';
import { YanjiThemeNative, onThemeChanged } from '../bridge';
import type { ThemeMode } from '../bridge';
import {
  YanjiEditorialHeader,
  YanjiIconButton,
  YanjiPaperSectionHeading,
  YanjiPaperSegmentedControl,
  YanjiPrimaryButton,
} from '../components/YanjiUI';
import { useNavigationActions } from '../navigation/NavigationShell';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing } from '../theme/tokens';
import { useSettingsSnapshot } from '../state/dataRefresh';

const THEME_OPTIONS: Array<{ mode: ThemeMode; label: string }> = [
  { mode: 'SYSTEM', label: '跟随系统' },
  { mode: 'LIGHT', label: '浅色' },
  { mode: 'DARK', label: '深色' },
];

function formatGoalHours(hours: number | undefined): string {
  if (hours === undefined || !Number.isFinite(hours) || hours <= 0) return '未设置';
  const whole = Math.floor(hours);
  const minutes = Math.round((hours - whole) * 60);
  if (whole === 0) return `${minutes} 分钟`;
  return minutes === 0 ? `${whole} 小时` : `${whole} 小时 ${minutes} 分钟`;
}

export function SettingsScreen(): React.JSX.Element {
  const theme = useYanjiTheme();
  const paper = theme.paper;
  const { closeSettings } = useNavigationActions();
  const settingsSnapshot = useSettingsSnapshot('settings', true);
  const settings = settingsSnapshot?.settings ?? null;
  const [mode, setMode] = useState<ThemeMode>(theme.mode);

  useEffect(() => {
    YanjiThemeNative.getThemePreference()
      .then(pref => setMode(pref.mode))
      .catch(() => {
        // Keep the current theme selection if the bridge read fails.
      });
  }, []);

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
    <View style={{ flex: 1, backgroundColor: paper.colors.canvas }}>
      <ScrollView
        contentContainerStyle={{
          paddingHorizontal: paper.layout.pageGutter,
          paddingTop: YanjiSpacing.md,
          paddingBottom: 120,
        }}
      >
        <YanjiEditorialHeader
          title="设置"
          subtitle="只保留当前真正可用的选项"
          trailing={
            <YanjiIconButton
              icon="close"
              accessibilityLabel="完成"
              onPress={closeSettings}
              tone="filled"
            />
          }
        />

        <YanjiPaperSectionHeading title="外观" />
        <YanjiPaperSegmentedControl
          accessibilityLabel="主题外观"
          value={mode}
          options={THEME_OPTIONS.map(option => ({
            value: option.mode,
            label: option.label,
          }))}
          onChange={next => void handleThemeChange(next)}
        />
        <Text
          style={[
            paper.typography.captionMeta,
            { color: paper.colors.secondary, marginTop: YanjiSpacing.md },
          ]}
        >
          研迹只读取系统深浅色，不会修改你的系统设置。
        </Text>

        <YanjiPaperSectionHeading title="备考" />
        <View
          style={{
            borderTopWidth: paper.layout.hairlineWidth,
            borderBottomWidth: paper.layout.hairlineWidth,
            borderColor: paper.colors.hairline,
          }}
        >
          <View
            style={{
              flexDirection: 'row',
              alignItems: 'center',
              justifyContent: 'space-between',
              minHeight: paper.layout.rowMinHeight,
              paddingVertical: YanjiSpacing.sm,
            }}
          >
            <Text style={[paper.typography.bodyPrimary, { color: paper.colors.secondary }]}>
              目标考试日期
            </Text>
            <Text
              style={[
                paper.typography.bodyPrimary,
                { color: paper.colors.ink, fontWeight: '600' },
              ]}
            >
              {settings?.examDate && settings.examDate.length > 0 ? settings.examDate : '未设置'}
            </Text>
          </View>
          <View
            style={{
              height: paper.layout.hairlineWidth,
              backgroundColor: paper.colors.hairline,
            }}
          />
          <View
            style={{
              flexDirection: 'row',
              alignItems: 'center',
              justifyContent: 'space-between',
              minHeight: paper.layout.rowMinHeight,
              paddingVertical: YanjiSpacing.sm,
            }}
          >
            <Text style={[paper.typography.bodyPrimary, { color: paper.colors.secondary }]}>
              默认每日时长
            </Text>
            <Text
              style={[
                paper.typography.bodyPrimary,
                { color: paper.colors.ink, fontWeight: '600' },
              ]}
            >
              {formatGoalHours(settings?.dailyGoalHours)}
            </Text>
          </View>
        </View>
        <Text
          style={[
            paper.typography.captionMeta,
            { color: paper.colors.secondary, marginTop: YanjiSpacing.md },
          ]}
        >
          考试日期与每日时长在「今天」页调整，这里显示的是同一份设置。
        </Text>

        <View style={{ marginTop: YanjiSpacing.xxl }}>
          <YanjiPrimaryButton label="返回今天" icon="today" onPress={closeSettings} />
        </View>
      </ScrollView>
    </View>
  );
}
