import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import {
  copyFileSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync,
} from 'node:fs';
import path from 'node:path';
import {
  bundleOptions, bundlePath, digest, hermesFlags, hermescPath, inspectBytecode,
  metadataPath, readVersions, repoRoot, snapshotInputs, verifyResources,
} from '../scripts/android-bundle.mjs';
import { runGuard } from '../scripts/check-bundle-sync.mjs';

// Real compiler output, never a fake header or a hard-coded "passing" hash.
// Every temporary fixture lives under ignored build/ and is removed by the test.
mkdirSync(path.join(repoRoot, 'build'), { recursive: true });
const fixture = mkdtempSync(path.join(repoRoot, 'build/bundle-guard-compiler-'));
let bytecode;
const source = Buffer.from('globalThis.bundleGuardFixture = 42;\n');
try {
  writeFileSync(path.join(fixture, 'fixture.js'), source);
  const result = spawnSync(hermescPath(repoRoot), [...hermesFlags, '-out', 'fixture.hbc', 'fixture.js'], {
    cwd: fixture, stdio: 'inherit', shell: false,
  });
  assert.equal(result.status, 0, result.error?.message);
  bytecode = readFileSync(path.join(fixture, 'fixture.hbc'));
} finally {
  rmSync(fixture, { recursive: true, force: true });
}

async function tree(t) {
  const root = mkdtempSync(path.join(repoRoot, 'build/bundle-guard-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  function put(name, content) {
    mkdirSync(path.dirname(path.join(root, name)), { recursive: true });
    writeFileSync(path.join(root, name), content);
  }
  for (const name of ['package.json', 'package-lock.json', 'gradle/libs.versions.toml',
    'scripts/android-bundle.mjs', 'scripts/bundle-android.mjs', 'scripts/check-bundle-sync.mjs']) {
    mkdirSync(path.dirname(path.join(root, name)), { recursive: true });
    copyFileSync(path.join(repoRoot, name), path.join(root, name));
  }
  for (const name of ['App.tsx', 'index.js', 'babel.config.js', 'metro.config.js', 'src/Screen.tsx']) put(name, 'export const value = 1;\n');
  put('assets/icon.bin', Buffer.from([0, 1, 255, 2]));
  put('patches/example.patch', 'real dependency patch\n');
  const assetPath = 'app/src/main/res/drawable-mdpi/fixture.png';
  const asset = Buffer.from([1, 2, 3, 4]);
  put(assetPath, asset);
  put(bundlePath, bytecode);
  const metadata = {
    schemaVersion: 1,
    versions: await readVersions(root),
    options: { metro: bundleOptions, hermes: hermesFlags },
    inputs: await snapshotInputs(root),
    metro: { bytes: source.length, sha256: digest(source), sha1: digest(source, 'sha1') },
    artifact: inspectBytecode(bytecode),
    assets: [{ path: assetPath, sha256: digest(asset) }],
  };
  const save = () => put(metadataPath, JSON.stringify(metadata));
  save();
  return { root, put, metadata, save, assetPath };
}

async function fails(root, pattern) {
  const { errors } = await runGuard(root);
  assert.ok(errors.some(error => pattern.test(error)), errors.join('\n'));
}

test('committed Hermes bundle and receipt match the real checkout', async () => {
  assert.deepEqual((await runGuard(repoRoot)).errors, []);
});

test('a real compiled fixture passes without node_modules or Git', async t => {
  const { root } = await tree(t);
  assert.deepEqual((await runGuard(root)).errors, []);
});

test('non-symbol source changes, additions and deletions all invalidate the artifact', async t => {
  for (const operation of ['change', 'add', 'delete']) {
    const { root, put } = await tree(t);
    if (operation === 'change') put('src/Screen.tsx', 'export const value = 2;\n');
    if (operation === 'add') put('src/New.tsx', 'export const added = true;\n');
    if (operation === 'delete') rmSync(path.join(root, 'src/Screen.tsx'));
    await fails(root, /Bundle inputs are stale.*src\//);
  }
});

test('config, dependency lock, patches, binary inputs and build script edits are detected', async t => {
  for (const name of ['metro.config.js', 'babel.config.js', 'package-lock.json',
    'patches/example.patch', 'assets/icon.bin', 'scripts/bundle-android.mjs']) {
    const { root, put } = await tree(t);
    const bytes = readFileSync(path.join(root, name));
    put(name, Buffer.concat([bytes, Buffer.from(' ')]));
    await fails(root, /Bundle inputs are stale/);
  }
});

test('text checkout line endings are portable while binary asset bytes remain exact', async t => {
  const { root, put } = await tree(t);
  put('src/Screen.tsx', 'export const value = 1;\r\n');
  assert.deepEqual((await runGuard(root)).errors, []);
  put('assets/icon.bin', Buffer.from([0, 1, 255, 3]));
  await fails(root, /Bundle inputs are stale/);
});

test('missing bundle or receipt, malformed receipt and old JS bundles fail closed', async t => {
  for (const operation of ['bundle', 'metadata', 'json', 'js', 'header']) {
    const { root, put } = await tree(t);
    if (operation === 'bundle') rmSync(path.join(root, bundlePath));
    if (operation === 'metadata') rmSync(path.join(root, metadataPath));
    if (operation === 'json') put(metadataPath, '{');
    if (operation === 'js') put(bundlePath, 'var __BUNDLE_START_TIME__=0;');
    if (operation === 'header') put(bundlePath, bytecode.subarray(0, 128));
    await fails(root, /missing|invalid|not Hermes bytecode/);
  }
});

test('bytecode payload corruption cannot be hidden by updating the receipt SHA256', async t => {
  const { root, put, metadata, save } = await tree(t);
  const broken = Buffer.from(bytecode);
  broken[130] ^= 1;
  put(bundlePath, broken);
  metadata.artifact.sha256 = digest(broken);
  save();
  await fails(root, /footer checksum/);
});

test('truncation, appended bytes, wrong HBC version and invalid table bounds are rejected', async t => {
  for (const operation of ['truncate', 'append', 'version', 'table']) {
    const { root, put } = await tree(t);
    let broken = Buffer.from(bytecode);
    if (operation === 'truncate') broken = broken.subarray(0, -1);
    if (operation === 'append') broken = Buffer.concat([broken, Buffer.from([0])]);
    if (operation === 'version') broken.writeUInt32LE(97, 8);
    if (operation === 'table') broken.writeUInt32LE(0xffffffff, 40);
    put(bundlePath, broken);
    await fails(root, /length is invalid|bytecode version|out of bounds/);
  }
});

test('fake input digests, artifact digests and compilation source hashes fail', async t => {
  for (const operation of ['input', 'file', 'artifact', 'source']) {
    const { root, metadata, save } = await tree(t);
    if (operation === 'input') metadata.inputs.sha256 = '0'.repeat(64);
    if (operation === 'file') metadata.inputs.files[0].sha256 = '0'.repeat(64);
    if (operation === 'artifact') metadata.artifact.sha256 = '0'.repeat(64);
    if (operation === 'source') metadata.metro.sha1 = '0'.repeat(40);
    save();
    await fails(root, /metadata was altered|artifact hash|source hash/);
  }
});

test('compiler/runtime version drift and altered build flags are rejected', async t => {
  for (const operation of ['runtime', 'compiler', 'flags']) {
    const { root, put, metadata, save } = await tree(t);
    if (operation === 'runtime') {
      const catalog = readFileSync(path.join(root, 'gradle/libs.versions.toml'), 'utf8');
      put('gradle/libs.versions.toml', catalog.replace(/hermesVersion = "[^"]+"/, 'hermesVersion = "wrong"'));
    }
    if (operation === 'compiler') metadata.versions.hermes = 'wrong';
    if (operation === 'flags') metadata.options.hermes = ['-O0'];
    save();
    await fails(root, /versions must match|versions are stale|build options differ/);
  }
});

test('missing or changed packaged Metro assets and invalid asset manifest paths fail', async t => {
  for (const operation of ['change', 'delete', 'path']) {
    const { root, put, metadata, save, assetPath } = await tree(t);
    if (operation === 'change') put(assetPath, Buffer.from([9, 9, 9]));
    if (operation === 'delete') rmSync(path.join(root, assetPath));
    if (operation === 'path') metadata.assets[0].path = '../outside';
    save();
    await fails(root, /asset differs|ENOENT|Invalid generated Android resource path/);
  }
});

test('obsolete generated assets must be removed before issuing a new receipt', async t => {
  const { root, metadata, assetPath } = await tree(t);
  await assert.rejects(verifyResources(root, [], metadata.assets), /Remove obsolete generated Android asset/);
  rmSync(path.join(root, assetPath));
  await verifyResources(root, [], metadata.assets);
});

test('compiler selection covers Windows, Linux and macOS without shell-specific syntax', () => {
  assert.match(hermescPath(repoRoot, 'win32', 'x64'), /win64-bin[\\/]hermesc\.exe$/);
  assert.match(hermescPath(repoRoot, 'linux', 'x64'), /linux64-bin[\\/]hermesc$/);
  assert.match(hermescPath(repoRoot, 'darwin', 'arm64'), /osx-bin[\\/]hermesc$/);
  assert.throws(() => hermescPath(repoRoot, 'linux', 'arm64'), /No bundled Hermes compiler/);
});
