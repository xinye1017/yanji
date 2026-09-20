package com.example.yanji.ui.journal

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.yanji.data.JournalEntry
import com.example.yanji.data.StudyStatisticsRepository
import com.example.yanji.data.YanjiRepository
import com.example.yanji.theme.YanjiTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 日记编辑器 UI 契约测试。
 *
 * 两条设计原则：
 *  1. **不依赖标题文案**：情绪档位、写作区、保存键一律通过 [JournalEditorTags] 定位，
 *     标题/提示语迭代不会打红测试。
 *  2. **不碰用户真实数据库**：`JournalEditorScreen` 默认 ViewModel 走 App 全局单例仓储，
 *     其落库目标就是用户真实学习库。这里注入 [NoopSaveJournalViewModel]，把「落库」这一步
 *     拦在测试边界内，只验证 UI 行为与保存回调契约，符合仓库
 *     「插桩测试必须用一次性数据库，绝不能打到用户真实库」的不变量。
 *
 * 日期统一使用历史占位日期 [PLACEHOLDER_DATE]：既不等于「今天」，也不可能是用户写过的日记，
 * 从而保证编辑器始终以「新建」状态进入，默认 moodScore = 5。
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class JournalEditorScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })

    private fun setEditorContent(onSaveSuccess: () -> Unit = {}) {
        val viewModel = NoopSaveJournalViewModel(
            YanjiRepository.getInstance(),
            StudyStatisticsRepository.getInstance()
        )
        composeRule.setContent {
            YanjiTheme {
                Box(Modifier.fillMaxSize()) {
                    JournalEditorScreen(
                        journalId = null,
                        date = PLACEHOLDER_DATE,
                        onBack = {},
                        onSaveSuccess = onSaveSuccess,
                        viewModel = viewModel
                    )
                }
            }
        }
    }

    @Test
    fun editorRendersWritingAreaAndSaveAction() {
        setEditorContent()

        // 页面已加载：写作区 + 保存键就位。
        composeRule.onNodeWithTag(JournalEditorTags.ContentInput).assertExists()
        composeRule.onNodeWithTag(JournalEditorTags.SaveButton).assertExists()
    }

    @Test
    fun writingAreaAcceptsTypedContent() {
        setEditorContent()

        val reflection = "今天把数学真题的错题重新推导了一遍，思路顺了很多。"

        composeRule.onNodeWithTag(JournalEditorTags.ContentInput)
            .performTextInput(reflection)
        composeRule.onNodeWithTag(JournalEditorTags.ContentInput).assertTextContains(reflection)
    }

    @Test
    fun saveWithBlankContentIsBlockedAndDoesNotReportSuccess() {
        var saveSucceeded = false
        setEditorContent(onSaveSuccess = { saveSucceeded = true })

        composeRule.onNodeWithTag(JournalEditorTags.SaveButton).performClick()

        assertFalse("内容为空时必须拦下保存，不得回调成功反馈", saveSucceeded)
    }

    @Test
    fun saveWithContentReportsSuccess() {
        var saveSucceeded = false
        setEditorContent(onSaveSuccess = { saveSucceeded = true })

        composeRule.onNodeWithTag(JournalEditorTags.ContentInput)
            .performTextInput("今天完成了数学真题复盘，效率很高。")
        composeRule.onNodeWithTag(JournalEditorTags.SaveButton).performClick()

        assertTrue("有内容时保存应回调成功反馈", saveSucceeded)
    }

    // ---- 富文本格式栏 ----

    @Test
    fun formatToolbarRendersAllFormatActions() {
        setEditorContent()

        composeRule.onNodeWithTag(JournalEditorTags.BoldButton).assertExists()
        composeRule.onNodeWithTag(JournalEditorTags.ItalicButton).assertExists()
        composeRule.onNodeWithTag(JournalEditorTags.UnderlineButton).assertExists()
        composeRule.onNodeWithTag(JournalEditorTags.ListButton).assertExists()
        composeRule.onNodeWithTag(JournalEditorTags.DividerButton).assertExists()
    }

    @Test
    fun boldButtonInsertsMarkersAroundTypedText() {
        setEditorContent()

        // 先输入正文，光标停在末尾；点 B 后应在光标处插入一对标记，
        // 光标落在两者之间 —— 紧接着输入的文字就落在标记内部。
        composeRule.onNodeWithTag(JournalEditorTags.ContentInput).performTextInput("重点")
        composeRule.onNodeWithTag(JournalEditorTags.BoldButton).performClick()
        composeRule.onNodeWithTag(JournalEditorTags.ContentInput).performTextInput("内容")

        // transformation 只加样式不改字符，因此断言仍然读得到包含标记的原文。
        composeRule.onNodeWithTag(JournalEditorTags.ContentInput)
            .assertTextContains("重点**内容**")
    }

    @Test
    fun listButtonAddsBulletPrefixToCurrentLine() {
        setEditorContent()

        composeRule.onNodeWithTag(JournalEditorTags.ContentInput).performTextInput("第一条")
        composeRule.onNodeWithTag(JournalEditorTags.ListButton).performClick()

        composeRule.onNodeWithTag(JournalEditorTags.ContentInput)
            .assertTextContains("• 第一条")
    }

    companion object {
        /** 历史占位日期：既不可能是「今天」，也不可能命中用户真实日记。 */
        private const val PLACEHOLDER_DATE = "1970-01-01"
    }
}

/**
 * 测试专用 ViewModel：拦下 [JournalViewModel.saveJournal]，避免插桩测试写入用户真实数据库。
 * 其余读路径（日记列表、当日学时聚合、用户设置）仍走真实实现，保证 UI 拿到的状态是真的。
 */
private class NoopSaveJournalViewModel(
    repo: YanjiRepository,
    statsRepo: StudyStatisticsRepository
) : JournalViewModel(repo, statsRepo) {
    override fun saveJournal(entry: JournalEntry) {
        // 测试边界：不落库。
    }
}
