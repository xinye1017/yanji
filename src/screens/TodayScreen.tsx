/** Paper & Chrono: saved facts come from Room; live ticks stay in their leaf. */
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Modal, Pressable, ScrollView, Text, useWindowDimensions, View } from 'react-native';
import {
  Easing,
  runOnJS,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';
import Animated from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { onDataChanged, YanjiDataNative } from '../bridge';
import type { ActiveSessionState, NoteEntry, StudyTask } from '../bridge';
import {
  YanjiBadge, YanjiCaptureCard, YanjiChronoSpectrum, YanjiDayRhythmChart, YanjiEditorialHeader,
  YanjiEmptyLedgerRow, YanjiHairline, YanjiIcon, YanjiIconButton, YanjiLedgerChip,
  YanjiPaperSectionHeading, YanjiPaperSheetHeading, YanjiPrimaryButton, YanjiSectionToggle,
  YanjiStaleDataNotice, YanjiTaskLedgerRow, YanjiTextAction,
} from '../components/YanjiUI';
import { RecordMomentModal } from '../components/RecordMomentModal';
import { TimelineCompactRow } from '../components/TimelineCompactRow';
import { TimelineActionPopover } from '../components/TimelineActionPopover';
import type { PopoverAnchor, TimelineAction } from '../components/TimelineActionPopover';
import { TaskEditorModal } from '../components/TaskEditorModal';
import { useNavigationActions, useNavigationState } from '../navigation/NavigationShell';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { useBottomTabLayout } from '../navigation/useBottomTabLayout';
import { toSubtleColor, YanjiSpacing } from '../theme/tokens';
import {
  refreshDailySnapshot, refreshSettingsSnapshot,
  useDailyReadFailed, useDailySnapshot, useSettingsSnapshot,
} from '../state/dataRefresh';
import { syncActiveSession, useActiveSession, useTimerTick } from '../state/timerStore';
import {
  buildRhythmBuckets, compactDuration, DAY_PARTS, displayDate, goalClock,
  greeting, hourRangeLabel, legendDuration, rhythmSummary, spectrumLegend,
  summarizeSubjects, taskMeta,
} from './todayViewLogic';
import { examCountdownText, todayIso } from '../state/settingsViewLogic';

/**
 * What the page is currently explaining.
 *
 * A subject chip above the chart explains that subject's total; a highlight in
 * the expanded 专注时段 strip explains one session. They are different
 * statements, so the variant is explicit rather than inferred from a key.
 */
type Selection =
  | { kind: 'subject'; key: string; subjectId: string; subjectName: string; seconds: number }
  | {
    kind: 'session'; key: string; subjectId: string; subjectName: string; seconds: number;
    /** Wall-clock bounds of the session (epoch ms) — the exact interval asked for. */
    startTime: number; endTime: number;
    /** The day part whose bar was tapped, so the detail renders under that bar. */
    partName: string;
  }
  | null;

/** 「记录此刻」 reading order: `newest` = 倒序 (default), `oldest` = 正序. */
type JournalOrder = 'newest' | 'oldest';

/** How many rows a folded journal keeps — the number the old hard slice showed. */
const JOURNAL_FOLDED_ROWS = 2;


function localDay(day: Date): string {
  return [day.getFullYear(), String(day.getMonth() + 1).padStart(2, '0'), String(day.getDate()).padStart(2, '0')].join('-');
}
function duration(seconds: number): string {
  const minutes = Math.max(0, Math.floor(seconds / 60));
  if (minutes < 1 && seconds > 0) return '不足1分钟';
  if (minutes < 60) return `${minutes}分钟`;
  return `${Math.floor(minutes / 60)}小时${minutes % 60 ? `${minutes % 60}分` : ''}`;
}
/**
 * A remainder as a person says it: 「41 分钟」/「2 小时 38 分」.
 *
 * The hero prints hours and minutes as two numbers because they are the
 * headline; a sentence needs its units spelled out, and `compactDuration`
 * (`2h38m`) is the form for a chip or a legend, not for prose.
 */
function goalDelta(seconds: number): string {
  const minutes = Math.max(0, Math.floor(seconds / 60));
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours === 0) return `${minutes} 分钟`;
  return rest ? `${hours} 小时 ${rest} 分` : `${hours} 小时`;
}
function clock(seconds: number): string {
  const whole = Math.max(0, Math.floor(seconds));
  const hours = Math.floor(whole / 3600);
  const minutes = String(Math.floor((whole % 3600) / 60)).padStart(2, '0');
  const remainder = String(whole % 60).padStart(2, '0');
  return hours ? `${hours}:${minutes}:${remainder}` : `${minutes}:${remainder}`;
}
function timeLabel(timestamp: number): string {
  return new Date(timestamp).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false });
}
function carryOverSourceLabel(sourceDate: string, today: string): string {
  const yesterday = new Date(`${today}T12:00:00`);
  yesterday.setDate(yesterday.getDate() - 1);
  if (sourceDate === localDay(yesterday)) return '昨天的计划 尚未完成';
  const source = new Date(`${sourceDate}T12:00:00`);
  return `${source.getMonth() + 1}月${source.getDate()}日的计划 尚未完成`;
}

/**
 * A historical unfinished plan is still a ledger row, but its decisions are
 * different from today's: carry it forward, finish it where it belongs, or put
 * it aside for this session. Keeping this local avoids pretending it is a
 * Today task before the user has decided.
 */
function CarryOverLedgerRow({
  task,
  today,
  index,
  busy,
  disabled,
  isLast,
  onCarry,
  onComplete,
  onDefer,
}: {
  task: StudyTask;
  today: string;
  index: number;
  busy: boolean;
  disabled: boolean;
  isLast: boolean;
  onCarry: () => void;
  onComplete: () => void;
  onDefer: () => void;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout, radius } = paper;
  return (
    <View>
      <View style={{ paddingVertical: YanjiSpacing.sm, gap: YanjiSpacing.xs }}>
        <Text style={[typography.captionMeta, { color: colors.secondary }]}>
          {carryOverSourceLabel(task.date, today)}
        </Text>
        <Text numberOfLines={2} style={[typography.bodyPrimary, { color: colors.ink, fontWeight: '600' }]}>
          {task.title}
        </Text>
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: YanjiSpacing.xs + 2 }}>
          <YanjiLedgerChip label={task.subjectName} tone="subject" color={task.subjectColor} />
          <YanjiLedgerChip label={taskMeta(task)} tone="meta" />
        </View>
        <View
          accessibilityState={{ disabled, busy }}
          style={{ flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', columnGap: YanjiSpacing.md }}
        >
          <YanjiTextAction
            testID={`carry-over-carry-${index}`}
            label={busy ? '处理中…' : '加入今日'}
            accessibilityLabel={`加入今日：${task.title}`}
            onPress={onCarry}
            disabled={disabled}
          />
          <YanjiTextAction
            testID={`carry-over-complete-${index}`}
            label="标记完成"
            accessibilityLabel={`完成历史计划：${task.title}`}
            onPress={onComplete}
            disabled={disabled}
            tone="quiet"
          />
          <YanjiTextAction
            testID={`carry-over-defer-${index}`}
            label="暂时保留"
            accessibilityLabel={`暂时保留：${task.title}`}
            onPress={onDefer}
            disabled={disabled}
            tone="quiet"
          />
        </View>
      </View>
      {isLast ? null : <YanjiHairline />}
    </View>
  );
}

export function TodayScreen({ visible, foregroundRevision }: {
  visible: boolean; foregroundRevision: number;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout, radius } = paper;
  const { contentPadding } = useBottomTabLayout();
  const insets = useSafeAreaInsets();
  const windowSize = useWindowDimensions();
  const reduceMotion = useReducedMotion();
  const { openSettings, openTaskEditor, closeTaskEditor, selectTab, requestFocusPreset } = useNavigationActions();
  const { taskEditorOpen } = useNavigationState();
  const [now, setNow] = useState(() => new Date());
  const date = localDay(now);
  const daily = useDailySnapshot('today', date, visible);
  const snapshot = daily?.date === date ? daily : null;
  /**
   * A failed read leaves the previous snapshot in place, so the page can keep
   * showing something real. That is only honest while the page also says the
   * numbers may be behind — which is exactly what this flag drives.
   */
  const dailyReadFailed = useDailyReadFailed(date);
  const settingsSnapshot = useSettingsSnapshot('today', visible);
  const settings = settingsSnapshot?.settings ?? null;
  const countdown = settingsSnapshot?.examCountdown ?? null;
  const active = useActiveSession(visible);
  const [rhythmExpanded, setRhythmExpanded] = useState(false);
  /** Journal reading order and fold — the reader's choice, not a fixed rule. */
  const [journalOrder, setJournalOrder] = useState<JournalOrder>('newest');
  const [journalExpanded, setJournalExpanded] = useState(true);
  const [selection, setSelection] = useState<Selection>(null);
  const [recordOpen, setRecordOpen] = useState(false);
  const [editingNote, setEditingNote] = useState<NoteEntry | null>(null);
  const [confirmDeleteNote, setConfirmDeleteNote] = useState(false);
  /** Anchored 「···」 menu: the note it acts on plus the button's window rect. */
  const [momentMenu, setMomentMenu] = useState<{ note: NoteEntry; anchor: PopoverAnchor } | null>(null);
  const [taskInfo, setTaskInfo] = useState<StudyTask | null>(null);
  const [retrying, setRetrying] = useState(false);
  const [busyTask, setBusyTask] = useState<string | null>(null);
  const [busyNote, setBusyNote] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [carryOverTasks, setCarryOverTasks] = useState<StudyTask[]>([]);
  const [carryOverOpen, setCarryOverOpen] = useState(false);
  const [carryOverClosing, setCarryOverClosing] = useState(false);
  const [carryOverLoading, setCarryOverLoading] = useState(false);
  const [carryOverReadFailed, setCarryOverReadFailed] = useState(false);
  const [carryWrite, setCarryWrite] = useState<{ taskId: string; action: 'carry' | 'complete' } | null>(null);
  const [carryActionError, setCarryActionError] = useState<string | null>(null);
  const [deferredCarryTaskIds, setDeferredCarryTaskIds] = useState<Set<string>>(() => new Set());
  const previousDate = useRef(date);
  const carryReadSequence = useRef(0);
  const carryWriteInFlight = useRef(false);
  const carryOverProgress = useSharedValue(0);

  const openCarryOver = useCallback(() => {
    setCarryActionError(null);
    setCarryOverClosing(false);
    setCarryOverOpen(true);
    carryOverProgress.value = 0;
    carryOverProgress.value = withTiming(1, {
      duration: reduceMotion ? paper.motion.opacityReducedMs : paper.motion.sheetEnterMs,
      easing: Easing.out(Easing.cubic),
    });
  }, [carryOverProgress, paper.motion.opacityReducedMs, paper.motion.sheetEnterMs, reduceMotion]);

  const closeCarryOver = useCallback(() => {
    if (!carryOverOpen || carryOverClosing) return;
    if (reduceMotion) {
      setCarryOverOpen(false);
      return;
    }
    setCarryOverClosing(true);
    carryOverProgress.value = withTiming(0, {
      duration: paper.motion.sheetExitMs,
      easing: Easing.in(Easing.cubic),
    }, finished => {
      if (!finished) return;
      runOnJS(setCarryOverOpen)(false);
      runOnJS(setCarryOverClosing)(false);
    });
  }, [carryOverClosing, carryOverOpen, carryOverProgress, paper.motion.sheetExitMs, reduceMotion]);

  const carryOverBackdropStyle = useAnimatedStyle(() => ({
    opacity: carryOverProgress.value,
  }));
  const carryOverSheetStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: (1 - carryOverProgress.value) * windowSize.height }],
  }));

  useEffect(() => {
    if (previousDate.current !== date) {
      previousDate.current = date;
      if (visible) void refreshSettingsSnapshot();
      setSelection(null);
      setMomentMenu(null);
      closeCarryOver();
      setDeferredCarryTaskIds(new Set());
    }
  }, [closeCarryOver, date, visible]);
  useEffect(() => {
    if (!visible) return;
    setNow(new Date());
    const timer = setInterval(() => setNow(new Date()), 30_000);
    return () => clearInterval(timer);
  }, [visible, foregroundRevision]);
  useEffect(() => {
    if (!visible) {
      setMomentMenu(null);
      closeCarryOver();
    }
  }, [closeCarryOver, visible]);
  // A rotation or a window resize invalidates both the anchor and the menu's
  // clamped placement; closing is the only honest response.
  useEffect(() => { setMomentMenu(null); }, [windowSize.width, windowSize.height]);

  const loadCarryOverTasks = useCallback(async () => {
    if (!visible) {
      carryReadSequence.current += 1;
      return;
    }
    const sequence = ++carryReadSequence.current;
    setCarryOverLoading(true);
    try {
      const tasks = await YanjiDataNative.getCarryOverTasks();
      if (sequence !== carryReadSequence.current) return;
      setCarryOverTasks(tasks);
      setCarryOverReadFailed(false);
    } catch {
      if (sequence !== carryReadSequence.current) return;
      setCarryOverReadFailed(true);
    } finally {
      if (sequence === carryReadSequence.current) setCarryOverLoading(false);
    }
  }, [visible]);

  useEffect(() => {
    if (!visible) {
      carryReadSequence.current += 1;
      return;
    }
    void loadCarryOverTasks();
  }, [visible, foregroundRevision, date, loadCarryOverTasks]);

  useEffect(() => {
    if (!visible) return undefined;
    return onDataChanged(event => {
      if (event.type === 'tasks' || event.type === 'sessions') {
        void loadCarryOverTasks();
      }
    });
  }, [visible, loadCarryOverTasks]);

  const retryRead = useCallback(async () => {
    setRetrying(true);
    setActionError(null);
    try {
      await Promise.all([refreshDailySnapshot(date), refreshSettingsSnapshot(), syncActiveSession()]);
    } catch { setActionError('读取失败，请点击“重新读取”重试。'); }
    finally { setRetrying(false); }
  }, [date]);

  const tasks = snapshot?.tasks;
  const notes = snapshot?.notes;
  const sessions = snapshot?.sessions;
  const effectiveSeconds = snapshot?.totalDurationSeconds ?? 0;
  const pending = tasks?.filter(task => !task.completed) ?? [];
  const completed = tasks?.filter(task => task.completed) ?? [];
  /**
   * The journal is ordered and folded by the reader, not by a fixed rule.
   *
   * 「记录此刻」 used to hard-slice the two newest notes and then link away for
   * the rest, so a day with five notes silently hid three of them. The order is
   * now a state the reader sets (倒序 = newest first, the default), and the fold
   * is a control they press. Nothing is hidden unless they ask for it.
   */
  const journalNotes = useMemo(() => {
    const ordered = [...notes ?? []].sort((a, b) => journalOrder === 'oldest'
      ? a.timestamp - b.timestamp
      : b.timestamp - a.timestamp);
    return journalExpanded ? ordered : ordered.slice(0, JOURNAL_FOLDED_ROWS);
  }, [notes, journalOrder, journalExpanded]);
  const noteCount = notes?.length ?? 0;
  const visibleCarryOverTasks = useMemo(
    () => carryOverTasks.filter(task =>
      !task.completed && task.carriedOverToId === null && !deferredCarryTaskIds.has(task.id)),
    [carryOverTasks, deferredCarryTaskIds],
  );
  useEffect(() => {
    if (visibleCarryOverTasks.length === 0) closeCarryOver();
  }, [closeCarryOver, visibleCarryOverTasks.length]);
  const goalSeconds = settings ? (settings.dailyGoalHours ?? 0) * 3600 : null;
  const subjects = useMemo(() => summarizeSubjects(sessions ?? [], colors.secondary), [sessions, colors.secondary]);
  const legendItems = useMemo(() => spectrumLegend(subjects, tasks ?? []), [subjects, tasks]);
  const rhythmBuckets = useMemo(() => buildRhythmBuckets(date, sessions ?? [], colors.secondary), [date, sessions, colors.secondary]);
  const rhythmSummaryText = useMemo(
    () => rhythmSummary(rhythmBuckets, snapshot !== null, hourRangeLabel),
    [rhythmBuckets, snapshot],
  );
  const peakHourText = useMemo(() => {
    const measured = rhythmBuckets.filter(b => b.seconds > 0);
    if (measured.length === 0) return null;
    const peak = measured.reduce((max, b) => b.seconds > max.seconds ? b : max, measured[0]);
    return `${String(peak.hour).padStart(2, '0')}—${String(peak.hour + 1).padStart(2, '0')}`;
  }, [rhythmBuckets]);
  const rhythmToggleLabel = rhythmExpanded ? '收起今日学习节奏详情' : '展开今日学习节奏详情';
  const selectSegment = (next: NonNullable<Selection>) =>
    setSelection(current => current?.key === next.key ? null : next);
  const startTask = (task: StudyTask) => requestFocusPreset({
    taskId: task.id, subjectId: task.subjectId, subjectName: task.subjectName,
    title: task.title, plannedMinutes: task.plannedMinutes,
  });
  const toggleTask = async (task: StudyTask) => {
    if (busyTask) return;
    setBusyTask(task.id);
    setActionError(null);
    try {
      if (!await YanjiDataNative.toggleTask(task.id, !task.completed)) throw new Error('refused');
    } catch { setActionError('任务状态未保存，请再次点击勾选重试。'); }
    finally { setBusyTask(null); }
  };
  const handleCarryOverAction = async (task: StudyTask, action: 'carry' | 'complete') => {
    if (carryWriteInFlight.current) return;
    carryWriteInFlight.current = true;
    setCarryWrite({ taskId: task.id, action });
    setCarryActionError(null);
    try {
      if (action === 'carry') {
        await YanjiDataNative.carryTaskToToday(task.id);
      } else if (!await YanjiDataNative.toggleTask(task.id, true)) {
        throw new Error('refused');
      }
      if (action === 'carry') {
        setCarryOverTasks(current => current.filter(item => item.id !== task.id));
      } else {
        setCarryOverTasks(current => current.filter(item => item.id !== task.id));
      }
      // The write is already durable. A failed refresh leaves the Room-backed
      // stale-data notice in charge; it must not turn a committed write into a
      // misleading action failure.
      void refreshDailySnapshot(date).catch(() => {});
    } catch {
      setCarryActionError(action === 'carry'
        ? '未能加入今日，任务仍在待处理区，请重试。'
        : '未能标记完成，任务仍在待处理区，请重试。');
    } finally {
      carryWriteInFlight.current = false;
      setCarryWrite(null);
    }
  };
  const deferCarryOverTask = (taskId: string) => {
    if (carryWriteInFlight.current) return;
    setCarryActionError(null);
    setDeferredCarryTaskIds(current => new Set(current).add(taskId));
  };
  const toggleResolved = async (note: NoteEntry) => {
    if (busyNote) return;
    setBusyNote(note.id);
    setActionError(null);
    try {
      if (!await YanjiDataNative.toggleNoteResolved(note.id)) throw new Error('refused');
    } catch { setActionError('疑点状态未保存，请重试。'); }
    finally { setBusyNote(null); }
  };
  const openGoal = () => {
    // The goal is edited on the settings page only: one writer per stored value
    // means Today can never save a goal the settings row does not show.
    openSettings();
  };
  const newNote = () => { setEditingNote(null); setConfirmDeleteNote(false); setRecordOpen(true); };
  const openNote = (note: NoteEntry, confirmDelete = false) => {
    setMomentMenu(null);
    setEditingNote(note);
    setConfirmDeleteNote(confirmDelete);
    setRecordOpen(true);
  };
  /** The anchored menu is the only management surface a moment row has. */
  const noteMenuActions = (note: NoteEntry): TimelineAction[] => [
    ...(note.category === 'DOUBT' ? [{
      key: 'resolve',
      icon: 'check' as const,
      label: note.isResolved ? '标记为待解决' : '标记为已解决',
      onPress: () => void toggleResolved(note),
    }] : []),
    { key: 'edit', icon: 'compose', label: '编辑记录', onPress: () => openNote(note) },
    { key: 'delete', icon: 'delete', label: '删除记录', danger: true, onPress: () => openNote(note, true) },
  ];
  // Share the settings page's formatter rather than reading the native countdown
  // directly: the native value is clamped at 0, so `daysRemaining === 0` cannot
  // tell "today is the exam" from "the date passed weeks ago". Settings already
  // computes this locally and correctly; reading the payload here made the two
  // pages contradict each other after the exam date.
  const countdownExamDate = countdown?.examDate ?? '';
  const countdownLocal = countdownExamDate ? examCountdownText(countdownExamDate, todayIso()) : null;
  // `examCountdownText` already reads 「距初试 N 天」/「已过设定日期 N 天」, so the
  // badge only adds the year it belongs to rather than restating the count.
  const countdownLabel = countdown && countdownLocal
    ? `${countdownExamDate.slice(0, 4)} 考研 ${countdownLocal}`
    : null;
  const goalReached = Boolean(snapshot && goalSeconds && goalSeconds > 0 && effectiveSeconds >= goalSeconds);
  // The remainder is prose, not a chip: it sits under the hero and says how far
  // the day still has to go in the same unit the hero prints ("2 小时 38 分"),
  // where it used to answer in a different one (`+6h25m`).
  const savedStatus = !snapshot ? '正在读取今日记录…'
    : goalSeconds && effectiveSeconds > goalSeconds ? `超出目标 ${goalDelta(effectiveSeconds - goalSeconds)}`
      : goalSeconds && effectiveSeconds === goalSeconds ? '目标已达成'
        : goalSeconds ? `距目标还差 ${goalDelta(goalSeconds - effectiveSeconds)}`
          : snapshot.examCount > 0 ? `含 ${snapshot.examCount} 场模考`
            : '';

  return (
    <View style={{ flex: 1, backgroundColor: colors.canvas }}>
      <ScrollView testID="scroll-today" style={{ flex: 1 }}
        showsVerticalScrollIndicator={false} overScrollMode="never"
        onScrollBeginDrag={() => setMomentMenu(null)}
        onMomentumScrollBegin={() => setMomentMenu(null)}
        // While the menu is up its backdrop owns every touch; hide the list from
        // TalkBack too, so the modal panel is the only thing being read.
        accessibilityElementsHidden={momentMenu !== null || carryOverOpen}
        importantForAccessibility={momentMenu || carryOverOpen ? 'no-hide-descendants' : 'auto'}
        // Same distance from the status bar as the Focus page's header: the three
        // tabs share one page-top rhythm, so switching tabs never shifts the
        // headline down or up.
        contentContainerStyle={{ paddingHorizontal: layout.pageGutter, paddingTop: YanjiSpacing.sm, paddingBottom: contentPadding }}>
        {/* The greeting owns the top-left at Serif headline size; the date and exam
            countdown stack on the right as a clean text card that taps to open settings. */}
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'flex-start', gap: 14, paddingBottom: layout.sectionGap }}>
          <Text accessibilityRole="header"
            style={[typography.titleGreeting, { color: colors.ink, flex: 1 }]}>
            {greeting(now.getHours())}
          </Text>
          {/* Date and countdown stay plain text; the gear beside them is the way
              into settings, so the text itself no longer doubles as a button. */}
          <View style={{ flexDirection: 'row', alignItems: 'flex-start', gap: 8, flexShrink: 0 }}>
            <View style={{ alignItems: 'flex-end', paddingTop: 4 }}>
              <Text style={[typography.captionMeta, { color: colors.ink, fontWeight: '600', fontVariant: ['tabular-nums'] }]}>
                {displayDate(date)}
              </Text>
              {countdown && countdownLocal ? (
                <Text style={[typography.captionMeta, { color: colors.secondary, marginTop: 2 }]}>
                  {countdownLocal.includes('距初试')
                    ? <>距初试 <Text style={{ color: colors.ink, fontWeight: '600' }}>{countdown.daysRemaining}</Text> 天</>
                    : countdownLocal}
                </Text>
              ) : null}
            </View>
            <YanjiIconButton icon="settings" onPress={openSettings} accessibilityLabel="设置与目标"
              size={32} iconSize={19} />
          </View>
        </View>
        <YanjiHairline />

        {/* One number answers "how is today going"; the goal rides the same
            top line with its state beside it, so the fact that used to repeat
            across four corners of this block is now said once. */}
        {/* One spacing rule for the page: two sections are `sectionGap` apart,
            and a hairline is a divider that sits centred in that same gap on
            each side instead of hugging whichever neighbour it happens to
            follow. */}
        <View style={{ paddingTop: layout.sectionGap, paddingBottom: layout.sectionGap }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'baseline', gap: layout.gridGap }}>
            <Text style={[typography.captionMeta, { color: colors.secondary }]}>今日专注</Text>
            <Pressable onPress={openGoal} accessibilityRole="button" accessibilityLabel="调整日常学习目标"
              hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
              style={({ pressed }) => ({ opacity: pressed ? 0.65 : 1 })}>
              {goalSeconds !== null && goalSeconds > 0 ? (
                <Text style={[typography.captionMeta, { color: colors.secondary }]}>
                  目标 <Text style={{ color: colors.ink, fontWeight: '600' }}>{goalClock(goalSeconds)}</Text>
                  {/* No percentage: the hero number and the remainder line below
                      already answer "how far along", and a third readout of the
                      same ratio made the top line noisy. */}
                  {snapshot && goalReached ? (
                    <Text style={{ color: colors.positive, fontWeight: '600' }}>{'  已达成'}</Text>
                  ) : null}
                </Text>
              ) : (
                <Text style={[typography.captionMeta, { color: colors.brandInk }]}>
                  {goalSeconds === null ? '设置与学习目标 ›' : '设置日常学习目标 ›'}
                </Text>
              )}
            </Pressable>
          </View>
          <View accessible accessibilityLabel={snapshot ? `今日已保存专注 ${duration(effectiveSeconds)}` : '正在读取今日专注'}
            style={{ flexDirection: 'row', alignItems: 'baseline', marginTop: 3 }}>
            {snapshot ? <>
              <Text style={[typography.metricDaily, { color: colors.ink }]}>{String(Math.floor(effectiveSeconds / 3600))}</Text>
              <Text style={{ fontSize: 14, fontWeight: '600', color: colors.secondary, marginLeft: 5, marginRight: 8, transform: [{ translateY: -4 }] }}>小时</Text>
              <Text style={[typography.metricDaily, { color: colors.ink }]}>{String(Math.floor((effectiveSeconds % 3600) / 60)).padStart(2, '0')}</Text>
              <Text style={{ fontSize: 14, fontWeight: '600', color: colors.secondary, marginLeft: 5, transform: [{ translateY: -4 }] }}>分</Text>
            </> : <Text style={[typography.metricDaily, { color: colors.ink }]}>—</Text>}
          </View>
          {/* Both halves are numbers the reader came for, so both are inked and
              joined by the same 「 · 」 the rest of the page uses — the old line
              separated them with three blank spaces. */}
          {savedStatus ? (
            <Text style={[typography.captionMeta, { color: colors.secondary, marginTop: 7, marginBottom: 4 }]}>
              {savedStatus}
              <Text style={{ color: colors.muted }}>{' · '}</Text>
              已完整结束 <Text style={{ color: colors.ink, fontWeight: '600' }}>{snapshot?.focusCount ?? 0}</Text> 轮
            </Text>
          ) : null}
          <YanjiChronoSpectrum segments={subjects} totalSeconds={effectiveSeconds} goalSeconds={snapshot ? goalSeconds : null}
            accessibilityLabel={snapshot ? undefined : '正在读取今日专注'} />
          {legendItems.length > 0 ? (
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', columnGap: 15, rowGap: 6, marginTop: 10 }}>
              {legendItems.map(item => (
                <Pressable key={item.id}
                  onPress={() => selectSegment({ kind: 'subject', key: `subject:${item.id}`, subjectId: item.id, subjectName: item.label, seconds: item.seconds })}
                  accessibilityRole="button" accessibilityState={{ selected: selection?.kind === 'subject' && selection.subjectId === item.id }}
                  accessibilityLabel={`${item.label}，今日专注 ${duration(item.seconds)}`}
                  hitSlop={{ top: 8, bottom: 8, left: 4, right: 4 }}
                  style={({ pressed }) => ({ flexDirection: 'row', alignItems: 'center', gap: 6, opacity: pressed ? 0.65 : 1 })}>
                  <View style={{ width: layout.subjectSwatchSize, height: layout.subjectSwatchSize, borderRadius: layout.subjectSwatchSize / 2, backgroundColor: item.color }} />
                  <Text style={[typography.captionMeta, { color: colors.secondary, fontWeight: selection?.kind === 'subject' && selection.subjectId === item.id ? '700' : '500' }]}>
                    {item.label} <Text style={{ color: colors.ink, fontWeight: '600' }}>{legendDuration(item.seconds)}</Text>
                  </Text>
                </Pressable>
              ))}
            </View>
          ) : <Text style={[typography.captionMeta, { color: colors.secondary, marginTop: YanjiSpacing.sm }]}>
            {snapshot ? '暂无专注记录' : '正在读取今日记录…'}
          </Text>}
          {selection?.kind === 'subject' ? (
            <Text accessibilityLiveRegion="polite" style={[typography.captionMeta, { color: colors.secondary, marginTop: YanjiSpacing.xs }]}>
              {selection.subjectName}  {duration(selection.seconds)}
            </Text>
          ) : null}
          {!snapshot ? <YanjiTextAction label={retrying ? '读取中…' : '重新读取'} onPress={() => void retryRead()} disabled={retrying} /> : null}
          {/* The two banners are mutually exclusive by construction: with no
              snapshot there is nothing to be stale about, and the retry above
              already covers it. This one is for the worse-looking case — the
              page has content, and that content may be behind Room. */}
          {snapshot && dailyReadFailed ? (
            <YanjiStaleDataNotice
              message="读取失败，以下记录可能不是最新。"
              actionLabel={retrying ? '读取中…' : '重新读取'}
              onAction={() => void retryRead()}
              busy={retrying}
              style={{ marginTop: YanjiSpacing.sm }}
            />
          ) : null}
          {active ? (
            <View style={{
              marginTop: YanjiSpacing.sm, padding: YanjiSpacing.md,
              minHeight: layout.activeSessionMinHeight,
              flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: YanjiSpacing.sm,
              backgroundColor: colors.elevated, borderRadius: radius.compactCard,
              borderWidth: layout.hairlineWidth, borderColor: colors.hairline,
            }}>
              <LiveSessionReadout session={active} visible={visible} />
              <Pressable onPress={() => selectTab('focus')} accessibilityRole="button" accessibilityLabel="返回专注工作台"
                style={({ pressed }) => ({
                  minHeight: layout.rowMinHeight, minWidth: layout.rowMinHeight,
                  justifyContent: 'center', paddingHorizontal: YanjiSpacing.md,
                  backgroundColor: colors.brandSubtle, borderRadius: radius.compactCard,
                  opacity: pressed ? 0.65 : 1,
                })}>
                <Text style={[typography.captionMeta, { color: colors.brandInk }]}>返回专注</Text>
              </Pressable>
            </View>
          ) : pending.length === 0 ? (
            <View style={{ marginTop: YanjiSpacing.md }}>
              <YanjiPrimaryButton label="开始一轮专注" icon="play" onPress={() => selectTab('focus')} />
            </View>
          ) : null}
        </View>
        <YanjiHairline />
        {actionError ? <Text accessibilityRole="alert" style={[typography.bodyPrimary, { color: colors.danger, marginTop: YanjiSpacing.md }]}>{actionError}</Text> : null}

        <YanjiPaperSectionHeading
          title="今日计划"
          titleStyle={typography.titleSection}
          countText={snapshot ? undefined : '—'}
          spaceBefore={layout.sectionGap}
          align="baseline"
          right={<YanjiTextAction label="新建" accessibilityLabel="添加任务" onPress={openTaskEditor} />}
        />
        <View>
          {/* The empty state states what is missing and nothing else: the one
              way to add a task is the heading's 「新建」, so repeating an action
              inside the row would be a second entry point to the same sheet. */}
          {!tasks?.length ? <YanjiEmptyLedgerRow
            prompt={snapshot ? '还没有安排今天的计划。' : '正在读取今日计划…'}
            actionLabel={null}
            onPress={openTaskEditor} /> : null}
          {[...pending, ...completed].map((task, index, rows) => (
            <YanjiTaskLedgerRow key={task.id} title={task.title} subjectName={task.subjectName}
              meta={taskMeta(task)} accentColor={task.subjectColor} completed={task.completed}
              isLead={!task.completed && task.id === pending[0]?.id}
              isLast={index === rows.length - 1}
              busy={busyTask === task.id} disabled={busyTask !== null}
              onToggle={() => void toggleTask(task)} onEdit={() => setTaskInfo(task)}
              onStartFocus={() => startTask(task)} />
          ))}
        </View>

        {visibleCarryOverTasks.length > 0 ? (
          <Pressable testID="open-carry-over" onPress={openCarryOver}
            accessibilityRole="button" accessibilityLabel={`待处理计划，${visibleCarryOverTasks.length} 项`}
            accessibilityState={{ expanded: carryOverOpen && !carryOverClosing }}
            style={({ pressed }) => ({
              minHeight: layout.rowMinHeight, paddingVertical: YanjiSpacing.sm,
              flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: YanjiSpacing.sm,
              marginTop: YanjiSpacing.sm, borderTopWidth: layout.hairlineWidth, borderColor: colors.hairline,
              opacity: pressed ? 0.65 : 1,
            })}>
            <Text style={[typography.bodyPrimary, { color: colors.secondary }]}>待处理计划</Text>
            <YanjiIcon name="forward" size={16} color={colors.secondary} />
          </Pressable>
        ) : null}
        {carryOverReadFailed ? (
          <YanjiStaleDataNotice
            message={visibleCarryOverTasks.length > 0
              ? '待处理任务读取失败，内容可能不是最新。'
              : '待处理任务读取失败。'}
            actionLabel={carryOverLoading ? '读取中…' : '重新读取'}
            onAction={() => void loadCarryOverTasks()}
            busy={carryOverLoading}
            style={{ marginTop: YanjiSpacing.sm }}
          />
        ) : null}


        {/* The clock lives with its note; only the quiet spine sits outside.
            The heading owns the whole section gap, so the wrapper adds none.

            The heading's accessory is the reading order and the fold, not a
            create action: writing is what the section is *for*, so its entry is
            the slot at the end of the list (`YanjiCaptureCard`) rather than a
            text link competing with the title. */}
        <View>
          <YanjiPaperSectionHeading
            title="记录此刻"
            titleStyle={typography.titleSection}
            countText={snapshot ? `${noteCount} 条` : '—'}
            align="baseline"
            spaceBefore={layout.sectionGap}
            right={noteCount > 1 ? (
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 15 }}>
                {/* A choice states its current value (倒序 / 正序) so the label
                    never flips meaning between "what I picked" and "what I will
                    get"; the two labels are the same width, so the fold control
                    beside it cannot jump when the order changes. */}
                <YanjiSectionToggle
                  testID="journal-order"
                  label={journalOrder === 'newest' ? '倒序' : '正序'}
                  icon={journalOrder === 'newest' ? 'sortNewest' : 'sortOldest'}
                  accessibilityLabel={journalOrder === 'newest' ? '按时间倒序，最新在前' : '按时间正序，最早在前'}
                  accessibilityHint="切换记录的时间顺序"
                  accessibilityState={{ selected: journalOrder === 'oldest' }}
                  onPress={() => setJournalOrder(order => order === 'newest' ? 'oldest' : 'newest')}
                />
                {noteCount > JOURNAL_FOLDED_ROWS ? (
                  <YanjiSectionToggle
                    testID="journal-fold"
                    label={journalExpanded ? '收起' : '展开'}
                    icon={journalExpanded ? 'chevronUp' : 'chevronDown'}
                    accessibilityLabel={journalExpanded ? '收起记录列表' : '展开全部记录'}
                    accessibilityHint={journalExpanded
                      ? `收起后只显示最新 ${JOURNAL_FOLDED_ROWS} 条`
                      : `展开全部 ${noteCount} 条记录`}
                    accessibilityState={{ expanded: journalExpanded }}
                    onPress={() => setJournalExpanded(expanded => !expanded)}
                  />
                ) : null}
              </View>
            ) : undefined}
          />
          {journalNotes.length > 0 ? (
            <View style={{ marginTop: YanjiSpacing.xs }}>
              {journalNotes.map((note, index) => (
                <TimelineCompactRow key={note.id}
                  timeLabel={timeLabel(note.timestamp)}
                  tagLabel={note.category === 'DOUBT' ? (note.isResolved ? '疑点 已解决' : '疑点 待解决')
                    : note.category === 'INSPIRATION' ? '灵感' : null}
                  tagIcon={note.category === 'INSPIRATION' ? 'inspiration'
                    : note.category === 'DOUBT' ? 'doubt' : undefined}
                  tagTone={note.category === 'INSPIRATION' ? 'positive'
                    : note.category === 'DOUBT' ? 'brand' : 'neutral'}
                  content={note.content}
                  isFirst={index === 0} isLast={index === journalNotes.length - 1}
                  disabled={busyNote !== null}
                  onPressContent={() => openNote(note)}
                  onMore={anchor => setMomentMenu({ note, anchor })} />
              ))}
            </View>
          ) : null}
          {/* The entry is also the empty state: with nothing above it the card
              is the only thing in the section, and it already says what to do.
              Loading is the one case that still needs a sentence, because there
              is no snapshot to invite the reader into yet. */}
          {snapshot ? (
            <View style={{ marginTop: journalNotes.length > 0 ? YanjiSpacing.md : YanjiSpacing.xs }}>
              <YanjiCaptureCard
                title="写一条记录"
                hint="一个想法、一处卡点、一点收获"
                accessibilityLabel="新建今日记录"
                onPress={newNote}
              />
            </View>
          ) : (
            <Text style={[typography.captionMeta, { color: colors.secondary, paddingTop: YanjiSpacing.sm }]}>
              正在读取今日随笔…
            </Text>
          )}
        </View>

        {/* 今日学习节奏.
            Collapsed, this used to be a caption, a row of 18 fixed-width ticks
            and a chevron — a title with no subject, and a strip that read as a
            pagination indicator. The section now shows the day's shape on a
            labelled clock, so the collapsed state
            already answers "what is this?" without repeating the 06:00–24:00
            range that the axis labels already carry. Expanding is a named
            control, not just an arrow. */}
        {/* Same rule as the journal: the heading owns the section gap alone. */}
        <View>
          <YanjiPaperSectionHeading
            title="今日学习节奏"
            titleStyle={typography.titleSection}
            spaceBefore={layout.sectionGap}
            right={<Pressable onPress={() => setRhythmExpanded(expanded => !expanded)}
              accessibilityRole="button"
              accessibilityLabel={rhythmToggleLabel}
              accessibilityState={{ expanded: rhythmExpanded }}
              accessibilityHint={rhythmExpanded ? '收起专注时段详情' : '展开专注时段详情'}
              // No 48dp floor here: a min-height on the heading accessory
              // inflates the whole row, so this section reads as ~15dp further
              // from the block above than the other headings do. hitSlop keeps
              // the touch target while the glyph stays on the heading baseline.
              hitSlop={{ top: 14, bottom: 14, left: 8, right: 8 }}
              style={({ pressed }) => ({ flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.xs, opacity: pressed ? 0.65 : 1 })}>
              {rhythmExpanded ? (
                <Text style={[typography.captionMeta, { color: colors.secondary }]}>收起详情</Text>
              ) : peakHourText ? (
                <Text style={[typography.captionMeta, { color: colors.secondary }]}>
                  最集中 <Text style={{ color: colors.ink, fontWeight: '600' }}>{peakHourText}</Text>
                </Text>
              ) : (
                <Text style={[typography.captionMeta, { color: colors.secondary }]}>展开详情</Text>
              )}
            </Pressable>} />
          {/* Same air under every heading: the chart body starts a step below
              the title, like the plan rows and the journal timeline do. */}
          <View style={{ marginTop: YanjiSpacing.sm }}>
            <YanjiDayRhythmChart buckets={rhythmBuckets} currentHour={now.getHours()}
              startLabel="06:00" endLabel="24:00"
              accessibilityLabel={`今日学习节奏，时间范围 06:00 至 24:00。${rhythmSummaryText}`} />
          </View>
          {rhythmExpanded ? (
            <View style={{ marginTop: YanjiSpacing.md }}>
              {DAY_PARTS.map(part => {
                const startDate = new Date(`${date}T00:00:00`);
                startDate.setHours(part.from);
                const endDate = new Date(`${date}T00:00:00`);
                endDate.setHours(part.to);
                const start = startDate.getTime();
                const end = endDate.getTime();
                const segments = sessions?.filter(session => {
                  const sStart = session.startTime;
                  const sEnd = session.endTime > sStart ? session.endTime : sStart + session.durationSeconds * 1000;
                  return session.durationSeconds > 0 && sEnd > start && sStart < end;
                }) ?? [];
                // The detail belongs under the bar it was read from, so a tap
                // on a highlight explains itself in place instead of in a
                // footer somewhere below four day parts.
                const detail = selection?.kind === 'session' && selection.partName === part.name
                  ? selection
                  : null;
                return <View key={part.name} style={{ marginBottom: YanjiSpacing.md }}>
                  <View style={{ flexDirection: 'row', justifyContent: 'space-between', flexWrap: 'wrap', gap: layout.gridGap, marginBottom: YanjiSpacing.xs }}>
                    <Text style={[typography.captionMeta, { color: colors.ink }]}>{part.name}</Text>
                    <Text style={[typography.captionMeta, { color: colors.secondary }]}>{String(part.from).padStart(2, '0')}:00—{String(part.to).padStart(2, '0')}:00</Text>
                  </View>
                  <View
                    style={{ height: layout.spectrumHeight, backgroundColor: colors.subtle, borderRadius: radius.tag, overflow: 'hidden' }}>
                    {segments.map(session => {
                      const sStart = session.startTime;
                      const sEnd = session.endTime > sStart ? session.endTime : sStart + session.durationSeconds * 1000;
                      const segLeft = Math.max(0, sStart - start) / (end - start) * 100;
                      const segWidth = Math.max(1, (Math.min(end, sEnd) - Math.max(start, sStart)) / (end - start) * 100);
                      const selected = selection?.kind === 'session' && selection.key === `session:${session.id}`;
                      return (
                        <Pressable
                          key={session.id}
                          onPress={() => selectSegment({
                            kind: 'session',
                            key: `session:${session.id}`,
                            subjectId: session.subjectId,
                            subjectName: session.subjectName,
                            seconds: session.durationSeconds,
                            startTime: sStart,
                            endTime: sEnd,
                            partName: part.name,
                          })}
                          accessibilityRole="button"
                          accessibilityState={{ selected }}
                          accessibilityLabel={`${session.subjectName}，${timeLabel(sStart)} 至 ${timeLabel(sEnd)}，${duration(session.durationSeconds)}`}
                          hitSlop={{ top: 14, bottom: 14, left: 2, right: 2 }}
                          style={({ pressed }) => ({
                            position: 'absolute', top: 0, bottom: 0,
                            left: `${segLeft}%`,
                            width: `${segWidth}%`,
                            backgroundColor: session.subjectColor || colors.secondary,
                            // Once one highlight is selected the others recede,
                            // so the detail has an obvious source on the strip.
                            opacity: pressed ? 0.7 : selection && !selected ? 0.35 : 1,
                          })}
                        />
                      );
                    })}
                  </View>
                  {detail ? (
                    <Text accessibilityLiveRegion="polite" style={[typography.captionMeta, { color: colors.ink, marginTop: YanjiSpacing.xs }]}>
                      {timeLabel(detail.startTime)}—{timeLabel(detail.endTime)}  {detail.subjectName}  {duration(detail.seconds)}
                    </Text>
                  ) : null}
                </View>;
              })}
            </View>
          ) : null}
        </View>
      </ScrollView>

      {carryOverOpen && visible ? (
        <Modal visible transparent animationType="none" onRequestClose={closeCarryOver}>
          <Animated.View pointerEvents={carryOverClosing ? 'none' : 'auto'}
            style={[{ flex: 1, backgroundColor: colors.scrim }, carryOverBackdropStyle]}>
            <Pressable testID="close-carry-over-backdrop" onPress={closeCarryOver}
              accessibilityRole="button" accessibilityLabel="关闭待处理计划" style={{ flex: 1 }} />
            <Animated.View accessibilityViewIsModal style={[{
              maxHeight: windowSize.height - insets.top - YanjiSpacing.xl,
              backgroundColor: colors.surface, borderTopLeftRadius: radius.sheet, borderTopRightRadius: radius.sheet,
              paddingHorizontal: layout.pageGutter, paddingTop: YanjiSpacing.lg, paddingBottom: insets.bottom + YanjiSpacing.lg,
            }, carryOverSheetStyle]}>
              <YanjiPaperSheetHeading title={`待处理计划 (${visibleCarryOverTasks.length} 项)`}
                subtitle="保留原计划日期，选择接下来如何安排。" onClose={closeCarryOver} />
              <ScrollView style={{ flexShrink: 1 }} showsVerticalScrollIndicator={false}>
                {carryOverReadFailed ? (
                  <YanjiStaleDataNotice message="读取失败，以下计划可能不是最新。"
                    actionLabel={carryOverLoading ? '读取中…' : '重新读取'}
                    onAction={() => void loadCarryOverTasks()} busy={carryOverLoading} />
                ) : null}
                {carryActionError ? <Text accessibilityRole="alert" style={[typography.bodyPrimary, { color: colors.danger }]}>{carryActionError}</Text> : null}
                {visibleCarryOverTasks.map((task, index) => (
                  <CarryOverLedgerRow key={task.id} task={task} today={date} index={index}
                    busy={carryWrite?.taskId === task.id} disabled={carryWrite !== null}
                    isLast={index === visibleCarryOverTasks.length - 1}
                    onCarry={() => void handleCarryOverAction(task, 'carry')}
                    onComplete={() => void handleCarryOverAction(task, 'complete')}
                    onDefer={() => deferCarryOverTask(task.id)} />
                ))}
              </ScrollView>
            </Animated.View>
          </Animated.View>
        </Modal>
      ) : null}
      {momentMenu ? (
        <TimelineActionPopover
          anchor={momentMenu.anchor}
          topInset={insets.top}
          containerWidth={windowSize.width}
          containerHeight={windowSize.height - insets.top}
          bottomInset={contentPadding}
          disabled={busyNote !== null}
          actions={noteMenuActions(momentMenu.note)}
          onClose={() => setMomentMenu(null)}
        />
      ) : null}
      <RecordMomentModal
        visible={recordOpen}
        date={editingNote?.date ?? date}
        editingNote={editingNote}
        initialConfirmDelete={confirmDeleteNote}
        onClose={() => { setRecordOpen(false); setEditingNote(null); setConfirmDeleteNote(false); }}
        onSaved={() => { setRecordOpen(false); setEditingNote(null); setConfirmDeleteNote(false); }}
      />
      <TaskEditorModal
        visible={taskEditorOpen || !!taskInfo}
        date={date}
        editingTask={taskInfo}
        onClose={() => { setTaskInfo(null); closeTaskEditor(); }}
        onSaved={() => {
          setTaskInfo(null);
          closeTaskEditor();
          void refreshDailySnapshot(date);
          void loadCarryOverTasks();
        }}
      />
    </View>
  );
}

function LiveSessionReadout({ session, visible }: {
  session: ActiveSessionState; visible: boolean;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const tick = useTimerTick(visible);
  const seconds = session.isCountdown ? tick?.remainingSeconds ?? session.remainingSeconds : tick?.elapsedSeconds ?? session.elapsedSeconds;
  const isPaused = session.isPaused || tick?.isPaused;
  return <View style={{ flexGrow: 1, flexBasis: paper.layout.activeSessionReadoutMinWidth }}>
    <Text style={[paper.typography.captionMeta, { color: isPaused ? paper.colors.secondary : paper.colors.brandInk }]}>
      {session.subjectName} {isPaused ? '已暂停' : '正在专注'}
    </Text>
    <View accessible accessibilityLabel={`${session.isCountdown ? '本轮剩余' : '本轮已持续'} ${clock(seconds)}，${isPaused ? '已暂停' : '正在专注'}`}
      accessibilityHint="本轮尚未计入今日已保存总量"
      style={{ flexDirection: 'row', flexWrap: 'wrap', alignItems: 'baseline', columnGap: YanjiSpacing.sm }}>
      <Text style={[paper.typography.metricSession, { color: paper.colors.ink }]}>{clock(seconds)}</Text>
      <Text style={[paper.typography.captionMeta, { color: paper.colors.secondary }]}>{session.isCountdown ? '本轮剩余' : '已持续'}</Text>
    </View>
  </View>;
}
