package com.example.yanji.ui.note

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 随笔列表「卡片化」的源码契约守卫。
 *
 * 用户诉求：每一篇随笔都用圆角卡片，背景与全站卡片背景色统一，相关组件一并跟上。
 * 这里曾经是「整行铺在页面底色上 + 0.8dp 分组线」的非卡片列表，与 App 里其它
 * 所有页面都不一致（其余页面一律走 ui/components 的 YanjiCard 基元）。
 *
 * 守四件事：
 *  1. 每篇随笔的容器是 `YanjiCard` 的 Compact 档（16dp 圆角 + `colorScheme.surface`）；
 *  2. 行内不再手写页面底色 —— 底色由卡片基元给出，在行里再写一次必然和全站漂移；
 *  3. 卡片与两侧滑动操作块内缩同一套页边距，滑开时才不会露出一截页面底色；
 *  4. 组与组之间不再画分隔线（卡片自己就是边界），页眉标题下那条规则线保留。
 */
class NoteListCardDesignTest {

    private fun source(relativePath: String): String {
        val root = findProjectRoot()
        val file = File(root, relativePath)
        assertTrue("$relativePath must exist", file.exists())
        return file.readText()
    }

    private val row = source("app/src/main/java/com/example/yanji/ui/note/NoteSwipeableRow.kt")
    private val screen = source("app/src/main/java/com/example/yanji/ui/note/NoteScreen.kt")

    @Test
    fun everyNoteRestsOnTheSiteWideCardPrimitive() {
        assertTrue("每篇随笔必须挂在 YanjiCard 上", row.contains("YanjiCard("))
        assertTrue(
            "必须是列表条目卡片档（Compact，16dp 圆角）",
            row.contains("variant = YanjiCardVariant.Compact")
        )
        assertTrue(
            "条目内边距走紧凑卡片档 token，不再手写 20dp",
            row.contains("horizontal = YanjiSpacing.CardPaddingCompact")
        )
    }

    @Test
    fun theRowMustNotPaintThePageBackgroundItself() {
        assertTrue(
            "行内不得再写 colorScheme.background：底色由 YanjiCard 给，" +
                "行里再写一次就会在主题调整后和全站卡片漂移",
            !row.contains("background(MaterialTheme.colorScheme.background)")
        )
    }

    @Test
    fun cardAndSwipeActionsShareTheSamePageInset() {
        val insets = Regex("""\.padding\(horizontal = YanjiSpacing\.PageHorizontalPadding\)""")
            .findAll(row)
            .count()
        assertTrue(
            "卡片本体与两侧操作块都要内缩同一套页边距（实际 $insets 处），" +
                "否则滑开会露出操作块与卡片边缘之间的一截页面底色",
            insets >= 2
        )
    }

    @Test
    fun theCardIsTheGroupBoundaryNotAHairline() {
        assertTrue(
            "组与组之间不得再插入分隔线 item —— 卡片自己就是边界",
            !screen.contains("divider-")
        )
        assertTrue("页眉「随笔」标题下的规则线保留", screen.contains("NoteRowDivider("))
        assertTrue(
            "日期头的分组上间距必须由调用方按组序给定（没有线之后它就是分组线索）",
            screen.contains("topPadding = if (groupIndex == 0)")
        )
    }

    @Test
    fun emptyAndNoResultStatesUseTheSameSpacingAndCardTokens() {
        assertTrue("空态必须是同一套卡片基元", screen.contains("variant = YanjiCardVariant.Grouped"))
        assertTrue(
            "空态 / 无结果态的页边距也要走 token，不再手写 20dp",
            !screen.contains(".padding(horizontal = 20.dp)")
        )
    }

    private fun findProjectRoot(): File {
        var current: File? = File(".").canonicalFile
        while (current != null) {
            if (File(current, "settings.gradle.kts").exists()) return current
            current = current.parentFile
        }
        return File(".").canonicalFile
    }
}
