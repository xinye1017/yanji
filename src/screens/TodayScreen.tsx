/** Paper & Chrono: saved facts come from Room; live ticks stay in their leaf. */
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Modal, Pressable, RefreshControl, ScrollView, Text, View } from 'react-native';
import { useReducedMotion } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { YanjiDataNative } from '../bridge';
import type { ActiveSessionState, DailySession, NoteEntry, StudyTask } from '../bridge';
import {
  YanjiChronoSpectrum, YanjiEditorialHeader, YanjiHairline, YanjiIcon,
  YanjiIconButton, YanjiPaperSectionHeading, YanjiPaperSegmentedControl,
  YanjiPaperSheetHeading, YanjiPrimaryButton,
} from '../components/YanjiUI';
import type { YanjiSpectrumSegment } from '../components/YanjiUI';
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

type ChartMode = 'subjects' | 'timeline';
type Selection = { key: string; subjectId: string; subjectName: string; seconds: number } | null;
const DAY_PARTS = [
  { name: '凌晨', from: 0, to: 6 }, { name: '上午', from: 6, to: 12 },
  { name: '下午', from: 12, to: 18 }, { name: '晚上', from: 18, to: 24 },
] as const;
const CHART_MODES = [
  { value: 'subjects', label: '学科投入' }, { value: 'timeline', label: '专注时段' },
] as const;

function localDay(d: Date): string {
  return [d.getFullYear(), String(d.getMonth() + 1).padStart(2, '0'), String(d.getDate()).padStart(2, '0')].join('-');
}
function greeting(hour: number): string {
  if (hour < 5) return '夜深了';
  if (hour < 11) return '早上好，新的一天';
  if (hour < 14) return '中午好';
  if (hour < 18) return '下午好';
  return '晚上好';
}
function duration(seconds: number): string {
  const mins = Math.max(0, Math.floor(seconds / 60));
  if (mins < 1 && seconds > 0) return '不足1分钟';
  if (mins < 60) return `${mins}分钟`;
  return `${Math.floor(mins / 60)}小时${mins % 60 ? `${mins % 60}分` : ''}`;
}
function clock(seconds: number): string {
  const whole = Math.max(0, Math.floor(seconds));
  const h = Math.floor(whole / 3600);
  const m = String(Math.floor((whole % 3600) / 60)).padStart(2, '0');
  const s = String(whole % 60).padStart(2, '0');
  return h ? `${h}:${m}:${s}` : `${m}:${s}`;
}
function displayDate(date: string): string {
  const d = new Date(`${date}T12:00:00`);
  return `${d.getMonth() + 1}月${d.getDate()}日 · 星期${'日一二三四五六'[d.getDay()]}`;
}
function noteTitle(note: NoteEntry): string {
  return note.content.trim().split('\n').find(line => line.trim())?.trim() || '无标题记录';
}
function sessionPrecise(s: DailySession): boolean {
  // Pause intervals are not exposed. Only verified uninterrupted spans have positions.
  return s.pauseCount === 0 && s.endTime > s.startTime
    && Math.abs((s.endTime - s.startTime) / 1000 - s.durationSeconds) <= 2;
}

export function TodayScreen({ visible, foregroundRevision }: {
  visible: boolean; foregroundRevision: number;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const { paper } = theme;
  const { colors, typography, layout, radius } = paper;
  const { contentPadding } = useBottomTabLayout();
  const insets = useSafeAreaInsets();
  const reduceMotion = useReducedMotion();
  const { openSettings, openTaskEditor, closeTaskEditor, selectTab, requestFocusPreset } = useNavigationActions();
  const { taskEditorOpen } = useNavigationState();
  const [now, setNow] = useState(() => new Date());
  const date = localDay(now);
  const daily = useDailySnapshot('today', date, visible);
  const snapshot = daily?.date === date ? daily : null;
  const settingsSnapshot = useSettingsSnapshot('today', visible);
  const settings = settingsSnapshot?.settings ?? null;
  const countdown = settingsSnapshot?.examCountdown ?? null;
  const tasks = snapshot?.tasks ?? [];
  const notes = snapshot?.notes ?? [];
  const sessions = snapshot?.sessions ?? [];
  const active = useActiveSession(visible);
  const [chart, setChart] = useState<ChartMode>('subjects');
  const [selection, setSelection] = useState<Selection>(null);
  const [recordOpen, setRecordOpen] = useState(false);
  const [editingNote, setEditingNote] = useState<NoteEntry | null>(null);
  const [taskInfo, setTaskInfo] = useState<StudyTask | null>(null);
  const [goalOpen, setGoalOpen] = useState(false);
  const [goalDraft, setGoalDraft] = useState(0);
  const [goalError, setGoalError] = useState<string | null>(null);
  const [savingGoal, setSavingGoal] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
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

  const handleRefresh = useCallback(async () => {
    setRefreshing(true);
    setActionError(null);
    try {
      await Promise.all([refreshDailySnapshot(date), refreshSettingsSnapshot(), syncActiveSession()]);
    } catch { setActionError('刷新失败，请下拉或点击重试。'); }
    finally { setRefreshing(false); }
  }, [date]);
  const effectiveSeconds = snapshot?.totalDurationSeconds ?? 0;
  const pending = tasks.filter(task => !task.completed);
  const completed = tasks.filter(task => task.completed);
  const latestNotes = useMemo(() => [...notes].sort((a, b) => b.timestamp - a.timestamp).slice(0, 2), [notes]);
  const goalSeconds = settings ? (settings.dailyGoalHours ?? 0) * 3600 : null;
  const subjects = useMemo(() => {
    const map = new Map<string, YanjiSpectrumSegment>();
    for (const session of sessions) {
      if (session.durationSeconds <= 0) continue;
      const previous = map.get(session.subjectId);
      if (previous) previous.seconds += session.durationSeconds;
      else map.set(session.subjectId, { id: session.subjectId, label: session.subjectName,
        seconds: session.durationSeconds, color: session.subjectColor || colors.secondary });
    }
    return [...map.values()].sort((a, b) => b.seconds - a.seconds || a.id.localeCompare(b.id));
  }, [sessions, colors.secondary]);
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
  const openGoal = () => {
    if (!settings) { openSettings(); return; }
    setGoalDraft(Math.max(0, Math.round((settings.dailyGoalHours ?? 0) * 60)));
    setGoalError(null);
    setGoalOpen(true);
  };
  const saveGoal = async () => {
    setSavingGoal(true);
    setGoalError(null);
    try {
      if (!await YanjiDataNative.updateUserSettings({ dailyGoalHours: goalDraft / 60 })) throw new Error('refused');
      await refreshSettingsSnapshot();
      setGoalOpen(false);
    } catch { setGoalError('保存失败，请稍后再试'); }
    finally { setSavingGoal(false); }
  };
  const newNote = () => { setEditingNote(null); setRecordOpen(true); };

  return (
    <View style={{ flex: 1, backgroundColor: colors.canvas }}>
      <ScrollView testID="scroll-today" style={{ flex: 1 }}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => void handleRefresh()} tintColor={colors.brandInk} />}
        contentContainerStyle={{ paddingHorizontal: layout.pageGutter, paddingTop: YanjiSpacing.lg, paddingBottom: contentPadding }}>
        <YanjiEditorialHeader eyebrow={displayDate(date)} title={greeting(now.getHours())}
          trailing={<YanjiIconButton icon="settings" onPress={openSettings} accessibilityLabel="设置与目标" size={layout.rowMinHeight} />} />
        {countdown?.examDate ? (
          <Pressable onPress={openSettings} accessibilityRole="button"
            accessibilityLabel={`考试日期 ${countdown.examDate}，${countdown.daysRemaining >= 0 ? `距初试${countdown.daysRemaining}天` : '考试日期已过'}`}
            style={({ pressed }) => ({ minHeight: layout.rowMinHeight, justifyContent: 'center', opacity: pressed ? 0.65 : 1 })}>
            <Text style={[typography.captionMeta, { color: colors.brandInk }]}>
              {countdown.daysRemaining > 0 ? `距初试还有 ${countdown.daysRemaining} 天` : countdown.daysRemaining === 0 ? '今天是初试日' : '已过设定的考试日期'} · {countdown.examDate}
            </Text>
          </Pressable>
        ) : null}

        <View style={{ marginTop: layout.sectionGap, marginBottom: YanjiSpacing.lg }}>
          <Text style={[typography.captionMeta, { color: colors.secondary }]}>今日累计专注 · 已保存</Text>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', alignItems: 'baseline', gap: layout.gridGap, marginTop: YanjiSpacing.sm }}>
            {snapshot ? <>
              <Text style={[typography.metricHero, { color: colors.ink }]}>{String(Math.floor(effectiveSeconds / 3600)).padStart(2, '0')}<Text style={typography.bodyPrimary}> h</Text></Text>
              <Text style={[typography.metricHero, { color: colors.ink }]}>{String(Math.floor(effectiveSeconds % 3600 / 60)).padStart(2, '0')}<Text style={typography.bodyPrimary}> m</Text></Text>
            </> : <Text style={[typography.metricHero, { color: colors.ink }]}>—</Text>}
          </View>
          <Pressable onPress={openGoal} accessibilityRole="button" accessibilityLabel="调整日常学习目标"
            style={({ pressed }) => ({ minHeight: layout.rowMinHeight, justifyContent: 'center', opacity: pressed ? 0.65 : 1 })}>
            <Text style={[typography.captionMeta, { color: colors.secondary }]}>
              {goalSeconds === null ? '设置与学习目标' : goalSeconds > 0 ? `日常目标 ${duration(goalSeconds)}${snapshot ? ` · ${Math.round(effectiveSeconds / goalSeconds * 100)}%` : ''}` : '设置日常学习目标'} ›
            </Text>
          </Pressable>
          <YanjiChronoSpectrum segments={subjects} totalSeconds={effectiveSeconds} goalSeconds={snapshot ? goalSeconds : null}
            accessibilityLabel={snapshot ? undefined : '正在读取今日专注'} />
          <Text style={[typography.captionMeta, { color: colors.secondary, marginTop: YanjiSpacing.sm }]}>
            {!snapshot ? '正在读取今日记录，可下拉重试。' : effectiveSeconds > 0
              ? `${snapshot.focusCount} 段专注 · ${snapshot.examCount} 场模考${goalSeconds && effectiveSeconds > goalSeconds ? ` · 超出目标 ${duration(effectiveSeconds - goalSeconds)}` : ''}`
              : active ? '当前这一轮尚未结算，会保留在专注工作台。' : '今天的纸页还很安静，按自己的步调开始。'}
          </Text>
          {!snapshot ? <Pressable onPress={() => void handleRefresh()} disabled={refreshing} accessibilityRole="button"
            style={{ minHeight: layout.rowMinHeight, justifyContent: 'center' }}><Text style={[typography.bodyPrimary, { color: colors.brandInk }]}>{refreshing ? '读取中…' : '重新读取'}</Text></Pressable> : null}
        </View>

        {active ? (
          <View style={{ padding: YanjiSpacing.lg, backgroundColor: colors.subtle, borderRadius: radius.surface, marginBottom: YanjiSpacing.md }}>
            <Text style={[typography.bodyPrimary, { color: colors.ink }]}>{active.subjectName} · {active.isPaused ? '已暂停' : '正在专注'}</Text>
            <LiveSessionReadout session={active} visible={visible} />
            <YanjiPrimaryButton label="返回专注工作台" icon={active.isPaused ? 'play' : 'focus'} onPress={() => selectTab('focus')} />
          </View>
        ) : <YanjiPrimaryButton label={pending.length ? '开始首项待办' : '开始一轮专注'} icon="play"
          onPress={() => pending.length ? startTask(pending[0]) : selectTab('focus')} />}
        {actionError ? <Text accessibilityRole="alert" style={[typography.bodyPrimary, { color: colors.brandInk, marginTop: YanjiSpacing.md }]}>{actionError}</Text> : null}

        <YanjiPaperSectionHeading title="今日计划" right={<Pressable onPress={openTaskEditor} accessibilityRole="button" accessibilityLabel="添加任务"
          style={({ pressed }) => ({ minHeight: layout.rowMinHeight, minWidth: layout.rowMinHeight, justifyContent: 'center', opacity: pressed ? 0.65 : 1 })}>
          <Text style={[typography.bodyPrimary, { color: colors.brandInk }]}>＋ 立一项任务</Text>
        </Pressable>} />
        <YanjiHairline />
        <Text style={[typography.captionMeta, { color: colors.secondary, marginVertical: YanjiSpacing.sm }]}>待办 {pending.length} · 已完成 {completed.length}</Text>
        {!tasks.length ? <Text style={[typography.bodyJournal, { color: colors.secondary, paddingVertical: YanjiSpacing.md }]}>
          {snapshot ? '写下一件想完成的事，也可以直接开始专注。' : '正在读取今日计划…'}
        </Text> : null}
        {[pending, completed].map((group, index) => group.length ? <View key={index}>
          {index === 1 ? <Text style={[typography.captionMeta, { color: colors.secondary, marginTop: YanjiSpacing.lg }]}>已完成</Text> : null}
          {group.map(task => <View key={task.id}>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: layout.gridGap, paddingVertical: YanjiSpacing.sm }}>
              <Pressable onPress={() => void toggleTask(task)} disabled={busyTask !== null} accessibilityRole="checkbox"
                accessibilityState={{ checked: task.completed, disabled: busyTask !== null, busy: busyTask === task.id }}
                accessibilityLabel={`${task.completed ? '恢复待办' : '完成任务'}：${task.title}`}
                style={({ pressed }) => ({ width: layout.rowMinHeight, minHeight: layout.rowMinHeight, alignItems: 'center', justifyContent: 'center', opacity: pressed ? 0.6 : 1 })}>
                <View style={{ width: 20, height: 20, borderWidth: 1, borderColor: colors.secondary, borderRadius: radius.tag, justifyContent: 'center', alignItems: 'center' }}>
                  {task.completed ? <YanjiIcon name="check" size={14} color={colors.ink} /> : null}
                </View>
              </Pressable>
              <Pressable onPress={() => setTaskInfo(task)} accessibilityRole="button" accessibilityLabel={`编辑任务：${task.title}`}
                style={({ pressed }) => ({ flex: 1, minHeight: layout.rowMinHeight, justifyContent: 'center', opacity: pressed ? 0.65 : 1 })}>
                <Text style={[typography.bodyPrimary, { color: task.completed ? colors.secondary : colors.ink, textDecorationLine: task.completed ? 'line-through' : 'none' }]}>{task.title}</Text>
                <Text style={[typography.captionMeta, { color: colors.secondary, marginTop: YanjiSpacing.xs }]}>{task.subjectName} · 预计 {task.plannedMinutes} 分钟 · 累计投入 {task.actualMinutes} 分钟</Text>
              </Pressable>
              <YanjiIconButton icon="play" onPress={() => startTask(task)} accessibilityLabel={`为${task.title}设置专注`} size={layout.rowMinHeight} />
            </View>
            <YanjiHairline />
          </View>)}
        </View> : null)}

        <YanjiPaperSectionHeading title="今日节奏" />
        <YanjiPaperSegmentedControl value={chart} options={CHART_MODES} accessibilityLabel="今日节奏视图"
          onChange={mode => { setChart(mode); setSelection(null); }} />
        {chart === 'subjects' ? (
          <View style={{ marginTop: YanjiSpacing.md }}>
            {subjects.map(subject => <Pressable key={subject.id}
              onPress={() => selectSegment({ key: `subject:${subject.id}`, subjectId: subject.id, subjectName: subject.label, seconds: subject.seconds })}
              accessibilityRole="button" accessibilityState={{ selected: selection?.subjectId === subject.id }}
              style={({ pressed }) => ({ minHeight: layout.rowMinHeight, flexDirection: 'row', alignItems: 'center', gap: layout.gridGap, opacity: pressed ? 0.65 : 1 })}>
              <View style={{ width: 8, height: 8, borderRadius: radius.tag, backgroundColor: subject.color }} />
              <Text style={[typography.bodyPrimary, { color: colors.ink, flex: 1 }]}>{subject.label}</Text>
              <Text style={[typography.captionMeta, { color: colors.secondary, flexShrink: 1 }]}>{duration(subject.seconds)} · {chartTotal ? Math.round(subject.seconds / chartTotal * 100) : 0}%</Text>
            </Pressable>)}
            {!subjects.length ? <Text style={[typography.captionMeta, { color: colors.secondary, paddingVertical: YanjiSpacing.md }]}>{snapshot ? '还没有已保存的学科投入。' : '正在读取学科记录…'}</Text> : null}
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
              const segments = sessions.filter(s => sessionPrecise(s) && s.endTime > start && s.startTime < end);
              return <View key={part.name} style={{ marginBottom: YanjiSpacing.md }}>
                <Text style={[typography.captionMeta, { color: colors.secondary, marginBottom: YanjiSpacing.xs }]}>{part.name} · {String(part.from).padStart(2, '0')}:00—{String(part.to).padStart(2, '0')}:00</Text>
                <View accessible accessibilityLabel={`${part.name}，${segments.length} 段可确认的连续专注`} style={{ height: layout.spectrumHeight, backgroundColor: colors.subtle, borderRadius: radius.tag, overflow: 'hidden' }}>
                  {segments.map(s => <View key={s.id} style={{ position: 'absolute', top: 0, bottom: 0,
                    left: `${Math.max(0, s.startTime - start) / (end - start) * 100}%`,
                    width: `${(Math.min(end, s.endTime) - Math.max(start, s.startTime)) / (end - start) * 100}%`, backgroundColor: s.subjectColor || colors.secondary }} />)}
                </View>
              </View>;
            })}
            {sessions.map(s => <Pressable key={s.id} onPress={() => selectSegment({ key: `session:${s.id}`, subjectId: s.subjectId, subjectName: s.subjectName, seconds: s.durationSeconds })}
              accessibilityRole="button" accessibilityLabel={`${s.subjectName}，本次${duration(s.durationSeconds)}`}
              style={({ pressed }) => ({ minHeight: layout.rowMinHeight, justifyContent: 'center', opacity: pressed ? 0.65 : 1 })}>
              <Text style={[typography.captionMeta, { color: colors.secondary }]}>{new Date(s.startTime).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })} 起 · {s.subjectName} · {duration(s.durationSeconds)}{sessionPrecise(s) ? '' : ' · 未绘连续区段'}</Text>
            </Pressable>)}
            <Text style={[typography.captionMeta, { color: colors.secondary }]}>仅绘制可确认的连续专注。暂停与起止不一致的记录仍计入累计时长。</Text>
          </View>
        )}
        {selection ? <Text style={[typography.bodyPrimary, { color: colors.ink, marginTop: YanjiSpacing.sm }]}>{selection.subjectName} · {duration(selection.seconds)}</Text> : null}

        <YanjiPaperSectionHeading title="记录此刻" right={<Pressable onPress={newNote} accessibilityRole="button" accessibilityLabel="新建今日记录"
          style={({ pressed }) => ({ minHeight: layout.rowMinHeight, minWidth: layout.rowMinHeight, justifyContent: 'center', opacity: pressed ? 0.65 : 1 })}>
          <Text style={[typography.bodyPrimary, { color: colors.brandInk }]}>＋ 写此刻</Text>
        </Pressable>} />
        <View style={{ backgroundColor: colors.surface, borderRadius: radius.surface, padding: YanjiSpacing.lg }}>
          {latestNotes.length ? latestNotes.map((note, index) => <View key={note.id}>
            {index > 0 ? <YanjiHairline style={{ marginVertical: YanjiSpacing.md }} /> : null}
            <Pressable onPress={() => { setEditingNote(note); setRecordOpen(true); }} accessibilityRole="button" accessibilityLabel={`编辑记录：${noteTitle(note)}`}
              style={({ pressed }) => ({ minHeight: layout.rowMinHeight, justifyContent: 'center', opacity: pressed ? 0.65 : 1 })}>
              <Text style={[typography.bodyJournal, { color: colors.ink }]} numberOfLines={4}>{note.content}</Text>
            </Pressable>
            <View style={{ flexDirection: 'row', alignItems: 'center' }}>
              <Text style={[typography.captionMeta, { color: colors.secondary, flex: 1 }]}>{new Date(note.timestamp).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })}</Text>
              <YanjiIconButton icon={note.isFavorite ? 'star' : 'starOutline'} onPress={() => void toggleFavorite(note)} selected={note.isFavorite} disabled={busyNote !== null}
                accessibilityLabel={`${note.isFavorite ? '取消收藏' : '收藏'}记录：${noteTitle(note)}`} size={layout.rowMinHeight} />
            </View>
          </View>) : <Pressable onPress={newNote} accessibilityRole="button" style={{ minHeight: layout.rowMinHeight, justifyContent: 'center' }}>
            <Text style={[typography.bodyJournal, { color: colors.secondary }]}>{snapshot ? '留一句想法，让今天有自己的注脚。' : '正在读取今日随笔…'}</Text>
          </Pressable>}
        </View>
        {notes.length > 2 ? <Pressable onPress={() => selectTab('review')} accessibilityRole="button" style={{ minHeight: layout.rowMinHeight, justifyContent: 'center' }}>
          <Text style={[typography.captionMeta, { color: colors.brandInk }]}>前往回顾查看全部 {notes.length} 篇记录 ›</Text>
        </Pressable> : null}
      </ScrollView>

      <RecordMomentModal visible={recordOpen} date={date} editingNote={editingNote} onClose={() => { setRecordOpen(false); setEditingNote(null); }} />
      <TaskEditorModal visible={taskEditorOpen || !!taskInfo} date={date} editingTask={taskInfo} onClose={() => { setTaskInfo(null); closeTaskEditor(); }} />
      <Modal visible={goalOpen} transparent animationType={reduceMotion ? 'none' : 'slide'} onRequestClose={() => { if (!savingGoal) setGoalOpen(false); }}>
        <View style={{ flex: 1, backgroundColor: theme.colors.scrim, justifyContent: 'flex-end' }}>
          <ScrollView contentContainerStyle={{ padding: layout.pageGutter, paddingBottom: insets.bottom + YanjiSpacing.lg }}
            style={{ flexGrow: 0, backgroundColor: colors.surface, borderTopLeftRadius: radius.sheet, borderTopRightRadius: radius.sheet }}>
            <YanjiPaperSheetHeading title="日常学习目标" subtitle="调整默认目标，不更改已记录的学习时长。" onClose={() => { if (!savingGoal) setGoalOpen(false); }} />
            <Text style={[typography.metricHero, { color: colors.ink, marginVertical: YanjiSpacing.lg, textAlign: 'center' }]}>{goalDraft ? duration(goalDraft * 60) : '未设置'}</Text>
            <View style={{ flexDirection: 'row', justifyContent: 'center', alignItems: 'center', gap: layout.gridGap }}>
              <YanjiIconButton icon="remove" onPress={() => setGoalDraft(Math.max(0, goalDraft - 30))} disabled={savingGoal || goalDraft <= 0} accessibilityLabel="减少30分钟" size={layout.rowMinHeight} tone="filled" />
              <Text style={[typography.bodyPrimary, { color: colors.secondary, flexShrink: 1 }]}>每次调整30分钟</Text>
              <YanjiIconButton icon="add" onPress={() => setGoalDraft(Math.min(720, goalDraft + 30))} disabled={savingGoal || goalDraft >= 720} accessibilityLabel="增加30分钟" size={layout.rowMinHeight} tone="filled" />
            </View>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'center', gap: layout.gridGap, marginVertical: YanjiSpacing.lg }}>
              {[240, 360, 480, 600].map(value => <Pressable key={value} onPress={() => setGoalDraft(value)} disabled={savingGoal} accessibilityRole="button" accessibilityState={{ selected: goalDraft === value, disabled: savingGoal }}
                style={({ pressed }) => ({ minHeight: layout.rowMinHeight, paddingHorizontal: YanjiSpacing.md, justifyContent: 'center', borderRadius: radius.tag, backgroundColor: colors.subtle, opacity: pressed ? 0.65 : 1 })}>
                <Text style={[typography.captionMeta, { color: goalDraft === value ? colors.brandInk : colors.secondary }]}>{duration(value * 60)}</Text>
              </Pressable>)}
            </View>
            {goalError ? <Text accessibilityRole="alert" style={[typography.bodyPrimary, { color: colors.brandInk, marginBottom: YanjiSpacing.md }]}>{goalError}</Text> : null}
            <YanjiPrimaryButton label={savingGoal ? '保存中…' : '保存目标'} onPress={() => void saveGoal()} disabled={savingGoal} />
            <Pressable onPress={() => setGoalOpen(false)} disabled={savingGoal} accessibilityRole="button" style={{ minHeight: layout.rowMinHeight, alignItems: 'center', justifyContent: 'center' }}>
              <Text style={[typography.bodyPrimary, { color: colors.secondary }]}>取消</Text>
            </Pressable>
          </ScrollView>
        </View>
      </Modal>
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
  return <View style={{ marginVertical: YanjiSpacing.md }}>
    <Text style={[paper.typography.metricHero, { color: paper.colors.ink }]}>{clock(seconds)}</Text>
    <Text style={[paper.typography.captionMeta, { color: paper.colors.secondary }]}>{isPaused ? '专注已暂停' : session.isCountdown ? '本轮剩余时长' : '本轮已持续时长'} · 尚未计入已保存总量</Text>
  </View>;
}
