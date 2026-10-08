/**
 * Yanji Design Tokens — React Native side.
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
    /**
     * Foreground on `accentPrimary` (Compose: YanjiOnPrimary #FFFFFF).
     * Measured 4.82:1 on #356AE6 — passes WCAG 1.4.3.
     */
    onAccent: '#FFFFFF',
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
     */
    onAccent: '#0D111A',
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
