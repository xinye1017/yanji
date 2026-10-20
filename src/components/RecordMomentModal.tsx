/**
 * Yanji "Record Moment" — the shared lightweight capture sheet.
 *
 * Behaviour contract (E2E Feature 15/16):
 * - Plain text only. No title, tag, mood or rating fields.
 * - Saving never pauses or disturbs a running focus session.
 * - Empty / whitespace-only input is rejected.
 * - On success the sheet closes immediately; on failure the text is preserved.
 * - The note is auto-associated with the current date and, when present, the
 *   active focus session id.
 */

import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Modal, Pressable, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useReducedMotion } from 'react-native-reanimated';
import { YanjiDataNative, YanjiTimerNative } from '../bridge';
import { classifyMoment } from '../services/decisionClient';
import {
  YanjiPaperSheetHeading,
  YanjiPrimaryButton,
  YanjiTextInput,
  useYanjiKeyboardInset,
} from './YanjiUI';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing } from '../theme/tokens';
import { parseIsoDate, weekdayLabelOf } from '../state/settingsViewLogic';

/** The sheet's date stamp: `2026-10-20` → `10月20日 · 周二`. */
function dateLabel(iso: string): string {
  const parts = parseIsoDate(iso);
  const weekday = weekdayLabelOf(iso);
  return parts && weekday ? `${parts.month}月${parts.day}日 · ${weekday}` : iso;
}

interface RecordMomentModalProps {
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
  /** When true, the sheet immediately reveals the delete confirmation buttons. */
  initialConfirmDelete?: boolean;
}

export function RecordMomentModal({
  visible,
  date,
  onClose,
  onSaved,
  editingNote = null,
  initialConfirmDelete = false,
}: RecordMomentModalProps): React.JSX.Element {
  const theme = useYanjiTheme();
  const reduceMotion = useReducedMotion();
  const [text, setText] = useState('');
  const [saving, setSaving] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const captureDraft = useRef('');

  // Editing starts from the saved note; new captures keep a separate unsaved
  // draft across closing the sheet and switching mounted tabs.
  useEffect(() => {
    if (!visible) return;
    setText(editingNote?.content ?? captureDraft.current);
    setError(null);
    setConfirmDelete(initialConfirmDelete);
  }, [visible, editingNote, initialConfirmDelete]);

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
        // 使用决策模型自动研判是否为疑点（阈值 0.7），不显示手动打标按钮
        let category = 'INSPIRATION';
        try {
          category = await classifyMoment(content);
        } catch {
          category = 'INSPIRATION';
        }
        await YanjiDataNative.saveQuickNote(content, date, sessionId, category);
      }
      if (!editingNote) captureDraft.current = '';
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

  const handleDelete = useCallback(async () => {
    if (saving || !editingNote) return;
    setSaving(true);
    setError(null);
    try {
      await YanjiDataNative.deleteNote(editingNote.id);
      setText('');
      onSaved?.();
      onClose();
    } catch {
      setError('删除失败，请重试');
    } finally {
      setSaving(false);
    }
  }, [saving, editingNote, onSaved, onClose]);

  const handleClose = useCallback(() => {
    setError(null);
    setConfirmDelete(false);
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
          backgroundColor: paper.colors.scrim,
          // Lift the whole sheet clear of the keyboard. `paddingBottom` (not a
          // bottom offset) keeps the scrim filling the screen so the backdrop
          // still swallows taps above the sheet while it is raised.
          paddingBottom: keyboardInset,
        }}
      >
        <Pressable style={{ flex: 1 }} onPress={handleClose} accessibilityLabel="关闭" />

        {/* The sheet: a floating page with a grabber; the capture is written
            directly on the page instead of inside a boxed well. */}
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
            subtitle={dateLabel(date)}
            onClose={handleClose}
          />

          {/* The sheet is the page: the capture is written straight onto it,
              with no tinted well or rule between the heading and the words. */}
          <YanjiTextInput
            value={text}
            onChangeText={next => {
              setText(next);
              if (!editingNote) captureDraft.current = next;
            }}
            placeholder="一个想法、一处卡点、一点收获…"
            multiline
            variant="page"
            accessibilityLabel="记录内容"
            style={{ minHeight: 152, maxHeight: 232, marginTop: YanjiSpacing.md }}
          />

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

          {/* One footer row carries every action: the quiet destructive text
              on the left while editing, the save pill on the right. The old
              hairline plus full-width button turned the page back into a form. */}
          <View
            style={{
              flexDirection: 'row',
              alignItems: 'center',
              justifyContent: editingNote ? 'space-between' : 'flex-end',
              gap: YanjiSpacing.md,
              marginTop: YanjiSpacing.md,
            }}
          >
            {editingNote ? (
              confirmDelete ? (
                <Text style={[paper.typography.captionMeta, { color: paper.colors.secondary }]}>
                  删除后无法恢复
                </Text>
              ) : (
                <Pressable
                  onPress={() => setConfirmDelete(true)}
                  disabled={saving}
                  accessibilityRole="button"
                  accessibilityLabel="删除这条记录"
                  style={({ pressed }) => ({
                    minHeight: paper.layout.rowMinHeight,
                    minWidth: paper.layout.rowMinHeight,
                    justifyContent: 'center',
                    opacity: pressed ? 0.6 : 1,
                  })}
                >
                  <Text style={[paper.typography.bodyPrimary, { color: paper.colors.danger }]}>
                    删除
                  </Text>
                </Pressable>
              )
            ) : null}

            {confirmDelete ? (
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.sm }}>
                <Pressable
                  onPress={() => setConfirmDelete(false)}
                  disabled={saving}
                  accessibilityRole="button"
                  accessibilityLabel="取消删除"
                  style={({ pressed }) => ({
                    minHeight: paper.layout.rowMinHeight,
                    justifyContent: 'center',
                    paddingHorizontal: YanjiSpacing.md,
                    opacity: pressed ? 0.6 : 1,
                  })}
                >
                  <Text style={[paper.typography.bodyPrimary, { color: paper.colors.secondary }]}>
                    取消
                  </Text>
                </Pressable>
                <Pressable
                  onPress={handleDelete}
                  disabled={saving}
                  accessibilityRole="button"
                  accessibilityLabel="确认删除这条记录"
                  style={({ pressed }) => ({
                    minHeight: paper.layout.rowMinHeight,
                    justifyContent: 'center',
                    paddingHorizontal: YanjiSpacing.md,
                    opacity: pressed ? 0.6 : 1,
                  })}
                >
                  <Text
                    style={[
                      paper.typography.bodyPrimary,
                      { color: paper.colors.danger, fontWeight: '600' },
                    ]}
                  >
                    {saving ? '删除中…' : '确认删除'}
                  </Text>
                </Pressable>
              </View>
            ) : (
              <YanjiPrimaryButton
                icon="check"
                label={saving ? '保存中' : '保存'}
                onPress={handleSave}
                disabled={saving}
              />
            )}
          </View>
        </View>
      </View>
    </Modal>
  );
}
