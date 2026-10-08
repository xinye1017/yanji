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
      assertEqual(tokens.YanjiColors.light.bgPrimary, '#F2F4F7', 'Light page background must be #F2F4F7');
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

    it('card rule declares zero border in both light and dark', () => {
      assertEqual(tokens.YanjiCardRules.borderWidth, 0, 'Card borderWidth must be 0');
      assertEqual(tokens.YanjiCardRules.hasBorderStroke, false, 'Card must not carry a BorderStroke');
    });
  });
}
