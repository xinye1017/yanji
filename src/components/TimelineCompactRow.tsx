/**
 * One compact event on a day's spine — shared by Today's 「记录此刻」 and the
 * Review timeline.
 *
 * The clock holds a fixed 40dp gutter, the day's continuous page-edge rule runs
 * down that gutter's right edge, and the entry is a single line (tag · preview ·
 * optional meta · 「···」) with no container of its own. Every management action
 * lives in the anchored menu the screen opens. Presentation only: the screen
 * owns identity and every write.
 */
import React, { useRef } from 'react';
import { Pressable, Text, View } from 'react-native';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiPaperLayout, YanjiSpacing, YanjiTouch } from '../theme/tokens';
import { YanjiIcon } from './YanjiUI';
import type { YanjiIconName } from './icons';
import type { PopoverAnchor } from './TimelineActionPopover';

/**
 * The clock's gutter: one HH:MM column width for every row on both screens, so
 * the spine and the entries never drift between Today and Review.
 */
export const TIMELINE_TIME_COLUMN = YanjiPaperLayout.timelineTimeColumn;

/**
 * Semantic colour families for the tag.
 *
 * `brand` is the accent that means *action* — an in-place "do this". A tag is a
 * *state* of an entry, so it never borrows the action accent: `doubt` is the
 * waiting/paused family and `positive` the settled one.
 */
type CompactTagTone = 'positive' | 'doubt' | 'neutral';

interface TimelineCompactRowProps {
  timeLabel: string;
  tagLabel?: string | null;
  /** The category glyph inside the tag (lightbulb / circle-help / clock-3). */
  tagIcon?: YanjiIconName;
  /** 灵感 = neutral (category, not a verdict), 疑点 = doubt (waiting), sessions = neutral. */
  tagTone?: CompactTagTone;
  /** Right-aligned secondary text before the menu, e.g. a session duration. */
  metaText?: string;
  content: string;
  /** The day's latest event: its clock is the one the reader is standing on. */
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
  tagIcon,
  tagTone = 'neutral',
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
  const moreRef = useRef<React.ComponentRef<typeof View>>(null);
  const title = previewOf(content);
  const tone = tagTone === 'positive'
    ? { chipBg: colors.positiveSubtle, chipInk: colors.positive }
    : tagTone === 'doubt'
      ? { chipBg: colors.doubtSubtle, chipInk: colors.doubt }
      : { chipBg: colors.subtle, chipInk: colors.secondary };

  // Window coordinates are the only space both the row and the overlay agree
  // on; the screen converts them into the overlay's own origin.
  const openMenu = () => {
    moreRef.current?.measureInWindow((x, y, width, height) => onMore({ x, y, width, height }));
  };

  return (
    <View
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        minHeight: YanjiTouch.min,
        paddingVertical: YanjiSpacing.xs,
        // The boundary between two events is a single hairline; the day's last
        // row closes the list rather than ruling it off.
        borderBottomWidth: isLast ? 0 : layout.hairlineWidth,
        borderColor: colors.hairline,
      }}
    >
      {/*
        The page-edge rule. One absolutely positioned rule per row, each running
        from its own top edge to its own bottom edge, so consecutive rows stack
        into one unbroken vertical line: the first row starts below its top
        padding (at the content's top), the last stops at the content's bottom,
        and every rule in between touches its neighbours.
      */}
      <View
        accessible={false}
        importantForAccessibility="no-hide-descendants"
        style={{
          position: 'absolute',
          left: TIMELINE_TIME_COLUMN,
          top: isFirst ? YanjiSpacing.xs : 0,
          bottom: isLast ? YanjiSpacing.xs : 0,
          width: layout.hairlineWidth,
          backgroundColor: colors.hairline,
        }}
      />

      {/* The clock gutter: fixed width, flush with the page gutter. */}
      <View style={{ width: TIMELINE_TIME_COLUMN, alignItems: 'flex-start' }}>
        <Text
          numberOfLines={1}
          adjustsFontSizeToFit
          minimumFontScale={0.75}
          style={[
            typography.captionMeta,
            // Today's latest event is where the reader is standing, so its
            // clock alone carries ink weight rather than secondary grey.
            isFirst ? { color: colors.ink, fontWeight: '700' } : { color: colors.secondary },
          ]}
        >
          {timeLabel}
        </Text>
      </View>

      {/* The entry itself: no wash, no border, no radius — just its text. */}
      <View
        style={{
          flex: 1,
          minWidth: 0,
          flexDirection: 'row',
          alignItems: 'center',
          gap: YanjiSpacing.xs,
          paddingLeft: YanjiSpacing.sm,
        }}
      >
        {tagLabel ? (
          <View
            style={{
              flexShrink: 0,
              flexDirection: 'row',
              alignItems: 'center',
              gap: layout.taskTagPadding,
              backgroundColor: tone.chipBg,
              borderRadius: radius.badge,
              paddingHorizontal: layout.taskTagPaddingHorizontal,
              paddingVertical: layout.taskTagPadding,
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
                <YanjiIcon name={tagIcon} size={typography.captionMeta.fontSize} color={tone.chipInk} />
              </View>
            ) : null}
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
  );
}
