/**
 * Pick a task for the focus session that is about to start.
 *
 * The ready screen used to accept a task only when the session was launched
 * from Today's plan row, so a user who opened Focus first had no way to bind
 * one. This sheet is that missing step: it lists today's unfinished tasks and
 * hands the chosen task back to the page, which owns the subject/duration
 * prefill and the native start.
 */
import React from 'react';
import { Modal, Pressable, ScrollView, Text, View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useReducedMotion } from 'react-native-reanimated';
import type { StudyTask } from '../bridge';
import { YanjiIcon, YanjiPaperSheetHeading } from './YanjiUI';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing } from '../theme/tokens';

export function TaskPickerSheet({
  visible,
  tasks,
  selectedTaskId,
  canBind,
  onSelect,
  onClose,
}: {
  visible: boolean;
  tasks: readonly StudyTask[];
  selectedTaskId: string | null;
  /** A task whose subject is disabled cannot start a session against it. */
  canBind: (task: StudyTask) => boolean;
  onSelect: (task: StudyTask | null) => void;
  onClose: () => void;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout, radius } = paper;
  const insets = useSafeAreaInsets();
  const reduceMotion = useReducedMotion();

  const row = (task: StudyTask) => {
    const disabled = !canBind(task);
    const selected = task.id === selectedTaskId;
    return (
      <Pressable
        key={task.id}
        disabled={disabled}
        onPress={() => onSelect(task)}
        accessibilityRole="button"
        accessibilityState={{ selected, disabled }}
        accessibilityLabel={`${task.title}，${task.subjectName}，计划 ${task.plannedMinutes} 分钟${disabled ? '，学科不可用' : ''}`}
        style={({ pressed }) => ({
          minHeight: layout.rowMinHeight,
          flexDirection: 'row',
          alignItems: 'center',
          gap: YanjiSpacing.sm,
          paddingVertical: YanjiSpacing.sm,
          borderBottomWidth: layout.hairlineWidth,
          borderBottomColor: colors.hairline,
          opacity: disabled ? 0.45 : pressed ? 0.65 : 1,
        })}
      >
        <View style={{ flex: 1, minWidth: 0 }}>
          <Text numberOfLines={1} style={[typography.bodyTask, { color: colors.ink, fontWeight: selected ? '700' : '500' }]}>
            {task.title}
          </Text>
          <Text numberOfLines={1} style={[typography.captionMeta, { color: colors.secondary, marginTop: 2 }]}>
            {task.subjectName} · 计划 {task.plannedMinutes} 分钟{disabled ? ' · 学科不可用' : ''}
          </Text>
        </View>
        {selected ? <YanjiIcon name="check" size={18} color={colors.brand} /> : null}
      </Pressable>
    );
  };

  return (
    <Modal visible={visible} transparent animationType={reduceMotion ? 'none' : 'slide'} onRequestClose={onClose}>
      <View style={{ flex: 1, backgroundColor: colors.scrim, justifyContent: 'flex-end' }}>
        <Pressable style={{ flex: 1 }} onPress={onClose} accessibilityLabel="关闭" />
        <View
          style={{
            backgroundColor: colors.surface,
            borderTopLeftRadius: radius.sheet,
            borderTopRightRadius: radius.sheet,
            paddingHorizontal: layout.pageGutter,
            paddingTop: YanjiSpacing.md,
            paddingBottom: YanjiSpacing.lg + insets.bottom,
          }}
        >
          <YanjiPaperSheetHeading
            title="关联今日计划"
            subtitle="学科与时长会按任务计划带入，可再调整"
            onClose={onClose}
          />
          <ScrollView style={{ maxHeight: 420 }} showsVerticalScrollIndicator={false}>
            {tasks.length === 0 ? (
              <Text style={[typography.bodyPrimary, { color: colors.secondary, marginTop: YanjiSpacing.md }]}>
                今天没有待完成的任务。
              </Text>
            ) : tasks.map(row)}
            {selectedTaskId ? (
              <Pressable
                onPress={() => onSelect(null)}
                accessibilityRole="button"
                accessibilityLabel="不关联任务"
                style={({ pressed }) => ({
                  minHeight: layout.rowMinHeight,
                  justifyContent: 'center',
                  opacity: pressed ? 0.65 : 1,
                })}
              >
                <Text style={[typography.bodyPrimary, { color: colors.secondary }]}>不关联任务</Text>
              </Pressable>
            ) : null}
          </ScrollView>
        </View>
      </View>
    </Modal>
  );
}
