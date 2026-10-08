/**
 * Yanji shared UI components — React Native / NativeWind.
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
}: {
  children: React.ReactNode;
  style?: ViewStyle;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  return (
    <View
      style={[
        {
          backgroundColor: theme.colors.bgSurface,
          borderRadius: YanjiRadius.lg,
          // Zero border — deliberately no borderWidth / borderColor.
          padding: 16,
        },
        style,
      ]}
    >
      {children}
    </View>
  );
}

// ---------------------------------------------------------------------------
// YanjiPrimaryButton — restrained single accent action
// ---------------------------------------------------------------------------

export function YanjiPrimaryButton({
  label,
  onPress,
  disabled = false,
}: {
  label: string;
  onPress: () => void;
  disabled?: boolean;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      accessibilityRole="button"
      accessibilityLabel={label}
      style={({ pressed }) => ({
        backgroundColor: disabled
          ? theme.colors.bgElevated
          : theme.colors.accentPrimary,
        borderRadius: YanjiRadius.md,
        paddingVertical: 14,
        paddingHorizontal: 24,
        alignItems: 'center',
        opacity: pressed ? 0.85 : 1,
      })}
    >
      <Text
        style={{
          color: disabled ? theme.colors.textDisabled : '#FFFFFF',
          fontSize: 16,
          fontWeight: '600',
        }}
      >
        {label}
      </Text>
    </Pressable>
  );
}

// ---------------------------------------------------------------------------
// YanjiEmptyState — quiet, factual empty state (no fake data, no nudging)
// ---------------------------------------------------------------------------

export function YanjiEmptyState({
  title,
  hint,
}: {
  title: string;
  hint?: string;
}): React.JSX.Element {
  const theme = useYanjiTheme();
  return (
    <View style={{ paddingVertical: 32, alignItems: 'center' }}>
      <Text style={{ color: theme.colors.textSecondary, fontSize: 15, fontWeight: '600' }}>
        {title}
      </Text>
      {hint ? (
        <Text style={{ color: theme.colors.textTertiary, fontSize: 13, marginTop: 6 }}>
          {hint}
        </Text>
      ) : null}
    </View>
  );
}

// ---------------------------------------------------------------------------
// YanjiSectionHeader — typographic grouping without card chrome
// ---------------------------------------------------------------------------

export function YanjiSectionHeader({ title }: { title: string }): React.JSX.Element {
  const theme = useYanjiTheme();
  return (
    <Text
      style={{
        color: theme.colors.textSecondary,
        fontSize: 13,
        fontWeight: '600',
        marginBottom: 8,
        marginTop: 20,
      }}
    >
      {title}
    </Text>
  );
}
