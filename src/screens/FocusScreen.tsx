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
import { Pressable, ScrollView, Text, View, useWindowDimensions } from 'react-native';
import {
  YanjiDataNative,
  YanjiTimerNative,
} from '../bridge';
import type { ActiveSessionState, Subject, TimerMode } from '../bridge';
import {
  YanjiBreathButton,
  YanjiCard,
  YanjiChip,
  YanjiIcon,
  YanjiPrimaryButton,
  YanjiProgressRing,
  YanjiSectionHeader,
  YanjiStepper,
} from '../components/YanjiUI';
import { RecordMomentModal } from '../components/RecordMomentModal';
import { useNavigationActions, useNavigationState } from '../navigation/NavigationShell';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { useBottomTabLayout } from '../navigation/useBottomTabLayout';
import { YanjiRadius, YanjiSpacing, YanjiTouch } from '../theme/tokens';
import { syncActiveSession, useActiveSession, useTimerTick } from '../state/timerStore';

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
const RING_THICKNESS = 8;

export function FocusScreen({ visible }: { visible: boolean }): React.JSX.Element {
  const theme = useYanjiTheme();
  const { contentPadding } = useBottomTabLayout();
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
  const [startError, setStartError] = useState<string | null>(null);
  const { focusPreset } = useNavigationState();
  const { clearFocusPreset } = useNavigationActions();

  const loadSubjects = useCallback(() => {
    setSubjectLoadState('loading');
    void YanjiDataNative.getSubjects()
      .then(list => {
        const selectable = list.filter(s => s.enabled && !s.isCategory);
        setSubjects(selectable);
        setSelectedSubjectId(prev => selectable.some(s => s.id === prev) ? prev : selectable[0]?.id ?? null);
        setSubjectLoadState('ready');
      })
      .catch(() => {
        setSubjects([]);
        setSubjectLoadState('error');
      });
  }, []);

  useEffect(() => { loadSubjects(); }, [loadSubjects]);

  useEffect(() => {
    setStartError(null);
  }, [session?.sessionId]);

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

  const isPaused = session?.isPaused ?? false;

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
      await syncActiveSession();
    } catch (error) {
      const code = (error as { code?: string } | null | undefined)?.code;
      setStartError(
        code === 'E_SESSION_ACTIVE' ? '已有专注正在进行，先结束或放弃当前会话' : '启动失败，请重试'
      );
      // Resynchronise from the authoritative native state.
      await syncActiveSession();
    } finally {
      setBusy(false);
    }
  }, [busy, selectedSubject, durationMinutes, timerMode, taskBinding]);

  const handlePauseResume = useCallback(async () => {
    try {
      if (isPaused) {
        await YanjiTimerNative.resumeTimer();
      } else {
        await YanjiTimerNative.pauseTimer();
      }
      await syncActiveSession();
    } catch {
      // Ignore — the next state event will resynchronise.
    }
  }, [isPaused]);

  const handleComplete = useCallback(async () => {
    try {
      await YanjiTimerNative.completeTimer();
      await syncActiveSession();
    } catch {
      // Ignore.
    }
  }, []);

  const handleDiscard = useCallback(async () => {
    try {
      await YanjiTimerNative.discardTimer();
      await syncActiveSession();
    } catch {
      // Ignore.
    }
  }, []);

  // ---- Preparation state (Zen Timer Studio) ----------------------------
  if (!session) {
    return (
      <View style={{ flex: 1, backgroundColor: theme.colors.bgPrimary }}>
        <ScrollView
          testID="scroll-focus"
          style={{ flex: 1 }}
          contentContainerStyle={{
            paddingHorizontal: YanjiSpacing.page,
            paddingTop: YanjiSpacing.md,
            // Content scrolls behind the dock; its final action can clear the glass.
            paddingBottom: contentPadding,
          }}
        >
          <View style={{ marginBottom: YanjiSpacing.md }}>
            <Text
              style={[
                theme.typography.pageTitle,
                { color: theme.colors.textPrimary, letterSpacing: -0.5 },
              ]}
            >
              专注
            </Text>
            <Text
              style={[
                theme.typography.caption,
                { color: theme.colors.textTertiary, marginTop: 3 },
              ]}
            >
              选好科目和时间，就开始
            </Text>
          </View>

          {/* 科目选择：横向胶囊流 */}
          <View style={{ marginBottom: YanjiSpacing.md }}>
            <Text
              style={[
                theme.typography.label,
                { color: theme.colors.textSecondary, textTransform: 'uppercase', marginBottom: 8 },
              ]}
            >
              科目
            </Text>
            <ScrollView horizontal showsHorizontalScrollIndicator={false} style={{ flexDirection: 'row' }}>
              {subjects.map(subject => {
                const isSelected = subject.id === selectedSubjectId;
                return (
                  <Pressable
                    key={subject.id}
                    onPress={() => {
                      setSelectedSubjectId(subject.id);
                      setTaskBinding(prev => (prev && prev.subjectId === subject.id ? prev : null));
                    }}
                    accessibilityRole="button"
                    accessibilityState={{ selected: isSelected }}
                    accessibilityLabel={subject.name}
                    hitSlop={6}
                    style={({ pressed }) => ({
                      paddingVertical: 8,
                      paddingHorizontal: 16,
                      minHeight: YanjiTouch.comfortable,
                      justifyContent: 'center',
                      borderRadius: YanjiRadius.full,
                      marginRight: 8,
                      backgroundColor: isSelected ? theme.colors.accentSoft : theme.colors.bgSurface,
                      borderWidth: 1,
                      borderColor: isSelected ? theme.colors.accentPrimary : theme.colors.cardBorder,
                      opacity: pressed ? 0.75 : 1,
                    })}
                  >
                    <Text
                      style={{
                        color: isSelected ? theme.colors.accentPrimary : theme.colors.textSecondary,
                        fontSize: 14,
                        fontWeight: isSelected ? '700' : '500',
                      }}
                    >
                      {subject.name}
                    </Text>
                  </Pressable>
                );
              })}
              {subjects.length === 0 ? (
                <View style={{ flexDirection: 'row', alignItems: 'center', minHeight: YanjiTouch.comfortable }}>
                  <Text style={[theme.typography.caption, { color: subjectLoadState === 'error' ? theme.colors.danger : theme.colors.textSecondary, flexShrink: 1 }]}>
                    {subjectLoadState === 'loading' ? '正在读取科目…' : subjectLoadState === 'error' ? '科目读取失败' : '暂无可用科目，请在设置中添加'}
                  </Text>
                  {subjectLoadState === 'error' ? (
                    <Pressable onPress={loadSubjects} accessibilityRole="button" accessibilityLabel="重新读取科目" style={{ minHeight: YanjiTouch.comfortable, justifyContent: 'center', paddingHorizontal: YanjiSpacing.md }}>
                      <Text style={[theme.typography.bodyStrong, { color: theme.colors.accentPrimary }]}>重试</Text>
                    </Pressable>
                  ) : null}
                </View>
              ) : null}
            </ScrollView>
          </View>

          {/* 禅意计时台仪表卡 (Zen Studio Station) */}
          <YanjiCard variant="plain" style={{ paddingTop: theme.layout.titleGap }}>
            {/* 模式选择分段器 */}
            <View
              style={{
                flexDirection: 'row',
                backgroundColor: theme.colors.bgElevated,
                borderRadius: YanjiRadius.full,
                padding: 4,
              }}
            >
              {TIMER_MODES.map(entry => {
                const isSelected = entry.mode === timerMode;
                return (
                  <Pressable
                    key={entry.mode}
                    onPress={() => setTimerMode(entry.mode)}
                    accessibilityRole="tab"
                    accessibilityState={{ selected: isSelected }}
                    style={{
                      flex: 1,
                      alignItems: 'center',
                      justifyContent: 'center',
                      paddingVertical: 8,
                      minHeight: YanjiTouch.comfortable,
                      borderRadius: YanjiRadius.full,
                      backgroundColor: isSelected ? theme.colors.bgSurface : 'transparent',
                      borderWidth: 0,
                    }}
                  >
                    <Text
                      style={{
                        color: isSelected ? theme.colors.textPrimary : theme.colors.textTertiary,
                        fontSize: 13,
                        fontWeight: isSelected ? '700' : '500',
                      }}
                    >
                      {entry.label}
                    </Text>
                  </Pressable>
                );
              })}
            </View>

            {/* 时长设定区域 */}
            {timerMode === 'COUNTDOWN' ? (
              <View style={{ alignItems: 'center', marginTop: theme.layout.timerTopGap, marginBottom: YanjiSpacing.lg }}>
                <Text style={[theme.typography.caption, { color: theme.colors.textSecondary }]}>
                  计划专注时长
                </Text>
                <View style={{ flexDirection: 'row', alignItems: 'baseline', marginTop: 6, marginBottom: 16 }}>
                  <Text
                    style={{
                      ...theme.typography.focusValue,
                      color: theme.colors.textPrimary,
                      fontVariant: ['tabular-nums'],
                      letterSpacing: -1,
                    }}
                  >
                    {durationMinutes}
                  </Text>
                  <Text style={{ fontSize: 16, fontWeight: '600', color: theme.colors.textSecondary, marginLeft: 6 }}>
                    分钟
                  </Text>
                </View>

                {/* 预设时长药丸 */}
                <View style={{ flexDirection: 'row', justifyContent: 'center', flexWrap: 'wrap', gap: 8, marginBottom: 14 }}>
                  {DURATION_PRESETS.map(minutes => {
                    const isSelected = minutes === durationMinutes;
                    return (
                      <Pressable
                        key={minutes}
                        onPress={() => setDurationMinutes(minutes)}
                        accessibilityRole="button"
                        accessibilityState={{ selected: isSelected }}
                        hitSlop={6}
                        style={({ pressed }) => ({
                          paddingVertical: 7,
                          paddingHorizontal: 14,
                          borderRadius: YanjiRadius.full,
                          backgroundColor: isSelected ? theme.colors.accentSoft : theme.colors.bgSunken,
                          borderWidth: 1,
                          borderColor: isSelected ? theme.colors.accentPrimary : theme.colors.cardBorder,
                          opacity: pressed ? 0.75 : 1,
                        })}
                      >
                        <Text
                          style={{
                            color: isSelected ? theme.colors.accentPrimary : theme.colors.textSecondary,
                            fontSize: 13,
                            fontWeight: isSelected ? '700' : '500',
                            fontVariant: ['tabular-nums'],
                          }}
                        >
                          {minutes}m
                        </Text>
                      </Pressable>
                    );
                  })}
                </View>

                {/* 细调节步进器 */}
                <YanjiStepper
                  label="微调时长"
                  alignment="center"
                  value={durationMinutes}
                  onChange={setDurationMinutes}
                  min={MIN_MINUTES}
                  max={MAX_MINUTES}
                  step={5}
                  suffix=" 分钟"
                />
              </View>
            ) : (
              <View style={{ alignItems: 'center', marginVertical: 32 }}>
                <View
                  style={{
                    width: 52,
                    height: 52,
                    borderRadius: YanjiRadius.full,
                    backgroundColor: theme.colors.accentSoft,
                    borderWidth: 1,
                    borderColor: theme.colors.cardBorder,
                    alignItems: 'center',
                    justifyContent: 'center',
                    marginBottom: 12,
                  }}
                >
                  <YanjiIcon name="focus" size={24} color={theme.colors.accentPrimary} />
                </View>
                <Text style={[theme.typography.bodyStrong, { color: theme.colors.textPrimary }]}>
                  正计时 · 自由沉浸
                </Text>
                <Text
                  style={[
                    theme.typography.caption,
                    { color: theme.colors.textTertiary, marginTop: 4, textAlign: 'center' },
                  ]}
                >
                  不设时间上限，按你的节奏随心专注，随时结束
                </Text>
              </View>
            )}

            {taskBinding ? (
              <View
                style={{
                  flexDirection: 'row',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  backgroundColor: theme.colors.bgSunken,
                  borderRadius: YanjiRadius.md,
                  borderWidth: 1,
                  borderColor: theme.colors.cardBorder,
                  paddingVertical: 6,
                  paddingHorizontal: 12,
                  marginBottom: 16,
                }}
              >
                <View style={{ flexDirection: 'row', alignItems: 'center', flex: 1, marginRight: 8 }}>
                  <YanjiIcon name="tasks" size={14} color={theme.colors.accentPrimary} />
                  <Text
                    numberOfLines={1}
                    style={[
                      theme.typography.caption,
                      { color: theme.colors.textSecondary, marginLeft: 6, fontWeight: '500' },
                    ]}
                  >
                    关联任务：{taskBinding.title}
                  </Text>
                </View>
                <Pressable
                  onPress={() => setTaskBinding(null)}
                  accessibilityRole="button"
                  accessibilityLabel="取消关联任务"
                  hitSlop={6}
                >
                  <View style={{ minHeight: YanjiTouch.min, minWidth: YanjiTouch.min, alignItems: 'center', justifyContent: 'center' }}>
                    <YanjiIcon name="close" size={16} color={theme.colors.textSecondary} />
                  </View>
                </Pressable>
              </View>
            ) : null}

            {/* 开始专注 CTA 按钮 */}
            <YanjiBreathButton
              icon="play"
              label={busy ? '启动中…' : '开始专注'}
              onPress={handleStart}
              disabled={busy || !selectedSubject}
            />

            {startError ? (
              <Text
                style={[
                  theme.typography.caption,
                  { color: theme.colors.danger, marginTop: YanjiSpacing.md, textAlign: 'center' },
                ]}
              >
                {startError}
              </Text>
            ) : null}
          </YanjiCard>

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
      <ScrollView
        contentContainerStyle={{
          flexGrow: 1,
          justifyContent: 'center',
          alignItems: 'center',
          paddingHorizontal: YanjiSpacing.page,
          paddingTop: YanjiSpacing.lg,
          paddingBottom: contentPadding,
        }}
        showsVerticalScrollIndicator={false}
      >
        <View
          style={{
            paddingVertical: 6,
            paddingHorizontal: 16,
            borderRadius: YanjiRadius.full,
            backgroundColor: theme.colors.bgSurface,
            borderWidth: 1,
            borderColor: theme.colors.cardBorder,
            marginBottom: YanjiSpacing.sm,
          }}
        >
          <Text style={[theme.typography.label, { color: theme.colors.textSecondary }]}>
            {session.subjectName}
          </Text>
        </View>

        <FocusTimerReadout session={session} visible={visible} />

        <View style={{ marginTop: YanjiSpacing.xxl, alignSelf: 'stretch', maxWidth: 360 }}>
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
            maxWidth: 360,
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
      </ScrollView>

      <RecordMomentModal
        visible={recordOpen}
        date={date}
        onClose={() => setRecordOpen(false)}
      />
    </View>
  );
}

function FocusTimerReadout({
  session,
  visible,
}: {
  session: ActiveSessionState;
  visible: boolean;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const tick = useTimerTick(visible);
  // This layout depends on the window but does not touch the screen-level timer store.
  const { width } = useWindowDimensions();
  const ringSize = Math.min(RING_SIZE, Math.max(160, width - YanjiSpacing.page * 2 - YanjiSpacing.md));
  const elapsed = tick?.elapsedSeconds ?? session.elapsedSeconds;
  const remaining = Math.max(0, tick?.remainingSeconds ?? session.remainingSeconds);
  const total = elapsed + remaining;
  const ringProgress = session.isCountdown && total > 0 ? elapsed / total : null;
  const isPaused = session.isPaused || tick?.isPaused === true;
  const displaySeconds = session.isCountdown ? remaining : elapsed;

  return (
    <>
      <View style={{ marginTop: YanjiSpacing.lg }}>
        <YanjiProgressRing
          progress={ringProgress ?? 0}
          size={ringSize}
          thickness={RING_THICKNESS}
          color={isPaused ? theme.colors.textDisabled : theme.colors.accentPrimary}
        >
          <Text
            adjustsFontSizeToFit
            minimumFontScale={0.42}
            numberOfLines={1}
            style={[
              theme.typography.timerDisplay,
              {
                color: theme.colors.textPrimary,
                fontVariant: ['tabular-nums'],
                width: ringSize - RING_THICKNESS * 5,
                textAlign: 'center',
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
          ? '已暂停 · 可以随时继续'
          : session.isCountdown
            ? ringProgress === null
              ? '倒计时'
              : `剩余 ${Math.round((1 - ringProgress) * 100)}%`
            : '正计时'}
      </Text>
    </>
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
        backgroundColor: theme.colors.bgElevated,
        minHeight: YanjiTouch.comfortable,
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
