import React, { useEffect, useRef, useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import Animated, { useAnimatedStyle, useReducedMotion, useSharedValue, withSequence, withTiming } from 'react-native-reanimated';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing, YanjiTouch } from '../theme/tokens';
import { YanjiIcon } from './YanjiUI';

interface MomentTimelineItemProps {
  timeLabel: string;
  categoryLabel?: string | null;
  content: string;
  isFavorite: boolean;
  isResolved?: boolean;
  isFirst: boolean;
  isLast: boolean;
  disabled: boolean;
  onPressContent: () => void;
  onToggleFavorite: () => void;
  onToggleResolved?: () => void;
  onMore: () => void;
}

/** Presentation only: the screen owns note identity and every business action. */
export function MomentTimelineItem({
  timeLabel, categoryLabel, content, isFavorite, isResolved, isFirst, isLast, disabled,
  onPressContent, onToggleFavorite, onToggleResolved, onMore,
}: MomentTimelineItemProps): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout, radius, motion } = paper;
  const reduceMotion = useReducedMotion();
  const scale = useSharedValue(1);
  const previousFavorite = useRef(isFavorite);
  const [headerHeight, setHeaderHeight] = useState<number>(YanjiTouch.min);
  const starStyle = useAnimatedStyle(() => ({ transform: [{ scale: scale.value }] }));
  useEffect(() => {
    const changed = previousFavorite.current !== isFavorite;
    previousFavorite.current = isFavorite;
    if (reduceMotion || !changed) { scale.value = 1; return; }
    scale.value = isFavorite ? 0.9 : 1.1;
    scale.value = withSequence(
      withTiming(isFavorite ? 1.1 : 0.9, { duration: motion.momentFavoriteMs / 2 }),
      withTiming(1, { duration: motion.momentFavoriteMs / 2 }),
    );
  }, [isFavorite, reduceMotion, motion.momentFavoriteMs, scale]);

  const center = YanjiSpacing.xs + headerHeight / 2;
  const nodeRadius = layout.timelineNodeSize / 2;
  const title = content.trim().split('\n').find(line => line.trim())?.trim() || '无标题记录';
  const categoryColor = onToggleResolved && isResolved ? colors.positive : colors.secondary;
  const categoryText = <Text style={[typography.captionMeta, { color: categoryColor, flexShrink: 1 }]}>{categoryLabel}</Text>;
  // Bring the glyphs inward while keeping two separate, non-overlapping targets.
  const iconButton = (pressed: boolean, trailing: boolean) => ({
    width: YanjiTouch.min, height: YanjiTouch.min, paddingHorizontal: YanjiSpacing.sm,
    borderRadius: radius.pill, alignItems: trailing ? 'flex-start' as const : 'flex-end' as const, justifyContent: 'center' as const,
    backgroundColor: pressed ? colors.subtle : 'transparent',
  });

  return (
    <View style={{ flexDirection: 'row', paddingBottom: isLast ? 0 : layout.momentItemGap }}>
      <View accessible={false} importantForAccessibility="no-hide-descendants"
        style={{ width: layout.timelineAxisColumn, marginRight: layout.momentAxisGap, alignItems: 'center' }}>
        {!isFirst ? <View style={{ position: 'absolute', top: 0, height: center - nodeRadius - YanjiSpacing.xs,
          width: layout.hairlineWidth, backgroundColor: colors.hairline }} /> : null}
        <View style={{ position: 'absolute', top: center - nodeRadius,
          width: layout.timelineNodeSize, height: layout.timelineNodeSize,
          borderRadius: nodeRadius, backgroundColor: colors.brand }} />
        {!isLast ? <View style={{ position: 'absolute', top: center + nodeRadius + YanjiSpacing.xs, bottom: -layout.momentItemGap,
          width: layout.hairlineWidth, backgroundColor: colors.hairline }} /> : null}
      </View>
      <View style={{ flex: 1, minWidth: 0, borderRadius: radius.surface, backgroundColor: colors.elevated,
        paddingHorizontal: YanjiSpacing.lg, paddingTop: YanjiSpacing.xs }}>
        <View onLayout={event => setHeaderHeight(event.nativeEvent.layout.height)}
          style={{ flexDirection: 'row', alignItems: 'center', minHeight: YanjiTouch.min }}>
          <View style={{ flex: 1, minWidth: 0, flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', columnGap: YanjiSpacing.sm }}>
            <Text style={[typography.captionMeta, { color: colors.secondary }]}>{timeLabel}</Text>
            {categoryLabel ? onToggleResolved ? (
              <Pressable onPress={onToggleResolved} disabled={disabled} accessibilityRole="checkbox"
                accessibilityLabel={isResolved ? '疑点已解决，点击标记为待解决' : '疑点待解决，点击标记为已解决'}
                accessibilityState={{ checked: !!isResolved, disabled }}
                style={({ pressed }) => ({ minHeight: YanjiTouch.min, minWidth: YanjiTouch.min, maxWidth: '100%',
                  justifyContent: 'center', opacity: pressed ? 0.65 : 1 })}>
                <View style={{ backgroundColor: isResolved ? colors.positiveSubtle : colors.subtle,
                  borderRadius: radius.badge, paddingHorizontal: YanjiSpacing.sm, paddingVertical: YanjiSpacing.xs }}>{categoryText}</View>
              </Pressable>
            ) : (
              <View style={{ maxWidth: '100%', backgroundColor: colors.subtle, borderRadius: radius.badge,
                paddingHorizontal: YanjiSpacing.sm, paddingVertical: YanjiSpacing.xs }}>{categoryText}</View>
            ) : null}
          </View>
          <View style={{ flexDirection: 'row', flexShrink: 0 }}>
            <Pressable onPress={onToggleFavorite} disabled={disabled} accessibilityRole="button"
              accessibilityLabel={`${isFavorite ? '取消收藏' : '收藏'}记录：${title}`}
              accessibilityState={{ selected: isFavorite, disabled }} style={({ pressed }) => iconButton(pressed, false)}>
              <Animated.View style={starStyle}>
                <YanjiIcon name={isFavorite ? 'star' : 'starOutline'} size={layout.momentIconSize}
                  color={isFavorite ? colors.brandInk : colors.secondary} filled={isFavorite} />
              </Animated.View>
            </Pressable>
            <Pressable onPress={onMore} disabled={disabled} accessibilityRole="button"
              accessibilityLabel={`更多记录操作：${title}`} accessibilityState={{ disabled }}
              style={({ pressed }) => iconButton(pressed, true)}>
              <YanjiIcon name="more" size={layout.momentIconSize} color={colors.secondary} />
            </Pressable>
          </View>
        </View>
        <Pressable onPress={onPressContent} accessibilityRole="button"
          accessibilityLabel={`查看完整记录：${title}`} accessibilityHint="打开这条记录，可阅读全文或编辑"
          style={({ pressed }) => ({ minHeight: YanjiTouch.min, paddingVertical: YanjiSpacing.xs, justifyContent: 'flex-start', opacity: pressed ? 0.75 : 1 })}>
          <Text style={[typography.bodyMoment, { color: colors.ink }]} numberOfLines={4} ellipsizeMode="tail">{content}</Text>
        </Pressable>
      </View>
    </View>
  );
}
