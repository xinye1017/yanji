/**
 * 今日工作台 V2：事实来自 Kotlin/Room；没有已保存专注就不渲染统计或图表。
 * 暂不在 JS 侧推算跨午夜/暂停归属：必须由原生统计接口提供准确区间。
 */
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { AppState, Modal, Pressable, RefreshControl, ScrollView, Text, View } from 'react-native';
import {
  YanjiDataNative, YanjiTimerNative, onDataChanged, onTimerStateChanged, onTimerTick,
} from '../bridge';
import type {
  ActiveSessionState, DailyTimeline, DailyTimelineSession, ExamCountdown,
  NoteEntry, StudyTask, TimerTickEvent, TodayStats, UserSettings,
} from '../bridge';
import { YanjiHairline, YanjiIcon, YanjiIconButton } from '../components/YanjiUI';
import { RecordMomentModal } from '../components/RecordMomentModal';
import { TaskEditorModal } from '../components/TaskEditorModal';
import { useNavigation } from '../navigation/NavigationShell';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { useBottomTabLayout } from '../navigation/useBottomTabLayout';
import { YanjiRadius, YanjiSpacing, YanjiTouch } from '../theme/tokens';

type ChartMode = 'subjects' | 'timeline';
type Selection = { key: string; subjectId: string; subjectName: string; seconds: number } | null;

const SUBJECT_COLORS = ['#396DE3', '#25A69B', '#E5A14B', '#9573CE', '#D86A82'];
const DAY_PARTS = [
  { name: '凌晨', from: 0, to: 6 },
  { name: '上午', from: 6, to: 12 },
  { name: '下午', from: 12, to: 18 },
  { name: '晚上', from: 18, to: 24 },
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
function sessionPrecise(s: DailyTimelineSession): boolean {
  // 现有原生桥接只有 pauseCount，没有暂停区间。不可把暂停画成连续专注。
  return s.pauseCount === 0 && s.endTime > s.startTime
    && Math.abs((s.endTime - s.startTime) / 1000 - s.durationSeconds) <= 2;
}

export function TodayScreen(): React.JSX.Element {
  const theme = useYanjiTheme();
  const { contentPadding } = useBottomTabLayout();
  const { openSettings, openTaskEditor, closeTaskEditor, taskEditorOpen, selectTab, requestFocusPreset } = useNavigation();

  const [now, setNow] = useState(() => new Date());
  const date = localDay(now);
  const [stats, setStats] = useState<TodayStats | null>(null);
  const [timeline, setTimeline] = useState<DailyTimeline | null>(null);
  const [tasks, setTasks] = useState<StudyTask[]>([]);
  const [notes, setNotes] = useState<NoteEntry[]>([]);
  const [countdown, setCountdown] = useState<ExamCountdown | null>(null);
  const [settings, setSettings] = useState<UserSettings | null>(null);
  const [active, setActive] = useState<ActiveSessionState | null>(null);
  const [tick, setTick] = useState<TimerTickEvent | null>(null);
  const [chart, setChart] = useState<ChartMode>('subjects');
  const [selection, setSelection] = useState<Selection>(null);
  const [recordOpen, setRecordOpen] = useState(false);
  const [editingNote, setEditingNote] = useState<NoteEntry | null>(null);
  const [taskInfo, setTaskInfo] = useState<StudyTask | null>(null);
  const [goalOpen, setGoalOpen] = useState(false);
  const [goalDraft, setGoalDraft] = useState(360);
  const [goalError, setGoalError] = useState<string | null>(null);
  const [savingGoal, setSavingGoal] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  const refresh = useCallback(async () => {
    try {
      const [nextStats, nextTasks, nextNotes, exam, nextTimeline, nextSettings] = await Promise.all([
        YanjiDataNative.getTodayStats(date),
        YanjiDataNative.getTodayTasks(date),
        YanjiDataNative.getNotesForDate(date),
        YanjiDataNative.getExamCountdown(),
        YanjiDataNative.getDailyTimeline(date),
        YanjiDataNative.getUserSettings(),
      ]);
      // 防止午夜/请求乱序把昨日数据写回新的「今天」。
      if (localDay(new Date()) !== date) return;
      setStats(nextStats);
      setTasks(nextTasks);
      setNotes(nextNotes);
      setCountdown(exam);
      setTimeline(nextTimeline);
      setSettings(nextSettings);
    } catch {
      // 保留已有快照；失败时绝不填充示例数据。
    }
  }, [date]);

  const refreshSession = useCallback(async () => {
    try {
      setActive(await YanjiTimerNative.getActiveSession());
    } catch {
      setActive(null);
    }
  }, []);

  useEffect(() => {
    setStats(null);
    setTimeline(null);
    setTasks([]);
    setNotes([]);
    setSelection(null);
    void refresh();
    void refreshSession();
  }, [date, refresh, refreshSession]);

  useEffect(() => {
    const subData = onDataChanged(() => void refresh());
    const subState = onTimerStateChanged(event => {
      setActive(event.session);
      setTick(null);
      void refreshSession();
    });
    const subTick = onTimerTick(event => setTick(event));
    const timer = setInterval(() => setNow(new Date()), 30_000); // 只刷新日期/问候；不驱动计时事实。
    const appState = AppState.addEventListener('change', next => {
      if (next === 'active') {
        setNow(new Date());
        void refresh();
        void refreshSession();
      }
    });
    return () => {
      subData(); subState(); subTick(); clearInterval(timer); appState.remove();
    };
  }, [refresh, refreshSession]);

  const handleRefresh = useCallback(async () => {
    setRefreshing(true);
    try { await Promise.all([refresh(), refreshSession()]); }
    finally { setRefreshing(false); }
  }, [refresh, refreshSession]);

  const effectiveSeconds = stats?.date === date ? stats.totalFocusSeconds : 0;
  const hasFocus = effectiveSeconds > 0;
  const pending = tasks.filter(t => !t.completed).slice(0, 3);
  const latestNotes = useMemo(() => [...notes].sort((a,b) => b.timestamp - a.timestamp).slice(0, 2), [notes]);
  const goalSeconds = (settings?.dailyGoalHours ?? 0) * 3600;
  const pct = goalSeconds > 0 ? Math.round(effectiveSeconds / goalSeconds * 100) : 0;

  const subjects = useMemo(() => {
    const map = new Map<string, { id: string; name: string; seconds: number; color: string }>();
    for (const s of timeline?.sessions ?? []) {
      if (s.durationSeconds <= 0) continue;
      const prev = map.get(s.subjectId);
      if (prev) prev.seconds += s.durationSeconds;
      else map.set(s.subjectId, { id: s.subjectId, name: s.subjectName, seconds: s.durationSeconds,
        color: SUBJECT_COLORS[map.size % SUBJECT_COLORS.length] });
    }
    return [...map.values()].sort((a,b) => b.seconds - a.seconds);
  }, [timeline]);
  const chartTotal = subjects.reduce((sum, sub) => sum + sub.seconds, 0);
  const selectedSubject = subjects.find(s => s.id === selection?.subjectId);
  const selectSegment = (next: NonNullable<Selection>) =>
    setSelection(current => current?.key === next.key ? null : next);

  const startTask = (task: StudyTask) => requestFocusPreset({
    taskId: task.id, subjectId: task.subjectId, subjectName: task.subjectName,
    title: task.title, plannedMinutes: task.plannedMinutes,
  });

  const toggleTask = async (task: StudyTask) => {
    try { await YanjiDataNative.toggleTask(task.id, !task.completed); await refresh(); }
    catch { /* keep original state until refreshed */ }
  };

  const openGoal = () => {
    if (!hasFocus || !settings) return;
    setGoalDraft(Math.max(0, Math.round((settings.dailyGoalHours ?? 0) * 60)));
    setGoalError(null);
    setGoalOpen(true);
  };
  const saveGoal = async () => {
    setSavingGoal(true);
    setGoalError(null);
    try {
      // 当前原生接口仅提供「日常默认」的持久化；不伪造当天覆盖。
      const ok = await YanjiDataNative.updateUserSettings({ dailyGoalHours: goalDraft / 60 });
      if (!ok) throw new Error('update failed');
      await refresh();
      setGoalOpen(false);
    } catch { setGoalError('保存失败，请稍后再试'); }
    finally { setSavingGoal(false); }
  };

  const ink = theme.colors.textPrimary;
  const muted = theme.colors.textSecondary;
  const subtle = theme.colors.textTertiary;
  const accent = theme.colors.accentPrimary;
  const sheet = theme.colors.bgSurface;
  const faint = theme.colors.bgElevated;

  return (
    <View style={{ flex: 1, backgroundColor: theme.colors.bgPrimary }}>
      <ScrollView
        testID="scroll-today"
        style={{ flex: 1 }}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={() => void handleRefresh()} tintColor={accent} />}
        contentContainerStyle={{ paddingHorizontal: YanjiSpacing.page, paddingTop: YanjiSpacing.md, paddingBottom: contentPadding }}
      >
        {/* 页眉：按当地时间变化，不使用固定「今天」大标题。 */}
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: YanjiSpacing.lg }}>
          <View style={{ flex: 1 }}>
            <Text style={[theme.typography.pageTitle, { color: ink, letterSpacing: -0.4 }]}>{greeting(now.getHours())}</Text>
            <Text style={[theme.typography.caption, { color: subtle, marginTop: 4 }]}>{displayDate(date)}</Text>
          </View>
          <YanjiIconButton icon="settings" onPress={openSettings} accessibilityLabel="设置" size={YanjiTouch.comfortable} />
        </View>

        {/* 倒计时是首屏视觉锚点，不再退化成一个小标签。 */}
        {countdown && countdown.examDate ? (
          <View style={{ backgroundColor: theme.colors.accentSoft, borderRadius: YanjiRadius.xl, paddingHorizontal: 20, paddingVertical: 15, flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
            <View>
              <Text style={[theme.typography.caption, { color: accent }]}>距离考研初试</Text>
              <View style={{ flexDirection: 'row', alignItems: 'baseline', marginTop: 4 }}>
                <Text style={{ color: accent, fontSize: 39, fontWeight: '700', fontVariant: ['tabular-nums'] }}>{countdown.daysRemaining}</Text>
                <Text style={{ color: accent, fontSize: 15, fontWeight: '600', marginLeft: 6 }}>天</Text>
              </View>
            </View>
            <YanjiIcon name="calendar" size={30} color={accent} />
          </View>
        ) : null}

        {hasFocus ? (
          <View style={{ marginTop: YanjiSpacing.xl }}>
            <Text style={[theme.typography.caption, { color: muted }]}>今日累计专注</Text>
            <View style={{ marginTop: 6, flexDirection: 'row', alignItems: 'flex-end', justifyContent: 'space-between' }}>
              <Text style={{ color: ink, fontSize: 32, fontWeight: '700', fontVariant: ['tabular-nums'] }}>{duration(effectiveSeconds)}</Text>
              {goalSeconds > 0 ? (
                <Pressable onPress={openGoal} accessibilityRole="button" accessibilityLabel="修改日常学习目标" style={{ alignItems: 'flex-end', padding: 4 }}>
                  <Text style={[theme.typography.meta, { color: muted }]}>目标 {duration(goalSeconds)}  ✎</Text>
                  <Text style={{ fontSize: 19, fontWeight: '700', color: accent }}>{pct}%</Text>
                </Pressable>
              ) : (
                <Pressable onPress={openGoal} accessibilityRole="button" accessibilityLabel="设置日常学习目标" style={{ padding: 7 }}>
                  <Text style={[theme.typography.caption, { color: accent }]}>设置学习目标  +</Text>
                </Pressable>
              )}
            </View>
            {goalSeconds > 0 ? (
              <>
                <View style={{ height: 6, borderRadius: YanjiRadius.full, backgroundColor: faint, overflow: 'hidden', marginTop: 12 }}>
                  <View style={{ height: '100%', width: `${Math.min(100, pct)}%`, backgroundColor: accent, borderRadius: YanjiRadius.full }} />
                </View>
                <Text style={[theme.typography.caption, { color: muted, marginTop: 8 }]}>
                  {effectiveSeconds >= goalSeconds ? `已超额 ${duration(effectiveSeconds - goalSeconds)}` : `距离今日目标还差 ${duration(goalSeconds - effectiveSeconds)}`}
                  {timeline ? ` · 专注 ${timeline.focusCount + timeline.examCount} 次` : ''}
                </Text>
              </>
            ) : (
              <Text style={[theme.typography.caption, { color: muted, marginTop: 8 }]}>
                {timeline ? `专注 ${timeline.focusCount + timeline.examCount} 次` : ''}
              </Text>
            )}
          </View>
        ) : null}

        {/* 仅这一处主要操作。活跃会话的数字只消费原生 Tick。 */}
        <Pressable
          onPress={() => selectTab('focus')}
          accessibilityRole="button"
          accessibilityLabel={active ? '返回正在进行的专注' : '开始专注'}
          style={({ pressed }) => ({
            marginTop: YanjiSpacing.lg, backgroundColor: accent, borderRadius: YanjiRadius.lg,
            minHeight: active ? 62 : 54, paddingHorizontal: 18,
            flexDirection: 'row', alignItems: 'center', justifyContent: 'center',
            opacity: pressed ? 0.84 : 1,
          })}
        >
          <YanjiIcon name={active ? (active.isPaused ? 'pause' : 'focus') : 'play'} color={theme.colors.onAccent} size={19} />
          <View style={{ marginLeft: 10, flex: active ? 1 : undefined }}>
            <Text style={{ color: theme.colors.onAccent, fontSize: 16, fontWeight: '700' }}>{active ? '返回专注' : '开始专注'}</Text>
            {active ? <Text style={{ color: theme.colors.onAccent, fontSize: 11, opacity: 0.82 }}>{active.subjectName} · {active.isPaused || tick?.isPaused ? '已暂停' : '专注中'}</Text> : null}
          </View>
          {active ? (
            <Text style={{ color: theme.colors.onAccent, fontVariant: ['tabular-nums'], fontSize: 19, fontWeight: '600' }}>
              {clock(active.isCountdown ? (tick?.remainingSeconds ?? active.remainingSeconds) : (tick?.elapsedSeconds ?? active.elapsedSeconds))}
            </Text>
          ) : null}
        </Pressable>

        {/* 没有已保存时长时图表彻底消失。 */}
        {hasFocus && subjects.length > 0 ? (
          <View style={{ marginTop: YanjiSpacing.xl }}>
            <View style={{ backgroundColor: faint, borderRadius: YanjiRadius.md, flexDirection: 'row', padding: 4 }}>
              {([{ id: 'subjects', label: '科目时长分布' }, { id: 'timeline', label: '今日专注时段' }] as const).map(mode => (
                <Pressable
                  key={mode.id} onPress={() => { setChart(mode.id); setSelection(null); }}
                  accessibilityRole="tab" accessibilityState={{ selected: chart === mode.id }}
                  style={{ flex: 1, alignItems: 'center', justifyContent: 'center', minHeight: 40,
                    borderRadius: YanjiRadius.sm, backgroundColor: chart === mode.id ? sheet : 'transparent' }}
                >
                  <Text style={{ color: chart === mode.id ? ink : muted, fontSize: 13, fontWeight: chart === mode.id ? '700' : '500' }}>{mode.label}</Text>
                </Pressable>
              ))}
            </View>

            {chart === 'subjects' ? (
              <View style={{ paddingTop: 20 }}>
                <View style={{ height: 32, flexDirection: 'row', alignItems: 'center', overflow: 'hidden', borderRadius: YanjiRadius.sm }}>
                  {subjects.map(subject => (
                    <Pressable
                      key={subject.id}
                      onPress={() => selectSegment({ key: `subject:${subject.id}`, subjectId: subject.id, subjectName: subject.name, seconds: subject.seconds })}
                      accessibilityRole="button"
                      accessibilityLabel={`${subject.name}，${duration(subject.seconds)}`}
                      hitSlop={8}
                      style={{ flex: subject.seconds, height: selection?.subjectId === subject.id ? 32 : 22,
                        backgroundColor: selection && selection.subjectId !== subject.id ? theme.colors.bgSunken : subject.color }}
                    />
                  ))}
                </View>
                <View style={{ flexDirection: 'row', flexWrap: 'wrap', marginTop: 12, gap: 10 }}>
                  {subjects.map(subject => (
                    <Pressable
                      key={subject.id} onPress={() => selectSegment({ key: `subject:${subject.id}`, subjectId: subject.id, subjectName: subject.name, seconds: subject.seconds })}
                      accessibilityRole="button" accessibilityLabel={`筛选${subject.name}`}
                      style={{ minHeight: 36, flexDirection: 'row', alignItems: 'center', opacity: selection && selection.subjectId !== subject.id ? 0.45 : 1 }}
                    >
                      <View style={{ height: 8, width: 8, backgroundColor: subject.color, borderRadius: 4, marginRight: 6 }} />
                      <Text style={[theme.typography.caption, { color: ink }]}>{subject.name} {chartTotal ? Math.round(subject.seconds / chartTotal * 100) : 0}%</Text>
                    </Pressable>
                  ))}
                </View>
              </View>
            ) : (
              <View style={{ paddingTop: 12 }}>
                {DAY_PARTS.filter(part => part.from !== 0 || (timeline?.sessions ?? []).some(s => new Date(s.startTime).getHours() < 6)).map(part => {
                  const start = new Date(`${date}T00:00:00`).getTime() + part.from * 3600000;
                  const end = start + 6 * 3600000;
                  const sixHours = 6 * 3600000;
                  const segments = (timeline?.sessions ?? [])
                    .filter(s => sessionPrecise(s) && s.endTime > start && s.startTime < end)
                    .sort((a, b) => a.startTime - b.startTime);
                  return (
                    <View key={part.name} style={{ marginTop: 12 }}>
                      <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: 7 }}>
                        <Text style={[theme.typography.caption, { color: ink, fontWeight: '600' }]}>{part.name}</Text>
                        <Text style={[theme.typography.meta, { color: subtle }]}>{String(part.from).padStart(2, '0')}:00—{String(part.to).padStart(2, '0')}:00</Text>
                      </View>
                      <View style={{ height: 30, backgroundColor: faint, borderRadius: YanjiRadius.sm, overflow: 'hidden', justifyContent: 'center' }}>
                        {segments.map(s => {
                          const left = Math.max(0, s.startTime - start) / sixHours * 100;
                          const right = Math.min(end, s.endTime);
                          const width = Math.max(0, right - Math.max(start, s.startTime)) / sixHours * 100;
                          const subject = subjects.find(x => x.id === s.subjectId);
                          return (
                            <Pressable
                              key={s.id}
                              onPress={() => selectSegment({ key: `session:${s.id}`, subjectId: s.subjectId, subjectName: s.subjectName, seconds: s.durationSeconds })}
                              accessibilityRole="button" accessibilityLabel={`${s.subjectName}，本次${duration(s.durationSeconds)}`}
                              hitSlop={8}
                              style={{ position: 'absolute', left: `${left}%`, width: `${width}%`, minHeight: selection?.key === `session:${s.id}` ? 30 : 21,
                                backgroundColor: selection && selection.subjectId !== s.subjectId ? theme.colors.bgSunken : (subject?.color ?? accent),
                                borderRadius: YanjiRadius.xs }}
                            />
                          );
                        })}
                      </View>
                    </View>
                  );
                })}
                {(timeline?.sessions ?? []).some(s => !sessionPrecise(s)) ? (
                  <Text style={[theme.typography.meta, { color: subtle, marginTop: 10 }]}>
                    含暂停的记录暂不绘制连续区段，避免把休息误算为专注
                  </Text>
                ) : null}
              </View>
            )}
            {selection ? (
              <View style={{ alignItems: 'flex-end', marginTop: 8 }}>
                <View style={{ backgroundColor: selectedSubject?.color ?? accent, borderRadius: YanjiRadius.full, paddingVertical: 7, paddingHorizontal: 12 }}>
                  <Text style={{ color: '#FFFFFF', fontSize: 12, fontWeight: '600' }}>{selection.subjectName} · {duration(selection.seconds)}</Text>
                </View>
              </View>
            ) : null}
          </View>
        ) : null}

        <YanjiHairline style={{ marginTop: YanjiSpacing.xl, marginBottom: YanjiSpacing.lg }} />
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
          <View>
            <Text style={[theme.typography.sectionTitle, { color: ink }]}>今日任务</Text>
            <Text style={[theme.typography.meta, { color: subtle, marginTop: 2 }]}>已完成 {stats?.completedTasksCount ?? 0}/{stats?.totalTasksCount ?? 0}</Text>
          </View>
          <Pressable onPress={openTaskEditor} accessibilityRole="button" accessibilityLabel="添加任务" style={{ flexDirection: 'row', alignItems: 'center', minHeight: 44, paddingHorizontal: 8 }}>
            <YanjiIcon name="add" size={16} color={accent} />
            <Text style={{ color: accent, marginLeft: 4, fontWeight: '600' }}>添加</Text>
          </Pressable>
        </View>
        {pending.length === 0 ? (
          <Pressable onPress={openTaskEditor} accessibilityRole="button" style={{ paddingVertical: 12 }}>
            <Text style={[theme.typography.caption, { color: muted }]}>{tasks.length ? '今日任务已全部完成' : '还没有任务，点击添加今天的计划'}</Text>
          </Pressable>
        ) : pending.map((task, i) => (
          <View key={task.id}>
            {i > 0 ? <YanjiHairline /> : null}
            <View style={{ flexDirection: 'row', alignItems: 'center', minHeight: 60 }}>
              <Pressable
                onPress={() => void toggleTask(task)} accessibilityRole="checkbox" accessibilityState={{ checked: false }}
                accessibilityLabel={`完成任务：${task.title}`}
                style={{ width: 44, height: 48, justifyContent: 'center' }}
              ><YanjiIcon name="tasks" color={subtle} size={20} /></Pressable>
              <Pressable onPress={() => setTaskInfo(task)} accessibilityRole="button"
                accessibilityLabel={`查看任务：${task.title}`} style={{ flex: 1, paddingVertical: 8 }}>
                <Text numberOfLines={1} style={[theme.typography.bodyStrong, { color: ink }]}>{task.title}</Text>
                <Text style={[theme.typography.meta, { color: subtle, marginTop: 2 }]}>{task.subjectName} · 预计 {task.plannedMinutes} 分钟</Text>
              </Pressable>
              <Pressable onPress={() => startTask(task)} accessibilityRole="button" accessibilityLabel={`为${task.title}设置专注`}
                style={{ width: 46, height: 48, alignItems: 'center', justifyContent: 'center' }}>
                <YanjiIcon name="play" size={19} color={accent} />
              </Pressable>
            </View>
          </View>
        ))}
        {tasks.filter(t => !t.completed).length > 3 ? (
          <Text style={[theme.typography.meta, { color: muted, marginTop: 6 }]}>另外还有 {tasks.filter(t => !t.completed).length - 3} 项未完成任务</Text>
        ) : null}

        <YanjiHairline style={{ marginTop: YanjiSpacing.xl, marginBottom: YanjiSpacing.lg }} />
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
          <Text style={[theme.typography.sectionTitle, { color: ink }]}>今日记录</Text>
          <Pressable onPress={() => { setEditingNote(null); setRecordOpen(true); }}
            accessibilityRole="button" accessibilityLabel="新建今日记录"
            style={{ minHeight: 44, paddingHorizontal: 8, flexDirection: 'row', alignItems: 'center' }}>
            <YanjiIcon name="add" size={16} color={accent} />
            <Text style={{ color: accent, marginLeft: 4, fontWeight: '600' }}>新建</Text>
          </Pressable>
        </View>
        {latestNotes.length ? latestNotes.map(note => (
          <Pressable key={note.id} onPress={() => { setEditingNote(note); setRecordOpen(true); }}
            accessibilityRole="button" accessibilityLabel={`编辑记录：${noteTitle(note)}`}
            style={{ paddingVertical: 10, flexDirection: 'row', alignItems: 'center', minHeight: 48 }}>
            <YanjiIcon name="compose" color={subtle} size={16} />
            <Text style={[theme.typography.body, { color: ink, marginHorizontal: 10, flex: 1 }]} numberOfLines={1}>{noteTitle(note)}</Text>
            <Text style={[theme.typography.meta, { color: subtle }]}>{new Date(note.timestamp).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })}</Text>
          </Pressable>
        )) : (
          <Pressable onPress={() => { setEditingNote(null); setRecordOpen(true); }} accessibilityRole="button" style={{ paddingVertical: 12 }}>
            <Text style={[theme.typography.caption, { color: muted }]}>随时记下今天的第一个想法</Text>
          </Pressable>
        )}
        {notes.length > 2 ? (
          <Pressable onPress={() => selectTab('review')} accessibilityRole="button" style={{ alignSelf: 'flex-start', paddingVertical: 10 }}>
            <Text style={[theme.typography.caption, { color: accent }]}>前往回顾查看今天全部 {notes.length} 篇记录 ›</Text>
          </Pressable>
        ) : null}
      </ScrollView>

      <RecordMomentModal
        visible={recordOpen} date={date} editingNote={editingNote}
        onClose={() => { setRecordOpen(false); setEditingNote(null); }}
        onSaved={() => void refresh()}
      />
      <TaskEditorModal
        visible={taskEditorOpen || !!taskInfo}
        date={date}
        editingTask={taskInfo}
        onClose={() => { setTaskInfo(null); closeTaskEditor(); }}
        onSaved={() => void refresh()}
      />

      {/* 默认目标使用原生 Room settings 持久化；当日覆盖尚未由原生数据层提供。 */}
      <Modal visible={goalOpen} transparent animationType="slide" onRequestClose={() => setGoalOpen(false)}>
        <View style={{ flex: 1, backgroundColor: theme.colors.scrim, justifyContent: 'flex-end' }}>
          <View style={{ backgroundColor: sheet, borderTopLeftRadius: YanjiRadius.xl, borderTopRightRadius: YanjiRadius.xl, padding: 24, paddingBottom: contentPadding }}>
            <Text style={[theme.typography.sectionTitle, { color: ink }]}>调整日常学习目标</Text>
            <Text style={[theme.typography.caption, { color: muted, marginTop: 5 }]}>修改后影响今天及未来，不更改已记录的学习时长。</Text>
            <Text style={{ color: accent, fontSize: 30, fontWeight: '700', textAlign: 'center', marginTop: 22 }}>{goalDraft ? duration(goalDraft * 60) : '未设置'}</Text>
            <View style={{ flexDirection: 'row', justifyContent: 'center', alignItems: 'center', marginTop: 14, gap: 24 }}>
              <Pressable onPress={() => setGoalDraft(Math.max(0, goalDraft - 30))}
                accessibilityRole="button" accessibilityLabel="减少30分钟" style={{ width: 48, height: 48, backgroundColor: faint, borderRadius: YanjiRadius.full, alignItems: 'center', justifyContent: 'center' }}>
                <YanjiIcon name="remove" color={ink} />
              </Pressable>
              <Text style={[theme.typography.bodyStrong, { color: ink }]}>每次调整30分钟</Text>
              <Pressable onPress={() => setGoalDraft(Math.min(720, goalDraft + 30))}
                accessibilityRole="button" accessibilityLabel="增加30分钟" style={{ width: 48, height: 48, backgroundColor: faint, borderRadius: YanjiRadius.full, alignItems: 'center', justifyContent: 'center' }}>
                <YanjiIcon name="add" color={ink} />
              </Pressable>
            </View>
            <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: 16 }}>
              {[240, 360, 480, 600].map(value => (
                <Pressable key={value} onPress={() => setGoalDraft(value)}
                  style={{ paddingVertical: 9, paddingHorizontal: 10, borderRadius: YanjiRadius.sm, backgroundColor: goalDraft === value ? theme.colors.accentSoft : faint }}>
                  <Text style={{ color: goalDraft === value ? accent : muted, fontSize: 12 }}>{duration(value * 60)}</Text>
                </Pressable>
              ))}
            </View>
            {goalError ? <Text style={{ color: theme.colors.danger, marginTop: 12 }}>{goalError}</Text> : null}
            <Pressable onPress={() => void saveGoal()} disabled={savingGoal}
              style={{ marginTop: 20, backgroundColor: accent, minHeight: 48, borderRadius: YanjiRadius.md, alignItems: 'center', justifyContent: 'center' }}>
              <Text style={{ color: theme.colors.onAccent, fontWeight: '700' }}>{savingGoal ? '保存中…' : '保存目标'}</Text>
            </Pressable>
            <Pressable onPress={() => setGoalOpen(false)} accessibilityRole="button" style={{ alignItems: 'center', padding: 12 }}>
              <Text style={{ color: muted }}>取消</Text>
            </Pressable>
          </View>
        </View>
      </Modal>
    </View>
  );
}
