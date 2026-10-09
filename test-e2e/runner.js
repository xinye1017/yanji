#!/usr/bin/env node
/**
 * Yanji E2E Test Suite - Main Executable Runner
 * Opaque-box requirement-driven test suite for 「研迹」 React Native refactoring.
 * Executes Tiers 1-4 covering all 21 features from PROJECT.md.
 */

import { harness } from './framework/harness.js';
import { registerM1Tests } from './tier1-features/m1-infra-bundle.test.js';
import { registerM2Tests } from './tier1-features/m2-native-bridge.test.js';
import { registerM3Tests } from './tier1-features/m3-design-navigation.test.js';
import { registerM4Tests } from './tier1-features/m4-today-focus-moment.test.js';
import { registerM5Tests } from './tier1-features/m5-review-ai-e2e.test.js';
import { registerShippedTokenTests } from './tier1-features/m8-shipped-tokens.test.js';
import { registerReviewViewLogicTests } from './tier1-features/m9-review-view-logic.test.js';
import { registerTier2Tests } from './tier2-boundaries/boundaries.test.js';
import { registerTier3Tests } from './tier3-interactions/interactions.test.js';
import { registerTier4Tests } from './tier4-workflows/workflows.test.js';

async function main() {
  // Register Tier 1: Feature Coverage (Features 1 to 21)
  harness.setTier('Tier 1');
  registerM1Tests();
  registerM2Tests();
  registerM3Tests();
  registerM4Tests();
  registerM5Tests();
  registerShippedTokenTests();
  registerReviewViewLogicTests();

  // Register Tier 2: Boundary & Corner Cases
  harness.setTier('Tier 2');
  registerTier2Tests();

  // Register Tier 3: Cross-Feature Interactions
  harness.setTier('Tier 3');
  registerTier3Tests();

  // Register Tier 4: Real-World Workflows
  harness.setTier('Tier 4');
  registerTier4Tests();

  // Execute test suite
  const success = await harness.runAll();
  if (!success) {
    process.exit(1);
  }
  process.exit(0);
}

main().catch(err => {
  console.error('Fatal runner execution error:', err);
  process.exit(1);
});
