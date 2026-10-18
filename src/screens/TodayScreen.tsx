/** Paper & Chrono: saved facts come from Room; live ticks stay in their leaf. */
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { YanjiDataNative } from '../bridge';
import type { ActiveSessionState, NoteEntry, StudyTask } from '../bridge';
import {
  YanjiBadge, YanjiCard, YanjiChronoSpectrum, YanjiDayRhythmChart, YanjiEditorialHeader,
  YanjiEmptyLedgerRow, YanjiHairline, YanjiIcon, YanjiIconButton,
  YanjiPaperSectionHeading, YanjiPaperSegmentedControl,
  YanjiPrimaryButton, YanjiSubjectProportionRow,
  YanjiTaskLedgerRow, YanjiTextAction,
} from '../components/YanjiUI';
import { RecordMomentModal } from '../components/RecordMomentModal';
import { TaskEditorModal } from '../components/TaskEditorModal';
import { useNavigationActions, useNavigationState } from '../navigation/NavigationShell';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { useBottomTabLayout } from '../navigation/useBottomTabLayout';
import { YanjiSpacing } from '../theme/tokens';
import {
  refreshDailySnapshot, refreshSettingsSnapshot, useDailySnapshot, useSettingsSnapshot,
} from '../state/dataRefresh';
import { syncActiveSession, useActiveSession, useTimerTick } from '../state/timerStore';
import {
  buildRhythmBuckets, compactDuration, DAY_PARTS, displayDate, goalClock,
  greeting, hourRangeLabel, rhythmSummary, sessionPrecise, spectrumLegend,
  summarizeSubjects, taskMeta,
} from './todayViewLogic';
import { examCountdownText, todayIso } from '../state/settingsViewLogic';

type ChartMode = 'subjects' | 'timeline';
type Selection = { key: string; subjectId: string; subjectName: string; seconds: number } | null;
const CHART_MODES = [
  { value: 'subjects', label: '学科投入' }, { value: 'timeline', label: '专注时段' },
] as const;
const ENTRY_ICON_SIZE = 17;


function localDay(day: Date): string {
  return [day.getFullYear(), String(day.getMonth() + 1).padStart(2, '0'), String(day.getDate()).padStart(2, '0')].join('-');
}
function duration(seconds: number): string {
  const minutes = Math.max(0, Math.floor(seconds / 60));
  if (minutes < 1 && seconds > 0) return '不足1分钟';
  if (minutes < 60) return `${minutes}分钟`;
  return `${Math.floor(minutes / 60)}小时${minutes % 60 ? `${minutes % 60}分` : ''}`;
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
function noteTitle(note: NoteEntry): string {
  return note.content.trim().split('\n').find(line => line.trim())?.trim() || '无标题记录';
}

export function TodayScreen({ visible, foregroundRevision }: {
  visible: boolean; foregroundRevision: number;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout, radius } = paper;
  const { contentPadding } = useBottomTabLayout();
  const { openSettings, openTaskEditor, closeTaskEditor, selectTab, requestFocusPreset } = useNavigationActions();
  const { taskEditorOpen } = useNavigationState();
  const [now, setNow] = useState(() => new Date());
  const date = localDay(now);
  const daily = useDailySnapshot('today', date, visible);
  const snapshot = daily?.date === date ? daily : null;
  const settingsSnapshot = useSettingsSnapshot('today', visible);
  const settings = settingsSnapshot?.settings ?? null;
  const countdown = settingsSnapshot?.examCountdown ?? null;
  const active = useActiveSession(visible);
  const [chart, setChart] = useState<ChartMode>('subjects');
  const [rhythmExpanded, setRhythmExpanded] = useState(false);
  const [selection, setSelection] = useState<Selection>(null);
  const [recordOpen, setRecordOpen] = useState(false);
  const [editingNote, setEditingNote] = useState<NoteEntry | null>(null);
  const [taskInfo, setTaskInfo] = useState<StudyTask | null>(null);
  const [retrying, setRetrying] = useState(false);
  const [busyTask, setBusyTask] = useState<string | null>(null);
  const [busyNote, setBusyNote] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const previousDate = useRef(date);

  useEffect(() => {
    if (previousDate.current !== date) {
      previousDate.current = date;
      if (visible) void refreshSettingsSnapshot();
      setSelection(null);
    }
  }, [date, visible]);
  useEffect(() => {
    if (!visible) return;
    setNow(new Date());
    const timer = setInterval(() => setNow(new Date()), 30_000);
    return () => clearInterval(timer);
  }, [visible, foregroundRevision]);

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
  const latestNotes = useMemo(() => [...notes ?? []].sort((a, b) => b.timestamp - a.timestamp).slice(0, 2), [notes]);
  const todayDoubts = useMemo(() => (notes ?? []).filter(note => note.category === 'DOUBT'), [notes]);
  const resolvedDoubts = useMemo(() => todayDoubts.filter(note => note.isResolved), [todayDoubts]);
  const goalSeconds = settings ? (settings.dailyGoalHours ?? 0) * 3600 : null;
  const subjects = useMemo(() => summarizeSubjects(sessions ?? [], colors.secondary), [sessions, colors.secondary]);
  const legendItems = useMemo(() => spectrumLegend(subjects, tasks ?? []), [subjects, tasks]);
  const rhythmBuckets = useMemo(() => buildRhythmBuckets(date, sessions ?? [], colors.secondary), [date, sessions, colors.secondary]);
  const rhythmSummaryText = useMemo(
    () => rhythmSummary(rhythmBuckets, snapshot !== null, hourRangeLabel),
    [rhythmBuckets, snapshot],
  );
  const rhythmToggleLabel = rhythmExpanded ? '收起今日学习节奏详情' : '展开今日学习节奏详情';
  const chartTotal = subjects.reduce((sum, subject) => sum + subject.seconds, 0);
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
  const toggleFavorite = async (note: NoteEntry) => {
    if (busyNote) return;
    setBusyNote(note.id);
    setActionError(null);
    try {
      if (!await YanjiDataNative.toggleFavoriteNote(note.id)) throw new Error('refused');
    } catch { setActionError('收藏未保存，请再次点击收藏重试。'); }
    finally { setBusyNote(null); }
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
  const newNote = () => { setEditingNote(null); setRecordOpen(true); };
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
    ? `${countdownExamDate.slice(0, 4)} 考研 · ${countdownLocal}`
    : null;
  const savedStatus = !snapshot ? '正在读取今日记录…'
    : goalSeconds && effectiveSeconds > goalSeconds ? `超出目标 +${compactDuration(effectiveSeconds - goalSeconds)}`
      : snapshot.examCount > 0 ? `含 ${snapshot.examCount} 场模考`
        : active ? '当前一轮进行中'
          : goalSeconds && effectiveSeconds === goalSeconds ? '今日目标已达成'
            : goalSeconds && effectiveSeconds > 0 ? `距目标还差 ${compactDuration(goalSeconds - effectiveSeconds)}`
              : '';

  return (
    <View style={{ flex: 1, backgroundColor: colors.canvas }}>
      <ScrollView testID="scroll-today" style={{ flex: 1 }}
        showsVerticalScrollIndicator={false} overScrollMode="never"
        contentContainerStyle={{ paddingHorizontal: layout.pageGutter, paddingTop: YanjiSpacing.lg, paddingBottom: contentPadding }}>
        <YanjiEditorialHeader
          eyebrow={displayDate(date)}
          eyebrowBadge={countdown && countdownLabel ? (
            <Pressable onPress={openSettings} accessibilityRole="button"
              accessibilityLabel={`考试日期 ${countdown.examDate}，${countdown.daysRemaining >= 0 ? `距初试${countdown.daysRemaining}天` : '考试日期已过'}`}
              hitSlop={{ top: 12, bottom: 12, left: 8, right: 8 }}
              style={({ pressed }) => ({ opacity: pressed ? 0.65 : 1 })}>
              <YanjiBadge tone={countdown.daysRemaining >= 0 ? 'brand' : 'neutral'} label={countdownLabel} />
            </Pressable>
          ) : undefined}
          title={greeting(now.getHours())}
          titleStyle={typography.titleGreeting}
          trailing={<YanjiIconButton icon="settings" onPress={openSettings} accessibilityLabel="设置与目标" size={layout.rowMinHeight} />}
        />
        <YanjiHairline style={{ marginTop: 8 }} />

        <View style={{ paddingVertical: YanjiSpacing.md }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: layout.gridGap }}>
            <Text style={[typography.captionMeta, { color: colors.secondary }]}>今日累计专注</Text>
            <Pressable onPress={openGoal} accessibilityRole="button" accessibilityLabel="调整日常学习目标"
              style={({ pressed }) => ({ minHeight: layout.rowMinHeight, justifyContent: 'center', opacity: pressed ? 0.65 : 1 })}>
              {goalSeconds !== null && goalSeconds > 0 ? (
                <Text style={[typography.captionMeta, { color: colors.secondary }]}>
                  目标 {goalClock(goalSeconds)} ·{' '}
                  <Text style={{ color: colors.ink, fontWeight: '700' }}>{snapshot ? `${Math.round(effectiveSeconds / goalSeconds * 100)}%` : '—'}</Text>
                </Text>
              ) : (
                <Text style={[typography.captionMeta, { color: colors.brandInk }]}>
                  {goalSeconds === null ? '设置与学习目标 ›' : '设置日常学习目标 ›'}
                </Text>
              )}
            </Pressable>
          </View>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', alignItems: 'flex-end', justifyContent: 'space-between', gap: layout.gridGap, marginBottom: YanjiSpacing.md }}>
            <View accessible accessibilityLabel={snapshot ? `今日已保存专注 ${duration(effectiveSeconds)}` : '正在读取今日专注'}
              style={{ flexDirection: 'row', alignItems: 'baseline', gap: YanjiSpacing.xs }}>
              {snapshot ? <>
                <Text style={[typography.metricDaily, { color: colors.ink }]}>{String(Math.floor(effectiveSeconds / 3600)).padStart(2, '0')}</Text>
                <Text style={[typography.bodyPrimary, { color: colors.secondary, marginRight: YanjiSpacing.xs }]}>h</Text>
                <Text style={[typography.metricDaily, { color: colors.ink }]}>{String(Math.floor(effectiveSeconds % 3600 / 60)).padStart(2, '0')}</Text>
                <Text style={[typography.bodyPrimary, { color: colors.secondary }]}>m</Text>
              </> : <Text style={[typography.metricDaily, { color: colors.ink }]}>—</Text>}
            </View>
            <View style={{ alignItems: 'flex-end', paddingBottom: YanjiSpacing.xs, flexShrink: 1 }}>
              {snapshot ? <Text style={[typography.captionMeta, { color: colors.secondary }]}>
                已完成 <Text style={{ color: colors.ink, fontWeight: '700' }}>{snapshot.focusCount}</Text> 轮专注
              </Text> : null}
              {savedStatus ? <Text style={[typography.captionMeta, { color: colors.secondary, marginTop: YanjiSpacing.xs }]}>{savedStatus}</Text> : null}
            </View>
          </View>
          <YanjiChronoSpectrum segments={subjects} totalSeconds={effectiveSeconds} goalSeconds={snapshot ? goalSeconds : null}
            accessibilityLabel={snapshot ? undefined : '正在读取今日专注'} />
          {legendItems.length > 0 ? (
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', columnGap: YanjiSpacing.md, marginTop: YanjiSpacing.xs }}>
              {legendItems.map(item => (
                <Pressable key={item.id}
                  onPress={() => selectSegment({ key: `subject:${item.id}`, subjectId: item.id, subjectName: item.label, seconds: item.seconds })}
                  accessibilityRole="button" accessibilityState={{ selected: selection?.subjectId === item.id }}
                  accessibilityLabel={`${item.label}，今日专注 ${duration(item.seconds)}`}
                  style={({ pressed }) => ({ minHeight: layout.rowMinHeight, flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.xs, opacity: pressed ? 0.65 : 1 })}>
                  <View style={{ width: layout.subjectSwatchSize, height: layout.subjectSwatchSize, borderRadius: layout.subjectSwatchSize / 2, backgroundColor: item.color }} />
                  <Text style={[typography.captionMeta, { color: colors.secondary, fontWeight: selection?.subjectId === item.id ? '700' : '500' }]}>
                    {item.label} {compactDuration(item.seconds)}
                  </Text>
                </Pressable>
              ))}
            </View>
          ) : <Text style={[typography.captionMeta, { color: colors.secondary, marginTop: YanjiSpacing.sm }]}>
            {snapshot ? '按自己的步调开始今天的第一轮专注。' : '正在读取今日记录，可点击下方重新读取。'}
          </Text>}
          {selection?.key.startsWith('subject:') ? (
            <Text accessibilityLiveRegion="polite" style={[typography.captionMeta, { color: colors.secondary, marginTop: YanjiSpacing.xs }]}>
              {selection.subjectName} · {duration(selection.seconds)}
            </Text>
          ) : null}
          {!snapshot ? <YanjiTextAction label={retrying ? '读取中…' : '重新读取'} onPress={() => void retryRead()} disabled={retrying} /> : null}
          {active ? (
            <View style={{ marginTop: YanjiSpacing.md, padding: YanjiSpacing.md, backgroundColor: colors.elevated, borderRadius: radius.surface, borderWidth: layout.hairlineWidth, borderColor: colors.hairline }}>
              <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: layout.gridGap }}>
                <Text style={[typography.bodyPrimary, { color: colors.ink, fontWeight: '700', flex: 1 }]}>{active.subjectName}</Text>
                <YanjiBadge tone={active.isPaused ? 'amber' : 'brand'} label={active.isPaused ? '已暂停' : '正在专注'} />
              </View>
              <LiveSessionReadout session={active} visible={visible} />
              <YanjiPrimaryButton label="返回专注工作台" icon={active.isPaused ? 'play' : 'focus'} onPress={() => selectTab('focus')} />
            </View>
          ) : pending.length === 0 ? (
            <View style={{ marginTop: YanjiSpacing.md }}>
              <YanjiPrimaryButton label="开始一轮专注" icon="play" onPress={() => selectTab('focus')} />
            </View>
          ) : null}
        </View>
        <YanjiHairline />
        {actionError ? <Text accessibilityRole="alert" style={[typography.bodyPrimary, { color: colors.danger, marginTop: YanjiSpacing.md }]}>{actionError}</Text> : null}

        <YanjiPaperSectionHeading title="今日计划" countText={snapshot ? `${completed.length}/${tasks?.length ?? 0}` : '—'}
          right={<YanjiTextAction label="＋ 新建任务" accessibilityLabel="添加任务" onPress={openTaskEditor} />} />
        {!tasks?.length ? <YanjiEmptyLedgerRow
          prompt={snapshot ? '写下一件想完成的事，也可以直接开始专注…' : '正在读取今日计划…'}
          onPress={openTaskEditor} /> : null}
        {[...pending, ...completed].map((task, index, rows) => (
          <YanjiTaskLedgerRow key={task.id} title={task.title} subjectName={task.subjectName}
            meta={taskMeta(task)} accentColor={task.subjectColor} completed={task.completed}
            isLast={index === rows.length - 1}
            busy={busyTask === task.id} disabled={busyTask !== null}
            onToggle={() => void toggleTask(task)} onEdit={() => setTaskInfo(task)} onStartFocus={() => startTask(task)} />
        ))}

        {todayDoubts.length > 0 ? (
          <View style={{ marginTop: layout.sectionGap }}>
            <YanjiPaperSectionHeading
              title="今日疑点"
              countText={`${resolvedDoubts.length}/${todayDoubts.length}`}
            />
            <YanjiCard variant="default">
              {todayDoubts.map((doubt, index) => (
                <View key={doubt.id}>
                  {index > 0 ? <YanjiHairline style={{ marginVertical: YanjiSpacing.sm }} /> : null}
                  <View style={{ flexDirection: 'row', alignItems: 'flex-start', gap: layout.gridGap }}>
                    <Pressable
                      onPress={() => void toggleResolved(doubt)}
                      disabled={busyNote !== null}
                      accessibilityRole="checkbox"
                      accessibilityState={{ checked: !!doubt.isResolved }}
                      accessibilityLabel={`${doubt.isResolved ? '标记为未解决' : '标记为已解决'}：${noteTitle(doubt)}`}
                      hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
                      style={({ pressed }) => ({
                        minHeight: layout.rowMinHeight,
                        justifyContent: 'center',
                        opacity: pressed ? 0.65 : 1,
                      })}
                    >
                      <YanjiIcon
                        name={doubt.isResolved ? 'doubtResolved' : 'doubtUnresolved'}
                        size={20}
                        color={doubt.isResolved ? colors.positive : colors.secondary}
                      />
                    </Pressable>
                    <Pressable
                      onPress={() => { setEditingNote(doubt); setRecordOpen(true); }}
                      accessibilityRole="button"
                      accessibilityLabel={`编辑疑点：${noteTitle(doubt)}`}
                      style={({ pressed }) => ({
                        flex: 1,
                        minHeight: layout.rowMinHeight,
                        justifyContent: 'center',
                        opacity: pressed ? 0.65 : 1,
                      })}
                    >
                      <Text
                        style={[
                          typography.bodyExcerpt,
                          {
                            color: doubt.isResolved ? colors.secondary : colors.ink,
                            textDecorationLine: doubt.isResolved ? 'line-through' : 'none',
                          },
                        ]}
                        numberOfLines={3}
                      >
                        {doubt.content}
                      </Text>
                      <View style={{ flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.xs, marginTop: 4 }}>
                        <Text style={[typography.captionMeta, { color: colors.secondary }]}>
                          {timeLabel(doubt.timestamp)}
                        </Text>
                        <YanjiBadge
                          tone={doubt.isResolved ? 'positive' : 'brand'}
                          label={doubt.isResolved ? '已解决' : '待解决'}
                        />
                      </View>
                    </Pressable>
                  </View>
                </View>
              ))}
            </YanjiCard>
          </View>
        ) : null}

        {/* 记录此刻：采用回顾页时间线语法，去外层白卡化，无随笔时展示极简紧凑单行插槽 */}
        <View style={{ marginTop: YanjiSpacing.sm }}>
          <YanjiPaperSectionHeading
            title="记录此刻"
            right={
              <YanjiTextAction
                label="＋ 写此刻"
                accessibilityLabel="新建今日记录"
                onPress={newNote}
                tone="quiet"
              />
            }
          />
          {latestNotes.length > 0 ? (
            <View style={{ marginTop: YanjiSpacing.xs }}>
              {latestNotes.map((note, index) => {
                const isFirst = index === 0;
                const isLast = index === latestNotes.length - 1;
                const isDoubt = note.category === 'DOUBT';
                const isResolved = !!note.isResolved;
                const nodeColor = isDoubt
                  ? (isResolved ? colors.positive : colors.brand)
                  : colors.brand;
                const dotCenterY = layout.timelineNodeSize / 2 + 2;
                const nodeRadius = layout.timelineNodeSize / 2;
                const dotGap = YanjiSpacing.xs;
                const clearance = nodeRadius + dotGap;
                const topSegmentHeight = dotCenterY - clearance;

                return (
                  <View key={note.id} style={{ flexDirection: 'row', marginHorizontal: -layout.timelineEdgeBleed }}>
                    <View style={{
                      width: layout.timelineTimeColumn,
                      height: 16,
                      justifyContent: 'center',
                      marginTop: Math.max(0, dotCenterY - 8),
                    }}>
                      <Text style={[typography.captionMeta, { color: colors.secondary, textAlign: 'right' }]}>
                        {timeLabel(note.timestamp)}
                      </Text>
                    </View>
                    <View style={{ width: layout.timelineAxisColumn, alignItems: 'center' }}>
                      {!isFirst && topSegmentHeight > 0 ? (
                        <View style={{
                          position: 'absolute', top: 0, height: topSegmentHeight,
                          width: layout.hairlineWidth, backgroundColor: colors.hairline,
                        }} />
                      ) : null}
                      <View style={{
                        position: 'absolute', top: dotCenterY - nodeRadius,
                        width: layout.timelineNodeSize, height: layout.timelineNodeSize,
                        borderRadius: nodeRadius, backgroundColor: nodeColor,
                      }} />
                      {!isLast ? (
                        <View style={{
                          position: 'absolute', top: dotCenterY + clearance, bottom: 0,
                          width: layout.hairlineWidth, backgroundColor: colors.hairline,
                        }} />
                      ) : null}
                    </View>
                    <View style={{ flex: 1, paddingBottom: isLast ? 0 : YanjiSpacing.sm, minHeight: 56 }}>
                      <Pressable
                        onPress={() => { setEditingNote(note); setRecordOpen(true); }}
                        accessibilityRole="button" accessibilityLabel={`编辑记录：${noteTitle(note)}`}
                        style={({ pressed }) => ({
                          backgroundColor: colors.elevated, borderWidth: layout.hairlineWidth, borderColor: colors.hairline,
                          borderLeftWidth: 3, borderLeftColor: nodeColor,
                          borderRadius: radius.tag,
                          paddingVertical: YanjiSpacing.sm, paddingHorizontal: YanjiSpacing.md,
                          opacity: pressed ? 0.7 : 1,
                        })}>
                        <View style={{ height: layout.timelineHeadBand, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', gap: layout.gridGap }}>
                          <View style={{ flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.xs, flex: 1 }}>
                            <Text style={[typography.captionMeta, { color: colors.brandInk, fontWeight: '700' }]}>
                              记录此刻
                            </Text>
                            {isDoubt ? (
                              <Pressable
                                onPress={() => void toggleResolved(note)}
                                disabled={busyNote !== null}
                                accessibilityRole="button"
                                accessibilityLabel={isResolved ? '疑点已解决，点击标记为待解决' : '疑点待解决，点击标记为已解决'}
                                hitSlop={{ top: 8, bottom: 8, left: 6, right: 6 }}
                                style={({ pressed }) => ({ opacity: pressed ? 0.7 : 1 })}>
                                <YanjiBadge tone={isResolved ? 'positive' : 'brand'} label={isResolved ? '疑点 · 已解决' : '疑点 · 待解决'} />
                              </Pressable>
                            ) : (
                              <YanjiBadge tone="neutral" label="灵感" />
                            )}
                            {note.isFavorite ? (
                              <Text style={[typography.captionMeta, { color: colors.secondary }]}>· 已收藏</Text>
                            ) : null}
                          </View>
                          <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                            <YanjiIconButton
                              icon="compose"
                              accessibilityLabel={`编辑记录：${noteTitle(note)}`}
                              onPress={() => { setEditingNote(note); setRecordOpen(true); }}
                              size={layout.timelineHeadBand}
                              iconSize={ENTRY_ICON_SIZE}
                            />
                            <YanjiIconButton
                              icon={note.isFavorite ? 'star' : 'starOutline'}
                              accessibilityLabel={`${note.isFavorite ? '取消收藏' : '收藏'}记录：${noteTitle(note)}`}
                              onPress={() => void toggleFavorite(note)}
                              disabled={busyNote !== null}
                              selected={note.isFavorite}
                              size={layout.timelineHeadBand}
                              iconSize={ENTRY_ICON_SIZE}
                            />
                          </View>
                        </View>
                        <Text style={[
                          typography.bodyExcerpt,
                          {
                            color: isDoubt && isResolved ? colors.secondary : colors.ink,
                            textDecorationLine: isDoubt && isResolved ? 'line-through' : 'none',
                          },
                        ]} numberOfLines={4}>“{note.content}”</Text>
                      </Pressable>
                    </View>
                  </View>
                );
              })}
              {(notes?.length ?? 0) > 2 ? (
                <View style={{ marginTop: YanjiSpacing.xs, alignItems: 'flex-end' }}>
                  <YanjiTextAction label={`前往回顾查看全部 ${notes?.length} 篇记录 ›`} onPress={() => selectTab('review')} />
                </View>
              ) : null}
            </View>
          ) : (
            <Pressable
              onPress={newNote}
              accessibilityRole="button"
              accessibilityLabel="写下今日随笔"
              style={({ pressed }) => ({
                minHeight: layout.rowMinHeight,
                flexDirection: 'row',
                alignItems: 'center',
                gap: YanjiSpacing.sm,
                backgroundColor: colors.elevated,
                borderWidth: layout.hairlineWidth,
                borderColor: colors.hairline,
                borderStyle: 'dashed',
                borderRadius: radius.tag,
                paddingHorizontal: YanjiSpacing.md,
                paddingVertical: YanjiSpacing.sm,
                marginTop: YanjiSpacing.xs,
                opacity: pressed ? 0.65 : 1,
              })}>
              <View style={{
                width: layout.timelineNodeSize,
                height: layout.timelineNodeSize,
                borderRadius: layout.timelineNodeSize / 2,
                borderWidth: layout.hairlineWidth * 1.5,
                borderColor: colors.secondary,
              }} />
              <Text style={[typography.captionMeta, { color: colors.secondary, flex: 1 }]}>
                {snapshot ? '留一句想法，让今天有自己的注脚…' : '正在读取今日随笔…'}
              </Text>
              <Text style={[typography.captionMeta, { color: colors.brandInk, fontWeight: '600' }]}>
                点击速记 ›
              </Text>
            </Pressable>
          )}
        </View>

        {/* 今日学习节奏.
            Collapsed, this used to be a caption, a row of 18 fixed-width ticks
            and a chevron — a title with no subject, and a strip that read as a
            pagination indicator. The section now shows the day's shape on a
            labelled clock and states it in one sentence, so the collapsed state
            already answers "what is this?" without repeating the 06:00–24:00
            range that the axis labels already carry. Expanding is a named
            control, not just an arrow. */}
        {/* `marginTop: sm` plus the heading's own 16dp keeps the 24dp section
            gap this page already used, so adding a real heading does not also
            add air. */}
        <View style={{ marginTop: YanjiSpacing.sm }}>
          <YanjiPaperSectionHeading
            title="今日学习节奏"
            right={<Pressable onPress={() => setRhythmExpanded(expanded => !expanded)}
              accessibilityRole="button"
              accessibilityLabel={rhythmToggleLabel}
              accessibilityState={{ expanded: rhythmExpanded }}
              accessibilityHint={rhythmExpanded ? '收起学科与时段详情' : '展开学科与时段详情'}
              hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
              style={({ pressed }) => ({ minHeight: layout.rowMinHeight, flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.xs, opacity: pressed ? 0.65 : 1 })}>
              <Text style={[typography.captionMeta, { color: colors.secondary }]}>
                {rhythmExpanded ? '收起详情' : '展开详情'}
              </Text>
              <YanjiIcon name={rhythmExpanded ? 'chevronUp' : 'chevronDown'} size={typography.captionMeta.fontSize + 3} color={colors.secondary} />
            </Pressable>} />
          <YanjiDayRhythmChart buckets={rhythmBuckets} currentHour={now.getHours()}
            startLabel="06:00" endLabel="24:00"
            accessibilityLabel={`今日学习节奏，时间范围 06:00 至 24:00。${rhythmSummaryText}`} />
          <Text style={[typography.captionMeta, { color: colors.secondary, marginTop: YanjiSpacing.sm }]}>
            {rhythmSummaryText}
          </Text>
          {rhythmExpanded ? (
            <View style={{ marginTop: YanjiSpacing.md }}>
              <YanjiPaperSegmentedControl value={chart} options={CHART_MODES} accessibilityLabel="今日节奏视图"
                onChange={mode => { setChart(mode); setSelection(null); }} />
              {chart === 'subjects' ? (
                <View style={{ marginTop: YanjiSpacing.md }}>
                  {subjects.map(subject => <YanjiSubjectProportionRow key={subject.id} label={subject.label} color={subject.color}
                    metaText={`${duration(subject.seconds)} · ${chartTotal ? Math.round(subject.seconds / chartTotal * 100) : 0}%`}
                    ratio={chartTotal > 0 ? subject.seconds / chartTotal : 0} selected={selection?.subjectId === subject.id}
                    onPress={() => selectSegment({ key: `subject:${subject.id}`, subjectId: subject.id, subjectName: subject.label, seconds: subject.seconds })} />)}
                  {!subjects.length ? <Text style={[typography.captionMeta, { color: colors.secondary, paddingVertical: YanjiSpacing.md }]}>
                    {snapshot ? '还没有已保存的学科投入。' : '正在读取学科记录…'}
                  </Text> : null}
                </View>
              ) : (
                <View style={{ marginTop: YanjiSpacing.md }}>
                  {DAY_PARTS.map(part => {
                    const startDate = new Date(`${date}T00:00:00`);
                    startDate.setHours(part.from);
                    const endDate = new Date(`${date}T00:00:00`);
                    endDate.setHours(part.to);
                    const start = startDate.getTime();
                    const end = endDate.getTime();
                    const segments = sessions?.filter(session => sessionPrecise(session) && session.endTime > start && session.startTime < end) ?? [];
                    return <View key={part.name} style={{ marginBottom: YanjiSpacing.md }}>
                      <View style={{ flexDirection: 'row', justifyContent: 'space-between', flexWrap: 'wrap', gap: layout.gridGap, marginBottom: YanjiSpacing.xs }}>
                        <Text style={[typography.captionMeta, { color: colors.ink }]}>{part.name}</Text>
                        <Text style={[typography.captionMeta, { color: colors.secondary }]}>{String(part.from).padStart(2, '0')}:00—{String(part.to).padStart(2, '0')}:00</Text>
                      </View>
                      <View accessible accessibilityLabel={`${part.name}，${segments.length} 段可确认的连续专注`}
                        style={{ height: layout.spectrumHeight, backgroundColor: colors.subtle, borderRadius: radius.tag, overflow: 'hidden' }}>
                        {segments.map(session => <View key={session.id} style={{ position: 'absolute', top: 0, bottom: 0,
                          left: `${Math.max(0, session.startTime - start) / (end - start) * 100}%`,
                          width: `${(Math.min(end, session.endTime) - Math.max(start, session.startTime)) / (end - start) * 100}%`,
                          backgroundColor: session.subjectColor || colors.secondary }} />)}
                      </View>
                    </View>;
                  })}
                  {sessions?.map(session => <Pressable key={session.id}
                    onPress={() => selectSegment({ key: `session:${session.id}`, subjectId: session.subjectId, subjectName: session.subjectName, seconds: session.durationSeconds })}
                    accessibilityRole="button" accessibilityLabel={`${session.subjectName}，本次${duration(session.durationSeconds)}`}
                    style={({ pressed }) => ({ minHeight: layout.rowMinHeight, justifyContent: 'center', borderBottomWidth: layout.hairlineWidth, borderColor: colors.hairline, opacity: pressed ? 0.65 : 1 })}>
                    <Text style={[typography.captionMeta, { color: colors.secondary }]}>
                      {timeLabel(session.startTime)} 起 · {session.subjectName} · {duration(session.durationSeconds)}{sessionPrecise(session) ? '' : ' · 未绘连续区段'}
                    </Text>
                  </Pressable>)}
                </View>
              )}
              <Text style={[typography.captionMeta, { color: colors.secondary, marginTop: YanjiSpacing.sm }]}>
                柱高按小时汇总可确认的连续专注，以当小时投入最多的学科着色。暂停与起止不一致的记录仍计入累计时长；06:00 前的记录可在专注时段查看。
              </Text>
              {selection?.key.startsWith('session:') ? <Text accessibilityLiveRegion="polite" style={[typography.bodyPrimary, { color: colors.secondary, marginTop: YanjiSpacing.sm }]}>
                {selection.subjectName} · {duration(selection.seconds)}
              </Text> : null}
            </View>
          ) : null}
        </View>
      </ScrollView>

      <RecordMomentModal visible={recordOpen} date={date} editingNote={editingNote} onClose={() => { setRecordOpen(false); setEditingNote(null); }} />
      <TaskEditorModal visible={taskEditorOpen || !!taskInfo} date={date} editingTask={taskInfo} onClose={() => { setTaskInfo(null); closeTaskEditor(); }} />
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
  return <View style={{ marginVertical: YanjiSpacing.sm }}>
    <Text style={[paper.typography.metricSub, { color: paper.colors.ink }]}>{clock(seconds)}</Text>
    <Text style={[paper.typography.captionMeta, { color: paper.colors.secondary }]}>{isPaused ? '专注已暂停' : session.isCountdown ? '本轮剩余时长' : '本轮已持续时长'} · 尚未计入已保存总量</Text>
  </View>;
}
