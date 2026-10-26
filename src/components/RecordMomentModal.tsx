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
 * - Both new captures and edits default to 'AUTO', so the model runs on save
 *   unless the user has explicitly picked 灵感 or 疑点.
 * - While in the modal (new or editing), tapping 'AUTO' triggers immediate
 *   classification on the current content and displays the result.
 * - Explicit user selection of 'INSPIRATION' or 'DOUBT' commits that category directly.
 * - A new capture falls back to 'INSPIRATION' when the model gives no answer.
 *   An edit keeps the note's stored category in that case: silently rewriting a
 *   doubt the user set by hand would be worse than leaving it untouched.
 * - Editing writes nothing until 保存: content and category commit together.
 */

import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Image, Modal, Pressable, StyleSheet, Text, View } from 'react-native';
import Svg, { Defs, LinearGradient, Rect, Stop } from 'react-native-svg';
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

export interface EditingNote {
  id: string;
  content: string;
  category: NoteCategory;
  isFavorite?: boolean;
  imageUri?: string | null;
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
  editingNote?: EditingNote | null;
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
  const [isFavorite, setIsFavorite] = useState(false);
  const captureDraft = useRef('');
  const [categoryOption, setCategoryOption] = useState<CaptureCategoryOption>('AUTO');
  // The pending choice alone says whether the user has decided: anything but
  // 'AUTO' *is* an explicit pick, so no separate "chosen" flag can drift from it.
  const captureDraftCategory = useRef<CaptureCategoryOption>('AUTO');
  const [imageUri, setImageUri] = useState<string | null>(null);
  const captureDraftImageUri = useRef<string | null>(null);

  // Editing starts from the saved note; new captures keep a separate unsaved
  // draft across closing the sheet and switching mounted tabs.
  useEffect(() => {
    if (!visible) return;
    setText(editingNote?.content ?? captureDraft.current);
    // An edit always starts at 智能识别: the stored category is what the last
    // save decided, not a decision the user is making now. The new-capture
    // draft's pending choice must not leak into an edit either.
    setCategoryOption(editingNote ? 'AUTO' : captureDraftCategory.current);
    setIsFavorite(Boolean(editingNote?.isFavorite));
    setImageUri(editingNote?.imageUri ?? captureDraftImageUri.current);
    setError(null);
    setAiFeedback(null);
    setPredictedCategory(null);
    setIdentifying(false);
    setConfirmDelete(initialConfirmDelete);
  }, [visible, editingNote, initialConfirmDelete]);

  const chooseCategory = useCallback(async (next: CaptureCategoryOption) => {
    setCategoryOption(next);
    setError(null);
    if (!editingNote) {
      captureDraftCategory.current = next;
    }
    if (next === 'AUTO') {
      const content = text.trim();
      if (content.length > 0) {
        setIdentifying(true);
        try {
          const resolved = await classifyMoment(content).catch(() => null);
          // `null` means the model gave no answer at all. Saying what it
          // "identified" then would be inventing a result.
          setPredictedCategory(resolved);
          setAiFeedback(
            resolved === null
              ? '识别超时，保存时将重试'
              : `已智能识别为：${resolved === 'INSPIRATION' ? '灵感' : '疑点'}`
          );
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

  const handleToggleFavorite = useCallback(async () => {
    const next = !isFavorite;
    setIsFavorite(next);
    if (editingNote) {
      try {
        await YanjiDataNative.toggleFavoriteNote(editingNote.id);
      } catch {
        setIsFavorite(!next);
        setError('收藏未保存，请重试');
      }
    }
  }, [isFavorite, editingNote]);

  const handleTakePhoto = useCallback(async () => {
    if (saving || identifying) return;
    setError(null);
    try {
      const uri = await YanjiDataNative.takePhoto();
      if (uri) {
        setImageUri(uri);
        if (!editingNote) captureDraftImageUri.current = uri;
      }
    } catch {
      setError('拍照失败，请重试');
    }
  }, [saving, identifying, editingNote]);

  const handleRemovePhoto = useCallback(() => {
    setImageUri(null);
    if (!editingNote) captureDraftImageUri.current = null;
  }, [editingNote]);

  const handleSave = useCallback(async () => {
    if (saving || identifying) return;
    const content = text.trim();
    if (content.length === 0) {
      setError('写点什么再保存吧');
      return;
    }
    setSaving(true);
    setError(null);
    let stage: 'content' | 'category' = 'content';
    try {
      // A tap on 智能识别 classifies the text as it stood at that moment, and
      // typing discards that answer — so anything still held here describes the
      // exact text being saved. Without one, the model answers the commit.
      let suggestion = predictedCategory;
      if (categoryOption === 'AUTO' && suggestion === null) {
        suggestion = await classifyMoment(content).catch(() => null);
      }
      // No answer: a new capture keeps the bridge's 灵感 fallback, but an edit
      // keeps the category the note already has — a timeout must never rewrite
      // a doubt the user set by hand.
      const resolvedCategory: NoteCategory = categoryOption === 'AUTO'
        ? (suggestion ?? editingNote?.category ?? 'INSPIRATION')
        : categoryOption;

      if (editingNote) {
        await YanjiDataNative.updateNote(editingNote.id, content);
        if (resolvedCategory !== editingNote.category) {
          stage = 'category';
          await YanjiDataNative.setNoteCategory(editingNote.id, resolvedCategory);
        }
      } else {
        let sessionId: string | null = null;
        try {
          const session = await YanjiTimerNative.getActiveSession();
          sessionId = session?.sessionId ?? null;
        } catch {
          sessionId = null;
        }
        const created = await YanjiDataNative.saveQuickNote(content, date, sessionId, resolvedCategory, imageUri);
        if (isFavorite && created?.id) {
          await YanjiDataNative.toggleFavoriteNote(created.id).catch(() => {});
        }
      }
      if (!editingNote) {
        captureDraft.current = '';
        captureDraftCategory.current = 'AUTO';
        captureDraftImageUri.current = null;
      }
      setImageUri(null);
      setText('');
      setCategoryOption('AUTO');
      setPredictedCategory(null);
      setAiFeedback(null);
      setIsFavorite(false);
      onSaved?.();
      onClose();
    } catch {
      setError(stage === 'category' ? '分类未保存，请重试' : '保存失败，内容已保留');
    } finally {
      setSaving(false);
    }
  }, [saving, identifying, text, categoryOption, predictedCategory, isFavorite, editingNote, date, imageUri, onClose, onSaved]);

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

  const sheetBackground = paper.colors.surface;
  const sheetBorderColor = paper.colors.hairline;
  const scrimColor = paper.colors.scrim;

  // Header trailing component: Camera, Bookmark and Trash icons
  const headerRight = (
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.xs }}>
      {/* Camera Icon button */}
      <Pressable
        onPress={handleTakePhoto}
        disabled={saving || identifying}
        accessibilityRole="button"
        accessibilityLabel="拍照封面"
        hitSlop={8}
        style={({ pressed }) => ({
          width: 34,
          height: 34,
          alignItems: 'center',
          justifyContent: 'center',
          opacity: pressed ? 0.6 : 1,
        })}
      >
        <YanjiIcon
          name="camera"
          size={19}
          color={imageUri ? paper.colors.brand : paper.colors.secondary}
        />
      </Pressable>

      {/* Bookmark Icon button */}
      <Pressable
        onPress={handleToggleFavorite}
        disabled={saving || identifying}
        accessibilityRole="button"
        accessibilityLabel={isFavorite ? '取消收藏' : '收藏记录'}
        hitSlop={8}
        style={({ pressed }) => ({
          width: 34,
          height: 34,
          alignItems: 'center',
          justifyContent: 'center',
          opacity: pressed ? 0.6 : 1,
        })}
      >
        <YanjiIcon
          name={isFavorite ? 'bookmark' : 'bookmarkOutline'}
          size={19}
          color={isFavorite ? paper.colors.amber : paper.colors.secondary}
        />
      </Pressable>

      {/* Trash (Delete) Icon button */}
      {editingNote ? (
        <Pressable
          onPress={() => setConfirmDelete(true)}
          disabled={saving || identifying}
          accessibilityRole="button"
          accessibilityLabel="删除这条记录"
          hitSlop={8}
          style={({ pressed }) => ({
            width: 34,
            height: 34,
            alignItems: 'center',
            justifyContent: 'center',
            opacity: pressed ? 0.6 : 1,
          })}
        >
          <YanjiIcon name="delete" size={19} color={paper.colors.secondary} />
        </Pressable>
      ) : null}
    </View>
  );

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
              overflow: 'hidden',
              paddingHorizontal: paper.layout.pageGutter,
              paddingTop: YanjiSpacing.md,
              paddingBottom: YanjiSpacing.md + insets.bottom,
            },
            theme.shadow.floating(theme.isDark),
          ]}
        >
          {imageUri ? (
            <View
              style={{
                position: 'absolute',
                top: 0,
                left: 0,
                right: 0,
                height: 150,
                overflow: 'hidden',
              }}
              pointerEvents="box-none"
            >
              <Image
                source={{ uri: imageUri }}
                style={{ width: '100%', height: 150 }}
                resizeMode="cover"
              />
              <Svg height={150} width="100%" style={StyleSheet.absoluteFill} pointerEvents="none">
                <Defs>
                  <LinearGradient id="paperFadeHeader" x1="0" y1="0" x2="0" y2="1">
                    <Stop offset="0%" stopColor={sheetBackground} stopOpacity="0" />
                    <Stop offset="35%" stopColor={sheetBackground} stopOpacity="0.12" />
                    <Stop offset="75%" stopColor={sheetBackground} stopOpacity="0.8" />
                    <Stop offset="100%" stopColor={sheetBackground} stopOpacity="1" />
                  </LinearGradient>
                </Defs>
                <Rect x="0" y="0" width="100%" height="100%" fill="url(#paperFadeHeader)" />
              </Svg>
              <View
                style={{
                  position: 'absolute',
                  top: YanjiSpacing.sm,
                  left: paper.layout.pageGutter,
                  flexDirection: 'row',
                  gap: YanjiSpacing.xs,
                  zIndex: 10,
                }}
              >
                <Pressable
                  onPress={handleTakePhoto}
                  disabled={saving || identifying}
                  style={({ pressed }) => ({
                    paddingHorizontal: 8,
                    paddingVertical: 2.5,
                    borderRadius: paper.radius.pill,
                    backgroundColor: theme.isDark ? 'rgba(30, 28, 25, 0.72)' : 'rgba(255, 255, 255, 0.85)',
                    borderWidth: 1,
                    borderColor: paper.colors.hairline,
                    opacity: pressed ? 0.7 : 1,
                  })}
                  accessibilityRole="button"
                  accessibilityLabel="重拍照片"
                >
                  <Text style={[paper.typography.captionMeta, { color: paper.colors.ink, fontSize: 11 }]}>重拍</Text>
                </Pressable>
                <Pressable
                  onPress={handleRemovePhoto}
                  disabled={saving || identifying}
                  style={({ pressed }) => ({
                    paddingHorizontal: 8,
                    paddingVertical: 2.5,
                    borderRadius: paper.radius.pill,
                    backgroundColor: theme.isDark ? 'rgba(30, 28, 25, 0.72)' : 'rgba(255, 255, 255, 0.85)',
                    borderWidth: 1,
                    borderColor: paper.colors.hairline,
                    opacity: pressed ? 0.7 : 1,
                  })}
                  accessibilityRole="button"
                  accessibilityLabel="移除照片"
                >
                  <Text style={[paper.typography.captionMeta, { color: paper.colors.danger, fontSize: 11 }]}>移除</Text>
                </Pressable>
              </View>
            </View>
          ) : null}

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

          {/* Heading with title "记录此刻", smaller refined font size, Bookmark & Trash in top right */}
          <YanjiPaperSheetHeading
            title="记录此刻"
            titleStyle={{ fontSize: 18, lineHeight: 24, fontWeight: '700' }}
            right={headerRight}
            disabled={saving || identifying}
          />

          {/* Compact text input using serif journal face */}
          <YanjiTextInput
            value={text}
            onChangeText={next => {
              setText(next);
              if (!editingNote) captureDraft.current = next;
              setAiFeedback(null);
              // The prediction describes the text it was asked about. Once the
              // words change it no longer does, so saving must ask again rather
              // than commit an answer about a different sentence.
              setPredictedCategory(null);
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
                AI 正在分析意图…
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

          {/* For editing: quiet destructive delete confirmation row when delete is tapped */}
          {confirmDelete && editingNote ? (
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'flex-end', gap: YanjiSpacing.sm, marginBottom: YanjiSpacing.xs }}>
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
                  borderRadius: paper.radius.pill,
                  backgroundColor: paper.colors.subtle,
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
                  borderRadius: paper.radius.pill,
                }}
              />
            </View>
          </View>
        </View>
      </View>
    </Modal>
  );
}
