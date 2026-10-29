/**
 * The running focus session, shown wherever the reader happens to be.
 *
 * Two shapes, one clock: Today prints a readout block beside its 返回专注
 * button, Review prints a one-line capsule under its title so a session is
 * never invisible from the page that is reviewing the day. The tick
 * subscription lives here so both pages read the same store, and only while
 * their page is visible — a hidden page must not keep a per-second subscriber
 * alive.
 */
import React from 'react';
import { Pressable, Text, View } from 'react-native';
import type { ActiveSessionState } from '../bridge';
import { useActiveSession, useTimerTick } from '../state/timerStore';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing } from '../theme/tokens';
import { YanjiIcon } from './YanjiUI';

function clock(seconds: number): string {
  const whole = Math.max(0, Math.floor(seconds));
  const hours = Math.floor(whole / 3600);
  const minutes = String(Math.floor((whole % 3600) / 60)).padStart(2, '0');
  const remainder = String(whole % 60).padStart(2, '0');
  return hours ? `${hours}:${minutes}:${remainder}` : `${minutes}:${remainder}`;
}

/**
 * The live numbers for a session: the native snapshot until the first tick
 * arrives, then the tick's fresher pair. A pause reported by either side wins.
 */
function useLiveSessionClock(session: ActiveSessionState, visible: boolean) {
  const tick = useTimerTick(visible);
  const seconds = session.isCountdown
    ? tick?.remainingSeconds ?? session.remainingSeconds
    : tick?.elapsedSeconds ?? session.elapsedSeconds;
  return { seconds, isPaused: session.isPaused || Boolean(tick?.isPaused) };
}

export function LiveSessionReadout({ session, visible }: {
  session: ActiveSessionState; visible: boolean;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { seconds, isPaused } = useLiveSessionClock(session, visible);
  return <View style={{ flexGrow: 1, flexBasis: paper.layout.activeSessionReadoutMinWidth }}>
    <Text style={[paper.typography.captionMeta, { color: isPaused ? paper.colors.secondary : paper.colors.brandInk }]}>
      {session.subjectName} {isPaused ? '已暂停' : '正在专注'}
    </Text>
    <View accessible accessibilityLabel={`${session.isCountdown ? '本轮剩余' : '本轮已持续'} ${clock(seconds)}，${isPaused ? '已暂停' : '正在专注'}`}
      accessibilityHint="本轮尚未计入今日已保存总量"
      style={{ flexDirection: 'row', flexWrap: 'wrap', alignItems: 'baseline', columnGap: YanjiSpacing.sm }}>
      <Text style={[paper.typography.metricSession, { color: paper.colors.ink }]}>{clock(seconds)}</Text>
      <Text style={[paper.typography.captionMeta, { color: paper.colors.secondary }]}>{session.isCountdown ? '本轮剩余' : '已持续'}</Text>
    </View>
  </View>;
}

/**
 * One tappable line: subject and state on the left, the live clock and a
 * chevron on the right. It renders nothing without a running session, so a
 * page can mount it unconditionally.
 */
export function LiveSessionCapsule({ visible, onPress }: {
  visible: boolean;
  onPress: () => void;
}): React.JSX.Element | null {
  const session = useActiveSession(visible);
  if (!session) return null;
  return <SessionCapsuleBody session={session} visible={visible} onPress={onPress} />;
}

function SessionCapsuleBody({ session, visible, onPress }: {
  session: ActiveSessionState; visible: boolean; onPress: () => void;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout, radius } = paper;
  const { seconds, isPaused } = useLiveSessionClock(session, visible);
  const stateLabel = isPaused ? '已暂停' : '正在专注';
  return <Pressable
    onPress={onPress}
    accessibilityRole="button"
    accessibilityLabel={`${session.subjectName}，${stateLabel}，${session.isCountdown ? '本轮剩余' : '已持续'} ${clock(seconds)}`}
    accessibilityHint="返回专注工作台；本轮尚未计入今日已保存总量"
    style={({ pressed }) => ({
      marginTop: YanjiSpacing.sm,
      minHeight: layout.rowMinHeight,
      paddingHorizontal: YanjiSpacing.md,
      flexDirection: 'row',
      alignItems: 'center',
      justifyContent: 'space-between',
      gap: YanjiSpacing.sm,
      backgroundColor: colors.brandSubtle,
      borderRadius: radius.pill,
      opacity: pressed ? 0.65 : 1,
    })}
  >
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.sm, flexShrink: 1, minWidth: 0 }}>
      <View style={{
        width: 7, height: 7, borderRadius: radius.pill,
        backgroundColor: isPaused ? colors.secondary : colors.brand,
      }} />
      <Text numberOfLines={1} style={[typography.captionMeta, { color: colors.brandInk }]}>
        {session.subjectName} · {stateLabel}
      </Text>
    </View>
    <View style={{ flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.xs, flexShrink: 0 }}>
      <Text style={[typography.captionMeta, { color: colors.ink, fontVariant: ['tabular-nums'] }]}>
        {clock(seconds)}
      </Text>
      <YanjiIcon name="forward" size={13} color={colors.brandInk} />
    </View>
  </Pressable>;
}
