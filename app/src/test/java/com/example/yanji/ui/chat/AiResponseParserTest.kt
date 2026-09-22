package com.example.yanji.ui.chat

import com.example.yanji.data.AiAction
import com.example.yanji.data.AiActionType
import com.example.yanji.data.AiBlockKind
import com.example.yanji.data.AiResponse
import org.junit.Assert.*
import org.junit.Test

class AiResponseParserTest {

    @Test
    fun `new token format parses to structured response`() {
        val content = """
            【诊断】过程性失分
            【证据】根据你最近 8 套模考，平均 120 分，错题多为符号抄错
            原因：草稿混乱 → 定位困难 → 转抄错误
            ① **草稿纸十字四折法**：拿到大草稿纸立刻横竖两折
            ② **特征值“迹和”双步秒核对**：求出 λ 之后验证 tr(A) = Σλ
            ③ **初等行变换宁写勿跳**：多写一行行变换
            [动作:CREATE_PLAN|草稿纸四分区]
            [追问:分析17题]
            [追问:生成草稿模板]
        """.trimIndent()

        val parsed = AiResponseParser.parse(content)

        assertNotNull(parsed.diagnosis)
        assertEquals("过程性失分", parsed.diagnosis)
        // 步骤合并为单个 STEPS 块（UI StepsBlock 按行拆分渲染）
        assertEquals(1, parsed.blocks.filter { it.kind == AiBlockKind.STEPS }.size)
        val stepsText = parsed.blocks.find { it.kind == AiBlockKind.STEPS }!!.text
        assertTrue(stepsText.contains("草稿纸十字四折法"))
        assertTrue(stepsText.contains("特征值"))
        assertTrue(stepsText.contains("初等行变换"))
        assertEquals(1, parsed.actions.size)
        assertEquals(AiActionType.CREATE_PLAN, parsed.actions[0].type)
        assertEquals("草稿纸四分区", parsed.actions[0].label)
        assertEquals(2, parsed.followups.size)
        assertTrue(parsed.followups.contains("分析17题"))
        assertTrue(parsed.followups.contains("生成草稿模板"))
    }

    @Test
    fun `fallback parse handles legacy format without new tokens`() {
        val content = """
            抱抱你，别自责！

            统计显示：在 110-130 分段考生中，**符号抄错与草稿混乱**占了智力外非技术丢分的 32%！明天模考可以立刻试试这三步“保分动作”：

            1. **草稿纸十字四折法**：拿到大草稿纸立刻横竖两折，划分出 4 个象限并从 ① 到 ④ 标号。
            2. **特征值“迹和”双步秒核对**：求出特征多项式解出 λ 之后，务必花 5 秒口算验证：tr(A) = Σλ。
            3. **初等行变换宁写勿跳**：规范答题卡上多写一行行变换，绝不心算负号倍加。

            要将『草稿纸四分区』加为明早计划吗？
        """.trimIndent()

        val parsed = AiResponseParser.parse(content)

        // Should fall back to legacy parsing
        assertNotNull(parsed.diagnosis)
        assertEquals("抱抱你，别自责！", parsed.diagnosis)
        assertEquals(1, parsed.actions.size)
        assertEquals(AiActionType.CREATE_PLAN, parsed.actions[0].type)
        assertEquals("草稿纸四分区", parsed.actions[0].label)
        assertTrue(parsed.blocks.any { it.kind == AiBlockKind.MAIN })
    }

    @Test
    fun `malformed action token does not crash and falls to main text`() {
        val content = """
            这是一个测试
            [动作:INVALID_TYPE|标签]
            正常文本
        """.trimIndent()

        val parsed = AiResponseParser.parse(content)

        // Malformed action should be silently ignored, text goes to MAIN
        assertEquals(0, parsed.actions.size)
        assertTrue(parsed.blocks.any { it.kind == AiBlockKind.MAIN && it.text.contains("测试") })
        assertTrue(parsed.blocks.any { it.kind == AiBlockKind.MAIN && it.text.contains("正常文本") })
    }

    @Test
    fun `lines already shown by a dedicated block are not duplicated into main text`() {
        val content = """
            【诊断】：过程性失分
            【证据】：根据你最近 8 套模考
            原因：草稿区没有分区
            要将『草稿纸四分区』加为明早计划吗？
            抱抱你，别自责！
            这是真正的正文。
        """.trimIndent()

        val parsed = AiResponseParser.parse(content)

        assertEquals("过程性失分", parsed.diagnosis)
        assertEquals(1, parsed.actions.size)
        assertEquals(AiActionType.CREATE_PLAN, parsed.actions[0].type)

        // 【诊断】由横幅渲染、询问计划由按钮行渲染、安抚语进横幅 —— 都不该再出现在正文里。
        // 【证据】与原因行没有独立渲染位（evidence 无人读取），仍须保留在正文中。
        // 【证据】/原因 无独立渲染位、安抚行因横幅已被【诊断】占用而不再现身横幅，
        // 三者都必须留在正文里；被横幅或按钮接管的两行才排除在外。
        assertEquals(
            listOf(
                "【证据】：根据你最近 8 套模考",
                "原因：草稿区没有分区",
                "抱抱你，别自责！",
                "这是真正的正文。"
            ),
            parsed.blocks.filter { it.kind == AiBlockKind.MAIN }.map { it.text }
        )
    }

    @Test
    fun `empty content returns empty response`() {
        val parsed = AiResponseParser.parse("")
        assertNull(parsed.diagnosis)
        assertTrue(parsed.blocks.isEmpty())
        assertTrue(parsed.actions.isEmpty())
        assertTrue(parsed.followups.isEmpty())
    }

    @Test
    fun `followup tokens with special chars are parsed`() {
        val content = """
            诊断内容
            [追问:生成明天草稿模板]
            [追问:制定明天训练计划]
        """.trimIndent()

        val parsed = AiResponseParser.parse(content)
        assertEquals(2, parsed.followups.size)
        assertTrue(parsed.followups.contains("生成明天草稿模板"))
        assertTrue(parsed.followups.contains("制定明天训练计划"))
    }

    @Test
    fun `numbered steps with various bullet formats parsed`() {
        val content = """
            ① 步骤一：详细说明
            ② 步骤二：详细说明
            3. 步骤三：详细说明
        """.trimIndent()

        val parsed = AiResponseParser.parse(content)
        val stepsBlock = parsed.blocks.find { it.kind == AiBlockKind.STEPS }
        assertNotNull(stepsBlock)
        assertTrue(stepsBlock!!.text.contains("步骤一"))
        assertTrue(stepsBlock.text.contains("步骤二"))
        assertTrue(stepsBlock.text.contains("步骤三"))
    }

    @Test
    fun empathyLineStaysVisibleWhenDiagnosisAlreadyPresent() {
        val content = """
            【诊断】：过程性失分
            抱抱你，别自责！
            正常正文。
        """.trimIndent()

        val parsed = AiResponseParser.parse(content)

        assertEquals("过程性失分", parsed.diagnosis)
        assertTrue(
            "横幅已被【诊断】占用，安抚行若不落进正文就会凭空消失",
            parsed.blocks.any { it.kind == AiBlockKind.MAIN && it.text.contains("抱抱你") }
        )
    }

    @Test
    fun empathyLineBecomesDiagnosisAndIsNotDuplicatedWhenBannerEmpty() {
        val content = """
            抱抱你，别自责！
            正常正文。
        """.trimIndent()

        val parsed = AiResponseParser.parse(content)

        assertEquals("抱抱你，别自责！", parsed.diagnosis)
        assertTrue(
            "横幅确实显示了它，正文不该再出现第二遍",
            parsed.blocks.none { it.kind == AiBlockKind.MAIN && it.text.contains("抱抱你") }
        )
    }
}