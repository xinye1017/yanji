/**
 * Yanji shared UI components — React Native.
 *
 * Card rule (AGENTS.md §三.6): card containers have ZERO borders in both light
 * and dark modes. Hierarchy is expressed through surface lightness steps and
 * typography only — never through a BorderStroke or a heavy shadow.
 */

import React from 'react';
import { Pressable, Text, View } from 'react-native';
import type { ViewStyle } from 'react-native';
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiRadius } from '../theme/tokens';

// ---------------------------------------------------------------------------
// YanjiCard — zero-border surface container
// ---------------------------------------------------------------------------

export function YanjiCard({
  children,
  style,
  variant = 'default',
}: {
  children: React.ReactNode;
  style?: ViewStyle;
  variant?: 'default' | 'hero' | 'elevated';
}): React.JSX.Element {
  const theme = useYanjiTheme();
  const radius =
    variant === 'hero'
      ? YanjiRadius.xxl
      : variant === 'elevated'
      ? YanjiRadius.lg
      : YanjiRadius.xl;
  const bgColor =
    variant === 'elevated' ? theme.colors.bgElevated : theme.colors.bgSurface;

  return (
    <View
      style={[
        {
          backgroundColor: bgColor,
          borderRadius: radius,
          // Zero border — strictly zero border per design system.
          padding: variant === 'hero' ? 22 : 18,
        },
        style,
      ]}
    >
      {children}
    </View>
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
  icon?: string;
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
        <Text style={{ fontSize: 12, marginRight: 4 }}>{icon}</Text>
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
}: {
  label: string;
  icon?: string;
  onPress: () => void;
  disabled?: boolean;
  variant?: 'primary' | 'secondary' | 'danger';
}): React.JSX.Element {
  const theme = useYanjiTheme();

  const getBgColor = () => {
    if (disabled) return theme.colors.bgElevated;
    if (variant === 'secondary') return theme.colors.bgElevated;
    if (variant === 'danger') return theme.colors.danger;
    return theme.colors.accentPrimary;
  };

  const getTextColor = () => {
    if (disabled) return theme.colors.textDisabled;
    if (variant === 'secondary') return theme.colors.textPrimary;
    if (variant === 'danger') return '#FFFFFF';
    return theme.colors.onAccent;
  };

  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={label}
      style={({ pressed }) => ({
        backgroundColor: getBgColor(),
        borderRadius: YanjiRadius.xl,
        paddingVertical: 15,
        paddingHorizontal: 24,
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'center',
        opacity: pressed ? 0.88 : 1,
        transform: [{ scale: pressed ? 0.985 : 1 }],
      })}
    >
      {icon ? (
        <Text
          style={{
            fontSize: 15,
            color: getTextColor(),
            marginRight: 8,
          }}
        >
          {icon}
        </Text>
      ) : null}
      <Text
        style={{
          color: getTextColor(),
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
// YanjiEmptyState — quiet, factual empty state with delicate illustration
// ---------------------------------------------------------------------------

export function YanjiEmptyState({
  title,
  hint,
  icon = '🌱',
}: {
  title: string;
  hint?: string;
  icon?: string;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  return (
    <View
      style={{
        paddingVertical: 36,
        paddingHorizontal: 20,
        alignItems: 'center',
        justifyContent: 'center',
      }}
    >
      <View
        style={{
          width: 48,
          height: 48,
          borderRadius: YanjiRadius.full,
          backgroundColor: theme.colors.bgElevated,
          alignItems: 'center',
          justifyContent: 'center',
          marginBottom: 12,
        }}
      >
        <Text style={{ fontSize: 22 }}>{icon}</Text>
      </View>
      <Text
        style={{
          color: theme.colors.textPrimary,
          fontSize: 15,
          fontWeight: '600',
          textAlign: 'center',
        }}
      >
        {title}
      </Text>
      {hint ? (
        <Text
          style={{
            color: theme.colors.textTertiary,
            fontSize: 13,
            marginTop: 6,
            textAlign: 'center',
            lineHeight: 18,
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
        marginBottom: 10,
        marginTop: 24,
      }}
    >
      <Text
        style={{
          color: theme.colors.textSecondary,
          fontSize: 14,
          fontWeight: '600',
          letterSpacing: 0.2,
        }}
      >
        {title}
      </Text>
      {rightAction}
    </View>
  );
}
