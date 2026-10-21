/**
 * Yanji settings — a secondary destination reached from the Today header.
 *
 * Shape of the page: five short groups, one line per setting, no cards.
 *  1. 备考 — the two values the product actually measures against (考试日期 /
 *     每日学习时长). Both are edited here, in the sheet that owns them.
 *  2. 学科 — the names used by focus sessions.
 *  3. 外观 — the app theme preference.
 *  4. 高级 — the decision-model configuration, collapsed by default. It is a
 *     long form that is configured once and then never touched, so letting it
 *     occupy the first screen was the single biggest source of clutter.
 *  5. 数据管理 — manual backup export and a validated restore preview.
 *
 * Everything printed here comes from the native snapshot (`UserSettings`), and
 * regular setting writes use `updateUserSettings` (date, goal, decision model)
 * and the theme module. BackupDataSection owns the separate restore workflow.
 * Today still reads the same Room row, so the two pages can never
 * disagree — but there is exactly one editor per value, on this page.
 */

import React, { useCallback, useEffect, useRef, useState } from 'react';
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
  useYanjiKeyboardInset,
} from '../components/YanjiUI';
import { YanjiSettingRow } from '../components/SettingsRows';
import { YanjiDecisionModelSection } from '../components/DecisionModelSection';
import { BackupDataSection } from '../components/BackupDataSection';
import { DailyGoalSheet, ExamDateSheet } from '../components/SettingsSheets';
import { SubjectsSheet } from '../components/SubjectsSheet';
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

type Sheet = 'examDate' | 'dailyGoal' | 'subjects' | null;

export function SettingsScreen(): React.JSX.Element {
  const theme = useYanjiTheme();
  const paper = theme.paper;
  const { colors, typography, layout } = paper;
  const { closeSettings } = useNavigationActions();
  const settingsSnapshot = useSettingsSnapshot('settings', true);
  const settings = settingsSnapshot?.settings ?? null;
  // The decision-model form is the only editor on this page that opens the
  // keyboard. Reserving its real height keeps the fields and the save row
  // reachable instead of leaving them underneath the IME.
  const keyboardHeight = useYanjiKeyboardInset();

  const [mode, setMode] = useState<ThemeMode>(theme.mode);
  const [openSheet, setOpenSheet] = useState<Sheet>(null);
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const themeSavingRef = useRef(false);
  const [sheetError, setSheetError] = useState<string | null>(null);
  const [themeSaving, setThemeSaving] = useState(false);
  const [themeError, setThemeError] = useState<string | null>(null);
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
    if (themeSavingRef.current) return;
    themeSavingRef.current = true;
    setThemeSaving(true);
    setThemeError(null);
    try {
      if (!await YanjiThemeNative.setThemePreference(next)) throw new Error('refused');
      setMode(next);
    } catch {
      setThemeError('主题未保存，请重试。');
    } finally {
      themeSavingRef.current = false;
      setThemeSaving(false);
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
    if (savingRef.current) return;
    setSheetError(null);
    setOpenSheet(null);
  };

  const saveExamDate = async (iso: string) => {
    if (savingRef.current) return;
    savingRef.current = true;
    setSaving(true);
    setSheetError(null);
    setReadFailed(false);
    try {
      if (!await YanjiDataNative.updateUserSettings({ examDate: iso })) throw new Error('refused');
    } catch {
      setSheetError('考试日期未保存，请稍后再试。');
      return;
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
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
    if (savingRef.current) return;
    savingRef.current = true;
    setSaving(true);
    setSheetError(null);
    setReadFailed(false);
    try {
      if (!await YanjiDataNative.updateUserSettings({ dailyGoalHours: goalHoursFromMinutes(minutes) })) {
        throw new Error('refused');
      }
    } catch {
      setSheetError('每日学习时长未保存，请稍后再试。');
      return;
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
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
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={{
          paddingHorizontal: layout.pageGutter,
          paddingTop: YanjiSpacing.lg,
          paddingBottom: YanjiSpacing.xxl + YanjiSpacing.lg + keyboardHeight,
        }}
      >
        <YanjiEditorialHeader
          title="设置"
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

        <View>
          <YanjiPaperSectionHeading
            title="备考"
            spaceBefore={layout.sectionGap}
            titleStyle={typography.titleGroup}
          />
          <YanjiSettingRow
            label="考试日期"
            value={examDate ? formatExamDate(examDate) : '未设置'}
            // Only the countdown earns a second line here. Why the date matters
            // belongs to the sheet that edits it, not to the page.
            hint={countdownText ?? undefined}
            accessibilityLabel={`考试日期，${examDate ? formatExamDate(examDate) : '未设置'}${countdownText ? `，${countdownText}` : ''}，点击修改`}
            pending={loading}
            onPress={() => openSheetWith('examDate')}
          />
          <YanjiSettingRow
            label="每日学习时长"
            value={formatGoalDuration(goalHours)}
            accessibilityLabel={`每日学习时长，${formatGoalDuration(goalHours)}，点击修改`}
            isLast
            pending={loading}
            onPress={() => openSheetWith('dailyGoal')}
          />
        </View>

        <View>
          <YanjiPaperSectionHeading
            title="学科"
            spaceBefore={layout.sectionGap}
            titleStyle={typography.titleGroup}
          />
          <YanjiSettingRow
            label="专注学科名称"
            value="点击自定义"
            accessibilityLabel="专注学科名称，点击自定义"
            isLast
            onPress={() => openSheetWith('subjects')}
          />
        </View>

        <YanjiPaperSectionHeading
          title="外观"
          spaceBefore={layout.sectionGap}
          titleStyle={typography.titleGroup}
          align="center"
          right={(
            <YanjiPaperSegmentedControl
              compact
              accessibilityLabel="主题外观"
              value={mode}
              disabled={themeSaving}
              options={THEME_OPTIONS.map(option => ({ value: option.mode, label: option.label }))}
              onChange={next => void handleThemeChange(next)}
            />
          )}
        />
        {themeError ? (
          <Text accessibilityRole="alert" style={[typography.captionMeta, { color: colors.danger, marginTop: YanjiSpacing.xs }]}>
            {themeError}
          </Text>
        ) : null}

        <View>
          <YanjiPaperSectionHeading
            title="高级"
            spaceBefore={layout.sectionGap}
            titleStyle={typography.titleGroup}
          />
          <YanjiDecisionModelSection settings={settings} onSaved={refreshSettingsSnapshot} />
        </View>

        <BackupDataSection />

        <YanjiHairline style={{ marginTop: layout.sectionGap }} />
        <Text
          style={[typography.captionMeta, { color: colors.secondary, marginTop: YanjiSpacing.md, textAlign: 'center' }]}
        >
          研迹 · 学习数据默认保存在本机
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
      <SubjectsSheet
        visible={openSheet === 'subjects'}
        onClose={closeSheet}
      />
    </View>
  );
}
