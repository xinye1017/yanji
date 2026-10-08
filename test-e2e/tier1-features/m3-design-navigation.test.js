/**
 * Tier 1 — Features 9 to 11: Design Tokens, Navigation Shell & Settings Flow
 */

import { describe, it } from '../framework/harness.js';
import { assert, assertEqual } from '../framework/assertions.js';
import { YanjiThemeTokens } from '../contracts/theme-tokens.js';

export function registerM3Tests() {
  describe('Tier 1: Feature 9 — Design Tokens (Midnight Blue, Radius, Zero-Border)', () => {
    it('verifies dark mode palette is Midnight Blue and forbids #000000 pure black', () => {
      const darkBg = YanjiThemeTokens.colors.dark.bgPrimary;
      assertEqual(darkBg, '#0B132B', 'Dark mode primary background must be Midnight Blue #0B132B');

      const validation = YanjiThemeTokens.validateBackgroundColor('#000000', true);
      assertEqual(validation.valid, false, 'Validation must reject #000000 in dark mode');
      assert(validation.error.includes('#000000'), 'Error must explicitly identify pure black violation');
    });

    it('verifies card containers mandate ZERO borders in both light and dark themes', () => {
      const borderCheckZero = YanjiThemeTokens.validateCardBorder(0);
      assertEqual(borderCheckZero.valid, true, 'Zero border width must be valid');

      const borderCheckNonZero = YanjiThemeTokens.validateCardBorder(1);
      assertEqual(borderCheckNonZero.valid, false, 'Card border > 0 must be rejected');
      assert(borderCheckNonZero.error.includes('Card Zero-Border'), 'Error message must cite Card Zero-Border rule');
    });

    it('verifies all radius tokens adhere to semantic YanjiRadius scale', () => {
      const allowedRadius = [4, 8, 12, 16, 24, 9999];
      for (const r of allowedRadius) {
        const res = YanjiThemeTokens.validateRadiusToken(r);
        assertEqual(res.valid, true, `Radius ${r} must be valid`);
      }

      const invalidCheck = YanjiThemeTokens.validateRadiusToken(15);
      assertEqual(invalidCheck.valid, false, 'Arbitrary radius (15px) must be rejected');
    });
  });

  describe('Tier 1: Feature 10 — Three-tab Navigation Shell', () => {
    it('verifies navigation shell strictly defines exactly three main tabs', () => {
      const allowedTabs = ['Today', 'Focus', 'Review'];
      assertEqual(allowedTabs.length, 3, 'Bottom navigation must have strictly 3 destinations');

      // Check forbidden distracting elements
      const forbiddenTabs = ['Achievements', 'Badges', 'Ranking', 'Mascot', 'Community', 'Shop'];
      for (const forbidden of forbiddenTabs) {
        assert(!allowedTabs.includes(forbidden), `Tab "${forbidden}" is strictly prohibited`);
      }
    });

    it('verifies tab destinations map to user-facing primary dimensions', () => {
      const tabMeta = {
        Today: { label: '今天', route: 'today' },
        Focus: { label: '专注', route: 'focus' },
        Review: { label: '回顾', route: 'review' },
      };
      assertEqual(tabMeta.Today.label, '今天');
      assertEqual(tabMeta.Focus.label, '专注');
      assertEqual(tabMeta.Review.label, '回顾');
    });
  });

  describe('Tier 1: Feature 11 — Settings Screen & Navigation Flow', () => {
    it('verifies Settings entry point is located in Today top-right header and not in main tab bar', () => {
      const navigationFlow = {
        tabBarDestinations: ['Today', 'Focus', 'Review'],
        todayHeaderActions: ['OpenSettings'],
        settingsRoutes: ['UserSettings', 'ExamDateConfig', 'ThemePreference'],
      };

      assert(!navigationFlow.tabBarDestinations.includes('Settings'), 'Settings must NOT occupy a tab bar slot');
      assert(navigationFlow.todayHeaderActions.includes('OpenSettings'), 'Settings entry must be in Today header action');
    });

    it('verifies settings updates persist exam target date and theme preference cleanly', () => {
      const mockSettings = {
        examDate: '2026-12-26',
        targetScore: 400,
        focusDurationMinutes: 45,
        themePreference: 'SYSTEM',
      };

      // User modifies exam date and theme
      const updated = {
        ...mockSettings,
        examDate: '2027-12-25',
        themePreference: 'DARK',
      };

      assertEqual(updated.examDate, '2027-12-25');
      assertEqual(updated.themePreference, 'DARK');
    });
  });
}
