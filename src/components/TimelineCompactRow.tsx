/**
 * One compact event on a day's spine — shared by Today's 「记录此刻」 and the
 * Review timeline.
 *
 * The clock sits left of the node, the entry is a single line (tag · preview ·
 * optional meta · 「···」), and every management action lives in the anchored
 * menu the screen opens. Presentation only: the screen owns identity and every
 * write.
 */
import React, { useRef, useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing, YanjiTouch } from '../theme/tokens';
import { YanjiIcon } from './YanjiUI';
import type { PopoverAnchor } from './TimelineActionPopover';

/** Semantic colour families for the tag; the dot follows unless overridden. */
export type CompactTagTone = 'positive' | 'brand' | 'neutral';

interface TimelineCompactRowProps {
  timeLabel: string;
  tagLabel?: string | null;
  /** 灵感 = positive (green), 疑点 = brand (orange-red), sessions = neutral. */
  tagTone?: CompactTagTone;
  /** Overrides the dot colour; sessions pass their subject colour. */
  dotColor?: string;
  /** Right-aligned secondary text before the menu, e.g. a session duration. */
  metaText?: string;
  content: string;
  isFirst: boolean;
  isLast: boolean;
  disabled: boolean;
  accessibilityLabel?: string;
  accessibilityHint?: string;
  moreAccessibilityLabel?: string;
  onPressContent: () => void;
  /** Receives the 「···」 button's window rect so the menu can anchor to it. */
  onMore: (anchor: PopoverAnchor) => void;
}

/**
 * The stored text as one display line: whitespace-only breaks collapse to a
 * space so the preview cannot start blank, and an empty note still gets a
 * truthful placeholder. The persisted content is never touched.
 */
function previewOf(content: string): string {
  return content.replace(/\s*\n+\s*/g, ' ').trim() || '无标题记录';
}

export function TimelineCompactRow({
  timeLabel,
  tagLabel,
  tagTone = 'neutral',
  dotColor,
  metaText,
  content,
  isFirst,
  isLast,
  disabled,
  accessibilityLabel,
  accessibilityHint,
  moreAccessibilityLabel,
  onPressContent,
  onMore,
}: TimelineCompactRowProps): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout, radius } = paper;
  const [rowHeight, setRowHeight] = useState<number>(YanjiTouch.min);
  const moreRef = useRef<React.ComponentRef<typeof View>>(null);
  const title = previewOf(content);
  const center = rowHeight / 2;
  const nodeRadius = layout.timelineNodeSize / 2;
  const tone = tagTone === 'positive'
    ? { dot: colors.positive, chipBg: colors.positiveSubtle, chipInk: colors.positive }
    : tagTone === 'brand'
      ? { dot: colors.brand, chipBg: colors.brandSubtle, chipInk: colors.brandInk }
      : { dot: colors.secondary, chipBg: colors.subtle, chipInk: colors.secondary };

  // Window coordinates are the only space both the row and the overlay agree
  // on; the screen converts them into the overlay's own origin.
  const openMenu = () => {
    moreRef.current?.measureInWindow((x, y, width, height) => onMore({ x, y, width, height }));
  };

  return (
    <View style={{ marginBottom: isLast ? 0 : layout.momentItemGap }}>
      <View style={{ flexDirection: 'row' }}>
        <View
          style={{
            // The clock sits flush with the page gutter and only a hair away
            // from its node. Every row uses the same HH:MM width, so the dots
            // stay aligned without reserving a wider fixed column.
            justifyContent: 'center',
            alignItems: 'flex-start',
            marginRight: YanjiSpacing.xs,
          }}
        >
          <Text numberOfLines={1} style={[typography.captionMeta, { color: colors.secondary }]}>
            {timeLabel}
          </Text>
        </View>

        <View
          accessible={false}
          importantForAccessibility="no-hide-descendants"
          style={{ width: layout.timelineAxisColumn, marginRight: layout.momentAxisGap, alignItems: 'center' }}
        >
          {!isFirst ? (
            <View
              style={{
                position: 'absolute',
                top: 0,
                height: Math.max(0, center - nodeRadius - YanjiSpacing.xs),
                width: layout.hairlineWidth,
                backgroundColor: colors.hairline,
              }}
            />
          ) : null}
          <View
            style={{
              position: 'absolute',
              top: center - nodeRadius,
              width: layout.timelineNodeSize,
              height: layout.timelineNodeSize,
              borderRadius: nodeRadius,
              backgroundColor: dotColor ?? tone.dot,
            }}
          />
          {!isLast ? (
            <View
              style={{
                position: 'absolute',
                top: center + nodeRadius + YanjiSpacing.xs,
                bottom: -layout.momentItemGap,
                width: layout.hairlineWidth,
                backgroundColor: colors.hairline,
              }}
            />
          ) : null}
        </View>

        <View
          onLayout={event => setRowHeight(event.nativeEvent.layout.height)}
          style={{
            flex: 1,
            minWidth: 0,
            flexDirection: 'row',
            alignItems: 'center',
            gap: YanjiSpacing.xs,
            minHeight: YanjiTouch.min,
            backgroundColor: colors.elevated,
            borderRadius: radius.tag,
            borderWidth: layout.hairlineWidth,
            borderColor: colors.hairline,
            paddingLeft: YanjiSpacing.sm,
          }}
        >
          {tagLabel ? (
            <View
              style={{
                flexShrink: 0,
                backgroundColor: tone.chipBg,
                borderRadius: radius.badge,
                paddingHorizontal: layout.taskTagPaddingHorizontal,
                paddingVertical: layout.taskTagPadding,
              }}
            >
              <Text numberOfLines={1} style={[typography.captionMeta, { color: tone.chipInk }]}>
                {tagLabel}
              </Text>
            </View>
          ) : null}

          <Pressable
            onPress={onPressContent}
            accessibilityRole="button"
            accessibilityLabel={accessibilityLabel ?? `查看完整记录：${title}`}
            accessibilityHint={accessibilityHint}
            style={({ pressed }) => ({
              flex: 1,
              minWidth: 0,
              minHeight: YanjiTouch.min,
              justifyContent: 'center',
              paddingHorizontal: YanjiSpacing.xs,
              opacity: pressed ? 0.7 : 1,
            })}
          >
            <Text
              numberOfLines={1}
              ellipsizeMode="tail"
              style={[typography.bodyPrimary, { color: colors.ink }]}
            >
              {title}
            </Text>
          </Pressable>

          {metaText ? (
            <Text numberOfLines={1} style={[typography.captionMeta, { color: colors.secondary, flexShrink: 0 }]}>
              {metaText}
            </Text>
          ) : null}

          <Pressable
            ref={moreRef}
            onPress={openMenu}
            disabled={disabled}
            accessibilityRole="button"
            accessibilityLabel={moreAccessibilityLabel ?? `更多记录操作：${title}`}
            accessibilityState={{ disabled }}
            hitSlop={{ top: 4, bottom: 4, left: 4, right: 4 }}
            style={({ pressed }) => ({
              width: YanjiTouch.min,
              height: YanjiTouch.min,
              alignItems: 'center',
              justifyContent: 'center',
              borderRadius: radius.pill,
              backgroundColor: pressed ? colors.subtle : 'transparent',
              opacity: disabled ? 0.5 : 1,
            })}
          >
            <YanjiIcon name="more" size={layout.momentPreviewIconSize} color={colors.secondary} />
          </Pressable>
        </View>
      </View>
    </View>
  );
}
