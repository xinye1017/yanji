#!/usr/bin/env node
// jev — TypeSafe/Jev helper for agent dev workflows.
//
// Pattern (TypeSafe "find and judge evidence"): code does the deterministic
// candidate gathering (ls/grep/git), Jev makes one narrow semantic judgment per
// candidate in a single parallel request, and code sorts/filters the answers.
//
// Commands:
//   jev rank   --query <text> [--top N] [--threshold T] [--paths] [--json]
//              Reads candidates from stdin (one per line, or a JSON array of
//              {id,text}). Asks one Noul per candidate and prints the ranked
//              list. --paths treats each stdin line as a file path candidate.
//   jev ask    --state <json|@file> --questions <json|@file>
//              Raw passthrough to POST /v1/systemone. Prints the full response.
//
// Auth: TYPESAFE_API_KEY env var, else scripts/jev/.env.local (git-ignored).
// Endpoint override: TYPESAFE_ENDPOINT (default https://api.typesafe.ai/v1/systemone).

import { readFileSync, existsSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const HERE = dirname(fileURLToPath(import.meta.url));
const DEFAULT_ENDPOINT = "https://api.typesafe.ai/v1/systemone";
const DEFAULT_MODEL = "jev-latest";
// One request accepts many questions, but state has a 32k-token budget. Cap the
// per-request candidate count and batch the rest, merging the results.
const MAX_CANDIDATES_PER_BATCH = 200;

// ---------------------------------------------------------------------------
// arg parsing
// ---------------------------------------------------------------------------

function parseArgs(argv) {
  const out = { _: [] };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a.startsWith("--")) {
      const key = a.slice(2);
      const next = argv[i + 1];
      if (next === undefined || next.startsWith("--")) {
        out[key] = true;
      } else {
        out[key] = next;
        i++;
      }
    } else {
      out._.push(a);
    }
  }
  return out;
}

// ---------------------------------------------------------------------------
// credentials
// ---------------------------------------------------------------------------

function loadApiKey() {
  if (process.env.TYPESAFE_API_KEY && process.env.TYPESAFE_API_KEY.trim()) {
    return process.env.TYPESAFE_API_KEY.trim();
  }
  const envLocal = join(HERE, ".env.local");
  if (existsSync(envLocal)) {
    for (const line of readFileSync(envLocal, "utf8").split(/\r?\n/)) {
      const m = line.match(/^\s*TYPESAFE_API_KEY\s*=\s*(.+?)\s*$/);
      if (m) return m[1].replace(/^["']|["']$/g, "").trim();
    }
  }
  return null;
}

// ---------------------------------------------------------------------------
// stdin
// ---------------------------------------------------------------------------

async function readStdin() {
  const chunks = [];
  for await (const chunk of process.stdin) chunks.push(chunk);
  return Buffer.concat(chunks).toString("utf8");
}

// ---------------------------------------------------------------------------
// candidates
// ---------------------------------------------------------------------------

// Parse stdin into [{id, text}]. Accepts a JSON array of {id,text}/{id,content}
// or {[id]: text} map; otherwise one candidate per non-empty line.
function parseCandidates(raw, { paths = false } = {}) {
  const trimmed = raw.trim();
  if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
    const parsed = JSON.parse(trimmed);
    if (Array.isArray(parsed)) {
      return parsed.map((item, i) => {
        if (typeof item === "string") return { id: `c${i}`, text: item };
        return {
          id: String(item.id ?? item.name ?? item.path ?? `c${i}`),
          text: String(item.text ?? item.content ?? item.value ?? item.summary ?? item.id ?? ""),
        };
      });
    }
    // map form: { id: text }
    return Object.entries(parsed).map(([id, text]) => ({ id, text: String(text) }));
  }
  return trimmed
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter((l) => l.length > 0)
    .map((line, i) => (paths ? { id: line, text: line } : { id: `L${String(i).padStart(4, "0")}`, text: line }));
}

// ---------------------------------------------------------------------------
// Jev request
// ---------------------------------------------------------------------------

const BACKTICK = String.fromCharCode(96);

function rankPrompt(query) {
  const idRef = BACKTICK + "candidates.<id>" + BACKTICK;
  const queryRef = BACKTICK + "query" + BACKTICK;
  return {
    instructions:
      `考虑候选 ${idRef} 的内容（及其 id/路径本身）。它是否与下面的意图高度相关，` +
      `值得作为实现、修改或排查该功能的上下文？\n\n意图（${queryRef}）：${query}`,
    criteria: {
      true: "该候选直接实现、定义或控制 query 所描述的功能或其紧邻调用链",
      false: "该候选与该功能无关，或仅因通用词汇/命名而表面相似",
    },
  };
}

function buildBatchBody({ query, batch, model }) {
  const state = {
    query,
    candidates: Object.fromEntries(batch.map((c) => [c.id, c.text])),
  };
  const questions = {};
  for (const c of batch) {
    const p = rankPrompt(query);
    questions[c.id] = {
      type: "noul",
      instructions: p.instructions.replace("<id>", c.id),
      criteria: p.criteria,
    };
  }
  return { state, model, questions };
}

const wait = (ms) => new Promise((r) => setTimeout(r, ms));

async function postSystemOne(endpoint, apiKey, body, { attempts = 4 } = {}) {
  let lastErr;
  for (let attempt = 0; attempt < attempts; attempt++) {
    let res;
    try {
      res = await fetch(endpoint, {
        method: "POST",
        headers: {
          Authorization: `Bearer ${apiKey}`,
          "Content-Type": "application/json",
          Accept: "application/json",
          "User-Agent": "yanji-jev/1.0",
        },
        body: JSON.stringify(body),
      });
    } catch (err) {
      lastErr = new Error(`无法连接 TypeSafe 服务: ${err.message}`);
      await wait(500 * 2 ** attempt);
      continue;
    }
    if (res.status === 429 || res.status === 529) {
      const retryAfter = Number(res.headers.get("retry-after")) || 0;
      await wait((retryAfter || 1) * 1000 * 2 ** attempt);
      lastErr = new Error(`TypeSafe 限流 HTTP ${res.status}，已重试`);
      continue;
    }
    const text = await res.text();
    if (res.status < 200 || res.status >= 300) {
      let detail = text.slice(0, 200);
      try {
        detail = JSON.parse(text).error?.message ?? JSON.parse(text).detail?.message ?? detail;
      } catch {
        /* keep raw slice */
      }
      const e = new Error(`TypeSafe HTTP ${res.status}: ${detail}`);
      e.status = res.status;
      throw e;
    }
    return JSON.parse(text);
  }
  throw lastErr ?? new Error("TypeSafe 请求失败");
}

function chunk(arr, size) {
  const out = [];
  for (let i = 0; i < arr.length; i += size) out.push(arr.slice(i, i + size));
  return out;
}

async function rank({ query, candidates, model, endpoint, apiKey }) {
  const batches = chunk(candidates, MAX_CANDIDATES_PER_BATCH);
  const results = [];
  const usage = { input_tokens: 0, output_tokens: 0 };
  let answeredModel = model;
  for (const batch of batches) {
    const body = buildBatchBody({ query, batch, model });
    const resp = await postSystemOne(endpoint, apiKey, body);
    answeredModel = resp.model ?? answeredModel;
    usage.input_tokens += resp.usage?.input_tokens ?? 0;
    usage.output_tokens += resp.usage?.output_tokens ?? 0;
    for (const c of batch) {
      const noul = resp.answers?.[c.id]?.noul ?? 0;
      results.push({ id: c.id, score: noul, text: c.text });
    }
  }
  results.sort((a, b) => b.score - a.score);
  return { model: answeredModel, usage, results };
}

// ---------------------------------------------------------------------------
// main
// ---------------------------------------------------------------------------

function fail(msg) {
  console.error(`jev: ${msg}`);
  process.exit(2);
}

async function main() {
  const [command, ...rest] = process.argv.slice(2);
  const args = parseArgs(rest);

  if (!command || command === "help" || args.help) {
    console.log(
      `用法:
  jev rank --query "<意图>" [--top N] [--threshold 0.4] [--paths] [--model jev-latest]
      从 stdin 读候选（每行一个；或 JSON 数组 [{id,text}]），一次请求排序。
  jev ask  --state <json|@file> --questions <json|@file> [--model jev-latest]
      直接调用 POST /v1/systemone 并打印完整响应。

认证: 环境变量 TYPESAFE_API_KEY，或 scripts/jev/.env.local。`
    );
    return;
  }

  const apiKey = loadApiKey();
  if (!apiKey) {
    fail("缺少 API key。请设置 TYPESAFE_API_KEY，或写入 scripts/jev/.env.local。");
  }
  const endpoint = process.env.TYPESAFE_ENDPOINT || DEFAULT_ENDPOINT;
  const model = args.model || DEFAULT_MODEL;

  const readJsonArg = (v, name) => {
    if (typeof v !== "string") fail(`缺少 --${name}`);
    const raw = v.startsWith("@") ? readFileSync(v.slice(1), "utf8") : v;
    try {
      return JSON.parse(raw);
    } catch (e) {
      fail(`--${name} 不是合法 JSON: ${e.message}`);
    }
  };

  if (command === "ask") {
    const state = readJsonArg(args.state, "state");
    const questions = readJsonArg(args.questions, "questions");
    const resp = await postSystemOne(endpoint, apiKey, { state, model, questions });
    console.log(JSON.stringify(resp, null, 2));
    return;
  }

  if (command === "rank") {
    if (typeof args.query !== "string") fail("缺少 --query");
    const raw = await readStdin();
    if (!raw.trim()) fail("stdin 没有候选。用 `git ls-files | jev rank ...` 或管道传入。");
    const candidates = parseCandidates(raw, { paths: Boolean(args.paths) });
    if (candidates.length === 0) fail("未解析出任何候选");
    const top = args.top ? Number(args.top) : 0;
    const threshold = args.threshold ? Number(args.threshold) : 0;

    const started = Date.now();
    const { model: answeredModel, usage, results } = await rank({
      query: args.query,
      candidates,
      model,
      endpoint,
      apiKey,
    });
    const elapsedMs = Date.now() - started;

    let out = results;
    if (threshold > 0) out = out.filter((r) => r.score >= threshold);
    if (top > 0) out = out.slice(0, top);

    const payload = {
      query: args.query,
      model: answeredModel,
      candidates: candidates.length,
      requests: Math.ceil(candidates.length / MAX_CANDIDATES_PER_BATCH),
      elapsed_ms: elapsedMs,
      usage,
      results: out.map((r) => ({ id: r.id, score: Number(r.score.toFixed(4)) })),
    };

    if (args.json) {
      console.log(JSON.stringify(payload, null, 2));
    } else {
      console.error(
        `# model=${answeredModel} candidates=${candidates.length} requests=${payload.requests} ` +
          `elapsed=${elapsedMs}ms in=${usage.input_tokens} out=${usage.output_tokens}`
      );
      for (const r of out) {
        console.log(`${r.score.toFixed(3)}\t${r.id}`);
      }
    }
    return;
  }

  fail(`未知命令: ${command}`);
}

main().catch((err) => {
  fail(err.message ?? String(err));
});
