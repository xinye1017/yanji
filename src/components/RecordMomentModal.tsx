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

import React, { useCallback, useEffect, useState } from 'react';
import { Modal, Pressable, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useReducedMotion } from 'react-native-reanimated';
import { YanjiDataNative, YanjiTimerNative } from '../bridge';
import {
  YanjiHairline,
  YanjiPaperSheetHeading,
  YanjiPrimaryButton,
  YanjiTextInput,
  useYanjiKeyboardInset,
} from './YanjiUI';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing } from '../theme/tokens';

export interface RecordMomentModalProps {
  visible: boolean;
  /** ISO date `yyyy-MM-dd`; defaults to the device's local today. */
  date: string;
  onClose: () => void;
  onSaved?: () => void;
  /**
   * When provided, the sheet edits this note instead of creating a new one:
   * the field is prefilled and saving calls `updateNote`. The note's date,
   * session binding and favorite state are left untouched.
   */
  editingNote?: { id: string; content: string } | null;
}

export function RecordMomentModal({
  visible,
  date,
  onClose,
  onSaved,
  editingNote = null,
}: RecordMomentModalProps): React.JSX.Element {
  const theme = useYanjiTheme();
  const reduceMotion = useReducedMotion();
  const [text, setText] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Re-seed the sheet every time it opens: prefilled for editing, blank for a
  // new capture. Without this, an edited note's text would leak into the next
  // "记录此刻", and a stale draft would show when reopening an editor.
  useEffect(() => {
    if (!visible) return;
    setText(editingNote?.content ?? '');
    setError(null);
  }, [visible, editingNote]);

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
      if (editingNote) {
        await YanjiDataNative.updateNote(editingNote.id, content);
      } else {
        // Bind to the active session when one exists; the timer keeps running.
        let sessionId: string | null = null;
        try {
          const session = await YanjiTimerNative.getActiveSession();
          sessionId = session?.sessionId ?? null;
        } catch {
          sessionId = null;
        }
        await YanjiDataNative.saveQuickNote(content, date, sessionId);
      }
      setText('');
      onSaved?.();
      onClose();
    } catch {
      // Preserve the user's text so nothing is lost on failure.
      setError('保存失败，内容已保留');
    } finally {
      setSaving(false);
    }
  }, [saving, text, date, onClose, onSaved, editingNote]);

  const handleClose = useCallback(() => {
    setError(null);
    onClose();
  }, [onClose]);

  // View constants for the paper sheet, grouped with the keyboard inset they
  // have to agree with.
  const paper = theme.paper;
  const insets = useSafeAreaInsets();
  const keyboardInset = useYanjiKeyboardInset();

  return (
    <Modal visible={visible} transparent animationType={reduceMotion ? 'none' : 'fade'} onRequestClose={handleClose}>
      <View
        style={{
          flex: 1,
          justifyContent: 'flex-end',
          backgroundColor: theme.colors.scrim,
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
              marginBottom: YanjiSpacing.lg,
            }}
          />

          <YanjiPaperSheetHeading
            title={editingNote ? '编辑记录' : '记录此刻'}
            subtitle={date}
            onClose={handleClose}
          />

          {/* The journal well: `multiline` is what makes `YanjiTextInput` pick
              the serif body face, so the sheet no longer overrides the font per
              call site. The 96–200 dp well height stays sheet-owned. */}
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
                paper.typography.captionMeta,
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
      </View>
    </Modal>
  );
}
