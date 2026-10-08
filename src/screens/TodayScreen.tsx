/**
 * Yanji "Today" screen — the minimal study entry point.
 *
 * Information hierarchy (PROJECT.md § R2 / user brief §03):
 *   1. Today status: date, restrained exam countdown, today's focus total.
 *   2. Primary action: one restrained button whose label adapts to real state
 *      (continue focus / continue task / start focus).
 *   3. Today's tasks: a plain list — not a wall of cards.
 *   4. Record Moment entry.
 *
 * The page never invents data: everything shown comes from Room through the
 * native bridge. First-run with zero records is a valid, quiet state.
 */

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import {
  YanjiDataNative,
  YanjiTimerNative,
  onDataChanged,
  onTimerStateChanged,
} from '../bridge';
import type {
  ActiveSessionState,
  ExamCountdown,
  StudyTask,
  TodayStats,
} from '../bridge';
import { YanjiCard, YanjiEmptyState, YanjiPrimaryButton, YanjiSectionHeader } from '../components/YanjiUI';
import { RecordMomentModal } from '../components/RecordMomentModal';
import { useNavigation } from '../navigation/NavigationShell';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiRadius, YanjiSpacing } from '../theme/tokens';

function todayIso(): string {
  const now = new Date();
  const month = `${now.getMonth() + 1}`.padStart(2, '0');
  const day = `${now.getDate()}`.padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

function formatDuration(totalSeconds: number): string {
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  if (hours > 0) return `${hours} 小时 ${minutes} 分`;
  if (minutes > 0) return `${minutes} 分钟`;
  return '还没开始';
}

export function TodayScreen(): React.JSX.Element {
  const theme = useYanjiTheme();
  const { openSettings, selectTab } = useNavigation();

  const [date] = useState(todayIso);
  const [stats, setStats] = useState<TodayStats | null>(null);
  const [tasks, setTasks] = useState<StudyTask[]>([]);
  const [countdown, setCountdown] = useState<ExamCountdown | null>(null);
  const [activeSession, setActiveSession] = useState<ActiveSessionState | null>(null);
  const [recordOpen, setRecordOpen] = useState(false);

  const refresh = useCallback(async () => {
    try {
      const [todayStats, todayTasks, exam] = await Promise.all([
        YanjiDataNative.getTodayStats(date),
        YanjiDataNative.getTodayTasks(date),
        YanjiDataNative.getExamCountdown(),
      ]);
      setStats(todayStats);
      setTasks(todayTasks);
      setCountdown(exam);
    } catch {
      // Native bridge unavailable — keep the previous snapshot rather than faking data.
    }
  }, [date]);

  const refreshSession = useCallback(async () => {
    try {
      setActiveSession(await YanjiTimerNative.getActiveSession());
    } catch {
      setActiveSession(null);
    }
  }, []);

  useEffect(() => {
    void refresh();
    void refreshSession();
  }, [refresh, refreshSession]);

  useEffect(() => {
    const unsubData = onDataChanged(() => void refresh());
    const unsubTimer = onTimerStateChanged(() => void refreshSession());
    return () => {
      unsubData();
      unsubTimer();
    };
  }, [refresh, refreshSession]);

  /** One restrained primary action, resolved from real state. */
  const primaryAction = useMemo(() => {
    if (activeSession) {
      return { label: '继续专注', onPress: () => selectTab('focus') };
    }
    const pending = tasks.find(t => !t.completed);
    if (pending) {
      return { label: `继续学习 · ${pending.title}`, onPress: () => selectTab('focus') };
    }
    return { label: '开始专注', onPress: () => selectTab('focus') };
  }, [activeSession, tasks, selectTab]);

  const weekday = useMemo(() => {
    const parsed = new Date(`${date}T00:00:00`);
    const names = ['日', '一', '二', '三', '四', '五', '六'];
    return `周${names[parsed.getDay()]}`;
  }, [date]);

  return (
    <View style={{ flex: 1, backgroundColor: theme.colors.bgPrimary }}>
      <ScrollView contentContainerStyle={{ padding: YanjiSpacing.xl, paddingBottom: 120 }}>
        {/* Header: date + settings entry (settings is never a tab). */}
        <View style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}>
          <View>
            <Text style={{ color: theme.colors.textPrimary, fontSize: 26, fontWeight: '700' }}>
              今天
            </Text>
            <Text style={{ color: theme.colors.textTertiary, fontSize: 13, marginTop: 2 }}>
              {date} · {weekday}
            </Text>
          </View>
          <Pressable
            onPress={openSettings}
            accessibilityRole="button"
            accessibilityLabel="设置"
            style={{ padding: 8 }}
          >
            <Text style={{ color: theme.colors.textSecondary, fontSize: 20 }}>⚙</Text>
          </Pressable>
        </View>

        {/* Layer 1 — today status */}
        <YanjiCard style={{ marginTop: YanjiSpacing.xl }}>
          <Text style={{ color: theme.colors.textSecondary, fontSize: 13 }}>今日专注</Text>
          <Text
            style={{
              color: theme.colors.textPrimary,
              fontSize: 30,
              fontWeight: '700',
              marginTop: 4,
            }}
          >
            {stats ? formatDuration(stats.totalFocusSeconds) : '—'}
          </Text>
          {countdown && countdown.daysRemaining > 0 ? (
            <Text style={{ color: theme.colors.textTertiary, fontSize: 13, marginTop: 8 }}>
              距考研还有 {countdown.daysRemaining} 天
            </Text>
          ) : null}
        </YanjiCard>

        {/* Layer 2 — primary action */}
        <View style={{ marginTop: YanjiSpacing.xl }}>
          <YanjiPrimaryButton label={primaryAction.label} onPress={primaryAction.onPress} />
        </View>

        {/* Layer 3 — today's tasks */}
        <YanjiSectionHeader title="今日任务" />
        {tasks.length === 0 ? (
          <YanjiEmptyState title="今天还没有任务" hint="可以直接开始专注，不必先做计划" />
        ) : (
          <View>
            {tasks.map(task => (
              <View
                key={task.id}
                style={{
                  flexDirection: 'row',
                  alignItems: 'center',
                  paddingVertical: 12,
                }}
              >
                <Pressable
                  onPress={async () => {
                    try {
                      await YanjiDataNative.toggleTask(task.id, !task.completed);
                      await refresh();
                    } catch {
                      // Ignore — the next data event will resynchronise.
                    }
                  }}
                  accessibilityRole="checkbox"
                  accessibilityState={{ checked: task.completed }}
                  style={{
                    width: 20,
                    height: 20,
                    borderRadius: YanjiRadius.xs,
                    borderWidth: 1.5,
                    borderColor: task.completed
                      ? theme.colors.accentPrimary
                      : theme.colors.fieldBorder,
                    backgroundColor: task.completed ? theme.colors.accentPrimary : 'transparent',
                    marginRight: YanjiSpacing.md,
                  }}
                />
                <View style={{ flex: 1 }}>
                  <Text
                    style={{
                      color: task.completed ? theme.colors.textTertiary : theme.colors.textPrimary,
                      fontSize: 15,
                      textDecorationLine: task.completed ? 'line-through' : 'none',
                    }}
                  >
                    {task.title}
                  </Text>
                  <Text style={{ color: theme.colors.textTertiary, fontSize: 12, marginTop: 2 }}>
                    {task.subjectName} · 计划 {task.plannedMinutes} 分钟
                  </Text>
                </View>
              </View>
            ))}
          </View>
        )}

        {/* Layer 4 — record moment entry */}
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
        onSaved={() => void refresh()}
      />
    </View>
  );
}
