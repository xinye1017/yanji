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
import {
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  Text,
  TextInput,
  View,
} from 'react-native';
import { YanjiDataNative } from '../bridge';
import type { Subject } from '../bridge';
import { YanjiCard, YanjiPrimaryButton, YanjiSectionHeader } from './YanjiUI';
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

  const chip = (
    label: string,
    active: boolean,
    onPress: () => void
  ): React.JSX.Element => (
    <Pressable
      key={label}
      onPress={onPress}
      accessibilityRole="button"
      accessibilityState={{ selected: active }}
      style={{
        paddingVertical: 8,
        paddingHorizontal: 14,
        borderRadius: YanjiRadius.full,
        marginRight: YanjiSpacing.sm,
        marginBottom: YanjiSpacing.sm,
        backgroundColor: active ? theme.colors.accentSoft : theme.colors.bgElevated,
      }}
    >
      <Text
        style={{
          color: active ? theme.colors.accentPrimary : theme.colors.textSecondary,
          fontSize: 14,
          fontWeight: active ? '600' : '400',
        }}
      >
        {label}
      </Text>
    </Pressable>
  );

  const stepStyle = {
    width: 36,
    height: 36,
    borderRadius: YanjiRadius.full,
    alignItems: 'center' as const,
    justifyContent: 'center' as const,
    backgroundColor: theme.colors.bgElevated,
  };

  return (
    <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        style={{ flex: 1, backgroundColor: theme.colors.bgPrimary }}
      >
        <ScrollView
          contentContainerStyle={{
            padding: YanjiSpacing.xl,
            paddingBottom: YanjiSpacing.xxl,
          }}
        >
          <View
            style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}
          >
            <Text style={{ color: theme.colors.textPrimary, fontSize: 24, fontWeight: '700' }}>
              添加任务
            </Text>
            <Pressable
              onPress={onClose}
              accessibilityRole="button"
              accessibilityLabel="取消"
              style={{ padding: 8 }}
            >
              <Text style={{ color: theme.colors.textSecondary, fontSize: 15 }}>取消</Text>
            </Pressable>
          </View>

          <YanjiSectionHeader title="任务" />
          <YanjiCard>
            <TextInput
              value={title}
              onChangeText={setTitle}
              placeholder="今天要做什么"
              placeholderTextColor={theme.colors.textTertiary}
              style={{
                backgroundColor: theme.colors.bgElevated,
                borderRadius: YanjiRadius.md,
                padding: YanjiSpacing.md,
                color: theme.colors.textPrimary,
                fontSize: 15,
              }}
            />
          </YanjiCard>

          <YanjiSectionHeader title="学科" />
          <YanjiCard>
            {selectedCategory ? (
              <>
                <View
                  style={{
                    flexDirection: 'row',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                  }}
                >
                  <Text
                    style={{ color: theme.colors.textPrimary, fontSize: 15, fontWeight: '600' }}
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
                    style={{ padding: 6 }}
                  >
                    <Text style={{ color: theme.colors.accentPrimary, fontSize: 13 }}>更换</Text>
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
                    {children.map(child =>
                      chip(child.name, child.id === subjectId, () => setSubjectId(child.id))
                    )}
                  </View>
                ) : (
                  <Text
                    style={{ color: theme.colors.textTertiary, fontSize: 13, marginTop: YanjiSpacing.sm }}
                  >
                    该学科没有子类
                  </Text>
                )}
              </>
            ) : (
              <View style={{ flexDirection: 'row', flexWrap: 'wrap' }}>
                {categories.map(category =>
                  chip(category.name, false, () => pickCategory(category))
                )}
                {categories.length === 0 ? (
                  <Text style={{ color: theme.colors.textTertiary, fontSize: 13 }}>暂无可用学科</Text>
                ) : null}
              </View>
            )}
          </YanjiCard>

          <YanjiSectionHeader title="时长" />
          <YanjiCard>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap' }}>
              {DURATION_PRESETS.map(preset =>
                chip(`${preset} 分钟`, preset === minutes, () => setMinutes(preset))
              )}
            </View>
            <View
              style={{ flexDirection: 'row', alignItems: 'center', marginTop: YanjiSpacing.md }}
            >
              <Text style={{ flex: 1, color: theme.colors.textSecondary, fontSize: 13 }}>自定义</Text>
              <Pressable
                onPress={() => setMinutes(m => Math.max(MIN_MINUTES, m - STEP_MINUTES))}
                accessibilityRole="button"
                accessibilityLabel="减少五分钟"
                style={stepStyle}
              >
                <Text style={{ color: theme.colors.textPrimary, fontSize: 16 }}>−</Text>
              </Pressable>
              <Text
                style={{
                  color: theme.colors.textPrimary,
                  fontSize: 15,
                  fontWeight: '600',
                  marginHorizontal: YanjiSpacing.md,
                  minWidth: 64,
                  textAlign: 'center',
                }}
              >
                {minutes} 分钟
              </Text>
              <Pressable
                onPress={() => setMinutes(m => Math.min(MAX_MINUTES, m + STEP_MINUTES))}
                accessibilityRole="button"
                accessibilityLabel="增加五分钟"
                style={stepStyle}
              >
                <Text style={{ color: theme.colors.textPrimary, fontSize: 16 }}>＋</Text>
              </Pressable>
            </View>
          </YanjiCard>

          {error ? (
            <Text style={{ color: theme.colors.danger, fontSize: 13, marginTop: YanjiSpacing.md }}>
              {error}
            </Text>
          ) : null}

          <View style={{ marginTop: YanjiSpacing.xl }}>
            <YanjiPrimaryButton
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
