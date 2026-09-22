package com.example.yanji

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yanji.theme.YanjiMotion
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.*
import com.adamglin.phosphoricons.regular.*
import com.example.yanji.di.LocalAppContainer
import com.example.yanji.di.yanjiViewModel
import com.example.yanji.ui.achievement.AchievementsScreen
import com.example.yanji.ui.chat.AiChatScreen
import com.example.yanji.ui.detail.DailyStudyDetailScreen
import com.example.yanji.ui.detail.FocusSessionDetailScreen
import com.example.yanji.ui.detail.SubjectStudyDetailScreen
import com.example.yanji.ui.exam.ExamDetailScreen
import com.example.yanji.ui.exam.ExamHistoryScreen
import com.example.yanji.ui.exam.ExamScreen
import com.example.yanji.ui.focus.FocusScreen
import com.example.yanji.ui.home.HomeScreen
import com.example.yanji.ui.note.NoteEditorScreen
import com.example.yanji.ui.note.NoteScreen
import com.example.yanji.ui.navigation.GlassBottomBar
import com.example.yanji.ui.profile.ProfileScreen
import com.example.yanji.ui.profile.ProfileViewModel
import com.example.yanji.ui.profile.SubjectManagerScreen
import com.example.yanji.ui.stats.StatsScreen
import com.example.yanji.data.YanjiRepository
import com.example.yanji.data.YanjiTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import java.util.UUID

enum class YanjiTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("首页", PhosphorIcons.Fill.House, PhosphorIcons.Regular.House),
    FOCUS("专注", PhosphorIcons.Fill.Timer, PhosphorIcons.Regular.Timer),
    NOTE("随笔", PhosphorIcons.Fill.Notebook, PhosphorIcons.Regular.Notebook),
    STATS("统计", PhosphorIcons.Fill.ChartBar, PhosphorIcons.Regular.ChartBar),
    PROFILE("我的", PhosphorIcons.Fill.UserCircle, PhosphorIcons.Regular.UserCircle)
}

@Serializable
sealed interface YanjiSubScreen {
    @Serializable
    data class DailyStudyDetail(val date: String) : YanjiSubScreen
    @Serializable
    data class SubjectStudyDetail(val subjectId: String) : YanjiSubScreen
    @Serializable
    data class FocusSessionDetail(val sessionId: String) : YanjiSubScreen
    @Serializable
    data object ExamHistory : YanjiSubScreen
    @Serializable
    data class ExamDetail(val examId: String) : YanjiSubScreen
    /**
     * 随笔编辑页。两种进入方式：
     *  - [noteId] 非空：编辑历史里已存在的那一篇；
     *  - [noteId] 为空：新建一篇，[newEntryNonce] 用唯一值断开与上一次新建页的
     *    状态继承（同一天连续新建时，`(null, date)` 相同会让 rememberSaveable 复用旧草稿）。
     */
    @Serializable
    data class NoteEditor(
        val noteId: String? = null,
        val date: String,
        val newEntryNonce: String = ""
    ) : YanjiSubScreen
    @Serializable
    data object ExamMode : YanjiSubScreen
    @Serializable
    data object AiChat : YanjiSubScreen
    @Serializable
    data object Achievements : YanjiSubScreen
    @Serializable
    data object SubjectManager : YanjiSubScreen
}

val YanjiSubScreenStackSaver: Saver<SnapshotStateList<YanjiSubScreen>, ArrayList<String>> = Saver(
    save = { list -> ArrayList(list.map { Json.encodeToString(YanjiSubScreen.serializer(), it) }) },
    restore = { savedList ->
        mutableStateListOf<YanjiSubScreen>().apply {
            savedList.forEach { json ->
                runCatching { Json.decodeFromString(YanjiSubScreen.serializer(), json) }.getOrNull()?.let { add(it) }
            }
        }
    }
)

@Composable
fun MainNavigation() {
    val repository = LocalAppContainer.current.repository
    val activeFocus by repository.activeFocus.collectAsStateWithLifecycle()
    var currentTab by rememberSaveable {
        mutableStateOf(if (activeFocus != null) YanjiTab.FOCUS else YanjiTab.HOME)
    }
    val screenStack = rememberSaveable(saver = YanjiSubScreenStackSaver) {
        mutableStateListOf<YanjiSubScreen>()
    }

    // 运行和暂停都属于专注环境；会话结束后仍回到专注页。
    // 直接派生显示页，确保 Activity 重建时不会先闪现其他主 Tab。
    val visibleTab = if (activeFocus != null) YanjiTab.FOCUS else currentTab
    LaunchedEffect(activeFocus?.id) {
        if (activeFocus != null) currentTab = YanjiTab.FOCUS
    }

    val todayStr = remember {
        YanjiTime.todayIso()
    }
    val hazeState = remember { HazeState() }

    // System back button handling for full screen sub-pages
    BackHandler(enabled = screenStack.isNotEmpty()) {
        screenStack.removeLastOrNull()
    }

    val reduceMotion = YanjiMotion.isReduceMotionEnabled()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(state = hazeState)
                .statusBarsPadding()
        ) {
            if (screenStack.isNotEmpty()) {
                // Render top of sub-screen stack with smooth sliding transitions
                AnimatedContent(
                    targetState = screenStack.last(),
                    transitionSpec = {
                        if (reduceMotion) {
                            fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        } else {
                            (slideInHorizontally(tween(220)) { it / 6 } + fadeIn(tween(220)))
                                .togetherWith(slideOutHorizontally(tween(180)) { -it / 6 } + fadeOut(tween(180)))
                        }
                    },
                    label = "subScreenTransition",
                    modifier = Modifier.fillMaxSize()
                ) { screen ->
                    when (screen) {
                        is YanjiSubScreen.DailyStudyDetail -> {
                            DailyStudyDetailScreen(
                                date = screen.date,
                                onBack = { screenStack.removeLastOrNull() },
                                onNavigateToSubjectDetail = { subId ->
                                    screenStack.add(YanjiSubScreen.SubjectStudyDetail(subId))
                                },
                                onNavigateToFocusDetail = { fsId ->
                                    screenStack.add(YanjiSubScreen.FocusSessionDetail(fsId))
                                },
                                onNavigateToExamDetail = { exId ->
                                    screenStack.add(YanjiSubScreen.ExamDetail(exId))
                                },
                                onNavigateToStartFocus = {
                                    screenStack.clear()
                                    currentTab = YanjiTab.FOCUS
                                }
                            )
                        }
                        is YanjiSubScreen.SubjectStudyDetail -> {
                            SubjectStudyDetailScreen(
                                subjectId = screen.subjectId,
                                onBack = { screenStack.removeLastOrNull() },
                                onNavigateToFocusDetail = { fsId ->
                                    screenStack.add(YanjiSubScreen.FocusSessionDetail(fsId))
                                },
                                onNavigateToExamDetail = { exId ->
                                    screenStack.add(YanjiSubScreen.ExamDetail(exId))
                                },
                                onNavigateToStartFocus = {
                                    screenStack.clear()
                                    currentTab = YanjiTab.FOCUS
                                }
                            )
                        }
                        is YanjiSubScreen.FocusSessionDetail -> {
                            FocusSessionDetailScreen(
                                sessionId = screen.sessionId,
                                onBack = { screenStack.removeLastOrNull() }
                            )
                        }
                        is YanjiSubScreen.ExamHistory -> {
                            ExamHistoryScreen(
                                onBack = { screenStack.removeLastOrNull() },
                                onNavigateToExamDetail = { exId ->
                                    screenStack.add(YanjiSubScreen.ExamDetail(exId))
                                },
                                onStartNewExam = {
                                    screenStack.add(YanjiSubScreen.ExamMode)
                                }
                            )
                        }
                        is YanjiSubScreen.ExamDetail -> {
                            ExamDetailScreen(
                                examId = screen.examId,
                                onBack = { screenStack.removeLastOrNull() }
                            )
                        }
                        is YanjiSubScreen.NoteEditor -> {
                            NoteEditorScreen(
                                noteId = screen.noteId,
                                date = screen.date,
                                draftKeySuffix = screen.newEntryNonce,
                                onBack = { screenStack.removeLastOrNull() },
                                onSaveSuccess = { /* 保存后保留在编辑页，不退出到历史页 */ }
                            )
                        }
                        is YanjiSubScreen.ExamMode -> {
                            ExamScreen(
                                onBack = { screenStack.removeLastOrNull() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        is YanjiSubScreen.AiChat -> {
                            AiChatScreen(
                                onNavigateBack = { screenStack.removeLastOrNull() }
                            )
                        }
                        is YanjiSubScreen.Achievements -> {
                            AchievementsScreen(
                                onBack = { screenStack.removeLastOrNull() }
                            )
                        }
                        is YanjiSubScreen.SubjectManager -> {
                            SubjectManagerScreen(
                                onBack = { screenStack.removeLastOrNull() },
                                viewModel = yanjiViewModel { container ->
                                    ProfileViewModel(container.repository)
                                }
                            )
                        }
                    }
                }
            } else {
                // Keep tab transitions to opacity; scaling the full haze source adds rendering work.
                AnimatedContent(
                    targetState = visibleTab,
                    transitionSpec = {
                        if (reduceMotion) {
                            fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        } else {
                            fadeIn(tween(200))
                                .togetherWith(fadeOut(tween(140)))
                        }
                    },
                    label = "primaryTabTransition",
                    modifier = Modifier.fillMaxSize()
                ) { tab ->
                    when (tab) {
                        YanjiTab.HOME -> {
                            HomeScreen(
                                onNavigateToFocus = { currentTab = YanjiTab.FOCUS },
                                onNavigateToExam = { screenStack.add(YanjiSubScreen.ExamMode) },
                                onNavigateToNote = { currentTab = YanjiTab.NOTE },
                                onNavigateToStats = { currentTab = YanjiTab.STATS },
                                onNavigateToSettings = { currentTab = YanjiTab.PROFILE },
                                onNavigateToAiChat = { screenStack.add(YanjiSubScreen.AiChat) },
                                onNavigateToDailyDetail = { d -> screenStack.add(YanjiSubScreen.DailyStudyDetail(d)) },
                                onNavigateToSubjectDetail = { subId -> screenStack.add(YanjiSubScreen.SubjectStudyDetail(subId)) },
                                onNavigateToExamHistory = { screenStack.add(YanjiSubScreen.ExamHistory) },
                                onNavigateToNoteEditor = { d ->
                                    screenStack.add(
                                        YanjiSubScreen.NoteEditor(
                                            date = d,
                                            newEntryNonce = UUID.randomUUID().toString()
                                        )
                                    )
                                },
                                onNavigateToAchievements = { screenStack.add(YanjiSubScreen.Achievements) }
                            )
                        }
                        YanjiTab.FOCUS -> {
                            FocusScreen(
                                onNavigateToExam = { screenStack.add(YanjiSubScreen.ExamMode) },
                                onNavigateToDailyDetail = { d -> screenStack.add(YanjiSubScreen.DailyStudyDetail(d)) },
                                onNavigateToFocusDetail = { fsId -> screenStack.add(YanjiSubScreen.FocusSessionDetail(fsId)) }
                            )
                        }
                        YanjiTab.NOTE -> {
                            NoteScreen(
                                onNavigateToDailyDetail = { d -> screenStack.add(YanjiSubScreen.DailyStudyDetail(d)) },
                                onNavigateToNoteEditor = { jId, d ->
                                    screenStack.add(
                                        YanjiSubScreen.NoteEditor(
                                            noteId = jId,
                                            date = d,
                                            // 新建一篇时给唯一 nonce，避免同一天连续新建复用上一次的草稿状态。
                                            newEntryNonce = if (jId == null) UUID.randomUUID().toString() else ""
                                        )
                                    )
                                }
                            )
                        }
                        YanjiTab.STATS -> {
                            StatsScreen(
                                onNavigateToDailyDetail = { d -> screenStack.add(YanjiSubScreen.DailyStudyDetail(d)) },
                                onNavigateToSubjectDetail = { subId -> screenStack.add(YanjiSubScreen.SubjectStudyDetail(subId)) },
                                onNavigateToFocusDetail = { fsId -> screenStack.add(YanjiSubScreen.FocusSessionDetail(fsId)) },
                                onNavigateToExamHistory = { screenStack.add(YanjiSubScreen.ExamHistory) }
                            )
                        }
                        YanjiTab.PROFILE -> {
                            ProfileScreen(
                                onNavigateToAchievements = { screenStack.add(YanjiSubScreen.Achievements) },
                                onNavigateToSubjectManager = { screenStack.add(YanjiSubScreen.SubjectManager) }
                            )
                        }
                    }
                }
            }
        }

        // 暂停也隐藏底栏；结束或放弃时由业务会话清空自动恢复。
        if (screenStack.isEmpty() && activeFocus == null) {
            GlassBottomBar(
                currentTab = currentTab,
                onTabSelected = { currentTab = it },
                modifier = Modifier.align(Alignment.BottomCenter),
                hazeState = hazeState
            )
        }
    }
}
