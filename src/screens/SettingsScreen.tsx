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
import { Pressable, ScrollView, Text, View } from 'react-native';
import { YanjiDataNative, YanjiThemeNative, onThemeChanged } from '../bridge';
import type { ThemeMode } from '../bridge';
import {
  YanjiEditorialHeader,
  YanjiHairline,
  YanjiIconButton,
  YanjiPaperSectionHeading,
  YanjiPaperSegmentedControl,
  YanjiTextAction,
  YanjiTextInput,
} from '../components/YanjiUI';
import { YanjiSettingDisclosureRow, YanjiSettingRow } from '../components/SettingsRows';
import { DailyGoalSheet, ExamDateSheet } from '../components/SettingsSheets';
import { useNavigationActions } from '../navigation/NavigationShell';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing } from '../theme/tokens';
import { refreshSettingsSnapshot, useSettingsSnapshot } from '../state/dataRefresh';
import { testDecisionConnection } from '../services/decisionClient';
import {
  examCountdownText,
  formatExamDate,
  formatGoalDuration,
  goalHoursFromMinutes,
  todayIso,
} from './settingsViewLogic';

const THEME_OPTIONS: Array<{ mode: ThemeMode; label: string }> = [
  { mode: 'SYSTEM', label: '跟随系统' },
  { mode: 'LIGHT', label: '浅色' },
  { mode: 'DARK', label: '深色' },
];

const DECISION_PROVIDERS: Array<{ value: 'openrouter' | 'typesafe'; label: string }> = [
  { value: 'openrouter', label: 'OpenRouter (Span)' },
  { value: 'typesafe', label: 'TypeSafe (Jev)' },
];

const PROVIDER_DEFAULTS: Record<'openrouter' | 'typesafe', { model: string; endpoint: string }> = {
  openrouter: {
    model: 'respan/span-01-lite:free',
    endpoint: 'https://openrouter.ai/api/alpha/decisions',
  },
  typesafe: {
    model: 'jev-latest',
    endpoint: 'https://api.typesafe.ai/v1/systemone',
  },
};

type Sheet = 'examDate' | 'dailyGoal' | null;

export function SettingsScreen(): React.JSX.Element {
  const theme = useYanjiTheme();
  const paper = theme.paper;
  const { colors, typography, layout, radius } = paper;
  const { closeSettings } = useNavigationActions();
  const settingsSnapshot = useSettingsSnapshot('settings', true);
  const settings = settingsSnapshot?.settings ?? null;

  const [mode, setMode] = useState<ThemeMode>(theme.mode);
  const [openSheet, setOpenSheet] = useState<Sheet>(null);
  const [saving, setSaving] = useState(false);
  const [sheetError, setSheetError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  const [advancedOpen, setAdvancedOpen] = useState(false);
  const [provider, setProvider] = useState<'openrouter' | 'typesafe'>('openrouter');
  const [model, setModel] = useState(PROVIDER_DEFAULTS.openrouter.model);
  const [endpoint, setEndpoint] = useState(PROVIDER_DEFAULTS.openrouter.endpoint);
  const [apiKeyDraft, setApiKeyDraft] = useState('');
  const [hasKey, setHasKey] = useState(false);
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<{ success: boolean; message: string } | null>(null);
  const [savingAi, setSavingAi] = useState(false);
  const [aiMessage, setAiMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!settings) return;
    const currentProvider = (settings.decisionProvider as 'openrouter' | 'typesafe') || 'openrouter';
    setProvider(currentProvider);
    setModel(settings.decisionModel || PROVIDER_DEFAULTS[currentProvider].model);
    setEndpoint(settings.decisionEndpoint || PROVIDER_DEFAULTS[currentProvider].endpoint);
    setHasKey(!!settings.hasDecisionApiKey);
  }, [
    settings?.decisionProvider,
    settings?.decisionModel,
    settings?.decisionEndpoint,
    settings?.hasDecisionApiKey,
  ]);

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
    setActionError(null);
    setOpenSheet(sheet);
  };

  const closeSheet = () => {
    if (saving) return;
    setSheetError(null);
    setOpenSheet(null);
  };

  const saveExamDate = async (iso: string) => {
    setSaving(true);
    setSheetError(null);
    try {
      if (!await YanjiDataNative.updateUserSettings({ examDate: iso })) throw new Error('refused');
      await refreshSettingsSnapshot();
      setOpenSheet(null);
    } catch {
      setSheetError('考试日期未保存，请稍后再试。');
    } finally {
      setSaving(false);
    }
  };

  const saveDailyGoal = async (minutes: number) => {
    setSaving(true);
    setSheetError(null);
    try {
      if (!await YanjiDataNative.updateUserSettings({ dailyGoalHours: goalHoursFromMinutes(minutes) })) {
        throw new Error('refused');
      }
      await refreshSettingsSnapshot();
      setOpenSheet(null);
    } catch {
      setSheetError('每日学习时长未保存，请稍后再试。');
    } finally {
      setSaving(false);
    }
  };

  const handleProviderChange = (nextProvider: 'openrouter' | 'typesafe') => {
    setProvider(nextProvider);
    setTestResult(null);
    setAiMessage(null);
    // Only swap the defaults when the field still holds the *other* provider's
    // default: a hand-edited model name must survive a provider switch.
    if (!model || model === PROVIDER_DEFAULTS.openrouter.model || model === PROVIDER_DEFAULTS.typesafe.model) {
      setModel(PROVIDER_DEFAULTS[nextProvider].model);
    }
    if (!endpoint || endpoint === PROVIDER_DEFAULTS.openrouter.endpoint || endpoint === PROVIDER_DEFAULTS.typesafe.endpoint) {
      setEndpoint(PROVIDER_DEFAULTS[nextProvider].endpoint);
    }
  };

  const handleTestConnection = async () => {
    setTesting(true);
    setTestResult(null);
    setAiMessage(null);
    try {
      let keyToTest = apiKeyDraft.trim();
      if (!keyToTest && hasKey) {
        keyToTest = (await YanjiDataNative.getDecisionApiKey()) ?? '';
      }
      const result = await testDecisionConnection(
        { provider, model: model.trim(), endpoint: endpoint.trim() },
        keyToTest
      );
      setTestResult(result);
    } catch (error) {
      setTestResult({ success: false, message: (error as Error | null)?.message || '测试连接异常' });
    } finally {
      setTesting(false);
    }
  };

  const handleSaveAi = async () => {
    setSavingAi(true);
    setTestResult(null);
    setAiMessage(null);
    try {
      const saved = await YanjiDataNative.updateUserSettings({
        decisionProvider: provider,
        decisionModel: model.trim(),
        decisionEndpoint: endpoint.trim(),
      });
      if (!saved) throw new Error('保存配置失败');

      if (apiKeyDraft.trim()) {
        const savedKey = await YanjiDataNative.saveDecisionApiKey(apiKeyDraft.trim());
        if (!savedKey) throw new Error('保存密钥失败');
        setApiKeyDraft('');
        setHasKey(true);
      }
      await refreshSettingsSnapshot();
      setAiMessage('AI 决策模型配置已保存');
    } catch (error) {
      setAiMessage((error as Error | null)?.message || '保存失败，请稍后再试');
    } finally {
      setSavingAi(false);
    }
  };

  const handleClearKey = async () => {
    try {
      await YanjiDataNative.clearDecisionApiKey();
      setHasKey(false);
      setApiKeyDraft('');
      await refreshSettingsSnapshot();
      setAiMessage('API Key 已清除');
    } catch {
      setAiMessage('清除失败');
    }
  };

  const examDate = settings?.examDate ?? '';
  const countdownText = examDate ? examCountdownText(examDate, todayIso()) : null;
  const goalHours = settings?.dailyGoalHours;
  const advancedSummary = hasKey ? '已配置' : '未配置';
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

        {actionError ? (
          <Text
            accessibilityRole="alert"
            style={[typography.bodyPrimary, { color: colors.danger, marginTop: YanjiSpacing.md }]}
          >
            {actionError}
          </Text>
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
          <YanjiSettingDisclosureRow
            label="AI 决策模型"
            summary={advancedSummary}
            icon="sparkles"
            expanded={advancedOpen}
            isLast
            onPress={() => setAdvancedOpen(open => !open)}
          />

          {advancedOpen ? (
            <View style={{ marginTop: YanjiSpacing.md }}>
              <Text style={[typography.captionMeta, { color: colors.secondary, marginBottom: YanjiSpacing.sm }]}>
                用于「记录此刻」自动区分灵感与疑点。密钥加密保存在系统 Keystore 中，不进入备份。
              </Text>

              <YanjiPaperSegmentedControl
                accessibilityLabel="决策模型提供商"
                value={provider}
                options={DECISION_PROVIDERS}
                onChange={next => handleProviderChange(next as 'openrouter' | 'typesafe')}
              />

              <View style={{ marginTop: YanjiSpacing.md, gap: YanjiSpacing.sm }}>
                <View>
                  <Text style={[typography.captionMeta, { color: colors.secondary, marginBottom: 4 }]}>
                    模型标识
                  </Text>
                  <YanjiTextInput
                    value={model}
                    onChangeText={setModel}
                    placeholder={PROVIDER_DEFAULTS[provider].model}
                    autoCapitalize="none"
                    accessibilityLabel="模型标识"
                  />
                </View>

                <View>
                  <Text style={[typography.captionMeta, { color: colors.secondary, marginBottom: 4 }]}>
                    接口地址
                  </Text>
                  <YanjiTextInput
                    value={endpoint}
                    onChangeText={setEndpoint}
                    placeholder={PROVIDER_DEFAULTS[provider].endpoint}
                    autoCapitalize="none"
                    accessibilityLabel="接口地址"
                  />
                </View>

                <View>
                  <View
                    style={{
                      flexDirection: 'row',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      marginBottom: 4,
                    }}
                  >
                    <Text style={[typography.captionMeta, { color: colors.secondary }]}>
                      API Key {hasKey ? '(已保存在 Keystore)' : '(未配置)'}
                    </Text>
                    {hasKey ? (
                      <YanjiTextAction label="清除密钥" onPress={() => void handleClearKey()} />
                    ) : null}
                  </View>
                  <YanjiTextInput
                    value={apiKeyDraft}
                    onChangeText={setApiKeyDraft}
                    placeholder={hasKey ? '已加密存储（输入新密钥以覆盖）' : 'sk-...'}
                    secureTextEntry
                    autoCapitalize="none"
                    accessibilityLabel="API Key"
                  />
                </View>

                {testResult ? (
                  <View
                    style={{
                      padding: YanjiSpacing.sm,
                      borderRadius: radius.surface,
                      backgroundColor: testResult.success ? colors.positiveSubtle : colors.brandSubtle,
                    }}
                  >
                    <Text
                      style={[
                        typography.captionMeta,
                        {
                          color: testResult.success ? colors.positive : colors.brandInk,
                          fontWeight: '600',
                        },
                      ]}
                    >
                      {testResult.success ? '✓ ' : '✕ '}
                      {testResult.message}
                    </Text>
                  </View>
                ) : null}

                {aiMessage ? (
                  <Text style={[typography.captionMeta, { color: colors.brandInk }]}>{aiMessage}</Text>
                ) : null}

                <View style={{ flexDirection: 'row', gap: YanjiSpacing.md, marginTop: YanjiSpacing.xs }}>
                  <View style={{ flex: 1 }}>
                    <Pressable
                      onPress={() => void handleTestConnection()}
                      disabled={testing || savingAi || (!hasKey && !apiKeyDraft.trim())}
                      accessibilityRole="button"
                      accessibilityLabel="测试决策模型连通性"
                      style={({ pressed }) => ({
                        minHeight: layout.rowMinHeight,
                        alignItems: 'center',
                        justifyContent: 'center',
                        borderRadius: radius.pill,
                        backgroundColor: colors.subtle,
                        borderWidth: layout.hairlineWidth,
                        borderColor: colors.hairline,
                        opacity: pressed || (!hasKey && !apiKeyDraft.trim()) ? 0.6 : 1,
                      })}
                    >
                      <Text style={[typography.bodyPrimary, { color: colors.ink, fontWeight: '600' }]}>
                        {testing ? '测试中…' : '测试连通性'}
                      </Text>
                    </Pressable>
                  </View>
                  <View style={{ flex: 1 }}>
                    <Pressable
                      onPress={() => void handleSaveAi()}
                      disabled={savingAi || testing}
                      accessibilityRole="button"
                      accessibilityLabel="保存 AI 决策配置"
                      style={({ pressed }) => ({
                        minHeight: layout.rowMinHeight,
                        alignItems: 'center',
                        justifyContent: 'center',
                        borderRadius: radius.pill,
                        backgroundColor: colors.ink,
                        opacity: pressed || savingAi ? 0.7 : 1,
                      })}
                    >
                      <Text style={[typography.bodyPrimary, { color: colors.canvas, fontWeight: '700' }]}>
                        {savingAi ? '保存中…' : '保存配置'}
                      </Text>
                    </Pressable>
                  </View>
                </View>
              </View>
            </View>
          ) : null}
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
