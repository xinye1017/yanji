/**
 * Yanji E2E Test Suite - Theme & Design Tokens Specification
 * Validates design system constraints:
 * 1. Dark mode uses a warm near-black canvas, STRICTLY NO '#000000' pure black.
 * 2. Light mode uses parchment.
 * 3. Card containers stay at a subtle hairline border (<= 1).
 * 4. Corner radius strictly follows YanjiRadius semantic scale.
 */

export const YanjiThemeTokens = {
  /**
   * Authoritative palette — the 素笺与寸阴 paper domain. Mirrors
   * `YanjiPaperColors` in src/theme/tokens.ts.
   *
   * Light danger adopts the retired-replacement value #BE3232; the previous
   * #D64545 failed WCAG and was formally retired.
   */
  colors: {
    light: {
      canvas: '#F6F4EF',         // Parchment page background
      surface: '#FFFFFF',
      subtle: '#EDEAE2',
      ink: '#1C1E21',
      secondary: '#5C5F66',
      muted: '#94969C',
      hairline: 'rgba(28,30,33,0.08)',
      brand: '#C84B31',          // Fills only; text uses brandInk
      brandInk: '#B23A22',       // Readable brand text on parchment (5.43:1)
      onBrand: '#FFFFFF',
      amber: '#9A6100',
      scrim: 'rgba(28,30,33,0.32)',
      danger: '#BE3232',
    },
    dark: {
      canvas: '#111315',         // Warm near-black (never #000000)
      surface: '#1A1D21',
      subtle: '#23272D',
      ink: '#EAE6DF',
      secondary: '#9BA0A8',
      muted: '#636870',
      hairline: 'rgba(234,230,223,0.08)',
      brand: '#E06D53',
      brandInk: '#E06D53',       // Dark brand already clears 4.5:1 on canvas
      onBrand: '#111315',
      amber: '#DDA059',
      scrim: 'rgba(0,0,0,0.56)',
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
    borderWidth: 1,
    hasBorderStroke: true,
    plainBorderWidth: 0,
  },

  validateBackgroundColor(colorHex, isDark) {
    const normalized = colorHex.trim().toLowerCase();
    if (isDark) {
      if (normalized === '#000' || normalized === '#000000' || normalized === 'black') {
        return {
          valid: false,
          error: `VIOLATION: Dark mode background is pure black (${colorHex}). Active constraint forbids #000000; use the warm paper canvas instead.`,
        };
      }
    }
    return { valid: true };
  },

  validateCardBorder(borderWidth) {
    if (borderWidth && borderWidth > 1) {
      return {
        valid: false,
        error: `VIOLATION: Card border width is ${borderWidth}. Active constraint mandates Card subtle hairline border <= 1 across light and dark modes.`,
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
