import { createHash } from 'node:crypto';
import { readdir, readFile, stat } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
export const bundlePath = 'app/src/main/assets/index.android.bundle';
export const metadataPath = `${bundlePath}.meta.json`;
export const hermesFlags = ['-O', '-w', '-emit-binary'];
export const bundleOptions = { platform: 'android', dev: false, minify: true, entryFile: 'index.js' };

export function digest(bytes, algorithm = 'sha256') {
  return createHash(algorithm).update(bytes).digest('hex');
}

// Git may check out text as CRLF on Windows. Binary assets are always hashed verbatim.
const textExtension = /\.(?:[cm]?js|jsx|ts|tsx|json|toml|patch|properties|md)$/;
export function inputDigest(name, bytes) {
  return digest(textExtension.test(name) ? bytes.toString('utf8').replace(/\r\n/g, '\n') : bytes);
}

async function filesUnder(root, directory) {
  let entries;
  try {
    entries = await readdir(path.join(root, directory), { withFileTypes: true });
  } catch (error) {
    if (error.code === 'ENOENT') return [];
    throw error;
  }
  const files = [];
  for (const entry of entries) {
    const name = path.posix.join(directory, entry.name);
    if (entry.isSymbolicLink()) throw new Error(`Bundle input must not be a symlink: ${name}`);
    if (entry.isDirectory()) files.push(...await filesUnder(root, name));
    else if (entry.isFile()) files.push(name);
  }
  return files;
}

/**
 * Repository build inputs, including npm's content integrity lock and local patches.
 * This also works in the checkout-only CI guard, without node_modules or Git.
 * Hash every file in the runtime input trees so additions/deletions cannot hide
 * behind a hand-maintained list. Root configs/entry points and build scripts are
 * included too; docs and native business code do not change the JS artifact.
 */
export async function snapshotInputs(root) {
  const names = new Set([
    'package.json', 'package-lock.json', 'index.js', 'App.tsx',
    'babel.config.js', 'metro.config.js', 'gradle/libs.versions.toml',
    'scripts/android-bundle.mjs', 'scripts/bundle-android.mjs', 'scripts/check-bundle-sync.mjs',
  ]);
  for (const entry of await readdir(root, { withFileTypes: true })) {
    if (entry.isFile() && /\.(?:[cm]?js|jsx|ts|tsx|json)$/.test(entry.name)) names.add(entry.name);
  }
  for (const directory of ['src', 'assets', 'patches']) {
    for (const name of await filesUnder(root, directory)) names.add(name);
  }
  const files = [];
  for (const name of [...names].sort()) {
    files.push({ path: name, sha256: inputDigest(name, await readFile(path.join(root, name))) });
  }
  return { sha256: digest(JSON.stringify(files)), files };
}

export async function readVersions(root) {
  const pkg = JSON.parse(await readFile(path.join(root, 'package.json'), 'utf8'));
  const lock = JSON.parse(await readFile(path.join(root, 'package-lock.json'), 'utf8'));
  const catalog = await readFile(path.join(root, 'gradle/libs.versions.toml'), 'utf8');
  const reactNative = lock.packages?.['node_modules/react-native']?.version;
  const compiler = lock.packages?.['node_modules/hermes-compiler'];
  const runtime = catalog.match(/^hermesVersion\s*=\s*"([^"]+)"/m)?.[1];
  const nativeReact = catalog.match(/^reactAndroid\s*=\s*"([^"]+)"/m)?.[1];
  if (!reactNative || pkg.dependencies?.['react-native'] !== reactNative || nativeReact !== reactNative) {
    throw new Error('React Native versions in package.json, package-lock.json and the Android catalog must match.');
  }
  if (!runtime || compiler?.version !== runtime || !compiler.integrity?.startsWith('sha512-') ||
      lock.packages['node_modules/react-native'].dependencies?.['hermes-compiler'] !== runtime) {
    throw new Error('Hermes compiler lock/integrity and Android runtime versions must match React Native.');
  }
  return { reactNative, hermes: runtime, compilerIntegrity: compiler.integrity };
}

export function hermescPath(root, platform = process.platform, arch = process.arch) {
  const directory = platform === 'win32' && arch === 'x64' ? 'win64-bin'
    : platform === 'linux' && arch === 'x64' ? 'linux64-bin'
      : platform === 'darwin' && ['x64', 'arm64'].includes(arch) ? 'osx-bin' : null;
  if (!directory) throw new Error(`No bundled Hermes compiler for ${platform}/${arch}.`);
  return path.join(root, 'node_modules/hermes-compiler/hermesc', directory,
    platform === 'win32' ? 'hermesc.exe' : 'hermesc');
}

/**
 * HBC execution form, not a magic/header-only check. The packed header and SHA1
 * footer are defined by Hermes' BytecodeFileFormat.h. In this runtime's HBC 98,
 * the header is 128 bytes, the source SHA1 starts at 12, fileLength at 32,
 * and debugInfoOffset at 108. Fail closed for unknown layouts on an upgrade.
 * https://github.com/facebook/hermes/blob/hermes-v250829098.0.17/include/hermes/BCGen/HBC/BytecodeFileFormat.h
 */
export function inspectBytecode(bytes, expectedVersion) {
  if (bytes.length < 148 || bytes.readBigUInt64LE(0) !== 0x1f1903c103bc1fc6n) {
    throw new Error('Artifact is not Hermes bytecode (JS text and header-only files are rejected).');
  }
  const version = bytes.readUInt32LE(8);
  if (version !== 98 || (expectedVersion !== undefined && version !== expectedVersion)) {
    throw new Error(`Unsupported/mismatched HBC bytecode version: ${version}; expected ${expectedVersion ?? 98}.`);
  }
  if (bytes.readUInt32LE(32) !== bytes.length) throw new Error('HBC file length is invalid (truncated or trailing data).');
  const functionCount = bytes.readUInt32LE(40);
  const debugOffset = bytes.readUInt32LE(108);
  if (!functionCount || bytes.readUInt32LE(36) >= functionCount ||
      128 + functionCount * 12 > bytes.length - 20 || debugOffset < 128 || debugOffset >= bytes.length - 20) {
    throw new Error('HBC function table or debug section is out of bounds.');
  }
  const footer = bytes.subarray(-20).toString('hex');
  if (footer !== digest(bytes.subarray(0, -20), 'sha1')) throw new Error('HBC footer checksum does not match the bytecode content.');
  return { version, sourceSha1: bytes.subarray(12, 32).toString('hex'), bytes: bytes.length, sha256: digest(bytes) };
}

export function assertResourcePath(name) {
  if (typeof name !== 'string' || !/^app\/src\/main\/res\/(?:drawable[^/]*|raw)\/[a-z0-9_.]+$/.test(name)) {
    throw new Error(`Invalid generated Android resource path: ${name}`);
  }
}

export async function resourceRecords(root, directory) {
  const records = [];
  for (const name of (await filesUnder(root, directory)).sort()) {
    const output = `app/src/main/res/${path.posix.relative(directory, name)}`;
    assertResourcePath(output);
    records.push({ path: output, sha256: digest(await readFile(path.join(root, name))) });
  }
  return records;
}

export async function verifyResources(root, records, previous = []) {
  if (!Array.isArray(records) || !Array.isArray(previous)) throw new Error('Generated asset manifest is missing or invalid.');
  const seen = new Set();
  for (const record of records) {
    assertResourcePath(record.path);
    if (seen.has(record.path)) throw new Error(`Duplicate generated asset: ${record.path}`);
    seen.add(record.path);
    if (digest(await readFile(path.join(root, record.path))) !== record.sha256) {
      throw new Error(`Generated Android asset differs from Metro output: ${record.path}`);
    }
  }
  for (const old of previous) {
    assertResourcePath(old.path);
    if (seen.has(old.path)) continue;
    let exists;
    try {
      exists = await stat(path.join(root, old.path));
    } catch (error) {
      if (error.code !== 'ENOENT') throw error;
    }
    if (exists) throw new Error(`Remove obsolete generated Android asset before rebuilding: ${old.path}`);
  }
}
