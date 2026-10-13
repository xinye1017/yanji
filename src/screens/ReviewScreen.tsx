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

import React, { useCallback, useMemo, useState } from 'react';
import { LayoutChangeEvent, Pressable, ScrollView, Text, View } from 'react-native';
import { YanjiDataNative } from '../bridge';
import type {
  DailySession,
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
  YanjiLineChart,
  YanjiSectionHeader,
  YanjiStackedBarChart,
} from '../components/YanjiUI';
import { YanjiPieChart } from '../components/YanjiPieChart';
import { RecordMomentModal } from '../components/RecordMomentModal';
import { SessionNoteModal } from '../components/SessionNoteModal';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { useBottomTabLayout } from '../navigation/useBottomTabLayout';
import { YanjiRadius, YanjiSpacing } from '../theme/tokens';
import {
  useDailySnapshot,
  useReviewDataVisibility,
  useReviewOverview,
} from '../state/dataRefresh';

import {
  buildHourBuckets,
  buildTimelineEntries,
  buildTrendColumns,
  computeAxisLabelsLayout,
  countElapsedDays,
  countWorkedDays,
  dayHasRecords,
  formatClockFromEpoch,
  formatDailyAverageDuration,
  formatDuration,
  formatMonthDaySlash,
  formatPercent,
  lineCenters,
  peakHourBucket,
  peakTrendDay,
  periodDelta,
  previousWindowArgs,
  relativeDayLabel,
  stepForwardWithinToday,
  todayIso,
  shiftIsoDate,
  type TimelineEntry,
} from './reviewViewLogic';

/** Wide enough for a full「2026年10月8日 星期四」label without wrapping. */
const DATE_PILL_MIN_WIDTH = 130;

/** Trend chart height in px. Tall enough for a column to stay legible. */
const TREND_CHART_HEIGHT = 64;


/** Colour dot beside a subject name in the distribution list. */
const SUBJECT_DOT_SIZE = 8;

/** Vertical padding of the trend scope pills. */
const SCOPE_PILL_PADDING_V = 3;
const SCOPES: ReadonlyArray<{ scope: ReviewScope; label: string }> = [
  { scope: 'TODAY', label: '今天' },
  { scope: 'CALENDAR_WEEK', label: '本周' },
  { scope: 'CALENDAR_MONTH', label: '本月' },
];

/** Hour ticks under the single-day curve; all 24 would crowd the axis. */
const HOUR_TICKS = [0, 6, 12, 18];

export function ReviewScreen({ visible }: { visible: boolean }): React.JSX.Element {
  const theme = useYanjiTheme();
  const { contentPadding } = useBottomTabLayout();
  const [date, setDate] = useState(todayIso);
  const [scope, setScope] = useState<ReviewScope>('TODAY');
  const snapshot = useDailySnapshot('review', date, visible);
  const overviewRequest = useMemo(
    () => ({ scope, periodsBack: 0, anchorDate: date }),
    [date, scope]
  );
  const previousRequest = useMemo(() => previousWindowArgs(scope, date), [date, scope]);
  const reviewRequests = useMemo(
    () => [overviewRequest, previousRequest],
    [overviewRequest, previousRequest]
  );
  const overview = useReviewOverview(overviewRequest, visible);
  const previousOverview = useReviewOverview(previousRequest, visible);
  useReviewDataVisibility('review', reviewRequests, visible);
  const dayTasks = snapshot?.tasks ?? [];
  const sessions = snapshot?.sessions ?? [];
  const prevTotalSeconds = previousOverview?.totalSeconds ?? null;
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
  /** The quick note being edited, or null when the editor is closed. */
  const [editingNote, setEditingNote] = useState<{ id: string; content: string } | null>(null);

  const entries = useMemo(() => buildTimelineEntries(snapshot), [snapshot]);

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
  /** Days that have already happened — the denominator matching `workedDays`. */
  const elapsedDays = countElapsedDays(days);
  /** The chart's columns: elapsed days only, each stacked by subject. */
  const columns = useMemo(
    () => buildTrendColumns(days, overview?.subjectDistribution ?? []),
    [days, overview]
  );
  const peak = peakTrendDay(columns);
  const delta = periodDelta(overview?.totalSeconds ?? 0, prevTotalSeconds);
  /** The single-day view: how the selected day's focus sits across its 24 hours. */
  const hourBuckets = useMemo(
    () => (scope === 'TODAY' ? buildHourBuckets(sessions) : []),
    [scope, sessions]
  );
  const peakHour = peakHourBucket(hourBuckets);
  const hourCenters = useMemo(() => lineCenters(24, chartWidth), [chartWidth]);
  /** Exact collision-free positioned axis labels. The month curve spaces its
      points edge-to-edge, so its labels sit on line centers, not bar centers. */
  const axisLabelItems = useMemo(
    () =>
      computeAxisLabelsLayout(columns, chartWidth, {
        centers: scope === 'CALENDAR_MONTH' ? lineCenters(columns.length, chartWidth) : undefined,
      }),
    [columns, chartWidth, scope]
  );

  return (
    <View style={{ flex: 1, backgroundColor: theme.colors.bgPrimary }}>
      <ScrollView
        testID="scroll-review"
        style={{ flex: 1 }}
        contentContainerStyle={{
          paddingHorizontal: YanjiSpacing.page,
          paddingTop: YanjiSpacing.sm,
          // Content scrolls behind the dock; its final action can clear the glass.
          paddingBottom: contentPadding,
        }}
      >
        <View style={{ marginBottom: YanjiSpacing.lg }}>
          <Text accessibilityRole="header" style={[theme.typography.pageTitle, { color: theme.colors.textPrimary }]}>回顾</Text>
          <Text style={[theme.typography.caption, { color: theme.colors.textSecondary, marginTop: 4 }]}>这一天的专注、计划和想法</Text>
        </View>

        {/* Date navigation */}
        <View
          style={{
            flexDirection: 'row',
            alignItems: 'center',
            justifyContent: 'space-between',
            flexWrap: 'wrap',
            rowGap: YanjiSpacing.sm,
            columnGap: YanjiSpacing.sm,
            marginBottom: YanjiSpacing.md,
          }}
        >
          <View
            style={{
              flexDirection: 'row',
              alignItems: 'center',
              backgroundColor: theme.colors.bgSurface,
              borderRadius: YanjiRadius.full,
              borderWidth: 1,
              borderColor: theme.colors.cardBorder,
              paddingVertical: YanjiSpacing.xs,
              paddingHorizontal: YanjiSpacing.xs,
              minWidth: DATE_PILL_MIN_WIDTH,
              ...theme.shadow.sheet(theme.isDark),
            }}
          >
            <YanjiIconButton
              icon="back"
              accessibilityLabel="前一天"
              onPress={() => setDate(prev => shiftIsoDate(prev, -1))}
              iconSize={16}
            />
            <View style={{ alignItems: 'center', paddingHorizontal: YanjiSpacing.sm }}>
              <Text
                testID="review-date"
                style={{
                  color: theme.colors.textPrimary,
                  ...theme.typography.bodyStrong,
                  fontVariant: ['tabular-nums'],
                }}
              >
                {formatMonthDaySlash(date)}
              </Text>
              {relativeLabel ? (
                <Text
                  style={{
                    color: theme.colors.textTertiary,
                    ...theme.typography.axisLabel,
                    marginTop: -2,
                  }}
                >
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
              iconSize={16}
            />
          </View>

          {!isToday ? (
            <Pressable
              onPress={() => setDate(todayIso())}
              accessibilityRole="button"
              accessibilityLabel="回到今天"
              style={({ pressed }) => ({
                paddingVertical: YanjiSpacing.xs + 3,
                paddingHorizontal: YanjiSpacing.md,
                borderRadius: YanjiRadius.full,
                backgroundColor: theme.colors.bgSurface,
                borderWidth: 1,
                borderColor: theme.colors.cardBorder,
                opacity: pressed ? 0.7 : 1,
                ...theme.shadow.sheet(theme.isDark),
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
        </View>

        {/* 当日记录 */}
        <YanjiSectionHeader title="这一天" />
        {!hasRecords ? (
          <YanjiCard variant="plain" style={{ paddingVertical: YanjiSpacing.md }}>
            <YanjiEmptyState
              compact
              icon="calendar"
              title={!snapshot ? '正在读取这一天的记录…' : relativeLabel ? `${relativeLabel}还没有记录` : '今天还没有记录'}
              hint={snapshot ? '每一个专注片段和随笔，都会留在这里' : '若长时间未更新，可以切换日期后重试'}
            />
          </YanjiCard>
        ) : (
          <View>
            {/* One chronological sheet: sessions, tasks and notes interleaved by
                when they actually happened. */}
            <YanjiCard variant="plain" style={{ paddingHorizontal: YanjiSpacing.xs }}>
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
                      onEdit={() =>
                        setEditingNote({ id: entry.noteId, content: entry.content })
                      }
                    />
                  )}
                </View>
              ))}
            </YanjiCard>
            <YanjiCard variant="plain" style={{ marginTop: YanjiSpacing.lg, paddingVertical: YanjiSpacing.md }}>
              <View
                style={{
                  flexDirection: 'row',
                  justifyContent: 'space-between',
                  alignItems: 'baseline',
                }}
              >
                <Text style={[theme.typography.label, { color: theme.colors.textSecondary }]}>
                  当日专注
                </Text>
                <Text
                  style={[
                    theme.typography.sectionTitle,
                    {
                      color: theme.colors.textPrimary,
                      fontVariant: ['tabular-nums'],
                    },
                  ]}
                >
                  {snapshot ? formatDuration(snapshot.totalDurationSeconds) : '—'}
                </Text>
              </View>
              <YanjiHairline style={{ marginTop: YanjiSpacing.sm, marginBottom: YanjiSpacing.sm }} />
              <View style={{ flexDirection: 'row' }}>
                <ReviewStat label="专注" value={`${snapshot?.focusCount ?? 0} 段`} />
                <ReviewStat label="完成" value={`${completedTaskCount} / ${dayTasks.length}`} />
                <ReviewStat label="随笔" value={`${snapshot?.notes.length ?? 0} 篇`} />
              </View>
            </YanjiCard>

          </View>
        )}

        {/* Secondary statistics */}
        <YanjiSectionHeader
          title="学习趋势"
          rightAction={
            <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'flex-end', flexWrap: 'wrap', flexShrink: 1 }}>
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
                    paddingHorizontal: YanjiSpacing.sm + 2,
                    paddingVertical: SCOPE_PILL_PADDING_V + 1,
                    borderRadius: YanjiRadius.full,
                    backgroundColor:
                      scope === item.scope ? theme.colors.accentSoft : 'transparent',
                    borderWidth: 1,
                    borderColor:
                      scope === item.scope ? theme.colors.accentPrimary : theme.colors.cardBorder,
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
          <YanjiCard variant="plain" style={{ paddingVertical: YanjiSpacing.lg }}>
            <YanjiEmptyState compact icon="chartMinimal" title="暂无统计数据" />
          </YanjiCard>
        ) : (
          <YanjiCard variant="plain" style={{ paddingVertical: YanjiSpacing.sm }}>
            <View
              style={{
                flexDirection: 'row',
                justifyContent: 'space-between',
                alignItems: 'flex-start',
                flexWrap: 'wrap',
                rowGap: YanjiSpacing.sm,
                columnGap: YanjiSpacing.sm,
                paddingBottom: YanjiSpacing.md,
              }}
            >
              <View>
                <Text style={[theme.typography.caption, { color: theme.colors.textSecondary }]}>
                  {overview?.label}
                </Text>
                <Text
                  style={[
                    theme.typography.meta,
                    {
                      color:
                        delta.tone === 'up'
                          ? theme.colors.success
                          : delta.tone === 'down'
                            ? theme.colors.danger
                            : theme.colors.textTertiary,
                      marginTop: 2,
                    },
                  ]}
                >
                  {delta.label}
                </Text>
              </View>
              {/* A single day has no "daily average" — the day sheet above already
                  carries its total, so the header keeps only the comparison. */}
              {scope === 'TODAY' ? null : (
                <View style={{ flexDirection: 'row', flexWrap: 'wrap', alignItems: 'baseline', maxWidth: '100%' }}>
                  <Text
                    style={[
                      theme.typography.caption,
                      { color: theme.colors.textTertiary, marginRight: 6 },
                    ]}
                  >
                    平均学习时长
                  </Text>
                  <Text
                    style={{
                      color: theme.colors.textPrimary,
                      ...theme.typography.valueStrong,
                      fontVariant: ['tabular-nums'],
                    }}
                  >
                    {overview ? formatDailyAverageDuration(overview.dailyAverageSeconds) : '—'}
                  </Text>
                </View>
              )}
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
              {scope === 'TODAY' ? (
                <YanjiLineChart
                  values={hourBuckets.map(bucket => bucket.minutes)}
                  width={chartWidth}
                  height={TREND_CHART_HEIGHT}
                  peakIndex={peakHour ? peakHour.hour : undefined}
                  emptyLabel="暂无统计数据"
                />
              ) : scope === 'CALENDAR_MONTH' ? (
                <YanjiLineChart
                  values={columns.map(column => column.totalMinutes)}
                  width={chartWidth}
                  height={TREND_CHART_HEIGHT}
                  averageValue={overview ? overview.dailyAverageSeconds / 60 : undefined}
                  averageLabel="日均"
                  peakIndex={peak ? columns.indexOf(peak) : undefined}
                  emptyLabel="暂无统计数据"
                />
              ) : (
                <YanjiStackedBarChart
                  columns={columns}
                  width={chartWidth}
                  height={TREND_CHART_HEIGHT}
                  averageMinutes={overview ? overview.dailyAverageSeconds / 60 : undefined}
                  averageLabel="日均"
                  emptyLabel="暂无统计数据"
                />
              )}

              {/* Axis labels sit under the chart, aligned to their point. Each
                  label is placed at its exact center and guaranteed not to
                  collide or truncate. */}
              {scope === 'TODAY' ? (
                <View style={{ height: 16, marginTop: YanjiSpacing.sm, width: chartWidth || '100%' }}>
                  {HOUR_TICKS.map(hour => (
                    <Text
                      key={hour}
                      numberOfLines={1}
                      style={{
                        position: 'absolute',
                        left: Math.max(0, Math.min(chartWidth - 38, (hourCenters[hour] ?? 0) - 19)),
                        width: 38,
                        textAlign: 'center',
                        color: theme.colors.textTertiary,
                        fontVariant: ['tabular-nums'],
                        ...theme.typography.axisLabel,
                      }}
                    >
                      {hour}时
                    </Text>
                  ))}
                </View>
              ) : (
                <View style={{ height: 16, marginTop: YanjiSpacing.sm, width: chartWidth || '100%' }}>
                  {axisLabelItems.map(item => (
                    <Text
                      key={item.date}
                      numberOfLines={1}
                      style={{
                        position: 'absolute',
                        left: item.left,
                        width: item.width,
                        textAlign: 'center',
                        color: item.isToday ? theme.colors.accentPrimary : theme.colors.textTertiary,
                        fontVariant: ['tabular-nums'],
                        ...theme.typography.axisLabel,
                      }}
                    >
                      {item.dayLabel}
                    </Text>
                  ))}
                </View>
              )}

              {scope === 'TODAY' ? (
                peakHour ? (
                  <Text
                    style={[
                      theme.typography.meta,
                      { color: theme.colors.textTertiary, marginTop: YanjiSpacing.xs },
                    ]}
                  >
                    最专注的时段 {peakHour.hour}时 · {formatDuration(peakHour.minutes * 60)}
                  </Text>
                ) : null
              ) : peak ? (
                <Text
                  style={[
                    theme.typography.meta,
                    { color: theme.colors.textTertiary, marginTop: YanjiSpacing.xs },
                  ]}
                >
                  最长的一天 {peak.dayLabel} · {formatDuration(peak.totalMinutes * 60)}
                </Text>
              ) : null}
            </View>

            {scope === 'TODAY' ? null : (
              <>
                <YanjiHairline style={{ marginTop: YanjiSpacing.md }} />

                <View style={{ flexDirection: 'row', marginTop: YanjiSpacing.md }}>
                  <ReviewStat
                    label="累计"
                    value={overview ? formatDuration(overview.totalSeconds) : '—'}
                  />
                  <ReviewStat label="有记录" value={`${workedDays} / ${elapsedDays} 天`} />
                  <ReviewStat label="模考" value={`${overview?.examCount ?? 0} 场`} />
                </View>
              </>
            )}
          </YanjiCard>
        )}

        {overview && overview.subjectDistribution.length > 0 ? (
          <>
            {/* The distribution follows the trend scope above; say which one, or
                the pie silently changes when the user switches 7天 / 本月. */}
            <YanjiSectionHeader
              title="科目分布"
              rightAction={
                <Text style={[theme.typography.caption, { color: theme.colors.textTertiary }]}>
                  {overview.label}
                </Text>
              }
            />
            <YanjiCard variant="plain" style={{ paddingVertical: YanjiSpacing.md }}>
              <YanjiPieChart
                slices={overview.subjectDistribution}
                size={144}
                innerRadiusRatio={0.55}
                centerTitle={formatDuration(overview.totalSeconds)}
                centerCaption="总时长"
                style={{ marginVertical: YanjiSpacing.xs }}
              />

              <YanjiHairline style={{ marginTop: YanjiSpacing.lg, marginBottom: YanjiSpacing.sm }} />

              {/* Legend: 色点 + 学科名称 + 百分比 + 学习时长 */}
              <View>
                {overview.subjectDistribution.map((slice, index) => (
                  <View key={slice.subjectId}>
                    {index > 0 ? (
                      <YanjiHairline inset={0} style={{ marginVertical: YanjiSpacing.xs }} />
                    ) : null}
                    <View
                      style={{
                        flexDirection: 'row',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        paddingVertical: YanjiSpacing.xs + 2,
                      }}
                      accessible
                      accessibilityLabel={`${slice.subjectName}，占比 ${formatPercent(slice.share)}，时长 ${formatDuration(slice.minutes * 60)}`}
                    >
                      <View
                        style={{
                          flexDirection: 'row',
                          alignItems: 'center',
                          flex: 1,
                          marginRight: YanjiSpacing.md,
                        }}
                      >
                        <View
                          style={{
                            width: SUBJECT_DOT_SIZE,
                            height: SUBJECT_DOT_SIZE,
                            borderRadius: YanjiRadius.full,
                            backgroundColor: slice.subjectColor,
                            marginRight: YanjiSpacing.sm,
                          }}
                        />
                        <Text
                          numberOfLines={1}
                          style={{
                            color: theme.colors.textPrimary,
                            ...theme.typography.rowTitle,
                            flex: 1,
                          }}
                        >
                          {slice.subjectName}
                        </Text>
                      </View>
                      <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                        <Text
                          style={{
                            color: theme.colors.textTertiary,
                            ...theme.typography.meta,
                            marginRight: YanjiSpacing.md,
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
                    </View>
                  </View>
                ))}
              </View>
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

      <RecordMomentModal
        visible={editingNote !== null}
        date={date}
        editingNote={editingNote}
        onClose={() => setEditingNote(null)}
        onSaved={() => setEditingNote(null)}
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
    `${overview.label}已过 ${countElapsedDays(overview.days)} 天，${worked.length} 天有记录，` +
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
  session: DailySession;
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
      {/* 22x22 timeline node matching TaskRow and NoteRow for strict horizontal alignment */}
      <View
        style={{
          width: 22,
          height: 22,
          borderRadius: YanjiRadius.full,
          backgroundColor: session.subjectColor ? `${session.subjectColor}20` : theme.colors.accentSoft,
          borderWidth: 1,
          borderColor: theme.colors.cardBorder,
          alignItems: 'center',
          justifyContent: 'center',
          marginRight: YanjiSpacing.md,
        }}
      >
        <View
          style={{
            width: 8,
            height: 8,
            borderRadius: YanjiRadius.full,
            backgroundColor: session.subjectColor || theme.colors.accentPrimary,
          }}
        />
      </View>
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
      <View
        style={{
          width: 22,
          height: 22,
          borderRadius: YanjiRadius.full,
          backgroundColor: theme.colors.accentSoft,
          borderWidth: 1,
          borderColor: theme.colors.cardBorder,
          alignItems: 'center',
          justifyContent: 'center',
          marginRight: YanjiSpacing.md,
        }}
      >
        <YanjiIcon name="check" size={13} color={theme.colors.success} strokeWidth={2.5} />
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
  onEdit,
}: {
  note: Extract<TimelineEntry, { kind: 'note' }>;
  onToggleFavorite: () => void;
  onEdit: () => void;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  return (
    <View style={{ paddingVertical: YanjiSpacing.md, paddingHorizontal: YanjiSpacing.lg }}>
      <View style={{ flexDirection: 'row', alignItems: 'flex-start' }}>
        <View
          style={{
            width: 22,
            height: 22,
            borderRadius: YanjiRadius.full,
            backgroundColor: theme.colors.bgSunken,
            borderWidth: 1,
            borderColor: theme.colors.cardBorder,
            alignItems: 'center',
            justifyContent: 'center',
            marginRight: YanjiSpacing.md,
            marginTop: 2,
          }}
        >
          <YanjiIcon name="compose" size={12} color={theme.colors.accentPrimary} />
        </View>
        <View style={{ flex: 1 }}>
          <Text style={{ color: theme.colors.textPrimary, ...theme.typography.body, lineHeight: 22 }}>
            {note.content}
          </Text>
          <View style={{ flexDirection: 'row', alignItems: 'center', marginTop: YanjiSpacing.xs }}>
            <Text style={{ color: theme.colors.textTertiary, ...theme.typography.meta }}>
              {formatClockFromEpoch(note.timestamp)}
            </Text>
            <View style={{ flex: 1 }} />
            <YanjiIconButton
              icon="compose"
              accessibilityLabel="编辑这条记录"
              onPress={onEdit}
              iconSize={16}
            />
            <YanjiIconButton
              icon={note.isFavorite ? 'star' : 'starOutline'}
              accessibilityLabel={note.isFavorite ? '取消收藏这条记录' : '收藏这条记录'}
              selected={note.isFavorite}
              onPress={onToggleFavorite}
              iconSize={16}
            />
          </View>
        </View>
      </View>
    </View>
  );
}

