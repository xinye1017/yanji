/**
 * The two bottom sheets the settings page opens: exam date and daily goal.
 *
 * They share one shell (scrim → sheet → grabber → heading → content → save)
 * because they are the same kind of interaction: change one stored value, then
 * save or dismiss. Keeping the shell here means the page itself stays a
 * declarative list of rows.
 *
 * Both sheets only *propose* a value. Persisting it is the caller's job, so the
 * page keeps exactly one writer per setting and one place that reports failure.
 */

import React, { useEffect, useState } from 'react';
import { Modal, Pressable, ScrollView, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useReducedMotion } from 'react-native-reanimated';
import { YanjiHairline, YanjiIconButton, YanjiPaperSheetHeading, YanjiPrimaryButton } from './YanjiUI';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing } from '../theme/tokens';
import {
  WEEKDAY_COLUMNS,
  buildMonthGrid,
  formatExamDate,
  formatGoalDuration,
  formatIsoDate,
  formatMonthTitle,
  monthOf,
  shiftMonth,
  todayIso,
  weekdayLabelOf,
} from '../state/settingsViewLogic';
import type { CalendarMonth } from '../state/settingsViewLogic';

/** Sheet shell: scrim, bottom-anchored page, grabber and heading. */
function SettingsSheet({
  visible,
  title,
  subtitle,
  pending = false,
  onClose,
  children,
}: {
  visible: boolean;
  title: string;
  subtitle: string;
  pending?: boolean;
  onClose: () => void;
  children: React.ReactNode;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const insets = useSafeAreaInsets();
  const reduceMotion = useReducedMotion();

  return (
    <Modal visible={visible} transparent animationType={reduceMotion ? 'none' : 'slide'} onRequestClose={onClose}>
      <View style={{ flex: 1, backgroundColor: paper.colors.scrim, justifyContent: 'flex-end' }}>
        <Pressable
          style={{ flex: 1 }}
          onPress={onClose}
          disabled={pending}
          accessibilityState={{ disabled: pending }}
          accessibilityLabel="关闭"
        />
        <ScrollView
          showsVerticalScrollIndicator={false}
          overScrollMode="never"
          keyboardShouldPersistTaps="handled"
          style={{
            flexGrow: 0,
            maxHeight: '88%',
            backgroundColor: paper.colors.surface,
            borderTopLeftRadius: paper.radius.sheet,
            borderTopRightRadius: paper.radius.sheet,
          }}
          contentContainerStyle={{
            paddingHorizontal: paper.layout.pageGutter,
            paddingTop: YanjiSpacing.md,
            paddingBottom: YanjiSpacing.lg + insets.bottom,
          }}
        >
          <View
            style={{
              alignSelf: 'center',
              width: 36,
              height: 4,
              borderRadius: paper.radius.pill,
              backgroundColor: paper.colors.hairline,
              marginBottom: YanjiSpacing.lg,
            }}
          />
          <YanjiPaperSheetHeading title={title} subtitle={subtitle} onClose={onClose} disabled={pending} />
          {children}
        </ScrollView>
      </View>
    </Modal>
  );
}

/** The month calendar used by the exam-date sheet. */
export function ExamDateSheet({
  visible,
  initialDate,
  saving,
  error,
  onClose,
  onSave,
}: {
  visible: boolean;
  /** Stored date, or an empty string when none is set yet. */
  initialDate: string;
  saving: boolean;
  error: string | null;
  onClose: () => void;
  onSave: (iso: string) => void;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout, radius } = paper;
  const today = todayIso();
  const [selected, setSelected] = useState(initialDate);
  const [month, setMonth] = useState<CalendarMonth>(() => monthOf(initialDate || today));

  // Reopen on whatever is actually stored, not on the last thing that was tapped.
  // `today` is a dependency even though it looks constant: a sheet left open
  // across midnight must re-anchor to the new day rather than keep claiming the
  // old one is "today".
  useEffect(() => {
    if (!visible) return;
    setSelected(initialDate);
    setMonth(monthOf(initialDate || today));
  }, [visible, initialDate, today]);

  const weeks = buildMonthGrid(month);
  const selectedWeekday = weekdayLabelOf(selected);

  return (
    <SettingsSheet
      visible={visible}
      title="考试日期"
      subtitle="初试当天按这个日期计算倒计时"
      pending={saving}
      onClose={onClose}
    >
      <View style={{ marginTop: YanjiSpacing.lg }}>
        <View
          style={{
            flexDirection: 'row',
            alignItems: 'center',
            justifyContent: 'space-between',
            minHeight: layout.rowMinHeight,
          }}
        >
          <YanjiIconButton
            icon="back"
            accessibilityLabel="上一个月"
            onPress={() => setMonth(shiftMonth(month, -1))}
            disabled={saving}
          />
          <Text
            accessibilityRole="header"
            style={[typography.bodyPrimary, { color: colors.ink, fontWeight: '700', fontVariant: ['tabular-nums'] }]}
          >
            {formatMonthTitle(month)}
          </Text>
          <YanjiIconButton
            icon="forward"
            accessibilityLabel="下一个月"
            onPress={() => setMonth(shiftMonth(month, 1))}
            disabled={saving}
          />
        </View>

        <View style={{ flexDirection: 'row', marginTop: YanjiSpacing.sm }}>
          {WEEKDAY_COLUMNS.map(column => (
            <View key={column} style={{ flex: 1, alignItems: 'center' }}>
              <Text style={[typography.captionMeta, { color: colors.secondary }]}>{column}</Text>
            </View>
          ))}
        </View>

        {weeks.map((week, weekIndex) => (
          <View key={weekIndex} style={{ flexDirection: 'row', marginTop: YanjiSpacing.xs }}>
            {week.map((day, dayIndex) => {
              if (day === null) {
                return <View key={`blank-${dayIndex}`} style={{ flex: 1, minHeight: layout.rowMinHeight }} />;
              }
              const iso = formatIsoDate({ ...month, day });
              const isSelected = iso === selected;
              const isToday = iso === today;
              return (
                <Pressable
                  key={iso}
                  onPress={() => setSelected(iso)}
                  disabled={saving}
                  accessibilityRole="radio"
                  accessibilityState={{ selected: isSelected, disabled: saving }}
                  accessibilityLabel={`${formatExamDate(iso)}${isToday ? '，今天' : ''}`}
                  style={({ pressed }) => ({
                    // The cell owns its column instead of shrinking inside a
                    // wrapper: a fixed 48dp minimum inside a flex:1 column
                    // overflows the grid on a 360dp screen (7 × 48 > 316dp
                    // of content width) and makes neighbouring touch targets
                    // overlap. `flex: 1` keeps the seven days evenly divided.
                    flex: 1,
                    minHeight: layout.rowMinHeight,
                    alignItems: 'center',
                    justifyContent: 'center',
                    borderRadius: radius.surface,
                    // Selected day is a filled ink chip; today is a hairline
                    // ring. Two different signals, so "today" survives a
                    // selection elsewhere in the month.
                    backgroundColor: isSelected ? colors.ink : 'transparent',
                    borderWidth: isToday && !isSelected ? layout.hairlineWidth : 0,
                    borderColor: colors.hairline,
                    opacity: pressed ? 0.6 : 1,
                  })}
                >
                  <Text
                    style={[
                      typography.bodyPrimary,
                      {
                        color: isSelected ? colors.canvas : colors.ink,
                        fontWeight: isSelected || isToday ? '700' : '500',
                        fontVariant: ['tabular-nums'],
                      },
                    ]}
                  >
                    {day}
                  </Text>
                </Pressable>
              );
            })}
          </View>
        ))}
      </View>

      <YanjiHairline style={{ marginTop: YanjiSpacing.md }} />

      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: YanjiSpacing.md,
          paddingVertical: YanjiSpacing.md,
        }}
      >
        <Text style={[typography.captionMeta, { color: colors.secondary }]}>已选日期</Text>
        <Text
          accessibilityLiveRegion="polite"
          style={[typography.bodyPrimary, { color: colors.ink, fontWeight: '700', fontVariant: ['tabular-nums'] }]}
        >
          {formatExamDate(selected)}{selectedWeekday ? ` · ${selectedWeekday}` : ''}
        </Text>
      </View>

      {error ? (
        <Text accessibilityRole="alert" style={[typography.captionMeta, { color: colors.danger, marginBottom: YanjiSpacing.sm }]}>
          {error}
        </Text>
      ) : null}

      <YanjiPrimaryButton
        label={saving ? '保存中…' : '保存考试日期'}
        onPress={() => onSave(selected)}
        disabled={saving || selected.length === 0}
      />
    </SettingsSheet>
  );
}

/** Shortcut targets, in minutes. Module scope so they are not reallocated per render. */
const GOAL_PRESETS = [240, 360, 480, 600] as const;
/** The stepper's ceiling: twelve hours. */
const GOAL_MAX_MINUTES = 720;

/**
 * Daily study goal sheet.
 *
 * The stepper owns the value and the four presets are shortcuts into it, so
 * there is one source of truth for "what will be saved" — a row of independent
 * preset buttons plus a separate stepper would let the two disagree.
 *
 * Zero is a real value here: it means "no goal", which the page and Today both
 * read as 未设置. It is reachable through the stepper on purpose, so the save
 * button says 「清除目标」 rather than 「保存目标」 when the draft is zero — the
 * user must be able to tell the difference between setting a goal and removing it.
 */
export function DailyGoalSheet({
  visible,
  initialHours,
  saving,
  error,
  onClose,
  onSave,
}: {
  visible: boolean;
  /** Stored goal in hours; 0 / undefined means "not set". */
  initialHours: number | undefined;
  saving: boolean;
  error: string | null;
  onClose: () => void;
  onSave: (minutes: number) => void;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout, radius } = paper;
  const [minutes, setMinutes] = useState(() => Math.max(0, Math.round((initialHours ?? 0) * 60)));

  useEffect(() => {
    if (!visible) return;
    setMinutes(Math.max(0, Math.round((initialHours ?? 0) * 60)));
  }, [visible, initialHours]);

  const adjust = (delta: number) => setMinutes(current => Math.max(0, Math.min(GOAL_MAX_MINUTES, current + delta)));

  return (
    <SettingsSheet
      visible={visible}
      title="每日学习时长"
      subtitle="只影响进度与达标，不改变已记录时长"
      pending={saving}
      onClose={onClose}
    >
      <Text
        accessibilityLiveRegion="polite"
        style={[typography.metricHero, { color: colors.ink, marginVertical: YanjiSpacing.lg, textAlign: 'center' }]}
      >
        {minutes > 0 ? formatGoalDuration(minutes / 60) : '未设置'}
      </Text>

      <View style={{ flexDirection: 'row', justifyContent: 'center', alignItems: 'center', gap: layout.gridGap }}>
        <YanjiIconButton
          icon="remove"
          onPress={() => adjust(-30)}
          disabled={saving || minutes <= 0}
          accessibilityLabel="减少 30 分钟"
          size={layout.rowMinHeight}
          tone="filled"
        />
        <Text style={[typography.bodyPrimary, { color: colors.secondary, flexShrink: 1 }]}>每次调整 30 分钟</Text>
        <YanjiIconButton
          icon="add"
          onPress={() => adjust(30)}
          disabled={saving || minutes >= 720}
          accessibilityLabel="增加 30 分钟"
          size={layout.rowMinHeight}
          tone="filled"
        />
      </View>

      <View
        style={{
          flexDirection: 'row',
          flexWrap: 'wrap',
          justifyContent: 'center',
          gap: layout.gridGap,
          marginVertical: YanjiSpacing.lg,
        }}
      >
        {GOAL_PRESETS.map(preset => {
          const isSelected = preset === minutes;
          return (
            <Pressable
              key={preset}
              onPress={() => setMinutes(preset)}
              disabled={saving}
              accessibilityRole="button"
              accessibilityState={{ selected: isSelected }}
              accessibilityLabel={`设为 ${formatGoalDuration(preset / 60)}`}
              style={({ pressed }) => ({
                minHeight: layout.rowMinHeight,
                paddingHorizontal: YanjiSpacing.md,
                justifyContent: 'center',
                borderRadius: radius.pill,
                backgroundColor: isSelected ? colors.brandSubtle : colors.subtle,
                borderWidth: layout.hairlineWidth,
                borderColor: isSelected ? colors.brand : colors.hairline,
                opacity: pressed ? 0.65 : 1,
              })}
            >
              <Text
                style={[
                  typography.captionMeta,
                  { color: isSelected ? colors.brandInk : colors.secondary, fontWeight: isSelected ? '700' : '500' },
                ]}
              >
                {formatGoalDuration(preset / 60)}
              </Text>
            </Pressable>
          );
        })}
      </View>

      {error ? (
        <Text accessibilityRole="alert" style={[typography.captionMeta, { color: colors.danger, marginBottom: YanjiSpacing.sm }]}>
          {error}
        </Text>
      ) : null}

      <YanjiPrimaryButton
        label={saving ? '保存中…' : minutes > 0 ? '保存目标' : '清除目标'}
        onPress={() => onSave(minutes)}
        disabled={saving}
      />
    </SettingsSheet>
  );
}
