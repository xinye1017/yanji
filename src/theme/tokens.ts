/**
 * Yanji Design Tokens — React Native / NativeWind side.
 *
 * Authority: test-e2e/contracts/theme-tokens.js (machine-asserted) +
 * app/src/main/java/com/example/yanji/theme/*.kt (Compose source of truth).
 *
 * Hard constraints (AGENTS.md §三.6):
 * - Dark mode MUST be Midnight Blue family; pure black #000000 is forbidden.
 * - Card containers have ZERO borders in both light and dark (no BorderStroke).
 * - Radius values come from the YanjiRadius semantic scale only.
 */

export const YanjiColors = {
  light: {
    /** Soft blue-gray page background (Compose: YanjiBackground #F2F4F7). */
    bgPrimary: '#F2F4F7',
    /** Card / surface (Compose: YanjiSurface #FFFFFF). */
    bgSurface: '#FFFFFF',
    /** Nested / secondary surface (Compose: YanjiSurfaceSoft #F1F5FB). */
    bgElevated: '#F1F5FB',
    /** Page-level elevated surface (Compose: YanjiElevatedSurface). */
    bgFloating: '#FFFFFF',
    textPrimary: '#172033',
    textSecondary: '#5A6473',
    textTertiary: '#606A7C',
    textDisabled: '#98A2B3',
    /** Restrained blue accent (Compose: YanjiPrimary #356AE6). */
    accentPrimary: '#356AE6',
    accentStrong: '#2453BF',
    accentSoft: '#EAF1FF',
    /** Semantic colors. */
    success: '#2F9E6D',
    warning: '#E67E22',
    danger: '#D64545',
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
    /** Floating surface (Compose: YanjiDarkSurfaceFloating #222C40). */
    bgFloating: '#222C40',
    textPrimary: '#F0F4FC',
    textSecondary: '#94A3B8',
    textTertiary: '#8797AC',
    textDisabled: '#475569',
    accentPrimary: '#4F7DF3',
    accentStrong: '#7197F7',
    accentSoft: '#294F7DF3',
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
} as const;

/** Typography scale — hierarchy by size and weight, not color dilution. */
export const YanjiTypography = {
  pageTitle: { fontSize: 28, fontWeight: '700' as const, lineHeight: 34 },
  sectionTitle: { fontSize: 20, fontWeight: '600' as const, lineHeight: 26 },
  body: { fontSize: 15, fontWeight: '400' as const, lineHeight: 22 },
  bodyStrong: { fontSize: 15, fontWeight: '600' as const, lineHeight: 22 },
  caption: { fontSize: 13, fontWeight: '400' as const, lineHeight: 18 },
  /** Oversized focus timer digits. */
  timerDisplay: { fontSize: 64, fontWeight: '300' as const, lineHeight: 72 },
} as const;

/** Card rule: zero border in both modes — hierarchy comes from surface lightness only. */
export const YanjiCardRules = {
  borderWidth: 0,
  hasBorderStroke: false,
} as const;
