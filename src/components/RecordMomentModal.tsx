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
  TextInput,
  View,
} from 'react-native';
import { YanjiDataNative, YanjiTimerNative } from '../bridge';
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
          backgroundColor: 'rgba(0,0,0,0.32)',
        }}
      >
        <Pressable style={{ flex: 1 }} onPress={handleClose} accessibilityLabel="关闭" />
        <View
          style={{
            backgroundColor: theme.colors.bgSurface,
            borderTopLeftRadius: YanjiRadius.xxl,
            borderTopRightRadius: YanjiRadius.xxl,
            padding: YanjiSpacing.xl,
            paddingBottom: YanjiSpacing.xxl,
          }}
        >
          <Text
            style={{
              color: theme.colors.textPrimary,
              fontSize: 17,
              fontWeight: '600',
              marginBottom: YanjiSpacing.md,
            }}
          >
            记录此刻
          </Text>

          <TextInput
            value={text}
            onChangeText={setText}
            placeholder="现在的想法、卡点或收获…"
            placeholderTextColor={theme.colors.textTertiary}
            multiline
            autoFocus
            style={{
              minHeight: 96,
              maxHeight: 200,
              backgroundColor: theme.colors.bgElevated,
              borderRadius: YanjiRadius.md,
              padding: YanjiSpacing.md,
              color: theme.colors.textPrimary,
              fontSize: 15,
              lineHeight: 22,
              textAlignVertical: 'top',
            }}
          />

          {error ? (
            <Text style={{ color: theme.colors.danger, fontSize: 13, marginTop: 8 }}>{error}</Text>
          ) : null}

          <View style={{ flexDirection: 'row', justifyContent: 'flex-end', marginTop: YanjiSpacing.lg }}>
            <Pressable onPress={handleClose} style={{ paddingVertical: 10, paddingHorizontal: 16 }}>
              <Text style={{ color: theme.colors.textSecondary, fontSize: 15 }}>取消</Text>
            </Pressable>
            <Pressable
              onPress={handleSave}
              disabled={saving}
              style={{
                paddingVertical: 10,
                paddingHorizontal: 20,
                marginLeft: YanjiSpacing.sm,
                borderRadius: YanjiRadius.md,
                backgroundColor: theme.colors.accentPrimary,
                opacity: saving ? 0.6 : 1,
              }}
            >
              <Text style={{ color: '#FFFFFF', fontSize: 15, fontWeight: '600' }}>
                {saving ? '保存中' : '保存'}
              </Text>
            </Pressable>
          </View>
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}
