#!/usr/bin/env node

import { spawn } from 'node:child_process';
import { mkdir, mkdtemp, readFile, rename, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import {
  bundleOptions, bundlePath, digest, hermesFlags, hermescPath, inspectBytecode,
  metadataPath, readVersions, repoRoot, resourceRecords, snapshotInputs, verifyResources,
} from './android-bundle.mjs';

function run(command, args, cwd, capture = false) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { cwd, shell: false, stdio: capture ? ['ignore', 'pipe', 'pipe'] : 'inherit' });
    let output = '';
    if (capture) {
      child.stdout.on('data', chunk => { output = (output + chunk).slice(-65536); });
      child.stderr.on('data', chunk => { output = (output + chunk).slice(-65536); });
    }
    child.on('error', reject);
    child.on('exit', code => code === 0 ? resolve(output) : reject(new Error(`${path.basename(command)} exited ${code}. ${output}`)));
  });
}

async function main() {
  const root = repoRoot;
  const versions = await readVersions(root);
  const installedReact = JSON.parse(await readFile(path.join(root, 'node_modules/react-native/package.json'), 'utf8'));
  const installedCompiler = JSON.parse(await readFile(path.join(root, 'node_modules/hermes-compiler/package.json'), 'utf8'));
  const properties = await readFile(path.join(root, 'node_modules/react-native/sdks/hermes-engine/version.properties'), 'utf8');
  if (installedReact.version !== versions.reactNative || installedCompiler.version !== versions.hermes ||
      properties.match(/^HERMES_VERSION_NAME=(\S+)/m)?.[1] !== versions.hermes) {
    throw new Error('Installed React Native/Hermes do not match the lock and Android runtime; run npm ci.');
  }
  const compiler = hermescPath(root);
  const compilerVersion = await run(compiler, ['-version'], root, true);
  const bytecodeVersion = Number(compilerVersion.match(/HBC bytecode version:\s*(\d+)/)?.[1]);
  if (bytecodeVersion !== 98) throw new Error(`Review the HBC format validator before using compiler bytecode version ${bytecodeVersion}.`);
  const inputs = await snapshotInputs(root);
  await mkdir(path.join(root, 'build'), { recursive: true });
  const stage = await mkdtemp(path.join(root, 'build/android-bundle-'));
  try {
    const js = path.join(stage, 'index.android.js');
    const hbc = path.join(stage, 'index.android.bundle');
    const resources = path.join(stage, 'res');
    // Preserve the existing production Metro minification, then optimize HBC.
    // All intermediate JS/maps/resources stay outside the packaged asset tree.
    await run(process.execPath, [path.join(root, 'node_modules/react-native/cli.js'), 'bundle',
      '--platform', 'android', '--dev', 'false', '--minify', 'true',
      '--entry-file', 'index.js', '--bundle-output', js, '--assets-dest', resources,
      '--sourcemap-output', path.join(stage, 'index.android.packager.map'), '--reset-cache'], root);
    const assets = await resourceRecords(root, path.relative(root, resources).split(path.sep).join('/'));
    // Add-to-App packages committed resources directly. Never certify stale or
    // missing assets merely because Metro copied fresh ones into a temp folder.
    let previous;
    try {
      previous = JSON.parse(await readFile(path.join(root, metadataPath), 'utf8'));
    } catch (error) {
      if (error.code !== 'ENOENT') throw error;
    }
    await verifyResources(root, assets, previous?.assets);
    // Relative input name keeps HBC debug filenames independent of the checkout
    // location and mkdtemp suffix, on Windows as well as Linux.
    await run(compiler, [...hermesFlags, '-out', 'index.android.bundle', 'index.android.js'], stage);
    const bytecode = await readFile(hbc);
    const artifact = inspectBytecode(bytecode, bytecodeVersion);
    const source = await readFile(js);
    if (artifact.sourceSha1 !== digest(source, 'sha1')) throw new Error('Hermes bytecode source hash differs from the Metro JS.');
    // Hermes' own bytecode reader additionally parses every table/instruction.
    await run(compiler, ['-b', '-dump-bytecode', 'index.android.bundle'], stage, true);
    const after = await snapshotInputs(root);
    if (after.sha256 !== inputs.sha256) throw new Error('Bundle inputs changed during compilation; retry after edits finish.');
    const metadata = {
      schemaVersion: 1,
      versions,
      options: { metro: bundleOptions, hermes: hermesFlags },
      inputs,
      metro: { bytes: source.length, sha256: digest(source), sha1: digest(source, 'sha1') },
      artifact,
      assets,
    };
    const stagedMetadata = path.join(stage, 'index.android.bundle.meta.json');
    await writeFile(stagedMetadata, `${JSON.stringify(metadata, null, 2)}\n`);
    await mkdir(path.dirname(path.join(root, bundlePath)), { recursive: true });
    await rename(hbc, path.join(root, bundlePath));
    await rename(stagedMetadata, path.join(root, metadataPath));
    console.log(`Hermes Android bundle: ${source.length} bytes Metro JS -> ${artifact.bytes} bytes HBC ${artifact.version}; Hermes ${versions.hermes}.`);
    console.log(`SHA256 ${artifact.sha256}; ${inputs.files.length} repository inputs; ${assets.length} verified Android assets.`);
  } finally {
    await rm(stage, { recursive: true, force: true });
  }
}

main().catch(error => {
  console.error(`Android bundle: FAIL: ${error.message}`);
  process.exitCode = 1;
});
