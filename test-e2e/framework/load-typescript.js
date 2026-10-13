import { readFileSync, existsSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { createRequire } from 'node:module';
import { Script, createContext } from 'node:vm';
import ts from 'typescript';

/** Load production TS with controlled native/time adapters, never a second implementation. */
export function loadTypeScript(entry, mocks = {}, globals = {}) {
  const cache = new Map();
  function load(file) {
    if (cache.has(file)) return cache.get(file).exports;
    const module = { exports: {} };
    cache.set(file, module);
    const nativeRequire = createRequire(file);
    function require(id) {
      if (id in mocks) return mocks[id];
      if (!id.startsWith('.')) return nativeRequire(id);
      const base = resolve(dirname(file), id);
      if (base in mocks) return mocks[base];
      const target = [base, `${base}.ts`, `${base}.tsx`, `${base}/index.ts`]
        .find(candidate => existsSync(candidate) && /\.(ts|tsx)$/.test(candidate));
      return target ? load(target) : nativeRequire(id);
    }
    const output = ts.transpileModule(readFileSync(file, 'utf8'), {
      fileName: file,
      compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, jsx: ts.JsxEmit.ReactJSX },
    }).outputText;
    const context = createContext({ module, exports: module.exports, require, console,
      setTimeout, clearTimeout, setInterval, clearInterval, queueMicrotask, ...globals });
    new Script(output, { filename: file }).runInContext(context);
    return module.exports;
  }
  return load(resolve(entry));
}
