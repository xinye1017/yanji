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
 * 守六件事：
 *  1. 每篇随笔的容器是 `YanjiCard` 的 Compact 档（16dp 圆角 + `colorScheme.surface`）；
 *  2. 行内不再手写页面底色 —— 底色由卡片基元给出，在行里再写一次必然和全站漂移；
 *  3. 卡片与两侧滑动操作块内缩同一套页边距，滑开时才不会露出一截页面底色；
 *  4. 组与组之间不再画分隔线（卡片自己就是边界），页眉标题下那条规则线保留；
 *  5. 圆角裁剪层与页边距分层 —— 同节点挂载会让圆角跑到屏幕两角，操作块带着直角
 *     从卡片四个圆角里漏出来（静止时看起来像「卡片和整行圆角没接上」）；
 *  6. 滑开时露出的那一侧由卡片自己的圆角描边 —— 操作底色铺满半行，不画 72dp 竖条，
 *     否则竖条贴卡片那一侧仍是直角，且卡片圆角与竖条之间会漏出页面底色。
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
    fun theRowContainerCarriesTheInsetAndTheCardRadiusOnce() {
        // 页边距与圆角只能由最外层容器给一次：卡片和操作块因此完全同形同位。
        // 曾经把页边距分别塞给卡片和操作块行，matchParentSize 撑满的是整行宽度，
        // 静止时操作块就从卡片左右各探出 20dp（直角，且与卡片圆角对不上）。
        val insets = Regex("""\.padding\(horizontal = YanjiSpacing\.PageHorizontalPadding\)""")
            .findAll(row)
            .count()
        assertTrue("页边距只能在最外层容器上给一次（实际 $insets 处）", insets == 1)
        assertTrue(
            "整行必须裁剪到卡片变体自己的圆角，露出的操作块才会跟着同一个圆角走",
            row.contains(".clip(YanjiCardVariant.Compact.toShape())")
        )
        assertTrue(
            "操作块行不得再自己内缩页边距",
            !row.contains(Regex("""matchParentSize\(\)\s*\.padding"""))
        )
    }

    @Test
    fun theClipLayerMustNotCarryThePageInset() {
        // 圆角裁剪层与页边距必须是**两层**：Modifier.clip 按节点自身容积取圆角矩形，
        // 把页边距和圆角挂进同一条 modifier 链时，圆角会被推到屏幕两角，行内操作块
        // 仍是直角 —— 静止时就从卡片的四个圆角里漏出蓝 / 粉色块，看起来像
        // 「卡片和整行圆角没接上」。真机截图上量过：左上角蓝块的外边界是一条直线
        // 而不是 16dp 圆角弧，就是同链挂载的后果。
        val paddingAt = row.indexOf(".padding(horizontal = YanjiSpacing.PageHorizontalPadding)")
        val clipAt = row.indexOf(".clip(YanjiCardVariant.Compact.toShape())")
        assertTrue("必须先有页边距、再有裁剪层", paddingAt in 0 until clipAt)
        assertTrue(
            "裁剪层不得与页边距同节点：两者之间必须隔着一个盒子的闭合",
            row.substring(paddingAt, clipAt).contains("Box(")
        )
        // 操作块与卡片都活在这个裁剪层里，滑开时才被同一个圆角约束
        assertTrue("操作块行必须在裁剪层内", clipAt < row.indexOf("matchParentSize()"))
        assertTrue("卡片必须在裁剪层内", clipAt < row.indexOf("YanjiCard("))
    }

    @Test
    fun theRevealSideIsTracedByTheCardsOwnRoundedCorner() {
        // 用户诉求：「两边的按键出来之后，靠近卡片的一侧还是直角」。
        // 卡片自己有 16dp 圆角，滑开时它的圆角弧相对卡片外框往里收 16dp。操作底色
        // 只画 72dp 竖条时：竖条贴卡片那一侧就是一条直角边，而且卡片圆角与竖条之间
        // 会漏出一小条页面底色（行顶 / 行底各 16dp 宽、行中约 2dp）。
        // 底色铺满半行后，「卡片 ∪ 操作底色」正好等于整行的圆角矩形 —— 露出的那一侧
        // 由卡片自己的圆角描边，既没有直角也没有空隙。
        assertTrue(
            "露出底色必须是半行宽的 ActionRevealSide，不能再是 72dp 竖条方块",
            row.contains("ActionRevealSide(") && !row.contains("SquareActionPanel(")
        )
        assertTrue(
            "左右两块底色必须各占半行（weight(1f)）：要盖住卡片 16dp 圆角弧往里收的那一截，",
            Regex("""ActionRevealSide\(\s*modifier = Modifier\.weight\(1f\)""").containsMatchIn(row)
        )
        // 可点区仍是原来那条 72dp 竖条：铺满是给「形状」用的，触控目标不跟着变大。
        assertTrue("可点区收在外沿 72dp", row.contains("tapWidth = actionWidth"))
        // 两半底色的接缝落在半行处，只能靠卡片盖住：最大露出 = 72dp + 20% 橡皮筋。
        // 这条上限一旦被放大到接近半行，两半的接缝就会从卡片底下露出来。
        assertTrue(
            "橡皮筋上限必须明显小于半行，否则两半底色的接缝会露出来",
            row.contains("overDragMaxPx = actionWidthPx * 1.2f")
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
