import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { runGuard } from '../scripts/check-rn-design-tokens.mjs';

/**
 * F07/T9 — self-tests for the RN design-token guard.
 *
 * The retired Compose guard carried explicit self-checks precisely because a
 * pattern bug had once produced a permanently green guard ("永远 0 命中、永远
 * 显示绿色通过"). These tests keep that guarantee for its replacement by driving
 * the **real** `runGuard` against a synthetic tree:
 *
 *  1. the real repository tree passes;
 *  2. a minimal tree with a correct token contract passes;
 *  3. each forbidden-use rule fires on an otherwise-valid-TS violation;
 *  4. a vanished scanned root fails instead of silently scanning nothing.
 *
 * Every mutation targets an **exact literal** declared next to the fixture it
 * edits. A regex-shaped mutation that silently stops matching turns the test
 * into a no-op that still reports green, which is the failure mode this suite
 * exists to prevent — so each fixture block below is a named constant and each
 * mutation replaces that constant verbatim.
 *
 * The fixture tree is dependency-free — the guard only uses `node:fs/promises`,
 * `node:path`, `node:url` plus Node's type stripping — which doubles as the F14
 * proof that the CI guards job needs no `npm ci`.
 */

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

// A `null` override means "do not create this file", so a test can prove the
// guard fails on a missing target instead of silently falling back to a default.
function fixtureTree(overrides = {}) {
  const root = mkdtempSync(path.join(tmpdir(), 'yanji-token-guard-'));
  const files = {
    'src/theme/tokens.ts': tokensSource,
    'src/theme/ThemeProvider.tsx': providerSource,
    'src/components/YanjiUI.tsx': sharedUiSource,
    'src/navigation/BottomTabBar.tsx': tabSource,
    'src/screens/TodayScreen.tsx': screenSource,
    'src/state/store.ts': stateSource,
    'App.tsx': appSource,
    ...overrides,
  };
  for (const [relative, content] of Object.entries(files)) {
    if (content === null) continue;
    const target = path.join(root, ...relative.split('/'));
    mkdirSync(path.dirname(target), { recursive: true });
    writeFileSync(target, content, 'utf8');
  }
  return {
    root,
    cleanup: () => rmSync(root, { recursive: true, force: true }),
  };
}

const paperDarkPalette = `  dark: { canvas: '#111315', surface: '#1a1d21', subtle: '#23272d', ink: '#eae6df',
    secondary: '#9ba0a8', muted: '#636870', hairline: 'rgba(234,230,223,0.08)',
    brand: '#e06d53', brandInk: '#e06d53', onBrand: '#111315', amber: '#dda059',
    scrim: 'rgba(0,0,0,0.56)', danger: '#f87171' },
`;

const pausedImmersivePalette = `  paused: { canvas: '#1c1917', ink: '#f6f4ef', secondary: '#9ba0a8', accent: '#dda059' },
`;

const providerImport = `import { YanjiRadius, YanjiSpacing, YanjiTouch,
  YanjiMotion, YanjiShadow, YanjiPaperColors, YanjiPaperImmersive, YanjiPaperRadius,
  YanjiPaperLayout, YanjiPaperMotion, YanjiPaperTypography } from './tokens';
`;

const tokensSource = `
export const YanjiRadius = { sm: 8, pill: 9999 };
export const YanjiSpacing = { sm: 8 };
export const YanjiTouch = { min: 44, comfortable: 48 };
export const YanjiMotion = { duration: { fast: 120 } };
export const YanjiShadow = { sheet: () => ({}) };
export const YanjiPaperColors = {
  light: { canvas: '#f6f4ef', surface: '#ffffff', subtle: '#edeae2', ink: '#1c1e21',
    secondary: '#5c5f66', muted: '#94969c', hairline: 'rgba(28,30,33,0.08)',
    brand: '#c84b31', brandInk: '#b23a22', onBrand: '#ffffff', amber: '#9a6100',
    scrim: 'rgba(28,30,33,0.32)', danger: '#be3232' },
${paperDarkPalette}};
export const YanjiPaperImmersive = {
  running: { canvas: '#15181c', ink: '#f6f4ef', secondary: '#9ba0a8', accent: '#e06d53' },
${pausedImmersivePalette}};
export const YanjiPaperRadius = { tick: 1, pill: 9999 };
export const YanjiPaperLayout = { pageGutter: 22, hairlineWidth: 1 };
export const YanjiPaperMotion = { pressMs: 120 };
export const YanjiPaperTypography = { bodyPrimary: { fontSize: 14 } };
`;

const providerSource = `
${providerImport}export function YanjiThemeProvider() { return null; }
export function useYanjiTheme() {
  return { isDark: false, mode: 'SYSTEM',
    radius: YanjiRadius, spacing: YanjiSpacing,
    layout: {}, touch: YanjiTouch, motion: YanjiMotion,
    shadow: YanjiShadow,
    paper: { colors: YanjiPaperColors.light, immersive: YanjiPaperImmersive,
      typography: YanjiPaperTypography, layout: YanjiPaperLayout,
      radius: YanjiPaperRadius, motion: YanjiPaperMotion } };
}
`;

const sharedUiSource = `
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiRadius, YanjiSpacing, YanjiTouch } from '../theme/tokens';
export function YanjiThing() {
  const theme = useYanjiTheme();
  const paper = theme.paper;
  return { radius: YanjiRadius.sm,
    spacing: YanjiSpacing.sm, touch: YanjiTouch.min,
    motion: theme.motion.duration, shadow: theme.shadow.sheet,
    paperColors: paper.colors.canvas, paperRadius: paper.radius.pill,
    paperLayout: paper.layout.pageGutter, paperTypography: paper.typography.bodyPrimary,
    paperMotion: paper.motion.pressMs };
}
`;

const tabSource = `
import { useYanjiTheme } from '../theme/ThemeProvider';
export function BottomTabBar() {
  const theme = useYanjiTheme();
  return theme.motion.duration.fast;
}
`;

const screenSource = `
import { useYanjiTheme } from '../theme/ThemeProvider';
import { YanjiSpacing } from '../theme/tokens';
export function Screen() {
  const theme = useYanjiTheme();
  const paper = theme.paper;
  return { radius: theme.radius.sm,
    spacing: YanjiSpacing.sm, touch: theme.touch.min,
    motion: theme.motion.duration, shadow: theme.shadow.sheet,
    paperColors: paper.colors.surface, paperRadius: paper.radius.tick,
    paperLayout: paper.layout.pageGutter, paperTypography: paper.typography.bodyPrimary,
    paperMotion: paper.motion.pressMs, immersive: paper.immersive.running.canvas,
    // Computed capsule geometry, not a raw radius literal.
    capsule: { borderRadius: 24 / 2 } };
}
`;

const stateSource = `
import { YanjiSpacing } from '../theme/tokens';
export const layout = { gutter: YanjiSpacing.sm };
`;

const appSource = `
import { YanjiThemeProvider } from './src/theme/ThemeProvider';
export default function App() { return YanjiThemeProvider; }
`;

test('the real repository tree passes the guard', async () => {
  const { errors, scanned } = await runGuard(repoRoot);
  assert.deepEqual(errors, [], `the real tree must be clean:\n${errors.join('\n')}`);
  assert.ok(scanned >= 22, `expected the whole-src scan surface, saw ${scanned}`);
});

test('a correct minimal token contract passes with no node_modules present', async () => {
  const tree = fixtureTree();
  const { errors } = await runGuard(tree.root);
  assert.deepEqual(errors, [], `fixture tree should pass:\n${errors.join('\n')}`);
  tree.cleanup();
});

test('dropping the paper dark palette fails instead of silently passing', async () => {
  const tree = fixtureTree({
    'src/theme/tokens.ts': tokensSource.replace(paperDarkPalette, ''),
  });
  const { errors } = await runGuard(tree.root);
  assert.ok(errors.some(e => e.includes('YanjiPaperColors must provide both light and dark')), errors.join('\n'));
  tree.cleanup();
});

test('emptying a required token scale fails', async () => {
  const tree = fixtureTree({
    'src/theme/tokens.ts': tokensSource
      .replace('export const YanjiTouch = { min: 44, comfortable: 48 };', 'export const YanjiTouch = {};'),
  });
  const { errors } = await runGuard(tree.root);
  assert.ok(errors.some(e => e.includes('YanjiTouch')), errors.join('\n'));
  tree.cleanup();
});

test('a missing immersive state fails', async () => {
  const tree = fixtureTree({
    'src/theme/tokens.ts': tokensSource.replace(pausedImmersivePalette, ''),
  });
  const { errors } = await runGuard(tree.root);
  assert.ok(errors.some(e => e.includes('YanjiPaperImmersive must define the paused palette')), errors.join('\n'));
  tree.cleanup();
});

test('light and dark paper palettes must expose the same roles', async () => {
  const tree = fixtureTree({
    'src/theme/tokens.ts': tokensSource.replace("brandInk: '#b23a22', ", ''),
  });
  const { errors } = await runGuard(tree.root);
  assert.ok(errors.some(e => e.includes('same semantic color roles')), errors.join('\n'));
  tree.cleanup();
});

test('a hardcoded hex colour in a consumer fails with a file:line anchor', async () => {
  const tree = fixtureTree({
    'src/screens/TodayScreen.tsx': screenSource.replace(
      "capsule: { borderRadius: 24 / 2 } };",
      "capsule: { backgroundColor: '#C84B31' } };"),
  });
  const { errors } = await runGuard(tree.root);
  assert.ok(errors.some(e => /TodayScreen\.tsx:\d+ hardcodes a hex colour/.test(e)), errors.join('\n'));
  tree.cleanup();
});

test('the bridge layer is scanned, not skipped', async () => {
  // F06: src/bridge ships real code, so a literal there must not escape.
  const tree = fixtureTree({
    'src/bridge/index.ts': "export const noteTint = '#C84B31';\n",
  });
  const { errors } = await runGuard(tree.root);
  assert.ok(errors.some(e => /bridge[\\/]index\.ts:\d+ hardcodes a hex colour/.test(e)), errors.join('\n'));
  tree.cleanup();
});

test('the token-definition exemption is per file, not per directory', async () => {
  // tokens.ts owns its hex literals …
  const exempt = fixtureTree({
    'src/theme/tokens.ts': tokensSource.replace(
      'export const YanjiRadius',
      "export const swatch = '#ABCDEF';\nexport const YanjiRadius"),
  });
  assert.deepEqual(await runGuard(exempt.root).then(r => r.errors), []);
  exempt.cleanup();

  // … while a different file under src/theme is an ordinary consumer. Without
  // this half, a directory-wide exemption would pass unnoticed.
  const consumer = fixtureTree({
    'src/theme/experiment.ts': "export const swatch = '#ABCDEF';\n",
  });
  const { errors } = await runGuard(consumer.root);
  assert.ok(errors.some(e => /theme[\\/]experiment\.ts:\d+ hardcodes a hex colour/.test(e)), errors.join('\n'));
  consumer.cleanup();
});

test('a reasoned token exemption passes and an empty one fails', async () => {
  const exempt = fixtureTree({
    'src/screens/TodayScreen.tsx': screenSource.replace(
      "capsule: { borderRadius: 24 / 2 } };",
      "capsule: { backgroundColor: '#C84B31' } }; // token-exempt: decorative spectrum swatch"),
  });
  assert.deepEqual(await runGuard(exempt.root).then(r => r.errors), []);
  exempt.cleanup();

  const empty = fixtureTree({
    'src/screens/TodayScreen.tsx': screenSource.replace(
      "capsule: { borderRadius: 24 / 2 } };",
      "capsule: { backgroundColor: '#C84B31' } }; // token-exempt:"),
  });
  const { errors } = await runGuard(empty.root);
  assert.ok(errors.some(e => e.includes('empty token-exempt reason')), errors.join('\n'));
  empty.cleanup();
});

test('reading a static light palette outside the provider fails', async () => {
  const tree = fixtureTree({
    'src/screens/TodayScreen.tsx': screenSource.replace(
      'const theme = useYanjiTheme();',
      "import { YanjiPaperColors } from '../theme/tokens';\n  const theme = useYanjiTheme();\n  const forced = YanjiPaperColors.light;"),
  });
  const { errors } = await runGuard(tree.root);
  assert.ok(errors.some(e => e.includes('reads a static light/dark palette directly')), errors.join('\n'));
  tree.cleanup();
});

test('a numeric radius literal fails while computed capsule geometry passes', async () => {
  const numeric = fixtureTree({
    'src/screens/TodayScreen.tsx': screenSource.replace(
      "capsule: { borderRadius: 24 / 2 } };", "capsule: { borderRadius: 24 } };"),
  });
  const { errors } = await runGuard(numeric.root);
  assert.ok(errors.some(e => e.includes('uses a numeric radius')), errors.join('\n'));
  numeric.cleanup();

  // A trailing comment must not launder the literal into "computed geometry".
  const commented = fixtureTree({
    'src/screens/TodayScreen.tsx': screenSource.replace(
      "capsule: { borderRadius: 24 / 2 } };", "capsule: { borderRadius: 24 } }; // legacy pill"),
  });
  const commentedErrors = await runGuard(commented.root).then(r => r.errors);
  assert.ok(commentedErrors.some(e => e.includes('uses a numeric radius')), commentedErrors.join('\n'));
  commented.cleanup();
});

test('computed radius arithmetic in every operator form stays valid', async () => {
  // Multi-digit operands are the backtracking trap: a pattern that matched a
  // leading `\d+` would read `100 / 2` as the literal 10 and fail here.
  for (const expression of [
    'height / 2', 'size * 0.5', 'width - 4', 'radius + 1',
    '100 / 2', '48 * 0.5', '120 - 6', '1000 / 10',
  ]) {
    const tree = fixtureTree({
      'src/screens/TodayScreen.tsx': screenSource.replace(
        "capsule: { borderRadius: 24 / 2 } };", `capsule: { borderRadius: ${expression} } };`),
    });
    const { errors } = await runGuard(tree.root);
    assert.deepEqual(errors, [], `borderRadius: ${expression} is computed geometry`);
    tree.cleanup();
  }
});

test('the root app shell is scanned, not skipped', async () => {
  const tree = fixtureTree({
    'App.tsx': appSource.replace('export default function App()',
      "const dock = { borderRadius: 18 };\nexport default function App()"),
  });
  const { errors } = await runGuard(tree.root);
  assert.ok(errors.some(e => /App\.tsx:\d+ uses a numeric radius/.test(e)), errors.join('\n'));
  tree.cleanup();
});

test('a vanished scanned root fails instead of silently scanning nothing', async () => {
  const tree = fixtureTree();
  // An existing but empty root: the guard must say so rather than scan nothing.
  rmSync(path.join(tree.root, 'src'), { recursive: true, force: true });
  mkdirSync(path.join(tree.root, 'src'), { recursive: true });
  const { errors } = await runGuard(tree.root);
  assert.ok(errors.some(e => e.includes('found no TypeScript files under src')), errors.join('\n'));
  tree.cleanup();
});

test('disconnecting the provider from a token scale fails', async () => {
  // Drop the import *and* its only use: a mutation that removed just the import
  // would leave `YanjiPaperColors.light` in the body and prove nothing.
  const tree = fixtureTree({
    'src/theme/ThemeProvider.tsx': providerSource
      .replace(providerImport, "import { YanjiRadius, YanjiSpacing } from './tokens';")
      .replace('colors: YanjiPaperColors.light', 'colors: {}'),
  });
  const { errors } = await runGuard(tree.root);
  assert.ok(errors.some(e => e.includes('ThemeProvider uses YanjiPaperColors')), errors.join('\n'));
  tree.cleanup();
});

test('a missing required source file fails', async () => {
  const tree = fixtureTree({ 'src/navigation/BottomTabBar.tsx': null });
  const { errors } = await runGuard(tree.root);
  assert.ok(errors.some(e => e.includes('BottomTabBar.tsx')), errors.join('\n'));
  tree.cleanup();
});

test('the guard resolves its fixture root, not the repository root', async () => {
  // Guard against a fixture tree accidentally validating the real sources.
  const tree = fixtureTree({ 'src/theme/tokens.ts': 'export const YanjiRadius = {};\n' });
  const { errors } = await runGuard(tree.root);
  assert.ok(errors.length > 0, 'a broken fixture must fail even though the real tree passes');
  assert.ok(errors.every(e => !e.includes('found no TypeScript files under src')),
    'the fixture must be the tree under test');
  tree.cleanup();
});
