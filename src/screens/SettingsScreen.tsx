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
import { Pressable, ScrollView, Text, View } from 'react-native';
import { YanjiDataNative, YanjiThemeNative, onThemeChanged } from '../bridge';
import type { ThemeMode } from '../bridge';
import {
  YanjiEditorialHeader,
  YanjiIconButton,
  YanjiPaperSectionHeading,
  YanjiPaperSegmentedControl,
  YanjiPrimaryButton,
  YanjiTextAction,
  YanjiTextInput,
} from '../components/YanjiUI';
import { useNavigationActions } from '../navigation/NavigationShell';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing } from '../theme/tokens';
import { refreshSettingsSnapshot, useSettingsSnapshot } from '../state/dataRefresh';
import { testDecisionConnection } from '../services/decisionClient';

const THEME_OPTIONS: Array<{ mode: ThemeMode; label: string }> = [
  { mode: 'SYSTEM', label: '跟随系统' },
  { mode: 'LIGHT', label: '浅色' },
  { mode: 'DARK', label: '深色' },
];

const DECISION_PROVIDERS: Array<{ value: 'openrouter' | 'typesafe'; label: string }> = [
  { value: 'openrouter', label: 'OpenRouter (Span)' },
  { value: 'typesafe', label: 'TypeSafe (Jev)' },
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

  const [provider, setProvider] = useState<'openrouter' | 'typesafe'>('openrouter');
  const [model, setModel] = useState('respan/span-01-lite:free');
  const [endpoint, setEndpoint] = useState('https://openrouter.ai/api/alpha/decisions');
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
    setModel(settings.decisionModel || (currentProvider === 'typesafe' ? 'jev-latest' : 'respan/span-01-lite:free'));
    setEndpoint(settings.decisionEndpoint || (currentProvider === 'typesafe' ? 'https://api.typesafe.ai/v1/systemone' : 'https://openrouter.ai/api/alpha/decisions'));
    setHasKey(!!settings.hasDecisionApiKey);
  }, [settings?.decisionProvider, settings?.decisionModel, settings?.decisionEndpoint, settings?.hasDecisionApiKey]);

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

  const handleProviderChange = (nextProvider: 'openrouter' | 'typesafe') => {
    setProvider(nextProvider);
    setTestResult(null);
    setAiMessage(null);
    if (nextProvider === 'typesafe') {
      if (!model || model === 'respan/span-01-lite:free') setModel('jev-latest');
      if (!endpoint || endpoint === 'https://openrouter.ai/api/alpha/decisions') setEndpoint('https://api.typesafe.ai/v1/systemone');
    } else {
      if (!model || model === 'jev-latest') setModel('respan/span-01-lite:free');
      if (!endpoint || endpoint === 'https://api.typesafe.ai/v1/systemone') setEndpoint('https://openrouter.ai/api/alpha/decisions');
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
      const res = await testDecisionConnection(
        { provider, model: model.trim(), endpoint: endpoint.trim() },
        keyToTest
      );
      setTestResult(res);
    } catch (e: any) {
      setTestResult({ success: false, message: e?.message || '测试连接异常' });
    } finally {
      setTesting(false);
    }
  };

  const handleSaveAi = async () => {
    setSavingAi(true);
    setTestResult(null);
    setAiMessage(null);
    try {
      const ok = await YanjiDataNative.updateUserSettings({
        decisionProvider: provider,
        decisionModel: model.trim(),
        decisionEndpoint: endpoint.trim(),
      });
      if (!ok) throw new Error('保存配置失败');

      if (apiKeyDraft.trim()) {
        const savedKey = await YanjiDataNative.saveDecisionApiKey(apiKeyDraft.trim());
        if (!savedKey) throw new Error('保存密钥失败');
        setApiKeyDraft('');
        setHasKey(true);
      }
      await refreshSettingsSnapshot();
      setAiMessage('AI 决策模型配置已保存');
    } catch (e: any) {
      setAiMessage(e?.message || '保存失败，请稍后再试');
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

        <YanjiPaperSectionHeading title="AI 决策模型" />
        <Text style={[paper.typography.captionMeta, { color: paper.colors.secondary, marginBottom: YanjiSpacing.sm }]}>
          用于「记录此刻」通过决策模型（Span / Jev）自动识别灵感与疑点。密钥加密保存在系统 Keystore 中。
        </Text>

        <YanjiPaperSegmentedControl
          accessibilityLabel="决策模型提供商"
          value={provider}
          options={DECISION_PROVIDERS}
          onChange={next => handleProviderChange(next as 'openrouter' | 'typesafe')}
        />

        <View style={{ marginTop: YanjiSpacing.md, gap: YanjiSpacing.sm }}>
          <View>
            <Text style={[paper.typography.captionMeta, { color: paper.colors.secondary, marginBottom: 4 }]}>
              模型标识 (Model)
            </Text>
            <YanjiTextInput
              value={model}
              onChangeText={setModel}
              placeholder={provider === 'typesafe' ? 'jev-latest' : 'respan/span-01-lite:free'}
              autoCapitalize="none"
              accessibilityLabel="模型标识"
            />
          </View>

          <View>
            <Text style={[paper.typography.captionMeta, { color: paper.colors.secondary, marginBottom: 4 }]}>
              接口地址 (Endpoint)
            </Text>
            <YanjiTextInput
              value={endpoint}
              onChangeText={setEndpoint}
              placeholder={provider === 'typesafe' ? 'https://api.typesafe.ai/v1/systemone' : 'https://openrouter.ai/api/alpha/decisions'}
              autoCapitalize="none"
              accessibilityLabel="接口地址"
            />
          </View>

          <View>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
              <Text style={[paper.typography.captionMeta, { color: paper.colors.secondary }]}>
                API Key {hasKey ? '(已保存在 Keystore)' : '(未配置)'}
              </Text>
              {hasKey ? (
                <YanjiTextAction label="清除密钥" onPress={() => void handleClearKey()} />
              ) : null}
            </View>
            <YanjiTextInput
              value={apiKeyDraft}
              onChangeText={setApiKeyDraft}
              placeholder={hasKey ? '已加密存储 (输入新密钥以覆盖)' : 'sk-...'}
              secureTextEntry
              autoCapitalize="none"
              accessibilityLabel="API Key"
            />
          </View>

          {testResult ? (
            <View style={{
              padding: YanjiSpacing.sm,
              borderRadius: paper.radius.surface,
              backgroundColor: testResult.success ? paper.colors.positiveSubtle : paper.colors.brandSubtle,
            }}>
              <Text style={[
                paper.typography.captionMeta,
                { color: testResult.success ? paper.colors.positive : paper.colors.brandInk, fontWeight: '600' }
              ]}>
                {testResult.success ? '✓ ' : '✕ '}{testResult.message}
              </Text>
            </View>
          ) : null}

          {aiMessage ? (
            <Text style={[paper.typography.captionMeta, { color: paper.colors.brandInk }]}>
              {aiMessage}
            </Text>
          ) : null}

          <View style={{ flexDirection: 'row', gap: YanjiSpacing.md, marginTop: YanjiSpacing.xs }}>
            <View style={{ flex: 1 }}>
              <Pressable
                onPress={() => void handleTestConnection()}
                disabled={testing || savingAi || (!hasKey && !apiKeyDraft.trim())}
                accessibilityRole="button"
                accessibilityLabel="测试决策模型连通性"
                style={({ pressed }) => ({
                  minHeight: paper.layout.rowMinHeight,
                  alignItems: 'center',
                  justifyContent: 'center',
                  borderRadius: paper.radius.pill,
                  backgroundColor: paper.colors.subtle,
                  borderWidth: paper.layout.hairlineWidth,
                  borderColor: paper.colors.hairline,
                  opacity: pressed || (!hasKey && !apiKeyDraft.trim()) ? 0.6 : 1,
                })}
              >
                <Text style={[paper.typography.bodyPrimary, { color: paper.colors.ink, fontWeight: '600' }]}>
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
                  minHeight: paper.layout.rowMinHeight,
                  alignItems: 'center',
                  justifyContent: 'center',
                  borderRadius: paper.radius.pill,
                  backgroundColor: paper.colors.ink,
                  opacity: pressed || savingAi ? 0.7 : 1,
                })}
              >
                <Text style={[paper.typography.bodyPrimary, { color: paper.colors.canvas, fontWeight: '700' }]}>
                  {savingAi ? '保存中…' : '保存配置'}
                </Text>
              </Pressable>
            </View>
          </View>
        </View>

        <View style={{ marginTop: YanjiSpacing.xxl }}>
          <YanjiPrimaryButton label="返回今天" icon="today" onPress={closeSettings} />
        </View>
      </ScrollView>
    </View>
  );
}
