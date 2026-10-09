/**
 * Yanji session note editor — attach or edit the note on a finished focus run.
 *
 * Scope honesty, same discipline as TaskEditorModal: the bridge can update a
 * focus session's note (`updateSessionNote`) and nothing more. Exam records
 * have no editable note in the domain model, so this sheet states that plainly
 * instead of offering a field that would silently discard what was typed.
 *
 * Empty input is rejected rather than saved as blank: a cleared note and a note
 * that was never written are different facts, and only the second is real here.
 */

import React, { useCallback, useEffect, useState } from 'react';
import {
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  Text,
  View,
} from 'react-native';
import { YanjiDataNative } from '../bridge';
import {
  YanjiHairline,
  YanjiIcon,
  YanjiIconButton,
  YanjiPrimaryButton,
  YanjiTextInput,
} from './YanjiUI';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiRadius, YanjiSpacing } from '../theme/tokens';

export interface SessionNoteModalProps {
  visible: boolean;
  /** `null` when no row is selected; saving is refused rather than guessed. */
  sessionId: string | null;
  /** Human-readable session name, shown above the input. */
  sessionTitle: string;
  initialNote: string;
  /** Exam sessions carry no editable note; the sheet says so instead of lying. */
  examSession: boolean;
  onClose: () => void;
  onSaved?: () => void;
}

export function SessionNoteModal({
  visible,
  sessionId,
  sessionTitle,
  initialNote,
  examSession,
  onClose,
  onSaved,
}: SessionNoteModalProps): React.JSX.Element {
  const theme = useYanjiTheme();
  const [note, setNote] = useState(initialNote);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Re-seed whenever the sheet opens on a different row, so a previous draft
  // never leaks into the next session.
  useEffect(() => {
    if (!visible) return;
    setNote(initialNote);
    setError(null);
    setSaving(false);
  }, [visible, initialNote, sessionId]);

  const handleSave = useCallback(async () => {
    const clean = note.trim();
    if (clean.length === 0) {
      setError('随笔不能为空');
      return;
    }
    if (!sessionId) {
      setError('找不到这条记录，请关闭后重试');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await YanjiDataNative.updateSessionNote(sessionId, examSession, clean);
      onSaved?.();
      onClose();
    } catch (err) {
      // Preserve the draft: losing typed text to a failed write is the one
      // outcome the user cannot recover from.
      setError(err instanceof Error && err.message ? err.message : '保存失败，请稍后再试');
      setSaving(false);
    }
  }, [note, sessionId, examSession, onClose, onSaved]);

  const handleClose = useCallback(() => {
    setSaving(false);
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
            <View style={{ flex: 1, flexDirection: 'row', alignItems: 'center' }}>
              <YanjiIcon name="compose" size={17} color={theme.colors.accentPrimary} />
              <Text
                numberOfLines={1}
                style={{
                  color: theme.colors.textPrimary,
                  ...theme.typography.valueStrong,
                  marginLeft: YanjiSpacing.sm,
                  flex: 1,
                }}
              >
                专注随笔
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

          {examSession ? (
            <Text style={[theme.typography.caption, { color: theme.colors.textSecondary }]}>
              模考记录不保存随笔，这里只展示它的时间和时长。
            </Text>
          ) : (
            <>
              <Text
                numberOfLines={1}
                style={[theme.typography.caption, { color: theme.colors.textTertiary }]}
              >
                {sessionTitle}
              </Text>
              <YanjiTextInput
                value={note}
                onChangeText={setNote}
                placeholder="这段专注里做了什么、卡在哪里…"
                multiline
                accessibilityLabel="专注随笔"
                style={{ minHeight: 96, maxHeight: 200, marginTop: YanjiSpacing.sm }}
              />
            </>
          )}

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

          {!examSession ? (
            <>
              <YanjiHairline style={{ marginTop: YanjiSpacing.lg }} />

              <View style={{ marginTop: YanjiSpacing.lg }}>
                <YanjiPrimaryButton
                  icon="check"
                  label={saving ? '保存中' : '保存随笔'}
                  onPress={handleSave}
                  disabled={saving}
                />
              </View>
            </>
          ) : null}
        </View>
      </KeyboardAvoidingView>
    </Modal>
  );
}
