/**
 * Yanji task editor — one layer for adding today's task.
 *
 * Contract (user brief §03): "提供轻量的添加、编辑和删除操作",
 * "添加任务尽量在单个弹层中完成", "学科选择采用层级选择器，不平铺全部子类".
 *
 * Create and edit tasks through the native bridge. Editing uses SQL UPDATE in place,
 * preserving the task ID and its linked focus history. Subject stays immutable
 * when editing, so earlier focus session subjects remain truthful.
 *
 * "不限时" is likewise not offered for a task: `StudyTask.plannedMinutes` is a
 * non-null Int and the native `createTask` coerces it to at least 1, so there
 * is no value that means "no plan". Storing a fabricated default would make the
 * task row lie about what the user chose.
 */

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { KeyboardAvoidingView, Modal, Platform, Pressable, ScrollView, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { YanjiDataNative } from '../bridge';
import type { StudyTask, Subject } from '../bridge';
import {
  YanjiBreathButton,
  YanjiChip,
  YanjiEditorialHeader,
  YanjiHairline,
  YanjiIcon,
  YanjiIconButton,
  YanjiPaperSectionHeading,
  YanjiStepper,
  YanjiTextInput,
  useYanjiKeyboardInset,
} from './YanjiUI';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing } from '../theme/tokens';

const DURATION_PRESETS = [15, 25, 45, 60, 90] as const;
const MIN_MINUTES = 5;
const MAX_MINUTES = 180;
const STEP_MINUTES = 5;
const DEFAULT_MINUTES = 45;

interface TaskEditorModalProps {
  visible: boolean;
  /** ISO date `yyyy-MM-dd` the new task belongs to. */
  date: string;
  onClose: () => void;
  onSaved?: () => void;
  editingTask?: StudyTask | null;
}

export function TaskEditorModal({
  visible,
  date,
  onClose,
  onSaved,
  editingTask = null,
}: TaskEditorModalProps): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const insets = useSafeAreaInsets();
  const [subjects, setSubjects] = useState<Subject[]>([]);
  const [title, setTitle] = useState('');
  const [categoryId, setCategoryId] = useState<string | null>(null);
  const [subjectId, setSubjectId] = useState<string | null>(null);
  const [minutes, setMinutes] = useState(DEFAULT_MINUTES);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [confirmDelete, setConfirmDelete] = useState(false);

  // An RN Modal owns a separate Android window. The window does not reliably
  // resize its ScrollView when the IME opens, so reserve actual keyboard-height
  // scroll room: the stepper and save action remain reachable without saving.
  const keyboardHeight = useYanjiKeyboardInset();

  useEffect(() => {
    if (!visible) return;
    let cancelled = false;
    setTitle(editingTask?.title ?? '');
    setCategoryId(null);
    setSubjectId(null);
    setMinutes(editingTask?.plannedMinutes ?? DEFAULT_MINUTES);
    setError(null);
    setConfirmDelete(false);
    YanjiDataNative.getSubjects()
      .then(list => {
        if (!cancelled) setSubjects(list);
      })
      .catch(() => {
        if (!cancelled) setSubjects([]);
      });
    return () => {
      cancelled = true;
    };
  }, [visible, editingTask]);

  const categories = useMemo(
    () => subjects.filter(s => s.isCategory && s.enabled),
    [subjects]
  );
  const children = useMemo(
    () => (categoryId ? subjects.filter(s => s.parentId === categoryId && s.enabled) : []),
    [subjects, categoryId]
  );
  const selectedCategory = useMemo(
    () => categories.find(c => c.id === categoryId) ?? null,
    [categories, categoryId]
  );
  const selectedSubject = useMemo(
    () => subjects.find(s => s.id === subjectId) ?? null,
    [subjects, subjectId]
  );

  const pickCategory = useCallback(
    (category: Subject) => {
      const kids = subjects.filter(s => s.parentId === category.id && s.enabled);
      setCategoryId(category.id);
      // A childless category (英语一 / 政治 / 其他) is itself the subject.
      setSubjectId(kids.length > 0 ? kids[0].id : category.id);
    },
    [subjects]
  );

  const handleSave = useCallback(async () => {
    const cleanTitle = title.trim();
    if (cleanTitle.length === 0) {
      setError('先填写任务名称');
      return;
    }
    if (!editingTask && !selectedSubject) {
      setError('先选择学科');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      if (editingTask) {
        await YanjiDataNative.updateTask(editingTask.id, cleanTitle, minutes);
      } else if (selectedSubject) {
        await YanjiDataNative.createTask(
          date,
          selectedSubject.id,
          selectedSubject.name,
          cleanTitle,
          minutes
        );
      }
      onSaved?.();
      onClose();
    } catch {
      setError('保存失败，请重试');
    } finally {
      setSaving(false);
    }
  }, [date, editingTask, minutes, onClose, onSaved, selectedSubject, title]);

  const handleDelete = useCallback(async () => {
    if (!editingTask) return;
    setSaving(true);
    try {
      await YanjiDataNative.deleteTask(editingTask.id);
      onSaved?.();
      onClose();
    } catch { setError('删除失败，请重试'); }
    finally { setSaving(false); }
  }, [editingTask, onSaved, onClose]);

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        // Keep the entire scroll viewport below the status bar, not merely
        // its initial contents: focused fields must never scroll over system UI.
        style={{
          flex: 1,
          backgroundColor: paper.colors.canvas,
          paddingTop: Math.max(insets.top, YanjiSpacing.lg),
        }}
      >
        <ScrollView
          keyboardShouldPersistTaps="always"
          keyboardDismissMode="none"
          contentContainerStyle={{
            paddingHorizontal: paper.layout.pageGutter,
            // The scroll viewport itself owns the safe-area inset above.
            // Preserve the original heading's extra visual breathing room.
            paddingTop: YanjiSpacing.lg,
            paddingBottom: YanjiSpacing.xxl + keyboardHeight,
          }}
        >
          <YanjiEditorialHeader
            eyebrow={editingTask ? '今日任务' : undefined}
            title={editingTask ? '编辑任务' : '添加任务'}
            subtitle={date}
            trailing={
              <YanjiIconButton
                icon="close"
                accessibilityLabel="取消"
                onPress={onClose}
                tone="filled"
              />
            }
          />

          {/*
            One page of paper divided by hairline rules, instead of a stack of
            cards: the whole point of "单个弹层中完成" is that the layer reads as
            a single sheet, not as three boxes.
          */}
          <YanjiPaperSectionHeading title="任务" />
          <YanjiTextInput
            value={title}
            onChangeText={setTitle}
            placeholder="今天要做什么"
            accessibilityLabel="任务名称"
          />

          <YanjiHairline style={{ marginTop: YanjiSpacing.lg }} />

          <YanjiPaperSectionHeading title="学科" />
          {editingTask ? (
            <Text
              style={[
                paper.typography.bodyPrimary,
                { color: paper.colors.ink, minHeight: paper.layout.rowMinHeight },
              ]}
            >
              {editingTask.subjectName}
            </Text>
          ) : selectedCategory ? (
            <>
              <View
                style={{
                  flexDirection: 'row',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  minHeight: paper.layout.rowMinHeight,
                }}
              >
                <Text
                  style={[
                    paper.typography.bodyPrimary,
                    { color: paper.colors.ink, fontWeight: '600' },
                  ]}
                >
                  {selectedCategory.name}
                </Text>
                <Pressable
                  onPress={() => {
                    setCategoryId(null);
                    setSubjectId(null);
                  }}
                  accessibilityRole="button"
                  accessibilityLabel="更换学科大类"
                  hitSlop={8}
                  style={({ pressed }) => ({
                    flexDirection: 'row',
                    alignItems: 'center',
                    minHeight: paper.layout.rowMinHeight,
                    opacity: pressed ? 0.6 : 1,
                  })}
                >
                  <YanjiIcon name="reset" size={13} color={paper.colors.brandInk} />
                  <Text
                    style={[
                      paper.typography.captionMeta,
                      { color: paper.colors.brandInk, marginLeft: 4 },
                    ]}
                  >
                    更换
                  </Text>
                </Pressable>
              </View>
              {children.length > 0 ? (
                <View
                  style={{
                    flexDirection: 'row',
                    flexWrap: 'wrap',
                    gap: YanjiSpacing.sm,
                    marginTop: YanjiSpacing.md,
                  }}
                >
                  {children.map(child => (
                    <YanjiChip
                      key={child.id}
                      label={child.name}
                      selected={child.id === subjectId}
                      onPress={() => setSubjectId(child.id)}
                    />
                  ))}
                </View>
              ) : (
                <Text
                  style={[
                    paper.typography.captionMeta,
                    { color: paper.colors.secondary, marginTop: YanjiSpacing.sm },
                  ]}
                >
                  该学科没有子类
                </Text>
              )}
            </>
          ) : (
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: YanjiSpacing.sm }}>
              {categories.map(category => (
                <YanjiChip
                  key={category.id}
                  label={category.name}
                  onPress={() => pickCategory(category)}
                />
              ))}
              {categories.length === 0 ? (
                <Text
                  style={[
                    paper.typography.bodyPrimary,
                    { color: paper.colors.secondary },
                  ]}
                >
                  暂无可用学科
                </Text>
              ) : null}
            </View>
          )}

          <YanjiHairline style={{ marginTop: YanjiSpacing.lg }} />

          <YanjiPaperSectionHeading title="时长" />
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: YanjiSpacing.sm }}>
            {DURATION_PRESETS.map(preset => (
              <YanjiChip
                key={preset}
                label={`${preset} 分钟`}
                selected={preset === minutes}
                onPress={() => setMinutes(preset)}
              />
            ))}
          </View>
          <View style={{ marginTop: YanjiSpacing.sm }}>
            <YanjiStepper
              label="自定义时长"
              value={minutes}
              onChange={setMinutes}
              min={MIN_MINUTES}
              max={MAX_MINUTES}
              step={STEP_MINUTES}
              suffix=" 分钟"
            />
          </View>

          {error ? (
            <Text
              style={[
                paper.typography.captionMeta,
                { color: paper.colors.danger, marginTop: YanjiSpacing.md },
              ]}
            >
              {error}
            </Text>
          ) : null}

          <View style={{ marginTop: YanjiSpacing.xl }}>
            <YanjiBreathButton
              icon="check"
              label={saving ? '保存中' : editingTask ? '保存修改' : '保存任务'}
              onPress={handleSave}
              disabled={saving}
            />
          </View>
          {editingTask ? (
            <View style={{ marginTop: YanjiSpacing.md }}>
              {!confirmDelete ? (
                <Pressable
                  onPress={() => setConfirmDelete(true)}
                  accessibilityRole="button"
                  style={({ pressed }) => ({
                    minHeight: paper.layout.rowMinHeight,
                    justifyContent: 'center',
                    alignItems: 'center',
                    opacity: pressed ? 0.6 : 1,
                  })}
                >
                  <Text style={[paper.typography.bodyPrimary, { color: paper.colors.danger }]}>
                    删除这个任务
                  </Text>
                </Pressable>
              ) : (
                <View style={{ flexDirection: 'row', justifyContent: 'space-around' }}>
                  <Pressable
                    onPress={() => setConfirmDelete(false)}
                    accessibilityRole="button"
                    style={({ pressed }) => ({
                      minHeight: paper.layout.rowMinHeight,
                      justifyContent: 'center',
                      paddingHorizontal: YanjiSpacing.md,
                      opacity: pressed ? 0.6 : 1,
                    })}
                  >
                    <Text style={[paper.typography.bodyPrimary, { color: paper.colors.secondary }]}>
                      取消删除
                    </Text>
                  </Pressable>
                  <Pressable
                    onPress={() => void handleDelete()}
                    disabled={saving}
                    accessibilityRole="button"
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
                      确认删除
                    </Text>
                  </Pressable>
                </View>
              )}
            </View>
          ) : null}
        </ScrollView>
      </KeyboardAvoidingView>
    </Modal>
  );
}
