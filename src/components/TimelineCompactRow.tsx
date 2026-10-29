/**
 * One compact event on a day's spine — shared by Today's 「今日随笔」 list and
 * the Review timeline.
 *
 * The clock sits left of the node and the entry is a single line (tag · preview ·
 * optional meta · one action). A note needs a menu (resolve / edit / delete), so
 * it shows 「···」; a focus session has exactly one action, so its trash button is
 * direct instead of hiding a one-line menu behind another tap. Presentation
 * only: the screen owns identity and every write.
 */
import React, { useRef } from 'react';
import { Pressable, Text, View } from 'react-native';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { toSubtleColor, YanjiSpacing } from '../theme/tokens';
import { YanjiIcon } from './YanjiUI';
import type { YanjiIconName } from './icons';
import type { PopoverAnchor } from './TimelineActionPopover';

/** Semantic colour families for the tag; the dot follows unless overridden. */
type CompactTagTone = 'positive' | 'brand' | 'neutral';

/**
 * The row's single management affordance. A menu receives the button's window
 * rect so the screen can anchor the popover to it; a direct action just runs.
 * `label` falls back to the row's own 「更多记录操作：…」 wording.
 */
type RowAction =
  | { kind: 'menu'; label?: string; onOpen: (anchor: PopoverAnchor) => void }
  | { kind: 'delete'; label: string; onPress: () => void };

interface TimelineCompactRowProps {
  timeLabel: string;
  tagLabel?: string | null;
  /** The category glyph inside the tag (lightbulb / circle-help / clock-3). */
  tagIcon?: YanjiIconName;
  /** 灵感 = positive (green), 疑点 = brand (orange-red), sessions = neutral. */
  tagTone?: CompactTagTone;
  /** Explicit subject/tag color (e.g. subjectColor) that styles the capsule background wash and text. */
  tagColor?: string;
  /** Overrides the dot colour; sessions pass their subject colour. */
  dotColor?: string;
  /** Right-aligned secondary text before the menu, e.g. a session duration. */
  metaText?: string;
  /** Note text body. If omitted or empty, the row renders as a lightweight single-line event without a body card. */
  content?: string | null;
  isFirst: boolean;
  isLast: boolean;
  disabled: boolean;
  accessibilityLabel?: string;
  accessibilityHint?: string;
  action: RowAction;
  onPressContent?: () => void;
}

/**
 * The stored text for display: excessive blank lines collapse, and an empty
 * note gets a truthful placeholder. The persisted content is never touched.
 */
function previewOf(content: string): string {
  return content.replace(/\r\n/g, '\n').replace(/\n{3,}/g, '\n\n').trim() || '无标题记录';
}

/**
 * The 28dp icon button at the row's right edge, identical in both layouts so
 * the two row shapes share one hit target. Window coordinates are the only
 * space both the row and the overlay agree on; the screen converts them into
 * the overlay's own origin.
 */
function RowActionButton({ action, disabled, label }: {
  action: RowAction;
  disabled: boolean;
  /** Resolved by the row: a delete action names itself, a menu may fall back. */
  label: string;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, layout, radius } = paper;
  const buttonRef = useRef<React.ComponentRef<typeof View>>(null);
  const onPress = action.kind === 'menu'
    ? () => buttonRef.current?.measureInWindow((x, y, width, height) => action.onOpen({ x, y, width, height }))
    : action.onPress;
  return (
    <Pressable
      ref={buttonRef}
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityHint={action.kind === 'delete' ? '删除前会再确认一次' : undefined}
      accessibilityState={{ disabled }}
      hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
      style={({ pressed }) => ({
        width: 28,
        height: 28,
        alignItems: 'center',
        justifyContent: 'center',
        borderRadius: radius.pill,
        backgroundColor: pressed ? colors.subtle : 'transparent',
        opacity: disabled ? 0.5 : 1,
      })}
    >
      <YanjiIcon
        name={action.kind === 'menu' ? 'more' : 'delete'}
        size={layout.momentPreviewIconSize}
        color={colors.secondary}
      />
    </Pressable>
  );
}

export function TimelineCompactRow({
  timeLabel,
  tagLabel,
  tagIcon,
  tagTone = 'neutral',
  tagColor,
  dotColor,
  metaText,
  content,
  isFirst,
  isLast,
  disabled,
  accessibilityLabel,
  accessibilityHint,
  action,
  onPressContent,
}: TimelineCompactRowProps): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout, radius } = paper;
  const hasContent = Boolean(content && content.trim());
  const title = hasContent ? previewOf(content!) : '';

  const tone = tagTone === 'positive'
    ? { dot: colors.positive, chipBg: colors.positiveSubtle, chipInk: colors.positive }
    : tagTone === 'brand'
      ? { dot: colors.brand, chipBg: colors.brandSubtle, chipInk: colors.brandInk }
      : { dot: colors.secondary, chipBg: colors.subtle, chipInk: colors.secondary };

  const capsuleTone = tagColor
    ? {
        dot: tagColor,
        chipBg: toSubtleColor(tagColor, 0.14),
        chipInk: tagColor,
      }
    : tone;

  // Single-line compact row (focus session) centers at 16; multi-line card row (note) centers at 24
  const center = hasContent ? 24 : 16;
  const nodeRadius = layout.timelineNodeSize / 2;
  /** A menu without its own label borrows the row's wording; delete must name itself. */
  const actionLabel = action.kind === 'delete'
    ? action.label
    : action.label ?? (hasContent
      ? `更多记录操作：${title.replace(/\n+/g, ' ')}`
      : `更多记录操作：${tagLabel ?? ''}`);

  return (
    <View style={{ marginBottom: isLast ? 0 : layout.momentItemGap }}>
      <View style={{ flexDirection: 'row' }}>
        <View
          style={{
            width: 40,
            flexShrink: 0,
            marginTop: hasContent ? 10 : 0,
            height: hasContent ? 28 : 32,
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
              backgroundColor: dotColor ?? capsuleTone.dot,
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

        {hasContent ? (
          <View
            style={{
              flex: 1,
              minWidth: 0,
              backgroundColor: colors.elevated,
              borderRadius: radius.surface,
              borderWidth: layout.hairlineWidth,
              borderColor: colors.hairline,
              paddingHorizontal: YanjiSpacing.md,
              paddingTop: 10,
              paddingBottom: 12,
            }}
          >
            <View
              style={{
                flexDirection: 'row',
                alignItems: 'center',
                justifyContent: 'space-between',
                minHeight: 28,
              }}
            >
              {tagLabel ? (
                <View
                  style={{
                    flexShrink: 0,
                    flexDirection: 'row',
                    alignItems: 'center',
                    gap: layout.taskTagPadding + 2,
                    backgroundColor: capsuleTone.chipBg,
                    borderRadius: radius.pill,
                    paddingHorizontal: 8,
                    paddingVertical: 3,
                  }}
                >
                  {tagIcon ? (
                    // One fixed slot for every category, so the tag text starts at
                    // the same x whether the glyph is a bulb, a question or a clock.
                    <View
                      style={{
                        width: layout.momentTagIconSlot,
                        alignItems: 'center',
                        justifyContent: 'center',
                      }}
                    >
                      <YanjiIcon name={tagIcon} size={typography.captionMeta.fontSize} color={capsuleTone.chipInk} />
                    </View>
                  ) : null}
                  <Text numberOfLines={1} style={[typography.captionMeta, { color: capsuleTone.chipInk, fontWeight: '600' }]}>
                    {tagLabel}
                  </Text>
                </View>
              ) : <View />}

              <View style={{ flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.xs }}>
                {metaText ? (
                  <Text numberOfLines={1} style={[typography.captionMeta, { color: colors.secondary, flexShrink: 0 }]}>
                    {metaText}
                  </Text>
                ) : null}

                <RowActionButton action={action} disabled={disabled} label={actionLabel} />
              </View>
            </View>

            <Pressable
              onPress={onPressContent}
              accessibilityRole="button"
              accessibilityLabel={accessibilityLabel ?? `查看完整记录：${title.replace(/\n+/g, ' ')}`}
              accessibilityHint={accessibilityHint}
              style={({ pressed }) => ({
                marginTop: 8,
                opacity: pressed ? 0.7 : 1,
              })}
            >
              <Text
                numberOfLines={3}
                ellipsizeMode="tail"
                style={[typography.bodyPrimary, { color: colors.ink, lineHeight: 22, fontFamily: 'serif' }]}
              >
                {title}
              </Text>
            </Pressable>
          </View>
        ) : (
          <View
            style={{
              flex: 1,
              minWidth: 0,
              flexDirection: 'row',
              alignItems: 'center',
              justifyContent: 'space-between',
              minHeight: 32,
            }}
          >
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: YanjiSpacing.sm, flexShrink: 1 }}>
              {tagLabel ? (
                <View
                  style={{
                    flexShrink: 0,
                    flexDirection: 'row',
                    alignItems: 'center',
                    gap: layout.taskTagPadding + 2,
                    backgroundColor: capsuleTone.chipBg,
                    borderRadius: radius.pill,
                    paddingHorizontal: 9,
                    paddingVertical: 3.5,
                  }}
                >
                  {tagIcon ? (
                    <View
                      style={{
                        width: layout.momentTagIconSlot,
                        alignItems: 'center',
                        justifyContent: 'center',
                      }}
                    >
                      <YanjiIcon name={tagIcon} size={typography.captionMeta.fontSize} color={capsuleTone.chipInk} />
                    </View>
                  ) : null}
                  <Text numberOfLines={1} style={[typography.captionMeta, { color: capsuleTone.chipInk, fontWeight: '600' }]}>
                    {tagLabel}
                  </Text>
                </View>
              ) : null}

              {metaText ? (
                <Text numberOfLines={1} style={[typography.captionMeta, { color: colors.secondary, flexShrink: 1 }]}>
                  {metaText}
                </Text>
              ) : null}
            </View>

            <RowActionButton action={action} disabled={disabled} label={actionLabel} />
          </View>
        )}
      </View>
    </View>
  );
}
