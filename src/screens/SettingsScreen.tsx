/**
 * Yanji settings — a secondary destination reached from the Today header.
 *
 * Shape of the page: three short groups, one line per setting, no cards.
 *  1. 备考 — the two values the product actually measures against (考试日期 /
 *     每日学习时长). Both are edited here, in the sheet that owns them.
 *  2. 外观 — the app theme preference.
 *  3. 高级 — the decision-model configuration, collapsed by default. It is a
 *     long form that is configured once and then never touched, so letting it
 *     occupy the first screen was the single biggest source of clutter.
 *
 * Everything printed here comes from the native snapshot (`UserSettings`), and
 * the only writes are `updateUserSettings` (date, goal, decision model) and the
 * theme module. Today still reads the same Room row, so the two pages can never
 * disagree — but there is exactly one editor per value, on this page.
 */

import React, { useCallback, useEffect, useState } from 'react';
import { ScrollView, Text, View } from 'react-native';
import { YanjiDataNative, YanjiThemeNative, onThemeChanged } from '../bridge';
import type { ThemeMode } from '../bridge';
import {
  YanjiEditorialHeader,
  YanjiHairline,
  YanjiIconButton,
  YanjiPaperSectionHeading,
  YanjiPaperSegmentedControl,
  YanjiTextAction,
} from '../components/YanjiUI';
import { YanjiSettingRow } from '../components/SettingsRows';
import { YanjiDecisionModelSection } from '../components/DecisionModelSection';
import { DailyGoalSheet, ExamDateSheet } from '../components/SettingsSheets';
import { useNavigationActions } from '../navigation/NavigationShell';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing } from '../theme/tokens';
import { refreshSettingsSnapshot, useSettingsSnapshot } from '../state/dataRefresh';
import {
  examCountdownText,
  formatExamDate,
  formatGoalDuration,
  goalHoursFromMinutes,
  todayIso,
} from '../state/settingsViewLogic';

const THEME_OPTIONS: Array<{ mode: ThemeMode; label: string }> = [
  { mode: 'SYSTEM', label: '跟随系统' },
  { mode: 'LIGHT', label: '浅色' },
  { mode: 'DARK', label: '深色' },
];

type Sheet = 'examDate' | 'dailyGoal' | null;

export function SettingsScreen(): React.JSX.Element {
  const theme = useYanjiTheme();
  const paper = theme.paper;
  const { colors, typography, layout } = paper;
  const { closeSettings } = useNavigationActions();
  const settingsSnapshot = useSettingsSnapshot('settings', true);
  const settings = settingsSnapshot?.settings ?? null;

  const [mode, setMode] = useState<ThemeMode>(theme.mode);
  const [openSheet, setOpenSheet] = useState<Sheet>(null);
  const [saving, setSaving] = useState(false);
  const [sheetError, setSheetError] = useState<string | null>(null);
  /**
   * A failed settings read used to leave the page permanently on 「—」: every
   * row disables itself while `settings` is null, and nothing retried. Because
   * this page is now the *only* writer for the two 备考 values, that state has
   * to be recoverable, so the failure is recorded and the page offers a retry.
   */
  const [readFailed, setReadFailed] = useState(false);
  const [retrying, setRetrying] = useState(false);

  useEffect(() => {
    YanjiThemeNative.getThemePreference()
      .then(pref => setMode(pref.mode))
      .catch(() => {
        // Keep the current theme selection if the bridge read fails.
      });
  }, []);

  useEffect(() => onThemeChanged(pref => setMode(pref.mode)), []);

  const handleThemeChange = useCallback(async (next: ThemeMode) => {
    setMode(next);
    try {
      await YanjiThemeNative.setThemePreference(next);
    } catch {
      const pref = await YanjiThemeNative.getThemePreference();
      setMode(pref.mode);
    }
  }, []);

  const openSheetWith = (sheet: Exclude<Sheet, null>) => {
    setSheetError(null);
    setOpenSheet(sheet);
  };

  const retryRead = async () => {
    setRetrying(true);
    setReadFailed(false);
    try {
      await refreshSettingsSnapshot();
    } catch {
      setReadFailed(true);
    } finally {
      setRetrying(false);
    }
  };

  const closeSheet = () => {
    if (saving) return;
    setSheetError(null);
    setOpenSheet(null);
  };

  const saveExamDate = async (iso: string) => {
    setSaving(true);
    setSheetError(null);
    setReadFailed(false);
    try {
      if (!await YanjiDataNative.updateUserSettings({ examDate: iso })) throw new Error('refused');
    } catch {
      setSheetError('考试日期未保存，请稍后再试。');
      setSaving(false);
      return;
    }
    setSaving(false);
    setOpenSheet(null);
    // The write already landed. A failed re-read must not be reported as a
    // failed save — it only means the row still shows the previous value, which
    // the banner and 重新读取 are there to fix.
    try {
      await refreshSettingsSnapshot();
    } catch {
      setReadFailed(true);
    }
  };

  const saveDailyGoal = async (minutes: number) => {
    setSaving(true);
    setSheetError(null);
    setReadFailed(false);
    try {
      if (!await YanjiDataNative.updateUserSettings({ dailyGoalHours: goalHoursFromMinutes(minutes) })) {
        throw new Error('refused');
      }
    } catch {
      setSheetError('每日学习时长未保存，请稍后再试。');
      setSaving(false);
      return;
    }
    setSaving(false);
    setOpenSheet(null);
    // See saveExamDate: the stored value changed either way.
    try {
      await refreshSettingsSnapshot();
    } catch {
      setReadFailed(true);
    }
  };

  const examDate = settings?.examDate ?? '';
  const countdownText = examDate ? examCountdownText(examDate, todayIso()) : null;
  const goalHours = settings?.dailyGoalHours;
  // Until the settings snapshot arrives, the page must not claim the values are
  // unset: it simply has not read them yet.
  const loading = settings === null;

  return (
    <View style={{ flex: 1, backgroundColor: colors.canvas }}>
      <ScrollView
        testID="scroll-settings"
        showsVerticalScrollIndicator={false}
        overScrollMode="never"
        contentContainerStyle={{
          paddingHorizontal: layout.pageGutter,
          paddingTop: YanjiSpacing.lg,
          paddingBottom: YanjiSpacing.xxl + YanjiSpacing.lg,
        }}
      >
        <YanjiEditorialHeader
          title="设置"
          subtitle="考试日期与每日时长决定「今天」的进度刻度"
          trailing={
            <YanjiIconButton
              icon="close"
              accessibilityLabel="完成"
              onPress={closeSettings}
              tone="filled"
              size={layout.rowMinHeight}
            />
          }
        />

        {readFailed ? (
          <View style={{ marginTop: YanjiSpacing.md }}>
            <Text
              accessibilityRole="alert"
              style={[typography.bodyPrimary, { color: colors.danger }]}
            >
              设置读取失败，考试日期与每日时长暂时无法修改。
            </Text>
            <YanjiTextAction
              label={retrying ? '读取中…' : '重新读取'}
              onPress={() => void retryRead()}
              disabled={retrying}
            />
          </View>
        ) : null}

        <View style={{ marginTop: YanjiSpacing.lg }}>
          <YanjiPaperSectionHeading title="备考" />
          <YanjiSettingRow
            label="考试日期"
            value={examDate ? formatExamDate(examDate) : '未设置'}
            hint={countdownText ?? '用于首页倒计时与回顾窗口'}
            accessibilityLabel={`考试日期，${examDate ? formatExamDate(examDate) : '未设置'}${countdownText ? `，${countdownText}` : ''}，点击修改`}
            pending={loading}
            onPress={() => openSheetWith('examDate')}
          />
          <YanjiSettingRow
            label="每日学习时长"
            value={formatGoalDuration(goalHours)}
            hint="作为每日目标，不改变已记录的学习时长"
            accessibilityLabel={`每日学习时长，${formatGoalDuration(goalHours)}，点击修改`}
            isLast
            pending={loading}
            onPress={() => openSheetWith('dailyGoal')}
          />
        </View>

        <View style={{ marginTop: layout.sectionGap }}>
          <YanjiPaperSectionHeading title="外观" />
          <YanjiPaperSegmentedControl
            accessibilityLabel="主题外观"
            value={mode}
            options={THEME_OPTIONS.map(option => ({ value: option.mode, label: option.label }))}
            onChange={next => void handleThemeChange(next)}
          />
          <Text style={[typography.captionMeta, { color: colors.secondary, marginTop: YanjiSpacing.sm }]}>
            只读取系统深浅色，不会修改你的系统设置。
          </Text>
        </View>

        <View style={{ marginTop: layout.sectionGap }}>
          <YanjiPaperSectionHeading title="高级" />
          <YanjiDecisionModelSection settings={settings} onSaved={refreshSettingsSnapshot} />
        </View>

        <YanjiHairline style={{ marginTop: layout.sectionGap }} />
        <Text
          style={[typography.captionMeta, { color: colors.secondary, marginTop: YanjiSpacing.md, textAlign: 'center' }]}
        >
          研迹 2.0 · 数据只保存在这台设备上
        </Text>
      </ScrollView>

      <ExamDateSheet
        visible={openSheet === 'examDate'}
        initialDate={examDate}
        saving={saving}
        error={sheetError}
        onClose={closeSheet}
        onSave={iso => void saveExamDate(iso)}
      />
      <DailyGoalSheet
        visible={openSheet === 'dailyGoal'}
        initialHours={goalHours}
        saving={saving}
        error={sheetError}
        onClose={closeSheet}
        onSave={minutes => void saveDailyGoal(minutes)}
      />
    </View>
  );
}
