/**
 * Yanji E2E Test Suite - Theme & Design Tokens Specification
 * Validates design system constraints:
 * 1. Dark mode uses Midnight Blue palette, STRICTLY NO '#000000' pure black background.
 * 2. Light mode uses soft blue-gray palette.
 * 3. Card containers have ZERO borders (no BorderStroke, border-width 0).
 * 4. Corner radius strictly follows YanjiRadius semantic scale.
 */

export const YanjiThemeTokens = {
  /**
   * Authoritative palette. Mirrors src/theme/tokens.ts (RN) and
   * app/src/main/java/com/example/yanji/theme/Color.kt (Compose).
   *
   * Semantic light values adopt Compose's retired-replacement numbers
   * (success #17784F / warning #9A6100 / danger #BE3232) — the previous
   * #2F9E6D / #E67E22 / #D64545 failed WCAG and were formally retired.
   */
  colors: {
    light: {
      bgPrimary: '#F2F4F7',      // Soft blue-gray page background
      bgSurface: '#FFFFFF',
      bgElevated: '#F1F5FB',
      textPrimary: '#172033',
      textSecondary: '#5A6473',
      accentPrimary: '#356AE6',  // Restrained blue accent
      onAccent: '#FFFFFF',       // Matches YanjiOnPrimary
      success: '#17784F',
      warning: '#9A6100',
      danger: '#BE3232',
    },
    dark: {
      bgPrimary: '#0D111A',      // Midnight Blue base (never #000000)
      bgSurface: '#151B28',
      bgElevated: '#1D2536',
      textPrimary: '#F0F4FC',
      textSecondary: '#94A3B8',
      accentPrimary: '#4F7DF3',  // Raised for dark-background contrast
      onAccent: '#0D111A',       // Matches YanjiDarkOnPrimary — white on the raised dark primary fails WCAG
      success: '#34D399',
      warning: '#FBBF24',
      danger: '#F87171',
    },
  },
  /** YanjiRadius semantic scale — raw dp literals are forbidden at call sites. */
  radius: {
    xs: 4,
    sm: 8,
    md: 12,
    lg: 16,
    xl: 24,
    xxl: 28, // HeroCardRadius / SheetRadius (Radius.kt)
    full: 9999,
  },
  cardRules: {
    borderWidth: 0,
    hasBorderStroke: false,
  },

  validateBackgroundColor(colorHex, isDark) {
    const normalized = colorHex.trim().toLowerCase();
    if (isDark) {
      if (normalized === '#000' || normalized === '#000000' || normalized === 'black') {
        return {
          valid: false,
          error: `VIOLATION: Dark mode background is pure black (${colorHex}). Active constraint forbids #000000, must use Midnight Blue.`,
        };
      }
    }
    return { valid: true };
  },

  validateCardBorder(borderWidth) {
    if (borderWidth && borderWidth > 0) {
      return {
        valid: false,
        error: `VIOLATION: Card border width is ${borderWidth}. Active constraint mandates Card Zero-Border across light and dark modes.`,
      };
    }
    return { valid: true };
  },

  validateRadiusToken(value) {
    const validValues = Object.values(YanjiThemeTokens.radius);
    if (!validValues.includes(value)) {
      return {
        valid: false,
        error: `VIOLATION: Radius ${value}px does not conform to YanjiRadius tokens (${validValues.join(', ')}).`,
      };
    }
    return { valid: true };
  },
};
