package com.example.yanji.ui.chat

import com.example.yanji.data.AiAction
import com.example.yanji.data.AiActionType
import com.example.yanji.data.AiBlockKind
import com.example.yanji.data.AiResponse
import com.example.yanji.data.AiResponseBlock
import java.util.UUID

/**
 * 卷卷回复的渐进式结构化解析器。
 *
 * 设计目标：
 *  1. 优先识别新 token（【诊断】/原因：/①/[动作:TYPE|LABEL]/[追问:LABEL]），
 *     把回复拆为 Diagnosis/Evidence/Steps/Actions/Followups；
 *  2. 一旦整段没有任何 token，**降级到旧 fallback**（见 [fallbackParse]），
 *     完全保留历史消息的渲染行为，**不破坏现有聊天功能**；
 *  3. 解析过程中绝不抛异常：损坏的 action / 追问 token 一律静默忽略，
 *     但**不会丢弃原始 token 文本**——它会落到 MAIN 块里照常渲染。
 *
 * 这样可以让 LLM 在「自由文本」与「结构化」之间渐进迁移，UI 永远有可显示的内容。
 */
object AiResponseParser {

    private val DIAGNOSIS_HEADER = Regex("""^【诊断】[:：]?\s*(.+)$""")
    private val EVIDENCE_HEADER = Regex("""^【证据】[:：]?\s*(.+)$""")
    private val ACTION_TOKEN = Regex("""\[动作[:：]([A-Z_]+)\|([^\]]+)]""")
    private val FOLLOWUP_TOKEN = Regex("""\[追问[:：]([^\]]+)]""")
    // 「原因：xxx」 顶格或独立成行
    private val CAUSE_LINE = Regex("""^原因[:：]\s*(.+)$""")
    // ①/②/③ 编号步骤，也兼容 1./2.
    private val NUMBERED_STEP = Regex("""^[①②③④⑤⑥⑦⑧⑨⑩]|^[0-9]+\.""")
    // 行动建议（要把…加为明早计划吗？）
    private val ASK_PLAN_LINE = Regex("""^要将『(.+)』加为明早计划吗[？?]?$""")
    // 拥抱安抚
    private val EMPATHY_LINE = Regex("""^(抱抱你[^！!]*[!！]?)$""")

    /**
     * @param content 原始 AI 回复
     * @return 结构化结果。即使 token 损坏也会保证至少有一段 MAIN。
     */
    fun parse(content: String): AiResponse {
        if (content.isBlank()) {
            return AiResponse()
        }
        val rawLines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (rawLines.isEmpty()) return AiResponse()

        // 第一轮扫描：找出是否存在至少一个「新 token」特征。
        val hasNewTokens = rawLines.any { line ->
            DIAGNOSIS_HEADER.matches(line) ||
                EVIDENCE_HEADER.matches(line) ||
                ACTION_TOKEN.containsMatchIn(line) ||
                FOLLOWUP_TOKEN.containsMatchIn(line) ||
                CAUSE_LINE.matches(line)
        }

        return if (!hasNewTokens) {
            fallbackParse(content)
        } else {
            structuredParse(rawLines)
        }
    }

    private fun structuredParse(lines: List<String>): AiResponse {
        var diagnosis: String? = null
        var evidence: String? = null
        val steps = mutableListOf<String>()
        val actions = mutableListOf<AiAction>()
        val followups = mutableListOf<String>()
        val mainBlocks = mutableListOf<AiResponseBlock>()

        for (raw in lines) {
            // 1. 【诊断】xxx
            DIAGNOSIS_HEADER.matchEntire(raw)?.let {
                diagnosis = it.groupValues[1].trim()
                return@let
            }
            // 2. 【证据】xxx
            EVIDENCE_HEADER.matchEntire(raw)?.let {
                evidence = it.groupValues[1].trim()
                return@let
            }
            // 3. 原因：xxx（追加到证据链，不覆盖【证据】内容）
            CAUSE_LINE.matchEntire(raw)?.let {
                val cause = it.groupValues[1].trim()
                evidence = evidence?.let { prev -> "$prev\n原因：$cause" } ?: cause
                return@let
            }
            // 4. 动作 token：行内任意位置可出现多次
            val actionMatches = ACTION_TOKEN.findAll(raw).toList()
            if (actionMatches.isNotEmpty()) {
                // 行内 token 走 actions 列表，原行文本不再入 main（避免重复显示）
                for (m in actionMatches) {
                    val type = parseActionType(m.groupValues[1]) ?: continue
                    val label = m.groupValues[2].trim()
                    if (label.isNotEmpty()) {
                        actions += AiAction(
                            id = UUID.randomUUID().toString(),
                            type = type,
                            label = label
                        )
                    }
                }
                continue
            }
            // 5. 追问 token
            val followupMatches = FOLLOWUP_TOKEN.findAll(raw).toList()
            if (followupMatches.isNotEmpty()) {
                for (m in followupMatches) {
                    val q = m.groupValues[1].trim()
                    if (q.isNotEmpty()) followups += q
                }
                continue
            }
            // 6. 编号步骤
            if (NUMBERED_STEP.containsMatchIn(raw)) {
                steps += stripBullet(raw)
                continue
            }
            // 7. 询问计划
            ASK_PLAN_LINE.matchEntire(raw)?.let {
                actions += AiAction(
                    id = UUID.randomUUID().toString(),
                    type = AiActionType.CREATE_PLAN,
                    label = it.groupValues[1].trim()
                )
                return@let
            }
            // 8. 拥抱安抚
            EMPATHY_LINE.matchEntire(raw)?.let {
                if (diagnosis == null) diagnosis = it.groupValues[1].trim()
                return@let
            }
            // 其余正文
            mainBlocks += AiResponseBlock(AiBlockKind.MAIN, stripBold(raw))
        }

        // 步骤汇总为一个块；followups 单独成块便于 FlowRow 渲染
        val blocks = buildList {
            if (steps.isNotEmpty()) {
                add(AiResponseBlock(AiBlockKind.STEPS, steps.joinToString("\n")))
            }
            addAll(mainBlocks)
        }

        return AiResponse(
            diagnosis = diagnosis,
            evidence = evidence,
            blocks = blocks,
            actions = actions,
            followups = followups.distinct()
        )
    }

    private fun parseActionType(raw: String): AiActionType? = runCatching {
        AiActionType.valueOf(raw)
    }.getOrNull()

    /**
     * 旧版本的纯文本回退解析。
     * 行为与重构前的 parseAiMessage 保持一致：
     *   - 编号行 → steps
     *   - 「要将…加为明早计划吗？」 → actionSuggestion
     *   - 「抱抱你」 → empathy
     *   - 「> xxx」 → calloutBox
     *   - 其余 → paragraphs
     */
    internal fun fallbackParse(content: String): AiResponse {
        val lines = content.lines().map { it.trim() }.filter { it.isNotEmpty() }

        var empathy: String? = null
        val paragraphs = mutableListOf<String>()
        var calloutBox: String? = null
        val steps = mutableListOf<String>()
        var actionLabel: String? = null

        for (line in lines) {
            when {
                line.startsWith("抱抱你") || line.contains("抱抱你，别自责") -> {
                    empathy = "抱抱你，别自责！"
                }
                line.startsWith("要将『") || (line.contains("加为") && line.endsWith("吗？")) -> {
                    actionLabel = line
                }
                line.contains("今天想聊聊考场时间分配") || line.startsWith("> ") -> {
                    calloutBox = line.removePrefix("> ").trim()
                }
                line.matches(Regex("^[0-9]+\\..*")) || NUMBERED_STEP.containsMatchIn(line) -> {
                    steps += line.replace("*", "").trim()
                }
                else -> {
                    val clean = line.replace("**", "")
                    if (clean.isNotBlank()) paragraphs += clean
                }
            }
        }

        val blocks = buildList {
            calloutBox?.let { add(AiResponseBlock(AiBlockKind.MAIN, it)) }
            paragraphs.forEach { add(AiResponseBlock(AiBlockKind.MAIN, it)) }
            if (steps.isNotEmpty()) {
                add(AiResponseBlock(AiBlockKind.STEPS, steps.joinToString("\n")))
            }
        }
        val actions = actionLabel?.let { label ->
            listOf(
                AiAction(
                    id = UUID.randomUUID().toString(),
                    type = AiActionType.CREATE_PLAN,
                    label = label
                        .replace("要将『", "")
                        .replace("』加为明早计划吗？", "")
                        .replace("吗？", "")
                        .trim()
                )
            )
        }.orEmpty()
        return AiResponse(
            diagnosis = empathy,
            evidence = calloutBox,
            blocks = blocks,
            actions = actions
        )
    }

    private fun stripBullet(line: String): String =
        line.replace(Regex("""^[①②③④⑤⑥⑦⑧⑨⑮⑩]"""), "")
            .replace(Regex("""^[0-9]+\.\s*"""), "")
            .replace("*", "")
            .trim()

    private fun stripBold(text: String): String = text.replace("**", "").trim()
}
