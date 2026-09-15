package com.example.yanji.ui.journal

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme
import com.adamglin.PhosphorIcons
import com.adamglin.phosphoricons.Regular
import com.adamglin.phosphoricons.regular.ArrowLeft
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.yanji.data.DurationFormatter
import com.example.yanji.data.JournalEntry
import com.example.yanji.data.StudyStatisticsRepository
import com.example.yanji.data.YanjiRepository
import com.example.yanji.data.YanjiTime
import com.example.yanji.theme.*
import com.example.yanji.theme.YanjiColors
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

/** UI 测试定位锚点：与 JournalEditorScreenInstrumentedTest 共享，避免断言依赖中文文案。 */
object JournalEditorTags {
    const val ContentInput = "journal_content_input"
    const val BlockersInput = "journal_blockers_input"
    const val SaveButton = "journal_save_button"
    const val PlanInput = "journal_plan_input"
    const val PlanAddButton = "journal_plan_add_button"
    fun moodOption(score: Int) = "journal_mood_$score"
}

/**
 * 记日记页（Stitch 设计稿「研迹 - 记录今日日记」重构版）。
 *
 * 布局约定：
 *  - 顶部导航沿用计时器页（FocusScreen）的样式：大标题 + 右侧「今日累计」胶囊徽章 + 副标题，
 *    编辑场景额外保留返回按钮；本页为全屏子页，不显示底部导航栏。
 *  - 心境五档（需调整/微浮躁/平稳前行/专注充实/深度心流）映射既有字段 moodScore 1..5，
 *    不改变数据库语义；energyScore / studySatisfaction 编辑时继承原值。
 *  - 「遇到的困难 / 卡点」对应 v10 新增的 blockers 列。
 *  - 「明日规划」以多行任务呈现，仍存储在 tomorrowPlan（按换行分隔）。
 */
@Composable
fun JournalEditorScreen(
    journalId: String?,
    date: String,
    onBack: () -> Unit,
    onSaveSuccess: () -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToDailyDetail: (date: String) -> Unit = {},
    viewModel: JournalViewModel = com.example.yanji.di.yanjiViewModel { container ->
        JournalViewModel(container.repository, container.statisticsRepository)
    }
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val journals = state.journals
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val existingEntry = remember(journalId, date, journals) {
        if (!journalId.isNullOrBlank()) {
            journals.find { it.id == journalId }
        } else {
            journals.find { it.date == date }
        }
    }

    // 单一事实来源：该日期真实的学习时长聚合（FocusSession + ExamSession）
    val dailySummary = viewModel.dailySummaryFor(date)
    val studyDuration = dailySummary.totalDurationSeconds

    // Draft fields survive Activity recreation and ordinary process death through saved state.
    // The keys are navigation identity only: a delayed Room emission must not overwrite a restored
    // draft after the user has already typed into it.
    var draftInitialized by rememberSaveable(journalId, date) { mutableStateOf(false) }
    var moodScore by rememberSaveable(journalId, date) { mutableIntStateOf(5) }
    var content by rememberSaveable(journalId, date) { mutableStateOf("") }
    var blockers by rememberSaveable(journalId, date) { mutableStateOf("") }
    var planTasks by rememberSaveable(
        journalId,
        date,
        stateSaver = listSaver(save = { it }, restore = { it })
    ) { mutableStateOf(emptyList<String>()) }
    var planInput by rememberSaveable(journalId, date) { mutableStateOf("") }

    LaunchedEffect(existingEntry?.id) {
        val entry = existingEntry ?: return@LaunchedEffect
        if (!draftInitialized) {
            moodScore = entry.moodScore
            content = entry.content
            blockers = entry.blockers
            planTasks = entry.tomorrowPlan
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            draftInitialized = true
        }
    }

    val todayStr = remember {
        YanjiTime.todayIso()
    }
    val isToday = date == todayStr

    val formattedDate = remember(date) {
        YanjiTime.parseIsoDate(date)?.format(
            DateTimeFormatter.ofPattern("M 月 d 日", Locale.CHINESE)
        ) ?: date
    }

    // 初试倒计时（单一事实来源：user_settings.targetExamDate，与 ProfileScreen 同口径）
    val countdownDays = remember(settings.targetExamDate) {
        val target = YanjiTime.parseIsoDate(settings.targetExamDate)
        target?.let { ChronoUnit.DAYS.between(YanjiTime.today(), it).toInt() }
            ?.takeIf { it >= 0 }
    }

    fun saveJournal() {
        if (content.isBlank() && blockers.isBlank()) {
            Toast.makeText(context, "请写点今日内容吧", Toast.LENGTH_SHORT).show()
            return
        }
        val finalEntry = JournalEntry(
            id = existingEntry?.id ?: UUID.randomUUID().toString(),
            date = date,
            title = existingEntry?.title ?: "",
            content = content.trim(),
            moodScore = moodScore,
            energyScore = existingEntry?.energyScore ?: 4,
            studySatisfaction = existingEntry?.studySatisfaction ?: 5,
            tomorrowPlan = planTasks.joinToString("\n"),
            blockers = blockers.trim(),
            tags = existingEntry?.tags ?: emptyList(),
            // 编辑时保留原始创建时间，避免既有行为把 createdAt 刷新成当前时刻
            createdAt = existingEntry?.createdAt ?: System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        viewModel.saveJournal(finalEntry)
        Toast.makeText(context, "日记已保存", Toast.LENGTH_SHORT).show()
        onSaveSuccess()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ---- 顶部导航（保持计时器页样式：大标题 + 今日累计徽章 + 副标题）----
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(0.dp)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .size(48.dp)
                            .testTag("detail_top_bar_back")
                    ) {
                        Icon(
                            imageVector = PhosphorIcons.Regular.ArrowLeft,
                            contentDescription = "返回",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = if (isToday) "今日日记" else "当日日记",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                // 今日累计徽章（点击进入当日学习明细）
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.clickable { onNavigateToDailyDetail(date) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "今日累计 ${DurationFormatter.formatHoursMinutes(studyDuration)}",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Text(
                text = buildString {
                    append(formattedDate)
                    if (countdownDays != null) append(" · 初试倒计时 $countdownDays 天")
                },
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp
                )
            )
        }

        // ---- 滚动内容区 ----
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ---- 卡片 1：今日专注摘要 ----
            JournalEditorCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "今日累计专注",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = DurationFormatter.formatHoursMinutes(studyDuration),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formatSubjectDistribution(dailySummary.subjectDistribution),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = YanjiColors.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // ---- 区块 2：今日专注心境 ----
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(
                    title = "今日专注心境",
                    hint = "快速标记",
                    icon = Icons.Default.Star,
                    iconTint = MaterialTheme.colorScheme.primary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MOOD_OPTIONS.forEach { option ->
                        val selected = moodScore == option.score
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .testTag(JournalEditorTags.moodOption(option.score))
                                .clip(RoundedCornerShape(YanjiRadius.ContentBlockRadius))
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(16.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = { moodScore = option.score }
                                )
                                .padding(vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = option.icon,
                                contentDescription = option.label,
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = option.label,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // ---- 区块 3：今日复盘与收获 ----
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(
                    title = "今日复盘与收获",
                    hint = "2~3句话归纳突破",
                    icon = Icons.Default.EditNote,
                    iconTint = MaterialTheme.colorScheme.primary
                )
                PlainInputCard(
                    value = content,
                    onValueChange = { content = it },
                    placeholder = "记录今天各科目的复习感受、突破与思路变化…",
                    minLines = 4,
                    testTag = JournalEditorTags.ContentInput
                )
            }

            // ---- 区块 4：遇到的困难 / 卡点 ----
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(
                    title = "遇到的困难 / 卡点",
                    hint = "快速诊断",
                    icon = Icons.Default.HelpOutline,
                    iconTint = YanjiColors.warning
                )
                PlainInputCard(
                    value = blockers,
                    onValueChange = { blockers = it },
                    placeholder = "写下今天卡住你的知识点，便于后续针对性回炉…",
                    minLines = 3,
                    testTag = JournalEditorTags.BlockersInput
                )
            }

            // ---- 区块 5：明日规划（动态任务列表）----
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionHeader(
                    title = "明日规划",
                    hint = if (planTasks.isEmpty()) "为明天列 1~3 项核心任务" else "已列 ${planTasks.size} 项",
                    icon = Icons.Default.Flag,
                    iconTint = MaterialTheme.colorScheme.tertiary
                )
                JournalEditorCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        planTasks.forEachIndexed { index, task ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(YanjiRadius.ContentBlockRadius))
                                    .padding(horizontal = 10.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PlanIndexBadge(
                                    index = index + 1,
                                    container = MaterialTheme.colorScheme.primaryContainer,
                                    textColor = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = task,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .clickable { planTasks = planTasks.filterIndexed { i, _ -> i != index } },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "删除任务",
                                        tint = YanjiColors.textTertiary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlanIndexBadge(
                                index = planTasks.size + 1,
                                container = MaterialTheme.colorScheme.surfaceVariant,
                                textColor = YanjiColors.textTertiary
                            )
                            BasicTextField(
                                value = planInput,
                                onValueChange = { planInput = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag(JournalEditorTags.PlanInput),
                                textStyle = TextStyle(
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {
                                    val value = planInput.trim()
                                    if (value.isNotEmpty()) {
                                        planTasks = planTasks + value
                                        planInput = ""
                                    }
                                }),
                                decorationBox = { inner ->
                                    Box {
                                        if (planInput.isEmpty()) {
                                            Text(
                                                text = "输入明日核心任务，回车或点击添加",
                                                fontSize = 13.sp,
                                                color = YanjiColors.textTertiary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        inner()
                                    }
                                }
                            )
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .testTag(JournalEditorTags.PlanAddButton)
                                    .clickable {
                                        val value = planInput.trim()
                                        if (value.isNotEmpty()) {
                                            planTasks = planTasks + value
                                            planInput = ""
                                        }
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "添加",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ---- 区块 6：保存 ----
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = { saveJournal() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag(JournalEditorTags.SaveButton),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "保存今日足迹",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "日记将自动关联今日学习数据，可在日记时间轴中回顾",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = YanjiColors.textTertiary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

// ---------------------------------------------------------------- 局部组件

/** 设计稿五档心境 → moodScore 1..5 的映射。 */
private data class MoodOption(val score: Int, val label: String, val icon: ImageVector)

private val MOOD_OPTIONS = listOf(
    MoodOption(1, "需调整", Icons.Default.BatteryAlert),
    MoodOption(2, "微浮躁", Icons.Default.Waves),
    MoodOption(3, "平稳前行", Icons.Default.Contrast),
    MoodOption(4, "专注充实", Icons.Default.CheckCircle),
    MoodOption(5, "深度心流", Icons.Default.Bolt)
)

/** 设计稿统一卡片：白底 + 细边框 + 24dp 圆角。 */
@Composable
private fun JournalEditorCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.8f), RoundedCornerShape(YanjiRadius.StandardCardRadius)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(content = content)
    }
}

/** 区块标题行：左侧 icon + 标题，右侧弱化提示。 */
@Composable
private fun SectionHeader(title: String, hint: String, icon: ImageVector, iconTint: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(text = hint, fontSize = 12.sp, color = YanjiColors.textTertiary)
    }
}

/** 无边框输入卡：透明 BasicTextField 直接嵌在白卡内（对应设计稿 focus-within 输入区）。 */
@Composable
private fun PlainInputCard(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    minLines: Int,
    testTag: String
) {
    JournalEditorCard {
        Box(modifier = Modifier.padding(16.dp)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = YanjiColors.textTertiary,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag),
                textStyle = TextStyle(
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                minLines = minLines,
                maxLines = 12
            )
        }
    }
}

/** 明日规划条目的序号圆标。 */
@Composable
private fun PlanIndexBadge(index: Int, container: Color, textColor: Color) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(container, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = index.toString(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

/** 科目分布摘要行："数 3.7h · 408 2.5h · 英 1.3h"，空数据时给出占位说明。 */
private fun formatSubjectDistribution(distribution: Map<String, Long>): String {
    if (distribution.isEmpty()) return "今日暂无专注记录"
    return distribution.entries
        .sortedByDescending { it.value }
        .joinToString(" · ") { "${subjectShortName(it.key)} ${formatCompactHours(it.value)}" }
}

/** 科目名缩写（设计稿口径：数 / 408 / 英 / 政）。 */
private fun subjectShortName(name: String): String = when {
    name.contains("数") -> "数"
    name.contains("408") || name.contains("专业") || name.contains("计") || name.contains("代码") -> "408"
    name.contains("英") -> "英"
    name.contains("政") || name.contains("思") -> "政"
    else -> name.take(2)
}

/** 紧凑时长："3.7h" / "45m"。 */
private fun formatCompactHours(seconds: Long): String {
    if (seconds <= 0) return "0h"
    val hours = seconds / 3600.0
    return if (hours >= 1) String.format(Locale.US, "%.1fh", hours) else "${seconds / 60}m"
}
