# jev — 用 TypeSafe/Jev 加速 Agent 工具调用、节省 token

这是把 **TypeSafe skill** 的 *“find and judge evidence”* 模式落地成的开发工作流工具。
核心思想（skill 原文精神）：**工作流归 code，语义判断归 Jev，一次请求并行问所有候选。**

```
grep / ls / git（确定性、便宜、code 负责）
        │  候选列表
        ▼
   jev rank  ← 一次 /v1/systemone 请求，每个候选一个 Noul(0~1)
        │  相关性分数
        ▼
   排序 / 阈值 / 取 top-k（code 负责）→ 只把命中项喂给主 Agent
```

传统做法是 Agent 反复「grep → 读文件 → 发现不对 → 再 grep」，每轮都把大段内容塞进 context；
`jev rank` 把这一步压成**一次往返**，只读真正相关的那几个文件。

## 为什么省 token / 更快

- Jev **只输出结构化判断**（choice/score/noul），不是长文本 → 输出 token 极小。
- **一次请求并行评估几百个候选**（一个候选一个 question）→ 用 1 次网络往返替代 N 次工具往返。
- Jev 定价 $0.042/Mtok（**输出免费**）→ 相对生成式模型便宜 2~3 个数量级。

本仓库实测（142 个 `.kt` 文件，问“AI 学情诊断代码在哪”）：
**1 次请求 / 1.42 秒**，top 命中即 `StudyDiagnostics.kt`、`ExamAiDiagnosisSection.kt`、`AiClient.kt`。

## 用法

认证：环境变量 `TYPESAFE_API_KEY`，或本目录 `.env.local`（已 git-ignore）。

```bash
# 1) 定位相关代码（候选=文件路径）
git ls-files '*.kt' | node scripts/jev/jev.mjs rank \
    --query "哪些文件实现了学情诊断报告？" --top 8 --paths

# 2) 在日志/diff/conversation 里筛相关行（候选=每行一个）
git diff | node scripts/jev/jev.mjs rank \
    --query "哪些改动涉及数据库迁移？" --threshold 0.4 --json

# 3) 通用透传：直接把 state + questions 交给 Jev（choice/score/noul 均可）
node scripts/jev/jev.mjs ask \
    --state @state.json --questions @questions.json
```

`rank` 输出：默认 `分数\t候选`，加 `--json` 输出结构化结果（含 `usage` / `elapsed_ms`）。

| 参数 | 说明 |
| --- | --- |
| `--query <text>` | 意图描述（必填）。写成一个清晰的语义问题。 |
| `--top N` | 只输出前 N 个 |
| `--threshold T` | 只输出分数 ≥ T 的候选（在 code 里做阈值，符合 skill） |
| `--paths` | 把每行 stdin 当作文件路径候选 |
| `--json` | 输出 JSON（Agent 解析更稳） |
| `--model` | 默认 `jev-latest`（= `jev-1.13.0`） |

候选输入支持：每行一个文本；或 JSON 数组 `[{"id","text"}]`；或 `{"id":"text"}` 映射。

## 使用原则（来自 TypeSafe skill / docs）

- **code 负责确定性的事**：grep、ls、精确字符串匹配、排序、阈值、执行。Jev 只做语义判断。
- **一次请求并行问多个问题**：它们互相独立、看不到彼此答案。把可一起问的都放进一次请求。
- **阈值要在你自己的数据上校准**：`--threshold` 是示例起点，不是普适真理。
- **候选要先粗筛**：单次请求受 state 32k token 限制（工具会自动按 200/批分批并合并），
  文件上千的仓库应先 grep/目录粗筛再交给 Jev。
- **不要把 API key 写进代码或提交**：用 `TYPESAFE_API_KEY` 或 git-ignored 的 `.env.local`。

## 已验证

- `rank` 单批 142 候选 → 1 请求 / ~1.4s / 命中正确。
- `ask` 透传 choice + noul → 正确返回结构化答案。
- 触发 429/529 时会按 `Retry-After` 指数退避重试。
