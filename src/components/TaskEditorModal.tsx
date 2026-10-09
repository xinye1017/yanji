/**
 * Yanji task editor — one layer for adding today's task.
 *
 * Contract (user brief §03): "提供轻量的添加、编辑和删除操作",
 * "添加任务尽量在单个弹层中完成", "学科选择采用层级选择器，不平铺全部子类".
 *
 * Scope honesty: the bridge exposes `createTask` / `deleteTask` / `toggleTask`
 * and nothing else — there is no `updateTask`, and `StudyTaskDao` has no update
 * beyond `setCompleted`. So this layer ADDS a task; deletion and completion
 * toggling live in the Today task row. Renaming or re-planning an existing task
 * is deliberately not offered: the only way to express it with the current
 * bridge (delete + recreate) would change the task id and orphan the focus
 * time already accumulated against it.
 *
 * "不限时" is likewise not offered for a task: `StudyTask.plannedMinutes` is a
 * non-null Int and the native `createTask` coerces it to at least 1, so there
 * is no value that means "no plan". Storing a fabricated default would make the
 * task row lie about what the user chose.
 */

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { KeyboardAvoidingView, Modal, Platform, Pressable, ScrollView, Text, View } from 'react-native';
import { YanjiDataNative } from '../bridge';
import type { Subject } from '../bridge';
import {
  YanjiBreathButton,
  YanjiChip,
  YanjiHairline,
  YanjiIcon,
  YanjiIconButton,
  YanjiStepper,
  YanjiTextInput,
} from './YanjiUI';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiRadius, YanjiSpacing } from '../theme/tokens';

const DURATION_PRESETS = [15, 25, 45, 60, 90] as const;
const MIN_MINUTES = 5;
const MAX_MINUTES = 180;
const STEP_MINUTES = 5;
const DEFAULT_MINUTES = 45;

export interface TaskEditorModalProps {
  visible: boolean;
  /** ISO date `yyyy-MM-dd` the new task belongs to. */
  date: string;
  onClose: () => void;
  onSaved?: () => void;
}

export function TaskEditorModal({
  visible,
  date,
  onClose,
  onSaved,
}: TaskEditorModalProps): React.JSX.Element {
  const theme = useYanjiTheme();
  const [subjects, setSubjects] = useState<Subject[]>([]);
  const [title, setTitle] = useState('');
  const [categoryId, setCategoryId] = useState<string | null>(null);
  const [subjectId, setSubjectId] = useState<string | null>(null);
  const [minutes, setMinutes] = useState(DEFAULT_MINUTES);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!visible) return;
    let cancelled = false;
    setTitle('');
    setCategoryId(null);
    setSubjectId(null);
    setMinutes(DEFAULT_MINUTES);
    setError(null);
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
  }, [visible]);

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
    if (!selectedSubject) {
      setError('先选择学科');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await YanjiDataNative.createTask(
        date,
        selectedSubject.id,
        selectedSubject.name,
        cleanTitle,
        minutes
      );
      onSaved?.();
      onClose();
    } catch {
      setError('保存失败，请重试');
    } finally {
      setSaving(false);
    }
  }, [date, minutes, onClose, onSaved, selectedSubject, title]);

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        style={{ flex: 1, backgroundColor: theme.colors.bgPrimary }}
      >
        <ScrollView
          contentContainerStyle={{
            paddingHorizontal: YanjiSpacing.page,
            paddingTop: YanjiSpacing.lg,
            paddingBottom: YanjiSpacing.xxl,
          }}
        >
          <View
            style={{
              flexDirection: 'row',
              justifyContent: 'space-between',
              alignItems: 'center',
            }}
          >
            <Text
              style={[
                theme.typography.pageTitle,
                { color: theme.colors.textPrimary, letterSpacing: -0.4 },
              ]}
            >
              添加任务
            </Text>
            <YanjiIconButton
              icon="close"
              accessibilityLabel="取消"
              onPress={onClose}
              tone="filled"
            />
          </View>

          {/*
            One sheet, hairline-divided sections, instead of three separate
            cards: the whole point of "单个弹层中完成" is that the layer reads as
            a single page of paper, not as a stack of boxes.
          */}
          <View
            style={{
              backgroundColor: theme.colors.bgSurface,
              borderRadius: YanjiRadius.xl,
              marginTop: YanjiSpacing.lg,
              paddingHorizontal: YanjiSpacing.lg,
            }}
          >
            <Field label="任务">
              <YanjiTextInput
                value={title}
                onChangeText={setTitle}
                placeholder="今天要做什么"
                accessibilityLabel="任务名称"
              />
            </Field>

            <YanjiHairline />

            <Field label="学科">
              {selectedCategory ? (
                <>
                  <View
                    style={{
                      flexDirection: 'row',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                    }}
                  >
                    <Text style={{ color: theme.colors.textPrimary, fontSize: 15, fontWeight: '600' }}>
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
                        opacity: pressed ? 0.6 : 1,
                      })}
                    >
                      <YanjiIcon name="reset" size={13} color={theme.colors.accentPrimary} />
                      <Text
                        style={{
                          color: theme.colors.accentPrimary,
                          fontSize: 13,
                          marginLeft: 4,
                        }}
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
                        theme.typography.caption,
                        { color: theme.colors.textTertiary, marginTop: YanjiSpacing.sm },
                      ]}
                    >
                      该学科没有子类
                    </Text>
                  )}
                </>
              ) : (
                <View style={{ flexDirection: 'row', flexWrap: 'wrap' }}>
                  {categories.map(category => (
                    <YanjiChip
                      key={category.id}
                      label={category.name}
                      onPress={() => pickCategory(category)}
                    />
                  ))}
                  {categories.length === 0 ? (
                    <Text style={{ color: theme.colors.textTertiary, fontSize: 13 }}>
                      暂无可用学科
                    </Text>
                  ) : null}
                </View>
              )}
            </Field>

            <YanjiHairline />

            <Field label="时长">
              <View style={{ flexDirection: 'row', flexWrap: 'wrap' }}>
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
            </Field>
          </View>

          {error ? (
            <Text
              style={[
                theme.typography.caption,
                { color: theme.colors.danger, marginTop: YanjiSpacing.md },
              ]}
            >
              {error}
            </Text>
          ) : null}

          <View style={{ marginTop: YanjiSpacing.xl }}>
            <YanjiBreathButton
              icon="check"
              label={saving ? '保存中' : '保存任务'}
              onPress={handleSave}
              disabled={saving}
            />
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </Modal>
  );
}

/** One labelled field inside the task-editor sheet. */
function Field({
  label,
  children,
}: {
  label: string;
  children: React.ReactNode;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  return (
    <View style={{ paddingVertical: YanjiSpacing.lg }}>
      <Text
        style={[
          theme.typography.label,
          { color: theme.colors.textTertiary, marginBottom: YanjiSpacing.sm },
        ]}
      >
        {label}
      </Text>
      {children}
    </View>
  );
}
