#!/usr/bin/env node

import { readdir, readFile, stat } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const scriptRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const requiredFiles = [
  'src/theme/tokens.ts',
  'src/theme/ThemeProvider.tsx',
  'src/components/YanjiUI.tsx',
  'src/navigation/BottomTabBar.tsx',
];
// Every TypeScript file under src, not a hand-picked subset: the app shell, the
// state layer, the bridge and the theme directory all ship real code, so a
// violation in any of them must not silently escape the scan. Exceptions are
// per-file below (token definitions own their literals; the provider binds them).
const sourceRoots = ['src'];
const rootComponents = ['App.tsx'];
// Token definitions own the only legitimate hex literals; the provider binds
// them to the theme; the frozen glass material keeps its own decorative RGB.
const tokenDefinitionFiles = new Set(['src/theme/tokens.ts', 'src/theme/liquidGlass.ts']);

/**
 * Run the whole contract against `root` and return the collected failures.
 *
 * Exported so the guard's own self-tests can drive the real rules against a
 * synthetic tree instead of re-implementing them — a guard whose self-test
 * copies its patterns would go green the moment the real one broke.
 */
export async function runGuard(root = scriptRoot) {
  const errors = [];

  const fail = message => {
    errors.push(message);
  };

async function readRequired(relativePath) {
  const absolutePath = path.join(root, relativePath);
  try {
    const fileStat = await stat(absolutePath);
    if (!fileStat.isFile()) {
      fail(`Required source target is not a file: ${relativePath}`);
      return '';
    }
    return await readFile(absolutePath, 'utf8');
  } catch {
    fail(`Required source target is missing: ${relativePath}`);
    return '';
  }
}

async function collectTypeScriptFiles(relativeRoot) {
  const absoluteRoot = path.join(root, relativeRoot);
  let entries;
  try {
    entries = await readdir(absoluteRoot, { withFileTypes: true });
  } catch {
    fail(`Required source directory is missing: ${relativeRoot}`);
    return [];
  }

  const files = [];
  for (const entry of entries) {
    // POSIX separators, so the per-file exception sets below compare equal on
    // Windows too (`path.join` would otherwise yield `src\theme\tokens.ts`).
    const relativePath = path.posix.join(relativeRoot, entry.name);
    if (entry.isDirectory()) {
      files.push(...await collectTypeScriptFiles(relativePath));
    } else if (entry.isFile() && /\.(?:ts|tsx)$/.test(entry.name)) {
      files.push(relativePath);
    }
  }
  return files;
}

function requireSourceMatch(source, pattern, description) {
  if (!pattern.test(source)) {
    fail(`RN design token contract is disconnected: ${description}`);
  }
}

const sourceByPath = new Map();
for (const relativePath of requiredFiles) {
  sourceByPath.set(relativePath, await readRequired(relativePath));
}

const componentFiles = [];
for (const sourceRoot of sourceRoots) {
  const files = await collectTypeScriptFiles(sourceRoot);
  if (files.length === 0) {
    fail(`RN source scan found no TypeScript files under ${sourceRoot}`);
  }
  componentFiles.push(...files);
}
for (const rootComponent of rootComponents) {
  try {
    const rootStat = await stat(path.join(root, rootComponent));
    if (!rootStat.isFile()) fail(`Required root component is not a file: ${rootComponent}`);
    else componentFiles.push(rootComponent);
  } catch {
    fail(`Required root component is missing: ${rootComponent}`);
  }
}

const sourceEntries = [];
for (const relativePath of componentFiles) {
  try {
    sourceEntries.push([
      relativePath,
      await readFile(path.join(root, ...relativePath.split('/')), 'utf8'),
    ]);
  } catch {
    fail(`RN source disappeared during scan: ${relativePath}`);
  }
}
const scannedSource = sourceEntries.map(([, source]) => source).join('\n');
const providerSource = sourceByPath.get('src/theme/ThemeProvider.tsx');
const sharedUiSource = sourceByPath.get('src/components/YanjiUI.tsx');
const tabSource = sourceByPath.get('src/navigation/BottomTabBar.tsx');

let tokens;
try {
  tokens = await import(pathToFileURL(path.join(root, 'src/theme/tokens.ts')).href);
} catch (error) {
  fail(`Unable to load src/theme/tokens.ts with the configured Node runtime: ${error.message}`);
}

if (tokens) {
  for (const name of [
    'YanjiRadius',
    'YanjiSpacing',
    'YanjiTouch',
    'YanjiMotion',
    'YanjiShadow',
  ]) {
    const value = tokens[name];
    if (!value || typeof value !== 'object' || Object.keys(value).length === 0) {
      fail(`Required RN token scale is missing or empty: ${name}`);
    }
  }

  // 素笺与寸阴 paper domain — the only colour and type vocabulary. It carries
  // the whole screen: body surfaces, shared controls and the navigation dock.
  const paperLight = tokens.YanjiPaperColors?.light;
  const paperDark = tokens.YanjiPaperColors?.dark;
  if (!paperLight || !paperDark || typeof paperLight !== 'object' || typeof paperDark !== 'object') {
    fail('YanjiPaperColors must provide both light and dark semantic palettes.');
  } else {
    const paperLightKeys = Object.keys(paperLight).sort();
    const paperDarkKeys = Object.keys(paperDark).sort();
    if (JSON.stringify(paperLightKeys) !== JSON.stringify(paperDarkKeys)) {
      fail('Paper light and dark palettes must expose the same semantic color roles.');
    }
    for (const role of [
      'canvas', 'surface', 'subtle', 'ink', 'secondary', 'muted', 'hairline',
      'brand', 'brandInk', 'onBrand', 'amber', 'scrim', 'danger', 'dangerSubtle',
    ]) {
      if (typeof paperLight[role] !== 'string' || typeof paperDark[role] !== 'string') {
        fail(`Both paper palettes must define the ${role} semantic role.`);
      }
    }
  }
  for (const name of ['YanjiPaperRadius', 'YanjiPaperLayout', 'YanjiPaperMotion', 'YanjiPaperTypography']) {
    const value = tokens[name];
    if (!value || typeof value !== 'object' || Object.keys(value).length === 0) {
      fail(`Required paper token scale is missing or empty: ${name}`);
    }
  }
  // The immersion field owns the whole screen during a running/paused session,
  // including the area behind the status bar.
  for (const state of ['running', 'paused']) {
    const immersive = tokens.YanjiPaperImmersive?.[state];
    if (!immersive || typeof immersive !== 'object' || Object.keys(immersive).length === 0) {
      fail(`YanjiPaperImmersive must define the ${state} palette.`);
    } else {
      for (const role of ['canvas', 'ink', 'secondary', 'accent']) {
        if (typeof immersive[role] !== 'string') {
          fail(`YanjiPaperImmersive.${state} must define the ${role} semantic role.`);
        }
      }
    }
  }
  if (!Number.isFinite(tokens.YanjiPaperLayout?.hairlineWidth) || tokens.YanjiPaperLayout.hairlineWidth < 0) {
    fail('YanjiPaperLayout.hairlineWidth must stay a non-negative geometry value.');
  }

  if (!Number.isFinite(tokens.YanjiTouch?.min) || tokens.YanjiTouch.min < 44) {
    fail('YanjiTouch.min must preserve the established 44-point minimum target.');
  }
  if (!Number.isFinite(tokens.YanjiTouch?.comfortable) ||
      tokens.YanjiTouch.comfortable < tokens.YanjiTouch.min) {
    fail('YanjiTouch.comfortable must be at least YanjiTouch.min.');
  }
}

for (const tokenName of [
  'YanjiRadius',
  'YanjiSpacing',
  'YanjiTouch',
  'YanjiMotion',
  'YanjiShadow',
  'YanjiPaperColors',
  'YanjiPaperImmersive',
  'YanjiPaperRadius',
  'YanjiPaperLayout',
  'YanjiPaperMotion',
  'YanjiPaperTypography',
]) {
  requireSourceMatch(providerSource, new RegExp(`\\b${tokenName}\\b`), `ThemeProvider uses ${tokenName}`);
}
for (const category of ['radius', 'spacing', 'touch', 'motion', 'shadow']) {
  requireSourceMatch(providerSource, new RegExp(`\\b${category}:`), `ThemeProvider exposes ${category}`);
}
requireSourceMatch(providerSource, /\bpaper:\s*\{/, 'ThemeProvider exposes the paper domain');
for (const paperCategory of ['colors', 'immersive', 'typography', 'layout', 'radius', 'motion']) {
  requireSourceMatch(providerSource, new RegExp(`\\b${paperCategory}:`), `ThemeProvider exposes paper.${paperCategory}`);
}
requireSourceMatch(providerSource, /\buseYanjiTheme\b/, 'ThemeProvider exports useYanjiTheme');

for (const [description, pattern, sharedFileToo] of [
  ['semantic radii', /\bYanjiRadius\./, true],
  ['semantic spacing', /\bYanjiSpacing\./, true],
  ['minimum touch targets', /\bYanjiTouch\./, true],
  ['theme motion', /\btheme\.motion\./, true],
  ['theme shadows', /\btheme\.shadow\./, true],
  ['paper colors', /\bpaper\.colors\./, true],
  ['paper radii', /\bpaper\.radius\./, true],
  ['paper layout', /\bpaper\.layout\./, true],
  ['paper typography', /\bpaper\.typography\./, true],
  ['paper motion', /\bpaper\.motion\./, true],
  // The immersion field belongs to the Focus screen alone; the shared component
  // file has no immersive surface, so demanding it there would force an unused
  // import. The union scan plus the per-file forbidden-use rules still cover it.
  ['immersive palettes', /\bpaper\.immersive\./, false],
]) {
  if (sharedFileToo) {
    requireSourceMatch(sharedUiSource, pattern, `shared RN components use ${description}`);
  }
  requireSourceMatch(scannedSource, pattern, `RN source scan finds ${description}`);
}

requireSourceMatch(sharedUiSource, /\buseYanjiTheme\b/, 'shared RN components use the theme provider');
requireSourceMatch(tabSource, /\bYanjiLiquidGlass\./, 'navigation uses the shared glass geometry/material tokens');

// ---------------------------------------------------------------------------
// Per-file forbidden-use rules.
//
// These are the RN equivalent of the retired Compose guard's hard rules 1 and 3:
// no inline colour literal and no static light-mode token at a call site. They
// are checked **per file**, so deleting the last consumer of a scale cannot be
// masked by a single surviving occurrence elsewhere.
//
// Scope notes:
//   - Only hex literals are forbidden. `rgba(...)` decorative/material values
//     (hairlines, scrims, glass tints) stay valid geometry/material, exactly as
//     the retired guard allowed them.
//   - Computed geometry such as `borderRadius: height / 2` or
//     `borderRadius: 100 / 2` is valid capsule geometry, not a raw radius
//     literal, so the value must be numeric in full to be flagged.
//   - Token-definition files and the frozen glass material own their values;
//     that exemption is per file, so a new file under src/theme is still held
//     to the same rules as any other consumer.
// ---------------------------------------------------------------------------

const hexColorPattern = /#[0-9A-Fa-f]{3,8}\b/;
const staticPalettePattern = /\b(?:YanjiPaperColors|YanjiPaperImmersive)\.(?:light|dark|running|paused)\b/;
// A numeric radius literal is flagged only when the number IS the whole value.
// Computed geometry such as `borderRadius: height / 2` or `borderRadius: 100 / 2`
// is valid capsule geometry, not a raw literal, so the captured value must be
// purely numeric. Capturing the whole value (instead of matching a leading
// `\d+`) is what keeps `100 / 2` from being read as the literal `10`.
// A numeric radius literal is flagged only when the number IS the whole value.
// Computed geometry such as `borderRadius: height / 2` or `borderRadius: 100 / 2`
// is valid capsule geometry, not a raw literal, so the captured value must be
// purely numeric. Capturing the whole value (instead of matching a leading
// `\d+`) is what keeps `100 / 2` from being read as the literal `10`.
const rawRadiusAssignment =
  /\b(?:borderRadius|borderTopLeftRadius|borderTopRightRadius|borderBottomLeftRadius|borderBottomRightRadius)\s*:\s*([^,;\n}]+)/g;
// A trailing line comment is not part of the value; `borderRadius: 24 // legacy`
// is still the literal 24.
const lineComment = /\/\/.*$/;
const purelyNumeric = value => /^-?\d+(?:\.\d+)?$/.test(value.replace(lineComment, '').trim());

for (const [relativePath, source] of sourceEntries) {
  const isTokenDefinition = tokenDefinitionFiles.has(relativePath);
  const isProvider = relativePath === 'src/theme/ThemeProvider.tsx';
  source.split(/\r?\n/).forEach((line, index) => {
    if (/^\s*(?:\/\/|\*|\*\/)/.test(line)) return;
    const lineNumber = index + 1;
    const exemption = line.match(/\/\/\s*token-exempt:\s*(.*?)\s*$/);
    if (exemption && exemption[1].trim().length === 0) {
      fail(`${relativePath}:${lineNumber} has an empty token-exempt reason.`);
      return;
    }
    const exempt = Boolean(exemption);
    if (!isTokenDefinition && !exempt && hexColorPattern.test(line)) {
      fail(`${relativePath}:${lineNumber} hardcodes a hex colour; reference a semantic token or add // token-exempt: <reason>.`);
    }
    if (!isProvider && !isTokenDefinition && !exempt && staticPalettePattern.test(line)) {
      fail(`${relativePath}:${lineNumber} reads a static light/dark palette directly; resolve it through useYanjiTheme so dark mode survives.`);
    }
    if (!exempt) {
      // Every assignment on the line, not just the first: an inline style can
      // legitimately carry two radii.
      for (const radiusAssignment of line.matchAll(rawRadiusAssignment)) {
        if (purelyNumeric(radiusAssignment[1])) {
          fail(`${relativePath}:${lineNumber} uses a numeric radius; reference YanjiRadius or paper.radius, or add // token-exempt: <reason>.`);
        }
      }
    }
  });
}

  return { errors, scanned: componentFiles.length };
}

const invokedDirectly = process.argv[1] &&
  path.resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (invokedDirectly) {
  const { errors, scanned } = await runGuard();
  if (errors.length > 0) {
    console.error('RN design token guard: FAIL');
    for (const error of errors) console.error(`  - ${error}`);
    process.exit(1);
  }
  console.log(
    `RN design token guard: PASS (${scanned} source files scanned; semantic palettes and token scales connected).`,
  );
}
