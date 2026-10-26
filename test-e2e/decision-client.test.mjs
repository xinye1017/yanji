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

test('classifyMoment returns null for empty content: no text, no answer', async () => {
  const { client } = createClientHarness();
  const result = await client.classifyMoment('   ');
  assert.equal(result, null);
});

test('classifyMoment returns null when no API key is configured', async () => {
  const { client } = createClientHarness({ apiKey: '' });
  const result = await client.classifyMoment('这道二重积分的轮换对称性完全没有看懂，为什么积出来是零？');
  assert.equal(result, null, 'an unanswered call must be distinguishable from a model verdict of 灵感');
});

test('classifyMoment returns null on a network failure instead of claiming 灵感', async () => {
  const { client } = createClientHarness({
    apiKey: 'sk-or-test',
    settings: { decisionProvider: 'openrouter', decisionModel: 'm', decisionEndpoint: 'https://openrouter.ai/api/v1' },
  });
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => { throw new Error('Network failure'); };

  try {
    assert.equal(await client.classifyMoment('这条其实是疑点'), null);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('classifyMoment returns null when the endpoint answers with an error status', async () => {
  const { client } = createClientHarness({
    apiKey: 'sk-or-test',
    settings: { decisionProvider: 'openrouter', decisionModel: 'm', decisionEndpoint: 'https://openrouter.ai/api/v1' },
  });
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async () => ({ ok: false, status: 500, json: async () => ({}) });

  try {
    assert.equal(await client.classifyMoment('这条其实是疑点'), null);
  } finally {
    globalThis.fetch = originalFetch;
  }
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

test('the System One surface uses the same noul schema as the Decisions surface', async () => {
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
        answers: { q_doubt: { type: 'noul', noul: 0.92 } },
      }),
    };
  };

  try {
    const result = await client.classifyMoment('反常积分收敛性审敛法在分母含有对数时到底该怎么放缩？');
    assert.equal(result, 'DOUBT');
    // Both surfaces share one schema, so the question type never depends on
    // which endpoint the key points at.
    assert.equal(capturedBody.questions.q_doubt.type, 'noul', 'no provider-specific question type');
    assert.equal(capturedBody.state.note, '反常积分收敛性审敛法在分母含有对数时到底该怎么放缩？',
      'the state carries the note in a named field');
    assert.equal(capturedBody.questions.q_doubt.criteria.true.length > 0, true);
    assert.equal(capturedBody.questions.q_doubt.criteria.false.length > 0, true);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('the question is about unanswered questions, not about studying', async () => {
  const { client } = createClientHarness({
    apiKey: 'sk-jev-test',
    settings: { decisionProvider: 'typesafe', decisionModel: 'jev-latest', decisionEndpoint: 'https://api.typesafe.ai/v1' },
  });

  let capturedBody = null;
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (url, opts) => {
    capturedBody = JSON.parse(opts.body);
    return { ok: true, status: 200, json: async () => ({ answers: { q_doubt: { type: 'noul', noul: 0.9 } } }) };
  };

  try {
    // Not a study note at all, and still a doubt: unanswered is the axis, not
    // the topic. The prompt must not narrow the judgement to coursework.
    assert.equal(await client.classifyMoment('为什么我把闹钟设在六点，早上还是起不来？'), 'DOUBT');
    const question = capturedBody.questions.q_doubt;
    assert.match(question.instructions, /Is this a question\?/, 'the asked question stays plain');
    const toldToTheModel = `${question.instructions} ${question.criteria.true} ${question.criteria.false}`;
    assert.doesNotMatch(toldToTheModel, /考研|做题|知识点/, 'the judge is not scoped to exam study');
    assert.match(question.criteria.true, /stuck/i, 'still-open is what makes a doubt');
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('an answer without a usable noul value is treated as no answer, not as 灵感', async () => {
  const { client } = createClientHarness({
    apiKey: 'sk-jev-test',
    settings: { decisionProvider: 'typesafe', decisionModel: 'jev-latest', decisionEndpoint: 'https://api.typesafe.ai/v1' },
  });
  const originalFetch = globalThis.fetch;

  // `confidence` is a model statistic, not the probability of the answer, and a
  // choice answer is not what this question asked for. Neither may be reused.
  for (const answer of [
    { type: 'choice', choice: 'match', confidence: 0.92 },
    { type: 'noul' },
    { type: 'noul', noul: 'high' },
    { type: 'noul', noul: 1.4 },
  ]) {
    globalThis.fetch = async () => ({ ok: true, status: 200, json: async () => ({ answers: { q_doubt: answer } }) });
    try {
      assert.equal(await client.classifyMoment('一个正在求解的疑点'), null, `unusable answer: ${JSON.stringify(answer)}`);
    } finally {
      globalThis.fetch = originalFetch;
    }
  }
});

test('the model verdict is compared against a 0.7 doubt threshold', async () => {
  const { client } = createClientHarness({
    apiKey: 'sk-jev-test',
    settings: { decisionProvider: 'typesafe', decisionModel: 'jev-latest', decisionEndpoint: 'https://api.typesafe.ai/v1' },
  });
  const originalFetch = globalThis.fetch;

  const verdictFor = async noul => {
    globalThis.fetch = async () => ({ ok: true, status: 200, json: async () => ({ answers: { q_doubt: { type: 'noul', noul } } }) });
    try {
      // Awaited inside the try: returning the promise would let `finally`
      // restore the real fetch before the request is ever made.
      return await client.classifyMoment('一段记录');
    } finally {
      globalThis.fetch = originalFetch;
    }
  };

  assert.equal(await verdictFor(0.69), 'INSPIRATION');
  assert.equal(await verdictFor(0.7), 'DOUBT', 'the threshold is inclusive');
  assert.equal(await verdictFor(0.1), 'INSPIRATION');
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

test('resolveDecisionEndpoint auto-completes OpenRouter base URL ending in /v1 to decisions endpoint', async () => {
  const { client } = createClientHarness();
  assert.equal(
    client.resolveDecisionEndpoint('https://openrouter.ai/api/v1', 'openrouter'),
    'https://openrouter.ai/api/alpha/decisions'
  );
  assert.equal(
    client.resolveDecisionEndpoint('https://openrouter.ai/api/v1/', 'openrouter'),
    'https://openrouter.ai/api/alpha/decisions'
  );
  assert.equal(
    client.resolveDecisionEndpoint('https://openrouter.ai/v1', 'openrouter'),
    'https://openrouter.ai/api/alpha/decisions'
  );
  assert.equal(
    client.resolveDecisionEndpoint('https://openrouter.ai', 'openrouter'),
    'https://openrouter.ai/api/alpha/decisions'
  );
  assert.equal(
    client.resolveDecisionEndpoint('https://custom-proxy.com/api/v1', 'openrouter'),
    'https://custom-proxy.com/api/alpha/decisions'
  );
  // Preserves already full endpoints
  assert.equal(
    client.resolveDecisionEndpoint('https://openrouter.ai/api/alpha/decisions', 'openrouter'),
    'https://openrouter.ai/api/alpha/decisions'
  );
});

test('resolveDecisionEndpoint auto-completes TypeSafe base URL to systemone endpoint', async () => {
  const { client } = createClientHarness();
  assert.equal(
    client.resolveDecisionEndpoint('https://api.typesafe.ai/v1', 'typesafe'),
    'https://api.typesafe.ai/v1/systemone'
  );
  assert.equal(
    client.resolveDecisionEndpoint('https://api.typesafe.ai/v1/', 'typesafe'),
    'https://api.typesafe.ai/v1/systemone'
  );
  assert.equal(
    client.resolveDecisionEndpoint('https://api.typesafe.ai', 'typesafe'),
    'https://api.typesafe.ai/v1/systemone'
  );
  // Preserves already full endpoints
  assert.equal(
    client.resolveDecisionEndpoint('https://api.typesafe.ai/v1/systemone', 'typesafe'),
    'https://api.typesafe.ai/v1/systemone'
  );
});

test('toBaseUrl converts legacy full endpoints to user-friendly base URLs for display', async () => {
  const { client } = createClientHarness();
  assert.equal(
    client.toBaseUrl('https://openrouter.ai/api/alpha/decisions', 'openrouter'),
    'https://openrouter.ai/api/v1'
  );
  assert.equal(
    client.toBaseUrl('https://api.typesafe.ai/v1/systemone', 'typesafe'),
    'https://api.typesafe.ai/v1'
  );
  assert.equal(
    client.toBaseUrl('https://openrouter.ai/api/v1', 'openrouter'),
    'https://openrouter.ai/api/v1'
  );
});

test('testDecisionConnection and classifyMoment automatically complete base URL when fetching', async () => {
  const { client } = createClientHarness({
    apiKey: 'sk-or-test',
    settings: {
      decisionProvider: 'openrouter',
      decisionModel: 'respan/span-01-lite:free',
      decisionEndpoint: 'https://openrouter.ai/api/v1',
    },
  });

  let capturedUrl = '';
  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (url) => {
    capturedUrl = url;
    return {
      ok: true,
      status: 200,
      json: async () => ({
        model: 'respan/span-01-lite:free',
        answers: {
          q_doubt: { type: 'noul', noul: 0.85 },
        },
      }),
    };
  };

  try {
    const res = await client.classifyMoment('这个知识点怎么理解？');
    assert.equal(res, 'DOUBT');
    assert.equal(capturedUrl, 'https://openrouter.ai/api/alpha/decisions', 'fetch was sent to auto-completed decisions endpoint');

    const testRes = await client.testDecisionConnection(
      { provider: 'openrouter', model: 'respan/span-01-lite:free', endpoint: 'https://openrouter.ai/api/v1' },
      'sk-or-test'
    );
    assert.equal(testRes.success, true);
    assert.equal(capturedUrl, 'https://openrouter.ai/api/alpha/decisions', 'test connection fetch was sent to auto-completed decisions endpoint');
  } finally {
    globalThis.fetch = originalFetch;
  }
});
