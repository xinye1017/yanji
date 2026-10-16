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
import { YanjiRadius, YanjiSpacing, YanjiTouch } from '../theme/tokens';
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
}: {
  name: YanjiIconName;
  size?: number;
  color?: string;
  strokeWidth?: number;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const Glyph = getYanjiIcon(name);
  return (
    <Glyph
      size={size}
      color={color ?? paper.colors.ink}
      strokeWidth={strokeWidth}
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
}: {
  title: string;
  subtitle?: string;
  onClose: () => void;
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
          style={[paper.typography.titleEditorial, { color: paper.colors.ink }]}
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
      <YanjiIconButton
        icon="close"
        accessibilityLabel="关闭"
        onPress={onClose}
        size={paper.layout.rowMinHeight}
        iconSize={17}
        tone="filled"
      />
    </View>
  );
}

// ---------------------------------------------------------------------------
// YanjiEditorialHeader — the page's opening line
// ---------------------------------------------------------------------------

export interface YanjiEditorialHeaderProps {
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
}: {
  title: string;
  countText?: string;
  right?: React.ReactNode;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const paper = theme.paper;
  return (
    <View
      style={{
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'baseline',
        flexWrap: 'wrap',
        gap: YanjiSpacing.sm,
        marginBottom: 4,
        marginTop: 16,
      }}
    >
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', alignItems: 'baseline', gap: YanjiSpacing.xs }}>
        <Text
          accessibilityRole="header"
          style={[paper.typography.titleSection, { color: paper.colors.ink }]}
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
}

export function YanjiPaperSegmentedControl<T extends string>({
  value,
  options,
  onChange,
  accessibilityLabel,
  compact = false,
}: {
  value: T;
  options: readonly YanjiPaperSegmentedOption<T>[];
  onChange: (value: T) => void;
  accessibilityLabel: string;
  compact?: boolean;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const paper = theme.paper;
  const reduceMotion = useReducedMotion();
  // Inset the compact paint inside full-height radios so the touch area stays 48dp.
  const segmentInset = compact
    ? (paper.layout.rowMinHeight - paper.layout.compactSegmentHeight) / 2
    : 0;

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
        borderWidth: compact ? 0 : paper.layout.hairlineWidth,
        borderColor: paper.colors.hairline,
        paddingVertical: compact ? 0 : YanjiSpacing.xs,
        paddingHorizontal: YanjiSpacing.xs + (compact ? paper.layout.hairlineWidth : 0),
      }}
    >
      {compact ? (
        <View
          pointerEvents="none"
          style={{
            position: 'absolute',
            left: 0,
            right: 0,
            top: segmentInset - YanjiSpacing.xs - paper.layout.hairlineWidth,
            bottom: segmentInset - YanjiSpacing.xs - paper.layout.hairlineWidth,
            backgroundColor: paper.colors.subtle,
            borderRadius: paper.radius.pill,
            borderWidth: paper.layout.hairlineWidth,
            borderColor: paper.colors.hairline,
          }}
        />
      ) : null}
      {options.map(option => (
        <PaperSegment
          key={option.value}
          label={option.label}
          selected={option.value === value}
          compact={compact}
          segmentInset={segmentInset}
          reduceMotion={reduceMotion}
          onPress={() => onChange(option.value)}
        />
      ))}
    </View>
  );
}

function PaperSegment({
  label,
  selected,
  compact,
  segmentInset,
  reduceMotion,
  onPress,
}: {
  label: string;
  selected: boolean;
  compact: boolean;
  segmentInset: number;
  reduceMotion: boolean;
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
      accessibilityRole="radio"
      accessibilityState={{ selected }}
      style={({ pressed }) => ({
        minHeight: compact ? paper.layout.rowMinHeight : paper.layout.rowMinHeight - YanjiSpacing.xs,
        minWidth: paper.layout.rowMinHeight,
        paddingHorizontal: compact ? YanjiSpacing.md : YanjiSpacing.lg,
        paddingVertical: segmentInset,
        alignItems: 'center',
        justifyContent: 'center',
        borderRadius: paper.radius.pill,
        // Press feedback is an instant state change, not an animation, so
        // reduced motion keeps it — only the fill crossfade above is gated.
        opacity: pressed ? 0.7 : 1,
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
            borderWidth: paper.layout.hairlineWidth,
            borderColor: paper.colors.hairline,
          },
          fill,
        ]}
      />
      <Text
        style={[
          compact ? paper.typography.captionMeta : paper.typography.bodyPrimary,
          {
            color: selected ? paper.colors.ink : paper.colors.secondary,
            fontWeight: selected ? '700' : '500',
          },
        ]}
      >
        {label}
      </Text>
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

export interface YanjiChronoSpectrumProps {
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
// YanjiMiniRhythmSparkline — saved continuous focus, 06:00–24:00
// ---------------------------------------------------------------------------

export interface YanjiMiniRhythmBucket {
  hour: number;
  seconds: number;
  color?: string;
}

export function YanjiMiniRhythmSparkline({
  buckets,
  currentHour,
}: {
  buckets: readonly YanjiMiniRhythmBucket[];
  currentHour: number;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, layout, radius } = paper;
  const maximum = Math.max(1800, ...buckets.map(bucket => bucket.seconds));
  return (
    <View
      importantForAccessibility="no-hide-descendants"
      style={{ flexDirection: 'row', alignItems: 'flex-end', gap: layout.rhythmBarGap, height: layout.rhythmBarHeight }}
    >
      {buckets.map(bucket => (
        <View
          key={bucket.hour}
          style={{
            width: layout.rhythmBarWidth,
            height: bucket.seconds > 0
              ? Math.max(layout.rhythmEmptyHeight, bucket.seconds / maximum * layout.rhythmBarHeight)
              : layout.rhythmEmptyHeight,
            borderRadius: radius.tick,
            backgroundColor: bucket.seconds > 0 ? bucket.color ?? colors.secondary
              : bucket.hour === currentHour ? colors.secondary : colors.subtle,
          }}
        />
      ))}
    </View>
  );
}

// ---------------------------------------------------------------------------
// YanjiTextAction — compact label inside a full-size touch target
// ---------------------------------------------------------------------------

export function YanjiTextAction({ label, accessibilityLabel, onPress, disabled = false }: {
  label: string;
  accessibilityLabel?: string;
  onPress: () => void;
  disabled?: boolean;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel ?? label}
      accessibilityState={{ disabled }}
      style={({ pressed }) => ({
        minHeight: paper.layout.rowMinHeight,
        minWidth: paper.layout.rowMinHeight,
        justifyContent: 'center',
        opacity: pressed ? 0.65 : 1,
      })}
    >
      <Text style={[paper.typography.captionMeta, { color: paper.colors.brandInk, fontWeight: '700' }]}>
        {label}
      </Text>
    </Pressable>
  );
}

// ---------------------------------------------------------------------------
// YanjiEmptyLedgerRow — a pressable invitation on the paper
// ---------------------------------------------------------------------------

export function YanjiEmptyLedgerRow({
  prompt,
  onPress,
}: {
  prompt: string;
  onPress: () => void;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout } = paper;
  return (
    <View>
      <Pressable
        onPress={onPress}
        accessibilityRole="button"
        accessibilityLabel={prompt}
        style={({ pressed }) => ({
          minHeight: layout.rowMinHeight,
          justifyContent: 'center',
          paddingVertical: YanjiSpacing.sm,
          opacity: pressed ? 0.72 : 1,
        })}
      >
        <Text style={[typography.bodyJournal, { color: colors.secondary }]}>{prompt}</Text>
      </Pressable>
      <YanjiHairline />
    </View>
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
  tone?: 'brand' | 'neutral' | 'amber';
  style?: ViewStyle;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const paper = theme.paper;
  const bg =
    tone === 'brand'
      ? paper.colors.brandSubtle
      : tone === 'amber'
        ? paper.colors.amberSubtle
        : paper.colors.subtle;
  const fg =
    tone === 'brand'
      ? paper.colors.brandInk
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
// YanjiStatCell — compact typographic metric block for summaries
// ---------------------------------------------------------------------------

export function YanjiStatCell({
  label,
  value,
}: {
  label: string;
  value: string;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  return (
    <View style={{ flexGrow: 1, flexBasis: 84 }}>
      <Text style={[paper.typography.captionMeta, { color: paper.colors.secondary }]}>
        {label}
      </Text>
      <Text
        style={[
          paper.typography.bodyPrimary,
          {
            color: paper.colors.ink,
            fontWeight: '700',
            fontVariant: ['tabular-nums'],
            marginTop: YanjiSpacing.xs,
          },
        ]}
      >
        {value}
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
}: {
  label: string;
  color: string;
  metaText: string;
  ratio: number;
  selected?: boolean;
  onPress?: () => void;
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
// AB-01's editorial row: the checkbox, title, tag and focus capsule all sit on
// one baseline grid separated by 11dp, and the rule below closes the row rather
// than boxing it. `isLast` drops that rule so the ledger ends on the paper
// instead of on a stray line.
// ---------------------------------------------------------------------------

/** Blend a subject colour into a translucent wash without leaving the paper stack. */
function subjectTagTint(accentColor: string, opacity: number): string {
  const hex = accentColor.replace('#', '');
  if (!/^[0-9a-fA-F]{6}$/.test(hex)) return 'transparent';
  return `#${hex}${Math.round(opacity * 255).toString(16).padStart(2, '0').toUpperCase()}`;
}

export function YanjiTaskLedgerRow({
  title,
  subjectName,
  meta,
  accentColor,
  completed,
  isLast = false,
  busy = false,
  disabled = false,
  onToggle,
  onEdit,
  onStartFocus,
}: {
  title: string;
  subjectName: string;
  meta: string;
  accentColor: string;
  completed: boolean;
  /** The final row of a ledger drops its rule, so the list ends without a stray line. */
  isLast?: boolean;
  busy?: boolean;
  disabled?: boolean;
  onToggle: () => void;
  onEdit: () => void;
  onStartFocus: () => void;
}): React.JSX.Element {
  const { paper } = useYanjiTheme();
  const { colors, typography, layout, radius } = paper;
  return (
    <View>
      <View style={{ flexDirection: 'row', alignItems: 'center', gap: layout.taskRowGap, paddingVertical: 2 }}>
        <Pressable
          onPress={onToggle}
          disabled={disabled}
          accessibilityRole="checkbox"
          accessibilityState={{ checked: completed, disabled, busy }}
          accessibilityLabel={`${completed ? '恢复待办' : '完成任务'}：${title}`}
          hitSlop={8}
          style={({ pressed }) => ({
            // AB-01's box is 18dp, but the touch target stays the full 48dp row
            // height; `hitSlop` widens it horizontally so the 11dp gap to the
            // title is not spent on padding.
            width: layout.taskCheckboxSize,
            minHeight: layout.rowMinHeight,
            justifyContent: 'center',
            opacity: pressed ? 0.6 : 1,
          })}
        >
          <View style={{
            width: layout.taskCheckboxSize,
            height: layout.taskCheckboxSize,
            borderWidth: completed ? 0 : layout.taskCheckboxStroke,
            borderColor: completed ? colors.ink : accentColor,
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
          accessibilityRole="button"
          accessibilityLabel={`编辑任务：${title}`}
          style={({ pressed }) => ({
            flex: 1,
            minHeight: layout.rowMinHeight,
            justifyContent: 'center',
            opacity: pressed ? 0.65 : 1,
          })}
        >
          <Text
            numberOfLines={1}
            style={{
              fontSize: 16,
              lineHeight: 22,
              fontWeight: '600',
              // AB-01 greys a finished title into the muted tier, but this
              // project's contrast policy bars `muted` from carrying a task
              // title at all — struck-through included. `secondary` is the
              // lowest tier a title may sit in, so the strike carries the
              // "done" signal instead.
              color: completed ? colors.secondary : colors.ink,
              textDecorationLine: completed ? 'line-through' : 'none',
            }}
          >{title}</Text>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', alignItems: 'center', gap: YanjiSpacing.sm, marginTop: layout.taskSubMarginTop }}>
            <View style={{
              flexShrink: 1,
              alignItems: 'center',
              justifyContent: 'center',
              backgroundColor: subjectTagTint(accentColor, layout.subjectTagTintOpacity),
              borderRadius: YanjiRadius.xs,
              paddingHorizontal: 6,
              paddingVertical: 1,
            }}>
              <Text numberOfLines={1} style={{ fontSize: 10, lineHeight: 14, fontWeight: '600', color: colors.secondary, flexShrink: 1 }}>{subjectName}</Text>
            </View>
            <Text numberOfLines={1} style={[typography.captionMeta, { color: colors.secondary, flexShrink: 1 }]}>{meta}</Text>
          </View>
        </Pressable>
        {!completed ? (
          <Pressable
            onPress={onStartFocus}
            accessibilityRole="button"
            accessibilityLabel={`为${title}设置专注`}
            hitSlop={8}
            style={({ pressed }) => ({
              minHeight: layout.rowMinHeight,
              justifyContent: 'center',
              opacity: pressed ? 0.68 : 1,
            })}
          >
            <View style={{
              minHeight: layout.compactActionHeight,
              paddingHorizontal: layout.compactActionPadding,
              borderRadius: radius.pill,
              backgroundColor: colors.subtle,
              flexDirection: 'row',
              alignItems: 'center',
              justifyContent: 'center',
              gap: YanjiSpacing.xs,
            }}>
              <YanjiIcon name="play" size={typography.captionMeta.fontSize - 1} color={colors.ink} />
              <Text style={[typography.captionMeta, { color: colors.ink, fontWeight: '600' }]}>专注</Text>
            </View>
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
  icon,
  onPress,
  disabled = false,
  variant = 'primary',
  style,
}: {
  label: string;
  icon?: YanjiIconName;
  onPress: () => void;
  disabled?: boolean;
  /** `primary` = the one accent action; `secondary` = a same-tier sibling. */
  variant?: 'primary' | 'secondary';
  style?: ViewStyle;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const paper = theme.paper;
  const reduceMotion = useReducedMotion();
  const secondary = variant === 'secondary';

  const backgroundColor = disabled || secondary ? paper.colors.subtle : paper.colors.brand;
  const textColor = disabled
    ? paper.colors.muted
    : secondary
      ? paper.colors.ink
      : paper.colors.onBrand;

  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={label}
      style={({ pressed }) => [
        {
          backgroundColor,
          borderRadius: paper.radius.pill,
          borderWidth: secondary ? paper.layout.hairlineWidth : 0,
          borderColor: secondary ? paper.colors.hairline : 'transparent',
          paddingVertical: 15,
          paddingHorizontal: 24,
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: YanjiTouch.comfortable + 4,
          opacity: pressed ? 0.88 : 1,
          transform: [{ scale: pressed && !reduceMotion ? 0.985 : 1 }],
          ...(!disabled && !secondary ? theme.shadow.sheet(theme.isDark) : null),
        },
        style,
      ]}
    >
      {icon ? (
        <View style={{ marginRight: YanjiSpacing.sm }}>
          <YanjiIcon name={icon} size={17} color={textColor} />
        </View>
      ) : null}
      <Text
        style={{
          color: textColor,
          fontSize: 16,
          fontWeight: '600',
          fontVariant: ['tabular-nums'],
        }}
      >
        {label}
      </Text>
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
  /** `plain` = bare glyph; `filled` = glyph inside a soft tinted disc. */
  tone?: 'plain' | 'filled';
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
      : paper.colors.secondary;

  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={accessibilityLabel}
      accessibilityState={selected === undefined ? undefined : { selected }}
      hitSlop={8}
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

export interface YanjiStackedBarChartColumn {
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
 * Deliberately NOT a looping animation: DESIGN.md rules out sustained motion,
 * and a CTA that never stops moving competes with the content it points at.
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
// YanjiEmptyState — quiet, factual empty state
// ---------------------------------------------------------------------------

export function YanjiEmptyState({
  title,
  hint,
  icon = 'sprout',
  compact = false,
}: {
  title: string;
  hint?: string;
  icon?: YanjiIconName;
  compact?: boolean;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const paper = theme.paper;
  return (
    <View
      style={{
        paddingVertical: compact ? YanjiSpacing.md : YanjiSpacing.xxl + YanjiSpacing.xs,
        paddingHorizontal: YanjiSpacing.lg,
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <View
        style={{
          width: compact ? 36 : 48,
          height: compact ? 36 : 48,
          borderRadius: paper.radius.pill,
          backgroundColor: paper.colors.subtle,
          alignItems: 'center',
          justifyContent: 'center',
          marginBottom: compact ? YanjiSpacing.xs : YanjiSpacing.md,
        }}
      >
        <YanjiIcon name={icon} size={compact ? 18 : 22} color={paper.colors.secondary} />
      </View>
      <Text
        style={[
          compact ? paper.typography.captionMeta : paper.typography.bodyPrimary,
          { color: paper.colors.ink, textAlign: 'center' },
        ]}
      >
        {title}
      </Text>
      {hint ? (
        <Text
          style={[
            paper.typography.captionMeta,
            {
              color: paper.colors.secondary,
              marginTop: compact ? 2 : YanjiSpacing.xs,
              textAlign: 'center',
            },
          ]}
        >
          {hint}
        </Text>
      ) : null}
    </View>
  );
}

// ---------------------------------------------------------------------------
// YanjiSectionHeader — legacy grouping header, kept for the existing screens
// ---------------------------------------------------------------------------

export function YanjiSectionHeader({
  title,
  rightAction,
}: {
  title: string;
  rightAction?: React.ReactNode;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const paper = theme.paper;
  return (
    <View
      style={{
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        flexWrap: 'wrap',
        gap: YanjiSpacing.sm,
        marginBottom: YanjiSpacing.sm,
        marginTop: paper.layout.sectionGap,
      }}
    >
      <Text
        accessibilityRole="header"
        style={[paper.typography.titleSection, { color: paper.colors.ink }]}
      >
        {title}
      </Text>
      {rightAction}
    </View>
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
  accessibilityLabel,
  style,
}: {
  value: string;
  onChangeText: (next: string) => void;
  placeholder: string;
  multiline?: boolean;
  accessibilityLabel?: string;
  style?: ViewStyle;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const paper = theme.paper;
  // A multiline field in this product is always a journal capture (记录此刻 /
  // 专注随笔), so it takes the serif journal face rather than the sans body.
  const type = multiline ? paper.typography.bodyJournal : paper.typography.bodyPrimary;
  return (
    <TextInput
      value={value}
      onChangeText={onChangeText}
      placeholder={placeholder}
      placeholderTextColor={paper.colors.secondary}
      multiline={multiline}
      accessibilityLabel={accessibilityLabel ?? placeholder}
      style={[
        {
          backgroundColor: paper.colors.subtle,
          borderRadius: paper.radius.surface,
          borderWidth: paper.layout.hairlineWidth,
          borderColor: paper.colors.hairline,
          padding: YanjiSpacing.md,
          color: paper.colors.ink,
          fontSize: type.fontSize,
          lineHeight: type.lineHeight,
          ...('fontFamily' in type ? { fontFamily: type.fontFamily } : {}),
          textAlignVertical: multiline ? 'top' : 'center',
        },
        style,
      ]}
    />
  );
}
