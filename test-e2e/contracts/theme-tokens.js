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
   * 靛青 indigo is the only accent hue; every neutral is a warm grey. The
   * accent ramp carries data series, so subject identity never introduces a
   * second page accent.
   */
  colors: {
    light: {
      canvas: '#F7F3EC',         // Warm parchment page background
      surface: '#FFFCF7',
      subtle: '#EFE8DD',
      ink: '#2A241D',
      secondary: '#5E564B',
      muted: '#9B9184',
      hairline: 'rgba(42,36,29,0.09)',
      brand: '#33587C',          // 靛青 — fills and marks
      brandInk: '#2C4A69',       // Readable brand text on parchment (8.29:1)
      onBrand: '#FFFCF7',
      accentRamp: ['#2C4A66', '#3C6183', '#4E7596', '#6289A8', '#6C8FAB'],
      amber: '#6E5926',          // 茶金 waiting accent, not orange
      scrim: 'rgba(42,36,29,0.32)',
      danger: '#A9362E',
    },
    dark: {
      canvas: '#1A1612',         // Warm charcoal (never #000000)
      surface: '#27221C',
      subtle: '#322B24',
      ink: '#F3ECE0',
      secondary: '#B4A99A',
      muted: '#7D7265',
      hairline: 'rgba(243,236,224,0.10)',
      brand: '#8FB0CE',
      brandInk: '#A9C4DC',
      onBrand: '#1A1612',
      accentRamp: ['#BBD3E6', '#A3C0D9', '#89A9C4', '#6F91AE', '#587C99'],
      amber: '#D6BB82',
      scrim: 'rgba(0,0,0,0.56)',
      danger: '#EE8B83',
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
