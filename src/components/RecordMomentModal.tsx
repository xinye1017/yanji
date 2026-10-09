/**
 * Yanji "Record Moment" — the shared lightweight capture sheet.
 *
 * Behaviour contract (PROJECT.md § R3 + E2E Feature 15/16):
 * - Plain text only. No title, tag, mood or rating fields.
 * - Saving never pauses or disturbs a running focus session.
 * - Empty / whitespace-only input is rejected.
 * - On success the sheet closes immediately; on failure the text is preserved.
 * - The note is auto-associated with the current date and, when present, the
 *   active focus session id.
 */

import React, { useCallback, useState } from 'react';
import {
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  Text,
  View,
} from 'react-native';
import { YanjiDataNative, YanjiTimerNative } from '../bridge';
import {
  YanjiHairline,
  YanjiIcon,
  YanjiIconButton,
  YanjiPrimaryButton,
  YanjiTextInput,
} from './YanjiUI';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiRadius, YanjiSpacing } from '../theme/tokens';

export interface RecordMomentModalProps {
  visible: boolean;
  /** ISO date `yyyy-MM-dd`; defaults to the device's local today. */
  date: string;
  onClose: () => void;
  onSaved?: () => void;
}

export function RecordMomentModal({
  visible,
  date,
  onClose,
  onSaved,
}: RecordMomentModalProps): React.JSX.Element {
  const theme = useYanjiTheme();
  const [text, setText] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSave = useCallback(async () => {
    if (saving) return;
    const content = text.trim();
    if (content.length === 0) {
      setError('写点什么再保存吧');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      // Bind to the active session when one exists; the timer keeps running.
      let sessionId: string | null = null;
      try {
        const session = await YanjiTimerNative.getActiveSession();
        sessionId = session?.sessionId ?? null;
      } catch {
        sessionId = null;
      }
      await YanjiDataNative.saveQuickNote(content, date, sessionId);
      setText('');
      onSaved?.();
      onClose();
    } catch {
      // Preserve the user's text so nothing is lost on failure.
      setError('保存失败，内容已保留');
    } finally {
      setSaving(false);
    }
  }, [saving, text, date, onClose, onSaved]);

  const handleClose = useCallback(() => {
    setError(null);
    onClose();
  }, [onClose]);

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={handleClose}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        style={{
          flex: 1,
          justifyContent: 'flex-end',
          backgroundColor: theme.colors.scrim,
        }}
      >
        <Pressable style={{ flex: 1 }} onPress={handleClose} accessibilityLabel="关闭" />

        {/* The sheet: a floating page with a grabber, the input well sunk into it */}
        <View
          style={[
            {
              backgroundColor: theme.colors.bgFloating,
              borderTopLeftRadius: YanjiRadius.xxl,
              borderTopRightRadius: YanjiRadius.xxl,
              paddingHorizontal: YanjiSpacing.xl,
              paddingTop: YanjiSpacing.md,
              paddingBottom: YanjiSpacing.xxl,
            },
            theme.shadow.floating(theme.isDark),
          ]}
        >
          <View
            style={{
              alignSelf: 'center',
              width: 36,
              height: 4,
              borderRadius: YanjiRadius.full,
              backgroundColor: theme.colors.hairline,
              marginBottom: YanjiSpacing.md,
            }}
          />

          <View
            style={{
              flexDirection: 'row',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: YanjiSpacing.md,
            }}
          >
            <View style={{ flexDirection: 'row', alignItems: 'center' }}>
              <YanjiIcon name="compose" size={17} color={theme.colors.accentPrimary} />
              <Text
                style={{
                  color: theme.colors.textPrimary,
                  fontSize: 17,
                  fontWeight: '600',
                  marginLeft: YanjiSpacing.sm,
                }}
              >
                记录此刻
              </Text>
            </View>
            <YanjiIconButton
              icon="close"
              accessibilityLabel="取消"
              onPress={handleClose}
              size={36}
              iconSize={16}
              tone="filled"
            />
          </View>

          <YanjiTextInput
            value={text}
            onChangeText={setText}
            placeholder="现在的想法、卡点或收获…"
            multiline
            accessibilityLabel="记录内容"
            style={{ minHeight: 96, maxHeight: 200 }}
          />

          {error ? (
            <Text
              style={[
                theme.typography.caption,
                { color: theme.colors.danger, marginTop: YanjiSpacing.sm },
              ]}
            >
              {error}
            </Text>
          ) : null}

          <YanjiHairline style={{ marginTop: YanjiSpacing.lg }} />

          <View style={{ marginTop: YanjiSpacing.lg }}>
            <YanjiPrimaryButton
              icon="check"
              label={saving ? '保存中' : '保存'}
              onPress={handleSave}
              disabled={saving}
            />
          </View>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}
