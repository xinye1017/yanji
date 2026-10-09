/**
 * Yanji Design Tokens — React Native side.
 *
 * Authority: test-e2e/contracts/theme-tokens.js (machine-asserted) +
 * app/src/main/java/com/example/yanji/theme/*.kt (Compose source of truth).
 *
 * Visual language: 「纸上研迹」— surfaces are sheets of paper on a desk.
 * Depth is expressed by a five-step surface ladder plus soft cast shadows;
 * cards carry NO borders (see YanjiCardRules). Hierarchy comes from surface
 * lightness, shadow and typography — never from outlines.
 *
 * Hard constraints (AGENTS.md §三.6), machine-asserted by
 * test-e2e/tier1-features/m8-shipped-tokens.test.js:
 * - Dark mode MUST be Midnight Blue family; pure black #000000 is forbidden.
 * - Light page background MUST be the soft blue-gray #F2F4F7 (never pure white,
 *   or white cards would vanish into the page).
 * - onAccent / success / warning / danger light values are pinned.
 * - Radius xxl=28 and full=9999 are first-class steps.
 * - Card containers have ZERO borders in both light and dark.
 */

import type { ViewStyle } from 'react-native';

export const YanjiColors = {
  light: {
    /** The desk: soft blue-gray page background (Compose: YanjiBackground #F2F4F7). */
    bgPrimary: '#F2F4F7',
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
    /** Restrained blue accent (Compose: YanjiPrimary #356AE6). */
    accentPrimary: '#356AE6',
    accentStrong: '#2453BF',
    accentSoft: '#EAF1FF',
    /**
     * Foreground on `accentPrimary` (Compose: YanjiOnPrimary #FFFFFF).
     * Measured 4.82:1 on #356AE6 — passes WCAG 1.4.3.
     */
    onAccent: '#FFFFFF',
    /**
     * Decorative 1px separator for list grouping. Deliberately NOT a card
     * border (cards stay zero-border): a hairline divides rows inside one
     * sheet. 1.23:1 against white — visible, never competing with content.
     */
    hairline: '#E3E8F0',
    /**
     * Modal / sheet scrim. RN-local: Compose has no scrim token (Material
     * `scrim` is only used as a shadow colour in GlassBottomBar.kt:220), so
     * this is a deliberate RN decision.
     */
    scrim: 'rgba(0,0,0,0.32)',
    /**
     * Semantic colours — the "text/icon" tier, so they are set for >=4.5:1.
     * Authority: app/src/main/java/com/example/yanji/theme/Color.kt:105-111,
     * which RETIRED the previous values (#2F9E6D / #E67E22 / #D64545) with
     * measured failures (success 3.37, warning 2.64, danger 4.15).
     * Measured here: #17784F 5.47/4.97, #9A6100 5.14/4.67, #BE3232 5.69/5.16
     * (white card / page #F2F4F7). Do not drift these back.
     */
    success: '#17784F',
    warning: '#9A6100',
    danger: '#BE3232',
    /** Field/control boundary color (WCAG 1.4.11 ≥3:1) — inputs/buttons only. */
    fieldBorder: '#7E8DA1',
  },
  dark: {
    /** Midnight Blue base (Compose: YanjiDarkBackground #0D111A). */
    bgPrimary: '#0D111A',
    /** Standard dark card (Compose: YanjiDarkSurface #151B28). */
    bgSurface: '#151B28',
    /** Nested / secondary surface (Compose: YanjiDarkSurfaceSoft #1D2536). */
    bgElevated: '#1D2536',
    /**
     * Dark-mode recessed well. Sits BELOW the sheet rather than above it, so
     * it is darker than #151B28 — but never pure black (§三.6). 1.03:1 against
     * the sheet: a felt recession, not a hole. Text stays 5.85:1+ on it.
     */
    bgSunken: '#111823',
    /** Floating surface (Compose: YanjiDarkSurfaceFloating #222C40). */
    bgFloating: '#222C40',
    textPrimary: '#F0F4FC',
    textSecondary: '#94A3B8',
    textTertiary: '#8797AC',
    textDisabled: '#475569',
    accentPrimary: '#4F7DF3',
    accentStrong: '#7197F7',
    accentSoft: '#294F7DF3',
    /**
     * Foreground on `accentPrimary` (Compose: YanjiDarkOnPrimary #0D111A).
     * Deliberately dark, not white: the dark accent is raised to #4F7DF3 for
     * legibility on the Midnight-Blue background, and white on it measures only
     * 3.78:1 (Color.kt:205-212 documents 2.56–3.78:1 across the five mascot
     * themes). #0D111A measures 5.00:1 and is the existing background token.
     *
     * The same reasoning applies to the danger button: white on the dark
     * danger #F87171 measures only 2.77:1, so danger buttons must use
     * `onAccent` too (6.83:1). YanjiPrimaryButton enforces this.
     */
    onAccent: '#0D111A',
    hairline: '#232C3D',
    /**
     * Heavier scrim than light mode: a 0.32 black veil composites to ~#090C11
     * on the #0D111A background, which barely separates the sheet. 0.56 gives
     * the floating layer real presence. RN-local decision (no Compose source).
     */
    scrim: 'rgba(0,0,0,0.56)',
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

/** Typography scale — hierarchy by size and weight, not color dilution. */
export const YanjiTypography = {
  pageTitle: { fontSize: 28, fontWeight: '700' as const, lineHeight: 34 },
  sectionTitle: { fontSize: 20, fontWeight: '600' as const, lineHeight: 26 },
  body: { fontSize: 15, fontWeight: '400' as const, lineHeight: 22 },
  bodyStrong: { fontSize: 15, fontWeight: '600' as const, lineHeight: 22 },
  caption: { fontSize: 13, fontWeight: '400' as const, lineHeight: 18 },
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

/** Card rule: zero border in both modes — hierarchy comes from surface lightness only. */
export const YanjiCardRules = {
  borderWidth: 0,
  hasBorderStroke: false,
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
 * Cast shadows — the material cue that says "this is a sheet of paper".
 *
 * Centralised because iOS and Android disagree: iOS wants shadowColor/Offset/
 * Radius/Opacity, Android only understands `elevation` (and derives its own
 * shadow from it). Spreading platform conditionals across call sites is how a
 * design system drifts, so every shadow in the app comes from here.
 */
export const YanjiShadow = {
  /** A card resting on the desk. */
  sheet: (isDark: boolean): ViewStyle =>
    PlatformShadow(isDark, {
      radius: 10,
      offsetY: 2,
      opacity: isDark ? 0.34 : 0.07,
      elevation: 2,
    }),
  /** The tab bar and other sheets lifted clear of the desk. */
  floating: (isDark: boolean): ViewStyle =>
    PlatformShadow(isDark, {
      radius: 22,
      offsetY: 8,
      opacity: isDark ? 0.46 : 0.13,
      elevation: 10,
    }),
  /** Pressed feedback — a sheet pushed toward the desk. */
  pressed: (isDark: boolean): ViewStyle =>
    PlatformShadow(isDark, {
      radius: 4,
      offsetY: 1,
      opacity: isDark ? 0.28 : 0.05,
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
