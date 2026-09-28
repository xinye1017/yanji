package com.example.yanji.data.ai

import android.util.Log
import com.example.yanji.data.AiAnalysis
import com.example.yanji.data.StudyDiagnosticSnapshot
import com.example.yanji.data.UserSettings
import org.json.JSONObject
import java.util.UUID

/**
 * 把第三方 AI 返回的**自由文本**解析成结构化的 [AiAnalysis]。
 *
 * 真实 LLM 的输出并不总是"单个干净 JSON"：可能包裹思考标签、```json 代码块、尾随逗号、
 * 改名换姓的字段、显式 null，甚至干脆退化成纯文本。因此解析按**三级降级**尽力而为：
 *
 *  1. [parseStrictJson] —— 提取首个 JSON 对象后用 `JSONObject` 解析；
 *  2. [parseByRegex]    —— JSON 坏掉时，用正则从残片里捞出字段；
 *  3. [parseFallback]   —— 连 JSON 残片都没有时，把文本当作带 bullet 的纯文本抽取。
 *
 * 三级共享同一套"这句话是否有意义"的清洗逻辑（[cleanString]）与同一套兜底文案，
 * 保证无论走到哪一级，产出的 [AiAnalysis] 都不会泄漏 `null`、`undefined` 或孤零零的括号。
 *
 * 纯函数、无网络、无 Android 框架依赖（仅 [Log]），可被 JVM 单元测试完整覆盖。
 */
internal class AiDiagnosticParser {

    fun parse(
        content: String,
        snapshot: StudyDiagnosticSnapshot,
        settings: UserSettings
    ): AiAnalysis? {
        val sanitized = content
            .replace(Regex("<(think|thought|reasoning|details)>[\\s\\S]*?</\\1>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("```(?:json)?|```", RegexOption.IGNORE_CASE), "")
            .trim()

        extractFirstJsonObject(sanitized)?.let { jsonString ->
            val parsed = runCatching { parseStrictJson(jsonString, snapshot, settings) }
                .onFailure { Log.e(TAG, "parseAiDiagnostic JSON parse failed: ${it.message}") }
                .getOrNull()
            if (parsed != null) return parsed
        }

        parseByRegex(sanitized, snapshot, settings)?.let { return it }

        return parseFallback(sanitized, snapshot, settings)
    }

    // ---------------------------------------------------------------- 1. 严格 JSON

    private fun parseStrictJson(
        jsonString: String,
        snapshot: StudyDiagnosticSnapshot,
        settings: UserSettings
    ): AiAnalysis? {
        // 容忍 LLM 常见的尾随逗号：{"a":1,} / [1,2,]
        val cleanJson = jsonString.replace(Regex(",+\\s*([}\\]])"), "$1")
        val json = JSONObject(cleanJson)

        fun optCleanString(vararg keys: String): String? {
            for (k in keys) {
                if (!json.has(k) || json.isNull(k)) continue
                val cleaned = cleanString(json.optString(k, ""))
                if (cleaned != null) return cleaned
            }
            return null
        }

        fun stringList(vararg keys: String): List<String> {
            for (k in keys) {
                val array = json.optJSONArray(k) ?: continue
                val list = (0 until array.length()).mapNotNull { index ->
                    if (array.isNull(index)) null else cleanString(array.optString(index))
                }
                if (list.isNotEmpty()) return list
            }
            return emptyList()
        }

        val strengths = stringList("strengths", "highlights", "advantages").take(5).map { it.take(220) }
        val weaknesses = stringList("weaknesses", "shortcomings", "risks", "areas_for_improvement").take(5).map { it.take(220) }
        val suggestions = stringList("threeDayPlan", "three_day_plan", "suggestions", "plan", "actions", "actionPlan").take(5).map { it.take(250) }
        val rawOverview = optCleanString("overview", "summary", "academicOverview", "analysis", "evaluation", "description")
        val rawTrend = optCleanString("trendAnalysis", "trend_analysis", "trend")

        if (rawOverview == null && suggestions.isEmpty() && strengths.isEmpty()) return null

        return buildAnalysis(
            snapshot = snapshot,
            settings = settings,
            overview = defaultOverview(rawOverview, strengths, suggestions),
            strengths = strengths,
            weaknesses = weaknesses,
            trend = defaultTrend(rawTrend, snapshot),
            suggestions = suggestions.ifEmpty { listOf("保持现有良好专注节奏，稳步推进各科复习任务。") }
        )
    }

    // ---------------------------------------------------------------- 2. 正则残片

    private fun parseByRegex(
        text: String,
        snapshot: StudyDiagnosticSnapshot,
        settings: UserSettings
    ): AiAnalysis? {
        fun unescape(str: String): String =
            str.replace("\\\"", "\"")
                .replace("\\n", "\n")
                .replace("\\r", "")
                .replace("\\t", " ")
                .replace("\\\\", "\\")

        fun extractStringField(vararg keys: String): String? {
            for (k in keys) {
                val pattern = Regex("\"$k\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"", RegexOption.IGNORE_CASE)
                val match = pattern.find(text)
                if (match != null) {
                    val cleaned = cleanString(unescape(match.groupValues[1]))
                    if (cleaned != null) return cleaned
                }
            }
            return null
        }

        fun extractArrayField(vararg keys: String): List<String> {
            for (k in keys) {
                val blockMatch = Regex("\"$k\"\\s*:\\s*\\[([\\s\\S]*?)\\]", RegexOption.IGNORE_CASE).find(text) ?: continue
                val items = Regex("\"((?:\\\\.|[^\"\\\\])*)\"")
                    .findAll(blockMatch.groupValues[1])
                    .mapNotNull { m -> cleanString(unescape(m.groupValues[1])) }
                    .toList()
                if (items.isNotEmpty()) return items
            }
            return emptyList()
        }

        val rawOverview = extractStringField("overview", "summary", "academicOverview", "analysis", "evaluation", "description")
        val strengths = extractArrayField("strengths", "highlights", "advantages").take(5).map { it.take(220) }
        val weaknesses = extractArrayField("weaknesses", "shortcomings", "risks", "areas_for_improvement").take(5).map { it.take(220) }
        val rawTrend = extractStringField("trendAnalysis", "trend_analysis", "trend")
        val suggestions = extractArrayField("threeDayPlan", "three_day_plan", "suggestions", "plan", "actions", "actionPlan").take(5).map { it.take(250) }

        if (rawOverview == null && strengths.isEmpty() && suggestions.isEmpty()) return null

        return buildAnalysis(
            snapshot = snapshot,
            settings = settings,
            overview = defaultOverview(rawOverview, strengths, suggestions),
            strengths = strengths,
            weaknesses = weaknesses,
            trend = defaultTrend(rawTrend, snapshot),
            suggestions = suggestions.ifEmpty { listOf("保持现有良好专注节奏，稳步推进各科复习任务。") }
        )
    }

    // ---------------------------------------------------------------- 3. 纯文本兜底

    private fun parseFallback(
        text: String,
        snapshot: StudyDiagnosticSnapshot,
        settings: UserSettings
    ): AiAnalysis? {
        if (text.isBlank()) return null

        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null

        val overview = lines.firstOrNull {
            !it.startsWith("#") && !it.startsWith("•") && !it.startsWith("-") && !it.startsWith("*") &&
                isMeaningfulText(it)
            } ?: lines.firstOrNull { isMeaningfulText(it) }
            ?: "阶段复习按计划稳步推进中，请保持良好的学习节奏。"

        val bullets = lines.filter {
            (it.startsWith("•") || it.startsWith("-") || it.startsWith("*") || it.matches(Regex("^\\d+[.、].*"))) &&
                isMeaningfulText(it.replace(Regex("^[•\\-*\\d.、\\s]+"), ""))
        }.map { it.replace(Regex("^[•\\-*\\d.、\\s]+"), "").trim() }

        val strengths = bullets.take(3).map { it.take(220) }
        val suggestions = bullets.let { if (it.size > 3) it.drop(3).take(3) else it.take(3) }.map { it.take(250) }

        val trend = if (snapshot.previousSessionCount == 0 && snapshot.previousExamCount == 0) {
            "前一同长周期没有可比的专注或模考记录，暂时无法判断变化趋势。"
        } else {
            "阶段复习按计划稳步推进。"
        }

        return buildAnalysis(
            snapshot = snapshot,
            settings = settings,
            overview = overview,
            strengths = strengths,
            weaknesses = emptyList(),
            trend = trend,
            suggestions = suggestions.ifEmpty { listOf("保持现有专注节奏，稳步推进复习。") }
        )
    }

    // ---------------------------------------------------------------- 共享helpers

    /**
     * 判断一段从 AI 抠出来的字符串是否"有内容"。
     * 过滤 null / undefined 字面量、过短片段，以及整段只剩括号/标点/空白的噪声。
     */
    private fun cleanString(value: String?): String? {
        if (value == null) return null
        val trimmed = value.trim()
        if (trimmed.length < 2) return null
        if (trimmed.equals("null", ignoreCase = true) || trimmed.equals("undefined", ignoreCase = true)) return null
        if (trimmed.count { it.isLetterOrDigit() } < 2) return null
        val brackets = setOf('{', '}', '[', ']', '(', ')', '（', '）', '【', '】', '<', '>', '"', '\'', ':', ';', ',', '、')
        if (trimmed.all { it in brackets || it.isWhitespace() }) return null
        return trimmed
    }

    /** 纯文本兜底专用的"有意义"判定：比 [cleanString] 更严格，额外排斥以括号开头或含 `":` 的代码残片。 */
    private fun isMeaningfulText(str: String?): Boolean {
        if (str == null) return false
        val t = str.trim()
        if (t.length < 3) return false
        if (t.equals("null", ignoreCase = true) || t.equals("undefined", ignoreCase = true)) return false
        if (t.count { it.isLetterOrDigit() } < 2) return false
        if (t.startsWith("{") || t.startsWith("}") || t.startsWith("[") || t.startsWith("]")) return false
        if (t.startsWith("\"") && t.contains("\":")) return false
        val brackets = setOf('{', '}', '[', ']', '(', ')', '（', '）', '【', '】', '<', '>', '"', '\'', ':', ';', ',', '、', '。')
        if (t.all { it in brackets || it.isWhitespace() }) return false
        return true
    }

    private fun defaultOverview(raw: String?, strengths: List<String>, suggestions: List<String>): String = when {
        raw != null -> raw
        strengths.isNotEmpty() -> "本阶段复习稳步推进中，已提炼 ${strengths.size} 项主要优势，建议对照下方重点展开针对性巩固。"
        suggestions.isNotEmpty() -> "已根据你的专注投入与复习进度完成学情评估，具体行动方案见下方建议。"
        else -> "已结合阶段学情完成分析，详见以下诊断要点与建议。"
    }

    private fun defaultTrend(raw: String?, snapshot: StudyDiagnosticSnapshot): String = when {
        raw != null -> raw.take(400)
        snapshot.previousSessionCount == 0 && snapshot.previousExamCount == 0 ->
            "前一同长周期没有可比的专注或模考记录，暂时无法判断变化趋势。"
        else -> "阶段复习按计划稳步推进中，请保持良好的学习节奏。"
    }

    private fun buildAnalysis(
        snapshot: StudyDiagnosticSnapshot,
        settings: UserSettings,
        overview: String,
        strengths: List<String>,
        weaknesses: List<String>,
        trend: String,
        suggestions: List<String>
    ): AiAnalysis = AiAnalysis(
        id = UUID.randomUUID().toString(),
        periodStart = snapshot.periodStart,
        periodEnd = snapshot.periodEnd,
        provider = settings.aiProvider.ifBlank { "自定义 AI" },
        model = settings.aiModel.ifBlank { AiProtocol.DEFAULT_MODEL },
        requestSnapshot = snapshot.requestSnapshot(),
        overview = overview.take(600),
        strengths = strengths,
        weaknesses = weaknesses,
        trendAnalysis = trend,
        suggestions = suggestions
    )

    /**
     * 从文本里抠出**第一个完整 JSON 对象**（正确处理字符串字面量里的花括号与转义）。
     * 找不到配对的 `}` 时，退化为"第一个 `{` 到最后一个 `}``"，把残片交给后续解析尝试。
     */
    private fun extractFirstJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in start until text.length) {
            val c = text[i]
            if (escape) {
                escape = false
                continue
            }
            if (c == '\\') {
                escape = true
                continue
            }
            if (c == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (c == '{') depth++
                else if (c == '}') {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        val last = text.lastIndexOf('}')
        return if (last > start) text.substring(start, last + 1) else null
    }

    private companion object {
        const val TAG = "YanjiAI"
    }
}
