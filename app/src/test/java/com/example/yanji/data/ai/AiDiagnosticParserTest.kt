package com.example.yanji.data.ai

import com.example.yanji.data.StudyDiagnosticSnapshot
import com.example.yanji.data.UserSettings
import com.example.yanji.data.YanjiRepository
import org.junit.Assert.*
import org.junit.Test

class AiDiagnosticParserTest {

    private val repo = YanjiRepository.getInstance()
    private val settings = UserSettings(
        aiProvider = "DeepSeek",
        aiModel = "deepseek-chat"
    )
    private val snapshot = StudyDiagnosticSnapshot.from(
        periodDays = 7,
        settings = settings,
        focusSessions = emptyList(),
        examSessions = emptyList(),
        noteEntries = emptyList(),
        now = System.currentTimeMillis()
    )

    @Test
    fun parsesStandardJsonSuccessfully() {
        val json = """
            {
              "overview": "近7天累计专注时长良好，数学与英语投入较多。",
              "strengths": ["专注连贯性较好", "有计划地完成全真模考"],
              "weaknesses": ["专业课投入时间偏少"],
              "trendAnalysis": "相比上周期，专注总时长稳步回升。",
              "threeDayPlan": [
                "第1天：强化专业课名词解释记忆",
                "第2天：精读一篇英语真题阅读",
                "第3天：完成一组数学错题二刷"
              ]
            }
        """.trimIndent()

        val analysis = repo.parseAiDiagnostic(json, snapshot, settings)
        assertNotNull("Should parse standard JSON", analysis)
        assertEquals("近7天累计专注时长良好，数学与英语投入较多。", analysis?.overview)
        assertEquals(2, analysis?.strengths?.size)
        assertEquals(1, analysis?.weaknesses?.size)
        assertEquals(3, analysis?.suggestions?.size)
        assertEquals("相比上周期，专注总时长稳步回升。", analysis?.trendAnalysis)
    }

    @Test
    fun handlesThinkingTagsWithInternalBraces() {
        val content = """
            <think>
            Here is the user's data: { focus: 3600 }.
            Let's evaluate the overview:
            { "dummy": true }
            Now generating final response.
            </think>
            ```json
            {
              "overview": "综合表现稳健，专注度较高。",
              "strengths": ["高效执行每日晨读"],
              "weaknesses": ["晚上注意力有所分散"],
              "trendAnalysis": "学习动能稳步提升。",
              "threeDayPlan": [
                "第1天：早间背诵政治考点",
                "第2天：午后模考数学选填"
              ]
            }
            ```
        """.trimIndent()

        val analysis = repo.parseAiDiagnostic(content, snapshot, settings)
        assertNotNull("Should handle thinking tags and code fences", analysis)
        assertEquals("综合表现稳健，专注度较高。", analysis?.overview)
        assertEquals(2, analysis?.suggestions?.size)
    }

    @Test
    fun handlesTrailingCommasInJson() {
        val jsonWithTrailingCommas = """
            {
              "overview": "学情平稳推进中。",
              "strengths": [
                "连续打卡3天",
              ],
              "weaknesses": [
                "复习节奏稍显前紧后松",
              ],
              "trendAnalysis": "平稳爬坡中。",
              "threeDayPlan": [
                "第1天：数学真题专练",
                "第2天：英语完形填空",
              ],
            }
        """.trimIndent()

        val analysis = repo.parseAiDiagnostic(jsonWithTrailingCommas, snapshot, settings)
        assertNotNull("Should tolerate trailing commas", analysis)
        assertEquals("学情平稳推进中。", analysis?.overview)
        assertEquals(1, analysis?.strengths?.size)
        assertEquals(2, analysis?.suggestions?.size)
    }

    @Test
    fun handlesAlternateKeyNamesAndOmittedTrend() {
        val json = """
            {
              "summary": "专注时间分布均衡，英语词汇掌握扎实。",
              "highlights": ["阅读理解正确率提升"],
              "shortcomings": ["作文套句较多"],
              "suggestions": [
                "精读范文积累地道词汇",
                "每日限时训练小作文"
              ]
            }
        """.trimIndent()

        val analysis = repo.parseAiDiagnostic(json, snapshot, settings)
        assertNotNull("Should map alternate keys", analysis)
        assertEquals("专注时间分布均衡，英语词汇掌握扎实。", analysis?.overview)
        assertEquals(1, analysis?.strengths?.size)
        assertEquals(1, analysis?.weaknesses?.size)
        assertEquals(2, analysis?.suggestions?.size)
        assertFalse(analysis?.trendAnalysis.isNullOrBlank())
    }

    @Test
    fun handlesPlainTextFallback() {
        val markdownText = """
            【综合学情概览】
            本周主要攻克了高等数学微分方程部分，随笔记录了较好的学习状态。

            • 优势：高数错题复盘及时，逻辑清晰
            • 建议：
            1. 整理线性代数特征值笔记
            2. 复习英语核心词汇50个
        """.trimIndent()

        val analysis = repo.parseAiDiagnostic(markdownText, snapshot, settings)
        assertNotNull("Should fallback to plain text parsing", analysis)
        assertTrue(analysis!!.overview.isNotBlank())
        assertTrue(analysis.suggestions.isNotEmpty())
    }

    @Test
    fun parsesJsonWithExplicitNullOverviewAndFields() {
        val jsonWithNulls = """
            {
              "overview": null,
              "strengths": ["坚持早起复习", null, "null"],
              "weaknesses": [null, "未及时整理错题"],
              "trendAnalysis": null,
              "threeDayPlan": ["制定专业课精读计划", null]
            }
        """.trimIndent()

        val analysis = repo.parseAiDiagnostic(jsonWithNulls, snapshot, settings)
        assertNotNull("Should parse JSON with explicit nulls", analysis)
        assertNotEquals("null", analysis?.overview)
        assertNotEquals("undefined", analysis?.overview)
        assertTrue("Overview should not be blank and should be synthesized", analysis!!.overview.isNotBlank())
        assertFalse(analysis.overview.contains("null", ignoreCase = true))

        assertEquals("Strengths should filter out null entries", 1, analysis.strengths.size)
        assertEquals("坚持早起复习", analysis.strengths[0])

        assertEquals("Weaknesses should filter out null entries", 1, analysis.weaknesses.size)
        assertEquals("未及时整理错题", analysis.weaknesses[0])

        assertFalse("Trend analysis should not be 'null'", analysis.trendAnalysis.equals("null", ignoreCase = true))
        assertTrue("Trend analysis should not be blank", analysis.trendAnalysis.isNotBlank())

        assertEquals("Suggestions should filter out null entries", 1, analysis.suggestions.size)
        assertEquals("制定专业课精读计划", analysis.suggestions[0])
    }

    @Test
    fun parsesJsonWithLiteralStringNullOverview() {
        val jsonWithStringNull = """
            {
              "overview": "null",
              "strengths": ["数学公式记忆牢固"],
              "threeDayPlan": ["做两套历年真题"]
            }
        """.trimIndent()

        val analysis = repo.parseAiDiagnostic(jsonWithStringNull, snapshot, settings)
        assertNotNull(analysis)
        assertNotEquals("null", analysis?.overview)
        assertTrue(analysis!!.overview.isNotBlank())
        assertFalse(analysis.overview.contains("null", ignoreCase = true))
    }

    @Test
    fun handlesMalformedJsonWithoutLeakingLeftBraceAsOverview() {
        // A JSON with syntax error that starts with '{' on line 1
        val malformedJson = """
            {
              "overview": "这是正确的学情概览，但后续有语法错误",
              "strengths": ["高数复习连贯"],
              "threeDayPlan": ["做真题"],
              BAD_SYNTAX_HERE
            }
        """.trimIndent()

        val analysis = repo.parseAiDiagnostic(malformedJson, snapshot, settings)
        assertNotNull("Should recover via regex or fallback", analysis)
        assertNotEquals("Overview must not be a single left bracket '{'", "{", analysis?.overview)
        assertNotEquals("Overview must not be a single left bracket '('", "(", analysis?.overview)
        assertNotEquals("Overview must not be a single left bracket '（'", "（", analysis?.overview)
        assertTrue("Overview must have meaningful content", analysis!!.overview.length >= 3)
        assertEquals("这是正确的学情概览，但后续有语法错误", analysis.overview)
    }

    @Test
    fun handlesSingleBracketOverviewGracefully() {
        val jsonWithSingleBracket = """
            {
              "overview": "（",
              "strengths": ["词汇背诵稳定"],
              "threeDayPlan": ["阅读专练"]
            }
        """.trimIndent()

        val analysis = repo.parseAiDiagnostic(jsonWithSingleBracket, snapshot, settings)
        assertNotNull(analysis)
        assertNotEquals("（", analysis?.overview)
        assertNotEquals("(", analysis?.overview)
        assertNotEquals("{", analysis?.overview)
        assertTrue("Should synthesize a valid overview instead of bracket", analysis!!.overview.length >= 5)
    }

    @Test
    fun fallbackTextParserRejectsBracesAndSyntaxLines() {
        val brokenLines = """
            {
              "not_json": true
            }
            • 重点复习专业课核心知识
            • 保持每日专注一小时以上
        """.trimIndent()

        val analysis = repo.parseAiDiagnostic(brokenLines, snapshot, settings)
        assertNotNull(analysis)
        assertNotEquals("{", analysis?.overview)
        assertNotEquals("}", analysis?.overview)
        assertTrue(analysis!!.overview.length >= 4)
    }
}
