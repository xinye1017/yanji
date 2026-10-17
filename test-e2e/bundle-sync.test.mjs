import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { runGuard } from '../scripts/check-bundle-sync.mjs';

/**
 * Self-tests for the bundle sync guard.
 *
 * The guard exists to catch one specific failure: source changed, the committed
 * Metro bundle did not. A guard that cannot fail would be worse than none — it
 * would advertise a check that is not happening — so these tests drive the real
 * `runGuard` against synthetic trees and prove each branch fires.
 */

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

const SYMBOLS = ['YanjiDayRhythmChart', 'measuredRhythmHours', 'rhythmChartHeight',
  'rhythmSummary', 'YanjiDecisionModelSection', 'examCountdownText'];
const REMOVED = ['YanjiMiniRhythmSparkline', 'rhythmBarWidth', 'rhythmBarHeight'];

function treeWith(bundleContent) {
  const root = mkdtempSync(path.join(tmpdir(), 'yanji-bundle-guard-'));
  const target = path.join(root, 'app', 'src', 'main', 'assets');
  mkdirSync(target, { recursive: true });
  if (bundleContent !== null) {
    writeFileSync(path.join(target, 'index.android.bundle'), bundleContent, 'utf8');
  }
  return { root, cleanup: () => rmSync(root, { recursive: true, force: true }) };
}

test('the real committed bundle passes', async () => {
  const { errors } = await runGuard(repoRoot);
  assert.deepEqual(errors, [], `the committed bundle must match the source:\n${errors.join('\n')}`);
});

test('a bundle missing a new symbol fails with the defining file named', async () => {
  const tree = treeWith(`var x = ${SYMBOLS.join(' ')};`);
  const { errors } = await runGuard(tree.root);
  // Every symbol is present, so this passes — the negative case is below.
  assert.deepEqual(errors, []);
  tree.cleanup();

  const missing = treeWith(SYMBOLS.slice(1).join(' '));
  const { errors: missingErrors } = await runGuard(missing.root);
  assert.ok(
    missingErrors.some(e => e.includes('YanjiDayRhythmChart') && e.includes('YanjiUI.tsx')),
    missingErrors.join('\n'),
  );
  missing.cleanup();
});

test('a bundle still carrying a removed symbol fails', async () => {
  const tree = treeWith(`${SYMBOLS.join(' ')} ${REMOVED[0]}`);
  const { errors } = await runGuard(tree.root);
  assert.ok(errors.some(e => e.includes(REMOVED[0]) && e.includes('removed symbol')), errors.join('\n'));
  tree.cleanup();
});

test('a missing bundle fails instead of silently passing', async () => {
  // The release build packages this file directly; without it the APK ships no
  // JavaScript at all, so absence must be an error rather than "nothing to check".
  const tree = treeWith(null);
  const { errors } = await runGuard(tree.root);
  assert.ok(errors.some(e => e.includes('Committed bundle is missing')), errors.join('\n'));
  tree.cleanup();
});
