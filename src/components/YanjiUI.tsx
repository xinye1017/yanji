/**
 * Shared RN UI primitives for the 素笺与寸阴 (Paper & Chrono) body.
 *
 * Two rules keep this file coherent:
 *  1. Every control, input, section and chart reads its colour, type, radius
 *     and motion from `theme.paper`. Raw hex / dp literals are not allowed here.
 *  2. The liquid-glass navigation imports only `YanjiIcon` from this module.
 *     `YanjiIconButton` pins its circle to `YanjiRadius.full` — the same step
 *     the dock uses — so the one primitive shared by nav and body cannot drift
 *     into a second geometry.
 */

import React, { useEffect, useState } from 'react';
import {
  Keyboard,
  Platform,
  Pressable,
  Text,
  TextInput,
  View,
  type StyleProp,
  type TextStyle,
  type ViewStyle,
} from 'react-native';
import Animated, {
  Easing,
  useAnimatedStyle,
  useReducedMotion,
  useSharedValue,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Circle, ClipPath, Defs, G, Line, Path, Rect } from 'react-native-svg';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { toSubtleColor, YanjiRadius, YanjiSpacing, YanjiTouch } from '../theme/tokens';
export { toSubtleColor } from '../theme/tokens';
import { buildLineChartGeometry } from './lineChartGeometry';
import { YANJI_ICON_STROKE, getYanjiIcon } from './icons';
import type { YanjiIconName } from './icons';

// ---------------------------------------------------------------------------
// YanjiIcon — the only way a glyph enters the tree
//
// The bottom dock renders through this component, so its default colour is the
// same ink the body uses.
// ---------------------------------------------------------------------------

export function YanjiIcon({
  name,
  size = 20,
  color,
  strokeWidth = YANJI_ICON_STROKE,
  filled = false,
}: {
  name: YanjiIconName;
  size?: number;
  color?: string;
  strokeWidth?: number;
  filled?: boolean;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const Glyph = getYanjiIcon(name);
  return (
    <Glyph
      size={size}
      color={color ?? paper.colors.ink}
      strokeWidth={strokeWidth}
      fill={filled ? (color ?? paper.colors.ink) : 'none'}
    />
  );
}

// ---------------------------------------------------------------------------
// useYanjiKeyboardInset — real Android IME height for sheets
//
// Why not `KeyboardAvoidingView` alone: a sheet inside an RN `Modal` lives in
// a separate Android window, and the view derives its offset from its own
// on-screen frame while the IME event is delivered against the Activity root.
// The two never agree inside a Modal, so the offset comes out 0 and the sheet
// ends up underneath the keyboard. Sampling `Keyboard.metrics()` and reserving
// that many points of scroll room is what actually keeps the save action
// reachable.
// ---------------------------------------------------------------------------

export function useYanjiKeyboardInset(): number {
  const [height, setHeight] = useState(0);

  useEffect(() => {
    if (Platform.OS !== 'android') {
      setHeight(0);
      return;
    }
    const sync = () => setHeight(Keyboard.metrics()?.height ?? 0);
    sync();
    const show = Keyboard.addListener('keyboardDidShow', sync);
    const hide = Keyboard.addListener('keyboardDidHide', () => setHeight(0));
    return () => {
      show.remove();
      hide.remove();
    };
  }, []);

  return height;
}

// ---------------------------------------------------------------------------
// YanjiPaperSheetHeading — the one sheet header (style only)
//
// The sheet itself owns its keyboard, back and save semantics; this only draws
// the title block and a reachable close control.
// ---------------------------------------------------------------------------

export function YanjiPaperSheetHeading({
  title,
  subtitle,
  onClose,
  disabled = false,
  closeTone = 'filled',
  titleStyle,
  right,
}: {
  title: string;
  subtitle?: string;
  onClose?: () => void;
  disabled?: boolean;
  closeTone?: 'filled' | 'plain';
  titleStyle?: TextStyle;
  right?: React.ReactNode;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const paper = theme.paper;
  return (
    <View
      style={{
        flexDirection: 'row',
        alignItems: 'flex-start',
        justifyContent: 'space-between',
        gap: paper.layout.gridGap,
      }}
    >
      <View style={{ flex: 1 }}>
        <Text
          accessibilityRole="header"
          style={[paper.typography.titleEditorial, titleStyle, { color: paper.colors.ink }]}
        >
          {title}
        </Text>
        {subtitle ? (
          <Text
            style={[
              paper.typography.captionMeta,
              { color: paper.colors.secondary, marginTop: YanjiSpacing.xs },
            ]}
          >
            {subtitle}
          </Text>
        ) : null}
      </View>
      {right !== undefined ? (
        right
      ) : onClose ? (
        <YanjiIconButton
          icon="close"
          accessibilityLabel="关闭"
          onPress={onClose}
          disabled={disabled}
          size={paper.layout.rowMinHeight}
          iconSize={17}
          tone={closeTone}
        />
      ) : null}
    </View>
  );
}

// ---------------------------------------------------------------------------
// YanjiEditorialHeader — the page's opening line
// ---------------------------------------------------------------------------

interface YanjiEditorialHeaderProps {
  eyebrow?: string;
  eyebrowBadge?: React.ReactNode;
  title: string;
  titleStyle?: TextStyle;
  subtitle?: string;
  trailing?: React.ReactNode;
}

export function YanjiEditorialHeader({
  eyebrow,
  eyebrowBadge,
  title,
  titleStyle,
  subtitle,
  trailing,
}: YanjiEditorialHeaderProps): React.JSX.Element {
  const theme = useYanjiTheme();
  const paper = theme.paper;
  return (
    <View
      style={{
        flexDirection: 'row',
        alignItems: 'flex-start',
        justifyContent: 'space-between',
        gap: paper.layout.gridGap,
      }}
    >
      <View style={{ flex: 1 }}>
        {eyebrow || eyebrowBadge ? (
          <View
            style={{
              flexDirection: 'row',
              alignItems: 'center',
              flexWrap: 'wrap',
              gap: YanjiSpacing.sm,
              marginBottom: 4,
            }}
          >
            {eyebrow ? (
              <Text style={[paper.typography.captionMeta, { color: paper.colors.secondary }]}>
                {eyebrow}
              </Text>
            ) : null}
            {eyebrowBadge}
          </View>
        ) : null}
        <Text
          accessibilityRole="header"
          style={[paper.typography.titleEditorial, titleStyle, { color: paper.colors.ink }]}
        >
          {title}
        </Text>
        {subtitle ? (
          <Text
            style={[
              paper.typography.bodyPrimary,
              { color: paper.colors.secondary, marginTop: YanjiSpacing.xs },
            ]}
          >
            {subtitle}
          </Text>
        ) : null}
      </View>
      {trailing}
    </View>
  );
}

// ---------------------------------------------------------------------------
// YanjiPaperSectionHeading — typographic grouping with an optional accessory
// ---------------------------------------------------------------------------

export function YanjiPaperSectionHeading({
  title,
  countText,
  right,
  spaceBefore = 14,
  spaceAfter = 2,
  titleStyle,
  align = 'baseline',
  style,
}: {
  title: string;
  countText?: string;
  right?: React.ReactNode;
  spaceBefore?: number;
  spaceAfter?: number;
  titleStyle?: TextStyle;
  align?: 'baseline' | 'center';
  style?: StyleProp<ViewStyle>;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const paper = theme.paper;
  return (
    <View
      style={[{
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: align,
        // No wrap: the heading is one line, and a wrapping row lets the action
        // drop below the title instead of sitting against the right edge.
        gap: YanjiSpacing.sm,
        marginBottom: spaceAfter,
        marginTop: spaceBefore,
      }, style]}
    >
      <View style={{ flexDirection: 'row', alignItems: 'baseline', gap: YanjiSpacing.xs, flexShrink: 1 }}>
        <Text
          accessibilityRole="header"
          style={[paper.typography.titleSection, titleStyle, { color: paper.colors.ink }]}
        >
          {title}
        </Text>
        {countText ? (
          // AB-01 sets this count at 12sp in the muted tier. It is real progress
          // data, and `muted` is reserved for decoration, so it keeps the
          // readable `secondary` tier at AB-01's size instead.
          <Text style={[paper.typography.captionMeta, { color: paper.colors.secondary }]}>
            {countText}
          </Text>
        ) : null}
      </View>
      {right}
    </View>
  );
}

// ---------------------------------------------------------------------------
// YanjiPaperSegmentedControl — one-of-N switch inside a subtle track
// ---------------------------------------------------------------------------

export interface YanjiPaperSegmentedOption<T extends string> {
  value: T;
  label: string;
  icon?: YanjiIconName;
}

export function YanjiPaperSegmentedControl<T extends string>({
  value,
  options,
  onChange,
  accessibilityLabel,
  compact = false,
  borderless = false,
  disabled = false,
}: {
  value: T;
  options: readonly YanjiPaperSegmentedOption<T>[];
  onChange: (value: T) => void;
  accessibilityLabel: string;
  compact?: boolean;
  borderless?: boolean;
  disabled?: boolean;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const paper = theme.paper;
  const reduceMotion = useReducedMotion();
  const segmentHeight = borderless ? paper.layout.borderlessSegmentHeight : paper.layout.compactSegmentHeight;
  // Inset the compact paint inside full-height radios so the touch area stays 48dp.
  const segmentInset = compact ? (paper.layout.rowMinHeight - segmentHeight) / 2 : 0;
  const trackPadding = borderless
    ? paper.layout.borderlessSegmentPadding
    : YanjiSpacing.xs + paper.layout.hairlineWidth;

  return (
    <View
      accessibilityRole="radiogroup"
      accessibilityLabel={accessibilityLabel}
      style={{
        flexDirection: 'row',
        alignItems: 'stretch',
        alignSelf: 'flex-start',
        backgroundColor: compact ? 'transparent' : paper.colors.subtle,
        borderRadius: paper.radius.pill,
        borderWidth: compact || borderless ? 0 : paper.layout.hairlineWidth,
        borderColor: paper.colors.hairline,
        paddingVertical: compact ? 0 : YanjiSpacing.xs,
        paddingHorizontal: compact ? trackPadding : YanjiSpacing.xs,
      }}
    >
      {compact ? (
        <View
          pointerEvents="none"
          style={{
            position: 'absolute',
            left: 0,
            right: 0,
            top: segmentInset - trackPadding,
            bottom: segmentInset - trackPadding,
            backgroundColor: paper.colors.subtle,
            borderRadius: paper.radius.pill,
            borderWidth: borderless ? 0 : paper.layout.hairlineWidth,
            borderColor: paper.colors.hairline,
          }}
        />
      ) : null}
      {options.map(option => (
        <PaperSegment
          key={option.value}
          label={option.label}
          icon={option.icon}
          selected={option.value === value}
          compact={compact}
          borderless={borderless}
          segmentInset={segmentInset}
          reduceMotion={reduceMotion}
          disabled={disabled}
          onPress={() => onChange(option.value)}
        />
      ))}
    </View>
  );
}

function PaperSegment({
  label,
  icon,
  selected,
  compact,
  borderless,
  segmentInset,
  reduceMotion,
  disabled,
  onPress,
}: {
  label: string;
  icon?: YanjiIconName;
  selected: boolean;
  compact: boolean;
  borderless: boolean;
  segmentInset: number;
  reduceMotion: boolean;
  disabled: boolean;
  onPress: () => void;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const paper = theme.paper;
  const emphasis = useSharedValue(selected ? 1 : 0);

  useEffect(() => {
    // Reduced motion: the fill appears and disappears rather than crossfading.
    if (reduceMotion) {
      emphasis.value = selected ? 1 : 0;
      return;
    }
    emphasis.value = withTiming(selected ? 1 : 0, { duration: paper.motion.selectionMs });
  }, [selected, reduceMotion, paper.motion.selectionMs, emphasis]);

  const fill = useAnimatedStyle(() => ({ opacity: emphasis.value }));

  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="radio"
      accessibilityState={{ selected, disabled }}
      style={({ pressed }) => ({
        minHeight: compact ? paper.layout.rowMinHeight : paper.layout.rowMinHeight - YanjiSpacing.xs,
        minWidth: paper.layout.rowMinHeight,
        paddingHorizontal: compact ? YanjiSpacing.sm : YanjiSpacing.lg,
        paddingVertical: segmentInset,
        alignItems: 'center',
        justifyContent: 'center',
        borderRadius: paper.radius.pill,
        // Press feedback is an instant state change, not an animation, so
        // reduced motion keeps it — only the fill crossfade above is gated.
        opacity: disabled ? 0.5 : pressed ? 0.7 : 1,
      })}
    >
      <Animated.View
        style={[
          {
            position: 'absolute',
            left: 0,
            right: 0,
            top: segmentInset,
            bottom: segmentInset,
            backgroundColor: paper.colors.surface,
            borderRadius: paper.radius.pill,
            borderWidth: borderless ? 0 : paper.layout.hairlineWidth,
            borderColor: paper.colors.hairline,
          },
          fill,
        ]}
      />
      <View style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6 }}>
        {icon ? (
          <YanjiIcon
            name={icon}
            size={compact ? 13 : 15}
            color={disabled ? paper.colors.muted : selected ? paper.colors.ink : paper.colors.secondary}
          />
        ) : null}
        <Text
          style={[
            compact ? paper.typography.captionMeta : paper.typography.bodyPrimary,
            {
              color: disabled ? paper.colors.muted : selected ? paper.colors.ink : paper.colors.secondary,
              fontWeight: selected ? '700' : '500',
            },
          ]}
        >
          {label}
        </Text>
      </View>
    </Pressable>
  );
}

// ---------------------------------------------------------------------------
// YanjiChronoSpectrum — 今日光阴光谱尺
//
// With a goal, the unfilled track represents the remaining time. Without one,
// the measured segments fill the width as a subject distribution (also Review).
// ---------------------------------------------------------------------------

export interface YanjiSpectrumSegment {
  id: string;
  label: string;
  seconds: number;
  color: string;
}

interface YanjiChronoSpectrumProps {
  segments: readonly YanjiSpectrumSegment[];
  totalSeconds: number;
  goalSeconds: number | null;
  accessibilityLabel?: string;
}

function formatSpectrumDuration(totalSeconds: number): string {
  const safeMinutes = Math.max(0, Math.floor(totalSeconds / 60));
  const hours = Math.floor(safeMinutes / 60);
  const minutes = safeMinutes % 60;
  if (hours > 0) return `${hours}h ${minutes}m`;
  return `${minutes}m`;
}

export function YanjiChronoSpectrum({
  segments,
  totalSeconds,
  goalSeconds,
  accessibilityLabel,
}: YanjiChronoSpectrumProps): React.JSX.Element {
  const theme = useYanjiTheme();
  const paper = theme.paper;
  const hasGoal = goalSeconds !== null && goalSeconds > 0;
  const ratio = hasGoal ? totalSeconds / (goalSeconds as number) : 0;
  const overGoal = hasGoal && ratio > 1;
  const markerPercent = Math.max(0, Math.min(100, ratio * 100));

  const filled = segments.filter(segment => segment.seconds > 0);
  const measuredSum = filled.reduce((sum, segment) => sum + segment.seconds, 0);
  const scale = Math.max(measuredSum, goalSeconds ?? 0);
  const filledPercent = scale > 0 ? measuredSum / scale * 100 : 0;
  const [filledWidth, setFilledWidth] = useState(0);
  // Short progress must retain colored area even when several subjects share it.
  const segmentGap = filled.length > 1
    ? Math.min(paper.layout.spectrumGap, filledWidth / (filled.length * 2)) : 0;
  const defaultLabel = [
    `今日专注 ${formatSpectrumDuration(totalSeconds)}`,
    hasGoal
      ? `，目标 ${formatSpectrumDuration(goalSeconds as number)}${
          overGoal ? '，已超出目标' : ''
        }`
      : '，未设置目标',
    filled.length > 0
      ? `：${filled
          .map(segment => `${segment.label} ${formatSpectrumDuration(segment.seconds)}`)
          .join('，')}`
      : '，暂无记录',
  ].join('');

  return (
    <View
      accessibilityRole="progressbar"
      accessibilityLabel={accessibilityLabel ?? defaultLabel}
      accessibilityValue={hasGoal ? { min: 0, max: 100, now: Math.round(markerPercent) } : undefined}
      style={{
        height: paper.layout.spectrumHeight,
        borderRadius: paper.radius.tag,
        backgroundColor: paper.colors.subtle,
        overflow: 'hidden',
      }}
    >
      <View onLayout={event => setFilledWidth(event.nativeEvent.layout.width)}
        style={{ width: `${filledPercent}%`, height: '100%', flexDirection: 'row', gap: segmentGap, backgroundColor: paper.colors.canvas, overflow: 'hidden' }}>
        {filled.map(segment => (
          <View
            key={segment.id}
            style={{
              flexGrow: segment.seconds,
              flexBasis: 0,
              backgroundColor: segment.color,
            }}
          />
        ))}
      </View>
    </View>
  );
}

// ---------------------------------------------------------------------------
// YanjiDayRhythmChart — saved continuous focus across 06:00–24:00
//
// A fixed-width bar with a fixed gap used to render as a row of dots: on a
// day with no saved focus that row of 18 tiny ticks read as a pagination
// indicator rather than a timeline. The bars therefore share the full width,
// stand on a hairline axis, and the axis is labelled at both ends so the strip
// says "this is today's clock", not "there are more pages".
//
// An hour with no focus draws nothing rather than a stub, so an empty day is a
// clean, labelled axis instead of a second dashed line.
//
// The current hour gets a short tick, but only once the strip actually has a
// bar to relate to. On a fully empty day that tick is a lone mark with no
// referent — exactly the "unexplained dot" this section was rebuilt to remove —
// so an empty day draws the axis and nothing else.
//
// Purely visual for its bars, but the axis range is the section's scope and a
// screen reader user needs it: the range used to live in the collapsed row's
// `accessibilityLabel` and would otherwise survive only as painted text inside
// a `no-hide-descendants` subtree. The chart therefore publishes one spoken
// node carrying the range (and an optional summary), and hides only the bars.
// ---------------------------------------------------------------------------

export interface YanjiDayRhythmBucket {
  hour: number;
  seconds: number;
  color?: string;
}

/**
 * How many hourly slots actually carry saved focus.
 *
 * Exported because it is the one fact the strip's appearance is derived from,
 * and both the spoken summary and the tests should ask the same question of the
 * data rather than inspect the rendered style objects.
 */
export function measuredRhythmHours(buckets: readonly YanjiDayRhythmBucket[]): number {
  return buckets.filter(bucket => bucket.seconds > 0).length;
}

/** One rendered slot's geometry, independent of how it is painted. */
interface YanjiRhythmSlot {
  hour: number;
  /** True when this hour carries saved focus. */
  measured: boolean;
  /** True when this slot is the current hour *and* the strip has any data. */
  isNow: boolean;
  /** Height in dp; `0` means the slot draws nothing. */
  height: number;
}

/**
 * The strip's slots: which hours are painted, and how tall.
 *
 * Separated from the component so the "an empty day draws nothing" rule can be
 * stated as arithmetic instead of asserting on style objects, and so no
 * test-only prop has to exist on the rendered tree.
 */
export function rhythmSlots(
  buckets: readonly YanjiDayRhythmBucket[],
  currentHour: number,
  chartHeight: number,
  emptyHeight: number,
): YanjiRhythmSlot[] {
  const maximum = Math.max(1800, ...buckets.map(bucket => bucket.seconds));
  const hasData = measuredRhythmHours(buckets) > 0;
  return buckets.map(bucket => {
    const measured = bucket.seconds > 0;
    const isNow = hasData && bucket.hour === currentHour;
    return {
      hour: bucket.hour,
      measured,
      isNow,
      height: measured
        ? Math.max(emptyHeight, bucket.seconds / maximum * chartHeight)
        : isNow ? emptyHeight : 0,
    };
  });
}

export function YanjiDayRhythmChart({
  buckets,
  currentHour,
  startLabel,
  endLabel,
  accessibilityLabel,
}: {
  buckets: readonly YanjiDayRhythmBucket[];
  currentHour: number;
  startLabel: string;
  endLabel: string;
  /** Spoken scope for the strip; defaults to the axis range alone. */
  accessibilityLabel?: string;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout, radius } = paper;
  const slots = rhythmSlots(buckets, currentHour, layout.rhythmChartHeight, layout.rhythmEmptyHeight);
  const hasData = measuredRhythmHours(buckets) > 0;
  return (
    <View>
      <View
        accessible={false}
        importantForAccessibility="no-hide-descendants"
        style={{
          flexDirection: 'row',
          alignItems: 'flex-end',
          gap: layout.rhythmBarGap,
          height: layout.rhythmChartHeight,
        }}
      >
        {slots.map(slot => (
          <View
            key={slot.hour}
            style={{
              flex: 1,
              flexBasis: 0,
              height: slot.height > 0 ? slot.height : (hasData ? 2 : 0),
              borderRadius: radius.tick,
              backgroundColor: slot.measured
                ? buckets.find(bucket => bucket.hour === slot.hour)?.color ?? colors.secondary
                : slot.isNow ? colors.secondary : (hasData ? colors.subtle : 'transparent'),
            }}
          />
        ))}
      </View>
      <YanjiHairline />
      {/* The painted range is decorative text; the spoken range is the node
          below, so the two can never disagree about the section's scope. */}
      <View
        accessible
        accessibilityRole="text"
        accessibilityLabel={accessibilityLabel ?? `时间范围 ${startLabel} 至 ${endLabel}`}
        style={{ flexDirection: 'row', justifyContent: 'space-between', marginTop: 6 }}
      >
        {['06', '09', '12', '15', '18', '21', '24'].map(mark => (
          <Text key={mark} style={[typography.captionMeta, { color: colors.secondary, fontSize: 11, fontVariant: ['tabular-nums'] }]}>
            {mark}
          </Text>
        ))}
      </View>
    </View>
  );
}

// ---------------------------------------------------------------------------
// YanjiTextAction — compact label inside a full-size touch target
//
// `tone` is how a page keeps one accent. The default `brand` is the in-place
// action a section is *about* (add the thing this list is missing); `quiet`
// is a secondary invitation that must stay findable without competing with
// the page's one filled CTA.
// ---------------------------------------------------------------------------

export function YanjiTextAction({ label, accessibilityLabel, onPress, disabled = false, tone = 'brand', testID }: {
  label: string;
  accessibilityLabel?: string;
  onPress: () => void;
  disabled?: boolean;
  /**
   * `brand` = the section's own in-place action; `quiet` = a secondary
   * invitation; `amber` = an action that belongs to a waiting notice, so it
   * reads as part of that notice rather than competing with the page accent.
   */
  tone?: 'brand' | 'quiet' | 'amber';
  testID?: string;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  return (
    <Pressable
      testID={testID}
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel ?? label}
      accessibilityState={{ disabled }}
      // A section accessory ("新建" / "写一条") is a text link beside a heading, not
      // a row: the 48dp floor it used to carry pushed every heading it sat in
      // down by ~17dp and made the whole page read airier than the design. The
      // touch target is restored with hitSlop so the glyph stays on the baseline.
      hitSlop={{ top: 14, bottom: 14, left: 12, right: 12 }}
      style={({ pressed }) => ({
        justifyContent: 'center',
        opacity: pressed ? 0.65 : 1,
      })}
    >
      <Text style={[
        paper.typography.captionMeta,
        {
          color: tone === 'quiet'
            ? paper.colors.secondary
            : tone === 'amber'
              ? paper.colors.amber
              : paper.colors.brandInk,
          fontWeight: tone === 'brand' ? '700' : '600',
        },
      ]}>
        {label}
      </Text>
    </Pressable>
  );
}

// ---------------------------------------------------------------------------
// YanjiStaleDataNotice — a page that is showing real but possibly old numbers
//
// The coordinator keeps the last good snapshot when a Room read fails, because
// blanking a page that has content is worse than showing content that may lag.
// That trade is only honest if the page says so. This is the one place that
// says it: amber (the product's waiting accent) rather than danger red, since
// nothing was lost — the numbers on screen are real, just not confirmed fresh.
// ---------------------------------------------------------------------------

export function YanjiStaleDataNotice({
  message,
  actionLabel,
  onAction,
  busy = false,
  style,
}: {
  message: string;
  actionLabel: string;
  onAction: () => void;
  /** Disables the retry and relabels it while a retry is already running. */
  busy?: boolean;
  style?: ViewStyle;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, layout, radius, typography } = paper;
  return (
    <View
      // The message is a status, not an error dialog: it is announced politely
      // so a screen reader finishes the numbers it was already reading first.
      accessibilityLiveRegion="polite"
      style={[
        {
          flexDirection: 'row',
          alignItems: 'center',
          flexWrap: 'wrap',
          gap: YanjiSpacing.sm,
          backgroundColor: colors.amberSubtle,
          borderWidth: layout.hairlineWidth,
          borderColor: colors.hairline,
          borderRadius: radius.tag,
          paddingHorizontal: YanjiSpacing.md,
          paddingVertical: YanjiSpacing.xs,
        },
        style,
      ]}
    >
      <Text style={[typography.captionMeta, { color: colors.amber, flex: 1 }]}>{message}</Text>
      <YanjiTextAction
        label={actionLabel}
        onPress={onAction}
        disabled={busy}
        tone="amber"
      />
    </View>
  );
}

// ---------------------------------------------------------------------------
// YanjiEmptyLedgerRow — a pressable invitation on the paper
// ---------------------------------------------------------------------------

export function YanjiEmptyLedgerRow({
  prompt,
  onPress,
  actionLabel = '新建任务 ›',
}: {
  prompt: string;
  onPress: () => void;
  /** `null` when the section heading already owns the only create entry. */
  actionLabel?: string | null;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout, radius } = paper;
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={prompt}
      style={({ pressed }) => ({
        minHeight: layout.rowMinHeight,
        flexDirection: 'row',
        alignItems: 'center',
        gap: YanjiSpacing.sm,
        backgroundColor: colors.elevated,
        borderWidth: layout.hairlineWidth,
        borderColor: colors.hairline,
        borderStyle: 'dashed',
        borderRadius: radius.tag,
        paddingHorizontal: YanjiSpacing.md,
        paddingVertical: YanjiSpacing.sm,
        marginTop: YanjiSpacing.xs,
        opacity: pressed ? 0.65 : 1,
      })}
    >
      <View
        style={{
          width: layout.timelineNodeSize,
          height: layout.timelineNodeSize,
          borderRadius: layout.timelineNodeSize / 2,
          borderWidth: layout.hairlineWidth,
          borderColor: colors.secondary,
        }}
      />
      <Text style={[typography.captionMeta, { color: colors.secondary, flex: 1 }]}>
        {prompt}
      </Text>
      {actionLabel ? (
        <Text style={[typography.captionMeta, { color: colors.brandInk, fontWeight: '600' }]}>
          {actionLabel}
        </Text>
      ) : null}
    </Pressable>
  );
}

// ---------------------------------------------------------------------------
// YanjiCard — a sheet on the desk, or an unboxed journal group
// ---------------------------------------------------------------------------

export function YanjiCard({
  children,
  style,
  variant = 'default',
}: {
  children: React.ReactNode;
  style?: ViewStyle;
  /**
   * `default`  a sheet lying flat on the desk with a hairline rule
   * `hero`     the sheet the eye should land on first — borderless, read by its
   *            cast shadow alone, and vertically tighter because it holds a
   *            line or two rather than a block of content
   * `sunken`   a well pressed into a sheet — empty states and recessed content
   * `plain`    unboxed typography for daily plans and journal entries
   */
  variant?: 'default' | 'hero' | 'sunken' | 'plain';
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const paper = theme.paper;

  const hero = variant === 'hero';
  const plain = variant === 'plain';
  const backgroundColor = plain
    ? 'transparent'
    : variant === 'sunken'
      ? paper.colors.subtle
      : hero
        ? paper.colors.surface
        : paper.colors.elevated;

  const inset = plain
    ? 0
    : variant === 'sunken'
      ? YanjiSpacing.lg
      : YanjiSpacing.lg + YanjiSpacing.xs;
  // A lifted sheet carries no rule — its shadow already separates it from the
  // desk, and the two together read as a bordered box. It also spends less
  // paper above and below: a slip holding one line of prose should not reserve
  // a paragraph's worth of blank space.
  const paddingVertical = hero ? YanjiSpacing.md : inset;

  return (
    <View
      style={[
        {
          backgroundColor,
          borderRadius: paper.radius.surface,
          borderWidth: plain || hero ? 0 : paper.layout.hairlineWidth,
          borderColor: paper.colors.hairline,
          paddingVertical,
          paddingHorizontal: inset,
        },
        // Only a lifted sheet casts a shadow — otherwise every block on screen
        // would look equally important.
        hero ? theme.shadow.sheet(theme.isDark) : null,
        style,
      ]}
    >
      {children}
    </View>
  );
}

// ---------------------------------------------------------------------------
// YanjiHairline — the one legitimate 1px rule
// ---------------------------------------------------------------------------

export function YanjiHairline({
  inset = 0,
  style,
}: {
  /** Symmetric inset, matching the horizontal padding of the rows it divides. */
  inset?: number;
  style?: ViewStyle;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const paper = theme.paper;
  return (
    <View
      style={[
        {
          height: paper.layout.hairlineWidth,
          marginLeft: inset,
          marginRight: inset,
          backgroundColor: paper.colors.hairline,
        },
        style,
      ]}
    />
  );
}

// ---------------------------------------------------------------------------
// YanjiBadge — restrained micro capsule
// ---------------------------------------------------------------------------

export function YanjiBadge({
  label,
  icon,
  tone = 'brand',
  style,
}: {
  label: string;
  icon?: YanjiIconName;
  tone?: 'brand' | 'neutral' | 'amber' | 'positive';
  style?: ViewStyle;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const paper = theme.paper;
  const bg =
    tone === 'brand'
      ? paper.colors.brandSubtle
      : tone === 'positive'
        ? paper.colors.positiveSubtle
        : tone === 'amber'
          ? paper.colors.amberSubtle
          : paper.colors.subtle;
  const fg =
    tone === 'brand'
      ? paper.colors.brandInk
      : tone === 'positive'
        ? paper.colors.positive
        : tone === 'amber'
          ? paper.colors.amber
          : paper.colors.secondary;

  return (
    <View
      style={[
        {
          flexDirection: 'row',
          alignItems: 'center',
          backgroundColor: bg,
          paddingVertical: 3,
          paddingHorizontal: 8,
          borderRadius: paper.radius.badge,
          alignSelf: 'flex-start',
        },
        style,
      ]}
    >
      {icon ? (
        <View style={{ marginRight: 4 }}>
          <YanjiIcon name={icon} size={11} color={fg} />
        </View>
      ) : null}
      <Text
        style={{
          fontSize: 11,
          lineHeight: 14,
          fontWeight: '600',
          color: fg,
        }}
      >
        {label}
      </Text>
    </View>
  );
}

// ---------------------------------------------------------------------------
// YanjiSubjectProportionRow — subject investment row with proportional bar
// ---------------------------------------------------------------------------

export function YanjiSubjectProportionRow({
  label,
  color,
  metaText,
  ratio,
  selected = false,
  onPress,
  showBar = true,
}: {
  label: string;
  color: string;
  metaText: string;
  ratio: number;
  selected?: boolean;
  onPress?: () => void;
  /**
   * The per-subject bar is redundant where the page already has one overall
   * proportion bar above the list (Review's trend view); the row then reads as
   * dot · name · time · share only.
   */
  showBar?: boolean;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const clamped = Math.max(0, Math.min(1, ratio));
  const content = (
    <View
      style={{
        paddingVertical: YanjiSpacing.sm + 2,
        paddingHorizontal: selected ? YanjiSpacing.sm : 0,
        borderRadius: paper.radius.tag,
        backgroundColor: selected ? paper.colors.brandSubtle : 'transparent',
      }}
    >
      <View
        style={{
          flexDirection: 'row',
          alignItems: 'center',
          gap: paper.layout.gridGap,
        }}
      >
        <View
          style={{
            width: 8,
            height: 8,
            borderRadius: paper.radius.pill,
            backgroundColor: color,
          }}
        />
        <Text
          numberOfLines={1}
          style={[paper.typography.bodyPrimary, { color: paper.colors.ink, flex: 1 }]}
        >
          {label}
        </Text>
        <Text
          style={[
            paper.typography.captionMeta,
            { color: selected ? paper.colors.brandInk : paper.colors.secondary, flexShrink: 0 },
          ]}
        >
          {metaText}
        </Text>
      </View>
      {showBar ? (
        <View
          style={{
            height: 4,
            borderRadius: paper.radius.pill,
            backgroundColor: paper.colors.subtle,
            overflow: 'hidden',
            marginTop: YanjiSpacing.xs + 2,
            marginLeft: 16,
          }}
        >
          <View
            style={{
              width: `${Math.round(clamped * 100)}%`,
              height: 4,
              borderRadius: paper.radius.pill,
              backgroundColor: color,
            }}
          />
        </View>
      ) : null}
    </View>
  );

  if (!onPress) {
    return (
      <View
        style={{
          borderBottomWidth: paper.layout.hairlineWidth,
          borderColor: paper.colors.hairline,
        }}
      >
        {content}
      </View>
    );
  }

  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityState={{ selected }}
      style={({ pressed }) => ({
        minHeight: paper.layout.rowMinHeight,
        justifyContent: 'center',
        borderBottomWidth: paper.layout.hairlineWidth,
        borderColor: paper.colors.hairline,
        opacity: pressed ? 0.68 : 1,
      })}
    >
      {content}
    </Pressable>
  );
}

// ---------------------------------------------------------------------------
// YanjiTaskLedgerRow — subject checkbox, task ledger & separate focus capsule
//
// The checkbox owns its touch column. Text wraps independently of the focus
// action, and subject identity stays in a small swatch beside readable metadata.
// ---------------------------------------------------------------------------

/**
 * The ledger's one chip shape: a subject tag and a duration/meta pill.
 *
 * A plan row used to mix two grammars in one line — a tinted subject tag plus a
 * bare grey sentence ("实际专注 52m") that happened to sit next to it. Both are
 * "a labeled value", so they now share one shape and differ only in tone.
 */
export function YanjiLedgerChip({
  label,
  tone = 'subject',
  color,
  completed = false,
}: {
  label: string;
  /** `subject` tints by the subject colour; `meta` stays neutral. */
  tone?: 'subject' | 'meta';
  /** Subject colour; required for the `subject` tone. */
  color?: string;
  /** A finished row flattens both chips to the neutral tier. */
  completed?: boolean;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, radius } = paper;
  const useSubject = tone === 'subject' && Boolean(color) && !completed;
  return (
    <View style={{
      flexShrink: 1,
      backgroundColor: useSubject ? toSubtleColor(color as string, 0.14) : colors.subtle,
      borderRadius: radius.tag,
      paddingHorizontal: 7,
      paddingVertical: 1.5,
    }}>
      <Text numberOfLines={1} style={{
        ...typography.captionMeta,
        color: useSubject ? (color as string) : colors.secondary,
        fontWeight: '600',
        flexShrink: 1,
      }}>{label}</Text>
    </View>
  );
}

/**
 * The plan's own time promise, for the row's left margin: `45m` / `1h30m`.
 *
 * `taskMeta` answers "what has happened here too" (投入/实际), which belongs on
 * the sub-line; the margin only has room for the bare commitment.
 */
export function planCommitment(meta: string): string {
  const planned = /预计\s*([^·]+)$/.exec(meta);
  const actual = /实际专注\s*([^·]+)$/.exec(meta);
  const invested = /已投入\s*([^·]+?)\s/.exec(meta);
  const value = planned?.[1] ?? actual?.[1] ?? invested?.[1];
  return (value ?? '').trim();
}

/**
 * The part of `taskMeta` that the margin does *not* already say.
 *
 * Used rows report how much actually happened ("实际专注 52m" / "已投入 12m");
 * a plan that has not been started has nothing to add, so the sub-line stays a
 * single subject chip rather than repeating the margin's number.
 */
export function planSubLineMeta(meta: string): string | null {
  const reported = /(实际专注|已投入)\s*([^·]+?)(?:\s+预计|$)/.exec(meta);
  return reported ? `${reported[1]} ${reported[2].trim()}` : null;
}

export function YanjiTaskLedgerRow({
  title,
  subjectName,
  meta,
  accentColor,
  completed,
  isLead = false,
  isLast = false,
  busy = false,
  disabled = false,
  onToggle,
  onEdit,
  onStartFocus,
  onLongPress,
}: {
  title: string;
  subjectName: string;
  meta: string;
  accentColor: string;
  completed: boolean;
  /** First pending task carries the prominent filled play button. */
  isLead?: boolean;
  /** The final row of a ledger drops its rule, so the list ends without a stray line. */
  isLast?: boolean;
  busy?: boolean;
  disabled?: boolean;
  onToggle: () => void;
  onEdit: () => void;
  onStartFocus: () => void;
  onLongPress?: () => void;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout, radius } = paper;
  // The left margin is the plan's time commitment, the same column the journal
  // uses for a note's clock: the day reads down one axis whether it is a task
  // being promised or an entry being recorded.
  const commitment = planCommitment(meta);
  const subLineMeta = planSubLineMeta(meta);
  return (
    <View>
      {/* `.row` in the design: 11dp between columns, 8dp above and below, and the
          commitment sits 3dp down so it optically centres on the title's first line. */}
      <View style={{
        flexDirection: 'row', alignItems: 'flex-start', gap: 11,
        // The final row drops its trailing padding the same way it drops its
        // rule: the ledger's bottom edge is then the section gap, not the gap
        // plus a row's internal air.
        paddingTop: layout.taskRowPadding,
        paddingBottom: isLast ? 0 : layout.taskRowPadding,
      }}>
        <View style={{ width: 40, flexShrink: 0, paddingTop: 3 }}>
          <Text numberOfLines={1} style={[typography.captionMeta, { color: colors.secondary }]}>
            {commitment}
          </Text>
        </View>
        <Pressable
          onPress={onToggle}
          disabled={disabled}
          accessibilityRole="checkbox"
          accessibilityState={{ checked: completed, disabled, busy }}
          accessibilityLabel={`${completed ? '恢复待办' : '完成任务'}：${title}`}
          hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
          style={({ pressed }) => ({
            // The box itself is the visible 18dp square; `hitSlop` supplies the
            // 44dp target, so the checkbox can sit flush with the title's left
            // edge instead of being floated inside a wider touch column.
            width: layout.taskCheckboxSize,
            minHeight: layout.taskCheckboxSize,
            marginTop: 2,
            opacity: pressed ? 0.6 : 1,
          })}
        >
          <View style={{
            width: layout.taskCheckboxSize,
            height: layout.taskCheckboxSize,
            borderWidth: completed ? 0 : 1.5,
            borderColor: completed ? colors.ink : colors.muted,
            backgroundColor: completed ? colors.ink : 'transparent',
            borderRadius: radius.tag,
            justifyContent: 'center',
            alignItems: 'center',
          }}>
            {completed ? <YanjiIcon name="check" size={layout.taskCheckIconSize} color={colors.canvas} /> : null}
          </View>
        </Pressable>
        <Pressable
          onPress={onEdit}
          onLongPress={onLongPress}
          delayLongPress={350}
          accessibilityRole="button"
          accessibilityLabel={`编辑任务：${title}`}
          style={({ pressed }) => ({
            flex: 1,
            minWidth: 0,
            opacity: pressed ? 0.65 : 1,
          })}
        >
          <Text
            numberOfLines={2}
            style={{
              ...typography.bodyTask,
              color: completed ? colors.secondary : colors.ink,
              textDecorationLine: completed ? 'line-through' : 'none',
            }}
          >{title}</Text>
          {/* The margin already states the plan's commitment, so the sub-line
              only adds what the margin cannot: the subject, and — once the row
              has real investment — how much of it has happened. */}
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: YanjiSpacing.xs + 2, marginTop: layout.taskSubMarginTop }}>
            <YanjiLedgerChip label={subjectName} tone="subject" color={accentColor} completed={completed} />
            {subLineMeta ? <YanjiLedgerChip label={subLineMeta} tone="meta" completed={completed} /> : null}
          </View>
        </Pressable>
        {!completed ? (
          /* A single circular play target, centred on the row. It is the same
             accent fill as the Focus page's start button — the solid brand with
             an on-brand glyph and no rim — so the two ways of starting a round
             read as one action. `isLead` only lightens the circle slightly: the
             row this page invites you to start keeps the strongest contrast,
             and the others recede without leaving the accent family. */
          <Pressable
            onPress={onStartFocus}
            accessibilityRole="button"
            accessibilityLabel={`开始专注：${title}`}
            hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
            style={({ pressed }) => ({
              alignSelf: 'center',
              width: layout.compactActionHeight + 2,
              height: layout.compactActionHeight + 2,
              borderRadius: (layout.compactActionHeight + 2) / 2,
              backgroundColor: colors.brand,
              alignItems: 'center',
              justifyContent: 'center',
              opacity: pressed ? 0.6 : isLead ? 1 : 0.72,
            })}
          >
            <YanjiIcon name="play" size={11} color={colors.onBrand} />
          </Pressable>
        ) : null}
      </View>
      {isLast ? null : <YanjiHairline />}
    </View>
  );
}

// ---------------------------------------------------------------------------
// YanjiPrimaryButton — the one accent action, with tactile press feedback
// ---------------------------------------------------------------------------

export function YanjiPrimaryButton({
  label,
  detail,
  icon,
  onPress,
  disabled = false,
  variant = 'primary',
  accessibilityLabel,
  style,
}: {
  label: string;
  /** Smaller trailing context aligned to the label's lower edge. */
  detail?: string;
  icon?: YanjiIconName;
  onPress: () => void;
  disabled?: boolean;
  /**
   * `primary` = the one accent action; `secondary` = a same-tier sibling;
   * `danger` = an irreversible confirmation. The three never share a fill, so
   * a destructive action cannot borrow the accent of "start" or "save".
   */
  variant?: 'primary' | 'secondary' | 'danger';
  accessibilityLabel?: string;
  style?: ViewStyle;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const paper = theme.paper;
  const reduceMotion = useReducedMotion();
  const secondary = variant === 'secondary';
  const danger = variant === 'danger';

  const backgroundColor = disabled
    ? paper.colors.subtle
    : danger
      ? paper.colors.danger
      : secondary
        ? paper.colors.subtle
        : paper.colors.brand;
  const textColor = disabled
    ? paper.colors.muted
    : danger
      ? paper.colors.onBrand
      : secondary
        ? paper.colors.ink
        : paper.colors.onBrand;

  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel ?? (detail ? `${label}，${detail}` : (label || '确认'))}
      style={({ pressed }) => [
        {
          backgroundColor,
          borderRadius: paper.radius.pill,
          borderWidth: secondary ? paper.layout.hairlineWidth : 0,
          borderColor: secondary ? paper.colors.hairline : 'transparent',
          paddingVertical: 15,
          paddingHorizontal: 24,
          flexDirection: 'row',
          flexWrap: detail ? 'wrap' : 'nowrap',
          columnGap: detail ? YanjiSpacing.sm : 0,
          rowGap: detail ? YanjiSpacing.xs : 0,
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: YanjiTouch.comfortable + 4,
          opacity: pressed ? 0.88 : 1,
          transform: [{ scale: pressed && !reduceMotion ? 0.985 : 1 }],
          ...(!disabled && !secondary && !danger ? theme.shadow.sheet(theme.isDark) : null),
        },
        style,
      ]}
    >
      {icon ? (
        <View style={{ marginRight: label ? (detail ? 0 : YanjiSpacing.sm) : 0 }}>
          <YanjiIcon name={icon} size={17} color={textColor} />
        </View>
      ) : null}
      {label ? (
        <Text
          style={{
            color: textColor,
            fontSize: 16,
            fontWeight: '600',
            fontVariant: ['tabular-nums'],
            maxWidth: detail ? '100%' : undefined,
          }}
        >
          {label}
        </Text>
      ) : null}
      {detail ? (
        <Text
          style={[
            paper.typography.captionMeta,
            {
              color: textColor,
              alignSelf: 'flex-end',
              maxWidth: '100%',
              marginBottom: -YanjiSpacing.xs / 2,
            },
          ]}
        >
          {detail}
        </Text>
      ) : null}
    </Pressable>
  );
}

// ---------------------------------------------------------------------------
// YanjiIconButton — icon-only control, guaranteed touch target
//
// The circle stays on `YanjiRadius.full`, the same step the frozen dock uses,
// so nav and body cannot end up with two different round geometries.
// ---------------------------------------------------------------------------

export function YanjiIconButton({
  icon,
  onPress,
  accessibilityLabel,
  size = YanjiTouch.comfortable,
  iconSize = 20,
  tone = 'plain',
  disabled = false,
  selected,
  style,
}: {
  icon: YanjiIconName;
  onPress: () => void;
  accessibilityLabel: string;
  size?: number;
  iconSize?: number;
  /** `plain` = bare glyph; `filled` = glyph inside a soft tinted disc; `danger` = destructive glyph. */
  tone?: 'plain' | 'filled' | 'danger';
  disabled?: boolean;
  /** Toggle buttons must say whether they are on — colour alone is not a state. */
  selected?: boolean;
  style?: ViewStyle;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const paper = theme.paper;
  const color = disabled
    ? paper.colors.muted
    : selected
      ? paper.colors.brandInk
      : tone === 'danger'
        ? paper.colors.danger
        : paper.colors.secondary;
  // Small glyphs expand their touch area to the 44dp floor. Larger controls
  // stay exact, so adjacent buttons cannot claim each other's taps.
  const hitSlop = Math.max(0, Math.ceil((YanjiTouch.min - size) / 2));

  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      accessibilityState={selected === undefined ? undefined : { selected }}
      hitSlop={hitSlop}
      style={({ pressed }) => [
        {
          width: size,
          height: size,
          borderRadius: YanjiRadius.full,
          alignItems: 'center',
          justifyContent: 'center',
          backgroundColor:
            selected
              ? paper.colors.brandSubtle
              : tone === 'filled'
                ? paper.colors.subtle
                : 'transparent',
          opacity: pressed ? 0.6 : 1,
        },
        style,
      ]}
    >
      <YanjiIcon name={icon} size={iconSize} color={color} />
    </Pressable>
  );
}

// ---------------------------------------------------------------------------
// YanjiStepper — +/- quantity control
// ---------------------------------------------------------------------------

export function YanjiStepper({
  value,
  onChange,
  min = 1,
  max = 999,
  step = 1,
  suffix,
  label,
  alignment = 'start',
}: {
  value: number;
  onChange: (next: number) => void;
  min?: number;
  max?: number;
  step?: number;
  suffix?: string;
  label: string;
  /** Keep form steppers left-aligned; the focus timer opts into the central axis. */
  alignment?: 'start' | 'center';
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const paper = theme.paper;
  const canDecrease = value - step >= min;
  const canIncrease = value + step <= max;

  return (
    <View
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        alignSelf: alignment === 'center' ? 'center' : 'flex-start',
        backgroundColor: paper.colors.subtle,
        borderRadius: paper.radius.pill,
        padding: 4,
      }}
      accessibilityRole="adjustable"
      accessibilityLabel={label}
      accessibilityValue={{ text: `${value}${suffix ?? ''}` }}
    >
      <YanjiIconButton
        icon="remove"
        accessibilityLabel={`减少${label}`}
        onPress={() => canDecrease && onChange(value - step)}
        disabled={!canDecrease}
        size={paper.layout.rowMinHeight}
        iconSize={16}
      />
      <Text
        style={{
          minWidth: 56,
          textAlign: 'center',
          color: paper.colors.ink,
          fontSize: 16,
          fontWeight: '600',
          fontVariant: ['tabular-nums'],
        }}
      >
        {value}
        {suffix ?? ''}
      </Text>
      <YanjiIconButton
        icon="add"
        accessibilityLabel={`增加${label}`}
        onPress={() => canIncrease && onChange(value + step)}
        disabled={!canIncrease}
        size={paper.layout.rowMinHeight}
        iconSize={16}
      />
    </View>
  );
}

// ---------------------------------------------------------------------------
// YanjiStackedBarChart — SVG column chart stacked by subject, honest about
// empty data and about days that have not happened yet
// ---------------------------------------------------------------------------

interface YanjiStackedBarChartColumn {
  totalMinutes: number;
  segments: ReadonlyArray<{ color: string; minutes: number }>;
}

export function YanjiStackedBarChart({
  columns,
  width = 240,
  height = 56,
  gap = 4,
  averageMinutes,
  averageLabel,
  emptyLabel,
}: {
  columns: ReadonlyArray<YanjiStackedBarChartColumn>;
  width?: number;
  height?: number;
  gap?: number;
  /** Dashed reference line, in the same minutes scale as the columns. */
  averageMinutes?: number;
  averageLabel?: string;
  emptyLabel?: string;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const max = Math.max(1, ...columns.map(column => column.totalMinutes));

  if (columns.length === 0) {
    return (
      <View style={{ width, height, justifyContent: 'flex-end' }}>
        <YanjiHairline style={{ marginBottom: 0 }} />
        {emptyLabel ? (
          <Text
            style={{
              marginTop: 8,
              // `secondary`, not `muted`: this is a sentence the user reads, and
              // the muted tier is by design below the 4.5:1 readable bar.
              color: paper.colors.secondary,
              ...paper.typography.captionMeta,
              textAlign: 'center',
            }}
          >
            {emptyLabel}
          </Text>
        ) : null}
      </View>
    );
  }

  const barWidth = Math.max(2, (width - gap * (columns.length - 1)) / columns.length);
  const scale = height / max;
  const averageY =
    averageMinutes !== undefined && averageMinutes > 0
      ? Math.max(0, Math.min(height, height - averageMinutes * scale))
      : null;

  return (
    <View style={{ width, height }}>
      <Svg width={width} height={height}>
        {columns.map((column, index) => {
          const x = index * (barWidth + gap);
          // A zero day keeps a visible slot so the axis never lies about which
          // day is which — but it must not read as "a little work happened".
          if (column.totalMinutes <= 0) {
            return (
              <Rect
                key={index}
                x={x}
                y={height - 2}
                width={barWidth}
                height={2}
                rx={Math.min(3, barWidth / 2)}
                fill={paper.colors.subtle}
              />
            );
          }
          const barHeight = column.totalMinutes * scale;
          // One rounded clip per column: segment boundaries stay crisp while
          // the column silhouette keeps the same soft top as a single bar.
          const clipId = `yanji-trend-col-${index}`;
          let cursor = height;
          return (
            <G key={index}>
              <Defs>
                <ClipPath id={clipId}>
                  <Rect
                    x={x}
                    y={height - barHeight}
                    width={barWidth}
                    height={barHeight}
                    rx={Math.min(3, barWidth / 2)}
                  />
                </ClipPath>
              </Defs>
              <G clipPath={`url(#${clipId})`}>
                {column.segments.map((segment, segmentIndex) => {
                  const segmentHeight = segment.minutes * scale;
                  cursor -= segmentHeight;
                  return (
                    <Rect
                      key={segmentIndex}
                      x={x}
                      y={cursor}
                      width={barWidth}
                      height={segmentHeight}
                      fill={segment.color}
                    />
                  );
                })}
              </G>
            </G>
          );
        })}
        {averageY !== null ? (
          <Line
            x1={0}
            y1={averageY}
            x2={width}
            y2={averageY}
            stroke={paper.colors.muted}
            strokeWidth={1}
            strokeDasharray="3 3"
          />
        ) : null}
      </Svg>
      {averageY !== null && averageLabel ? (
        <Text
          style={{
            position: 'absolute',
            right: 0,
            top: Math.max(0, averageY - 15),
            paddingHorizontal: 3,
            backgroundColor: paper.colors.surface,
            color: paper.colors.muted,
            ...paper.typography.captionMeta,
          }}
        >
          {averageLabel}
        </Text>
      ) : null}
    </View>
  );
}

// ---------------------------------------------------------------------------
// YanjiLineChart — smoothed line with a soft area, for windows where one column
// per point would turn into noise (a whole month, or one day's 24 hours)
// ---------------------------------------------------------------------------

export function YanjiLineChart({
  values,
  width = 240,
  height = 56,
  averageValue,
  averageLabel,
  peakIndex,
  emptyLabel,
}: {
  values: ReadonlyArray<number>;
  width?: number;
  height?: number;
  averageValue?: number;
  averageLabel?: string;
  /** Point to mark with a dot (the busiest day / hour). */
  peakIndex?: number;
  emptyLabel?: string;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const max = Math.max(0, ...values);

  if (values.length === 0 || max <= 0) {
    return (
      <View style={{ width, height, justifyContent: 'flex-end' }}>
        <YanjiHairline style={{ marginBottom: 0 }} />
        {emptyLabel ? (
          <Text
            style={{
              marginTop: 8,
              // See the bar chart's note: readable copy uses `secondary`.
              color: paper.colors.secondary,
              ...paper.typography.captionMeta,
              textAlign: 'center',
            }}
          >
            {emptyLabel}
          </Text>
        ) : null}
      </View>
    );
  }

  // Monotone cubic segments pass through every genuine data point. The old
  // midpoint-quadratic used points as *controls*, so its visible curve missed
  // the marked peak; the highest point was also clipped at y=0.
  const chart = buildLineChartGeometry(values, width, height, YanjiSpacing.sm, YanjiSpacing.xl);
  const averageY =
    averageValue !== undefined && averageValue > 0
      ? Math.max(chart.plotTop, Math.min(chart.plotBottom, chart.plotBottom - averageValue * chart.scale))
      : null;
  const peak = peakIndex !== undefined ? chart.points[peakIndex] : undefined;

  return (
    <View style={{ width, height }}>
      <Svg width={width} height={height}>
        <Path d={chart.areaPath} fill={paper.colors.brand} fillOpacity={0.14} />
        <Path
          d={chart.linePath}
          stroke={paper.colors.brand}
          strokeWidth={2}
          strokeLinecap="round"
          fill="none"
        />
        {averageY !== null ? (
          <Line
            x1={chart.plotLeft}
            y1={averageY}
            x2={chart.plotRight}
            y2={averageY}
            stroke={paper.colors.muted}
            strokeWidth={1}
            strokeDasharray="3 3"
          />
        ) : null}
        {peak ? (
          <Circle
            cx={peak.x}
            cy={peak.y}
            r={3.5}
            fill={paper.colors.brand}
            stroke={paper.colors.surface}
            strokeWidth={2}
          />
        ) : null}
      </Svg>
      {averageY !== null && averageLabel ? (
        <Text
          style={{
            position: 'absolute',
            right: chart.plotLeft,
            top: Math.max(0, averageY - 15),
            paddingHorizontal: 3,
            backgroundColor: paper.colors.surface,
            color: paper.colors.muted,
            ...paper.typography.captionMeta,
          }}
        >
          {averageLabel}
        </Text>
      ) : null}
    </View>
  );
}

// ---------------------------------------------------------------------------
// YanjiBreathButton — the one primary call to action
// ---------------------------------------------------------------------------

/**
 * Pressable with a single, finite "breath" on mount and a spring press-down.
 *
 * Deliberately NOT a looping animation: the paper domain rules out sustained
 * motion, and a CTA that never stops moving competes with the content it points
 * at.
 * Reduced motion skips both the breath and the press spring.
 */
export function YanjiBreathButton({
  label,
  icon,
  onPress,
  disabled = false,
  style,
}: {
  label: string;
  icon?: YanjiIconName;
  onPress: () => void;
  disabled?: boolean;
  style?: ViewStyle;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const paper = theme.paper;
  const scale = useSharedValue(1);
  const reduceMotion = useReducedMotion();

  useEffect(() => {
    if (disabled || reduceMotion) { scale.value = 1; return; }
    // One gentle settle: 1 -> 1.025 -> 1.
    scale.value = withSequence(
      withTiming(1.025, { duration: theme.motion.duration.slow, easing: Easing.out(Easing.quad) }),
      withSpring(1, theme.motion.spring.gentle),
    );
    // `theme` is intentionally omitted: this must fire once per mount, not on
    // every theme toggle.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [disabled, reduceMotion]);

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
  }));

  return (
    <Animated.View style={[animatedStyle, style]}>
      <Pressable
        onPress={onPress}
        disabled={disabled}
        accessibilityRole="button"
        accessibilityLabel={label}
        onPressIn={() => {
          if (!reduceMotion) scale.value = withSpring(0.97, theme.motion.spring.press);
        }}
        onPressOut={() => {
          if (!reduceMotion) scale.value = withSpring(1, theme.motion.spring.press);
        }}
        style={({ pressed }) => ({
          backgroundColor: disabled ? paper.colors.subtle : paper.colors.brand,
          borderRadius: paper.radius.pill,
          paddingVertical: 16,
          paddingHorizontal: 26,
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: YanjiTouch.comfortable + 6,
          opacity: disabled ? 1 : pressed ? 0.92 : 1,
          ...(disabled ? null : theme.shadow.sheet(theme.isDark)),
        })}
      >
        {icon ? (
          <View style={{ marginRight: 9 }}>
            <YanjiIcon
              name={icon}
              size={19}
              color={disabled ? paper.colors.muted : paper.colors.onBrand}
            />
          </View>
        ) : null}
        <Text
          style={{
            color: disabled ? paper.colors.muted : paper.colors.onBrand,
            fontSize: 16,
            fontWeight: '700',
            fontVariant: ['tabular-nums'],
          }}
        >
          {label}
        </Text>
      </Pressable>
    </Animated.View>
  );
}

// ---------------------------------------------------------------------------
// YanjiChip — selectable pill used for subjects, modes and duration presets
// ---------------------------------------------------------------------------

export function YanjiChip({
  label,
  selected = false,
  onPress,
  style,
}: {
  label: string;
  selected?: boolean;
  onPress: () => void;
  style?: ViewStyle;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const paper = theme.paper;
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityState={{ selected }}
      style={({ pressed }) => [
        {
          paddingVertical: YanjiSpacing.sm,
          paddingHorizontal: 16,
          minHeight: YanjiTouch.comfortable,
          justifyContent: 'center',
          borderRadius: paper.radius.pill,
          // Selected chips stay soft-tinted rather than filled: a wall of solid
          // accent pills reads as "many primary actions", and a screen has only
          // one (its start / save button).
          backgroundColor: selected ? paper.colors.brandSubtle : paper.colors.surface,
          borderWidth: paper.layout.hairlineWidth,
          borderColor: selected ? paper.colors.brand : paper.colors.hairline,
          opacity: pressed ? 0.72 : 1,
        },
        style,
      ]}
    >
      <Text
        numberOfLines={2}
        style={[
          paper.typography.bodyPrimary,
          {
            color: selected ? paper.colors.brandInk : paper.colors.ink,
            fontWeight: selected ? '700' : '500',
            flexShrink: 1,
          },
        ]}
      >
        {label}
      </Text>
    </Pressable>
  );
}

// ---------------------------------------------------------------------------
// YanjiTextInput — a well pressed into a sheet
// ---------------------------------------------------------------------------

export function YanjiTextInput({
  value,
  onChangeText,
  placeholder,
  multiline = false,
  secureTextEntry = false,
  autoCapitalize = 'sentences',
  accessibilityLabel,
  editable = true,
  variant = 'well',
  style,
}: {
  value: string;
  onChangeText: (next: string) => void;
  placeholder: string;
  multiline?: boolean;
  secureTextEntry?: boolean;
  autoCapitalize?: 'none' | 'sentences' | 'words' | 'characters';
  accessibilityLabel?: string;
  editable?: boolean;
  /**
   * `well` sinks the field into the sheet as a tinted box. `page` writes
   * directly on the paper with no box, for a sheet whose whole body is the
   * field (记录此刻).
   */
  variant?: 'well' | 'page';
  style?: ViewStyle;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const paper = theme.paper;
  const page = variant === 'page';
  // A multiline field in this product is always a journal capture (记录此刻 /
  // 专注随笔), so it takes the serif journal face rather than the sans body.
  // The capture page gets the larger writing cut; a boxed field keeps the
  // denser journal body so a long note still fits inside its well.
  const type = multiline
    ? (page ? paper.typography.bodyWriting : paper.typography.bodyJournal)
    : paper.typography.bodyPrimary;
  return (
    <TextInput
      value={value}
      onChangeText={onChangeText}
      placeholder={placeholder}
      placeholderTextColor={paper.colors.secondary}
      multiline={multiline}
      secureTextEntry={secureTextEntry}
      autoCapitalize={autoCapitalize}
      accessibilityLabel={accessibilityLabel ?? placeholder}
      editable={editable}
      style={[
        ({
          backgroundColor: page ? 'transparent' : paper.colors.subtle,
          ...(page ? { padding: 0 } : {
            borderRadius: paper.radius.surface,
            borderWidth: paper.layout.hairlineWidth,
            borderColor: paper.colors.hairline,
            padding: YanjiSpacing.md,
          }),
          color: paper.colors.ink,
          fontSize: type.fontSize,
          lineHeight: type.lineHeight,
          ...('fontFamily' in type ? { fontFamily: type.fontFamily } : {}),
          textAlignVertical: multiline ? 'top' : 'center',
        } as TextStyle),
        style,
      ]}
    />
  );
}
