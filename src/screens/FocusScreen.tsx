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
  type ViewStyle,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Defs, LinearGradient, RadialGradient, Rect, Stop } from 'react-native-svg';
import { YanjiDataNative, YanjiTimerNative, onDataChanged } from '../bridge';
import type { ActiveSessionState, Subject, TimerMode } from '../bridge';
import { YanjiIcon, YanjiPaperSegmentedControl } from '../components/YanjiUI';
import { FocusVernierDial } from '../components/FocusVernierDial';
import { RollingTimeDisplay } from '../components/RollingTimeDisplay';
import { RecordMomentModal } from '../components/RecordMomentModal';
import { useNavigationActions, useNavigationState } from '../navigation/NavigationShell';
import { useBottomTabLayout } from '../navigation/useBottomTabLayout';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing, YanjiTouch } from '../theme/tokens';
import { useDailySnapshot } from '../state/dataRefresh';
import { todayIso } from '../state/settingsViewLogic';
import { syncActiveSession, useActiveSession, useTimerTick } from '../state/timerStore';

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

interface SubjectGroup {
  category: Subject;
  children: Subject[];
}

function splitClockParts(totalSeconds: number): { main: string; sub: string } {
  const safe = Math.max(0, Math.floor(totalSeconds));
  const hours = Math.floor(safe / 3600);
  const minutes = String(Math.floor((safe % 3600) / 60)).padStart(2, '0');
  const seconds = String(safe % 60).padStart(2, '0');
  return hours > 0 ? { main: `${hours}:${minutes}`, sub: seconds } : { main: minutes, sub: seconds };
}

/** The dial reads as a duration, not a clock: 45 → `45:00`, 180 → `180:00`. */
function formatDialMinutes(minutes: number): string {
  return `${String(Math.max(0, Math.floor(minutes))).padStart(2, '0')}:00`;
}

/** Projection only — it never writes a planned duration, it just reads the clock. */
function formatEstimatedEndTime(durationMinutes: number): string {
  const end = new Date(Date.now() + durationMinutes * 60 * 1000);
  const hh = String(end.getHours()).padStart(2, '0');
  const mm = String(end.getMinutes()).padStart(2, '0');
  return `预计 ${hh}:${mm} 结束本轮`;
}

export function FocusScreen({ visible }: { visible: boolean }): React.JSX.Element {
  const theme = useYanjiTheme();
  const paper = theme.paper;
  const { contentPadding } = useBottomTabLayout();
  const safe = useSafeAreaInsets();
  const { width } = useWindowDimensions();
  const [date] = useState(todayIso);
  const session = useActiveSession(visible);
  // Today's per-subject totals come from the shared daily snapshot the refresh
  // coordinator already owns — the same read TodayScreen renders, so the two
  // pages can never disagree about today's minutes.
  const daily = useDailySnapshot('focus', date, visible);

  const [enabledSubjects, setEnabledSubjects] = useState<Subject[]>([]);
  const [subjectLoadState, setSubjectLoadState] = useState<'loading' | 'ready' | 'error'>('loading');
  const [selectedSubjectId, setSelectedSubjectId] = useState<string | null>(null);
  const [durationMinutes, setDurationMinutes] = useState(DEFAULT_MINUTES);
  const [timerMode, setTimerMode] = useState<TimerMode>('COUNTDOWN');
  const [recordOpen, setRecordOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [taskBinding, setTaskBinding] = useState<TaskBinding | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const { focusPreset } = useNavigationState();
  const { clearFocusPreset } = useNavigationActions();

  const loadSubjects = useCallback(() => {
    setSubjectLoadState('loading');
    void YanjiDataNative.getSubjects()
      .then(list => {
        setEnabledSubjects(
          list.filter(subject => subject.enabled).sort((a, b) => a.sortOrder - b.sortOrder)
        );
        setSubjectLoadState('ready');
      })
      .catch(() => {
        setEnabledSubjects([]);
        setSubjectLoadState('error');
      });
  }, []);

  useEffect(() => {
    if (visible) {
      loadSubjects();
    }
  }, [visible, loadSubjects]);
  useEffect(() => {
    return onDataChanged(event => {
      if (event.type === 'subjects') {
        loadSubjects();
      }
    });
  }, [loadSubjects]);
  useEffect(() => { setActionError(null); }, [session?.sessionId]);

  /**
   * Two-level subject grouping.
   *
   * A top-level subject with enabled children is a category: its card selects
   * the first child and reveals the child strip. A top-level subject with no
   * children (英语一 / 政治) is itself the choice — it must still get a card,
   * which is why every root is kept rather than only the ones with children.
   */
  const subjectGroups = useMemo<SubjectGroup[]>(() => {
    const roots = enabledSubjects.filter(
      subject => subject.parentId === null || !enabledSubjects.some(p => p.id === subject.parentId)
    );
    return roots.map(category => ({
      category,
      children: enabledSubjects.filter(child => child.parentId === category.id),
    }));
  }, [enabledSubjects]);

  /** Leaf subjects a session may actually be started against. */
  const selectableSubjects = useMemo(
    () => subjectGroups.flatMap(group => group.children.length > 0 ? group.children : [group.category]),
    [subjectGroups]
  );

  useEffect(() => {
    if (subjectLoadState !== 'ready') return;
    setSelectedSubjectId(previous =>
      selectableSubjects.some(subject => subject.id === previous)
        ? previous
        : selectableSubjects[0]?.id ?? null
    );
  }, [selectableSubjects, subjectLoadState]);

  const selectedSubject = useMemo(
    () => selectableSubjects.find(subject => subject.id === selectedSubjectId) ?? null,
    [selectableSubjects, selectedSubjectId]
  );

  /**
   * What the running session is actually about, for the immersion field's one
   * context line. The session carries only a `taskId`, so the title comes from
   * the daily snapshot this screen already reads — no second query. A session
   * that outlived its day (or was started unbound) simply falls back to the
   * quiet standing copy rather than showing a stale or invented title.
   */
  const taskTitle = useMemo(() => {
    if (!session?.taskId) return null;
    return daily?.tasks.find(task => task.id === session.taskId)?.title ?? null;
  }, [daily, session?.taskId]);

  const activeGroup = useMemo<SubjectGroup | null>(() => {
    if (!selectedSubject) return subjectGroups[0] ?? null;
    return subjectGroups.find(group =>
      group.category.id === selectedSubject.id ||
      group.children.some(child => child.id === selectedSubject.id)
    ) ?? subjectGroups[0] ?? null;
  }, [selectedSubject, subjectGroups]);

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

  const handleSelect = useCallback((subjectId: string) => {
    setSelectedSubjectId(subjectId);
    setTaskBinding(previous => previous?.subjectId === subjectId ? previous : null);
    setActionError(null);
  }, []);

  const handleSelectCategory = useCallback((group: SubjectGroup) => {
    handleSelect(group.children[0]?.id ?? group.category.id);
  }, [handleSelect]);

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
    const cardWidth = Math.floor((width - paper.layout.pageGutter * 2 - paper.layout.gridGap) / 2);

    return (
      <View testID="focus-ready" style={{ flex: 1, backgroundColor: paper.colors.canvas }}>
        <ScrollView
          testID="scroll-focus"
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
          contentContainerStyle={{
            flexGrow: 1,
            paddingHorizontal: paper.layout.pageGutter,
            paddingTop: YanjiSpacing.sm,
            paddingBottom: contentPadding,
          }}
        >
          {/* Left action | centred mode switch | balancing placeholder.
              The two 84dp shoulders are what keep the switch on the page axis. */}
          <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel="补录记录"
              hitSlop={8}
              onPress={() => setRecordOpen(true)}
              style={({ pressed }) => ({
                minWidth: 84,
                minHeight: YanjiTouch.min,
                flexDirection: 'row',
                alignItems: 'center',
                opacity: pressed ? 0.68 : 1,
              })}
            >
              <YanjiIcon name="compose" size={15} color={paper.colors.secondary} />
              <Text style={[paper.typography.captionMeta, { color: paper.colors.secondary, marginLeft: 5 }]}>
                补录记录
              </Text>
            </Pressable>

            <YanjiPaperSegmentedControl
              value={timerMode}
              options={TIMER_MODES}
              onChange={setTimerMode}
              accessibilityLabel="计时模式"
              compact
            />

            <View style={{ minWidth: 84, minHeight: YanjiTouch.min }} />
          </View>

          <View style={{ marginTop: YanjiSpacing.md }}>
            <Text
              accessibilityRole="header"
              style={[paper.typography.titleSection, { color: paper.colors.ink, marginBottom: YanjiSpacing.sm }]}
            >
              选择科目
            </Text>

            {subjectGroups.length > 0 ? (
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: paper.layout.gridGap }}>
                {subjectGroups.map(group => {
                  const isActive = activeGroup?.category.id === group.category.id;
                  const accent = group.category.colorHex || paper.colors.brand;
                  return (
                    <Pressable
                      key={group.category.id}
                      accessibilityRole="button"
                      accessibilityLabel={group.category.name}
                      accessibilityState={{ selected: isActive }}
                      onPress={() => handleSelectCategory(group)}
                      style={({ pressed }) => ({
                        width: cardWidth,
                        minHeight: YanjiTouch.min,
                        paddingVertical: 10,
                        paddingHorizontal: YanjiSpacing.md,
                        borderRadius: paper.radius.compactCard,
                        backgroundColor: paper.colors.surface,
                        borderWidth: isActive ? 1.5 : paper.layout.hairlineWidth,
                        borderColor: isActive ? accent : paper.colors.hairline,
                        flexDirection: 'row',
                        alignItems: 'center',
                        gap: YanjiSpacing.sm,
                        opacity: pressed ? 0.72 : isActive ? 1 : 0.76,
                      })}
                    >
                      <View style={{ width: 8, height: 8, borderRadius: paper.radius.pill, backgroundColor: accent }} />
                      <Text
                        numberOfLines={1}
                        style={[
                          paper.typography.bodyPrimary,
                          { color: paper.colors.ink, fontWeight: isActive ? '700' : '500', flex: 1 },
                        ]}
                      >
                        {group.category.name}
                      </Text>
                    </Pressable>
                  );
                })}
              </View>
            ) : (
              <View style={{ minHeight: paper.layout.rowMinHeight, justifyContent: 'center' }}>
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
            )}

            {/* Level 2: the active category's children, only when it has any. */}
            {activeGroup && activeGroup.children.length > 0 ? (
              <ScrollView
                horizontal
                showsHorizontalScrollIndicator={false}
                contentContainerStyle={{ gap: YanjiSpacing.sm, paddingTop: YanjiSpacing.sm }}
              >
                {activeGroup.children.map(child => {
                  const selected = child.id === selectedSubjectId;
                  const accent = child.colorHex || activeGroup.category.colorHex || paper.colors.brand;
                  return (
                    <Pressable
                      key={child.id}
                      accessibilityRole="button"
                      accessibilityLabel={child.name}
                      accessibilityState={{ selected }}
                      onPress={() => handleSelect(child.id)}
                      style={({ pressed }) => ({
                        minHeight: YanjiTouch.min,
                        paddingHorizontal: YanjiSpacing.md,
                        borderRadius: paper.radius.pill,
                        backgroundColor: selected ? paper.colors.surface : paper.colors.subtle,
                        borderWidth: selected ? 1.5 : paper.layout.hairlineWidth,
                        borderColor: selected ? accent : paper.colors.hairline,
                        flexDirection: 'row',
                        alignItems: 'center',
                        gap: 6,
                        opacity: pressed ? 0.72 : 1,
                      })}
                    >
                      <View
                        style={{
                          width: 6,
                          height: 6,
                          borderRadius: paper.radius.pill,
                          backgroundColor: selected ? accent : paper.colors.muted,
                        }}
                      />
                      <Text
                        style={[
                          paper.typography.captionMeta,
                          { color: selected ? paper.colors.ink : paper.colors.secondary, fontWeight: selected ? '700' : '500' },
                        ]}
                      >
                        {child.name}
                      </Text>
                    </Pressable>
                  );
                })}
              </ScrollView>
            ) : null}

            {taskBinding ? (
              <View
                style={{
                  marginTop: YanjiSpacing.sm,
                  paddingLeft: YanjiSpacing.md,
                  paddingRight: YanjiSpacing.xs,
                  paddingVertical: YanjiSpacing.xs,
                  borderRadius: paper.radius.compactCard,
                  backgroundColor: paper.colors.brandSubtle,
                  flexDirection: 'row',
                  alignItems: 'center',
                }}
              >
                <YanjiIcon name="tasks" size={16} color={paper.colors.brandInk} />
                <Text
                  numberOfLines={1}
                  style={[
                    paper.typography.captionMeta,
                    { color: paper.colors.brandInk, fontWeight: '700', flex: 1, marginHorizontal: YanjiSpacing.sm },
                  ]}
                >
                  关联任务 · {taskBinding.title}
                </Text>
                <Pressable
                  accessibilityRole="button"
                  accessibilityLabel="取消关联任务"
                  hitSlop={8}
                  onPress={() => setTaskBinding(null)}
                  style={{ minWidth: YanjiTouch.min, minHeight: YanjiTouch.min, alignItems: 'center', justifyContent: 'center' }}
                >
                  <YanjiIcon name="close" size={16} color={paper.colors.secondary} />
                </Pressable>
              </View>
            ) : null}
          </View>

          <View style={{ flexGrow: 1, justifyContent: 'center', alignItems: 'center', marginVertical: YanjiSpacing.sm }}>
            {timerMode === 'COUNTDOWN' ? (
              <>
                <Text style={[paper.typography.captionMeta, { color: paper.colors.secondary }]}>
                  {formatEstimatedEndTime(durationMinutes)}
                </Text>

                <RollingTimeDisplay
                  valueMinutes={durationMinutes}
                  color={paper.colors.ink}
                  style={{ marginVertical: YanjiSpacing.xs }}
                />

                <Text
                  testID="focus-duration-value"
                  style={{ position: 'absolute', opacity: 0, width: 0, height: 0 }}
                  accessible={false}
                >
                  {formatDialMinutes(durationMinutes)}
                </Text>

                {/* Full-bleed strip: the ruler belongs to the page, not to a card. */}
                <View
                  style={{
                    alignSelf: 'stretch',
                    marginHorizontal: -paper.layout.pageGutter,
                    marginTop: YanjiSpacing.md,
                    backgroundColor: paper.colors.vernierWash,
                    borderTopWidth: paper.layout.hairlineWidth,
                    borderBottomWidth: paper.layout.hairlineWidth,
                    borderColor: paper.colors.hairline,
                  }}
                >
                  <FocusVernierDial
                    valueMinutes={durationMinutes}
                    onChangeMinutes={setDurationMinutes}
                    minMinutes={MIN_MINUTES}
                    maxMinutes={MAX_MINUTES}
                    stepMinutes={5}
                    accessibilityLabel="调整倒计时长度"
                  />
                </View>
              </>
            ) : (
              <View style={{ paddingVertical: YanjiSpacing.xl, alignItems: 'center' }}>
                <Text style={[paper.typography.captionMeta, { color: paper.colors.secondary }]}>
                  自由正向计时 · 不设上限
                </Text>
                <Text
                  style={[
                    paper.typography.displayDial,
                    { color: paper.colors.ink, marginVertical: YanjiSpacing.xs, textAlign: 'center' },
                  ]}
                >
                  00:00
                </Text>
                <Text style={[paper.typography.bodyPrimary, { color: paper.colors.secondary, textAlign: 'center' }]}>
                  随时暂停或结算本轮专注时长
                </Text>
              </View>
            )}
          </View>

          <View style={{ marginTop: YanjiSpacing.md }}>
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={busy ? '正在启动专注' : '开始专注'}
              disabled={busy || !selectedSubject}
              onPress={handleStart}
              style={({ pressed }) => ({
                minHeight: 56,
                borderRadius: paper.radius.cta,
                backgroundColor: busy || !selectedSubject ? paper.colors.subtle : paper.colors.ink,
                flexDirection: 'row',
                alignItems: 'center',
                justifyContent: 'center',
                gap: YanjiSpacing.sm,
                opacity: pressed ? 0.85 : 1,
                ...(busy || !selectedSubject ? null : theme.shadow.floating(theme.isDark)),
              })}
            >
              <Text
                style={{
                  fontSize: 16,
                  fontWeight: '700',
                  color: busy || !selectedSubject ? paper.colors.secondary : paper.colors.canvas,
                }}
              >
                {busy ? '启动中…' : '开始专注'}
              </Text>
              {!busy ? (
                <Text
                  style={[
                    paper.typography.captionMeta,
                    {
                      color: busy || !selectedSubject ? paper.colors.secondary : paper.colors.canvas,
                      opacity: 0.72,
                    },
                  ]}
                >
                  · {timerMode === 'COUNTDOWN' ? `${durationMinutes} MIN` : '正计时'}
                </Text>
              ) : null}
            </Pressable>

            {actionError ? (
              <Text
                accessibilityRole="alert"
                style={[
                  paper.typography.bodyPrimary,
                  { color: paper.colors.danger, textAlign: 'center', marginTop: YanjiSpacing.md },
                ]}
              >
                {actionError}
              </Text>
            ) : null}
          </View>
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
    <View testID={isPaused ? 'focus-paused' : 'focus-running'} style={{ flex: 1, backgroundColor: immersed.canvas }}>
      {/* Horizon tide: a wash that rises from the bottom edge and colours the
          whole field with the session's accent.

          Two layers, because a single tall linear ramp still read as a cliff:
          at 32% height the gradient's whole travel sat below the eye line, and
          near its low end its alpha converged on the canvas's own value, so the
          first opaque pixel of the field looked like a seam. A radial falloff
          is what actually reads as "tide", and RN has no radial-gradient — but
          `react-native-svg` ships RadialGradient, so the falloff is drawn as
          one: a wide, flat ellipse rising 58% from the bottom, hottest at the
          centre of the bottom edge and fully gone by its rim. The linear layer
          under it ramps the full 58% at four stops (0 / 35 / 70 / 100%), which
          keeps the middle band moving gradually instead of arriving in one
          jump.

          The stop alpha rides on `stopOpacity`, never on an `rgba()` colour:
          `react-native-svg` masks the alpha back out of `stopColor` and reads
          opacity from `stopOpacity` alone, defaulting it to fully opaque. With
          an rgba() colour every stop paints solid, so the wash rendered as a
          hard-edged slab (see the `tideOpacity` note in tokens.ts). */}
      <View pointerEvents="none" style={{ position: 'absolute', left: 0, right: 0, bottom: 0, height: '58%' }}>
        <Svg width="100%" height="100%">
          <Defs>
            {/* Centre below the bottom edge (106%), so the field never shows the
                gradient's own centre or rim: the visible band is only the top
                of the falloff, which is why there is no line to see. Radii are
                in object-bounding-box units — 118% of the width and 66% of the
                height — giving a wide, flat bloom. Android honours cx/cy/rx/ry
                but ignores the focal point, so fx/fy are not passed. */}
            <RadialGradient id="focus-horizon-bloom" cx="50%" cy="106%" rx="118%" ry="66%">
              <Stop offset="0%" stopColor={immersed.tideColor} stopOpacity={immersed.tideOpacity} />
              <Stop offset="42%" stopColor={immersed.tideColor} stopOpacity={immersed.tideOpacity * 0.35} />
              <Stop offset="70%" stopColor={immersed.tideColor} stopOpacity={0} />
            </RadialGradient>
            <LinearGradient id="focus-horizon-tide" x1="0%" y1="0%" x2="0%" y2="100%">
              <Stop offset="0%" stopColor={immersed.tideColor} stopOpacity={0} />
              <Stop offset="35%" stopColor={immersed.tideColor} stopOpacity={immersed.tideOpacity * 0.25} />
              <Stop offset="70%" stopColor={immersed.tideColor} stopOpacity={immersed.tideOpacity * 0.7} />
              <Stop offset="100%" stopColor={immersed.tideColor} stopOpacity={immersed.tideOpacity} />
            </LinearGradient>
          </Defs>
          <Rect x="0" y="0" width="100%" height="100%" fill="url(#focus-horizon-tide)" />
          <Rect x="0" y="0" width="100%" height="100%" fill="url(#focus-horizon-bloom)" />
        </Svg>
      </View>

      <ScrollView
        showsVerticalScrollIndicator={false}
        contentContainerStyle={{
          flexGrow: 1,
          justifyContent: 'space-between',
          alignItems: 'stretch',
          paddingHorizontal: paper.layout.pageGutter,
          paddingTop: YanjiSpacing.md,
          paddingBottom: Math.max(safe.bottom, YanjiSpacing.lg) + 28,
        }}
      >
        {/* AB-04 keeps this field to one centred column: the session's own
            context pill, then what this session is actually about. There is
            deliberately no back affordance — the immersion field is exited
            with the system back gesture, which AppShell routes to Today. */}
        <View style={{ alignItems: 'center', gap: 6 }}>
          <View
            style={{
              flexDirection: 'row',
              alignItems: 'center',
              gap: 7,
              paddingVertical: 6,
              paddingHorizontal: 14,
              borderRadius: paper.radius.pill,
              backgroundColor: immersed.pillBg,
              borderWidth: paper.layout.hairlineWidth,
              borderColor: immersed.pillBorder,
            }}
          >
            {/* `immersed` is already the paused palette in this branch, so the dot
                carries `immersive.paused.accent` while the session is held. The
                pill is the field's only status line now that the readout no
                longer prints 「已专注 / 剩余」 — a held session must say so. */}
            <View style={{ width: 7, height: 7, borderRadius: paper.radius.pill, backgroundColor: immersed.accent }} />
            <Text numberOfLines={1} style={[paper.typography.captionMeta, { color: immersed.secondary }]}>
              {session.subjectName} · {isPaused ? '已暂停' : session.isCountdown ? '倒计时' : '正计时'}
            </Text>
          </View>

          <Text
            numberOfLines={1}
            style={[paper.typography.captionMeta, { color: immersed.subtitle, textAlign: 'center' }]}
          >
            {taskTitle ?? (isPaused ? '专注已暂歇 · 随时继续当前进度' : '全神贯注 · 把时间留给眼前这一件事')}
          </Text>
        </View>

        <View style={{ alignItems: 'center', paddingVertical: YanjiSpacing.xl }}>
          <FocusTimerReadout session={session} visible={visible} />
        </View>

        <View style={{ alignItems: 'center', gap: 18 }}>
          {isPaused ? (
            <View style={{ alignSelf: 'stretch', gap: YanjiSpacing.md }}>
              <View style={{ flexDirection: 'row', justifyContent: 'center', gap: 16 }}>
                <ImmersiveAction
                  label="记录此刻"
                  icon="compose"
                  variant="secondary"
                  paused
                  onPress={() => setRecordOpen(true)}
                />
                <ImmersiveAction
                  label={busy ? '处理中…' : '继续专注'}
                  icon="play"
                  variant="primary"
                  paused
                  disabled={busy}
                  onPress={() => { void handleTimerAction('resume'); }}
                />
              </View>
              <View style={{ flexDirection: 'row', justifyContent: 'center', gap: 16 }}>
                <ImmersiveAction
                  label="提前结束"
                  icon="check"
                  variant="ghost"
                  paused
                  disabled={busy}
                  onPress={() => { void handleTimerAction('complete'); }}
                />
                <ImmersiveAction
                  label="放弃本轮"
                  icon="delete"
                  variant="danger"
                  paused
                  disabled={busy}
                  onPress={confirmDiscard}
                />
              </View>
            </View>
          ) : (
            <View style={{ flexDirection: 'row', justifyContent: 'center', gap: 16 }}>
              <ImmersiveAction
                label="记录此刻"
                icon="compose"
                variant="secondary"
                onPress={() => setRecordOpen(true)}
              />
              <ImmersiveAction
                label={busy ? '处理中…' : '暂停专注'}
                icon="pause"
                variant="primary"
                disabled={busy}
                onPress={() => { void handleTimerAction('pause'); }}
              />
            </View>
          )}

          {actionError ? (
            <Text
              accessibilityRole="alert"
              style={[paper.typography.captionMeta, { color: paper.colors.danger, textAlign: 'center' }]}
            >
              {actionError}
            </Text>
          ) : null}

          <Text style={[paper.typography.captionMeta, { color: immersed.subtitle, textAlign: 'center' }]}>
            锁屏或切到其他应用后，本轮计时仍会继续
          </Text>
        </View>
      </ScrollView>

      <RecordMomentModal visible={recordOpen} date={date} onClose={() => setRecordOpen(false)} />
    </View>
  );
}

/**
 * The clock, and nothing else.
 *
 * A session's progress is already legible from the countdown itself, so this
 * leaf deliberately renders no bar and no 「已专注 / 剩余」 legend: a second
 * readout of the same two numbers competed with the clock for the field's
 * single focal point. Pause state is stated once, on the context pill above,
 * which sits outside the tick boundary.
 */
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
  const paused = session.isPaused || tick?.isPaused === true;
  const immersed = paused ? theme.paper.immersive.paused : theme.paper.immersive.running;
  const { main, sub } = splitClockParts(seconds);

  return (
    <View style={{ alignItems: 'center', alignSelf: 'stretch' }}>
      <Text
        testID="focus-native-clock"
        adjustsFontSizeToFit
        minimumFontScale={0.45}
        numberOfLines={1}
        style={[
          theme.paper.typography.displayTimer,
          {
            color: immersed.ink,
            maxWidth: Math.max(180, width - 2 * theme.paper.layout.pageGutter),
            fontSize: Math.min(
              theme.paper.typography.displayTimer.fontSize,
              Math.max(60, width * 0.245)
            ),
            textAlign: 'center',
          },
        ]}
      >
        {main}
        <Text style={{ opacity: 0.45 }}>:</Text>
        {sub}
      </Text>
    </View>
  );
}

/** The immersive field's one action shape, in four weights. */
function ImmersiveAction({
  label,
  icon,
  onPress,
  variant,
  paused = false,
  disabled = false,
  style,
}: {
  label: string;
  icon: 'compose' | 'play' | 'pause' | 'check' | 'delete';
  onPress: () => void;
  variant: 'primary' | 'secondary' | 'ghost' | 'danger';
  paused?: boolean;
  disabled?: boolean;
  style?: ViewStyle;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const immersed = paused ? theme.paper.immersive.paused : theme.paper.immersive.running;
  const isPrimary = variant === 'primary';
  const isGhost = variant === 'ghost';
  const isDanger = variant === 'danger';
  const quiet = isGhost || isDanger;

  const background = quiet
    ? 'transparent'
    : isPrimary
      ? immersed.primaryActionBg
      : immersed.secondaryActionBg;
  const border = isDanger
    ? 'transparent'
    : isGhost
      ? immersed.secondaryActionBorder
      : isPrimary
        ? immersed.primaryActionBorder
        : immersed.secondaryActionBorder;
  const content = isDanger
    ? immersed.danger
    : isGhost
      ? immersed.secondary
      : isPrimary
        ? immersed.primaryActionText
        : immersed.secondaryActionText;

  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={label}
      disabled={disabled}
      onPress={onPress}
      style={({ pressed }) => [
        {
          minHeight: quiet ? YanjiTouch.min : 52,
          paddingHorizontal: YanjiSpacing.xl,
          borderRadius: theme.paper.radius.pill,
          backgroundColor: background,
          borderWidth: theme.paper.layout.hairlineWidth,
          borderColor: border,
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'center',
          gap: YanjiSpacing.sm,
          opacity: disabled ? 0.45 : pressed ? 0.75 : 1,
        },
        style,
      ]}
    >
      <YanjiIcon name={icon} size={quiet ? 15 : 16} color={content} />
      <Text
        style={
          quiet
            ? [theme.paper.typography.captionMeta, { color: content, fontWeight: isDanger ? '600' : '500' }]
            : [theme.paper.typography.bodyPrimary, { color: content, fontWeight: isPrimary ? '700' : '500' }]
        }
      >
        {label}
      </Text>
    </Pressable>
  );
}
