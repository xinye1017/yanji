/**
 * 「补录专注」— record a focus session that happened but was never timed.
 *
 * What this sheet is NOT: a note capture. 「补录记录」 used to open
 * RecordMomentModal, so the only thing a forgotten session could become was a
 * sentence — the minutes never reached `focus_sessions`, and Today / Review
 * never counted them. This sheet writes a real session.
 *
 * The fields mirror what a person actually remembers: which day, which subject,
 * when it started, how long it ran, and optionally which plan it was for. The
 * end time is derived (start + duration) rather than asked for, because nobody
 * remembers "16:30 to 17:20" — they remember "half four, fifty minutes".
 *
 * Native rejections are surfaced in the sheet's own voice; nothing is silently
 * clamped, so an impossible record cannot appear as a successful save.
 */
import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { Modal, Pressable, ScrollView, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useReducedMotion } from 'react-native-reanimated';
import { YanjiDataNative } from '../bridge';
import type { StudyTask, Subject } from '../bridge';
import {
  YanjiPaperSheetHeading,
  YanjiPrimaryButton,
  YanjiStepper,
  useYanjiKeyboardInset,
} from './YanjiUI';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing } from '../theme/tokens';
import { todayIso } from '../state/settingsViewLogic';

/** Minutes of study a record may hold; mirrors the native validation exactly. */
const MIN_MINUTES = 1;
const MAX_MINUTES = 24 * 60;

/** A day offset back from today, as yyyy-MM-dd. */
function isoDaysAgo(days: number): string {
  const day = new Date();
  day.setDate(day.getDate() - days);
  return [
    day.getFullYear(),
    String(day.getMonth() + 1).padStart(2, '0'),
    String(day.getDate()).padStart(2, '0'),
  ].join('-');
}

/** 2026-10-21 -> 10月21日 周二. */
function dayLabel(iso: string): string {
  const day = new Date(iso + 'T12:00:00');
  return (day.getMonth() + 1) + '月' + day.getDate() + '日 周' + '日一二三四五六'[day.getDay()];
}

/** Two-digit clock text for a given hour/minute. */
function clockText(hour: number, minute: number): string {
  return String(hour).padStart(2, '0') + ':' + String(minute).padStart(2, '0');
}

/**
 * A default start time that is always in the past, rounded down to a quarter
 * hour: "the last session I forgot to time" is much more often the one that
 * just ended than a fixed hour of the day.
 */
function defaultStart(now: Date = new Date()): { hour: number; minute: number } {
  const minute = Math.floor(now.getMinutes() / 15) * 15;
  return { hour: now.getHours(), minute };
}

const DEFAULT_START = defaultStart();
const defaultStartHour = DEFAULT_START.hour;
const defaultStartMinute = DEFAULT_START.minute;

interface BackfillFocusModalProps {
  visible: boolean;
  onClose: () => void;
  onSaved?: () => void;
  /** Preselected subject when the tab that opened the sheet has one. */
  initialSubjectId?: string | null;
}

export function BackfillFocusModal({
  visible,
  onClose,
  onSaved,
  initialSubjectId = null,
}: BackfillFocusModalProps): React.JSX.Element {
  const theme = useYanjiTheme();
  const paper = theme.paper;
  const reduceMotion = useReducedMotion();
  const insets = useSafeAreaInsets();
  const keyboardInset = useYanjiKeyboardInset();

  const [subjects, setSubjects] = useState<Subject[]>([]);
  const [tasks, setTasks] = useState<StudyTask[]>([]);
  const [day, setDay] = useState<string>(todayIso);
  const [subjectId, setSubjectId] = useState<string | null>(initialSubjectId);
  // Default to a session that has plausibly just ended, never a future clock
  // time: a hard-coded 16:30 made the sheet refuse its own default save during
  // any morning session, and the user had to correct a time they never chose.
  // The clock is read once per open (see the effect below) so a sheet left open
  // across a boundary cannot drift into the future either.
  const [hour, setHour] = useState(defaultStartHour);
  const [minute, setMinute] = useState(defaultStartMinute);
  const [duration, setDuration] = useState(50);
  const [taskId, setTaskId] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Subjects and the day's plans are read when the sheet opens, not on mount, so
  // a plan added elsewhere shows up without remounting the screen.
  useEffect(() => {
    if (!visible) return;
    setError(null);
    setSaving(false);
    setTaskId(null);
    setDay(todayIso());
    // Re-read the clock per open so the default start stays in the past even
    // when the sheet is opened again hours later in the same process.
    const start = defaultStart();
    setHour(start.hour);
    setMinute(start.minute);
    YanjiDataNative.getSubjects()
      .then(list => {
        const enabled = list.filter(subject => subject.enabled);
        setSubjects(enabled);
        setSubjectId(current => {
          if (current && enabled.some(subject => subject.id === current)) return current;
          const preferred = initialSubjectId && enabled.some(s => s.id === initialSubjectId)
            ? initialSubjectId
            : null;
          return preferred ?? enabled[0]?.id ?? null;
        });
      })
      .catch(() => setSubjects([]));
    YanjiDataNative.getDailySnapshot(todayIso())
      .then(snapshot => setTasks(snapshot.tasks ?? []))
      .catch(() => setTasks([]));
  }, [visible, initialSubjectId]);

  /** Plans the record can be attached to: today's, incomplete first. */
  const planOptions = useMemo(
    () => [...tasks].sort((a, b) => Number(a.completed) - Number(b.completed)),
    [tasks],
  );

  const chosenSubject = subjects.find(subject => subject.id === subjectId) ?? null;

  const save = useCallback(async () => {
    if (saving) return;
    if (!chosenSubject) {
      setError('先选一科学科');
      return;
    }
    const start = new Date(day + 'T00:00:00');
    start.setHours(hour, minute, 0, 0);
    const startTime = start.getTime();
    if (startTime > Date.now()) {
      setError('开始时间不能晚于现在');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await YanjiDataNative.createFocusRecord({
        subjectId: chosenSubject.id,
        subjectName: chosenSubject.name,
        startTime,
        durationMinutes: duration,
        taskId,
        note: null,
      });
      onSaved?.();
      onClose();
    } catch (cause) {
      // The bridge rejects with a code; the sheet answers in the user's terms.
      const code = (cause as { code?: string } | null)?.code ?? '';
      setError(
        code === 'E_FUTURE_SESSION' ? '开始时间不能晚于现在'
          : code === 'E_INVALID_DURATION' ? '时长需要在 1 分钟到 24 小时之间'
            : code === 'E_UNKNOWN_SUBJECT' ? '这科学科已经不在了，换一个'
              : code === 'E_UNKNOWN_TASK' ? '这条计划已经不在了，取消关联再试'
                : '补录没有保存，请重试',
      );
    } finally {
      setSaving(false);
    }
  }, [saving, chosenSubject, day, hour, minute, duration, taskId, onSaved, onClose]);

  const dayChips = useMemo(
    () => Array.from(new Set([isoDaysAgo(0), isoDaysAgo(1), isoDaysAgo(2), isoDaysAgo(3), day])),
    [day],
  );

  const endLabel = useMemo(() => {
    const end = new Date(day + 'T00:00:00');
    end.setHours(hour, minute, 0, 0);
    end.setMinutes(end.getMinutes() + duration);
    return clockText(end.getHours(), end.getMinutes());
  }, [day, hour, minute, duration]);

  const chipStyle = (selected: boolean) => ({
    paddingHorizontal: 12,
    paddingVertical: 7,
    borderRadius: paper.radius.pill,
    backgroundColor: selected ? paper.colors.ink : paper.colors.subtle,
  });
  const chipText = (selected: boolean) => [
    paper.typography.captionMeta,
    {
      color: selected ? paper.colors.canvas : paper.colors.secondary,
      fontWeight: selected ? ('700' as const) : ('500' as const),
    },
  ];

  return (
    <Modal visible={visible} transparent animationType={reduceMotion ? 'fade' : 'slide'} onRequestClose={onClose}>
      <View style={{ flex: 1, justifyContent: 'flex-end', backgroundColor: paper.colors.scrim, paddingBottom: keyboardInset }}>
        <Pressable style={{ flex: 1 }} onPress={onClose} accessibilityLabel="关闭补录" />
        <View
          style={{
            backgroundColor: paper.colors.surface,
            borderTopLeftRadius: paper.radius.sheet,
            borderTopRightRadius: paper.radius.sheet,
            paddingHorizontal: paper.layout.pageGutter,
            paddingTop: YanjiSpacing.md,
            paddingBottom: YanjiSpacing.lg + insets.bottom,
            maxHeight: '88%',
          }}
        >
          <View style={{ alignSelf: 'center', width: 36, height: 4, borderRadius: paper.radius.pill, backgroundColor: paper.colors.hairline, marginBottom: YanjiSpacing.lg }} />

          <YanjiPaperSheetHeading title="补录专注" subtitle="记录一段已经发生的专注" onClose={onClose} />

          <ScrollView showsVerticalScrollIndicator={false} keyboardShouldPersistTaps="handled">
<Text style={[paper.typography.captionMeta, { color: paper.colors.secondary }]}>哪天</Text>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: YanjiSpacing.sm, marginTop: YanjiSpacing.xs }}>
              {dayChips.map(iso => {
                const selected = iso === day;
                return (
                  <Pressable
                    key={iso}
                    onPress={() => setDay(iso)}
                    accessibilityRole="button"
                    accessibilityState={{ selected }}
                    accessibilityLabel={dayLabel(iso)}
                    style={({ pressed }) => [chipStyle(selected), { opacity: pressed ? 0.7 : 1 }]}
                  >
                    <Text style={chipText(selected)}>{dayLabel(iso)}</Text>
                  </Pressable>
                );
              })}
            </View>

            <Text style={[paper.typography.captionMeta, { color: paper.colors.secondary, marginTop: YanjiSpacing.lg }]}>学科</Text>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: YanjiSpacing.sm, marginTop: YanjiSpacing.xs }}>
              {subjects.map(subject => {
                const selected = subject.id === subjectId;
                return (
                  <Pressable
                    key={subject.id}
                    onPress={() => setSubjectId(subject.id)}
                    accessibilityRole="button"
                    accessibilityState={{ selected }}
                    accessibilityLabel={subject.name}
                    style={({ pressed }) => [chipStyle(selected), { opacity: pressed ? 0.7 : 1 }]}
                  >
                    <Text style={chipText(selected)}>{subject.name}</Text>
                  </Pressable>
                );
              })}
              {subjects.length === 0 ? (
                <Text style={[paper.typography.captionMeta, { color: paper.colors.secondary }]}>没有可用学科</Text>
              ) : null}
            </View>

            <Text style={[paper.typography.captionMeta, { color: paper.colors.secondary, marginTop: YanjiSpacing.lg }]}>开始时间</Text>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.md, marginTop: YanjiSpacing.xs }}>
              <YanjiStepper value={hour} onChange={setHour} min={0} max={23} suffix=" 时" label="开始小时" />
              <YanjiStepper value={minute} onChange={setMinute} min={0} max={55} step={5} suffix=" 分" label="开始分钟" />
            </View>

            <Text style={[paper.typography.captionMeta, { color: paper.colors.secondary, marginTop: YanjiSpacing.lg }]}>时长</Text>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.md, marginTop: YanjiSpacing.xs }}>
              <YanjiStepper value={duration} onChange={setDuration} min={MIN_MINUTES} max={MAX_MINUTES} step={5} suffix=" 分钟" label="专注时长" />
            </View>
            <Text style={[paper.typography.captionMeta, { color: paper.colors.secondary, marginTop: YanjiSpacing.xs }]}>
              {(dayLabel(day) + ' ' + clockText(hour, minute) + ' 到 ' + endLabel)}
            </Text>

            {planOptions.length > 0 ? (
              <>
                <Text style={[paper.typography.captionMeta, { color: paper.colors.secondary, marginTop: YanjiSpacing.lg }]}>关联计划（可选）</Text>
                <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: YanjiSpacing.sm, marginTop: YanjiSpacing.xs }}>
                  <Pressable
                    onPress={() => setTaskId(null)}
                    accessibilityRole="button"
                    accessibilityState={{ selected: taskId === null }}
                    accessibilityLabel="不关联计划"
                    style={({ pressed }) => [chipStyle(taskId === null), { opacity: pressed ? 0.7 : 1 }]}
                  >
                    <Text style={chipText(taskId === null)}>不关联</Text>
                  </Pressable>
                  {planOptions.map(task => {
                    const selected = task.id === taskId;
                    return (
                      <Pressable
                        key={task.id}
                        onPress={() => setTaskId(task.id)}
                        accessibilityRole="button"
                        accessibilityState={{ selected }}
                        accessibilityLabel={task.title}
                        style={({ pressed }) => [chipStyle(selected), { maxWidth: '100%', opacity: pressed ? 0.7 : 1 }]}
                      >
                        <Text numberOfLines={1} style={chipText(selected)}>{task.title}</Text>
                      </Pressable>
                    );
                  })}
                </View>
              </>
            ) : null}

            {error ? (
              <Text style={[paper.typography.captionMeta, { color: paper.colors.danger, marginTop: YanjiSpacing.md }]}>{error}</Text>
            ) : null}

            <View style={{ marginTop: YanjiSpacing.lg }}>
              <YanjiPrimaryButton
                label={saving ? '保存中…' : '保存补录'}
                onPress={() => void save()}
                disabled={saving || !chosenSubject}
              />
            </View>
          </ScrollView>
        </View>
      </View>
    </Modal>
  );
}
