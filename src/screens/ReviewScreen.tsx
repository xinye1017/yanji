/**
 * Yanji "Review" screen — a readable growth journal, not a dashboard.
 *
 * Contract (PROJECT.md § R2 / user brief §05):
 * - Day-first: the default view is today's real record.
 * - The timeline is a deterministic aggregation of Room data — focus sessions,
 *   completed tasks and notes. No generated prose, no AI content in this phase.
 * - Statistics stay secondary and factual (7-day trend, subject distribution).
 * - No streak counters, leaderboards or achievement walls as primary content.
 */

import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Pressable, ScrollView, Text, View } from 'react-native';
import { YanjiDataNative, onDataChanged } from '../bridge';
import type { DailyTimeline, ReviewStats } from '../bridge';
import { YanjiCard, YanjiEmptyState, YanjiSectionHeader } from '../components/YanjiUI';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiRadius, YanjiSpacing } from '../theme/tokens';

function todayIso(): string {
  const now = new Date();
  const month = `${now.getMonth() + 1}`.padStart(2, '0');
  const day = `${now.getDate()}`.padStart(2, '0');
  return `${now.getFullYear()}-${month}-${day}`;
}

function shiftIsoDate(iso: string, deltaDays: number): string {
  const parsed = new Date(`${iso}T00:00:00`);
  parsed.setDate(parsed.getDate() + deltaDays);
  const month = `${parsed.getMonth() + 1}`.padStart(2, '0');
  const day = `${parsed.getDate()}`.padStart(2, '0');
  return `${parsed.getFullYear()}-${month}-${day}`;
}

function formatClockFromEpoch(epochMs: number): string {
  const date = new Date(epochMs);
  return `${`${date.getHours()}`.padStart(2, '0')}:${`${date.getMinutes()}`.padStart(2, '0')}`;
}

function formatMinutes(totalSeconds: number): string {
  const minutes = Math.round(totalSeconds / 60);
  if (minutes <= 0) return '0 分钟';
  const hours = Math.floor(minutes / 60);
  const rest = minutes % 60;
  if (hours > 0 && rest > 0) return `${hours} 小时 ${rest} 分`;
  if (hours > 0) return `${hours} 小时`;
  return `${rest} 分钟`;
}

export function ReviewScreen(): React.JSX.Element {
  const theme = useYanjiTheme();
  const [date, setDate] = useState(todayIso);
  const [timeline, setTimeline] = useState<DailyTimeline | null>(null);
  const [stats, setStats] = useState<ReviewStats | null>(null);

  const refresh = useCallback(async (targetDate: string) => {
    try {
      const [day, review] = await Promise.all([
        YanjiDataNative.getDailyTimeline(targetDate),
        YanjiDataNative.getReviewStats(7),
      ]);
      setTimeline(day);
      setStats(review);
    } catch {
      // Keep the previous snapshot rather than inventing entries.
    }
  }, []);

  useEffect(() => {
    void refresh(date);
  }, [date, refresh]);

  useEffect(() => {
    const unsub = onDataChanged(() => void refresh(date));
    return () => unsub();
  }, [date, refresh]);

  const trendEntries = useMemo(() => {
    if (!stats) return [] as Array<{ date: string; minutes: number }>;
    return Object.entries(stats.dailyFocusMinutes)
      .sort(([a], [b]) => (a < b ? -1 : 1))
      .map(([key, value]) => ({ date: key, minutes: value }));
  }, [stats]);

  const subjectEntries = useMemo(() => {
    if (!stats) return [] as Array<{ name: string; minutes: number }>;
    return Object.entries(stats.subjectDistribution)
      .sort(([, a], [, b]) => b - a)
      .map(([name, value]) => ({ name, minutes: value }));
  }, [stats]);

  const hasRecords =
    !!timeline &&
    (timeline.sessions.length > 0 || timeline.completedTasks.length > 0 || timeline.notes.length > 0);

  return (
    <View style={{ flex: 1, backgroundColor: theme.colors.bgPrimary }}>
      <ScrollView contentContainerStyle={{ padding: YanjiSpacing.xl, paddingBottom: 120 }}>
        <Text style={{ color: theme.colors.textPrimary, fontSize: 26, fontWeight: '700' }}>回顾</Text>

        {/* Date navigation */}
        <View
          style={{
            flexDirection: 'row',
            alignItems: 'center',
            justifyContent: 'space-between',
            marginTop: YanjiSpacing.lg,
          }}
        >
          <Pressable
            onPress={() => setDate(prev => shiftIsoDate(prev, -1))}
            accessibilityRole="button"
            accessibilityLabel="前一天"
            style={{ padding: 8 }}
          >
            <Text style={{ color: theme.colors.textSecondary, fontSize: 18 }}>‹</Text>
          </Pressable>
          <Text style={{ color: theme.colors.textPrimary, fontSize: 15, fontWeight: '600' }}>
            {timeline?.formattedDate ?? date}
          </Text>
          <Pressable
            onPress={() => setDate(prev => shiftIsoDate(prev, 1))}
            accessibilityRole="button"
            accessibilityLabel="后一天"
            style={{ padding: 8 }}
          >
            <Text style={{ color: theme.colors.textSecondary, fontSize: 18 }}>›</Text>
          </Pressable>
        </View>

        {/* Layer 1 — the day's record */}
        <YanjiSectionHeader title="这一天的记录" />
        {!hasRecords ? (
          <YanjiEmptyState title="这一天还没有记录" hint="专注、任务与想法都会出现在这里" />
        ) : (
          <View>
            <YanjiCard>
              <Text style={{ color: theme.colors.textSecondary, fontSize: 13 }}>当日专注</Text>
              <Text
                style={{
                  color: theme.colors.textPrimary,
                  fontSize: 24,
                  fontWeight: '700',
                  marginTop: 4,
                }}
              >
                {timeline ? formatMinutes(timeline.totalDurationSeconds) : '—'}
              </Text>
            </YanjiCard>

            {timeline?.sessions.map(session => (
              <View
                key={session.id}
                style={{
                  flexDirection: 'row',
                  justifyContent: 'space-between',
                  paddingVertical: 10,
                }}
              >
                <View style={{ flex: 1 }}>
                  <Text style={{ color: theme.colors.textPrimary, fontSize: 14 }}>
                    {session.subjectName}
                    {session.isExam ? ' · 模考' : ''}
                  </Text>
                  <Text style={{ color: theme.colors.textTertiary, fontSize: 12, marginTop: 2 }}>
                    {formatClockFromEpoch(session.startTime)} 起 · {session.mode}
                  </Text>
                </View>
                <Text style={{ color: theme.colors.textSecondary, fontSize: 14 }}>
                  {formatMinutes(session.durationSeconds)}
                </Text>
              </View>
            ))}

            {timeline?.completedTasks.map(task => (
              <View
                key={task.id}
                style={{ flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 8 }}
              >
                <Text style={{ color: theme.colors.textPrimary, fontSize: 14, flex: 1 }}>
                  ✓ {task.title}
                </Text>
                <Text style={{ color: theme.colors.textTertiary, fontSize: 12 }}>
                  {task.subjectName}
                </Text>
              </View>
            ))}

            {timeline?.notes.map(note => (
              <View
                key={note.id}
                style={{
                  backgroundColor: theme.colors.bgSurface,
                  borderRadius: YanjiRadius.md,
                  padding: YanjiSpacing.md,
                  marginTop: YanjiSpacing.sm,
                }}
              >
                <Text style={{ color: theme.colors.textPrimary, fontSize: 14, lineHeight: 21 }}>
                  {note.content}
                </Text>
                <Text style={{ color: theme.colors.textTertiary, fontSize: 11, marginTop: 6 }}>
                  {formatClockFromEpoch(note.timestamp)}
                </Text>
              </View>
            ))}
          </View>
        )}

        {/* Layer 3 — secondary statistics */}
        <YanjiSectionHeader title="最近 7 天" />
        {trendEntries.length === 0 ? (
          <YanjiEmptyState title="暂无统计数据" />
        ) : (
          <YanjiCard>
            {trendEntries.map(entry => {
              const max = Math.max(...trendEntries.map(e => e.minutes), 1);
              return (
                <View
                  key={entry.date}
                  style={{ flexDirection: 'row', alignItems: 'center', marginBottom: 8 }}
                >
                  <Text style={{ color: theme.colors.textTertiary, fontSize: 11, width: 62 }}>
                    {entry.date.slice(5)}
                  </Text>
                  <View
                    style={{
                      flex: 1,
                      height: 8,
                      borderRadius: YanjiRadius.full,
                      backgroundColor: theme.colors.bgElevated,
                      marginRight: YanjiSpacing.sm,
                    }}
                  >
                    <View
                      style={{
                        width: `${Math.round((entry.minutes / max) * 100)}%`,
                        height: 8,
                        borderRadius: YanjiRadius.full,
                        backgroundColor: theme.colors.accentPrimary,
                      }}
                    />
                  </View>
                  <Text style={{ color: theme.colors.textSecondary, fontSize: 11, width: 52, textAlign: 'right' }}>
                    {entry.minutes} 分
                  </Text>
                </View>
              );
            })}
          </YanjiCard>
        )}

        {subjectEntries.length > 0 ? (
          <>
            <YanjiSectionHeader title="科目分布" />
            <YanjiCard>
              {subjectEntries.map(entry => (
                <View
                  key={entry.name}
                  style={{ flexDirection: 'row', justifyContent: 'space-between', paddingVertical: 6 }}
                >
                  <Text style={{ color: theme.colors.textPrimary, fontSize: 14 }}>{entry.name}</Text>
                  <Text style={{ color: theme.colors.textSecondary, fontSize: 14 }}>
                    {formatMinutes(entry.minutes * 60)}
                  </Text>
                </View>
              ))}
            </YanjiCard>
          </>
        ) : null}
      </ScrollView>
    </View>
  );
}
