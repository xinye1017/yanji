package com.example.yanji.ui.focus

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.yanji.data.DurationFormatter
import com.example.yanji.data.FocusModes
import com.example.yanji.data.Subject
import com.example.yanji.theme.SubjectEnglish
import com.example.yanji.theme.SubjectEnglishSoft
import com.example.yanji.theme.SubjectMajor
import com.example.yanji.theme.SubjectMajorSoft
import com.example.yanji.theme.SubjectMath
import com.example.yanji.theme.SubjectPolitics
import com.example.yanji.theme.SubjectPoliticsSoft
import com.example.yanji.theme.YanjiBackground
import com.example.yanji.theme.YanjiBorder
import com.example.yanji.theme.YanjiPrimary
import com.example.yanji.theme.YanjiPrimarySoft
import com.example.yanji.theme.YanjiSurface
import com.example.yanji.theme.YanjiSurfaceSoft
import com.example.yanji.theme.YanjiTextPrimary
import com.example.yanji.theme.YanjiTextSecondary
import com.example.yanji.theme.YanjiTextTertiary
import com.example.yanji.ui.components.AppContentInsets
import com.example.yanji.data.YanjiTime

private enum class QuietFocusStep {
    CATEGORY,
    MODULE,
    RHYTHM
}

private data class QuietDurationOption(
    val minutes: Int,
    val mode: String
)

private val QuietDurationOptions = listOf(
    QuietDurationOption(25, FocusModes.POMODORO_25),
    QuietDurationOption(45, FocusModes.POMODORO_45),
    QuietDurationOption(60, FocusModes.DEEP_60),
    QuietDurationOption(90, FocusModes.BIG_90)
)

private val QuietCardShape = RoundedCornerShape(18.dp)
private val QuietControlShape = RoundedCornerShape(14.dp)

/**
 * 选中态容器 —— 复用 theme 的淡蓝强调色，不再在 Screen 内联。
 * 与 DESIGN.md「chip-blue / nav-active」的 `primary-soft` 同一语义。
 */
private val QuietSelectedSurface = YanjiPrimarySoft

/**
 * The restrained focus-preparation flow. It deliberately keeps one visual focus per step:
 * subject, module, then timer. Shared header, progress, spacing, cards, and selected states
 * make the three screens feel like one continuous decision instead of three dashboards.
 */
@Composable
fun QuietFocusSetupContent(
    subjects: List<Subject>,
    selectedSubject: Subject,
    onSelectSubject: (Subject) -> Unit,
    selectedMode: String,
    onSelectMode: (String) -> Unit,
    todayTotalSeconds: Long = 0L,
    onStart: () -> Unit,
    onSaveAsQuickAction: (String) -> Unit = {},
    onNavigateToExam: () -> Unit,
    onNavigateToDailyDetail: (String) -> Unit = {}
) {
    val todayIso = remember {
        YanjiTime.todayIso()
    }
    var currentStep by remember { mutableStateOf(QuietFocusStep.CATEGORY) }
    var selectedCategoryId by remember(subjects) {
        mutableStateOf(selectedSubject.parentId ?: selectedSubject.id)
    }
    var isCountdownMode by remember(selectedMode) {
        mutableStateOf(selectedMode != FocusModes.COUNT_UP)
    }
    var selectedDurationMinutes by remember(selectedMode) {
        val minutes = (FocusModes.targetSeconds(selectedMode) / 60L).toInt()
        mutableStateOf(if (minutes > 0) minutes else 45)
    }
    var showCustomDurationDialog by remember { mutableStateOf(false) }
    var showSaveQuickDialog by remember { mutableStateOf(false) }

    val topCategories = remember(subjects) {
        subjects.filter {
            it.parentId == null &&
                it.enabled &&
                it.id != "other" &&
                it.name != "其他" &&
                !it.name.contains("其他")
        }.sortedBy { it.sortOrder }
    }
    val selectedCategory = topCategories.firstOrNull { it.id == selectedCategoryId }
    val subcategories = remember(subjects, selectedCategoryId) {
        subjects.filter { it.parentId == selectedCategoryId && it.enabled }
            .sortedBy { it.sortOrder }
    }

    fun goBack() {
        currentStep = when (currentStep) {
            QuietFocusStep.CATEGORY -> QuietFocusStep.CATEGORY
            QuietFocusStep.MODULE -> QuietFocusStep.CATEGORY
            QuietFocusStep.RHYTHM -> {
                if (subcategories.isEmpty()) QuietFocusStep.CATEGORY else QuietFocusStep.MODULE
            }
        }
    }

    BackHandler(enabled = currentStep != QuietFocusStep.CATEGORY) { goBack() }

    val stepTitle = when (currentStep) {
        QuietFocusStep.CATEGORY -> "选择学习方向"
        QuietFocusStep.MODULE -> "选择知识模块"
        QuietFocusStep.RHYTHM -> "设置专注节奏"
    }
    val stepSubtitle = when (currentStep) {
        QuietFocusStep.CATEGORY -> "这次要把注意力放在哪里？"
        QuietFocusStep.MODULE -> "选择一个具体模块，让目标更清晰。"
        QuietFocusStep.RHYTHM -> "为 ${selectedSubject.name} 设定本次计时方式。"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(YanjiBackground)
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp)
    ) {
        QuietFocusHeader(
            stepIndex = currentStep.ordinal + 1,
            title = stepTitle,
            subtitle = stepSubtitle,
            todayTotalSeconds = todayTotalSeconds,
            onBack = if (currentStep == QuietFocusStep.CATEGORY) null else ::goBack,
            onTodayClick = { onNavigateToDailyDetail(todayIso) }
        )

        Spacer(modifier = Modifier.height(20.dp))

        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                val enterOffset: (Int) -> Int = { width -> if (forward) width / 12 else -width / 12 }
                val exitOffset: (Int) -> Int = { width -> if (forward) -width / 12 else width / 12 }
                (fadeIn(tween(180)) + slideInHorizontally(tween(180), enterOffset))
                    .togetherWith(fadeOut(tween(140)) + slideOutHorizontally(tween(140), exitOffset))
                    .using(SizeTransform(clip = false))
            },
            label = "quietFocusStep",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { step ->
            when (step) {
                QuietFocusStep.CATEGORY -> {
                    QuietCategoryStep(
                        categories = topCategories,
                        selectedCategoryId = selectedCategoryId,
                        onCategoryClick = { category ->
                            selectedCategoryId = category.id
                            val children = subjects.filter { it.parentId == category.id && it.enabled }
                            if (children.isEmpty()) {
                                onSelectSubject(category)
                                currentStep = QuietFocusStep.RHYTHM
                            } else {
                                currentStep = QuietFocusStep.MODULE
                            }
                        },
                        onNavigateToExam = onNavigateToExam
                    )
                }

                QuietFocusStep.MODULE -> {
                    QuietModuleStep(
                        modules = subcategories,
                        selectedSubjectId = selectedSubject.id,
                        onModuleClick = { subject ->
                            onSelectSubject(subject)
                            currentStep = QuietFocusStep.RHYTHM
                        }
                    )
                }

                QuietFocusStep.RHYTHM -> {
                    QuietRhythmStep(
                        subjectName = selectedSubject.name,
                        isCountdownMode = isCountdownMode,
                        selectedDurationMinutes = selectedDurationMinutes,
                        onCountdownSelected = {
                            isCountdownMode = true
                            val matched = QuietDurationOptions.firstOrNull {
                                it.minutes == selectedDurationMinutes
                            }
                            onSelectMode(
                                matched?.mode ?: "${selectedDurationMinutes}分钟专注"
                            )
                        },
                        onCountUpSelected = {
                            isCountdownMode = false
                            onSelectMode(FocusModes.COUNT_UP)
                        },
                        onDurationSelected = { option ->
                            selectedDurationMinutes = option.minutes
                            onSelectMode(option.mode)
                        },
                        onCustomDuration = { showCustomDurationDialog = true },
                        onStart = onStart,
                        onSaveQuick = { showSaveQuickDialog = true },
                        onNavigateToExam = onNavigateToExam
                    )
                }
            }
        }
    }

    if (showCustomDurationDialog) {
        QuietCustomDurationDialog(
            initialMinutes = selectedDurationMinutes,
            onDismiss = { showCustomDurationDialog = false },
            onConfirm = { minutes ->
                selectedDurationMinutes = minutes
                onSelectMode("${minutes}分钟专注")
                showCustomDurationDialog = false
            }
        )
    }

    if (showSaveQuickDialog) {
        QuietSaveQuickDialog(
            subjectName = selectedSubject.name,
            mode = selectedMode,
            onDismiss = { showSaveQuickDialog = false },
            onConfirm = { label ->
                onSaveAsQuickAction(label)
                showSaveQuickDialog = false
            }
        )
    }
}

@Composable
private fun QuietFocusHeader(
    stepIndex: Int,
    title: String,
    subtitle: String,
    todayTotalSeconds: Long,
    onBack: (() -> Unit)?,
    onTodayClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                if (onBack == null) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = YanjiPrimary,
                        modifier = Modifier.size(21.dp)
                    )
                } else {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回上一步",
                            tint = YanjiTextPrimary,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "专注准备",
                style = MaterialTheme.typography.headlineMedium,
                color = YanjiTextPrimary
            )
            Spacer(modifier = Modifier.weight(1f))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onTodayClick)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    tint = YanjiTextTertiary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "今日 ${DurationFormatter.formatHoursMinutes(todayTotalSeconds)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = YanjiTextSecondary
                )
            }
        }

        QuietProgressIndicator(stepIndex = stepIndex)

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = YanjiTextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = YanjiTextSecondary
            )
        }
    }
}

@Composable
private fun QuietProgressIndicator(stepIndex: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(3) { index ->
                val completed = index < stepIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(if (completed) YanjiPrimary else YanjiBorder)
                )
            }
        }
        Text(
            text = "$stepIndex / 3",
            style = MaterialTheme.typography.labelMedium,
            color = YanjiTextTertiary
        )
    }
}

@Composable
private fun QuietCategoryStep(
    categories: List<Subject>,
    selectedCategoryId: String,
    onCategoryClick: (Subject) -> Unit,
    onNavigateToExam: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = AppContentInsets.BottomBarPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        categories.chunked(2).forEach { categoryRow ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                categoryRow.forEach { category ->
                    val selected = selectedCategoryId == category.id
                    val accent = quietCategoryAccent(category.id)
                    val iconBackground = quietCategoryIconBackground(category.id)
                    Surface(
                        onClick = { onCategoryClick(category) },
                        modifier = Modifier
                            .weight(1f)
                            .height(108.dp),
                        shape = QuietCardShape,
                        color = if (selected) QuietSelectedSurface else YanjiSurface
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (selected) YanjiSurface else iconBackground),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = quietSubjectIcon(category.id, category.name),
                                        contentDescription = null,
                                        tint = if (selected) YanjiPrimary else accent,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "已选择",
                                        tint = YanjiPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = category.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (selected) YanjiPrimary else YanjiTextPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = quietCategoryTagline(category.id),
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                                    color = if (selected) YanjiPrimary.copy(alpha = 0.76f) else YanjiTextSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                if (categoryRow.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onNavigateToExam) {
                Text(
                    text = "进入模拟考试",
                    style = MaterialTheme.typography.labelLarge,
                    color = YanjiTextSecondary
                )
            }
        }
    }
}

@Composable
private fun QuietModuleStep(
    modules: List<Subject>,
    selectedSubjectId: String,
    onModuleClick: (Subject) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = AppContentInsets.BottomBarPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        modules.forEach { subject ->
            val selected = subject.id == selectedSubjectId
            Surface(
                onClick = { onModuleClick(subject) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp),
                shape = QuietCardShape,
                color = if (selected) QuietSelectedSurface else YanjiSurface
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = subject.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (selected) YanjiPrimary else YanjiTextPrimary
                        )
                        Text(
                            text = quietModuleTagline(subject.id),
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                            color = if (selected) YanjiPrimary.copy(alpha = 0.76f) else YanjiTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Icon(
                        imageVector = if (selected) Icons.Default.Check else Icons.Default.ChevronRight,
                        contentDescription = if (selected) "已选择" else null,
                        tint = if (selected) YanjiPrimary else YanjiTextTertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuietRhythmStep(
    subjectName: String,
    isCountdownMode: Boolean,
    selectedDurationMinutes: Int,
    onCountdownSelected: () -> Unit,
    onCountUpSelected: () -> Unit,
    onDurationSelected: (QuietDurationOption) -> Unit,
    onCustomDuration: () -> Unit,
    onStart: () -> Unit,
    onSaveQuick: () -> Unit,
    onNavigateToExam: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = AppContentInsets.BottomBarPadding),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        QuietTimerSegmentedControl(
            isCountdownMode = isCountdownMode,
            onCountdownSelected = onCountdownSelected,
            onCountUpSelected = onCountUpSelected
        )

        if (isCountdownMode) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "$selectedDurationMinutes",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 56.sp,
                        lineHeight = 64.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = YanjiTextPrimary
                )
                Text(
                    text = "分钟",
                    style = MaterialTheme.typography.bodyMedium,
                    color = YanjiTextSecondary
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuietDurationOptions.forEach { option ->
                    val selected = option.minutes == selectedDurationMinutes
                    Surface(
                        onClick = { onDurationSelected(option) },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = QuietControlShape,
                        color = if (selected) QuietSelectedSurface else YanjiSurface
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = YanjiPrimary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                            }
                            Text(
                                text = "${option.minutes}",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) YanjiPrimary else YanjiTextPrimary
                            )
                        }
                    }
                }
            }

            TextButton(
                onClick = onCustomDuration,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = YanjiTextSecondary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "自定义时长",
                    style = MaterialTheme.typography.labelLarge,
                    color = YanjiTextSecondary
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "00:00",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 52.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = YanjiTextPrimary
                )
                Text(
                    text = "从零开始累计，不预设结束时间。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = YanjiTextSecondary,
                    textAlign = TextAlign.Center
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = if (isCountdownMode) {
                    "$subjectName · $selectedDurationMinutes 分钟"
                } else {
                    "$subjectName · 正向计时"
                },
                style = MaterialTheme.typography.labelMedium,
                color = YanjiTextTertiary
            )
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = YanjiPrimary)
            ) {
                Text(
                    text = "开始专注",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onSaveQuick) {
                    Text(
                        text = "保存为首页快捷",
                        style = MaterialTheme.typography.labelMedium,
                        color = YanjiTextSecondary
                    )
                }
                TextButton(onClick = onNavigateToExam) {
                    Text(
                        text = "模拟考试",
                        style = MaterialTheme.typography.labelMedium,
                        color = YanjiTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun QuietTimerSegmentedControl(
    isCountdownMode: Boolean,
    onCountdownSelected: () -> Unit,
    onCountUpSelected: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        shape = QuietControlShape,
        color = YanjiSurfaceSoft
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            QuietTimerSegment(
                text = "倒计时",
                selected = isCountdownMode,
                onClick = onCountdownSelected,
                modifier = Modifier.weight(1f)
            )
            QuietTimerSegment(
                text = "正向计时",
                selected = !isCountdownMode,
                onClick = onCountUpSelected,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QuietTimerSegment(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) QuietSelectedSurface else Color.Transparent)
            .semantics { this.selected = selected }
            .selectable(
                selected = selected,
                role = Role.Tab,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = YanjiPrimary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) YanjiPrimary else YanjiTextSecondary
            )
        }
    }
}

@Composable
private fun QuietCustomDurationDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var input by remember(initialMinutes) { mutableStateOf("$initialMinutes") }
    val commonMinutes = listOf(15, 30, 45, 60, 75, 90, 120, 150, 180)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "自定义时长",
                style = MaterialTheme.typography.headlineMedium,
                color = YanjiTextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "输入 1–360 分钟，或选择常用时长。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = YanjiTextSecondary
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter(Char::isDigit).take(3) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("分钟") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    commonMinutes.forEach { minutes ->
                        val selected = input == "$minutes"
                        Surface(
                            onClick = { input = "$minutes" },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) QuietSelectedSurface else YanjiSurfaceSoft
                        ) {
                            Text(
                                text = "$minutes 分钟",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) YanjiPrimary else YanjiTextSecondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val minutes = (input.toIntOrNull() ?: initialMinutes).coerceIn(1, 360)
                    onConfirm(minutes)
                },
                colors = ButtonDefaults.buttonColors(containerColor = YanjiPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = YanjiTextSecondary) }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = YanjiSurface
    )
}

@Composable
private fun QuietSaveQuickDialog(
    subjectName: String,
    mode: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var label by remember(subjectName, mode) { mutableStateOf("开始$subjectName$mode") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "保存为首页快捷",
                style = MaterialTheme.typography.headlineMedium,
                color = YanjiTextPrimary
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "$subjectName · $mode",
                    style = MaterialTheme.typography.bodyMedium,
                    color = YanjiTextSecondary
                )
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("快捷名称") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(label.trim()) },
                colors = ButtonDefaults.buttonColors(containerColor = YanjiPrimary),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("添加到首页")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = YanjiTextSecondary) }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = YanjiSurface
    )
}

private fun quietCategoryTagline(id: String): String = when (id) {
    "math" -> "高数 · 线代 · 概率"
    "major" -> "数据结构 · 计组 · 操作系统 · 计网"
    "english" -> "词汇 · 阅读 · 写作"
    "politics" -> "马原 · 毛中特 · 史纲 · 思修"
    else -> "考点梳理 · 专项突破"
}

private fun quietModuleTagline(id: String): String = when (id) {
    "math_advanced" -> "极限微积分 · 多元积分 · 微分方程"
    "math_linear" -> "行列式 · 矩阵 · 特征值与二次型"
    "math_probability" -> "随机变量 · 期望方差 · 大数定律"
    "major_data_structure" -> "线性表 · 树与图 · 查找与排序"
    "major_organization" -> "运算存储 · 指令系统 · CPU 与总线"
    "major_os" -> "进程 · 内存 · 文件与 I/O"
    "major_network" -> "体系结构 · TCP/IP · 路由"
    else -> "重点模块专题突破"
}

private fun quietSubjectIcon(subjectId: String, name: String): ImageVector {
    val value = (subjectId + name).lowercase()
    return when {
        value.contains("math") || value.contains("数") -> Icons.Default.Calculate
        value.contains("408") || value.contains("major") || value.contains("计") -> Icons.Default.Terminal
        value.contains("english") || value.contains("英") || value.contains("语") -> Icons.Default.Translate
        value.contains("politic") || value.contains("政") || value.contains("思") -> Icons.Default.AutoStories
        else -> Icons.Default.Timer
    }
}

private fun quietCategoryAccent(id: String): Color = when (id) {
    "math" -> SubjectMath
    "major" -> SubjectMajor
    "english" -> SubjectEnglish
    "politics" -> SubjectPolitics
    else -> YanjiPrimary
}

/**
 * 科目分类的图标底色 —— 与 [quietCategoryAccent] 同色系的极浅容器。
 *
 * 使用 theme 中与分类色对应的 soft 阶，避免 Screen 内联 hex。
 */
private fun quietCategoryIconBackground(id: String): Color = when (id) {
    "math" -> YanjiPrimarySoft
    "major" -> SubjectMajorSoft
    "english" -> SubjectEnglishSoft
    "politics" -> SubjectPoliticsSoft
    else -> YanjiSurfaceSoft
}
