package com.example.yanji.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.yanji.data.StudyTask
import com.example.yanji.data.Subject
import com.example.yanji.theme.YanjiColors
import com.example.yanji.theme.YanjiRadius
import com.example.yanji.theme.YanjiSpacing
import com.example.yanji.theme.yanjiSubjectColorOf
import com.example.yanji.ui.components.YanjiProgressBar
import com.example.yanji.ui.icons.RemixIcons

/**
 * 完成圈直径。分割线要用它内缩到文字列起点，因此不能只写在 [TaskCheckCircle] 里。
 */
private val TaskCircleSize = 22.dp

/**
 * 勾选圈的触控尺寸。
 *
 * 取 40dp 而非 48dp：Material 自己的 Checkbox 正是「20dp 视觉 + 48dp 触控」，
 * 这里沿用同一范式；选 40 是因为**溢出量必须小于行的上下内边距（10dp）**，
 * 否则相邻两行的命中区会纵向重叠，勾上一行却把下面一行也带走。
 * 40 - 22 = 18，溢出 9dp < 10dp，两行互不侵占。
 * 40dp 也远高于 WCAG 2.5.5 (AAA) 的 24dp 下限与 2.5.8 (AA) 的要求。
 */
private val TaskTouchTargetSize = 40.dp

/**
 * 触控框比视觉圆圈每侧多出来的一半（(40-22)/2 = 9dp）。
 *
 * 行的左内边距要减去它，圆圈的**视觉**左缘才会与卡片文字左缘对齐；
 * 触摸区则正好铺到内边距边界，不越出卡片。
 *
 * 9dp 的溢出量还必须小于行高里 8+8dp 的上下内边距之和，勾选圈才不会在纵向
 * 侵入相邻两行的命中区（否则会勾上一行却把下一行也划掉）。
 */
private val TaskTouchTargetOverhang = (TaskTouchTargetSize - TaskCircleSize) / 2

private fun formatTaskMinutes(minutes: Int): String {
    if (minutes <= 0) return "0m"
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "${m}m"
        m == 0 -> "${h}h"
        else -> "${h}h ${m}m"
    }
}

/**
 * 一条计划的「计划时长 vs 实际投入」文案。纯函数，不读 Compose 状态，便于单测。
 *
 * @param plannedMinutes 计划时长，0 表示不限时。
 * @param actualSeconds 已落库到这条计划上的专注秒数（进行中的会话不计入，
 *   因此计时中的那几分钟要等结束时才跳到数字上，由调用方另行提示"计时中"）。
 */
internal data class PlanTimeLabels(
    /** 副标题里的对照短句，如 `28/45 分钟`；还没有实际投入时为 null。 */
    val compare: String?,
    /** 超额提示，如 `已超 12 分钟`；未超额或不足一分钟时为 null。 */
    val overrun: String?
)

internal fun planTimeLabels(plannedMinutes: Int, actualSeconds: Long): PlanTimeLabels {
    val actualMinutes = actualSeconds / 60L
    if (plannedMinutes <= 0) {
        // 不限时的计划没有目标可比，只报实际投入。
        return PlanTimeLabels(compare = if (actualMinutes > 0) "$actualMinutes 分钟" else null, overrun = null)
    }
    if (actualMinutes <= 0) return PlanTimeLabels(compare = null, overrun = null)
    val overrunSeconds = actualSeconds - plannedMinutes * 60L
    return PlanTimeLabels(
        compare = "$actualMinutes/$plannedMinutes 分钟",
        // 不足一分钟不提示：刚从倒计时归零跑到 59 秒时刷一条「已超 0 分钟」只是噪音。
        overrun = if (overrunSeconds >= 60L) "已超 ${overrunSeconds / 60L} 分钟" else null
    )
}

/** 今日计划以开放式列表紧随核心统计区，行内保留编辑、完成与开始入口。 */
@Composable
internal fun TodayPlanCard(
    tasks: List<StudyTask>,
    subjects: List<Subject>,
    onAdd: (Subject, String, Int, (StudyTask) -> Unit) -> Unit,
    onToggle: (StudyTask) -> Unit,
    onDelete: (String) -> Unit,
    onStart: (StudyTask) -> Unit,
    modifier: Modifier = Modifier,
    dailyGoalMinutes: Int = 0,
    /** 计划 id → 已投入秒数。缺项视为 0（尚未开始）。 */
    actualSecondsByTaskId: Map<String, Long> = emptyMap(),
    /** 正在计时且属于某条计划的计划 id，用于点亮那一行。 */
    runningTaskId: String? = null,
    onEdit: ((StudyTask, Subject, String, Int) -> Unit)? = null
) {
    var showAddSheet by rememberSaveable { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<StudyTask?>(null) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_today_plan_card")
            .padding(bottom = YanjiSpacing.ItemGap)
    ) {
        TodayPlanHeader(onAdd = {
            editingTask = null
            showAddSheet = true
        })

        if (tasks.isEmpty()) {
            EmptyPlanHint(onClick = {
                editingTask = null
                showAddSheet = true
            })
        } else {
            val completed = tasks.count { it.isCompleted }
            Spacer(modifier = Modifier.height(YanjiSpacing.ItemGapSmall))
            PlanProgressSummary(tasks = tasks, runningTaskId = runningTaskId)
            Spacer(modifier = Modifier.height(YanjiSpacing.ItemGapSmall))
            YanjiProgressBar(
                progress = completed.toFloat() / tasks.size,
                // 全部完成时转绿，与首页「达成 100%」用同一个 success 语义色。
                color = if (completed == tasks.size) {
                    YanjiColors.success
                } else {
                    MaterialTheme.colorScheme.primary
                },
                height = YanjiSpacing.TightGap,
                modifier = Modifier.padding(horizontal = YanjiSpacing.CardPadding)
            )
            Spacer(modifier = Modifier.height(YanjiSpacing.ItemGapSmall))

            tasks.forEachIndexed { index, task ->
                if (index > 0) {
                    // 分割线内缩到**文字列**的起点，与标题左缘严格对齐。
                    // 若只对齐卡片内边距，线会从勾选圈下方横穿过去，
                    // 视觉上像在给上一行的标题划删除线。
                    // 起点按行内实际排版推：左内边距(含溢出回补) + 触控框 + 间隙。
                    HorizontalDivider(
                        modifier = Modifier.padding(
                            start = (YanjiSpacing.CardPadding - TaskTouchTargetOverhang) +
                                TaskTouchTargetSize +
                                YanjiSpacing.ItemGap
                        ),
                        thickness = 0.6.dp,
                        color = YanjiColors.rowDivider
                    )
                }
                StudyTaskRow(
                    task = task,
                    actualSeconds = actualSecondsByTaskId[task.id] ?: 0L,
                    isRunning = task.id == runningTaskId,
                    onToggle = { onToggle(task) },
                    onStart = { onStart(task) },
                    onEdit = {
                        editingTask = task
                        showAddSheet = true
                    },
                    onDelete = { onDelete(task.id) }
                )
            }
        }
    }

    if (showAddSheet) {
        AddPlanSheet(
            subjects = subjects,
            recentTasks = tasks,
            plannedTodayMinutes = tasks.sumOf { it.plannedMinutes },
            dailyGoalMinutes = dailyGoalMinutes,
            editingTask = editingTask,
            onDismiss = {
                showAddSheet = false
                editingTask = null
            },
            onConfirm = { subject, title, minutes, startNow ->
                val currentEditing = editingTask
                showAddSheet = false
                editingTask = null
                if (currentEditing != null) {
                    onEdit?.invoke(currentEditing, subject, title, minutes)
                    if (startNow) {
                        onStart(
                            currentEditing.copy(
                                subjectId = subject.id,
                                subjectName = subject.name,
                                title = title,
                                plannedMinutes = minutes
                            )
                        )
                    }
                } else {
                    // 「添加并开始专注」必须等落库后回传的**真实**任务：
                    // 调用方临时构造的 StudyTask id 与写进库里的对不上，
                    // 那段专注会挂在一个不存在的计划上，首页这行永远拿不到实际用时。
                    onAdd(subject, title, minutes) { created ->
                        if (startNow) onStart(created)
                    }
                }
            },
        )
    }
}

/** 开放式列表标题与添加入口，不再使用彩色图标底座。 */
@Composable
private fun TodayPlanHeader(onAdd: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = YanjiSpacing.CardPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "今日计划",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        AddPlanButton(onClick = onAdd)
    }
}

/** 文本按钮保留清晰的添加入口与标准触控尺寸。 */
@Composable
private fun AddPlanButton(onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Icon(
            imageVector = RemixIcons.AddLine,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(YanjiSpacing.TightGap))
        Text("添加")
    }
}

/** 没有计划时用普通文字行引导添加，避免空态再套一张卡。 */
@Composable
private fun EmptyPlanHint(onClick: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "添加今日计划", onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = YanjiSpacing.CardPadding, vertical = YanjiSpacing.ItemGap)
    ) {
        Text(
            text = "规划今日第一项任务",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(YanjiSpacing.TightGap))
        Text(
            text = "写下要做的事，从这里开始专注。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 先回答下一步做什么，再给出剩余负担；进行中的计划优先显示。 */
@Composable
private fun PlanProgressSummary(
    tasks: List<StudyTask>,
    runningTaskId: String?
) {
    val runningTask = tasks.firstOrNull { it.id == runningTaskId }
    val pendingTasks = tasks.filter { !it.isCompleted }
    val nextTask = runningTask ?: pendingTasks.firstOrNull()
    val remainingMinutes = pendingTasks.sumOf { it.plannedMinutes }
    Column(modifier = Modifier.padding(horizontal = YanjiSpacing.CardPadding)) {
        Text(
            text = when {
                runningTask != null -> "正在专注 · ${runningTask.title}"
                nextTask != null -> "下一项 · ${nextTask.title}"
                else -> "今日计划已全部完成"
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (nextTask == null) YanjiColors.success else MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (pendingTasks.isNotEmpty()) {
            val remainingTime = if (remainingMinutes > 0) " · 约 ${formatTaskMinutes(remainingMinutes)}" else ""
            Text(
                text = "剩余 ${pendingTasks.size} 项$remainingTime",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * 单条计划行。
 *
 * 从左到右：完成圈 → 标题 / 学科·时长 → 开始按钮。
 * 标题用 weight 自适应并在过长时省略，保证右侧的开始按钮永远不被挤走。
 *
 * 两个交互目标各归其位：**整行（含长按菜单）管「这条计划是什么」，右侧 Play 管「现在就做这件事」**。
 * 曾经整行点击即开始专注，结果想改一条计划的时长或备注时无处下手 —— 「开始」是这条行里
 * 最高频的动作，但它不该吞掉行内其余全部点击语义。
 *
 * 完成态只由「删除线 + 标题变淡 + 圆圈填色」表达，不改变两个入口各自的可用性。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun StudyTaskRow(
    task: StudyTask,
    actualSeconds: Long,
    isRunning: Boolean,
    onToggle: () -> Unit,
    onStart: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    val subjectColor = yanjiSubjectColorOf(task.subjectName)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .combinedClickable(
                onClick = onEdit,
                onClickLabel = "编辑计划",
                onLongClick = { menuOpen = true },
                onLongClickLabel = "更多操作"
            )
            // 行的左内边距扣掉触控框比视觉圆圈多出来的一半，
            // 使 22dp 圆圈本身的左缘仍与卡片文字左缘对齐；
            // 触摸区则刚好铺满内边距，不越出卡片。
            .padding(
                start = YanjiSpacing.CardPadding - TaskTouchTargetOverhang,
                end = YanjiSpacing.CardPadding,
                top = 8.dp,
                bottom = 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 勾选圈：视觉 22dp 圆画在 40dp 的方盒正中 —— 盒子本身即命中区
        TaskCheckCircle(
            checked = task.isCompleted,
            subjectColor = subjectColor,
            title = task.title,
            onToggle = onToggle,
            modifier = Modifier.size(TaskTouchTargetSize)
        )
        Spacer(modifier = Modifier.width(YanjiSpacing.ItemGap))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = subjectColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(YanjiRadius.ItemRadius)
                ) {
                    Text(
                        text = task.subjectName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = subjectColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None,
                    color = if (task.isCompleted) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            val labels = planTimeLabels(task.plannedMinutes, actualSeconds)
            val durationText = labels.compare
                ?: if (task.plannedMinutes > 0) "${task.plannedMinutes} 分钟" else "不限时"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = durationText,
                    style = MaterialTheme.typography.bodySmall,
                    color = YanjiColors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val statusText = labels.overrun ?: if (isRunning) "计时中" else null
                if (statusText != null) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (labels.overrun != null) YanjiColors.warning else MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }
        }

        // 「开始」：行内唯一的启动入口，因此必须是一个真按钮 —— 自带触控框、涟漪边界
        // 与读屏标签，而不只是整行点击的视觉提示。与勾选圈同为「40dp 触控 + 更小视觉尺寸」
        // 的套路数；涟漪裁剪成圆形，避免方形水波纹铺满整行右侧。
        //
        // 内层 [androidx.compose.foundation.clickable] 先于整行的 combinedClickable
        // 消费事件，因此点这里是「开始」，点行的其余位置才是「编辑」。
        Box(
            modifier = Modifier
                .size(TaskTouchTargetSize)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClick = onStart),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = RemixIcons.PlayFill,
                contentDescription = "开始专注",
                tint = if (task.isCompleted) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier.size(18.dp)
            )
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false }
        ) {
            DropdownMenuItem(
                text = { Text("编辑计划") },
                leadingIcon = { Icon(RemixIcons.EditLine, contentDescription = null) },
                onClick = {
                    menuOpen = false
                    onEdit()
                }
            )
            DropdownMenuItem(
                text = { Text("删除") },
                leadingIcon = { Icon(RemixIcons.DeleteBinLine, contentDescription = null) },
                colors = MenuDefaults.itemColors(textColor = MaterialTheme.colorScheme.error),
                onClick = {
                    menuOpen = false
                    onDelete()
                }
            )
        }
    }
}

/**
 * 完成圈。
 *
 * 不用 M3 `Checkbox`：它默认是 20dp 方形，在 56dp 高的行里视觉重量过大，
 * 且「已勾选」态是主色方块，会和学科色点抢注意力。
 *
 * 这里用 22dp 圆形圈：未完成时描学科色、已完成时填学科色 ——
 * 于是「这行属于哪门课」与「做没做完」由同一个形状一次讲完，
 * 不必再单独放一个学科色点。描边走 `outline` 槽位（专职 ≥3:1 的控件边界，WCAG 1.4.11）。
 *
 * 交互用 [Modifier.toggleable] 而不是 `clickable`：配上 `Role.Checkbox` 与
 * `ToggleableState` 之后，读屏才会念出「已勾选 / 未勾选」。只给 `clickable` 挂一个
 * Checkbox 角色，勾选状态是念不出来的 —— 视障用户将无法知道这件计划到底做完没有。
 *
 * 视觉 22dp，触控 48dp：勾选圈是这条行里**唯一**的独立点击目标，
 * 22dp 的可点面积达不到 WCAG 2.5.8 的最小目标尺寸。因此外层套一个 48dp 的透明方框承载
 * toggleable，圆圈本身居中绘制。行高本来就 ≥56dp，多出来的透明区域不会挤压文字，
 * 也不会和整行的点击区产生「按到哪」的分歧：内层先消费事件。
 */
@Composable
private fun TaskCheckCircle(
    checked: Boolean,
    subjectColor: Color,
    title: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    // 勾选圈不画水波纹：同一行里整行已经可点，再叠一层涟漪会让人分不清点击层级。
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .toggleable(
                value = checked,
                onValueChange = { onToggle() },
                role = Role.Checkbox,
                interactionSource = interactionSource,
                indication = null
            )
            .semantics { contentDescription = "「$title」" },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(TaskCircleSize)
                .clip(CircleShape)
                .background(if (checked) subjectColor else MaterialTheme.colorScheme.surface)
                .then(
                    if (checked) {
                        Modifier
                    } else {
                        Modifier.border(1.5.dp, subjectColor, CircleShape)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = RemixIcons.CheckLine,
                    contentDescription = null,
                    // 勾号压在学科色填充上，需要的是「反色」而非固定主色，
                    // 否则深色学科上会与背景糊在一起。
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}
