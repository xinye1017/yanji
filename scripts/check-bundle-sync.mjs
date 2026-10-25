#!/usr/bin/env node

/**
 * Check the committed Add-to-App artifact without Metro, Git or node_modules.
 * The build receipt binds the entire repository input inventory (source,
 * configuration, npm content-integrity lock and patches) to a full HBC digest.
 * This detects stale/changed artifacts; it is not a signed build attestation.
 */
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  bundleOptions, bundlePath, hermesFlags, inspectBytecode, metadataPath,
  readVersions, repoRoot, snapshotInputs, verifyResources,
} from './android-bundle.mjs';

export async function runGuard(root = repoRoot) {
  const errors = [];
  try {
    let bytes;
    try {
      bytes = await readFile(path.join(root, bundlePath));
    } catch (error) {
      throw new Error(`Committed bundle is missing: ${bundlePath}. ${error.message}`);
    }
    const artifact = inspectBytecode(bytes);
    let metadata;
    try {
      metadata = JSON.parse(await readFile(path.join(root, metadataPath), 'utf8'));
    } catch (error) {
      throw new Error(`Bundle build metadata is missing or invalid: ${metadataPath}. ${error.message}`);
    }
    if (metadata.schemaVersion !== 1 || JSON.stringify(metadata.options) !==
        JSON.stringify({ metro: bundleOptions, hermes: hermesFlags })) {
      throw new Error('Bundle metadata schema/build options differ from the current build pipeline.');
    }
    const versions = await readVersions(root);
    if (JSON.stringify(metadata.versions) !== JSON.stringify(versions)) {
      throw new Error('Bundle compiler/runtime/dependency integrity versions are stale.');
    }
    const inputs = await snapshotInputs(root);
    if (JSON.stringify(metadata.inputs) !== JSON.stringify(inputs)) {
      const recorded = new Map((metadata.inputs?.files ?? []).map(file => [file.path, file.sha256]));
      const current = new Map(inputs.files.map(file => [file.path, file.sha256]));
      const changed = [...new Set([...recorded.keys(), ...current.keys()])]
        .filter(name => recorded.get(name) !== current.get(name));
      throw new Error(`Bundle inputs are stale or metadata was altered: ${changed.slice(0, 12).join(', ') || 'input manifest digest'}.`);
    }
    if (JSON.stringify(metadata.artifact) !== JSON.stringify(artifact)) {
      throw new Error('Bundle artifact hash/length/format differs from the build metadata.');
    }
    if (!/^[a-f0-9]{64}$/.test(metadata.metro?.sha256) || !Number.isInteger(metadata.metro?.bytes) ||
        metadata.metro.bytes <= 0 || metadata.metro.sha1 !== artifact.sourceSha1) {
      throw new Error('HBC source hash does not match the recorded Metro compilation input.');
    }
    await verifyResources(root, metadata.assets);
  } catch (error) {
    errors.push(`${error.message} Regenerate with npm run bundle:android (and synchronize any reported Android resources).`);
  }
  return { errors };
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const { errors } = await runGuard();
  if (errors.length) {
    console.error('Bundle sync guard: FAIL');
    for (const error of errors) console.error(`  - ${error}`);
    process.exitCode = 1;
  } else {
    console.log('Bundle sync guard: PASS (source/config/dependency contents, HBC integrity and Android assets match the build receipt).');
  }
}
