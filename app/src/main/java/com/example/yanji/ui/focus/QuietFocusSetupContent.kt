package com.example.yanji.ui.focus

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
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
import com.example.yanji.theme.SubjectMathSoft
import com.example.yanji.theme.SubjectPolitics
import com.example.yanji.theme.SubjectPoliticsSoft
import com.example.yanji.theme.YanjiColors
import com.example.yanji.theme.YanjiRadius
import com.example.yanji.theme.yanjiIsDarkTheme
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

private val QuietCardShape = RoundedCornerShape(YanjiRadius.GroupedCardRadius)
private val QuietControlShape = RoundedCornerShape(YanjiRadius.RowRadius)

/**
 * 选中态容器 —— 复用 theme 的淡蓝强调色，不再在 Screen 内联。
 * 与 DESIGN.md「chip-blue / nav-active」的 `primary-soft` 同一语义。
 */
private val QuietSelectedSurface: Color
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme.primaryContainer

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
    onNavigateToExam: () -> Unit,
    onNavigateToDailyDetail: (String) -> Unit = {}
) {
    val todayIso = remember {
        YanjiTime.todayIso()
    }
    var currentStep by rememberSaveable { mutableStateOf(QuietFocusStep.CATEGORY) }
    var selectedCategoryId by rememberSaveable(subjects) {
        mutableStateOf(selectedSubject.parentId ?: selectedSubject.id)
    }
    var isCountdownMode by rememberSaveable(selectedMode) {
        mutableStateOf(selectedMode != FocusModes.COUNT_UP)
    }
    var selectedDurationMinutes by rememberSaveable(selectedMode) {
        val minutes = (FocusModes.targetSeconds(selectedMode) / 60L).toInt()
        mutableIntStateOf(if (minutes > 0) minutes else 45)
    }
    var showCustomDurationDialog by rememberSaveable { mutableStateOf(false) }

    val topCategories = remember(subjects) {
        subjects.filter {
            it.parentId == null &&
                it.enabled &&
                it.id != "other" &&
                it.name != "其他" &&
                !it.name.contains("其他")
        }.sortedBy { it.sortOrder }
    }
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
            .background(MaterialTheme.colorScheme.background)
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
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(21.dp)
                    )
                } else {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回上一步",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "专注准备",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
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
                    tint = YanjiColors.textTertiary,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "今日 ${DurationFormatter.formatHoursMinutes(todayTotalSeconds)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        QuietProgressIndicator(stepIndex = stepIndex)

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        .background(if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                )
            }
        }
        Text(
            text = "$stepIndex / 3",
            style = MaterialTheme.typography.labelMedium,
            color = YanjiColors.textTertiary
        )
    }
}

@Composable
private fun QuietCategoryStep(
    categories: List<Subject>,
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
                    val accent = quietCategoryAccent(category.id)
                    val iconBackground = quietCategoryIconBackground(category.id)
                    Surface(
                        onClick = { onCategoryClick(category) },
                        modifier = Modifier
                            .weight(1f)
                            .height(108.dp),
                        shape = QuietCardShape,
                        border = androidx.compose.foundation.BorderStroke(0.8.dp, com.example.yanji.theme.YanjiColors.separator),
                        color = com.example.yanji.theme.YanjiColors.elevatedSurface
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
                                        .clip(RoundedCornerShape(YanjiRadius.Small))
                                        .background(iconBackground),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = quietSubjectIcon(category.id, category.name),
                                        contentDescription = null,
                                        tint = accent,
                                        modifier = Modifier.size(19.dp)
                                    )
                                }
                            }
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = category.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = quietCategoryTagline(category.id),
                                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun QuietModuleStep(
    modules: List<Subject>,
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
            Surface(
                onClick = { onModuleClick(subject) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp),
                shape = QuietCardShape,
                border = androidx.compose.foundation.BorderStroke(0.8.dp, com.example.yanji.theme.YanjiColors.separator),
                color = com.example.yanji.theme.YanjiColors.elevatedSurface
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = subject.name,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "分钟",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        color = if (selected) QuietSelectedSurface else MaterialTheme.colorScheme.surface
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${option.minutes}",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "自定义时长",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "从零开始累计，不预设结束时间。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                color = YanjiColors.textTertiary
            )
            Button(
                onClick = onStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = "开始专注",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onNavigateToExam) {
                    Text(
                        text = "模拟考试",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
    val isDark = yanjiIsDarkTheme()
    val trackColor = if (isDark) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    }
    val trackBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isDark) 0.35f else 0.70f)

    val trackPadding = 4.dp
    val segmentSpacing = 4.dp

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .background(trackColor)
            .border(1.dp, trackBorderColor, CircleShape)
            .padding(trackPadding)
    ) {
        val totalInnerWidth = maxWidth
        val segmentWidth = (totalInnerWidth - segmentSpacing) / 2f

        val targetOffset = if (isCountdownMode) 0.dp else (segmentWidth + segmentSpacing)
        val animatedOffset by animateDpAsState(
            targetValue = targetOffset,
            animationSpec = spring(
                dampingRatio = 0.82f,
                stiffness = 500f
            ),
            label = "timerSegmentSlider"
        )

        // 选中的圆角胶囊滑动指示器（显眼饱满的主色背景与柔和微光投影）
        Box(
            modifier = Modifier
                .offset { androidx.compose.ui.unit.IntOffset(animatedOffset.roundToPx(), 0) }
                .width(segmentWidth)
                .fillMaxHeight()
                .shadow(
                    elevation = if (isDark) 4.dp else 2.dp,
                    shape = CircleShape,
                    ambientColor = if (isDark) Color.Black.copy(alpha = 0.30f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                    spotColor = if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.40f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                )
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )

        // 倒计时与正向计时的文本选项
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(segmentSpacing)
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
    val textColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 500f),
        label = "segmentTextColor"
    )

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .semantics { this.selected = selected }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
            ),
            color = textColor
        )
    }
}

@Composable
private fun QuietCustomDurationDialog(
    initialMinutes: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var input by rememberSaveable(initialMinutes) { mutableStateOf("$initialMinutes") }
    val commonMinutes = listOf(15, 30, 45, 60, 75, 90, 120, 150, 180)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "自定义时长",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "输入 1–360 分钟，或选择常用时长。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter(Char::isDigit).take(3) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("分钟") },
                    singleLine = true,
                    shape = RoundedCornerShape(YanjiRadius.Small)
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
                            color = if (selected) QuietSelectedSurface else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "$minutes 分钟",
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(YanjiRadius.ButtonRadius)
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

private fun quietCategoryTagline(id: String): String = when (id) {
    "math" -> "高数 · 线代 · 概率"
    "major" -> "数据结构 · 计组 · 操作系统 · 计网"
    "english" -> "词汇 · 阅读 · 写作"
    "politics" -> "马原 · 毛中特 · 史纲 · 思修"
    else -> "考点梳理 · 专项突破"
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

@Composable
@ReadOnlyComposable
private fun quietCategoryAccent(id: String): Color = when (id) {
    "math" -> SubjectMath
    "major" -> SubjectMajor
    "english" -> SubjectEnglish
    "politics" -> SubjectPolitics
    else -> MaterialTheme.colorScheme.primary
}

/**
 * 科目分类的图标底色 —— 与 [quietCategoryAccent] 同色系的极浅容器。
 *
 * 使用 theme 中与分类色对应的 soft 阶，避免 Screen 内联 hex。
 */
@Composable
@ReadOnlyComposable
private fun quietCategoryIconBackground(id: String): Color = when (id) {
    "math" -> SubjectMathSoft
    "major" -> SubjectMajorSoft
    "english" -> SubjectEnglishSoft
    "politics" -> SubjectPoliticsSoft
    else -> MaterialTheme.colorScheme.surfaceVariant
}
