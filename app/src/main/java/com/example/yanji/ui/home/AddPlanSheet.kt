@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.yanji.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.yanji.data.StudyTask
import com.example.yanji.data.Subject
import com.example.yanji.theme.YanjiColors
import com.example.yanji.theme.YanjiRadius
import com.example.yanji.theme.YanjiSpacing
import com.example.yanji.theme.yanjiSubjectColorOf
import com.example.yanji.ui.icons.RemixIcons
import kotlinx.coroutines.flow.filter

/**
 * 页面枚举：主表单、科目选择器、时长滚轮选择器。
 */
private enum class AddPlanPage { Form, Subject, Duration }

private val DurationPresets = listOf(25, 45, 60, 90)
private val HourValues = (0..12).toList()
private val MinuteValues = (0..55 step 5).toList()

/**
 * 学科大类与其子学科聚合条目。
 */
data class SubjectCategoryItem(
    val category: Subject,
    val color: Color,
    val subjects: List<Subject> = emptyList()
)

/**
 * 学科选择状态（可选中某大类自身或其下具体子学科）。
 */
data class SubjectSelection(
    val categoryItem: SubjectCategoryItem,
    val subject: Subject? = null
) {
    val label: String
        get() = if (subject == null) categoryItem.category.name else "${categoryItem.category.name} › ${subject.name}"

    val shortName: String
        get() = subject?.name ?: categoryItem.category.name

    val actualSubject: Subject
        get() = subject ?: categoryItem.category
}

/**
 * 添加今日计划 BottomSheet：
 * 1. 支持学科层级选择（左侧大类 + 右侧子科目）与最近使用快捷选择。
 * 2. 计划内容选填，留空自动生成 "${科目}复习"。
 * 3. 预计时长支持预设 Chip、自定义滚轮选择（时/分）与不限时。
 * 4. 实时显示添加后今日计划总时长与日目标对比，超标告警。
 * 5. 支持「添加到今日计划」与「添加并开始专注」双确认入口。
 */
@Composable
fun AddPlanSheet(
    subjects: List<Subject>,
    recentTasks: List<StudyTask> = emptyList(),
    plannedTodayMinutes: Int = 0,
    dailyGoalMinutes: Int = 0,
    editingTask: StudyTask? = null,
    onDismiss: () -> Unit,
    onConfirm: (subject: Subject, title: String, durationMinutes: Int, startNow: Boolean) -> Unit,
    onCreateCategory: ((String) -> Unit)? = null,
    onCreateSubSubject: ((parentId: String, name: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var page by rememberSaveable { mutableStateOf(AddPlanPage.Form) }

    // 组织大类和子学科列表
    val enabledSubjects = remember(subjects) { subjects.filter { it.enabled } }
    val categoryItems = remember(enabledSubjects) {
        val categories = enabledSubjects.filter { it.isCategory }.sortedBy { it.sortOrder }
        categories.map { cat ->
            val children = enabledSubjects
                .filter { it.parentId == cat.id }
                .sortedBy { it.sortOrder }
            SubjectCategoryItem(
                category = cat,
                color = Color.Unspecified,
                subjects = children
            )
        }
    }

    // 附加大类颜色（保证深浅色模式与主题动态响应）
    val coloredCategories = categoryItems.map { item ->
        val c = yanjiSubjectColorOf(item.category.name)
        remember(item, c) { item.copy(color = c) }
    }

    // 计算最近选择
    val recentSelections = remember(recentTasks, coloredCategories) {
        val list = mutableListOf<SubjectSelection>()
        for (task in recentTasks) {
            val matchingCat = coloredCategories.firstOrNull { it.category.id == task.subjectId }
            if (matchingCat != null) {
                list.add(SubjectSelection(matchingCat, null))
                continue
            }
            for (cat in coloredCategories) {
                val matchingSub = cat.subjects.firstOrNull { it.id == task.subjectId }
                if (matchingSub != null) {
                    list.add(SubjectSelection(cat, matchingSub))
                    break
                }
            }
        }
        list.distinctBy { it.actualSubject.id }.take(3)
    }

    // 编辑模式或默认选择：优先匹配待编辑任务，其次取最近使用，最后取首个大类及首个子科目
    val editingCat = remember(editingTask, coloredCategories) {
        editingTask?.let { task ->
            coloredCategories.firstOrNull { it.category.id == task.subjectId || it.subjects.any { s -> s.id == task.subjectId } }
        }
    }
    val editingSub = remember(editingTask, editingCat) {
        editingCat?.subjects?.firstOrNull { it.id == editingTask?.subjectId }
    }

    var selectedCategoryItem by remember(coloredCategories, editingTask) {
        mutableStateOf(
            editingCat
                ?: recentSelections.firstOrNull()?.categoryItem
                ?: coloredCategories.firstOrNull()
        )
    }
    var selectedSubSubject by remember(coloredCategories, editingTask) {
        mutableStateOf(
            editingSub
                ?: (if (editingCat != null) null else recentSelections.firstOrNull()?.subject)
                ?: coloredCategories.firstOrNull()?.subjects?.firstOrNull()
        )
    }

    val currentSelection = selectedCategoryItem?.let {
        SubjectSelection(it, selectedSubSubject)
    }

    var title by rememberSaveable(editingTask?.id) {
        mutableStateOf(editingTask?.title.orEmpty())
    }
    // durationMinutes: null 表示不限时
    var durationMinutes by rememberSaveable(editingTask?.id) {
        mutableStateOf<Int?>(
            editingTask?.let { if (it.plannedMinutes > 0) it.plannedMinutes else null } ?: 45
        )
    }

    // 新建科目弹窗状态
    var showCreateDialog by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = YanjiRadius.SheetRadius, topEnd = YanjiRadius.SheetRadius),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        modifier = modifier
    ) {
        // 内置返回拦截：在科目/时长子页面按系统返回键退回主表单，而非关闭整个 BottomSheet
        BackHandler(enabled = page != AddPlanPage.Form) {
            page = AddPlanPage.Form
        }

        AnimatedContent(
            targetState = page,
            transitionSpec = {
                val forward = targetState != AddPlanPage.Form
                (slideInHorizontally { if (forward) it / 4 else -it / 4 } + fadeIn()) togetherWith
                    (slideOutHorizontally { if (forward) -it / 4 else it / 4 } + fadeOut())
            },
            label = "add-plan-page-anim"
        ) { currentPage ->
            when (currentPage) {
                AddPlanPage.Form -> {
                    FormPage(
                        isEditing = editingTask != null,
                        originalMinutes = editingTask?.plannedMinutes,
                        selection = currentSelection,
                        recentSelections = recentSelections,
                        title = title,
                        durationMinutes = durationMinutes,
                        plannedTodayMinutes = plannedTodayMinutes,
                        dailyGoalMinutes = dailyGoalMinutes,
                        onOpenSubjectPicker = { page = AddPlanPage.Subject },
                        onSelectRecent = { r ->
                            selectedCategoryItem = r.categoryItem
                            selectedSubSubject = r.subject
                        },
                        onTitleChange = { if (it.length <= 60) title = it },
                        onDurationChange = { durationMinutes = it },
                        onOpenCustomDuration = { page = AddPlanPage.Duration },
                        onSubmit = { startNow ->
                            val sel = currentSelection ?: return@FormPage
                            val finalTitle = title.trim().ifBlank { "${sel.shortName}复习" }
                            onConfirm(sel.actualSubject, finalTitle, durationMinutes ?: 0, startNow)
                        }
                    )
                }

                AddPlanPage.Subject -> {
                    SubjectPickerPage(
                        categories = coloredCategories,
                        currentSelection = currentSelection,
                        onBack = { page = AddPlanPage.Form },
                        onSelect = { sel ->
                            selectedCategoryItem = sel.categoryItem
                            selectedSubSubject = sel.subject
                            page = AddPlanPage.Form
                        },
                        onCreateSubject = { showCreateDialog = true }
                    )
                }

                AddPlanPage.Duration -> {
                    DurationPickerPage(
                        initialMinutes = durationMinutes ?: 45,
                        onBack = { page = AddPlanPage.Form },
                        onDone = { mins ->
                            durationMinutes = mins
                            page = AddPlanPage.Form
                        }
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateSubjectDialog(
            activeCategory = selectedCategoryItem?.category,
            onDismiss = { showCreateDialog = false },
            onCreateCategory = { name ->
                onCreateCategory?.invoke(name)
                showCreateDialog = false
            },
            onCreateSubSubject = { parentId, name ->
                onCreateSubSubject?.invoke(parentId, name)
                showCreateDialog = false
            }
        )
    }
}

// --------------------------- 主表单页 ---------------------------

private fun getSubjectActionSuggestions(subjectName: String): List<String> {
    val name = subjectName.lowercase()
    return when {
        name.contains("数") || name.contains("代数") || name.contains("微积分") || name.contains("概率") ->
            listOf("刷题训练", "错题订正", "真题模考", "概念复盘", "专项突破")
        name.contains("英") || name.contains("词") || name.contains("语") ->
            listOf("背核心词", "阅读真题", "长难句拆解", "作文模写", "真题精读")
        name.contains("政") || name.contains("思修") || name.contains("马原") || name.contains("毛中特") || name.contains("史纲") ->
            listOf("选择题刷题", "马原框架", "考点默写", "时政热点", "大题背诵")
        name.contains("计") || name.contains("408") || name.contains("数据结构") || name.contains("网") || name.contains("原理") || name.contains("操作系统") ->
            listOf("代码训练", "真题演练", "错题复盘", "框架梳理", "章节小测")
        else ->
            listOf("核心刷题", "概念复习", "真题精析", "背诵默写", "错题整理")
    }
}

@Composable
private fun FormPage(
    isEditing: Boolean = false,
    originalMinutes: Int? = null,
    selection: SubjectSelection?,
    recentSelections: List<SubjectSelection>,
    title: String,
    durationMinutes: Int?,
    plannedTodayMinutes: Int,
    dailyGoalMinutes: Int,
    onOpenSubjectPicker: () -> Unit,
    onSelectRecent: (SubjectSelection) -> Unit,
    onTitleChange: (String) -> Unit,
    onDurationChange: (Int?) -> Unit,
    onOpenCustomDuration: () -> Unit,
    onSubmit: (startNow: Boolean) -> Unit
) {
    val basePlanned = if (isEditing) {
        (plannedTodayMinutes - (originalMinutes ?: 0)).coerceAtLeast(0)
    } else {
        plannedTodayMinutes
    }
    val total = basePlanned + (durationMinutes ?: 0)
    val hasGoal = dailyGoalMinutes > 0
    val over = hasGoal && total > dailyGoalMinutes

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = YanjiSpacing.PageHorizontalPadding, vertical = 8.dp)
    ) {
        Text(
            text = if (isEditing) "编辑今日计划" else "添加今日计划",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 1. 科目区域
        FormSectionHeader(title = "科目")
        Spacer(modifier = Modifier.height(6.dp))

        if (selection != null) {
            SubjectField(
                selection = selection,
                onClick = onOpenSubjectPicker
            )
        }

        if (recentSelections.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "最近",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                recentSelections.forEach { r ->
                    SuggestionChip(
                        onClick = { onSelectRecent(r) },
                        label = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(r.categoryItem.color)
                                )
                                Text(r.shortName)
                            }
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (selection?.actualSubject?.id == r.actualSubject.id) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            }
                        ),
                        border = null,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 2. 内容输入（选填）+ 快捷灵感词
        FormSectionHeader(title = "内容（选填）")
        Spacer(modifier = Modifier.height(6.dp))

        OutlinedTextField(
            value = title,
            onValueChange = onTitleChange,
            placeholder = {
                Text(
                    text = "例如：第三章 微分中值定理",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            trailingIcon = if (title.isNotBlank()) {
                {
                    IconButton(onClick = { onTitleChange("") }) {
                        Icon(
                            imageVector = RemixIcons.CloseLine,
                            contentDescription = "清空",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            } else null,
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("add_plan_title_input")
        )

        Spacer(modifier = Modifier.height(8.dp))
        val currentSubName = selection?.shortName.orEmpty()
        val suggestions = remember(currentSubName) { getSubjectActionSuggestions(currentSubName) }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            suggestions.forEach { tag ->
                Surface(
                    onClick = {
                        if (title.isBlank()) {
                            onTitleChange(tag)
                        } else if (!title.contains(tag)) {
                            onTitleChange("$title $tag")
                        }
                    },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.height(28.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = RemixIcons.AddLine,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = tag,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 3. 预计时长
        FormSectionHeader(title = "预计时长")
        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DurationPresets.forEach { m ->
                PlanChip(
                    selected = durationMinutes == m,
                    label = "${m}m",
                    onClick = { onDurationChange(m) }
                )
            }
            val customMinutes = durationMinutes?.takeIf { it !in DurationPresets }
            val customLabel = if (customMinutes != null) formatMinutes(customMinutes) else "自定义"
            PlanChip(
                selected = customMinutes != null,
                label = customLabel,
                onClick = onOpenCustomDuration
            )
            PlanChip(
                selected = durationMinutes == null,
                label = "不限时",
                onClick = { onDurationChange(null) }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (hasGoal) {
            val baseRatio = (basePlanned.toFloat() / dailyGoalMinutes).coerceIn(0f, 1f)
            val addedRatio = ((durationMinutes ?: 0).toFloat() / dailyGoalMinutes).coerceIn(0f, 1f - baseRatio)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            ) {
                if (baseRatio > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(baseRatio)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                    )
                }
                if (addedRatio > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(baseRatio + addedRatio)
                            .fillMaxHeight()
                            .background(
                                if (over) YanjiColors.warning else MaterialTheme.colorScheme.primary
                            )
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // 今日计划总时长与日目标对比辅助文案
        val goalComparisonText = if (hasGoal) {
            if (over) {
                val overMins = total - dailyGoalMinutes
                "累计计划 ${formatMinutes(total)} / 目标 ${formatMinutes(dailyGoalMinutes)} (超标 ${formatMinutes(overMins)})"
            } else {
                val remainMins = dailyGoalMinutes - total
                "累计计划 ${formatMinutes(total)} / 目标 ${formatMinutes(dailyGoalMinutes)} (还可安排 ${formatMinutes(remainMins)})"
            }
        } else {
            "累计今日计划共 ${formatMinutes(total)}"
        }

        Text(
            text = goalComparisonText,
            style = MaterialTheme.typography.bodySmall,
            color = if (over) YanjiColors.warning else MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 4. 双操作入口
        Button(
            onClick = { onSubmit(false) },
            enabled = selection != null,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("add_plan_confirm_button")
        ) {
            Text(
                text = if (isEditing) "保存修改" else "添加到今日计划",
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        TextButton(
            onClick = { onSubmit(true) },
            enabled = selection != null,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("add_plan_start_now_button")
        ) {
            Text(
                text = if (isEditing) "保存并开始专注" else "添加并开始专注",
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun FormSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun SubjectField(
    selection: SubjectSelection,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("add_plan_subject_field")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(selection.categoryItem.color)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = selection.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = RemixIcons.ArrowRightSLine,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun PlanChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = RemixIcons.CheckLine,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else null,
        shape = RoundedCornerShape(12.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = Color.Transparent,
            selectedBorderColor = Color.Transparent
        )
    )
}

// --------------------------- 科目选择器页 ---------------------------

@Composable
private fun SubjectPickerPage(
    categories: List<SubjectCategoryItem>,
    currentSelection: SubjectSelection?,
    onBack: () -> Unit,
    onSelect: (SubjectSelection) -> Unit,
    onCreateSubject: () -> Unit
) {
    var activeCategoryItem by remember(categories, currentSelection) {
        mutableStateOf(currentSelection?.categoryItem ?: categories.firstOrNull())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
    ) {
        PageHeader(title = "选择科目", onBack = onBack)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
        ) {
            // 左列：大类
            LazyColumn(
                modifier = Modifier
                    .width(116.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                items(categories, key = { it.category.id }) { cat ->
                    val isActive = activeCategoryItem?.category?.id == cat.category.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (cat.subjects.isEmpty()) {
                                    onSelect(SubjectSelection(cat, null))
                                } else {
                                    activeCategoryItem = cat
                                }
                            }
                            .background(
                                if (isActive) MaterialTheme.colorScheme.surface
                                else Color.Transparent
                            )
                            .padding(vertical = 14.dp, horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(20.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isActive) cat.color else Color.Transparent)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = cat.category.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isActive) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 右列：子科目
            val active = activeCategoryItem
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 14.dp)
            ) {
                if (active != null) {
                    // 第一个选项：选中大类本身（全部/不细分）
                    val isCatSelected = currentSelection?.categoryItem?.category?.id == active.category.id &&
                        currentSelection.subject == null
                    item(key = "${active.category.id}_all") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(SubjectSelection(active, null)) }
                                .padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "全部${active.category.name}（不细分）",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isCatSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isCatSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                            if (isCatSelected) {
                                Icon(
                                    imageVector = RemixIcons.CheckLine,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = YanjiColors.rowDivider)
                    }

                    // 子科目列表
                    items(active.subjects, key = { it.id }) { sub ->
                        val isSubSelected = currentSelection?.categoryItem?.category?.id == active.category.id &&
                            currentSelection.subject?.id == sub.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(SubjectSelection(active, sub)) }
                                .padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = sub.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isSubSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (isSubSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                            if (isSubSelected) {
                                Icon(
                                    imageVector = RemixIcons.CheckLine,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        HorizontalDivider(thickness = 0.5.dp, color = YanjiColors.rowDivider)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 底部快捷新建
        TextButton(
            onClick = onCreateSubject,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Icon(
                imageVector = RemixIcons.AddLine,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("新建科目")
        }
    }
}

// --------------------------- 时长滚轮选择器页 ---------------------------

@Composable
private fun DurationPickerPage(
    initialMinutes: Int,
    onBack: () -> Unit,
    onDone: (Int) -> Unit
) {
    var hours by remember { mutableIntStateOf((initialMinutes / 60).coerceIn(0, 12)) }
    var minutes by remember { mutableIntStateOf((initialMinutes % 60 / 5 * 5).coerceIn(0, 55)) }
    val total = hours * 60 + minutes

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
    ) {
        PageHeader(title = "自定义时长", onBack = onBack) {
            TextButton(
                onClick = { onDone(total) },
                enabled = total >= 5
            ) {
                Text("完成", fontWeight = FontWeight.SemiBold)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            WheelPicker(
                values = HourValues,
                initialIndex = hours,
                onSelected = { hours = it }
            )
            Text(
                text = "小时",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            WheelPicker(
                values = MinuteValues,
                initialIndex = (minutes / 5).coerceIn(0, MinuteValues.lastIndex),
                onSelected = { minutes = it }
            )
            Text(
                text = "分钟",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
    }
}

@Composable
private fun WheelPicker(
    values: List<Int>,
    initialIndex: Int,
    onSelected: (Int) -> Unit
) {
    val itemHeight = 44.dp
    val itemHeightPx = with(LocalDensity.current) { itemHeight.roundToPx() }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex.coerceIn(values.indices))
    val currentOnSelected by rememberUpdatedState(onSelected)

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .filter { !it }
            .collect {
                val index = listState.firstVisibleItemIndex +
                    if (listState.firstVisibleItemScrollOffset > itemHeightPx / 2) 1 else 0
                currentOnSelected(values[index.coerceIn(values.indices)])
            }
    }

    Box(
        modifier = Modifier
            .width(76.dp)
            .height(itemHeight * 3),
        contentAlignment = Alignment.Center
    ) {
        // 中间高亮带
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f))
        )
        LazyColumn(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
            contentPadding = PaddingValues(vertical = itemHeight),
            modifier = Modifier.fillMaxSize()
        ) {
            items(values.size) { i ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = values[i].toString().padStart(2, '0'),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

// --------------------------- 通用组件 ---------------------------

@Composable
private fun PageHeader(
    title: String,
    onBack: () -> Unit,
    action: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = RemixIcons.ArrowLeftLine,
                contentDescription = "返回",
                modifier = Modifier.size(20.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (action != null) {
            action()
        }
    }
}

/**
 * 新建科目对话框：支持在当前活跃大类下新建子科目，或新建顶级学科大类。
 */
@Composable
private fun CreateSubjectDialog(
    activeCategory: Subject?,
    onDismiss: () -> Unit,
    onCreateCategory: (String) -> Unit,
    onCreateSubSubject: (parentId: String, name: String) -> Unit
) {
    var subjectName by rememberSaveable { mutableStateOf("") }
    // 0: 在当前大类下新建子科目, 1: 新建顶级大类
    var createMode by rememberSaveable { mutableIntStateOf(if (activeCategory != null) 0 else 1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "新建科目",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (activeCategory != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = createMode == 0,
                            onClick = { createMode = 0 },
                            label = { Text("属于「${activeCategory.name}」") },
                            shape = RoundedCornerShape(10.dp)
                        )
                        FilterChip(
                            selected = createMode == 1,
                            onClick = { createMode = 1 },
                            label = { Text("新顶级大类") },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }

                OutlinedTextField(
                    value = subjectName,
                    onValueChange = { if (it.length <= 20) subjectName = it },
                    placeholder = {
                        Text(
                            if (createMode == 0 && activeCategory != null) "例如：常微分方程"
                            else "例如：专业课二"
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val clean = subjectName.trim()
                    if (clean.isNotBlank()) {
                        if (createMode == 0 && activeCategory != null) {
                            onCreateSubSubject(activeCategory.id, clean)
                        } else {
                            onCreateCategory(clean)
                        }
                    }
                },
                enabled = subjectName.trim().isNotBlank()
            ) {
                Text("确定", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun formatMinutes(minutes: Int): String {
    if (minutes <= 0) return "0m"
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}
