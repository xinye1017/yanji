/**
 * 时间表的两个编辑层：模板管理弹层与整页模板编辑器。
 *
 * 管理弹层只做「选一套模板来改」：编辑 / 重命名 / 复制 / 删除 / 新建。
 * 编辑器负责模板名与时间段清单，时间段本身在同一个页面内切换成表单编辑，
 * 不再叠第三层 Modal——嵌套 Modal 在 Android 上会让返回键与键盘各自为政。
 *
 * 重命名与删除直接走桥接（一次只改一个字段，没有草稿可丢）；模板内容的
 * 新建与保存交给页面，因为保存后要刷新当天视图。
 */

import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Modal, Pressable, ScrollView, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useReducedMotion } from 'react-native-reanimated';
import { YanjiDataNative } from '../bridge';
import type { ScheduleBlockInput, ScheduleTemplate, Subject } from '../bridge';
import {
  YanjiBreathButton,
  YanjiChip,
  YanjiEditorialHeader,
  YanjiHairline,
  YanjiIcon,
  YanjiIconButton,
  YanjiPaperSectionHeading,
  YanjiPaperSheetHeading,
  YanjiTextAction,
  YanjiTextInput,
  useYanjiKeyboardInset,
} from './YanjiUI';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing } from '../theme/tokens';
import {
  MINUTES_PER_DAY,
  SCHEDULE_MAX_BLOCKS,
  SCHEDULE_MINUTE_STEP,
  blockDurationMinutes,
  blocksTotalMinutes,
  formatDurationMinutes,
  formatMinuteOfDay,
  nextBlockDraft,
  sortBlocks,
  uniqueTemplateName,
  validateBlockDraft,
} from '../screens/timetableViewLogic';
import type { ScheduleFocusOption } from '../screens/timetableViewLogic';

export interface ScheduleTemplateDraft {
  id: string | null;
  name: string;
  blocks: ScheduleBlockInput[];
}

function pad2(value: number): string {
  return String(value).padStart(2, '0');
}

/** 底部弹层外壳：遮罩 → 纸张 → 抓手 → 标题。 */
function SheetShell({
  visible,
  title,
  subtitle,
  pending = false,
  scrollKey = null,
  onClose,
  children,
}: {
  visible: boolean;
  title: string;
  subtitle: string;
  pending?: boolean;
  /** 变化时把弹层滚到底部，让重命名输入与确认动作避开键盘。 */
  scrollKey?: string | null;
  onClose: () => void;
  children: React.ReactNode;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const insets = useSafeAreaInsets();
  const reduceMotion = useReducedMotion();
  const keyboardHeight = useYanjiKeyboardInset();
  const scrollRef = useRef<React.ComponentRef<typeof ScrollView>>(null);

  useEffect(() => {
    if (scrollKey === null) return;
    // 等 IME 的 padding 进入这一帧后再滚，否则滚到的是键盘弹起前的高度。
    const timer = setTimeout(() => scrollRef.current?.scrollToEnd({ animated: true }), 60);
    return () => clearTimeout(timer);
  }, [scrollKey, keyboardHeight]);

  return (
    <Modal
      visible={visible}
      transparent
      animationType={reduceMotion ? 'none' : 'slide'}
      onRequestClose={onClose}
    >
      <View style={{ flex: 1, backgroundColor: paper.colors.scrim, justifyContent: 'flex-end' }}>
        <Pressable
          style={{ flex: 1 }}
          onPress={onClose}
          disabled={pending}
          accessibilityState={{ disabled: pending }}
          accessibilityLabel="关闭"
        />
        <ScrollView
          ref={scrollRef}
          showsVerticalScrollIndicator={false}
          overScrollMode="never"
          keyboardShouldPersistTaps="handled"
          style={{
            flexGrow: 0,
            maxHeight: '88%',
            backgroundColor: paper.colors.surface,
            borderTopLeftRadius: paper.radius.sheet,
            borderTopRightRadius: paper.radius.sheet,
          }}
          contentContainerStyle={{
            paddingHorizontal: paper.layout.pageGutter,
            paddingTop: YanjiSpacing.md,
            paddingBottom: YanjiSpacing.lg + insets.bottom + keyboardHeight,
          }}
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
            title={title}
            subtitle={subtitle}
            onClose={onClose}
            disabled={pending}
          />
          {children}
        </ScrollView>
      </View>
    </Modal>
  );
}

/** 弹层里的一枚文字动作；取消保持安静，删除才用 danger。 */
function SheetAction({
  label,
  onPress,
  disabled = false,
  tone = 'brand',
}: {
  label: string;
  onPress: () => void;
  disabled?: boolean;
  tone?: 'brand' | 'quiet' | 'danger';
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ disabled }}
      hitSlop={{ top: 10, bottom: 10, left: 8, right: 8 }}
      style={({ pressed }) => ({
        minHeight: paper.layout.rowMinHeight,
        justifyContent: 'center',
        opacity: disabled ? 0.4 : pressed ? 0.6 : 1,
      })}
    >
      <Text
        style={[
          paper.typography.captionMeta,
          {
            color:
              tone === 'danger'
                ? paper.colors.danger
                : tone === 'quiet'
                  ? paper.colors.secondary
                  : paper.colors.brandInk,
            fontWeight: '600',
          },
        ]}
      >
        {label}
      </Text>
    </Pressable>
  );
}

// ---------------------------------------------------------------------------
// 模板管理
// ---------------------------------------------------------------------------

export function ScheduleTemplateManagerSheet({
  visible,
  templates,
  activeTemplateId,
  onClose,
  onChanged,
  onCreate,
  onCopy,
  onEdit,
}: {
  visible: boolean;
  templates: ScheduleTemplate[];
  activeTemplateId: string | null;
  onClose: () => void;
  /** 重命名 / 删除落库后，让页面重新读取模板与当天数据。 */
  onChanged: () => void;
  onCreate: () => void;
  onCopy: (template: ScheduleTemplate) => void;
  onEdit: (template: ScheduleTemplate) => void;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout } = paper;
  const [renamingId, setRenamingId] = useState<string | null>(null);
  const [renameValue, setRenameValue] = useState('');
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [pendingId, setPendingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!visible) return;
    setRenamingId(null);
    setRenameValue('');
    setDeletingId(null);
    setPendingId(null);
    setError(null);
  }, [visible]);

  const startRename = useCallback((template: ScheduleTemplate) => {
    setRenamingId(template.id);
    setRenameValue(template.name);
    setDeletingId(null);
    setError(null);
  }, []);

  const commitRename = useCallback(async () => {
    if (!renamingId || pendingId) return;
    const name = renameValue.trim();
    if (!name) {
      setError('模板名不能为空');
      return;
    }
    setPendingId(renamingId);
    setError(null);
    try {
      await YanjiDataNative.renameScheduleTemplate(renamingId, name);
      setRenamingId(null);
      onChanged();
    } catch {
      setError('重命名失败，请重试');
    } finally {
      setPendingId(null);
    }
  }, [renamingId, pendingId, renameValue, onChanged]);

  const commitDelete = useCallback(async () => {
    if (!deletingId || pendingId) return;
    setPendingId(deletingId);
    setError(null);
    try {
      await YanjiDataNative.deleteScheduleTemplate(deletingId);
      setDeletingId(null);
      onChanged();
    } catch {
      setError('删除失败，请重试');
    } finally {
      setPendingId(null);
    }
  }, [deletingId, pendingId, onChanged]);

  return (
    <SheetShell
      visible={visible}
      title="时间表模板"
      subtitle={`${templates.length} 套模板 · 可为任意日期指定`}
      pending={pendingId !== null}
      scrollKey={renamingId ?? deletingId}
      onClose={onClose}
    >
      <View style={{ marginTop: YanjiSpacing.md }}>
        {templates.map((template, index) => {
          const active = template.id === activeTemplateId;
          const renaming = renamingId === template.id;
          const deleting = deletingId === template.id;
          const pending = pendingId === template.id;
          return (
            <View key={template.id}>
              {index > 0 ? <YanjiHairline /> : null}
              <View style={{ paddingVertical: YanjiSpacing.md }}>
                <View
                  style={{
                    flexDirection: 'row',
                    alignItems: 'center',
                    gap: YanjiSpacing.sm,
                    flexWrap: 'wrap',
                  }}
                >
                  <Text
                    numberOfLines={1}
                    style={[
                      typography.bodyPrimary,
                      { color: colors.ink, fontWeight: '600', flexShrink: 1 },
                    ]}
                  >
                    {template.name}
                  </Text>
                  {active ? (
                    <View
                      style={{
                        backgroundColor: colors.brandSubtle,
                        borderRadius: paper.radius.pill,
                        paddingHorizontal: 7,
                        paddingVertical: 2,
                      }}
                    >
                      <Text
                        style={[
                          typography.captionMeta,
                          { color: colors.brandInk, fontWeight: '700' },
                        ]}
                      >
                        使用中
                      </Text>
                    </View>
                  ) : null}
                </View>
                <Text
                  style={[
                    typography.captionMeta,
                    { color: colors.secondary, marginTop: 2 },
                  ]}
                >
                  {template.blocks.length} 段 ·{' '}
                  {formatDurationMinutes(blocksTotalMinutes(template.blocks))}
                </Text>

                {renaming ? (
                  <View style={{ marginTop: YanjiSpacing.sm }}>
                    <YanjiTextInput
                      value={renameValue}
                      onChangeText={setRenameValue}
                      placeholder="模板名称"
                      accessibilityLabel="模板名称"
                      editable={!pending}
                    />
                    <View
                      style={{
                        flexDirection: 'row',
                        gap: YanjiSpacing.lg,
                        marginTop: YanjiSpacing.xs,
                      }}
                    >
                      <SheetAction
                        label={pending ? '保存中' : '保存名称'}
                        onPress={() => void commitRename()}
                        disabled={pending}
                      />
                      <SheetAction
                        label="取消"
                        onPress={() => setRenamingId(null)}
                        disabled={pending}
                        tone="quiet"
                      />
                    </View>
                  </View>
                ) : deleting ? (
                  <View style={{ marginTop: YanjiSpacing.xs }}>
                    <Text
                      style={[
                        typography.captionMeta,
                        { color: colors.danger, lineHeight: 18 },
                      ]}
                    >
                      删除后，指定这套模板的日期会回落到第一套模板；这套模板的完成记录也会一并删除。
                    </Text>
                    <View
                      style={{
                        flexDirection: 'row',
                        gap: YanjiSpacing.lg,
                        marginTop: YanjiSpacing.xs,
                      }}
                    >
                      <SheetAction
                        label={pending ? '删除中' : '确认删除'}
                        onPress={() => void commitDelete()}
                        disabled={pending}
                        tone="danger"
                      />
                      <SheetAction
                        label="取消"
                        onPress={() => setDeletingId(null)}
                        disabled={pending}
                        tone="quiet"
                      />
                    </View>
                  </View>
                ) : (
                  <View
                    style={{
                      flexDirection: 'row',
                      gap: YanjiSpacing.lg,
                      marginTop: YanjiSpacing.xs,
                    }}
                  >
                    <SheetAction
                      label="编辑"
                      onPress={() => onEdit(template)}
                      disabled={pendingId !== null}
                    />
                    <SheetAction
                      label="重命名"
                      onPress={() => startRename(template)}
                      disabled={pendingId !== null}
                    />
                    <SheetAction
                      label="复制"
                      onPress={() => onCopy(template)}
                      disabled={pendingId !== null}
                    />
                    <SheetAction
                      label="删除"
                      onPress={() => {
                        setDeletingId(template.id);
                        setRenamingId(null);
                        setError(null);
                      }}
                      disabled={pendingId !== null}
                      tone="danger"
                    />
                  </View>
                )}
              </View>
            </View>
          );
        })}

        {templates.length === 0 ? (
          <Text
            style={[
              typography.bodyPrimary,
              { color: colors.secondary, paddingVertical: YanjiSpacing.md },
            ]}
          >
            还没有模板。新建一套，把一天的学习节奏排出来。
          </Text>
        ) : null}

        {error ? (
          <Text
            accessibilityLiveRegion="polite"
            style={[typography.captionMeta, { color: colors.danger, marginTop: YanjiSpacing.sm }]}
          >
            {error}
          </Text>
        ) : null}
      </View>

      <View style={{ marginTop: YanjiSpacing.lg }}>
        <YanjiBreathButton
          icon="add"
          label="新建模板"
          onPress={onCreate}
          disabled={pendingId !== null}
        />
      </View>
      <Text
        style={[
          typography.captionMeta,
          {
            color: colors.secondary,
            textAlign: 'center',
            marginTop: YanjiSpacing.md,
            lineHeight: 18,
          },
        ]}
      >
        模板只决定每天的排期，不会改动已有的专注记录。
      </Text>
      <View style={{ height: YanjiSpacing.xs }} />
    </SheetShell>
  );
}

// ---------------------------------------------------------------------------
// 模板编辑器
// ---------------------------------------------------------------------------

function toInputs(blocks: readonly ScheduleBlockInput[]): ScheduleBlockInput[] {
  return blocks.map(block => ({
    id: block.id,
    startMinute: block.startMinute,
    endMinute: block.endMinute,
    subjectIds: [...block.subjectIds],
    subjectLabel: block.subjectLabel,
    title: block.title,
  }));
}

export function ScheduleTemplateEditor({
  draft,
  subjects,
  saving,
  error,
  onClose,
  onSave,
}: {
  /** null 表示编辑器关闭；每次打开由页面传入新的草稿对象。 */
  draft: ScheduleTemplateDraft | null;
  subjects: Subject[];
  saving: boolean;
  error: string | null;
  onClose: () => void;
  onSave: (draft: ScheduleTemplateDraft) => void;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout, radius } = paper;
  const insets = useSafeAreaInsets();
  const keyboardHeight = useYanjiKeyboardInset();

  const [name, setName] = useState('');
  const [blocks, setBlocks] = useState<ScheduleBlockInput[]>([]);
  const [editingIndex, setEditingIndex] = useState<number | null>(null);
  const [blockDraft, setBlockDraft] = useState<ScheduleBlockInput | null>(null);
  const [blockError, setBlockError] = useState<string | null>(null);
  const [nameError, setNameError] = useState<string | null>(null);
  const [timeField, setTimeField] = useState<'start' | 'end' | null>(null);

  useEffect(() => {
    if (!draft) return;
    setName(draft.name);
    setBlocks(toInputs(draft.blocks));
    setEditingIndex(null);
    setBlockDraft(null);
    setBlockError(null);
    setNameError(null);
    setTimeField(null);
  }, [draft]);

  const categories = useMemo(
    () => subjects.filter(subject => subject.isCategory && subject.enabled),
    [subjects]
  );

  const totalMinutes = blocksTotalMinutes(blocks);

  const beginNewBlock = useCallback(() => {
    if (blocks.length >= SCHEDULE_MAX_BLOCKS) return;
    setBlockDraft(nextBlockDraft(blocks));
    setEditingIndex(null);
    setBlockError(null);
    setTimeField(null);
  }, [blocks]);

  const beginEditBlock = useCallback(
    (index: number) => {
      setBlockDraft(toInputs([blocks[index]])[0]);
      setEditingIndex(index);
      setBlockError(null);
      setTimeField(null);
    },
    [blocks]
  );

  const closeBlockForm = useCallback(() => {
    setEditingIndex(null);
    setBlockDraft(null);
    setBlockError(null);
    setTimeField(null);
  }, []);

  const toggleSubject = useCallback(
    (category: Subject) => {
      if (!blockDraft) return;
      const selected = blockDraft.subjectIds.includes(category.id);
      const nextIds = selected
        ? blockDraft.subjectIds.filter(id => id !== category.id)
        : [...blockDraft.subjectIds, category.id];
      const names = categories
        .filter(item => nextIds.includes(item.id))
        .map(item => item.name);
      setBlockDraft({
        ...blockDraft,
        subjectIds: nextIds,
        subjectLabel: names.join(' / '),
      });
      setBlockError(null);
    },
    [blockDraft, categories]
  );

  const saveBlock = useCallback(() => {
    if (!blockDraft) return;
    const candidate: ScheduleBlockInput = {
      ...blockDraft,
      subjectLabel: blockDraft.subjectLabel.trim(),
      title: blockDraft.title.trim(),
    };
    const others = blocks.filter((_, index) => index !== editingIndex);
    const message = validateBlockDraft(candidate, others);
    if (message) {
      setBlockError(message);
      return;
    }
    const next = sortBlocks([...others, candidate]);
    setBlocks(next);
    closeBlockForm();
  }, [blockDraft, blocks, editingIndex, closeBlockForm]);

  const deleteBlock = useCallback(() => {
    if (editingIndex === null) return;
    setBlocks(current => current.filter((_, index) => index !== editingIndex));
    closeBlockForm();
  }, [editingIndex, closeBlockForm]);

  const saveTemplate = useCallback(() => {
    const trimmed = name.trim();
    if (!trimmed) {
      setNameError('先给模板起个名字');
      return;
    }
    if (blocks.length === 0) {
      setNameError('至少添加一个时间段');
      return;
    }
    setNameError(null);
    onSave({ id: draft?.id ?? null, name: trimmed, blocks: sortBlocks(blocks) });
  }, [name, blocks, draft, onSave]);

  // 时间改动保持原有长度：把开始时间往后挪时，结束时间一起挪，避免一改就报错。
  const changeStart = useCallback(
    (next: number) => {
      if (!blockDraft) return;
      const duration = Math.max(
        SCHEDULE_MINUTE_STEP,
        blockDraft.endMinute - blockDraft.startMinute
      );
      const end = Math.min(MINUTES_PER_DAY, next + duration);
      setBlockDraft({ ...blockDraft, startMinute: next, endMinute: end });
      setBlockError(null);
    },
    [blockDraft]
  );

  const changeEnd = useCallback(
    (next: number) => {
      if (!blockDraft) return;
      setBlockDraft({ ...blockDraft, endMinute: next });
      setBlockError(null);
    },
    [blockDraft]
  );

  if (!draft) {
    return <></>;
  }

  const editingBlock = blockDraft !== null;
  const selectedIds = blockDraft?.subjectIds ?? [];
  // 已选但不在大类列表里的学科（旧备份或自定义数据）也要能取消，否则会
  // 在保存时被无声丢掉。
  const extraSelected = selectedIds
    .map(id => subjects.find(subject => subject.id === id))
    .filter((subject): subject is Subject => Boolean(subject) && !subject!.isCategory);

  return (
    <Modal
      visible
      animationType="slide"
      onRequestClose={editingBlock ? closeBlockForm : onClose}
    >
      <View
        style={{
          flex: 1,
          backgroundColor: colors.canvas,
          paddingTop: Math.max(insets.top, YanjiSpacing.lg),
        }}
      >
        <ScrollView
          keyboardShouldPersistTaps="always"
          keyboardDismissMode="none"
          contentContainerStyle={{
            paddingHorizontal: layout.pageGutter,
            paddingTop: YanjiSpacing.lg,
            paddingBottom: YanjiSpacing.xxl + keyboardHeight,
          }}
        >
          {editingBlock ? (
            <>
              <View
                style={{
                  flexDirection: 'row',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  minHeight: layout.rowMinHeight,
                }}
              >
                <Pressable
                  onPress={closeBlockForm}
                  accessibilityRole="button"
                  accessibilityLabel="返回模板"
                  hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
                  style={({ pressed }) => ({
                    flexDirection: 'row',
                    alignItems: 'center',
                    minHeight: layout.rowMinHeight,
                    opacity: pressed ? 0.6 : 1,
                  })}
                >
                  <YanjiIcon name="back" size={15} color={colors.brandInk} />
                  <Text
                    style={[
                      typography.captionMeta,
                      { color: colors.brandInk, fontWeight: '700', marginLeft: 4 },
                    ]}
                  >
                    返回模板
                  </Text>
                </Pressable>
                {editingIndex !== null ? (
                  <SheetAction label="删除这段" onPress={deleteBlock} tone="danger" />
                ) : null}
              </View>

              <YanjiPaperSectionHeading title="时间" spaceBefore={YanjiSpacing.sm} />
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.sm }}>
                <TimeField
                  label="开始"
                  minute={blockDraft.startMinute}
                  active={timeField === 'start'}
                  onPress={() => setTimeField(timeField === 'start' ? null : 'start')}
                />
                <Text style={[typography.bodyPrimary, { color: colors.secondary }]}>–</Text>
                <TimeField
                  label="结束"
                  minute={blockDraft.endMinute}
                  active={timeField === 'end'}
                  onPress={() => setTimeField(timeField === 'end' ? null : 'end')}
                />
              </View>
              <Text
                style={[
                  typography.captionMeta,
                  {
                    color:
                      blockDraft.endMinute <= blockDraft.startMinute
                        ? colors.danger
                        : colors.secondary,
                    marginTop: YanjiSpacing.xs,
                  },
                ]}
              >
                {blockDraft.endMinute > blockDraft.startMinute
                  ? `共 ${formatDurationMinutes(blockDurationMinutes(blockDraft))}`
                  : '结束时间需晚于开始时间'}
              </Text>

              {timeField ? (
                <TimePickerPanel
                  field={timeField}
                  minute={
                    timeField === 'start' ? blockDraft.startMinute : blockDraft.endMinute
                  }
                  onChange={timeField === 'start' ? changeStart : changeEnd}
                  onDone={() => setTimeField(null)}
                />
              ) : null}

              <YanjiHairline style={{ marginTop: YanjiSpacing.lg }} />

              <YanjiPaperSectionHeading title="科目" countText="可多选" />
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: YanjiSpacing.sm }}>
                <YanjiChip
                  label="不关联"
                  selected={selectedIds.length === 0}
                  onPress={() => {
                    setBlockDraft({
                      ...blockDraft,
                      subjectIds: [],
                      subjectLabel: '',
                    });
                    setBlockError(null);
                  }}
                />
                {categories.map(category => (
                  <YanjiChip
                    key={category.id}
                    label={category.name}
                    selected={selectedIds.includes(category.id)}
                    onPress={() => toggleSubject(category)}
                  />
                ))}
                {extraSelected.map(subject => (
                  <YanjiChip
                    key={subject.id}
                    label={subject.name}
                    selected
                    onPress={() =>
                      setBlockDraft({
                        ...blockDraft,
                        subjectIds: selectedIds.filter(id => id !== subject.id),
                        subjectLabel: blockDraft.subjectLabel,
                      })
                    }
                  />
                ))}
              </View>

              {selectedIds.length > 0 ? (
                <Text
                  style={[
                    typography.captionMeta,
                    { color: colors.secondary, marginTop: YanjiSpacing.sm },
                  ]}
                >
                  时间表显示为「{blockDraft.subjectLabel}」
                  {selectedIds.length > 1 ? ' · 开始专注时可选择记入哪一科' : ''}
                </Text>
              ) : (
                <View style={{ marginTop: YanjiSpacing.sm }}>
                  <YanjiTextInput
                    value={blockDraft.subjectLabel}
                    onChangeText={value => {
                      setBlockDraft({ ...blockDraft, subjectLabel: value });
                      setBlockError(null);
                    }}
                    placeholder="科目名称，例如：复盘"
                    accessibilityLabel="科目名称"
                  />
                </View>
              )}

              <YanjiHairline style={{ marginTop: YanjiSpacing.lg }} />

              <YanjiPaperSectionHeading title="学习内容" />
              <YanjiTextInput
                value={blockDraft.title}
                onChangeText={value => {
                  setBlockDraft({ ...blockDraft, title: value });
                  setBlockError(null);
                }}
                placeholder="这一段时间要做什么"
                accessibilityLabel="学习内容"
              />

              {blockError ? (
                <Text
                  accessibilityLiveRegion="polite"
                  style={[
                    typography.captionMeta,
                    { color: colors.danger, marginTop: YanjiSpacing.md },
                  ]}
                >
                  {blockError}
                </Text>
              ) : null}

              <View style={{ marginTop: YanjiSpacing.xl }}>
                <YanjiBreathButton
                  icon="check"
                  label={editingIndex === null ? '添加时间段' : '保存时间段'}
                  onPress={saveBlock}
                />
              </View>
            </>
          ) : (
            <>
              <YanjiEditorialHeader
                eyebrow="每日时间表"
                title={draft.id ? '编辑模板' : '新建模板'}
                subtitle={`${blocks.length} 段 · ${formatDurationMinutes(totalMinutes)}`}
                trailing={
                  <YanjiIconButton
                    icon="close"
                    accessibilityLabel="关闭"
                    onPress={onClose}
                    disabled={saving}
                    tone="filled"
                  />
                }
              />

              <YanjiPaperSectionHeading title="模板名称" />
              <YanjiTextInput
                value={name}
                onChangeText={value => {
                  setName(value);
                  setNameError(null);
                }}
                placeholder="例如：复习冲刺"
                accessibilityLabel="模板名称"
                editable={!saving}
              />

              <YanjiHairline style={{ marginTop: YanjiSpacing.lg }} />

              <YanjiPaperSectionHeading
                title="时间段"
                countText={`${blocks.length} 段 · ${formatDurationMinutes(totalMinutes)}`}
                right={
                  <YanjiTextAction
                    label="添加"
                    onPress={beginNewBlock}
                    disabled={saving || blocks.length >= SCHEDULE_MAX_BLOCKS}
                  />
                }
              />

              {blocks.length === 0 ? (
                <Text
                  style={[
                    typography.bodyPrimary,
                    { color: colors.secondary, paddingVertical: YanjiSpacing.md, lineHeight: 22 },
                  ]}
                >
                  还没有时间段。添加第一段，把一天的学习节奏排出来。
                </Text>
              ) : (
                <View>
                  {blocks.map((block, index) => (
                    <View key={block.id ?? `new-${index}`}>
                      {index > 0 ? <YanjiHairline /> : null}
                      <Pressable
                        onPress={() => beginEditBlock(index)}
                        disabled={saving}
                        accessibilityRole="button"
                        accessibilityLabel={`编辑时间段：${formatMinuteOfDay(
                          block.startMinute
                        )} 至 ${formatMinuteOfDay(block.endMinute)}，${block.subjectLabel}，${
                          block.title
                        }`}
                        style={({ pressed }) => ({
                          flexDirection: 'row',
                          alignItems: 'center',
                          minHeight: layout.rowMinHeight + 12,
                          paddingVertical: YanjiSpacing.sm,
                          opacity: pressed ? 0.6 : 1,
                        })}
                      >
                        <View style={{ width: layout.timelineTimeColumn + 34 }}>
                          <Text
                            style={[
                              typography.captionMeta,
                              { color: colors.ink, fontWeight: '700' },
                            ]}
                          >
                            {formatMinuteOfDay(block.startMinute)}
                          </Text>
                          <Text
                            style={[
                              typography.captionMeta,
                              { color: colors.secondary, marginTop: 2 },
                            ]}
                          >
                            至 {formatMinuteOfDay(block.endMinute)}
                          </Text>
                        </View>
                        <View style={{ flex: 1, minWidth: 0 }}>
                          <Text
                            numberOfLines={1}
                            style={[
                              typography.captionMeta,
                              { color: colors.secondary, fontWeight: '600' },
                            ]}
                          >
                            {block.subjectLabel}
                          </Text>
                          <Text
                            numberOfLines={1}
                            style={[
                              typography.bodyPrimary,
                              { color: colors.ink, fontWeight: '600', marginTop: 1 },
                            ]}
                          >
                            {block.title}
                          </Text>
                        </View>
                        <YanjiIcon name="forward" size={13} color={colors.muted} />
                      </Pressable>
                    </View>
                  ))}
                </View>
              )}

              {nameError ? (
                <Text
                  accessibilityLiveRegion="polite"
                  style={[
                    typography.captionMeta,
                    { color: colors.danger, marginTop: YanjiSpacing.md },
                  ]}
                >
                  {nameError}
                </Text>
              ) : null}
              {error ? (
                <Text
                  accessibilityLiveRegion="polite"
                  style={[
                    typography.captionMeta,
                    { color: colors.danger, marginTop: YanjiSpacing.sm },
                  ]}
                >
                  {error}
                </Text>
              ) : null}

              <View style={{ marginTop: YanjiSpacing.xl }}>
                <YanjiBreathButton
                  icon="check"
                  label={saving ? '保存中' : '保存模板'}
                  onPress={saveTemplate}
                  disabled={saving}
                />
              </View>
              <Text
                style={[
                  typography.captionMeta,
                  {
                    color: colors.secondary,
                    textAlign: 'center',
                    marginTop: YanjiSpacing.md,
                    lineHeight: 18,
                  },
                ]}
              >
                保存后，使用这套模板的日期会立即更新；历史专注记录不受影响。
              </Text>
              <View style={{ height: YanjiSpacing.xs }} />
            </>
          )}
        </ScrollView>
      </View>
    </Modal>
  );
}

function TimeField({
  label,
  minute,
  active,
  onPress,
}: {
  label: string;
  minute: number;
  active: boolean;
  onPress: () => void;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout, radius } = paper;
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`${label}时间 ${formatMinuteOfDay(minute)}`}
      accessibilityState={{ selected: active }}
      style={({ pressed }) => ({
        flex: 1,
        minHeight: layout.rowMinHeight + 8,
        justifyContent: 'center',
        alignItems: 'center',
        borderRadius: radius.tag,
        borderWidth: layout.hairlineWidth,
        borderColor: active ? colors.brand : colors.hairline,
        backgroundColor: active ? colors.brandSubtle : colors.surface,
        opacity: pressed ? 0.7 : 1,
      })}
    >
      <Text style={[typography.captionMeta, { color: colors.secondary }]}>{label}</Text>
      <Text
        style={[
          typography.titleSection,
          {
            color: active ? colors.brandInk : colors.ink,
            marginTop: 1,
            fontVariant: ['tabular-nums'],
          },
        ]}
      >
        {formatMinuteOfDay(minute)}
      </Text>
      <View style={{ position: 'absolute', right: YanjiSpacing.sm, top: 0, bottom: 0, justifyContent: 'center' }}>
        <YanjiIcon name="chevronDown" size={12} color={colors.muted} />
      </View>
    </Pressable>
  );
}

/**
 * 小时 / 分钟两排胶囊。整点 24 只出现在结束时间里，且只能配 00 分。
 * 面板嵌在编辑页内部，不再弹系统或第三层 Modal。
 */
function TimePickerPanel({
  field,
  minute,
  onChange,
  onDone,
}: {
  field: 'start' | 'end';
  minute: number;
  onChange: (next: number) => void;
  onDone: () => void;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, radius } = paper;
  const hourScroll = useRef<React.ComponentRef<typeof ScrollView>>(null);
  const minuteScroll = useRef<React.ComponentRef<typeof ScrollView>>(null);
  const [chipWidth, setChipWidth] = useState(0);
  const hour = Math.min(24, Math.floor(minute / 60));
  const minutePart = minute % 60;
  const hours = useMemo(
    () => Array.from({ length: field === 'end' ? 25 : 24 }, (_, index) => index),
    [field]
  );
  const minutes = useMemo(
    () =>
      Array.from(
        { length: Math.ceil(60 / SCHEDULE_MINUTE_STEP) },
        (_, index) => index * SCHEDULE_MINUTE_STEP
      ),
    []
  );

  // 打开面板时把当前值滚进视野；所有胶囊都是两位数字，量一次宽度即可。
  useEffect(() => {
    if (chipWidth <= 0) return;
    const step = chipWidth + YanjiSpacing.xs;
    hourScroll.current?.scrollTo({ x: Math.max(0, hour * step - 24), animated: false });
  }, [hour, chipWidth, field]);

  useEffect(() => {
    if (chipWidth <= 0) return;
    const step = chipWidth + YanjiSpacing.xs;
    const index = Math.round(minutePart / SCHEDULE_MINUTE_STEP);
    minuteScroll.current?.scrollTo({ x: Math.max(0, index * step - 24), animated: false });
  }, [minutePart, chipWidth, field]);

  return (
    <View
      style={{
        marginTop: YanjiSpacing.md,
        padding: YanjiSpacing.sm,
        borderRadius: radius.tag,
        backgroundColor: colors.subtle,
      }}
    >
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'space-between',
          paddingHorizontal: YanjiSpacing.xs,
        }}
      >
        <Text style={[typography.captionMeta, { color: colors.secondary }]}>
          {field === 'start' ? '选择开始时间' : '选择结束时间'}
        </Text>
        <YanjiTextAction label="完成" onPress={onDone} />
      </View>
      <ScrollView
        ref={hourScroll}
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={{ gap: YanjiSpacing.xs, paddingVertical: YanjiSpacing.xs }}
      >
        {hours.map(value => (
          <View
            key={value}
            onLayout={value === 0 ? event => setChipWidth(event.nativeEvent.layout.width) : undefined}
          >
            <YanjiChip
              label={pad2(value)}
              selected={value === hour}
              onPress={() => {
                const nextMinute = value === 24 ? 0 : minutePart;
                onChange(value * 60 + nextMinute);
              }}
            />
          </View>
        ))}
      </ScrollView>
      {hour === 24 ? (
        <Text
          style={[
            typography.captionMeta,
            { color: colors.secondary, paddingHorizontal: YanjiSpacing.xs, paddingBottom: YanjiSpacing.xs },
          ]}
        >
          24:00 表示当天结束，分钟固定为 00。
        </Text>
      ) : (
        <ScrollView
          ref={minuteScroll}
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={{ gap: YanjiSpacing.xs, paddingBottom: YanjiSpacing.xs }}
        >
          {minutes.map(value => (
            <YanjiChip
              key={value}
              label={pad2(value)}
              selected={value === minutePart}
              onPress={() => onChange(hour * 60 + value)}
            />
          ))}
        </ScrollView>
      )}
    </View>
  );
}

// ---------------------------------------------------------------------------
// 专注学科选择
// ---------------------------------------------------------------------------

export function ScheduleSubjectPickerSheet({
  visible,
  options,
  blockTitle,
  onClose,
  onPick,
}: {
  visible: boolean;
  options: readonly ScheduleFocusOption[];
  blockTitle: string;
  onClose: () => void;
  onPick: (option: ScheduleFocusOption) => void;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout, radius } = paper;
  return (
    <SheetShell
      visible={visible}
      title="开始专注"
      subtitle={blockTitle ? `${blockTitle} · 选择记入的学科` : '选择记入的学科'}
      onClose={onClose}
    >
      <View style={{ marginTop: YanjiSpacing.md }}>
        {options.map((option, index) => (
          <View key={option.subjectId}>
            {index > 0 ? <YanjiHairline /> : null}
            <Pressable
              onPress={() => onPick(option)}
              accessibilityRole="button"
              accessibilityLabel={
                option.subjectName === option.label
                  ? option.label
                  : `${option.label}，记入 ${option.subjectName}`
              }
              style={({ pressed }) => ({
                flexDirection: 'row',
                alignItems: 'center',
                gap: YanjiSpacing.sm,
                minHeight: layout.rowMinHeight + 8,
                opacity: pressed ? 0.6 : 1,
              })}
            >
              <View
                style={{
                  width: 10,
                  height: 10,
                  borderRadius: radius.pill,
                  backgroundColor: option.colorHex,
                }}
              />
              <View style={{ flex: 1, minWidth: 0 }}>
                <Text
                  numberOfLines={1}
                  style={[typography.bodyPrimary, { color: colors.ink, fontWeight: '600' }]}
                >
                  {option.label}
                </Text>
                {option.subjectName !== option.label ? (
                  <Text
                    numberOfLines={1}
                    style={[typography.captionMeta, { color: colors.secondary, marginTop: 1 }]}
                  >
                    记入 {option.subjectName}
                  </Text>
                ) : null}
              </View>
              <YanjiIcon name="forward" size={13} color={colors.muted} />
            </Pressable>
          </View>
        ))}
      </View>
    </SheetShell>
  );
}

/** 复制模板时用的新名字；保留在组件外，页面与弹层共用一套规则。 */
export function copiedTemplateName(
  templates: readonly ScheduleTemplate[],
  sourceName: string
): string {
  return uniqueTemplateName(
    templates.map(template => template.name),
    `${sourceName} 副本`
  );
}
