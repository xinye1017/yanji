/**
 * Yanji "Record Moment" — the shared lightweight capture sheet.
 *
 * Behaviour contract (E2E Feature 15/16):
 * - Plain text plus the category choice. No title, mood or rating fields.
 * - Saving never pauses or disturbs a running focus session.
 * - Empty / whitespace-only input is rejected.
 * - On success the sheet closes immediately; on failure the text is preserved.
 * - The note is auto-associated with the current date and, when present, the
 *   active focus session id.
 *
 * Category contract:
 * - The decision model only ever makes a *suggestion*. While the user has not
 *   touched the 灵感/疑点 control its answer preselects the value; once they have,
 *   their choice wins and the answer is discarded.
 * - This matters because classification fails closed to 灵感 when no API key is
 *   configured or the request times out. Without this control that silent
 *   fallback would become an uncorrectable fact about the record.
 * - The pending choice travels with the unsaved capture draft, so closing and
 *   reopening the sheet cannot quietly hand the decision back to the model.
 * - Editing writes nothing until 保存: content and category commit together.
 */

import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Modal, Pressable, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useReducedMotion } from 'react-native-reanimated';
import { NoteCategory, YanjiDataNative, YanjiTimerNative } from '../bridge';
import { classifyMoment } from '../services/decisionClient';
import {
  YanjiPaperSegmentedControl,
  YanjiPaperSheetHeading,
  YanjiPrimaryButton,
  YanjiTextInput,
  useYanjiKeyboardInset,
} from './YanjiUI';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing } from '../theme/tokens';
import { parseIsoDate, weekdayLabelOf } from '../state/settingsViewLogic';

const CATEGORY_OPTIONS: readonly { value: NoteCategory; label: string }[] = [
  { value: 'INSPIRATION', label: '灵感' },
  { value: 'DOUBT', label: '疑点' },
];

/** The sheet's date stamp: `2026-10-20` → `10月20日 · 周二`. */
function dateLabel(iso: string): string {
  const parts = parseIsoDate(iso);
  const weekday = weekdayLabelOf(iso);
  return parts && weekday ? `${parts.month}月${parts.day}日 ${weekday}` : iso;
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
   *
   * `category` is required on purpose: without the stored value the sheet cannot
   * tell "the user left it alone" from "the user picked 灵感", and a missing
   * category would silently rewrite a doubt into an inspiration on save.
   */
  editingNote?: { id: string; content: string; category: NoteCategory } | null;
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
  const [category, setCategory] = useState<NoteCategory>('INSPIRATION');
  // "The user has decided" is the switch that stops the model from overwriting
  // the control: before it flips, the model's answer preselects the value; after
  // it, the answer is discarded. It survives closing the sheet via the draft ref.
  const [categoryChosen, setCategoryChosen] = useState(false);
  const captureDraftCategory = useRef<{ category: NoteCategory; chosen: boolean }>({
    category: 'INSPIRATION',
    chosen: false,
  });

  // Editing starts from the saved note; new captures keep a separate unsaved
  // draft across closing the sheet and switching mounted tabs.
  useEffect(() => {
    if (!visible) return;
    setText(editingNote?.content ?? captureDraft.current);
    setCategory(editingNote ? editingNote.category : captureDraftCategory.current.category);
    // A saved category is already the user's decision, so editing never lets a
    // later model answer move it.
    setCategoryChosen(editingNote ? true : captureDraftCategory.current.chosen);
    setError(null);
    setConfirmDelete(initialConfirmDelete);
  }, [visible, editingNote, initialConfirmDelete]);

  const chooseCategory = useCallback((next: NoteCategory) => {
    setCategory(next);
    setCategoryChosen(true);
    if (!editingNote) captureDraftCategory.current = { category: next, chosen: true };
  }, [editingNote]);

  const handleSave = useCallback(async () => {
    if (saving) return;
    const content = text.trim();
    if (content.length === 0) {
      setError('写点什么再保存吧');
      return;
    }
    setSaving(true);
    setError(null);
    // Which write failed decides the message: after the content is committed only
    // the category is still outstanding, and saying "保存失败" then would imply
    // the text was lost too.
    let stage: 'content' | 'category' = 'content';
    try {
      if (editingNote) {
        await YanjiDataNative.updateNote(editingNote.id, content);
        // Content is already committed at this point, so a failed category write
        // has to say so instead of reporting a save that did not fully happen.
        // Both calls are idempotent, so retrying is safe.
        if (category !== editingNote.category) {
          stage = 'category';
          await YanjiDataNative.setNoteCategory(editingNote.id, category);
        }
      } else {
        // Bind to the active session when one exists; the timer keeps running.
        // The session lookup and the classification request are started together
        // so the sheet waits for one round trip instead of two in a row.
        const suggestion = categoryChosen ? null : classifyMoment(content).catch(() => null);
        let sessionId: string | null = null;
        try {
          const session = await YanjiTimerNative.getActiveSession();
          sessionId = session?.sessionId ?? null;
        } catch {
          sessionId = null;
        }
        // 决策模型只给建议：用户点过「灵感 / 疑点」就以用户为准，
        // 模型失败时静默回落灵感，但保存后仍可在菜单或编辑里纠正。
        const resolvedCategory = categoryChosen
          ? category
          : ((await suggestion) ?? 'INSPIRATION');
        await YanjiDataNative.saveQuickNote(content, date, sessionId, resolvedCategory);
      }
      if (!editingNote) {
        captureDraft.current = '';
        captureDraftCategory.current = { category: 'INSPIRATION', chosen: false };
      }
      setText('');
      setCategory('INSPIRATION');
      setCategoryChosen(false);
      onSaved?.();
      onClose();
    } catch {
      // Preserve the user's text so nothing is lost on failure.
      setError(stage === 'category' ? '分类未保存，请重试' : '保存失败，内容已保留');
    } finally {
      setSaving(false);
    }
  }, [saving, text, date, onClose, onSaved, editingNote, category, categoryChosen]);

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

          {/* No date stamp: this sheet always writes to the day the page is
              showing, so printing it restated the context the reader arrived with. */}
          <YanjiPaperSheetHeading
            title={editingNote ? '编辑记录' : '记录此刻'}
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

          {/* The category row sits under the words, not above them: the capture
              is the page's subject, this is one quiet decision about it. The hint
              only shows while the model is still allowed to decide, and it says so
              — once the user has tapped, the row needs no explanation. */}
          <View style={{ marginTop: YanjiSpacing.md }}>
            <YanjiPaperSegmentedControl
              value={category}
              options={CATEGORY_OPTIONS}
              onChange={chooseCategory}
              accessibilityLabel="记录分类"
              disabled={saving}
            />
            {!categoryChosen ? (
              <Text style={[paper.typography.captionMeta, { color: paper.colors.secondary, marginTop: YanjiSpacing.xs }]}>
                保存时由决策模型判定，你可以直接选择
              </Text>
            ) : null}
          </View>

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
