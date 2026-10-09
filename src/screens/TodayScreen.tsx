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
import type { ActiveSessionState, ExamCountdown, StudyTask, TodayStats } from '../bridge';
import {
  YanjiBadge,
  YanjiCard,
  YanjiEmptyState,
  YanjiPrimaryButton,
  YanjiSectionHeader,
} from '../components/YanjiUI';
import { RecordMomentModal } from '../components/RecordMomentModal';
import { TaskEditorModal } from '../components/TaskEditorModal';
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
  if (totalSeconds > 0) return `${Math.floor(totalSeconds)} 秒`;
  return '暂无记录';
}

export function TodayScreen(): React.JSX.Element {
  const theme = useYanjiTheme();
  const {
    openSettings,
    openTaskEditor,
    closeTaskEditor,
    taskEditorOpen,
    selectTab,
    requestFocusPreset,
  } = useNavigation();

  const [date] = useState(todayIso);
  const [stats, setStats] = useState<TodayStats | null>(null);
  const [tasks, setTasks] = useState<StudyTask[]>([]);
  const [countdown, setCountdown] = useState<ExamCountdown | null>(null);
  const [activeSession, setActiveSession] = useState<ActiveSessionState | null>(null);
  const [recordOpen, setRecordOpen] = useState(false);
  const [confirmDeleteId, setConfirmDeleteId] = useState<string | null>(null);

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

  /** Hand a task to the Focus tab: subject + duration preset + the taskId. */
  const startFromTask = useCallback(
    (task: StudyTask) => {
      requestFocusPreset({
        taskId: task.id,
        subjectId: task.subjectId,
        subjectName: task.subjectName,
        title: task.title,
        plannedMinutes: task.plannedMinutes,
      });
    },
    [requestFocusPreset]
  );

  /** One restrained primary action, resolved from real state. */
  const primaryAction = useMemo(() => {
    if (activeSession) {
      return {
        label: '继续专注',
        icon: '⏱️',
        onPress: () => selectTab('focus'),
      };
    }
    const pending = tasks.find(t => !t.completed);
    if (pending) {
      return {
        label: `继续学习 · ${pending.title}`,
        icon: '▶',
        onPress: () => startFromTask(pending),
      };
    }
    return {
      label: '开始专注',
      icon: '▶',
      onPress: () => selectTab('focus'),
    };
  }, [activeSession, tasks, selectTab, startFromTask]);

  const toggleTask = useCallback(
    async (task: StudyTask) => {
      try {
        await YanjiDataNative.toggleTask(task.id, !task.completed);
        await refresh();
      } catch {
        // Ignore — the next data event will resynchronise.
      }
    },
    [refresh]
  );

  const deleteTask = useCallback(
    async (taskId: string) => {
      try {
        await YanjiDataNative.deleteTask(taskId);
        setConfirmDeleteId(null);
        await refresh();
      } catch {
        // Ignore — the next data event will resynchronise.
      }
    },
    [refresh]
  );

  const formattedDate = useMemo(() => {
    const parsed = new Date(`${date}T00:00:00`);
    const names = ['日', '一', '二', '三', '四', '五', '六'];
    const month = parsed.getMonth() + 1;
    const day = parsed.getDate();
    return `${month}月${day}日 · 星期${names[parsed.getDay()]}`;
  }, [date]);

  return (
    <View style={{ flex: 1, backgroundColor: theme.colors.bgPrimary }}>
      <ScrollView
        contentContainerStyle={{
          paddingHorizontal: YanjiSpacing.xl,
          paddingTop: YanjiSpacing.md,
          paddingBottom: 120,
        }}
      >
        {/* Header: Greeting, date & subtle settings action button */}
        <View
          style={{
            flexDirection: 'row',
            justifyContent: 'space-between',
            alignItems: 'center',
            marginBottom: 4,
          }}
        >
          <View>
            <Text
              style={{
                color: theme.colors.textPrimary,
                fontSize: 30,
                fontWeight: '800',
                letterSpacing: -0.5,
              }}
            >
              今天
            </Text>
            <Text
              style={{
                color: theme.colors.textTertiary,
                fontSize: 13,
                fontWeight: '500',
                marginTop: 3,
              }}
            >
              {formattedDate}
            </Text>
          </View>
          <Pressable
            onPress={openSettings}
            accessibilityRole="button"
            accessibilityLabel="设置"
            style={({ pressed }) => ({
              width: 40,
              height: 40,
              borderRadius: YanjiRadius.full,
              backgroundColor: theme.colors.bgSurface,
              alignItems: 'center',
              justifyContent: 'center',
              opacity: pressed ? 0.7 : 1,
            })}
          >
            <Text style={{ fontSize: 18, color: theme.colors.textSecondary }}>⚙️</Text>
          </Pressable>
        </View>

        {/* Layer 1 — Today Hero Status Card with Exam Countdown Chip */}
        <YanjiCard variant="hero" style={{ marginTop: YanjiSpacing.lg }}>
          <View
            style={{
              flexDirection: 'row',
              justifyContent: 'space-between',
              alignItems: 'center',
            }}
          >
            <Text
              style={{
                color: theme.colors.textSecondary,
                fontSize: 13,
                fontWeight: '600',
              }}
            >
              今日专注
            </Text>
            {countdown && countdown.daysRemaining > 0 ? (
              <YanjiBadge
                icon="🎯"
                label={`距考研 ${countdown.daysRemaining} 天`}
              />
            ) : null}
          </View>

          <View style={{ marginTop: 10 }}>
            <Text
              style={{
                color: theme.colors.textPrimary,
                fontSize: 34,
                fontWeight: '800',
                letterSpacing: -0.5,
                fontVariant: ['tabular-nums'],
              }}
            >
              {stats ? formatDuration(stats.totalFocusSeconds) : '—'}
            </Text>
          </View>

          {stats && stats.totalFocusSeconds === 0 ? (
            <Text
              style={{
                color: theme.colors.textTertiary,
                fontSize: 12,
                marginTop: 8,
              }}
            >
              不足 1 分钟的专注不会被记录
            </Text>
          ) : (
            <Text
              style={{
                color: theme.colors.textTertiary,
                fontSize: 12,
                marginTop: 8,
              }}
            >
              静心专注，每一分钟都有意义
            </Text>
          )}
        </YanjiCard>

        {/* Layer 2 — Primary Action CTA Button */}
        <View style={{ marginTop: YanjiSpacing.lg }}>
          <YanjiPrimaryButton
            icon={primaryAction.icon}
            label={primaryAction.label}
            onPress={primaryAction.onPress}
          />
        </View>

        {/* Layer 3 — Today's Tasks */}
        <YanjiSectionHeader
          title="今日任务"
          rightAction={
            <Pressable
              onPress={openTaskEditor}
              accessibilityRole="button"
              accessibilityLabel="添加任务"
              style={({ pressed }) => ({
                flexDirection: 'row',
                alignItems: 'center',
                backgroundColor: theme.colors.bgSurface,
                paddingVertical: 5,
                paddingHorizontal: 12,
                borderRadius: YanjiRadius.full,
                opacity: pressed ? 0.7 : 1,
              })}
            >
              <Text
                style={{
                  color: theme.colors.accentPrimary,
                  fontSize: 13,
                  fontWeight: '600',
                }}
              >
                ＋ 添加
              </Text>
            </Pressable>
          }
        />

        {tasks.length === 0 ? (
          <YanjiCard variant="elevated" style={{ marginTop: 2, paddingVertical: 10 }}>
            <YanjiEmptyState
              icon="🌱"
              title="今天还没有任务"
              hint="可以直接开始专注，不必先做计划"
            />
          </YanjiCard>
        ) : (
          <View style={{ marginTop: 2 }}>
            {tasks.map(task => {
              const confirmingDelete = confirmDeleteId === task.id;
              return (
                <YanjiCard
                  key={task.id}
                  variant="elevated"
                  style={{
                    flexDirection: 'row',
                    alignItems: 'center',
                    marginBottom: 10,
                    paddingVertical: 14,
                    paddingHorizontal: 16,
                  }}
                >
                  <Pressable
                    onPress={() => void toggleTask(task)}
                    accessibilityRole="checkbox"
                    accessibilityState={{ checked: task.completed }}
                    style={{
                      width: 22,
                      height: 22,
                      borderRadius: YanjiRadius.xs,
                      borderWidth: 1.5,
                      borderColor: task.completed
                        ? theme.colors.accentPrimary
                        : theme.colors.fieldBorder,
                      backgroundColor: task.completed
                        ? theme.colors.accentPrimary
                        : 'transparent',
                      alignItems: 'center',
                      justifyContent: 'center',
                      marginRight: YanjiSpacing.md,
                    }}
                  >
                    {task.completed ? (
                      <Text
                        style={{
                          color: '#FFFFFF',
                          fontSize: 13,
                          fontWeight: '800',
                          lineHeight: 14,
                        }}
                      >
                        ✓
                      </Text>
                    ) : null}
                  </Pressable>

                  <Pressable
                    onPress={() => startFromTask(task)}
                    accessibilityRole="button"
                    accessibilityLabel={`继续学习 · ${task.title}`}
                    style={{ flex: 1 }}
                  >
                    <Text
                      style={{
                        color: task.completed
                          ? theme.colors.textTertiary
                          : theme.colors.textPrimary,
                        fontSize: 15,
                        fontWeight: task.completed ? '400' : '500',
                        textDecorationLine: task.completed ? 'line-through' : 'none',
                      }}
                    >
                      {task.title}
                    </Text>
                    <View style={{ flexDirection: 'row', alignItems: 'center', marginTop: 4 }}>
                      <View
                        style={{
                          backgroundColor: theme.colors.accentSoft,
                          paddingHorizontal: 6,
                          paddingVertical: 2,
                          borderRadius: YanjiRadius.xs,
                          marginRight: 6,
                        }}
                      >
                        <Text
                          style={{
                            color: theme.colors.accentPrimary,
                            fontSize: 11,
                            fontWeight: '600',
                          }}
                        >
                          {task.subjectName}
                        </Text>
                      </View>
                      <Text
                        style={{ color: theme.colors.textTertiary, fontSize: 12 }}
                      >
                        计划 {task.plannedMinutes} 分钟
                        {task.actualMinutes > 0 ? ` · 已专注 ${task.actualMinutes} 分钟` : ''}
                      </Text>
                    </View>
                  </Pressable>

                  {confirmingDelete ? (
                    <View style={{ flexDirection: 'row', alignItems: 'center' }}>
                      <Pressable
                        onPress={() => void deleteTask(task.id)}
                        accessibilityRole="button"
                        accessibilityLabel={`确认删除 · ${task.title}`}
                        style={{ padding: 6 }}
                      >
                        <Text style={{ color: theme.colors.danger, fontSize: 13, fontWeight: '600' }}>
                          删除
                        </Text>
                      </Pressable>
                      <Pressable
                        onPress={() => setConfirmDeleteId(null)}
                        accessibilityRole="button"
                        accessibilityLabel="取消删除"
                        style={{ padding: 6 }}
                      >
                        <Text style={{ color: theme.colors.textSecondary, fontSize: 13 }}>
                          取消
                        </Text>
                      </Pressable>
                    </View>
                  ) : (
                    <Pressable
                      onPress={() => setConfirmDeleteId(task.id)}
                      accessibilityRole="button"
                      accessibilityLabel={`删除任务 · ${task.title}`}
                      style={{ padding: 6, marginLeft: YanjiSpacing.sm }}
                    >
                      <Text style={{ color: theme.colors.textTertiary, fontSize: 16 }}>✕</Text>
                    </Pressable>
                  )}
                </YanjiCard>
              );
            })}
          </View>
        )}

        {/* Layer 4 — Record Moment Entry Quick Card */}
        <YanjiCard
          style={{
            marginTop: YanjiSpacing.lg,
            flexDirection: 'row',
            alignItems: 'center',
            justifyContent: 'space-between',
            paddingVertical: 14,
            paddingHorizontal: 18,
          }}
        >
          <Pressable
            onPress={() => setRecordOpen(true)}
            accessibilityRole="button"
            accessibilityLabel="记录此刻"
            style={{ flexDirection: 'row', alignItems: 'center', flex: 1 }}
          >
            <Text style={{ fontSize: 18, marginRight: 12 }}>✍️</Text>
            <View>
              <Text
                style={{
                  color: theme.colors.textPrimary,
                  fontSize: 14,
                  fontWeight: '600',
                }}
              >
                记录此刻
              </Text>
              <Text
                style={{
                  color: theme.colors.textTertiary,
                  fontSize: 12,
                  marginTop: 2,
                }}
              >
                随手记下当下的灵感与心得
              </Text>
            </View>
          </Pressable>

          <Pressable
            onPress={() => setRecordOpen(true)}
            style={({ pressed }) => ({
              backgroundColor: theme.colors.accentSoft,
              paddingVertical: 6,
              paddingHorizontal: 12,
              borderRadius: YanjiRadius.full,
              opacity: pressed ? 0.7 : 1,
            })}
          >
            <Text
              style={{
                color: theme.colors.accentPrimary,
                fontSize: 12,
                fontWeight: '600',
              }}
            >
              写心得
            </Text>
          </Pressable>
        </YanjiCard>
      </ScrollView>

      <RecordMomentModal
        visible={recordOpen}
        date={date}
        onClose={() => setRecordOpen(false)}
        onSaved={() => void refresh()}
      />

      <TaskEditorModal
        visible={taskEditorOpen}
        date={date}
        onClose={closeTaskEditor}
        onSaved={() => void refresh()}
      />
    </View>
  );
}
