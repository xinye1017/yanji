import { YanjiDataNative, NoteCategory } from '../bridge';

export type DecisionProvider = 'openrouter' | 'typesafe';

export interface DecisionConfig {
  provider: DecisionProvider;
  model: string;
  endpoint: string;
}

export const DEFAULT_OPENROUTER_CONFIG: DecisionConfig = {
  provider: 'openrouter',
  model: 'respan/span-01-lite:free',
  endpoint: 'https://openrouter.ai/api/alpha/decisions',
};

export const DEFAULT_TYPESAFE_CONFIG: DecisionConfig = {
  provider: 'typesafe',
  model: 'jev-latest',
  endpoint: 'https://api.typesafe.ai/v1/systemone',
};

/** 判定为「疑点」的置信度阈值：低于 0.7 自动落入「灵感」 */
export const DOUBT_THRESHOLD = 0.7;

const DOUBT_INSTRUCTIONS =
  '判断这段考研学习记录是否记录了一个疑点、卡点、困惑、做错的题目或尚未弄懂的知识点？';

const DOUBT_CRITERIA_TRUE =
  '记录了疑问、模糊知识点、做题错误原因、未弄清的步骤或卡点';

const DOUBT_CRITERIA_FALSE =
  '记录了心得、灵感、感悟、做题状态、学习方法或普通记事';

/**
 * 依据学习随笔正文，通过决策模型判断属于「疑点」还是「灵感」。
 *
 * 铁律约束：
 * 1. 快速判定（上限 1500ms），绝不阻塞用户正常随笔保存；
 * 2. 失败安全（Fail-Closed）：未配置 Key、网络超时或接口异常时静默回落为 INSPIRATION；
 * 3. 严格使用决策模型（Decisions API / System One），非传统 Chat Completions。
 */
export async function classifyMoment(
  content: string,
  providedConfig?: Partial<DecisionConfig>
): Promise<NoteCategory> {
  const text = content.trim();
  if (!text) {
    return 'INSPIRATION';
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
    const endpoint =
      providedConfig?.endpoint ||
      settings?.decisionEndpoint ||
      (provider === 'typesafe'
        ? DEFAULT_TYPESAFE_CONFIG.endpoint
        : DEFAULT_OPENROUTER_CONFIG.endpoint);

    const apiKey = await YanjiDataNative.getDecisionApiKey().catch(() => '');
    if (!apiKey || !apiKey.trim()) {
      return 'INSPIRATION';
    }

    const isNoul = provider === 'openrouter' || endpoint.includes('openrouter.ai');

    const body: Record<string, unknown> = {
      model: model.trim(),
      state: text,
      questions: {
        q_doubt: isNoul
          ? {
              type: 'noul',
              instructions: DOUBT_INSTRUCTIONS,
              criteria: {
                true: DOUBT_CRITERIA_TRUE,
                false: DOUBT_CRITERIA_FALSE,
              },
            }
          : {
              type: 'choice',
              instructions: DOUBT_INSTRUCTIONS,
              criteria: {
                match: DOUBT_CRITERIA_TRUE,
                other: DOUBT_CRITERIA_FALSE,
              },
            },
      },
    };

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 1500);

    const res = await fetch(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${apiKey.trim()}`,
      },
      body: JSON.stringify(body),
      signal: controller.signal,
    });
    clearTimeout(timer);

    if (!res.ok) {
      return 'INSPIRATION';
    }

    const data = await res.json();
    const answer = data?.answers?.q_doubt;
    if (!answer) {
      return 'INSPIRATION';
    }

    let probability = 0;
    if (answer.type === 'noul' && typeof answer.noul === 'number') {
      probability = answer.noul;
    } else if (typeof answer.probabilities?.match === 'number') {
      probability = answer.probabilities.match;
    } else if (typeof answer.confidence === 'number') {
      probability =
        answer.choice === 'match' ? answer.confidence : 1 - answer.confidence;
    }

    return probability >= DOUBT_THRESHOLD ? 'DOUBT' : 'INSPIRATION';
  } catch {
    return 'INSPIRATION';
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

  const isNoul = config.provider === 'openrouter' || config.endpoint.includes('openrouter.ai');
  const testState = '今天在复习高等数学时，关于柯西中值定理的构造辅助函数方法存在疑惑，没有弄懂。';

  const body: Record<string, unknown> = {
    model: config.model.trim(),
    state: testState,
    questions: {
      q_doubt: isNoul
        ? {
            type: 'noul',
            instructions: DOUBT_INSTRUCTIONS,
            criteria: {
              true: DOUBT_CRITERIA_TRUE,
              false: DOUBT_CRITERIA_FALSE,
            },
          }
        : {
            type: 'choice',
            instructions: DOUBT_INSTRUCTIONS,
            criteria: {
              match: DOUBT_CRITERIA_TRUE,
              other: DOUBT_CRITERIA_FALSE,
            },
          },
    },
  };

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 6000);

  try {
    const res = await fetch(config.endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${key}`,
      },
      body: JSON.stringify(body),
      signal: controller.signal,
    });
    clearTimeout(timer);

    if (res.status === 401 || res.status === 403) {
      return { success: false, message: 'API Key 无效或未授权 (HTTP ' + res.status + ')' };
    }
    if (!res.ok) {
      return { success: false, message: '接口返回错误 (HTTP ' + res.status + ')' };
    }

    const data = await res.json();
    const answer = data?.answers?.q_doubt;
    if (!answer) {
      return { success: false, message: '模型返回数据未包含决策结果' };
    }

    let probability = 0;
    if (answer.type === 'noul' && typeof answer.noul === 'number') {
      probability = answer.noul;
    } else if (typeof answer.probabilities?.match === 'number') {
      probability = answer.probabilities.match;
    } else if (typeof answer.confidence === 'number') {
      probability =
        answer.choice === 'match' ? answer.confidence : 1 - answer.confidence;
    }

    const answeredModel = typeof data.model === 'string' && data.model ? data.model : config.model;
    const tag = probability >= DOUBT_THRESHOLD ? '疑点' : '灵感';
    return {
      success: true,
      message: `连通成功！模型响应: ${answeredModel}，测试文本判定为:【${tag}】(置信度 ${(probability * 100).toFixed(0)}%)`,
      model: answeredModel,
      probability,
    };
  } catch (err: unknown) {
    clearTimeout(timer);
    const msg = err instanceof Error && err.name === 'AbortError' ? '请求超时 (6s)' : '网络连接失败';
    return { success: false, message: msg };
  }
}
