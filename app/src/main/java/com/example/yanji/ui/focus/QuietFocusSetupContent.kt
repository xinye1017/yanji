package com.example.yanji.ui.focus

import com.example.yanji.ui.icons.RemixIcons
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.minimumInteractiveComponentSize
import com.example.yanji.ui.components.YanjiCard
import com.example.yanji.ui.components.YanjiCardVariant
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.example.yanji.data.DurationFormatter
import com.example.yanji.data.FocusModes
import com.example.yanji.data.FocusPresetRequest
import com.example.yanji.data.QuickStartPreset
import com.example.yanji.data.Subject

import com.example.yanji.theme.YanjiColors
import com.example.yanji.theme.YanjiRadius
import com.example.yanji.ui.components.AppContentInsets
import com.example.yanji.ui.components.YanjiSegmentedControl
import com.example.yanji.ui.components.YanjiSegmentedControlVariant
import com.example.yanji.data.YanjiTime
import kotlin.math.abs

private enum class QuietFocusStep {
    CATEGORY,
    MODULE,
    RHYTHM
}

private data class QuietDurationOption(
    val minutes: Int,
    val mode: String
)

private const val QuietDurationMinMinutes = 25
// 上限与「添加今日计划」的时长滚轮对齐（同为 180 分钟）：
// 今日计划行「开始」会把计划预计时长直接落到这里，若准备页仍只能表达到 100 分钟，
// 「计划 120 分钟 → 滚轮停在 100 分钟」就会让用户看到的时长与真正启动的计时不一致。
private const val QuietDurationMaxMinutes = 180
private const val QuietDurationStepMinutes = 5

// 滚轮中心双侧光感指示线（左右对称，靠近内部处实体，向外侧羽化渐变透明）
private val QuietPointerLength = 68.dp
private val QuietPointerInnerGap = 48.dp
private val QuietPointerThickness = 3.dp

private val QuietDurationOptions =
    (QuietDurationMinMinutes..QuietDurationMaxMinutes step QuietDurationStepMinutes).map { minutes ->
        QuietDurationOption(minutes = minutes, mode = quietModeForMinutes(minutes))
    }

private fun quietModeForMinutes(minutes: Int): String = FocusModes.forPlannedMinutes(minutes)

private fun normalizeQuietDuration(minutes: Int): Int {
    val clamped = minutes.coerceIn(QuietDurationMinMinutes, QuietDurationMaxMinutes)
    val steps = ((clamped - QuietDurationMinMinutes) + QuietDurationStepMinutes / 2) / QuietDurationStepMinutes
    return QuietDurationMinMinutes + steps * QuietDurationStepMinutes
}

/**
 * The restrained focus-preparation flow. It deliberately keeps one visual focus per step:
 * subject, module, then timer. Shared header, progress, spacing, cards, and selected states
 * make the three screens feel like one continuous decision instead of three dashboards.
 *
 * ## 一次外部预设（今日计划行「开始」）为什么由这里消费
 *
 * 科目 / 时长 / 备注的**事实**由 [FocusScreen] 持有，本页只是经回调写回它们。
 * 预设请求因此也唯一交给本页消费：一次点击要同时决定「跳到第几步」与「三个字段填什么」，
 * 拆成父子两处各消费一半，就会出现「科目写回去了、步骤没跳」的半应用状态。
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
    onNavigateToDailyDetail: (String) -> Unit = {},
    onManualLogClick: () -> Unit = {},
    noteText: String = "",
    onNoteChange: (String) -> Unit = {},
    quickStartPresets: List<QuickStartPreset> = emptyList(),
    onSavePreset: () -> Unit = {},
    onQuickStart: (QuickStartPreset) -> Unit = {},
    onDeleteQuickStart: (String) -> Unit = {},
    presetRequest: FocusPresetRequest? = null,
    onPresetApplied: (() -> Unit)? = null,
    /** 末步沉浸式开关的上报通道：true 时宿主把底部浮岛滑出屏幕。 */
    onImmersiveChange: ((Boolean) -> Unit)? = null
) {
    val todayIso = remember {
        YanjiTime.todayIso()
    }
    var currentStep by rememberSaveable { mutableStateOf(QuietFocusStep.CATEGORY) }
    // rememberSaveable 的 key 必须能存进 Bundle，List<Subject> 做不到；
    // 用稳定的 id 投影既保留「学科集变化时重置」的原意，又让进程重建可正确恢复。
    val subjectIdsKey = remember(subjects) { subjects.map { it.id } }
    var selectedCategoryId by rememberSaveable(subjectIdsKey) {
        mutableStateOf(selectedSubject.parentId ?: selectedSubject.id)
    }
    var isCountdownMode by rememberSaveable {
        mutableStateOf(true)
    }
    var selectedDurationMinutes by rememberSaveable {
        val minutes = (FocusModes.targetSeconds(selectedMode) / 60L).toInt()
        mutableIntStateOf(normalizeQuietDuration(if (minutes > 0) minutes else 45))
    }

    // 外部预设落地：跳过「选大类 / 选细分」两步，直接停在最后一步，
    // 并把计划里的科目、预计时长（标题由 FocusScreen 侧作为备注传入）一并填好。
    //
    // 三个字段一律经 [onSelectSubject] / [onSelectMode] / [onNoteChange] 写回上层，
    // 不在本页另存一份事实；本页只额外同步自己内部才会有的步骤与滚轮位置。
    //
    // subjects 进 key：学科目录异步加载，首帧可能还是空表；等目录到位再解析目标学科，
    // 才不会把预设落到一个兜底默认科目上。
    LaunchedEffect(presetRequest, subjects) {
        val req = presetRequest ?: return@LaunchedEffect
        val resolved = subjects.firstOrNull { it.id == req.subjectId } ?: selectedSubject
        onSelectSubject(resolved)
        selectedCategoryId = resolved.parentId ?: resolved.id
        if (req.plannedMinutes > 0) {
            val minutes = normalizeQuietDuration(req.plannedMinutes)
            isCountdownMode = true
            selectedDurationMinutes = minutes
            onSelectMode(FocusModes.forPlannedMinutes(minutes))
        } else {
            // 计划选了「不设定时间」→ 同步落到正向计时，让准备页与计划的意图一致。
            isCountdownMode = false
            onSelectMode(FocusModes.COUNT_UP)
        }
        if (req.note.isNotBlank()) onNoteChange(req.note)
        currentStep = QuietFocusStep.RHYTHM
        onPresetApplied?.invoke()
    }

    // 末步是沉浸式：底部浮岛滑出屏幕，内容随之把原本预留给它的空间吃满。
    // 前两步浮岛仍在，预留量照旧。
    val isImmersive = currentStep == QuietFocusStep.RHYTHM
    LaunchedEffect(isImmersive) { onImmersiveChange?.invoke(isImmersive) }
    // 离开专注 tab（无论当时停在哪一步）都必须把浮岛归还，
    // 否则切到其他 tab 会一直缺一条底栏 —— LaunchedEffect 不会在离开组合时回调。
    DisposableEffect(Unit) {
        onDispose { onImmersiveChange?.invoke(false) }
    }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp)
    ) {
        QuietFocusHeader(
            step = currentStep,
            todayTotalSeconds = todayTotalSeconds,
            onBack = if (currentStep == QuietFocusStep.CATEGORY) null else ::goBack,
            onTodayClick = { onNavigateToDailyDetail(todayIso) },
            onManualLogClick = onManualLogClick
        )

        Spacer(modifier = Modifier.height(20.dp))

        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                if (forward) {
                    (slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { width -> width } + fadeIn(tween(240)))
                        .togetherWith(slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { width -> -width } + fadeOut(tween(200)))
                        .using(sizeTransform = null)
                } else {
                    (slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { width -> -width } + fadeIn(tween(240)))
                        .togetherWith(slideOutHorizontally(tween(280, easing = FastOutSlowInEasing)) { width -> width } + fadeOut(tween(200)))
                        .using(sizeTransform = null)
                }
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
                        subjects = subjects,
                        quickStartPresets = quickStartPresets,
                        onQuickStart = onQuickStart,
                        onDeleteQuickStart = onDeleteQuickStart,
                        onCategoryClick = { category ->
                            selectedCategoryId = category.id
                            val children = subjects.filter { it.parentId == category.id && it.enabled }
                            if (children.isEmpty()) {
                                onSelectSubject(category)
                                isCountdownMode = true
                                val duration = if (selectedDurationMinutes > 0) selectedDurationMinutes else 45
                                val matched = QuietDurationOptions.firstOrNull { it.minutes == duration }
                                onSelectMode(matched?.mode ?: "${duration}分钟专注")
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
                            isCountdownMode = true
                            val duration = if (selectedDurationMinutes > 0) selectedDurationMinutes else 45
                            val matched = QuietDurationOptions.firstOrNull { it.minutes == duration }
                            onSelectMode(matched?.mode ?: "${duration}分钟专注")
                            currentStep = QuietFocusStep.RHYTHM
                        }
                    )
                }

                QuietFocusStep.RHYTHM -> {
                    QuietRhythmStep(
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
                        noteText = noteText,
                        onNoteChange = onNoteChange,
                        onSavePreset = onSavePreset,
                        onStart = onStart,
                        // 末步时浮岛已滑出，只需避开系统手势区；
                        // 仍按 112dp 预留会在底栏消失后留下一大片空白。
                        immersive = isImmersive
                    )
                }
            }
        }
    }
}

@Composable
private fun QuietFocusHeader(
    step: QuietFocusStep,
    todayTotalSeconds: Long,
    onBack: (() -> Unit)?,
    onTodayClick: () -> Unit,
    onManualLogClick: () -> Unit = {}
) {
    val stepIndex = step.ordinal + 1
    val headerTitle = when (step) {
        QuietFocusStep.CATEGORY -> "专注准备"
        QuietFocusStep.MODULE -> "选择学科"
        QuietFocusStep.RHYTHM -> "选择专注时长"
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        // 触控盒由 40dp 扩到 48dp，偏移量同步 -4dp 以保持箭头绘制位置不变
                        .offset(x = (-12).dp)
                ) {
                    Icon(
                        imageVector = RemixIcons.ArrowLeftLine,
                        contentDescription = "返回上一步",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
            Text(
                text = headerTitle,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))

            // 只有第一步显示右侧的“补记”和“今日时长”
            // 第二、三子页取消“补记”与“今日专注时长”
            if (step == QuietFocusStep.CATEGORY) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onManualLogClick)
                            // 不走 primary 做前景：暗色 primaryContainer 是主色 16% 半透明
                            // （0x294F7DF3），再叠 0.55 alpha 后图标与文字同色相低分离，
                            // 实测文字仅 3.5:1。改为 M3 配对 primaryContainer 底 +
                            // onPrimaryContainer 前景（暗色 #7197F7 → 5.0:1，亮色 #2453BF → 6.0:1）。
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = RemixIcons.Edit2Line,
                            contentDescription = "手动补记专注",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "补记",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Row(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onTodayClick)
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = RemixIcons.TimeLine,
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
            }
        }

        QuietProgressIndicator(stepIndex = stepIndex)
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
    subjects: List<Subject>,
    quickStartPresets: List<QuickStartPreset>,
    onQuickStart: (QuickStartPreset) -> Unit,
    onDeleteQuickStart: (String) -> Unit,
    onCategoryClick: (Subject) -> Unit,
    onNavigateToExam: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = AppContentInsets.BottomBarPadding)
    ) {
        // 今日计划已迁到首页（TodayPlanCard）：「今天要做什么」是每天开屏就要回答的问题，
        // 不该藏在选学科的路径里。这里保留的快捷专注是**与当前所选科目/模式绑定**的启动器，
        // 归属本步骤（发起专注），与「今天打算做什么」是两件事，因此留下。
        if (quickStartPresets.isNotEmpty()) {
            QuickPresetSection(
                presets = quickStartPresets,
                onStart = onQuickStart,
                onDelete = onDeleteQuickStart
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "选择学习方向",
            style = MaterialTheme.typography.titleLarge.copy(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(14.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            categories.forEach { category ->
                YanjiCard(
                    onClick = { onCategoryClick(category) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(76.dp),
                    variant = YanjiCardVariant.Grouped
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = category.name,
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

        Spacer(modifier = Modifier.height(20.dp))

        // 模拟考试单独一行，不与学科并列
        YanjiCard(
            onClick = onNavigateToExam,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            variant = YanjiCardVariant.Grouped
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(YanjiRadius.Small))
                            // 同页「补记」chip 同一修正：去掉 0.55 alpha 稀释并改配
                            // onPrimaryContainer 前景，避免图标与底色同色相糊在一起。
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = RemixIcons.TimeLine,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "模拟考试",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "全真考场计时模式",
                            style = MaterialTheme.typography.bodySmall,
                            color = YanjiColors.textTertiary
                        )
                    }
                }
                Icon(
                    imageVector = RemixIcons.ArrowRightSLine,
                    contentDescription = null,
                    tint = YanjiColors.textTertiary,
                    modifier = Modifier.size(20.dp)
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
            YanjiCard(
                onClick = { onModuleClick(subject) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(76.dp),
                variant = YanjiCardVariant.Grouped
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
    isCountdownMode: Boolean,
    selectedDurationMinutes: Int,
    onCountdownSelected: () -> Unit,
    onCountUpSelected: () -> Unit,
    onDurationSelected: (QuietDurationOption) -> Unit,
    noteText: String,
    onNoteChange: (String) -> Unit,
    onSavePreset: () -> Unit,
    onStart: () -> Unit,
    immersive: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (immersive) {
                    Modifier.navigationBarsPadding()
                } else {
                    Modifier.padding(bottom = AppContentInsets.BottomBarPadding)
                }
            )
    ) {
        QuietTimerSegmentedControl(
            isCountdownMode = isCountdownMode,
            onCountdownSelected = onCountdownSelected,
            onCountUpSelected = onCountUpSelected
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (isCountdownMode) {
                QuietDurationWheel(
                    selectedDurationMinutes = selectedDurationMinutes,
                    onDurationSelected = onDurationSelected,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
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
        }

        OutlinedTextField(
            value = noteText,
            onValueChange = { value ->
                if (value.length <= 80) onNoteChange(value)
            },
            label = { Text("本次目标（可选）") },
            placeholder = { Text("例如：二次型 30 道题") },
            singleLine = true,
            supportingText = {
                if (noteText.isNotEmpty()) {
                    Text("${noteText.length}/80")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(YanjiRadius.InputRadius)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onSavePreset) {
                Text("保存为快捷专注")
            }
        }

        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
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
    }
}

@Composable
private fun QuietDurationWheel(
    selectedDurationMinutes: Int,
    onDurationSelected: (QuietDurationOption) -> Unit,
    modifier: Modifier = Modifier
) {
    val normalizedMinutes = normalizeQuietDuration(selectedDurationMinutes)
    val selectedIndex = QuietDurationOptions.indexOfFirst { it.minutes == normalizedMinutes }
        .coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val flingBehavior = rememberSnapFlingBehavior(lazyListState = listState)
    val itemHeight = 44.dp
    var initialized by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // 可见项数由 itemHeight + contentPadding 决定：上下留白后约看到 5 项。
        val centerPadding = if (maxHeight > itemHeight) {
            (maxHeight - itemHeight) / 2
        } else {
            0.dp
        }

        val centeredIndex by remember(listState, selectedIndex) {
            derivedStateOf {
                val layoutInfo = listState.layoutInfo
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                layoutInfo.visibleItemsInfo
                    .minByOrNull { item -> abs((item.offset + item.size / 2) - viewportCenter) }
                    ?.index
                    ?: selectedIndex
            }
        }

        // 某一行的「距中心程度」：0 = 正好居中，1 = 正好差一项，以此类推。
        val distanceInItemsAt: (Int) -> Float = remember(listState) {
            { index ->
                val layoutInfo = listState.layoutInfo
                val viewportCenter =
                    (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2f
                val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                if (item == null) {
                    abs(index - centeredIndex).toFloat()
                } else {
                    val itemSize = item.size.toFloat().coerceAtLeast(1f)
                    abs((item.offset + item.size / 2f) - viewportCenter) / itemSize
                }
            }
        }

        // 字号缓动：中心项为 32sp，距离中心一项及以上为 16sp。
        val sizeEaseAt: (Int) -> Float = remember(listState) {
            { index -> (1f - distanceInItemsAt(index)).coerceIn(0f, 1f) }
        }

        // 透明度：比字号衰减得更缓，保证相邻项仍然看得见。
        val fadeAt: (Int) -> Float = remember(listState) {
            { index -> (1f - distanceInItemsAt(index) / 2.5f).coerceIn(0f, 1f) }
        }

        LaunchedEffect(selectedIndex, centerPadding) {
            if (!initialized) {
                listState.scrollToItem(selectedIndex)
                initialized = true
            }
        }

        LaunchedEffect(centeredIndex, initialized) {
            if (!initialized) return@LaunchedEffect
            QuietDurationOptions.getOrNull(centeredIndex)?.let { option ->
                if (option.minutes != selectedDurationMinutes) {
                    onDurationSelected(option)
                }
            }
        }

        LaunchedEffect(normalizedMinutes) {
            if (!initialized || listState.isScrollInProgress) return@LaunchedEffect
            val targetIndex = QuietDurationOptions.indexOfFirst { it.minutes == normalizedMinutes }
            if (targetIndex >= 0 && targetIndex != centeredIndex) {
                listState.animateScrollToItem(targetIndex)
            }
        }

        // 手势层铺满整页（LazyColumn 占满可用区域，拖动/惯性/吸附都自然可用），
        // 但只在中间 bandHeight 高的可见带内绘制，形成「约 5 项」的滚轮观感。
        val visibleItems = 5
        val bandHeight = itemHeight * visibleItems
        val bandTopPx = with(LocalDensity.current) { ((maxHeight - bandHeight) / 2).toPx() }
        val bandBottomPx = with(LocalDensity.current) { ((maxHeight + bandHeight) / 2).toPx() }

        // 固定的左右中心双侧光感指示线：直接画在 LazyColumn 的绘制层里，与列表内容共享
        // 同一套坐标，因此必然与选中行严格同线。
        val pointerLengthPx = with(LocalDensity.current) { QuietPointerLength.toPx() }
        val innerGapPx = with(LocalDensity.current) { QuietPointerInnerGap.toPx() }
        val pointerThicknessPx = with(LocalDensity.current) { QuietPointerThickness.toPx() }
        val pointerCenterX = with(LocalDensity.current) { maxWidth.toPx() / 2f }
        // 指针的 y 必须和「选中行」在同一条线上：行中心 = centerPadding + itemHeight/2。
        // 不能直接用 size.height/2，因为绘制高度和 contentPadding 依据的高度可能差几 px。
        val pointerCenterY = with(LocalDensity.current) {
            (centerPadding + itemHeight / 2).toPx()
        }
        val pointerColor = MaterialTheme.colorScheme.primary

        LazyColumn(
            state = listState,
            flingBehavior = flingBehavior,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    clipRect(top = bandTopPx, bottom = bandBottomPx) {
                        this@drawWithContent.drawContent()
                    }
                    val cy = pointerCenterY
                    val leftInnerX = pointerCenterX - innerGapPx
                    val leftOuterX = leftInnerX - pointerLengthPx
                    val rightInnerX = pointerCenterX + innerGapPx
                    val rightOuterX = rightInnerX + pointerLengthPx

                    // 左侧羽化指示线：靠近内部实体 -> 外侧渐变透明
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(pointerColor.copy(alpha = 0f), pointerColor),
                            startX = leftOuterX,
                            endX = leftInnerX
                        ),
                        start = Offset(leftOuterX, cy),
                        end = Offset(leftInnerX, cy),
                        strokeWidth = pointerThicknessPx,
                        cap = StrokeCap.Round
                    )

                    // 右侧羽化指示线：靠近内部实体 -> 外侧渐变透明
                    drawLine(
                        brush = Brush.horizontalGradient(
                            colors = listOf(pointerColor, pointerColor.copy(alpha = 0f)),
                            startX = rightInnerX,
                            endX = rightOuterX
                        ),
                        start = Offset(rightInnerX, cy),
                        end = Offset(rightOuterX, cy),
                        strokeWidth = pointerThicknessPx,
                        cap = StrokeCap.Round
                    )
                },
            contentPadding = PaddingValues(vertical = centerPadding)
        ) {
            itemsIndexed(
                items = QuietDurationOptions,
                key = { _, option -> option.minutes },
                // 所有行结构一致，交给 LazyColumn 复用已有组合，避免重复创建。
                contentType = { _, _ -> "durationRow" }
            ) { index, option ->
                val isMajorTick = option.minutes % 25 == 0

                // 选中判定用精确的整数索引比较：centeredIndex 只在「居中项切换」时
                // 才变化（不是每帧），因此这里的重组次数很少。
                val isCentered = index == centeredIndex

                // 颜色不再用 animateColorAsState（那会给每行各起一个动画协程，
                // 且随滚动逐帧改色 → 逐帧重组）。改为固定基色 + 在绘制期按偏移
                // 调整透明度：状态只影响重绘，不影响重组。
                val baseTextColor = if (isCentered) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                // 各行透明度上限（沿用旧视觉：选中最亮，越远越淡）。
                val textAlphaMax = if (isCentered) 1f else 0.82f
                val baseFontSizeSp = 16f
                val sizeBoost = if (isMajorTick && !isCentered) 1.08f else 1f

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .semantics { this.selected = isCentered }
                        .clickable(
                            // 点击直接吸附到该值，不要水波纹/阴影反馈（避免遮挡选中态）。
                            interactionSource = null,
                            indication = null,
                            role = Role.RadioButton,
                            onClick = { onDurationSelected(option) }
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Row(
                        modifier = Modifier.width(104.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // 字号按连续偏移插值，但关键是把「读取滚动状态」推迟到绘制阶段：
                        // 文本始终按**最大字号**（32sp）排版，布局盒因此永远预留足够宽度
                        // （不会与「分钟」重叠），实际大小由 graphicsLayer 在绘制期缩小。
                        // 这样滚动时完全跳过重组与重新排版 —— 这是滚轮顺滑的关键。
                        val maxFontSizeSp = 32f
                        Text(
                            text = "${option.minutes}",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = maxFontSizeSp.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            color = baseTextColor,
                            modifier = Modifier.graphicsLayer {
                                // 在绘制块内读取滚动状态：只触发重绘，不触发重组。
                                val eased = sizeEaseAt(index)
                                val target = lerp(baseFontSizeSp, maxFontSizeSp, eased) * sizeBoost
                                val s = target / maxFontSizeSp
                                scaleX = s
                                scaleY = s
                                alpha = fadeAt(index) * textAlphaMax
                            }
                        )
                        if (isCentered) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "分钟",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.86f)
                            )
                        }
                    }
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
    YanjiSegmentedControl(
        items = listOf("倒计时", "正向计时"),
        selectedIndex = if (isCountdownMode) 0 else 1,
        onItemSelected = { index ->
            if (index == 0) onCountdownSelected() else onCountUpSelected()
        },
        variant = YanjiSegmentedControlVariant.OnPage,
        modifier = Modifier.fillMaxWidth()
    )
}
