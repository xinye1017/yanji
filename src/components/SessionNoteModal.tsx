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
  Modal,
  Pressable,
  Text,
  View,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useReducedMotion } from 'react-native-reanimated';
import { YanjiDataNative } from '../bridge';
import {
  YanjiHairline,
  YanjiPaperSheetHeading,
  YanjiPrimaryButton,
  YanjiTextInput,
  useYanjiKeyboardInset,
} from './YanjiUI';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing } from '../theme/tokens';

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
  const reduceMotion = useReducedMotion();
  const paper = theme.paper;
  const insets = useSafeAreaInsets();
  const keyboardInset = useYanjiKeyboardInset();
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
    <Modal visible={visible} transparent animationType={reduceMotion ? 'none' : 'fade'} onRequestClose={handleClose}>
      <View
        style={{
          flex: 1,
          justifyContent: 'flex-end',
          backgroundColor: paper.colors.scrim,
          // Lift the whole sheet clear of the keyboard. `paddingBottom` (not a
          // bottom offset) keeps the scrim filling the screen so the backdrop
          // still swallows taps above the sheet while it is raised.
          paddingBottom: keyboardInset,
        }}
      >
        <Pressable style={{ flex: 1 }} onPress={handleClose} accessibilityLabel="关闭" />

        {/* The sheet: a floating page with a grabber, the input well sunk into it */}
        <View
          style={[
            {
              backgroundColor: paper.colors.surface,
              borderTopLeftRadius: paper.radius.sheet,
              borderTopRightRadius: paper.radius.sheet,
              paddingHorizontal: paper.layout.pageGutter,
              paddingTop: YanjiSpacing.md,
              paddingBottom: YanjiSpacing.lg + insets.bottom,
            },
            theme.shadow.floating(theme.isDark),
          ]}
        >
          <View
            style={{
              alignSelf: 'center',
              width: 36,
              height: 4,
              borderRadius: paper.radius.pill,
              backgroundColor: paper.colors.hairline,
              marginBottom: YanjiSpacing.md,
            }}
          />

          <YanjiPaperSheetHeading
            title="专注随笔"
            subtitle={sessionTitle}
            onClose={handleClose}
          />

          {examSession ? (
            <Text
              style={[
                paper.typography.bodyJournal,
                { color: paper.colors.secondary, marginTop: YanjiSpacing.lg },
              ]}
            >
              模考记录不保存随笔，这里只展示它的时间和时长。
            </Text>
          ) : (
            <YanjiTextInput
              value={note}
              onChangeText={setNote}
              placeholder="这段专注里做了什么、卡在哪里…"
              multiline
              accessibilityLabel="专注随笔"
              style={{ minHeight: 96, maxHeight: 200, marginTop: YanjiSpacing.lg }}
            />
          )}

          {error ? (
            <Text
              style={[
                paper.typography.captionMeta,
                { color: paper.colors.danger, marginTop: YanjiSpacing.sm },
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
      </View>
    </Modal>
  );
}
