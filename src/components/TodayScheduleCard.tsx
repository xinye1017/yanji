/**
 * Today's timetable, read where the day is planned.
 *
 * The timetable editor used to live only behind Settings, so the schedule had
 * no presence on the page it governs. This card answers "what is now / next"
 * and starts a focus session straight from the block; editing stays one tap
 * away on the section heading.
 */
import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import { onDataChanged, YanjiDataNative } from '../bridge';
import type { ScheduleBlock, ScheduleDay, Subject } from '../bridge';
import { YanjiIcon, YanjiPaperSectionHeading, YanjiTextAction } from './YanjiUI';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing } from '../theme/tokens';
import {
  blockDurationMinutes,
  currentBlockId,
  focusOptionsForBlock,
  formatMinuteOfDay,
  minutesOfDay,
} from '../screens/timetableViewLogic';

/** Matches the navigation preset; `taskId: null` means "from the timetable". */
export interface ScheduleFocusPreset {
  taskId: null;
  subjectId: string;
  subjectName: string;
  title: string;
  plannedMinutes: number;
}

export function TodayScheduleCard({
  date,
  visible,
  onEdit,
  onStart,
}: {
  date: string;
  visible: boolean;
  onEdit: () => void;
  onStart: (preset: ScheduleFocusPreset) => void;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout, radius } = paper;
  const [day, setDay] = useState<ScheduleDay | null>(null);
  const [subjects, setSubjects] = useState<Subject[]>([]);
  const [failed, setFailed] = useState(false);
  const [nowMinute, setNowMinute] = useState(() => minutesOfDay(new Date()));
  const sequence = useRef(0);

  const load = useCallback(async () => {
    const current = ++sequence.current;
    try {
      const [nextDay, nextSubjects] = await Promise.all([
        YanjiDataNative.getScheduleDay(date),
        YanjiDataNative.getSubjects(),
      ]);
      if (current !== sequence.current) return;
      setDay(nextDay);
      setSubjects(nextSubjects);
      setFailed(false);
    } catch {
      if (current === sequence.current) setFailed(true);
    }
  }, [date]);

  useEffect(() => {
    void load();
    return onDataChanged(event => {
      if (event.type === 'schedule' || event.type === 'subjects') void load();
    });
  }, [load]);

  // A block boundary is minute-granular; half a minute keeps "现在 / 接下来"
  // from lagging visibly behind the wall clock without a per-second redraw.
  useEffect(() => {
    if (!visible) return;
    setNowMinute(minutesOfDay(new Date()));
    const timer = setInterval(() => setNowMinute(minutesOfDay(new Date())), 30_000);
    return () => clearInterval(timer);
  }, [visible]);

  const template = day?.template ?? null;
  const blocks = template?.blocks ?? [];
  const current = blocks.find(block => block.id === currentBlockId(blocks, nowMinute)) ?? null;
  const next = current ? null : blocks.find(block => block.startMinute > nowMinute) ?? null;
  const block = current ?? next;
  const option = block ? focusOptionsForBlock(block.subjectIds, subjects)[0] ?? null : null;
  const canStart = block !== null && option !== null;
  const remaining = current ? Math.max(1, current.endMinute - nowMinute) : 0;
  const plannedMinutes = current ? remaining : block ? blockDurationMinutes(block) : 0;

  const start = () => {
    if (!block || !option) return;
    onStart({
      taskId: null,
      subjectId: option.subjectId,
      subjectName: option.subjectName,
      title: block.title,
      plannedMinutes,
    });
  };

  const headingAction = template ? '编辑' : '去安排';
  const timeRange = block
    ? `${formatMinuteOfDay(block.startMinute)}–${formatMinuteOfDay(block.endMinute)}`
    : '';
  const meta = block
    ? [block.subjectLabel, current ? `剩余 ${remaining} 分钟` : `${blockDurationMinutes(block)} 分钟`]
      .filter(Boolean)
      .join(' · ')
    : '';
  const actionLabel = block
    ? canStart
      ? `${block.title}，${timeRange}，${option.subjectName}，开始 ${plannedMinutes} 分钟专注`
      : `${block.title}，${timeRange}，该时段不启动专注，打开时间表`
    : '';

  return (
    <View>
      <YanjiPaperSectionHeading
        title="今日时间表"
        titleStyle={typography.titlePageSection}
        spaceBefore={layout.sectionGap}
        right={
          <YanjiTextAction
            label={headingAction}
            accessibilityLabel={template ? '编辑今日时间表' : '安排今日时间表'}
            onPress={onEdit}
          />
        }
      />
      {failed ? (
        <View style={{ marginTop: YanjiSpacing.xs }}>
          <Text accessibilityRole="alert" style={[typography.captionMeta, { color: colors.danger }]}>
            时间表读取失败。
          </Text>
          <YanjiTextAction label="重试" onPress={() => void load()} />
        </View>
      ) : !day ? (
        <Text style={[typography.captionMeta, { color: colors.secondary, marginTop: YanjiSpacing.xs }]}>
          正在读取今日时间表…
        </Text>
      ) : !template ? (
        <Text style={[typography.captionMeta, { color: colors.secondary, marginTop: YanjiSpacing.xs }]}>
          还没有时间表，先安排一天的节奏。
        </Text>
      ) : blocks.length === 0 ? (
        <Text style={[typography.captionMeta, { color: colors.secondary, marginTop: YanjiSpacing.xs }]}>
          这套模板还没有时间段。
        </Text>
      ) : !block ? (
        <Text style={[typography.captionMeta, { color: colors.secondary, marginTop: YanjiSpacing.xs }]}>
          今天的时段已全部结束。
        </Text>
      ) : (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={actionLabel}
          onPress={canStart ? start : onEdit}
          style={({ pressed }) => ({
            marginTop: YanjiSpacing.sm,
            padding: YanjiSpacing.md,
            borderRadius: radius.compactCard,
            borderWidth: layout.hairlineWidth,
            borderColor: colors.hairline,
            backgroundColor: colors.elevated,
            opacity: pressed ? 0.7 : 1,
          })}
        >
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.sm }}>
            <View style={{
              paddingHorizontal: 8,
              paddingVertical: 2,
              borderRadius: radius.pill,
              backgroundColor: colors.brandSubtle,
            }}>
              <Text style={[typography.captionMeta, { color: colors.brandInk, fontWeight: '600' }]}>
                {current ? '现在' : '接下来'}
              </Text>
            </View>
            <Text style={[typography.captionMeta, { color: colors.secondary, fontVariant: ['tabular-nums'] }]}>
              {timeRange}
            </Text>
            <View style={{ flex: 1 }} />
            {canStart ? (
              <View style={{
                width: 34,
                height: 34,
                borderRadius: radius.pill,
                alignItems: 'center',
                justifyContent: 'center',
                backgroundColor: colors.brandSubtle,
              }}>
                <YanjiIcon name="play" size={15} color={colors.brandInk} />
              </View>
            ) : (
              <YanjiIcon name="forward" size={15} color={colors.muted} />
            )}
          </View>
          <Text numberOfLines={1} style={[typography.bodyPrimary, { color: colors.ink, fontWeight: '600', marginTop: YanjiSpacing.xs }]}>
            {block.title}
          </Text>
          {meta ? (
            <Text numberOfLines={1} style={[typography.captionMeta, { color: colors.secondary, marginTop: 2 }]}>
              {meta}
            </Text>
          ) : null}
        </Pressable>
      )}
    </View>
  );
}
