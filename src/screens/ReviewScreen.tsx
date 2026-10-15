/** Paper journal: event times are native facts; completed tasks have no time. */
import React, { useCallback, useMemo, useState } from 'react';
import { Modal, Pressable, ScrollView, Text, useWindowDimensions, View } from 'react-native';
import { useReducedMotion } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { YanjiDataNative } from '../bridge';
import type { DailySession, ReviewOverview, ReviewScope, StudyTask } from '../bridge';
import {
  YanjiBadge, YanjiCard, YanjiChronoSpectrum, YanjiEditorialHeader, YanjiHairline,
  YanjiIcon, YanjiIconButton, YanjiLineChart, YanjiPaperSectionHeading,
  YanjiPaperSegmentedControl, YanjiPaperSheetHeading, YanjiPrimaryButton,
  YanjiStackedBarChart, YanjiStatCell, YanjiSubjectProportionRow,
} from '../components/YanjiUI';
import { RecordMomentModal } from '../components/RecordMomentModal';
import { SessionNoteModal } from '../components/SessionNoteModal';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { useBottomTabLayout } from '../navigation/useBottomTabLayout';
import { YanjiSpacing } from '../theme/tokens';
import {
  dataRefreshCoordinator, refreshDailySnapshot, useDailySnapshot,
  useReviewDataVisibility, useReviewOverview,
} from '../state/dataRefresh';
import { lineChartXCenters } from '../components/lineChartGeometry';
import {
  buildHourBuckets, buildTimelineEntries, buildTrendColumns, computeAxisLabelsLayout,
  countElapsedDays, countWorkedDays, dayHasRecords, formatClockFromEpoch,
  formatDailyAverageDuration, formatDuration, formatPercent, peakHourBucket,
  peakTrendDay, periodDelta, previousWindowArgs, relativeDayLabel,
  stepForwardWithinToday, todayIso, shiftIsoDate, type TimelineEntry,
} from './reviewViewLogic';

type ReviewView = 'timeline' | 'trend';
const VIEWS = [{ value: 'timeline', label: '时间线' }, { value: 'trend', label: '趋势统计' }] as const;
const SCOPES: readonly { value: ReviewScope; label: string }[] = [
  { value: 'TODAY', label: '当日' }, { value: 'CALENDAR_WEEK', label: '本周' },
  { value: 'CALENDAR_MONTH', label: '本月' },
];
const HOUR_TICKS = [0, 6, 12, 18];
const TREND_CHART_HEIGHT = 96;

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
  const overviewRequest = useMemo(() => ({ scope, periodsBack: 0, anchorDate: date }), [date, scope]);
  const previousRequest = useMemo(() => previousWindowArgs(scope, date), [date, scope]);
  const reviewRequests = useMemo(() => [overviewRequest, previousRequest], [overviewRequest, previousRequest]);
  const trendVisible = visible && view === 'trend';
  const overview = useReviewOverview(overviewRequest, trendVisible);
  const previousOverview = useReviewOverview(previousRequest, trendVisible);
  useReviewDataVisibility('review', reviewRequests, trendVisible);
  const dayTasks = snapshot?.tasks ?? [];
  const sessions = snapshot?.sessions ?? [];
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
  const hasRecords = dayHasRecords(entries, dayTasks);
  const completedTasks = dayTasks.filter(task => task.completed);
  const pendingTasks = dayTasks.filter(task => !task.completed);
  const today = todayIso();
  const isToday = date === today;
  const relativeLabel = relativeDayLabel(date, today);
  const selectedDate = new Date(`${date}T12:00:00`);
  const weekDates = useMemo(() => {
    const day = new Date(`${date}T12:00:00`).getDay();
    const monday = shiftIsoDate(date, -((day + 6) % 7));
    return Array.from({ length: 7 }, (_, index) => shiftIsoDate(monday, index));
  }, [date]);

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
        ...(trendVisible ? reviewRequests.map(request => dataRefreshCoordinator.refreshReviewOverview(request)) : []),
      ]);
    } catch { setActionError('读取失败，请重新读取。'); }
    finally { setRefreshing(false); }
  }, [date, trendVisible, reviewRequests]);

  const days = overview?.days ?? [];
  const columns = useMemo(() => buildTrendColumns(days, overview?.subjectDistribution ?? []), [days, overview]);
  const peak = peakTrendDay(columns);
  const delta = periodDelta(overview?.totalSeconds ?? 0, previousOverview?.totalSeconds ?? null);
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
    <ScrollView testID="scroll-review" style={{ flex: 1 }} contentContainerStyle={{
      paddingHorizontal: layout.pageGutter, paddingTop: YanjiSpacing.lg, paddingBottom: contentPadding,
    }}>
      <YanjiEditorialHeader eyebrow="研迹 · 成长手账" title="回顾" subtitle="翻一页，看看时间留下的痕迹。" />

      <View style={{ flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap', gap: layout.gridGap, marginTop: YanjiSpacing.lg }}>
        <YanjiIconButton icon="back" accessibilityLabel="前一天" onPress={() => setDate(prev => shiftIsoDate(prev, -1))} size={layout.rowMinHeight} tone="filled" />
        <View style={{ flex: 1, minWidth: 130, alignItems: 'center' }}>
          <Text testID="review-date" style={[typography.titleSection, { color: colors.ink }]}>{selectedDate.getFullYear()}年{selectedDate.getMonth() + 1}月{selectedDate.getDate()}日</Text>
          <Text style={[typography.captionMeta, { color: colors.secondary }]}>{relativeLabel ? `${relativeLabel} · ` : ''}星期{'日一二三四五六'[selectedDate.getDay()]}</Text>
        </View>
        <YanjiIconButton icon="forward" accessibilityLabel="后一天" disabled={isToday} onPress={() => setDate(prev => stepForwardWithinToday(prev, todayIso()))} size={layout.rowMinHeight} tone="filled" />
      </View>

      {!isToday ? <Pressable onPress={() => setDate(todayIso())} accessibilityRole="button" accessibilityLabel="回到今天"
        style={({ pressed }) => ({ minHeight: layout.rowMinHeight, alignSelf: 'center', justifyContent: 'center', opacity: pressed ? 0.65 : 1 })}>
        <Text style={[typography.captionMeta, { color: colors.brandInk, fontWeight: '700' }]}>回到今天 ›</Text>
      </Pressable> : null}

      <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ marginVertical: YanjiSpacing.md }} contentContainerStyle={{ gap: layout.gridGap }}>
        {weekDates.map((weekDate, index) => {
          const selected = weekDate === date;
          const future = weekDate > today;
          const isCurrentToday = weekDate === today;
          return <Pressable key={weekDate} disabled={future} onPress={() => setDate(weekDate)} accessibilityRole="button"
            accessibilityLabel={`${weekDate} 星期${'一二三四五六日'[index]}`}
            accessibilityState={{ selected, disabled: future }}
            style={({ pressed }) => ({ minWidth: layout.rowMinHeight, minHeight: layout.rowMinHeight + 8, paddingVertical: YanjiSpacing.xs, paddingHorizontal: YanjiSpacing.sm,
              alignItems: 'center', justifyContent: 'center', borderRadius: radius.surface,
              borderWidth: selected ? layout.hairlineWidth : 0, borderColor: selected ? colors.brand : 'transparent',
              backgroundColor: selected ? colors.brandSubtle : colors.elevated, opacity: future ? 0.45 : pressed ? 0.65 : 1 })}>
            <Text style={[typography.captionMeta, { color: selected ? colors.brandInk : colors.secondary }]}>周{'一二三四五六日'[index]}</Text>
            <Text style={[typography.titleSection, { color: future ? colors.secondary : selected ? colors.brandInk : colors.ink, marginTop: 2 }]}>{Number(weekDate.slice(8))}</Text>
            {isCurrentToday ? <View style={{ width: 4, height: 4, borderRadius: radius.pill, backgroundColor: colors.brand, marginTop: 2 }} /> : null}
          </Pressable>;
        })}
      </ScrollView>

      <YanjiPaperSegmentedControl value={view} options={VIEWS} onChange={setView} accessibilityLabel="回顾视图" />
      {actionError ? <Text accessibilityRole="alert" style={[typography.bodyPrimary, { color: colors.brandInk, marginTop: YanjiSpacing.md }]}>{actionError}</Text> : null}

      {view === 'timeline' ? <>
        <YanjiPaperSectionHeading title="这一天" />
        <YanjiCard>
          <Text style={[typography.captionMeta, { color: colors.secondary }]}>当日累计专注</Text>
          <Text style={[typography.metricHero, { color: colors.ink, marginTop: YanjiSpacing.xs }]}>{snapshot ? formatDuration(snapshot.totalDurationSeconds) : '—'}</Text>
          <YanjiHairline style={{ marginVertical: YanjiSpacing.md }} />
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: YanjiSpacing.lg }}>
            <YanjiStatCell label="专注 / 模考" value={snapshot ? `${snapshot.focusCount} 段 / ${snapshot.examCount} 场` : '—'} />
            <YanjiStatCell label="完成计划" value={snapshot ? `${completedTasks.length} / ${dayTasks.length}` : '—'} />
            <YanjiStatCell label="随笔" value={snapshot ? `${snapshot.notes.length} 篇` : '—'} />
          </View>
        </YanjiCard>

        {!snapshot ? <Text style={[typography.bodyJournal, { color: colors.secondary, marginTop: layout.sectionGap }]}>正在读取这一天的记录…</Text>
          : !hasRecords ? <Text style={[typography.bodyJournal, { color: colors.secondary, marginTop: layout.sectionGap }]}>这一天还没有记录。专注片段和随笔会留在这里。</Text> : null}

        {entries.map(entry => <View key={entry.key}>
          {entry.kind === 'session' ? <SessionRow session={entry.session}
            onEdit={() => setNoteTarget({ sessionId: entry.session.id, isExam: entry.session.isExam, title: entry.session.title, note: entry.session.note })}
            onDelete={() => { setDeleteError(null); setPendingDelete({ sessionId: entry.session.id, isExam: entry.session.isExam, subjectName: entry.session.subjectName }); }} />
            : <NoteRow note={entry} busy={busyNote !== null}
              onToggleFavorite={() => void toggleFavorite(entry.noteId)} onEdit={() => setEditingNote({ id: entry.noteId, content: entry.content })} />}
          <YanjiHairline />
        </View>)}

        {completedTasks.length ? <>
          <YanjiPaperSectionHeading title="已完成的计划" />
          <Text style={[typography.captionMeta, { color: colors.secondary, marginBottom: YanjiSpacing.xs }]}>按计划归属日收录，不标记未提供的完成时刻。</Text>
          {completedTasks.map(task => <TaskRow key={task.id} task={task} />)}
        </> : null}
        {pendingTasks.length ? <>
          <YanjiPaperSectionHeading title="这一天的待办" />
          {pendingTasks.map(task => <TaskRow key={task.id} task={task} />)}
        </> : null}
      </> : <>
        <YanjiPaperSectionHeading title="学习趋势" />
        <YanjiPaperSegmentedControl value={scope} options={SCOPES} onChange={setScope} accessibilityLabel="趋势范围" />
        <YanjiCard style={{ marginTop: YanjiSpacing.md }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: layout.gridGap }}>
            <Text style={[typography.captionMeta, { color: colors.secondary }]}>{overview?.label ?? '正在读取统计…'}</Text>
            <YanjiBadge tone="neutral" label={!previousOverview ? '正在读取上一周期…' : delta.label} />
          </View>
          <Text style={[typography.metricHero, { color: colors.ink, marginVertical: YanjiSpacing.xs }]}>{overview ? formatDuration(overview.totalSeconds) : '—'}</Text>
          {scope !== 'TODAY' ? <Text style={[typography.bodyPrimary, { color: colors.secondary }]}>{overview ? formatDailyAverageDuration(overview.dailyAverageSeconds) : '—'}</Text> : null}

          <View onLayout={event => setChartWidth(event.nativeEvent.layout.width)} accessible accessibilityRole="image"
            accessibilityLabel={scope === 'TODAY' ? `当日可确认连续区间的小时分布，${preciseSessions.length} 段记录。${peakHour ? `${peakHour.hour}时最多，${formatDuration(peakHour.minutes * 60)}` : '暂无可绘制区间'}` : trendAccessibilityLabel(overview)}
            style={{ marginTop: YanjiSpacing.lg }}>
            {chartWidth > 0 && overview ? scope === 'TODAY' ? <YanjiLineChart values={hourBuckets.map(bucket => bucket.minutes)} width={chartWidth} height={TREND_CHART_HEIGHT}
              peakIndex={peakHour?.hour} /> : scope === 'CALENDAR_MONTH' ? <YanjiLineChart values={columns.map(column => column.totalMinutes)} width={chartWidth} height={TREND_CHART_HEIGHT}
                averageValue={overview.dailyAverageSeconds / 60} peakIndex={peak ? columns.indexOf(peak) : undefined} />
                : <YanjiStackedBarChart columns={columns} width={chartWidth} height={TREND_CHART_HEIGHT} averageMinutes={overview.dailyAverageSeconds / 60} /> : null}
            <View style={{ height: typography.captionMeta.lineHeight * Math.max(1, fontScale), marginTop: YanjiSpacing.sm }}>
              {scope === 'TODAY' ? HOUR_TICKS.map(hour => <Text key={hour} style={[typography.captionMeta, {
                position: 'absolute', left: Math.max(0, Math.min(chartWidth - 38 * fontScale, (hourCenters[hour] ?? 0) - 19 * fontScale)),
                width: 38 * fontScale, textAlign: 'center', color: colors.secondary,
              }]}>{hour}时</Text>) : axisLabelItems.map(item => <Text key={item.date} style={[typography.captionMeta, {
                position: 'absolute', left: item.left, width: item.width, textAlign: 'center', color: colors.secondary,
              }]}>{item.dayLabel}</Text>)}
            </View>
          </View>

          <YanjiHairline style={{ marginVertical: YanjiSpacing.md }} />
          {scope === 'TODAY' ? <Text style={[typography.captionMeta, { color: colors.secondary }]}>小时分布仅绘可确认的连续区间；暂停或起止不一致的记录不绘入曲线，仍计入总量。{peakHour ? `连续区间最多 ${peakHour.hour}时 · ${formatDuration(peakHour.minutes * 60)}` : ''}</Text>
            : <>
              <Text style={[typography.captionMeta, { color: colors.secondary }]}>{peak ? `最长的一天 ${peak.dayLabel} · ${formatDuration(peak.totalMinutes * 60)}` : overview ? '这段时间还没有已保存的专注。' : '统计尚未读取。'} · 虚线为日均；图表按原生学科分钟统计展示。</Text>
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: YanjiSpacing.lg, marginTop: YanjiSpacing.md }}>
                <YanjiStatCell label="有学习时长" value={overview ? `${countWorkedDays(days)} / ${countElapsedDays(days)} 天` : '—'} />
                <YanjiStatCell label="模考" value={overview ? `${overview.examCount} 场` : '—'} />
              </View>
            </>}
        </YanjiCard>

        <YanjiPaperSectionHeading title="学科投入" right={<Text style={[typography.captionMeta, { color: colors.secondary }]}>{overview?.label}</Text>} />
        <YanjiChronoSpectrum segments={spectrum} totalSeconds={overview?.totalSeconds ?? 0} goalSeconds={null}
          accessibilityLabel={overview ? `${overview.label}学科投入，${distribution.map(s => `${s.subjectName} ${formatDuration(s.minutes * 60)}`).join('，') || '暂无记录'}` : '正在读取学科分布'} />
        <View style={{ marginTop: YanjiSpacing.sm }}>
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
        {!distribution.length ? <Text style={[typography.bodyJournal, { color: colors.secondary, marginTop: YanjiSpacing.md }]}>{overview ? '空白轨道，等待下一段真实的投入。' : '正在读取学科统计…'}</Text> : null}
      </>}

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

function trendAccessibilityLabel(overview: ReviewOverview | null): string {
  if (!overview) return '学习趋势正在读取';
  const worked = overview.days.filter(day => !day.isFuture && day.durationSeconds > 0);
  if (!worked.length) return `${overview.label}内没有专注记录`;
  const best = worked.reduce((a, b) => b.durationSeconds > a.durationSeconds ? b : a);
  return `${overview.label}已过 ${countElapsedDays(overview.days)} 天，${worked.length} 天有学习时长，累计 ${formatDuration(overview.totalSeconds)}。最长一天 ${best.date}，${formatDuration(best.durationSeconds)}`;
}

function SessionRow({ session, onEdit, onDelete }: { session: DailySession; onEdit: () => void; onDelete: () => void }): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout } = paper;
  const { fontScale } = useWindowDimensions();
  return <View style={{ flexDirection: 'row', alignItems: 'flex-start', paddingVertical: YanjiSpacing.md, gap: layout.gridGap }}>
    <Text style={[typography.captionMeta, { width: layout.timelineTimeColumn * Math.max(1, fontScale), color: colors.secondary, paddingTop: YanjiSpacing.xs }]}>{formatClockFromEpoch(session.startTime)}</Text>
    <View style={{ flex: 1, borderLeftWidth: layout.hairlineWidth, borderColor: colors.hairline, paddingLeft: YanjiSpacing.md }}>
      <View style={{ flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between', gap: layout.gridGap }}>
        <Pressable onPress={onEdit} accessibilityRole="button"
          accessibilityLabel={`${session.subjectName}${session.isExam ? ' 模考' : ' 专注'}，${formatDuration(session.durationSeconds)}，${session.isExam ? '模考不支持编辑随笔' : '编辑随笔'}`}
          style={({ pressed }) => ({ flex: 1, minHeight: layout.rowMinHeight, justifyContent: 'center', opacity: pressed ? 0.65 : 1 })}>
          <View style={{ flexDirection: 'row', alignItems: 'center', gap: layout.gridGap }}>
            <View style={{ width: 8, height: 8, borderRadius: paper.radius.pill, backgroundColor: session.subjectColor || colors.secondary }} />
            <Text style={[typography.bodyPrimary, { color: colors.ink, flex: 1, fontWeight: '700' }]}>{session.subjectName}{session.isExam ? ' · 模考' : ' · 专注'}</Text>
          </View>
          <Text style={[typography.captionMeta, { color: colors.secondary, marginTop: YanjiSpacing.xs }]}>{formatDuration(session.durationSeconds)}{session.pauseCount > 0 ? ` · 暂停 ${session.pauseCount} 次` : ''} · {session.isExam ? '模考随笔暂不支持编辑' : session.note ? '编辑随笔 ›' : '添一段专注随笔 ›'}</Text>
          {session.note ? <Text style={[typography.bodyJournal, { color: colors.ink, marginTop: YanjiSpacing.sm }]}>{session.note}</Text> : null}
        </Pressable>
        <YanjiIconButton icon="delete" onPress={onDelete} size={layout.rowMinHeight}
          accessibilityLabel={`删除记录 ${session.subjectName}，${formatClockFromEpoch(session.startTime)} 起，${formatDuration(session.durationSeconds)}`} />
      </View>
    </View>
  </View>;
}

function TaskRow({ task }: { task: StudyTask }): React.JSX.Element {
  const { paper } = useYanjiTheme();
  return <View style={{ flexDirection: 'row', alignItems: 'flex-start', gap: paper.layout.gridGap, paddingVertical: YanjiSpacing.md, borderBottomWidth: paper.layout.hairlineWidth, borderColor: paper.colors.hairline }}>
    <View style={{ width: paper.layout.timelineTimeColumn, paddingTop: YanjiSpacing.xs }}><YanjiIcon name={task.completed ? 'check' : 'calendar'} size={18} color={task.completed ? paper.colors.brandInk : paper.colors.secondary} /></View>
    <View style={{ flex: 1 }}>
      <Text style={[paper.typography.bodyPrimary, { color: paper.colors.ink }]}>{task.title}</Text>
      <Text style={[paper.typography.captionMeta, { color: paper.colors.secondary, marginTop: YanjiSpacing.xs }]}>{task.subjectName} · {task.completed ? '已完成 · 无完成时刻' : '待办'} · 预计 {task.plannedMinutes} 分钟 · 累计投入 {task.actualMinutes} 分钟</Text>
    </View>
  </View>;
}

function NoteRow({ note, busy, onToggleFavorite, onEdit }: {
  note: Extract<TimelineEntry, { kind: 'note' }>; busy: boolean; onToggleFavorite: () => void; onEdit: () => void;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { fontScale } = useWindowDimensions();
  return <View style={{ flexDirection: 'row', alignItems: 'flex-start', gap: paper.layout.gridGap, paddingVertical: YanjiSpacing.md }}>
    <Text style={[paper.typography.captionMeta, { width: paper.layout.timelineTimeColumn * Math.max(1, fontScale), color: paper.colors.secondary, paddingTop: YanjiSpacing.xs }]}>{formatClockFromEpoch(note.timestamp)}</Text>
    <View style={{ flex: 1, borderLeftWidth: 2, borderLeftColor: paper.colors.brand, paddingLeft: YanjiSpacing.md }}>
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
        <Text style={[paper.typography.captionMeta, { color: paper.colors.brandInk }]}>记录此刻{note.isFavorite ? ' · 已收藏' : ''}</Text>
        <View style={{ flexDirection: 'row', alignItems: 'center' }}>
          <YanjiIconButton icon="compose" accessibilityLabel="编辑这条记录" onPress={onEdit} size={paper.layout.rowMinHeight} />
          <YanjiIconButton icon={note.isFavorite ? 'star' : 'starOutline'} accessibilityLabel={note.isFavorite ? '取消收藏这条记录' : '收藏这条记录'}
            onPress={onToggleFavorite} disabled={busy} selected={note.isFavorite} size={paper.layout.rowMinHeight} />
        </View>
      </View>
      <Text style={[paper.typography.bodyJournal, { color: paper.colors.ink }]}>{note.content}</Text>
    </View>
  </View>;
}
