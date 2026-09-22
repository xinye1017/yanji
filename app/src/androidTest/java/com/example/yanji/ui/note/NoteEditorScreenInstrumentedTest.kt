package com.example.yanji.ui.note

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.yanji.data.NoteEntry
import com.example.yanji.data.StudyStatisticsRepository
import com.example.yanji.data.YanjiRepository
import com.example.yanji.theme.YanjiTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 日记编辑器 UI 契约测试。
 *
 * 两条设计原则：
 *  1. **不依赖标题文案**：情绪档位、写作区、保存键一律通过 [NoteEditorTags] 定位，
 *     标题/提示语迭代不会打红测试。
 *  2. **不碰用户真实数据库**：`NoteEditorScreen` 默认 ViewModel 走 App 全局单例仓储，
 *     其落库目标就是用户真实学习库。这里注入 [NoopSaveNoteViewModel]，把「落库」这一步
 *     拦在测试边界内，只验证 UI 行为与保存回调契约，符合仓库
 *     「插桩测试必须用一次性数据库，绝不能打到用户真实库」的不变量。
 *
 * 日期统一使用历史占位日期 [PLACEHOLDER_DATE]：既不等于「今天」，也不可能是用户写过的日记，
 * 从而保证编辑器始终以「新建」状态进入，默认 moodScore = 5。
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class NoteEditorScreenInstrumentedTest {
    @get:Rule
    val composeRule = createComposeRule(effectContext = object : MotionDurationScale {
        override val scaleFactor = 0f
    })

    private fun setEditorContent(onSaveSuccess: () -> Unit = {}) {
        val viewModel = NoopSaveNoteViewModel(
            YanjiRepository.getInstance(),
            StudyStatisticsRepository.getInstance()
        )
        composeRule.setContent {
            YanjiTheme {
                Box(Modifier.fillMaxSize()) {
                    NoteEditorScreen(
                        noteId = null,
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
        composeRule.onNodeWithTag(NoteEditorTags.ContentInput).assertExists()
        composeRule.onNodeWithTag(NoteEditorTags.SaveButton).assertExists()
    }

    @Test
    fun writingAreaAcceptsTypedContent() {
        setEditorContent()

        val reflection = "今天把数学真题的错题重新推导了一遍，思路顺了很多。"

        composeRule.onNodeWithTag(NoteEditorTags.ContentInput)
            .performTextInput(reflection)
        composeRule.onNodeWithTag(NoteEditorTags.ContentInput).assertTextContains(reflection)
    }

    @Test
    fun saveWithBlankContentIsBlockedAndDoesNotReportSuccess() {
        var saveSucceeded = false
        setEditorContent(onSaveSuccess = { saveSucceeded = true })

        composeRule.onNodeWithTag(NoteEditorTags.SaveButton).performClick()

        assertFalse("内容为空时必须拦下保存，不得回调成功反馈", saveSucceeded)
    }

    @Test
    fun saveWithContentReportsSuccess() {
        var saveSucceeded = false
        setEditorContent(onSaveSuccess = { saveSucceeded = true })

        composeRule.onNodeWithTag(NoteEditorTags.ContentInput)
            .performTextInput("今天完成了数学真题复盘，效率很高。")
        composeRule.onNodeWithTag(NoteEditorTags.SaveButton).performClick()

        assertTrue("有内容时保存应回调成功反馈", saveSucceeded)
    }

    // ---- 富文本格式栏 ----

    @Test
    fun formatToolbarRendersAllFormatActions() {
        setEditorContent()

        composeRule.onNodeWithTag(NoteEditorTags.BoldButton).assertExists()
        composeRule.onNodeWithTag(NoteEditorTags.ItalicButton).assertExists()
        composeRule.onNodeWithTag(NoteEditorTags.UnderlineButton).assertExists()
        composeRule.onNodeWithTag(NoteEditorTags.ListButton).assertExists()
        composeRule.onNodeWithTag(NoteEditorTags.DividerButton).assertExists()
    }

    @Test
    fun boldButtonInsertsMarkersAroundTypedText() {
        setEditorContent()

        // 先输入正文，光标停在末尾；点 B 后应在光标处插入一对标记，
        // 光标落在两者之间 —— 紧接着输入的文字就落在标记内部。
        composeRule.onNodeWithTag(NoteEditorTags.ContentInput).performTextInput("重点")
        composeRule.onNodeWithTag(NoteEditorTags.BoldButton).performClick()
        composeRule.onNodeWithTag(NoteEditorTags.ContentInput).performTextInput("内容")

        // transformation 只加样式不改字符，因此断言仍然读得到包含标记的原文。
        composeRule.onNodeWithTag(NoteEditorTags.ContentInput)
            .assertTextContains("重点**内容**")
    }

    @Test
    fun listButtonAddsBulletPrefixToCurrentLine() {
        setEditorContent()

        composeRule.onNodeWithTag(NoteEditorTags.ContentInput).performTextInput("第一条")
        composeRule.onNodeWithTag(NoteEditorTags.ListButton).performClick()

        composeRule.onNodeWithTag(NoteEditorTags.ContentInput)
            .assertTextContains("• 第一条")
    }

    // ---- 一天多篇：新建必须进空白稿 ----

    /**
     * 「记今天」以 `noteId = null` 进入编辑器。即使当天已经存在随笔，
     * 也必须呈现空白稿 —— 否则一天多篇无法落地（会反复打开当天最早那篇）。
     */
    @Test
    fun newEntryStartsBlankEvenWhenTodayAlreadyHasEntries() {
        val today = "2026-09-21"
        val existing = NoteEntry(
            id = "existing-today",
            date = today,
            content = "今天已经写过一篇了",
            createdAt = 1_000L,
            updatedAt = 1_000L
        )
        val viewModel = SeededNoopSaveNoteViewModel(
            YanjiRepository.getInstance(),
            StudyStatisticsRepository.getInstance(),
            seed = listOf(existing)
        )

        composeRule.setContent {
            YanjiTheme {
                Box(Modifier.fillMaxSize()) {
                    NoteEditorScreen(
                        noteId = null,
                        date = today,
                        onBack = {},
                        onSaveSuccess = {},
                        viewModel = viewModel
                    )
                }
            }
        }

        // 空白稿：写作区不继承当天已存在随笔的正文。
        // 只能读 EditableText：该节点 MergeDescendants=true，placeholder 会并进 Text，
        // 所以 assertTextEquals("") 永远会被提示语打红；输入框真实内容由 EditableText 承载，
        // 一旦新建稿误继承当天已有随笔，这里就会变成那篇的正文而变红。
        assertEquals(
            "新建稿的输入框必须为空，不得继承当天已存在随笔的正文",
            "",
            contentInputEditableText()
        )
    }

    /**
     * 反向对照：证明上一条用例的探针**读得到**正文。
     * 若 EditableText 恒为空，「新建进空白稿」就成了永真的空断言。
     */
    @Test
    fun editableTextProbeReadsTypedContent() {
        setEditorContent()

        composeRule.onNodeWithTag(NoteEditorTags.ContentInput).performTextInput("探针六个字呀")

        assertEquals(
            "EditableText 必须跟随输入内容，否则空白稿用例是空断言",
            "探针六个字呀",
            contentInputEditableText()
        )
    }

    /** 写作区真实输入值；`null` 表示语义节点缺失该属性（用例应据此变红而非空过）。 */
    private fun contentInputEditableText(): String? {
        val config = composeRule.onNodeWithTag(NoteEditorTags.ContentInput)
            .fetchSemanticsNode()
            .config
        return if (config.contains(SemanticsProperties.EditableText)) {
            // 本 Compose 版本里 EditableText 承载的是 AnnotatedString，取 .text 才是原始输入值。
            config[SemanticsProperties.EditableText]?.text
        } else {
            null
        }
    }

    companion object {
        /** 历史占位日期：既不可能是「今天」，也不可能命中用户真实日记。 */
        private const val PLACEHOLDER_DATE = "1970-01-01"
    }
}

/**
 * 在 [NoopSaveNoteViewModel] 之上注入一份固定随笔列表，
 * 用于验证「当天已有随笔时，新建仍应进入空白稿」这一类只读 UI 契约。
 */
private class SeededNoopSaveNoteViewModel(
    repo: YanjiRepository,
    statsRepo: StudyStatisticsRepository,
    seed: List<NoteEntry>
) : NoteViewModel(repo, statsRepo) {
    override val uiState: StateFlow<NoteUiState> = MutableStateFlow(NoteViewModel.groupsOf(seed))
}

/**
 * 测试专用 ViewModel：拦下 [NoteViewModel.saveNote]，避免插桩测试写入用户真实数据库。
 * 其余读路径（日记列表、当日学时聚合、用户设置）仍走真实实现，保证 UI 拿到的状态是真的。
 */
private class NoopSaveNoteViewModel(
    repo: YanjiRepository,
    statsRepo: StudyStatisticsRepository
) : NoteViewModel(repo, statsRepo) {
    override fun saveNote(entry: NoteEntry) {
        // 测试边界：不落库。
    }
}
