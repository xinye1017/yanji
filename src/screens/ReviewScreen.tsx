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
import { LayoutChangeEvent, Pressable, ScrollView, Text, View } from 'react-native';
import { YanjiDataNative, onDataChanged } from '../bridge';
import type { DailyTimeline, ReviewStats, StudyTask } from '../bridge';
import {
  YanjiCard,
  YanjiEmptyState,
  YanjiHairline,
  YanjiIcon,
  YanjiIconButton,
  YanjiMiniBarChart,
  YanjiSectionHeader,
} from '../components/YanjiUI';
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
  const [dayTasks, setDayTasks] = useState<StudyTask[]>([]);
  /** Measured width of the trend-chart column, so the SVG matches its labels. */
  const [chartWidth, setChartWidth] = useState(0);

  const refresh = useCallback(async (targetDate: string) => {
    try {
      const [day, review] = await Promise.all([
        YanjiDataNative.getDailyTimeline(targetDate),
        YanjiDataNative.getReviewStats(7),
      ]);
      setTimeline(day);
      setStats(review);
      setDayTasks(await YanjiDataNative.getTodayTasks(targetDate));
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

  const completedTaskCount = useMemo(
    () => dayTasks.filter(task => task.completed).length,
    [dayTasks]
  );

  const isToday = date === todayIso();

  return (
    <View style={{ flex: 1, backgroundColor: theme.colors.bgPrimary }}>
      <ScrollView
        testID="scroll-review"
        style={{ flex: 1 }}
        contentContainerStyle={{
          paddingHorizontal: YanjiSpacing.page,
          paddingTop: YanjiSpacing.md,
          // The tab bar reserves its own height outside this ScrollView.
          paddingBottom: YanjiSpacing.xxl,
        }}
      >
        <Text
          style={[
            theme.typography.pageTitle,
            { color: theme.colors.textPrimary, letterSpacing: -0.4 },
          ]}
        >
          回顾
        </Text>

        {/* Date navigation — a small sheet the day label sits in */}
        <View
          style={{
            flexDirection: 'row',
            alignItems: 'center',
            justifyContent: 'space-between',
            alignSelf: 'center',
            marginTop: YanjiSpacing.lg,
            backgroundColor: theme.colors.bgSurface,
            borderRadius: YanjiRadius.full,
            paddingVertical: 4,
            paddingHorizontal: 6,
            minWidth: 200,
          }}
        >
          <YanjiIconButton
            icon="back"
            accessibilityLabel="前一天"
            onPress={() => setDate(prev => shiftIsoDate(prev, -1))}
            iconSize={17}
          />
          <Text testID="review-date" style={{ color: theme.colors.textPrimary, fontSize: 15, fontWeight: '600' }}>
            {timeline?.formattedDate ?? date}
          </Text>
          <YanjiIconButton
            icon="forward"
            accessibilityLabel="后一天"
            onPress={() => setDate(prev => shiftIsoDate(prev, 1))}
            iconSize={17}
          />
        </View>

        {/* The day's record */}
        <YanjiSectionHeader title="这一天的记录" />
        {!hasRecords ? (
          <YanjiCard variant="sunken">
            <YanjiEmptyState
              icon="calendar"
              title="这一天还没有记录"
              hint="专注、任务与想法都会出现在这里"
            />
          </YanjiCard>
        ) : (
          <View>
            <YanjiCard variant="hero">
              <Text style={[theme.typography.label, { color: theme.colors.textSecondary }]}>
                当日专注
              </Text>
              <Text
                style={[
                  theme.typography.display,
                  {
                    color: theme.colors.textPrimary,
                    marginTop: YanjiSpacing.sm,
                    fontVariant: ['tabular-nums'],
                  },
                ]}
              >
                {timeline ? formatMinutes(timeline.totalDurationSeconds) : '—'}
              </Text>
            </YanjiCard>

            {/* Sessions / tasks / notes as hairline-separated rows of one sheet */}
            <YanjiCard style={{ marginTop: YanjiSpacing.md, padding: 0 }}>
              {timeline?.sessions.map((session, index) => (
                <View key={session.id}>
                  {index > 0 ? <YanjiHairline inset={16} /> : null}
                  <View
                    style={{
                      flexDirection: 'row',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      paddingVertical: 12,
                      paddingHorizontal: 16,
                    }}
                  >
                    <View style={{ flex: 1 }}>
                      <Text style={{ color: theme.colors.textPrimary, fontSize: 14 }}>
                        {session.subjectName}
                        {session.isExam ? ' · 模考' : ''}
                      </Text>
                      <Text
                        style={{
                          color: theme.colors.textTertiary,
                          fontSize: 12,
                          marginTop: 2,
                        }}
                      >
                        {formatClockFromEpoch(session.startTime)} 起 · {session.mode}
                      </Text>
                    </View>
                    <Text style={{ color: theme.colors.textSecondary, fontSize: 14 }}>
                      {formatMinutes(session.durationSeconds)}
                    </Text>
                  </View>
                </View>
              ))}

              {timeline?.completedTasks.map((task, index) => (
                <View key={task.id}>
                  {(timeline.sessions.length + index) > 0 ? <YanjiHairline inset={16} /> : null}
                  <View
                    style={{
                      flexDirection: 'row',
                      alignItems: 'center',
                      paddingVertical: 12,
                      paddingHorizontal: 16,
                    }}
                  >
                    <YanjiIcon
                      name="check"
                      size={14}
                      color={theme.colors.success}
                      strokeWidth={2.5}
                    />
                    <Text
                      style={{
                        color: theme.colors.textPrimary,
                        fontSize: 14,
                        flex: 1,
                        marginLeft: YanjiSpacing.sm,
                      }}
                    >
                      {task.title}
                    </Text>
                    <Text style={{ color: theme.colors.textTertiary, fontSize: 12 }}>
                      {task.subjectName}
                    </Text>
                  </View>
                </View>
              ))}

              {timeline?.notes.map((note, index) => (
                <View key={note.id}>
                  {index > 0 ? <YanjiHairline inset={16} /> : null}
                  <View style={{ padding: 16 }}>
                    <Text
                      style={{
                        color: theme.colors.textPrimary,
                        fontSize: 14,
                        lineHeight: 21,
                      }}
                    >
                      {note.content}
                    </Text>
                    <Text
                      style={{
                        color: theme.colors.textTertiary,
                        fontSize: 11,
                        marginTop: 6,
                      }}
                    >
                      {formatClockFromEpoch(note.timestamp)}
                    </Text>
                  </View>
                </View>
              ))}
            </YanjiCard>
          </View>
        )}

        {/* Secondary statistics */}
        <YanjiSectionHeader title="最近 7 天" />
        {trendEntries.length === 0 ? (
          <YanjiCard variant="sunken">
            <YanjiEmptyState icon="chartMinimal" title="暂无统计数据" />
          </YanjiCard>
        ) : (
          <YanjiCard>
            <View
              style={{
                flexDirection: 'row',
                justifyContent: 'space-between',
                alignItems: 'baseline',
                paddingBottom: YanjiSpacing.md,
              }}
            >
              <Text style={{ color: theme.colors.textSecondary, fontSize: 13 }}>日均专注时长</Text>
              <Text style={{ color: theme.colors.textPrimary, fontSize: 15, fontWeight: '600' }}>
                {stats ? formatMinutes(stats.dailyAverageMinutes * 60) : '—'}
              </Text>
            </View>

            {/* The chart is measured, not given a magic width: it must line up
                with the day labels below it on any screen width. */}
            <View onLayout={event => setChartWidth(event.nativeEvent.layout.width)}>
              <YanjiMiniBarChart
                data={trendEntries.map(entry => entry.minutes)}
                width={chartWidth}
                height={64}
                highlightIndex={isToday ? trendEntries.length - 1 : undefined}
                emptyLabel="暂无统计数据"
              />

              {/* Day labels sit under the chart, aligned to their column. */}
              <View style={{ flexDirection: 'row', marginTop: YanjiSpacing.sm }}>
                {trendEntries.map(entry => (
                  <Text
                    key={entry.date}
                    style={{
                      flex: 1,
                      textAlign: 'center',
                      color: theme.colors.textTertiary,
                      fontSize: 10,
                    }}
                  >
                    {entry.date.slice(5)}
                  </Text>
                ))}
              </View>
            </View>

            <YanjiHairline style={{ marginTop: YanjiSpacing.md }} />

            <View style={{ flexDirection: 'row', marginTop: YanjiSpacing.md }}>
              <ReviewStat label="累计" value={formatMinutes(stats ? stats.totalFocusHours * 3600 : 0)} />
              <ReviewStat label="专注天数" value={`${stats?.activeDays ?? 0} 天`} />
            </View>
          </YanjiCard>
        )}

        {subjectEntries.length > 0 ? (
          <>
            <YanjiSectionHeader title="科目分布" />
            <YanjiCard style={{ padding: 0 }}>
              {subjectEntries.map((entry, index) => (
                <View key={entry.name}>
                  {index > 0 ? <YanjiHairline inset={16} /> : null}
                  <View
                    style={{
                      flexDirection: 'row',
                      justifyContent: 'space-between',
                      alignItems: 'center',
                      paddingVertical: 12,
                      paddingHorizontal: 16,
                    }}
                  >
                    <Text style={{ color: theme.colors.textPrimary, fontSize: 14 }}>{entry.name}</Text>
                    <Text style={{ color: theme.colors.textSecondary, fontSize: 14 }}>
                      {formatMinutes(entry.minutes * 60)}
                    </Text>
                  </View>
                </View>
              ))}
            </YanjiCard>
          </>
        ) : null}

        {dayTasks.length > 0 ? (
          <>
            <YanjiSectionHeader title="任务完成" />
            <YanjiCard>
              <View
                style={{
                  flexDirection: 'row',
                  justifyContent: 'space-between',
                  alignItems: 'baseline',
                  paddingVertical: 6,
                }}
              >
                <Text style={{ color: theme.colors.textSecondary, fontSize: 13 }}>已完成</Text>
                <Text style={{ color: theme.colors.textPrimary, fontSize: 15, fontWeight: '600' }}>
                  {completedTaskCount} / {dayTasks.length}
                </Text>
              </View>
            </YanjiCard>
          </>
        ) : null}
      </ScrollView>
    </View>
  );
}

/** Two-up tally strip, matching the hero sheet on the Today screen. */
function ReviewStat({ label, value }: { label: string; value: string }): React.JSX.Element {
  const theme = useYanjiTheme();
  return (
    <View style={{ flex: 1 }}>
      <Text style={[theme.typography.label, { color: theme.colors.textTertiary }]}>{label}</Text>
      <Text
        style={{
          color: theme.colors.textPrimary,
          fontSize: 17,
          fontWeight: '600',
          marginTop: 2,
        }}
      >
        {value}
      </Text>
    </View>
  );
}
