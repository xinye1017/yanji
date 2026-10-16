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
  Modal,
  Pressable,
  ScrollView,
  Text,
  View,
  useWindowDimensions,
  type ViewStyle,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Svg, { Defs, LinearGradient, Rect, Stop } from 'react-native-svg';
import { YanjiDataNative, YanjiTimerNative } from '../bridge';
import type { ActiveSessionState, Subject, TimerMode } from '../bridge';
import {
  YanjiHairline,
  YanjiIcon,
  YanjiPaperSegmentedControl,
  YanjiPaperSheetHeading,
  YanjiPrimaryButton,
  YanjiTextInput,
  useYanjiKeyboardInset,
} from '../components/YanjiUI';
import { FocusVernierDial } from '../components/FocusVernierDial';
import { RecordMomentModal } from '../components/RecordMomentModal';
import { useNavigationActions, useNavigationState } from '../navigation/NavigationShell';
import { useBottomTabLayout } from '../navigation/useBottomTabLayout';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing, YanjiTouch } from '../theme/tokens';
import { useDailySnapshot } from '../state/dataRefresh';
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

function formatClock(totalSeconds: number): string {
  const { main, sub } = splitClockParts(totalSeconds);
  return `${main}:${sub}`;
}

/** The dial reads as a duration, not a clock: 45 → `45:00`, 180 → `180:00`. */
function formatDialMinutes(minutes: number): string {
  return `${String(Math.max(0, Math.floor(minutes))).padStart(2, '0')}:00`;
}

function formatSubjectDuration(seconds: number): string {
  const totalMinutes = Math.floor(Math.max(0, seconds) / 60);
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  if (hours === 0) return `${minutes}m`;
  return minutes === 0 ? `${hours}h` : `${hours}h${minutes}m`;
}

/** Projection only — it never writes a planned duration, it just reads the clock. */
function formatEstimatedEndTime(durationMinutes: number): string {
  const end = new Date(Date.now() + durationMinutes * 60 * 1000);
  const hh = String(end.getHours()).padStart(2, '0');
  const mm = String(end.getMinutes()).padStart(2, '0');
  return `预计 ${hh}:${mm} 结束本轮`;
}

function todayIso(): string {
  const now = new Date();
  return [
    now.getFullYear(),
    String(now.getMonth() + 1).padStart(2, '0'),
    String(now.getDate()).padStart(2, '0'),
  ].join('-');
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
  const [customDurationOpen, setCustomDurationOpen] = useState(false);
  const [customDurationText, setCustomDurationText] = useState('');
  const [customDurationError, setCustomDurationError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [taskBinding, setTaskBinding] = useState<TaskBinding | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const { focusPreset } = useNavigationState();
  const { clearFocusPreset } = useNavigationActions();
  const keyboardInset = useYanjiKeyboardInset();

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

  useEffect(() => { loadSubjects(); }, [loadSubjects]);
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

  const subjectSecondsMap = useMemo(() => {
    const map: Record<string, number> = {};
    for (const item of daily?.sessions ?? []) {
      map[item.subjectId] = (map[item.subjectId] ?? 0) + item.durationSeconds;
    }
    return map;
  }, [daily]);

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

  /** A category card reports its own sessions plus every child's. */
  const groupSeconds = useCallback(
    (group: SubjectGroup): number =>
      group.children.reduce(
        (sum, child) => sum + (subjectSecondsMap[child.id] ?? 0),
        subjectSecondsMap[group.category.id] ?? 0
      ),
    [subjectSecondsMap]
  );

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

  const handleConfirmCustomDuration = useCallback(() => {
    const parsed = Number.parseInt(customDurationText.trim(), 10);
    if (!Number.isFinite(parsed) || parsed < MIN_MINUTES || parsed > MAX_MINUTES) {
      setCustomDurationError(`请输入 ${MIN_MINUTES}–${MAX_MINUTES} 之间的整数`);
      return;
    }
    setDurationMinutes(parsed);
    setCustomDurationOpen(false);
  }, [customDurationText]);

  if (!session) {
    const cardWidth = Math.floor((width - paper.layout.pageGutter * 2 - paper.layout.gridGap) / 2);
    const isCustomDuration = !(DURATION_PRESETS as readonly number[]).includes(durationMinutes);

    return (
      <View testID="focus-ready" style={{ flex: 1, backgroundColor: paper.colors.canvas }}>
        <ScrollView
          testID="scroll-focus"
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
          contentContainerStyle={{
            flexGrow: 1,
            justifyContent: 'space-between',
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
                        justifyContent: 'space-between',
                        opacity: pressed ? 0.72 : isActive ? 1 : 0.76,
                      })}
                    >
                      <View style={{ flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.sm, flex: 1, marginRight: YanjiSpacing.xs }}>
                        <View style={{ width: 8, height: 8, borderRadius: paper.radius.pill, backgroundColor: accent }} />
                        <Text
                          numberOfLines={1}
                          style={[
                            paper.typography.bodyPrimary,
                            { color: paper.colors.ink, fontWeight: isActive ? '700' : '500', flexShrink: 1 },
                          ]}
                        >
                          {group.category.name}
                        </Text>
                      </View>
                      <Text
                        style={[
                          paper.typography.captionMeta,
                          { color: isActive ? accent : paper.colors.secondary },
                        ]}
                      >
                        今日 {formatSubjectDuration(groupSeconds(group))}
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

          <View style={{ alignItems: 'center', marginVertical: YanjiSpacing.sm }}>
            {timerMode === 'COUNTDOWN' ? (
              <>
                <Text style={[paper.typography.captionMeta, { color: paper.colors.secondary }]}>
                  {formatEstimatedEndTime(durationMinutes)}
                </Text>

                <Text
                  testID="focus-duration-value"
                  adjustsFontSizeToFit
                  minimumFontScale={0.6}
                  numberOfLines={1}
                  style={[
                    paper.typography.displayDial,
                    { color: paper.colors.ink, marginVertical: YanjiSpacing.xs, textAlign: 'center' },
                  ]}
                >
                  {formatDialMinutes(durationMinutes)}
                </Text>

                {/* Full-bleed strip: the ruler belongs to the page, not to a card. */}
                <View
                  style={{
                    alignSelf: 'stretch',
                    marginHorizontal: -paper.layout.pageGutter,
                    marginTop: YanjiSpacing.md,
                    paddingVertical: YanjiSpacing.lg,
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

                  <View
                    style={{
                      flexDirection: 'row',
                      justifyContent: 'center',
                      flexWrap: 'wrap',
                      gap: YanjiSpacing.sm,
                      marginTop: YanjiSpacing.md,
                      paddingHorizontal: paper.layout.pageGutter,
                    }}
                  >
                    {DURATION_PRESETS.map(minutes => (
                      <DurationPill
                        key={minutes}
                        label={`${minutes}m`}
                        selected={minutes === durationMinutes}
                        accessibilityLabel={`预设 ${minutes} 分钟`}
                        onPress={() => setDurationMinutes(minutes)}
                      />
                    ))}
                    <DurationPill
                      label={isCustomDuration ? `${durationMinutes}m` : '自定义'}
                      selected={isCustomDuration}
                      accessibilityLabel="自定义专注时长"
                      onPress={() => {
                        setCustomDurationText(String(durationMinutes));
                        setCustomDurationError(null);
                        setCustomDurationOpen(true);
                      }}
                    />
                  </View>
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

        <Modal
          visible={customDurationOpen}
          transparent
          animationType="fade"
          onRequestClose={() => setCustomDurationOpen(false)}
        >
          <View
            style={{
              flex: 1,
              justifyContent: 'flex-end',
              backgroundColor: paper.colors.scrim,
              paddingBottom: keyboardInset,
            }}
          >
            <Pressable style={{ flex: 1 }} onPress={() => setCustomDurationOpen(false)} accessibilityLabel="关闭" />
            <View
              style={[
                {
                  backgroundColor: paper.colors.surface,
                  borderTopLeftRadius: paper.radius.sheet,
                  borderTopRightRadius: paper.radius.sheet,
                  paddingHorizontal: paper.layout.pageGutter,
                  paddingTop: YanjiSpacing.lg,
                  paddingBottom: YanjiSpacing.lg + safe.bottom,
                },
                theme.shadow.floating(theme.isDark),
              ]}
            >
              <YanjiPaperSheetHeading
                title="自定义专注时长"
                subtitle={`可输入 ${MIN_MINUTES}–${MAX_MINUTES} 分钟之间的整数`}
                onClose={() => setCustomDurationOpen(false)}
              />
              <View style={{ marginTop: YanjiSpacing.md }}>
                <YanjiTextInput
                  value={customDurationText}
                  onChangeText={next => {
                    setCustomDurationText(next);
                    setCustomDurationError(null);
                  }}
                  placeholder="例如：50"
                  accessibilityLabel="输入自定义分钟数"
                />
              </View>
              {customDurationError ? (
                <Text
                  accessibilityRole="alert"
                  style={[paper.typography.captionMeta, { color: paper.colors.danger, marginTop: YanjiSpacing.sm }]}
                >
                  {customDurationError}
                </Text>
              ) : null}
              <YanjiHairline style={{ marginTop: YanjiSpacing.lg }} />
              <View style={{ marginTop: YanjiSpacing.lg }}>
                <YanjiPrimaryButton icon="check" label="确认时长" onPress={handleConfirmCustomDuration} />
              </View>
            </View>
          </View>
        </Modal>

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
          whole field with the session's accent. */}
      <View pointerEvents="none" style={{ position: 'absolute', left: 0, right: 0, bottom: 0, height: '32%' }}>
        <Svg width="100%" height="100%">
          <Defs>
            <LinearGradient id="focus-horizon-tide" x1="0" y1="0" x2="0" y2="1">
              <Stop offset="0%" stopColor={immersed.tideStart} />
              <Stop offset="100%" stopColor={immersed.tideEnd} />
            </LinearGradient>
          </Defs>
          <Rect x="0" y="0" width="100%" height="100%" fill="url(#focus-horizon-tide)" />
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
            <View style={{ width: 7, height: 7, borderRadius: paper.radius.pill, backgroundColor: immersed.accent }} />
            <Text numberOfLines={1} style={[paper.typography.captionMeta, { color: immersed.secondary }]}>
              {session.subjectName} · {session.isCountdown ? '倒计时' : '正计时'}
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
                  onPress={() => setRecordOpen(true)}
                />
                <ImmersiveAction
                  label={busy ? '处理中…' : '继续专注'}
                  icon="play"
                  variant="primary"
                  disabled={busy}
                  onPress={() => { void handleTimerAction('resume'); }}
                />
              </View>
              <View style={{ flexDirection: 'row', justifyContent: 'center', gap: 16 }}>
                <ImmersiveAction
                  label="提前结束"
                  icon="check"
                  variant="ghost"
                  disabled={busy}
                  onPress={() => { void handleTimerAction('complete'); }}
                />
                <ImmersiveAction
                  label="放弃本轮"
                  icon="delete"
                  variant="ghost"
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

          <Text style={[paper.typography.captionMeta, { color: immersed.footnote, textAlign: 'center' }]}>
            锁屏或切到其他应用后，本轮计时仍会继续
          </Text>
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
  const { main, sub } = splitClockParts(seconds);
  const percent = progress === null ? null : Math.round(progress * 100);

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

      {progress === null ? (
        <View
          style={{
            width: 80,
            height: 3,
            marginTop: 28,
            marginBottom: 10,
            borderRadius: theme.paper.radius.pill,
            backgroundColor: immersed.accent,
            opacity: 0.85,
          }}
        />
      ) : (
        <View
          accessibilityRole="progressbar"
          accessibilityLabel="倒计时进度"
          accessibilityValue={{ min: 0, max: 100, now: percent ?? 0 }}
          style={{
            width: 220,
            height: 3,
            marginTop: 28,
            marginBottom: 10,
            backgroundColor: immersed.subtle,
            overflow: 'hidden',
            borderRadius: theme.paper.radius.pill,
          }}
        >
          <View
            style={{
              height: 3,
              width: `${progress * 100}%`,
              backgroundColor: immersed.accent,
              borderRadius: theme.paper.radius.pill,
            }}
          />
        </View>
      )}

      <Text
        style={[
          theme.paper.typography.captionMeta,
          { color: immersed.secondary, textAlign: 'center' },
        ]}
      >
        {paused
          ? `已专注 ${formatClock(elapsed)} · 已暂停`
          : percent === null
            ? `已专注 ${formatClock(elapsed)} · 正向计时中`
            : `已专注 ${formatClock(elapsed)} · 进度 ${percent}%`}
      </Text>
    </View>
  );
}

/** The immersive field's one action shape, in three weights. */
function ImmersiveAction({
  label,
  icon,
  onPress,
  variant,
  disabled = false,
  style,
}: {
  label: string;
  icon: 'compose' | 'play' | 'pause' | 'check' | 'delete';
  onPress: () => void;
  variant: 'primary' | 'secondary' | 'ghost';
  disabled?: boolean;
  style?: ViewStyle;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const immersed = theme.paper.immersive.running;
  const isPrimary = variant === 'primary';
  const isGhost = variant === 'ghost';

  const background = isGhost
    ? 'transparent'
    : isPrimary
      ? immersed.primaryActionBg
      : immersed.secondaryActionBg;
  const border = isGhost
    ? immersed.secondaryActionBorder
    : isPrimary
      ? immersed.primaryActionBorder
      : immersed.secondaryActionBorder;
  const content = isGhost
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
          minHeight: isGhost ? YanjiTouch.min : 52,
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
      <YanjiIcon name={icon} size={isGhost ? 15 : 16} color={content} />
      <Text
        style={
          isGhost
            ? [theme.paper.typography.captionMeta, { color: content }]
            : [theme.paper.typography.bodyPrimary, { color: content, fontWeight: isPrimary ? '700' : '500' }]
        }
      >
        {label}
      </Text>
    </Pressable>
  );
}

/** A duration preset inside the vernier strip; the selected one inverts to ink. */
function DurationPill({
  label,
  selected,
  accessibilityLabel,
  onPress,
}: {
  label: string;
  selected: boolean;
  accessibilityLabel: string;
  onPress: () => void;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      accessibilityState={{ selected }}
      onPress={onPress}
      style={({ pressed }) => ({
        minWidth: 56,
        minHeight: YanjiTouch.min,
        paddingHorizontal: YanjiSpacing.md,
        borderRadius: paper.radius.pill,
        backgroundColor: selected ? paper.colors.ink : paper.colors.surface,
        borderWidth: paper.layout.hairlineWidth,
        borderColor: selected ? paper.colors.ink : paper.colors.hairline,
        alignItems: 'center',
        justifyContent: 'center',
        opacity: pressed ? 0.72 : 1,
      })}
    >
      <Text
        style={[
          paper.typography.captionMeta,
          { color: selected ? paper.colors.canvas : paper.colors.secondary, fontWeight: selected ? '700' : '500' },
        ]}
      >
        {label}
      </Text>
    </Pressable>
  );
}
