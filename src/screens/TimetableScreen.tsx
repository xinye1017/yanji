/**
 * 每日学习时间表 — 从今天页右上角日历图标进入的整页浮层。
 *
 * 页面只读两件事：当前这一天的模板（`getScheduleDay`）和当天真实专注
 * （`useDailySnapshot`）。模板切换只写「这一天用哪套模板」，不改历史专注；
 * 每段右侧的勾选写「这一天这一段是否完成」，键是 (date, blockId)。
 *
 * 点击时段把科目与时长交给专注页：时长按段长预选，科目解析到大类下的
 * 第一个启用子类（与专注页的选择模型一致），不创建任务绑定。
 */

import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { onDataChanged, YanjiDataNative } from '../bridge';
import type { ScheduleBlock, ScheduleDay, ScheduleTemplate, Subject } from '../bridge';
import {
  YanjiChip,
  YanjiEditorialHeader,
  YanjiHairline,
  YanjiIconButton,
  YanjiPaperSectionHeading,
  YanjiStaleDataNotice,
  YanjiTextAction,
} from '../components/YanjiUI';
import { ScheduleBlockRow } from '../components/ScheduleBlockRow';
import {
  ScheduleSubjectPickerSheet,
  ScheduleTemplateEditor,
  ScheduleTemplateManagerSheet,
  copiedTemplateName,
} from '../components/ScheduleTemplateSheets';
import type { ScheduleTemplateDraft } from '../components/ScheduleTemplateSheets';
import { useNavigationActions } from '../navigation/NavigationShell';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing } from '../theme/tokens';
import { useDailySnapshot } from '../state/dataRefresh';
import {
  addDays,
  blockActualSeconds,
  blockDurationMinutes,
  blocksTotalMinutes,
  currentBlockId,
  dayOfMonth,
  displayScheduleDate,
  focusOptionsForBlock,
  formatDurationMinutes,
  formatFocusSeconds,
  isoDate,
  minutesOfDay,
  scheduleProgress,
  subjectColorForBlock,
  weekDates,
  uniqueTemplateName,
  weekRangeLabel,
  weekdayShort,
} from './timetableViewLogic';
import type { ScheduleFocusOption } from './timetableViewLogic';

/** 专注页的时长范围；超长时段按上限预选。 */
const FOCUS_MIN_MINUTES = 5;
const FOCUS_MAX_MINUTES = 180;
/** 当前时段的高亮不需要秒级刷新，半分钟足够跨过整点。 */
const CLOCK_REFRESH_MS = 30_000;

function draftFromTemplate(template: ScheduleTemplate): ScheduleTemplateDraft {
  return {
    id: template.id,
    name: template.name,
    blocks: template.blocks.map(block => ({
      id: block.id,
      startMinute: block.startMinute,
      endMinute: block.endMinute,
      subjectIds: [...block.subjectIds],
      subjectLabel: block.subjectLabel,
      title: block.title,
    })),
  };
}

export function TimetableScreen({ onClose }: { onClose: () => void }): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout, radius } = paper;
  const insets = useSafeAreaInsets();
  const { requestFocusPreset } = useNavigationActions();

  const [now, setNow] = useState(() => new Date());
  const [selectedDate, setSelectedDate] = useState(() => isoDate(new Date()));
  const [templates, setTemplates] = useState<ScheduleTemplate[]>([]);
  const [day, setDay] = useState<ScheduleDay | null>(null);
  const [subjects, setSubjects] = useState<Subject[]>([]);
  const [loading, setLoading] = useState(true);
  const [readFailed, setReadFailed] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  const [actionError, setActionError] = useState<string | null>(null);
  const [switching, setSwitching] = useState(false);
  const [pendingBlockId, setPendingBlockId] = useState<string | null>(null);
  const [managerOpen, setManagerOpen] = useState(false);
  const [editorDraft, setEditorDraft] = useState<ScheduleTemplateDraft | null>(null);
  const [editorSaving, setEditorSaving] = useState(false);
  const [editorError, setEditorError] = useState<string | null>(null);
  const [subjectPicker, setSubjectPicker] = useState<{
    block: ScheduleBlock;
    options: ScheduleFocusOption[];
  } | null>(null);

  const loadSeq = useRef(0);
  const loadedDateRef = useRef<string | null>(null);

  const daily = useDailySnapshot('timetable', selectedDate, true);

  useEffect(() => {
    const timer = setInterval(() => setNow(new Date()), CLOCK_REFRESH_MS);
    return () => clearInterval(timer);
  }, []);

  const loadSubjects = useCallback(() => {
    YanjiDataNative.getSubjects()
      .then(list => setSubjects(list))
      .catch(() => {
        // 学科读失败不影响排期本身；时间轴只是暂时没有颜色与启动入口。
      });
  }, []);

  useEffect(() => {
    loadSubjects();
  }, [loadSubjects]);

  useEffect(
    () =>
      onDataChanged(event => {
        if (event.type === 'schedule') setReloadKey(key => key + 1);
        if (event.type === 'subjects') loadSubjects();
      }),
    [loadSubjects]
  );

  useEffect(() => {
    const seq = ++loadSeq.current;
    if (loadedDateRef.current !== selectedDate) {
      loadedDateRef.current = selectedDate;
      setDay(null);
    }
    setLoading(true);
    setActionError(null);
    Promise.all([
      YanjiDataNative.getScheduleTemplates(),
      YanjiDataNative.getScheduleDay(selectedDate),
    ])
      .then(([nextTemplates, nextDay]) => {
        if (seq !== loadSeq.current) return;
        setTemplates(nextTemplates);
        setDay(nextDay);
        setReadFailed(false);
      })
      .catch(() => {
        if (seq !== loadSeq.current) return;
        setReadFailed(true);
      })
      .finally(() => {
        if (seq === loadSeq.current) setLoading(false);
      });
  }, [selectedDate, reloadKey]);

  const today = isoDate(now);
  const isToday = selectedDate === today;
  const week = useMemo(() => weekDates(selectedDate), [selectedDate]);
  const blocks = day?.template?.blocks ?? [];
  const completedIds = day?.completedBlockIds ?? [];
  const completedSet = useMemo(() => new Set(completedIds), [completedIds]);
  const progress = useMemo(() => scheduleProgress(blocks, completedIds), [blocks, completedIds]);
  const actualByBlock = useMemo(
    () => blockActualSeconds(blocks, daily?.sessions ?? [], selectedDate),
    [blocks, daily?.sessions, selectedDate]
  );
  const currentId = useMemo(
    () => (isToday ? currentBlockId(blocks, minutesOfDay(now)) : null),
    [isToday, blocks, now]
  );
  const plannedMinutes = blocksTotalMinutes(blocks);
  const actualSeconds = daily?.totalDurationSeconds ?? 0;
  const progressWidth = `${Math.round(progress.ratio * 100)}%`;

  const assignTemplate = useCallback(
    async (templateId: string) => {
      if (switching || day?.template?.id === templateId) return;
      setSwitching(true);
      setActionError(null);
      try {
        await YanjiDataNative.setScheduleDayTemplate(selectedDate, templateId);
        setReloadKey(key => key + 1);
      } catch {
        setActionError('模板切换失败，请重试');
      } finally {
        setSwitching(false);
      }
    },
    [switching, day?.template?.id, selectedDate]
  );

  const toggleCompleted = useCallback(
    async (block: ScheduleBlock) => {
      if (pendingBlockId) return;
      const completed = completedSet.has(block.id);
      setPendingBlockId(block.id);
      setActionError(null);
      // 先画勾选再落库：勾选是低风险、可回滚的动作，等一次往返会让点击发木。
      setDay(current =>
        current
          ? {
              ...current,
              completedBlockIds: completed
                ? current.completedBlockIds.filter(id => id !== block.id)
                : [...current.completedBlockIds, block.id],
            }
          : current
      );
      try {
        await YanjiDataNative.setScheduleBlockCompleted(selectedDate, block.id, !completed);
      } catch {
        setReloadKey(key => key + 1);
        setActionError('完成状态未保存，请重试');
      } finally {
        setPendingBlockId(null);
      }
    },
    [pendingBlockId, completedSet, selectedDate]
  );

  const beginFocus = useCallback(
    (option: ScheduleFocusOption, block: ScheduleBlock) => {
      setSubjectPicker(null);
      requestFocusPreset({
        taskId: null,
        subjectId: option.subjectId,
        subjectName: option.subjectName,
        title: block.title,
        plannedMinutes: Math.max(
          FOCUS_MIN_MINUTES,
          Math.min(FOCUS_MAX_MINUTES, blockDurationMinutes(block))
        ),
      });
      onClose();
    },
    [requestFocusPreset, onClose]
  );

  const startBlock = useCallback(
    (block: ScheduleBlock) => {
      const options = focusOptionsForBlock(block.subjectIds, subjects);
      if (options.length === 0) return;
      if (options.length === 1) {
        beginFocus(options[0], block);
        return;
      }
      setSubjectPicker({ block, options });
    },
    [subjects, beginFocus]
  );

  const openManager = useCallback(() => {
    setManagerOpen(true);
    setActionError(null);
  }, []);

  const openCreate = useCallback(() => {
    setManagerOpen(false);
    setEditorError(null);
    setEditorDraft({
      id: null,
      name: uniqueTemplateName(templates.map(template => template.name), '新模板'),
      blocks: [],
    });
  }, [templates.length]);

  const openCopy = useCallback(
    (template: ScheduleTemplate) => {
      setManagerOpen(false);
      setEditorError(null);
      setEditorDraft({
        id: null,
        name: copiedTemplateName(templates, template.name),
        blocks: template.blocks.map(block => ({
          id: null,
          startMinute: block.startMinute,
          endMinute: block.endMinute,
          subjectIds: [...block.subjectIds],
          subjectLabel: block.subjectLabel,
          title: block.title,
        })),
      });
    },
    [templates]
  );

  const openEdit = useCallback((template: ScheduleTemplate) => {
    setManagerOpen(false);
    setEditorError(null);
    setEditorDraft(draftFromTemplate(template));
  }, []);

  const saveTemplate = useCallback(
    async (draft: ScheduleTemplateDraft) => {
      if (editorSaving) return;
      setEditorSaving(true);
      setEditorError(null);
      try {
        if (draft.id) {
          await YanjiDataNative.updateScheduleTemplate(draft.id, draft.name, draft.blocks);
        } else {
          await YanjiDataNative.createScheduleTemplate(draft.name, draft.blocks);
        }
        setEditorDraft(null);
        setReloadKey(key => key + 1);
      } catch (error) {
        const code = (error as { code?: string } | null | undefined)?.code;
        setEditorError(
          code === 'E_SCHEDULE_BLOCK_OVERLAP'
            ? '时间段有重叠，请调整后再保存'
            : code === 'E_SCHEDULE_TEMPLATE_NAME'
              ? '模板名称无效'
              : code === 'E_SCHEDULE_BLOCK_INVALID'
                ? '时间段内容无效，请检查科目与名称长度'
                : '保存失败，请重试'
        );
      } finally {
        setEditorSaving(false);
      }
    },
    [editorSaving]
  );

  const hasTemplate = Boolean(day?.template);

  return (
    <View style={{ flex: 1, backgroundColor: colors.canvas }}>
      <ScrollView
        showsVerticalScrollIndicator={false}
        contentContainerStyle={{
          paddingHorizontal: layout.pageGutter,
          paddingTop: YanjiSpacing.lg,
          paddingBottom: YanjiSpacing.xxl + insets.bottom,
        }}
      >
        <YanjiEditorialHeader
          eyebrow="每日时间表"
          title="时间表"
          subtitle={`${displayScheduleDate(selectedDate)}${isToday ? ' · 今天' : ''}`}
          trailing={
            <YanjiIconButton
              icon="close"
              accessibilityLabel="关闭时间表"
              onPress={onClose}
              tone="filled"
            />
          }
        />

        <View style={{ marginTop: YanjiSpacing.lg }}>
          <View
            style={{
              flexDirection: 'row',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: YanjiSpacing.sm,
            }}
          >
            <Text style={[typography.captionMeta, { color: colors.secondary }]}>
              {weekRangeLabel(selectedDate)}
            </Text>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.xs }}>
              {!isToday ? (
                <YanjiTextAction label="回到今天" onPress={() => setSelectedDate(today)} />
              ) : null}
              <YanjiIconButton
                icon="back"
                accessibilityLabel="上一周"
                size={32}
                iconSize={14}
                onPress={() => setSelectedDate(current => addDays(current, -7))}
              />
              <YanjiIconButton
                icon="forward"
                accessibilityLabel="下一周"
                size={32}
                iconSize={14}
                onPress={() => setSelectedDate(current => addDays(current, 7))}
              />
            </View>
          </View>

          <View style={{ flexDirection: 'row', gap: YanjiSpacing.xs, marginTop: YanjiSpacing.xs }}>
            {week.map(date => {
              const selected = date === selectedDate;
              const dayIsToday = date === today;
              return (
                <Pressable
                  key={date}
                  onPress={() => setSelectedDate(date)}
                  accessibilityRole="button"
                  accessibilityLabel={`${displayScheduleDate(date)}${dayIsToday ? '，今天' : ''}`}
                  accessibilityState={{ selected }}
                  style={({ pressed }) => ({
                    flex: 1,
                    alignItems: 'center',
                    paddingVertical: YanjiSpacing.sm,
                    borderRadius: radius.tag,
                    borderWidth: layout.hairlineWidth,
                    borderColor: selected ? colors.brand : 'transparent',
                    backgroundColor: selected ? colors.brandSubtle : 'transparent',
                    opacity: pressed ? 0.7 : 1,
                  })}
                >
                  <Text
                    style={[
                      typography.captionMeta,
                      { color: selected ? colors.brandInk : colors.secondary },
                    ]}
                  >
                    {weekdayShort(date)}
                  </Text>
                  <Text
                    style={[
                      typography.bodyPrimary,
                      {
                        color: selected ? colors.brandInk : colors.ink,
                        fontWeight: selected ? '700' : '600',
                        fontVariant: ['tabular-nums'],
                        marginTop: 1,
                      },
                    ]}
                  >
                    {dayOfMonth(date)}
                  </Text>
                  <View
                    style={{
                      width: 4,
                      height: 4,
                      borderRadius: radius.pill,
                      marginTop: 3,
                      backgroundColor: dayIsToday
                        ? selected
                          ? colors.brandInk
                          : colors.brand
                        : 'transparent',
                    }}
                  />
                </Pressable>
              );
            })}
          </View>
        </View>

        <YanjiHairline style={{ marginTop: YanjiSpacing.lg }} />

        <YanjiPaperSectionHeading
          title="模板"
          countText={day?.assigned ? '已为这天指定' : '默认'}
          right={<YanjiTextAction label="管理" onPress={openManager} />}
        />
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={{ gap: YanjiSpacing.sm, paddingVertical: YanjiSpacing.xs }}
        >
          {templates.map(template => (
            <YanjiChip
              key={template.id}
              label={template.name}
              selected={template.id === day?.template?.id}
              onPress={() => void assignTemplate(template.id)}
            />
          ))}
          {templates.length === 0 ? (
            <YanjiChip label="新建模板" selected={false} onPress={openCreate} />
          ) : null}
        </ScrollView>
        <Text
          style={[
            typography.captionMeta,
            { color: colors.secondary, marginTop: 2, lineHeight: 17 },
          ]}
        >
          {day?.assigned
            ? '切换只影响这一天，历史专注记录不会被改写。'
            : '这天使用默认模板；切换后只影响这一天。'}
        </Text>

        <YanjiHairline style={{ marginTop: YanjiSpacing.lg }} />

        <YanjiPaperSectionHeading title={isToday ? '今日概览' : '当日概览'} />
        <View style={{ flexDirection: 'row', gap: YanjiSpacing.sm, marginTop: YanjiSpacing.xs }}>
          <SummaryStat label="计划" value={formatDurationMinutes(plannedMinutes)} />
          <SummaryStat label="已专注" value={formatFocusSeconds(actualSeconds)} />
          <SummaryStat
            label="完成"
            value={`${progress.completedCount}/${progress.totalCount}`}
          />
        </View>
        <View
          accessible={false}
          style={{
            height: 3,
            borderRadius: radius.pill,
            backgroundColor: colors.subtle,
            marginTop: YanjiSpacing.md,
            overflow: 'hidden',
          }}
        >
          <View
            style={{
              width: progressWidth,
              height: 3,
              borderRadius: radius.pill,
              backgroundColor: colors.brand,
            }}
          />
        </View>
        <Text
          style={[
            typography.captionMeta,
            { color: colors.secondary, marginTop: YanjiSpacing.xs },
          ]}
        >
          {progress.totalCount === 0
            ? '还没有时间段'
            : `已完成 ${progress.completedCount} / ${progress.totalCount} 段 · 按完成段数计算`}
        </Text>

        {readFailed ? (
          <View style={{ marginTop: YanjiSpacing.md }}>
            <YanjiStaleDataNotice
              message="时间表读取失败，当前显示的是上一次的内容。"
              actionLabel={loading ? '重试中' : '重试'}
              onAction={() => setReloadKey(key => key + 1)}
              busy={loading}
            />
          </View>
        ) : null}
        {actionError ? (
          <Text
            accessibilityLiveRegion="polite"
            style={[
              typography.captionMeta,
              { color: colors.danger, marginTop: YanjiSpacing.md },
            ]}
          >
            {actionError}
          </Text>
        ) : null}

        <YanjiHairline style={{ marginTop: YanjiSpacing.lg }} />

        <YanjiPaperSectionHeading
          title="时间轴"
          countText={blocks.length > 0 ? `${blocks.length} 段` : undefined}
          right={
            hasTemplate ? (
              <YanjiTextAction
                label="编辑"
                onPress={() => {
                  if (day?.template) openEdit(day.template);
                }}
              />
            ) : undefined
          }
        />

        {loading && !day ? (
          <Text
            style={[
              typography.bodyPrimary,
              { color: colors.secondary, paddingVertical: YanjiSpacing.lg },
            ]}
          >
            正在读取时间表…
          </Text>
        ) : blocks.length === 0 ? (
          <View style={{ paddingVertical: YanjiSpacing.md, gap: YanjiSpacing.sm }}>
            <Text style={[typography.bodyPrimary, { color: colors.secondary, lineHeight: 22 }]}>
              {hasTemplate
                ? '这套模板还没有时间段。加几段，把一天的学习节奏排出来。'
                : '还没有时间表模板。先新建一套，再为日期套用。'}
            </Text>
            <YanjiTextAction
              label={hasTemplate ? '编辑模板' : '新建模板'}
              onPress={() => {
                if (day?.template) openEdit(day.template);
                else openCreate();
              }}
            />
          </View>
        ) : (
          <View style={{ marginTop: YanjiSpacing.xs }}>
            {blocks.map((block, index) => (
              <ScheduleBlockRow
                key={block.id}
                block={block}
                subjectColor={subjectColorForBlock(block.subjectIds, subjects, colors.muted)}
                isFirst={index === 0}
                isLast={index === blocks.length - 1}
                isCurrent={block.id === currentId}
                completed={completedSet.has(block.id)}
                actualSeconds={actualByBlock[block.id] ?? 0}
                canStart={focusOptionsForBlock(block.subjectIds, subjects).length > 0}
                disabled={pendingBlockId === block.id || switching}
                onToggle={() => void toggleCompleted(block)}
                onStart={() => startBlock(block)}
              />
            ))}
          </View>
        )}

        {blocks.length > 0 ? (
          <Text
            style={[
              typography.captionMeta,
              {
                color: colors.secondary,
                marginTop: YanjiSpacing.md,
                lineHeight: 18,
              },
            ]}
          >
            点按时间段可直接开始专注；没有关联学科的时段只记录完成状态。
          </Text>
        ) : null}
      </ScrollView>

      <ScheduleTemplateManagerSheet
        visible={managerOpen}
        templates={templates}
        activeTemplateId={day?.template?.id ?? null}
        onClose={() => setManagerOpen(false)}
        onChanged={() => setReloadKey(key => key + 1)}
        onCreate={openCreate}
        onCopy={openCopy}
        onEdit={openEdit}
      />

      <ScheduleTemplateEditor
        draft={editorDraft}
        subjects={subjects}
        saving={editorSaving}
        error={editorError}
        onClose={() => setEditorDraft(null)}
        onSave={draft => void saveTemplate(draft)}
      />

      <ScheduleSubjectPickerSheet
        visible={subjectPicker !== null}
        options={subjectPicker?.options ?? []}
        blockTitle={subjectPicker?.block.title ?? ''}
        onClose={() => setSubjectPicker(null)}
        onPick={option => {
          if (subjectPicker) beginFocus(option, subjectPicker.block);
        }}
      />
    </View>
  );
}

function SummaryStat({ label, value }: { label: string; value: string }): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography } = paper;
  return (
    <View style={{ flex: 1, minWidth: 0 }}>
      <Text style={[typography.captionMeta, { color: colors.secondary }]}>{label}</Text>
      <Text
        numberOfLines={1}
        adjustsFontSizeToFit
        minimumFontScale={0.8}
        style={[
          typography.bodyPrimary,
          {
            color: colors.ink,
            fontWeight: '700',
            fontVariant: ['tabular-nums'],
            marginTop: 2,
          },
        ]}
      >
        {value}
      </Text>
    </View>
  );
}
