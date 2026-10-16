import test from 'node:test';
import assert from 'node:assert/strict';
import { loadTypeScript } from './framework/load-typescript.js';

function createClientHarness({ apiKey = '', settings = {} } = {}) {
  const bridge = {
    YanjiDataNative: {
      getDecisionApiKey: async () => apiKey,
      getUserSettings: async () => settings,
    },
  };

  const client = loadTypeScript(
    'src/services/decisionClient.ts',
    {
      '../bridge': bridge,
    },
    {
      fetch: (...args) => globalThis.fetch(...args),
      AbortController: globalThis.AbortController,
    }
  );

  return { client, bridge };
}

test('classifyMoment returns INSPIRATION if content is empty or whitespace', async () => {
  const { client } = createClientHarness();
  const result = await client.classifyMoment('   ');
  assert.equal(result, 'INSPIRATION');
});

test('classifyMoment fails closed to INSPIRATION if no API key is present', async () => {
  const { client } = createClientHarness({ apiKey: '' });
  const result = await client.classifyMoment('这道二重积分的轮换对称性完全没有看懂，为什么积出来是零？');
  assert.equal(result, 'INSPIRATION');
});

test('classifyMoment correctly parses OpenRouter noul response >= 0.7 as DOUBT', async () => {
  const { client } = createClientHarness({
    apiKey: 'sk-or-test',
    settings: {
      decisionProvider: 'openrouter',
      decisionModel: 'respan/span-01-lite:free',
      decisionEndpoint: 'https://openrouter.ai/api/alpha/decisions',
    },
  });

  let capturedUrl = '';
  let capturedBody = null;

  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (url, opts) => {
    capturedUrl = url;
    capturedBody = JSON.parse(opts.body);
    return {
      ok: true,
      status: 200,
      json: async () => ({
        model: 'respan/span-01-lite:free',
        answers: {
          q_doubt: {
            type: 'noul',
            noul: 0.88,
          },
        },
      }),
    };
  };

  try {
    const result = await client.classifyMoment('这道高数题的辅助函数为什么这样构造？完全想不通');
    assert.equal(result, 'DOUBT');
    assert.equal(capturedUrl, 'https://openrouter.ai/api/alpha/decisions');
    assert.equal(capturedBody.model, 'respan/span-01-lite:free');
    assert.equal(capturedBody.questions.q_doubt.type, 'noul');
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('classifyMoment correctly parses OpenRouter noul response < 0.7 as INSPIRATION', async () => {
  const { client } = createClientHarness({
    apiKey: 'sk-or-test',
    settings: {
      decisionProvider: 'openrouter',
      decisionModel: 'respan/span-01-lite:free',
      decisionEndpoint: 'https://openrouter.ai/api/alpha/decisions',
    },
  });

  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({
    ok: true,
    status: 200,
    json: async () => ({
      model: 'respan/span-01-lite:free',
      answers: {
        q_doubt: {
          type: 'noul',
          noul: 0.35,
        },
      },
    }),
  });

  try {
    const result = await client.classifyMoment('今天复习状态极佳，完成了30道线代选择题！');
    assert.equal(result, 'INSPIRATION');
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('classifyMoment correctly parses TypeSafe choice response', async () => {
  const { client } = createClientHarness({
    apiKey: 'sk-jev-test',
    settings: {
      decisionProvider: 'typesafe',
      decisionModel: 'jev-latest',
      decisionEndpoint: 'https://api.typesafe.ai/v1/systemone',
    },
  });

  let capturedBody = null;

  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (url, opts) => {
    capturedBody = JSON.parse(opts.body);
    return {
      ok: true,
      status: 200,
      json: async () => ({
        model: 'jev-latest',
        answers: {
          q_doubt: {
            choice: 'match',
            confidence: 0.92,
            probabilities: {
              match: 0.92,
              other: 0.08,
            },
          },
        },
      }),
    };
  };

  try {
    const result = await client.classifyMoment('反常积分收敛性审敛法在分母含有对数时到底该怎么放缩？');
    assert.equal(result, 'DOUBT');
    assert.equal(capturedBody.questions.q_doubt.type, 'choice');
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('testDecisionConnection handles network errors gracefully', async () => {
  const { client } = createClientHarness();
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => {
    throw new Error('Network failure');
  };

  try {
    const result = await client.testDecisionConnection(client.DEFAULT_OPENROUTER_CONFIG, 'test-key');
    assert.equal(result.success, false);
    assert.match(result.message, /网络连接失败/);
  } finally {
    globalThis.fetch = originalFetch;
  }
});
