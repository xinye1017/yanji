import { YanjiDataNative, NoteCategory } from '../bridge';

type DecisionProvider = 'openrouter' | 'typesafe';

interface DecisionConfig {
  provider: DecisionProvider;
  model: string;
  endpoint: string;
}

export const DEFAULT_OPENROUTER_CONFIG: DecisionConfig = {
  provider: 'openrouter',
  model: 'respan/span-01-lite:free',
  endpoint: 'https://openrouter.ai/api/v1',
};

const DEFAULT_TYPESAFE_CONFIG: DecisionConfig = {
  provider: 'typesafe',
  model: 'jev-latest',
  endpoint: 'https://api.typesafe.ai/v1',
};

/**
 * 将用户配置的基础 API Base URL 自动补全为实际调用的端点地址。
 *
 * 用户只需输入基础地址（例如 OpenRouter 输到 /v1 即可：https://openrouter.ai/api/v1），
 * 软件内部将自动补全相应的端点后缀：
 * - OpenRouter: .../api/alpha/decisions
 * - TypeSafe: .../v1/systemone
 *
 * 容错规则：
 * 1. 若用户已经输入了完整的决策端点（以 /alpha/decisions 或 /systemone 结尾），直接保留；
 * 2. 自动移除末尾斜杠；
 * 3. 自动剥离误粘贴的 /chat/completions；
 * 4. OpenRouter 支持 /api/v1、/v1、/api 或直接域名格式，统一映射至 decisions 端点。
 */
export function resolveDecisionEndpoint(
  baseUrlOrEndpoint: string,
  provider: DecisionProvider
): string {
  let url = (baseUrlOrEndpoint || '').trim();
  if (!url) {
    url = provider === 'typesafe'
      ? DEFAULT_TYPESAFE_CONFIG.endpoint
      : DEFAULT_OPENROUTER_CONFIG.endpoint;
  }
  url = url.replace(/\/+$/, '');

  // 若已经是完整的决策端点，直接返回
  if (
    url.endsWith('/api/alpha/decisions') ||
    url.endsWith('/alpha/decisions') ||
    url.endsWith('/systemone')
  ) {
    return url;
  }

  // 剥离可能误粘的 /chat/completions 后缀
  url = url.replace(/\/chat\/completions$/, '');

  if (provider === 'openrouter') {
    if (url.endsWith('/api/v1')) {
      return url.replace(/\/api\/v1$/, '/api/alpha/decisions');
    }
    if (url.endsWith('/v1')) {
      return url.replace(/\/v1$/, '/api/alpha/decisions');
    }
    if (url.endsWith('/api')) {
      return `${url}/alpha/decisions`;
    }
    return `${url}/api/alpha/decisions`;
  } else {
    if (url.endsWith('/v1')) {
      return `${url}/systemone`;
    }
    return `${url}/v1/systemone`;
  }
}

/**
 * 将存储的端点格式化为设置页展示的 API Base URL。
 * 消除历史配置中冗余的内部后缀（/api/alpha/decisions -> /api/v1，/systemone -> /v1）。
 */
export function toBaseUrl(
  endpointOrBase: string,
  provider: DecisionProvider
): string {
  let url = (endpointOrBase || '').trim();
  if (!url) {
    return provider === 'typesafe'
      ? DEFAULT_TYPESAFE_CONFIG.endpoint
      : DEFAULT_OPENROUTER_CONFIG.endpoint;
  }
  url = url.replace(/\/+$/, '');

  if (provider === 'openrouter') {
    if (url.endsWith('/api/alpha/decisions')) {
      return url.replace(/\/api\/alpha\/decisions$/, '/api/v1');
    }
    if (url.endsWith('/alpha/decisions')) {
      return url.replace(/\/alpha\/decisions$/, '/api/v1');
    }
  } else {
    if (url.endsWith('/v1/systemone')) {
      return url.replace(/\/v1\/systemone$/, '/v1');
    }
    if (url.endsWith('/systemone')) {
      return url.replace(/\/systemone$/, '');
    }
  }
  return url;
}

/**
 * 判定为「疑点」的概率门槛。
 *
 * Mercury Decide / Jev 返回的是**校准过的 P(疑点)**，判定轴只有一条：「作者是否
 * 还留下一个没解决的问题」。0.7 是刻意偏保守的取值：宁可漏标、由用户手动点一下，
 * 也不愿把心得大量误标成疑点污染待解决列表。
 */
const DOUBT_THRESHOLD = 0.7;

/**
 * 判定轴：问题是否**仍然开放**。
 *
 * 不用「疑点类名词 vs 灵感类名词」的清单，是因为两边的词天然重叠——
 * 「今天这道题做错了」到底算错题还是算状态记录，没有判据。改用单一的语义标准后，
 * 同一句话只有一种归属：作者还困在里面就是疑点，已经得出了结论就是灵感。
 * 这也和产品里「待解决 / 已解决」的流程是同一个轴。
 */
const DOUBT_INSTRUCTIONS =
  '这条学习记录里，作者是否还留下了一个尚未解决的问题？' +
  '若记录中既有已经想通的部分、也有仍然卡住的部分，以「是否还有一处没解决」为准。';

const DOUBT_CRITERIA_TRUE =
  '作者仍被某个知识点、步骤或题目困住：提出尚未解答的疑问、说明某处没弄懂、' +
  '记下错题但还没弄清错因，或卡在某一步无法继续。';

const DOUBT_CRITERIA_FALSE =
  '作者没有留下未解决的问题：写的是想通的心得、总结出的方法、完成情况或状态感受；' +
  '即使提到做错的题，也已经给出或弄清了出错原因。';

/**
 * 「是否疑点」的判定问题。
 *
 * `noul` 是官方唯一的 yes/no 原语，返回 P(true) 而不是一段需要解析的文本。
 * OpenRouter 的 Decisions API 与 TypeSafe 的 System One 共用同一套 schema，
 * 所以不存在「这个 surface 要用 choice、那个要用 noul」的分支。
 */
function doubtQuestion(): Record<string, unknown> {
  return {
    type: 'noul',
    instructions: DOUBT_INSTRUCTIONS,
    criteria: { true: DOUBT_CRITERIA_TRUE, false: DOUBT_CRITERIA_FALSE },
  };
}

/**
 * 从决策响应里读出 P(疑点)，读不到一律返回 `null`。
 *
 * 严格按官方契约取值：
 * 1. 只认 `noul` 原语。缺失、`type` 不符或值不是数字，一律视为**没有答案**，
 *    由调用方决定降级——在解析层悄悄当成灵感，等于把接口异常写成业务事实。
 * 2. **不用 `confidence` 顶替概率**。文档明确 `confidence` 是模型自身的统计量，
 *    并不等于所选选项的概率，两者不可互换。
 */
function readDoubtProbability(answers: unknown): number | null {
  const answer = (answers as { q_doubt?: { type?: unknown; noul?: unknown } } | undefined)?.q_doubt;
  if (!answer || answer.type !== 'noul' || typeof answer.noul !== 'number') {
    return null;
  }
  const probability = answer.noul;
  return Number.isFinite(probability) && probability >= 0 && probability <= 1 ? probability : null;
}

/**
 * 依据学习随笔正文，通过决策模型判断属于「疑点」还是「灵感」。
 *
 * 铁律约束：
 * 1. 快速判定（上限 1500ms），绝不阻塞用户正常随笔保存；
 * 2. 严格使用决策模型（Decisions API / System One），非传统 Chat Completions。
 *
 * 返回 `null` 表示**这次没拿到任何答案**（未配置 Key、超时、接口异常、响应
 * 无法解析），与「模型判定为灵感」严格区分。调用方据此决定降级：新建记录可以
 * 把 null 当成灵感（这是 `saveQuickNote` 的既有回落语义），**编辑已有记录绝不能**
 * ——那会把一条用户亲手标成疑点的记录在断网时静默改写成灵感。
 */
export async function classifyMoment(
  content: string,
  providedConfig?: Partial<DecisionConfig>
): Promise<NoteCategory | null> {
  const text = content.trim();
  if (!text) {
    return null;
  }

  try {
    const settings = await YanjiDataNative.getUserSettings().catch(() => null);
    const provider: DecisionProvider =
      providedConfig?.provider ||
      (settings?.decisionProvider === 'typesafe' ? 'typesafe' : 'openrouter');
    const model =
      providedConfig?.model ||
      settings?.decisionModel ||
      (provider === 'typesafe'
        ? DEFAULT_TYPESAFE_CONFIG.model
        : DEFAULT_OPENROUTER_CONFIG.model);
    const rawEndpoint =
      providedConfig?.endpoint ||
      settings?.decisionEndpoint ||
      (provider === 'typesafe'
        ? DEFAULT_TYPESAFE_CONFIG.endpoint
        : DEFAULT_OPENROUTER_CONFIG.endpoint);
    const endpoint = resolveDecisionEndpoint(rawEndpoint, provider);

    const apiKey = await YanjiDataNative.getDecisionApiKey().catch(() => '');
    if (!apiKey || !apiKey.trim()) {
      return null;
    }

    // A named field rather than a bare string: the shared state is the one thing
    // the model conditions on, and naming it keeps the request self-describing.
    const body: Record<string, unknown> = {
      model: model.trim(),
      state: { note: text },
      questions: { q_doubt: doubtQuestion() },
    };

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 1500);

    try {
      const res = await fetch(endpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${apiKey.trim()}`,
        },
        body: JSON.stringify(body),
        signal: controller.signal,
      });

      if (!res.ok) {
        return null;
      }

      const probability = readDoubtProbability((await res.json())?.answers);
      if (probability === null) {
        return null;
      }
      return probability >= DOUBT_THRESHOLD ? 'DOUBT' : 'INSPIRATION';
    } finally {
      // Cleared on every path: an abort on the error path would otherwise leave
      // the timer running and fire a spurious abort later.
      clearTimeout(timer);
    }
  } catch {
    return null;
  }
}

/**
 * 设置页测试决策模型连通性。
 */
export async function testDecisionConnection(
  config: DecisionConfig,
  apiKey: string
): Promise<{ success: boolean; message: string; model?: string; probability?: number }> {
  const key = apiKey.trim();
  if (!key) {
    return { success: false, message: '请先输入 API Key' };
  }

  const endpoint = resolveDecisionEndpoint(config.endpoint, config.provider);
  const testState = '今天在复习高等数学时，关于柯西中值定理的构造辅助函数方法存在疑惑，没有弄懂。';

  const body: Record<string, unknown> = {
    model: config.model.trim(),
    state: { note: testState },
    questions: { q_doubt: doubtQuestion() },
  };

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 6000);

  try {
    const res = await fetch(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${key}`,
      },
      body: JSON.stringify(body),
      signal: controller.signal,
    });

    if (res.status === 401 || res.status === 403) {
      return { success: false, message: 'API Key 无效或未授权 (HTTP ' + res.status + ')' };
    }
    if (!res.ok) {
      return { success: false, message: '接口返回错误 (HTTP ' + res.status + ')' };
    }

    const data = await res.json();
    const probability = readDoubtProbability(data?.answers);
    if (probability === null) {
      return { success: false, message: '模型返回数据未包含可用的 noul 判定结果' };
    }

    const answeredModel = typeof data.model === 'string' && data.model ? data.model : config.model;
    const tag = probability >= DOUBT_THRESHOLD ? '疑点' : '灵感';
    return {
      success: true,
      message: `连通成功！模型响应: ${answeredModel}，测试文本判定为:【${tag}】(疑点概率 ${(probability * 100).toFixed(0)}%)`,
      model: answeredModel,
      probability,
    };
  } catch (err: unknown) {
    const msg = err instanceof Error && err.name === 'AbortError' ? '请求超时 (6s)' : '网络连接失败';
    return { success: false, message: msg };
  } finally {
    clearTimeout(timer);
  }
}
