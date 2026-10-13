/**
 * Tier 1 — Feature 8b: Shipped Design Token Contract (load-bearing)
 *
 * The rest of this suite drives `MockYanjiBridge` and the local
 * `contracts/theme-tokens.js` copy, both of which are conformant by construction
 * and therefore prove nothing about the real React Native tree. This suite
 * closes that gap: it imports the ACTUAL `src/theme/tokens.ts` module and
 * asserts the AGENTS.md §三.6 red lines against shipped values.
 *
 * Node 22 strips TypeScript types natively, so `tokens.ts` loads directly.
 */

import path from 'node:path';
import { pathToFileURL } from 'node:url';
import { describe, it, beforeEach } from '../framework/harness.js';
import { assert, assertEqual } from '../framework/assertions.js';

const PROJECT_ROOT = path.resolve(process.cwd());
const TOKENS_PATH = path.join(PROJECT_ROOT, 'src', 'theme', 'tokens.ts');

function luminance(hex) {
  const components = hex.slice(1).match(/../g).map(s => parseInt(s, 16) / 255);
  const [r, g, b] = components.map(c => c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4);
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function contrastRatio(a, b) {
  const x = luminance(a);
  const y = luminance(b);
  return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
}

function resolveTint(tint, surface) {
  if (tint.startsWith('#')) return tint;
  const [r, g, b, alpha] = tint.match(/[\d.]+/g).map(Number);
  const background = surface.slice(1).match(/../g).map(s => parseInt(s, 16));
  const result = [r, g, b].map((v, i) => Math.round(v * alpha + background[i] * (1 - alpha)));
  return '#' + result.map(v => v.toString(16).padStart(2, '0')).join('');
}

export function registerShippedTokenTests() {
  let tokens = null;

  beforeEach(async () => {
    // Re-imported per test so a concurrent edit to tokens.ts cannot be masked.
    const moduleUrl = pathToFileURL(TOKENS_PATH).href;
    tokens = await import(moduleUrl);
  });

  describe('Tier 1: Feature 8b — Shipped tokens.ts red lines (no #000000 in dark)', () => {
    it('loads the shipped src/theme/tokens.ts module', async () => {
      assert(tokens && typeof tokens === 'object', 'tokens.ts must export an object');
      assert(tokens.YanjiColors && typeof tokens.YanjiColors === 'object', 'YanjiColors must be exported');
      assert(tokens.YanjiRadius && typeof tokens.YanjiRadius === 'object', 'YanjiRadius must be exported');
    });

    it('dark background is Midnight Blue #0D111A and no dark token is pure black', () => {
      assertEqual(tokens.YanjiColors.dark.bgPrimary, '#0D111A', 'Dark background must be Midnight Blue #0D111A');
      for (const [key, value] of Object.entries(tokens.YanjiColors.dark)) {
        const normalized = String(value).trim().toUpperCase();
        assert(
          normalized !== '#000000' && normalized !== '#000' && normalized !== 'BLACK',
          'Dark token ' + key + ' = ' + value + ' is pure black; §三.6 forbids it'
        );
      }
    });

    it('light mode is soft blue-gray, not pure white page background', () => {
      assertEqual(tokens.YanjiColors.light.bgPrimary, '#F6F8FA', 'Light page background must be #F6F8FA');
      assert(
        tokens.YanjiColors.light.bgPrimary !== '#FFFFFF',
        'A pure-white page background would leave white cards indistinguishable (§三.6)'
      );
    });

    it('onAccent exists in both modes with the planned values', () => {
      // F11/F12: the previous hardcoded #FFFFFF on-accent foreground failed WCAG
      // on the raised dark primary. Both modes must declare an explicit token.
      assertEqual(tokens.YanjiColors.light.onAccent, '#FFFFFF', 'Light onAccent must be #FFFFFF');
      assertEqual(tokens.YanjiColors.dark.onAccent, '#0D111A', 'Dark onAccent must be #0D111A');
    });

    it('actual shipped accent text meets normal-size 4.5:1 contrast in both themes', () => {
      for (const [mode, colors] of Object.entries(tokens.YanjiColors)) {
        assert(contrastRatio(colors.onAccent, colors.accentPrimary) >= 4.5, `${mode} CTA text contrast`);
        assert(contrastRatio(colors.accentPrimary, resolveTint(colors.accentSoft, colors.bgSurface)) >= 4.5, `${mode} accent text on tinted control contrast`);
        assert(contrastRatio(colors.accentStrong, resolveTint(colors.accentSoft, colors.bgSurface)) >= 4.5, `${mode} selected Chip contrast`);
      }
    });

    it('semantic light colors adopt the retired-replacement values', () => {
      // #2F9E6D / #E67E22 / #D64545 were retired on measured WCAG failures.
      assertEqual(tokens.YanjiColors.light.success, '#17784F', 'Light success must be #17784F');
      assertEqual(tokens.YanjiColors.light.warning, '#9A6100', 'Light warning must be #9A6100');
      assertEqual(tokens.YanjiColors.light.danger, '#BE3232', 'Light danger must be #BE3232');
    });

    it('every radius value used in the scale belongs to the YanjiRadius scale', () => {
      const scale = Object.values(tokens.YanjiRadius);
      for (const [name, value] of Object.entries(tokens.YanjiRadius)) {
        assert(
          scale.includes(value),
          'Radius token ' + name + ' = ' + value + ' is not on its own scale'
        );
      }
      // 28 is a first-class step (Radius.kt HeroCardRadius / SheetRadius).
      assertEqual(tokens.YanjiRadius.xxl, 28, 'xxl radius step must be 28');
      assertEqual(tokens.YanjiRadius.full, 9999, 'full radius step must be 9999');
    });

    it('cards distinguish raised controls from borderless journal groups', () => {
      assertEqual(tokens.YanjiCardRules.borderWidth, 1, 'Card borderWidth must be 1');
      assertEqual(tokens.YanjiCardRules.hasBorderStroke, true, 'Raised card may have a hairline');
      assertEqual(tokens.YanjiCardRules.plainBorderWidth, 0, 'Journal group is borderless');
      assert(tokens.YanjiLayout.sectionGap >= tokens.YanjiSpacing.xl, 'Page sections need a legible rhythm');
      assert(tokens.YanjiTypography.focusValue.fontSize > tokens.YanjiTypography.pageTitle.fontSize, 'Timer must dominate heading');
    });
  });
}
