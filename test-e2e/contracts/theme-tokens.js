/**
 * Yanji E2E Test Suite - Theme & Design Tokens Specification
 * Validates design system constraints:
 * 1. Dark mode uses Midnight Blue palette, STRICTLY NO '#000000' pure black background.
 * 2. Light mode uses soft blue-gray palette.
 * 3. Card containers have ZERO borders (no BorderStroke, border-width 0).
 * 4. Corner radius strictly follows YanjiRadius semantic scale.
 */

export const YanjiThemeTokens = {
  colors: {
    dark: {
      bgPrimary: '#0B132B',    // Deep midnight base
      bgSurface: '#1C2541',    // Surface elevation 1
      bgElevated: '#3A506B',   // Surface elevation 2
      textPrimary: '#F8FAFC',
      textSecondary: '#94A3B8',
      accentPrimary: '#48CAE4', // Quiet teal/cyan
    },
    light: {
      bgPrimary: '#F8FAFC',    // Soft slate/blue-gray base
      bgSurface: '#FFFFFF',
      bgElevated: '#F1F5F9',
      textPrimary: '#0F172A',
      textSecondary: '#64748B',
      accentPrimary: '#0284C7',
    },
  },
  radius: {
    xs: 4,
    sm: 8,
    md: 12,
    lg: 16,
    xl: 24,
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
