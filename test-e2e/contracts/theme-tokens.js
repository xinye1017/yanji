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
   * Light danger adopts the retired-replacement value #B52B2B; the previous
   * #D64545 failed WCAG and was formally retired.
   */
  colors: {
    light: {
      canvas: '#F6F4EF',         // Parchment page background
      surface: '#FFFFFF',
      subtle: '#EDEAE2',
      ink: '#1C1E21',
      secondary: '#565960',
      muted: '#94969C',
      hairline: 'rgba(28,30,33,0.08)',
      brand: '#C84B31',          // Fills only; text uses brandInk
      brandInk: '#B23A22',       // Readable brand text on parchment (5.43:1)
      onBrand: '#FFFFFF',
      amber: '#875400',
      scrim: 'rgba(28,30,33,0.32)',
      danger: '#B52B2B',
    },
    dark: {
      canvas: '#1B1917',         // Warm charcoal (never #000000)
      surface: '#262320',
      subtle: '#302C28',
      ink: '#EFEAE1',
      secondary: '#A8A199',
      muted: '#6E6862',
      hairline: 'rgba(239,234,225,0.09)',
      brand: '#E8705A',
      brandInk: '#F08A74',       // Lifted so dark brand labels clear 4.5:1 on brandSubtle/subtle
      onBrand: '#1B1917',
      amber: '#DFA76A',
      scrim: 'rgba(0,0,0,0.56)',
      danger: '#F5827F',
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
    borderWidth: 0.5,
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
