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
import type { ActiveSessionState, Subject, TimerTickEvent } from '../bridge';
import { YanjiPrimaryButton, YanjiSectionHeader } from '../components/YanjiUI';
import { RecordMomentModal } from '../components/RecordMomentModal';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiRadius, YanjiSpacing, YanjiTypography } from '../theme/tokens';

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

export function FocusScreen(): React.JSX.Element {
  const theme = useYanjiTheme();
  const [date] = useState(todayIso);

  const [session, setSession] = useState<ActiveSessionState | null>(null);
  const [tick, setTick] = useState<TimerTickEvent | null>(null);
  const [subjects, setSubjects] = useState<Subject[]>([]);
  const [selectedSubjectId, setSelectedSubjectId] = useState<string | null>(null);
  const [durationMinutes, setDurationMinutes] = useState(45);
  const [recordOpen, setRecordOpen] = useState(false);
  const [busy, setBusy] = useState(false);

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
      void refreshSession();
    });
    return () => {
      unsubTick();
      unsubState();
    };
  }, [refreshSession]);

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

  const handleStart = useCallback(async () => {
    if (busy || !selectedSubject) return;
    setBusy(true);
    try {
      await YanjiTimerNative.startFocus(
        selectedSubject.id,
        selectedSubject.name,
        `${durationMinutes}分钟专注`,
        '',
        null
      );
      await refreshSession();
    } catch {
      // A concurrent session or native error — the state event will resynchronise.
    } finally {
      setBusy(false);
    }
  }, [busy, selectedSubject, durationMinutes, refreshSession]);

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
        <ScrollView contentContainerStyle={{ padding: YanjiSpacing.xl, paddingBottom: 120 }}>
          <Text style={{ color: theme.colors.textPrimary, fontSize: 26, fontWeight: '700' }}>
            专注
          </Text>

          <YanjiSectionHeader title="科目" />
          <View style={{ flexDirection: 'row', flexWrap: 'wrap' }}>
            {subjects.map(subject => {
              const active = subject.id === selectedSubjectId;
              return (
                <Pressable
                  key={subject.id}
                  onPress={() => setSelectedSubjectId(subject.id)}
                  accessibilityRole="button"
                  accessibilityState={{ selected: active }}
                  style={{
                    paddingVertical: 8,
                    paddingHorizontal: 14,
                    borderRadius: YanjiRadius.full,
                    marginRight: YanjiSpacing.sm,
                    marginBottom: YanjiSpacing.sm,
                    backgroundColor: active ? theme.colors.accentSoft : theme.colors.bgSurface,
                  }}
                >
                  <Text
                    style={{
                      color: active ? theme.colors.accentPrimary : theme.colors.textSecondary,
                      fontSize: 14,
                      fontWeight: active ? '600' : '400',
                    }}
                  >
                    {subject.name}
                  </Text>
                </Pressable>
              );
            })}
            {subjects.length === 0 ? (
              <Text style={{ color: theme.colors.textTertiary, fontSize: 13 }}>暂无可用科目</Text>
            ) : null}
          </View>

          <YanjiSectionHeader title="时长" />
          <View style={{ flexDirection: 'row' }}>
            {DURATION_PRESETS.map(minutes => {
              const active = minutes === durationMinutes;
              return (
                <Pressable
                  key={minutes}
                  onPress={() => setDurationMinutes(minutes)}
                  accessibilityRole="button"
                  accessibilityState={{ selected: active }}
                  style={{
                    paddingVertical: 8,
                    paddingHorizontal: 14,
                    borderRadius: YanjiRadius.full,
                    marginRight: YanjiSpacing.sm,
                    backgroundColor: active ? theme.colors.accentPrimary : theme.colors.bgSurface,
                  }}
                >
                  <Text
                    style={{
                      color: active ? '#FFFFFF' : theme.colors.textSecondary,
                      fontSize: 14,
                      fontWeight: active ? '600' : '400',
                    }}
                  >
                    {minutes} 分钟
                  </Text>
                </Pressable>
              );
            })}
          </View>

          <View style={{ marginTop: YanjiSpacing.xxl }}>
            <YanjiPrimaryButton
              label={busy ? '启动中' : '开始专注'}
              onPress={handleStart}
              disabled={busy || !selectedSubject}
            />
          </View>

          <Pressable
            onPress={() => setRecordOpen(true)}
            accessibilityRole="button"
            style={{ marginTop: YanjiSpacing.xl, paddingVertical: YanjiSpacing.md }}
          >
            <Text style={{ color: theme.colors.accentPrimary, fontSize: 14 }}>＋ 记录此刻</Text>
          </Pressable>
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
      <View style={{ flex: 1, justifyContent: 'center', alignItems: 'center', padding: YanjiSpacing.xl }}>
        <Text style={{ color: theme.colors.textSecondary, fontSize: 15 }}>
          {session.subjectName}
        </Text>

        <Text
          style={{
            color: theme.colors.textPrimary,
            fontSize: YanjiTypography.timerDisplay.fontSize,
            fontWeight: YanjiTypography.timerDisplay.fontWeight,
            lineHeight: YanjiTypography.timerDisplay.lineHeight,
            marginTop: YanjiSpacing.sm,
            fontVariant: ['tabular-nums'],
          }}
        >
          {formatClock(displaySeconds)}
        </Text>

        <Text style={{ color: theme.colors.textTertiary, fontSize: 13, marginTop: YanjiSpacing.xs }}>
          {isPaused ? '已暂停' : session.isCountdown ? '倒计时' : '正计时'}
        </Text>

        <View style={{ marginTop: YanjiSpacing.xxl, alignSelf: 'stretch' }}>
          <YanjiPrimaryButton
            label={isPaused ? '恢复' : '暂停'}
            onPress={handlePauseResume}
          />
        </View>

        <View style={{ flexDirection: 'row', marginTop: YanjiSpacing.lg, alignSelf: 'stretch' }}>
          <View style={{ flex: 1, marginRight: YanjiSpacing.sm }}>
            <YanjiPrimaryButton label="结束" onPress={handleComplete} />
          </View>
          <View style={{ flex: 1, marginLeft: YanjiSpacing.sm }}>
            <YanjiPrimaryButton label="放弃" onPress={handleDiscard} />
          </View>
        </View>

        <Pressable
          onPress={() => setRecordOpen(true)}
          accessibilityRole="button"
          style={{ marginTop: YanjiSpacing.xl, paddingVertical: YanjiSpacing.md }}
        >
          <Text style={{ color: theme.colors.accentPrimary, fontSize: 14 }}>＋ 记录此刻</Text>
        </Pressable>
      </View>

      <RecordMomentModal
        visible={recordOpen}
        date={date}
        onClose={() => setRecordOpen(false)}
      />
    </View>
  );
}
