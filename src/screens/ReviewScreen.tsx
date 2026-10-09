/**
 * Yanji "Review" screen — a readable growth journal, not a dashboard.
 *
 * Contract (PROJECT.md § R2 / user brief §05):
 * - Day-first: the default view is today's real record, and the date can never
 *   be pushed past today — a day that has not happened yet holds no record.
 * - The timeline is a deterministic aggregation of Room data. Sessions,
 *   completed tasks and notes are merged into ONE chronological sequence so the
 *   page reads as "the day I walked through" rather than three stacked lists.
 * - Statistics stay secondary and factual, and the trend window is switchable
 *   between rolling days and the calendar week/month.
 * - No streak counters, leaderboards or achievement walls as primary content.
 */

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { LayoutChangeEvent, Pressable, ScrollView, Text, View } from 'react-native';
import { YanjiDataNative, onDataChanged } from '../bridge';
import type {
  DailyTimeline,
  DailyTimelineSession,
  ReviewOverview,
  ReviewScope,
  ReviewPeriodDay,
  ReviewSubjectSlice,
  StudyTask,
} from '../bridge';
import {
  YanjiCard,
  YanjiEmptyState,
  YanjiHairline,
  YanjiIcon,
  YanjiIconButton,
  YanjiMiniBarChart,
  YanjiSectionHeader,
} from '../components/YanjiUI';
import { SessionNoteModal } from '../components/SessionNoteModal';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { useBottomTabLayout } from '../navigation/useBottomTabLayout';
import { YanjiRadius, YanjiSpacing } from '../theme/tokens';

import {
  axisLabels,
  buildTimelineEntries,
  countWorkedDays,
  dayHasRecords,
  findTodayColumn,
  formatClockFromEpoch,
  formatDuration,
  formatPercent,
  relativeDayLabel,
  stepForwardWithinToday,
  todayIso,
  shiftIsoDate,
  type TimelineEntry,
} from './reviewViewLogic';

/** Wide enough for a full「2026年10月8日 星期四」label without wrapping. */
const DATE_PILL_MIN_WIDTH = 200;

/** Trend chart height in px. Tall enough for a column to stay legible. */
const TREND_CHART_HEIGHT = 64;

/** Hairline width of a subject share bar. */
const SHARE_BAR_HEIGHT = 4;

/** The subject colour rail on a session row: tall enough to read as a bar. */
const SUBJECT_RAIL_WIDTH = 3;
const SUBJECT_RAIL_HEIGHT = 34;

/** Colour dot beside a subject name in the distribution list. */
const SUBJECT_DOT_SIZE = 8;

/** Vertical padding of the trend scope pills. */
const SCOPE_PILL_PADDING_V = 3;
const SCOPES: ReadonlyArray<{ scope: ReviewScope; label: string }> = [
  { scope: 'ROLLING_7', label: '7 天' },
  { scope: 'ROLLING_30', label: '30 天' },
  { scope: 'CALENDAR_WEEK', label: '本周' },
  { scope: 'CALENDAR_MONTH', label: '本月' },
];

export function ReviewScreen(): React.JSX.Element {
  const theme = useYanjiTheme();
  const { contentPadding } = useBottomTabLayout();
  const [date, setDate] = useState(todayIso);
  const [timeline, setTimeline] = useState<DailyTimeline | null>(null);
  const [overview, setOverview] = useState<ReviewOverview | null>(null);
  const [dayTasks, setDayTasks] = useState<StudyTask[]>([]);
  const [scope, setScope] = useState<ReviewScope>('ROLLING_7');
  /** Measured width of the trend-chart column, so the SVG matches its labels. */
  const [chartWidth, setChartWidth] = useState(0);
  const [noteTarget, setNoteTarget] = useState<{
    sessionId: string;
    isExam: boolean;
    title: string;
    note: string;
  } | null>(null);
  const [pendingDelete, setPendingDelete] = useState<{
    sessionId: string;
    isExam: boolean;
    subjectName: string;
  } | null>(null);
  /** Expanded subject row, revealing that subject's per-day split. */
  const [expandedSubject, setExpandedSubject] = useState<string | null>(null);

  const refresh = useCallback(async (targetDate: string, targetScope: ReviewScope) => {
    try {
      const [day, stats, tasks] = await Promise.all([
        YanjiDataNative.getDailyTimeline(targetDate),
        YanjiDataNative.getReviewOverview(targetScope, 0),
        YanjiDataNative.getTodayTasks(targetDate),
      ]);
      setTimeline(day);
      setOverview(stats);
      setDayTasks(tasks);
    } catch {
      // Keep the previous snapshot rather than inventing entries.
    }
  }, []);

  useEffect(() => {
    void refresh(date, scope);
  }, [date, scope, refresh]);

  useEffect(() => {
    const unsub = onDataChanged(() => void refresh(date, scope));
    return () => unsub();
  }, [date, scope, refresh]);

  // Switching the window invalidates an open drill-down: its day labels belong
  // to the previous window and would otherwise be shown against a new chart.
  useEffect(() => {
    setExpandedSubject(null);
  }, [scope]);

  const entries = useMemo(() => buildTimelineEntries(timeline), [timeline]);

  // A planned-but-unfinished task is still something the user did that day:
  // the day has a record even when nothing was completed.
  const hasRecords = dayHasRecords(entries, dayTasks);
  const completedTaskCount = dayTasks.filter(task => task.completed).length;

  const isToday = date === todayIso();
  const relativeLabel = relativeDayLabel(date, todayIso());

  const toggleFavorite = useCallback(async (noteId: string) => {
    try {
      await YanjiDataNative.toggleFavoriteNote(noteId);
    } catch {
      // The notes list re-reads on the next data event; nothing to fake here.
    }
  }, []);

  const removeSession = useCallback(async (sessionId: string, isExam: boolean) => {
    setPendingDelete(null);
    try {
      await YanjiDataNative.deleteSessionRecord(sessionId, isExam);
    } catch {
      // Keep the record on screen when the delete was refused.
    }
  }, []);

  const days = overview?.days ?? [];
  const workedDays = countWorkedDays(days);
  /** The chart column for today, when today falls inside the window at all. */
  const todayColumn = findTodayColumn(days);
  /** Dates whose axis label survives the density thinning, today always kept. */
  const labelledDates = new Set(axisLabels(days).map(day => day.date));

  return (
    <View style={{ flex: 1, backgroundColor: theme.colors.bgPrimary }}>
      <ScrollView
        testID="scroll-review"
        style={{ flex: 1 }}
        contentContainerStyle={{
          paddingHorizontal: YanjiSpacing.page,
          paddingTop: YanjiSpacing.md,
          // Content scrolls behind the dock; its final action can clear the glass.
          paddingBottom: contentPadding,
        }}
      >
        <Text
          style={[
            theme.typography.pageTitle,
            { color: theme.colors.textPrimary, letterSpacing: -0.4 },
          ]}
        >
          回顾
        </Text>

        {/* Date navigation — a small sheet the day label sits in */}
        <View
          style={{
            flexDirection: 'row',
            alignItems: 'center',
            justifyContent: 'space-between',
            alignSelf: 'center',
            marginTop: YanjiSpacing.lg,
            backgroundColor: theme.colors.bgSurface,
            borderRadius: YanjiRadius.full,
            paddingVertical: YanjiSpacing.xs,
            paddingHorizontal: YanjiSpacing.sm,
            minWidth: DATE_PILL_MIN_WIDTH,
          }}
        >
          <YanjiIconButton
            icon="back"
            accessibilityLabel="前一天"
            onPress={() => setDate(prev => shiftIsoDate(prev, -1))}
            iconSize={17}
          />
          <View style={{ alignItems: 'center' }}>
            <Text
              testID="review-date"
              style={{ color: theme.colors.textPrimary, ...theme.typography.valueStrong }}
            >
              {timeline?.formattedDate ?? date}
            </Text>
            {relativeLabel ? (
              <Text style={{ color: theme.colors.textTertiary, ...theme.typography.axisLabel }}>
                {relativeLabel}
              </Text>
            ) : null}
          </View>
          {/* Today is a hard ceiling: a day that has not happened yet holds no record. */}
          <YanjiIconButton
            icon="forward"
            accessibilityLabel="后一天"
            disabled={isToday}
            onPress={() => setDate(prev => stepForwardWithinToday(prev, todayIso()))}
            iconSize={17}
          />
        </View>

        {!isToday ? (
          <Pressable
            onPress={() => setDate(todayIso())}
            accessibilityRole="button"
            accessibilityLabel="回到今天"
            style={({ pressed }) => ({
              alignSelf: 'center',
              marginTop: YanjiSpacing.sm,
              paddingVertical: YanjiSpacing.xs,
              paddingHorizontal: YanjiSpacing.md,
              borderRadius: YanjiRadius.full,
              backgroundColor: theme.colors.bgElevated,
              opacity: pressed ? 0.7 : 1,
            })}
          >
            <Text
              style={{
                color: theme.colors.accentPrimary,
                ...theme.typography.caption,
                fontWeight: '600',
              }}
            >
              回到今天
            </Text>
          </Pressable>
        ) : null}

        {/* The day's record */}
        <YanjiSectionHeader title="这一天的记录" />
        {!hasRecords ? (
          <YanjiCard variant="sunken">
            <YanjiEmptyState
              icon="calendar"
              title={relativeLabel ? `${relativeLabel}还没有记录` : '这一天还没有记录'}
              hint="专注、任务与想法都会出现在这里"
            />
          </YanjiCard>
        ) : (
          <View>
            <YanjiCard variant="hero">
              <Text style={[theme.typography.label, { color: theme.colors.textSecondary }]}>
                当日专注
              </Text>
              <Text
                style={[
                  theme.typography.display,
                  {
                    color: theme.colors.textPrimary,
                    marginTop: YanjiSpacing.sm,
                    fontVariant: ['tabular-nums'],
                  },
                ]}
              >
                {timeline ? formatDuration(timeline.totalDurationSeconds) : '—'}
              </Text>
              <YanjiHairline style={{ marginTop: YanjiSpacing.lg, marginBottom: YanjiSpacing.md }} />
              <View style={{ flexDirection: 'row' }}>
                <ReviewStat label="专注" value={`${timeline?.focusCount ?? 0} 段`} />
                <ReviewStat label="完成" value={`${completedTaskCount} / ${dayTasks.length}`} />
                <ReviewStat label="记录" value={`${timeline?.notes.length ?? 0} 篇`} />
              </View>
            </YanjiCard>

            {/* One chronological sheet: sessions, tasks and notes interleaved by
                when they actually happened. */}
            <YanjiCard style={{ marginTop: YanjiSpacing.md, padding: 0 }}>
              {entries.map((entry, index) => (
                <View key={entry.key}>
                  {index > 0 ? <YanjiHairline inset={YanjiSpacing.lg} /> : null}
                  {entry.kind === 'session' ? (
                    <SessionRow
                      session={entry.session}
                      onEdit={() =>
                        setNoteTarget({
                          sessionId: entry.session.id,
                          isExam: entry.session.isExam,
                          title: entry.session.title,
                          note: entry.session.note,
                        })
                      }
                      onDelete={() =>
                        setPendingDelete({
                          sessionId: entry.session.id,
                          isExam: entry.session.isExam,
                          subjectName: entry.session.subjectName,
                        })
                      }
                    />
                  ) : entry.kind === 'task' ? (
                    <TaskRow task={entry.task} />
                  ) : (
                    <NoteRow
                      note={entry}
                      onToggleFavorite={() => void toggleFavorite(entry.noteId)}
                    />
                  )}
                </View>
              ))}
            </YanjiCard>
          </View>
        )}

        {/* Secondary statistics */}
        <YanjiSectionHeader
          title="学习趋势"
          rightAction={
            <View style={{ flexDirection: 'row', alignItems: 'center' }}>
              {SCOPES.map(item => (
                <Pressable
                  key={item.scope}
                  onPress={() => setScope(item.scope)}
                  accessibilityRole="button"
                  accessibilityLabel={`趋势范围 ${item.label}`}
                  accessibilityState={{ selected: scope === item.scope }}
                  hitSlop={6}
                  style={{
                    marginLeft: YanjiSpacing.sm,
                    paddingHorizontal: YanjiSpacing.sm,
                    paddingVertical: SCOPE_PILL_PADDING_V,
                    borderRadius: YanjiRadius.full,
                    backgroundColor:
                      scope === item.scope ? theme.colors.accentSoft : 'transparent',
                  }}
                >
                  <Text
                    style={{
                      color:
                        scope === item.scope
                          ? theme.colors.accentPrimary
                          : theme.colors.textTertiary,
                      ...theme.typography.axisLabel,
                      fontWeight: '600',
                    }}
                  >
                    {item.label}
                  </Text>
                </Pressable>
              ))}
            </View>
          }
        />

        {days.length === 0 ? (
          <YanjiCard variant="sunken">
            <YanjiEmptyState icon="chartMinimal" title="暂无统计数据" />
          </YanjiCard>
        ) : (
          <YanjiCard>
            <View
              style={{
                flexDirection: 'row',
                justifyContent: 'space-between',
                alignItems: 'baseline',
                paddingBottom: YanjiSpacing.md,
              }}
            >
              <Text style={[theme.typography.caption, { color: theme.colors.textSecondary }]}>
                {overview?.label} · 日均
              </Text>
              <Text style={{ color: theme.colors.textPrimary, ...theme.typography.valueStrong }}>
                {overview ? formatDuration(overview.dailyAverageSeconds) : '—'}
              </Text>
            </View>

            {/* The chart is measured, not given a magic width: it must line up
                with the day labels below it on any screen width. */}
            <View
              onLayout={(event: LayoutChangeEvent) =>
                setChartWidth(event.nativeEvent.layout.width)
              }
              accessible
              accessibilityRole="image"
              accessibilityLabel={trendAccessibilityLabel(overview)}
            >
              <YanjiMiniBarChart
                data={days.map(day => day.durationSeconds)}
                width={chartWidth}
                height={TREND_CHART_HEIGHT}
                highlightIndex={todayColumn ? days.indexOf(todayColumn) : undefined}
                emptyLabel="暂无统计数据"
              />

              {/* Day labels sit under the chart, aligned to their column. A
                  31-day month gives each column ~30px, which cannot hold「28日」
                  — labelling every day turned the axis into an unreadable wall. */}
              <View style={{ flexDirection: 'row', marginTop: YanjiSpacing.sm }}>
                {days.map(day => {
                  if (!labelledDates.has(day.date)) {
                    // Keep the cell so labels stay aligned with the bars above.
                    return <View key={day.date} style={{ flex: 1 }} />;
                  }
                  return (
                    <Text
                      key={day.date}
                      // A thinned column is still only ~30px wide; without these
                      // two props「10日」wraps into「1」/「0日」 and reads as noise.
                      numberOfLines={1}
                      adjustsFontSizeToFit
                      minimumFontScale={0.75}
                      style={{
                        flex: 1,
                        textAlign: 'center',
                        color: day.isToday ? theme.colors.accentPrimary : theme.colors.textTertiary,
                        fontVariant: ['tabular-nums'],
                        ...theme.typography.axisLabel,
                      }}
                    >
                      {day.dayLabel}
                    </Text>
                  );
                })}
              </View>
            </View>

            {/* A bare column is unreadable: the selected day needs its number. */}
            {todayColumn ? (
              <View style={{ marginTop: YanjiSpacing.md }}>
                <Text style={[theme.typography.caption, { color: theme.colors.textSecondary }]}>
                  今天 · 当日
                </Text>
                <Text
                  style={{
                    color: theme.colors.textPrimary,
                    ...theme.typography.valueStrong,
                    marginTop: 2,
                    fontVariant: ['tabular-nums'],
                  }}
                >
                  {formatDuration(todayColumn.durationSeconds)}
                </Text>
              </View>
            ) : null}

            <YanjiHairline style={{ marginTop: YanjiSpacing.md }} />

            <View style={{ flexDirection: 'row', marginTop: YanjiSpacing.md }}>
              <ReviewStat label="累计" value={overview ? formatDuration(overview.totalSeconds) : '—'} />
              <ReviewStat label="有记录" value={`${workedDays} / ${days.length} 天`} />
              <ReviewStat label="模考" value={`${overview?.examCount ?? 0} 场`} />
            </View>
          </YanjiCard>
        )}

        {overview && overview.subjectDistribution.length > 0 ? (
          <>
            <YanjiSectionHeader title="科目分布" />
            <YanjiCard style={{ padding: 0 }}>
              {overview.subjectDistribution.map((slice, index) => (
                <View key={slice.subjectId}>
                  {index > 0 ? <YanjiHairline inset={YanjiSpacing.lg} /> : null}
                  <SubjectRow
                    slice={slice}
                    days={days}
                    expanded={expandedSubject === slice.subjectId}
                    onToggle={() =>
                      setExpandedSubject(prev => (prev === slice.subjectId ? null : slice.subjectId))
                    }
                  />
                </View>
              ))}
            </YanjiCard>
          </>
        ) : null}
      </ScrollView>

      <SessionNoteModal
        visible={noteTarget !== null}
        sessionId={noteTarget?.sessionId ?? null}
        sessionTitle={noteTarget?.title ?? ''}
        initialNote={noteTarget?.note ?? ''}
        examSession={noteTarget?.isExam ?? false}
        onClose={() => setNoteTarget(null)}
        onSaved={() => setNoteTarget(null)}
      />

      {pendingDelete ? (
        <YanjiCard
          style={{
            position: 'absolute',
            left: YanjiSpacing.page,
            right: YanjiSpacing.page,
            // Sit above the floating tab bar: a fixed offset put the confirm
            // buttons behind the glass capsule, so 删除 was unreachable.
            bottom: contentPadding + YanjiSpacing.sm,
          }}
        >
          <Text style={[theme.typography.body, { color: theme.colors.textPrimary }]}>
            删除「{pendingDelete.subjectName}」这条记录？
          </Text>
          <Text
            style={[
              theme.typography.caption,
              { color: theme.colors.textTertiary, marginTop: YanjiSpacing.xs },
            ]}
          >
            该时段与它记录的时长会一并消失，无法恢复。
          </Text>
          <View
            style={{
              flexDirection: 'row',
              justifyContent: 'flex-end',
              marginTop: YanjiSpacing.md,
            }}
          >
            <Pressable
              onPress={() => setPendingDelete(null)}
              accessibilityRole="button"
              accessibilityLabel="取消删除记录"
              hitSlop={8}
              style={{
                paddingVertical: YanjiSpacing.sm,
                paddingHorizontal: YanjiSpacing.md,
              }}
            >
              <Text style={{ color: theme.colors.textSecondary, ...theme.typography.body }}>
                取消
              </Text>
            </Pressable>
            <Pressable
              onPress={() => void removeSession(pendingDelete.sessionId, pendingDelete.isExam)}
              accessibilityRole="button"
              accessibilityLabel="确认删除记录"
              hitSlop={8}
              style={{
                paddingVertical: YanjiSpacing.sm,
                paddingHorizontal: YanjiSpacing.md,
                marginLeft: YanjiSpacing.sm,
              }}
            >
              <Text style={{ color: theme.colors.danger, ...theme.typography.bodyStrong }}>
                删除
              </Text>
            </Pressable>
          </View>
        </YanjiCard>
      ) : null}
    </View>
  );
}

/**
 * One spoken sentence for the chart. TalkBack users must not be left with an
 * unlabelled SVG, and DESIGN.md requires that colour is never the only cue.
 */
function trendAccessibilityLabel(overview: ReviewOverview | null): string {
  if (!overview) return '学习趋势暂无数据';
  const worked = overview.days.filter(day => !day.isFuture && day.durationSeconds > 0);
  if (worked.length === 0) return `${overview.label}内没有专注记录`;
  const best = worked.reduce((a, b) => (b.durationSeconds > a.durationSeconds ? b : a));
  return (
    `${overview.label}共 ${overview.windowDays} 天，${worked.length} 天有记录，` +
    `累计 ${formatDuration(overview.totalSeconds)}。` +
    `最长的一天是 ${best.date.slice(5)}，${formatDuration(best.durationSeconds)}`
  );
}

/** Tally strip used in both the hero sheet and the trend sheet. */
function ReviewStat({ label, value }: { label: string; value: string }): React.JSX.Element {
  const theme = useYanjiTheme();
  return (
    <View style={{ flex: 1 }}>
      <Text style={[theme.typography.label, { color: theme.colors.textTertiary }]}>{label}</Text>
      <Text
        style={{
          color: theme.colors.textPrimary,
          ...theme.typography.valueStrong,
          marginTop: 2,
        }}
      >
        {value}
      </Text>
    </View>
  );
}

function SessionRow({
  session,
  onEdit,
  onDelete,
}: {
  session: DailyTimelineSession;
  onEdit: () => void;
  onDelete: () => void;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  return (
    <View
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        paddingVertical: YanjiSpacing.md,
        paddingHorizontal: YanjiSpacing.lg,
      }}
    >
      {/* The subject's own colour, so a long day can be scanned by colour. */}
      <View
        style={{
          width: SUBJECT_RAIL_WIDTH,
          height: SUBJECT_RAIL_HEIGHT,
          borderRadius: YanjiRadius.full,
          backgroundColor: session.subjectColor,
          marginRight: YanjiSpacing.md,
        }}
      />
      <Pressable
        onPress={onEdit}
        accessibilityRole="button"
        accessibilityLabel={
          // The subject, not session.title: title falls back to the note text once
          // one is written, so a labelled row would announce the note twice.
          `${session.subjectName}${session.isExam ? ' 模考' : ' 专注'}，` +
          `${formatClockFromEpoch(session.startTime)} 起，` +
          `时长 ${formatDuration(session.durationSeconds)}，` +
          (session.isExam ? '模考暂不支持编辑随笔' : session.note ? `随笔：${session.note}` : '添加随笔')
        }
        style={{ flex: 1 }}
      >
        <Text style={{ color: theme.colors.textPrimary, ...theme.typography.rowTitle }}>
          {session.subjectName}
          {session.isExam ? ' · 模考' : ''}
        </Text>
        <Text
          style={{
            color: theme.colors.textTertiary,
            ...theme.typography.meta,
            marginTop: 2,
          }}
        >
          {formatClockFromEpoch(session.startTime)} 起 · {formatDuration(session.durationSeconds)}
        </Text>
        {session.note ? (
          <Text
            numberOfLines={2}
            style={{
              color: theme.colors.textSecondary,
              ...theme.typography.rowTitle,
              marginTop: YanjiSpacing.xs,
            }}
          >
            {session.note}
          </Text>
        ) : null}
      </Pressable>
      <YanjiIconButton
        icon="delete"
        accessibilityLabel={
          `删除记录 ${session.subjectName}，${formatClockFromEpoch(session.startTime)} 起，` +
          `${formatDuration(session.durationSeconds)}`
        }
        onPress={onDelete}
        iconSize={16}
      />
    </View>
  );
}

function TaskRow({ task }: { task: StudyTask }): React.JSX.Element {
  const theme = useYanjiTheme();
  return (
    <View
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        paddingVertical: YanjiSpacing.md,
        paddingHorizontal: YanjiSpacing.lg,
      }}
    >
      <View style={{ marginRight: YanjiSpacing.md }}>
        <YanjiIcon name="check" size={14} color={theme.colors.success} strokeWidth={2.5} />
      </View>
      <Text
        numberOfLines={2}
        style={{ color: theme.colors.textPrimary, ...theme.typography.rowTitle, flex: 1 }}
      >
        {task.title}
      </Text>
      <Text style={{ color: theme.colors.textTertiary, ...theme.typography.meta }}>
        {task.subjectName}
      </Text>
    </View>
  );
}

function NoteRow({
  note,
  onToggleFavorite,
}: {
  note: Extract<TimelineEntry, { kind: 'note' }>;
  onToggleFavorite: () => void;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  return (
    <View style={{ paddingVertical: YanjiSpacing.md, paddingHorizontal: YanjiSpacing.lg }}>
      <Text style={{ color: theme.colors.textPrimary, ...theme.typography.body, lineHeight: 22 }}>
        {note.content}
      </Text>
      <View style={{ flexDirection: 'row', alignItems: 'center', marginTop: YanjiSpacing.xs }}>
        <Text style={{ color: theme.colors.textTertiary, ...theme.typography.meta }}>
          {formatClockFromEpoch(note.timestamp)}
        </Text>
        <View style={{ flex: 1 }} />
        <YanjiIconButton
          icon={note.isFavorite ? 'star' : 'starOutline'}
          accessibilityLabel={note.isFavorite ? '取消收藏这条记录' : '收藏这条记录'}
          selected={note.isFavorite}
          onPress={onToggleFavorite}
          iconSize={16}
        />
      </View>
    </View>
  );
}

function SubjectRow({
  slice,
  days,
  expanded,
  onToggle,
}: {
  slice: ReviewSubjectSlice;
  days: ReadonlyArray<ReviewPeriodDay>;
  expanded: boolean;
  onToggle: () => void;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const widthPercent = Math.max(0, Math.min(1, slice.share)) * 100;
  // Only days this subject actually has minutes on are worth listing; a column
  // of zeroes is noise, not detail.
  const activeDays = days.filter(day => (slice.dailyMinutes[day.date] ?? 0) > 0);
  const peak = Math.max(1, ...activeDays.map(day => slice.dailyMinutes[day.date] ?? 0));

  return (
    <View style={{ paddingVertical: YanjiSpacing.md, paddingHorizontal: YanjiSpacing.lg }}>
      <Pressable
        onPress={onToggle}
        accessibilityRole="button"
        accessibilityState={{ expanded }}
        accessibilityLabel={
          `${slice.subjectName}，${formatDuration(slice.minutes * 60)}，占 ${formatPercent(slice.share)}，` +
          (activeDays.length > 0 ? '展开查看每日分布' : '窗口内没有记录')
        }
        hitSlop={4}
      >
        <View style={{ flexDirection: 'row', alignItems: 'center' }}>
          <View
            style={{
              width: SUBJECT_DOT_SIZE,
              height: SUBJECT_DOT_SIZE,
              borderRadius: YanjiRadius.full,
              backgroundColor: slice.subjectColor,
              marginRight: YanjiSpacing.sm,
            }}
          />
          <Text style={{ color: theme.colors.textPrimary, ...theme.typography.rowTitle, flex: 1 }}>
            {slice.subjectName}
          </Text>
          <Text
            style={{
              color: theme.colors.textTertiary,
              ...theme.typography.meta,
              marginRight: YanjiSpacing.sm,
              fontVariant: ['tabular-nums'],
            }}
          >
            {formatPercent(slice.share)}
          </Text>
          <Text
            style={{
              color: theme.colors.textSecondary,
              ...theme.typography.rowTitle,
              fontVariant: ['tabular-nums'],
            }}
          >
            {formatDuration(slice.minutes * 60)}
          </Text>
        </View>
        {/* A ten-subject list is unreadable as bare text — the bar carries the shape. */}
        <View
          style={{
            height: SHARE_BAR_HEIGHT,
            borderRadius: YanjiRadius.full,
            backgroundColor: theme.colors.bgElevated,
            marginTop: YanjiSpacing.sm,
          }}
        >
          <View
            style={{
              width: `${widthPercent}%`,
              height: SHARE_BAR_HEIGHT,
              borderRadius: YanjiRadius.full,
              backgroundColor: slice.subjectColor,
            }}
          />
        </View>
      </Pressable>

      {expanded && activeDays.length > 0 ? (
        <View style={{ marginTop: YanjiSpacing.md }}>
          {activeDays.map((day, index) => {
            const minutes = slice.dailyMinutes[day.date] ?? 0;
            return (
              <View key={day.date}>
                {index > 0 ? <YanjiHairline /> : null}
                <View
                  style={{
                    flexDirection: 'row',
                    alignItems: 'center',
                    paddingVertical: YanjiSpacing.xs,
                  }}
                >
                  <Text
                    style={{
                      color: theme.colors.textTertiary,
                      ...theme.typography.meta,
                      width: 44,
                    }}
                  >
                    {day.date.slice(5)}
                  </Text>
                  <View
                    style={{
                      flex: 1,
                      height: SHARE_BAR_HEIGHT,
                      borderRadius: YanjiRadius.full,
                      backgroundColor: theme.colors.bgElevated,
                      marginHorizontal: YanjiSpacing.sm,
                    }}
                  >
                    <View
                      style={{
                        width: `${(minutes / peak) * 100}%`,
                        height: SHARE_BAR_HEIGHT,
                        borderRadius: YanjiRadius.full,
                        backgroundColor: slice.subjectColor,
                      }}
                    />
                  </View>
                  <Text
                    style={{
                      color: theme.colors.textSecondary,
                      ...theme.typography.meta,
                      width: 56,
                      textAlign: 'right',
                      fontVariant: ['tabular-nums'],
                    }}
                  >
                    {formatDuration(minutes * 60)}
                  </Text>
                </View>
              </View>
            );
          })}
        </View>
      ) : null}
    </View>
  );
}
