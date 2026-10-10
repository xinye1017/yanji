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

import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  Keyboard,
  Modal,
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

/**
 * Height of the on-screen keyboard, sampled from `Keyboard.metrics()` the
 * moment the input takes focus and re-read on every keyboard frame change.
 *
 * Why not `KeyboardAvoidingView`: this sheet lives in an RN `Modal`, whose
 * window is a separate Dialog. `KeyboardAvoidingView` derives its offset from
 * its own on-screen frame, and Android delivers the keyboard event from the
 * Activity's root view — inside a Modal the two never agree, so the offset
 * comes out as 0 and the sheet is left underneath the keyboard.
 */
function useKeyboardHeight(): number {
  const [height, setHeight] = useState(0);
  const lastRef = useRef(-1);

  useEffect(() => {
    const sync = () => {
      const next = Keyboard.metrics()?.height ?? 0;
      if (next !== lastRef.current) {
        lastRef.current = next;
        setHeight(next);
      }
    };
    sync();
    const show = Keyboard.addListener('keyboardDidShow', sync);
    const hide = Keyboard.addListener('keyboardDidHide', sync);
    return () => {
      show.remove();
      hide.remove();
    };
  }, []);

  return height;
}

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

  const keyboardHeight = useKeyboardHeight();

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={handleClose}>
      <View
        style={{
          flex: 1,
          justifyContent: 'flex-end',
          backgroundColor: theme.colors.scrim,
          // Lift the whole sheet clear of the keyboard. `paddingBottom` (not a
          // bottom offset) keeps the scrim filling the screen so the backdrop
          // still swallows taps above the sheet while it is raised.
          paddingBottom: keyboardHeight,
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
              <YanjiIcon
                name="compose"
                size={17}
                color={theme.colors.accentPrimary}
              />
              <Text
                style={{
                  color: theme.colors.textPrimary,
                  fontSize: 17,
                  fontWeight: '600',
                  marginLeft: YanjiSpacing.sm,
                }}
              >
                {editingNote ? '编辑记录' : '记录此刻'}
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
      </View>
    </Modal>
  );
}
