@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.yanji.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.yanji.data.StudyTask
import com.example.yanji.data.Subject
import com.example.yanji.theme.YanjiColors
import com.example.yanji.theme.YanjiMotion
import com.example.yanji.theme.YanjiRadius
import com.example.yanji.theme.YanjiSpacing
import com.example.yanji.theme.yanjiSubjectColorOf
import com.example.yanji.ui.icons.RemixIcons
import kotlinx.coroutines.flow.filter

/**
 * 页面枚举：主表单、科目选择器、时长滚轮选择器。
 */
private enum class AddPlanPage { Form, Subject, Duration }

// 可选时长滚轮刻度：5 ~ 180 分钟（上限 180 分钟，以 5 分钟递增）
private val DurationValues = (5..180 step 5).toList()

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
    /**
     * 显示名：只取末端学科名，不做「大学科 + 细分」的路径拼接（真机规则 2）。
     * 拼接样式像面包屑，在窄字段里既挤又不比末端名多信息。
     */
    val displayName: String
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

    // 编辑模式或默认选择：优先匹配待编辑任务，其次取最近使用，最后取首个大类
    //（新计划默认落在**大学科自身**，不预选细分学科 —— 真机规则 1）。
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
    // 默认选择（真机规则 1）：新计划一律落在大学科自身，不预选细分学科；
    // 仅编辑既有任务时回填它的细分学科，保证改计划不串科目。
    var selectedSubSubject by remember(coloredCategories, editingTask) {
        mutableStateOf(editingSub)
    }

    val currentSelection = selectedCategoryItem?.let {
        SubjectSelection(it, selectedSubSubject)
    }

    var title by rememberSaveable(editingTask?.id) {
        mutableStateOf(editingTask?.title.orEmpty())
    }
    // durationMinutes: null 表示不设定时间（无限时间，默认不设定时间）
    var durationMinutes by rememberSaveable(editingTask?.id) {
        mutableStateOf<Int?>(
            editingTask?.let { if (it.plannedMinutes > 0) it.plannedMinutes else null }
        )
    }

    // 页面切换过渡：**纯交叉淡变，零方向位移**。
    //
    // 为什么去掉横向 slide：原实现是 slideIn/slideOutHorizontally(25% 宽) + fade，
    // 选科目回跳表单时，滑入内容的水平位移与 AnimatedContent 默认 SizeTransform 的高度
    // 形变叠加，真机上读成左下→右上的斜向扭动（用户原话“看着太别扭”）。层级切换只需
    // 让内容本身淡入淡出，高度仍交给 SizeTransform 平滑收敛 —— 位移量归零后斜向感消失，
    // 动效退回“哪一页在上层”这一个语义。
    //
    // spec 必须在组合作用域求值：accessibleFiniteTween 是 @Composable，
    // transitionSpec 不是（同 StatsTrendChart drawSpec 的既有写法）。
    // 附带收益：系统开启「减少动态效果」时自动降级为瞬切，原 slide 无此降级。
    val pageFade = YanjiMotion.accessibleFiniteTween<Float>(
        durationMillis = YanjiMotion.DurationFast,
        easing = YanjiMotion.EaseStandard
    )

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
                fadeIn(animationSpec = pageFade) togetherWith fadeOut(animationSpec = pageFade)
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
                        onOpenDurationPicker = { page = AddPlanPage.Duration },
                        onSubmit = { startNow ->
                            val sel = currentSelection ?: return@FormPage
                            val finalTitle = title.trim().ifBlank { "${sel.displayName}复习" }
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
                        }
                    )
                }

                AddPlanPage.Duration -> {
                    DurationPickerPage(
                        initialMinutes = durationMinutes,
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

}

// --------------------------- 主表单页 ---------------------------

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
    onOpenDurationPicker: () -> Unit,
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
                    val isRecentSelected = selection?.actualSubject?.id == r.actualSubject.id
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
                                Text(r.displayName)
                            }
                        },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = if (isRecentSelected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                YanjiColors.fill
                            },
                            labelColor = if (isRecentSelected) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        ),
                        border = null,
                        shape = RoundedCornerShape(10.dp) // token-exempt: 最近使用建议 chip 的紧凑几何，无同值 token
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 2. 备注输入
        FormSectionHeader(title = "备注")
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
            shape = RoundedCornerShape(14.dp), // token-exempt: 标题输入框沿用的独立圆角，无同值 token
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = YanjiColors.fill,
                focusedContainerColor = YanjiColors.fill,
                unfocusedBorderColor = Color.Transparent,
                focusedBorderColor = MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("add_plan_title_input")
        )

        Spacer(modifier = Modifier.height(18.dp))

        // 3. 预计时长（点击进入滚轮选择盘）
        FormSectionHeader(title = "预计时长")
        Spacer(modifier = Modifier.height(6.dp))

        DurationField(
            durationMinutes = durationMinutes,
            onClick = onOpenDurationPicker
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (hasGoal) {
            val baseRatio = (basePlanned.toFloat() / dailyGoalMinutes).coerceIn(0f, 1f)
            val addedRatio = ((durationMinutes ?: 0).toFloat() / dailyGoalMinutes).coerceIn(0f, 1f - baseRatio)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)) // token-exempt: 6dp 进度轨道的半圆端头，几何必须等于高度一半
                    .background(MaterialTheme.colorScheme.surfaceVariant)
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
            shape = RoundedCornerShape(YanjiRadius.CompactCardRadius),
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
        shape = RoundedCornerShape(14.dp), // token-exempt: 科目字段沿用的独立圆角，无同值 token
        color = YanjiColors.fill,
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
                text = selection.displayName,
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
private fun DurationField(
    durationMinutes: Int?,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp), // token-exempt: 时长字段沿用的独立圆角，无同值 token
        color = YanjiColors.fill,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("add_plan_duration_field")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = RemixIcons.TimeLine,
                contentDescription = null,
                tint = if (durationMinutes != null && durationMinutes > 0) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (durationMinutes == null || durationMinutes <= 0) {
                        "不设定时间"
                    } else {
                        formatDurationText(durationMinutes)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (durationMinutes == null || durationMinutes <= 0) {
                        "无限时间，自由专注"
                    } else {
                        "计划单次学习时长"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            Icon(
                imageVector = RemixIcons.ArrowRightSLine,
                contentDescription = "选择时长",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun formatDurationText(minutes: Int): String = when {
    minutes <= 0 -> "不限时"
    minutes < 60 -> "$minutes 分钟"
    minutes == 60 -> "1 小时 (60 分钟)"
    minutes == 180 -> "3 小时 (180 分钟)"
    minutes % 60 == 0 -> "${minutes / 60} 小时 ($minutes 分钟)"
    else -> "${minutes / 60} 小时 ${minutes % 60} 分钟 ($minutes 分钟)"
}

// --------------------------- 科目选择器页 ---------------------------

@Composable
private fun SubjectPickerPage(
    categories: List<SubjectCategoryItem>,
    currentSelection: SubjectSelection?,
    onBack: () -> Unit,
    onSelect: (SubjectSelection) -> Unit
) {
    var activeCategoryItem by remember(categories, currentSelection) {
        mutableStateOf(currentSelection?.categoryItem ?: categories.firstOrNull())
    }
    val contentFade = YanjiMotion.accessibleFiniteTween<Float>(
        durationMillis = YanjiMotion.DurationFast,
        easing = YanjiMotion.EaseStandard
    )

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
            // 左列：大类（胶囊高亮与平滑变色）
            LazyColumn(
                modifier = Modifier
                    .width(116.dp)
                    .fillMaxHeight()
                    .background(YanjiColors.fill),
                contentPadding = PaddingValues(vertical = 6.dp, horizontal = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(categories, key = { it.category.id }) { cat ->
                    val isActive = activeCategoryItem?.category?.id == cat.category.id
                    val itemBgColor by animateColorAsState(
                        targetValue = if (isActive) MaterialTheme.colorScheme.surface else Color.Transparent,
                        animationSpec = YanjiMotion.accessibleFiniteTween(durationMillis = YanjiMotion.DurationFast),
                        label = "cat_item_bg"
                    )
                    val textColor by animateColorAsState(
                        targetValue = if (isActive) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = YanjiMotion.accessibleFiniteTween(durationMillis = YanjiMotion.DurationFast),
                        label = "cat_item_text"
                    )

                    Surface(
                        onClick = {
                            if (cat.subjects.isEmpty()) {
                                onSelect(SubjectSelection(cat, null))
                            } else {
                                activeCategoryItem = cat
                            }
                        },
                        shape = RoundedCornerShape(10.dp), // token-exempt: 大类选择条目的紧凑几何，无同值 token
                        color = itemBgColor,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isActive) cat.color else cat.color.copy(alpha = 0.45f))
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = cat.category.name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                color = textColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 右列：子科目（大类切换时平滑淡入淡出）
            AnimatedContent(
                targetState = activeCategoryItem,
                transitionSpec = {
                    fadeIn(animationSpec = contentFade) togetherWith fadeOut(animationSpec = contentFade)
                },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                label = "active-cat-sub-anim"
            ) { active ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
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
        }

    }
}

// --------------------------- 时长滚轮选择器页 ---------------------------

@Composable
private fun DurationPickerPage(
    initialMinutes: Int?,
    onBack: () -> Unit,
    onDone: (Int?) -> Unit
) {
    var isTimed by rememberSaveable {
        mutableStateOf(initialMinutes != null && initialMinutes > 0)
    }
    var selectedMinutes by rememberSaveable {
        mutableIntStateOf(initialMinutes?.coerceIn(5, 180) ?: 45)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
    ) {
        PageHeader(title = "预计时长", onBack = onBack) {
            TextButton(
                onClick = {
                    onDone(if (isTimed) selectedMinutes else null)
                }
            ) {
                Text("完成", fontWeight = FontWeight.SemiBold)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 模式切换：不设定时间 vs 设定时间
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilterChip(
                selected = !isTimed,
                onClick = { isTimed = false },
                label = {
                    Text(
                        text = "不设定时间",
                        fontWeight = if (!isTimed) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                shape = RoundedCornerShape(YanjiRadius.Small),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = YanjiColors.fill,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = null,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
            )
            FilterChip(
                selected = isTimed,
                onClick = { isTimed = true },
                label = {
                    Text(
                        text = "设定时间",
                        fontWeight = if (isTimed) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                },
                shape = RoundedCornerShape(YanjiRadius.Small),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = YanjiColors.fill,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                border = null,
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!isTimed) {
            // 不设定时间展示说明
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp, horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(YanjiColors.fill),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = RemixIcons.TimeLine,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "不设定时间（无限时间）",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "开始专注后不设倒计时限制，直到您主动结束，适合自由复习与深度研读。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            // 设定时间：滚轮选择盘（上限 180 分钟）
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                WheelPicker(
                    values = DurationValues,
                    initialIndex = DurationValues.indexOf(selectedMinutes).coerceAtLeast(0),
                    onSelected = { selectedMinutes = it }
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = formatDurationText(selectedMinutes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selectedMinutes >= 180) YanjiColors.warning else MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (selectedMinutes >= 180) "已达时长上限 180 分钟 (3小时)" else "上下滑动选择合适的时间，上限 180 分钟",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
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
            .width(180.dp)
            .height(itemHeight * 3),
        contentAlignment = Alignment.Center
    ) {
        // 中间高亮带
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(itemHeight)
                .clip(RoundedCornerShape(YanjiRadius.Small))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f))
        )
        LazyColumn(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = listState),
            contentPadding = PaddingValues(vertical = itemHeight),
            modifier = Modifier.fillMaxSize()
        ) {
            items(values.size) { i ->
                val v = values[i]
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$v 分钟",
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
