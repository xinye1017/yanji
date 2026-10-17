#!/usr/bin/env node

/**
 * Guard: the committed Metro bundle must match the committed source.
 *
 * Why this exists. `app/src/main/assets/index.android.bundle` is checked in
 * because `release.yml` builds the signed APK with `assembleRelease` alone — no
 * bundling step — so the file has to be present in the tree for a release to
 * contain JavaScript at all. (The quality workflow regenerates it, but that is
 * not the path a release takes.)
 *
 * A committed build artifact carries one real risk: it can silently drift from
 * the source it was built from, and then the APK ships code that no longer
 * exists in `src/`. A stale bundle is invisible in review (it is one minified
 * line) and would only show up as "the fix isn't in the app" on a device.
 *
 * This guard closes that gap cheaply: every component and function name changed
 * recently must be either present in the bundle (a new symbol) or absent from it
 * (a removed symbol). It is a *presence* check, not a semantic one — it catches
 * "forgot to rebuild", which is the failure mode that actually happens.
 *
 * Run: `node scripts/check-bundle-sync.mjs`
 */

import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const bundlePath = 'app/src/main/assets/index.android.bundle';

/**
 * Symbols that MUST be in the bundle, with the source file that defines them.
 *
 * Each entry is a rename or a new export: if the bundle lacks the new name, it
 * was not rebuilt after that change. Keep this list short and current — it is a
 * tripwire for the last few UI refactors, not a mirror of the whole codebase.
 */
const requiredSymbols = [
  // The 今日学习节奏 rebuild.
  { symbol: 'YanjiDayRhythmChart', source: 'src/components/YanjiUI.tsx' },
  { symbol: 'measuredRhythmHours', source: 'src/components/YanjiUI.tsx' },
  { symbol: 'rhythmChartHeight', source: 'src/theme/tokens.ts' },
  { symbol: 'rhythmSummary', source: 'src/screens/todayViewLogic.ts' },
  // The settings rebuild.
  { symbol: 'YanjiDecisionModelSection', source: 'src/components/DecisionModelSection.tsx' },
  // Module paths are not preserved by Metro, so this checks an export instead:
  // `examCountdownText` is the formatter Today and Settings now share.
  { symbol: 'examCountdownText', source: 'src/state/settingsViewLogic.ts' },
];

/**
 * Symbols that must NOT be in the bundle: an old name still shipping there means
 * the artifact predates the rename that removed it.
 */
const forbiddenSymbols = ['YanjiMiniRhythmSparkline', 'rhythmBarWidth', 'rhythmBarHeight'];

export async function runGuard(root = scriptRoot) {
  const errors = [];
  let bundle;
  try {
    bundle = await readFile(path.join(root, bundlePath), 'utf8');
  } catch {
    errors.push(
      `Committed bundle is missing: ${bundlePath}. ` +
      'The release build packages this file directly, so a fresh clone cannot produce a working APK without it. ' +
      'Regenerate it with `npm run bundle:android`.',
    );
    return { errors };
  }

  for (const { symbol, source } of requiredSymbols) {
    if (!bundle.includes(symbol)) {
      errors.push(
        `Bundle is stale: it does not contain \`${symbol}\` (defined in ${source}). ` +
        'The committed artifact predates that change — run `npm run bundle:android` and commit the result.',
      );
    }
  }

  for (const symbol of forbiddenSymbols) {
    if (bundle.includes(symbol)) {
      errors.push(
        `Bundle is stale: it still contains the removed symbol \`${symbol}\`. ` +
        'Run `npm run bundle:android` so the shipped JS matches the source.',
      );
    }
  }

  return { errors };
}

const invokedDirectly = process.argv[1] &&
  path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (invokedDirectly) {
  const { errors } = await runGuard();
  if (errors.length > 0) {
    console.error('Bundle sync guard: FAIL');
    for (const error of errors) console.error(`  - ${error}`);
    process.exit(1);
  }
  console.log('Bundle sync guard: PASS (committed bundle matches the current source).');
}
