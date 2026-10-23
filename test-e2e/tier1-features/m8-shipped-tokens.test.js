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

function compositeOn(tint, background) {
  const rgba = /^rgba\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*([\d.]+)\s*\)$/.exec(tint);
  assert(rgba, `Expected an rgba tint, got ${tint}`);
  const [r, g, b, alpha] = rgba.slice(1).map(Number);
  const base = background.slice(1).match(/../g).map(c => parseInt(c, 16));
  return '#' + [r, g, b].map((c, i) =>
    Math.round(c * alpha + base[i] * (1 - alpha)).toString(16).padStart(2, '0')
  ).join('');
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
      assert(tokens.YanjiPaperColors && typeof tokens.YanjiPaperColors === 'object', 'YanjiPaperColors must be exported');
      assert(tokens.YanjiRadius && typeof tokens.YanjiRadius === 'object', 'YanjiRadius must be exported');
    });

    it('no dark token is pure black', () => {
      // §三.6: pure black is forbidden as a background. The paper canvas is a
      // warm near-black (#111315), never #000000.
      assertEqual(tokens.YanjiPaperColors.dark.canvas, '#111315', 'Dark canvas must be #111315');
      for (const [key, value] of Object.entries(tokens.YanjiPaperColors.dark)) {
        if (typeof value !== 'string' || !value.startsWith('#')) continue;
        const normalized = value.trim().toUpperCase();
        assert(
          normalized !== '#000000' && normalized !== '#000' && normalized !== 'BLACK',
          'Dark token ' + key + ' = ' + value + ' is pure black; §三.6 forbids it'
        );
      }
    });

    it('light mode is parchment, not a pure white page background', () => {
      assertEqual(tokens.YanjiPaperColors.light.canvas, '#F6F4EF', 'Light canvas must be #F6F4EF');
      assert(
        tokens.YanjiPaperColors.light.canvas !== '#FFFFFF',
        'A pure-white page background would leave white cards indistinguishable (§三.6)'
      );
    });

    it('onBrand exists in both modes with the planned values', () => {
      // The on-brand foreground must be explicit, not assumed: light brand is
      // dark enough for white, dark brand is light enough to need dark ink.
      assertEqual(tokens.YanjiPaperColors.light.onBrand, '#FFFFFF', 'Light onBrand must be #FFFFFF');
      assertEqual(tokens.YanjiPaperColors.dark.onBrand, '#111315', 'Dark onBrand must be #111315');
    });

    it('actual shipped brand text meets normal-size 4.5:1 contrast in both themes', () => {
      for (const [mode, colors] of Object.entries(tokens.YanjiPaperColors)) {
        assert(contrastRatio(colors.onBrand, colors.brand) >= 4.5, `${mode} on-brand CTA text contrast`);
        assert(contrastRatio(colors.brandInk, colors.canvas) >= 4.5, `${mode} brandInk on canvas contrast`);
        assert(contrastRatio(colors.brandInk, colors.surface) >= 4.5, `${mode} brandInk on surface contrast`);
        assert(contrastRatio(colors.ink, colors.canvas) >= 4.5, `${mode} ink on canvas contrast`);
        assert(contrastRatio(colors.secondary, colors.canvas) >= 4.5, `${mode} secondary on canvas contrast`);
        assert(contrastRatio(colors.danger, colors.surface) >= 4.5, `${mode} danger on surface contrast`);
      }
    });

    it('status labels stay readable on their tints across all paper surfaces', () => {
      for (const [mode, colors] of Object.entries(tokens.YanjiPaperColors)) {
        const expectedAlpha = mode === 'dark' ? 0.10 : 0.07;
        for (const [foreground, tint] of [
          ['brandInk', 'brandSubtle'], ['amber', 'amberSubtle'],
          ['positive', 'positiveSubtle'], ['danger', 'dangerSubtle'],
        ]) {
          const rgba = /^rgba\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*([\d.]+)\s*\)$/.exec(colors[tint]);
          assert(rgba, `${mode} ${tint} must be an rgba string`);
          assertEqual(Number(rgba[4]), expectedAlpha, `${mode} ${tint} must keep ${expectedAlpha} wash alpha`);
          for (const surface of ['canvas', 'elevated', 'surface', 'subtle']) {
            const background = compositeOn(colors[tint], colors[surface]);
            const ratio = contrastRatio(colors[foreground], background);
            assert(ratio >= 4.5, `${mode} ${foreground} on ${tint}/${surface}: ${ratio.toFixed(2)}:1`);
          }
        }
      }
    });

    it('light shadows share the paper ink while dark shadows keep their depth', () => {
      for (const [name, shadow] of Object.entries(tokens.YanjiShadow)) {
        assertEqual(shadow(false).shadowColor, tokens.YanjiPaperColors.light.ink, `${name} light shadow`);
        assertEqual(shadow(true).shadowColor, '#000000', `${name} dark shadow`);
      }
    });

    it('danger carries the retired-replacement value in light mode', () => {
      // #D64545 was retired on a measured WCAG failure; #B52B2B clears 4.5:1
      // even over a 7% dangerSubtle wash on the darkest light surface (subtle).
      assertEqual(tokens.YanjiPaperColors.light.danger, '#B52B2B', 'Light danger must be #B52B2B');
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

    it('paper layout keeps a shared hairline and legible section rhythm', () => {
      assertEqual(tokens.YanjiPaperLayout.hairlineWidth, 0.5, 'Shared hairline width must be 0.5');
      assert(tokens.YanjiPaperLayout.sectionGap >= tokens.YanjiSpacing.xl, 'Page sections need a legible rhythm');
      assert(
        tokens.YanjiPaperTypography.displayTimer.fontSize > tokens.YanjiPaperTypography.titleEditorial.fontSize,
        'Timer must dominate heading'
      );
    });
  });
}
