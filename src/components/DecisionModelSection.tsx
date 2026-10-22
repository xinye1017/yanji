/**
 * 设置 · 高级：AI 决策模型配置。
 *
 * Extracted from `SettingsScreen` because it is a self-contained form — its own
 * nine pieces of state, four handlers and one collapsed disclosure — that the
 * page only composes. Keeping it inline made the page 500+ lines and mixed the
 * one-and-done decision-model setup with the two 备考 values the product
 * actually measures against.
 *
 * The page still owns persistence *order*; this block owns the draft, the
 * connection test and the save/report cycle for its own fields.
 */

import React, { useEffect, useRef, useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import { YanjiIcon, YanjiPaperSegmentedControl, YanjiTextAction, YanjiTextInput } from './YanjiUI';
import { YanjiSettingDisclosureRow } from './SettingsRows';
import { testDecisionConnection, toBaseUrl } from '../services/decisionClient';
import { YanjiDataNative } from '../bridge';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing } from '../theme/tokens';
import type { UserSettings } from '../bridge';

const DECISION_PROVIDERS: Array<{ value: 'openrouter' | 'typesafe'; label: string }> = [
  { value: 'openrouter', label: 'OpenRouter' },
  { value: 'typesafe', label: 'TypeSafe (Jev)' },
];

const PROVIDER_DEFAULTS: Record<'openrouter' | 'typesafe', { model: string; endpoint: string }> = {
  openrouter: {
    model: 'respan/span-01-lite:free',
    endpoint: 'https://openrouter.ai/api/v1',
  },
  typesafe: {
    model: 'jev-latest',
    endpoint: 'https://api.typesafe.ai/v1',
  },
};

/**
 * The advanced block: a disclosure row plus, when open, the decision-model form.
 *
 * `settings` is the stored snapshot; it seeds the drafts whenever the stored
 * values change, so a save that lands in Room is reflected without the user
 * having to re-open the block.
 */
export function YanjiDecisionModelSection({
  settings,
  onSaved,
}: {
  settings: UserSettings | null;
  /** Called after a successful save or key change so the page can re-read Room. */
  onSaved: () => Promise<void> | void;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout, radius } = paper;

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
  const savingAiRef = useRef(false);

  useEffect(() => {
    if (!settings) return;
    const currentProvider = (settings.decisionProvider as 'openrouter' | 'typesafe') || 'openrouter';
    setProvider(currentProvider);
    setModel(settings.decisionModel || PROVIDER_DEFAULTS[currentProvider].model);
    const rawEndpoint = settings.decisionEndpoint || PROVIDER_DEFAULTS[currentProvider].endpoint;
    setEndpoint(toBaseUrl(rawEndpoint, currentProvider));
    setHasKey(!!settings.hasDecisionApiKey);
  }, [
    settings?.decisionProvider,
    settings?.decisionModel,
    settings?.decisionEndpoint,
    settings?.hasDecisionApiKey,
  ]);

  const handleProviderChange = (nextProvider: 'openrouter' | 'typesafe') => {
    setProvider(nextProvider);
    setTestResult(null);
    setAiMessage(null);
    // Only swap the defaults when the field still holds the *other* provider's
    // default: a value the user typed must never be overwritten by a switch.
    if (!model || model === PROVIDER_DEFAULTS.openrouter.model || model === PROVIDER_DEFAULTS.typesafe.model) {
      setModel(PROVIDER_DEFAULTS[nextProvider].model);
    }
    if (
      !endpoint ||
      endpoint === PROVIDER_DEFAULTS.openrouter.endpoint ||
      endpoint === PROVIDER_DEFAULTS.typesafe.endpoint ||
      endpoint === 'https://openrouter.ai/api/alpha/decisions' ||
      endpoint === 'https://api.typesafe.ai/v1/systemone'
    ) {
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
    if (savingAiRef.current) return;
    savingAiRef.current = true;
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
      try {
        await onSaved();
        setAiMessage('AI 决策模型配置已保存');
      } catch {
        setAiMessage('配置已保存，页面刷新失败。请稍后重新进入设置查看。');
      }
    } catch (error) {
      setAiMessage((error as Error | null)?.message || '保存失败，请稍后再试');
    } finally {
      savingAiRef.current = false;
      setSavingAi(false);
    }
  };

  const handleClearKey = async () => {
    try {
      await YanjiDataNative.clearDecisionApiKey();
      setHasKey(false);
      setApiKeyDraft('');
      await onSaved();
      setAiMessage('API Key 已清除');
    } catch {
      setAiMessage('清除失败');
    }
  };

  return (
    <View>
      <YanjiSettingDisclosureRow
        label="AI 决策模型"
        // Until the snapshot arrives, "未配置" is a claim the page has not
        // verified — the rows below show 「—」 for the same reason.
        summary={settings === null ? '—' : hasKey ? '已配置' : '未配置'}
        icon="sparkles"
        expanded={advancedOpen}
        isLast
        onPress={() => { if (!savingAiRef.current) setAdvancedOpen(open => !open); }}
      />

      {advancedOpen ? (
        <View style={{ marginTop: YanjiSpacing.md }}>
          <Text style={[typography.captionMeta, { color: colors.secondary, marginBottom: YanjiSpacing.sm }]}>
            用于「记录此刻」区分灵感与疑点；正文会发送到你配置的模型服务，密钥以系统 Keystore 加密保存在本机。
          </Text>

          <YanjiPaperSegmentedControl
            accessibilityLabel="决策模型提供商"
            value={provider}
            disabled={savingAi}
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
                editable={!savingAi}
                placeholder={PROVIDER_DEFAULTS[provider].model}
                autoCapitalize="none"
                accessibilityLabel="模型标识"
              />
            </View>

            <View>
              <Text style={[typography.captionMeta, { color: colors.secondary, marginBottom: 4 }]}>
                API Base URL
              </Text>
              <YanjiTextInput
                value={endpoint}
                onChangeText={setEndpoint}
                editable={!savingAi}
                placeholder={PROVIDER_DEFAULTS[provider].endpoint}
                autoCapitalize="none"
                accessibilityLabel="API Base URL"
              />
              <Text style={[typography.captionMeta, { color: colors.secondary, marginTop: 4 }]}>
                只需输入基础地址（如 {PROVIDER_DEFAULTS[provider].endpoint}），端点后缀将由软件内部自动补全
              </Text>
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
                  API Key{hasKey ? ' · 已保存' : ' · 未配置'}
                </Text>
                {hasKey ? (
                  <YanjiTextAction label="清除密钥" onPress={() => void handleClearKey()} disabled={savingAi} />
                ) : null}
              </View>
              <YanjiTextInput
                value={apiKeyDraft}
                onChangeText={setApiKeyDraft}
                editable={!savingAi}
                placeholder={hasKey ? '已加密保存，输入新密钥以覆盖' : 'sk-...'}
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
                <View style={{ flexDirection: 'row', alignItems: 'flex-start' }}>
                  <View style={{ marginRight: YanjiSpacing.xs, marginTop: 1 }}>
                    <YanjiIcon
                      name={testResult.success ? 'check' : 'close'}
                      size={typography.captionMeta.fontSize}
                      color={testResult.success ? colors.positive : colors.brandInk}
                    />
                  </View>
                  <Text
                    style={[
                      typography.captionMeta,
                      {
                        flex: 1,
                        color: testResult.success ? colors.positive : colors.brandInk,
                        fontWeight: '600',
                      },
                    ]}
                  >
                    {testResult.message}
                  </Text>
                </View>
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
                  accessibilityState={{ busy: savingAi, disabled: savingAi || testing }}
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
  );
}
