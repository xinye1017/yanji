package com.example.yanji

import androidx.activity.compose.BackHandler
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
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Fill
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.fill.*
import com.adamglin.phosphoricons.regular.*
import com.example.yanji.di.LocalAppContainer
import com.example.yanji.theme.YanjiBackground
import com.example.yanji.ui.achievement.AchievementsScreen
import com.example.yanji.ui.chat.JuanjuanChatScreen
import com.example.yanji.ui.detail.DailyStudyDetailScreen
import com.example.yanji.ui.detail.FocusSessionDetailScreen
import com.example.yanji.ui.detail.SubjectStudyDetailScreen
import com.example.yanji.ui.exam.ExamDetailScreen
import com.example.yanji.ui.exam.ExamHistoryScreen
import com.example.yanji.ui.exam.ExamScreen
import com.example.yanji.ui.focus.FocusScreen
import com.example.yanji.ui.home.HomeScreen
import com.example.yanji.ui.journal.JournalEditorScreen
import com.example.yanji.ui.journal.JournalScreen
import com.example.yanji.ui.navigation.GlassBottomBar
import com.example.yanji.ui.profile.ProfileScreen
import com.example.yanji.ui.stats.StatsScreen
import com.example.yanji.data.YanjiRepository
import com.example.yanji.data.YanjiTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class YanjiTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("首页", PhosphorIcons.Fill.House, PhosphorIcons.Regular.House),
    FOCUS("专注", PhosphorIcons.Fill.Timer, PhosphorIcons.Regular.Timer),
    JOURNAL("日记", PhosphorIcons.Fill.Notebook, PhosphorIcons.Regular.Notebook),
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
    @Serializable
    data class JournalEditor(val journalId: String? = null, val date: String) : YanjiSubScreen
    @Serializable
    data object ExamMode : YanjiSubScreen
    @Serializable
    data object JuanjuanChat : YanjiSubScreen
    @Serializable
    data object Achievements : YanjiSubScreen
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

    // System back button handling for full screen sub-pages
    BackHandler(enabled = screenStack.isNotEmpty()) {
        screenStack.removeLastOrNull()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(YanjiBackground)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            if (screenStack.isNotEmpty()) {
                // Render top of sub-screen stack
                when (val screen = screenStack.last()) {
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
                    is YanjiSubScreen.JournalEditor -> {
                        JournalEditorScreen(
                            journalId = screen.journalId,
                            date = screen.date,
                            onBack = { screenStack.removeLastOrNull() },
                            onSaveSuccess = { screenStack.removeLastOrNull() },
                            onNavigateToDailyDetail = { d ->
                                screenStack.add(YanjiSubScreen.DailyStudyDetail(d))
                            }
                        )
                    }
                    is YanjiSubScreen.ExamMode -> {
                        ExamScreen(
                            onBack = { screenStack.removeLastOrNull() },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    is YanjiSubScreen.JuanjuanChat -> {
                        JuanjuanChatScreen(
                            onNavigateBack = { screenStack.removeLastOrNull() }
                        )
                    }
                    is YanjiSubScreen.Achievements -> {
                        AchievementsScreen(
                            onBack = { screenStack.removeLastOrNull() }
                        )
                    }
                }
            } else {
                // Primary Tabs
                when (visibleTab) {
                    YanjiTab.HOME -> {
                        HomeScreen(
                            onNavigateToFocus = { currentTab = YanjiTab.FOCUS },
                            onNavigateToExam = { screenStack.add(YanjiSubScreen.ExamMode) },
                            onNavigateToJournal = { currentTab = YanjiTab.JOURNAL },
                            onNavigateToStats = { currentTab = YanjiTab.STATS },
                            onNavigateToSettings = { currentTab = YanjiTab.PROFILE },
                            onNavigateToJuanjuanChat = { screenStack.add(YanjiSubScreen.JuanjuanChat) },
                            onNavigateToDailyDetail = { d -> screenStack.add(YanjiSubScreen.DailyStudyDetail(d)) },
                            onNavigateToSubjectDetail = { subId -> screenStack.add(YanjiSubScreen.SubjectStudyDetail(subId)) },
                            onNavigateToExamHistory = { screenStack.add(YanjiSubScreen.ExamHistory) },
                            onNavigateToJournalEditor = { d -> screenStack.add(YanjiSubScreen.JournalEditor(date = d)) },
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
                    YanjiTab.JOURNAL -> {
                        JournalScreen(
                            onNavigateToDailyDetail = { d -> screenStack.add(YanjiSubScreen.DailyStudyDetail(d)) },
                            onNavigateToJournalEditor = { jId, d -> screenStack.add(YanjiSubScreen.JournalEditor(jId, d)) }
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
                            onNavigateToAchievements = { screenStack.add(YanjiSubScreen.Achievements) }
                        )
                    }
                }
            }
        }

        // 暂停也隐藏底栏；结束或放弃时由业务会话清空自动恢复。
        if (screenStack.isEmpty() && activeFocus == null) {
            GlassBottomBar(
                currentTab = currentTab,
                onTabSelected = { currentTab = it },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
