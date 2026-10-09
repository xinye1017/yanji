/**
 * Yanji shared UI components — React Native.
 *
 * Visual language: 「纸上研迹」. Surfaces are sheets of paper on a desk, so
 * depth is expressed by a five-step surface ladder plus a soft cast shadow —
 * never by an outline. AGENTS.md §三.6 requires card containers to have ZERO
 * borders in both light and dark modes, and `YanjiCardRules` is machine-asserted
 * on that; `YanjiHairline` is the only place a 1px line appears, and it divides
 * rows inside a sheet rather than outlining one.
 *
 * Every glyph in this file comes from `./icons` (lucide vector icons). No Emoji.
 */

import React, { useEffect } from 'react';
import {
  Pressable,
  Text,
  TextInput,
  View,
  type ViewStyle,
} from 'react-native';
import Animated, {
  Easing,
  useAnimatedStyle,
  useSharedValue,
  withSequence,
  withSpring,
  withTiming,
} from 'react-native-reanimated';
import Svg, { Circle, Rect } from 'react-native-svg';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiRadius, YanjiSpacing, YanjiTouch } from '../theme/tokens';
import { YANJI_ICON_STROKE, getYanjiIcon } from './icons';
import type { YanjiIconName } from './icons';

// ---------------------------------------------------------------------------
// YanjiIcon — the only way a glyph enters the tree
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
  const theme = useYanjiTheme();
  const Glyph = getYanjiIcon(name);
  return (
    <Glyph
      size={size}
      color={color ?? theme.colors.textPrimary}
      strokeWidth={strokeWidth}
    />
  );
}

// ---------------------------------------------------------------------------
// YanjiCard — zero-border paper surface
// ---------------------------------------------------------------------------

export function YanjiCard({
  children,
  style,
  variant = 'default',
}: {
  children: React.ReactNode;
  style?: ViewStyle;
  /**
   * `default`  a sheet lying flat on the desk
   * `hero`     the sheet the eye should land on first (soft cast shadow)
   * `sunken`   a well pressed into a sheet — empty states and recessed content
   */
  variant?: 'default' | 'hero' | 'sunken';
}): React.JSX.Element {
  const theme = useYanjiTheme();

  const radius = variant === 'hero' ? YanjiRadius.xxl : YanjiRadius.xl;

  const backgroundColor =
    variant === 'sunken' ? theme.colors.bgSunken : theme.colors.bgSurface;

  const padding = variant === 'hero' ? 22 : variant === 'sunken' ? 16 : 18;

  const shadow = variant === 'hero' ? theme.shadow.sheet(theme.isDark) : null;

  return (
    <View
      style={[
        {
          backgroundColor,
          borderRadius: radius,
          // Zero border — strictly zero border per design system.
          borderWidth: 0,
          padding,
        },
        shadow,
        style,
      ]}
    >
      {children}
    </View>
  );
}

// ---------------------------------------------------------------------------
// YanjiHairline — the one legitimate 1px line
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
  return (
    <View
      style={[
        {
          height: 1,
          marginLeft: inset,
          marginRight: inset,
          backgroundColor: theme.colors.hairline,
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
  style,
}: {
  label: string;
  icon?: YanjiIconName;
  style?: ViewStyle;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  return (
    <View
      style={[
        {
          flexDirection: 'row',
          alignItems: 'center',
          backgroundColor: theme.colors.accentSoft,
          paddingVertical: 4,
          paddingHorizontal: 10,
          borderRadius: YanjiRadius.full,
          alignSelf: 'flex-start',
        },
        style,
      ]}
    >
      {icon ? (
        <View style={{ marginRight: 5 }}>
          <YanjiIcon name={icon} size={12} color={theme.colors.accentPrimary} />
        </View>
      ) : null}
      <Text
        style={{
          color: theme.colors.accentPrimary,
          fontSize: 12,
          fontWeight: '600',
        }}
      >
        {label}
      </Text>
    </View>
  );
}

// ---------------------------------------------------------------------------
// YanjiPrimaryButton — restrained single accent action with tactile feel
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
  const secondary = variant === 'secondary';

  const backgroundColor = disabled || secondary ? theme.colors.bgElevated : theme.colors.accentPrimary;
  const textColor = disabled
    ? theme.colors.textDisabled
    : secondary
    ? theme.colors.textPrimary
    : theme.colors.onAccent;

  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={label}
      style={({ pressed }) => ({
        backgroundColor,
        borderRadius: YanjiRadius.xl,
        paddingVertical: 15,
        paddingHorizontal: 24,
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'center',
        minHeight: YanjiTouch.comfortable,
        opacity: pressed ? 0.88 : 1,
        transform: [{ scale: pressed ? 0.985 : 1 }],
        ...(pressed ? theme.shadow.pressed(theme.isDark) : null),
        // Only the accent action casts a shadow — otherwise every button on
        // screen would look equally important.
        ...(!disabled && !secondary ? theme.shadow.sheet(theme.isDark) : null),
      })}
    >
      {icon ? (
        <View style={{ marginRight: 8 }}>
          <YanjiIcon name={icon} size={17} color={textColor} />
        </View>
      ) : null}
      <Text
        style={{
          color: textColor,
          fontSize: 16,
          fontWeight: '600',
          letterSpacing: 0.3,
        }}
      >
        {label}
      </Text>
    </Pressable>
  );
}

// ---------------------------------------------------------------------------
// YanjiIconButton — icon-only control, guaranteed touch target
// ---------------------------------------------------------------------------

export function YanjiIconButton({
  icon,
  onPress,
  accessibilityLabel,
  size = YanjiTouch.min,
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
  const color = disabled ? theme.colors.textDisabled : theme.colors.textSecondary;

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
            tone === 'filled' ? theme.colors.bgElevated : 'transparent',
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
}: {
  value: number;
  onChange: (next: number) => void;
  min?: number;
  max?: number;
  step?: number;
  suffix?: string;
  label: string;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const canDecrease = value - step >= min;
  const canIncrease = value + step <= max;

  return (
    <View
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        alignSelf: 'flex-start',
        backgroundColor: theme.colors.bgSunken,
        borderRadius: YanjiRadius.full,
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
        size={YanjiTouch.min}
        iconSize={16}
      />
      <Text
        style={{
          minWidth: 56,
          textAlign: 'center',
          color: theme.colors.textPrimary,
          fontSize: 16,
          fontWeight: '600',
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
        size={YanjiTouch.min}
        iconSize={16}
      />
    </View>
  );
}

// ---------------------------------------------------------------------------
// YanjiProgressRing — SVG circular progress with centre content
// ---------------------------------------------------------------------------

export function YanjiProgressRing({
  progress,
  size = 132,
  thickness = 8,
  color,
  trackColor,
  children,
  style,
}: {
  /** 0..1. Values outside the range are clamped, never wrapped. */
  progress: number;
  size?: number;
  thickness?: number;
  color?: string;
  trackColor?: string;
  children?: React.ReactNode;
  style?: ViewStyle;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const clamped = Math.max(0, Math.min(1, Number.isFinite(progress) ? progress : 0));
  const radius = (size - thickness) / 2;
  const circumference = 2 * Math.PI * radius;
  const strokeColor = color ?? theme.colors.accentPrimary;
  const strokeTrack = trackColor ?? theme.colors.bgElevated;

  return (
    <View
      style={[{ width: size, height: size, alignItems: 'center', justifyContent: 'center' }, style]}
      accessibilityRole="progressbar"
      accessibilityValue={{ min: 0, max: 100, now: Math.round(clamped * 100) }}
    >
      <Svg width={size} height={size} style={{ position: 'absolute' }}>
        <Circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          stroke={strokeTrack}
          strokeWidth={thickness}
          fill="none"
        />
        <Circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          stroke={strokeColor}
          strokeWidth={thickness}
          strokeLinecap="round"
          fill="none"
          strokeDasharray={`${circumference} ${circumference}`}
          // Start at 12 o'clock rather than 3 o'clock.
          strokeDashoffset={circumference * (1 - clamped)}
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
        />
      </Svg>
      {children}
    </View>
  );
}

// ---------------------------------------------------------------------------
// YanjiMiniBarChart — SVG column chart, honest about empty data
// ---------------------------------------------------------------------------

export function YanjiMiniBarChart({
  data,
  width = 240,
  height = 56,
  gap = 4,
  color,
  highlightColor,
  highlightIndex,
  emptyLabel,
}: {
  data: number[];
  width?: number;
  height?: number;
  gap?: number;
  color?: string;
  highlightColor?: string;
  /** Column to emphasise (e.g. today). */
  highlightIndex?: number;
  emptyLabel?: string;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const barColor = color ?? theme.colors.bgElevated;
  const activeColor = highlightColor ?? theme.colors.accentPrimary;
  const max = Math.max(1, ...data);

  if (data.length === 0) {
    return (
      <View style={{ width, height, justifyContent: 'flex-end' }}>
        <YanjiHairline style={{ marginBottom: 0 }} />
        {emptyLabel ? (
          <Text
            style={{
              marginTop: 8,
              color: theme.colors.textTertiary,
              fontSize: 12,
              textAlign: 'center',
            }}
          >
            {emptyLabel}
          </Text>
        ) : null}
      </View>
    );
  }

  const barWidth = Math.max(2, (width - gap * (data.length - 1)) / data.length);

  return (
    <Svg width={width} height={height}>
      {data.map((value, index) => {
        // Every column keeps a visible minimum so a zero day is still a slot,
        // not a gap — but it must not read as "a little work happened".
        const ratio = value <= 0 ? 0 : Math.max(0.06, value / max);
        const barHeight = Math.max(2, ratio * height);
        const isActive = highlightIndex === index;
        return (
          <Rect
            key={index}
            x={index * (barWidth + gap)}
            y={height - barHeight}
            width={barWidth}
            height={barHeight}
            rx={Math.min(3, barWidth / 2)}
            fill={isActive ? activeColor : barColor}
          />
        );
      })}
    </Svg>
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
  const scale = useSharedValue(1);

  useEffect(() => {
    if (disabled) return;
    // One gentle settle: 1 -> 1.025 -> 1.
    scale.value = withSequence(
      withTiming(1.025, { duration: theme.motion.duration.slow, easing: Easing.out(Easing.quad) }),
      withSpring(1, theme.motion.spring.gentle),
    );
    // `theme` is intentionally omitted: this must fire once per mount, not on
    // every theme toggle.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [disabled]);

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
          scale.value = withSpring(0.97, theme.motion.spring.press);
        }}
        onPressOut={() => {
          scale.value = withSpring(1, theme.motion.spring.press);
        }}
        style={({ pressed }) => ({
          backgroundColor: disabled
            ? theme.colors.bgElevated
            : theme.colors.accentPrimary,
          borderRadius: YanjiRadius.xl,
          paddingVertical: 16,
          paddingHorizontal: 26,
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: YanjiTouch.comfortable,
          opacity: disabled ? 1 : pressed ? 0.92 : 1,
          ...(disabled ? null : theme.shadow.sheet(theme.isDark)),
        })}
      >
        {icon ? (
          <View style={{ marginRight: 9 }}>
            <YanjiIcon
              name={icon}
              size={19}
              color={disabled ? theme.colors.textDisabled : theme.colors.onAccent}
            />
          </View>
        ) : null}
        <Text
          style={{
            color: disabled ? theme.colors.textDisabled : theme.colors.onAccent,
            fontSize: 16,
            fontWeight: '600',
            letterSpacing: 0.3,
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
          borderRadius: YanjiRadius.full,
          backgroundColor: theme.colors.bgElevated,
          alignItems: 'center',
          justifyContent: 'center',
          marginBottom: compact ? YanjiSpacing.xs : YanjiSpacing.md,
        }}
      >
        <YanjiIcon name={icon} size={compact ? 18 : 22} color={theme.colors.textSecondary} />
      </View>
      <Text
        style={{
          color: theme.colors.textPrimary,
          ...(compact ? theme.typography.label : theme.typography.bodyStrong),
          textAlign: 'center',
        }}
      >
        {title}
      </Text>
      {hint ? (
        <Text
          style={{
            color: theme.colors.textTertiary,
            ...theme.typography.caption,
            marginTop: compact ? 2 : YanjiSpacing.xs,
            textAlign: 'center',
          }}
        >
          {hint}
        </Text>
      ) : null}
    </View>
  );
}

// ---------------------------------------------------------------------------
// YanjiSectionHeader — typographic grouping with optional right accessory
// ---------------------------------------------------------------------------

export function YanjiSectionHeader({
  title,
  rightAction,
}: {
  title: string;
  rightAction?: React.ReactNode;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  return (
    <View
      style={{
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: YanjiSpacing.md,
        marginTop: YanjiSpacing.xl,
      }}
    >
      {/* Marked as a heading so TalkBack users can jump between sections instead
          of reading the page as one unbroken stream of rows. */}
      <Text
        accessibilityRole="header"
        style={[
          theme.typography.label,
          { color: theme.colors.textSecondary, textTransform: 'uppercase' },
        ]}
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
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityState={{ selected }}
      style={({ pressed }) => [
        {
          paddingVertical: 8,
          paddingHorizontal: 14,
          borderRadius: YanjiRadius.full,
          marginRight: theme.spacing.sm,
          marginBottom: theme.spacing.sm,
          // Selected chips stay soft-tinted rather than filled: a wall of solid
          // accent pills reads as "many primary actions", and this screen has
          // only one (the start button below).
          backgroundColor: selected ? theme.colors.accentSoft : theme.colors.bgSurface,
          opacity: pressed ? 0.7 : 1,
        },
        style,
      ]}
    >
      <Text
        style={{
          color: selected ? theme.colors.accentPrimary : theme.colors.textSecondary,
          fontSize: 14,
          fontWeight: selected ? '600' : '400',
        }}
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
  return (
    <TextInput
      value={value}
      onChangeText={onChangeText}
      placeholder={placeholder}
      placeholderTextColor={theme.colors.textTertiary}
      multiline={multiline}
      accessibilityLabel={accessibilityLabel ?? placeholder}
      style={[
        {
          backgroundColor: theme.colors.bgSunken,
          borderRadius: YanjiRadius.md,
          padding: theme.spacing.md,
          color: theme.colors.textPrimary,
          fontSize: 15,
          lineHeight: 22,
          textAlignVertical: multiline ? 'top' : 'center',
        },
        style,
      ]}
    />
  );
}
