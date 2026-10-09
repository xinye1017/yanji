/**
 * Yanji "Focus" screen — the immersive timer surface.
 *
 * Contract (PROJECT.md § R2 / user brief §04):
 * - The large timer digits are the visual centre; everything else is restrained.
 * - The authoritative time fact lives in Kotlin (TimerMachine over
 *   SystemClock.elapsedRealtime). JS never runs its own timer — it only renders
 *   the `onTimerTick` payload pushed from the native layer.
 * - Pause / resume / complete / discard map to the native service actions, so
 *   the foreground notification, lock screen and process-death recovery all stay
 *   in sync with what the user sees here.
 * - Record Moment opens without pausing the timer.
 */

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import {
  YanjiDataNative,
  YanjiTimerNative,
  onTimerStateChanged,
  onTimerTick,
} from '../bridge';
import type { ActiveSessionState, Subject, TimerMode, TimerTickEvent } from '../bridge';
import {
  YanjiBreathButton,
  YanjiChip,
  YanjiIcon,
  YanjiPrimaryButton,
  YanjiProgressRing,
  YanjiSectionHeader,
  YanjiStepper,
} from '../components/YanjiUI';
import { RecordMomentModal } from '../components/RecordMomentModal';
import { useNavigation } from '../navigation/NavigationShell';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiRadius, YanjiSpacing, YanjiTouch } from '../theme/tokens';

function formatClock(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds));
  const hours = Math.floor(safe / 3600);
  const minutes = `${Math.floor((safe % 3600) / 60)}`.padStart(2, '0');
  const seconds = `${safe % 60}`.padStart(2, '0');
  return hours > 0 ? `${hours}:${minutes}:${seconds}` : `${minutes}:${seconds}`;
}

function todayIso(): string {
  const now = new Date();
  const month = `${now.getMonth() + 1}`.padStart(2, '0');
  const day = `${now.getDate()}`.padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

const DURATION_PRESETS = [25, 45, 60, 90] as const;

/** 计时模式选择项：正计时不限时长，倒计时走时长预设。 */
const TIMER_MODES: ReadonlyArray<{ mode: TimerMode; label: string }> = [
  { mode: 'COUNTDOWN', label: '倒计时' },
  { mode: 'STOPWATCH', label: '正计时' },
];

/** Task the Focus tab will bind the next session to (handed over from Today). */
interface TaskBinding {
  taskId: string;
  subjectId: string;
  title: string;
}

/** Bounds for a duration preset handed over from a task's planned minutes. */
const MIN_MINUTES = 5;
const MAX_MINUTES = 180;
const DEFAULT_MINUTES = 45;

/** Diameter of the running-session progress ring, in dp. */
const RING_SIZE = 248;
const RING_THICKNESS = 10;

export function FocusScreen(): React.JSX.Element {
  const theme = useYanjiTheme();
  const [date] = useState(todayIso);

  const [session, setSession] = useState<ActiveSessionState | null>(null);
  const [tick, setTick] = useState<TimerTickEvent | null>(null);
  const [subjects, setSubjects] = useState<Subject[]>([]);
  const [selectedSubjectId, setSelectedSubjectId] = useState<string | null>(null);
  const [durationMinutes, setDurationMinutes] = useState(DEFAULT_MINUTES);
  const [timerMode, setTimerMode] = useState<TimerMode>('COUNTDOWN');
  const [recordOpen, setRecordOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [taskBinding, setTaskBinding] = useState<TaskBinding | null>(null);
  const [startError, setStartError] = useState<string | null>(null);
  const { focusPreset, clearFocusPreset } = useNavigation();

  const refreshSession = useCallback(async () => {
    try {
      setSession(await YanjiTimerNative.getActiveSession());
    } catch {
      setSession(null);
    }
  }, []);

  useEffect(() => {
    void refreshSession();
    YanjiDataNative.getSubjects()
      .then(list => {
        const selectable = list.filter(s => s.enabled && !s.isCategory);
        setSubjects(selectable);
        setSelectedSubjectId(prev => prev ?? selectable[0]?.id ?? null);
      })
      .catch(() => setSubjects([]));
  }, [refreshSession]);

  useEffect(() => {
    const unsubTick = onTimerTick(event => setTick(event));
    const unsubState = onTimerStateChanged(event => {
      setSession(event.session);
      setStartError(null);
      void refreshSession();
    });
    return () => {
      unsubTick();
      unsubState();
    };
  }, [refreshSession]);

  // ---- Task handed over from the Today tab -------------------------------
  useEffect(() => {
    if (!focusPreset) return;
    clearFocusPreset();
    const planned = Math.round(focusPreset.plannedMinutes);
    setDurationMinutes(planned >= MIN_MINUTES ? Math.min(MAX_MINUTES, planned) : DEFAULT_MINUTES);
    // 任务自带计划时长，绑定任务必须走倒计时：正计时不限时长会让
    // 「实际 / 计划」对照失去意义。
    setTimerMode('COUNTDOWN');
    setSelectedSubjectId(focusPreset.subjectId);
    setTaskBinding({
      taskId: focusPreset.taskId,
      subjectId: focusPreset.subjectId,
      title: focusPreset.title,
    });
    setStartError(null);
  }, [focusPreset, clearFocusPreset]);

  const selectedSubject = useMemo(
    () => subjects.find(s => s.id === selectedSubjectId) ?? null,
    [subjects, selectedSubjectId]
  );

  const displaySeconds = session
    ? session.isCountdown
      ? tick?.remainingSeconds ?? session.remainingSeconds
      : tick?.elapsedSeconds ?? session.elapsedSeconds
    : 0;

  const isPaused = session?.isPaused ?? false;

  /**
   * Countdown progress, derived only from what the native layer already
   * reports: elapsed + remaining is the planned total. Stopwatch sessions have
   * no planned total, so they get no ring rather than a fabricated one.
   */
  const ringProgress = useMemo(() => {
    if (!session || !session.isCountdown) return null;
    const elapsed = tick?.elapsedSeconds ?? session.elapsedSeconds;
    const remaining = Math.max(0, tick?.remainingSeconds ?? session.remainingSeconds);
    const total = elapsed + remaining;
    if (total <= 0) return null;
    return elapsed / total;
  }, [session, tick]);

  const handleStart = useCallback(async () => {
    if (busy || !selectedSubject) return;
    setBusy(true);
    setStartError(null);
    try {
      await YanjiTimerNative.startFocus(
        selectedSubject.id,
        selectedSubject.name,
        timerMode,
        '',
        taskBinding?.taskId ?? null,
        durationMinutes
      );
      setTaskBinding(null);
      await refreshSession();
    } catch (error) {
      const code = (error as { code?: string } | null | undefined)?.code;
      setStartError(
        code === 'E_SESSION_ACTIVE' ? '已有专注正在进行，先结束或放弃当前会话' : '启动失败，请重试'
      );
      // Resynchronise from the authoritative native state.
      await refreshSession();
    } finally {
      setBusy(false);
    }
  }, [busy, selectedSubject, durationMinutes, timerMode, taskBinding, refreshSession]);

  const handlePauseResume = useCallback(async () => {
    try {
      if (isPaused) {
        await YanjiTimerNative.resumeTimer();
      } else {
        await YanjiTimerNative.pauseTimer();
      }
    } catch {
      // Ignore — the next state event will resynchronise.
    }
  }, [isPaused]);

  const handleComplete = useCallback(async () => {
    try {
      await YanjiTimerNative.completeTimer();
      await refreshSession();
    } catch {
      // Ignore.
    }
  }, [refreshSession]);

  const handleDiscard = useCallback(async () => {
    try {
      await YanjiTimerNative.discardTimer();
      await refreshSession();
    } catch {
      // Ignore.
    }
  }, [refreshSession]);

  // ---- Preparation state -------------------------------------------------
  if (!session) {
    return (
      <View style={{ flex: 1, backgroundColor: theme.colors.bgPrimary }}>
        <ScrollView
          contentContainerStyle={{
            paddingHorizontal: YanjiSpacing.page,
            paddingTop: YanjiSpacing.md,
            paddingBottom: 120,
          }}
        >
          <Text
            style={[
              theme.typography.pageTitle,
              { color: theme.colors.textPrimary, letterSpacing: -0.4 },
            ]}
          >
            专注
          </Text>

          <YanjiSectionHeader title="科目" />
          <View style={{ flexDirection: 'row', flexWrap: 'wrap' }}>
            {subjects.map(subject => (
              <YanjiChip
                key={subject.id}
                label={subject.name}
                selected={subject.id === selectedSubjectId}
                onPress={() => {
                  setSelectedSubjectId(subject.id);
                  // A session bound to a different task must not silently
                  // pick up this subject.
                  setTaskBinding(prev =>
                    prev && prev.subjectId === subject.id ? prev : null
                  );
                }}
              />
            ))}
            {subjects.length === 0 ? (
              <Text style={{ color: theme.colors.textTertiary, fontSize: 13 }}>暂无可用科目</Text>
            ) : null}
          </View>

          <YanjiSectionHeader title="模式" />
          <View style={{ flexDirection: 'row' }}>
            {TIMER_MODES.map(entry => (
              <YanjiChip
                key={entry.mode}
                label={entry.label}
                selected={entry.mode === timerMode}
                onPress={() => setTimerMode(entry.mode)}
                style={{ marginRight: 0, paddingHorizontal: 20 }}
              />
            ))}
          </View>

          {timerMode === 'COUNTDOWN' ? (
            <>
              <YanjiSectionHeader title="时长" />
              <View style={{ flexDirection: 'row', flexWrap: 'wrap' }}>
                {DURATION_PRESETS.map(minutes => (
                  <YanjiChip
                    key={minutes}
                    label={`${minutes} 分钟`}
                    selected={minutes === durationMinutes}
                    onPress={() => setDurationMinutes(minutes)}
                  />
                ))}
              </View>
              <View style={{ marginTop: YanjiSpacing.sm }}>
                <YanjiStepper
                  label="自定义时长"
                  value={durationMinutes}
                  onChange={setDurationMinutes}
                  min={MIN_MINUTES}
                  max={MAX_MINUTES}
                  step={5}
                  suffix=" 分钟"
                />
              </View>
            </>
          ) : (
            <Text
              style={[
                theme.typography.caption,
                { color: theme.colors.textTertiary, marginTop: YanjiSpacing.md },
              ]}
            >
              正计时不限时长，随时手动结束
            </Text>
          )}

          {taskBinding ? (
            <View
              style={{
                flexDirection: 'row',
                alignItems: 'center',
                marginTop: YanjiSpacing.md,
              }}
            >
              <YanjiIcon name="tasks" size={14} color={theme.colors.textTertiary} />
              <Text
                style={[
                  theme.typography.caption,
                  { color: theme.colors.textTertiary, marginLeft: 6 },
                ]}
              >
                将关联任务：{taskBinding.title}
              </Text>
            </View>
          ) : null}

          <View style={{ marginTop: YanjiSpacing.xxl }}>
            <YanjiBreathButton
              icon="play"
              label={busy ? '启动中' : '开始专注'}
              onPress={handleStart}
              disabled={busy || !selectedSubject}
            />
          </View>

          {startError ? (
            <Text
              style={[
                theme.typography.caption,
                { color: theme.colors.danger, marginTop: YanjiSpacing.md },
              ]}
            >
              {startError}
            </Text>
          ) : null}

          <RecordMomentLink onPress={() => setRecordOpen(true)} />
        </ScrollView>

        <RecordMomentModal
          visible={recordOpen}
          date={date}
          onClose={() => setRecordOpen(false)}
        />
      </View>
    );
  }

  // ---- Running / paused state -------------------------------------------
  return (
    <View style={{ flex: 1, backgroundColor: theme.colors.bgPrimary }}>
      <View
        style={{
          flex: 1,
          justifyContent: 'center',
          alignItems: 'center',
          paddingHorizontal: YanjiSpacing.page,
        }}
      >
        <Text style={[theme.typography.label, { color: theme.colors.textSecondary }]}>
          {session.subjectName}
        </Text>

        <View style={{ marginTop: YanjiSpacing.lg }}>
          <YanjiProgressRing
            progress={ringProgress ?? 0}
            size={RING_SIZE}
            thickness={RING_THICKNESS}
            color={isPaused ? theme.colors.textDisabled : theme.colors.accentPrimary}
          >
            <Text
              style={[
                theme.typography.timerDisplay,
                {
                  color: theme.colors.textPrimary,
                  fontVariant: ['tabular-nums'],
                },
              ]}
            >
              {formatClock(displaySeconds)}
            </Text>
          </YanjiProgressRing>
        </View>

        <Text
          style={[
            theme.typography.caption,
            { color: theme.colors.textTertiary, marginTop: YanjiSpacing.md },
          ]}
        >
          {isPaused
            ? '已暂停'
            : session.isCountdown
            ? ringProgress === null
              ? '倒计时'
              : `剩余 ${Math.round((1 - ringProgress) * 100)}%`
            : '正计时'}
        </Text>

        <View style={{ marginTop: YanjiSpacing.xxl, alignSelf: 'stretch' }}>
          <YanjiPrimaryButton
            icon={isPaused ? 'play' : 'pause'}
            label={isPaused ? '恢复' : '暂停'}
            onPress={handlePauseResume}
          />
        </View>

        <View
          style={{
            flexDirection: 'row',
            marginTop: YanjiSpacing.md,
            alignSelf: 'stretch',
          }}
        >
          <View style={{ flex: 1, marginRight: YanjiSpacing.sm }}>
            <YanjiPrimaryButton
              icon="check"
              label="结束"
              variant="secondary"
              onPress={handleComplete}
            />
          </View>
          <View style={{ flex: 1, marginLeft: YanjiSpacing.sm }}>
            <YanjiPrimaryButton
              icon="delete"
              label="放弃"
              variant="secondary"
              onPress={handleDiscard}
            />
          </View>
        </View>

        <RecordMomentLink onPress={() => setRecordOpen(true)} />
      </View>

      <RecordMomentModal
        visible={recordOpen}
        date={date}
        onClose={() => setRecordOpen(false)}
      />
    </View>
  );
}

/**
 * Secondary "record this moment" affordance. Uses the danger-free secondary
 * tier (not the primary) so it never competes with the timer controls.
 */
function RecordMomentLink({ onPress }: { onPress: () => void }): React.JSX.Element {
  const theme = useYanjiTheme();
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel="记录此刻"
      hitSlop={8}
      style={({ pressed }) => ({
        marginTop: YanjiSpacing.xl,
        paddingVertical: YanjiSpacing.md,
        paddingHorizontal: YanjiSpacing.lg,
        flexDirection: 'row',
        alignItems: 'center',
        borderRadius: YanjiRadius.full,
        backgroundColor: theme.colors.bgSurface,
        minHeight: YanjiTouch.min,
        opacity: pressed ? 0.7 : 1,
      })}
    >
      <YanjiIcon name="compose" size={15} color={theme.colors.accentPrimary} />
      <Text
        style={{
          color: theme.colors.accentPrimary,
          fontSize: 14,
          fontWeight: '600',
          marginLeft: 6,
        }}
      >
        记录此刻
      </Text>
    </Pressable>
  );
}
