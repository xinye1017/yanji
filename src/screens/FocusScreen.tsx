/**
 * Paper & Chrono — Focus.
 *
 * The native session and tick remain the only clock. The FocusTimerReadout
 * leaf alone subscribes to second ticks; neither AppShell nor this parent
 * redraws on a second boundary. A note may be captured without pausing.
 */
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Pressable,
  ScrollView,
  Text,
  View,
  useWindowDimensions,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { YanjiDataNative, YanjiTimerNative } from '../bridge';
import type { ActiveSessionState, Subject, TimerMode } from '../bridge';
import {
  YanjiEditorialHeader,
  YanjiIcon,
  YanjiPaperSectionHeading,
  YanjiPaperSegmentedControl,
} from '../components/YanjiUI';
import { FocusVernierDial } from '../components/FocusVernierDial';
import { RecordMomentModal } from '../components/RecordMomentModal';
import { useNavigationActions, useNavigationState } from '../navigation/NavigationShell';
import { useBottomTabLayout } from '../navigation/useBottomTabLayout';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { syncActiveSession, useActiveSession, useTimerTick } from '../state/timerStore';

const DURATION_PRESETS = [25, 45, 60, 90] as const;
const MIN_MINUTES = 5;
const MAX_MINUTES = 180;
const DEFAULT_MINUTES = 45;
const TIMER_MODES: ReadonlyArray<{ value: TimerMode; label: string }> = [
  { value: 'COUNTDOWN', label: '倒计时' },
  { value: 'STOPWATCH', label: '正计时' },
];

interface TaskBinding {
  taskId: string;
  subjectId: string;
  title: string;
}

function formatClock(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds));
  const hours = Math.floor(safe / 3600);
  const minutes = String(Math.floor((safe % 3600) / 60)).padStart(2, '0');
  const seconds = String(safe % 60).padStart(2, '0');
  return hours > 0 ? `${hours}:${minutes}:${seconds}` : `${minutes}:${seconds}`;
}

function todayIso(): string {
  const now = new Date();
  return [now.getFullYear(), String(now.getMonth() + 1).padStart(2, '0'), String(now.getDate()).padStart(2, '0')].join('-');
}

export function FocusScreen({ visible }: { visible: boolean }): React.JSX.Element {
  const theme = useYanjiTheme();
  const paper = theme.paper;
  const { contentPadding } = useBottomTabLayout();
  const safe = useSafeAreaInsets();
  const { width } = useWindowDimensions();
  const [date] = useState(todayIso);
  const session = useActiveSession(visible);

  const [subjects, setSubjects] = useState<Subject[]>([]);
  const [subjectLoadState, setSubjectLoadState] = useState<'loading' | 'ready' | 'error'>('loading');
  const [selectedSubjectId, setSelectedSubjectId] = useState<string | null>(null);
  const [durationMinutes, setDurationMinutes] = useState(DEFAULT_MINUTES);
  const [timerMode, setTimerMode] = useState<TimerMode>('COUNTDOWN');
  const [recordOpen, setRecordOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [taskBinding, setTaskBinding] = useState<TaskBinding | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const { focusPreset } = useNavigationState();
  const { clearFocusPreset, selectTab } = useNavigationActions();

  const loadSubjects = useCallback(() => {
    setSubjectLoadState('loading');
    void YanjiDataNative.getSubjects()
      .then(list => {
        // Enabled terminal subjects include top-level courses with no enabled
        // children. Previously !isCategory hid legitimate English/Politics.
        const selectable = list.filter(subject =>
          subject.enabled &&
          !list.some(child => child.enabled && child.parentId === subject.id)
        );
        setSubjects(selectable);
        setSelectedSubjectId(previous =>
          selectable.some(subject => subject.id === previous)
            ? previous : selectable[0]?.id ?? null
        );
        setSubjectLoadState('ready');
      })
      .catch(() => {
        setSubjects([]);
        setSubjectLoadState('error');
      });
  }, []);

  useEffect(() => { loadSubjects(); }, [loadSubjects]);
  useEffect(() => { setActionError(null); }, [session?.sessionId]);

  useEffect(() => {
    if (!focusPreset) return;
    clearFocusPreset();
    const planned = focusPreset.plannedMinutes;
    // Do NOT silently round a real task's planned time to the nearest mark.
    // The dial projects it for display only until the user changes the scale.
    setDurationMinutes(
      Number.isFinite(planned) && planned >= MIN_MINUTES
        ? Math.min(MAX_MINUTES, planned)
        : DEFAULT_MINUTES
    );
    setTimerMode('COUNTDOWN');
    setSelectedSubjectId(focusPreset.subjectId);
    setTaskBinding({
      taskId: focusPreset.taskId,
      subjectId: focusPreset.subjectId,
      title: focusPreset.title,
    });
    setActionError(null);
  }, [focusPreset, clearFocusPreset]);

  // A subject can become unavailable while a task preset is in flight.
  // Never start a session with a task id from another subject.
  useEffect(() => {
    if (subjectLoadState === 'ready' && taskBinding && selectedSubjectId
        && taskBinding.subjectId !== selectedSubjectId) {
      setTaskBinding(null);
    }
  }, [selectedSubjectId, subjectLoadState, taskBinding]);

  const selectedSubject = useMemo(
    () => subjects.find(subject => subject.id === selectedSubjectId) ?? null,
    [subjects, selectedSubjectId]
  );

  const handleStart = useCallback(async () => {
    if (busy || !selectedSubject) return;
    setBusy(true);
    setActionError(null);
    try {
      await YanjiTimerNative.startFocus(
        selectedSubject.id,
        selectedSubject.name,
        timerMode,
        '',
        taskBinding?.subjectId === selectedSubject.id ? taskBinding.taskId : null,
        durationMinutes
      );
      setTaskBinding(null);
      await syncActiveSession();
    } catch (error) {
      const code = (error as { code?: string } | null | undefined)?.code;
      setActionError(code === 'E_SESSION_ACTIVE'
        ? '已有专注正在进行，先结束或放弃当前会话'
        : '启动失败，请重试');
      await syncActiveSession();
    } finally {
      setBusy(false);
    }
  }, [busy, durationMinutes, selectedSubject, taskBinding, timerMode]);

  const handleTimerAction = useCallback(async (action: 'pause' | 'resume' | 'complete' | 'discard') => {
    if (busy) return;
    setBusy(true);
    setActionError(null);
    try {
      if (action === 'pause') await YanjiTimerNative.pauseTimer();
      if (action === 'resume') await YanjiTimerNative.resumeTimer();
      if (action === 'complete') await YanjiTimerNative.completeTimer();
      if (action === 'discard') await YanjiTimerNative.discardTimer();
      await syncActiveSession();
    } catch {
      setActionError('计时操作失败，请重试');
      await syncActiveSession();
    } finally {
      setBusy(false);
    }
  }, [busy]);

  const confirmDiscard = useCallback(() => {
    if (busy) return;
    Alert.alert(
      '放弃本轮专注？',
      '这会丢弃本轮计时记录，无法恢复。',
      [
        { text: '取消', style: 'cancel' },
        { text: '确认放弃', style: 'destructive', onPress: () => { void handleTimerAction('discard'); } },
      ],
      { cancelable: true },
    );
  }, [busy, handleTimerAction]);

  if (!session) {
    return (
      <View testID="focus-ready" style={{ flex: 1, backgroundColor: paper.colors.canvas }}>
        <ScrollView
          testID="scroll-focus"
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
          contentContainerStyle={{
            paddingHorizontal: paper.layout.pageGutter,
            paddingTop: 18,
            paddingBottom: contentPadding,
          }}
        >
          <YanjiEditorialHeader
            eyebrow="PAPER & CHRONO · 寸阴"
            title="专注"
            subtitle="把时间留给眼前这一件事。"
          />

          <YanjiPaperSectionHeading title="选择科目" />
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 9 }}>
            {subjects.map(subject => {
              const selected = selectedSubjectId === subject.id;
              return (
                <Pressable
                  key={subject.id}
                  onPress={() => {
                    setSelectedSubjectId(subject.id);
                    setTaskBinding(previous => previous?.subjectId === subject.id ? previous : null);
                    setActionError(null);
                  }}
                  accessibilityRole="button"
                  accessibilityLabel={subject.name}
                  accessibilityState={{ selected }}
                  style={({ pressed }) => ({
                    minHeight: paper.layout.rowMinHeight,
                    justifyContent: 'center',
                    maxWidth: '100%',
                    paddingHorizontal: 17,
                    borderRadius: paper.radius.pill,
                    backgroundColor: selected ? paper.colors.subtle : paper.colors.surface,
                    borderWidth: 1,
                    borderColor: selected ? paper.colors.brand : paper.colors.hairline,
                    opacity: pressed ? 0.72 : 1,
                  })}
                >
                  <Text numberOfLines={2} style={[
                    paper.typography.bodyPrimary,
                    { color: selected ? paper.colors.brandInk : paper.colors.ink, flexShrink: 1 },
                  ]}>
                    {subject.name}
                  </Text>
                </Pressable>
              );
            })}
          </View>
          {subjects.length === 0 ? (
            <View style={{ minHeight: paper.layout.rowMinHeight, alignItems: 'flex-start', justifyContent: 'center' }}>
              <Text style={[paper.typography.bodyPrimary, { color: paper.colors.secondary }]}>
                {subjectLoadState === 'loading' ? '正在读取科目…' :
                  subjectLoadState === 'error' ? '科目读取失败' : '暂无可用科目'}
              </Text>
              {subjectLoadState === 'error' ? (
                <Pressable
                  onPress={loadSubjects}
                  accessibilityRole="button"
                  accessibilityLabel="重新读取科目"
                  style={{ minHeight: paper.layout.rowMinHeight, justifyContent: 'center' }}
                >
                  <Text style={[paper.typography.bodyPrimary, { color: paper.colors.brandInk }]}>重新读取科目</Text>
                </Pressable>
              ) : null}
            </View>
          ) : null}

          <YanjiPaperSectionHeading title="计时方式" />
          <YanjiPaperSegmentedControl
            value={timerMode}
            options={TIMER_MODES}
            onChange={setTimerMode}
            accessibilityLabel="计时模式"
          />

          <View style={{
            marginTop: paper.layout.sectionGap + 12,
            alignItems: 'center',
            paddingBottom: 16,
            borderBottomWidth: paper.layout.hairlineWidth,
            borderColor: paper.colors.hairline,
          }}>
            {timerMode === 'COUNTDOWN' ? (
              <>
                <Text style={[paper.typography.captionMeta, { color: paper.colors.secondary }]}>
                  计划专注时长
                </Text>
                <View style={{ flexDirection: 'row', alignItems: 'baseline', marginTop: 7 }}>
                  <Text
                    testID="focus-duration-value"
                    adjustsFontSizeToFit
                    minimumFontScale={0.55}
                    numberOfLines={1}
                    style={[paper.typography.displayDial, {
                      color: paper.colors.ink,
                      fontSize: Math.min(paper.typography.displayDial.fontSize, Math.max(52, width * 0.23)),
                      textAlign: 'center',
                    }]}
                  >
                    {durationMinutes}
                  </Text>
                  <Text style={[paper.typography.bodyPrimary, { marginLeft: 7, color: paper.colors.secondary }]}>分钟</Text>
                </View>
                <View style={{ flexDirection: 'row', justifyContent: 'center', flexWrap: 'wrap', gap: 8, marginTop: 6 }}>
                  {DURATION_PRESETS.map(minutes => (
                    <Pressable
                      key={minutes}
                      accessibilityRole="button"
                      accessibilityLabel={`预设 ${minutes} 分钟`}
                      accessibilityState={{ selected: minutes === durationMinutes }}
                      onPress={() => setDurationMinutes(minutes)}
                      style={({ pressed }) => ({
                        minWidth: 59, minHeight: 44, alignItems: 'center', justifyContent: 'center',
                        paddingHorizontal: 11, borderRadius: paper.radius.pill,
                        backgroundColor: minutes === durationMinutes ? paper.colors.brand : paper.colors.subtle,
                        opacity: pressed ? 0.72 : 1,
                      })}
                    >
                      <Text style={[paper.typography.captionMeta, {
                        fontWeight: '700',
                        color: minutes === durationMinutes ? paper.colors.onBrand : paper.colors.secondary,
                      }]}>
                        {minutes}m
                      </Text>
                    </Pressable>
                  ))}
                </View>
              </>
            ) : (
              <>
                <Text style={[paper.typography.titleEditorial, { color: paper.colors.ink, textAlign: 'center' }]}>正计时</Text>
                <Text style={[paper.typography.bodyPrimary, {
                  color: paper.colors.secondary, textAlign: 'center', marginTop: 7,
                }]}>
                  自由计时，不设时间上限；可暂停后随时结算。
                </Text>
              </>
            )}
          </View>

          {timerMode === 'COUNTDOWN' ? (
            <View style={{ marginTop: 12 }}>
              <Text style={[paper.typography.captionMeta, { color: paper.colors.secondary, textAlign: 'center' }]}>寸阴尺 · 左右滑动选择</Text>
              <FocusVernierDial
                valueMinutes={durationMinutes}
                onChangeMinutes={setDurationMinutes}
                minMinutes={MIN_MINUTES}
                maxMinutes={MAX_MINUTES}
                stepMinutes={5}
                accessibilityLabel="调整倒计时长度"
              />
            </View>
          ) : null}

          {taskBinding ? (
            <View style={{
              marginTop: paper.layout.sectionGap,
              flexDirection: 'row', alignItems: 'center',
              borderTopWidth: 1, borderBottomWidth: 1,
              borderColor: paper.colors.hairline, paddingVertical: 9,
            }}>
              <YanjiIcon name="tasks" size={17} color={paper.colors.brandInk} />
              <Text numberOfLines={2} style={[paper.typography.bodyPrimary, {
                color: paper.colors.secondary, flex: 1, marginHorizontal: 9,
              }]}>
                关联任务 · {taskBinding.title}
              </Text>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel="取消关联任务"
                hitSlop={8}
                onPress={() => setTaskBinding(null)}
                style={{ minWidth: 48, minHeight: 48, alignItems: 'center', justifyContent: 'center' }}
              >
                <YanjiIcon name="close" size={18} color={paper.colors.secondary} />
              </Pressable>
            </View>
          ) : null}

          <View style={{ marginTop: paper.layout.sectionGap + 6 }}>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={busy ? '正在启动专注' : '开始专注'}
              disabled={busy || !selectedSubject}
              onPress={handleStart}
              style={({ pressed }) => ({
                minHeight: 58,
                flexDirection: 'row', justifyContent: 'center', alignItems: 'center',
                gap: 9,
                borderRadius: paper.radius.pill,
                backgroundColor: busy || !selectedSubject ? paper.colors.subtle : paper.colors.brand,
                opacity: pressed ? 0.85 : 1,
              })}
            >
              <YanjiIcon name="play" size={20} color={busy || !selectedSubject ? paper.colors.secondary : paper.colors.onBrand} />
              <Text style={{ fontSize: 17, fontWeight: '700',
                color: busy || !selectedSubject ? paper.colors.secondary : paper.colors.onBrand,
              }}>
                {busy ? '启动中…' : '开始专注'}
              </Text>
            </Pressable>
          </View>
          {actionError ? (
            <Text accessibilityRole="alert" style={[paper.typography.bodyPrimary, {
              color: paper.colors.danger, textAlign: 'center', marginTop: 12,
            }]}>
              {actionError}
            </Text>
          ) : null}

          <RecordMomentLink onPress={() => setRecordOpen(true)} />
        </ScrollView>
        <RecordMomentModal visible={recordOpen} date={date} onClose={() => setRecordOpen(false)} />
      </View>
    );
  }

  // Active / paused share the same full-height ink field. The navigation
  // intentionally does not reserve its glass dock inset in this state.
  const isPaused = session.isPaused;
  const immersed = isPaused ? paper.immersive.paused : paper.immersive.running;

  return (
    <View testID={isPaused ? 'focus-paused' : 'focus-running'} style={{
      flex: 1,
      backgroundColor: immersed.canvas,
    }}>
      <ScrollView
        showsVerticalScrollIndicator={false}
        contentContainerStyle={{
          flexGrow: 1,
          justifyContent: 'space-between',
          alignItems: 'stretch',
          paddingHorizontal: paper.layout.pageGutter,
          paddingTop: 28,
          paddingBottom: Math.max(safe.bottom, 16) + 26,
        }}
      >
        <View style={{ alignItems: 'center', gap: 8 }}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="返回今天，专注继续在后台运行"
            onPress={() => selectTab('today')}
            style={{ minHeight: 48, alignSelf: 'flex-start', flexDirection: 'row', gap: 6, alignItems: 'center' }}
          >
            <YanjiIcon name="back" size={17} color={immersed.secondary} />
            <Text style={[paper.typography.captionMeta, { color: immersed.secondary }]}>返回今天</Text>
          </Pressable>
          <Text style={[paper.typography.captionMeta, {
            color: immersed.secondary, letterSpacing: 1.5, textAlign: 'center',
          }]}>
            {isPaused ? 'TIME AT REST · 暂歇' : 'TIME IN FOCUS · 此刻'}
          </Text>
          <Text
            numberOfLines={2}
            style={[paper.typography.titleEditorial, {
              color: immersed.ink, textAlign: 'center', maxWidth: '90%',
            }]}
          >
            {session.subjectName}
          </Text>
        </View>

        <View style={{ alignItems: 'center', paddingVertical: 34 }}>
          <FocusTimerReadout session={session} visible={visible} />
        </View>

        <View style={{ alignSelf: 'stretch', gap: 13 }}>
          {isPaused ? (
            <>
              <ImmersiveAction label={busy ? '处理中…' : '继续专注'} icon="play" variant="filled"
                disabled={busy} onPress={() => { void handleTimerAction('resume'); }} />
              <View style={{ flexDirection: 'row', gap: 12 }}>
                <View style={{ flex: 1 }}>
                  <ImmersiveAction label="提前结束" icon="check" disabled={busy}
                    onPress={() => { void handleTimerAction('complete'); }} />
                </View>
                <View style={{ flex: 1 }}>
                  <ImmersiveAction label="放弃本轮" icon="delete" disabled={busy}
                    onPress={confirmDiscard} />
                </View>
              </View>
            </>
          ) : (
            <ImmersiveAction label={busy ? '处理中…' : '暂停专注'} icon="pause" variant="filled"
              disabled={busy} onPress={() => { void handleTimerAction('pause'); }} />
          )}
          {actionError ? (
            <Text accessibilityRole="alert" style={[paper.typography.bodyPrimary, {
              color: paper.immersive.running.accent, textAlign: 'center',
            }]}>
              {actionError}
            </Text>
          ) : null}
          <RecordMomentLink onPress={() => setRecordOpen(true)} immersiveColor={immersed.secondary} />
        </View>
      </ScrollView>
      <RecordMomentModal visible={recordOpen} date={date} onClose={() => setRecordOpen(false)} />
    </View>
  );
}

/** Isolated second-rate native tick subscriber, not an AppShell subscription. */
function FocusTimerReadout({
  session,
  visible,
}: {
  session: ActiveSessionState;
  visible: boolean;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const { width } = useWindowDimensions();
  const tick = useTimerTick(visible);
  const seconds = session.isCountdown
    ? Math.max(0, tick?.remainingSeconds ?? session.remainingSeconds)
    : (tick?.elapsedSeconds ?? session.elapsedSeconds);
  const elapsed = tick?.elapsedSeconds ?? session.elapsedSeconds;
  const remaining = Math.max(0, tick?.remainingSeconds ?? session.remainingSeconds);
  const total = elapsed + remaining;
  const progress = session.isCountdown && total > 0
    ? Math.max(0, Math.min(1, elapsed / total))
    : null;
  const paused = session.isPaused || tick?.isPaused === true;
  const immersed = paused ? theme.paper.immersive.paused : theme.paper.immersive.running;
  return (
    <View style={{ alignItems: 'center', alignSelf: 'stretch' }}>
      <Text
        testID="focus-native-clock"
        adjustsFontSizeToFit
        minimumFontScale={0.43}
        numberOfLines={1}
        style={[theme.paper.typography.displayTimer, {
          color: immersed.ink,
          maxWidth: Math.max(180, width - 2 * theme.paper.layout.pageGutter),
          fontSize: Math.min(theme.paper.typography.displayTimer.fontSize, Math.max(56, width * 0.235)),
          textAlign: 'center',
        }]}
      >
        {formatClock(seconds)}
      </Text>
      <Text style={[theme.paper.typography.bodyPrimary, {
        color: immersed.secondary,
        marginTop: 10, textAlign: 'center',
      }]}>
        {paused
          ? '已暂停 · 随时继续'
          : session.isCountdown
            ? '距离本轮完成'
            : '专注进行中 · 正计时'}
      </Text>
      {progress !== null ? (
        <View
          accessibilityRole="progressbar"
          accessibilityLabel="倒计时进度"
          accessibilityValue={{ min: 0, max: 100, now: Math.round(progress * 100) }}
          style={{
            width: '100%', maxWidth: 300, height: 2, marginTop: 32,
            backgroundColor: immersed.subtle, overflow: 'hidden', borderRadius: theme.paper.radius.tick,
          }}
        >
          <View style={{ height: 2, width: `${progress * 100}%`, backgroundColor: immersed.accent }} />
        </View>
      ) : (
        <View style={{
          width: 54, height: 2, marginTop: 32, backgroundColor: immersed.accent, opacity: 0.85,
        }} />
      )}
    </View>
  );
}

function ImmersiveAction({
  label,
  icon,
  onPress,
  disabled = false,
  variant = 'outline',
}: {
  label: string;
  icon: 'play' | 'pause' | 'check' | 'delete';
  onPress: () => void;
  disabled?: boolean;
  variant?: 'filled' | 'outline';
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const filled = variant === 'filled';
  const immersed = theme.paper.immersive.running;
  const active = theme.paper.immersive.paused;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      onPress={onPress}
      disabled={disabled}
      style={({ pressed }) => ({
        minHeight: 58,
        borderRadius: theme.paper.radius.pill,
        alignItems: 'center',
        justifyContent: 'center',
        flexDirection: 'row',
        gap: 8,
        backgroundColor: filled ? (icon === 'play' ? active.accent : immersed.accent) : 'transparent',
        borderWidth: filled ? 0 : 1,
        borderColor: 'rgba(255,255,255,0.28)',
        opacity: disabled ? 0.45 : pressed ? 0.76 : 1,
      })}
    >
      <YanjiIcon name={icon} size={18} color={filled ? immersed.canvas : immersed.ink} />
      <Text style={{ fontSize: 15, fontWeight: '700', color: filled ? immersed.canvas : immersed.ink }}>
        {label}
      </Text>
    </Pressable>
  );
}

function RecordMomentLink({
  onPress,
  immersiveColor,
}: {
  onPress: () => void;
  immersiveColor?: string;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const color = immersiveColor ?? theme.paper.colors.brandInk;
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel="记录此刻"
      hitSlop={8}
      style={({ pressed }) => ({
        minHeight: theme.paper.layout.rowMinHeight,
        alignItems: 'center',
        justifyContent: 'center',
        flexDirection: 'row',
        gap: 7,
        opacity: pressed ? 0.68 : 1,
        marginTop: 4,
      })}
    >
      <YanjiIcon name="compose" size={17} color={color} />
      <Text style={[theme.paper.typography.bodyPrimary, { color }]}>记录此刻</Text>
    </Pressable>
  );
}
