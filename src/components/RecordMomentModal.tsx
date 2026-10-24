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
 * - Three choices: 'AUTO' (智能识别), 'INSPIRATION' (灵感), 'DOUBT' (疑点).
 * - Defaults to 'AUTO'. When saving with 'AUTO', the decision model classifies
 *   the text, failing closed to 'INSPIRATION'.
 * - While in the modal (new or editing), tapping 'AUTO' triggers immediate
 *   classification on the current content and displays the result.
 * - Explicit user selection of 'INSPIRATION' or 'DOUBT' commits that category directly.
 * - Editing writes nothing until 保存: content and category commit together.
 */

import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Modal, Pressable, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useReducedMotion } from 'react-native-reanimated';
import { NoteCategory, YanjiDataNative, YanjiTimerNative } from '../bridge';
import { classifyMoment } from '../services/decisionClient';
import {
  YanjiIcon,
  YanjiPaperSegmentedControl,
  YanjiPaperSegmentedOption,
  YanjiPaperSheetHeading,
  YanjiPrimaryButton,
  YanjiTextInput,
  useYanjiKeyboardInset,
} from './YanjiUI';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing } from '../theme/tokens';
import { parseIsoDate, weekdayLabelOf } from '../state/settingsViewLogic';

export type CaptureCategoryOption = 'AUTO' | 'INSPIRATION' | 'DOUBT';

const CATEGORY_OPTIONS: readonly YanjiPaperSegmentedOption<CaptureCategoryOption>[] = [
  { value: 'AUTO', label: '智能识别', icon: 'sparkles' },
  { value: 'INSPIRATION', label: '灵感', icon: 'inspiration' },
  { value: 'DOUBT', label: '疑点', icon: 'doubt' },
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
  const [identifying, setIdentifying] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [aiFeedback, setAiFeedback] = useState<string | null>(null);
  const [predictedCategory, setPredictedCategory] = useState<NoteCategory | null>(null);
  const captureDraft = useRef('');
  const [categoryOption, setCategoryOption] = useState<CaptureCategoryOption>('AUTO');
  const captureDraftCategory = useRef<{ option: CaptureCategoryOption; chosen: boolean }>({
    option: 'AUTO',
    chosen: false,
  });

  // Editing starts from the saved note; new captures keep a separate unsaved
  // draft across closing the sheet and switching mounted tabs.
  useEffect(() => {
    if (!visible) return;
    setText(editingNote?.content ?? captureDraft.current);
    setCategoryOption(editingNote ? editingNote.category : captureDraftCategory.current.option);
    setError(null);
    setAiFeedback(null);
    setPredictedCategory(null);
    setIdentifying(false);
    setConfirmDelete(initialConfirmDelete);
  }, [visible, editingNote, initialConfirmDelete]);

  const chooseCategory = useCallback(async (next: CaptureCategoryOption) => {
    setCategoryOption(next);
    setError(null);
    const chosen = next !== 'AUTO';
    if (!editingNote) {
      captureDraftCategory.current = { option: next, chosen };
    }
    if (next === 'AUTO') {
      const content = text.trim();
      if (content.length > 0) {
        setIdentifying(true);
        try {
          const resolved = await classifyMoment(content);
          setPredictedCategory(resolved);
          setAiFeedback(`已智能识别为：${resolved === 'INSPIRATION' ? '灵感' : '疑点'}`);
        } catch {
          setAiFeedback('识别超时，保存时将重试');
        } finally {
          setIdentifying(false);
        }
      } else {
        setAiFeedback(null);
        setPredictedCategory(null);
      }
    } else {
      setAiFeedback(null);
      setPredictedCategory(null);
    }
  }, [editingNote, text]);

  const handleSave = useCallback(async () => {
    if (saving || identifying) return;
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
      let resolvedCategory: NoteCategory;
      if (categoryOption === 'AUTO') {
        if (predictedCategory) {
          resolvedCategory = predictedCategory;
        } else {
          try {
            resolvedCategory = await classifyMoment(content);
          } catch {
            resolvedCategory = 'INSPIRATION';
          }
        }
      } else {
        resolvedCategory = categoryOption;
      }

      if (editingNote) {
        await YanjiDataNative.updateNote(editingNote.id, content);
        // Content is already committed at this point, so a failed category write
        // has to say so instead of reporting a save that did not fully happen.
        // Both calls are idempotent, so retrying is safe.
        if (resolvedCategory !== editingNote.category) {
          stage = 'category';
          await YanjiDataNative.setNoteCategory(editingNote.id, resolvedCategory);
        }
      } else {
        // Bind to the active session when one exists; the timer keeps running.
        let sessionId: string | null = null;
        try {
          const session = await YanjiTimerNative.getActiveSession();
          sessionId = session?.sessionId ?? null;
        } catch {
          sessionId = null;
        }
        await YanjiDataNative.saveQuickNote(content, date, sessionId, resolvedCategory);
      }
      if (!editingNote) {
        captureDraft.current = '';
        captureDraftCategory.current = { option: 'AUTO', chosen: false };
      }
      setText('');
      setCategoryOption('AUTO');
      setPredictedCategory(null);
      setAiFeedback(null);
      onSaved?.();
      onClose();
    } catch {
      // Preserve the user's text so nothing is lost on failure.
      setError(stage === 'category' ? '分类未保存，请重试' : '保存失败，内容已保留');
    } finally {
      setSaving(false);
    }
  }, [saving, identifying, text, categoryOption, predictedCategory, editingNote, date, onClose, onSaved]);

  const handleDelete = useCallback(async () => {
    if (saving || identifying || !editingNote) return;
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
  }, [saving, identifying, editingNote, onSaved, onClose]);

  const handleClose = useCallback(() => {
    setError(null);
    setAiFeedback(null);
    setPredictedCategory(null);
    setIdentifying(false);
    setConfirmDelete(false);
    onClose();
  }, [onClose]);

  const paper = theme.paper;
  const insets = useSafeAreaInsets();
  const keyboardInset = useYanjiKeyboardInset();

  // In dark mode, blend smoothly into the dark screen with a deep charcoal tone
  const sheetBackground = theme.isDark ? '#141312' : paper.colors.surface;
  const sheetBorderColor = theme.isDark ? 'rgba(255,255,255,0.08)' : paper.colors.hairline;
  const scrimColor = theme.isDark ? 'rgba(0,0,0,0.65)' : paper.colors.scrim;

  return (
    <Modal visible={visible} transparent animationType={reduceMotion ? 'none' : 'fade'} onRequestClose={handleClose}>
      <View
        style={{
          flex: 1,
          justifyContent: 'flex-end',
          backgroundColor: scrimColor,
          paddingBottom: keyboardInset,
        }}
      >
        <Pressable style={{ flex: 1 }} onPress={handleClose} accessibilityLabel="关闭" />

        {/* The sheet: floating page with a grabber; seamlessly blended background */}
        <View
          style={[
            {
              backgroundColor: sheetBackground,
              borderTopLeftRadius: paper.radius.sheet,
              borderTopRightRadius: paper.radius.sheet,
              borderTopWidth: 1,
              borderColor: sheetBorderColor,
              paddingHorizontal: paper.layout.pageGutter,
              paddingTop: YanjiSpacing.md,
              paddingBottom: YanjiSpacing.md + insets.bottom,
            },
            theme.shadow.floating(theme.isDark),
          ]}
        >
          <View
            style={{
              alignSelf: 'center',
              width: 32,
              height: 4,
              borderRadius: paper.radius.pill,
              backgroundColor: paper.colors.hairline,
              marginBottom: YanjiSpacing.md,
            }}
          />

          {/* Heading with title "记录此刻" */}
          <YanjiPaperSheetHeading
            title="记录此刻"
            disabled={saving || identifying}
          />

          {/* Compact text input using serif journal face */}
          <YanjiTextInput
            value={text}
            onChangeText={next => {
              setText(next);
              if (!editingNote) captureDraft.current = next;
              setAiFeedback(null);
            }}
            placeholder="一个想法、一处卡点、一点收获…"
            multiline
            variant="page"
            accessibilityLabel="记录内容"
            style={{ minHeight: 84, maxHeight: 140, marginTop: YanjiSpacing.xs, marginBottom: 2 }}
          />

          {/* Feedback & status row */}
          <View style={{ minHeight: 18, justifyContent: 'center', marginBottom: YanjiSpacing.xs }}>
            {identifying ? (
              <Text style={[paper.typography.captionMeta, { color: paper.colors.brand }]}>
                ⚡ AI 正在分析意图…
              </Text>
            ) : aiFeedback ? (
              <Text style={[paper.typography.captionMeta, { color: paper.colors.brand }]}>
                ✓ {aiFeedback}
              </Text>
            ) : error ? (
              <Text style={[paper.typography.captionMeta, { color: paper.colors.danger }]}>
                {error}
              </Text>
            ) : null}
          </View>

          {/* For editing: quiet destructive delete button / confirmation row */}
          {editingNote ? (
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'flex-start', marginBottom: YanjiSpacing.xs }}>
              {confirmDelete ? (
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.sm }}>
                  <Text style={[paper.typography.captionMeta, { color: paper.colors.secondary }]}>
                    删除后无法恢复
                  </Text>
                  <Pressable
                    onPress={() => setConfirmDelete(false)}
                    disabled={saving || identifying}
                    accessibilityRole="button"
                    accessibilityLabel="取消删除"
                    style={{ paddingHorizontal: 6, paddingVertical: 2 }}
                  >
                    <Text style={[paper.typography.captionMeta, { color: paper.colors.secondary }]}>
                      取消
                    </Text>
                  </Pressable>
                  <Pressable
                    onPress={handleDelete}
                    disabled={saving || identifying}
                    accessibilityRole="button"
                    accessibilityLabel="确认删除这条记录"
                    style={{ paddingHorizontal: 6, paddingVertical: 2 }}
                  >
                    <Text style={[paper.typography.captionMeta, { color: paper.colors.danger, fontWeight: '600' }]}>
                      {saving ? '删除中…' : '确认删除'}
                    </Text>
                  </Pressable>
                </View>
              ) : (
                <Pressable
                  onPress={() => setConfirmDelete(true)}
                  disabled={saving || identifying}
                  accessibilityRole="button"
                  accessibilityLabel="删除这条记录"
                  style={({ pressed }) => ({
                    paddingVertical: 2,
                    opacity: pressed ? 0.6 : 1,
                  })}
                >
                  <Text style={[paper.typography.captionMeta, { color: paper.colors.danger }]}>
                    删除这条记录
                  </Text>
                </Pressable>
              )}
            </View>
          ) : null}

          {/* Single Toolbar Row: Left Compact Segmented Control | Right Cancel (✕) & Save (✓) */}
          <View
            style={{
              flexDirection: 'row',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: YanjiSpacing.sm,
            }}
          >
            {/* Left: Compact Category Segmented Control */}
            <View style={{ flexShrink: 1 }}>
              <YanjiPaperSegmentedControl
                value={categoryOption}
                options={CATEGORY_OPTIONS}
                onChange={chooseCategory}
                accessibilityLabel="记录分类"
                compact={true}
                disabled={saving || identifying}
              />
            </View>

            {/* Right: Cancel (✕) and Save (✓) button pills */}
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.sm }}>
              {/* Cancel (✕) */}
              <Pressable
                onPress={handleClose}
                disabled={saving || identifying}
                accessibilityRole="button"
                accessibilityLabel="取消"
                hitSlop={6}
                style={({ pressed }) => ({
                  width: 36,
                  height: 36,
                  borderRadius: 18,
                  backgroundColor: theme.isDark ? '#262422' : paper.colors.subtle,
                  alignItems: 'center',
                  justifyContent: 'center',
                  opacity: pressed ? 0.7 : 1,
                })}
              >
                <YanjiIcon name="close" size={15} color={paper.colors.secondary} />
              </Pressable>

              {/* Save (✓) */}
              <YanjiPrimaryButton
                icon="check"
                label=""
                accessibilityLabel="保存"
                onPress={handleSave}
                disabled={saving || identifying}
                style={{
                  width: 36,
                  height: 36,
                  minHeight: 36,
                  paddingHorizontal: 0,
                  paddingVertical: 0,
                  borderRadius: 18,
                }}
              />
            </View>
          </View>
        </View>
      </View>
    </Modal>
  );
}
