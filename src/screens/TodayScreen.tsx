/**
 * 今日工作台 V2：事实来自 Kotlin/Room；没有已保存专注就不渲染统计或图表。
 * 暂不在 JS 侧推算跨午夜/暂停归属：必须由原生统计接口提供准确区间。
 */
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Modal, Pressable, RefreshControl, ScrollView, Text, View } from 'react-native';
import { YanjiDataNative } from '../bridge';
import type { DailySession, NoteEntry, StudyTask } from '../bridge';
import { YanjiCard, YanjiHairline, YanjiIcon, YanjiIconButton } from '../components/YanjiUI';
import { RecordMomentModal } from '../components/RecordMomentModal';
import { TaskEditorModal } from '../components/TaskEditorModal';
import { useNavigationActions, useNavigationState } from '../navigation/NavigationShell';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { useBottomTabLayout } from '../navigation/useBottomTabLayout';
import { YanjiRadius, YanjiSpacing, YanjiTouch } from '../theme/tokens';
import {
  refreshDailySnapshot,
  refreshSettingsSnapshot,
  useDailySnapshot,
  useSettingsSnapshot,
} from '../state/dataRefresh';
import { syncActiveSession, useActiveSession, useTimerTick } from '../state/timerStore';

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
function sessionPrecise(s: DailySession): boolean {
  // Native pause intervals are unavailable; paused sessions cannot be drawn as uninterrupted focus.
  return s.pauseCount === 0 && s.endTime > s.startTime
    && Math.abs((s.endTime - s.startTime) / 1000 - s.durationSeconds) <= 2;
}

export function TodayScreen({
  visible,
  foregroundRevision,
}: {
  visible: boolean;
  foregroundRevision: number;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const { contentPadding } = useBottomTabLayout();
  const { openSettings, openTaskEditor, closeTaskEditor, selectTab, requestFocusPreset } =
    useNavigationActions();
  const { taskEditorOpen } = useNavigationState();

  const [now, setNow] = useState(() => new Date());
  const date = localDay(now);
  const snapshot = useDailySnapshot('today', date, visible);
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
  const [goalDraft, setGoalDraft] = useState(360);
  const [goalError, setGoalError] = useState<string | null>(null);
  const [savingGoal, setSavingGoal] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  const previousDate = useRef(date);

  useEffect(() => {
    if (previousDate.current !== date) {
      previousDate.current = date;
      void refreshSettingsSnapshot();
    }
  }, [date]);

  useEffect(() => {
    if (!visible) return;
    setNow(new Date());
    const timer = setInterval(() => setNow(new Date()), 30_000);
    return () => {
      clearInterval(timer);
    };
  }, [visible, foregroundRevision]);

  useEffect(() => {
    setSelection(null);
  }, [date]);

  const handleRefresh = useCallback(async () => {
    setRefreshing(true);
    try {
      await Promise.all([
        refreshDailySnapshot(date),
        refreshSettingsSnapshot(),
        syncActiveSession(),
      ]);
    }
    finally { setRefreshing(false); }
  }, [date]);

  const effectiveSeconds = snapshot?.date === date ? snapshot.totalDurationSeconds : 0;
  const hasFocus = effectiveSeconds > 0;
  const pending = tasks.filter(t => !t.completed).slice(0, 3);
  const latestNotes = useMemo(() => [...notes].sort((a,b) => b.timestamp - a.timestamp).slice(0, 2), [notes]);
  const goalSeconds = (settings?.dailyGoalHours ?? 0) * 3600;
  const pct = goalSeconds > 0 ? Math.round(effectiveSeconds / goalSeconds * 100) : 0;

  const subjects = useMemo(() => {
    const map = new Map<string, { id: string; name: string; seconds: number; color: string }>();
    for (const s of sessions) {
      if (s.durationSeconds <= 0) continue;
      const prev = map.get(s.subjectId);
      if (prev) prev.seconds += s.durationSeconds;
      else map.set(s.subjectId, { id: s.subjectId, name: s.subjectName, seconds: s.durationSeconds,
        color: SUBJECT_COLORS[map.size % SUBJECT_COLORS.length] });
    }
    return [...map.values()].sort((a,b) => b.seconds - a.seconds);
  }, [sessions]);
  const chartTotal = subjects.reduce((sum, sub) => sum + sub.seconds, 0);
  const selectedSubject = subjects.find(s => s.id === selection?.subjectId);
  const selectSegment = (next: NonNullable<Selection>) =>
    setSelection(current => current?.key === next.key ? null : next);

  const startTask = (task: StudyTask) => requestFocusPreset({
    taskId: task.id, subjectId: task.subjectId, subjectName: task.subjectName,
    title: task.title, plannedMinutes: task.plannedMinutes,
  });

  const toggleTask = async (task: StudyTask) => {
    try { await YanjiDataNative.toggleTask(task.id, !task.completed); }
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
        {/* 页眉：问候语 + 右侧轻量考研倒计时胶囊与设置 */}
        <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: YanjiSpacing.md }}>
          <View style={{ flex: 1, marginRight: YanjiSpacing.sm }}>
            <Text style={[theme.typography.pageTitle, { color: ink, letterSpacing: -0.5 }]}>{greeting(now.getHours())}</Text>
            <Text style={[theme.typography.caption, { color: subtle, marginTop: 3 }]}>{displayDate(date)}</Text>
          </View>
          <View style={{ flexDirection: 'row', alignItems: 'center' }}>
            {countdown && countdown.examDate ? (
              <Pressable
                onPress={openSettings}
                accessibilityRole="button"
                accessibilityLabel={`考研初试倒计时${countdown.daysRemaining}天`}
                hitSlop={6}
                style={({ pressed }) => ({
                  flexDirection: 'row',
                  alignItems: 'center',
                  backgroundColor: theme.colors.accentSoft,
                  borderWidth: 1,
                  borderColor: theme.colors.cardBorder,
                  paddingVertical: 6,
                  paddingHorizontal: 12,
                  borderRadius: YanjiRadius.full,
                  marginRight: 6,
                  opacity: pressed ? 0.75 : 1,
                })}
              >
                <YanjiIcon name="calendar" size={13} color={accent} />
                <Text style={{ color: accent, fontSize: 13, fontWeight: '700', marginLeft: 5, fontVariant: ['tabular-nums'] }}>
                  初试 {countdown.daysRemaining}天
                </Text>
              </Pressable>
            ) : null}
            <YanjiIconButton icon="settings" onPress={openSettings} accessibilityLabel="设置" size={YanjiTouch.comfortable} />
          </View>
        </View>

        {/* 整合式专注主控卡 (Integrated Focus Hero) */}
        <YanjiCard variant="hero" style={{ padding: 20 }}>
          {active ? (
            /* 活跃专注态：脉动沉浸展示 */
            <View>
              <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
                <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                  <View style={{ width: 8, height: 8, borderRadius: YanjiRadius.full, backgroundColor: active.isPaused ? theme.colors.warning : theme.colors.success, marginRight: 8 }} />
                  <Text style={[theme.typography.caption, { color: muted, fontWeight: '600' }]}>
                    {active.subjectName} · {active.isPaused ? '已暂停' : '正在专注'}
                  </Text>
                </View>
                <View
                  style={{
                    backgroundColor: theme.colors.accentSoft,
                    paddingHorizontal: 8,
                    paddingVertical: 3,
                    borderRadius: YanjiRadius.full,
                    borderWidth: 1,
                    borderColor: theme.colors.cardBorder,
                  }}
                >
                  <Text style={{ fontSize: 11, fontWeight: '600', color: accent }}>
                    {active.mode === 'COUNTDOWN' ? '倒计时' : '正计时'}
                  </Text>
                </View>
              </View>

              <View style={{ marginVertical: 18, alignItems: 'center' }}>
                <LiveSessionReadout session={active} visible={visible} color={ink} />
              </View>

              <Pressable
                onPress={() => selectTab('focus')}
                accessibilityRole="button"
                accessibilityLabel={active ? '返回正在进行的专注' : '开始专注'}
                style={({ pressed }) => ({
                  backgroundColor: accent,
                  borderRadius: YanjiRadius.lg,
                  minHeight: 50,
                  flexDirection: 'row',
                  alignItems: 'center',
                  justifyContent: 'center',
                  opacity: pressed ? 0.88 : 1,
                  ...theme.shadow.sheet(theme.isDark),
                })}
              >
                <YanjiIcon name={active.isPaused ? 'play' : 'focus'} color={theme.colors.onAccent} size={18} />
                <Text style={{ color: theme.colors.onAccent, fontSize: 15, fontWeight: '700', marginLeft: 8 }}>
                  返回专注工作台
                </Text>
              </Pressable>
            </View>
          ) : (
            /* 常规态：今日累计与一触即发入口 */
            <View>
              <View style={{ flexDirection: 'row', alignItems: 'flex-start', justifyContent: 'space-between' }}>
                <View>
                  <Text style={[theme.typography.caption, { color: muted, letterSpacing: 0.2 }]}>今日累计专注</Text>
                  <Text style={{ color: ink, fontSize: 34, fontWeight: '700', marginTop: 4, fontVariant: ['tabular-nums'] }}>
                    {duration(effectiveSeconds)}
                  </Text>
                </View>

                {goalSeconds > 0 ? (
                  <Pressable
                    onPress={openGoal}
                    accessibilityRole="button"
                    accessibilityLabel="修改日常学习目标"
                    hitSlop={8}
                    style={({ pressed }) => ({
                      alignItems: 'flex-end',
                      paddingVertical: 5,
                      paddingHorizontal: 10,
                      borderRadius: YanjiRadius.full,
                      backgroundColor: theme.colors.bgSunken,
                      borderWidth: 1,
                      borderColor: theme.colors.cardBorder,
                      opacity: pressed ? 0.7 : 1,
                    })}
                  >
                    <Text style={{ fontSize: 15, fontWeight: '700', color: accent, fontVariant: ['tabular-nums'] }}>{pct}%</Text>
                    <Text style={[theme.typography.axisLabel, { color: muted }]}>目标 {duration(goalSeconds)}</Text>
                  </Pressable>
                ) : (
                  <Pressable
                    onPress={openGoal}
                    accessibilityRole="button"
                    accessibilityLabel="设置日常学习目标"
                    hitSlop={8}
                    style={({ pressed }) => ({
                      paddingVertical: 6,
                      paddingHorizontal: 12,
                      borderRadius: YanjiRadius.full,
                      backgroundColor: theme.colors.accentSoft,
                      borderWidth: 1,
                      borderColor: theme.colors.cardBorder,
                      opacity: pressed ? 0.7 : 1,
                    })}
                  >
                    <Text style={[theme.typography.caption, { color: accent, fontWeight: '600' }]}>设目标 +</Text>
                  </Pressable>
                )}
              </View>

              {goalSeconds > 0 ? (
                <View style={{ marginTop: 12, marginBottom: 16 }}>
                  <View style={{ height: 6, borderRadius: YanjiRadius.full, backgroundColor: faint, overflow: 'hidden' }}>
                    <View style={{ height: '100%', width: `${Math.min(100, pct)}%`, backgroundColor: accent, borderRadius: YanjiRadius.full }} />
                  </View>
                  <Text style={[theme.typography.meta, { color: subtle, marginTop: 8 }]}>
                    {effectiveSeconds >= goalSeconds ? `已超额达成目标 ${duration(effectiveSeconds - goalSeconds)} 🎉` : `距离今日目标还差 ${duration(goalSeconds - effectiveSeconds)}`}
                    {snapshot ? ` · 专注 ${snapshot.focusCount + snapshot.examCount} 次` : ''}
                  </Text>
                </View>
              ) : (
                <Text style={[theme.typography.meta, { color: subtle, marginTop: 6, marginBottom: 16 }]}>
                  {snapshot && (snapshot.focusCount + snapshot.examCount > 0)
                    ? `今日已专注 ${snapshot.focusCount + snapshot.examCount} 次`
                    : '还没有专注记录，选一个任务开启心流吧'}
                </Text>
              )}

              <Pressable
                onPress={() => {
                  if (pending.length > 0) {
                    startTask(pending[0]);
                  } else {
                    selectTab('focus');
                  }
                }}
                accessibilityRole="button"
                accessibilityLabel="开始专注"
                style={({ pressed }) => ({
                  backgroundColor: accent,
                  borderRadius: YanjiRadius.lg,
                  minHeight: 50,
                  paddingHorizontal: 18,
                  flexDirection: 'row',
                  alignItems: 'center',
                  justifyContent: 'center',
                  opacity: pressed ? 0.88 : 1,
                  ...theme.shadow.sheet(theme.isDark),
                })}
              >
                <YanjiIcon name="play" color={theme.colors.onAccent} size={18} />
                <Text style={{ color: theme.colors.onAccent, fontSize: 15, fontWeight: '700', marginLeft: 8 }}>
                  {pending.length > 0 ? `继续学习 · ${pending[0].title}` : '开始专注'}
                </Text>
              </Pressable>
            </View>
          )}
        </YanjiCard>

        {/* 科目与时段图表 */}
        {hasFocus && subjects.length > 0 ? (
          <YanjiCard style={{ marginTop: YanjiSpacing.lg, padding: 16 }}>
            <View style={{ backgroundColor: faint, borderRadius: YanjiRadius.md, flexDirection: 'row', padding: 4 }}>
              {([{ id: 'subjects', label: '科目时长分布' }, { id: 'timeline', label: '今日专注时段' }] as const).map(mode => (
                <Pressable
                  key={mode.id} onPress={() => { setChart(mode.id); setSelection(null); }}
                  accessibilityRole="tab" accessibilityState={{ selected: chart === mode.id }}
                  style={{ flex: 1, alignItems: 'center', justifyContent: 'center', minHeight: 38,
                    borderRadius: YanjiRadius.sm, backgroundColor: chart === mode.id ? sheet : 'transparent' }}
                >
                  <Text style={{ color: chart === mode.id ? ink : muted, fontSize: 13, fontWeight: chart === mode.id ? '700' : '500' }}>{mode.label}</Text>
                </Pressable>
              ))}
            </View>

            {chart === 'subjects' ? (
              <View style={{ paddingTop: 16 }}>
                <View style={{ height: 28, flexDirection: 'row', alignItems: 'center', overflow: 'hidden', borderRadius: YanjiRadius.sm }}>
                  {subjects.map(subject => (
                    <Pressable
                      key={subject.id}
                      onPress={() => selectSegment({ key: `subject:${subject.id}`, subjectId: subject.id, subjectName: subject.name, seconds: subject.seconds })}
                      accessibilityRole="button"
                      accessibilityLabel={`${subject.name}，${duration(subject.seconds)}`}
                      hitSlop={8}
                      style={{ flex: subject.seconds, height: selection?.subjectId === subject.id ? 28 : 20,
                        backgroundColor: selection && selection.subjectId !== subject.id ? theme.colors.bgSunken : subject.color }}
                    />
                  ))}
                </View>
                <View style={{ flexDirection: 'row', flexWrap: 'wrap', marginTop: 12, gap: 10 }}>
                  {subjects.map(subject => (
                    <Pressable
                      key={subject.id} onPress={() => selectSegment({ key: `subject:${subject.id}`, subjectId: subject.id, subjectName: subject.name, seconds: subject.seconds })}
                      accessibilityRole="button" accessibilityLabel={`筛选${subject.name}`}
                      style={{ minHeight: 34, flexDirection: 'row', alignItems: 'center', opacity: selection && selection.subjectId !== subject.id ? 0.45 : 1 }}
                    >
                      <View style={{ height: 8, width: 8, backgroundColor: subject.color, borderRadius: YanjiRadius.xs, marginRight: 6 }} />
                      <Text style={[theme.typography.caption, { color: ink }]}>{subject.name} {chartTotal ? Math.round(subject.seconds / chartTotal * 100) : 0}%</Text>
                    </Pressable>
                  ))}
                </View>
              </View>
            ) : (
              <View style={{ paddingTop: 12 }}>
                {DAY_PARTS.filter(part => part.from !== 0 || sessions.some(s => new Date(s.startTime).getHours() < 6)).map(part => {
                  const start = new Date(`${date}T00:00:00`).getTime() + part.from * 3600000;
                  const end = start + 6 * 3600000;
                  const sixHours = 6 * 3600000;
                  const segments = sessions
                    .filter(s => sessionPrecise(s) && s.endTime > start && s.startTime < end)
                    .sort((a, b) => a.startTime - b.startTime);
                  return (
                    <View key={part.name} style={{ marginTop: 12 }}>
                      <View style={{ flexDirection: 'row', justifyContent: 'space-between', marginBottom: 6 }}>
                        <Text style={[theme.typography.caption, { color: ink, fontWeight: '600' }]}>{part.name}</Text>
                        <Text style={[theme.typography.meta, { color: subtle }]}>{String(part.from).padStart(2, '0')}:00—{String(part.to).padStart(2, '0')}:00</Text>
                      </View>
                      <View style={{ height: 28, backgroundColor: faint, borderRadius: YanjiRadius.sm, overflow: 'hidden', justifyContent: 'center' }}>
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
                              style={{ position: 'absolute', left: `${left}%`, width: `${width}%`, minHeight: selection?.key === `session:${s.id}` ? 28 : 18,
                                backgroundColor: selection && selection.subjectId !== s.subjectId ? theme.colors.bgSunken : (subject?.color ?? accent),
                                borderRadius: YanjiRadius.xs }}
                            />
                          );
                        })}
                      </View>
                    </View>
                  );
                })}
                {sessions.some(s => !sessionPrecise(s)) ? (
                  <Text style={[theme.typography.meta, { color: subtle, marginTop: 10 }]}>
                    含暂停的记录暂不绘制连续区段，避免把休息误算为专注
                  </Text>
                ) : null}
              </View>
            )}
            {selection ? (
              <View style={{ alignItems: 'flex-end', marginTop: 10 }}>
                <View style={{ backgroundColor: selectedSubject?.color ?? accent, borderRadius: YanjiRadius.full, paddingVertical: 6, paddingHorizontal: 12 }}>
                  <Text style={{ color: '#FFFFFF', fontSize: 12, fontWeight: '600' }}>{selection.subjectName} · {duration(selection.seconds)}</Text>
                </View>
              </View>
            ) : null}
          </YanjiCard>
        ) : null}

        {/* 今日任务卡片 */}
        <View style={{ marginTop: YanjiSpacing.lg }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
            <View>
              <Text style={[theme.typography.sectionTitle, { color: ink }]}>今日任务</Text>
              <Text style={[theme.typography.meta, { color: subtle, marginTop: 2 }]}>已完成 {tasks.filter(task => task.completed).length}/{tasks.length}</Text>
            </View>
            <Pressable onPress={openTaskEditor} accessibilityRole="button" accessibilityLabel="添加任务" hitSlop={8} style={{ flexDirection: 'row', alignItems: 'center', minHeight: 40, paddingHorizontal: 8 }}>
              <YanjiIcon name="add" size={16} color={accent} />
              <Text style={{ color: accent, marginLeft: 4, fontWeight: '600', fontSize: 14 }}>添加</Text>
            </Pressable>
          </View>
          <YanjiCard style={{ padding: pending.length === 0 ? 16 : 4 }}>
            {pending.length === 0 ? (
              <Pressable onPress={openTaskEditor} accessibilityRole="button" style={{ paddingVertical: 10, alignItems: 'center' }}>
                <Text style={[theme.typography.caption, { color: muted }]}>{tasks.length ? '今日任务已全部完成 🎉' : '还没有任务，点击添加今天的计划'}</Text>
              </Pressable>
            ) : pending.map((task, i) => (
              <View key={task.id}>
                {i > 0 ? <YanjiHairline inset={12} /> : null}
                <View style={{ flexDirection: 'row', alignItems: 'center', minHeight: 56, paddingHorizontal: 10 }}>
                  <Pressable
                    onPress={() => void toggleTask(task)}
                    accessibilityRole="checkbox"
                    accessibilityState={{ checked: false }}
                    accessibilityLabel={`完成任务：${task.title}`}
                    hitSlop={8}
                    style={{ width: 36, height: 44, justifyContent: 'center', alignItems: 'center' }}
                  >
                    <View
                      style={{
                        width: 18,
                        height: 18,
                        borderRadius: YanjiRadius.full,
                        borderWidth: 1.5,
                        borderColor: subtle,
                        alignItems: 'center',
                        justifyContent: 'center',
                      }}
                    />
                  </Pressable>
                  <Pressable onPress={() => setTaskInfo(task)} accessibilityRole="button"
                    accessibilityLabel={`查看任务：${task.title}`} style={{ flex: 1, paddingVertical: 8 }}>
                    <Text numberOfLines={1} style={[theme.typography.bodyStrong, { color: ink }]}>{task.title}</Text>
                    <Text style={[theme.typography.meta, { color: subtle, marginTop: 2 }]}>{task.subjectName} · 预计 {task.plannedMinutes} 分钟</Text>
                  </Pressable>
                  <Pressable onPress={() => startTask(task)} accessibilityRole="button" accessibilityLabel={`为${task.title}设置专注`}
                    hitSlop={8}
                    style={{ width: 44, height: 44, alignItems: 'center', justifyContent: 'center' }}>
                    <YanjiIcon name="play" size={18} color={accent} />
                  </Pressable>
                </View>
              </View>
            ))}
          </YanjiCard>
          {tasks.filter(t => !t.completed).length > 3 ? (
            <Text style={[theme.typography.meta, { color: muted, marginTop: 8, paddingHorizontal: 4 }]}>另外还有 {tasks.filter(t => !t.completed).length - 3} 项未完成任务</Text>
          ) : null}
        </View>

        {/* 今日记录卡片 */}
        <View style={{ marginTop: YanjiSpacing.lg }}>
          <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10 }}>
            <Text style={[theme.typography.sectionTitle, { color: ink }]}>今日记录</Text>
            <Pressable onPress={() => { setEditingNote(null); setRecordOpen(true); }}
              accessibilityRole="button" accessibilityLabel="新建今日记录"
              hitSlop={8}
              style={{ minHeight: 40, paddingHorizontal: 8, flexDirection: 'row', alignItems: 'center' }}>
              <YanjiIcon name="add" size={16} color={accent} />
              <Text style={{ color: accent, marginLeft: 4, fontWeight: '600', fontSize: 14 }}>新建</Text>
            </Pressable>
          </View>
          <YanjiCard style={{ padding: latestNotes.length === 0 ? 16 : 4 }}>
            {latestNotes.length ? latestNotes.map((note, i) => (
              <View key={note.id}>
                {i > 0 ? <YanjiHairline inset={12} /> : null}
                <Pressable onPress={() => { setEditingNote(note); setRecordOpen(true); }}
                  accessibilityRole="button" accessibilityLabel={`编辑记录：${noteTitle(note)}`}
                  style={{ paddingVertical: 10, paddingHorizontal: 10, flexDirection: 'row', alignItems: 'center', minHeight: 46 }}>
                  <YanjiIcon name="compose" color={subtle} size={16} />
                  <Text style={[theme.typography.body, { color: ink, marginHorizontal: 10, flex: 1 }]} numberOfLines={1}>{noteTitle(note)}</Text>
                  <Text style={[theme.typography.meta, { color: subtle }]}>{new Date(note.timestamp).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', hour12: false })}</Text>
                </Pressable>
              </View>
            )) : (
              <Pressable onPress={() => { setEditingNote(null); setRecordOpen(true); }} accessibilityRole="button" style={{ paddingVertical: 10, alignItems: 'center' }}>
                <Text style={[theme.typography.caption, { color: muted }]}>随时记下今天的第一个想法</Text>
              </Pressable>
            )}
          </YanjiCard>
          {notes.length > 2 ? (
            <Pressable onPress={() => selectTab('review')} accessibilityRole="button" style={{ alignSelf: 'flex-start', paddingVertical: 10, paddingHorizontal: 4 }}>
              <Text style={[theme.typography.caption, { color: accent }]}>前往回顾查看今天全部 {notes.length} 篇记录 ›</Text>
            </Pressable>
          ) : null}
        </View>
      </ScrollView>

      <RecordMomentModal
        visible={recordOpen} date={date} editingNote={editingNote}
        onClose={() => { setRecordOpen(false); setEditingNote(null); }}
      />
      <TaskEditorModal
        visible={taskEditorOpen || !!taskInfo}
        date={date}
        editingTask={taskInfo}
        onClose={() => { setTaskInfo(null); closeTaskEditor(); }}
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

function LiveSessionReadout({
  session,
  visible,
  color,
}: {
  session: NonNullable<ReturnType<typeof useActiveSession>>;
  visible: boolean;
  color: string;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const tick = useTimerTick(visible);
  const seconds = session.isCountdown
    ? tick?.remainingSeconds ?? session.remainingSeconds
    : tick?.elapsedSeconds ?? session.elapsedSeconds;
  const isPaused = session.isPaused || tick?.isPaused;

  return (
    <View style={{ alignItems: 'center' }}>
      <Text
        style={{
          color,
          fontVariant: ['tabular-nums'],
          fontSize: 38,
          fontWeight: '700',
          letterSpacing: -0.5,
        }}
      >
        {clock(seconds)}
      </Text>
      <Text
        style={[
          theme.typography.meta,
          {
            color: isPaused ? theme.colors.warning : theme.colors.textTertiary,
            marginTop: 4,
          },
        ]}
      >
        {isPaused
          ? '专注已暂停 · 点击下方按钮恢复'
          : session.isCountdown
            ? '剩余专注时长'
            : '已持续专注'}
      </Text>
    </View>
  );
}
