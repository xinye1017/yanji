/**
 * Subjects customization sheet — opened from SettingsScreen.
 *
 * Allows viewing and editing focus subject categories and sub-subjects.
 * Renaming a subject synchronizes historical records and study tasks in Room.
 */

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Modal, Pressable, ScrollView, Text, TextInput, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useReducedMotion } from 'react-native-reanimated';
import { YanjiIcon, YanjiIconButton, YanjiPaperSheetHeading } from './YanjiUI';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing } from '../theme/tokens';
import { YanjiDataNative } from '../bridge';
import type { Subject } from '../bridge';

export function SubjectsSheet({
  visible,
  onClose,
}: {
  visible: boolean;
  onClose: () => void;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout, radius } = paper;
  const insets = useSafeAreaInsets();
  const reduceMotion = useReducedMotion();

  const [subjects, setSubjects] = useState<Subject[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [editingId, setEditingId] = useState<string | null>(null);
  const [editName, setEditName] = useState('');
  const [savingId, setSavingId] = useState<string | null>(null);

  const [addingUnderParent, setAddingUnderParent] = useState<string | null>(null);
  const [newSubName, setNewSubName] = useState('');
  const [addingSubSaving, setAddingSubSaving] = useState(false);

  const [addingCategory, setAddingCategory] = useState(false);
  const [newCategoryName, setNewCategoryName] = useState('');
  const [addingCategorySaving, setAddingCategorySaving] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    setError(null);
    void YanjiDataNative.getSubjects()
      .then(list => {
        setSubjects(list);
        setLoading(false);
      })
      .catch(() => {
        setError('获取学科列表失败');
        setLoading(false);
      });
  }, []);

  useEffect(() => {
    if (visible) {
      load();
      setEditingId(null);
      setAddingUnderParent(null);
      setAddingCategory(false);
    }
  }, [visible, load]);

  const groups = useMemo(() => {
    const roots = subjects.filter(
      s => s.parentId === null || !subjects.some(p => p.id === s.parentId)
    ).sort((a, b) => a.sortOrder - b.sortOrder);
    return roots.map(category => ({
      category,
      children: subjects.filter(c => c.parentId === category.id).sort((a, b) => a.sortOrder - b.sortOrder),
    }));
  }, [subjects]);

  const handleStartEdit = (subject: Subject) => {
    setEditingId(subject.id);
    setEditName(subject.name);
    setError(null);
  };

  const handleCancelEdit = () => {
    setEditingId(null);
    setEditName('');
    setError(null);
  };

  const handleSaveRename = async (subjectId: string) => {
    const trimmed = editName.trim();
    if (!trimmed) {
      setError('学科名称不能为空');
      return;
    }
    setSavingId(subjectId);
    setError(null);
    try {
      const ok = await YanjiDataNative.renameSubject(subjectId, trimmed);
      if (!ok) throw new Error('failed');
      setEditingId(null);
      setEditName('');
      const updated = await YanjiDataNative.getSubjects();
      setSubjects(updated);
    } catch {
      setError('保存学科名称失败，请重试');
    } finally {
      setSavingId(null);
    }
  };

  const handleAddSub = async (parentId: string) => {
    const trimmed = newSubName.trim();
    if (!trimmed) {
      setError('子学科名称不能为空');
      return;
    }
    setAddingSubSaving(true);
    setError(null);
    try {
      const created = await YanjiDataNative.addSubSubject(parentId, trimmed);
      if (!created) throw new Error('failed');
      setAddingUnderParent(null);
      setNewSubName('');
      const updated = await YanjiDataNative.getSubjects();
      setSubjects(updated);
    } catch {
      setError('添加子学科失败，请重试');
    } finally {
      setAddingSubSaving(false);
    }
  };

  const handleAddCategory = async () => {
    const trimmed = newCategoryName.trim();
    if (!trimmed) {
      setError('学科分类名称不能为空');
      return;
    }
    setAddingCategorySaving(true);
    setError(null);
    try {
      const created = await YanjiDataNative.addSubjectCategory(trimmed);
      if (!created) throw new Error('failed');
      setAddingCategory(false);
      setNewCategoryName('');
      const updated = await YanjiDataNative.getSubjects();
      setSubjects(updated);
    } catch {
      setError('新建分类失败，请重试');
    } finally {
      setAddingCategorySaving(false);
    }
  };

  const handleDeleteSubject = async (subjectId: string) => {
    setError(null);
    try {
      await YanjiDataNative.deleteSubject(subjectId);
      const updated = await YanjiDataNative.getSubjects();
      setSubjects(updated);
    } catch {
      setError('删除学科失败');
    }
  };

  /**
   * A subject disappears from the picker immediately, while its historical
   * records survive and fall back to 「其他」. Both facts are stated before the
   * destructive call, for a category and a child alike.
   */
  const confirmDeleteSubject = (subject: Subject, childCount = 0) => {
    const scope = subject.isCategory
      ? childCount > 0
        ? `「${subject.name}」及其下的 ${childCount} 个子学科会从列表中移除。`
        : `「${subject.name}」会从列表中移除。`
      : `「${subject.name}」会从父分类中移除。`;
    Alert.alert(
      subject.isCategory ? '删除学科分类' : '删除子学科',
      `${scope}历史记录仍会保留，但统计中会归入「其他」。`,
      [
        { text: '取消', style: 'cancel' },
        {
          text: '删除',
          style: 'destructive',
          onPress: () => {
            void handleDeleteSubject(subject.id);
          },
        },
      ],
      { cancelable: true }
    );
  };

  const handleRestoreDefaults = async () => {
    setError(null);
    try {
      await YanjiDataNative.restoreDefaultSubjects();
      const updated = await YanjiDataNative.getSubjects();
      setSubjects(updated);
    } catch {
      setError('恢复默认失败');
    }
  };

  const confirmRestoreDefaults = () => {
    Alert.alert(
      '恢复默认学科？',
      '自定义分类和子学科会被移除，恢复为出厂列表。历史记录仍会保留，但统计中会归入「其他」。',
      [
        { text: '取消', style: 'cancel' },
        {
          text: '恢复默认',
          style: 'destructive',
          onPress: () => {
            void handleRestoreDefaults();
          },
        },
      ],
      { cancelable: true }
    );
  };

  return (
    <Modal visible={visible} transparent animationType={reduceMotion ? 'none' : 'slide'} onRequestClose={onClose}>
      <View style={{ flex: 1, backgroundColor: colors.scrim, justifyContent: 'flex-end' }}>
        <Pressable style={{ flex: 1 }} onPress={onClose} accessibilityLabel="关闭" />
        <ScrollView
          showsVerticalScrollIndicator={false}
          overScrollMode="never"
          keyboardShouldPersistTaps="handled"
          style={{
            flexGrow: 0,
            maxHeight: '88%',
            backgroundColor: colors.surface,
            borderTopLeftRadius: radius.sheet,
            borderTopRightRadius: radius.sheet,
          }}
          contentContainerStyle={{
            paddingHorizontal: layout.pageGutter,
            paddingTop: YanjiSpacing.md,
            paddingBottom: YanjiSpacing.lg + insets.bottom,
          }}
        >
          <View
            style={{
              alignSelf: 'center',
              width: 36,
              height: 4,
              borderRadius: radius.pill,
              backgroundColor: colors.hairline,
              marginBottom: YanjiSpacing.lg,
            }}
          />
          <YanjiPaperSheetHeading
            title="专注学科"
            subtitle="重命名会同步历史记录；删除后记录仍保留，统计归入「其他」"
            onClose={onClose}
          />

          {loading && subjects.length === 0 ? (
            <View style={{ paddingVertical: YanjiSpacing.xl, alignItems: 'center' }}>
              <Text style={[typography.bodyPrimary, { color: colors.secondary }]}>正在读取学科…</Text>
            </View>
          ) : (
            <View style={{ marginTop: YanjiSpacing.md }}>
              {groups.map(group => {
                const isEditingCategory = editingId === group.category.id;
                const categoryAccent = group.category.colorHex || colors.brand;
                return (
                  <View
                    key={group.category.id}
                    style={{
                      backgroundColor: colors.subtle,
                      borderRadius: radius.surface,
                      padding: YanjiSpacing.md,
                      marginBottom: YanjiSpacing.md,
                      borderWidth: layout.hairlineWidth,
                      borderColor: colors.hairline,
                    }}
                  >
                    {/* Category Row */}
                    {isEditingCategory ? (
                      <View style={{ flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.sm }}>
                        <View style={{ width: 8, height: 8, borderRadius: radius.pill, backgroundColor: categoryAccent }} />
                        <TextInput
                          value={editName}
                          onChangeText={setEditName}
                          autoFocus
                          placeholder="学科分类名称"
                          placeholderTextColor={colors.secondary}
                          style={[
                            typography.bodyPrimary,
                            {
                              flex: 1,
                              color: colors.ink,
                              backgroundColor: colors.surface,
                              borderRadius: radius.compactCard,
                              paddingHorizontal: YanjiSpacing.sm,
                              paddingVertical: 4,
                              borderWidth: 1,
                              borderColor: colors.brand,
                            },
                          ]}
                        />
                        <YanjiIconButton
                          icon="check"
                          accessibilityLabel="保存分类名称修改"
                          onPress={() => void handleSaveRename(group.category.id)}
                          disabled={savingId === group.category.id}
                          size={layout.rowMinHeight}
                          iconSize={14}
                          tone="filled"
                        />
                        <YanjiIconButton
                          icon="close"
                          accessibilityLabel="取消修改"
                          onPress={handleCancelEdit}
                          size={layout.rowMinHeight}
                          iconSize={14}
                          tone="plain"
                        />
                      </View>
                    ) : (
                      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                        <View style={{ flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.sm, flex: 1 }}>
                          <View style={{ width: 8, height: 8, borderRadius: radius.pill, backgroundColor: categoryAccent }} />
                          <Text style={[typography.titleGroup, { color: colors.ink, fontWeight: '700' }]}>
                            {group.category.name}
                          </Text>
                        </View>
                        <View style={{ flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.xs }}>
                          <YanjiIconButton
                            icon="compose"
                            accessibilityLabel={`修改${group.category.name}名称`}
                            onPress={() => handleStartEdit(group.category)}
                            size={layout.rowMinHeight}
                            iconSize={14}
                            tone="plain"
                          />
                          <YanjiIconButton
                            icon="delete"
                            accessibilityLabel={`删除${group.category.name}`}
                            onPress={() => confirmDeleteSubject(group.category, group.children.length)}
                            size={layout.rowMinHeight}
                            iconSize={14}
                            tone="danger"
                          />
                        </View>
                      </View>
                    )}

                    {/* Sub-subjects List */}
                    {group.children.length > 0 ? (
                      <View style={{ marginTop: YanjiSpacing.sm, gap: YanjiSpacing.xs, paddingLeft: YanjiSpacing.md }}>
                        {group.children.map(child => {
                          const isEditingChild = editingId === child.id;
                          const childAccent = child.colorHex || colors.muted;
                          return isEditingChild ? (
                            <View key={child.id} style={{ flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.sm }}>
                              <View style={{ width: 6, height: 6, borderRadius: radius.pill, backgroundColor: childAccent }} />
                              <TextInput
                                value={editName}
                                onChangeText={setEditName}
                                autoFocus
                                placeholder="子学科名称"
                                placeholderTextColor={colors.secondary}
                                style={[
                                  typography.bodyPrimary,
                                  {
                                    flex: 1,
                                    color: colors.ink,
                                    backgroundColor: colors.surface,
                                    borderRadius: radius.compactCard,
                                    paddingHorizontal: YanjiSpacing.sm,
                                    paddingVertical: 4,
                                    borderWidth: 1,
                                    borderColor: colors.brand,
                                  },
                                ]}
                              />
                              <YanjiIconButton
                                icon="check"
                                accessibilityLabel="保存子学科名称修改"
                                onPress={() => void handleSaveRename(child.id)}
                                disabled={savingId === child.id}
                                size={layout.rowMinHeight}
                                iconSize={13}
                                tone="filled"
                              />
                              <YanjiIconButton
                                icon="close"
                                accessibilityLabel="取消修改"
                                onPress={handleCancelEdit}
                                size={layout.rowMinHeight}
                                iconSize={13}
                                tone="plain"
                              />
                            </View>
                          ) : (
                            <View
                              key={child.id}
                              style={{
                                flexDirection: 'row',
                                alignItems: 'center',
                                justifyContent: 'space-between',
                                paddingVertical: 2,
                              }}
                            >
                              <View style={{ flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.sm, flex: 1 }}>
                                <View style={{ width: 6, height: 6, borderRadius: radius.pill, backgroundColor: childAccent }} />
                                <Text style={[typography.bodyPrimary, { color: colors.ink }]}>
                                  {child.name}
                                </Text>
                              </View>
                              <View style={{ flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.xs }}>
                                <YanjiIconButton
                                  icon="compose"
                                  accessibilityLabel={`修改${child.name}名称`}
                                  onPress={() => handleStartEdit(child)}
                                  size={layout.rowMinHeight}
                                  iconSize={13}
                                  tone="plain"
                                />
                                <YanjiIconButton
                                  icon="delete"
                                  accessibilityLabel={`删除${child.name}`}
                                  onPress={() => confirmDeleteSubject(child)}
                                  size={layout.rowMinHeight}
                                  iconSize={13}
                                  tone="danger"
                                />
                              </View>
                            </View>
                          );
                        })}
                      </View>
                    ) : null}

                    {/* Add Sub-Subject */}
                    {addingUnderParent === group.category.id ? (
                      <View style={{ flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.sm, marginTop: YanjiSpacing.sm, paddingLeft: YanjiSpacing.md }}>
                        <TextInput
                          value={newSubName}
                          onChangeText={setNewSubName}
                          autoFocus
                          placeholder="输入子学科名称"
                          placeholderTextColor={colors.secondary}
                          style={[
                            typography.captionMeta,
                            {
                              flex: 1,
                              color: colors.ink,
                              backgroundColor: colors.surface,
                              borderRadius: radius.compactCard,
                              paddingHorizontal: YanjiSpacing.sm,
                              paddingVertical: 4,
                              borderWidth: 1,
                              borderColor: colors.brand,
                            },
                          ]}
                        />
                        <YanjiIconButton
                          icon="check"
                          accessibilityLabel="添加子学科"
                          onPress={() => void handleAddSub(group.category.id)}
                          disabled={addingSubSaving}
                          size={layout.rowMinHeight}
                          iconSize={13}
                          tone="filled"
                        />
                        <YanjiIconButton
                          icon="close"
                          accessibilityLabel="取消添加"
                          onPress={() => { setAddingUnderParent(null); setNewSubName(''); }}
                          size={layout.rowMinHeight}
                          iconSize={13}
                          tone="plain"
                        />
                      </View>
                    ) : (
                      <Pressable
                        onPress={() => { setAddingUnderParent(group.category.id); setNewSubName(''); }}
                        accessibilityRole="button"
                        accessibilityLabel={`在${group.category.name}下添加子学科`}
                        style={({ pressed }) => ({
                          flexDirection: 'row',
                          alignItems: 'center',
                          gap: 4,
                          minHeight: layout.rowMinHeight,
                          marginTop: YanjiSpacing.sm,
                          paddingLeft: YanjiSpacing.md,
                          opacity: pressed ? 0.6 : 1,
                        })}
                      >
                        <YanjiIcon name="add" size={14} color={colors.brandInk} />
                        <Text style={[typography.captionMeta, { color: colors.brandInk }]}>添加子学科</Text>
                      </Pressable>
                    )}
                  </View>
                );
              })}

              {/* Add Category */}
              {addingCategory ? (
                <View style={{ flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.sm, marginBottom: YanjiSpacing.md }}>
                  <TextInput
                    value={newCategoryName}
                    onChangeText={setNewCategoryName}
                    autoFocus
                    placeholder="输入新学科分类名称"
                    placeholderTextColor={colors.secondary}
                    style={[
                      typography.bodyPrimary,
                      {
                        flex: 1,
                        color: colors.ink,
                        backgroundColor: colors.subtle,
                        borderRadius: radius.compactCard,
                        paddingHorizontal: YanjiSpacing.md,
                        paddingVertical: 8,
                        borderWidth: 1,
                        borderColor: colors.brand,
                      },
                    ]}
                  />
                  <YanjiIconButton
                    icon="check"
                    accessibilityLabel="确认新建分类"
                    onPress={() => void handleAddCategory()}
                    disabled={addingCategorySaving}
                    size={layout.rowMinHeight}
                    iconSize={14}
                    tone="filled"
                  />
                  <YanjiIconButton
                    icon="close"
                    accessibilityLabel="取消新建分类"
                    onPress={() => { setAddingCategory(false); setNewCategoryName(''); }}
                    size={layout.rowMinHeight}
                    iconSize={14}
                    tone="plain"
                  />
                </View>
              ) : (
                <Pressable
                  onPress={() => { setAddingCategory(true); setNewCategoryName(''); }}
                  accessibilityRole="button"
                  accessibilityLabel="新建学科分类"
                  style={({ pressed }) => ({
                    flexDirection: 'row',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: YanjiSpacing.xs,
                    minHeight: layout.rowMinHeight,
                    paddingVertical: YanjiSpacing.md,
                    borderRadius: radius.surface,
                    borderWidth: 1,
                    borderStyle: 'dashed',
                    borderColor: colors.hairline,
                    backgroundColor: colors.surface,
                    marginBottom: YanjiSpacing.lg,
                    opacity: pressed ? 0.6 : 1,
                  })}
                >
                  <YanjiIcon name="add" size={16} color={colors.brandInk} />
                  <Text style={[typography.bodyPrimary, { color: colors.brandInk, fontWeight: '600' }]}>
                    新建学科分类
                  </Text>
                </Pressable>
              )}

              {error ? (
                <Text accessibilityRole="alert" style={[typography.captionMeta, { color: colors.danger, marginBottom: YanjiSpacing.sm, textAlign: 'center' }]}>
                  {error}
                </Text>
              ) : null}

              <Text style={[typography.captionMeta, { color: colors.secondary, textAlign: 'center', marginBottom: YanjiSpacing.md }]}>
                修改学科名称将同步更新历史专注记录与统计
              </Text>

              <Pressable
                onPress={confirmRestoreDefaults}
                accessibilityRole="button"
                accessibilityLabel="恢复默认学科"
                style={({ pressed }) => ({
                  alignSelf: 'center',
                  minHeight: layout.rowMinHeight,
                  justifyContent: 'center',
                  paddingHorizontal: YanjiSpacing.md,
                  opacity: pressed ? 0.6 : 1,
                })}
              >
                <Text style={[typography.captionMeta, { color: colors.danger, fontWeight: '600' }]}>
                  恢复默认学科
                </Text>
              </Pressable>
            </View>
          )}
        </ScrollView>
      </View>
    </Modal>
  );
}
