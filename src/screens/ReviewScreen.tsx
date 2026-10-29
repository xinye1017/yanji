/** Paper journal: event times are native facts; completed tasks have no time. */
import React, { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import {
  Modal, PanResponder, Pressable, ScrollView, Text, useWindowDimensions, View,
} from 'react-native';
import Animated, {
  runOnUI, useAnimatedStyle, useReducedMotion, useSharedValue, withTiming,
} from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { FlashList } from '@shopify/flash-list';
import { YanjiDataNative } from '../bridge';
import type { DailySession, DailySnapshot, ReviewOverview, ReviewScope, ReviewSubjectSlice } from '../bridge';
import {
  YanjiCaptureCard, YanjiCard, YanjiChronoSpectrum, YanjiEditorialHeader,
  YanjiIconButton, YanjiLineChart, YanjiPaperSectionHeading,
  YanjiPaperSegmentedControl, YanjiPaperSheetHeading, YanjiPrimaryButton,
  YanjiStackedBarChart, YanjiStaleDataNotice, YanjiSubjectProportionRow,
} from '../components/YanjiUI';
import { EditingNote, RecordMomentModal } from '../components/RecordMomentModal';
import { RoamingNotesModal } from '../components/RoamingNotesModal';
import { TimelineCompactRow } from '../components/TimelineCompactRow';
import { JournalLibraryView } from './review/JournalLibraryView';
import { TimelineActionPopover } from '../components/TimelineActionPopover';
import type { PopoverAnchor, TimelineAction } from '../components/TimelineActionPopover';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { useNavigationActions } from '../navigation/NavigationShell';
import { useBottomTabLayout } from '../navigation/useBottomTabLayout';
import { YanjiSpacing } from '../theme/tokens';
import {
  prefetchDailySnapshots, prefetchReviewOverviews,
  refreshDailySnapshot, refreshReviewOverview,
  useDailyReadFailed, useDailySnapshot,
  useReviewDataVisibility, useReviewOverview, useReviewReadFailed, useSettingsSnapshot,
} from '../state/dataRefresh';
import { reviewWindowKey } from '../state/dataRefreshCoordinator';
import { lineChartXCenters } from '../components/lineChartGeometry';
import {
  buildFilmstrip, buildHourBuckets, buildTimelineEntries, buildTrendColumns,
  canPageWeek,
  computeAxisLabelsLayout, countElapsedDays, countWorkedDays, dominantSubjectColors,
  formatClockFromEpoch, formatDailyAverageDuration, formatDuration, formatDurationMetric,
  durationMetricMinutes,
  formatPercent, goalReachedDays, peakHourBucket, peakTrendDay,
  periodDelta, periodDeltaPercent, previousWindowArgs, scopeDateRangeLabel,
  shiftIsoDate, shiftMonthAnchor, stepForwardWithinToday, stepMonthForwardWithinToday,
  weekStartOf,
  type FilmstripDay, type TimelineEntry,
} from './reviewViewLogic';
import { todayIso } from '../state/settingsViewLogic';

type ReviewView = 'timeline' | 'trend' | 'journal';
const VIEWS = [{ value: 'timeline', label: '时间线' }, { value: 'trend', label: '趋势统计' }, { value: 'journal', label: '随笔库' }] as const;
const SCOPES: readonly { value: ReviewScope; label: string }[] = [
  { value: 'TODAY', label: '当日' }, { value: 'CALENDAR_WEEK', label: '本周' },
  { value: 'CALENDAR_MONTH', label: '本月' },
];
const HOUR_TICKS = [0, 6, 12, 18];
const TREND_CHART_HEIGHT = 108;

export function ReviewScreen({ visible }: { visible: boolean }): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout, radius } = paper;
  const { openSettings } = useNavigationActions();
  const { contentPadding } = useBottomTabLayout();
  const { fontScale, width: windowWidth, height: windowHeight } = useWindowDimensions();
  const insets = useSafeAreaInsets();
  const reduceMotion = useReducedMotion();
  const [date, setDate] = useState(todayIso);
  /**
   * The week the filmstrip is showing, as its Monday.
   *
   * Deliberately independent of `date`: paging the strip is a look-ahead, not a
   * selection. The timeline keeps the selected day until the user taps a cell,
   * so a swipe can never blank the page or change which records are listed.
   */
  const [weekStart, setWeekStart] = useState(() => weekStartOf(todayIso()));
  const [view, setView] = useState<ReviewView>('timeline');
  const [roamingVisible, setRoamingVisible] = useState(false);
  const [scope, setScope] = useState<ReviewScope>('TODAY');
  /** Anchored 「···」 menu: the entry it acts on plus the button's window rect. */
  const [timelineMenu, setTimelineMenu] = useState<{ entry: TimelineEntry; anchor: PopoverAnchor } | null>(null);
  const daily = useDailySnapshot('review', date, visible);
  const snapshot = daily?.date === date ? daily : null;
  const settingsSnapshot = useSettingsSnapshot('review', visible);
  const goalHours = settingsSnapshot?.settings.dailyGoalHours ?? 0;
  const goalSeconds = goalHours > 0 ? goalHours * 3600 : null;
  const overviewRequest = useMemo(() => ({ scope, periodsBack: 0, anchorDate: date }), [date, scope]);
  const previousRequest = useMemo(() => previousWindowArgs(scope, date), [date, scope]);
  /**
   * The filmstrip reads the week it is showing, which is a different window
   * from the trend scope and from the selected day. It is only needed for its
   * subject dots, so it stays a separate bounded request rather than widening
   * `scope`. `anchorDate` takes the strip's Monday: a calendar-week window is
   * keyed by its Monday anyway, so every day of the same week resolves to one
   * cached window instead of seven aliases of it.
   */
  const weekRequest = useMemo(
    () => ({ scope: 'CALENDAR_WEEK' as ReviewScope, periodsBack: 0, anchorDate: weekStart }),
    [weekStart]
  );
  const reviewRequests = useMemo(
    () => scope === 'CALENDAR_WEEK'
      ? [overviewRequest, previousRequest]
      : [overviewRequest, previousRequest, weekRequest],
    [overviewRequest, previousRequest, weekRequest]
  );
  const overview = useReviewOverview(overviewRequest);
  const previousOverview = useReviewOverview(previousRequest);
  const weekOverview = useReviewOverview(weekRequest);
  useReviewDataVisibility('review', reviewRequests, visible);
  /**
   * A failed read keeps the last good snapshot rather than blanking the page, so
   * these two flags are what stop the page from presenting that snapshot as
   * current. Only the day's own records and the window being summarised count:
   * `previousRequest` feeds one delta caption and `weekRequest` only the
   * filmstrip's subject dots, and neither is a number this page states outright.
   */
  const dailyReadFailed = useDailyReadFailed(date);
  const overviewReadFailed = useReviewReadFailed(overviewRequest);
  const sessions = snapshot?.sessions ?? [];
  const dayTasks = snapshot?.tasks ?? [];
  const [chartWidth, setChartWidth] = useState(0);
  /**
   * The banner only earns its place where there is real content on screen that
   * might be behind Room: the timeline reads the day's snapshot, the trend reads
   * the window overview. Showing it while a read is merely still in flight (no
   * value yet) would repeat the 「正在读取」 the page already says.
   */
  const staleRead = view === 'trend'
    ? (overview !== null && overviewReadFailed)
    : view === 'timeline' && snapshot !== null && dailyReadFailed;
  const [pendingDelete, setPendingDelete] = useState<{
    sessionId: string; isExam: boolean; subjectName: string;
  } | null>(null);
  const [editingNote, setEditingNote] = useState<EditingNote | null>(null);
  /** A blank record for the selected day, opened from the timeline's capture card. */
  const [createOpen, setCreateOpen] = useState(false);
  const [confirmDeleteNote, setConfirmDeleteNote] = useState(false);
  const [busyNote, setBusyNote] = useState<string | null>(null);
  const [deleting, setDeleting] = useState(false);
  const deletingRef = useRef(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [deleteError, setDeleteError] = useState<string | null>(null);
  const [retrying, setRetrying] = useState(false);
  const entries = useMemo(() => buildTimelineEntries(snapshot), [snapshot]);
  // A new day, a new view, or a resize invalidates the anchor and the clamped
  // placement; closing is the only honest response.
  useEffect(() => { setTimelineMenu(null); }, [date, view, windowWidth, windowHeight]);
  useEffect(() => { if (!visible) setTimelineMenu(null); }, [visible]);
  const today = todayIso();
  const selectedDate = new Date(`${date}T12:00:00`);
  /**
   * Three weeks for the strip: the one in frame, and one on either side so a
   * drag pulls a real neighbouring week in. A week after today holds nothing but
   * future cells, so the forward page is simply omitted rather than rendered
   * blank — that is what makes the current week the end of the strip.
   */
  const filmstripPages = useMemo(() => {
    const dotColors = dominantSubjectColors(weekOverview?.days ?? [], weekOverview?.subjectDistribution ?? []);
    const build = (start: string) => ({ start, cells: buildFilmstrip(start, date, today, dotColors) });
    const previous = shiftIsoDate(weekStart, -7);
    return [
      build(previous),
      build(weekStart),
      ...(canPageWeek(weekStart, today, 1) ? [build(shiftIsoDate(weekStart, 7))] : []),
    ];
  }, [weekStart, date, today, weekOverview]);

  /**
   * Picking a day moves the selection, and the strip follows it.
   *
   * Selecting from a paged-back week is the one case where the two diverge: the
   * strip would otherwise keep showing the week the user is not looking at.
   */
  const selectDay = (nextDate: string) => {
    setDate(nextDate);
    setWeekStart(weekStartOf(nextDate));
  };
  const stepWeek = (deltaWeeks: number) => {
    setWeekStart(previous => shiftIsoDate(previous, deltaWeeks * 7));
  };

  /**
   * 预取计划：让「切日期」的第一帧就命中真实数据。
   *
   * 一次切日期需要当前窗口、环比窗口和所选日所在自然周；再往前/后各预取一个锚点，
   * 用户按下 ‹ / › 时目标窗口已经在热缓存里。所有窗口由原生侧合并成一次区间扫描，
   * 不是 N 次独立查询。
   *
   * 每日快照只为「下一步可能点到的日期」预取：时间线按天翻，趋势图的当日视图
   * 还要画小时分布；其他粒度下当前日期已由页面读取，不必重复。
   */
  const prefetchPlan = useMemo(() => {
    const previousAnchor = scope === 'TODAY'
      ? shiftIsoDate(date, -1)
      : scope === 'CALENDAR_WEEK' ? shiftIsoDate(date, -7) : shiftMonthAnchor(date, -1);
    const nextAnchor = scope === 'TODAY'
      ? stepForwardWithinToday(shiftIsoDate(date, 1), today)
      : scope === 'CALENDAR_WEEK'
        ? stepForwardWithinToday(shiftIsoDate(date, 7), today)
        : stepMonthForwardWithinToday(date, today);
    const anchors = [date, previousAnchor, nextAnchor]
      .filter((anchor, index, all) => all.indexOf(anchor) === index);
    const requests = new Map<string, { scope: ReviewScope; periodsBack: number; anchorDate: string }>();
    anchors.forEach(anchor => {
      [
        { scope, periodsBack: 0, anchorDate: anchor },
        previousWindowArgs(scope, anchor),
        // 时间线的周点阵读的是所选日所在的自然周；一并预取，切回时间线也不闪空。
        { scope: 'CALENDAR_WEEK' as ReviewScope, periodsBack: 0, anchorDate: anchor },
      ].forEach(request => {
        requests.set(reviewWindowKey(request), request);
      });
    });
    // 周表可以左右翻页，且翻页不改变选中日：它显示的那一周与相邻两周都要预取，
    // 否则每滑一格，点阵都要等一次桥读取才出现。
    if (view === 'timeline') {
      [shiftIsoDate(weekStart, -7), weekStart, shiftIsoDate(weekStart, 7)]
        .filter(week => week <= today)
        .forEach(week => {
          const request = { scope: 'CALENDAR_WEEK' as ReviewScope, periodsBack: 0, anchorDate: week };
          requests.set(reviewWindowKey(request), request);
        });
    }
    const dailyDates = view === 'timeline'
      ? [shiftIsoDate(date, -1), date, stepForwardWithinToday(shiftIsoDate(date, 1), today)]
      : scope === 'TODAY' ? anchors : [date];
    return {
      requests: [...requests.values()],
      dailyDates: dailyDates.filter((item, index, all) => all.indexOf(item) === index),
    };
  }, [date, scope, today, view, weekStart]);

  useEffect(() => {
    if (!visible) return;
    // 排到下一拍执行：当前帧先画完，连续切换只保留最后一次计划，避免拖动日期时堆积请求。
    const timer = setTimeout(() => {
      void prefetchReviewOverviews(prefetchPlan.requests);
      void prefetchDailySnapshots(prefetchPlan.dailyDates);
    }, 0);
    return () => clearTimeout(timer);
  }, [visible, prefetchPlan]);

  /**
   * Re-reads what the banner is complaining about.
   *
   * Both reads are awaited together and neither is allowed to reject out of the
   * handler: the coordinator already reports a failed read through the flags
   * that put the banner up, so a rejection here would be a second, louder
   * report of the same fact. What the user sees is the banner staying (still
   * failing) or going away (read succeeded).
   */
  const retryRead = async () => {
    if (retrying) return;
    setRetrying(true);
    try {
      await Promise.all([
        refreshDailySnapshot(date),
        refreshReviewOverview(overviewRequest),
      ]);
    } catch {
      // The flags decide the banner, not this handler.
    } finally {
      setRetrying(false);
    }
  };

  const toggleResolved = async (noteId: string) => {
    if (busyNote) return;
    setBusyNote(noteId);
    setActionError(null);
    try {
      if (!await YanjiDataNative.toggleNoteResolved(noteId)) throw new Error('refused');
    } catch { setActionError('疑点状态未保存，请重试。'); }
    finally { setBusyNote(null); }
  };
  const removeSession = async () => {
    if (!pendingDelete || deletingRef.current) return;
    deletingRef.current = true;
    setDeleting(true);
    setDeleteError(null);
    try {
      if (!await YanjiDataNative.deleteSessionRecord(pendingDelete.sessionId, pendingDelete.isExam)) throw new Error('refused');
      setPendingDelete(null);
    } catch { setDeleteError('删除失败，记录已保留。请重试或取消。'); }
    finally {
      deletingRef.current = false;
      setDeleting(false);
    }
  };
  /** Every management action of a timeline row lives in its anchored menu. */
  const timelineActions = (entry: TimelineEntry): TimelineAction[] => {
    if (entry.kind === 'session') {
      const session = entry.session;
      return [
        {
          key: 'delete',
          icon: 'delete' as const,
          label: '删除记录',
          danger: true,
          onPress: () => {
            setDeleteError(null);
            setPendingDelete({ sessionId: session.id, isExam: session.isExam, subjectName: session.subjectName });
          },
        },
      ];
    }
    const note = entry;
    return [
      ...(note.category === 'DOUBT' ? [{
        key: 'resolve',
        icon: 'check' as const,
        label: note.isResolved ? '标记为待解决' : '标记为已解决',
        onPress: () => void toggleResolved(note.noteId),
      }] : []),
      {
        key: 'edit',
        icon: 'compose' as const,
        label: '编辑记录',
        onPress: () => {
          setEditingNote({ id: note.noteId, content: note.content, category: note.category ?? 'INSPIRATION', isFavorite: note.isFavorite });
          setConfirmDeleteNote(false);
        },
      },
      {
        key: 'delete',
        icon: 'delete' as const,
        label: '删除记录',
        danger: true,
        onPress: () => {
          setEditingNote({ id: note.noteId, content: note.content, category: note.category ?? 'INSPIRATION', isFavorite: note.isFavorite });
          setConfirmDeleteNote(true);
        },
      },
    ];
  };
  const days = overview?.days ?? [];
  const columns = useMemo(() => buildTrendColumns(days, overview?.subjectDistribution ?? []), [days, overview]);
  const peak = peakTrendDay(columns);
  const delta = periodDelta(overview?.totalSeconds ?? 0, previousOverview?.totalSeconds ?? null);
  const deltaPercent = periodDeltaPercent(overview?.totalSeconds ?? 0, previousOverview?.totalSeconds ?? null);
  const goalDays = goalReachedDays(days, goalSeconds);
  const preciseSessions = useMemo(() => {
    const dayStart = new Date(`${date}T00:00:00`).getTime();
    const dayEnd = new Date(`${shiftIsoDate(date, 1)}T00:00:00`).getTime();
    return sessions.filter(session => session.durationSeconds > 0
      && (session.endTime > dayStart || session.startTime >= dayStart)
      && session.startTime < dayEnd).map(session => ({
        startTime: Math.max(session.startTime, dayStart),
        durationSeconds: session.durationSeconds,
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

  const pageHeader = (
    <>
      {/* The page title stands alone: the window (year / week) is already spelled
          out by the filmstrip and the period bar below, so an eyebrow above the
          title only repeated it. The view switch rides the right edge as a
          borderless capsule. */}
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: 8, paddingBottom: 6 }}>
        <Text numberOfLines={1} accessibilityRole="header" style={[typography.titleEditorial, { color: colors.ink, fontFamily: 'serif', flexShrink: 1 }]}>
          备考手帐
        </Text>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 2, flexShrink: 0 }}>
          <YanjiPaperSegmentedControl
            value={view}
            options={VIEWS}
            onChange={setView}
            accessibilityLabel="回顾视图"
            compact
            borderless
          />
          <YanjiIconButton icon="settings" onPress={openSettings} accessibilityLabel="设置与目标"
            size={32} iconSize={19} />
        </View>
      </View>
      {actionError ? <Text accessibilityRole="alert" style={[typography.bodyPrimary, { color: colors.danger, marginTop: YanjiSpacing.md }]}>{actionError}</Text> : null}

      {staleRead ? (
        <YanjiStaleDataNotice
          message={view === 'trend'
            ? '读取失败，以下统计可能不是最新。'
            : '读取失败，这一天的记录可能不是最新。'}
          actionLabel={retrying ? '读取中…' : '重新读取'}
          onAction={() => void retryRead()}
          busy={retrying}
          style={{ marginTop: YanjiSpacing.md }}
        />
      ) : null}
    </>
  );

  const timelineHeader = (
    <>
      {pageHeader}
      <WeekFilmstrip
        pages={filmstripPages}
        onSelect={selectDay}
        onStep={stepWeek}
        canStepForward={canPageWeek(weekStart, today, 1)}
      />
      <DaySummary snapshot={snapshot} />
      <YanjiCaptureCard
        title="写一条记录"
        hint="补记这一天的想法或卡点"
        accessibilityLabel="写一条记录"
        onPress={() => setCreateOpen(true)}
      />
      <TimelineState loading={!snapshot} entryCount={entries.length} planCount={dayTasks.length} />
      {/* The rows used to sit under this gap inside their own wrapper. Now they
          are list items, so the header has to carry the separation itself. */}
      <View style={{ height: YanjiSpacing.lg }} />
    </>
  );

  const trendBody = (
    <>
      {pageHeader}
      <PeriodSelectorBar
        scope={scope}
        anchorDate={date}
        onScopeChange={setScope}
        onPrevious={() => {
          if (scope === 'TODAY') setDate(prev => shiftIsoDate(prev, -1));
          else if (scope === 'CALENDAR_WEEK') setDate(prev => shiftIsoDate(prev, -7));
          else setDate(prev => shiftMonthAnchor(prev, -1));
        }}
        onNext={() => {
          if (scope === 'TODAY') setDate(prev => stepForwardWithinToday(shiftIsoDate(prev, 1), todayIso()));
          else if (scope === 'CALENDAR_WEEK') setDate(prev => stepForwardWithinToday(shiftIsoDate(prev, 7), todayIso()));
          else setDate(prev => stepMonthForwardWithinToday(prev, todayIso()));
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
  );

  const listProps = {
    testID: 'scroll-review',
    showsVerticalScrollIndicator: false,
    overScrollMode: 'never' as const,
    onScrollBeginDrag: () => setTimelineMenu(null),
    onMomentumScrollBegin: () => setTimelineMenu(null),
    // While the menu is up its backdrop owns every touch; hide the list from
    // TalkBack too, so the modal panel is the only thing being read.
    accessibilityElementsHidden: timelineMenu !== null,
    importantForAccessibility: timelineMenu ? 'no-hide-descendants' as const : 'auto' as const,
    contentContainerStyle: {
      // The Focus page sets the page-top rhythm for all three tabs.
      paddingHorizontal: layout.pageGutter, paddingTop: YanjiSpacing.sm, paddingBottom: contentPadding,
    },
  };

  return <View style={{ flex: 1, backgroundColor: colors.canvas }}>
    {view === 'timeline'
      ? <FlashList
          {...listProps}
          data={entries}
          keyExtractor={entry => entry.key}
          ListHeaderComponent={timelineHeader}
          renderItem={({ item, index }) => <TimelineItem entry={item}
            isFirst={index === 0} isLast={index === entries.length - 1}
            busyNote={busyNote !== null}
            onEditNote={note => {
              setEditingNote({ id: note.noteId, content: note.content, category: note.category ?? 'INSPIRATION' });
              setConfirmDeleteNote(false);
            }}
            onOpenMenu={(entry, anchor) => setTimelineMenu({ entry, anchor })} />}
        />
      : view === 'trend'
      ? <ScrollView {...listProps} style={{ flex: 1 }}>{trendBody}</ScrollView>
      : <ScrollView {...listProps} style={{ flex: 1 }}>
          {pageHeader}
          <JournalLibraryView
            visible={visible && view === 'journal'}
            onOpenRoaming={() => setRoamingVisible(true)}
            onEditNote={note => {
              setEditingNote(note);
              setConfirmDeleteNote(false);
            }}
          />
        </ScrollView>}

    {timelineMenu ? (
      <TimelineActionPopover
        anchor={timelineMenu.anchor}
        topInset={insets.top}
        containerWidth={windowWidth}
        containerHeight={windowHeight - insets.top}
        bottomInset={contentPadding}
        disabled={busyNote !== null}
        actions={timelineActions(timelineMenu.entry)}
        onClose={() => setTimelineMenu(null)}
      />
    ) : null}

    <RecordMomentModal
      visible={editingNote !== null || createOpen}
      date={date}
      editingNote={editingNote}
      initialConfirmDelete={confirmDeleteNote}
      onClose={() => { setEditingNote(null); setCreateOpen(false); setConfirmDeleteNote(false); }}
      onSaved={() => { setEditingNote(null); setCreateOpen(false); setConfirmDeleteNote(false); }}
    />
    <RoamingNotesModal
      visible={roamingVisible}
      onClose={() => setRoamingVisible(false)}
      onEditNote={note => {
        setRoamingVisible(false);
        setEditingNote(note);
        setConfirmDeleteNote(false);
      }}
    />
    <Modal visible={pendingDelete !== null} transparent animationType={reduceMotion ? 'none' : 'fade'} onRequestClose={() => { if (!deletingRef.current) setPendingDelete(null); }}>
      <View style={{ flex: 1, backgroundColor: paper.colors.scrim, justifyContent: 'flex-end' }}>
        <ScrollView style={{ flexGrow: 0, backgroundColor: colors.surface, borderTopLeftRadius: radius.sheet, borderTopRightRadius: radius.sheet }}
          contentContainerStyle={{ padding: layout.pageGutter, paddingBottom: insets.bottom + YanjiSpacing.lg }}>
          <YanjiPaperSheetHeading title={`删除「${pendingDelete?.subjectName ?? ''}」记录？`} subtitle="该记录与它的学习时长会一并删除，无法恢复。" onClose={() => { if (!deletingRef.current) setPendingDelete(null); }} disabled={deleting} />
          {deleteError ? <Text accessibilityRole="alert" style={[typography.bodyPrimary, { color: colors.danger, marginTop: YanjiSpacing.md }]}>{deleteError}</Text> : null}
          <YanjiPrimaryButton variant="danger" label={deleting ? '删除中…' : '确认删除'} onPress={() => void removeSession()} disabled={deleting} style={{ marginTop: YanjiSpacing.lg }} />
          <Pressable onPress={() => { if (!deletingRef.current) setPendingDelete(null); }} disabled={deleting} accessibilityRole="button" accessibilityLabel="取消删除记录" accessibilityState={{ disabled: deleting }} style={{ minHeight: layout.rowMinHeight, alignItems: 'center', justifyContent: 'center' }}>
            <Text style={[typography.bodyPrimary, { color: colors.secondary }]}>取消</Text>
          </Pressable>
        </ScrollView>
      </View>
    </Modal>
  </View>;
}

// ---------------------------------------------------------------------------
// The date filmstrip — a week frame the finger can page, one cell per day
//
// Three weeks are laid side by side and only the middle one is in frame, so a
// drag pulls the neighbouring week in rather than sliding the row off a blank
// edge. Paging is a way to look around, not a selection: the timeline keeps the
// selected day until a cell is tapped.
// ---------------------------------------------------------------------------

export interface FilmstripPage {
  /** The page's Monday — the identity React keys the row by. */
  start: string;
  cells: FilmstripDay[];
}

/** A drag must clear this much travel (or flick this fast) to commit a page. */
const FILMSTRIP_DRAG_THRESHOLD = 24;
const FILMSTRIP_FLICK_VELOCITY = 0.4;
const FILMSTRIP_SETTLE_MS = 220;

function WeekFilmstrip({ pages, onSelect, onStep, canStepForward }: {
  pages: readonly FilmstripPage[];
  onSelect: (date: string) => void;
  onStep: (deltaWeeks: number) => void;
  canStepForward: boolean;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout, radius } = paper;
  const reduceMotion = useReducedMotion();
  const translateX = useSharedValue(0);
  const [width, setWidth] = useState(0);
  const widthRef = useRef(0);
  /** A committed page: how far the finger had moved when it let go. */
  const releaseOffsetRef = useRef<number | null>(null);
  const stepRef = useRef(onStep);
  const canStepForwardRef = useRef(canStepForward);
  stepRef.current = onStep;
  canStepForwardRef.current = canStepForward;

  const settle = (to: number) => {
    translateX.value = reduceMotion ? to : withTiming(to, { duration: FILMSTRIP_SETTLE_MS });
  };

  const panResponder = useMemo(() => PanResponder.create({
    // Taps belong to the day cells and vertical drags to the list underneath;
    // only a clearly horizontal drag takes the gesture away from them.
    onMoveShouldSetPanResponderCapture: (_evt, { dx, dy }) =>
      Math.abs(dx) > 6 && Math.abs(dx) > Math.abs(dy) * 1.5,
    onPanResponderMove: (_evt, { dx }) => {
      const frame = widthRef.current;
      if (frame <= 0) return;
      // There is no week after the current one to reveal, so that direction is
      // a hard wall: moving the row would only expose the blank container.
      if (dx < 0 && !canStepForwardRef.current) return;
      translateX.value = Math.max(-2 * frame, Math.min(0, -frame + dx));
    },
    onPanResponderRelease: (_evt, { dx, vx }) => {
      const frame = widthRef.current;
      if (frame <= 0) return;
      const direction = dx < 0 ? 1 : -1;
      const travelled = Math.abs(dx) > Math.max(FILMSTRIP_DRAG_THRESHOLD, frame * 0.18)
        || Math.abs(vx) > FILMSTRIP_FLICK_VELOCITY;
      if (!travelled || (direction > 0 && !canStepForwardRef.current)) {
        settle(-frame);
        return;
      }
      // Committing re-centres the row on the incoming week, which moves that
      // page from its old slot (one frame away) into the middle one. Re-basing
      // the offset by exactly that distance keeps it under the finger, and the
      // strip then settles home from there — so the turn is continuous instead
      // of jumping a frame.
      const travel = Math.max(-frame, Math.min(frame, dx));
      releaseOffsetRef.current = -frame + travel + direction * frame;
      stepRef.current(direction);
    },
    onPanResponderTerminate: () => settle(-widthRef.current),
  }), [reduceMotion, translateX]);

  /**
   * Put the strip back in frame whenever the middle page or the frame changes.
   *
   * After a committed page this runs in the same commit that swaps the rows, so
   * the incoming week is positioned before the frame is painted.
   */
  const middleStart = pages[1]?.start;
  useLayoutEffect(() => {
    if (width <= 0) return;
    const released = releaseOffsetRef.current;
    releaseOffsetRef.current = null;
    if (released === null || reduceMotion) {
      translateX.value = -width;
      return;
    }
    // Both writes must land in one UI-thread job: a JS-side assignment is
    // scheduled, so the settle could start from the pre-rebase value and show
    // the incoming week one frame off.
    runOnUI((from: number, to: number) => {
      'worklet';
      translateX.value = from;
      translateX.value = withTiming(to, { duration: FILMSTRIP_SETTLE_MS });
    })(released, -width);
  }, [width, middleStart, reduceMotion, translateX]);
  const stripStyle = useAnimatedStyle(() => ({
    transform: [{ translateX: translateX.value }],
  }));

  return <View
    {...panResponder.panHandlers}
    onLayout={event => {
      const next = event.nativeEvent.layout.width;
      widthRef.current = next;
      setWidth(next);
    }}
    style={{
      paddingTop: 2,
      paddingBottom: 12,
      borderBottomWidth: layout.hairlineWidth,
      borderColor: colors.hairline,
      overflow: 'hidden',
    }}>
    <Animated.View style={[{ flexDirection: 'row' }, stripStyle]}>
      {pages.map((page, index) => <View
        key={page.start}
        testID={`filmstrip-page-${page.start}`}
        // Only the middle page is in frame; the neighbouring weeks are there to
        // be dragged in, and reading them out would duplicate every day of the
        // month for a screen reader.
        accessibilityElementsHidden={index !== 1}
        importantForAccessibility={index === 1 ? 'auto' : 'no-hide-descendants'}
        style={{ width, flexDirection: 'row', gap: YanjiSpacing.xs }}>
        {page.cells.map(cell => <Pressable key={cell.date} disabled={cell.isFuture} onPress={() => onSelect(cell.date)}
          accessibilityRole="button"
          accessibilityLabel={`${cell.date} ${cell.weekdayLabel}${cell.dotColor ? '，有学习记录' : ''}`}
          accessibilityState={{ selected: cell.isSelected, disabled: cell.isFuture }}
          // Swiping is the visual affordance; these two actions are the same
          // page step for a screen reader, which cannot perform a drag.
          accessibilityActions={[
            { name: 'decrement', label: '上一周' },
            { name: 'increment', label: '下一周' },
          ]}
          onAccessibilityAction={event => {
            if (event.nativeEvent.actionName === 'decrement') stepRef.current(-1);
            else if (event.nativeEvent.actionName === 'increment' && canStepForwardRef.current) stepRef.current(1);
          }}
          // The selected day follows the Focus page's selected-subject card: a light
          // brand wash with no rim, so the mark is the same accent family everywhere
          // instead of an ink-filled block on a paper page.
          style={({ pressed }) => ({
            flex: 1, minHeight: layout.filmstripCellMinHeight, paddingVertical: YanjiSpacing.sm,
            alignItems: 'center', justifyContent: 'center', borderRadius: radius.surface,
            backgroundColor: cell.isSelected ? colors.brandSubtle : 'transparent',
            opacity: pressed ? 0.65 : 1,
          })}>
          <Text style={[typography.captionMeta, {
            color: cell.isSelected ? colors.brandInk : colors.secondary,
            fontWeight: cell.isSelected ? '700' : '500',
          }]}>{cell.weekdayLabel}</Text>
          <Text style={[typography.bodyPrimary, {
            color: cell.isSelected ? colors.brandInk : cell.isFuture ? colors.secondary : colors.ink,
            fontWeight: cell.isFuture ? '500' : '700',
            marginTop: YanjiSpacing.xs,
          }]}>{cell.dayOfMonth}</Text>
          {/* The dot only exists where study time does — a zero day must not
              borrow the visual weight of a recorded one. */}
          {cell.dotColor ? <View style={{
            width: 5, height: 5, borderRadius: radius.pill, marginTop: YanjiSpacing.xs, backgroundColor: cell.dotColor,
          }} /> : <View style={{ height: 5, marginTop: YanjiSpacing.xs }} />}
        </Pressable>)}
      </View>)}
    </Animated.View>
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

function DaySummary({ snapshot }: {
  snapshot: DailySnapshot | null;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout } = paper;
  const completed = snapshot?.tasks.filter(task => task.completed).length ?? 0;
  const totalTasks = snapshot?.tasks.length ?? 0;
  // Two rows per column only: the metric name, then the number that carries the
  // page's weight. Units ride the number's baseline at body size so a long value
  // can shrink (`adjustsFontSizeToFit`) without knocking the row out of line.
  //
  // 「完成任务」 splits its fraction: 「0」 is the number the reader came for,
  // 「/1」 is the denominator it is measured against. Both used to be set at the
  // hero size, which made "out of how many" shout as loudly as the answer.
  const cells: ReadonlyArray<{
    label: string;
    value: string;
    /** The small, quiet tail of a value — a denominator, never the answer. */
    tail?: string;
    unit: string | null;
    spoken: string;
  }> = [
    {
      label: '专注时长',
      value: snapshot ? durationMetricMinutes(snapshot.totalDurationSeconds) : '—',
      unit: snapshot ? '分钟' : null,
      spoken: snapshot ? `专注时长 ${formatDuration(snapshot.totalDurationSeconds)}` : '正在读取专注时长',
    },
    {
      label: '完成任务',
      value: snapshot ? `${completed}` : '—',
      tail: snapshot ? `/${totalTasks}` : undefined,
      unit: null,
      spoken: snapshot ? `完成任务 ${completed}/${totalTasks}` : '正在读取任务',
    },
    {
      label: '专注次数',
      value: snapshot ? `${snapshot.focusCount}` : '—',
      unit: snapshot ? '次' : null,
      spoken: snapshot ? `专注次数 ${snapshot.focusCount} 次` : '正在读取专注次数',
    },
  ];
  return <View style={{
    flexDirection: 'row',
    paddingVertical: YanjiSpacing.md,
    borderBottomWidth: layout.hairlineWidth,
    borderColor: colors.hairline,
  }}>
    {cells.map((cell, index) => <View key={cell.label} accessible accessibilityLabel={cell.spoken} style={{
      flex: 1, alignItems: 'center', paddingHorizontal: YanjiSpacing.xs,
      borderLeftWidth: index > 0 ? layout.hairlineWidth : 0, borderColor: colors.hairline,
    }}>
      <Text numberOfLines={1} style={[typography.captionMeta, { color: colors.secondary }]}>{cell.label}</Text>
      <View style={{
        flexDirection: 'row',
        alignItems: 'baseline',
        justifyContent: 'center',
        marginTop: YanjiSpacing.xs,
        minHeight: typography.metricSummary.lineHeight,
      }}>
        <Text numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.6}
          style={[typography.metricSummary, { color: colors.ink }]}>
          {cell.value}
        </Text>
        {cell.tail ? (
          <Text numberOfLines={1} style={[typography.captionMeta, { color: colors.muted }]}>
            {cell.tail}
          </Text>
        ) : null}
        {cell.unit ? <Text style={[typography.bodyPrimary, { color: colors.secondary, marginLeft: 2 }]}>{cell.unit}</Text> : null}
      </View>
    </View>)}
  </View>;
}

// ---------------------------------------------------------------------------
// The timeline — one spine, clock on the left, event on the right
//
// The rows are rendered by the page's own FlashList (`ListHeaderComponent`
// carries the filmstrip and summary), so there is no local list wrapper here:
// a list nested inside the page's scroll view never receives a viewport height
// and therefore cannot recycle anything.
// ---------------------------------------------------------------------------

function TimelineItem({ entry, isFirst, isLast, busyNote, onEditNote, onOpenMenu }: {
  entry: TimelineEntry;
  isFirst: boolean;
  isLast: boolean;
  busyNote: boolean;
  onEditNote: (note: Extract<TimelineEntry, { kind: 'note' }>) => void;
  onOpenMenu: (entry: TimelineEntry, anchor: PopoverAnchor) => void;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors } = paper;
  if (entry.kind === 'session') {
    const session = entry.session;
    return <TimelineCompactRow
      timeLabel={formatClockFromEpoch(entry.at)}
      tagLabel={session.subjectName}
      tagIcon="clock3"
      tagColor={session.subjectColor || colors.secondary}
      dotColor={session.subjectColor || colors.secondary}
      metaText={formatDuration(session.durationSeconds)}
      isFirst={isFirst} isLast={isLast} disabled={busyNote}
      accessibilityLabel={`${session.subjectName}，${formatDuration(session.durationSeconds)}`}
      moreAccessibilityLabel={`更多操作：${session.subjectName}，${formatClockFromEpoch(session.startTime)} 起`}
      onMore={anchor => onOpenMenu(entry, anchor)}
    />;
  }
  const tagLabel = entry.category === 'DOUBT'
    ? (entry.isResolved ? '疑点 已解决' : '疑点 待解决')
    : entry.category === 'INSPIRATION' ? '灵感' : null;
  return <TimelineCompactRow
    timeLabel={formatClockFromEpoch(entry.at)}
    tagLabel={tagLabel}
    tagIcon={entry.category === 'DOUBT' ? 'doubt'
      : entry.category === 'INSPIRATION' ? 'inspiration' : undefined}
    tagTone={entry.category === 'DOUBT' ? 'brand' : entry.category === 'INSPIRATION' ? 'positive' : 'neutral'}
    content={entry.content}
    isFirst={isFirst} isLast={isLast} disabled={busyNote}
    accessibilityLabel={`编辑随笔：${entry.content}`}
    accessibilityHint="打开这条记录，可阅读全文或编辑"
    moreAccessibilityLabel={`更多操作：${entry.content.slice(0, 24)}`}
    onPressContent={() => onEditNote(entry)}
    onMore={anchor => onOpenMenu(entry, anchor)}
  />;
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
        <Text testID="review-date" style={[typography.captionMeta, {
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
  const { colors, typography, radius } = paper;
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
                ? `${formatDailyAverageDuration(overview.dailyAverageSeconds)}${goalDays ? `  达标 ${goalDays.reached}/${goalDays.elapsed} 天` : ''}`
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
            metaText={`${formatDuration(slice.minutes * 60)} (${formatPercent(slice.share)})`}
            ratio={slice.share}
            showBar={false}
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
