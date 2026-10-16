/** Paper journal: event times are native facts; completed tasks have no time. */
import React, { useCallback, useMemo, useState } from 'react';
import { Modal, Pressable, ScrollView, Text, useWindowDimensions, View } from 'react-native';
import { useReducedMotion } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { YanjiDataNative } from '../bridge';
import type { DailySession, DailySnapshot, ReviewOverview, ReviewScope, ReviewSubjectSlice } from '../bridge';
import {
  YanjiCard, YanjiChronoSpectrum, YanjiEditorialHeader,
  YanjiIcon, YanjiIconButton, YanjiLineChart, YanjiPaperSectionHeading,
  YanjiPaperSegmentedControl, YanjiPaperSheetHeading, YanjiPrimaryButton,
  YanjiStackedBarChart, YanjiSubjectProportionRow,
} from '../components/YanjiUI';
import { RecordMomentModal } from '../components/RecordMomentModal';
import { SessionNoteModal } from '../components/SessionNoteModal';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { useBottomTabLayout } from '../navigation/useBottomTabLayout';
import { YanjiSpacing } from '../theme/tokens';
import {
  dataRefreshCoordinator, refreshDailySnapshot, useDailySnapshot,
  useReviewDataVisibility, useReviewOverview, useSettingsSnapshot,
} from '../state/dataRefresh';
import { lineChartXCenters } from '../components/lineChartGeometry';
import {
  boundTaskTitle, buildFilmstrip, buildHourBuckets, buildTimelineEntries, buildTrendColumns,
  computeAxisLabelsLayout, countElapsedDays, countWorkedDays, dominantSubjectColors,
  formatClockFromEpoch, formatDailyAverageDuration, formatDuration, formatDurationMetric,
  formatPercent, goalReachedDays, longestSessionSeconds, peakHourBucket, peakTrendDay,
  periodDelta, periodDeltaPercent, previousWindowArgs, relativeDayLabel, scopeDateRangeLabel,
  shiftIsoDate, stepForwardWithinToday, todayIso, weekNumberOfYear,
  type FilmstripDay, type TimelineEntry,
} from './reviewViewLogic';

type ReviewView = 'timeline' | 'trend';
const VIEWS = [{ value: 'timeline', label: '时间线' }, { value: 'trend', label: '趋势统计' }] as const;
const SCOPES: readonly { value: ReviewScope; label: string }[] = [
  { value: 'TODAY', label: '当日' }, { value: 'CALENDAR_WEEK', label: '本周' },
  { value: 'CALENDAR_MONTH', label: '本月' },
];
const HOUR_TICKS = [0, 6, 12, 18];
const TREND_CHART_HEIGHT = 108;

export function ReviewScreen({ visible }: { visible: boolean }): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout, radius } = paper;
  const { contentPadding } = useBottomTabLayout();
  const { fontScale } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduceMotion = useReducedMotion();
  const [date, setDate] = useState(todayIso);
  const [view, setView] = useState<ReviewView>('timeline');
  const [scope, setScope] = useState<ReviewScope>('TODAY');
  const daily = useDailySnapshot('review', date, visible);
  const snapshot = daily?.date === date ? daily : null;
  const settingsSnapshot = useSettingsSnapshot('review', visible);
  const goalHours = settingsSnapshot?.settings.dailyGoalHours ?? 0;
  const goalSeconds = goalHours > 0 ? goalHours * 3600 : null;
  const overviewRequest = useMemo(() => ({ scope, periodsBack: 0, anchorDate: date }), [date, scope]);
  const previousRequest = useMemo(() => previousWindowArgs(scope, date), [date, scope]);
  /**
   * The filmstrip reads the selected day's own calendar week, which is a
   * different window from the trend scope. It is only needed for its subject
   * dots, so it stays a separate bounded request rather than widening `scope`.
   */
  const weekRequest = useMemo(
    () => ({ scope: 'CALENDAR_WEEK' as ReviewScope, periodsBack: 0, anchorDate: date }),
    [date]
  );
  const reviewRequests = useMemo(
    () => [overviewRequest, previousRequest, weekRequest],
    [overviewRequest, previousRequest, weekRequest]
  );
  const trendVisible = visible && view === 'trend';
  const overview = useReviewOverview(overviewRequest, trendVisible);
  const previousOverview = useReviewOverview(previousRequest, trendVisible);
  // The strip is part of the timeline view, so its week window is read there.
  const weekOverview = useReviewOverview(weekRequest, visible && view === 'timeline');
  useReviewDataVisibility('review', reviewRequests, visible);
  const sessions = snapshot?.sessions ?? [];
  const dayTasks = snapshot?.tasks ?? [];
  const [chartWidth, setChartWidth] = useState(0);
  const [noteTarget, setNoteTarget] = useState<{
    sessionId: string; isExam: boolean; title: string; note: string;
  } | null>(null);
  const [pendingDelete, setPendingDelete] = useState<{
    sessionId: string; isExam: boolean; subjectName: string;
  } | null>(null);
  const [editingNote, setEditingNote] = useState<{ id: string; content: string } | null>(null);
  const [busyNote, setBusyNote] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [refreshing, setRefreshing] = useState(false);
  const entries = useMemo(() => buildTimelineEntries(snapshot), [snapshot]);
  const today = todayIso();
  const isToday = date === today;
  const relativeLabel = relativeDayLabel(date, today);
  const selectedDate = new Date(`${date}T12:00:00`);
  const filmstrip = useMemo(
    () => buildFilmstrip(
      date,
      today,
      dominantSubjectColors(weekOverview?.days ?? [], weekOverview?.subjectDistribution ?? [])
    ),
    [date, today, weekOverview]
  );

  const toggleFavorite = async (noteId: string) => {
    if (busyNote) return;
    setBusyNote(noteId);
    setActionError(null);
    try {
      if (!await YanjiDataNative.toggleFavoriteNote(noteId)) throw new Error('refused');
    } catch { setActionError('收藏未保存，请再次点击收藏重试。'); }
    finally { setBusyNote(null); }
  };
  const removeSession = async () => {
    if (!pendingDelete || deleting) return;
    setDeleting(true);
    setDeleteError(null);
    try {
      if (!await YanjiDataNative.deleteSessionRecord(pendingDelete.sessionId, pendingDelete.isExam)) throw new Error('refused');
      setPendingDelete(null);
    } catch { setDeleteError('删除失败，记录已保留。请重试或取消。'); }
    finally { setDeleting(false); }
  };
  const handleRefresh = useCallback(async () => {
    setRefreshing(true);
    setActionError(null);
    try {
      await Promise.all([
        refreshDailySnapshot(date),
        ...reviewRequests.map(request => dataRefreshCoordinator.refreshReviewOverview(request)),
      ]);
    } catch { setActionError('读取失败，请重新读取。'); }
    finally { setRefreshing(false); }
  }, [date, reviewRequests]);

  const days = overview?.days ?? [];
  const columns = useMemo(() => buildTrendColumns(days, overview?.subjectDistribution ?? []), [days, overview]);
  const peak = peakTrendDay(columns);
  const delta = periodDelta(overview?.totalSeconds ?? 0, previousOverview?.totalSeconds ?? null);
  const deltaPercent = periodDeltaPercent(overview?.totalSeconds ?? 0, previousOverview?.totalSeconds ?? null);
  const goalDays = goalReachedDays(days, goalSeconds);
  const preciseSessions = useMemo(() => {
    const dayStart = new Date(`${date}T00:00:00`).getTime();
    const dayEnd = new Date(`${shiftIsoDate(date, 1)}T00:00:00`).getTime();
    return sessions.filter(session => session.pauseCount === 0 && session.endTime > session.startTime
      && Math.abs((session.endTime - session.startTime) / 1000 - session.durationSeconds) <= 2
      && session.endTime > dayStart && session.startTime < dayEnd).map(session => ({
        startTime: Math.max(session.startTime, dayStart),
        durationSeconds: (Math.min(session.endTime, dayEnd) - Math.max(session.startTime, dayStart)) / 1000,
      }));
  }, [date, sessions]);
  const hourBuckets = useMemo(() => scope === 'TODAY' ? buildHourBuckets(preciseSessions) : [], [scope, preciseSessions]);
  const peakHour = peakHourBucket(hourBuckets);
  const hourCenters = useMemo(() => lineChartXCenters(24, chartWidth, YanjiSpacing.xl), [chartWidth]);
  const axisLabelItems = useMemo(() => computeAxisLabelsLayout(columns, chartWidth, {
    labelWidth: 38 * Math.max(1, fontScale),
    centers: scope === 'CALENDAR_MONTH' ? lineChartXCenters(columns.length, chartWidth, YanjiSpacing.xl) : undefined,
  }), [columns, chartWidth, scope, fontScale]);
  const distribution = overview?.subjectDistribution ?? [];
  const spectrum = distribution.map(slice => ({ id: slice.subjectId, label: slice.subjectName,
    seconds: slice.minutes * 60, color: slice.subjectColor || colors.secondary }));

  return <View style={{ flex: 1, backgroundColor: colors.canvas }}>
    <ScrollView testID="scroll-review" style={{ flex: 1 }}
      showsVerticalScrollIndicator={false} overScrollMode="never"
      contentContainerStyle={{
        paddingHorizontal: layout.pageGutter, paddingTop: YanjiSpacing.lg, paddingBottom: contentPadding,
      }}>
      {/* AB-06 & AB-07 share one header: the eyebrow names the window, the
          switcher rides along as the trailing slot. */}
      <YanjiEditorialHeader
        eyebrow={view === 'trend'
          ? '数据洞察 · 真实记录沉淀'
          : `${selectedDate.getFullYear()} 年 ${selectedDate.getMonth() + 1} 月 · 第 ${weekNumberOfYear(selectedDate)} 周`}
        title="备考手账"
        trailing={
          <YanjiPaperSegmentedControl
            value={view}
            options={VIEWS}
            onChange={setView}
            accessibilityLabel="回顾视图"
            compact
          />
        }
      />
      {actionError ? <Text accessibilityRole="alert" style={[typography.bodyPrimary, { color: colors.brandInk, marginTop: YanjiSpacing.md }]}>{actionError}</Text> : null}

      {view === 'timeline' ? (
        <>
          <WeekFilmstrip cells={filmstrip} onSelect={setDate} />
          <DaySummary snapshot={snapshot} goalSeconds={goalSeconds} />
          <TimelineState loading={!snapshot} entryCount={entries.length} planCount={dayTasks.length} />

          <Timeline
            entries={entries}
            dayTasks={dayTasks}
            busyNote={busyNote !== null}
            onEditSession={session => setNoteTarget({
              sessionId: session.id, isExam: session.isExam, title: session.subjectName, note: session.note,
            })}
            onDeleteSession={session => {
              setDeleteError(null);
              setPendingDelete({ sessionId: session.id, isExam: session.isExam, subjectName: session.subjectName });
            }}
            onToggleFavorite={noteId => void toggleFavorite(noteId)}
            onEditNote={note => setEditingNote({ id: note.noteId, content: note.content })}
          />
        </>
      ) : (
        <>
          <PeriodSelectorBar
            scope={scope}
            anchorDate={date}
            onScopeChange={setScope}
            onPrevious={() => {
              if (scope === 'TODAY') setDate(prev => shiftIsoDate(prev, -1));
              else if (scope === 'CALENDAR_WEEK') setDate(prev => shiftIsoDate(prev, -7));
              else setDate(prev => shiftIsoDate(prev, -30));
            }}
            onNext={() => {
              if (scope === 'TODAY') setDate(prev => stepForwardWithinToday(shiftIsoDate(prev, 1), todayIso()));
              else if (scope === 'CALENDAR_WEEK') setDate(prev => stepForwardWithinToday(shiftIsoDate(prev, 7), todayIso()));
              else setDate(prev => stepForwardWithinToday(shiftIsoDate(prev, 30), todayIso()));
            }}
            canStepForward={date < todayIso()}
          />

          <TrendSummary
            overview={overview}
            previousOverview={previousOverview}
            scope={scope}
            deltaLabel={delta.label}
            deltaPercent={deltaPercent}
            goalDays={goalDays}
            chartWidth={chartWidth}
            onChartLayout={setChartWidth}
            columns={columns}
            peak={peak}
            hourBuckets={hourBuckets}
            peakHour={peakHour}
            hourCenters={hourCenters}
            axisLabelItems={axisLabelItems}
            preciseSessionCount={preciseSessions.length}
            fontScale={fontScale}
            distribution={distribution}
            spectrum={spectrum}
          />
        </>
      )}

      <Pressable onPress={() => void handleRefresh()} disabled={refreshing} accessibilityRole="button" accessibilityLabel="重新读取回顾数据"
        style={({ pressed }) => ({ minHeight: layout.rowMinHeight, justifyContent: 'center', marginTop: YanjiSpacing.lg, opacity: pressed ? 0.65 : 1 })}>
        <Text style={[typography.captionMeta, { color: colors.brandInk }]}>{refreshing ? '读取中…' : '重新读取'}</Text>
      </Pressable>
    </ScrollView>

    <SessionNoteModal visible={noteTarget !== null} sessionId={noteTarget?.sessionId ?? null} sessionTitle={noteTarget?.title ?? ''}
      initialNote={noteTarget?.note ?? ''} examSession={noteTarget?.isExam ?? false} onClose={() => setNoteTarget(null)} onSaved={() => setNoteTarget(null)} />
    <RecordMomentModal visible={editingNote !== null} date={date} editingNote={editingNote} onClose={() => setEditingNote(null)} onSaved={() => setEditingNote(null)} />
    <Modal visible={pendingDelete !== null} transparent animationType={reduceMotion ? 'none' : 'fade'} onRequestClose={() => { if (!deleting) setPendingDelete(null); }}>
      <View style={{ flex: 1, backgroundColor: paper.colors.scrim, justifyContent: 'flex-end' }}>
        <ScrollView style={{ flexGrow: 0, backgroundColor: colors.surface, borderTopLeftRadius: radius.sheet, borderTopRightRadius: radius.sheet }}
          contentContainerStyle={{ padding: layout.pageGutter, paddingBottom: insets.bottom + YanjiSpacing.lg }}>
          <YanjiPaperSheetHeading title={`删除「${pendingDelete?.subjectName ?? ''}」记录？`} subtitle="该记录与它的学习时长会一并删除，无法恢复。" onClose={() => { if (!deleting) setPendingDelete(null); }} />
          {deleteError ? <Text accessibilityRole="alert" style={[typography.bodyPrimary, { color: colors.brandInk, marginTop: YanjiSpacing.md }]}>{deleteError}</Text> : null}
          <YanjiPrimaryButton label={deleting ? '删除中…' : '确认删除'} onPress={() => void removeSession()} disabled={deleting} style={{ marginTop: YanjiSpacing.lg }} />
          <Pressable onPress={() => setPendingDelete(null)} disabled={deleting} accessibilityRole="button" accessibilityLabel="取消删除记录" style={{ minHeight: layout.rowMinHeight, alignItems: 'center', justifyContent: 'center' }}>
            <Text style={[typography.bodyPrimary, { color: colors.secondary }]}>取消</Text>
          </Pressable>
        </ScrollView>
      </View>
    </Modal>
  </View>;
}

// ---------------------------------------------------------------------------
// The date filmstrip — a fixed week frame, one cell per day
// ---------------------------------------------------------------------------

function WeekFilmstrip({ cells, onSelect }: {
  cells: readonly FilmstripDay[]; onSelect: (date: string) => void;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout, radius } = paper;
  return <View style={{
    flexDirection: 'row',
    gap: YanjiSpacing.xs,
    paddingTop: 8,
    paddingBottom: 12,
    borderBottomWidth: layout.hairlineWidth,
    borderColor: colors.hairline,
  }}>
    {cells.map(cell => <Pressable key={cell.date} disabled={cell.isFuture} onPress={() => onSelect(cell.date)}
      accessibilityRole="button"
      accessibilityLabel={`${cell.date} ${cell.weekdayLabel}${cell.dotColor ? '，有学习记录' : ''}`}
      accessibilityState={{ selected: cell.isSelected, disabled: cell.isFuture }}
      style={({ pressed }) => ({
        flex: 1, minHeight: layout.filmstripCellMinHeight, paddingVertical: YanjiSpacing.sm,
        alignItems: 'center', justifyContent: 'center', borderRadius: radius.surface,
        backgroundColor: cell.isSelected ? colors.ink : 'transparent',
        opacity: cell.isFuture ? 0.45 : pressed ? 0.65 : 1,
      })}>
      <Text style={[typography.captionMeta, { color: cell.isSelected ? colors.canvas : colors.secondary }]}>{cell.weekdayLabel}</Text>
      <Text style={[typography.bodyPrimary, {
        color: cell.isSelected ? colors.canvas : cell.isFuture ? colors.secondary : colors.ink,
        fontWeight: '700', marginTop: YanjiSpacing.xs,
      }]}>{cell.dayOfMonth}</Text>
      {/* The dot only exists where study time does — a zero day must not
          borrow the visual weight of a recorded one. */}
      {cell.dotColor ? <View style={{
        width: 5, height: 5, borderRadius: radius.pill, marginTop: YanjiSpacing.xs, backgroundColor: cell.dotColor,
      }} /> : <View style={{ height: 5, marginTop: YanjiSpacing.xs }} />}
    </Pressable>)}
  </View>;
}

/**
 * The timeline's empty / loading line.
 *
 * A day whose only content is a plan is not an empty day — the plan is real and
 * the summary strip already counts it — so the copy says what is missing (a
 * recorded session or note) rather than claiming nothing happened.
 */
function TimelineState({ loading, entryCount, planCount }: {
  loading: boolean; entryCount: number; planCount: number;
}): React.JSX.Element | null {
  const { paper } = useYanjiTheme();
  const { colors, typography } = paper;
  if (loading) {
    return <Text style={[typography.bodyJournal, { color: colors.secondary, marginTop: paper.layout.sectionGap }]}>
      正在读取这一天的记录…
    </Text>;
  }
  if (entryCount > 0) return null;
  const message = planCount > 0
    ? '这一天只留下了计划，还没有专注片段或随笔。'
    : '这一天还没有记录。专注片段和随笔会留在这里。';
  return <Text style={[typography.bodyJournal, { color: colors.secondary, marginTop: paper.layout.sectionGap }]}>{message}</Text>;
}

// ---------------------------------------------------------------------------
// The day's summary strip — three facts, no card
// ---------------------------------------------------------------------------

function DaySummary({ snapshot, goalSeconds }: {
  snapshot: DailySnapshot | null;
  goalSeconds: number | null;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout } = paper;
  const completed = snapshot?.tasks.filter(task => task.completed).length ?? 0;
  const cells: readonly { label: string; value: string; meta?: string }[] = [
    {
      label: '当日专注',
      value: snapshot ? formatDuration(snapshot.totalDurationSeconds) : '—',
      meta: goalSeconds && snapshot ? `目标 ${formatDuration(goalSeconds)}` : undefined,
    },
    { label: '完成任务', value: snapshot ? `${completed} / ${snapshot.tasks.length}` : '—', meta: '项' },
    {
      label: '最长单次',
      value: snapshot ? formatDuration(longestSessionSeconds(snapshot.sessions ?? [])) : '—',
      meta: snapshot && snapshot.examCount > 0 ? `含 ${snapshot.examCount} 场模考` : undefined,
    },
  ];
  return <View style={{
    flexDirection: 'row',
    paddingVertical: 12,
    borderBottomWidth: layout.hairlineWidth,
    borderColor: colors.hairline,
  }}>
    {cells.map((cell, index) => <View key={cell.label} style={{
      flex: 1, alignItems: 'center', paddingHorizontal: YanjiSpacing.xs,
      borderLeftWidth: index > 0 ? layout.hairlineWidth : 0, borderColor: colors.hairline,
    }}>
      <Text style={[typography.captionMeta, { color: colors.secondary }]}>{cell.label}</Text>
      <Text style={[typography.bodyPrimary, {
        color: colors.ink, fontWeight: '700', fontVariant: ['tabular-nums'],
        marginTop: YanjiSpacing.xs, textAlign: 'center',
      }]}>{cell.value}</Text>
      {cell.meta ? <Text style={[typography.captionMeta, { color: colors.secondary, marginTop: 2, textAlign: 'center' }]}>{cell.meta}</Text> : null}
    </View>)}
  </View>;
}

// ---------------------------------------------------------------------------
// The timeline — one spine, clock on the left, event on the right
// ---------------------------------------------------------------------------

function Timeline({ entries, dayTasks, busyNote, onEditSession, onDeleteSession, onToggleFavorite, onEditNote }: {
  entries: readonly TimelineEntry[];
  dayTasks: readonly { id: string; title: string }[];
  busyNote: boolean;
  onEditSession: (session: DailySession) => void;
  onDeleteSession: (session: DailySession) => void;
  onToggleFavorite: (noteId: string) => void;
  onEditNote: (note: Extract<TimelineEntry, { kind: 'note' }>) => void;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { typography, layout } = paper;
  return <View style={{ marginTop: YanjiSpacing.lg }}>
    {entries.map((entry, index) => <TimelineItem key={entry.key} entry={entry} isLast={index === entries.length - 1}
      dayTasks={dayTasks} busyNote={busyNote}
      onEditSession={onEditSession} onDeleteSession={onDeleteSession}
      onToggleFavorite={onToggleFavorite} onEditNote={onEditNote} />)}
    {entries.length > 0 ? <Text style={[typography.captionMeta, { color: paper.colors.secondary, marginTop: YanjiSpacing.sm }]}>
      按真实发生时刻排列；完成计划不标记未提供的完成时刻。
    </Text> : null}
  </View>;
}

function TimelineItem({ entry, isLast, dayTasks, busyNote, onEditSession, onDeleteSession, onToggleFavorite, onEditNote }: {
  entry: TimelineEntry;
  isLast: boolean;
  dayTasks: readonly { id: string; title: string }[];
  busyNote: boolean;
  onEditSession: (session: DailySession) => void;
  onDeleteSession: (session: DailySession) => void;
  onToggleFavorite: (noteId: string) => void;
  onEditNote: (note: Extract<TimelineEntry, { kind: 'note' }>) => void;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout } = paper;
  const nodeColor = entry.kind === 'session'
    ? entry.session.subjectColor || colors.secondary
    : colors.brand;

  // Shared baseline height for the first line across time column, axis node, and entry title.
  // This guarantees time, node, title, and +duration are perfectly collinear.
  const firstLineHeight = 28;

  return (
    <View style={{ flexDirection: 'row', marginHorizontal: -layout.timelineEdgeBleed }}>
      <View style={{ width: layout.timelineTimeColumn, height: firstLineHeight, justifyContent: 'center', paddingRight: 4 }}>
        <Text style={[typography.captionMeta, {
          color: colors.secondary, textAlign: 'right',
        }]}>
          {formatClockFromEpoch(entry.at)}
        </Text>
      </View>
      <View style={{ width: layout.timelineAxisColumn, alignItems: 'center' }}>
        <View style={{ height: firstLineHeight, justifyContent: 'center', alignItems: 'center' }}>
          <View style={{
            width: layout.timelineNodeSize, height: layout.timelineNodeSize,
            borderRadius: layout.timelineNodeSize / 2,
            backgroundColor: nodeColor,
          }} />
        </View>
        {!isLast ? <View style={{ flex: 1, width: layout.hairlineWidth, backgroundColor: colors.hairline, marginVertical: YanjiSpacing.xs }} /> : null}
      </View>
      <View style={{ flex: 1, paddingBottom: isLast ? 0 : YanjiSpacing.lg, minHeight: 56 }}>
        {entry.kind === 'session'
          ? <SessionEntry session={entry.session} boundTitle={boundTaskTitle(entry.session.taskId, dayTasks)}
            firstLineHeight={firstLineHeight}
            onEdit={() => onEditSession(entry.session)} onDelete={() => onDeleteSession(entry.session)} />
          : <NoteEntry entry={entry} busy={busyNote} firstLineHeight={firstLineHeight}
            onToggleFavorite={() => onToggleFavorite(entry.noteId)} onEdit={() => onEditNote(entry)} />}
      </View>
    </View>
  );
}

function SessionEntry({ session, boundTitle, firstLineHeight, onEdit, onDelete }: {
  session: DailySession; boundTitle: string | null; firstLineHeight: number; onEdit: () => void; onDelete: () => void;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout, radius } = paper;
  const titleText = boundTitle
    ? `${session.subjectName} · ${boundTitle}`
    : `${session.subjectName} · ${session.isExam ? '模考' : '专注'}`;

  return (
    <Pressable
      onPress={onEdit}
      accessibilityRole="button"
      accessibilityLabel={`${titleText}，${formatDuration(session.durationSeconds)}`}
      style={({ pressed }) => ({ opacity: pressed ? 0.65 : 1 })}
    >
      <View style={{ height: firstLineHeight, flexDirection: 'row', alignItems: 'center', gap: layout.gridGap }}>
        <Text
          numberOfLines={1}
          style={[typography.bodyPrimary, { color: colors.ink, fontWeight: '700', flex: 1 }]}
        >
          {titleText}
        </Text>
        <Text style={[typography.captionMeta, {
          color: session.subjectColor || colors.secondary, fontWeight: '700',
        }]}>
          +{formatDuration(session.durationSeconds)}
        </Text>
        <YanjiIconButton
          icon="delete"
          onPress={onDelete}
          size={24}
          iconSize={15}
          style={{ marginRight: -4 }}
          accessibilityLabel={`删除记录 ${session.subjectName}，${formatClockFromEpoch(session.startTime)} 起，${formatDuration(session.durationSeconds)}`}
        />
      </View>
      <Text style={[typography.captionMeta, { color: colors.secondary, marginTop: 2 }]}>
        {formatDuration(session.durationSeconds)}
        {session.pauseCount > 0 ? ` · 暂停 ${session.pauseCount} 次` : ''}
      </Text>
      {boundTitle ? (
        <View style={{
          alignSelf: 'flex-start', flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.xs,
          backgroundColor: colors.subtle, borderRadius: radius.tag, paddingHorizontal: YanjiSpacing.sm,
          paddingVertical: layout.taskTagPadding, marginTop: 4,
        }}>
          <YanjiIcon name="check" size={typography.captionMeta.fontSize} color={colors.secondary} />
          <Text style={[typography.captionMeta, { color: colors.secondary }]}>已达成关联任务：{boundTitle}</Text>
        </View>
      ) : null}
      {session.note ? <Text style={[typography.bodyJournal, { color: colors.ink, marginTop: YanjiSpacing.sm }]}>{session.note}</Text> : null}
    </Pressable>
  );
}

function NoteEntry({ entry, busy, firstLineHeight, onToggleFavorite, onEdit }: {
  entry: Extract<TimelineEntry, { kind: 'note' }>; busy: boolean; firstLineHeight: number;
  onToggleFavorite: () => void; onEdit: () => void;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout, radius } = paper;
  return (
    <Pressable
      onPress={onEdit}
      accessibilityRole="button"
      accessibilityLabel={`编辑随笔：${entry.content}`}
      style={({ pressed }) => ({
        backgroundColor: colors.elevated,
        borderWidth: layout.hairlineWidth,
        borderColor: colors.hairline,
        borderLeftWidth: 3,
        borderLeftColor: colors.brand,
        borderRadius: radius.tag,
        paddingVertical: YanjiSpacing.sm,
        paddingHorizontal: YanjiSpacing.md,
        opacity: pressed ? 0.7 : 1,
      })}
    >
      <View style={{ height: firstLineHeight, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: layout.gridGap }}>
        <Text style={[typography.captionMeta, { color: colors.brandInk, fontWeight: '700', flexShrink: 1 }]}>
          记录此刻{entry.isFavorite ? ' · 已收藏' : ''}
        </Text>
        <View style={{ flexDirection: 'row', alignItems: 'center' }}>
          <YanjiIconButton icon="compose" accessibilityLabel="编辑这条记录" onPress={onEdit} size={24} iconSize={15} />
          <YanjiIconButton icon={entry.isFavorite ? 'star' : 'starOutline'} accessibilityLabel={entry.isFavorite ? '取消收藏这条记录' : '收藏这条记录'}
            onPress={onToggleFavorite} disabled={busy} selected={entry.isFavorite} size={24} iconSize={15} />
        </View>
      </View>
      <Text style={[typography.bodyJournal, { color: colors.ink, marginTop: 4 }]}>{entry.content}</Text>
    </Pressable>
  );
}

// ---------------------------------------------------------------------------
// The period selector bar — pill scopes on the left, date range on the right
// ---------------------------------------------------------------------------

function PeriodSelectorBar({
  scope,
  anchorDate,
  onScopeChange,
  onPrevious,
  onNext,
  canStepForward,
}: {
  scope: ReviewScope;
  anchorDate: string;
  onScopeChange: (scope: ReviewScope) => void;
  onPrevious: () => void;
  onNext: () => void;
  canStepForward: boolean;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, radius } = paper;
  const rangeLabel = scopeDateRangeLabel(scope, anchorDate);

  return (
    <View style={{
      flexDirection: 'row',
      justifyContent: 'space-between',
      alignItems: 'center',
      marginTop: 4,
      marginBottom: 12,
    }}>
      <View style={{ flexDirection: 'row', gap: 6 }}>
        {SCOPES.map(opt => {
          const selected = opt.value === scope;
          return (
            <Pressable
              key={opt.value}
              onPress={() => onScopeChange(opt.value)}
              accessibilityRole="button"
              accessibilityState={{ selected }}
              accessibilityLabel={`切换范围至${opt.label}`}
              style={{
                paddingVertical: 4,
                paddingHorizontal: YanjiSpacing.md,
                borderRadius: radius.pill,
                backgroundColor: selected ? colors.ink : colors.subtle,
              }}
            >
              <Text style={{
                fontSize: 12,
                fontWeight: selected ? '600' : '400',
                color: selected ? colors.canvas : colors.secondary,
              }}>
                {opt.label}
              </Text>
            </Pressable>
          );
        })}
      </View>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: 2 }}>
        <Pressable
          onPress={onPrevious}
          accessibilityRole="button"
          accessibilityLabel="上一周期"
          hitSlop={8}
        >
          <Text style={[typography.captionMeta, { color: colors.secondary, fontSize: 13 }]}>‹</Text>
        </Pressable>
        <Text style={[typography.captionMeta, {
          color: colors.secondary,
          fontVariant: ['tabular-nums'],
          fontSize: 11,
          paddingHorizontal: 4,
        }]}>
          {rangeLabel}
        </Text>
        <Pressable
          onPress={onNext}
          disabled={!canStepForward}
          accessibilityRole="button"
          accessibilityLabel="下一周期"
          hitSlop={8}
          style={{ opacity: canStepForward ? 1 : 0.3 }}
        >
          <Text style={[typography.captionMeta, { color: colors.secondary, fontSize: 13 }]}>›</Text>
        </Pressable>
      </View>
    </View>
  );
}

// ---------------------------------------------------------------------------
// The trend view — one KPI sheet, then the distribution (AB-07)
// ---------------------------------------------------------------------------

function TrendSummary({
  overview, previousOverview, scope, deltaLabel, deltaPercent, goalDays,
  chartWidth, onChartLayout, columns, peak, hourBuckets, peakHour, hourCenters, axisLabelItems,
  preciseSessionCount, fontScale, distribution, spectrum,
}: {
  overview: ReviewOverview | null;
  previousOverview: ReviewOverview | null;
  scope: ReviewScope;
  deltaLabel: string;
  deltaPercent: number | null;
  goalDays: { reached: number; elapsed: number } | null;
  chartWidth: number;
  onChartLayout: (width: number) => void;
  columns: ReturnType<typeof buildTrendColumns>;
  peak: ReturnType<typeof peakTrendDay>;
  hourBuckets: ReturnType<typeof buildHourBuckets>;
  peakHour: ReturnType<typeof peakHourBucket>;
  hourCenters: readonly number[];
  axisLabelItems: ReturnType<typeof computeAxisLabelsLayout>;
  preciseSessionCount: number;
  fontScale: number;
  distribution: readonly ReviewSubjectSlice[];
  spectrum: readonly { id: string; label: string; seconds: number; color: string }[];
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout, radius } = paper;
  const days = overview?.days ?? [];
  const chartLabel = scope === 'TODAY'
    ? `当日可确认连续区间的小时分布，${preciseSessionCount} 段记录。${peakHour ? `${peakHour.hour}时最多，${formatDuration(peakHour.minutes * 60)}` : '暂无可绘制区间'}`
    : trendAccessibilityLabel(overview);

  const metricParts = formatDurationMetric(overview?.totalSeconds ?? 0);
  const scopeName = scope === 'TODAY' ? '当日' : scope === 'CALENDAR_WEEK' ? '本周' : '本月';

  // The badge reads a period against the one before it: up is the `positive`
  // role, down is `brandInk` — the same readable brand tier every other
  // headline figure uses, so "down" is not mistaken for the destructive
  // `danger` red reserved for deleting a record.
  const isNegative = deltaPercent !== null && deltaPercent < 0;
  const deltaBadgeBg = !previousOverview
    ? colors.subtle
    : isNegative ? colors.brandSubtle : colors.positiveSubtle;
  const deltaBadgeColor = !previousOverview
    ? colors.secondary
    : isNegative ? colors.brandInk : colors.positive;
  const deltaBadgeText = !previousOverview
    ? '正在读取…'
    : deltaPercent !== null
      ? `${deltaPercent >= 0 ? '↑ +' : '↓ '}${(Math.abs(deltaPercent) * 100).toFixed(1)}% ${deltaLabel.replace('较上一周期 ', '环比')}`
      : deltaLabel;

  /**
   * Whether the current scope has anything to plot. Every branch feeds a chart
   * from a different series, so this asks the series the branch actually uses —
   * an all-zero day still produces 24 hour buckets, and treating that as "data"
   * is what paints an empty chart well.
   */
  const hasPlottedData = scope === 'TODAY'
    ? hourBuckets.some(bucket => bucket.minutes > 0)
    : columns.some(column => column.totalMinutes > 0);

  // One sentence, read straight off the figures above it. No forecast, no
  // coaching, nothing the data cannot support.
  const insightLine = !overview || overview.totalSeconds <= 0
    ? null
    : peak
      ? `最长投入日为 ${peak.dayLabel}（${formatDuration(peak.totalMinutes * 60)}）${distribution[0] ? `，${distribution[0].subjectName}占比最高（${formatPercent(distribution[0].share)}）` : ''}。`
      : '已沉淀真实专注数据，继续保持专注节奏。';

  return (
    <>
      {/* 1. KPI Summary & Chart Card (AB-07 Card) */}
      <YanjiCard style={{ paddingHorizontal: YanjiSpacing.lg, paddingVertical: 14 }}>
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <View>
            <Text style={[typography.captionMeta, { color: colors.secondary }]}>{scopeName}累计专注时长</Text>
            <View style={{ flexDirection: 'row', alignItems: 'baseline', marginTop: 2 }}>
              {overview ? (
                metricParts.hours > 0 ? (
                  <>
                    <Text style={{ fontSize: 32, fontWeight: '700', color: colors.ink, fontVariant: ['tabular-nums'] }}>
                      {metricParts.hours}
                    </Text>
                    <Text style={{ fontSize: 14, fontWeight: '500', color: colors.secondary, marginRight: 6 }}>h</Text>
                    <Text style={{ fontSize: 32, fontWeight: '700', color: colors.ink, fontVariant: ['tabular-nums'] }}>
                      {metricParts.minutes}
                    </Text>
                    <Text style={{ fontSize: 14, fontWeight: '500', color: colors.secondary }}>m</Text>
                  </>
                ) : (
                  <>
                    <Text style={{ fontSize: 32, fontWeight: '700', color: colors.ink, fontVariant: ['tabular-nums'] }}>
                      {metricParts.minutes}
                    </Text>
                    <Text style={{ fontSize: 14, fontWeight: '500', color: colors.secondary }}>m</Text>
                  </>
                )
              ) : (
                <Text style={{ fontSize: 32, fontWeight: '700', color: colors.secondary }}>—</Text>
              )}
            </View>
          </View>

          <View style={{ alignItems: 'flex-end' }}>
            <View style={{
              paddingHorizontal: YanjiSpacing.sm,
              paddingVertical: 3,
              borderRadius: radius.tag,
              backgroundColor: deltaBadgeBg,
            }}>
              <Text style={{
                fontSize: 11,
                fontWeight: '700',
                color: deltaBadgeColor,
                fontVariant: ['tabular-nums'],
              }}>
                {deltaBadgeText}
              </Text>
            </View>
            <Text style={{
              fontSize: 11,
              color: colors.secondary,
              marginTop: 6,
              fontVariant: ['tabular-nums'],
            }}>
              {scope !== 'TODAY' && overview
                ? `${formatDailyAverageDuration(overview.dailyAverageSeconds)}${goalDays ? ` · 达标 ${goalDays.reached}/${goalDays.elapsed} 天` : ''}`
                : `日均 ${overview ? formatDailyAverageDuration(overview.dailyAverageSeconds) : '—'}`}
            </Text>
          </View>
        </View>

        {/* 7-Day Stacked Bar Chart / Line Chart */}
        <View
          onLayout={event => onChartLayout(event.nativeEvent.layout.width)}
          accessible
          accessibilityRole="image"
          accessibilityLabel={chartLabel}
          style={{
            borderTopWidth: 1,
            borderStyle: 'dashed',
            borderColor: colors.hairline,
            marginTop: 10,
            paddingTop: 18,
          }}
        >
          {chartWidth > 0 && overview ? (
            scope === 'TODAY' ? (
              <YanjiLineChart
                values={hourBuckets.map(bucket => bucket.minutes)}
                width={chartWidth}
                height={TREND_CHART_HEIGHT}
                peakIndex={peakHour?.hour}
                emptyLabel={hasPlottedData ? undefined : '这一天还没有可绘制的专注区间。'}
              />
            ) : scope === 'CALENDAR_MONTH' ? (
              <YanjiLineChart
                values={columns.map(column => column.totalMinutes)}
                width={chartWidth}
                height={TREND_CHART_HEIGHT}
                averageValue={overview.dailyAverageSeconds / 60}
                peakIndex={peak ? columns.indexOf(peak) : undefined}
                emptyLabel={hasPlottedData ? undefined : '这个月还没有可绘制的专注记录。'}
              />
            ) : (
              <YanjiStackedBarChart
                columns={columns}
                width={chartWidth}
                height={TREND_CHART_HEIGHT}
                averageMinutes={overview.dailyAverageSeconds / 60}
                emptyLabel={hasPlottedData ? undefined : '这一周还没有可绘制的专注记录。'}
              />
            )
          ) : null}
          {/*
            The axis only earns its space when something is plotted against it —
            otherwise an all-zero window leaves a blank chart well with hour
            ticks floating under it, which reads as a broken chart rather than
            an empty one.
          */}
          <View style={{
            height: typography.captionMeta.lineHeight * Math.max(1, fontScale),
            marginTop: YanjiSpacing.sm,
            display: hasPlottedData ? 'flex' : 'none',
          }}>
            {scope === 'TODAY'
              ? HOUR_TICKS.map(hour => (
                  <Text
                    key={hour}
                    style={[
                      typography.captionMeta,
                      {
                        position: 'absolute',
                        left: Math.max(0, Math.min(chartWidth - 38 * fontScale, (hourCenters[hour] ?? 0) - 19 * fontScale)),
                        width: 38 * fontScale,
                        textAlign: 'center',
                        color: colors.secondary,
                      },
                    ]}
                  >
                    {hour}时
                  </Text>
                ))
              : axisLabelItems.map(item => (
                  <Text
                    key={item.date}
                    style={[
                      typography.captionMeta,
                      {
                        position: 'absolute',
                        left: item.left,
                        width: item.width,
                        textAlign: 'center',
                        color: item.isToday ? colors.brandInk : colors.secondary,
                        fontWeight: item.isToday ? '700' : '400',
                      },
                    ]}
                  >
                    {item.dayLabel}
                  </Text>
                ))}
          </View>
        </View>
      </YanjiCard>

      {/* 2. Subject Distribution Breakdown (AB-07) */}
      <YanjiPaperSectionHeading
        title="学科投入比重"
        countText={overview ? `共 ${countWorkedDays(days)} 天有投入` : '—'}
      />

      <YanjiChronoSpectrum
        segments={spectrum}
        totalSeconds={overview?.totalSeconds ?? 0}
        goalSeconds={null}
        accessibilityLabel={
          overview
            ? `${overview.label}学科投入，${distribution.map(s => `${s.subjectName} ${formatDuration(s.minutes * 60)}`).join('，') || '暂无记录'}`
            : '正在读取学科分布'
        }
      />

      <View style={{ marginTop: YanjiSpacing.md }}>
        {distribution.map(slice => (
          <YanjiSubjectProportionRow
            key={slice.subjectId}
            label={slice.subjectName}
            color={slice.subjectColor || colors.secondary}
            metaText={`${formatDuration(slice.minutes * 60)} · ${formatPercent(slice.share)}`}
            ratio={slice.share}
          />
        ))}
      </View>
      {!distribution.length ? (
        <Text style={[typography.bodyJournal, { color: colors.secondary, marginTop: YanjiSpacing.md }]}>
          {overview ? '空白轨道，等待下一段真实的投入。' : '正在读取学科统计…'}
        </Text>
      ) : null}

      {/*
        AB-07's closing block is an AI 节律简报 entry card. There is no such
        report in the product — no AI screen, no bridge method, nothing to open —
        so the card would ship a claim ("自动生成节律洞察") and a "详情 ›" that
        goes nowhere. What the board's card actually says is one true sentence
        drawn from the numbers already on this screen, so it is rendered as that
        sentence and nothing more.
      */}
      {insightLine ? (
        <Text style={[typography.bodyJournal, { color: colors.secondary, marginTop: YanjiSpacing.lg }]}>
          {insightLine}
        </Text>
      ) : null}
    </>
  );
}

function trendAccessibilityLabel(overview: ReviewOverview | null): string {
  if (!overview) return '学习趋势正在读取';
  const worked = overview.days.filter(day => !day.isFuture && day.durationSeconds > 0);
  if (!worked.length) return `${overview.label}内没有专注记录`;
  const best = worked.reduce((a, b) => b.durationSeconds > a.durationSeconds ? b : a);
  return `${overview.label}已过 ${countElapsedDays(overview.days)} 天，${worked.length} 天有学习时长，累计 ${formatDuration(overview.totalSeconds)}。最长一天 ${best.date}，${formatDuration(best.durationSeconds)}`;
}
