/**
 * 时间表里的一行：左侧时间与时长，中间轴线节点，右侧完成勾选。
 *
 * 当前时段用柔和品牌底色整行强调；已完成时段标题划掉并压到次要层级。
 * 「开始专注」与「勾选完成」是并列的两个触控目标，不互相嵌套——否则点勾选
 * 时外层行的开始动作也会被触发。
 */

import React from 'react';
import { Pressable, Text, View } from 'react-native';
import type { ScheduleBlock } from '../bridge';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing } from '../theme/tokens';
import { YanjiIcon, YanjiLedgerChip } from './YanjiUI';
import {
  blockDurationMinutes,
  compactDurationMinutes,
  formatFocusSeconds,
  formatMinuteOfDay,
} from '../screens/timetableViewLogic';

const NODE_TOP = 8;

export function ScheduleBlockRow({
  block,
  subjectColor,
  isFirst,
  isLast,
  isCurrent,
  completed,
  actualSeconds,
  canStart,
  disabled,
  onToggle,
  onStart,
}: {
  block: ScheduleBlock;
  subjectColor: string;
  isFirst: boolean;
  isLast: boolean;
  isCurrent: boolean;
  completed: boolean;
  actualSeconds: number;
  /** 该时段是否有关联学科；没有学科（如「复盘」）只能标记完成。 */
  canStart: boolean;
  disabled: boolean;
  onToggle: () => void;
  onStart: () => void;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout, radius } = paper;
  const duration = blockDurationMinutes(block);
  const startable = canStart && !disabled;
  const timeLabel = formatMinuteOfDay(block.startMinute);
  const endLabel = formatMinuteOfDay(block.endMinute);
  const nodeRadius = layout.timelineNodeSize / 2;
  const nodeColor = isCurrent ? colors.brand : completed ? colors.ink : colors.muted;
  const meta = actualSeconds > 0
    ? `已专注 ${formatFocusSeconds(actualSeconds)}${completed ? ' · 已完成' : ''}`
    : completed ? '已完成' : null;

  return (
    <View
      style={{
        marginHorizontal: -YanjiSpacing.sm,
        flexDirection: 'row',
        alignItems: 'flex-start',
        paddingVertical: YanjiSpacing.sm + 2,
        paddingHorizontal: YanjiSpacing.sm,
        borderRadius: radius.surface,
        backgroundColor: isCurrent ? colors.brandSubtle : 'transparent',
      }}
    >
      <Pressable
        onPress={startable ? onStart : undefined}
        accessibilityRole={startable ? 'button' : undefined}
        accessibilityLabel={startable
          ? `开始专注：${block.subjectLabel}，${block.title}，${timeLabel} 至 ${endLabel}`
          : undefined}
        accessibilityHint={startable ? '将切换到专注页并预选科目与时长' : undefined}
        accessibilityState={{ disabled: !startable }}
        style={({ pressed }) => ({
          flex: 1,
          flexDirection: 'row',
          alignItems: 'flex-start',
          opacity: pressed && startable ? 0.7 : 1,
        })}
      >
        <View style={{ width: layout.timelineTimeColumn, flexShrink: 0, paddingTop: 1 }}>
          <Text
            style={[
              typography.captionMeta,
              {
                color: isCurrent ? colors.brandInk : colors.ink,
                fontWeight: '700',
              },
            ]}
          >
            {timeLabel}
          </Text>
          <Text style={[typography.captionMeta, { color: colors.secondary, marginTop: 2 }]}>
            {compactDurationMinutes(duration)}
          </Text>
        </View>

        <View
          accessible={false}
          importantForAccessibility="no-hide-descendants"
          style={{
            width: layout.timelineAxisColumn,
            alignSelf: 'stretch',
            alignItems: 'center',
          }}
        >
          {!isFirst ? (
            <View
              style={{
                position: 'absolute',
                top: 0,
                height: Math.max(0, NODE_TOP - nodeRadius),
                width: layout.hairlineWidth,
                backgroundColor: colors.hairline,
              }}
            />
          ) : null}
          <View
            style={{
              position: 'absolute',
              top: NODE_TOP - nodeRadius,
              width: layout.timelineNodeSize,
              height: layout.timelineNodeSize,
              borderRadius: nodeRadius,
              backgroundColor: nodeColor,
            }}
          />
          {!isLast ? (
            <View
              style={{
                position: 'absolute',
                top: NODE_TOP + nodeRadius,
                bottom: 0,
                width: layout.hairlineWidth,
                backgroundColor: colors.hairline,
              }}
            />
          ) : null}
        </View>

        <View style={{ flex: 1, minWidth: 0 }}>
          <View
            style={{
              flexDirection: 'row',
              alignItems: 'center',
              flexWrap: 'wrap',
              gap: YanjiSpacing.xs + 2,
            }}
          >
            <YanjiLedgerChip
              label={block.subjectLabel}
              tone={block.subjectIds.length > 0 ? 'subject' : 'meta'}
              color={subjectColor}
              completed={completed}
            />
            {isCurrent ? (
              <View
                style={{
                  backgroundColor: colors.brandSubtle,
                  borderRadius: radius.pill,
                  paddingHorizontal: 7,
                  paddingVertical: 2,
                }}
              >
                <Text
                  style={[
                    typography.captionMeta,
                    { color: colors.brandInk, fontWeight: '700' },
                  ]}
                >
                  现在
                </Text>
              </View>
            ) : null}
          </View>
          <Text
            numberOfLines={2}
            style={{
              ...typography.bodyTask,
              color: completed ? colors.secondary : colors.ink,
              fontWeight: '600',
              marginTop: 3,
              textDecorationLine: completed ? 'line-through' : 'none',
            }}
          >
            {block.title}
          </Text>
          {meta ? (
            <Text
              style={[
                typography.captionMeta,
                { color: colors.secondary, marginTop: 2 },
              ]}
            >
              {meta}
            </Text>
          ) : null}
        </View>
      </Pressable>

      {startable ? (
        <Pressable
          onPress={onStart}
          accessibilityRole="button"
          accessibilityLabel={`开始专注：${block.title}`}
          hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
          style={({ pressed }) => ({
            alignSelf: 'center',
            width: layout.compactActionHeight,
            height: layout.compactActionHeight,
            borderRadius: layout.compactActionHeight / 2,
            backgroundColor: colors.brandSubtle,
            alignItems: 'center',
            justifyContent: 'center',
            marginLeft: YanjiSpacing.xs,
            marginRight: YanjiSpacing.xs,
            opacity: pressed ? 0.6 : 1,
          })}
        >
          <YanjiIcon name="play" size={11} color={colors.brandInk} />
        </Pressable>
      ) : null}

      <Pressable
        onPress={onToggle}
        disabled={disabled}
        accessibilityRole="checkbox"
        accessibilityState={{ checked: completed, disabled }}
        accessibilityLabel={`${completed ? '取消完成' : '标记完成'}：${block.title}`}
        hitSlop={{ top: 11, bottom: 11, left: 11, right: 11 }}
        style={({ pressed }) => ({
          alignSelf: 'center',
          width: 22,
          height: 22,
          marginLeft: YanjiSpacing.xs,
          opacity: pressed ? 0.6 : 1,
        })}
      >
        <View
          style={{
            width: 22,
            height: 22,
            borderRadius: radius.pill,
            borderWidth: completed ? 0 : 1.5,
            borderColor: colors.muted,
            backgroundColor: completed ? colors.ink : 'transparent',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          {completed ? (
            <YanjiIcon name="check" size={13} color={colors.canvas} />
          ) : null}
        </View>
      </Pressable>
    </View>
  );
}
