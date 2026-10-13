/**
 * Yanji visual language: quiet study journal.
 * Semantic surfaces may be elevated controls or unboxed plans and timelines.
 * Typography and readable rhythm, rather than card outlines, establish hierarchy.
 */

import type { ViewStyle } from 'react-native';

export const YanjiColors = {
  light: {
    /** The desk: refined soft blue-gray page background. */
    bgPrimary: '#F6F8FA',
    /** A sheet of paper lying on the desk (Compose: YanjiSurface #FFFFFF). */
    bgSurface: '#FFFFFF',
    /** Soft nested fill inside a sheet — chips, tracks, secondary buttons. */
    bgElevated: '#F1F5FB',
    /**
     * A well pressed INTO a sheet — text inputs and other recessed areas.
     * RN-local (no Compose source). 1.14:1 against the white sheet, and every
     * text tier still clears 4.5:1 on it (#5A6473 5.24, #606A7C 4.77).
     */
    bgSunken: '#EDF0F5',
    /** A sheet lifted clear of the desk — tab bar, modal sheets. */
    bgFloating: '#FFFFFF',
    textPrimary: '#172033',
    textSecondary: '#5A6473',
    textTertiary: '#606A7C',
    textDisabled: '#98A2B3',
    /** Indigo dark enough for 4.5:1 ordinary white labels and tinted-chip text. */
    accentPrimary: '#3462DC',
    accentStrong: '#2555D4',
    accentSoft: '#EDF2FE',
    /**
     * Foreground on `accentPrimary` (Compose: YanjiOnPrimary #FFFFFF).
     * Maintains WCAG 1.4.3 normal-text contrast on accentPrimary.
     */
    onAccent: '#FFFFFF',
    /**
     * Subtle 1px hairline border for card containers (1px hairline).
     */
    cardBorder: 'rgba(0, 0, 0, 0.06)',
    cardBorderStrong: 'rgba(0, 0, 0, 0.12)',
    /**
     * Decorative 1px separator for list grouping.
     */
    hairline: 'rgba(0, 0, 0, 0.06)',
    /**
     * Modal / sheet scrim. RN-local.
     */
    scrim: 'rgba(0, 0, 0, 0.32)',
    /**
     * Semantic colours — pinned for WCAG >= 4.5:1.
     */
    success: '#17784F',
    warning: '#9A6100',
    danger: '#BE3232',
    /** Field/control boundary color (WCAG 1.4.11 ≥3:1) — inputs/buttons only. */
    fieldBorder: '#7E8DA1',
  },
  dark: {
    /** Midnight Blue base (Compose: YanjiDarkBackground #0D111A). STRICTLY NO #000000. */
    bgPrimary: '#0D111A',
    /** Standard dark card (Compose: YanjiDarkSurface #151B28). */
    bgSurface: '#151B28',
    /** Nested / secondary surface (Compose: YanjiDarkSurfaceSoft #1D2536). */
    bgElevated: '#1D2536',
    /**
     * Dark-mode recessed well. Sits BELOW the sheet rather than above it, so
     * it is darker than #151B28 — but never pure black (§三.6).
     */
    bgSunken: '#111823',
    /** Floating surface (Compose: YanjiDarkSurfaceFloating #222C40). */
    bgFloating: '#222C40',
    textPrimary: '#F0F4FC',
    textSecondary: '#94A3B8',
    textTertiary: '#8797AC',
    textDisabled: '#475569',
    accentPrimary: '#7197F7',
    accentStrong: '#83A8FF',
    accentSoft: 'rgba(90, 136, 248, 0.15)',
    /**
     * Foreground on `accentPrimary` (Compose: YanjiDarkOnPrimary #0D111A).
     */
    onAccent: '#0D111A',
    /**
     * Subtle 1px hairline border for dark mode cards.
     */
    cardBorder: 'rgba(255, 255, 255, 0.08)',
    cardBorderStrong: 'rgba(255, 255, 255, 0.16)',
    hairline: 'rgba(255, 255, 255, 0.08)',
    /**
     * Heavier scrim than light mode.
     */
    scrim: 'rgba(0, 0, 0, 0.56)',
    success: '#34D399',
    warning: '#FBBF24',
    danger: '#F87171',
    fieldBorder: '#6B7A91',
  },
} as const;

/** Semantic radius scale (Compose: YanjiRadius). Raw dp literals are forbidden at call sites. */
export const YanjiRadius = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 28,
  full: 9999,
} as const;

/** Spacing scale (Compose: Spacing.kt). */
export const YanjiSpacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 32,
  /** Horizontal page gutter — every screen's outermost inset. */
  page: 20,
} as const;

/** Shared composition rhythm for the three main screens. */
export const YanjiLayout = {
  sectionGap: 28,
  titleGap: 10,
  heroInset: 22,
  listRowMinHeight: 56,
  timerTopGap: 36,
} as const;

/** Typography scale — hierarchy by size and weight, not color dilution. */
export const YanjiTypography = {
  pageTitle: { fontSize: 29, fontWeight: '700' as const, lineHeight: 38 },
  dayTitle: { fontSize: 25, fontWeight: '600' as const, lineHeight: 34 },
  heroTitle: { fontSize: 23, fontWeight: '600' as const, lineHeight: 32 },
  focusValue: { fontSize: 68, fontWeight: '300' as const, lineHeight: 82 },
  sectionTitle: { fontSize: 20, fontWeight: '600' as const, lineHeight: 26 },
  body: { fontSize: 15, fontWeight: '400' as const, lineHeight: 22 },
  bodyStrong: { fontSize: 15, fontWeight: '600' as const, lineHeight: 22 },
  caption: { fontSize: 13, fontWeight: '400' as const, lineHeight: 18 },
  /**
   * List-row title. One step under `body` so a dense list (timeline rows,
   * subject rows) still reads as a list rather than as paragraph text.
   */
  rowTitle: { fontSize: 14, fontWeight: '400' as const, lineHeight: 20 },
  rowTitleStrong: { fontSize: 14, fontWeight: '600' as const, lineHeight: 20 },
  /**
   * Secondary readout (totals, the selected date label). Heavier than
   * `caption` but well below `display`, so it never competes with a hero.
   */
  valueStrong: { fontSize: 17, fontWeight: '600' as const, lineHeight: 22 },
  /** Quiet metadata on a row: timestamps, mode names, "· 模考" tags. */
  meta: { fontSize: 12, fontWeight: '400' as const, lineHeight: 16 },
  /** Chart axis labels. The smallest legible step; never below 11px. */
  axisLabel: { fontSize: 11, fontWeight: '400' as const, lineHeight: 14 },
  /**
   * Micro label for over-line grouping ("今天", "专注记录"). Uppercase tracking
   * is applied at the call site so CJK labels are not letter-spaced apart.
   */
  label: { fontSize: 11, fontWeight: '600' as const, lineHeight: 14, letterSpacing: 0.6 },
  /** Oversized focus timer digits. */
  timerDisplay: { fontSize: 64, fontWeight: '300' as const, lineHeight: 72 },
  /** Large readout for secondary numbers (streak, totals) — lighter than a title. */
  display: { fontSize: 40, fontWeight: '300' as const, lineHeight: 48 },
} as const;

/** Card rule: subtle 1px hairline border (cardBorder) per modern refined minimal design. */
export const YanjiCardRules = {
  borderWidth: 1,
  hasBorderStroke: true,
  plainBorderWidth: 0,
} as const;

/**
 * Minimum touch targets (WCAG 2.5.5 / platform HIG). Icon-only controls must
 * reach `min`; primary actions should use `comfortable`.
 */
export const YanjiTouch = {
  min: 44,
  comfortable: 48,
} as const;

/**
 * Cast shadows — diffuse elevation.
 *
 * Centralised because iOS and Android disagree: iOS wants shadowColor/Offset/
 * Radius/Opacity, Android only understands `elevation`.
 */
export const YanjiShadow = {
  /** A card resting on the desk — subtle diffuse elevation. */
  sheet: (isDark: boolean): ViewStyle =>
    PlatformShadow(isDark, {
      radius: 12,
      offsetY: 2,
      opacity: isDark ? 0.36 : 0.05,
      elevation: 2,
    }),
  /** The tab bar and other sheets lifted clear of the desk. */
  floating: (isDark: boolean): ViewStyle =>
    PlatformShadow(isDark, {
      radius: 20,
      offsetY: 6,
      opacity: isDark ? 0.44 : 0.09,
      elevation: 8,
    }),
  /** Pressed feedback — a sheet pushed toward the desk. */
  pressed: (isDark: boolean): ViewStyle =>
    PlatformShadow(isDark, {
      radius: 4,
      offsetY: 1,
      opacity: isDark ? 0.28 : 0.04,
      elevation: 1,
    }),
} as const;

function PlatformShadow(
  isDark: boolean,
  spec: { radius: number; offsetY: number; opacity: number; elevation: number },
): ViewStyle {
  // Dark mode needs a deeper shadow colour to read at all; pure black is fine
  // as a *shadow* (the §三.6 ban is on background tokens), but a Midnight-Blue
  // tint keeps it in family.
  return {
    shadowColor: isDark ? '#000000' : '#172033',
    shadowOffset: { width: 0, height: spec.offsetY },
    shadowOpacity: spec.opacity,
    shadowRadius: spec.radius,
    elevation: spec.elevation,
  };
}

/**
 * Motion — 「纸上研迹」moves like paper: light, spring-settled, never bouncy.
 * Values are reanimated spring configs, shared by every animated component.
 */
export const YanjiMotion = {
  spring: {
    /** Default settle for sheets, pills and fades. */
    gentle: { damping: 18, stiffness: 140, mass: 1 },
    /** Selection changes that must feel immediate. */
    snappy: { damping: 20, stiffness: 260, mass: 0.9 },
    /** Press-down feedback. */
    press: { damping: 15, stiffness: 320, mass: 0.8 },
  },
  duration: {
    fast: 120,
    base: 220,
    slow: 360,
  },
} as const;
